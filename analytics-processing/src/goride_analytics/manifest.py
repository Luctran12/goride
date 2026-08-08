from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

from .config import ProcessingConfig
from .errors import DatasetValidationError
from .hashing import file_sha256


_SHA256 = re.compile(r"^[0-9a-fA-F]{64}$")


@dataclass(frozen=True)
class DatasetManifest:
    dataset_name: str
    dataset_version: str
    source_file: str
    source_relative_path: str
    source_crs: str
    timezone: str
    license_name: str
    sha256: str
    immutable: bool


@dataclass(frozen=True)
class DatasetValidationResult:
    source_type: str
    status: str
    dataset_name: str
    dataset_version: str
    source_relative_path: str | None = None
    source_bytes: int | None = None
    sha256: str | None = None


def _safe_child(root: Path, relative_path: str, label: str) -> Path:
    raw_path = Path(relative_path)
    if raw_path.is_absolute() or raw_path.drive:
        raise DatasetValidationError(
            "DATASET_PATH_UNSAFE",
            f"{label} must be relative to the analytics data root",
            {"field": label},
        )
    candidate = (root / raw_path).resolve()
    try:
        candidate.relative_to(root)
    except ValueError as error:
        raise DatasetValidationError(
            "DATASET_PATH_UNSAFE",
            f"{label} escapes the analytics data root",
            {"field": label},
        ) from error
    return candidate


def _manifest_mapping(path: Path) -> Mapping[str, Any]:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise DatasetValidationError(
            "DATASET_MANIFEST_NOT_FOUND",
            "Dataset manifest does not exist",
            {"manifest": path.name},
        ) from error
    except (OSError, json.JSONDecodeError) as error:
        raise DatasetValidationError(
            "DATASET_MANIFEST_INVALID",
            "Dataset manifest is not valid UTF-8 JSON",
            {"manifest": path.name},
        ) from error
    if not isinstance(raw, Mapping):
        raise DatasetValidationError(
            "DATASET_MANIFEST_INVALID",
            "Dataset manifest root must be an object",
            {"manifest": path.name},
        )
    return raw


def load_dataset_manifest(path: Path) -> DatasetManifest:
    raw = _manifest_mapping(path)
    required = {
        "datasetName",
        "datasetVersion",
        "sourceFile",
        "sourceRelativePath",
        "sourceCrs",
        "timezone",
        "license",
        "sha256",
        "immutable",
    }
    missing = sorted(required - set(raw))
    unknown = sorted(set(raw) - required)
    if missing:
        raise DatasetValidationError(
            "DATASET_MANIFEST_FIELD_MISSING",
            "Dataset manifest is missing required fields",
            {"manifest": path.name, "missing": missing},
        )
    if unknown:
        raise DatasetValidationError(
            "DATASET_MANIFEST_FIELD_UNKNOWN",
            "Dataset manifest contains unknown fields",
            {"manifest": path.name, "unknown": unknown},
        )

    def required_string(key: str) -> str:
        value = raw[key]
        if not isinstance(value, str) or not value.strip():
            raise DatasetValidationError(
                "DATASET_MANIFEST_FIELD_INVALID",
                f"Dataset manifest field {key} must be a non-blank string",
                {"manifest": path.name, "field": key},
            )
        return value.strip()

    checksum = required_string("sha256").lower()
    if not _SHA256.fullmatch(checksum):
        raise DatasetValidationError(
            "DATASET_MANIFEST_CHECKSUM_INVALID",
            "Dataset manifest sha256 must contain 64 hexadecimal characters",
            {"manifest": path.name},
        )
    immutable = raw["immutable"]
    if immutable is not True:
        raise DatasetValidationError(
            "DATASET_MANIFEST_MUTABLE",
            "Dataset source must be declared immutable",
            {"manifest": path.name},
        )
    manifest = DatasetManifest(
        dataset_name=required_string("datasetName"),
        dataset_version=required_string("datasetVersion"),
        source_file=required_string("sourceFile"),
        source_relative_path=required_string("sourceRelativePath"),
        source_crs=required_string("sourceCrs"),
        timezone=required_string("timezone"),
        license_name=required_string("license"),
        sha256=checksum,
        immutable=immutable,
    )
    if Path(manifest.source_relative_path).name != manifest.source_file:
        raise DatasetValidationError(
            "DATASET_MANIFEST_SOURCE_MISMATCH",
            "sourceFile must equal the file name in sourceRelativePath",
            {"manifest": path.name},
        )
    return manifest


def validate_dataset(
    config: ProcessingConfig,
    environment: Mapping[str, str] | None = None,
) -> DatasetValidationResult:
    if environment is None:
        environment = os.environ
    dataset = config.dataset
    if dataset.source_type == "postgresql":
        return DatasetValidationResult(
            source_type="postgresql",
            status="CONFIG_VALID_CONNECTION_DEFERRED_TO_PHASE_3",
            dataset_name=dataset.name,
            dataset_version=dataset.version,
        )

    if dataset.root_env is None or dataset.manifest_relative_path is None:
        raise DatasetValidationError(
            "DATASET_PROFILE_INCOMPLETE",
            "Archive dataset configuration is missing its root or manifest path",
        )
    root_value = environment.get(dataset.root_env, "").strip()
    if not root_value:
        raise DatasetValidationError(
            "DATASET_ROOT_ENV_MISSING",
            f"Environment variable {dataset.root_env} is required",
            {"environmentVariable": dataset.root_env},
        )
    root = Path(root_value).expanduser().resolve()
    if not root.is_dir():
        raise DatasetValidationError(
            "DATASET_ROOT_INVALID",
            "Analytics data root does not exist or is not a directory",
            {"environmentVariable": dataset.root_env},
        )
    manifest_path = _safe_child(root, dataset.manifest_relative_path, "manifest_relative_path")
    manifest = load_dataset_manifest(manifest_path)
    expected = {
        "datasetName": (dataset.name, manifest.dataset_name),
        "datasetVersion": (dataset.version, manifest.dataset_version),
        "sourceCrs": (dataset.expected_source_crs, manifest.source_crs),
        "timezone": (dataset.expected_timezone, manifest.timezone),
    }
    mismatches = {
        key: {"expected": expected_value, "actual": actual_value}
        for key, (expected_value, actual_value) in expected.items()
        if expected_value != actual_value
    }
    if mismatches:
        raise DatasetValidationError(
            "DATASET_MANIFEST_IDENTITY_MISMATCH",
            "Dataset manifest does not match the selected profile",
            {"mismatches": mismatches},
        )
    source_path = _safe_child(root, manifest.source_relative_path, "sourceRelativePath")
    if not source_path.is_file():
        raise DatasetValidationError(
            "DATASET_SOURCE_NOT_FOUND",
            "Dataset source artifact does not exist",
            {"sourceRelativePath": manifest.source_relative_path},
        )
    actual_checksum = file_sha256(source_path)
    if actual_checksum != manifest.sha256:
        raise DatasetValidationError(
            "DATASET_CHECKSUM_MISMATCH",
            "Dataset source checksum does not match the manifest",
            {
                "sourceRelativePath": manifest.source_relative_path,
                "expectedSha256": manifest.sha256,
                "actualSha256": actual_checksum,
            },
        )
    return DatasetValidationResult(
        source_type="archive",
        status="VALID",
        dataset_name=manifest.dataset_name,
        dataset_version=manifest.dataset_version,
        source_relative_path=manifest.source_relative_path,
        source_bytes=source_path.stat().st_size,
        sha256=actual_checksum,
    )
