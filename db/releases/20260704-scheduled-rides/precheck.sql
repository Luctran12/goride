SELECT
    column_name,
    data_type,
    is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'trips'
  AND column_name = 'scheduled_pickup_time';

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename = 'trips'
  AND indexname = 'idx_trips_scheduled_pickup_time';