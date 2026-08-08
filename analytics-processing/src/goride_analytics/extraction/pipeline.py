from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Mapping
from uuid import UUID

from ..config import ProcessingConfig
from ..database import (
    DatabaseSettings,
    PostgresProcessingRunRepository,
    ProcessingRunRepository,
    connect_database,
    processing_run_id,
    verify_database,
)
from ..errors import AnalyticsError, ConfigurationError, DataQualityError, ExtractionError
from ..hashing import file_sha256
from ..manifest import DatasetValidationResult
from ..quality import ExtractionStats, QualityReport, build_quality_report
from ..runs import (
    RunIdentity,
    allocate_run_directory,
    build_run_identity,
    write_run_manifest,
)
from .canonical import CanonicalSpool, SnapshotArtifact, build_snapshot_id, utc_text
from .goride import GoRidePostgresExtractor, query_hash, sanitized_query_plan
from .porto import PortoArchiveExtractor


_COMMIT_HASH = re.compile(r"^(?:[0-9a-f]{40}|[0-9a-f]{64})$")


@dataclass(frozen=True)
class ExtractionOutcome:
    artifact_run_id: str
    processing_run_id: UUID
    snapshot_id: UUID
    snapshot: SnapshotArtifact
    quality: QualityReport
    run_directory: Path
    attempt_no: int

    def to_dict(self) -> dict[str, object]:
        return {
            "artifactRunId": self.artifact_run_id,
            "attemptNo": self.attempt_no,
            "canonicalSnapshot": {
                "bytes": self.snapshot.byte_count,
                "file": self.snapshot.path.name,
                "rows": self.snapshot.row_count,
                "sha256": self.snapshot.sha256,
                "snapshotId": str(self.snapshot_id),
            },
            "processingRunId": str(self.processing_run_id),
            "qualityStatus": self.quality.overall_status,
        }


def parse_utc_boundary(value: str, field: str) -> datetime:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ConfigurationError(
            "EXTRACTION_BOUNDARY_INVALID",
            f"{field} must be an ISO-8601 date-time with a UTC offset",
            {"field": field},
        ) from error
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ConfigurationError(
            "EXTRACTION_BOUNDARY_INVALID",
            f"{field} must include a UTC offset",
            {"field": field},
        )
    return parsed.astimezone(timezone.utc)


def validate_interval(
    from_utc: datetime,
    cutoff_utc: datetime,
    *,
    bucket_minutes: int,
    now: datetime | None = None,
) -> None:
    now = (now or datetime.now(timezone.utc)).astimezone(timezone.utc)
    if from_utc.tzinfo is None or cutoff_utc.tzinfo is None:
        raise ConfigurationError(
            "EXTRACTION_BOUNDARY_INVALID",
            "Extraction boundaries must be timezone-aware",
        )
    if from_utc >= cutoff_utc:
        raise ConfigurationError(
            "EXTRACTION_RANGE_INVALID",
            "from-utc must be earlier than cutoff-utc",
        )
    if cutoff_utc > now:
        raise ConfigurationError(
            "EXTRACTION_CUTOFF_FUTURE",
            "cutoff-utc must not be in the future",
        )
    for field, value in (("from-utc", from_utc), ("cutoff-utc", cutoff_utc)):
        if value.second or value.microsecond or value.minute % bucket_minutes:
            raise ConfigurationError(
                "EXTRACTION_BOUNDARY_MISALIGNED",
                f"{field} must align to the configured time bucket",
                {"bucketMinutes": bucket_minutes, "field": field},
            )


def _write_json(path: Path, value: Any) -> None:
    temporary = path.with_name(f".{path.name}.tmp")
    temporary.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    os.replace(temporary, path)


def _artifact_root(
    config: ProcessingConfig,
    environment: Mapping[str, str],
) -> Path:
    variable = config.dataset.root_env or "GORIDE_ANALYTICS_DATA_ROOT"
    value = environment.get(variable, "").strip()
    if not value:
        raise ConfigurationError(
            "ARTIFACT_ROOT_ENV_MISSING",
            f"Environment variable {variable} is required for extraction artifacts",
            {"environmentVariable": variable},
        )
    root = Path(value).expanduser().resolve()
    if not root.is_dir():
        raise ConfigurationError(
            "ARTIFACT_ROOT_INVALID",
            "Analytics artifact root does not exist or is not a directory",
            {"environmentVariable": variable},
        )
    return root


