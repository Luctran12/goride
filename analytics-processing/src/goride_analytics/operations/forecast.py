from __future__ import annotations

import json
import math
import os
import re
import shutil
import time
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Callable, Mapping, Sequence

from ..config import ProcessingConfig
from ..database import (
    DatabaseSettings,
    PostgresProcessingRunRepository,
    ProcessingRunRepository,
    connect_database,
    processing_run_id,
    verify_database,
)
from ..errors import AnalyticsError, DataQualityError, DatabaseError, ExtractionError
from ..hashing import file_sha256
from ..models.experiment import ALLOWED_FEATURES
from ..quality import QualityResult
from ..runs import RunIdentity, allocate_run_directory, build_run_identity, write_run_manifest
from .artifact import analytics_root, load_registered_artifact
from .config import OperationsConfig, load_operations_config
from .registry import APPROVAL_SCOPE


FORECAST_RUN_NAMESPACE = uuid.UUID("a5977f26-228e-42de-a953-4fe7f8423123")
_COMMIT_HASH = re.compile(r"^(?:[0-9a-f]{40}|[0-9a-f]{64})$")


@dataclass(frozen=True)
class RegisteredModel:
    model_version_id: uuid.UUID
    model_name: str
    model_version: str
    lifecycle_status: str
    source_profile: str
    dataset_version: str
    demand_event_semantics: str
    feature_set_version: str
    grid_version: str
    cell_size_meters: int
    bucket_minutes: int
    training_cutoff_utc: datetime
    artifact_uri: str
    artifact_sha256: str
    approval_scope: str


@dataclass(frozen=True)
class ForecastFeature:
    cell_id: str
    grid_x: int
    grid_y: int
    inference_cutoff_utc: datetime
    target_bucket_start_utc: datetime
    horizon_minutes: int
    values: tuple[float, ...]


@dataclass(frozen=True)
class ForecastPrediction:
    feature: ForecastFeature
    predicted_demand: float


@dataclass(frozen=True)
class ForecastOutcome:
    artifact_run_id: str
    processing_run_id: uuid.UUID
    forecast_run_id: uuid.UUID
    model_version_id: uuid.UUID
    model_version: str
    inference_cutoff_utc: datetime
    run_purpose: str
    forecast_rows: int
    quality_status: str
    idempotent: bool
    attempt_no: int
    run_directory: Path | None

    def to_dict(self) -> dict[str, object]:
        return {
            "artifactRunId": self.artifact_run_id,
            "attemptNo": self.attempt_no,
            "forecastRun": {
                "forecastRows": self.forecast_rows,
                "forecastRunId": str(self.forecast_run_id),
                "idempotent": self.idempotent,
                "inferenceCutoffUtc": self.inference_cutoff_utc.isoformat().replace(
                    "+00:00", "Z"
                ),
                "modelVersion": self.model_version,
                "modelVersionId": str(self.model_version_id),
                "runPurpose": self.run_purpose,
            },
            "processingRunId": str(self.processing_run_id),
            "qualityStatus": self.quality_status,
        }


def _write_json(path: Path, value: object) -> None:
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


def parse_inference_cutoff(value: str, bucket_minutes: int) -> datetime:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ExtractionError(
            "FORECAST_CUTOFF_INVALID",
            "Inference cutoff must be an ISO-8601 timestamp",
        ) from error
    if parsed.tzinfo is None or parsed.utcoffset() != timedelta(0):
        raise ExtractionError(
            "FORECAST_CUTOFF_INVALID",
            "Inference cutoff must be explicitly UTC",
        )
    if int(parsed.timestamp()) % (bucket_minutes * 60):
        raise ExtractionError(
            "FORECAST_CUTOFF_INVALID",
            "Inference cutoff must align to the configured bucket",
        )
    return parsed.astimezone(timezone.utc)


