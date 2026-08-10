from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable, Mapping

import yaml

from .config import ProcessingConfig
from .errors import ExtractionError
from .hashing import file_sha256
from .manifest import DatasetValidationResult


_SHA256 = re.compile(r"^[0-9a-f]{64}$")
_REQUIRED_ROLES = {
    "extraction",
    "feature-g500",
    "feature-g1000",
    "feature-g2000",
    "baseline-g500",
    "baseline-g1000",
    "baseline-g2000",
    "candidate-g500",
    "candidate-g1000",
    "candidate-g2000",
    "forecast",
    "backfill",
    "rollback-failure",
}


@dataclass(frozen=True)
class VerifiedRun:
    role: str
    path: str
    manifest: dict[str, Any]
    verified_files: int
    verified_bytes: int


@dataclass(frozen=True)
class ThesisVerificationOutcome:
    artifact_run_id: str
    report_path: Path
    report_sha256: str
    contract_sha256: str
    verified_files: int
    verified_bytes: int
    quality_status: str = "PASS"

    def to_dict(self) -> dict[str, Any]:
        return {
            "artifactRunId": self.artifact_run_id,
            "qualityStatus": self.quality_status,
            "report": {
                "path": self.report_path.as_posix(),
                "sha256": self.report_sha256,
                "contractSha256": self.contract_sha256,
                "verifiedFiles": self.verified_files,
                "verifiedBytes": self.verified_bytes,
            },
        }


def _fail(code: str, message: str, details: dict[str, Any] | None = None) -> None:
    raise ExtractionError(code, message, details or {})


def _load_yaml(path: Path) -> dict[str, Any]:
    try:
        value = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (OSError, yaml.YAMLError) as error:
        raise ExtractionError(
            "THESIS_EVIDENCE_CONTRACT_INVALID",
            "Could not read the thesis evidence contract",
        ) from error
    if not isinstance(value, dict):
        _fail("THESIS_EVIDENCE_CONTRACT_INVALID", "Evidence contract must be an object")
    return value


def _safe_child(root: Path, relative: object, field: str) -> Path:
    supplied = Path(str(relative))
    if supplied.is_absolute():
        _fail("THESIS_EVIDENCE_PATH_INVALID", f"{field} must be relative")
    candidate = (root / supplied).resolve()
    try:
        candidate.relative_to(root)
    except ValueError:
        _fail("THESIS_EVIDENCE_PATH_INVALID", f"{field} escapes the analytics root")
    return candidate


def _load_json(path: Path, code: str = "THESIS_EVIDENCE_MANIFEST_INVALID") -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ExtractionError(code, f"Could not read {path.name}") from error
    if not isinstance(value, dict):
        _fail(code, f"{path.name} must contain an object")
    return value


def _checksum_entries(directory: Path) -> list[tuple[Path, str]]:
    checksum_path = directory / "checksums.sha256"
    try:
        lines = checksum_path.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        raise ExtractionError(
            "THESIS_EVIDENCE_CHECKSUMS_MISSING",
            f"checksums.sha256 is missing for {directory.name}",
        ) from error
    entries: list[tuple[Path, str]] = []
    covered: set[Path] = set()
    for line in lines:
        parts = line.split("  ", 1)
        if len(parts) != 2 or not _SHA256.fullmatch(parts[0]):
            _fail("THESIS_EVIDENCE_CHECKSUMS_INVALID", "Malformed SHA-256 evidence line")
        target = _safe_child(directory, parts[1], "checksums.sha256 path")
        if target in covered:
            _fail("THESIS_EVIDENCE_CHECKSUMS_INVALID", "Duplicate checksum evidence path")
        covered.add(target)
        entries.append((target, parts[0]))
    if not entries:
        _fail("THESIS_EVIDENCE_CHECKSUMS_INVALID", "Checksum evidence must not be empty")
    return entries


def _nested_file_records(value: object) -> Iterable[dict[str, Any]]:
    if isinstance(value, dict):
        if "file" in value and "sha256" in value:
            yield value
        for nested in value.values():
            yield from _nested_file_records(nested)
    elif isinstance(value, list):
        for nested in value:
            yield from _nested_file_records(nested)


