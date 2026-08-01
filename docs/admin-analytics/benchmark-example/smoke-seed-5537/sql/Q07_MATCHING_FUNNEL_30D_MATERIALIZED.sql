SELECT
    SUM(matching_runs),
    SUM(funnel_candidate_found),
    SUM(funnel_offer_sent),
    SUM(funnel_offer_accepted)
FROM analytics.mv_matching_daily
WHERE service_area_id IS NULL
  AND vehicle_type = 'MOTORBIKE'
  AND metric_day >= DATE '2026-04-03'
  AND metric_day < DATE '2026-05-03'

