from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from ..config import ProcessingConfig
from ..errors import ExtractionError
from ..features.persistence import ResumableFeatureArtifact
from ..hashing import canonical_mapping_sha256, file_sha256


@dataclass(frozen=True)
class BaselineEvidence:
    run_id: str
    run_directory: Path
    raw_prediction_sha256: str
    raw_prediction_rows: int
    metrics: tuple[dict[str, Any], ...]
    quantile_thresholds: dict[str, tuple[int, int, int]]

    def population_counts(self) -> dict[tuple[str, int], int]:
        return {
            (str(item["foldKey"]), int(item["horizonMinutes"])): int(item["sampleCount"])
            for item in self.metrics
            if item.get("modelName") == "HISTORICAL_MEAN" and item.get("sliceType") == "ALL"
        }


def _load_json(path: Path, code: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ExtractionError(code, f"Could not read {path.name}") from error
    if not isinstance(value, dict):
        raise ExtractionError(code, f"{path.name} must contain an object")
    return value


def _resolve(root: Path, baseline_run: str | Path) -> Path:
    supplied = Path(baseline_run)
    candidate = supplied.resolve() if supplied.is_absolute() else (
        (root / "runs" / "evaluation" / supplied).resolve()
        if len(supplied.parts) == 1
        else (root / supplied).resolve()
    )
    expected = (root / "runs" / "evaluation").resolve()
    try:
        candidate.relative_to(expected)
    except ValueError as error:
        raise ExtractionError(
            "MODEL_BASELINE_PATH_INVALID",
            "Baseline run must remain under runs/evaluation",
        ) from error
    if not candidate.is_dir():
        raise ExtractionError("MODEL_BASELINE_RUN_MISSING", "Baseline run does not exist")
    return candidate


def load_baseline_evidence(
    config: ProcessingConfig,
    artifact: ResumableFeatureArtifact,
    *,
    root: Path,
    baseline_run: str | Path,
) -> BaselineEvidence:
    directory = _resolve(root, baseline_run)
    checksums = directory / "checksums.sha256"
    try:
        checksum_lines = checksums.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        raise ExtractionError("MODEL_BASELINE_CHECKSUM_INVALID", "Baseline checksums are missing") from error
    covered: set[str] = set()
    for line in checksum_lines:
        parts = line.split("  ", 1)
        if len(parts) != 2 or len(parts[0]) != 64 or Path(parts[1]).name != parts[1]:
            raise ExtractionError("MODEL_BASELINE_CHECKSUM_INVALID", "Baseline checksum file is malformed")
        path = directory / parts[1]
        if not path.is_file() or file_sha256(path) != parts[0]:
            raise ExtractionError(
                "MODEL_BASELINE_CHECKSUM_MISMATCH",
                "Baseline top-level evidence checksum mismatches",
                {"file": parts[1]},
            )
        covered.add(parts[1])
    required = {
        "evaluation-manifest.json",
        "evaluation-metrics.json",
        "evaluation-quality.json",
        "raw-predictions-manifest.json",
        "run-manifest.json",
    }
    if not required.issubset(covered):
        raise ExtractionError(
            "MODEL_BASELINE_CHECKSUM_INVALID",
            "Baseline checksum coverage is incomplete",
            {"missing": sorted(required - covered)},
        )
    run = _load_json(directory / "run-manifest.json", "MODEL_BASELINE_RUN_INVALID")
    manifest = _load_json(directory / "evaluation-manifest.json", "MODEL_BASELINE_MANIFEST_INVALID")
    quality = _load_json(directory / "evaluation-quality.json", "MODEL_BASELINE_QUALITY_INVALID")
    raw = _load_json(directory / "raw-predictions-manifest.json", "MODEL_BASELINE_RAW_INVALID")
    metric_payload = _load_json(directory / "evaluation-metrics.json", "MODEL_BASELINE_METRICS_INVALID")
    if (
        run.get("configHash") != config.config_hash
        or run.get("profile") != config.profile.name
        or run.get("runType") != "evaluation"
        or manifest.get("sourceArtifactRunId") != artifact.source_run_id
        or manifest.get("featureSetVersion") != artifact.feature_set_version
        or manifest.get("qualityStatus") != "PASS"
        or quality.get("overallStatus") != "PASS"
    ):
        raise ExtractionError(
            "MODEL_BASELINE_MISMATCH",
            "Baseline evidence does not match the active config and feature artifact",
        )
    files = raw.get("files")
    if not isinstance(files, list) or not files:
        raise ExtractionError("MODEL_BASELINE_RAW_INVALID", "Baseline raw manifest has no files")
    try:
        import pyarrow.parquet as pq
    except ImportError as error:
        raise ExtractionError("MODEL_RUNTIME_MISSING", "PyArrow is required to verify baseline predictions") from error
    verified_rows = 0
    for item in files:
        if not isinstance(item, dict):
            raise ExtractionError("MODEL_BASELINE_RAW_INVALID", "Baseline raw file entry is invalid")
        path = (directory / str(item.get("file"))).resolve()
        try:
            path.relative_to(directory)
        except ValueError as error:
            raise ExtractionError("MODEL_BASELINE_RAW_INVALID", "Baseline raw path escapes its run") from error
        if (
            not path.is_file()
            or path.stat().st_size != int(item.get("bytes", -1))
            or file_sha256(path) != item.get("sha256")
            or int(pq.ParquetFile(path).metadata.num_rows) != int(item.get("rows", -1))
        ):
            raise ExtractionError(
                "MODEL_BASELINE_RAW_CHECKSUM_MISMATCH",
                "Baseline raw prediction checksum mismatches",
                {"file": item.get("file")},
            )
        verified_rows += int(item["rows"])
    raw_payload = {"files": files, "rowCount": raw.get("rowCount")}
    if verified_rows != int(raw.get("rowCount", -1)) or canonical_mapping_sha256(raw_payload) != raw.get("sha256"):
        raise ExtractionError("MODEL_BASELINE_RAW_CHECKSUM_MISMATCH", "Baseline raw manifest hash mismatches")
    metrics = metric_payload.get("records")
    thresholds = metric_payload.get("quantileThresholdsByFold")
    if not isinstance(metrics, list) or not isinstance(thresholds, dict):
        raise ExtractionError("MODEL_BASELINE_METRICS_INVALID", "Baseline metric payload is incomplete")
    parsed_thresholds: dict[str, tuple[int, int, int]] = {}
    for key, value in thresholds.items():
        if not isinstance(value, list) or len(value) != 3:
            raise ExtractionError("MODEL_BASELINE_METRICS_INVALID", "Baseline quantile thresholds are invalid")
        parsed_thresholds[str(key)] = tuple(int(item) for item in value)  # type: ignore[assignment]
    return BaselineEvidence(
        run_id=str(run["runId"]),
        run_directory=directory,
        raw_prediction_sha256=str(raw["sha256"]),
        raw_prediction_rows=int(raw["rowCount"]),
        metrics=tuple(dict(item) for item in metrics),
        quantile_thresholds=parsed_thresholds,
    )
