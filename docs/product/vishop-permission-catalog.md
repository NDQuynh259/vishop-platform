# ViShop — Danh mục quyền

**Vai trò của tài liệu:** nguồn sự thật cho phân quyền. Backend gác endpoint theo danh mục này, frontend dựng màn phân quyền từ `GET /api/v1/permissions/catalog`, test đối chiếu hai chiều. Không khai báo quyền ở nơi khác.

**Đọc kèm:** [vishop-erd.md](./vishop-erd.md) mục 8 (lược đồ `role`/`role_permission`) và mục 9 (`tenant_member_role`, gán vai trò theo gian hàng) · [vishop-thiet-ke-phan-quyen.md](./vishop-thiet-ke-phan-quyen.md) (tính quyền vai trò dựng sẵn lúc đọc, hướng UI Wave 1) · [vishop-api-contract.md](./vishop-api-contract.md) mục 9.3 (hợp đồng) · [vishop-security-privacy-guidelines.md](./vishop-security-privacy-guidelines.md) (quyền cấp trường)

---

## 1. Quy ước

**Dạng khoá:** `<nhóm>.<đối-tượng?>.<hành-động>` — chữ thường, `snake_case`, phân cách bằng dấu chấm.

Ba quy tắc bắt buộc khi thêm khoá mới:

1. **Tên phải là việc chủ shop gọi được thành lời.** `orders.ship` đạt. `orders.patch_allocation` không đạt.
2. **Không quá 12 khoá cho một nhóm.** Vượt ngưỡng nghĩa là nhóm đang bị chia quá mịn — gộp lại, không xin ngoại lệ.
3. **Mỗi khoá phải gắn ít nhất một endpoint.** Khoá không ai dùng và endpoint nghiệp vụ không có khoá đều làm test chống trôi fail.

**Nguyên tắc `view` là điều kiện cần.** Mọi khoá hành động trong một nhóm ngầm định yêu cầu khoá `view` của nhóm đó. Backend kiểm tra cả hai; UI tự bật `view` khi người dùng tick một hành động và chặn bỏ tick `view` khi còn hành động đang bật.

**Allow-list, không có deny.** Không có quyền phủ định. Không tick nghĩa là không có.

**Quyền nội bộ platform không nằm trong danh mục này.** Cờ tính năng (FR-00-07) do BD/vận hành ViShop bật cho từng tenant, không phải quyền của người dùng tenant. Màn `/admin/feature-flags` hiện đang nằm trong menu "Tổ chức & phân quyền" của app khách — cần tách ra khu vực quản trị nội bộ (xem [vishop-api-contract.md](./vishop-api-contract.md) mục 9.2).

---

## 2. Danh mục

### 2.1 Đơn hàng & kiện — `orders` (M06)

| Khoá                       | Tên hiển thị              | FR                  | Ghi chú                                              |
| -------------------------- | ------------------------- | ------------------- | ---------------------------------------------------- |
| `orders.view`              | Xem đơn hàng              | FR-06-01…12, 26, 27 | Gồm cả chi tiết đơn và lịch sử thao tác              |
| `orders.export`            | Xuất đơn ra Excel         | FR-06-06            |                                                      |
| `orders.note.edit`         | Ghi chú đơn               | FR-06-13            | Cả ghi chú nhặt hàng lẫn nội bộ                      |
| `orders.flag.manage`       | Gắn/gỡ cờ                 | FR-06-19            | Gồm tạo và đặt tên cờ                                |
| `orders.hold.manage`       | Tạm giữ / bỏ tạm giữ      | FR-06-18            | Giữ tồn locked, chặn đơn vào luồng                   |
| `orders.allocation.edit`   | Sửa phân bổ tồn thủ công  | FR-06-22            | Đụng số liệu tồn — tách khỏi `note.edit` có chủ đích |
| `orders.automation.manage` | Quản lý quy tắc tự động   | FR-06-23, 24        | Quy tắc tự RTS/tự gắn cờ chạy thay người             |
| `orders.package.create`    | Tạo kiện                  | FR-06-15a           |                                                      |
| `orders.package.ship`      | Bàn giao vận chuyển (RTS) | FR-06-15b, 15c      | **Ghi sang sàn, không hoàn tác được**                |
| `orders.package.handover`  | Đánh dấu đã bàn giao      | FR-06-17            | Thao tác nội bộ, khác RTS                            |

**10 khoá.** Ba khoá cuối là lý do chính khiến `module × CRUD` không dùng được: cả ba đều rơi vào ô "Sửa" nhưng hệ quả khác nhau hoàn toàn.

