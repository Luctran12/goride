from __future__ import annotations

import tempfile
import unittest
import uuid
from array import array
from datetime import datetime, timezone
from pathlib import Path

from goride_analytics.config import load_config
from goride_analytics.errors import ExtractionError
from goride_analytics.features.builder import (
    DemandCube,
    FeatureBuildStats,
    iter_feature_rows,
)
from goride_analytics.features.grid import GridCell, GridDefinition
from goride_analytics.features.planning import build_feature_plan

from support import valid_config_mapping, write_config


class FeaturePlanningTests(unittest.TestCase):
    def _config(self, root: Path, *, coverage: float = 0.65, limit: int = 100):
        mapping = valid_config_mapping()
        mapping["features"]["training_demand_coverage"] = coverage
        mapping["features"]["demand_lags"] = [1, 2, 4]
        mapping["features"]["rolling_windows"] = [4]
        mapping["quality"]["minimum_history_buckets"] = 4
        mapping["temporal"]["forecast_horizons_minutes"] = [15]
        mapping["evaluation"]["train"] = {
            "from": "2026-01-31T23:00:00",
            "to": "2026-02-01T00:00:00",
        }
        mapping["evaluation"]["validation"] = {
            "from": "2026-02-01T00:00:00",
            "to": "2026-02-01T00:30:00",
        }
        mapping["evaluation"]["test"] = {
            "from": "2026-02-01T00:30:00",
            "to": "2026-02-01T01:00:00",
        }
        mapping["artifacts"]["maximum_rows_per_partition"] = limit
        mapping["artifacts"]["maximum_rows_per_run"] = max(limit, 100)
        return load_config(write_config(root / "profile.yml", mapping))

    @staticmethod
    def _cube() -> DemandCube:
        start = datetime(2026, 1, 31, 23, tzinfo=timezone.utc)
        values = {
            (0, 0): array("I", [20, 10, 10, 10, 0, 0, 0, 0]),
            (1, 0): array("I", [5, 5, 5, 5, 0, 0, 0, 0]),
            (2, 0): array("I", [5, 5, 5, 5, 0, 0, 0, 0]),
            # Future-only volume must not influence train-only eligibility.
            (3, 0): array("I", [2, 2, 3, 3, 100, 100, 100, 100]),
        }
        cells = {
            key: GridCell(
                key[0],
                key[1],
                f"square-zero-floor-v1:3763:500:{key[0]}:0",
            )
            for key in values
        }
        return DemandCube(
            from_utc=start,
            cutoff_utc=datetime(2026, 2, 1, 1, tzinfo=timezone.utc),
            bucket_minutes=15,
            bucket_count=8,
            semantics="TRIP_STARTED_PROXY",
            series=values,
            cells=cells,
            service_areas={key: None for key in values},
            stats=FeatureBuildStats(input_events=sum(map(sum, values.values()))),
        )

    def test_train_only_coverage_includes_boundary_ties_and_plans_months(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            config = self._config(root)
            cube = self._cube()
            plan = build_feature_plan(cube, config)

        self.assertEqual(plan.eligibility.total_train_events, 100)
        self.assertEqual(plan.eligibility.selected_train_events, 90)
        self.assertEqual(plan.eligibility.achieved_coverage, 0.9)
        self.assertEqual(plan.eligibility.boundary_train_events, 20)
        self.assertEqual(plan.eligibility.selected_cell_count, 3)
        self.assertNotIn((3, 0), plan.eligibility.selected_cell_keys)
        self.assertEqual(
            [(item.key, item.projected_rows) for item in plan.partitions],
            [("2026-01", 6), ("2026-02", 12)],
        )
        self.assertEqual(plan.projected_rows, 18)

    def test_partitioned_iteration_matches_plan_without_duplicate_identity(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            config = self._config(root)
            cube = self._cube()
            plan = build_feature_plan(cube, config)
            grid = GridDefinition(config.spatial, 500)
            rows = [
                row
                for partition in plan.partitions
                for row in iter_feature_rows(
                    cube,
                    config,
                    grid=grid,
                    version="feature-v1",
                    snapshot_sha256="a" * 64,
                    feature_artifact_id=uuid.uuid4(),
                    selected_cell_keys=plan.eligibility.selected_cell_keys,
                    target_from_utc=partition.target_from_utc,
                    target_to_utc=partition.target_to_utc,
                )
            ]

        identities = {
            (row.cell_id, row.inference_cutoff_utc, row.horizon_minutes)
            for row in rows
        }
        self.assertEqual(len(rows), plan.projected_rows)
        self.assertEqual(len(identities), len(rows))
        excluded_cell_id = cube.cells[(3, 0)].cell_id
        self.assertTrue(all(row.cell_id != excluded_cell_id for row in rows))

    def test_partition_cost_guard_fails_before_materialization(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            config = self._config(Path(temporary), limit=10)
            with self.assertRaises(ExtractionError) as raised:
                build_feature_plan(self._cube(), config)
        self.assertEqual(
            raised.exception.code,
            "FEATURE_PARTITION_ROW_LIMIT_EXCEEDED",
        )


if __name__ == "__main__":
    unittest.main()
