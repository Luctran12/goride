from __future__ import annotations

import argparse
import json
import sys
from typing import Sequence, TextIO

from .config import load_config
from .errors import AnalyticsError, ConfigurationError, ExitCode
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
    for stage in STAGE_COMMANDS:
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
        if args.command != "validate-config":
            execute_placeholder(args.command)
        stdout.write(
            json.dumps(
                _success_payload(config, dataset_result),
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
