-- Purpose: exercise the Phase 2 persistence contract without retaining test
-- evidence. Run only after apply.sql and verify.sql on a non-production DB.

BEGIN;

INSERT INTO analytics.processing_runs (
    run_id,
    artifact_run_id,
    run_type,
    status,
    source_profile,
    dataset_version,
    source_cutoff,
    config_hash,
    code_commit,
    input_manifest,
    attempt_no,
    rows_read,
    rows_written,
    created_at,
    started_at,
    finished_at
) VALUES
    (
        '00000000-0000-0000-0000-000000000001',
        'fixture-feature-run',
        'FEATURE_BUILD',
        'SUCCEEDED',
        'porto-thesis',
        'porto-2013-07_2014-06-v1',
        '2014-06-01T00:00:00Z',
        repeat('1', 64),
        repeat('a', 40),
        '{"fixture":true}'::JSONB,
        1,
        100,
        10,
        '2026-08-08T00:00:00Z',
        '2026-08-08T00:00:01Z',
        '2026-08-08T00:00:02Z'
    ),
    (
        '00000000-0000-0000-0000-000000000002',
        'fixture-training-run',
        'TRAINING',
        'SUCCEEDED',
        'porto-thesis',
        'porto-2013-07_2014-06-v1',
        '2014-06-01T00:00:00Z',
        repeat('2', 64),
        repeat('b', 40),
        '{"fixture":true}'::JSONB,
        1,
        10,
        1,
        '2026-08-08T00:00:03Z',
        '2026-08-08T00:00:04Z',
        '2026-08-08T00:00:05Z'
    ),
    (
        '00000000-0000-0000-0000-000000000003',
        'fixture-forecast-run',
        'FORECAST',
        'SUCCEEDED',
        'porto-thesis',
        'porto-2013-07_2014-06-v1',
        '2014-06-01T00:00:00Z',
        repeat('3', 64),
        repeat('c', 40),
        '{"fixture":true}'::JSONB,
        1,
        1,
        1,
        '2026-08-08T00:00:06Z',
        '2026-08-08T00:00:07Z',
        '2026-08-08T00:00:08Z'
    ),
    (
        '00000000-0000-0000-0000-000000000004',
        'fixture-evaluation-run',
        'EVALUATION',
        'SUCCEEDED',
        'porto-thesis',
        'porto-2013-07_2014-06-v1',
        '2014-07-01T00:00:00Z',
        repeat('4', 64),
        repeat('d', 40),
        '{"fixture":true}'::JSONB,
        1,
        1,
        1,
        '2026-08-08T00:00:09Z',
        '2026-08-08T00:00:10Z',
        '2026-08-08T00:00:11Z'
    );

INSERT INTO analytics.data_quality_results (
    run_id,
    rule_code,
    scope_key,
    severity,
    result_status,
    records_checked,
    records_breached,
    threshold,
    details,
    evaluated_at
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'DQ_SCHEMA',
    'GLOBAL',
    'FAIL',
    'PASS',
    100,
    0,
    '{}',
    '{"fixture":true}',
    '2026-08-08T00:00:02Z'
);

INSERT INTO analytics.demand_features (
    feature_set_version,
    source_profile,
    dataset_version,
    demand_event_semantics,
    grid_version,
    projected_srid,
    cell_id,
    grid_x,
    grid_y,
    cell_size_meters,
    bucket_start_utc,
    inference_cutoff_utc,
    target_bucket_start_utc,
    horizon_minutes,
    target_trip_requests,
    lag_1,
    lag_2,
    lag_4,
    lag_96,
    lag_672,
    rolling_mean_4,
    rolling_mean_12,
    rolling_mean_96,
    rolling_mean_672,
    hour_sin,
    hour_cos,
    day_of_week,
    is_weekend,
    neighbor_demand_lag_1,
    available_driver_lag_1,
    coverage_ratio,
    quality_status,
    created_by_run_id
) VALUES (
    'demand-v1',
    'porto-thesis',
    'porto-2013-07_2014-06-v1',
    'TRIP_STARTED_PROXY',
    'porto-grid-v1',
    3763,
    'porto-grid-v1:3763:500:1:2',
    1,
    2,
    500,
    '2014-05-31T23:45:00Z',
    '2014-06-01T00:00:00Z',
    '2014-06-01T00:15:00Z',
    15,
    3,
    2,
    1,
    0,
    4,
    5,
    1.5,
    1.25,
    2.0,
    2.5,
    0,
    1,
    6,
    TRUE,
    1,
    NULL,
    1,
    'PASS',
    '00000000-0000-0000-0000-000000000001'
);

