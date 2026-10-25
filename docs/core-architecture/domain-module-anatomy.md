# Cấu trúc giải phẫu Microservice (Domain Module Anatomy)

Tài liệu này giải thích chi tiết mục đích và trách nhiệm của từng file bên trong một Microservice theo chuẩn **Domain-Driven Design (DDD) + Hexagonal Architecture + CQRS**, lấy ví dụ thực tế từ [`services/iam-service`](../../services/iam-service).

IAM chia theo phân hệ trước, chia tầng bên trong mỗi phân hệ. Use case dùng port của chính phân hệ và application API công khai của phân hệ khác; không dùng repository/entity của nhau. Toàn bộ persistence dùng Spring Data JPA/EntityManager, cùng datasource và transaction; native SQL cũng chạy qua JPA. Entity chỉ validate mapping, schema do Flyway SQL quản lý. Chi tiết xem [Persistence standard](./persistence.md) và [IAM README](../../services/iam-service/README.md).

---

## 1. Cấu trúc thư mục của một Microservice

```text
services/iam-service/src/main/java/com/vishop/iam/
├── IamApplication.java
├── composition/
│   ├── onboarding/
│   │   ├── application/command/              # CreateTenantCommand, CreateTenantCommandHandler
│   │   └── presentation/rest/                # TenantOnboardingController, dto/CreateTenantRequest
│   └── members/
│       ├── application/query/                # GetTenantMemberQuery, GetTenantMemberQueryHandler
│       ├── application/port/                 # TenantMemberReadPort
│       ├── infrastructure/persistence/       # JpaTenantMemberReadAdapter
│       └── presentation/rest/                # TenantMemberController
├── modules/
│   ├── tenant/
│   │   ├── domain/
│   │   │   ├── entity/Tenant.java
│   │   │   ├── valueobject/                    # TenantCode, TenantStatus
│   │   │   ├── port/TenantRepositoryPort.java
│   │   │   └── event/                          # TenantCreated/Renamed/StatusChangedEvent
│   │   ├── application/
│   │   │   ├── api/                           # TenantApi, TenantApplicationApi
│   │   │   ├── dto/TenantResponseDto.java
│   │   │   ├── mapper/TenantResponseMapper.java
│   │   │   ├── command/                        # RenameTenant, ChangeTenantStatus
│   │   │   └── query/                          # GetTenantById, ListCmsTenants
│   │   ├── infrastructure/persistence/         # JPA entity, repository và adapter
│   │   └── presentation/rest/                  # TenantController, TenantCmsController
│   ├── identity/
│   │   ├── domain/                             # User, SystemRole, PasswordPolicy, ports
│   │   ├── application/
│   │   │   ├── api/                           # IdentityApi, IdentityApplicationApi
│   │   │   ├── mapper/UserViewMapper.java
│   │   │   ├── command/                        # Gom theo bộ use case: login/, register/, logout/, switchtenant/, cms/
│   │   │   │   ├── login/                      # LoginCommand, LoginCommandHandler
│   │   │   │   ├── register/                   # RegisterUserCommand, RegisterUserCommandHandler
│   │   │   │   ├── logout/                     # LogoutCommand, LogoutCommandHandler
│   │   │   │   ├── switchtenant/               # SwitchTenantCommand, SwitchTenantCommandHandler
│   │   │   │   └── cms/                        # ChangeSystemRole/Status Commands & Handlers
│   │   │   ├── query/                          # Authenticate, Introspect, RefreshAccessToken, profile
│   │   │   ├── service/                        # TokenService, IdentityAuthorizationService
│   │   │   ├── port/                           # Password/token/UserRead ports
│   │   │   └── dto/
│   │   ├── infrastructure/                     # JPA entities/repositories, JWT/password/authentication adapters
│   │   └── presentation/rest/                  # AuthController, InternalAuthController, UserCmsController
│   └── access/
│       ├── domain/                             # Membership, Role, Invitation, RoleAssignmentPolicy, ports
│       ├── application/
│       │   ├── api/                           # AccessApi, AccessApplicationApi
│       │   ├── command/                        # Role, member và invitation use cases
│       │   ├── query/                          # ListMyTenants, ListTenantMembers/Roles
│       │   ├── service/AccessAuthorizationService.java
│       │   ├── port/AccessReadPort.java
│       │   └── dto/
│       ├── infrastructure/
│       │   ├── persistence/                    # JPA entities, repositories và adapters
│       │   ├── integration/IdentityUserActivityAdapter.java
│       │   └── config/IamDomainConfiguration.java
│       └── presentation/rest/                  # TenantAccessController, InvitationController
├── infra/
│   ├── messaging/                          # IamOutbox, IamOutboxRelay
│   ├── security/                           # IamPrincipal, opaque token utilities/adapter
│   └── database/IamDatabaseMigration.java
└── shared/
    ├── domain/event/IamDomainEvent.java
    └── application/
        ├── dto/IamActor.java
        └── port/                           # IamEventPublisherPort, OpaqueTokenPort
```

