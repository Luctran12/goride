# Tài liệu Thiết kế Hệ thống Ride-Sharing

> **Dự án:** Hệ thống đặt xe trực tuyến (Ride-Sharing)
> **Nhóm:** [Tên nhóm]
> **Ngày:** [Ngày]
> **Phiên bản:** 1.0

---

## 1. Tổng quan hệ thống

[To be written]

---

## 2. Kiến trúc tổng thể

### 2.1 Sơ đồ kiến trúc tổng thể

```
┌─────────────────────────────────────────────────────────┐
│                      CLIENT LAYER                        │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ Passenger App│  │  Driver App  │  │   Web Admin   │  │
│  │ (React Native│  │ (React Native│  │    (React)    │  │
└──┴──────┬───────┴──┴──────┬───────┴──┴───────┬───────┴──┘
          │ REST / WebSocket│                   │ REST
          └────────┬────────┘                   │
                   ▼                            ▼
┌──────────────────────────────────────────────────────────┐
│                    SERVER LAYER                           │
│                                                          │
│   Spring Boot — Modular Monolith                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐  │
│  │  Auth    │ │ Booking  │ │ Tracking │ │Notification│  │
│  │  Module  │ │  Module  │ │  Module  │ │   Module   │  │
│  └──────────┘ └──────────┘ └──────────┘ └────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                  │
│  │ Matching │ │ Payment  │ │  Admin   │                  │
│  │  Module  │ │  Module  │ │  Module  │                  │
│  └──────────┘ └──────────┘ └──────────┘                  │
│                     │ Spring Application Events           │
└─────────────────────┼────────────────────────────────────┘
                       │
┌─────────────────────┼────────────────────────────────────┐
│                DATA LAYER            │                    │
│   ┌──────────────────┐   ┌───────────────┐               │
│   │  PostgreSQL       │   │     Redis     │               │
│   │  + PostGIS        │   │   (Cache)     │               │
│   └──────────────────┘   └───────────────┘               │
└──────────────────────────────────────────────────────────┘
                       │
┌─────────────────────┼────────────────────────────────────┐
│              EXTERNAL SERVICES                            │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│   │ Google Maps  │  │ Firebase FCM │  │  (MoMo/VNPay │   │
│   │     API      │  │    (Push)    │  │   — future)  │   │
│   └──────────────┘  └──────────────┘  └──────────────┘   │
└──────────────────────────────────────────────────────────┘
```

---

### 2.2 Lý do chọn Modular Monolith

Hệ thống được thiết kế theo kiến trúc **Modular Monolith** — một ứng dụng Spring Boot duy nhất nhưng được chia thành các module nội bộ độc lập, giao tiếp với nhau qua interface và Spring Application Events thay vì gọi trực tiếp.

| Tiêu chí | Microservices | Modular Monolith | Monolith thuần |
|---|---|---|---|
| Độ phức tạp vận hành | Cao (nhiều service, nhiều DB) | Trung bình | Thấp |
| Khả năng scale độc lập | ✅ Tốt | ⚠️ Giới hạn | ❌ Không có |
| Tốc độ phát triển (team nhỏ) | ❌ Chậm | ✅ Nhanh | ✅ Nhanh |
| Khả năng tách thành microservice sau | — | ✅ Dễ dàng | ❌ Khó |
| Phù hợp 2 người / 3 tháng | ❌ | ✅ | ⚠️ |

**Quyết định:** Modular Monolith cho phép phát triển nhanh trong giai đoạn đồ án, đồng thời giữ ranh giới module rõ ràng để có thể tách microservice trong tương lai nếu cần.

---

### 2.3 Các module và trách nhiệm

| Module | Trách nhiệm |
|---|---|
| **Auth Module** | Đăng ký, đăng nhập, cấp/xác thực JWT, phân quyền theo role |
| **Booking Module** | Tạo yêu cầu đặt xe, quản lý trạng thái chuyến đi, lịch sử |
| **Matching Module** | Tìm tài xế phù hợp, áp dụng Strategy Pattern để đổi thuật toán |
| **Tracking Module** | Nhận vị trí real-time từ tài xế, broadcast đến hành khách qua WebSocket |
| **Payment Module** | Xử lý thanh toán, abstraction layer hỗ trợ nhiều phương thức |
| **Notification Module** | Gửi push notification (FCM) và in-app notification |
| **Admin Module** | Quản lý người dùng, tài xế, thống kê, báo cáo |

Các module giao tiếp nội bộ thông qua **Spring Application Events** — không gọi trực tiếp service của nhau — giúp giảm coupling và dễ tách module sau này.

---

### 2.4 Luồng giao tiếp chính

```
Passenger App                Server                   Driver App
     │                          │                          │
     │──── POST /bookings ──────▶│                          │
     │                          │── MatchingModule tìm ───▶│
     │                          │   tài xế gần nhất        │
     │                          │                          │
     │                          │──── WebSocket PUSH ─────▶│
     │                          │   (yêu cầu đặt xe)       │
     │                          │                          │
     │                          │◀─── Tài xế xác nhận ─────│
     │◀──── WebSocket PUSH ─────│                          │
     │   (tài xế đang đến)      │                          │
     │                          │                          │
     │         [Trong chuyến đi — tracking loop]           │
     │                          │◀─── vị trí GPS (WS) ─────│
     │◀──── vị trí tài xế ──────│                          │
     │                          │                          │
     │──── Hoàn thành ──────────▶│                          │
     │◀──── Tính tiền, rating ───│──── Thông báo ──────────▶│
```

**Nguyên tắc phân chia REST vs WebSocket:**
- **REST API:** Các thao tác CRUD, đặt xe, xác nhận, đánh giá, lịch sử
- **WebSocket (STOMP):** Tracking vị trí real-time, trạng thái chuyến đi, thông báo tức thời

---

### 2.5 Cơ chế Real-time (WebSocket + STOMP)

Hệ thống sử dụng **WebSocket với giao thức STOMP** (Simple Text Oriented Messaging Protocol) — được Spring Boot hỗ trợ native qua `spring-websocket`.

**Các channel chính:**

| Channel | Chiều | Mô tả |
|---|---|---|
| `/app/driver.location` | Driver → Server | Tài xế gửi vị trí GPS liên tục |
| `/topic/trip/{tripId}/location` | Server → Passenger | Server broadcast vị trí tài xế đến hành khách |
| `/topic/trip/{tripId}/status` | Server → cả 2 | Thay đổi trạng thái chuyến đi |
| `/user/queue/notifications` | Server → từng user | Thông báo cá nhân (có tài xế, bị hủy...) |

**Vị trí tài xế được quản lý trong Redis theo 2 mục đích:**
- `drivers:online` (Redis GEO Sorted Set) — lưu vị trí tất cả driver đang online, hỗ trợ query tìm tài xế gần nhất bằng `GEOSEARCH` trong 1 lệnh duy nhất
- Khi driver offline → vị trí cuối được cập nhật vào `driver_profiles.last_known_location` trong PostgreSQL

**Xử lý mất kết nối:**
- Tài xế mất kết nối WebSocket: Server đánh dấu `driver_status = OFFLINE` sau 30s không nhận heartbeat, hành khách nhận thông báo và hệ thống thử re-match
- Hành khách mất kết nối: Chuyến đi vẫn tiếp tục, trạng thái được đồng bộ lại khi reconnect
- Server restart: Client tự động reconnect với exponential backoff (SockJS hỗ trợ sẵn)

---

### 2.6 Luồng đặt xe end-to-end

```
[1] Passenger nhập điểm đón/trả
[2] App gọi API tính giá ước tính (Google Maps Distance Matrix)
[3] Passenger xác nhận đặt xe → POST /bookings
[4] Booking Module tạo Trip(status=SEARCHING), publish BookingCreatedEvent
[5] Matching Module nhận event, tìm tài xế phù hợp
[6] Gửi yêu cầu đến tài xế qua WebSocket (timeout 30s)
[7a] Tài xế chấp nhận → Trip(status=ACCEPTED), notify passenger
[7b] Tài xế từ chối / timeout → thử tài xế tiếp theo (tối đa 3 lần)
[7c] Không có tài xế → Trip(status=NO_DRIVER), notify passenger
[8] Tài xế đến nơi → Trip(status=ARRIVED)
[9] Bắt đầu chuyến → Trip(status=IN_PROGRESS), bật tracking loop
[10] Kết thúc chuyến → Trip(status=COMPLETED)
[11] Tính tiền, yêu cầu rating từ cả 2 phía
```

---

### 2.7 Authentication & Authorization

Hệ thống sử dụng **JWT (JSON Web Token)** stateless authentication.

**Các role:**

| Role | Quyền truy cập |
|---|---|
| `PASSENGER` | Đặt xe, xem lịch sử, rating, cập nhật profile |
| `DRIVER` | Nhận/từ chối chuyến, cập nhật vị trí, xem thu nhập |
| `ADMIN` | Quản lý toàn bộ hệ thống, xem báo cáo, khóa tài khoản |

**Luồng Auth:**
1. Đăng nhập → Server cấp `access_token` (15 phút) + `refresh_token` (7 ngày)
2. Mọi request đính kèm `Authorization: Bearer <access_token>`
3. WebSocket connection xác thực JWT trong handshake header
4. Khi `access_token` hết hạn → client dùng `refresh_token` lấy token mới

---

### 2.8 Stack công nghệ

| Layer | Công nghệ | Lý do |
|---|---|---|
| **Mobile App** | React Native | Cross-platform, 1 codebase cho cả iOS/Android |
| **Web Admin** | React + Vite | Cùng ecosystem, build nhanh |
| **Backend** | Java Spring Boot 3.x | Mature, hỗ trợ WebSocket/JPA/Security tốt |
| **Database chính** | PostgreSQL + PostGIS | PostGIS xử lý truy vấn địa lý (tìm tài xế gần nhất) |
| **Cache / Real-time store** | Redis | Cache vị trí tài xế, quản lý session WebSocket |
| **Map & Routing** | Google Maps API | Chất lượng bản đồ VN tốt nhất, $200 free/tháng |
| **Push Notification** | Firebase FCM | Free, SDK React Native hoàn chỉnh |
| **Containerization** | Docker + Docker Compose | Đồng nhất môi trường dev/prod |
| **Deploy** | Railway | Deploy Docker dễ, free tier đủ demo |

---

### 2.9 Matching Algorithm — Lựa chọn và thiết kế

#### Các thuật toán

| Thuật toán | Ưu điểm | Nhược điểm | Phù hợp |
|---|---|---|---|
| **Nearest Driver** (PostGIS ST_Distance) | Đơn giản, nhanh, dễ implement | Không tối ưu tổng thể | ✅ MVP |
| **Nearest + Rating filter** | Cân bằng khoảng cách và chất lượng | Phức tạp hơn một chút | Nâng cấp nhẹ |
| **Hungarian Algorithm** | Tối ưu matching nhiều xe cùng lúc | Phức tạp, overkill cho đồ án | ❌ |
| **ML-based** (demand prediction) | Tối ưu nhất | Cần dữ liệu lớn, rất phức tạp | ❌ |

