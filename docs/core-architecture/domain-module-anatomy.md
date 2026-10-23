# Cấu trúc giải phẫu Microservice (Domain Module Anatomy)

Tài liệu này giải thích chi tiết mục đích và trách nhiệm của từng file bên trong một Microservice theo chuẩn **Domain-Driven Design (DDD) + Hexagonal Architecture + CQRS**, lấy ví dụ thực tế từ [`services/iam-service`](../../services/iam-service).

IAM chia theo phân hệ trước, chia tầng bên trong mỗi phân hệ. Use case dùng port của chính phân hệ và application API công khai của phân hệ khác; không dùng repository/entity của nhau. Tenant dùng JPA, identity/access dùng JDBC, cùng datasource và transaction. Chi tiết runtime xem [IAM README](../../services/iam-service/README.md).

---

## 1. Cấu trúc thư mục của một Microservice

```text
services/iam-service/src/main/java/com/vishop/iam/
├── IamApplication.java
├── tenant/
│   ├── domain/
│   │   ├── entities/Tenant.java
│   │   ├── valueobjects/                   # TenantCode, TenantStatus
│   │   ├── ports/TenantRepositoryPort.java
│   │   └── event/                          # TenantCreated/Renamed/StatusChangedEvent
│   ├── application/
│   │   ├── api/TenantApi.java
│   │   ├── command/                        # CreateTenant, RenameTenant, ChangeTenantStatus
│   │   ├── query/                          # GetTenantById, ListCmsTenants
│   │   └── dto/
│   ├── infrastructure/persistence/         # JPA entity, repository và adapter
│   └── presentation/rest/                  # TenantController, TenantCmsController
├── identity/
│   ├── domain/                             # User, Session, SystemRole, PasswordPolicy, ports
│   ├── application/
│   │   ├── api/IdentityApi.java
│   │   ├── command/                        # RegisterUser, Login, Logout, SwitchTenant, CMS user
│   │   ├── query/                          # Authenticate, Introspect, RefreshAccessToken, profile
│   │   ├── service/                        # SessionService, IdentityAuthorizationService
│   │   ├── port/                           # Password/token/UserRead ports
│   │   └── dto/
│   ├── infrastructure/                     # JDBC adapters, JWT/password/authentication adapters
│   └── presentation/rest/                  # AuthController, InternalAuthController, UserCmsController
├── access/
│   ├── domain/                             # Membership, Role, Invitation, RoleAssignmentPolicy, ports
│   ├── application/
│   │   ├── api/AccessApi.java
│   │   ├── command/                        # Role, member và invitation use cases
│   │   ├── query/                          # ListMyTenants, ListTenantMembers/Roles
│   │   ├── service/AccessAuthorizationService.java
│   │   ├── port/AccessReadPort.java
│   │   └── dto/
│   ├── infrastructure/
│   │   ├── persistence/                    # JDBC adapters và AccessJdbcMappings
│   │   ├── integration/IdentityUserActivityAdapter.java
│   │   └── config/IamDomainConfiguration.java
│   └── presentation/rest/                  # TenantAccessController, InvitationController
└── shared/
    ├── domain/event/IamDomainEvent.java
    ├── application/
    │   ├── dto/IamActor.java
    │   └── port/                           # IamEventPublisherPort, OpaqueTokenPort
    └── infrastructure/
        ├── messaging/                      # IamOutbox, IamOutboxRelay
        ├── security/                       # IamPrincipal, opaque token utilities/adapter
        ├── persistence/JdbcValues.java
        └── database/IamDatabaseMigration.java
```

`resources/application.yml` và `resources/db/migration` vẫn ở cấp service. Đây là các package trong cùng một deployment và database, không phải ba service độc lập. Cây trên liệt kê các nhóm và file tiêu biểu.

---

## 2. Chi tiết từng tầng và vai trò của từng file

Các đường dẫn ở phần này được hiểu tương đối bên trong phân hệ tương ứng. Ví dụ `entities/Tenant.java` thuộc `tenant/domain`, còn `services/RoleAssignmentPolicy.java` thuộc `access/domain`.

### 2.1. Tầng Domain (`domain/`)

