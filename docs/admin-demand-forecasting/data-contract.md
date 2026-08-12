# Demand forecasting data contract

> Version: 1.0
>
> Status: Frozen for Phase 0
>
> Reporting interval convention: `[from, to)`

## 1. Purpose

This contract defines the canonical event, grid, time-bucket, target, feature
availability and quality semantics used by extraction, training, evaluation,
forecast persistence, Spring APIs and Admin UI. Source adapters may differ,
but downstream code must not silently change these semantics.

## 2. Source profiles and claim boundary

| Profile | Source | Primary use | Demand event semantics |
| --- | --- | --- | --- |
| `porto-thesis` | Porto Taxi `train.csv.zip` | Real-data method evaluation | Observed trip start/pickup proxy |
| `goride-local` | GoRide `trips` and Analytics telemetry | Integration and future operational training | Actual request creation at `requested_at` |
| `integration` | Small deterministic fixture | Automated correctness tests | Explicit fixture semantics |

Porto and GoRide metrics are reported separately. A Porto result does not
measure GoRide/Ho Chi Minh City accuracy. Synthetic GoRide data verifies the
pipeline and UI, not external validity.

## 3. Source contracts

### 3.1 Porto Taxi

Expected CSV header inside the ZIP:

```text
TRIP_ID,CALL_TYPE,ORIGIN_CALL,ORIGIN_STAND,TAXI_ID,TIMESTAMP,DAY_TYPE,MISSING_DATA,POLYLINE
```

Required source fields:

| Field | Rule |
| --- | --- |
| `TRIP_ID` | Non-empty source identity; duplicates are rejected from the canonical snapshot |
| `TIMESTAMP` | Unix epoch seconds parsed as a UTC instant; convert that instant to `Europe/Lisbon` only for calendar features/display |
| `MISSING_DATA` | Rows marked true are excluded from the forecasting population |
| `POLYLINE` | Valid JSON-like coordinate sequence; first `[longitude, latitude]` is the pickup proxy |

`CALL_TYPE`, origin fields, taxi identity and destination trajectory are not
core forecasting features in version 1. Taxi identity must not be exposed in
forecast artifacts or Admin APIs.

### 3.2 GoRide

Required fields:

| Field | Rule |
| --- | --- |
| `trips.id` | Stable source identity |
| `trips.requested_at` | Demand event time |
| `trips.pickup_location` | Valid EPSG:4326 pickup geometry |
| `trips.vehicle_type` | Optional slice/feature only when coverage is sufficient |
| `driver_supply_snapshots` | Optional lagged supply feature with visible coverage |
| `service_areas` | Spatial filter/dimension; current boundary is not historical truth |

Trip outcome is not used to decide whether a request counts as demand. A
cancelled or no-driver trip remains one request when its request timestamp and
pickup are valid.

## 4. Canonical demand event

The extraction output contains one row per accepted source trip:

```text
source_profile          STRING NOT NULL
dataset_version         STRING NOT NULL
source_trip_key         STRING NOT NULL
demand_event_time_utc   TIMESTAMPTZ NOT NULL
demand_event_semantics  ENUM(REQUEST_CREATED, TRIP_STARTED_PROXY) NOT NULL
pickup_wgs84            POINT(EPSG:4326) NOT NULL
vehicle_type            STRING NULL
service_area_key        STRING NULL
source_cutoff_utc       TIMESTAMPTZ NOT NULL
extraction_run_id       UUID NOT NULL
```

Uniqueness key:

```text
source_profile + dataset_version + source_trip_key
```

The canonical layer excludes passenger, driver, phone, payment and full
trajectory fields.

## 5. Time semantics

- Persistent instants use UTC.
- Calendar features use the source profile's IANA timezone.
- Porto uses `Europe/Lisbon`; GoRide uses `Asia/Ho_Chi_Minh`.
- Porto Unix epoch values are never parsed as timezone-less local timestamps.
- Bucket size is 15 minutes in version 1.
- Buckets use `[bucket_start, bucket_start + 15 minutes)`.
- Horizons are 15, 30 and 60 minutes.
- DST-sensitive Porto bucket construction uses timezone-aware instants; local
  display labels may repeat or skip, but UTC bucket identity must remain
  unique.