### 2.2 Vận chuyển — `shipping` (M06)

| Khoá                        | Tên hiển thị             | FR               | Ghi chú                                          |
| --------------------------- | ------------------------ | ---------------- | ------------------------------------------------ |
| `shipping.view`             | Theo dõi vận đơn         | FR-06-31, 32     |                                                  |
| `shipping.exception.handle` | Xử lý kiện bất thường    | FR-06-33, 34     | Gán người phụ trách, ghi hướng xử lý, đóng vụ    |
| `shipping.fee.manage`       | Đối soát cước vận chuyển | FR-06-37, 06-37b | Cước trả đơn vị vận chuyển, khác phí sàn của M12 |

**3 khoá.**

### 2.3 In tem & chứng từ — `print` (M07)

| Khoá                    | Tên hiển thị                        | FR                  | Ghi chú                                                                    |
| ----------------------- | ----------------------------------- | ------------------- | -------------------------------------------------------------------------- |
| `print.label`           | In tem vận chuyển                   | FR-07-01a…e, 02, 14 | Gọi API sàn lấy tem, tốn hạn mức                                           |
| `print.label.reprint`   | In lại tem                          | FR-07-07            | Tách riêng vì nhật ký in đánh dấu cờ "in lại" (FR-07-08) — hành vi cần soi |
| `print.document`        | In phiếu nhặt / đóng gói / tổng hợp | FR-07-03, 04, 05    | Không gọi API sàn                                                          |
| `print.template.manage` | Quản lý mẫu phiếu                   | FR-07-10            |                                                                            |
| `print.wave.manage`     | Quản lý sóng nhặt hàng              | FR-07-15, 16, 17    |                                                                            |

**5 khoá.** Nhật ký in (FR-07-08) xem được bởi bất kỳ ai có một khoá `print.*` — không cần khoá riêng.

### 2.4 Quét kiểm hàng — `scan` (M08)

| Khoá            | Tên hiển thị   | FR              | Ghi chú                                |
| --------------- | -------------- | --------------- | -------------------------------------- |
| `scan.check`    | Quét kiểm hàng | FR-08-02…05, 10 | Gồm cả xem nhật ký quét của chính mình |
| `scan.handover` | Quét bàn giao  | FR-08-01        |                                        |

**2 khoá.** Cấu hình quét (FR-08-07, 08) thuộc `settings.manage`.

### 2.5 Hoàn hàng — `returns` (M09)

| Khoá              | Tên hiển thị                | FR               | Ghi chú                       |
| ----------------- | --------------------------- | ---------------- | ----------------------------- |
| `returns.view`    | Xem đơn hoàn                | FR-09-01, 02, 07 |                               |
| `returns.receive` | Nhập kho hàng hoàn          | FR-09-03, 04     | Phân loại hàng tốt / hàng lỗi |
| `returns.approve` | Duyệt / từ chối yêu cầu huỷ | FR-09-09         | **Ghi sang sàn, có hạn 24h**  |
| `returns.create`  | Nhập tay đơn hoàn           | FR-09-08         | Khi sàn không đẩy về          |

**4 khoá.**

### 2.6 Tồn kho — `inventory` (M05)

| Khoá                         | Tên hiển thị             | FR                       | Ghi chú                                                      |
| ---------------------------- | ------------------------ | ------------------------ | ------------------------------------------------------------ |
| `inventory.view`             | Xem tồn kho              | FR-05-01, 05, 22, 27     | Gồm sổ biến động và nhật ký đẩy tồn                          |
| `inventory.stock_in`         | Nhập kho                 | FR-05-02, 07             | Cập nhật giá vốn bình quân (FR-05-06 → xem `data.cost.view`) |
| `inventory.stock_out`        | Xuất kho                 | FR-05-03                 |                                                              |
| `inventory.stocktake`        | Kiểm kho                 | FR-05-04, 08             | Gồm xác nhận điều chỉnh lệch                                 |
| `inventory.transfer`         | Chuyển kho               | FR-05-23                 |                                                              |
| `inventory.movement.export`  | Xuất sổ biến động        | FR-05-06                 |                                                              |
| `inventory.batch.manage`     | Quản lý lô & hạn dùng    | FR-05-24, 25             |                                                              |
| `inventory.warehouse.manage` | Quản lý kho & vị trí kệ  | FR-05-20, 21             |                                                              |
| `inventory.push.config`      | Cấu hình đẩy tồn lên sàn | FR-05-11, 12, 13, 18, 19 | Bật/tắt, tỉ lệ, tồn an toàn                                  |
| `inventory.push.run`         | Đẩy tồn thủ công         | FR-05-16                 | **Ghi sang sàn**                                             |

