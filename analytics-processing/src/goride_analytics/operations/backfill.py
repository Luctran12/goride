from __future__ import annotations

import json
import os
import re
import shutil
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Mapping, Sequence

from ..config import ProcessingConfig
from ..database import (
    DatabaseSettings,
    PostgresProcessingRunRepository,
    ProcessingRunRepository,
    connect_database,
    processing_run_id,
    verify_database,
)
from ..errors import AnalyticsError, DataQualityError, DatabaseError, ExtractionError
from ..hashing import file_sha256
from ..quality import QualityResult
from ..runs import RunIdentity, allocate_run_directory, build_run_identity, write_run_manifest
from .artifact import analytics_root
from .config import load_operations_config
from .forecast import parse_inference_cutoff


_COMMIT_HASH = re.compile(r"^(?:[0-9a-f]{40}|[0-9a-f]{64})$")


@dataclass(frozen=True)
class BackfillSource:
    forecast_run_id: uuid.UUID
    model_version: str
    status: str
    forecast_rows: int
    already_evaluated_rows: int
    earliest_target_utc: datetime


@dataclass(frozen=True)
class ActualBackfillOutcome:
    artifact_run_id: str
    processing_run_id: uuid.UUID
    forecast_run_id: uuid.UUID
    watermark_utc: datetime
    eligible_rows: int
    updated_rows: int
    already_evaluated_rows: int
    quality_status: str
    attempt_no: int
    run_directory: Path

    def to_dict(self) -> dict[str, object]:
        return {
            "actualBackfill": {
                "alreadyEvaluatedRows": self.already_evaluated_rows,
                "eligibleRows": self.eligible_rows,
                "forecastRunId": str(self.forecast_run_id),
                "updatedRows": self.updated_rows,
                "watermarkUtc": self.watermark_utc.isoformat().replace(
                    "+00:00", "Z"
                ),
            },
            "artifactRunId": self.artifact_run_id,
            "attemptNo": self.attempt_no,
            "processingRunId": str(self.processing_run_id),
            "qualityStatus": self.quality_status,
        }


def _write_json(path: Path, value: object) -> None:
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


