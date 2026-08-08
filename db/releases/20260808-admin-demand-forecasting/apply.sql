BEGIN;

CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE analytics.processing_runs (
    run_id UUID PRIMARY KEY,
    artifact_run_id VARCHAR(200) NOT NULL,
    run_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_profile VARCHAR(80) NOT NULL,
    dataset_version VARCHAR(120) NOT NULL,
    source_cutoff TIMESTAMPTZ NOT NULL,
    config_hash VARCHAR(64) NOT NULL,
    code_commit VARCHAR(64) NOT NULL,
    input_manifest JSONB NOT NULL,
    attempt_no SMALLINT NOT NULL DEFAULT 1,
    rows_read BIGINT NOT NULL DEFAULT 0,
    rows_written BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error_code VARCHAR(80),
    error_message VARCHAR(2000),
    CONSTRAINT uq_processing_runs_artifact UNIQUE (artifact_run_id),
    CONSTRAINT uq_processing_runs_forecast_contract UNIQUE (
        run_id,
        run_type,
        source_profile,
        dataset_version,
        source_cutoff,
        config_hash
    ),
    CONSTRAINT chk_processing_runs_run_type CHECK (
        run_type IN (
            'EXTRACTION',
            'QUALITY',
            'FEATURE_BUILD',
            'TRAINING',
            'EVALUATION',
            'FORECAST',
            'ACTUAL_BACKFILL'
        )
    ),
    CONSTRAINT chk_processing_runs_status CHECK (
        status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT chk_processing_runs_identity CHECK (
        BTRIM(artifact_run_id) <> ''
        AND BTRIM(source_profile) <> ''
        AND BTRIM(dataset_version) <> ''
        AND config_hash ~ '^[0-9a-f]{64}$'
        AND code_commit ~ '^([0-9a-f]{40}|[0-9a-f]{64})$'
    ),
    CONSTRAINT chk_processing_runs_manifest CHECK (
        jsonb_typeof(input_manifest) = 'object'
    ),
    CONSTRAINT chk_processing_runs_attempt CHECK (attempt_no > 0),
    CONSTRAINT chk_processing_runs_counts CHECK (
        rows_read >= 0 AND rows_written >= 0
    ),
    CONSTRAINT chk_processing_runs_cutoff CHECK (source_cutoff <= created_at),
    CONSTRAINT chk_processing_runs_time_order CHECK (
        (started_at IS NULL OR started_at >= created_at)
        AND (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
    ),
    CONSTRAINT chk_processing_runs_state CHECK (
        (
            status = 'PENDING'
            AND started_at IS NULL
            AND finished_at IS NULL
            AND error_code IS NULL
            AND error_message IS NULL
        )
        OR (
            status = 'RUNNING'
            AND started_at IS NOT NULL
            AND finished_at IS NULL
            AND error_code IS NULL
            AND error_message IS NULL
        )
        OR (
            status = 'SUCCEEDED'
            AND started_at IS NOT NULL
            AND finished_at IS NOT NULL
            AND error_code IS NULL
            AND error_message IS NULL
        )
        OR (
            status = 'FAILED'
            AND started_at IS NOT NULL
            AND finished_at IS NOT NULL
            AND error_code IS NOT NULL
            AND error_message IS NOT NULL
            AND BTRIM(error_code) <> ''
            AND BTRIM(error_message) <> ''
        )
        OR (
            status = 'CANCELLED'
            AND finished_at IS NOT NULL
            AND error_code IS NULL
            AND error_message IS NULL
        )
    )
);

CREATE UNIQUE INDEX uq_processing_runs_idempotency
    ON analytics.processing_runs (
        run_type,
        source_profile,
        dataset_version,
        config_hash,
        source_cutoff,
        attempt_no
    );

CREATE INDEX idx_processing_runs_status_created
    ON analytics.processing_runs (status, created_at DESC);

CREATE INDEX idx_processing_runs_profile_cutoff
    ON analytics.processing_runs (source_profile, source_cutoff DESC);

CREATE TABLE analytics.data_quality_results (
    data_quality_result_id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL,
    rule_code VARCHAR(80) NOT NULL,
    scope_key VARCHAR(200) NOT NULL DEFAULT 'GLOBAL',
    severity VARCHAR(10) NOT NULL,
    result_status VARCHAR(10) NOT NULL,
    records_checked BIGINT NOT NULL DEFAULT 0,
    records_breached BIGINT NOT NULL DEFAULT 0,
    metric_value NUMERIC(24, 8),
    threshold JSONB NOT NULL DEFAULT '{}'::JSONB,
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    evaluated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_data_quality_results_run FOREIGN KEY (run_id)
        REFERENCES analytics.processing_runs(run_id) ON DELETE RESTRICT,
    CONSTRAINT uq_data_quality_results_scope
        UNIQUE (run_id, rule_code, scope_key),
    CONSTRAINT chk_data_quality_results_identity CHECK (
        BTRIM(rule_code) <> '' AND BTRIM(scope_key) <> ''
    ),
    CONSTRAINT chk_data_quality_results_severity CHECK (
        severity IN ('WARN', 'FAIL')
    ),
    CONSTRAINT chk_data_quality_results_status CHECK (
        result_status IN ('PASS', 'WARN', 'FAIL')
    ),
    CONSTRAINT chk_data_quality_results_outcome CHECK (
        result_status = 'PASS'
        OR (result_status = 'WARN' AND severity = 'WARN')
        OR (result_status = 'FAIL' AND severity = 'FAIL')
    ),
    CONSTRAINT chk_data_quality_results_counts CHECK (
        records_checked >= 0
        AND records_breached >= 0
        AND records_breached <= records_checked
    ),
    CONSTRAINT chk_data_quality_results_json CHECK (
        jsonb_typeof(threshold) = 'object'
        AND jsonb_typeof(details) = 'object'
    )
);

CREATE INDEX idx_data_quality_results_run_status
    ON analytics.data_quality_results (run_id, result_status);

CREATE TABLE analytics.demand_features (
    demand_feature_id BIGSERIAL PRIMARY KEY,
    feature_set_version VARCHAR(80) NOT NULL,
    source_profile VARCHAR(80) NOT NULL,
    dataset_version VARCHAR(120) NOT NULL,
    demand_event_semantics VARCHAR(30) NOT NULL,
    grid_version VARCHAR(80) NOT NULL,
    projected_srid INTEGER NOT NULL,
    cell_id VARCHAR(180) NOT NULL,
    grid_x BIGINT NOT NULL,
    grid_y BIGINT NOT NULL,
    cell_size_meters INTEGER NOT NULL,
    bucket_start_utc TIMESTAMPTZ NOT NULL,
    inference_cutoff_utc TIMESTAMPTZ NOT NULL,
    target_bucket_start_utc TIMESTAMPTZ NOT NULL,
    horizon_minutes SMALLINT NOT NULL,
    target_trip_requests INTEGER,
    lag_1 INTEGER,
    lag_2 INTEGER,
    lag_4 INTEGER,
    lag_96 INTEGER,
    lag_672 INTEGER,
    rolling_mean_4 NUMERIC(18, 6),
    rolling_mean_12 NUMERIC(18, 6),
    rolling_mean_96 NUMERIC(18, 6),
    rolling_mean_672 NUMERIC(18, 6),
    hour_sin NUMERIC(9, 8) NOT NULL,
    hour_cos NUMERIC(9, 8) NOT NULL,
    day_of_week SMALLINT NOT NULL,
    is_weekend BOOLEAN NOT NULL,
    neighbor_demand_lag_1 INTEGER,
    available_driver_lag_1 INTEGER,
    coverage_ratio NUMERIC(8, 6) NOT NULL,
    quality_status VARCHAR(10) NOT NULL,
    created_by_run_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_demand_features_run FOREIGN KEY (created_by_run_id)
        REFERENCES analytics.processing_runs(run_id) ON DELETE RESTRICT,
    CONSTRAINT uq_demand_features_identity UNIQUE (
        feature_set_version,
        source_profile,
        dataset_version,
        grid_version,
        cell_id,
        bucket_start_utc,
        inference_cutoff_utc,
        horizon_minutes
    ),
    CONSTRAINT chk_demand_features_identity CHECK (
        BTRIM(feature_set_version) <> ''
        AND BTRIM(source_profile) <> ''
        AND BTRIM(dataset_version) <> ''
        AND BTRIM(grid_version) <> ''
        AND BTRIM(cell_id) <> ''
        AND cell_id = CONCAT(
            grid_version,
            ':',
            projected_srid,
            ':',
            cell_size_meters,
            ':',
            grid_x,
            ':',
            grid_y
        )
    ),
    CONSTRAINT chk_demand_features_semantics CHECK (
        demand_event_semantics IN ('REQUEST_CREATED', 'TRIP_STARTED_PROXY')
    ),
    CONSTRAINT chk_demand_features_spatial CHECK (
        projected_srid IN (3763, 32648)
        AND cell_size_meters IN (250, 500, 1000, 2000)
    ),
    CONSTRAINT chk_demand_features_alignment CHECK (
        MOD(EXTRACT(EPOCH FROM bucket_start_utc)::BIGINT, 900) = 0
        AND MOD(EXTRACT(EPOCH FROM inference_cutoff_utc)::BIGINT, 900) = 0
        AND MOD(EXTRACT(EPOCH FROM target_bucket_start_utc)::BIGINT, 900) = 0
        AND horizon_minutes IN (15, 30, 60)
        AND bucket_start_utc <= inference_cutoff_utc
        AND target_bucket_start_utc =
            inference_cutoff_utc + horizon_minutes * INTERVAL '1 minute'
    ),
    CONSTRAINT chk_demand_features_nonnegative CHECK (
        (target_trip_requests IS NULL OR target_trip_requests >= 0)
        AND (lag_1 IS NULL OR lag_1 >= 0)
        AND (lag_2 IS NULL OR lag_2 >= 0)
        AND (lag_4 IS NULL OR lag_4 >= 0)
        AND (lag_96 IS NULL OR lag_96 >= 0)
        AND (lag_672 IS NULL OR lag_672 >= 0)
        AND (rolling_mean_4 IS NULL OR rolling_mean_4 >= 0)
        AND (rolling_mean_12 IS NULL OR rolling_mean_12 >= 0)
        AND (rolling_mean_96 IS NULL OR rolling_mean_96 >= 0)
        AND (rolling_mean_672 IS NULL OR rolling_mean_672 >= 0)
        AND (neighbor_demand_lag_1 IS NULL OR neighbor_demand_lag_1 >= 0)
        AND (available_driver_lag_1 IS NULL OR available_driver_lag_1 >= 0)
    ),
    CONSTRAINT chk_demand_features_calendar CHECK (
        hour_sin BETWEEN -1 AND 1
        AND hour_cos BETWEEN -1 AND 1
        AND day_of_week BETWEEN 0 AND 6
        AND is_weekend = (day_of_week IN (5, 6))
    ),
    CONSTRAINT chk_demand_features_coverage CHECK (
        coverage_ratio BETWEEN 0 AND 1
        AND quality_status IN ('PASS', 'WARN', 'FAIL')
    ),
    CONSTRAINT chk_demand_features_label_time CHECK (
        target_trip_requests IS NULL
        OR created_at >= target_bucket_start_utc + INTERVAL '15 minutes'
    )
);

CREATE INDEX idx_demand_features_training_lookup
    ON analytics.demand_features (
        source_profile,
        feature_set_version,
        cell_size_meters,
        target_bucket_start_utc
    );

CREATE INDEX idx_demand_features_target_brin
    ON analytics.demand_features USING BRIN (target_bucket_start_utc);

CREATE TABLE analytics.model_versions (
    model_version_id UUID PRIMARY KEY,
    model_name VARCHAR(120) NOT NULL,
    model_version VARCHAR(80) NOT NULL,
    model_family VARCHAR(40) NOT NULL,
    lifecycle_status VARCHAR(20) NOT NULL,
    source_profile VARCHAR(80) NOT NULL,
    dataset_version VARCHAR(120) NOT NULL,
    demand_event_semantics VARCHAR(30) NOT NULL,
    feature_set_version VARCHAR(80) NOT NULL,
    grid_version VARCHAR(80) NOT NULL,
    cell_size_meters INTEGER NOT NULL,
    bucket_minutes SMALLINT NOT NULL,
    training_cutoff_utc TIMESTAMPTZ NOT NULL,
    training_run_id UUID NOT NULL,
    artifact_uri VARCHAR(500) NOT NULL,
    artifact_sha256 VARCHAR(64) NOT NULL,
    hyperparameters JSONB NOT NULL,
    training_manifest JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    validated_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    CONSTRAINT fk_model_versions_training_run FOREIGN KEY (training_run_id)
        REFERENCES analytics.processing_runs(run_id) ON DELETE RESTRICT,
    CONSTRAINT uq_model_versions_name_version
        UNIQUE (model_name, model_version),
    CONSTRAINT uq_model_versions_compatibility UNIQUE (
        model_version_id,
        source_profile,
        dataset_version,
        demand_event_semantics,
        grid_version,
        cell_size_meters,
        bucket_minutes
    ),
    CONSTRAINT chk_model_versions_identity CHECK (
        BTRIM(model_name) <> ''
        AND BTRIM(model_version) <> ''
        AND BTRIM(source_profile) <> ''
        AND BTRIM(dataset_version) <> ''
        AND BTRIM(feature_set_version) <> ''
        AND BTRIM(grid_version) <> ''
        AND BTRIM(artifact_uri) <> ''
        AND artifact_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_model_versions_family CHECK (
        model_family IN (
            'HISTORICAL_MEAN',
            'SEASONAL_NAIVE',
            'GRADIENT_BOOSTED_TREES'
        )
    ),
    CONSTRAINT chk_model_versions_status CHECK (
        lifecycle_status IN (
            'DRAFT', 'VALIDATED', 'APPROVED', 'REJECTED', 'RETIRED'
        )
    ),
    CONSTRAINT chk_model_versions_semantics CHECK (
        demand_event_semantics IN ('REQUEST_CREATED', 'TRIP_STARTED_PROXY')
    ),
    CONSTRAINT chk_model_versions_dimensions CHECK (
        cell_size_meters IN (250, 500, 1000, 2000)
        AND bucket_minutes = 15
        AND MOD(EXTRACT(EPOCH FROM training_cutoff_utc)::BIGINT, 900) = 0
        AND training_cutoff_utc <= created_at
    ),
    CONSTRAINT chk_model_versions_json CHECK (
        jsonb_typeof(hyperparameters) = 'object'
        AND jsonb_typeof(training_manifest) = 'object'
    ),
    CONSTRAINT chk_model_versions_lifecycle_time CHECK (
        (validated_at IS NULL OR validated_at >= created_at)
        AND (approved_at IS NULL OR approved_at >= validated_at)
        AND (retired_at IS NULL OR retired_at >= approved_at)
    ),
    CONSTRAINT chk_model_versions_lifecycle CHECK (
        (
            lifecycle_status = 'DRAFT'
            AND validated_at IS NULL
            AND approved_at IS NULL
            AND retired_at IS NULL
        )
        OR (
            lifecycle_status IN ('VALIDATED', 'REJECTED')
            AND validated_at IS NOT NULL
            AND approved_at IS NULL
            AND retired_at IS NULL
        )
        OR (
            lifecycle_status = 'APPROVED'
            AND validated_at IS NOT NULL
            AND approved_at IS NOT NULL
            AND retired_at IS NULL
        )
        OR (
            lifecycle_status = 'RETIRED'
            AND validated_at IS NOT NULL
            AND approved_at IS NOT NULL
            AND retired_at IS NOT NULL
        )
    )
);

CREATE INDEX idx_model_versions_status_name
    ON analytics.model_versions (lifecycle_status, model_name, created_at DESC);

CREATE INDEX idx_model_versions_profile
    ON analytics.model_versions (source_profile, dataset_version, cell_size_meters);

CREATE TABLE analytics.forecast_runs (
    forecast_run_id UUID PRIMARY KEY,
    processing_run_id UUID NOT NULL,
    processing_run_type VARCHAR(30) NOT NULL DEFAULT 'FORECAST',
    model_version_id UUID NOT NULL,
    run_purpose VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_profile VARCHAR(80) NOT NULL,
    dataset_version VARCHAR(120) NOT NULL,
    demand_event_semantics VARCHAR(30) NOT NULL,
    grid_version VARCHAR(80) NOT NULL,
    cell_size_meters INTEGER NOT NULL,
    bucket_minutes SMALLINT NOT NULL,
    inference_cutoff_utc TIMESTAMPTZ NOT NULL,
    config_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    generated_at_utc TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    error_code VARCHAR(80),
    error_message VARCHAR(2000),
    CONSTRAINT fk_forecast_runs_processing_contract FOREIGN KEY (
        processing_run_id,
        processing_run_type,
        source_profile,
        dataset_version,
        inference_cutoff_utc,
        config_hash
    ) REFERENCES analytics.processing_runs (
        run_id,
        run_type,
        source_profile,
        dataset_version,
        source_cutoff,
        config_hash
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_forecast_runs_model_compatibility FOREIGN KEY (
        model_version_id,
        source_profile,
        dataset_version,
        demand_event_semantics,
        grid_version,
        cell_size_meters,
        bucket_minutes
    ) REFERENCES analytics.model_versions (
        model_version_id,
        source_profile,
        dataset_version,
        demand_event_semantics,
        grid_version,
        cell_size_meters,
        bucket_minutes
    ) ON DELETE RESTRICT,
    CONSTRAINT uq_forecast_runs_processing UNIQUE (processing_run_id),
    CONSTRAINT uq_forecast_runs_idempotency UNIQUE (
        model_version_id,
        inference_cutoff_utc,
        config_hash,
        run_purpose
    ),
    CONSTRAINT uq_forecast_runs_model UNIQUE (
        forecast_run_id,
        model_version_id
    ),
    CONSTRAINT uq_forecast_runs_row_contract UNIQUE (
        forecast_run_id,
        model_version_id,
        generated_at_utc,
        inference_cutoff_utc,
        cell_size_meters
    ),
    CONSTRAINT chk_forecast_runs_purpose CHECK (
        run_purpose IN ('EVALUATION', 'PUBLISHED')
        AND processing_run_type = 'FORECAST'
    ),
    CONSTRAINT chk_forecast_runs_status CHECK (
        status IN (
            'PENDING', 'RUNNING', 'SUCCEEDED', 'PUBLISHED', 'FAILED', 'CANCELLED'
        )
    ),
    CONSTRAINT chk_forecast_runs_identity CHECK (
        BTRIM(source_profile) <> ''
        AND BTRIM(dataset_version) <> ''
        AND BTRIM(grid_version) <> ''
        AND config_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_forecast_runs_dimensions CHECK (
        demand_event_semantics IN ('REQUEST_CREATED', 'TRIP_STARTED_PROXY')
        AND cell_size_meters IN (250, 500, 1000, 2000)
        AND bucket_minutes = 15
        AND MOD(EXTRACT(EPOCH FROM inference_cutoff_utc)::BIGINT, 900) = 0
        AND inference_cutoff_utc <= created_at
    ),
    CONSTRAINT chk_forecast_runs_time_order CHECK (
        (started_at IS NULL OR started_at >= created_at)
        AND (generated_at_utc IS NULL OR (
            started_at IS NOT NULL
            AND generated_at_utc >= started_at
            AND generated_at_utc >= inference_cutoff_utc
        ))
        AND (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
        AND (published_at IS NULL OR (
            finished_at IS NOT NULL AND published_at >= finished_at
        ))
    ),
    CONSTRAINT chk_forecast_runs_state CHECK (
        (
            status = 'PENDING'
            AND started_at IS NULL
            AND generated_at_utc IS NULL
            AND finished_at IS NULL
            AND published_at IS NULL
            AND error_code IS NULL
            AND error_message IS NULL
        )
        OR (
            status = 'RUNNING'
            AND started_at IS NOT NULL
            AND generated_at_utc IS NOT NULL
            AND finished_at IS NULL
            AND published_at IS NULL
            AND error_code IS NULL
            AND error_message IS NULL
        )
        OR (
            status = 'SUCCEEDED'
            AND started_at IS NOT NULL
            AND generated_at_utc IS NOT NULL
            AND finished_at IS NOT NULL
            AND published_at IS NULL
            AND error_code IS NULL
            AND error_message IS NULL
        )
        OR (
            status = 'PUBLISHED'
            AND run_purpose = 'PUBLISHED'
            AND started_at IS NOT NULL
            AND generated_at_utc IS NOT NULL
            AND finished_at IS NOT NULL
            AND published_at IS NOT NULL
            AND error_code IS NULL
            AND error_message IS NULL
        )
        OR (
            status = 'FAILED'
            AND started_at IS NOT NULL
            AND finished_at IS NOT NULL
            AND published_at IS NULL
            AND error_code IS NOT NULL
            AND error_message IS NOT NULL
            AND BTRIM(error_code) <> ''
            AND BTRIM(error_message) <> ''
        )
        OR (
            status = 'CANCELLED'
            AND finished_at IS NOT NULL
            AND published_at IS NULL
            AND error_code IS NULL
            AND error_message IS NULL
        )
    )
);

CREATE INDEX idx_forecast_runs_status_cutoff
    ON analytics.forecast_runs (status, inference_cutoff_utc DESC);

CREATE INDEX idx_forecast_runs_model_cutoff
    ON analytics.forecast_runs (model_version_id, inference_cutoff_utc DESC);

CREATE TABLE analytics.demand_forecasts (
    demand_forecast_id BIGSERIAL PRIMARY KEY,
    forecast_run_id UUID NOT NULL,
    model_version_id UUID NOT NULL,
    cell_id VARCHAR(180) NOT NULL,
    cell_geometry geometry(Polygon, 4326) NOT NULL,
    cell_size_meters INTEGER NOT NULL,
    generated_at_utc TIMESTAMPTZ NOT NULL,
    inference_cutoff_utc TIMESTAMPTZ NOT NULL,
    target_bucket_start_utc TIMESTAMPTZ NOT NULL,
    horizon_minutes SMALLINT NOT NULL,
    predicted_demand NUMERIC(18, 6) NOT NULL,
    prediction_lower NUMERIC(18, 6),
    prediction_upper NUMERIC(18, 6),
    actual_demand INTEGER,
    absolute_error NUMERIC(18, 6),
    evaluated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_demand_forecasts_run_contract FOREIGN KEY (
        forecast_run_id,
        model_version_id,
        generated_at_utc,
        inference_cutoff_utc,
        cell_size_meters
    ) REFERENCES analytics.forecast_runs (
        forecast_run_id,
        model_version_id,
        generated_at_utc,
        inference_cutoff_utc,
        cell_size_meters
    ) ON DELETE RESTRICT,
    CONSTRAINT uq_demand_forecasts_identity UNIQUE (
        forecast_run_id,
        cell_id,
        target_bucket_start_utc,
        horizon_minutes
    ),
    CONSTRAINT chk_demand_forecasts_identity CHECK (BTRIM(cell_id) <> ''),
    CONSTRAINT chk_demand_forecasts_spatial CHECK (
        cell_size_meters IN (250, 500, 1000, 2000)
        AND ST_SRID(cell_geometry) = 4326
        AND GeometryType(cell_geometry) = 'POLYGON'
        AND NOT ST_IsEmpty(cell_geometry)
        AND ST_IsValid(cell_geometry)
        AND ST_CoveredBy(
            cell_geometry,
            ST_MakeEnvelope(-180, -90, 180, 90, 4326)
        )
    ),
    CONSTRAINT chk_demand_forecasts_alignment CHECK (
        MOD(EXTRACT(EPOCH FROM inference_cutoff_utc)::BIGINT, 900) = 0
        AND MOD(EXTRACT(EPOCH FROM target_bucket_start_utc)::BIGINT, 900) = 0
        AND horizon_minutes IN (15, 30, 60)
        AND target_bucket_start_utc =
            inference_cutoff_utc + horizon_minutes * INTERVAL '1 minute'
        AND generated_at_utc >= inference_cutoff_utc
    ),
    CONSTRAINT chk_demand_forecasts_prediction CHECK (
        predicted_demand >= 0
        AND (
            (prediction_lower IS NULL AND prediction_upper IS NULL)
            OR (
                prediction_lower IS NOT NULL
                AND prediction_upper IS NOT NULL
                AND prediction_lower >= 0
                AND prediction_lower <= predicted_demand
                AND predicted_demand <= prediction_upper
            )
        )
    ),
    CONSTRAINT chk_demand_forecasts_evaluation CHECK (
        (
            actual_demand IS NULL
            AND absolute_error IS NULL
            AND evaluated_at IS NULL
        )
        OR (
            actual_demand IS NOT NULL
            AND absolute_error IS NOT NULL
            AND actual_demand >= 0
            AND absolute_error = ABS(predicted_demand - actual_demand::NUMERIC)
            AND evaluated_at IS NOT NULL
            AND evaluated_at >= target_bucket_start_utc + INTERVAL '15 minutes'
            AND evaluated_at >= generated_at_utc
        )
    )
);

CREATE INDEX idx_demand_forecasts_target_lookup
    ON analytics.demand_forecasts (
        target_bucket_start_utc,
        horizon_minutes,
        cell_size_meters
    );

CREATE INDEX idx_demand_forecasts_cell_target
    ON analytics.demand_forecasts (cell_id, target_bucket_start_utc DESC);

CREATE INDEX idx_demand_forecasts_geometry_gist
    ON analytics.demand_forecasts USING GIST (cell_geometry);

CREATE INDEX idx_demand_forecasts_target_brin
    ON analytics.demand_forecasts USING BRIN (target_bucket_start_utc);

CREATE TABLE analytics.forecast_evaluations (
    forecast_evaluation_id BIGSERIAL PRIMARY KEY,
    evaluation_run_id UUID NOT NULL,
    forecast_run_id UUID NOT NULL,
    model_version_id UUID NOT NULL,
    fold_key VARCHAR(80) NOT NULL,
    metric_name VARCHAR(10) NOT NULL,
    horizon_minutes SMALLINT NOT NULL,
    cell_size_meters INTEGER NOT NULL,
    slice_type VARCHAR(40),
    slice_key VARCHAR(120),
    metric_value NUMERIC(24, 8) NOT NULL,
    sample_count BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_forecast_evaluations_processing_run
        FOREIGN KEY (evaluation_run_id)
        REFERENCES analytics.processing_runs(run_id) ON DELETE RESTRICT,
    CONSTRAINT fk_forecast_evaluations_forecast_model
        FOREIGN KEY (forecast_run_id, model_version_id)
        REFERENCES analytics.forecast_runs(forecast_run_id, model_version_id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_forecast_evaluations_fold CHECK (BTRIM(fold_key) <> ''),
    CONSTRAINT chk_forecast_evaluations_metric CHECK (
        metric_name IN ('MAE', 'RMSE', 'WAPE') AND metric_value >= 0
    ),
    CONSTRAINT chk_forecast_evaluations_dimensions CHECK (
        horizon_minutes IN (15, 30, 60)
        AND cell_size_meters IN (250, 500, 1000, 2000)
        AND sample_count > 0
        AND (
            (slice_type IS NULL AND slice_key IS NULL)
            OR (
                slice_type IS NOT NULL
                AND slice_key IS NOT NULL
                AND BTRIM(slice_type) <> ''
                AND BTRIM(slice_key) <> ''
            )
        )
    )
);

CREATE UNIQUE INDEX uq_forecast_evaluations_dimensions
    ON analytics.forecast_evaluations (
        evaluation_run_id,
        forecast_run_id,
        model_version_id,
        fold_key,
        metric_name,
        horizon_minutes,
        cell_size_meters,
        slice_type,
        slice_key
    ) NULLS NOT DISTINCT;

CREATE INDEX idx_forecast_evaluations_model_metric
    ON analytics.forecast_evaluations (
        model_version_id,
        metric_name,
        horizon_minutes,
        cell_size_meters
    );

CREATE FUNCTION analytics.enforce_published_forecast_model()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    model_status VARCHAR(20);
BEGIN
    IF NEW.run_purpose <> 'PUBLISHED' THEN
        RETURN NEW;
    END IF;

    SELECT lifecycle_status
    INTO model_status
    FROM analytics.model_versions
    WHERE model_version_id = NEW.model_version_id;

    IF model_status IS NOT NULL AND model_status <> 'APPROVED' THEN
        RAISE EXCEPTION
            'Published forecast run requires an APPROVED model, found %',
            model_status
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END $$;

CREATE TRIGGER trg_forecast_runs_approved_model
BEFORE INSERT OR UPDATE OF model_version_id, run_purpose, status
ON analytics.forecast_runs
FOR EACH ROW
EXECUTE FUNCTION analytics.enforce_published_forecast_model();

COMMIT;
