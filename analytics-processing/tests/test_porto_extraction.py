from __future__ import annotations

import tempfile
import unittest
import uuid
from datetime import datetime, timezone
from pathlib import Path

from goride_analytics.extraction.canonical import CanonicalSpool
from goride_analytics.extraction.porto import PortoArchiveExtractor
from goride_analytics.quality import build_quality_report

from support import create_porto_data_root, porto_row


class PortoExtractionTests(unittest.TestCase):
    def test_quality_covers_duplicate_null_invalid_missing_and_cutoff(self) -> None:
        start = datetime(2013, 7, 1, tzinfo=timezone.utc)
        cutoff = datetime(2013, 7, 2, tzinfo=timezone.utc)
        inside = int(datetime(2013, 7, 1, 1, tzinfo=timezone.utc).timestamp())
        outside = int(cutoff.timestamp())
        rows = [
            porto_row("valid", inside),
            porto_row("valid", inside),
            porto_row("invalid", inside, polyline="[[999, 41.0]]"),
            porto_row("missing-time", ""),
            porto_row("missing-data", inside, missing_data="True"),
            porto_row("at-cutoff", outside),
        ]
        with tempfile.TemporaryDirectory() as temporary:
            root = create_porto_data_root(Path(temporary), rows)
            with CanonicalSpool(root / "spool.db") as spool:
                stats = PortoArchiveExtractor().extract(
                    root / "raw/test-dataset/v1/source.zip",
                    spool,
                    source_profile="porto-test",
                    dataset_version="v1",
                    from_utc=start,
                    cutoff_utc=cutoff,
                    snapshot_id=uuid.uuid4(),
                )
                artifact = spool.export_jsonl(root / "events.jsonl")
        report = build_quality_report(
            stats,
            checksum_verified=True,
            include_missing_trajectory_rule=True,
            include_supply_coverage_rule=False,
        )

        self.assertEqual(artifact.row_count, 1)
        self.assertEqual(stats.rows_scanned, 6)
        self.assertEqual(stats.duplicate_trip_breaches, 1)
        self.assertEqual(stats.invalid_pickup_breaches, 1)
        self.assertEqual(stats.missing_event_time_breaches, 1)
        self.assertEqual(stats.missing_trajectory_warnings, 1)
        self.assertEqual(stats.outside_interval_rows, 1)
        self.assertEqual(report.overall_status, "FAIL")


if __name__ == "__main__":
    unittest.main()