class PostgresForecastRepository:
    def __init__(self, connection: Any) -> None:
        self._connection = connection

    def get_model(self, model_version: str) -> RegisteredModel:
        with self._connection.transaction():
            with self._connection.cursor() as cursor:
                cursor.execute(
                """
                SELECT model_version_id, model_name, model_version,
                       lifecycle_status, source_profile, dataset_version,
                       demand_event_semantics, feature_set_version, grid_version,
                       cell_size_meters, bucket_minutes, training_cutoff_utc,
                       artifact_uri, artifact_sha256,
                       training_manifest->>'approvalScope'
                FROM analytics.model_versions
                WHERE model_version = %s
                """,
                    (model_version,),
                )
                rows = cursor.fetchall()
        if len(rows) != 1:
            raise DatabaseError(
                "MODEL_VERSION_NOT_FOUND",
                "Exactly one registered model version is required",
                {"modelVersion": model_version, "matches": len(rows)},
            )
        return RegisteredModel(*rows[0])

    def find_existing(
        self,
        model_version_id: uuid.UUID,
        cutoff: datetime,
        config_hash: str,
        purpose: str,
    ) -> ForecastOutcome | None:
        with self._connection.transaction():
            with self._connection.cursor() as cursor:
                cursor.execute(
                """
                SELECT p.artifact_run_id, p.run_id, f.forecast_run_id,
                       f.model_version_id, m.model_version,
                       f.inference_cutoff_utc, f.run_purpose,
                       COUNT(d.demand_forecast_id), p.attempt_no, f.status
                FROM analytics.forecast_runs f
                JOIN analytics.processing_runs p
                  ON p.run_id = f.processing_run_id
                JOIN analytics.model_versions m
                  ON m.model_version_id = f.model_version_id
                LEFT JOIN analytics.demand_forecasts d
                  ON d.forecast_run_id = f.forecast_run_id
                WHERE f.model_version_id = %s
                  AND f.inference_cutoff_utc = %s
                  AND f.config_hash = %s
                  AND f.run_purpose = %s
                GROUP BY p.artifact_run_id, p.run_id, f.forecast_run_id,
                         f.model_version_id, m.model_version,
                         f.inference_cutoff_utc, f.run_purpose,
                         p.attempt_no, f.status
                """,
                    (model_version_id, cutoff, config_hash, purpose),
                )
                row = cursor.fetchone()
        if row is None:
            return None
        if row[9] in {"PUBLISHED", "SUCCEEDED"}:
            return ForecastOutcome(
                artifact_run_id=row[0],
                processing_run_id=row[1],
                forecast_run_id=row[2],
                model_version_id=row[3],
                model_version=row[4],
                inference_cutoff_utc=row[5],
                run_purpose=row[6],
                forecast_rows=int(row[7]),
                quality_status="PASS",
                idempotent=True,
                attempt_no=int(row[8]),
                run_directory=None,
            )
        if row[9] not in {"FAILED", "CANCELLED"} or int(row[7]) != 0:
            raise DatabaseError(
                "FORECAST_RUN_CONFLICT",
                "Equivalent forecast run is not safely reusable",
                {"status": row[9], "forecastRows": int(row[7])},
            )
        with self._connection.transaction():
            with self._connection.cursor() as cursor:
                cursor.execute(
                    "DELETE FROM analytics.forecast_runs WHERE forecast_run_id = %s",
                    (row[2],),
                )
        return None

    def load_features(
        self,
        model: RegisteredModel,
        cutoff: datetime,
        horizons: Sequence[int],
        feature_names: Sequence[str],
    ) -> list[ForecastFeature]:
        if not feature_names or any(name not in ALLOWED_FEATURES for name in feature_names):
            raise ExtractionError(
                "FORECAST_FEATURE_CONTRACT_INVALID",
                "Registered model feature names are unsupported",
            )
        selected = ", ".join(feature_names)
        with self._connection.transaction():
            with self._connection.cursor() as cursor:
                cursor.execute(
                f"""
                SELECT cell_id, grid_x, grid_y, inference_cutoff_utc,
                       target_bucket_start_utc, horizon_minutes, {selected}
                FROM analytics.demand_features
                WHERE feature_set_version = %s
                  AND source_profile = %s
                  AND dataset_version = %s
                  AND grid_version = %s
                  AND cell_size_meters = %s
                  AND inference_cutoff_utc = %s
                  AND horizon_minutes = ANY(%s)
                ORDER BY horizon_minutes, cell_id
                """,
                    (
                        model.feature_set_version,
                        model.source_profile,
                        model.dataset_version,
                        model.grid_version,
                        model.cell_size_meters,
                        cutoff,
                        list(horizons),
                    ),
                )
                rows = cursor.fetchall()
        features: list[ForecastFeature] = []
        for row in rows:
            values = tuple(float("nan") if value is None else float(value) for value in row[6:])
            features.append(
                ForecastFeature(
                    cell_id=str(row[0]),
                    grid_x=int(row[1]),
                    grid_y=int(row[2]),
                    inference_cutoff_utc=row[3],
                    target_bucket_start_utc=row[4],
                    horizon_minutes=int(row[5]),
                    values=values,
                )
            )
        return features

    def start_forecast(
        self,
        *,
        forecast_run_id: uuid.UUID,
        processing_run_id_value: uuid.UUID,
        model: RegisteredModel,
        purpose: str,
        cutoff: datetime,
        config_hash: str,
        generated_at: datetime,
    ) -> None:
        try:
            with self._connection.transaction():
                with self._connection.cursor() as cursor:
                    cursor.execute(
                        """
                        INSERT INTO analytics.forecast_runs (
                            forecast_run_id, processing_run_id, model_version_id,
                            run_purpose, status, source_profile, dataset_version,
                            demand_event_semantics, grid_version, cell_size_meters,
                            bucket_minutes, inference_cutoff_utc, config_hash,
                            created_at, started_at, generated_at_utc
                        ) VALUES (
                            %s, %s, %s, %s, 'RUNNING', %s, %s, %s, %s,
                            %s, %s, %s, %s, %s, %s, %s
                        )
                        """,
                        (
                            forecast_run_id,
                            processing_run_id_value,
                            model.model_version_id,
                            purpose,
                            model.source_profile,
                            model.dataset_version,
                            model.demand_event_semantics,
                            model.grid_version,
                            model.cell_size_meters,
                            model.bucket_minutes,
                            cutoff,
                            config_hash,
                            generated_at,
                            generated_at,
                            generated_at,
                        ),
                    )
        except Exception as error:
            raise DatabaseError(
                "FORECAST_RUN_START_FAILED",
                "Could not start forecast run",
            ) from error

    def publish(
        self,
        *,
        forecast_run_id: uuid.UUID,
        processing_run_id_value: uuid.UUID,
        model: RegisteredModel,
        predictions: Sequence[ForecastPrediction],
        generated_at: datetime,
        finished_at: datetime,
        quality_results: Sequence[QualityResult],
        processing_repository: ProcessingRunRepository,
        projected_srid: int,
        grid_origin_x_meters: float,
        grid_origin_y_meters: float,
    ) -> None:
        statement = """
            INSERT INTO analytics.demand_forecasts (
                forecast_run_id, model_version_id, cell_id, cell_geometry,
                cell_size_meters, generated_at_utc, inference_cutoff_utc,
                target_bucket_start_utc, horizon_minutes, predicted_demand
            ) VALUES (
                %s, %s, %s,
                ST_Transform(
                    ST_MakeEnvelope(%s, %s, %s, %s, %s), 4326
                )::geometry(Polygon, 4326),
                %s, %s, %s, %s, %s, %s
            )
        """
        try:
            with self._connection.transaction():
                with self._connection.cursor() as cursor:
                    values = []
                    size = model.cell_size_meters
                    for item in predictions:
                        x0 = grid_origin_x_meters + item.feature.grid_x * size
                        y0 = grid_origin_y_meters + item.feature.grid_y * size
                        values.append(
                            (
                                forecast_run_id,
                                model.model_version_id,
                                item.feature.cell_id,
                                x0,
                                y0,
                                x0 + size,
                                y0 + size,
                                projected_srid,
                                size,
                                generated_at,
                                item.feature.inference_cutoff_utc,
                                item.feature.target_bucket_start_utc,
                                item.feature.horizon_minutes,
                                item.predicted_demand,
                            )
                        )
                    cursor.executemany(statement, values)
                    cursor.execute(
                        """
                        UPDATE analytics.forecast_runs
                        SET status = CASE WHEN run_purpose = 'PUBLISHED'
                                          THEN 'PUBLISHED' ELSE 'SUCCEEDED' END,
                            finished_at = %s,
                            published_at = CASE WHEN run_purpose = 'PUBLISHED'
                                                THEN %s ELSE NULL END
                        WHERE forecast_run_id = %s AND status = 'RUNNING'
                        """,
                        (finished_at, finished_at, forecast_run_id),
                    )
                    if cursor.rowcount != 1:
                        raise DatabaseError(
                            "FORECAST_TERMINAL_STATE_FAILED",
                            "Forecast run was not in RUNNING state",
                        )
                processing_repository.save_quality(
                    processing_run_id_value,
                    quality_results,
                )
                processing_repository.succeed(
                    processing_run_id_value,
                    rows_read=len(predictions),
                    rows_written=len(predictions),
                )
        except DatabaseError:
            raise
        except Exception as error:
            raise DatabaseError(
                "FORECAST_PUBLICATION_FAILED",
                "Forecast publication failed and was rolled back",
            ) from error

    def fail_forecast(
        self,
        forecast_run_id: uuid.UUID,
        *,
        error_code: str,
        error_message: str,
        finished_at: datetime,
    ) -> None:
        with self._connection.transaction():
            with self._connection.cursor() as cursor:
                cursor.execute(
                    """
                    UPDATE analytics.forecast_runs
                    SET status = 'FAILED', finished_at = %s,
                        error_code = %s, error_message = %s
                    WHERE forecast_run_id = %s AND status = 'RUNNING'
                    """,
                    (finished_at, error_code, error_message[:2000], forecast_run_id),
                )


