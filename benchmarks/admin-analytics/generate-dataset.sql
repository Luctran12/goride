BEGIN;

DO $benchmark_guard$
BEGIN
    IF current_database() !~* '(benchmark|goride_test)' THEN
        RAISE EXCEPTION
            'Admin Analytics benchmark requires a disposable benchmark database';
    END IF;
    IF EXISTS (SELECT 1 FROM users)
       OR EXISTS (SELECT 1 FROM trips)
       OR EXISTS (SELECT 1 FROM matching_runs)
       OR EXISTS (SELECT 1 FROM matching_offer_events)
       OR EXISTS (SELECT 1 FROM driver_supply_snapshots) THEN
        RAISE EXCEPTION
            'Admin Analytics benchmark requires empty application and telemetry tables';
    END IF;
END
$benchmark_guard$;

INSERT INTO service_areas (
    name,
    city_name,
    country_code,
    boundary,
    is_active,
    created_at,
    updated_at
)
VALUES (
    'Benchmark Ho Chi Minh City',
    'Ho Chi Minh City',
    'VN',
    ST_MakeEnvelope(106.35, 10.35, 107.05, 11.20, 4326),
    TRUE,
    TIMESTAMPTZ '2026-01-01 00:00:00+00',
    TIMESTAMPTZ '2026-01-01 00:00:00+00'
);

INSERT INTO users (
    full_name,
    phone,
    email,
    password_hash,
    status,
    created_at,
    updated_at
)
SELECT
    CASE
        WHEN sequence_no <= ${DRIVERS}
            THEN 'Benchmark Driver ' || sequence_no
        ELSE 'Benchmark Passenger ' || (sequence_no - ${DRIVERS})
    END,
    '09' || LPAD(sequence_no::TEXT, 8, '0'),
    CASE
        WHEN sequence_no <= ${DRIVERS}
            THEN 'benchmark-driver-' || sequence_no || '@example.invalid'
        ELSE 'benchmark-passenger-' || (sequence_no - ${DRIVERS})
            || '@example.invalid'
    END,
    'benchmark-not-a-real-password',
    'ACTIVE',
    TIMESTAMPTZ '2026-01-01 00:00:00+00',
    TIMESTAMPTZ '2026-01-01 00:00:00+00'
FROM generate_series(1, ${USERS}) sequence_no;

INSERT INTO user_roles (user_id, role)
SELECT
    id,
    CASE
        WHEN email LIKE 'benchmark-driver-%' THEN 'DRIVER'
        ELSE 'PASSENGER'
    END
FROM users
WHERE email LIKE 'benchmark-%';

INSERT INTO driver_profiles (
    user_id,
    license_number,
    license_expiry,
    id_card_number,
    portrait_url,
    vehicle_plate,
    vehicle_type,
    vehicle_brand,
    vehicle_model,
    vehicle_color,
    vehicle_year,
    approval_status,
    is_online,
    average_rating,
    total_ratings,
    total_trips,
    last_known_location,
    last_location_at,
    created_at,
    updated_at
)
SELECT
    id,
    'BENCH-LICENSE-' || id,
    DATE '2035-01-01',
    'BENCH-ID-' || id,
    'https://example.invalid/benchmark-driver.png',
    'BENCH-' || LPAD(id::TEXT, 6, '0'),
    CASE MOD(ROW_NUMBER() OVER (ORDER BY id) - 1, 10)
        WHEN 8 THEN 'CAR_4_SEAT'
        WHEN 9 THEN 'CAR_7_SEAT'
        ELSE 'MOTORBIKE'
    END,
    'Synthetic',
    'Benchmark',
    'Blue',
    2025,
    'APPROVED',
    FALSE,
    4.8,
    100,
    0,
    NULL,
    NULL,
    TIMESTAMPTZ '2026-01-01 00:00:00+00',
    TIMESTAMPTZ '2026-01-01 00:00:00+00'
FROM users
WHERE email LIKE 'benchmark-driver-%'
ORDER BY id;

