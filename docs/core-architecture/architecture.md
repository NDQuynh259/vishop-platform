# ViShop Platform — Architecture

## 1. Multi-Module Monorepo Structure

Dự án **ViShop Platform** được tổ chức theo cấu trúc Gradle Multi-Module trên nền tảng **Java 21 LTS** và **Spring Boot 3.3.x**, áp dụng **Domain-Driven Design (DDD)**, **CQRS (Command Query Responsibility Segregation)**, **Transactional Outbox**, và **Apache Kafka** làm event bus phân tán.

```text
vishop-platform/
├── platform-common/                     # Thư viện dùng chung (Shared libraries)
│   ├── common-domain/                   # Hạt nhân Domain thuần túy (AggregateRoot, Entity, VO, Event, CQRS interfaces)
│   ├── common-web/                      # Web & CQRS Bus: SpringCommandBus, SpringQueryBus, Problem Details (RFC 9457)
│   ├── common-security/                 # Multi-Tenancy context (TenantContext, TenantContextFilter)
│   ├── common-events/                   # Kafka Backbone (EventEnvelope, KafkaEventPublisher, DLQ, RecordInterceptor)
│   ├── common-persistence/              # Transactional Outbox, OutboxPublisher, ConsumedMessage (Idempotency)
│   ├── common-saga/                     # Saga Pattern Orchestrator (Distributed Transactions with Compensation)
│   ├── common-observability/            # Actuator & Micrometer Prometheus
│   └── common-test/                     # Testcontainers (PostgreSQL, Kafka)
│
├── services/                            # Các Microservices độc lập (Bounded Contexts)
│   ├── api-gateway/                     # Spring Cloud Gateway (:8000) - Unified Routing, CorrelationId, CORS
│   ├── iam-service/                     # Identity, system role, tenant, membership, RBAC và session
│   └── audit-service/                   # Event-driven consumer ghi nhận lịch sử audit log từ Kafka
│
├── infra/
│   └── docker-compose/                  # Môi trường chạy local: Kafka KRaft, Kafka UI, PostgreSQL 16, Redis 7
│
├── build.gradle.kts                      # Cấu hình build gốc
├── settings.gradle.kts                   # Đăng ký các modules
└── docs/                                # Tài liệu kiến trúc và hướng dẫn phát triển
```

---

## 2. Layered Architecture & Dependency Rule

Mỗi Microservice tuân thủ chặt chẽ mô hình **Hexagonal Architecture (Ports & Adapters) kết hợp DDD**:

