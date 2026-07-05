# GoRide Implementation Log

> Muc dich: ghi lai noi dung trien khai theo tung commit de review nhanh va giu trace giua code voi phase trong `docs/backend-implementation.md`.
>
> Tu commit `feat: add matching driver search` tro di, moi commit backend can cap nhat file nay trong cung commit.

---
## Commit: `feat: add scheduled ride dispatch`

Branch: `feature/scheduled-rides`

Phase: Phase 6 - Product expansion, scheduled rides

### Muc tieu

Cho phep passenger dat xe trong tuong lai ma khong gui offer cho driver ngay lap tuc. Backend giu trip o `SCHEDULED`, sau do scheduler tu mo matching gan gio don de tai su dung flow booking -> matching -> driver offer hien co.

### Noi dung da trien khai

- Them `TripStatus.SCHEDULED` vao active statuses va cancelable statuses.
- Them `scheduledPickupTime` vao `BookingCreateRequest` va `TripResponse`.
- Them state machine tren `Trip`:
  - `createScheduled(...)` tao trip status `SCHEDULED`.
  - `dispatchScheduled()` chuyen `SCHEDULED -> SEARCHING`.
- Cap nhat `BookingService`:
  - Booking dat ngay van tao `SEARCHING` va publish `BookingCreatedEvent` de matching ngay.
  - Booking dat lich validate min lead time theo config, tao `SCHEDULED`, ghi history va khong publish matching event ngay.
- Them `ScheduledRideProperties` va config `app.booking.scheduled-rides.*`.
- Them `ScheduledRideDispatchService` va `ScheduledRideDispatchScheduler`:
  - Quet batch trip `SCHEDULED` co `scheduledPickupTime <= now + dispatchLeadTime`.
  - Lock trip, ghi history `SCHEDULED -> SEARCHING`, publish `BookingCreatedEvent` va broadcast trip status `SEARCHING` sau transaction commit.
- Them SQL release `db/releases/20260704-scheduled-rides` de them cot `trips.scheduled_pickup_time` va index dispatch.
- Cap nhat `plan.md`, `integrate-plan.md`, `docs/implementation-log.md` va `docs/pland.xlsx`.

### Review truoc commit

