# GoRide Current Phase

> Last updated: 2026-07-23, Asia/Ho_Chi_Minh
>
> Purpose: source of truth before starting or reviewing the next implementation commit.

---

## 1. Repository Status

- Current branch: `codex/admin-v2`.
- Current base: `develop` commit `e6aba60` (`merge: production observability wiring`).
- The branch already existed when Admin Web v2 work started; it is based on the current backend contract on `develop`, not the older `main` snapshot.
- User-owned untracked artifacts under `.codex-tmp/` and `deliverables/` must remain untouched and uncommitted.
- Active feature: Admin Web v2 aligned with the backend contract in the attached `PLAN.md`.

---

## 2. Plan Review Findings

- The repository currently contains the Spring Boot backend only; there is no existing React/Vite admin application to update. Frontend work therefore starts with a new, isolated admin web scaffold after the backend detail contract is ready.
- Driver approval accepts only `APPROVED` or `REJECTED`; `PENDING` exists in the enum but is rejected by `DriverApprovalService` for the approval update endpoint.
- `BookingLocationResponse` serializes coordinates as `lat` and `lng`, not `latitude` and `longitude`.
- The current trip lifecycle also includes `SCHEDULED`; admin filters/charts must not assume the seven older statuses are exhaustive.
- `PUT /api/users/{id}` is a full update contract. A status-only UI action must first retain/load `fullName`, `phone`, `roles`, `status`, and other current values rather than send only `{ "status": ... }`.
- The existing branch/base and API deviations from the original TDD are recorded in `docs/changes-in-implementation.md`.

---

## 3. Active Work In Review

Draft commit ready for user review: `feat: expose booking status history`.

Scope implemented:
- Add a minimal `TripStatusHistoryResponse` DTO.
- Include ordered status history in `GET /api/v1/bookings/{tripId}` only.
- Keep create/list/cancel/admin trip-list mapping free of extra history queries.
- Cover mapping, chronological ordering, actor id, nullable initial status/actor, and access behavior with focused tests.
- Update the implementation log with validation and manual review findings.

Validation completed:
- Focused booking/JSON contract suite: 16 tests passed.
- All non-integration unit/service/controller tests: 432 passed, 0 failures, 0 errors.
- Full suite attempted: 448 tests discovered; 10 integration errors were caused by Testcontainers not finding Docker, and one pre-existing CORS health assertion received 503 because local Redis was unavailable.
- `git diff --check`: passed with only the repository's LF-to-CRLF warnings on Windows.
- Manual review: no blocker found. History is queried only after access authorization and only in booking detail; list/create/cancel/admin-list mappings do not add history queries. Empty detail history serializes as `[]`, while non-detail responses omit the field.

The patch remains uncommitted. User review is required before creating the commit and starting the next commit.

---

## 4. Next Commit After User Approval

Planned scope: scaffold a React + Vite + TypeScript admin app and implement only the shared API/auth/pagination/type foundation.

Later commits, each behind the same review gate:
- Dashboard.
- User management.
- Driver approval.
- Trip monitoring/detail timeline.
- Pricing version management.
- Frontend regression tests and final contract review.
