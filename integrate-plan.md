# GoRide Front-end Integration Plan

Branch da kiem tra: `feature/driver-rating-list-api`

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
  "timestamp": "2026-05-27T10:00:00Z"
}
```

FE nen map `error.code` thay vi chi doc text message.

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
- Swagger/OpenAPI routes

### Enum FE can dong bo

```ts
type UserRole = "PASSENGER" | "DRIVER" | "ADMIN";
type VehicleType = "MOTORBIKE" | "CAR_4_SEAT" | "CAR_7_SEAT";
type PaymentMethod = "CASH";
type TripStatus =
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

### Booking va trip

- [x] Passenger tinh gia uoc luong.
- [x] Passenger tao booking.
- [x] Passenger/driver xem chi tiet trip neu co quyen.
- [x] Passenger/driver xem danh sach trip cua minh.
- [x] Passenger/driver/admin huy trip neu status con cancel duoc.
- [x] Tao trip history khi tao/huy/doi status.

### Matching

- [x] Sau khi booking created, backend tu dong tim driver gan nhat trong Redis.
- [x] Gui offer toi driver qua WebSocket user queue.
- [x] Driver accept/reject offer.
- [x] Driver accept thi trip chuyen `SEARCHING -> ACCEPTED`, driver status Redis thanh `BUSY`.
- [x] Driver reject/timeout thi backend thu driver tiep theo toi da 3 lan.
- [x] Het driver thi trip chuyen `NO_DRIVER` va notify passenger.

### Trip status

- [x] Driver cap nhat `ACCEPTED -> ARRIVED`.
- [x] Driver cap nhat `ARRIVED -> IN_PROGRESS`.
- [x] Driver cap nhat `IN_PROGRESS -> COMPLETED`.
- [x] Khi completed, backend tinh actual distance/duration tu tracking history va tinh `finalFare`.
- [x] Khi completed, backend tao payment `PENDING`.
- [x] Broadcast trip status qua WebSocket topic.

### Tracking realtime

- [x] Driver gui location qua STOMP `/app/driver.location`.
- [x] Backend luu location history.
- [x] Backend luu latest location vao Redis.
- [x] Backend broadcast driver location qua `/topic/trip/{tripId}/location`.
- [x] Passenger lay latest driver location qua REST.

### Payment cash

- [x] Tao payment record khi trip completed.
- [x] Driver confirm da nhan tien mat.
- [x] Payment `PENDING -> COMPLETED`, set `paidAt`.
- [x] Notify passenger va driver khi payment completed.
- [x] Set Redis driver status ve `AVAILABLE` sau payment completed.

### Rating

- [x] Passenger rating driver sau trip completed.
- [x] Moi trip chi duoc rating mot lan.
- [x] Chi passenger cua trip moi rating duoc.
- [x] Cap nhat `driver_profiles.average_rating` va `total_ratings`.
- [x] Public API xem rating cua driver co pagination.

### WebSocket notifications

- [x] Driver offer queue: `/user/queue/trip-requests`.
- [x] User notification queue: `/user/queue/notifications`.
- [x] Trip status topic: `/topic/trip/{tripId}/status`.
- [x] Trip location topic: `/topic/trip/{tripId}/location`.

---

## 3. Checklist chuc nang chua hoan thien / can lam tiep

### High priority cho FE integration

- [ ] WebSocket JWT authentication chua thay interceptor xu ly STOMP `CONNECT`.
  - FE van nen thiet ke gui token trong connect headers.
  - Backend can commit them channel interceptor de gan `Principal`/roles cho STOMP message.
- [ ] Admin approval driver chua co REST API.
  - Driver online yeu cau profile `APPROVED`, nhung hien chua co endpoint admin approve/reject.
  - Tam thoi can seed/update DB thu cong trong moi truong dev.
- [ ] API pricing public/admin chua co.
  - Backend co seeder pricing config va dung noi bo de tinh fare.
  - FE chua co endpoint de hien thi bang gia.
- [ ] API profile passenger/user chua co.
  - Auth response chi tra `userId`, token, roles.
  - Chua co API lay/cap nhat fullName/avatar/email cua user.

### Payment/rating/statistics

- [ ] Payment chi ho tro `CASH`.
- [ ] Chua co API xem payment detail/history theo trip.
- [ ] Chua co provider MoMo/VNPay.
- [ ] Chua co API kiem tra trip da rating hay chua; FE co the suy luan tu response loi `TRIP_ALREADY_RATED` khi submit.
- [ ] Sau khi rating, Redis `driver:{id}:meta.rating` chua duoc sync ngay neu driver dang online.
  - Rating moi se vao PostgreSQL, matching Redis co the cap nhat khi driver online lai.
- [ ] `driver_profiles.total_trips` co method domain nhung chua thay service goi cap nhat khi trip completed.
- [ ] Admin/statistics doanh thu/rating trung binh chua co API.

### Notification/mo rong

- [ ] Chua co FCM device token API.
- [ ] Chua co mobile push notification; hien tai moi co WebSocket.
- [ ] Chua co persistence cho notification inbox.

### Admin module

- [ ] Admin list users.
- [ ] Admin suspend/activate user.
- [ ] Admin list pending drivers.
- [ ] Admin approve/reject driver.
- [ ] Admin list trips/filter.
- [ ] Admin stats/dashboard.

---

