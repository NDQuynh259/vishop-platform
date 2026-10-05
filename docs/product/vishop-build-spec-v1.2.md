# ViShop — Build Spec v1.2

## Tài liệu bàn giao đội phát triển

**Sản phẩm:** ViShop — Phần mềm quản lý gian hàng TikTok Shop
**Chủ quản:** ViTechGroup · **Phiên bản:** v1.2 (hợp nhất) + bản vá §13 ngày 07/08/2026 · **Ngày:** 04/08/2026
**Thay thế:** PRD v1.0 và Amendment v1.1 — đây là **bản duy nhất có hiệu lực**

> **§13 là bản vá sau khi dựng xong UI.** Mười chỗ trong tài liệu này đã sai hoặc thiếu (chi tiết ở §13.1); nội dung §13 **thắng** nội dung các mục trên khi hai bên mâu thuẫn. Ảnh hưởng trực tiếp tới §1.3 (phạm vi), §3.3 (trường Phase 2), §4.2 (máy trạng thái Package), §4.3 (tồn kho), §9 (Capability Matrix) và §11 (Phase 2), cộng ba FR mới không sửa mục nào có sẵn (bộ nhận webhook M00, duyệt/từ chối huỷ M09, quét toàn màn hình M08). Đọc §13 trước khi tin bất kỳ mục nào trong số đó.

---

## ĐỌC TRƯỚC KHI BẮT ĐẦU

### Hai cổng chặn — chưa qua thì không viết code nghiệp vụ

| Gate   | Nội dung                                                       | Chủ trì        | Chặn cái gì       |
| ------ | -------------------------------------------------------------- | -------------- | ----------------- |
| **G0** | Tư cách **TikTok Shop Seller Partner API**                     | BD/Legal       | Toàn bộ dự án     |
| **G1** | **API Capability Matrix** xác minh xong cho toàn bộ FR P0 (§9) | Tech Lead + PM | Khoá scope sprint |

Song song, không chặn: **ToS check** về quyền lưu trữ và dùng lại dữ liệu creator (§10, R10) — chốt chặn duy nhất của M11-B. _(Bản trước ghi thêm "hồ sơ TAP" ở đây. Sai — xem §2.2.)_

### Nguyên tắc làm việc với tài liệu này

- Mọi FR có ID cố định. Khi tạo ticket, **giữ nguyên ID** trong tiêu đề để truy vết ngược.
- **P0** = không có thì không ship · **P1** = fast follow sau GA · **P2** = chỉ thiết kế schema/interface, không xây.
- Gặp mâu thuẫn giữa tài liệu này và thực tế API → **API thắng**. Ghi vào Capability Matrix và báo PM, không tự quyết đổi nghiệp vụ.
- Mọi thay đổi scope phải kèm một hạng mục bị cắt.

---

## MỤC LỤC

1. Phạm vi sản phẩm · 2. Quyết định kiến trúc bắt buộc · 3. Mô hình dữ liệu lõi · 4. Máy trạng thái
2. Module M00–M15 (Phase 1) · 6. Yêu cầu phi chức năng · 7. Definition of Done
3. Lộ trình & phụ thuộc · 9. API Capability Matrix (bắt buộc điền) · 10. Rủi ro · 11. Phase 2 · 12. Câu hỏi mở
4. **Bản vá 07/08/2026 — sau khi dựng xong UI** (thắng các mục trên khi mâu thuẫn)

---

# 1. PHẠM VI SẢN PHẨM

## 1.1 Sản phẩm giải quyết gì

Nhà bán hàng TikTok Shop VN vận hành nhiều gian hàng đang phải: đăng nhập từng Seller Center lấy đơn, in vận đơn riêng từng shop, cập nhật tồn bằng Excel, và ước lượng lợi nhuận bằng cảm tính.

Phase 1 phải đạt: **một seller chạy 1–10 gian hàng ngừng dùng Seller Center cho vận hành hằng ngày, và lần đầu nhìn thấy lợi nhuận thật theo từng đơn.**

## 1.2 Trong phạm vi Phase 1

Kết nối đa gian hàng TikTok Shop · Đồng bộ đơn/sản phẩm · SKU kho + ghép nối · Tồn kho + đẩy tồn đa shop · Xử lý đơn theo package (RTS) · In tem hàng loạt · Quét kiểm hàng · Sau bán/hoàn hàng · Bóc tách chi phí & lợi nhuận · Affiliate insight · Livestream (ước tính) · Đối soát payout · Báo cáo · Cảnh báo · Phân quyền · Billing

## 1.3 NGOÀI phạm vi Phase 1 — không tự ý thêm

| Không làm                                  | Lý do                                                                                                                      |
| ------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------- |
| Đăng bán / cào sản phẩm / nhân bản listing | Vùng xám điều khoản sàn; không phải nỗi đau chính của seller TikTok                                                        |
| Sàn khác ngoài TikTok Shop                 | Phase 2. Nhưng **kiến trúc phải sẵn sàng** (§2.1)                                                                          |
| ~~Đa kho, vị trí kệ, wave picking~~        | **Bỏ khỏi danh sách này (§13, 07/08/2026)** — đã đưa vào Phase 1. Còn ở Phase 2: quy tắc phân bổ kho tự động, PDA, số seri |
| POS bán lẻ, CRM, loyalty                   | Khác persona; sàn không đẩy PII                                                                                            |
| Quản lý quảng cáo                          | Phase 2                                                                                                                    |
| Mobile app                                 | Web responsive là đủ cho Phase 1                                                                                           |
| Kết nối 3PL trực tiếp                      | Phase 1 dùng vận chuyển của sàn                                                                                            |

## 1.4 Ba trụ khác biệt — mọi quyết định thiết kế phải phục vụ chúng

1. **Lợi nhuận thật** — bóc tách phí TikTok đến từng dòng, đối soát ngược với payout
2. **Affiliate insight** — lợi nhuận tách kênh affiliate vs tự nhiên (mức creator chờ kết luận ToS §3.8)
3. **Dữ liệu tại Việt Nam** — hạ tầng trong nước

---

# 2. QUYẾT ĐỊNH KIẾN TRÚC BẮT BUỘC

> Sáu quyết định dưới đây nếu làm sai ở Phase 1 sẽ phải viết lại phần lớn hệ thống ở Phase 2. Không được đơn giản hoá để ship nhanh.

## 2.1 Connector-based — mọi sàn là adapter

Core không được biết TikTok là gì. Định nghĩa interface trước, TikTok là implementation đầu tiên:

```
ChannelAdapter
  ├── AuthProvider        : authorize, refresh, revoke, getScopes
  ├── ProductProvider     : listProducts, getProduct
  ├── OrderProvider       : listOrders, getOrder, listPackages
  ├── FulfillmentProvider : createPackage, shipPackage, batchShip, getLabel
  ├── InventoryProvider   : updateStock, batchUpdateStock
  └── FinanceProvider     : listSettlements, getOrderSettlement
```

Quy tắc: **không một dòng logic nghiệp vụ nào được import trực tiếp SDK/HTTP client của TikTok.** Toàn bộ đi qua adapter.

## 2.2 Uỷ quyền một chiều từ gian hàng — mô hình đã sửa 07/08/2026

> **Bản trước của mục này mô tả sai.** Nó dựng mô hình "3 role uỷ quyền" (seller / creator / partner) và kèm một ma trận capability nói rằng dữ liệu creator chỉ lấy được khi có tư cách TAP. Cả hai đều không đúng với tài liệu chính thức của TikTok Shop. Nội dung dưới đây được viết lại sau khi đọc trực tiếp `partner.tiktokshop.com` ngày 07/08/2026.

### ViShop là ai trong hệ Partner Center

Partner Center có sáu loại đối tác: Developer, TikTok Shop Partner (TSP), Creator Agency Partner (CAP), Non-US TikTok Shop Affiliate Partner (TAP), US TAP, Multi-Channel Network (MCN). Năm loại sau là **ngành dịch vụ** — TSP nhận vận hành thuê, CAP/MCN quản lý creator, TAP môi giới creator cho seller và ăn hoa hồng trên doanh số creator. Không loại nào là "gói nâng cấp quyền đọc dữ liệu".

ViShop thuộc loại **Developer**, phân nhánh **App Developer**, phát hành **Public App**. Đăng ký tại `partner.tiktokshop.com/account/sign-up`, thị trường VN.

Hệ quả trực tiếp: **không có khái niệm chọn vai trò khi kết nối.** Seller mở trang uỷ quyền của TikTok, đồng ý cấp quyền cho app ViShop, TikTok trả về authorization code. Một gian hàng — một uỷ quyền.

```
tenant
 └── shop_authorization (n)   ← mỗi gian hàng một bản ghi
      ├── shop_id, shop_cipher
      ├── granted_scopes[]    ← TikTok trả về khi đổi authorization code
      ├── capabilities[]      ← suy ra từ granted_scopes, cache
      └── access_token, refresh_token, expires_at

feature_gate(feature) = feature.required_scopes ⊆ shop.granted_scopes
```

Feature gating vẫn giữ, nhưng gate theo **scope đã cấp**, không gate theo role. Lý do thật để một màn không có dữ liệu là gian hàng uỷ quyền từ bản app cũ chưa có scope đó, và cách xử lý là yêu cầu seller uỷ quyền lại.

### Ma trận capability theo scope

Nguồn: `GET /api/v1/document/api_meta` của chính trang tài liệu TikTok, đọc ngày 07/08/2026. Đây là schema đầy đủ, không phải phần mô tả tóm tắt.

**Sửa 17/08/2026 (FR-02-12):** cột scope dưới đây trước ghi `seller.product.info` và
`seller.fulfillment` — hai tên này không khớp danh sách scope thật ViShop đã đăng ký (đối chiếu
trực tiếp với Partner Center, xem `vishop-tiktok-integration-runbook.md`). Đã sửa thành tên đúng:
`seller.product.basic`, và tách `seller.fulfillment` thành 2 scope riêng
`seller.order.info`/`seller.fulfillment.basic`. Đây là lỗi đánh máy khi soạn bảng, không phải đổi
quyết định nghiệp vụ — `TIKTOK_CAPABILITY_CATALOG` (`libs/infra/tiktok`) dùng đúng tên đã sửa.

| Dữ liệu / hành động             | Scope cần có                                    | Trạng thái                                                                                                                                                       |
| ------------------------------- | ----------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Sản phẩm, tồn kho, cập nhật tồn | `seller.product.basic`                          | ✅                                                                                                                                                               |
| Đơn, package, RTS, tem          | `seller.order.info`, `seller.fulfillment.basic` | ✅                                                                                                                                                               |
| Finance / settlement            | `seller.finance.info`                           | ✅ _(sau payout)_                                                                                                                                                |
| Danh sách đơn affiliate         | `seller.affiliate_collaboration.read`           | ✅                                                                                                                                                               |
| Creator gắn với đơn             | `seller.affiliate_collaboration.read`           | ✅ `skus[].creator_username`                                                                                                                                     |
| Tỉ lệ hoa hồng theo creator     | `seller.affiliate_collaboration.read`           | ✅ `skus[].commission_rate`                                                                                                                                      |
| Hoa hồng ước tính và thực trả   | `seller.affiliate_collaboration.read`           | ✅ `estimated_commission_base`, `actual_paid_commission`                                                                                                         |
| Nguồn đơn (SHOP/VIDEO/LIVE/…)   | `seller.affiliate_collaboration.read`           | ✅ `skus[].content_type` — theo dòng SKU, không phải cấp đơn                                                                                                     |
| Hoàn/huỷ theo SKU-order         | `seller.affiliate_collaboration.read`           | ⚠️ `fully_return` (Yes/No) và `settlement_status` dùng được; `refunded_quantity`/`returned_quantity` đã bị sàn khai tử — hoàn một phần phải suy ra, xem BR-11-02 |
| LIVE performance                | `data.shop_analytics.public.read`               | ⚠️ chỉ official account của gian hàng                                                                                                                            |
| Danh sách đơn theo phiên live   | —                                               | ✗ **không endpoint nào có**                                                                                                                                      |

Chi tiết trường và nguồn xác minh: [vishop-capability-matrix.md](./vishop-capability-matrix.md).

**Điểm cần nhớ:** toàn bộ dữ liệu creator mà ViShop cần đều nằm trong phạm vi gian hàng tự cấp. TAP không liên quan.

### Rào cản còn lại của nhóm phân tích creator

Rào cản không phải kỹ thuật mà là **ToS Partner Center §3.8(e)/(f)/(a)** về việc lưu trữ và dùng lại dữ liệu creator. Xem R10 ở §10. Chưa có kết luận từ Legal, và **có khả năng không được chấp nhận**. Do đó nhóm M11-B nằm ngoài cam kết Phase 1 và không được xây sâu trước khi Legal trả lời.

**Yêu cầu UX bắt buộc:** tính năng thiếu scope hiển thị ở trạng thái **khoá kèm lý do và hành động** ("Gian hàng chưa cấp quyền Affiliate — uỷ quyền lại để xem dữ liệu creator"). **Không ẩn đi. Không hiển thị số 0 hoặc bảng rỗng thay cho dữ liệu bị khoá.**

## 2.3 Package là đơn vị fulfillment, không phải Order

TikTok Shop không có endpoint "confirm order". Luồng thật: **order → package → Ship Package (RTS) → label**. Một order có thể tách nhiều package.

