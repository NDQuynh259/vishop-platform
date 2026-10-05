# ViShop — Product Requirements Document

**Sản phẩm:** ViShop — Phần mềm quản lý gian hàng thương mại điện tử
**Chủ quản:** ViTechGroup
**Phiên bản:** v1.1 · 07/08/2026 _(v1.0 · 04/08/2026 là baseline gốc)_
**Người soạn:** Khánh (PM)
**Trạng thái:** Baseline để phân rã sprint

**Thay đổi ở v1.1 — sau khi dựng xong bản UI đầy đủ:**

| #   | Thay đổi                                                                               | Lý do                                                                                                                                                                                                                                             |
| --- | -------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | **Đa kho, vị trí kệ, lô & hạn dùng, chuyển kho** kéo từ M17 (Phase 2) về M05 (Phase 1) | Không có vị trí kệ thì phiếu nhặt hàng in ra thứ tự ngẫu nhiên; không có lô và hạn dùng thì không bán được FMCG, mỹ phẩm, thực phẩm chức năng — nhóm hàng lớn nhất trên TikTok Shop VN. Đây là điều kiện dùng được, không phải tính năng nâng cao |
| 2   | **Sóng nhặt hàng** thêm vào M07                                                        | Hệ quả trực tiếp của vị trí kệ, không phải dự án riêng                                                                                                                                                                                            |
| 3   | **Theo dõi vận chuyển** thêm vào M06 (FR-06-31…36)                                     | FR-06-14 cũ chỉ nêu "tra cứu hành trình", không nói lưu lại — không lưu thì không phát hiện được kiện đứng im                                                                                                                                     |
| 4   | **Máy trạng thái đóng gói và ba chốt chặn thứ tự** thêm vào M06                        | Bản trước cho mở màn quét và màn in song song, người kho quét trúng kiện chưa có tem rồi phải quay lại                                                                                                                                            |
| 5   | **Sức khoẻ gian hàng** thêm vào M02                                                    | Điểm hiệu suất do sàn chấm; trước đó ViShop tự đặt ngưỡng cảnh báo — sai về nguyên tắc                                                                                                                                                            |
| 6   | **Việc hôm nay** và **lịch báo cáo** thêm vào M13                                      | Người vận hành mở phần mềm để làm việc, không để đọc biểu đồ                                                                                                                                                                                      |
| 7   | **Bộ nhận webhook từ TikTok** thêm vào M00 (FR-00-09)                                  | Mất sự kiện webhook là mất mốc đếm 24h của FR-09-09, mất tín hiệu đổi trạng thái đơn, và mất tín hiệu huỷ uỷ quyền của FR-02-06                                                                                                                   |
| 8   | **Chế độ quét toàn màn hình và gộp hai luồng quét** thêm vào M08 (FR-08-10, FR-08-11)  | Người kho đứng cách màn hình 1-2 mét không dùng chuột; tách hai route quét/bàn giao trước đây làm mất nhịp thao tác                                                                                                                               |
| 9   | **Duyệt/Từ chối yêu cầu huỷ** thêm vào M09 (FR-09-09)                                  | TikTok bắt seller trả lời yêu cầu huỷ trong 24h, quá hạn sàn tự duyệt và hoàn tiền khách — đọc mà không trả lời được là lỗ hổng mất tiền thật                                                                                                     |
| 10  | **Trợ lý ViAI** thêm vào M13 (FR-13-14…17)                                             | Trợ lý đọc số nên xếp cùng nhà với dashboard và Việc hôm nay, không mở module riêng — chi tiết ở `vishop-ai-automation-roadmap.md`                                                                                                                |
| 11  | **Tự động hoá có người duyệt** thêm vào M14 (FR-14-08…10)                              | Ba mức tự động hoá cho các tool của ViAI: chỉ đọc, ghi nội bộ hoàn tác được, và luôn cần người duyệt khi ghi sang sàn hoặc đụng tiền                                                                                                              |

Tổng: 35 FR bổ sung, đánh số tiếp trong module cũ (chi tiết ở Phụ lục `vishop-function-list.md`). Dải M16–M23 giữ nguyên cho Phase 2.

---

## Mục lục

**Phần I — Tổng quan**

1. Problem Statement · 2. Định vị & khác biệt · 3. Personas · 4. Phạm vi 2 phase · 5. Nguyên tắc thiết kế

**Phần II — Phase 1: TikTok Shop** (14 module)
M00 Nền tảng · M01 Tài khoản & Phân quyền · M02 Kết nối gian hàng · M03 Sản phẩm & SKU · M04 Ghép nối SKU · M05 Tồn kho · M06 Đơn hàng · M07 In ấn & Chứng từ · M08 Quét mã · M09 Sau bán · M10 Chi phí & Lợi nhuận · M11 Affiliate & Livestream · M12 Đối soát tài chính · M13 Báo cáo · M14 Cảnh báo · M15 Gói cước & Billing

**Phần III — Phase 2: Mở rộng** (8 module)
M16 Đa sàn · M17 Đa kho & WMS _(phần lớn đã kéo về Phase 1 ở v1.1)_ · M18 Thu mua · M19 Vận chuyển 3PL · M20 Thuế & Kế toán · M21 Quảng cáo · M22 Mobile & PDA · M23 Open API

**Phần IV — Vận hành spec**
Metrics · Rủi ro · Timeline · Open Questions

---

---

# PHẦN I — TỔNG QUAN

## 1. Problem Statement

Nhà bán hàng TikTok Shop tại Việt Nam vận hành nhiều gian hàng đang phải thực hiện thủ công bốn công việc mỗi ngày: đăng nhập từng Seller Center để lấy đơn, in vận đơn riêng từng shop, cập nhật tồn kho bằng Excel, và ước lượng lợi nhuận bằng cảm tính.

Hệ quả trực tiếp:

- **Oversell** → huỷ đơn → trừ điểm sức khoẻ shop → giảm hiển thị
- **Sai giá vốn** → bán lỗ mà không biết, phát hiện sau 1–2 tháng
- **Không có số liệu doanh thu thực** để đối chiếu khi sàn khấu trừ nộp thuế thay
- **Không biết creator nào thực sự có lãi** sau khi trừ hoa hồng affiliate

Cấu trúc phí của TikTok Shop phức tạp nhất trong các sàn tại Việt Nam: commission sàn, hoa hồng affiliate, phí thanh toán, phần shop đối ứng voucher và freeship, phí quảng cáo. Không công cụ nào trên thị trường bóc tách được các dòng phí này về từng đơn.

## 2. Định vị & khác biệt

**Định vị:** Phần mềm vận hành gian hàng TikTok Shop cho nhà bán hàng đa gian hàng tại Việt Nam, mở rộng sang đa sàn ở Phase 2.

**Ba trụ khác biệt:**

| Trụ                       | Nội dung                                                                         | Vì sao khó copy                                                           |
| ------------------------- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| **TikTok-native depth**   | Affiliate/creator P&L, livestream session P&L, bóc tách phí TikTok đến từng dòng | Đối thủ hiện tại đều Shopee-first; module TikTok là phần mỏng nhất của họ |
| **Lợi nhuận & thuế thật** | Đối soát payout, doanh thu thực sau phí, số liệu phục vụ kê khai thuế TMĐT VN    | Cần hiểu nghiệp vụ thuế VN, không port được từ sản phẩm nước ngoài        |
| **Dữ liệu tại Việt Nam**  | Hạ tầng và dữ liệu đặt trong nước                                                | Là vấn đề pháp nhân, không sửa được bằng release                          |

**Không cạnh tranh ở:** đăng bán / cào sản phẩm / nhân bản listing, POS bán lẻ, CRM & loyalty, wave picking cho kho lớn.

## 3. Personas

| ID     | Persona                      | Quy mô                          | Nỗi đau chính                                              | Ưu tiên |
| ------ | ---------------------------- | ------------------------------- | ---------------------------------------------------------- | ------- |
| **P1** | Chủ shop TikTok đa gian hàng | 2–10 shop, 500–10.000 đơn/tháng | Không biết lãi thật; oversell; mất thời gian chuyển shop   | Chính   |
| **P2** | Nhân viên vận hành đơn       | 1–3 người/shop                  | Thao tác lặp; nhầm shop; in tem chậm                       | Chính   |
| **P3** | Kế toán / chủ doanh nghiệp   | —                               | Không đối chiếu được payout; không có số liệu kê khai thuế | Chính   |
| **P4** | Nhân viên kho                | 1–5 người                       | Nhặt nhầm hàng; không biết hàng ở đâu                      | Phụ     |
| **P5** | Agency vận hành thuê         | 10–50 shop                      | Không công cụ nào quản lý được quy mô này                  | Mở rộng |

## 4. Phạm vi 2 phase

|                | Phase 1                                                                                                | Phase 2                                                |
| -------------- | ------------------------------------------------------------------------------------------------------ | ------------------------------------------------------ |
| **Sàn**        | TikTok Shop VN                                                                                         | + Shopee, Lazada, Shopify/Woo, Tiki                    |
| **Kho**        | Đa kho, vị trí kệ, lô & hạn dùng, chuyển kho, sóng nhặt hàng _(nâng từ Phase 2 lên Phase 1 — xem M05)_ | + PDA, số seri, quy tắc phân bổ kho tự động            |
| **Tài chính**  | Lợi nhuận + đối soát payout                                                                            | + Hoá đơn điện tử, kế toán, thuế                       |
| **Vận chuyển** | Dùng vận chuyển của sàn                                                                                | + 3PL trực tiếp (GHN, GHTK, Viettel Post, VNPost, J&T) |
| **Thu mua**    | Không                                                                                                  | PO, NCC, gợi ý nhập hàng                               |
| **Marketing**  | Affiliate & Livestream                                                                                 | + TikTok Ads, Shopee Ads                               |
| **Nền tảng**   | Web                                                                                                    | + Mobile app, PDA, Open API                            |

## 5. Nguyên tắc thiết kế

| #   | Nguyên tắc                      | Hệ quả kỹ thuật                                                                                                            |
| --- | ------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| N1  | **Connector-based từ ngày đầu** | Mọi sàn là adapter cùng interface: Auth, Product, Order, Inventory, Fulfillment, Finance. Không hardcode TikTok vào core   |
| N2  | **Chi phí là line-item động**   | Bảng phí dạng dòng (fee_type, amount, source), không phải cột cố định                                                      |
| N3  | **Event log bất biến**          | Mọi biến động tồn và chuyển trạng thái đơn ghi log append-only, không sửa. Là nền tảng cho debug lệch tồn, đối soát, audit |
| N4  | **Idempotent theo external_id** | Mọi bản ghi từ sàn có khoá tự nhiên; re-sync không tạo bản ghi trùng                                                       |
| N5  | **Degradation thay vì fail**    | API sàn lỗi → hàng đợi retry, không chặn thao tác người dùng                                                               |
| N6  | **Không thao tác nào mất dấu**  | Ai làm gì lúc nào — truy được cho mọi hành động thay đổi dữ liệu                                                           |

---

---

# PHẦN II — PHASE 1: TIKTOK SHOP

> **Mục tiêu Phase 1:** Nhà bán hàng TikTok chạy 1–10 gian hàng có thể ngừng dùng Seller Center cho vận hành hằng ngày, và lần đầu nhìn thấy lợi nhuận thật theo từng đơn.

Ký hiệu ưu tiên: **P0** = không có thì không ship · **P1** = fast follow sau GA · **P2** = chỉ thiết kế kiến trúc, không xây

---

## M00 — Nền tảng hệ thống

**Mục đích:** Hạ tầng dùng chung cho mọi module. Không có giao diện người dùng riêng.

### Yêu cầu chức năng

| ID       | Ưu tiên | Yêu cầu                                                                                                                                                                       |
| -------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| FR-00-01 | P0      | **Event log bất biến** — mọi thay đổi tồn kho, trạng thái đơn, giá vốn ghi bản ghi append-only: `entity_type, entity_id, event_type, before, after, actor, source, timestamp` |
| FR-00-02 | P0      | **Job queue có retry** — mọi lệnh gọi API sàn đi qua queue, retry theo backoff luỹ thừa, tối đa 5 lần, có dead-letter queue                                                   |
| FR-00-03 | P0      | **Idempotency** — mọi bản ghi đồng bộ từ sàn khoá theo `(platform, shop_id, external_id)`; re-sync không tạo trùng                                                            |
| FR-00-04 | P0      | **Audit log** — ghi lại mọi thao tác ghi dữ liệu của người dùng: user, hành động, đối tượng, IP, thời gian                                                                    |
| FR-00-05 | P0      | **Rate limit governor** — quản lý hạn mức gọi API theo từng shop, xếp hàng khi chạm ngưỡng, không để bị sàn khoá                                                              |
| FR-00-06 | P0      | **Multi-tenant isolation** — mọi truy vấn bắt buộc gắn `tenant_id`; không có đường truy cập chéo tenant                                                                       |
| FR-00-07 | P1      | **Feature flag** — bật/tắt tính năng theo tenant phục vụ beta                                                                                                                 |
| FR-00-08 | P2      | Kiến trúc sẵn sàng cho **webhook ra ngoài** và **public API** (không xây ở Phase 1)                                                                                           |