**10 khoá.**

### 2.7a Sản phẩm sàn — `products` (M03)

| Khoá              | Tên hiển thị         | FR               | Ghi chú                          |
| ----------------- | -------------------- | ---------------- | -------------------------------- |
| `products.view`   | Xem sản phẩm sàn     | FR-03-01, 02, 03 | Dữ liệu đồng bộ một chiều từ sàn |
| `products.resync` | Đồng bộ lại sản phẩm | FR-03-04         | Tốn hạn mức API                  |

**2 khoá.** Tách khỏi `skus` vì đây là dữ liệu của sàn, không phải SKU kho do người bán tạo —
hai thứ có vòng đời và người phụ trách khác nhau.

### 2.7b SKU kho & ghép nối — `skus` (M03, M04)

| Khoá                | Tên hiển thị      | FR                       | Ghi chú                                                 |
| ------------------- | ----------------- | ------------------------ | ------------------------------------------------------- |
| `skus.view`         | Xem SKU kho       | FR-03-02, 10             |                                                         |
| `skus.create`       | Tạo SKU kho       | FR-03-05, 11             | Gồm SKU combo                                           |
| `skus.edit`         | Sửa SKU kho       | FR-03-05, 07, 08, 09, 13 | Không gồm giá vốn                                       |
| `skus.cost.edit`    | Sửa giá vốn SKU   | FR-03-06, 13, 15         | **Đụng trực tiếp số lợi nhuận** — tách khỏi `skus.edit` |
| `skus.delete`       | Xoá SKU kho       | FR-03-05                 |                                                         |
| `skus.import`       | Nhập SKU từ Excel | FR-03-12                 |                                                         |
| `skus.export`       | Xuất SKU ra Excel | FR-03-12                 |                                                         |
| `skus.mapping.edit` | Ghép nối SKU      | FR-04-03…08, 10          | Gỡ ghép nối ảnh hưởng đơn đang chạy                     |

**8 khoá.** `skus.mapping.view` không tách riêng — đã nằm trong `skus.view`.

### 2.8 Chi phí & lợi nhuận — `finance` (M10)

| Khoá                     | Tên hiển thị            | FR              | Ghi chú                                                            |
| ------------------------ | ----------------------- | --------------- | ------------------------------------------------------------------ |
| `finance.view`           | Xem chi phí & lợi nhuận | FR-10-01…08, 12 | Thấy màn hình; **số tiền cụ thể còn phụ thuộc `data.profit.view`** |
| `finance.expense.manage` | Quản lý chi phí khác    | FR-10-10, 11    | Chi phí theo kỳ, phân bổ quảng cáo                                 |
| `finance.export`         | Xuất báo cáo lợi nhuận  | FR-10-09        |                                                                    |

**3 khoá.**

### 2.9 Đối soát — `reconciliation` (M12)

| Khoá                    | Tên hiển thị          | FR              | Ghi chú |
| ----------------------- | --------------------- | --------------- | ------- |
| `reconciliation.view`   | Xem đối soát          | FR-12-01…06, 09 |         |
| `reconciliation.export` | Xuất dữ liệu đối soát | FR-12-07        |         |

**2 khoá.**

### 2.10 Affiliate & Live — `affiliate` (M11)

| Khoá                  | Tên hiển thị               | FR                               | Ghi chú                                    |
| --------------------- | -------------------------- | -------------------------------- | ------------------------------------------ |
| `affiliate.view`      | Xem affiliate & phiên live | FR-11-01a…06a, 02b, 05b, 10c…15c |                                            |
| `affiliate.manage`    | Quản lý nhãn creator       | VIS-39, VIS-41                   | Tạo, sửa, xoá nhãn và gán nhãn cho creator |
| `affiliate.cost.edit` | Nhập chi phí phiên live    | FR-11-16c                        | Ads, phí host, quà tặng                    |

**3 khoá.**

### 2.11 Báo cáo — `reports` (M13)

| Khoá                              | Tên hiển thị             | FR               | Ghi chú                             |
| --------------------------------- | ------------------------ | ---------------- | ----------------------------------- |
| `reports.view`                    | Xem báo cáo              | FR-13-03…06      |                                     |
| `reports.export`                  | Xuất báo cáo             | FR-13-07         |                                     |
| `reports.staff_productivity.view` | Xem năng suất nhân viên  | FR-13-08         | Dữ liệu về người — tách có chủ đích |
| `reports.schedule.manage`         | Quản lý lịch gửi báo cáo | FR-13-10, 12, 13 | Gửi ra email ngoài hệ thống         |