class PostgresActualBackfillRepository:
    def __init__(self, connection: Any) -> None:
        self._connection = connection

    def source(self, forecast_run_id: uuid.UUID) -> BackfillSource:
        with self._connection.transaction():
            with self._connection.cursor() as cursor:
                cursor.execute(
                """
                SELECT f.forecast_run_id, m.model_version, f.status,
                       COUNT(d.demand_forecast_id),
                       COUNT(d.evaluated_at),
                       MIN(d.target_bucket_start_utc)
                FROM analytics.forecast_runs f
                JOIN analytics.model_versions m
                  ON m.model_version_id = f.model_version_id
                JOIN analytics.demand_forecasts d
                  ON d.forecast_run_id = f.forecast_run_id
                WHERE f.forecast_run_id = %s
                GROUP BY f.forecast_run_id, m.model_version, f.status
                """,
                    (forecast_run_id,),
                )
                row = cursor.fetchone()
        if row is None:
            raise DatabaseError(
                "BACKFILL_FORECAST_RUN_NOT_FOUND",
                "Forecast run with published rows does not exist",
            )
        if row[2] not in {"SUCCEEDED", "PUBLISHED"}:
            raise DatabaseError(
                "BACKFILL_FORECAST_RUN_INVALID",
                "Only successful forecast runs may be evaluated",
                {"status": row[2]},
            )
        return BackfillSource(
            forecast_run_id=row[0],
            model_version=str(row[1]),
            status=str(row[2]),
            forecast_rows=int(row[3]),
            already_evaluated_rows=int(row[4]),
            earliest_target_utc=row[5],
        )

    def counts(
        self,
        forecast_run_id: uuid.UUID,
        watermark: datetime,
        watermark_delay_minutes: int,
    ) -> tuple[int, int]:
        with self._connection.transaction():
            with self._connection.cursor() as cursor:
                cursor.execute(
                """
                SELECT
                    COUNT(*) FILTER (
                        WHERE d.target_bucket_start_utc
                              + %s * INTERVAL '1 minute' <= %s
                          AND d.evaluated_at IS NULL
                    ),
                    COUNT(*) FILTER (
                        WHERE d.target_bucket_start_utc
                              + %s * INTERVAL '1 minute' <= %s
                          AND d.evaluated_at IS NULL
                          AND f.demand_feature_id IS NOT NULL
                    )
                FROM analytics.demand_forecasts d
                JOIN analytics.forecast_runs r
                  ON r.forecast_run_id = d.forecast_run_id
                JOIN analytics.model_versions m
                  ON m.model_version_id = r.model_version_id
                LEFT JOIN analytics.demand_features f
                  ON f.feature_set_version = m.feature_set_version
                 AND f.source_profile = r.source_profile
                 AND f.dataset_version = r.dataset_version
                 AND f.grid_version = r.grid_version
                 AND f.cell_size_meters = r.cell_size_meters
                 AND f.cell_id = d.cell_id
                 AND f.inference_cutoff_utc = d.inference_cutoff_utc
                 AND f.target_bucket_start_utc = d.target_bucket_start_utc
                 AND f.horizon_minutes = d.horizon_minutes
                WHERE d.forecast_run_id = %s
                """,
                    (
                        watermark_delay_minutes,
                        watermark,
                        watermark_delay_minutes,
                        watermark,
                        forecast_run_id,
                    ),
                )
                row = cursor.fetchone()
        return int(row[0]), int(row[1])

    def backfill(
        self,
        *,
        forecast_run_id: uuid.UUID,
        watermark: datetime,
        watermark_delay_minutes: int,
        evaluated_at: datetime,
        processing_run_id_value: uuid.UUID,
        quality_results: Sequence[QualityResult],
        processing_repository: ProcessingRunRepository,
        eligible_rows: int,
    ) -> int:
        try:
            with self._connection.transaction():
                with self._connection.cursor() as cursor:
                    cursor.execute(
                        """
                        UPDATE analytics.demand_forecasts d
                        SET actual_demand = f.target_trip_requests,
                            absolute_error = ABS(
                                d.predicted_demand
                                - f.target_trip_requests::NUMERIC
                            ),
                            evaluated_at = %s
                        FROM analytics.forecast_runs r
                        JOIN analytics.model_versions m
                          ON m.model_version_id = r.model_version_id
                        JOIN analytics.demand_features f
                          ON f.feature_set_version = m.feature_set_version
                         AND f.source_profile = r.source_profile
                         AND f.dataset_version = r.dataset_version
                         AND f.grid_version = r.grid_version
                         AND f.cell_size_meters = r.cell_size_meters
                        WHERE d.forecast_run_id = %s
                          AND r.forecast_run_id = d.forecast_run_id
                          AND f.cell_id = d.cell_id
                          AND f.inference_cutoff_utc = d.inference_cutoff_utc
                          AND f.target_bucket_start_utc = d.target_bucket_start_utc
                          AND f.horizon_minutes = d.horizon_minutes
                          AND d.evaluated_at IS NULL
                          AND d.target_bucket_start_utc
                              + %s * INTERVAL '1 minute' <= %s
                        """,
                        (
                            evaluated_at,
                            forecast_run_id,
                            watermark_delay_minutes,
                            watermark,
                        ),
                    )
                    updated = int(cursor.rowcount)
                    if updated != eligible_rows:
                        raise DatabaseError(
                            "BACKFILL_SOURCE_COUNT_MISMATCH",
                            "Actual source rows changed during atomic backfill",
                            {"eligibleRows": eligible_rows, "updatedRows": updated},
                        )
                processing_repository.save_quality(
                    processing_run_id_value,
                    quality_results,
                )
                processing_repository.succeed(
                    processing_run_id_value,
                    rows_read=eligible_rows,
                    rows_written=updated,
                )
            return updated
        except DatabaseError:
            raise
        except Exception as error:
            raise DatabaseError(
                "BACKFILL_PUBLICATION_FAILED",
                "Actual/error backfill failed and was rolled back",
            ) from error


