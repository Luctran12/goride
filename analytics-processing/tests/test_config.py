from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from goride_analytics.config import load_config
from goride_analytics.errors import ConfigurationError

from support import valid_config_mapping, write_config


class ProcessingConfigTests(unittest.TestCase):
    def test_loads_valid_config_and_hash_is_deterministic(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = write_config(Path(temporary) / "profile.yml")
            first = load_config(path)
            second = load_config(path)

        self.assertEqual(first.config_hash, second.config_hash)
        self.assertEqual(first.profile.random_seed, 5537)
        self.assertEqual(first.spatial.projected_srid, 3763)
        self.assertEqual(first.spatial.grid_version, "square-zero-floor-v1")
        self.assertEqual(first.spatial.grid_origin_x_meters, 0)
        self.assertEqual(first.temporal.forecast_horizons_minutes, (15, 30, 60))
        self.assertFalse(first.features.include_supply_features)

    def test_config_hash_changes_when_semantics_change(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first_mapping = valid_config_mapping()
            second_mapping = valid_config_mapping()
            second_mapping["temporal"]["forecast_horizons_minutes"] = [15, 30]
            first = load_config(write_config(root / "first.yml", first_mapping))
            second = load_config(write_config(root / "second.yml", second_mapping))

        self.assertNotEqual(first.config_hash, second.config_hash)

    def test_rejects_unknown_key(self) -> None:
        mapping = valid_config_mapping()
        mapping["profile"]["unexpected"] = True
        with tempfile.TemporaryDirectory() as temporary:
            path = write_config(Path(temporary) / "profile.yml", mapping)
            with self.assertRaises(ConfigurationError) as raised:
                load_config(path)

        self.assertEqual(raised.exception.code, "CONFIG_KEY_UNKNOWN")
        self.assertEqual(raised.exception.details["unknown"], ["unexpected"])

    def test_rejects_profile_name_that_is_unsafe_for_run_paths(self) -> None:
        mapping = valid_config_mapping()
        mapping["profile"]["name"] = "../../unsafe"
        with tempfile.TemporaryDirectory() as temporary:
            path = write_config(Path(temporary) / "profile.yml", mapping)
            with self.assertRaises(ConfigurationError) as raised:
                load_config(path)

        self.assertEqual(raised.exception.code, "CONFIG_PROFILE_NAME_INVALID")

    def test_rejects_horizon_not_aligned_to_bucket(self) -> None:
        mapping = valid_config_mapping()
        mapping["temporal"]["forecast_horizons_minutes"] = [15, 20]
        with tempfile.TemporaryDirectory() as temporary:
            path = write_config(Path(temporary) / "profile.yml", mapping)
            with self.assertRaises(ConfigurationError) as raised:
                load_config(path)

        self.assertEqual(raised.exception.code, "CONFIG_HORIZON_MISALIGNED")

    def test_rejects_overlapping_chronological_splits(self) -> None:
        mapping = valid_config_mapping()
        mapping["evaluation"]["validation"]["from"] = "2014-02-01T00:00:00"
        with tempfile.TemporaryDirectory() as temporary:
            path = write_config(Path(temporary) / "profile.yml", mapping)
            with self.assertRaises(ConfigurationError) as raised:
                load_config(path)

        self.assertEqual(raised.exception.code, "CONFIG_SPLITS_OVERLAP")

    def test_loads_repository_profiles(self) -> None:
        root = Path(__file__).resolve().parents[1]
        porto = load_config(root / "configs" / "porto-thesis.yml")
        goride = load_config(root / "configs" / "goride-local.yml")

        self.assertEqual(porto.profile.name, "porto-thesis")
        self.assertEqual(porto.dataset.source_type, "archive")
        self.assertEqual(goride.profile.name, "goride-local")
        self.assertEqual(goride.dataset.source_type, "postgresql")
        self.assertTrue(goride.features.include_supply_features)
        self.assertEqual(porto.spatial.study_bounds_wgs84.minimum_longitude, -8.75)

    def test_rejects_invalid_or_unbounded_study_area(self) -> None:
        mapping = valid_config_mapping()
        mapping["spatial"]["study_bounds_wgs84"]["maximum_longitude"] = -9
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaises(ConfigurationError) as raised:
                load_config(write_config(Path(temporary) / "profile.yml", mapping))
        self.assertEqual(raised.exception.code, "CONFIG_STUDY_BOUNDS_INVALID")

    def test_rejects_unsafe_grid_version(self) -> None:
        mapping = valid_config_mapping()
        mapping["spatial"]["grid_version"] = "../../grid"
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaises(ConfigurationError) as raised:
                load_config(write_config(Path(temporary) / "profile.yml", mapping))
        self.assertEqual(raised.exception.code, "CONFIG_GRID_VERSION_INVALID")

    def test_rejects_feature_windows_not_represented_in_frozen_schema(self) -> None:
        mapping = valid_config_mapping()
        mapping["features"]["demand_lags"] = [1, 3]
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaises(ConfigurationError) as raised:
                load_config(write_config(Path(temporary) / "profile.yml", mapping))
        self.assertEqual(
            raised.exception.code,
            "CONFIG_FEATURE_WINDOW_UNSUPPORTED",
        )

    def test_rejects_disabling_required_temporal_feature_schema(self) -> None:
        mapping = valid_config_mapping()
        mapping["features"]["include_temporal_features"] = False
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaises(ConfigurationError) as raised:
                load_config(write_config(Path(temporary) / "profile.yml", mapping))
        self.assertEqual(
            raised.exception.code,
            "CONFIG_TEMPORAL_FEATURES_REQUIRED",
        )


if __name__ == "__main__":
    unittest.main()
