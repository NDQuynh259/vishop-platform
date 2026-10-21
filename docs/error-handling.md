# Xử lý ngoại lệ chuẩn (Error Handling)

Tài liệu quy định cơ chế quản lý ngoại lệ và trả về lỗi chuẩn theo RFC 9457 Problem Details trong toàn bộ các Microservices của **ViShop Platform**.

---

## 1. Nguyên tắc cốt lõi: Ném ngoại lệ từ tầng Domain & Application

Tầng Domain và Application chỉ chịu trách nhiệm phát hiện vi phạm nghiệp vụ và ném đúng loại `DomainException`. Tầng Web (`common-web`) sẽ tự động ánh xạ thành HTTP Response theo chuẩn **RFC 9457 Problem Details**:

```java
// Trong CommandHandler hoặc Aggregate Root:
if (tenantRepository.existsByCode(command.code())) {
    throw new ConflictException("tenant_code_already_exists", 
        "A tenant with code '" + command.code() + "' already exists");
}
```

---

## 2. Bảng phân loại ngoại lệ và HTTP Status tương ứng

| Ngoại lệ | HTTP Status | Mã Problem Detail | Khi nào sử dụng? |
| :--- | :--- | :--- | :--- |
| `MethodArgumentNotValidException` | `422 Unprocessable Entity` | `validation_failed` | Client gửi body JSON thiếu trường bắt buộc hoặc sai định dạng regex/size (Jakarta Validation `@Valid`). |
| `ConstraintViolationException` / `HandlerMethodValidationException` | `422 Unprocessable Entity` | `validation_failed` | Tham số query param, path variable vi phạm validation annotation (Jakarta `@Validated`). |
| `BusinessRuleViolationException` | `422 Unprocessable Entity` | `business_rule_violation` | Dữ liệu hợp lệ cú pháp nhưng vi phạm quy tắc nghiệp vụ domain (ví dụ: tenant đã bị archived không thể update). |
| `EntityNotFoundException` | `404 Not Found` | `entity_not_found` | Aggregate Root hoặc Entity không tồn tại với ID được cung cấp. |
| `ConflictException` | `409 Conflict` | `conflict` | Trùng lặp unique key trong domain hoặc xung đột nghiệp vụ. |
| `DataIntegrityViolationException` | `409 Conflict` | `database_conflict` | Xung đột unique key hoặc ràng buộc khóa ngoại tầng DB mà không làm lộ câu lệnh SQL. |
| `OptimisticLockingFailureException` | `409 Conflict` | `optimistic_lock_conflict` | Xung đột phiên bản cập nhật đồng thời (Optimistic Lock concurrency collision). |
| `UnauthorizedException` / `AuthenticationException` | `401 Unauthorized` | `unauthorized` | Chưa xác thực danh tính hoặc phiên đăng nhập đã hết hạn. |
| `ForbiddenException` / `AccessDeniedException` | `403 Forbidden` | `forbidden` / `access_denied` | Người dùng đã đăng nhập nhưng không có quyền thao tác trên tài nguyên (tenant/shop). |
| `BadRequestException` / `IllegalArgumentException` | `400 Bad Request` | `bad_request` | Tham số truyền vào không hợp lệ hoặc tiền điều kiện logic bị vi phạm. |
| `HttpMessageNotReadableException` | `400 Bad Request` | `malformed_request` | Body request JSON bị lỗi cú pháp hoặc không thể deserialize. |
| `MethodArgumentTypeMismatchException` | `400 Bad Request` | `type_mismatch` | Tham số path variable / query param sai kiểu dữ liệu (ví dụ: chuỗi không thể parse thành UUID). |
| `MissingServletRequestParameterException` | `400 Bad Request` | `missing_parameter` | Thiếu query param bắt buộc. |
| `HttpRequestMethodNotSupportedException` | `405 Method Not Allowed` | `method_not_allowed` | HTTP method không được hỗ trợ trên endpoint (ví dụ: gửi POST vào GET endpoint). |
| `HttpMediaTypeNotSupportedException` | `415 Unsupported Media Type` | `unsupported_media_type` | Định dạng Content-Type không được hỗ trợ. |
| `NoResourceFoundException` | `404 Not Found` | `resource_not_found` | Đường dẫn tài nguyên tĩnh hoặc endpoint không tồn tại. |
| `Exception` (Mọi lỗi chưa phân loại) | `500 Internal Server Error` | `internal_server_error` | Lỗi sập database, lỗi mạng, lỗi runtime không lường trước. Log đầy đủ SLF4J stack trace với `requestId`, tuyệt đối không lộ chi tiết nội bộ ra client. |

---

## 3. Cấu trúc Response chuẩn RFC 9457 Problem Details

Tất cả các lỗi trả về từ API đều có định dạng thống nhất:

```json
{
  "type": "/errors/conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "A tenant with code 'viet-shop-hn' already exists",
  "code": "tenant_code_already_exists",
  "timestamp": "2026-10-05T05:14:00.123Z",
  "requestId": "01925b6a-7230-7e18-86d9-83bc9a022144"
}
```

Đối với lỗi validation nhiều trường (`422`):
```json
{
  "type": "/errors/validation-failed",
  "title": "Validation failed",
  "status": 422,
  "detail": "One or more fields did not pass validation.",
  "code": "validation_failed",
  "timestamp": "2026-10-05T05:14:00.123Z",
  "requestId": "01925b6a-7230-7e18-86d9-83bc9a022144",
  "errors": [
    {
      "field": "code",
      "message": "code must consist of lowercase letters, digits, and hyphens"
    },
    {
      "field": "name",
      "message": "name is required"
    }
  ]
}
```

---

## 4. Bảo mật thông tin lỗi nội bộ (Never leak internals)

* Mọi lỗi `500 Internal Server Error` tuyệt đối **không** trả về stack trace, tên bảng SQL, thông tin connection string ra cho client.
* Chi tiết lỗi kỹ thuật được ghi vào Structured Log thông qua SLF4J để kỹ sư tra cứu bằng `requestId`.
