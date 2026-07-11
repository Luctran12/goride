SELECT
    to_regclass('public.users') AS users_table,
    to_regclass('public.payments') AS payments_table,
    to_regclass('public.payment_sandbox_uat_results') AS payment_sandbox_uat_results_table,
    to_regclass('public.payment_sandbox_e2e_sessions') AS payment_sandbox_e2e_sessions_table;