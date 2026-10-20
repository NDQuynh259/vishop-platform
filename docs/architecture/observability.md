# Giám sát & Quan sát hệ thống (Observability)

Tài liệu quy định kiến trúc giám sát (Metrics, Logs, Traces) của **ViShop Platform** dựa trên Spring Boot Actuator, Micrometer Prometheus, OpenTelemetry và SLF4J MDC.

---

## 1. Định danh tương quan (Correlation ID & Distributed Tracing)

Mỗi request trong hệ thống microservices được định danh bằng các header:

* **`X-Request-Id`**: Mã định danh duy nhất cho từng HTTP Request (UUID v7 sinh bởi `RequestIdFilter`).
* **`X-Correlation-Id`**: Chuỗi định danh một luồng nghiệp vụ người dùng xuyên suốt qua nhiều Microservices và Kafka events.
* **`X-Tenant-Id`**: Định danh tổ chức đang thực hiện thao tác.

### Cơ chế lan truyền (Context Propagation)
* **Trong cùng một Service**: Được lưu trữ qua `MDC` (Mapped Diagnostic Context) của SLF4J và `TenantContext` (`ThreadLocal`). Mọi log line sinh ra trong cùng thread request đều tự động mang theo `requestId` và `tenantId`.
* **Xuyên qua Kafka (Event-Driven)**: [KafkaEventPublisher.java](../platform-common/common-events/src/main/java/com/vishop/common/events/publisher/KafkaEventPublisher.java) tự động đính kèm các header này vào `ProducerRecord`. Consumer phía nhận sử dụng `TenantRecordInterceptor` để nạp ngược lại vào context.

---

## 2. Ghi log có cấu trúc (Structured Logging)

* Toàn bộ microservices sử dụng thư viện **SLF4J** với cấu hình JSON logging chuẩn trên môi trường Production (Logstash / Loki / ELK format).
* **Mỗi log line chứa các trường bắt buộc**:
  * `timestamp`: Thời gian ISO-8601 UTC.
  * `level`: `INFO`, `WARN`, `ERROR`, `DEBUG`.
  * `service`: Tên microservice (`spring.application.name`, ví dụ: `tenant-service`).
  * `traceId` / `spanId`: Tự động nạp qua OpenTelemetry / Micrometer Tracing.
  * `tenantId`: Lấy từ `TenantContext`.
  * `message`: Nội dung log.
* **Bảo mật**: Tuyệt đối không log thông tin nhạy cảm (mật khẩu, auth token, cookie, thông tin thẻ ngân hàng).

---

## 3. Chỉ số đo lường (Metrics & Prometheus)

Mỗi Microservice tích hợp module `platform-common:common-observability` cung cấp Spring Boot Actuator và Micrometer Prometheus Registry:

* **Endpoint Metrics**: `GET /actuator/prometheus`
* **Các metric tiêu chuẩn được thu thập**:
  * `http_server_requests_seconds`: Độ trễ (latency), số lượng request (throughput), mã HTTP status cho từng API path.
  * `spring_kafka_listener_seconds`: Thời gian xử lý từng Kafka message.
  * `jvm_memory_used_bytes` / `jvm_gc_pause_seconds`: Bộ nhớ Heap/Non-Heap và thời gian tạm dừng Garbage Collection.
  * `hikaricp_connections_active` / `hikaricp_connections_idle`: Tình trạng connection pool cơ sở dữ liệu PostgreSQL.

---

## 4. Kiểm tra sức khỏe (Health Checks)

Endpoint chuẩn phục vụ Kubernetes Liveness và Readiness Probes:
* `GET /actuator/health`
  * Trả về `{"status":"UP"}` kèm trạng thái chi tiết của PostgreSQL database (`db`) và Apache Kafka broker (`kafka`).
