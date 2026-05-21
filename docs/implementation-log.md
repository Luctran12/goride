# GoRide Implementation Log

> Muc dich: ghi lai noi dung trien khai theo tung commit de review nhanh va giu trace giua code voi phase trong `docs/backend-implementation.md`.
>
> Tu commit `feat: add matching driver search` tro di, moi commit backend can cap nhat file nay trong cung commit.

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
