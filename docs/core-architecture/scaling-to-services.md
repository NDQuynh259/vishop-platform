# Chiến lược & Kiến trúc Phân tách Dịch vụ (Service Decomposition & Splitting Strategy)

Tài liệu này đặc tả toàn bộ chiến lược phân tách dịch vụ, ranh giới các Bounded Contexts, các quy tắc bất biến khi chia nhỏ hệ thống, và quy trình từng bước để tách một module nghiệp vụ thành một microservice độc lập trong **ViShop Platform**.

---

## 1. Khung Tiêu chí Quyết định Tách Service (Decision Framework)

Không phân tách service theo cảm tính, theo từng màn hình hoặc theo từng bảng đơn lẻ trong cơ sở dữ liệu. Một Bounded Context chỉ được xem xét tách ra thành một Microservice độc lập khi thỏa mãn ít nhất **1 trong 3 động lực kiến trúc cốt lõi**:

```mermaid
flowchart TD
    Start{"Đánh giá một Bounded Context"}
    
    Q1{"1. Có đặc tính tải đột biến / I-O nặng?\n(Divergent Scaling)"}
    Q2{"2. Có yêu cầu cô lập lỗi tuyệt đối?\n(Fault Isolation)"}
    Q3{"3. Có đội ngũ kỹ sư riêng biệt?\n(Team Ownership / Conway's Law)"}
    
    KeepCore["GIỮ TRONG COMMERCE-CORE\n(Modular Monolith)\n- Ràng buộc ACID cao (Đơn hàng - Kho - Giá)\n- Tránh Distributed Transactions (Saga)\n- Local development nhẹ, triển khai nhanh"]
    SplitService["TÁCH THÀNH MICROSERVICE ĐỘC LẬP\n- Có Database riêng (Database-per-service)\n- Có Container / Pod runtime riêng\n- Giao tiếp 100% qua Kafka Outbox & Idempotency"]

    Start --> Q1
    Q1 -- Có (VD: TikTok Sync, Webhook Ingestion) --> SplitService
    Q1 -- Không --> Q2
    Q2 -- Có (VD: Audit Log, Notification) --> SplitService
    Q2 -- Không --> Q3
    Q3 -- Có (Squad riêng > 10 dev) --> SplitService
    Q3 -- Không --> KeepCore

    classDef split fill:#FEF9E7,stroke:#D4AC0D,stroke-width:2px,color:#7D6608;
    classDef keep fill:#E8F8F5,stroke:#16A085,stroke-width:2px,color:#0E6251;
    classDef check fill:#EBF5FB,stroke:#2980B9,stroke-width:2px,color:#1B4F72;

    class SplitService split;
    class KeepCore keep;
    class Start,Q1,Q2,Q3 check;
```

---

## 2. Sơ đồ Phân tách Dịch vụ Tổng thể (Service Decomposition Map)

Dựa trên khung tiêu chí trên, hệ thống ViShop Platform áp dụng mô hình **Pragmatic Modular Microservices** gồm **1 API Gateway + 1 Core Modular Monolith + 2 Satellite Microservices**:

