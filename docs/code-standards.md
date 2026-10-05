# Chuẩn mực lập trình (Code Standards)

Quy chuẩn và quy ước lập trình bắt buộc trên toàn bộ codebase của **ViShop Platform** nhằm duy trì chất lượng mã nguồn cao và đảm bảo tính nhất quán kiến trúc.

---

## 1. Ngôn ngữ & Nền tảng (Java 21 LTS)

1. **Sử dụng Java Record cho Data Carriers**:
   * Toàn bộ Value Objects, Domain Events, CQRS Commands/Queries, và REST DTOs phải sử dụng Java `record` để bảo đảm tính bất biến (immutability) và concise code.
   ```java
   // ĐÚNG
   public record CreateTenantCommand(String code, String name) implements Command<TenantResponseDto> {}
   ```
2. **Factory methods cho Aggregate Roots**:
   * Aggregate Root chỉ có `private`/`protected` constructor.
   * Khởi tạo bắt buộc qua 2 phương thức rõ ràng: `create(...)` cho nghiệp vụ mới sinh event và `reconstitute(...)` để nạp từ database.
3. **Tuyệt đối không dùng public setters trên Aggregate Root**:
   * Trạng thái Aggregate chỉ thay đổi thông qua các phương thức mang ý nghĩa nghiệp vụ (ví dụ: `activate()`, `suspend()`, `archive()`), không cho phép mutate trực tiếp field.

---

## 2. Ghi nhật ký (Logging)

* **Sử dụng SLF4J Logger chuẩn**:
  Tuyệt đối không dùng `System.out.println` hay `System.err.println`.
  ```java
  // ĐÚNG
  private static final Logger log = LoggerFactory.getLogger(CreateTenantCommandHandler.class);
  log.info("Creating tenant with code [{}]", command.code());
  
  // SAI
  System.out.println("Creating tenant: " + command.code());
  ```
* Không ghi log thông tin nhạy cảm (mật khẩu, access token, thông tin thẻ tín dụng).
* Đính kèm `requestId`, `tenantId`, `aggregateId` trong các log quan trọng để phục vụ distributed tracing.

---

## 3. Xử lý lỗi (Error Handling & HTTP Status)

### 3.1. Nguyên tắc cốt lõi: Ném Domain Exception từ tầng nghiệp vụ
Tầng Domain và Application không được tự sinh mã lỗi HTTP hay format JSON response. Hãy ném đúng loại ngoại lệ nghiệp vụ, tầng `GlobalExceptionHandler` trong `common-web` sẽ tự động chuyển thành **RFC 9457 Problem Details**:

| Tình huống | Loại ngoại lệ nên ném | HTTP Status sinh ra |
| :--- | :--- | :--- |
| Dữ liệu đầu vào sai định dạng, thiếu trường bắt buộc | Jakarta Bean Validation (`@Valid`, `@NotBlank`, ...) | `422 Unprocessable Entity` |
| Ràng buộc nghiệp vụ bị vi phạm (Business invariant broken) | `BusinessRuleViolationException` hoặc `DomainException` | `422 Unprocessable Entity` |
| Không tìm thấy bản ghi theo ID | `EntityNotFoundException("Tenant", id)` | `404 Not Found` |
| Xung đột dữ liệu (trùng unique key, stale version concurrency) | `ConflictException("tenant_code_exists", ...)` | `409 Conflict` |
| Lỗi dữ liệu không thể xử lý / logic sai | `IllegalArgumentException` / `IllegalStateException` | `400 Bad Request` |
| Lỗi server ngoài ý muốn (DB sập, lỗi kết nối) | Bất kỳ runtime exception chưa bắt | `500 Internal Server Error` |

### 3.2. Cấu trúc Response chuẩn RFC 9457
```json
{
  "type": "/errors/conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "A tenant with code 'viet-shop-hn' already exists",
  "code": "tenant_code_already_exists",
  "timestamp": "2026-10-05T05:14:00Z",
  "requestId": "01925b6a-7230-7e18-86d9-83bc9a022144"
}
```

---

## 4. Quy ước Đặt tên (Naming Conventions)

| Thành phần | Quy ước | Ví dụ |
| :--- | :--- | :--- |
| **Package** | Viết thường liền nhau | `com.vishop.tenant.domain` |
| **Class / Record / Interface** | PascalCase | `Tenant`, `TenantCode`, `TenantCreatedEvent` |
| **Method / Variable** | camelCase | `existsByCode()`, `tenantId` |
| **Constant / Static Final** | UPPER_SNAKE_CASE | `HEADER_TENANT_ID`, `DEFAULT_PAGE_SIZE` |
| **Database Table** | snake_case số nhiều | `tenants`, `outbox_events`, `consumed_messages` |
| **Database Column** | snake_case | `created_at`, `aggregate_id`, `tenant_id` |
| **Kafka Topic** | `vishop.{aggregate_kebab_case}.events` | `vishop.tenant.events` |
| **API Path** | kebab-case số nhiều | `/api/v1/tenants/{id}/status` |

---

## 5. Ranh giới kiến trúc (Hexagonal Boundaries)

1. **Domain không import Infrastructure**:
   Không bao giờ `@Entity`, `@Table`, `@Autowired`, `@Repository` xuất hiện trong package `domain`.
2. **Controller không gọi Repository Adapter trực tiếp**:
   Mọi tương tác từ Controller phải thông qua `commandBus.dispatch(...)` hoặc `queryBus.dispatch(...)`.
3. **Database Transaction**:
   Đặt `@Transactional` tại các lớp `CommandHandler` trong tầng Application, không đặt tràn lan trên Controller hay Domain methods.
