from __future__ import annotations

import json
import tempfile
import unittest
import uuid
from datetime import datetime, timezone
from pathlib import Path

from goride_analytics.extraction.canonical import CanonicalDemandEvent, CanonicalSpool


class CanonicalSnapshotTests(unittest.TestCase):
    def test_order_and_checksum_are_deterministic_and_private_fields_are_absent(self) -> None:
        cutoff = datetime(2026, 1, 2, tzinfo=timezone.utc)
        snapshot_id = uuid.UUID("11111111-1111-1111-1111-111111111111")

        def event(key: str, hour: int) -> CanonicalDemandEvent:
            return CanonicalDemandEvent(
                "fixture",
                "v1",
                key,
                datetime(2026, 1, 1, hour, tzinfo=timezone.utc),
                "REQUEST_CREATED",
                106.7,
                10.8,
                "BIKE",
                None,
                cutoff,
                snapshot_id,
            )

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            artifacts = []
            for index, values in enumerate(((event("b", 2), event("a", 1)), (event("a", 1), event("b", 2)))):
                with CanonicalSpool(root / f"spool-{index}.db") as spool:
                    for value in values:
                        self.assertTrue(spool.add(value))
                    self.assertFalse(spool.add(values[0]))
                    artifacts.append(spool.export_jsonl(root / f"snapshot-{index}.jsonl"))
            payload = json.loads((root / "snapshot-0.jsonl").read_text().splitlines()[0])

        self.assertEqual(artifacts[0].sha256, artifacts[1].sha256)
        self.assertEqual(payload["source_trip_key"], "a")
        self.assertNotIn("taxi_id", payload)
        self.assertNotIn("driver_id", payload)
        self.assertNotIn("passenger_id", payload)
        self.assertNotIn("polyline", payload)


if __name__ == "__main__":
    unittest.main()
