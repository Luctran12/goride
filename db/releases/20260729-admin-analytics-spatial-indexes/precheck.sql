DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_extension
        WHERE extname = 'postgis'
    ) THEN
        RAISE EXCEPTION 'PostGIS must be installed before applying spatial analytics indexes';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'trips'
          AND column_name = 'pickup_location'
          AND udt_name = 'geometry'
    ) THEN
        RAISE EXCEPTION 'trips.pickup_location geometry column is missing';
    END IF;
END $$;
