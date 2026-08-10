# GoRide Current Phase

> Last updated: 2026-08-10, Asia/Ho_Chi_Minh
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Active feature: Admin demand-forecasting processing layer.
- Current branch: `codex/admin-demand-forecasting`.
- Backend base: `develop` commit `ae3dac2` (`merge admin-v2`).
- Frontend integration: `goride-web` branch `codex/admin-demand-forecasting`,
  commit `70e0cb2` (`docs: record demand forecasting phase 11 review`).
- Branch-base exception: the repository convention normally starts features
  from `main`, but `main` does not contain Admin Analytics Phase 0-8. This
  feature starts from `develop` because its schema, PostGIS grid, telemetry and
  API contracts are required dependencies.
- User-local `.env.example` remains outside the analytics commits unless
  reviewed separately.

---

## 2. Active Phase

Phase 11 — End-to-end evaluation and thesis artifacts from
[`admin-analytics-processing-layer-implementation-plan.md`](admin-analytics-processing-layer-implementation-plan.md).

User approval to continue implementation: 2026-08-10. Frozen-evidence
verification, failure/privacy contracts, synthetic serving benchmarks, full
backend/frontend regressions and the thesis artifact set are implemented and
verified. Phase 11 is at the final review gate.

Phase 7 implementation commits:

```text
9177be9 feat: operationalize registered demand forecasts
cc2c935 fix: canonicalize idempotent forecast cutoff
```

Phase 8 implementation commit:

```text
b9e00e2 feat: expose admin demand forecasting APIs
```

Phase 9 frontend implementation commits:

```text
40a5170 feat: add demand forecasting frontend foundation
376b99d feat: add analytics processing status UI
913a44f feat: add model evaluation analytics UI
835635b docs: record demand forecasting phase 9 review
```

Phase 10 frontend implementation commits:

```text
ca010ab feat: add forecast exploration foundation
8572fd1 feat: add forecast heatmap exploration UI
42c0b74 test: harden forecast exploration states
8a384b7 docs: record demand forecasting phase 10 review
```

Phase 11 implementation commits:

```text
e2b8b56 feat: suppress low-count forecast actuals
1345ff5 test: enforce forecast privacy contract
ff35d7c feat: verify forecasting thesis evidence
bb0bdbf test: restore full forecasting regression
fbffcb4 feat: explain forecast privacy suppression (frontend)
70e0cb2 docs: record demand forecasting phase 11 review (frontend)
```

Completed scope:

- forecast/hotspot endpoint integration with exact contract params and whitelist mappers;
- GeoJSON heatmap with Forecast/Actual/Absolute Error and target-bucket slider;
- run, UTC range, horizon, grid-size and map-bounds controls with local limits;
- backend uncertainty, freshness, quality, actual coverage and demand units;
- backend-ranked hotspot table and cell-detail evidence drawer;
- request cancellation/race protection and oversized payload guard;
- visible Porto `RESEARCH_DEMONSTRATION` and stale boundaries;
- responsive and accessible map/legend/table/filter/drawer states;
- immutable evidence verification across 296 files / 2,074,459,267 bytes;
- low-count actual suppression at a minimum threshold of three, with no direct
  identifiers or artifact locations in serving DTOs;
- one-command frozen verification and clean full-reproduction orchestration;
- synthetic PostGIS serving benchmark with explicit non-accuracy claim;
- model card, data sheet, evaluation report, architecture, runbook and final
  Definition-of-Done traceability;
- 135 backend suites / 584 tests and 50 frontend files / 238 tests, plus lint
  and successful 2,610-module production build.

Remaining deployment scope:

- Vehicle/service-area forecast filters, because Phase 8 does not expose those
  dimensions on forecast rows or accept the query parameters.
- browser-triggered training, model approval or forecast publication.
- production approval of a Porto-trained model for Ho Chi Minh City.
- authenticated deployment UAT, production-traffic load tests, monitoring,
  backups and an enabled destructive retention schedule.

---

## 3. Frozen Baseline

