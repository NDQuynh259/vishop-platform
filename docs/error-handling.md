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
| `MethodArgumentNotValidException` | `422 Unprocessable Entity` | `validation_failed` | Client gửi body JSON thiếu trường bắt buộc hoặc sai định dạng regex/size (Jakarta Validation). |
| `BusinessRuleViolationException` | `422 Unprocessable Entity` | `business_rule_violation` | Dữ liệu hợp lệ cú pháp nhưng vi phạm quy tắc nghiệp vụ domain (ví dụ: tenant đã bị archived không thể update). |
| `EntityNotFoundException` | `404 Not Found` | `entity_not_found` | Aggregate Root hoặc Entity không tồn tại với ID được cung cấp. |
| `ConflictException` | `409 Conflict` | `conflict` | Trùng lặp unique key trong DB hoặc xung đột phiên bản cập nhật đồng thời (Optimistic Lock). |
| `IllegalArgumentException` / `IllegalStateException` | `400 Bad Request` | `bad_request` | Lập trình viên gọi hàm với tham số bất hợp lệ hoặc trạng thái request không hợp lệ. |
| `Exception` (Mọi lỗi chưa phân loại) | `500 Internal Server Error` | `internal_server_error` | Lỗi sập database, lỗi mạng, lỗi runtime không lường trước. |

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
