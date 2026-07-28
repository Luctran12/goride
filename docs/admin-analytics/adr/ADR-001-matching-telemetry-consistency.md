# ADR-001: Matching Telemetry Consistency and Idempotency

Date: 2026-07-28

Status: Accepted

Approval: Phase 0 review approved by the user on 2026-07-28.

## Context

The operational matching flow spans:

- PostgreSQL trip state;
- Redis candidate availability, locks and temporary matching state;
- WebSocket driver notifications;
- scheduled timeout processing;
- booking and driver-availability listeners.

Redis state has TTL and cannot be used as a durable analytical history. The Admin Analytics subsystem needs persistent matching runs and offer events without introducing duplicate records or changing user-visible matching behavior unexpectedly.

The project is a Spring Boot modular monolith with one PostgreSQL database. Kafka and a separate analytics warehouse are outside the core thesis scope.

## Decision

### Durable Source

Matching telemetry is persisted in PostgreSQL:

- `matching_runs`;
- `matching_offer_events`.

Redis remains operational state only.

### Write Model

Telemetry writes are synchronous and idempotent.

They are not best-effort log writes. A code path must not silently send an offer that cannot be represented in persistent telemetry.

### Run Identity

- A trip has at most one open run.
- The database enforces this with a partial unique constraint/index.
- Booking-created and scheduled-dispatch flows open a run when none exists.
- Retry and driver-available triggers continue the current open run.
- Recovery may open a new run only when no run is open and the trip is still eligible for matching; the trigger is `RECOVERY`.

### Offer Identity

- An actual notification attempt has one offer event.
- Idempotency key: `(matching_run_id, attempt_no)`.
- Candidate lock failures are not offers.
- An offer record is persisted before its driver notification is dispatched.

### State Transitions

Allowed offer transition:

```text
OFFERED
  -> ACCEPTED
  -> REJECTED
  -> TIMEOUT
  -> CANCELLED
  -> EXPIRED
```

Each offer can enter one terminal state once.

Allowed run transition:

```text
IN_PROGRESS
  -> MATCHED
  -> NO_DRIVER
  -> CANCELLED
  -> FAILED
```

Each run can enter one terminal state once.

Updates use conditional SQL/entity guards so duplicate listener or scheduler execution becomes a no-op or returns the existing terminal state.

### Transaction Boundaries

- Trip terminal transitions and matching telemetry terminal updates use the same PostgreSQL transaction when both are changed by one service operation.
- Search allocation and `OFFERED` persistence use short `REQUIRES_NEW` transactions so the durable offer commits before Redis trip-matching state is written.
- A tokenized Redis search lock serializes the start/search/offer pipeline per trip; release uses compare-and-delete so an expired lock owner cannot delete a newer lock.
- Driver notification is dispatched only after the `OFFERED` record commits.
- Passenger/status notifications remain after-commit actions.
- Redis cannot participate in the PostgreSQL transaction. Redis mutations require explicit compensation or reconciliation when a database transaction fails.
- Phase 02 must not add a hidden catch-and-ignore path for telemetry errors.

### First Offer Failure

If a candidate lock succeeds but creation of the run/offer telemetry fails:

1. Do not notify the driver.
2. Release the candidate lock.
3. Do not create Redis trip-matching state for that attempt.
4. Emit a telemetry failure metric and structured log.
5. Leave the trip eligible for a later retry/recovery.

After the offer commits, Redis trip-matching state is written. If that operational write fails, the driver is not notified, the candidate lock is released, and the durable open offer remains available to database-backed timeout recovery.

### Accept Failure

If the trip/telemetry database transaction fails:

1. Do not emit passenger success notifications.
2. Reconcile Redis candidate/trip state with the persisted trip state.
3. Allow the client to retry safely.

### Reject and Timeout

- Persist the terminal offer outcome before creating the next offer.
- The next attempt number is monotonic within the run.
- Duplicate timeout processing must not create another offer with the same attempt number.

### Cancellation

Booking cancellation:

- closes the current `OFFERED` event as `CANCELLED`;
- closes the open run as `CANCELLED`;
- releases operational Redis state;
- is idempotent.

### Recovery

A scheduled reconciliation process checks:

- open offers past `expiresAt`;
- open runs whose trip is already terminal;
- trips eligible for matching with no open run;
- counter drift that can be recomputed from offers.

Recovery changes are recorded with normalized reason codes.

### Supply Snapshots

Driver-supply snapshots use upsert semantics keyed by:

```text
(bucket_start, service_area_id, vehicle_type)
```

Missing source data does not generate a zero-count snapshot.

## Consequences

### Positive

- Matching metrics survive Redis expiry and application restarts.
- Offer rates and duration percentiles have an auditable source.
- Duplicate listeners/schedulers do not inflate metrics.
- The design stays within the modular monolith and current database platform.

### Negative

- Matching paths gain database writes and failure handling.
- Redis/PostgreSQL reconciliation remains necessary.
- Synchronous persistence may add small latency before notification.
- Without an outbox, the post-commit notification boundary still needs careful retry handling.

## Alternatives Considered

### Parse application logs

Rejected because logs are not a relational, complete or idempotent source of business events.

### Keep analytics history in Redis

Rejected because TTL, eviction and operational mutation make it unsuitable as the durable source.

### Asynchronous in-memory Spring events only

Rejected because application crashes can lose events.

### Kafka plus event warehouse

Deferred because it exceeds the core thesis and project operational scope.

### Transactional outbox

Deferred as a future reliability enhancement. It can be introduced later without changing metric semantics.

## Implementation Gate

Phase 02 cannot start until this ADR is approved or replaced by a newer ADR.
