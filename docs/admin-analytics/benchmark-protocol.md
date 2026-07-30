# Admin Analytics Benchmark Protocol

> Version: 1.0
>
> Phase: 07 - Reproducible Dataset and Benchmark
>
> Status: Implemented; methodology and raw evidence pending review

## 1. Objective

Compare:

- Variant A: direct SQL on operational and telemetry tables.
- Variant B: SQL on materialized analytical read models.

The benchmark evaluates:

- correctness;
- query latency;
- refresh duration;
- data freshness;
- storage overhead.

It does not assume Variant B is faster.

---

## 2. Benchmark Questions

### BQ1

Do direct and materialized variants return equivalent results at the same cutoff?

### BQ2

How do average, P50 and P95 query latencies differ by query type and dataset scale?

### BQ3

What storage and refresh costs are introduced by materialized read models?

### BQ4

How does spatial heatmap latency change with date range and cell size?

---

## 3. Dataset Profiles

| Profile | Trips | Matching offers | Location points | Purpose |
| --- | ---: | ---: | ---: | --- |
| `smoke` | 1,000 | 2,500 | 20,000 | Local correctness and script smoke |
| `medium` | 25,000 | 65,000 | 500,000 | Development tuning |
| `thesis` | 100,000 | 250,000 | 2,000,000 | Reported experiment |

Each generation run records:

- generator version;
- seed;
- profile;
- row count by table;
- start/end timestamp;
- PostgreSQL/PostGIS version;
- generation duration.

The published thesis seed is `5537`. Additional seeds may be used for
sensitivity analysis but cannot replace the published baseline silently.
Profile definitions are versioned in
[`profiles.json`](../../benchmarks/admin-analytics/profiles.json).

---

## 4. Environment Manifest

Record before each benchmark:

```text
timestamp
git commit
branch
operating system
CPU model and logical core count
RAM
storage type
Java version
PostgreSQL version
PostGIS version
database configuration overrides
container/runtime versions
dataset profile and seed
table/index/materialized-view sizes
```

Do not compare results from different environment manifests as if they were one controlled run.

---

## 5. Correctness Gate

Before timing:

1. Refresh materialized views to a recorded cutoff.
2. Run direct queries with the same cutoff.
3. Normalize stable ordering and numbers to 9 decimal places, which is stricter
   than the scale of the published API metrics.
4. Compare every metric and every returned bucket/cell.
5. Stop the benchmark if results differ.

Required edge fixtures:

- empty range;
- timezone day boundary;
- cancelled and no-driver trips;
- pending/failed/completed payments;
- open and terminal matching runs;
- rejected, timed-out and accepted offers;
- missing supply snapshots;
- pickup points on a spatial boundary.

---

## 6. Query Cases

Core query IDs:

```text
Q01_OVERVIEW_7D
Q02_OVERVIEW_30D
Q03_DEMAND_HOURLY_7D
Q04_DEMAND_DAILY_90D
Q05_SUPPLY_HOURLY_7D
Q06_MATCHING_PERFORMANCE_30D
Q07_MATCHING_FUNNEL_30D
Q08_HEATMAP_7D_250M
Q09_HEATMAP_7D_1000M
Q10_HEATMAP_30D_2000M
```

Each query case fixes:

- filters;
- range;
- timezone;
- vehicle type;
- service area;
- cell size where applicable.

---

## 7. Measurement Procedure

Core reported mode: warm-cache, single-client latency.

For each query and variant:

1. Verify correctness.
2. Run 10 warm-up iterations.
3. Run 50 measured iterations.
4. Record each raw duration independently.
5. Alternate direct/materialized execution order deterministically from the
   dataset seed, query ID and iteration so neither variant always runs first.
6. Record query errors; do not drop failed samples silently.
7. Capture one representative `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`.

Optional modes:

- cold-cache exploratory run;
- five-client concurrency run.

Optional results must be labeled separately from the core mode.

---

## 8. Derived Statistics

For each query/variant:

- sample count;
- error count;
- minimum;
- maximum;
- arithmetic mean;
- P50;
- P95;
- standard deviation.

Percentiles are calculated from raw measured samples, not rounded aggregate logs.

No percentage improvement is reported when the baseline is zero or when correctness fails.

---

## 9. Materialized Refresh Measurement

Record:

- refresh start/end;
- refresh duration;
- concurrent or non-concurrent mode;
- rows before/after;
- view size;
- index size;
- cutoff represented by the refreshed data;
- refresh error, if any.

Freshness lag:

```text
queryStart - dataFreshnessAt
```

---

## 10. Storage Measurement

Record with PostgreSQL size functions:

- source table size;
- source index size;
- each materialized view size;
- each materialized index size;
- total analytics storage overhead.

Storage overhead must be reported as both bytes and a ratio against the source dataset size.

---

## 11. Raw Artifact Layout

```text
benchmark-results/
  <timestamp>-<commit>-<profile>-<seed>/
    environment.json
    dataset-manifest.json
    correctness.json
    samples/
      Q01_OVERVIEW_7D_DIRECT.csv
      Q01_OVERVIEW_7D_MATERIALIZED.csv
    explain/
      Q01_OVERVIEW_7D_DIRECT.json
      Q01_OVERVIEW_7D_MATERIALIZED.json
    refresh/
      refresh-samples.csv
    storage.json
    summary.json
    README.md
```

Generated artifacts are not committed when excessively large. The repository must still contain:

- schemas;
- runner;
- summarizer;
- example small output;
- checksum/location instructions for thesis artifacts.

The executable generator, runner and summarizer are documented in
[`benchmarks/admin-analytics/README.md`](../../benchmarks/admin-analytics/README.md).
The runner refuses non-empty output directories and writes SHA-256 checksums
after every core artifact.

The committed
[`smoke` example](benchmark-example/smoke-seed-5537/README.md) demonstrates the
artifact layout and the 10/50 procedure. It is development evidence for one
environment, not the thesis-scale result.

---

## 12. Reporting Rules

- Report exact dataset profile and seed.
- Report environment limitations.
- Distinguish measured result from interpretation.
- Do not generalize beyond tested ranges.
- Preserve failed runs and explain exclusions.
- Link every thesis table to a query ID and raw artifact set.
