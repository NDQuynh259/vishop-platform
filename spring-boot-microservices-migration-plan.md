> Historical design proposal: service boundaries and security flows below predate the IAM merge.
> Current runtime/configuration is documented in [IAM README](services/iam-service/README.md).
> The separate identity/tenant service source directories have been removed.

# ViShop — Kế hoạch chuyển đổi sang Java Spring Boot Microservices

## 1. Mục tiêu

Chuyển backend hiện tại từ NestJS/Fastify/Nx sang Java Spring Boot theo kiến trúc microservice, giữ nguyên:

- API contract và hành vi nghiệp vụ quan trọng.
- PostgreSQL làm database chính.
- Multi-tenant isolation.
- UUID v7, cursor pagination, idempotency và RFC 9457 Problem Details.
- Transactional outbox, xử lý bất đồng bộ và retry.
- Tích hợp TikTok Shop, S3/MinIO, Redis, email và Telegram.
- Khả năng triển khai độc lập từng service.

Không nên chuyển toàn bộ một lần. Lộ trình khuyến nghị là **modular monolith trước, sau đó tách service theo bounded context**. Cách này giảm rủi ro distributed transaction và cho phép rollback từng bước.

## 2. Hiện trạng cần chuyển đổi

Repository hiện tại là Nx monorepo với 4 runtime app:

- `api`: REST, GraphQL, WebSocket, auth, health, metrics.
- `worker`: BullMQ consumers xử lý TikTok sync, fulfillment, import/export, notification và upload verification.
- `scheduler`: cron jobs, leader election bằng Redis, cleanup và reconciliation.
- `migration`: chạy database migration/seed.

Các bounded context chính:

- `users`, `tenants`, `shops`.
- `products`, `catalog`, `inventories`.
- `orders`, fulfillment, returns, packages, shipping.
- `finance`, `affiliate`, reports.
- `upload`, notifications, feature flags.
- `audit-log`, `event-log`, assistant/automation.

Các hạ tầng quan trọng:

- PostgreSQL + Prisma, schema lớn trong `prisma/schema.prisma`.
- Redis + BullMQ + Dead Letter Queue.
- Transactional outbox trong `outbox_events`.
- Nguồn NestJS cũ dùng Better Auth. Runtime Java đích dùng JWT stateless access/refresh; contract nguồn không áp dụng cho xác thực hiện tại.
- TikTok Shop adapter, OAuth, request signing, webhook và rate limit.
- S3/MinIO presigned upload.
- OpenTelemetry, Prometheus metrics và Sentry.

Nguồn tham chiếu:

- `CLAUDE.md`: quy ước kiến trúc, API, database và eventing.
- `docs/product/vishop-erd.md`: mô hình dữ liệu nghiệp vụ.
- `docs/architecture/architecture.md`: DDD, contract và outbox.
- `docs/architecture/scaling-to-services.md`: chiến lược tách service hiện có.
- `prisma/schema.prisma`: schema thực tế, là nguồn chính khi migrate database.

## 3. Kiến trúc đích

### 3.1. Thành phần nền tảng

| Thành phần | Công nghệ đề xuất |
|---|---|
| Java | Java 21 LTS |
| Framework | Spring Boot 3.x |
| Build | Gradle Kotlin DSL hoặc Maven; chọn một, không trộn |
| API | Spring MVC hoặc WebFlux; ưu tiên MVC ở giai đoạn đầu |
| Security | Spring Security 6 |
| Persistence | Spring Data JPA + jOOQ cho query phức tạp |
| Migration | Flyway |
| Validation | Jakarta Bean Validation |
| Messaging | Kafka hoặc RabbitMQ; ưu tiên Kafka nếu cần replay/event stream |
| Queue tác vụ | Kafka consumer hoặc RabbitMQ work queue |
| Cache/lock | Redis |
| Object storage | AWS SDK v2 S3 |
| API docs | springdoc-openapi |
| Observability | Micrometer, OpenTelemetry, Prometheus |
| Testing | JUnit 5, Testcontainers, WireMock |
| Container | Docker, Kubernetes/HelM nếu có |

### 3.2. Service boundary đề xuất

