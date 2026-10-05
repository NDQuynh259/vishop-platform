# Stock Alerts (FR-05-10 / FR-05-27 / FR-03-09)

`StockAlertService` trong `inventory-service` và quy tắc nghiệp vụ `StockAlertEvaluator` cung cấp nguồn dữ liệu đánh giá cảnh báo tồn kho thấp cho toàn bộ hệ thống.

Consumers include:

- Inventory low-stock alerts & dashboard views (FR-05-10 / FR-05-27)
- Inventory Reports (`/reports/inventory` — FR-13-04 / VIS-73)
- Notification Center low-stock alert events (FR-14-02 / VIS-94)
- ViAI Assistant overview (`days_of_stock` feed — FR-13-17 / VIS-252)

## Inputs

- `MerchantSku.lowStockThreshold`: The persisted static fallback threshold (FR-03-09).
- `Inventory.available`: Summed across **all** warehouses of the tenant. Sellable available stock is evaluated organization-wide; separate warehouse shortages do not trigger false alerts when internal transfers are possible. (For combo SKUs, virtual available stock is derived from the minimum component availability ratio).
- `OrderLine.quantity`: Sales velocity is aggregated from active orders within the configured sales window (`orders.status <> 'CANCELLED'`).

## Policy & Tenant Defaults

Consumers may pass an explicit policy or rely on tenant settings:

```ts
const policy = {
  salesWindowDays: 30, // Default: 30 days
  minimumHistoryDays: 14, // Default: 14 days
  lowStockDaysThreshold: 7, // Default: TenantSetting.lowStockCoverDays (default 7)
};
```

The service validates that all values are positive integers and enforces `minimumHistoryDays <= salesWindowDays`.

## Assessment Logic

1. **Sufficient Sales History** (`historyDays >= minimumHistoryDays`):

   ```text
   averageDailySales = totalSoldQuantity / salesWindowDays
   daysOfStock = available / averageDailySales
   ```
   - If `averageDailySales === 0`: `daysOfStock: null`, `alert: false`, `source: 'days_of_stock'`.
   - If `daysOfStock <= lowStockDaysThreshold`: `alert: true`, `source: 'days_of_stock'`.
   - If `daysOfStock > lowStockDaysThreshold`: `alert: false`, `source: 'days_of_stock'`.

2. **Insufficient Sales History** (`historyDays < minimumHistoryDays`):
   - Falls back to `lowStockThreshold` (FR-03-09):
     - `alert: lowStockThreshold !== null && available <= lowStockThreshold`
     - `source: lowStockThreshold === null ? 'none' : 'static_threshold'`
   - The UI and consumers expose `source: 'static_threshold'` so operators are aware the static fallback is being used.

3. **Discontinued SKUs** (`status === 'DISCONTINUED'`):
   - Always returns `alert: false` and `source: 'none'`.
