SELECT column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'driver_profiles'
  AND column_name IN ('license_image_url', 'id_card_image_url', 'vehicle_registration_url')
ORDER BY column_name;