Không tạo một service cho từng bảng. Mỗi service phải sở hữu một bounded context và database schema riêng về lâu dài.

```text
api-gateway
identity-service
tenant-service
shop-integration-service
catalog-service
inventory-service
order-service
fulfillment-service
finance-service
notification-service
file-service
report-service
audit-service
scheduler-worker
```

Giai đoạn đầu có thể deploy ít process hơn nhưng giữ boundary code:

```text
commerce-app
  ├── identity module
  ├── tenant module
  ├── shop module
  ├── catalog module
  ├── inventory module
  ├── order module
  ├── fulfillment module
  ├── finance module
  └── audit module

background-worker
scheduler
```

### 3.3. Service ownership

#### `identity-service`

Sở hữu:

- Global user, password, JWT access/refresh; verification và two-factor là phạm vi mở rộng, chưa được triển khai đầy đủ.
- Login, logout, password reset, email verification.
- JWT access token và refresh token trong JSON body; API dùng Bearer access token.

Gateway xác minh access JWT bằng JWT_ACCESS_SECRET; IAM dùng JWT_REFRESH_SECRET riêng cho refresh. Downstream nhận context từ gateway. Không lưu session đăng nhập hoặc introspect IAM cho từng request.

#### `tenant-service`

Sở hữu:

- Tenant, tenant member, invitation.
- Role, permission, member-role scope.
- Tenant settings, feature flags, API keys.

Mọi request tenant-scoped phải có `tenantId` trong security context và query condition bắt buộc theo tenant.

#### `shop-integration-service`

Sở hữu:

- Shop, authorization, OAuth token và platform connection.
- TikTok OAuth, token refresh, webhook verification.
- TikTok adapter và platform-neutral port.
- Initial sync orchestration.

Không để domain order/inventory gọi trực tiếp TikTok SDK. Mọi gọi ra nền tảng đi qua `PlatformChannel`/`ShopProvider` interface.

#### `catalog-service`

Sở hữu:

- Merchant SKU.
- SKU category.
- Platform product/SKU.
- SKU mapping, combo component, UOM conversion.

#### `inventory-service`

Sở hữu:

- Warehouse, bin, inventory balance.
- Stock movement, stocktake, stock debt.
- Inventory transfer, safety stock.
- Stock push request và inventory projection.

Inventory mutation phải có optimistic locking hoặc database locking, append-only stock movement và idempotency key.

#### `order-service`

Sở hữu:

- Order, order line, order fee.
- Order status, flags, cancellation request.
- Order sync/upsert từ platform.

Unique idempotency bắt buộc cho dữ liệu platform: `(platform, shop_id, external_order_id)`.

#### `fulfillment-service`

Sở hữu:

- Package, package line.
- Shipping/label, pick wave, scan session.
- Ship batch, returns, shipment issue.
- Print log và label cache nếu không tách thành file service.

Fulfillment là cấp package, không phải cấp order. Một order có thể có nhiều package và mỗi package có trạng thái vận chuyển riêng.

#### `finance-service`

Sở hữu:

- Settlement.
- Reconciliation.
- Operating expense, ad cost.
- Profit calculation và finance export.

Tiền phải lưu bằng số nguyên theo minor unit của currency, không dùng `double`/`float`.

#### `notification-service`

Sở hữu:

- Email, Telegram, in-app notification.
- Alert config, recipient, digest.
- SMTP/Telegram provider.

Các service nghiệp vụ chỉ publish event hoặc command; không gửi email trực tiếp trong transaction request.

#### `file-service`

Sở hữu:

- Presign/confirm upload.
- Stored file metadata.
- S3/MinIO adapter.
- File verification và cleanup.

Browser upload trực tiếp lên S3 bằng presigned URL. Server confirm bằng HEAD và worker kiểm tra lại object.

#### `audit-service`

Sở hữu:

- `audit_logs`: người dùng đã thực hiện thao tác gì.
- `event_logs`: hệ thống đã thay đổi dữ liệu gì.
- Outbox consumer và immutable append-only storage.

Audit service chỉ nhận event/reference value; không tạo foreign key xuyên service.

## 4. Quy tắc database

### 4.1. Giai đoạn migration ban đầu

