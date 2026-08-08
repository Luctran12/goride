from __future__ import annotations

import io
import json
import tempfile
import unittest
from pathlib import Path
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


if __name__ == "__main__":
    unittest.main()
