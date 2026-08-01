SELECT
    date_trunc('day', timezone('Asia/Ho_Chi_Minh', requested_at)),
    COUNT(*),
    COUNT(*) FILTER (WHERE status = 'COMPLETED')
FROM trips
WHERE deleted_at IS NULL
  AND vehicle_type = 'MOTORBIKE'
  AND requested_at >= TIMESTAMPTZ '2026-02-01T17:00:00Z'
  AND requested_at < TIMESTAMPTZ '2026-05-02T17:00:00Z'
GROUP BY 1
ORDER BY 1

