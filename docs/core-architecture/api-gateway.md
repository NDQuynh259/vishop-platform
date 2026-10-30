# API Gateway

IAM authentication hiện đã [reset](iam-auth-reset.md): bốn auth endpoint chỉ trả
501, các API nghiệp vụ IAM trả 503. Phần routing gateway vẫn giữ; mô tả xác thực
IAM cũ bên dưới là tham chiếu trước reset.

Gateway dùng Spring Cloud Gateway/WebFlux, chạy ở cổng 8000. Các route được định nghĩa
trong [application.yml](../../services/api-gateway/src/main/resources/application.yml).

| Route | Đích |
| --- | --- |
| `/api/v1/auth/**`, `/api/v1/tenants/**` | `iam-service` (:8080) |
| `/api/v1/invitations/**`, `/api/v1/cms/**` | `iam-service` (:8080) |
| `/api/v1/audit/**` | `audit-service` (:8082), khi có HTTP endpoint tương ứng |
| `/v3/api-docs/iam-service` | IAM `/v3/api-docs` |
| `/v3/api-docs/audit-service` | Audit `/v3/api-docs` |

Tài liệu API được cung cấp dưới dạng OpenAPI 3 JSON qua springdoc API starter. Gateway
giữ endpoint `/v3/api-docs` và chuyển tiếp spec của từng service theo các URL trên;
không cài Swagger UI hay cấu hình trang `/swagger-ui.html`.

## Mô hình giao tiếp (North - South Communication)

- **Giao tiếp đồng bộ HTTP/REST (JSON)**: API Gateway là cửa ngõ duy nhất đón nhận request từ Internet (Web, Mobile, Webhook) và chuyển tiếp (reverse-proxy) trực tiếp tới các backend services nội bộ (`iam-service:8080`, `audit-service:8082`) theo mô hình **ĐỒNG BỘ (Synchronous Request/Response)**. Gateway **không dùng gRPC** và **không dùng Kafka** để forward request.
- **Luồng Đăng nhập (Login/Register)**: Bắt buộc là luồng đồng bộ HTTP. Endpoint `/api/v1/auth/login` nằm trong `publicPath`, Gateway cho phép đi qua không cần JWT, chuyển tiếp tới `iam-service`. Client nhận kết quả ngay tức thì (`200 OK` kèm access/refresh JWT hoặc `401 Unauthorized`). Việc ghi nhật ký audit từ sự kiện `UserLoggedIn` được đẩy sang Kafka bất đồng bộ qua Transactional Outbox ở phía sau mà không chặn luồng đăng nhập của người dùng.
- **Phân định với Kafka và Saga**:
  - **Kafka (East - West)**: Dùng cho giao tiếp bất đồng bộ giữa các backend service với nhau (Event-Driven Messaging) thông qua Outbox Pattern (ví dụ: IAM phát sự kiện sang Audit).
  - **Saga Pattern (`libs/common-saga`)**: Dùng để điều phối các giao dịch phân tán trải dài trên nhiều microservices độc lập, hỗ trợ rollback nghiệp vụ bằng các giao dịch bù trừ (`compensate`).

## Xác thực và tenant context

Gateway strips client identity/context headers and verifies the user access JWT once.
It resolves active user/tenant/membership and role permissions from Auth Redis, then sends
request-bound signed context to the selected service. The original Bearer token is removed.
Backends verify gateway context and authorize resources locally. They do not verify user JWTs.

See [gateway authorization contract](gateway-authorization.md) for cache versions, freshness,
miss loading, fail-closed behavior, context secrets, migration and rollout requirements.
Public auth routes retain HttpOnly cookie and CSRF/Origin protections; SePay authenticates
its own provider callback. Logout clears cookies even if the access credential is unusable.

