-- Purpose: remove only Phase 2 forecasting objects. Stop writers and export
-- evidence before running this script on any database containing real runs.

BEGIN;

DROP TABLE IF EXISTS analytics.forecast_evaluations;
DROP TABLE IF EXISTS analytics.demand_forecasts;
DROP TABLE IF EXISTS analytics.forecast_runs;
DROP TABLE IF EXISTS analytics.model_versions;
DROP TABLE IF EXISTS analytics.demand_features;
DROP TABLE IF EXISTS analytics.data_quality_results;
DROP TABLE IF EXISTS analytics.processing_runs;
DROP FUNCTION IF EXISTS analytics.enforce_published_forecast_model();

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_namespace WHERE nspname = 'analytics'
    ) AND NOT EXISTS (
        SELECT 1
        FROM pg_class object
        JOIN pg_namespace object_schema ON object_schema.oid = object.relnamespace
        WHERE object_schema.nspname = 'analytics'
    ) THEN
        DROP SCHEMA analytics;
    END IF;
END $$;

COMMIT;
