# GoRide Project Completion Plan

Generated: 2026-06-15, Asia/Bangkok

## Project Snapshot

| Item | Status |
| --- | --- |
| Working branch | `feature/production-health-readiness` |
| Latest merged feature on develop | `feature/trip-completion-payment-integration` |
| Develop merge commit | `merge: trip completion payment integration` |
| Test status | Full `./mvnw.cmd test` passed 338 tests on 2026-06-24; targeted `HealthReadinessIntegrationTests` passed 1 test on 2026-06-27; backend CI workflow added locally, awaiting first GitHub run |
| Diff hygiene | `git diff --check`: pass on 2026-06-25 |
| CodeRabbit CLI | Blocked: CLI missing; installer via Git Bash reports unsupported OS `mingw64_nt-10.0-26200` |
| Publish status | Trip completion/payment integration merged to `develop`; current health/readiness feature is in review flow |
| Local config | `src/main/resources/application.yml` is environment-specific and must stay uncommitted |

## Completed Backend Modules

| Module | Completed Scope | API / Channel | Notes |
| --- | --- | --- | --- |
| Auth and JWT | Register, login, refresh token, logout, JWT generation/validation, role-aware security context | `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` | Backend foundation is ready for passenger, driver, and admin sign-in flows. |
| User profile | Current user profile, update profile, admin user list/detail/create/update/status controls | `GET/PUT /api/users/me`, admin user endpoints under `/api/users` | Covers user account management and admin CRUD workflows. |
| Driver profile and availability | Driver profile creation/update, document/profile data, admin approval flow, online/offline status, heartbeat refresh and automatic stale-driver timeout | `/api/v1/drivers/me/profile`, `/api/v1/drivers/me/status`, `POST /api/v1/drivers/me/heartbeat`, admin approval endpoints | Redis TTL removes stale drivers from matching immediately; a scheduled batch synchronizes expired online state back to the database. |
| Pricing and routing | Fare estimate, OSRM-compatible route distance/duration, driver navigation GeoJSON/steps, configurable estimate fallback, pricing configuration and admin pricing management | `/api/v1/bookings/estimate`, `POST /api/v1/drivers/trips/{tripId}/route`, `/api/v1/pricing`, `/api/v1/admin/pricing` | Estimate/create booking can use routed distance/time; assigned drivers can request pickup/dropoff routes; completed-trip fare still uses actual tracking history. |
| Booking | Create booking, booking detail, passenger history/listing, cancellation rules/status updates | `/api/v1/bookings` and related detail/cancel/list endpoints | Booking lifecycle is connected to matching and trip creation. |
| Trip lifecycle | Driver response, arrived/start/complete transitions, passenger/driver trip history, payment confirmation hooks | `/api/v1/drivers/trips/{tripId}/respond`, `/status`, `/payment-confirm` | Trip completion can compute final fare from tracking history. |
| Matching | Nearby driver lookup, offer dispatch, accept/reject handling, timeout handling, retry/no-driver flow, rematch on driver availability | Internal matching services and driver availability events | Initial no-candidate booking remains `SEARCHING`; driver reject/offer timeout also keeps the trip searchable when no immediate next driver is available; driver online/heartbeat events retry unmatched searching trips and dispatch offers when a candidate becomes available; passenger cancellation clears active matching state/driver lock and dismisses the stale driver offer. |
| WebSocket security | JWT-authenticated STOMP `CONNECT`, trip topic authorization, user-specific messaging, SockJS/native endpoint compatibility | `/ws` for SockJS, `/ws-native` for native STOMP, trip subscription topics | `GET /ws/info` is supported for SockJS clients while native clients retain a dedicated endpoint. |
| Realtime tracking | Driver location updates from accepted trip onward, REST fallback for latest location/history, trip location notifications | `POST /api/v1/tracking/trips/{tripId}/driver-location`, WebSocket driver location channel | Caches/broadcasts driver location for `ACCEPTED`, `ARRIVED`, `IN_PROGRESS`; persists history for fare only during `IN_PROGRESS`. |
| Cash payment | Cash payment record, payment detail, cash confirmation, payment completion workflow | `/api/v1/payments/trips/{tripId}`, driver payment confirmation endpoint | CASH path is implemented end to end enough for MVP trip completion. |
| Payment checkout and webhook foundation | Payment provider registry/config properties, checkout entry point, webhook entry point, payment method metadata, signed VNPAY and MoMo checkout/callback flows, configurable callback freshness/replay policy | `/api/v1/payments/methods`, `/api/v1/payments/providers/readiness`, `/api/v1/payments/trips/{tripId}/checkout`, `/api/v1/payments/providers/{providerName}/webhook` | CASH works end to end; VNPAY and MoMo support signed checkout/callbacks, readiness diagnostics, service-level sandbox callback contract coverage, stale/future callback rejection and idempotent terminal retries. |
| Rating | Passenger trip rating, duplicate prevention/status, driver public ratings, Redis rating sync | `/api/v1/ratings`, `/api/v1/ratings/trips/{tripId}/me`, `/api/v1/drivers/{driverId}/ratings` | Rating data is available for frontend review displays. |
| Notifications | Notification inbox, mark-read flow, in-app/WebSocket delivery, FCM token CRUD, Firebase Admin sender, ADC/service-account credential loading and startup validation | `/api/v1/notifications`, FCM token endpoints | FCM stays disabled by default; enabled deployments fail startup clearly when credentials are unavailable and never require committed credential JSON. |
| Admin trip operations | Admin trip list/filter/detail-like views and dashboard metrics | `/api/v1/admin/trips`, `/api/v1/admin/dashboard` | Supports basic operations dashboard and trip monitoring. |
| Integration test foundation | Docker-backed PostGIS/Redis base, full auth HTTP flow, booking-to-driver-routing flow, trip completion/payment flow, notification inbox/FCM token flow, admin dashboard/pricing/driver approval flow, and GitHub Actions backend CI wiring | `AuthFlowIntegrationTests`, `BookingMatchingRoutingIntegrationTests`, `NotificationFlowIntegrationTests`, `AdminFlowIntegrationTests`, `.github/workflows/backend-ci.yml` | Covers auth, booking/matching/routing, tracking/payment, Redis FCM token CRUD, notification inbox, admin RBAC, driver approval, pricing management, trip list and dashboard through the real Spring stack; CI now runs `./mvnw test` on Docker-enabled GitHub runners. |
| Request tracing, error logging and health checks | HTTP correlation ID, response trace header/body, request completion logs, centralized exception/security logs, Actuator health/liveness/readiness/info endpoints | `X-Request-Id`, all REST endpoints, `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info` | Logs method/path/status/duration without request bodies or secrets; unexpected 5xx errors retain stack traces; readiness checks DB/Redis while liveness only reflects app state. |
| Documentation | Implementation log and frontend integration plan | `docs/implementation-log.md`, `integrate-plan.md` | Living docs describe commit history and integration contracts. |