Giữ nguyên PostgreSQL hiện tại và schema hiện tại trong giai đoạn đầu. Không đổi tên bảng hoặc đổi kiểu dữ liệu chỉ vì chuyển ORM.

- Spring Boot dùng Flyway baseline từ schema hiện có.
- Import schema/migration hiện tại vào Flyway.
- Chạy `validate` trước khi service boot production.
- Không để Hibernate tự tạo hoặc sửa schema production:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

### 4.2. Giai đoạn database ownership

Khi tách service thật:

1. Mỗi service có schema/database riêng.
2. Service chỉ ghi bảng do mình sở hữu.
3. Không dùng cross-service foreign key.
4. ID của entity bên ngoài chỉ lưu dưới dạng reference value.
5. Cross-context read dùng API hoặc read model.
6. Cross-context write dùng command/event, không dùng transaction xuyên database.

### 4.3. UUID v7

Tiếp tục dùng UUID v7 để giữ đặc tính sắp xếp theo thời gian và tương thích cursor. Java cần có một implementation UUID v7 thống nhất trong shared library.

Không được dùng `UUID.randomUUID()` cho entity mới nếu API/database dựa vào UUID v7.

### 4.4. Pagination

Giữ cursor pagination:

```text
cursor = base64url(createdAt + ":" + id)
```

Query mẫu:

```sql
where (created_at, id) < (:createdAt, :id)
order by created_at desc, id desc
limit :limit
```

Không thay bằng offset pagination trên các collection lớn. Nếu UI cần page number hoặc total count, tạo endpoint summary riêng.

## 5. API contract

### 5.1. Success response

Resource đơn trả trực tiếp:

```json
{
  "id": "019dd1a5-9235-70db-8d57-54ef901d8185",
  "name": "Example"
}
```

Collection dùng envelope:

```json
{
  "object": "list",
  "url": "/api/v1/orders",
  "data": [],
  "pageInfo": {
    "hasNextPage": true,
    "nextCursor": "opaque-cursor"
  }
}
```

### 5.2. Error response

Dùng `application/problem+json` theo RFC 9457:

```json
{
  "type": "/errors/validation-failed",
  "title": "Validation failed",
  "status": 422,
  "code": "validation_failed",
  "detail": "One or more fields did not pass validation.",
  "instance": "/api/v1/orders",
  "requestId": "019dd1a5-9235-70db-8d57-54ef901d8185",
  "timestamp": "2026-10-05T00:00:00Z",
  "errors": []
}
```

Spring implementation:

- `@RestControllerAdvice` toàn cục.
- `ProblemDetail` của Spring làm nền tảng.
- Thêm extension `code`, `requestId`, `errors`.
- Không trả stack trace, SQL message hoặc token cho client.
- Domain exception không chứa HTTP status; adapter HTTP map domain error sang status.

### 5.3. Headers

- `X-Request-Id` được tạo/propagate ở gateway.
- `Idempotency-Key` cho mutation quan trọng.
- `Content-Type: application/problem+json` cho lỗi.

## 6. Event-driven architecture

### 6.1. Event envelope

Dùng envelope versioned:

```json
{
  "eventId": "uuid-v7",
  "eventType": "order.created",
  "schemaVersion": 1,
  "occurredAt": "2026-10-05T00:00:00Z",
  "producer": "order-service",
  "tenantId": "uuid-v7",
  "aggregateType": "order",
  "aggregateId": "uuid-v7",
  "correlationId": "uuid-v7",
  "causationId": "uuid-v7",
  "payload": {}
}
```

### 6.2. Outbox flow

Mọi mutation có event:

```text
BEGIN
  update aggregate
  insert outbox_event
COMMIT

outbox-publisher
  -> publish broker
  -> consumer xử lý idempotent
  -> mark published/processed
```

Không publish Kafka/RabbitMQ trực tiếp trong transaction database. Không dùng in-memory event bus cho cross-service event.

### 6.3. Event cần chuẩn hóa

Nhóm identity:

- `user.registered`
- `user.logged_in`
- `user.logged_out`
- `user.updated`

Nhóm tenant:

