from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

from .extraction.canonical import utc_text


@dataclass
class ExtractionStats:
    rows_scanned: int = 0
    rows_in_interval: int = 0
    accepted_rows: int = 0
    outside_interval_rows: int = 0
    schema_breaches: int = 0
    duplicate_trip_breaches: int = 0
    missing_event_time_breaches: int = 0
    future_event_breaches: int = 0
    invalid_pickup_breaches: int = 0
    missing_trajectory_warnings: int = 0
    minimum_event_time_utc: datetime | None = None
    maximum_event_time_utc: datetime | None = None
    expected_supply_buckets: int | None = None
    observed_supply_buckets: int | None = None
    details: dict[str, Any] = field(default_factory=dict)

    def observe_event_time(self, value: datetime) -> None:
        if self.minimum_event_time_utc is None or value < self.minimum_event_time_utc:
            self.minimum_event_time_utc = value
        if self.maximum_event_time_utc is None or value > self.maximum_event_time_utc:
            self.maximum_event_time_utc = value


@dataclass(frozen=True)
class QualityResult:
    rule_code: str
    severity: str
    result_status: str
    records_checked: int
    records_breached: int
    metric_value: float | None = None
    threshold: dict[str, Any] = field(default_factory=dict)
    details: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "details": self.details,
            "metricValue": self.metric_value,
            "recordsBreached": self.records_breached,
            "recordsChecked": self.records_checked,
            "resultStatus": self.result_status,
            "ruleCode": self.rule_code,
            "severity": self.severity,
            "threshold": self.threshold,
        }


@dataclass(frozen=True)
class QualityReport:
    overall_status: str
    results: tuple[QualityResult, ...]
    rows_scanned: int
    rows_in_interval: int
    rows_accepted: int
    minimum_event_time_utc: datetime | None
    maximum_event_time_utc: datetime | None

    @property
    def failed_rules(self) -> list[str]:
        return [
            result.rule_code
            for result in self.results
            if result.result_status == "FAIL"
        ]

    def to_dict(self) -> dict[str, Any]:
        return {
            "maximumEventTimeUtc": (
                utc_text(self.maximum_event_time_utc)
                if self.maximum_event_time_utc
                else None
            ),
            "minimumEventTimeUtc": (
                utc_text(self.minimum_event_time_utc)
                if self.minimum_event_time_utc
                else None
            ),
            "overallStatus": self.overall_status,
            "results": [result.to_dict() for result in self.results],
            "rowsAccepted": self.rows_accepted,
            "rowsInInterval": self.rows_in_interval,
            "rowsScanned": self.rows_scanned,
        }


def _status(breaches: int, severity: str) -> str:
    if breaches == 0:
        return "PASS"
    return severity


def build_quality_report(
    stats: ExtractionStats,
    *,
    checksum_verified: bool,
    include_missing_trajectory_rule: bool,
    include_supply_coverage_rule: bool,
) -> QualityReport:
    results: list[QualityResult] = [
        QualityResult(
            "DQ_SCHEMA",
            "FAIL",
            _status(stats.schema_breaches, "FAIL"),
            max(stats.rows_scanned, stats.schema_breaches),
            stats.schema_breaches,
            threshold={"maximumBreaches": 0},
        ),
        QualityResult(
            "DQ_CHECKSUM",
            "FAIL",
            "PASS" if checksum_verified else "FAIL",
            1,
            0 if checksum_verified else 1,
            metric_value=1.0 if checksum_verified else 0.0,
            threshold={"mustMatchManifest": True},
        ),
        QualityResult(
            "DQ_DUPLICATE_TRIP",
            "FAIL",
            _status(stats.duplicate_trip_breaches, "FAIL"),
            stats.rows_in_interval,
            stats.duplicate_trip_breaches,
            threshold={"maximumDuplicates": 0},
        ),
        QualityResult(
            "DQ_MISSING_EVENT_TIME",
            "FAIL",
            _status(stats.missing_event_time_breaches, "FAIL"),
            stats.rows_scanned,
            stats.missing_event_time_breaches,
            threshold={"maximumMissing": 0},
        ),
        QualityResult(
            "DQ_FUTURE_EVENT",
            "FAIL",
            _status(stats.future_event_breaches, "FAIL"),
            stats.accepted_rows + stats.future_event_breaches,
            stats.future_event_breaches,
            threshold={"maximumEventsAtOrAfterCutoff": 0},
        ),
        QualityResult(
            "DQ_INVALID_PICKUP",
            "FAIL",
            _status(stats.invalid_pickup_breaches, "FAIL"),
            stats.rows_in_interval,
            stats.invalid_pickup_breaches,
            threshold={"maximumInvalid": 0, "srid": 4326},
        ),
    ]
    if include_missing_trajectory_rule:
        results.append(
            QualityResult(
                "DQ_MISSING_TRAJECTORY",
                "WARN",
                _status(stats.missing_trajectory_warnings, "WARN"),
                stats.rows_in_interval,
                stats.missing_trajectory_warnings,
                metric_value=(
                    stats.missing_trajectory_warnings / stats.rows_in_interval
                    if stats.rows_in_interval
                    else 0.0
                ),
                threshold={"action": "EXCLUDE_AND_REPORT"},
            )
        )
    if include_supply_coverage_rule:
        expected = stats.expected_supply_buckets or 0
        observed = stats.observed_supply_buckets or 0
        coverage = min(observed / expected, 1.0) if expected else 0.0
        breaches = max(expected - observed, 0)
        results.append(
            QualityResult(
                "DQ_SUPPLY_COVERAGE",
                "WARN",
                _status(breaches, "WARN"),
                expected,
                breaches,
                metric_value=coverage,
                threshold={"minimumCoverageRatio": 1.0},
                details={"observedBuckets": observed},
            )
        )

    if any(result.result_status == "FAIL" for result in results):
        overall_status = "FAIL"
    elif any(result.result_status == "WARN" for result in results):
        overall_status = "WARN"
    else:
        overall_status = "PASS"
    return QualityReport(
        overall_status=overall_status,
        results=tuple(results),
        rows_scanned=stats.rows_scanned,
        rows_in_interval=stats.rows_in_interval,
        rows_accepted=stats.accepted_rows,
        minimum_event_time_utc=stats.minimum_event_time_utc,
        maximum_event_time_utc=stats.maximum_event_time_utc,
    )
