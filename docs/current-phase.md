# GoRide Current Phase

> Last updated: 2026-08-09, Asia/Ho_Chi_Minh
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

## 2. Active Phase

Phase 4 — Spatial-temporal aggregation and feature pipeline from
[`admin-analytics-processing-layer-implementation-plan.md`](admin-analytics-processing-layer-implementation-plan.md).

User approval to start Phase 4: 2026-08-09.

Planned commit:

```text
feat: build versioned spatio-temporal demand features
```

Scope:

- freeze grid version, zero-origin floor rule and profile study-area bounds;
- aggregate canonical demand into continuous 15-minute cell buckets;
- build calendar, demand lag, trailing rolling and spatial-neighbor features;
- add optional cutoff-safe GoRide supply lag with visible coverage;
- emit a versioned feature dictionary, manifest and deterministic Parquet;
- enforce leakage, bucket continuity, grid assignment and coverage gates;
- persist FEATURE_BUILD lifecycle, quality evidence and idempotent feature rows;
- prove boundary, empty-bucket, DST, uniqueness and checksum behavior.

Out of scope for Phase 4:

- baseline/candidate model fitting, evaluation or prediction;
- model registry and scheduled inference;
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

Implementation and review are complete. User review is required before Phase 4.

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

## 6. Next Expected Work

After implementation, Phase 4 must stop at a user review gate. Phase 5 may add
historical-mean and seasonal-naive baselines with walk-forward evaluation only
after feature leakage, determinism and persistence evidence are approved. Full
Porto validation still requires this field in the external dataset manifest:

```json
"sourceRelativePath": "raw/porto-taxi/v1/train.csv.zip"
```
