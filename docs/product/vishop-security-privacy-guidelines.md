# ViShop — Bảo mật & Xử lý Dữ liệu Nhạy cảm

**Áp dụng:** toàn bộ code chạm token TikTok (`authorization.access_token`/`refresh_token`/`shop_cipher`), dữ liệu người mua trong `order`/`package`, và dữ liệu creator/affiliate ở M11-B — chủ yếu M00, M02, M06, M09, M11
**Tham chiếu bắt buộc:** `vishop-build-spec-v1.2.md` §2.2, §2.5, §6.3, §6.4, §10 (R10) · `vishop-erd.md` mục 4 · `vishop-tiktok-integration-runbook.md` mục 1–3, 8 · `vishop-function-list.md` (FR-06-08, FR-11-02b/05b/06b/09b) · `vishop-capability-matrix.md` mục M11-B · `vishop-dev-guidelines.md` (không lặp lại ở đây)
**Đối tượng:** toàn bộ dev, reviewer, tech lead — bắt buộc đọc trước khi PR chạm `authorization`, response API có PII người mua, hoặc bất kỳ phần nào của M11-B

`vishop-dev-guidelines.md` quy định ChannelAdapter, idempotency, event-log-first, tenant isolation, rate governor — cách viết code đúng spec ở tầng implementation. Tài liệu đó không có mục nào cho bảo mật/dữ liệu nhạy cảm. Đây là khoảng trống thật, không phải tuỳ chọn bổ sung — token TikTok và PII người mua là hai loại dữ liệu duy nhất trong hệ thống mà lộ ra gây thiệt hại ngay lập tức cho bên thứ ba (seller, người mua), không chỉ cho ViShop. Tài liệu này lấp khoảng đó, không lặp lại nội dung đã có ở dev-guidelines.

---

## A. Token TikTok — access_token, refresh_token, shop_cipher

### Vì sao access_token/refresh_token là bí mật hạng cao nhất

`access_token` của một `authorization` role `seller` mang toàn bộ scope đã cấp cho shop đó theo ma trận capability §2.2 — sản phẩm, tồn kho, đơn, package, RTS, tem, finance sau payout. ViShop không tách thêm một lớp quyền "chỉ đọc" nào bên dưới token này. Ai cầm được access_token là cầm được nguyên quyền API của shop đó, tương đương chiếm quyền vận hành shop từ bên ngoài Seller Center mà không cần biết mật khẩu đăng nhập TikTok của seller. `refresh_token` nguy hiểm hơn về mặt thời gian sống: TTL tính bằng tuần so với access_token tính bằng giờ (runbook mục 2) — lộ refresh_token nghĩa là kẻ tấn công tự cấp access_token mới liên tục mà seller không hề hay biết, cho tới khi có người chủ động revoke.

NFR §6.3 đã chốt cứng, không phải khuyến nghị: "Token sàn mã hoá at-rest, không bao giờ ghi ra log." Bắt buộc mã hoá cột `authorization.access_token`/`authorization.refresh_token` tại rest (application-level encryption hay column encryption tuỳ hạ tầng — chọn cơ chế nào không quan trọng bằng việc đáp ứng đủ hai vế: mã hoá tại rest và không bao giờ xuất hiện dạng plaintext trong log). "Log" ở đây hiểu rộng: log lỗi, log request/response khi debug, APM trace, log của job queue khi in ra payload để retry — tất cả đều tính.

### Ai/service nào được đọc token

Build-spec chưa chốt danh sách service cụ thể được đọc token dạng giải mã. Nguyên tắc hợp lý suy ra từ ranh giới ChannelAdapter đã chốt ở §2.1 và dev-guidelines mục 1: chỉ tầng `TikTokChannelAdapter` — nơi duy nhất được import SDK/HTTP client TikTok — mới đọc token dạng giải mã, và chỉ đọc ngay trước khi gọi API. Service/job nghiệp vụ phía trên gọi qua interface (`AuthProvider`, `OrderProvider`...), không tự tay lấy token ra khỏi bảng `authorization` rồi tự gọi HTTP. Đây là khuyến nghị áp dụng ranh giới kiến trúc đã có, chưa phải điều khoản đã được Security/Legal xác nhận riêng bằng văn bản — ghi rõ để không hiểu nhầm thành đã chốt cứng.

### Quy trình xoay vòng khi phát hiện lộ