Các HTTP request DTO (`*Request`) và annotation validation đặt trong `presentation/rest/dto` của từng phân hệ. `application/dto` chứa kết quả use case và các contract dùng chung ở tầng application. Package domain dùng tên số ít: `entity`, `valueobject`, `port`, `service`, `event`.

Cách tổ chức tham khảo `api-vishop/libs` được áp dụng trong phạm vi microservice IAM: `infra` chứa kỹ thuật dùng chung của IAM, còn adapter riêng vẫn ở `modules/<tên>/infrastructure`. `shared` giữ kiểu dữ liệu và abstraction không phụ thuộc framework; các primitive domain/CQRS dùng lại `libs/common-*` của platform. `composition/onboarding` chỉ phối hợp các contract trong transaction, không giữ domain model hay quy tắc nghiệp vụ riêng.

`resources/application.yml` và `resources/db/migration` vẫn ở cấp service. Đây là các package trong cùng một deployment và database, không phải ba service độc lập. Cây trên liệt kê các nhóm và file tiêu biểu.

---

## 2. Chi tiết từng tầng và vai trò của từng file

Các đường dẫn ở phần này được hiểu tương đối bên trong phân hệ tương ứng. Ví dụ `entity/Tenant.java` thuộc `modules/tenant/domain`, còn `service/RoleAssignmentPolicy.java` thuộc `modules/access/domain`.

### 2.1. Tầng Domain (`domain/`)

Tầng trung tâm chứa toàn bộ quy tắc nghiệp vụ cốt lõi. **Không import Spring, JPA, Hibernate hay bất kỳ framework nào.** Được phân chia thành các thư mục con rành mạch:

#### `entity/Tenant.java` — Aggregate Root
* Kế thừa từ `AggregateRoot<UUID>`.
* Constructor đặt ở chế độ `private` để bắt buộc khởi tạo qua 2 static factory methods:
  * `create(TenantCode code, String name, UUID ownerId)`: Dùng khi tạo mới nghiệp vụ, nội bộ sinh ra `TenantCreatedEvent` lưu vào danh sách domain events.
  * `reconstitute(...)`: Dùng bởi Repository Adapter để tái dựng Aggregate từ database mà **không** kích hoạt domain events.
* Không có setter công khai. Trạng thái thay đổi qua `changeStatus(...)` và `rename(...)`; aggregate chặn tái kích hoạt tenant đã archive và ghi domain event cho thay đổi hợp lệ.

#### `valueobject/TenantCode.java` & `TenantStatus.java` — Value Objects & Enums
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

#### `port/TenantRepositoryPort.java` — Repository Port
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

#### `service/` — Domain Services
* Nơi chứa các logic nghiệp vụ phức tạp liên quan đến nhiều Aggregate hoặc cần tính toán quy tắc nghiệp vụ không thuộc về một Entity đơn lẻ.

