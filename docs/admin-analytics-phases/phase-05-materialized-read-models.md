# Phase 05 - Materialized Analytical Read Models

## Goal

Add materialized variants only after the direct-query correctness baseline is stable.

## Prerequisites

- Phases 03 and 04 approved.
- Deterministic equivalence fixtures available.

## Scope

- `analytics` database schema.
- Daily trip/revenue view.
- Hourly spatial demand view.
- Hourly supply view.
- Daily matching view.
- Concurrent refresh indexes and scheduler.
- Freshness metadata.
- Direct/materialized adapter switch.

## Planned Commit

```text
feat: add materialized admin analytics read models
```

## Acceptance Criteria

- Results equal direct-query results at the same cutoff.
- Refresh semantics are documented and tested.
- Concurrent reads remain available during refresh.
- API reports `dataFreshnessAt`.
- Refresh failures are observable.
- Release and rollback SQL pass review.

## Review Gate

Do not choose the default query variant until benchmark evidence exists.