## Unfinished Work

| Priority | Area | Work To Complete | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| P0 | Payment providers | Finish MoMo/VNPAY sandbox E2E validation against real merchant flows; readiness endpoint and service-level signed callback contract tests now verify backend before UAT | Sandbox merchant accounts, callback URLs, provider test apps | Both online providers report `sandboxReady=true`, return usable checkout URLs and real sandbox success/failure callbacks reconcile internal payment/trip state. |
| P0 | Webhook sandbox handling | Run real sandbox callback tests for MoMo and VNPAY; service-level success/failure/stale callback contract coverage is implemented | Sandbox callback payloads and merchant test accounts | Sandbox success/failure statuses map to internal payment states and provider acknowledgements meet real gateway expectations. |
| P1 | E2E/integration tests | Auth, booking/matching/pickup-dropoff routing, trip completion, tracking fallback, cash payment confirmation, notification inbox/FCM token flow, admin flow and GitHub Actions backend CI wiring are covered; add provider sandbox E2E flow | Existing Testcontainers base, sandbox merchant accounts | Remaining provider sandbox happy paths run in CI/staging against database/Redis-compatible services and real provider sandbox callbacks. |
| P1 | Production hardening | Request correlation, centralized error logging and Actuator health/readiness endpoints are implemented; add centralized log shipping, metrics/tracing backend, rate limits and CORS policy | Deployment platform requirements | API has safe production defaults and operational visibility across instances; deployment can use public liveness/readiness probes. |
| P1 | Database release strategy | Define production database migration/release SQL workflow without Flyway unless project policy changes | DBA/deployment convention | Schema changes are reproducible across environments and documented per release. |
| P2 | Upload storage | Add driver document/avatar upload storage | S3-compatible storage, local dev storage, file validation | Driver can upload required files; admin can view verified document URLs. |
| P2 | In-trip messaging | Add passenger-driver chat during active trip | WebSocket channel policy, persistence decision | Participants can exchange trip-scoped messages; unauthorized users cannot subscribe/send. |
| P2 | Scheduled rides | Support future pickup time and scheduled dispatch | Scheduler, matching delay policy, cancellation rules | Passenger can create scheduled ride; dispatch starts at configured lead time. |
| P2 | Surge pricing | Add dynamic surge rules beyond static pricing config | Demand/supply metrics, admin controls | Fare estimate reflects surge multiplier with transparent breakdown. |
| P2 | Multi-city/service area | Add city/service zone configuration | Geofence data and admin controls | Bookings outside active service zones are rejected or handled according to policy. |
| P2 | Analytics | Add richer operational analytics/exporting | Event model and reporting store | Admin can inspect demand, conversion, revenue, cancellation, and driver utilization trends. |