### Backend

- Existing Admin Analytics remains the historical descriptive layer.
- The new artifact extends it; it does not rewrite direct/materialized queries,
  telemetry or the historical heatmap.
- PostgreSQL/PostGIS remains the serving store.
- Spring Boot remains the only HTTP boundary exposed to Admin Web.

### Dataset

- Thesis method dataset: Porto Taxi, version
  `porto-2013-07_2014-06-v1`.
- Source artifact: `train.csv.zip`.
- SHA-256:
  `210dd0a20da66a8fc2de3440aecd84670921bc257591f8365a4475e31453c5ea`.
- License recorded by the source repository: CC BY 4.0.
- Local dataset root is external to Git and selected through
  `GORIDE_ANALYTICS_DATA_ROOT`.
- GoRide synthetic/operational data is a separate integration profile and must
  not be merged into Porto evaluation results.
- Local experiment database `goride_analytics_porto` exists with PostgreSQL 18,
  PostGIS 3.6.2 and both EPSG:3763/EPSG:32648 spatial references available.

---

## 4. Phase 2 Approved Baseline

Phase 2 was approved when the user requested Phase 3. Its schema, release and
rollback contracts remain the persistence baseline.

Validation evidence:

- All 13 database release descriptors passed `validate-db-release.ps1 -All`.
- PostgreSQL 18/PostGIS 3.6.2 local cycle passed precheck, apply, verify,
  transactional fixture, rollback to zero forecasting tables and re-apply.
- The final local schema is applied to `goride_analytics_porto`; all seven
  forecasting tables are empty after fixture rollback.
- Testcontainers PostgreSQL 15/PostGIS release-chain integration passed 1/1:
  apply/verify/fixture, reverse rollback with absence assertions, then re-apply
  and verify again.
- Fixtures prove rejection of duplicate processing runs, forecasts and
  evaluation dimensions; unsupported cell sizes; invalid model lifecycle;
  non-EPSG:4326 geometry; and deletion of referenced evidence.
- Actual/error backfill passed the target-bucket closure and exact absolute
  error constraints.
- `git diff --check` and the release-folder trailing-whitespace scan passed.

Manual review added a composite processing/forecast contract, an approved-model
trigger for published runs, canonical cell-ID enforcement, label-availability
time checks and WGS84 coordinate bounds. UUID database identities remain
separate from Phase 1 artifact-directory identities by design.

---

## 5. Phase 3 Evidence

Phase 3 was approved when the user requested Phase 4. Its extraction and
quality evidence remains the immutable input contract for feature builds.

Validation evidence:

- Python 3.11 passed 33/33 tests with the two opt-in PostgreSQL integrations
  enabled; Python 3.12 passed 31 tests with those two integrations skipped.
- Repeated fixture extraction at the same source interval/config/commit produced
  the same snapshot UUID and SHA-256 across different processing attempts.
- PostgreSQL 18/PostGIS 3.6.2 persistence passed against
  `goride_analytics_porto`; the fixture run and seven quality rows were verified
  and removed, leaving both tables at zero rows.
- Real GoRide extraction passed in `READ ONLY`, `REPEATABLE READ`; bounded
  temporal SQL used `idx_trips_driver_requested_at` and no output event reached
  the exclusive cutoff.
- PASS/WARN/FAIL fixtures cover every Phase 3 rule and satisfy the Phase 2 count
  constraints. Quality FAIL records terminal failure and raises exit code 6.
- Canonical privacy assertions prove passenger, driver, payment, taxi and full
  trajectory fields are absent.
- `compileall`, dependency-lock contract, CLI help, `git diff --check` and the
  staged credential scan passed.

Manual review retained the disk-backed spool for bounded memory, separated
snapshot identity from processing attempts, added repeatable-read source
isolation and ensured typed/unexpected failures try to close the DB lifecycle.

---

## 6. Phase 4 Evidence

Implementation commit: `39abdb3`
(`feat: build versioned spatio-temporal demand features`).

Implementation and manual review are complete. User review is required before
Phase 5.

