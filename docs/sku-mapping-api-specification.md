# Đặc Tả Kỹ Thuật API Ghép Nối SKU (SKU Mapping Specification)

- **Mã tính năng**: M04 / FR-04 (Ghép nối SKU sàn ↔ SKU kho)
- **Phiên bản API**: `v1`
- **Base Path**: `/api/v1/sku-mappings`
- **Phân quyền yêu cầu**:
  - Xem danh sách & thống kê: `skus:view`
  - Thao tác ghép/gỡ/duyệt: `skus.mapping:edit`
  - Tự động tạo SKU kho: `skus:create` + `skus.mapping:edit`

---

## 1. Tổng quan & Mô hình Dữ liệu

### 1.1. Khái niệm

- **SKU sàn (`PlatformSku`)**: Biến thể hàng đang bán trên TikTok Shop (do TikTok cấp mã, ViShop chỉ đồng bộ về một chiều).
- **SKU kho (`MerchantSku`)**: Mã hàng vật lý thực tế trong kho ViShop (quản lý tồn, giá vốn, lô date, combo).
- **Liên kết (`SkuMapping`)**: Bảng quan hệ N–N giữa `PlatformSku` và `MerchantSku` kèm số lượng tiêu hao (`quantity`).

### 1.2. Sơ đồ thực thể (ERD)

```
PlatformSku (1) ───< SkuMapping (N) >─── (1) MerchantSku
```

- Một SKU sàn có thể map tới **1 SKU kho** (đơn lẻ 1–1) hoặc **nhiều SKU kho** (listing combo/bundle 1–N).
- Một SKU kho tiêu hao với hệ số `quantity >= 1` trên mỗi đơn vị SKU sàn bán ra.

---

## 2. Danh sách Endpoints

### 2.1. Thống kê tổng quan mapping (`GET /api/v1/sku-mappings/summary`)

Phục vụ 4 thẻ StatCard đầu trang.

- **Query Params**:
  - `shopId` _(optional, UUID)_: Lọc theo 1 gian hàng cụ thể.

- **Response `200 OK`**:

```json
{
  "total": 120,
  "mapped": 96,
  "unmapped": 20,
  "partial": 4,
  "coveragePercent": 80,
  "affectedOrderCount": 15,
  "byShop": [
    {
      "shopId": "01912ecb-7241-7a01-b541-112233445566",
      "shopName": "Shop Thời Trang Nam",
      "total": 60,
      "mapped": 55,
      "unmapped": 5,
      "coveragePercent": 92
    }
  ]
}
```

---

### 2.2. Danh sách SKU sàn & Trạng thái ghép nối (`GET /api/v1/sku-mappings`)

Lấy danh sách cho Hàng chờ chưa ghép hoặc Bảng quản lý đã ghép (hỗ trợ phân trang Cursor).

- **Query Params**:
  - `limit` _(optional, number, default: 20, max: 100)_
  - `cursor` _(optional, string)_: Con trỏ trang tiếp theo.
  - `shopId` _(optional, UUID)_: Lọc theo gian hàng.
  - `mappingStatus` _(optional, enum)_: `MAPPED` | `UNMAPPED` | `PARTIAL`
  - `search` _(optional, string)_: Tìm kiếm theo tên sản phẩm, tên biến thể, mã seller SKU.
  - `withTopSuggestion` _(optional, boolean)_: `true` để nhúng gợi ý tốt nhất vào các dòng chưa ghép.

- **Response `200 OK`**:

```json
{
  "items": [
    {
      "platformSkuId": "01912ecb-7241-7a01-b541-112233445566",
      "shopId": "01912ecb-7241-7a01-b541-778899aabbcc",
      "shopName": "Shop Official",
      "productName": "Áo Thun Cotton Nam Cổ Tròn",
      "variantName": "Đen / L",
      "sellerSkuCode": "TSHIRT-BLK-L",
      "imageUrl": "https://p16-oec-va.ibyteimg.com/tos-va-i-b39110/sku.jpg",
      "price": 199000,
      "currency": "VND",
      "platformStock": 50,
      "mappingStatus": "UNMAPPED",
      "pushableStock": 0,
      "mappings": [],
      "topSuggestion": {
        "merchantSkuId": "01912ecb-7241-7a01-b541-ddeeff001122",
        "merchantSkuCode": "TSHIRT-BLK-L",
        "merchantSkuName": "Áo thun cotton trơn Đen size L",
        "confidence": 95,
        "tier": "high",
        "reason": "Khớp chính xác mã Seller SKU"
      }
    }
  ],
  "hasMore": false,
  "nextCursor": null
}
```

