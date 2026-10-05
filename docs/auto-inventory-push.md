# Kế hoạch Triển khai FR-05-14: Đẩy tồn tự động khi có bất kỳ biến động nào

## 1. Tổng quan & Mục tiêu

Cơ chế lõi giữ tồn kho trên sàn TikTok Shop luôn khớp gần thời gian thực với tồn kho trong ViShop. Mọi biến động ảnh hưởng đến tồn khả dụng (`available`) — bao gồm nhập kho, xuất kho, kiểm kho điều chỉnh, chuyển kho, đơn hàng mới phát sinh (khóa tồn), và huỷ đơn hàng trước khi RTS (trả tồn khả dụng) — phải kích hoạt tính toán và đẩy tồn tự động lên các gian hàng đã liên kết và bật đẩy tồn, đảm bảo SLA **≤ 30 giây**.

---

## 2. Tiêu chí Hoàn thành & Quy tắc Nghiệp vụ

1. **Kích hoạt tự động (Event Triggers)**:
   - Mọi biến động `available` phát sinh từ:
     - Nhập kho (`StockInCommand`)
     - Xuất kho (`StockOutCommand`)
     - Điều chỉnh kiểm kho (`ConfirmStocktakeCommand`)
     - Chuyển kho (`ConfirmTransferCommand`)
     - Đơn hàng mới (`OrderCreatedEvent` / đặt hàng khóa tồn)
     - Huỷ đơn hàng (`OrderCancelledEvent` / hoàn trả tồn khóa)
     - Nhập kho file Excel (`ImportInventoryCommand`)
   - Tự động kích hoạt luồng tính toán tồn đẩy cho các shop thỏa mãn điều kiện.

2. **Lọc điều kiện tham gia đẩy tồn (BR-05-02 & FR-05-11)**:
   - Gian hàng phải có `inventoryPushEnabled = true`.
   - SKU phải được ghép nối với ít nhất 1 listing trên sàn (`SkuMapping` liên kết `PlatformSku`).
   - SKU chưa ghép nối hoặc shop tắt đẩy tồn thì hoàn toàn bỏ qua.

3. **Xử lý Combo SKU (BR-05-03)**:
   - Khi một SKU con (`componentSkuId`) có biến động tồn kho:
     - Hệ thống tự động truy vấn tìm tất cả các combo SKU cha (`comboSkuId`) thông qua quan hệ `ComboComponent`.
     - Tính lại tồn khả dụng cho combo cha theo công thức:
       $$\text{available}_{\text{combo}} = \min_{i} \left( \left\lfloor \frac{\text{available}_{\text{component}_i}}{\text{quantity}_i} \right\rfloor \right)$$
     - Đẩy tồn cập nhật cho toàn bộ các combo cha bị ảnh hưởng.

4. **Công thức tính SL đẩy (BR-05-01, FR-05-12, FR-05-13)**:
   $$\text{SL đẩy} = \left\lfloor \max(\text{available} - \text{effectiveSafetyStock}, 0) \times \frac{\text{pushRatio}}{100} \right\rfloor$$
   - `effectiveSafetyStock`: ưu tiên tồn an toàn override của SKU tại shop (`ShopInventorySafetyStock`), nếu không có dùng mặc định của shop (`TikTokShop.inventorySafetyStock`).
   - `pushRatio`: tỉ lệ đẩy cấu hình theo shop (`TikTokShop.inventoryPushRatio`).

5. **Gộp biến động (Coalescing / Debouncing)**:
   - Khi có nhiều biến động liên tiếp trên cùng một SKU trong thời gian ngắn (ví dụ flash sale đơn về dồn dập), hệ thống gộp thành **1 lần đẩy duy nhất** mang giá trị tính toán mới nhất, không xếp hàng đẩy từng bước trung gian gây nghẽn hàng đợi và lãng phí quota API sàn.
   - Sử dụng Redis State + BullMQ Job Deduplication với `jobId` định danh theo cặp `(shopId, platformSkuId)`.

6. **Chống lệnh cũ ghi đè lệnh mới (Out-of-Order Rejection)**:
   - Mỗi đợt tính toán mang một phiên bản tuần tự đơn điệu (`pushVersion` hoặc timestamp biến động).
   - Trước khi gửi lên sàn (hoặc khi xử lý job trong worker), kiểm tra `pushVersion >= lastPushedVersion`. Nếu lệnh đến sau mang phiên bản cũ hơn thì lập tức hủy bỏ (`DROP / NO-OP`).

---

## 3. Kiến trúc Luồng Xử lý (Architecture Flow)

