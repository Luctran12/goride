# Phase 02 - Matching Telemetry Instrumentation

Status: Implemented and validated; awaiting user review.

## Goal

Persist a complete, idempotent matching history at the actual business transition points.

## Prerequisites

- Phase 01 schema approved and available in tests.

## Scope

- `MatchingTelemetryPort`.
- PostgreSQL telemetry adapter.
- Start/search/offer/accept/reject/timeout/cancel recording.
- Open-run recovery.
- Driver supply snapshot scheduler and Redis read port.
- Failure observability.

## Existing Matching Policy

The approved operational flow keeps a trip in `SEARCHING` when no driver is
immediately available after an initial search, rejection, or timeout. A later
driver-online event may continue the same run. Therefore Phase 02 does not
automatically close these runs as `NO_DRIVER`; they remain `IN_PROGRESS` until
matched or cancelled. `NO_DRIVER` remains a reserved terminal outcome for a
future explicit exhaustion policy.

Likewise, transient PostgreSQL or Redis failures are surfaced through metrics
and exceptions without falsely terminalizing a recoverable trip as `FAILED`.
`FAILED` remains reserved for a future normalized unrecoverable-failure policy.

## Planned Commit

```text
feat: persist matching run and offer telemetry
```

## Acceptance Criteria

- One actual driver notification maps to one `OFFERED` event.
- Accept, reject and timeout transitions are idempotent.
- Retry continues the same run.
- Driver-available matching continues the existing open run.
- Cancellation closes the open offer and run.
- Runs are not falsely closed as `NO_DRIVER` while the trip remains eligible for rematching.
- Missing supply samples are not converted to zero.
- Telemetry write failures are observable.

## Required Scenarios

- Booking -> offer -> accept.
- Reject -> retry -> accept.
- Timeout -> retry -> no driver.
- Cancellation while an offer is open.
- Duplicate listener/scheduler invocation.
- Application recovery with a stale open run.

## Review Gate

Review transactional behavior and operational regressions before adding analytics queries.

## Validation Evidence

- Focused matching, cancellation, failure-compensation and supply tests pass.
- Docker-backed PostgreSQL/PostGIS and Redis integration tests pass.
- Booking -> offer -> accept persists one matched run and one accepted offer.
- Full backend regression suite: 477 tests passed, 0 failures, 0 errors.
- Manual review found and fixed the concurrent-trigger race with a tokenized
  per-trip search lock.
