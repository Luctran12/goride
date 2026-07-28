# Phase 00 - Contracts and Architecture

## Goal

Freeze the analytical semantics and architecture before changing schema or matching behavior.

## Inputs

- `docs/admin-analytics-backend-implementation-plan.md`
- Existing booking, matching, payment, tracking and admin contracts.
- Current branch: `codex/admin-v2`.

## Deliverables

- [`metric-dictionary.md`](../admin-analytics/metric-dictionary.md)
- [`api-contract-draft.md`](../admin-analytics/api-contract-draft.md)
- [`benchmark-protocol.md`](../admin-analytics/benchmark-protocol.md)
- [`ADR-001-matching-telemetry-consistency.md`](../admin-analytics/adr/ADR-001-matching-telemetry-consistency.md)
- Updated `docs/current-phase.md`.

## Tasks

- [x] Define metric names, formulas, source timestamps and edge cases.
- [x] Define `[from, to)` range semantics and reporting timezone behavior.
- [x] Define matching run and offer lifecycle semantics.
- [x] Define the initial Admin Analytics API surface.
- [x] Define direct-query versus materialized-view benchmark rules.
- [x] Record telemetry consistency, transaction and idempotency decisions.
- [ ] Review and approve Phase 00.

## Acceptance Criteria

- Every core metric has one formula and one source of truth.
- Terminal and in-progress matching states are unambiguous.
- Revenue is derived only from completed payments.
- Retry and driver-available triggers do not create duplicate open runs.
- API filters, timezone, units and empty-result behavior are documented.
- Benchmark variants use the same dataset, filters and refresh cutoff.
- No production schema or runtime behavior changes are included.

## Validation

```powershell
git diff --check
rg -n "T[B]D|T[O]DO|PLACEH[O]LDER" docs/admin-analytics docs/admin-analytics-phases
```

## Review Gate

Do not start Phase 01 until the user approves these contracts.
