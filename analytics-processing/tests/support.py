from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

import yaml


def valid_config_mapping() -> dict[str, Any]:
    return {
        "profile": {
            "name": "test-profile",
            "purpose": "unit-test",
            "random_seed": 5537,
        },
        "dataset": {
            "name": "test-dataset",
            "version": "v1",
            "source_type": "archive",
            "root_env": "TEST_ANALYTICS_ROOT",
            "manifest_relative_path": "manifests/datasets/test-v1.json",
            "expected_source_crs": "EPSG:4326",
            "expected_timezone": "Europe/Lisbon",
        },
        "spatial": {
            "source_srid": 4326,
            "projected_srid": 3763,
            "primary_cell_size_meters": 500,
            "evaluation_cell_sizes_meters": [500, 1000],
        },
        "temporal": {
            "source_timezone": "Europe/Lisbon",
            "storage_timezone": "UTC",
            "bucket_minutes": 15,
            "forecast_horizons_minutes": [15, 30, 60],
        },
        "quality": {
            "reject_missing_trajectory": True,
            "reject_invalid_coordinate": True,
            "reject_future_timestamp": True,
            "minimum_history_buckets": 672,
            "fail_on_checksum_mismatch": True,
        },
        "features": {
            "demand_lags": [1, 4, 96, 672],
            "rolling_windows": [4, 96, 672],
            "include_temporal_features": True,
            "include_spatial_neighbors": True,
            "include_supply_features": False,
        },
        "evaluation": {
            "strategy": "rolling_origin",
            "metrics": ["MAE", "RMSE", "WAPE"],
            "train": {
                "from": "2013-07-01T00:00:00",
                "to": "2014-03-01T00:00:00",
            },
            "validation": {
                "from": "2014-03-01T00:00:00",
                "to": "2014-05-01T00:00:00",
            },
            "test": {
                "from": "2014-05-01T00:00:00",
                "to": "2014-07-01T00:00:00",
            },
        },
        "artifacts": {
            "feature_format": "parquet",
            "compression": "zstd",
            "write_raw_predictions": True,
            "write_run_manifest": True,
            "checksum_algorithm": "sha256",
        },
    }


def write_config(path: Path, mapping: dict[str, Any] | None = None) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        yaml.safe_dump(mapping or valid_config_mapping(), sort_keys=False),
        encoding="utf-8",
    )
    return path


def create_data_root(root: Path, *, include_source_relative_path: bool = True) -> Path:
    source = root / "raw" / "test-dataset" / "v1" / "source.zip"
    source.parent.mkdir(parents=True, exist_ok=True)
    source.write_bytes(b"deterministic-test-source\n")
    manifest: dict[str, Any] = {
        "datasetName": "test-dataset",
        "datasetVersion": "v1",
        "sourceFile": "source.zip",
        "sourceCrs": "EPSG:4326",
        "timezone": "Europe/Lisbon",
        "license": "CC BY 4.0",
        "sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "immutable": True,
    }
    if include_source_relative_path:
        manifest["sourceRelativePath"] = "raw/test-dataset/v1/source.zip"
    manifest_path = root / "manifests" / "datasets" / "test-v1.json"
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return root