Given inference cutoff `t`, horizon `h` predicts the bucket beginning at
`t + h`. The cutoff must lie on a 15-minute boundary for published forecasts.

## 6. Spatial semantics

### 6.1 Grid identity

Points are transformed from EPSG:4326 into the profile's metric projected CRS
before grid assignment. A cell identity is:

```text
<grid-version>:<projected-srid>:<cell-size-meters>:<grid-x>:<grid-y>
```

The grid origin and boundary rule are part of `grid-version`. Geometry returned
to the API is transformed back to EPSG:4326 GeoJSON.

### 6.2 Sizes

- Primary Porto configuration: 500 m.
- Porto sensitivity configurations: 1,000 m and 2,000 m.
- Porto 250 m is profiling-only until sparsity evidence justifies it.
- Porto projected CRS is EPSG:3763 (`ETRS89 / Portugal TM06`, metre).
- GoRide supports the existing 250, 500, 1,000 and 2,000 m set.

Results at different cell sizes are separate experiments, not rows merged into
one metric without a dimension.

### 6.3 Boundary behavior

- A point on a grid boundary is assigned by the same floor/origin rule used by
  historical heatmap SQL.
- Invalid/out-of-study-area points are rejected with a quality reason.
- The study-area bounds are derived once during profiling, reviewed and frozen
  in a versioned manifest before reported experiments.

Version 1 freezes `square-zero-floor-v1`: projected X/Y are divided from a
zero-metre origin and assigned with `floor`. The Porto WGS84 study bounds are
longitude `[-8.75, -8.45)` and latitude `[41.05, 41.30)`; the GoRide-local
bounds are longitude `[106.45, 107.05)` and latitude `[10.55, 11.05)`. The
minimum edge is inclusive and the maximum edge is exclusive. These bounds are
part of the versioned profile and must not be inferred again during a run.

## 7. Target definition

For cell `c` and target bucket `b`:

```text
y(c, b) = count of accepted canonical demand events whose pickup belongs to c
          and demand_event_time belongs to b
```

- Target unit: trip-start proxies for Porto; trip requests for GoRide.
- Target is a non-negative integer before model transformation.
- Missing buckets inside the frozen study interval are materialized with zero
  after source completeness checks pass.
- Missing source coverage is not converted into zero.

Every evaluation table/report must carry `demand_event_semantics` so the Porto
proxy cannot be mislabeled as observed request creation.

### 7.1 Evaluation cell population

Porto version 1 evaluates the boundary-tied cells required to cover at least
95% of demand observed in the train split. Ranking and the boundary are computed
without validation/test rows. The frozen selected cell IDs are reused for all
later folds, horizons and candidate models so comparisons use the same
population. Excluded tail cells remain in canonical evidence and their demand
coverage is reported; conclusions must not claim full-city cell coverage.

Feature rows are partitioned by `target_bucket_start_utc` calendar month. The
partition dimension changes storage/layout only, not feature or target
semantics.

Database publication copies one verified Parquet partition at a time into a
transaction-local PostgreSQL staging table, then merges it idempotently into
`analytics.demand_features`. All partitions and the processing-run terminal
transition share one outer transaction: readers observe either the complete
feature set or none of it.

An interrupted database publication may resume from an existing immutable
feature artifact. Resume must revalidate the source run/config, top-level
checksums, dataset-manifest checksum, each partition's SHA-256/byte count/
Parquet row count, quality status and row cost guards before connecting to the
database. Resume writes a new audit/evidence run that references the original
feature artifact and never mutates it.

## 8. Feature availability contract

A feature used to predict target bucket `b` at inference cutoff `t` is valid
only when all of its source events would have been available at or before `t`.

Version 1 groups:

