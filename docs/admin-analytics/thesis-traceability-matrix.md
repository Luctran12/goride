# Admin Analytics Thesis Traceability Matrix

> Traceability version: 1.0
>
> Contract version: 1.0
>
> Benchmark protocol version: 1.0

## 1. Objective-to-Artifact Trace

| Objective | Implemented artifact | Verification |
| --- | --- | --- |
| Define unambiguous operational KPIs | [Metric dictionary](metric-dictionary.md) and matching [ADR](adr/ADR-001-matching-telemetry-consistency.md) | Hand-calculated direct-query fixture and API contract tests |
| Persist matching and supply evidence | `matching_runs`, `matching_offer_events`, `driver_supply_snapshots` | Telemetry repository/constraint/recovery integration tests |
| Analyze demand in time and space | Demand timeseries and PostGIS square-grid heatmap | Timezone/boundary fixture tests and `EXPLAIN ANALYZE` index evidence |
| Analyze matching effectiveness | Matching performance and five-step funnel | Direct fixture, materialized equivalence and benchmark Q06/Q07 |
| Report completed-payment revenue | Overview completed-payment metrics | Fixture with pending, failed and completed payments |
| Compare direct and materialized reads | Versioned generator, correctness gate and benchmark runner | Raw samples, result hashes, query plans, refresh and storage artifacts |
| Provide a safe Admin backend contract | Six Admin-only APIs with validation and observability | RBAC, OpenAPI, Postman, rate-limit, production guardrail and release-chain tests |

## 2. Metric-to-Code-and-Evidence Trace

| Metric group | Durable source | API | Correctness test | Benchmark query/artifact |
| --- | --- | --- | --- | --- |
| Trip requests, completed/cancelled/no-driver trips and completion rate | `trips`, `trip_status_history` | `/overview`, `/demand/timeseries`, `/demand/heatmap` | `directQueriesMatchHandCalculatedFixture` | Q01-Q04 and Q08-Q10 |
| Completed payments and revenue | `payments` where status is `COMPLETED` | `/overview` | `directQueriesMatchHandCalculatedFixture` | Q01-Q02 |
| Matching runs, terminal outcomes, duration P50/P95 and averages | `matching_runs` | `/overview`, `/matching/performance` | telemetry state tests plus direct fixture | Q01-Q02 and Q06 |
| Offer rates and candidate distance | `matching_offer_events` | `/matching/performance` | direct fixture and offer constraint tests | Q06 |
| Matching funnel | runs, offers and completed trips | `/matching/funnel` | direct fixture and materialized equivalence | Q07 |
| Driver supply averages and coverage | `driver_supply_snapshots` | `/supply/timeseries` | supply snapshot/upsert and partial-coverage tests | Q05 |
| Spatial demand and spatial completion | `trips.pickup_location`, PostGIS indexes | `/demand/heatmap` | stable boundary/GeoJSON and index-plan tests | Q08-Q10 |
| Source and freshness provenance | query router and refresh state | all six endpoints | materialized equivalence/fallback and observation tests | every query case |

The normative formula, cohort timestamp, denominator, unit, precision and null
semantics for every row above are in the
[metric dictionary](metric-dictionary.md). The API path and payload shape are
in the [frozen API contract](api-contract.md).

## 3. Benchmark Evidence Identity

The committed development evidence is
[`smoke-seed-5537`](benchmark-example/smoke-seed-5537/README.md).

| Identity field | Value/source |
| --- | --- |
| Git commit | `aaea493488926f36a99186d62185a8549722d189` in [`environment.json`](benchmark-example/smoke-seed-5537/environment.json) |
| Branch | `codex/admin-v2` |
| Generator version | `1.0` |
| Generator SQL checksum | `cee3537ef89676000a00ff478cf5967ccb68bf135773d8a14ad566577bfab940` |
| Profile and seed | `smoke`, `5537` in [`dataset-manifest.json`](benchmark-example/smoke-seed-5537/dataset-manifest.json) |
| Distribution fingerprint | `68d4bf0c35479d67fabdc1474663b7a3f6ac33bdf57dfe24b59b9dd1a8821b2f` |
| Query cutoff | `2026-05-02T17:00:00Z` |
| Warm-up/measurement | 10/50 iterations per query and variant |
| Correctness precision | Numeric normalization scale 9 |
| Query definitions | [`query-cases.json`](benchmark-example/smoke-seed-5537/query-cases.json) |
| Result hashes | [`correctness.json`](benchmark-example/smoke-seed-5537/correctness.json) |
| Raw measurements | [`samples/`](benchmark-example/smoke-seed-5537/samples/) |
| Derived statistics | [`summary.json`](benchmark-example/smoke-seed-5537/summary.json) |
| SQL and plans | [`sql/`](benchmark-example/smoke-seed-5537/sql/) and [`explain/`](benchmark-example/smoke-seed-5537/explain/) |
| Refresh/storage | [`refresh/`](benchmark-example/smoke-seed-5537/refresh/) and [`storage.json`](benchmark-example/smoke-seed-5537/storage.json) |
| Integrity | [`checksums.sha256`](benchmark-example/smoke-seed-5537/checksums.sha256) |