**Quyết định MVP:** Nearest Driver sử dụng PostGIS `ST_DWithin` để tìm tài xế trong bán kính 5km, sắp xếp theo khoảng cách tăng dần.

#### Strategy Pattern — cho phép đổi thuật toán

```java
// Interface — không đổi dù thuật toán thay đổi
public interface DriverMatchingStrategy {
    Optional<Driver> findBestDriver(Location pickupLocation, List<Driver> availableDrivers);
}

// MVP implementation
@Component("nearest")
public class NearestDriverStrategy implements DriverMatchingStrategy { ... }

// Tương lai — thêm không cần sửa Booking logic
@Component("ratingBased")
public class RatingWeightedStrategy implements DriverMatchingStrategy { ... }

// Matching Module inject strategy qua config
@Service
public class MatchingService {
    @Autowired
    @Qualifier("nearest") // đổi thành "ratingBased" không ảnh hưởng code khác
    private DriverMatchingStrategy matchingStrategy;
}
```

---

### 2.10 Database Layer

| Thành phần | Vai trò |
|---|---|
| **PostgreSQL** | Lưu trữ chính: users, trips, payments, ratings |
| **PostGIS extension** | Lưu và truy vấn tọa độ địa lý, tìm tài xế gần nhất hiệu quả |
| **Redis** | Cache vị trí tài xế real-time (key: `driver:{id}:location`, TTL: 30s), refresh token store |

---

### 2.11 Giới hạn địa lý

Hệ thống hoạt động trong phạm vi **một thành phố** (thành phố được cấu hình trong application config). Các yêu cầu đặt xe có điểm đón hoặc điểm trả ngoài vùng bounding box của thành phố sẽ bị từ chối ở tầng validation.

```yaml
# application.yml
service:
  geographic:
    city: "Ho Chi Minh City"
    bounding-box:
      min-lat: 10.3919
      max-lat: 11.1600
      min-lng: 106.3634
      max-lng: 107.0312
```

---

### 2.12 Module Dependency

```
Auth Module ◀──────────────── tất cả module (xác thực)
                │
Booking Module ──▶ Matching Module (qua Event)
                │
                ├──▶ Payment Module (qua Event)
                │
                └──▶ Notification Module (qua Event)

Tracking Module ──▶ Booking Module (cập nhật trạng thái)

Admin Module ──▶ tất cả module (read-only queries)
```

**Nguyên tắc:** Module chỉ depend vào Auth Module trực tiếp. Các module khác giao tiếp qua **Spring Application Events** để tránh circular dependency và giảm coupling.

---

### 2.13 Event-driven nội bộ (Spring Application Events)

| Event | Publisher | Subscriber |
|---|---|---|
| `BookingCreatedEvent` | Booking Module | Matching Module |
| `TripMatchedEvent` | Matching Module | Notification Module |
| `TripStatusChangedEvent` | Booking/Tracking Module | Notification Module, Payment Module |
| `TripCompletedEvent` | Booking Module | Payment Module, Notification Module |
| `PaymentCompletedEvent` | Payment Module | Notification Module |

---

### 2.14 Cấu trúc package Spring Boot

```
com.ridesharing
├── auth/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   └── dto/
├── booking/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── event/
│   └── dto/
├── matching/
│   ├── service/
│   └── strategy/          ← DriverMatchingStrategy implementations
├── tracking/
│   ├── controller/        ← WebSocket controllers
│   └── service/
├── payment/
│   ├── service/
│   └── provider/          ← PaymentProvider implementations
├── notification/
│   └── service/
├── admin/
│   └── controller/
└── common/                ← shared DTOs, exceptions, utils
```

---

### 2.15 Payment Abstraction Layer

Thiết kế cho phép thêm phương thức thanh toán mới mà không sửa business logic:

```java
// Interface trung tâm
public interface PaymentProvider {
    PaymentResult process(PaymentRequest request);
    String getProviderName();
}

// MVP — chỉ implement Cash
@Component
public class CashPaymentProvider implements PaymentProvider {
    public PaymentResult process(PaymentRequest request) {
        // Ghi nhận thanh toán tiền mặt, không cần gọi external API
        return PaymentResult.success();
    }
}

// Tương lai — thêm không đụng code hiện tại
@Component
public class MoMoPaymentProvider implements PaymentProvider { ... }
@Component
public class VNPayPaymentProvider implements PaymentProvider { ... }

// Service dùng chung
@Service
public class PaymentService {
    private final Map<String, PaymentProvider> providers; // inject tất cả providers
    
    public PaymentResult pay(String method, PaymentRequest request) {
        return providers.get(method).process(request);
    }
}
```

---

### 2.16 Sơ đồ Client Platforms

| Client | Tech | User | Kết nối Server |
|---|---|---|---|
| **Passenger App** | React Native | Hành khách | REST + WebSocket |
| **Driver App** | React Native | Tài xế | REST + WebSocket (gửi GPS liên tục) |
| **Web Admin** | React + Vite | Admin | REST only |

Ba client dùng chung một backend, phân quyền qua JWT role. Driver App có thêm trách nhiệm gửi vị trí GPS định kỳ (mỗi 3-5 giây trong chuyến đi) qua WebSocket.

---

## 3. Thiết kế Database

### 3.1 Danh sách bảng

| Bảng | Vai trò |
|---|---|
| `users` | Thông tin chung của mọi người dùng (passenger, driver, admin) |
| `driver_profiles` | Thông tin bổ sung dành riêng cho tài xế |
| `trips` | Toàn bộ vòng đời một chuyến đi |
| `trip_status_history` | Audit log mọi thay đổi trạng thái của trip |
| `ratings` | Hành khách đánh giá tài xế sau chuyến đi |
| `payments` | Lịch sử thanh toán gắn với từng chuyến đi |
| `pricing_config` | Cấu hình công thức tính giá, có thể thay đổi qua admin |

---

### 3.2 ERD

```
users (1) ──────────── (0..1) driver_profiles
  │                               │
  │ (1)                           │
  │                               │
  ├── as passenger                │
  │      │ (1)                    │
  │      ▼                        │ (1)
  │    trips (*)  ────────────────┘
  │      │  (1)
  │      │
  │      ├──── (*)  trip_status_history
  │      │
  │      ├──── (0..1)  ratings
  │      │
  │      └──── (0..1)  payments
  │
  └── as driver
         │ (1)
         ▼
       trips (*) [driver_id FK]
```

---

### 3.3 Bảng `users`

```sql
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    full_name       VARCHAR(100)        NOT NULL,
    phone           VARCHAR(15)         NOT NULL UNIQUE,
    email           VARCHAR(100)        UNIQUE,
    password_hash   VARCHAR(255)        NOT NULL,
    avatar_url      VARCHAR(500),
    role            VARCHAR(20)         NOT NULL,  -- PASSENGER | DRIVER | ADMIN
    status          VARCHAR(20)         NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | SUSPENDED
    created_at      TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP           NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP           NULL       -- soft delete
);

CREATE INDEX idx_users_phone ON users(phone) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_role   ON users(role)  WHERE deleted_at IS NULL;
```

**Ghi chú:**
- Một người dùng có thể đăng ký làm tài xế — khi đó `role = DRIVER` và có thêm bản ghi trong `driver_profiles`
- `status = SUSPENDED` dùng khi admin khóa tài khoản, khác với soft delete

---

### 3.4 Bảng `driver_profiles`

```sql
CREATE TABLE driver_profiles (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT          NOT NULL UNIQUE REFERENCES users(id),
    license_number      VARCHAR(20)     NOT NULL UNIQUE,  -- số bằng lái
    license_expiry      DATE            NOT NULL,
    id_card_number      VARCHAR(12)     NOT NULL UNIQUE,  -- số CCCD
    portrait_url        VARCHAR(500)    NOT NULL,          -- ảnh chân dung
    vehicle_plate       VARCHAR(20)     NOT NULL UNIQUE,  -- biển số xe
    vehicle_type        VARCHAR(20)     NOT NULL,         -- MOTORBIKE | CAR_4_SEAT | CAR_7_SEAT
    vehicle_brand       VARCHAR(50),
    vehicle_model       VARCHAR(50),
    vehicle_color       VARCHAR(30),
    vehicle_year        SMALLINT,
    approval_status       VARCHAR(20)     NOT NULL DEFAULT 'PENDING', -- PENDING | APPROVED | REJECTED
    is_online             BOOLEAN         NOT NULL DEFAULT FALSE,
    average_rating        NUMERIC(2,1)    NOT NULL DEFAULT 5.0,
    total_trips           INTEGER         NOT NULL DEFAULT 0,
    last_known_location   GEOMETRY(Point, 4326)   NULL, -- cập nhật khi driver offline, không phải real-time
    last_location_at      TIMESTAMP       NULL,
    created_at            TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP       NOT NULL DEFAULT NOW()
);
```

**Ghi chú:**
- `approval_status` — admin duyệt tài xế trước khi họ có thể nhận chuyến
- `is_online` — tài xế chủ động bật/tắt trạng thái sẵn sàng nhận khách
- `average_rating` được cập nhật mỗi khi có rating mới (denormalized để query nhanh)
- Không soft delete `driver_profiles` — nếu xóa user thì cascade

---

### 3.5 Bảng `trips`

```sql
CREATE TABLE trips (
    id                  BIGSERIAL PRIMARY KEY,
    passenger_id        BIGINT          NOT NULL REFERENCES users(id),
    driver_id           BIGINT          REFERENCES users(id),  -- NULL khi chưa match
    status              VARCHAR(20)     NOT NULL DEFAULT 'SEARCHING',
    -- SEARCHING | ACCEPTED | ARRIVED | IN_PROGRESS | COMPLETED | CANCELLED | NO_DRIVER

    -- Điểm đón
    pickup_address      VARCHAR(300)    NOT NULL,
    pickup_location     GEOMETRY(Point, 4326) NOT NULL,  -- PostGIS

    -- Điểm trả
    dropoff_address     VARCHAR(300)    NOT NULL,
    dropoff_location    GEOMETRY(Point, 4326) NOT NULL,  -- PostGIS

    -- Giá và khoảng cách
    estimated_distance  NUMERIC(8,2),   -- km, tính khi đặt xe
    actual_distance     NUMERIC(8,2),   -- km, tính khi hoàn thành
    estimated_fare      NUMERIC(10,0),  -- VND, tính khi đặt xe
    final_fare          NUMERIC(10,0),  -- VND, tính khi hoàn thành
    pricing_config_id   BIGINT          REFERENCES pricing_config(id),

    -- Thời gian
    requested_at        TIMESTAMP       NOT NULL DEFAULT NOW(),
    accepted_at         TIMESTAMP,
    arrived_at          TIMESTAMP,
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    cancelled_at        TIMESTAMP,
    cancel_reason       VARCHAR(200),

    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP       NULL
);

-- Index địa lý để tìm chuyến đi gần khu vực
CREATE INDEX idx_trips_pickup_location  ON trips USING GIST(pickup_location);
CREATE INDEX idx_trips_dropoff_location ON trips USING GIST(dropoff_location);
CREATE INDEX idx_trips_status           ON trips(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_trips_passenger        ON trips(passenger_id);
CREATE INDEX idx_trips_driver           ON trips(driver_id);
```

