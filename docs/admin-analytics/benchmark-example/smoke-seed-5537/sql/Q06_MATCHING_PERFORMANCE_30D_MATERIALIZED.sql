WITH selected AS (
    SELECT terminal_runs, matched_runs, duration_samples_ms
    FROM analytics.mv_matching_daily
    WHERE service_area_id IS NULL
      AND vehicle_type = 'MOTORBIKE'
      AND metric_day >= DATE '2026-04-03'
      AND metric_day < DATE '2026-05-03'
),
durations AS (
    SELECT duration
    FROM selected
    CROSS JOIN LATERAL unnest(duration_samples_ms) duration
)
SELECT
    COALESCE((SELECT SUM(terminal_runs) FROM selected), 0),
    COALESCE((SELECT SUM(matched_runs) FROM selected), 0),
    (SELECT AVG(duration) FROM durations),
    (
        SELECT percentile_cont(0.50)
            WITHIN GROUP (ORDER BY duration)
        FROM durations
    ),
    (
        SELECT percentile_cont(0.95)
            WITHIN GROUP (ORDER BY duration)
        FROM durations
    )