---

### 2.3. Lấy danh sách gợi ý ghép nối (`GET /api/v1/sku-mappings/suggestions`)

Trả về danh sách các SKU kho gợi ý cho 1 tập các SKU sàn (FR-04-02).

- **Query Params**:
  - `platformSkuIds` _(array of UUID, required, max: 100)_: Danh sách ID SKU sàn cần gợi ý.

- **Thuật toán gợi ý**:
  1. **Tầng 1 (Khớp tuyệt đối)**: Trùng mã vạch GTIN/Barcode (`confidence: 100`, `tier: high`).
  2. **Tầng 2 (Khớp mã SKU)**: Trùng mã `sellerSkuCode` với `MerchantSku.code` (`confidence: 90`, `tier: high`).
  3. **Tầng 3 (So khớp tương đồng)**: Tính độ tương đồng tên sản phẩm + biến thể theo Jaccard token (`confidence: 40-80`, `tier: medium/low`).

- **Response `200 OK`**:

```json
{
  "suggestions": [
    {
      "platformSkuId": "01912ecb-7241-7a01-b541-112233445566",
      "items": [
        {
          "merchantSkuId": "01912ecb-7241-7a01-b541-ddeeff001122",
          "merchantSkuCode": "TSHIRT-BLK-L",
          "merchantSkuName": "Áo thun đen L",
          "confidence": 95,
          "tier": "high",
          "reason": "Khớp chính xác mã Seller SKU"
        }
      ]
    }
  ]
}
```

---

### 2.4. Danh sách SKU kho ứng viên khả dụng (`GET /api/v1/sku-mappings/candidates`)

Tìm kiếm SKU kho để ghép thủ công (kéo thả hoặc tìm kiếm qua popover).

- **Query Params**:
  - `platformSkuId` _(optional, UUID)_: SKU sàn đang chọn ghép.
  - `shopId` _(optional, UUID)_
  - `search` _(optional, string)_: Tìm theo mã hoặc tên SKU kho.
  - `limit` _(optional, number, default: 20)_
  - `cursor` _(optional, string)_

- **Quy tắc lọc**:
  - Tự động loại trừ SKU đã bị soft-delete hoặc ngừng kinh doanh.
  - Loại trừ SKU kho đơn lẻ đã được map cho 1 SKU sàn khác trong cùng gian hàng (**BR-04-04**).

- **Response `200 OK`**:

```json
{
  "items": [
    {
      "merchantSkuId": "01912ecb-7241-7a01-b541-ddeeff001122",
      "code": "TSHIRT-BLK-L",
      "name": "Áo thun cotton trơn Đen size L",
      "imageUrl": "https://cdn.example.com/sku.jpg",
      "baseUom": "PCS",
      "available": 120,
      "isCombo": false
    }
  ],
  "hasMore": false,
  "nextCursor": null
}
```

---

### 2.5. Tạo liên kết ghép nối (`POST /api/v1/sku-mappings`)

Dùng khi kéo thả (1–1) hoặc ghép combo (1–N thành phần).

- **Request Body**:

```json
{
  "platformSkuId": "01912ecb-7241-7a01-b541-112233445566",
  "components": [
    {
      "merchantSkuId": "01912ecb-7241-7a01-b541-ddeeff001122",
      "quantity": 1
    }
  ]
}
```

- **Validation**:
  - `platformSkuId`: UUIDv7, bắt buộc. Phải chưa có mapping trước đó (nếu đã có mapping, yêu cầu gọi API `PUT` để replace).
  - `components`: Mảng từ 1 đến 50 phần tử, không được chứa `merchantSkuId` trùng lặp trong cùng request.
  - `quantity`: Số nguyên $\ge 1$.