## Luồng hoạt động chi tiết khi có 1 API đi qua Gateway (Request Lifecycle)

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant CORS as CorsWebFilter (WebFlux)
    participant CorrFilter as CorrelationIdGlobalFilter
    participant TenantFilter as TenantResolutionGlobalFilter
    participant AuthFilter as AuthenticationGlobalFilter
    participant Routing as NettyRoutingFilter (Reverse Proxy)
    participant Downstream as Microservice (IAM / Audit :8080/:8082)
    participant LogFilter as LoggingGlobalFilter (doFinally)

    Client->>CORS: Gửi HTTP Request (VD: POST /api/v1/tenants)
    Note over CORS: Kiểm tra CORS (Allowed Origins, Methods, Headers)
    
    CORS->>CorrFilter: Chuyển tiếp vào chuỗi GatewayFilterChain
    Note over CorrFilter: Sinh mới hoặc giữ X-Correlation-Id, X-Request-Id (UUIDv7)
    
    CorrFilter->>TenantFilter: Chuyển tiếp
    Note over TenantFilter: Kiểm tra cú pháp UUID nếu có header X-Tenant-Id
    
    TenantFilter->>AuthFilter: Chuyển tiếp
    Note over AuthFilter: 1. Header Sanitization: Xóa x-user-*, X-Tenant-Id, X-Iam-Internal-Key<br/>2. Kiểm tra Allowed Origins<br/>3. Public path bypass (login/register/refresh/docs/health)<br/>4. Verify JWT; resolve current authorization from Auth Redis; sign service context<br/>5. Context Propagation: Bơm X-User-Id, X-Tenant-Id chuẩn
    
    AuthFilter->>Routing: Khớp Route Predicates (application.yml)
    Routing->>Downstream: Forward HTTP Request đã được làm sạch và gắn context (đồng bộ)
    
    Downstream-->>Routing: Trả về HTTP Response
    Routing-->>CorrFilter: Phản hồi đi ngược ra
    Note over CorrFilter: Gắn X-Correlation-Id, X-Request-Id vào Client Response Header
    CorrFilter-->>LogFilter: Hoàn tất reactive stream
    Note over LogFilter: Log [Gateway] METHOD PATH -> STATUS (ms) qua doFinally
    LogFilter-->>Client: Trả kết quả về cho Client
```

### Thứ tự thực thi của các Filter (`getOrder()`)

Trong Spring Cloud Gateway, tất cả các filter implement `GlobalFilter, Ordered` sẽ được tự động sắp xếp theo quy tắc: **Số có giá trị càng nhỏ thì độ ưu tiên càng cao (chạy càng sớm trong pha Pre-routing)**:

| Thứ tự | Thành phần / Filter | Giá trị `getOrder()` | Vai trò và trách nhiệm chính |
| :---: | :--- | :--- | :--- |
| **0** | `CorsWebFilter` | *Tầng WebFilter* | Chạy trước `GatewayFilterChain`. Xử lý preflight `OPTIONS`, kiểm tra `AllowedOrigins`, `AllowedMethods`, `AllowedHeaders`. |
| **1** | `CorrelationIdGlobalFilter` | `Ordered.HIGHEST_PRECEDENCE`<br>($-2,147,483,648$) | **Distributed Tracing**: Đảm bảo luôn có `X-Correlation-Id` và `X-Request-Id` (tự sinh UUIDv7 nếu thiếu). Bơm vào cả downstream request và client response. |
| **2** | `TenantResolutionGlobalFilter` | `Ordered.HIGHEST_PRECEDENCE + 1`<br>($-2,147,483,647$) | Kiểm tra cú pháp UUID của header `X-Tenant-Id`. Nếu sai định dạng, ngắt luồng và trả ngay `400 Bad Request` (RFC 9457). |
| **3** | `AuthenticationGlobalFilter` | `Ordered.HIGHEST_PRECEDENCE + 2`<br>($-2,147,483,646$) | **Zero Trust & Security**: Tẩy sạch untrusted header (`x-user-*`, `X-Tenant-Id` giả mạo). Bypass public routes (`/login`, `/register`, `/docs`). Verify JWT locally, resolve Redis authorization and sign service context, bơm `X-User-Id` và `X-Tenant-Id` đã xác thực. |
| **4** | `NettyRoutingFilter` (Core) | `Ordered.LOWEST_PRECEDENCE`<br>($2,147,483,647$) | Chuyển tiếp request qua Netty HTTP client tới service đích (`iam-service:8080`, `audit-service:8082`). |
| **5** | `LoggingGlobalFilter` | `Ordered.LOWEST_PRECEDENCE`<br>($2,147,483,647$) | Dùng reactive operator `.doFinally(...)` để đón lúc response hoàn thành, tính toán `duration (ms)` và ghi log tập trung. |

### Cơ chế tự động kích hoạt Filter (`FilteringWebHandler`)
* **Tại sao không cần khai báo filter trong `application.yml`?**  
  Class `TenantResolutionGlobalFilter`, `CorrelationIdGlobalFilter`, `AuthenticationGlobalFilter` đều được đánh dấu `@Component` và `implements GlobalFilter`.
* **Cơ chế nạp ngầm:** Lúc khởi động, `GatewayAutoConfiguration` của Spring Boot tự động quét và inject toàn bộ `List<GlobalFilter>` vào class trung tâm **`FilteringWebHandler`** (nằm trong thư viện `spring-cloud-gateway-server.jar`). Khi có bất kỳ request nào đến, `FilteringWebHandler` sẽ duyệt danh sách này và gọi hàm `filter(exchange, chain)` theo đúng thứ tự `Order`.

### Ý nghĩa của `Mono<Void>` trong hàm `filter()`
* `Mono` là publisher phát ra 0 hoặc 1 giá trị trong lập trình phản ứng (Project Reactor).
* `<Void>` thể hiện không có đối tượng dữ liệu trả về.
* **`Mono<Void>`** là **tín hiệu thông báo trạng thái hoàn thành bất đồng bộ (Completion Signal)**:
  * Khi trả về `response.writeWith(...)`: Báo hiệu ghi xong dữ liệu lỗi ra mạng và ngắt luồng.
  * Khi trả về `chain.filter(exchange)`: Chuyển giao request cho filter tiếp theo và đợi toàn bộ chuỗi hoàn tất mà không chặn (block) thread.

---

## Khung Quyết Định: Khi nào dùng Spring WebFlux vs Spring MVC?

Trong hệ thống **ViShop Platform**, cả hai công nghệ được phối hợp nhịp nhàng theo nguyên tắc:

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        Internet Clients / Apps                         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTP / REST
┌───────────────────────────────────▼────────────────────────────────────┐
│  API GATEWAY: Spring WebFlux (Eclipse Netty)                           │
│  → Mô hình: Event Loop, Non-blocking I/O                               │
│  → Nhiệm vụ: Routing, Token Verification, Rate Limit, Header Injection  │
│  → Đặc điểm: Không chạm Database, xử lý hàng vạn kết nối đồng thời     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTP / REST (Nội bộ)
┌───────────────────────────────────▼────────────────────────────────────┐
│  BACKEND SERVICES: Spring MVC (Apache Tomcat)                          │
│  (iam-service, audit-service...)                                       │
│  → Mô hình: Thread-per-request, Synchronous                            │
│  → Nhiệm vụ: Xử lý nghiệp vụ phức tạp, Domain-Driven Design, CQRS      │
│  → Lưu trữ: PostgreSQL 16 qua Spring Data JPA, Hibernate, Flyway       │
└────────────────────────────────────────────────────────────────────────┘
```

