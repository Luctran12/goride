# GoRide Front-end Integration Plan

Branch da kiem tra: `feature/payment-webhook-foundation`

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
- `POST /api/v1/payments/providers/{providerName}/webhook`
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

### User profile va admin user management

- [x] User xem profile cua minh.
- [x] User cap nhat profile cua minh.
- [x] User doi mat khau.
- [x] Admin tao/xem/list/cap nhat/xoa user.
- [x] Admin suspend/activate user qua `status`.

### Booking va trip

- [x] Public API xem pricing config active.
- [x] Admin API xem/tao pricing config version moi.
- [x] Admin API deactivate pricing config cu.
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
- [x] Khi completed, backend tang `driver_profiles.total_trips` cua assigned driver.
- [x] Broadcast trip status qua WebSocket topic.

### Tracking realtime

- [x] Driver gui location qua STOMP `/app/driver.location`.
- [x] Backend luu location history.
- [x] Backend luu latest location vao Redis.
- [x] Backend broadcast driver location qua `/topic/trip/{tripId}/location`.
- [x] Passenger lay latest driver location qua REST.

### Payment cash

- [x] Tao payment record khi trip completed.
- [x] Payment record duoc tao qua `PaymentProvider` abstraction.
- [x] Co `CashPaymentProvider` cho payment method `CASH`.
- [x] Co checkout foundation endpoint cho payment `PENDING`.
- [x] Co webhook foundation endpoint cho payment provider external callback.
- [x] Driver confirm da nhan tien mat.
- [x] Payment `PENDING -> COMPLETED`, set `paidAt`.
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
- [x] Backend tu xoa FCM token Redis khi Firebase bao token `UNREGISTERED`.
- [x] Notification ca nhan duoc luu DB de lam inbox trong app.
- [x] User xem danh sach notification inbox va mark read.
- [x] STOMP `CONNECT` authenticate bang JWT trong header `Authorization`.
- [x] STOMP `SUBSCRIBE` vao trip topic chi cho passenger/driver cua trip hoac admin.
- [x] Driver offer queue: `/user/queue/trip-requests`.
- [x] User notification queue: `/user/queue/notifications`.
- [x] Trip status topic: `/topic/trip/{tripId}/status`.
- [x] Trip location topic: `/topic/trip/{tripId}/location`.

---

## 3. Checklist chuc nang chua hoan thien / can lam tiep

### Payment/rating/statistics

- [ ] Payment chi ho tro `CASH`.
- [ ] Chua co provider MoMo/VNPay implementation; webhook foundation da co endpoint nhung chua verify signature/provider payload that.

### Notification/mo rong

- [ ] Chua co deploy secret/env production cho Firebase service account path.

### Admin module

- [x] Admin list/create/update/delete users.
- [x] Admin suspend/activate user.
- [x] Admin pricing list/create/deactivate.
- [x] Admin list pending drivers.
- [x] Admin approve/reject driver.
- [x] Admin list trips/filter.
- [x] Admin stats/dashboard.

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
Backend da co provider abstraction noi bo; hien tai provider runtime duy nhat la `CashPaymentProvider`, nen FE van chi gui `paymentMethod = "CASH"`.

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
- Voi `CASH`, `checkoutRequired=false`, FE hien man hinh thanh toan tien mat va cho driver confirm.
- Khi sau nay co MoMo/VNPay, neu `checkoutRequired=true`, FE mo `checkoutUrl` va theo doi payment status/webhook flow.
- Endpoint chi hop le cho payment `PENDING`; neu payment da completed backend tra `PAYMENT_INVALID_STATUS`, FE nen refresh payment detail.

#### Provider webhook callback

```http
POST /api/v1/payments/providers/{providerName}/webhook
Content-Type: application/json
```

Request body: raw JSON callback cua provider.

Response `data`:

```json
{
  "provider": "momo",
  "accepted": true,
  "paymentId": 70,
  "tripId": 99,
  "status": "COMPLETED",
  "transactionRef": "provider-transaction-ref",
  "message": "processed"
}
```

FE action:
- FE app khong goi endpoint nay truc tiep; day la endpoint public de MoMo/VNPay callback vao backend.
- Khi tich hop provider that, FE chi mo `checkoutUrl` neu checkout response bao `checkoutRequired=true`, sau do theo doi payment detail/notification.
- Hien tai `CASH` khong support webhook; goi `/providers/cash/webhook` se tra `PAYMENT_PROVIDER_UNSUPPORTED`.
- Moi provider sau nay phai tu verify signature/header/payload trong `PaymentProvider.handleWebhook(...)`.

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

Response `data`: `DriverProfileResponse`.

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
- Backend da co FCM channel va Firebase Admin SDK sender. Khi backend bat `app.notifications.fcm.enabled=true` va cau hinh service account path, notification ca nhan se duoc gui them qua FCM bang token da luu.
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
- Neu Firebase tra `UNREGISTERED`, backend xoa token trong Redis va khong retry token do.
- Cac loi transient khac duoc retry/suppress; backend khong xoa token voi cac loi nay.
- Firebase Admin SDK lazy-init khi gui push lan dau. Neu thieu/sai service account path, loi push duoc log/suppress trong FCM channel; WebSocket va inbox van chay binh thuong.
- Cau hinh backend can co de gui push that:
  - `app.notifications.fcm.enabled=true`
  - `app.notifications.fcm.service-account-path=/path/to/firebase-service-account.json`
  - `app.notifications.fcm.project-id=<firebase-project-id>` neu service account khong tu suy ra project.
  - `app.notifications.fcm.app-name=goride` neu can doi app name mac dinh.

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

Luu y: HTTP handshake toi `/ws` duoc mo de client ket noi WebSocket. Backend authenticate o STOMP `CONNECT`; neu thieu hoac sai `Authorization: Bearer <accessToken>`, connection frame bi tu choi. Sau khi connect thanh cong, backend gan `Principal`/roles tu JWT cho message mapping nhu `/app/driver.location`.

Subscribe vao `/topic/trip/{tripId}/status` va `/topic/trip/{tripId}/location` chi thanh cong neu JWT user la passenger cua trip, driver cua trip, hoac admin. Neu FE subscribe nham trip, backend reject frame voi `FORBIDDEN`/access denied o WebSocket layer.

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
- [ ] FCM token registration sau login/refresh token.
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
- [ ] FCM token registration sau login/refresh token.
- [ ] Profile onboarding: tao profile bang `POST /api/v1/drivers/me/profile`.
- [ ] Approval waiting screen: doc `approvalStatus` tu `GET /api/v1/drivers/me/profile`; chi cho online khi status la `APPROVED`.
- [ ] Online toggle with current GPS.
- [ ] Offer modal from `/user/queue/trip-requests`.
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
12. Cash payment confirm.
13. Rating create + public rating list.
14. WebSocket auth production: gui token trong STOMP `CONNECT`, test reconnect khi access token het han.
