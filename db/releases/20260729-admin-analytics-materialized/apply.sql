BEGIN;

CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE analytics.materialized_refresh_state (
    id SMALLINT PRIMARY KEY DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'NEVER',
    last_started_at TIMESTAMPTZ,
    last_completed_at TIMESTAMPTZ,
    last_duration_ms BIGINT,
    last_error VARCHAR(1000),
    reporting_timezone VARCHAR(80) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    projected_srid INTEGER NOT NULL DEFAULT 32648,
    base_cell_size_meters INTEGER NOT NULL DEFAULT 250,
    CONSTRAINT chk_materialized_refresh_state_singleton CHECK (id = 1),
    CONSTRAINT chk_materialized_refresh_state_status
        CHECK (status IN ('NEVER', 'RUNNING', 'SUCCESS', 'FAILED')),
    CONSTRAINT chk_materialized_refresh_state_duration
        CHECK (last_duration_ms IS NULL OR last_duration_ms >= 0)
);

INSERT INTO analytics.materialized_refresh_state (id)
VALUES (1)
ON CONFLICT (id) DO NOTHING;

CREATE MATERIALIZED VIEW analytics.mv_trip_daily AS
WITH trip_dimensions AS MATERIALIZED (
    SELECT
        t.id AS trip_id,
        t.vehicle_type,
        t.status,
        t.requested_at,
        t.completed_at,
        t.cancelled_at,
        dimension.service_area_id
    FROM trips t
    CROSS JOIN LATERAL (
        SELECT NULL::BIGINT AS service_area_id
        UNION ALL
        SELECT sa.id
        FROM service_areas sa
        WHERE ST_Covers(sa.boundary, t.pickup_location)
    ) dimension
    WHERE t.deleted_at IS NULL
),
metric_events AS (
    SELECT
        (timezone('Asia/Ho_Chi_Minh', requested_at))::DATE AS metric_day,
        vehicle_type,
        service_area_id,
        1::BIGINT AS trip_requests,
        CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END::BIGINT
            AS completed_request_cohort,
        0::BIGINT AS completed_trips,
        0::BIGINT AS cancelled_trips,
        0::BIGINT AS no_driver_trips,
        0::BIGINT AS completed_payments,
        0::NUMERIC AS completed_revenue
    FROM trip_dimensions

    UNION ALL

    SELECT
        (timezone('Asia/Ho_Chi_Minh', completed_at))::DATE,
        vehicle_type,
        service_area_id,
        0, 0, 1, 0, 0, 0, 0::NUMERIC
    FROM trip_dimensions
    WHERE status = 'COMPLETED'
      AND completed_at IS NOT NULL

    UNION ALL

    SELECT
        (timezone('Asia/Ho_Chi_Minh', cancelled_at))::DATE,
        vehicle_type,
        service_area_id,
        0, 0, 0, 1, 0, 0, 0::NUMERIC
    FROM trip_dimensions
    WHERE status = 'CANCELLED'
      AND cancelled_at IS NOT NULL

    UNION ALL

    SELECT
        (timezone('Asia/Ho_Chi_Minh', history.changed_at))::DATE,
        trip.vehicle_type,
        trip.service_area_id,
        0, 0, 0, 0, 1, 0, 0::NUMERIC
    FROM (
        SELECT DISTINCT
            td.trip_id,
            td.vehicle_type,
            td.service_area_id,
            h.changed_at
        FROM trip_dimensions td
        JOIN trip_status_history h ON h.trip_id = td.trip_id
        WHERE td.status = 'NO_DRIVER'
          AND h.to_status = 'NO_DRIVER'
    ) history
    JOIN trip_dimensions trip
      ON trip.trip_id = history.trip_id
     AND trip.service_area_id IS NOT DISTINCT FROM history.service_area_id

    UNION ALL

    SELECT
        (timezone('Asia/Ho_Chi_Minh', payment.paid_at))::DATE,
        trip.vehicle_type,
        trip.service_area_id,
        0, 0, 0, 0, 0, 1, payment.amount
    FROM payments payment
    JOIN trip_dimensions trip ON trip.trip_id = payment.trip_id
    WHERE payment.status = 'COMPLETED'
      AND payment.paid_at IS NOT NULL
)
SELECT
    metric_day,
    vehicle_type,
    service_area_id,
    SUM(trip_requests)::BIGINT AS trip_requests,
    SUM(completed_request_cohort)::BIGINT AS completed_request_cohort,
    SUM(completed_trips)::BIGINT AS completed_trips,
    SUM(cancelled_trips)::BIGINT AS cancelled_trips,
    SUM(no_driver_trips)::BIGINT AS no_driver_trips,
    SUM(completed_payments)::BIGINT AS completed_payments,
    SUM(completed_revenue)::NUMERIC AS completed_revenue
FROM metric_events
GROUP BY metric_day, vehicle_type, service_area_id
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_trip_daily
    ON analytics.mv_trip_daily (metric_day, vehicle_type, service_area_id)
    NULLS NOT DISTINCT;