def run_actual_backfill(
    config: ProcessingConfig,
    *,
    operations_config: str | Path,
    forecast_run: str,
    watermark_utc: str,
    environment: Mapping[str, str] | None = None,
    now: datetime | None = None,
    identity: RunIdentity | None = None,
    connection_factory: Callable[..., Any] = connect_database,
    processing_repository_factory: Callable[[Any], ProcessingRunRepository] = PostgresProcessingRunRepository,
    backfill_repository_factory: Callable[[Any], PostgresActualBackfillRepository] = PostgresActualBackfillRepository,
) -> ActualBackfillOutcome:
    environment = environment or os.environ
    now = (now or datetime.now(timezone.utc)).astimezone(timezone.utc)
    operations = load_operations_config(operations_config)
    root = analytics_root(config, environment)
    watermark = parse_inference_cutoff(
        watermark_utc,
        config.temporal.bucket_minutes,
    )
    try:
        forecast_run_id = uuid.UUID(forecast_run)
    except ValueError as error:
        raise ExtractionError(
            "BACKFILL_FORECAST_RUN_INVALID",
            "Forecast run must be a UUID",
        ) from error
    settings = DatabaseSettings.from_environment(environment)
    connection = connection_factory(settings, read_only=False)
    processing_repository: ProcessingRunRepository | None = None
    db_run_id: uuid.UUID | None = None
    started = False
    terminal = False
    eligible = 0
    run_directory: Path | None = None
    try:
        verify_database(connection, require_forecast_schema=True)
        processing_repository = processing_repository_factory(connection)
        repository = backfill_repository_factory(connection)
        source = repository.source(forecast_run_id)
        if watermark < source.earliest_target_utc:
            raise ExtractionError(
                "BACKFILL_WATERMARK_TOO_EARLY",
                "Watermark is earlier than the first forecast target",
            )
        identity = identity or build_run_identity(config, "actual-backfill")
        if not _COMMIT_HASH.fullmatch(identity.git_commit):
            raise ExtractionError(
                "CODE_COMMIT_UNAVAILABLE",
                "A full Git commit hash is required for backfill evidence",
            )
        run_directory = allocate_run_directory(root, identity)
        write_run_manifest(run_directory, identity)
        shutil.copyfile(operations.path, run_directory / "operations-config.yml")
        db_run_id = processing_run_id(identity)
        attempt_no = processing_repository.start(
            run_id=db_run_id,
            run_type="ACTUAL_BACKFILL",
            identity=identity,
            source_profile=config.profile.name,
            dataset_version=config.dataset.version,
            source_cutoff=watermark,
            input_manifest={
                "forecastRunId": str(forecast_run_id),
                "modelVersion": source.model_version,
                "operationsConfigHash": operations.config_hash,
                "watermarkDelayMinutes": operations.actual_watermark_delay_minutes,
                "watermarkUtc": watermark.isoformat().replace("+00:00", "Z"),
            },
        )
        started = True
        eligible, matched = repository.counts(
            forecast_run_id,
            watermark,
            operations.actual_watermark_delay_minutes,
        )
        missing = eligible - matched
        quality_results = (
            QualityResult(
                "BACKFILL_WATERMARK_CLOSED",
                "FAIL",
                "PASS" if eligible > 0 or source.already_evaluated_rows > 0 else "FAIL",
                source.forecast_rows,
                0 if eligible > 0 or source.already_evaluated_rows > 0 else 1,
                details={
                    "watermarkDelayMinutes": operations.actual_watermark_delay_minutes
                },
            ),
            QualityResult(
                "BACKFILL_ACTUAL_SOURCE_COMPLETE",
                "FAIL",
                "PASS" if missing == 0 else "FAIL",
                eligible,
                missing,
            ),
        )
        failed = [item.rule_code for item in quality_results if item.result_status == "FAIL"]
        _write_json(
            run_directory / "backfill-quality.json",
            {
                "overallStatus": "FAIL" if failed else "PASS",
                "results": [item.to_dict() for item in quality_results],
            },
        )
        if failed:
            processing_repository.save_quality(db_run_id, quality_results)
            raise DataQualityError(failed, str(db_run_id))
        updated = repository.backfill(
            forecast_run_id=forecast_run_id,
            watermark=watermark,
            watermark_delay_minutes=operations.actual_watermark_delay_minutes,
            evaluated_at=now,
            processing_run_id_value=db_run_id,
            quality_results=quality_results,
            processing_repository=processing_repository,
            eligible_rows=eligible,
        )
        terminal = True
        _write_json(
            run_directory / "backfill-manifest.json",
            {
                "alreadyEvaluatedRows": source.already_evaluated_rows,
                "eligibleRows": eligible,
                "forecastRunId": str(forecast_run_id),
                "modelVersion": source.model_version,
                "qualityStatus": "PASS",
                "updatedRows": updated,
                "watermarkDelayMinutes": operations.actual_watermark_delay_minutes,
                "watermarkUtc": watermark.isoformat().replace("+00:00", "Z"),
            },
        )
        _write_checksums(run_directory)
        return ActualBackfillOutcome(
            artifact_run_id=identity.run_id,
            processing_run_id=db_run_id,
            forecast_run_id=forecast_run_id,
            watermark_utc=watermark,
            eligible_rows=eligible,
            updated_rows=updated,
            already_evaluated_rows=source.already_evaluated_rows,
            quality_status="PASS",
            attempt_no=attempt_no,
            run_directory=run_directory,
        )
    except Exception as error:
        if started and not terminal and processing_repository is not None and db_run_id is not None:
            code = error.code if isinstance(error, AnalyticsError) else "ACTUAL_BACKFILL_FAILED"
            try:
                processing_repository.fail(
                    db_run_id,
                    rows_read=eligible,
                    rows_written=0,
                    error_code=code,
                    error_message=str(error),
                )
            except Exception:
                pass
            if run_directory is not None:
                _write_json(
                    run_directory / "failure.json",
                    {"errorCode": code, "message": str(error)},
                )
                _write_checksums(run_directory)
        raise
    finally:
        connection.close()
