from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Iterator

from ..errors import ExtractionError
from ..features.persistence import ResumableFeatureArtifact
from ..evaluation.folds import EvaluationFold
from .experiment import CandidateExperiment


@dataclass(frozen=True)
class StratumCount:
    positive: int
    zero: int


@dataclass(frozen=True)
class SampledTrainingSet:
    features: object
    target: object
    sample_weight: object
    feature_names: tuple[str, ...]
    population_positive: int
    population_zero: int
    sampled_positive: int
    sampled_zero: int

    @property
    def sampled_rows(self) -> int:
        return self.sampled_positive + self.sampled_zero

    def to_dict(self) -> dict[str, object]:
        return {
            "populationPositive": self.population_positive,
            "populationRows": self.population_positive + self.population_zero,
            "populationZero": self.population_zero,
            "sampledPositive": self.sampled_positive,
            "sampledRows": self.sampled_rows,
            "sampledZero": self.sampled_zero,
            "weightPositive": self.population_positive / self.sampled_positive,
            "weightZero": self.population_zero / self.sampled_zero,
        }


def _timestamp_us(value: datetime) -> int:
    return int(value.timestamp() * 1_000_000)


def _runtime():
    try:
        import numpy as np
        import pyarrow as pa
        import pyarrow.parquet as pq
    except ImportError as error:
        raise ExtractionError(
            "MODEL_RUNTIME_MISSING",
            "NumPy, PyArrow and scikit-learn Phase 6 dependencies are required",
        ) from error
    return np, pa, pq


def count_training_strata(
    artifact: ResumableFeatureArtifact,
    folds: tuple[EvaluationFold, ...],
    horizons: tuple[int, ...],
) -> dict[tuple[str, int], StratumCount]:
    np, pa, pq = _runtime()
    counts: dict[tuple[str, int], list[int]] = {
        (fold.key, horizon): [0, 0]
        for fold in folds
        for horizon in horizons
    }
    boundaries = {
        fold.key: (_timestamp_us(fold.train_from_utc), _timestamp_us(fold.train_to_utc))
        for fold in folds
    }
    for partition in artifact.partitions:
        parquet = pq.ParquetFile(partition.path)
        for batch in parquet.iter_batches(
            batch_size=200_000,
            columns=["target_bucket_start_utc", "horizon_minutes", "target_trip_requests"],
        ):
            targets = batch.column(0).cast(pa.int64()).to_numpy(zero_copy_only=False)
            row_horizons = batch.column(1).to_numpy(zero_copy_only=False)
            actuals = batch.column(2).to_numpy(zero_copy_only=False)
            for fold in folds:
                start, end = boundaries[fold.key]
                time_mask = (targets >= start) & (targets < end)
                if not np.any(time_mask):
                    continue
                for horizon in horizons:
                    values = actuals[time_mask & (row_horizons == horizon)]
                    positive = int(np.count_nonzero(values > 0))
                    counts[(fold.key, horizon)][0] += positive
                    counts[(fold.key, horizon)][1] += int(values.size) - positive
    result = {
        key: StratumCount(positive=value[0], zero=value[1])
        for key, value in counts.items()
    }
    empty = [f"{key[0]}:{key[1]}" for key, value in result.items() if value.positive + value.zero == 0]
    if empty:
        raise ExtractionError(
            "MODEL_TRAINING_POPULATION_EMPTY",
            "One or more fold/horizon training populations are empty",
            {"populations": empty},
        )
    return result


def _stable_hash(np, grid_x, grid_y, target_us, horizon: int, seed: int):
    with np.errstate(over="ignore"):
        value = target_us.astype(np.int64).view(np.uint64)
        value ^= grid_x.astype(np.int64).view(np.uint64) * np.uint64(0x9E3779B185EBCA87)
        value ^= grid_y.astype(np.int64).view(np.uint64) * np.uint64(0xC2B2AE3D27D4EB4F)
        value ^= np.uint64(horizon) * np.uint64(0x165667B19E3779F9)
        value ^= np.uint64(seed)
        value += np.uint64(0x9E3779B97F4A7C15)
        value = (value ^ (value >> np.uint64(30))) * np.uint64(0xBF58476D1CE4E5B9)
        value = (value ^ (value >> np.uint64(27))) * np.uint64(0x94D049BB133111EB)
        return value ^ (value >> np.uint64(31))


def _numeric_column(np, column):
    values = column.to_numpy(zero_copy_only=False)
    if values.dtype == object:
        return np.asarray([np.nan if item is None else item for item in values], dtype=np.float64)
    return values.astype(np.float64, copy=False)


