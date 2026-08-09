from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from itertools import islice
from typing import Iterable
from zoneinfo import ZoneInfo

from ..config import ProcessingConfig, TimeRange
from ..errors import ExtractionError
from .builder import DemandCube


@dataclass(frozen=True)
class CellEligibilityPlan:
    strategy: str
    requested_coverage: float
    achieved_coverage: float
    boundary_train_events: int | None
    train_from_utc: datetime | None
    train_to_utc: datetime | None
    total_cells: int
    selected_cell_keys: tuple[tuple[int, int], ...]
    total_train_events: int
    selected_train_events: int

    @property
    def selected_cell_count(self) -> int:
        return len(self.selected_cell_keys)

    def to_dict(self) -> dict[str, object]:
        return {
            "achievedCoverage": self.achieved_coverage,
            "boundaryTrainEvents": self.boundary_train_events,
            "includeBoundaryTies": True,
            "requestedCoverage": self.requested_coverage,
            "selectedCellCount": self.selected_cell_count,
            "selectedTrainEvents": self.selected_train_events,
            "strategy": self.strategy,
            "totalCellCount": self.total_cells,
            "totalTrainEvents": self.total_train_events,
            "trainFromUtc": _utc_text(self.train_from_utc),
            "trainToUtc": _utc_text(self.train_to_utc),
        }


@dataclass(frozen=True)
class FeaturePartitionPlan:
    key: str
    target_from_utc: datetime
    target_to_utc: datetime
    projected_rows: int

    def to_dict(self) -> dict[str, object]:
        return {
            "key": self.key,
            "projectedRows": self.projected_rows,
            "targetFromUtc": _utc_text(self.target_from_utc),
            "targetToUtc": _utc_text(self.target_to_utc),
        }


@dataclass(frozen=True)
class FeatureBuildPlan:
    eligibility: CellEligibilityPlan
    partitions: tuple[FeaturePartitionPlan, ...]
    projected_rows: int


def _utc_text(value: datetime | None) -> str | None:
    if value is None:
        return None
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def _split_bounds_utc(
    split: TimeRange,
    *,
    timezone_name: str,
) -> tuple[datetime, datetime]:
    local = ZoneInfo(timezone_name)
    return (
        split.from_value.replace(tzinfo=local).astimezone(timezone.utc),
        split.to_value.replace(tzinfo=local).astimezone(timezone.utc),
    )


def _bucket_index(cube: DemandCube, value: datetime) -> int:
    bucket_seconds = cube.bucket_minutes * 60
    elapsed_seconds = int((value - cube.from_utc).total_seconds())
    if elapsed_seconds % bucket_seconds:
        raise ExtractionError(
            "FEATURE_ELIGIBILITY_SPLIT_MISALIGNED",
            "Cell-eligibility train boundaries must align to feature buckets",
        )
    return elapsed_seconds // bucket_seconds


def select_cells(
    cube: DemandCube,
    config: ProcessingConfig,
) -> CellEligibilityPlan:
    requested = config.features.training_demand_coverage
    ordered_all = tuple(sorted(cube.cells, key=lambda key: cube.cells[key].cell_id))
    if requested >= 1:
        return CellEligibilityPlan(
            strategy="ALL_OBSERVED",
            requested_coverage=1.0,
            achieved_coverage=1.0,
            boundary_train_events=None,
            train_from_utc=None,
            train_to_utc=None,
            total_cells=len(ordered_all),
            selected_cell_keys=ordered_all,
            total_train_events=0,
            selected_train_events=0,
        )

    train = config.evaluation.train
    if train is None:
        raise ExtractionError(
            "FEATURE_ELIGIBILITY_TRAIN_SPLIT_MISSING",
            "Training-demand cell selection requires a train split",
        )
    configured_from, configured_to = _split_bounds_utc(
        train,
        timezone_name=config.temporal.source_timezone,
    )
    train_from = max(configured_from, cube.from_utc)
    train_to = min(configured_to, cube.cutoff_utc)
    if train_from >= train_to:
        raise ExtractionError(
            "FEATURE_ELIGIBILITY_TRAIN_INTERVAL_EMPTY",
            "Train split does not overlap the extraction snapshot",
        )
    start_index = _bucket_index(cube, train_from)
    end_index = _bucket_index(cube, train_to)
    ranked = sorted(
        (
            (
                key,
                int(sum(islice(cube.series[key], start_index, end_index))),
            )
            for key in cube.cells
        ),
        key=lambda item: (-item[1], cube.cells[item[0]].cell_id),
    )
    total_events = sum(count for _key, count in ranked)
    if total_events <= 0:
        raise ExtractionError(
            "FEATURE_ELIGIBILITY_TRAIN_POPULATION_EMPTY",
            "No in-study-area demand exists inside the train split",
        )
    cumulative = 0
    boundary = None
    for _key, count in ranked:
        cumulative += count
        boundary = count
        if cumulative / total_events >= requested:
            break
    assert boundary is not None
    selected = tuple(
        sorted(
            (key for key, count in ranked if count >= boundary),
            key=lambda key: cube.cells[key].cell_id,
        )
    )
    selected_set = set(selected)
    selected_events = sum(count for key, count in ranked if key in selected_set)
    return CellEligibilityPlan(
        strategy="CUMULATIVE_TRAIN_DEMAND",
        requested_coverage=requested,
        achieved_coverage=selected_events / total_events,
        boundary_train_events=boundary,
        train_from_utc=train_from,
        train_to_utc=train_to,
        total_cells=len(ordered_all),
        selected_cell_keys=selected,
        total_train_events=total_events,
        selected_train_events=selected_events,
    )