INSERT INTO analytics.model_versions (
    model_version_id,
    model_name,
    model_version,
    model_family,
    lifecycle_status,
    source_profile,
    dataset_version,
    demand_event_semantics,
    feature_set_version,
    grid_version,
    cell_size_meters,
    bucket_minutes,
    training_cutoff_utc,
    training_run_id,
    artifact_uri,
    artifact_sha256,
    hyperparameters,
    training_manifest,
    created_at,
    validated_at,
    approved_at
) VALUES (
    '10000000-0000-0000-0000-000000000001',
    'seasonal-naive',
    'fixture-v1',
    'SEASONAL_NAIVE',
    'APPROVED',
    'porto-thesis',
    'porto-2013-07_2014-06-v1',
    'TRIP_STARTED_PROXY',
    'demand-v1',
    'porto-grid-v1',
    500,
    15,
    '2014-06-01T00:00:00Z',
    '00000000-0000-0000-0000-000000000002',
    'models/demand-forecasting/fixture-v1/model.json',
    repeat('e', 64),
    '{"seasonalLag":672}',
    '{"fixture":true}',
    '2026-08-08T00:00:12Z',
    '2026-08-08T00:00:13Z',
    '2026-08-08T00:00:14Z'
);

INSERT INTO analytics.forecast_runs (
    forecast_run_id,
    processing_run_id,
    model_version_id,
    run_purpose,
    status,
    source_profile,
    dataset_version,
    demand_event_semantics,
    grid_version,
    cell_size_meters,
    bucket_minutes,
    inference_cutoff_utc,
    config_hash,
    created_at,
    started_at,
    generated_at_utc,
    finished_at,
    published_at
) VALUES (
    '20000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000003',
    '10000000-0000-0000-0000-000000000001',
    'PUBLISHED',
    'PUBLISHED',
    'porto-thesis',
    'porto-2013-07_2014-06-v1',
    'TRIP_STARTED_PROXY',
    'porto-grid-v1',
    500,
    15,
    '2014-06-01T00:00:00Z',
    repeat('3', 64),
    '2026-08-08T00:00:15Z',
    '2026-08-08T00:00:16Z',
    '2026-08-08T00:00:17Z',
    '2026-08-08T00:00:18Z',
    '2026-08-08T00:00:19Z'
);

INSERT INTO analytics.demand_forecasts (
    forecast_run_id,
    model_version_id,
    cell_id,
    cell_geometry,
    cell_size_meters,
    generated_at_utc,
    inference_cutoff_utc,
    target_bucket_start_utc,
    horizon_minutes,
    predicted_demand,
    prediction_lower,
    prediction_upper
) VALUES (
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    'porto-grid-v1:3763:500:1:2',
    ST_GeomFromText(
        'POLYGON((-8.62 41.14,-8.61 41.14,-8.61 41.15,-8.62 41.15,-8.62 41.14))',
        4326
    ),
    500,
    '2026-08-08T00:00:17Z',
    '2014-06-01T00:00:00Z',
    '2014-06-01T00:15:00Z',
    15,
    2.5,
    1,
    4
);

