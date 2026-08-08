from __future__ import annotations

import tempfile
import unittest
import uuid
from datetime import datetime, timezone
from pathlib import Path

from goride_analytics.extraction.canonical import CanonicalSpool
from goride_analytics.extraction.goride import (
    GORIDE_EXTRACTION_SQL,
    GORIDE_SUPPLY_COVERAGE_SQL,
    normalize_goride_rows,
)


class GoRideExtractionTests(unittest.TestCase):
    def test_query_contract_is_bounded_ordered_and_supply_is_bounded(self) -> None:
        normalized = " ".join(GORIDE_EXTRACTION_SQL.split())
        supply = " ".join(GORIDE_SUPPLY_COVERAGE_SQL.split())
        self.assertIn("t.requested_at >= %s", normalized)
        self.assertIn("t.requested_at < %s", normalized)
        self.assertIn("ORDER BY t.requested_at, t.id", normalized)
        self.assertIn("bucket_start >= %s", supply)
        self.assertIn("bucket_start < %s", supply)

    def test_normalization_drops_future_duplicate_and_invalid_geometry(self) -> None:
        start = datetime(2026, 1, 1, tzinfo=timezone.utc)
        cutoff = datetime(2026, 1, 2, tzinfo=timezone.utc)
        rows = [
            ("one", datetime(2026, 1, 1, 1, tzinfo=timezone.utc), 106.7, 10.8, "BIKE", 1),
            ("one", datetime(2026, 1, 1, 2, tzinfo=timezone.utc), 106.7, 10.8, "BIKE", 1),
            ("invalid", datetime(2026, 1, 1, 3, tzinfo=timezone.utc), 999, 10.8, "BIKE", None),
            ("future", cutoff, 106.7, 10.8, "BIKE", None),
        ]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with CanonicalSpool(root / "spool.db") as spool:
                stats = normalize_goride_rows(
                    rows,
                    spool,
                    source_profile="goride-test",
                    dataset_version="v1",
                    from_utc=start,
                    cutoff_utc=cutoff,
                    snapshot_id=uuid.uuid4(),
                )
                artifact = spool.export_jsonl(root / "events.jsonl")
        self.assertEqual(artifact.row_count, 1)
        self.assertEqual(stats.duplicate_trip_breaches, 1)
        self.assertEqual(stats.invalid_pickup_breaches, 1)
        self.assertEqual(stats.future_event_breaches, 1)


if __name__ == "__main__":
    unittest.main()
