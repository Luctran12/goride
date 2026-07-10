# GoRide Front-end Integration Plan

Branch dang cap nhat: `feature/service-area-zones`

Muc tieu file nay:
- Checklist chuc nang backend da co code va co the tich hop FE.
- Checklist chuc nang con thieu hoac can commit tiep.
- Huong dan FE goi REST API va ket noi WebSocket theo tung flow.

---

## 1. Quy uoc chung cho FE

### Base URL

```text
API_BASE_URL=http://localhost:8080
WS_URL=ws://localhost:8080/ws
```

### Response thanh cong

Tat ca REST API thanh cong tra ve envelope:

```json
{
  "success": true,
  "data": {},
  "message": "OK",
  "timestamp": "2026-05-27T10:00:00Z"
}
```

Voi create:

```json
{
  "success": true,
  "data": {},
  "message": "Created",
  "timestamp": "2026-05-27T10:00:00Z"
}
```

### Response loi

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request is invalid",
    "details": {}
  },
  "requestId": "fe-booking-550e8400",
  "timestamp": "2026-05-27T10:00:00Z"
}
```

FE nen map `error.code` thay vi chi doc text message.

### Request tracing

- FE co the gui header `X-Request-Id` tren moi REST request. Gia tri nen la UUID hoac ID duy nhat gom toi da 64 ky tu chu, so, `.`, `_`, `:`, `-`.
- Backend luon tra `X-Request-Id` trong response header; neu FE khong gui hoac gui gia tri khong an toan, backend tu sinh UUID.
- Error response cung co field `requestId`. Khi hien man loi/support, FE nen luu `requestId`, endpoint, thoi gian va `error.code` de backend tim dung log.
- Log request completion co MDC fields `requestId`, `http.request.method`, `url.path`, `http.response.status_code`, `event.duration_ms` de log collector search/correlation tot hon khi bat structured logging.
- Khong dua access token, refresh token, password hoac thong tin nhay cam vao `X-Request-Id`.


### CORS cho web frontend

Backend bat CORS theo allowlist, mac dinh cho cac local web origins pho bien: `http://localhost:3000`, `http://localhost:5173`, `http://localhost:19006`, `http://127.0.0.1:5173`.

Runtime config:

```properties
CORS_ALLOWED_ORIGINS=http://localhost:5173,https://app.example.com
CORS_ALLOWED_ORIGIN_PATTERNS=
CORS_ALLOWED_METHODS=GET,POST,PUT,PATCH,DELETE,OPTIONS
CORS_ALLOWED_HEADERS=Authorization,Content-Type,X-Request-Id
CORS_EXPOSED_HEADERS=X-Request-Id,Retry-After,X-RateLimit-Limit,X-RateLimit-Remaining,X-RateLimit-Reset
CORS_ALLOW_CREDENTIALS=false
CORS_MAX_AGE_SECONDS=3600
```

FE action:
- Web FE phai chay tren origin nam trong `CORS_ALLOWED_ORIGINS` hoac duoc match boi `CORS_ALLOWED_ORIGIN_PATTERNS`.
- FE co the doc response header `X-Request-Id`, `Retry-After` va `X-RateLimit-*` vi backend expose cac header nay qua CORS.
- Neu browser bao CORS/preflight failed, kiem tra origin frontend thuc te va bien moi truong `CORS_ALLOWED_ORIGINS` cua backend.
- Native mobile app thuong khong bi browser CORS, nhung web build va admin dashboard se can allowlist nay.
- Production readiness guardrail: khi `APP_ENV=production` hoac `APP_ENV=prod`, backend se fail startup neu `CORS_ALLOWED_ORIGINS`/`CORS_ALLOWED_ORIGIN_PATTERNS` con chua `localhost`, `127.*`, `0.0.0.0`, IPv6 loopback `::1` hoac wildcard `*`. FE web/admin production phai dung origin HTTPS that trong allowlist.

### Rate limit cho REST API

Backend bat token-bucket rate limit mac dinh cho REST API, theo client IP. Cac endpoint diagnostics/docs/WebSocket mac dinh duoc exclude: `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/ws/**`, `/ws-native/**`.

Runtime config:

```properties
RATE_LIMIT_ENABLED=true
RATE_LIMIT_CAPACITY=120
RATE_LIMIT_REFILL_TOKENS=120
RATE_LIMIT_REFILL_PERIOD_SECONDS=60
RATE_LIMIT_MAX_KEYS=10000
RATE_LIMIT_EXCLUDED_PATHS=/actuator/**,/v3/api-docs/**,/swagger-ui/**,/swagger-ui.html,/ws/**,/ws-native/**
RATE_LIMIT_USE_FORWARDED_FOR=false
```

Khi vuot gioi han, backend tra HTTP 429:

```json
{
  "success": false,
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Too many requests",
    "details": {
      "retryAfterSeconds": 60
    }
  },
  "requestId": "fe-login-123",
  "timestamp": "2026-06-28T04:45:00Z"
}
```

Response headers lien quan:

```http
Retry-After: 60
X-RateLimit-Limit: 120
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1782621960
```

FE action:
- Khi gap HTTP 429 hoac `error.code=RATE_LIMIT_EXCEEDED`, dung retry tuc thi va schedule retry theo `Retry-After` hoac `error.details.retryAfterSeconds`.
- Hien thong bao tam thoi, vi du "He thong dang nhan qua nhieu yeu cau, thu lai sau it giay".
- Giu `requestId`, endpoint va thoi gian request de backend trace log.
- Voi login/register/booking, disable nut submit trong thoi gian backoff de tranh tao retry storm.

### Auth header

Tru cac API public, gui:

```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

API public hien co:
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/drivers/{driverId}/ratings`
- `GET /api/v1/payments/methods`
- `POST /api/v1/payments/providers/{providerName}/webhook`
- `GET /api/v1/payments/providers/{providerName}/webhook`
- Swagger/OpenAPI routes

### Enum FE can dong bo

```ts
type UserRole = "PASSENGER" | "DRIVER" | "ADMIN";
type VehicleType = "MOTORBIKE" | "CAR_4_SEAT" | "CAR_7_SEAT";
type PaymentMethod = "CASH" | "MOMO" | "VNPAY";
type TripStatus =
  | "SCHEDULED"
  | "SEARCHING"
  | "ACCEPTED"
  | "ARRIVED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED"
  | "NO_DRIVER";
type DriverOfferDecision = "ACCEPT" | "REJECT";
type ApprovalStatus = "PENDING" | "APPROVED" | "REJECTED";
type PaymentStatus = "PENDING" | "COMPLETED" | "FAILED" | "REFUNDED";
type PaymentSandboxUatStatus = "NOT_RUN" | "BLOCKED" | "FAILED" | "PASSED";
```

---

## 2. Checklist chuc nang da hoan thien

### Auth

- [x] Dang ky user voi role `PASSENGER`, `DRIVER`, `ADMIN`.
- [x] Dang nhap bang phone/password, tra `accessToken`, `refreshToken`, `roles`.
- [x] Refresh access token bang refresh token.
- [x] Logout/xoa refresh token.
- [x] JWT REST security theo role qua `@PreAuthorize`.

### Driver profile va trang thai online

- [x] Driver tao profile ca nhan/xe.
- [x] Driver xem profile cua minh.
- [x] Driver bat/tat online.
- [x] Khi online, backend ghi vi tri + metadata driver vao Redis.
- [x] Khi offline, backend xoa status/meta khoi Redis.
- [x] Driver chi online duoc neu profile da `APPROVED`.
- [x] Driver gui heartbeat REST de cap nhat vi tri va gia han Redis TTL.
- [x] Heartbeat giu nguyen status `BUSY`, khong dua driver dang chay trip ve `AVAILABLE`.
- [x] Redis status het TTL thi driver khong con duoc matching chon.
- [x] Scheduler theo batch chuyen profile DB stale sang offline va don Redis GEO/meta.

### User profile va admin user management

- [x] User xem profile cua minh.
- [x] User cap nhat profile cua minh.
- [x] User doi mat khau.
- [x] Admin tao/xem/list/cap nhat/xoa user.
- [x] Admin suspend/activate user qua `status`.


### Upload storage

- [x] User upload avatar bang multipart `file`, backend validate content type/size, luu local dev hoac Cloudflare R2 theo config va cap nhat `avatarUrl`.
- [x] Driver upload anh portrait, license, ID card va vehicle registration bang multipart `file`.
- [x] Backend tra public URL va object key cho FE; FE dua URL vao driver profile request.
- [x] Admin pending-driver response co cac field document URL neu driver da submit.
- [x] Local `/uploads/**` public resource serving da co cho dev/test.
- [x] Cloudflare R2/S3-compatible storage provider da co; staging can cau hinh bucket/domain/API token va UAT real upload.

### Booking va trip

- [x] Public API xem pricing config active.
- [x] Admin API xem/tao pricing config version moi.
- [x] Admin API deactivate pricing config cu.
- [x] Admin API quan ly dynamic surge pricing rules.
- [x] Passenger tinh gia uoc luong voi breakdown base fare, surge amount va multiplier.
- [x] Backend validate pickup/dropoff trong cung active service area truoc khi tinh gia/tao booking; neu chua co active zone nao thi flow hien tai van duoc phep.
- [x] Passenger tao booking.
- [x] Passenger tao scheduled booking bang `scheduledPickupTime`; backend giu `SCHEDULED` va tu dispatch sang matching gan gio don.
- [x] Passenger/driver xem chi tiet trip neu co quyen.
- [x] Passenger/driver xem danh sach trip cua minh.
- [x] Passenger/driver/admin huy trip neu status con cancel duoc.
- [x] Tao trip history khi tao/huy/doi status.

### Matching

- [x] Sau khi booking tuc thi created, backend tu dong tim driver gan nhat trong Redis.
- [x] Scheduled booking khong gui offer ngay; scheduler chuyen `SCHEDULED -> SEARCHING` truoc gio don theo config roi kich hoat matching.
- [x] Gui offer toi driver qua WebSocket user queue.
- [x] Khi passenger huy booking dang co offer active, backend clear matching state/driver lock va gui dismiss payload toi driver qua WebSocket user queue.
- [x] Driver accept/reject offer.
- [x] Driver accept thi trip chuyen `SEARCHING -> ACCEPTED`, driver status Redis thanh `BUSY`.
- [x] Driver reject/timeout thi backend release offer hien tai, exclude driver do va thu driver tiep theo neu co candidate kha dung.
- [x] Neu chua co driver tiep theo ngay luc do, trip van giu `SEARCHING` de rematch khi driver khac online/heartbeat; khong chuyen `NO_DRIVER` chi vi mot driver reject/timeout.

### Trip status

- [x] Driver cap nhat `ACCEPTED -> ARRIVED`.
- [x] Driver cap nhat `ARRIVED -> IN_PROGRESS`.
- [x] Driver cap nhat `IN_PROGRESS -> COMPLETED`.
- [x] Khi completed, backend tinh actual distance/duration tu tracking history va tinh `finalFare`.
- [x] Khi completed, backend tao payment `PENDING`.
- [x] Khi completed, backend tang `driver_profiles.total_trips` cua assigned driver.
- [x] Broadcast trip status qua WebSocket topic.

### Tracking realtime

- [x] Driver gui location qua STOMP `/app/driver.location`.
- [x] Backend luu location history.
- [x] Backend luu latest location vao Redis.
- [x] Backend broadcast driver location qua `/topic/trip/{tripId}/location`.
- [x] Passenger lay latest driver location qua REST.
- [x] Fare estimate va booking creation co the dung OSRM-compatible route distance/duration khi `app.routing.enabled=true`.
- [x] Routing timeout/HTTP error/NoRoute co Haversine fallback cau hinh duoc.
- [x] Assigned driver co API route tu GPS hien tai den pickup/dropoff, tra GeoJSON `LineString` va maneuver steps.

### Service area zones

- [x] Public API `GET /api/v1/service-areas` tra cac active polygon de FE ve/hint vung phuc vu.
- [x] Admin API `/api/v1/admin/service-areas` list/create/update/deactivate service area polygon.
- [x] Booking estimate/create reject pickup/dropoff ngoai active service area bang `LOCATION_OUT_OF_SERVICE_AREA`.
- [x] Rollout an toan: neu chua co active service area nao, backend khong block booking hien tai.
- [x] Neu service area bi overlap/nested, backend chap nhan booking khi pickup/dropoff co it nhat mot active area chung; FE khong can tu suy luan theo polygon dau tien.

### In-trip messaging

- [x] Passenger va assigned driver gui message text trong active trip qua REST hoac STOMP.
- [x] Backend luu message history vao `trip_messages`.
- [x] FE lay lich su qua `GET /api/v1/trips/{tripId}/messages`.
- [x] Backend broadcast message realtime qua `/topic/trip/{tripId}/messages`.
- [x] Subscribe topic messages dung cung authorization voi trip status/location: passenger cua trip, driver cua trip hoac admin.
- [x] Chi cho gui khi trip status la `ACCEPTED`, `ARRIVED` hoac `IN_PROGRESS`; status khac tra `TRIP_MESSAGE_NOT_AVAILABLE`.

