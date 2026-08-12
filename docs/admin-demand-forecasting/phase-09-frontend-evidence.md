# Phase 9 — Processing and Model Frontend Evidence

> Date: 2026-08-10
>
> Frontend repository: `goride-web`
>
> Branch: `codex/admin-demand-forecasting`
>
> Claim boundary: Porto results are historical thesis evidence under
> `RESEARCH_DEMONSTRATION`, not live Ho Chi Minh City forecasts.

## 1. Delivered UI

The existing `/analytics` route now includes two backend-connected tabs:

- **Xử lý dữ liệu**: latest extraction/quality/feature/training/inference/
  evaluation stages, stale/fresh state, timestamps, row counts, quality
  summaries, filtered processing history and rule-level Data Quality;
- **Đánh giá mô hình**: registry/model card, lifecycle and checksum, backend
  MAE/RMSE/WAPE cards/table/chart, model/horizon/grid filters and forecast-run
  actual coverage/freshness history.

The operational trip-date/service-area filters are not mounted and their APIs
are not called on these two tabs. Runtime data comes only from the Phase 8 API;
MSW fixtures are test-only.

## 2. Contract and claim safety

- All eight Phase 8 routes have frontend constants. Phase 9 consumes processing
  status/runs, data quality, models, evaluation and forecast-run history; map
  and hotspot consumers remain Phase 10.
- Mappers whitelist registry fields and never expose an artifact path/URI.
- The UI formats individual evaluation rows and does not average or recalculate
  MAE, RMSE or WAPE.
- Porto model/evaluation responses display a persistent research-only warning.
- No train, rerun, approve, promote or publish mutation exists in the browser.

## 3. UX and accessibility

- Widget-level loading, empty, stale and retry states are independent.
- `403`, `404`, `422`, `429` and `503` have distinct copy and retry behavior.
- Analytics tabs implement ARIA tab semantics plus Left/Right/Home/End keyboard
  navigation.
- Stage, model-card, quality and metric layouts collapse at tablet/mobile
  breakpoints; wide evidence tables remain horizontally scrollable.

## 4. Commits

| Commit | Scope |
| --- | --- |
| `40a5170` | Endpoint constants, mappers, service and normalized errors |
| `376b99d` | Processing Status, processing history and Data Quality UI |
| `913a44f` | Model registry/evaluation and forecast-run history UI |
| `835635b` | Keyboard hardening and Phase 9 review evidence |

## 5. Verification

- `npm run lint` — pass.
- `npm run test:run` — 48 files, 217 tests pass.
- `npm run build` — pass; 2,607 modules transformed.
- New tests cover exact query params, nullable metrics, exclusion of artifact
  locations, stale/research labels, run selection, filters, keyboard navigation
  and `422/429/503` states.

Recharts emits its known container-size warning in JSDOM, which has no layout
engine. The production build succeeds and browser layout receives explicit
chart height.

## 6. Remaining boundary

Phase 9 is complete at the review gate. Forecast Heatmap,
Actual/Forecast/Absolute Error exploration, time/horizon/grid controls,
uncertainty, hotspots and cell detail belong to Phase 10 and are not included
in this phase.
