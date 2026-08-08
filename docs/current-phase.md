# GoRide Current Phase

> Last updated: 2026-08-08, Asia/Ho_Chi_Minh
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Active feature: Admin demand-forecasting processing layer.
- Current branch: `codex/admin-demand-forecasting`.
- Backend base: `develop` commit `ae3dac2` (`merge admin-v2`).
- Frontend integration baseline: `goride-web` branch `codex/admin_v2`, commit
  `5acdf06` (`docs: record phase 11 release hardening review`).
- Branch-base exception: the repository convention normally starts features
  from `main`, but `main` does not contain Admin Analytics Phase 0-8. This
  feature starts from `develop` because its schema, PostGIS grid, telemetry and
  API contracts are required dependencies.
- User-local `.env.example` remains outside the analytics commits unless
  reviewed separately.

---

## 2. Active Phase Ready For Review

Phase 2 — Forecast analytics database release from
[`admin-analytics-processing-layer-implementation-plan.md`](admin-analytics-processing-layer-implementation-plan.md).

User approval to start Phase 2: 2026-08-08.

Completed commit:

```text
2e460fa feat: add demand forecasting analytics schema
```

Scope:

- add one reviewed `precheck/apply/verify/rollback/manifest` release;
- create persistent contracts for processing runs, quality results, demand
  features, model versions, forecast runs, cell forecasts and evaluations;
- enforce lifecycle, checksum, UTC bucket, horizon, cell-size, geometry/SRID,
  foreign-key and idempotency constraints;
- add query/retention-oriented B-tree and GiST indexes;
- add transactional integration fixtures for valid rows, duplicate rejection,
  invalid-state rejection and forecast evaluation backfill;
- prove apply, verify, rollback and re-apply on PostgreSQL/PostGIS.

Out of scope for Phase 2:

- reading or transforming the full Porto CSV;
- feature computation, model fitting or prediction;
- Python database adapter or persistence repositories;
- Spring forecast APIs;
- frontend forecast screens.

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

## 4. Phase 2 Evidence

Implementation and validation are complete. User review is required before
Phase 3 starts.

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

## 5. Next Expected Work

Phase 2 stops at this review gate. After user approval, Phase 3 may add
deterministic extraction and data-quality persistence. Full Porto validation
additionally requires this field in the external dataset manifest:

```json
"sourceRelativePath": "raw/porto-taxi/v1/train.csv.zip"
```
