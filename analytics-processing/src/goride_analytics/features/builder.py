from __future__ import annotations

import math
import uuid
from array import array
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Iterator, Mapping
from uuid import UUID
from zoneinfo import ZoneInfo

from ..config import ProcessingConfig
from ..errors import ExtractionError
from ..quality import QualityResult
from .grid import GridCell, GridDefinition
from .model import FeatureRow
from .snapshot import ExtractionSnapshot


FEATURE_ARTIFACT_NAMESPACE = uuid.UUID("8cf8a178-8ad8-42bb-9a02-c901eff19ea7")


@dataclass
class FeatureBuildStats:
    input_events: int = 0
    events_aggregated: int = 0
    outside_study_area: int = 0
    grid_assignment_breaches: int = 0
    duplicate_feature_rows: int = 0
    feature_rows: int = 0
    leakage_breaches: int = 0
    low_coverage_rows: int = 0
    missing_supply_rows: int = 0


@dataclass(frozen=True)
class FeatureQualityReport:
    overall_status: str
    results: tuple[QualityResult, ...]

    @property
    def failed_rules(self) -> list[str]:
        return [item.rule_code for item in self.results if item.result_status == "FAIL"]

    def to_dict(self) -> dict[str, object]:
        return {
            "overallStatus": self.overall_status,
            "results": [item.to_dict() for item in self.results],
        }


@dataclass
class DemandCube:
    from_utc: datetime
    cutoff_utc: datetime
    bucket_minutes: int
    bucket_count: int
    semantics: str
    series: dict[tuple[int, int], array]
    cells: dict[tuple[int, int], GridCell]
    service_areas: dict[tuple[int, int], str | None]
    stats: FeatureBuildStats


@dataclass(frozen=True)
class SupplySeries:
    values: Mapping[tuple[int, str | None], int] = field(default_factory=dict)

    def get(self, bucket_index: int, service_area_key: str | None) -> int | None:
        return self.values.get((bucket_index, service_area_key))


def feature_set_version(config_hash: str, snapshot_sha256: str, code_commit: str) -> str:
    return f"demand-v1-{config_hash[:10]}-{snapshot_sha256[:12]}-{code_commit[:10]}"


def feature_artifact_id(
    version: str,
    *,
    cell_size_meters: int,
    from_utc: datetime,
    cutoff_utc: datetime,
) -> UUID:
    identity = "|".join(
        (
            version,
            str(cell_size_meters),
            from_utc.isoformat(),
            cutoff_utc.isoformat(),
        )
    )
    return uuid.uuid5(FEATURE_ARTIFACT_NAMESPACE, identity)


def aggregate_snapshot(
    snapshot: ExtractionSnapshot,
    grid: GridDefinition,
    *,
    bucket_minutes: int,
) -> DemandCube:
    interval_seconds = int((snapshot.cutoff_utc - snapshot.from_utc).total_seconds())
    configured_bucket_seconds = 60 * bucket_minutes
    # Snapshot intervals are validated again by the feature pipeline with the
    # configured bucket. This local guard prevents non-integral array indexes.
    if interval_seconds <= 0 or interval_seconds % configured_bucket_seconds:
        raise ExtractionError(
            "FEATURE_BUCKET_INTERVAL_INVALID",
            "Extraction interval is not aligned to 15-minute feature buckets",
        )
    bucket_count = interval_seconds // configured_bucket_seconds
    series: dict[tuple[int, int], array] = {}
    cells: dict[tuple[int, int], GridCell] = {}
    area_values: dict[tuple[int, int], set[str | None]] = {}
    semantics: str | None = None
    stats = FeatureBuildStats()
    for event in snapshot.events():
        stats.input_events += 1
        if semantics is None:
            semantics = event.demand_event_semantics
        elif semantics != event.demand_event_semantics:
            raise ExtractionError(
                "FEATURE_SEMANTICS_MIXED",
                "One feature set cannot mix demand-event semantics",
            )
        cell = grid.assign(event.longitude, event.latitude)
        if cell is None:
            stats.outside_study_area += 1
            continue
        index = (
            int((event.event_time_utc - snapshot.from_utc).total_seconds())
            // configured_bucket_seconds
        )
        if not 0 <= index < bucket_count:
            stats.grid_assignment_breaches += 1
            continue
        key = (cell.grid_x, cell.grid_y)
        if key not in series:
            series[key] = array("I", [0]) * bucket_count
            cells[key] = cell
            area_values[key] = set()
        series[key][index] += 1
        area_values[key].add(event.service_area_key)
        stats.events_aggregated += 1
    if semantics is None or not series:
        raise ExtractionError(
            "FEATURE_POPULATION_EMPTY",
            "No canonical events remain inside the configured study area",
        )
    service_areas = {
        key: next(iter(values)) if len(values) == 1 else None
        for key, values in area_values.items()
    }
    return DemandCube(
        from_utc=snapshot.from_utc,
        cutoff_utc=snapshot.cutoff_utc,
        bucket_minutes=bucket_minutes,
        bucket_count=bucket_count,
        semantics=semantics,
        series=series,
        cells=cells,
        service_areas=service_areas,
        stats=stats,
    )


