from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Mapping
from uuid import UUID

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
from ..hashing import canonical_mapping_sha256, file_sha256
from ..quality import QualityResult
from ..runs import (
    RunIdentity,
    allocate_run_directory,
    build_run_identity,
    write_run_manifest,
)
from ..features.persistence import (
    ResumableFeatureArtifact,
    load_resumable_feature_artifact,
)
from .baselines import (
    DemandCube,
    fit_historical_mean,
    seasonal_naive_prediction,
)
from .folds import EvaluationFold, build_rolling_origin_folds
from .metrics import (
    MetricAccumulator,
    demand_quantile_slice,
    demand_quantile_thresholds,
    time_of_day_slice,
)


_COMMIT_HASH = re.compile(r"^(?:[0-9a-f]{40}|[0-9a-f]{64})$")
_MODELS = ("HISTORICAL_MEAN", "SEASONAL_NAIVE")


@dataclass(frozen=True)
class EvaluationOutcome:
    artifact_run_id: str
    processing_run_id: UUID
    source_artifact_run_id: str
    feature_set_version: str
    fold_count: int
    metric_group_count: int
    raw_prediction_rows: int
    raw_prediction_sha256: str
    quality_status: str
    attempt_no: int
    run_directory: Path

    def to_dict(self) -> dict[str, object]:
        return {
            "artifactRunId": self.artifact_run_id,
            "attemptNo": self.attempt_no,
            "evaluationArtifact": {
                "featureSetVersion": self.feature_set_version,
                "folds": self.fold_count,
                "metricGroups": self.metric_group_count,
                "rawPredictionRows": self.raw_prediction_rows,
                "rawPredictionSha256": self.raw_prediction_sha256,
                "sourceArtifactRunId": self.source_artifact_run_id,
            },
            "processingRunId": str(self.processing_run_id),
            "qualityStatus": self.quality_status,
        }


def _write_json(path: Path, value: Any) -> None:
    temporary = path.with_name(f".{path.name}.tmp")
    temporary.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    os.replace(temporary, path)


def _write_checksums(directory: Path) -> None:
    files = sorted(
        item
        for item in directory.iterdir()
        if item.is_file()
        and not item.name.startswith(".")
        and item.name != "checksums.sha256"
    )
    content = "".join(f"{file_sha256(item)}  {item.name}\n" for item in files)
    temporary = directory / ".checksums.sha256.tmp"
    temporary.write_text(content, encoding="utf-8")
    os.replace(temporary, directory / "checksums.sha256")


def _analytics_root(config: ProcessingConfig, environment: Mapping[str, str]) -> Path:
    variable = config.dataset.root_env or "GORIDE_ANALYTICS_DATA_ROOT"
    raw = environment.get(variable, "").strip()
    if not raw:
        raise ExtractionError(
            "EVALUATION_ARTIFACT_ROOT_MISSING",
            f"Environment variable {variable} is required",
            {"environmentVariable": variable},
        )
    root = Path(raw).expanduser().resolve()
    if not root.is_dir():
        raise ExtractionError(
            "EVALUATION_ARTIFACT_ROOT_INVALID",
            "Analytics data root does not exist or is not a directory",
        )
    return root


