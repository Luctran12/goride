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

User approval to continue after external Porto manifest repair: 2026-08-09.
The full-data validation follow-up remains in Phase 4 until the empirical
quality findings and projected feature volume are reviewed.

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
- profile the complete Porto archive, classify source exclusions and freeze a
  bounded duplicate-rate threshold before full feature materialization;

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

---

## 7. Next Expected Work

Phase 4 remains at a decision gate. Do not run the full feature build until the
cell population, partition size and maximum-row cost guard are frozen. Phase 5
may add historical-mean and seasonal-naive baselines only after the bounded
Phase 4 feature artifact passes leakage, determinism and persistence review.
