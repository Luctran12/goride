SELECT
    COUNT(*) AS terminal_runs,
    COUNT(*) FILTER (WHERE outcome = 'MATCHED') AS matched_runs,
    AVG(EXTRACT(EPOCH FROM (finished_at - started_at)) * 1000),
    percentile_cont(0.50) WITHIN GROUP (
        ORDER BY EXTRACT(EPOCH FROM (finished_at - started_at)) * 1000
    ),
    percentile_cont(0.95) WITHIN GROUP (
        ORDER BY EXTRACT(EPOCH FROM (finished_at - started_at)) * 1000
    )
FROM matching_runs
WHERE outcome IN ('MATCHED', 'NO_DRIVER', 'CANCELLED', 'FAILED')
  AND finished_at >= TIMESTAMPTZ '2026-04-02T17:00:00Z'
  AND finished_at < TIMESTAMPTZ '2026-05-02T17:00:00Z'

