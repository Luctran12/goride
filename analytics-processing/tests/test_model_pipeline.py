from __future__ import annotations

import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

import pyarrow.parquet as pq

from goride_analytics.config import load_config
from goride_analytics.models.baseline import BaselineEvidence
from goride_analytics.models.data import count_training_strata
from goride_analytics.models.pipeline import _evaluate_selected, _search_candidate
from goride_analytics.runs import GitState, build_run_identity

from support import write_config
import test_model_data


class CandidatePipelineTests(unittest.TestCase):
    def test_search_locks_development_configs_before_full_population_evaluation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            artifact, experiment, fold = test_model_data.CandidateModelDataTests()._fixture(root)
            config = load_config(write_config(root / "profile.yml"))
            counts = count_training_strata(artifact, (fold,), (15,))

            search, selected, search_samples, _seconds = _search_candidate(
                artifact,
                experiment,
                (fold,),
                (15,),
                counts,
            )
            baseline = BaselineEvidence(
                run_id="baseline-run",
                run_directory=root,
                raw_prediction_sha256="b" * 64,
                raw_prediction_rows=16,
                metrics=(
                    {
                        "modelName": "HISTORICAL_MEAN",
                        "foldKey": "D1",
                        "horizonMinutes": 15,
                        "sliceType": "ALL",
                        "sampleCount": 8,
                        "mae": 1.0,
                        "rmse": 1.0,
                        "wape": 1.0,
                    },
                ),
                quantile_thresholds={"D1": (0, 1, 3)},
            )
            identity = build_run_identity(
                config,
                "training",
                created_at=datetime(2026, 1, 1, tzinfo=timezone.utc),
                git_state=GitState("f" * 40, False),
            )
            run_directory = root / "training"
            run_directory.mkdir()
            (
                metrics,
                files,
                final_models,
                rows,
                _evaluation_seconds,
                evaluation_samples,
                negative_predictions,
                minimum_prediction,
            ) = _evaluate_selected(
                config,
                artifact,
                baseline,
                experiment,
                (fold,),
                (15,),
                counts,
                selected,
                identity,
                run_directory,
            )
            parquet_rows = sum(
                pq.ParquetFile(run_directory / item["file"]).metadata.num_rows
                for item in files
            )

        self.assertEqual(len(search), 9)
        self.assertEqual(set(selected), {"A0", "A1", "A2"})
        self.assertEqual(len(search_samples), 1)
        self.assertEqual(len(evaluation_samples), 1)
        self.assertEqual(rows, 24)
        self.assertEqual(parquet_rows, rows)
        self.assertEqual(len(files), 3)
        self.assertTrue(metrics)
        self.assertFalse(final_models)
        self.assertEqual(negative_predictions, 0)
        self.assertGreaterEqual(minimum_prediction, 0)


if __name__ == "__main__":
    unittest.main()