```mermaid
flowchart TD
    %% ==========================================
    %% 1. PRESENTATION LAYER
    %% ==========================================
    subgraph Presentation["1. Presentation Layer (Driving Adapters)"]
        Controller["RestController\n(TenantController)"]
        KafkaSub["Kafka Consumer / Listeners\n(TenantEventListener)"]
    end

    %% ==========================================
    %% 2. APPLICATION LAYER
    %% ==========================================
    subgraph Application["2. Application Layer (Use Cases & CQRS)"]
        direction TB
        DTOs["DTOs\n(CreateTenantRequest, TenantResponseDto)"]
        CommandBus["CQRS Dispatcher\n(SpringCommandBus / SpringQueryBus)"]
        CmdHandler["Command Handlers\n(CreateTenantCommandHandler)"]
        QryHandler["Query Handlers\n(ListTenantsQueryHandler)"]
    end

    %% ==========================================
    %% 3. DOMAIN LAYER
    %% ==========================================
    subgraph Domain["3. Domain Layer (Pure Business Core - POJO)"]
        direction TB
        Aggregate["AggregateRoot & Value Objects\n(Tenant & TenantCode)"]
        DomainEvents["Domain Events\n(TenantCreatedEvent)"]
        Ports["Domain Repository Ports\n(TenantRepositoryPort Interface)"]
    end

    %% ==========================================
    %% 4. INFRASTRUCTURE LAYER
    %% ==========================================
    subgraph Infrastructure["4. Infrastructure Layer (Driven Adapters)"]
        direction TB
        RepoAdapter["TenantRepositoryAdapter\n(Spring Data JPA)"]
        OutboxRelay["OutboxService & OutboxPublisher\n(Transactional Outbox)"]
        KafkaPub["KafkaEventPublisher\n(Kafka Producer)"]
    end

    %% --- External Triggers ---
    Client([HTTP Client]) -->|1. REST Request| Controller
    KafkaBroker([Apache Kafka Broker]) -.->|Inbound Event| KafkaSub

    %% --- Presentation to Application ---
    Controller -->|Bọc Request| DTOs
    DTOs -->|Gửi Command / Query| CommandBus
    KafkaSub -->|Gửi Command từ Event| CommandBus
    CommandBus -->|Write Action| CmdHandler
    CommandBus -->|Read Action| QryHandler

    %% --- Application to Domain & Persistence ---
    CmdHandler -->|2. Thực thi Business Rule| Aggregate
    Aggregate -.->|3. Phát sinh| DomainEvents
    CmdHandler -->|4. Lưu Aggregate qua Port| Ports
    CmdHandler -->|5. Ghi nhận sự kiện| OutboxRelay
    QryHandler -->|Truy vấn dữ liệu| Ports

    %% --- Infrastructure Implementations ---
    RepoAdapter -.->|implements| Ports
    OutboxRelay -->|6. Chuyển tiếp Outbox| KafkaPub
    KafkaPub -.->|7. Bắn Message ra Topic| KafkaBroker

    %% --- Styling & Color Themes ---
    classDef pres fill:#EBF5FB,stroke:#2980B9,stroke-width:2px,color:#1B4F72;
    classDef app fill:#F4ECF7,stroke:#8E44AD,stroke-width:2px,color:#512E5F;
    classDef domain fill:#E8F8F5,stroke:#16A085,stroke-width:2px,color:#0E6251;
    classDef infra fill:#FEF9E7,stroke:#D4AC0D,stroke-width:2px,color:#7D6608;
    classDef ext fill:#EAEDED,stroke:#5D6D7E,stroke-width:2px,color:#2C3E50;

    class Controller,KafkaSub pres;
    class DTOs,CommandBus,CmdHandler,QryHandler app;
    class Aggregate,DomainEvents,Ports domain;
    class RepoAdapter,OutboxRelay,KafkaPub infra;
    class Client,KafkaBroker ext;
```

### Quy tắc phụ thuộc (Dependency Rules):
1. **Domain Layer**:
   * Tuyệt đối không phụ thuộc vào framework (không import Spring, JPA, Hibernate, Web).
   * Chỉ sử dụng Java thuần (`java.*`) và `platform-common:common-domain`.
   * Định nghĩa các Ports (Interfaces) cho các thao tác persistence hoặc external services.
2. **Application Layer**:
   * Điều phối nghiệp vụ thông qua CQRS Commands và Queries.
   * Phụ thuộc vào `Domain Layer` (Entities, Ports, Domain Events).
   * Không phụ thuộc vào tầng Presentation hay các công nghệ hạ tầng cụ thể.
3. **Infrastructure Layer**:
   * Cung cấp các Adapters hiện thực các Ports của Domain Layer (ví dụ: `TenantRepositoryAdapter` dùng Spring Data JPA).
   * Quản lý kết nối Database, Kafka Consumer/Producer, Redis Cache, S3 Storage.
4. **Presentation Layer**:
   * REST Controllers nhận HTTP Request, validate cú pháp với Jakarta Validation (`@Valid`).
   * Chuyển tiếp tác vụ vào Application Layer thông qua `CommandBus.dispatch()` hoặc `QueryBus.dispatch()`.
   * Trả về kết quả bọc trong `ListEnvelope` hoặc RFC 9457 `ProblemDetail`.

---

## 3. CQRS Pattern (Command Query Responsibility Segregation)

Dự án phân tách triệt để luồng **Write (Command)** và luồng **Read (Query)**:

### 3.1. Luồng Ghi (Write Path)
```text
HTTP POST/PATCH
   │
   ▼
TenantController
   │ commandBus.dispatch(new CreateTenantCommand(code, name))
   ▼
SpringCommandBus (Định tuyến tự động qua Spring Context)
   │
   ▼
CreateTenantCommandHandler (@Transactional)
   ├── 1. Kiểm tra quy tắc nghiệp vụ qua domain port: tenantRepository.existsByCode(code)
   ├── 2. Khởi tạo Aggregate Root: Tenant.create(TenantCode.of(code), name)
   │      └── Aggregate nội bộ sinh TenantCreatedEvent và lưu vào memory list
   ├── 3. Lưu Aggregate: tenantRepository.save(tenant)
   ├── 4. Ghi Domain Events vào Transactional Outbox trong transaction của service
   └── 5. Trả về TenantResponseDto
```

