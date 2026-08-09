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
from ..hashing import canonical_mapping_sha256, file_sha256
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
from .planning import FeaturePartitionPlan, build_feature_plan
from .snapshot import ExtractionSnapshot
from .spool import FeatureArtifact, FeatureSpool, iter_parquet_rows
from .supply import load_supply_series


_COMMIT_HASH = re.compile(r"^(?:[0-9a-f]{40}|[0-9a-f]{64})$")


@dataclass(frozen=True)
class FeaturePartitionArtifact:
    plan: FeaturePartitionPlan
    artifact: FeatureArtifact

    def to_dict(self, root: Path) -> dict[str, object]:
        return {
            **self.plan.to_dict(),
            "bytes": self.artifact.byte_count,
            "file": self.artifact.path.relative_to(root).as_posix(),
            "rows": self.artifact.row_count,
            "sha256": self.artifact.sha256,
        }


@dataclass(frozen=True)
class FeatureDatasetArtifact:
    path: Path
    partitions: tuple[FeaturePartitionArtifact, ...]
    row_count: int
    byte_count: int
    sha256: str


@dataclass(frozen=True)
class FeatureBuildOutcome:
    artifact_run_id: str
    processing_run_id: UUID
    feature_artifact_id: UUID
    feature_set_version: str
    artifact: FeatureDatasetArtifact
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
                "directory": self.artifact.path.name,
                "partitions": len(self.artifact.partitions),
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


def _dataset_artifact(
    path: Path,
    partitions: list[FeaturePartitionArtifact],
    *,
    run_directory: Path,
) -> FeatureDatasetArtifact:
    values = [item.to_dict(run_directory) for item in partitions]
    return FeatureDatasetArtifact(
        path=path,
        partitions=tuple(partitions),
        row_count=sum(item.artifact.row_count for item in partitions),
        byte_count=sum(item.artifact.byte_count for item in partitions),
        sha256=canonical_mapping_sha256({"partitions": values}),
    )


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
        "cellEligibility": {
            "includeBoundaryTies": True,
            "requestedTrainingDemandCoverage": (
                config.features.training_demand_coverage
            ),
            "selectionData": "training split only",
        },
        "demandLags": list(config.features.demand_lags),
        "neighbor": {
            "enabled": config.features.include_spatial_neighbors,
            "meaning": "sum of lag-1 demand across eight adjacent square cells",
        },
        "rollingWindows": list(config.features.rolling_windows),
        "partitioning": {
            "dimension": config.artifacts.feature_partition,
            "maximumRowsPerPartition": (
                config.artifacts.maximum_rows_per_partition
            ),
            "maximumRowsPerRun": config.artifacts.maximum_rows_per_run,
        },
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
    spool_paths: set[Path] = set()
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
                "featurePartition": config.artifacts.feature_partition,
                "maximumRowsPerPartition": (
                    config.artifacts.maximum_rows_per_partition
                ),
                "maximumRowsPerRun": config.artifacts.maximum_rows_per_run,
                "sourceSnapshotSha256": snapshot.snapshot_sha256,
                "trainingDemandCoverage": (
                    config.features.training_demand_coverage
                ),
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
        plan = build_feature_plan(cube, config)
        eligibility_payload = {
            **plan.eligibility.to_dict(),
            "selectedCellIds": [
                cube.cells[key].cell_id
                for key in plan.eligibility.selected_cell_keys
            ],
        }
        _write_json(
            run_directory / "cell-eligibility.json",
            eligibility_payload,
        )
        features_directory = run_directory / "demand-features"
        features_directory.mkdir()
        partition_artifacts: list[FeaturePartitionArtifact] = []
        for partition in plan.partitions:
            spool_path = run_directory / f".feature-spool-{partition.key}.sqlite3"
            spool_paths.add(spool_path)
            with FeatureSpool(spool_path) as spool:
                for row in iter_feature_rows(
                    cube,
                    config,
                    grid=grid,
                    version=version,
                    snapshot_sha256=snapshot.snapshot_sha256,
                    feature_artifact_id=deterministic_artifact_id,
                    supply=supply,
                    selected_cell_keys=plan.eligibility.selected_cell_keys,
                    target_from_utc=partition.target_from_utc,
                    target_to_utc=partition.target_to_utc,
                ):
                    spool.add(row)
                cube.stats.duplicate_feature_rows += spool.duplicate_rows
                partition_rows = spool.count()
                if partition_rows != partition.projected_rows:
                    raise ExtractionError(
                        "FEATURE_PARTITION_COUNT_MISMATCH",
                        "Generated partition row count differs from its cost plan",
                        {
                            "actualRows": partition_rows,
                            "partition": partition.key,
                            "projectedRows": partition.projected_rows,
                        },
                    )
                partition_directory = (
                    features_directory / f"target_month={partition.key}"
                )
                partition_directory.mkdir()
                partition_artifacts.append(
                    FeaturePartitionArtifact(
                        partition,
                        spool.export_parquet(
                            partition_directory / "part-00000.parquet",
                            compression=config.artifacts.compression,
                        ),
                    )
                )
            spool_path.unlink(missing_ok=True)
            spool_paths.discard(spool_path)
        artifact = _dataset_artifact(
            features_directory,
            partition_artifacts,
            run_directory=run_directory,
        )
        rows_built = artifact.row_count
        if rows_built != plan.projected_rows:
            raise ExtractionError(
                "FEATURE_RUN_COUNT_MISMATCH",
                "Generated feature-row count differs from the cost plan",
                {"actualRows": rows_built, "projectedRows": plan.projected_rows},
            )
        quality = build_feature_quality(
            cube.stats,
            expected_buckets=cube.bucket_count,
            include_supply=config.features.include_supply_features,
        )
        _write_json(run_directory / "feature-quality.json", quality.to_dict())
        _write_json(
            run_directory / "feature-manifest.json",
            {
                "cellEligibility": plan.eligibility.to_dict(),
                "cellSizeMeters": selected_cell_size,
                "featureArtifactId": str(deterministic_artifact_id),
                "featureSetVersion": version,
                "fromUtc": snapshot.from_utc.isoformat().replace("+00:00", "Z"),
                "gridOriginMeters": {
                    "x": config.spatial.grid_origin_x_meters,
                    "y": config.spatial.grid_origin_y_meters,
                },
                "gridVersion": config.spatial.grid_version,
                "partitionBy": config.artifacts.feature_partition,
                "partitions": [
                    item.to_dict(run_directory) for item in artifact.partitions
                ],
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
        persisted = 0
        with write_connection.transaction():
            for partition in artifact.partitions:
                persisted += feature_repository.upsert(
                    iter_parquet_rows(partition.artifact.path),
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
        for spool_path in spool_paths:
            spool_path.unlink(missing_ok=True)
        if supply_connection is not None:
            supply_connection.close()
        if write_connection is not None:
            write_connection.close()
