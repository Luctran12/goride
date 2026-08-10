from __future__ import annotations

import json
import math
import os
import platform
import re
import shutil
import time
from dataclasses import dataclass
from importlib import metadata
from pathlib import Path
from typing import Any, Callable, Mapping
from uuid import UUID
from zoneinfo import ZoneInfo

from ..config import ProcessingConfig
from ..database import (
    DatabaseSettings,
    PostgresProcessingRunRepository,
    ProcessingRunRepository,
    connect_database,
    processing_run_id,
    verify_database,
)
from ..errors import AnalyticsError, DataQualityError, ExtractionError
from ..evaluation.folds import EvaluationFold, build_rolling_origin_folds
from ..evaluation.metrics import demand_quantile_slice, time_of_day_slice
from ..features.persistence import load_resumable_feature_artifact
from ..hashing import canonical_mapping_sha256, file_sha256
from ..quality import QualityResult
from ..runs import RunIdentity, allocate_run_directory, build_run_identity, write_run_manifest
from .baseline import BaselineEvidence, load_baseline_evidence
from .data import (
    count_training_strata,
    feature_indexes,
    iter_evaluation_batches,
    load_sampled_training_set,
)
from .experiment import CandidateExperiment, HgbConfiguration, load_candidate_experiment


_COMMIT_HASH = re.compile(r"^(?:[0-9a-f]{40}|[0-9a-f]{64})$")
os.environ.setdefault("LOKY_MAX_CPU_COUNT", "1")


@dataclass
class VectorAccumulator:
    sample_count: int = 0
    absolute_error_sum: float = 0.0
    squared_error_sum: float = 0.0
    actual_sum: float = 0.0

    def update(self, actual, prediction, mask=None) -> None:
        import numpy as np

        if mask is not None:
            actual = actual[mask]
            prediction = prediction[mask]
        if actual.size == 0:
            return
        error = prediction - actual
        self.sample_count += int(actual.size)
        self.absolute_error_sum += float(np.abs(error).sum())
        self.squared_error_sum += float(np.square(error).sum())
        self.actual_sum += float(actual.sum())

    def result(self) -> dict[str, float | int | None]:
        if self.sample_count <= 0:
            raise ValueError("metric accumulator is empty")
        return {
            "sampleCount": self.sample_count,
            "absoluteErrorSum": self.absolute_error_sum,
            "squaredErrorSum": self.squared_error_sum,
            "actualSum": self.actual_sum,
            "mae": self.absolute_error_sum / self.sample_count,
            "rmse": math.sqrt(self.squared_error_sum / self.sample_count),
            "wape": None if self.actual_sum == 0 else self.absolute_error_sum / self.actual_sum,
        }


class CandidateMetricBook:
    def __init__(self) -> None:
        self.values: dict[tuple[str, str, int, str, str], VectorAccumulator] = {}
        self.negative_prediction_count = 0
        self.minimum_prediction: float | None = None

    def update(
        self,
        *,
        feature_set: str,
        fold_key: str,
        horizon: int,
        actual,
        prediction,
        demand_slices,
        time_slices,
    ) -> None:
        import numpy as np

        self.negative_prediction_count += int(np.count_nonzero(prediction < 0))
        batch_minimum = float(np.min(prediction))
        if self.minimum_prediction is None or batch_minimum < self.minimum_prediction:
            self.minimum_prediction = batch_minimum

        groups = [("ALL", "ALL", None)]
        groups.extend(
            ("DEMAND_QUANTILE", str(value), demand_slices == value)
            for value in np.unique(demand_slices)
        )
        groups.extend(
            ("TIME_OF_DAY", str(value), time_slices == value)
            for value in np.unique(time_slices)
        )
        for slice_type, slice_key, mask in groups:
            key = (feature_set, fold_key, horizon, slice_type, slice_key)
            self.values.setdefault(key, VectorAccumulator()).update(actual, prediction, mask)

    def records(self) -> list[dict[str, object]]:
        return [
            {
                "modelName": "HIST_GRADIENT_BOOSTING",
                "featureSet": feature_set,
                "foldKey": fold,
                "horizonMinutes": horizon,
                "sliceType": slice_type,
                "sliceKey": slice_key,
                **accumulator.result(),
            }
            for (feature_set, fold, horizon, slice_type, slice_key), accumulator in sorted(self.values.items())
        ]


