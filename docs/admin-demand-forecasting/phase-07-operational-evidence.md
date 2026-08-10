# Phase 7 model-registry and inference evidence

- Date: 2026-08-10
- Profile: `porto-thesis`
- Operations contract: `porto-operations-v1`
- Approval scope: `RESEARCH_DEMONSTRATION`

## Outcome

Phase 7 is complete for the research-demonstration boundary. The selected G500
A2 model can now be checksum-verified, registered, explicitly approved, loaded
for scheduled inference, published atomically to the forecast store and
evaluated through watermark-safe actual/error backfill.

The Porto model remains research-only. It is not approved as a production
model for Ho Chi Minh City.

## Implementation

| Commit | Purpose |
| --- | --- |
| `9177be9` | Model registry, audited lifecycle, operations contract, inference, atomic publication and actual/error backfill |
| `cc2c935` | Canonical UTC serialization for idempotent forecast responses |

The database lifecycle retains the verified Phase 2 vocabulary:

- research `CANDIDATE` maps to database `VALIDATED`;
- research validation failure maps to database `REJECTED`;
- approved and retired states map directly to `APPROVED` and `RETIRED`.

Registration and every lifecycle transition append actor, reason, UTC time and
approval scope to `training_manifest.lifecycleEvents`. Metric results never
auto-promote a model.

## Frozen operations policy

| Policy | Value |
| --- | ---: |
| Approval scope | `RESEARCH_DEMONSTRATION` |
| Real-time stale threshold | 30 minutes |
| Actual watermark delay | 15 minutes after target bucket start |
| Forecast retention policy | 90 days |
| Maximum forecast rows/run | 10,000 |
| Purposes | `EVALUATION`, `PUBLISHED` |

Historical `EVALUATION` runs report freshness as not applicable. A real-time
`PUBLISHED` run fails before inference when its cutoff is older than 30 minutes.
Automated destructive pruning is intentionally disabled until deployment owns
a backup and retention schedule; the 90-day policy is persisted in evidence.

## Registered model

| Field | Value |
| --- | --- |
| Model version ID | `057f564e-0412-5958-9291-6af0932326d1` |
| Model version | `porto-hgb-v1-g500-63f545298c-f4a46557d3` |
| Lifecycle | `APPROVED` |
| Approval scope | `RESEARCH_DEMONSTRATION` |
| Artifact SHA-256 | `8643527f721db9e86c8721bd2262b36a5587ac0b13ccd68ad4feb563114db5b4` |
| Lifecycle events | 2 (`VALIDATED`, `APPROVED`) |

Registration verified every top-level training checksum, the model checksum in
both manifests, quality `PASS`, model-card candidate status, bundle metadata,
feature names and the exact 15/30/60-minute horizon model set before writing
the registry row.

## Official inference evidence

- Artifact run:
  `20260810T102601474464Z-9177be915f0a-porto-thesis-4cf869ca75a0`.
- Processing run: `9246ef7e-59ff-5729-8a62-a28bbe7ccfc7`.
- Forecast run: `791b9584-c448-516e-94b4-0ab50aa59e96`.
- Inference cutoff: `2014-06-01T00:00:00Z`.
- Purpose/status: `EVALUATION` / `SUCCEEDED`.
- Population: 134 cells and horizons 15/30/60, exactly 402 rows.
- Inference plus atomic publication: 0.3464 seconds.
- Five quality rules pass; no negative prediction, duplicate, horizon drift,
  target alignment breach or non-finite value.
- All geometries are valid WGS84 polygons with SRID 4326.

An identical CLI rerun returned the same forecast and processing run IDs with
`idempotent = true`; row count remained 402 and no second processing run was
created.

### One-cutoff operational metrics

These values describe one scheduled-inference cutoff after actual backfill.
They do not replace the Phase 6 FINAL holdout results.

| Horizon | Rows | MAE | RMSE | WAPE |
| ---: | ---: | ---: | ---: | ---: |
| 15 | 134 | 0.622092 | 1.345348 | 0.737703 |
| 30 | 134 | 0.506418 | 0.984888 | 0.848250 |
| 60 | 134 | 0.505258 | 1.294334 | 0.778213 |

## Actual/error backfill

First backfill:

- Artifact run:
  `20260810T102748599113Z-cc2c935f50cc-porto-thesis-4cf869ca75a0`.
- Processing run: `3dc9620f-a94a-52cf-a492-05ad2cb1ca3e`.
- Watermark: `2014-06-01T01:15:00Z`, which closes H60 target 01:00 plus the
  frozen 15-minute delay.
- Eligible/source-matched/updated rows: 402/402/402.
- Every row has actual demand and exact
  `absolute_error = abs(predicted_demand - actual_demand)`.

Idempotent rerun:

- Artifact run:
  `20260810T102805758168Z-cc2c935f50cc-porto-thesis-4cf869ca75a0`.
- Processing run: `05390c89-c96a-5c6f-81d1-a9b910c0c07d`.
- Already evaluated/eligible/updated rows: 402/0/0.

## Failure recovery evidence

A terminal-transition fault was injected after forecast inserts for cutoff
`2014-06-02T00:00:00Z`:

- Failure artifact:
  `20260810T103126533716Z-cc2c935f50cc-porto-thesis-4cf869ca75a0`.
- Failed processing run: `08207750-1948-5e18-8cb9-37c9e93ae2d8`.
- Error code: `PHASE7_INJECTED_TERMINAL_FAILURE`.
- Forecast rows visible after rollback: zero.

Controlled retry removed only the empty failed forecast header, retained the
failed processing/artifact evidence and reused the deterministic forecast UUID:

- Retry artifact:
  `20260810T103208296319Z-cc2c935f50cc-porto-thesis-4cf869ca75a0`.
- Retry processing run: `30c196d1-10f6-5bae-862f-e46722aacf88`, attempt 2.
- Forecast run: `ade381eb-f5e0-5d3f-a11c-c98c2fb804b1`.
- Result: `SUCCEEDED`, 402 rows and all quality gates pass.

## Verification

- Python 3.11 and 3.12 each pass 96 tests; five opt-in tests skip in the
  default suite.
- The existing real PostgreSQL/PostGIS integration suite passes 4/4.
- A rollback-only full lifecycle rehearsal produced 402 features, forecasts
  and backfilled errors, then independently confirmed zero persistent rows.
- Independent database QA verified model lifecycle/scope, processing status,
  quality results, cell/horizon population, nonnegative predictions, geometry,
  actual completeness and exact error equality.
- SHA-256 was independently recomputed for the official forecast, both
  backfills, fault-injection and retry artifacts.

## Boundary for Phase 8

Phase 7 writes the persistence contracts required by the serving layer. Phase
8 may expose read-only model, forecast, hotspot, evaluation, freshness and run
status APIs. It must label this Porto evidence as research evaluation and must
not present the model as a live Ho Chi Minh City forecast.
