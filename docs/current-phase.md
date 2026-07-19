# GoRide Current Phase

> Last updated: 2026-07-19, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `feature/payment-production-exposure-gate`.
- Base develop commit: `39fb871` (`merge: payment checkout post endpoint`).
- Latest merged payment feature: `feature/payment-checkout-post-endpoint`.
- Latest merged feature on develop: `feature/payment-checkout-post-endpoint`.
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Working direction: finish online payment/provider launch gates before adding advanced features.

---

## 2. Active Work In Review

Draft commit: `fix: allow production payment exposure after uat`.

Scope implemented in this branch:
- Keep `enabled=true` as backend/provider checkout availability when provider runtime and checkout config are present.
- Compute online `readyForFrontendExposure`/`consumerEnabled` from provider registered, provider enabled, checkout config, webhook config and aggregate UAT evidence `PASSED`.
- Stop requiring current runtime `sandbox=true` for passenger exposure after UAT evidence has already passed, so switching provider config to production mode does not hide MoMo/VNPAY again.
- Keep `sandboxReady` as the admin/devops signal for whether the current environment can run sandbox UAT now.
- Update `PaymentSandboxUatResultResponse.readyForFrontendExposure` to use checkout+webhook readiness plus passed evidence, while `sandboxReady` remains separate.
- Add regression tests for `/payments/methods` and sandbox UAT result response in production mode after UAT pass.

Validation so far:
- Targeted payment method/UAT result suite passed: 11 tests.
- Full Maven regression: pass 454 tests.
- `git diff --check`: pass; Git reports Windows CRLF conversion warnings only.
- CodeRabbit CLI: unavailable in PATH (`coderabbit` command not found).
- User review: completed 2026-07-19; commit created on feature branch, not merged or pushed yet.

---

## 3. Remaining Product-Readiness Gate

Code-side online payment flow is now expected to be ready for FE integration once this branch is reviewed and merged. The remaining P0 item is external UAT:
- Run real MoMo/VNPAY sandbox E2E validation with merchant test accounts and a public HTTPS callback URL.
- Record sandbox E2E sessions with checkout URL, success/failure callbacks, duplicate replay and freshness rejection evidence.
- Confirm `/api/v1/payments/methods` returns `consumerEnabled=true` after aggregate evidence is `PASSED`, including after provider config is switched from sandbox to production mode.