# Phân quyền theo từng gian hàng

Bản chốt để duyệt · 18/08/2026

## Vấn đề

Hôm nay một thành viên mang đúng một vai trò cho cả tổ chức. Không diễn đạt được câu:
"A là kế toán ở gian hàng O, nhưng chỉ là nhân viên kho ở gian hàng P".

## Mô hình

Thay cột `tenant_members.roleId` bằng bảng nối:

```
tenant_member_roles
  id
  tenantMemberId  → tenant_members
  roleId          → roles
  shopId          → shops, CHO PHÉP RỖNG
  createdAt
  unique (tenantMemberId, roleId, shopId)
```

Đọc ra tiếng Việt:

- `shopId` rỗng → "mang vai trò này ở **mọi** gian hàng, kể cả gian hàng nối sau này"
- `shopId = O` → "mang vai trò này **chỉ ở** gian hàng O"

Một người có thể có nhiều dòng. Ví dụ của Sếp là hai dòng: (A, Kế toán, O) và (A, Kho, P).

## Quy tắc

**Cộng dồn.** Quyền ở một gian hàng = quyền từ các vai trò rỗng gian hàng, cộng quyền từ các vai trò
gắn đúng gian hàng đó. Không có cơ chế trừ.

Muốn một người có ít quyền hơn ở gian hàng P thì **không** gán vai trò rỗng gian hàng cho họ, mà gán
riêng cho từng gian hàng. Đây là cách diễn đạt tự nhiên và làm được mọi thứ cần làm.

Chọn cộng dồn vì nó dễ suy luận: nhìn màn hình là biết ai làm được gì, không phải đoán vai trò nào
thắng vai trò nào.

## Không phải quyền nào cũng gán theo gian hàng được

Đây là phát hiện quan trọng nhất khi rà 75 quyền trong danh mục: **hơn một nửa không tách được**, vì
dữ liệu phía sau chúng không thuộc gian hàng nào.

### Gán theo gian hàng được — 33 quyền

| Nhóm         | Quyền                                                                       | Dữ liệu neo vào gian hàng            |
| ------------ | --------------------------------------------------------------------------- | ------------------------------------ |
| Đơn hàng     | `orders.*` (10)                                                             | `orders.shopId`                      |
| Vận chuyển   | `shipping.*` (3)                                                            | qua `packages.shopId`                |
| In ấn        | `print.label`, `print.label.reprint`, `print.document`, `print.wave.manage` | tem của gói hàng                     |
| Quét         | `scan.check`, `scan.handover`                                               | gói hàng                             |
| Đổi trả      | `returns.*` (4)                                                             | đơn của gian hàng                    |
| Sản phẩm sàn | `products.view`, `products.resync`                                          | `platform_products.shopId`           |
| Gian hàng    | `shops.view`, `shops.edit`, `shops.disconnect`, `shops.sync.run`            | chính gian hàng đó                   |
| Đối soát     | `reconciliation.view`, `reconciliation.export`                              | quyết toán theo gian hàng            |
| Affiliate    | `affiliate.view`, `affiliate.cost.edit`                                     | phiên live và creator theo gian hàng |

### Chỉ cấp tổ chức — 42 quyền

| Nhóm             | Quyền                                                                | Vì sao không tách được                                                                         |
| ---------------- | -------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| SKU kho          | `skus.*` (8)                                                         | `merchant_skus` không có `shopId` — một SKU kho bán ở mọi gian hàng                            |
| Tồn kho          | `inventory.*` (10)                                                   | tồn theo SKU kho và kho vật lý, không theo gian hàng                                           |
| Tài chính        | `finance.*` (3)                                                      | chi phí ghi cho tổ chức                                                                        |
| Báo cáo          | `reports.*` (4)                                                      | bản chất là tổng hợp nhiều gian hàng                                                           |
| Tổ chức          | `members.*` (3), `roles.manage`, `audit_log.view`, `settings.manage` | thuộc tổ chức                                                                                  |
| Cảnh báo         | `alerts.view`, `alerts.config`                                       | cấu hình chung                                                                                 |
| Gói cước         | `billing.view`, `billing.manage`                                     | thuộc tổ chức                                                                                  |
| Trợ lý           | `assistant.use`, `assistant.action.approve`                          | không neo vào gian hàng                                                                        |
| Nối gian hàng    | `shops.connect`                                                      | lúc cấp quyền thì gian hàng chưa tồn tại                                                       |
| Dữ liệu nhạy cảm | `data.cost.view`, `data.profit.view`, `data.buyer_pii.view`          | giá vốn nằm trên SKU kho                                                                       |
| Ghép nối SKU     | `skus.mapping.edit`                                                  | nối SKU sàn (có gian hàng) với SKU kho (không có) — nửa nọ nửa kia, xếp về tổ chức cho an toàn |

