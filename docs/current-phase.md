# GoRide Current Phase

> Last updated: 2026-07-09, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `hardening/product-readiness-guardrails`.
- Base branch: `develop` at `78cc0a9` (`merge: surge pricing rules`).
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Paused WIP: service-area zone feature is stashed as `wip: pause service area zones for product readiness` and should not be resumed until go-product hardening is done.
- Working direction: feature freeze. Do not start new product features unless the user explicitly reopens feature development.

---

## 2. Active Work

Active commit under review: `chore: add production readiness guardrails`.

Goal:
- Make backend safer to deploy by failing startup when `APP_ENV=production/prod` still uses local/dev config.
- Keep the change focused on release hardening, not new product scope.

Implemented in this branch:
- `app.environment=${APP_ENV:local}` in application config.
- Startup validator for production JWT secret, Hibernate DDL mode, upload storage provider, CORS origins/patterns and R2 required config.
- Focused unit tests for local skip, production safe config, unsafe defaults and incomplete R2 config.
- Updated release tracking docs and frontend/devops integration notes.

Review status:
- Targeted test passed: `./mvnw.cmd -Dtest=ProductionReadinessValidatorTests test` (6 tests).
- Context-test repair passed: `./mvnw.cmd clean "-Dtest=SecurityCorsIntegrationTests,RateLimitFilterIntegrationTests,ObservabilityMetricsIntegrationTests,GorideApplicationTests" test` (7 tests).
- Full regression passed: `./mvnw.cmd test` (403 tests).
- CodeRabbit CLI unavailable locally (`coderabbit` not found in PATH).
- User review pending.

---

## 3. Go-Product Priorities

P0 before release candidate:
- Keep full backend regression green before merge/publish.
- Validate production env variables with `APP_ENV=production` in staging.
- Run real MoMo/VNPAY sandbox checkout and callback UAT, then record admin evidence before FE exposure.
- Verify Cloudflare R2 real avatar/document upload and public URL access.
- Verify Firebase push with production credential strategy.

P1 before production scale-out:
- Wire log collector/dashboard/alerts for structured stdout logs, request IDs, rate-limit metrics and Actuator probes.
- Decide whether in-memory rate limit is enough for MVP or move limits to Redis/API gateway.
- Keep SQL release process disciplined because this project intentionally does not use Flyway.

---

## 4. Next Checkpoint

After user review:
1. Commit `chore: add production readiness guardrails`.
2. Merge the hardening branch into `develop` if approved.
3. Re-run/record full regression if any code changes after review.
4. Continue with P0 UAT/hardening tasks, not new features.