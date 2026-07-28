# GoRide Current Phase

> Last updated: 2026-07-28
> Active branch: `codex/admin-v2`

## Repository Status

- Base development commit: `e6aba60`.
- Latest approved implementation commit: `6f873ef` (`feat: expose booking status history`).
- Latest planning commit: `0c0e080` (`docs: add admin analytics implementation plan`).
- The user explicitly selected `codex/admin-v2` for the Admin Analytics backend work.
- User-owned `.codex-tmp/` and `deliverables/` content must remain untouched and uncommitted.

## Completed Phase

Admin Analytics Backend — Phase 0: Contracts and Architecture.

- The backend roadmap is split into nine independently reviewable phases.
- Metric, API, benchmark and matching-telemetry consistency contracts are documented.
- `completedTrips` throughput is separated from the request-cohort numerator used by `completionRate`.
- Documentation links and unresolved-placeholder checks passed.
- `git diff --check` passed.
- User review: approved on 2026-07-28.

## Active Feature

Admin Analytics Backend — Phase 1: Persistent Telemetry Schema.

## Work In Review

- Completed the release bundle under `db/releases/20260727-admin-analytics-telemetry`.
- Added durable entities and enums for matching runs, offer events, and driver-supply snapshots.
- Added repositories with open-run/open-offer pessimistic-lock queries and snapshot key lookups.
- Enforced one open run per trip, one offer per run/attempt, one accepted offer per run, terminal-state combinations, non-negative counters, and global snapshot uniqueness.
- Kept matching runtime behavior unchanged.

## Validation

- Database release validator: passed.
- Domain tests: 9 passed.
- PostgreSQL/PostGIS repository and release integration tests: 5 passed.
- Focused analytics/matching regression tests: 27 passed.
- Full backend suite: 461 passed, 0 failures, 0 errors.
- `git diff --check`: passed with only Windows LF/CRLF warnings.

## Manual Review

- Release `precheck.sql`, `apply.sql`, and `verify.sql` execute successfully against PostgreSQL/PostGIS 15.
- SQL constraints are stricter than application factories and protect against duplicate listener/scheduler writes.
- Snapshot uniqueness treats a nullable service area as a real global aggregation key through `NULLS NOT DISTINCT`.
- No service currently writes the new tables, so production matching behavior remains unchanged.
- Rollback is safe before Phase 2; after telemetry collection begins it requires an export because it drops analytical history.

## Explicitly Out of Scope

- Writing telemetry from the matching services.
- Redis/SQL consistency handling.
- Analytics controllers or queries.
- Materialized views.

## Review Gate

The Phase 1 patch remains uncommitted for user review.

After approval, create the Phase 1 commit and start Phase 2 matching instrumentation.
