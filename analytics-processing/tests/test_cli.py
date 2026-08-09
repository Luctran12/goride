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

    def test_stage_validates_then_returns_explicit_not_implemented(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = create_data_root(base / "data")
            config_path = write_config(base / "profile.yml")
            stdout = io.StringIO()
            stderr = io.StringIO()
            with patch.dict("os.environ", {"TEST_ANALYTICS_ROOT": str(root)}, clear=False):
                exit_code = main(
                    ["train", "--config", str(config_path)],
                    stdout=stdout,
                    stderr=stderr,
                )

        self.assertEqual(exit_code, ExitCode.STAGE_NOT_IMPLEMENTED)
        self.assertEqual(stdout.getvalue(), "")
        records = [json.loads(line) for line in stderr.getvalue().splitlines()]
        self.assertEqual(records[-1]["errorCode"], "STAGE_NOT_IMPLEMENTED")

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
                    ],
                    stdout=stdout,
                    stderr=io.StringIO(),
                    feature_runner=build_features,
                )

        self.assertEqual(exit_code, ExitCode.SUCCESS)
        self.assertEqual(captured["cell_size_meters"], 1000)
        self.assertEqual(captured["extraction_run"], "runs/extraction/source-run")
        self.assertEqual(json.loads(stdout.getvalue())["qualityStatus"], "PASS")


if __name__ == "__main__":
    unittest.main()
