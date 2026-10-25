# ViShop — Tài liệu Ghép nối API Frontend (FR-03 & FR-05)

Tài liệu này tổng hợp toàn bộ các API Endpoints, kiểu dữ liệu (DTOs), phân quyền (`Permissions`), query parameters và bản đồ ghép nối trực tiếp vào các trang / component của Frontend (`vishop-next`).

---

## 🔐 1. Quy chuẩn chung & Authentication

- **Base URL**: `http://localhost:3000/api/v1` (hoặc biến môi trường `NEXT_PUBLIC_API_URL`).
- **Authentication**: gửi `Authorization: Bearer <accessToken>`. Login/register trả cặp JWT trong JSON body; refresh bằng `POST /api/v1/auth/refresh` với `{refreshToken}` khi access hết hạn.
- **Multi-tenancy**: tenant context nằm trong access JWT. Đổi tenant qua `POST /api/v1/auth/switch-tenant` với `{tenantId,refreshToken}` và Bearer access; dùng cặp JWT mới. Không dùng header tenant để override quyền.
- **Phân trang chuẩn (§0.2 / §0.3)**:
  - **Cursor Pagination** (`limit`, `cursor`):
    - Trả về: `{ data: T[], pageInfo: { hasNextPage: boolean, nextCursor: string | null } }`
  - **Offset Pagination** (`limit`, `page`):
    - Trả về: `{ data: T[], pageInfo: { page: number, totalPages: number, totalItems: number, hasNextPage: boolean } }`
- **Xử lý lỗi**: Chuẩn RFC 9457 Problem Details (`application/problem+json`) với trường `code` cố định.

---

## 🏷️ 2. Module FR-03: Danh mục Sản phẩm & Liên kết SKU Sàn

### 2.1 Quản lý SKU Kho (`MerchantSku`)

| Method   | Endpoint                                 | Quyền (`Permission`)                             | Mô tả & Chức năng                                                                                        |
| -------- | ---------------------------------------- | ------------------------------------------------ | -------------------------------------------------------------------------------------------------------- |
| `GET`    | `/merchant-skus`                         | `skus.view`                                      | Danh sách SKU kho (`search`, `categoryId`, `status`, `stockStatus`, `isCombo`, `page`/`cursor`, `sort`). |
| `GET`    | `/merchant-skus/overview`                | `skus.view`                                      | Thống kê tổng quan: tổng số SKU, đang kinh doanh, hết hàng, sắp hết hàng, tổng vốn.                      |
| `GET`    | `/merchant-skus/export`                  | `skus.view`                                      | Xuất file Excel danh sách SKU (`status`, `page`/`cursor`).                                               |
| `POST`   | `/merchant-skus`                         | `skus.create`                                    | Tạo mới SKU kho (`skuCode`, `name`, `categoryId`, `avgCost`, `gtins[]`, `lowStockThreshold`, `isCombo`). |
| `GET`    | `/merchant-skus/:id`                     | `skus.view`                                      | Chi tiết 1 SKU kho theo UUID v7.                                                                         |
| `PATCH`  | `/merchant-skus/:id`                     | `skus.edit`                                      | Cập nhật thông tin cơ bản SKU (tên, ảnh, ghi chú, đơn vị tính, kích thước, khối lượng).                  |
| `DELETE` | `/merchant-skus/:id`                     | `skus.delete`                                    | Xóa SKU kho (chỉ xóa được khi chưa có giao dịch tồn kho/đơn hàng).                                       |
| `PATCH`  | `/merchant-skus/bulk`                    | `skus.edit` (`+ skus.cost.edit` nếu sửa giá vốn) | Cập nhật hàng loạt SKU (`ids[]`, `status`, `avgCost`, `categoryId`, `lowStockThreshold`).                |
| `PUT`    | `/merchant-skus/:id/low-stock-threshold` | `skus.edit`                                      | Cài đặt ngưỡng cảnh báo tồn thấp.                                                                        |
| `PUT`    | `/merchant-skus/:id/category`            | `skus.edit`                                      | Gán danh mục cho SKU.                                                                                    |

#### Request mẫu: Tạo SKU (`POST /api/v1/merchant-skus`)

