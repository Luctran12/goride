DO $$
DECLARE
    view_name TEXT;
    index_name TEXT;
BEGIN
    FOREACH view_name IN ARRAY ARRAY[
        'mv_trip_daily',
        'mv_demand_hourly_cell',
        'mv_supply_hourly',
        'mv_matching_daily'
    ]
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM pg_matviews
            WHERE schemaname = 'analytics'
              AND matviewname = view_name
        ) THEN
            RAISE EXCEPTION 'analytics.% is missing', view_name;
        END IF;
    END LOOP;

    FOREACH index_name IN ARRAY ARRAY[
        'uq_mv_trip_daily',
        'uq_mv_demand_hourly_cell',
        'uq_mv_supply_hourly',
        'uq_mv_matching_daily'
    ]
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM pg_index index_metadata
            JOIN pg_class index_object
              ON index_object.oid = index_metadata.indexrelid
            JOIN pg_namespace index_schema
              ON index_schema.oid = index_object.relnamespace
            WHERE index_schema.nspname = 'analytics'
              AND index_object.relname = index_name
              AND index_metadata.indisunique
              AND index_metadata.indisvalid
              AND index_metadata.indpred IS NULL
        ) THEN
            RAISE EXCEPTION
                'analytics.% is missing or is not a qualifying unique index',
                index_name;
        END IF;
    END LOOP;

    IF NOT EXISTS (
        SELECT 1
        FROM analytics.materialized_refresh_state
        WHERE id = 1
          AND reporting_timezone = 'Asia/Ho_Chi_Minh'
          AND projected_srid = 32648
          AND base_cell_size_meters = 250
    ) THEN
        RAISE EXCEPTION 'materialized refresh metadata is missing or incompatible';
    END IF;
END $$;
