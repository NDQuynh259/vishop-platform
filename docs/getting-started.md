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

Thiết lập database credentials theo [hướng dẫn IAM](../services/iam-service/README.md).
IAM cấp access/refresh JWT HS256 bằng hai key riêng và tạo sid cho mỗi lần login.
IAM/gateway phải dùng cùng JWT_ACCESS_SECRET, JWT_REFRESH_SECRET,
GATEWAY_IAM_CONTEXT_SECRET, Auth Redis và AUTH_CACHE_PREFIX. Production bắt buộc
secrets riêng và ALLOWED_ORIGINS; xem [luồng auth](core-architecture/authentication-flow.md).

## Build và kiểm thử

```powershell
.\gradlew.bat test
.\gradlew.bat :services:iam-service:bootJar :services:api-gateway:bootJar :services:audit-service:bootJar
```

Kiểm thử IAM/gateway dùng PostgreSQL tạm riêng, không cần Docker và không truy cập database vận hành.

## Khởi chạy

### Migration trước khi chạy service

JPA chỉ kiểm tra schema (`ddl-auto: validate`); ứng dụng không tự chạy Flyway.
Sau khi PostgreSQL sẵn sàng, chạy migration cho cả IAM và audit từ thư mục gốc:

```powershell
$env:IAM_DB_URL = "jdbc:postgresql://localhost:5432/vishop_iam"
$env:IAM_DB_USER = "vishop"
$env:IAM_DB_PASSWORD = "vishop_secret"
$env:AUDIT_DB_URL = "jdbc:postgresql://localhost:5432/vishop_audit"
$env:AUDIT_DB_USER = "vishop"
$env:AUDIT_DB_PASSWORD = "vishop_secret"
.\gradlew.bat :services:iam-service:migrate :services:audit-service:migrate
if ($LASTEXITCODE -ne 0) { throw "Migration thất bại; chưa khởi chạy ứng dụng." }
```

Các giá trị trên dành cho database local mặc định. Khi đổi host, port hoặc credentials,
đặt `IAM_DB_*` và `AUDIT_DB_*` trỏ đúng database mà từng service dùng qua
`SPRING_DATASOURCE_*`. Chạy lại lệnh migration trước khi khởi động phiên bản có
migration mới; Flyway chỉ áp dụng các migration chưa chạy.

### Chạy từng service

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

### Chạy toàn bộ stack development

Cấu hình secrets trong môi trường hoặc `infra/.env.dev`. Nếu dùng file này,
thêm `--env-file infra/.env.dev` vào lệnh Compose dưới đây.
Khởi chạy PostgreSQL của stack dev trước:

```powershell
docker compose -f infra/compose/docker-compose.dev.yml up -d --wait postgres
if ($LASTEXITCODE -ne 0) { throw "PostgreSQL chưa sẵn sàng." }
```

Chạy hai lệnh migration ở trên với port `${POSTGRES_PORT}` (mặc định `5432`),
user `${POSTGRES_USER}` và password `${POSTGRES_PASSWORD}` của stack dev.
Sau khi migration thành công, khởi chạy stack:

```powershell
.\infra\scripts\up.ps1 -Env dev -Build
```

Stack `infra` và `dev` dùng volume PostgreSQL riêng; cần migrate đúng database
của stack định chạy. Dừng stack đang chiếm cùng port trước khi đổi stack và giữ
nguyên volume dữ liệu.

`up.ps1` chỉ khởi chạy container, không chạy migration. Script chờ healthcheck
và trả lỗi nếu Compose thất bại hoặc service không healthy trong thời gian chờ
(mặc định 180 giây, điều chỉnh bằng `-WaitTimeout 300`). Cần Docker Compose hỗ trợ
`up --wait --wait-timeout`. Với production, migrate database đích trước rồi chạy
`.\infra\scripts\up.ps1 -Env prod -Build` với cấu hình `infra/.env.prod`.

## Luồng thử nghiệm

Gửi request qua gateway tại `http://localhost:8000`:

1. `POST /api/v1/auth/register` với email/password để tạo tài khoản toàn cục.
2. IAM đặt access/refresh vào cookie HttpOnly; JSON không chứa token. Frontend dùng
   `credentials: 'include'` và header `X-Requested-With: XMLHttpRequest` cho các request ghi.
3. `POST /api/v1/tenants` với code/name: IAM tạo tenant, bốn role và OWNER membership
   trong cùng transaction, đồng thời ghi event vào outbox.
4. `POST /api/v1/auth/refresh` với `{tenantId}` và cookie tự gửi từ trình duyệt;
   IAM thay cookie theo tổ chức được chọn, giữ nguyên sid và hạn phiên.
5. Khi access hết hạn, gọi `POST /api/v1/auth/refresh` không body; IAM đọc refresh cookie.
6. Inspect `vishop.tenant.events` and `vishop.user.events` in Kafka UI (:8088).
   Audit service nhận cả event user và tenant, xử lý chống trùng rồi lưu audit log.
7. `POST /api/v1/auth/logout` thu hồi sid hiện tại và xóa cookies; thiết bị khác vẫn
   dùng được phiên riêng. Nếu trả 503 sau commit, retry để đồng bộ Redis.

API, response mới, JWT access/refresh và hướng dẫn chuyển dữ liệu được mô tả trong
[IAM README](../services/iam-service/README.md) và [kiến trúc tenancy](core-architecture/tenancy.md).
