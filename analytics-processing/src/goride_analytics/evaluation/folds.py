from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from zoneinfo import ZoneInfo

from ..config import ProcessingConfig, TimeRange
from ..errors import ConfigurationError


@dataclass(frozen=True)
class EvaluationFold:
    key: str
    split_role: str
    train_from_utc: datetime
    train_to_utc: datetime
    evaluate_from_utc: datetime
    evaluate_to_utc: datetime

    def to_dict(self) -> dict[str, str]:
        return {
            "key": self.key,
            "splitRole": self.split_role,
            "trainFromUtc": _utc_text(self.train_from_utc),
            "trainToUtc": _utc_text(self.train_to_utc),
            "evaluateFromUtc": _utc_text(self.evaluate_from_utc),
            "evaluateToUtc": _utc_text(self.evaluate_to_utc),
        }


def _utc_text(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def _local_boundary(value: datetime, source_timezone: str) -> datetime:
    zone = ZoneInfo(source_timezone)
    if value.tzinfo is None:
        value = value.replace(tzinfo=zone)
    else:
        value = value.astimezone(zone)
    return value.astimezone(timezone.utc)


def _shift_month(value: datetime, months: int) -> datetime:
    month_index = value.year * 12 + value.month - 1 + months
    year, zero_month = divmod(month_index, 12)
    try:
        return value.replace(year=year, month=zero_month + 1)
    except ValueError as error:
        raise ConfigurationError(
            "CONFIG_EVALUATION_BOUNDARY_INVALID",
            "Rolling-origin boundaries must be aligned to a valid calendar day",
        ) from error


def _required_split(value: TimeRange | None, name: str) -> TimeRange:
    if value is None:
        raise ConfigurationError(
            "CONFIG_EVALUATION_SPLIT_REQUIRED",
            f"evaluation.{name} is required for baseline evaluation",
        )
    return value


def build_rolling_origin_folds(config: ProcessingConfig) -> tuple[EvaluationFold, ...]:
    """Build the frozen D1-D4 development folds and final holdout.

    Four one-month development origins immediately precede the test boundary.
    The training window expands from the immutable train start. The final holdout
    is never included in a development fold.
    """

    train = _required_split(config.evaluation.train, "train")
    test = _required_split(config.evaluation.test, "test")
    if any(
        value.day != 1
        or value.hour != 0
        or value.minute != 0
        or value.second != 0
        or value.microsecond != 0
        for value in (train.from_value, test.from_value, test.to_value)
    ):
        raise ConfigurationError(
            "CONFIG_EVALUATION_BOUNDARY_INVALID",
            "Baseline evaluation boundaries must be local calendar-month boundaries",
        )
    development_start = _shift_month(test.from_value, -4)
    if development_start <= train.from_value:
        raise ConfigurationError(
            "CONFIG_EVALUATION_HISTORY_INSUFFICIENT",
            "Four development folds require training history before their first origin",
        )
    zone = config.temporal.source_timezone
    train_from_utc = _local_boundary(train.from_value, zone)
    folds: list[EvaluationFold] = []
    for index in range(4):
        evaluate_from = _shift_month(development_start, index)
        evaluate_to = _shift_month(development_start, index + 1)
        folds.append(
            EvaluationFold(
                key=f"D{index + 1}",
                split_role="DEVELOPMENT",
                train_from_utc=train_from_utc,
                train_to_utc=_local_boundary(evaluate_from, zone),
                evaluate_from_utc=_local_boundary(evaluate_from, zone),
                evaluate_to_utc=_local_boundary(evaluate_to, zone),
            )
        )
    folds.append(
        EvaluationFold(
            key="FINAL",
            split_role="TEST",
            train_from_utc=train_from_utc,
            train_to_utc=_local_boundary(test.from_value, zone),
            evaluate_from_utc=_local_boundary(test.from_value, zone),
            evaluate_to_utc=_local_boundary(test.to_value, zone),
        )
    )
    return tuple(folds)