class PredictionFileWriter:
    def __init__(
        self,
        root: Path,
        *,
        run_id: str,
        source_feature_run_id: str,
        feature_set_version: str,
        feature_group: str,
        fold: EvaluationFold,
        horizon: int,
        compression: str,
    ) -> None:
        import pyarrow as pa
        import pyarrow.parquet as pq

        self.root = root
        self.feature_group = feature_group
        self.fold = fold
        self.horizon = horizon
        self.run_id = run_id
        self.source_feature_run_id = source_feature_run_id
        self.feature_set_version = feature_set_version
        directory = root / f"feature_set={feature_group}" / f"fold={fold.key}" / f"horizon={horizon}"
        directory.mkdir(parents=True, exist_ok=False)
        self.path = directory / "part-00000.parquet"
        self.schema = pa.schema(
            [
                ("training_run_id", pa.string()),
                ("source_feature_run_id", pa.string()),
                ("feature_set_version", pa.string()),
                ("model_name", pa.string()),
                ("feature_group", pa.string()),
                ("fold_key", pa.string()),
                ("split_role", pa.string()),
                ("cell_id", pa.string()),
                ("inference_cutoff_utc", pa.timestamp("us", tz="UTC")),
                ("target_bucket_start_utc", pa.timestamp("us", tz="UTC")),
                ("horizon_minutes", pa.int16()),
                ("actual_demand", pa.int64()),
                ("predicted_demand", pa.float64()),
                ("demand_volume_slice", pa.string()),
                ("time_of_day_slice", pa.string()),
                ("absolute_error", pa.float64()),
                ("squared_error", pa.float64()),
            ]
        )
        self.writer = pq.ParquetWriter(
            self.path,
            self.schema,
            compression=compression,
            use_dictionary=True,
            write_statistics=True,
        )
        self.rows = 0

    def write(self, batch, prediction, demand_slices, time_slices) -> None:
        import numpy as np
        import pyarrow as pa

        if batch.cell_ids is None or batch.inference_cutoff_utc is None or batch.target_bucket_start_utc is None:
            raise RuntimeError("prediction metadata is required")
        count = int(batch.actual.size)
        errors = prediction - batch.actual
        table = pa.Table.from_pydict(
            {
                "training_run_id": [self.run_id] * count,
                "source_feature_run_id": [self.source_feature_run_id] * count,
                "feature_set_version": [self.feature_set_version] * count,
                "model_name": ["HIST_GRADIENT_BOOSTING"] * count,
                "feature_group": [self.feature_group] * count,
                "fold_key": [self.fold.key] * count,
                "split_role": [self.fold.split_role] * count,
                "cell_id": batch.cell_ids,
                "inference_cutoff_utc": batch.inference_cutoff_utc,
                "target_bucket_start_utc": batch.target_bucket_start_utc,
                "horizon_minutes": np.full(count, self.horizon, dtype=np.int16),
                "actual_demand": batch.actual.astype(np.int64),
                "predicted_demand": prediction,
                "demand_volume_slice": demand_slices.tolist(),
                "time_of_day_slice": time_slices.tolist(),
                "absolute_error": np.abs(errors),
                "squared_error": np.square(errors),
            },
            schema=self.schema,
        )
        self.writer.write_table(table)
        self.rows += count

    def close(self) -> dict[str, object]:
        self.writer.close()
        return {
            "bytes": self.path.stat().st_size,
            "featureSet": self.feature_group,
            "file": self.path.relative_to(self.root.parent).as_posix(),
            "foldKey": self.fold.key,
            "horizonMinutes": self.horizon,
            "rows": self.rows,
            "sha256": file_sha256(self.path),
        }


