DO $$
BEGIN
    IF to_regclass('public.users') IS NULL THEN
        RAISE EXCEPTION 'users table is missing';
    END IF;
    IF to_regclass('public.payment_sandbox_uat_results') IS NULL THEN
        RAISE EXCEPTION 'payment_sandbox_uat_results table is missing';
    END IF;
END $$;

SELECT COUNT(*) AS orphaned_tested_by_user_rows
FROM payment_sandbox_uat_results result
LEFT JOIN users actor ON actor.id = result.tested_by_user_id
WHERE result.tested_by_user_id IS NOT NULL
  AND actor.id IS NULL;
