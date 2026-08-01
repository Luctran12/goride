DO $$
DECLARE
    missing_objects TEXT[];
    missing_constraints TEXT[];
BEGIN
    SELECT ARRAY_AGG(object_name)
    INTO missing_objects
    FROM (
        VALUES
            ('matching_runs'),
            ('matching_offer_events'),
            ('driver_supply_snapshots'),
            ('uq_matching_runs_one_open_per_trip'),
            ('uq_matching_offer_events_run_attempt'),
            ('uq_matching_offer_events_one_accepted_per_run'),
            ('uq_driver_supply_snapshots_bucket_area_vehicle'),
            ('idx_matching_runs_started_at'),
            ('idx_matching_runs_outcome_started_at'),
            ('idx_matching_offer_events_driver_offered_at'),
            ('idx_matching_offer_events_outcome_offered_at'),
            ('idx_driver_supply_snapshots_bucket_vehicle'),
            ('idx_driver_supply_snapshots_area_bucket')
    ) AS expected(object_name)
    WHERE to_regclass('public.' || object_name) IS NULL;

    IF missing_objects IS NOT NULL THEN
        RAISE EXCEPTION 'Telemetry release objects are missing: %', missing_objects;
    END IF;

    SELECT ARRAY_AGG(constraint_name)
    INTO missing_constraints
    FROM (
        VALUES
            ('fk_matching_runs_trip'),
            ('fk_matching_runs_matched_driver'),
            ('chk_matching_runs_outcome'),
            ('chk_matching_runs_trigger_type'),
            ('chk_matching_runs_counters'),
            ('chk_matching_runs_finished_time'),
            ('chk_matching_runs_state'),
            ('fk_matching_offer_events_run'),
            ('fk_matching_offer_events_driver'),
            ('uq_matching_offer_events_run_attempt'),
            ('chk_matching_offer_events_outcome'),
            ('chk_matching_offer_events_attempt'),
            ('chk_matching_offer_events_candidate_rank'),
            ('chk_matching_offer_events_candidate_distance'),
            ('chk_matching_offer_events_expiry'),
            ('chk_matching_offer_events_response_time'),
            ('chk_matching_offer_events_state'),
            ('fk_driver_supply_snapshots_service_area'),
            ('chk_driver_supply_snapshots_vehicle_type'),
            ('chk_driver_supply_snapshots_counts'),
            ('chk_driver_supply_snapshots_sample_time')
    ) AS expected(constraint_name)
    WHERE NOT EXISTS (
        SELECT 1
        FROM pg_constraint constraint_info
        WHERE constraint_info.conname = expected.constraint_name
          AND constraint_info.conrelid IN (
              'public.matching_runs'::regclass,
              'public.matching_offer_events'::regclass,
              'public.driver_supply_snapshots'::regclass
          )
    );

    IF missing_constraints IS NOT NULL THEN
        RAISE EXCEPTION 'Telemetry constraints are missing: %', missing_constraints;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_index index_info
        JOIN pg_class index_class ON index_class.oid = index_info.indexrelid
        WHERE index_class.relname = 'uq_driver_supply_snapshots_bucket_area_vehicle'
          AND index_info.indnullsnotdistinct
    ) THEN
        RAISE EXCEPTION 'Snapshot unique index must use NULLS NOT DISTINCT';
    END IF;
END $$;

SELECT
    table_name,
    (
        SELECT COUNT(*)
        FROM information_schema.columns column_info
        WHERE column_info.table_schema = 'public'
          AND column_info.table_name = tables.table_name
    ) AS column_count
FROM (
    VALUES
        ('matching_runs'),
        ('matching_offer_events'),
        ('driver_supply_snapshots')
) AS tables(table_name)
ORDER BY table_name;
