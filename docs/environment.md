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

## 4. Cổng HTTP & Logging

| Biến môi trường | Mặc định | Mô tả |
| :--- | :--- | :--- |
| `SERVER_PORT` | `8080` (identity), `8081` (tenant), `8082` (audit) | Cổng lắng nghe HTTP của microservice |
| `LOGGING_LEVEL_ROOT` | `INFO` | Mức log hệ thống |
| `LOGGING_LEVEL_COM_VISHOP` | `DEBUG` (dev) / `INFO` (prod) | Mức log cho mã nguồn ViShop |
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | `health,info,metrics,prometheus` | Danh sách Actuator endpoints được mở |
