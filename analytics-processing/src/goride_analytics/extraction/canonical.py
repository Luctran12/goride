from __future__ import annotations

import hashlib
import json
import os
import sqlite3
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterator


SNAPSHOT_NAMESPACE = uuid.UUID("5ab16063-5b0d-4c79-ae96-c5e47db96b4b")


def utc_text(value: datetime) -> str:
    if value.tzinfo is None:
        raise ValueError("canonical timestamps must be timezone-aware")
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def build_snapshot_id(
    *,
    source_profile: str,
    dataset_version: str,
    from_utc: datetime,
    cutoff_utc: datetime,
    config_hash: str,
    code_commit: str,
) -> uuid.UUID:
    identity = "|".join(
        (
            "goride-canonical-demand-v1",
            source_profile,
            dataset_version,
            utc_text(from_utc),
            utc_text(cutoff_utc),
            config_hash,
            code_commit,
        )
    )
    return uuid.uuid5(SNAPSHOT_NAMESPACE, identity)


@dataclass(frozen=True)
class CanonicalDemandEvent:
    source_profile: str
    dataset_version: str
    source_trip_key: str
    demand_event_time_utc: datetime
    demand_event_semantics: str
    pickup_longitude: float
    pickup_latitude: float
    vehicle_type: str | None
    service_area_key: str | None
    source_cutoff_utc: datetime
    extraction_run_id: uuid.UUID

    def to_dict(self) -> dict[str, object]:
        return {
            "dataset_version": self.dataset_version,
            "demand_event_semantics": self.demand_event_semantics,
            "demand_event_time_utc": utc_text(self.demand_event_time_utc),
            "extraction_run_id": str(self.extraction_run_id),
            "pickup_wgs84": {
                "latitude": self.pickup_latitude,
                "longitude": self.pickup_longitude,
                "srid": 4326,
                "type": "Point",
            },
            "service_area_key": self.service_area_key,
            "source_cutoff_utc": utc_text(self.source_cutoff_utc),
            "source_profile": self.source_profile,
            "source_trip_key": self.source_trip_key,
            "vehicle_type": self.vehicle_type,
        }


@dataclass(frozen=True)
class SnapshotArtifact:
    path: Path
    row_count: int
    byte_count: int
    sha256: str


class CanonicalSpool:
    """Disk-backed uniqueness and ordering for bounded-memory extraction."""

    def __init__(self, path: Path) -> None:
        self.path = path
        self._connection = sqlite3.connect(path)
        self._connection.execute("PRAGMA journal_mode = DELETE")
        self._connection.execute("PRAGMA synchronous = FULL")
        self._connection.execute(
            """
            CREATE TABLE canonical_events (
                source_profile TEXT NOT NULL,
                dataset_version TEXT NOT NULL,
                source_trip_key TEXT NOT NULL,
                demand_event_time_utc TEXT NOT NULL,
                payload TEXT NOT NULL,
                PRIMARY KEY (
                    source_profile,
                    dataset_version,
                    source_trip_key
                )
            ) WITHOUT ROWID
            """
        )
        self._pending = 0

    def add(self, event: CanonicalDemandEvent) -> bool:
        payload = json.dumps(
            event.to_dict(),
            ensure_ascii=False,
            allow_nan=False,
            separators=(",", ":"),
            sort_keys=True,
        )
        try:
            self._connection.execute(
                """
                INSERT INTO canonical_events (
                    source_profile,
                    dataset_version,
                    source_trip_key,
                    demand_event_time_utc,
                    payload
                ) VALUES (?, ?, ?, ?, ?)
                """,
                (
                    event.source_profile,
                    event.dataset_version,
                    event.source_trip_key,
                    utc_text(event.demand_event_time_utc),
                    payload,
                ),
            )
        except sqlite3.IntegrityError:
            return False
        self._pending += 1
        if self._pending >= 10_000:
            self._connection.commit()
            self._pending = 0
        return True

    def rows(self) -> Iterator[str]:
        self._connection.commit()
        cursor = self._connection.execute(
            """
            SELECT payload
            FROM canonical_events
            ORDER BY demand_event_time_utc, source_trip_key
            """
        )
        for (payload,) in cursor:
            yield str(payload)

    def export_jsonl(self, target: Path) -> SnapshotArtifact:
        temporary = target.with_name(f".{target.name}.tmp")
        if target.exists() or temporary.exists():
            raise FileExistsError(f"snapshot output already exists: {target.name}")
        digest = hashlib.sha256()
        row_count = 0
        byte_count = 0
        with temporary.open("wb") as output:
            for payload in self.rows():
                line = (payload + "\n").encode("utf-8")
                output.write(line)
                digest.update(line)
                row_count += 1
                byte_count += len(line)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, target)
        return SnapshotArtifact(target, row_count, byte_count, digest.hexdigest())

    def close(self) -> None:
        self._connection.close()

    def __enter__(self) -> CanonicalSpool:
        return self

    def __exit__(self, *_args: object) -> None:
        self.close()
