BEGIN;

DROP INDEX IF EXISTS idx_service_areas_boundary_gist;
DROP INDEX IF EXISTS idx_service_areas_city_active;
DROP TABLE IF EXISTS service_areas;

COMMIT;