from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import yaml

from goride_analytics.config import load_config
from goride_analytics.errors import ExtractionError
from goride_analytics.hashing import file_sha256
from goride_analytics.manifest import validate_dataset
from goride_analytics.thesis import run_thesis_verification

from support import create_data_root, write_config


ROLES = (
    "extraction",
    "feature-g500",
    "feature-g1000",
    "feature-g2000",
    "baseline-g500",
    "baseline-g1000",
    "baseline-g2000",
    "candidate-g500",
    "candidate-g1000",
    "candidate-g2000",
    "forecast",
    "backfill",
    "rollback-failure",
)


class ThesisEvidenceTests(unittest.TestCase):
    def test_verifies_frozen_lineage_checksums_privacy_and_storage(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = create_data_root(base / "data")
            config_path = write_config(base / "profile.yml")
            contract_path = self._write_evidence(root, base / "evidence.yml")
            output = base / "verified"
            with patch.dict("os.environ", {"TEST_ANALYTICS_ROOT": str(root)}, clear=False):
                config = load_config(config_path)
                dataset = validate_dataset(config)
                outcome = run_thesis_verification(
                    config,
                    dataset,
                    evidence_contract=contract_path,
                    output_directory=output,
                )

            report = json.loads(outcome.report_path.read_text(encoding="utf-8"))
            self.assertEqual(report["status"], "PASS")
            self.assertEqual(report["claimScope"], "RESEARCH_DEMONSTRATION")
            self.assertEqual(report["lineage"]["cellSizesMeters"], [500, 1000, 2000])
            self.assertEqual(report["lineage"]["minimumAggregateCount"], 3)
            self.assertGreater(outcome.verified_files, len(ROLES))
            self.assertGreater(outcome.verified_bytes, 0)
            self.assertEqual(
                (output / "checksums.sha256").read_text(encoding="utf-8").split()[0],
                outcome.report_sha256,
            )

    def test_rejects_tampered_frozen_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = create_data_root(base / "data")
            config_path = write_config(base / "profile.yml")
            contract_path = self._write_evidence(root, base / "evidence.yml")
            target = root / "runs" / "fixture" / "feature-g500" / "feature-g500-manifest.json"
            target.write_text('{"cellSizeMeters":999}\n', encoding="utf-8")
            with patch.dict("os.environ", {"TEST_ANALYTICS_ROOT": str(root)}, clear=False):
                config = load_config(config_path)
                dataset = validate_dataset(config)
                with self.assertRaises(ExtractionError) as raised:
                    run_thesis_verification(
                        config,
                        dataset,
                        evidence_contract=contract_path,
                        output_directory=base / "verified",
                    )
            self.assertEqual(raised.exception.code, "THESIS_EVIDENCE_CHECKSUM_MISMATCH")

    def _write_evidence(self, root: Path, target: Path) -> Path:
        experiment_hash = "e" * 64
        model_sha = "m" * 64
        forecast_id = "791b9584-c448-516e-94b4-0ab50aa59e96"
        manifests: dict[str, dict[str, object]] = {
            "extraction": {"qualityStatus": "WARN"},
            "feature-g500": {"cellSizeMeters": 500},
            "feature-g1000": {"cellSizeMeters": 1000},
            "feature-g2000": {"cellSizeMeters": 2000},
            "baseline-g500": {"models": ["HISTORICAL_MEAN", "SEASONAL_NAIVE"], "qualityStatus": "PASS"},
            "baseline-g1000": {"models": ["HISTORICAL_MEAN", "SEASONAL_NAIVE"], "qualityStatus": "PASS"},
            "baseline-g2000": {"models": ["HISTORICAL_MEAN", "SEASONAL_NAIVE"], "qualityStatus": "PASS"},
            "candidate-g500": {
                "experimentHash": experiment_hash,
                "modelVersion": "candidate-v1",
                "modelSha256": model_sha,
                "qualityStatus": "PASS",
            },
            "candidate-g1000": {"qualityStatus": "PASS"},
            "candidate-g2000": {"qualityStatus": "PASS"},
            "forecast": {
                "modelVersion": "candidate-v1",
                "artifactSha256": model_sha,
                "forecastRunId": forecast_id,
                "approvalScope": "RESEARCH_DEMONSTRATION",
                "horizonsMinutes": [15, 30, 60],
                "forecastRows": 9,
            },
            "backfill": {
                "forecastRunId": forecast_id,
                "qualityStatus": "PASS",
                "eligibleRows": 9,
                "updatedRows": 9,
            },
            "rollback-failure": {"errorCode": "INJECTED_ROLLBACK"},
        }
        run_entries = []
        for role in ROLES:
            directory = root / "runs" / "fixture" / role
            directory.mkdir(parents=True)
            name = "failure.json" if role == "rollback-failure" else f"{role}-manifest.json"
            manifest_path = directory / name
            manifest_path.write_text(
                json.dumps(manifests[role], sort_keys=True) + "\n",
                encoding="utf-8",
            )
            (directory / "checksums.sha256").write_text(
                f"{file_sha256(manifest_path)}  {name}\n",
                encoding="utf-8",
            )
            run_entries.append({
                "role": role,
                "path": f"runs/fixture/{role}",
                "manifest": name,
            })
        dataset = json.loads(
            (root / "manifests" / "datasets" / "test-v1.json").read_text(encoding="utf-8")
        )
        contract = {
            "schema_version": 1,
            "frozen": True,
            "claim_scope": "RESEARCH_DEMONSTRATION",
            "dataset_sha256": dataset["sha256"],
            "minimum_aggregate_count": 3,
            "expected": {
                "cell_sizes_meters": [500, 1000, 2000],
                "horizons_minutes": [15, 30, 60],
                "experiment_hash": experiment_hash,
                "model_version": "candidate-v1",
                "forecast_run_id": forecast_id,
                "rollback_failure_code": "INJECTED_ROLLBACK",
            },
            "runs": run_entries,
            "privacy": {
                "forbidden_api_fields": [
                    "tripId", "userId", "driverId", "passengerId",
                    "email", "phone", "artifactUri",
                ]
            },
        }
        target.write_text(yaml.safe_dump(contract, sort_keys=False), encoding="utf-8")
        return target


if __name__ == "__main__":
    unittest.main()