- Mọi thao tác fulfillment (RTS, lấy tem, in, quét, bàn giao) ở **cấp package**
- **Trừ tồn neo theo package**, không theo order
- Giao diện mặc định gộp theo order, bung package khi cần
- Phân biệt **TikTok Shipping** vs **Seller Shipping** ở cấp package — luồng tem khác hoàn toàn

## 2.4 Chi phí là line-item động

Không được tạo cột cố định cho từng loại phí. Mỗi phí là một bản ghi:

```
order_fee
  ├── order_id, package_id (nullable), order_line_id (nullable)
  ├── fee_type       (enum mở rộng được)
  ├── amount         (số nguyên, đơn vị đồng)
  ├── source         : estimated | settlement
  └── currency, created_at
```

Lý do: mỗi sàn có cấu trúc phí khác nhau; cột cứng sẽ chết ở sàn thứ hai. Và cùng một đơn có hai bộ phí — ước tính và thực tế sau payout — phải cùng tồn tại.

## 2.5 Event log bất biến

Mọi biến động tồn kho, chuyển trạng thái đơn/package, thay đổi giá vốn ghi bản ghi **append-only**:

```
event_log: entity_type, entity_id, event_type, payload_before, payload_after,
           actor_type (user|system|platform), actor_id, source, created_at
```

Không sửa, không xoá. Đây là nền tảng để debug lệch tồn, đối soát, và audit. Thiếu nó thì không có cách nào truy được vì sao tồn sai.

## 2.6 Idempotency + Queue + Rate Governor

- **Idempotency:** mọi bản ghi từ sàn khoá theo `(platform, shop_id, external_id)`. Re-sync không tạo trùng.
- **Queue:** mọi lệnh gọi API sàn đi qua hàng đợi, retry backoff luỹ thừa, tối đa 5 lần, có dead-letter queue.
- **Rate Governor:** quản lý hạn mức API **theo từng shop**. Ưu tiên: đồng bộ đơn > đẩy tồn > lấy tem > báo cáo. Không để việc in tem làm cạn hạn mức của đồng bộ đơn.

---

# 3. MÔ HÌNH DỮ LIỆU LÕI

## 3.1 Thực thể chính

```
user ──< tenant_member >── tenant ─┬─ role ── role_permission
                                   ├─ authorization (một bản ghi mỗi gian hàng: shop_id, shop_cipher, granted_scopes, token)
                                   ├─ shop (sinh từ Get Authorized Shops)
                                   ├─ merchant_sku ─┬─ sku_mapping ── platform_sku
                                   │                ├─ combo_component (self-ref)
                                   │                └─ inventory (warehouse_id, on_hand, locked, available)
                                   ├─ order ─┬─ order_line
                                   │         ├─ package ── package_line
                                   │         ├─ order_fee (line-item động)
                                   │         └─ settlement
                                   ├─ stock_movement (append-only)
                                   ├─ inventory_push_log
                                   ├─ event_log (append-only)
                                   └─ audit_log (append-only)
```

## 3.2 Ràng buộc bắt buộc

| Bảng              | Ràng buộc                                                                             |
| ----------------- | ------------------------------------------------------------------------------------- |
| Mọi bảng          | Có `tenant_id`, index đầu tiên. **Không truy vấn nào được thiếu điều kiện tenant_id** |
| `merchant_sku`    | `(tenant_id, sku_code)` unique                                                        |
| `order`           | `(platform, shop_id, external_order_id)` unique                                       |
| `package`         | `(platform, shop_id, external_package_id)` unique                                     |
| `inventory`       | Có `warehouse_id` ngay từ Phase 1 (dù chỉ 1 kho); `available >= 0` luôn đúng          |
| Tiền tệ           | Số nguyên đơn vị đồng. **Không dùng float ở bất kỳ đâu**                              |
| Thời gian         | Lưu UTC, hiển thị UTC+7                                                               |
| Bản ghi giao dịch | **Không xoá cứng**. Chỉ đánh dấu huỷ/vô hiệu                                          |

## 3.3 Trường dành sẵn cho Phase 2 (tạo ngay, chưa dùng)

`inventory.warehouse_id` · `inventory.reserved` (dự trữ campaign) · `order.parent_order_id` (tách đơn) · `merchant_sku.batch_enabled`, `serial_enabled` · `order.channel` (đa sàn)

---

# 4. MÁY TRẠNG THÁI

## 4.1 Order (đơn vị thương mại)

```
Chưa thanh toán → Đã thanh toán → Đang xử lý → Đang giao → Hoàn thành
                                       ↓            ↓           ↓
                                     Huỷ         Huỷ         Hoàn
```

## 4.2 Package (đơn vị fulfillment — ViShop vận hành ở tầng này)

```
Chờ tạo package
  → Chờ RTS
    → Đã RTS / có mã vận đơn
      → Chờ in tem
        → Đã in, chờ bàn giao
          → Đã bàn giao vận chuyển
            → Đang giao → Hoàn thành

Nhánh phụ: Thiếu tồn · Chưa ghép nối SKU · Tạm giữ · Bất thường
```

**Quy tắc:** trạng thái trong ViShop **luôn lấy sàn làm chuẩn**. Lệch thì đồng bộ lại theo sàn và ghi vào danh sách Bất thường.

## 4.3 Tồn kho

```
Tồn tổng (on_hand) = Khả dụng (available) + Đã khoá (locked)
Đang về (incoming) — nằm ngoài Tồn tổng, chỉ tham khảo
```

| Sự kiện                                          | Chuyển trạng thái                                    |
| ------------------------------------------------ | ---------------------------------------------------- |
| Đơn về (theo cấu hình: khi đặt / khi thanh toán) | available → locked                                   |
| **Package RTS**                                  | locked → trừ khỏi on_hand, sinh phiếu xuất           |
| Đơn huỷ khi chưa RTS                             | locked → available                                   |
| Đơn huỷ/hoàn sau khi đã RTS                      | **Không tự cộng lại** — cần nhập kho hàng hoàn (M09) |
| Nhập kho                                         | on_hand ↑, available ↑                               |
| Kiểm kho lệch                                    | Điều chỉnh on_hand, ghi lý do                        |

### 4.3.1 Snapshot sổ biến động tồn

Mỗi dòng sổ biến động lưu snapshot trước/sau của `onHand`, `available`, `locked` và `reserved`, cùng `quantity` là độ lớn biến động (> 0). Giá trị `Before = After` nghĩa là bucket đó không đổi; không được bỏ trống, để từng dòng vẫn đối soát độc lập theo SKU, kho, chứng từ hoặc thời gian.

| Nghiệp vụ                    | `quantity` | `onHandBefore/After` | `availableBefore/After` | `lockedBefore/After` | `reservedBefore/After` |
| ---------------------------- | ---------- | -------------------- | ----------------------- | -------------------- | ---------------------- |
| Nhập kho                     | Có         | Có                   | Có                      | Không đổi            | Không đổi              |
| Xuất kho thủ công            | Có         | Có                   | Có                      | Không đổi            | Không đổi              |
| Kiểm kho lệch                | Có         | Có                   | Có                      | Có thể không đổi     | Có thể không đổi       |
| Đơn về / khóa hàng           | Có         | Không đổi            | Có                      | Có                   | Không đổi              |
| RTS / trừ tồn                | Có         | Có                   | Thường không đổi        | Có                   | Không đổi              |
| Hủy đơn trước RTS / nhả khóa | Có         | Không đổi            | Có                      | Có                   | Không đổi              |
| Nhập hàng hoàn thực tế M09   | Có         | Có                   | Có                      | Không đổi            | Không đổi              |
| Giữ tồn chiến dịch           | Có         | Không đổi            | Có                      | Không đổi            | Có                     |
| Nhả tồn chiến dịch           | Có         | Không đổi            | Có                      | Không đổi            | Có                     |

---

# 5. MODULE PHASE 1

Ký hiệu: **P0** không có thì không ship · **P1** fast follow · **P2** chỉ thiết kế schema · **[chờ Legal]** chờ kết luận ToS §3.8 trước khi xây sâu

---

## M00 — Nền tảng hệ thống

| ID       | Ưu tiên | Yêu cầu                                                               |
| -------- | ------- | --------------------------------------------------------------------- |
| FR-00-01 | P0      | Event log bất biến theo §2.5                                          |
| FR-00-02 | P0      | Job queue có retry backoff luỹ thừa, tối đa 5 lần, dead-letter queue  |
| FR-00-03 | P0      | Idempotency theo `(platform, shop_id, external_id)`                   |
| FR-00-04 | P0      | Audit log mọi thao tác ghi: user, hành động, đối tượng, IP, thời gian |
| FR-00-05 | P0      | Rate limit governor theo shop, có thứ tự ưu tiên luồng (§2.6)         |
| FR-00-06 | P0      | Multi-tenant isolation — mọi truy vấn bắt buộc `tenant_id`            |
| FR-00-07 | P1      | Feature flag theo tenant phục vụ beta                                 |
| FR-00-08 | P2      | Kiến trúc sẵn cho webhook ra ngoài và public API                      |

**BR-00-01** Không xoá cứng bản ghi giao dịch · **BR-00-02** Thời gian UTC, hiển thị UTC+7 · **BR-00-03** Tiền là số nguyên đồng

---

## M01 — Tài khoản, Tổ chức & Phân quyền

| ID       | Ưu tiên | Yêu cầu                                                                             |
| -------- | ------- | ----------------------------------------------------------------------------------- |
| FR-01-01 | P0      | Đăng ký/đăng nhập email + mật khẩu, xác thực email                                  |
| FR-01-02 | P0      | Quan hệ User ↔ Tenant qua TenantMember; Role được gán theo membership               |
| FR-01-03 | P0      | 4 vai trò dựng sẵn: Chủ tài khoản · Vận hành · Kho · Kế toán                        |
| FR-01-04 | P0      | Vai trò tuỳ chỉnh: chọn quyền từ danh mục quyền, nhóm theo module                   |
| FR-01-05 | P0      | Data scope theo gian hàng                                                           |
| FR-01-06 | P0      | Quyền cấp trường độc lập: `Xem giá vốn`, `Xem lợi nhuận`, `Xem thông tin người mua` |
| FR-01-07 | P0      | Chủ tài khoản đặt lại mật khẩu cho tài khoản phụ                                    |
| FR-01-08 | P0      | Vô hiệu hoá tài khoản (không xoá), giữ lịch sử                                      |
| FR-01-09 | P1      | Nhóm gian hàng — gom shop theo khách hàng/thương hiệu                               |
| FR-01-10 | P1      | 2FA qua TOTP                                                                        |
| FR-01-11 | P1      | Lịch sử đăng nhập & thiết bị                                                        |
| FR-01-12 | P0      | Liệt kê các tổ chức mà user đang đăng nhập là thành viên                            |
| FR-01-13 | P0      | Chọn tổ chức hiện hành và duy trì lựa chọn qua các request                          |
| FR-01-14 | P0      | Tạo thêm tổ chức                                                                    |

Một user có thể thuộc nhiều tenant, và một tenant có nhiều user. Quan hệ N–N được lưu bằng `TenantMember`, duy nhất theo cặp `(tenantId, userId)`; role và quyền theo tenant gắn với membership, không gắn trực tiếp lên `User`. Trong giao diện seller-facing, tenant được gọi là **tổ chức** và chứa nhiều gian hàng.

**Quyết định contract 14/08/2026 (FR-01-13):** lựa chọn tổ chức của browser được lưu trong server-side session và dùng lại cho các request sau. `X-Tenant-Id` được giữ làm override cho caller machine-to-machine, không phải cơ chế chính của SPA.

**Quyết định sản phẩm 14/08/2026 (FR-01-14):** mọi user đã đăng nhập được tạo thêm tổ chức và trở thành `OWNER` của tổ chức đó. Tạo mới không chuyển hay sao chép gian hàng, thành viên hoặc dữ liệu từ tổ chức đang dùng.

**BR-01-01** Số tài khoản phụ giới hạn theo gói; vượt thì chặn tạo, không tự tính thêm tiền
**BR-01-02** Chủ tài khoản không tự hạ quyền chính mình
**BR-01-03** User bị vô hiệu → huỷ mọi phiên trong ≤60 giây

**AC**

- [ ] User vai trò Vận hành gán Shop A → chỉ thấy đơn Shop A, không có tuỳ chọn lọc sang shop khác
- [ ] User không có quyền `Xem giá vốn` → UI hiện `***` **và API response cũng không chứa giá trị thật**
- [ ] User có nhiều membership và session chưa có active tenant → phải chọn tổ chức trước khi vào màn tenant-scoped
- [ ] Switch tổ chức → request tiếp theo dùng tenant mới; không hiển thị dữ liệu cache của tenant cũ
- [ ] User tạo thêm tổ chức → có membership `OWNER`; tổ chức mới không chứa shop hoặc dữ liệu của tổ chức cũ
- [ ] Vô hiệu hoá user B → B bị đăng xuất trong ≤60s kèm thông báo

---

## M02 — Kết nối gian hàng & Authorization