def _verify_file(target: Path, expected_sha256: str, expected_bytes: object = None) -> int:
    if not _SHA256.fullmatch(str(expected_sha256)):
        _fail("THESIS_EVIDENCE_CHECKSUMS_INVALID", "Expected SHA-256 is malformed")
    if not target.is_file():
        _fail(
            "THESIS_EVIDENCE_FILE_MISSING",
            "A frozen evidence file is missing",
            {"file": target.name},
        )
    actual_size = target.stat().st_size
    if expected_bytes is not None and int(expected_bytes) != actual_size:
        _fail(
            "THESIS_EVIDENCE_SIZE_MISMATCH",
            "A frozen evidence file size mismatches its manifest",
            {"file": target.name},
        )
    if file_sha256(target) != expected_sha256:
        _fail(
            "THESIS_EVIDENCE_CHECKSUM_MISMATCH",
            "A frozen evidence file checksum mismatches",
            {"file": target.name},
        )
    return actual_size


def _verify_run(root: Path, raw: Mapping[str, Any]) -> VerifiedRun:
    role = str(raw.get("role", "")).strip()
    relative = str(raw.get("path", "")).strip()
    manifest_name = str(raw.get("manifest", "")).strip()
    if not role or not relative.startswith("runs/") or not manifest_name:
        _fail("THESIS_EVIDENCE_CONTRACT_INVALID", "Every run requires role, runs/ path and manifest")
    directory = _safe_child(root, relative, f"runs.{role}.path")
    if not directory.is_dir():
        _fail("THESIS_EVIDENCE_RUN_MISSING", f"Frozen run {role} does not exist")
    manifest_path = _safe_child(directory, manifest_name, f"runs.{role}.manifest")
    manifest = _load_json(manifest_path)
    verified: dict[Path, int] = {}
    for target, expected in _checksum_entries(directory):
        verified[target] = _verify_file(target, expected)
    if manifest_path not in verified:
        _fail(
            "THESIS_EVIDENCE_CHECKSUM_COVERAGE_MISSING",
            f"Primary manifest for {role} is not covered by checksums.sha256",
        )
    nested_names = raw.get("nested_manifests", [])
    if not isinstance(nested_names, list):
        _fail("THESIS_EVIDENCE_CONTRACT_INVALID", "nested_manifests must be a list")
    for nested_name in nested_names:
        nested_path = _safe_child(directory, nested_name, f"runs.{role}.nested_manifests")
        nested = _load_json(nested_path)
        if nested_path not in verified:
            _fail(
                "THESIS_EVIDENCE_CHECKSUM_COVERAGE_MISSING",
                f"Nested manifest {nested_path.name} is not checksum-covered",
            )
        for record in _nested_file_records(nested):
            target = _safe_child(directory, record["file"], f"runs.{role}.nested file")
            verified[target] = _verify_file(target, str(record["sha256"]), record.get("bytes"))
    return VerifiedRun(role, relative, manifest, len(verified), sum(verified.values()))


def _require_equal(actual: object, expected: object, field: str) -> None:
    if actual != expected:
        _fail(
            "THESIS_EVIDENCE_LINEAGE_MISMATCH",
            f"Frozen lineage mismatch for {field}",
            {"field": field, "expected": expected, "actual": actual},
        )


