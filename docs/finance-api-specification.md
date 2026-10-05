# Đặc Tả API Module 10: Chi Phí & Lợi Nhuận (Finance API Specification)

Tài liệu này đặc tả toàn bộ các RESTful API của **Module 10 (M10: Chi phí & Lợi nhuận — `finance`)** và phần mở rộng tài chính cho **Module 06 (M06: Đơn hàng — `orders`)** phục vụ Frontend (`vishop-next`).

---

## Mục Lục

1. [Nguyên Tắc Chung & Quy Ước Bắt Buộc](#1-nguyên-tắc-chung--quy-ước-bắt-buộc)
2. [Danh Mục Endpoint](#2-danh-mục-endpoint)
3. [Chi Tiết Từng API](#3-chi-tiết-từng-api)
   - [3.1. Cơ cấu chi phí (FR-10-01 · BR-10-01)](#31-cơ-cấu-chi-phí-fr-10-01--br-10-01)
   - [3.2. Báo cáo lợi nhuận theo ngày (VIS-205 · FR-10-08)](#32-báo-cáo-lợi-nhuận-theo-ngày-vis-205--fr-10-08)
   - [3.3. So sánh lợi nhuận hai kỳ (VIS-216 · FR-10-12)](#33-so-sánh-lợi-nhuận-hai-kỳ-vis-216--fr-10-12)
   - [3.4. Báo cáo lợi nhuận theo gian hàng (FR-10-06)](#34-báo-cáo-lợi-nhuận-theo-gian-hàng-fr-10-06)
   - [3.5. Báo cáo lợi nhuận theo SKU kho (FR-10-07)](#35-báo-cáo-lợi-nhuận-theo-sku-kho-fr-10-07)
   - [3.6. Chi tiết lợi nhuận một đơn hàng (FR-10-02)](#36-chi-tiết-lợi-nhuận-một-đơn-hàng-fr-10-02)
   - [3.7. Bóc tách các dòng chi phí đơn hàng (FR-10-01)](#37-bóc-tách-các-dòng-chi-phí-đơn-hàng-fr-10-01)
   - [3.8. Danh sách đơn hàng kèm tài chính (VIS-189 · M06 Extension)](#38-danh-sách-đơn-hàng-kèm-tài-chính-vis-189--m06-extension)

---

## 1. Nguyên Tắc Chung & Quy Ước Bắt Buộc

### 1.1. Công thức tính lợi nhuận

$$\text{Lợi nhuận (Profit)} = \text{Doanh thu (Revenue)} - \text{Giá vốn (Cost of Goods)} - \text{Tổng chi phí sàn (Total Fees)}$$
$$\text{Biên lợi nhuận (Profit Margin)} = \frac{\text{Lợi nhuận}}{\text{Doanh thu}} \quad (\text{làm tròn 4 chữ số thập phân, ví dụ: } 0.4333 \Leftrightarrow 43.33\%)$$

### 1.2. Quy tắc thiếu dữ liệu giá vốn (BR-10-02)

- Khi một đơn hàng có ít nhất một SKU **chưa được ghép nối với SKU kho** hoặc SKU kho **chưa có giá vốn** (`avgCost === null`):
  - `costOfGoods = null`
  - `profit = null`
  - `profitMargin = null`
  - `dataStatus = 'insufficient_data'`
  - `unavailableReason = 'missing_cost_data'`
  - **Tuyệt đối không coi giá vốn là 0** để tránh tính khống lợi nhuận và gây hiểu nhầm cho người bán.

### 1.3. Phân quyền và bảo mật dữ liệu nhạy cảm (FR-01-06)

- `finance.view`: Quyền truy cập vào màn hình và các endpoint báo cáo tài chính.
- `data.cost.view`: Quyền xem các trường liên quan đến giá vốn (`costOfGoods`, `totalCostOfGoods`). Nếu thiếu quyền, server tự động lược bỏ trường này về `null`.
- `data.profit.view`: Quyền xem các trường lợi nhuận (`profit`, `profitMargin`, `totalProfit`).
  - Với các API danh sách & báo cáo: Server tự động gạt `profit = null`, `profitMargin = null`.
  - Với API chi tiết lợi nhuận đơn (`GET /orders/:orderId/profit`): Server trả về lỗi **`403 Forbidden`**.

### 1.4. Trạng thái dữ liệu (`DataStatus`)

- `estimated`: Dữ liệu ước tính (trước khi nhận được đối soát / payout từ sàn).
- `reconciled`: Dữ liệu thực tế đã được đối soát từ Statement Transactions của sàn.
- `insufficient_data`: Dữ liệu không đầy đủ (thiếu giá vốn SKU hoặc thiếu liên kết kho).

---

## 2. Danh Mục Endpoint

| Method | Endpoint                               | Quyền yêu cầu                      | Mô tả                                                      | Ticket / FR         |
| :----- | :------------------------------------- | :--------------------------------- | :--------------------------------------------------------- | :------------------ |
| `GET`  | `/api/v1/reports/profit/fee-breakdown` | `finance.view`                     | Tổng hợp cơ cấu chi phí sàn trong kỳ (ước tính & đối soát) | FR-10-01 · BR-10-01 |
| `GET`  | `/api/v1/reports/profit/by-day`        | `finance.view`                     | Báo cáo doanh thu & lợi nhuận theo chuỗi ngày GMT+7        | VIS-205 · FR-10-08  |
| `GET`  | `/api/v1/reports/profit/compare`       | `finance.view`                     | So sánh chỉ số tài chính giữa 2 kỳ (`shop`, `sku`, `day`)  | VIS-216 · FR-10-12  |
| `GET`  | `/api/v1/reports/profit/by-shop`       | `finance.view`                     | Báo cáo lợi nhuận theo từng gian hàng liên kết             | FR-10-06            |
| `GET`  | `/api/v1/reports/profit/by-sku`        | `finance.view`                     | Báo cáo lợi nhuận theo SKU kho (kèm số bán, tỉ lệ hoàn)    | FR-10-07            |
| `GET`  | `/api/v1/orders/{orderId}/profit`      | `finance.view`, `data.profit.view` | Xem chi tiết giá vốn, chi phí, lợi nhuận của 1 đơn         | FR-10-02            |
| `GET`  | `/api/v1/orders/{orderId}/fees`        | `orders.view`                      | Bóc tách từng dòng chi phí của đơn (không gộp)             | FR-10-01            |
| `GET`  | `/api/v1/orders`                       | `orders.view`                      | Danh sách đơn hàng trả kèm `financialSummary`              | VIS-189 · FR-10-02  |

---

## 3. Chi Tiết Từng API

### 3.1. Cơ cấu chi phí (FR-10-01 · BR-10-01)

- **Endpoint**: `GET /api/v1/reports/profit/fee-breakdown`
- **Mô tả**: Bóc tách chi tiết từng loại chi phí trong kỳ (hoa hồng sàn, phí thanh toán, hoa hồng affiliate...), phân tách rõ 2 cột **Ước tính** (`estimated`) và **Đã đối soát** (`settled`), kèm **Tổng** và **Tỉ trọng / doanh thu** (`shareOfRevenue`).

#### Request Query Parameters (`FeeBreakdownQueryDto`)

```typescript
interface FeeBreakdownQueryDto {
  from?: Date; // Bắt đầu kỳ (ISO 8601, mặc định 30 ngày trước)
  to?: Date; // Kết thúc kỳ (ISO 8601, mặc định hiện tại)
  shopIds?: string[]; // Lọc danh sách shopId (UUID)
}
```

#### Response Payload (`FeeBreakdownResponseDto`)

```json
{
  "period": {
    "from": "2026-08-01T00:00:00.000Z",
    "to": "2026-08-31T23:59:59.999Z"
  },
  "totalRevenue": 120000000,
  "totalFees": 12000000,
  "items": [
    {
      "feeType": "platform_commission",
      "label": "Hoa hồng nền tảng (TikTok Shop)",
      "estimated": 6000000,
      "settled": 0,
      "total": 6000000,
      "shareOfRevenue": 0.05
    },
    {
      "feeType": "affiliate_commission",
      "label": "Hoa hồng tiếp thị liên kết (Affiliate / Creator)",
      "estimated": 4200000,
      "settled": 0,
      "total": 4200000,
      "shareOfRevenue": 0.035
    },
    {
      "feeType": "payment_processing",
      "label": "Phí xử lý thanh toán (Giao dịch)",
      "estimated": 1800000,
      "settled": 0,
      "total": 1800000,
      "shareOfRevenue": 0.015
    }
  ]
}
```

---

### 3.2. Báo cáo lợi nhuận theo ngày (VIS-205 · FR-10-08)

- **Endpoint**: `GET /api/v1/reports/profit/by-day`
- **Mô tả**: Trả về dữ liệu tài chính theo từng ngày (lịch GMT+7). Đánh dấu `isHighMissingData = true` khi tỉ lệ đơn thiếu giá vốn $\ge 10\%$.

#### Request Query Parameters (`DailyProfitReportQueryDto`)

```typescript
interface DailyProfitReportQueryDto {
  from?: Date; // Thời điểm bắt đầu (ISO 8601, mặc định 30 ngày trước)
  to?: Date; // Thời điểm kết thúc (ISO 8601, mặc định hiện tại)
  shopIds?: string[]; // Lọc danh sách shopId (UUID)
}
```

#### Response Payload (`DailyProfitReportResponseDto`)

```json
{
  "period": {
    "from": "2026-08-01T00:00:00.000Z",
    "to": "2026-08-31T23:59:59.999Z"
  },
  "items": [
    {
      "date": "2026-08-24",
      "revenue": 4500000,
      "costOfGoods": 2100000,
      "totalFees": 450000,
      "profit": 1950000,
      "profitMargin": 0.4333,
      "orderCount": 15,
      "missingCostOrderCount": 0,
      "isHighMissingData": false,
      "dataStatus": "estimated"
    }
  ],
  "summary": {
    "totalRevenue": 120000000,
    "totalCostOfGoods": 60000000,
    "totalFees": 12000000,
    "totalProfit": 48000000,
    "profitMargin": 0.4,
    "totalOrders": 350,
    "totalMissingCostOrders": 2,
    "dataStatus": "estimated"
  }
}
```

---

### 3.3. So sánh lợi nhuận hai kỳ (VIS-216 · FR-10-12)

- **Endpoint**: `GET /api/v1/reports/profit/compare`
- **Mô tả**: So sánh hai kỳ tài chính theo lát cắt `shop`, `sku`, hoặc `day`. Tính chênh lệch tuyệt đối, chênh lệch % và giá trị bình quân ngày (`dailyAverage`).

#### Request Query Parameters (`ProfitCompareQueryDto`)

```typescript
interface ProfitCompareQueryDto {
  from: Date; // Kỳ 1: Bắt đầu (bắt buộc)
  to: Date; // Kỳ 1: Kết thúc (bắt buộc)
  compareFrom: Date; // Kỳ 2: Bắt đầu (bắt buộc)
  compareTo: Date; // Kỳ 2: Kết thúc (bắt buộc)
  dimension: 'shop' | 'sku' | 'day'; // Chiều phân tích (bắt buộc)
  shopIds?: string[]; // Lọc danh sách shopId
}
```

#### Response Payload (`ProfitCompareResponseDto`)

```json
{
  "period1": {
    "from": "2026-08-01T00:00:00.000Z",
    "to": "2026-08-10T23:59:59.999Z",
    "days": 10
  },
  "period2": {
    "from": "2026-07-01T00:00:00.000Z",
    "to": "2026-07-31T23:59:59.999Z",
    "days": 31
  },
  "dimension": "shop",
  "items": [
    {
      "key": "01973abc-1234-7000-8000-000000000001",
      "name": "Shop Thời Trang ViShop",
      "period1": {
        "revenue": 10000000,
        "costOfGoods": 4000000,
        "totalFees": 650000,
        "profit": 5350000,
        "profitMargin": 0.535,
        "orderCount": 50,
        "soldQuantity": 50,
        "dataStatus": "estimated"
      },
      "period2": {
        "revenue": 20000000,
        "costOfGoods": 8000000,
        "totalFees": 1300000,
        "profit": 10700000,
        "profitMargin": 0.535,
        "orderCount": 100,
        "soldQuantity": 100,
        "dataStatus": "estimated"
      },
      "diffAbsolute": {
        "revenue": -10000000,
        "costOfGoods": -4000000,
        "totalFees": -650000,
        "profit": -5350000,
        "profitMargin": 0,
        "orderCount": -50,
        "soldQuantity": -50
      },
      "diffPercent": {
        "revenue": -50.0,
        "costOfGoods": -50.0,
        "totalFees": -50.0,
        "profit": -50.0,
        "profitMargin": 0,
        "orderCount": -50.0,
        "soldQuantity": -50.0
      },
      "dailyAverage": {
        "period1": {
          "revenue": 1000000,
          "costOfGoods": 400000,
          "totalFees": 65000,
          "profit": 535000,
          "profitMargin": 0.535,
          "orderCount": 5.0,
          "soldQuantity": 5.0,
          "dataStatus": "estimated"
        },
        "period2": {
          "revenue": 645161,
          "costOfGoods": 258065,
          "totalFees": 41935,
          "profit": 345161,
          "profitMargin": 0.535,
          "orderCount": 3.23,
          "soldQuantity": 3.23,
          "dataStatus": "estimated"
        },
        "diffAbsolute": {
          "revenue": 354839,
          "costOfGoods": 141935,
          "totalFees": 23065,
          "profit": 189839,
          "profitMargin": 0,
          "orderCount": 1.77,
          "soldQuantity": 1.77
        },
        "diffPercent": {
          "revenue": 55.0,
          "costOfGoods": 55.0,
          "totalFees": 55.0,
          "profit": 55.0,
          "profitMargin": 0,
          "orderCount": 54.8,
          "soldQuantity": 54.8
        }
      }
    }
  ]
}
```

---

### 3.4. Báo cáo lợi nhuận theo gian hàng (FR-10-06)

- **Endpoint**: `GET /api/v1/reports/profit/by-shop`
- **Mô tả**: Tổng hợp số liệu tài chính theo từng shop kết nối trong kỳ.

#### Request Query Parameters (`ProfitByShopQueryDto`)

```typescript
interface ProfitByShopQueryDto {
  from?: Date;
  to?: Date;
  shopIds?: string[];
}
```

#### Response Payload (`ProfitByShopResponseDto`)

```json
{
  "period": {
    "from": "2026-08-01T00:00:00.000Z",
    "to": "2026-08-31T23:59:59.999Z"
  },
  "items": [
    {
      "shopId": "01973abc-1234-7000-8000-000000000001",
      "shopName": "Gian Hàng Thời Trang Nam",
      "platform": "TIKTOK",
      "revenue": 50000000,
      "costOfGoods": 22000000,
      "totalFees": 5500000,
      "profit": 22500000,
      "profitMargin": 0.45,
      "orderCount": 180,
      "missingCostOrderCount": 0,
      "dataStatus": "estimated"
    }
  ]
}
```

---

### 3.5. Báo cáo lợi nhuận theo SKU kho (FR-10-07)

- **Endpoint**: `GET /api/v1/reports/profit/by-sku`
- **Mô tả**: Tổng hợp doanh thu, giá vốn, chi phí, lợi nhuận, số lượng bán và tỉ lệ hoàn theo từng SKU kho (`merchantSkuId`), hỗ trợ tìm kiếm, lọc nhóm hàng, phân trang 2 chế độ (Offset / Cursor) và sắp xếp nhiều tiêu chí.

#### Request Query Parameters (`ProfitBySkuQueryDto`)

```typescript
interface ProfitBySkuQueryDto {
  from?: Date; // Thời điểm bắt đầu (ISO 8601, mặc định 30 ngày trước)
  to?: Date; // Thời điểm kết thúc (ISO 8601, mặc định hiện tại)
  shopIds?: string[]; // Lọc theo danh sách gian hàng (UUID)
  search?: string; // Tìm kiếm theo mã SKU (skuCode) hoặc tên SKU / sản phẩm (skuName)
  categoryIds?: string[]; // Lọc theo danh sách nhóm hàng (categoryId, UUID)

  // 🟢 Phân trang (hỗ trợ cả 2 kiểu):
  page?: number; // Số trang (Offset pagination, >= 1). Khi gửi trường này, API trả OffsetListResponse
  limit?: number; // Kích thước trang (mặc định 20, tối đa 100)
  cursor?: string; // Con trỏ lấy trang tiếp theo (Cursor pagination). Khi không gửi `page`, API trả ListResponse

  // 🟢 Sắp xếp (Sorting):
  sortBy?:
    'revenue' | 'profit' | 'soldQuantity' | 'profitMargin' | 'orderCount' | 'skuCode' | 'skuName'; // Mặc định: 'revenue'
  sortOrder?: 'asc' | 'desc'; // Mặc định: 'desc'
}
```

#### Response Payload (`OffsetListResponseDto<ProfitBySkuItemDto>` / `ListResponseDto<ProfitBySkuItemDto>`)

- **Khi dùng phân trang theo trang (`?page=1&limit=20`)**:

```json
{
  "object": "list",
  "url": "/api/v1/reports/profit/by-sku",
  "data": [
    {
      "merchantSkuId": "01973sku-1234-7000-8000-000000000001",
      "skuCode": "AO-POLO-DEN-M",
      "skuName": "Áo Polo Nam Đen Size M",
      "categoryId": "01973cat-1234-7000-8000-000000000001",
      "categoryName": "Thời Trang Nam",
      "imageUrl": "https://cdn.example.com/sku/polo-black.jpg",
      "imageHue": 210,
      "orderCount": 85,
      "revenue": 15000000,
      "costOfGoods": 6000000,
      "totalFees": 1650000,
      "profit": 7350000,
      "profitMargin": 0.49,
      "soldQuantity": 100,
      "returnQuantity": 2,
      "returnRate": 0.02,
      "dataStatus": "estimated",
      "affiliate": {
        "quantity": 60,
        "revenue": 9000000,
        "profit": 4200000,
        "margin": 0.4667
      },
      "nonAffiliate": {
        "quantity": 40,
        "revenue": 6000000,
        "profit": 3150000,
        "margin": 0.525
      },
      "affiliateShare": 60
    }
  ],
  "pageInfo": {
    "page": 1,
    "limit": 20,
    "total": 120,
    "totalPages": 6,
    "hasNextPage": true
  }
}
```

- **Khi dùng phân trang theo Cursor (`?limit=20&cursor=...`)**:

```json
{
  "object": "list",
  "url": "/api/v1/reports/profit/by-sku",
  "data": [
    {
      "merchantSkuId": "01973sku-1234-7000-8000-000000000001",
      "skuCode": "AO-POLO-DEN-M",
      "skuName": "Áo Polo Nam Đen Size M",
      "categoryId": "01973cat-1234-7000-8000-000000000001",
      "categoryName": "Thời Trang Nam",
      "imageUrl": "https://cdn.example.com/sku/polo-black.jpg",
      "imageHue": 210,
      "orderCount": 85,
      "revenue": 15000000,
      "costOfGoods": 6000000,
      "totalFees": 1650000,
      "profit": 7350000,
      "profitMargin": 0.49,
      "soldQuantity": 100,
      "returnQuantity": 2,
      "returnRate": 0.02,
      "dataStatus": "estimated",
      "affiliate": {
        "quantity": 60,
        "revenue": 9000000,
        "profit": 4200000,
        "margin": 0.4667
      },
      "nonAffiliate": {
        "quantity": 40,
        "revenue": 6000000,
        "profit": 3150000,
        "margin": 0.525
      },
      "affiliateShare": 60
    }
  ],
  "pageInfo": {
    "hasNextPage": true,
    "nextCursor": "01973sku-1234-7000-8000-000000000001"
  }
}
```

---

### 3.6. Chi tiết lợi nhuận một đơn hàng (FR-10-02)

- **Endpoint**: `GET /api/v1/orders/{orderId}/profit`
- **Mô tả**: Bóc tách lợi nhuận cụ thể cho một đơn hàng.
- **Yêu cầu quyền**: `data.profit.view` (trả `403 Forbidden` nếu người gọi thiếu quyền).

#### Path Parameters

- `orderId` (`string`, UUID): ID nội bộ của đơn hàng trong ViShop.

#### Response Payload (`OrderProfitResponseDto`)

```json
{
  "revenue": 450000,
  "costOfGoods": 210000,
  "fees": [
    {
      "feeType": "platform_commission",
      "amount": 22500,
      "source": "estimated",
      "description": "Phí hoa hồng sàn TikTok Shop (5%)"
    },
    {
      "feeType": "payment_processing",
      "amount": 6750,
      "source": "estimated",
      "description": "Phí xử lý thanh toán (1.5%)"
    }
  ],
  "profit": 210750,
  "profitMargin": 0.4683,
  "dataStatus": "estimated",
  "missingCostSkuIds": []
}
```

_Trường hợp đơn thiếu giá vốn SKU (BR-10-02)_:

```json
{
  "revenue": 450000,
  "costOfGoods": null,
  "fees": [],
  "profit": null,
  "profitMargin": null,
  "dataStatus": "insufficient_data",
  "missingCostSkuIds": ["01973sku-1234-7000-8000-000000000099"]
}
```

---

### 3.7. Bóc tách các dòng chi phí đơn hàng (FR-10-01)

- **Endpoint**: `GET /api/v1/orders/{orderId}/fees`
- **Mô tả**: Trả về danh sách từng khoản phí của đơn hàng, **mỗi loại phí là một dòng riêng, không gộp**.

#### Path Parameters

- `orderId` (`string`, UUID): ID nội bộ của đơn hàng.

#### Response Payload (`OrderFeesResponseDto`)

```json
{
  "orderId": "01973abc-1234-7000-8000-000000000001",
  "externalOrderId": "576462438203756789",
  "totalFees": 29250,
  "fees": [
    {
      "feeType": "platform_commission",
      "amount": 22500,
      "source": "estimated",
      "description": "Phí hoa hồng sàn TikTok Shop (5%)"
    },
    {
      "feeType": "payment_processing",
      "amount": 6750,
      "source": "estimated",
      "description": "Phí xử lý thanh toán (1.5%)"
    }
  ],
  "isEstimated": true
}
```

---

### 3.8. Danh sách đơn hàng kèm tài chính (VIS-189 · M06 Extension)

- **Endpoint**: `GET /api/v1/orders`
- **Mô tả**: Trả về danh sách đơn hàng có trường `financialSummary` cho từng đơn, hỗ trợ lọc và sắp xếp theo lợi nhuận tại server.

#### Query Parameters bổ sung (`FindOrdersQueryDto`)

- `isLoss` (`boolean`): Lọc danh sách đơn hàng bị lỗ (`profit < 0`).
- `profitMin` (`number`): Lọc đơn có lợi nhuận $\ge \text{profitMin}$ (VND).
- `profitMax` (`number`): Lọc đơn có lợi nhuận $\le \text{profitMax}$ (VND).
- `profitMarginMin` (`number`): Lọc đơn có biên lợi nhuận $\ge \text{profitMarginMin}$ (ví dụ: `0.15`).
- `profitMarginMax` (`number`): Lọc đơn có biên lợi nhuận $\le \text{profitMarginMax}$ (ví dụ: `0.50`).
- `dataStatus` (`'estimated' | 'reconciled' | 'insufficient_data'`): Lọc theo trạng thái dữ liệu.
- `sortBy` (`'profit' | 'profitMargin' | 'orderedAt' | 'totalAmount'`): Trường sắp xếp.
- `sortDirection` (`'asc' | 'desc'`): Chiều sắp xếp.

#### Response Item (`financialSummary` trong `OrderResponseDto`)

```json
{
  "id": "01973abc-1234-7000-8000-000000000001",
  "externalOrderId": "576462438203756789",
  "status": "AWAITING_SHIPMENT",
  "totalAmount": 450000,
  "financialSummary": {
    "revenue": 450000,
    "costOfGoods": 210000,
    "profit": 210750,
    "profitMargin": 0.4683,
    "dataStatus": "estimated",
    "unavailableReason": null
  }
}
```
