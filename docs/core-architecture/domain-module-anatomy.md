# Cấu trúc giải phẫu Microservice (Domain Module Anatomy)

Tài liệu này giải thích chi tiết mục đích và trách nhiệm của từng file bên trong một Microservice theo chuẩn **Domain-Driven Design (DDD) + Hexagonal Architecture + CQRS**, lấy ví dụ thực tế từ [`services/tenant-service`](../../services/tenant-service).

---

## 1. Cấu trúc thư mục của một Microservice

```text
services/tenant-service/src/main/
├── java/com/vishop/tenant/
│   ├── TenantApplication.java                # Spring Boot Main Entry Point
│   │
│   ├── domain/                               # [Layer 1: Domain - Thuần túy nghiệp vụ]
│   │   ├── Tenant.java                       # Aggregate Root
│   │   ├── TenantCode.java                   # Value Object
│   │   ├── TenantStatus.java                 # Domain Enum
│   │   ├── TenantRepositoryPort.java         # Domain Repository Port (Interface)
│   │   └── event/
│   │       ├── TenantCreatedEvent.java       # Domain Event
│   │       └── TenantStatusChangedEvent.java # Domain Event
│   │
│   ├── application/                          # [Layer 2: Application - Use Cases & CQRS]
│   │   ├── command/                          # CQRS Write Operations
│   │   │   ├── CreateTenantCommand.java
│   │   │   ├── CreateTenantCommandHandler.java
│   │   │   ├── ChangeTenantStatusCommand.java
│   │   │   └── ChangeTenantStatusCommandHandler.java
│   │   ├── query/                            # CQRS Read Operations
│   │   │   ├── GetTenantByIdQuery.java
│   │   │   ├── GetTenantByIdQueryHandler.java
│   │   │   ├── ListTenantsQuery.java
│   │   │   └── ListTenantsQueryHandler.java
│   │   └── dto/                              # DTOs giao tiếp
│   │       ├── CreateTenantRequest.java
│   │       ├── ChangeTenantStatusRequest.java
│   │       └── TenantResponseDto.java
│   │
│   ├── infrastructure/                       # [Layer 3: Infrastructure - Adapters]
│   │   └── persistence/
│   │       ├── TenantJpaEntity.java          # JPA Table Mapping
│   │       ├── SpringDataTenantRepository.java # Spring Data JPA
│   │       └── TenantRepositoryAdapter.java  # Hiện thực Domain Port
│   │
│   └── presentation/                         # [Layer 4: Presentation - Controllers]
│       └── rest/
│           └── TenantController.java         # HTTP REST API
│
└── resources/
    ├── application.yml                       # Cấu hình runtime (port, DB, Kafka)
    └── db/migration/                         # Flyway DB Migrations
        └── V1__init_tenant_schema.sql
```

---

## 2. Chi tiết từng tầng và vai trò của từng file

### 2.1. Tầng Domain (`domain/`)

Tầng trung tâm chứa toàn bộ quy tắc nghiệp vụ cốt lõi. **Không import Spring, JPA, Hibernate hay bất kỳ framework nào.**

#### `Tenant.java` — Aggregate Root
* Kế thừa từ `AggregateRoot<UUID>`.
* Constructor đặt ở chế độ `private` để bắt buộc khởi tạo qua 2 static factory methods:
  * `create(TenantCode code, String name)`: Dùng khi tạo mới nghiệp vụ, nội bộ sinh ra `TenantCreatedEvent` lưu vào danh sách domain events.
  * `reconstitute(...)`: Dùng bởi Repository Adapter để tái dựng Aggregate từ database mà **không** kích hoạt domain events.
* Không có setter công khai. Trạng thái chỉ được thay đổi qua các phương thức nghiệp vụ: `activate()`, `suspend()`, `archive()`, `updateName()`.

#### `TenantCode.java` — Value Object
* Cài đặt dưới dạng Java `record` kế thừa interface `ValueObject`.
* Đảm bảo tính bất biến (immutable) và tự kiểm tra tính hợp lệ (invariants) trong constructor:
  ```java
  public record TenantCode(String value) implements ValueObject {
      private static final Pattern PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$");
      public TenantCode {
          if (!PATTERN.matcher(value).matches()) {
              throw new DomainException("invalid_tenant_code", "Mã tenant không hợp lệ: " + value);
          }
      }
  }
  ```

