# Kế hoạch triển khai phân hệ Phân tích Dữ liệu Quản trị

> Tên tiếng Anh: **Admin Analytics Subsystem**
>
> Trọng tâm: **Spatio-Temporal Demand Analytics and Driver-Matching Performance Evaluation**
>
> Ngày lập kế hoạch: 2026-07-26
>
> Trạng thái: Đang triển khai theo phase trên nhánh `codex/admin-v2`; Phase 0 là checkpoint hiện tại.

## Hồ sơ thực thi theo phase

| Phase | Hồ sơ thực thi | Review gate |
| --- | --- | --- |
| 0 | [`phase-00-contracts-and-architecture.md`](admin-analytics-phases/phase-00-contracts-and-architecture.md) | Metric, ADR, API draft và benchmark protocol được duyệt |
| 1 | [`phase-01-telemetry-schema.md`](admin-analytics-phases/phase-01-telemetry-schema.md) | Schema/release SQL và repository tests được duyệt |
| 2 | [`phase-02-matching-instrumentation.md`](admin-analytics-phases/phase-02-matching-instrumentation.md) | Matching telemetry đầy đủ và idempotent |
| 3 | [`phase-03-direct-analytics-api.md`](admin-analytics-phases/phase-03-direct-analytics-api.md) | Direct-query correctness baseline được duyệt |
| 4 | [`phase-04-spatial-and-supply-analytics.md`](admin-analytics-phases/phase-04-spatial-and-supply-analytics.md) | PostGIS/supply correctness và query plan được duyệt |
| 5 | [`phase-05-materialized-read-models.md`](admin-analytics-phases/phase-05-materialized-read-models.md) | Direct/materialized equivalence và refresh semantics được duyệt |
| 6 | [`phase-06-api-hardening-and-handoff.md`](admin-analytics-phases/phase-06-api-hardening-and-handoff.md) | Backend contract ổn định để bàn giao frontend |
| 7 | [`phase-07-dataset-and-benchmark.md`](admin-analytics-phases/phase-07-dataset-and-benchmark.md) | Dataset và raw benchmark artifacts tái lập được |
| 8 | [`phase-08-hardening-and-thesis-artifacts.md`](admin-analytics-phases/phase-08-hardening-and-thesis-artifacts.md) | Full backend review và thesis traceability hoàn tất |

---

## 1. Mục tiêu

Xây dựng một phân hệ phân tích dữ liệu dành cho quản trị viên, có khả năng:

1. Theo dõi nhu cầu, nguồn cung tài xế, chuyến đi và doanh thu theo thời gian.
2. Phân tích phân bố nhu cầu theo không gian bằng PostgreSQL/PostGIS.
3. Ghi nhận đầy đủ quá trình ghép tài xế thay vì chỉ lưu trạng thái cuối của chuyến.
4. Đánh giá hiệu quả ghép tài xế bằng các chỉ số có định nghĩa rõ ràng và có thể tái lập.
5. So sánh hiệu năng giữa truy vấn trực tiếp trên dữ liệu giao dịch và truy vấn qua materialized view/read model.
6. Cung cấp API và giao diện Admin phục vụ quan sát, lọc, drill-down và trình bày kết quả luận văn.

Kết quả cần đạt không chỉ là một dashboard, mà là một chuỗi xử lý dữ liệu hoàn chỉnh:

```text
Operational events
    -> persistent matching telemetry
    -> validated analytical metrics
    -> direct-query baseline
    -> materialized analytical read models
    -> Admin APIs
    -> charts, funnel and spatial heatmap
    -> reproducible benchmark report
```

---

## 2. Hiện trạng làm nền

Backend hiện tại đã có:

- Spring Boot 3.5.13, Java 17, kiến trúc modular monolith.
- PostgreSQL/PostGIS là nguồn dữ liệu bền vững.
- Redis GEO phục vụ tìm tài xế và trạng thái matching thời gian thực.
- Các luồng booking, matching, tracking, payment, rating và admin cơ bản.
- `GET /api/v1/admin/dashboard` cung cấp số liệu tổng hợp toàn cục:
  - người dùng;
  - tài xế;
  - chuyến theo trạng thái;
  - thanh toán hoàn tất;
  - doanh thu hoàn tất;
  - điểm đánh giá tài xế trung bình.
- Admin có API quản lý user, duyệt tài xế, pricing, service area và giám sát chuyến.
- Matching hiện lưu trạng thái tạm thời trong Redis và xử lý:
  - tìm ứng viên;
  - khóa tài xế;
  - gửi offer;
  - accept;
  - reject;
  - timeout;
  - retry.

Khoảng trống cần xử lý:

- Redis matching state có TTL nên không thể dùng làm lịch sử nghiên cứu.
- Chưa có bản ghi bền vững cho từng matching run và từng offer.
- Không thể giải thích đầy đủ vì sao một chuyến match thành công hoặc thất bại.
- Dashboard hiện tại chỉ là số liệu toàn cục, chưa hỗ trợ khoảng thời gian, bucket, vùng không gian hoặc drill-down.
- Chưa có direct-query baseline và materialized-view variant để thực nghiệm.
- Chưa có dataset sinh tự động và quy trình benchmark tái lập.

