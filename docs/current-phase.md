# GoRide Current Phase

> Last updated: 2026-07-11, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `feature/payment-sandbox-e2e-uat`.
- Base develop commit: `77f83a4` (`docs: mark service area merged`).
- Latest feature commit: `84735cc` (`feat: add payment sandbox e2e evidence`).
- Latest merged feature on develop: `feature/service-area-zones`.
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Paused WIP: production API docs hardening is stashed as `wip: pause production api docs guardrails` and can be resumed after higher-priority product readiness tasks.
- Working direction: stop adding broad new features; finish production readiness/UAT tasks needed to go product.

---

## 2. Feature Commit

Feature commit: `fix: clarify sandbox session evidence and enforce foreign keys`.

Scope implemented in this follow-up:
- Replace session-level `readyForFrontendExposure` with stable `sessionEvidencePassed`.
- Keep aggregate `PaymentSandboxUatResultResponse.readyForFrontendExposure` as the only FE exposure gate.
- Add regression coverage proving historical session evidence is independent from current provider readiness.
- Add four named `ON DELETE RESTRICT` foreign keys for checkout/success/failure payment ids and tested-by user id.
- Add idempotent constraint creation for compatible pre-existing tables.
- Extend SQL precheck, manifest and verification for the foreign keys.

Validation so far:
- Targeted payment tests passed: `./mvnw.cmd "-Dtest=PaymentSandboxE2eSessionServiceTests,PaymentSandboxUatResultServiceTests,PaymentSandboxUatPlanServiceTests,PaymentControllerTests" test` (23 tests).
- SQL release validator passed: `scripts/validate-db-release.ps1 -ReleasePath db/releases/20260710-payment-sandbox-e2e-sessions`.
- Full `./mvnw.cmd test` passed: 424 tests.
- `git diff --check` passed; final manual review found no blocker in the response contract, regression coverage or SQL release.
- User review completed on 2026-07-11; the follow-up is committed on its feature branch but not merged into `develop`.
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