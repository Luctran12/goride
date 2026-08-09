from __future__ import annotations

import argparse
import json
import sys
from typing import Any, Callable, Sequence, TextIO

from .config import load_config
from .errors import AnalyticsError, ConfigurationError, ExitCode
from .extraction.pipeline import parse_utc_boundary, run_extraction
from .features.pipeline import run_feature_build
from .manifest import validate_dataset
from .runs import build_run_identity
from .stages import STAGE_COMMANDS, execute_placeholder
from .structured_logging import JsonLogger


class AnalyticsArgumentParser(argparse.ArgumentParser):
    def error(self, message: str) -> None:
        raise ConfigurationError("CLI_ARGUMENT_INVALID", message)


def build_parser() -> argparse.ArgumentParser:
    parser = AnalyticsArgumentParser(
        prog="goride-analytics",
        description="GoRide demand-forecasting processing CLI",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)
    validate = subparsers.add_parser(
        "validate-config",
        help="Validate profile, manifest, source path and checksum",
    )
    validate.add_argument("--config", required=True)
    extract = subparsers.add_parser(
        "extract",
        help="Create a deterministic canonical snapshot and evaluate quality",
    )
    extract.add_argument("--config", required=True)
    extract.add_argument("--from-utc", required=True)
    extract.add_argument("--cutoff-utc", required=True)
    build_features = subparsers.add_parser(
        "build-features",
        help="Build versioned leakage-safe spatial-temporal features",
    )
    build_features.add_argument("--config", required=True)
    build_features.add_argument("--extraction-run", required=True)
    build_features.add_argument("--cell-size-meters", type=int)
    for stage in (
        stage
        for stage in STAGE_COMMANDS
        if stage not in {"extract", "build-features"}
    ):
        command = subparsers.add_parser(stage, help=f"Phase-gated {stage} stage")
        command.add_argument("--config", required=True)
    return parser


def _success_payload(config, dataset_result) -> dict[str, object]:
    run_preview = build_run_identity(config, "validation")
    dataset = {
        "sourceType": dataset_result.source_type,
        "status": dataset_result.status,
        "datasetName": dataset_result.dataset_name,
        "datasetVersion": dataset_result.dataset_version,
    }
    if dataset_result.source_relative_path is not None:
        dataset.update(
            {
                "sourceRelativePath": dataset_result.source_relative_path,
                "sourceBytes": dataset_result.source_bytes,
                "sha256": dataset_result.sha256,
            }
        )
    return {
        "status": "VALID",
        "profile": config.profile.name,
        "configHash": config.config_hash,
        "dataset": dataset,
        "runIdentityPreview": run_preview.to_dict(),
    }


def main(
    argv: Sequence[str] | None = None,
    *,
    stdout: TextIO | None = None,
    stderr: TextIO | None = None,
    extraction_runner: Callable[..., Any] = run_extraction,
    feature_runner: Callable[..., Any] = run_feature_build,
) -> int:
    stdout = stdout or sys.stdout
    logger = JsonLogger(stderr or sys.stderr)
    try:
        args = build_parser().parse_args(list(argv) if argv is not None else None)
        config = load_config(args.config)
        dataset_result = validate_dataset(config)
        logger.info(
            "analytics_configuration_validated",
            command=args.command,
            profile=config.profile.name,
            configHash=config.config_hash,
            datasetStatus=dataset_result.status,
        )
        outcome = None
        if args.command == "extract":
            outcome = extraction_runner(
                config,
                dataset_result,
                from_utc=parse_utc_boundary(args.from_utc, "from-utc"),
                cutoff_utc=parse_utc_boundary(args.cutoff_utc, "cutoff-utc"),
            )
            logger.info(
                "analytics_extraction_completed",
                artifactRunId=outcome.artifact_run_id,
                processingRunId=str(outcome.processing_run_id),
                qualityStatus=outcome.quality.overall_status,
                snapshotSha256=outcome.snapshot.sha256,
            )
        elif args.command == "build-features":
            outcome = feature_runner(
                config,
                extraction_run=args.extraction_run,
                cell_size_meters=args.cell_size_meters,
            )
            logger.info(
                "analytics_feature_build_completed",
                artifactRunId=outcome.artifact_run_id,
                featureSetVersion=outcome.feature_set_version,
                processingRunId=str(outcome.processing_run_id),
                qualityStatus=outcome.quality.overall_status,
                featureSha256=outcome.artifact.sha256,
            )
        elif args.command != "validate-config":
            execute_placeholder(args.command)
        payload = _success_payload(config, dataset_result)
        if outcome is not None:
            payload = {
                "status": "SUCCEEDED",
                "profile": config.profile.name,
                "configHash": config.config_hash,
                "dataset": {
                    "datasetName": dataset_result.dataset_name,
                    "datasetVersion": dataset_result.dataset_version,
                    "sourceType": dataset_result.source_type,
                },
                **outcome.to_dict(),
            }
        stdout.write(
            json.dumps(
                payload,
                ensure_ascii=False,
                sort_keys=True,
            )
            + "\n"
        )
        stdout.flush()
        return int(ExitCode.SUCCESS)
    except AnalyticsError as error:
        logger.error(
            "analytics_command_failed",
            errorCode=error.code,
            message=error.message,
            details=error.details,
        )
        return int(error.exit_code)
    except Exception as error:  # pragma: no cover - final process boundary
        logger.error(
            "analytics_command_failed",
            errorCode="INTERNAL_ERROR",
            message="Unexpected analytics processing failure",
            errorType=type(error).__name__,
        )
        return int(ExitCode.INTERNAL_ERROR)
