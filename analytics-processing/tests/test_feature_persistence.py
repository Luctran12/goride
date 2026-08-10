from __future__ import annotations

import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

from goride_analytics.config import load_config
from goride_analytics.database import DatabaseMetadata
from goride_analytics.errors import ExtractionError
from goride_analytics.extraction.pipeline import run_extraction
from goride_analytics.features.persistence import (
    load_resumable_feature_artifact,
    run_feature_persistence,
)
from goride_analytics.features.pipeline import run_feature_build
from goride_analytics.manifest import validate_dataset
from goride_analytics.runs import GitState, build_run_identity

from support import create_porto_data_root, porto_row, valid_config_mapping, write_config


class _Connection:
    class _Transaction:
        def __enter__(self):
            return self

        def __exit__(self, *_args):
            pass

    def transaction(self):
        return self._Transaction()

    def close(self):
        pass


class _RunRepository:
    def __init__(self):
        self.started = []
        self.quality = []
        self.succeeded = []
        self.failed = []

    def start(self, **kwargs):
        self.started.append(kwargs)
        return len(self.started)

    def save_quality(self, run_id, results):
        self.quality.append((run_id, tuple(results)))

    def succeed(self, run_id, **counts):
        self.succeeded.append((run_id, counts))

    def fail(self, run_id, **details):
        self.failed.append((run_id, details))


class _FeatureRepository:
    def __init__(self, *, fail_on_call: int | None = None):
        self.calls = 0
        self.rows = []
        self.fail_on_call = fail_on_call

    def upsert(self, rows, *, created_by_run_id):
        self.calls += 1
        values = list(rows)
        if self.calls == self.fail_on_call:
            raise RuntimeError("injected persistence failure")
        self.rows.extend((created_by_run_id, row) for row in values)
        return len(values)


