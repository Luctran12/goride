# GoRide Implementation Log

> Muc dich: ghi lai noi dung trien khai theo tung commit de review nhanh va giu trace giua code voi phase trong `docs/backend-implementation.md`.
>
> Tu commit `feat: add matching driver search` tro di, moi commit backend can cap nhat file nay trong cung commit.

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

- Da xac nhan increment chi nam trong nhánh `COMPLETED`, sau khi domain transition hop le.
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