WITH
passenger_ids AS (
    SELECT ARRAY_AGG(id ORDER BY id) AS ids
    FROM users
    WHERE email LIKE 'benchmark-passenger-%'
),
driver_ids AS (
    SELECT ARRAY_AGG(id ORDER BY id) AS ids
    FROM users
    WHERE email LIKE 'benchmark-driver-%'
),
pricing AS (
    SELECT
        MAX(id) FILTER (WHERE vehicle_type = 'MOTORBIKE') AS motorbike_id,
        MAX(id) FILTER (WHERE vehicle_type = 'CAR_4_SEAT') AS car4_id,
        MAX(id) FILTER (WHERE vehicle_type = 'CAR_7_SEAT') AS car7_id
    FROM pricing_config
    WHERE is_active
),
generated AS (
    SELECT
        sequence_no,
        MOD(sequence_no * 37 + ${SEED}, 100) AS status_roll,
        MOD(sequence_no * 53 + ${SEED}, 10) AS vehicle_roll,
        MOD(sequence_no * 7919 + ${SEED}, 120) AS day_offset,
        MOD(sequence_no * 97 + ${SEED}, 60) AS minute_offset,
        CASE
            WHEN MOD(sequence_no * 43 + ${SEED}, 10) < 4
                THEN 7 + MOD(sequence_no, 3)
            WHEN MOD(sequence_no * 43 + ${SEED}, 10) < 8
                THEN 16 + MOD(sequence_no, 4)
            ELSE 11 + MOD(sequence_no, 4)
        END AS hour_offset,
        CASE
            WHEN MOD(sequence_no * 29 + ${SEED}, 10) < 7
                THEN 106.6800 + MOD(sequence_no * 17 + ${SEED}, 200)::NUMERIC / 100000
            WHEN MOD(sequence_no * 29 + ${SEED}, 10) < 9
                THEN 106.7200 + MOD(sequence_no * 19 + ${SEED}, 300)::NUMERIC / 100000
            ELSE 106.4000 + MOD(sequence_no * 23 + ${SEED}, 5000)::NUMERIC / 10000
        END AS pickup_lng,
        CASE
            WHEN MOD(sequence_no * 29 + ${SEED}, 10) < 7
                THEN 10.7600 + MOD(sequence_no * 31 + ${SEED}, 200)::NUMERIC / 100000
            WHEN MOD(sequence_no * 29 + ${SEED}, 10) < 9
                THEN 10.8000 + MOD(sequence_no * 13 + ${SEED}, 300)::NUMERIC / 100000
            ELSE 10.4000 + MOD(sequence_no * 11 + ${SEED}, 6000)::NUMERIC / 10000
        END AS pickup_lat
    FROM generate_series(1, ${TRIPS}) sequence_no
),
normalized AS (
    SELECT
        generated.*,
        CASE
            WHEN vehicle_roll < 7 THEN 'MOTORBIKE'
            WHEN vehicle_roll < 9 THEN 'CAR_4_SEAT'
            ELSE 'CAR_7_SEAT'
        END AS vehicle_type,
        1 + ((${MATCHING_OFFERS} - sequence_no) / ${MATCHING_RUNS})
            AS matching_attempts,
        CASE
            WHEN sequence_no = 2
                THEN TIMESTAMP '2026-02-01 00:00:00'
                    AT TIME ZONE 'Asia/Ho_Chi_Minh'
            ELSE (
                TIMESTAMP '2026-01-02 00:00:00'
                    + day_offset * INTERVAL '1 day'
                    + hour_offset * INTERVAL '1 hour'
                    + minute_offset * INTERVAL '1 minute'
            ) AT TIME ZONE 'Asia/Ho_Chi_Minh'
        END AS requested_at
    FROM generated
)
INSERT INTO trips (
    passenger_id,
    driver_id,
    status,
    vehicle_type,
    payment_method,
    pickup_address,
    pickup_location,
    dropoff_address,
    dropoff_location,
    estimated_distance_km,
    estimated_duration_min,
    actual_distance_km,
    actual_duration_min,
    estimated_fare,
    final_fare,
    pricing_config_id,
    fare_surge_multiplier,
    requested_at,
    accepted_at,
    arrived_at,
    started_at,
    completed_at,
    cancelled_at,
    cancel_reason,
    created_at,
    updated_at
)
SELECT
    passenger_ids.ids[
        1 + MOD(normalized.sequence_no * 17 + ${SEED}, CARDINALITY(passenger_ids.ids))
    ],
    CASE
        WHEN normalized.status_roll < 80
            THEN driver_ids.ids[
                1 + MOD(normalized.sequence_no * 13 + ${SEED}, CARDINALITY(driver_ids.ids))
            ]
        ELSE NULL
    END,
    CASE
        WHEN normalized.status_roll < 80 THEN 'COMPLETED'
        WHEN normalized.status_roll < 88 THEN 'CANCELLED'
        WHEN normalized.status_roll < 94 THEN 'NO_DRIVER'
        ELSE 'SEARCHING'
    END,
    normalized.vehicle_type,
    CASE WHEN MOD(normalized.sequence_no + ${SEED}, 4) = 0 THEN 'CASH' ELSE 'VNPAY' END,
    'Synthetic benchmark pickup',
    CASE
        WHEN normalized.sequence_no = 1
            THEN ST_Transform(
                ST_SetSRID(ST_MakePoint(685000, 1190000), 32648),
                4326
            )
        ELSE ST_SetSRID(
            ST_MakePoint(normalized.pickup_lng, normalized.pickup_lat),
            4326
        )
    END,
    'Synthetic benchmark dropoff',
    ST_SetSRID(
        ST_MakePoint(normalized.pickup_lng + 0.015, normalized.pickup_lat + 0.010),
        4326
    ),
    3.00 + MOD(normalized.sequence_no * 7 + ${SEED}, 1200)::NUMERIC / 100,
    10 + MOD(normalized.sequence_no * 11 + ${SEED}, 50),
    CASE
        WHEN normalized.status_roll < 80
            THEN 3.00 + MOD(normalized.sequence_no * 7 + ${SEED}, 1200)::NUMERIC / 100
        ELSE NULL
    END,
    CASE
        WHEN normalized.status_roll < 80
            THEN 10 + MOD(normalized.sequence_no * 11 + ${SEED}, 50)
        ELSE NULL
    END,
    20000 + MOD(normalized.sequence_no * 101 + ${SEED}, 100000),
    CASE
        WHEN normalized.status_roll < 80
            THEN 20000 + MOD(normalized.sequence_no * 101 + ${SEED}, 100000)
        ELSE NULL
    END,
    CASE normalized.vehicle_type
        WHEN 'MOTORBIKE' THEN pricing.motorbike_id
        WHEN 'CAR_4_SEAT' THEN pricing.car4_id
        ELSE pricing.car7_id
    END,
    1.00,
    normalized.requested_at,
    CASE
        WHEN normalized.status_roll < 80
            THEN normalized.requested_at
                + INTERVAL '30 seconds'
                + normalized.matching_attempts * INTERVAL '40 seconds'
                + INTERVAL '10 seconds'
    END,
    CASE
        WHEN normalized.status_roll < 80
            THEN normalized.requested_at
                + INTERVAL '150 seconds'
                + normalized.matching_attempts * INTERVAL '40 seconds'
    END,
    CASE
        WHEN normalized.status_roll < 80
            THEN normalized.requested_at
                + INTERVAL '270 seconds'
                + normalized.matching_attempts * INTERVAL '40 seconds'
    END,
    CASE
        WHEN normalized.status_roll < 80
            THEN normalized.requested_at
                + INTERVAL '270 seconds'
                + normalized.matching_attempts * INTERVAL '40 seconds'
                + (10 + MOD(normalized.sequence_no * 11 + ${SEED}, 50))
                    * INTERVAL '1 minute'
    END,
    CASE
        WHEN normalized.status_roll >= 80 AND normalized.status_roll < 88
            THEN normalized.requested_at + INTERVAL '4 minutes'
    END,
    CASE
        WHEN normalized.status_roll >= 80 AND normalized.status_roll < 88
            THEN 'Synthetic cancellation'
    END,
    normalized.requested_at,
    normalized.requested_at
