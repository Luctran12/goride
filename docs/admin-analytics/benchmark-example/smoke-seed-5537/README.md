# Admin Analytics Benchmark Run

This directory is raw evidence produced by the versioned Phase 7 runner.

- Profile: `smoke`
- Seed: `5537`
- Warm-up iterations: `10`
- Measured iterations: `50`
- Query cutoff: `2026-05-02T17:00:00Z`
- Materialized refresh cutoff: `2026-07-30T02:42:00.598028Z`
- Correctness gate: passed before timing

Latency samples measure the backend JDBC query-port operations. SQL and
EXPLAIN files are representative, versioned plan evidence for the same
workload, tables, filters and ranges; some overview plans intentionally
use a metric subset. They are not substituted for raw latency samples.

`summary.json` is derived only from `MEASURED` rows. Warm-up rows remain
in the CSV files but are excluded. P50/P95 use continuous interpolation;
standard deviation is the population value.

This smoke/development artifact is evidence for this environment only.
It is not, by itself, a thesis performance claim.