| ID           | Ưu tiên | Yêu cầu                                                                                                                                                                  |
| ------------ | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| FR-02-01     | P0      | Kết nối TikTok Shop VN qua OAuth Open API chính thức — seller uỷ quyền cho Public App của ViShop, `user_type = 0`                                                        |
| FR-02-02     | P0      | Đặt nickname gian hàng, unique trong tenant                                                                                                                              |
| FR-02-03     | P0      | Danh sách gian hàng: trạng thái, hạn token, lần sync cuối, số đơn/tồn đã sync                                                                                            |
| FR-02-04     | P0      | Đồng bộ khởi tạo: sản phẩm + đơn 30 ngày gần nhất, có thanh tiến độ                                                                                                      |
| FR-02-05     | P0      | Tự gia hạn token; thất bại → trạng thái "Cần kết nối lại" + cảnh báo                                                                                                     |
| FR-02-06     | P0      | Cảnh báo hết hạn ủy quyền trước 7 ngày, nhắc lại ở mốc 3 và 1 ngày                                                                                                       |
| FR-02-07     | P0      | Kết nối lại không mất dữ liệu lịch sử; kéo bù đơn phát sinh trong lúc gián đoạn                                                                                          |
| FR-02-08     | P0      | Nhật ký đồng bộ: loại dữ liệu, thời điểm, số bản ghi, số lỗi, lý do                                                                                                      |
| FR-02-09     | P0      | Ngắt kết nối — giữ dữ liệu lịch sử, dừng sync và đẩy tồn                                                                                                                 |
| **FR-02-12** | **P0**  | **Feature gating theo scope đã cấp (§2.2)** — lưu `granted_scopes` khi đổi authorization code, suy ra capability set, khoá màn thiếu quyền kèm lý do và nút uỷ quyền lại |
| **FR-02-13** | **P0**  | **Màn hình kết nối 3 loại riêng biệt**, giải thích mỗi loại mở khoá tính năng gì                                                                                         |
| FR-02-10     | P1      | Đồng bộ thủ công theo loại dữ liệu                                                                                                                                       |
| FR-02-11     | P1      | Kiểm tra sức khoẻ kết nối định kỳ, đèn xanh/vàng/đỏ                                                                                                                      |

**BR-02-01** Một shop TikTok chỉ kết nối vào **một tenant** tại một thời điểm; đã ở tenant khác → báo lỗi rõ, không cho đè
**BR-02-02** Số gian hàng giới hạn theo gói
**BR-02-03** Shop trạng thái "Cần kết nối lại" → **dừng đẩy tồn** cho shop đó, giữ nguyên dữ liệu
**BR-02-04** **Tuyệt đối không scraping, không mô phỏng đăng nhập, không lưu cookie phiên người dùng**

**AC**

- [ ] Hoàn tất OAuth → shop hiện trạng thái "Đang đồng bộ khởi tạo" + thanh tiến độ
- [ ] Shop ≤10.000 đơn → đơn 30 ngày về hết trong ≤10 phút
- [ ] Token còn 7 ngày → cảnh báo in-app + email; lặp ở mốc 3 và 1
- [ ] Kết nối lại → đơn phát sinh lúc gián đoạn được kéo bù đủ, không trùng
- [ ] Shop đã ở tenant khác → lỗi rõ ràng kèm hướng xử lý
- [ ] Tính năng thiếu capability → hiện trạng thái khoá + lý do + hành động, không ẩn, không hiện bảng rỗng

---

## M03 — Sản phẩm & SKU hàng hoá

**Sản phẩm sàn (chỉ đọc)**

| ID       | Ưu tiên | Yêu cầu                                                                     |
| -------- | ------- | --------------------------------------------------------------------------- |
| FR-03-01 | P0      | Đồng bộ sản phẩm + biến thể từ TikTok Shop (một chiều)                      |
| FR-03-02 | P0      | Danh sách: ảnh, tên, SKU sàn, biến thể, giá, tồn trên sàn, trạng thái, shop |
| FR-03-03 | P0      | Lọc theo shop, trạng thái, đã/chưa ghép nối                                 |
| FR-03-04 | P1      | Đồng bộ lại theo yêu cầu                                                    |

**Merchant SKU**

| ID       | Ưu tiên | Yêu cầu                                                          |
| -------- | ------- | ---------------------------------------------------------------- |
| FR-03-05 | P0      | Tạo/sửa SKU kho: mã (unique/tenant), tên, ảnh, danh mục, ghi chú |
| FR-03-06 | P0      | Giá vốn bình quân gia quyền động, cập nhật khi nhập kho          |
| FR-03-07 | P0      | GTIN — tối đa 10 mã/SKU                                          |
| FR-03-08 | P0      | Cân nặng, kích thước D×R×C                                       |
| FR-03-09 | P0      | Ngưỡng cảnh báo tồn theo SKU                                     |
| FR-03-10 | P0      | Trạng thái: Đang bán / Ngừng bán                                 |
| FR-03-11 | P0      | SKU combo — nhiều SKU con + số lượng, không có tồn vật lý riêng  |
| FR-03-12 | P0      | Import/Export Excel, báo lỗi theo dòng                           |
| FR-03-13 | P0      | Cập nhật hàng loạt: giá vốn, danh mục, trạng thái, ngưỡng        |
| FR-03-14 | P1      | Danh mục SKU nhiều cấp                                           |
| FR-03-15 | P1      | Lịch sử thay đổi giá vốn                                         |
| FR-03-16 | P2      | Đa đơn vị tính                                                   |

**BR-03-01** `Giá vốn mới = (Tồn hiện tại × Giá vốn cũ + SL nhập × Đơn giá nhập) ÷ (Tồn hiện tại + SL nhập)`. Chỉ tính lại khi **nhập** kho; xuất kho không đổi giá vốn.
**BR-03-02** Tồn khả dụng combo = `MIN(available SKU con ÷ số lượng trong combo)`
**BR-03-03** Giá vốn combo = tổng giá vốn SKU con theo số lượng, tại thời điểm xuất kho
**BR-03-04** **Không xoá SKU** khi: tồn ≠ 0 · đang có ghép nối · là thành phần combo · đã phát sinh giao dịch. Chỉ cho "Ngừng bán"
**BR-03-05** Mã SKU không sửa được sau khi đã phát sinh giao dịch
**BR-03-06** SKU "Ngừng bán" không vào cảnh báo tồn thấp

**AC**

- [ ] SKU A tồn 100 giá vốn 50.000đ, nhập 50 cái giá 60.000đ → giá vốn mới 53.333đ
- [ ] Combo C = 2×A + 1×B; A available 10, B available 3 → tồn khả dụng C = 3
- [ ] Xoá SKU đang có ghép nối → chặn, liệt kê ghép nối đang tồn tại
- [ ] Import 1.000 dòng có 5 lỗi → 995 dòng vào, file lỗi ghi rõ dòng nào sai vì sao

---

## M04 — Ghép nối SKU

> Đây là điểm nghẽn onboarding lớn nhất. Trải nghiệm phải cực nhẹ.

| ID       | Ưu tiên | Yêu cầu                                                                  |
| -------- | ------- | ------------------------------------------------------------------------ |
| FR-04-01 | P0      | Trang ghép nối 2 cột, lọc theo trạng thái/shop/sản phẩm                  |
| FR-04-02 | P0      | Gợi ý tự động theo thứ tự: mã SKU trùng → mã vạch trùng → tên tương đồng |
| FR-04-03 | P0      | Chấp nhận gợi ý hàng loạt / từng dòng                                    |
| FR-04-04 | P0      | Ghép nối thủ công theo biến thể                                          |
| FR-04-05 | P0      | Tự tạo SKU kho từ SKU sàn, hàng loạt                                     |
| FR-04-06 | P0      | Một SKU sàn ↔ một hoặc nhiều SKU kho theo số lượng (listing dạng combo)  |
| FR-04-07 | P0      | Import/Export ghép nối qua Excel                                         |
| FR-04-08 | P0      | Sửa/gỡ ghép nối, cảnh báo đơn đang bị ảnh hưởng                          |
| FR-04-09 | P0      | Đếm SKU chưa ghép nối hiển thị thường trực trên dashboard                |
| FR-04-10 | P1      | Quy tắc ghép nối tự động cho listing mới                                 |

**BR-04-01** Đơn có SKU chưa ghép nối → **không trừ tồn**, vào danh sách "Cần ghép nối", **nhưng vẫn RTS và in tem được**. Không chặn dòng tiền của khách.
**BR-04-02** Không cho bật đẩy tồn cho shop có tỉ lệ ghép nối < 100% trừ khi người dùng xác nhận rõ rủi ro
**BR-04-03** Sửa ghép nối chỉ áp dụng từ thời điểm sửa; có tuỳ chọn áp ngược cho đơn chưa giao
**BR-04-04** Không cho hai SKU sàn cùng shop ghép trùng theo cách gây mâu thuẫn đẩy tồn

**AC**

- [ ] 500 listing có mã SKU trùng mã kho → ≥90% được gợi ý đúng, chấp nhận hàng loạt trong 1 thao tác
- [ ] Listing chưa ghép nối có đơn về → đơn vẫn RTS và in tem được, gắn cờ "Chưa ghép nối", không trừ tồn
- [ ] Gỡ ghép nối SKU đang có 5 package chờ xử lý → cảnh báo cụ thể 5 package đó trước khi cho phép

---

## M05 — Tồn kho

**Quản lý tồn**

| ID       | Ưu tiên     | Yêu cầu                                                                                                                                              |
| -------- | ----------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| FR-05-01 | P0          | Bảng tồn theo SKU: on_hand, available, locked, incoming, giá vốn, giá trị tồn                                                                        |
| FR-05-02 | P0          | Nhập kho thủ công: SKU, SL, đơn giá, lý do (Nhập mua/Trả hàng/Điều chỉnh/Khác), ghi chú                                                              |
| FR-05-03 | P0          | Xuất kho thủ công: SL, lý do (Hỏng/Mất/Biếu tặng/Điều chỉnh/Khác)                                                                                    |
| FR-05-04 | P0          | Kiểm kho: tạo phiếu, nhập số đếm thực, tính lệch, xác nhận điều chỉnh                                                                                |
| FR-05-05 | P0          | Sổ biến động tồn: thời gian, SKU, loại nghiệp vụ, số lượng trước/sau, chứng từ, người thao tác                                                       |
| FR-05-06 | P0          | Lọc sổ biến động + export Excel                                                                                                                      |
| FR-05-07 | P0          | Import nhập kho hàng loạt qua Excel                                                                                                                  |
| FR-05-08 | P1          | Kiểm kho theo nhóm SKU/danh mục                                                                                                                      |
| FR-05-09 | ~~P2~~ → P0 | Đa kho — `warehouse_id` đã kích hoạt, tồn khoá theo cặp (`merchant_sku_id`, `warehouse_id`). Nâng lên P0 ngày 07/08/2026, xem nhóm FR-05-20…FR-05-27 |
| FR-05-10 | P2          | Dự trữ chương trình — schema có trạng thái `reserved`                                                                                                |

**Đẩy tồn**

| ID       | Ưu tiên | Yêu cầu                                                                         |
| -------- | ------- | ------------------------------------------------------------------------------- |
| FR-05-11 | P0      | Bật/tắt đẩy tồn theo từng gian hàng                                             |
| FR-05-12 | P0      | Tỉ lệ đẩy (%) cấu hình theo shop                                                |
| FR-05-13 | P0      | Tồn an toàn cấu hình theo shop hoặc theo SKU                                    |
| FR-05-14 | P0      | Đẩy tự động khi có bất kỳ biến động nào                                         |
| FR-05-15 | P0      | Nhật ký đẩy tồn: SKU, shop, giá trị đẩy, kết quả, lý do lỗi, thời gian          |
| FR-05-16 | P0      | Đẩy thủ công (một SKU / hàng loạt / toàn shop)                                  |
| FR-05-17 | P0      | Tự retry khi thất bại; cảnh báo nếu fail > 3 lần liên tiếp                      |
| FR-05-18 | P1      | Gian hàng ưu tiên — tồn dưới ngưỡng thì shop ưu tiên giữ nguyên, shop khác về 0 |
| FR-05-19 | P1      | Xem trước tác động trước khi bật đẩy tồn lần đầu                                |

**BR-05-01** `SL đẩy = FLOOR((available − tồn_an_toàn) × tỉ_lệ_đẩy)`, tối thiểu 0
**BR-05-02** SKU chưa ghép nối không tham gia đẩy tồn
**BR-05-03** Combo đẩy theo `MIN(available SKU con ÷ SL)`; SKU con biến động → **mọi combo chứa nó phải được đẩy lại**
**BR-05-04** Đẩy tồn **một chiều** (ViShop → sàn). Sửa tồn trên Seller Center sẽ bị ghi đè. Phải cảnh báo rõ khi bật tính năng
**BR-05-05** `available` không bao giờ âm. Đơn về khi đã hết hàng → vào danh sách "Thiếu tồn", giữ available = 0
**BR-05-06** Thời điểm khoá tồn cấu hình cấp tenant: khi đơn tạo (mặc định) hoặc khi thanh toán

**AC**

- [ ] SKU A available 10, ghép nối 3 shop, tỉ lệ 100%, an toàn 0; đơn 2 cái về shop 1 → trong ≤30s cả 3 shop hiện 8
- [ ] Tỉ lệ 80%, an toàn 5, available 100 → đẩy `FLOOR((100−5)×0.8) = 76`
- [ ] available về 0 → mọi listing ghép nối về 0 trong ≤30s
- [ ] Đẩy shop A fail 3 lần → cảnh báo + đánh dấu shop cần kiểm tra, **không âm thầm bỏ qua**
- [ ] Đơn huỷ khi chưa RTS → locked trả về available + kích hoạt đẩy tồn
- [ ] Kiểm kho lệch −3 → on_hand giảm 3, sổ biến động ghi "Điều chỉnh kiểm kho" + người thao tác