```json
{
  "skuCode": "SKU-IPHONE-15-PRO-256",
  "name": "iPhone 15 Pro 256GB Titan Tự Nhiên",
  "categoryId": "019dd1a5-0000-7000-8000-000000000001",
  "avgCost": 25000000,
  "gtins": ["195949018228", "8935001234567"],
  "baseUom": "PCS",
  "weightG": 187,
  "lowStockThreshold": 10,
  "isCombo": false
}
```

---

### 2.2 Barcode GTINs & Đơn vị tính (UOM)

| Method | Endpoint                             | Quyền       | Mô tả                                                            |
| ------ | ------------------------------------ | ----------- | ---------------------------------------------------------------- |
| `GET`  | `/merchant-skus/:id/gtins`           | `skus.view` | Lấy danh sách mã vạch (tối đa 10 GTINs/SKU, unique theo tenant). |
| `PUT`  | `/merchant-skus/:id/gtins`           | `skus.edit` | Cập nhật/thay thế danh sách GTINs cho SKU.                       |
| `PUT`  | `/merchant-skus/:id/uom-conversions` | `skus.edit` | Cấu hình quy đổi đơn vị tính (ví dụ: 1 Thùng = 24 Lon).          |

#### Request mẫu: Cập nhật GTINs (`PUT /api/v1/merchant-skus/:id/gtins`)

```json
{
  "gtins": ["195949018228", "8935001234567"]
}
```

---

### 2.3 Combo SKUs & Tồn khả dụng tính toán

| Method | Endpoint                                | Quyền       | Mô tả                                                                           |
| ------ | --------------------------------------- | ----------- | ------------------------------------------------------------------------------- |
| `PUT`  | `/merchant-skus/:id/combo-components`   | `skus.edit` | Khai báo định mức linh kiện con (`components: [{ componentSkuId, quantity }]`). |
| `GET`  | `/merchant-skus/:id/combo-availability` | `skus.view` | Tính tồn khả dụng Combo = `MIN(available / qty)` trên các linh kiện con.        |

#### Request mẫu: Cập nhật linh kiện Combo (`PUT /api/v1/merchant-skus/:id/combo-components`)

```json
{
  "components": [
    { "componentSkuId": "019dd1a5-0000-7000-8000-000000000010", "quantity": 1 },
    { "componentSkuId": "019dd1a5-0000-7000-8000-000000000011", "quantity": 2 }
  ]
}
```

---

### 2.4 Import SKU hàng loạt qua Excel

| Method | Endpoint                                   | Quyền         | Mô tả                                                                         |
| ------ | ------------------------------------------ | ------------- | ----------------------------------------------------------------------------- |
| `GET`  | `/merchant-skus/import/template`           | `skus.view`   | Tải file Excel mẫu (`.xlsx`).                                                 |
| `POST` | `/merchant-skus/import`                    | `skus.create` | Upload multipart form `file`. Trả về `202 ACCEPTED` kèm `id` job import.      |
| `GET`  | `/merchant-skus/imports/:id`               | `skus.view`   | Theo dõi tiến độ import (`QUEUED` ➡️ `PROCESSING` ➡️ `COMPLETED` / `FAILED`). |
| `GET`  | `/merchant-skus/imports/:id/errors/export` | `skus.view`   | Xuất file Excel báo cáo các dòng lỗi.                                         |

---

### 2.5 Danh mục Sản phẩm (`SkuCategory`)

| Method   | Endpoint                   | Quyền       | Mô tả                                             |
| -------- | -------------------------- | ----------- | ------------------------------------------------- |
| `GET`    | `/sku-categories`          | `skus.view` | Cây danh mục đa cấp phân tầng (Tree).             |
| `POST`   | `/sku-categories`          | `skus.edit` | Tạo danh mục mới (`code`, `name`, `parentId`).    |
| `PUT`    | `/sku-categories/:id`      | `skus.edit` | Sửa danh mục.                                     |
| `DELETE` | `/sku-categories/:id`      | `skus.edit` | Xóa danh mục (phải không có SKU và danh mục con). |
| `GET`    | `/sku-categories/:id/skus` | `skus.view` | Lấy toàn bộ SKU thuộc cây con của danh mục.       |

---

### 2.6 Biến thể Sàn (`PlatformSku` — Sản phẩm từ TikTok Shop)

> [!NOTE]
> **Thay đổi kiến trúc quan trọng**: Toàn bộ danh sách sản phẩm & biến thể sàn hiện được truy vấn trực tiếp ở cấp Tenant qua `/api/v1/platform-skus` (thay vì lồng theo từng shop `/shops/:shopId/...`), cho phép FE hiển thị hợp nhất hoặc lọc linh hoạt theo `shopId` qua Query Parameter.