---

## 3. Nguyên tắc thiết kế

### 3.1 Data-first

Không triển khai chart trước khi:

- định nghĩa metric được chốt;
- nguồn dữ liệu của metric được xác định;
- timezone, bộ lọc và quy tắc loại trừ được thống nhất;
- kết quả truy vấn có test kiểm chứng.

### 3.2 PostgreSQL là nguồn sự thật cho analytics

- Redis tiếp tục phục vụ matching thời gian thực.
- Telemetry dùng cho báo cáo phải được lưu trong PostgreSQL.
- Không xây báo cáo lịch sử từ Redis key có TTL.

### 3.3 Không làm ảnh hưởng luồng giao dịch hiện tại

- API analytics chỉ đọc dữ liệu.
- Analytics không được cập nhật ngược các bảng booking, payment, driver hoặc user.
- Truy vấn nặng phải có giới hạn khoảng thời gian, index và timeout phù hợp.
- Materialized views được dùng để tách workload đọc tổng hợp khỏi các bảng OLTP.

### 3.4 Metric phải có một định nghĩa duy nhất

Các DTO, SQL, chart và báo cáo benchmark phải dùng chung một metric dictionary. Không để frontend tự tính lại metric từ dữ liệu thô nếu backend đã cung cấp kết quả chuẩn hóa.

### 3.5 Không khẳng định kết quả trước thực nghiệm

Plan chỉ đặt giả thuyết rằng materialized views có thể giảm độ trễ truy vấn. Kết luận cuối cùng phải dựa trên benchmark thực tế.

### 3.6 Phạm vi luận văn có kiểm soát

Core scope:

- matching telemetry;
- demand analytics theo thời gian;
- spatial demand analytics;
- matching performance;
- completed-payment revenue;
- direct SQL và materialized-view comparison;
- Admin API và hợp đồng bàn giao cho frontend.

Ngoài core scope:

- dự báo nhu cầu;
- phát hiện bất thường bằng machine learning;
- dynamic pricing tự động;
- data warehouse độc lập;
- Kafka hoặc streaming platform;
- thay đổi thuật toán matching trong cùng giai đoạn đánh giá lớp analytics.

Các nội dung ngoài core scope chỉ triển khai khi toàn bộ acceptance criteria cốt lõi đã đạt.

---

## 4. Metric dictionary ban đầu

Metric dictionary phải được chốt trong commit đầu tiên và được dùng làm hợp đồng cho backend, frontend và benchmark.

| Metric | Định nghĩa đề xuất | Nguồn dữ liệu |
| --- | --- | --- |
| `tripRequests` | Số trip được tạo trong khoảng thời gian theo `requestedAt` | `trips` |
| `completedTrips` | Số trip có trạng thái `COMPLETED` và `completedAt` nằm trong khoảng lọc | `trips` |
| `completedTripsByRequestCohort` | Số trip được yêu cầu trong khoảng lọc và cuối cùng đạt `COMPLETED` | `trips` |
| `cancelledTrips` | Số trip kết thúc ở `CANCELLED` | `trips` |
| `noDriverTrips` | Số trip kết thúc ở `NO_DRIVER` | `trips` |
| `completionRate` | `completedTripsByRequestCohort / tripRequests`, trả `null` khi mẫu số bằng 0 | `trips` |
| `completedRevenue` | Tổng `payments.amount` chỉ với payment `COMPLETED`, lọc theo `paidAt` | `payments` |
| `matchingRuns` | Số matching run được bắt đầu trong khoảng thời gian | `matching_runs` |
| `matchedRuns` | Số matching run có outcome `MATCHED` | `matching_runs` |
| `matchingSuccessRate` | `matchedRuns / terminalRuns`; không tính run còn `IN_PROGRESS` | `matching_runs` |
| `averageMatchingDurationMs` | Trung bình `finishedAt - startedAt` của terminal run | `matching_runs` |
| `p50MatchingDurationMs` | Phân vị 50 của matching duration | `matching_runs` |
| `p95MatchingDurationMs` | Phân vị 95 của matching duration | `matching_runs` |
| `averageOffersPerRun` | Số offer trung bình trên terminal matching run | `matching_offer_events` |
| `offerAcceptanceRate` | Offer `ACCEPTED / terminal offers` | `matching_offer_events` |
| `offerRejectionRate` | Offer `REJECTED / terminal offers` | `matching_offer_events` |
| `offerTimeoutRate` | Offer `TIMEOUT / terminal offers` | `matching_offer_events` |
| `averageCandidateDistanceM` | Khoảng cách trung bình của các offer có distance hợp lệ | `matching_offer_events` |
| `demandByTimeBucket` | Số trip request theo bucket giờ/ngày trong reporting timezone | `trips` |
| `demandBySpatialCell` | Số pickup point theo spatial cell và khoảng thời gian | `trips.pickup_location` |
| `activeDriverSupply` | Số tài xế online/available/busy được lấy mẫu theo bucket; kèm snapshot coverage | `driver_supply_snapshots` |