def _validate_model(
    config: ProcessingConfig,
    model: RegisteredModel,
    cutoff: datetime,
    purpose: str,
    now: datetime,
    operations: OperationsConfig,
) -> None:
    if (
        model.lifecycle_status != "APPROVED"
        or model.approval_scope != APPROVAL_SCOPE
    ):
        raise DatabaseError(
            "MODEL_NOT_APPROVED",
            "Only an explicitly approved research model may run inference",
            {"lifecycleStatus": model.lifecycle_status},
        )
    if (
        model.source_profile != config.profile.name
        or model.dataset_version != config.dataset.version
        or model.grid_version != config.spatial.grid_version
        or model.bucket_minutes != config.temporal.bucket_minutes
    ):
        raise DatabaseError(
            "MODEL_PROFILE_INCOMPATIBLE",
            "Registered model is incompatible with the active profile",
        )
    if purpose not in operations.allowed_purposes:
        raise ExtractionError(
            "FORECAST_PURPOSE_INVALID",
            "Forecast purpose is not allowed by the operations contract",
        )
    if cutoff < model.training_cutoff_utc:
        raise ExtractionError(
            "FORECAST_BEFORE_TRAINING_CUTOFF",
            "Inference before the training cutoff would leak future training data",
        )
    if cutoff > now:
        raise ExtractionError(
            "FORECAST_CUTOFF_IN_FUTURE",
            "Inference cutoff cannot be in the future",
        )
    if purpose == "PUBLISHED" and now - cutoff > timedelta(
        minutes=operations.stale_after_minutes
    ):
        raise ExtractionError(
            "FORECAST_INPUT_STALE",
            "Published inference exceeded the frozen freshness threshold",
            {"staleAfterMinutes": operations.stale_after_minutes},
        )


