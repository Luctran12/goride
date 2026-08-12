from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Iterable


@dataclass
class MetricAccumulator:
    sample_count: int = 0
    absolute_error_sum: float = 0.0
    squared_error_sum: float = 0.0
    actual_sum: float = 0.0

    def update(self, actual: int | float, prediction: int | float) -> None:
        error = float(prediction) - float(actual)
        self.sample_count += 1
        self.absolute_error_sum += abs(error)
        self.squared_error_sum += error * error
        self.actual_sum += float(actual)

    def result(self) -> dict[str, float | int | None]:
        if self.sample_count == 0:
            raise ValueError("metrics require at least one observation")
        return {
            "sampleCount": self.sample_count,
            "absoluteErrorSum": self.absolute_error_sum,
            "squaredErrorSum": self.squared_error_sum,
            "actualSum": self.actual_sum,
            "mae": self.absolute_error_sum / self.sample_count,
            "rmse": math.sqrt(self.squared_error_sum / self.sample_count),
            "wape": (
                None
                if self.actual_sum == 0
                else self.absolute_error_sum / self.actual_sum
            ),
        }


def demand_quantile_thresholds(values: Iterable[int]) -> tuple[int, int, int]:
    counts: dict[int, int] = {}
    total = 0
    for raw in values:
        value = int(raw)
        counts[value] = counts.get(value, 0) + 1
        total += 1
    if total == 0:
        raise ValueError("demand quantiles require training observations")
    ranks = tuple(max(1, math.ceil(total * quantile)) for quantile in (0.25, 0.5, 0.75))
    result: list[int] = []
    cumulative = 0
    rank_index = 0
    for value in sorted(counts):
        cumulative += counts[value]
        while rank_index < len(ranks) and cumulative >= ranks[rank_index]:
            result.append(value)
            rank_index += 1
    return tuple(result)  # type: ignore[return-value]


def demand_quantile_slice(actual: int, thresholds: tuple[int, int, int]) -> str:
    q25, q50, q75 = thresholds
    if actual <= q25:
        return "Q1_LOW"
    if actual <= q50:
        return "Q2"
    if actual <= q75:
        return "Q3"
    return "Q4_HIGH"


def time_of_day_slice(local_hour: int) -> str:
    if 0 <= local_hour <= 5:
        return "NIGHT"
    if local_hour <= 9:
        return "AM_PEAK"
    if local_hour <= 15:
        return "DAY"
    if local_hour <= 19:
        return "PM_PEAK"
    return "EVENING"