Validation evidence:

- Python 3.11 and 3.12 passed 54 tests; Python 3.12 skipped five opt-in database
  integrations, while Python 3.11 passed all 54 with both databases enabled.
- Repeated builds from the same extraction snapshot/config/commit emitted the
  same deterministic Parquet SHA-256 and six unique persisted rows.
- PostgreSQL upsert remained idempotent; an injected terminal-state failure
  rolled back feature rows atomically and recorded the processing run as
  `FAILED`.
- Python/PyProj and PostgreSQL/PostGIS produced the same grid coordinates for
  the golden transform using the historical zero-origin `FLOOR` rule.
- Golden fixtures cover grid boundaries, minimum-inclusive/maximum-exclusive
  study bounds, continuous empty buckets, Porto DST fallback, feature leakage,
  supply cutoff, duplicate rows and extraction-evidence tampering.
- The Phase 4 artifact includes a versioned dictionary, manifest, quality
  results, deterministic Parquet and complete SHA-256 evidence.
- PostgreSQL integration cleanup left `processing_runs`,
  `data_quality_results` and `demand_features` at zero rows.
- Both runtimes passed `compileall`; dependency locks, `git diff --check` and
  the staged credential scan passed. `.env.example` remained outside commit.

Manual review made feature-row publication and terminal success one atomic
transaction, required checksum coverage for every input evidence file, rejected
schema-incompatible feature switches and removed partial Parquet temporary
files on failure.

Known scale limitation: Phase 4 uses disk-backed feature uniqueness and batched
database writes, but the compact demand cube remains proportional to active
cells times bucket count and still needs a full Porto profile.

### Full Porto follow-up (`e8ad1ac`)

The repaired external manifest passed identity/path/SHA-256 validation. The
first complete extraction correctly failed the original zero-tolerance policy,
which exposed 74 canonical duplicate IDs and 5,901 empty polylines. Read-only
profiling proved all 5,901 were missing trajectories rather than malformed or
invalid WGS84 coordinates.

Commit `e8ad1ac` (`fix: classify Porto source exclusions by quality policy`)
freezes a 0.01% Porto duplicate-rate ceiling, keeps duplicate exclusion
deterministic, classifies empty polylines as WARN/excluded missing trajectory,
and preserves zero-tolerance invalid-coordinate failure.

Full rerun evidence:

- 1,710,670 source rows scanned in 212.8 seconds;
- 1,704,685 canonical rows and 758,208,824-byte JSONL artifact;
- `WARN` overall: 74 duplicates (0.004326%) and 5,911 missing trajectories
  (0.345537%) excluded;
- schema, checksum, event-time, cutoff and invalid-pickup FAIL gates all PASS;
- snapshot SHA-256
  `e1fbd4a7fbe44e48db508f69dacf83aba100e33fe6db8b4b91a8bcd337820153`;
- Python 3.11/3.12 passed 57 tests; all 57 passed with PostgreSQL/PostGIS and
  GoRide integrations enabled on Python 3.11.

The scale dry-run stopped full materialization before database writes. At
500 m, 1,238 active cells across 35,040 buckets would emit 130,126,180 rows.
Training-only cumulative-demand populations are:

| Coverage | Cells (including boundary ties) | Captured train demand | Projected all-year rows |
| ---: | ---: | ---: | ---: |
| 90% | 98 | 90.0209% | 10,300,780 |
| 95% | 134 | 95.0648% | 14,084,740 |
| 97% | 159 | 97.0136% | 16,712,490 |
| 99% | 256 | 99.0072% | 26,908,160 |

Decision required before the next Phase 4 corrective commit: approve a
train-only cell population and time-partition/cost-guard design. The recommended
population is 95% cumulative training demand because it is leakage-safe,
methodologically explicit and reduces projected rows by 89.2%.

User approval: 2026-08-09. Freeze 95% cumulative train-demand coverage with
boundary ties, UTC calendar-month partitions, a 1,500,000-row partition guard
and a 15,000,000-row run guard. The corrective commit must preserve atomic
database publication and record the selected population in artifact evidence.

