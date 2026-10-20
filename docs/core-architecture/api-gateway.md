# API Gateway — ViShop Platform

## 1. Tổng quan

`services/api-gateway` là điểm chạm duy nhất (Single Point of Entry) cho toàn bộ traffic từ client (Web SPA, Mobile App, Third-party Webhook) đi vào hệ sinh thái microservices của **ViShop Platform**.

API Gateway được xây dựng trên **Spring Cloud Gateway (Spring WebFlux / Project Reactor - Netty phi đồng bộ)** chạy trên cổng mặc định **`8000`**.

```text
               Client (Web, Mobile, 3rd Party)
                             │
                             ▼
                    ┌─────────────────┐
                    │   API Gateway   │ (:8000)
                    │  (Spring Cloud) │
                    └────────┬────────┘
                             │
       ┌─────────────────────┼─────────────────────┐
       ▼                     ▼                     ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  identity-   │      │   tenant-    │      │    audit-    │
│   service    │      │   service    │      │   service    │
│   (:8080)    │      │   (:8081)    │      │   (:8082)    │
└──────────────┘      └──────────────┘      └──────────────┘
```

---

## 2. Các chức năng chính

### 2.1. Routing Rules (Định tuyến dịch vụ)
Được cấu hình trong [`application.yml`](../../services/api-gateway/src/main/resources/application.yml):

| Path Pattern | Target Service | Mục đích |
| :--- | :--- | :--- |
| `/api/v1/auth/**`, `/api/v1/users/**` | `http://localhost:8080` | Định tuyến sang `identity-service` |
| `/api/v1/tenants/**` | `http://localhost:8081` | Định tuyến sang `tenant-service` |
| `/api/v1/audit-logs/**` | `http://localhost:8082` | Định tuyến sang `audit-service` |

### 2.2. Global Filters (Bộ lọc toàn cục)

1. **[`CorrelationIdGlobalFilter`](../../services/api-gateway/src/main/java/com/vishop/gateway/filter/CorrelationIdGlobalFilter.java)**:
   * Kiểm tra header `X-Correlation-Id` hoặc `X-Request-Id`.
   * Nếu client chưa gửi, gateway tự động sinh UUID v7 mới và đính kèm vào cả downstream request lẫn upstream response.
   * Đảm bảo tính khả kiến và truy vết phân tán xuyên suốt các dịch vụ.

2. **[`TenantResolutionGlobalFilter`](../../services/api-gateway/src/main/java/com/vishop/gateway/filter/TenantResolutionGlobalFilter.java)**:
   * Kiểm tra header `X-Tenant-Id` trên các protected routes.
   * Hỗ trợ resolve tenant qua custom domain/subdomain nếu sau này tích hợp tenant routing theo host header.

3. **[`LoggingGlobalFilter`](../../services/api-gateway/src/main/java/com/vishop/gateway/filter/LoggingGlobalFilter.java)**:
   * Ghi log access phi đồng bộ (Method, URI, Query Params, Status code, Client IP, Latency ms).

### 2.3. CORS Configuration
Được định cấu hình tại [`CorsConfig.java`](../../services/api-gateway/src/main/java/com/vishop/gateway/config/CorsConfig.java), hỗ trợ đầy đủ preflight request (`OPTIONS`), cho phép các headers phổ biến (`X-Tenant-Id`, `X-Correlation-Id`, `Authorization`, `Content-Type`) và hỗ trợ credentials.

### 2.4. Global Exception & Problem Details (RFC 9457)
Tại [`GatewayExceptionHandler.java`](../../services/api-gateway/src/main/java/com/vishop/gateway/filter/GatewayExceptionHandler.java):
* Khi downstream service bị sập (`503 Service Unavailable` hoặc `ConnectException` / `TimeoutException`), Gateway trả về chuẩn **RFC 9457 Problem Details** (JSON) thay vì HTML mặc định của trình duyệt/container.

Ví dụ phản hồi lỗi khi downstream service không hoạt động:
```json
{
  "type": "https://api.vishop.vn/errors/service-unavailable",
  "title": "Service Unavailable",
  "status": 503,
  "detail": "Upstream service is temporarily unavailable or unreachable",
  "instance": "/api/v1/tenants"
}
```

---

## 3. Khởi chạy API Gateway

```bash
# Chạy trực tiếp từ root qua Gradle
./gradlew :services:api-gateway:bootRun
```

Kiểm tra sức khỏe dịch vụ:
```bash
curl http://localhost:8000/actuator/health
```