Danh mục quyền sẽ mang thêm một nhãn cho mỗi quyền: gán theo gian hàng được, hay chỉ cấp tổ chức. Màn
gán vai trò chỉ hiện đúng nhóm phù hợp, và endpoint từ chối một vai trò gắn gian hàng nếu vai trò đó
chứa quyền chỉ-cấp-tổ chức. Im lặng bỏ qua một cấu hình phân quyền là chỗ sinh lỗ hổng.

## Các tình huống và hành vi chốt

### Quyền và vai trò

| Tình huống                                              | Hành vi                                                        |
| ------------------------------------------------------- | -------------------------------------------------------------- |
| Có vai trò rỗng gian hàng, cộng vai trò riêng ở O       | Ở O có cả hai; ở gian hàng khác chỉ có vai trò rỗng            |
| Chỉ có vai trò riêng ở O và P                           | Không có quyền gì ở gian hàng Q                                |
| Có vai trò rỗng gian hàng, tổ chức nối thêm gian hàng Q | Có quyền ở Q ngay, không cần gán lại                           |
| Chỉ có vai trò riêng, tổ chức nối thêm gian hàng Q      | Không có quyền ở Q cho tới khi được gán                        |
| Không có dòng nào                                       | Là thành viên nhưng không làm được gì — hợp lệ, không phải lỗi |
| Là chủ tổ chức                                          | Bỏ qua toàn bộ, thấy mọi gian hàng (giữ nguyên BR-01-02)       |
| Hai vai trò cùng gắn gian hàng O                        | Hợp quyền của cả hai                                           |
| Gán trùng đúng bộ ba (người, vai trò, gian hàng)        | Ràng buộc duy nhất chặn, trả 409                               |

### Vòng đời

| Tình huống                                 | Hành vi                                                                  |
| ------------------------------------------ | ------------------------------------------------------------------------ |
| Vai trò bị xoá                             | Xoá kèm mọi dòng gán vai trò đó                                          |
| Gian hàng bị ngắt kết nối (xoá mềm)        | Dòng gán **giữ nguyên** — nối lại thì quyền còn đó                       |
| Gian hàng bị xoá cứng                      | Cascade xoá dòng gán. Chỉ xảy ra khi dọn dữ liệu, không có đường qua API |
| Thành viên bị gỡ khỏi tổ chức              | Cascade xoá mọi dòng gán                                                 |
| Thành viên bị vô hiệu hoá                  | Dòng gán giữ nguyên; chặn ở tầng phân giải như hiện nay                  |
| Gán vai trò cho gian hàng của tổ chức khác | 404, không phải 403                                                      |
| Gán vai trò của tổ chức khác               | 404                                                                      |

### Endpoint

| Dạng endpoint                                  | Ví dụ                               | Cách kiểm                                                                                        |
| ---------------------------------------------- | ----------------------------------- | ------------------------------------------------------------------------------------------------ |
| Gian hàng nằm trong đường dẫn                  | `/shops/{shopId}/platform-products` | Kiểm đúng gian hàng đó                                                                           |
| Gian hàng bắt buộc trong query                 | `/packages?shopId=`                 | Kiểm đúng gian hàng đó                                                                           |
| Gian hàng trong thân yêu cầu                   | `POST /packages/{id}/hold`          | Kiểm đúng gian hàng đó                                                                           |
| Gian hàng tuỳ chọn                             | `GET /orders`                       | Cho qua nếu có quyền ở ít nhất một gian hàng; kho dữ liệu lọc theo tập gian hàng có quyền đó     |
| Không có gian hàng, dữ liệu của tổ chức        | `GET /tenants/members`              | Kiểm quyền cấp tổ chức như hiện nay                                                              |
| Không có gian hàng, dữ liệu neo gian hàng ngầm | `GET /orders/{id}`                  | Kho dữ liệu lọc; ngoài phạm vi thì trả 404                                                       |
| Xuất dữ liệu                                   | `POST /orders/exports`              | Chụp tập gian hàng có quyền vào bộ lọc ngay lúc bấm, vì nhánh chạy nền không có ngữ cảnh yêu cầu |
| Webhook, worker, scheduler                     | —                                   | Không có người dùng, không áp phân quyền                                                         |

