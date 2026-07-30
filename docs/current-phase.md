# GoRide Current Phase

> Last updated: 2026-07-30
> Active branch: `codex/admin-v2`

## Repository Status

- Base development commit: `e6aba60`.
- Latest completed implementation commit: `140c70a`
  (`feat: harden admin analytics API handoff`).
- Latest approved implementation commit: `4060b6b`
  (`feat: add materialized admin analytics read models`).
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

## Completed Phase

Admin Analytics Backend — Phase 5: Materialized Analytical Read Models.

## Implemented Scope

- Added the `analytics` schema, refresh-state table and four materialized
  analytical read models for trips/revenue, spatial demand, supply and matching.
- Added qualifying unique indexes and a scheduler-backed refresh service with a
  PostgreSQL advisory lock.
- The first refresh is non-concurrent; later refreshes use
  `REFRESH MATERIALIZED VIEW CONCURRENTLY` while existing reads remain
  available.
- Added freshness metadata, refresh duration/outcome metrics, persisted failure
  state and safe direct-query fallback.
- Added an internal query router. `DIRECT` remains the default; `MATERIALIZED`
  must be explicitly enabled and is selected only for compatible timezone,
  metadata and temporal boundaries.
- Verified direct/materialized equivalence for global and service-area filters,
  hour/day series, matching metrics and the re-aggregated PostGIS heatmap.
- Added reversible release SQL; rollback removes only Phase 5 objects and
  preserves unrelated objects in a shared `analytics` schema.

## Validation

- Focused Admin Analytics suite: 22 tests passed before the final review
  hardening.
- Materialized PostgreSQL/PostGIS integration suite: 3 tests passed, including
  initial/concurrent refresh, stale snapshot semantics, failure fallback,
  service-area equivalence and rollback preservation.
- Full backend regression suite: 500 tests passed, 0 failures, 0 errors and
  0 skipped.
- Release precheck, apply, verify and rollback SQL executed successfully against
  PostgreSQL/PostGIS 15.
- `git diff --check` passed; only existing Windows LF/CRLF warnings were
  reported.

## Explicitly Out of Scope

- Choosing `MATERIALIZED` as the production default before benchmark evidence.
- Frontend integration and API handoff.
- Benchmark dataset generation and latency reporting.
- Materialized handling for partial-day overview/matching filters or heatmap
  bounding boxes; these requests intentionally fall back to direct SQL to
  preserve exact semantics.

## Review Gate

Phase 5 was approved by the user's request to continue with the next phase on
2026-07-29.

## Completed Phase

Admin Analytics Backend — Phase 6: API Hardening and Frontend Handoff.

## Implementation Status

Implementation is complete and approved by the user's request to implement
Phase 7 on 2026-07-30.

## Implemented Scope

- Finalized OpenAPI 3.1 descriptions, concrete success/error envelopes, examples
  and Bearer JWT security requirements.
- Made units, precision, timezone, freshness, enum, required-property and
  nullable-field behavior explicit.
- Froze API and metric contracts at version 1.0, including empty, partial-data,
  error and guardrail behavior.
- Added an executable six-request Postman collection and frontend integration
  guide.
- Added consumer-contract and backward-compatibility tests for the legacy
  Admin dashboard.

## Validation

- Focused Phase 6 suite: 17 tests passed.
- Full backend regression suite: 503 tests passed, 0 failures, 0 errors and
  0 skipped.
- All relative Markdown links resolve and the Postman collection parses.
- `git diff --check` passed; only existing Windows LF/CRLF warnings were
  reported while staging.
- Manual review found no remaining blocker.

## Explicitly Out of Scope

- React/Vite implementation, chart styling and page layout.
- Dataset generation, performance benchmarks or selecting the default query
  variant.

## Review Gate

Phase 6 is approved.

## Active Feature

Admin Analytics Backend — Phase 7: Reproducible Dataset and Benchmark.

## Planned Scope

- Add deterministic smoke, medium and thesis dataset profiles with fixed seeds.
- Generate valid operational, telemetry, spatial, supply and payment data
  without using real user information.
- Add an opt-in benchmark runner with a direct/materialized correctness gate.
- Separate warm-up and measured iterations and retain every raw sample.
- Capture environment, dataset, refresh, storage and query-plan artifacts.
- Summarize average, P50, P95 and standard deviation from raw CSV samples.
- Commit a small reproducible example; keep large generated runs out of Git.

## Explicitly Out of Scope

- Performance tuning or changing the production query variant.
- Publishing a thesis performance claim before raw artifacts are reviewed.
- Frontend implementation.

## Review Gate

Dataset methodology, raw artifacts and derived summaries require user review
before Phase 8 begins.