- `tenant.created`
- `tenant.member_added`
- `tenant.member_removed`
- `permission.changed`

Nhóm shop:

- `shop.connected`
- `shop.reauthorized`
- `shop.revoked`
- `shop.sync_completed`

Nhóm catalog/inventory:

- `sku.created`
- `sku.updated`
- `inventory.changed`
- `inventory.low_stock`
- `inventory.push_requested`
- `inventory.push_completed`

Nhóm order/fulfillment:

- `order.created`
- `order.updated`
- `order.status_changed`
- `package.created`
- `package.shipped`
- `package.delivered`
- `return.created`

### 6.4. Consumer rules

Mỗi consumer phải:

- Idempotent theo `eventId` hoặc business key.
- Có retry với exponential backoff.
- Có dead-letter topic/queue.
- Ghi correlation ID.
- Có schema compatibility test.
- Không giả định event chỉ được giao một lần.

## 7. Security và multi-tenancy

### 7.1. Authentication

Xác thực dùng JWT stateless với access token và refresh token, trả trong JSON body.
API được bảo vệ nhận `Authorization: Bearer <accessToken>`; gateway và downstream xác minh
chữ ký, issuer, audience, loại token và thời hạn tại chỗ. Không lưu session đăng nhập,
không dùng cookie xác thực hoặc Redis projection. Gateway không gọi IAM introspection
trên mỗi request.

`POST /api/v1/auth/refresh` nhận `{ "refreshToken": "..." }`, hoạt động cả khi access token
đã hết hạn. IAM kiểm tra tài khoản và quyền hiện tại trước khi cấp cặp token mới. Thời hạn
mặc định: access 15 phút, refresh 7 ngày; refresh giữ thời điểm hết hạn tuyệt đối ban đầu.
Token refresh cũ còn hiệu lực đến hết hạn; không có cơ chế lưu trạng thái rotation/reuse.

Logout ghi audit, trả 204 và client xoá cả hai token. JWT đã cấp vẫn hợp lệ đến hết hạn;
không cam kết thu hồi tức thời. Tenant context lấy từ JWT đã xác minh; đổi tenant qua
`POST /api/v1/auth/switch-tenant` với Bearer access token và `{tenantId,refreshToken}`,
rồi dùng cặp token mới. Header `X-Tenant-Id` từ client không cấp hoặc đổi quyền tenant.

### 7.2. Tenant context

Tạo các thành phần dùng chung:

```text
TenantContext
AuthenticatedPrincipal
TenantContextFilter
TenantAuthorizationService
PermissionEvaluator
```

Mọi repository tenant-scoped phải nhận `tenantId` tường minh. Không lấy tenant từ request body. Không cho phép query thiếu tenant condition.

### 7.3. Secrets

- OAuth token lưu encrypted at rest.
- Secret lấy từ Vault/Kubernetes Secret/Cloud Secret Manager.
- Không log access token, refresh token, password, cookie hoặc PII nhạy cảm.
- S3 presigned URL có expiry ngắn.

## 8. Background processing

### 8.1. Worker

Thay BullMQ bằng Kafka consumer hoặc RabbitMQ worker tùy yêu cầu:

- Kafka: phù hợp event replay, nhiều consumer, audit và sync stream.
- RabbitMQ: phù hợp task queue, routing và retry đơn giản.

Có thể dùng cả hai:

- Kafka cho domain/integration event.
- RabbitMQ cho command/task như export, email, label fetch.

### 8.2. Scheduler

Dùng một trong các mô hình:

- `@Scheduled` + ShedLock cho cron đơn giản.
- Kubernetes CronJob cho job độc lập.
- Quartz clustered cho lịch phức tạp/user-defined report schedule.

Không chạy cùng một cron trên mọi replica nếu chưa có distributed lock. Mỗi job cần:

- Lock/leader guard.
- Running guard chống overlap.
- Timeout.
- Retry policy.
- Metric success/failure/duration.

### 8.3. Job mapping

Các nhóm job hiện tại cần chuyển:

- TikTok token refresh, initial sync, order/product/tracking/return sync.
- Inventory push và reconcile.
- Label fetch, ship package, ship reconcile.
- Import/export.
- Email/Telegram notification.
- Upload verification.
- Settlement/reconciliation.
- Cleanup verification, file, label cache, outbox; JWT tự hết hạn, không có bảng session đăng nhập cần cleanup.
- DLQ monitor và alert digest.

## 9. API Gateway

Gateway chịu trách nhiệm:

- TLS termination.
- Routing service.
- CORS, rate limit, request size limit.
- Correlation ID.
- Xác minh JWT access tại chỗ và cấp lại token bằng refresh JWT tại IAM.
- Centralized access log.
- Circuit breaker và timeout.
- API versioning.

Không đưa business logic vào gateway. Không để gateway truy cập trực tiếp database service khác.

Có thể dùng:

- Spring Cloud Gateway nếu muốn toàn bộ Java.
- Kong/NGINX/Traefik nếu muốn tách gateway khỏi application runtime.

## 10. Repository structure đề xuất

### 10.1. Một repository multi-module

```text
vishop-platform/
├── settings.gradle.kts
├── build.gradle.kts
├── platform-common/
│   ├── common-web/
│   ├── common-security/
│   ├── common-events/
│   ├── common-observability/
│   └── common-test/
├── services/
│   ├── api-gateway/
│   ├── identity-service/
│   ├── tenant-service/
│   ├── shop-integration-service/
│   ├── catalog-service/
│   ├── inventory-service/
│   ├── order-service/
│   ├── fulfillment-service/
│   ├── finance-service/
│   ├── notification-service/
│   ├── file-service/
│   ├── report-service/
│   └── audit-service/
├── workers/
│   ├── integration-worker/
│   ├── fulfillment-worker/
│   └── export-worker/
├── infra/
│   ├── docker-compose/
│   ├── helm/
│   └── terraform/
└── docs/
```

### 10.2. Package structure mỗi service

```text
src/main/java/com/vishop/orders/
├── OrdersApplication.java
├── domain/
│   ├── model/
│   ├── event/
│   ├── port/
│   └── service/
├── application/
│   ├── command/
│   ├── query/
│   ├── handler/
│   └── mapper/
├── adapter/
│   ├── in/web/
│   ├── in/messaging/
│   └── out/
│       ├── persistence/
│       ├── messaging/
│       └── platform/
└── config/
```

Quy tắc dependency:

```text
a adapter -> application -> domain
infrastructure adapter -> domain ports
web controller không gọi repository trực tiếp
service này không import domain class của service khác
```

## 11. Lộ trình triển khai

### Phase 0 — Chốt contract và baseline

Mục tiêu: có tài liệu và test bảo vệ hành vi hiện tại.

Công việc:

- Freeze OpenAPI hiện tại.
- Export endpoint catalogue và auth flow.
- Chụp schema PostgreSQL và migration checksum.
- Liệt kê event, queue, cron và integration.
- Xác định table ownership.
- Tạo contract tests cho pagination, errors, idempotency, tenant isolation.
- Chốt Java 21, Spring Boot version, Gradle/Maven, broker.

Exit criteria:

- Có OpenAPI baseline.
- Có danh sách database table owner.
- Có danh sách event version 1.
- Có test kiểm tra API contract quan trọng.

### Phase 1 — Tạo Spring Boot modular monolith

Mục tiêu: chuyển business logic đầu tiên sang Java nhưng chưa tách database/service.

Thứ tự:

1. `platform-common`.
2. Error handling, request ID, validation, pagination.
3. Database access với Flyway và jOOQ/JPA.
4. Tenant context và permission.
5. Audit/outbox framework.
6. Users/tenants.
7. Catalog.
8. Inventory.
9. Orders.
10. Fulfillment.

Mỗi module phải có domain/application/adapter boundary riêng.

Exit criteria:

- Spring Boot app chạy song song với NestJS.
- Có smoke test và contract test.
- Chưa chuyển traffic production nếu chưa có shadow comparison.

### Phase 2 — Chuyển read path trước

Mục tiêu: giảm rủi ro bằng cách chuyển các API GET trước.

- Gateway route read request sang Java theo feature flag.
- So sánh response Java và NestJS bằng shadow traffic.
- Không cho Java ghi production ở giai đoạn đầu.
- Sửa khác biệt về field mapping, timezone, pagination và permission.

