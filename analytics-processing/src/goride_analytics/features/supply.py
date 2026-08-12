from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Any

from ..errors import ExtractionError
from .builder import SupplySeries


SUPPLY_FEATURE_SQL = """
SELECT
    bucket_start,
    service_area_id::TEXT,
    SUM(available_drivers)::BIGINT,
    MAX(sampled_at)
FROM driver_supply_snapshots
WHERE bucket_start >= %s
  AND bucket_start < %s
GROUP BY bucket_start, service_area_id
ORDER BY bucket_start, service_area_id NULLS FIRST
""".strip()


def load_supply_series(
    connection: Any,
    *,
    from_utc: datetime,
    cutoff_utc: datetime,
    bucket_minutes: int,
) -> SupplySeries:
    bucket = timedelta(minutes=bucket_minutes)
    values: dict[tuple[int, str | None], int] = {}
    try:
        with connection.cursor(name="goride_supply_feature_source") as cursor:
            cursor.itersize = 2_000
            cursor.execute(SUPPLY_FEATURE_SQL, (from_utc, cutoff_utc))
            for raw_time, raw_area, raw_available, raw_sampled_at in cursor:
                if not isinstance(raw_time, datetime) or raw_time.tzinfo is None:
                    raise ExtractionError(
                        "SUPPLY_TIMESTAMP_INVALID",
                        "Supply bucket timestamp must be timezone-aware",
                    )
                event_time = raw_time.astimezone(timezone.utc)
                elapsed = event_time - from_utc
                index = int(elapsed / bucket)
                if event_time != from_utc + index * bucket:
                    raise ExtractionError(
                        "SUPPLY_BUCKET_MISALIGNED",
                        "Supply bucket is not aligned to the feature interval",
                    )
                if not isinstance(raw_sampled_at, datetime) or raw_sampled_at.tzinfo is None:
                    raise ExtractionError(
                        "SUPPLY_TIMESTAMP_INVALID",
                        "Supply sample timestamp must be timezone-aware",
                    )
                sampled_at = raw_sampled_at.astimezone(timezone.utc)
                if sampled_at > event_time + bucket:
                    raise ExtractionError(
                        "SUPPLY_SAMPLE_AFTER_BUCKET",
                        "Supply sample was recorded after its bucket closed",
                    )
                key = (index, str(raw_area) if raw_area is not None else None)
                values[key] = int(raw_available)
        connection.rollback()
        return SupplySeries(values)
    except ExtractionError:
        connection.rollback()
        raise
    except Exception as error:
        connection.rollback()
        raise ExtractionError(
            "SUPPLY_EXTRACTION_FAILED",
            "Could not read bounded driver-supply features",
        ) from error
