# GoRide Analytics Processing

Deterministic Python command-line foundation for the Admin demand-forecasting
processing layer. Phase 5 adds bounded Porto/GoRide extraction, deterministic
canonical snapshots, spatial-temporal aggregation, leakage-safe features,
historical/seasonal baselines, rolling-origin evaluation, Parquet evidence and
PostgreSQL lifecycle persistence. Candidate training and forecast publication
remain gated.

## Runtime

- Python 3.11 or 3.12
- PyYAML 6.0.3, tzdata 2026.3, Psycopg 3.3.4, PyProj 3.7.2 and
  PyArrow 25.0.0
- External data root selected by `GORIDE_ANALYTICS_DATA_ROOT`

Create an isolated environment from this directory:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements\runtime.lock
.\.venv\Scripts\python.exe -m pip install -e . --no-deps
```

Do not place raw data or model binaries under this repository.

## Validate the Porto profile

```powershell
$env:GORIDE_ANALYTICS_DATA_ROOT = "D:\hoc\Project\LVTN\goride-analytics-data"

$env:PYTHONPATH = "$PWD\src"
python -m goride_analytics validate-config `
  --config configs\porto-thesis.yml
```

`validate-config` checks:

- strict YAML keys and types;
- timezone, CRS, bucket, horizon and chronological split semantics;
- external-root path containment;
- dataset manifest identity and required fields;
- source artifact existence and SHA-256.

It exits before processing data when validation fails.

## Extract a bounded snapshot

Set the Phase 2 analytics database variables in addition to the data root:

```powershell
$env:ANALYTICS_DATABASE_HOST = "localhost"
$env:ANALYTICS_DATABASE_PORT = "5432"
$env:ANALYTICS_DATABASE_NAME = "goride_analytics_porto"
$env:ANALYTICS_DATABASE_USERNAME = "postgres"
$env:ANALYTICS_DATABASE_PASSWORD = "<local-secret>"

python -m goride_analytics extract `
  --config configs\porto-thesis.yml `
  --from-utc 2013-07-01T00:00:00Z `
  --cutoff-utc 2013-08-01T00:00:00Z
```

The interval is exactly `[from-utc, cutoff-utc)`. Both boundaries must align
to the configured bucket. Raw datasets and generated run evidence remain below
the external analytics data root and are never committed.

## Build leakage-safe features

Use the successful extraction run directory printed by `extract`. A relative
path is resolved below the configured analytics data root:

```powershell
python -m goride_analytics build-features `
  --config configs\porto-thesis.yml `
  --extraction-run runs\extraction\<artifact-run-id> `
  --cell-size-meters 500
```

For cell-size sensitivity experiments that do not need database serving rows,
add `--artifact-only`. The run still writes verified Parquet, quality evidence
and a PostgreSQL processing audit, but records `publicationMode = PARQUET_ONLY`
and does not insert into `analytics.demand_features`.

The command verifies every extraction checksum before reading data, projects
WGS84 pickups into the profile CRS, applies the frozen zero-origin floor grid,
selects cells using training data only, materializes continuous UTC buckets and
emits:

- `demand-features/target_month=YYYY-MM/part-00000.parquet` in stable
  cell/cutoff/horizon order;
- `cell-eligibility.json` with the frozen selected cell IDs and coverage;
- `feature-dictionary.json` and `feature-manifest.json`;
- `feature-quality.json`, run evidence and `checksums.sha256`;
- idempotent rows in `analytics.demand_features`.

Database publication uses PostgreSQL `COPY` into a transaction-local staging
table followed by one conflict-aware merge per partition. All partition merges
and the terminal `SUCCEEDED` transition remain in one outer transaction.

Demand lag, rolling and neighbor features use only buckets closed at the row's
inference cutoff. GoRide supply remains nullable and WARNs when coverage is
missing; a sample recorded after its bucket closes fails the build.

The Porto profile selects all boundary-tied cells needed to cover at least 95%
of demand in the train split. Validation/test demand never influences that
population. Planning fails before Parquet/feature-row writes above 1,500,000
rows per target-month partition or 15,000,000 rows for the run.

If feature generation finishes but the database client is interrupted before
commit, resume directly from the immutable artifact instead of rebuilding it:

```powershell
python -m goride_analytics persist-features `
  --config configs\porto-thesis.yml `
  --feature-run <feature-build-artifact-run-id>
```

The resume command verifies the source run/config, top-level checksums, every
partition's SHA-256/byte count/Parquet row count, quality gates and cost guards
before opening the database connection. It writes a separate persistence run
that references the original feature artifact; it never edits that artifact.

## Evaluate immutable baselines

Use the successful Phase 4 feature-build run, not a persistence-run ID:

```powershell
python -m goride_analytics evaluate `
  --config configs\porto-thesis.yml `
  --feature-run <feature-build-artifact-run-id>
```

