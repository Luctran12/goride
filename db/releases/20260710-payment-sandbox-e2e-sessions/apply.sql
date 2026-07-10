BEGIN;

CREATE TABLE IF NOT EXISTS payment_sandbox_e2e_sessions (
    id BIGSERIAL PRIMARY KEY,
    provider_name VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_RUN',
    checkout_payment_id BIGINT,
    checkout_url VARCHAR(2048),
    success_payment_id BIGINT,
    success_transaction_ref VARCHAR(100),
    failure_payment_id BIGINT,
    failure_transaction_ref VARCHAR(100),
    replay_transaction_ref VARCHAR(100),
    checkout_url_tested BOOLEAN NOT NULL DEFAULT false,
    success_callback_tested BOOLEAN NOT NULL DEFAULT false,
    failure_callback_tested BOOLEAN NOT NULL DEFAULT false,
    idempotent_replay_tested BOOLEAN NOT NULL DEFAULT false,
    freshness_rejection_tested BOOLEAN NOT NULL DEFAULT false,
    notes VARCHAR(1000),
    tested_at TIMESTAMPTZ,
    tested_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_payment_sandbox_e2e_status CHECK (status IN ('NOT_RUN', 'BLOCKED', 'FAILED', 'PASSED')),
    CONSTRAINT chk_payment_sandbox_e2e_provider_not_blank CHECK (length(btrim(provider_name)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_payment_sandbox_e2e_provider_tested_at
    ON payment_sandbox_e2e_sessions (provider_name, tested_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_sandbox_e2e_status
    ON payment_sandbox_e2e_sessions (status);

COMMIT;