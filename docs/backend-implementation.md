# GoRide Backend Implementation Spec

> Muc dich: tai lieu nay la ban dac ta de trien khai backend Spring Boot cho du an GoRide. No thay cho ban thiet ke tong quat khi bat dau code backend.
>
> Pham vi MVP: Passenger dat xe, driver nhan/tu choi chuyen, matching gan nhat, tracking realtime, thanh toan tien mat, rating, admin toi thieu.

---

## 1. Quyet Dinh Kien Truc

### 1.1 Backend style

Backend la mot ung dung **Spring Boot Modular Monolith**.

Ly do:
- Team nho, thoi gian ngan, can toc do trien khai.
- Van giu ranh gioi module ro rang de co the tach service sau nay.
- Mot database chinh giup transaction va debug don gian hon trong giai doan MVP.

### 1.2 Root package theo repo hien tai

Repo hien tai dang dung:

```text
com.example.goride
```

Khi trien khai, dung package nay de tranh refactor som. Neu muon doi sang `com.goride`, nen doi truoc khi bat dau tao nhieu entity/service.

### 1.3 Module ownership

```text
com.example.goride
|-- common
|   |-- api
|   |-- config
|   |-- error
|   |-- security
|   |-- util
|   `-- validation
|-- auth
|-- user
|-- driver
|-- booking
|-- matching
|-- tracking
|-- payment
|-- rating
|-- notification
`-- admin
```

Quy tac:
- Controller chi goi service cua module minh.
- Service goi repository cua module minh.
- Cross-module write flow di qua Spring events.
- Cross-module read flow chi di qua public port/interface trong package `*.api`, khong inject truc tiep service noi bo cua module khac.
- Shared DTO/error/security nam trong `common`.

### 1.4 Data ownership

| Loai du lieu | Noi luu | Ghi chu |
|---|---|---|
| User, driver profile, trips, payments, ratings | PostgreSQL | Source of truth |
| Toa do pickup/dropoff, last known location | PostgreSQL + PostGIS | Luu du lieu ben vung |
| Driver online realtime | Redis GEO | Source chinh cho matching gan nhat |
| Driver status realtime | Redis key TTL | AVAILABLE/BUSY/OFFLINE |
| Vi tri moi nhat trong trip | Redis | REST fallback va broadcast WS |
| Lich su vi tri trong trip | PostgreSQL | Luu lay mau moi 10-15 giay/lan, khong nhat thiet moi GPS point |
| Refresh token | Redis | TTL 7 ngay |

Quyet dinh quan trong: **Matching driver online dung Redis GEO, khong dung PostGIS query tren bang `driver_profiles`**. PostGIS chi dung de luu toa do ben vung, validate/reporting, va last known location khi driver offline.

---

## 2. Thu Tu Trien Khai Backend

### Phase 0 - Project foundation

1. Doi parent Spring Boot SNAPSHOT sang ban stable 3.x truoc khi code nhieu.
2. Them dependencies:
   - `spring-boot-starter-security`
   - `spring-boot-starter-websocket`
   - `spring-boot-starter-oauth2-resource-server` hoac thu vien JWT rieng
   - `hibernate-spatial`
   - `org.locationtech.jts:jts-core`
   - `testcontainers`, `postgresql`, `junit-jupiter`
3. Them Docker Compose cho PostgreSQL/PostGIS va Redis.
4. Cau hinh profile `local`.
5. Tao response envelope, global exception handler, validation error format.

### Phase 1 - Auth + User

1. Tao entity/schema cho `users`, `user_roles`.
2. Register/login/refresh/logout.
3. JWT access token 15 phut, refresh token 7 ngay trong Redis.
4. Spring Security role guard.
5. Unit test password hashing, JWT, refresh token.

### Phase 2 - Driver profile

1. Tao entity/schema cho `driver_profiles`.
2. Driver submit profile.
3. Admin approve/reject driver.
4. Driver toggle online/offline.
5. Redis keys cho online driver.

### Phase 3 - Pricing + Booking

1. Tao entity/schema cho `pricing_config`, `trips`, `trip_status_history`.
2. API estimate fare.
3. API create trip.
4. State machine cho trip.
5. Publish `BookingCreatedEvent` sau khi commit transaction.

### Phase 4 - Matching

1. Redis GEO query driver gan nhat.
2. Lock candidate driver de tranh 2 trip lay cung driver.
3. Gui offer den driver qua WebSocket user queue.
4. Driver accept/reject.
5. Timeout va retry toi da 3 driver.

### Phase 5 - Tracking realtime

1. STOMP WebSocket endpoint `/ws`.
2. JWT auth trong STOMP `CONNECT`.
3. Driver gui location.
4. Server update Redis va broadcast den passenger cua trip.
5. REST fallback lay driver location moi nhat.

### Phase 6 - Payment + Rating

1. Cash payment provider.
2. Tao payment khi trip completed.
3. Passenger rating driver.
4. Cap nhat `driver_profiles.average_rating` va `total_ratings`.

### Phase 7 - Admin minimum

1. User list/filter.
2. Driver approval.
3. Trip list/filter.
4. Basic stats.

