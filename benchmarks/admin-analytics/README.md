# Admin Analytics Reproducible Benchmark

This benchmark generates synthetic data in an empty PostgreSQL/PostGIS
database, refreshes the materialized analytical read models, verifies
direct/materialized correctness and only then records latency.
Numeric correctness is compared after normalization to 9 decimal places,
which is stricter than every published Admin Analytics response scale.

## Safety

The generator refuses to run when application users, trips or benchmark
telemetry already exist. Run it only through the supplied Testcontainers
runner or against a disposable database whose name contains `benchmark`.
Generated people, phones, emails, coordinates and payments are synthetic.

## Profiles

Profile sizes and the published thesis seed are versioned in
[`profiles.json`](profiles.json). `smoke`, `medium` and `thesis` preserve the
same deterministic temporal, spatial and outcome distributions.

## Run

From the repository root:

```powershell
.\scripts\run-admin-analytics-benchmark.ps1 -Profile smoke -Seed 5537
```

The default reported procedure uses 10 warm-up and 50 measured iterations for
each query/variant. Set `-StorageDescription` to a factual description of the
host storage used by Docker. Override `-MavenCommand` when the wrapper is
unavailable:

```powershell
.\scripts\run-admin-analytics-benchmark.ps1 `
  -Profile smoke `
  -Seed 5537 `
  -StorageDescription "NVMe SSD; Docker Desktop WSL2 virtual disk" `
  -MavenCommand C:\path\to\mvn.cmd
```

Runs are written beneath the ignored `benchmark-results/` directory. Each run
contains:

- environment and dataset manifests;
- correctness results;
- raw CSV samples;
- `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` plans;
- refresh and relation-size evidence;
- a JSON statistical summary;
- SHA-256 checksums.

Recompute a summary from retained samples with:

```powershell
.\scripts\summarize-admin-analytics-benchmark.ps1 `
  -RunDirectory .\benchmark-results\<run>
```

Large benchmark artifacts must remain outside Git. Preserve thesis runs in a
durable location and record the artifact directory/checksum in the thesis.
