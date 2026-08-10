from __future__ import annotations

import argparse
import json
import sys
from typing import Any, Callable, Sequence, TextIO

from .config import load_config
from .errors import AnalyticsError, ConfigurationError, ExitCode
from .extraction.pipeline import parse_utc_boundary, run_extraction
from .features.pipeline import run_feature_build
from .features.persistence import run_feature_persistence
from .evaluation.pipeline import run_evaluation
from .manifest import validate_dataset
from .models.pipeline import run_candidate_training
from .operations.backfill import run_actual_backfill
from .operations.forecast import run_forecast
from .operations.registry import register_model, transition_model
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
    build_features.add_argument(
        "--artifact-only",
        action="store_true",
        help="Write verified Parquet evidence without publishing feature rows to PostgreSQL",
    )
    persist_features = subparsers.add_parser(
        "persist-features",
        help="Resume atomic PostgreSQL persistence from a verified feature artifact",
    )
    persist_features.add_argument("--config", required=True)
    persist_features.add_argument("--feature-run", required=True)
    evaluate = subparsers.add_parser(
        "evaluate",
        help="Evaluate immutable forecasting baselines with rolling-origin folds",
    )
    evaluate.add_argument("--config", required=True)
    evaluate.add_argument("--feature-run", required=True)
    train = subparsers.add_parser(
        "train",
        help="Tune and evaluate the frozen gradient-boosted-tree candidate",
    )
    train.add_argument("--config", required=True)
    train.add_argument("--feature-run", required=True)
    train.add_argument("--baseline-run", required=True)
    train.add_argument("--experiment-config", required=True)
    register = subparsers.add_parser(
        "register-model",
        help="Verify and register a Phase 6 candidate model",
    )
    register.add_argument("--config", required=True)
    register.add_argument("--training-run", required=True)
    register.add_argument("--actor", required=True)
    register.add_argument("--reason", required=True)
    for command_name, target_status in (
        ("approve-model", "APPROVED"),
        ("reject-model", "REJECTED"),
        ("retire-model", "RETIRED"),
    ):
        lifecycle = subparsers.add_parser(
            command_name,
            help=f"Transition a registered model to {target_status}",
        )
        lifecycle.add_argument("--config", required=True)
        lifecycle.add_argument("--model-version", required=True)
        lifecycle.add_argument("--actor", required=True)
        lifecycle.add_argument("--reason", required=True)
    forecast = subparsers.add_parser(
        "forecast",
        help="Run checksum-gated scheduled inference",
    )
    forecast.add_argument("--config", required=True)
    forecast.add_argument("--operations-config", required=True)
    forecast.add_argument("--model-version", required=True)
    forecast.add_argument("--inference-cutoff", required=True)
    forecast.add_argument(
        "--purpose",
        choices=("EVALUATION", "PUBLISHED"),
        default="EVALUATION",
    )
    backfill = subparsers.add_parser(
        "backfill-actual",
        help="Backfill actual demand and absolute error after watermark closure",
    )
    backfill.add_argument("--config", required=True)
    backfill.add_argument("--operations-config", required=True)
    backfill.add_argument("--forecast-run", required=True)
    backfill.add_argument("--watermark-utc", required=True)
    for stage in (
        stage
        for stage in STAGE_COMMANDS
        if stage
        not in {
            "extract", "build-features", "persist-features", "evaluate", "train",
            "forecast",
        }
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
    persistence_runner: Callable[..., Any] = run_feature_persistence,
    evaluation_runner: Callable[..., Any] = run_evaluation,
    training_runner: Callable[..., Any] = run_candidate_training,
    registration_runner: Callable[..., Any] = register_model,
    transition_runner: Callable[..., Any] = transition_model,
    forecast_runner: Callable[..., Any] = run_forecast,
    backfill_runner: Callable[..., Any] = run_actual_backfill,
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
                persist_database=not args.artifact_only,
            )
            logger.info(
                "analytics_feature_build_completed",
                artifactRunId=outcome.artifact_run_id,
                featureSetVersion=outcome.feature_set_version,
                processingRunId=str(outcome.processing_run_id),
                qualityStatus=outcome.quality.overall_status,
                featureSha256=outcome.artifact.sha256,
            )
        elif args.command == "persist-features":
            outcome = persistence_runner(config, feature_run=args.feature_run)
            logger.info(
                "analytics_feature_persistence_completed",
                artifactRunId=outcome.artifact_run_id,
                featureSetVersion=outcome.feature_set_version,
                processingRunId=str(outcome.processing_run_id),
                qualityStatus=outcome.quality_status,
                featureSha256=outcome.sha256,
                sourceArtifactRunId=outcome.source_artifact_run_id,
            )
        elif args.command == "evaluate":
            outcome = evaluation_runner(config, feature_run=args.feature_run)
            logger.info(
                "analytics_evaluation_completed",
                artifactRunId=outcome.artifact_run_id,
                featureSetVersion=outcome.feature_set_version,
                processingRunId=str(outcome.processing_run_id),
                qualityStatus=outcome.quality_status,
                rawPredictionRows=outcome.raw_prediction_rows,
                rawPredictionSha256=outcome.raw_prediction_sha256,
                sourceArtifactRunId=outcome.source_artifact_run_id,
            )
        elif args.command == "train":
            outcome = training_runner(
                config,
                feature_run=args.feature_run,
                baseline_run=args.baseline_run,
                experiment_config=args.experiment_config,
            )
            logger.info(
                "analytics_candidate_training_completed",
                artifactRunId=outcome.artifact_run_id,
                baselineArtifactRunId=outcome.baseline_artifact_run_id,
                experimentHash=outcome.experiment_hash,
                modelSha256=outcome.model_sha256,
                modelVersion=outcome.model_version,
                processingRunId=str(outcome.processing_run_id),
                qualityStatus=outcome.quality_status,
                rawPredictionRows=outcome.raw_prediction_rows,
                rawPredictionSha256=outcome.raw_prediction_sha256,
                sourceArtifactRunId=outcome.source_artifact_run_id,
            )
        elif args.command == "register-model":
            outcome = registration_runner(
                config,
                training_run=args.training_run,
                actor=args.actor,
                reason=args.reason,
            )
            logger.info(
                "analytics_model_registered",
                modelVersion=outcome.model_version,
                modelVersionId=str(outcome.model_version_id),
                lifecycleStatus=outcome.lifecycle_status,
                approvalScope=outcome.approval_scope,
                idempotent=outcome.idempotent,
            )
        elif args.command in {"approve-model", "reject-model", "retire-model"}:
            target_status = {
                "approve-model": "APPROVED",
                "reject-model": "REJECTED",
                "retire-model": "RETIRED",
            }[args.command]
            outcome = transition_runner(
                config,
                model_version=args.model_version,
                target_status=target_status,
                actor=args.actor,
                reason=args.reason,
            )
            logger.info(
                "analytics_model_lifecycle_updated",
                modelVersion=outcome.model_version,
                modelVersionId=str(outcome.model_version_id),
                lifecycleStatus=outcome.lifecycle_status,
                approvalScope=outcome.approval_scope,
                idempotent=outcome.idempotent,
            )
        elif args.command == "forecast":
            outcome = forecast_runner(
                config,
                operations_config=args.operations_config,
                model_version=args.model_version,
                inference_cutoff=args.inference_cutoff,
                purpose=args.purpose,
            )
            logger.info(
                "analytics_forecast_completed",
                artifactRunId=outcome.artifact_run_id,
                forecastRunId=str(outcome.forecast_run_id),
                forecastRows=outcome.forecast_rows,
                idempotent=outcome.idempotent,
                modelVersion=outcome.model_version,
                processingRunId=str(outcome.processing_run_id),
                qualityStatus=outcome.quality_status,
            )
        elif args.command == "backfill-actual":
            outcome = backfill_runner(
                config,
                operations_config=args.operations_config,
                forecast_run=args.forecast_run,
                watermark_utc=args.watermark_utc,
            )
            logger.info(
                "analytics_actual_backfill_completed",
                artifactRunId=outcome.artifact_run_id,
                forecastRunId=str(outcome.forecast_run_id),
                eligibleRows=outcome.eligible_rows,
                updatedRows=outcome.updated_rows,
                processingRunId=str(outcome.processing_run_id),
                qualityStatus=outcome.quality_status,
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