| Method | Endpoint                                                   | Quyền             | Mô tả                                                                                                                                                                                                                 |
| ------ | ---------------------------------------------------------- | ----------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `GET`  | `/platform-skus`                                           | `products.view`   | Danh sách toàn bộ SKU/biến thể sàn trên toàn Tenant. Hỗ trợ lọc theo `shopId`, `search`, `status` (`ACTIVATE`, `INACTIVATE`, `DELETED`), `mappingStatus` (`all`, `mapped`, `unmapped`), phân trang `page` / `cursor`. |
| `GET`  | `/platform-skus/overview`                                  | `products.view`   | Thống kê tổng quan biến thể sàn (`totalSkus`, `mappedSkus`, `unmappedSkus`, `activeSkus`, `inactiveSkus`). Hỗ trợ lọc theo `shopId`.                                                                                  |
| `GET`  | `/platform-skus/:id`                                       | `products.view`   | Chi tiết 1 biến thể sàn (bao gồm thông tin product cha, giá, tồn trên sàn, và danh sách SKU kho đang liên kết).                                                                                                       |
| `POST` | `/shops/:shopId/platform-products/sync`                    | `products.resync` | Kích hoạt job đồng bộ lại toàn bộ sản phẩm của shop từ TikTok Shop về ViShop.                                                                                                                                         |
| `POST` | `/shops/:shopId/platform-products/:platformProductId/sync` | `products.resync` | Kích hoạt job đồng bộ lại 1 sản phẩm cụ thể từ TikTok Shop.                                                                                                                                                           |

---

### 2.7 Liên kết SKU Sàn (`SkuMapping` — TikTok Shop ↔ Kho ViShop)

| Method   | Endpoint                          | Quyền               | Mô tả                                                                                                                                                                               |
| -------- | --------------------------------- | ------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `GET`    | `/sku-mappings`                   | `skus.view`         | Danh sách biến thể sàn & trạng thái liên kết ở cấp Tenant (`UNMAPPED`, `PARTIAL`, `MAPPED`). Hỗ trợ lọc `shopId`, `search`, và `withTopSuggestion=true` để lấy ngay gợi ý tốt nhất. |
| `GET`    | `/sku-mappings/summary`           | `skus.view`         | Thống kê tỷ lệ map theo shop (`mappingRate`, `affectedOrderCount`, `unmappedCount`).                                                                                                |
| `GET`    | `/sku-mappings/suggestions`       | `skus.view`         | Gợi ý map tự động xếp hạng theo độ tin cậy (Exact Code, GTIN Barcode, Trigram Name similarity).                                                                                     |
| `GET`    | `/sku-mappings/candidates`        | `skus.view`         | Tìm kiếm nhanh SKU kho để kéo-thả / map thủ công (`search`, `limit`).                                                                                                               |
| `POST`   | `/sku-mappings`                   | `skus.mapping.edit` | Tạo liên kết N:1 (`platformSkuId`, `components: [{ merchantSkuId, quantity }]`).                                                                                                    |
| `DELETE` | `/sku-mappings`                   | `skus.mapping.edit` | Hủy liên kết (`platformSkuId`, `merchantSkuId`).                                                                                                                                    |
| `POST`   | `/sku-mappings/accept-suggestion` | `skus.mapping.edit` | Chấp nhận nhanh gợi ý liên kết có độ tin cậy cao.                                                                                                                                   |
| `POST`   | `/sku-mappings/auto-create`       | `skus.mapping.edit` | Tự động tạo SKU kho từ biến thể sàn chưa map và liên kết ngay.                                                                                                                      |

#### Request mẫu: Tạo liên kết SKU (`POST /api/v1/sku-mappings`)

```json
{
  "platformSkuId": "019dd1a5-0000-7000-8000-000000000020",
  "components": [{ "merchantSkuId": "019dd1a5-0000-7000-8000-000000000001", "quantity": 1 }]
}
```

---

## 🏭 3. Module FR-05: Kho Hàng & Quản lý Tồn Kho

### 3.1 Quản lý Kho Hàng (`Warehouse`)