---

## 3. Database Schema

Giai doan nay khong dung Flyway. Schema duoc tao va cap nhat chu yeu bang Hibernate/JPA trong moi truong local.

Quy uoc lam viec:
- `spring.jpa.hibernate.ddl-auto=update` chi dung cho local de di nhanh.
- Staging/production khong dua vao Hibernate `update`; moi thay doi schema can co release folder trong `db/releases/YYYYMMDD-short-name`.
- Moi release folder gom `manifest.yml`, `precheck.sql`, `apply.sql`, `verify.sql`, `rollback.sql` va phai pass `scripts/validate-db-release.ps1` truoc review/deploy.
- Khi doi schema lon hoac doi constraint, uu tien reset local DB thay vi tin rang `update` se sua moi thay doi phuc tap dung y.
- Cac doan SQL ben duoi la schema tham chieu de doi chieu voi entity va de sinh release SQL thu cong khi can.

### 3.1 Extensions

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
```

### 3.2 users

Khong dat `UNIQUE` truc tiep tren `phone`/`email` neu dung soft delete. Dung partial unique index de co the tai su dung sau khi soft delete neu can.

```sql
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    full_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    email           VARCHAR(120),
    password_hash   VARCHAR(255) NOT NULL,
    avatar_url      VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP
);

CREATE UNIQUE INDEX uk_users_phone_active
    ON users(phone)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_users_email_active
    ON users(email)
    WHERE email IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_users_status_active
    ON users(status)
    WHERE deleted_at IS NULL;
