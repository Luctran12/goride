# GoRide Project Completion Plan

Generated: 2026-06-11, Asia/Bangkok

## Project Snapshot

| Item | Status |
| --- | --- |
| Working branch | `develop` |
| Feature merged | `feature/payment-provider-config` |
| Develop merge commit | `0fda13c` (`merge: payment provider config foundation`) |
| Test status | Pending final validation in this publish flow with `./mvnw.cmd test` |
| Diff hygiene | Pending final validation with `git diff --check` |
| CodeRabbit CLI | Pending check; record unavailable if CLI is not installed |
| Publish status | Pending: merge `develop` into `main`, then push `develop` and `main` |
| Local config | `src/main/resources/application.yml` is environment-specific and must stay uncommitted |

## Completed Backend Modules

| Module | Completed Scope | API / Channel | Notes |
| --- | --- | --- | --- |
| Auth and JWT | Register, login, refresh token, logout, JWT generation/validation, role-aware security context | `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` | Backend foundation is ready for passenger, driver, and admin sign-in flows. |
| User profile | Current user profile, update profile, admin user list/detail/create/update/status controls | `GET/PUT /api/users/me`, admin user endpoints under `/api/users` | Covers user account management and admin CRUD workflows. |
| Driver profile | Driver profile creation/update, document/profile data, admin approval flow, driver online/offline status | `/api/v1/drivers/me/profile`, `/api/v1/drivers/me/status`, `/api/v1/admin/drivers/pending`, approval endpoints | Driver availability is stored in Redis and tied into matching. |
| Pricing | Fare estimate foundation, pricing configuration, admin pricing management | `/api/v1/bookings/estimate`, `/api/v1/pricing`, `/api/v1/admin/pricing` | Supports base fare, distance/time inputs, and admin-maintained pricing config. |
| Booking | Create booking, booking detail, passenger history/listing, cancellation rules/status updates | `/api/v1/bookings` and related detail/cancel/list endpoints | Booking lifecycle is connected to matching and trip creation. |
| Trip lifecycle | Driver response, arrived/start/complete transitions, passenger/driver trip history, payment confirmation hooks | `/api/v1/drivers/trips/{tripId}/respond`, `/status`, `/payment-confirm` | Trip completion can compute final fare from tracking history. |
| Matching | Nearby driver lookup, offer dispatch, accept/reject handling, timeout handling, retry/no-driver flow | Internal matching services and driver offer APIs | Uses Redis GEO/availability data to dispatch ride offers. |
| WebSocket security | JWT-authenticated STOMP `CONNECT`, trip topic authorization, user-specific messaging | WebSocket/STOMP endpoints and trip subscription topics | Prevents unauthorized trip subscription access. |
| Realtime tracking | Driver location updates, REST fallback for latest location/history, trip location notifications | `POST /api/v1/tracking/trips/{tripId}/driver-location`, WebSocket driver location channel | Stores latest driver location in Redis and tracking history in persistence. |
| Cash payment | Cash payment record, payment detail, cash confirmation, payment completion workflow | `/api/v1/payments/trips/{tripId}`, driver payment confirmation endpoint | CASH path is implemented end to end enough for MVP trip completion. |
| Payment checkout foundation | Payment provider registry/config properties, checkout entry point, webhook entry point, provider config groundwork | `/api/v1/payments/trips/{tripId}/checkout`, `/api/v1/payments/providers/{providerName}/webhook` | Structure exists for MoMo/VNPay, but providers are not fully implemented yet. |
| Rating | Passenger trip rating, duplicate prevention/status, driver public ratings, Redis rating sync | `/api/v1/ratings`, `/api/v1/ratings/trips/{tripId}/me`, `/api/v1/drivers/{driverId}/ratings` | Rating data is available for frontend review displays. |
| Notifications | Notification inbox, mark-read flow, in-app/WebSocket notification delivery, FCM token CRUD | `/api/v1/notifications`, FCM token endpoints | Includes FCM sender channel and invalid-token cleanup path. |
| Admin trip operations | Admin trip list/filter/detail-like views and dashboard metrics | `/api/v1/admin/trips`, `/api/v1/admin/dashboard` | Supports basic operations dashboard and trip monitoring. |
| Documentation | Implementation log and frontend integration plan | `docs/implementation-log.md`, `integrate-plan.md` | Living docs describe commit history and integration contracts. |

## Unfinished Work

