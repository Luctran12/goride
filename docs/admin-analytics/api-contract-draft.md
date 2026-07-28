# Admin Analytics API Contract Draft

> Version: 1.0-draft
>
> Phase: 00 - Contracts and Architecture
>
> Status: Proposed for review

## 1. Common Contract

Base path:

```text
/api/v1/admin/analytics
```

Security:

```text
ROLE_ADMIN
```

Envelope:

```text
ApiResponse<T>
```

Required common parameters:

| Parameter | Type | Rule |
| --- | --- | --- |
| `from` | ISO-8601 timestamp | Inclusive; offset or `Z` required |
| `to` | ISO-8601 timestamp | Exclusive; offset or `Z` required |
| `timezone` | IANA zone | Optional; default `Asia/Ho_Chi_Minh` |
| `vehicleType` | enum | Optional |
| `serviceAreaId` | positive long | Optional |

Validation:

- `from < to`.
- General analytics maximum range: 366 days.
- Heatmap maximum range: 31 days.
- Unsupported timezone returns `VALIDATION_ERROR`.
- Unknown service area returns `SERVICE_AREA_NOT_FOUND`.
- Future enum values must fail as validation errors, not server errors.

Common metadata:

```json
{
  "from": "2026-06-30T17:00:00Z",
  "to": "2026-07-31T17:00:00Z",
  "reportingTimezone": "Asia/Ho_Chi_Minh",
  "sourceVariant": "DIRECT",
  "dataFreshnessAt": "2026-07-31T17:00:00Z"
}
```

`sourceVariant` values:

```text
DIRECT
MATERIALIZED
```

For direct queries, `dataFreshnessAt` equals the query start cutoff. For materialized queries, it equals the completed refresh cutoff.

---

## 2. Overview

```http
GET /api/v1/admin/analytics/overview
```

Response data:

```json
{
  "from": "2026-06-30T17:00:00Z",
  "to": "2026-07-31T17:00:00Z",
  "reportingTimezone": "Asia/Ho_Chi_Minh",
  "sourceVariant": "DIRECT",
  "dataFreshnessAt": "2026-07-28T03:00:00Z",
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

Empty range:

- counts: `0`;
- revenue: `0`;
- ratios, averages and percentiles: `null`.

---

## 3. Demand Time Series

```http
GET /api/v1/admin/analytics/demand/timeseries
```

Additional parameter:

| Parameter | Values |
| --- | --- |
| `bucket` | `HOUR`, `DAY`, `WEEK` |

Response data:

```json
{
  "from": "2026-07-01T00:00:00Z",
  "to": "2026-07-02T00:00:00Z",
  "reportingTimezone": "Asia/Ho_Chi_Minh",
  "bucket": "HOUR",
  "sourceVariant": "DIRECT",
  "dataFreshnessAt": "2026-07-28T03:00:00Z",
  "points": [
    {
      "bucketStart": "2026-07-01T00:00:00+07:00",
      "tripRequests": 120,
      "completedTripsByRequestCohort": 98,
      "completionRate": 0.8167
    }
  ]
}
```

The backend returns continuous buckets, including zero-count buckets.

---

## 4. Driver Supply Time Series

```http
GET /api/v1/admin/analytics/supply/timeseries
```

Additional parameter:

| Parameter | Values |
| --- | --- |
| `bucket` | `HOUR`, `DAY` |

Response point:

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

`requestToAvailableDriverRatio` is `null` when coverage is below `0.80`.

---

## 5. Demand Heatmap

```http
GET /api/v1/admin/analytics/demand/heatmap
```

Additional parameters:

| Parameter | Rule |
| --- | --- |
| `cellSizeMeters` | Required; one of `250`, `500`, `1000`, `2000` |
| `minLng`, `minLat`, `maxLng`, `maxLat` | Optional bounding box; all-or-none |

Guardrails:

- Maximum range: 31 days.
- Maximum returned cells: 5,000.
- Bounding box area is limited by configuration.
- Requests exceeding limits return `VALIDATION_ERROR`.

Response data is a GeoJSON FeatureCollection:

```json
{
  "type": "FeatureCollection",
  "metadata": {
    "from": "2026-07-01T00:00:00Z",
    "to": "2026-07-08T00:00:00Z",
    "reportingTimezone": "Asia/Ho_Chi_Minh",
    "cellSizeMeters": 1000,
    "sourceVariant": "DIRECT",
    "dataFreshnessAt": "2026-07-28T03:00:00Z"
  },
  "features": [
    {
      "type": "Feature",
      "geometry": {
        "type": "Polygon",
        "coordinates": []
      },
      "properties": {
        "cellId": "stable-cell-id",
        "tripRequests": 120,
        "completedTripsByRequestCohort": 97,
        "completionRate": 0.8083
      }
    }
  ]
}
```

Geometry is always EPSG:4326.

---

## 6. Matching Performance

```http
GET /api/v1/admin/analytics/matching/performance
```

Response data:

```json
{
  "from": "2026-07-01T00:00:00Z",
  "to": "2026-07-08T00:00:00Z",
  "reportingTimezone": "Asia/Ho_Chi_Minh",
  "sourceVariant": "DIRECT",
  "dataFreshnessAt": "2026-07-28T03:00:00Z",
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

---

## 7. Matching Funnel

```http
GET /api/v1/admin/analytics/matching/funnel
```

Response data:

```json
{
  "from": "2026-07-01T00:00:00Z",
  "to": "2026-07-08T00:00:00Z",
  "reportingTimezone": "Asia/Ho_Chi_Minh",
  "sourceVariant": "DIRECT",
  "dataFreshnessAt": "2026-07-28T03:00:00Z",
  "steps": [
    {
      "name": "RUN_STARTED",
      "unit": "RUN",
      "count": 1000
    },
    {
      "name": "CANDIDATE_FOUND",
      "unit": "RUN",
      "count": 960
    },
    {
      "name": "OFFER_SENT",
      "unit": "RUN",
      "count": 940
    },
    {
      "name": "OFFER_ACCEPTED",
      "unit": "RUN",
      "count": 900
    },
    {
      "name": "TRIP_COMPLETED",
      "unit": "TRIP",
      "count": 820
    }
  ]
}
```

`OFFER_SENT` counts distinct runs with at least one offer, not total offers.

---

## 8. Error and Partial Data Semantics

Expected error codes:

```text
VALIDATION_ERROR
SERVICE_AREA_NOT_FOUND
ANALYTICS_RANGE_TOO_LARGE
ANALYTICS_RESULT_TOO_LARGE
ANALYTICS_DATA_UNAVAILABLE
```

Rules:

- A valid query with no records returns `200`, not `404`.
- Missing optional supply snapshots return `snapshotCoverage = 0` and nullable derived ratios.
- A failed materialized refresh does not silently report current freshness.
- If materialized data is unavailable and fallback is enabled, response uses `sourceVariant = DIRECT`.
- If fallback is disabled, return `ANALYTICS_DATA_UNAVAILABLE`.

---

## 9. Backward Compatibility

- `GET /api/v1/admin/dashboard` remains unchanged.
- Analytics endpoints are additive.
- No analytics response exposes JPA entities.
- Contract changes after Phase 00 require a documented deviation and version update.