```mermaid
flowchart TD
    A[Biến động tồn kho: StockIn / StockOut / Stocktake / Order] --> B[Domain Event / CommandBus: InventoryChangedEvent]
    B --> C[InventoryPushEventListener]
    C --> D[Resolve affected SKUs: Single SKU + Parent Combos]
    D --> E[Query active SkuMappings & Connected Shops with Push Enabled]
    E --> F[Calculate push quantity: Formula BR-05-01]
    F --> G[Redis State: Update Latest Value & Push Version]
    G --> H[Kafka: Publish Coalesced Push Event to vishop.inventory.push-stock]
    H --> I[Kafka Consumer: TikTokPushStockConsumer]
    I --> J{Check Out-of-Order?}
    J -- Version is Stale --> K[Drop Event / No-op]
    J -- Version is Fresh --> L[Call TikTok API: /product/202309/products/{id}/inventory/update]
    L --> M[Record EventLog & Update lastPushedVersion]
```

---

## 4. Kế hoạch Thay đổi Code Chi tiết

### Component 1: Core Domain Events
- Định nghĩa `InventoryChangedEvent` chứa `{ tenantId, merchantSkuIds: List<UUID>, reason: String, correlationId: UUID }` implements `DomainEvent`.

### Component 2: Service Tính toán & Điều phối Đẩy tồn
- `InventoryPushCoordinator`:
  - `resolveAffectedSkuIds(tenantId, skuIds)`: Tìm SKU con + tất cả combo cha chứa SKU con.
  - `calculateShopSkuPushPayloads(tenantId, skuIds)`: Tính toán số lượng tồn đẩy cho từng shop và listing dựa trên công thức `BR-05-01`.
  - `enqueueCoalescedPushes(payloads)`: Ghi nhận version mới nhất vào Redis và bắn event sang topic Kafka `vishop.inventory.push-stock` với partition key theo `shopId`.

### Component 3: Kafka Event Listener
- Lắng nghe `InventoryChangedEvent` và kích hoạt luồng tính toán đẩy tồn.
  - Gọi `InventoryPushCoordinatorService` để thực hiện tính toán và lên lịch đẩy tồn tự động.

### Component 4: Worker Processor Nâng cấp & Out-of-Order Rejection

- **[MODIFY]** `apps/worker/src/app/processors/tiktok-push-stock.processor.ts`:
  - Kiểm tra `version` của job so với `lastPushedVersion` trong Redis.
  - Gọi API cập nhật tồn kho TikTok Shop.
  - Ghi nhận `EventLog` và cập nhật `lastPushedVersion` khi thành công.

### Component 5: Kích hoạt Event từ các Nghiệp vụ Tồn kho

- **[MODIFY]** `libs/modules/inventories/src/application/commands/stock-in/stock-in.handler.ts`
- **[MODIFY]** `libs/modules/inventories/src/application/commands/stock-out/stock-out.handler.ts`
- **[MODIFY]** `libs/modules/inventories/src/application/commands/stocktakes/confirm-stocktake/confirm-stocktake.handler.ts`
- **[MODIFY]** `libs/modules/inventories/src/application/commands/import-inventory/import-inventory.handler.ts`
- Phát event `InventoryChangedEvent` sau khi giao dịch cơ sở dữ liệu hoàn tất.

---

## 5. Kế hoạch Kiểm thử & Xác minh

### Automated Tests

1. **Unit Tests cho Service điều phối**:
   - Test giải quyết combo cha khi SKU con thay đổi.
   - Test lọc bỏ SKU chưa ghép nối hoặc shop tắt đẩy tồn.
   - Test công thức tính số lượng đẩy với tồn an toàn 2 cấp và tỉ lệ đẩy.
   - Test cơ chế chống lệnh cũ ghi đè lệnh mới (out-of-order rejection).
2. **Unit Tests cho Event Listener**:
   - Verify event listener nhận event và điều phối tính toán chính xác.
3. **Unit Tests cho Worker Processor**:
   - Verify xử lý job, gọi TikTok API, bỏ qua job khi có version cũ hơn, và ghi nhật ký event log.
4. **Integration / Monorepo Gates**:
   - Chạy `pnpm nx test modules-shops`
   - Chạy `pnpm nx test modules-inventories`
   - Chạy `pnpm nx test worker`
   - Chạy `pnpm nx run-many -t typecheck` và `lint`

---

## 6. Câu hỏi Mở / Quyết định Cần Thống nhất

> [!NOTE]
>
> 1. **Batching size**: Mặc định gửi ngay từng cặp listing (hoặc gom tối đa 500 SKU nếu cùng thời điểm) để tối ưu SLA ≤ 30s.
> 2. **Governor rate-limit**: Khi API sàn bị rate-limit hoặc governor tạm giữ, job tự động retry qua cơ chế exponential backoff của BullMQ.
