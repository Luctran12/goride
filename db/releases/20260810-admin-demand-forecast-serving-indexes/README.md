# Admin demand forecast serving indexes

This additive release supports the Phase 8 read-only API access paths.

Apply in order:

1. `precheck.sql`
2. `apply.sql`
3. `verify.sql`

`idx_demand_forecasts_serving_lookup` supports deterministic map/series reads for one
forecast run and horizon. `idx_demand_forecasts_hotspot_lookup` supports descending
predicted-demand ranking without a table-wide sort. The existing PostGIS GiST index
continues to serve bounding-box filters.

Run `rollback.sql` only when the Phase 8 endpoints are no longer deployed.
