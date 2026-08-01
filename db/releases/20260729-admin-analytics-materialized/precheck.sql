DO $$
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
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name IN (
              'trips',
              'payments',
              'trip_status_history',
              'matching_runs',
              'matching_offer_events',
              'driver_supply_snapshots',
              'service_areas'
          )
        GROUP BY table_schema
        HAVING COUNT(*) = 7
    ) THEN
        RAISE EXCEPTION 'One or more analytics source tables are missing';
    END IF;
END $$;