```mermaid
flowchart TB
    Client["Client Applications\n(Web Admin, POS, Mobile, TikTok Platform)"]
    
    subgraph Edge["Tầng Cổng (Edge & Routing)"]
        Gateway["api-gateway (:8000)\n- Dynamic Routing\n- X-Correlation-Id & X-Request-Id\n- Multi-Tenancy Filter (X-Tenant-Id)\n- Rate Limiting & Auth Check"]
    end

    Client -->|REST HTTPS| Gateway

    subgraph CoreDomain["Tầng Nghiệp Vụ Giao Dịch ACID (Core Transactional)"]
        subgraph CommerceApp["commerce-core (:8080) [Modular Monolith]"]
            direction TB
            subgraph CoreModules["Các Module nghiệp vụ gắn kết cao"]
                TenantM["Tenant Module\n(Org, Shop, Role)"]
                IdentityM["Identity Module\n(User, Auth, 2FA)"]
                CatalogM["Catalog Module\n(SKU, Combo, Pricing)"]
                InvM["Inventory Module\n(Warehouse, Stock)"]
                OrderM["Order Module\n(Checkout, State Machine)"]
                FinanceM["Finance Module\n(COD, Reconciliation)"]
            end
            CoreOutbox["Transactional Outbox Relay"]
        end
    end

    subgraph SatelliteServices["Tầng Vệ Tinh Độc Lập (Specialized Microservices)"]
        subgraph TikTokSvc["tiktok-integration (:8083) [Tải cao / I-O Heavy]"]
            direction TB
            Hook["Webhook Ingestion (Fast Ack < 50ms)"]
            Sync["Auto-sync Worker (KEDA Lag Scale)"]
            Token["OAuth Token Vault & Refresher"]
        end

        subgraph AuditSvc["audit-service (:8082) [Bất biến / Cô lập lỗi]"]
            AuditConsumer["Idempotent Consumer"]
            AuditLog["Append-only Log Writer"]
        end
    end

    subgraph DataTier["Tầng Cơ Sở Dữ Liệu Độc Lập (Database-per-Service)"]
        DBCore[("vishop_commerce\n(orders, stock, catalog, tenants)")]
        DBTikTok[("vishop_tiktok\n(webhook_inbox, tokens, sync_state)")]
        DBAudit[("vishop_audit\n(audit_logs, consumed_messages)")]
        RedisCore[("Redis 7\n(Locks, Cache, Rate Limit)")]
    end

    subgraph MessageBus["Hạ Tầng Sự Kiện (Event-Driven Backbone)"]
        Kafka["Apache Kafka KRaft\nTopics: vishop.order.events | vishop.stock.events | vishop.tiktok.events"]
    end

    %% Gateway Routing
    Gateway -->|/api/v1/tenants, /orders, /products...| CommerceApp
    Gateway -->|/api/v1/tiktok/**| TikTokSvc
    Gateway -->|/api/v1/audit/**| AuditSvc

    %% Database Connections
    CommerceApp --> DBCore
    TikTokSvc --> DBTikTok
    TikTokSvc --> RedisCore
    AuditSvc --> DBAudit

    %% Event Flows
    CommerceApp -.->|Ghi Outbox| CoreOutbox
    CoreOutbox ==>|Publish Domain Events| Kafka
    TikTokSvc ==>|Publish TikTok Events| Kafka
    Kafka ==>|Subscribe TikTok Order/Stock| CommerceApp
    Kafka ==>|Subscribe All Events| AuditConsumer

    %% Styling
    classDef pres fill:#EBF5FB,stroke:#2980B9,stroke-width:2px,color:#1B4F72;
    classDef app fill:#F4ECF7,stroke:#8E44AD,stroke-width:2px,color:#512E5F;
    classDef domain fill:#E8F8F5,stroke:#16A085,stroke-width:2px,color:#0E6251;
    classDef infra fill:#FEF9E7,stroke:#D4AC0D,stroke-width:2px,color:#7D6608;
    classDef ext fill:#EAEDED,stroke:#5D6D7E,stroke-width:2px,color:#2C3E50;

    class Gateway pres;
    class CommerceApp domain;
    class TikTokSvc,AuditSvc infra;
    class DBCore,DBTikTok,DBAudit,RedisCore,Kafka ext;
```

---

## 3. Ranh giới Bounded Contexts & Cơ sở Dữ liệu

Mỗi Service sở hữu hoàn toàn Bounded Context và Database riêng của mình:

| Dịch vụ / Module | Bounded Context | Trách nhiệm chính | Database Schema |
| :--- | :--- | :--- | :--- |
| **`api-gateway`** | Edge / Gateway | Routing, xác thực token thô, phân giải `X-Tenant-Id`, tạo `X-Correlation-Id`, chuẩn hóa RFC 9457 | *Không có DB* |
| **`commerce-core`** *(Modular Monolith)* | Tenants, Users, Catalog, Inventory, Orders, Finance | Quản lý toàn bộ vòng đời sản phẩm, tồn kho, đơn hàng, thanh toán và tổ chức trong cùng 1 đơn vị transaction ACID | `vishop_commerce` (PostgreSQL) |
| **`tiktok-integration`** *(Specialized Microservice)* | TikTok Marketplace, Webhooks, OAuth | Nhận webhook TikTok cực nhanh, quản lý OAuth Token, đồng bộ đơn hàng và tồn kho 2 chiều theo rate limit của sàn | `vishop_tiktok` (PostgreSQL) + Redis |
| **`audit-service`** *(Isolated Consumer)* | Audit Trails | Lắng nghe toàn bộ event từ Kafka để ghi nhận nhật ký kiểm toán bất biến (append-only) | `vishop_audit` (PostgreSQL) |

---

## 4. Ba Quy tắc Vàng khi Tách Dịch vụ (The Golden Rules)

### Quy tắc 1: Cơ sở dữ liệu hoàn toàn độc lập (Database-per-Service)
1. **Tuyệt đối không có Foreign Key giữa các Database khác nhau**:
   - Khóa ngoại chéo (`tenantId`, `userId`, `orderId`, `productId`) chỉ được lưu dưới dạng giá trị tham chiếu `UUID v7`.
