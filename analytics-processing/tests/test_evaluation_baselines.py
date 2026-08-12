from __future__ import annotations

import unittest
from datetime import datetime, timedelta, timezone

from goride_analytics.evaluation.baselines import (
    DemandCube,
    fit_historical_mean,
    seasonal_naive_prediction,
)


class EvaluationBaselineTests(unittest.TestCase):
    def setUp(self) -> None:
        self.start = datetime(2014, 1, 1, tzinfo=timezone.utc)
        self.cube = DemandCube(
            cells=("cell-a", "cell-b"),
            from_utc=self.start,
            to_utc=self.start + timedelta(days=22),
            bucket_minutes=15,
            source_timezone="Europe/Lisbon",
        )

    def test_historical_mean_uses_training_history_only(self) -> None:
        self.cube.set("cell-a", self.start, 2)
        self.cube.set("cell-a", self.start + timedelta(days=7), 4)
        self.cube.set("cell-a", self.start + timedelta(days=14), 100)

        profile = fit_historical_mean(
            self.cube,
            train_from_utc=self.start,
            train_to_utc=self.start + timedelta(days=14),
        )
        bucket = self.cube.bucket_index(self.start + timedelta(days=14))
        prediction, source = profile.predict(0, self.cube.weekly_slots[bucket])

        self.assertEqual(prediction, 3)
        self.assertEqual(source, "CELL_WEEKLY_SLOT")

    def test_seasonal_naive_uses_latest_week_and_falls_back_for_missing_history(self) -> None:
        self.cube.set("cell-a", self.start, 5)
        profile = fit_historical_mean(
            self.cube,
            train_from_utc=self.start,
            train_to_utc=self.start + timedelta(days=7),
        )
        target_bucket = self.cube.bucket_index(self.start + timedelta(days=7))

        prediction, source = seasonal_naive_prediction(
            self.cube,
            profile,
            cell_index=0,
            target_bucket_index=target_bucket,
        )
        missing_prediction, missing_source = seasonal_naive_prediction(
            self.cube,
            profile,
            cell_index=1,
            target_bucket_index=target_bucket,
        )

        self.assertEqual((prediction, source), (5.0, "WEEK_LAG_LOCAL"))
        self.assertEqual(missing_prediction, 5.0)
        self.assertEqual(missing_source, "HISTORICAL_MEAN_GLOBAL_MEAN")

    def test_local_weekly_season_is_dst_aware(self) -> None:
        start = datetime(2014, 3, 20, tzinfo=timezone.utc)
        cube = DemandCube(
            cells=("cell-a",),
            from_utc=start,
            to_utc=datetime(2014, 4, 2, tzinfo=timezone.utc),
            bucket_minutes=15,
            source_timezone="Europe/Lisbon",
        )
        previous_local_midnight = datetime(2014, 3, 25, tzinfo=timezone.utc)
        target_local_midnight = datetime(2014, 3, 31, 23, tzinfo=timezone.utc)
        cube.set("cell-a", previous_local_midnight, 7)
        profile = fit_historical_mean(
            cube,
            train_from_utc=start,
            train_to_utc=target_local_midnight,
        )
        target_bucket = cube.bucket_index(target_local_midnight)

        prediction, source = seasonal_naive_prediction(
            cube,
            profile,
            cell_index=0,
            target_bucket_index=target_bucket,
        )

        self.assertEqual(target_bucket - cube.previous_seasonal_indexes[target_bucket], 668)
        self.assertEqual((prediction, source), (7.0, "WEEK_LAG_LOCAL"))


if __name__ == "__main__":
    unittest.main()