```

### 3.3 user_roles

Mot tai khoan co the vua la passenger vua la driver. Admin la role rieng.

```sql
CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role    VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE INDEX idx_user_roles_role ON user_roles(role);
```

Enum:

```text
PASSENGER, DRIVER, ADMIN
```

### 3.4 driver_profiles

```sql
CREATE TABLE driver_profiles (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    license_number      VARCHAR(30)  NOT NULL UNIQUE,
    license_expiry      DATE         NOT NULL,
    id_card_number      VARCHAR(20)  NOT NULL UNIQUE,
    portrait_url        VARCHAR(500) NOT NULL,
    license_image_url   VARCHAR(500),
    id_card_image_url   VARCHAR(500),
    vehicle_registration_url VARCHAR(500),
    vehicle_plate       VARCHAR(30)  NOT NULL UNIQUE,
    vehicle_type        VARCHAR(20)  NOT NULL,
    vehicle_brand       VARCHAR(50),
    vehicle_model       VARCHAR(50),
    vehicle_color       VARCHAR(30),
    vehicle_year        SMALLINT,
    approval_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    is_online           BOOLEAN      NOT NULL DEFAULT FALSE,
    average_rating      NUMERIC(2,1) NOT NULL DEFAULT 5.0,
    total_ratings       INTEGER      NOT NULL DEFAULT 0,
    total_trips         INTEGER      NOT NULL DEFAULT 0,
    last_known_location GEOMETRY(Point, 4326),
    last_location_at    TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_driver_profiles_approval
    ON driver_profiles(approval_status);

CREATE INDEX idx_driver_profiles_last_location
    ON driver_profiles USING GIST(last_known_location);
```

Enum:

```text
ApprovalStatus: PENDING, APPROVED, REJECTED
VehicleType: MOTORBIKE, CAR_4_SEAT, CAR_7_SEAT
```

### 3.5 pricing_config

```sql
CREATE TABLE pricing_config (
    id                BIGSERIAL PRIMARY KEY,
    vehicle_type      VARCHAR(20)   NOT NULL,
    base_fare         NUMERIC(10,0) NOT NULL,
    per_km_rate       NUMERIC(8,0)  NOT NULL,
    per_minute_rate   NUMERIC(6,0)  NOT NULL DEFAULT 0,
    minimum_fare      NUMERIC(10,0) NOT NULL,
    surge_multiplier  NUMERIC(3,1)  NOT NULL DEFAULT 1.0,
    is_active         BOOLEAN       NOT NULL DEFAULT TRUE,
    effective_from    TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pricing_active_vehicle
    ON pricing_config(vehicle_type, is_active, effective_from DESC);
```

Seed:

```sql
INSERT INTO pricing_config
    (vehicle_type, base_fare, per_km_rate, per_minute_rate, minimum_fare)
VALUES
    ('MOTORBIKE',  10000, 4000, 300, 15000),
    ('CAR_4_SEAT', 15000, 8000, 500, 25000),
    ('CAR_7_SEAT', 20000, 9000, 600, 30000);
```

Formula:

```text
fare = base_fare + distance_km * per_km_rate + duration_min * per_minute_rate
fare = max(fare, minimum_fare)
fare = round(fare * surge_multiplier)
```

Server phai tu tinh gia. Khong tin `estimatedFare` tu client khi tao trip.

### 3.6 trips

```sql
CREATE TABLE trips (
    id                     BIGSERIAL PRIMARY KEY,
    passenger_id           BIGINT       NOT NULL REFERENCES users(id),
    driver_id              BIGINT       REFERENCES users(id),
    status                 VARCHAR(20)  NOT NULL DEFAULT 'SEARCHING',
    vehicle_type           VARCHAR(20)  NOT NULL,
    payment_method         VARCHAR(20)  NOT NULL DEFAULT 'CASH',

    pickup_address         VARCHAR(300) NOT NULL,
    pickup_location        GEOMETRY(Point, 4326) NOT NULL,
    dropoff_address        VARCHAR(300) NOT NULL,
    dropoff_location       GEOMETRY(Point, 4326) NOT NULL,

    estimated_distance_km  NUMERIC(8,2)  NOT NULL,
    estimated_duration_min INTEGER       NOT NULL,
    actual_distance_km     NUMERIC(8,2),
    actual_duration_min    INTEGER,
    estimated_fare         NUMERIC(10,0) NOT NULL,
    final_fare             NUMERIC(10,0),
    pricing_config_id      BIGINT        NOT NULL REFERENCES pricing_config(id),

    requested_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    accepted_at            TIMESTAMP,
    arrived_at             TIMESTAMP,
    started_at             TIMESTAMP,
    completed_at           TIMESTAMP,
    cancelled_at           TIMESTAMP,
    cancel_reason          VARCHAR(200),

    created_at             TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP     NOT NULL DEFAULT NOW(),
    deleted_at             TIMESTAMP
);

CREATE INDEX idx_trips_pickup_location
    ON trips USING GIST(pickup_location);

CREATE INDEX idx_trips_dropoff_location
    ON trips USING GIST(dropoff_location);

CREATE INDEX idx_trips_status_active
    ON trips(status)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_trips_passenger_active
    ON trips(passenger_id, requested_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_trips_driver_active
    ON trips(driver_id, requested_at DESC)
    WHERE deleted_at IS NULL;
```

Enum:

```text
SEARCHING, ACCEPTED, ARRIVED, IN_PROGRESS, COMPLETED, CANCELLED, NO_DRIVER
```

### 3.7 trip_status_history

```sql
CREATE TABLE trip_status_history (
    id          BIGSERIAL PRIMARY KEY,
    trip_id     BIGINT      NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    changed_by  BIGINT      REFERENCES users(id),
    note        VARCHAR(200),
    changed_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trip_status_history_trip
    ON trip_status_history(trip_id, changed_at ASC);
```

Chi insert, khong update.

### 3.8 trip_location_history

Bang nay bi thieu trong tai lieu goc, nhung can neu muon luu lo trinh.

```sql
CREATE TABLE trip_location_history (
    id         BIGSERIAL PRIMARY KEY,
    trip_id    BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    driver_id  BIGINT NOT NULL REFERENCES users(id),
    location   GEOMETRY(Point, 4326) NOT NULL,
    bearing    NUMERIC(5,2),
    speed      NUMERIC(6,2),
    recorded_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trip_location_history_trip_time
    ON trip_location_history(trip_id, recorded_at ASC);

CREATE INDEX idx_trip_location_history_location
    ON trip_location_history USING GIST(location);
```

Ghi lay mau moi 10-15 giay, khong can luu moi packet GPS 3 giay neu demo.

### 3.9 payments

```sql
CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    trip_id         BIGINT        NOT NULL UNIQUE REFERENCES trips(id) ON DELETE CASCADE,
    amount          NUMERIC(10,0) NOT NULL,
    method          VARCHAR(20)   NOT NULL DEFAULT 'CASH',
    provider        VARCHAR(30),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    transaction_ref VARCHAR(100),
    paid_at         TIMESTAMP,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_status ON payments(status);
```

MVP cash:
- Khi trip `COMPLETED`, tao payment.
- Neu `method = CASH`, set `status = COMPLETED`, `paid_at = NOW()`.

### 3.10 ratings

```sql
CREATE TABLE ratings (
    id           BIGSERIAL PRIMARY KEY,
    trip_id      BIGINT       NOT NULL UNIQUE REFERENCES trips(id) ON DELETE CASCADE,
    passenger_id BIGINT       NOT NULL REFERENCES users(id),
    driver_id    BIGINT       NOT NULL REFERENCES users(id),
    score        SMALLINT     NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment      VARCHAR(500),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ratings_driver_created
    ON ratings(driver_id, created_at DESC);
```

Rule:
- Chi passenger cua trip moi duoc rating.
- Chi rating khi trip da `COMPLETED`.
- Moi trip chi co 1 rating trong MVP.

---

## 4. Redis Schema

### 4.1 Keys

| Key | Type | TTL | Noi dung |
|---|---|---|---|
| `drivers:online` | GEO sorted set | none | member = driverId, score = geo |
| `driver:{id}:status` | string | 60s | `AVAILABLE`, `BUSY` |
| `driver:{id}:meta` | hash | 60s | `vehicleType`, `rating`, `name`, `avatarUrl` |
| `driver:{id}:trip` | string | 24h | trip hien tai neu `BUSY` |
| `driver:{id}:lock` | string | 30s | lock khi offer/matching |
| `refresh_token:{userId}:{tokenId}` | string | 7d | refresh token hash hoac token id |
| `trip:{id}:matching` | hash | 5m | `attempt`, `offeredDriverId`, `offerExpiresAt` |
| `trip:{id}:driver-location` | json/string | 60s | vi tri moi nhat cua driver |

### 4.2 Driver online lifecycle

```text
Driver bat online
  -> validate role DRIVER va approval_status APPROVED
  -> UPDATE driver_profiles.is_online = true
  -> GEOADD drivers:online <lng> <lat> <driverId>
  -> SET driver:{id}:status AVAILABLE EX 60
  -> HSET driver:{id}:meta ...

Driver gui heartbeat/location
  -> SET driver:{id}:status AVAILABLE|BUSY EX 60
  -> EXPIRE driver:{id}:meta 60
  -> neu AVAILABLE: GEOADD drivers:online ...
  -> neu BUSY: update trip location key va broadcast

Driver tat online
  -> UPDATE driver_profiles.is_online = false
  -> ZREM drivers:online <driverId>
  -> DEL driver:{id}:status driver:{id}:meta
  -> luu last_known_location vao PostgreSQL neu co
```

### 4.3 Race condition rule

Truoc khi offer trip cho driver:

```text
SET driver:{id}:lock <tripId> NX EX 30
```

Neu fail, bo qua driver do. Khi driver reject/timeout, xoa lock va set status lai `AVAILABLE`. Khi accept, set `driver:{id}:status = BUSY`, `driver:{id}:trip = tripId`.

---

## 5. REST API

### 5.1 Common response

Success:

```json
{
  "success": true,
  "data": {},
  "message": "OK",
  "timestamp": "2026-05-16T10:30:00Z"
}
```

Error:

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request is invalid",
    "details": {}
  },
  "timestamp": "2026-05-16T10:30:00Z"
}
```

Pagination:

```text
page bat dau tu 1
size mac dinh 20, toi da 100
sort mac dinh createdAt
direction desc
```

### 5.2 Auth APIs

| Method | Path | Role | Ghi chu |
|---|---|---|---|
| POST | `/api/v1/auth/register` | PUBLIC | Tao user + roles |
| POST | `/api/v1/auth/login` | PUBLIC | Tra access/refresh token |
| POST | `/api/v1/auth/refresh` | PUBLIC | Rotate refresh token |
| POST | `/api/v1/auth/logout` | AUTHENTICATED | Xoa refresh token trong Redis |

Register request:

```json
{
  "fullName": "Nguyen Van A",
  "phone": "0901234567",
  "email": "a@email.com",
  "password": "password123",
  "roles": ["PASSENGER"]
}
```

Khong cho public tao `ADMIN`.

### 5.3 Driver APIs

| Method | Path | Role | Ghi chu |
|---|---|---|---|
| POST | `/api/v1/drivers/me/profile` | DRIVER | multipart, tao ho so driver |
| GET | `/api/v1/drivers/me/profile` | DRIVER | Xem ho so |
| PATCH | `/api/v1/drivers/me/status` | DRIVER | Bat/tat online |
| PATCH | `/api/v1/drivers/trips/{tripId}/respond` | DRIVER | ACCEPT/REJECT offer hien tai |
| PATCH | `/api/v1/drivers/trips/{tripId}/status` | DRIVER | ARRIVED/IN_PROGRESS/COMPLETED |
| GET | `/api/v1/drivers/me/trips` | DRIVER | Lich su + thu nhap |
| GET | `/api/v1/drivers/{driverId}/ratings` | PUBLIC | Rating cong khai |

Respond request:

```json
{
  "action": "ACCEPT"
}
```

Server phai validate:
- Driver nay dung la `offeredDriverId` trong `trip:{id}:matching`.
- Offer chua het han.
- Trip van la `SEARCHING`.
- Driver chua `BUSY` voi trip khac.

### 5.4 Booking APIs

| Method | Path | Role | Ghi chu |
|---|---|---|---|
| POST | `/api/v1/bookings/estimate` | PASSENGER | Server tinh gia uoc tinh |
| POST | `/api/v1/bookings` | PASSENGER | Tao trip, server tinh lai gia |
| GET | `/api/v1/bookings/{tripId}` | OWNER/ADMIN | Chi passenger/driver cua trip |
| GET | `/api/v1/bookings` | PASSENGER/DRIVER | Lich su cua minh |
| PATCH | `/api/v1/bookings/{tripId}/cancel` | OWNER | Huy khi status cho phep |

Create booking request:

```json
{
  "pickup": {
    "lat": 10.7769,
    "lng": 106.7009,
    "address": "123 Le Loi, Q1"
  },
  "dropoff": {
    "lat": 10.7850,
    "lng": 106.6800,
    "address": "456 CMT8, Q3"
  },
  "vehicleType": "CAR_4_SEAT",
  "paymentMethod": "CASH"
}
```

Khac voi tai lieu goc: **khong nhan `estimatedFare` tu client de ghi DB**. Client co the hien thi estimate, nhung server phai tinh lai khi tao trip.

### 5.5 Tracking APIs

| Method | Path | Role | Ghi chu |
|---|---|---|---|
| GET | `/api/v1/tracking/trips/{tripId}/driver-location` | PASSENGER/DRIVER | REST fallback khi WS mat ket noi |

Response:

```json
{
  "driverId": 5,
  "location": {
    "lat": 10.7800,
    "lng": 106.6900
  },
  "bearing": 120.5,
  "speed": 32.0,
  "updatedAt": "2026-05-16T10:35:00Z"
}
```

### 5.6 Rating APIs

| Method | Path | Role | Ghi chu |
|---|---|---|---|
| POST | `/api/v1/ratings` | PASSENGER | Rating trip completed |

Request:

```json
{
  "tripId": 101,
  "score": 5,
  "comment": "Tai xe dung gio"
}
```

### 5.7 Admin APIs minimum

| Method | Path | Role | Ghi chu |
|---|---|---|---|
| GET | `/api/v1/admin/users` | ADMIN | Filter role/status |
| PATCH | `/api/v1/admin/users/{userId}/status` | ADMIN | ACTIVE/SUSPENDED |
| GET | `/api/v1/admin/drivers/pending` | ADMIN | Ho so cho duyet |
| PATCH | `/api/v1/admin/drivers/{driverId}/approval` | ADMIN | APPROVED/REJECTED |
| GET | `/api/v1/admin/trips` | ADMIN | Filter status/date |
| GET | `/api/v1/admin/stats` | ADMIN | So lieu demo |

---

## 6. WebSocket Design

### 6.1 Endpoint

```text
/ws
```

Client dung STOMP. Gui JWT trong STOMP `CONNECT` header:

```javascript
stompClient.connect(
  { Authorization: `Bearer ${accessToken}` },
  onConnected
);
```

Server can `ChannelInterceptor` de:
- Doc JWT khi `CONNECT`.
- Gan authenticated `Principal`.
- Tu choi subscribe neu user khong co quyen voi topic.

### 6.2 Client SEND destinations

| Destination | Role | Payload | Ghi chu |
|---|---|---|---|
| `/app/driver.location` | DRIVER | `{ lat, lng, bearing, speed, tripId? }` | Gui moi 3-5 giay |
| `/app/driver.heartbeat` | DRIVER | `{}` | Reset Redis TTL |
| `/app/trip.status` | DRIVER | `{ tripId, status }` | Alternative cho REST status patch |

Khong tin `driverId` tu payload. Lay driver id tu JWT principal.

### 6.3 Server SUBSCRIBE destinations

| Destination | Subscriber | Payload | Ghi chu |
|---|---|---|---|
| `/topic/trips/{tripId}/location` | Passenger/driver cua trip | `{ lat, lng, bearing, speed, updatedAt }` | Vi tri realtime |
| `/topic/trips/{tripId}/status` | Passenger/driver cua trip | `{ tripId, status, updatedAt }` | Trang thai trip |
| `/user/queue/notifications` | Authenticated user | `{ type, title, body, data }` | Thong bao ca nhan |
| `/user/queue/trip-requests` | Driver | `{ tripId, passenger, pickup, dropoff, estimatedFare, expiresAt }` | Offer cho dung driver |

Dung `/user/queue/trip-requests`, khong dung `/topic/driver/{driverId}/request`, de tranh subscribe cheo neu security bi cau hinh sai.

### 6.4 Notification types

```text
NEW_TRIP_REQUEST
TRIP_ACCEPTED
DRIVER_ARRIVED
TRIP_STARTED
TRIP_COMPLETED
TRIP_CANCELLED
NO_DRIVER_FOUND
DRIVER_DISCONNECTED
```

---

## 7. Trip State Machine

### 7.1 Valid transitions

| From | To | Trigger |
|---|---|---|
| SEARCHING | ACCEPTED | Matching/driver accept |
| SEARCHING | NO_DRIVER | Matching retry het |
| SEARCHING | CANCELLED | Passenger |
| ACCEPTED | ARRIVED | Driver |
| ACCEPTED | CANCELLED | Passenger/driver |
| ARRIVED | IN_PROGRESS | Driver |
| ARRIVED | CANCELLED | Passenger/driver |
| IN_PROGRESS | COMPLETED | Driver |

Moi transition khac tra `TRIP_STATUS_INVALID_TRANSITION`.

### 7.2 Active trip rule

Passenger chi duoc co 1 trip active:

```text
SEARCHING, ACCEPTED, ARRIVED, IN_PROGRESS
```

Driver chi duoc co 1 trip active:

```text
ACCEPTED, ARRIVED, IN_PROGRESS
```

### 7.3 Side effects

| Transition | Side effect |
|---|---|
| SEARCHING -> ACCEPTED | set `driver_id`, `accepted_at`, driver Redis status BUSY |
| ACCEPTED -> ARRIVED | set `arrived_at`, notify passenger |
| ARRIVED -> IN_PROGRESS | set `started_at`, start tracking history |
| IN_PROGRESS -> COMPLETED | set `completed_at`, calculate final fare, payment, rating prompt |
| any -> CANCELLED | set `cancelled_at`, release driver if needed |
| SEARCHING -> NO_DRIVER | release matching locks, notify passenger |

---

## 8. Events

Dung `@TransactionalEventListener(phase = AFTER_COMMIT)` cho events phat sinh tu DB transaction. Neu handler xu ly lau, ket hop `@Async`.

| Event | Publisher | Subscriber | Ghi chu |
|---|---|---|---|
| `BookingCreatedEvent` | Booking | Matching | Bat dau tim driver sau commit |
| `TripAcceptedEvent` | Matching | Notification | Bao passenger |
| `TripStatusChangedEvent` | Booking/Tracking | Notification, Payment | Moi lan doi status |
| `TripCompletedEvent` | Booking | Payment, Notification, Driver | Tao payment va cap nhat stats |
| `PaymentCompletedEvent` | Payment | Notification | MVP cash co the sync |
| `RatingCreatedEvent` | Rating | Driver | Cap nhat average rating |

Event object nen chua id va snapshot toi thieu, khong chua JPA entity.

```java
public record BookingCreatedEvent(
    Long tripId,
    Long passengerId,
    String vehicleType,
    double pickupLat,
    double pickupLng
) {}
```

---

## 9. Matching Algorithm

### 9.1 MVP flow

```text
BookingCreatedEvent
  -> get trip
  -> GEOSEARCH drivers:online FROMLONLAT pickupLng pickupLat BYRADIUS 5 km ASC COUNT 20
  -> filter vehicleType, redis status AVAILABLE, approved driver
  -> lay toi da 3 candidates
  -> offer tung driver, moi driver timeout 30s
  -> accept: trip ACCEPTED
  -> reject/timeout: offer driver tiep theo
  -> het attempt: trip NO_DRIVER
```

### 9.2 Redis query

```text
GEOSEARCH drivers:online FROMLONLAT <lng> <lat> BYRADIUS 5 km ASC COUNT 20 WITHDIST
```

Sau khi co candidate tu Redis:
- Doc `driver:{id}:status`.
- Doc `driver:{id}:meta`.
- Validate DB `approval_status = APPROVED` neu can chong stale cache.
- Lock driver bang `SET NX`.

### 9.3 Strategy interface

```java
public interface DriverMatchingStrategy {
    List<DriverCandidate> rank(MatchingRequest request, List<DriverCandidate> candidates);
}
```

MVP:

```text
NearestDriverStrategy: sort theo distanceMeters tang dan
```

Sau nay:

```text
RatingWeightedStrategy: distance + rating + acceptance rate
```

---

## 10. Security Rules

### 10.1 REST

| Resource | Rule |
|---|---|
| Auth endpoints | Public |
| Booking detail/cancel | Passenger cua trip, driver cua trip, hoac admin |
| Driver respond/status | Chi driver dang duoc offer/assigned |
| Tracking location fallback | Passenger/driver cua trip |
| Rating create | Passenger cua completed trip |
| Admin | Role ADMIN |

### 10.2 WebSocket

Bat buoc:
- Authenticate STOMP `CONNECT`.
- Khong nhan `userId`/`driverId` trong payload de xac thuc.
- Authorize subscribe `/topic/trips/{tripId}/...` bang DB/cache.
- Dung `/user/queue/...` cho message ca nhan.
- Rate limit `driver.location` neu client spam qua nhieu.

### 10.3 Secrets

Khong commit:
- `JWT_SECRET`
- `GOOGLE_MAPS_API_KEY`
- Firebase service account
- DB password

Dung `.env` local va environment variables khi deploy.

---

## 11. External Services

### 11.1 Maps/distance

De tranh phu thuoc Google Maps trong giai doan dau, tao interface:

```java
public interface DistanceService {
    DistanceEstimate estimate(Location pickup, Location dropoff);
}
```

Implementations:
- `MockDistanceService` cho local/test.
- `GoogleMapsDistanceService` khi co API key.

Booking chi depend vao interface nay.

### 11.1.1 Three-word location lookup

Backend wraps the custom Python location service through two authenticated mobile APIs:

- `GET /api/v1/locations/to-words?lat={lat}&lng={lng}` for passenger map selection.
- `GET /api/v1/locations/to-coordinate?address={word.word.word}` for driver lookup.

Runtime variables are `THREE_WORD_LOCATION_ENABLED`, `THREE_WORD_LOCATION_BASE_URL`, and `THREE_WORD_LOCATION_TIMEOUT_SECONDS`; the provider is disabled by default. The adapter translates GoRide `lng` to the provider's `lon` query/response field. Provider compound words are normalized with `_` internally, but API responses convert `_` back to spaces for mobile display. Coordinate lookup rejects mismatched provider addresses, inverted bounds, and points outside the returned cell bounds. Booking, routing, matching, and fare calculation continue to use coordinates as source of truth; the three-word address is optional display/share metadata.

The complete React Native contract is documented in `docs/three-word-location-mobile-integration.md`.

### 11.2 Push notification

MVP co the dung WebSocket notification truoc. FCM de phase sau neu thieu thoi gian.

```java
public interface NotificationSender {
    void send(UserNotification notification);
}
```

Implementations:
- `WebSocketNotificationSender`
- `FcmNotificationSender` sau nay.

---

### 11.3 Upload storage

Backend co `FileStorageService` de tach upload API khoi storage provider.

Implementations hien co:
- `LocalFileStorageService`: mac dinh cho local/dev, luu file duoi `uploads/` va serve `/uploads/**`.
- `R2FileStorageService`: dung Cloudflare R2/S3-compatible API khi `STORAGE_PROVIDER=r2`.

Runtime R2 can cac bien moi truong:

```text
STORAGE_PROVIDER=r2
CLOUDFLARE_R2_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
CLOUDFLARE_R2_REGION=auto
CLOUDFLARE_R2_BUCKET=goride-uploads
CLOUDFLARE_R2_ACCESS_KEY=<r2-access-key>
CLOUDFLARE_R2_SECRET_KEY=<r2-secret-key>
CLOUDFLARE_R2_PUBLIC_BASE_URL=https://cdn.example.com
```

Quy tac:
- Khong commit R2 credential.
- Avatar co the public qua CDN/custom domain.
- Anh giay to tai xe nen review lai privacy; neu bucket khong public, them signed URL/proxy download cho admin/driver truoc production.

---

### 11.4 In-trip messaging

Backend co module `chat` cho message text theo tung trip.

REST contract:
- `GET /api/v1/trips/{tripId}/messages?page=1&size=50`: passenger/driver cua trip va admin xem lich su, tra `PageResponse<TripMessageResponse>` sap xep moi nhat truoc.
- `POST /api/v1/trips/{tripId}/messages`: passenger/assigned driver gui `{clientMessageId, body}`; retry cung UUID tra message da luu va khong fan-out lan hai.
- `GET /api/v1/trips/{tripId}/messages/sync`: initial/older/newer cursor sync qua `beforeId` hoac `afterId`.
- `PUT /api/v1/trips/{tripId}/messages/read-state`: tien read cursor toi `lastReadMessageId`.
- `GET /api/v1/trips/{tripId}/messages/unread-count`: dem message cua participant con lai sau read cursor.

WebSocket contract:
- FE gui `SEND /app/trip.message` voi `{ "tripId": 99, "clientMessageId": "<uuid>", "body": "..." }`.
- Backend broadcast message da luu qua `/topic/trip/{tripId}/messages`.
- Backend broadcast read cursor qua `/topic/trip/{tripId}/message-read`.
- STOMP ACK qua `/user/queue/trip-message-acks`; business error qua `/user/queue/trip-message-errors`.
- `TripTopicSubscriptionAuthorizer` cho `/topic/trip/{tripId}/messages` dung chung rule voi status/location: passenger cua trip, driver cua trip hoac admin moi subscribe duoc.

Rule nghiep vu:
- Chi passenger va assigned driver duoc gui message.
- Chi gui khi trip status la `ACCEPTED`, `ARRIVED` hoac `IN_PROGRESS`.
- Admin duoc xem history/subscription de support, khong gui thay participant.
- Body trim, bat buoc khong rong va toi da 1000 ky tu.

Database:
- SQL release `db/releases/20260701-trip-messages` tao bang `trip_messages` voi FK den `trips` va `users`.
- SQL release `db/releases/20260811-trip-messaging-reliability` them idempotency UUID, unique constraint va bang `trip_message_read_states`.

Reliability/security:
- Chat rate limit mac dinh 30 message/phut/user va dung cung memory/Redis store voi global rate limiting.
- WebSocket endpoint dung CORS allowlist, khong con wildcard origin.
- FCM type `TRIP_MESSAGE_RECEIVED` duoc gui cho recipient khi Firebase channel enabled.
- Simple Broker hien tai chi dam bao realtime trong mot backend replica; scale ngang can broker relay/shared fan-out, con REST cursor sync la recovery path.

---
## 12. Error Codes

| Code | HTTP | Khi nao |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Request sai format |
| `INVALID_CREDENTIALS` | 401 | Sai phone/password |
| `TOKEN_EXPIRED` | 401 | Access token het han |
| `TOKEN_INVALID` | 401 | Token sai |
| `FORBIDDEN` | 403 | Khong co quyen |
| `USER_NOT_FOUND` | 404 | Khong tim thay user |
| `TRIP_NOT_FOUND` | 404 | Khong tim thay trip |
| `DRIVER_PROFILE_NOT_FOUND` | 404 | Chua co driver profile |
| `PHONE_ALREADY_EXISTS` | 409 | Trung phone |
| `TRIP_ALREADY_RATED` | 409 | Rating lan 2 |
| `DRIVER_NOT_APPROVED` | 422 | Driver chua duyet |
| `DRIVER_NOT_AVAILABLE` | 422 | Driver dang busy/offline |
| `PASSENGER_HAS_ACTIVE_TRIP` | 422 | Passenger co trip active |
| `TRIP_CANNOT_BE_CANCELLED` | 422 | Status khong cho huy |
| `TRIP_STATUS_INVALID_TRANSITION` | 422 | State machine reject |
| `TRIP_MESSAGE_NOT_AVAILABLE` | 422 | Trip chua/khong con cho phep chat |
| `LOCATION_OUT_OF_SERVICE_AREA` | 422 | Ngoai vung phuc vu |
| `WORD_LOCATION_INVALID_ADDRESS` | 400 | Cum 3 tu sai dinh dang |
| `WORD_LOCATION_NOT_FOUND` | 404 | Khong tim thay cum 3 tu |
| `WORD_LOCATION_OUT_OF_BOUNDS` | 422 | Toa do ngoai vung provider ho tro |
| `WORD_LOCATION_PROVIDER_UNAVAILABLE` | 503 | Provider dang tat/cau hinh sai |
| `WORD_LOCATION_PROVIDER_ERROR` | 502 | Provider timeout/HTTP/response loi |
| `NO_DRIVER_AVAILABLE` | 422 | Khong co driver |
| `INTERNAL_SERVER_ERROR` | 500 | Loi khong xac dinh |

---

## 13. Local Development

### 13.1 docker-compose.yml

```yaml
services:
  postgres:
    image: postgis/postgis:15-3.3
    environment:
      POSTGRES_DB: goride
      POSTGRES_USER: goride
      POSTGRES_PASSWORD: goride
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

volumes:
  postgres_data:
```

### 13.2 application-local.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/goride
    username: goride
    password: goride
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        format_sql: true
  data:
    redis:
      host: localhost
      port: 6379

app:
  jwt:
    secret: ${JWT_SECRET:local-dev-secret-change-me}
    access-token-minutes: 15
    refresh-token-days: 7
  service-area:
    city: Ho Chi Minh City
    min-lat: 10.3919
    max-lat: 11.1600
    min-lng: 106.3634
    max-lng: 107.0312
  matching:
    radius-km: 5
    max-attempts: 3
    offer-timeout-seconds: 30
```

---

## 14. Test Plan

### 14.1 Unit tests

| Module | Can test |
|---|---|
| Auth | Password hashing, JWT, refresh rotation |
| Booking | Fare calculation, active trip validation, state transitions |
| Matching | Candidate ranking, retry, timeout, no driver |
| Tracking | Location validation, broadcast decision |
| Payment | Cash completed, provider selection |
| Rating | Only completed trip, update average |

### 14.2 Integration tests

Dung Testcontainers:
- PostgreSQL + PostGIS.
- Redis.

Flow can co:
1. Register passenger/driver.
2. Admin approve driver.
3. Driver online.
4. Passenger create booking.
5. Matching offer driver.
6. Driver accept.
7. Driver update ARRIVED -> IN_PROGRESS -> COMPLETED.
8. Payment cash created.
9. Passenger rating.

Edge cases:
- Passenger co active trip.
- Driver reject 3 lan -> `NO_DRIVER`.
- Cancel o `SEARCHING`, `ACCEPTED`, `ARRIVED`.
- Driver khong phai offered driver nhan accept -> 403/422.
- WebSocket subscribe trip khong phai cua minh -> reject.

---

## 15. Checklist Trien Khai Dau Tien Trong Repo

1. Cap nhat `pom.xml` dependencies can thiet.
2. Tao `docker-compose.yml`.
3. Tao `application-local.yml`.
4. Tao `common`:
   - `ApiResponse`
   - `PageResponse`
   - `ErrorResponse`
   - `GlobalExceptionHandler`
   - `BusinessException`
   - `ErrorCode`
5. Tao entity/repository cho `users`, `user_roles`.
6. Tao Spring Security + JWT.
7. Tao Auth APIs.
8. Tao driver profile + admin approval.
9. Tao pricing + fare service.
10. Tao booking state machine.
11. Tao matching Redis adapter.
12. Tao WebSocket config + auth interceptor.
13. Tao tracking handler.
14. Tao payment cash + rating.
15. Them integration test happy path.

---

## 16. Viec Co The Cat Neu Tre

Giu lai:
- Auth
- Driver approval co the lam bang endpoint admin don gian
- Booking
- Matching
- Driver accept/reject
- Tracking realtime hoac REST fallback
- Complete trip
- Cash payment
- Rating

Cat/trien khai sau:
- Web Admin UI
- FCM push notification
- Cloudflare R2/S3-compatible storage deployment UAT
- MoMo/VNPay
- Surge pricing dong
- Scheduled rides
- Analytics dashboard
- Multi-city

---

## 17. Khac Biet Quan Trong So Voi Tai Lieu Goc

1. Matching online driver dung Redis GEO, khong dung SQL PostGIS tren cot `driver_location`.
2. Them bang `trip_location_history`.
3. `users` dung `user_roles` de ho tro mot tai khoan nhieu vai tro.
4. `trips` them `vehicle_type`, `payment_method`, `estimated_duration_min`.
5. API tao booking khong nhan `estimatedFare` lam source of truth.
6. WebSocket offer driver dung `/user/queue/trip-requests` thay vi public topic theo driver id.
7. Them race-condition lock khi matching.
8. Deployment AWS/Railway duoc dua ra ngoai MVP backend; upload storage hien co local dev va Cloudflare R2 provider cho staging/production khi duoc cau hinh env.
9. Attachment/read receipt/typing indicator cho chat duoc de sau MVP text messaging.