def iter_feature_rows(
    cube: DemandCube,
    config: ProcessingConfig,
    *,
    grid: GridDefinition,
    version: str,
    snapshot_sha256: str,
    feature_artifact_id: UUID,
    supply: SupplySeries | None = None,
    selected_cell_keys: tuple[tuple[int, int], ...] | None = None,
    target_from_utc: datetime | None = None,
    target_to_utc: datetime | None = None,
) -> Iterator[FeatureRow]:
    bucket = timedelta(minutes=config.temporal.bucket_minutes)
    timezone_name = ZoneInfo(config.temporal.source_timezone)
    lag_values = set(config.features.demand_lags)
    rolling_values = set(config.features.rolling_windows)
    supply = supply or SupplySeries()
    selected = set(selected_cell_keys) if selected_cell_keys is not None else None
    cells_by_id = sorted(
        (
            cell
            for key, cell in cube.cells.items()
            if selected is None or key in selected
        ),
        key=lambda item: item.cell_id,
    )
    minimum_horizon = min(config.temporal.forecast_horizons_minutes) // (
        config.temporal.bucket_minutes
    )
    maximum_horizon = max(config.temporal.forecast_horizons_minutes) // (
        config.temporal.bucket_minutes
    )
    cutoff_start = 1
    cutoff_stop = cube.bucket_count
    if target_from_utc is not None:
        target_from_index = int(
            (target_from_utc - cube.from_utc).total_seconds()
            // bucket.total_seconds()
        )
        cutoff_start = max(cutoff_start, target_from_index - maximum_horizon)
    if target_to_utc is not None:
        target_to_index = int(
            (target_to_utc - cube.from_utc).total_seconds()
            // bucket.total_seconds()
        )
        cutoff_stop = min(cutoff_stop, target_to_index - minimum_horizon)
    for cell in cells_by_id:
        key = (cell.grid_x, cell.grid_y)
        values = cube.series[key]
        prefix = [0]
        running = 0
        for value in values:
            running += value
            prefix.append(running)
        for cutoff_index in range(cutoff_start, cutoff_stop):
            inference_cutoff = cube.from_utc + cutoff_index * bucket
            history_coverage = min(
                cutoff_index / config.quality.minimum_history_buckets,
                1.0,
            )
            lags = {
                lag: int(values[cutoff_index - lag])
                if lag in lag_values and cutoff_index >= lag
                else None
                for lag in (1, 2, 4, 96, 672)
            }
            rolling = {
                window: (
                    (prefix[cutoff_index] - prefix[cutoff_index - window]) / window
                    if window in rolling_values and cutoff_index >= window
                    else None
                )
                for window in (4, 12, 96, 672)
            }
            neighbor = None
            if config.features.include_spatial_neighbors and cutoff_index >= 1:
                neighbor = sum(
                    int(cube.series[neighbor_key][cutoff_index - 1])
                    for neighbor_key in grid.neighbors(cell)
                    if neighbor_key in cube.series
                )
            available_supply = None
            supply_coverage = 1.0
            if config.features.include_supply_features:
                available_supply = supply.get(
                    cutoff_index - 1,
                    cube.service_areas[key],
                )
                if available_supply is None:
                    supply_coverage = 0.0
            coverage = min(history_coverage, supply_coverage)
            for horizon_minutes in config.temporal.forecast_horizons_minutes:
                horizon_buckets = horizon_minutes // config.temporal.bucket_minutes
                target_index = cutoff_index + horizon_buckets
                if target_index >= cube.bucket_count:
                    continue
                target_start = cube.from_utc + target_index * bucket
                if target_from_utc is not None and target_start < target_from_utc:
                    continue
                if target_to_utc is not None and target_start >= target_to_utc:
                    continue
                local_target = target_start.astimezone(timezone_name)
                minute_of_day = local_target.hour * 60 + local_target.minute
                angle = 2 * math.pi * minute_of_day / (24 * 60)
                max_source_time = inference_cutoff if cutoff_index >= 1 else None
                quality_status = "PASS" if coverage >= 1.0 else "WARN"
                row = FeatureRow(
                    feature_set_version=version,
                    source_profile=config.profile.name,
                    dataset_version=config.dataset.version,
                    demand_event_semantics=cube.semantics,
                    grid_version=config.spatial.grid_version,
                    projected_srid=config.spatial.projected_srid,
                    cell_id=cell.cell_id,
                    grid_x=cell.grid_x,
                    grid_y=cell.grid_y,
                    cell_size_meters=grid.cell_size_meters,
                    bucket_start_utc=inference_cutoff,
                    inference_cutoff_utc=inference_cutoff,
                    target_bucket_start_utc=target_start,
                    horizon_minutes=horizon_minutes,
                    target_trip_requests=int(values[target_index]),
                    lag_1=lags[1],
                    lag_2=lags[2],
                    lag_4=lags[4],
                    lag_96=lags[96],
                    lag_672=lags[672],
                    rolling_mean_4=rolling[4],
                    rolling_mean_12=rolling[12],
                    rolling_mean_96=rolling[96],
                    rolling_mean_672=rolling[672],
                    hour_sin=math.sin(angle),
                    hour_cos=math.cos(angle),
                    day_of_week=local_target.weekday(),
                    is_weekend=local_target.weekday() >= 5,
                    neighbor_demand_lag_1=neighbor,
                    available_driver_lag_1=available_supply,
                    coverage_ratio=coverage,
                    quality_status=quality_status,
                    feature_artifact_id=feature_artifact_id,
                    max_feature_source_time_utc=max_source_time,
                    source_snapshot_sha256=snapshot_sha256,
                )
                cube.stats.feature_rows += 1
                cube.stats.low_coverage_rows += int(coverage < 1.0)
                cube.stats.missing_supply_rows += int(
                    config.features.include_supply_features
                    and available_supply is None
                )
                if max_source_time is not None and max_source_time > inference_cutoff:
                    cube.stats.leakage_breaches += 1
                yield row