### Payment cash

- [x] Tao payment record khi trip completed.
- [x] Payment record duoc tao qua `PaymentProvider` abstraction.
- [x] Co `CashPaymentProvider` cho payment method `CASH`.
- [x] Co runtime config foundation cho MoMo/VNPay provider, mac dinh disabled.
- [x] FE co the lay danh sach payment method/provider metadata tu backend.
- [x] Admin/devops co the kiem tra readiness MoMo/VNPAY sandbox truoc khi enable checkout that.
- [x] Admin/devops co payment sandbox UAT plan de xem callback endpoint, missing config va kich ban test truoc khi expose online payment.
- [x] Admin/devops co endpoint luu ket qua sandbox UAT evidence, tinh `readyForFrontendExposure` de FE biet provider nao da du dieu kien hien thi.
- [x] Admin/devops co endpoint sandbox E2E session de record tung lan test checkout URL, success/failure callback, replay va freshness evidence voi payment id/transaction ref that.
- [x] Backend reject booking neu `paymentMethod` chua duoc enable.
- [x] Co checkout foundation endpoint cho payment `PENDING`.
- [x] Co webhook foundation endpoint cho payment provider external callback.
- [x] VNPAY co signed checkout URL provider khi du config sandbox.
- [x] VNPAY webhook/callback verify `vnp_SecureHash`, merchant code va amount truoc khi cap nhat payment.
- [x] VNPAY callback success chay shared payment completion workflow; callback lap lai cung transaction reference khong chay workflow lan nua.
- [x] MoMo co create-payment provider ky HMAC-SHA256, dung stable `orderId`/`requestId` va verify chu ky + du lieu response.
- [x] MoMo checkout tra `payUrl` qua checkout API khi provider registered, enabled va du config.
- [x] MoMo IPN verify HMAC-SHA256 va doi chieu partner/order/request/amount/orderInfo truoc khi cap nhat payment.
- [x] MoMo IPN success/failure cap nhat payment idempotent; success chay shared payment completion workflow.
- [x] MoMo IPN hop le tra HTTP 204 khong co response body theo contract provider.
- [x] MoMo/VNPAY callback ap dung freshness policy tren timestamp da ky; callback qua cu/tuong lai bi reject, duplicate terminal callback cung transaction reference van idempotent.
- [x] MoMo/VNPAY webhook dispatch co service-level sandbox contract coverage cho success/failure/stale callback bang payload signed.
- [x] Driver confirm da nhan tien mat.
- [x] Payment `PENDING -> COMPLETED`, set `paidAt`.
- [x] Payment completed workflow dung chung de notify va dua driver ve `AVAILABLE`.
- [x] Passenger/driver/admin xem payment detail theo trip.
- [x] Notify passenger va driver khi payment completed.
- [x] Set Redis driver status ve `AVAILABLE` sau payment completed.

### Rating

- [x] Passenger rating driver sau trip completed.
- [x] Moi trip chi duoc rating mot lan.
- [x] Chi passenger cua trip moi rating duoc.
- [x] Passenger kiem tra trip da rating hay chua.
- [x] Cap nhat `driver_profiles.average_rating` va `total_ratings`.
- [x] Sau khi rating, sync `driver:{id}:meta.rating` trong Redis neu driver dang online.
- [x] Public API xem rating cua driver co pagination.

### Admin driver approval

- [x] Admin xem danh sach driver profile dang `PENDING`.
- [x] Admin approve driver profile.
- [x] Admin reject driver profile.
- [x] Khi reject, backend dua driver offline khoi Redis availability pool.

### WebSocket notifications

- [x] User dang ky/cap nhat FCM device token sau login.
- [x] User xoa FCM device token khi logout hoac token invalid.
- [x] FCM token duoc luu Redis theo key `fcm_token:{userId}`.
- [x] Notification ca nhan duoc route qua `UserNotificationChannel` pipeline.
- [x] FCM push channel foundation doc token Redis va build payload push.
- [x] Firebase Admin SDK sender gui mobile push khi backend duoc cau hinh service account.
- [x] Firebase production config ho tro Google Application Default Credentials va explicit service-account path.
- [x] Khi FCM enabled, backend validate Firebase luc startup va fail ro rang neu credential thieu/sai.
- [x] File `.env`, `secrets/` va `firebase-service-account*.json` duoc ignore khoi Git.
- [x] Backend tu xoa FCM token Redis khi Firebase bao token `UNREGISTERED`.
- [x] Notification ca nhan duoc luu DB de lam inbox trong app.
- [x] User xem danh sach notification inbox va mark read.
- [x] STOMP `CONNECT` authenticate bang JWT trong header `Authorization`.
- [x] STOMP `SUBSCRIBE` vao trip topic chi cho passenger/driver cua trip hoac admin.
- [x] REST API co token-bucket rate limit, tra `RATE_LIMIT_EXCEEDED` HTTP 429 va headers `Retry-After`/`X-RateLimit-*`.
- [x] Driver offer queue: `/user/queue/trip-requests`.
- [x] User notification queue: `/user/queue/notifications`.
- [x] Trip status topic: `/topic/trip/{tripId}/status`.
- [x] Trip location topic: `/topic/trip/{tripId}/location`.

---

## 3. Checklist chuc nang chua hoan thien / can lam tiep

### Pricing/surge

- [x] Dynamic surge pricing rules foundation da co: admin list/create/update/deactivate rule, xem current surge status theo `vehicleType`, estimate tra base fare/surge breakdown va trip snapshot `fareSurgeMultiplier`.
- [ ] Can UAT threshold surge tren staging de tranh gia nhay qua manh; neu can chi tiet hon thi them policy theo khu vuc/gio cao diem o commit sau.

### Payment/rating/statistics

- [x] Payment method metadata da expose `CASH`, `MOMO`, `VNPAY`; hien chi `CASH` enabled mac dinh.
- [ ] MoMo va VNPAY da co signed checkout/webhook, readiness diagnostics, sandbox UAT plan, persisted UAT evidence, session-level sandbox E2E evidence API, freshness policy va service-level sandbox callback contract tests; ca hai van can sandbox account/E2E callback test that va admin phai mark `PASSED` day du truoc khi expose FE.

### Routing/maps

- [x] Da thay mock distance bean bang OSRM-compatible routing provider cho estimate/create booking.
- [x] Da co driver trip routing API tu current GPS den pickup/dropoff theo trip status.
- [ ] Can UAT routing endpoint production/self-hosted va theo doi tan suat fallback truoc khi launch.

### Service area/multi-city

- [x] Da co backend foundation cho service area polygon va geofence validation.
- [ ] Can apply SQL release `db/releases/20260708-service-area-zones`, nhap polygon that cho thanh pho launch, UAT cac cap pickup/dropoff pho bien va quyet dinh policy hien thi boundary tren FE.

### Notification/mo rong

- [x] Backend da co ADC/env production config va startup validation cho Firebase.
- [ ] Can mount/gan workload identity that va gui test push tren staging truoc launch.

### Admin module

- [x] Admin list/create/update/delete users.
- [x] Admin suspend/activate user.
- [x] Admin pricing list/create/deactivate.
- [x] Admin list pending drivers.
- [x] Admin approve/reject driver.
- [x] Admin list trips/filter.
- [x] Admin stats/dashboard.

### Integration test backend

- [x] Co Testcontainers base dung PostGIS va Redis that cho integration test.
- [x] Auth flow da duoc test qua HTTP/JWT/JPA/Redis: register, protected request, refresh rotation, logout revocation va duplicate phone.
- [x] Booking -> Redis matching -> driver accept -> route pickup -> arrived -> route dropoff da duoc test qua full Spring HTTP flow va OSRM boundary local.
- [x] Trip start/completion va final fare da co integration flow qua `BookingMatchingRoutingIntegrationTests`.
- [x] Tracking REST fallback, payment detail, CASH checkout va driver payment-confirm da co integration flow qua `BookingMatchingRoutingIntegrationTests`.
- [x] Notification inbox + FCM token REST flow da co `NotificationFlowIntegrationTests` qua HTTP/JWT/JPA/Redis.
- [x] Admin RBAC, driver approval, pricing, trip list va dashboard da co `AdminFlowIntegrationTests` qua HTTP/JWT/JPA/PostGIS.
- [ ] Provider sandbox chua co integration flow hoan chinh voi merchant account that; backend da co admin UAT plan endpoint de dieu phoi cau hinh va kich ban test.
- [x] Basic rate-limit integration coverage da co cho login throttle va actuator exclusion.
- [x] Docker-backed integration suite da duoc cau hinh chay tren GitHub Actions CI bang `.github/workflows/backend-ci.yml`; workflow dung Java 17, Maven cache va Docker-enabled runner de chay `./mvnw test`.
- [x] Database release workflow khong dung Flyway da co `db/releases` template, manifest/precheck/apply/verify/rollback va validator PowerShell.

---

## 4. Huong dan FE tich hop tung chuc nang

### 4.0 Routing config va fare estimate

Backend giu nguyen API contract fare estimate; FE khong goi OSRM truc tiep va khong can provider API key.

```yaml
app:
  routing:
    enabled: ${ROUTING_ENABLED:false}
    base-url: ${ROUTING_BASE_URL:https://router.project-osrm.org}
    profile: ${ROUTING_PROFILE:driving}
    timeout-seconds: ${ROUTING_TIMEOUT_SECONDS:5}
    fallback-enabled: ${ROUTING_FALLBACK_ENABLED:true}
```

- Khi enabled, backend gui pickup/dropoff theo OSRM order `longitude,latitude`.
- Provider distance meter duoc tra ve FE thanh km; duration giay duoc lam tron len thanh phut.
- Neu fallback enabled, timeout/HTTP error/`NoRoute` tu provider se dung Haversine estimate de booking flow khong bi dung.
- Neu fallback disabled, estimate/create booking tra `ROUTING_PROVIDER_ERROR` HTTP 502; FE hien thong bao khong the tinh lo trinh va cho retry.
- Distance/duration nay chi la estimate truoc chuyen. Khi trip completed, final fare dung tracking history va actual duration, nhung giu `fareSurgeMultiplier` da snapshot luc booking.

#### Fare estimate response voi surge breakdown

```http
POST /api/v1/bookings/estimate
Authorization: Bearer <passengerToken>
```

Response `data` co them cac field pricing minh bach:

```json
{
  "vehicleType": "MOTORBIKE",
  "distanceKm": 5.5,
  "durationMinutes": 20,
  "baseFare": 38000,
  "estimatedFare": 47500,
  "currency": "VND",
  "pricingSurgeMultiplier": 1.0,
  "dynamicSurgeMultiplier": 1.25,
  "effectiveSurgeMultiplier": 1.25,
  "surgeAmount": 9500,
  "surge": {
    "vehicleType": "MOTORBIKE",
    "demandTrips": 3,
    "onlineDrivers": 1,
    "demandSupplyRatio": 3.0,
    "pricingSurgeMultiplier": 1.0,
    "dynamicSurgeMultiplier": 1.25,
    "effectiveSurgeMultiplier": 1.25,
    "surgeApplied": true,
    "ruleId": 10,
    "ruleName": "Peak demand"
  }
}
```

FE action:
- Hien `baseFare`, `surgeAmount` va badge surge khi `surge.surgeApplied=true` hoac `dynamicSurgeMultiplier > 1`.
- Khong tu tinh lai fare tren FE; khi tao booking backend snapshot `effectiveSurgeMultiplier` vao trip.
- Neu user quay lai man confirm sau thoi gian dai, goi estimate lai de lay surge hien tai truoc khi tao booking.

### 4.0.1 Service area zones

#### Lay danh sach vung phuc vu active

```http
GET /api/v1/service-areas
```

Response `data`:

```json
[
  {
    "id": 10,
    "name": "Ho Chi Minh Core",
    "cityName": "Ho Chi Minh",
    "countryCode": "VN",
    "active": true,
    "boundary": [
      { "lat": 10.70, "lng": 106.60 },
      { "lat": 10.70, "lng": 106.90 },
      { "lat": 10.90, "lng": 106.90 },
      { "lat": 10.90, "lng": 106.60 }
    ],
    "createdAt": "2026-07-08T10:00:00Z",
    "updatedAt": "2026-07-08T10:00:00Z"
  }
]
```

FE action:
- Goi khi mo map/booking form de ve polygon hoac hint vung phuc vu.
- Neu response rong, backend dang rollout open va khong chan booking theo service area.
- Khong tu quyet dinh hop le cuoi cung tren FE; van goi estimate/create de backend validate.

#### Admin quan ly service area

```http
GET /api/v1/admin/service-areas
POST /api/v1/admin/service-areas
PATCH /api/v1/admin/service-areas/{serviceAreaId}
PATCH /api/v1/admin/service-areas/{serviceAreaId}/deactivate
Authorization: Bearer <adminToken>
```

Request tao moi:

```json
{
  "name": "Ho Chi Minh Core",
  "cityName": "Ho Chi Minh",
  "countryCode": "VN",
  "active": true,
  "boundary": [
    { "lat": 10.70, "lng": 106.60 },
    { "lat": 10.70, "lng": 106.90 },
    { "lat": 10.90, "lng": 106.90 },
    { "lat": 10.90, "lng": 106.60 }
  ]
}
```

FE action:
- Boundary dung WGS84 `lat/lng`, toi thieu 3 diem; backend tu dong dong polygon bang diem dau.
- `PATCH` cho phep gui mot phan field can doi; `deactivate` giu record nhung khong con dung de validate booking.
- Neu admin chua apply SQL release `20260708-service-area-zones`, cac endpoint nay se loi do bang chua ton tai.

#### Loi service area khi estimate/create booking

Khi da co it nhat mot active service area, `POST /api/v1/bookings/estimate` va `POST /api/v1/bookings` yeu cau pickup/dropoff cung nam trong mot active area.

```json
{
  "success": false,
  "error": {
    "code": "LOCATION_OUT_OF_SERVICE_AREA",
    "message": "Location is outside active service areas",
    "details": {
      "location": "pickup",
      "latitude": 11.0,
      "longitude": 106.7009
    }
  },
  "requestId": "fe-booking-550e8400",
  "timestamp": "2026-07-08T10:00:00Z"
}
```

Neu pickup va dropoff nam o hai active area khac nhau, `details` co `pickupServiceAreaId`, `pickupServiceAreaName`, `dropoffServiceAreaId`, `dropoffServiceAreaName`.

FE action:
- Giu user o booking form, highlight pickup/dropoff bi loi va yeu cau chon diem trong cung vung phuc vu.
- Co the dung public boundary list de zoom/hint map, nhung khong hardcode polygon trong app.
### 4.1 Auth

#### Dang ky

```http
POST /api/v1/auth/register
```

Request:

```json
{
  "fullName": "Nguyen Van A",
  "phone": "0900000000",
  "email": "a@example.com",
  "password": "password123",
  "roles": ["PASSENGER"]
}
```

Response `data`:

```json
{
  "userId": 1,
  "accessToken": "...",
  "refreshToken": "...",
  "roles": ["PASSENGER"]
}
```

FE action:
- Luu `accessToken` trong memory/secure storage.
- Luu `refreshToken` trong secure storage.
- Dieu huong theo role: passenger app, driver onboarding, admin neu co.
- Gan mot `X-Request-Id` moi cho moi request; neu request fail, ghi lai `response.requestId` de trace voi backend.

#### Dang nhap

```http
POST /api/v1/auth/login
```

Request:

```json
{
  "phone": "0900000000",
  "password": "password123"
}
```

Response giong register.

#### Refresh token

```http
POST /api/v1/auth/refresh
```

Request:

```json
{
  "refreshToken": "..."
}
```

FE action:
- Khi REST tra 401 voi `TOKEN_EXPIRED` hoac `TOKEN_INVALID`, thu refresh mot lan.
- Neu refresh fail, logout local va ve man login.

#### Logout

```http
POST /api/v1/auth/logout
```

Request:

```json
{
  "refreshToken": "..."
}
```

FE action:
- Goi API, sau do xoa token local ke ca khi API fail.

---

### 4.1.1 Upload avatar va driver documents

#### Upload avatar user

```http
POST /api/users/me/avatar
Authorization: Bearer <accessToken>
Content-Type: multipart/form-data
```

Multipart field:

```text
file=<image/jpeg|image/png|image/webp>
```

Response `data`:

```json
{
  "avatarUrl": "/uploads/avatars/10/8e8f...png"
}
```

Backend se validate file khong rong, dung content type, khong vuot `STORAGE_MAX_FILE_SIZE`, luu file vao local dev hoac Cloudflare R2 theo `STORAGE_PROVIDER`, va cap nhat `users.avatar_url`.

#### Upload driver documents

```http
POST /api/v1/uploads/driver-documents/{documentType}
Authorization: Bearer <driverToken>
Content-Type: multipart/form-data
```

`documentType`:

```ts
type DriverDocumentType = "PORTRAIT" | "LICENSE" | "ID_CARD" | "VEHICLE_REGISTRATION";
```

Multipart field:

```text
file=<image/jpeg|image/png|image/webp>
```

Response `data`:

```json
{
  "documentType": "LICENSE",
  "url": "/uploads/driver-documents/licenses/10/8e8f...png",
  "objectKey": "driver-documents/licenses/10/8e8f...png",
  "contentType": "image/png",
  "sizeBytes": 123456
}
```

FE action:
- Upload `PORTRAIT`, `LICENSE`, `ID_CARD`, `VEHICLE_REGISTRATION` truoc khi tao driver profile.
- Dung `url` tra ve de gan vao `portraitUrl`, `licenseImageUrl`, `idCardImageUrl`, `vehicleRegistrationUrl` trong request tao profile.
- Neu gap `FILE_UPLOAD_INVALID`, hien loi file rong/sai dinh dang/qua dung luong va cho user chon lai file.
- Local/dev co the dung URL `/uploads/**`; production nen set `STORAGE_PROVIDER=r2` va `CLOUDFLARE_R2_PUBLIC_BASE_URL` tro den custom/public domain cua bucket.


Runtime config Cloudflare R2 cho staging/production:

```properties
STORAGE_PROVIDER=r2
CLOUDFLARE_R2_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
CLOUDFLARE_R2_REGION=auto
CLOUDFLARE_R2_BUCKET=goride-uploads
CLOUDFLARE_R2_ACCESS_KEY=<r2-access-key>
CLOUDFLARE_R2_SECRET_KEY=<r2-secret-key>
CLOUDFLARE_R2_PUBLIC_BASE_URL=https://cdn.example.com
CLOUDFLARE_R2_PATH_STYLE_ACCESS_ENABLED=true
CLOUDFLARE_R2_API_CALL_TIMEOUT=30s
CLOUDFLARE_R2_API_CALL_ATTEMPT_TIMEOUT=10s
```

Ghi chu FE/devops:
- Khi `STORAGE_PROVIDER=local`, URL tra ve dang `/uploads/...` va chi dung local/dev.
- Khi `STORAGE_PROVIDER=r2`, backend upload object len bucket R2 va tra URL theo `CLOUDFLARE_R2_PUBLIC_BASE_URL + objectKey`.
- Backend fail startup neu bat R2 ma thieu endpoint, bucket, access key, secret key hoac public base URL; khong commit cac secret nay vao repo.
- Neu bucket/document can private access, giu API upload hien tai nhung can them commit signed URL/proxy download cho admin/driver truoc khi launch production.
---

### 4.2 Driver profile

#### Tao driver profile

```http
POST /api/v1/drivers/me/profile
Authorization: Bearer <driverToken>
```

Request:

```json
{
  "licenseNumber": "GPLX123456",
  "licenseExpiry": "2028-12-31",
  "idCardNumber": "079000000000",
  "portraitUrl": "/uploads/driver-documents/portraits/10/portrait.png",
  "licenseImageUrl": "/uploads/driver-documents/licenses/10/license.png",
  "idCardImageUrl": "/uploads/driver-documents/id-cards/10/id-card.png",
  "vehicleRegistrationUrl": "/uploads/driver-documents/vehicle-registrations/10/registration.png",
  "vehiclePlate": "59A1-12345",
  "vehicleType": "MOTORBIKE",
  "vehicleBrand": "Honda",
  "vehicleModel": "Wave",
  "vehicleColor": "Black",
  "vehicleYear": 2022
}
```

Response `data`: `DriverProfileResponse`, gom them `licenseImageUrl`, `idCardImageUrl`, `vehicleRegistrationUrl` neu FE da submit.

FE action:
- Sau khi tao profile, hien trang "Dang cho duyet".
- Chua cho bat online neu `approvalStatus !== "APPROVED"`.

#### Lay profile driver hien tai

```http
GET /api/v1/drivers/me/profile
Authorization: Bearer <driverToken>
```

FE action:
- Goi khi vao driver app.
- Neu 404 `DRIVER_PROFILE_NOT_FOUND`, dieu huong sang onboarding.
- Neu `approvalStatus = PENDING`, disable nut online.

#### Bat/tat online

```http
PATCH /api/v1/drivers/me/status
Authorization: Bearer <driverToken>
```

Request online:

```json
{
  "online": true,
  "lat": 10.7769,
  "lng": 106.7009
}
```

Request offline:

```json
{
  "online": false,
  "lat": 10.7769,
  "lng": 106.7009
}
```

FE action:
- Chi cho online khi da co quyen location.
- Sau khi online thanh cong, bat dau heartbeat moi 20 giay.
- Neu loi `DRIVER_NOT_APPROVED`, hien thong bao "Ho so chua duoc duyet".

#### Driver heartbeat

```http
POST /api/v1/drivers/me/heartbeat
Authorization: Bearer <driverToken>
Content-Type: application/json
```

Request:

```json
{
  "lat": 10.7769,
  "lng": 106.7009
}
```

Response `data`:

```json
{
  "online": true,
  "heartbeatAt": "2026-06-14T06:30:00Z",
  "expiresAt": "2026-06-14T06:31:00Z"
}
```

FE action:
- Gui heartbeat khi app driver dang online, de xuat moi 20 giay va truoc `expiresAt`.
- Moi heartbeat gui location moi nhat; backend cap nhat Redis GEO va `driver_profiles.last_location_at`.
- Tam dung heartbeat khi driver bam offline hoÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â·c logout.
- Neu mat mang ngan, retry voi exponential backoff nhung khong de qua `expiresAt`.
- Neu nhan `DRIVER_NOT_AVAILABLE`, dung heartbeat va hien nut "Bat dau nhan chuyen" de goi lai `PATCH /api/v1/drivers/me/status` voi `online=true`.
- Heartbeat khong lam driver dang `BUSY` thanh `AVAILABLE`; FE tiep tuc gui heartbeat trong suot active trip.

Backend config:

```yaml
app:
  driver:
    availability:
      heartbeat-timeout-seconds: 60
      cleanup-batch-size: 100
      scheduler:
        enabled: true
        fixed-delay-ms: 15000
        initial-delay-ms: 30000
```

---

### 4.3 Passenger booking

#### Tinh gia uoc luong

```http
POST /api/v1/bookings/estimate
Authorization: Bearer <passengerToken>
```

Request:

```json
{
  "pickup": {
    "lat": 10.7769,
    "lng": 106.7009,
    "address": "Ben Thanh Market"
  },
  "dropoff": {
    "lat": 10.8130,
    "lng": 106.6650,
    "address": "Tan Son Nhat Airport"
  },
  "vehicleType": "MOTORBIKE"
}
```

Response `data`:

```json
{
  "vehicleType": "MOTORBIKE",
  "distanceKm": 4.2,
  "durationMinutes": 18,
  "estimatedFare": 32000,
  "currency": "VND"
}
```

FE action:
- Goi khi pickup/dropoff/vehicleType thay doi.
- Neu `PRICING_CONFIG_NOT_FOUND`, disable booking va bao "Chua co cau hinh gia".

#### Tao booking

```http
POST /api/v1/bookings
Authorization: Bearer <passengerToken>
```

Request:

```json
{
  "pickup": {
    "lat": 10.7769,
    "lng": 106.7009,
    "address": "Ben Thanh Market"
  },
  "dropoff": {
    "lat": 10.8130,
    "lng": 106.6650,
    "address": "Tan Son Nhat Airport"
  },
  "vehicleType": "MOTORBIKE",
  "paymentMethod": "CASH",
  "scheduledPickupTime": null
}
```

Response `data`: `TripResponse`, status ban dau `SEARCHING` neu dat ngay, hoac `SCHEDULED` neu co `scheduledPickupTime` hop le.

