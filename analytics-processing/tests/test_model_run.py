from __future__ import annotations

import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
from uuid import UUID

from goride_analytics.config import load_config
from goride_analytics.database import DatabaseMetadata
from goride_analytics.evaluation.folds import EvaluationFold
from goride_analytics.features.persistence import ResumableFeatureArtifact
from goride_analytics.models.baseline import BaselineEvidence
from goride_analytics.models.pipeline import run_candidate_training
from goride_analytics.runs import GitState, build_run_identity

from support import write_config


class _Connection:
    def close(self):
        pass


class _Repository:
    def __init__(self):
        self.started = []
        self.quality = []
        self.succeeded = []
        self.failed = []

    def start(self, **kwargs):
        self.started.append(kwargs)
        return 1

    def save_quality(self, run_id, results):
        self.quality.append((run_id, tuple(results)))

    def succeed(self, run_id, **counts):
        self.succeeded.append((run_id, counts))

    def fail(self, run_id, **details):
        self.failed.append((run_id, details))


class CandidateTrainingRunTests(unittest.TestCase):
    def test_training_run_writes_model_metrics_quality_and_checksums(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            config = load_config(write_config(root / "profile.yml"))
            experiment_path = Path(__file__).resolve().parents[1] / "configs" / "porto-phase6-hgb.yml"
            artifact = ResumableFeatureArtifact(
                source_run_id="feature-run",
                source_run_directory=root,
                feature_artifact_id=UUID(int=1),
                feature_set_version="feature-v1",
                source_cutoff_utc=datetime(2014, 7, 1, tzinfo=timezone.utc),
                row_count=16,
                byte_count=10,
                sha256="a" * 64,
                quality_status="PASS",
                quality_results=(),
                partitions=(),
                cell_size_meters=500,
            )
            metric_rows = tuple(
                {
                    "modelName": model,
                    "foldKey": "FINAL",
                    "horizonMinutes": 15,
                    "sliceType": "ALL",
                    "sampleCount": 8,
                    "mae": 1.0,
                    "rmse": 1.0,
                    "wape": 1.0,
                }
                for model in ("HISTORICAL_MEAN", "SEASONAL_NAIVE")
            )
            baseline = BaselineEvidence(
                run_id="baseline-run",
                run_directory=root,
                raw_prediction_sha256="b" * 64,
                raw_prediction_rows=16,
                metrics=metric_rows,
                quantile_thresholds={"FINAL": (0, 1, 2)},
            )
            final_fold = EvaluationFold(
                key="FINAL",
                split_role="TEST",
                train_from_utc=datetime(2014, 1, 1, tzinfo=timezone.utc),
                train_to_utc=datetime(2014, 2, 1, tzinfo=timezone.utc),
                evaluate_from_utc=datetime(2014, 2, 1, tzinfo=timezone.utc),
                evaluate_to_utc=datetime(2014, 2, 2, tzinfo=timezone.utc),
            )
            repository = _Repository()
            identity = build_run_identity(
                config,
                "training",
                created_at=datetime(2026, 1, 1, tzinfo=timezone.utc),
                git_state=GitState("f" * 40, False),
            )

            def fake_evaluation(*args, **kwargs):
                run_directory = args[-1]
                prediction = run_directory / "prediction.parquet"
                prediction.write_bytes(b"fixture")
                files = [
                    {
                        "bytes": 7,
                        "featureSet": "A2",
                        "file": "prediction.parquet",
                        "foldKey": "FINAL",
                        "horizonMinutes": 15,
                        "rows": 24,
                        "sha256": "c" * 64,
                    }
                ]
                records = [
                    {
                        "modelName": "HIST_GRADIENT_BOOSTING",
                        "featureSet": "A2",
                        "foldKey": "FINAL",
                        "horizonMinutes": 15,
                        "sliceType": "ALL",
                        "sliceKey": "ALL",
                        "sampleCount": 8,
                        "absoluteErrorSum": 4.0,
                        "squaredErrorSum": 4.0,
                        "actualSum": 8.0,
                        "mae": 0.5,
                        "rmse": 0.707,
                        "wape": 0.5,
                    }
                ]
                return records, files, {15: {"model": "fixture"}}, 24, 1.0, [], 0, 0.0

            with (
                patch("goride_analytics.models.pipeline.load_resumable_feature_artifact", return_value=artifact),
                patch("goride_analytics.models.pipeline.load_baseline_evidence", return_value=baseline),
                patch("goride_analytics.models.pipeline.build_rolling_origin_folds", return_value=(final_fold,)),
                patch("goride_analytics.models.pipeline.count_training_strata", return_value={}),
                patch(
                    "goride_analytics.models.pipeline._search_candidate",
                    return_value=(
                        [],
                        {
                            key: SimpleNamespace(
                                config_id="HGB_C1",
                                to_dict=lambda: {"id": "HGB_C1"},
                            )
                            for key in ("A0", "A1", "A2")
                        },
                        [],
                        1.0,
                    ),
                ),
                patch("goride_analytics.models.pipeline._evaluate_selected", side_effect=fake_evaluation),
                patch(
                    "goride_analytics.models.pipeline.verify_database",
                    return_value=DatabaseMetadata("analytics", "18", "3.6"),
                ),
            ):
                outcome = run_candidate_training(
                    config,
                    feature_run="feature-run",
                    baseline_run="baseline-run",
                    experiment_config=experiment_path,
                    environment={
                        "TEST_ANALYTICS_ROOT": str(root),
                        "ANALYTICS_DATABASE_HOST": "localhost",
                        "ANALYTICS_DATABASE_PORT": "5432",
                        "ANALYTICS_DATABASE_NAME": "analytics",
                        "ANALYTICS_DATABASE_USERNAME": "runner",
                        "ANALYTICS_DATABASE_PASSWORD": "not-logged",
                    },
                    identity=identity,
                    connection_factory=lambda *_args, **_kwargs: _Connection(),
                    repository_factory=lambda _connection: repository,
                )
            required = {
                "candidate-metrics.json",
                "candidate-models.joblib",
                "candidate-predictions-manifest.json",
                "checksums.sha256",
                "model-card.json",
                "model-search.json",
                "system-metrics.json",
                "training-manifest.json",
                "training-quality.json",
            }
            actual_files = {item.name for item in outcome.run_directory.iterdir()}

        self.assertEqual(outcome.quality_status, "PASS")
        self.assertEqual(outcome.raw_prediction_rows, 24)
        self.assertTrue(required.issubset(actual_files))
        self.assertEqual(len(repository.succeeded), 1)
        self.assertFalse(repository.failed)


if __name__ == "__main__":
    unittest.main()
