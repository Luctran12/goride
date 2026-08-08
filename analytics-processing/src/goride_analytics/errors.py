from __future__ import annotations

from enum import IntEnum
from typing import Any, Mapping


class ExitCode(IntEnum):
    SUCCESS = 0
    CONFIG_INVALID = 2
    DATASET_INVALID = 3
    STAGE_NOT_IMPLEMENTED = 4
    RUN_CONFLICT = 5
    DATA_QUALITY_FAILED = 6
    DATABASE_ERROR = 7
    EXTRACTION_FAILED = 8
    INTERNAL_ERROR = 70


class AnalyticsError(Exception):
    def __init__(
        self,
        code: str,
        message: str,
        exit_code: ExitCode,
        details: Mapping[str, Any] | None = None,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.exit_code = exit_code
        self.details = dict(details or {})


class ConfigurationError(AnalyticsError):
    def __init__(
        self,
        code: str,
        message: str,
        details: Mapping[str, Any] | None = None,
    ) -> None:
        super().__init__(code, message, ExitCode.CONFIG_INVALID, details)


class DatasetValidationError(AnalyticsError):
    def __init__(
        self,
        code: str,
        message: str,
        details: Mapping[str, Any] | None = None,
    ) -> None:
        super().__init__(code, message, ExitCode.DATASET_INVALID, details)


class StageNotImplementedError(AnalyticsError):
    def __init__(self, stage: str) -> None:
        super().__init__(
            "STAGE_NOT_IMPLEMENTED",
            f"Stage '{stage}' is intentionally not implemented in the current phase",
            ExitCode.STAGE_NOT_IMPLEMENTED,
            {"stage": stage, "phase": 3},
        )


class RunConflictError(AnalyticsError):
    def __init__(self, message: str, details: Mapping[str, Any]) -> None:
        super().__init__(
            "RUN_OUTPUT_CONFLICT",
            message,
            ExitCode.RUN_CONFLICT,
            details,
        )


class DataQualityError(AnalyticsError):
    def __init__(self, failed_rules: list[str], run_id: str) -> None:
        super().__init__(
            "DATA_QUALITY_FAILED",
            "One or more FAIL-level data-quality rules were breached",
            ExitCode.DATA_QUALITY_FAILED,
            {"failedRules": sorted(failed_rules), "processingRunId": run_id},
        )


class DatabaseError(AnalyticsError):
    def __init__(
        self,
        code: str,
        message: str,
        details: Mapping[str, Any] | None = None,
    ) -> None:
        super().__init__(code, message, ExitCode.DATABASE_ERROR, details)


class ExtractionError(AnalyticsError):
    def __init__(
        self,
        code: str,
        message: str,
        details: Mapping[str, Any] | None = None,
    ) -> None:
        super().__init__(code, message, ExitCode.EXTRACTION_FAILED, details)
