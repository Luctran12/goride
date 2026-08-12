from __future__ import annotations

import json
import re
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Mapping

from ..config import ProcessingConfig
from ..errors import ExtractionError
from ..hashing import file_sha256


_SHA256 = re.compile(r"^[0-9a-f]{64}$")


@dataclass(frozen=True)
class OperationalModelArtifact:
    run_id: str
    run_directory: Path
    artifact_uri: str
    model_name: str
    model_version: str
    model_sha256: str
    feature_set_version: str
    feature_names: tuple[str, ...]
    feature_set: str
    cell_size_meters: int
    training_cutoff_utc: datetime
    selected_configuration: dict[str, Any]
    training_manifest: dict[str, Any]
    model_card: dict[str, Any]
    bundle: dict[str, Any]


def analytics_root(
    config: ProcessingConfig,
    environment: Mapping[str, str],
) -> Path:
    variable = config.dataset.root_env or "GORIDE_ANALYTICS_DATA_ROOT"
    raw = environment.get(variable, "").strip()
    if not raw:
        raise ExtractionError(
            "MODEL_ARTIFACT_ROOT_MISSING",
            f"Environment variable {variable} is required",
        )
    root = Path(raw).expanduser().resolve()
    if not root.is_dir():
        raise ExtractionError(
            "MODEL_ARTIFACT_ROOT_INVALID",
            "Analytics artifact root does not exist",
        )
    return root


