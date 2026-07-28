# Phase 06 - API Hardening and Frontend Handoff

## Goal

Stabilize the backend contract so Admin Web can integrate without reading backend entities or duplicating metric logic.

## Prerequisites

- Analytics query behavior approved.

## Scope

- Final OpenAPI descriptions and examples.
- Error, empty and partial-data semantics.
- Precision, unit and timezone documentation.
- Query-range, bounds and payload guardrails.
- API smoke collection.
- Frontend integration guide.
- Backward-compatibility tests.

## Planned Commit

```text
docs: finalize admin analytics API handoff
```

## Acceptance Criteria

- Frontend does not calculate percentiles or metric formulas.
- Every response documents units and freshness.
- Enum and nullable-field behavior are explicit.
- Heatmap bounds and response limits are documented.
- Legacy dashboard contract remains unchanged.
- Consumer examples pass against the running backend.

## Out of Scope

- React/Vite implementation.
- Chart styling and page layout.

## Review Gate

Backend contract must be frozen before frontend implementation begins.

