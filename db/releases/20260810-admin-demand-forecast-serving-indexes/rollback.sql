BEGIN;

DROP INDEX IF EXISTS analytics.idx_demand_forecasts_hotspot_lookup;
DROP INDEX IF EXISTS analytics.idx_demand_forecasts_serving_lookup;

COMMIT;