**4 khoá.** Dashboard và "Việc hôm nay" (FR-13-01, 02, 11) **không gate** — mọi thành viên đều vào được; số liệu hiển thị trên đó lọc theo quyền của chính người xem.

### 2.12 Trợ lý ViAI — `assistant` (M13, M14)

| Khoá                       | Tên hiển thị             | FR               | Ghi chú                                                            |
| -------------------------- | ------------------------ | ---------------- | ------------------------------------------------------------------ |
| `assistant.use`            | Dùng trợ lý              | FR-13-14…17      | Tool loại `read` chỉ trả về dữ liệu người dùng vốn đã có quyền xem |
| `assistant.action.approve` | Duyệt đề xuất của trợ lý | FR-14-08, 09, 10 | Duyệt xong vẫn phải có quyền của chính hành động đó                |

**2 khoá.** Trợ lý không phải cửa sau: mỗi tool `read` lọc theo quyền người hỏi, mỗi tool `action` kiểm tra quyền tương ứng trước khi thực thi.

### 2.13 Gian hàng — `shops` (M02)

| Khoá                 | Tên hiển thị             | FR                       | Ghi chú                                       |
| -------------------- | ------------------------ | ------------------------ | --------------------------------------------- |
| `shops.view`         | Xem gian hàng & sức khoẻ | FR-02-03, 08, 11, 12, 14 | Gồm nhật ký đồng bộ, danh sách uỷ quyền       |
| `shops.edit`         | Sửa thông tin gian hàng  | FR-02-02                 | Nickname                                      |
| `shops.connect`      | Kết nối gian hàng        | FR-02-01, 13             | **Cấp quyền truy cập dữ liệu sàn cho ViShop** |
| `shops.disconnect`   | Ngắt kết nối gian hàng   | FR-02-09                 | Dừng sync và đẩy tồn toàn hệ thống            |
| `shops.sync.run`     | Chạy đồng bộ thủ công    | FR-02-04, 10             | Tốn hạn mức API                               |
| `shops.group.manage` | Quản lý nhóm gian hàng   | FR-01-09                 | Tạo, sửa, xoá nhóm và gán/gỡ shop khỏi nhóm   |

**6 khoá.**

### 2.14 Tổ chức & hệ thống — `org` (M01, M00)

| Khoá              | Tên hiển thị             | FR           | Ghi chú                                                                   |
| ----------------- | ------------------------ | ------------ | ------------------------------------------------------------------------- |
| `members.view`    | Xem danh sách thành viên | FR-01-02     |                                                                           |
| `members.invite`  | Mời thành viên           | FR-01-02     |                                                                           |
| `members.manage`  | Quản lý thành viên       | FR-01-07, 08 | Đặt lại mật khẩu, vô hiệu hoá, gán vai trò và phạm vi gian hàng           |
| `roles.manage`    | Quản lý vai trò & quyền  | FR-01-03, 04 | **Quyền leo thang — người có khoá này tự cấp được mọi khoá khác.** Xem §4 |
| `audit_log.view`  | Xem nhật ký thao tác     | FR-00-04     | Ghi nhận khuyến nghị ở `vishop-security-privacy-guidelines.md`            |
| `settings.manage` | Cấu hình hệ thống        | M00          | Cấu hình chung, cấu hình quét, ngưỡng cảnh báo                            |

**6 khoá.**

### 2.15 Cảnh báo — `alerts` (M14)

| Khoá            | Tên hiển thị           | FR           | Ghi chú                            |
| --------------- | ---------------------- | ------------ | ---------------------------------- |
| `alerts.view`   | Xem trung tâm cảnh báo | FR-14-01, 02 |                                    |
| `alerts.config` | Cấu hình cảnh báo      | FR-14-03…07  | Loại, ngưỡng, người nhận, kênh gửi |

**2 khoá.**

### 2.16 Gói cước — `billing` (M15)

| Khoá             | Tên hiển thị           | FR                       | Ghi chú                       |
| ---------------- | ---------------------- | ------------------------ | ----------------------------- |
| `billing.view`   | Xem gói cước & hoá đơn | FR-15-01, 02, 05         |                               |
| `billing.manage` | Mua / nâng cấp gói     | FR-15-03, 04, 06, 07, 08 | **Phát sinh thanh toán thật** |

**2 khoá.**

### 2.17 Dữ liệu nhạy cảm — `data` (FR-01-06)

