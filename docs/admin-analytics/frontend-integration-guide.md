# Admin Analytics Frontend Integration Guide

> Contract version: 1.0
>
> Target consumer: Admin Web
>
> Backend base path: `/api/v1/admin/analytics`

This guide explains how Admin Web consumes the frozen backend contract. Metric
definitions remain authoritative in
[`metric-dictionary.md`](metric-dictionary.md), while exact payload and
guardrail behavior is defined in
[`api-contract.md`](api-contract.md).

## 1. Integration Boundary

The frontend is responsible for:

- selecting and serializing filters;
- displaying loading, empty, partial and error states;
- formatting backend values for human-readable presentation;
- displaying freshness and query-source metadata;
- rendering returned GeoJSON.

The frontend must not:

- calculate KPI formulas, P50/P95 values or funnel counts;
- infer missing supply values as zero;
- hide `snapshotCoverage` when showing a supply-derived ratio;
- merge `RUN` and `TRIP` funnel steps into one conversion cohort;
- infer freshness from the response-envelope `timestamp`.

## 2. Authentication and Response Parsing

Send the Admin JWT on every request:

```http
Authorization: Bearer <access-token>
Accept: application/json
```

A successful response uses:

```ts
type ApiResponse<T> = {
  success: true;
  data: T;
  message: string;
  timestamp: string;
};
```

An error uses:

```ts
type ErrorResponse = {
  success: false;
  error: {
    code: string;
    message: string;
    details: Record<string, unknown>;
  };
  requestId: string | null;
  timestamp: string;
};
```

Parse the HTTP status before reading `data`. Do not treat a `200` empty dataset
as an error, and do not assume a non-`200` body has the success-envelope shape.
Retain `requestId` in diagnostic UI or logs so a backend request can be traced.

## 3. Filter Serialization and URL State

Use one shared filter object for all six endpoints:

```ts
type AnalyticsFilters = {
  from: string;
  to: string;
  timezone?: string;
  vehicleType?: "MOTORBIKE" | "CAR_4_SEAT" | "CAR_7_SEAT";
  serviceAreaId?: number;
};
```

Serialize with `URLSearchParams`; do not hand-build a query string:

```ts
function toAnalyticsParams(filters: AnalyticsFilters): URLSearchParams {
  const params = new URLSearchParams({
    from: filters.from,
    to: filters.to,
    timezone: filters.timezone ?? "Asia/Ho_Chi_Minh",
  });

  if (filters.vehicleType) {
    params.set("vehicleType", filters.vehicleType);
  }
  if (filters.serviceAreaId !== undefined) {
    params.set("serviceAreaId", String(filters.serviceAreaId));
  }
  return params;
}
```

Preserve ISO-8601 offsets in `from` and `to`. `URLSearchParams` safely encodes
the `+` in values such as `2026-07-01T00:00:00+07:00`; manual concatenation may
turn it into a space. Keep active filters in the browser URL so reload, sharing
and back/forward navigation reproduce the same request.

The backend returns normalized UTC `from`/`to`. Use
`reportingTimezone` and each point's `bucketStart` for labels; do not bucket UTC
timestamps again in the browser.

## 4. Endpoint Matrix

| UI data | Route | Additional parameters | Successful empty state |
| --- | --- | --- | --- |
| KPI overview | `/overview` | None | Zero counts/revenue; nullable derived metrics |
| Demand chart | `/demand/timeseries` | `bucket=HOUR\|DAY\|WEEK` | Continuous zero-count points |
| Supply chart | `/supply/timeseries` | `bucket=HOUR\|DAY` | Nullable supply averages and coverage |
| Demand map | `/demand/heatmap` | `cellSizeMeters`; optional four bounds | `features: []` |
| Matching KPI | `/matching/performance` | None | Zero counts; nullable rates/averages/percentiles |
| Matching funnel | `/matching/funnel` | None | Five ordered zero-count steps |

All routes are relative to `/api/v1/admin/analytics`.

Recommended client cache/query keys include every filter that changes the
response:

```text
["admin-analytics", endpoint, from, to, timezone, vehicleType, serviceAreaId,
 bucket, cellSizeMeters, minLng, minLat, maxLng, maxLat]
```

Omit values that do not apply to the endpoint, but never omit an active
dimension from the key.

## 5. UI State Semantics

### Loading

Keep the previous successful result visually distinct if the UI supports
background refresh. Do not present old values as current while silently
changing filters.

### Empty

A `200` with zero counts or an empty `features` list is a valid empty result.
Show the selected range and dimensions with an explicit “no data” state.

### Nullable metric

Display a neutral marker such as `—` or “Not enough data”. Do not format
`null` as `0`, `0%`, or `0 ms`.

