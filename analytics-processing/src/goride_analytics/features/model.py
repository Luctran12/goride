from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime
from typing import Any
from uuid import UUID

from ..extraction.canonical import utc_text


@dataclass(frozen=True)
class FeatureRow:
    feature_set_version: str
    source_profile: str
    dataset_version: str
    demand_event_semantics: str
    grid_version: str
    projected_srid: int
    cell_id: str
    grid_x: int
    grid_y: int
    cell_size_meters: int
    bucket_start_utc: datetime
    inference_cutoff_utc: datetime
    target_bucket_start_utc: datetime
    horizon_minutes: int
    target_trip_requests: int
    lag_1: int | None
    lag_2: int | None
    lag_4: int | None
    lag_96: int | None
    lag_672: int | None
    rolling_mean_4: float | None
    rolling_mean_12: float | None
    rolling_mean_96: float | None
    rolling_mean_672: float | None
    hour_sin: float
    hour_cos: float
    day_of_week: int
    is_weekend: bool
    neighbor_demand_lag_1: int | None
    available_driver_lag_1: int | None
    coverage_ratio: float
    quality_status: str
    feature_artifact_id: UUID
    max_feature_source_time_utc: datetime | None
    source_snapshot_sha256: str

    def to_storage_json(self) -> str:
        value = self.to_dict()
        for key in (
            "bucket_start_utc",
            "inference_cutoff_utc",
            "target_bucket_start_utc",
            "max_feature_source_time_utc",
        ):
            raw = value[key]
            value[key] = utc_text(raw) if isinstance(raw, datetime) else None
        value["feature_artifact_id"] = str(self.feature_artifact_id)
        return json.dumps(value, separators=(",", ":"), sort_keys=True)

    @classmethod
    def from_storage_json(cls, payload: str) -> FeatureRow:
        value = json.loads(payload)
        return cls.from_dict(value)

    @classmethod
    def from_dict(cls, value: dict[str, Any]) -> FeatureRow:
        value = dict(value)
        for key in (
            "bucket_start_utc",
            "inference_cutoff_utc",
            "target_bucket_start_utc",
            "max_feature_source_time_utc",
        ):
            if isinstance(value[key], str):
                value[key] = datetime.fromisoformat(value[key].replace("Z", "+00:00"))
        if not isinstance(value["feature_artifact_id"], UUID):
            value["feature_artifact_id"] = UUID(value["feature_artifact_id"])
        return cls(**value)

    def to_dict(self) -> dict[str, Any]:
        return dict(self.__dict__)