Ba quyền độc lập, **không thuộc module nào**. Người có `orders.view` vẫn không thấy SĐT người mua nếu thiếu `data.buyer_pii.view`.

| Khoá                  | Tên hiển thị            | Chặn field nào                                                         |
| --------------------- | ----------------------- | ---------------------------------------------------------------------- |
| `data.cost.view`      | Xem giá vốn             | `merchantSku.avgCost`, `orderLine.costPrice`, giá trị tồn theo giá vốn |
| `data.profit.view`    | Xem lợi nhuận           | `profit`, `profitMargin` trên đơn, SKU, báo cáo, phiên live            |
| `data.buyer_pii.view` | Xem thông tin người mua | `buyer.phone`, `buyer.fullAddress`                                     |

**3 khoá.**

Ba ràng buộc bắt buộc, đã chốt trong `vishop-security-privacy-guidelines.md`:

- Thiếu quyền thì **field bị lược khỏi response**, không trả giá trị thật rồi để UI che. Cắt bằng ánh xạ DTO tường minh, không dùng `ClassSerializerInterceptor`/`@Exclude`.
- `buyer.name` **không** thuộc phạm vi `data.buyer_pii.view` — mọi vai trò xem được đơn đều thấy tên người nhận. Field PII người mua thêm sau này mặc định bị chặn.
- `event_log` chứa `payloadBefore`/`payloadAfter` đầy đủ, tức là snapshot PII. Đọc `event_log` thô của entity `order`/`package` phải yêu cầu `data.buyer_pii.view`, nếu không nó thành cửa sau lách quyền.

---

## 3. Tổng hợp

**76 khoá / 18 nhóm.** Nhóm lớn nhất 10 khoá, dưới ngưỡng 12.

| Nhóm      | Số khoá |     | Nhóm                                                                        | Số khoá    |
| --------- | ------- | --- | --------------------------------------------------------------------------- | ---------- |
| orders    | 10      |     | returns                                                                     | 4          |
| inventory | 10      |     | reports                                                                     | 4          |
| skus      | 8       |     | shipping                                                                    | 3          |
| org       | 6       |     | finance                                                                     | 3          |
| shops     | 6       |     | data                                                                        | 3          |
| print     | 5       |     | products · scan · reconciliation · affiliate · assistant · alerts · billing | 2 mỗi nhóm |

So với 65 ô của ma trận `13 module × 5 hành động`: nhiều hơn 10 mục nhưng **không còn ô chết** (không có "Xoá đơn hàng"), và mỗi mục nói rõ nó cấp cái gì.

Ngưỡng "≤12 khoá mỗi nhóm" và tính duy nhất của khoá được kiểm tự động, không phải lời hứa: xem `vishop-next/apps/app/tests/unit/permissions.test.ts`.

---

## 4. Quyền cần cảnh báo khi cấp

Bốn khoá dưới đây cần cảnh báo rõ trên UI khi tick, và cần ghi audit riêng khi được cấp:

| Khoá                  | Vì sao                                                                                                               |
| --------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `roles.manage`        | Leo thang — tự cấp được mọi khoá còn lại. Vai trò tuỳ chỉnh **không được phép** chứa khoá này; chỉ Chủ tài khoản có. |
| `orders.package.ship` | Ghi sang sàn, không hoàn tác                                                                                         |
| `returns.approve`     | Ghi sang sàn, ảnh hưởng tiền hoàn                                                                                    |
| `billing.manage`      | Phát sinh thanh toán thật                                                                                            |

`shops.connect` và `shops.disconnect` ở mức dưới một bậc: không mất tiền ngay nhưng làm gián đoạn toàn bộ vận hành.

---

## 5. Ba khoá chưa có FR phủ

| Khoá                  | Màn hình               | Tình trạng                                                                                                                                        |
| --------------------- | ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `shipping.fee.manage` | `/shipping/fees`       | Đã có FR phủ từ 27/08/2026: FR-06-37 (backend) và FR-06-37b (giao diện). Không thuộc M12 vì cước được sàn trừ theo kiện, không theo kỳ quyết toán |
| `audit_log.view`      | `/settings/audit-log`  | Build-spec chưa có FR cho màn hình xem nhật ký thao tác                                                                                           |
| —                     | `/admin/feature-flags` | FR-00-07 là công cụ nội bộ ViShop, không phải quyền của tenant. Cần tách khỏi ứng dụng khách                                                      |

Ba mục này cần PM bổ sung FR trước khi chốt phạm vi GA.

## 6. Bốn vai trò dựng sẵn