---

### 3.6 Bảng `trip_status_history`

```sql
CREATE TABLE trip_status_history (
    id          BIGSERIAL PRIMARY KEY,
    trip_id     BIGINT      NOT NULL REFERENCES trips(id),
    from_status VARCHAR(20),            -- NULL nếu là trạng thái đầu tiên
    to_status   VARCHAR(20) NOT NULL,
    changed_by  BIGINT      REFERENCES users(id),  -- ai thay đổi
    note        VARCHAR(200),
    changed_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trip_status_history_trip ON trip_status_history(trip_id);
```

**Ghi chú:** Bảng này phục vụ audit log — không cập nhật, chỉ INSERT. Admin có thể xem toàn bộ hành trình trạng thái của một chuyến đi.

---

### 3.7 Bảng `ratings`

```sql
CREATE TABLE ratings (
    id              BIGSERIAL PRIMARY KEY,
    trip_id         BIGINT          NOT NULL UNIQUE REFERENCES trips(id),
    passenger_id    BIGINT          NOT NULL REFERENCES users(id),
    driver_id       BIGINT          NOT NULL REFERENCES users(id),
    score           SMALLINT        NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment         VARCHAR(500),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ratings_driver ON ratings(driver_id);
```

**Ghi chú:**
- `UNIQUE` trên `trip_id` — mỗi chuyến chỉ có 1 rating
- Khi có rating mới, trigger hoặc service cập nhật lại `driver_profiles.average_rating`

---

### 3.8 Bảng `payments`

```sql
CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    trip_id         BIGINT          NOT NULL UNIQUE REFERENCES trips(id),
    amount          NUMERIC(10,0)   NOT NULL,  -- VND
    method          VARCHAR(20)     NOT NULL DEFAULT 'CASH',  -- CASH | MOMO | VNPAY
    provider        VARCHAR(30),               -- tên provider cụ thể
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    -- PENDING | COMPLETED | FAILED | REFUNDED
    transaction_ref VARCHAR(100),              -- mã tham chiếu từ payment provider
    paid_at         TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);
```

**Ghi chú:** `transaction_ref` dùng để đối soát với MoMo/VNPay khi mở rộng sau. Với CASH thì trường này NULL.

---

### 3.9 Bảng `pricing_config`

```sql
CREATE TABLE pricing_config (
    id              BIGSERIAL PRIMARY KEY,
    vehicle_type    VARCHAR(20)     NOT NULL,  -- MOTORBIKE | CAR_4_SEAT | CAR_7_SEAT
    base_fare       NUMERIC(10,0)   NOT NULL,  -- phí mở cửa (VND)
    per_km_rate     NUMERIC(8,0)    NOT NULL,  -- giá mỗi km (VND)
    per_minute_rate NUMERIC(6,0)    NOT NULL DEFAULT 0,  -- giá mỗi phút (cho tắc đường)
    minimum_fare    NUMERIC(10,0)   NOT NULL,  -- giá tối thiểu
    surge_multiplier NUMERIC(3,1)   NOT NULL DEFAULT 1.0,  -- hệ số giờ cao điểm
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    effective_from  TIMESTAMP       NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Seed data mẫu
INSERT INTO pricing_config (vehicle_type, base_fare, per_km_rate, per_minute_rate, minimum_fare)
VALUES
    ('MOTORBIKE',  10000, 4000, 300, 15000),
    ('CAR_4_SEAT', 15000, 8000, 500, 25000),
    ('CAR_7_SEAT', 20000, 9000, 600, 30000);
```

**Công thức tính giá:**
```
fare = base_fare + (distance_km × per_km_rate) + (duration_min × per_minute_rate)
fare = MAX(fare, minimum_fare)
fare = fare × surge_multiplier
```

---

### 3.10 PostGIS — Xử lý dữ liệu địa lý

PostGIS là extension của PostgreSQL cho phép lưu và truy vấn dữ liệu địa lý hiệu quả.

**Kiểu dữ liệu:** `GEOMETRY(Point, 4326)` — lưu tọa độ theo chuẩn WGS84 (cùng hệ với GPS và Google Maps)

**Truy vấn tìm tài xế trong bán kính 5km:**
```sql
SELECT u.id, u.full_name, dp.vehicle_type, dp.average_rating,
       ST_Distance(
           ST_Transform(driver_location, 3857),
           ST_Transform(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), 3857)
       ) AS distance_meters
FROM users u
JOIN driver_profiles dp ON dp.user_id = u.id
WHERE dp.is_online = TRUE
  AND dp.approval_status = 'APPROVED'
  AND u.status = 'ACTIVE'
  AND ST_DWithin(
      ST_Transform(driver_location, 3857),
      ST_Transform(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), 3857),
      5000  -- 5000 meters
  )
ORDER BY distance_meters ASC
LIMIT 10;
```

**Lưu ý:** Driver location trong truy vấn matching lấy từ **Redis cache** (cập nhật liên tục qua WebSocket), không phải từ PostgreSQL — PostgreSQL chỉ lưu vị trí cuối cùng khi driver offline.

---

### 3.11 Index Strategy

| Index | Bảng | Lý do |
|---|---|---|
| `GIST(pickup_location)` | `trips` | Spatial query tìm chuyến gần khu vực |
| `idx_trips_status` | `trips` | Filter theo trạng thái thường xuyên |
| `idx_trips_passenger` | `trips` | Xem lịch sử chuyến của hành khách |
| `idx_trips_driver` | `trips` | Xem lịch sử chuyến của tài xế |
| `idx_users_phone` | `users` | Login bằng số điện thoại |
| `idx_ratings_driver` | `ratings` | Tính điểm trung bình tài xế |
| `idx_trip_status_history_trip` | `trip_status_history` | Xem audit log của 1 trip |

---

### 3.12 Soft Delete Strategy

Áp dụng nhất quán với cột `deleted_at TIMESTAMP NULL`:

- **Khi xóa:** `UPDATE table SET deleted_at = NOW() WHERE id = ?`
- **Khi query:** Luôn thêm `WHERE deleted_at IS NULL`
- **Index:** Tất cả index quan trọng đều có `WHERE deleted_at IS NULL` (partial index) để tránh scan bản ghi đã xóa

Với Spring Boot, dùng `@Where(clause = "deleted_at IS NULL")` trên entity để tự động filter:

```java
@Entity
@Where(clause = "deleted_at IS NULL")
public class User {
    // ...
    private LocalDateTime deletedAt;

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
```

---

### 3.13 Redis Schema

| Key Pattern | Type | Value | TTL | Mô tả |
|---|---|---|---|---|
| `drivers:online` | Geo Sorted Set | `{driverId → geohash(lat,lng)}` | — | Vị trí real-time tất cả driver đang online. Dùng `GEOADD` khi cập nhật vị trí, `ZREM` khi driver offline |
| `driver:{id}:status` | String | `ONLINE / BUSY / OFFLINE` | 60s | Trạng thái tài xế — reset TTL mỗi heartbeat |
| `driver:{id}:meta` | Hash | `{vehicleType, rating, name}` | 60s | Metadata cần thiết khi trả kết quả matching, tránh query PostgreSQL |
| `refresh_token:{userId}` | String | `<token_string>` | 7 ngày | Refresh token |
| `trip:{id}:matching` | Hash | `{"attempt":1,"lastDriverId":...}` | 5 phút | Trạng thái quá trình matching |

**Vòng đời driver trong Geo Set:**
```
Driver bật online
  → GEOADD drivers:online <lng> <lat> <driverId>
  → SET driver:{id}:status ONLINE EX 60
  → HSET driver:{id}:meta vehicleType CAR_4_SEAT rating 4.8 ...

Driver gửi vị trí mới (mỗi 3-5s)
  → GEOADD drivers:online <lng> <lat> <driverId>  ← tự động update, không tạo duplicate
  → driver:{id}:status TTL được reset bởi heartbeat

Driver tắt online / mất kết nối (TTL expire)
  → ZREM drivers:online <driverId>
  → Cập nhật last_known_location trong PostgreSQL driver_profiles
```

**Phân vai rõ ràng giữa Redis và PostgreSQL:**

| Dữ liệu | Lưu ở đâu | Tần suất ghi |
|---|---|---|
| Vị trí real-time (đang online) | Redis `drivers:online` (GEO) | Mỗi 3-5 giây |
| Vị trí cuối cùng khi offline | PostgreSQL `driver_profiles.last_known_location` | Chỉ khi driver offline |
| Lộ trình trong chuyến đi | PostgreSQL `trip_location_history` | Mỗi 3-5 giây, chỉ khi `IN_PROGRESS` |

---

### 3.14 Enum Definitions

```java
// Trip status
public enum TripStatus {
    SEARCHING, ACCEPTED, ARRIVED, IN_PROGRESS, COMPLETED, CANCELLED, NO_DRIVER
}

// Vehicle type
public enum VehicleType {
    MOTORBIKE, CAR_4_SEAT, CAR_7_SEAT
}

// Payment method
public enum PaymentMethod {
    CASH, MOMO, VNPAY  // MOMO và VNPAY cho tương lai
}

// Payment status
public enum PaymentStatus {
    PENDING, COMPLETED, FAILED, REFUNDED
}

// User role
public enum UserRole {
    PASSENGER, DRIVER, ADMIN
}

// Driver approval
public enum ApprovalStatus {
    PENDING, APPROVED, REJECTED
}
```

---

### 3.15 Quan hệ User — Driver Profile

Một tài khoản `users` có thể đóng vai trò hành khách hoặc tài xế:

```
users
 ├── role = PASSENGER  →  không có driver_profiles
 ├── role = DRIVER     →  có 1 bản ghi driver_profiles (1-1)
 └── role = ADMIN      →  không có driver_profiles
```

Thiết kế này cho phép mở rộng sau — một tài khoản vừa là hành khách vừa có thể đăng ký làm tài xế (chuyển role) mà không cần tạo tài khoản mới.

---

## 4. API Design

### 4.1 Quy ước chung

| Quy ước | Giá trị |
|---|---|
| **Base URL** | `https://api.ridesharing.app/api/v1` |
| **Content-Type** | `application/json` |
| **Auth Header** | `Authorization: Bearer <access_token>` |
| **Encoding** | UTF-8 |
| **Timestamp format** | ISO 8601 — `2024-01-15T10:30:00Z` |
| **Tiền tệ** | VND, kiểu `integer` (không dùng float) |
| **Tọa độ** | `{ "lat": 10.7769, "lng": 106.7009 }` |

---

### 4.2 Success Response Format

Mọi response thành công đều wrap trong envelope:

```json
{
  "success": true,
  "data": { },
  "message": "OK",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

Với danh sách có pagination:

```json
{
  "success": true,
  "data": {
    "items": [ ],
    "pagination": {
      "page": 1,
      "size": 20,
      "totalItems": 150,
      "totalPages": 8
    }
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

---

### 4.3 Error Response Format

```json
{
  "success": false,
  "error": {
    "code": "TRIP_NOT_FOUND",
    "message": "Chuyến đi không tồn tại",
    "details": { }
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**HTTP Status mapping:**

| HTTP Status | Ý nghĩa |
|---|---|
| `200 OK` | Thành công |
| `201 Created` | Tạo mới thành công |
| `400 Bad Request` | Dữ liệu đầu vào không hợp lệ |
| `401 Unauthorized` | Chưa xác thực hoặc token hết hạn |
| `403 Forbidden` | Không có quyền truy cập |
| `404 Not Found` | Resource không tồn tại |
| `409 Conflict` | Xung đột dữ liệu (vd: phone đã tồn tại) |
| `422 Unprocessable Entity` | Dữ liệu hợp lệ nhưng không xử lý được |
| `500 Internal Server Error` | Lỗi server |

---

### 4.4 Pagination

**Request params:**
```
GET /trips?page=1&size=20
```

| Param | Default | Mô tả |
|---|---|---|
| `page` | `1` | Trang hiện tại (bắt đầu từ 1) |
| `size` | `20` | Số item mỗi trang (tối đa 100) |
| `sort` | `created_at` | Field sắp xếp |
| `direction` | `desc` | `asc` hoặc `desc` |

---

### 4.5 Auth APIs

#### Đăng ký
```
POST /auth/register
Role: PUBLIC
```
```json
// Request
{
  "fullName": "Nguyen Van A",
  "phone": "0901234567",
  "email": "a@email.com",
  "password": "password123",
  "role": "PASSENGER"  // PASSENGER | DRIVER
}

// Response 201
{
  "userId": 1,
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "role": "PASSENGER"
}
```

#### Đăng nhập
```
POST /auth/login
Role: PUBLIC
```
```json
// Request
{
  "phone": "0901234567",
  "password": "password123"
}

// Response 200
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "role": "PASSENGER",
  "userId": 1
}
```

#### Refresh Token
```
POST /auth/refresh
Role: PUBLIC
```
```json
// Request
{ "refreshToken": "eyJ..." }

// Response 200
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ..."
}
```

#### Đăng xuất
```
POST /auth/logout
Role: PASSENGER | DRIVER | ADMIN
```
```json
// Request
{ "refreshToken": "eyJ..." }

// Response 200 — xóa refresh token khỏi Redis
```

---

### 4.6 Booking APIs

#### Tính giá ước tính
```
POST /bookings/estimate
Role: PASSENGER
```
```json
// Request
{
  "pickup": { "lat": 10.7769, "lng": 106.7009, "address": "123 Lê Lợi, Q1" },
  "dropoff": { "lat": 10.7850, "lng": 106.6800, "address": "456 CMT8, Q3" },
  "vehicleType": "CAR_4_SEAT"
}

// Response 200
{
  "estimatedDistance": 4.2,       // km
  "estimatedDuration": 18,        // phút
  "estimatedFare": 49600,         // VND
  "pricingConfigId": 1
}
```

#### Tạo chuyến đi
```
POST /bookings
Role: PASSENGER
```
```json
// Request
{
  "pickup": { "lat": 10.7769, "lng": 106.7009, "address": "123 Lê Lợi, Q1" },
  "dropoff": { "lat": 10.7850, "lng": 106.6800, "address": "456 CMT8, Q3" },
  "vehicleType": "CAR_4_SEAT",
  "paymentMethod": "CASH",
  "pricingConfigId": 1,
  "estimatedFare": 49600
}

// Response 201
{
  "tripId": 101,
  "status": "SEARCHING",
  "estimatedFare": 49600,
  "estimatedDistance": 4.2
}
// Sau khi tạo, hệ thống bắt đầu matching — kết quả trả về qua WebSocket
```

#### Xem chi tiết chuyến đi
```
GET /bookings/{tripId}
Role: PASSENGER | DRIVER (chỉ trip của mình) | ADMIN
```
```json
// Response 200
{
  "tripId": 101,
  "status": "IN_PROGRESS",
  "passenger": { "id": 1, "fullName": "Nguyen Van A", "phone": "090..." },
  "driver": {
    "id": 5,
    "fullName": "Tran Van B",
    "phone": "091...",
    "avatarUrl": "...",
    "vehiclePlate": "51A-123.45",
    "vehicleType": "CAR_4_SEAT",
    "averageRating": 4.8
  },
  "pickup": { "lat": 10.7769, "lng": 106.7009, "address": "123 Lê Lợi, Q1" },
  "dropoff": { "lat": 10.7850, "lng": 106.6800, "address": "456 CMT8, Q3" },
  "estimatedFare": 49600,
  "finalFare": null,
  "requestedAt": "2024-01-15T10:30:00Z",
  "acceptedAt": "2024-01-15T10:31:00Z"
}
```

#### Lịch sử chuyến đi
```
GET /bookings?page=1&size=20
Role: PASSENGER (lịch sử của mình) | DRIVER (lịch sử của mình)
```

#### Hủy chuyến
```
PATCH /bookings/{tripId}/cancel
Role: PASSENGER | DRIVER
```
```json
// Request
{ "reason": "Đổi ý không đi nữa" }

// Response 200
{ "tripId": 101, "status": "CANCELLED" }
```

---

### 4.7 Driver APIs

#### Bật/tắt trạng thái online
```
PATCH /drivers/me/status
Role: DRIVER
```
```json
// Request
{ "isOnline": true }

// Response 200
{ "isOnline": true, "message": "Bạn đang sẵn sàng nhận chuyến" }
```

#### Chấp nhận / từ chối chuyến
```
PATCH /drivers/trips/{tripId}/respond
Role: DRIVER
```
```json
// Request
{ "action": "ACCEPT" }  // ACCEPT | REJECT

// Response 200
{ "tripId": 101, "status": "ACCEPTED" }
```

#### Cập nhật trạng thái chuyến đi
```
PATCH /drivers/trips/{tripId}/status
Role: DRIVER
```
```json
// Request — tài xế tự cập nhật khi đến nơi, bắt đầu, kết thúc
{ "status": "ARRIVED" }   // ARRIVED | IN_PROGRESS | COMPLETED

// Response 200
{ "tripId": 101, "status": "ARRIVED" }
```

#### Lịch sử & thu nhập
```
GET /drivers/me/trips?page=1&size=20&from=2024-01-01&to=2024-01-31
Role: DRIVER
```
```json
// Response 200
{
  "items": [ ... ],
  "summary": {
    "totalTrips": 45,
    "totalEarnings": 2350000
  },
  "pagination": { ... }
}
```

---

### 4.8 Tracking APIs (REST fallback)

Dùng khi client mất kết nối WebSocket và cần lấy vị trí mới nhất:

#### Lấy vị trí hiện tại của tài xế
```
GET /tracking/trips/{tripId}/driver-location
Role: PASSENGER (chỉ khi là passenger của trip đó)
```
```json
// Response 200 — lấy từ Redis cache
{
  "driverId": 5,
  "location": { "lat": 10.7800, "lng": 106.6900 },
  "updatedAt": "2024-01-15T10:35:00Z"
}

// Response 404 nếu driver location không có trong cache (driver offline)
```

---

### 4.9 Rating APIs

#### Gửi rating
```
POST /ratings
Role: PASSENGER
```
```json
// Request
{
  "tripId": 101,
  "score": 5,
  "comment": "Tài xế lái tốt, đúng giờ"
}

// Response 201
{
  "ratingId": 55,
  "tripId": 101,
  "score": 5
}
```

#### Xem rating của tài xế
```
GET /drivers/{driverId}/ratings?page=1&size=20
Role: PUBLIC
```

---

### 4.10 Pricing APIs

#### Lấy danh sách cấu hình giá hiện tại
```
GET /pricing
Role: PUBLIC
```
```json
// Response 200
{
  "items": [
    {
      "vehicleType": "MOTORBIKE",
      "baseFare": 10000,
      "perKmRate": 4000,
      "perMinuteRate": 300,
      "minimumFare": 15000,
      "surgeMultiplier": 1.0
    },
    { "vehicleType": "CAR_4_SEAT", ... },
    { "vehicleType": "CAR_7_SEAT", ... }
  ]
}
```

---

### 4.11 User Profile APIs

#### Xem profile bản thân
```
GET /users/me
Role: PASSENGER | DRIVER | ADMIN
```

#### Cập nhật profile
```
PATCH /users/me
Role: PASSENGER | DRIVER
```
```json
// Request — chỉ gửi fields cần update
{
  "fullName": "Nguyen Van A Updated",
  "email": "newemail@example.com"
}
```

#### Upload ảnh đại diện
```
POST /users/me/avatar
Role: PASSENGER | DRIVER
Content-Type: multipart/form-data
```
```json
// Response 200
{ "avatarUrl": "https://storage.../avatar/user_1.jpg" }
```

#### Đăng ký thông tin tài xế (sau khi có tài khoản DRIVER)
```
POST /drivers/me/profile
Role: DRIVER
Content-Type: multipart/form-data
// Fields: licenseNumber, licenseExpiry, idCardNumber, vehiclePlate,
//         vehicleType, vehicleBrand, vehicleModel, vehicleColor,
//         vehicleYear, portraitImage (file)
```

---

### 4.12 Admin APIs

#### Danh sách người dùng
```
GET /admin/users?page=1&size=20&role=DRIVER&status=ACTIVE
Role: ADMIN
```

#### Duyệt / từ chối tài xế
```
PATCH /admin/drivers/{driverId}/approval
Role: ADMIN
```
```json
// Request
{
  "status": "APPROVED",  // APPROVED | REJECTED
  "note": "Hồ sơ hợp lệ"
}
```

#### Khóa / mở khóa tài khoản
```
PATCH /admin/users/{userId}/status
Role: ADMIN
```
```json
// Request
{ "status": "SUSPENDED", "reason": "Vi phạm điều khoản" }
```

#### Danh sách tất cả chuyến đi
```
GET /admin/trips?page=1&size=20&status=COMPLETED&from=2024-01-01&to=2024-01-31
Role: ADMIN
```

#### Thống kê tổng quan
```
GET /admin/stats?from=2024-01-01&to=2024-01-31
Role: ADMIN
```
```json
// Response 200
{
  "totalTrips": 1250,
  "completedTrips": 1100,
  "cancelledTrips": 120,
  "totalRevenue": 85000000,
  "activeDrivers": 45,
  "activePassengers": 320,
  "averageRating": 4.7
}
```

#### Cập nhật pricing config
```
PUT /admin/pricing/{pricingConfigId}
Role: ADMIN
```

---

### 4.13 WebSocket — Channel Definitions

Kết nối WebSocket endpoint: `wss://api.ridesharing.app/ws`

Sử dụng **STOMP over SockJS**. Client gửi JWT trong header lúc handshake:
```javascript
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);
stompClient.connect(
  { Authorization: `Bearer ${accessToken}` },
  onConnected
);
```

#### Channels — Client gửi lên Server (SEND)

| Destination | Gửi bởi | Payload | Mô tả |
|---|---|---|---|
| `/app/driver.location` | Driver | `{ lat, lng, bearing, speed }` | Gửi vị trí GPS mỗi 3-5 giây khi có chuyến |
| `/app/driver.heartbeat` | Driver | `{ driverId }` | Giữ kết nối, server reset TTL 30s |
| `/app/trip.status` | Driver | `{ tripId, status }` | Thay thế REST PATCH khi đã có WS |