### Partial supply

Use `snapshotCoverage` to communicate completeness:

- `1.0000`: complete expected snapshot coverage;
- `0 < coverage < 1`: partial coverage;
- `0`: no observed snapshots for a non-empty expected interval;
- `null`: no expected sampling bucket for that point.

The backend already suppresses `requestToAvailableDriverRatio` below `0.80`.
The frontend should show why the value is unavailable, not compute an
alternative ratio.

### Error

Map stable codes to appropriate UI behavior:

| Code/status | Consumer behavior |
| --- | --- |
| `VALIDATION_ERROR` | Associate `error.details` with filters when possible |
| `ANALYTICS_RANGE_TOO_LARGE` | Keep filters and explain the permitted range |
| `ANALYTICS_RESULT_TOO_LARGE` | Ask for a tighter map bounds/range or larger cells |
| `SERVICE_AREA_NOT_FOUND` | Clear or refresh the stale service-area selection |
| `401` | Use the existing session refresh/login flow |
| `403` / `FORBIDDEN` | Show access denied; do not retry automatically |
| `ANALYTICS_DATA_UNAVAILABLE` | Show a temporary unavailable state with retry |

Do not expose a raw stack trace or backend implementation detail.

## 6. Units, Formatting and Freshness

- Ratios are decimals (`0.8167`), so display-only percentage formatting may
  render `81.67%`.
- Revenue is the completed-payment amount; current deployment currency is VND.
- Durations are whole milliseconds. A view may convert them to seconds while
  preserving the original meaning.
- Candidate distance is meters and may be converted only for display.
- Driver averages have scale 2; counts remain integers.

Every payload exposes:

```ts
type AnalyticsMetadata = {
  from: string;
  to: string;
  reportingTimezone: string;
  sourceVariant: "DIRECT" | "MATERIALIZED";
  dataFreshnessAt: string;
};
```

Show `dataFreshnessAt` anywhere data age affects interpretation. A
`MATERIALIZED` response can be older than its HTTP response. `DIRECT` and
`MATERIALIZED` are provenance labels, not quality rankings; the frontend must
not override or guess them.

## 7. GeoJSON Heatmap

Use:

```text
cellSizeMeters = 250 | 500 | 1000 | 2000
```

If map bounds are sent, send all four: `minLng`, `minLat`, `maxLng`, `maxLat`.
Coordinates are WGS84/EPSG:4326 in `[longitude, latitude]` order. Pass the
returned `FeatureCollection` directly to a GeoJSON-capable layer instead of
reconstructing cell polygons.

The backend enforces:

- a maximum heatmap range of 31 days;
- a configurable bounding-box area, `25,000 km²` by default;
- at most 5,000 returned cells;
- a fixed cell-size whitelist.

Heatmap responses are not paginated. When the result is too large, tighten the
time/bounds filter or select a larger supported cell size and send a new
request.

## 8. OpenAPI Types and Enum Handling

Generate or validate client types against the running backend's
`/v3/api-docs`. The document is OpenAPI 3.1 and contains:

- concrete success envelopes for every endpoint;
- the shared `ErrorResponse`;
- Bearer JWT security requirements;
- enum values, nullable types, examples, units and guardrails.

Treat known enum values as generated contract values. For display labels, use
a fallback that shows an unknown future value safely instead of crashing. A
new enum value is a contract evolution; it must not be silently mapped to an
existing semantic value.

## 9. Consumer Smoke Verification

Import
[`admin-analytics-smoke.postman_collection.json`](admin-analytics-smoke.postman_collection.json)
into Postman and set:

```text
baseUrl=http://localhost:8080
accessToken=<ADMIN JWT>
from=2026-07-01T00:00:00+07:00
to=2026-07-02T00:00:00+07:00
timezone=Asia/Ho_Chi_Minh
```

Run the complete collection. It calls all six endpoints and verifies status,
the success envelope, metadata, endpoint-specific shape and GeoJSON types.
The backend integration suite also parses and executes the same collection
against PostgreSQL/PostGIS, preventing examples from drifting away from the
implemented routes.

## 10. Handoff Checklist

- Generate/validate types from `/v3/api-docs`.
- Reuse one `[from, to)` filter model and preserve timezone offsets.
- Include every response-changing filter in client query/cache keys.
- Implement loading, empty, nullable, partial and error states separately.
- Display backend freshness rather than envelope time.
- Do not duplicate metric formulas or percentiles.
- Pass returned GeoJSON through without swapping longitude/latitude.
- Run the smoke collection with an Admin token.
- Confirm the existing `/api/v1/admin/dashboard` integration remains
  unchanged.
