# GoRide Implementation Log

> Muc dich: ghi lai noi dung trien khai theo tung commit de review nhanh va giu trace giua code voi phase trong `docs/backend-implementation.md`.
>
> Tu commit `feat: add matching driver search` tro di, moi commit backend can cap nhat file nay trong cung commit.

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