class FeaturePersistenceTests(unittest.TestCase):
    @staticmethod
    def _environment(data_root: Path):
        return {
            "TEST_ANALYTICS_ROOT": str(data_root),
            "ANALYTICS_DATABASE_HOST": "localhost",
            "ANALYTICS_DATABASE_PORT": "5432",
            "ANALYTICS_DATABASE_NAME": "analytics",
            "ANALYTICS_DATABASE_USERNAME": "runner",
            "ANALYTICS_DATABASE_PASSWORD": "not-logged",
        }

    def _source_artifact(self, base: Path):
        mapping = valid_config_mapping()
        mapping["quality"]["minimum_history_buckets"] = 4
        mapping["features"]["training_demand_coverage"] = 1.0
        mapping["features"]["demand_lags"] = [1, 2, 4]
        mapping["features"]["rolling_windows"] = [4]
        mapping["temporal"]["forecast_horizons_minutes"] = [15]
        config = load_config(write_config(base / "profile.yml", mapping))
        start = datetime(2013, 7, 31, 23, tzinfo=timezone.utc)
        cutoff = datetime(2013, 8, 1, 1, tzinfo=timezone.utc)
        data_root = create_porto_data_root(
            base / "data",
            [
                porto_row(
                    "trip-1",
                    int(datetime(2013, 7, 31, 23, 5, tzinfo=timezone.utc).timestamp()),
                ),
                porto_row(
                    "trip-2",
                    int(datetime(2013, 8, 1, 0, 20, tzinfo=timezone.utc).timestamp()),
                ),
            ],
        )
        environment = self._environment(data_root)
        dataset = validate_dataset(config, environment)
        with patch(
            "goride_analytics.extraction.pipeline.verify_database",
            return_value=DatabaseMetadata("analytics", "18", "3.6"),
        ):
            extraction = run_extraction(
                config,
                dataset,
                from_utc=start,
                cutoff_utc=cutoff,
                environment=environment,
                now=datetime(2026, 1, 1, tzinfo=timezone.utc),
                identity=build_run_identity(
                    config,
                    "extraction",
                    created_at=datetime(2026, 1, 1, tzinfo=timezone.utc),
                    git_state=GitState("a" * 40, False),
                ),
                connection_factory=lambda *_args, **_kwargs: _Connection(),
                repository_factory=lambda _connection: _RunRepository(),
            )
        feature_repository = _FeatureRepository()
        with patch(
            "goride_analytics.features.pipeline.verify_database",
            return_value=DatabaseMetadata("analytics", "18", "3.6"),
        ):
            feature = run_feature_build(
                config,
                extraction_run=extraction.run_directory.relative_to(data_root),
                environment=environment,
                identity=build_run_identity(
                    config,
                    "feature-build",
                    created_at=datetime(2026, 1, 2, tzinfo=timezone.utc),
                    git_state=GitState("b" * 40, False),
                ),
                connection_factory=lambda *_args, **_kwargs: _Connection(),
                repository_factory=lambda _connection: _RunRepository(),
                feature_repository_factory=lambda _connection: feature_repository,
            )
        return config, data_root, environment, feature

    def test_verified_artifact_resumes_all_partitions_and_writes_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            config, data_root, environment, feature = self._source_artifact(
                Path(temporary)
            )
            artifact = load_resumable_feature_artifact(
                config,
                root=data_root,
                feature_run=feature.run_directory.name,
            )
            run_repository = _RunRepository()
            feature_repository = _FeatureRepository()
            with patch(
                "goride_analytics.features.persistence.verify_database",
                return_value=DatabaseMetadata("analytics", "18", "3.6"),
            ):
                outcome = run_feature_persistence(
                    config,
                    feature_run=feature.run_directory.name,
                    environment=environment,
                    identity=build_run_identity(
                        config,
                        "feature-persist",
                        created_at=datetime(2026, 1, 3, tzinfo=timezone.utc),
                        git_state=GitState("c" * 40, False),
                    ),
                    connection_factory=lambda *_args, **_kwargs: _Connection(),
                    repository_factory=lambda _connection: run_repository,
                    feature_repository_factory=lambda _connection: feature_repository,
                )
            persistence_manifest_exists = (
                outcome.run_directory / "persistence-manifest.json"
            ).is_file()
            checksums_exist = (outcome.run_directory / "checksums.sha256").is_file()

        self.assertEqual(len(artifact.partitions), 2)
        self.assertEqual(outcome.row_count, artifact.row_count)
        self.assertEqual(feature_repository.calls, 2)
        self.assertEqual(len(feature_repository.rows), artifact.row_count)
        self.assertEqual(len(run_repository.succeeded), 1)
        self.assertFalse(run_repository.failed)
        self.assertTrue(persistence_manifest_exists)
        self.assertTrue(checksums_exist)

    def test_partition_failure_marks_resume_failed_without_terminal_success(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            config, _data_root, environment, feature = self._source_artifact(
                Path(temporary)
            )
            run_repository = _RunRepository()
            with patch(
                "goride_analytics.features.persistence.verify_database",
                return_value=DatabaseMetadata("analytics", "18", "3.6"),
            ):
                with self.assertRaises(ExtractionError) as raised:
                    run_feature_persistence(
                        config,
                        feature_run=feature.run_directory.name,
                        environment=environment,
                        identity=build_run_identity(
                            config,
                            "feature-persist",
                            created_at=datetime(2026, 1, 4, tzinfo=timezone.utc),
                            git_state=GitState("d" * 40, False),
                        ),
                        connection_factory=lambda *_args, **_kwargs: _Connection(),
                        repository_factory=lambda _connection: run_repository,
                        feature_repository_factory=lambda _connection: _FeatureRepository(
                            fail_on_call=2
                        ),
                    )

        self.assertEqual(raised.exception.code, "FEATURE_PERSIST_UNEXPECTED_FAILURE")
        self.assertFalse(run_repository.succeeded)
        self.assertEqual(len(run_repository.failed), 1)

    def test_tampered_partition_is_rejected_before_database_connection(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            config, data_root, environment, feature = self._source_artifact(
                Path(temporary)
            )
            partition = next(feature.artifact.path.rglob("*.parquet"))
            with partition.open("ab") as output:
                output.write(b"tampered")
            connections = []

            with self.assertRaises(ExtractionError) as raised:
                run_feature_persistence(
                    config,
                    feature_run=feature.run_directory.name,
                    environment=environment,
                    identity=build_run_identity(
                        config,
                        "feature-persist",
                        created_at=datetime(2026, 1, 5, tzinfo=timezone.utc),
                        git_state=GitState("e" * 40, False),
                    ),
                    connection_factory=lambda *_args, **_kwargs: connections.append(1),
                )

        self.assertEqual(
            raised.exception.code, "FEATURE_RESUME_PARTITION_CHECKSUM_MISMATCH"
        )
        self.assertFalse(connections)


if __name__ == "__main__":
    unittest.main()