Các read path ưu tiên:

- `users/me`.
- Tenant/member/role.
- Merchant SKU/category/mapping.
- Inventory dashboard.
- Order list/detail.
- Audit/event logs.

### Phase 3 — Chuyển write path theo bounded context

Thứ tự khuyến nghị:

1. Feature flags, audit và upload.
2. Catalog/product.
3. Tenant/role.
4. Inventory.
5. Order.
6. Fulfillment.
7. Finance.
8. Shop/TikTok sync.
9. Identity/auth cuối cùng.

Mỗi context:

- Chọn một owner duy nhất cho write.
- Tắt write tương ứng ở NestJS.
- Enable Java write bằng feature flag.
- Đối soát row count và business invariant.
- Giữ rollback route trong thời gian quan sát.

### Phase 4 — Tách worker và scheduler

- Chuyển queue/task sang broker đã chọn.
- Chạy Java worker song song nhưng dùng consumer group riêng.
- So sánh side effect và output.
- Chuyển từng job theo nhóm.
- Cuối cùng tắt BullMQ processor tương ứng.

### Phase 5 — Tách database/service

Chỉ bắt đầu khi module đã có ownership sạch:

- Audit/file trước vì ít foreign key ngược.
- Catalog/inventory sau khi event contract ổn định.
- Order/fulfillment tiếp theo.
- Identity/auth cuối cùng.

Mỗi lần tách:

1. Tạo schema/database mới.
2. Backfill dữ liệu.
3. Dual-read kiểm tra.
4. Bật service write owner.
5. Publish event từ owner.
6. Xóa quyền ghi của service cũ.
7. Xóa cross-database repository/import.

## 12. Chiến lược migrate dữ liệu

### 12.1. Không big-bang

Dùng expand-and-contract:

1. Expand: thêm cột/bảng/event cần thiết, backward-compatible.
2. Dual-write có kiểm soát nếu bắt buộc.
3. Backfill bằng batch có checkpoint.
4. Dual-read và compare.
5. Cutover bằng feature flag.
6. Contract: xóa code/schema cũ sau thời gian ổn định.

### 12.2. Quy tắc backfill

- Không chạy một transaction khổng lồ.
- Có batch size, rate limit, checkpoint và resume.
- Ghi metric số row thành công/thất bại.
- Không gửi email/webhook side effect khi backfill nếu không cần.
- Có checksum hoặc reconciliation report.

### 12.3. Tách dữ liệu nhạy cảm

- Identity/password cần migration riêng; client đăng nhập lại để nhận cặp JWT access/refresh, không chuyển session cũ sang runtime mới.
- OAuth token phải re-encrypt hoặc validate decrypt sau khi chuyển.
- PII của order phải có policy masking và access control tương đương hệ thống cũ.

## 13. Testing strategy

### Unit test

- Domain entity/value object.
- State transition order/package.
- Inventory reservation/stock movement.
- Permission và tenant rule.
- Cursor encode/decode.
- Idempotency key.

### Integration test

- PostgreSQL thật bằng Testcontainers.
- Redis thật.
- Kafka/RabbitMQ thật hoặc container.
- Flyway migration từ empty database.
- Outbox publish/mark/retry.
- Optimistic locking/concurrency.

### Contract test

- OpenAPI response schema.
- Problem Details shape.
- Event envelope/schema compatibility.
- TikTok adapter bằng WireMock recorded fixtures.

### End-to-end test

- Login, refresh sau access expiry, đổi tenant và logout stateless.
- Create tenant/member/permission.
- Connect shop.
- Import SKU.
- Sync order.
- Reserve stock và fulfill package.
- Reconcile finance.
- Retry/DLQ.

### Migration verification

- Row count source/target.
- Aggregate totals inventory/order/finance.
- Orphan reference scan.
- Duplicate external ID scan.
- Event replay test.

## 14. Observability và vận hành

Mọi service phải có:

- `/actuator/health/liveness`.
- `/actuator/health/readiness`.
- `/actuator/prometheus`.
- OpenTelemetry trace propagation.
- Structured JSON logs.
- `requestId`, `correlationId`, `tenantId`, `userId` đã được redact phù hợp.