Runbook mục 2 đã định nghĩa hành vi hệ thống khi refresh tự nhiên thất bại: chuyển shop sang "Cần kết nối lại", dừng đẩy tồn (BR-02-03), bắn cảnh báo, không tự retry vô hạn — refresh token hết hạn là hết, phải OAuth lại từ đầu. Khi phát hiện token bị lộ (rò rỉ log, breach hạ tầng, nghi ngờ truy cập bất thường), áp dụng đúng cơ chế này nhưng kích hoạt chủ động, không đợi refresh tự nhiên fail:

1. Revoke ngay authorization liên quan qua `AuthProvider.revoke` — không đợi access_token tự hết hạn theo TTL.
2. Chuyển shop/authorization sang trạng thái "Cần kết nối lại", dừng đẩy tồn ngay (áp dụng hành vi BR-02-03 cho trường hợp revoke chủ động, không chỉ trường hợp refresh fail tự nhiên).
3. Yêu cầu seller uỷ quyền lại từ đầu qua đúng luồng OAuth ban đầu — không có đường "khôi phục" token cũ.
4. Ghi sự kiện revoke chủ động vào `event_log` (`payloadBefore`/`payloadAfter` là trạng thái authorization, tuyệt đối không chứa giá trị token) và `audit_logs` nếu có người vận hành ViShop kích hoạt thao tác.

