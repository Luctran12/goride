# Admin Analytics API Contract

> Version: 1.0
>
> Phase: 06 - API Hardening and Frontend Handoff
>
> Status: Frozen backend contract; pending Phase 6 review

## 1. Common Contract

Base path:

```text
/api/v1/admin/analytics
```

All endpoints:

- are read-only `GET` operations;
- require a JWT access token with `ROLE_ADMIN`;
- return `application/json`;
- use `[from, to)` range semantics;
- return normalized metrics so consumers do not reproduce formulas or
  percentiles;
- expose the physical query source and its freshness cutoff.

The generated OpenAPI 3.1 contract is available at `/v3/api-docs`. The
`bearerAuth` security scheme is an HTTP Bearer JWT.

### 1.1 Successful envelope

```json
{
  "success": true,
  "data": {},
  "message": "OK",
  "timestamp": "2026-07-29T08:00:00Z"
}
```

`timestamp` is the UTC time at which the HTTP envelope was created. It is not a
data-freshness timestamp.

### 1.2 Error envelope

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request is invalid",
    "details": {
      "from": "from is required and must include an offset"
    }
  },
  "requestId": "01J3ANALYTICSREQUEST",
  "timestamp": "2026-07-29T08:00:00Z"
}
```

`error.code` is stable and machine-readable. `error.details` is always an
object and may be empty. `requestId` is nullable when request correlation is
not active.

### 1.3 Shared query parameters

| Parameter | Type | Required | Rule |
| --- | --- | --- | --- |
| `from` | ISO-8601 timestamp | Yes | Inclusive; an offset or `Z` is required |
| `to` | ISO-8601 timestamp | Yes | Exclusive; an offset or `Z` is required |
| `timezone` | IANA zone ID | No | Default: `Asia/Ho_Chi_Minh` |
| `vehicleType` | enum | No | `MOTORBIKE`, `CAR_4_SEAT`, `CAR_7_SEAT` |
| `serviceAreaId` | positive integer | No | Omit for the global result |

`from` and `to` are normalized to UTC in response metadata. Calendar bucket
starts retain the offset of the requested reporting timezone.

### 1.4 Shared metadata

Every analytics payload contains:

```json
{
  "from": "2026-06-30T17:00:00Z",
  "to": "2026-07-31T17:00:00Z",
  "reportingTimezone": "Asia/Ho_Chi_Minh",
  "sourceVariant": "DIRECT",
  "dataFreshnessAt": "2026-07-31T17:00:00Z"
}
```

`sourceVariant` has exactly two values:

| Value | Meaning of `dataFreshnessAt` |
| --- | --- |
| `DIRECT` | Database snapshot cutoff captured at query start |
| `MATERIALIZED` | Cutoff of the latest successfully completed refresh |

If a requested filter cannot be answered exactly by the materialized read
model, the router uses `DIRECT` when fallback is enabled. This includes
partial-day overview/matching filters and heatmap requests with a bounding box.
The response always reports the source actually used.

### 1.5 Precision and units

| Value category | JSON representation |
| --- | --- |
| Counts | Integer in the metric's documented unit |
| Ratios and rates | Decimal ratio from `0` to `1`, scale 4, `HALF_UP` |
| Averages and distances | Decimal, scale 2, `HALF_UP` |
| Durations | Whole milliseconds, `HALF_UP` |
| Revenue | Decimal payment amount; current deployment currency is VND |
| Timestamps | ISO-8601 with an offset; shared range metadata is UTC |

Consumers may format ratios as percentages for display, but must not
recalculate a backend metric. Nullable derived metrics serialize as JSON
`null`; they never use a sentinel number or string.

## 2. Endpoints

### 2.1 Overview

```http
GET /api/v1/admin/analytics/overview
```

Successful `data` example:

```json
{
  "from": "2026-06-30T17:00:00Z",
  "to": "2026-07-31T17:00:00Z",
  "reportingTimezone": "Asia/Ho_Chi_Minh",
  "sourceVariant": "DIRECT",
  "dataFreshnessAt": "2026-07-31T17:00:00Z",
  "tripRequests": 10000,
  "completedTrips": 8250,
  "completedTripsByRequestCohort": 8200,
  "cancelledTrips": 900,
  "noDriverTrips": 900,
  "completionRate": 0.8200,
  "completedPayments": 8150,
  "completedRevenue": 245000000,
  "matchingRuns": 10000,
  "terminalRuns": 9980,
  "matchingSuccessRate": 0.9100,
  "averageMatchingDurationMs": 10320,
  "p50MatchingDurationMs": 8200,
  "p95MatchingDurationMs": 26400
}
```

`completedTrips` is completion throughput by `completedAt`.
`completedTripsByRequestCohort` is the numerator of `completionRate`, using
trips requested inside `[from, to)`.

### 2.2 Demand time series

```http
GET /api/v1/admin/analytics/demand/timeseries
```

Additional parameter:

| Parameter | Required | Values |
| --- | --- | --- |
| `bucket` | Yes | `HOUR`, `DAY`, `WEEK` |

The backend returns ordered, continuous calendar buckets. A missing demand
bucket is zero-filled.

```json
{
  "from": "2026-06-30T17:00:00Z",
  "to": "2026-07-01T17:00:00Z",
  "reportingTimezone": "Asia/Ho_Chi_Minh",
  "bucket": "HOUR",
  "sourceVariant": "DIRECT",
  "dataFreshnessAt": "2026-07-01T17:00:00Z",
  "points": [
    {
      "bucketStart": "2026-07-01T08:00:00+07:00",
      "tripRequests": 120,
      "completedTripsByRequestCohort": 98,
      "completionRate": 0.8167
    }
  ]
}
```

For a zero-count bucket, counts are `0` and `completionRate` is `null`.

### 2.3 Driver supply time series

```http
GET /api/v1/admin/analytics/supply/timeseries
```

Additional parameter:

| Parameter | Required | Values |
| --- | --- | --- |
| `bucket` | Yes | `HOUR`, `DAY` |

Each point has the same time-bucket semantics as demand:

```json
{
  "bucketStart": "2026-07-01T08:00:00+07:00",
  "averageOnlineDrivers": 84.25,
  "averageAvailableDrivers": 52.50,
  "averageBusyDrivers": 31.75,
  "snapshotCoverage": 0.9167,
  "tripRequests": 140,
  "requestToAvailableDriverRatio": 2.6667
}
```

Supply values use observed five-minute snapshots. Missing samples are not
zero-filled as driver counts. Instead:

- `snapshotCoverage` reports `observedBuckets / expectedBuckets`;
- the three driver averages are `null` when no sample exists;
- `tripRequests` remains a demand count and may be `0`;
- `requestToAvailableDriverRatio` is `null` when coverage is below `0.80`,
  supply is missing, or average availability is zero.

This is partial-data behavior, not an HTTP error.

### 2.4 Demand heatmap

```http
GET /api/v1/admin/analytics/demand/heatmap
```

Additional parameters:

| Parameter | Required | Rule |
| --- | --- | --- |
| `cellSizeMeters` | Yes | Integer: `250`, `500`, `1000`, or `2000` |
| `minLng` | No | WGS84 longitude; all four bounds are all-or-none |
| `minLat` | No | WGS84 latitude; all four bounds are all-or-none |
| `maxLng` | No | Must be greater than `minLng` |
| `maxLat` | No | Must be greater than `minLat` |

The payload is a GeoJSON `FeatureCollection`. Geometry is EPSG:4326 and
coordinates are in `[longitude, latitude]` order.

```json
{
  "type": "FeatureCollection",
  "metadata": {
    "from": "2026-06-30T17:00:00Z",
    "to": "2026-07-07T17:00:00Z",
    "reportingTimezone": "Asia/Ho_Chi_Minh",
    "cellSizeMeters": 1000,
    "sourceVariant": "DIRECT",
    "dataFreshnessAt": "2026-07-07T17:00:00Z"
  },
  "features": [
    {
      "type": "Feature",
      "geometry": {
        "type": "Polygon",
        "coordinates": [
          [
            [106.68, 10.76],
            [106.69, 10.76],
            [106.69, 10.77],
            [106.68, 10.77],
            [106.68, 10.76]
          ]
        ]
      },
      "properties": {
        "cellId": "32648:1000:685:1190",
        "tripRequests": 120,
        "completedTripsByRequestCohort": 97,
        "completionRate": 0.8083
      }
    }
  ]
}
```

`cellId` is a stable projected-grid identifier with the form
`{projectedSrid}:{cellSizeMeters}:{gridX}:{gridY}`. Features are ordered by
`tripRequests` descending and then `cellId`. An empty query returns
`features: []`.

### 2.5 Matching performance

```http
GET /api/v1/admin/analytics/matching/performance
```

```json
{
  "from": "2026-06-30T17:00:00Z",
  "to": "2026-07-07T17:00:00Z",
  "reportingTimezone": "Asia/Ho_Chi_Minh",
  "sourceVariant": "DIRECT",
  "dataFreshnessAt": "2026-07-07T17:00:00Z",
  "matchingRuns": 1000,
  "terminalRuns": 990,
  "matchedRuns": 900,
  "noDriverRuns": 60,
  "cancelledRuns": 25,
  "failedRuns": 5,
  "matchingSuccessRate": 0.9091,
  "averageMatchingDurationMs": 10320,
  "p50MatchingDurationMs": 8200,
  "p95MatchingDurationMs": 26400,
  "averageSearchesPerRun": 1.42,
  "averageCandidatesPerRun": 4.21,
  "averageOffersPerRun": 1.36,
  "offerAcceptanceRate": 0.6680,
  "offerRejectionRate": 0.2040,
  "offerTimeoutRate": 0.1280,
  "averageCandidateDistanceM": 1450.25
}
```

The backend calculates all averages, rates and continuous percentiles. Open
runs and non-terminal offers are excluded from terminal metrics.

### 2.6 Matching funnel

```http
GET /api/v1/admin/analytics/matching/funnel
```

The response always contains five steps in this order:

```json
{
  "steps": [
    {"name": "RUN_STARTED", "unit": "RUN", "count": 1000},
    {"name": "CANDIDATE_FOUND", "unit": "RUN", "count": 960},
    {"name": "OFFER_SENT", "unit": "RUN", "count": 940},
    {"name": "OFFER_ACCEPTED", "unit": "RUN", "count": 900},
    {"name": "TRIP_COMPLETED", "unit": "TRIP", "count": 820}
  ]
}
```

The complete payload also contains the shared metadata. The stable enums are:

```text
name: RUN_STARTED | CANDIDATE_FOUND | OFFER_SENT | OFFER_ACCEPTED | TRIP_COMPLETED
unit: RUN | TRIP
```

`OFFER_SENT` counts distinct runs with at least one persisted offer, not total
offers. `TRIP_COMPLETED` counts trips, so a consumer must not derive conversion
rates across the `RUN`/`TRIP` unit boundary.

## 3. Empty, Nullable and Partial Data Semantics

| Situation | HTTP result | Payload behavior |
| --- | --- | --- |
| Valid range with no records | `200` | Counts/revenue `0`; ratios, averages and percentiles `null` |
| Empty demand series range | `200` | Continuous points with zero counts and nullable rates |
| Empty heatmap | `200` | Valid metadata and `features: []` |
| Missing supply snapshots | `200` | Nullable averages, coverage reflects missing samples |
| Low supply coverage | `200` | `requestToAvailableDriverRatio: null` |
| Materialized source unavailable, fallback enabled | `200` | Direct query with `sourceVariant: DIRECT` |
| Materialized source unavailable, fallback disabled | `503` | `ANALYTICS_DATA_UNAVAILABLE` |

Consumers must distinguish a successful empty/partial payload from an error
envelope. A nullable metric means “not defined or not reliable for this
cohort,” not zero.

## 4. Validation and Guardrails

| Condition | HTTP | Error code |
| --- | --- | --- |
| Missing/malformed parameter, invalid timezone or enum | `400` | `VALIDATION_ERROR` |
| `from >= to` | `400` | `VALIDATION_ERROR` |
| General range exceeds 366 days | `400` | `ANALYTICS_RANGE_TOO_LARGE` |
| Heatmap range exceeds 31 days | `400` | `ANALYTICS_RANGE_TOO_LARGE` |
| Unsupported heatmap cell size | `400` | `VALIDATION_ERROR` |
| Bounds are incomplete, unordered or outside WGS84 | `400` | `VALIDATION_ERROR` |
| Bounding-box area exceeds configured maximum | `400` | `VALIDATION_ERROR` |
| Heatmap would exceed 5,000 cells | `400` | `ANALYTICS_RESULT_TOO_LARGE` |
| Positive service-area ID does not exist | `404` | `SERVICE_AREA_NOT_FOUND` |
| Missing/invalid token | `401` | Authentication error |
| Authenticated user lacks `ROLE_ADMIN` | `403` | `FORBIDDEN` |
| Materialized data unavailable and fallback disabled | `503` | `ANALYTICS_DATA_UNAVAILABLE` |

The default maximum bounding-box area is `25,000 km²`; it is configurable by
deployment. The heatmap result cap is enforced by fetching at most one row
beyond the configured limit and rejecting the whole response if it would be
truncated. Analytics endpoints do not paginate.

## 5. Backward Compatibility and Versioning

- `GET /api/v1/admin/dashboard` remains unchanged.
- Admin Analytics routes are additive and do not expose JPA entities.
- Enum names, field names, units, formulas and null semantics are contract
  elements.
- A breaking change requires a version update, a documented entry in
  `docs/changes-in-implementation.md`, updated OpenAPI/examples and updated
  contract tests.

The executable consumer examples are maintained in
[`admin-analytics-smoke.postman_collection.json`](admin-analytics-smoke.postman_collection.json).
Frontend-specific consumption rules are in
[`frontend-integration-guide.md`](frontend-integration-guide.md).