def _validate_lineage(
        runs: Mapping[str, VerifiedRun],
        expected: Mapping[str, Any],
        minimum_aggregate_count: int,
) -> dict[str, Any]:
    feature_roles = ("feature-g500", "feature-g1000", "feature-g2000")
    feature_sizes = sorted(int(runs[role].manifest["cellSizeMeters"]) for role in feature_roles)
    expected_sizes = sorted(int(value) for value in expected.get("cell_sizes_meters", []))
    _require_equal(feature_sizes, expected_sizes, "cell_sizes_meters")

    baseline_roles = ("baseline-g500", "baseline-g1000", "baseline-g2000")
    for role in baseline_roles:
        _require_equal(runs[role].manifest.get("models"), ["HISTORICAL_MEAN", "SEASONAL_NAIVE"], f"{role}.models")
        _require_equal(runs[role].manifest.get("qualityStatus"), "PASS", f"{role}.qualityStatus")

    candidate = runs["candidate-g500"].manifest
    _require_equal(candidate.get("experimentHash"), expected.get("experiment_hash"), "candidate.experimentHash")
    _require_equal(candidate.get("modelVersion"), expected.get("model_version"), "candidate.modelVersion")
    _require_equal(candidate.get("qualityStatus"), "PASS", "candidate.qualityStatus")

    forecast = runs["forecast"].manifest
    _require_equal(forecast.get("modelVersion"), candidate.get("modelVersion"), "forecast.modelVersion")
    _require_equal(forecast.get("artifactSha256"), candidate.get("modelSha256"), "forecast.artifactSha256")
    _require_equal(forecast.get("forecastRunId"), expected.get("forecast_run_id"), "forecast.forecastRunId")
    _require_equal(forecast.get("approvalScope"), "RESEARCH_DEMONSTRATION", "forecast.approvalScope")
    _require_equal(sorted(forecast.get("horizonsMinutes", [])), sorted(expected.get("horizons_minutes", [])), "forecast.horizonsMinutes")

    backfill = runs["backfill"].manifest
    _require_equal(backfill.get("forecastRunId"), forecast.get("forecastRunId"), "backfill.forecastRunId")
    _require_equal(backfill.get("qualityStatus"), "PASS", "backfill.qualityStatus")
    _require_equal(backfill.get("eligibleRows"), backfill.get("updatedRows"), "backfill.updatedRows")

    rollback = runs["rollback-failure"].manifest
    _require_equal(rollback.get("errorCode"), expected.get("rollback_failure_code"), "rollback.errorCode")
    if minimum_aggregate_count < 3:
        _fail(
            "THESIS_EVIDENCE_PRIVACY_INVALID",
            "minimum_aggregate_count must be at least 3",
        )
    return {
        "cellSizesMeters": feature_sizes,
        "horizonsMinutes": sorted(forecast.get("horizonsMinutes", [])),
        "experimentHash": candidate.get("experimentHash"),
        "modelVersion": candidate.get("modelVersion"),
        "modelSha256": candidate.get("modelSha256"),
        "forecastRunId": forecast.get("forecastRunId"),
        "forecastRows": forecast.get("forecastRows"),
        "backfilledRows": backfill.get("updatedRows"),
        "rollbackFailureCode": rollback.get("errorCode"),
        "minimumAggregateCount": minimum_aggregate_count,
    }