#### Channels — Server gửi xuống Client (SUBSCRIBE)

| Topic | Subscribe bởi | Payload | Mô tả |
|---|---|---|---|
| `/topic/trip/{tripId}/location` | Passenger | `{ lat, lng, bearing, updatedAt }` | Vị trí tài xế real-time |
| `/topic/trip/{tripId}/status` | Passenger + Driver | `{ tripId, status, updatedAt }` | Thay đổi trạng thái chuyến |
| `/user/queue/notifications` | Passenger + Driver | `{ type, title, body, data }` | Thông báo cá nhân |
| `/topic/driver/{driverId}/request` | Driver | `{ tripId, passenger, pickup, dropoff, estimatedFare }` | Yêu cầu đặt xe mới |

#### Notification types (`/user/queue/notifications`)

| `type` | Gửi đến | Mô tả |
|---|---|---|
| `TRIP_MATCHED` | Passenger | Tìm được tài xế |
| `TRIP_ACCEPTED` | Passenger | Tài xế chấp nhận |
| `DRIVER_ARRIVED` | Passenger | Tài xế đã đến điểm đón |
| `TRIP_STARTED` | Passenger | Chuyến đi bắt đầu |
| `TRIP_COMPLETED` | Passenger + Driver | Chuyến đi kết thúc |
| `TRIP_CANCELLED` | Passenger + Driver | Chuyến bị hủy |
| `NO_DRIVER_FOUND` | Passenger | Không tìm được tài xế |
| `NEW_TRIP_REQUEST` | Driver | Có yêu cầu đặt xe mới |

---

### 4.14 WebSocket — Message Flow Diagram

```
Passenger App          Server                        Driver App
     │                   │                               │
     │──SUBSCRIBE──────▶ │ /topic/trip/101/status        │
     │──SUBSCRIBE──────▶ │ /topic/trip/101/location      │
     │──SUBSCRIBE──────▶ │ /user/queue/notifications     │
     │                   │                               │──SUBSCRIBE──▶ /user/queue/notifications
     │                   │                               │──SUBSCRIBE──▶ /topic/driver/5/request
     │                   │                               │──SEND───────▶ /app/driver.heartbeat (loop)
     │                   │                               │
     │──POST /bookings──▶│                               │
     │                   │── matching algorithm ────────▶│
     │                   │                               │
     │                   │──PUSH /topic/driver/5/request▶│ {tripId, pickup, dropoff, fare}
     │                   │                               │
     │                   │◀─PATCH /drivers/trips/101/respond─│ ACCEPT
     │                   │                               │
     │◀─PUSH /user/queue/notifications── TRIP_ACCEPTED ──│
     │◀─PUSH /topic/trip/101/status── {status:ACCEPTED}  │
     │                   │                               │
     │    [Tài xế di chuyển đến điểm đón]                │
     │                   │◀──SEND /app/driver.location───│ {lat,lng} mỗi 3s
     │◀─PUSH /topic/trip/101/location── {lat,lng} ───────│
     │                   │                               │
     │                   │◀──SEND /app/trip.status───────│ ARRIVED
     │◀─PUSH /topic/trip/101/status── {status:ARRIVED} ──│
     │◀─PUSH /user/queue/notifications── DRIVER_ARRIVED ─│
     │                   │                               │
     │    [Hành khách lên xe — tài xế bắt đầu chuyến]    │
     │                   │◀──SEND /app/trip.status───────│ IN_PROGRESS
     │◀─PUSH /topic/trip/101/status── {status:IN_PROGRESS}│
     │                   │                               │
     │    [Trong chuyến — tracking loop tiếp tục]         │
     │◀─PUSH /topic/trip/101/location (mỗi 3s) ──────────│
     │                   │                               │
     │                   │◀──SEND /app/trip.status───────│ COMPLETED
     │◀─PUSH /topic/trip/101/status── {status:COMPLETED} │
     │◀─PUSH /user/queue/notifications── TRIP_COMPLETED ─│
     │                   │── tính tiền, yêu cầu rating   │
```

---

### 4.15 Error Code Catalogue

| Error Code | HTTP | Mô tả |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Dữ liệu request không hợp lệ |
| `INVALID_CREDENTIALS` | 401 | Sai phone hoặc password |
| `TOKEN_EXPIRED` | 401 | Access token hết hạn |
| `TOKEN_INVALID` | 401 | Token không hợp lệ |
| `REFRESH_TOKEN_EXPIRED` | 401 | Refresh token hết hạn, cần đăng nhập lại |
| `FORBIDDEN` | 403 | Không có quyền thực hiện hành động này |
| `USER_NOT_FOUND` | 404 | Người dùng không tồn tại |
| `TRIP_NOT_FOUND` | 404 | Chuyến đi không tồn tại |
| `DRIVER_PROFILE_NOT_FOUND` | 404 | Tài xế chưa có hồ sơ |
| `PHONE_ALREADY_EXISTS` | 409 | Số điện thoại đã được đăng ký |
| `TRIP_ALREADY_RATED` | 409 | Chuyến đi đã được đánh giá |
| `DRIVER_NOT_APPROVED` | 422 | Tài xế chưa được duyệt |
| `DRIVER_ALREADY_ONLINE` | 422 | Tài xế đang trong trạng thái online |
| `PASSENGER_HAS_ACTIVE_TRIP` | 422 | Hành khách đang có chuyến đi chưa hoàn thành |
| `TRIP_CANNOT_BE_CANCELLED` | 422 | Trạng thái chuyến không cho phép hủy |
| `TRIP_STATUS_INVALID_TRANSITION` | 422 | Chuyển trạng thái không hợp lệ |
| `LOCATION_OUT_OF_SERVICE_AREA` | 422 | Điểm đón/trả ngoài vùng phục vụ |
| `NO_DRIVER_AVAILABLE` | 422 | Không có tài xế trong khu vực |
| `INTERNAL_SERVER_ERROR` | 500 | Lỗi server không xác định |

---

## 5. Các module chức năng

### 5.1 Auth Module

#### Trách nhiệm
Quản lý toàn bộ vòng đời xác thực: đăng ký, đăng nhập, cấp/xác thực/thu hồi JWT, phân quyền theo role.

#### Business Logic

**Đăng ký:**
1. Validate phone chưa tồn tại trong hệ thống
2. Hash password bằng BCrypt (cost factor 12)
3. Tạo bản ghi `users` với role tương ứng
4. Cấp `access_token` (JWT, TTL 15 phút) + `refresh_token` (JWT, TTL 7 ngày)
5. Lưu `refresh_token` vào Redis: `refresh_token:{userId}` với TTL 7 ngày

**Đăng nhập:**
1. Tìm user theo phone, kiểm tra `deleted_at IS NULL` và `status = ACTIVE`
2. So khớp password với BCrypt
3. Cấp token mới, ghi đè refresh token cũ trong Redis

**Refresh Token:**
1. Validate chữ ký JWT của refresh token
2. Kiểm tra token tồn tại trong Redis (chưa bị logout)
3. Cấp access token mới + rotate refresh token (xóa cũ, lưu mới)

**Đăng xuất:**
1. Xóa `refresh_token:{userId}` khỏi Redis
2. Access token vẫn hợp lệ đến khi hết hạn tự nhiên (15 phút) — chấp nhận được vì TTL ngắn

**JWT Payload:**
```json
{
  "sub": "1",
  "role": "PASSENGER",
  "iat": 1705312200,
  "exp": 1705313100
}
```

**Role Guard:** Spring Security kiểm tra role từ JWT trên mỗi request. WebSocket connection xác thực JWT trong STOMP handshake header, lưu `Principal` để phân biệt user trên từng session.

#### Flowchart — Đăng nhập & Refresh

```
[Đăng nhập]
     │
     ▼
Tìm user theo phone
     │
     ├── Không tìm thấy ──▶ 401 INVALID_CREDENTIALS
     │
     ▼
Kiểm tra status = ACTIVE?
     │
     ├── SUSPENDED ────────▶ 403 FORBIDDEN
     │
     ▼
BCrypt verify password
     │
     ├── Sai ──────────────▶ 401 INVALID_CREDENTIALS
     │
     ▼
Cấp access_token + refresh_token
Lưu refresh_token vào Redis
     │
     ▼
200 OK + tokens


[Refresh Token]
     │
     ▼
Validate JWT signature
     │
     ├── Invalid ──────────▶ 401 TOKEN_INVALID
     │
     ▼
Kiểm tra token trong Redis
     │
     ├── Không tồn tại ────▶ 401 REFRESH_TOKEN_EXPIRED
     │
     ▼
Rotate: xóa token cũ, cấp token mới
Lưu refresh_token mới vào Redis
     │
     ▼
200 OK + tokens mới
```

---

### 5.2 Booking Module

#### Trách nhiệm
Quản lý toàn bộ vòng đời một chuyến đi từ lúc đặt đến khi hoàn thành, bao gồm state machine, tính giá ước tính, lịch sử chuyến đi.

#### Trip State Machine

```
                    ┌─────────────┐
                    │  SEARCHING  │◀─── Passenger đặt xe
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
              ▼            ▼            ▼
        CANCELLED      NO_DRIVER    ACCEPTED ◀─── Driver chấp nhận
    (passenger hủy)  (hết tài xế)     │
                                       │
                                  ┌────┴─────┐
                                  │          │
                                  ▼          ▼
                               ARRIVED   CANCELLED
                           (driver đến)  (driver/passenger hủy)
                                  │
                                  ▼
                            IN_PROGRESS ◀─── Driver bắt đầu chuyến
                                  │
                                  ▼
                            COMPLETED ◀─── Driver kết thúc chuyến
```

**Quy tắc chuyển trạng thái hợp lệ:**

| Từ | Sang | Ai trigger |
|---|---|---|
| `SEARCHING` | `ACCEPTED` | System (Matching) |
| `SEARCHING` | `NO_DRIVER` | System (Matching hết retry) |
| `SEARCHING` | `CANCELLED` | Passenger |
| `ACCEPTED` | `ARRIVED` | Driver |
| `ACCEPTED` | `CANCELLED` | Passenger hoặc Driver |
| `ARRIVED` | `IN_PROGRESS` | Driver |
| `ARRIVED` | `CANCELLED` | Passenger hoặc Driver |
| `IN_PROGRESS` | `COMPLETED` | Driver |

Mọi chuyển trạng thái không nằm trong bảng trên đều bị reject với `TRIP_STATUS_INVALID_TRANSITION`.

#### Business Logic

**Tạo chuyến đi:**
1. Kiểm tra passenger không có trip đang active (`SEARCHING / ACCEPTED / ARRIVED / IN_PROGRESS`)
2. Validate pickup và dropoff nằm trong bounding box thành phố
3. Gọi Google Maps Distance Matrix API lấy `estimatedDistance` và `estimatedDuration`
4. Tính `estimatedFare` theo `pricing_config`
5. Tạo bản ghi `trips` với `status = SEARCHING`
6. Ghi `trip_status_history` (NULL → SEARCHING)
7. Publish `BookingCreatedEvent` → Matching Module xử lý bất đồng bộ

