SELECT
    FLOOR(grid_x::NUMERIC / 1)::BIGINT,
    FLOOR(grid_y::NUMERIC / 1)::BIGINT,
    SUM(trip_requests)
FROM analytics.mv_demand_hourly_cell
WHERE service_area_id IS NULL
  AND vehicle_type = 'MOTORBIKE'
  AND bucket_start >= TIMESTAMPTZ '2026-04-25T17:00:00Z'
  AND bucket_start < TIMESTAMPTZ '2026-05-02T17:00:00Z'
GROUP BY 1, 2
ORDER BY 3 DESC
LIMIT 5001