def _load_json(path: Path, code: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ExtractionError(code, f"Could not read {path.name}") from error
    if not isinstance(value, dict):
        raise ExtractionError(code, f"{path.name} must contain an object")
    return value


def _resolve_training_run(root: Path, training_run: str | Path) -> Path:
    supplied = Path(training_run)
    if supplied.is_absolute():
        candidate = supplied.resolve()
    elif len(supplied.parts) == 1:
        candidate = (root / "runs" / "training" / supplied).resolve()
    else:
        candidate = (root / supplied).resolve()
    expected = (root / "runs" / "training").resolve()
    try:
        candidate.relative_to(expected)
    except ValueError as error:
        raise ExtractionError(
            "MODEL_ARTIFACT_PATH_INVALID",
            "Training run must remain under runs/training",
        ) from error
    if not candidate.is_dir():
        raise ExtractionError(
            "MODEL_ARTIFACT_RUN_MISSING",
            "Training run directory does not exist",
        )
    return candidate


def _verify_checksums(directory: Path) -> None:
    try:
        lines = (directory / "checksums.sha256").read_text(
            encoding="utf-8"
        ).splitlines()
    except OSError as error:
        raise ExtractionError(
            "MODEL_ARTIFACT_CHECKSUMS_INVALID",
            "Training checksums are missing",
        ) from error
    covered: set[str] = set()
    for line in lines:
        parts = line.split("  ", 1)
        if (
            len(parts) != 2
            or not _SHA256.fullmatch(parts[0])
            or Path(parts[1]).name != parts[1]
            or parts[1] in covered
        ):
            raise ExtractionError(
                "MODEL_ARTIFACT_CHECKSUMS_INVALID",
                "Training checksum file is malformed or unsafe",
            )
        target = directory / parts[1]
        if not target.is_file() or file_sha256(target) != parts[0]:
            raise ExtractionError(
                "MODEL_ARTIFACT_CHECKSUM_MISMATCH",
                "Training artifact checksum mismatches",
                {"file": parts[1]},
            )
        covered.add(parts[1])
    required = {
        "candidate-models.joblib",
        "model-card.json",
        "run-manifest.json",
        "training-manifest.json",
        "training-quality.json",
    }
    if not required.issubset(covered):
        raise ExtractionError(
            "MODEL_ARTIFACT_CHECKSUMS_INVALID",
            "Training checksums do not cover required model evidence",
            {"missing": sorted(required - covered)},
        )


def _parse_utc(value: object, field: str) -> datetime:
    try:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError as error:
        raise ExtractionError(
            "MODEL_ARTIFACT_MANIFEST_INVALID",
            f"{field} is not an ISO-8601 timestamp",
        ) from error
    if parsed.tzinfo is None:
        raise ExtractionError(
            "MODEL_ARTIFACT_MANIFEST_INVALID",
            f"{field} must include an offset",
        )
    return parsed


def load_operational_model_artifact(
    config: ProcessingConfig,
    *,
    root: Path,
    training_run: str | Path,
) -> OperationalModelArtifact:
    directory = _resolve_training_run(root, training_run)
    _verify_checksums(directory)
    run = _load_json(directory / "run-manifest.json", "MODEL_ARTIFACT_RUN_INVALID")
    manifest = _load_json(
        directory / "training-manifest.json",
        "MODEL_ARTIFACT_MANIFEST_INVALID",
    )
    quality = _load_json(
        directory / "training-quality.json",
        "MODEL_ARTIFACT_QUALITY_INVALID",
    )
    card = _load_json(directory / "model-card.json", "MODEL_ARTIFACT_CARD_INVALID")
    if (
        run.get("runType") != "training"
        or run.get("profile") != config.profile.name
        or run.get("configHash") != config.config_hash
        or quality.get("overallStatus") != "PASS"
        or manifest.get("qualityStatus") != "PASS"
        or card.get("status") != "VALIDATED_CANDIDATE"
    ):
        raise ExtractionError(
            "MODEL_ARTIFACT_INCOMPATIBLE",
            "Training evidence is not a validated candidate for this profile",
        )
    model_path = directory / "candidate-models.joblib"
    model_sha256 = file_sha256(model_path)
    if (
        manifest.get("modelSha256") != model_sha256
        or card.get("modelSha256") != model_sha256
        or not _SHA256.fullmatch(model_sha256)
    ):
        raise ExtractionError(
            "MODEL_ARTIFACT_CHECKSUM_MISMATCH",
            "Model SHA-256 does not match its manifests",
        )
    try:
        import joblib

        bundle = joblib.load(model_path)
    except ImportError as error:
        raise ExtractionError(
            "MODEL_RUNTIME_MISSING",
            "joblib is required to load the candidate model",
        ) from error
    except Exception as error:
        raise ExtractionError(
            "MODEL_ARTIFACT_DESERIALIZATION_FAILED",
            "Candidate model bundle could not be loaded",
        ) from error
    if not isinstance(bundle, dict):
        raise ExtractionError(
            "MODEL_ARTIFACT_BUNDLE_INVALID",
            "Candidate model bundle must contain an object",
        )
    try:
        horizons = sorted(int(value) for value in bundle["horizonModels"])
        feature_names = tuple(str(value) for value in bundle["featureNames"])
        model_version = str(bundle["modelVersion"])
        cell_size = int(bundle["cellSizeMeters"])
        training_cutoff = _parse_utc(bundle["trainingCutoffUtc"], "trainingCutoffUtc")
        selected = dict(bundle["selectedConfiguration"])
    except (KeyError, TypeError, ValueError) as error:
        raise ExtractionError(
            "MODEL_ARTIFACT_BUNDLE_INVALID",
            "Candidate model bundle fields are invalid",
        ) from error
    if (
        horizons != list(config.temporal.forecast_horizons_minutes)
        or not feature_names
        or model_version != manifest.get("modelVersion")
        or cell_size != int(manifest.get("cellSizeMeters", -1))
        or bundle.get("featureSet") != card.get("featureSet")
        or bundle.get("experimentHash") != manifest.get("experimentHash")
    ):
        raise ExtractionError(
            "MODEL_ARTIFACT_BUNDLE_INVALID",
            "Candidate bundle and immutable manifests disagree",
        )
    artifact_uri = model_path.relative_to(root).as_posix()
    return OperationalModelArtifact(
        run_id=str(run["runId"]),
        run_directory=directory,
        artifact_uri=artifact_uri,
        model_name=f"{config.profile.name}-demand-hgb",
        model_version=model_version,
        model_sha256=model_sha256,
        feature_set_version=str(manifest["featureSetVersion"]),
        feature_names=feature_names,
        feature_set=str(bundle["featureSet"]),
        cell_size_meters=cell_size,
        training_cutoff_utc=training_cutoff,
        selected_configuration=selected,
        training_manifest=manifest,
        model_card=card,
        bundle=bundle,
    )


def load_registered_artifact(
    config: ProcessingConfig,
    *,
    root: Path,
    artifact_uri: str,
    expected_sha256: str,
) -> OperationalModelArtifact:
    path = (root / artifact_uri).resolve()
    try:
        path.relative_to(root)
    except ValueError as error:
        raise ExtractionError(
            "MODEL_ARTIFACT_PATH_INVALID",
            "Registered artifact URI escapes the analytics root",
        ) from error
    if not path.is_file() or file_sha256(path) != expected_sha256:
        return _raise_checksum()
    return load_operational_model_artifact(
        config,
        root=root,
        training_run=path.parent,
    )


def _raise_checksum() -> OperationalModelArtifact:
    raise ExtractionError(
        "MODEL_ARTIFACT_CHECKSUM_MISMATCH",
        "Registered model artifact SHA-256 mismatches",
    )