def _predict(
    bundle: Mapping[str, Any],
    features: Sequence[ForecastFeature],
    horizons: Sequence[int],
) -> list[ForecastPrediction]:
    try:
        import numpy as np
    except ImportError as error:
        raise ExtractionError(
            "MODEL_RUNTIME_MISSING",
            "NumPy is required for inference",
        ) from error
    models = bundle["horizonModels"]
    predictions: list[ForecastPrediction] = []
    for horizon in horizons:
        subset = [item for item in features if item.horizon_minutes == horizon]
        matrix = np.asarray([item.values for item in subset], dtype=np.float64)
        values = models[horizon].predict(matrix)
        for item, value in zip(subset, values, strict=True):
            prediction = max(0.0, float(value))
            if not math.isfinite(prediction):
                raise ExtractionError(
                    "FORECAST_NONFINITE_PREDICTION",
                    "Model emitted a non-finite prediction",
                )
            predictions.append(ForecastPrediction(item, prediction))
    return predictions


def _quality(
    features: Sequence[ForecastFeature],
    predictions: Sequence[ForecastPrediction],
    horizons: Sequence[int],
    cutoff: datetime,
) -> tuple[QualityResult, ...]:
    population = {
        horizon: {item.cell_id for item in features if item.horizon_minutes == horizon}
        for horizon in horizons
    }
    expected = population[horizons[0]] if horizons else set()
    population_breaches = sum(
        1 for horizon in horizons if not population[horizon] or population[horizon] != expected
    )
    identities = {
        (item.feature.cell_id, item.feature.horizon_minutes)
        for item in predictions
    }
    alignment_breaches = sum(
        1
        for item in features
        if item.inference_cutoff_utc != cutoff
        or item.target_bucket_start_utc
        != cutoff + timedelta(minutes=item.horizon_minutes)
    )
    duplicate_count = len(predictions) - len(identities)
    return (
        QualityResult(
            "FORECAST_MODEL_APPROVED",
            "FAIL",
            "PASS",
            1,
            0,
        ),
        QualityResult(
            "FORECAST_HORIZON_POPULATION",
            "FAIL",
            "PASS" if population_breaches == 0 else "FAIL",
            len(horizons),
            population_breaches,
            metric_value=float(len(expected)),
        ),
        QualityResult(
            "FORECAST_ALIGNMENT",
            "FAIL",
            "PASS" if alignment_breaches == 0 else "FAIL",
            len(features),
            alignment_breaches,
        ),
        QualityResult(
            "FORECAST_UNIQUENESS",
            "FAIL",
            "PASS" if duplicate_count == 0 else "FAIL",
            len(predictions),
            duplicate_count,
        ),
        QualityResult(
            "FORECAST_NONNEGATIVE_FINITE",
            "FAIL",
            "PASS",
            len(predictions),
            0,
            metric_value=min(
                (item.predicted_demand for item in predictions), default=0.0
            ),
        ),
    )


