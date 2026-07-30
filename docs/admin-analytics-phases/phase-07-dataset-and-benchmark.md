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

- [x] Same seed produces the same row counts and distributions.
- [x] Correctness gate passes before timing.
- [x] Warm-up and measured iterations are separated.
- [x] Direct and materialized variants use identical filters and cutoff.
- [x] Average, P50 and P95 are derived from raw samples.
- [x] Table/index/view size and refresh duration are recorded.
- [x] No performance claim exists without raw evidence.

## Validation Evidence

- Implementation commits: `54dbb62`
  (`test: add reproducible analytics benchmark`) and `aaea493`
  (`test: report benchmark storage overhead`).
- Benchmark utility tests: 6 passed.
- Full backend regression suite: 509 passed, 0 failures, 0 errors, 0 skipped.
- Opt-in PostgreSQL/PostGIS benchmark integration: passed.
- Controlled `smoke` run used seed `5537`, 10 warm-up and 50 measured
  iterations for each of 10 query cases and both variants.
- Direct/materialized correctness hashes matched for all 10 cases before
  timing; all 1,000 measured samples succeeded.
- Independent runs produced the same row counts, distributions and fingerprint
  `68d4bf0c35479d67fabdc1474663b7a3f6ac33bdf57dfe24b59b9dd1a8821b2f`.
- The standalone summarizer reproduced every value in the 20-row statistical
  summary from raw CSV.
- All core artifact checksums passed.

The committed evidence is at
[`benchmark-example/smoke-seed-5537`](../admin-analytics/benchmark-example/smoke-seed-5537/README.md).

## Review Gate

Review methodology and raw artifacts before using results in the thesis.
The committed smoke output is development evidence only; it is not a thesis
performance claim. The `thesis` profile must be run and reviewed separately.