@dataclass(frozen=True)
class TrainingOutcome:
    artifact_run_id: str
    processing_run_id: UUID
    source_artifact_run_id: str
    baseline_artifact_run_id: str
    experiment_hash: str
    model_version: str
    model_sha256: str
    raw_prediction_rows: int
    raw_prediction_sha256: str
    metric_group_count: int
    quality_status: str
    attempt_no: int
    run_directory: Path

    def to_dict(self) -> dict[str, object]:
        return {
            "artifactRunId": self.artifact_run_id,
            "attemptNo": self.attempt_no,
            "candidateArtifact": {
                "baselineArtifactRunId": self.baseline_artifact_run_id,
                "experimentHash": self.experiment_hash,
                "metricGroups": self.metric_group_count,
                "modelSha256": self.model_sha256,
                "modelVersion": self.model_version,
                "rawPredictionRows": self.raw_prediction_rows,
                "rawPredictionSha256": self.raw_prediction_sha256,
                "sourceArtifactRunId": self.source_artifact_run_id,
            },
            "processingRunId": str(self.processing_run_id),
            "qualityStatus": self.quality_status,
        }


def _write_json(path: Path, value: Any) -> None:
    temporary = path.with_name(f".{path.name}.tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    os.replace(temporary, path)


def _write_checksums(directory: Path) -> None:
    files = sorted(item for item in directory.iterdir() if item.is_file() and not item.name.startswith(".") and item.name != "checksums.sha256")
    content = "".join(f"{file_sha256(item)}  {item.name}\n" for item in files)
    temporary = directory / ".checksums.sha256.tmp"
    temporary.write_text(content, encoding="utf-8")
    os.replace(temporary, directory / "checksums.sha256")


def _analytics_root(config: ProcessingConfig, environment: Mapping[str, str]) -> Path:
    variable = config.dataset.root_env or "GORIDE_ANALYTICS_DATA_ROOT"
    raw = environment.get(variable, "").strip()
    if not raw:
        raise ExtractionError("MODEL_ARTIFACT_ROOT_MISSING", f"Environment variable {variable} is required")
    root = Path(raw).expanduser().resolve()
    if not root.is_dir():
        raise ExtractionError("MODEL_ARTIFACT_ROOT_INVALID", "Analytics data root does not exist")
    return root


def _fit_model(configuration: HgbConfiguration, experiment: CandidateExperiment, features, target, weights):
    import numpy as np

    try:
        from sklearn.ensemble import HistGradientBoostingRegressor
    except ImportError as error:
        raise ExtractionError("MODEL_RUNTIME_MISSING", "scikit-learn Phase 6 dependencies are required") from error
    training_features = np.asarray(features, dtype=np.float64)
    all_missing = np.all(np.isnan(training_features), axis=0)
    if np.any(all_missing):
        training_features = training_features.copy()
        training_features[:, all_missing] = 0.0
    model = HistGradientBoostingRegressor(**configuration.parameters(random_seed=experiment.random_seed))
    return model.fit(training_features, target, sample_weight=weights)


def _search_candidate(
    artifact,
    experiment: CandidateExperiment,
    folds: tuple[EvaluationFold, ...],
    horizons: tuple[int, ...],
    counts,
) -> tuple[list[dict[str, object]], dict[str, HgbConfiguration], list[dict[str, object]], float]:
    import numpy as np

    started = time.perf_counter()
    development = tuple(fold for fold in folds if fold.split_role == "DEVELOPMENT")
    score_records: list[dict[str, object]] = []
    sample_records: list[dict[str, object]] = []
    for fold in development:
        for horizon in horizons:
            sample = load_sampled_training_set(
                artifact,
                experiment,
                fold,
                horizon,
                counts[(fold.key, horizon)],
            )
            sample_records.append({"foldKey": fold.key, "horizonMinutes": horizon, **sample.to_dict()})
            models = {}
            fit_seconds = {}
            for feature_set, names in experiment.feature_sets.items():
                indexes = feature_indexes(sample.feature_names, names)
                for configuration in experiment.search_space:
                    fit_started = time.perf_counter()
                    models[(feature_set, configuration.config_id)] = _fit_model(
                        configuration,
                        experiment,
                        sample.features[:, indexes],
                        sample.target,
                        sample.sample_weight,
                    )
                    fit_seconds[(feature_set, configuration.config_id)] = time.perf_counter() - fit_started
            accumulators = {key: VectorAccumulator() for key in models}
            indexes_by_feature = {
                key: feature_indexes(sample.feature_names, names)
                for key, names in experiment.feature_sets.items()
            }
            for batch in iter_evaluation_batches(
                artifact,
                experiment,
                fold,
                horizon,
                include_metadata=False,
            ):
                for (feature_set, config_id), model in models.items():
                    prediction = np.maximum(0.0, model.predict(batch.features[:, indexes_by_feature[feature_set]]))
                    accumulators[(feature_set, config_id)].update(batch.actual, prediction)
            for (feature_set, config_id), accumulator in sorted(accumulators.items()):
                score_records.append(
                    {
                        "featureSet": feature_set,
                        "configId": config_id,
                        "foldKey": fold.key,
                        "horizonMinutes": horizon,
                        "fitSeconds": fit_seconds[(feature_set, config_id)],
                        **accumulator.result(),
                    }
                )
    selected: dict[str, HgbConfiguration] = {}
    for feature_set in experiment.feature_sets:
        candidates = []
        for order, configuration in enumerate(experiment.search_space):
            records = [
                item
                for item in score_records
                if item["featureSet"] == feature_set and item["configId"] == configuration.config_id
            ]
            absolute = sum(float(item["absoluteErrorSum"]) for item in records)
            actual = sum(float(item["actualSum"]) for item in records)
            samples = sum(int(item["sampleCount"]) for item in records)
            wape = float("inf") if actual == 0 else absolute / actual
            mae = absolute / samples
            candidates.append((wape, mae, order, configuration))
        selected[feature_set] = min(candidates, key=lambda item: item[:3])[3]
    return score_records, selected, sample_records, time.perf_counter() - started


def _slices(actual, targets, thresholds, source_timezone: str):
    import numpy as np

    zone = ZoneInfo(source_timezone)
    demand = np.asarray([demand_quantile_slice(int(value), thresholds) for value in actual], dtype=object)
    time_values = np.asarray([time_of_day_slice(value.astimezone(zone).hour) for value in targets], dtype=object)
    return demand, time_values


def _evaluate_selected(
    config: ProcessingConfig,
    artifact,
    baseline: BaselineEvidence,
    experiment: CandidateExperiment,
    folds: tuple[EvaluationFold, ...],
    horizons: tuple[int, ...],
    counts,
    selected: Mapping[str, HgbConfiguration],
    identity: RunIdentity,
    run_directory: Path,
) -> tuple[
    list[dict[str, object]],
    list[dict[str, object]],
    dict[int, object],
    int,
    float,
    list[dict[str, object]],
    int,
    float | None,
]:
    import numpy as np

    started = time.perf_counter()
    prediction_root = run_directory / "candidate-predictions"
    prediction_root.mkdir()
    metrics = CandidateMetricBook()
    files: list[dict[str, object]] = []
    final_models: dict[int, object] = {}
    sample_records: list[dict[str, object]] = []
    rows = 0
    for fold in folds:
        for horizon in horizons:
            sample = load_sampled_training_set(
                artifact,
                experiment,
                fold,
                horizon,
                counts[(fold.key, horizon)],
            )
            sample_records.append({"foldKey": fold.key, "horizonMinutes": horizon, **sample.to_dict()})
            indexes = {
                key: feature_indexes(sample.feature_names, names)
                for key, names in experiment.feature_sets.items()
            }
            models = {
                key: _fit_model(
                    selected[key],
                    experiment,
                    sample.features[:, indexes[key]],
                    sample.target,
                    sample.sample_weight,
                )
                for key in experiment.feature_sets
            }
            if fold.key == "FINAL":
                final_models[horizon] = models[experiment.selected_feature_set]
            writers = {
                key: PredictionFileWriter(
                    prediction_root,
                    run_id=identity.run_id,
                    source_feature_run_id=artifact.source_run_id,
                    feature_set_version=artifact.feature_set_version,
                    feature_group=key,
                    fold=fold,
                    horizon=horizon,
                    compression=config.artifacts.compression,
                )
                for key in experiment.feature_sets
            }
            try:
                for batch in iter_evaluation_batches(artifact, experiment, fold, horizon):
                    if batch.target_bucket_start_utc is None:
                        raise RuntimeError("target metadata is required")
                    demand_slices, time_slices = _slices(
                        batch.actual,
                        batch.target_bucket_start_utc,
                        baseline.quantile_thresholds[fold.key],
                        config.temporal.source_timezone,
                    )
                    for key, model in models.items():
                        prediction = np.maximum(0.0, model.predict(batch.features[:, indexes[key]]))
                        writers[key].write(batch, prediction, demand_slices, time_slices)
                        metrics.update(
                            feature_set=key,
                            fold_key=fold.key,
                            horizon=horizon,
                            actual=batch.actual,
                            prediction=prediction,
                            demand_slices=demand_slices,
                            time_slices=time_slices,
                        )
            finally:
                for writer in writers.values():
                    item = writer.close()
                    files.append(item)
                    rows += int(item["rows"])
    return (
        metrics.records(),
        files,
        final_models,
        rows,
        time.perf_counter() - started,
        sample_records,
        metrics.negative_prediction_count,
        metrics.minimum_prediction,
    )


def _comparison(candidate_metrics, baseline: BaselineEvidence, selected_feature_set: str):
    candidate = {
        (item["foldKey"], item["horizonMinutes"]): item
        for item in candidate_metrics
        if item["featureSet"] == selected_feature_set and item["sliceType"] == "ALL"
    }
    baselines = {
        (item["modelName"], item["foldKey"], item["horizonMinutes"]): item
        for item in baseline.metrics
        if item.get("sliceType") == "ALL"
    }
    result = []
    for (fold, horizon), candidate_row in sorted(candidate.items()):
        for model_name in ("HISTORICAL_MEAN", "SEASONAL_NAIVE"):
            baseline_row = baselines[(model_name, fold, horizon)]
            result.append(
                {
                    "baselineModel": model_name,
                    "candidateFeatureSet": selected_feature_set,
                    "foldKey": fold,
                    "horizonMinutes": horizon,
                    "sampleCount": candidate_row["sampleCount"],
                    "maeDelta": float(candidate_row["mae"]) - float(baseline_row["mae"]),
                    "rmseDelta": float(candidate_row["rmse"]) - float(baseline_row["rmse"]),
                    "wapeDelta": float(candidate_row["wape"]) - float(baseline_row["wape"]),
                    "candidate": {key: candidate_row[key] for key in ("mae", "rmse", "wape")},
                    "baseline": {key: baseline_row[key] for key in ("mae", "rmse", "wape")},
                }
            )
    return result


def _peak_working_set_bytes() -> int | None:
    if os.name != "nt":
        try:
            import resource

            return int(resource.getrusage(resource.RUSAGE_SELF).ru_maxrss * 1024)
        except Exception:
            return None
    try:
        import ctypes
        from ctypes import wintypes

        class Counters(ctypes.Structure):
            _fields_ = [
                ("cb", wintypes.DWORD),
                ("PageFaultCount", wintypes.DWORD),
                ("PeakWorkingSetSize", ctypes.c_size_t),
                ("WorkingSetSize", ctypes.c_size_t),
                ("QuotaPeakPagedPoolUsage", ctypes.c_size_t),
                ("QuotaPagedPoolUsage", ctypes.c_size_t),
                ("QuotaPeakNonPagedPoolUsage", ctypes.c_size_t),
                ("QuotaNonPagedPoolUsage", ctypes.c_size_t),
                ("PagefileUsage", ctypes.c_size_t),
                ("PeakPagefileUsage", ctypes.c_size_t),
            ]

        counters = Counters()
        counters.cb = ctypes.sizeof(Counters)
        handle = ctypes.windll.kernel32.GetCurrentProcess()
        if not ctypes.windll.psapi.GetProcessMemoryInfo(handle, ctypes.byref(counters), counters.cb):
            return None
        return int(counters.PeakWorkingSetSize)
    except Exception:
        return None


def run_candidate_training(
    config: ProcessingConfig,
    *,
    feature_run: str | Path,
    baseline_run: str | Path,
    experiment_config: str | Path,
    environment: Mapping[str, str] | None = None,
    identity: RunIdentity | None = None,
    connection_factory: Callable[..., Any] = connect_database,
    repository_factory: Callable[[Any], ProcessingRunRepository] = PostgresProcessingRunRepository,
) -> TrainingOutcome:
    environment = environment or os.environ
    root = _analytics_root(config, environment)
    experiment = load_candidate_experiment(experiment_config)
    if experiment.random_seed != config.profile.random_seed:
        raise ExtractionError("MODEL_SEED_MISMATCH", "Experiment and processing profile seeds must match")
    artifact = load_resumable_feature_artifact(config, root=root, feature_run=feature_run)
    baseline = load_baseline_evidence(config, artifact, root=root, baseline_run=baseline_run)
    expected_rows = baseline.raw_prediction_rows // 2 * len(experiment.feature_sets)
    if baseline.raw_prediction_rows % 2 or expected_rows > experiment.maximum_prediction_rows:
        raise ExtractionError(
            "MODEL_PREDICTION_COST_GUARD_EXCEEDED",
            "Projected candidate predictions exceed the frozen experiment guard",
            {"maximumRows": experiment.maximum_prediction_rows, "projectedRows": expected_rows},
        )
    folds = build_rolling_origin_folds(config)
    horizons = config.temporal.forecast_horizons_minutes
    identity = identity or build_run_identity(config, "training")
    if not _COMMIT_HASH.fullmatch(identity.git_commit):
        raise ExtractionError("CODE_COMMIT_UNAVAILABLE", "A full Git commit hash is required for training evidence")
    run_directory = allocate_run_directory(root, identity)
    write_run_manifest(run_directory, identity)
    shutil.copyfile(experiment.path, run_directory / "experiment-config.yml")
    _write_json(run_directory / "experiment-contract.json", experiment.to_dict())
    connection = None
    repository = None
    db_run_id = processing_run_id(identity)
    started = False
    terminal = False
    raw_rows = 0
    total_started = time.perf_counter()
    try:
        settings = DatabaseSettings.from_environment(environment)
        connection = connection_factory(settings, read_only=False)
        database_metadata = verify_database(connection, require_forecast_schema=True)
        repository = repository_factory(connection)
        attempt_no = repository.start(
            run_id=db_run_id,
            run_type="TRAINING",
            identity=identity,
            source_profile=config.profile.name,
            dataset_version=config.dataset.version,
            source_cutoff=folds[-1].evaluate_to_utc,
            input_manifest={
                "baselineArtifactRunId": baseline.run_id,
                "baselineRawPredictionSha256": baseline.raw_prediction_sha256,
                "database": database_metadata.to_dict(),
                "experimentHash": experiment.experiment_hash,
                "featureArtifactSha256": artifact.sha256,
                "featureSetVersion": artifact.feature_set_version,
                "sourceArtifactRunId": artifact.source_run_id,
            },
        )
        started = True
        count_started = time.perf_counter()
        counts = count_training_strata(artifact, folds, horizons)
        count_seconds = time.perf_counter() - count_started
        search_metrics, selected, search_samples, search_seconds = _search_candidate(
            artifact, experiment, folds, horizons, counts
        )
        _write_json(
            run_directory / "model-search.json",
            {
                "developmentOnly": True,
                "experimentHash": experiment.experiment_hash,
                "records": search_metrics,
                "sampleEvidence": search_samples,
                "selectedConfigurations": {key: value.to_dict() for key, value in selected.items()},
                "selectionRule": "pooled development WAPE; tie MAE then config order",
            },
        )
        (
            candidate_metrics,
            raw_files,
            final_models,
            raw_rows,
            evaluation_seconds,
            evaluation_samples,
            negative_predictions,
            minimum_prediction,
        ) = _evaluate_selected(
            config,
            artifact,
            baseline,
            experiment,
            folds,
            horizons,
            counts,
            selected,
            identity,
            run_directory,
        )
        if raw_rows != expected_rows:
            raise ExtractionError(
                "MODEL_PREDICTION_COUNT_MISMATCH",
                "Candidate raw prediction count differs from the frozen baseline population",
                {"actualRows": raw_rows, "expectedRows": expected_rows},
            )
        raw_manifest_payload = {"files": raw_files, "rowCount": raw_rows}
        raw_sha256 = canonical_mapping_sha256(raw_manifest_payload)
        _write_json(
            run_directory / "candidate-predictions-manifest.json",
            {**raw_manifest_payload, "sha256": raw_sha256},
        )
        comparison = _comparison(candidate_metrics, baseline, experiment.selected_feature_set)
        _write_json(
            run_directory / "candidate-metrics.json",
            {
                "baselineArtifactRunId": baseline.run_id,
                "baselineRawPredictionSha256": baseline.raw_prediction_sha256,
                "comparison": comparison,
                "records": candidate_metrics,
            },
        )
        try:
            import joblib
        except ImportError as error:
            raise ExtractionError("MODEL_RUNTIME_MISSING", "joblib is required to serialize candidate models") from error
        model_version = (
            f"{experiment.version}-g{artifact.cell_size_meters}-"
            f"{experiment.experiment_hash[:10]}-{identity.git_commit[:10]}"
        )
        model_path = run_directory / "candidate-models.joblib"
        joblib.dump(
            {
                "experimentHash": experiment.experiment_hash,
                "featureNames": list(experiment.feature_sets[experiment.selected_feature_set]),
                "featureSet": experiment.selected_feature_set,
                "cellSizeMeters": artifact.cell_size_meters,
                "horizonModels": final_models,
                "modelVersion": model_version,
                "selectedConfiguration": selected[experiment.selected_feature_set].to_dict(),
                "trainingCutoffUtc": folds[-1].train_to_utc.isoformat().replace("+00:00", "Z"),
            },
            model_path,
            compress=3,
        )
        model_sha256 = file_sha256(model_path)
        final_comparison = [item for item in comparison if item["foldKey"] == "FINAL"]
        _write_json(
            run_directory / "model-card.json",
            {
                "baselineArtifactRunId": baseline.run_id,
                "candidateFamily": experiment.model_family,
                "experimentHash": experiment.experiment_hash,
                "featureSet": experiment.selected_feature_set,
                "finalHoldoutComparison": final_comparison,
                "implementation": experiment.implementation,
                "limitations": [
                    "Porto trip-start demand proxy is not GoRide request demand",
                    "No calibrated prediction interval",
                    "Training uses deterministic weighted stratified sampling; evaluation uses the full population",
                ],
                "modelSha256": model_sha256,
                "modelVersion": model_version,
                "predictionInterval": {"enabled": False, "reason": experiment.prediction_interval_reason},
                "selectedConfiguration": selected[experiment.selected_feature_set].to_dict(),
                "selectionData": "D1-D4 only; FINAL opened after configuration lock",
                "status": "VALIDATED_CANDIDATE",
            },
        )
        candidate_population = {
            (item["foldKey"], int(item["horizonMinutes"])): int(item["sampleCount"])
            for item in candidate_metrics
            if item["featureSet"] == experiment.selected_feature_set and item["sliceType"] == "ALL"
        }
        baseline_population = baseline.population_counts()
        mismatches = {
            f"{key[0]}:{key[1]}": {"baseline": baseline_population.get(key), "candidate": candidate_population.get(key)}
            for key in sorted(set(baseline_population) | set(candidate_population))
            if baseline_population.get(key) != candidate_population.get(key)
        }
        quality_results = (
            QualityResult("MODEL_DEVELOPMENT_ONLY_SELECTION", "FAIL", "PASS", len(search_metrics), 0, details={"finalHoldoutUsed": False}),
            QualityResult("MODEL_BASELINE_POPULATION_MATCH", "FAIL", "PASS" if not mismatches else "FAIL", len(baseline_population), len(mismatches), details={"mismatches": mismatches}),
            QualityResult(
                "MODEL_NONNEGATIVE_PREDICTIONS",
                "FAIL",
                "PASS" if negative_predictions == 0 else "FAIL",
                raw_rows,
                negative_predictions,
                metric_value=minimum_prediction,
            ),
            QualityResult("MODEL_RAW_PREDICTION_CHECKSUM", "FAIL", "PASS", len(raw_files), 0, details={"sha256": raw_sha256}),
        )
        quality_status = "FAIL" if any(item.result_status == "FAIL" for item in quality_results) else "PASS"
        _write_json(run_directory / "training-quality.json", {"overallStatus": quality_status, "results": [item.to_dict() for item in quality_results]})
        total_seconds = time.perf_counter() - total_started
        _write_json(
            run_directory / "system-metrics.json",
            {
                "countTrainingPopulationSeconds": count_seconds,
                "evaluationAndSerializationSeconds": evaluation_seconds,
                "machine": platform.machine(),
                "modelSearchSeconds": search_seconds,
                "operatingSystem": platform.platform(),
                "peakWorkingSetBytes": _peak_working_set_bytes(),
                "python": platform.python_version(),
                "rawPredictionBytes": sum(int(item["bytes"]) for item in raw_files),
                "scikitLearn": metadata.version("scikit-learn"),
                "numpy": metadata.version("numpy"),
                "totalSeconds": total_seconds,
                "trainingSampleEvidence": evaluation_samples,
            },
        )
        _write_json(
            run_directory / "training-manifest.json",
            {
                "baselineArtifactRunId": baseline.run_id,
                "experimentHash": experiment.experiment_hash,
                "featureArtifactSha256": artifact.sha256,
                "featureSetVersion": artifact.feature_set_version,
                "cellSizeMeters": artifact.cell_size_meters,
                "metricGroupCount": len(candidate_metrics),
                "modelSha256": model_sha256,
                "modelVersion": model_version,
                "qualityStatus": quality_status,
                "rawPredictionRows": raw_rows,
                "rawPredictionSha256": raw_sha256,
                "sourceArtifactRunId": artifact.source_run_id,
            },
        )
        repository.save_quality(db_run_id, quality_results)
        if quality_status == "FAIL":
            raise DataQualityError([item.rule_code for item in quality_results if item.result_status == "FAIL"], identity.run_id)
        repository.succeed(db_run_id, rows_read=artifact.row_count, rows_written=raw_rows)
        terminal = True
        _write_checksums(run_directory)
        return TrainingOutcome(
            artifact_run_id=identity.run_id,
            processing_run_id=db_run_id,
            source_artifact_run_id=artifact.source_run_id,
            baseline_artifact_run_id=baseline.run_id,
            experiment_hash=experiment.experiment_hash,
            model_version=model_version,
            model_sha256=model_sha256,
            raw_prediction_rows=raw_rows,
            raw_prediction_sha256=raw_sha256,
            metric_group_count=len(candidate_metrics),
            quality_status=quality_status,
            attempt_no=attempt_no,
            run_directory=run_directory,
        )
    except Exception as error:
        if started and not terminal and repository is not None:
            code = error.code if isinstance(error, AnalyticsError) else "MODEL_TRAINING_FAILED"
            try:
                repository.fail(db_run_id, rows_read=artifact.row_count, rows_written=raw_rows, error_code=code, error_message=str(error))
            except Exception:
                pass
        raise
    finally:
        if connection is not None:
            connection.close()
