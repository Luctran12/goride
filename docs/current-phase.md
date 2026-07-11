# GoRide Current Phase

> Last updated: 2026-07-11, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `feature/redis-rate-limit-store`.
- Base develop commit: `bea0201` (`merge: production api docs guardrails`).
- Latest merged payment feature: `feature/payment-uat-actor-foreign-key`.
- Latest merged feature on develop: `feature/production-api-docs-guardrails`.
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Held WIP: OTLP tracing remains on `feature/otlp-tracing-config` and is not part of this commit.
- Working direction: stop adding broad new features; finish production readiness/UAT tasks needed to go product.

---

## 2. Active Work In Review

Draft commit: `feat: add redis-backed distributed rate limiting`.

Scope implemented in this branch:
- Introduce a conditional `RateLimitStore` abstraction with memory default and Redis provider.
- Use an atomic Redis Lua token bucket with Redis server time, shared quota across replicas, hashed client keys and idle-key TTL.
- Keep existing 429 headers/body unchanged for frontend compatibility.
- Return structured HTTP 503 `RATE_LIMIT_STORE_UNAVAILABLE` with one-second retry guidance when the store fails.
- Require Redis store in production whenever application rate limiting is enabled.
- Cover config defaults, production guardrails, shared/refilled Redis buckets, conditional bean wiring and HTTP 429/503 contracts.

Validation so far:
- Targeted rate-limit/production suite passed: 19 tests, including Redis 7 Testcontainers integration.
- Full Maven suite reached 436 tests: 426 passed and 10 Docker-backed integration tests errored because Testcontainers could not find a valid Docker environment; no assertion failure was reported.
- `git diff --check` and manual code review passed with no blocker in the Redis Lua bucket, conditional wiring, production guardrail or 429/503 contracts.
- User review completed on 2026-07-11; patch is approved for commit and merge.
- CodeRabbit CLI blocked: `coderabbit` is not in PATH; `sh` is unavailable; WSL `bash` failed with E_ACCESSDENIED and curl could not connect to cli.coderabbit.ai.


---

## 3. Next Product-Readiness Priorities

P0:
- Run real MoMo/VNPAY sandbox E2E validation with merchant test accounts and public HTTPS callback URL.
- Use the new sandbox E2E session endpoints to record checkout URL, success/failure callbacks, duplicate replay and freshness rejection evidence.
- Only expose MoMo/VNPAY to real users after aggregate UAT result returns `readyForFrontendExposure=true`.

P1:
- Add remaining provider sandbox E2E coverage to integration/CI flow where secrets/callback infrastructure are available.
- Wire production log/metric dashboards and distributed tracing backend around existing structured logs/Actuator metrics.

P2:
- Provision and UAT Cloudflare R2 uploads in staging.
- Tune surge pricing thresholds with staging demand/supply data.
- Apply service-area SQL release in staging, create real launch-city polygons, and UAT common pickup/dropoff pairs.
- Add analytics/exporting after launch-critical UAT tasks are stable.