# Model Card — Porto HGB Demand Forecast Candidate

## Identity and status

| Field | Value |
| --- | --- |
| Model version | `porto-hgb-v1-g500-63f545298c-f4a46557d3` |
| Family | Histogram Gradient Boosting regression |
| Feature set | A2 — calendar, demand history and spatial neighbors |
| Grid / bucket | 500 m / 15 minutes |
| Horizons | 15, 30 and 60 minutes |
| Artifact SHA-256 | `8643527f721db9e86c8721bd2262b36a5587ac0b13ccd68ad4feb563114db5b4` |
| Experiment hash | `63f545298cd5fa879876efd0a82252c6aea4fccea9f95ab5e37ed6134eeb0788` |
| Lifecycle | `APPROVED` only for `RESEARCH_DEMONSTRATION` |

This model is an evaluated thesis artifact. It is not approved for live
Ho Chi Minh City dispatch, driver allocation or pricing decisions.

## Intended use

- Demonstrate a reproducible spatio-temporal demand-processing layer.
- Compare a tabular candidate with historical-mean and seasonal-naive baselines.
- Generate historical Porto forecast cells for Admin Analytics inspection.
- Support analysis of horizon, grid-size and feature-ablation trade-offs.

Prohibited uses include individual mobility profiling, driver/passenger ranking,
automated operational decisions and claims about live demand in another city.

## Training and evaluation data

The method dataset is Porto Taxi `porto-2013-07_2014-06-v1`, licensed CC BY 4.0.
The target is trip-start demand aggregated to a fixed projected grid and
15-minute buckets. It is a proxy for observed served trips, not all latent
ride-hailing requests. See [data-sheet.md](data-sheet.md).

Chronological rolling-origin folds D1–D4 are used for tuning. The FINAL holdout
is inspected only after the A2 configuration is locked. Candidate and baselines
use the same folds, cutoffs and complete evaluation population.

## Locked configuration

The selected HGB_C2 configuration uses learning rate 0.06, 80 iterations,
31 leaf nodes, minimum 50 samples per leaf and L2 regularization 1.0. Training
uses deterministic hash-stratified bounded samples; validation and FINAL rows
are not sampled.

## FINAL results at the primary 500 m grid

| Horizon | Candidate MAE | Candidate RMSE | Candidate WAPE | Historical WAPE | Seasonal WAPE |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 15 | 0.374862 | 0.749781 | 1.009661 | 0.951110 | 1.105694 |
| 30 | 0.378400 | 0.759382 | 1.019189 | 0.951110 | 1.105694 |
| 60 | 0.381554 | 0.774807 | 1.027685 | 0.951110 | 1.105694 |

The candidate beats seasonal naive on MAE/RMSE/WAPE at every tested grid and
horizon, but does not beat historical mean on primary G500 MAE or WAPE. Across
all nine grid/horizon comparisons it improves all three historical-mean metrics
only at G2000-H15. This negative result is part of the model card, not hidden.

## Feature and resolution findings

- Demand-history features provide the dominant improvement over calendar-only A0.
- Spatial neighbors add a smaller but consistent WAPE improvement from A1 to A2.
- Coarser cells lower scale-normalized WAPE but reduce spatial resolution.
- MAE cannot be compared directly across grid sizes because the target count scale changes.

## Operational evidence

The official historical inference writes 402 rows (134 cells × three horizons)
in 0.3464 seconds. Actual backfill updates 402/402 rows after the watermark.
Reruns are idempotent and an injected terminal failure publishes zero visible rows.

## Privacy and safety

- The Admin API exposes aggregate cell geometry and never trip/user/driver identifiers.
- Actual/error values with positive aggregate demand below 3 are suppressed.
- Model artifact paths are internal and excluded from serving DTOs.
- Porto responses always carry `RESEARCH_DEMONSTRATION` and historical freshness labels.

## Limitations

- Porto 2013–2014 mobility and taxi operations do not represent current TP.HCM.
- Trip starts omit rejected, cancelled-before-start and unmet requests.
- No calibrated interval method was frozen before FINAL; intervals may be absent.
- G500 peak memory was not captured due a corrected Windows collector defect.
- The strongest simple baseline remains competitive and superior on primary WAPE.

## Traceability

The frozen training artifact is
`runs/training/20260810T083830837344Z-f4a46557d3fd-porto-thesis-4cf869ca75a0`
under the external data root. Phase 11 verification report SHA-256 is
`e64b5a8f7455b3bbfaa892b4401ac9c8059d5e4d5ecd6757514f7bf9cd7ae433`.
