from __future__ import annotations

import json
import os
import platform
import re
import subprocess
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .config import ProcessingConfig
from .errors import RunConflictError


_SAFE_RUN_TYPE = re.compile(r"^[a-z][a-z-]*$")


@dataclass(frozen=True)
class GitState:
    commit: str
    dirty: bool


@dataclass(frozen=True)
class RunIdentity:
    run_id: str
    run_type: str
    created_at_utc: str
    profile: str
    config_hash: str
    git_commit: str
    git_dirty: bool
    random_seed: int
    python_version: str
    operating_system: str

    def to_dict(self) -> dict[str, Any]:
        return {
            "runId": self.run_id,
            "runType": self.run_type,
            "createdAtUtc": self.created_at_utc,
            "profile": self.profile,
            "configHash": self.config_hash,
            "gitCommit": self.git_commit,
            "gitDirty": self.git_dirty,
            "randomSeed": self.random_seed,
            "pythonVersion": self.python_version,
            "operatingSystem": self.operating_system,
        }


def repository_root() -> Path:
    return Path(__file__).resolve().parents[3]


def read_git_state(root: Path | None = None) -> GitState:
    root = root or repository_root()
    try:
        commit = subprocess.run(
            ["git", "rev-parse", "--verify", "HEAD"],
            cwd=root,
            check=True,
            capture_output=True,
            text=True,
            timeout=5,
        ).stdout.strip()
        status = subprocess.run(
            ["git", "status", "--porcelain", "--untracked-files=no"],
            cwd=root,
            check=True,
            capture_output=True,
            text=True,
            timeout=5,
        ).stdout
        if not commit:
            raise ValueError("empty commit")
        return GitState(commit=commit, dirty=bool(status.strip()))
    except (OSError, subprocess.SubprocessError, ValueError):
        return GitState(commit="unknown", dirty=True)


def build_run_identity(
    config: ProcessingConfig,
    run_type: str,
    *,
    created_at: datetime | None = None,
    git_state: GitState | None = None,
) -> RunIdentity:
    if not _SAFE_RUN_TYPE.fullmatch(run_type):
        raise ValueError("run_type must be a lowercase safe slug")
    created_at = created_at or datetime.now(timezone.utc)
    if created_at.tzinfo is None:
        raise ValueError("created_at must be timezone-aware")
    created_at = created_at.astimezone(timezone.utc)
    git_state = git_state or read_git_state()
    timestamp = created_at.strftime("%Y%m%dT%H%M%S%fZ")
    commit_part = git_state.commit[:12] if git_state.commit != "unknown" else "unknown"
    run_id = (
        f"{timestamp}-{commit_part}-{config.profile.name}-"
        f"{config.config_hash[:12]}"
    )
    return RunIdentity(
        run_id=run_id,
        run_type=run_type,
        created_at_utc=created_at.isoformat().replace("+00:00", "Z"),
        profile=config.profile.name,
        config_hash=config.config_hash,
        git_commit=git_state.commit,
        git_dirty=git_state.dirty,
        random_seed=config.profile.random_seed,
        python_version=platform.python_version(),
        operating_system=platform.platform(),
    )


def allocate_run_directory(data_root: Path, identity: RunIdentity) -> Path:
    root = data_root.expanduser().resolve()
    if not root.is_dir():
        raise RunConflictError(
            "Analytics data root is not a directory",
            {"runId": identity.run_id},
        )
    run_directory = (root / "runs" / identity.run_type / identity.run_id).resolve()
    try:
        run_directory.relative_to(root)
    except ValueError as error:
        raise RunConflictError(
            "Run directory escapes analytics data root",
            {"runId": identity.run_id},
        ) from error
    if run_directory.exists():
        raise RunConflictError(
            "Run directory already exists; evidence will not be overwritten",
            {"runId": identity.run_id},
        )
    run_directory.mkdir(parents=True, exist_ok=False)
    return run_directory


def write_run_manifest(run_directory: Path, identity: RunIdentity) -> Path:
    target = run_directory / "run-manifest.json"
    temporary = run_directory / ".run-manifest.json.tmp"
    if target.exists() or temporary.exists():
        raise RunConflictError(
            "Run manifest already exists; evidence will not be overwritten",
            {"runId": identity.run_id},
        )
    payload = identity.to_dict()
    temporary.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    os.replace(temporary, target)
    return target
