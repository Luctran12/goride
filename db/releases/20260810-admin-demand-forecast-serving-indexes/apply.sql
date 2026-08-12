BEGIN;

CREATE INDEX IF NOT EXISTS idx_demand_forecasts_serving_lookup
    ON analytics.demand_forecasts (
        forecast_run_id,
        horizon_minutes,
        target_bucket_start_utc,
        cell_id
    )
    INCLUDE (
        cell_size_meters,
        predicted_demand,
        prediction_lower,
        prediction_upper,
        actual_demand,
        absolute_error,
        evaluated_at
    );

CREATE INDEX IF NOT EXISTS idx_demand_forecasts_hotspot_lookup
    ON analytics.demand_forecasts (
        forecast_run_id,
        horizon_minutes,
        predicted_demand DESC,
        cell_id
    )
    INCLUDE (
        target_bucket_start_utc,
        cell_size_meters,
        prediction_lower,
        prediction_upper,
        actual_demand,
        absolute_error,
        evaluated_at
    );

COMMIT;
