# Cơ chế Xóa mềm (Soft Delete)

Tài liệu quy định cách thức triển khai và quy ước xóa mềm (Soft Delete) trong cơ sở dữ liệu và Spring Data JPA cho các entity trong **ViShop Platform**.

---

## 1. Phạm vi áp dụng

Xóa mềm áp dụng cho các Entity có trường `deletedAt` (`deleted_at TIMESTAMPTZ`):
* `Tenant`
* `Product` / `MerchantSku` / `PlatformSku`
* `Warehouse` / `WarehouseBin`
* `TikTokShop`

Quy ước:
* `deleted_at IS NULL`: Bản ghi đang hoạt động (active).
* `deleted_at IS NOT NULL`: Bản ghi đã bị xóa mềm (mang mốc thời gian xóa).

---

## 2. Triển khai trong Spring Data JPA & Hibernate 6

Trong Spring Boot 3.3 (Hibernate 6.4+), ta sử dụng annotation `@SQLRestriction` hoặc `@SoftDelete`:

```java
@Entity
@Table(name = "merchants_skus")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantSkuJpaEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public void markDeleted() {
        this.deletedAt = Instant.now();
    }
}
```

Với cấu hình này:
1. Mọi câu query thông thường (`findById`, `findAll`) tự động thêm điều kiện `WHERE deleted_at IS NULL`.
2. Không bị rò rỉ dữ liệu đã xóa mềm ra ngoài API nghiệp vụ.

---

## 3. Thực hiện xóa mềm trong Repository Adapter

Tầng Domain không gọi `delete()`, mà gọi phương thức nghiệp vụ mang ý nghĩa rõ ràng (ví dụ: `archive()` hoặc `delete()` trên Aggregate Root).

Tại Repository Adapter:
```java
@Override
public void delete(MerchantSku sku) {
    skuJpaRepository.findById(sku.getId()).ifPresent(entity -> {
        entity.markDeleted();
        skuJpaRepository.save(entity);
    });
}
```

---

## 4. Truy vấn các bản ghi bao gồm cả đã xóa mềm

Khi cần tra cứu lịch sử kiểm toán hoặc khôi phục dữ liệu:
* Khai báo phương thức native query hoặc query không áp dụng filter trong Spring Data JPA repository:
```java
@Query(value = "SELECT * FROM merchants_skus WHERE id = :id", nativeQuery = true)
Optional<MerchantSkuJpaEntity> findByIdIncludingDeleted(@Param("id") UUID id);
```