def _write_dataset_manifest(
    target: Path,
    config: ProcessingConfig,
    dataset: DatasetValidationResult,
    *,
    from_utc: datetime,
    cutoff_utc: datetime,
) -> dict[str, Any]:
    if dataset.source_type == "archive":
        if dataset.manifest_path is None:
            raise ExtractionError(
                "DATASET_MANIFEST_UNRESOLVED",
                "Validated archive manifest path is unavailable",
            )
        payload = json.loads(dataset.manifest_path.read_text(encoding="utf-8"))
    else:
        payload = {
            "datasetName": dataset.dataset_name,
            "datasetVersion": dataset.dataset_version,
            "extractFromUtc": utc_text(from_utc),
            "sourceCutoffUtc": utc_text(cutoff_utc),
            "sourceCrs": config.dataset.expected_source_crs,
            "sourceQueryHash": query_hash(),
            "sourceType": "postgresql",
            "timezone": config.dataset.expected_timezone,
        }
    _write_json(target, payload)
    return payload


def _write_checksums(run_directory: Path) -> None:
    files = sorted(
        path
        for path in run_directory.iterdir()
        if path.is_file()
        and not path.name.startswith(".")
        and path.name != "checksums.sha256"
    )
    content = "".join(f"{file_sha256(path)}  {path.name}\n" for path in files)
    temporary = run_directory / ".checksums.sha256.tmp"
    temporary.write_text(content, encoding="utf-8")
    os.replace(temporary, run_directory / "checksums.sha256")


def _failure_evidence(
    run_directory: Path,
    error: AnalyticsError,
    stats: ExtractionStats,
) -> None:
    _write_json(
        run_directory / "extraction-failure.json",
        {
            "errorCode": error.code,
            "message": error.message,
            "rowsAccepted": stats.accepted_rows,
            "rowsScanned": stats.rows_scanned,
            "status": "FAILED",
        },
    )
    _write_checksums(run_directory)


