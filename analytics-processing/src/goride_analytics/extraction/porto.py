from __future__ import annotations

import csv
import io
import json
import math
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from uuid import UUID

from ..errors import ExtractionError
from ..quality import ExtractionStats
from .canonical import CanonicalDemandEvent, CanonicalSpool


PORTO_HEADER = (
    "TRIP_ID",
    "CALL_TYPE",
    "ORIGIN_CALL",
    "ORIGIN_STAND",
    "TAXI_ID",
    "TIMESTAMP",
    "DAY_TYPE",
    "MISSING_DATA",
    "POLYLINE",
)


def _event_time(raw: str | None) -> datetime | None:
    if raw is None or not raw.strip():
        return None
    try:
        timestamp = int(raw)
        return datetime.fromtimestamp(timestamp, tz=timezone.utc)
    except (OverflowError, OSError, TypeError, ValueError):
        return None


def _missing_data(raw: str | None) -> bool | None:
    if raw is None:
        return None
    normalized = raw.strip().lower()
    if normalized == "true":
        return True
    if normalized == "false":
        return False
    return None


def _pickup(raw: str | None) -> tuple[float, float] | None:
    if raw is None or not raw.strip():
        return None
    try:
        polyline: Any = json.loads(raw)
    except json.JSONDecodeError:
        return None
    if not isinstance(polyline, list) or not polyline:
        return None
    first = polyline[0]
    if not isinstance(first, list) or len(first) != 2:
        return None
    longitude, latitude = first
    if (
        isinstance(longitude, bool)
        or isinstance(latitude, bool)
        or not isinstance(longitude, (int, float))
        or not isinstance(latitude, (int, float))
    ):
        return None
    longitude = float(longitude)
    latitude = float(latitude)
    if (
        not math.isfinite(longitude)
        or not math.isfinite(latitude)
        or not -180 <= longitude <= 180
        or not -90 <= latitude <= 90
    ):
        return None
    return longitude, latitude


class PortoArchiveExtractor:
    def extract(
        self,
        source_zip: Path,
        spool: CanonicalSpool,
        *,
        source_profile: str,
        dataset_version: str,
        from_utc: datetime,
        cutoff_utc: datetime,
        snapshot_id: UUID,
    ) -> ExtractionStats:
        stats = ExtractionStats()
        try:
            archive = zipfile.ZipFile(source_zip)
        except (OSError, zipfile.BadZipFile) as error:
            raise ExtractionError(
                "PORTO_ARCHIVE_INVALID",
                "Porto source is not a readable ZIP archive",
                {"sourceFile": source_zip.name},
            ) from error

        with archive:
            members = sorted(
                [
                    member
                    for member in archive.infolist()
                    if not member.is_dir()
                    and member.filename.lower().endswith(".csv")
                ],
                key=lambda member: member.filename,
            )
            if len(members) != 1:
                raise ExtractionError(
                    "PORTO_ARCHIVE_LAYOUT_INVALID",
                    "Porto archive must contain exactly one CSV member",
                    {"csvMemberCount": len(members)},
                )
            try:
                raw_stream = archive.open(members[0], "r")
                text_stream = io.TextIOWrapper(raw_stream, encoding="utf-8-sig", newline="")
                with raw_stream, text_stream:
                    reader = csv.DictReader(text_stream)
                    if tuple(reader.fieldnames or ()) != PORTO_HEADER:
                        stats.schema_breaches = 1
                        stats.details["actualHeader"] = list(reader.fieldnames or ())
                        stats.details["expectedHeader"] = list(PORTO_HEADER)
                        return stats
                    for row in reader:
                        self._consume_row(
                            row,
                            stats,
                            spool,
                            source_profile=source_profile,
                            dataset_version=dataset_version,
                            from_utc=from_utc,
                            cutoff_utc=cutoff_utc,
                            snapshot_id=snapshot_id,
                        )
            except UnicodeDecodeError as error:
                raise ExtractionError(
                    "PORTO_CSV_ENCODING_INVALID",
                    "Porto CSV member is not valid UTF-8",
                    {"csvMember": members[0].filename},
                ) from error
        return stats

    @staticmethod
    def _consume_row(
        row: dict[str | None, str | list[str] | None],
        stats: ExtractionStats,
        spool: CanonicalSpool,
        *,
        source_profile: str,
        dataset_version: str,
        from_utc: datetime,
        cutoff_utc: datetime,
        snapshot_id: UUID,
    ) -> None:
        stats.rows_scanned += 1
        if None in row:
            stats.schema_breaches += 1
            return

        event_time = _event_time(str(row.get("TIMESTAMP") or ""))
        if event_time is None:
            stats.missing_event_time_breaches += 1
            return
        if event_time < from_utc or event_time >= cutoff_utc:
            stats.outside_interval_rows += 1
            return
        stats.rows_in_interval += 1

        source_trip_key = str(row.get("TRIP_ID") or "").strip()
        if not source_trip_key:
            stats.schema_breaches += 1
            return
        missing_data = _missing_data(str(row.get("MISSING_DATA") or ""))
        if missing_data is None:
            stats.schema_breaches += 1
            return
        if missing_data:
            stats.missing_trajectory_warnings += 1
            return
        pickup = _pickup(str(row.get("POLYLINE") or ""))
        if pickup is None:
            stats.invalid_pickup_breaches += 1
            return
        longitude, latitude = pickup
        event = CanonicalDemandEvent(
            source_profile=source_profile,
            dataset_version=dataset_version,
            source_trip_key=source_trip_key,
            demand_event_time_utc=event_time,
            demand_event_semantics="TRIP_STARTED_PROXY",
            pickup_longitude=longitude,
            pickup_latitude=latitude,
            vehicle_type=None,
            service_area_key=None,
            source_cutoff_utc=cutoff_utc,
            extraction_run_id=snapshot_id,
        )
        if not spool.add(event):
            stats.duplicate_trip_breaches += 1
            return
        if event.demand_event_time_utc >= cutoff_utc:
            stats.future_event_breaches += 1
            return
        stats.accepted_rows += 1
        stats.observe_event_time(event_time)