---

## M06 — Đơn hàng & Package

**Danh sách & tìm kiếm**

| ID       | Ưu tiên | Yêu cầu                                                                                               |
| -------- | ------- | ----------------------------------------------------------------------------------------------------- |
| FR-06-01 | P0      | Danh sách hợp nhất đa gian hàng, tab theo trạng thái, badge đếm                                       |
| FR-06-02 | P0      | Lọc: shop, trạng thái, thời gian, SKU, mã đơn, mã vận đơn, người mua, cờ, nguồn đơn                   |
| FR-06-03 | P0      | Tìm nhanh theo mã đơn / mã vận đơn / SĐT / tên người nhận                                             |
| FR-06-04 | P0      | Bộ lọc lưu được, đặt tên, ghim                                                                        |
| FR-06-05 | P0      | Tuỳ chỉnh cột, đổi thứ tự, ghim cột trái                                                              |
| FR-06-06 | P0      | Export theo bộ lọc hiện tại                                                                           |
| FR-06-26 | P0      | **Hiển thị 2 tầng**: mặc định gộp theo order, bung package; đơn nhiều package hiện rõ quan hệ cha–con |
| FR-06-27 | P0      | **Phân biệt TikTok Shipping vs Seller Shipping** ở cấp package                                        |
| FR-06-07 | P1      | Chế độ xem gọn / chi tiết                                                                             |

**Chi tiết đơn**

| ID       | Ưu tiên | Yêu cầu                                                                                             |
| -------- | ------- | --------------------------------------------------------------------------------------------------- |
| FR-06-08 | P0      | Mã đơn sàn, shop, thời gian đặt/thanh toán, trạng thái, người nhận, địa chỉ, phương thức thanh toán |
| FR-06-09 | P0      | Dòng SP: ảnh, tên, biến thể, SKU sàn, SKU kho, SL, đơn giá, chiết khấu                              |
| FR-06-10 | P0      | Khối tài chính: doanh thu, dòng phí, giá vốn, lợi nhuận (theo quyền)                                |
| FR-06-11 | P0      | Nguồn đơn: Affiliate / Livestream / Video / Shop tab / Tìm kiếm                                     |
| FR-06-12 | P0      | Lịch sử thao tác trên đơn                                                                           |
| FR-06-13 | P0      | Ghi chú 2 loại: nhặt hàng (in ra phiếu) và nội bộ (không in)                                        |
| FR-06-14 | P0      | Tra cứu hành trình vận chuyển                                                                       |

**Xử lý — cấp package**

| ID        | Ưu tiên | Yêu cầu                                                                                  |
| --------- | ------- | ---------------------------------------------------------------------------------------- |
| FR-06-15a | P0      | **Tạo package** cho đơn (một hoặc tách nhiều theo cấu hình)                              |
| FR-06-15b | P0      | **Ship Package hàng loạt (Batch RTS)** — gửi theo lô, xử lý kết quả từng package độc lập |
| FR-06-15c | P0      | Hàng đợi RTS retry riêng từng package; package lỗi không chặn lô                         |
| FR-06-16  | P0      | Lấy mã vận đơn **theo package**                                                          |
| FR-06-17  | P0      | Đánh dấu đã bàn giao hàng loạt                                                           |
| FR-06-18  | P0      | Tạm giữ / bỏ tạm giữ — không vào luồng xử lý, tồn vẫn locked                             |
| FR-06-19  | P0      | Cờ đánh dấu nhiều màu, đặt tên tuỳ ý, gắn/gỡ hàng loạt                                   |
| FR-06-20  | P0      | Danh sách **Thiếu tồn** kèm SKU và số lượng thiếu                                        |
| FR-06-21  | P0      | Danh sách **Bất thường** — lỗi sync, lệch dữ liệu, thiếu thông tin                       |
| FR-06-22  | P0      | Sửa số lượng phân bổ tồn thủ công                                                        |
| FR-06-23  | P1      | Quy tắc tự động (tự RTS / tự gắn cờ theo điều kiện)                                      |
| FR-06-24  | P1      | Chặn xử lý theo điều kiện (giá trị bất thường, SKU nhạy cảm)                             |
| FR-06-25  | P2      | Tách đơn — schema `parent_order_id` sẵn từ Phase 1                                       |

**BR-06-01** Trạng thái luôn theo sàn làm chuẩn; lệch → sync lại + ghi Bất thường
**BR-06-02** Đơn "Chưa thanh toán" không trừ tồn, không vào báo cáo doanh thu
**BR-06-03** Package thiếu tồn **vẫn RTS và in tem được**, ghi nhận tồn âm ảo + cảnh báo. Không chặn vận hành
**BR-06-04** Package tạm giữ vẫn giữ tồn ở locked
**BR-06-05** Không sửa dữ liệu gốc từ sàn (giá, sản phẩm, người mua). Chỉ thêm dữ liệu nội bộ
**BR-06-06** Mọi thao tác fulfillment ở **cấp package**, không có thao tác fulfillment cấp order
**BR-06-07** **Trừ tồn neo theo package** — order tách nhiều package thì trừ theo từng package khi package đó RTS
**BR-06-08** Package Seller Shipping không có tem từ TikTok — nhập mã vận đơn thủ công (Phase 1) hoặc qua 3PL (Phase 2)

**AC**

- [ ] 300 package Chờ RTS thuộc 5 shop → Batch Ship theo lô, có tiến độ, bảng kết quả tách thành công/thất bại kèm **mã lỗi gốc từ sàn**
- [ ] Order tách 2 package, package 1 RTS → chỉ trừ tồn dòng hàng thuộc package 1; order vẫn Đang xử lý
- [ ] Package Seller Shipping vào màn hình in tem → hiện rõ "Đơn tự giao — không có tem từ TikTok" + hướng xử lý, **không báo lỗi chung chung**
- [ ] Package tạm giữ → không xuất hiện ở danh sách chờ in, không lấy được mã vận đơn
- [ ] Đồng bộ lại từ sàn → không tạo bản ghi trùng, chỉ cập nhật trạng thái + ghi log

---

## M07 — In tem & Chứng từ

> `Get Package Shipping Document` trả **từng package một**, chỉ áp dụng **TikTok Shipping**. Việc gộp file là của ViShop. Đây là bài toán throughput + cache, không phải bài toán render.

| ID           | Ưu tiên | Yêu cầu                                                                                  |
| ------------ | ------- | ---------------------------------------------------------------------------------------- |
| FR-07-01a    | P0      | Dịch vụ lấy tem theo package, chạy nền, giới hạn đồng thời theo hạn mức API từng shop    |
| FR-07-01b    | P0      | Dịch vụ merge PDF — gộp N tem, giữ đúng thứ tự người dùng chọn, chuẩn hoá khổ giấy       |
| FR-07-01c    | P0      | **Cache tem theo package** — lấy một lần, in lại nhiều lần không gọi API                 |
| FR-07-01d    | P0      | Tiến độ realtime; **cho phép tải file bộ phận** khi một số package lỗi                   |
| FR-07-01e    | P0      | Lọc và cảnh báo package Seller Shipping ngay khi chọn in, tách khỏi lô TikTok Shipping   |
| **FR-07-14** | **P0**  | **Pre-fetch tem ngay sau khi RTS thành công** — quyết định trải nghiệm in nhanh hay chậm |
| FR-07-02     | P0      | Thứ tự in: theo thời gian đặt / theo SKU / theo gian hàng                                |
| FR-07-03     | P0      | Phiếu nhặt hàng — gom theo SKU (tổng SL) hoặc theo đơn                                   |
| FR-07-04     | P0      | Phiếu đóng gói theo package, có ghi chú nhặt hàng                                        |
| FR-07-05     | P0      | Phiếu tổng hợp — tổng SKU và SL theo lô in                                               |
| FR-07-06     | P0      | Đánh dấu đã in; tách tab Chưa in / Đã in                                                 |
| FR-07-07     | P0      | In lại trong 30 ngày                                                                     |
| FR-07-08     | P0      | Nhật ký in: ai, lúc nào, bao nhiêu package, lô nào, có cờ "in lại"                       |
| FR-07-09     | P0      | Lỗi từng package không chặn cả lô                                                        |
| FR-07-10     | P1      | Template phiếu tuỳ biến: logo, thông tin shop, chọn trường, khổ A4/A5/K80/K57            |
| FR-07-11     | P1      | Tem SKU (mã vạch SKU kho)                                                                |
| FR-07-12     | P1      | Biên bản bàn giao vận chuyển có chữ ký                                                   |
| FR-07-13     | P1      | Công cụ in tốc độ cao (in trực tiếp không qua hộp thoại trình duyệt)                     |

**BR-07-01** Tem phải là **tem gốc do TikTok cấp**, không tự vẽ lại, không chỉnh sửa nội dung
**BR-07-02** Lô in tối đa 500 package/lệnh, vượt thì tự chia lô
**BR-07-05** Cache tem tối thiểu 30 ngày; cache là nguồn ưu tiên, chỉ gọi API khi cache miss
**BR-07-06** Hạn mức đồng thời lấy tem cấu hình theo shop, mặc định thận trọng. **Không để in tem làm cạn hạn mức của đồng bộ đơn và đẩy tồn**
**BR-07-07** Package Seller Shipping không vào luồng lấy tem TikTok

**SLA hai chế độ**

| Chế độ                   | Cam kết                                                                 |
| ------------------------ | ----------------------------------------------------------------------- |
| Cache hit (đã pre-fetch) | 300 tem → file gộp trong **≤10 giây**                                   |
| Cache miss               | Hiển thị tiến độ ngay, tải được file bộ phận, **không block giao diện** |

**AC**

- [ ] 300 package đã pre-fetch → file gộp sẵn sàng ≤10 giây
- [ ] 300 package chưa có tem → tiến độ hiện ngay, lấy tới đâu tải tới đó, không thao tác nào bị treo
- [ ] 12/300 package lỗi → 288 tem vẫn ra file, 12 package hiện riêng kèm mã lỗi gốc + nút thử lại
- [ ] Lô in lẫn Seller Shipping → tách riêng và cảnh báo trước khi thực thi
- [ ] Phiếu nhặt hàng chế độ gom SKU cho 200 package → mỗi SKU một dòng với tổng SL, sắp xếp theo mã

---

## M08 — Quét mã & Kiểm hàng

| ID       | Ưu tiên | Yêu cầu                                                                               |
| -------- | ------- | ------------------------------------------------------------------------------------- |
| FR-08-01 | P0      | Quét giao hàng — quét mã vận đơn → hiện package → xác nhận bàn giao                   |
| FR-08-02 | P0      | Quét kiểm hàng — mở kiện bằng mã kiện → quét từng mã vạch SP → đối chiếu đủ/thiếu/sai |
| FR-08-03 | P0      | Cảnh báo âm thanh + màu khi quét sai hoặc thừa                                        |
| FR-08-04 | P0      | Chặn hoàn tất khi chưa quét đủ                                                        |
| FR-08-05 | P0      | Hỗ trợ máy quét USB (hoạt động như bàn phím)                                          |
| FR-08-06 | P0      | Nhật ký quét: người quét, thời gian, kết quả, package nào                             |
| FR-08-07 | P1      | Cấu hình thời gian chờ giữa 2 lần quét                                                |
| FR-08-08 | P1      | Chế độ quét nhanh — không cần xác nhận, tự chuyển package tiếp theo                   |
| FR-08-09 | P1      | Quét kèm cân — ghi cân nặng thực, cảnh báo lệch so với dự kiến                        |

**BR-08-01** Quét kiểm hàng chỉ áp dụng cho package có 100% SKU đã ghép nối và có mã vạch
**BR-08-02** Package quét thiếu/sai không chuyển sang trạng thái đã bàn giao
**BR-08-03** **Mọi lần quét đều ghi log kể cả quét sai** — phục vụ truy vết khiếu nại

**AC**

- [ ] Package gồm 2×SKU-A + 1×SKU-B; quét đủ 3 mã đúng → chuyển trạng thái + âm thanh thành công
- [ ] Quét nhầm SKU-C → báo lỗi ngay, không ghi nhận, không cho hoàn tất
- [ ] Quét 2×SKU-A rồi bỏ dở → package giữ nguyên trạng thái, phiên quét được lưu để tiếp tục

---

## M09 — Sau bán & Hoàn hàng

| ID       | Ưu tiên | Yêu cầu                                                                                       |
| -------- | ------- | --------------------------------------------------------------------------------------------- |
| FR-09-01 | P0      | Đồng bộ yêu cầu hoàn/huỷ từ TikTok Shop                                                       |
| FR-09-02 | P0      | Danh sách đơn hoàn: mã đơn, shop, lý do, SKU, SL, tiền hoàn, trạng thái                       |
| FR-09-03 | P0      | Nhập kho hàng hoàn — thủ công hoặc quét mã vận đơn                                            |
| FR-09-04 | P0      | Phân loại khi nhập: **Hàng tốt** (→ available) / **Hàng lỗi** (→ tồn hàng lỗi, không đẩy sàn) |
| FR-09-05 | P0      | Cảnh báo đơn hoàn quá X ngày chưa nhập kho                                                    |
| FR-09-06 | P0      | Điều chỉnh doanh thu và lợi nhuận khi đơn hoàn hoàn tất                                       |
| FR-09-07 | P1      | Thống kê tỉ lệ hoàn theo SKU / shop / lý do                                                   |
| FR-09-08 | P1      | Nhập tay đơn hoàn khi sàn không đẩy về                                                        |

