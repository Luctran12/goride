from __future__ import annotations

import json
import uuid
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Iterable, Mapping, Protocol, Sequence

from .errors import DatabaseError
from .quality import QualityResult
from .runs import RunIdentity
from .features.model import FeatureRow


PROCESSING_RUN_NAMESPACE = uuid.UUID("30c041c7-f162-4f3c-aad2-e7cfcc7e5190")
PROCESSING_RUN_TYPES = {
    "EXTRACTION",
    "QUALITY",
    "FEATURE_BUILD",
    "TRAINING",
    "EVALUATION",
    "FORECAST",
    "ACTUAL_BACKFILL",
}


@dataclass(frozen=True)
class DatabaseSettings:
    host: str
    port: int
    database: str
    username: str
    password: str = field(repr=False)
    sslmode: str = "prefer"

    @classmethod
    def from_environment(
        cls,
        environment: Mapping[str, str],
        prefix: str = "ANALYTICS_DATABASE",
    ) -> DatabaseSettings:
        names = {
            "host": f"{prefix}_HOST",
            "port": f"{prefix}_PORT",
            "database": f"{prefix}_NAME",
            "username": f"{prefix}_USERNAME",
            "password": f"{prefix}_PASSWORD",
        }
        values = {key: environment.get(name, "").strip() for key, name in names.items()}
        missing = [names[key] for key, value in values.items() if not value]
        if missing:
            raise DatabaseError(
                "DATABASE_ENV_MISSING",
                "Analytics database environment is incomplete",
                {"missing": sorted(missing), "prefix": prefix},
            )
        try:
            port = int(values["port"])
        except ValueError as error:
            raise DatabaseError(
                "DATABASE_PORT_INVALID",
                "Analytics database port must be an integer",
                {"environmentVariable": names["port"]},
            ) from error
        if not 1 <= port <= 65535:
            raise DatabaseError(
                "DATABASE_PORT_INVALID",
                "Analytics database port must be between 1 and 65535",
                {"environmentVariable": names["port"]},
            )
        sslmode = environment.get(f"{prefix}_SSLMODE", "prefer").strip() or "prefer"
        if sslmode not in {
            "disable",
            "allow",
            "prefer",
            "require",
            "verify-ca",
            "verify-full",
        }:
            raise DatabaseError(
                "DATABASE_SSLMODE_INVALID",
                "Analytics database sslmode is unsupported",
                {"environmentVariable": f"{prefix}_SSLMODE"},
            )
        return cls(
            host=values["host"],
            port=port,
            database=values["database"],
            username=values["username"],
            password=values["password"],
            sslmode=sslmode,
        )


@dataclass(frozen=True)
class DatabaseMetadata:
    database: str
    server_version: str
    postgis_version: str

    def to_dict(self) -> dict[str, str]:
        return {
            "database": self.database,
            "postgisVersion": self.postgis_version,
            "serverVersion": self.server_version,
        }


def connect_database(settings: DatabaseSettings, *, read_only: bool = False) -> Any:
    try:
        import psycopg

        connection = psycopg.connect(
            host=settings.host,
            port=settings.port,
            dbname=settings.database,
            user=settings.username,
            password=settings.password,
            sslmode=settings.sslmode,
            connect_timeout=10,
            application_name="goride-analytics-processing",
        )
        connection.read_only = read_only
        if read_only:
            connection.isolation_level = psycopg.IsolationLevel.REPEATABLE_READ
        return connection
    except ImportError as error:
        raise DatabaseError(
            "DATABASE_DRIVER_MISSING",
            "Psycopg is not installed; install requirements/runtime.lock",
        ) from error
    except Exception as error:
        raise DatabaseError(
            "DATABASE_CONNECTION_FAILED",
            "Could not connect to the analytics database",
            {
                "database": settings.database,
                "host": settings.host,
                "port": settings.port,
            },
        ) from error


