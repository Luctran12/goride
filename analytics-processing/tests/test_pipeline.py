from __future__ import annotations

import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

from goride_analytics.config import load_config
from goride_analytics.database import DatabaseMetadata
from goride_analytics.errors import DataQualityError
from goride_analytics.extraction.pipeline import run_extraction
from goride_analytics.manifest import validate_dataset
from goride_analytics.runs import GitState, build_run_identity

from support import create_porto_data_root, porto_row, write_config


class _Connection:
    def close(self) -> None:
        pass


class _Repository:
    def __init__(self) -> None:
        self.started: list[object] = []
        self.quality: list[object] = []
        self.succeeded: list[object] = []
        self.failed: list[object] = []

    def start(self, **kwargs) -> int:
        self.started.append(kwargs)
        return len(self.started)

    def save_quality(self, run_id, results) -> None:
        self.quality.append((run_id, tuple(results)))

    def succeed(self, run_id, **counts) -> None:
        self.succeeded.append((run_id, counts))

    def fail(self, run_id, **details) -> None:
        self.failed.append((run_id, details))


class ExtractionPipelineTests(unittest.TestCase):
    def _environment(self, root: Path) -> dict[str, str]:
        return {
            "TEST_ANALYTICS_ROOT": str(root),
            "ANALYTICS_DATABASE_HOST": "localhost",
            "ANALYTICS_DATABASE_PORT": "5432",
            "ANALYTICS_DATABASE_NAME": "analytics",
            "ANALYTICS_DATABASE_USERNAME": "runner",
            "ANALYTICS_DATABASE_PASSWORD": "not-logged",
        }

    def test_same_cutoff_and_commit_produce_same_snapshot_checksum(self) -> None:
        from_utc = datetime(2013, 7, 1, tzinfo=timezone.utc)
        cutoff = datetime(2013, 7, 2, tzinfo=timezone.utc)
        event_time = int(datetime(2013, 7, 1, 1, tzinfo=timezone.utc).timestamp())
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = create_porto_data_root(base / "data", [porto_row("trip-1", event_time)])
            environment = self._environment(root)
            config = load_config(write_config(base / "profile.yml"))
            dataset = validate_dataset(config, environment)
            repository = _Repository()
            git = GitState("a" * 40, False)
            identities = [
                build_run_identity(
                    config,
                    "extraction",
                    created_at=datetime(2026, 1, 1, 0, index, tzinfo=timezone.utc),
                    git_state=git,
                )
                for index in range(2)
            ]
            with patch(
                "goride_analytics.extraction.pipeline.verify_database",
                return_value=DatabaseMetadata("analytics", "18", "3.6"),
            ):
                outcomes = [
                    run_extraction(
                        config,
                        dataset,
                        from_utc=from_utc,
                        cutoff_utc=cutoff,
                        environment=environment,
                        now=datetime(2026, 1, 1, tzinfo=timezone.utc),
                        identity=identity,
                        connection_factory=lambda *_args, **_kwargs: _Connection(),
                        repository_factory=lambda _connection: repository,
                    )
                    for identity in identities
                ]

        self.assertEqual(outcomes[0].snapshot.sha256, outcomes[1].snapshot.sha256)
        self.assertEqual(outcomes[0].snapshot_id, outcomes[1].snapshot_id)
        self.assertEqual(len(repository.succeeded), 2)
        self.assertEqual(len(repository.failed), 0)

    def test_fail_quality_gate_marks_run_failed_and_raises(self) -> None:
        from_utc = datetime(2013, 7, 1, tzinfo=timezone.utc)
        cutoff = datetime(2013, 7, 2, tzinfo=timezone.utc)
        event_time = int(datetime(2013, 7, 1, 1, tzinfo=timezone.utc).timestamp())
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = create_porto_data_root(
                base / "data",
                [porto_row("duplicate", event_time), porto_row("duplicate", event_time)],
            )
            environment = self._environment(root)
            config = load_config(write_config(base / "profile.yml"))
            dataset = validate_dataset(config, environment)
            repository = _Repository()
            identity = build_run_identity(
                config,
                "extraction",
                created_at=datetime(2026, 1, 1, tzinfo=timezone.utc),
                git_state=GitState("b" * 40, False),
            )
            with patch(
                "goride_analytics.extraction.pipeline.verify_database",
                return_value=DatabaseMetadata("analytics", "18", "3.6"),
            ):
                with self.assertRaises(DataQualityError):
                    run_extraction(
                        config,
                        dataset,
                        from_utc=from_utc,
                        cutoff_utc=cutoff,
                        environment=environment,
                        now=datetime(2026, 1, 1, tzinfo=timezone.utc),
                        identity=identity,
                        connection_factory=lambda *_args, **_kwargs: _Connection(),
                        repository_factory=lambda _connection: repository,
                    )

        self.assertEqual(len(repository.succeeded), 0)
        self.assertEqual(len(repository.failed), 1)
        self.assertEqual(repository.failed[0][1]["error_code"], "DATA_QUALITY_FAILED")


if __name__ == "__main__":
    unittest.main()
