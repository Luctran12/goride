from __future__ import annotations

from .errors import StageNotImplementedError


STAGE_COMMANDS = ("extract", "build-features", "train", "evaluate", "forecast")


def execute_placeholder(stage: str) -> None:
    if stage not in STAGE_COMMANDS:
        raise ValueError(f"Unknown stage: {stage}")
    raise StageNotImplementedError(stage)