Bước 1–3 là hành vi hệ thống đã có sẵn qua BR-02-03 và luồng OAuth chuẩn. Phần "phát hiện lộ" — giám sát, cảnh báo truy cập bất thường vào bảng `authorization` — chưa có FR/NFR nào định nghĩa trong build-spec hiện tại. Đây là khoảng trống cần một FR riêng, cùng tính chất với khoảng trống webhook receiver phát hiện trước đó — khoảng trống ấy đã khép bằng FR-00-09 ([VIS-257](https://baotrongit.atlassian.net/browse/VIS-257)) ngày 07/08/2026, khoảng trống giám sát bảng `authorization` thì chưa, mới ở mức đề xuất bổ sung và chưa có xác nhận từ Tech Lead.

### shop_cipher — không phải bí mật, vẫn phải theo dõi

ERD mục 4 và runbook mục 3 đã nói rõ: `shop_cipher` không dùng cơ chế hết hạn như token và không cần mã hoá at-rest như access_token/refresh_token, nhưng không được coi là hằng số vĩnh viễn. Nó có thể đổi khi seller deauthorize rồi re-authorize (runbook mục 3), và ghi đè sai chỗ — một job nền còn giữ cipher cũ trong bộ nhớ đệm hoặc trong request đang bay — tạo ra lỗi xác thực dễ bị chẩn đoán nhầm thành token hết hạn trong khi token vẫn còn hạn. Quy tắc bắt buộc theo runbook: mọi lần ghi đè `shop_cipher` phải qua `event_log` (payload_before/after chứa cipher cũ/mới), ghi đè chứ không cộng dồn, và nhật ký đồng bộ (FR-02-08) cùng danh sách Bất thường (FR-06-21) phải phân biệt được lỗi "cipher lệch" với lỗi "token hết hạn" — gộp chung hai loại lỗi này là sai, không phải việc tối ưu để sau.

---

## B. PII khách hàng trong luồng đơn/package

### Masking theo vai trò

FR-01-06 định nghĩa quyền cấp trường `Xem thông tin người mua` độc lập với `Xem giá vốn`/`Xem lợi nhuận`. AC của M01 đã chốt nguyên tắc chung: user thiếu quyền thì UI hiện `***` và **API response cũng không chứa giá trị thật** — nguyên tắc này áp dụng nguyên vẹn cho `Xem thông tin người mua`, không có ngoại lệ. NFR §6.3: "Dữ liệu người mua: chỉ hiển thị cho vai trò có quyền, ghi audit khi truy cập hàng loạt." Dev-guidelines mục 4 đã chốt cách làm đúng ở tầng API: field bị khoá trả `null` hoặc lược khỏi response, không trả giá trị thật rồi để frontend tự che — trả giá trị thật cho client rồi che ở UI là lỗi thiết kế response, không phải bug giao diện có thể fix sau bằng CSS.

**Không phải mọi field PII người mua đều bị chặn theo quyền này.** `vishop-erd.md` mục 1 (entity `order`) tách rõ: `order.buyerName` **không** bị field-level permission chặn — mọi vai trò có quyền xem đơn đều thấy tên người nhận, vì tên đơn thuần không đủ để liên hệ trực tiếp khách hàng và mọi màn xử lý vận hành (đóng gói, giao hàng, đối chiếu) đều cần đến nó. Chỉ `order.buyerPhone` và `order.buyerFullAddress` mới thuộc phạm vi `Xem thông tin người mua` (FR-01-06) — hai field này mới áp nguyên tắc "thiếu quyền thì `null`/lược khỏi response" ở trên. Field mới thêm vào nhóm PII người mua sau này mặc định coi là bị chặn trừ khi có lý do nghiệp vụ tương đương `buyerName` để miễn trừ, và lý do đó phải ghi lại ở đây.

Vai trò dựng sẵn nào mặc định có quyền này không được build-spec chốt cứng theo tên (FR-01-03 chỉ liệt kê 4 vai trò dựng sẵn: Chủ tài khoản/Vận hành/Kho/Kế toán, không map trước quyền field-level cho từng vai trò) — đây là quyết định cấu hình khi seed permission mặc định, không phải nguyên tắc kỹ thuật. Gợi ý theo nghiệp vụ thực tế để PM/Tech Lead cân nhắc, không phải quyết định đã chốt: `Kho` cần tên và địa chỉ để đóng gói/giao hàng nhưng không có lý do nghiệp vụ cần SĐT dạng plaintext nếu bản thân TikTok đã mask sẵn phần lớn trường hợp; `Kế toán` không có lý do nghiệp vụ cần thấy PII người mua nguyên vẹn ở bất kỳ màn hình nào.

Truy cập hàng loạt (export danh sách đơn, xem trang lớn có PII) phải ghi audit riêng đúng câu NFR §6.3, đi cùng với rate limit chống lạm dụng cho endpoint export cũng đã ghi trong cùng mục NFR — hai việc luôn đi cùng nhau: chặn tần suất và ghi vết ai đã lấy dữ liệu ra khỏi hệ thống.

### Không cố "unmask" dữ liệu TikTok đã che sẵn

FR-06-08 đã ghi nhận qua audit: `Get Package Detail` trả SĐT/tên người nhận bị TikTok che một phần khi đơn dùng platform logistics — ví dụ `(+1)213-***-1234`. Dev-guidelines mục 6.3 đã chốt cách xử lý: hiển thị đúng giá trị mask nhận được, không cố unmask, không báo lỗi "thiếu dữ liệu", không hiển thị field trống. Nhắc lại dưới góc độ bảo mật vì đây là ranh giới không được vượt, không chỉ là vấn đề UX: dữ liệu bị che tại nguồn phía TikTok, không phải do ViShop đồng bộ thiếu — không có kỹ thuật nào "giải mã" được phần bị che, và mọi nỗ lực suy đoán số điện thoại đầy đủ (ví dụ đối chiếu chéo với nguồn dữ liệu khác ngoài phạm vi scope OAuth đã xin) là hành vi vượt phạm vi cấp phép, không được implement dưới bất kỳ lý do nghiệp vụ nào. Vận hành cần liên hệ khách khi có vấn đề giao hàng thì dùng kênh sàn (chat/hotline shop trên platform) — dev-guidelines đã nhắc điều này, đây là ranh giới bảo mật/pháp lý đi kèm, không phải gợi ý tuỳ chọn.

### PII trong log

NFR §6.3 chốt cứng riêng cho token: "không bao giờ ghi ra log." Build-spec chưa có câu tương đương chốt cứng riêng cho PII người mua trong log ứng dụng. Nguyên tắc áp dụng hợp lý, suy ra từ cùng NFR và từ việc FR-01-06 đã xác lập PII người mua là dữ liệu có quyền cấp trường (không phải dữ liệu công khai nội bộ cho mọi kỹ sư có quyền đọc log hệ thống): log ứng dụng thông thường — log lỗi, log debug, APM trace — không ghi SĐT/tên/địa chỉ người nhận dạng plaintext. Cụ thể:

- Log lỗi khi gọi `Get Order Detail`/`Get Package Detail` thất bại: log theo `order_id`/`package_id`, không log nguyên văn payload response nếu payload chứa PII.
- Log debug trong quá trình phát triển không commit kèm dữ liệu production thật.
- `event_log` là ngoại lệ có kiểm soát, không phải lỗ hổng: nó là dữ liệu nghiệp vụ bắt buộc theo §2.5, không phải log ứng dụng thông thường. Quyền đọc `event_log` phải theo đúng nguyên tắc ở mục D, không mặc định mở cho mọi người có quyền xem log hệ thống.

Đây là khuyến nghị áp dụng nguyên tắc đã có sẵn trong spec cho một trường hợp spec chưa viết thành câu riêng — không phải điều khoản mới tự đặt ra. Cần Tech Lead xác nhận khi viết logging convention chi tiết ở mức implementation.

### PII khi nối mô hình ngôn ngữ — BR-14-05

Trợ lý ViAI ở bản hiện tại chạy hoàn toàn trong ViShop, không gọi ra ngoài. Ràng buộc dưới đây đặt ra từ bây giờ vì tới lúc nối mô hình thì hình dạng dữ liệu đã cố định, sửa lại tốn hơn nhiều. Bối cảnh đầy đủ: [vishop-ai-automation-roadmap.md](./vishop-ai-automation-roadmap.md).

- **Không gửi tên, số điện thoại hay địa chỉ người mua ra khỏi hệ thống ViShop.** Kết quả tool trả về số đếm và mã đơn, không trả về dữ liệu cá nhân — kể cả khi mô hình chạy nội bộ, vì hình dạng kết quả là thứ sẽ được đem đi dùng lại khi đổi sang mô hình đặt ngoài.
- **Ranh giới là lời gọi ra ngoài, không phải nhà cung cấp.** Dữ liệu đã rời khỏi ViShop thì không còn kiểm soát được, bất kể nhà cung cấp cam kết gì về việc không huấn luyện trên dữ liệu khách hàng. Đánh giá theo dữ liệu đi ra, không theo điều khoản đọc được.
- **Nhật ký hội thoại cũng áp cùng luật.** Bảng `audit_logs` với `action = 'assistant.ask'` (FR-14-10) lưu câu hỏi, tool đã gọi và kết quả trong cột `metadata` — trong đó không có PII người mua, đúng như kết quả tool. Cách xếp cột chi tiết ở [vishop-erd.md](./vishop-erd.md) mục 3.5.
- **Khoá API của mô hình xếp cùng hạng với token TikTok ở mục A.** Nạp từ biến môi trường hoặc trình quản lý bí mật, không commit vào mã nguồn, không ghi ra log.

---

## C. Rủi ro pháp lý dữ liệu creator/Affiliate — R10 (BLOCKER)

### Bốn FR đang mang rủi ro pháp lý thật

| FR-ID     | Nội dung                                                                           | Ưu tiên |
| --------- | ---------------------------------------------------------------------------------- | ------- |
| FR-11-02b | Bảng creator: doanh thu, số đơn, hoa hồng, tỉ lệ hoàn, lợi nhuận ròng sau hoa hồng | P0*     |
| FR-11-05b | Lợi nhuận theo creator × SKU                                                       | P0*     |
| FR-11-06b | Cảnh báo creator tỉ lệ hoàn bất thường                                             | P0*     |
| FR-11-09b | Gắn nhãn creator, so sánh giữa kỳ                                                  | P1      |

`P0*` = P0 trong phạm vi tính năng có gate, không chặn GA (build-spec §5, M11-B). Cả bốn FR cùng thuộc M11-B — Creator P&L, đọc dữ liệu từ [Search Seller Affiliate Orders](https://partner.tiktokshop.com/docv2/page/search-seller-affiliate-orders-202410) bằng scope `seller.affiliate_collaboration.read`.

> **Đính chính 07/08/2026 — làm mục này quan trọng hơn, không nhẹ đi.** Bản trước ghi bốn FR này "cần `authorization` role `partner` (TAP)". Sai: dữ liệu creator nằm ngay trong scope gian hàng tự cấp. Nghĩa là **không có rào cản kỹ thuật nào ngăn đội dev vô tình build và ship phần này.** Rào cản duy nhất là kỷ luật review nêu ở dưới. Trước đây hồ sơ TAP chưa duyệt còn chặn hộ; giờ thì không.

### Ràng buộc ToS đã audit — trích nguyên văn

Ghi chú audit trong [vishop-function-list.md](./vishop-function-list.md) và [vishop-capability-matrix.md](./vishop-capability-matrix.md) (mục M11-B) đã xác nhận:

> ToS Partner Center §3.8(e)/(f): cấm dùng dữ liệu Subscriber (bao gồm creator) ngoài mục đích được cấp phép, cấm bán/trao đổi; §3.8(a) yêu cầu uỷ quyền bằng văn bản từ creator.

Bản chất của bốn FR trên: ViShop lưu trữ và hiển thị dữ liệu doanh thu/hoa hồng/tỉ lệ hoàn ở mức từng creator cho seller xem trong sản phẩm trả phí. Đọc thẳng vào ba điều khoản đã trích: đây là "dùng dữ liệu Subscriber ngoài mục đích được cấp phép" nếu không có uỷ quyền bằng văn bản từ creator theo §3.8(a), và có thể chạm vào "bán/trao đổi" theo §3.8(e)/(f) vì tính năng này là một phần giá trị của gói cước ViShop thu phí seller — không phải suy diễn xa, là đọc trực tiếp phạm vi điều khoản đã trích khớp với mô hình kinh doanh đang thiết kế.

### Đây là gate, không phải khuyến nghị

Capability matrix đánh dấu M11-B là **⚠️ Ngoài cam kết Phase 1** với ghi chú rõ: capability đã đủ, chặn nằm ở pháp lý. R10 trong bảng rủi ro §10 của build-spec xếp mức "Cao — chưa verify", biện pháp giảm thiểu ghi rõ: "Nếu bị cấm thì bỏ hẳn, không phải hoãn." Kết cục khả dĩ là loại bỏ vĩnh viễn bốn FR này khỏi scope, không phải "chờ rồi làm sau" — điều kiện chặn dưới đây phải được đọc với giả định đó.

Điều kiện chặn cụ thể trước khi viết bất kỳ dòng code nào lưu trữ dữ liệu creator persistent cho bốn FR trên:

1. Legal xác nhận bằng văn bản: mô hình kinh doanh ViShop (bán tính năng phân tích creator cho seller, thu phí gián tiếp qua gói cước) có nằm trong "mục đích được cấp phép" theo ToS Partner Center §3.8 hay không.
2. Nếu cần uỷ quyền từ creator theo §3.8(a) — xác định cơ chế lấy uỷ quyền đó là việc của Legal/vận hành, không tự suy diễn một giải pháp kỹ thuật (ví dụ: một dòng chấp thuận nào đó trong luồng uỷ quyền gian hàng) để coi như đã giải quyết xong mà chưa có Legal xác nhận cơ chế đó đủ.
3. Cho tới khi có xác nhận (1) và (2): không build phần lưu trữ dữ liệu creator persistent — bảng lưu doanh thu/hoa hồng/tỉ lệ hoàn theo creator, cache dài hạn, mọi hình thức giữ dữ liệu creator qua nhiều phiên xử lý. Được phép: thiết kế schema/interface ở mức tài liệu (áp dụng tinh thần P2 "chỉ thiết kế, không xây" tạm thời cho riêng phần này dù FR gốc đánh P0*). Không được phép: ship tính năng cho seller dùng thật, hoặc lưu dữ liệu creator vượt phạm vi một phiên xử lý tạm thời trong bộ nhớ.

PR chạm bất kỳ phần nào của FR-11-02b/05b/06b/09b mà không kèm xác nhận Legal (link hoặc tham chiếu cụ thể) phải bị reviewer từ chối ngay ở bước review — không đợi QA hay pháp chế phát hiện sau khi đã build xong. Chi phí revert một tính năng đã lưu dữ liệu creator thật trên production — xoá dữ liệu, thông báo các bên liên quan, khả năng đã vi phạm ToS trong thời gian tính năng chạy — cao hơn nhiều so với chi phí chờ xác nhận trước khi viết code.

---

## D. Phân quyền & audit trail

### audit_log — "ai làm gì"

FR-00-04 (P0): audit log mọi thao tác ghi — user, hành động, đối tượng, IP, thời gian. Dev-guidelines mục 3 đã phân biệt rõ với `event_log`: `audit_log` trả lời "ai bấm gì" (phục vụ compliance/bảo mật), `event_log` trả lời "hệ thống đã đổi cái gì" kể cả khi actor là `system` hoặc `platform` — hai bảng không thay thế cho nhau.

Ai được xem `audit_log`: quyền `audit_log.view` trong [danh mục quyền](./vishop-permission-catalog.md) §2.14. Mặc định cấp cho Chủ tài khoản và Kế toán; ba vai trò dựng sẵn còn lại không có. Build-spec chưa có FR riêng cho màn hình này — cần bổ sung FR nếu muốn khoá cứng thay vì để ở mức cấu hình vai trò.

### event_log — không phải công cụ audit người dùng, và không phải cửa sau để lách field permission

`event_log` (§2.5, FR-00-01) phục vụ mục đích khác: debug lệch tồn, tái dựng trạng thái tại một thời điểm, nền tảng đối soát — không phải nơi trả lời "nhân viên X đã làm gì lúc mấy giờ". Actor có thể là `system` hoặc `platform` (webhook TikTok bắn về), hai trường hợp không có "người dùng" cụ thể để audit — đây chính là lý do phải tách bảng thay vì gộp, không phải trùng lặp dữ liệu vô nghĩa.

Hệ quả trực tiếp cho quyền đọc: `event_log` chứa `payload_before`/`payload_after` đầy đủ theo đúng nguyên tắc "không ghi delta khi thiếu context" (dev-guidelines mục 3) — với entity là `order`/`package`, payload này có thể mang PII người nhận tại thời điểm ghi. Quyền đọc `event_log` thô (không qua tầng nghiệp vụ đã lọc field) cho entity liên quan order/package yêu cầu `data.buyer_pii.view` (FR-01-06) — không phải vì `event_log` kém an toàn hơn `audit_log`, mà vì nó là snapshot PII đầy đủ theo đúng thiết kế của chính nó. Không mở `event_log` như một kênh phụ để một user thiếu quyền `Xem thông tin người mua` vẫn xem được PII qua đường vòng "xem lịch sử thay đổi" — field permission chặn ở API chính vô nghĩa nếu event_log để hở cùng dữ liệu đó.

---

## E. Checklist bảo mật trước khi merge

Áp dụng đủ cho PR chạm shared/persisted state hoặc gọi API TikTok, cùng phạm vi với checklist Definition of Done ở dev-guidelines mục 8 — đây là phần bổ sung riêng cho bảo mật/PII, không lặp lại các mục đã có ở đó (tenant_id, idempotency, event log nói chung đã có sẵn).

- [ ] Không log secrets/PII dạng plaintext — access_token, refresh_token, SĐT/tên/địa chỉ người nhận — trong log lỗi, log debug, APM trace, hay log của job queue khi retry.
- [ ] Không hardcode access_token/refresh_token/app_secret trong code, file config committed, hay test fixture — dùng biến môi trường/secret manager theo hạ tầng đang dùng.
- [ ] Token TikTok chỉ được đọc dạng giải mã trong tầng `TikTokChannelAdapter`, không bị cache dạng plaintext ở service/job nghiệp vụ phía trên (mục A).
- [ ] Mọi lệnh gọi API TikTok đi qua ChannelAdapter + queue có governor — không có nhánh "gọi tắt" trực tiếp cho debug hay thao tác gấp trên production (dev-guidelines mục 5, §2.6).
- [ ] Mọi query/job mới có điều kiện `tenant_id` trong WHERE clause và index phù hợp — kể cả script migration và query debug production (FR-00-06).
- [ ] Field bị khoá theo quyền cấp trường (`data.cost.view` / `data.profit.view` / `data.buyer_pii.view` — FR-01-06) chặn ở tầng API bằng cách lược field, không trả giá trị thật rồi ẩn ở UI.
- [ ] Không cố unmask hoặc suy đoán dữ liệu người nhận đã bị TikTok che sẵn (FR-06-08) bằng bất kỳ kỹ thuật hay nguồn đối chiếu chéo nào.
- [ ] Webhook nhận từ TikTok (FR-00-09, xem runbook mục 8): xác thực chữ ký trước khi xử lý, không tin payload chưa verify; không ghi payload nguyên văn chứa dữ liệu người mua vào log.
- [ ] PR chạm `authorization`/token: mô tả PR nêu rõ token được đọc/ghi ở đâu, có đi đúng qua biên ChannelAdapter không.
- [ ] PR chạm FR-11-02b/05b/06b/09b (Creator P&L): có link xác nhận Legal đính kèm trong mô tả PR — không có thì reviewer từ chối thẳng, không thương lượng (mục C).
- [ ] PR chạm export hoặc xem danh sách lớn có PII người mua: có ghi audit truy cập hàng loạt, có rate limit chống lạm dụng đúng NFR §6.3.
- [ ] PR thêm field mới vào `event_log.payload_before`/`payload_after` cho entity order/package: xác nhận quyền đọc entry đó tương đương quyền `Xem thông tin người mua`, không mở rộng hơn (mục D).
