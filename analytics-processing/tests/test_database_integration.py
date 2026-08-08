from __future__ import annotations

import os
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

from goride_analytics.config import load_config
from goride_analytics.database import (
    DatabaseSettings,
    connect_database,
    processing_run_id,
)
from goride_analytics.extraction.pipeline import run_extraction
from goride_analytics.manifest import validate_dataset
from goride_analytics.runs import build_run_identity

from support import create_porto_data_root, porto_row, write_config


@unittest.skipUnless(
    os.environ.get("RUN_ANALYTICS_DB_INTEGRATION") == "1",
    "set RUN_ANALYTICS_DB_INTEGRATION=1 for PostgreSQL/PostGIS integration",
)
class DatabaseExtractionIntegrationTests(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