## 4. Huong dan FE tich hop tung chuc nang

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
  "portraitUrl": "https://cdn.example.com/driver.jpg",
  "vehiclePlate": "59A1-12345",
  "vehicleType": "MOTORBIKE",
  "vehicleBrand": "Honda",
  "vehicleModel": "Wave",
  "vehicleColor": "Black",
  "vehicleYear": 2022
}
```

Response `data`: `DriverProfileResponse`.

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
- Goi lai API hoac gui heartbeat tu app de Redis TTL khong het. Backend hien co endpoint online/offline, chua co heartbeat REST rieng.
- Neu loi `DRIVER_NOT_APPROVED`, hien thong bao "Ho so chua duoc duyet".

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
  "paymentMethod": "CASH"
}
```

Response `data`: `TripResponse`, status ban dau `SEARCHING`.

FE action:
- Sau create thanh cong, chuyen sang man tim driver.
- Subscribe `/topic/trip/{tripId}/status` va `/user/queue/notifications`.
- Neu `PASSENGER_HAS_ACTIVE_TRIP`, mo trip dang active thay vi tao trip moi.

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
- Chi hien nut huy khi status `SEARCHING`, `ACCEPTED`, `ARRIVED`.
- Neu backend tra `TRIP_CANNOT_BE_CANCELLED`, refresh trip detail.

---

### 4.4 Matching va driver offer

Matching chay tu dong sau khi passenger tao booking. FE khong co endpoint "start matching" rieng.

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
- Neu accept thanh cong, mo man trip driver.
- Neu reject thanh cong, dong modal offer.
- Neu `MATCHING_OFFER_EXPIRED`, dong offer va doi offer moi.

Passenger can subscribe:

```text
/topic/trip/{tripId}/status
/user/queue/notifications
```

Khi driver accept, passenger nhan `TRIP_ACCEPTED` notification va topic status `ACCEPTED`.

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

Backend se tim trip `IN_PROGRESS` moi nhat cua driver. Khong can gui `tripId` trong payload.

FE action:
- Chi gui location khi trip status `IN_PROGRESS`.
- Tan suat goi y: 3-5 giay/lan voi mobile MVP.
- Neu backend tra/emit loi `TRIP_NOT_FOUND`, dung tracking vi driver chua co trip in-progress.

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

---

### 4.7 Payment cash

Payment record duoc tao khi driver complete trip. FE khong co endpoint create payment rieng.

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

## 5. WebSocket integration

### Ket noi

Endpoint:

```text
ws://localhost:8080/ws
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

Can luu y: backend hien chua co interceptor doc JWT tu STOMP `CONNECT`, nen day la contract FE nen chuan bi, con backend can commit rieng de hoan thien auth WebSocket.

### Destinations can subscribe

| Man hinh | Destination | Payload |
|---|---|---|
| Driver online/offer modal | `/user/queue/trip-requests` | `DriverOfferNotification` |
| Passenger/driver app shell | `/user/queue/notifications` | `UserNotification` |
| Trip detail | `/topic/trip/{tripId}/status` | `TripStatusNotification` |
| Passenger tracking | `/topic/trip/{tripId}/location` | `DriverLocationResponse` |

### Messages FE gui len backend

| Flow | Destination | Body |
|---|---|---|
| Driver location tracking | `/app/driver.location` | `DriverLocationUpdateRequest` |

---

## 6. Man hinh FE goi y theo flow

### Passenger app

- [ ] Auth screen: register/login/refresh/logout.
- [ ] Home map: chon pickup/dropoff/vehicleType, goi estimate.
- [ ] Booking confirm: goi create booking.
- [ ] Finding driver: subscribe trip status + notifications.
- [ ] Active trip:
  - `ACCEPTED`: hien driver dang den.
  - `ARRIVED`: hien driver da den.
  - `IN_PROGRESS`: hien map tracking, subscribe location.
  - `COMPLETED`: hien final fare.
- [ ] Payment done screen: doi `PAYMENT_COMPLETED`.
- [ ] Rating screen: post rating.
- [ ] Trip history: list bookings.

### Driver app

- [ ] Auth screen.
- [ ] Profile onboarding.
- [ ] Approval waiting screen.
- [ ] Online toggle with current GPS.
- [ ] Offer modal from `/user/queue/trip-requests`.
- [ ] Trip workflow buttons: arrived/start/complete.
- [ ] Location sender while `IN_PROGRESS`.
- [ ] Cash confirmation screen.
- [ ] Driver trip history: list bookings.

### Public/driver profile

- [ ] Public rating list: `GET /api/v1/drivers/{driverId}/ratings`.
- [ ] Display `averageRating` tu driver profile neu FE co endpoint lay profile tu app driver; public driver profile API rieng chua co.

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
| `TRIP_ALREADY_RATED` | An rating form, coi trip da danh gia. |
| `DRIVER_LOCATION_NOT_FOUND` | Hien "Dang cho vi tri tai xe". |

---

## 8. Thu tu tich hop de giam rui ro

1. Auth REST + token refresh.
2. Passenger estimate/create booking/list booking.
3. Driver profile + online/offline.
4. WebSocket subscribe driver offer va passenger trip status.
5. Driver accept/reject offer.
6. Trip status buttons.
7. Tracking realtime.
8. Cash payment confirm.
9. Rating create + public rating list.
10. Sau khi backend co admin approval API va STOMP JWT interceptor, noi tiep admin app va WebSocket auth production.