def _load_json(path: Path, code: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ExtractionError(code, f"Could not read {path.name}") from error
    if not isinstance(value, dict):
        raise ExtractionError(code, f"{path.name} must contain a JSON object")
    return value


def _parse_utc(value: object, field: str) -> datetime:
    try:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError as error:
        raise ExtractionError(
            "EVALUATION_FEATURE_MANIFEST_INVALID",
            f"{field} must be an ISO-8601 timestamp",
        ) from error
    if parsed.tzinfo is None:
        raise ExtractionError(
            "EVALUATION_FEATURE_MANIFEST_INVALID",
            f"{field} must include a UTC offset",
        )
    return parsed.astimezone(timezone.utc)


def _load_cube(
    config: ProcessingConfig,
    artifact: ResumableFeatureArtifact,
) -> DemandCube:
    manifest = _load_json(
        artifact.source_run_directory / "feature-manifest.json",
        "EVALUATION_FEATURE_MANIFEST_INVALID",
    )
    eligibility = _load_json(
        artifact.source_run_directory / "cell-eligibility.json",
        "EVALUATION_CELL_ELIGIBILITY_INVALID",
    )
    raw_cells = eligibility.get("selectedCellIds")
    if (
        not isinstance(raw_cells, list)
        or not raw_cells
        or any(not isinstance(item, str) or not item for item in raw_cells)
        or len(set(raw_cells)) != len(raw_cells)
    ):
        raise ExtractionError(
            "EVALUATION_CELL_ELIGIBILITY_INVALID",
            "Cell eligibility must contain unique selectedCellIds",
        )
    cube = DemandCube(
        cells=tuple(sorted(raw_cells)),
        from_utc=_parse_utc(manifest.get("fromUtc"), "fromUtc"),
        to_utc=_parse_utc(manifest.get("sourceCutoffUtc"), "sourceCutoffUtc"),
        bucket_minutes=config.temporal.bucket_minutes,
        source_timezone=config.temporal.source_timezone,
    )
    try:
        import pyarrow.parquet as pq
    except ImportError as error:
        raise ExtractionError(
            "EVALUATION_RUNTIME_MISSING",
            "pyarrow is required to evaluate feature artifacts",
        ) from error
    base_horizon = min(config.temporal.forecast_horizons_minutes)
    observed = 0
    for partition in artifact.partitions:
        parquet = pq.ParquetFile(partition.path)
        for batch in parquet.iter_batches(
            batch_size=100_000,
            columns=[
                "cell_id",
                "target_bucket_start_utc",
                "horizon_minutes",
                "target_trip_requests",
            ],
        ):
            cells = batch.column(0).to_pylist()
            targets = batch.column(1).to_pylist()
            horizons = batch.column(2).to_pylist()
            actuals = batch.column(3).to_pylist()
            for cell_id, target, horizon, actual in zip(
                cells, targets, horizons, actuals
            ):
                if int(horizon) != base_horizon:
                    continue
                cube.set(str(cell_id), target, int(actual))
                observed += 1
    if observed == 0:
        raise ExtractionError(
            "EVALUATION_DEMAND_CUBE_EMPTY",
            "No base-horizon targets were available for evaluation",
        )
    return cube


class _RawPredictionWriters:
    def __init__(
        self,
        root: Path,
        *,
        compression: str,
        enabled: bool,
    ) -> None:
        self.root = root
        self.compression = compression
        self.enabled = enabled
        self._writers: dict[tuple[str, str], Any] = {}
        self._paths: dict[tuple[str, str], Path] = {}
        self._batches: dict[tuple[str, str], list[dict[str, Any]]] = {}
        self._row_counts: dict[tuple[str, str], int] = {}
        self.row_count = 0
        self._schema = None

    def _open(self, key: tuple[str, str]) -> Any:
        import pyarrow as pa
        import pyarrow.parquet as pq

        if self._schema is None:
            self._schema = pa.schema(
                [
                    ("evaluation_run_id", pa.string()),
                    ("source_feature_run_id", pa.string()),
                    ("feature_set_version", pa.string()),
                    ("model_name", pa.string()),
                    ("fold_key", pa.string()),
                    ("split_role", pa.string()),
                    ("cell_id", pa.string()),
                    ("inference_cutoff_utc", pa.timestamp("us", tz="UTC")),
                    ("target_bucket_start_utc", pa.timestamp("us", tz="UTC")),
                    ("horizon_minutes", pa.int16()),
                    ("actual_demand", pa.int64()),
                    ("predicted_demand", pa.float64()),
                    ("prediction_source", pa.string()),
                    ("demand_volume_slice", pa.string()),
                    ("time_of_day_slice", pa.string()),
                    ("absolute_error", pa.float64()),
                    ("squared_error", pa.float64()),
                ]
            )
        fold, model = key
        directory = self.root / f"fold={fold}" / f"model={model.lower()}"
        directory.mkdir(parents=True, exist_ok=False)
        path = directory / "part-00000.parquet"
        self._paths[key] = path
        self._batches[key] = []
        self._row_counts[key] = 0
        writer = pq.ParquetWriter(
            path,
            self._schema,
            compression=self.compression,
            use_dictionary=True,
            write_statistics=True,
        )
        self._writers[key] = writer
        return writer

    def write(self, key: tuple[str, str], row: dict[str, Any]) -> None:
        self.row_count += 1
        if not self.enabled:
            return
        writer = self._writers.get(key) or self._open(key)
        self._row_counts[key] += 1
        batch = self._batches[key]
        batch.append(row)
        if len(batch) >= 25_000:
            import pyarrow as pa

            writer.write_table(pa.Table.from_pylist(batch, schema=self._schema))
            batch.clear()

    def close(self) -> tuple[dict[str, object], ...]:
        if not self.enabled:
            return ()
        import pyarrow as pa

        for key, writer in self._writers.items():
            batch = self._batches[key]
            if batch:
                writer.write_table(pa.Table.from_pylist(batch, schema=self._schema))
                batch.clear()
            writer.close()
        self._writers.clear()
        return tuple(
            {
                "file": path.relative_to(self.root.parent).as_posix(),
                "foldKey": key[0],
                "modelName": key[1],
                "rows": self._row_counts[key],
                "bytes": path.stat().st_size,
                "sha256": file_sha256(path),
            }
            for key, path in sorted(self._paths.items())
        )


def _fold_for_target(
    folds: tuple[EvaluationFold, ...], target: datetime
) -> EvaluationFold | None:
    for fold in folds:
        if fold.evaluate_from_utc <= target < fold.evaluate_to_utc:
            return fold
    return None


def _model_cards(config: ProcessingConfig) -> tuple[dict[str, object], ...]:
    common = {
        "bucketMinutes": config.temporal.bucket_minutes,
        "forecastHorizonsMinutes": list(config.temporal.forecast_horizons_minutes),
        "intendedUse": "Academic benchmark for aggregate cell-level demand forecasting",
        "limitations": [
            "Not a dispatch decision model",
            "No prediction interval",
            "Evaluated only on cells frozen by training-demand coverage",
        ],
        "status": "BASELINE",
    }
    return (
        {
            **common,
            "modelName": "HISTORICAL_MEAN",
            "semantics": "Mean demand for the same cell and local weekly 15-minute slot using training history only",
            "fallbackOrder": ["CELL_WEEKLY_SLOT", "CELL_MEAN", "GLOBAL_MEAN", "ZERO"],
        },
        {
            **common,
            "modelName": "SEASONAL_NAIVE",
            "semantics": "Most recent available demand from the same cell and local weekly slot, with DST-aware bucket alignment",
            "fallbackOrder": ["WEEK_LAG_LOCAL", "HISTORICAL_MEAN"],
        },
    )


def _evaluate_rows(
    config: ProcessingConfig,
    artifact: ResumableFeatureArtifact,
    cube: DemandCube,
    folds: tuple[EvaluationFold, ...],
    identity: RunIdentity,
    prediction_root: Path,
) -> tuple[list[dict[str, object]], tuple[dict[str, object], ...], int, dict[str, int]]:
    try:
        import pyarrow.parquet as pq
    except ImportError as error:
        raise ExtractionError(
            "EVALUATION_RUNTIME_MISSING",
            "pyarrow is required to evaluate feature artifacts",
        ) from error
    profiles = {
        fold.key: fit_historical_mean(
            cube,
            train_from_utc=fold.train_from_utc,
            train_to_utc=fold.train_to_utc,
        )
        for fold in folds
    }
    thresholds = {
        fold.key: demand_quantile_thresholds(
            cube.iter_interval(fold.train_from_utc, fold.train_to_utc)
        )
        for fold in folds
    }
    metrics: dict[tuple[str, str, int, str, str], MetricAccumulator] = {}
    population: dict[tuple[str, int, str], int] = {}
    prediction_sources: dict[str, int] = {}
    writers = _RawPredictionWriters(
        prediction_root,
        compression=config.artifacts.compression,
        enabled=config.artifacts.write_raw_predictions,
    )
    closed = False
    try:
        for partition in artifact.partitions:
            parquet = pq.ParquetFile(partition.path)
            for batch in parquet.iter_batches(
                batch_size=50_000,
                columns=[
                    "cell_id",
                    "inference_cutoff_utc",
                    "target_bucket_start_utc",
                    "horizon_minutes",
                    "target_trip_requests",
                ],
            ):
                for cell_id, inference, target, horizon, actual in zip(
                    batch.column(0).to_pylist(),
                    batch.column(1).to_pylist(),
                    batch.column(2).to_pylist(),
                    batch.column(3).to_pylist(),
                    batch.column(4).to_pylist(),
                ):
                    fold = _fold_for_target(folds, target)
                    if fold is None:
                        continue
                    horizon = int(horizon)
                    actual = int(actual)
                    cell_id = str(cell_id)
                    cell_index = cube.cell_indexes[cell_id]
                    bucket_index = cube.bucket_index(target)
                    profile = profiles[fold.key]
                    historical = profile.predict(
                        cell_index, cube.weekly_slots[bucket_index]
                    )
                    seasonal = seasonal_naive_prediction(
                        cube,
                        profile,
                        cell_index=cell_index,
                        target_bucket_index=bucket_index,
                    )
                    volume_slice = demand_quantile_slice(actual, thresholds[fold.key])
                    tod_slice = time_of_day_slice(cube.local_hours[bucket_index])
                    for model, (prediction, source) in zip(
                        _MODELS, (historical, seasonal)
                    ):
                        error = prediction - actual
                        writers.write(
                            (fold.key, model),
                            {
                                "evaluation_run_id": identity.run_id,
                                "source_feature_run_id": artifact.source_run_id,
                                "feature_set_version": artifact.feature_set_version,
                                "model_name": model,
                                "fold_key": fold.key,
                                "split_role": fold.split_role,
                                "cell_id": cell_id,
                                "inference_cutoff_utc": inference,
                                "target_bucket_start_utc": target,
                                "horizon_minutes": horizon,
                                "actual_demand": actual,
                                "predicted_demand": float(prediction),
                                "prediction_source": source,
                                "demand_volume_slice": volume_slice,
                                "time_of_day_slice": tod_slice,
                                "absolute_error": abs(error),
                                "squared_error": error * error,
                            },
                        )
                        population_key = (fold.key, horizon, model)
                        population[population_key] = population.get(population_key, 0) + 1
                        prediction_sources[source] = prediction_sources.get(source, 0) + 1
                        for slice_type, slice_key in (
                            ("ALL", "ALL"),
                            ("DEMAND_QUANTILE", volume_slice),
                            ("TIME_OF_DAY", tod_slice),
                        ):
                            key = (model, fold.key, horizon, slice_type, slice_key)
                            metrics.setdefault(key, MetricAccumulator()).update(
                                actual, prediction
                            )
        files = writers.close()
        closed = True
    finally:
        if not closed:
            writers.close()
    records: list[dict[str, object]] = []
    for (model, fold, horizon, slice_type, slice_key), accumulator in sorted(
        metrics.items()
    ):
        records.append(
            {
                "modelName": model,
                "foldKey": fold,
                "horizonMinutes": horizon,
                "sliceType": slice_type,
                "sliceKey": slice_key,
                **accumulator.result(),
            }
        )
    metadata = {
        "quantileThresholdsByFold": {
            key: list(value) for key, value in thresholds.items()
        },
        "predictionSources": dict(sorted(prediction_sources.items())),
    }
    return records, files, writers.row_count, metadata


def run_evaluation(
    config: ProcessingConfig,
    *,
    feature_run: str | Path,
    environment: Mapping[str, str] | None = None,
    identity: RunIdentity | None = None,
    connection_factory: Callable[..., Any] = connect_database,
    repository_factory: Callable[[Any], ProcessingRunRepository] = (
        PostgresProcessingRunRepository
    ),
) -> EvaluationOutcome:
    environment = environment or os.environ
    if not config.artifacts.write_raw_predictions:
        raise ExtractionError(
            "EVALUATION_RAW_PREDICTIONS_REQUIRED",
            "Phase 5 requires artifacts.write_raw_predictions for traceability",
        )
    root = _analytics_root(config, environment)
    artifact = load_resumable_feature_artifact(
        config,
        root=root,
        feature_run=feature_run,
    )
    folds = build_rolling_origin_folds(config)
    identity = identity or build_run_identity(config, "evaluation")
    if not _COMMIT_HASH.fullmatch(identity.git_commit):
        raise ExtractionError(
            "CODE_COMMIT_UNAVAILABLE",
            "A full Git commit hash is required for persistent evaluation evidence",
        )
    run_directory = allocate_run_directory(root, identity)
    write_run_manifest(run_directory, identity)
    folds_payload = {
        "strategy": config.evaluation.strategy,
        "timezone": config.temporal.source_timezone,
        "boundaryPolicy": "local calendar boundaries converted immutably to UTC",
        "shuffle": False,
        "folds": [fold.to_dict() for fold in folds],
    }
    _write_json(run_directory / "folds.json", folds_payload)
    for card in _model_cards(config):
        _write_json(
            run_directory / f"model-card-{str(card['modelName']).lower()}.json",
            card,
        )
    connection = None
    repository = None
    db_run_id = processing_run_id(identity)
    started = False
    terminal = False
    raw_rows = 0
    try:
        settings = DatabaseSettings.from_environment(environment)
        connection = connection_factory(settings, read_only=False)
        metadata = verify_database(connection, require_forecast_schema=True)
        repository = repository_factory(connection)
        source_manifest_sha256 = file_sha256(
            artifact.source_run_directory / "feature-manifest.json"
        )
        attempt_no = repository.start(
            run_id=db_run_id,
            run_type="EVALUATION",
            identity=identity,
            source_profile=config.profile.name,
            dataset_version=config.dataset.version,
            source_cutoff=folds[-1].evaluate_to_utc,
            input_manifest={
                "database": metadata.to_dict(),
                "featureArtifactSha256": artifact.sha256,
                "featureManifestSha256": source_manifest_sha256,
                "featureSetVersion": artifact.feature_set_version,
                "foldKeys": [fold.key for fold in folds],
                "models": list(_MODELS),
                "sourceArtifactRunId": artifact.source_run_id,
            },
        )
        started = True
        cube = _load_cube(config, artifact)
        prediction_root = run_directory / "raw-predictions"
        prediction_root.mkdir()
        metrics, raw_files, raw_rows, evaluation_metadata = _evaluate_rows(
            config,
            artifact,
            cube,
            folds,
            identity,
            prediction_root,
        )
        if raw_rows == 0:
            raise ExtractionError(
                "EVALUATION_POPULATION_EMPTY",
                "No feature rows fall inside the frozen evaluation folds",
            )
        if raw_rows > config.artifacts.maximum_rows_per_run:
            raise ExtractionError(
                "EVALUATION_COST_GUARD_EXCEEDED",
                "Raw prediction rows exceed artifacts.maximum_rows_per_run",
                {
                    "maximumRows": config.artifacts.maximum_rows_per_run,
                    "rawPredictionRows": raw_rows,
                },
            )
        raw_manifest = {
            "files": list(raw_files),
            "rowCount": raw_rows,
        }
        raw_sha256 = canonical_mapping_sha256(raw_manifest)
        raw_manifest["sha256"] = raw_sha256
        _write_json(run_directory / "raw-predictions-manifest.json", raw_manifest)
        metrics_payload = {
            "evaluationRunId": identity.run_id,
            "metricDefinitions": {
                "MAE": "sum(abs(prediction-actual))/n",
                "RMSE": "sqrt(sum((prediction-actual)^2)/n)",
                "WAPE": "sum(abs(prediction-actual))/sum(actual); null when denominator is zero",
            },
            "records": metrics,
            **evaluation_metadata,
        }
        _write_json(run_directory / "evaluation-metrics.json", metrics_payload)
        counts: dict[tuple[str, int], dict[str, int]] = {}
        for record in metrics:
            if record["sliceType"] != "ALL":
                continue
            key = (str(record["foldKey"]), int(record["horizonMinutes"]))
            counts.setdefault(key, {})[str(record["modelName"])] = int(
                record["sampleCount"]
            )
        expected_populations = {
            (fold.key, horizon)
            for fold in folds
            for horizon in config.temporal.forecast_horizons_minutes
        }
        mismatches = {}
        for key in sorted(expected_populations):
            values = counts.get(key, {})
            if (
                not values
                or len(set(values.values())) != 1
                or set(values) != set(_MODELS)
            ):
                mismatches[f"{key[0]}:{key[1]}"] = values
        quality_results = (
            QualityResult(
                "EVAL_CHRONOLOGICAL_FOLDS",
                "FAIL",
                "PASS",
                len(folds),
                0,
                details={"shuffle": False},
            ),
            QualityResult(
                "EVAL_MODEL_POPULATION_MATCH",
                "FAIL",
                "PASS" if not mismatches else "FAIL",
                len(expected_populations),
                len(mismatches),
                details={"mismatches": mismatches},
            ),
            QualityResult(
                "EVAL_RAW_PREDICTIONS_CHECKSUM",
                "FAIL",
                "PASS",
                len(raw_files),
                0,
                details={"sha256": raw_sha256},
            ),
        )
        quality_status = (
            "FAIL"
            if any(item.result_status == "FAIL" for item in quality_results)
            else "PASS"
        )
        _write_json(
            run_directory / "evaluation-quality.json",
            {
                "overallStatus": quality_status,
                "results": [item.to_dict() for item in quality_results],
            },
        )
        _write_json(
            run_directory / "evaluation-manifest.json",
            {
                "evaluationRunId": identity.run_id,
                "featureArtifactSha256": artifact.sha256,
                "featureSetVersion": artifact.feature_set_version,
                "foldCount": len(folds),
                "metricGroupCount": len(metrics),
                "models": list(_MODELS),
                "rawPredictionRows": raw_rows,
                "rawPredictionSha256": raw_sha256,
                "sourceArtifactRunId": artifact.source_run_id,
                "qualityStatus": quality_status,
            },
        )
        repository.save_quality(db_run_id, quality_results)
        if quality_status == "FAIL":
            raise DataQualityError(
                [
                    item.rule_code
                    for item in quality_results
                    if item.result_status == "FAIL"
                ],
                identity.run_id,
            )
        repository.succeed(
            db_run_id,
            rows_read=artifact.row_count,
            rows_written=raw_rows,
        )
        terminal = True
        _write_checksums(run_directory)
        return EvaluationOutcome(
            artifact_run_id=identity.run_id,
            processing_run_id=db_run_id,
            source_artifact_run_id=artifact.source_run_id,
            feature_set_version=artifact.feature_set_version,
            fold_count=len(folds),
            metric_group_count=len(metrics),
            raw_prediction_rows=raw_rows,
            raw_prediction_sha256=raw_sha256,
            quality_status=quality_status,
            attempt_no=attempt_no,
            run_directory=run_directory,
        )
    except Exception as error:
        if started and not terminal and repository is not None:
            code = error.code if isinstance(error, AnalyticsError) else "EVALUATION_FAILED"
            try:
                repository.fail(
                    db_run_id,
                    rows_read=artifact.row_count,
                    rows_written=raw_rows,
                    error_code=code,
                    error_message=str(error),
                )
            except Exception:
                pass
        raise
    finally:
        if connection is not None:
            connection.close()
