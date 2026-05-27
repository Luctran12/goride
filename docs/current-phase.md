# GoRide Current Phase

> Last updated: 2026-05-27
>
> Purpose: this file is the source of truth before starting the next implementation commit, as required by `docs/agent.md`.

---

## 1. Repository Status

- Current branch: `develop`
- Remote tracking branch: `origin/develop`
- `develop` is ahead of `main` with recent backend feature and merge commits; `main` does not yet contain the complete Phase 4-6 implementation.
- Working tree before this file was first populated: clean.
- `docs/changes-in-implementation.md`: not present.

Branching note: `docs/agent.md` requires each feature to start from `main`, but the latest backend implementation currently lives on `develop`. Before starting a new feature branch, choose one of the following approaches:

1. Merge or sync `develop` into `main`, then create the new feature branch from `main`.
2. If the team is using `develop` as the integration branch, create the new feature branch from `develop` and record this process deviation in the implementation log.

---

## 2. Active Feature

- Current active feature: none.
- Next phase from `docs/backend-implementation.md`: **Phase 7 - Admin minimum**.
- Proposed next branch: `codex/admin-minimum`, after confirming the base branch.
- User review status: user confirmation is required before starting Phase 7 implementation and selecting the base branch.

---

## 3. Completed Phases In The Codebase

This status was derived by cross-checking `docs/backend-implementation.md`, `docs/implementation-log.md`, the source packages, and the existing controllers.

### Phase 0 - Project Foundation

Status: completed.

Implemented:
- Spring Boot 3.5.13 and Java 17.
- JPA, Redis, Security, OAuth2 Resource Server, WebSocket, Hibernate Spatial, JTS, and Testcontainers.
- Docker Compose setup for PostgreSQL/PostGIS and Redis.
- Common API response, error handling, security, and configuration foundation.

### Phase 1 - Auth + User

Status: completed.

Implemented:
- Register, login, refresh, and logout flows.
- JWT access tokens and Redis-backed refresh tokens.
- Role-based authorization through Spring Security method annotations.
- Public registration prevents creating `ADMIN` users.

### Phase 2 - Driver Profile

Status: mostly completed; admin approval endpoints are still missing.

Implemented:
- Driver profile entity and APIs for drivers to create and view their own profile.
- Driver online/offline status updates, including approval validation before going online.
- Redis-backed driver availability store.

Missing from the specification:
- Admin approve/reject driver endpoint: `/api/v1/admin/drivers/{driverId}/approval`.

### Phase 3 - Pricing + Booking

Status: completed.

Implemented:
- Pricing configuration, fare estimation, and booking creation.
- Trip state machine and trip status history.
- Server-side fare recalculation; the backend does not trust client-provided `estimatedFare`.
- `BookingCreatedEvent` publication after transaction commit.

### Phase 4 - Matching

Status: completed for MVP.

Implemented:
- Redis GEO nearest-driver candidate lookup.
- Candidate locking through Redis.
- WebSocket driver offer dispatch through user queues.
- Driver accept/reject API.
- Timeout scheduler, up to 3 matching attempts, and `NO_DRIVER` handling.
- Realtime passenger notifications for `ACCEPTED` and `NO_DRIVER`.

### Phase 5 - Realtime Tracking

Status: completed for MVP.

Implemented:
- WebSocket endpoint `/ws`.
- Driver location send destination `/app/driver.location`.
- PostgreSQL trip location history.
- Redis cache for the latest driver location.
- Location topic broadcast and REST fallback endpoint.

Still missing or requiring hardening:
- STOMP `CONNECT` JWT authentication and subscription authorization were not clearly visible in the current source scan.
- Heartbeat and offline-timeout flow is not documented in the implementation log.

### Phase 6 - Payment + Rating

Status: completed for MVP.

Implemented:
- Final fare calculation on trip completion from location history, with estimated-distance fallback when tracking points are insufficient.
- Pending payment creation when a trip is completed.
- Cash payment confirmation endpoint for drivers.
- Payment-completed notification and Redis driver status reset to `AVAILABLE`.
- Passenger-to-driver rating after completed trips.
- Transactional update of driver average rating and total rating count.

Remaining items noted in the latest implementation log:
- Public driver rating list API: `GET /api/v1/drivers/{driverId}/ratings`.
- Redis `driver:{id}:meta.rating` synchronization when an online driver's rating changes.
- Admin/statistics features based on completed payments and ratings.

---

## 4. Next Pending Phase

### Phase 7 - Admin Minimum

Status: not started.

Required endpoints from `docs/backend-implementation.md`:

- `GET /api/v1/admin/users` - list/filter users by role and status.
- `PATCH /api/v1/admin/users/{userId}/status` - update user status to `ACTIVE` or `SUSPENDED`.
- `GET /api/v1/admin/drivers/pending` - list driver profiles pending review.
- `PATCH /api/v1/admin/drivers/{driverId}/approval` - approve or reject a driver profile.
- `GET /api/v1/admin/trips` - list/filter trips by status and date.
- `GET /api/v1/admin/stats` - provide basic demo statistics.

Recommended first commit scope:

1. Create the required admin package, controller, service, and DTOs.
2. Implement user list/filter and user status update.
3. Add focused service tests for authorization and business rules.
4. Run `./mvnw.cmd test`.
5. Update `docs/implementation-log.md` after the commit.

---

## 5. Next Checkpoint

Before writing implementation code:

- Confirm the base branch: sync `develop` into `main`, or continue branching from `develop`.
- Confirm the first Phase 7 commit scope; recommended starting point is Admin Users API.
- Create a dedicated feature branch, proposed as `codex/admin-minimum`.
- After the commit: perform a review pass, update `docs/implementation-log.md`, update this file again, then wait for user review.
