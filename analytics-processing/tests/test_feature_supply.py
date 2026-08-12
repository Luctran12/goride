from __future__ import annotations

import unittest
from datetime import datetime, timedelta, timezone

from goride_analytics.errors import ExtractionError
from goride_analytics.features.supply import SUPPLY_FEATURE_SQL, load_supply_series


class _Cursor:
    def __init__(self, rows):
        self.rows = rows
        self.executed = None
        self.itersize = 0

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        pass

    def execute(self, statement, parameters):
        self.executed = (statement, parameters)

    def __iter__(self):
        return iter(self.rows)


class _Connection:
    def __init__(self, rows):
        self.cursor_value = _Cursor(rows)
        self.rollbacks = 0

    def cursor(self, **_kwargs):
        return self.cursor_value

    def rollback(self):
        self.rollbacks += 1


class FeatureSupplyTests(unittest.TestCase):
    def test_supply_query_is_bounded_and_uses_closed_bucket_samples(self) -> None:
        start = datetime(2026, 1, 1, tzinfo=timezone.utc)
        connection = _Connection([(start, "10", 7, start + timedelta(minutes=15))])
        series = load_supply_series(
            connection,
            from_utc=start,
            cutoff_utc=start + timedelta(hours=1),
            bucket_minutes=15,
        )

        self.assertEqual(series.get(0, "10"), 7)
        self.assertIn("bucket_start >= %s", SUPPLY_FEATURE_SQL)
        self.assertIn("bucket_start < %s", SUPPLY_FEATURE_SQL)
        self.assertIn("MAX(sampled_at)", SUPPLY_FEATURE_SQL)
        self.assertEqual(connection.rollbacks, 1)

    def test_supply_sample_after_bucket_close_fails_leakage_guard(self) -> None:
        start = datetime(2026, 1, 1, tzinfo=timezone.utc)
        connection = _Connection([(start, None, 7, start + timedelta(minutes=16))])

        with self.assertRaises(ExtractionError) as raised:
            load_supply_series(
                connection,
                from_utc=start,
                cutoff_utc=start + timedelta(hours=1),
                bucket_minutes=15,
            )
        self.assertEqual(raised.exception.code, "SUPPLY_SAMPLE_AFTER_BUCKET")
        self.assertEqual(connection.rollbacks, 1)


if __name__ == "__main__":
    unittest.main()
