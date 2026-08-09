from __future__ import annotations

import os
import sqlite3
from dataclasses import dataclass
from pathlib import Path
from typing import Iterator

from ..errors import ExtractionError
from ..hashing import file_sha256
from .model import FeatureRow


@dataclass(frozen=True)
class FeatureArtifact:
    path: Path
    row_count: int
    byte_count: int
    sha256: str


class FeatureSpool:
    def __init__(self, path: Path) -> None:
        self.path = path
        self._connection = sqlite3.connect(path)
        self._connection.execute("PRAGMA journal_mode = DELETE")
        self._connection.execute("PRAGMA synchronous = FULL")
        self._connection.execute(
            """
            CREATE TABLE feature_rows (
                cell_id TEXT NOT NULL,
                inference_cutoff_utc TEXT NOT NULL,
                horizon_minutes INTEGER NOT NULL,
                payload TEXT NOT NULL,
                PRIMARY KEY (cell_id, inference_cutoff_utc, horizon_minutes)
            ) WITHOUT ROWID
            """
        )
        self._pending = 0
        self.duplicate_rows = 0

    def add(self, row: FeatureRow) -> bool:
        try:
            self._connection.execute(
                "INSERT INTO feature_rows VALUES (?, ?, ?, ?)",
                (
                    row.cell_id,
                    row.inference_cutoff_utc.isoformat(),
                    row.horizon_minutes,
                    row.to_storage_json(),
                ),
            )
        except sqlite3.IntegrityError:
            self.duplicate_rows += 1
            return False
        self._pending += 1
        if self._pending >= 10_000:
            self._connection.commit()
            self._pending = 0
        return True

    def rows(self) -> Iterator[FeatureRow]:
        self._connection.commit()
        cursor = self._connection.execute(
            """
            SELECT payload
            FROM feature_rows
            ORDER BY cell_id, inference_cutoff_utc, horizon_minutes
            """
        )
        for (payload,) in cursor:
            yield FeatureRow.from_storage_json(str(payload))

    def count(self) -> int:
        self._connection.commit()
        return int(self._connection.execute("SELECT COUNT(*) FROM feature_rows").fetchone()[0])

    def export_parquet(self, target: Path, *, compression: str = "zstd") -> FeatureArtifact:
        try:
            import pyarrow as pa
            import pyarrow.parquet as pq
        except ImportError as error:
            raise ExtractionError(
                "FEATURE_RUNTIME_MISSING",
                "pyarrow is required to write feature Parquet artifacts",
            ) from error

        schema = pa.schema(
            [
                ("feature_set_version", pa.string()),
                ("source_profile", pa.string()),
                ("dataset_version", pa.string()),
                ("demand_event_semantics", pa.string()),
                ("grid_version", pa.string()),
                ("projected_srid", pa.int32()),
                ("cell_id", pa.string()),
                ("grid_x", pa.int64()),
                ("grid_y", pa.int64()),
                ("cell_size_meters", pa.int32()),
                ("bucket_start_utc", pa.timestamp("us", tz="UTC")),
                ("inference_cutoff_utc", pa.timestamp("us", tz="UTC")),
                ("target_bucket_start_utc", pa.timestamp("us", tz="UTC")),
                ("horizon_minutes", pa.int16()),
                ("target_trip_requests", pa.int64()),
                ("lag_1", pa.int64()),
                ("lag_2", pa.int64()),
                ("lag_4", pa.int64()),
                ("lag_96", pa.int64()),
                ("lag_672", pa.int64()),
                ("rolling_mean_4", pa.float64()),
                ("rolling_mean_12", pa.float64()),
                ("rolling_mean_96", pa.float64()),
                ("rolling_mean_672", pa.float64()),
                ("hour_sin", pa.float64()),
                ("hour_cos", pa.float64()),
                ("day_of_week", pa.int8()),
                ("is_weekend", pa.bool_()),
                ("neighbor_demand_lag_1", pa.int64()),
                ("available_driver_lag_1", pa.int64()),
                ("coverage_ratio", pa.float64()),
                ("quality_status", pa.string()),
                ("feature_artifact_id", pa.string()),
                ("max_feature_source_time_utc", pa.timestamp("us", tz="UTC")),
                ("source_snapshot_sha256", pa.string()),
            ]
        )
        temporary = target.with_name(f".{target.name}.tmp")
        if target.exists() or temporary.exists():
            raise ExtractionError(
                "FEATURE_ARTIFACT_CONFLICT",
                "Feature Parquet output already exists",
                {"file": target.name},
            )
        row_count = 0
        batch: list[dict[str, object]] = []
        try:
            writer = pq.ParquetWriter(
                temporary,
                schema,
                compression=compression,
                use_dictionary=False,
                write_statistics=True,
            )
            try:
                for row in self.rows():
                    value = row.to_dict()
                    value["feature_artifact_id"] = str(row.feature_artifact_id)
                    batch.append(value)
                    if len(batch) >= 10_000:
                        writer.write_table(pa.Table.from_pylist(batch, schema=schema))
                        row_count += len(batch)
                        batch.clear()
                if batch:
                    writer.write_table(pa.Table.from_pylist(batch, schema=schema))
                    row_count += len(batch)
            finally:
                writer.close()
            os.replace(temporary, target)
        except Exception:
            temporary.unlink(missing_ok=True)
            raise
        return FeatureArtifact(
            target,
            row_count,
            target.stat().st_size,
            file_sha256(target),
        )

    def close(self) -> None:
        self._connection.close()

    def __enter__(self) -> FeatureSpool:
        return self

    def __exit__(self, *_args: object) -> None:
        self.close()
