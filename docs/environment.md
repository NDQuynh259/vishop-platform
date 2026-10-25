# Biến môi trường (Environment Variables)

Danh mục các biến môi trường cấu hình chuẩn được hỗ trợ qua Spring Boot `application.yml` cho các Microservices trong **ViShop Platform**.

---

## 1. Cơ sở dữ liệu (PostgreSQL)

Mỗi service sử dụng một Database riêng biệt:

| Biến môi trường | Mặc định (Local) | Mô tả |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/vishop_{service}` | URL kết nối JDBC |
| `SPRING_DATASOURCE_USERNAME` | `vishop` | Tên đăng nhập PostgreSQL |
| `SPRING_DATASOURCE_PASSWORD` | `vishop_secret` | Mật khẩu cơ sở dữ liệu |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | `10` | Số connection tối đa trong pool |
| `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE` | `2` | Số connection nhàn rỗi tối thiểu |

---

## 2. Apache Kafka (Event Streaming)

| Biến môi trường | Mặc định (Local) | Mô tả |
| :--- | :--- | :--- |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Danh sách địa chỉ Kafka Brokers |
| `SPRING_KAFKA_CONSUMER_GROUP_ID` | `{service-name}` | Tên Consumer Group của service |
| `SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET` | `earliest` | Chiến lược đọc offset (`earliest` hoặc `latest`) |
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

Có thể đặt trực tiếp trong môi trường chạy IAM hoặc file env truyền cho Docker Compose:

```dotenv
JWT_ACCESS_EXPIRATION_MS=900000
JWT_REFRESH_EXPIRATION_MS=604800000
```

Compose dev/prod đã truyền hai biến vào IAM. Ví dụ chạy từ root repo:

```powershell
docker compose --env-file infra/.env.prod -f infra/compose/docker-compose.prod.yml up -d iam-service
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
