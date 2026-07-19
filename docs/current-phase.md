# GoRide Current Phase

> Last updated: 2026-07-19, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `feature/payment-checkout-post-endpoint`.
- Base develop commit: `6284ce5` (`merge: online payment exposure gate`).
- Latest merged payment feature: `feature/online-payment-production-gate`.
- Latest merged feature on develop: `feature/online-payment-production-gate`.
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Working direction: stop adding broad new features; finish payment/provider UAT and launch reliability tasks needed to go product.

---

## 2. Active Work In Review

Draft commit: `feat: add payment checkout post endpoint`.

Scope implemented in this branch:
- Add canonical `POST /api/v1/payments/trips/{tripId}/checkout` for FE/provider checkout creation.
- Keep existing `GET /api/v1/payments/trips/{tripId}/checkout` as a compatibility alias for older FE builds.
- Share controller logic between GET and POST so auth/user access, payment status validation and provider idempotency stay in one service path.
- Update payment sandbox UAT plan response to advertise the POST checkout endpoint.
- Update UAT plan wording so passenger exposure uses `/methods.consumerEnabled=true`; `enabled=true` remains controlled backend/UAT checkout availability.
- Update `integrate-plan.md`, `plan.md`, `docs/current-phase.md`, `docs/implementation-log.md` and `docs/pland.xlsx` with the new FE checkout contract.

Validation so far:
- Targeted checkout/controller/UAT plan suite passed: 17 tests.
- Full Maven regression: pass 452 tests.
- `git diff --check`: pass; Git reports Windows CRLF conversion warnings only.
- CodeRabbit CLI: unavailable in PATH (`coderabbit` command not found).
- User review: completed 2026-07-19; commit created on feature branch, not merged or pushed yet.

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