UPDATE analytics.demand_forecasts
SET actual_demand = 3,
    absolute_error = 0.5,
    evaluated_at = '2026-08-08T00:00:20Z'
WHERE forecast_run_id = '20000000-0000-0000-0000-000000000001';

INSERT INTO analytics.forecast_evaluations (
    evaluation_run_id,
    forecast_run_id,
    model_version_id,
    fold_key,
    metric_name,
    horizon_minutes,
    cell_size_meters,
    slice_type,
    slice_key,
    metric_value,
    sample_count,
    created_at
) VALUES (
    '00000000-0000-0000-0000-000000000004',
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    'final-holdout',
    'MAE',
    15,
    500,
    NULL,
    NULL,
    0.5,
    1,
    '2026-08-08T00:00:21Z'
);

DO $$
BEGIN
    BEGIN
        INSERT INTO analytics.processing_runs (
            run_id, artifact_run_id, run_type, status, source_profile,
            dataset_version, source_cutoff, config_hash, code_commit,
            input_manifest, attempt_no, created_at
        ) VALUES (
            '00000000-0000-0000-0000-000000000099',
            'fixture-duplicate-natural-key',
            'FEATURE_BUILD',
            'PENDING',
            'porto-thesis',
            'porto-2013-07_2014-06-v1',
            '2014-06-01T00:00:00Z',
            repeat('1', 64),
            repeat('a', 40),
            '{}',
            1,
            '2026-08-08T00:01:00Z'
        );
        RAISE EXCEPTION 'duplicate processing run was accepted';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;

    BEGIN
        INSERT INTO analytics.demand_forecasts (
            forecast_run_id, model_version_id, cell_id, cell_geometry,
            cell_size_meters, generated_at_utc, inference_cutoff_utc,
            target_bucket_start_utc, horizon_minutes, predicted_demand
        )
        SELECT
            forecast_run_id,
            model_version_id,
            cell_id,
            cell_geometry,
            cell_size_meters,
            generated_at_utc,
            inference_cutoff_utc,
            target_bucket_start_utc,
            horizon_minutes,
            predicted_demand
        FROM analytics.demand_forecasts
        WHERE forecast_run_id = '20000000-0000-0000-0000-000000000001';
        RAISE EXCEPTION 'duplicate forecast was accepted';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;

    BEGIN
        INSERT INTO analytics.forecast_evaluations (
            evaluation_run_id, forecast_run_id, model_version_id, fold_key,
            metric_name, horizon_minutes, cell_size_meters, metric_value,
            sample_count
        ) VALUES (
            '00000000-0000-0000-0000-000000000004',
            '20000000-0000-0000-0000-000000000001',
            '10000000-0000-0000-0000-000000000001',
            'final-holdout',
            'MAE',
            15,
            500,
            0.5,
            1
        );
        RAISE EXCEPTION 'duplicate evaluation dimensions were accepted';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;

    BEGIN
        INSERT INTO analytics.model_versions (
            model_version_id, model_name, model_version, model_family,
            lifecycle_status, source_profile, dataset_version,
            demand_event_semantics, feature_set_version, grid_version,
            cell_size_meters, bucket_minutes, training_cutoff_utc,
            training_run_id, artifact_uri, artifact_sha256,
            hyperparameters, training_manifest, created_at
        ) VALUES (
            '10000000-0000-0000-0000-000000000099',
            'invalid-lifecycle',
            'fixture-v1',
            'SEASONAL_NAIVE',
            'APPROVED',
            'porto-thesis',
            'porto-2013-07_2014-06-v1',
            'TRIP_STARTED_PROXY',
            'demand-v1',
            'porto-grid-v1',
            500,
            15,
            '2014-06-01T00:00:00Z',
            '00000000-0000-0000-0000-000000000002',
            'invalid/model.json',
            repeat('f', 64),
            '{}',
            '{}',
            '2026-08-08T00:02:00Z'
        );
        RAISE EXCEPTION 'invalid approved model lifecycle was accepted';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;

    BEGIN
        INSERT INTO analytics.demand_features (
            feature_set_version,
            source_profile,
            dataset_version,
            demand_event_semantics,
            grid_version,
            projected_srid,
            cell_id,
            grid_x,
            grid_y,
            cell_size_meters,
            bucket_start_utc,
            inference_cutoff_utc,
            target_bucket_start_utc,
            horizon_minutes,
            hour_sin,
            hour_cos,
            day_of_week,
            is_weekend,
            coverage_ratio,
            quality_status,
            created_by_run_id
        ) VALUES (
            'demand-v1',
            'porto-thesis',
            'porto-2013-07_2014-06-v1',
            'TRIP_STARTED_PROXY',
            'porto-grid-v1',
            3763,
            'porto-grid-v1:3763:333:9:9',
            9,
            9,
            333,
            '2014-05-31T23:45:00Z',
            '2014-06-01T00:00:00Z',
            '2014-06-01T00:15:00Z',
            15,
            0,
            1,
            6,
            TRUE,
            1,
            'PASS',
            '00000000-0000-0000-0000-000000000001'
        );
        RAISE EXCEPTION 'unsupported cell size was accepted';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;

    UPDATE analytics.model_versions
    SET lifecycle_status = 'VALIDATED',
        approved_at = NULL
    WHERE model_version_id = '10000000-0000-0000-0000-000000000001';

    BEGIN
        UPDATE analytics.forecast_runs
        SET status = status
        WHERE forecast_run_id = '20000000-0000-0000-0000-000000000001';
        RAISE EXCEPTION 'published run accepted a non-approved model';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;

    UPDATE analytics.model_versions
    SET lifecycle_status = 'APPROVED',
        approved_at = '2026-08-08T00:00:14Z'
    WHERE model_version_id = '10000000-0000-0000-0000-000000000001';

    BEGIN
        DELETE FROM analytics.model_versions
        WHERE model_version_id = '10000000-0000-0000-0000-000000000001';
        RAISE EXCEPTION 'referenced model deletion was accepted';
    EXCEPTION WHEN integrity_constraint_violation THEN
        NULL;
    END;