### Quy tắc nghiệp vụ

- **BR-00-01:** Không được xoá cứng bản ghi giao dịch (đơn, phiếu kho, dòng phí). Chỉ đánh dấu huỷ/vô hiệu.
- **BR-00-02:** Mọi thời gian lưu ở UTC, hiển thị theo múi giờ VN (UTC+7).
- **BR-00-03:** Mọi số tiền lưu dạng số nguyên đơn vị nhỏ nhất (đồng), không dùng float.

---

## M01 — Tài khoản, Tổ chức & Phân quyền

**Mục đích:** Cho phép chủ shop cấp quyền cho nhân viên theo đúng phạm vi công việc, không lộ dữ liệu nhạy cảm.

### User Stories

- Là chủ shop, tôi muốn tạo tài khoản cho nhân viên và giới hạn họ chỉ thấy shop được giao.
- Là chủ shop, tôi muốn ẩn giá vốn khỏi nhân viên vận hành.
- Là agency, tôi muốn nhóm các shop theo khách hàng để phân công và báo cáo riêng.
- Là người dùng được mời vào nhiều tổ chức, tôi muốn chuyển giữa các tổ chức bằng một lần chọn, không phải đăng xuất rồi đăng nhập bằng tài khoản khác.
- Là chủ shop đang vận hành một tổ chức, tôi muốn mở thêm tổ chức riêng cho một mảng kinh doanh mới mà không trộn dữ liệu với tổ chức đang chạy.

### Yêu cầu chức năng

| ID       | Ưu tiên | Yêu cầu                                                                                            |
| -------- | ------- | -------------------------------------------------------------------------------------------------- |
| FR-01-01 | P0      | Đăng ký / đăng nhập bằng email + mật khẩu; xác thực email                                          |
| FR-01-02 | P0      | Quan hệ **User ↔ Tenant** là N–N qua **TenantMember**; Role gán theo membership                    |
| FR-01-03 | P0      | 4 vai trò dựng sẵn: **Chủ tài khoản · Vận hành · Kho · Kế toán**                                   |
| FR-01-04 | P0      | Vai trò tuỳ chỉnh: chọn quyền từ danh mục quyền, nhóm theo module                                  |
| FR-01-05 | P0      | **Data scope theo gian hàng** — user chỉ thấy đơn, sản phẩm, báo cáo của shop được gán             |
| FR-01-06 | P0      | **Quyền cấp trường**: `Xem giá vốn`, `Xem lợi nhuận`, `Xem thông tin người mua` là 3 quyền độc lập |
| FR-01-07 | P0      | Chủ tài khoản đặt lại mật khẩu cho tài khoản phụ                                                   |
| FR-01-08 | P0      | Vô hiệu hoá tài khoản (không xoá) — giữ lịch sử thao tác                                           |
| FR-01-09 | P1      | **Nhóm gian hàng** — gom shop theo khách hàng/thương hiệu, phân quyền và báo cáo theo nhóm         |
| FR-01-10 | P1      | Xác thực 2 lớp (2FA) qua TOTP                                                                      |
| FR-01-11 | P1      | Lịch sử đăng nhập & thiết bị                                                                       |
| FR-01-12 | P0      | Liệt kê các **tổ chức** (tenant) mà user đang đăng nhập là thành viên                              |
| FR-01-13 | P0      | Chọn tổ chức hiện hành; lựa chọn giữ nguyên qua các request                                        |
| FR-01-14 | P0      | Tạo thêm tổ chức, người tạo trở thành Chủ tài khoản của tổ chức đó                                 |

Một người dùng có thể thuộc nhiều tổ chức, và một tổ chức có nhiều người dùng. Mỗi tổ chức chứa nhiều gian hàng TikTok Shop — gian hàng không phải là tổ chức.

### Quy tắc nghiệp vụ

- **BR-01-01:** Số tài khoản phụ giới hạn theo gói cước. Vượt hạn mức → chặn tạo mới, không tự động tính thêm tiền.
- **BR-01-02:** Chủ tài khoản không thể tự hạ quyền của chính mình.
- **BR-01-03:** Khi user bị vô hiệu, mọi phiên đăng nhập bị huỷ trong ≤60 giây.

### Acceptance Criteria

- [ ] Given user có vai trò Vận hành và chỉ được gán Shop A, when mở danh sách đơn, then chỉ thấy đơn của Shop A, không có tuỳ chọn lọc sang shop khác
- [ ] Given user không có quyền `Xem giá vốn`, when mở chi tiết đơn, then cột giá vốn và lợi nhuận hiển thị dấu `***`, và API trả về cũng không chứa giá trị thật
- [ ] Given chủ tài khoản vô hiệu hoá user B, when B thao tác tiếp, then bị đăng xuất và nhận thông báo tài khoản đã bị vô hiệu

---

## M02 — Kết nối gian hàng

**Mục đích:** Đưa gian hàng TikTok Shop vào hệ thống qua API chính thức và giữ kết nối luôn khoẻ.

### User Stories

- Là chủ shop, tôi muốn kết nối nhiều gian hàng TikTok bằng vài cú click, không phải nhập cookie hay mật khẩu.
- Là chủ shop, tôi muốn được cảnh báo trước khi ủy quyền hết hạn, không phải phát hiện khi đơn ngừng về.

### Yêu cầu chức năng

| ID       | Ưu tiên | Yêu cầu                                                                                                                                                   |
| -------- | ------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| FR-02-01 | P0      | Kết nối TikTok Shop VN qua **OAuth Open API chính thức**                                                                                                  |
| FR-02-02 | P0      | Đặt **nickname** cho gian hàng, unique trong tenant                                                                                                       |
| FR-02-03 | P0      | Danh sách gian hàng: trạng thái kết nối, ngày hết hạn token, lần đồng bộ cuối, số đơn/tồn đã đồng bộ                                                      |
| FR-02-04 | P0      | **Đồng bộ khởi tạo**: kéo sản phẩm + đơn 30 ngày gần nhất khi kết nối lần đầu, hiển thị tiến độ                                                           |
| FR-02-05 | P0      | **Tự động gia hạn token**; nếu thất bại → chuyển trạng thái "Cần kết nối lại" + cảnh báo                                                                  |
| FR-02-06 | P0      | Cảnh báo hết hạn ủy quyền **trước 7 ngày** qua in-app + email                                                                                             |
| FR-02-07 | P0      | Luồng **kết nối lại** không mất dữ liệu lịch sử của shop                                                                                                  |
| FR-02-08 | P0      | **Nhật ký đồng bộ**: loại dữ liệu, thời điểm, số bản ghi, số lỗi, lý do lỗi                                                                               |
| FR-02-09 | P0      | Ngắt kết nối gian hàng — dữ liệu lịch sử được giữ, dừng mọi đồng bộ và đẩy tồn                                                                            |
| FR-02-10 | P1      | Nút **đồng bộ thủ công** theo loại dữ liệu (đơn / sản phẩm / tồn / tài chính)                                                                             |
| FR-02-11 | P1      | Kiểm tra sức khoẻ kết nối định kỳ, hiển thị đèn xanh/vàng/đỏ                                                                                              |
| FR-02-14 | P0      | **Sức khoẻ gian hàng** — điểm hiệu suất do TikTok chấm cho từng shop, sáu chỉ số thành phần, xu hướng theo thời gian và danh sách đơn đang kéo điểm xuống |

### Quy tắc nghiệp vụ

- **BR-02-01:** Một shop TikTok chỉ được kết nối vào **một tenant** tại một thời điểm. Nếu shop đã ở tenant khác → báo lỗi rõ ràng, không cho đè.
- **BR-02-05:** Điểm và ngưỡng hiệu suất gian hàng **lấy nguyên từ sàn, ViShop không tự đặt ngưỡng và không nội suy**. Thiếu dữ liệu thì hiển thị "chưa có dữ liệu", không đoán. Ngưỡng của sàn được lưu lại tại thời điểm chấm, vì sàn đổi ngưỡng được mà báo cáo cũ vẫn phải giải thích được vì sao lúc đó shop bị đánh giá như vậy.
- **BR-02-02:** Số gian hàng kết nối giới hạn theo gói cước.
- **BR-02-03:** Khi shop ở trạng thái "Cần kết nối lại", hệ thống **dừng đẩy tồn** cho shop đó để tránh ghi đè sai, nhưng vẫn giữ dữ liệu.
- **BR-02-04:** Không sử dụng bất kỳ hình thức scraping, mô phỏng đăng nhập, hay lưu cookie phiên của người dùng.

### Acceptance Criteria

- [ ] Given tenant chưa có shop, when hoàn tất OAuth, then shop xuất hiện với trạng thái "Đang đồng bộ khởi tạo" và thanh tiến độ
- [ ] Given đồng bộ khởi tạo, then đơn 30 ngày gần nhất về hết trong ≤10 phút với shop có ≤10.000 đơn
- [ ] Given token còn 7 ngày, then user nhận cảnh báo in-app + email, lặp lại vào ngày còn 3 và còn 1
- [ ] Given kết nối lại thành công, then đơn phát sinh trong thời gian gián đoạn được kéo bù đầy đủ, không trùng
- [ ] Given shop đã kết nối ở tenant khác, when thử kết nối, then hiển thị lỗi "Gian hàng đang được kết nối ở tài khoản khác" và hướng dẫn xử lý

---

## M03 — Sản phẩm & SKU hàng hoá

**Mục đích:** Xây lớp SKU kho độc lập với SKU sàn — nền tảng cho toàn bộ tồn kho và giá vốn.

### User Stories

- Là chủ shop, tôi muốn quản lý một danh mục SKU kho duy nhất, không phụ thuộc cách đặt mã của từng shop.
- Là chủ shop, tôi muốn khai báo giá vốn để hệ thống tính được lợi nhuận.
- Là chủ shop, tôi muốn tạo combo bán kèm mà vẫn trừ đúng tồn từng món.

### Yêu cầu chức năng

**Sản phẩm sàn (đọc từ TikTok)**

| ID       | Ưu tiên | Yêu cầu                                                                                     |
| -------- | ------- | ------------------------------------------------------------------------------------------- |
| FR-03-01 | P0      | Đồng bộ danh sách sản phẩm + biến thể từ TikTok Shop (một chiều, chỉ đọc)                   |
| FR-03-02 | P0      | Danh sách sản phẩm sàn: ảnh, tên, mã SKU sàn, biến thể, giá, tồn trên sàn, trạng thái, shop |
| FR-03-03 | P0      | Lọc & tìm theo shop, trạng thái, đã ghép nối / chưa ghép nối                                |
| FR-03-04 | P1      | Đồng bộ lại sản phẩm theo yêu cầu                                                           |

**Merchant SKU (SKU kho)**

| ID       | Ưu tiên | Yêu cầu                                                                   |
| -------- | ------- | ------------------------------------------------------------------------- |
| FR-03-05 | P0      | Tạo/sửa SKU kho: mã (unique trong tenant), tên, ảnh, danh mục, ghi chú    |
| FR-03-06 | P0      | **Giá vốn** — bình quân gia quyền động, cập nhật tự động khi nhập kho     |
| FR-03-07 | P0      | **Mã vạch (GTIN)** — hỗ trợ tối đa 10 mã cho một SKU                      |
| FR-03-08 | P0      | Cân nặng và kích thước (D×R×C)                                            |
| FR-03-09 | P0      | **Ngưỡng cảnh báo tồn** theo SKU                                          |
| FR-03-10 | P0      | Trạng thái kinh doanh: Đang bán / Ngừng bán                               |
| FR-03-11 | P0      | **SKU combo** — gồm nhiều SKU con với số lượng; không có tồn vật lý riêng |
| FR-03-12 | P0      | Import/Export SKU qua Excel (mẫu chuẩn, báo lỗi theo dòng)                |
| FR-03-13 | P0      | Cập nhật hàng loạt: giá vốn, danh mục, trạng thái, ngưỡng cảnh báo        |
| FR-03-14 | P1      | Danh mục SKU nhiều cấp                                                    |
| FR-03-15 | P1      | Lịch sử thay đổi giá vốn theo SKU                                         |
| FR-03-16 | P2      | Đa đơn vị tính (thùng / lốc / cái)                                        |

