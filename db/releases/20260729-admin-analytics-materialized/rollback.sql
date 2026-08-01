BEGIN;

DROP MATERIALIZED VIEW IF EXISTS analytics.mv_matching_daily;
DROP MATERIALIZED VIEW IF EXISTS analytics.mv_supply_hourly;
DROP MATERIALIZED VIEW IF EXISTS analytics.mv_demand_hourly_cell;
DROP MATERIALIZED VIEW IF EXISTS analytics.mv_trip_daily;
DROP TABLE IF EXISTS analytics.materialized_refresh_state;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_namespace
        WHERE nspname = 'analytics'
    ) AND NOT EXISTS (
        SELECT 1
        FROM pg_class object
        JOIN pg_namespace schema ON schema.oid = object.relnamespace
        WHERE schema.nspname = 'analytics'
    ) THEN
        DROP SCHEMA analytics;
    END IF;
END $$;

COMMIT;