Metrics tối thiểu:

- HTTP latency/error rate.
- DB pool usage.
- Outbox lag.
- Broker consumer lag.
- Job retry/DLQ count.
- TikTok rate-limit/error count.
- Inventory mutation conflict.
- Sync duration và records processed.
- Reconciliation mismatch count.

Readiness chỉ kiểm tra dependency cần để phục vụ request. Không đưa deep dependency hoặc replica lag vào readiness của mọi replica.

## 15. Rủi ro và cách giảm thiểu

| Rủi ro | Giảm thiểu |
|---|---|
| Distributed transaction | Outbox, saga, idempotent consumer |
| Data drift khi dual-write | Reconciliation job, checksum, single write owner |
| Auth contract không tương thích | Cập nhật client sang Bearer access JWT và refresh JWT; yêu cầu đăng nhập lại |
| Mất event | Transactional outbox, retry, DLQ |
| Duplicate TikTok webhook | Unique event ID + idempotent handler |
| Race condition inventory | Optimistic lock/DB lock + stock movement |
| Event schema drift | Versioned envelope + compatibility test |
| Query thiếu tenant filter | Repository API bắt buộc tenantId + integration test |
| Tách service quá sớm | Modular monolith trước, tách edge context trước |
| Hibernate làm thay đổi schema | `ddl-auto=validate`, chỉ dùng Flyway |
| Chậm do gọi service đồng bộ | Read model/cache, timeout/circuit breaker |
| Token/PII lộ trong log | Centralized redaction test |

## 16. Definition of Done cho mỗi service

- [ ] Có ownership rõ ràng cho bảng và event.
- [ ] Có OpenAPI và event contract.
- [ ] Có Flyway migration.
- [ ] Không có cross-service repository import.
- [ ] Tenant isolation được test.
- [ ] Error response đúng RFC 9457.
- [ ] Cursor pagination đúng contract.
- [ ] Mutation quan trọng có idempotency.
- [ ] Outbox được ghi cùng transaction.
- [ ] Consumer idempotent và có DLQ.
- [ ] Có health, metric, trace và structured log.
- [ ] Có unit/integration/contract/e2e test.
- [ ] Có Docker image và deployment manifest.
- [ ] Có rollback/cutover procedure.

## 17. Checklist bắt đầu ở repository mới

```bash
# 1. Tạo project
mkdir vishop-platform
cd vishop-platform

# 2. Khởi tạo Spring Boot multi-module
# Chọn Java 21, Spring Boot 3.x, Gradle Kotlin DSL

# 3. Tạo modules
mkdir -p platform-common services workers infra docs

# 4. Tạo service đầu tiên
# identity-service hoặc audit-service, không bắt đầu bằng order/inventory

# 5. Kết nối database read-only
# Flyway baseline + ddl-auto=validate

# 6. Import OpenAPI contract
# Tạo contract tests trước khi implement controller

# 7. Chạy Testcontainers
./gradlew test

# 8. Build image
./gradlew bootBuildImage
```

## 18. Quyết định cuối cùng được khuyến nghị

1. Chọn Java 21 + Spring Boot 3.x + Gradle Kotlin DSL.
2. Bắt đầu bằng modular monolith Java, không triển khai 12 microservice ngay.
3. Giữ PostgreSQL hiện tại trong giai đoạn migration đầu.
4. Dùng Flyway; Hibernate chỉ validate schema.
5. Giữ UUID v7, cursor pagination và Problem Details; xác thực dùng JWT stateless access/refresh theo contract IAM hiện tại.
6. Dùng transactional outbox cho mọi cross-context event.
7. Chọn Kafka cho domain/integration event; RabbitMQ cho task queue nếu hệ thống cần cả hai loại.
8. Tách audit và file trước; identity/auth sau cùng.
9. Không cho service truy cập database của service khác.
10. Mỗi bounded context chỉ có một write owner.
11. Chuyển read path trước, write path sau, worker/scheduler tiếp theo.
12. Chỉ xóa NestJS sau khi tất cả contract, side effect, event và migration verification đều đạt.