### 3.2. Luồng Đọc (Read Path)
```text
HTTP GET /api/v1/tenants?limit=20&cursor=...
   │
   ▼
TenantController
   │ queryBus.dispatch(new ListTenantsQuery(filter, pagination))
   ▼
SpringQueryBus
   │
   ▼
ListTenantsQueryHandler (@Transactional(readOnly = true))
   ├── 1. Giải mã opaque cursor (CursorPagination.decode(cursor))
   ├── 2. Query trực tiếp DB tối ưu qua Spring Data JPA
   └── 3. Trả về PagedResult<TenantResponseDto> (có hasNextPage và nextCursor)
   ▼
TenantController
   └── Bọc PagedResult vào ListEnvelope<TenantResponseDto> (kèm request URL) trả về HTTP client
```

---

## 4. Transactional Outbox & Kafka Event Backbone

Để bảo đảm tính nhất quán dữ liệu giữa Database và Message Broker (tránh hiện tượng mất event hoặc event gửi đi nhưng DB rollback), ViShop sử dụng **Transactional Outbox Pattern**:

```mermaid
sequenceDiagram
    autonumber
    participant App as CommandHandler
    participant DB as PostgreSQL (tenants & outbox_events)
    participant Relay as OutboxPublisher (Background Job)
    participant Kafka as Apache Kafka
    participant Consumer as AuditService (Kafka Listener)

    App->>DB: BEGIN TRANSACTION
    App->>DB: INSERT / UPDATE tenants
    App->>DB: INSERT outbox_events (EventEnvelope JSON, status='PENDING')
    App->>DB: COMMIT TRANSACTION
    
    Note over Relay,Kafka: Bất đồng bộ (Mỗi 1000ms)
    Relay->>DB: SELECT * FROM outbox_events WHERE status = 'PENDING'
    Relay->>Kafka: send(record) với key = aggregateId
    Kafka-->>Relay: ACK
    Relay->>DB: UPDATE outbox_events SET status = 'PUBLISHED', published_at = NOW()
    
    Kafka->>Consumer: onEvent(record)
    Consumer->>DB: IdempotentConsumer check (consumed_messages)
    Consumer->>DB: INSERT audit_logs & INSERT consumed_messages
```

### 4.1. Quy tắc đặt tên Topic Kafka
* Format chuẩn: `vishop.{aggregate_kebab_case}.events` (ví dụ: `vishop.tenant.events`).
* Dead-Letter Topic: `{topic}.DLT` (ví dụ: `vishop.tenant.events.DLT`).
* Partition Key: Luôn sử dụng `aggregateId` làm message key để đảm bảo thứ tự message cho từng aggregate.

### 4.2. Consumer Idempotency & Error Handling
* **Deduplication**: Mọi consumer sử dụng `IdempotentConsumer` ghi nhận `(message_id, consumer_group)` vào bảng `consumed_messages` để ngăn chặn duplicate execution do cơ chế at-least-once của Kafka.
* **Retry Backoff**: Tự động retry theo cấp số nhân (1s, 2s, 4s). Nếu quá số lần cho phép, đẩy message lỗi sang topic `.DLT`.
* **Multi-Tenancy Propagation**: Header `X-Tenant-Id` được tự động trích xuất từ Kafka record bởi `TenantRecordInterceptor` và thiết lập vào `TenantContext`.

---

## 5. Multi-Tenancy & Context Propagation

* Mỗi request HTTP phải kèm header `X-Tenant-Id` (UUID).
* `TenantContextFilter` trích xuất `X-Tenant-Id` và lưu vào `TenantContext` (`ThreadLocal<UUID>`).
* Toàn bộ các bảng nghiệp vụ kế thừa `BaseTenantJpaEntity` chứa cột `tenant_id`.
* Khi gửi event qua Kafka, `KafkaEventPublisher` tự động đính kèm header `X-Tenant-Id`.
* `TenantRecordInterceptor` giải mã header `X-Tenant-Id` và thiết lập `TenantContext` trong thread xử lý event.

