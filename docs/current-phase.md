# GoRide Current Phase

> Last updated: 2026-07-29
> Active branch: `codex/admin-v2`

## Repository Status

- Base development commit: `e6aba60`.
- Latest approved implementation commit: `05b96e0` (`feat: add PostGIS demand and supply analytics`).
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

## Completed Phase

Admin Analytics Backend — Phase 2: Matching Telemetry Instrumentation.

- Implementation commit: `510e727`.
- Durable matching run/offer transitions and driver-supply snapshots are active.
- Per-trip Redis search lock protects concurrent listener/scheduler execution.
- Database recovery covers durable expired offers when Redis state is missing.
- Full backend regression suite passed with 477 tests.
- User review: approved by request to continue with the next phase.

## Completed Phase

Admin Analytics Backend — Phase 3: Direct Analytics Queries and API Baseline.

## Implemented Scope

- Added a shared analytics filter with mandatory `[from, to)` range, reporting
  timezone, optional vehicle type and optional service-area filters.
- Implemented direct-query overview, demand timeseries, supply timeseries,
  matching performance and matching funnel.
- Returned normalized KPI values without requiring frontend recomputation.
- Added request validation, range guardrails, response metadata and OpenAPI
  examples.
- Protected every endpoint with Admin RBAC while preserving the existing
  `/api/v1/admin/dashboard` contract.

## Validation

- Hand-calculated PostgreSQL/PostGIS fixture tests passed.
- Admin/passenger RBAC and generated OpenAPI path tests passed.
- Full backend regression suite passed with 489 tests, 0 failures and 0 errors.
- `git diff --check` passed; only the existing Windows LF/CRLF warnings were
  reported while staging.

## Explicitly Out of Scope

- PostGIS demand heatmap and spatial-cell aggregation.
- Materialized views and refresh scheduling.
- Frontend implementation.
- Benchmark dataset generation.

## Review Gate

Phase 3 was approved by the user's request to continue with the next phase on
2026-07-29.

## Completed Phase

Admin Analytics Backend — Phase 4: Spatial Demand and Supply Analytics.

## Implemented Scope

- Added bounded PostGIS square-grid aggregation for trip pickup demand.
- Returned EPSG:4326 GeoJSON with stable cell identifiers.
- Enforced the 31-day range, cell-size whitelist, bounding-box and payload
  guardrails.
- Applied time, vehicle, service-area and optional bounding-box filters together.
- Added partial temporal and GiST pickup indexes through a reversible database
  release.
- Verified boundary semantics, demand/supply alignment and spatial query plans.

## Validation

- Projected-cell, boundary, service-area, vehicle, time and bounding-box fixture
  tests passed against PostgreSQL/PostGIS 15.
- Release precheck, apply and verify SQL passed.
- `EXPLAIN ANALYZE` confirmed the temporal and pickup GiST indexes are usable.
- Admin RBAC and generated OpenAPI coverage remain active for the heatmap route.
- Full backend regression suite passed with 493 tests, 0 failures and 0 errors.
- `git diff --check` passed; only Windows LF/CRLF staging warnings were reported.

## Explicitly Out of Scope

- Materialized views and refresh scheduling.
- H3 or another spatial extension.
- Frontend heatmap rendering.
- Benchmark dataset generation.

## Review Gate

Phase 4 was approved by the user's request to continue with the next phase on
2026-07-29.

## Active Feature

Admin Analytics Backend — Phase 5: Materialized Analytical Read Models.

## Planned Scope

- Add the `analytics` schema and four materialized analytical read models.
- Add unique indexes required for concurrent refresh.
- Implement refresh scheduling, freshness state and observable failure paths.
- Add internal direct/materialized selection with safe direct fallback.
- Verify direct/materialized equivalence at one refresh cutoff.

## Explicitly Out of Scope

- Choosing `MATERIALIZED` as the production default before benchmark evidence.
- Frontend integration and API handoff.
- Benchmark dataset generation and latency reporting.

## Review Gate

Phase 5 must prove correctness, refresh availability and freshness semantics
before Phase 6 hardens the frontend-facing contract.
