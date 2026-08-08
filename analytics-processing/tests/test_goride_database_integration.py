from __future__ import annotations

import os
import tempfile
import unittest
import uuid
from datetime import datetime, timezone
from pathlib import Path

from goride_analytics.database import DatabaseSettings, connect_database, verify_database
from goride_analytics.extraction.canonical import CanonicalSpool
from goride_analytics.extraction.goride import GoRidePostgresExtractor


@unittest.skipUnless(
    os.environ.get("RUN_GORIDE_DB_INTEGRATION") == "1",
    "set RUN_GORIDE_DB_INTEGRATION=1 for read-only GoRide integration",
)
class GoRideDatabaseExtractionIntegrationTests(unittest.TestCase):
    def test_real_query_is_read_only_bounded_and_explainable(self) -> None:
        settings = DatabaseSettings.from_environment(os.environ, "GORIDE_SOURCE_DATABASE")
        connection = connect_database(settings, read_only=True)
        from_utc = datetime(2026, 7, 1, tzinfo=timezone.utc)
        cutoff = datetime(2026, 7, 2, tzinfo=timezone.utc)
        try:
            self.assertTrue(connection.read_only)
            self.assertEqual(connection.isolation_level.name, "REPEATABLE_READ")
            verify_database(connection, require_forecast_schema=False)
            with tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                with CanonicalSpool(root / "spool.db") as spool:
                    result = GoRidePostgresExtractor().extract(
                        connection,
                        spool,
                        source_profile="goride-integration",
                        dataset_version="local",
                        from_utc=from_utc,
                        cutoff_utc=cutoff,
                        snapshot_id=uuid.uuid4(),
                        bucket_minutes=15,
                    )
                    artifact = spool.export_jsonl(root / "events.jsonl")
            self.assertEqual(artifact.row_count, result.stats.accepted_rows)
            self.assertEqual(result.stats.expected_supply_buckets, 96)
            self.assertIsNotNone(result.query_plan)
            if result.stats.maximum_event_time_utc is not None:
                self.assertLess(result.stats.maximum_event_time_utc, cutoff)
        finally:
            connection.close()


if __name__ == "__main__":
    unittest.main()
