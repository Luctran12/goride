from __future__ import annotations

import json
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterator, Mapping
from uuid import UUID

from ..config import ProcessingConfig
from ..errors import ExtractionError
from ..hashing import file_sha256


_SHA256 = re.compile(r"^[0-9a-f]{64}$")


def _utc(value: str, field: str) -> datetime:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except (AttributeError, ValueError) as error:
        raise ExtractionError(
            "FEATURE_INPUT_INVALID",
            f"{field} is not a valid timestamp",
            {"field": field},
        ) from error
    if parsed.tzinfo is None:
        raise ExtractionError(
            "FEATURE_INPUT_INVALID",
            f"{field} must be timezone-aware",
            {"field": field},
        )
    return parsed.astimezone(timezone.utc)


def _json_object(path: Path) -> Mapping[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ExtractionError(
            "FEATURE_INPUT_INVALID",
            "Required extraction evidence is missing or invalid",
            {"file": path.name},
        ) from error
    if not isinstance(value, Mapping):
        raise ExtractionError(
            "FEATURE_INPUT_INVALID",
            "Extraction evidence root must be an object",
            {"file": path.name},
        )
    return value


@dataclass(frozen=True)
class CanonicalInputEvent:
    source_profile: str
    dataset_version: str
    source_trip_key: str
    event_time_utc: datetime
    demand_event_semantics: str
    longitude: float
    latitude: float
    service_area_key: str | None
    source_cutoff_utc: datetime
    extraction_run_id: UUID


@dataclass(frozen=True)
class ExtractionSnapshot:
    run_directory: Path
    canonical_path: Path
    source_profile: str
    dataset_version: str
    config_hash: str
    from_utc: datetime
    cutoff_utc: datetime
    snapshot_id: UUID
    snapshot_sha256: str
    row_count: int

    @classmethod
    def load(
        cls,
        run_directory: str | Path,
        *,
        analytics_root: Path,
        config: ProcessingConfig,
    ) -> ExtractionSnapshot:
        root = analytics_root.resolve()
        requested = Path(run_directory).expanduser()
        directory = (
            requested.resolve()
            if requested.is_absolute()
            else (root / requested).resolve()
        )
        try:
            directory.relative_to(root)
        except ValueError as error:
            raise ExtractionError(
                "FEATURE_INPUT_PATH_UNSAFE",
                "Extraction run must be inside the analytics data root",
            ) from error
        if not directory.is_dir():
            raise ExtractionError(
                "FEATURE_INPUT_NOT_FOUND",
                "Extraction run directory does not exist",
            )
        required = {
            "run-manifest.json",
            "dataset-manifest.json",
            "data-quality.json",
            "extraction-summary.json",
            "canonical-demand-events.jsonl",
            "checksums.sha256",
        }
        missing = sorted(name for name in required if not (directory / name).is_file())
        if missing:
            raise ExtractionError(
                "FEATURE_INPUT_INCOMPLETE",
                "Extraction run is missing required evidence",
                {"missing": missing},
            )
        verified = cls._verify_checksums(directory)
        unsigned = sorted(required - {"checksums.sha256"} - verified)
        if unsigned:
            raise ExtractionError(
                "FEATURE_INPUT_CHECKSUMS_INCOMPLETE",
                "Required extraction evidence is missing checksum coverage",
                {"unsigned": unsigned},
            )
        run = _json_object(directory / "run-manifest.json")
        dataset = _json_object(directory / "dataset-manifest.json")
        quality = _json_object(directory / "data-quality.json")
        summary = _json_object(directory / "extraction-summary.json")
        if quality.get("overallStatus") == "FAIL":
            raise ExtractionError(
                "FEATURE_INPUT_QUALITY_FAILED",
                "A failed extraction snapshot cannot be promoted to features",
            )
        if (
            run.get("profile") != config.profile.name
            or run.get("configHash") != config.config_hash
        ):
            raise ExtractionError(
                "FEATURE_INPUT_CONFIG_MISMATCH",
                "Extraction profile/config hash does not match the feature config",
            )
        if dataset.get("datasetVersion") != config.dataset.version:
            raise ExtractionError(
                "FEATURE_INPUT_DATASET_MISMATCH",
                "Extraction dataset version does not match the feature config",
            )
        canonical = summary.get("canonicalSnapshot")
        if not isinstance(canonical, Mapping):
            raise ExtractionError(
                "FEATURE_INPUT_INVALID",
                "Extraction summary lacks canonical snapshot metadata",
            )
        canonical_path = directory / "canonical-demand-events.jsonl"
        expected_sha = str(canonical.get("sha256") or "")
        if file_sha256(canonical_path) != expected_sha:
            raise ExtractionError(
                "FEATURE_INPUT_CHECKSUM_MISMATCH",
                "Canonical snapshot checksum does not match its summary",
            )
        try:
            snapshot_id = UUID(str(canonical["snapshotId"]))
            row_count = int(canonical["rows"])
        except (KeyError, TypeError, ValueError) as error:
            raise ExtractionError(
                "FEATURE_INPUT_INVALID",
                "Canonical snapshot identity/count is invalid",
            ) from error
        return cls(
            run_directory=directory,
            canonical_path=canonical_path,
            source_profile=config.profile.name,
            dataset_version=config.dataset.version,
            config_hash=config.config_hash,
            from_utc=_utc(str(summary.get("extractFromUtc")), "extractFromUtc"),
            cutoff_utc=_utc(str(summary.get("sourceCutoffUtc")), "sourceCutoffUtc"),
            snapshot_id=snapshot_id,
            snapshot_sha256=expected_sha,
            row_count=row_count,
        )

    @staticmethod
    def _verify_checksums(directory: Path) -> set[str]:
        verified: set[str] = set()
        lines = (directory / "checksums.sha256").read_text(
            encoding="utf-8"
        ).splitlines()
        for line in lines:
            parts = line.split("  ", 1)
            if len(parts) != 2:
                raise ExtractionError(
                    "FEATURE_INPUT_CHECKSUMS_INVALID",
                    "Extraction checksum evidence is malformed",
                )
            expected, name = parts
            if (
                not _SHA256.fullmatch(expected)
                or Path(name).name != name
                or name == "checksums.sha256"
                or name in verified
            ):
                raise ExtractionError(
                    "FEATURE_INPUT_CHECKSUMS_INVALID",
                    "Extraction checksum evidence contains an unsafe file name",
                )
            path = directory / name
            if not path.is_file() or file_sha256(path) != expected:
                raise ExtractionError(
                    "FEATURE_INPUT_CHECKSUM_MISMATCH",
                    "Extraction evidence checksum mismatch",
                    {"file": name},
                )
            verified.add(name)
        return verified

    def events(self) -> Iterator[CanonicalInputEvent]:
        observed = 0
        with self.canonical_path.open("r", encoding="utf-8") as source:
            for line_number, line in enumerate(source, 1):
                try:
                    value = json.loads(line)
                    point = value["pickup_wgs84"]
                    event = CanonicalInputEvent(
                        source_profile=str(value["source_profile"]),
                        dataset_version=str(value["dataset_version"]),
                        source_trip_key=str(value["source_trip_key"]),
                        event_time_utc=_utc(
                            value["demand_event_time_utc"],
                            "demand_event_time_utc",
                        ),
                        demand_event_semantics=str(value["demand_event_semantics"]),
                        longitude=float(point["longitude"]),
                        latitude=float(point["latitude"]),
                        service_area_key=(
                            str(value["service_area_key"])
                            if value.get("service_area_key") is not None
                            else None
                        ),
                        source_cutoff_utc=_utc(
                            value["source_cutoff_utc"],
                            "source_cutoff_utc",
                        ),
                        extraction_run_id=UUID(str(value["extraction_run_id"])),
                    )
                except (KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
                    raise ExtractionError(
                        "FEATURE_INPUT_ROW_INVALID",
                        "Canonical snapshot contains an invalid row",
                        {"line": line_number},
                    ) from error
                if (
                    event.source_profile != self.source_profile
                    or event.dataset_version != self.dataset_version
                    or event.source_cutoff_utc != self.cutoff_utc
                    or event.extraction_run_id != self.snapshot_id
                    or not self.from_utc <= event.event_time_utc < self.cutoff_utc
                ):
                    raise ExtractionError(
                        "FEATURE_INPUT_ROW_IDENTITY_MISMATCH",
                        "Canonical row identity or cutoff interval is inconsistent",
                        {"line": line_number},
                    )
                observed += 1
                yield event
        if observed != self.row_count:
            raise ExtractionError(
                "FEATURE_INPUT_ROW_COUNT_MISMATCH",
                "Canonical row count does not match extraction summary",
                {"expected": self.row_count, "actual": observed},
            )
