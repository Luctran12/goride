from __future__ import annotations

import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

from goride_analytics.config import load_config
from goride_analytics.errors import RunConflictError
from goride_analytics.runs import (
    GitState,
    allocate_run_directory,
    build_run_identity,
    write_run_manifest,
)

from support import write_config


class RunIdentityTests(unittest.TestCase):
    def test_builds_deterministic_identity_for_fixed_inputs(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            config = load_config(write_config(Path(temporary) / "profile.yml"))
            created_at = datetime(2026, 8, 8, 7, 0, tzinfo=timezone.utc)
            git_state = GitState("a" * 40, False)
            first = build_run_identity(
                config, "training", created_at=created_at, git_state=git_state
            )
            second = build_run_identity(
                config, "training", created_at=created_at, git_state=git_state
            )

        self.assertEqual(first, second)
        self.assertIn(
            "20260808T070000000000Z-aaaaaaaaaaaa-test-profile",
            first.run_id,
        )

    def test_allocates_once_and_writes_manifest_without_overwrite(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            config = load_config(write_config(root / "profile.yml"))
            identity = build_run_identity(
                config,
                "training",
                created_at=datetime(2026, 8, 8, tzinfo=timezone.utc),
                git_state=GitState("b" * 40, True),
            )
            run_directory = allocate_run_directory(root, identity)
            manifest_path = write_run_manifest(run_directory, identity)
            payload = json.loads(manifest_path.read_text(encoding="utf-8"))

            self.assertEqual(payload["runId"], identity.run_id)
            self.assertTrue(payload["gitDirty"])
            with self.assertRaises(RunConflictError):
                write_run_manifest(run_directory, identity)
            with self.assertRaises(RunConflictError):
                allocate_run_directory(root, identity)


if __name__ == "__main__":
    unittest.main()
