# GoRide Current Phase

> Last updated: 2026-07-28
> Active branch: `codex/admin-v2`

## Repository Status

- Base development commit: `e6aba60`.
- Latest approved implementation commit: `510e727` (`feat: persist matching telemetry and driver-supply snapshots`).
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

## Active Feature

Admin Analytics Backend — Phase 3: Direct Analytics Queries and API Baseline.

## Planned Scope

- Add a shared analytics filter with mandatory `[from, to)` range, reporting
  timezone, optional vehicle type and optional service-area filters.
- Implement direct-query overview, demand timeseries, supply timeseries,
  matching performance and matching funnel.
- Return normalized KPI values without requiring frontend recomputation.
- Add request validation, range guardrails, response metadata and OpenAPI
  examples.
- Protect every endpoint with Admin RBAC while preserving the existing
  `/api/v1/admin/dashboard` contract.

## Explicitly Out of Scope

- PostGIS demand heatmap and spatial-cell aggregation.
- Materialized views and refresh scheduling.
- Frontend implementation.
- Benchmark dataset generation.

## Review Gate

Phase 3 must establish a manually verifiable direct-query correctness baseline
before Phase 4 adds spatial demand analytics.
