# IAM: gỡ luồng xác thực để viết lại

Implementation xác thực IAM cũ đã được xóa. Bốn endpoint còn lại là khung API,
chưa thực hiện nghiệp vụ đăng ký, đăng nhập, gia hạn hay đăng xuất.

| Endpoint | Hành vi hiện tại |
| --- | --- |
| POST /api/v1/auth/register | Validate email/password, sau đó trả 501 |
| POST /api/v1/auth/login | Validate email/password, sau đó trả 501 |
| POST /api/v1/auth/refresh | Trả 501 |
| POST /api/v1/auth/logout | Trả 501 |

Response dùng ProblemDetail với code `auth_not_implemented` và Cache-Control
`no-store`. Không tạo user, không phát JWT/cookie, không ghi audit đăng nhập và
không trả thành công giả. Payload login/register sai vẫn theo validation chung
của hệ thống, trả 422. Contract refresh/logout chưa chọn lại cách truyền credential.

## Đã gỡ

- Các command/handler login, register, logout, switch-tenant; query refresh,
  verify-access-token và profile của luồng cũ.
- Token service/port, claims và auth response DTO, JWT codec/adapter, password
  adapter/policy, cookie helper và OpenAPI security scheme cũ.
- SecurityFilterChain, authentication filter, principal wrapper, JWT key
  configuration, gateway-context verifier và controller authorization bean cũ.
- Redis authorization projection, commit hook, lịch relay và endpoint
  `/internal/authorization`; bridge cấp tenant grant cho identity.
- Các truy vấn chỉ phục vụ chọn tenant/login cũ và cấu hình secret/cookie/Redis IAM.
- Các test phụ thuộc luồng đã xóa; thay bằng test HTTP cho trạng thái reset.

Hai route `/api/v1/auth/me` và `/api/v1/auth/switch-tenant` không còn được đăng ký.
Lib JWT và code gateway/audit/subscription không bị xóa bởi đợt reset riêng IAM.

## Phần giữ lại

User, tenant, membership, role, permission và invitation vẫn giữ model, persistence
và use case nghiệp vụ. Actor DTO và các kiểm tra quyền trong use case/domain là
contract nghiệp vụ còn dùng. Các controller nghiệp vụ dùng actor DTO trực tiếp,
không phụ thuộc principal/filter IAM đã xóa.

Token ngẫu nhiên mời thành viên vẫn có adapter ở `infra/tokens`; nó không phải JWT
access/refresh. Kafka outbox nghiệp vụ và CLI migration vẫn giữ.

Không sửa hoặc chạy migration trên DB đang dùng. Lịch sử Flyway V1–V11 và các cột
token_version/authorization_outbox vẫn còn; hiện không có Java relay phân quyền.

## Chặn API nghiệp vụ trong thời gian viết lại

`IamApiResetConfiguration` là MVC interceptor tạm thời. Các HTTP handler nghiệp vụ
trả 503 với code `iam_authentication_reset`, trước khi resolve actor hoặc chạy use
case. Header tự gắn user/admin hay gateway context không mở được các API này.
Health/OpenAPI vẫn dùng được. Đây không phải implementation xác thực thay thế.

`IamApplication` loại trừ auto-configuration JWT/security mặc định, tránh Spring tự
tạo user/password hoặc provider khác sau khi xóa security IAM.

Khi viết lại, triển khai việc xác thực actor và kiểm tra quyền controller trước khi
gỡ interceptor. Gateway hiện vẫn chứa verifier/cache theo luồng trước; IAM hiện
không cấp token mới và không cung cấp cache-loader cho gateway. Các tài liệu JWT,
Redis/revocation trước reset chỉ còn là tham chiếu thiết kế.

## Kiểm tra

```powershell
.\gradlew.bat :services:iam-service:test :services:iam-service:bootJar
```

Test kiểm tra bốn route POST còn lại, response 501 không cookie/ghi DB, chặn
business API với header giả, không có auth bean cũ, validation, ranh giới module,
domain tenant và schema PostgreSQL/Flyway.