### Bounded full-scale build and publication

- Fresh source snapshot under config hash `4cf869ca75a0...`: 1,704,685
  canonical rows, 758,208,824 bytes, SHA-256
  `837011e6a8f113a744fa2abb0b3d4f87d4fce1ef49ee4c2ace23e3be22636375`.
- Feature artifact run
  `20260809T153840318116Z-6f2ea5e3b834-porto-thesis-4cf869ca75a0`:
  134 train-selected cells, 95.0648104% train-demand coverage, 12 UTC-month
  partitions, 14,084,740 rows and 118,864,225 Parquet bytes.
- Feature dataset SHA-256:
  `4e226e38bb97c5eb560310cd951438223d739f162e028175e93d51f7e8859d5b`;
  quality `WARN`, with no FAIL rule.
- The first row-wise database publication was client-interrupted before commit;
  PostgreSQL exposed zero feature rows and the audit run was closed as
  `FEATURE_BUILD_CLIENT_INTERRUPTED`. The immutable Parquet artifact was kept.
- Commit `19eb69f` adds checksum/quality/cost-guarded resume and PostgreSQL
  temporary-staging `COPY` while retaining one outer atomic transaction.
- Persistence run
  `20260810T054210932454Z-19eb69f94c04-porto-thesis-4cf869ca75a0`
  succeeded in about 65 minutes. Processing run
  `69f7dbfb-45b9-5193-81a2-bd465b59cccd` records
  `rows_read = rows_written = 14,084,740`, attempt 2 and code commit `19eb69f`.
- PostgreSQL/PostGIS integration proves idempotency and complete rollback when
  terminal success fails. Python 3.11/3.12 pass 66 tests; the Python 3.11 real
  analytics database suite passes 65 available tests with one opt-in GoRide
  read-only test skipped.
- Independent post-commit QA returns exact `COUNT(*) = 14,084,740`; PostgreSQL
  statistics identify 134 cells, horizons 15/30/60, one feature set and one
  creator run. UTC grouping returns exactly the 12 manifest months and their
  row counts; `DQ_LEAKAGE`, uniqueness, population, grid and continuity PASS.
- Persisted `analytics.demand_features` consumes about 15.96 GB including
  indexes. Training/evaluation must stream/filter by split rather than load the
  full table into memory.

---

## 7. Phase 5 Evidence

- Successful evaluation artifact:
  `20260810T075446729084Z-3c08e4269888-porto-thesis-4cf869ca75a0`.
- Processing run `53fc1ba9-0d73-5789-a3a5-324393814f0e` is `SUCCEEDED` with
  `rows_read = 14,084,740`, `rows_written = 13,967,088`, three quality rules
  and zero FAIL results.
- Five folds (`D1`-`D4`, `FINAL`), three horizons and two baselines produced
  240 metric groups and 13,967,088 raw prediction rows in ten Parquet files.
- Raw-prediction manifest SHA-256:
  `87980eb5b7a073865cf7bf3a26af32eb29e978eabbc00f6ec44a33c81d7e1f3e`.
- Independent verification recomputed every Parquet SHA-256, summed exact
  metadata row counts, recomputed the manifest hash and verified all top-level
  checksums. Raw Parquet totals 61,801,348 bytes.
- All 15 fold/horizon populations match exactly between the two baselines.
- FINAL holdout has 784,704 observations per horizon/model. Historical mean:
  MAE `0.353124`, RMSE `0.755793`, WAPE `0.951110`. Seasonal naive: MAE
  `0.410517`, RMSE `1.025132`, WAPE `1.105694`.
- Baseline metrics are identical across horizons by design because both fixed
  baselines predict from target calendar/history and do not consume
  horizon-specific feature rows. The Phase 6 candidate will use features at
  each inference cutoff and can therefore vary by horizon.
- Python 3.11 and 3.12 pass 76 tests; five opt-in integrations skip in the
  default suite. The real PostgreSQL/PostGIS suite passes all four tests.