def run_forecast(
    config: ProcessingConfig,
    *,
    operations_config: str | Path,
    model_version: str,
    inference_cutoff: str,
    purpose: str,
    environment: Mapping[str, str] | None = None,
    now: datetime | None = None,
    identity: RunIdentity | None = None,
    connection_factory: Callable[..., Any] = connect_database,
    processing_repository_factory: Callable[[Any], ProcessingRunRepository] = PostgresProcessingRunRepository,
    forecast_repository_factory: Callable[[Any], PostgresForecastRepository] = PostgresForecastRepository,
) -> ForecastOutcome:
    environment = environment or os.environ
    now = (now or datetime.now(timezone.utc)).astimezone(timezone.utc)
    operations = load_operations_config(operations_config)
    root = analytics_root(config, environment)
    cutoff = parse_inference_cutoff(inference_cutoff, config.temporal.bucket_minutes)
    settings = DatabaseSettings.from_environment(environment)
    connection = connection_factory(settings, read_only=False)
    started = False
    terminal = False
    db_run_id: uuid.UUID | None = None
    forecast_run_id: uuid.UUID | None = None
    processing_repository: ProcessingRunRepository | None = None
    forecast_repository: PostgresForecastRepository | None = None
    rows = 0
    run_directory: Path | None = None
    try:
        verify_database(connection, require_forecast_schema=True)
        processing_repository = processing_repository_factory(connection)
        forecast_repository = forecast_repository_factory(connection)
        model = forecast_repository.get_model(model_version)
        _validate_model(config, model, cutoff, purpose, now, operations)
        artifact = load_registered_artifact(
            config,
            root=root,
            artifact_uri=model.artifact_uri,
            expected_sha256=model.artifact_sha256,
        )
        if artifact.model_version != model.model_version:
            raise ExtractionError(
                "MODEL_ARTIFACT_REGISTRY_MISMATCH",
                "Verified artifact and model registry version disagree",
            )
        existing = forecast_repository.find_existing(
            model.model_version_id,
            cutoff,
            config.config_hash,
            purpose,
        )
        if existing is not None:
            return existing
        identity = identity or build_run_identity(config, "forecast")
        if not _COMMIT_HASH.fullmatch(identity.git_commit):
            raise ExtractionError(
                "CODE_COMMIT_UNAVAILABLE",
                "A full Git commit hash is required for forecast evidence",
            )
        run_directory = allocate_run_directory(root, identity)
        write_run_manifest(run_directory, identity)
        shutil.copyfile(operations.path, run_directory / "operations-config.yml")
        db_run_id = processing_run_id(identity)
        attempt_no = processing_repository.start(
            run_id=db_run_id,
            run_type="FORECAST",
            identity=identity,
            source_profile=config.profile.name,
            dataset_version=config.dataset.version,
            source_cutoff=cutoff,
            input_manifest={
                "approvalScope": APPROVAL_SCOPE,
                "artifactSha256": model.artifact_sha256,
                "modelVersion": model.model_version,
                "modelVersionId": str(model.model_version_id),
                "operationsConfigHash": operations.config_hash,
                "purpose": purpose,
            },
        )
        started = True
        forecast_run_id = uuid.uuid5(
            FORECAST_RUN_NAMESPACE,
            f"{model.model_version_id}:{cutoff.isoformat()}:{config.config_hash}:{purpose}",
        )
        forecast_repository.start_forecast(
            forecast_run_id=forecast_run_id,
            processing_run_id_value=db_run_id,
            model=model,
            purpose=purpose,
            cutoff=cutoff,
            config_hash=config.config_hash,
            generated_at=now,
        )
        features = forecast_repository.load_features(
            model,
            cutoff,
            config.temporal.forecast_horizons_minutes,
            artifact.feature_names,
        )
        if len(features) > operations.maximum_rows_per_run:
            raise ExtractionError(
                "FORECAST_COST_GUARD_EXCEEDED",
                "Forecast population exceeds the frozen operations guard",
                {
                    "maximumRows": operations.maximum_rows_per_run,
                    "projectedRows": len(features),
                },
            )
        predictions = _predict(
            artifact.bundle,
            features,
            config.temporal.forecast_horizons_minutes,
        )
        rows = len(predictions)
        quality_results = _quality(
            features,
            predictions,
            config.temporal.forecast_horizons_minutes,
            cutoff,
        )
        failed = [item.rule_code for item in quality_results if item.result_status == "FAIL"]
        _write_json(
            run_directory / "forecast-quality.json",
            {
                "overallStatus": "FAIL" if failed else "PASS",
                "results": [item.to_dict() for item in quality_results],
            },
        )
        if failed:
            processing_repository.save_quality(db_run_id, quality_results)
            raise DataQualityError(failed, str(db_run_id))
        started_at = time.perf_counter()
        finished_at = datetime.now(timezone.utc)
        forecast_repository.publish(
            forecast_run_id=forecast_run_id,
            processing_run_id_value=db_run_id,
            model=model,
            predictions=predictions,
            generated_at=now,
            finished_at=finished_at,
            quality_results=quality_results,
            processing_repository=processing_repository,
            projected_srid=config.spatial.projected_srid,
            grid_origin_x_meters=config.spatial.grid_origin_x_meters,
            grid_origin_y_meters=config.spatial.grid_origin_y_meters,
        )
        terminal = True
        _write_json(
            run_directory / "forecast-manifest.json",
            {
                "approvalScope": APPROVAL_SCOPE,
                "artifactSha256": model.artifact_sha256,
                "cellSizeMeters": model.cell_size_meters,
                "forecastRows": rows,
                "forecastRunId": str(forecast_run_id),
                "freshnessStatus": (
                    "NOT_APPLICABLE_HISTORICAL"
                    if purpose == "EVALUATION"
                    else "FRESH"
                ),
                "horizonsMinutes": list(config.temporal.forecast_horizons_minutes),
                "inferenceCutoffUtc": cutoff.isoformat().replace("+00:00", "Z"),
                "modelVersion": model.model_version,
                "modelVersionId": str(model.model_version_id),
                "operationsConfigHash": operations.config_hash,
                "qualityStatus": "PASS",
                "retentionDays": operations.forecast_retention_days,
                "runPurpose": purpose,
            },
        )
        _write_json(
            run_directory / "system-metrics.json",
            {
                "forecastRows": rows,
                "inferenceAndPublicationSeconds": time.perf_counter() - started_at,
            },
        )
        _write_checksums(run_directory)
        return ForecastOutcome(
            artifact_run_id=identity.run_id,
            processing_run_id=db_run_id,
            forecast_run_id=forecast_run_id,
            model_version_id=model.model_version_id,
            model_version=model.model_version,
            inference_cutoff_utc=cutoff,
            run_purpose=purpose,
            forecast_rows=rows,
            quality_status="PASS",
            idempotent=False,
            attempt_no=attempt_no,
            run_directory=run_directory,
        )
    except Exception as error:
        if started and not terminal and processing_repository is not None and db_run_id is not None:
            code = error.code if isinstance(error, AnalyticsError) else "FORECAST_FAILED"
            if forecast_repository is not None and forecast_run_id is not None:
                try:
                    forecast_repository.fail_forecast(
                        forecast_run_id,
                        error_code=code,
                        error_message=str(error),
                        finished_at=datetime.now(timezone.utc),
                    )
                except Exception:
                    pass
            try:
                processing_repository.fail(
                    db_run_id,
                    rows_read=rows,
                    rows_written=0,
                    error_code=code,
                    error_message=str(error),
                )
            except Exception:
                pass
            if run_directory is not None:
                _write_json(
                    run_directory / "failure.json",
                    {"errorCode": code, "message": str(error)},
                )
                _write_checksums(run_directory)
        raise
    finally:
        connection.close()