**BR-09-01** Hàng hoàn **không tự cộng lại tồn** — bắt buộc có thao tác nhập kho (hàng có thể chưa về hoặc về trong tình trạng lỗi)
**BR-09-02** Giá vốn khi nhập hàng hoàn = **giá vốn tại thời điểm xuất kho của đơn gốc**, không phải giá vốn hiện tại
**BR-09-03** Tồn hàng lỗi tách riêng, không tham gia đẩy tồn
**BR-09-04** Hoàn toàn phần → đảo toàn bộ doanh thu và phí. Hoàn một phần → đảo theo tỉ lệ dòng bị hoàn

**AC**

- [ ] Quét mã vận đơn ở màn hình nhập hàng hoàn → hiện đúng SKU và SL cần nhập
- [ ] Nhập 2 cái Hàng tốt → available +2 và kích hoạt đẩy tồn
- [ ] Nhập 1 cái Hàng lỗi → tồn hàng lỗi +1, available không đổi
- [ ] Đơn hoàn hoàn tất → báo cáo lợi nhuận kỳ tương ứng được điều chỉnh giảm

---

## M10 — Chi phí & Lợi nhuận

> Module tạo khác biệt cốt lõi. Một con số lợi nhuận sai một lần là mất niềm tin vào cả module.

**Mô hình tính**

```
Lợi nhuận = Doanh thu thực − Giá vốn hàng bán − Σ(dòng chi phí)

Doanh thu thực = Tổng tiền hàng − Chiết khấu do shop chịu

Dòng chi phí (line-item động):
  hoa hồng sàn · hoa hồng affiliate · phí thanh toán
  · phần shop đối ứng voucher · phần shop đối ứng phí vận chuyển
  · phí vận chuyển thực trả · chi phí quảng cáo phân bổ [P1]
  · chi phí vận hành phân bổ [P1] · chi phí khác do người dùng khai
```

| ID       | Ưu tiên | Yêu cầu                                                                              |
| -------- | ------- | ------------------------------------------------------------------------------------ |
| FR-10-01 | P0      | Bóc tách chi phí theo đơn — **mỗi loại phí một dòng riêng, không gộp**               |
| FR-10-02 | P0      | Lợi nhuận + biên lợi nhuận trên danh sách đơn (theo quyền)                           |
| FR-10-03 | P0      | Nhãn trạng thái dữ liệu: **Ước tính** (trước payout) / **Đã đối soát** (sau payout)  |
| FR-10-04 | P0      | Cảnh báo trực quan đơn lỗ                                                            |
| FR-10-05 | P0      | Cảnh báo thiếu dữ liệu khi SKU chưa có giá vốn hoặc chưa ghép nối                    |
| FR-10-06 | P0      | Báo cáo lợi nhuận theo gian hàng                                                     |
| FR-10-07 | P0      | Báo cáo lợi nhuận theo SKU, kèm SL bán và tỉ lệ hoàn                                 |
| FR-10-08 | P0      | Báo cáo lợi nhuận theo ngày, có biểu đồ xu hướng                                     |
| FR-10-09 | P0      | Export tất cả báo cáo ra Excel                                                       |
| FR-10-10 | P1      | Chi phí khác theo kỳ (thuê kho, lương, phần mềm), phân bổ theo doanh thu hoặc số đơn |
| FR-10-11 | P1      | Phân bổ chi phí quảng cáo vào lợi nhuận SKU                                          |
| FR-10-12 | P1      | So sánh lợi nhuận giữa các kỳ                                                        |

**BR-10-01** Trước payout là **ước tính** theo biểu phí cấu hình. Sau payout thay bằng số thực và đổi nhãn "Đã đối soát"
**BR-10-02** Bất kỳ SKU nào trong đơn thiếu giá vốn → **không hiển thị con số lợi nhuận**. Hiện "Thiếu dữ liệu" + link xử lý. **Tuyệt đối không hiển thị số sai**
**BR-10-03** Giá vốn dùng để tính là giá vốn **tại thời điểm xuất kho**, đóng băng vào đơn, không đổi khi giá vốn SKU thay đổi sau đó
**BR-10-04** Đơn huỷ trước khi giao không vào báo cáo lợi nhuận. Đơn hoàn tính vào kỳ mà đơn hoàn hoàn tất
**BR-10-05** Hoa hồng affiliate là dòng phí độc lập, **không gộp vào hoa hồng sàn**

**AC**

- [ ] Đơn hoàn thành đủ giá vốn → hiện bảng: doanh thu, giá vốn, từng dòng phí có tên và số tiền, lợi nhuận, biên %
- [ ] Một SKU thiếu giá vốn → ô lợi nhuận hiện "Thiếu giá vốn" + link tới SKU đó
- [ ] Đơn có hoa hồng affiliate → là dòng riêng, tách khỏi hoa hồng sàn
- [ ] Giá vốn SKU A đổi 50k→60k hôm nay → đơn xuất kho hôm qua vẫn giữ 50k
- [ ] Tổng lợi nhuận báo cáo theo SKU khớp tổng lợi nhuận theo đơn cùng kỳ

---

## M11 — Affiliate & Livestream

> Ba tầng. Tầng A luôn có; tầng B chờ kết luận ToS §3.8; tầng C hạ scope theo giới hạn nền tảng.

### M11-A — Affiliate Insight (Seller scope, luôn có)

Làm được gì với Search Seller Affiliate Orders + Finance settlement:

| ID        | Ưu tiên | Yêu cầu                                                                                       |
| --------- | ------- | --------------------------------------------------------------------------------------------- |
| FR-11-01a | P0      | Gắn cờ đơn có nguồn affiliate trong danh sách đơn                                             |
| FR-11-02a | P0      | **Tỉ trọng affiliate**: doanh thu / số đơn / số SKU từ affiliate so với tổng, theo shop và kỳ |
| FR-11-03a | P0      | Tổng chi phí hoa hồng affiliate theo kỳ, lấy từ Finance sau payout                            |
| FR-11-04a | P0      | **Lợi nhuận theo SKU tách 2 kênh** — affiliate vs tự nhiên                                    |
| FR-11-05a | P0      | Cảnh báo SKU tỉ trọng affiliate cao nhưng **biên lợi nhuận âm**                               |
| FR-11-06a | P1      | Xu hướng tỉ trọng affiliate theo thời gian                                                    |

### M11-B — Creator P&L `[chờ Legal]`

Dữ liệu đã có sẵn ở scope `seller.affiliate_collaboration.read` (§2.2). Chốt chặn là ToS §3.8, không phải capability. **Có khả năng không được chấp nhận** — nếu Legal trả lời không, bỏ hẳn nhóm này thay vì hoãn.

| ID        | Ưu tiên | Yêu cầu                                                                                |
| --------- | ------- | -------------------------------------------------------------------------------------- |
| FR-11-02b | P0*     | Bảng creator: doanh thu, số đơn, hoa hồng, tỉ lệ hoàn, **lợi nhuận ròng sau hoa hồng** |
| FR-11-05b | P0*     | Lợi nhuận theo creator × SKU                                                           |
| FR-11-06b | P0*     | Cảnh báo creator tỉ lệ hoàn bất thường                                                 |
| FR-11-09b | P1      | Gắn nhãn creator, so sánh giữa kỳ                                                      |

`P0*` = P0 trong phạm vi tính năng có gate, **không chặn GA**.
Hoa hồng ở mức creator chỉ chính xác **sau payout**; trước đó là ước tính theo tỉ lệ cấu hình — áp dụng cùng nguyên tắc BR-10-01.

### M11-C — Livestream (scope `data.shop_analytics.public.read`, đã hạ scope)

| ID            | Ưu tiên | Yêu cầu                                                                                                                                                                                                                       |
| ------------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| FR-11-10c     | P0      | Danh sách phiên live của **official account thuộc shop**, kèm metric sàn cấp: gmv, items_sold, sku_orders                                                                                                                     |
| FR-11-11c     | P0      | **Ước tính lợi nhuận phiên theo SKU-sold**: `items_sold theo SKU × giá vốn tại thời điểm phiên` → giá vốn ước tính; lợi nhuận ước tính = gmv − giá vốn ước tính − phí ước tính                                                |
| FR-11-12c     | P0      | Top SKU theo phiên                                                                                                                                                                                                            |
| FR-11-15c     | P0      | **Nhãn cảnh báo bắt buộc trên UI**: "Số liệu phiên là ước tính từ dữ liệu tổng hợp của sàn, chưa trừ đơn huỷ và hoàn"                                                                                                         |
| FR-11-16c     | P1      | Nhập tay chi phí phiên (ads, phí host, quà tặng)                                                                                                                                                                              |
| FR-11-17c     | P1      | So sánh giữa các phiên                                                                                                                                                                                                        |
| ~~FR-11-18c~~ | —       | ~~Mở rộng nếu xác minh TAP đọc được live của creator affiliate~~ — **đã bỏ 07/08/2026.** Chỉ khả thi khi ViAI vận hành một đơn vị môi giới creator (TAP), tức một ngành kinh doanh khác. Không nằm trong định hướng sản phẩm. |

**NGOÀI PHẠM VI — không hứa với khách hàng:** P&L phiên ở mức đơn; đối chiếu huỷ/hoàn theo phiên. **TikTok không có endpoint trả danh sách đơn theo từng phiên live.** Đây là giới hạn nền tảng.

**BR-11-01** Đơn affiliate chưa có dữ liệu hoa hồng → hiện "Chờ dữ liệu hoa hồng", không tính lợi nhuận vội
**BR-11-02** Đơn hoàn phải trừ khỏi doanh thu và hoa hồng của **creator** tương ứng. Không điều chỉnh theo phiên livestream vì nền tảng không cung cấp dữ liệu đơn theo phiên (xem NGOÀI PHẠM VI ở trên).

