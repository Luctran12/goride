# GoRide Current Phase

> Last updated: 2026-07-11, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `feature/payment-sandbox-e2e-automation`.
- Base develop commit: `aed76ed` (`merge: redis-backed distributed rate limiting`).
- Latest merged payment feature: `feature/payment-uat-actor-foreign-key`.
- Latest merged feature on develop: `feature/redis-rate-limit-store`.
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Held WIP: OTLP tracing remains on `feature/otlp-tracing-config` and is not part of this commit.
- Working direction: stop adding broad new features; finish production readiness/UAT tasks needed to go product.

---

## 2. Active Work In Review

Draft commit: `ci: automate payment sandbox callback verification`.

Scope implemented in this branch:
- Add a staging runner that obtains two real pending checkout sessions, replays signed success/failure/stale payloads and verifies persisted payment states.
- Verify duplicate success replay remains idempotent and stale failure callback leaves the payment pending.
- Record a `PASSED` sandbox E2E session only after all checks succeed, then assert aggregate `readyForFrontendExposure=true`.
- Add a protected GitHub Actions workflow using the `staging` environment, Base64 callback secrets and sanitized JSON artifacts.
- Keep workflow inputs out of inline PowerShell expressions to prevent command injection.
- Keep real wallet/bank-app merchant UAT open; captured callback replay automation does not replace that launch gate.

Validation so far:
- PowerShell parser: pass.
- Controlled connection-failure smoke: pass; report is created with `success=false` and contains neither admin token nor callback signature.
- Static workflow run-block validation: pass; no direct GitHub input interpolation in PowerShell commands.
- Targeted payment regression: 26 tests passed.
- Full Maven suite reached 436 tests: 426 passed and 10 Docker-backed integration tests errored because Testcontainers could not find a valid Docker environment; no assertion failure was reported.
- `git diff --check` and manual review passed without blockers.
- Actionlint and CodeRabbit CLIs remain unavailable.
- User review completed on 2026-07-11; patch is approved for commit and merge.


---

## 3. Next Product-Readiness Priorities

P0:
- Run real MoMo/VNPAY sandbox E2E validation with merchant test accounts and public HTTPS callback URL.
- Use the new sandbox E2E session endpoints to record checkout URL, success/failure callbacks, duplicate replay and freshness rejection evidence.
- Only expose MoMo/VNPAY to real users after aggregate UAT result returns `readyForFrontendExposure=true`.

P1:
- Run the protected callback workflow for MoMo and VNPAY on staging, archive sanitized reports and pair them with real merchant checkout evidence.
- Wire production log/metric dashboards and distributed tracing backend around existing structured logs/Actuator metrics.

P2:
- Provision and UAT Cloudflare R2 uploads in staging.
- Tune surge pricing thresholds with staging demand/supply data.
- Apply service-area SQL release in staging, create real launch-city polygons, and UAT common pickup/dropoff pairs.
- Add analytics/exporting after launch-critical UAT tasks are stable.