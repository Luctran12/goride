BEGIN;

-- This removes stored document URL metadata, not files in object storage.
ALTER TABLE driver_profiles
    DROP COLUMN IF EXISTS vehicle_registration_url,
    DROP COLUMN IF EXISTS id_card_image_url,
    DROP COLUMN IF EXISTS license_image_url;

COMMIT;