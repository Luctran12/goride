from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

import yaml

from ..errors import ConfigurationError
from ..hashing import canonical_mapping_sha256


ALLOWED_FEATURES = {
    "hour_sin",
    "hour_cos",
    "day_of_week",
    "is_weekend",
    "lag_1",
    "lag_2",
    "lag_4",
    "lag_96",
    "lag_672",
    "rolling_mean_4",
    "rolling_mean_12",
    "rolling_mean_96",
    "rolling_mean_672",
    "neighbor_demand_lag_1",
}


@dataclass(frozen=True)
class TrainingSampleSettings:
    strategy: str
    positive_row_quota: int
    zero_row_quota: int
    preserve_population_with_sample_weight: bool


@dataclass(frozen=True)
class HgbConfiguration:
    config_id: str
    learning_rate: float
    max_iter: int
    max_leaf_nodes: int
    min_samples_leaf: int
    l2_regularization: float

    def parameters(self, *, random_seed: int) -> dict[str, Any]:
        return {
            "early_stopping": False,
            "l2_regularization": self.l2_regularization,
            "learning_rate": self.learning_rate,
            "loss": "squared_error",
            "max_iter": self.max_iter,
            "max_leaf_nodes": self.max_leaf_nodes,
            "min_samples_leaf": self.min_samples_leaf,
            "random_state": random_seed,
        }

    def to_dict(self) -> dict[str, Any]:
        return {
            "id": self.config_id,
            "l2Regularization": self.l2_regularization,
            "learningRate": self.learning_rate,
            "maxIter": self.max_iter,
            "maxLeafNodes": self.max_leaf_nodes,
            "minSamplesLeaf": self.min_samples_leaf,
        }


@dataclass(frozen=True)
class CandidateExperiment:
    path: Path
    experiment_hash: str
    version: str
    model_family: str
    implementation: str
    primary_metric: str
    secondary_metrics: tuple[str, ...]
    random_seed: int
    maximum_prediction_rows: int
    training_sample: TrainingSampleSettings
    feature_sets: Mapping[str, tuple[str, ...]]
    search_space: tuple[HgbConfiguration, ...]
    selected_feature_set: str
    prediction_interval_enabled: bool
    prediction_interval_reason: str

    def to_dict(self) -> dict[str, Any]:
        return {
            "experimentHash": self.experiment_hash,
            "featureSets": {
                key: list(value) for key, value in self.feature_sets.items()
            },
            "implementation": self.implementation,
            "modelFamily": self.model_family,
            "predictionInterval": {
                "enabled": self.prediction_interval_enabled,
                "reason": self.prediction_interval_reason,
            },
            "primaryMetric": self.primary_metric,
            "randomSeed": self.random_seed,
            "maximumPredictionRows": self.maximum_prediction_rows,
            "searchSpace": [item.to_dict() for item in self.search_space],
            "secondaryMetrics": list(self.secondary_metrics),
            "selectedFeatureSet": self.selected_feature_set,
            "trainingSample": {
                "positiveRowQuota": self.training_sample.positive_row_quota,
                "preservePopulationWithSampleWeight": self.training_sample.preserve_population_with_sample_weight,
                "strategy": self.training_sample.strategy,
                "zeroRowQuota": self.training_sample.zero_row_quota,
            },
            "version": self.version,
        }


