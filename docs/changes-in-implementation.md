# Changes in Implementation

> Approved deviations from the original `docs/TDD.md`. New entries should describe the current contract rather than silently changing routes, payloads, states, or UX behavior.

---

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
