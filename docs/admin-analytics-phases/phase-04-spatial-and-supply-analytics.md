# Phase 04 - Spatial Demand and Supply Analytics

## Goal

Add PostGIS-backed spatial demand aggregation and align demand with persisted driver-supply samples.

## Prerequisites

- Phase 03 direct-query conventions approved.

## Scope

- Configurable, bounded square-grid aggregation.
- Service-area filter.
- GeoJSON response model.
- Supply coverage calculation.
- Demand-to-available-driver ratio for aligned buckets.
- Spatial correctness and query-plan tests.

## Planned Commit

```text
feat: add PostGIS demand and supply analytics
```

## Acceptance Criteria

- Returned GeoJSON is valid EPSG:4326.
- Cell size is restricted to approved values.
- Boundary behavior is deterministic.
- Time and spatial filters are both applied.
- Low snapshot coverage suppresses misleading supply ratios.
- Query plans use appropriate temporal/spatial indexes.
- Payload size and maximum bounds are guarded.

## Review Gate

Review coordinate transforms, boundary semantics and `EXPLAIN ANALYZE` evidence before Phase 05.

