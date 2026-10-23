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

## Xác thực và tenant context

Gateway xóa `X-User-*`, `X-Tenant-Id` và khóa nội bộ do client gửi. Các request được
bảo vệ phải có JWT Bearer hoặc opaque cookie `vishop_session`.

[IamAuthenticationClient](../../services/api-gateway/src/main/java/com/vishop/gateway/security/IamAuthenticationClient.java)
gọi endpoint riêng `/internal/auth/introspect` bằng `IAM_INTERNAL_SECRET`. IAM kiểm tra
session, trạng thái tài khoản, tenant, membership và quyền hiện tại. Gateway nhận JWT
mới từ IAM để chuyển downstream, loại bỏ cookie gốc và gắn metadata user/tenant đã xác minh.
Roles và permissions nằm trong JWT, không lấy từ các header client.

Identity/onboarding/CMS API có thể dùng context chưa chọn tenant; API nghiệp vụ cần tenant
context. IAM không hoạt động thì gateway trả 503, không dùng quyền cũ. Credential bị thu hồi
trả 401; thiếu tenant/quyền trả 403. Downstream vẫn xác minh JWT của IAM.

Chỉ login/register, tài liệu API, health và OPTIONS được đi qua mà không cần xác thực.
Cookie-authenticated writes cần Origin được phép. CORS dùng danh sách origin chính xác
trong `ALLOWED_ORIGINS`. Authorization sai không được fallback sang cookie.

## Các filter khác

* Correlation filter gắn request/correlation ID để truy vết.
* Tenant resolution filter kiểm tra cú pháp header UUID; header này không chứng minh membership.
* Logging filter ghi thông tin request theo cấu hình quan sát hệ thống.
* Gateway exception handler trả Problem Details cho lỗi định tuyến/kết nối.

## Khởi chạy

Đặt `IAM_SERVICE_URL`, `IAM_INTERNAL_SECRET`, `ALLOWED_ORIGINS` phù hợp rồi chạy:

```powershell
.\gradlew.bat :services:api-gateway:bootRun
```

Gateway không cần khóa ký JWT để introspect. IAM và các downstream xác minh JWT cần
`JWT_SECRET`. Xem [IAM README](../../services/iam-service/README.md) để biết API và cấu hình.
