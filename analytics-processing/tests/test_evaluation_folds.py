from __future__ import annotations

import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

from goride_analytics.config import load_config
from goride_analytics.evaluation.folds import build_rolling_origin_folds

from support import write_config


class EvaluationFoldTests(unittest.TestCase):
    def test_builds_frozen_expanding_monthly_folds_without_test_leakage(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            config = load_config(write_config(Path(temporary) / "profile.yml"))

        folds = build_rolling_origin_folds(config)

        self.assertEqual([fold.key for fold in folds], ["D1", "D2", "D3", "D4", "FINAL"])
        self.assertEqual(folds[0].train_to_utc, datetime(2014, 1, 1, tzinfo=timezone.utc))
        self.assertEqual(folds[0].evaluate_to_utc, datetime(2014, 2, 1, tzinfo=timezone.utc))
        self.assertEqual(folds[3].evaluate_from_utc, datetime(2014, 3, 31, 23, tzinfo=timezone.utc))
        self.assertEqual(folds[-1].evaluate_from_utc, datetime(2014, 4, 30, 23, tzinfo=timezone.utc))
        self.assertEqual(folds[-1].evaluate_to_utc, datetime(2014, 6, 30, 23, tzinfo=timezone.utc))
        self.assertTrue(all(fold.train_to_utc == fold.evaluate_from_utc for fold in folds))
        self.assertTrue(all(fold.train_to_utc <= fold.evaluate_from_utc for fold in folds))


if __name__ == "__main__":
    unittest.main()
