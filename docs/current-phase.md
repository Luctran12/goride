# GoRide Current Phase

> Last updated: 2026-07-11, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `feature/staging-readiness-smoke`.
- Base develop commit: `1596826` (`merge: payment sandbox uat actor foreign key`).
- Latest merged payment feature: `feature/payment-uat-actor-foreign-key`.
- Latest merged feature on develop: `feature/payment-uat-actor-foreign-key`.
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Paused WIP: production API docs hardening is stashed as `wip: pause production api docs guardrails` and can be resumed after higher-priority product readiness tasks.
- Working direction: stop adding broad new features; finish production readiness/UAT tasks needed to go product.

---

## 2. Feature Commit

Feature commit: `chore: add staging readiness smoke gate`.

Scope implemented in this branch:
- Add a PowerShell smoke command for liveness, readiness, app identity, service-area data and payment metadata.
- Add optional admin checks for MoMo/VNPAY readiness, aggregate UAT evidence and public exposure consistency.
- Add strict launch gates for active service areas and online payments with non-zero failure exit codes.
- Export a secret-free JSON report for deployment evidence.
- Add a manually triggered GitHub Actions workflow with a 14-day report artifact.

Validation so far:
- PowerShell syntax validation passed.
- Mock staging happy path passed 13 checks with strict service-area and online-payment gates.
- Missing admin token failure path returned exit code 1 as expected.
- Full `./mvnw.cmd test` passed: 424 tests, 0 failures, 0 errors.
- `git diff --check` and final manual review passed; workflow inputs are isolated through environment variables and no blocker remains.
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