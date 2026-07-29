DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND tablename = 'trips'
          AND indexname = 'idx_trips_analytics_requested_at'
    ) THEN
        RAISE EXCEPTION 'idx_trips_analytics_requested_at is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND tablename = 'trips'
          AND indexname = 'idx_trips_pickup_location_gist'
    ) THEN
        RAISE EXCEPTION 'idx_trips_pickup_location_gist is missing';
    END IF;
END $$;