| Method   | Endpoint          | Quyền                        | Mô tả                                                     |
| -------- | ----------------- | ---------------------------- | --------------------------------------------------------- |
| `GET`    | `/warehouses`     | `inventory.view`             | Danh sách kho hàng vật lý đang hoạt động.                 |
| `POST`   | `/warehouses`     | `inventory.warehouse.manage` | Tạo kho mới (`code`, `name`, `fullAddress`, `isDefault`). |
| `GET`    | `/warehouses/:id` | `inventory.view`             | Chi tiết kho hàng.                                        |
| `PUT`    | `/warehouses/:id` | `inventory.warehouse.manage` | Cập nhật kho hàng.                                        |
| `DELETE` | `/warehouses/:id` | `inventory.warehouse.manage` | Xóa mềm kho hàng.                                         |

---

### 3.2 Sổ cái Biến động Tồn kho (`Stock Movements Ledger`)

| Method | Endpoint                 | Quyền            | Mô tả                                                                                                                                                                                            |
| ------ | ------------------------ | ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `GET`  | `/inventories/movements` | `inventory.view` | Sổ cái bất biến (Append-only ledger). Lọc theo `merchantSkuId`, `warehouseId`, `eventType` (`STOCK_IN`, `STOCK_OUT`, `STOCKTAKE_ADJUSTMENT`, `ORDER_LOCKED`, `RTS_DEDUCTED`, `RETURN_RECEIVED`). |

---

### 3.3 Nhập kho (`Stock-In`)

| Method | Endpoint                                          | Quyền                | Mô tả                                                                                                                                                                  |
| ------ | ------------------------------------------------- | -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `POST` | `/inventories/stock-in`                           | `inventory.stock_in` | Nhập kho thủ công: tự động tính lại giá vốn bình quân gia quyền (`avgCost`), tăng `onHand` & `available`. Lý do: `PURCHASE`, `SUPPLIER_RETURN`, `ADJUSTMENT`, `OTHER`. |
| `GET`  | `/inventories/stock-in/import/template`           | `inventory.stock_in` | Tải file Excel mẫu nhập kho.                                                                                                                                           |
| `POST` | `/inventories/stock-in/import`                    | `inventory.stock_in` | Upload file `.xlsx` nhập kho hàng loạt theo `warehouseId` (nhận qua multipart field hoặc query). Trả về `202 ACCEPTED`.                                                |
| `GET`  | `/inventories/stock-in/imports/:id/errors`        | `inventory.view`     | Danh sách lỗi từng dòng của đợt nhập kho.                                                                                                                              |
| `GET`  | `/inventories/stock-in/imports/:id/errors/export` | `inventory.view`     | Xuất file Excel báo cáo lỗi nhập kho.                                                                                                                                  |

#### Request mẫu: Nhập kho thủ công (`POST /api/v1/inventories/stock-in`)

```json
{
  "merchantSkuId": "019dd1a5-0000-7000-8000-000000000001",
  "warehouseId": "019dd1a5-0000-7000-8000-000000000004",
  "quantity": 50,
  "unitCost": 60000,
  "reason": "PURCHASE",
  "referenceId": "PO-2026-08-001",
  "note": "Nhập hàng từ nhà cung cấp"
}
```

---

### 3.4 Xuất kho (`Stock-Out`)

| Method | Endpoint                 | Quyền                 | Mô tả                                                                                                                                                      |
| ------ | ------------------------ | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `POST` | `/inventories/stock-out` | `inventory.stock_out` | Xuất kho thủ công (Hư hỏng, thất thoát, hàng mẫu quà tặng, cân đối). Giảm `onHand` & `available`. Lý do: `DAMAGED`, `LOST`, `GIFT`, `ADJUSTMENT`, `OTHER`. |

#### Request mẫu: Xuất kho thủ công (`POST /api/v1/inventories/stock-out`)

```json
{
  "merchantSkuId": "019dd1a5-0000-7000-8000-000000000001",
  "warehouseId": "019dd1a5-0000-7000-8000-000000000004",
  "quantity": 2,
  "reason": "DAMAGED",
  "referenceId": "OUT-DAMAGED-001",
  "note": "Hàng vỡ trong lúc đóng gói"
}
```

---

### 3.5 Kiểm kho (`Stocktake`)