def run_extraction(
    config: ProcessingConfig,
    dataset: DatasetValidationResult,
    *,
    from_utc: datetime,
    cutoff_utc: datetime,
    environment: Mapping[str, str] | None = None,
    now: datetime | None = None,
    identity: RunIdentity | None = None,
    connection_factory: Callable[..., Any] = connect_database,
    repository_factory: Callable[[Any], ProcessingRunRepository] = (
        PostgresProcessingRunRepository
    ),
    porto_extractor: PortoArchiveExtractor | None = None,
    goride_extractor: GoRidePostgresExtractor | None = None,
) -> ExtractionOutcome:
    environment = environment or os.environ
    validate_interval(
        from_utc,
        cutoff_utc,
        bucket_minutes=config.temporal.bucket_minutes,
        now=now,
    )
    identity = identity or build_run_identity(config, "extraction")
    if not _COMMIT_HASH.fullmatch(identity.git_commit):
        raise ExtractionError(
            "CODE_COMMIT_UNAVAILABLE",
            "A full Git commit hash is required for persistent extraction evidence",
        )

    snapshot_id = build_snapshot_id(
        source_profile=config.profile.name,
        dataset_version=config.dataset.version,
        from_utc=from_utc,
        cutoff_utc=cutoff_utc,
        config_hash=config.config_hash,
        code_commit=identity.git_commit,
    )
    root = _artifact_root(config, environment)
    run_directory = allocate_run_directory(root, identity)
    write_run_manifest(run_directory, identity)
    dataset_payload = _write_dataset_manifest(
        run_directory / "dataset-manifest.json",
        config,
        dataset,
        from_utc=from_utc,
        cutoff_utc=cutoff_utc,
    )

    write_connection = None
    source_connection = None
    repository = None
    db_run_id = processing_run_id(identity)
    stats = ExtractionStats()
    started = False
    terminal = False
    try:
        settings = DatabaseSettings.from_environment(environment)
        write_connection = connection_factory(settings, read_only=False)
        repository = repository_factory(write_connection)
        database_metadata = verify_database(
            write_connection,
            require_forecast_schema=True,
        )
        attempt_no = repository.start(
            run_id=db_run_id,
            identity=identity,
            source_profile=config.profile.name,
            dataset_version=config.dataset.version,
            source_cutoff=cutoff_utc,
            input_manifest={
                "datasetManifestSha256": file_sha256(
                    run_directory / "dataset-manifest.json"
                ),
                "extractFromUtc": utc_text(from_utc),
                "snapshotId": str(snapshot_id),
                "sourceCutoffUtc": utc_text(cutoff_utc),
            },
        )
        started = True
        spool_path = run_directory / ".canonical-spool.sqlite3"
        query_evidence: dict[str, Any] | None = None
        with CanonicalSpool(spool_path) as spool:
            if dataset.source_type == "archive":
                if dataset.source_path is None:
                    raise ExtractionError(
                        "DATASET_SOURCE_UNRESOLVED",
                        "Validated archive source path is unavailable",
                    )
                stats = (porto_extractor or PortoArchiveExtractor()).extract(
                    dataset.source_path,
                    spool,
                    source_profile=config.profile.name,
                    dataset_version=config.dataset.version,
                    from_utc=from_utc,
                    cutoff_utc=cutoff_utc,
                    snapshot_id=snapshot_id,
                )
            else:
                source_prefix = config.dataset.database_env_prefix or "ANALYTICS_DATABASE"
                source_settings = DatabaseSettings.from_environment(
                    environment,
                    source_prefix,
                )
                source_connection = connection_factory(source_settings, read_only=True)
                verify_database(source_connection, require_forecast_schema=False)
                result = (goride_extractor or GoRidePostgresExtractor()).extract(
                    source_connection,
                    spool,
                    source_profile=config.profile.name,
                    dataset_version=config.dataset.version,
                    from_utc=from_utc,
                    cutoff_utc=cutoff_utc,
                    snapshot_id=snapshot_id,
                    bucket_minutes=config.temporal.bucket_minutes,
                )
                stats = result.stats
                query_evidence = {
                    "boundedInterval": "[fromUtc, cutoffUtc)",
                    "queryHash": result.query_hash,
                    "queryPlan": sanitized_query_plan(result.query_plan),
                    "readOnly": True,
                }
            snapshot = spool.export_jsonl(
                run_directory / "canonical-demand-events.jsonl"
            )
        spool_path.unlink(missing_ok=True)

        quality = build_quality_report(
            stats,
            checksum_verified=(
                dataset.source_type != "archive"
                or dataset.sha256 == dataset_payload.get("sha256")
            ),
            include_missing_trajectory_rule=dataset.source_type == "archive",
            include_supply_coverage_rule=(
                dataset.source_type == "postgresql"
                and config.features.include_supply_features
            ),
        )
        _write_json(run_directory / "data-quality.json", quality.to_dict())
        if query_evidence is not None:
            _write_json(run_directory / "source-query-plan.json", query_evidence)
        _write_json(
            run_directory / "extraction-summary.json",
            {
                "canonicalSnapshot": {
                    "bytes": snapshot.byte_count,
                    "rows": snapshot.row_count,
                    "sha256": snapshot.sha256,
                    "snapshotId": str(snapshot_id),
                },
                "database": database_metadata.to_dict(),
                "extractFromUtc": utc_text(from_utc),
                "qualityStatus": quality.overall_status,
                "sourceCutoffUtc": utc_text(cutoff_utc),
            },
        )
        repository.save_quality(db_run_id, quality.results)
        if quality.failed_rules:
            repository.fail(
                db_run_id,
                rows_read=stats.rows_scanned,
                rows_written=snapshot.row_count,
                error_code="DATA_QUALITY_FAILED",
                error_message="FAIL rules: " + ", ".join(quality.failed_rules),
            )
            terminal = True
            _write_checksums(run_directory)
            raise DataQualityError(quality.failed_rules, str(db_run_id))
        repository.succeed(
            db_run_id,
            rows_read=stats.rows_scanned,
            rows_written=snapshot.row_count,
        )
        terminal = True
        _write_checksums(run_directory)
        return ExtractionOutcome(
            artifact_run_id=identity.run_id,
            processing_run_id=db_run_id,
            snapshot_id=snapshot_id,
            snapshot=snapshot,
            quality=quality,
            run_directory=run_directory,
            attempt_no=attempt_no,
        )
    except AnalyticsError as error:
        if started and not terminal and repository is not None:
            try:
                repository.fail(
                    db_run_id,
                    rows_read=stats.rows_scanned,
                    rows_written=stats.accepted_rows,
                    error_code=error.code,
                    error_message=error.message,
                )
            except AnalyticsError:
                pass
        if not (run_directory / "extraction-failure.json").exists():
            _failure_evidence(run_directory, error, stats)
        raise
    except Exception as error:
        wrapped = ExtractionError(
            "EXTRACTION_UNEXPECTED_FAILURE",
            "Unexpected failure while producing the canonical snapshot",
            {"errorType": type(error).__name__},
        )
        if started and not terminal and repository is not None:
            try:
                repository.fail(
                    db_run_id,
                    rows_read=stats.rows_scanned,
                    rows_written=stats.accepted_rows,
                    error_code=wrapped.code,
                    error_message=wrapped.message,
                )
            except AnalyticsError:
                pass
        _failure_evidence(run_directory, wrapped, stats)
        raise wrapped from error
    finally:
        spool = run_directory / ".canonical-spool.sqlite3"
        spool.unlink(missing_ok=True)
        if source_connection is not None:
            source_connection.close()
        if write_connection is not None:
            write_connection.close()