FROM normalized
CROSS JOIN passenger_ids
CROSS JOIN driver_ids
CROSS JOIN pricing;

INSERT INTO trip_status_history (
    trip_id,
    from_status,
    to_status,
    changed_by,
    note,
    changed_at
)
SELECT
    trip.id,
    transition.from_status,
    transition.to_status,
    NULL,
    'Synthetic benchmark transition',
    CASE transition.to_status
        WHEN 'SEARCHING' THEN trip.requested_at
        WHEN 'ACCEPTED' THEN trip.accepted_at
        WHEN 'ARRIVED' THEN trip.arrived_at
        WHEN 'IN_PROGRESS' THEN trip.started_at
        WHEN 'COMPLETED' THEN trip.completed_at
        WHEN 'CANCELLED' THEN trip.cancelled_at
        WHEN 'NO_DRIVER' THEN trip.requested_at + INTERVAL '3 minutes'
    END
FROM trips trip
CROSS JOIN LATERAL (
    SELECT *
    FROM (
        VALUES
            (NULL::VARCHAR, 'SEARCHING'::VARCHAR),
            ('SEARCHING'::VARCHAR,
                CASE
                    WHEN trip.status = 'COMPLETED' THEN 'ACCEPTED'
                    WHEN trip.status = 'CANCELLED' THEN 'CANCELLED'
                    WHEN trip.status = 'NO_DRIVER' THEN 'NO_DRIVER'
                    ELSE NULL
                END),
            ('ACCEPTED'::VARCHAR,
                CASE WHEN trip.status = 'COMPLETED' THEN 'ARRIVED' END),
            ('ARRIVED'::VARCHAR,
                CASE WHEN trip.status = 'COMPLETED' THEN 'IN_PROGRESS' END),
            ('IN_PROGRESS'::VARCHAR,
                CASE WHEN trip.status = 'COMPLETED' THEN 'COMPLETED' END)
    ) transition(from_status, to_status)
    WHERE transition.to_status IS NOT NULL
) transition;