---

### 2.2. Tầng Application (`application/`)

Tầng điều phối luồng thực thi (Use Cases) theo mô hình **CQRS**.

#### Chuẩn thiết kế Command và CommandHandler trong CQRS
- **Tổ chức theo bộ use case (Feature Subpackages)**: Trong package `application/command/`, các use case được gom thành các thư mục con riêng biệt (ví dụ: `login/`, `register/`, `logout/`, `switchtenant/`, `cms/`). Mỗi thư mục chứa đúng cặp `*Command` và `*CommandHandler` tương ứng.
- **`Command` khai báo là `public record`**:
  - `record`: Bất biến (immutable), không có setter, đảm bảo an toàn tuyệt đối khi luân chuyển dữ liệu qua `CommandBus`.
  - `public`: Bắt buộc để Controller ở tầng `presentation.rest` (nằm khác package) có thể khởi tạo (`new XxxCommand(...)`) và gửi vào `CommandBus.dispatch(...)`.
- **`CommandHandler` với `@Override` và `@Transactional`**:
  - Cài đặt interface `CommandHandler<C, R>` với hàm `@Override R handle(C command)`, cho phép `CommandBus` tự động ánh xạ và gọi đúng bean xử lý.
  - `@Transactional`: Đóng vai trò ranh giới giao dịch (Transaction Boundary), đảm bảo tính nguyên tử giữa việc ghi dữ liệu vào database (như tạo user, đổi quyền) và ghi sự kiện vào Transactional Outbox (`outbox.append`). Nếu có bất kỳ lỗi nào, DB và Outbox cùng rollback 100%.
  - `isolation = Isolation.REPEATABLE_READ`: Dùng cho các use case cấp phát token hoặc quyền hạn để đóng băng snapshot dữ liệu, ngăn ngừa tình trạng token bị ghép quyền cũ với security version mới khi có mutation đồng thời.

#### `composition/onboarding/application/command/CreateTenantCommand.java` & `CreateTenantCommandHandler.java`
* `Command` là một record mang dữ liệu đầu vào thực hiện hành động ghi.
* `CommandHandler` bọc trong transaction (`@Transactional`):
  1. Xác thực actor và kiểm tra tài khoản đang hoạt động. Unique constraint bảo vệ tenant code, kể cả khi có request đồng thời.
  2. Gọi contract `TenantApi.create(...)`. Implementation của tenant tạo aggregate, lưu qua repository và đưa domain event vào outbox; trigger tạo bốn role mặc định khi insert tenant.
  3. Gọi `AccessApi.initializeOwner(...)` để tạo membership OWNER. Cả hai API ghi đều yêu cầu transaction đang tồn tại.
  4. Trả về `TenantResponseDto`. Tenant, role mặc định, membership và outbox cùng rollback khi có lỗi; relay gửi Kafka bất đồng bộ sau commit.

Composition chỉ dùng contract công khai; không truy cập domain, repository hay implementation của các module. Module không phụ thuộc ngược vào composition.

#### Truy vấn tổng hợp trong `composition/members`

`TenantMemberController` nhận `GET /api/v1/tenants/{tenantId}/members/{userId}` và `/members/me`, gửi query qua `QueryBus`. Handler kiểm tra quyền qua `AccessApi`, rồi đọc qua `TenantMemberReadPort`. `JpaTenantMemberReadAdapter` dùng JPA `EntityManager` chạy native query có bind parameter, JOIN năm bảng `users`, `tenants`, `tenant_members`, `roles`, `role_permissions` trong datasource IAM và gom permissions thành một projection. Kết quả `Tuple` được map trực tiếp sang DTO, không tải entity graph. LEFT JOIN giữ được role chưa có permission; mọi liên kết dùng ID và tenant scope.

