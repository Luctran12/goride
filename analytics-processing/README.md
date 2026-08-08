# GoRide Analytics Processing

Deterministic Python command-line foundation for the Admin demand-forecasting
processing layer. Phase 1 implements configuration, dataset-manifest validation,
hashing, run identity and failure semantics. It intentionally does not extract
the full Porto dataset, build features, train or publish forecasts yet.

## Runtime

- Python 3.11 or 3.12
- PyYAML 6.0.3 and tzdata 2026.3
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

## Stage commands

```text
extract
build-features
train
evaluate
forecast
```

Phase 1 exposes these commands as explicit placeholders. They validate their
profile and then return exit code `4`/`STAGE_NOT_IMPLEMENTED`. They never write
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
