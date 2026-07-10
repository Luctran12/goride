# GoRide Current Phase

> Last updated: 2026-07-10, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `feature/payment-sandbox-e2e-uat`.
- Base develop commit: `77f83a4` (`docs: mark service area merged`).
- Latest merged feature on develop: `feature/service-area-zones`.
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Paused WIP: production API docs hardening is stashed as `wip: pause production api docs guardrails` and can be resumed after higher-priority product readiness tasks.
- Working direction: stop adding broad new features; finish production readiness/UAT tasks needed to go product.

---

## 2. Feature Commit

Feature commit: `feat: add payment sandbox e2e evidence`.

Scope implemented in this branch:
- Add `payment_sandbox_e2e_sessions` SQL release folder for manual deployment without Flyway.
- Add `PaymentSandboxE2eSession` entity/repository/service to record each real provider sandbox test session.
- Add admin APIs:
  - `GET /api/v1/payments/providers/sandbox-e2e-sessions`
  - `GET /api/v1/payments/providers/{providerName}/sandbox-e2e-sessions`
  - `POST /api/v1/payments/providers/{providerName}/sandbox-e2e-sessions`
- Validate evidence before a session can be recorded as reliable:
  - `PASSED` requires provider readiness `sandboxReady=true`.
  - checkout evidence requires a payment id and HTTPS checkout URL.
  - success evidence requires matching provider method, `COMPLETED` payment status and matching transaction reference.
  - failure evidence requires matching provider method, `FAILED` payment status and matching transaction reference.
  - replay evidence must reuse one terminal callback transaction reference.
- Sync recorded session evidence back into existing aggregate `payment_sandbox_uat_results`, preserving `readyForFrontendExposure` gating for FE.
- Update payment sandbox UAT plan so admin/devops can see the new session evidence endpoint in backend checks.

Validation so far:
- Targeted payment tests passed: `./mvnw.cmd "-Dtest=PaymentSandboxE2eSessionServiceTests,PaymentSandboxUatResultServiceTests,PaymentSandboxUatPlanServiceTests,PaymentControllerTests" test` (22 tests).
- SQL release validator passed: `scripts/validate-db-release.ps1 -ReleasePath db/releases/20260710-payment-sandbox-e2e-sessions`.
- Full `./mvnw.cmd test` passed: 423 tests.
- `git diff --check` passed; Windows only reports LF/CRLF warnings.
- Manual code review passed with no blocker found.
- User review completed on 2026-07-10; feature is committed on its branch but not merged into `develop`.
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