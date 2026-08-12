from __future__ import annotations

import math
import os
import unittest

import numpy as np

from goride_analytics.models.pipeline import VectorAccumulator, _peak_working_set_bytes


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

    def test_peak_memory_collector_returns_process_measurement_on_windows(self) -> None:
        value = _peak_working_set_bytes()

        if os.name == "nt":
            self.assertIsNotNone(value)
            self.assertGreater(value, 0)


if __name__ == "__main__":
    unittest.main()
