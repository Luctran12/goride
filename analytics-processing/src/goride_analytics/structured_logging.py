from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping, TextIO


_SENSITIVE_KEY_PARTS = ("password", "secret", "token", "credential", "api_key")


def _json_value(value: Any) -> Any:
    if isinstance(value, datetime):
        return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
    if isinstance(value, Path):
        return str(value)
    if isinstance(value, Mapping):
        result: dict[str, Any] = {}
        for key, nested in value.items():
            normalized_key = str(key)
            if any(part in normalized_key.lower() for part in _SENSITIVE_KEY_PARTS):
                result[normalized_key] = "[REDACTED]"
            else:
                result[normalized_key] = _json_value(nested)
        return result
    if isinstance(value, (list, tuple, set)):
        return [_json_value(item) for item in value]
    if value is None or isinstance(value, (str, int, float, bool)):
        return value
    return str(value)


class JsonLogger:
    def __init__(self, stream: TextIO | None = None) -> None:
        self._stream = stream or sys.stderr

    def emit(self, level: str, event: str, **fields: Any) -> None:
        record = {
            "timestamp": datetime.now(timezone.utc),
            "level": level.upper(),
            "event": event,
            **fields,
        }
        self._stream.write(
            json.dumps(_json_value(record), ensure_ascii=False, sort_keys=True)
            + "\n"
        )
        self._stream.flush()

    def info(self, event: str, **fields: Any) -> None:
        self.emit("INFO", event, **fields)

    def error(self, event: str, **fields: Any) -> None:
        self.emit("ERROR", event, **fields)
