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

## Completed Phase

Admin Analytics Backend — Phase 1: Persistent Telemetry Schema.

- Durable release SQL, entities and repositories are complete.
- PostgreSQL constraints protect run, offer and snapshot identities and terminal states.
- Release/repository integration tests passed against PostgreSQL/PostGIS 15.
- Full backend regression suite passed.
- User review: approved on 2026-07-28.

## Active Feature

Admin Analytics Backend — Phase 2: Matching Telemetry Instrumentation.

## Planned Scope

- Add a matching telemetry port and PostgreSQL adapter.
- Open or recover one matching run and record searches/offers at real business transitions.
- Resolve offers and runs on accept, reject, timeout, cancellation, no-driver and unrecoverable failure.
- Preserve retry and driver-available idempotency.
- Add scheduled driver-supply snapshots sourced from Redis.
- Make telemetry failures observable and avoid hidden catch-and-ignore behavior.

## Explicitly Out of Scope

- Analytics query APIs.
- Heatmap aggregation.
- Materialized views.
- Frontend work.

## Review Gate

Phase 2 must be reviewed before Phase 3 adds direct-query analytics APIs.
