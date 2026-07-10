DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_extension
        WHERE extname = 'postgis'
    ) THEN
        RAISE EXCEPTION 'PostGIS extension must be installed before applying service area zones release';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'service_areas'
    ) THEN
        RAISE NOTICE 'service_areas already exists; verify schema compatibility before applying';
    END IF;
END $$;