Quy ước:

- Dữ liệu mới lưu thời gian theo UTC.
- API nhận `from` và `to` dưới dạng ISO-8601 có timezone.
- Reporting timezone mặc định: `Asia/Ho_Chi_Minh`.
- Khoảng lọc dùng quy ước nửa mở: `[from, to)`.
- Doanh thu không lấy từ `trips.final_fare`; chỉ lấy payment có trạng thái `COMPLETED`.
- Run/offer còn `IN_PROGRESS` hoặc `OFFERED` không được đưa vào mẫu số của terminal rate.

---

## 5. Mô hình dữ liệu telemetry

### 5.1 `matching_runs`

Một run biểu diễn toàn bộ quá trình tìm tài xế cho một trip, từ lần tìm đầu tiên đến khi match thành công, hết tài xế, bị hủy hoặc kết thúc do lỗi.

Các cột dự kiến:

```text
id                         BIGSERIAL PRIMARY KEY
trip_id                    BIGINT NOT NULL REFERENCES trips(id)
started_at                 TIMESTAMPTZ NOT NULL
finished_at                TIMESTAMPTZ
outcome                    VARCHAR(30) NOT NULL
matched_driver_id          BIGINT REFERENCES users(id)
trigger_type               VARCHAR(30) NOT NULL
search_count               INTEGER NOT NULL DEFAULT 0
candidate_count            INTEGER NOT NULL DEFAULT 0
offer_count                INTEGER NOT NULL DEFAULT 0
failure_reason_code        VARCHAR(50)
created_at                 TIMESTAMPTZ NOT NULL
updated_at                 TIMESTAMPTZ NOT NULL
```

Outcome ban đầu:

```text
IN_PROGRESS
MATCHED
NO_DRIVER
CANCELLED
FAILED
```

Trigger type ban đầu:

```text
BOOKING_CREATED
SCHEDULED_DISPATCH
RECOVERY
```

Ràng buộc dự kiến:

- Chỉ có tối đa một run `IN_PROGRESS` cho một trip.
- Retry và sự kiện driver available phải tiếp tục run đang mở, không tạo run mới.
- `finished_at` bắt buộc với terminal outcome.
- `matched_driver_id` chỉ được có giá trị khi outcome là `MATCHED`.
- Các counter không âm.

### 5.2 `matching_offer_events`

Một record biểu diễn một offer thực sự đã được gửi tới một tài xế. Candidate bị lock fail nhưng chưa nhận offer không được tính là offer.

Các cột dự kiến:

```text
id                         BIGSERIAL PRIMARY KEY
matching_run_id            BIGINT NOT NULL REFERENCES matching_runs(id)
driver_id                  BIGINT NOT NULL REFERENCES users(id)
attempt_no                 INTEGER NOT NULL
candidate_rank             INTEGER
candidate_distance_m       DOUBLE PRECISION
offered_at                 TIMESTAMPTZ NOT NULL
expires_at                 TIMESTAMPTZ NOT NULL
responded_at               TIMESTAMPTZ
outcome                    VARCHAR(30) NOT NULL
created_at                 TIMESTAMPTZ NOT NULL
updated_at                 TIMESTAMPTZ NOT NULL
```

Outcome ban đầu:

```text
OFFERED
ACCEPTED
REJECTED
TIMEOUT
CANCELLED
EXPIRED
```

Ràng buộc dự kiến:

- Unique `(matching_run_id, attempt_no)`.
- `attempt_no > 0`.
- `candidate_distance_m >= 0` khi có giá trị.
- `responded_at` bắt buộc cho `ACCEPTED` và `REJECTED`.
- Mỗi offer chỉ được chuyển từ `OFFERED` sang một terminal outcome đúng một lần.

### 5.3 `driver_supply_snapshots`

Redis chỉ phản ánh trạng thái tài xế tại thời điểm hiện tại và có TTL. Nếu cần phân tích lịch sử cung-cầu, hệ thống phải lấy mẫu định kỳ và lưu snapshot bền vững.

Các cột dự kiến:

```text
id                         BIGSERIAL PRIMARY KEY
bucket_start               TIMESTAMPTZ NOT NULL
service_area_id            BIGINT REFERENCES service_areas(id)
vehicle_type               VARCHAR(20) NOT NULL
online_drivers             INTEGER NOT NULL
available_drivers          INTEGER NOT NULL
busy_drivers               INTEGER NOT NULL
sampled_at                 TIMESTAMPTZ NOT NULL
```

Ràng buộc dự kiến:

- Unique `(bucket_start, service_area_id, vehicle_type)`.
- Các count không âm.
- `available_drivers + busy_drivers <= online_drivers`.
- Chu kỳ lấy mẫu mặc định 5 phút và cấu hình được.
- Bucket bị thiếu được xem là missing data, không tự động diễn giải thành 0 tài xế.