**Hủy chuyến:**
1. Kiểm tra trạng thái hiện tại cho phép hủy (SEARCHING, ACCEPTED, ARRIVED)
2. Cập nhật `status = CANCELLED`, ghi `cancelled_at` và `cancel_reason`
3. Ghi `trip_status_history`
4. Publish `TripStatusChangedEvent` → Notification Module

#### Flowchart — Tạo chuyến đi

```
Passenger gọi POST /bookings
          │
          ▼
Có active trip không?
          │
          ├── Có ──▶ 422 PASSENGER_HAS_ACTIVE_TRIP
          │
          ▼
Tọa độ trong service area?
          │
          ├── Ngoài vùng ──▶ 422 LOCATION_OUT_OF_SERVICE_AREA
          │
          ▼
Gọi Google Maps Distance Matrix
          │
          ▼
Tính estimatedFare từ pricing_config
          │
          ▼
INSERT trips (status=SEARCHING)
INSERT trip_status_history
          │
          ▼
Publish BookingCreatedEvent
          │
          ▼
201 Created {tripId, status: SEARCHING}
          │
          ▼ (bất đồng bộ)
[Matching Module tiếp nhận]
```

---

### 5.3 Matching Module

#### Trách nhiệm
Tìm tài xế phù hợp cho chuyến đi, xử lý retry khi tài xế từ chối hoặc timeout, thông báo kết quả về cho hành khách.

#### Business Logic — Retry Logic

1. Nhận `BookingCreatedEvent` từ Booking Module
2. Query Redis GEO: `GEOSEARCH drivers:online FROMLONLAT <lng> <lat> BYRADIUS 5 km ASC COUNT 20` — lấy top 20 driver gần nhất trong 1 lệnh
3. Filter danh sách: loại driver có `driver:{id}:status = BUSY`, chỉ giữ `ONLINE + AVAILABLE`
4. Dùng `DriverMatchingStrategy` sắp xếp / chọn driver tốt nhất từ danh sách đã filter
5. Lần lượt gửi request đến từng tài xế qua WebSocket, chờ tối đa **30 giây**
6. Tài xế chấp nhận → cập nhật `trip.driver_id`, `trip.status = ACCEPTED`, set `driver:{id}:status = BUSY`, kết thúc
7. Tài xế từ chối hoặc timeout → thử tài xế tiếp theo
8. Sau **3 lần thất bại** → `trip.status = NO_DRIVER`, notify passenger

**Trạng thái tài xế trong Redis:**
- `ONLINE + AVAILABLE` → có thể nhận chuyến
- `ONLINE + BUSY` → đang trong chuyến, bỏ qua
- `OFFLINE` → key đã expire, bỏ qua

#### Flowchart — Matching Retry Logic

```
Nhận BookingCreatedEvent {tripId, pickupLocation}
               │
               ▼
Query top 10 driver gần nhất (PostGIS + Redis)
               │
               ├── Không có driver nào ──▶ Trip: NO_DRIVER
               │                           Notify passenger
               ▼
attempt = 1, driverQueue = [d1, d2, d3, ...]
               │
         ┌─────▼─────┐
         │ Lấy driver │
         │  tiếp theo │
         └─────┬─────┘
               │
               ├── Hết driver trong queue ──▶ Trip: NO_DRIVER
               │                              Notify passenger
               ▼
Đánh dấu driver BUSY trong Redis
Gửi request qua WebSocket /topic/driver/{id}/request
Lưu matching state vào Redis: trip:{id}:matching
               │
               ▼
         Chờ tối đa 30s
               │
        ┌──────┴──────┐
        │             │
      ACCEPT        REJECT / TIMEOUT
        │             │
        ▼             ▼
Trip: ACCEPTED    attempt += 1
Driver: BUSY      Reset driver status: AVAILABLE
Notify passenger  │
Kết thúc          ├── attempt <= 3 ──▶ [Lấy driver tiếp theo]
                  │
                  └── attempt > 3 ───▶ Trip: NO_DRIVER
                                       Notify passenger
```

---

### 5.4 Tracking Module

#### Trách nhiệm
Nhận vị trí GPS từ tài xế qua WebSocket, cache vào Redis, broadcast đến hành khách, lưu lịch sử tọa độ để vẽ lại lộ trình, quản lý driver heartbeat.

#### Business Logic

**Nhận vị trí từ Driver:**
1. Driver gửi `{ lat, lng, bearing, speed }` lên `/app/driver.location` mỗi 3-5 giây
2. Server cập nhật Redis GEO: `GEOADD drivers:online <lng> <lat> <driverId>`
3. Nếu trip đang `IN_PROGRESS`: lưu tọa độ vào bảng `trip_location_history` (PostgreSQL)
4. Broadcast vị trí đến passenger qua `/topic/trip/{tripId}/location`

**Heartbeat:**
1. Driver gửi heartbeat lên `/app/driver.heartbeat` mỗi 10 giây
2. Server reset TTL của `driver:{id}:status` và `driver:{id}:meta`
3. Nếu không nhận heartbeat sau 60s → TTL expire → driver bị đánh dấu `OFFLINE`
4. Server chạy scheduled check: nếu `driver:{id}:status` expire mà driver đang có trip `IN_PROGRESS` → notify passenger, chờ reconnect
5. Khi driver offline: `ZREM drivers:online <driverId>`, cập nhật `last_known_location` vào PostgreSQL

**Vẽ lại lộ trình sau chuyến:**
- Khi `status = COMPLETED`, API trả về danh sách tọa độ từ `trip_location_history`
- Dữ liệu giữ 3 ngày rồi xóa (scheduled job chạy hàng ngày)

**Bảng lưu lịch sử tọa độ (bổ sung vào Database):**
```sql
CREATE TABLE trip_location_history (
    id          BIGSERIAL PRIMARY KEY,
    trip_id     BIGINT      NOT NULL REFERENCES trips(id),
    location    GEOMETRY(Point, 4326) NOT NULL,
    recorded_at TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_trip_location_trip ON trip_location_history(trip_id);
-- Scheduled job: DELETE WHERE recorded_at < NOW() - INTERVAL '3 days'
```

#### Flowchart — Tracking Loop

```
Driver kết nối WebSocket
          │
          ▼
Xác thực JWT trong STOMP handshake
          │
          ├── Fail ──▶ Disconnect
          │
          ▼
Đăng ký session cho driverId
          │
          ▼  (loop mỗi 3-5s khi đang có chuyến)
Driver SEND /app/driver.location {lat, lng}
          │
          ▼
Cập nhật Redis driver:{id}:location (TTL 30s)
          │
          ▼
Trip đang IN_PROGRESS?
          │
          ├── Có ──▶ INSERT trip_location_history
          │          PUBLISH /topic/trip/{tripId}/location
          │
          └── Không ──▶ Chỉ update Redis, không broadcast


[Heartbeat check — chạy mỗi 30s phía server]
          │
          ▼
Redis key driver:{id}:location còn tồn tại?
          │
          ├── Có ──▶ Driver vẫn online, tiếp tục
          │
          └── Không ──▶ Đánh dấu driver OFFLINE
                         │
                         ▼
                   Đang có trip IN_PROGRESS?
                         │
                         ├── Có ──▶ Notify passenger
                         │          "Tài xế mất kết nối"
                         │          Chờ reconnect 60s
                         │
                         └── Không ──▶ Kết thúc
```

---

### 5.5 Payment Module

#### Trách nhiệm
Tính giá cuối dựa trên dữ liệu thực tế, ghi nhận thanh toán, cung cấp abstraction layer để mở rộng phương thức thanh toán sau.

#### Business Logic

**Tính giá cuối (khi trip COMPLETED):**
1. Lấy `actual_distance` từ tổng quãng đường trong `trip_location_history`
2. Tính `actual_duration` = `completed_at - started_at`
3. Lấy `pricing_config` được gắn với trip
4. Áp dụng công thức:
```
fare = base_fare + (actual_distance × per_km_rate) + (actual_duration_min × per_minute_rate)
fare = MAX(fare, minimum_fare)
fare = ROUND(fare × surge_multiplier, -3)  // làm tròn đến nghìn đồng
```
5. Cập nhật `trips.final_fare` và `trips.actual_distance`
6. Tạo bản ghi `payments` với `status = PENDING`

**Ghi nhận thanh toán CASH:**
1. Hành khách trả tiền mặt trực tiếp cho tài xế
2. Driver xác nhận nhận tiền → `payments.status = COMPLETED`, `paid_at = NOW()`
3. Publish `PaymentCompletedEvent` → Notification Module

**Abstraction Layer:**
```java
// Thêm phương thức mới chỉ cần implement interface, không sửa PaymentService
public interface PaymentProvider {
    PaymentResult process(PaymentRequest request);
    boolean supports(String method);
}
```

#### Flowchart — Tính giá và thanh toán

```
Nhận TripCompletedEvent {tripId}
          │
          ▼
Tính actual_distance từ trip_location_history
Tính actual_duration từ started_at → completed_at
          │
          ▼
Lấy pricing_config của trip
Áp dụng công thức tính giá
          │
          ▼
UPDATE trips SET final_fare, actual_distance
INSERT payments (status=PENDING, method=CASH)
          │
          ▼
Notify passenger + driver: "Tổng tiền: {fare} VND"
          │
          ▼  (Driver xác nhận nhận tiền mặt)
PATCH /drivers/trips/{tripId}/payment-confirm
          │
          ▼
UPDATE payments SET status=COMPLETED, paid_at=NOW()
Publish PaymentCompletedEvent
          │
          ▼
Notify cả 2: "Thanh toán hoàn tất"
Yêu cầu passenger đánh giá chuyến đi
```

---

### 5.6 Notification Module

#### Trách nhiệm
Lắng nghe các domain event, gửi push notification qua Firebase FCM đến đúng đối tượng, thiết kế cho phép mở rộng sang in-app notification sau.

#### Business Logic

**Đăng ký FCM Token:**
- Khi user đăng nhập, app gửi FCM device token lên server
- Server lưu token vào Redis: `fcm_token:{userId}` (cập nhật mỗi lần login)

**Xử lý Event → Notification:**

| Event nhận | Gửi đến | Nội dung |
|---|---|---|
| `TripMatchedEvent` | Passenger | "Đang tìm tài xế cho bạn..." |
| `TripStatusChangedEvent` (ACCEPTED) | Passenger | "Tài xế đang đến đón bạn" |
| `TripStatusChangedEvent` (ARRIVED) | Passenger | "Tài xế đã đến điểm đón" |
| `TripStatusChangedEvent` (IN_PROGRESS) | Passenger | "Chuyến đi bắt đầu" |
| `TripCompletedEvent` | Passenger + Driver | "Chuyến đi hoàn thành" |
| `TripStatusChangedEvent` (CANCELLED) | Passenger + Driver | "Chuyến đi đã bị hủy" |
| `PaymentCompletedEvent` | Passenger + Driver | "Thanh toán hoàn tất" |
| `BookingCreatedEvent` | Driver | "Có yêu cầu đặt xe mới" |

