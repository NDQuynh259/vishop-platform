# Khắc phục sự cố (Troubleshooting)

Tổng hợp các sự cố thường gặp trong quá trình phát triển cục bộ và vận hành các Microservices của **ViShop Platform** cùng hướng dẫn xử lý.

---

## 1. Môi trường phát triển & Gradle

### 1.1. Lỗi: `./gradlew` hoặc `gradlew` không nhận diện được lệnh trên Windows
* **Nguyên nhân**: Trên PowerShell / Windows Terminal, cần chỉ định đường dẫn tương đối bằng dấu chấm và gạch chéo ngược.
* **Cách khắc phục**: Dùng `.\gradlew.bat` thay vì `./gradlew`:
  ```powershell
  .\gradlew.bat compileJava
  ```

### 1.2. Lỗi phiên bản Java không tương thích (Unsupported class file version)
* **Nguyên nhân**: Dự án cấu hình Java 21 LTS (`JavaLanguageVersion.of(21)`). Nếu biến môi trường `JAVA_HOME` hoặc máy trỏ vào Java 17 hoặc cũ hơn, Gradle sẽ báo lỗi.
* **Cách khắc phục**:
  1. Kiểm tra: `java -version`.
  2. Cài đặt JDK 21 (Temurin hoặc OpenJDK 21).
  3. Cập nhật biến môi trường: `$env:JAVA_HOME = "C:\Path\To\jdk-21"`.

### 1.3. Gradle Daemon bị đơ hoặc lỗi file lock trên Windows
* **Nguyên nhân**: Daemon chạy nền bị khóa file trong thư mục `.gradle`.
* **Cách khắc phục**: Dừng toàn bộ Gradle Daemons đang chạy:
  ```powershell
  .\gradlew.bat --stop
  ```

---

## 2. Hạ tầng Docker Compose & Cơ sở dữ liệu

### 2.1. Không kết nối được tới PostgreSQL (`Connection refused: localhost:5432`)
* **Nguyên nhân**: Container Postgres chưa khởi động hoặc port 5432 bị chiếm bởi service Postgres local khác.
* **Cách khắc phục**:
  1. Kiểm tra trạng thái: `docker compose -f infra/docker-compose/docker-compose.yml ps`
  2. Khởi động lại: `docker compose -f infra/docker-compose/docker-compose.yml up -d postgres`
  3. Kiểm tra log: `docker logs vishop-postgres`

### 2.2. Lỗi `Flyway: Migration checksum mismatch`
* **Nguyên nhân**: File migration đã chạy trước đó trong `db/migration/` bị sửa đổi nội dung.
* **Cách khắc phục**: Trong môi trường local development:
  ```powershell
  # Có thể drop database dev để Flyway chạy lại từ đầu:
  docker exec -it vishop-postgres psql -U vishop -c "DROP DATABASE vishop_tenant; CREATE DATABASE vishop_tenant;"
  ```
  *(Trên production: tuyệt đối không sửa file migration cũ, luôn tạo file migration mới `V2__...sql`)*.

---

## 3. Apache Kafka & Event Streaming

### 3.1. Kafka broker không sẵn sàng (`Connection to node -1 (localhost/127.0.0.1:29092) could not be established`)
* **Nguyên nhân**: Kafka KRaft container chưa hoàn tất quá trình khởi tạo cluster ID.
* **Cách khắc phục**:
  1. Đợi 5-10 giây để Kafka hoàn thành self-heal và election.
  2. Kiểm tra log Kafka: `docker logs vishop-kafka`
  3. Mở Kafka UI [http://localhost:8088](http://localhost:8088) để xác nhận trạng thái cụm là `ONLINE`.

### 3.2. Outbox event bị kẹt ở trạng thái `PENDING`
* **Nguyên nhân**:
  1. Service chưa kích hoạt `@EnableScheduling` nên `OutboxPublisher` không được trigger.
  2. Không kết nối được Kafka broker khiến `OutboxPublisher` thất bại và tăng `retry_count`.
* **Cách khắc phục**:
  1. Đảm bảo main class có annotation `@EnableScheduling`.
  2. Kiểm tra log của service để xem chi tiết lỗi kết nối Kafka.
  3. Kiểm tra cột `error_message` trong bảng `outbox_events` để biết nguyên nhân thất bại.

### 3.3. Consumer không nhận được message hoặc xử lý lặp lại
* **Nguyên nhân**:
  1. Consumer Group bị lỗi rebalance.
  2. Bị trùng message nhưng `IdempotentConsumer` đã ghi nhận vào bảng `consumed_messages` nên tự động bỏ qua (hành vi chính xác của deduplication).
* **Cách khắc phục**:
  1. Kiểm tra bảng `consumed_messages` để xem message ID đã được ghi nhận trước đó chưa:
     ```sql
     SELECT * FROM consumed_messages WHERE message_id = '<event-id>';
     ```
  2. Kiểm tra topic Dead-Letter Queue (`.DLT`) trên Kafka UI để xem message có bị đẩy vào DLQ do lỗi không.
