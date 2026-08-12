# Phase 11 Evaluation Report

## Evaluation question

The evaluation asks whether a reproducible spatial-temporal processing layer can
produce auditable demand forecasts, whether the candidate improves appropriate
baselines, and whether the artifact can be served safely through Admin Analytics.

## Protocol

- Frozen chronological rolling-origin folds D1–D4 for configuration selection.
- Untouched FINAL interval for one-time reporting.
- Historical mean, seasonal naive and HGB candidate evaluated on identical rows.
- MAE, RMSE and WAPE reported by horizon, cell size, fold and feature ablation.
- Raw predictions, metrics, manifests and SHA-256 retained for every reported claim.

The protocol does not encode “candidate must win” as a test. Negative results remain valid evidence.

## FINAL candidate comparison

| Grid | Horizon | Candidate MAE | RMSE | WAPE | Historical WAPE | Seasonal WAPE |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 500 | 15 | 0.374862 | 0.749781 | 1.009661 | 0.951110 | 1.105694 |
| 500 | 30 | 0.378400 | 0.759382 | 1.019189 | 0.951110 | 1.105694 |
| 500 | 60 | 0.381554 | 0.774807 | 1.027685 | 0.951110 | 1.105694 |
| 1000 | 15 | 0.822853 | 1.363661 | 0.680421 | 0.658965 | 0.822200 |
| 1000 | 30 | 0.830521 | 1.379293 | 0.686762 | 0.658965 | 0.822200 |
| 1000 | 60 | 0.842811 | 1.422183 | 0.696924 | 0.658965 | 0.822200 |
| 2000 | 15 | 1.658584 | 2.602512 | 0.429051 | 0.432167 | 0.584305 |
| 2000 | 30 | 1.687921 | 2.672329 | 0.436640 | 0.432167 | 0.584305 |
| 2000 | 60 | 1.727496 | 2.778525 | 0.446878 | 0.432167 | 0.584305 |

The candidate beats seasonal naive on all three metrics in all nine tests. It
beats historical mean on all three only at G2000-H15, and does not improve G500
MAE/WAPE. Coarser-grid WAPE improvements trade away location resolution.

## Ablation conclusion

A1 demand-history features provide the largest improvement over calendar-only A0.
A2 spatial neighbors provide a smaller but consistent additional WAPE improvement
at every grid/horizon. The evidence supports retaining A2, but not claiming a
large spatial-neighbor effect.

## Resource and operational measurements

| Grid | Candidate total time | Peak working set | Candidate prediction bytes |
| ---: | ---: | ---: | ---: |
| 500 | 1,692.29 s | Not captured | 323,735,883 |
| 1000 | 427.52 s | 678,674,432 B | 129,457,751 |
| 2000 | 324.84 s | 531,931,136 B | 49,091,618 |

Official historical inference generates 402 rows in 0.3464 seconds. Watermark-safe
backfill updates 402/402. Phase 11 checksum verification covers 296 files and
2,074,459,267 bytes.

## Serving benchmark

Spring/PostGIS Testcontainers used two synthetic forecast rows, ten warmups and
100 measured calls per endpoint:

| Endpoint | P50 | P95 | Max | Payload |
| --- | ---: | ---: | ---: | ---: |
| Forecast demand | 4.294 ms | 8.241 ms | 13.733 ms | 1,894 B |
| Forecast hotspots | 4.505 ms | 9.524 ms | 19.122 ms | 1,718 B |

This measures query/serialization plumbing on one local environment. It is not
a production capacity claim. Benchmark checksum:
`a3d11d71671647a52eb3517cde0a1b75d2f97277f97db0f0dd19bd66fa8ff289`.

## Failure and safety evaluation

| Scenario | Expected behavior | Evidence |
| --- | --- | --- |
| DB unavailable | Stable `ANALYTICS_DATA_UNAVAILABLE` | Spring unit test |
| Quality FAIL | Stage fails and does not publish | Python quality/pipeline tests |
| Corrupt model/artifact | SHA mismatch before load/inference | Artifact and Phase 11 tamper tests |
| Stale published input | Forecast rejected or API labelled STALE | Python operations and Spring service tests |
| Failure after inserts | Transaction rollback leaves zero visible forecasts | Phase 7 injected failure artifact |
| Low-count actual | Actual/error/evaluated time suppressed | Spring integration and DTO privacy tests |

## Conclusion

The artifact demonstrates reproducibility, safe publication, traceability and
Admin exploration. Its academic contribution is stronger as an evaluated data
processing and serving system than as a claim of universal model superiority.
