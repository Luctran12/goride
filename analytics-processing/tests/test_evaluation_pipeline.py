from __future__ import annotations

import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from uuid import UUID

import pyarrow as pa
import pyarrow.parquet as pq

from goride_analytics.config import load_config
from goride_analytics.evaluation.baselines import DemandCube
from goride_analytics.evaluation.folds import EvaluationFold
from goride_analytics.evaluation.pipeline import _evaluate_rows
from goride_analytics.features.persistence import (
    PersistedPartition,
    ResumableFeatureArtifact,
)
from goride_analytics.hashing import file_sha256
from goride_analytics.runs import GitState, build_run_identity

from support import write_config


class EvaluationPipelineTests(unittest.TestCase):
    def test_raw_predictions_metrics_and_model_populations_share_one_source(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            config = load_config(write_config(root / "profile.yml"))
            start = datetime(2013, 12, 1, tzinfo=timezone.utc)
            evaluate_from = datetime(2014, 1, 1, tzinfo=timezone.utc)
            evaluate_to = evaluate_from + timedelta(days=1)
            cube = DemandCube(
                cells=("cell-a",),
                from_utc=start,
                to_utc=evaluate_to,
                bucket_minutes=15,
                source_timezone="Europe/Lisbon",
            )
            bucket = start
            while bucket < evaluate_to:
                cube.set("cell-a", bucket, bucket.hour % 4)
                bucket += timedelta(minutes=15)
            rows = []
            target = evaluate_from
            while target < evaluate_to:
                for horizon in (15, 30, 60):
                    rows.append(
                        {
                            "cell_id": "cell-a",
                            "inference_cutoff_utc": target - timedelta(minutes=horizon),
                            "target_bucket_start_utc": target,
                            "horizon_minutes": horizon,
                            "target_trip_requests": target.hour % 4,
                        }
                    )
                target += timedelta(minutes=15)
            partition_path = root / "features.parquet"
            pq.write_table(pa.Table.from_pylist(rows), partition_path, compression="zstd")
            partition = PersistedPartition(
                key="2014-01",
                path=partition_path,
                row_count=len(rows),
                byte_count=partition_path.stat().st_size,
                sha256=file_sha256(partition_path),
            )
            artifact = ResumableFeatureArtifact(
                source_run_id="feature-run",
                source_run_directory=root,
                feature_artifact_id=UUID(int=1),
                feature_set_version="feature-v1",
                source_cutoff_utc=evaluate_to,
                row_count=len(rows),
                byte_count=partition.byte_count,
                sha256="a" * 64,
                quality_status="PASS",
                quality_results=(),
                partitions=(partition,),
            )
            fold = EvaluationFold(
                key="D1",
                split_role="DEVELOPMENT",
                train_from_utc=start,
                train_to_utc=evaluate_from,
                evaluate_from_utc=evaluate_from,
                evaluate_to_utc=evaluate_to,
            )
            identity = build_run_identity(
                config,
                "evaluation",
                created_at=datetime(2026, 1, 1, tzinfo=timezone.utc),
                git_state=GitState("f" * 40, False),
            )
            prediction_root = root / "raw-predictions"
            prediction_root.mkdir()

            metrics, files, raw_rows, metadata = _evaluate_rows(
                config,
                artifact,
                cube,
                (fold,),
                identity,
                prediction_root,
            )

            stored_rows = sum(
                pq.ParquetFile(prediction_root.parent / item["file"]).metadata.num_rows
                for item in files
            )

        self.assertEqual(raw_rows, len(rows) * 2)
        self.assertEqual(stored_rows, raw_rows)
        self.assertEqual(len(files), 2)
        self.assertTrue(all(item["rows"] == len(rows) for item in files))
        self.assertEqual(
            {
                (record["modelName"], record["horizonMinutes"])
                for record in metrics
                if record["sliceType"] == "ALL"
            },
            {
                (model, horizon)
                for model in ("HISTORICAL_MEAN", "SEASONAL_NAIVE")
                for horizon in (15, 30, 60)
            },
        )
        self.assertIn("quantileThresholdsByFold", metadata)


if __name__ == "__main__":
    unittest.main()
