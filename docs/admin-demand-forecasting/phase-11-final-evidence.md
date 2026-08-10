# Phase 11 Final Evidence

Date: 2026-08-10 (Asia/Ho_Chi_Minh)

This document is the final traceability matrix for the Admin Demand Forecasting
research artifact. It distinguishes empirical evidence, integration evidence
and remaining deployment work so that a successful smoke test cannot be
mistaken for a forecasting-accuracy claim.

## Definition of Done

| Requirement | Evidence | Result |
| --- | --- | --- |
| Frozen inputs and one reproduction contract | `porto-phase11-reproduction.json`, `porto-phase11-evidence.yml`, `run-demand-forecasting-thesis.ps1` | PASS |
| Dataset identity verified before processing | Porto manifest and source SHA-256 `210dd0...c5ea` | PASS |
| Leakage-safe chronological evaluation | Four expanding development folds and one untouched FINAL holdout; no shuffle | PASS |
| Candidate and baselines use identical observations | Candidate verifier rejects fold, row, horizon or target mismatches | PASS |
| Required model comparisons | Historical mean, seasonal naive and HGB A0/A1/A2 | PASS |
| Spatial and horizon sensitivity | G500/G1000/G2000 at 15/30/60 minutes | PASS |
| Negative results retained | HGB does not beat historical mean on primary G500 FINAL MAE/WAPE | PASS |
| Immutable evidence is independently verifiable | 296 files, 2,074,459,267 bytes and nested SHA-256 coverage verified | PASS |
| Registry and approval are auditable | Model lifecycle is `APPROVED` only for `RESEARCH_DEMONSTRATION` | PASS |
| Forecast publication is atomic and idempotent | 402-row official run, deterministic rerun and injected rollback evidence | PASS |
| Actual/error backfill is closed-time only | Watermark-gated 402/402 backfill and idempotent second attempt | PASS |
| Serving boundary is Admin-only and aggregate-only | RBAC tests, query guards, DTO privacy reflection test and count suppression | PASS |
| UI consumes backend-owned results | Whitelist mappers, no Python/browser model runtime and no KPI recomputation | PASS |
| Failure states are visible | Invalid input, quality FAIL, corrupt artifact, stale data, unavailable DB, rate-limit and rollback paths covered | PASS |
| Thesis artifacts are linked to evidence | Model card, data sheet, evaluation report, architecture and runbook | PASS |

## Reproduction and integrity result

The frozen verification command was executed successfully:

```powershell
.\scripts\run-demand-forecasting-thesis.ps1 `
  -Mode VerifyFrozen `
  -DataRoot D:\hoc\Project\LVTN\goride-analytics-data
```

- Verification artifact:
  `20260810T153807249207Z-phase11-verification`.
- Files verified: `296`.
- Bytes verified: `2,074,459,267`.
- Frozen contract SHA-256:
  `1c41721e33c2b78148c1f9d84cbf39b29b0620a3f1ea5dcb4f9c86dc222b4e94`.
- Verification report SHA-256:
  `e64b5a8f7455b3bbfaa892b4401ac9c8059d5e4d5ecd6757514f7bf9cd7ae433`.

`FullReproduction` is the end-to-end orchestration mode for a clean analytics
database and external data root. It runs extraction, three feature grids,
baseline evaluation, candidate training, registration, explicit approval,
forecast publication and actual backfill. The official frozen FINAL result is
not reopened merely to improve a metric; the verifier is the normal review
path for the existing result.

## Failure and recovery matrix

| Failure | Expected behavior | Evidence |
| --- | --- | --- |
| Dataset/checksum tampering | Stop before processing and return a manifest error | Config and thesis-verifier tests |
| Feature quality FAIL | Record terminal failure; publish no feature rows | Extraction/feature quality fixtures |
| Corrupt model bundle | Refuse registration/inference before prediction | Phase 7 checksum-gate tests |
| Database unavailable | Stable unavailable response; no fabricated success | Repository/service/controller tests |
| Forecast terminal failure | Roll back inserted rows; preserve failed processing evidence | Injected Phase 7 rollback run |
| Duplicate retry | Return the deterministic existing run without duplicate rows | Official forecast rerun |
| Target bucket still open | Refuse actual/error backfill | Watermark tests |
| Stale published forecast | Mark stale or reject according to serving purpose | Serving freshness tests |
| Low-count positive actual | Hide actual, error and evaluation time; retain forecast | `ACTUAL_SUPPRESSED` tests |
| Non-Admin caller | Return `403` before serving analytics | Security/controller tests |

## Privacy and security boundary

- Serving DTO records are checked against direct identifiers (`tripId`,
  `userId`, `driverId`, `passengerId`, email and phone) and artifact locations.
- Positive actual counts below `3` are suppressed. The property rejects a
  configured threshold below `3`; `0` remains a legitimate aggregate.
- Model files, raw trajectories and the external data-root path never cross the
  HTTP boundary.
- Forecast endpoints are read-only, Admin-authorized, rate-limited, range- and
  payload-bounded, and use a 10-second database statement timeout.

## Performance and storage evidence

The serving benchmark uses synthetic PostGIS fixtures to measure plumbing, not
forecast accuracy:

| Endpoint | Calls | P50 | P95 | Maximum | Payload |
| --- | ---: | ---: | ---: | ---: | ---: |
| Demand GeoJSON | 100 | 4.294 ms | 8.241 ms | 13.733 ms | 1,894 B |
| Hotspots | 100 | 4.505 ms | 9.524 ms | 19.122 ms | 1,718 B |

The benchmark table contained two synthetic rows and occupied 172,032 bytes.
The tracked report SHA-256 is
`a3d11d71671647a52eb3517cde0a1b75d2f97277f97db0f0dd19bd66fa8ff289`.
The full G500 feature store contains 14,084,740 rows and occupies about 15.96
GB including indexes; G1000/G2000 sensitivity artifacts are Parquet-only.

## Regression result

- Python 3.11: 98 tests pass, 5 opt-in integrations skipped.
- Python 3.12: 98 tests pass, 5 opt-in integrations skipped.
- Spring backend: 135 suites / 584 tests pass, no failure, error or skip.
- Admin Web: 50 files / 238 tests pass; lint and production build pass (2,610
  modules).

## Claim boundary and unresolved work

- Porto validates the research method on historical taxi demand. It does not
  establish production accuracy for Ho Chi Minh City.
- The synthetic serving benchmark validates the API/database path only.
- Vehicle type and service-area filters remain unavailable until those
  dimensions are added to forecast storage and the backend contract.
- Authenticated deployment UAT, production traffic load testing, monitoring,
  backup and retention scheduling are deployment tasks, not completed research
  evidence.
- Prediction intervals remain unavailable because no calibrated interval method
  was frozen before FINAL evaluation.

## Artifact index

- [Architecture](architecture.md)
- [Data sheet](data-sheet.md)
- [Evaluation report](evaluation-report.md)
- [Model card](model-card.md)
- [Operations runbook](runbook.md)
- [Phase 6 candidate evidence](phase-06-candidate-evidence.md)
- [Phase 7 operational evidence](phase-07-operational-evidence.md)
- [Phase 8 serving evidence](phase-08-serving-api-evidence.md)
- [Phase 9 frontend evidence](phase-09-frontend-evidence.md)
- [Phase 10 forecast UI evidence](phase-10-forecast-ui-evidence.md)
