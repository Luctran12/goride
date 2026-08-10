from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import yaml

from goride_analytics.errors import ConfigurationError
from goride_analytics.models.experiment import load_candidate_experiment


class CandidateExperimentTests(unittest.TestCase):
    def setUp(self) -> None:
        self.source = Path(__file__).resolve().parents[1] / "configs" / "porto-phase6-hgb.yml"

    def test_repository_experiment_is_strict_and_frozen(self) -> None:
        experiment = load_candidate_experiment(self.source)

        self.assertEqual(experiment.primary_metric, "WAPE")
        self.assertEqual(tuple(experiment.feature_sets), ("A0", "A1", "A2"))
        self.assertEqual(len(experiment.search_space), 3)
        self.assertFalse(experiment.prediction_interval_enabled)
        self.assertEqual(len(experiment.experiment_hash), 64)

    def test_rejects_final_holdout_in_selection_scope(self) -> None:
        raw = yaml.safe_load(self.source.read_text(encoding="utf-8"))
        raw["selection_rule"]["scope"] = "INCLUDING_FINAL"
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "experiment.yml"
            target.write_text(yaml.safe_dump(raw, sort_keys=False), encoding="utf-8")
            with self.assertRaises(ConfigurationError) as raised:
                load_candidate_experiment(target)

        self.assertEqual(raised.exception.code, "MODEL_CONFIG_INVALID")


if __name__ == "__main__":
    unittest.main()