| Method | Endpoint                              | Quyền                         | Mô tả                                                                                |
| ------ | ------------------------------------- | ----------------------------- | ------------------------------------------------------------------------------------ |
| `POST` | `/inventories/stocktakes`             | `inventory.stocktake.create`  | Tạo phiếu kiểm kho Draft cho 1 kho hàng (`warehouseId`, `note`).                     |
| `GET`  | `/inventories/stocktakes/:id`         | `inventory.view`              | Chi tiết phiếu kiểm và danh sách SKU đang đếm.                                       |
| `PUT`  | `/inventories/stocktakes/:id/counts`  | `inventory.stocktake.count`   | Ghi nhận số lượng đếm thực tế (`lines: [{ merchantSkuId, countedQuantity, note }]`). |
| `POST` | `/inventories/stocktakes/:id/confirm` | `inventory.stocktake.confirm` | Xác nhận cân bằng kho: tự động tạo bút toán điều chỉnh chênh lệch vào sổ cái.        |

#### Request mẫu: Cập nhật số lượng đếm (`PUT /api/v1/inventories/stocktakes/:id/counts`)

```json
{
  "lines": [
    {
      "merchantSkuId": "019dd1a5-0000-7000-8000-000000000001",
      "countedQuantity": 48,
      "note": "Thiếu 2 cái so với sổ sách"
    }
  ]
}
```

---

### 3.6 Tuyến Đẩy Tồn & Đồng bộ Sàn (`Push Routes & Push Logs` — FR-05-10, FR-05-14)

| Method   | Endpoint                              | Quyền                   | Mô tả                                                                                                                                                                   |
| -------- | ------------------------------------- | ----------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `GET`    | `/inventories/push-routes`            | `inventory.view`        | Danh sách tuyến phân bổ tồn từ kho ➡️ gian hàng TikTok. Hỗ trợ cursor (`limit`, `cursor`) hoặc offset (`limit`, `page`).                                                |
| `POST`   | `/inventories/push-routes`            | `inventory.push.config` | Tạo tuyến đẩy tồn mới (`warehouseId`, `shopId`, `ratio`, `safetyStock`, `priority`, `platformWarehouseId`).                                                             |
| `PATCH`  | `/inventories/push-routes/:id`        | `inventory.push.config` | Sửa cấu hình 1 tuyến (tỷ lệ %, tồn an toàn, bật/tắt, ưu tiên, kho sàn map).                                                                                             |
| `PUT`    | `/inventories/push-routes/bulk`       | `inventory.push.config` | Cập nhật hàng loạt tuyến đẩy tồn (`routes: [{ id, ratio, safetyStock, enabled, priority, platformWarehouseId }]`).                                                      |
| `DELETE` | `/inventories/push-routes/:id`        | `inventory.push.config` | Xóa tuyến đẩy tồn.                                                                                                                                                      |
| `POST`   | `/inventories/pushes`                 | `inventory.push.run`    | Kích hoạt đẩy tồn thủ công ngay lập tức lên TikTok Shop (`scope: 'shop'` hoặc `'sku'`). Trả về `202 ACCEPTED` kèm `batchId` và tiến độ khởi tạo.                        |
| `GET`    | `/inventories/pushes/:batchId`        | `inventory.view`        | Lấy chi tiết tiến độ đợt đẩy tồn kho theo `batchId` (`requestedCount`, `eligibleCount`, `excludedCount`, `queuedCount`, `succeededCount`, `failedCount`, `status`).     |
| `GET`    | `/inventories/pushes/:batchId/events` | `inventory.view`        | **SSE Stream**: Theo dõi realtime tiến độ đẩy tồn kho qua Server-Sent Events (`text/event-stream`). Stream tự đóng khi hoàn thành (`COMPLETED`, `CANCELLED`, `FAILED`). |
| `POST`   | `/inventories/pushes/:batchId/cancel` | `inventory.push.run`    | Yêu cầu hủy một đợt đẩy tồn đang chạy hoặc trong hàng đợi.                                                                                                              |
| `GET`    | `/inventories/pushes/logs`            | `inventory.view`        | Lịch sử nhật ký đẩy tồn (xem số lượng gửi, kết quả, chi tiết lỗi từ sàn). Hỗ trợ lọc `shopId`, `externalSkuId`, `status`, `source`, phân trang `page` / `cursor`.       |

#### Request mẫu: Tạo tuyến đẩy tồn (`POST /api/v1/inventories/push-routes`)

```json
{
  "warehouseId": "019dd1a5-0000-7000-8000-000000000004",
  "shopId": "019dd1a5-0000-7000-8000-000000000005",
  "ratio": 100,
  "safetyStock": 2,
  "enabled": true,
  "priority": false,
  "platformWarehouseId": "019dd1a5-0000-7000-8000-000000000006"
}
```

