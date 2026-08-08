# GoRide Analytics Processing

Deterministic Python command-line foundation for the Admin demand-forecasting
processing layer. Phase 3 adds bounded Porto/GoRide extraction, a canonical
demand-event snapshot, deterministic checksums, data-quality gates and lifecycle
persistence. Feature building, training and forecast publication remain gated.

## Runtime

- Python 3.11 or 3.12
- PyYAML 6.0.3, tzdata 2026.3 and Psycopg 3.3.4
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

## Stage commands

```text
extract
build-features
train
evaluate
forecast
```

`extract` is implemented in Phase 3. The remaining commands are explicit
placeholders: they validate their profile and then return exit code
`4`/`STAGE_NOT_IMPLEMENTED` without fabricated success artifacts.

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

## Model compatibility spike

The candidate family is represented by scikit-learn's
`HistGradientBoostingRegressor`. Verify the optional local stack without adding
it to runtime commands:

```powershell
python scripts\check_model_runtime.py
```

This smoke check only verifies import, deterministic fit/predict and dependency
versions. It is not model evaluation evidence.
