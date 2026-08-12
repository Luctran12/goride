from __future__ import annotations

import tempfile
import unittest
import uuid
from array import array
from datetime import datetime, timezone
from pathlib import Path

from goride_analytics.config import load_config
from goride_analytics.features.builder import (
    DemandCube,
    FeatureBuildStats,
    build_feature_quality,
    iter_feature_rows,
)
from goride_analytics.features.grid import GridCell, GridDefinition

from support import valid_config_mapping, write_config


class _Grid:
    cell_size_meters = 500

    @staticmethod
    def neighbors(cell: GridCell):
        return GridDefinition.neighbors(cell)


class FeatureBuilderTests(unittest.TestCase):
    def _config(self, root: Path):
        mapping = valid_config_mapping()
        mapping["quality"]["minimum_history_buckets"] = 4
        mapping["features"]["demand_lags"] = [1, 2, 4]
        mapping["features"]["rolling_windows"] = [4]
        mapping["temporal"]["forecast_horizons_minutes"] = [15]
        return load_config(write_config(root / "profile.yml", mapping))

    def _cube(self) -> DemandCube:
        first = GridCell(0, 0, "square-zero-floor-v1:3763:500:0:0")
        neighbor = GridCell(0, 1, "square-zero-floor-v1:3763:500:0:1")
        start = datetime(2013, 10, 27, 0, 0, tzinfo=timezone.utc)
        stats = FeatureBuildStats(input_events=11, events_aggregated=11)
        return DemandCube(
            from_utc=start,
            cutoff_utc=datetime(2013, 10, 27, 2, 0, tzinfo=timezone.utc),
            bucket_minutes=15,
            bucket_count=8,
            semantics="TRIP_STARTED_PROXY",
            series={
                (0, 0): array("I", [2, 0, 1, 0, 0, 5, 0, 0]),
                (0, 1): array("I", [0, 0, 0, 3, 0, 0, 0, 0]),
            },
            cells={(0, 0): first, (0, 1): neighbor},
            service_areas={(0, 0): None, (0, 1): None},
            stats=stats,
        )

    def test_lags_rollings_neighbors_and_zero_buckets_use_closed_history(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            config = self._config(Path(temporary))
        cube = self._cube()
        rows = list(
            iter_feature_rows(
                cube,
                config,
                grid=_Grid(),
                version="feature-v1",
                snapshot_sha256="a" * 64,
                feature_artifact_id=uuid.uuid4(),
            )
        )
        cutoff = cube.from_utc.replace(hour=1)
        row = next(
            item
            for item in rows
            if item.grid_x == 0 and item.grid_y == 0
            and item.inference_cutoff_utc == cutoff
        )

        self.assertEqual(row.lag_1, 0)
        self.assertEqual(row.lag_2, 1)
        self.assertEqual(row.lag_4, 2)
        self.assertEqual(row.rolling_mean_4, 0.75)
        self.assertEqual(row.neighbor_demand_lag_1, 3)
        self.assertEqual(row.target_trip_requests, 5)
        self.assertEqual(row.max_feature_source_time_utc, cutoff)
        self.assertEqual(row.coverage_ratio, 1.0)
        self.assertEqual(row.quality_status, "PASS")

        # Bucket at the cutoff is forbidden feature input. Changing it must not
        # alter lag/rolling/neighbor values for the same cutoff.
        cube.series[(0, 0)][4] = 99
        rebuilt = next(
            item
            for item in iter_feature_rows(
                cube,
                config,
                grid=_Grid(),
                version="feature-v1",
                snapshot_sha256="a" * 64,
                feature_artifact_id=uuid.uuid4(),
            )
            if item.grid_x == 0 and item.grid_y == 0
            and item.inference_cutoff_utc == cutoff
        )
        self.assertEqual(
            (rebuilt.lag_1, rebuilt.lag_2, rebuilt.lag_4, rebuilt.rolling_mean_4),
            (row.lag_1, row.lag_2, row.lag_4, row.rolling_mean_4),
        )

    def test_utc_bucket_identity_remains_unique_across_dst_fallback(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            config = self._config(Path(temporary))
        rows = list(
            iter_feature_rows(
                self._cube(),
                config,
                grid=_Grid(),
                version="feature-v1",
                snapshot_sha256="b" * 64,
                feature_artifact_id=uuid.uuid4(),
            )
        )
        identities = {
            (row.cell_id, row.inference_cutoff_utc, row.horizon_minutes)
            for row in rows
        }
        self.assertEqual(len(identities), len(rows))

    def test_quality_fails_empty_population_and_leakage(self) -> None:
        stats = FeatureBuildStats(feature_rows=0, leakage_breaches=1)
        report = build_feature_quality(
            stats,
            expected_buckets=8,
            include_supply=False,
        )
        by_code = {item.rule_code: item for item in report.results}
        self.assertEqual(report.overall_status, "FAIL")
        self.assertEqual(by_code["DQ_FEATURE_POPULATION"].result_status, "FAIL")
        self.assertEqual(by_code["DQ_LEAKAGE"].result_status, "FAIL")
        self.assertTrue(
            all(item.records_breached <= item.records_checked for item in report.results)
        )


if __name__ == "__main__":
    unittest.main()