Nguồn snapshot:

- trạng thái online/available/busy hiện tại từ Redis;
- vị trí gần nhất để ánh xạ service area;
- không lưu raw GPS point mới trong bảng snapshot.

### 5.4 Index ban đầu

```text
matching_runs(trip_id)
matching_runs(started_at)
matching_runs(outcome, started_at)
matching_offer_events(matching_run_id, attempt_no)
matching_offer_events(driver_id, offered_at)
matching_offer_events(outcome, offered_at)
driver_supply_snapshots(bucket_start, vehicle_type)
driver_supply_snapshots(service_area_id, bucket_start)
trips(requested_at)
trips(completed_at)
payments(status, paid_at)
```

Index cuối cùng chỉ được chốt sau khi có `EXPLAIN ANALYZE`.

### 5.5 Database release

Schema mới phải đi qua release folder:

```text
db/releases/YYYYMMDD-admin-analytics-telemetry/
  manifest.yml
  precheck.sql
  apply.sql
  verify.sql
  rollback.sql
```

Không dựa vào `ddl-auto=update` cho staging/production.

---

## 6. Điểm gắn telemetry vào matching flow

Telemetry phải được gắn tại các điểm nghiệp vụ thực sự xảy ra, không suy luận lại từ log text.

| Điểm trong flow | Telemetry cần ghi |
| --- | --- |
| Booking bắt đầu matching | Tạo hoặc lấy run `IN_PROGRESS` theo idempotency key của trip |
| Scheduled dispatch | Tạo/lấy run với trigger `SCHEDULED_DISPATCH` |
| Tìm candidate | Tăng `search_count`, ghi nhận số candidate hợp lệ |
| Gửi offer | Tạo `matching_offer_events` với outcome `OFFERED` |
| Driver accept | Chuyển offer sang `ACCEPTED`, kết thúc run `MATCHED` |
| Driver reject | Chuyển offer sang `REJECTED`, tiếp tục cùng run |
| Offer timeout | Chuyển offer sang `TIMEOUT`, tiếp tục cùng run |
| Booking cancel | Chuyển offer mở sang `CANCELLED`, kết thúc run `CANCELLED` |
| Hết candidate/attempt | Kết thúc run `NO_DRIVER` |
| Lỗi không phục hồi | Kết thúc run `FAILED` và ghi reason code đã chuẩn hóa |
| Supply snapshot scheduler | Lấy mẫu online/available/busy theo vehicle type và service area |

Thiết kế code dự kiến:

```text
matching
  -> MatchingTelemetryPort

analytics
  -> JpaMatchingTelemetryAdapter
  -> MatchingRunRepository
  -> MatchingOfferEventRepository
```

Quyết định mặc định cho phạm vi luận văn:

- Ghi telemetry đồng bộ, idempotent trong modular monolith và cùng PostgreSQL.
- Không triển khai Kafka.
- Outbox pattern được ghi nhận là hướng mở rộng nếu cần delivery guarantee cao hơn.
- Telemetry failure phải có log/metric rõ ràng; không được âm thầm bỏ sự kiện.

Trước khi code cần viết ADR ngắn để chốt:

- ranh giới transaction giữa Redis lock, DB trip update và telemetry write;
- hành vi khi DB telemetry write thất bại;
- idempotency khi listener hoặc scheduler chạy lại;
- cách đóng run bị treo do app crash.

---

## 7. Kiến trúc module analytics

Package dự kiến:

```text
com.example.goride.analytics
|-- api
|   |-- AnalyticsQuery
|   `-- AnalyticsTimeRange
|-- controller
|   `-- AdminAnalyticsController
|-- domain
|   |-- MatchingRun
|   |-- MatchingOfferEvent
|   |-- DriverSupplySnapshot
|   |-- MatchingRunOutcome
|   `-- MatchingOfferOutcome
|-- dto
|   |-- AnalyticsOverviewResponse
|   |-- DemandTimeSeriesResponse
|   |-- DemandHeatmapResponse
|   |-- MatchingPerformanceResponse
|   `-- MatchingFunnelResponse
|-- repository
|   |-- MatchingRunRepository
|   |-- MatchingOfferEventRepository
|   |-- DriverSupplySnapshotRepository
|   |-- DirectAnalyticsRepository
|   `-- MaterializedAnalyticsRepository
|-- service
|   |-- MatchingTelemetryService
|   |-- DriverSupplySnapshotService
|   |-- AdminAnalyticsQueryService
|   |-- AnalyticsMaterializedViewRefreshService
|   `-- AnalyticsMetricValidator
`-- config
    `-- AnalyticsProperties
```

Analytics là read-side đặc thù:

- có thể dùng native SQL/projection để tổng hợp nhiều bảng;
- chỉ đọc các bảng thuộc module khác;
- không gọi repository analytics để ghi vào bảng nghiệp vụ;
- mọi ngoại lệ so với quy tắc module ownership hiện tại phải được ghi vào `docs/changes-in-implementation.md` khi bắt đầu code.

