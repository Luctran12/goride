DO $$
DECLARE
    missing_tables TEXT[];
    existing_targets TEXT[];
BEGIN
    SELECT ARRAY_AGG(required_table)
    INTO missing_tables
    FROM (
        VALUES ('trips'), ('users'), ('service_areas')
    ) AS required(required_table)
    WHERE to_regclass('public.' || required_table) IS NULL;

    IF missing_tables IS NOT NULL THEN
        RAISE EXCEPTION 'Required source tables are missing: %', missing_tables;
    END IF;

    SELECT ARRAY_AGG(target_table)
    INTO existing_targets
    FROM (
        VALUES ('matching_runs'), ('matching_offer_events'), ('driver_supply_snapshots')
    ) AS target(target_table)
    WHERE to_regclass('public.' || target_table) IS NOT NULL;

    IF existing_targets IS NOT NULL THEN
        RAISE EXCEPTION 'Telemetry target tables already exist: %', existing_targets;
    END IF;
END $$;
