from __future__ import annotations

import tempfile
import unittest
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pyarrow.parquet as pq

from goride_analytics.features.model import FeatureRow
from goride_analytics.features.spool import FeatureSpool


def _row(cell_x: int, minute: int) -> FeatureRow:
    cutoff = datetime(2026, 1, 1, tzinfo=timezone.utc) + timedelta(minutes=minute)
    return FeatureRow(
        feature_set_version="feature-v1",
        source_profile="fixture",
        dataset_version="v1",
        demand_event_semantics="REQUEST_CREATED",
        grid_version="square-zero-floor-v1",
        projected_srid=32648,
        cell_id=f"square-zero-floor-v1:32648:500:{cell_x}:0",
        grid_x=cell_x,
        grid_y=0,
        cell_size_meters=500,
        bucket_start_utc=cutoff,
        inference_cutoff_utc=cutoff,
        target_bucket_start_utc=cutoff + timedelta(minutes=15),
        horizon_minutes=15,
        target_trip_requests=2,
        lag_1=1,
        lag_2=None,
        lag_4=None,
        lag_96=None,
        lag_672=None,
        rolling_mean_4=None,
        rolling_mean_12=None,
        rolling_mean_96=None,
        rolling_mean_672=None,
        hour_sin=0.0,
        hour_cos=1.0,
        day_of_week=3,
        is_weekend=False,
        neighbor_demand_lag_1=0,
        available_driver_lag_1=None,
        coverage_ratio=1.0,
        quality_status="PASS",
        feature_artifact_id=uuid.UUID("11111111-1111-1111-1111-111111111111"),
        max_feature_source_time_utc=cutoff,
        source_snapshot_sha256="a" * 64,
    )


class FeatureSpoolTests(unittest.TestCase):
    def test_parquet_order_checksum_and_duplicate_detection_are_deterministic(self) -> None:
        first = _row(0, 15)
        second = _row(1, 30)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            artifacts = []
            for index, rows in enumerate(((second, first), (first, second))):
                with FeatureSpool(root / f"spool-{index}.db") as spool:
                    for row in rows:
                        self.assertTrue(spool.add(row))
                    self.assertFalse(spool.add(rows[0]))
                    self.assertEqual(spool.duplicate_rows, 1)
                    artifacts.append(
                        spool.export_parquet(root / f"features-{index}.parquet")
                    )
            table = pq.read_table(root / "features-0.parquet")

        self.assertEqual(artifacts[0].row_count, 2)
        self.assertEqual(artifacts[0].sha256, artifacts[1].sha256)
        self.assertEqual(
            table.column("cell_id").to_pylist(),
            [first.cell_id, second.cell_id],
        )


if __name__ == "__main__":
    unittest.main()