---

## 8. API dự kiến

Base path:

```text
/api/v1/admin/analytics
```

Tất cả endpoint:

- yêu cầu role `ADMIN`;
- yêu cầu `from < to`;
- giới hạn khoảng thời gian tối đa theo cấu hình;
- trả timezone và khoảng thời gian đã chuẩn hóa;
- không trả entity trực tiếp;
- dùng `ApiResponse<T>` hiện tại.

### 8.1 Overview

```http
GET /api/v1/admin/analytics/overview
    ?from=2026-07-01T00:00:00+07:00
    &to=2026-08-01T00:00:00+07:00
    &vehicleType=MOTORBIKE
    &serviceAreaId=1
```

Response dự kiến:

```json
{
  "from": "2026-06-30T17:00:00Z",
  "to": "2026-07-31T17:00:00Z",
  "reportingTimezone": "Asia/Ho_Chi_Minh",
  "tripRequests": 10000,
  "completedTrips": 8250,
  "completedTripsByRequestCohort": 8200,
  "cancelledTrips": 900,
  "noDriverTrips": 900,
  "completionRate": 0.82,
  "completedRevenue": 245000000,
  "matchingSuccessRate": 0.91,
  "p50MatchingDurationMs": 8200,
  "p95MatchingDurationMs": 26400
}
```

### 8.2 Demand time series

```http
GET /api/v1/admin/analytics/demand/timeseries
    ?from=...
    &to=...
    &bucket=HOUR
    &timezone=Asia/Ho_Chi_Minh
```

Bucket cho phép:

```text
HOUR
DAY
WEEK
```

### 8.3 Spatial demand heatmap

```http
GET /api/v1/admin/analytics/demand/heatmap
    ?from=...
    &to=...
    &cellSizeMeters=1000
    &vehicleType=MOTORBIKE
```

Response ưu tiên GeoJSON `FeatureCollection`.

Mỗi feature tối thiểu có:

```json
{
  "type": "Feature",
  "geometry": {},
  "properties": {
    "cellId": "stable-cell-id",
    "tripRequests": 120,
    "completedTripsByRequestCohort": 97,
    "completionRate": 0.8083
  }
}
```

Triển khai spatial grid:

- baseline: aggregate theo pickup point và service area;
- core heatmap: square grid có `cellSizeMeters` giới hạn trong tập giá trị cho phép;
- sử dụng PostGIS với SRID/transform được ghi rõ;
- không đưa H3 extension vào core scope;
- query phải có spatial index và time-range filter.

### 8.4 Driver supply time series

```http
GET /api/v1/admin/analytics/supply/timeseries
    ?from=...
    &to=...
    &bucket=HOUR
    &vehicleType=MOTORBIKE
    &serviceAreaId=1
```

Response gồm:

- average online drivers;
- average available drivers;
- average busy drivers;
- snapshot coverage;
- request-to-available-driver ratio khi demand và supply dùng cùng bucket.

Không trả ratio nếu snapshot coverage dưới ngưỡng được chốt trong metric dictionary.

### 8.5 Matching performance

```http
GET /api/v1/admin/analytics/matching/performance
    ?from=...
    &to=...
    &vehicleType=MOTORBIKE
```

Response gồm:

- run count;
- success/no-driver/cancelled/failed count;
- success rate;
- average/P50/P95 matching duration;
- average offers per run;
- average candidate distance;
- offer acceptance/rejection/timeout rate.

### 8.6 Matching funnel

```http
GET /api/v1/admin/analytics/matching/funnel
    ?from=...
    &to=...
```

Các bước:

```text
RUN_STARTED
CANDIDATE_FOUND
OFFER_SENT
OFFER_ACCEPTED
TRIP_COMPLETED
```

Funnel phải ghi rõ unit của mỗi bước là run, offer hay trip để tránh so sánh sai mẫu số.

### 8.7 Backward compatibility

- Giữ nguyên `GET /api/v1/admin/dashboard`.
- Admin Web v2 hiện tại tiếp tục dùng endpoint cũ cho summary cơ bản.
- Các màn hình analytics mới dùng base path mới.
- Không mở rộng response cũ bằng các query nặng.

---

## 9. Direct-query baseline và analytical read models

### 9.1 Variant A: Direct SQL

Mục đích:

- làm baseline về độ đúng và hiệu năng;
- chạy trực tiếp trên `trips`, `payments`, `matching_runs`, `matching_offer_events`;
- dùng cùng filter/timezone với variant B.

### 9.2 Variant B: Materialized views

Materialized views dự kiến:

```text
analytics.mv_trip_daily
analytics.mv_demand_hourly_cell
analytics.mv_supply_hourly
analytics.mv_matching_daily
```

Nội dung:

- `mv_trip_daily`: trip request, completed/cancelled/no-driver và completed revenue theo ngày, vehicle type, service area.
- `mv_demand_hourly_cell`: demand theo bucket giờ và spatial cell.
- `mv_supply_hourly`: supply snapshot coverage và số tài xế trung bình theo giờ, vehicle type, service area.
- `mv_matching_daily`: matching run/offer metrics theo ngày, vehicle type và service area.

Yêu cầu:

- tạo schema `analytics`;
- có unique index phù hợp nếu dùng `REFRESH MATERIALIZED VIEW CONCURRENTLY`;
- refresh interval cấu hình được;
- response trả `dataFreshnessAt`;
- có manual refresh dành cho test/admin nếu được duyệt;
- direct và materialized variant phải cho kết quả tương đương tại cùng refresh cutoff.

Không bật materialized view mặc định trước khi:

- direct query đã đúng;
- benchmark fixture đã có;
- refresh semantics được test;
- rollback SQL được chuẩn bị.

---

## 10. Hợp đồng backend và bàn giao cho Admin Web

Các màn hình dưới đây là consumer dự kiến của backend. Việc xây dựng giao diện không thuộc phạm vi triển khai của backend plan này.

### 10.1 Analytics Overview

- date range picker;
- vehicle type;
- service area;
- KPI cards;
- trip status distribution;
- revenue trend;
- data freshness indicator.

### 10.2 Demand Analytics

- hourly/daily demand chart;
- supply overlay và snapshot coverage;
- lựa chọn bucket;
- PostGIS heatmap;
- hover tooltip;
- click cell để drill-down;
- bảng top demand cells.

### 10.3 Matching Performance

- success rate;
- P50/P95 duration;
- matching funnel;
- offer outcomes;
- offers per run;
- candidate distance distribution;
- bảng các run thất bại/chậm để điều tra.

### 10.4 Yêu cầu contract khi bàn giao

- URL lưu filter để tái lập cùng một phân tích.
- Enum không hard-code ngoài shared types.
- Response phải đủ dữ liệu để frontend phân biệt loading, empty, partial-data và error.
- Backend trả percentile đã tính, frontend không tính lại.
- OpenAPI/example phải ghi unit, timezone và metric semantics.
- Handoff checklist phải nêu rõ enum, precision, freshness và pagination.

---

## 11. Dataset và benchmark

### 11.1 Dataset mục tiêu

Default benchmark profile:

```text
users:                    10,000
drivers:                   1,000
trips:                   100,000
trip status events:      500,000
matching runs:           100,000
matching offers:         200,000-300,000
driver supply snapshots:         50,000+
location points:       2,000,000
payments:                 80,000+
```

Quy mô phải cấu hình được để chạy smoke, medium và thesis benchmark.

### 11.2 Yêu cầu generator

- deterministic seed;
- dữ liệu hợp lệ theo foreign key và state machine;
- phân bố thời gian có giờ cao điểm;
- phân bố không gian có hotspot;
- có matched, rejected, timeout, no-driver và cancelled samples;
- có supply snapshot đầy đủ, thiếu mẫu có chủ đích và các vùng mất cân bằng cung-cầu;
- doanh thu chỉ xuất hiện từ payment hợp lệ;
- ghi lại seed, scale và thời gian sinh dữ liệu;
- không chứa dữ liệu người dùng thật.

### 11.3 Query benchmark

Mỗi benchmark case gồm:

1. Query name.
2. Dataset version và seed.
3. Khoảng thời gian/filter.
4. Direct-query SQL.
5. Materialized-view SQL.
6. Index đang tồn tại.
7. Warm-up count.
8. Measurement count.
9. Average, P50 và P95 latency.
10. `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`.
11. Kích thước bảng/index/materialized view.
12. Refresh duration và data freshness.

Không so sánh hai variant với filter hoặc snapshot dữ liệu khác nhau.

### 11.4 Correctness gate

Trước khi đo hiệu năng:

- direct và materialized result phải bằng nhau tại cùng cutoff;
- timezone boundary phải có test;
- khoảng thời gian rỗng không được chia cho 0;
- cancelled/no-driver không được tính nhầm completed;
- `PENDING`/`FAILED` payment không được tính doanh thu;
- run/offer đang mở không được tính terminal rate.

---

## 12. Roadmap triển khai

Mỗi phase phải là một hoặc nhiều commit nhỏ, có review gate theo `docs/agent.md`.

### Phase 0 - Chốt hợp đồng nghiên cứu và kiến trúc

Deliverables:

- metric dictionary chính thức;
- research questions;
- ADR về telemetry transaction/idempotency;
- API draft;
- timezone/filter conventions;
- benchmark protocol draft.

Acceptance criteria:

- mỗi metric có tên, công thức, nguồn, filter time và edge case;
- matching run/offer semantics không mơ hồ;
- không còn quyết định schema quan trọng bị để ngỏ.

Commit đề xuất:

```text
docs: define admin analytics metrics and architecture
```

### Phase 1 - Schema matching telemetry

Deliverables:

- entity/enums/repositories;
- database release folder;
- constraints/indexes;
- repository tests;
- verify/rollback SQL.

Acceptance criteria:

- validate DB release pass;
- unique/idempotency constraints hoạt động;
- terminal-state constraints có test;
- không sửa behavior matching hiện tại.

Commit đề xuất:

```text
feat: add persistent matching telemetry schema
```

### Phase 2 - Ghi telemetry trong matching flow

Deliverables:

- `MatchingTelemetryPort`;
- adapter PostgreSQL;
- instrumentation cho start/search/offer/accept/reject/timeout/cancel/no-driver;
- supply snapshot scheduler và Redis read port;
- recovery cho run bị treo;
- Micrometer counters cho telemetry failure.

Acceptance criteria:

- mỗi offer thực sự gửi có đúng một event;
- accept/reject/timeout idempotent;
- terminal run chỉ đóng một lần;
- retry thuộc cùng run;
- booking cancellation đóng offer/run đang mở;
- snapshot không ghi trùng cùng bucket/service area/vehicle type;
- missing snapshot không bị ghi thành zero;
- focused unit tests và integration tests pass.

Commit đề xuất:

```text
feat: persist matching run and offer telemetry
```

### Phase 3 - Direct analytics queries và API baseline

Deliverables:

- query model;
- overview;
- demand timeseries;
- supply timeseries;
- matching performance;
- matching funnel;
- API validation;
- OpenAPI contract;
- repository/controller tests.

Acceptance criteria:

- metric đúng với fixture nhỏ tính tay được;
- filter/timezone thống nhất;
- query có time range bắt buộc;
- admin RBAC được test;
- endpoint dashboard cũ không bị ảnh hưởng.

Commit đề xuất:

```text
feat: add direct-query admin analytics APIs
```

### Phase 4 - Spatial demand analytics

Deliverables:

- PostGIS grid aggregation;
- GeoJSON DTO;
- service-area filter;
- demand/supply bucket alignment;
- spatial indexes nếu cần;
- heatmap endpoint;
- spatial correctness tests.

Acceptance criteria:

- pickup point nằm đúng cell;
- boundary point có quy tắc rõ ràng;
- geometry hợp lệ;
- GeoJSON đúng SRID 4326;
- supply coverage thấp không sinh ratio gây hiểu nhầm;
- cell size được whitelist;
- query plan dùng index phù hợp.

Commit đề xuất:

```text
feat: add PostGIS demand heatmap analytics
```

### Phase 5 - Materialized analytical read models

Deliverables:

- `analytics` schema;
- materialized views;
- unique indexes;
- refresh service/scheduler;
- freshness metadata;
- materialized query adapter;
- direct/materialized result equivalence tests.

Acceptance criteria:

- refresh không phá dữ liệu đang đọc;
- API có thể chọn variant qua internal configuration;
- kết quả đúng tại cùng cutoff;
- release SQL và rollback được review.

Commit đề xuất:

```text
feat: add materialized admin analytics read models
```

### Phase 6 - API hardening và frontend handoff

Deliverables:

- OpenAPI contract hoàn chỉnh;
- response examples;
- error/empty/partial-data semantics;
- pagination và query guardrails;
- frontend integration guide;
- backward-compatibility tests;
- API smoke collection.

Acceptance criteria:

- API không yêu cầu frontend tự tính metric;
- filter, unit, timezone và precision được mô tả;
- heatmap có payload/bounds guardrail;
- dashboard endpoint cũ không regression;
- tài liệu đủ để frontend tích hợp mà không đọc entity/backend code.

Commit đề xuất:

```text
docs: finalize admin analytics API handoff
```

### Phase 7 - Dataset generator và benchmark

Deliverables:

- deterministic dataset generator;
- benchmark runner;
- SQL/query plan artifacts;
- raw result CSV/JSON;
- script tổng hợp bảng kết quả;
- hướng dẫn tái lập.

Acceptance criteria:

- chạy lại cùng seed tạo cùng phân bố/row count;
- benchmark phân biệt warm-up và measurement;
- lưu môi trường chạy;
- không công bố con số trước khi có raw result;
- direct/materialized correctness gate pass trước benchmark.

Commit đề xuất:

```text
test: add reproducible analytics benchmark
```

### Phase 8 - Hardening và tài liệu luận văn

Deliverables:

- performance tuning dựa trên evidence;
- security/rate-limit review;
- final API docs;
- architecture/data-flow diagrams;
- limitation/future-work section;
- implementation log và current phase cập nhật.

Acceptance criteria:

- full applicable test suite pass;
- database release validator pass;
- manual review không còn blocker;
- metrics và kết quả trong luận văn truy ngược được về raw benchmark artifacts.

Commit đề xuất:

```text
docs: finalize admin analytics evaluation artifacts
```

---

## 13. Thứ tự phụ thuộc