#### Request mẫu: Kích hoạt đẩy tồn thủ công (`POST /api/v1/inventories/pushes`)

```json
{
  "shopId": "019dd1a5-0000-7000-8000-000000000005",
  "scope": "shop"
}
```

_Response trả về (`202 ACCEPTED`):_

```json
{
  "batchId": "019dd1a5-0000-7000-8000-000000000099",
  "jobId": "tiktok-push-batch-019dd1a5...",
  "shopId": "019dd1a5-0000-7000-8000-000000000005",
  "status": "QUEUED",
  "requestedCount": 150,
  "eligibleCount": 145,
  "excludedCount": 5,
  "queuedCount": 145
}
```

---

### 3.7 Đồng bộ Kho Sàn TikTok (`Platform Warehouses` — FR-05-14)

| Method | Endpoint                                         | Quyền                   | Mô tả                                                                                                                                                                                |
| ------ | ------------------------------------------------ | ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `POST` | `/tiktok/shops/:shopId/platform-warehouses/sync` | `inventory.push.config` | Kích hoạt đồng bộ danh sách kho vật lý / kho ảo từ Seller Center của TikTok Shop về ViShop.                                                                                          |
| `GET`  | `/tiktok/shops/:shopId/platform-warehouses`      | `shops.view`            | Lấy danh sách kho sàn TikTok đã đồng bộ của gian hàng (`externalWarehouseId`, `name`, `type`, `effectStatus`, `isDefault`). Dùng để gán vào `platformWarehouseId` của Tuyến đẩy tồn. |

---

### 3.8 Công tắc Đẩy Tồn Gian Hàng & Tồn An Toàn Theo SKU (FR-05-11, FR-05-13)

| Method   | Endpoint                                                   | Quyền                   | Mô tả                                                                                                                         |
| -------- | ---------------------------------------------------------- | ----------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `PUT`    | `/authorizations/:shopId/inventory-push`                   | `inventory.push.config` | Bật/tắt công tắc đẩy tồn kho cấp gian hàng (`{ "enabled": boolean }`). Khi tắt, hệ thống chặn cả đẩy tồn tự động và thủ công. |
| `GET`    | `/shops/:shopId/merchant-skus/safety-stocks`               | `shops.view`            | Danh sách các SKU có cấu hình tồn an toàn riêng biệt ghi đè cho gian hàng cụ thể (`limit`, `cursor`).                         |
| `PUT`    | `/shops/:shopId/merchant-skus/:merchantSkuId/safety-stock` | `shops.view`            | Cài đặt tồn an toàn ghi đè cho 1 SKU tại 1 gian hàng (`{ "safetyStock": number }`).                                           |
| `DELETE` | `/shops/:shopId/merchant-skus/:merchantSkuId/safety-stock` | `shops.view`            | Xóa cấu hình tồn an toàn riêng của SKU để quay lại sử dụng tồn an toàn mặc định của gian hàng / tuyến.                        |

---

## 🗺️ 4. Bản đồ Ghép nối Màn hình Frontend (`vishop-next`)

