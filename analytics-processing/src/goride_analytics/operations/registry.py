from __future__ import annotations

import json
import os
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Mapping

from ..config import ProcessingConfig
from ..database import DatabaseSettings, connect_database, verify_database
from ..errors import ConfigurationError, DatabaseError
from .artifact import (
    OperationalModelArtifact,
    analytics_root,
    load_operational_model_artifact,
)


MODEL_VERSION_NAMESPACE = uuid.UUID("c16e42a8-b121-4c63-af62-53d896984378")
APPROVAL_SCOPE = "RESEARCH_DEMONSTRATION"


@dataclass(frozen=True)
class ModelRegistryOutcome:
    model_version_id: uuid.UUID
    model_name: str
    model_version: str
    lifecycle_status: str
    artifact_sha256: str
    approval_scope: str
    idempotent: bool

    def to_dict(self) -> dict[str, object]:
        return {
            "modelRegistry": {
                "approvalScope": self.approval_scope,
                "artifactSha256": self.artifact_sha256,
                "idempotent": self.idempotent,
                "lifecycleStatus": self.lifecycle_status,
                "modelName": self.model_name,
                "modelVersion": self.model_version,
                "modelVersionId": str(self.model_version_id),
                "researchLifecycle": (
                    "CANDIDATE"
                    if self.lifecycle_status == "VALIDATED"
                    else self.lifecycle_status
                ),
            }
        }