WITH
driver_ids AS (
    SELECT ARRAY_AGG(id ORDER BY id) AS ids
    FROM users
    WHERE email LIKE 'benchmark-driver-%'
),
selected_trips AS (
    SELECT
        id,
        requested_at,
        accepted_at,
        driver_id,
        status,
        ROW_NUMBER() OVER (ORDER BY id) AS sequence_no
    FROM trips
    ORDER BY id
    LIMIT ${MATCHING_RUNS}
)
INSERT INTO matching_runs (
    trip_id,
    started_at,
    finished_at,
    outcome,
    matched_driver_id,
    trigger_type,
    search_count,
    candidate_count,
    offer_count,
    failure_reason_code,
    created_at,
    updated_at
)
SELECT
    selected_trips.id,
    selected_trips.requested_at + INTERVAL '30 seconds',
    CASE
        WHEN selected_trips.status = 'COMPLETED'
            THEN selected_trips.accepted_at
        WHEN selected_trips.status <> 'SEARCHING'
          OR MOD(selected_trips.sequence_no + ${SEED}, 2) = 0
            THEN selected_trips.requested_at
                + INTERVAL '60 seconds'
                + (
                    1 + (
                        (${MATCHING_OFFERS} - selected_trips.sequence_no)
                        / ${MATCHING_RUNS}
                    )
                ) * INTERVAL '40 seconds'
        ELSE NULL
    END,
    CASE
        WHEN selected_trips.status = 'COMPLETED' THEN 'MATCHED'
        WHEN selected_trips.status = 'NO_DRIVER' THEN 'NO_DRIVER'
        WHEN selected_trips.status = 'CANCELLED' THEN 'CANCELLED'
        WHEN MOD(selected_trips.sequence_no + ${SEED}, 2) = 0 THEN 'FAILED'
        ELSE 'IN_PROGRESS'
    END,
    CASE
        WHEN selected_trips.status = 'COMPLETED'
            THEN selected_trips.driver_id
        ELSE NULL
    END,
    CASE MOD(selected_trips.sequence_no + ${SEED}, 3)
        WHEN 0 THEN 'BOOKING_CREATED'
        WHEN 1 THEN 'SCHEDULED_DISPATCH'
        ELSE 'RECOVERY'
    END,
    1 + MOD(selected_trips.sequence_no * 3 + ${SEED}, 4),
    CASE
        WHEN selected_trips.status = 'COMPLETED'
            THEN 1 + MOD(selected_trips.sequence_no * 7 + ${SEED}, 12)
        ELSE MOD(selected_trips.sequence_no * 7 + ${SEED}, 12)
    END,
    0,
    CASE
        WHEN selected_trips.status = 'SEARCHING'
         AND MOD(selected_trips.sequence_no + ${SEED}, 2) = 0
            THEN 'SYNTHETIC_FAILURE'
    END,
    selected_trips.requested_at,
    selected_trips.requested_at
FROM selected_trips
CROSS JOIN driver_ids;

