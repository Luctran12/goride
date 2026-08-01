BEGIN;

DROP INDEX IF EXISTS idx_trips_pickup_location_gist;
DROP INDEX IF EXISTS idx_trips_analytics_requested_at;

COMMIT;