def _mapping(value: Any, path: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise ConfigurationError("MODEL_CONFIG_INVALID", f"{path} must be a mapping")
    return value


def _keys(value: Mapping[str, Any], path: str, expected: set[str]) -> None:
    missing = sorted(expected - set(value))
    unknown = sorted(set(value) - expected)
    if missing or unknown:
        raise ConfigurationError(
            "MODEL_CONFIG_INVALID",
            f"{path} has missing or unknown keys",
            {"missing": missing, "path": path, "unknown": unknown},
        )


def _positive_int(value: Any, path: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ConfigurationError("MODEL_CONFIG_INVALID", f"{path} must be a positive integer")
    return value


def _positive_number(value: Any, path: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or value <= 0:
        raise ConfigurationError("MODEL_CONFIG_INVALID", f"{path} must be positive")
    return float(value)


def load_candidate_experiment(path: str | Path) -> CandidateExperiment:
    target = Path(path).expanduser().resolve()
    try:
        raw = yaml.safe_load(target.read_text(encoding="utf-8"))
    except (OSError, yaml.YAMLError) as error:
        raise ConfigurationError("MODEL_CONFIG_INVALID", "Could not read model experiment config") from error
    root = _mapping(raw, "root")
    _keys(
        root,
        "root",
        {
            "version",
            "model_family",
            "implementation",
            "primary_metric",
            "secondary_metrics",
            "random_seed",
            "maximum_prediction_rows",
            "training_sample",
            "feature_sets",
            "search_space",
            "selection_rule",
            "prediction_interval",
        },
    )
    if root["model_family"] != "GRADIENT_BOOSTED_TREES" or root["implementation"] != "sklearn.ensemble.HistGradientBoostingRegressor":
        raise ConfigurationError("MODEL_CONFIG_INVALID", "Phase 6 candidate implementation is not frozen")
    if root["primary_metric"] != "WAPE" or root["secondary_metrics"] != ["MAE", "RMSE"]:
        raise ConfigurationError("MODEL_CONFIG_INVALID", "Phase 6 metric order must be WAPE, MAE, RMSE")
    sample = _mapping(root["training_sample"], "training_sample")
    _keys(
        sample,
        "training_sample",
        {
            "strategy",
            "positive_row_quota",
            "zero_row_quota",
            "preserve_population_with_sample_weight",
        },
    )
    if sample["strategy"] != "deterministic_hash_stratified_weighted" or sample["preserve_population_with_sample_weight"] is not True:
        raise ConfigurationError("MODEL_CONFIG_INVALID", "Training sampling must be deterministic and population weighted")
    feature_sets_raw = _mapping(root["feature_sets"], "feature_sets")
    if tuple(feature_sets_raw) != ("A0", "A1", "A2"):
        raise ConfigurationError("MODEL_CONFIG_INVALID", "Feature sets must be ordered A0, A1, A2")
    feature_sets: dict[str, tuple[str, ...]] = {}
    previous: set[str] = set()
    for key, value in feature_sets_raw.items():
        if not isinstance(value, list) or not value or any(item not in ALLOWED_FEATURES for item in value):
            raise ConfigurationError("MODEL_CONFIG_INVALID", f"feature_sets.{key} is invalid")
        current = set(value)
        if len(current) != len(value) or not previous.issubset(current):
            raise ConfigurationError("MODEL_CONFIG_INVALID", "Feature ablations must be unique and cumulative")
        feature_sets[str(key)] = tuple(str(item) for item in value)
        previous = current
    search_raw = root["search_space"]
    if not isinstance(search_raw, list) or not 2 <= len(search_raw) <= 12:
        raise ConfigurationError("MODEL_CONFIG_INVALID", "search_space must contain 2-12 configurations")
    search: list[HgbConfiguration] = []
    for index, value in enumerate(search_raw):
        item = _mapping(value, f"search_space[{index}]")
        _keys(item, f"search_space[{index}]", {"id", "learning_rate", "max_iter", "max_leaf_nodes", "min_samples_leaf", "l2_regularization"})
        search.append(
            HgbConfiguration(
                config_id=str(item["id"]),
                learning_rate=_positive_number(item["learning_rate"], "learning_rate"),
                max_iter=_positive_int(item["max_iter"], "max_iter"),
                max_leaf_nodes=_positive_int(item["max_leaf_nodes"], "max_leaf_nodes"),
                min_samples_leaf=_positive_int(item["min_samples_leaf"], "min_samples_leaf"),
                l2_regularization=_positive_number(item["l2_regularization"], "l2_regularization"),
            )
        )
    if len({item.config_id for item in search}) != len(search):
        raise ConfigurationError("MODEL_CONFIG_INVALID", "search_space ids must be unique")
    selection = _mapping(root["selection_rule"], "selection_rule")
    _keys(selection, "selection_rule", {"scope", "aggregate", "tie_breakers", "selected_feature_set", "final_holdout_once"})
    if selection != {
        "scope": "DEVELOPMENT_D1_D4_ONLY",
        "aggregate": "pooled_absolute_error_over_pooled_actual",
        "tie_breakers": ["MAE", "CONFIG_ORDER"],
        "selected_feature_set": "A2",
        "final_holdout_once": True,
    }:
        raise ConfigurationError("MODEL_CONFIG_INVALID", "Phase 6 selection rule is not frozen")
    interval = _mapping(root["prediction_interval"], "prediction_interval")
    _keys(interval, "prediction_interval", {"enabled", "reason"})
    if interval["enabled"] is not False or not str(interval["reason"]).strip():
        raise ConfigurationError("MODEL_CONFIG_INVALID", "Prediction intervals must remain disabled with a reason")
    return CandidateExperiment(
        path=target,
        experiment_hash=canonical_mapping_sha256(dict(root)),
        version=str(root["version"]),
        model_family=str(root["model_family"]),
        implementation=str(root["implementation"]),
        primary_metric=str(root["primary_metric"]),
        secondary_metrics=tuple(root["secondary_metrics"]),
        random_seed=_positive_int(root["random_seed"], "random_seed"),
        maximum_prediction_rows=_positive_int(
            root["maximum_prediction_rows"], "maximum_prediction_rows"
        ),
        training_sample=TrainingSampleSettings(
            strategy=str(sample["strategy"]),
            positive_row_quota=_positive_int(sample["positive_row_quota"], "positive_row_quota"),
            zero_row_quota=_positive_int(sample["zero_row_quota"], "zero_row_quota"),
            preserve_population_with_sample_weight=True,
        ),
        feature_sets=feature_sets,
        search_space=tuple(search),
        selected_feature_set=str(selection["selected_feature_set"]),
        prediction_interval_enabled=False,
        prediction_interval_reason=str(interval["reason"]),
    )
