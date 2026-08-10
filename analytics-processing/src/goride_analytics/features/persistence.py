from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass
from datetime import datetime
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
from ..quality import QualityResult
from ..runs import (
    RunIdentity,
    allocate_run_directory,
    build_run_identity,
    write_run_manifest,
)
from .spool import iter_parquet_rows


_COMMIT_HASH = re.compile(r"^(?:[0-9a-f]{40}|[0-9a-f]{64})$")


@dataclass(frozen=True)
class PersistedPartition:
    key: str
    path: Path
    row_count: int
    byte_count: int
    sha256: str


@dataclass(frozen=True)
class ResumableFeatureArtifact:
    source_run_id: str
    source_run_directory: Path
    feature_artifact_id: UUID
    feature_set_version: str
    source_cutoff_utc: datetime
    row_count: int
    byte_count: int
    sha256: str
    quality_status: str
    quality_results: tuple[QualityResult, ...]
    partitions: tuple[PersistedPartition, ...]


@dataclass(frozen=True)
class FeaturePersistenceOutcome:
    artifact_run_id: str
    processing_run_id: UUID
    source_artifact_run_id: str
    feature_artifact_id: UUID
    feature_set_version: str
    partition_count: int
    row_count: int
    byte_count: int
    sha256: str
    quality_status: str
    attempt_no: int
    run_directory: Path

    def to_dict(self) -> dict[str, object]:
        return {
            "artifactRunId": self.artifact_run_id,
            "attemptNo": self.attempt_no,
            "featureArtifact": {
                "bytes": self.byte_count,
                "featureArtifactId": str(self.feature_artifact_id),
                "featureSetVersion": self.feature_set_version,
                "partitions": self.partition_count,
                "rows": self.row_count,
                "sha256": self.sha256,
                "sourceArtifactRunId": self.source_artifact_run_id,
            },
            "processingRunId": str(self.processing_run_id),
            "qualityStatus": self.quality_status,
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


def _load_json(path: Path, error_code: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ExtractionError(error_code, f"Could not read {path.name}") from error
    if not isinstance(value, dict):
        raise ExtractionError(error_code, f"{path.name} must contain a JSON object")
    return value


def _verify_source_checksums(source: Path) -> None:
    checksum_path = source / "checksums.sha256"
    try:
        lines = checksum_path.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        raise ExtractionError(
            "FEATURE_RESUME_CHECKSUMS_INVALID",
            "Source feature checksums are missing",
        ) from error
    covered: set[str] = set()
    for line in lines:
        parts = line.split("  ", 1)
        if len(parts) != 2 or not re.fullmatch(r"[0-9a-f]{64}", parts[0]):
            raise ExtractionError(
                "FEATURE_RESUME_CHECKSUMS_INVALID",
                "Source feature checksum file is malformed",
            )
        name = parts[1]
        if Path(name).name != name or name in covered:
            raise ExtractionError(
                "FEATURE_RESUME_CHECKSUMS_INVALID",
                "Source feature checksum coverage is unsafe or duplicated",
            )
        target = source / name
        if not target.is_file() or file_sha256(target) != parts[0]:
            raise ExtractionError(
                "FEATURE_RESUME_CHECKSUM_MISMATCH",
                "Source feature evidence does not match its checksum file",
                {"file": name},
            )
        covered.add(name)
    required = {
        "feature-manifest.json",
        "feature-quality.json",
        "run-manifest.json",
    }
    if not required.issubset(covered):
        raise ExtractionError(
            "FEATURE_RESUME_CHECKSUMS_INVALID",
            "Source feature checksums do not cover required evidence",
            {"missing": sorted(required - covered)},
        )


def _parse_utc(value: object, field: str) -> datetime:
    try:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError as error:
        raise ExtractionError(
            "FEATURE_RESUME_MANIFEST_INVALID",
            f"{field} must be an ISO-8601 timestamp",
        ) from error
    if parsed.tzinfo is None:
        raise ExtractionError(
            "FEATURE_RESUME_MANIFEST_INVALID",
            f"{field} must include a UTC offset",
        )
    return parsed


def _quality_result(value: object) -> QualityResult:
    if not isinstance(value, dict):
        raise ExtractionError(
            "FEATURE_RESUME_QUALITY_INVALID",
            "Feature quality results must be JSON objects",
        )
    try:
        return QualityResult(
            rule_code=str(value["ruleCode"]),
            severity=str(value["severity"]),
            result_status=str(value["resultStatus"]),
            records_checked=int(value["recordsChecked"]),
            records_breached=int(value["recordsBreached"]),
            metric_value=(
                None if value.get("metricValue") is None else float(value["metricValue"])
            ),
            threshold=dict(value.get("threshold", {})),
            details=dict(value.get("details", {})),
        )
    except (KeyError, TypeError, ValueError) as error:
        raise ExtractionError(
            "FEATURE_RESUME_QUALITY_INVALID",
            "Feature quality result fields are invalid",
        ) from error


def _resolve_source_run(root: Path, feature_run: str | Path) -> Path:
    supplied = Path(feature_run)
    if supplied.is_absolute():
        candidate = supplied.resolve()
    elif len(supplied.parts) == 1:
        candidate = (root / "runs" / "feature-build" / supplied).resolve()
    else:
        candidate = (root / supplied).resolve()
    expected_root = (root / "runs" / "feature-build").resolve()
    try:
        candidate.relative_to(expected_root)
    except ValueError as error:
        raise ExtractionError(
            "FEATURE_RESUME_PATH_INVALID",
            "Source feature run must remain under runs/feature-build",
        ) from error
    if not candidate.is_dir():
        raise ExtractionError(
            "FEATURE_RESUME_RUN_MISSING",
            "Source feature run directory does not exist",
        )
    return candidate


def load_resumable_feature_artifact(
    config: ProcessingConfig,
    *,
    root: Path,
    feature_run: str | Path,
) -> ResumableFeatureArtifact:
    source = _resolve_source_run(root, feature_run)
    _verify_source_checksums(source)
    run_manifest = _load_json(
        source / "run-manifest.json", "FEATURE_RESUME_RUN_MANIFEST_INVALID"
    )
    if (
        run_manifest.get("configHash") != config.config_hash
        or run_manifest.get("profile") != config.profile.name
        or run_manifest.get("runType") != "feature-build"
    ):
        raise ExtractionError(
            "FEATURE_RESUME_RUN_MISMATCH",
            "Source feature run does not match the active profile/config",
        )
    manifest = _load_json(
        source / "feature-manifest.json", "FEATURE_RESUME_MANIFEST_INVALID"
    )
    quality = _load_json(
        source / "feature-quality.json", "FEATURE_RESUME_QUALITY_INVALID"
    )
    quality_results_raw = quality.get("results")
    if not isinstance(quality_results_raw, list):
        raise ExtractionError(
            "FEATURE_RESUME_QUALITY_INVALID",
            "Feature quality report must contain results",
        )
    quality_results = tuple(_quality_result(item) for item in quality_results_raw)
    failed = [item.rule_code for item in quality_results if item.result_status == "FAIL"]
    if failed:
        raise DataQualityError(failed, str(source.name))

    raw_partitions = manifest.get("partitions")
    if not isinstance(raw_partitions, list) or not raw_partitions:
        raise ExtractionError(
            "FEATURE_RESUME_MANIFEST_INVALID",
            "Feature manifest must contain partitions",
        )
    try:
        row_count = int(manifest["rowCount"])
        artifact_sha256 = str(manifest["sha256"])
        artifact_id = UUID(str(manifest["featureArtifactId"]))
        feature_set_version = str(manifest["featureSetVersion"])
        quality_status = str(manifest["qualityStatus"])
        source_run_id = str(run_manifest["runId"])
    except (KeyError, TypeError, ValueError) as error:
        raise ExtractionError(
            "FEATURE_RESUME_MANIFEST_INVALID",
            "Feature manifest identity/count fields are invalid",
        ) from error
    if quality_status not in {"PASS", "WARN"} or quality.get(
        "overallStatus"
    ) != quality_status:
        raise ExtractionError(
            "FEATURE_RESUME_QUALITY_INVALID",
            "Feature manifest and quality status do not match",
        )
    if row_count > config.artifacts.maximum_rows_per_run:
        raise ExtractionError(
            "FEATURE_RUN_ROW_LIMIT_EXCEEDED",
            "Resumed feature artifact exceeds the configured cost guard",
            {
                "artifactRows": row_count,
                "maximumRows": config.artifacts.maximum_rows_per_run,
            },
        )
    if canonical_mapping_sha256({"partitions": raw_partitions}) != artifact_sha256:
        raise ExtractionError(
            "FEATURE_RESUME_DATASET_CHECKSUM_MISMATCH",
            "Feature dataset manifest checksum does not match",
        )

    try:
        import pyarrow.parquet as pq
    except ImportError as error:
        raise ExtractionError(
            "FEATURE_RUNTIME_MISSING",
            "pyarrow is required to validate feature Parquet artifacts",
        ) from error
    partitions: list[PersistedPartition] = []
    seen_paths: set[Path] = set()
    for value in raw_partitions:
        if not isinstance(value, dict):
            raise ExtractionError(
                "FEATURE_RESUME_MANIFEST_INVALID",
                "Feature partitions must be JSON objects",
            )
        try:
            relative = Path(str(value["file"]))
            expected_rows = int(value["rows"])
            expected_bytes = int(value["bytes"])
            expected_sha256 = str(value["sha256"])
            key = str(value["key"])
        except (KeyError, TypeError, ValueError) as error:
            raise ExtractionError(
                "FEATURE_RESUME_MANIFEST_INVALID",
                "Feature partition fields are invalid",
            ) from error
        path = (source / relative).resolve()
        try:
            path.relative_to(source)
        except ValueError as error:
            raise ExtractionError(
                "FEATURE_RESUME_PATH_INVALID",
                "Feature partition path escapes its source run",
            ) from error
        if path in seen_paths or not path.is_file():
            raise ExtractionError(
                "FEATURE_RESUME_PARTITION_INVALID",
                "Feature partition is missing or duplicated",
                {"partition": key},
            )
        seen_paths.add(path)
        actual_bytes = path.stat().st_size
        if actual_bytes != expected_bytes or file_sha256(path) != expected_sha256:
            raise ExtractionError(
                "FEATURE_RESUME_PARTITION_CHECKSUM_MISMATCH",
                "Feature partition does not match its immutable manifest",
                {"partition": key},
            )
        try:
            actual_rows = int(pq.ParquetFile(path).metadata.num_rows)
        except Exception as error:
            raise ExtractionError(
                "FEATURE_RESUME_PARTITION_INVALID",
                "Feature partition is not readable Parquet",
                {"partition": key},
            ) from error
        if actual_rows != expected_rows:
            raise ExtractionError(
                "FEATURE_RESUME_PARTITION_CHECKSUM_MISMATCH",
                "Feature partition row count does not match its immutable manifest",
                {"partition": key},
            )
        if expected_rows > config.artifacts.maximum_rows_per_partition:
            raise ExtractionError(
                "FEATURE_PARTITION_ROW_LIMIT_EXCEEDED",
                "Resumed feature partition exceeds the configured cost guard",
                {"partition": key, "projectedRows": expected_rows},
            )
        partitions.append(
            PersistedPartition(
                key=key,
                path=path,
                row_count=expected_rows,
                byte_count=expected_bytes,
                sha256=expected_sha256,
            )
        )
    if sum(item.row_count for item in partitions) != row_count:
        raise ExtractionError(
            "FEATURE_RESUME_ROW_COUNT_MISMATCH",
            "Feature partition rows do not match the dataset manifest",
        )
    return ResumableFeatureArtifact(
        source_run_id=source_run_id,
        source_run_directory=source,
        feature_artifact_id=artifact_id,
        feature_set_version=feature_set_version,
        source_cutoff_utc=_parse_utc(manifest.get("sourceCutoffUtc"), "sourceCutoffUtc"),
        row_count=row_count,
        byte_count=sum(item.byte_count for item in partitions),
        sha256=artifact_sha256,
        quality_status=quality_status,
        quality_results=quality_results,
        partitions=tuple(partitions),
    )


def run_feature_persistence(
    config: ProcessingConfig,
    *,
    feature_run: str | Path,
    environment: Mapping[str, str] | None = None,
    identity: RunIdentity | None = None,
    connection_factory: Callable[..., Any] = connect_database,
    repository_factory: Callable[[Any], ProcessingRunRepository] = (
        PostgresProcessingRunRepository
    ),
    feature_repository_factory: Callable[[Any], Any] = PostgresFeatureRepository,
) -> FeaturePersistenceOutcome:
    environment = environment or os.environ
    root_variable = config.dataset.root_env or "GORIDE_ANALYTICS_DATA_ROOT"
    raw_root = environment.get(root_variable, "").strip()
    if not raw_root:
        raise ExtractionError(
            "FEATURE_ARTIFACT_ROOT_MISSING",
            f"Environment variable {root_variable} is required",
        )
    root = Path(raw_root).expanduser().resolve()
    if not root.is_dir():
        raise ExtractionError(
            "FEATURE_ARTIFACT_ROOT_INVALID",
            "Analytics data root does not exist or is not a directory",
        )
    artifact = load_resumable_feature_artifact(
        config, root=root, feature_run=feature_run
    )
    identity = identity or build_run_identity(config, "feature-persist")
    if not _COMMIT_HASH.fullmatch(identity.git_commit):
        raise ExtractionError(
            "CODE_COMMIT_UNAVAILABLE",
            "A full Git commit hash is required for persistent feature evidence",
        )
    run_directory = allocate_run_directory(root, identity)
    write_run_manifest(run_directory, identity)
    source_manifest_sha256 = file_sha256(
        artifact.source_run_directory / "feature-manifest.json"
    )
    _write_json(
        run_directory / "source-feature-artifact.json",
        {
            "featureArtifactId": str(artifact.feature_artifact_id),
            "featureDatasetSha256": artifact.sha256,
            "featureManifestSha256": source_manifest_sha256,
            "featureSetVersion": artifact.feature_set_version,
            "partitions": len(artifact.partitions),
            "rows": artifact.row_count,
            "sourceArtifactRunId": artifact.source_run_id,
            "strategy": "POSTGRES_TEMP_STAGING_COPY_V1",
        },
    )
    connection = None
    repository = None
    db_run_id = processing_run_id(identity)
    started = False
    terminal = False
    attempt_no = 0
    try:
        settings = DatabaseSettings.from_environment(environment)
        connection = connection_factory(settings, read_only=False)
        verify_database(connection, require_forecast_schema=True)
        repository = repository_factory(connection)
        attempt_no = repository.start(
            run_id=db_run_id,
            run_type="FEATURE_BUILD",
            identity=identity,
            source_profile=config.profile.name,
            dataset_version=config.dataset.version,
            source_cutoff=artifact.source_cutoff_utc,
            input_manifest={
                "featureArtifactId": str(artifact.feature_artifact_id),
                "featureDatasetSha256": artifact.sha256,
                "featureManifestSha256": source_manifest_sha256,
                "featureSetVersion": artifact.feature_set_version,
                "partitions": len(artifact.partitions),
                "resumedFromArtifactRunId": artifact.source_run_id,
                "rows": artifact.row_count,
                "strategy": "POSTGRES_TEMP_STAGING_COPY_V1",
            },
        )
        started = True
        repository.save_quality(db_run_id, artifact.quality_results)
        feature_repository = feature_repository_factory(connection)
        persisted = 0
        with connection.transaction():
            for partition in artifact.partitions:
                persisted += feature_repository.upsert(
                    iter_parquet_rows(partition.path),
                    created_by_run_id=db_run_id,
                )
            if persisted != artifact.row_count:
                raise ExtractionError(
                    "FEATURE_PERSIST_COUNT_MISMATCH",
                    "Persisted feature-row count differs from the artifact",
                    {"artifactRows": artifact.row_count, "persistedRows": persisted},
                )
            repository.succeed(
                db_run_id,
                rows_read=artifact.row_count,
                rows_written=persisted,
            )
        terminal = True
        _write_json(
            run_directory / "persistence-manifest.json",
            {
                "featureArtifactId": str(artifact.feature_artifact_id),
                "featureSetVersion": artifact.feature_set_version,
                "partitions": len(artifact.partitions),
                "processingRunId": str(db_run_id),
                "qualityStatus": artifact.quality_status,
                "rowsPersisted": persisted,
                "sourceArtifactRunId": artifact.source_run_id,
                "status": "SUCCEEDED",
                "strategy": "POSTGRES_TEMP_STAGING_COPY_V1",
            },
        )
        _write_checksums(run_directory)
        return FeaturePersistenceOutcome(
            artifact_run_id=identity.run_id,
            processing_run_id=db_run_id,
            source_artifact_run_id=artifact.source_run_id,
            feature_artifact_id=artifact.feature_artifact_id,
            feature_set_version=artifact.feature_set_version,
            partition_count=len(artifact.partitions),
            row_count=artifact.row_count,
            byte_count=artifact.byte_count,
            sha256=artifact.sha256,
            quality_status=artifact.quality_status,
            attempt_no=attempt_no,
            run_directory=run_directory,
        )
    except AnalyticsError as error:
        if started and not terminal and repository is not None:
            try:
                repository.fail(
                    db_run_id,
                    rows_read=artifact.row_count,
                    rows_written=0,
                    error_code=error.code,
                    error_message=error.message,
                )
            except AnalyticsError:
                pass
        _write_json(
            run_directory / "persistence-failure.json",
            {"errorCode": error.code, "message": error.message, "status": "FAILED"},
        )
        _write_checksums(run_directory)
        raise
    except Exception as error:
        wrapped = ExtractionError(
            "FEATURE_PERSIST_UNEXPECTED_FAILURE",
            "Unexpected failure while persisting a feature artifact",
            {"errorType": type(error).__name__},
        )
        if started and not terminal and repository is not None:
            try:
                repository.fail(
                    db_run_id,
                    rows_read=artifact.row_count,
                    rows_written=0,
                    error_code=wrapped.code,
                    error_message=wrapped.message,
                )
            except AnalyticsError:
                pass
        _write_json(
            run_directory / "persistence-failure.json",
            {
                "errorCode": wrapped.code,
                "message": wrapped.message,
                "status": "FAILED",
            },
        )
        _write_checksums(run_directory)
        raise wrapped from error
    finally:
        if connection is not None:
            connection.close()
