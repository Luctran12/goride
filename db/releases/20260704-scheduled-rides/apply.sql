BEGIN;

ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS scheduled_pickup_time TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_trips_scheduled_pickup_time
    ON trips (status, scheduled_pickup_time)
    WHERE deleted_at IS NULL;

COMMIT;