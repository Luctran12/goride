# Phase 01 - Persistent Telemetry Schema

> Status: implemented and ready for review on `codex/admin-v2`.

## Goal

Create the database foundation for matching runs, matching offer events and driver-supply snapshots without changing matching behavior.

## Prerequisites

- Phase 00 approved.
- Metric dictionary and ADR unchanged or explicitly versioned.

## Scope

- Analytics domain enums and JPA entities.
- Repositories and persistence mappings.
- PostgreSQL constraints and indexes.
- Versioned database release folder.
- Repository and schema tests.

## Planned Database Objects

- `matching_runs`
- `matching_offer_events`
- `driver_supply_snapshots`

## Planned Commit

```text
feat: add persistent matching telemetry schema
```

## Acceptance Criteria

- [x] At most one open matching run per trip.
- [x] Offer attempt is unique within a run.
- [x] Invalid terminal-state combinations are rejected.
- [x] Snapshot bucket/service-area/vehicle tuple is unique.
- [x] Release validator passes.
- [x] Existing booking and matching tests remain green.
- [x] No matching service writes telemetry yet.

## Validation

```powershell
.\scripts\validate-db-release.ps1 -ReleasePath db\releases\20260727-admin-analytics-telemetry
.\mvnw.cmd -Dtest=AnalyticsTelemetryDomainTests,AdminAnalyticsTelemetryRepositoryIntegrationTests test
.\mvnw.cmd test
git diff --check
```

## Review Gate

Review entity semantics, DDL, rollback limitations and indexes before Phase 02.
