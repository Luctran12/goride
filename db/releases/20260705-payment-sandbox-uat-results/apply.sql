BEGIN;

CREATE TABLE IF NOT EXISTS payment_sandbox_uat_results (
    id BIGSERIAL PRIMARY KEY,
    provider_name VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_RUN',
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
    CONSTRAINT chk_payment_sandbox_uat_status CHECK (status IN ('NOT_RUN', 'BLOCKED', 'FAILED', 'PASSED')),
    CONSTRAINT chk_payment_sandbox_uat_provider_not_blank CHECK (length(btrim(provider_name)) > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_sandbox_uat_provider
    ON payment_sandbox_uat_results (provider_name);

COMMIT;