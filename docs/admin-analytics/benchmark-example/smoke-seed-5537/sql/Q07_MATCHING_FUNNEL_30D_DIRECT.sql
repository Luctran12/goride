SELECT
    COUNT(*) AS run_started,
    COUNT(*) FILTER (WHERE candidate_count > 0) AS candidate_found,
    COUNT(*) FILTER (
        WHERE EXISTS (
            SELECT 1
            FROM matching_offer_events offer
            WHERE offer.matching_run_id = matching_runs.id
        )
    ) AS offer_sent
FROM matching_runs
WHERE started_at >= TIMESTAMPTZ '2026-04-02T17:00:00Z'
  AND started_at < TIMESTAMPTZ '2026-05-02T17:00:00Z'

