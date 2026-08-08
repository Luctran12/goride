from __future__ import annotations

import json
import uuid
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Mapping, Protocol, Sequence

from .errors import DatabaseError
from .quality import QualityResult
from .runs import RunIdentity


PROCESSING_RUN_NAMESPACE = uuid.UUID("30c041c7-f162-4f3c-aad2-e7cfcc7e5190")


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
        identity: RunIdentity,
        source_profile: str,
        dataset_version: str,
        source_cutoff: datetime,
        input_manifest: Mapping[str, Any],
    ) -> int:
        try:
            with self._connection.transaction():
                with self._connection.cursor() as cursor:
                    cursor.execute(
                        """
                        SELECT COALESCE(MAX(attempt_no), 0) + 1
                        FROM analytics.processing_runs
                        WHERE run_type = 'EXTRACTION'
                          AND source_profile = %s
                          AND dataset_version = %s
                          AND config_hash = %s
                          AND source_cutoff = %s
                        """,
                        (
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
                            %s, %s, 'EXTRACTION', 'RUNNING', %s, %s, %s,
                            %s, %s, %s::JSONB, %s, %s, CURRENT_TIMESTAMP
                        )
                        """,
                        (
                            run_id,
                            identity.run_id,
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
                "Could not persist the extraction run start",
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
                "Could not persist the extraction run terminal state",
                {"processingRunId": str(run_id), "status": status},
            ) from error