**Extensibility — In-app Notification:**
```java
public interface NotificationChannel {
    void send(NotificationMessage message);
    String getChannelName();
}

@Component
public class FcmNotificationChannel implements NotificationChannel { ... }

// Tương lai — thêm không sửa NotificationService
@Component
public class InAppNotificationChannel implements NotificationChannel { ... }
```

#### Flowchart — Gửi Notification

```
Nhận domain event (vd: TripStatusChangedEvent)
          │
          ▼
Xác định đối tượng nhận (passenger / driver / cả 2)
          │
          ▼
Lấy FCM token từ Redis: fcm_token:{userId}
          │
          ├── Không có token ──▶ Log warning, bỏ qua
          │                      (user chưa login hoặc token hết hạn)
          ▼
Tạo NotificationMessage {title, body, data}
          │
          ▼
Gửi qua FCM API
          │
          ├── FCM lỗi / token invalid ──▶ Log error, retry 1 lần
          │
          ▼
Đồng thời: Push qua WebSocket /user/queue/notifications
(để hiển thị real-time nếu app đang mở)
          │
          ▼
Hoàn tất
```

---

### 5.7 Admin Module

#### Trách nhiệm
Cung cấp giao diện quản trị cho admin: duyệt tài xế, quản lý tài khoản, xem thống kê hệ thống, điều chỉnh cấu hình giá.

#### Business Logic

**Duyệt tài xế:**
1. Admin xem danh sách driver có `approval_status = PENDING`
2. Kiểm tra hồ sơ: ảnh chân dung, CCCD, bằng lái, biển số
3. Chấp nhận → `approval_status = APPROVED`, notify driver qua FCM
4. Từ chối → `approval_status = REJECTED`, ghi note lý do, notify driver

**Khóa tài khoản:**
1. Admin set `users.status = SUSPENDED`
2. Nếu user đang có trip active → hủy trip, notify đối phương
3. Refresh token bị xóa khỏi Redis → user bị đăng xuất ngay lập tức
4. Mọi request tiếp theo với access token hiện tại → 403 (kiểm tra status trong JWT filter)

**Thống kê:**
- Tổng chuyến đi / hoàn thành / hủy trong khoảng thời gian
- Tổng doanh thu (từ `payments.amount` where `status = COMPLETED`)
- Tài xế active, hành khách active
- Rating trung bình toàn hệ thống

**Cập nhật Pricing Config:**
1. Admin chỉnh `base_fare`, `per_km_rate`... qua Web Admin
2. Tạo bản ghi `pricing_config` mới (không sửa bản cũ — giữ lịch sử)
3. Set `is_active = FALSE` cho config cũ
4. Trip mới sẽ dùng config active mới nhất

#### Flowchart — Duyệt tài xế

```
Admin mở danh sách driver PENDING
          │
          ▼
Chọn driver, xem chi tiết hồ sơ
(CCCD, bằng lái, ảnh, biển số xe)
          │
          ▼
     Quyết định?
    ┌─────┴─────┐
    │           │
 APPROVE     REJECT
    │           │
    ▼           ▼
UPDATE       UPDATE
approval_    approval_
status=      status=
APPROVED     REJECTED
    │           │
    ▼           ▼
Notify       Notify driver
driver FCM   FCM + lý do
"Hồ sơ      "Hồ sơ bị từ
được duyệt"  chối: {note}"
    │
    ▼
Driver có thể
bật ONLINE
nhận chuyến
```

---

## 6. Khả năng mở rộng

### 6.1 Triết lý thiết kế

Hệ thống được xây dựng theo nguyên tắc **Open/Closed Principle** — mở để mở rộng, đóng để sửa đổi. Các điểm mở rộng được thiết kế có chủ đích ngay từ MVP:

| Cơ chế | Áp dụng cho |
|---|---|
| **Strategy Pattern** | Matching algorithm |
| **Provider Pattern** | Payment method |
| **Channel Pattern** | Notification channel |
| **Event-driven** | Giao tiếp giữa module, dễ thêm subscriber mới |
| **Config-driven** | Pricing, geographic boundary — thay đổi qua DB, không cần deploy |
| **Modular boundaries** | Mỗi module độc lập, dễ tách thành service riêng |

---

### 6.2 Payment — Thêm MoMo / VNPay

**Hiện tại:** `CashPaymentProvider` xử lý thanh toán tiền mặt.

**Mở rộng:** Thêm provider mới chỉ cần implement interface, không sửa bất kỳ business logic nào:

```
PaymentProvider (interface)
├── CashPaymentProvider      ✅ MVP
├── MoMoPaymentProvider      🔜 Tương lai
└── VNPayPaymentProvider     🔜 Tương lai
```

**Những gì cần thêm khi triển khai:**
- Implement `MoMoPaymentProvider` — tích hợp MoMo Payment API
- Thêm webhook endpoint nhận callback từ MoMo/VNPay
- UI cho passenger chọn phương thức thanh toán khi đặt xe

**Những gì không cần sửa:** `PaymentService`, `BookingModule`, toàn bộ trip flow.

---

### 6.3 Matching Algorithm — Đổi thuật toán

**Hiện tại:** `NearestDriverStrategy` — tìm tài xế gần nhất theo khoảng cách.

**Mở rộng:** Thêm strategy mới không đụng đến Matching Service:

```
DriverMatchingStrategy (interface)
├── NearestDriverStrategy         ✅ MVP
├── RatingWeightedStrategy        🔜 Kết hợp khoảng cách + rating
├── SmartMatchStrategy            🔜 Khoảng cách + rating + tỷ lệ chấp nhận
└── MLBasedStrategy               🔜 Dự đoán bằng ML (cần dữ liệu lớn)
```

**Switching strategy:** Cấu hình strategy active qua `application.yml` — không cần sửa code, chỉ cần restart.

---

### 6.4 Notification — Thêm In-app Notification

**Hiện tại:** FCM push notification.

**Mở rộng:** Thêm channel mới không ảnh hưởng logic hiện tại:

```
NotificationChannel (interface)
├── FcmNotificationChannel        ✅ MVP
├── InAppNotificationChannel      🔜 Lưu DB, hiển thị trong app
└── EmailNotificationChannel      🔜 Thông báo qua email
```

**Những gì cần thêm khi triển khai in-app:**
- Bảng `notifications` trong DB (userId, type, title, body, isRead, createdAt)
- API `GET /notifications` và `PATCH /notifications/{id}/read`
- Badge count trên app icon

---

### 6.5 Messaging — Nhắn tin Passenger ↔ Driver

**Mô tả:** Cho phép hành khách và tài xế nhắn tin trong chuyến đi.

**Thiết kế đề xuất:**
- Thêm WebSocket channel: `/topic/trip/{tripId}/chat`
- Bảng `trip_messages` (tripId, senderId, content, sentAt)
- Chỉ cho phép nhắn tin khi trip đang `ACCEPTED / ARRIVED / IN_PROGRESS`
- Giữ lịch sử tin nhắn 7 ngày

**Tại sao dễ thêm:** WebSocket infrastructure đã có sẵn, chỉ cần thêm channel mới và bảng DB — không ảnh hưởng module khác.

---

### 6.6 Surge Pricing — Giá động

**Mô tả:** Tăng giá tự động trong giờ cao điểm hoặc khu vực đông nhu cầu.

**Thiết kế đề xuất:**
- Cột `surge_multiplier` trong `pricing_config` đã có sẵn (hiện để 1.0)
- Scheduled job chạy mỗi 5 phút: tính tỷ lệ demand/supply theo khu vực
- Nếu số trip SEARCHING / số driver ONLINE vượt ngưỡng → tăng multiplier
- Passenger thấy cảnh báo "Giá cao hơn bình thường" trước khi xác nhận

**Tại sao dễ thêm:** Công thức tính giá đã nhân `surge_multiplier` — chỉ cần thêm logic cập nhật giá trị này.

---

### 6.7 Scheduled Rides — Đặt xe trước

**Mô tả:** Hành khách đặt xe trước theo giờ hẹn.

**Thiết kế đề xuất:**
- Thêm cột `scheduled_at` vào bảng `trips`
- Scheduled job (Spring `@Scheduled`) quét trip có `scheduled_at` trong 15 phút tới
- Kích hoạt Matching Module như chuyến đặt thông thường

**Tại sao dễ thêm:** Matching flow không thay đổi — chỉ thêm trigger theo thời gian thay vì trigger ngay lập tức.

---

### 6.8 Multi-city — Mở rộng nhiều thành phố

**Hiện tại:** Bounding box một thành phố trong `application.yml`.

**Mở rộng:**
- Chuyển cấu hình địa lý vào DB — bảng `service_areas`
- Mỗi trip gắn với một `service_area_id`
- Driver đăng ký hoạt động tại một hoặc nhiều thành phố
- Matching chỉ tìm driver trong cùng service area

**Những gì không thay đổi:** Toàn bộ Matching, Tracking, Booking flow — chỉ thêm filter `service_area_id`.

---

### 6.9 Rating mở rộng

**Hiện tại:** Passenger rate driver, score 1-5 và comment.

**Mở rộng có thể thêm:**
- **Driver rate passenger** — thêm chiều ngược lại vào bảng `ratings`
- **Tag đánh giá** — bảng `rating_tags` (Lái xe an toàn, Đúng giờ, Thân thiện, Xe sạch...) — passenger chọn tag thay vì chỉ gõ comment
- **Weighted rating** — chuyến gần đây có trọng số cao hơn khi tính `average_rating`

---

### 6.10 Analytics Dashboard

**Hiện tại:** Thống kê cơ bản trong Admin Module.

**Mở rộng:**
- **Heatmap** khu vực đông nhu cầu theo giờ — giúp điều phối tài xế
- **Biểu đồ doanh thu** theo ngày/tuần/tháng
- **Driver performance** — tỷ lệ chấp nhận, tổng chuyến, doanh thu từng tài xế
- **Thời gian chờ trung bình** — metric đánh giá chất lượng dịch vụ

**Công nghệ đề xuất khi mở rộng:** Đổ dữ liệu sang data warehouse (BigQuery/ClickHouse) để query nhanh mà không ảnh hưởng DB chính.

---

### 6.11 Microservice Migration Path

Khi hệ thống phát triển và team lớn hơn, Modular Monolith có thể tách dần theo thứ tự:

```
Giai đoạn 1 — Tách service có traffic cao nhất
└── Tracking Service (WebSocket + Redis — stateful, cần scale độc lập)

Giai đoạn 2 — Tách service có domain rõ ràng
├── Notification Service (scale độc lập, thêm channel dễ)
└── Payment Service (security boundary rõ ràng)

Giai đoạn 3 — Core services
├── Booking Service
└── Matching Service
    (thay Spring Events bằng Kafka/RabbitMQ)
```

**Điều kiện nên tách:** Khi một module trở thành bottleneck rõ ràng, hoặc team đủ lớn để quản lý nhiều service. Với quy mô đồ án, Modular Monolith là lựa chọn phù hợp nhất.

---

### 6.12 Bảng tổng hợp

