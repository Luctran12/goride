SELECT
    FLOOR(ST_X(ST_Transform(pickup_location, 32648)) / 2000)::BIGINT,
    FLOOR(ST_Y(ST_Transform(pickup_location, 32648)) / 2000)::BIGINT,
    COUNT(*)
FROM trips
WHERE deleted_at IS NULL
  AND vehicle_type = 'MOTORBIKE'
  AND requested_at >= TIMESTAMPTZ '2026-04-02T17:00:00Z'
  AND requested_at < TIMESTAMPTZ '2026-05-02T17:00:00Z'
GROUP BY 1, 2
ORDER BY 3 DESC
LIMIT 5001

