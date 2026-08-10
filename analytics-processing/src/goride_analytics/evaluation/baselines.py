from __future__ import annotations

from array import array
from bisect import bisect_right
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Iterable
from zoneinfo import ZoneInfo


MISSING_DEMAND = -1


class DemandCube:
    def __init__(
        self,
        *,
        cells: tuple[str, ...],
        from_utc: datetime,
        to_utc: datetime,
        bucket_minutes: int,
        source_timezone: str,
    ) -> None:
        self.cells = cells
        self.cell_indexes = {cell: index for index, cell in enumerate(cells)}
        self.from_utc = from_utc.astimezone(timezone.utc)
        self.to_utc = to_utc.astimezone(timezone.utc)
        self.bucket_minutes = bucket_minutes
        self.bucket_seconds = bucket_minutes * 60
        seconds = int((self.to_utc - self.from_utc).total_seconds())
        if seconds <= 0 or seconds % self.bucket_seconds:
            raise ValueError("demand cube interval must be positive and bucket aligned")
        self.bucket_count = seconds // self.bucket_seconds
        self.values = array("q", [MISSING_DEMAND]) * (len(cells) * self.bucket_count)
        zone = ZoneInfo(source_timezone)
        self.weekly_slots: list[int] = []
        self.local_hours: list[int] = []
        self.previous_seasonal_indexes = array("i", [-1]) * self.bucket_count
        prior_by_slot: dict[int, list[int]] = {}
        self.minimum_seasonal_gap = 6 * 24 * 60 // bucket_minutes
        for bucket in range(self.bucket_count):
            local = (self.from_utc + timedelta(seconds=bucket * self.bucket_seconds)).astimezone(zone)
            weekly_slot = (
                local.weekday() * (24 * 60 // bucket_minutes)
                + (local.hour * 60 + local.minute) // bucket_minutes
            )
            self.weekly_slots.append(weekly_slot)
            self.local_hours.append(local.hour)
            previous = prior_by_slot.setdefault(weekly_slot, [])
            for candidate in reversed(previous):
                if bucket - candidate >= self.minimum_seasonal_gap:
                    self.previous_seasonal_indexes[bucket] = candidate
                    break
            previous.append(bucket)
        self.weekly_occurrences = prior_by_slot

    def bucket_index(self, target_utc: datetime) -> int:
        seconds = int((target_utc.astimezone(timezone.utc) - self.from_utc).total_seconds())
        if seconds % self.bucket_seconds:
            raise ValueError("target timestamp is not bucket aligned")
        return seconds // self.bucket_seconds

    def _position(self, cell_index: int, bucket_index: int) -> int:
        return cell_index * self.bucket_count + bucket_index

    def set(self, cell_id: str, target_utc: datetime, demand: int) -> None:
        cell_index = self.cell_indexes[cell_id]
        bucket_index = self.bucket_index(target_utc)
        if not 0 <= bucket_index < self.bucket_count:
            raise ValueError("target timestamp is outside demand cube")
        self.values[self._position(cell_index, bucket_index)] = int(demand)

    def get(self, cell_index: int, bucket_index: int) -> int | None:
        if not 0 <= bucket_index < self.bucket_count:
            return None
        value = self.values[self._position(cell_index, bucket_index)]
        return None if value == MISSING_DEMAND else int(value)

    def iter_interval(self, from_utc: datetime, to_utc: datetime) -> Iterable[int]:
        first = max(0, self.bucket_index(from_utc))
        last = min(self.bucket_count, self.bucket_index(to_utc))
        for cell_index in range(len(self.cells)):
            offset = cell_index * self.bucket_count
            for bucket_index in range(first, last):
                value = self.values[offset + bucket_index]
                if value != MISSING_DEMAND:
                    yield int(value)

    def previous_seasonal_value(
        self,
        cell_index: int,
        target_bucket_index: int,
    ) -> int | None:
        slot = self.weekly_slots[target_bucket_index]
        candidates = self.weekly_occurrences[slot]
        latest_allowed = target_bucket_index - self.minimum_seasonal_gap
        position = bisect_right(candidates, latest_allowed) - 1
        while position >= 0:
            value = self.get(cell_index, candidates[position])
            if value is not None:
                return value
            position -= 1
        return None


@dataclass(frozen=True)
class HistoricalMeanProfile:
    slot_sums: array
    slot_counts: array
    cell_sums: array
    cell_counts: array
    global_sum: int
    global_count: int
    weekly_slot_count: int

    def predict(self, cell_index: int, weekly_slot: int) -> tuple[float, str]:
        position = cell_index * self.weekly_slot_count + weekly_slot
        if self.slot_counts[position]:
            return (
                self.slot_sums[position] / self.slot_counts[position],
                "CELL_WEEKLY_SLOT",
            )
        if self.cell_counts[cell_index]:
            return (
                self.cell_sums[cell_index] / self.cell_counts[cell_index],
                "CELL_MEAN",
            )
        if self.global_count:
            return self.global_sum / self.global_count, "GLOBAL_MEAN"
        return 0.0, "ZERO"


def fit_historical_mean(
    cube: DemandCube,
    *,
    train_from_utc: datetime,
    train_to_utc: datetime,
) -> HistoricalMeanProfile:
    first = max(0, cube.bucket_index(train_from_utc))
    last = min(cube.bucket_count, cube.bucket_index(train_to_utc))
    weekly_slot_count = 7 * 24 * 60 // cube.bucket_minutes
    slot_sums = array("q", [0]) * (len(cube.cells) * weekly_slot_count)
    slot_counts = array("I", [0]) * (len(cube.cells) * weekly_slot_count)
    cell_sums = array("q", [0]) * len(cube.cells)
    cell_counts = array("I", [0]) * len(cube.cells)
    global_sum = 0
    global_count = 0
    for cell_index in range(len(cube.cells)):
        for bucket_index in range(first, last):
            value = cube.get(cell_index, bucket_index)
            if value is None:
                continue
            slot_position = cell_index * weekly_slot_count + cube.weekly_slots[bucket_index]
            slot_sums[slot_position] += value
            slot_counts[slot_position] += 1
            cell_sums[cell_index] += value
            cell_counts[cell_index] += 1
            global_sum += value
            global_count += 1
    return HistoricalMeanProfile(
        slot_sums=slot_sums,
        slot_counts=slot_counts,
        cell_sums=cell_sums,
        cell_counts=cell_counts,
        global_sum=global_sum,
        global_count=global_count,
        weekly_slot_count=weekly_slot_count,
    )


def seasonal_naive_prediction(
    cube: DemandCube,
    profile: HistoricalMeanProfile,
    *,
    cell_index: int,
    target_bucket_index: int,
) -> tuple[float, str]:
    seasonal_value = cube.previous_seasonal_value(cell_index, target_bucket_index)
    if seasonal_value is not None:
        return float(seasonal_value), "WEEK_LAG_LOCAL"
    prediction, fallback = profile.predict(
        cell_index,
        cube.weekly_slots[target_bucket_index],
    )
    return prediction, f"HISTORICAL_MEAN_{fallback}"
