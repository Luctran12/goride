from __future__ import annotations

import math
import unittest

import numpy as np

from goride_analytics.models.pipeline import VectorAccumulator


class CandidateMetricTests(unittest.TestCase):
    def test_vector_metrics_match_hand_calculated_values(self) -> None:
        accumulator = VectorAccumulator()
        accumulator.update(np.asarray([0.0, 2.0, 4.0]), np.asarray([1.0, 1.0, 7.0]))

        result = accumulator.result()

        self.assertAlmostEqual(result["mae"], 5 / 3)
        self.assertAlmostEqual(result["rmse"], math.sqrt(11 / 3))
        self.assertAlmostEqual(result["wape"], 5 / 6)

    def test_vector_wape_is_null_for_zero_actual_sum(self) -> None:
        accumulator = VectorAccumulator()
        accumulator.update(np.asarray([0.0, 0.0]), np.asarray([0.0, 2.0]))

        self.assertIsNone(accumulator.result()["wape"])


if __name__ == "__main__":
    unittest.main()