Field optional cho dat lich:
- `scheduledPickupTime`: ISO-8601 instant UTC, vi du `2026-07-04T10:30:00Z`.
- Bo field nay hoac gui
ull` de dat xe ngay nhu cu.
- Backend mac dinh yeu cau thoi gian don toi thieu 15 phut trong tuong lai va tra `SCHEDULED_PICKUP_TIME_INVALID` neu qua gan.

FE action:
- Neu response `status=SEARCHING`, chuyen sang man tim driver.
- Neu response `status=SCHEDULED`, chuyen sang man dat lich/cho den gio, cho phep huy va hydrate bang `GET /api/v1/bookings/{tripId}`.
- Subscribe `/topic/trip/{tripId}/status` va `/user/queue/notifications` khi man trip dang mo; backend se tu dispatch matching gan gio don theo config va broadcast status `SEARCHING`.
- Neu `PASSENGER_HAS_ACTIVE_TRIP`, mo trip dang active/scheduled thay vi tao trip moi.

#### Xem chi tiet trip

```http
GET /api/v1/bookings/{tripId}
Authorization: Bearer <passengerOrDriverToken>
```

FE action:
- Dung de hydrate lai man hinh trip sau refresh app.
- Passenger, assigned driver, admin moi co quyen.

#### Xem danh sach trip cua minh

```http
GET /api/v1/bookings
Authorization: Bearer <passengerOrDriverToken>
```

Response `data`: array `TripResponse`.

#### Huy booking

```http
PATCH /api/v1/bookings/{tripId}/cancel
Authorization: Bearer <passengerOrDriverToken>
```

Request:

```json
{
  "reason": "Khach doi y"
}
```

FE action:
- Chi hien nut huy khi status `SCHEDULED`, `SEARCHING`, `ACCEPTED`, `ARRIVED`.
- Neu backend tra `TRIP_CANNOT_BE_CANCELLED`, refresh trip detail.

---

### 4.4 Matching va driver offer

Matching chay tu dong sau khi passenger tao booking tuc thi, hoac sau khi scheduler mo scheduled booking gan gio don. FE khong co endpoint "start matching" rieng.
Neu trip dang `SCHEDULED`, backend chua gui offer cho driver. Khi den cua dispatch, backend chuyen `SCHEDULED -> SEARCHING`, broadcast status topic va kich hoat matching nhu booking tuc thi.
Neu chua co driver online/phu hop ngay lan matching dau, backend giu trip o `SEARCHING`. Khi driver online hoac gui heartbeat thanh cong, backend tu thu match lai cac trip dang `SEARCHING` chua co offer active.

Driver can:
1. Tao profile.
2. Profile duoc approve.
3. Bat online voi vi tri.
4. Subscribe queue offer.

WebSocket subscribe:

```text
/user/queue/trip-requests
```

Payload offer:

```json
{
  "tripId": 99,
  "passengerId": 10,
  "vehicleType": "MOTORBIKE",
  "pickupLatitude": 10.7769,
  "pickupLongitude": 106.7009,
  "dropoffLatitude": 10.813,
  "dropoffLongitude": 106.665,
  "estimatedFare": 32000,
  "distanceMeters": 250,
  "expiresAt": "2026-05-27T10:00:30Z"
}
```

Payload dismiss khi passenger huy booking luc offer dang hien tren driver:

```json
{
  "type": "TRIP_CANCELLED",
  "action": "DISMISS",
  "tripId": 99,
  "passengerId": 10,
  "driverId": 20,
  "reason": "Changed plan",
  "cancelledAt": "2026-05-27T10:00:10Z"
}
```

Driver phan hoi:

```http
PATCH /api/v1/drivers/trips/{tripId}/respond
Authorization: Bearer <driverToken>
```

Request accept:

```json
{
  "action": "ACCEPT"
}
```

Request reject:

```json
{
  "action": "REJECT"
}
```

Response `data`:

```json
{
  "tripId": 99,
  "status": "ACCEPTED"
}
```

FE action:
- Hien countdown den `expiresAt`.
- Neu nhan payload co `type=TRIP_CANCELLED` va `action=DISMISS` cho offer hien tai, dong modal offer, dung countdown, khong goi accept/reject nua va co the refresh danh sach trip neu man hinh dang lien quan.
- Neu accept thanh cong, mo man trip driver.
- Neu reject thanh cong, dong modal offer; passenger trip van `SEARCHING` neu backend chua tim duoc driver tiep theo.
- Neu `MATCHING_OFFER_EXPIRED`, dong offer va doi offer moi. Passenger van o man hinh dang tim tai xe cho toi khi co driver accept, co offer moi cho driver khac, hoac passenger tu huy.

Passenger can subscribe:

```text
/topic/trip/{tripId}/status
/user/queue/notifications
```

Khi driver accept, passenger nhan `TRIP_ACCEPTED` notification va topic status `ACCEPTED`. Khi driver reject/offer timeout, passenger khong nhan `NO_DRIVER_FOUND` neu trip van co the tiep tuc search/rematch.

#### Routing cho tai xe den pickup/dropoff

```http
POST /api/v1/drivers/trips/{tripId}/route
Authorization: Bearer <driverToken>
Content-Type: application/json
```

Request la GPS hien tai cua tai xe:

```json
{
  "latitude": 10.7600,
  "longitude": 106.6900
}
```

Backend tu chon destination:

| Trip status | `destinationType` | Dich |
|---|---|---|
| `ACCEPTED` | `PICKUP` | Vi tri don khach |
| `ARRIVED`, `IN_PROGRESS` | `DROPOFF` | Vi tri tra khach |
| Status khac | Error | `TRIP_ROUTE_NOT_AVAILABLE` |

Response `data`:

```json
{
  "tripId": 99,
  "tripStatus": "ACCEPTED",
  "destinationType": "PICKUP",
  "destination": {
    "lat": 10.7769,
    "lng": 106.7009,
    "address": "Ben Thanh Market"
  },
  "distanceMeters": 2346,
  "durationSeconds": 457,
  "geometry": {
    "type": "LineString",
    "coordinates": [
      [106.6900, 10.7600],
      [106.6950, 10.7680],
      [106.7009, 10.7769]
    ]
  },
  "steps": [
    {
      "distanceMeters": 125,
      "durationSeconds": 32,
      "roadName": "Le Loi",
      "maneuverType": "turn",
      "maneuverModifier": "right",
      "longitude": 106.6950,
      "latitude": 10.7680
    }
  ]
}
```

FE action:
- GeoJSON coordinates luon theo thu tu `[longitude, latitude]`; khong dao thanh `[lat, lng]` khi ve polyline.
- Goi lan dau ngay sau khi accept trip, sau do re-route khi tai xe di lech/di chuyen du nguong hoac theo interval co debounce; khong goi theo tung GPS frame.
- Khi status doi sang `ARRIVED`, goi lai endpoint de destination tu dong chuyen sang `DROPOFF`.
- `maneuverType`/`maneuverModifier` la du lieu OSRM; FE tu map sang icon va text tieng Viet.
- Chi assigned driver goi duoc. `FORBIDDEN` thi dong navigation va refresh trip detail.
- `ROUTING_PROVIDER_ERROR` HTTP 502 thi giu destination marker, cho retry hoac mo external map app bang destination coordinates.
- Driver navigation yeu cau `ROUTING_ENABLED=true`; khong dung Haversine fallback vi duong thang khong an toan de dieu huong.

---

### 4.5 Trip status cho driver

```http
PATCH /api/v1/drivers/trips/{tripId}/status
Authorization: Bearer <driverToken>
```

Request arrived:

```json
{
  "status": "ARRIVED"
}
```

Request start:

```json
{
  "status": "IN_PROGRESS"
}
```

Request complete:

```json
{
  "status": "COMPLETED"
}
```

FE action:
- Chi hien nut theo state:
  - `ACCEPTED`: "Da den diem don" -> `ARRIVED`
  - `ARRIVED`: "Bat dau chuyen" -> `IN_PROGRESS`
  - `IN_PROGRESS`: "Hoan thanh" -> `COMPLETED`
- Sau `COMPLETED`, backend tinh `finalFare` va tao payment `PENDING`.
- Sau `COMPLETED`, backend tang `totalTrips` trong driver profile; FE doc lai driver profile se thay thong ke moi.
- Passenger/driver subscribe `/topic/trip/{tripId}/status`.
- Driver cung nhan user notification khi trip completed.

---

### 4.6 Tracking realtime

#### Driver gui location

STOMP send:

```text
destination: /app/driver.location
```

Payload:

```json
{
  "lat": 10.7769,
  "lng": 106.7009,
  "bearing": 120.5,
  "speed": 25.3
}
```

Backend se tim trip active moi nhat cua driver voi status `ACCEPTED`, `ARRIVED` hoac `IN_PROGRESS`. Khong can gui `tripId` trong payload.

FE action:
- Bat dau gui location ngay sau khi driver accept trip (`ACCEPTED`) de passenger thay tai xe dang den diem don.
- Tiep tuc gui location trong `ARRIVED` va `IN_PROGRESS`.
- Tan suat goi y: 3-5 giay/lan voi mobile MVP.
- Neu backend tra/emit loi `TRIP_NOT_FOUND`, dung tracking vi driver chua co active assigned trip.

#### Passenger subscribe vi tri driver

```text
/topic/trip/{tripId}/location
```

Payload:

```json
{
  "tripId": 99,
  "driverId": 20,
  "lat": 10.7769,
  "lng": 106.7009,
  "bearing": 120.5,
  "speed": 25.3,
  "updatedAt": "2026-05-27T10:00:00Z"
}
```

#### Passenger lay latest location qua REST

```http
GET /api/v1/tracking/trips/{tripId}/driver-location
Authorization: Bearer <passengerToken>
```

FE action:
- Goi REST khi mo lai app/deep link vao trip screen.
- Sau do dung WebSocket topic de realtime.
- Neu `DRIVER_LOCATION_NOT_FOUND`, hien "Dang cho vi tri tai xe".
- Chi bat dau subscribe/polling vi tri khi trip da co driver va status la `ACCEPTED`, `ARRIVED` hoac `IN_PROGRESS`; khong polling khi status con `SEARCHING` hoac da la `NO_DRIVER`.
- Backend nhan vi tri driver tu luc `ACCEPTED` de passenger thay tai xe dang den diem don. Vi tri truoc `IN_PROGRESS` chi duoc cache/broadcast realtime, khong luu vao trip location history dung de tinh quang duong/final fare.
- Khi status con `SEARCHING`, hien man hinh dang tim tai xe va cho offer/status qua WebSocket; khong coi `DRIVER_LOCATION_NOT_FOUND` la loi.

---

### 4.6.1 In-trip messaging

Dung cho hop thoai passenger-driver trong active trip. FE nen load history qua REST khi mo trip detail, sau do subscribe WebSocket topic de nhan message moi.

#### Lay lich su message

```http
GET /api/v1/trips/{tripId}/messages?page=1&size=50
Authorization: Bearer <accessToken>
```

Response `data`: `PageResponse<TripMessageResponse>`.

```json
{
  "items": [
    {
      "id": 1,
      "tripId": 99,
      "senderId": 10,
      "senderRole": "PASSENGER",
      "body": "Toi dang dung o cong A",
      "sentAt": "2026-07-01T10:00:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "size": 50,
    "totalItems": 1,
    "totalPages": 1
  }
}
```

FE action:
- Goi khi mo trip detail/chat panel hoac reconnect app.
- `page` la 1-based; backend clamp `size` tu 1 den 100.
- Response sap xep message moi nhat truoc; FE co the dao nguoc list de render timeline cu -> moi.
- Passenger/driver chi xem duoc trip cua minh; admin co the xem de support.

#### Gui message qua REST fallback

```http
POST /api/v1/trips/{tripId}/messages
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Request:

```json
{
  "body": "Toi dang den trong 2 phut"
}
```

Response `201 Created`, `data`: `TripMessageResponse`.

FE action:
- Disable input neu trip chua co driver hoac status khong nam trong `ACCEPTED`, `ARRIVED`, `IN_PROGRESS`.
- Body khong rong sau trim va toi da 1000 ky tu.
- Neu response `TRIP_MESSAGE_NOT_AVAILABLE`, refresh trip status va khoa input.
- Neu response `FORBIDDEN`, user khong phai passenger/assigned driver cua trip hien tai; roi khoi chat panel.

#### Gui message qua STOMP

```text
SEND /app/trip.message
```

Payload:

```json
{
  "tripId": 99,
  "body": "Toi dang den trong 2 phut"
}
```

FE action:
- Chi gui STOMP sau khi `CONNECT` thanh cong voi bearer token.
- Neu app can delivery confirmation ro rang, uu tien REST `POST` va de WebSocket chi lam realtime fan-out.
- Backend van save DB va broadcast message da luu, nen FE nen de-dupe theo `id` neu vua POST vua nhan lai qua topic.

#### Subscribe message realtime

```text
/topic/trip/{tripId}/messages
```

Payload topic: `TripMessageResponse`.

FE action:
- Subscribe cung luc voi trip status/location trong active trip screen.
- Topic nay chi cho passenger cua trip, assigned driver hoac admin subscribe; subscribe nham trip se bi backend reject.
- Khi nhan message moi, append vao chat panel neu `id` chua ton tai.

---

### 4.7 Payment cash

Payment record duoc tao khi driver complete trip. FE khong co endpoint create payment rieng.
Backend da co provider abstraction noi bo. `CASH` enabled mac dinh; `VNPAY` va `MOMO` chi enabled khi provider bean da registered, config `enabled=true` va du config checkout rieng cho tung provider. Ca hai provider online da co signed checkout va callback foundation; production van nen disabled cho den khi sandbox/UAT thanh cong.

#### Lay danh sach payment methods

```http
GET /api/v1/payments/methods
```

Response `data`:

```json
[
  {
    "method": "CASH",
    "provider": "cash",
    "displayName": "Cash",
    "enabled": true,
    "checkoutRequired": false,
    "sandbox": false,
    "providerConfigured": true,
    "providerRegistered": true
  },
  {
    "method": "MOMO",
    "provider": "momo",
    "displayName": "MoMo",
    "enabled": false,
    "checkoutRequired": true,
    "sandbox": true,
    "providerConfigured": false,
    "providerRegistered": true
  },
  {
    "method": "VNPAY",
    "provider": "vnpay",
    "displayName": "VNPay",
    "enabled": false,
    "checkoutRequired": true,
    "sandbox": true,
    "providerConfigured": false,
    "providerRegistered": true
  }
]
```

FE action:
- Goi endpoint nay khi mo booking/payment screen de render option payment method, khong hardcode availability.
- Chi cho user chon method co `enabled=true`.
- Neu backend tra `enabled=false`, disable/hide option hoac hien "Coming soon".
- Backend cung reject booking neu client gui method chua enabled bang `PAYMENT_PROVIDER_UNSUPPORTED`.

#### Admin kiem tra provider sandbox readiness

```http
GET /api/v1/payments/providers/readiness
Authorization: Bearer <adminToken>
```

Response `data`:

```json
[
  {
    "method": "MOMO",
    "provider": "momo",
    "displayName": "MoMo",
    "enabled": true,
    "sandbox": true,
    "providerRegistered": true,
    "checkoutConfigured": true,
    "webhookConfigured": true,
    "checkoutReady": true,
    "webhookReady": true,
    "sandboxReady": true,
    "missingRequirements": [],
    "webhookMaxAgeSeconds": 86400,
    "webhookFutureSkewSeconds": 300
  },
  {
    "method": "VNPAY",
    "provider": "vnpay",
    "displayName": "VNPay",
    "enabled": true,
    "sandbox": true,
    "providerRegistered": true,
    "checkoutConfigured": true,
    "webhookConfigured": false,
    "checkoutReady": true,
    "webhookReady": false,
    "sandboxReady": false,
    "missingRequirements": ["webhook-secret"],
    "webhookMaxAgeSeconds": 86400,
    "webhookFutureSkewSeconds": 300
  }
]
```

Admin/devops action:
- Goi endpoint nay sau khi set env sandbox de xac nhan `sandboxReady=true` truoc khi chay UAT sandbox.
- De expose MoMo/VNPAY cho user that, FE/admin nen kiem tra them sandbox UAT result co `readyForFrontendExposure=true`.
- Khong log/commit secret; response chi tra ten requirement bi thieu, khong tra gia tri config.
- Neu `checkoutReady=false`, checkout URL chua nen duoc test tren FE.
- Neu `webhookReady=false`, chua nen chay callback sandbox vi backend se reject callback hoac khong reconcile duoc payment.

#### Admin lay payment sandbox UAT plan

```http
GET /api/v1/payments/providers/sandbox-uat-plan
Authorization: Bearer <adminToken>
```

Response `data` gom prerequisites, danh sach provider va cac scenario can test truoc khi mo online payment cho user that:

```json
{
  "prerequisites": [
    "Expose backend through a public HTTPS URL that the payment sandbox can call.",
    "Configure provider sandbox merchant credentials through environment variables or secret manager."
  ],
  "providers": [
    {
      "method": "MOMO",
      "provider": "momo",
      "displayName": "MoMo",
      "status": "READY_FOR_SANDBOX_UAT",
      "enabled": true,
      "sandbox": true,
      "checkoutReady": true,
      "webhookReady": true,
      "sandboxReady": true,
      "checkoutEndpoint": "GET /api/v1/payments/trips/{tripId}/checkout",
      "webhookEndpoint": "POST /api/v1/payments/providers/momo/webhook",
      "returnUrl": "https://app.example/payments/momo/return",
      "ipnUrl": "https://api.example/api/v1/payments/providers/momo/webhook",
      "missingRequirements": [],
      "frontendActions": [
        "Show this payment method only when GET /api/v1/payments/methods marks it enabled.",
        "After provider redirect, call GET /api/v1/payments/trips/{tripId} until payment status is terminal."
      ],
      "backendChecks": [
        "Confirm checkout returns a provider URL without PAYMENT_PROVIDER_ERROR.",
        "Confirm duplicate terminal callbacks are accepted idempotently with the same transaction reference."
      ]
    }
  ],
  "validationScenarios": [
    "Create a completed trip payment, open the checkout URL, then finish a sandbox success payment.",
    "Replay the same signed provider callback and verify the terminal payment update is idempotent."
  ]
}
```

Admin/devops action:
- Dung endpoint nay cung voi readiness de chot checklist truoc UAT sandbox that.
- Plan response co backend check yeu cau record evidence qua `POST /api/v1/payments/providers/{providerName}/sandbox-e2e-sessions` sau khi test checkout/callback/replay/freshness that.
- `status=DISABLED`, `NOT_REGISTERED`, `SANDBOX_DISABLED` hoac `BLOCKED_BY_CONFIG` thi FE khong expose online payment cho user that.
- `readyForFrontendExposure=false` nghia la backend/readiness co the da san sang, nhung admin chua record du evidence sandbox pass.
- `returnUrl` va `ipnUrl` chi la URL callback da cau hinh; response khong tra merchant secret/access key.
- Endpoint nay khong thay the real sandbox callback tu MoMo/VNPAY; no chi giup FE/admin biet can test gi va callback endpoint nao can dang ky voi provider.

#### Admin xem/ghi ket qua sandbox UAT evidence

```http
GET /api/v1/payments/providers/sandbox-uat-results
Authorization: Bearer <adminToken>
```

Response `data` la danh sach provider online kem ket qua UAT moi nhat:

```json
[
  {
    "method": "MOMO",
    "provider": "momo",
    "displayName": "MoMo",
    "sandboxReady": true,
    "readyForFrontendExposure": false,
    "status": "NOT_RUN",
    "checkoutUrlTested": false,
    "successCallbackTested": false,
    "failureCallbackTested": false,
    "idempotentReplayTested": false,
    "freshnessRejectionTested": false,
    "missingChecks": ["checkout-url", "success-callback", "failure-callback", "idempotent-replay", "freshness-rejection"],
    "notes": null,
    "testedAt": null,
    "testedByUserId": null,
    "updatedAt": null
  }
]
```

```http
PUT /api/v1/payments/providers/{providerName}/sandbox-uat-result
Authorization: Bearer <adminToken>
Content-Type: application/json
```

Request:

```json
{
  "status": "PASSED",
  "checkoutUrlTested": true,
  "successCallbackTested": true,
  "failureCallbackTested": true,
  "idempotentReplayTested": true,
  "freshnessRejectionTested": true,
  "notes": "MoMo sandbox passed with public callback URL on staging.",
  "testedAt": "2026-07-05T14:00:00Z"
}
```

Admin/devops action:
- Chi mark `PASSED` sau khi test checkout URL, success callback, failure callback, duplicate terminal callback va callback freshness rejection tren sandbox that.
- Backend reject `PASSED` neu provider chua `sandboxReady=true` hoac con thieu bat ky check nao; response loi `VALIDATION_ERROR` co `details.missingRequirements` hoac `details.missingChecks`.
- FE/admin dashboard co the dung `readyForFrontendExposure=true` lam gate cuoi cung de hien MoMo/VNPAY cho user that tren moi truong UAT/production.
- Nen uu tien ghi session E2E chi tiet bang endpoint ben duoi; endpoint `PUT` nay van huu ich neu can cap nhat aggregate result thu cong.
- Endpoint chi luu evidence va notes ngan; khong luu raw callback payload, card/wallet data, access key hay secret.

#### Admin ghi sandbox E2E session evidence

Dung endpoint nay sau khi da thuc hien real sandbox checkout/callback tren MoMo hoac VNPAY. Endpoint se tao mot evidence session rieng va dong bo sang aggregate UAT result neu du dieu kien.

```http
GET /api/v1/payments/providers/sandbox-e2e-sessions
Authorization: Bearer <adminToken>
```

```http
GET /api/v1/payments/providers/{providerName}/sandbox-e2e-sessions
Authorization: Bearer <adminToken>
```

```http
POST /api/v1/payments/providers/{providerName}/sandbox-e2e-sessions
Authorization: Bearer <adminToken>
Content-Type: application/json
```

Request mau khi provider sandbox da pass day du:

```json
{
  "status": "PASSED",
  "checkoutPaymentId": 100,
  "checkoutUrl": "https://sandbox-payment-provider.example/checkout/abc",
  "successPaymentId": 101,
  "successTransactionRef": "SANDBOX-SUCCESS-REF",
  "failurePaymentId": 102,
  "failureTransactionRef": "SANDBOX-FAILURE-REF",
  "replayTransactionRef": "SANDBOX-SUCCESS-REF",
  "checkoutUrlTested": true,
  "successCallbackTested": true,
  "failureCallbackTested": true,
  "idempotentReplayTested": true,
  "freshnessRejectionTested": true,
  "notes": "MoMo/VNPAY sandbox checkout, callbacks, replay and freshness checks passed",
  "testedAt": "2026-07-10T08:00:00Z"
}
```

Response `data` tra ve session da ghi:

```json
{
  "id": 1,
  "method": "MOMO",
  "provider": "momo",
  "displayName": "MoMo",
  "sandboxReady": true,
  "readyForFrontendExposure": true,
  "status": "PASSED",
  "checkoutPaymentId": 100,
  "checkoutUrl": "https://sandbox-payment-provider.example/checkout/abc",
  "successPaymentId": 101,
  "successTransactionRef": "SANDBOX-SUCCESS-REF",
  "failurePaymentId": 102,
  "failureTransactionRef": "SANDBOX-FAILURE-REF",
  "replayTransactionRef": "SANDBOX-SUCCESS-REF",
  "checkoutUrlTested": true,
  "successCallbackTested": true,
  "failureCallbackTested": true,
  "idempotentReplayTested": true,
  "freshnessRejectionTested": true,
  "missingChecks": [],
  "notes": "MoMo/VNPAY sandbox checkout, callbacks, replay and freshness checks passed",
  "testedAt": "2026-07-10T08:00:00Z",
  "testedByUserId": 42,
  "createdAt": "2026-07-10T08:01:00Z",
  "updatedAt": "2026-07-10T08:01:00Z"
}
```

Validation backend:
- `PASSED` bi reject neu readiness provider chua `sandboxReady=true`.
- `checkoutUrlTested=true` yeu cau `checkoutPaymentId` va `checkoutUrl` HTTPS hop le.
- `successCallbackTested=true` yeu cau payment cung provider, status `COMPLETED` va transaction ref khop.
- `failureCallbackTested=true` yeu cau payment cung provider, status `FAILED` va transaction ref khop.
- `idempotentReplayTested=true` yeu cau `replayTransactionRef` khop success hoac failure transaction ref da ghi.
- Endpoint nay khong luu raw callback payload hoac secret; chi luu evidence reference de audit.

Admin/FE action:
- Admin dashboard co the hien lich su session theo provider de audit ai da test, test luc nao va con thieu check nao.
- FE consumer payment method khong can goi endpoint session nay; chi dung aggregate `readyForFrontendExposure=true` tu metadata/UAT result de hien MoMo/VNPAY cho nguoi dung.
- Neu response loi `VALIDATION_ERROR`, dung `details.field`, `details.missingRequirements` hoac payment status hien tai de sua evidence truoc khi record lai.
#### Runtime config cho provider online

Backend da co config foundation cho MoMo/VNPay, mac dinh disabled.

```yaml
app:
  payments:
    providers:
      momo:
        enabled: false
        sandbox: true
        merchant-id: ${MOMO_MERCHANT_ID:}
        access-key: ${MOMO_ACCESS_KEY:}
        secret-key: ${MOMO_SECRET_KEY:}
        checkout-base-url: ${MOMO_CHECKOUT_BASE_URL:}
        return-url: ${MOMO_RETURN_URL:}
        ipn-url: ${MOMO_IPN_URL:}
        default-ip-address: ${PAYMENT_DEFAULT_IP_ADDRESS:127.0.0.1}
        webhook-secret: ${MOMO_WEBHOOK_SECRET:}
        webhook-max-age-seconds: ${MOMO_WEBHOOK_MAX_AGE_SECONDS:86400}
        webhook-future-skew-seconds: ${MOMO_WEBHOOK_FUTURE_SKEW_SECONDS:300}
      vnpay:
        enabled: false
        sandbox: true
        merchant-id: ${VNPAY_MERCHANT_ID:}
        secret-key: ${VNPAY_SECRET_KEY:}
        checkout-base-url: ${VNPAY_CHECKOUT_BASE_URL:}
        return-url: ${VNPAY_RETURN_URL:}
        ipn-url: ${VNPAY_IPN_URL:}
        default-ip-address: ${PAYMENT_DEFAULT_IP_ADDRESS:127.0.0.1}
        webhook-secret: ${VNPAY_WEBHOOK_SECRET:}
        webhook-max-age-seconds: ${VNPAY_WEBHOOK_MAX_AGE_SECONDS:86400}
        webhook-future-skew-seconds: ${VNPAY_WEBHOOK_FUTURE_SKEW_SECONDS:300}
```

FE action:
- `VNPAY` da co provider tao checkout URL; FE chi hien option nay khi metadata tra `enabled=true`.
- `VNPAY` da co callback/webhook verifier; backend tu cap nhat payment khi provider redirect/IPN ve endpoint webhook.
- `MOMO` da co provider tao create-payment request `captureWallet`, verify response va tra `payUrl`; stable ID la `GORIDE-PAY-{paymentId}` va `GORIDE-CREATE-{paymentId}`.
- `MOMO` can `merchant-id`, `access-key`, `secret-key`, create URL, return URL va IPN URL.
- MoMo HTTP client dung connect/read timeout 30 giay; loi mang, timeout, provider reject, sai chu ky hoac response khong khop tra `PAYMENT_PROVIDER_ERROR` (HTTP 502).
- MoMo IPN verify canonical HMAC-SHA256 truoc khi lookup payment; callback lap lai cung `transId` khong chay completion workflow lan nua.
- `webhook-max-age-seconds` gioi han tuoi callback dau tien; `webhook-future-skew-seconds` cho phep sai lech dong ho provider nho. Mac dinh la 24 gio va 5 phut.
- Khi provider online enabled, FE can xu ly `checkoutRequired=true`.
- `sandbox=true` dung cho moi truong test provider; production nen set `sandbox=false` va secret qua env/secret manager.

#### Lay checkout session theo trip

```http
GET /api/v1/payments/trips/{tripId}/checkout
Authorization: Bearer <passengerOrDriverOrAdminToken>
```

Response `data` voi CASH:

```json
{
  "paymentId": 70,
  "tripId": 99,
  "amount": 45000,
  "method": "CASH",
  "status": "PENDING",
  "provider": null,
  "checkoutRequired": false,
  "checkoutUrl": null,
  "expiresAt": null
}
```

FE action:
- Goi sau trip `COMPLETED`/payment `PENDING` de biet payment method co can redirect khong.
- Voi `CASH`, `checkoutRequired=false`, FE hien man hinh thanh toan tien mat va cho driver confirm; khong mo webview/checkout URL khi `checkoutUrl` null hoac khong co field.
- Voi `VNPAY`, neu `checkoutRequired=true`, FE mo `checkoutUrl` va theo doi payment status/webhook flow.
- Voi `MOMO`, neu backend expose `enabled=true`, FE mo `checkoutUrl` (`payUrl`) tu response; sau redirect refresh payment detail trong khi backend xu ly IPN.
- Endpoint chi hop le cho payment `PENDING`; neu payment da completed backend tra `PAYMENT_INVALID_STATUS`, FE nen refresh payment detail.

#### Provider webhook callback

```http
POST /api/v1/payments/providers/{providerName}/webhook
Content-Type: application/json
```

Hoac voi provider gui query params kieu VNPAY:

```http
GET /api/v1/payments/providers/{providerName}/webhook?vnp_TxnRef=GORIDE-PAY-70&vnp_ResponseCode=00&...
```

Request body/query: raw callback params cua provider.

POST generic provider response van dung `ApiResponse`:

```json
{
  "success": true,
  "data": {
    "provider": "vnpay",
    "accepted": true,
    "paymentId": 70,
    "tripId": 99,
    "status": "COMPLETED",
    "transactionRef": "provider-transaction-ref",
    "message": "processed"
  }
}
```

MoMo IPN hop le tra HTTP `204 No Content`, khong co response body. Neu signature/payload khong hop le, backend khong acknowledge 204 va khong cap nhat payment.

VNPAY GET/IPN response tra raw JSON theo contract provider, khong boc `ApiResponse`:

```json
{
  "RspCode": "00",
  "Message": "Confirm Success"
}
```

Mapping IPN response:
- `00`: callback da duoc xu ly.
- `01`: khong tim thay payment/order.
- `04`: amount callback khong khop payment.
- `97`: `vnp_SecureHash` khong hop le.
- `99`: loi/validation khac.

FE action:
- FE app khong goi endpoint nay truc tiep; day la endpoint public de MoMo/VNPay callback vao backend.
- Khi tich hop provider that, FE chi mo `checkoutUrl` neu checkout response bao `checkoutRequired=true`, sau do theo doi payment detail/notification.
- Voi `VNPAY`, sau khi user quay ve app/web tu `vnp_ReturnUrl`, FE nen refresh payment detail theo trip va hien trang thai moi. Backend xu ly callback khi provider goi `/providers/vnpay/webhook` bang GET query params hoac POST JSON.
- Hien tai `CASH` khong support webhook; goi `/providers/cash/webhook` se tra `PAYMENT_PROVIDER_UNSUPPORTED`.
- `VNPAY` verify `vnp_SecureHash` khong phan biet uppercase/lowercase, `vnp_TmnCode`, `vnp_Amount` va map `vnp_ResponseCode=00` + `vnp_TransactionStatus=00` thanh `COMPLETED`; cac status khac thanh `FAILED`.
- MoMo checkout va IPN deu verify HMAC-SHA256; IPN doi chieu partner code, stable order/request ID, amount, order info va `extraData`.
- MoMo map `resultCode=0` hoac `9000` thanh `COMPLETED` cho flow `captureWallet` auto-capture; cac result code khac thanh `FAILED`.
- Duplicate MoMo success cung `transId` la idempotent; callback conflict voi transaction reference da luu bi reject.
- MoMo dung signed `responseTime`; VNPAY dung signed `vnp_PayDate` GMT+7. Callback qua cu hoac nam qua xa trong tuong lai tra validation error va khong cap nhat payment.
- Provider retry qua freshness window van duoc acknowledge neu payment da terminal voi cung provider va transaction reference; retry conflict van bi reject.
- Backend da co service-level contract tests cho MoMo success, VNPAY failure va stale callback reject bang signed payload; day khong thay the sandbox callback that tu provider.
- Sau khi provider webhook mark payment `COMPLETED`, backend goi shared payment completion workflow de notify passenger/driver va dua driver ve `AVAILABLE`.

Driver confirm cash:

```http
PATCH /api/v1/drivers/trips/{tripId}/payment-confirm
Authorization: Bearer <driverToken>
```

Request: empty body.

Response `data`:

```json
{
  "tripId": 99,
  "status": "COMPLETED",
  "amount": 45000,
  "paidAt": "2026-05-27T10:05:00Z"
}
```

FE action:
- Sau trip `COMPLETED`, hien final fare cho driver va nut "Da nhan tien".
- Goi endpoint nay khi driver xac nhan da nhan tien mat.
- Sau thanh cong, driver co the ve man online/available.
- Passenger/driver subscribe `/user/queue/notifications` de nhan `PAYMENT_COMPLETED`.
- Neu `PAYMENT_INVALID_STATUS`, refresh trip/payment state; co the payment da confirm roi.

#### Xem payment theo trip

```http
GET /api/v1/payments/trips/{tripId}
Authorization: Bearer <passengerOrDriverOrAdminToken>
```

Response `data`:

```json
{
  "paymentId": 70,
  "tripId": 99,
  "amount": 45000,
  "method": "CASH",
  "status": "COMPLETED",
  "provider": null,
  "transactionRef": null,
  "paidAt": "2026-05-27T10:05:00Z",
  "createdAt": "2026-05-27T10:01:00Z",
  "updatedAt": "2026-05-27T10:05:00Z"
}
```

FE action:
- Passenger cua trip, assigned driver, hoac admin moi xem duoc payment.
- Goi sau khi trip `COMPLETED` de hydrate invoice/payment screen.
- Neu tra `PAYMENT_NOT_FOUND`, payment record chua duoc tao hoac trip chua completed; FE nen refresh trip detail.
- Neu tra `FORBIDDEN`, an invoice/payment action cho user khong co quyen.

---

### 4.8 Rating

#### Passenger tao rating

```http
POST /api/v1/ratings
Authorization: Bearer <passengerToken>
```

Request:

```json
{
  "tripId": 99,
  "score": 5,
  "comment": "Tai xe dung gio"
}
```

Response `data`:

```json
{
  "ratingId": 55,
  "tripId": 99,
  "driverId": 20,
  "score": 5,
  "comment": "Tai xe dung gio",
  "createdAt": "2026-05-27T10:10:00Z"
}
```

FE action:
- Hien rating prompt sau khi trip `COMPLETED` va payment `COMPLETED`.
- Gioi han score 1-5, comment <= 500 ky tu.
- Neu `TRIP_ALREADY_RATED`, an form va coi la da rating.
- Neu driver dang online, backend sync `driver:{id}:meta.rating` sau khi transaction rating commit; FE khong can goi them endpoint.

#### Passenger kiem tra trip da rating chua

```http
GET /api/v1/ratings/trips/{tripId}/me
Authorization: Bearer <passengerToken>
```

Response chua rating:

```json
{
  "tripId": 99,
  "rated": false,
  "rating": null
}
```

Response da rating:

```json
{
  "tripId": 99,
  "rated": true,
  "rating": {
    "ratingId": 55,
    "tripId": 99,
    "driverId": 20,
    "score": 5,
    "comment": "Tai xe dung gio",
    "createdAt": "2026-05-27T10:10:00Z"
  }
}
```

FE action:
- Goi truoc khi hien rating form o trip completed/payment completed screen.
- Chi passenger cua trip moi xem duoc status nay.
- Neu `rated = true`, an form va hien rating da gui.

#### Public list rating cua driver

```http
GET /api/v1/drivers/{driverId}/ratings?page=1&size=20
```

Luu y: `driverId` la `userId` cua driver, khong phai `DriverProfileResponse.id`.

Response `data`:

```json
{
  "items": [
    {
      "ratingId": 55,
      "tripId": 99,
      "driverId": 20,
      "score": 5,
      "comment": "Tai xe dung gio",
      "createdAt": "2026-05-27T10:10:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "size": 20,
    "totalItems": 1,
    "totalPages": 1
  }
}
```

FE action:
- Dung cho driver public profile/review screen.
- `page` la 1-based.
- `size` hop le tu 1 den 100.

---

### 4.9 User profile va admin user management

Luu y: nhom API user hien dang dung prefix `/api/users`, khong phai `/api/v1`.

#### User lay profile cua minh

```http
GET /api/users/me
Authorization: Bearer <accessToken>
```

Response `data`:

```json
{
  "id": 1,
  "fullName": "Nguyen Van A",
  "phone": "0900000000",
  "email": "a@example.com",
  "avatarUrl": "https://cdn.example.com/avatar.jpg",
  "status": "ACTIVE",
  "roles": ["PASSENGER"],
  "createdAt": "2026-05-27T10:00:00Z",
  "updatedAt": "2026-05-27T10:00:00Z"
}
```

#### User cap nhat profile cua minh

```http
PUT /api/users/me
Authorization: Bearer <accessToken>
```

Request:

```json
{
  "fullName": "Nguyen Van A",
  "phone": "0900000000",
  "email": "a@example.com",
  "avatarUrl": "https://cdn.example.com/avatar.jpg"
}
```

#### User doi mat khau

```http
PUT /api/users/me/password
Authorization: Bearer <accessToken>
```

Request:

```json
{
  "currentPassword": "old-password",
  "newPassword": "new-password"
}
```

FE action:
- Neu `INVALID_CREDENTIALS`, hien "Mat khau hien tai khong dung".
- Sau khi doi mat khau thanh cong, nen yeu cau user dang nhap lai neu app policy can chat hon.

#### Admin list users

```http
GET /api/users?page=1&size=20
Authorization: Bearer <adminToken>
```

Response `data`: `PageResponse<UserResponse>`.

#### Admin tao/cap nhat/xoa user

```http
POST /api/users
GET /api/users/{id}
PUT /api/users/{id}
DELETE /api/users/{id}
Authorization: Bearer <adminToken>
```

FE action:
- Dung `status` = `ACTIVE` hoac `SUSPENDED` de activate/suspend.
- `roles` la set role day du cua user sau cap nhat.

---

### 4.10 Admin driver approval

#### Admin xem driver dang cho duyet

```http
GET /api/v1/admin/drivers/pending?page=1&size=20
Authorization: Bearer <adminToken>
```

Response `data`: `PageResponse<DriverProfileResponse>`.

FE action:
- Hien danh sach ho so driver co `approvalStatus = PENDING`.
- `page` la 1-based, `size` hop le tu 1 den 100.

#### Admin approve/reject driver

```http
PATCH /api/v1/admin/drivers/{driverId}/approval
Authorization: Bearer <adminToken>
```

Luu y: `driverId` la `userId` cua driver trong `DriverProfileResponse`, khong phai profile `id`.

Request approve:

```json
{
  "approvalStatus": "APPROVED"
}
```

Request reject:

```json
{
  "approvalStatus": "REJECTED"
}
```

Response `data`: `DriverProfileResponse`, gom them `licenseImageUrl`, `idCardImageUrl`, `vehicleRegistrationUrl` neu FE da submit.

FE action:
- Sau approve, driver co the bat online.
- Sau reject, backend set profile offline va xoa driver khoi Redis availability pool.
- Khong gui `PENDING`; backend se tra `VALIDATION_ERROR`.

---

### 4.11 Pricing config

#### Public xem bang gia active

```http
GET /api/v1/pricing
```

Response `data`: `PricingConfigResponse[]`.

```json
[
  {
    "id": 1,
    "vehicleType": "MOTORBIKE",
    "baseFare": 10000,
    "perKmRate": 4000,
    "perMinuteRate": 300,
    "minimumFare": 15000,
    "surgeMultiplier": 1.0,
    "active": true,
    "effectiveFrom": "2026-01-01T00:00:00Z",
    "createdAt": "2026-06-03T10:00:00Z",
    "currency": "VND"
  }
]
```

FE action:
- Dung endpoint nay de hien thi bang gia cho tung `vehicleType`.
- Gia hien thi chi mang tinh config; khi booking, van goi `POST /api/v1/bookings/estimate` de server tinh gia theo khoang cach/thoi gian.

#### Admin xem tat ca pricing versions

```http
GET /api/v1/admin/pricing
Authorization: Bearer <adminToken>
```

Response `data`: `PricingConfigResponse[]`, bao gom active va inactive versions.

#### Admin tao pricing version moi

```http
POST /api/v1/admin/pricing
Authorization: Bearer <adminToken>
```

Request:

```json
{
  "vehicleType": "CAR_4_SEAT",
  "baseFare": 18000,
  "perKmRate": 8500,
  "perMinuteRate": 600,
  "minimumFare": 28000,
  "surgeMultiplier": 1.1,
  "effectiveFrom": "2026-06-01T00:00:00Z"
}
```

FE action:
- Khi tao version moi cho cung `vehicleType`, backend deactivate cac active config cu cua loai xe do.
- Khong sua version cu truc tiep de giu lich su pricing.
- `effectiveFrom` khong duoc nam trong tuong lai trong MVP; tao version moi chi ap dung ngay hoac tu thoi diem qua khu.

#### Admin deactivate pricing version

```http
PATCH /api/v1/admin/pricing/{pricingConfigId}/deactivate
Authorization: Bearer <adminToken>
```

FE action:
- Dung khi can tat mot pricing config.
- Can dam bao moi `vehicleType` co it nhat mot active config neu FE/backend van cho booking loai xe do.

#### Admin surge pricing rules

```http
GET /api/v1/admin/pricing/surge-rules
GET /api/v1/admin/pricing/surge-status?vehicleType=MOTORBIKE
POST /api/v1/admin/pricing/surge-rules
PATCH /api/v1/admin/pricing/surge-rules/{ruleId}
PATCH /api/v1/admin/pricing/surge-rules/{ruleId}/deactivate
Authorization: Bearer <adminToken>
```

Create request:

```json
{
  "vehicleType": "MOTORBIKE",
  "name": "Peak demand",
  "minDemandTrips": 3,
  "minDemandSupplyRatio": 2.0,
  "multiplier": 1.25,
  "active": true,
  "startsAt": null,
  "endsAt": null
}
```

Update request chi can gui field muon doi:

```json
{
  "name": "Peak demand - evening",
  "minDemandSupplyRatio": 2.5,
  "multiplier": 1.3,
  "active": true
}
```

FE action:
- Dung `surge-status` de hien demand/supply/matched rule hien tai theo `vehicleType`.
- Rule hop le khi `minDemandTrips >= 1`, `minDemandSupplyRatio > 0`, `multiplier` tu `1.0` den `3.0`; backend validate va tra `VALIDATION_ERROR` neu sai.
- `deactivate` la thao tac an toan de tat rule nhanh trong UAT; pricing config static van con nguyen.

---

### 4.12 Admin trip monitoring

#### Admin list/filter trips

```http
GET /api/v1/admin/trips?page=1&size=20&status=COMPLETED&from=2026-06-01T00:00:00Z&to=2026-06-08T23:59:59Z
Authorization: Bearer <adminToken>
```

Query params:
- `page`: 1-based, default `1`, min `1`.
- `size`: default `20`, min `1`, max `100`.
- `status`: optional `TripStatus`.
- `from`: optional ISO-8601 instant, filter `requestedAt >= from`.
- `to`: optional ISO-8601 instant, filter `requestedAt <= to`.

Response `data`: `PageResponse<TripResponse>`.

```json
{
  "items": [
    {
      "id": 99,
      "passengerId": 10,
      "driverId": 20,
      "status": "COMPLETED",
      "vehicleType": "MOTORBIKE",
      "paymentMethod": "CASH",
      "pickup": {
        "lat": 10.77,
        "lng": 106.7,
        "address": "Ben Thanh Market"
      },
      "dropoff": {
        "lat": 10.813,
        "lng": 106.665,
        "address": "Tan Son Nhat Airport"
      },
      "estimatedDistanceKm": 4.2,
      "estimatedDurationMin": 18,
      "estimatedFare": 32000,
      "finalFare": 45000,
      "requestedAt": "2026-06-05T10:00:00Z",
      "acceptedAt": "2026-06-05T10:01:00Z",
      "arrivedAt": "2026-06-05T10:10:00Z",
      "startedAt": "2026-06-05T10:12:00Z",
      "completedAt": "2026-06-05T10:30:00Z",
      "cancelledAt": null,
      "cancelReason": null
    }
  ],
  "pagination": {
    "page": 1,
    "size": 20,
    "totalItems": 1,
    "totalPages": 1
  }
}
```

FE action:
- Dung cho admin trip monitoring/table.
- Goi lai API khi admin doi status/date range/page/size.
- Neu `from > to`, backend tra `VALIDATION_ERROR`; FE nen validate tren form de tranh request sai.
- Ket hop voi `GET /api/v1/admin/trips?status=SEARCHING` de theo doi trip dang tim tai xe.

---

### 4.13 Admin dashboard

#### Admin dashboard summary

```http
GET /api/v1/admin/dashboard
Authorization: Bearer <adminToken>
```

Response `data`:

```json
{
  "totalUsers": 120,
  "activeUsers": 110,
  "suspendedUsers": 10,
  "totalDrivers": 35,
  "pendingDrivers": 4,
  "approvedDrivers": 29,
  "rejectedDrivers": 2,
  "totalTrips": 450,
  "tripsByStatus": {
    "SEARCHING": 2,
    "ACCEPTED": 3,
    "ARRIVED": 1,
    "IN_PROGRESS": 4,
    "COMPLETED": 420,
    "CANCELLED": 15,
    "NO_DRIVER": 5
  },
  "completedPayments": 400,
  "completedRevenue": 18500000,
  "averageDriverRating": 4.7
}
```

FE action:
- Dung cho admin dashboard cards va trip status chart.
- `completedRevenue` chi tinh payment `COMPLETED`, khong tinh payment `PENDING`.
- `tripsByStatus` luon co du cac key `TripStatus`; status chua co trip se la `0`.
- `averageDriverRating` la trung binh rating hien tai cua driver profiles, lam tron 1 chu so thap phan.

---

### 4.14 FCM device token

#### Dang ky/cap nhat FCM token

```http
PUT /api/v1/notifications/fcm-token
Authorization: Bearer <accessToken>
```

Request:

```json
{
  "token": "firebase-device-token"
}
```

Response `data`:

```json
{
  "userId": 10,
  "registered": true
}
```

FE action:
- Goi sau login, sau refresh FCM token, hoac khi Firebase cap token moi.
- Backend trim token va luu vao Redis key `fcm_token:{userId}`.
- Token toi da 4096 ky tu; neu token rong backend tra `VALIDATION_ERROR`.
- Backend da co FCM channel va Firebase Admin SDK sender. Khi `FCM_ENABLED=true`, notification ca nhan duoc gui them qua FCM bang token da luu.
- Local/dev van mac dinh khong gui push that neu `app.notifications.fcm.enabled=false`.

#### Xoa FCM token

```http
DELETE /api/v1/notifications/fcm-token
Authorization: Bearer <accessToken>
```

Response `data`:

```json
{
  "userId": 10,
  "registered": false
}
```

FE action:
- Goi khi logout hoac khi Firebase bao token invalid tren client.
- Sau khi xoa, user van nhan WebSocket notification neu app dang ket noi.
- Neu app nhan token moi, goi lai `PUT /api/v1/notifications/fcm-token`; backend se overwrite token cu trong Redis.
- Neu backend gui push va Firebase tra token `UNREGISTERED`, backend se tu xoa Redis key `fcm_token:{userId}`. FE can dang ky lai token o lan app khoi dong/login/refresh token tiep theo.

#### Trang thai backend FCM push

Khong co REST/WebSocket contract moi cho FE. FE chi can dang ky token qua endpoint tren.

Runtime backend:
- `app.notifications.fcm.enabled=false` theo mac dinh trong code.
- Khi `enabled=false`, channel FCM bo qua push va khong doc Redis token.
- Khi `enabled=true`, backend lay token tu `fcm_token:{userId}`, build payload `{ title, body, data }`, retry theo `app.notifications.fcm.max-attempts` mac dinh `2`.
- Khi `enabled=true`, Firebase Admin SDK duoc khoi tao ngay luc startup. Credential thieu/sai lam application startup fail de deployment phat hien som.
- Neu Firebase tra `UNREGISTERED`, backend xoa token trong Redis va khong retry token do.
- Cac loi transient khac duoc retry/suppress; backend khong xoa token voi cac loi nay.
- Uu tien production tren Google Cloud: gan service account cho workload de ADC tu tim credential, khong can JSON key.
- Production ngoai Google Cloud: mount credential/config federation ngoai container va dat `GOOGLE_APPLICATION_CREDENTIALS=/run/secrets/firebase-credentials.json`.
- Fallback tuong thich: dat `FIREBASE_SERVICE_ACCOUNT_PATH=/run/secrets/firebase-service-account.json`; backend uu tien path nay truoc ADC.
- Khong bake credential vao image, khong commit JSON vao repo. `.gitignore` da chan `.env`, `secrets/` va `firebase-service-account*.json`.

Environment variables:

```text
FCM_ENABLED=true
FCM_MAX_ATTEMPTS=2
FIREBASE_PROJECT_ID=goride-production
FIREBASE_APP_NAME=goride
GOOGLE_APPLICATION_CREDENTIALS=/run/secrets/firebase-credentials.json
```

`GOOGLE_APPLICATION_CREDENTIALS` co the tro den service account key hoac workload identity federation credential config. Neu da dung attached service account tren Google Cloud thi khong can dat bien nay.

---

### 4.15 Notification inbox

Notification inbox luu cac `UserNotification` ca nhan da gui qua `/user/queue/notifications`.

#### Xem notification inbox

```http
GET /api/v1/notifications?page=1&size=20
Authorization: Bearer <accessToken>
```

Response `data`:

```json
{
  "items": [
    {
      "notificationId": 50,
      "type": "TRIP_ACCEPTED",
      "title": "Trip accepted",
      "body": "Your driver is on the way",
      "data": {
        "tripId": 99,
        "status": "ACCEPTED",
        "driverId": 20
      },
      "read": false,
      "readAt": null,
      "createdAt": "2026-06-09T10:00:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "size": 20,
    "totalItems": 1,
    "totalPages": 1
  }
}
```

FE action:
- Goi khi mo notification center/inbox.
- `page` la 1-based, `size` hop le tu 1 den 100.
- `data` giu payload theo tung notification type de FE deep link ve trip/payment.
- Notification moi van duoc gui realtime qua WebSocket; inbox la fallback/history.
- Noi bo backend dang fan-out `UserNotification` qua `UserNotificationChannel`: hien co channel `in_app` de luu inbox, `websocket` de gui `/user/queue/notifications`, va `fcm` foundation cho mobile push. FE khong can doi contract.

#### Mark notification read

```http
PATCH /api/v1/notifications/{notificationId}/read
Authorization: Bearer <accessToken>
```

Response `data`: `NotificationResponse`.

FE action:
- Goi khi user mo notification hoac bam danh dau da doc.
- Chi owner cua notification moi mark read duoc.
- Neu tra `NOTIFICATION_NOT_FOUND`, notification khong ton tai hoac khong thuoc user hien tai.

---


### 4.16 Health/readiness/metrics

Dung cho FE/devops smoke check, load balancer, deployment probes va Prometheus scrape. Cac endpoint nay public trong app, khong gui bearer token; production nen chi expose qua trusted network/API gateway rule.

```http
GET /actuator
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
GET /actuator/info
GET /actuator/metrics
GET /actuator/metrics/{meterName}
GET /actuator/prometheus
```

Response discovery thanh cong co `_links`; response health thanh cong:

```json
{
  "status": "UP"
}
```

Prometheus endpoint tra text exposition format. Metrics dang chu y:
- `http.server.requests`: request count/latency/status theo URI pattern.
- `goride.rate.limit.requests`: counter theo tag `outcome=allowed|rejected`.
- `goride.rate.limit.buckets`: so bucket IP dang duoc in-memory rate limiter track.

Runtime config:

```properties
PROMETHEUS_METRICS_ENABLED=true
HTTP_SERVER_REQUESTS_HISTOGRAM=true
APP_NAME=goride
APP_GROUP=goride-backend
APP_ENV=staging
APP_VERSION=2026.07.05
LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash
```

FE/devops action:
- Dung `/actuator` de xem discovery links cua cac endpoint actuator expose.
- Dung `/actuator/health/liveness` cho container/process liveness probe.
- Dung `/actuator/health/readiness` cho readiness probe; endpoint nay phu thuoc DB va Redis nen co the tra non-2xx khi dependency chua san sang.
- Dung `/actuator/info` de xac nhan app identity (`app.name=goride`) trong smoke test.
- Dung `/actuator/prometheus` cho Prometheus/platform scraper; canh bao khi 5xx tang, latency tang, hoac `goride_rate_limit_requests_total{outcome="rejected"}` tang bat thuong.
- Neu deployment co log collector, dat `LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash` hoac format Spring Boot ho tro (`ecs`, `gelf`) de stdout chuyen sang JSON structured logs. Mac dinh rong giu console pattern dev hien tai.
- Structured logs co cac field tu MDC: `requestId`, `http.request.method`, `url.path`, `http.response.status_code`, `event.duration_ms`, kem context `service.name`, `service.environment`, `service.version`. Khong log request body, password, token hoac secret.
- Khong hien thi cac endpoint nay nhu chuc nang nguoi dung; chi dung cho diagnostics/deployment.

### 4.17 Production startup guardrails

Day la guardrail backend/devops, khong phai API cho man hinh nguoi dung. Khi `APP_ENV=production` hoac `APP_ENV=prod`, backend se fail-fast luc startup neu con cau hinh local/unsafe.

Runtime production toi thieu:

```properties
APP_ENV=production
JWT_SECRET=<secret-random-it-nhat-32-bytes-khong-dung-default-dev>
spring.jpa.hibernate.ddl-auto=validate
STORAGE_PROVIDER=r2
CORS_ALLOWED_ORIGINS=https://app.goride.example,https://admin.goride.example
CORS_ALLOWED_ORIGIN_PATTERNS=
CLOUDFLARE_R2_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
CLOUDFLARE_R2_BUCKET=goride-prod
CLOUDFLARE_R2_ACCESS_KEY=<r2-access-key>
CLOUDFLARE_R2_SECRET_KEY=<r2-secret-key>
CLOUDFLARE_R2_PUBLIC_BASE_URL=https://cdn.goride.example
```

Backend se chan cac loi cau hinh sau trong production:
- `JWT_SECRET` van la default dev hoac chua chuoi `change-me`/`local-dev`.
- `spring.jpa.hibernate.ddl-auto=update`, `create` hoac `create-drop`.
- `STORAGE_PROVIDER=local`.
- CORS allowed origins/patterns chua localhost, loopback hoac wildcard `*`.
- `STORAGE_PROVIDER=r2` nhung thieu endpoint, bucket, access key, secret key hoac public base URL.

Devops action:
- Dung `APP_ENV=local` cho may dev de tiep tuc dung local storage/CORS localhost.
- Truoc staging/production, set day du env vars tren platform, khong commit secret vao Git.
- Neu app fail voi message `Production readiness check failed`, doc tung property trong message va sua env/deployment config truoc khi restart.

FE action:
- FE khong can doi request body/header cho guardrail nay.
- Web/admin production phai chay dung domain nam trong backend CORS allowlist; khong dung `localhost` khi smoke production.
- Khi backend production khong start, day la loi deploy config chua san sang, khong phai loi FE.

---

## 5. WebSocket integration

### Ket noi

Endpoint:

```text
SockJS: http://localhost:8080/ws
Native WebSocket: ws://localhost:8080/ws-native
```

FE dung `SockJS`:

```ts
const socket = new SockJS("http://localhost:8080/ws");
const client = Stomp.over(socket);
```

FE dung native WebSocket voi `@stomp/stompjs`:

```ts
const client = new Client({
  brokerURL: "ws://localhost:8080/ws-native",
  connectHeaders: {
    Authorization: `Bearer ${accessToken}`,
  },
});
```

Application destination prefix:

```text
/app
```

Broker destinations:

```text
/topic
/queue
/user
```

FE nen gui JWT trong STOMP connect headers:

```ts
connectHeaders: {
  Authorization: `Bearer ${accessToken}`
}
```

Luu y: `/ws` la SockJS endpoint nen request `GET /ws/info` phai tra `200`. `/ws-native` la endpoint cho native WebSocket. Backend authenticate o STOMP `CONNECT`; neu thieu hoac sai `Authorization: Bearer <accessToken>`, connection frame bi tu choi. Sau khi connect thanh cong, backend gan `Principal`/roles tu JWT cho message mapping nhu `/app/driver.location`.

Subscribe vao `/topic/trip/{tripId}/status`, `/topic/trip/{tripId}/location` va `/topic/trip/{tripId}/messages` chi thanh cong neu JWT user la passenger cua trip, driver cua trip, hoac admin. Neu FE subscribe nham trip, backend reject frame voi `FORBIDDEN`/access denied o WebSocket layer.

### Destinations can subscribe

| Man hinh | Destination | Payload |
|---|---|---|
| Driver online/offer modal | `/user/queue/trip-requests` | `DriverOfferNotification` hoac `DriverOfferCancelledNotification` |
| Passenger/driver app shell | `/user/queue/notifications` | `UserNotification` |
| Trip detail | `/topic/trip/{tripId}/status` | `TripStatusNotification` |
| Passenger tracking | `/topic/trip/{tripId}/location` | `DriverLocationResponse` |
| Passenger/driver chat | `/topic/trip/{tripId}/messages` | `TripMessageResponse` |

### Messages FE gui len backend

| Flow | Destination | Body |
|---|---|---|
| Driver location tracking | `/app/driver.location` | `DriverLocationUpdateRequest` |
| Passenger/driver chat | `/app/trip.message` | `TripMessageSendRequest` |

---

### Database release workflow khong dung Flyway

Backend khong expose API runtime cho muc nay. Day la quy trinh devops/backend de moi thay doi schema production co SQL duoc version control va review truoc khi chay.

Thu muc release:

```text
db/releases/YYYYMMDD-short-name/
  manifest.yml
  precheck.sql
  apply.sql
  verify.sql
  rollback.sql
```

Lenh validate truoc review/deploy:

```powershell
.\scripts\validate-db-release.ps1 -ReleasePath db\releases\YYYYMMDD-short-name
```

Hoac validate tat ca release folders:

```powershell
.\scripts\validate-db-release.ps1 -All
```

Devops/backend action:
- Dung `db/releases/0000-template` khi co thay doi schema moi.
- Chay `precheck.sql` truoc deploy, `apply.sql` trong cua so deploy, va `verify.sql` sau deploy.
- Luu output precheck/verify voi release notes.
- `apply.sql` mac dinh phai co `BEGIN;` va `COMMIT;`; neu operation Postgres khong transactional thi set `transactional: false` trong manifest va ghi ly do.
- Neu co `DROP TABLE`, `TRUNCATE`, `DELETE FROM` hoac drop column, phai them comment `-- destructive-reviewed: true` sau review ro rang.
- Khong dung Hibernate `ddl-auto=update` cho staging/production; chi dung local dev de di nhanh.

---
## 6. Man hinh FE goi y theo flow

### Passenger app

- [ ] Auth screen: register/login/refresh/logout.
- [ ] FCM token registration sau login/refresh token.
- [ ] Home map: chon pickup/dropoff/vehicleType, goi estimate.
- [ ] Booking confirm: goi create booking; neu dat lich gui `scheduledPickupTime` ISO-8601 UTC, neu dat ngay gui
ull`/bo field.
- [ ] Finding driver: subscribe trip status + notifications.
- [ ] Active trip:
  - `ACCEPTED`: hien driver dang den.
  - `ARRIVED`: hien driver da den.
  - `IN_PROGRESS`: hien map tracking, subscribe location.
  - `COMPLETED`: hien final fare.
- [ ] Chat panel: load `GET /api/v1/trips/{tripId}/messages`, subscribe `/topic/trip/{tripId}/messages`, gui message khi trip active.
- [ ] Payment done screen: doi `PAYMENT_COMPLETED`.
- [ ] Rating screen: post rating.
- [ ] Trip history: list bookings.

### Driver app

- [ ] Auth screen.
- [ ] FCM token registration sau login/refresh token.
- [ ] Profile onboarding: tao profile bang `POST /api/v1/drivers/me/profile`.
- [ ] Approval waiting screen: doc `approvalStatus` tu `GET /api/v1/drivers/me/profile`; chi cho online khi status la `APPROVED`.
- [ ] Online toggle with current GPS.
- [ ] Offer modal from `/user/queue/trip-requests`, including `TRIP_CANCELLED`/`DISMISS` payload to close stale offers.
- [ ] Driver navigation: goi `POST /api/v1/drivers/trips/{tripId}/route`, ve GeoJSON route den pickup/dropoff va debounce re-route.
- [ ] Chat panel: load/send/subscribe trip messages nhu passenger app.
- [ ] Trip workflow buttons: arrived/start/complete.
- [ ] Location sender while `IN_PROGRESS`.
- [ ] Cash confirmation screen.
- [ ] Driver trip history: list bookings.

### Public/driver profile

- [ ] Public rating list: `GET /api/v1/drivers/{driverId}/ratings`.
- [ ] Display `averageRating` tu driver profile neu FE co endpoint lay profile tu app driver; public driver profile API rieng chua co.

### Admin app

- [ ] User management: list/create/update/delete users qua `/api/users`.
- [ ] User status management: set `ACTIVE`/`SUSPENDED` qua `/api/users/{id}`.
- [ ] Pricing management: list/create/deactivate pricing qua `/api/v1/admin/pricing`.
- [ ] Surge pricing management: list/create/update/deactivate rules va xem current status qua `/api/v1/admin/pricing/surge-*`.
- [ ] Pending driver list: `GET /api/v1/admin/drivers/pending`.
- [ ] Driver approval action: `PATCH /api/v1/admin/drivers/{driverId}/approval`.
- [ ] Trip monitoring: list/filter trips qua `GET /api/v1/admin/trips`.
- [ ] Dashboard statistics: summary cards qua `GET /api/v1/admin/dashboard`.

---

## 7. Error code FE nen xu ly rieng

| Code | FE handling |
|---|---|
| `VALIDATION_ERROR` | Hien field/form error. |
| `INVALID_CREDENTIALS` | Hien sai phone/password. |
| `TOKEN_EXPIRED`, `TOKEN_INVALID`, `REFRESH_TOKEN_EXPIRED` | Refresh token hoac logout. |
| `FORBIDDEN` | An action/ve man khong co quyen. |
| `PASSENGER_HAS_ACTIVE_TRIP` | Mo trip active thay vi tao moi. |
| `DRIVER_NOT_APPROVED` | Hien trang cho duyet. |
| `DRIVER_PROFILE_NOT_FOUND` | Driver onboarding hoac public not found. |
| `NO_DRIVER_AVAILABLE` | Passenger: khong tim thay driver. |
| `MATCHING_OFFER_NOT_FOUND`, `MATCHING_OFFER_EXPIRED` | Driver: dong offer hien tai. |
| `TRIP_STATUS_INVALID_TRANSITION` | Refresh trip state va disable nut sai flow. |
| `PAYMENT_INVALID_STATUS`, `PAYMENT_NOT_FOUND` | Refresh payment/trip, tranh double confirm. |
| `PAYMENT_PROVIDER_UNSUPPORTED` | Provider payment chua duoc backend enable; refresh/cau hinh lai payment method. |
| `PAYMENT_PROVIDER_ERROR` | Hien loi tam thoi cua cong thanh toan, cho retry checkout; khong danh dau payment da thanh cong. |
| `ROUTING_PROVIDER_ERROR` | Hien khong the tinh lo trinh, giu du lieu pickup/dropoff va cho retry. |
| `SURGE_PRICING_RULE_NOT_FOUND` | Admin refresh danh sach rule; rule da bi xoa/khong ton tai. |
| `TRIP_ROUTE_NOT_AVAILABLE` | Dung navigation va refresh trip; status hien tai khong cho route pickup/dropoff. |
| `TRIP_MESSAGE_NOT_AVAILABLE` | Khoa chat input, refresh trip status; chi cho gui message trong `ACCEPTED`, `ARRIVED`, `IN_PROGRESS`. |
| `RATE_LIMIT_EXCEEDED` | Dung retry tuc thi, doc `Retry-After`, disable action tam thoi va thu lai sau backoff. |
| `TRIP_ALREADY_RATED` | An rating form, coi trip da danh gia. |
| `DRIVER_LOCATION_NOT_FOUND` | Hien "Dang cho vi tri tai xe". |
| `NOTIFICATION_NOT_FOUND` | Refresh inbox; notification khong ton tai hoac khong thuoc user. |

---

## 8. Thu tu tich hop de giam rui ro

1. Auth REST + token refresh.
2. User profile: get/update me va change password.
3. Admin user management: list/create/update/delete/suspend users.
4. Driver profile onboarding.
5. Admin approve/reject driver profile.
6. Driver online/offline sau khi profile da `APPROVED`.
7. Passenger estimate/create booking/list booking.
8. WebSocket subscribe driver offer va passenger trip status.
9. Driver accept/reject offer.
10. Trip status buttons.
11. Tracking realtime.
12. In-trip messaging history + realtime topic.
13. Cash payment confirm.
14. Rating create + public rating list.
15. WebSocket auth production: gui token trong STOMP `CONNECT`, test reconnect khi access token het han.
