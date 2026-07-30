# Admin Analytics Security, Rate-Limit and Observability Review

> Review date: 2026-07-30
>
> Scope: Admin Analytics backend Phase 8

## 1. Review Outcome

No unresolved correctness blocker was found in the reviewed backend scope.
Admin Analytics remains read-only, Admin-only and bounded by time, spatial,
payload and transaction limits.

One external action is mandatory: rotate the database credential that existed
in Git history before Phase 8. Removing it from the current file does not revoke
the old value.

## 2. Security Review

| Control | Evidence | Result |
| --- | --- | --- |
| Authentication | OAuth2 resource-server JWT | Pass |
| Authorization | Class-level `hasRole('ADMIN')` on all six endpoints | Pass |
| Mutation surface | All Analytics routes are `GET`; no entity is exposed | Pass |
| Input validation | Timestamp, timezone, enum, service-area and spatial validation | Pass |
| Secret handling | Database and Redis settings use environment overrides | Pass |
| Production datasource | Fail-fast on loopback JDBC, embedded credentials, `postgres` superuser and default passwords | Pass |
| Query timeout | Read-only repeatable-read transaction, 15-second timeout | Pass |
| Sensitive logging | No coordinates, bounds, user IDs, trip IDs or raw exception message in Analytics query logs | Pass |
| Legacy compatibility | Existing `/api/v1/admin/dashboard` contract remains frozen | Pass |

The production validator also retains the existing checks for JWT, unsafe
Hibernate DDL modes, CORS, API documentation, durable storage, Redis-backed rate
limiting and tracing.

The metrics and Prometheus Actuator endpoints follow the project's previously
approved public scrape policy. Production must place them behind an ingress or
network perimeter; they must not be exposed directly to the public Internet.

## 3. Rate-Limit Review

Analytics routes are not excluded from the global REST token bucket.

| Setting | Default | Production rule |
| --- | ---: | --- |
| Capacity | 120 requests | Tune from observed traffic |
| Refill | 120 per 60 seconds | Tune with the same unit |
| Key | Client IP | Trust forwarded IP only behind a controlled proxy |
| Store | In-memory | Redis is mandatory when production rate limiting is enabled |
| Store failure | Fail closed with `503` | Alert on `store_error` |

The six read endpoints therefore receive the same default IP-level protection
as other REST endpoints. A separate per-user or cost-weighted Analytics quota
is not implemented. The query guardrails below are the second line of defense
against expensive requests.

## 4. Query Guardrails

| Guardrail | Bound |
| --- | --- |
| General time range | At most 366 days |
| Heatmap time range | At most 31 days |
| Heatmap cell sizes | 250, 500, 1,000 or 2,000 metres |
| Heatmap bounding-box area | 25,000 km² by default |
| Heatmap payload | At most 5,000 cells |
| Service area | Positive existing ID |
| Transaction | Read-only, repeatable read, 15-second timeout |
| Materialized mismatch | Exact direct fallback or explicit `503` |

The heatmap query asks SQL for one row above the configured cap and rejects the
whole response rather than silently truncating spatial data.

## 5. Observability Inventory

| Signal | Tags | Purpose |
| --- | --- | --- |
| `goride.analytics.query.duration` | `queryType`, `sourceVariant`, `outcome` | Endpoint latency and success/error distribution |
| `goride.analytics.query.errors` | `queryType`, `sourceVariant`, `outcome` | Analytics request failures |
| `goride.analytics.materialized.freshness.seconds` | none | Age of the latest materialized cutoff observed by the process |
| `goride.analytics.materialized.refresh` | `outcome` | Refresh success, failure and lock-skip count |
| `goride.analytics.materialized.refresh.duration` | none | Refresh duration |
| `goride.analytics.materialized.fallback` | none | Safe materialized-to-direct fallback |
| `goride.analytics.supply.snapshot.captured` | none | Successful supply snapshots |
| `goride.analytics.supply.snapshot.skipped` | `reason` | Incomplete Redis reads not persisted |
| `goride.matching.telemetry.failures` | `stage` | Durable matching telemetry failures |
| `goride.rate.limit.requests` | `outcome` | Allowed, rejected and store-error REST requests |

Allowed Analytics query tag values are bounded enums. Identifiers such as
`tripId`, `driverId`, `cellId`, request IDs and exception messages are
deliberately absent.

Query completion logs include query type, range, timezone, selected source,
duration, result row count and refresh cutoff. Failure logs include the bounded
query context and exception class only.

The freshness gauge is process-observed state and may be `NaN` immediately
after a restart. The persisted
`analytics.materialized_refresh_state` row remains the authoritative refresh
state.

## 6. Operational Alert Suggestions

These are operational starting points, not thesis results:

- alert when materialized refresh failures increase;
- alert when refresh freshness exceeds the deployment SLA;
- alert when Analytics error or fallback counts increase;
- alert when the P95 query duration approaches the 15-second transaction
  timeout;
- alert on skipped supply snapshots and matching telemetry failures;
- alert on sustained rate-limit rejection or store failure.

Thresholds must be calibrated from staging/production traffic. The repository
does not claim that one threshold is valid for every deployment.

## 7. Database Release Review

The repository validator passed every release folder. An isolated
PostgreSQL/PostGIS test also applied and verified the Admin Analytics release
chain in dependency order, then rolled it back in reverse order:

1. `20260727-admin-analytics-telemetry`;
2. `20260729-admin-analytics-spatial-indexes`;
3. `20260729-admin-analytics-materialized`.

Production still requires the checklist in
[the database release process](../database-release-process.md): backup,
environment precheck, apply, verify output retention, API smoke test and a
prepared rollback.