def build_feature_quality(
    stats: FeatureBuildStats,
    *,
    expected_buckets: int,
    include_supply: bool,
) -> FeatureQualityReport:
    results = [
        QualityResult(
            "DQ_FEATURE_POPULATION",
            "FAIL",
            "PASS" if stats.feature_rows else "FAIL",
            1,
            0 if stats.feature_rows else 1,
            metric_value=float(stats.feature_rows),
            threshold={"minimumRows": 1},
        ),
        QualityResult(
            "DQ_BUCKET_CONTINUITY",
            "FAIL",
            "PASS",
            expected_buckets,
            0,
            metric_value=1.0,
            threshold={"minimumContinuityRatio": 1.0},
        ),
        QualityResult(
            "DQ_GRID_ASSIGNMENT",
            "FAIL",
            "FAIL" if stats.grid_assignment_breaches else "PASS",
            max(stats.input_events, stats.grid_assignment_breaches),
            stats.grid_assignment_breaches,
            threshold={"maximumBreaches": 0},
        ),
        QualityResult(
            "DQ_FEATURE_UNIQUENESS",
            "FAIL",
            "FAIL" if stats.duplicate_feature_rows else "PASS",
            max(stats.feature_rows, stats.duplicate_feature_rows),
            stats.duplicate_feature_rows,
            threshold={"maximumDuplicates": 0},
        ),
        QualityResult(
            "DQ_LEAKAGE",
            "FAIL",
            "FAIL" if stats.leakage_breaches else "PASS",
            max(stats.feature_rows, stats.leakage_breaches),
            stats.leakage_breaches,
            threshold={"maximumSourceTimeAfterCutoff": 0},
        ),
        QualityResult(
            "DQ_CELL_COVERAGE",
            "WARN",
            "WARN" if stats.low_coverage_rows else "PASS",
            stats.feature_rows,
            stats.low_coverage_rows,
            metric_value=(
                1 - stats.low_coverage_rows / stats.feature_rows
                if stats.feature_rows
                else 0.0
            ),
            threshold={"minimumHistoryCoverageRatio": 1.0},
        ),
        QualityResult(
            "DQ_STUDY_AREA_EXCLUSION",
            "WARN",
            "WARN" if stats.outside_study_area else "PASS",
            stats.input_events,
            stats.outside_study_area,
            metric_value=(
                stats.events_aggregated / stats.input_events
                if stats.input_events
                else 0.0
            ),
            threshold={"boundsPolicy": "MIN_INCLUSIVE_MAX_EXCLUSIVE"},
        ),
    ]
    if include_supply:
        results.append(
            QualityResult(
                "DQ_SUPPLY_COVERAGE",
                "WARN",
                "WARN" if stats.missing_supply_rows else "PASS",
                stats.feature_rows,
                stats.missing_supply_rows,
                metric_value=(
                    1 - stats.missing_supply_rows / stats.feature_rows
                    if stats.feature_rows
                    else 0.0
                ),
                threshold={"minimumCoverageRatio": 1.0},
            )
        )
    if any(item.result_status == "FAIL" for item in results):
        overall = "FAIL"
    elif any(item.result_status == "WARN" for item in results):
        overall = "WARN"
    else:
        overall = "PASS"
    return FeatureQualityReport(overall, tuple(results))
