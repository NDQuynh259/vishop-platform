# ViShop Platform — Tài liệu Kiến trúc Hệ thống (Architecture Handbook)

Thư mục này tập trung toàn bộ tài liệu đặc tả kiến trúc, ranh giới dịch vụ (Bounded Contexts), chuẩn mực thiết kế và các mẫu kiến trúc (Architectural Patterns) được áp dụng trong **ViShop Platform**.

---

## 1. Bản đồ Tài liệu Kiến trúc

| Tài liệu | Nội dung chính |
| :--- | :--- |
| **[architecture.md](./architecture.md)** | **Tổng quan Kiến trúc Hệ thống**: Multi-Module Monorepo, Hexagonal Architecture (Ports & Adapters), CQRS, Transactional Outbox, và Kafka Event Bus. |
| **[api-gateway.md](./api-gateway.md)** | **API Gateway & Routing**: Spring Cloud Gateway, Dynamic Routing, Correlation ID Tracing, Multi-Tenant Resolution, RFC 9457 Normalization. |
| **[saga-pattern.md](./saga-pattern.md)** | **Saga Pattern (Distributed Transactions)**: Điều phối giao dịch phân tán, State Machine, và cơ chế đền bù đảo ngược (Compensating Transactions). |
| **[scaling-to-services.md](./scaling-to-services.md)** | **Chiến lược & Kiến trúc Phân tách Dịch vụ**: Khung tiêu chí quyết định tách service (Fault Isolation, Divergent Scaling), sơ đồ phân rã, 3 quy tắc vàng, và playbook 5 bước bóc tách module. |
| **[domain-module-anatomy.md](./domain-module-anatomy.md)** | **Cấu trúc giải phẫu Microservice**: Hướng dẫn chi tiết trách nhiệm và vị trí của từng file qua 4 tầng: Domain, Application, Infrastructure, Presentation. |
| **[tenancy.md](./tenancy.md)** | **Kiến trúc Đa người thuê (Multi-Tenancy)**: Cơ chế cô lập dữ liệu theo Tenant xuyên suốt từ HTTP Header (`X-Tenant-Id`), Kafka Record Header đến Database Entity. |
| **[idempotency-and-tracing.md](./idempotency-and-tracing.md)** | **Chống trùng lặp & Truy vết Phân tán**: Vai trò, phân biệt và cách hiện thực `idempotencyId` (Idempotency Key / Message ID) và `trackingId` (Correlation ID / Trace ID). |
| **[observability.md](./observability.md)** | **Giám sát & Quan sát Hệ thống (Observability)**: 3 trụ cột Logs (Loki JSON), Metrics (Prometheus 4 Golden Signals), Traces (OpenTelemetry Tempo), và ma trận cảnh báo sự cố. |
| **[spring-boot-microservices-migration-plan.md](./spring-boot-microservices-migration-plan.md)** | **Kế hoạch Chuyển đổi Kiến trúc**: Lộ trình di trú từng bước từ Modular Monolith sang Microservices phân tán an toàn và kiểm soát rủi ro. |

---

## 2. Các Nguyên tắc Kiến trúc Cốt lõi (Architectural Principles)

1. **Pragmatic Architecture (Thực dụng & Cân bằng)**:
   - Nghiệp vụ lõi có tính ràng buộc giao dịch cao (Đơn hàng $\leftrightarrow$ Tồn kho $\leftrightarrow$ Bảng giá) được duy trì trong Bounded Context rõ ràng nhưng triển khai tập trung (**Modular Monolith**) để bảo toàn tính toàn vẹn **ACID**.
   - Chỉ tách thành các Service/Worker riêng biệt đối với các thành phần có đặc tính kỹ thuật đặc biệt:
     - **Tải đột biến / I-O Heavy (Divergent Scaling)**: `tiktok-integration` (Webhook ingestion, sync worker auto-scaling).
     - **Cô lập lỗi (Fault Isolation)**: `audit-service` (Append-only Kafka consumer), `notification-worker`.
     - **Cổng vào tập trung (Edge Routing)**: `api-gateway`.

2. **Domain-Driven Design (DDD) & Hexagonal Architecture**:
   - Tầng `Domain` hoàn toàn thuần túy Java (POJO), không phụ thuộc framework (`spring`, `jakarta`, `hibernate`).
   - Tầng `Application` điều phối nghiệp vụ thông qua `CommandBus` và `QueryBus`.
   - Tầng `Infrastructure` hiện thực các `Ports` (Repository, Kafka, Redis) thông qua Adapters.

3. **Event-Driven & Eventual Consistency**:
   - Giao tiếp giữa các service sử dụng **Transactional Outbox Pattern** kết hợp với **Apache Kafka**.
   - Không thực hiện gọi HTTP đồng bộ giữa các backend services để cập nhật dữ liệu.
   - Mọi Consumer xử lý sự kiện đều áp dụng **Idempotent Consumer Pattern** thông qua bảng `consumed_messages`.

4. **Multi-Tenancy từ gốc**:
   - Mọi thao tác đọc/ghi dữ liệu nghiệp vụ đều bắt buộc có ngữ cảnh `tenantId` (truyền qua `TenantContext`).
   - Không chia sẻ dữ liệu hoặc chạy query không có điều kiện lọc theo tenant.