2. **Tuyệt đối không JOIN bảng xuyên Database**:
   - Dữ liệu cần tổng hợp từ nhiều context được thực hiện thông qua:
     * **CQRS Read Model / Materialized Views** đồng bộ bất đồng bộ qua Kafka.
     * Hoặc tổng hợp ở tầng API Gateway / BFF qua các lời gọi query song song.

### Quy tắc 2: Zero Synchronous Write (Không gọi HTTP chéo để ghi dữ liệu)
- Không dùng HTTP `POST / PUT / DELETE` gọi trực tiếp từ Service A sang Service B để cập nhật dữ liệu.
- Mọi thao tác ghi dữ liệu liên service **bắt buộc sử dụng Transactional Outbox Pattern + Kafka Event**.
- *Chỉ cho phép HTTP GET hoặc gRPC*: Áp dụng cho các truy vấn tra cứu thông tin tức thời (Read-only lookup) hoặc kiểm tra validation khẩn cấp mà cache chưa kịp có. Chi tiết xem tại [Chiến lược giao tiếp liên dịch vụ (Kafka vs REST vs gRPC)](./inter-service-communication.md).

### Quy tắc 3: Idempotent Consumer ở mọi đầu nhận
- Do cơ chế của Kafka là *At-Least-Once Delivery*, mọi consumer nhận sự kiện bắt buộc phải kiểm tra trùng lặp thông qua [`IdempotentConsumer`](../../platform-common/common-persistence/src/main/java/io/github/coreplatform/common/persistence/idempotency/IdempotentConsumer.java) và bảng `consumed_messages`.

---

## 5. Quy trình 5 Bước để "Xé" một Module thành Microservice Độc lập

Khi hệ thống phát triển, nếu một module trong `commerce-core` (ví dụ: `inventory` hoặc `finance`) đủ lớn và cần tách riêng thành `inventory-service`, hãy thực hiện theo đúng **Playbook 5 bước** sau để đảm bảo không downtime và không vỡ dữ liệu:

```text
[Bước 1: Khóa Ranh Giới Code] ──> [Bước 2: Tách DB Schema] ──> [Bước 3: Chuyển sang Event Outbox]
                                                                             │
[Bước 5: Trỏ Route tại Gateway] <── [Bước 4: Tạo Module Gradle & Pod Mới] <──┘
```

### Bước 1: Khóa ranh giới code (Code Boundary Enforcement)
- Đảm bảo module mục tiêu (ví dụ: `inventory`) chỉ tương tác với các module khác thông qua Java Interface (`Ports`) hoặc Domain Event nội bộ.
- Cấm triệt để việc các module khác import trực tiếp JPA Entity hoặc Repository của module này.

### Bước 2: Tách Database Schema (Database Decoupling)
- Gỡ bỏ mọi ràng buộc Foreign Key cứng trỏ vào bảng của module (thay bằng cột `UUID`).
- Chuyển các bảng sang schema riêng (`inventory.*`) hoặc Database PostgreSQL mới (`vishop_inventory`).
- Chạy batch backfill dữ liệu cũ sang database mới nếu cần.

### Bước 3: Chuyển đổi giao tiếp sang Kafka Event (Event Decoupling)
- Thay thế lời gọi hàm in-memory giữa `order` và `inventory` bằng luồng sự kiện bất đồng bộ:
  * `OrderCreatedEvent` $\rightarrow$ Kafka $\rightarrow$ `inventory` lắng nghe và trừ kho.
  * `StockReservedEvent` hoặc `StockExhaustedEvent` $\rightarrow$ Kafka $\rightarrow$ `order` nhận để xác nhận đơn hoặc kích hoạt Saga hủy đơn.

### Bước 4: Tạo Module Gradle mới trong `services/`
- Tạo thư mục `services/inventory-service` với file `build.gradle.kts`.
- Khai báo subproject vào [settings.gradle.kts](../../settings.gradle.kts).
- Cấu hình port riêng (ví dụ: `:8084`) và Dockerfile container riêng.

### Bước 5: Cấu hình định tuyến tại API Gateway
- Bổ sung cấu hình route trong [services/api-gateway/src/main/resources/application.yml](../../services/api-gateway/src/main/resources/application.yml):
  ```yaml
  - id: inventory-service
    uri: ${INVENTORY_SERVICE_URL:http://localhost:8084}
    predicates:
      - Path=/api/v1/inventory/**, /api/v1/warehouses/**
  ```
- Kiểm thử luồng dữ liệu song song (Canary / Dual-run) trước khi chuyển đổi 100% traffic sang service mới.
