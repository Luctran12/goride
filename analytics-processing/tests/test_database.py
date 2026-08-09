from __future__ import annotations

import unittest

from goride_analytics.database import DatabaseSettings, PostgresProcessingRunRepository
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

    def test_processing_repository_rejects_unknown_run_type_before_sql(self) -> None:
        repository = PostgresProcessingRunRepository(object())
        with self.assertRaises(DatabaseError) as raised:
            repository.start(
                run_id=None,
                run_type="UNSUPPORTED",
                identity=None,
                source_profile="fixture",
                dataset_version="v1",
                source_cutoff=None,
                input_manifest={},
            )
        self.assertEqual(raised.exception.code, "PROCESSING_RUN_TYPE_INVALID")


if __name__ == "__main__":
    unittest.main()