- Two earlier local attempts were terminated by terminal time limits before
  Parquet finalization and are explicitly recorded as `FAILED` with zero
  published rows; they are not evaluation evidence.

---

## 8. Phase 6 Evidence

- The frozen experiment hash is
  `63f545298cd5fa879876efd0a82252c6aea4fccea9f95ab5e37ed6134eeb0788`;
  HGB tuning used D1-D4 only and FINAL was evaluated after selection.
- G500 candidate artifact
  `20260810T083830837344Z-f4a46557d3fd-porto-thesis-4cf869ca75a0`
  is the primary result. A2/HGB_C2 does not beat historical mean on FINAL MAE
  or WAPE, but beats seasonal naive on MAE/RMSE/WAPE at all three horizons.
- G1000 candidate artifact
  `20260810T092802643667Z-2ae7154c60d3-porto-thesis-4cf869ca75a0`
  has 6,410,268 raw prediction rows, 405 metric groups and quality `PASS`.
- G2000 candidate artifact
  `20260810T093827894213Z-2ae7154c60d3-porto-thesis-4cf869ca75a0`
  has 2,032,524 raw prediction rows, 450 metric groups and quality `PASS`.
- A2 improves A1 and A0 on FINAL WAPE for all nine grid/horizon tests. Against
  historical mean, A2 improves all three metrics only for G2000-H15; against
  seasonal naive it improves all three metrics for all nine tests.
- Candidate runtime is 1,692.29 s at G500, 427.52 s at G1000 and 324.84 s at
  G2000. G500 peak memory was not captured due a Windows collector defect;
  `9a612b2` fixes it for later runs without reopening FINAL.
- G1000/G2000 feature builds use `PARQUET_ONLY`. Database audit confirms six
  sensitivity runs `SUCCEEDED`, zero quality FAIL and zero feature rows
  published by those runs.
- Independent QA verified top-level checksums and every sensitivity Parquet
  SHA-256/byte/metadata-row count, and reloaded both model bundles with exactly
  the 15/30/60-minute horizons.
- Python 3.11 and 3.12 each pass 86 tests with five opt-in skips; the real
  PostgreSQL/PostGIS suite passes 4/4.
- Full tables, artifact identities, ablation, resource evidence and claim
  boundaries are recorded in
  [`admin-demand-forecasting/phase-06-candidate-evidence.md`](admin-demand-forecasting/phase-06-candidate-evidence.md).

---

## 9. Phase 7 Evidence

- Model version `porto-hgb-v1-g500-63f545298c-f4a46557d3` is registered as
  `APPROVED` only under `RESEARCH_DEMONSTRATION`, with two audited lifecycle
  events and artifact SHA-256 `8643527f...b5b4`.
- Official inference artifact
  `20260810T102601474464Z-9177be915f0a-porto-thesis-4cf869ca75a0`
  wrote exactly 402 rows for 134 cells and three horizons in 0.3464 seconds.
- Identical reruns return the same forecast/processing IDs with
  `idempotent = true` and create no duplicate rows.
- Backfill artifact
  `20260810T102748599113Z-cc2c935f50cc-porto-thesis-4cf869ca75a0`
  updated 402/402 actual/error rows after the closed watermark. Its rerun
  observed 402 evaluated rows and updated zero.
- Fault injection after forecast inserts rolled publication back to zero rows,
  recorded `PHASE7_INJECTED_TERMINAL_FAILURE`, and retained failed evidence.
  Controlled attempt 2 then published 402 rows under the same deterministic
  forecast UUID.
- Independent QA found zero negative predictions, invalid geometries, quality
  FAIL results or absolute-error mismatches. All official Phase 7 artifact
  checksums were recomputed successfully.
- Python 3.11 and 3.12 each pass 96 tests; the real PostgreSQL/PostGIS suite
  passes 4/4, in addition to a full rollback-only Phase 7 lifecycle rehearsal.
