from __future__ import annotations

import math
import unittest

from goride_analytics.evaluation.metrics import (
    MetricAccumulator,
    demand_quantile_slice,
    demand_quantile_thresholds,
    time_of_day_slice,
)


class EvaluationMetricTests(unittest.TestCase):
    def test_metrics_match_hand_calculated_example(self) -> None:
        accumulator = MetricAccumulator()
        for actual, prediction in ((0, 1), (2, 1), (4, 7)):
            accumulator.update(actual, prediction)

        result = accumulator.result()

        self.assertAlmostEqual(result["mae"], 5 / 3)
        self.assertAlmostEqual(result["rmse"], math.sqrt(11 / 3))
        self.assertAlmostEqual(result["wape"], 5 / 6)
        self.assertEqual(result["sampleCount"], 3)

    def test_wape_is_null_when_actual_demand_sum_is_zero(self) -> None:
        accumulator = MetricAccumulator()
        accumulator.update(0, 0)
        accumulator.update(0, 2)

        self.assertIsNone(accumulator.result()["wape"])

    def test_training_quantiles_and_slices_are_deterministic_with_ties(self) -> None:
        thresholds = demand_quantile_thresholds([0, 0, 0, 1, 2, 3, 4, 9])

        self.assertEqual(thresholds, (0, 1, 3))
        self.assertEqual(demand_quantile_slice(0, thresholds), "Q1_LOW")
        self.assertEqual(demand_quantile_slice(2, thresholds), "Q3")
        self.assertEqual(demand_quantile_slice(9, thresholds), "Q4_HIGH")

    def test_time_of_day_slices_cover_every_hour(self) -> None:
        values = [time_of_day_slice(hour) for hour in range(24)]

        self.assertEqual(len(values), 24)
        self.assertEqual(values[0], "NIGHT")
        self.assertEqual(values[6], "AM_PEAK")
        self.assertEqual(values[12], "DAY")
        self.assertEqual(values[18], "PM_PEAK")
        self.assertEqual(values[22], "EVENING")


if __name__ == "__main__":
    unittest.main()