WITH
runs AS (
    SELECT
        ARRAY_AGG(id ORDER BY id) AS ids,
        COUNT(*)::INTEGER AS run_count
    FROM matching_runs
),
drivers AS (
    SELECT ARRAY_AGG(id ORDER BY id) AS ids
    FROM users
    WHERE email LIKE 'benchmark-driver-%'
),
generated AS (
    SELECT
        offer_no,
        1 + MOD(offer_no - 1, runs.run_count) AS run_position,
        1 + ((offer_no - 1) / runs.run_count) AS attempt_no,
        runs.ids[1 + MOD(offer_no - 1, runs.run_count)] AS run_id,
        runs.run_count
    FROM generate_series(1, ${MATCHING_OFFERS}) offer_no
    CROSS JOIN runs
),
enriched AS (
    SELECT
        generated.*,
        matching_runs.outcome AS run_outcome,
        matching_runs.matched_driver_id,
        matching_runs.started_at,
        1 + ((${MATCHING_OFFERS} - generated.run_position) / generated.run_count)
            AS max_attempt
    FROM generated
    JOIN matching_runs ON matching_runs.id = generated.run_id
)
INSERT INTO matching_offer_events (
    matching_run_id,
    driver_id,
    attempt_no,
    candidate_rank,
    candidate_distance_m,
    offered_at,
    expires_at,
    responded_at,
    outcome,
    created_at,
    updated_at
)
SELECT
    enriched.run_id,
    CASE
        WHEN enriched.run_outcome = 'MATCHED'
         AND enriched.attempt_no = enriched.max_attempt
            THEN enriched.matched_driver_id
        ELSE drivers.ids[
            1 + MOD(enriched.run_position * 19 + enriched.attempt_no + ${SEED},
                CARDINALITY(drivers.ids))
        ]
    END,
    enriched.attempt_no,
    enriched.attempt_no,
    150 + MOD(
        enriched.run_position * 31 + enriched.attempt_no * 17 + ${SEED},
        4850
    ),
    enriched.started_at + enriched.attempt_no * INTERVAL '40 seconds',
    enriched.started_at + enriched.attempt_no * INTERVAL '40 seconds'
        + INTERVAL '30 seconds',
    CASE
        WHEN enriched.run_outcome = 'MATCHED'
         AND enriched.attempt_no = enriched.max_attempt
            THEN enriched.started_at + enriched.attempt_no * INTERVAL '40 seconds'
                + INTERVAL '10 seconds'
        WHEN enriched.run_outcome = 'IN_PROGRESS'
         AND enriched.attempt_no = enriched.max_attempt
            THEN NULL
        WHEN MOD(enriched.attempt_no + enriched.run_position + ${SEED}, 2) = 0
            THEN enriched.started_at + enriched.attempt_no * INTERVAL '40 seconds'
                + INTERVAL '10 seconds'
        ELSE NULL
    END,
    CASE
        WHEN enriched.run_outcome = 'MATCHED'
         AND enriched.attempt_no = enriched.max_attempt THEN 'ACCEPTED'
        WHEN enriched.run_outcome = 'IN_PROGRESS'
         AND enriched.attempt_no = enriched.max_attempt THEN 'OFFERED'
        WHEN MOD(enriched.attempt_no + enriched.run_position + ${SEED}, 2) = 0
            THEN 'REJECTED'
        ELSE 'TIMEOUT'
    END,
    enriched.started_at,
    enriched.started_at + enriched.attempt_no * INTERVAL '40 seconds'
        + INTERVAL '30 seconds'
FROM enriched
CROSS JOIN drivers;

UPDATE matching_runs run
SET offer_count = offer_totals.offer_count
FROM (
    SELECT matching_run_id, COUNT(*)::INTEGER AS offer_count
    FROM matching_offer_events
    GROUP BY matching_run_id
) offer_totals
WHERE run.id = offer_totals.matching_run_id;