### 1. Khi nào dùng Spring WebFlux?
* **Làm API Gateway / Reverse Proxy (Như `api-gateway`)**: Nhiệm vụ chính là I/O mạng (nhận request, đọc token, forward). WebFlux sử dụng số lượng ít thread (bằng số CPU core) nhưng giải phóng thread ngay lập tức khi gửi request sang downstream, chịu tải cực cao với lượng RAM tối thiểu.
* **Hệ thống Streaming / Real-time**: Server-Sent Events (SSE), WebSockets (thông báo nhảy số, tracking đơn hàng).
* **BFF (Backend For Frontend)**: Cần gọi song song nhiều API bên thứ 3 và gộp dữ liệu bằng `WebClient.zip(...)`.
* *Lưu ý quan trọng*: WebFlux đòi hỏi toàn bộ stack phải là Non-blocking (R2DBC, Reactive Redis). **Tuyệt đối không dùng JPA/Hibernate/JDBC truyền thống trong WebFlux** vì câu lệnh blocking SQL sẽ làm nghẽn Event Loop của Netty.

### 2. Khi nào dùng Spring MVC?
* **Core Business Services (Như `iam-service`, `audit-service`)**: Nơi chứa logic nghiệp vụ phức tạp, cần kiểm soát giao dịch ACID (`@Transactional`).
* **Hệ sinh thái JPA / Hibernate / Flyway**: Toàn bộ chuẩn JPA (`jakarta.persistence.*`, `Spring Data JPA`) là blocking I/O theo thiết kế. Spring MVC trên Tomcat quản lý mô hình thread-per-request hoàn hảo cho JDBC connection pool (HikariCP).
* **Dễ phát triển và gỡ lỗi (Debugging)**: Code tuần tự (Imperative), stack trace rõ ràng từng dòng lệnh khi có Exception, cộng đồng thư viện hỗ trợ phong phú.

| Tiêu chí | Spring MVC | Spring WebFlux |
| :--- | :--- | :--- |
| **Web Server** | Apache Tomcat | Eclipse Netty |
| **Mô hình luồng** | 1 Request = 1 Thread (Blocking) | Event Loop (Non-blocking I/O) |
| **Kiểu Filter** | `OncePerRequestFilter` (`void doFilterInternal`) | `GlobalFilter` (`Mono<Void> filter`) |
| **Thế mạnh áp dụng** | Core Business CRUD, Transaction ACID, JPA/Hibernate | API Gateway, Routing Proxy, Streaming Real-time |
| **Áp dụng tại ViShop** | `services/iam-service`, `services/audit-service` | `services/api-gateway` |

---

## Khởi chạy

Đặt `IAM_SERVICE_URL` cho routing, `JWT_ACCESS_SECRET` giống IAM và `ALLOWED_ORIGINS` phù hợp rồi chạy.
IAM hiện không cấp token mới; xem [trạng thái reset](iam-auth-reset.md):

```powershell
.\gradlew.bat :services:api-gateway:bootRun
```

Gateway verifies user JWTs and uses dedicated Auth Redis. See the authorization contract for failure and freshness behavior.
