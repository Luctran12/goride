# Changes in Implementation

> Approved deviations from the original `docs/TDD.md`. New entries should describe the current contract rather than silently changing routes, payloads, states, or UX behavior.

---

## 2026-07-28 - Matching analytics preserves open-ended rematching

- Date: 2026-07-28
- Branch: `codex/admin-v2`
- Affected feature: Admin Analytics Phase 2 matching telemetry
- Approval source: existing reviewed matching behavior recorded in `docs/implementation-log.md`

### TDD expectation

The Admin Analytics implementation plan initially described closing a matching
run as `NO_DRIVER` when candidates or a configured attempt budget were
exhausted.

### Implemented behavior

The current backend keeps the trip in `SEARCHING` after an initial no-candidate
result, driver rejection, or offer timeout when no next driver is immediately
available. Driver-online and heartbeat events may rematch that trip later.
Telemetry therefore keeps the same matching run `IN_PROGRESS`; it closes the
run only when a driver accepts (`MATCHED`) or the booking is cancelled
(`CANCELLED`). `NO_DRIVER` remains in the schema as a reserved outcome but is
not inferred from a temporary lack of Redis candidates.

### Reason

Candidate availability is transient. Treating one empty search as terminal
would regress the approved passenger flow and prevent the existing
driver-available listener from completing a later match.

### Impact

- Retry and driver-available searches accumulate in the same durable run.
- Open runs are excluded from terminal matching success/failure rates.
- Analytics must not interpret a long-running `IN_PROGRESS` run as a
  `NO_DRIVER` outcome.
- A future product decision may add an explicit time or policy threshold that
  closes a run as `NO_DRIVER`; that change will require a separate reviewed
  transition and telemetry test.

## 2026-07-23 - Admin Web v2 uses the current backend contract

- Date: 2026-07-23
- Branch: `codex/admin-v2`
- Affected feature: Admin Web v2 and booking detail timeline
- Approval source: user-provided `PLAN.md` requesting implementation against the current backend

### TDD expectation

- Admin user management uses `/api/v1/admin/users` and a dedicated status patch endpoint.
- Admin summary uses `/api/v1/admin/stats`.
- Driver approval request uses a `status` field.
- Pricing can be updated in place with `PUT /api/v1/admin/pricing/{pricingConfigId}`.
- Trip locations in older examples use mixed coordinate naming and the documented status set does not include scheduled rides.

### Implemented behavior

- Admin user CRUD remains under `/api/users` and is protected by `hasRole('ADMIN')`; updates use the full `PUT /api/users/{id}` contract.
- Dashboard uses `GET /api/v1/admin/dashboard`.
- Driver approval uses `{ "approvalStatus": "APPROVED|REJECTED" }`; `PENDING` is not a valid update action.
- Pricing uses immutable versions: list/create/deactivate through `/api/v1/admin/pricing`, with no update-in-place endpoint.
- Trip locations serialize as `{ "lat": ..., "lng": ..., "address": ... }` and the current status enum includes `SCHEDULED`.
- Booking detail will expose ordered status history through the existing `GET /api/v1/bookings/{tripId}` route rather than adding a second endpoint.

### Reason

The backend has already evolved past the original TDD and these routes/payloads are covered by the current implementation and tests. Admin Web v2 must integrate with the deployed contract instead of reintroducing obsolete endpoints or mutating historical pricing records.

### Impact

- The frontend API client must use the current route and payload names exactly.
- A status-only user action must submit a complete valid `UserUpdateRequest`, so the UI must preserve the existing user fields.
- Status rendering must tolerate current and future enum values instead of hard-coding only the original seven trip states.
- Existing backend consumers remain compatible because the booking timeline change is additive on the detail response.
