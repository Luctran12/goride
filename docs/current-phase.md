# GoRide Current Phase

> Last updated: 2026-07-11, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `feature/production-api-docs-guardrails`.
- Base develop commit: `3ea7317` (`merge: staging readiness smoke gate`).
- Latest merged payment feature: `feature/payment-uat-actor-foreign-key`.
- Latest merged feature on develop: `feature/staging-readiness-smoke`.
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Held WIP: OTLP tracing remains on `feature/otlp-tracing-config` and is not part of this commit.
- Working direction: stop adding broad new features; finish production readiness/UAT tasks needed to go product.

---

## 2. Feature Commit

Feature commit: `chore: enforce production api docs guardrails`.

Scope implemented in this branch:
- Add environment-controlled Springdoc API and Swagger UI flags, enabled by default for local/staging use.
- Reject production startup while either API documentation surface remains enabled.
- Keep existing authentication/rate-limit behavior unchanged; disabled Springdoc endpoints are not registered.
- Add coverage for production-safe config, each independently enabled documentation surface and non-production availability.
- Update frontend/devops integration guidance and project tracking documents.

Validation so far:
- Targeted `ProductionReadinessValidatorTests` passed: 9 tests.
- Full `./mvnw.cmd test` passed: 427 tests, 0 failures, 0 errors.
- `git diff --check` and final manual review passed; no blocker remains in environment detection, independent flags or non-production behavior.
- User review completed 2026-07-11; commit is being created from the reviewed patch.
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