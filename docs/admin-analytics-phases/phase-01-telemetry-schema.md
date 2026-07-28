# Phase 01 - Persistent Telemetry Schema

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

- At most one open matching run per trip.
- Offer attempt is unique within a run.
- Invalid terminal-state combinations are rejected.
- Snapshot bucket/service-area/vehicle tuple is unique.
- Release validator passes.
- Existing booking and matching tests remain green.
- No matching service writes telemetry yet.

## Validation

```powershell
.\scripts\validate-db-release.ps1 -ReleasePath db\releases\<release-folder>
.\mvnw.cmd -Dtest=<focused-tests> test
git diff --check
```

## Review Gate

Review entity semantics, DDL, rollback limitations and indexes before Phase 02.

