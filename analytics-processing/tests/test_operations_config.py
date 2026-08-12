from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from goride_analytics.errors import ConfigurationError
from goride_analytics.operations.config import load_operations_config


VALID = """\
version: porto-operations-v1
approval_scope: RESEARCH_DEMONSTRATION
stale_after_minutes: 30
actual_watermark_delay_minutes: 15
forecast_retention_days: 90
maximum_rows_per_run: 10000
allowed_purposes: [EVALUATION, PUBLISHED]
"""


class OperationsConfigTests(unittest.TestCase):
    def test_loads_frozen_research_operations_contract(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "operations.yml"
            path.write_text(VALID, encoding="utf-8")
            config = load_operations_config(path)

        self.assertEqual(config.approval_scope, "RESEARCH_DEMONSTRATION")
        self.assertEqual(config.allowed_purposes, ("EVALUATION", "PUBLISHED"))
        self.assertEqual(config.actual_watermark_delay_minutes, 15)
        self.assertEqual(len(config.config_hash), 64)

    def test_rejects_production_scope_for_porto(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "operations.yml"
            path.write_text(
                VALID.replace("RESEARCH_DEMONSTRATION", "PRODUCTION"),
                encoding="utf-8",
            )
            with self.assertRaises(ConfigurationError) as raised:
                load_operations_config(path)

        self.assertEqual(raised.exception.code, "OPERATIONS_CONFIG_INVALID")


if __name__ == "__main__":
    unittest.main()
