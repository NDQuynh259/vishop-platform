# Cấu hình ứng dụng và biến môi trường

Các service Spring Boot của **ViShop Platform** khai báo cấu trúc và giá trị mặc định trong YAML, rồi nhận giá trị triển khai từ môi trường chạy.

## Chọn nơi đặt cấu hình

| Loại giá trị | Nơi đặt |
| --- | --- |
| Cổng mặc định, logging, pool size, timeout, feature flag, cấu hình datasource không nhạy cảm | `src/main/resources/application-dev.yml` và `application-prod.yml` của service |
| Cấu hình cố định cho một profile và có thể đưa vào Git | `application-dev.yml`, `application-prod.yml` hoặc YAML của profile tương ứng |
| URL cơ sở dữ liệu, địa chỉ dịch vụ tích hợp, công tắc và giới hạn cần chỉnh theo từng lần triển khai | Biến môi trường của tiến trình chạy service |
| Mật khẩu, API key, khóa ký JWT | Trình quản lý bí mật hoặc biến môi trường do hệ thống triển khai cấp lúc chạy |

Commit YAML, tên biến và giá trị mẫu không nhạy cảm. Giữ bí mật thật và file `.env` chứa bí mật ngoài Git. Ưu tiên trình quản lý bí mật nếu môi trường triển khai đã có sẵn.

## Kết hợp YAML và env trong Spring Boot

Ví dụ cho một service có PostgreSQL. URL và username có giá trị cho local. Mật khẩu lấy từ môi trường chạy.

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/vishop_example}
    username: ${SPRING_DATASOURCE_USERNAME:vishop}
    password: ${SPRING_DATASOURCE_PASSWORD}
```

Spring Boot ưu tiên biến môi trường của tiến trình hơn giá trị từ YAML. Ví dụ, `SPRING_DATASOURCE_URL` có thể ghi đè `spring.datasource.url` ngay cả khi YAML khai báo một URL cố định. Với các nhóm cấu hình của service, dùng `@ConfigurationProperties` để đọc giá trị có kiểu và kiểm tra hợp lệ. [Cấu hình ngoài ứng dụng trong Spring Boot 3.3](https://docs.spring.io/spring-boot/3.3/reference/features/external-config.html)

- Với Docker Compose, truyền các biến cần dùng vào container qua `environment` hoặc `env_file` của service. File `.env` dùng để nội suy cấu hình Compose chỉ cấp giá trị cho Compose. Xem các file mẫu và cách chạy trong [tài liệu hạ tầng](../infra/README.md).
- Với Kubernetes, dùng ConfigMap cho cấu hình thường và Secret hoặc trình quản lý bí mật cho credential. Cấp chúng cho ứng dụng qua env hoặc file được mount khi chạy.
- Với CLI hoặc ứng dụng Java thuần, có thể đọc env bằng `System.getenv()`. Nếu cần đọc YAML, dùng thư viện phù hợp như SnakeYAML và xác định rõ thứ tự ghi đè giữa YAML và env.

Các bảng bên dưới liệt kê những biến môi trường theo từng nhóm. Giá trị được ghi là mặc định local hoặc ví dụ theo ngữ cảnh của service.

---

## 1. Cơ sở dữ liệu (PostgreSQL)

Mỗi service sử dụng một Database riêng biệt:

| Biến môi trường | Mặc định (Local) | Mô tả |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/vishop_{service}` | URL kết nối JDBC |
| `SPRING_DATASOURCE_USERNAME` | `vishop` | Tên đăng nhập PostgreSQL |
| `SPRING_DATASOURCE_PASSWORD` | Cấp lúc chạy | Mật khẩu cơ sở dữ liệu của tiến trình Spring Boot |
| `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE` | `10` | Số connection tối đa trong pool |
| `SPRING_DATASOURCE_HIKARI_MINIMUMIDLE` | `2` | Số connection nhàn rỗi tối thiểu |

---

## Biến bắt buộc khi chạy Docker Compose