#### `event/TenantCreatedEvent.java` — Domain Event
* Cài đặt bằng `record` implements `DomainEvent`.
* Lưu lại dữ liệu sự kiện đã diễn ra trong quá khứ (`tenantId`, `code`, `name`, `status`, `occurredAt`).

#### `TenantRepositoryPort.java` — Repository Port
* Là một interface Java thuần túy định nghĩa các phương thức lưu trữ Aggregate Root mà nghiệp vụ cần, ví dụ:
  ```java
  public interface TenantRepositoryPort extends DomainRepository<Tenant, UUID> {
      Optional<Tenant> findByCode(String code);
      boolean existsByCode(String code);
      List<Tenant> findTenants(Instant cursorCreatedAt, UUID cursorId, int limit);
  }
  ```

---

### 2.2. Tầng Application (`application/`)

Tầng điều phối luồng thực thi (Use Cases) theo mô hình **CQRS**.

#### `command/CreateTenantCommand.java` & `CreateTenantCommandHandler.java`
* `Command` là một record mang dữ liệu đầu vào thực hiện hành động ghi.
* `CommandHandler` bọc trong transaction (`@Transactional`):
  1. Kiểm tra ràng buộc nghiệp vụ qua `tenantRepository.existsByCode(...)`.
  2. Tạo mới Aggregate: `Tenant.create(...)`.
  3. Lưu Aggregate: `tenantRepository.save(tenant)`.
  4. Đưa Domain Events vào Outbox: `outboxService.saveEventsFor(tenant, "tenant-service")` để OutboxPublisher gửi Kafka bất đồng bộ.
  5. Trả về `TenantResponseDto`.

#### `query/ListTenantsQuery.java` & `ListTenantsQueryHandler.java`
* `Query` đại diện cho yêu cầu đọc dữ liệu (`@Transactional(readOnly = true)`), chỉ nhận tham số đọc thuần túy (`limit`, `cursor`), không phụ thuộc vào HTTP request URL.
* `ListTenantsQueryHandler` giải mã cursor (`CursorPagination.decode(...)`), query dữ liệu trực tiếp và trả về `PagedResult<TenantResponseDto>` kèm theo `nextCursor`. Tại tầng Presentation, Controller sẽ bọc `PagedResult` thành `ListEnvelope` kèm theo URL hiện tại để trả về client.

---

### 2.3. Tầng Infrastructure (`infrastructure/`)

Hiện thực hóa các Ports của Domain bằng các công nghệ cụ thể.

#### `persistence/TenantJpaEntity.java`
* Entity JPA ánh xạ vào bảng `tenants` trong database PostgreSQL.
* Có 2 hàm chuyển đổi:
  * `fromDomain(Tenant)`: Ánh xạ từ Domain Model sang JPA Entity.
  * `toDomain()`: Gọi `Tenant.reconstitute(...)` để chuyển sang Domain Model.

#### `persistence/TenantRepositoryAdapter.java`
* Lớp cài đặt interface `TenantRepositoryPort`.
* Tiêm `SpringDataTenantRepository` để thực hiện câu lệnh SQL/JPA thực tế.

---

### 2.4. Tầng Presentation (`presentation/`)

Giao tiếp với client bên ngoài (REST API, Webhook).

#### `rest/TenantController.java`
* Nhận HTTP Request, validate cú pháp với `@Valid`.
* **Tuyệt đối không gọi trực tiếp repository hay domain methods**.
* Chỉ giao tiếp với Application Layer qua `CommandBus` và `QueryBus`:
  ```java
  @PostMapping
  public ResponseEntity<TenantResponseDto> create(@Valid @RequestBody CreateTenantRequest req) {
      TenantResponseDto dto = commandBus.dispatch(new CreateTenantCommand(req.code(), req.name()));
      return ResponseEntity.created(URI.create("/api/v1/tenants/" + dto.id())).body(dto);
  }
  ```
* Bất kỳ ngoại lệ nghiệp vụ nào (`EntityNotFoundException`, `ConflictException`, `BusinessRuleViolationException`) đều được `GlobalExceptionHandler` bắt và chuyển thành RFC 9457 Problem Details tự động.