| Group | Features | Availability rule |
| --- | --- | --- |
| Calendar | hour/day cyclical values, weekday, weekend | Derived from target bucket and known calendar only |
| Demand lag | 1, 2, 4, 96 and 672 buckets where history permits | Source bucket ends at or before cutoff |
| Rolling demand | 4, 12, 96 and 672 trailing buckets | Window is closed on cutoff; no centered window |
| Spatial neighbor | Lagged demand of adjacent cells | Same cutoff rule as demand lag |
| Supply | Lagged available/online drivers and coverage | GoRide only; snapshot timestamp at or before cutoff |

Weather, events, holidays and pricing are outside feature set version 1.

### 8.1 Leakage examples

At cutoff `2026-08-08T10:00:00Z`, predicting the bucket beginning 10:15Z:

- demand from 09:45-10:00Z is allowed after the bucket closes;
- demand from 10:00-10:15Z is forbidden;
- completed/cancelled outcome recorded after 10:00Z is forbidden;
- a rolling window including 10:00-10:15Z is forbidden;
- feature normalization fitted using May-June test data is forbidden;
- tuning based on final holdout metrics is forbidden.

The feature builder must test maximum source timestamp per row/group and fail
when it exceeds the row's inference cutoff.

## 9. Canonical feature row

```text
feature_set_version
source_profile
dataset_version
grid_version
cell_id
cell_size_meters
bucket_start_utc
inference_cutoff_utc
target_bucket_start_utc
horizon_minutes
demand_event_semantics
calendar_features...
demand_lag_features...
rolling_features...
neighbor_features...
supply_features...
coverage_ratio
quality_status
created_by_run_id
```

Offline training labels may be joined after the feature row has been built
using cutoff-safe inputs. Online inference rows have no target/actual value at
creation time.

## 10. Quality gates

| Code | Severity | Rule |
| --- | --- | --- |
| `DQ_SCHEMA` | FAIL | Required fields and types exist |
| `DQ_CHECKSUM` | FAIL | Source checksum matches manifest |
| `DQ_DUPLICATE_TRIP` | WARN/excluded below configured ratio; FAIL above | Canonical source identity is unique; the first immutable source occurrence wins |
| `DQ_MISSING_EVENT_TIME` | FAIL | Demand event time exists |
| `DQ_FUTURE_EVENT` | FAIL | Event time is not after extraction cutoff |
| `DQ_INVALID_PICKUP` | FAIL | Pickup is valid EPSG:4326 and transformable |
| `DQ_MISSING_TRAJECTORY` | WARN/excluded | Porto declares missing data or has an empty trajectory |
| `DQ_BUCKET_CONTINUITY` | FAIL | Expected bucket index is continuous after completeness validation |
| `DQ_CELL_COVERAGE` | WARN | Cell lacks minimum training history |
| `DQ_SUPPLY_COVERAGE` | WARN/feature disabled | GoRide supply snapshots are incomplete |
| `DQ_LEAKAGE` | FAIL | Any feature source timestamp exceeds cutoff |
| `DQ_DRIFT` | WARN | Volume/missing/cell distribution exceeds frozen threshold |

Threshold values live in versioned config and are copied into the run
manifest. A `FAIL` prevents stage promotion. A `WARN` remains visible in stored
quality results and the Admin UI.

## 11. Persistence and idempotency

- Extraction snapshot identity includes dataset version, cutoff, config hash
  and code commit.
- Feature uniqueness includes feature-set version, cell, bucket and horizon.
- Forecast uniqueness includes forecast run, cell, target bucket and horizon.
- Rerunning the same immutable input/config either reuses a completed artifact
  after checksum verification or creates a separately identified attempt; it
  never silently overwrites evidence.
- Actual demand/error is backfilled only after target-bucket closure plus the
  configured source watermark.

## 12. Privacy and API boundary

- Forecast APIs expose aggregate cells, counts, intervals and quality flags.
- They do not expose source trip IDs, taxi IDs, passenger/driver IDs or raw
  coordinates/trajectories.
- Low-count suppression thresholds are frozen before any real GoRide data is
  exposed outside the trusted Admin environment.
- Logs and metric tags never contain cell IDs with raw user/trip identity.
