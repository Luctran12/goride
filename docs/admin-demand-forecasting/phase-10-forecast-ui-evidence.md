# Phase 10 — Forecast Heatmap and Hotspot Frontend Evidence

> Date: 2026-08-10
>
> Frontend repository: `goride-web`
>
> Branch: `codex/admin-demand-forecasting`
>
> Claim boundary: Porto results are historical thesis evidence under
> `RESEARCH_DEMONSTRATION`, not live Ho Chi Minh City forecasts.

## 1. Delivered UI

The `/analytics` route now includes a **Dự báo nhu cầu** tab connected to the
Phase 8 forecast and hotspot endpoints:

- a GeoJSON EPSG:4326 heatmap with Forecast, Actual and Absolute Error modes;
- forecast-run, UTC range, horizon, grid-size, target-bucket and map-bounds controls;
- uncertainty, availability, freshness, quality and actual-coverage metadata;
- a backend-ranked hotspot table and cell-detail drawer with lineage and time series.

Map colors format backend values only. The frontend does not interpolate cells,
rank hotspots or recalculate actual error/evaluation metrics.

## 2. Contract and claim safety

- Forecast requests require a successful run, model version, purpose, UTC range,
  horizon and grid size. Bounds are optional and must be complete/valid.
- The client rejects reversed ranges, ranges over 31 days, unsupported horizons/
  grid sizes and forecast responses over 5,000 features.
- Porto research and stale warnings are derived from both run and response metadata,
  so the claim boundary remains visible while data is loading.
- Actual/Error controls are disabled with an explanation until backend backfill is
  available for the selected target bucket.
- Phase 8 does not accept `vehicleType` or `serviceAreaId` on forecast/hotspot
  endpoints. Phase 10 does not send invented parameters or perform misleading
  client-side filtering; adding those filters requires a backend contract/data change.

## 3. Race, UX and accessibility hardening

- A superseded request is aborted by the shared fetch hook.
- Existing map/hotspot data is hidden during a changed-filter request, preventing a
  stale response from appearing under the new horizon/time/grid controls.
- The GeoJSON layer remount key is based on geometry, not mutable properties, and
  map bounds auto-fit Porto/backend cells rather than a fixed city viewport.
- Heatmap, legend, filter region, table and drawer have independent accessible names;
  all controls are keyboard operable and the layout collapses at responsive breakpoints.

## 4. Commits

| Commit | Scope |
| --- | --- |
| `ca010ab` | Forecast filters, mapper and serving-service foundation |
| `8572fd1` | Forecast Heatmap, hotspots and cell-detail interaction |
| `42c0b74` | Race-state, research/freshness and page-isolation hardening |
| `8a384b7` | Phase 10 frontend review record |

## 5. Verification

- `npm run lint` — pass.
- `npm test -- --run` — 50 files, 237 tests pass.
- `npm run build` — pass; 2,610 modules transformed.
- Tests cover exact query parameters, GeoJSON cell mapping, backend-owned values,
  mode availability, research/stale labels, range limits, request supersession,
  hotspot detail and isolation from overview/service-area APIs.

Recharts emits its known container-size warning in JSDOM, which has no layout
engine. The production build succeeds.

## 6. Remaining boundary

Phase 10 is complete at the review gate. Authenticated end-to-end execution,
load/freshness/storage measurements, recovery scenarios, privacy review and final
thesis artifacts belong to Phase 11.