Kiểm tra quyền và đọc projection chạy trong cùng transaction read-only `REPEATABLE_READ`. Composition có thể sở hữu query handler, read port và SQL adapter của luồng tổng hợp; nó không sở hữu domain model hay truy cập repository/entity Java nội bộ module. Adapter phụ thuộc schema IAM nên phải được cập nhật và kiểm thử khi schema đổi. Nó không ghi dữ liệu hoặc JOIN database của microservice khác; tổng hợp liên service dùng API hoặc read model nhận sự kiện.

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
* Identity và access có entity/repository riêng: `UserJpaEntity`, `RoleJpaEntity`, `MembershipJpaEntity`, `InvitationJpaEntity`. `RoleJpaEntity` ánh xạ `role_permissions` bằng `@ElementCollection`; các truy vấn thời hạn/cập nhật nguyên tử dùng SQL qua Spring Data JPA. Domain model không có annotation persistence.

#### `persistence/adapters/TenantRepositoryAdapter.java`
* Lớp cài đặt interface `TenantRepositoryPort`.
* Tiêm `SpringDataTenantRepository` để thực hiện câu lệnh SQL/JPA thực tế.

---

### 2.4. Tầng Presentation (`presentation/`)

Giao tiếp với client bên ngoài (REST API, Webhook).

#### `composition/onboarding/presentation/rest/TenantOnboardingController.java`
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
- `TenantApi`: tạo tenant, kiểm tra trạng thái, trả tenant view và lấy khóa trong transaction của use case.
- `AccessApi`: cung cấp quyền tenant hiện tại, danh sách tenant ID và khởi tạo OWNER. Identity dùng API này để cấp JWT theo tenant; composition dùng để hoàn tất onboarding.
- `UserActivityPort` thuộc access domain; `IdentityUserActivityAdapter` gọi `IdentityApi` để policy kiểm tra user còn hoạt động mà không phụ thuộc identity entity/repository.

Interface công khai nằm trong `modules/<tên>/application/api`, DTO công khai nằm trong `application/dto` của chính module: `UserView`, `TenantResponseDto`, `TenantMemberResponseDto`. Các kiểu công khai này không phụ thuộc framework, infra hay nội bộ module. Implementation cùng nằm trong `application/api` nhưng chỉ được dùng nội bộ; việc map domain sang DTO nằm trong `application/mapper`. Phân hệ khác và composition chỉ dùng interface công khai và ba DTO được phép, không gọi implementation, repository, handler, service nội bộ hay adapter của nhau. Các SQL projection của access có thể join bảng user/tenant để đọc trong database chung; cập nhật thuộc phân hệ sở hữu. `shared` không phụ thuộc các module, composition, hạ tầng hay framework.

`IamActor` chỉ chứa user ID, tenant ID, system role và grant của request; không chứa User entity hoặc UserView. JWT/password/authentication adapter thuộc identity; opaque token và principal chung nằm trong `infra/security`. Outbox và relay ở `infra/messaging`, dùng entity/repository JPA của `common-persistence`; migration CLI ở `infra/database`.

JWT issuance uses `TokenService` and verification uses `AccessTokenPort`.
Authentication has no session persistence or Redis security projection.

## 4. Kiểm tra ranh giới kiến trúc

`IamArchitectureTest` bảo vệ chiều phụ thuộc: domain độc lập framework và outer layers, application dùng port, controller không gọi repository/domain entity; phân hệ và composition chỉ dùng interface/DTO công khai của module khác. Các kiểu công khai không phụ thuộc implementation hoặc framework; module không phụ thuộc composition; shared không phụ thuộc nghiệp vụ. `TenantTest` kiểm tra invariant và domain event. `IamApplicationTest` kiểm tra API/phân quyền, transaction bắt buộc của API thay đổi dữ liệu, cập nhật version, các request đồng thời và rollback JPA giữa tenant, role mặc định, membership và outbox. `PersistenceArchitectureTest` ở root chặn JDBC runtime và kiểm tra cấu hình schema/migration toàn dự án.
