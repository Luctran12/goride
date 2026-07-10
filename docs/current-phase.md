# GoRide Current Phase

> Last updated: 2026-07-10, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `feature/service-area-zones`.
- Base branch: `develop` at `10e5159` (`merge: production readiness guardrails`), merged into this feature branch by `merge: develop into service area zones`.
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Paused WIP: production API docs hardening is stashed as `wip: pause production api docs guardrails` and should be resumed after feature completion/review.
- Working direction: finish product features first, starting with service area/multi-city foundation.

---

## 2. Active Work

Active commit under review: `feat: add service area zones`.

Goal:
- Add service area/geofence foundation so the backend can limit booking pickup/dropoff to active launch zones.
- Keep rollout safe: if no active service areas exist, booking estimate/create continues to work as before.
- Provide public polygon read API for FE map hints and admin CRUD/deactivate APIs for operations.

Implemented in this branch:
- `ServiceArea` entity/repository/service with PostGIS polygon SRID 4326 validation.
- Public `GET /api/v1/service-areas` for active polygons.
- Admin `/api/v1/admin/service-areas` list/create/update/deactivate APIs.
- Booking estimate/create validation before pricing/distance; outside-zone and cross-zone cases return `LOCATION_OUT_OF_SERVICE_AREA`.
- Overlapping service areas are resolved by common active area, so valid nested/overlap zones do not reject trips accidentally.
- SQL release folder `db/releases/20260708-service-area-zones` for manual deployment without Flyway.
- Updated `plan.md`, `integrate-plan.md`, `docs/implementation-log.md` and `docs/pland.xlsx`.

Review status:
- Targeted test passed: `./mvnw.cmd "-Dtest=ServiceAreaServiceTests,BookingServiceTests" test` (25 tests).
- SQL release validator passed: `scripts/validate-db-release.ps1 -ReleasePath db/releases/20260708-service-area-zones`.
- Full regression passed: `./mvnw.cmd test` (416 tests).
- Staged `git diff --cached --check`: passed after docs/workbook update.
- CodeRabbit CLI unavailable locally (`coderabbit` not found in PATH).
- User review pending.

---

## 3. Feature Completion Priorities

P0 for this feature:
- Keep service area rollout open when no active zones exist.
- Validate both pickup and dropoff inside one active polygon before fare/distance work.
- Keep FE integration docs clear for public map polygons, admin CRUD and `LOCATION_OUT_OF_SERVICE_AREA` handling.
- Validate SQL release folder and targeted unit coverage.

P1 after merge/UAT:
- Apply the SQL release in staging and create real launch-city polygons.
- Test common pickup/dropoff pairs on real devices.
- Decide whether to add seed/import tooling for larger multi-city rollout.

---

## 4. Next Checkpoint

After user review:
1. Commit `feat: add service area zones`.
2. Merge the feature branch into `develop` if approved.
3. Resume the stashed API docs hardening or continue the next product feature based on priority.
