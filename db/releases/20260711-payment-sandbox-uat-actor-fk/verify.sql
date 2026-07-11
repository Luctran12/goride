DO $$
DECLARE
    orphan_count BIGINT;
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint constraint_info
        WHERE constraint_info.conrelid = 'public.payment_sandbox_uat_results'::regclass
          AND constraint_info.confrelid = 'public.users'::regclass
          AND constraint_info.conname = 'fk_payment_sandbox_uat_tested_by_user'
          AND constraint_info.contype = 'f'
          AND constraint_info.confdeltype = 'r'
          AND constraint_info.conkey = ARRAY[
              (
                  SELECT attribute.attnum
                  FROM pg_attribute attribute
                  WHERE attribute.attrelid = constraint_info.conrelid
                    AND attribute.attname = 'tested_by_user_id'
              )
          ]::SMALLINT[]
          AND constraint_info.confkey = ARRAY[
              (
                  SELECT attribute.attnum
                  FROM pg_attribute attribute
                  WHERE attribute.attrelid = constraint_info.confrelid
                    AND attribute.attname = 'id'
              )
          ]::SMALLINT[]
    ) THEN
        RAISE EXCEPTION 'fk_payment_sandbox_uat_tested_by_user is missing or invalid';
    END IF;

    SELECT COUNT(*)
    INTO orphan_count
    FROM payment_sandbox_uat_results result
    LEFT JOIN users actor ON actor.id = result.tested_by_user_id
    WHERE result.tested_by_user_id IS NOT NULL
      AND actor.id IS NULL;

    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'payment_sandbox_uat_results contains % orphan actor references', orphan_count;
    END IF;
END $$;
