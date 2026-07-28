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

## Planned Scope

- Complete the versioned release bundle under `db/releases/20260727-admin-analytics-telemetry`.
- Add matching run, matching offer event, and driver supply snapshot enums/entities.
- Add repositories required by the later telemetry adapter and analytics query phases.
- Add entity mapping tests and PostgreSQL repository/constraint integration tests.
- Validate the release SQL and run focused regressions.

## Explicitly Out of Scope

- Writing telemetry from the matching services.
- Redis/SQL consistency handling.
- Analytics controllers or queries.
- Materialized views.

## Review Gate

Phase 1 must be reviewed before Phase 2 instruments the matching runtime.
