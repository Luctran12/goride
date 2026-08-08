-- Purpose: fail before apply when the database cannot satisfy the frozen
-- demand-forecasting persistence contract.

DO $$
DECLARE
    existing_tables TEXT[];
BEGIN
    IF current_setting('server_version_num')::INTEGER < 150000 THEN
        RAISE EXCEPTION 'PostgreSQL 15 or newer is required';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_extension WHERE extname = 'postgis'
    ) THEN
        RAISE EXCEPTION 'PostGIS must be installed';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM spatial_ref_sys WHERE srid = 3763
    ) OR NOT EXISTS (
        SELECT 1 FROM spatial_ref_sys WHERE srid = 32648
    ) THEN
        RAISE EXCEPTION 'EPSG:3763 and EPSG:32648 must exist in spatial_ref_sys';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_timezone_names WHERE name = 'Europe/Lisbon'
    ) OR NOT EXISTS (
        SELECT 1 FROM pg_timezone_names WHERE name = 'Asia/Ho_Chi_Minh'
    ) THEN
        RAISE EXCEPTION 'Required IANA timezone definitions are missing';
    END IF;

    SELECT ARRAY_AGG(table_name ORDER BY table_name)
    INTO existing_tables
    FROM information_schema.tables
    WHERE table_schema = 'analytics'
      AND table_name IN (
          'processing_runs',
          'data_quality_results',
          'demand_features',
          'model_versions',
          'forecast_runs',
          'demand_forecasts',
          'forecast_evaluations'
      );

    IF existing_tables IS NOT NULL THEN
        RAISE EXCEPTION
            'Forecasting tables already exist or release is partially applied: %',
            existing_tables;
    END IF;
END $$;