| Priority | Area | Work To Complete | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| P0 | Payment providers | Implement real MoMo and VNPay providers instead of provider-config foundation only | Sandbox merchant accounts, provider SDK/API specs, callback URLs | Checkout returns real provider redirect/deeplink; successful sandbox payment updates GoRide payment/trip state; failed/cancelled payments are persisted correctly. |
| P0 | Payment method exposure | Expose supported payment methods/provider metadata to frontend | Provider configuration and enabled/disabled flags | FE can render CASH/MoMo/VNPay options from backend without hardcoding availability. |
| P0 | Webhook security | Verify provider webhook signatures and request freshness | Provider secret keys, canonical signature algorithms | Invalid signatures are rejected; replay/expired callbacks are rejected; valid callbacks are idempotent. |
| P0 | Webhook sandbox handling | Complete callback payload parsing for MoMo/VNPay sandbox flows | Sandbox callback payload examples | Sandbox success/failure/refund-like statuses map to internal payment states with tests. |
| P0 | Maps and distance | Replace `MockDistanceService` with a real maps/distance provider | Google Maps, Mapbox, HERE, or local routing service key | Estimates and completed-trip fare use real route distance/time; provider failures fall back predictably. |
| P1 | Driver heartbeat | Add periodic driver heartbeat and automatic offline timeout | Redis TTL policy, scheduler/job config | Drivers who stop heartbeating become unavailable for matching without manual offline action. |
| P1 | Firebase production setup | Finalize Firebase Admin service account and secret loading for production | Secure secret storage, env-specific config | FCM works in staging/prod without committing credentials; missing secrets fail clearly. |
| P1 | E2E/integration tests | Add integration tests for auth, booking, matching, tracking, payment, notification, and admin flows | Testcontainers or stable local test profile | Critical passenger/driver/admin flows pass in CI against database/Redis-compatible services. |
| P1 | Production hardening | Add rate limits, observability, CORS policy, error audit, health/readiness checks | Deployment platform requirements | API has safe production defaults and operational visibility. |
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
| Admin user management | `/api/users` admin endpoints | Build list, filter/search, create/update, status controls. |
| Admin driver approval | `/api/v1/admin/drivers/pending`, approval endpoint | Review pending drivers and approve/reject with reason. |
| Fare estimate | `/api/v1/bookings/estimate` | Show fare/distance/time estimate before booking creation. |
| Passenger booking | `/api/v1/bookings` | Create booking, show matching progress, allow cancel when allowed. |
| Driver offers | Driver trip offer APIs and user-specific WebSocket notifications | Display incoming offer countdown, accept/reject, handle timeout. |
| Trip status | Driver trip status endpoints and trip WebSocket topic | Render status timeline: accepted, arrived, in progress, completed/cancelled. |
| Realtime tracking | WebSocket location topic, REST fallback tracking endpoints | Subscribe for live driver location and poll REST fallback on reconnect. |
| Payment | `/api/v1/payments/trips/{tripId}`, `/checkout`, webhook-driven state | For now support CASH fully; show online payment options only when backend provider metadata says enabled. |
| Rating | `/api/v1/ratings`, rating status/list endpoints | Prompt passenger after completed trip, hide form after already rated. |
| Notifications | `/api/v1/notifications`, FCM token endpoints, WebSocket notification channel | Register FCM token, render inbox/badge, mark notifications read. |
| Admin trips/dashboard | `/api/v1/admin/trips`, `/api/v1/admin/dashboard` | Build admin operational dashboard and trip filter pages. |

## Suggested Roadmap

| Phase | Goal | Main Deliverables |
| --- | --- | --- |
| Phase 1 | Payment provider completion | MoMo/VNPay checkout, webhook signature verification, payment method metadata, sandbox tests. |
| Phase 2 | Real-world routing | Real distance/maps provider, route estimate tests, fallback and provider error handling. |
| Phase 3 | Driver availability reliability | Heartbeat API/job, automatic offline timeout, matching tests around stale drivers. |
| Phase 4 | Production readiness | Firebase secrets, environment profiles, CORS/rate limits, observability, release SQL strategy. |
| Phase 5 | Integration confidence | Testcontainers/integration tests, end-to-end passenger/driver/admin happy paths, CI validation. |
| Phase 6 | Product expansion | Uploads, messaging, scheduled rides, surge pricing, multi-city, analytics. |

## Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Online payment providers are only foundations today | Frontend cannot safely launch MoMo/VNPay production payments | Keep CASH as MVP, finish sandbox providers before exposing online methods. |
| Webhook signatures are not fully verified | Payment state could be spoofed in production | Implement provider-specific signature verification and idempotency before public callback URLs go live. |
| Mock distance provider remains in use | Fare estimates may be inaccurate | Integrate real maps provider before real-money launch. |
| Drivers can stay online without heartbeat | Matching may dispatch to unavailable drivers | Add heartbeat/offline timeout before broad driver testing. |
| Production secrets are not finalized | Deployments may fail or leak credentials if handled manually | Use environment-specific secret storage and never commit provider/Firebase credentials. |
| Integration coverage is still limited | Regressions may slip across auth/booking/matching/payment flows | Add focused integration tests and CI before release candidate. |
