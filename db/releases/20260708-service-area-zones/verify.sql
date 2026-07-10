DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'service_areas'
    ) THEN
        RAISE EXCEPTION 'service_areas table is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND tablename = 'service_areas'
          AND indexname = 'idx_service_areas_city_active'
    ) THEN
        RAISE EXCEPTION 'idx_service_areas_city_active is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND tablename = 'service_areas'
          AND indexname = 'idx_service_areas_boundary_gist'
    ) THEN
        RAISE EXCEPTION 'idx_service_areas_boundary_gist is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'service_areas'
          AND column_name = 'boundary'
          AND udt_name = 'geometry'
    ) THEN
        RAISE EXCEPTION 'service_areas.boundary geometry column is missing';
    END IF;
END $$;