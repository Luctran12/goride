# GoRide Current Phase

> Last updated: 2026-08-04, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `codex/routing-estimate-cache`.
- Base develop commit: `caf3cd1` (`merge: gps fare filtering`).
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Working direction: reduce repeated OSRM-compatible estimate calls before route-ETA driver ranking is introduced.

---

## 2. Reviewed Work

Commit: `perf: cache routing estimates`.

Scope implemented:
- Add a Redis-backed `RouteEstimateCache` for successful provider distance/duration responses.
- Build versioned keys from routing profile and pickup/dropoff coordinates rounded to four decimal places.
- Store structured JSON with a configurable five-minute TTL.
- Treat cache read/write failures as misses so Redis cache outages never block estimate or booking flows.
- Evict malformed cache payloads and continue with the routing provider.
- Do not cache straight-line fallback estimates, allowing a recovered provider to be used immediately.
- Skip all cache access while routing is disabled.
- Keep fare estimate and booking API contracts unchanged.

---

## 3. Runtime Configuration

- `ROUTING_ESTIMATE_CACHE_ENABLED=true`
- `ROUTING_ESTIMATE_CACHE_COORDINATE_SCALE=4`
- `ROUTING_ESTIMATE_CACHE_TTL_SECONDS=300`

Coordinate scale accepts values from 3 to 6. These controls are backend-only; FE continues using the existing fare-estimate contract.

---

## 4. Validation

- Targeted routing/cache/properties/Spring context suite: pass 22 tests.
- Covers cache hit, rounded key stability, TTL, malformed payload eviction, disabled cache, Redis outage, provider success, provider fallback and cache write failure.
- Full `./mvnw.cmd test`: 584 tests, 3 baseline failures and 18 Docker/Testcontainers initialization errors; routing cache tests have no failures.
- `git diff --check`: pass; Windows CRLF conversion warnings only.
- User and internal reviews completed; CodeRabbit CLI is unavailable in PATH.
- Next routing task: enrich the Redis GEO top-N candidates with coordinates and rank the shortlist by provider route ETA.
