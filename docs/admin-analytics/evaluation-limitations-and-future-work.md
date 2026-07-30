# Admin Analytics Evaluation, Limitations and Future Work

> Evaluation status: core backend methodology verified
>
> Performance-claim status: thesis-scale experiment pending

## 1. Evaluated Questions

The implemented evaluation addresses four questions from the
[benchmark protocol](benchmark-protocol.md):

- whether direct and materialized reads are correct at the same cutoff;
- how latency can be measured by query type and dataset scale;
- what refresh and storage costs a materialized design adds;
- how heatmap range and cell size can be compared.

The evaluation does not assume that materialized reads are always faster.

## 2. Evidence Reviewed in Phase 8

The committed smoke run verified:

- deterministic generation with profile `smoke`, seed `5537` and a stable
  distribution fingerprint across independent databases;
- 10 direct/materialized correctness cases at numeric comparison scale 9;
- 10 warm-up and 50 measured iterations for each query/variant pair;
- 1,000 successful measured samples and no query error;
- independently reproducible summary statistics and SHA-256 checksums;
- captured PostgreSQL/PostGIS, runtime, refresh, storage, SQL and plan metadata.

These facts validate the experimental pipeline. The latency and storage values
inside the smoke artifact describe only the recorded development environment
and 1,000-trip dataset. They are not thesis-scale performance conclusions.

## 3. Evidence-Based Tuning Decision

Phase 4 already introduced a partial time index and a GiST pickup index.
Integration `EXPLAIN ANALYZE` evidence confirms both are usable for the bounded
spatial workload.

No additional index or query-variant change is justified by the smoke run:

- aggregate cases show that materialized reads can help at this scale;
- several demand, supply and spatial cases are close or mixed;
- a 1,000-trip synthetic dataset is too small to infer production behavior;
- storage uses a Docker Desktop virtual disk whose host media is not asserted;
- only warm-cache, single-client mode is part of the core smoke run.

Therefore:

- `DIRECT` remains the production default;
- materialized refresh remains independently deployable and observable;
- materialized query selection remains opt-in with exact direct fallback;
- index changes require thesis-scale or production-like plan evidence, not one
  small-run latency difference.

This is a deliberate evidence-based no-change decision, not an assumption that
direct SQL is universally better.

## 4. Correctness and Quality Findings

- Metric formulas, units, time cohorts and nullable behavior are centralized in
  one versioned dictionary.
- Direct and materialized query ports produce equivalent normalized results at
  the same cutoff for the tested filters.
- Revenue excludes pending and failed payments.
- Open matching runs and non-terminal offers do not enter terminal rates.
- Supply gaps remain visible through coverage and nullable values.
- Spatial cells are stable projected squares returned as WGS84 GeoJSON.
- Admin RBAC, request validation, legacy dashboard compatibility and database
  rollback have regression coverage.
- Query failures, telemetry gaps, refresh failures and materialized freshness
  have operational signals.

## 5. Known Limitations

### Dataset and external validity

- The dataset is synthetic and follows configured peak-hour/hotspot
  distributions; it is not sampled from real users.
- One published seed is the baseline. One seed cannot quantify sensitivity to
  every plausible demand distribution.
- The committed run is `smoke`, not `thesis`, scale.
- No claim is made about another city, geography, vehicle mix or hardware.

### Measurement

- Core mode is warm-cache and single-client.
- JVM, OS scheduling, Docker virtualization and other host load can affect
  latency.
- Storage media is not identified beyond Docker Desktop virtual disk.
- Cold-cache and concurrent-client modes are optional and were not used for the
  committed core evidence.
- Materialized refresh cost is environment- and dataset-dependent.

### Data semantics

- A temporary empty candidate search does not terminate a matching run as
  `NO_DRIVER`; a future product rule must define that terminal threshold.
- Supply is sampled, not reconstructed continuously. Malformed/incomplete Redis
  reads create visible gaps.
- Service areas are current spatial dimensions; historical boundary versioning
  is not implemented.
- The read models support a fixed reporting timezone, projected SRID and base
  cell size. Incompatible requests use direct SQL.
- Materialized overview/matching require full local-day boundaries; heatmap
  bounding boxes are direct-only.
- P50/P95 describe persisted terminal matching runs, not passenger-perceived
  end-to-end time outside the recorded boundaries.

### Operations and security

- Rate limiting is global IP-based, not cost-weighted per Admin or query type.
- Public Actuator scrape endpoints require a production network perimeter.
- A credential once committed remains in Git history and must be rotated
  externally.
- Freshness gauge state is process-local; refresh-state storage is authoritative
  after restart.

## 6. Required Thesis-Scale Procedure

Before reporting latency or storage as a thesis result:

1. use a clean reviewed commit;
2. record a controlled hardware/runtime environment;
3. generate the `thesis` profile with seed `5537`;
4. preserve the dataset manifest and distribution fingerprint;
5. pass every direct/materialized correctness case;
6. run the frozen 10/50 measurement procedure;
7. retain failed samples, raw CSV, plans, refresh and storage evidence;
8. run the standalone summarizer and checksum verification;
9. review outliers and environmental anomalies;
10. archive the artifact set in durable storage and link each thesis table to
    it.

Additional seeds or concurrency experiments should be reported as separate
runs, not merged into the baseline without explanation.

## 7. Future Work

Prioritized extensions after the core thesis scope:

1. run and review the controlled `thesis` profile;
2. add multi-client and cold-cache experiments;
3. evaluate cost-weighted/per-Admin rate limits;
4. version historical service-area boundaries;
5. evaluate H3 or another grid only if multi-resolution analysis is required;
6. add multi-city and multi-timezone read models;
7. evaluate incremental refresh if full refresh cost becomes material;
8. add forecasting and anomaly detection as separate research questions.

Forecasting and anomaly detection are explicitly outside the current core
claims. Their future implementation must not be described as an existing
capability.

## 8. Claim Boundary

The current evidence supports claims that the subsystem:

- implements the documented metrics and Admin APIs;
- preserves durable matching/supply telemetry;
- returns bounded temporal and PostGIS spatial analytics;
- provides direct/materialized correctness checks and a reproducible benchmark
  method;
- exposes operational security and observability controls.

It does not yet support a thesis-scale claim that one query variant has a
general performance advantage, that the workload represents real-city demand,
or that the system performs forecasting/anomaly detection.
