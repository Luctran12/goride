SELECT
    date_trunc('hour', timezone('Asia/Ho_Chi_Minh', bucket_start)),
    SUM(online_driver_sum),
    SUM(available_driver_sum),
    SUM(busy_driver_sum),
    SUM(observed_buckets)
FROM analytics.mv_supply_hourly
WHERE service_area_id IS NULL
  AND vehicle_type = 'MOTORBIKE'
  AND bucket_start >= TIMESTAMPTZ '2026-04-25T17:00:00Z'
  AND bucket_start < TIMESTAMPTZ '2026-05-02T17:00:00Z'
GROUP BY 1
ORDER BY 1

