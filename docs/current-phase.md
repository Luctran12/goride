# GoRide Current Phase

> Last updated: 2026-07-10, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `develop`.
- Latest merged feature: `feature/service-area-zones`.
- Latest develop merge commit: `af2583a` (`merge: service area zones`).
- Feature commit merged: `54e8a3e` (`feat: add service area zones`).
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Paused WIP: production API docs hardening is stashed as `wip: pause production api docs guardrails` and can be resumed after higher-priority product readiness tasks.
- Working direction: stop adding broad new features; finish production readiness/UAT tasks needed to go product.

---

## 2. Latest Completed Work

Merged commit: `feat: add service area zones`.

Completed scope:
- `ServiceArea` entity/repository/service with PostGIS polygon SRID 4326 validation.
- Public `GET /api/v1/service-areas` for active polygons.
- Admin `/api/v1/admin/service-areas` list/create/update/deactivate APIs.
- Booking estimate/create validation before pricing/distance; outside-zone and cross-zone cases return `LOCATION_OUT_OF_SERVICE_AREA`.
- Overlapping service areas are resolved by common active area, so valid nested/overlap zones do not reject trips accidentally.
- SQL release folder `db/releases/20260708-service-area-zones` for manual deployment without Flyway.
- Updated `plan.md`, `integrate-plan.md`, `docs/implementation-log.md` and `docs/pland.xlsx`.

Validation recorded before merge:
- Targeted test passed: `./mvnw.cmd "-Dtest=ServiceAreaServiceTests,BookingServiceTests" test` (25 tests).
- SQL release validator passed: `scripts/validate-db-release.ps1 -ReleasePath db/releases/20260708-service-area-zones`.
- Full regression passed: `./mvnw.cmd test` (416 tests).
- `git diff --check`: passed.
- CodeRabbit CLI unavailable locally (`coderabbit` not found in PATH); local review was used.

---

## 3. Next Product-Readiness Priorities

P0:
- Run real MoMo/VNPAY sandbox E2E validation and record admin UAT evidence.
- Verify real webhook success/failure/replay/freshness behavior against provider sandboxes.

P1:
- Add remaining provider sandbox E2E coverage to integration/CI flow.
- Wire production log/metric dashboards and distributed tracing backend around existing structured logs/Actuator metrics.

P2:
- Provision and UAT Cloudflare R2 uploads in staging.
- Tune surge pricing thresholds with staging demand/supply data.
- Apply service-area SQL release in staging, create real launch-city polygons, and UAT common pickup/dropoff pairs.
- Add analytics/exporting after launch-critical UAT tasks are stable.
