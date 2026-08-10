from __future__ import annotations

import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

from goride_analytics.config import load_config
from goride_analytics.database import DatabaseMetadata
from goride_analytics.extraction.pipeline import run_extraction
from goride_analytics.features.pipeline import run_feature_build
from goride_analytics.errors import ExtractionError
from goride_analytics.features.snapshot import ExtractionSnapshot
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
    def __init__(self):
        self.rows = []

    def upsert(self, rows, *, created_by_run_id):
        values = list(rows)
        self.rows.extend((created_by_run_id, row) for row in values)
        return len(values)


class FeaturePipelineTests(unittest.TestCase):
    def _config(self, root: Path):
        mapping = valid_config_mapping()
        mapping["quality"]["minimum_history_buckets"] = 4
        mapping["features"]["demand_lags"] = [1, 2, 4]
        mapping["features"]["rolling_windows"] = [4]
        mapping["temporal"]["forecast_horizons_minutes"] = [15]
        return load_config(write_config(root / "profile.yml", mapping))

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

    def test_repeated_build_has_stable_parquet_and_cutoff_safe_rows(self) -> None:
        start = datetime(2013, 7, 1, tzinfo=timezone.utc)
        cutoff = datetime(2013, 7, 1, 2, tzinfo=timezone.utc)
        event_times = [
            int(datetime(2013, 7, 1, 0, 5, tzinfo=timezone.utc).timestamp()),
            int(datetime(2013, 7, 1, 1, 20, tzinfo=timezone.utc).timestamp()),
        ]
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            data_root = create_porto_data_root(
                base / "data",
                [porto_row(f"trip-{index}", value) for index, value in enumerate(event_times)],
            )
            config = self._config(base)
            environment = self._environment(data_root)
            dataset = validate_dataset(config, environment)
            extraction_repository = _RunRepository()
            extraction_identity = build_run_identity(
                config,
                "extraction",
                created_at=datetime(2026, 1, 1, tzinfo=timezone.utc),
                git_state=GitState("a" * 40, False),
            )
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
                    identity=extraction_identity,
                    connection_factory=lambda *_args, **_kwargs: _Connection(),
                    repository_factory=lambda _connection: extraction_repository,
                )

            run_repository = _RunRepository()
            feature_repositories = [_FeatureRepository(), _FeatureRepository()]
            identities = [
                build_run_identity(
                    config,
                    "feature-build",
                    created_at=datetime(2026, 1, 2, 0, index, tzinfo=timezone.utc),
                    git_state=GitState("b" * 40, False),
                )
                for index in range(2)
            ]
            relative_extraction = extraction.run_directory.relative_to(data_root)
            outcomes = []
            with patch(
                "goride_analytics.features.pipeline.verify_database",
                return_value=DatabaseMetadata("analytics", "18", "3.6"),
            ):
                for index, identity in enumerate(identities):
                    outcomes.append(
                        run_feature_build(
                            config,
                            extraction_run=relative_extraction,
                            environment=environment,
                            identity=identity,
                            connection_factory=lambda *_args, **_kwargs: _Connection(),
                            repository_factory=lambda _connection: run_repository,
                            feature_repository_factory=(
                                lambda _connection, value=feature_repositories[index]: value
                            ),
                        )
                    )
                artifact_only_identity = build_run_identity(
                    config,
                    "feature-build",
                    created_at=datetime(2026, 1, 2, 1, 0, tzinfo=timezone.utc),
                    git_state=GitState("d" * 40, False),
                )

                def reject_feature_repository(_connection):
                    raise AssertionError(
                        "artifact-only builds must not publish feature rows"
                    )

                artifact_only_outcome = run_feature_build(
                    config,
                    extraction_run=relative_extraction,
                    environment=environment,
                    identity=artifact_only_identity,
                    connection_factory=lambda *_args, **_kwargs: _Connection(),
                    repository_factory=lambda _connection: run_repository,
                    feature_repository_factory=reject_feature_repository,
                    persist_database=False,
                )

            first_rows = [row for _run_id, row in feature_repositories[0].rows]
            first_directory = outcomes[0].run_directory

            self.assertEqual(outcomes[0].artifact.row_count, 6)
            self.assertEqual(outcomes[0].artifact.sha256, outcomes[1].artifact.sha256)
            self.assertEqual(
                outcomes[0].feature_artifact_id,
                outcomes[1].feature_artifact_id,
            )
            self.assertEqual(
                artifact_only_outcome.artifact.row_count,
                outcomes[0].artifact.row_count,
            )
            self.assertEqual(len(run_repository.succeeded), 3)
            self.assertFalse(run_repository.failed)
            self.assertEqual(
                run_repository.started[-1]["input_manifest"]["publicationMode"],
                "PARQUET_ONLY",
            )
            self.assertEqual(
                run_repository.succeeded[-1][1],
                {"rows_read": 2, "rows_written": 6},
            )
            self.assertTrue(first_rows)
            self.assertTrue(
                all(
                    row.max_feature_source_time_utc <= row.inference_cutoff_utc
                    for row in first_rows
                )
            )
            self.assertTrue((first_directory / "feature-dictionary.json").is_file())
            self.assertTrue((first_directory / "feature-manifest.json").is_file())
            self.assertTrue((first_directory / "cell-eligibility.json").is_file())
            self.assertTrue((first_directory / "checksums.sha256").is_file())
            self.assertEqual(len(outcomes[0].artifact.partitions), 1)
            self.assertTrue(
                (
                    first_directory
                    / "demand-features"
                    / "target_month=2013-07"
                    / "part-00000.parquet"
                ).is_file()
            )
            manifest = json.loads(
                (first_directory / "feature-manifest.json").read_text(
                    encoding="utf-8"
                )
            )
            self.assertEqual(manifest["partitionBy"], "target_month_utc")
            self.assertEqual(manifest["rowCount"], 6)

    def test_snapshot_checksum_tampering_is_rejected_before_build(self) -> None:
        start = datetime(2013, 7, 1, tzinfo=timezone.utc)
        cutoff = datetime(2013, 7, 1, 1, tzinfo=timezone.utc)
        event_time = int(datetime(2013, 7, 1, 0, 5, tzinfo=timezone.utc).timestamp())
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            data_root = create_porto_data_root(
                base / "data",
                [porto_row("trip", event_time)],
            )
            config = self._config(base)
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
                        git_state=GitState("c" * 40, False),
                    ),
                    connection_factory=lambda *_args, **_kwargs: _Connection(),
                    repository_factory=lambda _connection: _RunRepository(),
                )
            with (extraction.run_directory / "canonical-demand-events.jsonl").open(
                "a",
                encoding="utf-8",
            ) as output:
                output.write("{}\n")

            with self.assertRaises(ExtractionError) as raised:
                ExtractionSnapshot.load(
                    extraction.run_directory,
                    analytics_root=data_root,
                    config=config,
                )
            self.assertEqual(raised.exception.code, "FEATURE_INPUT_CHECKSUM_MISMATCH")

    def test_snapshot_requires_checksum_coverage_for_every_required_file(self) -> None:
        start = datetime(2013, 7, 1, tzinfo=timezone.utc)
        cutoff = datetime(2013, 7, 1, 1, tzinfo=timezone.utc)
        event_time = int(datetime(2013, 7, 1, 0, 5, tzinfo=timezone.utc).timestamp())
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            data_root = create_porto_data_root(
                base / "data",
                [porto_row("trip", event_time)],
            )
            config = self._config(base)
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
                        git_state=GitState("f" * 40, False),
                    ),
                    connection_factory=lambda *_args, **_kwargs: _Connection(),
                    repository_factory=lambda _connection: _RunRepository(),
                )
            checksums = extraction.run_directory / "checksums.sha256"
            checksums.write_text(
                "\n".join(
                    line
                    for line in checksums.read_text(encoding="utf-8").splitlines()
                    if not line.endswith("  run-manifest.json")
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaises(ExtractionError) as raised:
                ExtractionSnapshot.load(
                    extraction.run_directory,
                    analytics_root=data_root,
                    config=config,
                )
            self.assertEqual(
                raised.exception.code,
                "FEATURE_INPUT_CHECKSUMS_INCOMPLETE",
            )


if __name__ == "__main__":
    unittest.main()
