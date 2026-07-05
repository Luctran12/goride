BEGIN;

DROP INDEX IF EXISTS idx_trips_scheduled_pickup_time;

ALTER TABLE trips
    DROP COLUMN IF EXISTS scheduled_pickup_time;

COMMIT;