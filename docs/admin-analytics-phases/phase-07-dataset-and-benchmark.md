# Phase 07 - Reproducible Dataset and Benchmark

## Goal

Produce evidence that can be rerun and traced from thesis tables back to raw artifacts.

## Prerequisites

- Direct and materialized query variants approved.

## Scope

- Deterministic data generator with smoke/medium/thesis profiles.
- Benchmark runner.
- Query plans and buffer statistics.
- Raw CSV/JSON output.
- Environment manifest.
- Result summarization script.

## Implemented Scope

- Versioned `smoke`, `medium` and `thesis` profiles use published baseline seed
  `5537`.
- Set-based PostgreSQL/PostGIS generation creates valid synthetic operational,
  payment, matching, supply and location history without real user data.
- Temporal data includes reporting-timezone morning/evening peaks; spatial data
  includes two hotspots and a distributed cohort.
- The opt-in Testcontainers runner applies both analytics releases, refreshes
  read models twice, verifies 10 direct/materialized cases and stops before
  timing on any mismatch.
- Warm-up and measured samples are retained separately. Direct/materialized
  order alternates deterministically within each iteration.
- Each run records dataset and environment manifests, correctness hashes, raw
  CSV, statistical JSON, refresh/storage evidence, representative SQL,
  `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` and SHA-256 checksums.
- The PowerShell summarizer recomputes mean, continuous P50/P95 and population
  standard deviation from measured CSV rows.

## Reproduction

See
[`benchmarks/admin-analytics/README.md`](../../benchmarks/admin-analytics/README.md)
and the frozen
[`benchmark-protocol.md`](../admin-analytics/benchmark-protocol.md).

## Planned Commit

```text
test: add reproducible analytics benchmark
```

## Acceptance Criteria

- Same seed produces the same row counts and distributions.
- Correctness gate passes before timing.
- Warm-up and measured iterations are separated.
- Direct and materialized variants use identical filters and cutoff.
- Average, P50 and P95 are derived from raw samples.
- Table/index/view size and refresh duration are recorded.
- No performance claim exists without raw evidence.

## Review Gate

Review methodology and raw artifacts before using results in the thesis.
The committed smoke output is development evidence only; it is not a thesis
performance claim. The `thesis` profile must be run and reviewed separately.
