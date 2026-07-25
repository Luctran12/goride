# GoRide Current Phase

> Last updated: 2026-07-24, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `feature/driver-location-bootstrap`.
- Base develop commit: `eee3359` (`merge: harden trip startup and offer access`).
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Working direction: remove the passenger tracking gap immediately after a driver accepts an offer.

---

## 2. Active Work In Review

Planned commit: `feat: bootstrap driver location after offer accept`.

Scope implemented:
- Store `locationUpdatedAt` with the driver's Redis availability metadata on online/heartbeat updates.
- Read a single online driver's current Redis GEO position only while its status key is still active.
- After `SEARCHING -> ACCEPTED` commits, map that valid availability snapshot to the accepted trip.
- Save the snapshot in `LatestDriverLocationStore` and broadcast `/topic/trip/{tripId}/location` without waiting for the first trip-scoped location update.
- Keep accept successful when Redis cache or WebSocket broadcast is temporarily unavailable; emit structured warning logs for those bootstrap failures.
- Keep the existing FE fallback: `DRIVER_LOCATION_NOT_FOUND` is a temporary waiting state when no valid heartbeat snapshot exists.
- Add HTTP integration coverage asserting passenger REST fallback returns the online driver coordinates immediately after accept.

Validation so far:
- Targeted `RedisDriverAvailabilityStoreTests,TripDriverLocationBootstrapServiceTests,DriverOfferResponseServiceTests,TripLocationTrackingServiceTests`: pass 26 tests.
- Full `./mvnw.cmd test`: 470 tests discovered, 460 passed and 10 integration tests failed during Docker/Testcontainers initialization; no assertion failures.
- `git diff --check`: pass; Git reports Windows CRLF conversion warnings only.
- CodeRabbit CLI: unavailable in PATH (`coderabbit` command not found).

---

## 3. Frontend Contract

- Passenger should call `GET /api/v1/tracking/trips/{tripId}/driver-location` once after receiving `ACCEPTED`, then continue with `/topic/trip/{tripId}/location`.
- The bootstrapped payload uses the latest valid online heartbeat coordinates; `bearing` and `speed` are `null` until the driver sends trip-scoped tracking data.
- If REST still returns `DRIVER_LOCATION_NOT_FOUND`, FE shows a waiting state and retries with debounce. It must not treat that response as trip failure or cancellation.