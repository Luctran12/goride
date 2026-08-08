from __future__ import annotations

import unittest

from goride_analytics.database import DatabaseSettings
from goride_analytics.errors import DatabaseError


class DatabaseSettingsTests(unittest.TestCase):
    def test_password_is_required_and_redacted_from_repr(self) -> None:
        environment = {
            "ANALYTICS_DATABASE_HOST": "localhost",
            "ANALYTICS_DATABASE_PORT": "5432",
            "ANALYTICS_DATABASE_NAME": "analytics",
            "ANALYTICS_DATABASE_USERNAME": "runner",
            "ANALYTICS_DATABASE_PASSWORD": "top-secret",
        }
        settings = DatabaseSettings.from_environment(environment)
        self.assertNotIn("top-secret", repr(settings))
        self.assertEqual(settings.port, 5432)

        environment.pop("ANALYTICS_DATABASE_PASSWORD")
        with self.assertRaises(DatabaseError) as raised:
            DatabaseSettings.from_environment(environment)
        self.assertEqual(raised.exception.code, "DATABASE_ENV_MISSING")


if __name__ == "__main__":
    unittest.main()
