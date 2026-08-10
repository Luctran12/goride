from __future__ import annotations

import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

import joblib

from goride_analytics.config import load_config
from goride_analytics.errors import ExtractionError
from goride_analytics.hashing import file_sha256
from goride_analytics.operations.artifact import load_operational_model_artifact

from support import create_data_root, valid_config_mapping, write_config


class OperationalModelArtifactTests(unittest.TestCase):
    def _artifact(self, base: Path):
        root = create_data_root(base / "data")
        config = load_config(write_config(base / "profile.yml", valid_config_mapping()))
        run_id = "20260101T000000000000Z-" + "a" * 12 + "-test-profile-" + config.config_hash[:12]
        directory = root / "runs" / "training" / run_id
        directory.mkdir(parents=True)
        bundle = {
            "experimentHash": "b" * 64,
            "featureNames": ["hour_sin", "lag_1"],
            "featureSet": "A2",
            "cellSizeMeters": 500,
            "horizonModels": {15: None, 30: None, 60: None},
            "modelVersion": "candidate-v1",
            "selectedConfiguration": {"id": "HGB_C2"},
            "trainingCutoffUtc": "2014-05-01T00:00:00Z",
        }
        joblib.dump(bundle, directory / "candidate-models.joblib")
        model_sha = file_sha256(directory / "candidate-models.joblib")
        values = {
            "run-manifest.json": {
                "runId": run_id,
                "runType": "training",
                "profile": config.profile.name,
                "configHash": config.config_hash,
            },
            "training-manifest.json": {
                "cellSizeMeters": 500,
                "experimentHash": "b" * 64,
                "featureSetVersion": "feature-v1",
                "modelSha256": model_sha,
                "modelVersion": "candidate-v1",
                "qualityStatus": "PASS",
            },
            "training-quality.json": {"overallStatus": "PASS", "results": []},
            "model-card.json": {
                "featureSet": "A2",
                "modelSha256": model_sha,
                "status": "VALIDATED_CANDIDATE",
            },
        }
        for name, value in values.items():
            (directory / name).write_text(
                json.dumps(value, sort_keys=True),
                encoding="utf-8",
            )
        signed = [directory / "candidate-models.joblib"] + [
            directory / name for name in values
        ]
        (directory / "checksums.sha256").write_text(
            "".join(f"{file_sha256(path)}  {path.name}\n" for path in sorted(signed)),
            encoding="utf-8",
        )
        return config, root, run_id, directory

    def test_loads_checksum_gated_bundle_with_all_horizons(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            config, root, run_id, _directory = self._artifact(Path(temporary))
            artifact = load_operational_model_artifact(
                config,
                root=root,
                training_run=run_id,
            )

        self.assertEqual(artifact.model_version, "candidate-v1")
        self.assertEqual(artifact.feature_names, ("hour_sin", "lag_1"))
        self.assertEqual(
            artifact.training_cutoff_utc,
            datetime(2014, 5, 1, tzinfo=timezone.utc),
        )

    def test_rejects_model_tampering_before_deserialization(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            config, root, run_id, directory = self._artifact(Path(temporary))
            with (directory / "candidate-models.joblib").open("ab") as output:
                output.write(b"tamper")
            with self.assertRaises(ExtractionError) as raised:
                load_operational_model_artifact(
                    config,
                    root=root,
                    training_run=run_id,
                )

        self.assertEqual(raised.exception.code, "MODEL_ARTIFACT_CHECKSUM_MISMATCH")


if __name__ == "__main__":
    unittest.main()
