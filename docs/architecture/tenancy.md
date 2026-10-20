# Kiến trúc Đa người thuê (Multi-Tenancy)

Mọi dữ liệu nghiệp vụ trong nền tảng **ViShop Platform** đều thuộc về một **Tenant** (tổ chức / doanh nghiệp), không trực thuộc trực tiếp một User đơn lẻ. Một người dùng có thể là thành viên của nhiều Tenant khác nhau.

---

## 1. Thuật ngữ chuẩn (Naming Convention)

Khái niệm này có tên gọi duy nhất trong toàn bộ mã nguồn: **`tenant`**.
* Không sử dụng các từ thay thế như `workspace`, `organization` hay `company` trong tên bảng, cột, biến hoặc API path.
* Trên giao diện tiếng Việt, `tenant` được dịch là **"tổ chức"**; `shop` là **"gian hàng"**. Một tổ chức có thể sở hữu và liên kết nhiều gian hàng.

---

## 2. Mô hình phân cấp dữ liệu

```text
users ──< tenant_members >── tenants ──< roles ──< role_permissions
   │          (role tier)        │              │
   │                             │              └──< tenant_member_roles >──┐
   │                             └──< tenant_invitations                    │
   └─────────────────────────────────────────────────────────────< shops ──┘
```

---

## 3. Cơ chế cô lập Tenant trong Microservices

### 3.1. Luồng HTTP Request (REST API)
1. Client gửi request kèm header `X-Tenant-Id: <uuid>`.
2. [TenantContextFilter.java](../../platform-common/common-security/src/main/java/com/vishop/common/security/TenantContextFilter.java) (Spring OncePerRequestFilter) tự động:
   * Trích xuất giá trị UUID từ header `X-Tenant-Id`.
   * Gán vào [TenantContext.java](../../platform-common/common-security/src/main/java/com/vishop/common/security/TenantContext.java) (`ThreadLocal<UUID>`).
   * Thực thi request chain.
   * Gọi `TenantContext.clear()` trong khối `finally` để giải phóng thread an toàn.

### 3.2. Luồng Kafka Event (Asynchronous Messaging)
1. Khi phát hành event ra Kafka, [KafkaEventPublisher.java](../../platform-common/common-events/src/main/java/com/vishop/common/events/publisher/KafkaEventPublisher.java) tự động đính kèm header Kafka `X-Tenant-Id`.
2. Khi Consumer nhận record, [TenantRecordInterceptor.java](../../platform-common/common-events/src/main/java/com/vishop/common/events/consumer/TenantRecordInterceptor.java) trích xuất header này và thiết lập vào `TenantContext` trước khi `@KafkaListener` xử lý, sau đó giải phóng context khi hoàn tất.

### 3.3. Tầng lưu trữ (Database Persistence)
* Toàn bộ Entity JPA của dữ liệu phân tán theo tenant kế thừa từ [BaseTenantJpaEntity.java](../../platform-common/common-persistence/src/main/java/com/vishop/common/persistence/BaseTenantJpaEntity.java):
  ```java
  @MappedSuperclass
  public abstract class BaseTenantJpaEntity {
      @Column(name = "tenant_id", nullable = false, updatable = false)
      private UUID tenantId;
      ...
  }
  ```
* Bất kỳ câu truy vấn lọc theo tenant đều sử dụng `TenantContext.requiredTenantId()` hoặc tham số `tenantId` từ Query object để đảm bảo tuyệt đối không rò rỉ dữ liệu chéo giữa các tenant.
