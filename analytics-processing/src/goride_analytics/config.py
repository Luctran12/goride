from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Mapping
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

import yaml

from .errors import ConfigurationError
from .hashing import canonical_mapping_sha256


_ENV_NAME = re.compile(r"^[A-Z][A-Z0-9_]*$")
_SAFE_SLUG = re.compile(r"^[a-z0-9][a-z0-9-]*$")
_METRICS = {"MAE", "RMSE", "WAPE"}
_DEMAND_LAGS = {1, 2, 4, 96, 672}
_ROLLING_WINDOWS = {4, 12, 96, 672}


@dataclass(frozen=True)
class ProfileSettings:
    name: str
    purpose: str
    random_seed: int


@dataclass(frozen=True)
class DatasetSettings:
    name: str
    version: str
    source_type: str
    expected_source_crs: str
    expected_timezone: str
    root_env: str | None = None
    manifest_relative_path: str | None = None
    database_env_prefix: str | None = None


@dataclass(frozen=True)
class StudyBounds:
    minimum_longitude: float
    minimum_latitude: float
    maximum_longitude: float
    maximum_latitude: float


@dataclass(frozen=True)
class SpatialSettings:
    source_srid: int
    projected_srid: int
    grid_version: str
    grid_origin_x_meters: float
    grid_origin_y_meters: float
    study_bounds_wgs84: StudyBounds
    primary_cell_size_meters: int
    evaluation_cell_sizes_meters: tuple[int, ...]


@dataclass(frozen=True)
class TemporalSettings:
    source_timezone: str
    storage_timezone: str
    bucket_minutes: int
    forecast_horizons_minutes: tuple[int, ...]


@dataclass(frozen=True)
class QualitySettings:
    reject_missing_trajectory: bool
    reject_invalid_coordinate: bool
    reject_future_timestamp: bool
    minimum_history_buckets: int
    fail_on_checksum_mismatch: bool


@dataclass(frozen=True)
class FeatureSettings:
    demand_lags: tuple[int, ...]
    rolling_windows: tuple[int, ...]
    include_temporal_features: bool
    include_spatial_neighbors: bool
    include_supply_features: bool


@dataclass(frozen=True)
class TimeRange:
    from_value: datetime
    to_value: datetime


@dataclass(frozen=True)
class EvaluationSettings:
    strategy: str
    metrics: tuple[str, ...]
    train: TimeRange | None
    validation: TimeRange | None
    test: TimeRange | None


@dataclass(frozen=True)
class ArtifactSettings:
    feature_format: str
    compression: str
    write_raw_predictions: bool
    write_run_manifest: bool
    checksum_algorithm: str


@dataclass(frozen=True)
class ProcessingConfig:
    path: Path
    config_hash: str
    profile: ProfileSettings
    dataset: DatasetSettings
    spatial: SpatialSettings
    temporal: TemporalSettings
    quality: QualitySettings
    features: FeatureSettings
    evaluation: EvaluationSettings
    artifacts: ArtifactSettings


