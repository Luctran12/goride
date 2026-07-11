BEGIN;

ALTER TABLE payment_sandbox_uat_results
    DROP CONSTRAINT IF EXISTS fk_payment_sandbox_uat_tested_by_user;

COMMIT;
