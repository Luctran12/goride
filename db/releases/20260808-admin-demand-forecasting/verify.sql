-- Purpose: prove the complete forecasting persistence contract exists after
-- apply.sql and is safe for later processing/API phases to target.

DO $$
DECLARE
    missing_objects TEXT[];
    missing_constraints TEXT[];
BEGIN
    SELECT ARRAY_AGG(object_name ORDER BY object_name)
    INTO missing_objects
    FROM (
        VALUES
            ('processing_runs'),
            ('data_quality_results'),
            ('demand_features'),
            ('model_versions'),
            ('forecast_runs'),
            ('demand_forecasts'),
            ('forecast_evaluations'),
            ('uq_processing_runs_idempotency'),
            ('idx_processing_runs_status_created'),
            ('idx_processing_runs_profile_cutoff'),
            ('idx_data_quality_results_run_status'),
            ('idx_demand_features_training_lookup'),
            ('idx_demand_features_target_brin'),
            ('idx_model_versions_status_name'),
            ('idx_model_versions_profile'),
            ('idx_forecast_runs_status_cutoff'),
            ('idx_forecast_runs_model_cutoff'),
            ('idx_demand_forecasts_target_lookup'),
            ('idx_demand_forecasts_cell_target'),
            ('idx_demand_forecasts_geometry_gist'),
            ('idx_demand_forecasts_target_brin'),
            ('uq_forecast_evaluations_dimensions'),
            ('idx_forecast_evaluations_model_metric')
    ) AS expected(object_name)
    WHERE to_regclass('analytics.' || object_name) IS NULL;

    IF missing_objects IS NOT NULL THEN
        RAISE EXCEPTION 'Forecasting release objects are missing: %', missing_objects;
    END IF;

    SELECT ARRAY_AGG(constraint_name ORDER BY constraint_name)
    INTO missing_constraints
    FROM (
        VALUES
            ('uq_processing_runs_artifact'),
            ('uq_processing_runs_forecast_contract'),
            ('chk_processing_runs_run_type'),
            ('chk_processing_runs_status'),
            ('chk_processing_runs_identity'),
            ('chk_processing_runs_manifest'),
            ('chk_processing_runs_attempt'),
            ('chk_processing_runs_counts'),
            ('chk_processing_runs_cutoff'),
            ('chk_processing_runs_time_order'),
            ('chk_processing_runs_state'),
            ('fk_data_quality_results_run'),
            ('uq_data_quality_results_scope'),
            ('chk_data_quality_results_outcome'),
            ('chk_data_quality_results_counts'),
            ('fk_demand_features_run'),
            ('uq_demand_features_identity'),
            ('chk_demand_features_alignment'),
            ('chk_demand_features_calendar'),
            ('chk_demand_features_coverage'),
            ('chk_demand_features_label_time'),
            ('fk_model_versions_training_run'),
            ('uq_model_versions_name_version'),
            ('uq_model_versions_compatibility'),
            ('chk_model_versions_lifecycle'),
            ('fk_forecast_runs_processing_contract'),
            ('fk_forecast_runs_model_compatibility'),
            ('uq_forecast_runs_processing'),
            ('uq_forecast_runs_idempotency'),
            ('uq_forecast_runs_model'),
            ('uq_forecast_runs_row_contract'),
            ('chk_forecast_runs_state'),
            ('fk_demand_forecasts_run_contract'),
            ('uq_demand_forecasts_identity'),
            ('chk_demand_forecasts_spatial'),
            ('chk_demand_forecasts_alignment'),
            ('chk_demand_forecasts_prediction'),
            ('chk_demand_forecasts_evaluation'),
            ('fk_forecast_evaluations_processing_run'),
            ('fk_forecast_evaluations_forecast_model'),
            ('chk_forecast_evaluations_metric'),
            ('chk_forecast_evaluations_dimensions')
    ) AS expected(constraint_name)
    WHERE NOT EXISTS (
        SELECT 1
        FROM pg_constraint constraint_info
        JOIN pg_namespace constraint_schema
          ON constraint_schema.oid = constraint_info.connamespace
        WHERE constraint_schema.nspname = 'analytics'
          AND constraint_info.conname = expected.constraint_name
          AND constraint_info.convalidated
    );

    IF missing_constraints IS NOT NULL THEN
        RAISE EXCEPTION
            'Forecasting release constraints are missing or invalid: %',
            missing_constraints;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint constraint_info
        JOIN pg_namespace constraint_schema
          ON constraint_schema.oid = constraint_info.connamespace
        WHERE constraint_schema.nspname = 'analytics'
          AND constraint_info.contype = 'f'
          AND constraint_info.confdeltype <> 'r'
    ) THEN
        RAISE EXCEPTION 'All forecasting foreign keys must use ON DELETE RESTRICT';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_index index_info
        JOIN pg_class index_object ON index_object.oid = index_info.indexrelid
        JOIN pg_namespace index_schema ON index_schema.oid = index_object.relnamespace
        WHERE index_schema.nspname = 'analytics'
          AND index_object.relname = 'uq_forecast_evaluations_dimensions'
          AND index_info.indisunique
          AND index_info.indnullsnotdistinct
    ) THEN
        RAISE EXCEPTION
            'Evaluation dimension uniqueness must use NULLS NOT DISTINCT';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_index index_info
        JOIN pg_class index_object ON index_object.oid = index_info.indexrelid
        JOIN pg_namespace index_schema ON index_schema.oid = index_object.relnamespace
        JOIN pg_am access_method ON access_method.oid = index_object.relam
        WHERE index_schema.nspname = 'analytics'
          AND index_object.relname = 'idx_demand_forecasts_geometry_gist'
          AND access_method.amname = 'gist'
          AND index_info.indisvalid
    ) THEN
        RAISE EXCEPTION 'Forecast cell geometry GiST index is missing or invalid';
    END IF;

    IF (
        SELECT COUNT(*)
        FROM pg_index index_info
        JOIN pg_class index_object ON index_object.oid = index_info.indexrelid
        JOIN pg_namespace index_schema ON index_schema.oid = index_object.relnamespace
        JOIN pg_am access_method ON access_method.oid = index_object.relam
        WHERE index_schema.nspname = 'analytics'
          AND index_object.relname IN (
              'idx_demand_features_target_brin',
              'idx_demand_forecasts_target_brin'
          )
          AND access_method.amname = 'brin'
          AND index_info.indisvalid
    ) <> 2 THEN
        RAISE EXCEPTION 'Feature and forecast target-time BRIN indexes are required';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_attribute column_info
        WHERE column_info.attrelid = 'analytics.demand_forecasts'::REGCLASS
          AND column_info.attname = 'cell_geometry'
          AND postgis_typmod_type(column_info.atttypmod) = 'Polygon'
          AND postgis_typmod_srid(column_info.atttypmod) = 4326
    ) THEN
        RAISE EXCEPTION 'demand_forecasts.cell_geometry must be Polygon SRID 4326';
    END IF;

    IF to_regprocedure(
        'analytics.enforce_published_forecast_model()'
    ) IS NULL THEN
        RAISE EXCEPTION 'Published-model lifecycle guard function is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_trigger trigger_info
        WHERE trigger_info.tgrelid = 'analytics.forecast_runs'::REGCLASS
          AND trigger_info.tgname = 'trg_forecast_runs_approved_model'
          AND NOT trigger_info.tgisinternal
          AND trigger_info.tgenabled = 'O'
    ) THEN
        RAISE EXCEPTION 'Published-model lifecycle guard trigger is missing';
    END IF;
END $$;

SELECT
    table_name,
    (
        SELECT COUNT(*)
        FROM information_schema.columns column_info
        WHERE column_info.table_schema = 'analytics'
          AND column_info.table_name = expected.table_name
    ) AS column_count,
    pg_size_pretty(
        pg_total_relation_size(('analytics.' || table_name)::REGCLASS)
    ) AS total_size
FROM (
    VALUES
        ('processing_runs'),
        ('data_quality_results'),
        ('demand_features'),
        ('model_versions'),
        ('forecast_runs'),
        ('demand_forecasts'),
        ('forecast_evaluations')
) AS expected(table_name)
ORDER BY table_name;