---

## 6. Distributed Transactions — Saga Pattern

Để xử lý các giao dịch phức tạp cần bảo đảm tính nhất quán sau cùng (Eventual Consistency) trải dài qua nhiều microservices, ViShop sử dụng **Saga Orchestrator** trong [`platform-common:common-saga`](../../platform-common/common-saga):
* **State Machine**: Vòng đời Saga (`STARTED`, `EXECUTING`, `COMPENSATING`, `COMPENSATED`, `COMPLETED`, `FAILED`).
* **Reverse Compensation**: Tự động rollback các bước trước đó theo chiều ngược lại (LIFO) khi có bất kỳ step nào fail.
* **Persistent Instances**: Lưu trữ trạng thái Saga trong bảng `saga_instances` bằng transaction độc lập `REQUIRES_NEW`.
* Chi tiết xem thêm tại [saga-pattern.md](./saga-pattern.md).

---

## 7. API Gateway (Spring Cloud Gateway)

`services/api-gateway` hoạt động trên cổng `8000` đóng vai trò là Reverse Proxy và Gateway trung tâm:
* **Dynamic Routing**: Chuyển tiếp request tới `iam-service:8080` và `audit-service:8082`.
* **Distributed Tracing**: Tự động sinh hoặc kế thừa `X-Correlation-Id` qua `CorrelationIdGlobalFilter`.
* **Multi-Tenancy**: Kiểm tra và chuyển tiếp `X-Tenant-Id` qua `TenantResolutionGlobalFilter`.
* **Error Normalization**: Bắt các lỗi hạ tầng (503 Service Unavailable, timeout) và chuẩn hóa về **RFC 9457 Problem Details**.
* Chi tiết xem thêm tại [api-gateway.md](./api-gateway.md).

---

## 8. Database Persistence & Schema Migration Strategy (JPA, Hibernate, Flyway)

Hệ thống ViShop Platform áp dụng chiến lược phân tách rành mạch giữa **Quản trị cấu trúc Schema (Database as Code)** và **Thao tác dữ liệu nghiệp vụ (Data Access Object / ORM)**.

```mermaid
flowchart TD
    subgraph AppStartup["Khởi động Microservice (Startup Lifecycle)"]
        direction TB
        FwCheck["1. Flyway Database Migration\n(Quét db/migration/V*.sql)"]
        FwHistory["Đối chiếu flyway_schema_history\n& Kiểm tra Checksum"]
        FwApply["Thực thi DDL SQL tuần tự\n(Tạo bảng, Partial Index, JSONB)"]
        
        JpaVal["2. Spring Data JPA / Hibernate\n(ddl-auto: validate)"]
        JpaCheck["Chỉ đọc metadata so sánh với @Entity\n(Tuyệt đối KHÔNG chạy DDL)"]
        AppReady["3. Microservice Ready\n(Sẵn sàng nhận Traffic)"]

        FwCheck --> FwHistory --> FwApply --> JpaVal --> JpaCheck --> AppReady
    end

    classDef step fill:#EBF5FB,stroke:#2980B9,stroke-width:2px,color:#1B4F72;
    classDef check fill:#FEF9E7,stroke:#D4AC0D,stroke-width:2px,color:#7D6608;
    classDef ready fill:#E8F8F5,stroke:#16A085,stroke-width:2px,color:#0E6251;
    class FwCheck,FwApply,JpaVal step;
    class FwHistory,JpaCheck check;
    class AppReady ready;
```

### 8.1. Phân biệt bức tranh 3 tầng: JPA vs Hibernate vs Spring Data JPA

Mô hình kiến trúc tương tác cơ sở dữ liệu trong các service bao gồm 3 tầng lồng nhau:

```text
┌────────────────────────────────────────────────────────────────────────┐
│  Tầng 3: Spring Data JPA (Data Access Layer)                          │
│  → Cung cấp JpaRepository, JpaSpecificationExecutor                    │
│  → Ví dụ: SpringDataTenantRepository, TenantSpecifications             │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (gọi xuống thực thi)
┌───────────────────────────────────▼────────────────────────────────────┐
│  Tầng 2: Hibernate (ORM Engine)                                        │
│  → Động cơ sinh câu lệnh SQL thực tế chạy ngầm                         │
│  → Tự động map ResultSets thành Entity Objects                         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (tuân thủ tiêu chuẩn)
┌───────────────────────────────────▼────────────────────────────────────┐
│  Tầng 1: JPA Specification (Jakarta Persistence API)                  │
│  → Chuẩn giao diện: @Entity, @Table, @Column, EntityManager            │
│  → Không chứa code thực thi kết nối DB                                 │
└────────────────────────────────────────────────────────────────────────┘
```

* **Nguyên tắc đóng gói:** Toàn bộ mã nguồn nghiệp vụ trong tầng `infrastructure/persistence` chỉ import chuẩn JPA thuần (`jakarta.persistence.*`, `org.springframework.data.jpa.*`), **không import trực tiếp các thư viện độc quyền của Hibernate** (`org.hibernate.*`).
* **Động cơ thực thi:** Hibernate đóng vai trò là "JPA Provider" chạy ngầm bên dưới, được Spring Boot tự động tích hợp thông qua `spring-boot-starter-data-jpa`.

---

### 8.2. Quản lý phiên bản Schema với Flyway (Database as Code)

Trong môi trường phân tán nhiều microservices và nhiều cụm môi trường (Local, Dev, Staging, Production), **Flyway** được chọn làm công cụ quản lý phiên bản cơ sở dữ liệu (Database Migration Tool) tương tự như vai trò của Git đối với mã nguồn.

#### Quy ước đặt tên file Migration:
Tất cả các file migration được lưu tại `src/main/resources/db/migration/` theo cấu trúc:
$$\mathbf{V}\langle\text{Version}\rangle\mathbf{\_\_}\langle\text{Mô\_tả}\rangle\mathbf{.sql}$$
*(Ví dụ: `V1__init_tenant_schema.sql`, `V2__add_tenant_domains.sql`). Giữa Version và Mô tả bắt buộc có 2 dấu gạch dưới `__`.*

#### Cơ chế tự động hóa:
1. Khi service khởi động, Flyway tự tạo bảng `flyway_schema_history` trong PostgreSQL (nếu chưa có).
2. Flyway quét thư mục migration, so sánh mã băm (Checksum) và danh sách file đã chạy trong `flyway_schema_history`.
3. Chỉ những file mới (version cao hơn) mới được thực thi tuần tự. Nếu phát hiện file cũ đã chạy bị sửa đổi nội dung, Flyway sẽ ngắt khởi động ngay lập tức để bảo vệ tính toàn vẹn dữ liệu.

#### Cấu trúc 3 nhóm bảng nền tảng trong mỗi Microservice:
Trong file khởi tạo ban đầu (ví dụ [V1__create_iam_schema.sql](../../services/iam-service/src/main/resources/db/migration/V1__create_iam_schema.sql)), phân tách các nhóm bảng nghiệp vụ và hạ tầng:
* **Core Business Tables** (ví dụ `tenants`): Chứa dữ liệu nghiệp vụ chính, tối ưu hóa index cursor (`created_at DESC, id DESC`) và status.
* **Transactional Outbox Table** (`outbox_events`): Lưu trữ các domain event cùng transaction DB với entity nghiệp vụ nhằm giải quyết triệt để bài toán **Dual-Write**.
  ```sql
  CREATE INDEX idx_outbox_events_pending ON outbox_events (occurred_at ASC)
      WHERE status = 'PENDING';
  ```
  *Sử dụng **Partial Index** lọc riêng bản ghi `PENDING`, giúp background poller quét cực nhanh mà không bị ảnh hưởng bởi hàng triệu event đã `PUBLISHED`.*
* **Idempotent Consumed Messages Table** (`consumed_messages`): Lưu vết `(message_id, consumer_group)` của các message Kafka đã xử lý, đảm bảo tính **Idempotency** (ngăn chặn duplicate processing khi Kafka retry hoặc rebalance).

---

### 8.3. Nguyên tắc bất di bất dịch: Tuyệt đối không để JPA tự sửa đổi Database