CREATE MATERIALIZED VIEW analytics.mv_demand_hourly_cell AS
WITH dimensioned_trips AS (
    SELECT
        t.id,
        t.vehicle_type,
        t.status,
        (
            date_trunc(
                'hour',
                timezone('Asia/Ho_Chi_Minh', t.requested_at)
            ) AT TIME ZONE 'Asia/Ho_Chi_Minh'
        ) AS bucket_start,
        ST_Transform(t.pickup_location, 32648) AS projected_pickup,
        dimension.service_area_id
    FROM trips t
    CROSS JOIN LATERAL (
        SELECT NULL::BIGINT AS service_area_id
        UNION ALL
        SELECT sa.id
        FROM service_areas sa
        WHERE ST_Covers(sa.boundary, t.pickup_location)
    ) dimension
    WHERE t.deleted_at IS NULL
)
SELECT
    bucket_start,
    vehicle_type,
    service_area_id,
    FLOOR(ST_X(projected_pickup) / 250)::BIGINT AS grid_x,
    FLOOR(ST_Y(projected_pickup) / 250)::BIGINT AS grid_y,
    COUNT(*)::BIGINT AS trip_requests,
    COUNT(*) FILTER (WHERE status = 'COMPLETED')::BIGINT
        AS completed_request_cohort
FROM dimensioned_trips
GROUP BY
    bucket_start,
    vehicle_type,
    service_area_id,
    grid_x,
    grid_y
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_demand_hourly_cell
    ON analytics.mv_demand_hourly_cell (
        bucket_start,
        vehicle_type,
        service_area_id,
        grid_x,
        grid_y
    )
    NULLS NOT DISTINCT;

CREATE MATERIALIZED VIEW analytics.mv_supply_hourly AS
SELECT
    (
        date_trunc(
            'hour',
            timezone('Asia/Ho_Chi_Minh', bucket_start)
        ) AT TIME ZONE 'Asia/Ho_Chi_Minh'
    ) AS bucket_start,
    service_area_id,
    vehicle_type,
    SUM(online_drivers)::BIGINT AS online_driver_sum,
    SUM(available_drivers)::BIGINT AS available_driver_sum,
    SUM(busy_drivers)::BIGINT AS busy_driver_sum,
    COUNT(DISTINCT bucket_start)::BIGINT AS observed_buckets
FROM driver_supply_snapshots
GROUP BY 1, service_area_id, vehicle_type
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_supply_hourly
    ON analytics.mv_supply_hourly (
        bucket_start,
        service_area_id,
        vehicle_type
    )
    NULLS NOT DISTINCT;