def run_thesis_verification(
        config: ProcessingConfig,
        dataset: DatasetValidationResult,
        *,
        evidence_contract: str | Path,
        output_directory: str | Path,
        environment: Mapping[str, str] | None = None,
) -> ThesisVerificationOutcome:
    environment = environment or os.environ
    contract_path = Path(evidence_contract).expanduser().resolve()
    contract = _load_yaml(contract_path)
    allowed = {
        "schema_version", "frozen", "claim_scope", "dataset_sha256",
        "minimum_aggregate_count", "expected", "runs", "privacy",
    }
    if set(contract) != allowed or contract.get("schema_version") != 1 or contract.get("frozen") is not True:
        _fail("THESIS_EVIDENCE_CONTRACT_INVALID", "Evidence contract keys/version/frozen flag are invalid")
    if contract.get("claim_scope") != "RESEARCH_DEMONSTRATION":
        _fail("THESIS_EVIDENCE_SCOPE_INVALID", "Porto evidence must remain research-only")
    _require_equal(dataset.sha256, contract.get("dataset_sha256"), "dataset.sha256")

    root_variable = config.dataset.root_env or "GORIDE_ANALYTICS_DATA_ROOT"
    root_value = environment.get(root_variable, "").strip()
    if not root_value:
        _fail("THESIS_EVIDENCE_ROOT_MISSING", f"Environment variable {root_variable} is required")
    root = Path(root_value).expanduser().resolve()
    raw_runs = contract.get("runs")
    if not isinstance(raw_runs, list):
        _fail("THESIS_EVIDENCE_CONTRACT_INVALID", "runs must be a list")
    verified_runs = [_verify_run(root, raw) for raw in raw_runs if isinstance(raw, dict)]
    by_role = {run.role: run for run in verified_runs}
    if len(by_role) != len(verified_runs) or set(by_role) != _REQUIRED_ROLES:
        _fail("THESIS_EVIDENCE_CONTRACT_INVALID", "Evidence roles are missing or duplicated")

    privacy = contract.get("privacy")
    if not isinstance(privacy, dict) or not isinstance(privacy.get("forbidden_api_fields"), list):
        _fail("THESIS_EVIDENCE_PRIVACY_INVALID", "Privacy contract is incomplete")
    forbidden = {str(value) for value in privacy["forbidden_api_fields"]}
    required_forbidden = {"tripId", "userId", "driverId", "passengerId", "email", "phone", "artifactUri"}
    if not required_forbidden.issubset(forbidden):
        _fail("THESIS_EVIDENCE_PRIVACY_INVALID", "Privacy contract omits forbidden API identifiers")

    expected = contract.get("expected")
    if not isinstance(expected, dict):
        _fail("THESIS_EVIDENCE_CONTRACT_INVALID", "expected must be an object")
    minimum_count = int(contract.get("minimum_aggregate_count", 0))
    lineage = _validate_lineage(by_role, expected, minimum_count)
    verified_files = sum(run.verified_files for run in verified_runs) + 2
    verified_bytes = (
        sum(run.verified_bytes for run in verified_runs)
        + int(dataset.source_bytes or 0)
        + contract_path.stat().st_size
    )
    artifact_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ-phase11-verification")
    report = {
        "schemaVersion": 1,
        "artifactRunId": artifact_id,
        "verifiedAtUtc": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "status": "PASS",
        "claimScope": contract["claim_scope"],
        "profile": config.profile.name,
        "configHash": config.config_hash,
        "contractSha256": file_sha256(contract_path),
        "dataset": {
            "name": dataset.dataset_name,
            "version": dataset.dataset_version,
            "sha256": dataset.sha256,
            "license": dataset.license_name,
        },
        "lineage": lineage,
        "privacy": {
            "minimumAggregateCount": minimum_count,
            "forbiddenApiFields": sorted(forbidden),
        },
        "storage": {
            "verifiedFiles": verified_files,
            "verifiedBytes": verified_bytes,
            "datasetSourceBytes": int(dataset.source_bytes or 0),
            "evidenceContractBytes": contract_path.stat().st_size,
            "byRole": {
                run.role: {
                    "path": run.path,
                    "verifiedFiles": run.verified_files,
                    "verifiedBytes": run.verified_bytes,
                }
                for run in verified_runs
            },
        },
        "limitations": [
            "Porto is historical research evidence, not a live Ho Chi Minh City model.",
            "Synthetic smoke evidence validates integration only.",
            "The selected candidate does not beat historical mean on every final metric.",
        ],
    }
    output = Path(output_directory).expanduser().resolve()
    if output.exists():
        _fail("THESIS_EVIDENCE_OUTPUT_EXISTS", "Verification evidence is immutable and will not be overwritten")
    output.mkdir(parents=True, exist_ok=False)
    report_path = output / "thesis-verification.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    report_sha = file_sha256(report_path)
    checksum_path = output / "checksums.sha256"
    checksum_path.write_text(f"{report_sha}  thesis-verification.json\n", encoding="utf-8")
    return ThesisVerificationOutcome(
        artifact_run_id=artifact_id,
        report_path=report_path,
        report_sha256=report_sha,
        contract_sha256=report["contractSha256"],
        verified_files=verified_files,
        verified_bytes=verified_bytes,
    )
