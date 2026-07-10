BEGIN;

DROP INDEX IF EXISTS idx_payment_sandbox_e2e_status;
DROP INDEX IF EXISTS idx_payment_sandbox_e2e_provider_tested_at;
DROP TABLE IF EXISTS payment_sandbox_e2e_sessions;

COMMIT;