SELECT
    date_trunc('hour', timezone('Asia/Ho_Chi_Minh', bucket_start)),
    AVG(online_drivers),
    AVG(available_drivers),
    AVG(busy_drivers),
    COUNT(*)
FROM driver_supply_snapshots
WHERE service_area_id IS NULL
  AND vehicle_type = 'MOTORBIKE'
  AND bucket_start >= TIMESTAMPTZ '2026-04-25T17:00:00Z'
  AND bucket_start < TIMESTAMPTZ '2026-05-02T17:00:00Z'
GROUP BY 1
ORDER BY 1

