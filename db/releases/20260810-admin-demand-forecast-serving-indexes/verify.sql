BEGIN;

DO $$
DECLARE
    missing_indexes TEXT[];
BEGIN
    SELECT ARRAY_AGG(expected.index_name ORDER BY expected.index_name)
    INTO missing_indexes
    FROM (
        VALUES
            ('idx_demand_forecasts_serving_lookup'),
            ('idx_demand_forecasts_hotspot_lookup')
    ) AS expected(index_name)
    WHERE NOT EXISTS (
        SELECT 1
        FROM pg_class index_object
        JOIN pg_index index_info ON index_info.indexrelid = index_object.oid
        WHERE index_object.relname = expected.index_name
          AND index_info.indisvalid
          AND index_info.indisready
    );

    IF missing_indexes IS NOT NULL THEN
        RAISE EXCEPTION 'Missing or invalid Phase 8 indexes: %', missing_indexes;
    END IF;
END
$$;

COMMIT;