Tầng trung tâm chứa toàn bộ quy tắc nghiệp vụ cốt lõi. **Không import Spring, JPA, Hibernate hay bất kỳ framework nào.** Được phân chia thành các thư mục con rành mạch:

#### `entities/Tenant.java` — Aggregate Root
* Kế thừa từ `AggregateRoot<UUID>`.
* Constructor đặt ở chế độ `private` để bắt buộc khởi tạo qua 2 static factory methods:
  * `create(TenantCode code, String name, UUID ownerId)`: Dùng khi tạo mới nghiệp vụ, nội bộ sinh ra `TenantCreatedEvent` lưu vào danh sách domain events.
  * `reconstitute(...)`: Dùng bởi Repository Adapter để tái dựng Aggregate từ database mà **không** kích hoạt domain events.
* Không có setter công khai. Trạng thái thay đổi qua `changeStatus(...)` và `rename(...)`; aggregate chặn tái kích hoạt tenant đã archive và ghi domain event cho thay đổi hợp lệ.

#### `valueobjects/TenantCode.java` & `TenantStatus.java` — Value Objects & Enums
* `TenantCode`: Cài đặt dưới dạng Java `record` không cần marker interface. Đảm bảo tính bất biến (immutable) và tự kiểm tra tính hợp lệ (invariants) trong constructor:
  ```java
  public record TenantCode(String value) {
      private static final Pattern PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$");
      public TenantCode {
          if (value == null || !PATTERN.matcher(value).matches()) {
              throw new BadRequestException("Invalid tenant code");
          }
      }
  }
  ```
* `TenantStatus`: Enum định nghĩa vòng đời của Tenant (`ACTIVE`, `SUSPENDED`, `ARCHIVED`).

#### `ports/TenantRepositoryPort.java` — Repository Port
* Là một interface Java thuần túy định nghĩa các phương thức lưu trữ Aggregate Root mà nghiệp vụ cần, ví dụ:
  ```java
  public interface TenantRepositoryPort {
      Tenant tenant(UUID id);
      void save(Tenant tenant);
      void lockTenant(UUID id);
      List<Tenant> tenants(int limit, int offset);
  }
  ```

#### `event/TenantCreatedEvent.java` & `TenantStatusChangedEvent.java` — Domain Events
* Cài đặt bằng `record` implements `DomainEvent`.
* Lưu lại dữ liệu sự kiện đã diễn ra trong quá khứ (`tenantId`, `code`, `name`, `status`, `occurredAt`), được đưa vào Outbox để phát tán sang Kafka.

#### `services/` — Domain Services
* Nơi chứa các logic nghiệp vụ phức tạp liên quan đến nhiều Aggregate hoặc cần tính toán quy tắc nghiệp vụ không thuộc về một Entity đơn lẻ.

---

### 2.2. Tầng Application (`application/`)

Tầng điều phối luồng thực thi (Use Cases) theo mô hình **CQRS**.

#### `command/CreateTenantCommand.java` & `CreateTenantCommandHandler.java`
* `Command` là một record mang dữ liệu đầu vào thực hiện hành động ghi.
* `CommandHandler` bọc trong transaction (`@Transactional`):
  1. Xác thực actor và kiểm tra tài khoản đang hoạt động. Unique constraint bảo vệ tenant code, kể cả khi có request đồng thời.
  2. Tạo mới Aggregate: `Tenant.create(...)`.
  3. Lưu Aggregate: `tenantRepository.save(tenant)`.
  4. Gọi `AccessApi.initializeOwner(...)` để tạo membership OWNER. API này yêu cầu transaction đang tồn tại; trigger đã tạo bốn role mặc định khi insert tenant.
  5. Đưa Domain Events vào Outbox: `tenant.pullDomainEvents().forEach(outbox::publish)` để relay gửi Kafka bất đồng bộ sau khi commit. Tenant, role mặc định, membership và outbox cùng rollback khi có lỗi.
  6. Trả về `TenantResponseDto`.

