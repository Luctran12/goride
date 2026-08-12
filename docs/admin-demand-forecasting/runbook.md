# Demand-Forecasting Reproduction and Operations Runbook

## Prerequisites

- Python 3.11 or 3.12 virtual environment from the locked requirements.
- PostgreSQL/PostGIS schema releases applied and verified.
- External analytics root containing the immutable dataset manifest/source.
- Database variables `ANALYTICS_DATABASE_HOST`, `PORT`, `NAME`, `USERNAME`,
  `PASSWORD` for full reproduction. Never commit these values.
- Docker Desktop for Spring/PostGIS integration and benchmark tests.

## Fast frozen-evidence verification

From the backend repository root:

```powershell
.\scripts\run-demand-forecasting-thesis.ps1 `
  -DataRoot D:\path\to\goride-analytics-data `
  -Mode VerifyFrozen `
  -OutputDirectory target/phase11/thesis-verification
```

The command fails closed on missing files, unsafe paths, checksum/size drift,
lineage mismatch, wrong research scope, missing grids/horizons, invalid backfill,
missing rollback evidence or privacy threshold below three. Output directories
are immutable and never overwritten.

Expected frozen evidence:

- 296 verified files;
- 2,074,459,267 verified bytes;
- report SHA-256 `e64b5a8f7455b3bbfaa892b4401ac9c8059d5e4d5ecd6757514f7bf9cd7ae433`.

## Full reproduction

The same script and one frozen JSON config orchestrate all stages:

```powershell
.\scripts\run-demand-forecasting-thesis.ps1 `
  -DataRoot D:\path\to\clean-analytics-data `
  -Mode FullReproduction `
  -ReproductionConfig analytics-processing/configs/porto-phase11-reproduction.json
```

It runs extraction → features → baseline evaluation → candidate training →
register → explicit approval → forecast → actual backfill. Allow at least the
documented G500 candidate time (about 28 minutes) plus extraction/feature/evaluation
time and sufficient space for the raw dataset, 14M feature rows and predictions.
Do not rerun FINAL merely to improve a reported result; a new protocol/version is required.

## Database release verification

Use `scripts/validate-db-release.ps1` for precheck/apply/verify/rollback/re-apply.
The application database role must not be used as the analytics writer. A failed
release or precheck must be resolved before any batch stage starts.

## Serving benchmark and regression

```powershell
mvn -Dtest=AdminDemandForecastServingIntegrationTests test
```

The test writes raw benchmark JSON/checksum to `target/phase11`. Record machine,
sample count and synthetic scope with results. Do not present local P95 as a
production SLA.

```powershell
cd analytics-processing
.\.venv-3.11\Scripts\python.exe -m unittest discover -s tests
.\.venv-3.12\Scripts\python.exe -m unittest discover -s tests
```

Frontend final checks are `npm run lint`, `npm test -- --run`, `npm run build`.

## Failure response

| Symptom | Action |
| --- | --- |
| Dataset checksum mismatch | Stop; restore the declared immutable source or create a new dataset version |
| Quality FAIL | Inspect rule evidence; never bypass or promote that run |
| Model checksum/corruption | Quarantine artifact; restore verified bytes; do not deserialize |
| DB unavailable | Keep artifact evidence, mark processing failed, retry only after DB health is restored |
| Failed/partial forecast | Confirm zero visible rows; retain failure audit; retry with the same idempotency identity |
| Stale published forecast | UI/API must remain STALE; rerun scheduler with a current cutoff |
| Low-count actual exposed | Treat as privacy incident; verify threshold ≥3, revoke cached response and audit logs |

## Deployment boundary

- Deploy Spring and React artifacts; do not deploy raw Porto data/model directories in web roots.
- Mount model artifacts read-only for the batch worker only.
- Use separate least-privilege application and analytics-writer database roles.
- Set `ANALYTICS_FORECAST_MINIMUM_ACTUAL_DEMAND_COUNT` to 3 or greater.
- Configure backups before enabling forecast retention cleanup.
- Porto remains `EVALUATION`/`RESEARCH_DEMONSTRATION`; production TP.HCM requires a new local dataset, model version and approval scope.
