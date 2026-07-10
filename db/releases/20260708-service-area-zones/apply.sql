BEGIN;

CREATE TABLE IF NOT EXISTS service_areas (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    city_name VARCHAR(120) NOT NULL,
    country_code CHAR(2) NOT NULL DEFAULT 'VN',
    boundary geometry(Polygon, 4326) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_service_areas_name CHECK (btrim(name) <> ''),
    CONSTRAINT chk_service_areas_city_name CHECK (btrim(city_name) <> ''),
    CONSTRAINT chk_service_areas_country_code CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT chk_service_areas_boundary_valid CHECK (ST_IsValid(boundary)),
    CONSTRAINT chk_service_areas_boundary_srid CHECK (ST_SRID(boundary) = 4326)
);

CREATE INDEX IF NOT EXISTS idx_service_areas_city_active
    ON service_areas (city_name, is_active);

CREATE INDEX IF NOT EXISTS idx_service_areas_boundary_gist
    ON service_areas
    USING GIST (boundary);

COMMIT;