Each query ID has exactly two raw CSV files, two representative SQL files and
two plan files: `DIRECT` and `MATERIALIZED`. The correctness gate must pass
before the samples are used.

### 3.1 Frozen Query Case Trace

| Query ID | Metric/API focus | Raw artifact prefix |
| --- | --- | --- |
| `Q01_OVERVIEW_7D` | Seven-day overview | `Q01_OVERVIEW_7D_*` |
| `Q02_OVERVIEW_30D` | Thirty-day overview | `Q02_OVERVIEW_30D_*` |
| `Q03_DEMAND_HOURLY_7D` | Hourly demand | `Q03_DEMAND_HOURLY_7D_*` |
| `Q04_DEMAND_DAILY_90D` | Daily demand | `Q04_DEMAND_DAILY_90D_*` |
| `Q05_SUPPLY_HOURLY_7D` | Hourly supply and coverage | `Q05_SUPPLY_HOURLY_7D_*` |
| `Q06_MATCHING_PERFORMANCE_30D` | Matching and offer metrics | `Q06_MATCHING_PERFORMANCE_30D_*` |
| `Q07_MATCHING_FUNNEL_30D` | Five-step funnel | `Q07_MATCHING_FUNNEL_30D_*` |
| `Q08_HEATMAP_7D_250M` | Fine seven-day spatial grid | `Q08_HEATMAP_7D_250M_*` |
| `Q09_HEATMAP_7D_1000M` | Medium seven-day spatial grid | `Q09_HEATMAP_7D_1000M_*` |
| `Q10_HEATMAP_30D_2000M` | Coarse thirty-day spatial grid | `Q10_HEATMAP_30D_2000M_*` |

`*` is replaced by `DIRECT` or `MATERIALIZED` and the file extension appropriate
to the `samples`, `sql` or `explain` directory.

## 4. Test Trace

| Concern | Test class or method |
| --- | --- |
| Telemetry constraints, idempotency and recovery | `AdminAnalyticsTelemetryRepositoryIntegrationTests` |
| Direct metric semantics and Admin RBAC | `AdminAnalyticsDirectQueryIntegrationTests` |
| Time/spatial boundary and index use | `AdminAnalyticsSpatialIntegrationTests` |
| Direct/materialized equivalence, stale data, failure fallback and rollback | `AdminAnalyticsMaterializedIntegrationTests` |
| API annotations, validation and legacy compatibility | `AdminAnalyticsControllerTests`, `AdminAnalyticsBackwardCompatibilityTests` |
| Executable frontend handoff | `postmanConsumerSmokeCollectionExecutesAgainstBackend` |
| Deterministic generation and benchmark artifacts | `AdminAnalyticsBenchmarkArtifactTests`, `AdminAnalyticsBenchmarkIT` |
| Query metrics and freshness | `AnalyticsQueryObservationTests` |
| Rate limiting | `RateLimitFilterIntegrationTests` |
| Production database configuration | `ProductionReadinessValidatorTests`, `RuntimeConfigurationSecurityTests` |
| Full database release chain | `AdminAnalyticsReleaseChainIntegrationTests` |

Test source is under
[`src/test/java/com/example/goride`](../../src/test/java/com/example/goride/).

## 5. Phase and Commit Trace

| Phase | Primary implementation commit |
| --- | --- |
| 0 — contracts and architecture | `cf0c6fd` |
| 1 — persistent telemetry schema | `92a6bc6` |
| 2 — matching instrumentation | `510e727` |
| 3 — direct analytics API | `c02effa` |
| 4 — spatial analytics | `05b96e0` |
| 5 — materialized read models | `4060b6b` |
| 6 — API hardening and handoff | `140c70a` |
| 7 — reproducible benchmark | `54dbb62`, `aaea493` |
| 8 — operational hardening and release validation | `a77b4f2`, `d86cb77` |

Detailed review notes are maintained in
[the implementation log](../implementation-log.md).

## 6. Rule for Every Thesis Table or Figure

A reported table or chart must retain:

1. objective or benchmark question;
2. metric/query ID and variant;
3. metric dictionary and benchmark protocol version;
4. Git commit;
5. environment manifest;
6. dataset profile, seed and distribution fingerprint;
7. query and refresh cutoff;
8. raw sample file;
9. derivation field in `summary.json`;
10. artifact checksum.

If any item is missing, the result is not traceable and must not be presented as
a measured thesis result. The committed smoke artifact is labeled development
evidence; a thesis performance claim requires a controlled `thesis` profile run
and separate review.