### Bảo mật

| Tình huống                                           | Hành vi                                                                                                                     |
| ---------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| Người có `members.manage` tự gán thêm quyền cho mình | Chặn: không gán được vai trò chứa quyền mà bản thân không có. Quy tắc này đã có ở endpoint gán vai trò hiện tại, giữ nguyên |
| Gán vai trò chứa `roles.manage` cho người khác       | Chỉ chủ tổ chức làm được — đã có                                                                                            |
| Đoán id gian hàng của tổ chức khác                   | 404 giống hệt gian hàng không tồn tại                                                                                       |
| Xem vai trò của người khác                           | Cần `members.view`, như hiện nay                                                                                            |

## Chuyển đổi dữ liệu

Đây là bước rủi ro nhất: sai một dòng là có người mất quyền hoặc thừa quyền.

| Trạng thái hiện tại                          | Sau khi chuyển                                                    |
| -------------------------------------------- | ----------------------------------------------------------------- |
| Có `roleId`, không có dòng `user_shop_scope` | Một dòng `(thành viên, vai trò, rỗng)`                            |
| Có `roleId`, có N dòng `user_shop_scope`     | N dòng `(thành viên, vai trò, gian hàng)`, **không** có dòng rỗng |
| `roleId` rỗng, là chủ tổ chức                | Không dòng nào — chủ tổ chức vốn bỏ qua mọi kiểm tra              |
| `roleId` rỗng, không phải chủ tổ chức        | Không dòng nào — vốn đã không có quyền gì                         |

Bảng `user_shop_scope` bị bỏ sau khi chuyển. Giữ lại là hai nguồn sự thật cho cùng một câu hỏi, và
chúng sẽ mâu thuẫn.

Bài kiểm tra bắt buộc: dựng dữ liệu mẫu phủ cả bốn dòng trên, tính tập `(người, quyền, gian hàng)`
trước và sau khi chuyển, khẳng định hai tập giống hệt nhau.

## Giới hạn phải chấp nhận

**Tồn kho không tách được.** Nhân viên chỉ làm gian hàng O vẫn xuất kho được SKU đang bán ở gian
hàng P, vì tồn kho là một con số chung cho cả tổ chức. Không có cách phân quyền nào chặn điều này. Muốn
tách thật phải tách tồn kho theo gian hàng — đó là đổi mô hình sản phẩm, không nằm trong đợt này.

**Giá vốn không tách được**, cùng lý do: nó nằm trên SKU kho.

Nếu khách hàng cần hai điều trên, phải nói rõ trước khi bán, hoặc mở một đợt riêng cho việc tách kho
theo gian hàng.

## Thứ tự thi công

| Bước | Việc                                                               | Rủi ro                      |
| ---- | ------------------------------------------------------------------ | --------------------------- |
| 1    | Đánh nhãn "gán theo gian hàng được" cho từng quyền trong danh mục  | Thấp, nhưng phải xong trước |
| 2    | Bảng `tenant_member_roles` + chuyển dữ liệu + bài kiểm tra so khớp | **Cao**                     |
| 3    | Ngữ cảnh yêu cầu tra quyền theo gian hàng; bỏ `user_shop_scope`    | Cao                         |
| 4    | Kho dữ liệu lọc theo tập gian hàng của đúng quyền đang xét         | Trung bình                  |
| 5    | Endpoint gán vai trò theo gian hàng                                | Thấp                        |
| 6    | Màn hình gán và hiển thị                                           | Thấp                        |

Bước 2 và 3 nên đi cùng một nhánh và lên môi trường thử cùng lúc: giữa hai bước đó hệ thống có hai
nguồn quyền, không nên tồn tại lâu.
