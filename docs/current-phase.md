# GoRide Current Phase

> Last updated: 2026-07-22, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `feature/trip-surge-schema-default`.
- Base develop commit: `a6610e7`.
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- User-only document: `docs/GoRide_Thesis_Proposal.docx` is untracked and must remain untouched unless explicitly requested.
- Working direction: fix local startup schema compatibility and booking/matching offer-time access warnings before continuing product hardening.

---

## 2. Active Work In Review

Draft commits in this branch:
- `fix: align trip surge schema default`.
- `fix: allow offered driver trip status access`.

Scope implemented:
- Diagnose startup warning where Hibernate `ddl-auto=update` tries to add `trips.fare_surge_multiplier numeric(4,2) not null` to a table that already has rows.
- Align `Trip.fareSurgeMultiplier` mapping with the reviewed SQL release default `numeric(4,2) default 1.00`.
- Add active-offer access for the driver currently holding a Redis matching offer.
- Allow that offered driver to call `GET /api/v1/bookings/{tripId}` and subscribe `/topic/trip/{tripId}/status` before accepting the trip.
- Keep `/topic/trip/{tripId}/messages` and `/topic/trip/{tripId}/location` restricted to actual trip participants/admin until the driver accepts.
- Document FE handling for offer modal hydration and temporary `DRIVER_LOCATION_NOT_FOUND` before first driver location update.

Validation so far:
- Targeted `TripTests`: pass 9 tests.
- Targeted `TripTests,BookingServiceTests,TripTopicSubscriptionAuthorizerTests,OfferedTripAccessServiceTests,SecurityCorsIntegrationTests`: pass 38 tests.
- `git diff --check`: pass; Git reports Windows CRLF conversion warnings only.
- Full `./mvnw.cmd test`: blocked by Testcontainers/Docker access (`permission denied while trying to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`). Non-integration tests reached during the run passed before the Docker-dependent suite failed.
- CodeRabbit CLI: unavailable in PATH (`coderabbit` command not found).

---

## 3. Notes

Startup schema root cause:
- `db/releases/20260707-surge-pricing-rules/apply.sql` already contains a safe SQL path with `NOT NULL DEFAULT 1.00`.
- Local `spring.jpa.hibernate.ddl-auto=update` does not execute release SQL. It generated DDL from the entity annotation instead.
- The previous entity annotation had `nullable=false` but no database default, so PostgreSQL rejected the new NOT NULL column for existing `trips` rows.

Booking/matching log root cause:
- FE opens the driver offer modal before driver accepts, so the driver is not yet `trip.driver` in PostgreSQL.
- Older backend access rules allowed only passenger, assigned driver or admin to read the trip/subcribe trip topics.
- The new rule allows only the current active `offeredDriverId` to read trip detail and subscribe trip status while the offer is active.