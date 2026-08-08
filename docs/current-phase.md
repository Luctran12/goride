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
- User-local `.env.example` remains outside the Phase 0 commit unless reviewed
  separately.

---

## 2. Active Phase

Phase 1 — Processing project scaffold and reproducibility foundation from
[`admin-analytics-processing-layer-implementation-plan.md`](admin-analytics-processing-layer-implementation-plan.md).

User approval to start Phase 1: 2026-08-08.

Planned commit:

```text
feat: scaffold reproducible analytics processing layer
```

Scope:

- create an installable `analytics-processing` Python package;
- freeze Python/runtime and minimal dependency ranges;
- implement strict YAML profile loading and environment resolution;
- implement dataset manifest/path/SHA-256 validation;
- create deterministic config hash, run identity and run manifest metadata;
- emit structured JSON logs without secrets;
- expose skeleton commands `extract`, `build-features`, `train`, `evaluate`
  and `forecast` that fail as not implemented instead of fabricating output;
- add standard-library unit/CLI tests and local validation instructions.

Out of scope for Phase 1:

- database connection and forecast schema;
- reading or transforming the full Porto CSV;
- feature computation, model fitting or prediction;
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

## 4. Phase 0 Evidence

Implementation and local validation are complete. User review is required, and
the external credential-rotation action remains mandatory.

Validation evidence:

- 33/33 focused Analytics/configuration tests passed.
- All 12 database release descriptors passed `validate-db-release.ps1 -All`.
- Porto SHA-256, ZIP structure, CSV header and first data row passed.
- PostgreSQL 18/PostGIS 3.6.2 database and EPSG:3763 transform smoke passed.
- Documentation links and `git diff --check` passed.
- Manual review corrected Unix-epoch timezone semantics and froze EPSG:3763.

Phase 0 contracts were approved by the user when Phase 1 was requested. The
external credential-rotation action remains an operational responsibility.
The Porto manifest still lacks `sourceRelativePath`; Phase 1 validation must
report this explicitly and must not silently infer a source path.

---

## 5. Next Expected Work

After implementation, Phase 1 must stop at a user review gate. Phase 2 may add
the forecast database release only after the package, dependency lock, failure
semantics and validation evidence are approved.
