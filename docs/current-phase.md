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

## Phase 2 Status

Implementation complete and validated; awaiting user review.

## Planned Scope

- Add a matching telemetry port and PostgreSQL adapter.
- Open or recover one matching run and record searches/offers at real business transitions.
- Resolve offers and runs on accept, reject, timeout and cancellation; preserve
  open rematching instead of inferring `NO_DRIVER` from transient availability.
- Preserve retry and driver-available idempotency.
- Add scheduled driver-supply snapshots sourced from Redis.
- Make telemetry failures observable and avoid hidden catch-and-ignore behavior.

## Explicitly Out of Scope

- Analytics query APIs.
- Heatmap aggregation.
- Materialized views.
- Frontend work.

## Review Gate

Phase 2 must be reviewed before its implementation commit is created and before
Phase 3 adds direct-query analytics APIs.

## Implemented Scope

- Added the matching telemetry port and transactional PostgreSQL adapter.
- Persisted start/search/offer/accept/reject/timeout/cancel transitions.
- Kept retries and driver-available rematching in one run.
- Added a tokenized Redis search lock to serialize matching per trip.
- Added database recovery for expired offers when Redis state is missing.
- Added configurable Redis-backed driver-supply snapshots with idempotent
  PostgreSQL upsert.
- Added structured Micrometer failure counters without catch-and-ignore paths.
- Recorded the existing `SEARCHING` versus `NO_DRIVER` policy in
  `docs/changes-in-implementation.md`.

## Validation

- Focused Phase 2 unit tests: pass.
- PostgreSQL/PostGIS + Redis telemetry repository integration tests: pass.
- Booking -> matching -> routing integration tests: pass.
- Full Maven regression suite: 477 passed, 0 failed, 0 errors.
- `git diff --check`: required before handoff and expected clean.

## Manual Review

- Fixed a DB/Redis ordering race by committing `OFFERED` telemetry before
  writing operational Redis state.
- Fixed duplicate concurrent search accounting with a per-trip distributed lock
  released by tokenized compare-and-delete.
- Verified terminal trip/telemetry transitions share the same PostgreSQL
  transaction.
- No blocker remains for the Phase 2 review gate.

## Known Risks

- The post-commit driver notification gap remains without a transactional
  outbox; this is an accepted ADR limitation and database timeout recovery
  prevents a durable open offer from being lost silently.
- Redis matching state created by an older deployment has no corresponding
  PostgreSQL telemetry and is allowed to expire before normal rematching
  recovery takes over.

## Next Step After Approval

Create the Phase 2 implementation commit, append its hash and review evidence to
`docs/implementation-log.md`, then prepare Phase 3 direct-query analytics APIs.
