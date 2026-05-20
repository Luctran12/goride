# GoRide Implementation Log

> Muc dich: ghi lai noi dung trien khai theo tung commit de review nhanh va giu trace giua code voi phase trong `docs/backend-implementation.md`.
>
> Tu commit `feat: add matching driver search` tro di, moi commit backend can cap nhat file nay trong cung commit.

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