CREATE MATERIALIZED VIEW analytics.mv_matching_daily AS
WITH run_dimensions AS MATERIALIZED (
    SELECT
        run.id AS run_id,
        run.started_at,
        run.finished_at,
        run.outcome,
        run.search_count,
        run.candidate_count,
        trip.status AS trip_status,
        trip.vehicle_type,
        dimension.service_area_id,
        (
            SELECT COUNT(*)
            FROM matching_offer_events offer
            WHERE offer.matching_run_id = run.id
        )::BIGINT AS actual_offer_count,
        EXISTS (
            SELECT 1
            FROM matching_offer_events offer
            WHERE offer.matching_run_id = run.id
        ) AS has_offer,
        EXISTS (
            SELECT 1
            FROM matching_offer_events offer
            WHERE offer.matching_run_id = run.id
              AND offer.outcome = 'ACCEPTED'
        ) AS has_accepted_offer,
        run.trip_id
    FROM matching_runs run
    JOIN trips trip ON trip.id = run.trip_id
    CROSS JOIN LATERAL (
        SELECT NULL::BIGINT AS service_area_id
        UNION ALL
        SELECT sa.id
        FROM service_areas sa
        WHERE ST_Covers(sa.boundary, trip.pickup_location)
    ) dimension
),
metric_events AS (
    SELECT
        (timezone('Asia/Ho_Chi_Minh', started_at))::DATE AS metric_day,
        vehicle_type,
        service_area_id,
        1::BIGINT AS matching_runs,
        0::BIGINT AS terminal_runs,
        0::BIGINT AS matched_runs,
        0::BIGINT AS no_driver_runs,
        0::BIGINT AS cancelled_runs,
        0::BIGINT AS failed_runs,
        NULL::NUMERIC AS duration_ms,
        0::NUMERIC AS searches_sum,
        0::NUMERIC AS candidates_sum,
        0::NUMERIC AS offers_sum,
        0::BIGINT AS terminal_offers,
        0::BIGINT AS accepted_offers,
        0::BIGINT AS rejected_offers,
        0::BIGINT AS timed_out_offers,
        0::NUMERIC AS candidate_distance_sum,
        0::BIGINT AS candidate_distance_count,
        CASE WHEN candidate_count > 0 THEN 1 ELSE 0 END::BIGINT
            AS funnel_candidate_found,
        CASE WHEN has_offer THEN 1 ELSE 0 END::BIGINT AS funnel_offer_sent,
        CASE WHEN has_accepted_offer THEN 1 ELSE 0 END::BIGINT
            AS funnel_offer_accepted,
        CASE WHEN trip_status = 'COMPLETED' THEN trip_id ELSE NULL END::BIGINT
            AS completed_trip_id
    FROM run_dimensions

    UNION ALL

    SELECT
        (timezone('Asia/Ho_Chi_Minh', finished_at))::DATE,
        vehicle_type,
        service_area_id,
        0,
        1,
        CASE WHEN outcome = 'MATCHED' THEN 1 ELSE 0 END,
        CASE WHEN outcome = 'NO_DRIVER' THEN 1 ELSE 0 END,
        CASE WHEN outcome = 'CANCELLED' THEN 1 ELSE 0 END,
        CASE WHEN outcome = 'FAILED' THEN 1 ELSE 0 END,
        EXTRACT(EPOCH FROM (finished_at - started_at)) * 1000,
        search_count,
        candidate_count,
        actual_offer_count,
        0, 0, 0, 0, 0::NUMERIC, 0, 0, 0, 0, NULL::BIGINT
    FROM run_dimensions
    WHERE outcome IN ('MATCHED', 'NO_DRIVER', 'CANCELLED', 'FAILED')
      AND finished_at IS NOT NULL
      AND finished_at >= started_at

    UNION ALL

    SELECT
        (
            timezone(
                'Asia/Ho_Chi_Minh',
                COALESCE(offer.responded_at, offer.expires_at)
            )
        )::DATE,
        run.vehicle_type,
        run.service_area_id,
        0, 0, 0, 0, 0, 0, NULL::NUMERIC, 0, 0, 0,
        1,
        CASE WHEN offer.outcome = 'ACCEPTED' THEN 1 ELSE 0 END,
        CASE WHEN offer.outcome = 'REJECTED' THEN 1 ELSE 0 END,
        CASE WHEN offer.outcome = 'TIMEOUT' THEN 1 ELSE 0 END,
        0::NUMERIC, 0, 0, 0, 0, NULL::BIGINT
    FROM matching_offer_events offer
    JOIN run_dimensions run ON run.run_id = offer.matching_run_id
    WHERE offer.outcome IN (
        'ACCEPTED',
        'REJECTED',
        'TIMEOUT',
        'CANCELLED',
        'EXPIRED'
    )

    UNION ALL

    SELECT
        (timezone('Asia/Ho_Chi_Minh', offer.offered_at))::DATE,
        run.vehicle_type,
        run.service_area_id,
        0, 0, 0, 0, 0, 0, NULL::NUMERIC, 0, 0, 0,
        0, 0, 0, 0,
        offer.candidate_distance_m::NUMERIC,
        1,
        0, 0, 0, NULL::BIGINT
    FROM matching_offer_events offer
    JOIN run_dimensions run ON run.run_id = offer.matching_run_id
    WHERE offer.candidate_distance_m IS NOT NULL
      AND offer.candidate_distance_m >= 0
)
SELECT
    metric_day,
    vehicle_type,
    service_area_id,
    SUM(matching_runs)::BIGINT AS matching_runs,
    SUM(terminal_runs)::BIGINT AS terminal_runs,
    SUM(matched_runs)::BIGINT AS matched_runs,
    SUM(no_driver_runs)::BIGINT AS no_driver_runs,
    SUM(cancelled_runs)::BIGINT AS cancelled_runs,
    SUM(failed_runs)::BIGINT AS failed_runs,
    COALESCE(
        ARRAY_AGG(duration_ms) FILTER (WHERE duration_ms IS NOT NULL),
        ARRAY[]::NUMERIC[]
    ) AS duration_samples_ms,
    SUM(searches_sum)::NUMERIC AS searches_sum,
    SUM(candidates_sum)::NUMERIC AS candidates_sum,
    SUM(offers_sum)::NUMERIC AS offers_sum,
    SUM(terminal_offers)::BIGINT AS terminal_offers,
    SUM(accepted_offers)::BIGINT AS accepted_offers,
    SUM(rejected_offers)::BIGINT AS rejected_offers,
    SUM(timed_out_offers)::BIGINT AS timed_out_offers,
    SUM(candidate_distance_sum)::NUMERIC AS candidate_distance_sum,
    SUM(candidate_distance_count)::BIGINT AS candidate_distance_count,
    SUM(funnel_candidate_found)::BIGINT AS funnel_candidate_found,
    SUM(funnel_offer_sent)::BIGINT AS funnel_offer_sent,
    SUM(funnel_offer_accepted)::BIGINT AS funnel_offer_accepted,
    COALESCE(
        ARRAY_AGG(completed_trip_id) FILTER (WHERE completed_trip_id IS NOT NULL),
        ARRAY[]::BIGINT[]
    ) AS completed_trip_ids
FROM metric_events
GROUP BY metric_day, vehicle_type, service_area_id
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_matching_daily
    ON analytics.mv_matching_daily (metric_day, vehicle_type, service_area_id)
    NULLS NOT DISTINCT;

COMMIT;