- Full evidence and limitations are recorded in
  [`admin-demand-forecasting/phase-07-operational-evidence.md`](admin-demand-forecasting/phase-07-operational-evidence.md).

---

## 10. Phase 8 Evidence

- Eight read-only endpoints cover processing, quality, model registry, forecast
  GeoJSON, hotspots, evaluation and run history.
- Porto `EVALUATION` responses are labelled `AVAILABLE_RESEARCH` and
  `HISTORICAL_EVALUATION`; operational `PUBLISHED` responses have an explicit
  fresh/stale contract.
- Payload/range/page/bounds guards, 10-second query timeout, Admin RBAC, global
  rate limiting and stable `400/403/404/429/503` contracts are in place.
- MAE/RMSE/WAPE are supplied by PostgreSQL from the evaluation store or
  actual-backfilled rows; the UI does not recalculate them.
- Serving/hotspot indexes and the existing geometry GiST index are verified by
  Testcontainers query plans.
- Phase 8 focused suite passes 11/11; the existing Admin Analytics regression
  suite passes 19/19.
- Full contract and limitations are recorded in
  [`admin-demand-forecasting/phase-08-serving-api-evidence.md`](admin-demand-forecasting/phase-08-serving-api-evidence.md).

---

## 11. Phase 9 Evidence

- Two new `/analytics` tabs consume the Phase 8 read contract without runtime
  mocks or legacy trip-filter requests.
- Evaluation metrics remain backend-owned; the UI does not recompute or average
  MAE/RMSE/WAPE.
- Model artifact paths remain absent; only safe metadata and shortened checksum
  are displayed.
- Distinct empty/stale/403/404/422/429/503 states and keyboard tab navigation
  are covered by component/page tests.
- Frontend lint passes, 48 test files / 217 tests pass and production build
  transforms 2,607 modules successfully.
- Full evidence and limitations are recorded in
  [`admin-demand-forecasting/phase-09-frontend-evidence.md`](admin-demand-forecasting/phase-09-frontend-evidence.md).

---

## 12. Phase 10 Evidence

- The forecast tab renders backend GeoJSON cells with Forecast, Actual and
  Absolute Error modes, UTC target-bucket navigation and map-bounds filtering.
- Run, range, horizon and grid limits are validated before requests; changed
  filters abort prior requests and hide stale map/hotspot responses.
- Uncertainty, freshness, quality, demand units, actual coverage and Porto
  `RESEARCH_DEMONSTRATION` boundaries remain visible.
- Hotspot rank/error values remain backend-owned and open an auditable cell
  drawer rather than being recomputed in the browser.
- Frontend lint passes, 50 test files / 237 tests pass and production build
  transforms 2,610 modules successfully.
- Full evidence and limitations are recorded in
  [`admin-demand-forecasting/phase-10-forecast-ui-evidence.md`](admin-demand-forecasting/phase-10-forecast-ui-evidence.md).

---

## 13. Phase 11 Final Evidence

- Frozen verification passed for 296 files / 2,074,459,267 bytes. The report
  checksum is `e64b5a8...ae433`.
- Privacy suppression hides positive actual counts below three together with
  error/evaluation time; aggregate forecast values remain available.
- Synthetic PostGIS serving P95 is 8.241 ms for demand GeoJSON and 9.524 ms for
  hotspots over 100 measured calls per endpoint. This is plumbing evidence,
  not a production load or accuracy claim.
- Python 3.11/3.12 each pass 98 tests (five opt-in skips); Spring passes 135
  suites / 584 tests with no skip; Admin Web passes 50 files / 238 tests, lint
  and production build.
- Final traceability, failure matrix, security boundary and limitations are in
  [`admin-demand-forecasting/phase-11-final-evidence.md`](admin-demand-forecasting/phase-11-final-evidence.md).

---

## 14. Review Gate

Phase 11 implementation is complete and ready for user/thesis review. Porto
evidence remains visibly separated from live Ho Chi Minh City operations.
Further work is deployment hardening or a newly approved scope, not an
unfinished Phase 11 requirement.
