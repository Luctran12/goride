# Phase 6 candidate-model evidence

- Date: 2026-08-10
- Profile: `porto-thesis`
- Dataset: `porto-2013-07_2014-06-v1`
- Experiment hash: `63f545298cd5fa879876efd0a82252c6aea4fccea9f95ab5e37ed6134eeb0788`

## Outcome

Phase 6 is complete. HistGradientBoosting was tuned on development folds D1-D4
only, one configuration was locked per ablation, and the FINAL holdout was then
evaluated once. The same fold/cutoff contract and complete observation
population were used for the candidate and both Phase 5 baselines.

The result is deliberately reported as mixed rather than as a universal model
improvement:

- A2 (calendar, demand history and spatial-neighbor features) improves A1 and
  A0 on FINAL for every tested grid and horizon.
- A2 beats seasonal naive on MAE, RMSE and WAPE in all nine grid/horizon tests.
- Against historical mean, A2 improves all three metrics only for G2000-H15.
  It improves RMSE in seven of nine tests, but improves MAE and WAPE in only one
  of nine tests.
- On the primary G500 grid, A2 does not improve the primary MAE/WAPE criteria
  over historical mean. This negative primary result is retained in full.

The evidence supports the usefulness of history and neighbor features and
shows a spatial-resolution trade-off. It does not support a claim that the
candidate universally outperforms the strongest simple baseline.

## Frozen implementation

| Commit | Purpose |
| --- | --- |
| `f4a4655` | HGB search, A0/A1/A2 ablation, full-fold evaluation, model card and serialization |
| `9a612b2` | Correct Windows peak-working-set collection for subsequent runs |
| `2ae7154` | Parquet-only feature builds for sensitivity experiments without PostgreSQL feature publication |

The search budget contains three HGB configurations, three feature groups,
three horizons and four development folds: 108 development fits. Selection is
by pooled development WAPE, then MAE, then frozen configuration order. Training
uses a deterministic, hash-stratified bounded sample; validation and FINAL are
never sampled.

Prediction intervals remain disabled because no calibrated interval method was
frozen before FINAL evaluation. Adding an interval method after inspecting the
holdout would invalidate the protocol.

## Artifact inventory

| Grid | Feature artifact | Baseline artifact | Candidate artifact |
| ---: | --- | --- | --- |
| G500 | `20260809T153840318116Z-6f2ea5e3b834-porto-thesis-4cf869ca75a0` | `20260810T075446729084Z-3c08e4269888-porto-thesis-4cf869ca75a0` | `20260810T083830837344Z-f4a46557d3fd-porto-thesis-4cf869ca75a0` |
| G1000 | `20260810T091335355060Z-2ae7154c60d3-porto-thesis-4cf869ca75a0` | `20260810T092314641438Z-2ae7154c60d3-porto-thesis-4cf869ca75a0` | `20260810T092802643667Z-2ae7154c60d3-porto-thesis-4cf869ca75a0` |
| G2000 | `20260810T093527097412Z-2ae7154c60d3-porto-thesis-4cf869ca75a0` | `20260810T093742000671Z-2ae7154c60d3-porto-thesis-4cf869ca75a0` | `20260810T093827894213Z-2ae7154c60d3-porto-thesis-4cf869ca75a0` |

G1000 contains 4,309,510 feature rows, 4,273,512 baseline predictions and
6,410,268 candidate predictions. G2000 contains 1,366,430 feature rows,
1,355,016 baseline predictions and 2,032,524 candidate predictions. Both
sensitivity feature runs use `publicationMode = PARQUET_ONLY`; PostgreSQL
contains zero `demand_features` rows created by either run.

## Locked configurations

| Grid | A0 | A1 | A2 |
| ---: | --- | --- | --- |
| G500 | HGB_C3 | HGB_C2 | HGB_C2 |
| G1000 | HGB_C1 | HGB_C2 | HGB_C2 |
| G2000 | HGB_C3 | HGB_C2 | HGB_C2 |

HGB_C2, selected for A2 at all three resolutions, uses learning rate 0.06,
80 iterations, 31 leaf nodes, minimum 50 samples per leaf and L2
regularization 1.0.

## FINAL comparison

The table reports A2 on the untouched FINAL holdout. A negative delta is an
improvement over the named baseline.

