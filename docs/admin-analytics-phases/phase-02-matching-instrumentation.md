# Phase 02 - Matching Telemetry Instrumentation

## Goal

Persist a complete, idempotent matching history at the actual business transition points.

## Prerequisites

- Phase 01 schema approved and available in tests.

## Scope

- `MatchingTelemetryPort`.
- PostgreSQL telemetry adapter.
- Start/search/offer/accept/reject/timeout/cancel/no-driver/failure recording.
- Open-run recovery.
- Driver supply snapshot scheduler and Redis read port.
- Failure observability.

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
- No-driver and failed runs have normalized reason codes.
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

