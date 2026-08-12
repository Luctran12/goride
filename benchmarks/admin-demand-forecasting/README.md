# Admin Demand Forecasting Benchmarks

Phase 11 stores small, reviewable summaries here; raw datasets, Parquet files
and model binaries remain under the external `GORIDE_ANALYTICS_DATA_ROOT`.

- `phase11-thesis-verification.json`: checksum/lineage/privacy/storage result
  produced by `verify-thesis-evidence` over the frozen Porto artifact set.
- `phase11-serving-benchmark.json`: 100-request-per-endpoint Spring/PostGIS
  Testcontainers measurement. It is synthetic serving-plumbing evidence and
  must not be cited as Porto model-accuracy or production-capacity evidence.
- `checksums.sha256`: SHA-256 for the two tracked result summaries.

Regenerate frozen verification from the repository root:

```powershell
.\scripts\run-demand-forecasting-thesis.ps1 `
  -DataRoot D:\path\to\goride-analytics-data `
  -Mode VerifyFrozen `
  -OutputDirectory target/phase11/thesis-verification
```

Regenerate the synthetic serving benchmark with Docker available:

```powershell
mvn -Dtest=AdminDemandForecastServingIntegrationTests test
```

The benchmark raw output is written to `target/phase11` before its reviewed
summary is copied here.