#### `query/ListCmsTenantsQuery.java` & `ListCmsTenantsQueryHandler.java`
* `Query` đại diện cho yêu cầu đọc dữ liệu (`@Transactional(readOnly = true)`). Handler kiểm tra quyền CMS, đọc qua repository port và map sang response DTO.
* API CMS hiện giữ contract `limit`/`offset`, sắp xếp ổn định theo `created_at DESC, id DESC`. Khi bổ sung filter/cursor, cần định nghĩa contract và metadata phân trang tương ứng; việc tách CQRS không tự thay đổi API hiện tại.

---

### 2.3. Tầng Infrastructure (`infrastructure/`)

Hiện thực hóa các Ports của Domain bằng các công nghệ cụ thể.

#### `persistence/entities/TenantJpaEntity.java`
* Entity JPA ánh xạ vào bảng `tenants` trong database PostgreSQL.
* Có 2 hàm chuyển đổi:
  * `fromDomain(Tenant)`: Ánh xạ từ Domain Model sang JPA Entity.
  * `toDomain()`: Gọi `Tenant.reconstitute(...)` để chuyển sang Domain Model.

#### `persistence/repositories/SpringDataTenantRepository.java`
* Interface Spring Data JPA kế thừa `JpaRepository` và `JpaSpecificationExecutor`.
* Repository cung cấp truy vấn khóa tenant và danh sách phân trang. `TenantJpaEntity` dùng `@Version`.
* Identity và access có `IdentityJdbcMappings`/`AccessJdbcMappings` riêng. Chỉ các hàm JDBC thuần kỹ thuật như đọc UUID nằm trong `shared/infrastructure/persistence/JdbcValues`.

#### `persistence/adapters/TenantRepositoryAdapter.java`
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
  public ResponseEntity<TenantResponseDto> create(
          @AuthenticationPrincipal IamPrincipal actor,
          @Valid @RequestBody CreateTenantRequest req) {
      TenantResponseDto dto = commandBus.dispatch(
          new CreateTenantCommand(actor.actor(), req.code(), req.name()));
      return ResponseEntity.created(URI.create("/api/v1/tenants/" + dto.id())).body(dto);
  }
  ```
* Ngoại lệ nghiệp vụ, validation, database và security được ánh xạ ở HTTP boundary bằng exception handler/filter tương ứng. Kiểm thử cả HTTP status và dữ liệu lỗi; không trả stack trace hoặc secret ra response.

## 3. Giao tiếp giữa các phân hệ

- `IdentityApi`: kiểm tra tài khoản, quyền CMS và cung cấp user view hiện tại.
- `TenantApi`: kiểm tra trạng thái tenant, trả tenant view và lấy khóa trong transaction của use case.
- `AccessApi`: cung cấp quyền tenant hiện tại, danh sách tenant ID và khởi tạo OWNER. Identity dùng API này để phân giải session; tenant dùng để hoàn tất onboarding.
- `UserActivityPort` thuộc access domain; `IdentityUserActivityAdapter` gọi `IdentityApi` để policy kiểm tra user còn hoạt động mà không phụ thuộc identity entity/repository.

Đây là application API nội bộ trong cùng process. Phân hệ khác chỉ dùng `application/api` và DTO công khai, không gọi repository, handler, service nội bộ hay adapter của nhau. Các SQL projection của access có thể join bảng user/tenant để đọc trong database chung; cập nhật thuộc phân hệ sở hữu. `shared` chỉ chứa thành phần dùng chung và không phụ thuộc ngược vào ba phân hệ.

`IamActor` chỉ chứa user ID, session ID, tenant ID, system role và grant của request; không chứa User entity hoặc UserView. JWT/password/authentication adapter thuộc identity; opaque token và principal chung nằm trong shared.

## 4. Kiểm tra ranh giới kiến trúc

`IamArchitectureTest` bảo vệ chiều phụ thuộc: domain độc lập framework và outer layers, application dùng port, controller không gọi repository/domain entity; phân hệ chỉ dùng hợp đồng công khai của nhau và shared không phụ thuộc nghiệp vụ. `TenantTest` kiểm tra invariant và domain event. `IamApplicationTest` kiểm tra API/phân quyền, transaction bắt buộc của API thay đổi dữ liệu, cập nhật version, các request đồng thời và rollback chung giữa JPA, JDBC, role mặc định và outbox.
