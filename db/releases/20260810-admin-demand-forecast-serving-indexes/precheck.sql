BEGIN;

DO $$
BEGIN
    IF to_regclass('analytics.demand_forecasts') IS NULL THEN
        RAISE EXCEPTION 'analytics.demand_forecasts is required before Phase 8 indexes';
    END IF;
END
$$;

COMMIT;
