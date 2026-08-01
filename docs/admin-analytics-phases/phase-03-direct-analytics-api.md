# Phase 03 - Direct Analytics API Baseline

## Goal

Implement the correctness baseline by querying operational and telemetry tables directly.

## Prerequisites

- Phase 02 telemetry approved.
- Small hand-verifiable fixtures exist.

## Scope

- Shared analytics range/filter model.
- Overview endpoint.
- Demand time-series endpoint.
- Supply time-series endpoint.
- Matching performance endpoint.
- Matching funnel endpoint.
- Admin RBAC and OpenAPI draft implementation.

## Planned Commit

```text
feat: add direct-query admin analytics APIs
```

## Acceptance Criteria

- Results match hand-calculated fixtures.
- All ranges use `[from, to)`.
- Reporting timezone is explicit.
- Completed revenue excludes pending and failed payments.
- Percentiles exclude open runs.
- Funnel units and denominators are explicit.
- Empty ranges do not divide by zero.
- `/api/v1/admin/dashboard` remains backward compatible.

## Review Gate

Direct-query correctness must be approved before spatial aggregation or materialized views.