Mỗi môi trường có một file env chung cho hạ tầng và file env riêng cho từng service.
Sao chép mẫu thành `.env.dev` hoặc `.env.prod` trong cùng thư mục rồi điền các giá trị cần dùng.

| Mẫu | Nội dung |
| --- | --- |
| [Hạ tầng dev](../infra/.env.example) và [hạ tầng prod](../infra/.env.prod.example) | `POSTGRES_PASSWORD`, `GRAFANA_ADMIN_PASSWORD`, `JWT_ACCESS_SECRET` dùng chung IAM và Gateway. Prod còn cần `AUTH_REDIS_PASSWORD`. Stack `infra` chỉ cần mật khẩu PostgreSQL và Grafana. |
| [IAM](../services/iam-service/.env.example) | `JWT_REFRESH_SECRET`, thời gian sống JWT, cookie và kết nối IAM |
| [Gateway](../services/api-gateway/.env.example) | Port và URL định tuyến của Gateway |
| [Audit](../services/audit-service/.env.example) | Cấu hình kết nối và port riêng của Audit |
| [Subscription](../services/subscription-service/.env.example) | SePay, kết nối và port của Subscription |
| [Notification](../services/notification-service/.env.example) | Cấu hình cho runtime chạy module Notification. Runtime đó cần truyền env vì Compose hiện chưa có container Notification. |

Trong Compose, IAM, Audit và Subscription có bộ biến `*_DATASOURCE_URL`,
`*_DATASOURCE_USERNAME` và `*_DATASOURCE_PASSWORD` theo prefix của service. Ví dụ, đặt
`SUBSCRIPTION_DATASOURCE_USERNAME` và `SUBSCRIPTION_DATASOURCE_PASSWORD` trong file env
của Subscription để dùng tài khoản riêng đã được cấp quyền trên database đó. Compose
truyền các giá trị này vào `SPRING_DATASOURCE_*` của đúng container. Nếu không đặt user
hoặc mật khẩu riêng, Compose dùng `POSTGRES_USER` và `POSTGRES_PASSWORD` của stack.
Khi chạy trực tiếp bằng IDE hoặc Gradle, bộ biến riêng của service vẫn dùng được.
Ví dụ, dùng `localhost` hoặc địa chỉ PostgreSQL truy cập được từ máy chạy Java.
Hostname `postgres` trong mẫu dành cho mạng Compose. Các biến `SPRING_DATASOURCE_*`
của tiến trình Java cũng có thể ghi đè cấu hình. Các biến `*_DB_*` dành cho CLI migration.

IAM và Gateway dùng chung `JWT_ACCESS_SECRET`. Chỉ IAM nhận
`JWT_REFRESH_SECRET`. Hai khóa phải khác nhau và mỗi khóa dài ít nhất 32 byte UTF-8.
`up.ps1` và `down.ps1` nạp file hạ tầng trước, sau đó nạp các file service cùng profile nếu có.
Mỗi container ứng dụng nhận file env của chính nó. Các khóa trong `environment` của Compose
được xác định khi nội suy, gồm cấu hình dùng chung cần cấp cho từng container. Biến trong shell được ưu tiên,
nên CI/CD có thể cấp env trực tiếp. Compose báo lỗi khi thiếu giá trị bắt buộc.
Nếu đang dùng một file chung cũ, chuyển cấu hình riêng sang file của service rồi bỏ các mục
đã chuyển trong file chung. Cấu hình chung cũ vẫn chạy qua các tên biến hiện tại.

Khi chạy IAM, Gateway, Audit hoặc Subscription qua IDE, Gradle hay JAR, Spring mặc định
dùng profile `dev`. Bộ nạp cấu hình đọc file env theo profile của chính service.
Để chỉ định đường dẫn riêng, dùng `IAM_ENV_FILE`, `API_GATEWAY_ENV_FILE`, `AUDIT_ENV_FILE`
hoặc `SUBSCRIPTION_ENV_FILE`. Biến môi trường hệ điều hành vẫn có ưu tiên cao hơn file.
[Nội suy biến trong Docker Compose](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/)

