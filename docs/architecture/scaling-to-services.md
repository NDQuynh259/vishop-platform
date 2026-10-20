# Chiến lược Microservices & Bounded Contexts

Tài liệu mô tả kiến trúc phân tách dịch vụ của **ViShop Platform** theo mô hình Microservices phân tán hướng sự kiện (Event-Driven Architecture) với Apache Kafka.

---

## 1. Ranh giới Bounded Contexts & Services

Mỗi Microservice sở hữu hoàn toàn Bounded Context và Database riêng của mình (Database-per-service):

| Microservice | Bounded Context | Trách nhiệm chính | Database Schema |
| :--- | :--- | :--- | :--- |
| **`tenant-service`** | Tenants, Organizations | Quản lý thông tin tổ chức, gian hàng, phân quyền nội bộ tenant, trạng thái hoạt động | `vishop_tenant` |
| **`identity-service`** | Users, Authentication | Quản lý người dùng, mật khẩu, tài khoản, xác thực 2FA, JWT token | `vishop_identity` |
| **`audit-service`** | Audit Trails | Ghi nhận nhật ký kiểm toán bất biến (append-only) cho mọi sự kiện trên nền tảng | `vishop_audit` |
| **`catalog-service`** | Products, Categories | Quản lý thông tin sản phẩm, danh mục, thuộc tính, phân loại SKU | `vishop_catalog` |
| **`inventory-service`** | Stock, Warehouses | Quản lý tồn kho, khóa tồn kho tạm thời khi đặt hàng, đồng bộ kho sàn | `vishop_inventory` |
| **`order-service`** | Orders, Checkouts | Quản lý đơn hàng, giỏ hàng, tính giá, trạng thái vòng đời đơn | `vishop_order` |

---

## 2. Giao tiếp giữa các Dịch vụ (Inter-Service Communication)

### 2.1. Bất đồng bộ qua Apache Kafka (Event-Driven) — 90% các tương tác
* **Không dùng HTTP gọi chéo giữa các service để cập nhật dữ liệu**.
* Khi một service hoàn tất thao tác ghi, nó lưu Domain Event vào bảng `outbox_events` trong cùng một transaction.
* `OutboxPublisher` gửi sự kiện lên topic Kafka tương ứng (`vishop.{aggregate}.events`).
* Các service quan tâm (ví dụ: `audit-service`, `inventory-service`) lắng nghe event qua `@KafkaListener` và xử lý idempotent bằng `IdempotentConsumer`.

### 2.2. Đồng bộ qua REST / gRPC — Chỉ dùng khi thực sự cần thiết
* Chỉ áp dụng cho các truy vấn tra cứu thông tin tức thời (Read-only query) hoặc kiểm tra validation không thể trì hoãn.

---

## 3. Tính độc lập của Database (Database-per-Service)

1. **Tuyệt đối không có Foreign Key giữa các Database của các service khác nhau**:
   * Khóa ngoại chéo (`tenantId`, `userId`, `productId`) chỉ được lưu dưới dạng giá trị tham chiếu UUID v7.
2. **Không join bảng xuyên database**:
   * Dữ liệu cần tổng hợp từ nhiều context được thực hiện thông qua **Event Sourcing / Materialized Views** hoặc tổng hợp ở tầng API Gateway / BFF.