## Frontend Integration Checklist

| Screen / Flow | Backend Endpoint / Channel | Frontend Action |
| --- | --- | --- |
| Auth | `/api/v1/auth/register`, `/login`, `/refresh`, `/logout` | Store access token safely, refresh before expiry, clear local session on logout/401. |
| Passenger profile | `/api/users/me` | Load and update current passenger profile. |
| Driver onboarding | `/api/v1/drivers/me/profile` | Submit driver profile/document metadata and display approval status. |
| Driver availability | `PATCH /api/v1/drivers/me/status`, `POST /api/v1/drivers/me/heartbeat` | Start heartbeat after going online, send current coordinates before the returned expiry, and return to the online action when heartbeat reports `DRIVER_NOT_AVAILABLE`. |
| Admin user management | `/api/users` admin endpoints | Build list, filter/search, create/update, status controls. |
| Admin driver approval | `/api/v1/admin/drivers/pending`, approval endpoint | Review pending drivers and approve/reject with reason. |
| Fare estimate | `/api/v1/bookings/estimate` | Show fare/distance/time estimate before booking creation. |
| Passenger booking | `/api/v1/bookings` | Create booking, show matching progress, allow cancel when allowed. |
| Driver offers | Driver trip offer APIs and user-specific WebSocket notifications | Display incoming offer countdown, accept/reject, handle timeout, and close stale offer modal when `/user/queue/trip-requests` receives `TRIP_CANCELLED`/`DISMISS`. |
| Driver navigation | `POST /api/v1/drivers/trips/{tripId}/route` | Send current GPS, draw returned GeoJSON `LineString`, route to pickup while accepted and dropoff after arrival, then re-route only when movement/time threshold is reached. |
| Trip status | Driver trip status endpoints and trip WebSocket topic | Render status timeline: accepted, arrived, in progress, completed/cancelled. |
| Realtime tracking | `/ws` SockJS or `/ws-native` native STOMP location topic, REST fallback tracking endpoints | Subscribe for live driver location after a driver is assigned; passenger can display driver approach during `ACCEPTED`/`ARRIVED` and trip movement during `IN_PROGRESS`. |
| Payment | `/api/v1/payments/methods`, `/api/v1/payments/trips/{tripId}`, `/checkout`, webhook-driven state | Support CASH fully; show VNPAY/MoMo only when provider metadata says enabled; open returned checkout URL and refresh payment detail after redirect while signed callbacks update state. |
| Rating | `/api/v1/ratings`, rating status/list endpoints | Prompt passenger after completed trip, hide form after already rated. |
| Notifications | `/api/v1/notifications`, FCM token endpoints, WebSocket notification channel | Register FCM token, render inbox/badge, mark notifications read. |
| Admin trips/dashboard | `/api/v1/admin/trips`, `/api/v1/admin/dashboard` | Build admin operational dashboard and trip filter pages. |
| Error/support flow | All REST endpoints, `X-Request-Id` response header and error body | Generate a request ID per call and retain `requestId`, endpoint, time and `error.code` when reporting failures. |