Trong dự án thực tế và Microservices doanh nghiệp, tính năng tự sinh DDL của Hibernate/JPA (`ddl-auto: create`, `ddl-auto: update`, hoặc `generate-ddl: true`) **bị cấm tuyệt đối** vì các lý do:
1. **Không tạo được Index chuyên sâu**: JPA không hỗ trợ tự sinh Partial Index (`WHERE status = 'PENDING'`) hay Composite Cursor Index tối ưu theo chiều DESC.
2. **Không kiểm soát được kiểu dữ liệu đặc thù**: PostgreSQL có các kiểu dữ liệu mạnh mẽ như `JSONB` (dùng cho payload event), `TIMESTAMPTZ` (chuẩn UTC). JPA tự sinh thường mapping thành `BYTEA` hoặc `VARCHAR`.
3. **Nguy cơ mất dữ liệu và sập Production**: Khi đổi tên trường, `ddl-auto: update` sẽ tạo thêm cột mới chứ không migrate dữ liệu từ cột cũ. Trên Production có hàng triệu dòng, Hibernate tự chạy `ALTER TABLE` có thể gây **Lock toàn bộ bảng (Table Lock)**, dẫn đến gián đoạn dịch vụ nghiêm trọng.
4. **Không quản lý được bảng phi-Entity**: Bảng hạ tầng như `consumed_messages` được truy xuất qua JDBC thuần tốc độ cao, không có `@Entity` nên JPA không thể tự sinh.

#### Cấu hình chuẩn trong `application.yml`:
```yaml
spring:
  jpa:
    open-in-view: false          # Tắt OSIV để tránh leak DB connection
    generate-ddl: false          # Tắt tự sinh DDL ở tầng chuẩn JPA
    hibernate:
      ddl-auto: validate         # CHỈ KIỂM TRA ĐỐI CHIẾU, TUYỆT ĐỐI KHÔNG SỬA ĐỔI
```
* **Chế độ `validate`**: Hibernate chỉ gửi truy vấn `SELECT` đọc schema metadata để đối chiếu xem bảng, cột trong DB (do Flyway tạo) có khớp với các `@Entity` không. Nếu code Java sai lệch hoặc thiếu cột, service sẽ từ chối khởi động (fail-fast) ngay lập tức.

#### Phân quyền bảo mật tại tầng Database (Production Security Best Practice):
* **User ứng dụng (Spring Boot connection)**: Chỉ được cấp quyền DML:
  ```sql
  GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO vishop_app;
  ```
  *(Cắt hoàn toàn quyền DDL như `CREATE`, `ALTER`, `DROP`. Kể cả khi cấu hình vô tình bật `update`, JPA cũng không thể sửa đổi DB).*
* **User Migration (Flyway CI/CD pipeline)**: User riêng biệt duy nhất được cấp quyền DDL khi chạy deployment script.

---

### 8.4. Bảng phân định trách nhiệm

| Trách nhiệm | Thành phần phụ trách | Phương thức thực hiện |
| :--- | :--- | :--- |
| **Quản trị cấu trúc Schema (DDL)**<br>*(Tạo bảng, đổi kiểu cột, index, constraint)* | **Flyway** | Viết file SQL version tuần tự (`V1__...sql`, `V2__...sql`) |
| **Thao tác dữ liệu nghiệp vụ (DML)** | **JDBC hoặc JPA theo service** | IAM chia adapter theo phân hệ: [TenantRepositoryAdapter](../../services/iam-service/src/main/java/com/vishop/iam/modules/tenant/infrastructure/persistence/adapters/TenantRepositoryAdapter.java) dùng JPA; [MembershipRepositoryAdapter](../../services/iam-service/src/main/java/com/vishop/iam/modules/access/infrastructure/persistence/adapters/MembershipRepositoryAdapter.java) dùng JDBC với tenant predicate tường minh |
| **Thực thi truy vấn ORM & Quản lý Session**<br>*(Dirty checking, First-level cache, L2 cache)* | **Hibernate** | Chạy ngầm bên dưới, được kiểm soát chặt chẽ bởi `ddl-auto: validate` |
| **Thao tác bảng hạ tầng tốc độ cao**<br>*(Idempotency deduplication check)* | **Spring JDBC / Template** | Thao tác trực tiếp trên bảng `consumed_messages` với độ trễ thấp nhất |