Tạo cùng tenant lúc provisioning (`seed_builtin_roles()`), nhưng **quyền của ba vai trò không phải
Chủ tài khoản được tính lúc đọc, không lưu `role_permissions`** — `effectivePermissionKeys(builtinKey,
storedKeys)` trong `libs/contracts` trả về đúng danh sách dưới đây cho mỗi `builtinKey`, bất kể
`role_permissions` có dòng nào hay không (đổi 19/08/2026, xem `vishop-thiet-ke-phan-quyen.md` §"Đợt
0"). Ba danh sách dưới đây vì vậy là **mã nguồn**, không phải dữ liệu chép trong DB — sửa một danh
sách là sửa `BUILTIN_ROLE_PERMISSIONS` trong `libs/contracts/src/lib/permissions/permission-catalog.ts`
và áp dụng ngay cho mọi tenant đang chạy, không cần migration bù.

**Chỉ đọc, không sửa được, nhân bản được** — giữ nguyên hành vi UI hiện tại.

### Chủ tài khoản

Bỏ qua toàn bộ kiểm tra quyền (`TenantMember.role = OWNER`). Không lưu grant nào trong `role_permissions`.

### Vận hành

```
orders.view · orders.export · orders.note.edit · orders.flag.manage · orders.hold.manage
orders.allocation.edit · orders.package.create · orders.package.ship · orders.package.handover
shipping.view · shipping.exception.handle
print.label · print.label.reprint · print.document · print.wave.manage
scan.check · scan.handover
returns.view · returns.receive · returns.approve
inventory.view · skus.view · skus.mapping.edit · products.view
reports.view · alerts.view · shops.view · assistant.use
data.buyer_pii.view
```

Không có: `data.cost.view`, `data.profit.view`, mọi khoá `finance.*`, `reconciliation.*`, `billing.*`, `roles.manage`.

### Kho

```
orders.view · orders.note.edit
inventory.view · inventory.stock_in · inventory.stock_out · inventory.stocktake
inventory.transfer · inventory.movement.export · inventory.batch.manage · inventory.push.run
skus.view · skus.create · skus.edit · skus.import · skus.export · skus.mapping.edit · products.view
print.label · print.document · print.wave.manage
scan.check · scan.handover
returns.view · returns.receive
shipping.view · reports.view · alerts.view · assistant.use
data.cost.view
```

Không có: `data.profit.view`, `data.buyer_pii.view` (theo khuyến nghị bảo mật: kho cần tên và địa chỉ để đóng gói, không có lý do nghiệp vụ cần SĐT nguyên vẹn — địa chỉ in trên tem do sàn cấp, không đi qua API ViShop).
`inventory.push.config` không có — cấu hình đẩy tồn ảnh hưởng mọi shop, thuộc Chủ tài khoản.

### Kế toán

```
orders.view · orders.export
finance.view · finance.expense.manage · finance.export
reconciliation.view · reconciliation.export
affiliate.view · affiliate.manage · affiliate.cost.edit
inventory.view · skus.view · skus.cost.edit · products.view
reports.view · reports.export · shipping.fee.manage
members.view · audit_log.view · billing.view · alerts.view · assistant.use
data.cost.view · data.profit.view
```

Không có: `data.buyer_pii.view` (không có lý do nghiệp vụ cần PII người mua ở bất kỳ màn nào), `billing.manage`, `roles.manage`.

> Ba preset trên bám theo `vishop-security-privacy-guidelines.md` §"Vai trò dựng sẵn nào mặc định có quyền cấp trường" — tài liệu đó ghi rõ đây là **quyết định cấu hình, không phải nguyên tắc kỹ thuật**, và cần PM/Tech Lead chốt. Bảng trên là đề xuất, không phải điều đã chốt.

---

---

## 5. Phạm vi gán của từng quyền — bổ sung 27/08/2026

Mỗi quyền mang một nhãn phạm vi. `shop_scopable` là quyền gán kèm một gian hàng hoặc một nhóm gian hàng được; `tenant_only` là quyền chỉ có nghĩa ở cấp tổ chức, gán kèm gian hàng là vô nghĩa.

Nhãn này là **ràng buộc cưỡng chế, không phải gợi ý giao diện**. Endpoint gán vai trò phải trả `422` khi một vai trò chứa quyền `tenant_only` được gán vào một `shopId` hoặc `groupId`; màn gán vai trò chỉ hiện đúng nhóm phù hợp. Im lặng bỏ qua một cấu hình phân quyền là chỗ sinh lỗ hổng: người vận hành tin rằng đã giới hạn phạm vi, thực tế thì không.

`GET /api/v1/permissions/catalog` trả nhãn này cho từng khoá. Tổng 76 quyền: **33** `shop_scopable`, **43** `tenant_only`.

| Khoá                              | Phạm vi         | Neo vào đâu / vì sao không tách được                                                           |
| --------------------------------- | --------------- | ---------------------------------------------------------------------------------------------- |
| `orders.view`                     | `shop_scopable` | `orders.shopId`                                                                                |
| `orders.export`                   | `shop_scopable` | `orders.shopId`                                                                                |
| `orders.note.edit`                | `shop_scopable` | `orders.shopId`                                                                                |
| `orders.flag.manage`              | `shop_scopable` | `orders.shopId`                                                                                |
| `orders.hold.manage`              | `shop_scopable` | `orders.shopId`                                                                                |
| `orders.allocation.edit`          | `shop_scopable` | `orders.shopId`                                                                                |
| `orders.automation.manage`        | `shop_scopable` | `orders.shopId`                                                                                |
| `orders.package.create`           | `shop_scopable` | `orders.shopId`                                                                                |
| `orders.package.ship`             | `shop_scopable` | `orders.shopId`                                                                                |
| `orders.package.handover`         | `shop_scopable` | `orders.shopId`                                                                                |
| `shipping.view`                   | `shop_scopable` | qua `packages.shopId`                                                                          |
| `shipping.exception.handle`       | `shop_scopable` | qua `packages.shopId`                                                                          |
| `shipping.fee.manage`             | `shop_scopable` | qua `packages.shopId`                                                                          |
| `print.label`                     | `shop_scopable` | tem của kiện hàng                                                                              |
| `print.label.reprint`             | `shop_scopable` | tem của kiện hàng                                                                              |
| `print.document`                  | `shop_scopable` | tem của kiện hàng                                                                              |
| `print.template.manage`           | `tenant_only`   | mẫu tem dùng chung cho mọi gian hàng                                                           |
| `print.wave.manage`               | `shop_scopable` | tem của kiện hàng                                                                              |
| `scan.check`                      | `shop_scopable` | kiện hàng                                                                                      |
| `scan.handover`                   | `shop_scopable` | kiện hàng                                                                                      |
| `returns.view`                    | `shop_scopable` | đơn của gian hàng                                                                              |
| `returns.receive`                 | `shop_scopable` | đơn của gian hàng                                                                              |
| `returns.approve`                 | `shop_scopable` | đơn của gian hàng                                                                              |
| `returns.create`                  | `shop_scopable` | đơn của gian hàng                                                                              |
| `inventory.view`                  | `tenant_only`   | tồn theo SKU kho và kho vật lý, không theo gian hàng                                           |
| `inventory.stock_in`              | `tenant_only`   | tồn theo SKU kho và kho vật lý, không theo gian hàng                                           |
| `inventory.stock_out`             | `tenant_only`   | tồn theo SKU kho và kho vật lý, không theo gian hàng                                           |
| `inventory.stocktake`             | `tenant_only`   | tồn theo SKU kho và kho vật lý, không theo gian hàng                                           |
| `inventory.transfer`              | `tenant_only`   | tồn theo SKU kho và kho vật lý, không theo gian hàng                                           |
| `inventory.movement.export`       | `tenant_only`   | tồn theo SKU kho và kho vật lý, không theo gian hàng                                           |
| `inventory.batch.manage`          | `tenant_only`   | tồn theo SKU kho và kho vật lý, không theo gian hàng                                           |
| `inventory.warehouse.manage`      | `tenant_only`   | tồn theo SKU kho và kho vật lý, không theo gian hàng                                           |
| `inventory.push.config`           | `tenant_only`   | tồn theo SKU kho và kho vật lý, không theo gian hàng                                           |
| `inventory.push.run`              | `tenant_only`   | tồn theo SKU kho và kho vật lý, không theo gian hàng                                           |
| `products.view`                   | `shop_scopable` | `platform_products.shopId`                                                                     |
| `products.resync`                 | `shop_scopable` | `platform_products.shopId`                                                                     |
| `skus.view`                       | `tenant_only`   | `merchant_skus` không có `shopId` — một SKU kho bán ở mọi gian hàng                            |
| `skus.create`                     | `tenant_only`   | `merchant_skus` không có `shopId` — một SKU kho bán ở mọi gian hàng                            |
| `skus.edit`                       | `tenant_only`   | `merchant_skus` không có `shopId` — một SKU kho bán ở mọi gian hàng                            |
| `skus.cost.edit`                  | `tenant_only`   | `merchant_skus` không có `shopId` — một SKU kho bán ở mọi gian hàng                            |
| `skus.delete`                     | `tenant_only`   | `merchant_skus` không có `shopId` — một SKU kho bán ở mọi gian hàng                            |
| `skus.import`                     | `tenant_only`   | `merchant_skus` không có `shopId` — một SKU kho bán ở mọi gian hàng                            |
| `skus.export`                     | `tenant_only`   | `merchant_skus` không có `shopId` — một SKU kho bán ở mọi gian hàng                            |
| `skus.mapping.edit`               | `tenant_only`   | nối SKU sàn (có gian hàng) với SKU kho (không có) — nửa nọ nửa kia, xếp về tổ chức cho an toàn |
| `finance.view`                    | `tenant_only`   | chi phí ghi cho tổ chức                                                                        |
| `finance.expense.manage`          | `tenant_only`   | chi phí ghi cho tổ chức                                                                        |
| `finance.export`                  | `tenant_only`   | chi phí ghi cho tổ chức                                                                        |
| `reconciliation.view`             | `shop_scopable` | quyết toán theo gian hàng                                                                      |
| `reconciliation.export`           | `shop_scopable` | quyết toán theo gian hàng                                                                      |
| `affiliate.view`                  | `shop_scopable` | phiên live và creator theo gian hàng                                                           |
| `affiliate.manage`                | `tenant_only`   | nhãn creator gắn theo tổ chức, không theo gian hàng                                            |
| `affiliate.cost.edit`             | `shop_scopable` | phiên live và creator theo gian hàng                                                           |
| `reports.view`                    | `tenant_only`   | bản chất là tổng hợp nhiều gian hàng                                                           |
| `reports.export`                  | `tenant_only`   | bản chất là tổng hợp nhiều gian hàng                                                           |
| `reports.staff_productivity.view` | `tenant_only`   | bản chất là tổng hợp nhiều gian hàng                                                           |
| `reports.schedule.manage`         | `tenant_only`   | bản chất là tổng hợp nhiều gian hàng                                                           |
| `assistant.use`                   | `tenant_only`   | không neo vào gian hàng                                                                        |
| `assistant.action.approve`        | `tenant_only`   | không neo vào gian hàng                                                                        |
| `shops.view`                      | `shop_scopable` | chính gian hàng đó                                                                             |
| `shops.edit`                      | `shop_scopable` | chính gian hàng đó                                                                             |
| `shops.connect`                   | `tenant_only`   | lúc cấp quyền thì gian hàng chưa tồn tại                                                       |
| `shops.disconnect`                | `shop_scopable` | chính gian hàng đó                                                                             |
| `shops.sync.run`                  | `shop_scopable` | chính gian hàng đó                                                                             |
| `shops.group.manage`              | `tenant_only`   | nhóm là tập gian hàng, quản lý nhóm là việc cấp tổ chức                                        |
| `members.view`                    | `tenant_only`   | thuộc tổ chức                                                                                  |
| `members.invite`                  | `tenant_only`   | thuộc tổ chức                                                                                  |
| `members.manage`                  | `tenant_only`   | thuộc tổ chức                                                                                  |
| `roles.manage`                    | `tenant_only`   | thuộc tổ chức                                                                                  |
| `audit_log.view`                  | `tenant_only`   | thuộc tổ chức                                                                                  |
| `settings.manage`                 | `tenant_only`   | thuộc tổ chức                                                                                  |
| `alerts.view`                     | `tenant_only`   | cấu hình chung                                                                                 |
| `alerts.config`                   | `tenant_only`   | cấu hình chung                                                                                 |
| `billing.view`                    | `tenant_only`   | thuộc tổ chức                                                                                  |
| `billing.manage`                  | `tenant_only`   | thuộc tổ chức                                                                                  |
| `data.cost.view`                  | `tenant_only`   | giá vốn và lợi nhuận nằm trên SKU kho                                                          |
| `data.profit.view`                | `tenant_only`   | giá vốn và lợi nhuận nằm trên SKU kho                                                          |
| `data.buyer_pii.view`             | `tenant_only`   | giá vốn và lợi nhuận nằm trên SKU kho                                                          |

**Hai khoá trước đây chưa được phân loại** đã bổ sung ở bảng trên: `print.template.manage` và `shops.group.manage`. Bảng phân loại trong [vishop-quyen-theo-gian-hang.md](./vishop-quyen-theo-gian-hang.md) đếm 75 quyền và bỏ sót hai khoá này — con số đúng là 76.
