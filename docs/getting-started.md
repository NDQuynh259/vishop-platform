# Hướng dẫn khởi động (Getting Started)

Tài liệu hướng dẫn thiết lập môi trường phát triển cục bộ và khởi chạy các Microservices của **ViShop Platform**.

---

## 1. Yêu cầu môi trường (Prerequisites)

* **Java JDK 21 LTS** (Khuyến nghị Eclipse Temurin hoặc OpenJDK 21).
  Kiểm tra bằng lệnh: `java -version`
* **Docker Desktop & Docker Compose** (để chạy Kafka KRaft, Kafka UI, PostgreSQL 16 và Redis 7).
* **Git**.

---

## 2. Thiết lập môi trường cục bộ (Setup)

### Bước 1: Khởi động các hạ tầng phụ trợ (Docker Compose)
Chạy cụm Kafka (KRaft), Kafka UI, Postgres và Redis:
```powershell
docker compose -f infra/docker-compose/docker-compose.yml up -d
```

Sau khi khởi động thành công, các dịch vụ sẽ hoạt động tại:

| Thành phần | Địa chỉ / URL | Mô tả |
| :--- | :--- | :--- |
| **Kafka Broker** | `localhost:29092` | Event stream broker (KRaft mode) |
| **Kafka UI** | [http://localhost:8088](http://localhost:8088) | Giao diện web quản lý Topics, Messages, Consumer Groups |
| **PostgreSQL 16** | `localhost:5432` | User: `vishop`, Pass: `vishop_secret`, tự động tạo `vishop_tenant`, `vishop_identity`, `vishop_audit` |
| **Redis 7** | `localhost:6379` | Cache & Distributed lock |

---

## 3. Biên dịch và kiểm thử toàn dự án

Dự án sử dụng Gradle Wrapper (`gradlew.bat` trên Windows / `./gradlew` trên Linux/macOS):

### Biên dịch toàn bộ modules:
```powershell
.\gradlew.bat compileJava
```

### Chạy toàn bộ Unit & Integration Tests:
```powershell
.\gradlew.bat test
```

---

## 4. Khởi chạy các Microservices

Mỗi Microservice là một ứng dụng Spring Boot độc lập có thể chạy riêng lẻ:

### Khởi chạy `tenant-service`:
```powershell
.\gradlew.bat :services:tenant-service:bootRun
```
* Service chạy tại port: `http://localhost:8081`
* Kiểm tra trạng thái hoạt động:
  ```powershell
  curl http://localhost:8081/actuator/health
  # Trả về: {"status":"UP"}
  ```

### Khởi chạy `audit-service` (Event Consumer):
```powershell
.\gradlew.bat :services:audit-service:bootRun
```
* Service chạy tại port: `http://localhost:8082`

### Khởi chạy `identity-service`:
```powershell
.\gradlew.bat :services:identity-service:bootRun
```
* Service chạy tại port: `http://localhost:8080`

---

## 5. Thử nghiệm luồng nghiệp vụ mẫu (Tenant & Audit)

1. **Tạo mới một Tenant qua API**:
   ```bash
   curl -X POST http://localhost:8081/api/v1/tenants \
     -H "Content-Type: application/json" \
     -d '{"code": "viet-shop-hn", "name": "ViShop Hà Nội"}'
   ```
   * Request được tiếp nhận bởi `TenantController`.
   * `CreateTenantCommandHandler` lưu tenant vào PostgreSQL và ghi `TenantCreatedEvent` vào bảng `outbox_events` trong cùng một transaction.
   * `OutboxPublisher` quét bảng `outbox_events` và bắn sự kiện lên topic `vishop.tenant.events` trên Kafka.
   * `audit-service` lắng nghe topic `vishop.tenant.events`, kiểm tra tính idempotent qua `consumed_messages` và lưu log vào bảng `audit_logs`.

2. **Xem message trên Kafka UI**:
   Mở trình duyệt truy cập [http://localhost:8088](http://localhost:8088), chọn topic `vishop.tenant.events` để thấy message dạng `EventEnvelope`.

---

## 6. Đọc tiếp các tài liệu quan trọng

1. [Kiến trúc hệ thống (Architecture)](./architecture.md) — Tổng quan kiến trúc đa module, quy tắc phân tầng DDD, CQRS và Outbox relay.
2. [Cấu trúc giải phẫu Microservice (Domain Module Anatomy)](./domain-module-anatomy.md) — Hướng dẫn chi tiết từng file trong 4 tầng: Domain, Application, Infrastructure, Presentation.
3. [Tạo mới Microservice](./creating-a-module.md) — Hướng dẫn scaffold một service mới.
4. [Chuẩn mực lập trình (Code Standards)](./code-standards.md) — Quy ước viết code Java 21, Logging, Exception handling.