### Quy tắc nghiệp vụ

- **BR-03-01:** Công thức giá vốn bình quân gia quyền:
  `Giá vốn mới = (Tồn hiện tại × Giá vốn cũ + SL nhập × Đơn giá nhập) ÷ (Tồn hiện tại + SL nhập)`
  Tính lại tại mỗi lần nhập kho. Xuất kho không làm đổi giá vốn.
- **BR-03-02:** SKU combo **không có tồn vật lý**. Tồn khả dụng của combo = `MIN(tồn khả dụng SKU con ÷ số lượng trong combo)`.
- **BR-03-03:** Giá vốn combo = tổng giá vốn các SKU con theo số lượng, tại thời điểm xuất kho.
- **BR-03-04:** **Không xoá được SKU** khi: tồn ≠ 0, hoặc đang có ghép nối, hoặc là thành phần của combo, hoặc đã phát sinh giao dịch. Chỉ cho phép "Ngừng bán".
- **BR-03-05:** Mã SKU không được sửa sau khi đã phát sinh giao dịch.
- **BR-03-06:** SKU trạng thái "Ngừng bán" không xuất hiện trong cảnh báo tồn thấp và gợi ý nhập hàng.

### Acceptance Criteria

- [ ] Given SKU A tồn 100 giá vốn 50.000đ, when nhập 50 cái giá 60.000đ, then giá vốn mới = 53.333đ (làm tròn đến đồng)
- [ ] Given combo C = 2×A + 1×B, A khả dụng 10, B khả dụng 3, then tồn khả dụng của C = 3
- [ ] Given SKU đang được ghép nối với listing, when bấm xoá, then hệ thống chặn và liệt kê các ghép nối đang tồn tại
- [ ] Given import Excel 1.000 dòng có 5 dòng lỗi, then 995 dòng được nhập và file lỗi tải về ghi rõ dòng nào sai vì sao

---

## M04 — Ghép nối SKU (SKU Mapping)

**Mục đích:** Nối SKU sàn ↔ SKU kho. Đây là bước cản trở lớn nhất khi onboarding — phải làm cực nhẹ.

### User Stories

- Là chủ shop mới, tôi muốn hệ thống tự ghép nối phần lớn sản phẩm để không phải làm tay hàng trăm dòng.
- Là chủ shop, tôi muốn biết còn bao nhiêu listing chưa ghép nối vì chúng sẽ làm sai tồn kho.

### Yêu cầu chức năng

| ID       | Ưu tiên | Yêu cầu                                                                                                 |
| -------- | ------- | ------------------------------------------------------------------------------------------------------- |
| FR-04-01 | P0      | Trang ghép nối: hai cột SKU sàn ↔ SKU kho, lọc theo trạng thái ghép nối, shop, sản phẩm                 |
| FR-04-02 | P0      | **Tự động gợi ý ghép nối** theo: mã SKU sàn trùng mã SKU kho → mã vạch trùng → tên tương đồng           |
| FR-04-03 | P0      | Chấp nhận gợi ý hàng loạt / từng dòng                                                                   |
| FR-04-04 | P0      | Ghép nối thủ công: chọn SKU kho cho từng biến thể sàn                                                   |
| FR-04-05 | P0      | **Tự tạo SKU kho từ SKU sàn** — tạo hàng loạt cho các listing chưa có SKU tương ứng                     |
| FR-04-06 | P0      | Một SKU sàn ghép với **một** SKU kho hoặc **nhiều** SKU kho theo số lượng (trường hợp listing là combo) |
| FR-04-07 | P0      | Import/Export ghép nối qua Excel                                                                        |
| FR-04-08 | P0      | Sửa/gỡ ghép nối; cảnh báo ảnh hưởng tới đơn đang xử lý                                                  |
| FR-04-09 | P0      | **Bảng đếm chưa ghép nối** hiển thị thường trực trên dashboard                                          |
| FR-04-10 | P1      | **Quy tắc ghép nối tự động** — cấu hình mẫu để listing mới về tự ghép                                   |

### Quy tắc nghiệp vụ

- **BR-04-01:** Đơn có SKU chưa ghép nối → **không trừ tồn**, đơn vào danh sách "Cần xử lý ghép nối", không chặn việc in tem và giao hàng.
- **BR-04-02:** Không cho phép bật đẩy tồn cho gian hàng có tỉ lệ ghép nối < 100% trừ khi người dùng xác nhận rõ ràng rủi ro.
- **BR-04-03:** Sửa ghép nối chỉ áp dụng cho đơn **phát sinh từ thời điểm sửa**. Có tuỳ chọn áp dụng ngược cho đơn chưa giao.
- **BR-04-04:** Không cho phép hai SKU sàn khác nhau trong cùng shop ghép trùng cách gây mâu thuẫn đẩy tồn.

### Acceptance Criteria

- [ ] Given shop có 500 listing với mã SKU đặt trùng mã kho, when chạy gợi ý tự động, then ≥90% được gợi ý đúng và chấp nhận hàng loạt trong 1 thao tác
- [ ] Given listing chưa ghép nối, when có đơn về, then đơn vẫn hiển thị và in tem được, nhưng gắn cờ "Chưa ghép nối SKU" và không trừ tồn
- [ ] Given gỡ ghép nối của SKU đang có 5 đơn chờ xử lý, then hệ thống cảnh báo cụ thể 5 đơn đó trước khi cho phép

---

## M05 — Tồn kho

**Mục đích:** Một nguồn sự thật về tồn, đẩy đồng thời lên mọi gian hàng, không bao giờ bán quá.

### User Stories

- Là chủ shop, tôi muốn bán một cái ở shop A thì tồn ở shop B, C tự giảm ngay.
- Là chủ shop, tôi muốn giữ lại một phần tồn không đẩy lên sàn để dự phòng.
- Là chủ shop, tôi muốn biết vì sao tồn của một SKU thay đổi.

### Mô hình dữ liệu tồn kho

```
Tồn tổng (On-hand) = Khả dụng (Available) + Đã khoá (Locked)
Đang về (Incoming)  — nằm ngoài Tồn tổng, chỉ để tham khảo
```

Chuyển trạng thái:

| Sự kiện                                                  | Chuyển                                                    |
| -------------------------------------------------------- | --------------------------------------------------------- |
| Đơn đồng bộ về (theo cấu hình: khi đặt / khi thanh toán) | Khả dụng → Đã khoá                                        |
| Đơn chuyển sang "Đã giao cho vận chuyển"                 | Đã khoá → trừ khỏi Tồn tổng, sinh phiếu xuất              |
| Đơn huỷ khi chưa xuất kho                                | Đã khoá → Khả dụng                                        |
| Đơn huỷ/hoàn sau khi đã xuất kho                         | Không tự cộng lại — cần thao tác nhập kho hàng hoàn (M09) |
| Nhập kho                                                 | → Tồn tổng, Khả dụng tăng                                 |
| Kiểm kho lệch                                            | Điều chỉnh Tồn tổng, ghi lý do                            |

### Yêu cầu chức năng

**Quản lý tồn**

| ID       | Ưu tiên     | Yêu cầu                                                                                                                        |
| -------- | ----------- | ------------------------------------------------------------------------------------------------------------------------------ |
| FR-05-01 | P0          | Bảng tồn kho theo SKU: Tồn tổng, Khả dụng, Đã khoá, Đang về, Giá vốn, Giá trị tồn                                              |
| FR-05-02 | P0          | **Nhập kho thủ công** — chọn SKU, số lượng, đơn giá, lý do (Nhập mua / Trả hàng / Điều chỉnh / Khác), ghi chú                  |
| FR-05-03 | P0          | **Xuất kho thủ công** — số lượng, lý do (Hỏng / Mất / Biếu tặng / Điều chỉnh / Khác)                                           |
| FR-05-04 | P0          | **Kiểm kho** — tạo phiếu, nhập số đếm thực tế, hệ thống tính lệch, xác nhận điều chỉnh                                         |
| FR-05-05 | P0          | **Sổ biến động tồn** — mọi thay đổi có: thời gian, SKU, loại nghiệp vụ, số lượng trước/sau, chứng từ liên quan, người thao tác |
| FR-05-06 | P0          | Lọc sổ biến động theo SKU, loại nghiệp vụ, khoảng thời gian; export Excel                                                      |
| FR-05-07 | P0          | Import nhập kho hàng loạt qua Excel                                                                                            |
| FR-05-08 | P1          | Kiểm kho theo nhóm SKU / danh mục                                                                                              |
| FR-05-09 | ~~P2~~ → P0 | **Đa kho** — đã nâng lên Phase 1 (07/08/2026), xem nhóm FR-05-20…FR-05-27 dưới đây                                             |
| FR-05-10 | P2          | Dự trữ chương trình khuyến mãi (Phase 2) — schema sẵn trạng thái `reserved`                                                    |

**Đa kho, vị trí kệ, lô và hạn dùng** _(kéo từ M17 Phase 2 về Phase 1 — lý do ghi ở M17)_

| ID       | Ưu tiên | Yêu cầu                                                                                                               |
| -------- | ------- | --------------------------------------------------------------------------------------------------------------------- |
| FR-05-20 | P0      | **Danh sách kho** — mã, tên, địa chỉ, kho mặc định, số SKU và giá trị tồn theo kho                                    |
| FR-05-21 | P0      | **Vị trí kệ** (`bin_code`) trong từng kho, gán SKU vào vị trí — quyết định thứ tự nhặt hàng                           |
| FR-05-22 | P0      | Tồn tách theo từng kho: Tồn tổng / Khả dụng / Đã khoá theo `warehouse_id`                                             |
| FR-05-23 | P0      | **Phiếu chuyển kho** — kho đi, kho đến, danh sách SKU, trạng thái Nháp → Đang chuyển → Đã nhận                        |
| FR-05-24 | P0      | **Lô hàng** — số lô, ngày sản xuất, hạn sử dụng, số lượng còn theo lô                                                 |
| FR-05-25 | P0      | Bốn trạng thái hạn dùng (Hết hạn / Bị chặn bán / Sắp hết hạn / Còn hạn), chặn xuất lô đã hết hạn                      |
| FR-05-26 | P0      | Đẩy tồn lên sàn theo **tổng khả dụng của các kho được cấu hình bán cho gian hàng đó**, không chia chi tiết theo kệ    |
| FR-05-27 | P0      | **Tồn theo tốc độ bán** — số ngày tồn còn lại tính từ sản lượng bán thực tế, cảnh báo tự tính thay cho ngưỡng cố định |

**Đẩy tồn lên sàn**

| ID       | Ưu tiên | Yêu cầu                                                                                                   |
| -------- | ------- | --------------------------------------------------------------------------------------------------------- |
| FR-05-11 | P0      | **Bật/tắt đẩy tồn** theo từng gian hàng                                                                   |
| FR-05-12 | P0      | **Tỉ lệ đẩy (%)** — đẩy n% tồn khả dụng lên sàn, cấu hình theo shop                                       |
| FR-05-13 | P0      | **Tồn an toàn** — trừ một lượng cố định trước khi đẩy, cấu hình theo shop hoặc theo SKU                   |
| FR-05-14 | P0      | Đẩy tồn tự động khi có bất kỳ biến động nào                                                               |
| FR-05-15 | P0      | **Nhật ký đẩy tồn** — SKU, shop, giá trị đẩy, kết quả, lý do lỗi, thời gian                               |
| FR-05-16 | P0      | Đẩy tồn thủ công (một SKU / hàng loạt / toàn bộ shop)                                                     |
| FR-05-17 | P0      | Tự retry khi đẩy thất bại; cảnh báo nếu thất bại quá 3 lần liên tiếp                                      |
| FR-05-18 | P1      | **Gian hàng ưu tiên** — khi tồn khả dụng dưới ngưỡng, shop ưu tiên giữ nguyên tồn, các shop khác đẩy về 0 |
| FR-05-19 | P1      | Xem trước tác động trước khi bật đẩy tồn lần đầu                                                          |

### Quy tắc nghiệp vụ

