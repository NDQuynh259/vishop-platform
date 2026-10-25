# API Gateway

Gateway dùng Spring Cloud Gateway/WebFlux, chạy ở cổng 8000. Các route được định nghĩa
trong [application.yml](../../services/api-gateway/src/main/resources/application.yml).

| Route | Đích |
| --- | --- |
| `/api/v1/auth/**`, `/api/v1/tenants/**` | `iam-service` (:8080) |
| `/api/v1/invitations/**`, `/api/v1/cms/**` | `iam-service` (:8080) |
| `/api/v1/audit/**` | `audit-service` (:8082), khi có HTTP endpoint tương ứng |
| `/v3/api-docs/iam-service` | IAM `/v3/api-docs` |
| `/v3/api-docs/audit-service` | Audit `/v3/api-docs` |

## Mô hình giao tiếp (North - South Communication)

- **Giao tiếp đồng bộ HTTP/REST (JSON)**: API Gateway là cửa ngõ duy nhất đón nhận request từ Internet (Web, Mobile, Webhook) và chuyển tiếp (reverse-proxy) trực tiếp tới các backend services nội bộ (`iam-service:8080`, `audit-service:8082`) theo mô hình **ĐỒNG BỘ (Synchronous Request/Response)**. Gateway **không dùng gRPC** và **không dùng Kafka** để forward request.
- **Luồng Đăng nhập (Login/Register)**: Bắt buộc là luồng đồng bộ HTTP. Endpoint `/api/v1/auth/login` nằm trong `publicPath`, Gateway cho phép đi qua không cần JWT, chuyển tiếp tới `iam-service`. Client nhận kết quả ngay tức thì (`200 OK` kèm access/refresh JWT hoặc `401 Unauthorized`). Việc ghi nhật ký audit từ sự kiện `UserLoggedIn` được đẩy sang Kafka bất đồng bộ qua Transactional Outbox ở phía sau mà không chặn luồng đăng nhập của người dùng.
- **Phân định với Kafka và Saga**:
  - **Kafka (East - West)**: Dùng cho giao tiếp bất đồng bộ giữa các backend service với nhau (Event-Driven Messaging) thông qua Outbox Pattern (ví dụ: IAM phát sự kiện sang Audit).
  - **Saga Pattern (`libs/common-saga`)**: Dùng để điều phối các giao dịch phân tán trải dài trên nhiều microservices độc lập, hỗ trợ rollback nghiệp vụ bằng các giao dịch bù trừ (`compensate`).

## Xác thực và tenant context

Gateway removes client-supplied `X-User-*`, `X-Tenant-Id` and internal secret headers.
Protected requests require Bearer access JWTs. `LocalAuthenticationVerifier` validates
signature, issuer, audience, token_use and expiry locally, without session, Redis or IAM
lookups. The original verified JWT is forwarded; user/tenant metadata comes from its claims.
Identity/CMS/onboarding allow a null tenant; business APIs require tenant context.

Login/register/refresh, API docs, health and OPTIONS do not require access JWTs at gateway.
IAM validates refresh JWTs in the request body even when access JWTs have expired.
Logout requires an access JWT and writes audit; clients discard both tokens.
CORS uses exact `ALLOWED_ORIGINS`.

## Các filter khác

* Correlation filter gắn request/correlation ID để truy vết.
* Tenant resolution filter kiểm tra cú pháp header UUID; header này không chứng minh membership.
* Logging filter ghi thông tin request theo cấu hình quan sát hệ thống.
* Gateway exception handler trả Problem Details cho lỗi định tuyến/kết nối.

## Khởi chạy

Đặt `IAM_SERVICE_URL` cho routing, `JWT_SECRET` và `ALLOWED_ORIGINS` phù hợp rồi chạy:

```powershell
.\gradlew.bat :services:api-gateway:bootRun
```

Gateway does not use `IAM_INTERNAL_SECRET` or Redis for authentication. Issued JWTs
remain valid until expiry; there is no session/projection revocation mechanism.
See [security architecture](security-architecture.md) and [IAM README](../../services/iam-service/README.md).
