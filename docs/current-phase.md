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

## 2. Active Phase

Phase 2 — Forecast analytics database release from
[`admin-analytics-processing-layer-implementation-plan.md`](admin-analytics-processing-layer-implementation-plan.md).

User approval to start Phase 2: 2026-08-08.

Planned commit:

```text
feat: add demand forecasting analytics schema
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

## 4. Phase 1 Approved Baseline

Phase 1 was approved when the user requested Phase 2. Its package, dependency
locks, validation behavior and two commits remain the implementation baseline.

Validation evidence:

- 19/19 unit and CLI tests passed on Python 3.11.9 and Python 3.12.13.
- Dependency-lock contract matches `pyproject.toml` for runtime and optional
  model-spike packages.
- `HistGradientBoostingRegressor` compatibility spike passed deterministic
  fit/predict with NumPy 1.26.4 and scikit-learn 1.9.0.
- GoRide profile validation passed and returned
  `CONFIG_VALID_CONNECTION_DEFERRED_TO_PHASE_3` as designed.
- The real Porto profile returned exit code 3 and
  `DATASET_MANIFEST_FIELD_MISSING` because the external manifest lacks
  `sourceRelativePath`; no path was inferred and no artifact was written.
- Path traversal, checksum mismatch, unsafe run slug, chronological overlap,
  output overwrite and structured-log redaction all have regression tests.
- `git diff --check` passed before the implementation commit.

Manual review replaced optimization-sensitive boundary assertions with an
explicit dataset error, enforced safe run/profile slugs, added microseconds to
run IDs and retained fail-closed output allocation. The external
credential-rotation action from Phase 0 remains an operational responsibility.

---

## 5. Next Expected Work

After implementation, Phase 2 must stop at a user review gate. Phase 3 may add
deterministic extraction and data-quality persistence only after the schema,
rollback evidence and idempotency constraints are approved. Full Porto
validation additionally requires this field in the external dataset manifest:

```json
"sourceRelativePath": "raw/porto-taxi/v1/train.csv.zip"
```