- Targeted `./mvnw.cmd "-Dtest=TripTests,TripStatusTests,BookingServiceTests,ScheduledRideDispatchServiceTests" test`: pass 24 tests.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-db-release.ps1 -ReleasePath db/releases/20260704-scheduled-rides`: pass.
- Full `./mvnw.cmd test`: pass 383 tests, 0 failure, 0 error.
- `git diff --check`: pass; chi co warning LF/CRLF tren Windows.
- CodeRabbit CLI: blocked vi `coderabbit` khong co trong PATH; installer chinh thuc chi ho tro Linux/macOS va WSL tren may nay khong khoi chay duoc `/bin/bash`.

### Files chinh

- `src/main/java/com/example/goride/booking/domain/Trip.java`
- `src/main/java/com/example/goride/booking/domain/TripStatus.java`
- `src/main/java/com/example/goride/booking/dto/BookingCreateRequest.java`
- `src/main/java/com/example/goride/booking/dto/TripResponse.java`
- `src/main/java/com/example/goride/booking/service/ScheduledRideDispatchService.java`
- `src/main/java/com/example/goride/booking/service/ScheduledRideDispatchScheduler.java`
- `src/main/java/com/example/goride/booking/service/ScheduledRideProperties.java`
- `db/releases/20260704-scheduled-rides/**`
- `src/test/java/com/example/goride/booking/service/ScheduledRideDispatchServiceTests.java`
- `plan.md`
- `integrate-plan.md`
- `docs/implementation-log.md`
- `docs/pland.xlsx`

### Viec tiep theo

- FE them option dat lich tren man booking confirm, gui `scheduledPickupTime` ISO-8601 UTC khi dat lich.
- FE hien trang thai `SCHEDULED`, cho phep huy, subscribe trip status topic va chuyen sang finding-driver khi backend broadcast `SEARCHING` gan gio don.
- UAT can tune `SCHEDULED_RIDES_MIN_LEAD_TIME_MINUTES`, `SCHEDULED_RIDES_DISPATCH_LEAD_TIME_MINUTES` va scheduler delay theo thuc te van hanh.
---
## Commit: `feat: add in-trip messaging`

Branch: `feature/in-trip-messaging`

Phase: Phase 6 - Product expansion, in-trip messaging

### Muc tieu

Them passenger-driver chat theo tung trip de FE co the hien thi hop thoai trong active trip, vua co realtime WebSocket vua co REST fallback/lich su.

### Noi dung da trien khai

- Them entity `TripMessage` va enum `TripMessageSenderRole` de luu message theo `trip_id`, `sender_id`, role nguoi gui, body va `sent_at`.
- Them SQL release `db/releases/20260701-trip-messages` gom manifest, precheck, apply, verify va rollback.
- Them `TripMessageService`:
  - Chi cho passenger cua trip hoac assigned driver gui message.
  - Chi cho gui khi trip da `ACCEPTED`, `ARRIVED` hoac `IN_PROGRESS`.
  - Admin duoc xem history de ho tro van hanh nhung khong gui thay participant.
  - Broadcast message sau khi transaction commit.
- Them REST API:
  - `GET /api/v1/trips/{tripId}/messages?page=1&size=50` lay lich su message.
  - `POST /api/v1/trips/{tripId}/messages` gui message qua REST fallback.
- Them STOMP API `SEND /app/trip.message` va realtime topic `/topic/trip/{tripId}/messages`.
- Mo rong `TripTopicSubscriptionAuthorizer` de topic messages dung chung rule subscribe voi status/location.
- Them error code `TRIP_MESSAGE_NOT_AVAILABLE` cho FE disable input khi status khong cho chat.
- Cap nhat `plan.md`, `integrate-plan.md`, `docs/backend-implementation.md` va `docs/pland.xlsx`.

### Review truoc commit

- Targeted `./mvnw.cmd "-Dtest=TripMessage*Tests,WebSocketTripMessageRealtimeNotifierTests,TripTopicSubscriptionAuthorizerTests" test`: pass 18 tests.
- Full `./mvnw.cmd test`: pass 379 tests, 0 failure, 0 error.
- `git diff --check`: pass; chi co warning LF/CRLF tren Windows.
- CodeRabbit CLI: blocked vi `coderabbit` khong co trong PATH; installer chinh thuc chi ho tro Linux/macOS va WSL tren may nay khong khoi chay duoc `/bin/bash`.

### Files chinh

- `src/main/java/com/example/goride/chat/**`
- `src/main/java/com/example/goride/booking/security/TripTopicSubscriptionAuthorizer.java`
- `src/main/java/com/example/goride/common/error/ErrorCode.java`
- `db/releases/20260701-trip-messages/**`
- `src/test/java/com/example/goride/chat/**`
- `src/test/java/com/example/goride/booking/security/TripTopicSubscriptionAuthorizerTests.java`
- `plan.md`
- `integrate-plan.md`
- `docs/backend-implementation.md`
- `docs/implementation-log.md`
- `docs/pland.xlsx`

### Viec tiep theo

- FE them chat panel trong active trip, load history qua REST, subscribe `/topic/trip/{tripId}/messages` va gui qua REST hoac STOMP.
- Neu can attachment/read receipt/typing indicator, tach commit rieng sau MVP text chat.

## Commit: `feat: add cloudflare r2 storage provider`

Branch: `feature/upload-storage`

Phase: Phase 6 - Product expansion, production upload storage

### Muc tieu

Them storage provider Cloudflare R2/S3-compatible de upload avatar va driver documents len object storage khi staging/production set `STORAGE_PROVIDER=r2`, trong khi van giu local filesystem provider lam mac dinh cho dev/test.

### Noi dung da trien khai

- Them AWS SDK v2 S3 dependency va `R2StorageConfig` tao `S3Client` dung endpoint Cloudflare R2, region `auto`, path-style access va timeout cau hinh duoc.
- Mo rong `FileStorageProperties` voi `provider=local|r2` va nhom config `app.storage.r2.*`.
- Refactor validation/object-key/public-URL upload vao `FileStorageSupport` de local va R2 dung chung rule.
- Them `R2FileStorageService`:
  - Validate file rong, size limit va content type nhu local provider.
  - Upload object bang `PutObject` voi bucket/key/content type/content length.
  - Tra `StoredFile.url` theo `CLOUDFLARE_R2_PUBLIC_BASE_URL + objectKey`.
  - Map loi IO/R2 SDK thanh `FILE_STORAGE_ERROR`.
- Dieu chinh local resource handler `/uploads/**` chi active khi provider la `local`.
- Them env vars trong `application.properties`: `STORAGE_PROVIDER`, `CLOUDFLARE_R2_ENDPOINT`, `CLOUDFLARE_R2_BUCKET`, `CLOUDFLARE_R2_ACCESS_KEY`, `CLOUDFLARE_R2_SECRET_KEY`, `CLOUDFLARE_R2_PUBLIC_BASE_URL`, timeout va path-style flag.
- Cap nhat `plan.md`, `integrate-plan.md`, `docs/backend-implementation.md` va `docs/pland.xlsx` de ghi ro R2 provider da co code, con can bucket/secret/domain UAT.

### Review truoc commit

- Targeted `./mvnw.cmd "-Dtest=R2FileStorageServiceTests,R2StorageConfigTests,LocalFileStorageServiceTests" test`: pass 8 tests.
- Full `./mvnw.cmd test`: pass 366 tests, 0 failure, 0 error.
- `git diff --check`: pass; chi co warning LF/CRLF tren Windows.
- CodeRabbit CLI: blocked vi `coderabbit` khong co trong PATH; remote installer can approval ro rang truoc khi cai third-party software.

### Files chinh

- `pom.xml`
- `src/main/java/com/example/goride/storage/config/FileStorageProperties.java`
- `src/main/java/com/example/goride/storage/config/FileStorageConfig.java`
- `src/main/java/com/example/goride/storage/config/R2StorageConfig.java`
- `src/main/java/com/example/goride/storage/service/FileStorageSupport.java`
- `src/main/java/com/example/goride/storage/service/LocalFileStorageService.java`
- `src/main/java/com/example/goride/storage/service/R2FileStorageService.java`
- `src/main/resources/application.properties`
- `src/test/java/com/example/goride/storage/config/R2StorageConfigTests.java`
- `src/test/java/com/example/goride/storage/service/R2FileStorageServiceTests.java`
- `plan.md`
- `integrate-plan.md`
- `docs/backend-implementation.md`
- `docs/implementation-log.md`
- `docs/pland.xlsx`

### Viec tiep theo

- Tao R2 bucket, API token va public/custom domain ngoai repo.
- Set env vars `CLOUDFLARE_R2_*` tren staging, upload thu mot avatar va mot driver document.
- Neu giay to tai xe can private access, them signed URL/proxy download thay vi public bucket truoc production.

## Commit: `feat: add upload storage foundation`

Branch: `feature/upload-storage`

Phase: Phase 6 - Product expansion, upload storage foundation

### Muc tieu

Them nen tang upload file cho avatar user va anh ho so tai xe de FE co the upload anh truoc khi tao ho so driver. Backend validate file, luu local trong dev/demo, tra public URL va luu document URL vao driver profile response de admin co the xem khi duyet.

### Noi dung da trien khai

- Them storage module local filesystem:
  - `FileStorageProperties` voi `STORAGE_LOCAL_ROOT`, `STORAGE_PUBLIC_BASE_URL`, `STORAGE_MAX_FILE_SIZE`, `STORAGE_ALLOWED_CONTENT_TYPES`.
  - `LocalFileStorageService` validate file rong, size limit va content type `image/jpeg`, `image/png`, `image/webp`.
  - Public resource handler `/uploads/**` cho local/dev.
- Them API upload:
  - `POST /api/users/me/avatar` multipart `file`, upload va cap nhat `users.avatar_url`.
  - `POST /api/v1/uploads/driver-documents/{documentType}` multipart `file` cho `PORTRAIT`, `LICENSE`, `ID_CARD`, `VEHICLE_REGISTRATION`.
- Them response upload driver document gom `documentType`, `url`, `objectKey`, `contentType`, `sizeBytes`.
- Mo rong `DriverProfile` voi `licenseImageUrl`, `idCardImageUrl`, `vehicleRegistrationUrl` de admin xem URL tai lieu trong pending profile/approval response.
- Them SQL release `db/releases/20260630-driver-document-urls` cho 3 cot driver document URL.
- Them `FILE_UPLOAD_INVALID` va `FILE_STORAGE_ERROR`; upload qua dung luong tra loi co cau truc thay vi 500.
- Cap nhat `plan.md`, `integrate-plan.md`, `docs/backend-implementation.md` va `docs/pland.xlsx`.

### Review truoc commit

- Targeted `./mvnw.cmd "-Dtest=LocalFileStorageServiceTests,FileUploadControllerTests,UserProfileControllerTests,UserServiceTests,DriverProfileServiceTests" test`: pass 29 tests.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-db-release.ps1 -ReleasePath db/releases/20260630-driver-document-urls`: pass.
- Full `./mvnw.cmd test`: pass 362 tests, 0 failure, 0 error.
- `git diff --check`: pass; chi co warning LF/CRLF tren Windows.
- CodeRabbit CLI: blocked vi `coderabbit` khong co trong PATH; remote installer can approval ro rang truoc khi cai third-party software.

### Files chinh

- `src/main/java/com/example/goride/storage/**`
- `src/main/java/com/example/goride/user/controller/UserProfileController.java`
- `src/main/java/com/example/goride/user/service/UserService.java`
- `src/main/java/com/example/goride/driver/domain/DriverProfile.java`
- `src/main/java/com/example/goride/driver/dto/DriverProfileUpsertRequest.java`
- `src/main/java/com/example/goride/driver/dto/DriverProfileResponse.java`
- `db/releases/20260630-driver-document-urls/**`
- `src/test/java/com/example/goride/storage/**`
- `src/test/java/com/example/goride/user/controller/UserProfileControllerTests.java`
- `src/test/java/com/example/goride/user/service/UserServiceTests.java`
- `src/test/java/com/example/goride/driver/service/DriverProfileServiceTests.java`
- `plan.md`
- `integrate-plan.md`
- `docs/backend-implementation.md`
- `docs/implementation-log.md`
- `docs/pland.xlsx`

### Viec tiep theo

- Quyet dinh production storage: S3-compatible bucket, CDN URL hoac persistent volume.
- Neu dung S3, them provider rieng sau abstraction `FileStorageService` va giu local provider cho dev/test.
- Can full regression suite truoc khi merge neu review chap nhan scope nay.

## Commit: `chore: add database release sql workflow`

Branch: `feature/database-release-sql-workflow`

Phase: Phase 4 - Production readiness, database release strategy without Flyway

### Muc tieu

Thiet lap quy trinh release database co version control vi project khong dung Flyway. Muc tieu la moi thay doi schema staging/production co SQL rieng, co precheck, apply, verify, rollback va co validator truoc khi review/deploy.

### Noi dung da trien khai

- Them `db/releases/README.md` mo ta quy uoc release SQL khong dung Flyway.
- Them template `db/releases/0000-template` gom:
  - `manifest.yml`
  - `precheck.sql`
  - `apply.sql`
  - `verify.sql`
  - `rollback.sql`
- Them `scripts/validate-db-release.ps1`:
  - Validate tung release folder bang `-ReleasePath` hoac tat ca bang `-All`.
  - Kiem tra required files va required manifest fields.
  - Dam bao `release_id` khop ten folder.
  - Yeu cau `BEGIN;`/`COMMIT;` trong `apply.sql` khi `transactional=true`.
  - Chan SQL destructive neu chua co comment `-- destructive-reviewed: true`.
  - Khong ket noi DB va khong thuc thi SQL.
- Them `docs/database-release-process.md` voi checklist deploy, rollback va GoRide-specific notes cho Postgres/PostGIS.
- Cap nhat `docs/backend-implementation.md`, `plan.md`, `integrate-plan.md` va `docs/pland.xlsx` de danh dau database release strategy da co workflow.

### Review truoc commit

- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-db-release.ps1 -ReleasePath db/releases/0000-template`: pass.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-db-release.ps1 -All`: pass.
- Full `./mvnw.cmd test`: pending; branch nay chi thay doi docs/script release SQL.
- `git diff --check`: pass; chi co warning LF/CRLF tren Windows.
- CodeRabbit CLI: blocked vi `coderabbit` khong co trong PATH; remote installer bi approval policy tu choi vi se cai third-party software len may khi chua co phe duyet ro rang.

### Files chinh

- `db/releases/README.md`
- `db/releases/0000-template/manifest.yml`
- `db/releases/0000-template/precheck.sql`
- `db/releases/0000-template/apply.sql`
- `db/releases/0000-template/verify.sql`
- `db/releases/0000-template/rollback.sql`
- `scripts/validate-db-release.ps1`
- `docs/database-release-process.md`
- `docs/backend-implementation.md`
- `plan.md`
- `integrate-plan.md`
- `docs/implementation-log.md`
- `docs/pland.xlsx`

### Viec tiep theo

- Khi commit schema moi, tao release folder that tu template thay vi sua DB truc tiep.
- Can quyet dinh environment production se set Hibernate schema mode nao de khong mutate schema tu dong.
- Tiep tuc cac muc con lai: real payment sandbox UAT, log shipping/distributed tracing deployment, upload storage.
## Commit: `feat: add payment sandbox uat harness`

Branch: `feature/payment-sandbox-uat-harness`

Phase: Phase 1 - Payment provider completion, sandbox UAT handoff

### Muc tieu

Them endpoint admin/devops de tong hop checklist UAT sandbox cho MoMo/VNPAY tu readiness hien co, giup FE va nguoi van hanh biet provider nao da san sang test, callback endpoint nao can dang ky va con thieu cau hinh nao truoc khi expose online payment.

### Noi dung da trien khai

- Them `PaymentSandboxUatPlanResponse` voi prerequisites, provider checklist va validation scenarios.
- Them `PaymentSandboxUatPlanService`:
  - Doc readiness MoMo/VNPAY hien co va config callback da normalize.
  - Tra `status` theo cac trang thai `NOT_REGISTERED`, `DISABLED`, `SANDBOX_DISABLED`, `BLOCKED_BY_CONFIG`, `READY_FOR_SANDBOX_UAT`.
  - Tra endpoint checkout/webhook, `returnUrl`, `ipnUrl`, missing requirements, action cho FE va backend checks ma khong lo secret/access key.
- Them admin endpoint `GET /api/v1/payments/providers/sandbox-uat-plan` trong `PaymentController`, bao ve bang `hasRole('ADMIN')`.
- Them unit tests cho controller delegation va service output khi provider disabled, ready va sandbox mode bi tat.
- Cap nhat `plan.md`, `integrate-plan.md` va `docs/pland.xlsx`; real merchant sandbox E2E cua MoMo/VNPAY van con open.

### Review truoc commit

- Targeted `./mvnw.cmd "-Dtest=PaymentControllerTests,PaymentSandboxUatPlanServiceTests" test`: pass 8 tests.
- Full `./mvnw.cmd test`: pass 354 tests, 0 failure, 0 error.
- `git diff --check`: pass; chi co warning LF/CRLF tren Windows.
- CodeRabbit CLI: blocked vi `coderabbit` khong co trong PATH; buoc chay remote installer bi approval policy tu choi vi se cai third-party software len may khi chua co phe duyet ro rang.

### Files chinh

- `src/main/java/com/example/goride/payment/controller/PaymentController.java`
- `src/main/java/com/example/goride/payment/dto/PaymentSandboxUatPlanResponse.java`
- `src/main/java/com/example/goride/payment/service/PaymentSandboxUatPlanService.java`
- `src/test/java/com/example/goride/payment/controller/PaymentControllerTests.java`
- `src/test/java/com/example/goride/payment/service/PaymentSandboxUatPlanServiceTests.java`
- `plan.md`
- `integrate-plan.md`
- `docs/implementation-log.md`
- `docs/pland.xlsx`

### Viec tiep theo

- Chay real sandbox UAT voi merchant account va callback URL public cho MoMo/VNPAY.
- Sau UAT, tune freshness window theo hanh vi retry cua tung provider neu can.
- Khi sandbox pass, cap nhat payment metadata de FE expose online payment tren moi truong staging/production phu hop.

## Commit: `feat: add prometheus observability metrics`

Branch: `feature/observability-metrics`

Phase: Phase 4 - Production readiness

### Muc tieu

Bo sung metrics foundation de devops co the scrape Prometheus, theo doi HTTP latency/status va trace rate-limit decisions theo allowed/rejected.

### Noi dung da trien khai

- Them dependency `micrometer-registry-prometheus`.
- Expose `/actuator/metrics`, `/actuator/metrics/{meterName}` va `/actuator/prometheus` trong actuator config va Spring Security allowlist.
- Gan common tag `application=goride` cho metrics.
- Bat histogram cho `http.server.requests` qua config `HTTP_SERVER_REQUESTS_HISTOGRAM`.
- Them custom metrics cho rate limiter:
  - `goride.rate.limit.requests` voi tag `outcome=allowed|rejected`.
  - `goride.rate.limit.buckets` gauge so in-memory buckets dang track.
- Them `ObservabilityMetricsIntegrationTests` cover actuator discovery, Prometheus scrape endpoint va custom rate-limit metrics.
- Dieu chinh `GorideApplicationTests` override readiness group trong no-DB test context de full suite load duoc application context.
- Cap nhat `plan.md`, `integrate-plan.md` va `docs/pland.xlsx`; production hardening van con log shipping, distributed tracing va deployment dashboards.

### Review truoc commit

- Full `./mvnw.cmd test`: pass 350 tests, 0 failure, 0 error.
- `git diff --check`: pass; chi con warning LF/CRLF tren Windows.
- CodeRabbit CLI: blocked vi `coderabbit` khong co trong PATH; buoc chay remote installer bi approval policy tu choi vi se cai third-party software len may khi chua co phe duyet ro rang.

### Files chinh

- `pom.xml`
- `src/main/java/com/example/goride/auth/config/SecurityConfig.java`
- `src/main/java/com/example/goride/common/ratelimit/RateLimitFilter.java`
- `src/main/resources/application.properties`
- `src/test/resources/application.properties`
- `src/test/java/com/example/goride/GorideApplicationTests.java`
- `src/test/java/com/example/goride/common/observability/ObservabilityMetricsIntegrationTests.java`
- `plan.md`
- `integrate-plan.md`
- `docs/implementation-log.md`
- `docs/pland.xlsx`

## Commit: `feat: add api rate limiting`

Branch: `feature/rate-limit-policy`

Phase: Phase 4 - Production readiness

### Muc tieu

Them rate limit token-bucket co the cau hinh de bao ve API khoi request burst va cung cap contract HTTP 429 ro rang cho FE retry/backoff.

### Noi dung da trien khai

- Them `RateLimitProperties`, `RateLimitDecision`, `InMemoryRateLimitStore` va `RateLimitFilter`.
- Wire filter vao Spring Security chain sau CORS de browser FE van doc duoc CORS response va headers rate-limit.
- Mac dinh gioi han `120` request/phut theo client IP; co the override bang `RATE_LIMIT_*` environment variables.
- Exclude mac dinh cac path diagnostics/docs/WebSocket: `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/ws/**`, `/ws-native/**`.
- Them error code `RATE_LIMIT_EXCEEDED` HTTP 429, response body co `retryAfterSeconds` va header `Retry-After`.
- Expose `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` qua CORS.
- Them `RateLimitFilterIntegrationTests` cover login throttle va actuator exclusion.
- Cap nhat `plan.md`, `integrate-plan.md` va `docs/pland.xlsx` de danh dau API rate limiting da co; production hardening tong the van can log shipping va metrics/tracing backend.

### Review truoc commit

- Targeted `./mvnw.cmd -Dtest=RateLimitFilterIntegrationTests,SecurityCorsIntegrationTests test`: pass 5 tests.
- `git diff --check`: pass.
- CodeRabbit CLI: blocked vi `coderabbit` khong co trong PATH; buoc chay remote installer bi approval policy tu choi vi se cai third-party software len may khi chua co phe duyet ro rang.

### Files chinh

- `src/main/java/com/example/goride/common/ratelimit/RateLimitProperties.java`
- `src/main/java/com/example/goride/common/ratelimit/RateLimitDecision.java`
- `src/main/java/com/example/goride/common/ratelimit/InMemoryRateLimitStore.java`
- `src/main/java/com/example/goride/common/ratelimit/RateLimitFilter.java`
- `src/main/java/com/example/goride/auth/config/SecurityConfig.java`
- `src/main/java/com/example/goride/auth/config/CorsProperties.java`
- `src/main/java/com/example/goride/common/error/ErrorCode.java`
- `src/main/resources/application.properties`
- `src/test/resources/application.properties`
- `src/test/java/com/example/goride/common/ratelimit/RateLimitFilterIntegrationTests.java`
- `src/test/java/com/example/goride/auth/config/SecurityCorsIntegrationTests.java`
- `plan.md`
- `integrate-plan.md`
- `docs/implementation-log.md`
- `docs/pland.xlsx`

## Commit: `feat: add configurable cors policy`

Branch: `feature/production-health-readiness`

Phase: Phase 4 - Production readiness

### Muc tieu

Them CORS allowlist co the cau hinh bang environment variables de web FE/admin dashboard goi backend an toan, dong thoi cho preflight `OPTIONS` di qua truoc JWT authentication va expose `X-Request-Id` cho FE trace loi.

### Noi dung da trien khai

- Them `CorsProperties` voi prefix `app.security.cors`.
- Wire `http.cors(...)` trong `SecurityConfig` bang `CorsConfigurationSource` rieng.
- Cau hinh default local origins trong `application.properties`:
  - `http://localhost:3000`
  - `http://localhost:5173`
  - `http://localhost:19006`
  - `http://127.0.0.1:5173`
- Ho tro override qua env vars `CORS_ALLOWED_ORIGINS`, `CORS_ALLOWED_ORIGIN_PATTERNS`, `CORS_ALLOWED_METHODS`, `CORS_ALLOWED_HEADERS`, `CORS_EXPOSED_HEADERS`, `CORS_ALLOW_CREDENTIALS`, `CORS_MAX_AGE_SECONDS`.
- Expose response header `X-Request-Id` qua CORS de FE doc duoc request trace id.
- Them `SecurityCorsIntegrationTests` cover:
  - Preflight tu allowed origin vao protected endpoint khong bi JWT chan.
  - Actual request tu allowed origin co `Access-Control-Allow-Origin` va `Access-Control-Expose-Headers`.
  - Origin ngoai allowlist bi reject.
- Cap nhat `plan.md`, `integrate-plan.md` va `docs/pland.xlsx`.

### Review truoc commit

- Targeted `./mvnw.cmd -Dtest=SecurityCorsIntegrationTests test`: pass 3 tests.
- `git diff --check`: pass; chi con warning LF/CRLF tren Windows.
- CodeRabbit CLI: blocked vi `coderabbit` khong co trong PATH; buoc chay remote installer bi approval policy tu choi vi se cai third-party software len may khi chua co phe duyet ro rang.

### Files chinh

- `src/main/java/com/example/goride/auth/config/CorsProperties.java`
- `src/main/java/com/example/goride/auth/config/SecurityConfig.java`
- `src/main/resources/application.properties`
- `src/test/resources/application.properties`
- `src/test/java/com/example/goride/auth/config/SecurityCorsIntegrationTests.java`
- `plan.md`
- `integrate-plan.md`
- `docs/implementation-log.md`
- `docs/pland.xlsx`

---
## Commit: `feat: add actuator health readiness endpoints`

Branch: `feature/production-health-readiness`

Phase: Phase 4 - Production readiness

### Muc tieu

Bo sung health/readiness endpoints de deployment, load balancer va smoke test co the kiem tra trang thai app ma khong can JWT. Readiness phan biet dependency DB/Redis san sang voi liveness cua process ung dung.

### Noi dung da trien khai

- Them `spring-boot-starter-actuator`.
- Them `src/main/resources/application.properties` chua management defaults commit duoc rieng, khong dung vao `application.yml` local.
- Expose public `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` va `/actuator/info` trong `SecurityConfig`.
- Cau hinh health probes:
  - Liveness chi include `livenessState`.
  - Readiness include `readinessState`, `db`, `redis`.
  - Khong show health details de tranh lo thong tin noi bo.
- Dong bo `src/test/resources/application.properties` de integration tests thay dung actuator config.
- Them `HealthReadinessIntegrationTests` chay qua full Spring security + Testcontainers PostGIS/Redis.
- Cap nhat `plan.md`, `integrate-plan.md` va `docs/pland.xlsx` de danh dau health/readiness review da hoan thanh.

### Review truoc commit

- Targeted `./mvnw.cmd -Dtest=HealthReadinessIntegrationTests test`: pass 1 test voi Docker/Testcontainers.
- `git diff --check`: pass; chi con warning LF/CRLF tren Windows.
- Lan chay sandbox dau tien bi chan Docker pipe; rerun escalated thanh cong.
- CodeRabbit CLI: blocked vi `coderabbit` khong co trong PATH; buoc chay remote installer bi approval policy tu choi vi se cai third-party software len may khi chua co phe duyet ro rang.

### Files chinh

- `pom.xml`
- `src/main/java/com/example/goride/auth/config/SecurityConfig.java`
- `src/main/resources/application.properties`
- `src/test/resources/application.properties`
- `src/test/java/com/example/goride/integration/HealthReadinessIntegrationTests.java`
- `plan.md`
- `integrate-plan.md`
- `docs/implementation-log.md`
- `docs/pland.xlsx`

---
## Commit: `ci: run backend tests on github actions`

Branch: `feature/trip-completion-payment-integration`

Phase: Phase 5 - Integration confidence

### Muc tieu

Dua suite backend vao CI de moi push/PR len `main` hoac `develop` co the chay `./mvnw test` tren runner Linux co Docker, phu hop voi Testcontainers PostGIS/Redis hien co.

### Noi dung da trien khai

- Them `.github/workflows/backend-ci.yml`.
- Workflow chay khi push len `main`, `develop`, `feature/**` va khi pull request vao `main`/`develop`.
- Setup Java 17 Temurin dung Maven cache cua `actions/setup-java`.
- Verify Docker bang `docker version` truoc khi chay test de loi Testcontainers ro rang hon.
- Chay `./mvnw test` tren GitHub-hosted `ubuntu-latest` runner.
- Dat concurrency theo workflow/ref de huy run cu khi push commit moi vao cung branch.
- Cap nhat `plan.md`, `integrate-plan.md` va `docs/pland.xlsx` de danh dau CI wiring da co; provider sandbox E2E van con mo.

### Review truoc commit

- Local `git diff --check`: pass; chi con warning LF/CRLF tren Windows.
- Full `./mvnw.cmd test`: khong can chay lai rieng cho thay doi YAML/docs nay, suite se duoc CI chay sau khi push.
- CodeRabbit CLI: blocked vi `coderabbit` khong co trong PATH; buoc chay remote installer bi approval policy tu choi vi se cai third-party software len may khi chua co phe duyet ro rang.

### Files chinh

- `.github/workflows/backend-ci.yml`
- `plan.md`
- `integrate-plan.md`
- `docs/implementation-log.md`
- `docs/pland.xlsx`

---

## Commit: `test: cover admin flow integration`

Branch: `feature/trip-completion-payment-integration`

Phase: Phase 5 - Integration confidence

### Muc tieu

Tang coverage P1 cho admin backend flow bang integration test chay qua HTTP/JWT/JPA/PostGIS, dong thoi sua loi `GET /api/v1/admin/trips` bi 500 tren Postgres khi khong truyen filter ngay thang.

### Noi dung da trien khai

- Them `AdminFlowIntegrationTests` dung Testcontainers PostGIS/Redis base hien co.
- Test passenger bi chan `FORBIDDEN` khi goi admin dashboard.
- Test driver tao profile `PENDING`, admin list pending drivers va approve driver.
- Test admin tao/list/deactivate pricing config.
- Test admin list trips khong filter tra `200` voi pagination mac dinh.
- Test admin dashboard tra tong users/drivers/approval counts sau approve.
- Sua `AdminTripService` dung JPA `Specification` thay vi JPQL optional null parameters de Postgres suy luan type on dinh.
- Cap nhat `AdminTripServiceTests` theo repository `findAll(spec, pageable)`.
- Cap nhat `plan.md`, `integrate-plan.md` va `docs/pland.xlsx` de ghi nhan admin integration coverage da co.

### Review truoc commit

- Targeted `./mvnw.cmd -Dtest=AdminFlowIntegrationTests,AdminTripServiceTests test`: pass 4 tests.
- Full `./mvnw.cmd test`: chua chay trong buoc nay.
- `git diff --check`: pass; chi con warning CRLF tren Windows.
- CodeRabbit CLI: blocked vi `coderabbit` khong co trong PATH; buoc chay remote installer bi approval policy tu choi vi se cai third-party software len may khi chua co phe duyet ro rang.

### Files chinh

- `src/test/java/com/example/goride/integration/AdminFlowIntegrationTests.java`
- `src/main/java/com/example/goride/booking/service/AdminTripService.java`
- `src/main/java/com/example/goride/booking/repository/TripRepository.java`
- `src/test/java/com/example/goride/booking/service/AdminTripServiceTests.java`
- `plan.md`
- `integrate-plan.md`
- `docs/implementation-log.md`
- `docs/pland.xlsx`

---

## Commit: `test: cover notification flow integration`

Branch: `feature/trip-completion-payment-integration`

Phase: Phase 5 - Integration confidence

### Muc tieu

Tang coverage P1 cho notification flow bang integration test chay qua full Spring HTTP/JWT/JPA/Redis, gom FCM token CRUD va notification inbox/mark-read.

### Noi dung da trien khai

- Them `NotificationFlowIntegrationTests` dung Testcontainers PostGIS/Redis base hien co.
- Test tao passenger bang REST auth register de lay JWT that.
- Test goi `PUT /api/v1/notifications/fcm-token`, verify token duoc trim va luu Redis qua `FcmDeviceTokenStore`.
- Test tao `UserNotification` qua `TripRealtimeNotifier`, verify inbox REST list tra notification da luu DB va payload deep-link.
- Test goi `PATCH /api/v1/notifications/{notificationId}/read`, verify read/readAt.
- Test goi `DELETE /api/v1/notifications/fcm-token`, verify Redis token da xoa.
- Cap nhat `plan.md`, `integrate-plan.md` va `docs/pland.xlsx` de ghi nhan notification integration coverage da co.

### Review truoc commit

- Targeted `./mvnw.cmd -Dtest=NotificationFlowIntegrationTests test`: pass 1 test.
- Full `./mvnw.cmd test`: chua chay trong buoc nay.
- `git diff --check`: pass; chi con warning CRLF tren Windows.
- CodeRabbit CLI: blocked vi `coderabbit` khong co trong PATH; buoc chay remote installer bi approval policy tu choi vi se cai third-party software len may khi chua co phe duyet ro rang.

### Files chinh

- `src/test/java/com/example/goride/integration/NotificationFlowIntegrationTests.java`
- `plan.md`
- `integrate-plan.md`
- `docs/implementation-log.md`
- `docs/pland.xlsx`

---

## Commit: `fix: dismiss driver offer on passenger cancellation`

Branch: `feature/trip-completion-payment-integration`

Phase: Phase 5 - Integration confidence / booking-matching bug fix

### Muc tieu

Khac phuc loi passenger huy booking khi trip dang `SEARCHING` nhung popup offer tren man hinh driver van hien thi cho toi khi TTL timeout.

### Noi dung da trien khai

- Them `BookingCancelledEvent` va publish sau transaction commit trong `BookingService.cancelBooking`.
- Them `BookingCancelledMatchingListener` de doc active matching state trong Redis, release lock cua driver dang duoc offer va clear trip matching state.
- Them `DriverOfferCancelledNotification` va mo rong `DriverOfferNotifier` de gui payload dismiss qua `/user/queue/trip-requests`.
- Cap nhat `integrate-plan.md` huong dan FE dong offer modal khi nhan `type=TRIP_CANCELLED`, `action=DISMISS`.
- Cap nhat `plan.md` ghi ro matching da cleanup/dismiss stale offer khi passenger huy.

### Review truoc commit

- Targeted `./mvnw.cmd -Dtest=BookingServiceTests,BookingCancelledMatchingListenerTests,WebSocketDriverOfferNotifierTests test`: pass 15 tests.
- Full `./mvnw.cmd test`: chua chay trong buoc nay.
- `git diff --check`: pass; chi con warning CRLF tren Windows.
- CodeRabbit CLI: blocked vi `coderabbit` khong co trong PATH; buoc chay remote installer bi approval policy tu choi vi se cai third-party software len may khi chua co phe duyet ro rang.

### Files chinh

- `src/main/java/com/example/goride/booking/event/BookingCancelledEvent.java`
- `src/main/java/com/example/goride/booking/service/BookingService.java`
- `src/main/java/com/example/goride/matching/service/BookingCancelledMatchingListener.java`
- `src/main/java/com/example/goride/matching/notification/DriverOfferCancelledNotification.java`
- `src/main/java/com/example/goride/matching/notification/DriverOfferNotifier.java`
- `src/main/java/com/example/goride/matching/notification/WebSocketDriverOfferNotifier.java`
- `integrate-plan.md`
- `plan.md`

---

## Commit: `test: cover trip completion payment integration`

Branch: `feature/trip-completion-payment-integration`

Phase: Phase 5 - Integration confidence

### Muc tieu

Them coverage tich hop cho flow MVP sau khi driver da nhan booking: passenger tao booking, driver accept/arrived/start/complete, backend tinh final fare tu tracking history, tao cash payment pending, FE lay payment detail/checkout, driver confirm tien mat va duoc dua ve hang heartbeat/available.

### Noi dung da trien khai

- Mo rong `BookingMatchingRoutingIntegrationTests` bang scenario end-to-end moi dung MockMvc + Testcontainers PostGIS/Redis.
- Refactor test helper admin/driver profile dung phone/email/license suffix rieng de moi scenario khong dung unique constraint.
- Scenario moi cover:
  - Booking CASH va matching driver gan nhat.
  - Driver accept offer, update `ARRIVED`, `IN_PROGRESS`, `COMPLETED`.
  - Driver location update trong `IN_PROGRESS`, passenger doc latest location qua REST fallback.
  - Trip completed co `finalFare` va `completedAt`.
  - Payment detail tra `PENDING`, `method=CASH`, amount khop `finalFare`.
  - Cash checkout tra `checkoutRequired=false`.
  - Driver goi `PATCH /payment-confirm`, payment thanh `COMPLETED`, `paidAt` duoc set.
  - Driver heartbeat thanh cong sau payment completion workflow dua driver ve availability queue.
- Cap nhat `plan.md`, `integrate-plan.md` va `docs/pland.xlsx` de danh dau trip completion/tracking/cash payment integration coverage da co.

### Review truoc commit

- Targeted `./mvnw.cmd -Dtest=BookingMatchingRoutingIntegrationTests test`: pass 2 tests.
- Full `./mvnw.cmd test`: pass 338 tests.
- `git diff --check`: pass.
- CodeRabbit CLI: blocked. `coderabbit --version` khong tim thay lenh; installer mac dinh fail vi `sh` khong co trong PATH; installer qua Git Bash fail voi `Unsupported operating system: mingw64_nt-10.0-26200`.

### Files chinh

- `src/test/java/com/example/goride/integration/BookingMatchingRoutingIntegrationTests.java`
- `plan.md`
- `integrate-plan.md`
- `docs/implementation-log.md`
- `docs/pland.xlsx`

---

## Commit: `test: add payment sandbox webhook contract coverage`

Branch: `feature/payment-sandbox-callback-uat-support`

Phase: Phase 1 - Payment provider completion, sandbox callback UAT support

### Muc tieu

Tang do tin cay cho buoc UAT sandbox MoMo/VNPAY bang service-level contract coverage: backend dispatch callback qua `PaymentWebhookService` voi signed payload giong provider, dung received time co the co dinh trong test, va van giu real merchant sandbox test la viec con lai.

### Noi dung da trien khai

- Inject `Clock` vao `PaymentWebhookService` de `PaymentWebhookRequest.receivedAt` co the test on dinh thay vi goi `Instant.now()` truc tiep.
- Them `PaymentSandboxWebhookContractTests` chay qua provider registry va provider implementation that:
  - MoMo signed IPN success cap nhat payment `COMPLETED` va goi completion workflow.
  - VNPAY signed callback failure cap nhat payment `FAILED` va khong goi completion workflow.
  - VNPAY signed callback qua cu bi reject theo freshness window truoc khi save payment.
- Cap nhat `integrate-plan.md`, `plan.md` va `docs/pland.xlsx` de ghi ro backend da co contract coverage nhung van can sandbox account/E2E callback that.

### Review truoc commit

- Payment webhook/provider sandbox targeted suite: pass 41 tests.
- Full `./mvnw.cmd test`: pass 337 tests.
- `git diff --check`: pass.
- CodeRabbit CLI: blocked vi `coderabbit` chua cai; installer mac dinh fail do khong co `sh`, installer qua Git Bash fail voi `Unsupported operating system: mingw64_nt-10.0-26200`.

### Files chinh

- `src/main/java/com/example/goride/payment/service/PaymentWebhookService.java`
- `src/test/java/com/example/goride/payment/service/PaymentWebhookServiceTests.java`
- `src/test/java/com/example/goride/payment/service/PaymentSandboxWebhookContractTests.java`
- `integrate-plan.md`
- `plan.md`
- `docs/pland.xlsx`

---

## Commit: `feat: add payment provider readiness diagnostics`

Branch: `feature/payment-provider-sandbox-e2e`

Phase: Phase 1 - Payment provider completion

### Muc tieu

Them buoc readiness gate cho MoMo/VNPAY truoc khi chay sandbox E2E that. Backend can bao ro provider da registered/enabled chua, checkout/webhook config da du chua, va con thieu requirement nao ma khong lo secret.

### Noi dung da trien khai

- Them `PaymentProviderReadinessService` de danh gia tung online provider (`MOMO`, `VNPAY`).
- Them DTO `PaymentProviderReadinessResponse` gom:
  - `providerRegistered`, `enabled`, `checkoutConfigured`, `webhookConfigured`.
  - `checkoutReady`, `webhookReady`, `sandboxReady`.
  - `missingRequirements` nhu `provider-enabled`, `merchant-id`, `webhook-secret`.
  - `webhookMaxAgeSeconds`, `webhookFutureSkewSeconds` de kiem tra freshness policy dang ap dung.
- Them admin endpoint `GET /api/v1/payments/providers/readiness`.
- Cap nhat `integrate-plan.md` va `plan.md` de FE/admin/devops dung readiness endpoint truoc khi expose MoMo/VNPAY.

### Review truoc commit

- Payment readiness/controller/method targeted suite: pass 10 tests.
- Chua commit; cho user review patch.

### Files chinh

- `src/main/java/com/example/goride/payment/dto/PaymentProviderReadinessResponse.java`
- `src/main/java/com/example/goride/payment/service/PaymentProviderReadinessService.java`
- `src/main/java/com/example/goride/payment/controller/PaymentController.java`
- `src/test/java/com/example/goride/payment/service/PaymentProviderReadinessServiceTests.java`
- `src/test/java/com/example/goride/payment/controller/PaymentControllerTests.java`
- `integrate-plan.md`
- `plan.md`

---

## Commit: `fix: keep searching trips after driver rejection`

Branch: `develop`

Phase: Booking -> matching retry stabilization

### Muc tieu

Sua loi tren thiet bi that: passenger dat chuyen, offer gui den driver, driver bam tu choi thi trip cua passenger bi chuyen sang ket thuc/tim khong thay tai xe thay vi tiep tuc tim driver khac.

### Noi dung da trien khai

- Doi behavior khi driver reject offer:
  - Release lock driver hien tai va clear matching state active.
  - Them driver vua reject vao excluded list khi thu candidate tiep theo.
  - Neu co driver tiep theo: gui offer moi nhu cu.
  - Neu chua co driver tiep theo ngay luc do: giu trip o `SEARCHING`, khong save `NO_DRIVER`, khong gui `NO_DRIVER_FOUND` cho passenger.
- Doi behavior offer timeout tuong tu reject:
  - Driver timeout duoc exclude o attempt tiep theo.
  - Neu chua co candidate moi, trip van `SEARCHING` de co the rematch khi driver khac online/heartbeat.
- Loai bo gioi han terminal theo `MAX_MATCHING_ATTEMPTS`; attempt van tang de trace retry, nhung khong dung de huy trip som.
- Cap nhat `integrate-plan.md` va `plan.md` de FE giu passenger o man hinh searching sau reject/timeout.

### Review truoc commit

- Chua commit; cho user review patch.

### Files chinh

- `src/main/java/com/example/goride/matching/service/DriverOfferResponseService.java`
- `src/main/java/com/example/goride/matching/service/MatchingOfferTimeoutService.java`
- `src/test/java/com/example/goride/matching/service/DriverOfferResponseServiceTests.java`
- `src/test/java/com/example/goride/matching/service/MatchingOfferTimeoutServiceTests.java`
- `integrate-plan.md`
- `plan.md`

---

## Commit: `fix: complete initial matching and SockJS handshake`

Branch: `feature/request-tracing-logging`

Phase: Booking -> matching -> realtime integration stabilization

### Muc tieu

Sua hai loi phat hien tu backend log khi passenger dat xe: SockJS goi `/ws/info` bi 500 vi backend chi dang ky native WebSocket, va trip bi giu o `SEARCHING` khi initial matching khong tim thay driver.

### Noi dung da trien khai

- Doi `/ws` thanh SockJS/STOMP endpoint:
  - `GET /ws/info` duoc SockJS phuc vu.
  - Them `/ws-native` cho client dung native WebSocket voi `brokerURL`.
  - Mo Spring Security handshake routes cho ca hai endpoint; JWT van duoc kiem tra tai STOMP `CONNECT`.
- Hoan thien initial matching no-driver:
  - Neu initial matching chua co candidate, giu trip o `SEARCHING` thay vi terminal `NO_DRIVER`.
  - Them `DriverAvailableEvent` khi driver online hoac heartbeat thanh cong.
  - Them `SearchingTripMatchingService` de thu match lai cac trip `SEARCHING` chua co offer active khi co driver available.
  - Bo qua trip da co active matching state de tranh gui trung offer.
  - Them domain log cho offer dau tien, initial no-candidate va rematch tu driver availability.
- Cap nhat `integrate-plan.md`:
  - Phan biet URL SockJS va native WebSocket.
  - FE chi poll driver location sau khi trip da co driver.
  - Khi trip con `SEARCHING`, FE hien dang tim tai xe va cho WebSocket status/offer thay vi coi `DRIVER_LOCATION_NOT_FOUND` la loi.

### Review truoc commit

- Auth/logging/STOMP/WebSocket/driver/matching targeted suite: pass 46 tests.
- Docker-backed `AuthFlowIntegrationTests` da them assertion `/ws/info` nhung chua rerun duoc vi Docker Desktop dang dong.
- Chua commit; cho user review patch.

### Files chinh

- `src/main/java/com/example/goride/common/config/WebSocketConfig.java`
- `src/main/java/com/example/goride/auth/config/SecurityConfig.java`
- `src/main/java/com/example/goride/matching/service/BookingCreatedMatchingListener.java`
- `src/test/java/com/example/goride/common/config/WebSocketConfigTests.java`
- `src/test/java/com/example/goride/matching/service/BookingCreatedMatchingListenerTests.java`
- `src/test/java/com/example/goride/integration/AuthFlowIntegrationTests.java`
- `integrate-plan.md`
- `plan.md`
- `docs/pland.xlsx`

---

## Commit: `fix: broadcast driver approach location`

Branch: `feature/request-tracing-logging`

Phase: Booking -> matching -> driver approach tracking stabilization

### Muc tieu

Sua loi passenger chi thay vi tri tai xe sau khi driver bam bat dau chuyen. Log cho thay driver da gui `/app/driver.location` sau khi accept, nhung backend chi chap nhan trip `IN_PROGRESS`, nen toa do khi tai xe dang den diem don bi tu choi voi "Driver has no in-progress trip".

### Noi dung da trien khai

- Cho phep driver update location khi trip o cac status:
  - `ACCEPTED`
  - `ARRIVED`
  - `IN_PROGRESS`
- Them repository query lay current tracking trip cua driver theo status active assigned moi nhat.
- Tach behavior tracking:
  - `ACCEPTED`/`ARRIVED`: chi cache latest location va broadcast realtime cho passenger xem tai xe dang den diem don.
  - `IN_PROGRESS`: cache/broadcast dong thoi luu `TripLocationHistory` de tinh actual distance/final fare.
- Cap nhat FE guide:
  - Passenger co the subscribe/poll driver location sau khi trip co driver va status la `ACCEPTED`, `ARRIVED` hoac `IN_PROGRESS`.
  - Khong poll khi `SEARCHING` hoac `NO_DRIVER`.

### Review truoc commit

- Auth/logging/STOMP/WebSocket/driver/matching/tracking targeted suite: pass 57 tests.
- Docker-backed `BookingMatchingRoutingIntegrationTests` chua rerun duoc vi Docker Desktop/dockerdang dong (`docker_engine` access denied).
- Chua commit; cho user review patch.

### Files chinh

- `src/main/java/com/example/goride/booking/repository/TripRepository.java`
- `src/main/java/com/example/goride/tracking/service/TripLocationTrackingService.java`
- `src/test/java/com/example/goride/tracking/service/TripLocationTrackingServiceTests.java`
- `integrate-plan.md`
- `plan.md`
- `docs/pland.xlsx`

---

## Commit: `feat: add request tracing logs`

Branch: `feature/request-tracing-logging`

Phase: Phase 4 - Production readiness, observability foundation

### Muc tieu

Them correlation ID va logging tap trung de truy vet loi REST tu FE den backend ma khong ghi request body, password, token hoac query string nhay cam.

### Noi dung da trien khai

- Them `RequestCorrelationFilter` chay truoc Spring Security:
  - Nhan `X-Request-Id` an toan tu client hoac tu sinh UUID.
  - Gan request ID vao SLF4J MDC va tra lai trong response header.
  - Log method, path, HTTP status va duration cho moi request.
  - Loai request ID khoi MDC sau request de tranh ro ri giua servlet threads.
- Them `requestId` vao error response de FE/support doi chieu truc tiep voi backend log.
- Them centralized exception logging:
  - Business/validation/auth/access-denied log WARN voi method, path va error code/field names.
  - Unexpected exception log ERROR kem full stack trace.
- Them security entry-point/access-denied logging cho loi xay ra truoc controller.
- Them console log pattern co request ID; khong log request body, Authorization header, token, password hoac query string.
- Cap nhat FE integration guide de client tao, nhan va luu `X-Request-Id` khi bao loi.

### Review truoc commit

- Targeted request tracing va auth integration tests: pass 4 tests.
- `./mvnw.cmd test`: pass 325 tests, 0 failure, 0 error.
- `git diff --check`: khong co whitespace error.
- CodeRabbit khong chay duoc: CLI chua cai va official installer execution da bi environment security policy chan o luong cai dat truoc; khong gan nhan CodeRabbit cho ket qua review khac.

### Files chinh

- `src/main/java/com/example/goride/common/logging/RequestCorrelationFilter.java`
- `src/main/java/com/example/goride/common/api/ErrorResponse.java`
- `src/main/java/com/example/goride/common/error/GlobalExceptionHandler.java`
- `src/main/java/com/example/goride/auth/security/RestAuthenticationEntryPoint.java`
- `src/main/java/com/example/goride/auth/security/RestAccessDeniedHandler.java`
- `src/test/java/com/example/goride/common/logging/RequestCorrelationFilterTests.java`
- `src/test/java/com/example/goride/common/api/ErrorResponseTests.java`
- `integrate-plan.md`
- `plan.md`
- `docs/pland.xlsx`

### Viec tiep theo

- Ship stdout log vao centralized logging platform va dat retention/search/alert rules.
- Them metrics, health/readiness va distributed tracing neu tach service.
- Them rate limit va production CORS policy.

---

## Commit: `test: cover booking matching routing flow`

Branch: `feature/booking-matching-routing-integration`

Phase: Phase 5 - Integration confidence

### Muc tieu

Bao phu bang integration test luong backend quan trong tu passenger dat xe, Redis matching tai xe, tai xe nhan chuyen, routing den diem don va chuyen routing sang diem tra sau khi den noi don.

### Noi dung da trien khai

- Them `BookingMatchingRoutingIntegrationTests` dung full Spring context, MockMvc, PostGIS va Redis Testcontainers.
- Thuc thi flow qua REST/JWT that:
  - Dang ky passenger va driver.
  - Seed admin bootstrap, dang nhap qua auth API, tao va approve driver profile.
  - Dua driver online gan pickup de Redis matching tao offer.
  - Passenger tao booking, driver accept offer va trip chuyen sang `ACCEPTED`.
  - Tai xe lay route `PICKUP`, cap nhat `ARRIVED`, sau do lay route `DROPOFF`.
- Khoi dong HTTP server cuc bo mo phong bien OSRM:
  - Tra distance/duration cho booking estimate.
  - Tra GeoJSON `LineString` va maneuver step cho driver navigation.
  - Khong phu thuoc network/provider ngoai trong test suite.
- Khong thay doi REST API contract, database schema hoac production configuration.

### Review truoc commit

- Targeted `BookingMatchingRoutingIntegrationTests`: pass 1 test.
- `./mvnw.cmd test`: pass 322 tests, 0 failure, 0 error.
- CodeRabbit khong chay duoc: CLI chua cai va official installer execution da bi environment security policy chan o luong cai dat truoc; khong gan nhan CodeRabbit cho ket qua review khac.

### Files chinh

- `src/test/java/com/example/goride/integration/BookingMatchingRoutingIntegrationTests.java`
- `integrate-plan.md`
- `plan.md`
- `docs/pland.xlsx`

### Viec tiep theo

- Mo rong flow qua `IN_PROGRESS`, tracking history, final fare va payment completion.
- Them notification/admin integration flows.
- Cau hinh Docker-backed integration suite tren CI.

---

## Commit: `feat: add driver trip routing`

Branch: `feature/driver-trip-routing`

Phase: Phase 2 - Real-world routing, driver navigation

### Muc tieu

Bo sung API routing cho app tai xe tu vi tri GPS hien tai den diem don hoac diem tra, tra ve GeoJSON route geometry va maneuver steps de FE ve polyline va hien huong dan dieu huong.

### Noi dung da trien khai

- Them `POST /api/v1/drivers/trips/{tripId}/route`:
  - Chi role `DRIVER` va assigned driver cua trip duoc goi.
  - Request nhan `latitude`/`longitude` GPS hien tai va validate WGS84 range.
  - Trip `ACCEPTED` tu dong route den `PICKUP`.
  - Trip `ARRIVED` hoac `IN_PROGRESS` tu dong route den `DROPOFF`.
  - Status khac tra `TRIP_ROUTE_NOT_AVAILABLE`.
- Them OSRM route geometry provider:
  - Goi route service theo thu tu `longitude,latitude`.
  - Dung `overview=full`, `geometries=geojson`, `steps=true`.
  - Verify distance, duration, GeoJSON `LineString`, coordinates va maneuver locations.
  - Provider disabled, timeout, HTTP error, `NoRoute` hoac payload sai tra `ROUTING_PROVIDER_ERROR` HTTP 502; navigation geometry khong dung duong thang Haversine gia lap.
- Response cho FE gom destination type/address/location, distance meter, duration second, GeoJSON geometry va maneuver steps.
- Them environment mappings `ROUTING_ENABLED`, `ROUTING_BASE_URL`, `ROUTING_PROFILE`, `ROUTING_TIMEOUT_SECONDS`, `ROUTING_FALLBACK_ENABLED` vao `application.properties`.

### Review truoc commit

- Targeted routing/controller/service/context tests: pass 10 tests.
- Targeted routing suite cung estimate provider: pass 15 tests.
- `./mvnw.cmd test`: pass 321 tests, 0 failure, 0 error.
- `git diff --check`: khong co whitespace error.
- CodeRabbit khong chay duoc: CLI chua cai va environment security policy khong cho thuc thi official downloaded installer script; khong gan nhan CodeRabbit cho ket qua review khac.

### Files chinh

- `src/main/java/com/example/goride/booking/service/distance/OsrmRouteGeometryProvider.java`
- `src/main/java/com/example/goride/booking/service/distance/RouteGeometryProvider.java`
- `src/main/java/com/example/goride/driver/service/DriverTripRoutingService.java`
- `src/main/java/com/example/goride/driver/controller/DriverTripController.java`
- `src/main/java/com/example/goride/driver/dto/DriverTripRouteResponse.java`
- `src/test/java/com/example/goride/booking/service/distance/OsrmRouteGeometryProviderTests.java`
- `src/test/java/com/example/goride/driver/service/DriverTripRoutingServiceTests.java`
- `src/test/java/com/example/goride/driver/controller/DriverTripControllerTests.java`
- `integrate-plan.md`
- `plan.md`
- `docs/pland.xlsx`

### Viec tiep theo

- UAT voi OSRM-compatible endpoint production/self-hosted va route that tai Viet Nam.
- FE debounce/re-route theo GPS movement va localize maneuver type/modifier.
- Them caching/rate limiting/observability cho route requests truoc production.

---

## Commit: `test: add auth integration flow`

Branch: `feature/auth-integration-tests`

Phase: Phase 5 - Integration confidence

### Muc tieu

Tao nen integration test dung dich vu PostGIS va Redis that, sau do bao phu auth flow qua HTTP de xac nhan REST controller, Spring Security/JWT, JPA va refresh-token state hoat dong cung nhau.

### Noi dung da trien khai

- Them `PostgresRedisIntegrationTest` lam base dung chung cho integration tests:
  - Khoi dong `postgis/postgis:15-3.3` va `redis:7-alpine` bang Testcontainers.
  - Gan datasource/Redis runtime ports qua `@DynamicPropertySource`.
  - Dung `hibernate.ddl-auto=create-drop` cho schema test rieng.
  - Tat matching timeout, driver availability scheduler va FCM de test on dinh, khong goi provider ngoai.
- Them `AuthFlowIntegrationTests` voi `MockMvc` va full Spring context:
  - Dang ky passenger va nhan access/refresh token.
  - Dung access token goi notification inbox duoc bao ve boi JWT.
  - Refresh token duoc rotate; token cu bi reject.
  - Logout revoke token moi; token da logout bi reject.
  - Dang ky trung phone tra `PHONE_ALREADY_EXISTS`.
- Khong thay doi REST API contract hoac production configuration.

### Review truoc commit

- Targeted `AuthFlowIntegrationTests`: pass 1 test.
- `./mvnw.cmd test`: pass 312 tests, 0 failure, 0 error.
- `git diff --check`: khong co whitespace error.
- CodeRabbit khong chay duoc: CLI chua cai va environment security policy khong cho thuc thi official downloaded installer script; khong gan nhan CodeRabbit cho ket qua review khac.

### Files chinh

- `src/test/java/com/example/goride/integration/PostgresRedisIntegrationTest.java`
- `src/test/java/com/example/goride/integration/AuthFlowIntegrationTests.java`
- `integrate-plan.md`
- `plan.md`
- `docs/pland.xlsx`

### Viec tiep theo

- Mo rong integration coverage cho booking/matching va trip lifecycle.
- Them tracking/payment/notification/admin integration flows.
- Dua Docker-backed integration suite vao CI.

---

## Commit: `feat: add firebase production config`

Branch: `feature/firebase-production-config`

Phase: Phase 4 - Production readiness, Firebase credential loading

### Muc tieu

Hoan thien cach Firebase Admin SDK nhan credential trong staging/production ma khong commit service account JSON, dong thoi fail-fast khi FCM duoc bat nhung deployment cau hinh sai.

### Noi dung da trien khai

- Chuyen credential loading sang hai che do:
  - Neu co `FIREBASE_SERVICE_ACCOUNT_PATH`, doc credential file duoc mount ngoai application/image.
  - Neu khong co path rieng, dung `GoogleCredentials.getApplicationDefault()` de ho tro `GOOGLE_APPLICATION_CREDENTIALS`, workload identity federation va attached service account tren Google Cloud.
- Them `FirebaseAdminStartupValidator`:
  - Chi tao bean khi `app.notifications.fcm.enabled=true`.
  - Khoi tao Firebase Messaging ngay luc startup.
  - Credential thieu, file khong doc duoc hoac JSON sai lam startup fail voi message neu ro credential source.
- Them environment mapping trong `application.properties`:
  - `FCM_ENABLED`, `FCM_MAX_ATTEMPTS`.
  - `FIREBASE_SERVICE_ACCOUNT_PATH`, `FIREBASE_PROJECT_ID`, `FIREBASE_APP_NAME`.
  - Local/dev mac dinh van `FCM_ENABLED=false`.
- Them Git ignore cho `.env`, `.env.*`, `secrets/` va `firebase-service-account*.json`; van cho phep commit `.env.example`.
- Khong thay doi REST/FCM token contract cua frontend.

### Review truoc commit

- Da xac nhan default credential source la Google Application Default Credentials.
- Da xac nhan explicit service-account path duoc uu tien va error message neu dung path khong doc duoc.
- Da xac nhan startup validator goi Firebase initialization.
- Da xac nhan Spring context local van load khi FCM disabled.
- Targeted tests: pass 16 tests.
- `./mvnw.cmd test`: pass 311 tests.
- `git diff --check`: khong co whitespace error.
- CodeRabbit khong chay duoc: CLI chua cai va environment security policy khong cho thuc thi official downloaded installer script; khong gan nhan CodeRabbit cho ket qua review khac.

### Files chinh

- `.gitignore`
- `src/main/resources/application.properties`
- `src/main/java/com/example/goride/notification/config/FcmPushProperties.java`
- `src/main/java/com/example/goride/notification/service/FirebaseMessagingGateway.java`
- `src/main/java/com/example/goride/notification/service/FirebaseAdminMessagingGateway.java`
- `src/main/java/com/example/goride/notification/service/FirebaseAdminStartupValidator.java`
- `src/test/java/com/example/goride/notification/config/FcmPushPropertiesTests.java`
- `src/test/java/com/example/goride/notification/service/FirebaseAdminMessagingGatewayTests.java`
- `src/test/java/com/example/goride/notification/service/FirebaseAdminStartupValidatorTests.java`
- `integrate-plan.md`
- `plan.md`
- `docs/pland.xlsx`

### Viec tiep theo

- Cau hinh workload identity hoac secret mount that tren staging va gui mot test push.
- Tiep tuc E2E/integration tests hoac production hardening theo plan.

---

## Commit: `feat: add driver heartbeat timeout`

Branch: `feature/driver-heartbeat-timeout`

Phase: Phase 3 - Driver availability reliability

### Muc tieu

Bo sung heartbeat rieng cho driver dang online va dong bo offline tu dong khi driver mat ket noi. Redis TTL tiep tuc la lop chan nhanh cho matching, con scheduler cap nhat lai `driver_profiles.is_online` de database khong giu trang thai stale.

### Noi dung da trien khai

- Them `POST /api/v1/drivers/me/heartbeat`:
  - Yeu cau role `DRIVER`, profile ton tai va dang online.
  - Nhan `lat`/`lng` bat buoc, cap nhat Redis GEO va `last_known_location`/`last_location_at`.
  - Tra `heartbeatAt` va `expiresAt` de FE lap lich heartbeat tiep theo.
  - Neu Redis status da het TTL, tra `DRIVER_NOT_AVAILABLE` va khong tu dong hoi sinh availability.
- Tap trung config vao `app.driver.availability`:
  - `heartbeat-timeout-seconds`, mac dinh 60 va toi thieu 10.
  - `cleanup-batch-size`, mac dinh 100.
  - Scheduler mac dinh chay moi 15 giay, initial delay 30 giay va co the disable bang config.
- Mo rong `DriverAvailabilityStore.refreshHeartbeat`:
  - Gia han status/meta TTL va cap nhat GEO/metadata.
  - Khong ghi lai gia tri status, vi vay `BUSY` duoc giu nguyen.
  - Status da het han se khong duoc tao lai; matching tiep tuc bo qua driver.
- Them `DriverHeartbeatTimeoutService`:
  - Dung pessimistic lock lay profile online co `lastLocationAt` cu hon timeout.
  - Xu ly theo batch, chuyen database sang offline.
  - Sau transaction commit, xoa driver khoi Redis GEO/status/meta.
- Them `Clock` bean de heartbeat va timeout co thoi gian nhat quan, test duoc.

### Review truoc commit

- Da xac nhan heartbeat cap nhat location va expiry dung 60 giay.
- Da xac nhan driver offline va Redis status da expire bi reject.
- Da xac nhan heartbeat khong ghi de status `BUSY`.
- Da xac nhan scheduler dung cutoff va batch size config, chuyen stale profile offline va don Redis.
- Targeted tests: pass 24 tests.
- `./mvnw.cmd test`: pass 309 tests.
- `git diff --check`: khong co whitespace error.
- CodeRabbit khong chay duoc: CLI chua cai va environment security policy khong cho thuc thi official downloaded installer script; khong gan nhan CodeRabbit cho ket qua review khac.

### Files chinh

- `src/main/java/com/example/goride/driver/controller/DriverStatusController.java`
- `src/main/java/com/example/goride/driver/service/DriverHeartbeatService.java`
- `src/main/java/com/example/goride/driver/service/DriverHeartbeatTimeoutService.java`
- `src/main/java/com/example/goride/driver/service/DriverHeartbeatTimeoutScheduler.java`
- `src/main/java/com/example/goride/driver/service/availability/RedisDriverAvailabilityStore.java`
- `src/main/java/com/example/goride/driver/service/availability/DriverAvailabilityProperties.java`
- `src/main/java/com/example/goride/driver/repository/DriverProfileRepository.java`
- `integrate-plan.md`
- `plan.md`
- `docs/pland.xlsx`

### Viec tiep theo

- UAT heartbeat interval/timeout voi app mobile o background va mang khong on dinh.
- Them metrics cho heartbeat reject, Redis expiry va so driver bi scheduler cleanup.
- Tiep tuc Firebase production secret setup hoac integration test phase theo plan.

---

## Commit: `feat: add real distance routing provider`

Branch: `feature/real-distance-provider`

Phase: Phase 8 - Real-world routing and fare estimation

### Muc tieu

Thay `MockDistanceService` bang routing provider OSRM-compatible cho fare estimate va booking creation. Provider duoc bat/tat bang config, co timeout va fallback Haversine ro rang de local/dev van hoat dong khi chua co routing server.

### Noi dung da trien khai

- Them `RoutingDistanceService` lam `DistanceService` duy nhat:
  - Goi OSRM-compatible `GET /route/v1/{profile}/{longitude,latitude;longitude,latitude}`.
  - Dung `overview=false&steps=false` de chi lay distance/duration can cho pricing.
  - Chuyen `distance` tu meter sang km, lam tron 2 chu so.
  - Chuyen `duration` tu giay sang phut va lam tron len.
- Them `RoutingProperties` voi prefix `app.routing`:
  - `enabled`, mac dinh `false`.
  - `base-url`, mac dinh `https://router.project-osrm.org`.
  - `profile`, mac dinh `driving`.
  - `timeout-seconds`, mac dinh `5`.
  - `fallback-enabled`, mac dinh `true`.
  - Validate HTTP base URL, safe profile va timeout duong; fail fast khi provider enabled nhung config sai.
- Fallback:
  - Giu cong thuc Haversine x 1.25 va toc do thanh pho 25 km/h lam fallback noi bo.
  - Provider timeout, HTTP error, `NoRoute`, empty/invalid route se log warning va fallback neu enabled.
  - Neu `fallback-enabled=false`, tra `ROUTING_PROVIDER_ERROR` HTTP 502.
- Khong doi API contract:
  - `POST /api/v1/bookings/estimate` va booking create van tra distance km, duration minutes va fare nhu cu.
  - Final fare sau trip van dung actual tracking history va actual duration, khong goi routing provider.

### Review truoc commit

- Da xac nhan OSRM request dung thu tu `longitude,latitude`.
- Da xac nhan 5425.6 meter map thanh 5.43 km va 987.1 giay map thanh 17 phut.
- Da xac nhan provider `NoRoute`, timeout va HTTP error co fallback.
- Da xac nhan fallback disabled tra `ROUTING_PROVIDER_ERROR`.
- Da xac nhan provider enabled voi config URL sai fail fast.
- Da chay targeted tests: pass 20 tests.
- Da chay `./mvnw.cmd test`: pass 297 tests.
- Da chay `git diff --check`: khong co whitespace error.
- CodeRabbit khong chay duoc: CLI chua cai va environment security policy khong cho thuc thi official downloaded installer script; khong gan nhan CodeRabbit cho ket qua review khac.

### Files chinh

- `src/main/java/com/example/goride/booking/config/RoutingConfig.java`
- `src/main/java/com/example/goride/booking/service/distance/RoutingProperties.java`
- `src/main/java/com/example/goride/booking/service/distance/RoutingDistanceService.java`
- `src/main/java/com/example/goride/common/error/ErrorCode.java`
- `src/test/java/com/example/goride/booking/service/distance/RoutingPropertiesTests.java`
- `src/test/java/com/example/goride/booking/service/distance/RoutingDistanceServiceTests.java`
- `integrate-plan.md`
- `plan.md`
- `docs/pland.xlsx`

### Viec tiep theo

- Chay UAT voi OSRM self-hosted hoac routing endpoint duoc chon cho production.
- Theo doi fallback warning va dieu chinh timeout/profile theo ha tang that.
- Tiep tuc Phase 3 driver heartbeat/offline timeout.

---

## Commit: `feat: enforce payment webhook freshness`

Branch: `feature/payment-webhook-freshness`

Phase: Phase 7 - Payment provider completion, callback freshness and replay policy

### Muc tieu

Bo sung freshness policy dung chung cho MoMo va VNPAY de reject callback co timestamp da ky qua cu hoac nam qua xa trong tuong lai, dong thoi van chap nhan provider retry cu neu payment da o terminal state voi dung provider va transaction reference.

### Noi dung da trien khai

- Them `PaymentWebhookFreshnessPolicy`:
  - Mac dinh callback dau tien hop le trong 24 gio.
  - Cho phep clock skew toi da 5 phut trong tuong lai.
  - Callback qua cu chi duoc chap nhan neu la idempotent replay cua terminal payment voi cung provider va transaction reference.
  - Timestamp nam trong tuong lai qua clock skew luon bi reject, ke ca callback lap.
- Mo rong config provider:
  - `webhook-max-age-seconds`, mac dinh `86400`.
  - `webhook-future-skew-seconds`, mac dinh `300`.
  - Reject config max age khong duong hoac future skew am.
- Tich hop MoMo:
  - Dung `responseTime` epoch milliseconds trong canonical signed IPN.
  - Verify chu ky va du lieu callback truoc khi ap dung freshness policy va cap nhat payment.
- Tich hop VNPAY:
  - Bat buoc `vnp_PayDate` theo `yyyyMMddHHmmss`, timezone `Asia/Ho_Chi_Minh`.
  - Parse strict calendar date sau khi verify `vnp_SecureHash`.
  - Ap dung cung freshness/idempotent replay policy voi MoMo.
- Cap nhat frontend/config docs va `docs/pland.xlsx` de danh dau freshness policy hoan tat; sandbox merchant E2E van open.

### Review truoc commit

- Da xac nhan callback dung boundary 24 gio duoc chap nhan va qua boundary bi reject.
- Da xac nhan timestamp vuot qua future skew bi reject.
- Da xac nhan duplicate callback cu cung transaction reference van idempotent cho ca MoMo va VNPAY.
- Da xac nhan `vnp_PayDate` calendar khong hop le bi reject.
- Da chay targeted payment tests: pass 56 tests.
- Da chay `./mvnw.cmd test`: pass 288 tests.
- Da chay `git diff --check`: khong co whitespace error.
- CodeRabbit khong chay duoc: CLI chua cai va environment security policy khong cho thuc thi official downloaded installer script; khong gan nhan CodeRabbit cho ket qua review khac.

### Files chinh

- `src/main/java/com/example/goride/payment/config/PaymentProviderProperties.java`
- `src/main/java/com/example/goride/payment/provider/PaymentWebhookFreshnessPolicy.java`
- `src/main/java/com/example/goride/payment/provider/MoMoPaymentProvider.java`
- `src/main/java/com/example/goride/payment/provider/VnPayPaymentProvider.java`
- `src/test/java/com/example/goride/payment/config/PaymentProviderPropertiesTests.java`
- `src/test/java/com/example/goride/payment/provider/PaymentWebhookFreshnessPolicyTests.java`
- `src/test/java/com/example/goride/payment/provider/MoMoPaymentProviderTests.java`
- `src/test/java/com/example/goride/payment/provider/VnPayPaymentProviderTests.java`
- `src/test/java/com/example/goride/payment/service/PaymentMethodServiceTests.java`
- `integrate-plan.md`
- `plan.md`
- `docs/pland.xlsx`

### Viec tiep theo

- Chay MoMo/VNPAY sandbox E2E voi merchant account va callback URL that.
- Dieu chinh freshness window theo retry/UAT cua tung provider neu sandbox cho thay can thay doi.
- Tiep tuc Phase 2 real maps/distance provider sau khi payment sandbox duoc xac nhan.

---

## Commit: `feat: verify momo webhook callbacks`

Branch: `feature/momo-webhook-verification`

Phase: Phase 7 - Payment provider completion, MoMo IPN verification and payment completion

### Muc tieu

Hoan thien IPN/webhook cho MoMo de backend verify ket qua thanh toan server-to-server, cap nhat payment idempotent va tra HTTP 204 theo contract MoMo. Sandbox merchant E2E va freshness window rieng van de commit/validation sau.

### Noi dung da trien khai

- Mo rong `MoMoPaymentProvider.handleWebhook(...)`:
  - Parse cac field IPN: partner/order/request ID, amount, order info/type, transaction ID, result code, message, pay type, response time va extra data.
  - Verify HMAC-SHA256 theo canonical order cua MoMo truoc khi lookup payment.
  - Lay payment ID tu `GORIDE-PAY-{paymentId}` va load bang pessimistic lock.
  - Doi chieu partner code, `GORIDE-CREATE-{paymentId}`, amount, order info va `extraData=""` voi du lieu backend.
  - Map `resultCode=0` hoac `9000` thanh `COMPLETED` cho flow `captureWallet` auto-capture; cac result code khac thanh `FAILED`.
  - Luu MoMo `transId` lam transaction reference.
  - Callback success lap lai cung transaction reference la idempotent va khong chay completion workflow lan hai.
  - Callback success moi goi shared `PaymentCompletionWorkflow`.
- Mo rong config readiness:
  - Them `hasMomoWebhookConfiguration()` yeu cau partner code, access key va secret key.
- Mo rong `PaymentController`:
  - `POST /api/v1/payments/providers/momo/webhook` tra HTTP `204 No Content` sau khi xu ly IPN hop le.
  - Giu response contract hien tai cho generic POST provider va VNPAY GET/IPN.
- Cap nhat docs:
  - `integrate-plan.md` ghi ro FE khong goi IPN, MoMo tra HTTP 204 va FE refresh payment detail sau redirect.
  - `plan.md` danh dau MoMo IPN/payment completion da implement; sandbox E2E va freshness policy van open.
  - `docs/pland.xlsx` cap nhat status checkout/webhook MoMo.

### Review truoc commit

- Da doi chieu payload, canonical signature va HTTP 204 voi tai lieu MoMo Payment Notification.
- Da xac nhan invalid signature bi reject truoc repository lookup.
- Da xac nhan callback sai amount/partner/order/request/order info khong cap nhat payment.
- Da xac nhan duplicate success cung `transId` khong chay completion workflow lan hai.
- Da xac nhan failure callback khong chay completion workflow.
- Da chay targeted tests: pass 36 tests.
- Da chay `./mvnw.cmd test`: pass 275 tests.
- Da chay `git diff --check`: khong co whitespace error.
- CodeRabbit khong chay duoc: CLI chua cai va environment security policy khong cho thuc thi official downloaded installer script; khong gan nhan CodeRabbit cho ket qua review khac.

### Files chinh

- `src/main/java/com/example/goride/payment/provider/MoMoPaymentProvider.java`
- `src/main/java/com/example/goride/payment/config/PaymentProviderProperties.java`
- `src/main/java/com/example/goride/payment/controller/PaymentController.java`
- `src/test/java/com/example/goride/payment/provider/MoMoPaymentProviderTests.java`
- `src/test/java/com/example/goride/payment/controller/PaymentControllerTests.java`
- `src/test/java/com/example/goride/payment/config/PaymentProviderPropertiesTests.java`
- `src/test/java/com/example/goride/payment/service/PaymentMethodServiceTests.java`
- `integrate-plan.md`
- `plan.md`
- `docs/pland.xlsx`

### Viec tiep theo

- Chay MoMo/VNPAY sandbox E2E voi merchant account that.
- Dinh nghia freshness/replay policy khong lam hong legitimate provider retry.
- Tiep tuc Phase 2 real maps/distance provider sau khi payment sandbox duoc xac nhan.

---

## Commit: `feat: add momo checkout provider`

Branch: `feature/momo-checkout-provider`

Phase: Phase 7 - Payment provider completion, MoMo create-payment checkout foundation

### Muc tieu

Them MoMo create-payment provider cho checkout API hien tai. Commit nay chi tao checkout session va verify response cua MoMo; chua xu ly IPN/webhook, payment completion hoac provider HTTP 204.

### Noi dung da trien khai

- Mo rong payment provider config:
  - Them `access-key`.
  - Tach dieu kien config day du cho VNPAY va MoMo.
  - MoMo can partner code, access key, secret key, create URL, redirect URL va IPN URL.
- Them `MoMoPaymentProvider`:
  - Gui request `captureWallet`, amount VND, `extraData=""`, `lang=vi`.
  - Dung stable ID `GORIDE-PAY-{paymentId}` va `GORIDE-CREATE-{paymentId}` de provider idempotency hoat dong khi retry.
  - Ky request HMAC-SHA256 theo canonical order cua MoMo.
  - Verify HMAC-SHA256 response va doi chieu partner code, order ID, request ID, amount.
  - Chi chap nhan `resultCode=0` va `payUrl` HTTP/HTTPS hop le; checkout response co `expiresAt=null`.
  - Validate amount la so VND nguyen trong khoang 1.000 den 50.000.000.
- Them `RestClientMoMoPaymentClient`:
  - POST JSON den create-payment endpoint.
  - Connect/read timeout 30 giay.
  - Map timeout, HTTP error, URL config sai va response rong thanh `PAYMENT_PROVIDER_ERROR` HTTP 502.
- Cap nhat payment method metadata:
  - `MOMO.enabled=true` chi khi provider registered, config `enabled=true` va du config MoMo.
  - Mac dinh van disabled de khong expose flow chua co IPN.
- Cap nhat `plan.md`, `integrate-plan.md` va `docs/pland.xlsx`; MoMo checkout la done, payment provider tong the van partial va MoMo webhook van open.

### Review truoc commit

- Da doi chieu canonical signature request/response voi tai lieu MoMo create-payment.
- Da xac nhan retry cung payment giu nguyen order ID va request ID.
- Da xac nhan reject config thieu, amount ngoai range, response sai signature/du lieu, provider reject va pay URL khong hop le.
- Da xac nhan timeout, HTTP error va checkout URL sai duoc map thanh `PAYMENT_PROVIDER_ERROR`.
- Da chay targeted tests: pass 21 tests.
- Da chay `./mvnw.cmd test`: pass 262 tests.
- Da chay `git diff --check`: khong co whitespace error.
- CodeRabbit khong chay duoc: CLI chua cai va he thong bao mat tu choi lenh tai/thuc thi official installer script; khong gan nhan CodeRabbit cho ket qua review khac.

### Files chinh

- `src/main/java/com/example/goride/payment/config/PaymentProviderProperties.java`
- `src/main/java/com/example/goride/payment/provider/MoMoPaymentProvider.java`
- `src/main/java/com/example/goride/payment/provider/MoMoPaymentClient.java`
- `src/main/java/com/example/goride/payment/provider/RestClientMoMoPaymentClient.java`
- `src/main/java/com/example/goride/payment/provider/MoMoCreatePaymentRequest.java`
- `src/main/java/com/example/goride/payment/provider/MoMoCreatePaymentResponse.java`
- `src/main/java/com/example/goride/payment/service/PaymentMethodService.java`
- `src/main/java/com/example/goride/common/error/ErrorCode.java`
- `src/test/java/com/example/goride/payment/provider/MoMoPaymentProviderTests.java`
- `src/test/java/com/example/goride/payment/provider/RestClientMoMoPaymentClientTests.java`
- `integrate-plan.md`
- `plan.md`
- `docs/pland.xlsx`

### Viec tiep theo

- Implement MoMo IPN/webhook signature verification va shared payment completion workflow.
- Chay MoMo/VNPAY sandbox E2E voi merchant account that.
- Them callback replay/freshness policy neu provider payload co du du lieu.

---

## Commit: `feat: verify vnpay webhook callbacks`

Branch: `feature/vnpay-webhook-verification`

Phase: Phase 7 - Payment provider completion, VNPAY callback/IPN verification

### Muc tieu

Hoan thien phan webhook/callback sandbox cho VNPAY de backend co the nhan ket qua thanh toan, verify `vnp_SecureHash`, cap nhat payment va chay shared payment completion workflow. Commit nay van chua implement MoMo provider va chua them replay-window/freshness check.

### Noi dung da trien khai

- Mo rong `VnPayPaymentProvider`:
  - Parse cac tham so `vnp_*` tu webhook payload.
  - Verify `vnp_SecureHash` bang HMAC-SHA512 tren sorted query params, bo qua `vnp_SecureHash` va `vnp_SecureHashType`; chap nhan hex uppercase/lowercase.
  - Lay payment id tu `vnp_TxnRef` format `GORIDE-PAY-{paymentId}`.
  - Load payment bang pessimistic lock de xu ly callback idempotent/an toan hon.
  - Kiem tra payment method la `VNPAY`.
  - Kiem tra `vnp_TmnCode` khop merchant config va `vnp_Amount` khop payment amount x 100.
  - Map `vnp_ResponseCode=00` va `vnp_TransactionStatus=00` thanh payment `COMPLETED`; cac trang thai khac thanh `FAILED`.
  - Goi `PaymentCompletionWorkflow` khi payment moi chuyen sang `COMPLETED`.
- Mo rong `PaymentController`:
  - Them `GET /api/v1/payments/providers/{providerName}/webhook` de nhan callback/query params kieu VNPAY.
  - VNPAY GET/IPN tra raw JSON `RspCode`/`Message` theo contract provider: `00` confirm success, `01` order not found, `04` invalid amount, `97` invalid signature, `99` unknown error.
  - Giu `POST /api/v1/payments/providers/{providerName}/webhook` cho provider gui JSON callback.
- Mo rong `PaymentRepository`:
  - Them `findByIdForUpdate(...)` co `PESSIMISTIC_WRITE` va fetch trip/passenger/driver.
- Cap nhat docs:
  - `integrate-plan.md` ghi ro VNPAY callback GET/POST, FE khong goi webhook truc tiep va nen poll/refresh payment detail sau redirect.
  - `plan.md` danh dau VNPAY webhook sandbox/signature da partial/done trong pham vi VNPAY, MoMo van open.
  - `docs/pland.xlsx` cap nhat status tracking cho payment provider/webhook.

### Review truoc commit

- Da xac nhan invalid `vnp_SecureHash` bi reject truoc khi load payment.
- Da xac nhan secure hash uppercase/lowercase deu duoc verify dung.
- Da xac nhan callback sai amount/merchant khong cap nhat payment.
- Da xac nhan callback success lap lai cung transaction reference khong chay lai completion workflow.
- Da xac nhan VNPAY IPN response tra `RspCode=00` khi xu ly thanh cong va `RspCode=97` khi signature sai.
- Da chay `./mvnw.cmd '-Dtest=PaymentControllerTests,VnPayPaymentProviderTests,PaymentWebhookServiceTests,PaymentMethodServiceTests' test`: pass 16 tests.
- Da chay `./mvnw.cmd test`: pass 249 tests.
- Da chay `git diff --check`: khong co whitespace error.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.
- Da review diff thu cong: khong thay blocker trong scope VNPAY webhook verification.

### Files chinh

- `src/main/java/com/example/goride/payment/provider/VnPayPaymentProvider.java`
- `src/main/java/com/example/goride/payment/controller/PaymentController.java`
- `src/main/java/com/example/goride/payment/dto/VnPayIpnResponse.java`
- `src/main/java/com/example/goride/payment/repository/PaymentRepository.java`
- `src/test/java/com/example/goride/payment/controller/PaymentControllerTests.java`
- `src/test/java/com/example/goride/payment/provider/VnPayPaymentProviderTests.java`
- `src/test/java/com/example/goride/payment/service/PaymentMethodServiceTests.java`
- `integrate-plan.md`
- `plan.md`
- `docs/pland.xlsx`

### Viec tiep theo

- Implement MoMo checkout/webhook provider.
- Them replay-window/freshness check neu provider cung cap timestamp du tin cay.
- Hoan thien sandbox callback/end-to-end test voi VNPAY account that.

---

## Commit: `feat: add vnpay checkout provider`

Branch: `feature/vnpay-checkout-provider`

Phase: Phase 7 - Payment provider completion, VNPAY checkout sandbox foundation

### Muc tieu

Them provider runtime cho VNPAY de backend co the tao signed checkout URL khi payment method `VNPAY` duoc enable bang config. Commit nay chua xu ly return callback/IPN/webhook signature; cac phan do se tach commit sau de review rieng.

### Noi dung da trien khai

- Mo rong `PaymentProviderProperties.ProviderSettings`:
  - Them `returnUrl`, `ipnUrl`, `defaultIpAddress`.
  - `hasCheckoutConfiguration()` yeu cau them `returnUrl`.
  - `defaultIpAddress` fallback `127.0.0.1` neu config blank/missing.
- Them `VnPayPaymentProvider`:
  - `paymentMethod()` tra `PaymentMethod.VNPAY`.
  - Tao pending payment bang `Payment.createPending(trip)`.
  - Tao checkout URL tu `checkoutBaseUrl` voi cac field VNPAY co ban: `vnp_Version`, `vnp_Command`, `vnp_TmnCode`, `vnp_Amount`, `vnp_TxnRef`, `vnp_OrderInfo`, `vnp_ReturnUrl`, `vnp_IpAddr`, `vnp_CreateDate`, `vnp_ExpireDate`.
  - Ky URL bang `vnp_SecureHash` HMAC-SHA512 tren sorted query params.
  - Checkout session het han sau 15 phut.
- Cap nhat payment method metadata:
  - Khi VNPAY provider bean co trong registry va config checkout day du, `GET /api/v1/payments/methods` co the tra `VNPAY.enabled=true`.
  - MoMo van disabled cho den khi co provider implementation that.
- Cap nhat `integrate-plan.md`:
  - Ghi VNPAY da co checkout provider.
  - Them config `return-url`, `ipn-url`, `default-ip-address`.
  - Ghi FE chi hien VNPAY khi metadata tra `enabled=true`.

### Review truoc commit

- Da xac nhan provider khong tao checkout neu `enabled=false` hoac thieu config checkout.
- Da xac nhan `vnp_Amount` nhan 100 theo format VNPAY.
- Da xac nhan `vnp_CreateDate`/`vnp_ExpireDate` dung timezone `Asia/Ho_Chi_Minh`.
- Da xac nhan URL co `vnp_SecureHash` HMAC-SHA512 va test verify lai chu ky.
- Da chay `./mvnw.cmd '-Dtest=VnPayPaymentProviderTests,PaymentProviderPropertiesTests,PaymentMethodServiceTests,PaymentCheckoutServiceTests' test`: pass 13 tests.
- Da chay `./mvnw.cmd test`: pass 241 tests.
- Da chay `git diff --check`: khong co whitespace error.
- Da review diff thu cong: them guard separator `?`/`&` cho `checkoutBaseUrl` co san query string.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `src/main/java/com/example/goride/payment/provider/VnPayPaymentProvider.java`
- `src/main/java/com/example/goride/payment/config/PaymentProviderProperties.java`
- `src/test/java/com/example/goride/payment/provider/VnPayPaymentProviderTests.java`
- `src/test/java/com/example/goride/payment/config/PaymentProviderPropertiesTests.java`
- `src/test/java/com/example/goride/payment/service/PaymentMethodServiceTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them VNPAY return/IPN handling va verify `vnp_SecureHash` callback.
- Implement MoMo checkout provider.
- Cap nhat `docs/pland.xlsx` theo trang thai partial/open cua payment providers.

---

## Commit: `feat: expose payment method metadata`

Branch: `feature/payment-method-metadata`

Phase: Phase 7 - Payment provider completion, expose payment method availability cho FE

### Muc tieu

Cho FE lay danh sach payment method tu backend thay vi hardcode `CASH`/MoMo/VNPay, dong thoi chan booking neu client gui payment method chua duoc backend enable. Commit nay chua implement provider MoMo/VNPay that; hai method online chi duoc expose o metadata voi `enabled=false` cho den khi co provider bean va config day du.

### Noi dung da trien khai

- Mo rong `PaymentMethod`:
  - Them `MOMO` va `VNPAY`.
  - Them metadata noi bo: `providerName`, `displayName`, `checkoutRequired`.
- Them `PaymentMethodResponse` de FE doc method/provider/display/status:
  - `method`, `provider`, `displayName`.
  - `enabled`, `checkoutRequired`, `sandbox`.
  - `providerConfigured`, `providerRegistered`.
- Them `PaymentMethodService`:
  - `CASH` enabled khi co provider runtime registered.
  - `MOMO`/`VNPAY` chi enabled khi provider da registered va config `enabled=true` + du checkout config.
  - Mac dinh MoMo/VNPay van disabled, dung voi plan P0 payment method exposure.
- Them endpoint public:
  - `GET /api/v1/payments/methods`.
  - Cap nhat `SecurityConfig` permit endpoint nay de FE co the goi truoc flow booking.
- Cap nhat `BookingService`:
  - Check `paymentMethodService.isPaymentMethodEnabled(...)` truoc khi tinh fare/tao trip.
  - Tra `PAYMENT_PROVIDER_UNSUPPORTED` neu client gui method chua enabled.
- Cap nhat docs:
  - `integrate-plan.md` them enum `CASH | MOMO | VNPAY`, endpoint payment methods va FE action.
  - `docs/pland.xlsx` them status tracking ro hon, danh dau task Payment methods la `Done`.

### Review truoc commit

- Da xac nhan MoMo/VNPay duoc expose cho FE nhung khong the tao booking khi chua co provider implementation that.
- Da xac nhan provider online chi enabled khi co ca runtime provider bean va config checkout day du.
- Da xac nhan endpoint payment methods la public, khong expose secret provider.
- Da chay `./mvnw.cmd '-Dtest=PaymentMethodServiceTests,BookingServiceTests,PaymentProviderRegistryTests' test`: pass 18 tests.
- Da chay `./mvnw.cmd test`: pass 237 tests.
- Da chay `git diff --check`: khong co whitespace error.
- Da review diff thu cong: khong thay blocker/regression trong scope payment metadata.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `src/main/java/com/example/goride/booking/domain/PaymentMethod.java`
- `src/main/java/com/example/goride/payment/service/PaymentMethodService.java`
- `src/main/java/com/example/goride/payment/dto/PaymentMethodResponse.java`
- `src/main/java/com/example/goride/payment/controller/PaymentController.java`
- `src/main/java/com/example/goride/booking/service/BookingService.java`
- `src/test/java/com/example/goride/payment/service/PaymentMethodServiceTests.java`
- `src/test/java/com/example/goride/booking/service/BookingServiceTests.java`
- `integrate-plan.md`
- `docs/pland.xlsx`

### Viec tiep theo

- Implement MoMo/VNPay provider sandbox that.
- Them signature verifier cho webhook provider.
- Cap nhat metadata endpoint khi online provider co redirect/deeplink that.

---

## Commit: `feat: add payment provider config foundation`

Branch: `feature/payment-provider-config`

Phase: Phase 6 - Payment module extensibility, chuan bi config runtime cho MoMo/VNPay provider

### Muc tieu

Them nen tang cau hinh runtime cho payment provider online truoc khi bat provider MoMo/VNPay that. Commit nay khong doi enum `PaymentMethod`, khong expose payment method moi cho FE va khong chinh `application.yml` local; MoMo/VNPay mac dinh disabled.

### Noi dung da trien khai

- Them `PaymentConfig` de enable configuration properties cho payment module.
- Them `PaymentProviderProperties` voi prefix `app.payments.providers`:
  - `momo.enabled`, `momo.sandbox`, `momo.merchant-id`, `momo.secret-key`, `momo.checkout-base-url`, `momo.webhook-secret`.
  - `vnpay.enabled`, `vnpay.sandbox`, `vnpay.merchant-id`, `vnpay.secret-key`, `vnpay.checkout-base-url`, `vnpay.webhook-secret`.
  - Mac dinh provider online disabled va sandbox mode enabled.
  - Helper `settingsFor(providerName)` resolve `momo`/`vnpay` case-insensitive.
  - Helper normalize secret/merchant/base-url/webhook-secret.
  - Helper check provider co du checkout/webhook config hay chua.
- Them `PaymentProviderPropertiesTests` cho default, normalize va provider lookup.
- Cap nhat `integrate-plan.md`:
  - Ghi provider config foundation da co.
  - Them YAML mau dung env variables cho MoMo/VNPay.
  - Ghi ro FE van chi gui `CASH` cho den khi provider online duoc enable o commit sau.

### Review truoc commit

- Da xac nhan commit nay khong them `MOMO`/`VNPAY` vao enum FE.
- Da xac nhan app context load duoc khi khong co config provider trong `application.yml`.
- Da xac nhan MoMo/VNPay disabled by default va sandbox=true by default.
- Da chay `./mvnw.cmd "-Dtest=PaymentProviderPropertiesTests,GorideApplicationTests" test`: pass 5 tests.
- Da chay `./mvnw.cmd test`: pass 234 tests.
- Da chay `git diff --check`: khong co whitespace error.
- Da quet marker debug/disabled test: khong co ket qua.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `src/main/java/com/example/goride/payment/config/PaymentConfig.java`
- `src/main/java/com/example/goride/payment/config/PaymentProviderProperties.java`
- `src/test/java/com/example/goride/payment/config/PaymentProviderPropertiesTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them MoMo provider skeleton dung config nay de tao checkout session khi payment method online duoc mo.
- Them signature verifier/helper cho webhook provider.

---

## Commit: `feat: extract payment completion workflow`

Branch: `feature/payment-completion-workflow`

Phase: Phase 6 - Payment module extensibility, chuan bi dung chung payment completed side effects cho CASH va provider online sau nay

### Muc tieu

Tach cac side effect sau khi payment completed thanh workflow rieng de cash confirmation va MoMo/VNPay webhook sau nay co the dung chung. Commit nay khong doi REST/WebSocket contract voi FE; behavior hien tai van la: khi driver confirm cash thanh cong, payment completed se notify passenger/driver va dua driver ve `AVAILABLE`.

### Noi dung da trien khai

- Them `PaymentCompletionWorkflow`:
  - Chi chap nhan payment status `COMPLETED`.
  - Kiem tra payment thuoc trip da co assigned driver.
  - Tao `PAYMENT_COMPLETED` notification dung payload hien co.
  - Sau transaction commit, mark driver `AVAILABLE` trong Redis matching store.
  - Sau transaction commit, notify passenger va driver qua realtime notification pipeline.
- Refactor `CashPaymentConfirmationService`:
  - Giu validation driver ownership, trip completed, method `CASH`, payment `PENDING`.
  - Sau khi `payment.markCompleted()` va save, goi `PaymentCompletionWorkflow.handleCompletedPayment(...)`.
  - Loai bo logic notify/driver availability duplicate khoi service cash.
- Them `PaymentCompletionWorkflowTests` de bao phu notification va driver availability side effects.
- Cap nhat `CashPaymentConfirmationServiceTests` de assert cash service goi workflow.
- Cap nhat `integrate-plan.md` ghi ro workflow dung chung cho cash va provider callback sau nay.

### Review truoc commit

- Da xac nhan response/endpoint cash confirm khong doi.
- Da xac nhan driver chi duoc dua ve `AVAILABLE` sau transaction commit nhu behavior cu.
- Da xac nhan notification `PAYMENT_COMPLETED` giu payload cu cho passenger va driver.
- Da xac nhan workflow reject payment chua completed bang `PAYMENT_INVALID_STATUS`.
- Da chay `./mvnw.cmd "-Dtest=CashPaymentConfirmationServiceTests,PaymentCompletionWorkflowTests,PaymentWebhookServiceTests" test`: pass 10 tests.
- Da chay `./mvnw.cmd test`: pass 230 tests.
- Da chay `git diff --check`: khong co whitespace error.
- Da quet marker debug/disabled test: khong co ket qua.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `src/main/java/com/example/goride/payment/service/PaymentCompletionWorkflow.java`
- `src/main/java/com/example/goride/payment/service/CashPaymentConfirmationService.java`
- `src/test/java/com/example/goride/payment/service/PaymentCompletionWorkflowTests.java`
- `src/test/java/com/example/goride/payment/service/CashPaymentConfirmationServiceTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Khi them MoMo/VNPay provider, provider webhook sau khi mark payment completed se goi workflow nay.
- Them provider config/client sandbox cho payment online.

---

## Commit: `feat: add payment webhook foundation`

Branch: `feature/payment-webhook-foundation`

Phase: Phase 6 - Payment module extensibility, theo `docs/TDD.md` muc 6.2 webhook callback cho MoMo/VNPay tuong lai

### Muc tieu

Them nen tang webhook/callback cho external payment provider de MoMo/VNPay sau nay co the goi ve backend. Commit nay chua bat provider online that, chua verify signature MoMo/VNPay that va van giu enum FE `PaymentMethod = CASH`; muc tieu la tao contract/service extension point nho, an toan de provider sau chi implement logic rieng.

### Noi dung da trien khai

- Mo rong `Payment` domain:
  - Them `markCompletedByProvider(provider, transactionRef)`.
  - Them `markFailedByProvider(provider, transactionRef)`.
  - Luu `provider` va `transactionRef` sau khi callback provider duoc xu ly.
  - Cho phep callback lap lai idempotent neu trung provider/reference.
  - Chan callback mau thuan neu payment da completed/failed voi reference khac.
- Mo rong `PaymentProvider`:
  - Them `providerName()` mac dinh theo payment method.
  - Them hook `handleWebhook(request)` de provider sau nay parse payload, verify signature va cap nhat payment.
  - Default provider tra `PAYMENT_PROVIDER_UNSUPPORTED` neu chua support webhook.
- Mo rong `PaymentProviderRegistry`:
  - Tra provider theo `PaymentMethod`.
  - Tra provider theo path `providerName` case-insensitive cho webhook.
  - Tra `PAYMENT_PROVIDER_UNSUPPORTED` neu provider name chua duoc enable.
- Them webhook model/response:
  - `PaymentWebhookRequest` gom provider name, headers, raw payload va received timestamp.
  - `PaymentWebhookResult` gom accepted/payment/status/transactionRef/message.
  - `PaymentWebhookResponse` map ket qua tra ve provider.
- Them `PaymentWebhookService` de dispatch callback toi provider tuong ung.
- Mo rong `PaymentController`:
  - Them public endpoint `POST /api/v1/payments/providers/{providerName}/webhook`.
- Cap nhat `SecurityConfig` permit public webhook path; provider implementation sau phai verify signature rieng trong `handleWebhook`.
- Them unit test cho domain callback, provider registry lookup by name, webhook service dispatch va unsupported provider.
- Cap nhat `integrate-plan.md` voi endpoint webhook, public route va error code moi.

### Review truoc commit

- Da xac nhan FE enum `PaymentMethod` van chi la `CASH`.
- Da xac nhan FE app khong goi webhook truc tiep; endpoint nay danh cho provider external callback.
- Da xac nhan `CashPaymentProvider` chua support webhook va tra `PAYMENT_PROVIDER_UNSUPPORTED`.
- Da xac nhan callback provider completed/failed ghi provider/reference va lap lai cung reference thi idempotent.
- Da chay `./mvnw.cmd "-Dtest=PaymentTests,PaymentProviderRegistryTests,PaymentWebhookServiceTests,PaymentCheckoutServiceTests" test`: pass 20 tests.
- Da chay `./mvnw.cmd test`: pass 228 tests.
- Da chay `git diff --check`: khong co whitespace error.
- Da quet marker debug/disabled test: khong co ket qua.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `src/main/java/com/example/goride/auth/config/SecurityConfig.java`
- `src/main/java/com/example/goride/common/error/ErrorCode.java`
- `src/main/java/com/example/goride/payment/controller/PaymentController.java`
- `src/main/java/com/example/goride/payment/domain/Payment.java`
- `src/main/java/com/example/goride/payment/dto/PaymentWebhookResponse.java`
- `src/main/java/com/example/goride/payment/provider/PaymentProvider.java`
- `src/main/java/com/example/goride/payment/provider/PaymentProviderRegistry.java`
- `src/main/java/com/example/goride/payment/provider/PaymentWebhookRequest.java`
- `src/main/java/com/example/goride/payment/provider/PaymentWebhookResult.java`
- `src/main/java/com/example/goride/payment/service/PaymentWebhookService.java`
- `src/test/java/com/example/goride/payment/domain/PaymentTests.java`
- `src/test/java/com/example/goride/payment/provider/PaymentProviderRegistryTests.java`
- `src/test/java/com/example/goride/payment/service/PaymentWebhookServiceTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them provider MoMo hoac VNPay that su: tao checkout URL, verify signature, parse callback payload.
- Can thiet ke cau hinh secret/key theo provider va sandbox/production mode.
- Sau khi co provider online, bo sung notification/payment status polling docs cho FE.

---

## Commit: `feat: add payment checkout foundation`

Branch: `feature/payment-checkout-foundation`

Phase: Phase 6 - Payment module extensibility, theo `docs/TDD.md` muc 6.2 MoMo/VNPay future extension

### Muc tieu

Them contract checkout cho payment `PENDING` de FE co the hoi backend xem payment method hien tai co can redirect/provider checkout hay khong. Commit nay giu runtime payment method chi la `CASH`; CASH khong can checkout URL va van di theo flow driver confirm tien mat hien co.

### Noi dung da trien khai

- Them `PaymentCheckoutSession`:
  - Mo ta ket qua checkout cua provider: `checkoutRequired`, `checkoutUrl`, `expiresAt`.
  - Validate checkout URL bat buoc khi provider bao can checkout.
  - Mac dinh `notRequired()` cho CASH/no redirect.
- Mo rong `PaymentProvider`:
  - Them hook `createCheckoutSession(payment)`.
  - Default provider tra `PaymentCheckoutSession.notRequired()` de CASH khong doi behavior.
- Them `PaymentProviderRegistry`:
  - Gom provider theo `PaymentMethod`.
  - Chan duplicate provider cung method.
  - Tra `PAYMENT_INVALID_STATUS` neu method chua co provider runtime.
- Tach `PaymentAccessService`:
  - Dung chung logic load payment theo trip va check quyen passenger/assigned driver/admin.
  - `PaymentQueryService` va checkout flow cung dung access rule nay.
- Them `PaymentCheckoutService`:
  - Chi cho checkout voi payment `PENDING`.
  - Lay provider theo `payment.method` va map checkout session ra response.
- Mo rong `PaymentController`:
  - Them `GET /api/v1/payments/trips/{tripId}/checkout`.
- Them `PaymentCheckoutResponse` gom payment core fields va checkout fields.
- Cap nhat test cho registry, trip payment provider lookup, payment query access va checkout service.
- Cap nhat `integrate-plan.md` voi checklist va huong dan FE goi checkout endpoint.

### Review truoc commit

- Da xac nhan enum `PaymentMethod` van chi expose `CASH`.
- Da xac nhan CASH checkout tra `checkoutRequired=false`, `checkoutUrl=null`, `expiresAt=null`.
- Da xac nhan payment da `COMPLETED` bi chan bang `PAYMENT_INVALID_STATUS`.
- Da xac nhan duplicate provider van bi chan trong registry.
- Da chay `./mvnw.cmd "-Dtest=PaymentProviderRegistryTests,TripPaymentServiceTests,PaymentQueryServiceTests,PaymentCheckoutServiceTests" test`: pass 16 tests.
- Da chay `./mvnw.cmd test`: pass 218 tests.
- Da chay `git diff --check`: khong co whitespace error.
- Da quet marker debug/disabled test: khong co ket qua.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `src/main/java/com/example/goride/payment/controller/PaymentController.java`
- `src/main/java/com/example/goride/payment/dto/PaymentCheckoutResponse.java`
- `src/main/java/com/example/goride/payment/provider/PaymentCheckoutSession.java`
- `src/main/java/com/example/goride/payment/provider/PaymentProvider.java`
- `src/main/java/com/example/goride/payment/provider/PaymentProviderRegistry.java`
- `src/main/java/com/example/goride/payment/service/PaymentAccessService.java`
- `src/main/java/com/example/goride/payment/service/PaymentCheckoutService.java`
- `src/main/java/com/example/goride/payment/service/PaymentQueryService.java`
- `src/main/java/com/example/goride/payment/service/TripPaymentService.java`
- `src/test/java/com/example/goride/payment/provider/PaymentProviderRegistryTests.java`
- `src/test/java/com/example/goride/payment/service/PaymentCheckoutServiceTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them payment method/provider MoMo hoac VNPay.
- Thiet ke callback/webhook va mapping `provider`/`transactionRef`.
- Can nhac tach endpoint tao provider checkout thanh `POST` khi provider online co side effect tao order that.

---

## Commit: `feat: remove invalid fcm tokens`

Branch: `feature/fcm-invalid-token-cleanup`

Phase: Phase 6 - Notification module, theo `docs/TDD.md` muc 5.6 FCM error handling

### Muc tieu

Tu dong xoa FCM token trong Redis khi Firebase Admin SDK bao token da het hieu luc/khong con dang ky. Muc tieu la tranh retry push vo ich cho token chet, dong thoi giu WebSocket/inbox pipeline khong bi fail khi mobile push loi.

### Noi dung da trien khai

- Mo rong `FcmPushSendException`:
  - Them flag `invalidToken`.
  - Them factory `invalidToken(message, cause)`.
- Cap nhat `FirebaseAdminMessagingGateway`:
  - Map `MessagingErrorCode.UNREGISTERED` thanh `FcmPushSendException.invalidToken(...)`.
  - Cac loi Firebase khac van la loi push thong thuong de retry/suppress theo channel.
- Cap nhat `FcmUserNotificationChannel`:
  - Neu sender bao `invalidToken`, xoa token qua `FcmDeviceTokenStore.deleteToken(userId)`.
  - Khong retry token invalid.
  - Van retry loi transient theo `maxAttempts` va khong xoa token voi loi transient.
- Them unit test cho invalid token exception va token cleanup trong channel.
- Cap nhat `integrate-plan.md` de FE biet backend tu cleanup token invalid khi Firebase tra `UNREGISTERED`.

### Review truoc commit

- Da xac nhan backend chi xoa token voi `UNREGISTERED`, khong xoa voi loi transient/payload.
- Da xac nhan cleanup token khong lam fail WebSocket/inbox pipeline.
- Da chay `./mvnw.cmd "-Dtest=FcmPushSendExceptionTests,FcmUserNotificationChannelTests,FirebaseAdminMessagingGatewayTests" test`: pass 11 tests.
- Da chay `./mvnw.cmd test`: pass 212 tests.
- Da chay `git diff --check`: khong co whitespace error.
- Da quet marker debug/disabled test: khong co ket qua.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `src/main/java/com/example/goride/notification/service/FcmPushSendException.java`
- `src/main/java/com/example/goride/notification/service/FirebaseAdminMessagingGateway.java`
- `src/main/java/com/example/goride/notification/service/FcmUserNotificationChannel.java`
- `src/test/java/com/example/goride/notification/service/FcmPushSendExceptionTests.java`
- `src/test/java/com/example/goride/notification/service/FcmUserNotificationChannelTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Bo sung deploy secret/env cho service account path trong moi truong production.
- Can nhac them observability/metrics cho push success/failure.

---

## Commit: `feat: add firebase admin push sender`

Branch: `feature/firebase-admin-push-sender`

Phase: Phase 6 - Notification module, theo `docs/TDD.md` muc 5.6 gui push notification qua Firebase FCM

### Muc tieu

Hoan thien sender Firebase Admin SDK that cho `FcmPushSender`, de FCM channel co the gui mobile push bang service account khi production/local duoc cau hinh. Commit nay van giu local/dev an toan: Firebase Admin SDK chi lazy-init khi backend thuc su gui push va co `service-account-path`.

### Noi dung da trien khai

- Them dependency `com.google.firebase:firebase-admin` version `9.9.0`, exclude `commons-logging` de tranh conflict voi `spring-jcl`.
- Mo rong `FcmPushProperties`:
  - `serviceAccountPath` de tro den Firebase service account JSON.
  - `projectId` tuy chon.
  - `appName` mac dinh `goride`.
- Them `FirebaseMessagingGateway` interface de tach tang Firebase SDK.
- Them `FirebaseAdminMessagingGateway`:
  - Lazy-init `FirebaseApp` theo `appName`.
  - Doc credential tu `app.notifications.fcm.service-account-path`.
  - Set `projectId` neu duoc cau hinh.
  - Wrap loi Firebase/IO bang `FcmPushSendException`.
- Them `FirebaseAdminFcmPushSender`:
  - Implement `FcmPushSender`.
  - Build Firebase `Message` tu `FcmPushMessage`.
  - Set notification title/body va data payload.
- Them unit test cho properties, sender va gateway missing credential.
- Cap nhat `integrate-plan.md` voi cau hinh runtime can thiet cho backend push that.

### Review truoc commit

- Da xac nhan app context van khong can credential khi `app.notifications.fcm.enabled=false`.
- Da xac nhan FCM channel tiep tuc retry/suppress loi de khong lam fail WebSocket/inbox pipeline.
- Da xac nhan FE khong co endpoint moi, van chi dang ky/xoa token nhu truoc.
- Da chay `./mvnw.cmd "-Dtest=FcmPushPropertiesTests,FirebaseAdminFcmPushSenderTests,FirebaseAdminMessagingGatewayTests,FcmUserNotificationChannelTests" test`: pass 11 tests.
- Da chay `./mvnw.cmd test`: pass 208 tests.
- Maven lan dau can tai `firebase-admin` vao `~/.m2`; sandbox bi AccessDenied nen da chay lai voi quyen ngoai sandbox.
- Da chay `git diff --check`: khong co whitespace error.
- Da quet marker debug/disabled test: khong co ket qua.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `pom.xml`
- `src/main/java/com/example/goride/notification/config/FcmPushProperties.java`
- `src/main/java/com/example/goride/notification/service/FirebaseMessagingGateway.java`
- `src/main/java/com/example/goride/notification/service/FirebaseAdminMessagingGateway.java`
- `src/main/java/com/example/goride/notification/service/FirebaseAdminFcmPushSender.java`
- `src/test/java/com/example/goride/notification/config/FcmPushPropertiesTests.java`
- `src/test/java/com/example/goride/notification/service/FirebaseAdminFcmPushSenderTests.java`
- `src/test/java/com/example/goride/notification/service/FirebaseAdminMessagingGatewayTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them co che xoa token trong Redis khi Firebase bao token invalid/unregistered.
- Bo sung deploy secret/env cho service account path trong moi truong production.

---

## Commit: `feat: add fcm push channel foundation`

Branch: `feature/fcm-push-channel`

Phase: Phase 6 - Notification module, theo `docs/TDD.md` muc 5.6 va flow FCM push notification

### Muc tieu

Them nen tang FCM push channel vao notification pipeline da co, de backend co the doc device token tu Redis va gui mobile push khi co Firebase sender implementation. Commit nay khong ep cau hinh Firebase credential ngay: FCM mac dinh disabled/no sender, nen local/dev va app context van chay binh thuong.

### Noi dung da trien khai

- Them `FcmPushProperties` voi prefix `app.notifications.fcm`:
  - `enabled` mac dinh `false`.
  - `maxAttempts` mac dinh `2`.
- Them `NotificationConfig` de enable properties cho notification module.
- Them `FcmPushMessage`:
  - Chuan hoa token/title/body/data gui sang FCM.
  - Convert `UserNotification.data` thanh `Map<String, String>` theo yeu cau FCM data payload.
- Them `FcmPushSender` interface:
  - La diem cam Firebase Admin SDK implementation o commit sau.
- Them `FcmPushSendException` cho sender implementation sau nay nem loi co nghia.
- Them `FcmUserNotificationChannel`:
  - Channel name `fcm`.
  - Chi hoat dong khi `app.notifications.fcm.enabled=true`.
  - Lay token tu `FcmDeviceTokenStore`/Redis key `fcm_token:{userId}`.
  - Bo qua neu user chua co token.
  - Bo qua neu token trong Redis bi rong/blank.
  - Bo qua va log warning neu chua co `FcmPushSender` bean.
  - Retry theo `maxAttempts`, khong lam fail pipeline WebSocket/inbox neu push loi.
- Them test cho payload builder va FCM channel.
- Cap nhat `integrate-plan.md` de FE biet khong can doi REST/WebSocket contract; push mobile se tu chay khi backend duoc cau hinh sender that.

### Review truoc commit

- Da xac nhan FCM channel khong anh huong behavior WebSocket/inbox hien co khi disabled.
- Da xac nhan FE van chi can dang ky/xoa token qua endpoint hien co.
- Da xac nhan commit nay chua gui Firebase that vi chua co Firebase Admin SDK sender implementation.
- Da chay `./mvnw.cmd "-Dtest=FcmPushMessageTests,FcmUserNotificationChannelTests,WebSocketTripRealtimeNotifierTests" test`: pass 10 tests.
- Da chay `./mvnw.cmd test`: pass 203 tests.
- Da chay `git diff --check`: khong co whitespace error.
- Da quet marker debug/disabled test: khong co ket qua.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `src/main/java/com/example/goride/notification/config/FcmPushProperties.java`
- `src/main/java/com/example/goride/notification/config/NotificationConfig.java`
- `src/main/java/com/example/goride/notification/dto/FcmPushMessage.java`
- `src/main/java/com/example/goride/notification/service/FcmPushSender.java`
- `src/main/java/com/example/goride/notification/service/FcmPushSendException.java`
- `src/main/java/com/example/goride/notification/service/FcmUserNotificationChannel.java`
- `src/test/java/com/example/goride/notification/dto/FcmPushMessageTests.java`
- `src/test/java/com/example/goride/notification/service/FcmUserNotificationChannelTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them Firebase Admin SDK sender implementation doc service account credential.
- Them co che xoa token khi Firebase bao token invalid.

---

## Commit: `feat: add notification channel pipeline`

Branch: `feature/notification-channel-pipeline`

Phase: Phase 6 - Notification module extensibility, theo `docs/TDD.md` muc 6.4 va huong mo rong FCM push

### Muc tieu

Tach notification ca nhan thanh pipeline channel de giu behavior WebSocket/inbox hien tai, dong thoi co diem cam Firebase push channel o commit sau ma khong phai sua cac flow booking/matching/payment dang goi `TripRealtimeNotifier`.

### Noi dung da trien khai

- Them `UserNotificationChannel` interface:
  - Khai bao `channelName()`.
  - Khai bao `send(userId, notification)`.
- Them `InAppNotificationChannel`:
  - Channel name `in_app`.
  - Luu `UserNotification` vao inbox thong qua `NotificationInboxService`.
  - Duoc order truoc de giu behavior cu: luu inbox truoc khi gui realtime.
- Them `WebSocketUserNotificationChannel`:
  - Channel name `websocket`.
  - Gui `UserNotification` toi `/user/queue/notifications`.
- Cap nhat `WebSocketTripRealtimeNotifier`:
  - Inject danh sach `UserNotificationChannel`.
  - `notifyUser(...)` fan-out notification qua cac channel da cau hinh.
  - `broadcastTripStatus(...)` van gui `/topic/trip/{tripId}/status` nhu cu.
- Cap nhat `WebSocketTripRealtimeNotifierTests` va them test rieng cho tung channel.
- Cap nhat `integrate-plan.md` de FE biet contract khong doi, push Firebase that su van la viec tiep theo.

### Review truoc commit

- Da xac nhan notification ca nhan van duoc luu inbox va gui WebSocket nhu truoc thong qua channel rieng.
- Da xac nhan khong co thay doi REST/WebSocket contract cho FE.
- Da xac nhan pipeline du cho commit tiep theo them Firebase channel dua tren token Redis.
- Da chay `./mvnw.cmd "-Dtest=WebSocketTripRealtimeNotifierTests,InAppNotificationChannelTests,WebSocketUserNotificationChannelTests" test`: pass 4 tests.
- Da chay `./mvnw.cmd test`: pass 195 tests.
- Da chay `git diff --check`: khong co whitespace error.
- Da quet marker debug/disabled test: khong co ket qua.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `src/main/java/com/example/goride/notification/service/UserNotificationChannel.java`
- `src/main/java/com/example/goride/notification/service/InAppNotificationChannel.java`
- `src/main/java/com/example/goride/notification/service/WebSocketUserNotificationChannel.java`
- `src/main/java/com/example/goride/notification/service/WebSocketTripRealtimeNotifier.java`
- `src/test/java/com/example/goride/notification/service/InAppNotificationChannelTests.java`
- `src/test/java/com/example/goride/notification/service/WebSocketUserNotificationChannelTests.java`
- `src/test/java/com/example/goride/notification/service/WebSocketTripRealtimeNotifierTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them Firebase sender/channel de gui mobile push dua tren token trong Redis.
- Them config credential Firebase va co che disable push khi chua cau hinh.

---

## Commit: `feat: add payment provider foundation`

Branch: `feature/payment-provider-foundation`

Phase: Phase 6 - Payment module extensibility, theo `docs/TDD.md` muc 2.15 va 6.2

### Muc tieu

Tach nen tang payment provider de sau nay them MoMo/VNPay bang implementation moi, khong phai sua trip completion flow. Commit nay giu behavior CASH hien tai khong doi: khi trip completed, backend tao payment `PENDING` va driver van confirm cash bang endpoint hien co.

### Noi dung da trien khai

- Them `PaymentProvider` interface:
  - Khai bao `paymentMethod()`.
  - Khai bao `createPendingPayment(trip)`.
- Them `CashPaymentProvider`:
  - Ho tro `PaymentMethod.CASH`.
  - Tao `Payment.createPending(trip)` nhu behavior hien co.
- Cap nhat `TripPaymentService`:
  - Inject danh sach provider.
  - Build map theo `PaymentMethod`.
  - Tao pending payment bang provider tuong ung voi `trip.paymentMethod`.
  - Chan duplicate provider cho cung payment method.
  - Tra `PAYMENT_INVALID_STATUS` neu trip dung payment method chua co provider.
- Cap nhat `TripPaymentServiceTests`:
  - Tao payment qua cash provider.
  - Khong tao duplicate neu payment da ton tai.
  - Reject missing provider.
  - Reject duplicate provider.
- Cap nhat `integrate-plan.md` de FE biet enum payment van chi expose `CASH`, nhung backend da co provider foundation.

### Review truoc commit

- Da xac nhan behavior payment CASH khong doi voi FE.
- Da xac nhan `provider` va `transactionRef` cua CASH van giu null nhu truoc.
- Da xac nhan them provider moi sau nay chi can implement `PaymentProvider` va mo enum/flow tuong ung.
- Da chay `./mvnw.cmd "-Dtest=TripPaymentServiceTests" test`: pass 4 tests.
- Da chay `./mvnw.cmd test`: pass 193 tests.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `src/main/java/com/example/goride/payment/provider/PaymentProvider.java`
- `src/main/java/com/example/goride/payment/provider/CashPaymentProvider.java`
- `src/main/java/com/example/goride/payment/service/TripPaymentService.java`
- `src/test/java/com/example/goride/payment/service/TripPaymentServiceTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them MoMo/VNPay provider implementation va webhook callback.
- Them Firebase sender/channel de gui mobile push dua tren token trong Redis.

---

## Commit: `feat: add notification inbox api`

Branch: `feature/notification-inbox-api`

Phase: Phase 6 - Notification module, theo `docs/TDD.md` muc 6.4 va `integrate-plan.md` muc Notification inbox

### Muc tieu

Them in-app notification persistence de FE co man hinh inbox/history cho cac notification ca nhan da gui qua WebSocket. Truoc commit nay user chi nhan realtime qua `/user/queue/notifications`, neu app mat ket noi thi khong co API doc lai notification.

### Noi dung da trien khai

- Them entity `Notification` va repository:
  - Bang `notifications`.
  - Luu `user_id`, `type`, `title`, `body`, `data_json`, `is_read`, `read_at`, `created_at`.
  - Them index `idx_notifications_user_created_at` cho list inbox theo user.
- Them `NotificationInboxService`:
  - Luu `UserNotification` vao DB, serialize `data` thanh JSON.
  - List notification cua user voi pagination 1-based.
  - Mark notification read theo `id + userId`, chan user doc/sua notification cua nguoi khac.
- Mo rong `NotificationController`:
  - `GET /api/v1/notifications?page=1&size=20`.
  - `PATCH /api/v1/notifications/{notificationId}/read`.
- Cap nhat `WebSocketTripRealtimeNotifier`:
  - Moi `notifyUser(...)` vua luu inbox vua gui WebSocket `/user/queue/notifications`.
- Them `NotificationResponse`.
- Them `NOTIFICATION_NOT_FOUND`.
- Cap nhat `GorideApplicationTests` voi mock `NotificationRepository`.
- Cap nhat `integrate-plan.md` voi huong dan FE cho notification inbox.

### Review truoc commit

- Da xac nhan endpoint yeu cau authenticated user.
- Da xac nhan list/mark-read luon scope theo current `userId`.
- Da xac nhan response khong expose notification cua user khac.
- Da xac nhan WebSocket notification hien co van duoc gui sau khi luu inbox.
- Da chay `./mvnw.cmd "-Dtest=NotificationInboxServiceTests,WebSocketTripRealtimeNotifierTests" test`: pass 8 tests.
- Da chay `./mvnw.cmd test`: pass 191 tests.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `src/main/java/com/example/goride/notification/domain/Notification.java`
- `src/main/java/com/example/goride/notification/repository/NotificationRepository.java`
- `src/main/java/com/example/goride/notification/service/NotificationInboxService.java`
- `src/main/java/com/example/goride/notification/service/WebSocketTripRealtimeNotifier.java`
- `src/main/java/com/example/goride/notification/controller/NotificationController.java`
- `src/main/java/com/example/goride/notification/dto/NotificationResponse.java`
- `src/test/java/com/example/goride/notification/service/NotificationInboxServiceTests.java`
- `src/test/java/com/example/goride/notification/service/WebSocketTripRealtimeNotifierTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them Firebase sender/channel de gui mobile push dua tren token trong Redis.
- Them payment provider abstraction neu bat dau MoMo/VNPay.

---

## Commit: `feat: add fcm device token api`

Branch: `feature/fcm-device-token-api`

Phase: Phase 6 - Notification module foundation, theo `docs/TDD.md` muc 5.6 va `integrate-plan.md` muc Notification

### Muc tieu

Them nen tang de mobile app dang ky FCM device token len backend sau login. Commit nay chi quan ly token trong Redis theo thiet ke `fcm_token:{userId}`; viec gui push qua Firebase se tach commit sau de giu scope review nho.

### Noi dung da trien khai

- Them `NotificationController`:
  - `PUT /api/v1/notifications/fcm-token`
  - `DELETE /api/v1/notifications/fcm-token`
  - Yeu cau user da authenticate.
- Them DTO:
  - `FcmTokenUpdateRequest` voi `token` bat buoc, toi da 4096 ky tu.
  - `FcmTokenResponse` gom `userId` va `registered`.
- Them `FcmDeviceTokenService`:
  - Validate user id.
  - Trim token truoc khi luu.
  - Chan token rong hoac qua dai bang `VALIDATION_ERROR`.
- Them `FcmDeviceTokenStore` va `RedisFcmDeviceTokenStore`:
  - Luu token vao Redis key `fcm_token:{userId}`.
  - Xoa token khi logout/token invalid tren client.
  - Doc token qua `findToken(...)` de dung cho push sender o commit sau.
- Them tests cho service validation va Redis key contract.
- Cap nhat `integrate-plan.md` voi huong dan FE goi API FCM token.

### Review truoc commit

- Da xac nhan endpoint khong public, yeu cau JWT authenticated user.
- Da xac nhan backend khong expose lai raw token trong response.
- Da xac nhan Redis key khop TDD: `fcm_token:{userId}`.
- Da xac nhan commit chua them Firebase SDK/webhook gui push de tranh tron scope voi token registry.
- Da chay `./mvnw.cmd "-Dtest=FcmDeviceTokenServiceTests,RedisFcmDeviceTokenStoreTests" test`: pass 9 tests.
- Da chay `./mvnw.cmd test`: pass 185 tests.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `src/main/java/com/example/goride/notification/controller/NotificationController.java`
- `src/main/java/com/example/goride/notification/dto/FcmTokenUpdateRequest.java`
- `src/main/java/com/example/goride/notification/dto/FcmTokenResponse.java`
- `src/main/java/com/example/goride/notification/service/FcmDeviceTokenService.java`
- `src/main/java/com/example/goride/notification/service/FcmDeviceTokenStore.java`
- `src/main/java/com/example/goride/notification/service/RedisFcmDeviceTokenStore.java`
- `src/test/java/com/example/goride/notification/service/FcmDeviceTokenServiceTests.java`
- `src/test/java/com/example/goride/notification/service/RedisFcmDeviceTokenStoreTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them Firebase sender/channel de gui mobile push dua tren token trong Redis.
- Ket noi cac notification hien co sang pipeline push + WebSocket.
- Them notification inbox persistence neu FE can lich su thong bao.

---

## Commit: `feat: sync driver rating to redis`

Branch: `feature/driver-rating-redis-sync`

Phase: Phase 6 - Rating + matching metadata consistency, theo `integrate-plan.md` muc Rating

### Muc tieu

Dong bo rating moi cua driver vao Redis availability metadata sau khi passenger tao rating thanh cong. Truoc commit nay PostgreSQL da co `average_rating` moi, nhung matching co the tiep tuc doc rating cu trong `driver:{id}:meta.rating` cho den khi driver online lai.

### Noi dung da trien khai

- Mo rong `DriverAvailabilityStore` voi `updateRating(driverId, rating)`.
- Cap nhat `RedisDriverAvailabilityStore`:
  - Chi update Redis khi ca `driver:{id}:status` va `driver:{id}:meta` con ton tai, de tranh tao metadata thieu cho driver offline.
  - Ghi field `rating` bang `BigDecimal.toPlainString()`.
  - Refresh TTL cua `driver:{id}:meta` ve 60 giay, dong bo voi TTL availability hien co.
- Cap nhat `RatingService`:
  - Inject `DriverAvailabilityStore`.
  - Sau khi cap nhat `DriverProfile.averageRating`, dang ky sync Redis bang callback sau transaction commit.
  - Khi khong co transaction synchronization trong unit test, callback chay ngay theo pattern cac service hien co.
- Them `RedisDriverAvailabilityStoreTests` cho case update metadata, thieu status va thieu metadata.
- Cap nhat `RatingServiceTests` de verify rating moi duoc sync va cac flow loi khong cham Redis.
- Cap nhat `integrate-plan.md` danh dau Redis rating sync da hoan thien va huong dan FE khong can goi them endpoint.

### Review truoc commit

- Da xac nhan Redis sync chi chay sau DB transaction commit, tranh cache rating moi khi DB rollback.
- Da xac nhan driver offline/thieu metadata khong bi tao hash metadata khong day du.
- Da xac nhan matching van doc cung field `driver:{id}:meta.rating`, nen khong thay doi contract Redis hien co.
- Da chay `./mvnw.cmd "-Dtest=RatingServiceTests,RedisDriverAvailabilityStoreTests" test`: pass 16 tests.
- Da chay `./mvnw.cmd test`: pass 176 tests.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `src/main/java/com/example/goride/driver/service/availability/DriverAvailabilityStore.java`
- `src/main/java/com/example/goride/driver/service/availability/RedisDriverAvailabilityStore.java`
- `src/main/java/com/example/goride/rating/service/RatingService.java`
- `src/test/java/com/example/goride/driver/service/availability/RedisDriverAvailabilityStoreTests.java`
- `src/test/java/com/example/goride/rating/service/RatingServiceTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them payment provider abstraction neu bat dau MoMo/VNPay.
- Them FCM device token va push notification pipeline neu can mobile production notification.
- Them notification inbox persistence neu FE can lich su thong bao.

---

## Commit: `feat: add trip rating status api`

Branch: `feature/trip-rating-status-api`

Phase: Phase 6 - Rating module, theo `integrate-plan.md` muc Rating

### Muc tieu

Bo sung API de passenger kiem tra trip da rating hay chua truoc khi hien form danh gia. Truoc commit nay FE phai submit rating roi suy luan tu loi `TRIP_ALREADY_RATED`.

### Noi dung da trien khai

- Them endpoint:
  - `GET /api/v1/ratings/trips/{tripId}/me`
  - Yeu cau role `PASSENGER`.
- Them `TripRatingStatusResponse`:
  - `tripId`
  - `rated`
  - `rating` neu da co rating, `null` neu chua rating.
- Mo rong `RatingService`:
  - Load trip active theo `tripId`.
  - Chi passenger cua trip moi xem rating status.
  - Tra `rated=false` neu chua co rating.
  - Tra `rated=true` kem `RatingResponse` neu trip da co rating.
- Mo rong `RatingRepository` voi `findByTripId(...)`.
- Cap nhat `RatingServiceTests` cho rated/not-rated, unrelated passenger va missing trip.
- Cap nhat `integrate-plan.md` voi huong dan FE goi rating status endpoint.

### Review truoc commit

- Da xac nhan endpoint chi expose cho role `PASSENGER`.
- Da xac nhan service van check passenger owner cua trip.
- Da xac nhan response khong bat FE suy luan bang loi duplicate submit nua.
- Da chay `./mvnw.cmd -Dtest=RatingServiceTests test`: pass 13 tests.
- Da chay `./mvnw.cmd test`: pass 173 tests.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `src/main/java/com/example/goride/rating/controller/RatingController.java`
- `src/main/java/com/example/goride/rating/service/RatingService.java`
- `src/main/java/com/example/goride/rating/dto/TripRatingStatusResponse.java`
- `src/main/java/com/example/goride/rating/repository/RatingRepository.java`
- `src/test/java/com/example/goride/rating/service/RatingServiceTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Dong bo Redis `driver:{id}:meta.rating` khi driver dang online sau rating.
- Them payment provider abstraction neu bat dau MoMo/VNPay.
- Neu can mobile production notification, them FCM device token va push pipeline.

---

## Commit: `feat: add payment detail api`

Branch: `feature/payment-detail-api`

Phase: Phase 6 - Payment module, theo `integrate-plan.md` muc Payment cash

### Muc tieu

Bo sung API de passenger/driver/admin xem payment cua mot trip, phuc vu man hinh hoa don/payment state sau khi trip completed.

### Noi dung da trien khai

- Them `PaymentController`:
  - `GET /api/v1/payments/trips/{tripId}`
  - Yeu cau role `PASSENGER`, `DRIVER`, hoac `ADMIN`.
- Them `PaymentQueryService`:
  - Load current user active.
  - Load payment theo `tripId` kem trip passenger/driver.
  - Cho phep passenger cua trip, assigned driver, hoac admin xem payment.
  - Tra `PAYMENT_NOT_FOUND` neu payment chua ton tai.
  - Tra `FORBIDDEN` neu user khong thuoc trip.
- Them `PaymentDetailResponse` gom payment id, trip id, amount, method, status, provider, transactionRef, paidAt, createdAt, updatedAt.
- Mo rong `PaymentRepository` voi `findByTripIdWithTrip(...)`.
- Them `PaymentQueryServiceTests` cho passenger, driver, admin, missing payment, missing user va unauthorized user.
- Cap nhat `integrate-plan.md` voi huong dan FE goi payment detail endpoint.

### Review truoc commit

- Da xac nhan endpoint co method security `hasAnyRole('PASSENGER', 'DRIVER', 'ADMIN')`.
- Da xac nhan service van check owner/admin theo trip, khong chi dua vao role.
- Da xac nhan user khong ton tai bi chan truoc khi query payment.
- Da chay `./mvnw.cmd -Dtest=PaymentQueryServiceTests test`: pass 6 tests.
- Da chay `./mvnw.cmd test`: pass 169 tests.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `src/main/java/com/example/goride/payment/controller/PaymentController.java`
- `src/main/java/com/example/goride/payment/service/PaymentQueryService.java`
- `src/main/java/com/example/goride/payment/dto/PaymentDetailResponse.java`
- `src/main/java/com/example/goride/payment/repository/PaymentRepository.java`
- `src/test/java/com/example/goride/payment/service/PaymentQueryServiceTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them API kiem tra trip da rating hay chua neu FE can disable form truoc submit.
- Dong bo Redis `driver:{id}:meta.rating` khi driver dang online sau rating.
- Them payment provider abstraction neu bat dau MoMo/VNPay.

---

## Commit: `feat: add admin dashboard api`

Branch: `feature/admin-dashboard-api`

Phase: Phase 7 - Admin module va statistics foundation, theo `integrate-plan.md` muc Admin module

### Muc tieu

Bo sung API tong hop dashboard cho admin de FE co so lieu cards/chart co ban: user, driver approval, trip theo status, payment completed revenue va average driver rating.

### Noi dung da trien khai

- Them `AdminDashboardController`:
  - `GET /api/v1/admin/dashboard`
  - Yeu cau role `ADMIN`.
- Them `AdminDashboardService`:
  - Tong hop user active/suspended/total.
  - Tong hop driver total va approval status `PENDING`, `APPROVED`, `REJECTED`.
  - Tong hop total trips va `tripsByStatus`, luon fill du moi `TripStatus` voi default `0`.
  - Tong hop payment `COMPLETED`: count va revenue.
  - Tinh average driver rating, lam tron 1 chu so thap phan.
- Them `AdminDashboardResponse`.
- Mo rong repositories voi aggregate queries/counts:
  - `UserRepository.countByDeletedAtIsNull()`.
  - `DriverProfileRepository` count theo approval va average rating.
  - `TripRepository.countTripsByStatus()`.
  - `PaymentRepository.countByStatus(...)` va `sumAmountByStatus(...)`.
- Them `AdminDashboardServiceTests` cho aggregate mapping va default trip status count.
- Cap nhat `integrate-plan.md` voi endpoint FE `GET /api/v1/admin/dashboard`.

### Review truoc commit

- Da xac nhan endpoint admin duoc bao ve bang `hasRole('ADMIN')`.
- Da xac nhan revenue chi tinh payment `COMPLETED`.
- Da xac nhan `tripsByStatus` fill du tat ca `TripStatus` de FE khong can tu bo sung key thieu.
- Da chay `./mvnw.cmd -Dtest=AdminDashboardServiceTests test`: pass 1 test.
- Da chay `./mvnw.cmd test`: pass 163 tests.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `src/main/java/com/example/goride/admin/controller/AdminDashboardController.java`
- `src/main/java/com/example/goride/admin/service/AdminDashboardService.java`
- `src/main/java/com/example/goride/admin/dto/AdminDashboardResponse.java`
- `src/main/java/com/example/goride/booking/repository/TripRepository.java`
- `src/main/java/com/example/goride/payment/repository/PaymentRepository.java`
- `src/main/java/com/example/goride/driver/repository/DriverProfileRepository.java`
- `src/main/java/com/example/goride/user/repository/UserRepository.java`
- `src/test/java/com/example/goride/admin/service/AdminDashboardServiceTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them payment detail/history API neu FE can man hinh hoa don.
- Dong bo Redis `driver:{id}:meta.rating` khi driver dang online sau rating.
- Neu admin can chart theo ngay, them endpoint statistics time-series rieng.

---

## Commit: `feat: update driver total trips on completion`

Branch: `feature/driver-total-trips-update`

Phase: Phase 6 - Payment/statistics foundation, theo `integrate-plan.md` muc Payment/rating/statistics

### Muc tieu

Cap nhat thong ke so chuyen da hoan thanh cua driver ngay khi trip chuyen sang `COMPLETED`. Truoc commit nay `DriverProfile.recordCompletedTrip()` da co trong domain nhung service chua goi, lam `DriverProfileResponse.totalTrips` khong tang theo lifecycle trip.

### Noi dung da trien khai

- Cap nhat `DriverTripStatusService`:
  - Inject `DriverProfileRepository`.
  - Khi driver complete trip hop le, lock driver profile bang `findByUserIdForUpdate(...)`.
  - Goi `DriverProfile.recordCompletedTrip()` trong cung transaction voi trip update, history va pending payment.
  - Neu assigned driver khong co profile, tra `DRIVER_PROFILE_NOT_FOUND` va rollback flow complete.
- Cap nhat `DriverTripStatusServiceTests`:
  - Kiem tra `totalTrips` tang khi `IN_PROGRESS -> COMPLETED`.
  - Kiem tra cac transition `ARRIVED` va `IN_PROGRESS` khong cham vao driver profile.
  - Kiem tra missing driver profile bi chan truoc khi save trip/history/payment.
- Cap nhat `integrate-plan.md`:
  - Danh dau `driver_profiles.total_trips` da duoc cap nhat khi trip completed.
  - Ghi chu FE co the doc lai driver profile de thay `totalTrips` moi.

### Review truoc commit

- Da xac nhan increment chi nam trong nhÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡nh `COMPLETED`, sau khi domain transition hop le.
- Da xac nhan trip lock va profile lock cung nam trong transaction de tranh double-count theo concurrent complete request.
- Da xac nhan transition khong phai `COMPLETED` khong query driver profile.
- Da chay `./mvnw.cmd -Dtest=DriverTripStatusServiceTests test`: pass 8 tests.
- Da chay `./mvnw.cmd test`: pass 162 tests.
- CodeRabbit CLI chua chay duoc trong moi truong nay vi `coderabbit` command not found.

### Files chinh

- `src/main/java/com/example/goride/driver/service/DriverTripStatusService.java`
- `src/test/java/com/example/goride/driver/service/DriverTripStatusServiceTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them admin stats/dashboard API.
- Them payment detail/history API neu FE can man hinh hoa don.
- Dong bo Redis `driver:{id}:meta.rating` khi driver dang online sau rating.

---

## Commit: `feat: add admin trip list api`

Branch: `feature/admin-trip-list-api`

Phase: Phase 7 - Admin module, theo `integrate-plan.md` muc Admin module

### Muc tieu

Bo sung API de admin theo doi danh sach trip va loc theo status/khoang thoi gian request. Endpoint nay phuc vu man hinh trip monitoring truoc khi lam tiep dashboard/statistics.

### Noi dung da trien khai

- Them `AdminTripController`:
  - `GET /api/v1/admin/trips`
  - Yeu cau role `ADMIN`.
  - Ho tro query params `status`, `from`, `to`, `page`, `size`.
- Them `AdminTripService`:
  - Validate pagination 1-based, `size` toi da 100.
  - Validate `from <= to`.
  - Sort trip theo `requestedAt DESC`.
  - Tra ve `PageResponse<TripResponse>` de FE dung lai contract trip hien co.
- Mo rong `TripRepository` voi `searchAdminTrips(...)`:
  - Bo qua trip da soft delete.
  - Optional filter theo `TripStatus`.
  - Optional filter theo `requestedAt` tu `from` den `to`.
  - Co `countQuery` rieng cho pagination.
- Them `AdminTripServiceTests`:
  - Kiem tra status/date filters duoc truyen xuong repository.
  - Kiem tra page 1-based sang Spring Pageable 0-based.
  - Kiem tra validate page/size va date range sai truoc khi query.
- Cap nhat `integrate-plan.md`:
  - Danh dau admin list trips/filter da hoan thien.
  - Them huong dan FE goi `GET /api/v1/admin/trips`.

### Review truoc commit

- Da xac nhan endpoint admin duoc bao ve bang `hasRole('ADMIN')`.
- Da xac nhan API dung pagination contract 1-based giong admin driver pending list.
- Da xac nhan query mac dinh khong tra trip da soft delete.
- Da xac nhan `from > to` tra `VALIDATION_ERROR` thay vi query sai khoang thoi gian.
- Da chay `./mvnw.cmd test`: pass 161 tests.

### Files chinh

- `src/main/java/com/example/goride/booking/controller/AdminTripController.java`
- `src/main/java/com/example/goride/booking/service/AdminTripService.java`
- `src/main/java/com/example/goride/booking/repository/TripRepository.java`
- `src/test/java/com/example/goride/booking/service/AdminTripServiceTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them admin stats/dashboard API.
- Them payment detail/history API neu FE can man hinh hoa don.
- Cap nhat `driver_profiles.total_trips` khi trip completed.

---

## Commit: `feat: authorize trip topic subscriptions`

Branch: `feature/stomp-subscribe-authorization`

Phase: Phase 5 - Tracking realtime va WebSocket security, theo `docs/backend-implementation.md` muc 6.3 va 10.2

### Muc tieu

Chan user subscribe vao topic realtime cua trip khong thuoc ve minh. Sau commit STOMP JWT auth, backend da biet user la ai; commit nay them owner check cho trip status/location topics.

### Noi dung da trien khai

- Them `StompSubscriptionAuthorizer` lam extension point cho cac rule authorize STOMP subscribe.
- Cap nhat `StompJwtAuthenticationInterceptor`:
  - Khi frame la `SUBSCRIBE`, goi tat ca `StompSubscriptionAuthorizer`.
  - Van set `SecurityContext` cho handler thread nhu truoc.
- Them `TripTopicSubscriptionAuthorizer`:
  - Guard `/topic/trip/{tripId}/status`.
  - Guard `/topic/trip/{tripId}/location`.
  - Cho phep passenger cua trip, driver cua trip, hoac admin.
  - Bo qua destination khong phai trip topic.
- Mo rong `TripRepository` voi query `existsAccessibleTripTopicByUserId(...)`.
- Them tests:
  - Interceptor delegate SUBSCRIBE sang authorizer.
  - Passenger/driver owner duoc subscribe.
  - User khong thuoc trip bi reject.
  - Admin duoc subscribe khong can owner lookup.
  - Destination khac khong bi guard nham.
  - Principal subject khong phai numeric bi reject.
- Cap nhat `integrate-plan.md` de FE biet trip topics da enforce owner/admin.

### Review truoc commit

- Da xac nhan guard dung destination thuc te trong code: `/topic/trip/%d/status` va `/topic/trip/%d/location`.
- Da xac nhan user queues nhu `/user/queue/notifications` va `/user/queue/trip-requests` khong bi trip topic guard nham.
- Da xac nhan query owner check khong load full trip entity.
- Da chay `./mvnw.cmd test`: pass 158 tests.

### Files chinh

- `src/main/java/com/example/goride/common/security/StompSubscriptionAuthorizer.java`
- `src/main/java/com/example/goride/common/security/StompJwtAuthenticationInterceptor.java`
- `src/main/java/com/example/goride/booking/security/TripTopicSubscriptionAuthorizer.java`
- `src/main/java/com/example/goride/booking/repository/TripRepository.java`
- `src/test/java/com/example/goride/common/security/StompJwtAuthenticationInterceptorTests.java`
- `src/test/java/com/example/goride/booking/security/TripTopicSubscriptionAuthorizerTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them admin trip filter va stats/dashboard.
- Them payment detail/history API neu FE can man hinh hoa don.
- Cap nhat `driver_profiles.total_trips` khi trip completed.

---

## Commit: `feat: add pricing config api`

Branch: `feature/pricing-api`

Phase: Phase 3 - Pricing + Booking va Phase 7 - Admin minimum, theo `docs/backend-implementation.md` muc 3.5 va `integrate-plan.md`

### Muc tieu

Mo API pricing de FE hien thi bang gia active va admin quan ly pricing versions. Backend van tiep tuc tu tinh fare khi estimate/create booking, khong tin gia client gui len.

### Noi dung da trien khai

- Them DTO pricing:
  - `PricingConfigResponse`
  - `PricingConfigCreateRequest`
- Mo rong `PricingConfigRepository`:
  - List active pricing configs.
  - List tat ca pricing versions cho admin.
  - Tim active configs theo `vehicleType` de deactivate khi tao version moi.
- Them `PricingConfigService`:
  - Public list active pricing.
  - Admin list all pricing.
  - Admin create pricing version moi va deactivate active config cu cung `vehicleType`.
  - Chan `effectiveFrom` trong tuong lai de tranh deactivate pricing hien tai truoc khi version moi co hieu luc.
  - Admin deactivate pricing config theo id.
- Them controller:
  - `GET /api/v1/pricing`
  - `GET /api/v1/admin/pricing`
  - `POST /api/v1/admin/pricing`
  - `PATCH /api/v1/admin/pricing/{pricingConfigId}/deactivate`
- Mo REST security cho `/api/v1/pricing/**` public; admin endpoints van yeu cau role `ADMIN`.
- Them service tests cho list/create/deactivate pricing.
- Cap nhat `integrate-plan.md` voi huong dan FE goi pricing APIs.

### Review truoc commit

- Da xac nhan public endpoint chi expose pricing config active.
- Da xac nhan admin create version moi khong sua version cu truc tiep, ma deactivate active config cu va tao record moi.
- Da xac nhan booking estimate/create van dung server-side pricing config hien co.
- Da xac nhan khong cho tao pricing version co `effectiveFrom` trong tuong lai vi MVP deactivate active config cu ngay.
- Da chay `./mvnw.cmd test`: pass 152 tests.

### Files chinh

- `src/main/java/com/example/goride/booking/controller/PricingController.java`
- `src/main/java/com/example/goride/booking/controller/AdminPricingController.java`
- `src/main/java/com/example/goride/booking/service/PricingConfigService.java`
- `src/main/java/com/example/goride/booking/dto/PricingConfigResponse.java`
- `src/main/java/com/example/goride/booking/dto/PricingConfigCreateRequest.java`
- `src/main/java/com/example/goride/booking/repository/PricingConfigRepository.java`
- `src/test/java/com/example/goride/booking/service/PricingConfigServiceTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them authorize SUBSCRIBE theo owner cua trip cho `/topic/trip/{tripId}/...`.
- Them admin trip filter va stats/dashboard.
- Them payment detail/history API neu FE can man hinh hoa don.

---

## Commit: `feat: add stomp jwt authentication`

Branch: `feature/stomp-jwt-auth`

Phase: Phase 5 - Tracking realtime, theo `docs/backend-implementation.md` muc 6.1 va 10.2

### Muc tieu

Hoan thien authentication cho WebSocket/STOMP: client ket noi `/ws`, sau do gui JWT trong STOMP `CONNECT` de backend gan dung `Principal` va roles cho cac message mapping realtime.

### Noi dung da trien khai

- Them `StompJwtAuthenticationInterceptor`:
  - Doc `Authorization: Bearer <accessToken>` tu STOMP `CONNECT`.
  - Decode JWT bang `JwtDecoder` hien co.
  - Chuyen claim `roles` thanh authorities `ROLE_*`.
  - Gan `JwtAuthenticationToken` vao STOMP user/principal.
  - Set `SecurityContext` trong `beforeHandle` cho `SEND`/`SUBSCRIBE` de method security co authentication tren handler thread.
  - Chan `CONNECT` thieu/sai token va chan `SEND`/`SUBSCRIBE` khong co authenticated principal.
- Wire interceptor vao `WebSocketConfig.configureClientInboundChannel(...)`.
- Mo REST security cho `/ws` va `/ws/**` de handshake WebSocket khong bi chan truoc khi STOMP `CONNECT` gui token.
- Them unit test cho interceptor:
  - CONNECT hop le.
  - CONNECT thieu token.
  - CONNECT token invalid.
  - SEND co principal se set/clear `SecurityContext`.
  - SEND khong co principal bi reject.
- Cap nhat `integrate-plan.md` de FE biet authentication nam o STOMP `CONNECT`, khong nam o HTTP handshake.

### Review truoc commit

- Da xac nhan `/ws` permitAll chi ap dung HTTP handshake; STOMP frame van bat buoc JWT o interceptor.
- Da xac nhan role mapping dung claim `roles` va prefix `ROLE_`, khop REST JWT converter.
- Da xac nhan `SecurityContextHolder.clearContext()` duoc goi sau message handled de tranh leak authentication giua thread.
- Da chay `./mvnw.cmd test`: pass 146 tests.

### Files chinh

- `src/main/java/com/example/goride/common/security/StompJwtAuthenticationInterceptor.java`
- `src/main/java/com/example/goride/common/config/WebSocketConfig.java`
- `src/main/java/com/example/goride/auth/config/SecurityConfig.java`
- `src/test/java/com/example/goride/common/security/StompJwtAuthenticationInterceptorTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them authorize SUBSCRIBE theo owner cua trip cho `/topic/trip/{tripId}/...`.
- Them pricing public/admin API.
- Them admin trip filter va stats/dashboard.

---

## Commit: `feat: add admin driver approval api`

Branch: `feature/admin-driver-approval`

Phase: Admin Module minimum, theo `docs/backend-implementation.md` muc 5.7 va checklist FE trong `integrate-plan.md`

### Muc tieu

Giai quyet diem nghen FE/driver onboarding: driver can profile `APPROVED` moi bat online duoc, nhung truoc commit nay chua co API admin duyet ho so. Commit nay them API admin xem ho so pending va approve/reject driver.

### Noi dung da trien khai

- Them `AdminDriverController`:
  - `GET /api/v1/admin/drivers/pending?page=1&size=20`
  - `PATCH /api/v1/admin/drivers/{driverId}/approval`
  - Tat ca endpoint yeu cau role `ADMIN`.
- Them `DriverApprovalUpdateRequest` voi field `approvalStatus`.
- Them `DriverApprovalService`:
  - List driver profiles dang `PENDING` voi pagination 1-based.
  - Approve driver profile bang driver user id.
  - Reject driver profile bang driver user id.
  - Khi reject, set profile offline va goi Redis availability store de xoa driver khoi online pool.
  - Chan request `PENDING` vi endpoint chi cho `APPROVED` hoac `REJECTED`.
- Mo rong `DriverProfileRepository`:
  - Query pending profiles theo `approvalStatus`.
  - Tai su dung lock lookup theo `userId` cho update approval an toan.
- Cap nhat `integrate-plan.md`:
  - Danh dau admin pending/approve-reject da hoan thien.
  - Them huong dan FE goi Admin driver approval APIs.

### Review truoc commit

- Da xac nhan endpoint admin duoc bao ve bang `hasRole('ADMIN')`.
- Da xac nhan approve/reject dung driver user id, khop voi convention `driverId` trong cac API driver/rating hien co.
- Da xac nhan reject driver online se goi `markOffline(...)` sau transaction commit.
- Da xac nhan page/size sai va request `PENDING` bi chan bang `VALIDATION_ERROR`.
- Da chay `./mvnw.cmd test`: pass 141 tests.

### Files chinh

- `src/main/java/com/example/goride/driver/controller/AdminDriverController.java`
- `src/main/java/com/example/goride/driver/service/DriverApprovalService.java`
- `src/main/java/com/example/goride/driver/dto/DriverApprovalUpdateRequest.java`
- `src/main/java/com/example/goride/driver/repository/DriverProfileRepository.java`
- `src/test/java/com/example/goride/driver/service/DriverApprovalServiceTests.java`
- `integrate-plan.md`

### Viec tiep theo

- Them WebSocket JWT authentication interceptor cho STOMP `CONNECT`.
- Them pricing public/admin API.
- Them admin stats/dashboard.

---

## Commit: `feat: add driver rating list api`

Branch: `feature/driver-rating-list-api`

Phase: Phase 6 - Payment + Rating, theo `docs/TDD.md` muc 4.9 va `docs/backend-implementation.md` muc 5.6

### Muc tieu

Bo sung API public de xem danh sach rating cua mot driver. API nay phuc vu man hinh ho so/lich su danh gia cong khai va dung pagination 1-based nhu tai lieu TDD.

### Noi dung da trien khai

- Them endpoint public:
  - `GET /api/v1/drivers/{driverId}/ratings?page=1&size=20`
  - Tra ve `ApiResponse<PageResponse<RatingResponse>>`.
  - Khong yeu cau JWT nhung van validate driver profile ton tai.
- Mo rong `SecurityConfig`:
  - Permit route `/api/v1/drivers/*/ratings` de endpoint dung contract public.
- Mo rong `RatingRepository`:
  - Them `findByDriverIdOrderByCreatedAtDesc(driverId, pageable)`.
- Mo rong `RatingService`:
  - Them `listDriverRatings(driverId, page, size)`.
  - Validate `driverId`, `page >= 1`, `1 <= size <= 100`.
  - Convert page 1-based tu API sang `PageRequest` 0-based cua Spring Data.
  - Tra ve `PageResponse` gom items va metadata pagination.

### Review truoc commit

- Da xac nhan route public duoc permit trong security filter.
- Da xac nhan driver khong co profile tra `DRIVER_PROFILE_NOT_FOUND`.
- Da xac nhan page/size sai bi chan truoc khi query database.
- Da xac nhan danh sach rating sap xep theo `createdAt DESC`.
- Da chay `./mvnw.cmd test`: pass 122 tests.

### Files chinh

- `src/main/java/com/example/goride/rating/controller/DriverRatingController.java`
- `src/main/java/com/example/goride/rating/service/RatingService.java`
- `src/main/java/com/example/goride/rating/repository/RatingRepository.java`
- `src/main/java/com/example/goride/auth/config/SecurityConfig.java`
- `src/test/java/com/example/goride/rating/service/RatingServiceTests.java`

### Viec tiep theo

- Dong bo Redis `driver:{id}:meta.rating` khi driver dang online de matching co rating moi hon.
- Tiep tuc admin/statistics: doanh thu tu payment completed va rating trung binh.

---

## Commit: `feat: add trip rating flow`

Branch: `feature/trip-rating-flow`

Phase: Phase 6 - Payment + Rating, theo `docs/TDD.md` muc 3.7, 4.9 va `docs/backend-implementation.md` muc 3.10, 5.6

### Muc tieu

Cho phep passenger danh gia driver sau khi trip da hoan tat. Moi trip chi duoc rating mot lan, chi passenger cua trip moi duoc rating, va diem trung binh cua driver duoc cap nhat ngay trong cung transaction.

### Noi dung da trien khai

- Them rating domain va repository:
  - `Rating`: entity map bang `ratings`, lien ket unique voi `trip_id`, luu `passenger_id`, `driver_id`, `score`, `comment`, `created_at`.
  - `RatingRepository.existsByTripId(...)` de chan rating lap cho cung trip.
- Them REST API:
  - `POST /api/v1/ratings`
  - Yeu cau role `PASSENGER`.
  - Request gom `tripId`, `score`, `comment`.
  - Response tra ve `ratingId`, `tripId`, `driverId`, `score`, `comment`, `createdAt`.
- Them `RatingService.createRating(...)`:
  - Lock trip theo `tripId` bang `TripRepository.findActiveByIdForUpdate(...)`.
  - Validate passenger hien tai la passenger cua trip.
  - Chi cho rating trip `COMPLETED` va da co driver.
  - Chan duplicate bang `TRIP_ALREADY_RATED`.
  - Lock `DriverProfile` theo driver user id de cap nhat rating an toan khi co concurrency.
  - Cap nhat `averageRating` va `totalRatings` bang weighted average, lam tron 1 chu so thap phan.
- Mo rong `DriverProfileRepository` voi `findByUserIdForUpdate(...)`.
- Cap nhat Spring context test mock `RatingRepository`.

### Review truoc commit

- Da xac nhan API chi expose cho role `PASSENGER`.
- Da xac nhan user khong phai passenger cua trip bi chan bang `FORBIDDEN`.
- Da xac nhan trip chua `COMPLETED` khong duoc rating.
- Da xac nhan moi trip chi co mot rating va duplicate tra `TRIP_ALREADY_RATED`.
- Da xac nhan driver average rating tinh lai dung tu average hien tai, total hien tai va score moi.
- Da chay `./mvnw.cmd test`: pass 119 tests.

### Files chinh

- `src/main/java/com/example/goride/rating/domain/Rating.java`
- `src/main/java/com/example/goride/rating/repository/RatingRepository.java`
- `src/main/java/com/example/goride/rating/service/RatingService.java`
- `src/main/java/com/example/goride/rating/controller/RatingController.java`
- `src/main/java/com/example/goride/rating/dto/RatingCreateRequest.java`
- `src/main/java/com/example/goride/rating/dto/RatingResponse.java`
- `src/main/java/com/example/goride/driver/repository/DriverProfileRepository.java`
- `src/test/java/com/example/goride/rating/domain/RatingTests.java`
- `src/test/java/com/example/goride/rating/service/RatingServiceTests.java`

### Viec tiep theo

- Them API public xem rating cua driver: `GET /api/v1/drivers/{driverId}/ratings`.
- Dong bo Redis `driver:{id}:meta.rating` khi driver dang online de matching co rating moi hon.
- Tiep tuc admin/statistics: doanh thu tu payment completed va rating trung binh.

---

## Commit: `feat: add cash payment confirmation`

Branch: `feature/cash-payment-confirmation`

Phase: Phase 5 - Payment Module, theo `docs/TDD.md` muc 5.5

### Muc tieu

Hoan tat MVP cash payment sau khi trip da `COMPLETED`: driver xac nhan da nhan tien mat, payment chuyen tu `PENDING` sang `COMPLETED`, ghi `paidAt`, notify passenger/driver va dua driver ve trang thai co the nhan chuyen tiep theo.

### Noi dung da trien khai

- Them endpoint driver xac nhan thanh toan tien mat:
  - `PATCH /api/v1/drivers/trips/{tripId}/payment-confirm`
  - Yeu cau role `DRIVER`, lay driver id tu authentication hien tai.
  - Tra ve `PaymentConfirmationResponse` gom `tripId`, `status`, `amount`, `paidAt`.
- Them `CashPaymentConfirmationService`:
  - Lock payment theo `tripId` bang `PaymentRepository.findByTripIdForUpdate(...)`.
  - Validate payment ton tai, trip thuoc driver hien tai, trip da `COMPLETED`, method la `CASH`, status dang `PENDING`.
  - Goi `Payment.markCompleted()` de set `COMPLETED` va `paidAt`.
  - Sau transaction commit, set Redis driver status ve `AVAILABLE` va notify ca passenger/driver.
- Mo rong payment domain:
  - Them `Payment.markCompleted()`.
  - Them error code `PAYMENT_NOT_FOUND`, `PAYMENT_INVALID_STATUS`.
- Mo rong notification:
  - Them `NotificationType.PAYMENT_COMPLETED`.
  - Them `UserNotification.paymentCompleted(...)` voi payload `tripId`, `status`, `driverId`, `amount`, `paymentStatus`.
- Mo rong driver candidate Redis store:
  - Them `markCandidateAvailable(driverId)`.
  - Set `driver:{id}:status = AVAILABLE` voi TTL 60 giay sau khi payment completed.

### Review truoc commit

- Da xac nhan chi assigned driver cua trip moi confirm duoc payment.
- Da xac nhan payment chi duoc confirm khi trip `COMPLETED`, method `CASH`, status `PENDING`.
- Da xac nhan double-confirm bi chan bang `PAYMENT_INVALID_STATUS`.
- Da xac nhan notification va driver availability chi chay sau transaction commit.
- Da chay `./mvnw.cmd test`: pass 110 tests.

### Files chinh

- `src/main/java/com/example/goride/payment/service/CashPaymentConfirmationService.java`
- `src/main/java/com/example/goride/payment/dto/PaymentConfirmationResponse.java`
- `src/main/java/com/example/goride/payment/domain/Payment.java`
- `src/main/java/com/example/goride/payment/repository/PaymentRepository.java`
- `src/main/java/com/example/goride/driver/controller/DriverTripController.java`
- `src/main/java/com/example/goride/notification/dto/UserNotification.java`
- `src/main/java/com/example/goride/matching/service/DriverCandidateStore.java`
- `src/main/java/com/example/goride/matching/service/RedisDriverCandidateStore.java`
- `src/test/java/com/example/goride/payment/service/CashPaymentConfirmationServiceTests.java`
- `src/test/java/com/example/goride/payment/domain/PaymentTests.java`
- `src/test/java/com/example/goride/matching/service/RedisDriverCandidateStoreTests.java`

### Viec tiep theo

- Them rating flow sau khi payment completed: passenger danh gia driver/trip.
- Cap nhat driver statistics/revenue tu payments `COMPLETED`.
- Tiep tuc hoan thien Payment Provider abstraction de mo rong MoMo/VNPay sau MVP cash.

---

## Commit: `feat: create trip completion payment`

Branch: `feature/trip-completion-payment`

Phase: Phase 5 - Payment Module, theo `docs/TDD.md` muc 3.8 va 5.5

### Muc tieu

Sau khi trip duoc cap nhat sang `COMPLETED` va da co `finalFare`, backend tao payment record de bat dau flow thanh toan. Voi MVP tien mat, record duoc tao o trang thai `PENDING`; buoc driver xac nhan da nhan tien se tach commit sau.

### Noi dung da trien khai

- Them payment domain:
  - `PaymentStatus`: `PENDING`, `COMPLETED`, `FAILED`, `REFUNDED`.
  - `Payment`: entity map bang `payments`, lien ket unique voi `trip_id`.
- Them `PaymentRepository` voi lookup theo `tripId`.
- Them `TripPaymentService.createPendingPayment(...)`:
  - Neu trip da co payment thi tra ve payment hien co, tranh tao duplicate.
  - Neu chua co thi tao `Payment.createPending(trip)`.
- Noi vao `DriverTripStatusService`:
  - Khi trip status sau update la `COMPLETED`, tao pending payment trong cung transaction voi trip/history.
  - Cac transition `ARRIVED` va `IN_PROGRESS` khong tao payment.
- Cap nhat Spring context test mock `PaymentRepository`.

### Review truoc commit

- Da xac nhan payment chi duoc tao cho trip `COMPLETED` va da co `finalFare`.
- Da xac nhan payment amount lay tu `trip.finalFare`, method lay tu `trip.paymentMethod`, status mac dinh `PENDING`.
- Da xac nhan service khong tao duplicate khi payment cho trip da ton tai.
- Da xac nhan transition sai thu tu khong goi payment service.
- Da chay `./mvnw.cmd test`: pass 102 tests.

### Files chinh

- `src/main/java/com/example/goride/payment/domain/Payment.java`
- `src/main/java/com/example/goride/payment/domain/PaymentStatus.java`
- `src/main/java/com/example/goride/payment/repository/PaymentRepository.java`
- `src/main/java/com/example/goride/payment/service/TripPaymentService.java`
- `src/main/java/com/example/goride/driver/service/DriverTripStatusService.java`
- `src/test/java/com/example/goride/payment/domain/PaymentTests.java`
- `src/test/java/com/example/goride/payment/service/TripPaymentServiceTests.java`
- `src/test/java/com/example/goride/driver/service/DriverTripStatusServiceTests.java`

### Viec tiep theo

- Them endpoint driver xac nhan da nhan tien mat: `PATCH /drivers/trips/{tripId}/payment-confirm`.
- Chuyen payment `PENDING -> COMPLETED`, set `paidAt`.
- Notify passenger/driver va dua driver ve `AVAILABLE` sau khi thanh toan hoan tat.

---

## Commit: `feat: calculate trip completion fare`

Branch: `feature/trip-completion-fare`

Phase: Phase 5 - Payment Module va Tracking Module, theo `docs/TDD.md` muc 5.5 va 5.4

### Muc tieu

Khi driver cap nhat trip sang `COMPLETED`, backend khong con dung estimated fare/distance truc tiep nua. Thay vao do, he thong tinh actual distance tu `trip_location_history`, tinh actual duration tu `startedAt` den thoi diem hoan tat, tinh final fare bang pricing config cua trip, roi ghi vao `trips.final_fare`, `actual_distance_km`, `actual_duration_min`.

Commit nay moi xu ly tinh fare va cap nhat trip khi completed. Tao bang/record payment va flow thanh toan tien mat se tach commit sau.

### Noi dung da trien khai

- Them `TripCompletionFareService` trong package `payment.service`:
  - Doc `trip_location_history` theo thu tu `recorded_at`.
  - Tinh tong quang duong bang Haversine giua cac diem lien tiep.
  - Lam tron actual distance ve 2 chu so thap phan.
  - Tinh actual duration bang chenh lech giua `startedAt` va thoi diem completed, lam tron len theo phut.
  - Dung `PricingConfig.estimateFare(actualDistanceKm, actualDurationMin)` de tinh final fare theo pricing hien co.
- Them `TripCompletionFare` record de tra ve `finalFare`, `actualDistanceKm`, `actualDurationMin`.
- Them `TimeConfig` cung cap `Clock` de logic tinh duration co the test on dinh.
- Cap nhat `DriverTripStatusService`:
  - Khi status `COMPLETED`, validate trip dang `IN_PROGRESS`.
  - Goi `TripCompletionFareService` truoc khi `trip.complete(...)`.
  - Notification `TRIP_COMPLETED` tiep tuc co `finalFare` trong payload.
- Co fallback an toan: neu tracking history chua du 2 diem hoac quang duong tinh duoc bang 0, dung `estimatedDistanceKm` de trip van co the hoan tat.

### Review truoc commit

- Da xac nhan `COMPLETED` dung actual fare tu calculator thay vi estimated fare hard-code.
- Da xac nhan transition sai thu tu khong goi calculator.
- Da xac nhan calculator tinh distance tu history va fallback khi history thieu diem.
- Da xac nhan duration partial minute duoc lam tron len de tranh duration 0.
- Da xac nhan notification completed co `finalFare` moi trong payload.
- Da chay `./mvnw.cmd test`: pass 98 tests.

### Files chinh

- `src/main/java/com/example/goride/payment/service/TripCompletionFareService.java`
- `src/main/java/com/example/goride/payment/service/TripCompletionFare.java`
- `src/main/java/com/example/goride/driver/service/DriverTripStatusService.java`
- `src/main/java/com/example/goride/common/config/TimeConfig.java`
- `src/test/java/com/example/goride/payment/service/TripCompletionFareServiceTests.java`
- `src/test/java/com/example/goride/driver/service/DriverTripStatusServiceTests.java`

### Viec tiep theo

- Tao payment entity/repository cho payment record khi trip completed.
- Xu ly cash payment MVP va publish/notify payment completed.
- Dua driver status ve `AVAILABLE` sau khi trip hoan tat.

---

## Commit: `feat: add trip location tracking`

Branch: `feature/trip-location-tracking`

Phase: Phase 5 - Tracking Module va Phase 4 - WebSocket, theo `docs/TDD.md` muc 4.8, 4.13, 4.14 va 5.4

### Muc tieu

Them tracking MVP cho chuyen dang chay: driver gui toa do realtime, backend luu location history, cache vi tri moi nhat vao Redis va broadcast vi tri qua topic cua trip. Passenger co REST fallback de lay vi tri driver moi nhat khi mat/reconnect WebSocket.

Commit nay chua xu ly heartbeat/offline timeout va chua tinh actual distance/final fare tu location history. Cac phan do se tach commit rieng sau khi tracking loop co nen tang.

### Noi dung da trien khai

- Them entity `TripLocationHistory` va repository `TripLocationHistoryRepository` cho bang `trip_location_history`.
- Them DTO tracking:
  - `DriverLocationUpdateRequest`: `lat`, `lng`, optional `bearing`, `speed`.
  - `DriverLocationResponse`: payload broadcast/REST fallback.
- Them `TripLocationTrackingService`:
  - Driver update location chi duoc chap nhan khi driver co trip `IN_PROGRESS`.
  - Luu moi diem tracking vao PostgreSQL.
  - Sau transaction commit moi cache latest location va broadcast topic.
  - Passenger chi lay duoc latest location cua trip minh so huu.
- Them Redis latest-location store:
  - Key `driver:{id}:location`.
  - TTL 30 giay theo tracking design.
- Them WebSocket/REST entrypoints:
  - `SEND /app/driver.location` cho driver gui toa do.
  - `GET /api/v1/tracking/trips/{tripId}/driver-location` cho passenger lay fallback.
  - Broadcast `/topic/trip/{tripId}/location`.
- Mo rong `CurrentUser` de doc user id tu `Principal` trong WebSocket handler.
- Them `DRIVER_LOCATION_NOT_FOUND` cho truong hop cache location khong co/het TTL.

### Review truoc commit

- Da xac nhan driver khong co trip `IN_PROGRESS` bi chan va khong ghi location history.
- Da xac nhan passenger khong so huu trip bi chan bang `FORBIDDEN`.
- Da xac nhan latest location thieu/het TTL tra `DRIVER_LOCATION_NOT_FOUND`.
- Da xac nhan location history, Redis cache va WebSocket broadcast dung trip/driver id.
- Da xac nhan callback sau commit chi dung payload/id da capture, khong giu lazy entity.
- Da chay `./mvnw.cmd test`: pass 95 tests.

### Files chinh

- `src/main/java/com/example/goride/tracking/domain/TripLocationHistory.java`
- `src/main/java/com/example/goride/tracking/dto/DriverLocationUpdateRequest.java`
- `src/main/java/com/example/goride/tracking/dto/DriverLocationResponse.java`
- `src/main/java/com/example/goride/tracking/service/TripLocationTrackingService.java`
- `src/main/java/com/example/goride/tracking/service/RedisLatestDriverLocationStore.java`
- `src/main/java/com/example/goride/tracking/service/WebSocketTripLocationNotifier.java`
- `src/main/java/com/example/goride/tracking/controller/TrackingWebSocketController.java`
- `src/main/java/com/example/goride/tracking/controller/TrackingController.java`
- `src/test/java/com/example/goride/tracking/service/*`

### Viec tiep theo

- Tinh actual distance tu `trip_location_history`.
- Cap nhat `COMPLETED` de dung actual distance/fare thay vi estimated data.
- Tao payment record va dua driver ve `AVAILABLE` sau khi trip hoan tat.

---

## Commit: `feat: add driver trip status API`

Branch: `feature/driver-trip-status-api`

Phase: Phase 4 - Driver API, Phase 5 - Booking lifecycle va Phase 6 - Notification, theo `docs/TDD.md` muc 4.7, 5.2 va 5.6

### Muc tieu

Cho phep driver cap nhat vong doi trip sau khi da accept: den diem don, bat dau chuyen, va ket thuc chuyen. Moi lan doi status phai duoc lock trip, kiem tra driver dang duoc gan vao trip, ghi audit history va push realtime status sau khi transaction commit.

Commit nay chua tinh lai final fare theo tracking thuc te va chua tao payment record. Khi `COMPLETED`, service tam dung estimated fare, estimated distance va estimated duration de dong vong doi trip; phan payment/final fare se tach commit sau.

### Noi dung da trien khai

- Them endpoint `PATCH /api/v1/drivers/trips/{tripId}/status`.
- Them request `DriverTripStatusUpdateRequest` voi `status` bat buoc.
- Them `DriverTripStatusService`:
  - Lock trip bang `findActiveByIdForUpdate`.
  - Chi cho driver dang duoc assign vao trip cap nhat status.
  - Chi chap nhan `ARRIVED`, `IN_PROGRESS`, `COMPLETED`.
  - Reuse domain methods `markArrived`, `startTrip`, `complete` de giu transition rule o aggregate `Trip`.
  - Ghi `trip_status_history` cho moi transition thanh cong.
- Mo rong notification:
  - Them `DRIVER_ARRIVED`, `TRIP_STARTED`, `TRIP_COMPLETED`.
  - Broadcast `/topic/trip/{tripId}/status` cho moi transition.
  - Notify passenger khi driver den noi, trip bat dau, trip hoan thanh.
  - Notify driver ca nhan khi trip hoan thanh.
- Mo rong `TripRealtimeNotifier` voi `notifyUser(...)`, giu `notifyPassenger(...)` la convenience method de cac flow cu tiep tuc dung duoc.

### Review truoc commit

- Da xac nhan driver khong so huu trip bi chan bang `FORBIDDEN`.
- Da xac nhan transition sai thu tu, vi du `ACCEPTED -> COMPLETED`, bi chan bang `TRIP_STATUS_INVALID_TRANSITION`.
- Da xac nhan status ngoai scope driver API, vi du `CANCELLED`, bi chan.
- Da xac nhan moi transition thanh cong co ghi `trip_status_history`.
- Da xac nhan realtime notification chi duoc dang ky sau transaction commit va khong giu lazy entity trong callback.
- Da chay `./mvnw.cmd test`: pass 85 tests.

### Files chinh

- `src/main/java/com/example/goride/driver/controller/DriverTripController.java`
- `src/main/java/com/example/goride/driver/dto/DriverTripStatusUpdateRequest.java`
- `src/main/java/com/example/goride/driver/service/DriverTripStatusService.java`
- `src/main/java/com/example/goride/notification/domain/NotificationType.java`
- `src/main/java/com/example/goride/notification/dto/UserNotification.java`
- `src/main/java/com/example/goride/notification/service/TripRealtimeNotifier.java`
- `src/test/java/com/example/goride/driver/service/DriverTripStatusServiceTests.java`

### Viec tiep theo

- Them tracking location API/WebSocket cho trip dang `IN_PROGRESS`.
- Tinh final fare theo location history khi `COMPLETED`.
- Tao payment record va cap nhat driver status ve `AVAILABLE` sau khi chuyen hoan tat.

---

## Commit: `feat: notify passengers about matching results`

Branch: `feature/matching-passenger-notifications`

Phase: Phase 4 - Matching va Phase 6 - Notification, theo `docs/TDD.md` muc 4.13, 4.14 va 5.6

### Muc tieu

Thong bao realtime cho passenger khi matching co ket qua cuoi: driver accept trip hoac he thong khong tim duoc driver.

Commit nay chua xu ly cac trang thai sau do nhu `ARRIVED`, `IN_PROGRESS`, `COMPLETED`. Cac trang thai do se di chung voi commit driver trip status/tracking sau.

### Noi dung da trien khai

- Them notification payload:
  - `NotificationType`: `TRIP_ACCEPTED`, `NO_DRIVER_FOUND`.
  - `UserNotification`: payload gui qua `/user/queue/notifications`.
  - `TripStatusNotification`: payload broadcast qua `/topic/trip/{tripId}/status`.
- Them `TripRealtimeNotifier` interface de tach matching service khoi WebSocket implementation.
- Them `WebSocketTripRealtimeNotifier`:
  - Gui notification ca nhan den passenger bang `convertAndSendToUser(passengerId, "/queue/notifications", payload)`.
  - Broadcast status trip bang `convertAndSend("/topic/trip/{tripId}/status", payload)`.
- Noi notification vao matching flow:
  - Khi driver `ACCEPT`: gui `TRIP_ACCEPTED` cho passenger va broadcast `ACCEPTED`.
  - Khi reject/timeout dan den `NO_DRIVER`: gui `NO_DRIVER_FOUND` cho passenger va broadcast `NO_DRIVER`.
- Tat ca notification duoc dang ky gui sau transaction commit de tranh push status khi DB rollback.

### Review truoc commit

- Da xac nhan accept tao notification `TRIP_ACCEPTED` va status topic `ACCEPTED`.
- Da xac nhan reject/timeout dan den `NO_DRIVER` tao notification `NO_DRIVER_FOUND` va status topic `NO_DRIVER`.
- Da xac nhan retry sang driver tiep theo khong notify passenger som.
- Da xac nhan WebSocket destination dung voi TDD: `/user/queue/notifications` va `/topic/trip/{tripId}/status`.
- Da chay `./mvnw.cmd test`: pass 78 tests.

### Files chinh

- `src/main/java/com/example/goride/notification/domain/NotificationType.java`
- `src/main/java/com/example/goride/notification/dto/UserNotification.java`
- `src/main/java/com/example/goride/notification/dto/TripStatusNotification.java`
- `src/main/java/com/example/goride/notification/service/TripRealtimeNotifier.java`
- `src/main/java/com/example/goride/notification/service/WebSocketTripRealtimeNotifier.java`
- `src/main/java/com/example/goride/matching/service/DriverOfferResponseService.java`
- `src/main/java/com/example/goride/matching/service/MatchingOfferTimeoutService.java`

### Viec tiep theo

- Them API driver cap nhat trip status: `ARRIVED`, `IN_PROGRESS`, `COMPLETED`.
- Reuse `TripRealtimeNotifier` de notify passenger o cac status tiep theo.
- Them WebSocket/JWT handshake security.

---

## Commit: `feat: add matching offer timeout handler`

Branch: `feature/matching-offer-timeout`

Phase: Phase 4 - Matching, theo `docs/TDD.md` muc 5.3 retry logic va timeout 30 giay

### Muc tieu

Tu dong xu ly offer matching bi qua han: release driver hien tai, retry driver tiep theo neu con attempt, hoac chuyen trip sang `NO_DRIVER` khi het driver/het 3 lan thu.

Commit nay chi xu ly timeout o backend. Notification cho passenger khi `ACCEPTED` hoac `NO_DRIVER` se tach commit rieng.

### Noi dung da trien khai

- Bat Spring scheduling qua `SchedulingConfig`.
- Them `MatchingOfferTimeoutScheduler`:
  - Quet danh sach trip dang matching theo `matching:activeTrips`.
  - Goi service xu ly tung trip het han.
  - Cho phep cau hinh qua `app.matching.timeout-scheduler.*`.
- Them `MatchingOfferTimeoutService`:
  - Bo qua offer chua het han hoac khong con matching state.
  - Khi offer het han: release `driver:{id}:lock`, clear `trip:{id}:matching`.
  - Neu trip khong con `SEARCHING`: chi don state, khong retry.
  - Neu con attempt: them driver timeout vao excluded list, retry driver tiep theo va gui offer moi.
  - Neu het 3 attempts hoac khong lock duoc driver moi: cap nhat trip sang `NO_DRIVER` va ghi `trip_status_history`.
- Mo rong Redis matching store:
  - Luu active trip id vao set `matching:activeTrips` khi record matching state.
  - Xoa trip id khoi set khi clear matching state.
  - Doc danh sach active matching trip ids cho scheduler.
- Tat scheduler trong test config de context test khong goi Redis nen ngoai y muon.

### Review truoc commit

- Da xac nhan timeout khong retry offer chua het han.
- Da xac nhan driver bi timeout duoc exclude o attempt tiep theo.
- Da xac nhan stale matching state duoc don neu trip da bi cancel/khong con `SEARCHING`.
- Da xac nhan active trip set duoc don khi hash matching da het TTL.
- Da xac nhan `NO_DRIVER` co ghi status history khi timeout het attempt.
- Da chay `./mvnw.cmd test`: pass 76 tests.

### Files chinh

- `src/main/java/com/example/goride/common/config/SchedulingConfig.java`
- `src/main/java/com/example/goride/matching/service/MatchingOfferTimeoutScheduler.java`
- `src/main/java/com/example/goride/matching/service/MatchingOfferTimeoutService.java`
- `src/main/java/com/example/goride/matching/service/RedisDriverCandidateStore.java`
- `src/test/java/com/example/goride/matching/service/MatchingOfferTimeoutServiceTests.java`
- `src/test/java/com/example/goride/matching/service/MatchingOfferTimeoutSchedulerTests.java`

### Viec tiep theo

- Notify passenger khi trip `ACCEPTED` hoac `NO_DRIVER`.
- Them realtime status topic `/topic/trip/{tripId}/status`.
- Them WebSocket/JWT handshake security.

---

## Commit: `feat: add driver offer response API`

Branch: `feature/matching-offer-response`

Phase: Phase 4 - Matching, theo `docs/TDD.md` muc 4.7 va 5.3

### Muc tieu

Them endpoint de driver phan hoi offer matching: chap nhan thi gan driver vao trip, tu choi thi release offer hien tai va thu driver tiep theo trong gioi han 3 lan.

Commit nay chua xu ly timeout tu dong bang scheduler/heartbeat. Timeout se tach commit rieng de tranh tron logic bat dong bo vao API response.

### Noi dung da trien khai

- Them endpoint `PATCH /api/v1/drivers/trips/{tripId}/respond`.
- Them request action `ACCEPT | REJECT` va response toi thieu gom `tripId`, `status`.
- Them `DriverOfferResponseService`:
  - Doc `trip:{id}:matching` de xac nhan driver hien tai dung la `offeredDriverId`.
  - Tu choi offer het han va don state Redis lien quan.
  - Khi `ACCEPT`: lock trip bang pessimistic write, chuyen `SEARCHING -> ACCEPTED`, gan driver, ghi `trip_status_history`, set Redis status driver thanh `BUSY`, clear matching state.
  - Khi `REJECT`: release driver lock, clear offer cu, them driver vao excluded list, goi matching lai voi attempt tiep theo, gui offer moi neu co driver.
  - Neu het 3 attempts hoac khong lock duoc driver tiep theo: chuyen trip sang `NO_DRIVER` va ghi status history.
- Mo rong Redis matching store:
  - Luu/doc `rejectedDriverIds`.
  - Xoa `driver:{id}:lock`.
  - Xoa `trip:{id}:matching`.
  - Set `driver:{id}:status = BUSY` khi accept.
- Mo rong `MatchingService` de retry voi `attempt` va danh sach driver da reject.

### Review truoc commit

- Da xac nhan reject khong offer lai driver vua tu choi trong cung retry.
- Da xac nhan accept chi thanh cong khi driver dung voi `offeredDriverId`.
- Da xac nhan trip status transition co ghi history.
- Da xac nhan timeout tu dong chua nam trong commit nay.
- Da chay `./mvnw.cmd test`: pass 68 tests.

### Files chinh

- `src/main/java/com/example/goride/driver/controller/DriverTripController.java`
- `src/main/java/com/example/goride/driver/dto/DriverTripRespondRequest.java`
- `src/main/java/com/example/goride/matching/service/DriverOfferResponseService.java`
- `src/main/java/com/example/goride/matching/service/MatchingService.java`
- `src/main/java/com/example/goride/matching/service/RedisDriverCandidateStore.java`
- `src/test/java/com/example/goride/matching/service/DriverOfferResponseServiceTests.java`

### Viec tiep theo

- Them timeout handler de tu dong xu ly offer het han sau 30 giay.
- Notify passenger khi trip `ACCEPTED` hoac `NO_DRIVER`.
- Them WebSocket/JWT handshake security cho realtime channel.

---

## Commit: `feat: dispatch matching offers`

Branch: `feature/matching-offer-dispatch`

Phase: Phase 4 - Matching

### Muc tieu

Noi `BookingCreatedEvent` voi matching flow de khi passenger tao booking, he thong tu tim va lock driver gan nhat, sau do gui offer den user queue cua driver.

Commit nay chua xu ly driver accept/reject va timeout retry. Cac phan do se duoc tach commit rieng.

### Noi dung da trien khai

- Them WebSocket/STOMP config toi thieu:
  - Endpoint `/ws`.
  - Application prefix `/app`.
  - Simple broker `/topic`, `/queue`.
  - User destination prefix `/user`.
- Them listener:
  - `BookingCreatedMatchingListener` lang nghe `BookingCreatedEvent`.
  - Tao `MatchingRequest` tu booking event.
  - Goi `MatchingService.findAndLockDriver(...)`.
  - Neu lock duoc driver thi tao notification va gui offer.
- Them notification layer:
  - `DriverOfferNotification`: payload gui den driver, gom trip, passenger, pickup/dropoff, vehicle type, fare, distance, expiresAt.
  - `DriverOfferNotifier`: interface de tach kenh gui.
  - `WebSocketDriverOfferNotifier`: gui qua `SimpMessagingTemplate.convertAndSendToUser(driverId, "/queue/trip-requests", payload)`.

### Review truoc commit

- Xac nhan WebSocket config khong lam fail Spring context.
- Xac nhan listener khong notify khi matching khong lock duoc driver.
- Xac nhan queue dung voi tai lieu: `/user/queue/trip-requests`.
- Da chay `./mvnw.cmd test`: pass 56 tests.

### Files chinh

- `src/main/java/com/example/goride/common/config/WebSocketConfig.java`
- `src/main/java/com/example/goride/matching/service/BookingCreatedMatchingListener.java`
- `src/main/java/com/example/goride/matching/notification/*`
- `src/test/java/com/example/goride/matching/service/BookingCreatedMatchingListenerTests.java`
- `src/test/java/com/example/goride/matching/notification/WebSocketDriverOfferNotifierTests.java`

### Viec tiep theo

- Them API driver accept/reject offer.
- Validate driver dung la `offeredDriverId` trong `trip:{id}:matching`.
- Khi accept, cap nhat trip sang `ACCEPTED`, set driver status `BUSY`, va notify passenger.
- Khi reject/timeout, release lock va retry driver tiep theo.

---

## Commit: `feat: add matching driver search`

Branch: `feature/matching-driver-search`

Phase: Phase 4 - Matching

### Muc tieu

Tao nen tang matching MVP de tim tai xe gan diem don nhat tu Redis GEO, loc tai xe phu hop, lock candidate truoc khi gui offer, va ghi trang thai matching cua trip vao Redis.

Commit nay chua gui WebSocket offer va chua xu ly driver accept/reject. Cac phan do se duoc tach commit rieng de review ro rang.

### Noi dung da trien khai

- Them model matching:
  - `DriverCandidate`: thong tin candidate driver, khoang cach, loai xe, rating, ten, avatar.
  - `DriverOffer`: ket qua lock thanh cong de dung cho buoc gui offer sau.
  - `MatchingRequest`: request matching tu `BookingCreatedEvent`, mac dinh ban kinh 5km va toi da 3 candidate.
- Them strategy:
  - `DriverMatchingStrategy`: interface sap xep candidate.
  - `NearestDriverStrategy`: sap xep theo `distanceMeters` tang dan, tie-break bang `driverId`.
- Them service:
  - `MatchingService.findAndLockDriver(...)`: lay candidate, rank, thu lock tung driver, ghi matching state khi lock thanh cong.
- Them Redis store:
  - Query `drivers:online` bang Redis GEO.
  - Chi lay driver co `driver:{id}:status = AVAILABLE`.
  - Loc dung `vehicleType` tu `driver:{id}:meta`.
  - Lock bang `SET driver:{id}:lock <tripId> NX EX 30`.
  - Ghi `trip:{id}:matching` voi `attempt`, `offeredDriverId`, `offerExpiresAt` va TTL 5 phut.

### Review truoc commit

- Da sua logic `attempt`: candidate bi lock fail khong lam tang attempt, vi driver do chua that su duoc offer.
- Da them test cho Redis candidate store de bao phu:
  - Loc `AVAILABLE`.
  - Loc dung `vehicleType`.
  - Lock candidate bang Redis SET NX co TTL.
  - Ghi hash `trip:{id}:matching`.
- Da chay `./mvnw.cmd test`: pass 53 tests.

### Files chinh

- `src/main/java/com/example/goride/matching/domain/*`
- `src/main/java/com/example/goride/matching/service/*`
- `src/test/java/com/example/goride/matching/service/*`

### Viec tiep theo

- Lang nghe `BookingCreatedEvent` va kich hoat matching.
- Gui offer den driver qua WebSocket user queue.
- Them API driver accept/reject offer.
- Xu ly timeout va retry driver tiep theo.
