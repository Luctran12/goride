# Demand forecasting evaluation protocol

> Version: 1.0
>
> Status: Frozen for Phase 0
>
> Primary dataset: Porto Taxi `porto-2013-07_2014-06-v1`

## 1. Objective and claim

Evaluate whether a versioned spatio-temporal feature pipeline and a tabular
candidate model improve short-horizon aggregate pickup-demand prediction over
simple historical baselines, and measure how spatial resolution and feature
groups affect error and system cost.

The experiment evaluates a forecasting method on Porto taxi trip-start demand.
It does not establish accuracy for GoRide or Ho Chi Minh City and does not
evaluate driver rebalancing, dynamic pricing or matching changes.

## 2. Research questions

| ID | Question |
| --- | --- |
| RQ1 | How do MAE, RMSE and WAPE of the candidate compare with historical-mean and seasonal-naive baselines at 15, 30 and 60 minutes? |
| RQ2 | How do 500, 1,000 and 2,000 m grids change forecast error, zero-demand sparsity, processing time and storage? |
| RQ3 | What is the incremental contribution of calendar, demand-lag, spatial-neighbor and eligible supply feature groups? |
| RQ4 | What processing duration, inference latency, forecast freshness, API latency and storage cost does the artifact introduce? |

GoRide-only supply ablation is reported as integration/future operational
evidence unless a real GoRide dataset has sufficient coverage. It is not mixed
with Porto RQ3 because Porto has no matching supply source.

## 3. Dataset identity

```text
name: Porto Taxi
version: porto-2013-07_2014-06-v1
source artifact: train.csv.zip
period: 2013-07-01 through 2014-06-30
source CRS: EPSG:4326
source timezone: Europe/Lisbon
license: CC BY 4.0
sha256: 210dd0a20da66a8fc2de3440aecd84670921bc257591f8365a4475e31453c5ea
```

The source ZIP and large derivatives remain outside Git. The dataset manifest,
configuration, code commit, raw prediction checksums and summary are retained.

## 4. Population and exclusions

Include a source row when:

- `TRIP_ID` is present and unique;
- `MISSING_DATA` is false;
- timestamp is inside the frozen dataset interval;
- polyline contains a valid first coordinate;
- pickup transforms into the reviewed study bounds.

Report counts for every exclusion reason. Do not remove peak periods, zero
buckets or high-demand cells merely because they increase error. Outlier rules
must be frozen from training/profiling data and applied unchanged to holdout
data.

## 5. Spatial and temporal configurations

Core time bucket: 15 minutes.

Forecast horizons:

```text
H15 = 15 minutes
H30 = 30 minutes
H60 = 60 minutes
```

Spatial experiments:

```text
G500  = 500 m primary grid
G1000 = 1,000 m sensitivity grid
G2000 = 2,000 m sensitivity grid
```

A 250 m grid may be profiled but is excluded from core reporting unless its
coverage/sparsity gate is approved before final evaluation.

## 6. Chronological split and tuning

Random record splitting is prohibited.

Development walk-forward folds:

| Fold | Training interval | Validation interval |
| --- | --- | --- |
| D1 | 2013-07-01 to 2014-01-01 | 2014-01-01 to 2014-02-01 |
| D2 | 2013-07-01 to 2014-02-01 | 2014-02-01 to 2014-03-01 |
| D3 | 2013-07-01 to 2014-03-01 | 2014-03-01 to 2014-04-01 |
| D4 | 2013-07-01 to 2014-04-01 | 2014-04-01 to 2014-05-01 |

Intervals are interpreted in `Europe/Lisbon` and converted to immutable UTC
cutoffs in the run manifest.

Final holdout:

```text
train selected configuration: 2013-07-01 to 2014-05-01
final test:                  2014-05-01 to 2014-07-01
```

Model family, feature groups, hyperparameter search space and selection rule
are frozen using D1-D4. The final holdout is evaluated once for the reported
primary run. Any rerun caused by a defect is retained and documented rather
than silently replacing evidence.

## 7. Models

### Required baselines

1. `HISTORICAL_MEAN`: mean demand for the same cell and local time slot using
   training history only.
