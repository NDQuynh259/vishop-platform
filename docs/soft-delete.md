# Soft delete

## Phạm vi

Soft delete áp dụng cho các model có `deletedAt` trong schema:

- `MerchantSku`
- `PlatformSku`
- `Warehouse`
- `WarehouseBin`
- `Tenant`
- `TikTokShop`

`deletedAt = NULL` là record active; một timestamp nghĩa là record đã bị xóa mềm.

## Client mặc định

`PrismaService.db` và `PrismaService.dbRead` là scoped client. Với các thao tác đọc, aggregate và
update trên các model trên, client tự thêm:

```ts
where: {
  deletedAt: null;
}
```

Không cần lặp lại điều kiện trong repository:

```ts
const sku = await this.prisma.db.merchantSku.findFirst({
  where: { id: skuId, tenantId },
});
```

Client mặc định chặn `delete()` và `deleteMany()` trên soft-deletable models. Không biến hard delete
thành soft delete một cách ngầm; lifecycle phải được đặt tên rõ ở repository/use case.

## Thực hiện xóa mềm

Repository cung cấp method như `softDelete` và cập nhật `deletedAt`:

```ts
await client.merchantSku.updateMany({
  where: { id: skuId, tenantId },
  data: { deletedAt: new Date() },
});
```

Nên kiểm tra record còn active và dùng transaction nếu cần xóa dữ liệu phụ thuộc, ví dụ GTIN của SKU.

## Đọc cả record đã xóa

Dùng unscoped client:

```ts
const db = this.prisma.getDb({ withDeleted: true });

const allRows = await db.merchantSku.findMany({
  where: { tenantId },
});
```

Chỉ record đã xóa:

```ts
const deletedRows = await db.merchantSku.findMany({
  where: { tenantId, deletedAt: { not: null } },
});
```

Chỉ record active với unscoped client:

```ts
where: {
  deletedAt: null;
}
```

Prisma không có cú pháp `NotNull`; dùng `{ not: null }`. Read replica có API tương ứng:

```ts
const dbRead = this.prisma.getDbRead({ withDeleted: true });
```

## Join/relation khác scope

Extension chỉ scope model đang được query trực tiếp. Nó không rewrite sâu `include`, `select` hoặc
relation filter. Đây là chủ ý để core đơn giản và tránh sinh Prisma query không hợp lệ.

Khi bảng A cần gồm record đã xóa nhưng bảng B chỉ lấy active, dùng unscoped client và viết filter
cho B:

```ts
const db = this.prisma.getDb({ withDeleted: true });

const mappings = await db.skuMapping.findMany({
  where: {
    platformSku: {
      is: { deletedAt: null },
    },
  },
  include: {
    merchantSku: true,
    platformSku: true,
  },
});
```

Cùng quy tắc này áp dụng cho `Tenant` và `TikTokShop` trong relation filter. Ví dụ tenant guard
phải kiểm tra tenant chưa bị xóa:

```ts
where: {
  userId,
  tenant: {
    status: 'ACTIVE',
    deletedAt: null,
  },
}
```

Nếu dùng raw SQL, extension không can thiệp; phải viết điều kiện cho từng bảng:

```ts
const rows = await this.prisma.db.$queryRaw`
  SELECT w.id, b.id AS "binId"
  FROM warehouses w
  LEFT JOIN warehouse_bins b
    ON b."warehouseId" = w.id
   AND b."deletedAt" IS NULL
  WHERE w."tenantId" = ${tenantId}::uuid
    AND w."deletedAt" IS NULL
`;
```

## Partial unique index

Các model danh mục dùng unique index chỉ áp dụng cho record active:

```prisma
@@unique([tenantId, skuCode], where: { deletedAt: null })
```

Vì vậy mã có thể được tái sử dụng sau khi record cũ bị xóa mềm, theo chính sách nghiệp vụ.

## Quy tắc

1. Dùng `prisma.db`/`dbRead` cho luồng nghiệp vụ bình thường.
2. Không tự thêm `deletedAt: null` trong repository thông thường.
3. Dùng `getDb({ withDeleted: true })` chỉ cho admin, restore, audit hoặc maintenance.
4. Khi dùng unscoped client, tự ghi `deletedAt: null` hoặc `{ not: null }` khi cần.
5. Không gọi `delete()`/`deleteMany()` trên soft-deletable model qua client mặc định.
6. Soft delete phải có method/use case tên rõ như `softDelete` và xử lý transaction khi có dữ liệu phụ thuộc.