The command evaluates historical mean and DST-aware seasonal naive on four
expanding monthly development folds plus one untouched final holdout. It never
shuffles observations. Demand-volume slice thresholds are fitted from each
fold's training history only.

Evidence is written under `runs/evaluation/<artifact-run-id>`: fold contract,
two model cards, raw predictions partitioned by fold/model, MAE/RMSE/WAPE
summaries, quality gates, manifests and SHA-256 checksums. Raw predictions are
required in Phase 5 so every reported aggregate can be traced to a fold and
observation.

## Train and evaluate the candidate

Phase 6 freezes model family, search budget, sampling policy, feature ablations
and selection rule in `configs/porto-phase6-hgb.yml` before the final holdout is
opened:

```powershell
python -m goride_analytics train `
  --config configs\porto-thesis.yml `
  --experiment-config configs\porto-phase6-hgb.yml `
  --feature-run <feature-build-artifact-run-id> `
  --baseline-run <evaluation-artifact-run-id>
```

The command verifies the immutable feature and baseline artifacts, tunes every
HGB configuration for A0/A1/A2 on D1-D4 only, then locks one configuration per
feature group. Training uses a bounded deterministic hash sample with inverse
stratum weights; validation and FINAL metrics always use the complete frozen
population. Candidate and baseline rows must match for every fold/horizon.

The training artifact contains development search evidence, selected model
card, serialized A2 horizon models, full candidate predictions, baseline
comparisons, resource measurements, quality gates and SHA-256 checksums.
Prediction intervals remain disabled because Phase 6 has no frozen calibrated
interval method.

## Register and operationalize the research model

Phase 7 verifies the complete training evidence before creating a registry
entry. The database lifecycle name `VALIDATED` represents the research
`CANDIDATE`; it is never auto-promoted from a metric:

```powershell
python -m goride_analytics register-model `
  --config configs\porto-thesis.yml `
  --training-run <training-artifact-run-id> `
  --actor <admin-identity> `
  --reason "validated Phase 6 research evidence"

python -m goride_analytics approve-model `
  --config configs\porto-thesis.yml `
  --model-version <model-version> `
  --actor <admin-identity> `
  --reason "research demonstration only"
```

The approval scope is fixed to `RESEARCH_DEMONSTRATION`. A Porto model is not
approved for production use in Ho Chi Minh City.

Run checksum-gated inference from persisted feature rows at an aligned cutoff:

```powershell
python -m goride_analytics forecast `
  --config configs\porto-thesis.yml `
  --operations-config configs\porto-phase7-operations.yml `
  --model-version <model-version> `
  --inference-cutoff 2014-06-01T00:00:00Z `
  --purpose EVALUATION
```

Equivalent successful runs are returned idempotently. Failed attempts retain
their processing evidence and may retry only after the empty failed forecast
header is removed by the controlled retry path. Forecast rows, terminal run
state and processing success are published in one transaction.

After the target bucket and configured watermark delay have closed, backfill
actual demand and exact absolute error:

```powershell
python -m goride_analytics backfill-actual `
  --config configs\porto-thesis.yml `
  --operations-config configs\porto-phase7-operations.yml `
  --forecast-run <forecast-run-uuid> `
  --watermark-utc 2014-06-01T01:15:00Z
```

Real-time `PUBLISHED` runs additionally enforce the 30-minute freshness
threshold. The frozen operations contract records a 90-day forecast retention
policy; automated destructive pruning remains disabled until deployment owns a
backup and retention schedule.

## Stage commands

```text
extract
build-features
persist-features
train
evaluate
forecast
```

`extract`, `build-features`, `persist-features`, `evaluate` and `train` are
implemented through Phase 6. `forecast` remains an explicit placeholder: it
validates its profile and returns exit code `4`/`STAGE_NOT_IMPLEMENTED` without
fabricated success artifacts.

## Run tests

From `analytics-processing`:

```powershell
$env:PYTHONPATH = "$PWD\src"
python -m unittest discover -s tests -v
```

## Exit codes

| Code | Meaning |
| ---: | --- |
| 0 | Success |
| 2 | Invalid configuration |
| 3 | Dataset/manifest validation failure |
| 4 | Stage intentionally not implemented in the current phase |
| 5 | Unsafe or conflicting run output |
| 6 | Data-quality FAIL gate |
| 7 | Database configuration, compatibility or persistence failure |
| 8 | Source extraction failure |
| 70 | Unexpected internal error |

Errors and lifecycle events are JSON lines on stderr. Password, secret, token
and credential fields are redacted by key.

## Candidate model runtime

The candidate family is represented by scikit-learn's
`HistGradientBoostingRegressor`. Verify the optional local stack without adding
it to runtime commands:

```powershell
python scripts\check_model_runtime.py
```

This smoke check verifies import, deterministic fit/predict and dependency
versions. Reported model evidence is produced only by the `train` command.
