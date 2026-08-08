from __future__ import annotations

import hashlib
import json
import math
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Any, Iterable
from uuid import UUID

from ..errors import ExtractionError
from ..quality import ExtractionStats
from .canonical import CanonicalDemandEvent, CanonicalSpool


GORIDE_EXTRACTION_SQL = """
SELECT
    t.id::TEXT,
    t.requested_at,
    ST_X(t.pickup_location),
    ST_Y(t.pickup_location),
    t.vehicle_type::TEXT,
    (
        SELECT MIN(sa.id)::TEXT
        FROM service_areas sa
        WHERE ST_Covers(sa.boundary, t.pickup_location)
    ) AS service_area_key
FROM trips t
WHERE t.deleted_at IS NULL
  AND t.requested_at >= %s
  AND t.requested_at < %s
ORDER BY t.requested_at, t.id
""".strip()

GORIDE_SUPPLY_COVERAGE_SQL = """
SELECT COUNT(DISTINCT bucket_start)
FROM driver_supply_snapshots
WHERE bucket_start >= %s
  AND bucket_start < %s
""".strip()


@dataclass(frozen=True)
class GoRideExtractionResult:
    stats: ExtractionStats
    query_hash: str
    query_plan: Any


def query_hash() -> str:
    normalized = " ".join(GORIDE_EXTRACTION_SQL.split())
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def _utc(value: Any) -> datetime | None:
    if not isinstance(value, datetime) or value.tzinfo is None:
        return None
    return value.astimezone(timezone.utc)


def normalize_goride_rows(
    rows: Iterable[tuple[Any, ...]],
    spool: CanonicalSpool,
    *,
    source_profile: str,
    dataset_version: str,
    from_utc: datetime,
    cutoff_utc: datetime,
    snapshot_id: UUID,
) -> ExtractionStats:
    stats = ExtractionStats()
    for row in rows:
        stats.rows_scanned += 1
        if len(row) != 6:
            stats.schema_breaches += 1
            continue
        trip_key, raw_time, raw_longitude, raw_latitude, vehicle_type, area_key = row
        event_time = _utc(raw_time)
        if event_time is None:
            stats.missing_event_time_breaches += 1
            continue
        if event_time < from_utc or event_time >= cutoff_utc:
            # The SQL contract should make this impossible. Count it as leakage,
            # never emit it, and fail the quality gate.
            stats.future_event_breaches += int(event_time >= cutoff_utc)
            stats.outside_interval_rows += 1
            continue
        stats.rows_in_interval += 1
        key = str(trip_key or "").strip()
        if not key:
            stats.schema_breaches += 1
            continue
        try:
            longitude = float(raw_longitude)
            latitude = float(raw_latitude)
        except (TypeError, ValueError):
            stats.invalid_pickup_breaches += 1
            continue
        if (
            not math.isfinite(longitude)
            or not math.isfinite(latitude)
            or not -180 <= longitude <= 180
            or not -90 <= latitude <= 90
        ):
            stats.invalid_pickup_breaches += 1
            continue
        event = CanonicalDemandEvent(
            source_profile=source_profile,
            dataset_version=dataset_version,
            source_trip_key=key,
            demand_event_time_utc=event_time,
            demand_event_semantics="REQUEST_CREATED",
            pickup_longitude=longitude,
            pickup_latitude=latitude,
            vehicle_type=str(vehicle_type) if vehicle_type is not None else None,
            service_area_key=str(area_key) if area_key is not None else None,
            source_cutoff_utc=cutoff_utc,
            extraction_run_id=snapshot_id,
        )
        if not spool.add(event):
            stats.duplicate_trip_breaches += 1
            continue
        stats.accepted_rows += 1
        stats.observe_event_time(event_time)
    return stats


class GoRidePostgresExtractor:
    def extract(
        self,
        connection: Any,
        spool: CanonicalSpool,
        *,
        source_profile: str,
        dataset_version: str,
        from_utc: datetime,
        cutoff_utc: datetime,
        snapshot_id: UUID,
        bucket_minutes: int,
    ) -> GoRideExtractionResult:
        try:
            with connection.cursor() as plan_cursor:
                plan_cursor.execute(
                    "EXPLAIN (FORMAT JSON, COSTS TRUE) " + GORIDE_EXTRACTION_SQL,
                    (from_utc, cutoff_utc),
                )
                query_plan = plan_cursor.fetchone()[0]
            with connection.cursor(name="goride_demand_extraction") as cursor:
                cursor.itersize = 2_000
                cursor.execute(GORIDE_EXTRACTION_SQL, (from_utc, cutoff_utc))
                stats = normalize_goride_rows(
                    cursor,
                    spool,
                    source_profile=source_profile,
                    dataset_version=dataset_version,
                    from_utc=from_utc,
                    cutoff_utc=cutoff_utc,
                    snapshot_id=snapshot_id,
                )
            with connection.cursor() as supply_cursor:
                supply_cursor.execute(
                    GORIDE_SUPPLY_COVERAGE_SQL,
                    (from_utc, cutoff_utc),
                )
                stats.observed_supply_buckets = int(supply_cursor.fetchone()[0])
            interval = cutoff_utc - from_utc
            stats.expected_supply_buckets = int(
                interval / timedelta(minutes=bucket_minutes)
            )
            connection.rollback()
            return GoRideExtractionResult(stats, query_hash(), query_plan)
        except Exception as error:
            connection.rollback()
            raise ExtractionError(
                "GORIDE_EXTRACTION_FAILED",
                "Bounded read-only extraction from the GoRide database failed",
                {"queryHash": query_hash()},
            ) from error


def sanitized_query_plan(value: Any) -> Any:
    """Round-trip the plan to plain JSON values before writing evidence."""
    return json.loads(json.dumps(value, default=str))