- **BR-05-01:** Số lượng đẩy lên sàn = `FLOOR((Khả dụng − Tồn an toàn) × Tỉ lệ đẩy)`, tối thiểu 0.
- **BR-05-02:** SKU chưa ghép nối → không tham gia đẩy tồn.
- **BR-05-03:** Combo SKU → đẩy theo `MIN(khả dụng SKU con ÷ số lượng)`; khi SKU con biến động, mọi combo chứa nó phải được đẩy lại.
- **BR-05-04:** Đẩy tồn là **một chiều** (ViShop → sàn). Sửa tồn trên Seller Center không được coi là nguồn sự thật và sẽ bị ghi đè ở lần đẩy tiếp theo. Phải cảnh báo rõ điều này khi bật tính năng.
- **BR-05-05:** Tồn khả dụng không bao giờ âm. Nếu phát sinh tình huống âm (đơn về sau khi đã hết hàng), ghi nhận đơn vào danh sách "Thiếu tồn" và giữ tồn ở 0.
- **BR-05-06:** Thời điểm khoá tồn cấu hình được ở cấp tenant: **khi đơn được tạo** hoặc **khi đơn được thanh toán**. Mặc định: khi đơn được tạo.
- **BR-05-07:** Số đẩy lên sàn tính trên **tổng khả dụng của tất cả các kho**, không chia theo kho. Lý do: đa số seller Việt Nam giao hàng từ bất kỳ kho nào tuỳ hàng còn ở đâu — chia tồn theo kho chỉ làm mất đơn ở kho hết hàng trong khi kho khác vẫn còn. Kho của TikTok lấy về chỉ để đối chiếu, **không map 1-1 với kho vật lý** của seller.
- **BR-05-08:** Lô ở trạng thái Hết hạn hoặc Bị chặn bán vẫn nằm trong Tồn tổng nhưng **không tính vào Khả dụng** và không được ghép vào đơn. Ngưỡng chặn có hai nguồn và **phải phân biệt được**: hàng nằm trong kho TikTok lấy ngưỡng từ chính sách hạn dùng của sàn (chặn cứng, được nói với seller là "sàn không cho bán"); hàng seller tự giữ dùng ngưỡng mặc định của ViShop, seller sửa được và chỉ được trình bày như cảnh báo nội bộ. Lý do là API công bố ngưỡng hạn dùng chỉ phủ hàng FBT — xem mục 2 dòng #5 của Capability Matrix.
- **BR-05-09:** Chuyển kho trừ tồn ở kho đi ngay khi xuất, chỉ cộng vào kho đến khi có xác nhận nhận hàng. Phần chênh trong lúc vận chuyển nằm ở trạng thái Đang chuyển — không biến mất khỏi tổng, cũng không được đếm hai lần.

### Acceptance Criteria

- [ ] Given SKU A khả dụng 10, ghép nối với listing ở 3 shop, tỉ lệ đẩy 100%, tồn an toàn 0, when đơn 2 cái về ở shop 1, then trong ≤30 giây tồn đẩy lên cả 3 shop là 8
- [ ] Given tỉ lệ đẩy 80%, tồn an toàn 5, khả dụng 100, then số đẩy lên sàn = FLOOR((100−5)×0.8) = 76
- [ ] Given khả dụng về 0, then mọi listing ghép nối được đẩy về 0 trong ≤30 giây
- [ ] Given đẩy tồn shop A thất bại 3 lần, then hệ thống gửi cảnh báo và đánh dấu shop cần kiểm tra, không âm thầm bỏ qua
- [ ] Given đơn huỷ ở trạng thái chưa xuất kho, then tồn Đã khoá trả về Khả dụng và kích hoạt đẩy tồn
- [ ] Given kiểm kho phát hiện lệch −3, when xác nhận, then Tồn tổng giảm 3, sổ biến động ghi nghiệp vụ "Điều chỉnh kiểm kho" kèm người thao tác
- [ ] Given SKU A khả dụng 4 ở kho Hà Nội và 6 ở kho HCM, then số đẩy lên sàn là 10, không phải 4 hay 6
- [ ] Given một lô hết hạn còn 20 cái, then 20 cái đó vẫn hiện trong Tồn tổng nhưng không cộng vào Khả dụng, và không được chọn khi ghép đơn
- [ ] Given phiếu chuyển kho 15 cái từ kho A sang kho B đang ở trạng thái Đang chuyển, then kho A đã giảm 15, kho B chưa tăng, tổng khả dụng toàn hệ thống giảm đúng 15 — không giảm 30, không giữ nguyên

---

## M06 — Đơn hàng

**Mục đích:** Xử lý toàn bộ vòng đời đơn của mọi gian hàng trong một màn hình.

### Vòng đời đơn

```
Chờ xác nhận  →  Chờ lấy mã vận đơn  →  Chờ in  →  Chờ bàn giao  →  Đang giao  →  Hoàn thành
                                                                                    ↘
Nhánh phụ:  Thiếu tồn · Chưa ghép nối SKU · Tạm giữ · Đơn bất thường · Đã huỷ · Đang hoàn
```

### User Stories

- Là nhân viên vận hành, tôi muốn thấy đơn của tất cả shop trong một danh sách để không phải chuyển tab.
- Là nhân viên vận hành, tôi muốn xử lý hàng loạt hàng trăm đơn trong vài thao tác.
- Là chủ shop, tôi muốn tạm giữ đơn nghi ngờ trước khi giao.

### Yêu cầu chức năng

**Danh sách & tìm kiếm**

| ID       | Ưu tiên | Yêu cầu                                                                                                  |
| -------- | ------- | -------------------------------------------------------------------------------------------------------- |
| FR-06-01 | P0      | Danh sách đơn hợp nhất đa gian hàng, phân theo tab trạng thái, có badge đếm                              |
| FR-06-02 | P0      | Lọc: gian hàng, trạng thái, khoảng thời gian, SKU, mã đơn, mã vận đơn, người mua, cờ đánh dấu, nguồn đơn |
| FR-06-03 | P0      | Tìm kiếm nhanh theo mã đơn / mã vận đơn / SĐT / tên người nhận                                           |
| FR-06-04 | P0      | **Bộ lọc lưu được** và đặt tên, ghim lên đầu                                                             |
| FR-06-05 | P0      | Tuỳ chỉnh cột hiển thị, kéo đổi thứ tự, ghim cột trái                                                    |
| FR-06-06 | P0      | Export danh sách đơn ra Excel theo bộ lọc hiện tại                                                       |
| FR-06-07 | P1      | Chế độ xem gọn / xem chi tiết (hiện đủ dòng sản phẩm)                                                    |

**Chi tiết đơn**

| ID       | Ưu tiên | Yêu cầu                                                                                                             |
| -------- | ------- | ------------------------------------------------------------------------------------------------------------------- |
| FR-06-08 | P0      | Thông tin: mã đơn sàn, gian hàng, thời gian đặt/thanh toán, trạng thái, người nhận, địa chỉ, phương thức thanh toán |
| FR-06-09 | P0      | Dòng sản phẩm: ảnh, tên, biến thể, SKU sàn, SKU kho đã ghép, số lượng, đơn giá, chiết khấu                          |
| FR-06-10 | P0      | Khối tài chính: doanh thu, các dòng phí, giá vốn, lợi nhuận ước tính (theo quyền)                                   |
| FR-06-11 | P0      | **Nguồn đơn**: Affiliate / Livestream / Video / Shop tab / Tìm kiếm — kèm creator nếu có                            |
| FR-06-12 | P0      | Lịch sử thao tác trên đơn (ai làm gì lúc nào)                                                                       |
| FR-06-13 | P0      | **Ghi chú** tách 2 loại: ghi chú nhặt hàng (in ra phiếu) và ghi chú nội bộ (không in)                               |
| FR-06-14 | P0      | Tra cứu hành trình vận chuyển                                                                                       |

**Xử lý đơn**

| ID       | Ưu tiên | Yêu cầu                                                                                                                                   |
| -------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| FR-06-15 | P0      | Xác nhận đơn hàng loạt                                                                                                                    |
| FR-06-16 | P0      | Lấy mã vận đơn hàng loạt (đa shop trong một lệnh)                                                                                         |
| FR-06-17 | P0      | Đánh dấu đã bàn giao vận chuyển hàng loạt                                                                                                 |
| FR-06-18 | P0      | **Tạm giữ / bỏ tạm giữ** đơn — đơn tạm giữ không vào luồng xử lý, tồn vẫn khoá                                                            |
| FR-06-19 | P0      | **Cờ đánh dấu (mark)** nhiều màu, đặt tên tuỳ ý, gắn/gỡ hàng loạt                                                                         |
| FR-06-20 | P0      | Danh sách **Thiếu tồn** — đơn không đủ tồn để trừ, kèm SKU thiếu và số lượng thiếu                                                        |
| FR-06-21 | P0      | Danh sách **Đơn bất thường** — lỗi đồng bộ, lệch dữ liệu, thiếu thông tin                                                                 |
| FR-06-22 | P0      | Sửa số lượng phân bổ tồn thủ công cho đơn cụ thể                                                                                          |
| FR-06-23 | P1      | **Quy tắc tự động** — tự xác nhận đơn / tự gắn cờ theo điều kiện (shop, giá trị đơn, SKU, nguồn đơn)                                      |
| FR-06-24 | P1      | Chặn xử lý theo điều kiện (giá trị bất thường, SKU nhạy cảm, người mua trong danh sách theo dõi)                                          |
| FR-06-25 | P2      | Tách đơn — schema `parent_order_id` phải có sẵn từ Phase 1                                                                                |
| FR-06-28 | P0      | **Máy trạng thái đóng gói** — 9 bước nội bộ của một kiện, suy ra từ trạng thái sàn cộng dữ liệu vận hành của ViShop                       |
| FR-06-29 | P0      | **Ba chốt chặn thứ tự**: chưa đủ tồn thì không vào sóng nhặt · chưa RTS thì không có tem để in · chưa in tem thì không cho quét kiểm hàng |
| FR-06-30 | P0      | **Dải tiến trình** trên chi tiết đơn — nhìn ra ngay kiện đang tắc ở bước nào và ai phải làm tiếp                                          |

**Theo dõi vận chuyển** _(hiện thực hoá FR-06-14, vốn chỉ nêu "tra cứu" mà không nói lưu lại)_

| ID       | Ưu tiên | Yêu cầu                                                                                                    |
| -------- | ------- | ---------------------------------------------------------------------------------------------------------- |
| FR-06-31 | P0      | **Hành trình kiện hàng** theo mốc: đã lấy hàng → đang trung chuyển → đang giao → đã giao, lưu lại từng mốc |
| FR-06-32 | P0      | Danh sách kiện đang vận chuyển, lọc theo mốc hành trình và theo đơn vị vận chuyển                          |
| FR-06-33 | P0      | **Phát hiện kiện bất thường** — quá lâu không cập nhật mốc, giao thất bại, đang chuyển hoàn                |
| FR-06-34 | P0      | Màn xử lý kiện bất thường: gán người phụ trách, ghi hướng xử lý, đóng vụ việc                              |
| FR-06-35 | P1      | Thống kê thời gian giao trung bình theo đơn vị vận chuyển và theo khu vực                                  |
| FR-06-36 | P1      | Đẩy cảnh báo sang trung tâm thông báo khi kiện chuyển sang trạng thái bất thường                           |

### Quy tắc nghiệp vụ

- **BR-06-01:** Trạng thái đơn trong ViShop **luôn theo sàn làm chuẩn**. Nếu lệch, đồng bộ lại theo sàn và ghi vào Đơn bất thường.
- **BR-06-02:** Đơn ở trạng thái "Chưa thanh toán" không trừ tồn và không tính vào báo cáo doanh thu.
- **BR-06-03:** Đơn thiếu tồn **vẫn cho phép in tem và giao**, nhưng ghi nhận tồn âm ảo và cảnh báo. Không chặn vận hành.
- **BR-06-04:** Đơn tạm giữ vẫn giữ tồn ở trạng thái Đã khoá.
- **BR-06-05:** Không cho sửa dữ liệu gốc của đơn từ sàn (giá, sản phẩm, người mua). Chỉ cho thêm dữ liệu nội bộ: ghi chú, cờ, ghép nối SKU, phân bổ tồn.
- **BR-06-09:** Bước đóng gói nội bộ **không đọc thẳng từ trạng thái sàn**. Trạng thái sàn chỉ nói kiện đã bàn giao hay chưa, không nói đã nhặt, đã in tem hay đã quét. Bước hiện tại suy ra tại thời điểm đọc từ trạng thái sàn cộng dữ liệu vận hành — cố tình không lưu thành một cột, vì cột lưu sẵn sẽ lệch ngay lần đầu webhook sàn về trước khi việc nội bộ chạy xong, và lúc đó không có cách nào biết cột hay dữ liệu nguồn mới đúng.
- **BR-06-10:** Mốc hành trình vận chuyển **không phải dữ liệu realtime** — nhịp cập nhật do đơn vị vận chuyển quyết định. Gọi dày không làm dữ liệu mới hơn, chỉ đốt hạn mức API: nhịp gọi giãn dần theo tuổi kiện, dừng hẳn khi kiện đã giao.

### Acceptance Criteria

