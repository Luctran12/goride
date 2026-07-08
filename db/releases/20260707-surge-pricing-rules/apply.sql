BEGIN;

ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS fare_surge_multiplier NUMERIC(4,2) NOT NULL DEFAULT 1.00;

UPDATE trips t
SET fare_surge_multiplier = COALESCE(pc.surge_multiplier, 1.00)
FROM pricing_config pc
WHERE t.pricing_config_id = pc.id
  AND t.fare_surge_multiplier = 1.00;

CREATE TABLE IF NOT EXISTS surge_pricing_rules (
    id BIGSERIAL PRIMARY KEY,
    vehicle_type VARCHAR(20) NOT NULL,
    name VARCHAR(120) NOT NULL,
    min_demand_trips INTEGER NOT NULL DEFAULT 1,
    min_demand_supply_ratio NUMERIC(5,2) NOT NULL,
    multiplier NUMERIC(4,2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_surge_pricing_rules_min_demand CHECK (min_demand_trips >= 1),
    CONSTRAINT chk_surge_pricing_rules_ratio CHECK (min_demand_supply_ratio > 0),
    CONSTRAINT chk_surge_pricing_rules_multiplier CHECK (multiplier >= 1.00 AND multiplier <= 3.00),
    CONSTRAINT chk_surge_pricing_rules_window CHECK (starts_at IS NULL OR ends_at IS NULL OR starts_at < ends_at)
);

CREATE INDEX IF NOT EXISTS idx_surge_pricing_rules_vehicle_active
    ON surge_pricing_rules (vehicle_type, is_active);

CREATE INDEX IF NOT EXISTS idx_surge_pricing_rules_window
    ON surge_pricing_rules (starts_at, ends_at);

COMMIT;