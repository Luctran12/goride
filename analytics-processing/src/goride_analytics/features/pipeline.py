from __future__ import annotations

import json
import os
import re
import shutil
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Mapping
from uuid import UUID

from ..config import ProcessingConfig
from ..database import (
    DatabaseSettings,
    PostgresFeatureRepository,
    PostgresProcessingRunRepository,
    ProcessingRunRepository,
    connect_database,
    processing_run_id,
    verify_database,
)
from ..errors import AnalyticsError, DataQualityError, ExtractionError
from ..hashing import file_sha256
from ..runs import (
    RunIdentity,
    allocate_run_directory,
    build_run_identity,
    write_run_manifest,
)
from .builder import (
    FeatureQualityReport,
    SupplySeries,
    aggregate_snapshot,
    build_feature_quality,
    feature_artifact_id,
    feature_set_version,
    iter_feature_rows,
)
from .grid import GridDefinition
from .snapshot import ExtractionSnapshot
from .spool import FeatureArtifact, FeatureSpool
from .supply import load_supply_series


_COMMIT_HASH = re.compile(r"^(?:[0-9a-f]{40}|[0-9a-f]{64})$")


@dataclass(frozen=True)
class FeatureBuildOutcome:
    artifact_run_id: str
    processing_run_id: UUID
    feature_artifact_id: UUID
    feature_set_version: str
    artifact: FeatureArtifact
    quality: FeatureQualityReport
    attempt_no: int
    run_directory: Path

    def to_dict(self) -> dict[str, object]:
        return {
            "artifactRunId": self.artifact_run_id,
            "attemptNo": self.attempt_no,
            "featureArtifact": {
                "bytes": self.artifact.byte_count,
                "featureArtifactId": str(self.feature_artifact_id),
                "featureSetVersion": self.feature_set_version,
                "file": self.artifact.path.name,
                "rows": self.artifact.row_count,
                "sha256": self.artifact.sha256,
            },
            "processingRunId": str(self.processing_run_id),
            "qualityStatus": self.quality.overall_status,
        }


def _write_json(path: Path, value: Any) -> None:
    temporary = path.with_name(f".{path.name}.tmp")
    temporary.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    os.replace(temporary, path)


def _write_checksums(directory: Path) -> None:
    files = sorted(
        item
        for item in directory.iterdir()
        if item.is_file()
        and not item.name.startswith(".")
        and item.name != "checksums.sha256"
    )
    content = "".join(f"{file_sha256(item)}  {item.name}\n" for item in files)
    temporary = directory / ".checksums.sha256.tmp"
    temporary.write_text(content, encoding="utf-8")
    os.replace(temporary, directory / "checksums.sha256")


def _analytics_root(config: ProcessingConfig, environment: Mapping[str, str]) -> Path:
    variable = config.dataset.root_env or "GORIDE_ANALYTICS_DATA_ROOT"
    raw = environment.get(variable, "").strip()
    if not raw:
        raise ExtractionError(
            "FEATURE_ARTIFACT_ROOT_MISSING",
            f"Environment variable {variable} is required",
            {"environmentVariable": variable},
        )
    root = Path(raw).expanduser().resolve()
    if not root.is_dir():
        raise ExtractionError(
            "FEATURE_ARTIFACT_ROOT_INVALID",
            "Analytics data root does not exist or is not a directory",
        )
    return root


def _dictionary_payload(config: ProcessingConfig) -> dict[str, object]:
    return {
        "calendar": {
            "dayOfWeek": "target bucket in source IANA timezone; Monday=0",
            "hourSinCos": "target local minute-of-day encoded over 24 hours",
            "isWeekend": "dayOfWeek in {5,6}",
        },
        "coverage": {
            "history": "available closed history buckets / minimum_history_buckets",
            "policy": "missing history is null; continuous complete buckets are zero",
            "quality": "WARN when coverage_ratio < 1",
        },
        "demandLags": list(config.features.demand_lags),
        "neighbor": {
            "enabled": config.features.include_spatial_neighbors,
            "meaning": "sum of lag-1 demand across eight adjacent square cells",
        },
        "rollingWindows": list(config.features.rolling_windows),
        "supply": {
            "enabled": config.features.include_supply_features,
            "meaning": "sum of available drivers at the previous closed area bucket",
            "missingPolicy": "null and WARN; never coerced to zero",
        },
        "target": "accepted demand events in target cell and [bucket,bucket+15m)",
        "version": "demand-features-v1",
    }