- [ ] Given 5 shop có tổng 300 đơn ở trạng thái Chờ xác nhận, when chọn tất cả và xác nhận, then hệ thống xử lý theo lô, hiển thị tiến độ, và báo cáo riêng đơn nào thất bại kèm lý do
- [ ] Given một đơn thất bại khi lấy mã vận đơn, then các đơn còn lại trong lô vẫn được xử lý bình thường
- [ ] Given đơn có SKU thiếu tồn, when mở danh sách Thiếu tồn, then thấy đơn kèm SKU và số lượng còn thiếu
- [ ] Given đơn bị tạm giữ, then không xuất hiện trong danh sách chờ in và không thể lấy mã vận đơn
- [ ] Given đơn được đồng bộ lại từ sàn, then không tạo bản ghi trùng, chỉ cập nhật trạng thái và ghi log

---

## M07 — In ấn & Chứng từ

**Mục đích:** In tem hàng loạt xuyên gian hàng — đây là thao tác chiếm nhiều thời gian nhất của nhân viên vận hành mỗi sáng.

### User Stories

- Là nhân viên vận hành, tôi muốn in tem cho 300 đơn của 5 shop bằng một lệnh, ra một file duy nhất.
- Là nhân viên kho, tôi muốn phiếu nhặt hàng gom theo SKU để đi một vòng kho là đủ.

### Yêu cầu chức năng

| ID       | Ưu tiên | Yêu cầu                                                                                                                                           |
| -------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| FR-07-01 | P0      | **In tem vận đơn hàng loạt xuyên gian hàng** — một lệnh, một file PDF gộp                                                                         |
| FR-07-02 | P0      | Chọn thứ tự in: theo thời gian đặt / theo SKU / theo gian hàng                                                                                    |
| FR-07-03 | P0      | **Phiếu nhặt hàng (Pick List)** — gom theo SKU với tổng số lượng, hoặc theo đơn                                                                   |
| FR-07-04 | P0      | **Phiếu đóng gói (Pack List)** — theo đơn, có ghi chú nhặt hàng                                                                                   |
| FR-07-05 | P0      | **Phiếu tổng hợp** — tổng SKU và số lượng theo lô in, phục vụ đối chiếu                                                                           |
| FR-07-06 | P0      | Đánh dấu đơn **đã in**, tách tab Chưa in / Đã in                                                                                                  |
| FR-07-07 | P0      | **In lại** trong vòng 30 ngày kể từ ngày lấy mã vận đơn                                                                                           |
| FR-07-08 | P0      | **Nhật ký in** — ai in, lúc nào, bao nhiêu đơn, lô nào                                                                                            |
| FR-07-09 | P0      | Xử lý lỗi từng đơn trong lô: không chặn cả lô, báo riêng đơn lỗi kèm lý do                                                                        |
| FR-07-10 | P1      | **Template phiếu tuỳ biến** — logo, thông tin shop, chọn trường hiển thị, khổ giấy (A4 / A5 / K80 / K57)                                          |
| FR-07-11 | P1      | **Tem SKU** — in mã vạch SKU kho để dán lên hàng hoá                                                                                              |
| FR-07-12 | P1      | Biên bản bàn giao vận chuyển (danh sách kiện giao cho tài xế, có chữ ký)                                                                          |
| FR-07-13 | P1      | Công cụ in tốc độ cao (in trực tiếp không qua hộp thoại trình duyệt)                                                                              |
| FR-07-15 | P0      | **Sóng nhặt hàng** — gom nhiều kiện thành một sóng, trạng thái Nháp → Đang nhặt → Đã nhặt xong                                                    |
| FR-07-16 | P0      | Sóng nhặt sắp theo vị trí kệ để người kho đi một vòng kho, không đi lại. Sóng có kiện thuộc nhiều kho phải cảnh báo và tách theo kho trước khi in |
| FR-07-17 | P0      | Thứ tự in mặc định **theo vị trí kệ của sóng**, không theo thời gian đặt đơn — bổ sung tuỳ chọn thứ ba cho FR-07-02                               |

### Quy tắc nghiệp vụ

- **BR-07-01:** Tem vận đơn phải là **tem gốc do TikTok Shop cấp**, không tự vẽ lại, không chỉnh sửa nội dung tem.
- **BR-07-05:** Một kiện chỉ thuộc **một sóng nhặt đang mở** tại một thời điểm. Nếu không, hai người kho sẽ cùng đi nhặt một kiện — chuyện xảy ra thật trong ca cao điểm.
- **BR-07-06:** Chỉ in tem được cho kiện **đã RTS thành công**. Trước khi RTS, sàn chưa cấp mã vận đơn nên không có tem để in — đây là thứ tự bắt buộc, không phải khuyến nghị.
- **BR-07-02:** Lô in tối đa 500 đơn/lệnh. Vượt quá → hệ thống tự chia lô.
- **BR-07-03:** Tem đã lấy được lưu cache 30 ngày. Sau 30 ngày, hệ thống thử lấy lại từ sàn; nếu sàn không còn thì báo không in lại được.
- **BR-07-04:** Đơn đã đánh dấu "đã in" vẫn in lại được, nhưng phải ghi nhận vào nhật ký in với cờ "in lại".

### Acceptance Criteria

- [ ] Given 200 đơn ở 5 shop đang ở trạng thái Chờ in, when bấm In tem, then trả về một file PDF gộp trong ≤60 giây
- [ ] Given trong lô có 3 đơn lấy tem thất bại, then 197 đơn còn lại vẫn ra file, và 3 đơn kia hiển thị riêng kèm lý do
- [ ] Given in phiếu nhặt hàng chế độ gom theo SKU cho 200 đơn, then phiếu liệt kê mỗi SKU một dòng với tổng số lượng, sắp xếp theo mã SKU
- [ ] Given user A in 200 đơn lúc 8:15, then nhật ký in ghi lại đúng user, thời gian, số đơn, và danh sách mã đơn
- [ ] Given một sóng nhặt 40 kiện trong cùng kho, then phiếu in ra theo đúng thứ tự vị trí kệ tăng dần, không theo thời gian đặt đơn
- [ ] Given một kiện đã nằm trong sóng đang nhặt, when người khác gom nó vào sóng mới, then hệ thống từ chối và chỉ ra sóng đang giữ kiện đó
- [ ] Given một kiện chưa RTS, when chọn in tem, then hệ thống chặn và nói rõ phải RTS trước, không trả về file rỗng

---

## M08 — Quét mã & Kiểm hàng

**Mục đích:** Chặn giao nhầm hàng ở bước cuối — lỗi đắt nhất trong vận hành.

### User Stories

- Là nhân viên kho, tôi muốn quét mã vận đơn rồi quét sản phẩm, nếu sai thì máy báo ngay.
- Là chủ shop, tôi muốn biết ai đã quét đơn nào để truy trách nhiệm khi có khiếu nại.

### Yêu cầu chức năng

| ID       | Ưu tiên | Yêu cầu                                                                                                                                            |
| -------- | ------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| FR-08-01 | P0      | **Quét giao hàng** — quét mã vận đơn → hiển thị đơn → xác nhận bàn giao                                                                            |
| FR-08-02 | P0      | **Quét kiểm hàng** — quét mã vận đơn → quét mã vạch từng sản phẩm → hệ thống đối chiếu đủ/thiếu/sai                                                |
| FR-08-03 | P0      | Cảnh báo âm thanh + màu sắc khi quét sai hoặc thừa                                                                                                 |
| FR-08-04 | P0      | Chặn hoàn tất khi chưa quét đủ sản phẩm của đơn                                                                                                    |
| FR-08-05 | P0      | Hỗ trợ máy quét USB (hoạt động như bàn phím)                                                                                                       |
| FR-08-06 | P0      | Nhật ký quét: người quét, thời gian, kết quả, đơn nào                                                                                              |
| FR-08-07 | P1      | Cấu hình thời gian chờ giữa hai lần quét liên tiếp                                                                                                 |
| FR-08-08 | P1      | Chế độ quét nhanh: không cần xác nhận, tự chuyển đơn tiếp theo                                                                                     |
| FR-08-09 | P1      | **Quét kèm cân** — ghi nhận cân nặng thực tế, cảnh báo lệch so với cân nặng dự kiến                                                                |
| FR-08-10 | P0      | **Chế độ quét toàn màn hình** — chữ to, không sidebar, phản hồi màu chiếm hết khung nhìn. Người kho đứng cách màn hình 1–2 mét và không dùng chuột |
| FR-08-11 | P0      | Một màn quét làm cả hai việc (kiểm hàng và bàn giao), tự nhận loại việc theo bước hiện tại của kiện                                                |

### Quy tắc nghiệp vụ

- **BR-08-01:** Quét kiểm hàng chỉ áp dụng cho đơn có 100% SKU đã ghép nối và có mã vạch. Đây là **phạm vi áp dụng, không phải hàng rào**: kiện nằm ngoài phạm vi thì bỏ qua cổng quét và vẫn RTS được theo BR-04-01, kèm cảnh báo `scan_gate_skipped_unmapped` để vận hành biết. Riêng vế "có mã vạch" không được dùng để tắt cổng — dòng thiếu mã vạch cho **xác nhận tay** tại bàn quét, nếu không thì một SKU quên khai mã vạch sẽ âm thầm vô hiệu hoá cả cổng chất lượng.
- **BR-08-02:** Đơn quét thiếu/sai không được chuyển sang trạng thái đã bàn giao.
- **BR-08-03:** Mọi lần quét đều ghi log kể cả quét sai — phục vụ truy vết khiếu nại.
- **BR-08-04:** Quét kiểm hàng đứng **trước** khi gọi lệnh giao, tức trước cả tem. Ở Đông Nam Á tem chỉ có sau khi `Ship Package` thành công, mà cam kết với hãng rồi mới phát hiện thiếu hàng thì Việt Nam không huỷ lại được sau `AWAITING_COLLECTION`. Hệ quả: bàn quét mở kiện bằng **mã kiện**, không phải mã vận đơn. _(Sửa 28/08/2026 — bản trước ghi ngược: "chỉ quét được kiện đã in tem". Nguồn chuẩn: `docs/spec/02` §2 và `docs/spec/31`.)_

### Acceptance Criteria

- [ ] Given đơn gồm 2×SKU-A và 1×SKU-B, when quét đủ 3 mã đúng, then đơn chuyển trạng thái và phát âm thanh thành công
- [ ] Given quét nhầm SKU-C, then hệ thống báo lỗi ngay, không ghi nhận, và không cho hoàn tất
- [ ] Given quét 2×SKU-A rồi bỏ dở, then đơn giữ nguyên trạng thái cũ, phiên quét được lưu để tiếp tục
- [ ] Given một kiện chưa in tem, when quét mã vận đơn ở màn kiểm hàng, then hệ thống chặn và chỉ ra bước còn thiếu, không mở phiên quét

---

## M09 — Sau bán & Hoàn hàng

**Mục đích:** Theo dõi đơn hoàn và đưa hàng hoàn trở lại kho đúng số lượng, đúng giá vốn.

### User Stories

- Là chủ shop, tôi muốn biết đơn nào đang hoàn để không mất hàng.
- Là nhân viên kho, tôi muốn quét mã để nhập hàng hoàn về kho nhanh chóng.
- Là kế toán, tôi muốn biết tiền hoàn đã bị trừ đúng hay chưa.

### Yêu cầu chức năng

| ID       | Ưu tiên | Yêu cầu                                                                                                                              |
| -------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| FR-09-01 | P0      | Đồng bộ yêu cầu hoàn/huỷ từ TikTok Shop, cập nhật trạng thái tự động                                                                 |
| FR-09-02 | P0      | Danh sách đơn hoàn: mã đơn, shop, lý do, SKU, số lượng, số tiền hoàn, trạng thái                                                     |
| FR-09-03 | P0      | **Nhập kho hàng hoàn** — thủ công hoặc bằng quét mã vận đơn                                                                          |
| FR-09-04 | P0      | Phân loại khi nhập: **Hàng tốt** (về tồn khả dụng) / **Hàng lỗi** (về tồn hàng lỗi, không đẩy sàn)                                   |
| FR-09-05 | P0      | Cảnh báo đơn hoàn đã quá X ngày mà chưa nhập kho                                                                                     |
| FR-09-06 | P0      | Điều chỉnh doanh thu và lợi nhuận khi đơn hoàn hoàn tất                                                                              |
| FR-09-07 | P1      | Thống kê tỉ lệ hoàn theo SKU, theo shop, theo creator, theo lý do                                                                    |
| FR-09-08 | P1      | Nhập tay đơn hoàn cho trường hợp sàn không đẩy về                                                                                    |
| FR-09-09 | P0      | **Duyệt/Từ chối yêu cầu huỷ** ngay trên màn FR-09-02, kèm đồng hồ đếm ngược tới hạn 24h và cảnh báo trước hạn _(bổ sung 07/08/2026)_ |

