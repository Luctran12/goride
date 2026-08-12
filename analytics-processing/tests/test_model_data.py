from __future__ import annotations

import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from uuid import UUID

import numpy as np
import pyarrow as pa
import pyarrow.parquet as pq
import yaml

from goride_analytics.evaluation.folds import EvaluationFold
from goride_analytics.features.persistence import PersistedPartition, ResumableFeatureArtifact
from goride_analytics.hashing import file_sha256
from goride_analytics.models.data import (
    count_training_strata,
    iter_evaluation_batches,
    load_sampled_training_set,
)
from goride_analytics.models.experiment import load_candidate_experiment


class CandidateModelDataTests(unittest.TestCase):
    def _fixture(self, root: Path):
        source = Path(__file__).resolve().parents[1] / "configs" / "porto-phase6-hgb.yml"
        experiment_mapping = yaml.safe_load(source.read_text(encoding="utf-8"))
        experiment_mapping["training_sample"]["positive_row_quota"] = 2
        experiment_mapping["training_sample"]["zero_row_quota"] = 2
        experiment_path = root / "experiment.yml"
        experiment_path.write_text(yaml.safe_dump(experiment_mapping, sort_keys=False), encoding="utf-8")
        experiment = load_candidate_experiment(experiment_path)
        start = datetime(2014, 1, 1, tzinfo=timezone.utc)
        rows = []
        for index in range(16):
            target = start + timedelta(minutes=15 * index)
            actual = 0 if index % 2 == 0 else index
            rows.append(
                {
                    "cell_id": f"cell-{index % 2}",
                    "grid_x": index % 2,
                    "grid_y": 1,
                    "inference_cutoff_utc": target - timedelta(minutes=15),
                    "target_bucket_start_utc": target,
                    "horizon_minutes": 15,
                    "target_trip_requests": actual,
                    "hour_sin": 0.0,
                    "hour_cos": 1.0,
                    "day_of_week": 2,
                    "is_weekend": False,
                    "lag_1": index,
                    "lag_2": index,
                    "lag_4": index,
                    "lag_96": None,
                    "lag_672": None,
                    "rolling_mean_4": float(index),
                    "rolling_mean_12": float(index),
                    "rolling_mean_96": None,
                    "rolling_mean_672": None,
                    "neighbor_demand_lag_1": index,
                }
            )
        path = root / "features.parquet"
        pq.write_table(pa.Table.from_pylist(rows), path, compression="zstd")
        partition = PersistedPartition("fixture", path, len(rows), path.stat().st_size, file_sha256(path))
        artifact = ResumableFeatureArtifact(
            source_run_id="feature-run",
            source_run_directory=root,
            feature_artifact_id=UUID(int=1),
            feature_set_version="feature-v1",
            source_cutoff_utc=start + timedelta(hours=4),
            row_count=len(rows),
            byte_count=path.stat().st_size,
            sha256="a" * 64,
            quality_status="PASS",
            quality_results=(),
            partitions=(partition,),
            cell_size_meters=500,
        )
        fold = EvaluationFold(
            key="D1",
            split_role="DEVELOPMENT",
            train_from_utc=start,
            train_to_utc=start + timedelta(hours=2),
            evaluate_from_utc=start + timedelta(hours=2),
            evaluate_to_utc=start + timedelta(hours=4),
        )
        return artifact, experiment, fold

    def test_sampling_is_bounded_deterministic_stratified_and_weighted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            artifact, experiment, fold = self._fixture(Path(temporary))
            counts = count_training_strata(artifact, (fold,), (15,))
            first = load_sampled_training_set(artifact, experiment, fold, 15, counts[("D1", 15)])
            second = load_sampled_training_set(artifact, experiment, fold, 15, counts[("D1", 15)])

        self.assertEqual((first.population_positive, first.population_zero), (4, 4))
        self.assertEqual((first.sampled_positive, first.sampled_zero), (2, 2))
        np.testing.assert_array_equal(first.features, second.features)
        np.testing.assert_array_equal(first.target, second.target)
        self.assertEqual(set(first.sample_weight.tolist()), {2.0})

    def test_evaluation_iterator_preserves_full_chronological_population(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            artifact, experiment, fold = self._fixture(Path(temporary))
            batches = list(iter_evaluation_batches(artifact, experiment, fold, 15, batch_size=3))

        targets = [target for batch in batches for target in (batch.target_bucket_start_utc or [])]
        self.assertEqual(sum(int(batch.actual.size) for batch in batches), 8)
        self.assertEqual(targets, sorted(targets))
        self.assertTrue(all(batch.cell_ids is not None for batch in batches))


if __name__ == "__main__":
    unittest.main()
