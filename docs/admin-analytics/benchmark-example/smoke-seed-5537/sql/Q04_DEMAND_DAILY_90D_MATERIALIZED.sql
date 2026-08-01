SELECT
    date_trunc('day', timezone('Asia/Ho_Chi_Minh', bucket_start)),
    SUM(trip_requests),
    SUM(completed_request_cohort)
FROM analytics.mv_demand_hourly_cell
WHERE service_area_id IS NULL
  AND vehicle_type = 'MOTORBIKE'
  AND bucket_start >= TIMESTAMPTZ '2026-02-01T17:00:00Z'
  AND bucket_start < TIMESTAMPTZ '2026-05-02T17:00:00Z'
GROUP BY 1
ORDER BY 1

