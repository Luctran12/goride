# GoRide Current Phase

> Last updated: 2026-08-11, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `codex/matching-route-eta-ranking`.
- Base develop commit: `ffe9964` (`merge: routing estimate cache`).
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Working direction: rank a bounded Redis GEO shortlist by provider route ETA without making provider failure block matching.

---

## 2. Reviewed Work

Commit: `feat: rank matching candidates by route eta`.

Scope implemented:
- Request coordinates with the Redis GEO distance result and carry driver latitude/longitude in each matching candidate.
- Keep Redis distance as the deterministic initial order and shortlist boundary.
- Add a provider-only routing estimate boundary so matching never ranks a straight-line fallback as real route ETA.
- Rank at most the configured top N by provider duration, provider distance, Redis distance and driver id.
- Execute bounded ETA calls concurrently through a dedicated no-queue executor.
- Fall back to the original Redis-distance order when any route is unavailable, coordinates are missing or executor capacity is exhausted.
- Keep offer locking, retry, notification and FE contracts unchanged.

---

## 3. Runtime Configuration

- `MATCHING_ROUTE_ETA_ENABLED=false`
- `MATCHING_ROUTE_ETA_CANDIDATE_LIMIT=3`
- `MATCHING_ROUTE_ETA_EXECUTOR_THREADS=6`
- `ROUTING_ENABLED=false`

Route-ETA ranking is opt-in until a controlled OSRM-compatible endpoint is available. Enabling it also requires routing to be enabled. These settings are backend-only; FE continues receiving the same offer payloads.

---

## 4. Validation

- Targeted matching/routing/cache/properties suite: pass 41 tests.
- Spring application context starts with the primary route-ETA strategy and dedicated executor.
- Covers ETA ordering, provider-distance tie break, candidate limit, missing route/coordinates, disabled mode, provider exception, executor rejection and Redis coordinate mapping.
- Full `./mvnw.cmd test`: 597 tests, 3 baseline failures and 18 Docker/Testcontainers initialization errors; route-ETA tests have no failures.
- `git diff --check`: pass.
- User and internal reviews completed; CodeRabbit CLI is unavailable in PATH.
