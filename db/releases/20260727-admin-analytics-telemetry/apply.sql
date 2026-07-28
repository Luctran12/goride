BEGIN;

CREATE TABLE matching_runs (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    outcome VARCHAR(30) NOT NULL,
    matched_driver_id BIGINT,
    trigger_type VARCHAR(30) NOT NULL,
    search_count INTEGER NOT NULL DEFAULT 0,
    candidate_count INTEGER NOT NULL DEFAULT 0,
    offer_count INTEGER NOT NULL DEFAULT 0,
    failure_reason_code VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_matching_runs_trip
        FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE RESTRICT,
    CONSTRAINT fk_matching_runs_matched_driver
        FOREIGN KEY (matched_driver_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_matching_runs_outcome
        CHECK (outcome IN ('IN_PROGRESS', 'MATCHED', 'NO_DRIVER', 'CANCELLED', 'FAILED')),
    CONSTRAINT chk_matching_runs_trigger_type
        CHECK (trigger_type IN ('BOOKING_CREATED', 'SCHEDULED_DISPATCH', 'RECOVERY')),
    CONSTRAINT chk_matching_runs_counters
        CHECK (search_count >= 0 AND candidate_count >= 0 AND offer_count >= 0),
    CONSTRAINT chk_matching_runs_finished_time
        CHECK (finished_at IS NULL OR finished_at >= started_at),
    CONSTRAINT chk_matching_runs_state
        CHECK (
            (outcome = 'IN_PROGRESS'
                AND finished_at IS NULL
                AND matched_driver_id IS NULL
                AND failure_reason_code IS NULL)
            OR
            (outcome = 'MATCHED'
                AND finished_at IS NOT NULL
                AND matched_driver_id IS NOT NULL
                AND failure_reason_code IS NULL)
            OR
            (outcome IN ('NO_DRIVER', 'CANCELLED')
                AND finished_at IS NOT NULL
                AND matched_driver_id IS NULL
                AND failure_reason_code IS NULL)
            OR
            (outcome = 'FAILED'
                AND finished_at IS NOT NULL
                AND matched_driver_id IS NULL
                AND failure_reason_code IS NOT NULL
                AND BTRIM(failure_reason_code) <> '')
        )
);

CREATE UNIQUE INDEX uq_matching_runs_one_open_per_trip
    ON matching_runs (trip_id)
    WHERE outcome = 'IN_PROGRESS';

CREATE INDEX idx_matching_runs_trip
    ON matching_runs (trip_id);

CREATE INDEX idx_matching_runs_started_at
    ON matching_runs (started_at);

CREATE INDEX idx_matching_runs_outcome_started_at
    ON matching_runs (outcome, started_at);

CREATE TABLE matching_offer_events (
    id BIGSERIAL PRIMARY KEY,
    matching_run_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    attempt_no INTEGER NOT NULL,
    candidate_rank INTEGER,
    candidate_distance_m DOUBLE PRECISION,
    offered_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ,
    outcome VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_matching_offer_events_run
        FOREIGN KEY (matching_run_id) REFERENCES matching_runs(id) ON DELETE RESTRICT,
    CONSTRAINT fk_matching_offer_events_driver
        FOREIGN KEY (driver_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uq_matching_offer_events_run_attempt
        UNIQUE (matching_run_id, attempt_no),
    CONSTRAINT chk_matching_offer_events_outcome
        CHECK (outcome IN ('OFFERED', 'ACCEPTED', 'REJECTED', 'TIMEOUT', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_matching_offer_events_attempt
        CHECK (attempt_no > 0),
    CONSTRAINT chk_matching_offer_events_candidate_rank
        CHECK (candidate_rank IS NULL OR candidate_rank > 0),
    CONSTRAINT chk_matching_offer_events_candidate_distance
        CHECK (
            candidate_distance_m IS NULL
            OR (
                candidate_distance_m >= 0
                AND candidate_distance_m < 'Infinity'::DOUBLE PRECISION
            )
        ),
    CONSTRAINT chk_matching_offer_events_expiry
        CHECK (expires_at > offered_at),
    CONSTRAINT chk_matching_offer_events_response_time
        CHECK (responded_at IS NULL OR responded_at >= offered_at),
    CONSTRAINT chk_matching_offer_events_state
        CHECK (
            (outcome = 'OFFERED' AND responded_at IS NULL)
            OR
            (outcome IN ('ACCEPTED', 'REJECTED')
                AND responded_at IS NOT NULL
                AND responded_at <= expires_at)
            OR
            (outcome = 'TIMEOUT' AND responded_at IS NULL)
            OR
            (outcome = 'CANCELLED' AND responded_at IS NOT NULL)
            OR
            (outcome = 'EXPIRED'
                AND responded_at IS NOT NULL
                AND responded_at >= expires_at)
        )
);

CREATE INDEX idx_matching_offer_events_driver_offered_at
    ON matching_offer_events (driver_id, offered_at);

CREATE INDEX idx_matching_offer_events_outcome_offered_at
    ON matching_offer_events (outcome, offered_at);

CREATE UNIQUE INDEX uq_matching_offer_events_one_accepted_per_run
    ON matching_offer_events (matching_run_id)
    WHERE outcome = 'ACCEPTED';

CREATE TABLE driver_supply_snapshots (
    id BIGSERIAL PRIMARY KEY,
    bucket_start TIMESTAMPTZ NOT NULL,
    service_area_id BIGINT,
    vehicle_type VARCHAR(20) NOT NULL,
    online_drivers INTEGER NOT NULL,
    available_drivers INTEGER NOT NULL,
    busy_drivers INTEGER NOT NULL,
    sampled_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_driver_supply_snapshots_service_area
        FOREIGN KEY (service_area_id) REFERENCES service_areas(id) ON DELETE RESTRICT,
    CONSTRAINT chk_driver_supply_snapshots_vehicle_type
        CHECK (vehicle_type IN ('MOTORBIKE', 'CAR_4_SEAT', 'CAR_7_SEAT')),
    CONSTRAINT chk_driver_supply_snapshots_counts
        CHECK (
            online_drivers >= 0
            AND available_drivers >= 0
            AND busy_drivers >= 0
            AND available_drivers + busy_drivers <= online_drivers
        ),
    CONSTRAINT chk_driver_supply_snapshots_sample_time
        CHECK (sampled_at >= bucket_start)
);

CREATE UNIQUE INDEX uq_driver_supply_snapshots_bucket_area_vehicle
    ON driver_supply_snapshots (bucket_start, service_area_id, vehicle_type)
    NULLS NOT DISTINCT;

CREATE INDEX idx_driver_supply_snapshots_bucket_vehicle
    ON driver_supply_snapshots (bucket_start, vehicle_type);

CREATE INDEX idx_driver_supply_snapshots_area_bucket
    ON driver_supply_snapshots (service_area_id, bucket_start);

COMMIT;
