from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

import yaml

from ..errors import ConfigurationError
from ..hashing import canonical_mapping_sha256
from .registry import APPROVAL_SCOPE


@dataclass(frozen=True)
class OperationsConfig:
    path: Path
    config_hash: str
    version: str
    approval_scope: str
    stale_after_minutes: int
    actual_watermark_delay_minutes: int
    forecast_retention_days: int
    maximum_rows_per_run: int
    allowed_purposes: tuple[str, ...]

    def to_dict(self) -> dict[str, object]:
        return {
            "actualWatermarkDelayMinutes": self.actual_watermark_delay_minutes,
            "allowedPurposes": list(self.allowed_purposes),
            "approvalScope": self.approval_scope,
            "configHash": self.config_hash,
            "forecastRetentionDays": self.forecast_retention_days,
            "maximumRowsPerRun": self.maximum_rows_per_run,
            "staleAfterMinutes": self.stale_after_minutes,
            "version": self.version,
        }


def _positive(value: object, field: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ConfigurationError(
            "OPERATIONS_CONFIG_INVALID",
            f"{field} must be a positive integer",
        )
    return value


def load_operations_config(path: str | Path) -> OperationsConfig:
    target = Path(path).expanduser().resolve()
    try:
        raw = yaml.safe_load(target.read_text(encoding="utf-8"))
    except (OSError, yaml.YAMLError) as error:
        raise ConfigurationError(
            "OPERATIONS_CONFIG_INVALID",
            "Could not read Phase 7 operations config",
        ) from error
    if not isinstance(raw, Mapping):
        raise ConfigurationError(
            "OPERATIONS_CONFIG_INVALID",
            "Operations config must contain an object",
        )
    expected = {
        "version",
        "approval_scope",
        "stale_after_minutes",
        "actual_watermark_delay_minutes",
        "forecast_retention_days",
        "maximum_rows_per_run",
        "allowed_purposes",
    }
    if set(raw) != expected:
        raise ConfigurationError(
            "OPERATIONS_CONFIG_INVALID",
            "Operations config has missing or unknown keys",
            {
                "missing": sorted(expected - set(raw)),
                "unknown": sorted(set(raw) - expected),
            },
        )
    purposes = raw["allowed_purposes"]
    if purposes != ["EVALUATION", "PUBLISHED"]:
        raise ConfigurationError(
            "OPERATIONS_CONFIG_INVALID",
            "Allowed purposes must be frozen as EVALUATION and PUBLISHED",
        )
    if raw["approval_scope"] != APPROVAL_SCOPE:
        raise ConfigurationError(
            "OPERATIONS_CONFIG_INVALID",
            "Only research-demonstration approval is permitted for Porto",
        )
    return OperationsConfig(
        path=target,
        config_hash=canonical_mapping_sha256(dict(raw)),
        version=str(raw["version"]),
        approval_scope=str(raw["approval_scope"]),
        stale_after_minutes=_positive(raw["stale_after_minutes"], "stale_after_minutes"),
        actual_watermark_delay_minutes=_positive(
            raw["actual_watermark_delay_minutes"],
            "actual_watermark_delay_minutes",
        ),
        forecast_retention_days=_positive(
            raw["forecast_retention_days"],
            "forecast_retention_days",
        ),
        maximum_rows_per_run=_positive(
            raw["maximum_rows_per_run"],
            "maximum_rows_per_run",
        ),
        allowed_purposes=tuple(str(value) for value in purposes),
    )
