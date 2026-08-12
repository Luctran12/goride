from __future__ import annotations

import io
import json
import unittest

from goride_analytics.structured_logging import JsonLogger


class StructuredLoggingTests(unittest.TestCase):
    def test_redacts_sensitive_keys_recursively(self) -> None:
        output = io.StringIO()
        JsonLogger(output).info(
            "test_event",
            password="plain-text",
            nested={"apiToken": "token-value", "safe": "visible"},
        )
        record = json.loads(output.getvalue())

        self.assertEqual(record["password"], "[REDACTED]")
        self.assertEqual(record["nested"]["apiToken"], "[REDACTED]")
        self.assertEqual(record["nested"]["safe"], "visible")
        self.assertEqual(record["event"], "test_event")


if __name__ == "__main__":
    unittest.main()
