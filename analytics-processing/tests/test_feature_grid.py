from __future__ import annotations

import math
import tempfile
import unittest
from pathlib import Path

from goride_analytics.config import load_config
from goride_analytics.features.grid import GridDefinition

from support import write_config


class FeatureGridTests(unittest.TestCase):
    def test_zero_origin_floor_rule_matches_boundary_contract(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            config = load_config(write_config(Path(temporary) / "profile.yml"))
        grid = GridDefinition(config.spatial, 500)

        self.assertEqual(grid.assign_projected(0, 0).grid_x, 0)
        self.assertEqual(grid.assign_projected(499.999, 0).grid_x, 0)
        self.assertEqual(grid.assign_projected(500, 0).grid_x, 1)
        self.assertEqual(grid.assign_projected(-0.001, 0).grid_x, -1)
        self.assertEqual(
            grid.assign_projected(500, -0.001).cell_id,
            "square-zero-floor-v1:3763:500:1:-1",
        )

    def test_study_bounds_are_minimum_inclusive_maximum_exclusive(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            config = load_config(write_config(Path(temporary) / "profile.yml"))
        grid = GridDefinition(config.spatial, 500)
        bounds = config.spatial.study_bounds_wgs84

        self.assertTrue(
            grid.contains_wgs84(bounds.minimum_longitude, bounds.minimum_latitude)
        )
        self.assertFalse(
            grid.contains_wgs84(bounds.maximum_longitude, bounds.minimum_latitude)
        )
        self.assertFalse(
            grid.contains_wgs84(bounds.minimum_longitude, bounds.maximum_latitude)
        )

    def test_real_porto_transform_is_finite_and_canonical(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            config = load_config(write_config(Path(temporary) / "profile.yml"))
        cell = GridDefinition(config.spatial, 500).assign(-8.61099, 41.14557)

        self.assertIsNotNone(cell)
        assert cell is not None
        self.assertTrue(math.isfinite(cell.grid_x))
        self.assertTrue(cell.cell_id.startswith("square-zero-floor-v1:3763:500:"))


if __name__ == "__main__":
    unittest.main()