Stack dev và prod dùng địa chỉ nội bộ mặc định. Có thể ghi đè các đích định tuyến của
Gateway qua `IAM_SERVICE_URL`, `AUDIT_SERVICE_URL` và `SUBSCRIPTION_SERVICE_URL`, cũng như
Kafka và hệ thống tracing qua `KAFKA_BOOTSTRAP_SERVERS` và `OTEL_EXPORTER_OTLP_ENDPOINT`.

## 2. Apache Kafka (Event Streaming)

| Biến môi trường | Mặc định (Local) | Mô tả |
| :--- | :--- | :--- |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Danh sách địa chỉ Kafka Brokers |
| `SPRING_KAFKA_CONSUMER_GROUPID` | `{service-name}` | Tên Consumer Group của service |
| `SPRING_KAFKA_CONSUMER_AUTOOFFSETRESET` | `earliest` | Chiến lược đọc offset (`earliest` hoặc `latest`) |
| `VISHOP_OUTBOX_PUBLISH_DELAY_MS` | `1000` | Tần suất quét bảng outbox (milliseconds) |
| `VISHOP_OUTBOX_BATCH_SIZE` | `50` | Số lượng outbox records xử lý trong một batch |
| `VISHOP_OUTBOX_MAX_RETRIES` | `5` | Số lần thử lại tối đa trước khi đánh dấu FAILED |

---

## 3. Redis (Cache & Lock)

| Biến môi trường | Mặc định (Local) | Mô tả |
| :--- | :--- | :--- |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Địa chỉ máy chủ Redis |
| `SPRING_DATA_REDIS_PORT` | `6379` | Cổng Redis |
| `SPRING_DATA_REDIS_PASSWORD` | _(trống)_ | Mật khẩu Redis |

---

## Thời gian sống JWT (IAM)

| Biến môi trường | Mặc định | Thuộc tính Spring |
| --- | --- | --- |
| `JWT_ACCESS_EXPIRATION_MS` | `900000` (15 phút) | `vishop.security.jwt.access-expiration-ms` |
| `JWT_REFRESH_EXPIRATION_MS` | `604800000` (7 ngày) | `vishop.security.jwt.refresh-expiration-ms` |

Đơn vị là **milliseconds**. Refresh TTL phải lớn hơn access TTL. IAM đọc các giá trị
này khi khởi động và dùng để cấp token; thời điểm hết hạn nằm trong claim `exp` của JWT.
Refresh giữ thời điểm hết hạn tuyệt đối của refresh token ban đầu.

Có thể đặt trực tiếp trong môi trường chạy IAM hoặc `services/iam-service/.env.dev` hay `.env.prod`:

```dotenv
JWT_ACCESS_EXPIRATION_MS=900000
JWT_REFRESH_EXPIRATION_MS=604800000
```

Compose dev/prod đã truyền hai biến vào IAM. Ví dụ chạy từ root repo:

```powershell
.\infra\scripts\up.ps1 -Env prod -Service iam-service
```

Khởi động lại IAM khi thay TTL. Cấu hình mới áp dụng cho token cấp sau đó; JWT đã cấp
giữ thời điểm hết hạn trong claim `exp`.

## 4. Cổng HTTP & Logging

| Biến môi trường | Mặc định | Mô tả |
| :--- | :--- | :--- |
| `SERVER_PORT` | `8080` (identity), `8081` (tenant), `8082` (audit) | Cổng lắng nghe HTTP của microservice |
| `LOGGING_LEVEL_ROOT` | `INFO` | Mức log hệ thống |
| `LOGGING_LEVEL_COM_VISHOP` | `DEBUG` (dev) / `INFO` (prod) | Mức log cho mã nguồn ViShop |
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | `health,info,metrics,prometheus` | Danh sách Actuator endpoints được mở |