### Quy tắc nghiệp vụ

- **BR-09-01:** Hàng hoàn **không tự động** cộng lại tồn. Bắt buộc có thao tác nhập kho — vì hàng có thể chưa về, hoặc về trong tình trạng lỗi.
- **BR-09-02:** Giá vốn khi nhập hàng hoàn = giá vốn tại thời điểm xuất kho của đơn gốc, không phải giá vốn hiện tại.
- **BR-09-03:** Tồn hàng lỗi tách riêng, không tham gia đẩy tồn lên sàn.
- **BR-09-04:** Đơn hoàn toàn phần → toàn bộ doanh thu và phí của đơn bị đảo. Hoàn một phần → đảo theo tỉ lệ dòng sản phẩm bị hoàn.
- **BR-09-05** _(bổ sung 07/08/2026)_: TikTok bắt seller trả lời yêu cầu huỷ trong 24 giờ; quá hạn sàn tự duyệt và hoàn tiền cho khách. Đồng hồ đếm ngược tính từ mốc phát sinh yêu cầu theo webhook trả về, không tính từ lúc ViShop nhận được. Thao tác ghi sang sàn nên luôn cần người duyệt (mức ba của FR-14-09), không tự động hoá. Kiện đã chuyển sang bước bàn giao thì mặc định gợi ý Từ chối kèm lý do, nhưng người dùng vẫn quyết định cuối cùng.

### Acceptance Criteria

- [ ] Given đơn hoàn về, when quét mã vận đơn ở màn hình nhập hàng hoàn, then hiển thị đúng SKU và số lượng cần nhập
- [ ] Given nhập hàng hoàn 2 cái loại Hàng tốt, then tồn khả dụng tăng 2 và đẩy tồn được kích hoạt
- [ ] Given nhập 1 cái loại Hàng lỗi, then tồn hàng lỗi tăng 1, tồn khả dụng không đổi
- [ ] Given đơn hoàn hoàn tất, then báo cáo lợi nhuận của kỳ tương ứng được điều chỉnh giảm
- [ ] Given yêu cầu huỷ còn trong hạn 24h, when người vận hành bấm Duyệt hoặc Từ chối trên màn đơn hoàn, then trạng thái ghi sang TikTok Shop và đồng hồ đếm ngược biến mất
- [ ] Given yêu cầu huỷ sắp hết hạn 24h mà chưa xử lý, then hệ thống phát cảnh báo P0 qua trung tâm cảnh báo (FR-14-02)

---

## M10 — Chi phí & Lợi nhuận

**Mục đích:** Module tạo khác biệt cốt lõi. Trả lời câu hỏi "đơn này lãi bao nhiêu" ngay trong ngày.

### Mô hình chi phí

```
Lợi nhuận đơn = Doanh thu thực
              − Giá vốn hàng bán
              − Σ (các dòng chi phí)

Doanh thu thực = Tổng tiền hàng − Chiết khấu do shop chịu
Các dòng chi phí (line-item động):
  · Hoa hồng sàn (commission)
  · Hoa hồng affiliate
  · Phí thanh toán
  · Phần shop đối ứng voucher
  · Phần shop đối ứng phí vận chuyển
  · Phí vận chuyển thực trả
  · Chi phí quảng cáo phân bổ        [P1]
  · Chi phí đóng gói / vận hành      [P1, phân bổ theo cấu hình]
  · Chi phí khác do người dùng khai
```

### User Stories

- Là chủ shop, tôi muốn thấy lãi ước tính ngay trên dòng đơn, không phải mở từng đơn.
- Là chủ shop, tôi muốn biết SKU nào đang bán lỗ để dừng hoặc tăng giá.
- Là kế toán, tôi muốn thêm chi phí phát sinh vào để ra lợi nhuận đúng.

### Yêu cầu chức năng

| ID       | Ưu tiên | Yêu cầu                                                                                                                                  |
| -------- | ------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| FR-10-01 | P0      | **Bóc tách chi phí theo đơn** — mỗi loại phí là một dòng riêng, không gộp                                                                |
| FR-10-02 | P0      | Lợi nhuận và biên lợi nhuận hiển thị trên danh sách đơn (theo quyền)                                                                     |
| FR-10-03 | P0      | Đánh dấu trạng thái dữ liệu: **Ước tính** (trước payout) / **Đã đối soát** (sau payout)                                                  |
| FR-10-04 | P0      | Cảnh báo trực quan **đơn lỗ**                                                                                                            |
| FR-10-05 | P0      | Cảnh báo **thiếu dữ liệu** khi SKU chưa có giá vốn hoặc chưa ghép nối — không hiển thị lợi nhuận sai                                     |
| FR-10-06 | P0      | **Báo cáo lợi nhuận theo gian hàng** — doanh thu, giá vốn, từng nhóm phí, lợi nhuận, biên                                                |
| FR-10-07 | P0      | **Báo cáo lợi nhuận theo SKU** — kèm số lượng bán, tỉ lệ hoàn                                                                            |
| FR-10-08 | P0      | **Báo cáo lợi nhuận theo ngày** — biểu đồ xu hướng                                                                                       |
| FR-10-09 | P0      | Export tất cả báo cáo lợi nhuận ra Excel                                                                                                 |
| FR-10-10 | P1      | **Chi phí khác** — khai báo chi phí cố định theo kỳ (thuê kho, lương, phần mềm) và phân bổ vào lợi nhuận shop theo doanh thu hoặc số đơn |
| FR-10-11 | P1      | Phân bổ chi phí quảng cáo vào lợi nhuận SKU                                                                                              |
| FR-10-12 | P1      | So sánh lợi nhuận giữa các kỳ                                                                                                            |

### Quy tắc nghiệp vụ

- **BR-10-01:** Trước khi có payout, lợi nhuận là **ước tính** — dùng biểu phí cấu hình. Sau payout, thay bằng số thực và đánh dấu "Đã đối soát".
- **BR-10-02:** Nếu bất kỳ SKU nào trong đơn thiếu giá vốn → **không hiển thị con số lợi nhuận**, hiển thị "Thiếu dữ liệu" kèm link xử lý. Tuyệt đối không hiển thị con số sai.
- **BR-10-03:** Giá vốn dùng để tính là giá vốn **tại thời điểm xuất kho**, được đóng băng vào đơn, không thay đổi khi giá vốn SKU thay đổi sau đó.
- **BR-10-04:** Đơn huỷ trước khi giao không tính vào báo cáo lợi nhuận. Đơn hoàn tính vào kỳ mà đơn hoàn hoàn tất.
- **BR-10-05:** Hoa hồng affiliate là một dòng phí độc lập, không được gộp vào hoa hồng sàn.

### Acceptance Criteria

- [ ] Given đơn hoàn thành với đủ giá vốn, then chi tiết đơn hiển thị bảng: doanh thu, giá vốn, từng dòng phí có tên và số tiền, lợi nhuận, biên lợi nhuận %
- [ ] Given một SKU trong đơn chưa có giá vốn, then ô lợi nhuận hiển thị "Thiếu giá vốn" và link tới SKU đó
- [ ] Given đơn có hoa hồng affiliate 15%, then hoa hồng affiliate là dòng riêng, tách khỏi hoa hồng sàn
- [ ] Given giá vốn SKU A đổi từ 50k lên 60k hôm nay, then đơn đã xuất kho hôm qua vẫn giữ giá vốn 50k
- [ ] Given báo cáo lợi nhuận theo SKU tháng 7, then tổng lợi nhuận khớp với tổng lợi nhuận theo đơn của cùng kỳ

---

## M11 — Affiliate & Livestream

**Mục đích:** Trả lời hai câu hỏi không công cụ nào trên thị trường trả lời được: creator nào thực sự có lãi, và phiên live nào có lãi.

### User Stories

- Là chủ shop, tôi muốn biết creator nào mang doanh thu cao nhưng lợi nhuận âm sau hoa hồng.
- Là chủ shop, tôi muốn biết creator nào có tỉ lệ hoàn hàng bất thường.
- Là chủ shop, tôi muốn biết một phiên livestream 4 tiếng cuối cùng lãi bao nhiêu.

### Yêu cầu chức năng

**Affiliate**

| ID       | Ưu tiên | Yêu cầu                                                                                                                           |
| -------- | ------- | --------------------------------------------------------------------------------------------------------------------------------- |
| FR-11-01 | P0      | Đồng bộ dữ liệu affiliate từ TikTok Shop: creator, hoa hồng theo đơn                                                              |
| FR-11-02 | P0      | **Bảng creator**: tên, số đơn, doanh thu, hoa hồng, tỉ lệ hoa hồng thực, số đơn hoàn, tỉ lệ hoàn, **lợi nhuận ròng sau hoa hồng** |
| FR-11-03 | P0      | Lọc theo shop, khoảng thời gian; sắp xếp theo mọi cột                                                                             |
| FR-11-04 | P0      | Drill-down: click creator → danh sách đơn của creator đó                                                                          |
| FR-11-05 | P0      | **Lợi nhuận theo creator × SKU** — creator nào bán SKU nào có lãi                                                                 |
| FR-11-06 | P0      | Cảnh báo creator có tỉ lệ hoàn > ngưỡng cấu hình                                                                                  |
| FR-11-07 | P0      | Export bảng creator                                                                                                               |
| FR-11-08 | P1      | So sánh hiệu quả creator giữa các kỳ                                                                                              |
| FR-11-09 | P1      | Gắn nhãn creator (Ưu tiên / Theo dõi / Ngừng hợp tác)                                                                             |

**Livestream**

| ID       | Ưu tiên | Yêu cầu                                                                                                        |
| -------- | ------- | -------------------------------------------------------------------------------------------------------------- |
| FR-11-10 | P0      | Nhận diện đơn phát sinh từ livestream, gắn với phiên live                                                      |
| FR-11-11 | P0      | **P&L theo phiên live**: thời lượng, doanh thu, số đơn, số đơn huỷ/hoàn, giá vốn, hoa hồng, **lợi nhuận ròng** |
| FR-11-12 | P0      | Top SKU bán chạy theo phiên                                                                                    |
| FR-11-13 | P1      | So sánh hiệu quả giữa các phiên live                                                                           |
| FR-11-14 | P1      | Ghi chú cho phiên (host là ai, chạy ads bao nhiêu) để đối chiếu                                                |

### Quy tắc nghiệp vụ

- **BR-11-01:** Đơn từ affiliate nhưng chưa có dữ liệu hoa hồng → hiển thị "Chờ dữ liệu hoa hồng", không tính lợi nhuận vội.
- **BR-11-02:** Đơn hoàn phải được trừ khỏi doanh thu và hoa hồng của creator/phiên tương ứng.
- **BR-11-03:** Phiên livestream xác định theo cửa sổ thời gian mà sàn cung cấp. Đơn phát sinh trong X giờ sau khi phiên kết thúc vẫn được gán cho phiên đó (X cấu hình được, mặc định 24 giờ).

### Acceptance Criteria

- [ ] Given creator X mang về 100 đơn doanh thu 50 triệu, hoa hồng 20%, giá vốn 30 triệu, then bảng hiển thị lợi nhuận ròng = 50 − 30 − 10 − các phí khác
- [ ] Given creator Y có tỉ lệ hoàn 35% và ngưỡng cảnh báo 20%, then creator Y được đánh dấu cảnh báo trong bảng
- [ ] Given phiên live ngày 01/08 từ 20:00–24:00, then P&L phiên gồm cả đơn phát sinh tới 24:00 ngày 02/08 theo cấu hình mặc định

---

## M12 — Đối soát tài chính

**Mục đích:** Xác minh sàn trả đúng tiền, và tạo số liệu doanh thu thực dùng được cho kế toán.

### User Stories

- Là kế toán, tôi muốn biết đơn nào sàn đã thanh toán, đơn nào chưa.
- Là kế toán, tôi muốn phát hiện đơn sàn trả thiếu so với dự kiến.
- Là chủ doanh nghiệp, tôi muốn số liệu doanh thu thực theo tháng để kê khai thuế.

### Yêu cầu chức năng