- **Response `201 Created`**: Trả về `SkuMappingResponseDto`.

---

### 2.6. Cập nhật / Gỡ ghép nối (`PUT /api/v1/sku-mappings/{platformSkuId}`)

Thay thế toàn bộ liên kết của SKU sàn. Truyền mảng rỗng `components: []` để **gỡ bỏ hoàn toàn** mapping.

- **Request Body**:

```json
{
  "components": []
}
```

- **Response `200 OK`**: Trả về `SkuMappingResponseDto` sau khi cập nhật (nếu mảng rỗng, `mappingStatus` trở về `UNMAPPED`).

---

### 2.7. Duyệt hàng loạt gợi ý ghép nối (`POST /api/v1/sku-mappings/accept`)

Phê duyệt hàng loạt các cặp ghép nối được hệ thống gợi ý (**FR-04-03**).

- **Request Body**:

```json
{
  "items": [
    {
      "platformSkuId": "01912ecb-7241-7a01-b541-112233445566",
      "merchantSkuId": "01912ecb-7241-7a01-b541-ddeeff001122",
      "quantity": 1
    }
  ]
}
```

- **Response `200 OK`**:

```json
{
  "decisions": [
    {
      "platformSkuId": "01912ecb-7241-7a01-b541-112233445566",
      "merchantSkuId": "01912ecb-7241-7a01-b541-ddeeff001122",
      "outcome": "ACCEPTED"
    }
  ]
}
```

_(Các outcome có thể có: `ACCEPTED`, `ALREADY_MAPPED`, `REJECTED_CONFLICT_SAME_SHOP`, `SKU_NOT_FOUND`)_.

---

### 2.8. Tự động tạo SKU kho từ listing chưa ghép (`POST /api/v1/sku-mappings/auto-create`)

Tự động tạo bản ghi `MerchantSku` từ thông tin của `PlatformSku` và tạo liên kết mapping ngay trong cùng một Transaction (**FR-04-05**).

- **Request Body**:

```json
{
  "items": [
    {
      "platformSkuId": "01912ecb-7241-7a01-b541-112233445566",
      "skuCode": "TSHIRT-BLK-L"
    }
  ]
}
```

_(Nếu `skuCode` để trống, backend sẽ tự động lấy `sellerSkuCode` từ `PlatformSku`)_.

- **Response `200 OK`**:

```json
{
  "decisions": [
    {
      "platformSkuId": "01912ecb-7241-7a01-b541-112233445566",
      "merchantSkuId": "01912ecb-7241-7a01-b541-aabbccddeeff",
      "outcome": "CREATED_AND_MAPPED"
    }
  ]
}
```

---

## 3. Quy tắc Nghiệp vụ Cốt lõi (Business Rules)

### BR-05-03: Công thức tính tồn đẩy lên sàn (Pushable Stock)

Tồn khả dụng thực tế của 1 SKU sàn được tính toán dựa trên các SKU kho thành phần:
$$\text{pushableStock} = \min_{i} \left( \left\lfloor \frac{\text{MerchantSku}_i.\text{available}}{\text{quantity}_i} \right\rfloor \right)$$

- Nếu bất kỳ SKU kho nào bị gỡ bỏ, ngừng kinh doanh hoặc hết hàng, `pushableStock = 0` và trạng thái chuyển về `PARTIAL`.

### BR-04-04: Ràng buộc trùng lặp trong cùng 1 Shop

- Không cho phép 2 SKU sàn trong cùng 1 Shop ghép tới cùng 1 SKU kho đơn lẻ (để tránh 2 biến thể trừ đè và làm sai lệch đồng bộ tồn kho).

### Xử lý hậu kỳ (Post-Mapping Trigger)

- Khi một SKU sàn được ghép nối thành công, hệ thống phải phát sự kiện `SKU_MAPPING_CHANGED` để:
  1. Kích hoạt tính toán lại tồn khả dụng và đẩy tồn lên TikTok Shop.
  2. Kích hoạt Worker hồi tố cập nhật `merchantSkuId`, trừ tồn kho và tính giá vốn/lợi nhuận cho các đơn hàng chưa xử lý đang chờ SKU sàn này.
