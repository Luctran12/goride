# GoRide Current Phase

> Last updated: 2026-07-16, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `feature/production-observability-wiring`.
- Base develop commit: `4e96274` (`merge: payment sandbox callback automation`).
- Latest merged payment feature: `feature/payment-sandbox-e2e-automation`.
- Latest merged feature on develop: `feature/payment-sandbox-e2e-automation`.
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Historical WIP branch `feature/otlp-tracing-config` remains unmerged; this branch supersedes it on the current develop base.
- Working direction: stop adding broad new features; finish production readiness/UAT tasks needed to go product.

---

## 2. Active Work In Review

Draft commit: `feat: wire production otlp tracing`.

Scope implemented in this branch:
- Add Micrometer OpenTelemetry bridge and OTLP exporter dependencies managed by Spring Boot 3.5.
- Configure disabled-by-default local/test tracing, OTLP endpoint/timeouts, sampling and OpenTelemetry resource attributes.
- Add `traceId`/`spanId` to plain console logs and attach the sanitized request ID as a high-cardinality span attribute.
- Expose only typed tracing/export-enabled flags through `/actuator/info`; collector URL and auth headers remain private.
- Fail production startup when required tracing/exporter/collector/sampling configuration is unsafe, with an explicit external-agent opt-out.
- Extend staging readiness smoke with optional `-RequireTracing` and workflow input.

Validation so far:
- Targeted observability/security/production suite passed: 24 tests.
- PowerShell parser and `git diff --check`: pass.
- Full Maven regression passed: 445 tests.
- Manual review passed without blockers.
- User review completed on 2026-07-19; feature commit created on branch and is not merged/pushed yet.


---

## 3. Next Product-Readiness Priorities

P0:
- Run real MoMo/VNPAY sandbox E2E validation with merchant test accounts and public HTTPS callback URL.
- Use the new sandbox E2E session endpoints to record checkout URL, success/failure callbacks, duplicate replay and freshness rejection evidence.
- Only expose MoMo/VNPAY to real users after aggregate UAT result returns `readyForFrontendExposure=true`.

P1:
- Run the protected callback workflow for MoMo and VNPAY on staging, archive sanitized reports and pair them with real merchant checkout evidence.
- Deploy an OTLP collector, centralized log shipping, Prometheus dashboards and alerts; backend exporter/config/startup gates are code-ready.

P2:
- Provision and UAT Cloudflare R2 uploads in staging.
- Tune surge pricing thresholds with staging demand/supply data.
- Apply service-area SQL release in staging, create real launch-city polygons, and UAT common pickup/dropoff pairs.
- Add analytics/exporting after launch-critical UAT tasks are stable.