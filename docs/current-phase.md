# GoRide Current Phase

> Last updated: 2026-07-11, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `feature/payment-uat-actor-foreign-key`.
- Base develop commit: `d9b2d98` (`merge: payment sandbox e2e evidence`).
- Latest merged payment feature: `feature/payment-sandbox-e2e-uat`.
- Latest merged feature on develop: `feature/payment-sandbox-e2e-uat`.
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Paused WIP: production API docs hardening is stashed as `wip: pause production api docs guardrails` and can be resumed after higher-priority product readiness tasks.
- Working direction: stop adding broad new features; finish production readiness/UAT tasks needed to go product.

---

## 2. Feature Commit

Feature commit: `fix: enforce payment sandbox uat actor foreign key`.

Scope implemented in this branch:
- Add versioned SQL release `20260711-payment-sandbox-uat-actor-fk`.
- Reject deployment when aggregate UAT evidence contains orphan tested-by user ids.
- Add `fk_payment_sandbox_uat_tested_by_user` with `ON DELETE RESTRICT`.
- Verify exact source/target columns, referenced table, delete action and orphan count.
- Keep rollback data-safe by removing only the constraint.

Validation so far:
- SQL release validator passed: `scripts/validate-db-release.ps1 -ReleasePath db/releases/20260711-payment-sandbox-uat-actor-fk`.
- Full `./mvnw.cmd test` passed: 424 tests on merge base `d9b2d98`.
- `git diff --check` passed; final manual review found no blocker in orphan handling, FK verification, release ordering or rollback safety.
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