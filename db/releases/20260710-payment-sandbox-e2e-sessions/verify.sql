DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'payment_sandbox_e2e_sessions'
    ) THEN
        RAISE EXCEPTION 'payment_sandbox_e2e_sessions table is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND tablename = 'payment_sandbox_e2e_sessions'
          AND indexname = 'idx_payment_sandbox_e2e_provider_tested_at'
    ) THEN
        RAISE EXCEPTION 'idx_payment_sandbox_e2e_provider_tested_at is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND tablename = 'payment_sandbox_e2e_sessions'
          AND indexname = 'idx_payment_sandbox_e2e_status'
    ) THEN
        RAISE EXCEPTION 'idx_payment_sandbox_e2e_status is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'payment_sandbox_e2e_sessions'
          AND column_name = 'success_transaction_ref'
    ) THEN
        RAISE EXCEPTION 'success_transaction_ref evidence column is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'payment_sandbox_e2e_sessions'
          AND column_name = 'freshness_rejection_tested'
    ) THEN
        RAISE EXCEPTION 'freshness_rejection_tested evidence column is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.payment_sandbox_e2e_sessions'::regclass
          AND confrelid = 'public.payments'::regclass
          AND conname = 'fk_payment_sandbox_e2e_checkout_payment'
          AND contype = 'f'
          AND confdeltype = 'r'
    ) THEN
        RAISE EXCEPTION 'fk_payment_sandbox_e2e_checkout_payment is missing or invalid';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.payment_sandbox_e2e_sessions'::regclass
          AND confrelid = 'public.payments'::regclass
          AND conname = 'fk_payment_sandbox_e2e_success_payment'
          AND contype = 'f'
          AND confdeltype = 'r'
    ) THEN
        RAISE EXCEPTION 'fk_payment_sandbox_e2e_success_payment is missing or invalid';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.payment_sandbox_e2e_sessions'::regclass
          AND confrelid = 'public.payments'::regclass
          AND conname = 'fk_payment_sandbox_e2e_failure_payment'
          AND contype = 'f'
          AND confdeltype = 'r'
    ) THEN
        RAISE EXCEPTION 'fk_payment_sandbox_e2e_failure_payment is missing or invalid';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.payment_sandbox_e2e_sessions'::regclass
          AND confrelid = 'public.users'::regclass
          AND conname = 'fk_payment_sandbox_e2e_tested_by_user'
          AND contype = 'f'
          AND confdeltype = 'r'
    ) THEN
        RAISE EXCEPTION 'fk_payment_sandbox_e2e_tested_by_user is missing or invalid';
    END IF;
END $$;