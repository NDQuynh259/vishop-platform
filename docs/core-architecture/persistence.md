# Persistence: Spring Data JPA và migration SQL

Quy định này áp dụng cho toàn bộ code runtime trong `services/` và `libs/` của dự án microservice hiện tại. Nó thay thế các ví dụ JDBC/jOOQ trong tài liệu lịch sử.

## Truy cập dữ liệu

- CRUD dùng entity hạ tầng và Spring Data `JpaRepository`. Domain model và application port không phụ thuộc JPA.
- JPQL, paging, entity graph và projection được đặt trong repository/adapter, không đặt trong controller hay query handler.
- Khi cần SQL PostgreSQL, dùng `@Query(nativeQuery = true)` của Spring Data JPA hoặc `EntityManager.createNativeQuery`. Bind parameter; không nối giá trị từ request vào SQL.
- Không dùng `JdbcTemplate`, `JdbcClient`, JDBC `Connection`/`Statement`, jOOQ hoặc một datasource riêng để truy cập dữ liệu runtime ngoài JPA.
- JDBC driver và connection pool vẫn là hạ tầng bên dưới JPA. Flyway và kiểm thử schema có thể dùng connection để kiểm chứng DDL; đây không phải đường truy cập dữ liệu runtime của ứng dụng.
- Adapter trả domain model hoặc DTO, không để managed entity thoát ra API hay vượt ranh giới module.

Ví dụ layout của module:

```text
modules/<feature>/
├── domain/
│   ├── entity/                 # Domain model thuần Java
│   └── port/                   # Repository interface nghiệp vụ
└── infrastructure/persistence/
    ├── entities/               # @Entity, @Version, mapping cột
    ├── repositories/           # JpaRepository, @Query, @Modifying
    └── adapters/               # Implement domain/application port và map dữ liệu
```

`composition/members` sở hữu read port và JPA native adapter để JOIN các bảng trong database IAM. Quyền truy cập vẫn kiểm tra qua contract; SQL luôn giới hạn tenant. Không JOIN database của microservice khác.

## Khai báo entity và timestamp

Mỗi entity tự khai báo các cột cần thiết, không kế thừa base JPA. Năm entity IAM khai báo
trực tiếp `UUID id`, `createdAt`, `updatedAt`, `deletedAt` nullable và `Long version` với
`@Version`. ID do domain/application cấp theo luồng hiện có, không dùng `@GeneratedValue`.
Membership, role và invitation tự khai báo `tenantId` không đổi sau insert; repository
và authorization vẫn phải kiểm tra tenant scope. Domain model độc lập với mapping JPA.

Getter/setter dùng Lombok `@Getter`/`@Setter`; constructor JPA dùng
`@NoArgsConstructor(access = AccessLevel.PROTECTED)`. Entity log, outbox và consumed
message dùng getter và constructor phù hợp, không thêm setter cho dữ liệu bất biến.
Lombok được cấu hình bằng `compileOnly` và `annotationProcessor`, theo version do
Spring Boot BOM quản lý. Factory có logic khởi tạo và mapping domain giữ code tường minh.

Entity cần audit timestamp tự khai báo callback `@PrePersist` và `@PreUpdate` ngay trong
entity, không dùng interface/listener chung. `@PrePersist` chỉ bổ sung `createdAt` khi
còn null, giữ nguyên thời gian domain đã cấp; `updatedAt` ban đầu lấy từ `createdAt` nếu
chưa có. `@PreUpdate` cập nhật `updatedAt`. `createdAt` không được cập nhật sau insert.
Thời điểm nghiệp vụ như `acceptedAt` do domain/use case quản lý. Khi map aggregate đã
tồn tại, phải giữ nguyên ID, `createdAt` và version để optimistic locking phát hiện cập nhật cũ.

Bulk/native SQL không chạy các callback này; câu lệnh ghi vẫn phải tự cập nhật timestamp
và version. `deleted_at` chỉ lưu dấu xóa mềm; use case/repository quy định thao tác xóa và
lọc dữ liệu. Audit log giữ `occurredAt`/`createdAt`; outbox giữ metadata gửi/retry;
consumed message giữ khóa kép và `processedAt`, không cần callback `updatedAt`.

## Entity không tạo hay sửa schema

Entity chỉ mô tả ánh xạ persistence. Thêm `@Entity`, `@Column`, `@Version`, `@ElementCollection` hoặc quan hệ không tự tạo bảng/cột. Entity có thể khai báo metadata nhưng Hibernate không được thực thi schema DDL.

Mọi service có database phải dùng:

```yaml
spring:
  jpa:
    open-in-view: false
    generate-ddl: false
    hibernate:
      ddl-auto: validate
  sql:
    init:
      mode: never
  flyway:
    enabled: true
    baseline-on-migrate: false
    clean-disabled: true
```

Không bật `update`, `create`, `create-drop`, JPA schema generation hoặc `schema.sql`. Không thêm DDL vào `@PostConstruct`, repository hay code khởi động để né migration.

## Quy trình thay đổi schema

1. Tạo migration mới tại `services/<service>/src/main/resources/db/migration/V<version>__<description>.sql`.
2. Viết SQL cho bảng, cột, kiểu dữ liệu, index, constraint, trigger và backfill cần thiết.
3. Cập nhật entity/repository/query tương ứng. JSONB phải có JPA/Hibernate JSON type mapping; `columnDefinition` không thay thế mapping giá trị.
4. Kiểm thử trên PostgreSQL: migrate từ database rỗng, validate mapping và kiểm tra hành vi/constraint. Với thay đổi schema đã triển khai, kiểm thử nâng cấp từ version trước.
5. Triển khai migration bằng Flyway trước khi Hibernate validate. Khi Flyway được bật trong runtime, nó áp dụng các file SQL trước khi khởi tạo JPA. Việc này không phải entity tự sinh migration.

Không sửa/xóa migration đã chạy. Thêm migration version mới cho mỗi thay đổi. Database cũ không có Flyway history cần kế hoạch baseline/import riêng; không tự baseline khi khởi động.

## Transaction và tính nhất quán

- Use case ghi giữ `@Transactional`; query dùng `@Transactional(readOnly = true)` và isolation phù hợp.
- Native bulk update phải flush thay đổi đang chờ và clear persistence context khi cần, tránh đọc entity cũ sau SQL.
- Giữ điều kiện tenant, optimistic version, khóa tenant/advisory lock và kiểm tra thời hạn trong database.
- Invitation dùng SQL qua Spring Data JPA khi cần `clock_timestamp()` hoặc cập nhật có điều kiện để tránh race.
- IAM dùng chung `OutboxEventEntity`/repository của `common-persistence`; relay khóa bản ghi bằng native `FOR UPDATE SKIP LOCKED` trong transaction và chỉ đánh dấu gửi sau Kafka acknowledgement.

## Kiểm tra tự động

`src/test/java/com/vishop/architecture/PersistenceArchitectureTest.java` kiểm tra toàn bộ class runtime của các service/library không gọi JDBC trực tiếp; đồng thời kiểm tra cấu hình validate-only và migration SQL của service có database. `check` cũng chạy kiểm thử IAM và audit trên PostgreSQL.

```powershell
.\gradlew.bat check
```