def run_feature_build(
    config: ProcessingConfig,
    *,
    extraction_run: str | Path,
    cell_size_meters: int | None = None,
    environment: Mapping[str, str] | None = None,
    identity: RunIdentity | None = None,
    connection_factory: Callable[..., Any] = connect_database,
    repository_factory: Callable[[Any], ProcessingRunRepository] = (
        PostgresProcessingRunRepository
    ),
    feature_repository_factory: Callable[[Any], Any] = PostgresFeatureRepository,
    supplied_series: SupplySeries | None = None,
) -> FeatureBuildOutcome:
    environment = environment or os.environ
    root = _analytics_root(config, environment)
    snapshot = ExtractionSnapshot.load(
        extraction_run,
        analytics_root=root,
        config=config,
    )
    selected_cell_size = cell_size_meters or config.spatial.primary_cell_size_meters
    grid = GridDefinition(config.spatial, selected_cell_size)
    identity = identity or build_run_identity(config, "feature-build")
    if not _COMMIT_HASH.fullmatch(identity.git_commit):
        raise ExtractionError(
            "CODE_COMMIT_UNAVAILABLE",
            "A full Git commit hash is required for persistent feature evidence",
        )
    version = feature_set_version(
        config.config_hash,
        snapshot.snapshot_sha256,
        identity.git_commit,
    )
    deterministic_artifact_id = feature_artifact_id(
        version,
        cell_size_meters=selected_cell_size,
        from_utc=snapshot.from_utc,
        cutoff_utc=snapshot.cutoff_utc,
    )
    run_directory = allocate_run_directory(root, identity)
    write_run_manifest(run_directory, identity)
    shutil.copyfile(config.path, run_directory / "config.yml")
    _write_json(run_directory / "feature-dictionary.json", _dictionary_payload(config))

    write_connection = None
    supply_connection = None
    run_repository = None
    db_run_id = processing_run_id(identity)
    started = False
    terminal = False
    rows_built = 0
    input_rows = 0
    spool_path = run_directory / ".feature-spool.sqlite3"
    try:
        settings = DatabaseSettings.from_environment(environment)
        write_connection = connection_factory(settings, read_only=False)
        verify_database(write_connection, require_forecast_schema=True)
        run_repository = repository_factory(write_connection)
        attempt_no = run_repository.start(
            run_id=db_run_id,
            run_type="FEATURE_BUILD",
            identity=identity,
            source_profile=config.profile.name,
            dataset_version=config.dataset.version,
            source_cutoff=snapshot.cutoff_utc,
            input_manifest={
                "cellSizeMeters": selected_cell_size,
                "extractionRun": snapshot.run_directory.name,
                "featureArtifactId": str(deterministic_artifact_id),
                "featureSetVersion": version,
                "sourceSnapshotSha256": snapshot.snapshot_sha256,
            },
        )
        started = True

        supply = supplied_series
        if config.features.include_supply_features and supply is None:
            source_prefix = config.dataset.database_env_prefix or "ANALYTICS_DATABASE"
            source_settings = DatabaseSettings.from_environment(environment, source_prefix)
            supply_connection = connection_factory(source_settings, read_only=True)
            verify_database(supply_connection, require_forecast_schema=False)
            supply = load_supply_series(
                supply_connection,
                from_utc=snapshot.from_utc,
                cutoff_utc=snapshot.cutoff_utc,
                bucket_minutes=config.temporal.bucket_minutes,
            )

        cube = aggregate_snapshot(
            snapshot,
            grid,
            bucket_minutes=config.temporal.bucket_minutes,
        )
        input_rows = cube.stats.input_events
        with FeatureSpool(spool_path) as spool:
            for row in iter_feature_rows(
                cube,
                config,
                grid=grid,
                version=version,
                snapshot_sha256=snapshot.snapshot_sha256,
                feature_artifact_id=deterministic_artifact_id,
                supply=supply,
            ):
                spool.add(row)
            cube.stats.duplicate_feature_rows = spool.duplicate_rows
            rows_built = spool.count()
            quality = build_feature_quality(
                cube.stats,
                expected_buckets=cube.bucket_count,
                include_supply=config.features.include_supply_features,
            )
            artifact = spool.export_parquet(
                run_directory / "demand-features.parquet",
                compression=config.artifacts.compression,
            )
            _write_json(run_directory / "feature-quality.json", quality.to_dict())
            _write_json(
                run_directory / "feature-manifest.json",
                {
                    "cellCount": len(cube.cells),
                    "cellSizeMeters": selected_cell_size,
                    "featureArtifactId": str(deterministic_artifact_id),
                    "featureSetVersion": version,
                    "fromUtc": snapshot.from_utc.isoformat().replace("+00:00", "Z"),
                    "gridOriginMeters": {
                        "x": config.spatial.grid_origin_x_meters,
                        "y": config.spatial.grid_origin_y_meters,
                    },
                    "gridVersion": config.spatial.grid_version,
                    "projectedSrid": config.spatial.projected_srid,
                    "qualityStatus": quality.overall_status,
                    "rowCount": artifact.row_count,
                    "sha256": artifact.sha256,
                    "sourceCutoffUtc": snapshot.cutoff_utc.isoformat().replace(
                        "+00:00", "Z"
                    ),
                    "sourceSnapshotSha256": snapshot.snapshot_sha256,
                    "studyBoundsWgs84": dict(
                        config.spatial.study_bounds_wgs84.__dict__
                    ),
                },
            )
            run_repository.save_quality(db_run_id, quality.results)
            if quality.failed_rules:
                run_repository.fail(
                    db_run_id,
                    rows_read=input_rows,
                    rows_written=rows_built,
                    error_code="DATA_QUALITY_FAILED",
                    error_message="FAIL rules: " + ", ".join(quality.failed_rules),
                )
                terminal = True
                _write_checksums(run_directory)
                raise DataQualityError(quality.failed_rules, str(db_run_id))
            _write_checksums(run_directory)
            feature_repository = feature_repository_factory(write_connection)
            with write_connection.transaction():
                persisted = feature_repository.upsert(
                    spool.rows(),
                    created_by_run_id=db_run_id,
                )
                if persisted != rows_built:
                    raise ExtractionError(
                        "FEATURE_PERSIST_COUNT_MISMATCH",
                        "Persisted feature-row count differs from the artifact",
                        {"artifactRows": rows_built, "persistedRows": persisted},
                    )
                run_repository.succeed(
                    db_run_id,
                    rows_read=input_rows,
                    rows_written=rows_built,
                )
        terminal = True
        return FeatureBuildOutcome(
            artifact_run_id=identity.run_id,
            processing_run_id=db_run_id,
            feature_artifact_id=deterministic_artifact_id,
            feature_set_version=version,
            artifact=artifact,
            quality=quality,
            attempt_no=attempt_no,
            run_directory=run_directory,
        )
    except AnalyticsError as error:
        if started and not terminal and run_repository is not None:
            try:
                run_repository.fail(
                    db_run_id,
                    rows_read=input_rows,
                    rows_written=rows_built,
                    error_code=error.code,
                    error_message=error.message,
                )
            except AnalyticsError:
                pass
        _write_json(
            run_directory / "feature-build-failure.json",
            {"errorCode": error.code, "message": error.message, "status": "FAILED"},
        )
        _write_checksums(run_directory)
        raise
    except Exception as error:
        wrapped = ExtractionError(
            "FEATURE_BUILD_UNEXPECTED_FAILURE",
            "Unexpected failure while building demand features",
            {"errorType": type(error).__name__},
        )
        if started and not terminal and run_repository is not None:
            try:
                run_repository.fail(
                    db_run_id,
                    rows_read=input_rows,
                    rows_written=rows_built,
                    error_code=wrapped.code,
                    error_message=wrapped.message,
                )
            except AnalyticsError:
                pass
        _write_json(
            run_directory / "feature-build-failure.json",
            {
                "errorCode": wrapped.code,
                "message": wrapped.message,
                "status": "FAILED",
            },
        )
        _write_checksums(run_directory)
        raise wrapped from error
    finally:
        spool_path.unlink(missing_ok=True)
        if supply_connection is not None:
            supply_connection.close()
        if write_connection is not None:
            write_connection.close()
