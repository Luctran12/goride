BEGIN;

DO $$
DECLARE
    orphan_count BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.payment_sandbox_uat_results'::regclass
          AND conname = 'fk_payment_sandbox_uat_tested_by_user'
    ) AND NOT EXISTS (
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
        RAISE EXCEPTION 'fk_payment_sandbox_uat_tested_by_user exists with an unexpected definition';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.payment_sandbox_uat_results'::regclass
          AND conname = 'fk_payment_sandbox_uat_tested_by_user'
    ) THEN
        SELECT COUNT(*)
        INTO orphan_count
        FROM payment_sandbox_uat_results result
        LEFT JOIN users actor ON actor.id = result.tested_by_user_id
        WHERE result.tested_by_user_id IS NOT NULL
          AND actor.id IS NULL;

        IF orphan_count > 0 THEN
            RAISE EXCEPTION
                'Cannot add fk_payment_sandbox_uat_tested_by_user: % orphan actor references found',
                orphan_count;
        END IF;

        ALTER TABLE payment_sandbox_uat_results
            ADD CONSTRAINT fk_payment_sandbox_uat_tested_by_user
            FOREIGN KEY (tested_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;
    END IF;
END $$;

COMMIT;
