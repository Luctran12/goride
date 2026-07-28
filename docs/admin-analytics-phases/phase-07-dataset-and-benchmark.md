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