| Grid | Horizon | Candidate MAE | Candidate RMSE | Candidate WAPE | Historical WAPE | WAPE delta vs historical | Seasonal WAPE | WAPE delta vs seasonal |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| G500 | 15 | 0.374862 | 0.749781 | 1.009661 | 0.951110 | +0.058551 | 1.105694 | -0.096033 |
| G500 | 30 | 0.378400 | 0.759382 | 1.019189 | 0.951110 | +0.068079 | 1.105694 | -0.086505 |
| G500 | 60 | 0.381554 | 0.774807 | 1.027685 | 0.951110 | +0.076574 | 1.105694 | -0.078009 |
| G1000 | 15 | 0.822853 | 1.363661 | 0.680421 | 0.658965 | +0.021456 | 0.822200 | -0.141779 |
| G1000 | 30 | 0.830521 | 1.379293 | 0.686762 | 0.658965 | +0.027797 | 0.822200 | -0.135439 |
| G1000 | 60 | 0.842811 | 1.422183 | 0.696924 | 0.658965 | +0.037960 | 0.822200 | -0.125276 |
| G2000 | 15 | 1.658584 | 2.602512 | 0.429051 | 0.432167 | -0.003116 | 0.584305 | -0.155254 |
| G2000 | 30 | 1.687921 | 2.672329 | 0.436640 | 0.432167 | +0.004473 | 0.584305 | -0.147665 |
| G2000 | 60 | 1.727496 | 2.778525 | 0.446878 | 0.432167 | +0.014711 | 0.584305 | -0.137427 |

MAE values must not be compared directly across grid sizes because each target
is a count per cell and larger cells contain more trips. WAPE is scale-normalized,
but its improvement at coarser grids also represents loss of spatial detail;
it is a resolution/accuracy trade-off, not a free accuracy gain.

## FINAL ablation (WAPE)

| Grid | Horizon | A0 calendar | A1 + demand history | A2 + neighbors |
| ---: | ---: | ---: | ---: | ---: |
| G500 | 15 | 1.487163 | 1.010305 | 1.009661 |
| G500 | 30 | 1.493993 | 1.019412 | 1.019189 |
| G500 | 60 | 1.494658 | 1.029038 | 1.027685 |
| G1000 | 15 | 1.092096 | 0.682139 | 0.680421 |
| G1000 | 30 | 1.085121 | 0.688124 | 0.686762 |
| G1000 | 60 | 1.088031 | 0.699676 | 0.696924 |
| G2000 | 15 | 0.843970 | 0.431469 | 0.429051 |
| G2000 | 30 | 0.841414 | 0.438728 | 0.436640 |
| G2000 | 60 | 0.840396 | 0.448275 | 0.446878 |

Demand history provides the dominant improvement over calendar-only features.
Spatial neighbors add a smaller but consistent incremental improvement. The
ablation supports retaining A2 while avoiding an exaggerated claim about the
size of the neighbor effect.

## Resource evidence

| Grid | Search time | Evaluation/serialization | Total candidate time | Peak working set | Candidate Parquet bytes |
| ---: | ---: | ---: | ---: | ---: | ---: |
| G500 | 959.69 s | 731.55 s | 1,692.29 s | Not captured | 323,735,883 |
| G1000 | 270.63 s | 156.32 s | 427.52 s | 678,674,432 B | 129,457,751 |
| G2000 | 214.75 s | 109.75 s | 324.84 s | 531,931,136 B | 49,091,618 |

The G500 peak memory value is absent because a Windows native-handle signature
was incorrect during that already-completed run. Commit `9a612b2` fixed the
collector and added a regression test before G1000/G2000. FINAL was not rerun
to fill an optional system metric.

## Verification and quality

- Python 3.11 and 3.12 each pass 86 tests; five opt-in integrations are skipped
  in the default suite. The real PostgreSQL/PostGIS suite passes 4/4 tests.
- All top-level checksums, Parquet byte sizes, SHA-256 values and metadata row
  counts were independently recomputed for the G1000/G2000 feature, baseline
  and candidate artifacts.
- Both candidate model bundles reload successfully and contain exactly the
  15/30/60-minute horizon models.
- All six G1000/G2000 processing runs are `SUCCEEDED` with zero quality FAIL.
- Feature-build WARN results are expected and disclosed: incomplete early
  history coverage and study-area exclusions. Leakage, uniqueness, grid
  assignment, continuity and population gates pass.
- One G1000 launch used the application database role and was rejected before
  a processing run started. Its incomplete directory is not evidence; the
  successful run above used the analytics database role.

## Phase decision

Phase 6 closes at the tabular HGB candidate. A neural spatio-temporal extension
is not added now: it is not required to answer the frozen research questions,
would require a new fair tuning/ablation budget, and the primary G500 result
does not justify expanding the thesis scope after FINAL inspection.

Phase 7 may operationalize the serialized A2 candidate through an explicit
`CANDIDATE` to `APPROVED` registry workflow. Approval must remain an audited
admin decision; Phase 6 evidence must not auto-promote the model.