| ID       | Ưu tiên | Yêu cầu                                                                             |
| -------- | ------- | ----------------------------------------------------------------------------------- |
| FR-12-01 | P0      | Đồng bộ dữ liệu quyết toán (settlement/payout) từ TikTok Shop                       |
| FR-12-02 | P0      | **Bảng đối soát theo đơn**: số tiền dự kiến nhận vs số tiền thực nhận vs chênh lệch |
| FR-12-03 | P0      | Trạng thái thanh toán theo đơn: Chưa quyết toán / Đã quyết toán / Lệch              |
| FR-12-04 | P0      | **Danh sách đơn lệch** — lọc riêng, sắp xếp theo mức lệch                           |
| FR-12-05 | P0      | Chi tiết từng dòng phí thực tế mà sàn trừ, so với dòng phí ước tính                 |
| FR-12-06 | P0      | Báo cáo dòng tiền theo kỳ: tổng dự kiến, tổng đã nhận, tổng còn chờ                 |
| FR-12-07 | P0      | Export dữ liệu đối soát ra Excel                                                    |
| FR-12-08 | P1      | Cảnh báo tự động khi tỉ lệ đơn lệch vượt ngưỡng                                     |
| FR-12-09 | P1      | Báo cáo doanh thu thực theo tháng, tách rõ phần sàn đã khấu trừ                     |
| FR-12-10 | P2      | Đối chiếu với sao kê ngân hàng (Phase 2)                                            |

### Quy tắc nghiệp vụ

- **BR-12-01:** Ngưỡng coi là lệch: chênh lệch tuyệt đối > 1.000đ **hoặc** chênh lệch tương đối > 1%. Cấu hình được.
- **BR-12-02:** Khi có dữ liệu payout, các dòng phí ước tính bị thay bằng số thực và đơn chuyển sang "Đã đối soát".
- **BR-12-03:** Dữ liệu đối soát không được sửa tay. Nếu người dùng không đồng ý, chỉ được thêm ghi chú và cờ tranh chấp.

### Acceptance Criteria

- [ ] Given đơn dự kiến nhận 450.000đ, sàn trả 430.000đ, then đơn vào danh sách Lệch với mức lệch −20.000đ và −4,4%
- [ ] Given payout về cho 500 đơn, then 500 đơn đó chuyển trạng thái Đã đối soát và lợi nhuận được cập nhật bằng số thực
- [ ] Given export đối soát tháng 7, then file Excel có đủ: mã đơn, ngày, doanh thu, từng dòng phí thực, tiền thực nhận, chênh lệch

---

## M13 — Báo cáo & Dashboard

**Mục đích:** Đưa toàn bộ dữ liệu đã có thành thứ nhìn được trong 5 giây.

### Yêu cầu chức năng

| ID       | Ưu tiên | Yêu cầu                                                                                                                                   |
| -------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| FR-13-01 | P0      | **Dashboard hôm nay**: doanh thu, số đơn, đơn chờ xử lý, đơn thiếu tồn, lợi nhuận ước tính, so sánh cùng kỳ                               |
| FR-13-02 | P0      | **Cần xử lý ngay** — khối hành động: đơn chờ xác nhận, chờ in, thiếu tồn, chưa ghép nối SKU, shop cần kết nối lại                         |
| FR-13-03 | P0      | **Báo cáo bán hàng**: theo shop / theo SKU / theo ngày, có biểu đồ                                                                        |
| FR-13-04 | P0      | **Báo cáo tồn kho**: giá trị tồn, SKU tồn thấp, SKU tồn lâu không bán                                                                     |
| FR-13-05 | P0      | **Báo cáo đơn hàng**: tổng đơn, đơn hiệu quả (tổng − huỷ), tỉ lệ huỷ, tỉ lệ hoàn                                                          |
| FR-13-06 | P0      | Bộ lọc chung: gian hàng, khoảng thời gian, danh mục SKU                                                                                   |
| FR-13-07 | P0      | Export mọi báo cáo ra Excel                                                                                                               |
| FR-13-08 | P1      | **Báo cáo năng suất nhân sự**: số đơn xử lý / in / quét theo người, theo ngày                                                             |
| FR-13-09 | P1      | Tuỳ biến dashboard — chọn và sắp xếp thẻ hiển thị                                                                                         |
| FR-13-10 | P1      | Báo cáo tự động gửi email hằng ngày / hằng tuần                                                                                           |
| FR-13-11 | P0      | **Việc hôm nay** — trang chủ vận hành, xếp theo _việc phải làm tiếp_, không theo trạng thái đơn                                           |
| FR-13-12 | P1      | **Quản lý lịch báo cáo** — loại báo cáo, tần suất, giờ gửi, người nhận, bộ lọc kèm theo                                                   |
| FR-13-13 | P1      | **Lịch sử gửi báo cáo** — lần gửi gần nhất, kết quả, lần gửi kế tiếp; gửi lỗi phải hiện lý do                                             |
| FR-13-14 | P1      | **Trợ lý ViAI** — khung hội thoại mở từ mọi màn, hỏi bằng tiếng Việt có dấu hoặc không dấu, nhận về thẻ số liệu kèm nút dẫn tới màn xử lý |
| FR-13-15 | P0      | **Danh mục tool khai báo tập trung** — tên hàm, loại `read`/`action`, mô tả cho máy đọc, câu hỏi mẫu; tên hàm đã phát hành thì không đổi  |
| FR-13-16 | P0      | Mỗi câu trả lời nêu **nguồn số liệu và mốc thời gian tính**; câu hỏi không khớp việc nào thì trả lời là chưa làm được                     |
| FR-13-17 | P1      | Sáu việc đọc mở màn: hàng đợi việc · hạn bàn giao · tồn theo tốc độ bán · lô hạn dùng · vận đơn có vấn đề · điểm gian hàng                |

### Quy tắc nghiệp vụ

- **BR-13-01:** Mọi báo cáo phải ghi rõ định nghĩa chỉ số ngay trên giao diện (tooltip). Ví dụ: "Đơn hiệu quả = tổng đơn − đơn huỷ".
- **BR-13-02:** Báo cáo chỉ hiển thị dữ liệu thuộc phạm vi quyền của người dùng.
- **BR-13-03:** Báo cáo tài chính không hiển thị cho vai trò không có quyền `Xem lợi nhuận`.
- **BR-13-04:** Trang chủ vận hành là **Việc hôm nay**, không phải dashboard số liệu. Người vận hành mở phần mềm để làm việc, không để đọc biểu đồ — dashboard số liệu là màn của chủ shop, đặt riêng.
- **BR-13-05:** Chọn loại biểu đồ theo câu hỏi người đọc đang hỏi, không theo thói quen dựng. Tỉ trọng dùng vành khuyên · thành phần của một tổng dùng thanh chia tỉ lệ · xếp hạng tên dài dùng cột ngang · chuỗi thời gian dùng đường/vùng · hai đơn vị khác nhau dùng cột kết hợp đường · dòng tiền rơi rụng dùng biểu đồ thác nước. **Không vẽ đường xu hướng cho chỉ số không có chuỗi lịch sử thật.**
- **BR-13-06:** Trợ lý ViAI không được nói ra con số mà người dùng không đối chiếu được ở màn tương ứng. Mỗi câu trả lời phải nêu nguồn số liệu và mốc thời gian tính, và phải phân biệt số của sàn với số ViShop tự suy. Câu hỏi không thuộc danh mục việc đã khai báo thì trả lời là chưa làm được — cấm chọn việc gần đúng nhất rồi trả số của việc khác. Chi tiết ở [vishop-ai-automation-roadmap.md](./vishop-ai-automation-roadmap.md).

---

## M14 — Cảnh báo & Thông báo

**Mục đích:** Người dùng biết vấn đề trước khi nó thành thiệt hại.

### Yêu cầu chức năng

| ID       | Ưu tiên | Yêu cầu                                                                                                                                                    |
| -------- | ------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| FR-14-01 | P0      | **Trung tâm cảnh báo** trong ứng dụng, phân loại và đánh dấu đã đọc                                                                                        |
| FR-14-02 | P0      | Loại cảnh báo P0: ủy quyền sắp hết hạn · đẩy tồn thất bại · đơn thiếu tồn · SKU chưa ghép nối · tồn dưới ngưỡng · đơn lỗ · đơn lệch đối soát · đồng bộ lỗi |
| FR-14-03 | P0      | Cấu hình bật/tắt từng loại cảnh báo và ngưỡng                                                                                                              |
| FR-14-04 | P0      | **Người nhận cảnh báo** — chọn user nào nhận loại cảnh báo nào                                                                                             |
| FR-14-05 | P0      | Gửi qua email                                                                                                                                              |
| FR-14-06 | P1      | Gửi qua **Telegram / Zalo**                                                                                                                                |
| FR-14-07 | P1      | Gom cảnh báo cùng loại trong khung giờ để tránh spam                                                                                                       |
| FR-14-08 | P0      | **Đề xuất thay vì tự làm** — tool loại `action` chỉ soạn việc sẽ làm, số đối tượng ảnh hưởng và hệ quả; không tự ghi dữ liệu                               |
| FR-14-09 | P1      | **Ba mức tự động**: tự chạy (chỉ đọc) · chạy sau khi bật (ghi nội bộ, hoàn tác được) · luôn cần người duyệt (ghi sang sàn, đụng tiền, không hoàn tác được) |
| FR-14-10 | P1      | **Nhật ký trợ lý** — câu hỏi, tool đã gọi, kết quả, đề xuất và người xác nhận                                                                              |

### Quy tắc nghiệp vụ

- **BR-14-01:** Cảnh báo chỉ gửi tới user có quyền xem dữ liệu liên quan.
- **BR-14-02:** Cảnh báo cùng loại cho cùng đối tượng không gửi lại trong vòng 24 giờ trừ khi mức độ tăng.
- **BR-14-03:** Việc được phép chạy tự động chia ba mức, phân theo **có hoàn tác được không**, không theo độ phức tạp. Mức 1 tự chạy: chỉ đọc, không đổi trạng thái. Mức 2 chạy sau khi người bật: ghi trong ViShop và hoàn tác được. Mức 3 luôn cần người duyệt từng lần: mọi lệnh ghi sang sàn, mọi việc đụng tiền, mọi việc không hoàn tác được — lấy tem, bàn giao vận chuyển, đẩy tồn lên sàn, duyệt hoàn hàng, huỷ đơn.
- **BR-14-04:** Trợ lý chỉ soạn đề xuất cho việc mức 2 và mức 3, không tự thực hiện. Đề xuất phải nêu việc sẽ làm, số đối tượng ảnh hưởng và hệ quả; người dùng xác nhận ở màn thực hiện thì việc mới chạy. Mọi lượt hỏi và mọi lần xác nhận ghi vào nhật ký hệ thống của M01.
- **BR-14-05:** Không gửi dữ liệu người mua (tên, số điện thoại, địa chỉ) ra ngoài hệ thống ViShop khi nối mô hình ngôn ngữ. Trợ lý trả về số đếm và mã đơn; thông tin người mua chỉ hiện ở màn xử lý, nơi đã có phân quyền.

---

## M15 — Gói cước & Billing

**Mục đích:** Thu tiền đúng, minh bạch, không gây bất ngờ cho khách.

### Cấu trúc gói

| Gói               | Gian hàng  | Đơn/tháng  | Tài khoản phụ |
| ----------------- | ---------- | ---------- | ------------- |
| **Miễn phí**      | 1          | 300        | 1             |
| **Cơ bản**        | 3          | 3.000      | 3             |
| **Chuyên nghiệp** | 10         | 15.000     | 10            |
| **Doanh nghiệp**  | 30         | 50.000     | 30            |
| **Tuỳ chỉnh**     | Thoả thuận | Thoả thuận | Thoả thuận    |

### Yêu cầu chức năng

| ID       | Ưu tiên | Yêu cầu                                                              |
| -------- | ------- | -------------------------------------------------------------------- |
| FR-15-01 | P0      | Trang gói cước: giới hạn của gói hiện tại, mức đã dùng, ngày hết hạn |
| FR-15-02 | P0      | **Đồng hồ quota đơn** hiển thị realtime, cảnh báo tại 80% và 95%     |
| FR-15-03 | P0      | Nâng cấp gói giữa kỳ, quy đổi phần thời hạn còn lại                  |
| FR-15-04 | P0      | Mua thêm **gói đơn bổ sung** khi vượt quota trong tháng              |
| FR-15-05 | P0      | Lịch sử giao dịch và hoá đơn                                         |
| FR-15-06 | P0      | Thanh toán: chuyển khoản ngân hàng + cổng thanh toán nội địa         |
| FR-15-07 | P0      | Xuất hoá đơn VAT với thông tin công ty tự khai                       |
| FR-15-08 | P1      | Tự động gia hạn                                                      |
| FR-15-09 | P1      | Chương trình giới thiệu (affiliate của ViShop)                       |

### Quy tắc nghiệp vụ