| Tính năng | Trạng thái | Effort | Ghi chú |
|---|---|---|---|
| Thanh toán tiền mặt | ✅ MVP | — | Hoàn chỉnh |
| Nearest driver matching | ✅ MVP | — | Hoàn chỉnh |
| FCM Push Notification | ✅ MVP | — | Hoàn chỉnh |
| Real-time tracking | ✅ MVP | — | Hoàn chỉnh |
| Rating passenger → driver | ✅ MVP | — | Hoàn chỉnh |
| Lịch sử chuyến đi | ✅ MVP | — | Hoàn chỉnh |
| Thanh toán MoMo/VNPay | 🔜 Tương lai | Trung bình | Interface đã có sẵn |
| In-app Notification | 🔜 Tương lai | Thấp | Channel pattern đã có |
| Messaging trong chuyến | 🔜 Tương lai | Thấp | WebSocket đã có |
| Surge Pricing | 🔜 Tương lai | Thấp | `surge_multiplier` đã có |
| Đặt xe trước | 🔜 Tương lai | Trung bình | Thêm scheduled job |
| Rating mở rộng | 🔜 Tương lai | Thấp | Mở rộng schema đơn giản |
| Multi-city | 🔜 Tương lai | Trung bình | Refactor service area |
| Matching algorithm nâng cao | 🔜 Tương lai | Cao | Cần dữ liệu lịch sử |
| Analytics Dashboard | 🔜 Tương lai | Cao | Cần data pipeline riêng |
| Microservice migration | 🔜 Tương lai | Rất cao | Khi scale thực sự cần |

---

## 7. Kế hoạch triển khai

### 7.1 Tổng quan môi trường

| | Local | Production |
|---|---|---|
| **Mục đích** | Phát triển, debug, test | Demo, bảo vệ đồ án |
| **Backend** | Docker Compose | AWS EC2 / GCP Cloud Run |
| **Database** | PostgreSQL local (Docker) | AWS RDS / Cloud SQL |
| **Redis** | Redis local (Docker) | AWS ElastiCache / Cloud Memorystore |
| **Storage (ảnh)** | Local filesystem | AWS S3 / GCP Cloud Storage |
| **Config** | `application-local.yml` | Environment variables (Secrets) |
| **Domain** | `localhost:8080` | `api.ridesharing.app` (HTTPS) |

---

### 7.2 Cấu hình Local — Docker Compose

Toàn bộ stack chạy local bằng một lệnh `docker compose up`:

```yaml
# docker-compose.yml
version: '3.8'

services:
  postgres:
    image: postgis/postgis:15-3.3
    environment:
      POSTGRES_DB: ridesharing
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: local
      DB_URL: jdbc:postgresql://postgres:5432/ridesharing
      DB_USERNAME: postgres
      DB_PASSWORD: postgres
      REDIS_HOST: redis
      REDIS_PORT: 6379
      JWT_SECRET: ${JWT_SECRET}
      GOOGLE_MAPS_API_KEY: ${GOOGLE_MAPS_API_KEY}
      FIREBASE_CONFIG: ${FIREBASE_CONFIG}
    depends_on:
      - postgres
      - redis

volumes:
  postgres_data:
```

**Khởi động local:**
```bash
# Copy file env mẫu
cp .env.example .env
# Điền API keys vào .env

# Chạy toàn bộ stack
docker compose up -d

# Xem logs backend
docker compose logs -f backend
```

---

### 7.3 Cấu hình Production — AWS

**Kiến trúc AWS đề xuất:**

```
Internet
    │
    ▼
Route 53 (DNS)
    │
    ▼
Application Load Balancer (HTTPS)
    │
    ▼
EC2 Instance (t3.small — đủ cho demo)
├── Docker: Spring Boot backend
│
├── RDS PostgreSQL (db.t3.micro) + PostGIS extension
│
├── ElastiCache Redis (cache.t3.micro)
│
└── S3 Bucket (lưu ảnh avatar, ảnh tài xế)
```

**Lý do chọn AWS thay GCP:** AWS Free Tier 12 tháng bao gồm EC2 t2.micro, RDS, S3 — đủ cho môi trường demo đồ án không tốn phí.

**Biến môi trường production** được lưu trong **AWS Secrets Manager** — không hardcode trong code hay Dockerfile:

```
DB_URL, DB_USERNAME, DB_PASSWORD
REDIS_HOST, REDIS_PORT
JWT_SECRET
GOOGLE_MAPS_API_KEY
FIREBASE_CONFIG
AWS_S3_BUCKET
```

**SSL/HTTPS:** Dùng AWS Certificate Manager (free) gắn vào Load Balancer.

---

### 7.4 CI/CD Pipeline — GitHub Actions

Mỗi khi push lên `main` branch, GitHub Actions tự động build, test và deploy:

```
Push to main
     │
     ▼
┌─────────────────────────────────┐
│  Job 1: Test                    │
│  ├── Checkout code              │
│  ├── Setup Java 17              │
│  ├── Run unit tests (Maven)     │
│  └── Run integration tests      │
└─────────────┬───────────────────┘
              │ Pass
              ▼
┌─────────────────────────────────┐
│  Job 2: Build & Push Image      │
│  ├── Build Docker image         │
│  ├── Tag: ghcr.io/{repo}:main  │
│  └── Push to GitHub Container   │
│      Registry (GHCR)            │
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│  Job 3: Deploy to AWS EC2       │
│  ├── SSH vào EC2                │
│  ├── Pull image mới từ GHCR     │
│  ├── docker compose pull        │
│  └── docker compose up -d       │
└─────────────────────────────────┘
```

**GitHub Actions workflow:**
```yaml
# .github/workflows/deploy.yml
name: CI/CD Pipeline

on:
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run tests
        run: mvn test

  build-and-push:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Login to GHCR
        uses: docker/login-action@v2
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Build and push
        uses: docker/build-push-action@v4
        with:
          push: true
          tags: ghcr.io/${{ github.repository }}:main

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to EC2
        uses: appleboy/ssh-action@v0.1.10
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ec2-user
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            cd /app
            docker compose pull
            docker compose up -d
```

---

### 7.5 Chiến lược kiểm thử

Hệ thống áp dụng **Testing Pyramid** — nhiều unit test, ít integration test, manual test cho happy path:

```
        /\
       /  \
      / E2E\        ← Manual test — happy path trước demo
     /──────\
    /Integrat\      ← Integration test — API + DB
   /──────────\
  /  Unit Test  \   ← Unit test — business logic từng module
 /──────────────\
```

**Unit Test (JUnit 5 + Mockito):**

| Module | Những gì cần test |
|---|---|
| Auth | JWT generation/validation, BCrypt, role guard |
| Booking | State machine transitions, validate tọa độ, tính giá |
| Matching | Retry logic 3 lần, timeout handling, NO_DRIVER case |
| Payment | Công thức tính giá, làm tròn, payment provider selection |
| Tracking | Heartbeat timeout, location update, broadcast logic |

**Integration Test (Spring Boot Test + Testcontainers):**
- Dùng **Testcontainers** để spin up PostgreSQL + PostGIS + Redis thật trong test
- Test các flow quan trọng end-to-end qua REST API:
    - Happy path: Đặt xe → Match → Tracking → Hoàn thành → Rating
    - Edge case: Tài xế từ chối 3 lần → NO_DRIVER
    - Edge case: Hủy chuyến ở từng trạng thái

**Manual Test (trước demo):**
- Chạy 2 app mobile (Passenger + Driver) trên thiết bị thật hoặc emulator
- Test toàn bộ happy path end-to-end
- Test kịch bản mất mạng giữa chừng (tắt WiFi rồi bật lại)
- Test trên cả iOS và Android

---

### 7.6 Test Coverage mục tiêu

| Module | Coverage mục tiêu | Ưu tiên |
|---|---|---|
| Booking (state machine) | ≥ 80% | 🔴 Cao |
| Matching (retry logic) | ≥ 80% | 🔴 Cao |
| Auth (JWT, security) | ≥ 80% | 🔴 Cao |
| Payment (tính giá) | ≥ 75% | 🟡 Trung bình |
| Tracking | ≥ 60% | 🟡 Trung bình |
| Notification | ≥ 50% | 🟢 Thấp |
| Admin | ≥ 50% | 🟢 Thấp |

**Lý do ưu tiên:** Booking, Matching và Auth là core flow — lỗi ở đây ảnh hưởng toàn bộ hệ thống. Payment cần test kỹ vì liên quan đến tiền.

---

### 7.7 Timeline 3 tháng

**Tháng 1 — Foundation & Core Backend**

| Tuần | Việc cần làm |
|---|---|
| Tuần 1 | Setup project: Spring Boot skeleton, Docker Compose, GitHub repo, CI pipeline cơ bản |
| Tuần 2 | Auth Module hoàn chỉnh (đăng ký, đăng nhập, JWT, role guard) + unit test |
| Tuần 3 | Database schema, Booking Module (tạo trip, state machine, lịch sử) |
| Tuần 4 | Matching Module (nearest driver, retry logic, Strategy Pattern) |

**Tháng 2 — Real-time & Mobile**

| Tuần | Việc cần làm |
|---|---|
| Tuần 5 | Tracking Module (WebSocket STOMP, Redis cache, heartbeat) |
| Tuần 6 | Payment Module + Notification Module (FCM) + integration test core flow |
| Tuần 7 | React Native — Passenger App (đặt xe, tracking, lịch sử, rating) |
| Tuần 8 | React Native — Driver App (nhận/từ chối chuyến, cập nhật trạng thái, tracking GPS) |

**Tháng 3 — Admin, Polish & Deploy**

| Tuần | Việc cần làm |
|---|---|
| Tuần 9 | Web Admin (React) — duyệt tài xế, quản lý user, thống kê |
| Tuần 10 | Deploy lên AWS, cấu hình CD pipeline, HTTPS, domain |
| Tuần 11 | End-to-end testing, fix bug, polish UI |
| Tuần 12 | Buffer — fix bug phát sinh, chuẩn bị demo, hoàn thiện tài liệu |

---

### 7.8 Rủi ro và kế hoạch dự phòng

| Rủi ro | Khả năng | Ảnh hưởng | Dự phòng |
|---|---|---|---|
| WebSocket phức tạp hơn dự kiến | Cao | Cao | Dùng polling REST làm fallback tạm thời, triển khai WS sau |
| Google Maps API tốn phí vượt free tier | Trung bình | Trung bình | Giới hạn call API trong dev, dùng mock data khi test |
| React Native build lỗi trên iOS/Android | Trung bình | Trung bình | Ưu tiên 1 platform trước (Android), iOS sau |
| AWS config phức tạp, tốn thời gian | Cao | Trung bình | Fallback về Railway/Render nếu AWS mất quá nhiều thời gian |
| Tính năng bị trễ so với timeline | Cao | Thấp | Buffer tuần 12 — cắt Admin Module nếu cần, core flow phải xong |
| PostGIS setup khó | Thấp | Cao | Dùng image `postgis/postgis` có sẵn, không tự cài |

**Nguyên tắc ưu tiên khi trễ:**
1. Core flow phải hoàn chỉnh: Đặt xe → Match → Tracking → Hoàn thành
2. Cắt Admin Module nếu thiếu thời gian (có thể demo qua Swagger/Postman)
3. Cắt Web Admin trước khi cắt bất kỳ tính năng mobile nào