INSERT INTO payments (
    trip_id,
    amount,
    method,
    provider,
    status,
    transaction_ref,
    paid_at,
    created_at,
    updated_at
)
SELECT
    trip.id,
    trip.final_fare,
    trip.payment_method,
    CASE WHEN trip.payment_method = 'CASH' THEN NULL ELSE 'VNPAY' END,
    CASE
        WHEN MOD(ROW_NUMBER() OVER (ORDER BY trip.id) + ${SEED}, 20) = 0 THEN 'FAILED'
        WHEN MOD(ROW_NUMBER() OVER (ORDER BY trip.id) + ${SEED}, 20) = 1 THEN 'PENDING'
        ELSE 'COMPLETED'
    END,
    CASE
        WHEN trip.payment_method = 'CASH' THEN NULL
        ELSE 'BENCH-' || trip.id
    END,
    CASE
        WHEN MOD(ROW_NUMBER() OVER (ORDER BY trip.id) + ${SEED}, 20) NOT IN (0, 1)
            THEN trip.completed_at + INTERVAL '1 minute'
    END,
    trip.completed_at,
    trip.completed_at
FROM trips trip
WHERE trip.status = 'COMPLETED'
ORDER BY trip.id
LIMIT ${PAYMENTS};

WITH generated AS (
    SELECT
        snapshot_no,
        ((snapshot_no - 1) / 3) AS ordinal,
        MOD(snapshot_no - 1, 3) AS vehicle_no,
        CEIL(${SUPPLY_SNAPSHOTS}::NUMERIC / 3)::BIGINT AS samples_per_vehicle
    FROM generate_series(1, ${SUPPLY_SNAPSHOTS}) snapshot_no
),
counts AS (
    SELECT
        generated.*,
        20 + MOD(generated.snapshot_no * 13 + ${SEED}, 35) AS available_drivers,
        10 + MOD(generated.snapshot_no * 7 + ${SEED}, 20) AS busy_drivers
    FROM generated
)
INSERT INTO driver_supply_snapshots (
    bucket_start,
    service_area_id,
    vehicle_type,
    online_drivers,
    available_drivers,
    busy_drivers,
    sampled_at
)
SELECT
    TIMESTAMPTZ '2026-01-02 00:00:00+00'
        + FLOOR(counts.ordinal * 34560::NUMERIC / counts.samples_per_vehicle)
            * INTERVAL '5 minutes',
    NULL,
    CASE counts.vehicle_no
        WHEN 0 THEN 'MOTORBIKE'
        WHEN 1 THEN 'CAR_4_SEAT'
        ELSE 'CAR_7_SEAT'
    END,
    counts.available_drivers
        + counts.busy_drivers
        + 10
        + MOD(counts.snapshot_no * 17 + ${SEED}, 30),
    counts.available_drivers,
    counts.busy_drivers,
    TIMESTAMPTZ '2026-01-02 00:00:00+00'
        + FLOOR(counts.ordinal * 34560::NUMERIC / counts.samples_per_vehicle)
            * INTERVAL '5 minutes'
        + INTERVAL '10 seconds'
FROM counts;

WITH completed_trips AS (
    SELECT
        ARRAY_AGG(id ORDER BY id) AS ids,
        COUNT(*)::INTEGER AS trip_count
    FROM trips
    WHERE status = 'COMPLETED'
),
generated AS (
    SELECT
        location_no,
        1 + MOD(location_no - 1, completed_trips.trip_count) AS trip_position,
        1 + ((location_no - 1) / completed_trips.trip_count) AS point_no,
        completed_trips.ids[
            1 + MOD(location_no - 1, completed_trips.trip_count)
        ] AS trip_id
    FROM generate_series(1, ${LOCATION_POINTS}) location_no
    CROSS JOIN completed_trips
)
INSERT INTO trip_location_history (
    trip_id,
    location,
    bearing,
    speed,
    recorded_at
)
SELECT
    generated.trip_id,
    ST_LineInterpolatePoint(
        ST_MakeLine(trip.pickup_location, trip.dropoff_location),
        LEAST(1.0, (generated.point_no - 1)::DOUBLE PRECISION / 30.0)
    ),
    MOD(generated.location_no * 17 + ${SEED}, 360),
    15 + MOD(generated.location_no * 11 + ${SEED}, 35),
    trip.started_at + generated.point_no * INTERVAL '30 seconds'
FROM generated
JOIN trips trip ON trip.id = generated.trip_id;

ANALYZE users;
ANALYZE trips;
ANALYZE trip_status_history;
ANALYZE payments;
ANALYZE matching_runs;
ANALYZE matching_offer_events;
ANALYZE driver_supply_snapshots;
ANALYZE trip_location_history;

COMMIT;
