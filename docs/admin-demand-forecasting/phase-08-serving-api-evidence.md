# Phase 8 — Spring Boot Serving API Evidence

> Date: 2026-08-10
>
> Scope: read-only Admin API for the demand-forecasting processing layer
>
> Claim boundary: Porto responses are historical research evaluation evidence,
> not live Ho Chi Minh City operational forecasts.

## 1. Delivered contract

All routes use the existing response/error envelope, require `ROLE_ADMIN`, and
are exposed below `/api/v1/admin/analytics`.

| Route | Server responsibility |
| --- | --- |
| `GET /processing/status` | Latest run for each stage, row counts, quality summary and freshness |
| `GET /processing/runs` | Filtered, bounded run history |
| `GET /data-quality?runId=...` | Rule results plus backend-computed PASS/WARN/FAIL summary |
| `GET /models` | Model lifecycle, model card, hyperparameters, checksum and approval scope |
| `GET /forecast/demand` | GeoJSON cells with predicted/actual/error/interval and lineage |
| `GET /forecast/hotspots` | Server-ranked predicted-demand cells |
| `GET /forecast/evaluation` | MAE/RMSE/WAPE from the evaluation store or backfilled actuals |
| `GET /forecast/runs` | Forecast history, evaluated coverage, quality and freshness |

Model artifact URIs and binaries are deliberately absent from every response.
The frontend receives units, metrics, ranking, errors and quality summaries and
does not need to recalculate analytical values.

## 2. Availability and freshness semantics

Forecast metadata carries stable machine-readable states:

| State | Meaning |
| --- | --- |
| `AVAILABLE` | Published operational forecast within the freshness threshold |
| `STALE` | Published forecast exists but exceeds the configured threshold |
| `AVAILABLE_RESEARCH` | Historical `EVALUATION` run; visible for thesis analysis only |
| `EMPTY` | A run matches but has no rows in the requested time/bounds filter |
| `UNAVAILABLE` | No successful run matches the selected model/purpose/grid |

Research runs also return `freshnessStatus = HISTORICAL_EVALUATION` and
`availabilityReason = HISTORICAL_EVALUATION_NOT_OPERATIONAL`. This prevents an
old Porto cutoff from being presented as stale TP.HCM operational data.

If PostgreSQL is unavailable or the serving schema cannot be queried, the API
returns `503 ANALYTICS_DATA_UNAVAILABLE`. A missing processing run returns
`404 ANALYTICS_RUN_NOT_FOUND`.

## 3. Safety and cost controls

- Class-level `@PreAuthorize("hasRole('ADMIN')")` protects every new route.
- The existing global token-bucket filter supplies `429` and rate-limit headers.
- Read transactions use a 10-second timeout and `READ_COMMITTED` isolation.
- Time ranges, timezone, horizon, cell size, WGS84 bounds and pagination are
  validated before repository access.
- Default caps: 5,000 forecast rows, 200 hotspots, page size 100, 1,000
  evaluation rows and 31 days per forecast query.
- Database failures are logged with the bounded component and error type;
  successful requests use the existing structured analytics observation with
  query type, duration, row count and freshness.
- Repository reads are set-based: one forecast-run selection plus one bulk cell
  query; quality and row counts use CTE aggregation. There is no per-cell or
  per-run follow-up query.

Environment overrides are defined in `application.properties` with the prefix
`ANALYTICS_FORECAST_`; the defaults match the frozen Phase 7 operational
contract.

## 4. Evaluation semantics

`/forecast/evaluation` first reads `analytics.forecast_evaluations`. If the
store has no rows for the requested filters, PostgreSQL derives the metrics from
actual-backfilled `analytics.demand_forecasts`:

- `MAE = AVG(absolute_error)`;
- `RMSE = SQRT(AVG((predicted_demand - actual_demand)^2))`;
- `WAPE = SUM(absolute_error) / SUM(actual_demand)` when the denominator is
  non-zero.

The response identifies the source as either
`PERSISTED_EVALUATION_STORE` or `DERIVED_FROM_BACKFILLED_ACTUALS`. These derived
Phase 7 values are operational-backfill diagnostics, not a replacement for the
frozen Phase 6 FINAL walk-forward comparison.

## 5. PostgreSQL/PostGIS evidence

The additive release
`db/releases/20260810-admin-demand-forecast-serving-indexes` provides:

- `idx_demand_forecasts_serving_lookup` for run/horizon/time/cell reads;
- `idx_demand_forecasts_hotspot_lookup` for descending server-side ranking.

The existing `idx_demand_forecasts_geometry_gist` remains the bounding-box
access path. Testcontainers `EXPLAIN` evidence with sequential scans disabled
confirmed the hotspot query uses `idx_demand_forecasts_hotspot_lookup` and the
map bounds query uses `idx_demand_forecasts_geometry_gist`.

Deploy the index release in `precheck -> apply -> verify` order after the Phase
2 schema release. It was applied automatically to the isolated integration
database; it was not applied automatically to a developer database.

## 6. Verification

Focused verification on Java 17 / Spring Boot 3.5.13:

- service tests cover research labelling, unavailable and stale contracts,
  invalid range/horizon/grid/bounds, response caps, derived metrics and stable
  database failure mapping;
- controller tests prove `ROLE_PASSENGER -> 403` and malformed/missing filters
  return the common `400 VALIDATION_ERROR` envelope;
- PostgreSQL 15 + PostGIS 3.3 + Redis 7 Testcontainers integration covers
  GeoJSON, bounded hotspots, model-card exposure without artifact URI,
  data-quality summary, run/evaluated counts, MAE/WAPE derivation and both query
  plans.

The focused unit/controller suite and both PostGIS integration tests pass.

## 7. Remaining boundary

Phase 8 exposes a stable backend read contract only. Phase 9 must add frontend
foundation, Processing Status, Data Quality and Model Evaluation UI. Phase 10
must add the forecast heatmap and hotspot exploration. No browser-triggered
training, model approval or forecast publication is enabled.
