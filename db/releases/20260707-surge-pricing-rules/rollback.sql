BEGIN;

DROP TABLE IF EXISTS surge_pricing_rules;

ALTER TABLE trips
    DROP COLUMN IF EXISTS fare_surge_multiplier;

COMMIT;