def _mapping(value: Any, path: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise ConfigurationError(
            "CONFIG_TYPE_INVALID",
            f"{path} must be a mapping",
            {"path": path},
        )
    return value


def _keys(
    value: Mapping[str, Any],
    path: str,
    required: set[str],
    optional: set[str] | None = None,
) -> None:
    optional = optional or set()
    missing = sorted(required - set(value))
    unknown = sorted(set(value) - required - optional)
    if missing:
        raise ConfigurationError(
            "CONFIG_KEY_MISSING",
            f"{path} is missing required keys",
            {"path": path, "missing": missing},
        )
    if unknown:
        raise ConfigurationError(
            "CONFIG_KEY_UNKNOWN",
            f"{path} contains unknown keys",
            {"path": path, "unknown": unknown},
        )


def _string(value: Any, path: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ConfigurationError(
            "CONFIG_VALUE_INVALID",
            f"{path} must be a non-blank string",
            {"path": path},
        )
    return value.strip()


def _integer(value: Any, path: str, minimum: int = 1) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < minimum:
        raise ConfigurationError(
            "CONFIG_VALUE_INVALID",
            f"{path} must be an integer >= {minimum}",
            {"path": path},
        )
    return value


def _number(value: Any, path: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ConfigurationError(
            "CONFIG_VALUE_INVALID",
            f"{path} must be a finite number",
            {"path": path},
        )
    result = float(value)
    if not (-float("inf") < result < float("inf")):
        raise ConfigurationError(
            "CONFIG_VALUE_INVALID",
            f"{path} must be a finite number",
            {"path": path},
        )
    return result


def _boolean(value: Any, path: str) -> bool:
    if not isinstance(value, bool):
        raise ConfigurationError(
            "CONFIG_VALUE_INVALID",
            f"{path} must be boolean",
            {"path": path},
        )
    return value


def _integer_tuple(value: Any, path: str) -> tuple[int, ...]:
    if not isinstance(value, list) or not value:
        raise ConfigurationError(
            "CONFIG_VALUE_INVALID",
            f"{path} must be a non-empty integer list",
            {"path": path},
        )
    result = tuple(_integer(item, f"{path}[{index}]") for index, item in enumerate(value))
    if len(set(result)) != len(result):
        raise ConfigurationError(
            "CONFIG_VALUE_INVALID",
            f"{path} must not contain duplicates",
            {"path": path},
        )
    return result


def _timezone(value: Any, path: str) -> str:
    name = _string(value, path)
    try:
        ZoneInfo(name)
    except ZoneInfoNotFoundError as error:
        raise ConfigurationError(
            "CONFIG_TIMEZONE_INVALID",
            f"{path} is not a known IANA timezone",
            {"path": path, "timezone": name},
        ) from error
    return name


def _env_name(value: Any, path: str) -> str:
    name = _string(value, path)
    if not _ENV_NAME.fullmatch(name):
        raise ConfigurationError(
            "CONFIG_ENV_NAME_INVALID",
            f"{path} must be an uppercase environment variable name",
            {"path": path},
        )
    return name


def _time_range(value: Any, path: str) -> TimeRange:
    mapping = _mapping(value, path)
    _keys(mapping, path, {"from", "to"})
    try:
        from_value = datetime.fromisoformat(_string(mapping["from"], f"{path}.from"))
        to_value = datetime.fromisoformat(_string(mapping["to"], f"{path}.to"))
    except ValueError as error:
        raise ConfigurationError(
            "CONFIG_DATETIME_INVALID",
            f"{path} boundaries must be ISO-8601 date-times",
            {"path": path},
        ) from error
    if from_value.tzinfo is not None or to_value.tzinfo is not None:
        raise ConfigurationError(
            "CONFIG_DATETIME_INVALID",
            f"{path} boundaries must be local wall times interpreted by source_timezone",
            {"path": path},
        )
    if from_value >= to_value:
        raise ConfigurationError(
            "CONFIG_RANGE_INVALID",
            f"{path}.from must be before {path}.to",
            {"path": path},
        )
    return TimeRange(from_value, to_value)


def load_config(path: str | Path) -> ProcessingConfig:
    config_path = Path(path).expanduser().resolve()
    if not config_path.is_file():
        raise ConfigurationError(
            "CONFIG_FILE_NOT_FOUND",
            "Configuration file does not exist",
            {"path": str(config_path)},
        )
    try:
        raw = yaml.safe_load(config_path.read_text(encoding="utf-8"))
    except yaml.YAMLError as error:
        raise ConfigurationError(
            "CONFIG_YAML_INVALID",
            "Configuration file is not valid YAML",
            {"path": str(config_path)},
        ) from error
    root = _mapping(raw, "config")
    required_sections = {
        "profile",
        "dataset",
        "spatial",
        "temporal",
        "quality",
        "features",
        "evaluation",
        "artifacts",
    }
    _keys(root, "config", required_sections)

    profile_raw = _mapping(root["profile"], "profile")
    _keys(profile_raw, "profile", {"name", "purpose", "random_seed"})
    profile = ProfileSettings(
        name=_string(profile_raw["name"], "profile.name"),
        purpose=_string(profile_raw["purpose"], "profile.purpose"),
        random_seed=_integer(profile_raw["random_seed"], "profile.random_seed", 0),
    )
    if not _SAFE_SLUG.fullmatch(profile.name):
        raise ConfigurationError(
            "CONFIG_PROFILE_NAME_INVALID",
            "profile.name must contain only lowercase letters, numbers and hyphens",
            {"profile": profile.name},
        )

    dataset_raw = _mapping(root["dataset"], "dataset")
    _keys(
        dataset_raw,
        "dataset",
        {"name", "version", "source_type", "expected_source_crs", "expected_timezone"},
        {"root_env", "manifest_relative_path", "database_env_prefix"},
    )
    source_type = _string(dataset_raw["source_type"], "dataset.source_type")
    if source_type not in {"archive", "postgresql"}:
        raise ConfigurationError(
            "CONFIG_SOURCE_TYPE_INVALID",
            "dataset.source_type must be archive or postgresql",
            {"sourceType": source_type},
        )
    root_env = (
        _env_name(dataset_raw["root_env"], "dataset.root_env")
        if "root_env" in dataset_raw
        else None
    )
    manifest_relative_path = (
        _string(dataset_raw["manifest_relative_path"], "dataset.manifest_relative_path")
        if "manifest_relative_path" in dataset_raw
        else None
    )
    database_env_prefix = (
        _env_name(dataset_raw["database_env_prefix"], "dataset.database_env_prefix")
        if "database_env_prefix" in dataset_raw
        else None
    )
    if source_type == "archive" and (not root_env or not manifest_relative_path):
        raise ConfigurationError(
            "CONFIG_ARCHIVE_SOURCE_INCOMPLETE",
            "Archive datasets require root_env and manifest_relative_path",
        )
    if source_type == "postgresql" and not database_env_prefix:
        raise ConfigurationError(
            "CONFIG_DATABASE_SOURCE_INCOMPLETE",
            "PostgreSQL datasets require database_env_prefix",
        )
    if source_type == "archive" and database_env_prefix:
        raise ConfigurationError(
            "CONFIG_SOURCE_FIELDS_CONFLICT",
            "Archive dataset cannot define database_env_prefix",
        )
    if source_type == "postgresql" and (root_env or manifest_relative_path):
        raise ConfigurationError(
            "CONFIG_SOURCE_FIELDS_CONFLICT",
            "PostgreSQL dataset cannot define archive path fields",
        )
    expected_timezone = _timezone(
        dataset_raw["expected_timezone"], "dataset.expected_timezone"
    )
    dataset = DatasetSettings(
        name=_string(dataset_raw["name"], "dataset.name"),
        version=_string(dataset_raw["version"], "dataset.version"),
        source_type=source_type,
        expected_source_crs=_string(
            dataset_raw["expected_source_crs"], "dataset.expected_source_crs"
        ),
        expected_timezone=expected_timezone,
        root_env=root_env,
        manifest_relative_path=manifest_relative_path,
        database_env_prefix=database_env_prefix,
    )

    spatial_raw = _mapping(root["spatial"], "spatial")
    _keys(
        spatial_raw,
        "spatial",
        {
            "source_srid",
            "projected_srid",
            "grid_version",
            "grid_origin_x_meters",
            "grid_origin_y_meters",
            "study_bounds_wgs84",
            "primary_cell_size_meters",
            "evaluation_cell_sizes_meters",
        },
    )
    cell_sizes = _integer_tuple(
        spatial_raw["evaluation_cell_sizes_meters"],
        "spatial.evaluation_cell_sizes_meters",
    )
    primary_cell_size = _integer(
        spatial_raw["primary_cell_size_meters"],
        "spatial.primary_cell_size_meters",
    )
    if primary_cell_size not in cell_sizes:
        raise ConfigurationError(
            "CONFIG_PRIMARY_CELL_UNSUPPORTED",
            "Primary cell size must be included in evaluation cell sizes",
            {"primaryCellSizeMeters": primary_cell_size},
        )
    grid_version = _string(spatial_raw["grid_version"], "spatial.grid_version")
    if not _SAFE_SLUG.fullmatch(grid_version):
        raise ConfigurationError(
            "CONFIG_GRID_VERSION_INVALID",
            "spatial.grid_version must be a lowercase safe slug",
            {"gridVersion": grid_version},
        )
    bounds_raw = _mapping(
        spatial_raw["study_bounds_wgs84"],
        "spatial.study_bounds_wgs84",
    )
    _keys(
        bounds_raw,
        "spatial.study_bounds_wgs84",
        {
            "minimum_longitude",
            "minimum_latitude",
            "maximum_longitude",
            "maximum_latitude",
        },
    )
    bounds = StudyBounds(
        minimum_longitude=_number(
            bounds_raw["minimum_longitude"],
            "spatial.study_bounds_wgs84.minimum_longitude",
        ),
        minimum_latitude=_number(
            bounds_raw["minimum_latitude"],
            "spatial.study_bounds_wgs84.minimum_latitude",
        ),
        maximum_longitude=_number(
            bounds_raw["maximum_longitude"],
            "spatial.study_bounds_wgs84.maximum_longitude",
        ),
        maximum_latitude=_number(
            bounds_raw["maximum_latitude"],
            "spatial.study_bounds_wgs84.maximum_latitude",
        ),
    )
    if not (
        -180 <= bounds.minimum_longitude < bounds.maximum_longitude <= 180
        and -90 <= bounds.minimum_latitude < bounds.maximum_latitude <= 90
    ):
        raise ConfigurationError(
            "CONFIG_STUDY_BOUNDS_INVALID",
            "WGS84 study bounds must be ordered and within longitude/latitude limits",
        )
    spatial = SpatialSettings(
        source_srid=_integer(spatial_raw["source_srid"], "spatial.source_srid"),
        projected_srid=_integer(
            spatial_raw["projected_srid"], "spatial.projected_srid"
        ),
        grid_version=grid_version,
        grid_origin_x_meters=_number(
            spatial_raw["grid_origin_x_meters"],
            "spatial.grid_origin_x_meters",
        ),
        grid_origin_y_meters=_number(
            spatial_raw["grid_origin_y_meters"],
            "spatial.grid_origin_y_meters",
        ),
        study_bounds_wgs84=bounds,
        primary_cell_size_meters=primary_cell_size,
        evaluation_cell_sizes_meters=cell_sizes,
    )
    expected_crs = f"EPSG:{spatial.source_srid}"
    if dataset.expected_source_crs.upper() != expected_crs:
        raise ConfigurationError(
            "CONFIG_SOURCE_CRS_MISMATCH",
            "Dataset expected_source_crs must match spatial.source_srid",
            {"expected": expected_crs, "actual": dataset.expected_source_crs},
        )

    temporal_raw = _mapping(root["temporal"], "temporal")
    _keys(
        temporal_raw,
        "temporal",
        {
            "source_timezone",
            "storage_timezone",
            "bucket_minutes",
            "forecast_horizons_minutes",
        },
    )
    source_timezone = _timezone(
        temporal_raw["source_timezone"], "temporal.source_timezone"
    )
    storage_timezone = _timezone(
        temporal_raw["storage_timezone"], "temporal.storage_timezone"
    )
    if source_timezone != dataset.expected_timezone:
        raise ConfigurationError(
            "CONFIG_SOURCE_TIMEZONE_MISMATCH",
            "Dataset and temporal source timezones must match",
            {"dataset": dataset.expected_timezone, "temporal": source_timezone},
        )
    if storage_timezone != "UTC":
        raise ConfigurationError(
            "CONFIG_STORAGE_TIMEZONE_INVALID",
            "temporal.storage_timezone must be UTC",
        )
    bucket_minutes = _integer(
        temporal_raw["bucket_minutes"], "temporal.bucket_minutes"
    )
    horizons = _integer_tuple(
        temporal_raw["forecast_horizons_minutes"],
        "temporal.forecast_horizons_minutes",
    )
    if any(horizon % bucket_minutes for horizon in horizons):
        raise ConfigurationError(
            "CONFIG_HORIZON_MISALIGNED",
            "Every forecast horizon must be divisible by bucket_minutes",
            {"bucketMinutes": bucket_minutes, "horizons": list(horizons)},
        )
    temporal = TemporalSettings(
        source_timezone=source_timezone,
        storage_timezone=storage_timezone,
        bucket_minutes=bucket_minutes,
        forecast_horizons_minutes=horizons,
    )

    quality_raw = _mapping(root["quality"], "quality")
    _keys(
        quality_raw,
        "quality",
        {
            "reject_missing_trajectory",
            "reject_invalid_coordinate",
            "reject_future_timestamp",
            "minimum_history_buckets",
            "fail_on_checksum_mismatch",
        },
    )
    quality = QualitySettings(
        reject_missing_trajectory=_boolean(
            quality_raw["reject_missing_trajectory"],
            "quality.reject_missing_trajectory",
        ),
        reject_invalid_coordinate=_boolean(
            quality_raw["reject_invalid_coordinate"],
            "quality.reject_invalid_coordinate",
        ),
        reject_future_timestamp=_boolean(
            quality_raw["reject_future_timestamp"],
            "quality.reject_future_timestamp",
        ),
        minimum_history_buckets=_integer(
            quality_raw["minimum_history_buckets"],
            "quality.minimum_history_buckets",
        ),
        fail_on_checksum_mismatch=_boolean(
            quality_raw["fail_on_checksum_mismatch"],
            "quality.fail_on_checksum_mismatch",
        ),
    )
    if source_type == "archive" and not quality.fail_on_checksum_mismatch:
        raise ConfigurationError(
            "CONFIG_ARCHIVE_CHECKSUM_REQUIRED",
            "Archive profiles must fail when the source checksum mismatches",
        )

    features_raw = _mapping(root["features"], "features")
    _keys(
        features_raw,
        "features",
        {
            "demand_lags",
            "rolling_windows",
            "include_temporal_features",
            "include_spatial_neighbors",
            "include_supply_features",
        },
    )
    demand_lags = _integer_tuple(features_raw["demand_lags"], "features.demand_lags")
    rolling_windows = _integer_tuple(
        features_raw["rolling_windows"], "features.rolling_windows"
    )
    unsupported_lags = sorted(set(demand_lags) - _DEMAND_LAGS)
    unsupported_windows = sorted(set(rolling_windows) - _ROLLING_WINDOWS)
    if unsupported_lags or unsupported_windows:
        raise ConfigurationError(
            "CONFIG_FEATURE_WINDOW_UNSUPPORTED",
            "Demand lags and rolling windows must map to the frozen feature schema",
            {
                "unsupportedDemandLags": unsupported_lags,
                "unsupportedRollingWindows": unsupported_windows,
            },
        )
    if quality.minimum_history_buckets < max((*demand_lags, *rolling_windows)):
        raise ConfigurationError(
            "CONFIG_HISTORY_TOO_SHORT",
            "minimum_history_buckets must cover every lag and rolling window",
        )
    include_temporal_features = _boolean(
        features_raw["include_temporal_features"],
        "features.include_temporal_features",
    )
    if not include_temporal_features:
        raise ConfigurationError(
            "CONFIG_TEMPORAL_FEATURES_REQUIRED",
            "Feature schema version 1 requires temporal features",
        )
    features = FeatureSettings(
        demand_lags=demand_lags,
        rolling_windows=rolling_windows,
        include_temporal_features=include_temporal_features,
        include_spatial_neighbors=_boolean(
            features_raw["include_spatial_neighbors"],
            "features.include_spatial_neighbors",
        ),
        include_supply_features=_boolean(
            features_raw["include_supply_features"],
            "features.include_supply_features",
        ),
    )
    if source_type == "archive" and features.include_supply_features:
        raise ConfigurationError(
            "CONFIG_SUPPLY_SOURCE_UNAVAILABLE",
            "Archive profile cannot enable GoRide supply features",
        )

    evaluation_raw = _mapping(root["evaluation"], "evaluation")
    _keys(
        evaluation_raw,
        "evaluation",
        {"strategy", "metrics"},
        {"train", "validation", "test"},
    )
    strategy = _string(evaluation_raw["strategy"], "evaluation.strategy")
    if strategy != "rolling_origin":
        raise ConfigurationError(
            "CONFIG_EVALUATION_STRATEGY_INVALID",
            "evaluation.strategy must be rolling_origin",
        )
    metrics_value = evaluation_raw["metrics"]
    if not isinstance(metrics_value, list) or not metrics_value:
        raise ConfigurationError(
            "CONFIG_METRICS_INVALID",
            "evaluation.metrics must be a non-empty list",
        )
    metrics = tuple(_string(item, "evaluation.metrics[]").upper() for item in metrics_value)
    if len(set(metrics)) != len(metrics) or set(metrics) - _METRICS:
        raise ConfigurationError(
            "CONFIG_METRICS_INVALID",
            "evaluation.metrics contains duplicates or unsupported metrics",
            {"allowed": sorted(_METRICS), "actual": list(metrics)},
        )
    split_keys = {key for key in ("train", "validation", "test") if key in evaluation_raw}
    if split_keys and split_keys != {"train", "validation", "test"}:
        raise ConfigurationError(
            "CONFIG_SPLITS_INCOMPLETE",
            "Evaluation splits must define train, validation and test together",
            {"present": sorted(split_keys)},
        )
    train = _time_range(evaluation_raw["train"], "evaluation.train") if split_keys else None
    validation = (
        _time_range(evaluation_raw["validation"], "evaluation.validation")
        if split_keys
        else None
    )
    test = _time_range(evaluation_raw["test"], "evaluation.test") if split_keys else None
    if train and validation and test:
        if train.to_value > validation.from_value or validation.to_value > test.from_value:
            raise ConfigurationError(
                "CONFIG_SPLITS_OVERLAP",
                "Train, validation and test intervals must be chronological and non-overlapping",
            )
    evaluation = EvaluationSettings(strategy, metrics, train, validation, test)

    artifacts_raw = _mapping(root["artifacts"], "artifacts")
    _keys(
        artifacts_raw,
        "artifacts",
        {
            "feature_format",
            "compression",
            "write_raw_predictions",
            "write_run_manifest",
            "checksum_algorithm",
        },
    )
    feature_format = _string(artifacts_raw["feature_format"], "artifacts.feature_format")
    compression = _string(artifacts_raw["compression"], "artifacts.compression")
    checksum_algorithm = _string(
        artifacts_raw["checksum_algorithm"], "artifacts.checksum_algorithm"
    ).lower()
    if feature_format != "parquet" or compression != "zstd":
        raise ConfigurationError(
            "CONFIG_ARTIFACT_FORMAT_INVALID",
            "Phase 1 supports parquet features with zstd compression",
        )
    if checksum_algorithm != "sha256":
        raise ConfigurationError(
            "CONFIG_CHECKSUM_ALGORITHM_INVALID",
            "Phase 1 supports only sha256",
        )
    artifacts = ArtifactSettings(
        feature_format=feature_format,
        compression=compression,
        write_raw_predictions=_boolean(
            artifacts_raw["write_raw_predictions"],
            "artifacts.write_raw_predictions",
        ),
        write_run_manifest=_boolean(
            artifacts_raw["write_run_manifest"],
            "artifacts.write_run_manifest",
        ),
        checksum_algorithm=checksum_algorithm,
    )

    return ProcessingConfig(
        path=config_path,
        config_hash=canonical_mapping_sha256(dict(root)),
        profile=profile,
        dataset=dataset,
        spatial=spatial,
        temporal=temporal,
        quality=quality,
        features=features,
        evaluation=evaluation,
        artifacts=artifacts,
    )