class PostgresModelRegistry:
    def __init__(self, connection: Any) -> None:
        self._connection = connection

    def _training_run_id(self, artifact_run_id: str) -> uuid.UUID:
        with self._connection.transaction():
            with self._connection.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT run_id
                    FROM analytics.processing_runs
                    WHERE artifact_run_id = %s
                      AND run_type = 'TRAINING'
                      AND status = 'SUCCEEDED'
                    """,
                    (artifact_run_id,),
                )
                row = cursor.fetchone()
        if row is None:
            raise DatabaseError(
                "MODEL_TRAINING_RUN_MISSING",
                "Successful training processing evidence is required",
                {"artifactRunId": artifact_run_id},
            )
        return row[0]

    @staticmethod
    def _event(
        status: str,
        actor: str,
        reason: str,
        occurred_at: datetime,
    ) -> dict[str, str]:
        return {
            "actor": actor,
            "approvalScope": APPROVAL_SCOPE,
            "occurredAtUtc": occurred_at.isoformat().replace("+00:00", "Z"),
            "reason": reason,
            "status": status,
        }

    def register(
        self,
        config: ProcessingConfig,
        artifact: OperationalModelArtifact,
        *,
        actor: str,
        reason: str,
        now: datetime,
    ) -> ModelRegistryOutcome:
        training_run_id = self._training_run_id(artifact.run_id)
        model_id = uuid.uuid5(
            MODEL_VERSION_NAMESPACE,
            f"{artifact.model_version}:{artifact.model_sha256}",
        )
        manifest = {
            **artifact.training_manifest,
            "approvalScope": APPROVAL_SCOPE,
            "artifactRunId": artifact.run_id,
            "lifecycleEvents": [self._event("VALIDATED", actor, reason, now)],
            "modelCard": artifact.model_card,
        }
        semantics = (
            "TRIP_STARTED_PROXY"
            if config.dataset.source_type == "archive"
            else "REQUEST_CREATED"
        )
        inserted = False
        try:
            with self._connection.transaction():
                with self._connection.cursor() as cursor:
                    cursor.execute(
                        """
                        INSERT INTO analytics.model_versions (
                            model_version_id, model_name, model_version,
                            model_family, lifecycle_status, source_profile,
                            dataset_version, demand_event_semantics,
                            feature_set_version, grid_version, cell_size_meters,
                            bucket_minutes, training_cutoff_utc, training_run_id,
                            artifact_uri, artifact_sha256, hyperparameters,
                            training_manifest, created_at, validated_at
                        ) VALUES (
                            %s, %s, %s, 'GRADIENT_BOOSTED_TREES', 'VALIDATED',
                            %s, %s, %s, %s, %s, %s, %s, %s, %s,
                            %s, %s, %s::JSONB, %s::JSONB, %s, %s
                        )
                        ON CONFLICT (model_name, model_version) DO NOTHING
                        RETURNING model_version_id
                        """,
                        (
                            model_id,
                            artifact.model_name,
                            artifact.model_version,
                            config.profile.name,
                            config.dataset.version,
                            semantics,
                            artifact.feature_set_version,
                            config.spatial.grid_version,
                            artifact.cell_size_meters,
                            config.temporal.bucket_minutes,
                            artifact.training_cutoff_utc,
                            training_run_id,
                            artifact.artifact_uri,
                            artifact.model_sha256,
                            json.dumps(artifact.selected_configuration, sort_keys=True),
                            json.dumps(manifest, sort_keys=True),
                            now,
                            now,
                        ),
                    )
                    inserted = cursor.fetchone() is not None
                    cursor.execute(
                        """
                        SELECT model_version_id, lifecycle_status,
                               artifact_sha256, training_manifest
                        FROM analytics.model_versions
                        WHERE model_name = %s AND model_version = %s
                        """,
                        (artifact.model_name, artifact.model_version),
                    )
                    row = cursor.fetchone()
        except DatabaseError:
            raise
        except Exception as error:
            raise DatabaseError(
                "MODEL_REGISTRATION_FAILED",
                "Could not register the validated model",
            ) from error
        if row is None or str(row[2]) != artifact.model_sha256:
            raise DatabaseError(
                "MODEL_REGISTRATION_CONFLICT",
                "Existing model version does not match the immutable artifact",
            )
        existing_manifest = row[3]
        if isinstance(existing_manifest, str):
            existing_manifest = json.loads(existing_manifest)
        if existing_manifest.get("approvalScope") != APPROVAL_SCOPE:
            raise DatabaseError(
                "MODEL_APPROVAL_SCOPE_MISMATCH",
                "Existing model has an incompatible approval scope",
            )
        return ModelRegistryOutcome(
            model_version_id=row[0],
            model_name=artifact.model_name,
            model_version=artifact.model_version,
            lifecycle_status=str(row[1]),
            artifact_sha256=artifact.model_sha256,
            approval_scope=APPROVAL_SCOPE,
            idempotent=not inserted,
        )

    def transition(
        self,
        model_version: str,
        *,
        target_status: str,
        actor: str,
        reason: str,
        now: datetime,
    ) -> ModelRegistryOutcome:
        transitions = {
            "APPROVED": ("VALIDATED", "approved_at"),
            "REJECTED": ("VALIDATED", None),
            "RETIRED": ("APPROVED", "retired_at"),
        }
        if target_status not in transitions:
            raise ValueError("unsupported lifecycle transition")
        expected, timestamp_column = transitions[target_status]
        try:
            with self._connection.transaction():
                with self._connection.cursor() as cursor:
                    cursor.execute(
                        """
                        SELECT model_version_id, model_name, lifecycle_status,
                               artifact_sha256, training_manifest
                        FROM analytics.model_versions
                        WHERE model_version = %s
                        FOR UPDATE
                        """,
                        (model_version,),
                    )
                    row = cursor.fetchone()
                    if row is None:
                        raise DatabaseError(
                            "MODEL_VERSION_NOT_FOUND",
                            "Model version is not registered",
                            {"modelVersion": model_version},
                        )
                    current = str(row[2])
                    manifest = row[4]
                    if isinstance(manifest, str):
                        manifest = json.loads(manifest)
                    if manifest.get("approvalScope") != APPROVAL_SCOPE:
                        raise DatabaseError(
                            "MODEL_APPROVAL_SCOPE_MISMATCH",
                            "Model is not scoped for research demonstration",
                        )
                    if current == target_status:
                        return ModelRegistryOutcome(
                            row[0], row[1], model_version, current, row[3],
                            APPROVAL_SCOPE, True,
                        )
                    if current != expected:
                        raise DatabaseError(
                            "MODEL_LIFECYCLE_TRANSITION_INVALID",
                            "Model lifecycle transition is not allowed",
                            {"current": current, "target": target_status},
                        )
                    events = list(manifest.get("lifecycleEvents", []))
                    events.append(self._event(target_status, actor, reason, now))
                    manifest["lifecycleEvents"] = events
                    assignments = "lifecycle_status = %s, training_manifest = %s::JSONB"
                    params: list[Any] = [target_status, json.dumps(manifest, sort_keys=True)]
                    if target_status == "REJECTED":
                        assignments += ", validated_at = %s"
                    elif timestamp_column is not None:
                        assignments += f", {timestamp_column} = %s"
                    params.extend([now, row[0]])
                    cursor.execute(
                        f"UPDATE analytics.model_versions SET {assignments} WHERE model_version_id = %s",
                        tuple(params),
                    )
        except DatabaseError:
            raise
        except Exception as error:
            raise DatabaseError(
                "MODEL_LIFECYCLE_UPDATE_FAILED",
                "Could not update model lifecycle",
            ) from error
        return ModelRegistryOutcome(
            row[0], row[1], model_version, target_status, row[3],
            APPROVAL_SCOPE, False,
        )


def register_model(
    config: ProcessingConfig,
    *,
    training_run: str | Path,
    actor: str,
    reason: str,
    environment: Mapping[str, str] | None = None,
    now: datetime | None = None,
    connection_factory: Callable[..., Any] = connect_database,
    repository_factory: Callable[[Any], PostgresModelRegistry] = PostgresModelRegistry,
) -> ModelRegistryOutcome:
    if not actor.strip() or not reason.strip():
        raise ConfigurationError(
            "MODEL_AUDIT_FIELDS_INVALID",
            "Model registration requires non-empty actor and reason",
        )
    environment = environment or os.environ
    now = now or datetime.now(timezone.utc)
    root = analytics_root(config, environment)
    artifact = load_operational_model_artifact(
        config,
        root=root,
        training_run=training_run,
    )
    settings = DatabaseSettings.from_environment(environment)
    connection = connection_factory(settings, read_only=False)
    try:
        verify_database(connection, require_forecast_schema=True)
        return repository_factory(connection).register(
            config,
            artifact,
            actor=actor.strip(),
            reason=reason.strip(),
            now=now,
        )
    finally:
        connection.close()


def transition_model(
    config: ProcessingConfig,
    *,
    model_version: str,
    target_status: str,
    actor: str,
    reason: str,
    environment: Mapping[str, str] | None = None,
    now: datetime | None = None,
    connection_factory: Callable[..., Any] = connect_database,
    repository_factory: Callable[[Any], PostgresModelRegistry] = PostgresModelRegistry,
) -> ModelRegistryOutcome:
    if not actor.strip() or not reason.strip():
        raise ConfigurationError(
            "MODEL_AUDIT_FIELDS_INVALID",
            "Model lifecycle transition requires non-empty actor and reason",
        )
    environment = environment or os.environ
    now = now or datetime.now(timezone.utc)
    settings = DatabaseSettings.from_environment(environment)
    connection = connection_factory(settings, read_only=False)
    try:
        verify_database(connection, require_forecast_schema=True)
        return repository_factory(connection).transition(
            model_version,
            target_status=target_status,
            actor=actor.strip(),
            reason=reason.strip(),
            now=now,
        )
    finally:
        connection.close()
