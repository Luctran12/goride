from __future__ import annotations

import os
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

from goride_analytics.config import load_config
from goride_analytics.database import (
    DatabaseSettings,
    PostgresProcessingRunRepository,
    connect_database,
    processing_run_id,
)
from goride_analytics.errors import DatabaseError
from goride_analytics.extraction.pipeline import run_extraction
from goride_analytics.features.grid import GridDefinition
from goride_analytics.features.pipeline import run_feature_build
from goride_analytics.manifest import validate_dataset
from goride_analytics.runs import GitState, build_run_identity

from support import (
    create_porto_data_root,
    porto_row,
    valid_config_mapping,
    write_config,
)


class _FailingTerminalRepository(PostgresProcessingRunRepository):
    def succeed(self, run_id, *, rows_read, rows_written):
        raise DatabaseError(
            "TEST_TERMINAL_FAILURE",
            "Injected terminal-state failure",
        )


@unittest.skipUnless(
    os.environ.get("RUN_ANALYTICS_DB_INTEGRATION") == "1",
    "set RUN_ANALYTICS_DB_INTEGRATION=1 for PostgreSQL/PostGIS integration",
)
class DatabaseExtractionIntegrationTests(unittest.TestCase):
    def test_python_grid_matches_postgis_zero_origin_floor_contract(self) -> None:
        longitude = -8.61099
        latitude = 41.14557
        with tempfile.TemporaryDirectory() as temporary:
            config = load_config(write_config(Path(temporary) / "profile.yml"))
        grid = GridDefinition(config.spatial, 500)
        python_cell = grid.assign(longitude, latitude)
        self.assertIsNotNone(python_cell)
        assert python_cell is not None

        settings = DatabaseSettings.from_environment(dict(os.environ))
        connection = connect_database(settings)
        try:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    WITH projected AS (
                        SELECT ST_Transform(
                            ST_SetSRID(ST_MakePoint(%s, %s), %s),
                            %s
                        ) AS point
                    )
                    SELECT
                        FLOOR((ST_X(point) - %s) / %s)::BIGINT,
                        FLOOR((ST_Y(point) - %s) / %s)::BIGINT
                    FROM projected
                    """,
                    (
                        longitude,
                        latitude,
                        config.spatial.source_srid,
                        config.spatial.projected_srid,
                        config.spatial.grid_origin_x_meters,
                        grid.cell_size_meters,
                        config.spatial.grid_origin_y_meters,
                        grid.cell_size_meters,
                    ),
                )
                self.assertEqual(
                    cursor.fetchone(),
                    (python_cell.grid_x, python_cell.grid_y),
                )
            connection.rollback()
        finally:
            connection.close()

    def test_persists_successful_run_and_quality_then_cleans_fixture(self) -> None:
        from_utc = datetime(2013, 7, 1, tzinfo=timezone.utc)
        cutoff = datetime(2013, 7, 2, tzinfo=timezone.utc)
        event_time = int(datetime(2013, 7, 1, 1, tzinfo=timezone.utc).timestamp())
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            data_root = create_porto_data_root(
                base / "data",
                [porto_row("database-integration-trip", event_time)],
            )
            environment = dict(os.environ)
            environment["TEST_ANALYTICS_ROOT"] = str(data_root)
            config = load_config(write_config(base / "profile.yml"))
            dataset = validate_dataset(config, environment)
            identity = build_run_identity(config, "extraction")
            run_id = processing_run_id(identity)
            settings = DatabaseSettings.from_environment(environment)
            try:
                outcome = run_extraction(
                    config,
                    dataset,
                    from_utc=from_utc,
                    cutoff_utc=cutoff,
                    environment=environment,
                    identity=identity,
                )
                connection = connect_database(settings)
                try:
                    with connection.cursor() as cursor:
                        cursor.execute(
                            """
                            SELECT status, rows_read, rows_written
                            FROM analytics.processing_runs
                            WHERE run_id = %s
                            """,
                            (run_id,),
                        )
                        self.assertEqual(cursor.fetchone(), ("SUCCEEDED", 1, 1))
                        cursor.execute(
                            """
                            SELECT COUNT(*)
                            FROM analytics.data_quality_results
                            WHERE run_id = %s
                            """,
                            (run_id,),
                        )
                        self.assertEqual(cursor.fetchone()[0], 7)
                    connection.rollback()
                finally:
                    connection.close()
                self.assertEqual(outcome.quality.overall_status, "PASS")
            finally:
                connection = connect_database(settings)
                try:
                    with connection.transaction():
                        with connection.cursor() as cursor:
                            cursor.execute(
                                "DELETE FROM analytics.data_quality_results WHERE run_id = %s",
                                (run_id,),
                            )
                            cursor.execute(
                                "DELETE FROM analytics.processing_runs WHERE run_id = %s",
                                (run_id,),
                            )
                finally:
                    connection.close()

    def test_feature_build_is_idempotent_and_persists_cutoff_safe_rows(self) -> None:
        from_utc = datetime(2013, 7, 1, tzinfo=timezone.utc)
        cutoff = datetime(2013, 7, 1, 2, tzinfo=timezone.utc)
        event_times = [
            int(datetime(2013, 7, 1, 0, 5, tzinfo=timezone.utc).timestamp()),
            int(datetime(2013, 7, 1, 1, 20, tzinfo=timezone.utc).timestamp()),
        ]
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            data_root = create_porto_data_root(
                base / "data",
                [
                    porto_row(f"feature-db-{index}", value)
                    for index, value in enumerate(event_times)
                ],
            )
            mapping = valid_config_mapping()
            mapping["quality"]["minimum_history_buckets"] = 4
            mapping["features"]["demand_lags"] = [1, 2, 4]
            mapping["features"]["rolling_windows"] = [4]
            mapping["temporal"]["forecast_horizons_minutes"] = [15]
            environment = dict(os.environ)
            environment["TEST_ANALYTICS_ROOT"] = str(data_root)
            config = load_config(write_config(base / "profile.yml", mapping))
            dataset = validate_dataset(config, environment)
            created = datetime.now(timezone.utc) - timedelta(minutes=1)
            extraction_identity = build_run_identity(
                config,
                "extraction",
                created_at=created,
                git_state=GitState("d" * 40, False),
            )
            feature_identities = [
                build_run_identity(
                    config,
                    "feature-build",
                    created_at=created + timedelta(seconds=index + 1),
                    git_state=GitState("e" * 40, False),
                )
                for index in range(2)
            ]
            run_ids = [
                processing_run_id(extraction_identity),
                *(processing_run_id(identity) for identity in feature_identities),
            ]
            settings = DatabaseSettings.from_environment(environment)
            feature_version = None
            try:
                extraction = run_extraction(
                    config,
                    dataset,
                    from_utc=from_utc,
                    cutoff_utc=cutoff,
                    environment=environment,
                    identity=extraction_identity,
                )
                outcomes = [
                    run_feature_build(
                        config,
                        extraction_run=extraction.run_directory,
                        environment=environment,
                        identity=identity,
                    )
                    for identity in feature_identities
                ]
                feature_version = outcomes[0].feature_set_version
                connection = connect_database(settings)
                try:
                    with connection.cursor() as cursor:
                        cursor.execute(
                            """
                            SELECT
                                COUNT(*),
                                COUNT(DISTINCT created_by_run_id),
                                MIN(created_by_run_id::TEXT),
                                MAX(created_by_run_id::TEXT)
                            FROM analytics.demand_features
                            WHERE feature_set_version = %s
                            """,
                            (feature_version,),
                        )
                        count, distinct_runs, minimum_run, maximum_run = cursor.fetchone()
                        self.assertEqual(count, 6)
                        self.assertEqual(distinct_runs, 1)
                        self.assertEqual(minimum_run, str(run_ids[2]))
                        self.assertEqual(maximum_run, str(run_ids[2]))
                        cursor.execute(
                            """
                            SELECT status
                            FROM analytics.processing_runs
                            WHERE run_id IN (%s, %s, %s)
                            ORDER BY run_type, artifact_run_id
                            """,
                            tuple(run_ids),
                        )
                        self.assertEqual(
                            [row[0] for row in cursor.fetchall()],
                            ["SUCCEEDED", "SUCCEEDED", "SUCCEEDED"],
                        )
                    connection.rollback()
                finally:
                    connection.close()
                self.assertEqual(
                    outcomes[0].artifact.sha256,
                    outcomes[1].artifact.sha256,
                )
            finally:
                connection = connect_database(settings)
                try:
                    with connection.transaction():
                        with connection.cursor() as cursor:
                            if feature_version is not None:
                                cursor.execute(
                                    """
                                    DELETE FROM analytics.demand_features
                                    WHERE feature_set_version = %s
                                    """,
                                    (feature_version,),
                                )
                            else:
                                cursor.execute(
                                    """
                                    DELETE FROM analytics.demand_features
                                    WHERE created_by_run_id IN (%s, %s)
                                    """,
                                    (run_ids[1], run_ids[2]),
                                )
                            cursor.execute(
                                """
                                DELETE FROM analytics.data_quality_results
                                WHERE run_id IN (%s, %s, %s)
                                """,
                                tuple(run_ids),
                            )
                            cursor.execute(
                                """
                                DELETE FROM analytics.processing_runs
                                WHERE run_id IN (%s, %s, %s)
                                """,
                                tuple(run_ids),
                            )
                finally:
                    connection.close()

    def test_feature_rows_roll_back_when_success_transition_fails(self) -> None:
        from_utc = datetime(2013, 7, 31, 23, tzinfo=timezone.utc)
        cutoff = datetime(2013, 8, 1, 1, tzinfo=timezone.utc)
        event_time = int(
            datetime(2013, 7, 31, 23, 5, tzinfo=timezone.utc).timestamp()
        )
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            data_root = create_porto_data_root(
                base / "data",
                [porto_row("feature-rollback", event_time)],
            )
            mapping = valid_config_mapping()
            mapping["quality"]["minimum_history_buckets"] = 4
            mapping["features"]["demand_lags"] = [1, 2, 4]
            mapping["features"]["rolling_windows"] = [4]
            mapping["temporal"]["forecast_horizons_minutes"] = [15]
            environment = dict(os.environ)
            environment["TEST_ANALYTICS_ROOT"] = str(data_root)
            config = load_config(write_config(base / "profile.yml", mapping))
            dataset = validate_dataset(config, environment)
            created = datetime.now(timezone.utc) - timedelta(minutes=1)
            extraction_identity = build_run_identity(
                config,
                "extraction",
                created_at=created,
                git_state=GitState("1" * 40, False),
            )
            feature_identity = build_run_identity(
                config,
                "feature-build",
                created_at=created + timedelta(seconds=1),
                git_state=GitState("2" * 40, False),
            )
            extraction_run_id = processing_run_id(extraction_identity)
            feature_run_id = processing_run_id(feature_identity)
            settings = DatabaseSettings.from_environment(environment)
            try:
                extraction = run_extraction(
                    config,
                    dataset,
                    from_utc=from_utc,
                    cutoff_utc=cutoff,
                    environment=environment,
                    identity=extraction_identity,
                )
                with self.assertRaises(DatabaseError) as raised:
                    run_feature_build(
                        config,
                        extraction_run=extraction.run_directory,
                        environment=environment,
                        identity=feature_identity,
                        repository_factory=_FailingTerminalRepository,
                    )
                self.assertEqual(raised.exception.code, "TEST_TERMINAL_FAILURE")
                feature_directory = (
                    data_root
                    / "runs"
                    / "feature-build"
                    / feature_identity.run_id
                    / "demand-features"
                )
                self.assertEqual(
                    len(list(feature_directory.glob("target_month=*/part-*.parquet"))),
                    2,
                )

                connection = connect_database(settings)
                try:
                    with connection.cursor() as cursor:
                        cursor.execute(
                            """
                            SELECT COUNT(*)
                            FROM analytics.demand_features
                            WHERE created_by_run_id = %s
                            """,
                            (feature_run_id,),
                        )
                        self.assertEqual(cursor.fetchone()[0], 0)
                        cursor.execute(
                            "SELECT status FROM analytics.processing_runs WHERE run_id = %s",
                            (feature_run_id,),
                        )
                        self.assertEqual(cursor.fetchone()[0], "FAILED")
                    connection.rollback()
                finally:
                    connection.close()
            finally:
                connection = connect_database(settings)
                try:
                    with connection.transaction():
                        with connection.cursor() as cursor:
                            cursor.execute(
                                """
                                DELETE FROM analytics.demand_features
                                WHERE created_by_run_id = %s
                                """,
                                (feature_run_id,),
                            )
                            cursor.execute(
                                """
                                DELETE FROM analytics.data_quality_results
                                WHERE run_id IN (%s, %s)
                                """,
                                (extraction_run_id, feature_run_id),
                            )
                            cursor.execute(
                                """
                                DELETE FROM analytics.processing_runs
                                WHERE run_id IN (%s, %s)
                                """,
                                (extraction_run_id, feature_run_id),
                            )
                finally:
                    connection.close()


if __name__ == "__main__":
    unittest.main()
