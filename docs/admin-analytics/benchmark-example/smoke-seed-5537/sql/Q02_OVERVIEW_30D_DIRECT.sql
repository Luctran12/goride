SELECT
    COUNT(*) AS trip_requests,
    COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed_request_cohort,
    (
        SELECT COUNT(*)
        FROM payments
        WHERE status = 'COMPLETED'
          AND paid_at >= TIMESTAMPTZ '2026-04-02T17:00:00Z'
          AND paid_at < TIMESTAMPTZ '2026-05-02T17:00:00Z'
    ) AS completed_payments,
    (
        SELECT COUNT(*)
        FROM matching_runs
        WHERE started_at >= TIMESTAMPTZ '2026-04-02T17:00:00Z'
          AND started_at < TIMESTAMPTZ '2026-05-02T17:00:00Z'
    ) AS matching_runs
FROM trips
WHERE deleted_at IS NULL
  AND vehicle_type = 'MOTORBIKE'
  AND requested_at >= TIMESTAMPTZ '2026-04-02T17:00:00Z'
  AND requested_at < TIMESTAMPTZ '2026-05-02T17:00:00Z'