## Suggested Roadmap

| Phase | Goal | Main Deliverables |
| --- | --- | --- |
| Phase 1 | Payment provider completion | MoMo/VNPAY sandbox E2E validation, provider-specific freshness-window tuning, service-level callback contract coverage and real merchant callback tests. |
| Phase 2 | Real-world routing | Fare estimation and assigned-driver pickup/dropoff GeoJSON routing implemented; production endpoint UAT, route request rate control, monitoring and timeout tuning remain. |
| Phase 3 | Driver availability reliability | Heartbeat API, Redis TTL refresh and automatic database offline timeout implemented; production interval tuning and Redis/database soak testing remain. |
| Phase 4 | Production readiness | Firebase secure credential loading, HTTP request correlation, centralized application error logging and Actuator health/readiness endpoints implemented; log aggregation, metrics/tracing backend, environment profiles, CORS/rate limits and release SQL strategy remain. |
| Phase 5 | Integration confidence | Testcontainers PostGIS/Redis foundation, auth flow, booking-to-matching-to-pickup/dropoff-routing flow, trip completion, tracking fallback, cash payment confirmation, notification inbox/FCM token flow, admin flow and backend CI workflow are implemented; provider sandbox E2E validation remains. |
| Phase 6 | Product expansion | Uploads, messaging, scheduled rides, surge pricing, multi-city, analytics. |

## Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Online payment providers are still not production-complete | Frontend should not expose MoMo/VNPAY in production without real sandbox/UAT validation | Keep CASH as MVP; use readiness plus service-level signed callback tests before UAT, then enable online methods only after full real sandbox callback validation. |
| Webhook freshness defaults need sandbox validation | The 24-hour age and 5-minute future-skew defaults are configurable but not yet calibrated against real merchant retries | Validate both gateways in sandbox; service-level tests already cover signed success/failure/stale callbacks, then tune provider-specific windows without weakening signature or transaction-reference checks. |
| Routing endpoint is not production-calibrated | Fare estimates and driver navigation geometry are implemented, but endpoint capacity, route request rate and Vietnamese road quality are not validated | Use a controlled OSRM-compatible endpoint, debounce/re-route on FE, add rate/latency metrics, and tune timeout/profile during UAT. |
| Heartbeat timing is not production-calibrated | Aggressive intervals may create reconnect churn; loose intervals delay database cleanup | Start with a 20-second client heartbeat and 60-second timeout, then tune from staging disconnect and scheduler metrics. |
| Firebase credential path still needs staging UAT | Credential loading is production-ready, but the real deployment identity/secret mount has not been exercised in this repository | Prefer attached workload identity/ADC; otherwise mount the JSON outside the image, set `GOOGLE_APPLICATION_CREDENTIALS`, and verify startup plus one test push in staging. |
| Integration coverage is partial | Auth, booking/matching/driver-routing, trip completion, tracking fallback, cash payment confirmation, notification inbox/FCM token flow and admin flow now run through HTTP/JWT/JPA/Redis, and GitHub Actions runs the suite on pushes/PRs, but real provider sandbox regressions may still slip through | Reuse the Testcontainers base for remaining provider flows and validate real gateway callbacks before release candidate. |
| Logs are local to each application instance | Request IDs and health probes improve diagnosis, but logs can still be lost or fragmented across replicas | Ship stdout logs to a centralized platform and add retention, search, alert and health-probe dashboard rules before production scaling. |
| FE uses the wrong WebSocket transport URL | SockJS calls to a native-only endpoint fail at `/ws/info`; native clients pointed at a SockJS root also fail | Use `/ws` with SockJS and `/ws-native` with native STOMP exactly as documented in `integrate-plan.md`. |
