from __future__ import annotations

import unittest
import uuid
from dataclasses import replace
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace

from goride_analytics.config import load_config
from goride_analytics.errors import DatabaseError, ExtractionError
from goride_analytics.operations.config import OperationsConfig
from goride_analytics.operations.forecast import (
    ForecastFeature,
    RegisteredModel,
    _quality,
    _validate_model,
    parse_inference_cutoff,
)

from support import valid_config_mapping, write_config
import tempfile
from pathlib import Path


class ForecastOperationsTests(unittest.TestCase):
    def _config(self, base: Path):
        return load_config(write_config(base / "profile.yml", valid_config_mapping()))

    @staticmethod
    def _operations(base: Path) -> OperationsConfig:
        return OperationsConfig(
            path=base / "operations.yml",
            config_hash="a" * 64,
            version="v1",
            approval_scope="RESEARCH_DEMONSTRATION",
            stale_after_minutes=30,
            actual_watermark_delay_minutes=15,
            forecast_retention_days=90,
            maximum_rows_per_run=10000,
            allowed_purposes=("EVALUATION", "PUBLISHED"),
        )

    def test_cutoff_must_be_utc_and_bucket_aligned(self) -> None:
        self.assertEqual(
            parse_inference_cutoff("2014-06-01T00:00:00Z", 15),
            datetime(2014, 6, 1, tzinfo=timezone.utc),
        )
        for value in ("2014-06-01T00:01:00Z", "2014-06-01T00:00:00"):
            with self.assertRaises(ExtractionError):
                parse_inference_cutoff(value, 15)

    def test_only_approved_compatible_model_can_infer(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            config = self._config(base)
            cutoff = datetime(2014, 6, 1, tzinfo=timezone.utc)
            now = cutoff + timedelta(minutes=5)
            model = RegisteredModel(
                uuid.uuid4(), "model", "v1", "VALIDATED",
                config.profile.name, config.dataset.version,
                "TRIP_STARTED_PROXY", "feature-v1",
                config.spatial.grid_version, 500, 15,
                datetime(2014, 5, 1, tzinfo=timezone.utc),
                "runs/training/a/candidate-models.joblib", "b" * 64,
                "RESEARCH_DEMONSTRATION",
            )
            with self.assertRaises(DatabaseError) as raised:
                _validate_model(config, model, cutoff, "EVALUATION", now, self._operations(base))
            self.assertEqual(raised.exception.code, "MODEL_NOT_APPROVED")

            approved = replace(model, lifecycle_status="APPROVED")
            _validate_model(
                config,
                approved,
                cutoff,
                "EVALUATION",
                now,
                self._operations(base),
            )
            with self.assertRaises(ExtractionError) as stale:
                _validate_model(
                    config,
                    approved,
                    cutoff,
                    "PUBLISHED",
                    cutoff + timedelta(minutes=31),
                    self._operations(base),
                )
            self.assertEqual(stale.exception.code, "FORECAST_INPUT_STALE")

    def test_quality_rejects_horizon_population_drift(self) -> None:
        cutoff = datetime(2014, 6, 1, tzinfo=timezone.utc)
        features = [
            ForecastFeature("a", 1, 1, cutoff, cutoff + timedelta(minutes=15), 15, (1.0,)),
            ForecastFeature("a", 1, 1, cutoff, cutoff + timedelta(minutes=30), 30, (1.0,)),
            ForecastFeature("b", 2, 2, cutoff, cutoff + timedelta(minutes=30), 30, (1.0,)),
        ]
        predictions = [SimpleNamespace(feature=item, predicted_demand=1.0) for item in features]
        results = _quality(features, predictions, (15, 30), cutoff)
        statuses = {item.rule_code: item.result_status for item in results}
        self.assertEqual(statuses["FORECAST_HORIZON_POPULATION"], "FAIL")


if __name__ == "__main__":
    unittest.main()
