# Khởi chạy ViShop Platform

Các runtime hiện tại là `iam-service` (:8080), `api-gateway` (:8000) và
`audit-service` (:8082). Identity và tenant đã được gộp vào IAM.

## Môi trường

Cần JDK 21 và Docker Desktop/Compose để chạy PostgreSQL 16, Kafka và Redis:

```powershell
docker compose -f infra/compose/docker-compose.infra.yml up -d
```

Trên volume mới, PostgreSQL tự tạo `vishop_iam` và `vishop_audit` tại localhost:5432
(user `vishop`, mật khẩu local `vishop_secret`). Volume cũ cần tạo database IAM
riêng; không xóa volume để áp dụng lại init script.

Thiết lập `JWT_SECRET`, `IAM_INTERNAL_SECRET`, database credentials và
`ALLOWED_ORIGINS` theo [hướng dẫn IAM](../services/iam-service/README.md).
Gateway and downstream use `JWT_SECRET` for local verification; authentication does not
require Redis. `IAM_INTERNAL_SECRET` only protects internal introspection.

## Build và kiểm thử

```powershell
.\gradlew.bat test
.\gradlew.bat :services:iam-service:bootJar :services:api-gateway:bootJar :services:audit-service:bootJar
```

Kiểm thử IAM/gateway dùng PostgreSQL tạm riêng, không cần Docker và không truy cập database vận hành.

## Khởi chạy

Mỗi lệnh chạy trong một terminal đã thiết lập biến môi trường:

```powershell
.\gradlew.bat :services:iam-service:bootRun
```

```powershell
.\gradlew.bat :services:api-gateway:bootRun
```

```powershell
.\gradlew.bat :services:audit-service:bootRun
```

Hoặc cấu hình secrets trong môi trường và chạy toàn bộ stack development:

```powershell
docker compose -f infra/compose/docker-compose.dev.yml up -d --build
```

## Luồng thử nghiệm

Gửi request qua gateway tại `http://localhost:8000`:

1. `POST /api/v1/auth/register` với email/password để tạo tài khoản toàn cục.
2. IAM đặt access/refresh vào cookie HttpOnly; JSON không chứa token. Frontend dùng
   `credentials: 'include'` và header `X-Requested-With: XMLHttpRequest` cho các request ghi.
3. `POST /api/v1/tenants` với code/name: IAM tạo tenant, bốn role và OWNER membership
   trong cùng transaction, đồng thời ghi event vào outbox.
4. `POST /api/v1/auth/switch-tenant` với `{tenantId}` và cookie tự gửi từ trình duyệt;
   IAM thay cookie theo tổ chức được chọn.
5. Khi access hết hạn, gọi `POST /api/v1/auth/refresh` không body; IAM đọc refresh cookie.
6. Inspect `vishop.tenant.events` and `vishop.user.events` in Kafka UI (:8088).
   Audit service nhận cả event user và tenant, xử lý chống trùng rồi lưu audit log.

API, response mới, JWT access/refresh và hướng dẫn chuyển dữ liệu được mô tả trong
[IAM README](../services/iam-service/README.md) và [kiến trúc tenancy](core-architecture/tenancy.md).