2. `SEASONAL_NAIVE`: most recent available value for the same cell and weekly
   seasonal slot, with a documented fallback when history is absent.

### Candidate

One gradient-boosted decision-tree implementation selected in Phase 1 after a
runtime/licensing compatibility spike. All models use the same fold, target,
grid and metric code.

### Conditional extension

STGCN or another neural spatio-temporal model is not core. It is added only
after baseline/candidate completion, sufficient data evidence and an approved
Phase 6 decision. Its absence does not make the thesis artifact incomplete.

## 8. Feature ablation

Porto ablation order:

```text
A0 calendar only
A1 calendar + demand lags/rolling history
A2 A1 + lagged spatial-neighbor demand
```

GoRide integration ablation may add:

```text
A3 A2 + lagged driver-supply features with coverage
```

Every comparison uses identical folds and model search budget. A feature group
is not claimed useful from training fit alone.

## 9. Metrics

For observations `y_i` and predictions `p_i`:

```text
MAE  = mean(abs(y_i - p_i))
RMSE = sqrt(mean((y_i - p_i)^2))
WAPE = sum(abs(y_i - p_i)) / sum(abs(y_i))
```

WAPE is null/not reported for a slice whose denominator is zero. MAPE is not a
primary metric because cell-bucket demand frequently equals zero.

Report metrics by:

- model;
- development fold/final holdout;
- horizon;
- grid size;
- demand-volume quantile;
- time-of-day slice.

The primary comparison is final-holdout WAPE and MAE on G500 for each horizon.
RMSE describes sensitivity to larger misses.

## 10. Uncertainty and comparisons

- Preserve one prediction/error row per evaluated cell-bucket-horizon.
- Report point estimates across all final holdout observations.
- When computing confidence intervals, use paired resampling over time blocks
  so competing models are compared on the same observations and temporal
  dependence is not treated as independent rows.
- Report absolute and relative differences; do not report relative improvement
  when the baseline metric is zero/null.
- A non-improving candidate is a valid negative result and remains in the
  artifact set.

Prediction intervals are optional. If implemented, their method, nominal
coverage, empirical coverage and average width are reported separately from
point-forecast metrics.

## 11. System evaluation

Record for each profile/grid/run:

- extraction, quality, feature, training and inference duration;
- rows read/written and peak process memory when available;
- raw/intermediate/feature/model/forecast storage bytes;
- forecasts per second;
- forecast freshness lag;
- Spring API P50/P95 latency, payload size and error count for frozen queries.

System results use a recorded environment manifest and are not compared across
different machines as one controlled experiment.

## 12. Correctness gate

Before timing or reporting model quality:

1. Source checksum matches the dataset manifest.
2. All FAIL-level data-quality rules pass.
3. Freeze the boundary-tied 95% cumulative train-demand cell population and
   reuse the exact cell IDs for validation, test, baselines and candidates.
4. Grid boundary fixtures pass.
5. Bucket continuity and timezone/DST fixtures pass.
6. Leakage guards prove every feature source timestamp is at/before cutoff.
7. Metric unit tests match hand-calculated examples, including zero WAPE.
8. Baseline and candidate predictions cover the same evaluated population.
9. Raw prediction row count and checksum are recorded.

Failure stops the reported run; failed artifacts are retained with status.

## 13. Reproducibility artifact

Each reported run contains:

```text
environment.json
dataset-manifest.json
config.yml
folds.json
quality.json
feature-manifest.json
model-card.json
predictions/*.parquet
metrics.json
system-metrics.json
checksums.sha256
README.md
```

The manifest records Git commit, dirty state, Python/dependency versions,
PostgreSQL/PostGIS versions, OS/CPU/RAM, timezone, projected CRS, grid version,
seed, model parameters and artifact checksums.

## 14. Reporting rules

- Say `trip-start demand proxy` for Porto, not observed app request demand.
- Distinguish smoke, development folds and final holdout.
- Distinguish measured result, interpretation and inference.
- Link every thesis table/figure to run ID and raw artifact checksum.
- Report exclusions, warnings, failed runs, negative results and known limits.
- Do not generalize Porto accuracy to GoRide, Ho Chi Minh City or driver supply.
