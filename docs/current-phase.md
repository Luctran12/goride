# GoRide Current Phase

> Last updated: 2026-07-19, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `feature/online-payment-production-gate`.
- Base develop commit: `e6aba60` (`merge: production observability wiring`).
- Latest merged payment feature: `feature/payment-sandbox-e2e-automation`.
- Latest merged feature on develop: `feature/production-observability-wiring`.
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Working direction: stop adding broad new features; finish payment/provider UAT and launch reliability tasks needed to go product.

---

## 2. Active Work In Review

Draft commit: `feat: complete online payment exposure gate`.

Scope implemented in this branch:
- Add `readyForFrontendExposure` and `consumerEnabled` to `GET /api/v1/payments/methods`.
- Keep `enabled` as backend/provider checkout availability for controlled booking/UAT flows.
- Compute `consumerEnabled=true` only when online provider config/readiness is sufficient and aggregate sandbox UAT evidence has passed.
- Keep CASH `consumerEnabled=true` without sandbox evidence.
- Release the assigned driver immediately after a checkout-required online payment becomes `PENDING` at trip completion, so driver availability is not blocked while passenger completes MoMo/VNPAY checkout.
- Preserve existing payment completed notifications and driver release on successful provider callbacks.
- Update `integrate-plan.md`, `plan.md`, `docs/current-phase.md`, `docs/implementation-log.md` and `docs/pland.xlsx` with the new FE contract and status.

Validation so far:
- Targeted payment method/lifecycle suite passed: 14 tests.
- Full Maven regression passed: 450 tests.
- `git diff --check`: pass; Git reports Windows CRLF conversion warnings only.
- CodeRabbit CLI: unavailable in PATH (`coderabbit` command not found).
- User review: completed 2026-07-19; feature commit created, not merged or pushed yet.

---

## 3. Next Product-Readiness Priorities

P0:
- Run real MoMo/VNPAY sandbox E2E validation with merchant test accounts and public HTTPS callback URL.
- Use the sandbox E2E session endpoints to record checkout URL, success/failure callbacks, duplicate replay and freshness rejection evidence.
- Confirm `/api/v1/payments/methods` returns `consumerEnabled=true` for each online provider only after aggregate `readyForFrontendExposure=true`.

P1:
- Run the protected callback workflow for MoMo and VNPAY on staging, archive sanitized reports and pair them with real merchant checkout evidence.
- Deploy an OTLP collector, centralized log shipping, Prometheus dashboards and alerts; backend exporter/config/startup gates are code-ready.

P2:
- Provision and UAT Cloudflare R2 uploads in staging.
- Tune surge pricing thresholds with staging demand/supply data.
- Apply service-area SQL release in staging, create real launch-city polygons, and UAT common pickup/dropoff pairs.
- Add analytics/exporting after launch-critical UAT tasks are stable.