def verify_database(connection: Any, *, require_forecast_schema: bool) -> DatabaseMetadata:
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT
                    current_database(),
                    current_setting('server_version'),
                    PostGIS_Full_Version()
                """
            )
            database, server_version, postgis_version = cursor.fetchone()
            if require_forecast_schema:
                cursor.execute(
                    """
                    SELECT COUNT(*)
                    FROM unnest(ARRAY[
                        'analytics.processing_runs',
                        'analytics.data_quality_results'
                    ]) AS expected(object_name)
                    WHERE to_regclass(expected.object_name) IS NOT NULL
                    """
                )
                if cursor.fetchone()[0] != 2:
                    raise DatabaseError(
                        "DATABASE_SCHEMA_MISSING",
                        "Phase 2 processing and quality tables are required",
                        {"database": database},
                    )
        connection.rollback()
        return DatabaseMetadata(
            database=str(database),
            server_version=str(server_version),
            postgis_version=str(postgis_version),
        )
    except DatabaseError:
        raise
    except Exception as error:
        connection.rollback()
        raise DatabaseError(
            "DATABASE_COMPATIBILITY_FAILED",
            "PostgreSQL/PostGIS compatibility validation failed",
        ) from error


def processing_run_id(identity: RunIdentity) -> uuid.UUID:
    return uuid.uuid5(PROCESSING_RUN_NAMESPACE, identity.run_id)


class ProcessingRunRepository(Protocol):
    def start(
        self,
        *,
        run_id: uuid.UUID,
        run_type: str,
        identity: RunIdentity,
        source_profile: str,
        dataset_version: str,
        source_cutoff: datetime,
        input_manifest: Mapping[str, Any],
    ) -> int: ...

    def save_quality(
        self,
        run_id: uuid.UUID,
        results: Sequence[QualityResult],
    ) -> None: ...

    def succeed(self, run_id: uuid.UUID, *, rows_read: int, rows_written: int) -> None: ...

    def fail(
        self,
        run_id: uuid.UUID,
        *,
        rows_read: int,
        rows_written: int,
        error_code: str,
        error_message: str,
    ) -> None: ...


class PostgresProcessingRunRepository:
    def __init__(self, connection: Any) -> None:
        self._connection = connection

    def start(
        self,
        *,
        run_id: uuid.UUID,
        run_type: str,
        identity: RunIdentity,
        source_profile: str,
        dataset_version: str,
        source_cutoff: datetime,
        input_manifest: Mapping[str, Any],
    ) -> int:
        if run_type not in PROCESSING_RUN_TYPES:
            raise DatabaseError(
                "PROCESSING_RUN_TYPE_INVALID",
                "Processing run type is not supported",
                {"runType": run_type},
            )
        try:
            with self._connection.transaction():
                with self._connection.cursor() as cursor:
                    cursor.execute(
                        """
                        SELECT COALESCE(MAX(attempt_no), 0) + 1
                        FROM analytics.processing_runs
                        WHERE run_type = %s
                          AND source_profile = %s
                          AND dataset_version = %s
                          AND config_hash = %s
                          AND source_cutoff = %s
                        """,
                        (
                            run_type,
                            source_profile,
                            dataset_version,
                            identity.config_hash,
                            source_cutoff,
                        ),
                    )
                    attempt_no = int(cursor.fetchone()[0])
                    cursor.execute(
                        """
                        INSERT INTO analytics.processing_runs (
                            run_id,
                            artifact_run_id,
                            run_type,
                            status,
                            source_profile,
                            dataset_version,
                            source_cutoff,
                            config_hash,
                            code_commit,
                            input_manifest,
                            attempt_no,
                            created_at,
                            started_at
                        ) VALUES (
                            %s, %s, %s, 'RUNNING', %s, %s, %s,
                            %s, %s, %s::JSONB, %s, %s, CURRENT_TIMESTAMP
                        )
                        """,
                        (
                            run_id,
                            identity.run_id,
                            run_type,
                            source_profile,
                            dataset_version,
                            source_cutoff,
                            identity.config_hash,
                            identity.git_commit,
                            json.dumps(input_manifest, sort_keys=True),
                            attempt_no,
                            identity.created_at_utc,
                        ),
                    )
            return attempt_no
        except Exception as error:
            raise DatabaseError(
                "PROCESSING_RUN_START_FAILED",
                "Could not persist the processing run start",
                {"processingRunId": str(run_id)},
            ) from error

    def save_quality(
        self,
        run_id: uuid.UUID,
        results: Sequence[QualityResult],
    ) -> None:
        try:
            with self._connection.transaction():
                with self._connection.cursor() as cursor:
                    cursor.executemany(
                        """
                        INSERT INTO analytics.data_quality_results (
                            run_id,
                            rule_code,
                            scope_key,
                            severity,
                            result_status,
                            records_checked,
                            records_breached,
                            metric_value,
                            threshold,
                            details,
                            evaluated_at
                        ) VALUES (
                            %s, %s, 'GLOBAL', %s, %s, %s, %s, %s,
                            %s::JSONB, %s::JSONB, CURRENT_TIMESTAMP
                        )
                        """,
                        [
                            (
                                run_id,
                                result.rule_code,
                                result.severity,
                                result.result_status,
                                result.records_checked,
                                result.records_breached,
                                result.metric_value,
                                json.dumps(result.threshold, sort_keys=True),
                                json.dumps(result.details, sort_keys=True),
                            )
                            for result in results
                        ],
                    )
        except Exception as error:
            raise DatabaseError(
                "QUALITY_RESULTS_PERSIST_FAILED",
                "Could not persist data-quality results",
                {"processingRunId": str(run_id)},
            ) from error

    def succeed(self, run_id: uuid.UUID, *, rows_read: int, rows_written: int) -> None:
        self._finish(
            run_id,
            status="SUCCEEDED",
            rows_read=rows_read,
            rows_written=rows_written,
            error_code=None,
            error_message=None,
        )

    def fail(
        self,
        run_id: uuid.UUID,
        *,
        rows_read: int,
        rows_written: int,
        error_code: str,
        error_message: str,
    ) -> None:
        self._finish(
            run_id,
            status="FAILED",
            rows_read=rows_read,
            rows_written=rows_written,
            error_code=error_code[:80],
            error_message=error_message[:2000],
        )

    def _finish(
        self,
        run_id: uuid.UUID,
        *,
        status: str,
        rows_read: int,
        rows_written: int,
        error_code: str | None,
        error_message: str | None,
    ) -> None:
        try:
            with self._connection.transaction():
                with self._connection.cursor() as cursor:
                    cursor.execute(
                        """
                        UPDATE analytics.processing_runs
                        SET status = %s,
                            rows_read = %s,
                            rows_written = %s,
                            finished_at = CURRENT_TIMESTAMP,
                            error_code = %s,
                            error_message = %s
                        WHERE run_id = %s
                          AND status = 'RUNNING'
                        """,
                        (
                            status,
                            rows_read,
                            rows_written,
                            error_code,
                            error_message,
                            run_id,
                        ),
                    )
                    if cursor.rowcount != 1:
                        raise RuntimeError("processing run is not RUNNING")
        except Exception as error:
            raise DatabaseError(
                "PROCESSING_RUN_FINISH_FAILED",
                "Could not persist the processing run terminal state",
                {"processingRunId": str(run_id), "status": status},
            ) from error


class PostgresFeatureRepository:
    def __init__(self, connection: Any) -> None:
        self._connection = connection

    @staticmethod
    def _values(row: FeatureRow, created_by_run_id: uuid.UUID) -> tuple[Any, ...]:
        return (
            row.feature_set_version,
            row.source_profile,
            row.dataset_version,
            row.demand_event_semantics,
            row.grid_version,
            row.projected_srid,
            row.cell_id,
            row.grid_x,
            row.grid_y,
            row.cell_size_meters,
            row.bucket_start_utc,
            row.inference_cutoff_utc,
            row.target_bucket_start_utc,
            row.horizon_minutes,
            row.target_trip_requests,
            row.lag_1,
            row.lag_2,
            row.lag_4,
            row.lag_96,
            row.lag_672,
            row.rolling_mean_4,
            row.rolling_mean_12,
            row.rolling_mean_96,
            row.rolling_mean_672,
            row.hour_sin,
            row.hour_cos,
            row.day_of_week,
            row.is_weekend,
            row.neighbor_demand_lag_1,
            row.available_driver_lag_1,
            row.coverage_ratio,
            row.quality_status,
            created_by_run_id,
        )

    def upsert(
        self,
        rows: Iterable[FeatureRow],
        *,
        created_by_run_id: uuid.UUID,
        batch_size: int = 2_000,
    ) -> int:
        statement = """
            INSERT INTO analytics.demand_features (
                feature_set_version, source_profile, dataset_version,
                demand_event_semantics, grid_version, projected_srid,
                cell_id, grid_x, grid_y, cell_size_meters,
                bucket_start_utc, inference_cutoff_utc,
                target_bucket_start_utc, horizon_minutes,
                target_trip_requests, lag_1, lag_2, lag_4, lag_96, lag_672,
                rolling_mean_4, rolling_mean_12, rolling_mean_96,
                rolling_mean_672, hour_sin, hour_cos, day_of_week,
                is_weekend, neighbor_demand_lag_1, available_driver_lag_1,
                coverage_ratio, quality_status, created_by_run_id
            ) VALUES (
                %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                %s, %s, %s
            )
            ON CONFLICT (
                feature_set_version, source_profile, dataset_version,
                grid_version, cell_id, bucket_start_utc,
                inference_cutoff_utc, horizon_minutes
            ) DO UPDATE SET
                demand_event_semantics = EXCLUDED.demand_event_semantics,
                projected_srid = EXCLUDED.projected_srid,
                grid_x = EXCLUDED.grid_x,
                grid_y = EXCLUDED.grid_y,
                cell_size_meters = EXCLUDED.cell_size_meters,
                target_bucket_start_utc = EXCLUDED.target_bucket_start_utc,
                target_trip_requests = EXCLUDED.target_trip_requests,
                lag_1 = EXCLUDED.lag_1,
                lag_2 = EXCLUDED.lag_2,
                lag_4 = EXCLUDED.lag_4,
                lag_96 = EXCLUDED.lag_96,
                lag_672 = EXCLUDED.lag_672,
                rolling_mean_4 = EXCLUDED.rolling_mean_4,
                rolling_mean_12 = EXCLUDED.rolling_mean_12,
                rolling_mean_96 = EXCLUDED.rolling_mean_96,
                rolling_mean_672 = EXCLUDED.rolling_mean_672,
                hour_sin = EXCLUDED.hour_sin,
                hour_cos = EXCLUDED.hour_cos,
                day_of_week = EXCLUDED.day_of_week,
                is_weekend = EXCLUDED.is_weekend,
                neighbor_demand_lag_1 = EXCLUDED.neighbor_demand_lag_1,
                available_driver_lag_1 = EXCLUDED.available_driver_lag_1,
                coverage_ratio = EXCLUDED.coverage_ratio,
                quality_status = EXCLUDED.quality_status,
                created_by_run_id = EXCLUDED.created_by_run_id,
                created_at = CURRENT_TIMESTAMP
        """
        written = 0
        batch: list[tuple[Any, ...]] = []
        try:
            with self._connection.transaction():
                with self._connection.cursor() as cursor:
                    for row in rows:
                        batch.append(self._values(row, created_by_run_id))
                        if len(batch) >= batch_size:
                            cursor.executemany(statement, batch)
                            written += len(batch)
                            batch.clear()
                    if batch:
                        cursor.executemany(statement, batch)
                        written += len(batch)
            return written
        except DatabaseError:
            raise
        except Exception as error:
            raise DatabaseError(
                "FEATURE_ROWS_PERSIST_FAILED",
                "Could not persist demand-feature rows",
                {"rowsWrittenBeforeFailure": written},
            ) from error