```text
Phase 0: metric + ADR
    |
    v
Phase 1: telemetry schema
    |
    v
Phase 2: telemetry instrumentation
    |
    +--------------------+
    |                    |
    v                    v
Phase 3: direct APIs   Phase 4: spatial analytics
    |                    |
    +---------+----------+
              |
              v
Phase 5: materialized views
              |
              +------------------+
              |                  |
              v                  v
Phase 6: API handoff     Phase 7: dataset + benchmark
              |                  |
              +---------+--------+
                        |
                        v
              Phase 8: hardening + thesis artifacts
```

Không bắt đầu Phase 5 trước khi Phase 3 có correctness baseline.

---

## 14. Chiến lược test

### Unit tests

- metric calculations;
- matching state transitions;
- supply snapshot bucketing;
- timezone bucketing;
- zero denominator;
- DTO mapping;
- filter validation.

### Repository tests

- aggregate SQL;
- percentile SQL;
- payment revenue rules;
- PostGIS spatial grouping;
- materialized/direct equivalence.

### Integration tests

- booking -> offer -> accept -> telemetry;
- reject -> retry -> accept -> telemetry;
- timeout -> retry -> no-driver -> telemetry;
- booking cancellation -> telemetry close;
- Redis driver state -> persistent supply snapshot;
- Admin RBAC;
- API filter/timezone;
- materialized refresh.

### Contract tests

- response field names;
- numeric precision;
- GeoJSON structure;
- stable enum serialization;
- backward compatibility của `/api/v1/admin/dashboard`.

### Performance tests

- direct query;
- materialized query;
- refresh duration;
- concurrent admin reads trong lúc refresh;
- large date-range rejection/guardrail.

---

## 15. Observability

Micrometer metrics dự kiến:

```text
analytics.telemetry.write.total
analytics.telemetry.write.failures
analytics.query.duration
analytics.query.errors
analytics.materialized.refresh.duration
analytics.materialized.refresh.failures
analytics.materialized.freshness.seconds
```

Tag cần giới hạn cardinality:

```text
queryType
sourceVariant
outcome
```

Không dùng `tripId`, `driverId`, `cellId` làm metric tag.

Log cần có:

- request/correlation id;
- query type;
- normalized time range;
- source variant;
- duration;
- result row count;
- refresh cutoff.

Không log dữ liệu vị trí chi tiết hoặc thông tin cá nhân không cần thiết.

---

## 16. Rủi ro và phương án giảm thiểu

| Rủi ro | Mức độ | Giảm thiểu |
| --- | --- | --- |
| Telemetry thiếu hoặc ghi trùng | Cao | Idempotency key, unique constraint, integration test |
| Redis và PostgreSQL lệch trạng thái | Cao | ADR transaction, recovery job, failure metrics |
| Metric bị hiểu khác giữa BE/FE/luận văn | Cao | Metric dictionary duy nhất và contract test |
| Query tổng hợp làm chậm OLTP | Cao | Time-range guard, index, timeout, materialized view |
| Materialized data cũ | Trung bình | `dataFreshnessAt`, refresh SLA, manual refresh cho test |
| Timezone làm lệch bucket ngày/giờ | Cao | UTC storage, explicit reporting timezone, boundary tests |
| Spatial query không dùng index | Cao | `EXPLAIN ANALYZE`, giới hạn cell size và bounds |
| Dataset benchmark không đại diện | Trung bình | Nhiều profile, hotspot/peak-hour distribution, công bố limitation |
| Consumer tích hợp trước khi contract ổn định | Trung bình | Data-first phase gate và Phase 6 handoff |
| Phạm vi luận văn quá rộng | Cao | Forecasting/anomaly để ngoài core scope |

---

## 17. Definition of Done

Phân hệ chỉ được xem là hoàn thành khi:

- matching run và offer telemetry được lưu bền vững, đầy đủ và idempotent;
- metric dictionary được phản ánh nhất quán trong SQL, API, tài liệu handoff và báo cáo;
- có analytics theo thời gian và heatmap không gian;
- có matching performance và matching funnel;
- doanh thu chỉ tính từ completed payments;
- direct query và materialized view có correctness equivalence tại cùng cutoff;
- benchmark có raw artifacts và hướng dẫn tái lập;
- mọi database change có release folder hợp lệ;
- RBAC, timezone, empty data và failure cases có test;
- dashboard cũ vẫn tương thích;
- implementation log, current phase và design deviations được cập nhật;
- kết quả luận văn không chứa con số không truy nguyên được.

---

## 18. Điều kiện và nhánh triển khai

User đã chỉ định tiếp tục trên nhánh hiện tại:

```text
codex/admin-v2
```

Quyết định này thay thế đề xuất tạo branch `codex/admin-analytics` trong bản plan ban đầu. Mỗi phase vẫn phải:

1. Được ghi là active scope trong `docs/current-phase.md`.
2. Tách thành commit nhỏ có thể review độc lập.
3. Chạy validation phù hợp.
4. Cập nhật implementation log sau commit.
5. Dừng tại review checkpoint trước phase kế tiếp.
6. Không commit `.codex-tmp/` và `deliverables/`.
