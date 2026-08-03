# GoRide Current Phase

> Last updated: 2026-08-03, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `codex/driver-routing-fallback`.
- Base develop commit: `ae3dac2` (`merge admin-v2`).
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Working direction: keep assigned-driver pickup/dropoff navigation usable when the OSRM-compatible provider is disabled, unavailable, or returns invalid route data.

---

## 2. Reviewed Work

Commit: `feat: add driver routing fallback`.

Scope implemented:
- Keep provider route geometry and maneuver steps unchanged when routing succeeds.
- Catch only `ROUTING_PROVIDER_ERROR` and honor `app.routing.fallback-enabled`.
- Return a direct GeoJSON `LineString` from the driver's current GPS to pickup/dropoff when fallback is enabled.
- Estimate fallback distance with Haversine and duration at 25 km/h; values remain at least one meter and one second.
- Add `routeSource=PROVIDER|STRAIGHT_LINE_FALLBACK` so FE can distinguish real navigation from degraded guidance.
- Return `steps=[]` for fallback and log driver/trip/provider failure context.
- Preserve authorization and trip-status errors without fallback.

---

## 3. Frontend Contract

- Call `POST /api/v1/drivers/trips/{tripId}/route` with the driver's current `latitude` and `longitude`.
- When `routeSource=PROVIDER`, draw the returned route and render maneuver steps normally.
- When `routeSource=STRAIGHT_LINE_FALLBACK`, treat geometry as a destination guide only, show degraded routing, hide maneuver UI, and offer external navigation.
- Continue using `destinationType=PICKUP` for `ACCEPTED` and `DROPOFF` for `ARRIVED`/`IN_PROGRESS`.
- If fallback is disabled, provider failure remains `ROUTING_PROVIDER_ERROR` HTTP 502.

---

## 4. Validation

- Targeted `DriverTripRoutingServiceTests` and `DriverTripControllerTests`: pass 10 tests.
- Covers provider success, fallback geometry, zero-distance minimum values, fallback disabled, non-provider error propagation, driver authorization and trip lifecycle rules.
- Full `./mvnw.cmd test`: 562 tests, 3 baseline failures and 18 Docker/Testcontainers initialization errors; routing fallback targeted tests have no failures.
- `git diff --check`: pass; Windows CRLF conversion warnings only.
- Internal review completed; CodeRabbit CLI is unavailable in PATH.