def _next_month(value: datetime) -> datetime:
    if value.month == 12:
        return value.replace(year=value.year + 1, month=1, day=1)
    return value.replace(month=value.month + 1, day=1)


def _month_ranges(
    from_utc: datetime,
    to_utc: datetime,
) -> Iterable[tuple[datetime, datetime]]:
    cursor = from_utc.astimezone(timezone.utc).replace(
        day=1,
        hour=0,
        minute=0,
        second=0,
        microsecond=0,
    )
    while cursor < to_utc:
        next_cursor = _next_month(cursor)
        yield max(cursor, from_utc), min(next_cursor, to_utc)
        cursor = next_cursor


def _partition_row_count(
    cube: DemandCube,
    config: ProcessingConfig,
    *,
    selected_cells: int,
    target_from_utc: datetime,
    target_to_utc: datetime,
) -> int:
    target_from_index = _bucket_index(cube, target_from_utc)
    target_to_index = _bucket_index(cube, target_to_utc)
    rows_per_cell = 0
    for horizon_minutes in config.temporal.forecast_horizons_minutes:
        horizon = horizon_minutes // config.temporal.bucket_minutes
        first_target = max(target_from_index, horizon + 1)
        last_target = min(target_to_index, cube.bucket_count)
        rows_per_cell += max(last_target - first_target, 0)
    return selected_cells * rows_per_cell


def build_feature_plan(
    cube: DemandCube,
    config: ProcessingConfig,
) -> FeatureBuildPlan:
    eligibility = select_cells(cube, config)
    partitions = []
    for target_from, target_to in _month_ranges(cube.from_utc, cube.cutoff_utc):
        rows = _partition_row_count(
            cube,
            config,
            selected_cells=eligibility.selected_cell_count,
            target_from_utc=target_from,
            target_to_utc=target_to,
        )
        if not rows:
            continue
        if rows > config.artifacts.maximum_rows_per_partition:
            raise ExtractionError(
                "FEATURE_PARTITION_ROW_LIMIT_EXCEEDED",
                "Projected feature partition exceeds the configured cost guard",
                {
                    "partition": target_from.strftime("%Y-%m"),
                    "projectedRows": rows,
                    "maximumRows": config.artifacts.maximum_rows_per_partition,
                },
            )
        partitions.append(
            FeaturePartitionPlan(
                key=target_from.strftime("%Y-%m"),
                target_from_utc=target_from,
                target_to_utc=target_to,
                projected_rows=rows,
            )
        )
    projected = sum(item.projected_rows for item in partitions)
    if projected > config.artifacts.maximum_rows_per_run:
        raise ExtractionError(
            "FEATURE_RUN_ROW_LIMIT_EXCEEDED",
            "Projected feature run exceeds the configured cost guard",
            {
                "projectedRows": projected,
                "maximumRows": config.artifacts.maximum_rows_per_run,
            },
        )
    if not partitions or projected <= 0:
        raise ExtractionError(
            "FEATURE_PLAN_EMPTY",
            "No feature rows are eligible for the configured snapshot",
        )
    return FeatureBuildPlan(eligibility, tuple(partitions), projected)
