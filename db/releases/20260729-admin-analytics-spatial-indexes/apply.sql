BEGIN;

CREATE INDEX IF NOT EXISTS idx_trips_analytics_requested_at
    ON trips (requested_at)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_trips_pickup_location_gist
    ON trips
    USING GIST (pickup_location)
    WHERE deleted_at IS NULL;

COMMIT;