def load_sampled_training_set(
    artifact: ResumableFeatureArtifact,
    experiment: CandidateExperiment,
    fold: EvaluationFold,
    horizon: int,
    count: StratumCount,
) -> SampledTrainingSet:
    np, pa, pq = _runtime()
    feature_names = tuple(experiment.feature_sets["A2"])
    columns = [
        "grid_x",
        "grid_y",
        "target_bucket_start_utc",
        "horizon_minutes",
        "target_trip_requests",
        *feature_names,
    ]
    start = _timestamp_us(fold.train_from_utc)
    end = _timestamp_us(fold.train_to_utc)
    quotas = {
        True: experiment.training_sample.positive_row_quota,
        False: experiment.training_sample.zero_row_quota,
    }
    reservoirs: dict[bool, tuple[object, object, object] | None] = {
        True: None,
        False: None,
    }
    for partition in artifact.partitions:
        parquet = pq.ParquetFile(partition.path)
        for batch in parquet.iter_batches(batch_size=100_000, columns=columns):
            targets_us = batch.column(2).cast(pa.int64()).to_numpy(zero_copy_only=False)
            row_horizons = batch.column(3).to_numpy(zero_copy_only=False)
            actuals = batch.column(4).to_numpy(zero_copy_only=False).astype(np.float64, copy=False)
            population_mask = (
                (targets_us >= start)
                & (targets_us < end)
                & (row_horizons == horizon)
            )
            if not np.any(population_mask):
                continue
            indexes = np.flatnonzero(population_mask)
            hashes = _stable_hash(
                np,
                batch.column(0).to_numpy(zero_copy_only=False)[indexes],
                batch.column(1).to_numpy(zero_copy_only=False)[indexes],
                targets_us[indexes],
                horizon,
                experiment.random_seed,
            )
            values = actuals[indexes]
            batch_features = np.column_stack(
                [
                    _numeric_column(np, batch.column(5 + offset))[indexes]
                    for offset in range(len(feature_names))
                ]
            )
            for positive in (True, False):
                stratum_indexes = np.flatnonzero((values > 0) == positive)
                if stratum_indexes.size == 0:
                    continue
                new_features = batch_features[stratum_indexes]
                new_target = values[stratum_indexes]
                new_hashes = hashes[stratum_indexes]
                current = reservoirs[positive]
                if current is not None:
                    new_features = np.concatenate((current[0], new_features))
                    new_target = np.concatenate((current[1], new_target))
                    new_hashes = np.concatenate((current[2], new_hashes))
                quota = quotas[positive]
                if new_hashes.size > quota:
                    retained = np.argpartition(new_hashes, quota - 1)[:quota]
                    new_features = new_features[retained]
                    new_target = new_target[retained]
                    new_hashes = new_hashes[retained]
                reservoirs[positive] = (new_features, new_target, new_hashes)
    if reservoirs[True] is None or reservoirs[False] is None:
        raise ExtractionError(
            "MODEL_TRAINING_SAMPLE_EMPTY",
            "Deterministic sampling did not produce both training strata",
            {"foldKey": fold.key, "horizonMinutes": horizon},
        )
    features = np.concatenate((reservoirs[True][0], reservoirs[False][0]))
    target = np.concatenate((reservoirs[True][1], reservoirs[False][1]))
    hashes = np.concatenate((reservoirs[True][2], reservoirs[False][2]))
    order = np.argsort(hashes, kind="stable")
    features = features[order]
    target = target[order]
    sampled_positive = int(np.count_nonzero(target > 0))
    sampled_zero = int(target.size - sampled_positive)
    if sampled_positive == 0 or sampled_zero == 0:
        raise ExtractionError(
            "MODEL_TRAINING_STRATUM_EMPTY",
            "Both positive and zero-demand strata are required",
            {"foldKey": fold.key, "horizonMinutes": horizon},
        )
    weights = np.where(
        target > 0,
        count.positive / sampled_positive,
        count.zero / sampled_zero,
    ).astype(np.float64)
    return SampledTrainingSet(
        features=features,
        target=target,
        sample_weight=weights,
        feature_names=feature_names,
        population_positive=count.positive,
        population_zero=count.zero,
        sampled_positive=sampled_positive,
        sampled_zero=sampled_zero,
    )


@dataclass(frozen=True)
class EvaluationBatch:
    features: object
    actual: object
    cell_ids: list[str] | None
    inference_cutoff_utc: list[datetime] | None
    target_bucket_start_utc: list[datetime] | None


def iter_evaluation_batches(
    artifact: ResumableFeatureArtifact,
    experiment: CandidateExperiment,
    fold: EvaluationFold,
    horizon: int,
    *,
    batch_size: int = 100_000,
    include_metadata: bool = True,
) -> Iterator[EvaluationBatch]:
    np, pa, pq = _runtime()
    feature_names = tuple(experiment.feature_sets["A2"])
    columns = [
        "cell_id",
        "inference_cutoff_utc",
        "target_bucket_start_utc",
        "horizon_minutes",
        "target_trip_requests",
        *feature_names,
    ]
    start = _timestamp_us(fold.evaluate_from_utc)
    end = _timestamp_us(fold.evaluate_to_utc)
    for partition in artifact.partitions:
        parquet = pq.ParquetFile(partition.path)
        for batch in parquet.iter_batches(batch_size=batch_size, columns=columns):
            targets_us = batch.column(2).cast(pa.int64()).to_numpy(zero_copy_only=False)
            row_horizons = batch.column(3).to_numpy(zero_copy_only=False)
            mask = (targets_us >= start) & (targets_us < end) & (row_horizons == horizon)
            if not np.any(mask):
                continue
            indexes = np.flatnonzero(mask)
            yield EvaluationBatch(
                features=np.column_stack(
                    [_numeric_column(np, batch.column(5 + offset))[indexes] for offset in range(len(feature_names))]
                ),
                actual=batch.column(4).to_numpy(zero_copy_only=False)[indexes].astype(np.float64, copy=False),
                cell_ids=(
                    [str(batch.column(0)[int(index)].as_py()) for index in indexes]
                    if include_metadata
                    else None
                ),
                inference_cutoff_utc=(
                    [batch.column(1)[int(index)].as_py() for index in indexes]
                    if include_metadata
                    else None
                ),
                target_bucket_start_utc=(
                    [batch.column(2)[int(index)].as_py() for index in indexes]
                    if include_metadata
                    else None
                ),
            )


def feature_indexes(
    full_names: tuple[str, ...],
    selected_names: tuple[str, ...],
):
    np, _pa, _pq = _runtime()
    lookup = {name: index for index, name in enumerate(full_names)}
    return np.asarray([lookup[name] for name in selected_names], dtype=np.int64)
