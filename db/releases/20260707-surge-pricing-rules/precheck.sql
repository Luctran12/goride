SELECT
    to_regclass('public.surge_pricing_rules') AS surge_pricing_rules_table,
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'trips'
          AND column_name = 'fare_surge_multiplier'
    ) AS trips_fare_surge_multiplier_exists;