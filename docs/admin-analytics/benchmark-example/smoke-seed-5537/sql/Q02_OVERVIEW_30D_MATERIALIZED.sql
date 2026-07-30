SELECT
    COALESCE(SUM(trip_requests), 0) AS trip_requests,
    COALESCE(SUM(completed_request_cohort), 0)
        AS completed_request_cohort,
    COALESCE(SUM(completed_payments), 0) AS completed_payments,
    COALESCE(SUM(completed_revenue), 0) AS completed_revenue
FROM analytics.mv_trip_daily
WHERE service_area_id IS NULL
  AND vehicle_type = 'MOTORBIKE'
  AND metric_day >= DATE '2026-04-03'
  AND metric_day < DATE '2026-05-03'

