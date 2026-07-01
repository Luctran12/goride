BEGIN;

ALTER TABLE driver_profiles
    ADD COLUMN IF NOT EXISTS license_image_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS id_card_image_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS vehicle_registration_url VARCHAR(500);

COMMIT;