END $$;

DO $$
DECLARE
    rejected BOOLEAN := FALSE;
BEGIN
    BEGIN
        INSERT INTO analytics.demand_forecasts (
            forecast_run_id,
            model_version_id,
            cell_id,
            cell_geometry,
            cell_size_meters,
            generated_at_utc,
            inference_cutoff_utc,
            target_bucket_start_utc,
            horizon_minutes,
            predicted_demand
        ) VALUES (
            '20000000-0000-0000-0000-000000000001',
            '10000000-0000-0000-0000-000000000001',
            'invalid-srid-cell',
            ST_GeomFromText(
                'POLYGON((0 0,1 0,1 1,0 1,0 0))',
                3763
            ),
            500,
            '2026-08-08T00:00:17Z',
            '2014-06-01T00:00:00Z',
            '2014-06-01T00:15:00Z',
            15,
            1
        );
    EXCEPTION WHEN OTHERS THEN
        rejected := TRUE;
    END;

    IF NOT rejected THEN
        RAISE EXCEPTION 'non-EPSG:4326 forecast geometry was accepted';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM analytics.demand_forecasts
        WHERE forecast_run_id = '20000000-0000-0000-0000-000000000001'
          AND actual_demand = 3
          AND absolute_error = 0.5
          AND evaluated_at IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'forecast actual/error backfill contract failed';
    END IF;

    IF (
        SELECT COUNT(*) FROM analytics.forecast_evaluations
        WHERE forecast_run_id = '20000000-0000-0000-0000-000000000001'
    ) <> 1 THEN
        RAISE EXCEPTION 'evaluation idempotency fixture failed';
    END IF;
END $$;

ROLLBACK;