| Tuyến đường dẫn FE         | Thành phần UI / Component                               | APIs Backend cần kết nối                                                                                                                                                                                                                                                                                     |
| -------------------------- | ------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `/inventory`               | `InventoryTable`, `StatCard`                            | `GET /merchant-skus` (lấy danh sách tồn kho theo SKU), `GET /merchant-skus/overview` (thống kê thẻ)                                                                                                                                                                                                          |
| `/inventory/movements`     | `MovementsTable`                                        | `GET /inventories/movements` (Sổ cái lịch sử biến động kho)                                                                                                                                                                                                                                                  |
| `/inventory/stock-in`      | `StockInForm`, `StockInDialog`                          | `POST /inventories/stock-in` (nhập tay), `POST /inventories/stock-in/import` (import Excel)                                                                                                                                                                                                                  |
| `/inventory/stock-out`     | `StockOutForm`                                          | `POST /inventories/stock-out` (xuất kho hao hụt, quà tặng, xuất khác)                                                                                                                                                                                                                                        |
| `/inventory/stocktake`     | `StocktakeList`, `StocktakeDetail`                      | `POST /inventories/stocktakes` (tạo phiếu), `PUT .../counts` (nhập đếm), `POST .../confirm` (cân bằng kho)                                                                                                                                                                                                   |
| `/inventory/warehouses`    | `WarehouseTable`, `WarehouseDialog`                     | `GET /warehouses`, `POST /warehouses`, `PUT /warehouses/:id`, `DELETE /warehouses/:id`                                                                                                                                                                                                                       |
| `/inventory/push-settings` | `PushRouteTable`, `RouteDialog`, `ShopSyncSwitch`       | `GET /inventories/push-routes`, `POST /inventories/push-routes`, `PATCH .../push-routes/:id`, `PUT .../push-routes/bulk`, `DELETE .../push-routes/:id`, `POST /tiktok/shops/:shopId/platform-warehouses/sync`, `GET /tiktok/shops/:shopId/platform-warehouses`, `PUT /authorizations/:shopId/inventory-push` |
| `/inventory/push-logs`     | `PushLogsTable`, `PushBatchProgressModal`               | `GET /inventories/pushes/logs`, `POST /inventories/pushes` (nút kích hoạt đẩy tồn), `GET /inventories/pushes/:batchId` (polling), `GET /inventories/pushes/:batchId/events` (SSE realtime progress), `POST /inventories/pushes/:batchId/cancel`                                                              |
| `/skus`                    | `SkusTable`, `CreateSkuDialog`                          | `GET /merchant-skus`, `POST /merchant-skus`, `PATCH /merchant-skus/bulk`, `GET /sku-categories`                                                                                                                                                                                                              |
| `/skus/[id]`               | `SkuDetail`, `ComboComponentsCard`, `SafetyStockDialog` | `GET /merchant-skus/:id`, `PATCH /merchant-skus/:id`, `PUT .../combo-components`, `PUT .../gtins`, `PUT /shops/:shopId/merchant-skus/:id/safety-stock`                                                                                                                                                       |
| `/sku-mapping`             | `QueueTable`, `MappedTable`, `MatchPanel`               | `GET /sku-mappings`, `GET /sku-mappings/summary`, `GET /sku-mappings/suggestions`, `POST /sku-mappings`, `POST .../auto-create`                                                                                                                                                                              |

---

## 🛠️ 5. Hướng dẫn Tích hợp trong Codebase FE (`packages/api-client`)

Frontend `vishop-next` đã có sẵn thư viện `@vishop/api-client` (sinh tự động qua Orval). Các React Hooks / API functions tương ứng:

```tsx
// Ví dụ sử dụng React Query Hooks & SSE:
import {
  useMerchantSkusList,
  useMerchantSkusCreate,
  useInventoriesListMovements,
  useInventoriesStockIn,
  useInventoriesListPushRoutes,
  useInventoriesManualPush,
  useInventoriesGetPushBatch,
  useInventoriesCancelPushBatch,
  useSkuMappingsList,
  useWarehousesList,
} from '@vishop/api-client';

// 1. Lấy danh sách tồn kho SKU
const { data, isLoading } = useMerchantSkusList({
  limit: 20,
  page: 1,
  stockStatus: 'low_stock',
});

// 2. Nhập kho
const { mutate: stockIn } = useInventoriesStockIn();
stockIn({
  data: {
    merchantSkuId: '019dd1a5-0000-7000-8000-000000000001',
    warehouseId: '019dd1a5-0000-7000-8000-000000000004',
    quantity: 10,
    unitCost: 50000,
    reason: 'PURCHASE',
  },
});

// 3. Kích hoạt đẩy tồn kho & theo dõi tiến độ SSE realtime:
const { mutate: triggerPush } = useInventoriesManualPush();

const handlePush = (shopId: string) => {
  triggerPush(
    { data: { shopId, scope: 'shop' } },
    {
      onSuccess: (res) => {
        const batchId = res.batchId;
        // Khởi tạo SSE EventSource lắng nghe tiến độ
        const eventSource = new EventSource(`/api/v1/inventories/pushes/${batchId}/events`, {
          withCredentials: true,
        });
        eventSource.onmessage = (event) => {
          const payload = JSON.parse(event.data);
          console.log(
            'Push Progress:',
            payload.data.succeededCount,
            '/',
            payload.data.eligibleCount,
          );
          if (['COMPLETED', 'CANCELLED', 'FAILED'].includes(payload.data.status)) {
            eventSource.close();
          }
        };
      },
    },
  );
};
```
