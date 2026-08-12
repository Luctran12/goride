from __future__ import annotations

import io
import json
import tempfile
import unittest
import uuid
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from goride_analytics.cli import main
from goride_analytics.errors import ExitCode
from goride_analytics.errors import DataQualityError

from support import create_data_root, write_config


class AnalyticsCliTests(unittest.TestCase):
    def test_validate_config_returns_machine_readable_success(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = create_data_root(base / "data")
            config_path = write_config(base / "profile.yml")
            stdout = io.StringIO()
            stderr = io.StringIO()
            with patch.dict("os.environ", {"TEST_ANALYTICS_ROOT": str(root)}, clear=False):
                exit_code = main(
                    ["validate-config", "--config", str(config_path)],
                    stdout=stdout,
                    stderr=stderr,
                )

        self.assertEqual(exit_code, ExitCode.SUCCESS)
        payload = json.loads(stdout.getvalue())
        self.assertEqual(payload["status"], "VALID")
        self.assertEqual(payload["dataset"]["status"], "VALID")
        log = json.loads(stderr.getvalue())
        self.assertEqual(log["event"], "analytics_configuration_validated")

    def test_forecast_dispatches_operational_contract(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = create_data_root(base / "data")
            config_path = write_config(base / "profile.yml")
            operations_path = base / "operations.yml"
            operations_path.write_text("frozen: true\n", encoding="utf-8")
            stdout = io.StringIO()
            stderr = io.StringIO()
            captured = {}

            class Outcome:
                artifact_run_id = "forecast-run"
                processing_run_id = uuid.UUID(int=10)
                forecast_run_id = uuid.UUID(int=11)
                forecast_rows = 30
                idempotent = False
                model_version = "candidate-v1"
                quality_status = "PASS"

                @staticmethod
                def to_dict():
                    return {
                        "artifactRunId": "forecast-run",
                        "forecastRun": {"forecastRows": 30},
                        "qualityStatus": "PASS",
                    }

            def forecast(_config, **kwargs):
                captured.update(kwargs)
                return Outcome()

            with patch.dict("os.environ", {"TEST_ANALYTICS_ROOT": str(root)}, clear=False):
                exit_code = main(
                    [
                        "forecast",
                        "--config",
                        str(config_path),
                        "--operations-config",
                        str(operations_path),
                        "--model-version",
                        "candidate-v1",
                        "--inference-cutoff",
                        "2014-06-01T00:00:00Z",
                        "--purpose",
                        "EVALUATION",
                    ],
                    stdout=stdout,
                    stderr=stderr,
                    forecast_runner=forecast,
                )

        self.assertEqual(exit_code, ExitCode.SUCCESS)
        self.assertEqual(captured["model_version"], "candidate-v1")
        self.assertEqual(captured["purpose"], "EVALUATION")
        self.assertEqual(json.loads(stdout.getvalue())["qualityStatus"], "PASS")

    def test_invalid_cli_arguments_use_config_exit_code(self) -> None:
        stderr = io.StringIO()
        exit_code = main(["validate-config"], stdout=io.StringIO(), stderr=stderr)

        self.assertEqual(exit_code, ExitCode.CONFIG_INVALID)
        self.assertEqual(json.loads(stderr.getvalue())["errorCode"], "CLI_ARGUMENT_INVALID")

    def test_extract_requires_explicit_interval(self) -> None:
        stderr = io.StringIO()
        exit_code = main(
            ["extract", "--config", "profile.yml"],
            stdout=io.StringIO(),
            stderr=stderr,
        )
        self.assertEqual(exit_code, ExitCode.CONFIG_INVALID)
        self.assertEqual(json.loads(stderr.getvalue())["errorCode"], "CLI_ARGUMENT_INVALID")

    def test_extract_quality_failure_uses_dedicated_exit_code(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = create_data_root(base / "data")
            config_path = write_config(base / "profile.yml")
            stderr = io.StringIO()

            def fail_quality(*_args, **_kwargs):
                raise DataQualityError(["DQ_DUPLICATE_TRIP"], "run-id")

            with patch.dict("os.environ", {"TEST_ANALYTICS_ROOT": str(root)}, clear=False):
                exit_code = main(
                    [
                        "extract",
                        "--config",
                        str(config_path),
                        "--from-utc",
                        "2013-07-01T00:00:00Z",
                        "--cutoff-utc",
                        "2013-07-02T00:00:00Z",
                    ],
                    stdout=io.StringIO(),
                    stderr=stderr,
                    extraction_runner=fail_quality,
                )
        self.assertEqual(exit_code, ExitCode.DATA_QUALITY_FAILED)
        self.assertEqual(json.loads(stderr.getvalue().splitlines()[-1])["errorCode"], "DATA_QUALITY_FAILED")

    def test_build_features_dispatches_and_returns_artifact_contract(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = create_data_root(base / "data")
            config_path = write_config(base / "profile.yml")
            stdout = io.StringIO()
            captured = {}

            class Outcome:
                artifact_run_id = "feature-run"
                feature_set_version = "feature-v1"
                processing_run_id = uuid.UUID(int=1)
                quality = SimpleNamespace(overall_status="PASS")
                artifact = SimpleNamespace(sha256="a" * 64)

                @staticmethod
                def to_dict():
                    return {
                        "artifactRunId": "feature-run",
                        "featureArtifact": {"sha256": "a" * 64},
                        "qualityStatus": "PASS",
                    }

            def build_features(_config, **kwargs):
                captured.update(kwargs)
                return Outcome()

            with patch.dict("os.environ", {"TEST_ANALYTICS_ROOT": str(root)}, clear=False):
                exit_code = main(
                    [
                        "build-features",
                        "--config",
                        str(config_path),
                        "--extraction-run",
                        "runs/extraction/source-run",
                        "--cell-size-meters",
                        "1000",
                        "--artifact-only",
                    ],
                    stdout=stdout,
                    stderr=io.StringIO(),
                    feature_runner=build_features,
                )

        self.assertEqual(exit_code, ExitCode.SUCCESS)
        self.assertEqual(captured["cell_size_meters"], 1000)
        self.assertFalse(captured["persist_database"])
        self.assertEqual(captured["extraction_run"], "runs/extraction/source-run")
        self.assertEqual(json.loads(stdout.getvalue())["qualityStatus"], "PASS")

    def test_persist_features_dispatches_verified_source_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = create_data_root(base / "data")
            config_path = write_config(base / "profile.yml")
            stdout = io.StringIO()
            captured = {}

            class Outcome:
                artifact_run_id = "persistence-run"
                source_artifact_run_id = "source-feature-run"
                feature_set_version = "feature-v1"
                processing_run_id = uuid.UUID(int=2)
                quality_status = "WARN"
                sha256 = "b" * 64

                @staticmethod
                def to_dict():
                    return {
                        "artifactRunId": "persistence-run",
                        "featureArtifact": {
                            "sourceArtifactRunId": "source-feature-run"
                        },
                        "qualityStatus": "WARN",
                    }

            def persist_features(_config, **kwargs):
                captured.update(kwargs)
                return Outcome()

            with patch.dict(
                "os.environ", {"TEST_ANALYTICS_ROOT": str(root)}, clear=False
            ):
                exit_code = main(
                    [
                        "persist-features",
                        "--config",
                        str(config_path),
                        "--feature-run",
                        "source-feature-run",
                    ],
                    stdout=stdout,
                    stderr=io.StringIO(),
                    persistence_runner=persist_features,
                )

        self.assertEqual(exit_code, ExitCode.SUCCESS)
        self.assertEqual(captured["feature_run"], "source-feature-run")
        self.assertEqual(json.loads(stdout.getvalue())["qualityStatus"], "WARN")

    def test_evaluate_dispatches_verified_feature_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = create_data_root(base / "data")
            config_path = write_config(base / "profile.yml")
            stdout = io.StringIO()
            captured = {}

            class Outcome:
                artifact_run_id = "evaluation-run"
                source_artifact_run_id = "source-feature-run"
                feature_set_version = "feature-v1"
                processing_run_id = uuid.UUID(int=3)
                quality_status = "PASS"
                raw_prediction_rows = 120
                raw_prediction_sha256 = "c" * 64

                @staticmethod
                def to_dict():
                    return {
                        "artifactRunId": "evaluation-run",
                        "evaluationArtifact": {"rawPredictionRows": 120},
                        "qualityStatus": "PASS",
                    }

            def evaluate(_config, **kwargs):
                captured.update(kwargs)
                return Outcome()

            with patch.dict(
                "os.environ", {"TEST_ANALYTICS_ROOT": str(root)}, clear=False
            ):
                exit_code = main(
                    [
                        "evaluate",
                        "--config",
                        str(config_path),
                        "--feature-run",
                        "source-feature-run",
                    ],
                    stdout=stdout,
                    stderr=io.StringIO(),
                    evaluation_runner=evaluate,
                )

        self.assertEqual(exit_code, ExitCode.SUCCESS)
        self.assertEqual(captured["feature_run"], "source-feature-run")
        self.assertEqual(json.loads(stdout.getvalue())["qualityStatus"], "PASS")

    def test_train_dispatches_frozen_candidate_contract(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = create_data_root(base / "data")
            config_path = write_config(base / "profile.yml")
            experiment_path = base / "experiment.yml"
            experiment_path.write_text("frozen: true\n", encoding="utf-8")
            captured = {}

            class Outcome:
                artifact_run_id = "training-run"
                baseline_artifact_run_id = "baseline-run"
                source_artifact_run_id = "feature-run"
                experiment_hash = "d" * 64
                model_sha256 = "e" * 64
                model_version = "candidate-v1"
                processing_run_id = uuid.UUID(int=4)
                quality_status = "PASS"
                raw_prediction_rows = 360
                raw_prediction_sha256 = "f" * 64

                @staticmethod
                def to_dict():
                    return {
                        "artifactRunId": "training-run",
                        "candidateArtifact": {"modelVersion": "candidate-v1"},
                        "qualityStatus": "PASS",
                    }

            def train(_config, **kwargs):
                captured.update(kwargs)
                return Outcome()

            stdout = io.StringIO()
            with patch.dict("os.environ", {"TEST_ANALYTICS_ROOT": str(root)}, clear=False):
                exit_code = main(
                    [
                        "train",
                        "--config",
                        str(config_path),
                        "--feature-run",
                        "feature-run",
                        "--baseline-run",
                        "baseline-run",
                        "--experiment-config",
                        str(experiment_path),
                    ],
                    stdout=stdout,
                    stderr=io.StringIO(),
                    training_runner=train,
                )

        self.assertEqual(exit_code, ExitCode.SUCCESS)
        self.assertEqual(captured["feature_run"], "feature-run")
        self.assertEqual(captured["baseline_run"], "baseline-run")
        self.assertEqual(captured["experiment_config"], str(experiment_path))

    def test_register_and_approve_model_dispatch_audited_lifecycle(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = create_data_root(base / "data")
            config_path = write_config(base / "profile.yml")
            calls = []

            class Outcome:
                model_version_id = uuid.UUID(int=20)
                model_name = "research-model"
                model_version = "candidate-v1"
                lifecycle_status = "VALIDATED"
                artifact_sha256 = "a" * 64
                approval_scope = "RESEARCH_DEMONSTRATION"
                idempotent = False

                @staticmethod
                def to_dict():
                    return {"modelRegistry": {"lifecycleStatus": "VALIDATED"}}

            def register(_config, **kwargs):
                calls.append(("register", kwargs))
                return Outcome()

            def transition(_config, **kwargs):
                calls.append(("transition", kwargs))
                Outcome.lifecycle_status = kwargs["target_status"]
                return Outcome()

            with patch.dict("os.environ", {"TEST_ANALYTICS_ROOT": str(root)}, clear=False):
                register_exit = main(
                    [
                        "register-model", "--config", str(config_path),
                        "--training-run", "training-run", "--actor", "admin",
                        "--reason", "research evidence",
                    ],
                    stdout=io.StringIO(), stderr=io.StringIO(),
                    registration_runner=register,
                )
                approve_exit = main(
                    [
                        "approve-model", "--config", str(config_path),
                        "--model-version", "candidate-v1", "--actor", "admin",
                        "--reason", "research demonstration only",
                    ],
                    stdout=io.StringIO(), stderr=io.StringIO(),
                    transition_runner=transition,
                )

        self.assertEqual(register_exit, ExitCode.SUCCESS)
        self.assertEqual(approve_exit, ExitCode.SUCCESS)
        self.assertEqual(calls[0][1]["training_run"], "training-run")
        self.assertEqual(calls[1][1]["target_status"], "APPROVED")

    def test_backfill_dispatches_watermark_contract(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = create_data_root(base / "data")
            config_path = write_config(base / "profile.yml")
            operations_path = base / "operations.yml"
            operations_path.write_text("frozen: true\n", encoding="utf-8")
            captured = {}

            class Outcome:
                artifact_run_id = "backfill-run"
                processing_run_id = uuid.UUID(int=30)
                forecast_run_id = uuid.UUID(int=31)
                eligible_rows = 10
                updated_rows = 10
                quality_status = "PASS"

                @staticmethod
                def to_dict():
                    return {"actualBackfill": {"updatedRows": 10}, "qualityStatus": "PASS"}

            def backfill(_config, **kwargs):
                captured.update(kwargs)
                return Outcome()

            with patch.dict("os.environ", {"TEST_ANALYTICS_ROOT": str(root)}, clear=False):
                exit_code = main(
                    [
                        "backfill-actual", "--config", str(config_path),
                        "--operations-config", str(operations_path),
                        "--forecast-run", str(uuid.UUID(int=31)),
                        "--watermark-utc", "2014-06-01T01:15:00Z",
                    ],
                    stdout=io.StringIO(), stderr=io.StringIO(),
                    backfill_runner=backfill,
                )

        self.assertEqual(exit_code, ExitCode.SUCCESS)
        self.assertEqual(captured["watermark_utc"], "2014-06-01T01:15:00Z")


if __name__ == "__main__":
    unittest.main()