> **Đã sửa 27/08/2026 — bản trước dùng hai trường sàn không còn trả về.** Bản cũ ghi "dùng `refunded_quantity` / `returned_quantity` / `fully_return`". Đọc lại schema gốc [Search Seller Affiliate Orders](https://partner.tiktokshop.com/docv2/page/search-seller-affiliate-orders-202410) ngày 27/08/2026: cả `refunded_quantity` lẫn `returned_quantity` đều mang chú thích _"Field depreciated, no value return. please refer to fully_return."_ Viết logic đọc hai trường này thì nhận về rỗng, và tỉ lệ hoàn sẽ luôn bằng 0 — sai lặng lẽ, không có lỗi nào để lần ra. Trường `status` ở cấp đơn cũng đã bị khai tử cùng cách.
>
> Nguồn còn dùng được, theo thứ tự ưu tiên:
>
> 1. `skus[].fully_return` — chuỗi `"Yes"` / `"No"`, **không phải boolean**. `"Yes"` nghĩa là dòng hàng bị hoàn toàn bộ và không có đồng hoa hồng nào.
> 2. `skus[].settlement_status = INELIGIBLE` — sàn đã chốt là không trả hoa hồng do hoàn/huỷ.
> 3. **Hoàn một phần** không có trường riêng, nhưng suy được từ chênh lệch hai gốc tính hoa hồng mà sàn trả sẵn: `estimated_commission_base = giá bán × số lượng đặt`, còn `actual_commission_base = giá bán × (số lượng đặt − số lượng hoàn)`. Vậy `số lượng hoàn = (estimated_commission_base − actual_commission_base) / giá bán`, với điều kiện hai giá trị cùng đơn vị tiền.
>
> Cách 3 chỉ dùng được **sau khi sàn chốt**: trước payout thì `actual_*` chưa có giá trị. Trước thời điểm đó chỉ đếm được dòng hàng hoàn toàn bộ qua `fully_return`, và con số tỉ lệ hoàn phải mang nhãn Ước tính đúng như các báo cáo còn lại của M11.
> **BR-11-03** _(Đã bỏ — gán đơn vào phiên theo cửa sổ giờ là bất khả thi, không endpoint nào cung cấp danh sách đơn theo phiên.)_ P&L phiên chỉ dùng metric tổng hợp cấp sàn sẵn có từ FR-11-10c/FR-11-11c (gmv, items_sold, sku_orders, lợi nhuận ước tính theo SKU-sold), không gán từng đơn cụ thể vào phiên.

---

## M12 — Đối soát tài chính

> ⚠️ **Phụ thuộc kết quả xác minh Capability Matrix §9.** Nếu Finance API chỉ trả tổng theo kỳ mà không breakdown theo đơn, BR-12-01 không thực hiện được và module phải thiết kế lại. **Xác minh trước khi code.**

| ID       | Ưu tiên | Yêu cầu                                                         |
| -------- | ------- | --------------------------------------------------------------- |
| FR-12-01 | P0      | Đồng bộ dữ liệu quyết toán từ TikTok Shop                       |
| FR-12-02 | P0      | Bảng đối soát: số dự kiến vs số thực nhận vs chênh lệch         |
| FR-12-03 | P0      | Trạng thái thanh toán: Chưa quyết toán / Đã quyết toán / Lệch   |
| FR-12-04 | P0      | Danh sách đơn lệch, sắp xếp theo mức lệch                       |
| FR-12-05 | P0      | Chi tiết từng dòng phí thực tế so với dòng phí ước tính         |
| FR-12-06 | P0      | Báo cáo dòng tiền theo kỳ: tổng dự kiến, đã nhận, còn chờ       |
| FR-12-07 | P0      | Export dữ liệu đối soát                                         |
| FR-12-08 | P1      | Cảnh báo tự động khi tỉ lệ đơn lệch vượt ngưỡng                 |
| FR-12-09 | P1      | Báo cáo doanh thu thực theo tháng, tách rõ phần sàn đã khấu trừ |
| FR-12-10 | P2      | Đối chiếu sao kê ngân hàng                                      |

**BR-12-01** Ngưỡng lệch: chênh tuyệt đối > 1.000đ **hoặc** chênh tương đối > 1%. Cấu hình được
**BR-12-02** Có payout → thay dòng phí ước tính bằng số thực, chuyển "Đã đối soát"
**BR-12-03** **Dữ liệu đối soát không được sửa tay.** Chỉ được thêm ghi chú và cờ tranh chấp

**AC**

- [ ] Đơn dự kiến 450.000đ, sàn trả 430.000đ → vào danh sách Lệch, mức −20.000đ và −4,4%
- [ ] Payout về cho 500 đơn → 500 đơn chuyển "Đã đối soát", lợi nhuận cập nhật bằng số thực
- [ ] Export đối soát tháng → đủ: mã đơn, ngày, doanh thu, từng dòng phí thực, tiền thực nhận, chênh lệch

---

## M13 — Báo cáo & Dashboard

| ID       | Ưu tiên | Yêu cầu                                                                                                |
| -------- | ------- | ------------------------------------------------------------------------------------------------------ |
| FR-13-01 | P0      | Dashboard hôm nay: doanh thu, số đơn, đơn chờ xử lý, thiếu tồn, lợi nhuận ước tính, so cùng kỳ         |
| FR-13-02 | P0      | **Khối "Cần xử lý ngay"**: package chờ RTS, chờ in, thiếu tồn, chưa ghép nối SKU, shop cần kết nối lại |
| FR-13-03 | P0      | Báo cáo bán hàng theo shop / SKU / ngày, có biểu đồ                                                    |
| FR-13-04 | P0      | Báo cáo tồn kho: giá trị tồn, SKU tồn thấp, SKU tồn lâu không bán                                      |
| FR-13-05 | P0      | Báo cáo đơn hàng: tổng đơn, đơn hiệu quả, tỉ lệ huỷ, tỉ lệ hoàn                                        |
| FR-13-06 | P0      | Bộ lọc chung: gian hàng, thời gian, danh mục SKU                                                       |
| FR-13-07 | P0      | Export mọi báo cáo                                                                                     |
| FR-13-08 | P1      | Báo cáo năng suất nhân sự: số package xử lý/in/quét theo người, theo ngày                              |
| FR-13-09 | P1      | Tuỳ biến dashboard                                                                                     |
| FR-13-10 | P1      | Báo cáo tự động gửi email hằng ngày/tuần                                                               |

**BR-13-01** Mọi chỉ số phải có tooltip ghi rõ định nghĩa (ví dụ: "Đơn hiệu quả = tổng đơn − đơn huỷ")
**BR-13-02** Báo cáo chỉ hiện dữ liệu trong phạm vi quyền
**BR-13-03** Báo cáo tài chính không hiện cho vai trò thiếu quyền `Xem lợi nhuận`

---

## M14 — Cảnh báo & Thông báo

| ID       | Ưu tiên | Yêu cầu                                                                                                                                               |
| -------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| FR-14-01 | P0      | Trung tâm cảnh báo trong ứng dụng, phân loại, đánh dấu đã đọc                                                                                         |
| FR-14-02 | P0      | Loại P0: ủy quyền sắp hết hạn · đẩy tồn thất bại · package thiếu tồn · SKU chưa ghép nối · tồn dưới ngưỡng · đơn lỗ · đơn lệch đối soát · đồng bộ lỗi |
| FR-14-03 | P0      | Cấu hình bật/tắt từng loại và ngưỡng                                                                                                                  |
| FR-14-04 | P0      | Chọn người nhận theo từng loại cảnh báo                                                                                                               |
| FR-14-05 | P0      | Gửi qua email                                                                                                                                         |
| FR-14-06 | P1      | Gửi qua Telegram / Zalo                                                                                                                               |
| FR-14-07 | P1      | Gom cảnh báo cùng loại trong khung giờ, tránh spam                                                                                                    |

**BR-14-01** Chỉ gửi tới user có quyền xem dữ liệu liên quan
**BR-14-02** Cảnh báo cùng loại cho cùng đối tượng không gửi lại trong 24h trừ khi mức độ tăng

---

## M15 — Gói cước & Billing

| Gói           | Gian hàng  | Đơn/tháng  | Tài khoản phụ |
| ------------- | ---------- | ---------- | ------------- |
| Miễn phí      | 1          | 300        | 1             |
| Cơ bản        | 3          | 3.000      | 3             |
| Chuyên nghiệp | 10         | 15.000     | 10            |
| Doanh nghiệp  | 30         | 50.000     | 30            |
| Tuỳ chỉnh     | Thoả thuận | Thoả thuận | Thoả thuận    |

| ID       | Ưu tiên | Yêu cầu                                             |
| -------- | ------- | --------------------------------------------------- |
| FR-15-01 | P0      | Trang gói cước: giới hạn, mức đã dùng, ngày hết hạn |
| FR-15-02 | P0      | Đồng hồ quota đơn realtime, cảnh báo tại 80% và 95% |
| FR-15-03 | P0      | Nâng cấp giữa kỳ, quy đổi thời hạn còn lại          |
| FR-15-04 | P0      | Mua gói đơn bổ sung khi vượt quota                  |
| FR-15-05 | P0      | Lịch sử giao dịch và hoá đơn                        |
| FR-15-06 | P0      | Thanh toán: chuyển khoản + cổng thanh toán nội địa  |
| FR-15-07 | P0      | Xuất hoá đơn VAT với thông tin công ty tự khai      |
| FR-15-08 | P1      | Tự động gia hạn                                     |
| FR-15-09 | P1      | Chương trình giới thiệu                             |

**BR-15-01** Quota tính theo **đơn đồng bộ về lần đầu**; mỗi đơn chỉ tính một lần, re-sync không tính lại
**BR-15-02** Đơn huỷ/chưa thanh toán tại lần đồng bộ đầu tiên **không tính quota**
**BR-15-03** Vượt quota → **không chặn đồng bộ đơn**, chỉ chặn thao tác ghi (RTS, lấy tem, đẩy tồn). _Lý do: chặn đồng bộ làm sai tồn kho của khách, gây thiệt hại thật và mất niềm tin vĩnh viễn._ **Đây là ngoại lệ có chủ đích với nguyên tắc "không chặn RTS/in tem" ở BR-04-01/BR-06-03**: hai BR đó miễn trừ vì lý do dữ liệu nội bộ chưa hoàn chỉnh (SKU chưa ghép nối, thiếu tồn) — lỗi vận hành không do khách hàng gây ra, chặn sẽ phạt oan seller. Quota là ranh giới thương mại (không thanh toán = ngừng dùng dịch vụ trả phí), seller được thông báo trước và toàn quyền xử lý bằng cách nâng gói — hai loại lý do khác bản chất nên không cùng một quy tắc miễn trừ.
**BR-15-04** Hạ gói chỉ thực hiện tại thời điểm gia hạn, sau khi đưa số shop/tài khoản phụ về đúng giới hạn gói mới
**BR-15-05** Hết hạn → chế độ chỉ đọc 15 ngày, sau đó ngừng đồng bộ. Dữ liệu giữ tối thiểu 90 ngày
**BR-15-06** **Khách export toàn bộ dữ liệu bất cứ lúc nào, kể cả sau khi hết hạn.** Không giữ dữ liệu làm con tin

---

# 6. YÊU CẦU PHI CHỨC NĂNG

## 6.1 Hiệu năng

| Chỉ tiêu                           | Ngưỡng                                                            |
| ---------------------------------- | ----------------------------------------------------------------- |
| Độ trễ đồng bộ đơn (p95)           | < 60s từ khi sàn tạo đơn                                          |
| Độ trễ đẩy tồn sau biến động (p95) | < 30s                                                             |
| Tải danh sách 1.000 đơn            | < 2s                                                              |
| In tem 300 package (cache hit)     | ≤ 10s                                                             |
| Batch RTS 300 package              | Có tiến độ ngay, hoàn tất < 5 phút                                |
| Tải dashboard                      | < 3s                                                              |
| Chịu tải đỉnh campaign             | **10× lưu lượng ngày thường** — load test bắt buộc trước tháng 11 |

## 6.2 Độ tin cậy

- Uptime mục tiêu 99,5% (Phase 1)
- Sàn lỗi → **degrade, không fail**: đưa vào hàng đợi, hiển thị trạng thái, không chặn thao tác người dùng
- Không mất dữ liệu đơn trong mọi tình huống lỗi — hàng đợi phải bền vững
- Backup hằng ngày, kiểm tra khôi phục hằng tháng

## 6.3 Bảo mật

- Token sàn mã hoá at-rest, **không bao giờ ghi ra log**
- Không lưu mật khẩu sàn, không lưu cookie phiên
- Mọi API nội bộ kiểm tra tenant + quyền ở tầng server, **không tin client**
- Quyền cấp trường phải chặn ở **tầng API**, không chỉ ẩn ở UI
- Dữ liệu người mua: chỉ hiển thị cho vai trò có quyền, ghi audit khi truy cập hàng loạt
- Rate limit chống lạm dụng cho endpoint export

## 6.4 Dữ liệu & tuân thủ

- **Hạ tầng và dữ liệu đặt tại Việt Nam**
- Lịch sử biến động tồn giữ tối thiểu 2 năm
- Người dùng export được toàn bộ dữ liệu của mình
- Xoá tài khoản → xoá dữ liệu cá nhân theo yêu cầu, giữ dữ liệu giao dịch ẩn danh cho nghĩa vụ kế toán

## 6.5 Khả năng vận hành

- Log có `trace_id` xuyên suốt request
- Dashboard nội bộ: tỉ lệ lỗi API theo shop, độ sâu hàng đợi, độ trễ sync, tỉ lệ đẩy tồn thất bại
- Cảnh báo vận hành khi hàng đợi tồn đọng hoặc tỉ lệ lỗi vượt ngưỡng
- Mọi lỗi từ sàn giữ nguyên **mã lỗi gốc** trong log và trong thông báo cho người dùng

---

# 7. DEFINITION OF DONE

Một FR chỉ được coi là xong khi đủ **tất cả**:

- [ ] Đáp ứng mọi Acceptance Criteria của module
- [ ] Tuân thủ mọi Business Rule liên quan
- [ ] Kiểm tra quyền ở tầng API, không chỉ UI
- [ ] Có unit test cho logic nghiệp vụ (công thức giá vốn, tính đẩy tồn, tính lợi nhuận — **bắt buộc**)
- [ ] Có integration test cho luồng gọi API sàn (dùng mock adapter)
- [ ] Ghi event log / audit log đầy đủ
- [ ] Xử lý lỗi: lỗi từng phần không làm hỏng cả lô; thông báo lỗi cho người dùng có nội dung hành động được
- [ ] Không có truy vấn nào thiếu điều kiện `tenant_id`
- [ ] Đo được: có metric hoặc log để biết tính năng đang chạy đúng trên production

---

# 8. LỘ TRÌNH & PHỤ THUỘC

```
T1–T4    M00 · M01 · M02 · M03 · M04         Nền tảng + uỷ quyền gian hàng + SKU + ghép nối
T5–T8    M05 · M06 · M07                     Tồn kho + package/RTS + in tem      → ALPHA nội bộ
T9–T12   M08 · M09 · M10                     Quét + sau bán + lợi nhuận          → BETA khách hàng
T13–T16  M11 · M12 · M13 · M14 · M15         Affiliate + đối soát + báo cáo + billing → GA
```

**Chuỗi phụ thuộc cứng:**

```
G0 (Seller API) → M02 → M03 → M04 → M05 → M06 → M07
                                       M10 cần M03 (giá vốn) + M06 (dòng phí)
                                       M12 cần M10
                                       M11-A cần M06 + M12
                                       M11-B cần kết luận Legal về ToS §3.8 (song song, không chặn)
```

**Không được đảo thứ tự:** M04 (ghép nối) phải xong trước M05 (tồn kho), vì không có ghép nối thì không có gì để đẩy. M10 (lợi nhuận) phải sau M03 vì cần giá vốn.

**Mốc kiểm tra:**

- Cuối T4: kết nối được shop thật, thấy đơn thật, ghép nối được SKU
- Cuối T8: xử lý trọn vòng đời một package thật từ đơn tới bàn giao — **Alpha nội bộ**
- Cuối T12: 5 khách beta chạy thật, đo được tỉ lệ đơn xử lý trong hệ thống
- Cuối T16: **GA**

---

# 9. API CAPABILITY MATRIX — BẮT BUỘC ĐIỀN TRƯỚC KHI KHOÁ SPEC

Bài học từ đợt review v1.1: tài liệu được viết theo nhu cầu nghiệp vụ, chưa đối chiếu capability thực. Ba module đã lộ sai sót. Khả năng cao còn nữa.

**Mỗi FR P0 phải có một dòng:**

| FR ID | Dữ liệu/hành động cần | Endpoint | Scope cần                            | Trần thông tin | Độ trễ dữ liệu              | Hạn mức  | Trạng thái   |
| ----- | --------------------- | -------- | ------------------------------------ | -------------- | --------------------------- | -------- | ------------ |
|       |                       |          | `seller.*` / `data.shop_analytics.*` | có/không       | realtime / sau payout / T+n | req/phút | ✅ / ⚠️ / ❌ |

**Cách xác minh, bắt buộc dùng.** Trang tài liệu TikTok render bằng JavaScript nên khối JSON mẫu luôn rỗng khi tải thẳng — đọc phần mô tả trên trang là **không đủ** và đã một lần dẫn tới kết luận sai (xem §2.2). Lấy schema đầy đủ ở API nội bộ của trang docs:

```bash
curl "https://partner.tiktokshop.com/api/v1/document/api_meta?src_document_id=<slug>&aid=359713&locale=en-US"
```

Trường `response_param` trong kết quả là schema thật, có đủ tên trường, kiểu, mô tả và giá trị enum. Dán trích dẫn từ đó vào cột "Trần thông tin", không diễn giải lại bằng lời.

## Bốn nhóm ưu tiên soi trước — nghi ngờ còn giả định sai

| #   | Nhóm             | Câu hỏi phải trả lời                                                                                                              | Hệ quả nếu sai                                                                   |
| --- | ---------------- | --------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| 1   | **M12 Đối soát** | Finance API trả settlement ở mức nào — theo đơn, theo package, hay chỉ theo kỳ? Có breakdown từng dòng phí không? Độ trễ bao lâu? | Nếu chỉ có tổng theo kỳ → **BR-12-01 sập, trụ khác biệt số 1 phải thiết kế lại** |
| 2   | **M05 Đẩy tồn**  | Hạn mức cập nhật tồn thực tế bao nhiêu lần/phút/shop?                                                                             | Quyết định SLA "≤30 giây" có khả thi không                                       |
| 3   | **M09 Sau bán**  | API hoàn trả có đủ trường lý do/SKU/SL/tiền hoàn theo dòng? Có webhook hay phải polling?                                          | Ảnh hưởng BR-09-04 và độ chính xác báo cáo lợi nhuận                             |
| 4   | **M03 Sản phẩm** | Cấu trúc biến thể ánh xạ 1-1 sang mô hình SKU của ViShop không? Có trường hợp đặc biệt nào?                                       | Ảnh hưởng toàn bộ M04 và M05                                                     |

**Quy trình khi phát hiện lệch:** ghi vào matrix → báo PM → PM quyết định hạ scope hay đổi thiết kế → cập nhật tài liệu này. **Dev không tự đổi nghiệp vụ.**

---

# 10. RỦI RO

| #       | Rủi ro                                                                                                           | Mức                                                             | Giảm thiểu                                                                                                                                                                                                                       |
| ------- | ---------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| R1      | Không được cấp Seller Partner API                                                                                | Chặn dự án                                                      | Gate G0. Nộp hồ sơ trước khi code                                                                                                                                                                                                |
| R2      | TikTok đổi API hoặc siết hạn mức                                                                                 | Cao                                                             | Adapter có versioning, giám sát deprecation, fallback polling                                                                                                                                                                    |
| R3      | Người dùng vẫn xử lý đơn trên Seller Center → tồn sai                                                            | Cao                                                             | Onboarding ép hoàn tất ghép nối trước khi bật đẩy tồn; đo tỉ lệ xử lý là metric số 1                                                                                                                                             |
| R4      | Bóc tách phí sai → mất niềm tin ngay                                                                             | Cao                                                             | Luôn gắn nhãn "Ước tính" tới khi có payout; đối soát ngược 100%                                                                                                                                                                  |
| ~~R9~~  | ~~Không lấy được TAP hoặc lấy chậm~~                                                                             | —                                                               | **Đã khép 07/08/2026.** Rủi ro không tồn tại: ViShop là App Developer phát hành Public App, không cần tư cách TAP. Toàn bộ dữ liệu creator nằm trong scope gian hàng tự cấp (§2.2)                                               |
| **R10** | **ToS Partner Center §3.8 có thể cấm lưu trữ và dùng lại dữ liệu creator cho mục đích analytics bán cho seller** | **Cao — chưa verify, khả năng không được chấp nhận là có thật** | **Phải có kết luận Legal trước khi thiết kế sâu M11-B. Nếu bị cấm thì bỏ hẳn, không phải hoãn.** Đến khi có kết luận: M11-B nằm ngoài cam kết Phase 1, chỉ giữ ở mức màn hình đã dựng, không xây thêm tầng lưu trữ hồ sơ creator |
| R11     | Hạn mức API khi in tem quy mô lớn                                                                                | Trung–cao                                                       | Pre-fetch + cache + governor. Load test 1.000 package/giờ trước GA                                                                                                                                                               |
| R12     | Package-centric làm tăng độ phức tạp UI                                                                          | Trung                                                           | Mặc định gộp theo order, bung khi cần. Test khả dụng với nhân viên vận hành thật                                                                                                                                                 |
| R13     | Còn capability khác đã giả định sai                                                                              | Cao                                                             | §9 — Capability Matrix là bắt buộc, không phải tuỳ chọn                                                                                                                                                                          |
| R7      | Hạ tầng không chịu nổi đỉnh campaign                                                                             | Trung                                                           | Kiến trúc queue-based; load test 10× trước tháng 11                                                                                                                                                                              |
| R8      | Chi phí hạ tầng vượt doanh thu ở free tier                                                                       | Trung                                                           | Free tier chỉ 300 đơn; giám sát chi phí/tenant từ ngày đầu                                                                                                                                                                       |

---

# 11. PHASE 2 — chỉ để không chặn kiến trúc

Không xây ở Phase 1. Liệt kê ở đây để đội kỹ thuật biết đường mà chừa chỗ.

| Module             | Nội dung                                                                                                                                                                    | Ràng buộc lên Phase 1                                           |
| ------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------- |
| M16 Đa sàn         | Shopee → Lazada → Shopify/Woo → Tiki                                                                                                                                        | ChannelAdapter interface (§2.1), `order.channel`, fee line-item |
| M17 Đa kho & WMS   | **Còn lại ở Phase 2:** quy tắc phân bổ kho tự động, PDA, số seri, tồn hàng lỗi tách kho riêng. _(Đa kho, vị trí kệ, lô & hạn dùng, chuyển kho đã kéo về Phase 1 — xem §13)_ | `merchant_sku.serial_enabled`                                   |
| M18 Thu mua        | NCC, PO, gợi ý nhập hàng, phân bổ chi phí vào giá vốn                                                                                                                       | Giá vốn phải hỗ trợ nhiều nguồn nhập                            |
| M19 Vận chuyển 3PL | GHN · GHTK · Viettel Post · J&T · Ninja Van · VNPost · SPX                                                                                                                  | Package phải hỗ trợ nhà vận chuyển ngoài sàn                    |
| M20 Thuế & Kế toán | Hoá đơn điện tử, kê khai, đẩy sang phần mềm kế toán                                                                                                                         | Dữ liệu đối soát phải sạch và export được                       |
| M21 Quảng cáo      | TikTok Ads, ROAS sau giá vốn                                                                                                                                                | Fee line-item có loại "quảng cáo phân bổ"                       |
| M22 Mobile & PDA   | App xử lý đơn, kiểm kho, quét                                                                                                                                               | API nội bộ phải tách bạch khỏi tầng render                      |
| M23 Open API       | REST + webhook cho khách hàng lớn                                                                                                                                           | §2.1 + FR-00-08                                                 |

---

# 12. CÂU HỎI MỞ

| #   | Câu hỏi                                                                                            | Người trả lời | Chặn?         |
| --- | -------------------------------------------------------------------------------------------------- | ------------- | ------------- |
| 1   | Hồ sơ đăng ký App Developer + Public App tại Partner Center — trạng thái và người phụ trách?       | BD/Legal      | **Có (G0)**   |
| 2   | Capability Matrix §9 — ai chủ trì, khi nào xong?                                                   | Tech Lead     | **Có (G1)**   |
| 3   | ToS Partner Center §3.8 có cho lưu trữ và dùng lại dữ liệu creator làm analytics cho seller không? | Legal         | Có, với M11-B |
| 4   | Headcount và ngân sách 16 tuần đầu?                                                                | Ban giám đốc  | Có            |
| 5   | Hạ tầng đặt ở đâu, nhà cung cấp nào?                                                               | Kỹ thuật      | Có            |
| 6   | Nguồn 30 khách beta?                                                                               | Sales         | Không         |
| 7   | Mô hình giá cuối: theo gian hàng, theo đơn, hay hybrid?                                            | PM            | Không         |
| 8   | Mở Open API ngay từ GA hay để Phase 2?                                                             | Kỹ thuật + PM | Không         |
| 9   | Chính sách lưu trữ dữ liệu sau khi khách rời đi?                                                   | Legal         | Không         |

---

# 13. BẢN VÁ 07/08/2026 — SAU KHI DỰNG XONG UI

Bản dựng UI đầy đủ đã đi qua bốn lô A → B → C → D. Quá trình đó lộ ra mười chỗ spec v1.2 sai hoặc thiếu. Phần này là **bản vá chính thức**: nội dung ở đây thắng nội dung ở các mục trên khi hai bên mâu thuẫn. 206 FR nền giữ nguyên ID; FR-01-02 được sửa câu chữ để phản ánh đúng quan hệ N–N đã có trong data model, 3 FR tổ chức được thêm vào M01, và 35 FR từ bản dựng UI đánh số tiếp trong module cũ, chi tiết đầy đủ ở phần Phụ lục của `vishop-function-list.md`.

## 13.1 Mười thay đổi và lý do

| #   | Thay đổi                                                                                                      | Cái sai của v1.2                                                                                                                                                                                                                                                           |
| --- | ------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | **Đa kho, vị trí kệ, lô & hạn dùng, chuyển kho** kéo từ M17 (Phase 2) về M05 (Phase 1) — FR-05-20…FR-05-27    | §1.3 loại đa kho khỏi Phase 1 với giả định seller chỉ có một kho. Không có vị trí kệ thì phiếu nhặt hàng in ra thứ tự ngẫu nhiên; không có lô và hạn dùng thì không bán được FMCG, mỹ phẩm, thực phẩm chức năng — nhóm hàng lớn nhất trên TikTok Shop VN                   |
| 2   | **Sóng nhặt hàng** thêm vào M07 — FR-07-15…FR-07-17                                                           | Đây là hệ quả trực tiếp của vị trí kệ, không phải một dự án riêng: đã có `bin_code` thì sắp thứ tự nhặt chỉ là một câu lệnh sắp xếp                                                                                                                                        |
| 3   | **Máy trạng thái đóng gói 9 bước** thay cho §4.2 — FR-06-28…FR-06-30                                          | §4.2 mô tả một chuỗi tuyến tính nhưng không nói ai chặn ai. Hệ quả: màn quét và màn in mở song song, người kho quét trúng kiện chưa có tem rồi phải quay lại                                                                                                               |
| 4   | **Theo dõi vận chuyển** thêm vào M06 — FR-06-31…FR-06-36                                                      | FR-06-14 chỉ nêu "tra cứu hành trình", không nói lưu lại. Không lưu thì không phát hiện được kiện đứng im                                                                                                                                                                  |
| 5   | **Sức khoẻ gian hàng** thêm vào M02 — FR-02-14                                                                | Trước đó ViShop tự đặt ngưỡng cảnh báo hiệu suất. Sai về nguyên tắc: sàn có điểm chính thức, seller bị phạt theo điểm của sàn chứ không theo điểm ViShop tự nghĩ ra                                                                                                        |
| 6   | **Việc hôm nay** và **lịch báo cáo** thêm vào M13 — FR-13-11…FR-13-13                                         | Người vận hành mở phần mềm để làm việc, không để đọc biểu đồ. Dashboard số liệu là màn của chủ shop                                                                                                                                                                        |
| 7   | **Trợ lý ViAI** thêm vào M13 và **ranh giới tự động hoá** thêm vào M14 — FR-13-14…FR-13-17, FR-14-08…FR-14-10 | v1.2 không nói gì về trợ lý, nên cũng không nói việc gì máy được tự làm. Thiếu ranh giới đó thì đợt đầu tiên có người nối mô hình vào sẽ có ai đó cho nó tự gọi lấy tem. Chi tiết ở [vishop-ai-automation-roadmap.md](./vishop-ai-automation-roadmap.md)                   |
| 8   | **Bộ nhận webhook từ TikTok** thêm vào M00 — FR-00-09                                                         | v1.2 chỉ có kiến trúc chờ sẵn cho webhook **gửi ra** (FR-00-08, Phase 2). Không có FR nào phụ trách hướng ngược lại: nhận sự kiện TikTok đẩy sang. Mất sự kiện là mất mốc đếm 24h của FR-09-09, mất tín hiệu đổi trạng thái đơn, và mất tín hiệu huỷ uỷ quyền của FR-02-06 |
| 9   | **Duyệt/Từ chối yêu cầu huỷ** thêm vào M09 — FR-09-09                                                         | FR-09-01 (v1.2) chỉ đồng bộ đọc một chiều. TikTok bắt seller trả lời yêu cầu huỷ trong 24h, quá hạn sàn tự duyệt và hoàn tiền khách — đọc mà không trả lời được là lỗ hổng mất tiền thật                                                                                   |
| 10  | **Chế độ quét toàn màn hình và gộp hai luồng quét** thêm vào M08 — FR-08-10, FR-08-11                         | Người kho đứng cách màn hình 1-2 mét, không dùng chuột. Bản trước tách hai route quét kiểm hàng/bàn giao riêng, chuyển trang giữa chừng làm mất nhịp thao tác                                                                                                              |

## 13.2 §4.2 thay thế — máy trạng thái Package

Chuỗi ở §4.2 thay bằng chín bước dưới đây. `stage` do BE tính và trả về; **FE không tự suy lại từ các cờ rời**.

| #   | `stage`             | Điều kiện vào                         | Màn xử lý             |
| --- | ------------------- | ------------------------------------- | --------------------- |
| 1   | `NEED_MAPPING`      | Còn dòng hàng chưa ghép SKU kho       | `/sku-mapping`        |
| 2   | `NEED_PICK`         | Đã ghép SKU, chưa thuộc sóng nhặt nào | `/picking`            |
| 3   | `PICKING`           | Đã vào sóng, sóng chưa nhặt xong      | `/picking`            |
| 4   | `NEED_SCAN`         | Nhặt xong, chờ soát khớp              | `/scan`               |
| 5   | `NEED_SHIP`         | Đã soát, chờ gọi lệnh giao            | `/orders/pending-rts` |
| 6   | `NEED_LABEL`        | Sàn đã nhận lệnh giao, chờ lấy tem    | `/print`              |
| 7   | `NEED_PRINT`        | Có tem trong máy, chưa in ra giấy     | `/print`              |
| 8   | `READY_TO_HANDOVER` | Đã in tem, chờ hãng đến lấy           | `/print`              |
| —   | `SHIPPED`           | Hãng đã lấy hàng                      | trạng thái cuối       |
| —   | `CANCELLED`         | Kiện bị huỷ                           | trạng thái cuối       |

Thứ tự này là của `docs/spec/02` §2. **Soát hàng đứng trước lệnh giao**, vì ở Đông Nam Á tem chỉ có sau khi `Ship Package` thành công, mà cam kết với hãng rồi mới phát hiện thiếu hàng thì Việt Nam không huỷ lại được sau `AWAITING_COLLECTION`.

Ba chốt chặn, là **ràng buộc ở BE** chứ không phải chỉ disable nút trên giao diện:

1. **Chỉ quét được kiện đang ở `NEED_SCAN`.** Bàn quét mở kiện bằng **mã kiện**, không phải mã vận đơn — quét kiểm đứng trước cả lệnh giao lẫn tem nên lúc đó chưa có tem để đọc.
2. **Không gọi lệnh giao cho kiện chưa quét.** `POST /packages/batch-ship` lọc bỏ package có `stage != NEED_SHIP` và trả về danh sách bị loại — giao hàng chưa soát là nguồn gốc khiếu nại thiếu hàng. Ngoại lệ theo BR-04-01: kiện còn dòng chưa ghép SKU nằm ngoài phạm vi quét (BR-08-01) nên bỏ qua cổng này và vẫn RTS được.
3. **Tem và mã vận đơn về cùng một lần gọi, và lần gọi đó nằm SAU `Ship Package`.** [Get Package Shipping Document](https://partner.tiktokshop.com/docv2/page/650aa5fac16ffe02b8f112ca) trả cả `data.doc_url` lẫn `data.tracking_number` trong một response, nhưng chỉ trả về sau khi [Ship Package](https://partner.tiktokshop.com/docv2/page/650aa4f1defece02be6e7cb1) thành công. Không thiết kế luồng "xin tem trước rồi mới ship".

Đây là bản thi công của **FR-06-29** (PRD/function-list): chốt 1-2 ở trên hiện thực hai vế sau của "ba chốt chặn thứ tự" (chưa soát thì không gọi lệnh giao · chưa gọi lệnh giao thì chưa có tem để in); vế đầu là **đã ghép SKU mới vào sóng nhặt**, nằm ở điều kiện vào `NEED_PICK` của bảng `stage` bên trên — không phải "đủ tồn", vì BR-06-03 nói thiếu tồn không chặn gì. Chốt 3 là một ràng buộc kỹ thuật hỗ trợ thêm (thứ tự gọi API), không phải vế thứ tư của FR-06-29.

`stage` **không lưu thành một cột** trên `package`. Suy tại thời điểm đọc, từ trạng thái sàn cộng dữ liệu vận hành nội bộ. Cột lưu sẵn sẽ lệch ngay lần đầu webhook sàn về trước khi việc nội bộ chạy xong, và lúc đó không có cách nào biết cột hay dữ liệu nguồn mới đúng.

## 13.3 §4.3 bổ sung — tồn khi có nhiều kho

Bảng chuyển trạng thái ở §4.3 giữ nguyên, áp dụng ở **cấp từng kho**. Bốn quy tắc thêm:

- **Đẩy tồn gửi một dòng cho mỗi kho sàn, số của dòng nào tính theo tuyến dẫn tới kho sàn đó** (BR-05-07). [Update Inventory](https://partner.tiktokshop.com/docv2/page/6503068fc20ad60284b38858) nhận `inventory[]` với `warehouse_id` bắt buộc trên từng phần tử; không có dạng payload nào nhận một con số tổng, nên "đẩy tổng, không chia theo kho" là bất khả thi chứ không phải một lựa chọn còn mở. Số của mỗi dòng lấy `available` của kho ViShop nối tới kho sàn ấy qua `shop_warehouse_route`, áp `ratio` rồi trừ `safetyStock` của chính tuyến đó; nhiều kho ViShop trỏ chung một kho sàn thì cộng dồn. Nhu cầu nghiệp vụ "giao từ bất kỳ kho nào tuỳ hàng còn ở đâu" đạt được bằng cách nối nhiều tuyến vào cùng một gian hàng, không bằng cách gộp số trước khi gửi. Lưu ý `FLOOR` của tổng không bằng tổng các `FLOOR`, nên số đẩy phải tính ở cấp tuyến để khớp với bảng xem trước (FR-05-19).
- **Lô ở trạng thái `EXPIRED` hoặc `BLOCKED` vẫn nằm trong `on_hand` nhưng không tính vào `available`** (BR-05-08). Ngưỡng lấy từ `exp_base_info` của [Search Goods Hazmat And Expiration Info](https://partner.tiktokshop.com/docv2/page/69e7252e795c140490ac8ef4).
- **`stock_batch.threshold_source` là bắt buộc, nhận `platform_fbt` hoặc `vishop_default`.** Endpoint ngưỡng hạn dùng ở trên thuộc nhóm **Fulfilled by TikTok (FBT)** — chỉ phủ hàng nằm trong kho TikTok, trong khi phần lớn hàng ViShop quản là hàng seller tự giữ. Với `platform_fbt` thì chặn cứng và UI được nói "sàn không cho bán"; với `vishop_default` thì đây là mặc định nội bộ, seller sửa được và UI chỉ được nói là cảnh báo của ViShop. Chi tiết ở [mục 2 dòng #5 của Capability Matrix](./vishop-capability-matrix.md) và §5.2 của [API Contract](./vishop-api-contract.md).
- **Chuyển kho** trừ tồn kho đi ngay khi xuất, chỉ cộng kho đến khi xác nhận nhận (BR-05-09). Phần chênh trong lúc vận chuyển nằm ở trạng thái Đang chuyển — không biến mất khỏi tổng, cũng không được đếm hai lần.

## 13.4 §3.3 cập nhật — trường Phase 2 đã kích hoạt

`inventory.warehouse_id` và `merchant_sku.batch_enabled` **không còn là trường dự phòng**. `inventory` khoá theo cặp `(merchant_sku_id, warehouse_id)`. Chi tiết schema mở rộng (kho, vị trí kệ, lô, chuyển kho, sóng nhặt, hành trình vận chuyển, điểm hiệu suất, lịch báo cáo) nằm ở mục 7 của `vishop-erd.md`.

Còn ở Phase 2: `inventory.reserved`, `order.parent_order_id`, `merchant_sku.serial_enabled`, `channel`/`platform` đa sàn.

## 13.5 §9 Capability Matrix — kết quả audit phần bổ sung

Đã audit 35 FR bổ sung. Phần lớn là nghiệp vụ nội bộ không chạm API sàn. Bốn chỗ có phụ thuộc thật, chi tiết ở [Capability Matrix](./vishop-capability-matrix.md):

- **Điểm hiệu suất gian hàng** (FR-02-14) — [Get SPS Overview](https://partner.tiktokshop.com/docv2/page/6a5f09c44c0d61049ea780c5) và bốn endpoint SPS liên quan. Ràng buộc: điểm và ngưỡng do sàn chấm, ViShop không tự đặt và không nội suy.
- **Kho và tồn theo kho** (FR-05-20, FR-05-22, FR-05-26) — [Get Warehouse List](https://partner.tiktokshop.com/docv2/page/650aa418defece02be6e66b6), [Inventory Search](https://partner.tiktokshop.com/docv2/page/650a9191c16ffe02b8eec161), [Update Inventory](https://partner.tiktokshop.com/docv2/page/6503068fc20ad60284b38858). Ràng buộc: kho ViShop **không map 1-1** với kho TikTok, và Update Inventory chặn thao tác thêm/bớt warehouse.
- **Hành trình vận chuyển** (FR-06-31, FR-06-32) — ✅ [Get Tracking](https://partner.tiktokshop.com/docv2/page/650aa630c16ffe02b8f11803), [Get Shipping Providers](https://partner.tiktokshop.com/docv2/page/650aa48d4a0bb702c06d85cd). Ràng buộc: dữ liệu không realtime, nhịp gọi phải giãn dần theo tuổi kiện.
- **Ngưỡng hạn dùng** (FR-05-24, FR-05-25) — ⚠️ **chỉ phủ một phần**. [Search Goods Hazmat And Expiration Info](https://partner.tiktokshop.com/docv2/page/69e7252e795c140490ac8ef4) nằm ở nhóm Fulfilled by TikTok (FBT), tức chỉ áp cho hàng trong kho TikTok. Hàng seller tự giữ — phần lớn dữ liệu ViShop quản — chưa xác minh được có nguồn ngưỡng tương đương. Cách xử lý ở §13.3; đây là **hạng mục chặn G1 thứ năm**.

Hạng mục chặn G1 ở §9 tăng từ bốn lên **năm**: bốn hạng mục cũ giữ nguyên (phần bổ sung không giải quyết và cũng không làm nặng thêm chúng), cộng thêm khoảng trống ngưỡng hạn dùng ngoài FBT ở trên.

Bảy FR đợt Lô D (FR-13-14…FR-13-17, FR-14-08…FR-14-10) **không thêm phụ thuộc API nào**: trợ lý đọc lại dữ liệu ViShop đã có, còn tool loại `action` chỉ soạn đề xuất rồi dừng. Danh sách bốn phụ thuộc ở trên và số hạng mục chặn G1 vì vậy giữ nguyên.

## 13.6 Ranh giới tự động hoá — điều kiện áp cho mọi việc chạy không có người bấm

Ba mức ở BR-14-03 là **ràng buộc ở BE**, không phải quy ước giao diện. Bất kỳ đường nào gọi được tới lệnh ghi sang sàn — trợ lý, job theo lịch, webhook, Open API sau này — đều phải đi qua cùng một chốt kiểm.

| Mức                      | Điều kiện                                             | Việc thuộc mức                                                                                                                          |
| ------------------------ | ----------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| 1 — tự chạy              | Chỉ đọc, không đổi trạng thái                         | Đọc số liệu, tính lại cảnh báo, gửi báo cáo theo lịch                                                                                   |
| 2 — chạy sau khi bật     | Ghi trong ViShop, hoàn tác được, không gọi sang sàn   | Gom sóng nhặt, gán vị trí kệ, đánh dấu cảnh báo đã đọc                                                                                  |
| 3 — luôn cần người duyệt | Gọi ghi sang sàn, đụng tiền, hoặc không hoàn tác được | Lấy tem, Ship Package, [Update Inventory](https://partner.tiktokshop.com/docv2/page/6503068fc20ad60284b38858), duyệt hoàn hàng, huỷ đơn |

Tiêu chí phân mức là **có hoàn tác được không**, không phải độ phức tạp. Lấy tem là ví dụ điển hình: mỗi kiện chỉ lấy một lần, có tem rồi thì muốn đổi hãng vận chuyển phải huỷ kiện và tách lại từ đầu, nên một lượt tự động sai hãng cho hai trăm kiện là hai trăm lần sửa tay.

Hai điều kiện phát hành đi kèm, cả hai đều chặn:

1. **Luật tự động phải chạy ở chế độ chỉ ghi nhật ký đủ hai tuần** trước khi được phép ghi thật.
2. **Phải có đường tắt dừng toàn bộ tự động hoá trong một thao tác, không cần deploy.** Một hệ tự động không có công tắc tắt là một hệ không được phép bật.