- **BR-15-01:** Quota tính theo **đơn được đồng bộ về hệ thống lần đầu**. Mỗi đơn chỉ tính một lần, re-sync không tính lại.
- **BR-15-02:** Đơn ở trạng thái huỷ/chưa thanh toán tại lần đồng bộ đầu tiên **không tính quota**.
- **BR-15-03:** Vượt quota → **không chặn đồng bộ đơn**, nhưng chặn các thao tác ghi (lấy mã vận đơn, in tem, đẩy tồn) cho tới khi nâng cấp hoặc mua thêm. _Lý do: chặn đồng bộ sẽ làm sai tồn kho, gây thiệt hại cho khách và mất niềm tin vĩnh viễn._
- **BR-15-04:** Hạ gói chỉ thực hiện được tại thời điểm gia hạn, và phải đưa số shop/tài khoản phụ về đúng giới hạn gói mới trước.
- **BR-15-05:** Hết hạn gói → tài khoản chuyển sang chế độ chỉ đọc trong 15 ngày, sau đó ngừng đồng bộ. Dữ liệu giữ tối thiểu 90 ngày.
- **BR-15-06:** Khách được **export toàn bộ dữ liệu của mình** bất cứ lúc nào, kể cả khi đã hết hạn. Không giữ dữ liệu làm con tin.

---

---

# PHẦN III — PHASE 2: MỞ RỘNG

> Thứ tự triển khai dựa trên nguyên tắc: nhánh nào bảo vệ retention của Phase 1 thì làm trước.

## M16 — Đa sàn

| Ưu tiên | Sàn                   | Lý do                                                      |
| ------- | --------------------- | ---------------------------------------------------------- |
| 1       | **Shopee**            | Sàn lớn nhất VN; seller TikTok gần như luôn có shop Shopee |
| 2       | **Lazada**            | Còn đáng kể, API ổn định                                   |
| 3       | Shopify / WooCommerce | Tệp brand D2C                                              |
| 4       | Tiki                  | Quy mô nhỏ, làm sau                                        |

**Phạm vi:**

- Hoàn thiện connector interface; mỗi sàn là một adapter
- Chuẩn hoá mô hình phí theo từng sàn vào cấu trúc line-item chung
- Xử lý khác biệt vòng đời đơn giữa các sàn (mapping về state machine chung)
- **Dự trữ tồn cho chương trình khuyến mãi** (Shopee Flash Sale, Lazada campaign)
- Báo cáo hợp nhất đa sàn
- In tem xuyên sàn

**Điều kiện khởi động:** TikTok đã đạt ≥100 khách trả tiền và tỉ lệ đơn xử lý trong hệ thống ≥85%. Mở Shopee sớm hơn sẽ biến ViShop thành bản kém hơn của sản phẩm có sẵn.

## M17 — Đa kho & Quản lý kho vật lý

**Cập nhật 07/08/2026 — phần lớn module này đã kéo về Phase 1.** Khi dựng UI thật cho người kho, giả định "1 kho logic, không vị trí kệ" vỡ ngay ở màn đầu tiên: không có vị trí kệ thì phiếu nhặt hàng in ra theo thứ tự ngẫu nhiên, người kho đi đi lại lại trong kho; không có lô và hạn dùng thì không bán được FMCG, mỹ phẩm và thực phẩm chức năng — vốn là nhóm hàng lớn nhất trên TikTok Shop VN. Hai thứ đó không phải tính năng nâng cao, chúng là điều kiện để dùng được phần mềm.

Đã làm ở Phase 1 (chi tiết ở M05, FR-05-20…FR-05-27 và M07, FR-07-15…FR-07-17):

- Nhiều kho, mỗi kho có mã, địa chỉ, kho mặc định
- Khu vực kho và vị trí kệ (`bin_code`), một SKU nhiều vị trí
- Chuyển kho + nhận hàng chuyển kho
- Lô & hạn sử dụng, bốn trạng thái hạn dùng, chặn xuất lô hết hạn
- **Sóng nhặt hàng** — làm sớm hơn dự kiến vì nó là hệ quả trực tiếp của vị trí kệ, không phải một dự án riêng: đã có `bin_code` thì việc sắp thứ tự nhặt là chuyện của một câu lệnh sắp xếp

Còn lại ở Phase 2:

- **Quy tắc phân bổ kho tự động** cho đơn (theo địa chỉ giao, theo shop, theo tồn). Phase 1 đẩy tồn lên sàn theo **tổng available toàn hệ thống** và để người vận hành chọn kho xuất — đúng với cách đa số seller Việt Nam đang làm, và tránh mất đơn khi một kho hết hàng còn kho khác vẫn còn
- Nhập/xuất/kiểm kho qua thiết bị PDA
- **Số seri (SN)** cho hàng điện tử
- Tồn hàng lỗi tách kho riêng

## M18 — Thu mua

- Nhà cung cấp, ghép SKU ↔ nhà cung cấp
- **Gợi ý nhập hàng**: `SL đề xuất = (Chu kỳ đặt + Lead time + Số ngày an toàn) × Dự báo bán/ngày − (Tồn khả dụng + Đang về)`
- Dự báo bán/ngày: trung bình 7/15/30/60/90 ngày hoặc bình quân gia quyền; có hệ số điều chỉnh ngày campaign
- Đơn đặt hàng (PO), nhận hàng nhiều đợt
- Phân bổ chi phí nhập (vận chuyển, thuế) vào giá vốn
- Công nợ nhà cung cấp

## M19 — Vận chuyển 3PL

Kết nối trực tiếp hãng vận chuyển cho đơn ngoài sàn và đơn tự giao:
**GHN · GHTK · Viettel Post · J&T · Ninja Van · VNPost · SPX**

_(GHTK và VNPost là khoảng trống của sản phẩm cạnh tranh hiện có — cần ưu tiên.)_

- So sánh cước trước khi chọn hãng
- Lấy mã vận đơn, in tem, theo dõi hành trình
- Đối soát cước vận chuyển thực trả vs thu của khách

## M20 — Thuế, Hoá đơn & Kế toán

- Tích hợp hoá đơn điện tử: **MISA meInvoice · Viettel S-Invoice · VNPT Invoice**
- Phát hành hoá đơn từ đơn hàng
- Báo cáo doanh thu phục vụ kê khai thuế TMĐT
- Đối chiếu phần sàn đã khấu trừ nộp thay
- Đẩy dữ liệu sang phần mềm kế toán (MISA AMIS, Fast)

## M21 — Quảng cáo

- Đồng bộ dữ liệu TikTok Ads (và Shopee Ads ở giai đoạn sau)
- Dashboard hợp nhất: chi phí, ROAS, chuyển đổi
- **ROAS sau giá vốn** — chỉ số không sản phẩm nào trên thị trường có
- Phân bổ chi phí quảng cáo vào lợi nhuận SKU
- Quy tắc tự động điều chỉnh ngân sách

## M22 — Mobile & PDA

- App xử lý đơn, xem báo cáo, nhận cảnh báo
- Nhập/xuất/kiểm kho trên PDA
- Quét kiểm hàng trên thiết bị di động

## M23 — Open API & Webhook

- REST API cho đơn, sản phẩm, tồn kho, báo cáo
- Webhook đẩy sự kiện ra hệ thống của khách
- Quản lý API key, hạn mức, tài liệu công khai

_Đây là điểm khác biệt so với đối thủ hiện tại — không nên để tới quá muộn._

---

---

# PHẦN IV — VẬN HÀNH SPEC

## Success Metrics

**Leading — đo trong 1–4 tuần sau launch**

| Chỉ số                                      | Đạt       | Xuất sắc | Cách đo                                              |
| ------------------------------------------- | --------- | -------- | ---------------------------------------------------- |
| Thời gian từ kết nối shop → in tem đầu tiên | < 15 phút | < 8 phút | Funnel onboarding                                    |
| **Tỉ lệ đơn xử lý trong ViShop / tổng đơn** | > 80%     | > 95%    | So sánh đơn ship từ hệ thống vs tổng đơn đồng bộ     |
| Độ trễ đồng bộ đơn (p95)                    | < 60s     | < 20s    | Từ thời điểm sàn tạo đơn tới khi hiện trong hệ thống |
| Đơn oversell / 1.000 đơn                    | < 3       | < 1      | Đơn huỷ do hết hàng                                  |
| % đơn có đủ dữ liệu tính lợi nhuận          | > 90%     | > 98%    | Đơn có giá vốn + ghép nối đầy đủ                     |
| Tỉ lệ ghép nối SKU sau onboarding           | > 85%     | > 95%    | Trong 48 giờ đầu                                     |

**Lagging — đo trong 1–3 tháng**

| Chỉ số                                      | Ngưỡng |
| ------------------------------------------- | ------ |
| Retention M1 (tài khoản đã map ≥20 SKU)     | > 60%  |
| Tỉ lệ kết nối gian hàng thứ 2 trong 30 ngày | > 40%  |
| Chuyển đổi Miễn phí → Trả phí               | > 8%   |
| Khách trả phí sau 3 tháng beta              | ≥ 30   |
| Tỉ lệ ticket hỗ trợ / khách / tháng         | < 1,5  |

**Chỉ số quyết định:** _Tỉ lệ đơn xử lý trong ViShop_. Dưới 80% nghĩa là người dùng vẫn mở Seller Center — khi đó tồn kho sai và toàn bộ giá trị sản phẩm sụp theo. Mọi ưu tiên sản phẩm phải phục vụ chỉ số này trước.

## Rủi ro

| #   | Rủi ro                                                | Mức        | Giảm thiểu                                                                            |
| --- | ----------------------------------------------------- | ---------- | ------------------------------------------------------------------------------------- |
| R1  | Không được cấp TikTok Shop Partner API                | Chặn dự án | Nộp hồ sơ trước khi bắt đầu phát triển. Là gate 0 của dự án                           |
| R2  | TikTok đổi API hoặc siết hạn mức                      | Cao        | Adapter có versioning, giám sát deprecation, fallback polling                         |
| R3  | Người dùng vẫn xử lý đơn trên Seller Center → tồn sai | Cao        | Onboarding ép hoàn tất ghép nối trước khi bật đẩy tồn; đo tỉ lệ xử lý như metric số 1 |
| R4  | Bóc tách phí sai → mất niềm tin ngay lập tức          | Cao        | Luôn gắn nhãn "Ước tính" cho tới khi có payout; đối soát ngược 100%                   |
| R5  | Đối thủ nâng cấp module TikTok                        | Trung–cao  | Cửa sổ khoảng 12–18 tháng. Phải chiếm được affiliate và thuế trước khi họ kịp         |
| R6  | Scope creep sang đăng bán / cào sản phẩm              | Trung      | Đã ghi rõ ngoài phạm vi. Mọi bổ sung phải kèm một hạng mục bị cắt                     |
| R7  | Hạ tầng không chịu nổi đỉnh campaign (11.11, 12.12)   | Trung      | Kiến trúc queue-based; load test ở mức 10× lưu lượng thường trước tháng 11            |
| R8  | Chi phí hạ tầng vượt doanh thu ở giai đoạn free tier  | Trung      | Free tier chỉ 300 đơn; giám sát chi phí/tenant từ ngày đầu                            |

## Timeline

```
T1–T4    M00, M01, M02, M03, M04              Nền tảng + kết nối + SKU
T5–T8    M05, M06, M07                        Tồn kho + đơn + in tem      → ALPHA nội bộ
T9–T12   M08, M09, M10                        Quét + sau bán + lợi nhuận  → BETA khách hàng
T13–T16  M11, M12, M13, M14, M15              Affiliate + đối soát + billing → GA TikTok
T17–T24  M16                                  Shopee → Lazada
T20–T32  M17, M18                             Kho + thu mua
T28–T40  M19, M20, M21, M22, M23              3PL, thuế, ads, mobile, API
```

**Đường găng:** hồ sơ TikTok Partner API (R1) → M02 → mọi thứ còn lại.

## Open Questions

| #   | Câu hỏi                                                                                       | Người trả lời | Chặn? |
| --- | --------------------------------------------------------------------------------------------- | ------------- | ----- |
| 1   | Hồ sơ TikTok Shop Partner API đã nộp chưa, ai phụ trách?                                      | BD / Legal    | Có    |
| 2   | Headcount và ngân sách cho 16 tuần đầu?                                                       | Ban giám đốc  | Có    |
| 3   | Có tận dụng tệp khách hàng sẵn có để lấy 30 khách beta không?                                 | Sales         | Có    |
| 4   | Hạ tầng đặt ở đâu, nhà cung cấp nào?                                                          | Kỹ thuật      | Có    |
| 5   | Mô hình giá cuối: theo gian hàng, theo đơn, hay hybrid?                                       | PM            | Không |
| 6   | Có làm chi trả hoa hồng affiliate hộ khách không (liên quan giấy phép trung gian thanh toán)? | Legal         | Không |
| 7   | Mở Open API ngay từ GA hay để Phase 2?                                                        | Kỹ thuật / PM | Không |
| 8   | Chính sách lưu trữ dữ liệu sau khi khách rời đi?                                              | Legal         | Không |
