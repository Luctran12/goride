from __future__ import annotations

import unittest

from goride_analytics.quality import ExtractionStats, build_quality_report


class QualityRuleTests(unittest.TestCase):
    def test_fail_and_warn_rules_expose_counts_compatible_with_database(self) -> None:
        stats = ExtractionStats(
            schema_breaches=1,
            future_event_breaches=1,
            expected_supply_buckets=4,
            observed_supply_buckets=3,
        )
        report = build_quality_report(
            stats,
            checksum_verified=True,
            include_missing_trajectory_rule=False,
            include_supply_coverage_rule=True,
        )
        by_code = {result.rule_code: result for result in report.results}

        self.assertEqual(report.overall_status, "FAIL")
        self.assertEqual(by_code["DQ_SCHEMA"].records_checked, 1)
        self.assertEqual(by_code["DQ_FUTURE_EVENT"].records_checked, 1)
        self.assertEqual(by_code["DQ_SUPPLY_COVERAGE"].result_status, "WARN")
        self.assertEqual(by_code["DQ_SUPPLY_COVERAGE"].metric_value, 0.75)
        self.assertTrue(
            all(item.records_breached <= item.records_checked for item in report.results)
        )

    def test_clean_archive_is_pass(self) -> None:
        stats = ExtractionStats(rows_scanned=2, rows_in_interval=2, accepted_rows=2)
        report = build_quality_report(
            stats,
            checksum_verified=True,
            include_missing_trajectory_rule=True,
            include_supply_coverage_rule=False,
        )
        self.assertEqual(report.overall_status, "PASS")
        self.assertEqual(report.failed_rules, [])

    def test_every_phase_three_rule_reports_a_measured_outcome(self) -> None:
        stats = ExtractionStats(
            rows_scanned=6,
            rows_in_interval=4,
            accepted_rows=1,
            schema_breaches=1,
            duplicate_trip_breaches=1,
            missing_event_time_breaches=1,
            future_event_breaches=1,
            invalid_pickup_breaches=1,
            missing_trajectory_warnings=1,
            expected_supply_buckets=4,
            observed_supply_buckets=2,
        )
        report = build_quality_report(
            stats,
            checksum_verified=False,
            include_missing_trajectory_rule=True,
            include_supply_coverage_rule=True,
        )
        by_code = {item.rule_code: item for item in report.results}
        self.assertEqual(
            set(by_code),
            {
                "DQ_SCHEMA",
                "DQ_CHECKSUM",
                "DQ_DUPLICATE_TRIP",
                "DQ_MISSING_EVENT_TIME",
                "DQ_FUTURE_EVENT",
                "DQ_INVALID_PICKUP",
                "DQ_MISSING_TRAJECTORY",
                "DQ_SUPPLY_COVERAGE",
            },
        )
        self.assertEqual(by_code["DQ_CHECKSUM"].result_status, "FAIL")
        self.assertEqual(by_code["DQ_MISSING_TRAJECTORY"].result_status, "WARN")
        for item in by_code.values():
            self.assertGreaterEqual(item.records_checked, item.records_breached)
            self.assertTrue(item.threshold)


if __name__ == "__main__":
    unittest.main()
