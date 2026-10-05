# ViShop — Runbook tích hợp API TikTok Shop

**Đối tượng:** dev backend làm connector TikTok trong `ChannelAdapter` (§2.1, build-spec v1.2)
**Tiền đề:** Gate G0 đã qua — ViShop đã đăng ký App Developer (ISV) tại Partner Center và đã tạo Public App thị trường VN. Hồ sơ ứng dụng thật ở mục 0.
**Không thay thế:** build-spec v1.2 và Function List — runbook này chỉ diễn giải vận hành, mọi FR-ID trích dẫn phải đối chiếu ngược hai tài liệu đó.

---

## 0. Hồ sơ ứng dụng trên Partner Center

Ứng dụng đã được tạo ngày 07/08/2026 dưới tài khoản MKT Software, loại tài khoản **App Developer (ISV)**.

| Trường              | Giá trị                                                                      |
| ------------------- | ---------------------------------------------------------------------------- |
| Tên app             | ViShop                                                                       |
| Loại                | Public app (seller cài và tự cấp quyền)                                      |
| Service category    | eCommerce Management / Enterprise Resource Planning                          |
| Service ID          | `7670869274681952021`                                                        |
| Trang quản trị app  | https://partner.tiktokshop.com/service/gather?service_id=7670869274681952021 |
| Thị trường mục tiêu | Vietnam — Local sellers                                                      |
| Trạng thái          | Draft (chưa publish)                                                         |

`app_key` và `app_secret` **không ghi vào tài liệu hay mã nguồn**. Lấy trực tiếp trên trang quản trị app và nạp qua biến môi trường / trình quản lý bí mật. Xem lại mục ràng buộc bảo mật ở [vishop-build-spec-v1.2.md](./vishop-build-spec-v1.2.md).

### 0.1 Scope đã bật (26/40)

Đã bật toàn bộ scope thuộc nhóm `seller.*` và `data.*` mà ứng dụng có thể tự bật, đủ dùng cho mọi module từ M00 đến M23:

`data.bestselling.public.read` · `data.shop_analytics.public.read` · `seller.affiliate_collaboration.read` · `seller.affiliate_messages.write` · `seller.authorization.info` · `seller.delivery.status.write` · `seller.finance.info` · `seller.fulfillment.basic` · `seller.fulfillment.package.write` · `seller.global_product.category.info` · `seller.global_product.delete` · `seller.global_product.image_trans_task` · `seller.global_product.info` · `seller.global_product.write` · `seller.logistics` · `seller.order.info` · `seller.product.basic` · `seller.product.delete` · `seller.product.write` · `seller.promotion.info` · `seller.promotion.write` · `seller.affiliate_collaboration.write` · `seller.creator_marketplace.read` · `seller.return_refund.basic` · `seller.shop.info` · `serller.product.optimize` (lỗi chính tả từ phía TikTok, giữ nguyên)

**Cố ý không bật:**

- `seller.customer_service` — chứa thông tin cá nhân người mua. Chính TikTok cảnh báo không bật nếu không thực sự cần, và ViShop có ràng buộc không đưa dữ liệu người mua ra ngoài hệ thống (BR-14-05). Nếu sau này làm module chăm sóc khách hàng thì phải bấm **Apply** và qua duyệt riêng.
- `seller.redeem_info.write` — cũng phải Apply riêng, ngoài phạm vi sản phẩm.
- 12 scope `creator.*` và `partner.tap_campaign.*` / `partner.authorization.info` — ViShop không đứng vai creator hay đại lý affiliate, xem mục 1.2.

**Chi phí của việc bật thiếu:** thêm scope sau khi seller đã cài app thì phải yêu cầu toàn bộ khách hàng **uỷ quyền lại**. Vì vậy scope được bật hết ngay khi app còn ở Draft, trước khi có seller đầu tiên.

### 0.2 Việc còn treo

- **Redirect URL đang là `https://viai.vn`** — mới là tên miền trần, chưa có đường dẫn callback. Luồng OAuth ở mục 1.1 sẽ gãy ngay bước 2 nếu không sửa thành endpoint callback thật.
- **Hồ sơ năng lực Enterprise Resource Planning đang ở trạng thái Draft – Awaiting submission.** Không chặn việc tạo app hay bật scope, nhưng chặn bước publish.
- Ba cửa duyệt trước khi app chạy thật: Partner registration review → Listing review → App review.

---

## 1. Ba luồng OAuth theo role

Một tenant giữ nhiều `authorization`, mỗi cái một role, một tập scope, một cặp token riêng (§2.2). Ba luồng dưới đây độc lập nhau — không có "one OAuth to rule all three".

### 1.1 Seller (FR-02-01, P0)

Luồng chuẩn Authorization Code:

1. Redirect user sang trang cấp quyền của TikTok Shop với `app_key` và `state` (state phải nhúng `tenantId` để chống CSRF và để biết gán authorization vào tenant nào khi callback về).
2. TikTok redirect về callback kèm `auth_code`.
3. Đổi `auth_code` lấy `access_token` + `refresh_token` qua endpoint token (tên field theo đúng response OAuth của TikTok, giữ nguyên snake_case ở bước này vì đây là wire format của sàn).
4. Gọi Get Authorized Shops để lấy danh sách shop thuộc authorization này, mỗi shop kèm `cipher` (lưu nội bộ thành `shopCipher`, xem mục 3).
5. Ghi **một bản ghi `authorization` cho mỗi shop** trả về ở bước 4, tên cột theo quy ước camelCase của repo (`vishop-erd.md` mục 0): `authorization(shopId, shopCipher, grantedScopes, accessToken, refreshToken, expiresAt)`. Không có cột `role` — một lượt uỷ quyền có thể trả về nhiều shop, và scope là của từng shop, không hợp nhất ở cấp tenant (xem [vishop-build-spec-v1.2.md](./vishop-build-spec-v1.2.md) §2.2).

**Ràng buộc bắt buộc (BR-02-01):** một `shopId` chỉ được gắn vào một tenant. Callback phải kiểm tra shop đã tồn tại ở tenant khác chưa trước khi ghi — nếu có, chặn và trả lỗi rõ, không cho đè.

Sau khi OAuth xong: shop chuyển trạng thái "Đang đồng bộ khởi tạo" và chạy đồng bộ sản phẩm + đơn 30 ngày (FR-02-04). SLA: shop ≤10.000 đơn phải về hết trong ≤10 phút — có nghĩa governor phải dành riêng một phần hạn mức cho luồng sync khởi tạo, không share chung hàng đợi với sync định kỳ.

### 1.2 Không có luồng uỷ quyền nào khác

> **Sửa 07/08/2026.** Hai mục cũ ở đây mô tả luồng OAuth riêng cho Creator và cho Partner (TAP). Cả hai đã bị xoá. ViShop là App Developer phát hành một Public App — TikTok Shop không có khái niệm chọn vai trò khi uỷ quyền cho loại app này. Xem [vishop-build-spec-v1.2.md](./vishop-build-spec-v1.2.md) §2.2.

Toàn bộ dữ liệu ViShop cần nằm trong scope gian hàng tự cấp ở mục 1.1, gồm cả `seller.affiliate_collaboration.read` (creator gắn với đơn, hoa hồng, nguồn đơn) và `data.shop_analytics.public.read` (phiên live của official account).

**Rủi ro pháp lý còn lại — R10.** ToS Partner Center §3.8(e)/(f) cấm dùng dữ liệu Subscriber/creator ngoài mục đích được cấp phép, §3.8(a) yêu cầu uỷ quyền bằng văn bản từ creator. Đây là rủi ro thật và **có khả năng Legal không chấp nhận**. FR-11-02b/05b/06b mang P0\* và FR-11-09b (P1) đều phụ thuộc dữ liệu creator lưu lâu dài — **không code phần lưu trữ này trước khi có văn bản của Legal.** Đọc và tổng hợp trong bộ nhớ để hiển thị một lần thì không thuộc phạm vi cấm; lưu lại thành hồ sơ creator thì có.

### 1.3 Feature gate

```
feature_gate(feature) = feature.required_scopes ⊆ shop.granted_scopes
```

`capabilities` là cache suy ra từ `granted_scopes` của uỷ quyền còn hiệu lực. Revoke hoặc hết hạn → invalidate cache ngay, không đợi cron.

---

## 2. Refresh token (FR-02-05, P0)

- Access token TikTok Shop có TTL ngắn (giờ), refresh token TTL dài (tuần). Job nền refresh access token **trước khi hết hạn**, không đợi request thật fail rồi mới refresh — tránh request nghiệp vụ bị chặn giữa chừng.
- Refresh thất bại (refresh token cũng hết hạn hoặc bị revoke phía TikTok) → chuyển shop sang trạng thái **"Cần kết nối lại"**, dừng đẩy tồn cho shop đó (BR-02-03), bắn cảnh báo. Không tự động thử vô hạn — refresh token hết hạn là hết, phải OAuth lại từ đầu.
- Cảnh báo hết hạn ủy quyền (FR-02-06, P0): mốc T-7 ngày, nhắc lại T-3 và T-1. Mốc này tính trên hạn refresh token, không phải access token.

**Đã đơn giản hoá 07/08/2026:** webhook báo hết hạn uỷ quyền/deauthorization của TikTok tồn tại ở scope **seller**, mà mọi uỷ quyền của ViShop đều là scope seller — không còn scope nào phải polling để bù capability. Vẫn giữ một vòng quét dự phòng mỗi 6 giờ: đọc `expires_at` nội bộ, cộng một lần gọi thử API đọc nhẹ để bắt trường hợp user thu hồi quyền mà webhook rơi. Đây là lưới an toàn cho kênh webhook, không phải cách lấy dữ liệu chính.

---

## 3. Vòng đời `shopCipher`

`shopCipher` là định danh shop bắt buộc trên mọi request nghiệp vụ (header hoặc query tuỳ endpoint), song song với `accessToken` — không phải bí mật cần mã hoá, nhưng bắt buộc đúng shop mới gọi được API của shop đó.

- **Lấy lần đầu:** ngay sau bước 4 của luồng OAuth seller (mục 1.1), qua Get Authorized Shops (`data.shops.cipher`). Một authorization có thể trả nhiều shop, mỗi shop một `shopCipher` riêng.
- **Lưu trữ:** gắn vào record `authorization`, **không** gắn vào record `shop` — đúng như bước 5 của mục 1.1 đã ghi (`authorization(shopId, shopCipher, grantedScopes, accessToken, refreshToken, expiresAt)`). _(Sửa 07/08/2026: bản trước của mục này ghi ngược — "gắn vào record shop" — mâu thuẫn với chính mục 1.1 bước 5 và với `vishop-erd.md` mục 4. Lý do đặt trên `authorization`: một shop có thể trải qua nhiều lượt uỷ quyền theo thời gian, cipher gắn với lượt uỷ quyền đang hiệu lực, không phải một thuộc tính cố định của shop.)_
- **Hiệu lực:** `shopCipher` **không đổi khi refresh token định kỳ** (mục 2) — refresh chỉ thay `accessToken`/`refreshToken`, không đụng tới cipher. Chỉ có thể đổi khi seller **uỷ quyền lại** (re-authorize, ví dụ sau khi thu hồi rồi cấp lại quyền) — trường hợp này bắt buộc gọi lại Get Authorized Shops, đối chiếu cipher mới, và ghi `event_log` (loại `authorization.reauthorized`) vì mọi lệnh gọi TikTok đang cache cipher cũ sẽ lỗi cho tới khi đồng bộ lại. Không coi `shopCipher` là hằng số vĩnh viễn, nhưng cũng không đối chiếu lại ở mọi lần refresh token thường — chỉ ở luồng re-authorize.
- **Request nghiệp vụ dùng sai cipher** (cipher cũ, cipher shop khác) → TikTok trả lỗi xác thực ở tầng shop dù `accessToken` vẫn hợp lệ. Log lỗi này phải phân biệt được với lỗi hết hạn token — nhầm hai loại sẽ khiến hệ thống refresh token sai chỗ trong khi vấn đề thật là cipher lệch.

---

## 4. Rate-limit governor (FR-00-05, P0)

Hạn mức tính theo cặp **App ID × `shopId`** — không phải hạn mức chung toàn hệ thống, không phải hạn mức chung theo tenant. Một tenant nhiều shop thì mỗi shop có ngân sách riêng, độc lập.

| Nhóm endpoint         | Hạn mức   |
| --------------------- | --------- |
| Heavy write/phân tích | 0.2–1 rps |
| Standard write        | 1–3 rps   |
| Standard read/sync    | 3–10 rps  |

**Ramp-up:** tăng dần 20–30% mỗi 10–15 phút — áp dụng khi shop mới kết nối hoặc governor vừa reset sau một lần bị TikTok giảm hạn mức đột ngột. Không nhảy thẳng lên trần khi chưa có lịch sử gọi ổn định với shop đó.

**Thứ tự ưu tiên luồng khi hạn mức căng** (§2.6): đồng bộ đơn > đẩy tồn > lấy tem > báo cáo. Governor phải chặn báo cáo trước khi chặn đồng bộ đơn — đơn hàng trễ ảnh hưởng SLA giao, báo cáo trễ vài phút không ai chết vì việc đó. Đồng bộ khởi tạo (FR-02-04) cần một hạn mức tách riêng khỏi sync định kỳ để không bị các shop khác trong cùng App ID ăn hết quota lúc đang chạy SLA ≤10 phút.

---

## 5. Retry & backoff (FR-00-02, P0)

- Mọi lệnh gọi API TikTok đi qua queue, không gọi trực tiếp từ code nghiệp vụ.
- Retry exponential backoff, **tối đa 5 lần**. Quá 5 lần → dead-letter queue, không rơi rụng âm thầm.
- Idempotency key `(platform, shopId, externalId)` (FR-00-03) áp dụng cho mọi job ghi từ sàn về — retry hoặc re-sync không được tạo bản ghi trùng. Với các thao tác ghi lên sàn (RTS, cập nhật tồn), idempotency phải xét thêm theo nghiệp vụ cụ thể (ví dụ `packageId` cho RTS) vì TikTok không đảm bảo idempotency ở phía họ cho mọi endpoint ghi.
- Lỗi 429 (rate limit) không tính là lỗi retry thường — governor phải throttle chủ động trước khi chạm 429, retry backoff chỉ là lưới an toàn cho trường hợp governor tính sai hoặc TikTok đột ngột siết hạn mức (R2).

---

## 6. Webhook cần subscribe

| Webhook                           | Dùng cho           | Ghi chú                                                                                                                                                                                                   |
| --------------------------------- | ------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Upcoming authorization expiration | FR-02-06           | Chỉ có ở scope seller — xem mục 7                                                                                                                                                                         |
| Seller deauthorization            | FR-02-05, FR-02-06 | Chỉ có ở scope seller — xem mục 7                                                                                                                                                                         |
| Return/Refund status change       | FR-09-01           | Function List gọi tắt "Return Status Change"                                                                                                                                                              |
| Cancellation Status Change        | FR-09-01           | SLA cứng 24h xử lý huỷ trước hạn giao 2 ngày làm việc — quá hạn TikTok tự duyệt và hoàn tiền                                                                                                              |
| Reverse Status Update             | FR-09-01           |                                                                                                                                                                                                           |
| Product creation                  | FR-04-10           | **Caveat: chỉ bắn cho 2 loại sản phẩm — `COMBINED_PRODUCT` và `GPR_PRODUCT`.** Không phải tín hiệu chung cho mọi listing mới, không dùng làm trigger duy nhất cho quy tắc ghép nối tự động (FR-04-10, P1) |

**Về FR-09-01:** webhook chỉ đưa dữ liệu về (đọc một chiều). SLA hoàn/huỷ 24h là của TikTok, không phải của ViShop hiển thị. Gap "seller phải quay lại Seller Center để trả lời trong hạn" đã được bịt ngày 07/08/2026 bằng `FR-09-09` — Duyệt/Từ chối làm ngay trong ViShop, đồng hồ đếm ngược tính từ mốc phát sinh yêu cầu trong payload webhook chứ không tính từ lúc ViShop nhận được. Vì thế event của ba webhook này không được phép rơi: mất event là mất luôn mốc bắt đầu đếm.

---

## 7. Vòng quét dự phòng cho uỷ quyền

> **Sửa 07/08/2026.** Mục này trước đây mô tả một khoảng trống capability: scope creator và partner không có webhook nên bắt buộc polling. Khoảng trống đó không tồn tại — ViShop không dùng scope nào ngoài scope seller, mà scope seller có webhook deauthorization/token expiration đầy đủ (FR-02-06).

Vẫn giữ một vòng quét, nhưng đổi mục đích: **lưới an toàn cho kênh webhook**, không phải nguồn dữ liệu chính.

- Chu kỳ 6 giờ, đọc `expiresAt` nội bộ của mọi uỷ quyền còn hiệu lực.
- Kèm một lần gọi thử endpoint đọc nhẹ để bắt trường hợp user thu hồi quyền phía TikTok mà webhook rơi.
- Cộng một lần quét bắt buộc ngay trước mỗi job dài dùng token đó, tránh job chạy nửa chừng thì token chết.

Không thiết kế hệ thống với giả định webhook luôn tới. Webhook rơi là chuyện bình thường, và vòng quét này là chỗ phát hiện ra.

> **Cập nhật 14/08/2026 (FR-02-06 review).** Hai gạch đầu đầu tiên đã triển khai:
> `TikTokAuthExpiryWarningService.scanExpiringSoon()` gọi `TikTokShopSyncService.probeAuthorizationHealth`
> (cùng endpoint `GET /authorization/202309/shops` mà `syncShopIdentity` dùng lúc connect, nhưng gắn
> `TikTokPriorityTier.REPORT` — ưu tiên thấp nhất — để không tranh hạn mức với đồng bộ đơn thật) cho
> từng shop trước khi kiểm tra milestone. TikTok từ chối token thẳng (401-class) đã tự động kích hoạt
> `markStatus('REVOKED', ...)` bên trong `TikTokHttpClient`/`rotate()`; probe còn bắt thêm trường hợp
> "mềm" hơn — TikTok trả 200 nhưng danh sách không còn shop này. Cũng đã sửa một bug liền kề: token đã
> hết hạn (`daysRemaining <= 0`) trước đây bị tính nhầm thành mốc "còn 1 ngày" do
> `AUTH_EXPIRY_WARNING_MILESTONES_DAYS.find(d => daysRemaining <= d)` khớp `1` với mọi số âm; giờ tách
> riêng thành `markStatus('EXPIRED', ...)`, đúng nhánh "Cần kết nối lại" (FR-02-05) thay vì cảnh báo sắp
> hết hạn. **Gạch đầu dòng thứ ba (quét bắt buộc ngay trước mỗi job dài) chưa làm** — nằm ngoài phạm vi
> đợt sửa này, vẫn là việc còn nợ.

---

## 8. Điều kiện bắt buộc trước khi nhận webhook thật

FR-00-08 (P2, đã chốt trong scope M00) chỉ lo webhook **gửi ra** (ViShop → khách hàng lớn qua Open API, M23 Phase 2). Đường **nhận vào** — endpoint nhận sự kiện thật từ TikTok cho toàn bộ danh sách ở mục 6 — thuộc FR-00-09.

**Cập nhật 07/08/2026:** khoảng trống này đã khép. FR-00-09 (bộ nhận webhook) nằm trong Function List, P0, Sprint 1-2, theo dõi tại [VIS-257](https://baotrongit.atlassian.net/browse/VIS-257). Scope M00 lên 9 FR và đã khoá. Trước khi subscribe bất kỳ webhook nào ở mục 6 vào production, phải có:

- Endpoint public nhận callback, xác thực chữ ký/signature từ TikTok.
- Idempotency theo event (tránh xử lý trùng khi TikTok retry gửi webhook).
- Hàng đợi xử lý tách khỏi request path nhận webhook — nhận xong trả 200 ngay, xử lý nghiệp vụ async.
- Dead-letter cho event xử lý lỗi, không được để mất event liên quan tới SLA 24h của FR-09-01.

Không nhận webhook thật vào hệ thống khi phần này chưa xong — nhận rồi không xử lý được sẽ tệ hơn không nhận, vì tạo cảm giác sai rằng dữ liệu đang realtime trong khi thực tế đang rơi.

---

## 9. Trường thời gian của Order API — đã đo trên sandbox

> Đo ngày 16/09/2026 trên shop sandbox VN `7494828431304066802` trong môi trường dev, bằng app key
> của chính ViShop. Mẫu: 50 đơn qua Search Orders 202309, 5 đơn đối chiếu Get Order Detail, 75 đơn
> đã đồng bộ trong DB dev.

### 9.1 `update_time` — mốc chống ghi đè

| Câu hỏi                       | Kết quả                                                                                                             |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| Có trả về không?              | 50/50 trên Search Orders, 5/5 trên Get Order Detail, hai endpoint khớp nhau                                         |
| Đơn vị                        | 10 chữ số — **giây**, không phải mili giây                                                                          |
| Có nhích thật không?          | 50/50 đơn có `update_time ≠ create_time`, 50 giá trị phân biệt                                                      |
| Chỉ nhích khi đổi trạng thái? | **Không** — 48/50 đơn có `update_time` muộn hơn mốc trạng thái cuối cùng (thường lệch ~500 s, một đơn lệch 26 ngày) |

Đây là cơ sở của cột `orders.platformUpdatedAt`: lượt đồng bộ cầm ảnh chụp cũ hơn giá trị đang lưu
thì bị bỏ, so ngay trong câu `SELECT ... FOR UPDATE` của đường ghi.

#### 9.1.1 Thí nghiệm có đối chứng — 16/09/2026

Quan sát 50 đơn ở trên chỉ cho thấy `update_time` **khác nhau giữa các đơn**, không chứng minh nó
**nhích khi chính đơn đó đổi trạng thái**. Chốt chặn đứng hay đổ là ở mệnh đề thứ hai, nên nó được đo
riêng bằng một thí nghiệm có tác động và có đối chứng, trên đơn `586081102669841418`:

| Bước                                           | `update_time` | ISO (UTC)              | `status`            |
| ---------------------------------------------- | ------------- | ---------------------- | ------------------- |
| Trước                                          | 1789544558    | `2026-09-16T07:42:38Z` | `IN_TRANSIT`        |
| `sandbox order transition` (toolkit)           | —             | —                      | → `DELIVERED`       |
| Sau                                            | 1789544677    | `2026-09-16T07:44:37Z` | `DELIVERED`         |
| **Đối chứng** `586081088162792458`, không đụng | 1789457796    | `2026-09-15T07:36:36Z` | `AWAITING_SHIPMENT` |

`update_time` nhích **119 giây**, đúng bằng khoảng cách giữa hai lần đọc; đơn đối chứng đứng yên
suốt. Cả hai giá trị đều 10 chữ số. Hai mốc này được dùng nguyên văn làm hằng số trong
`prisma-order.repository.integration.spec.ts` — chốt chặn được thử ở đúng độ phân giải của sàn thay
vì ở một mốc tự nghĩ ra.

Cách lặp lại:

```bash
tts_open_toolkit auth login
tts_open_toolkit sandbox shop list --region-code 704 --json     # region_code là mã ISO dạng số
tts_open_toolkit sandbox order list --shop-id <shop> --json      # BSO KHÔNG trả update_time
tts_open_toolkit sandbox order transition <order-id> --shop-id <shop> --timeout-ms 120000
```

BSO sandbox không trả `update_time`; phải đọc qua Order API `GET /order/202309/orders` bằng token của
chính gian hàng. Mặc định `--timeout-ms` là 10 s và thường không đủ — lần hết giờ **vẫn có thể đã
chuyển trạng thái**, nên đọc lại trạng thái trước khi thử lại, đúng như cảnh báo của toolkit.

**Giới hạn phải nhớ:** chốt chặn chỉ phân giải tới **giây**. Hai thay đổi trong cùng một giây mang
cùng một mốc và đều được ghi — cửa sổ đua bị thu hẹp, không bị đóng kín. Đóng kín cần `version`
optimistic lock cho `orders`, cố ý để ngoài phạm vi.

### 9.2 `cancel_order_sla_time` — hạn tự huỷ

| Câu hỏi                                           | Kết quả                                                                                                      |
| ------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| Có trên đơn không?                                | 74/75 đơn trong DB dev có giá trị                                                                            |
| Là hằng số hay mốc thật?                          | **Mốc thật do sàn tính** — khoảng cách tới `orderedAt` biến thiên 57–134 h, 52 giá trị phân biệt trên 74 đơn |
| `is_buyer_request_cancel` / `request_cancel_time` | **Không xuất hiện trên bất kỳ đơn nào**; 0/75 đơn có `buyerRequestedCancel = true`                           |

**Đã đo thêm 16/09/2026:** `cancel_order_sla_time` **không đổi khi đơn đổi trạng thái**. Trên đơn
`586081102669841418`, qua ba lần chuyển (`AWAITING_SHIPMENT` → `AWAITING_COLLECTION` → `IN_TRANSIT`
→ `DELIVERED`) giá trị giữ nguyên `1789750799`. `is_buyer_request_cancel` vắng mặt suốt.

**Vẫn chưa đo được:** `cancel_order_sla_time` có đổi khi người mua gửi yêu cầu huỷ hay không. Sandbox
không có đơn nào đang có yêu cầu huỷ, và không có đường tạo yêu cầu huỷ từ phía người mua. Cách đo
khi có đơn thật ở trạng thái đó:

1. Ghi lại `cancel_order_sla_time` và `autoCancelAt` trước khi yêu cầu huỷ phát sinh.
2. Sau khi webhook `Cancellation Status Change` về, gọi lại Get Order Detail cho đúng đơn đó.
3. So hai giá trị. Nếu đổi, `autoCancelAt` đang bị đồng bộ đè bằng một hạn khác nghĩa — tách hai
   trường ra trước khi hàng đợi "chờ duyệt huỷ" (FR-09-09) tin vào nó.

### 9.3 `shipping_type`

| Câu hỏi                                       | Kết quả                                                                                |
| --------------------------------------------- | -------------------------------------------------------------------------------------- |
| Có bao giờ vắng hoặc rỗng?                    | **Không** — 50/50 đơn sandbox có giá trị; 75/75 đơn DB dev đều `TIKTOK`                |
| Có token ngoài `TIKTOK` / `SELLER`?           | Không tìm thấy trong OAS bundle, 50 đơn sandbox, 75 đơn DB dev, hay tìm kiếm công khai |
| Đơn có `package_id` mà thiếu `shipping_type`? | 0                                                                                      |

`TIKTOK_DIGITAL` từng được ghi trong mã nguồn như một giá trị thứ ba của sàn, viện dẫn docv2. Không
xác minh được ở bất kỳ nguồn nào tiếp cận được (trang docv2 là SPA sau đăng nhập). Vì thế mapper
`shipping_type` trả `null` cho **mọi** token nó không biết — không dựng loại bất thường riêng cho
một ca chưa ai quan sát được. Lượt đồng bộ vẫn ghi đơn và dòng đơn, chỉ bỏ phần kiện, kèm một dòng
log có cấu trúc. Dòng log đó là số liệu để quyết định có cần làm thêm hay không.

**Không dựng lại được ca token lạ.** `sandbox order create` không có tham số `shipping_type`, và cả
5 gian hàng sandbox VN của tài khoản (`sandbox shop list --region-code 704`) đều `seller_type = 2`,
`logistics_type = 4pl`, trả `shipping_type = TIKTOK`. Nhánh `null` của mapper vì thế **chỉ được
kiểm bằng unit test**, không có xác nhận từ sàn — ghi rõ ở đây để người sau không tưởng là đã kiểm.

Shop sandbox đang dùng là `FULFILLMENT_BY_SELLER` nhưng `shipping_type = TIKTOK` toàn bộ, nên hành
vi trên shop giao hàng kiểu `SELLER` **chưa đo được** — không chặn việc nào, mapper vốn đã hiểu
`SELLER`.

---

## 10. Checklist trước khi bật kết nối thật

- [x] G0 đã qua — tài khoản App Developer (ISV), app ViShop đã tạo, 26 scope đã bật (mục 0)
- [ ] Redirect URL trỏ về endpoint callback thật, không phải tên miền trần (mục 0.2)
- [ ] Hồ sơ năng lực Enterprise Resource Planning đã submit và được duyệt
- [x] `state` OAuth nhúng `tenantId`, callback kiểm tra shop đã thuộc tenant khác chưa (BR-02-01)
- [x] Governor cấu hình đúng 3 nhóm rps ở mục 4, tách riêng ngân sách sync khởi tạo
- [ ] Job refresh token chạy trước hạn, không đợi request fail
- [ ] Refresh token **không** đối chiếu lại `shopCipher` (chỉ luồng re-authorize mới cần, xem mục 3) — không giả định cipher đổi cùng lúc với access token
- [ ] Vòng quét dự phòng uỷ quyền đã bật (mục 7) — không thiết kế với giả định webhook luôn tới
- [x] FR-00-09 (webhook receiver) đã có kế hoạch/đã build trước khi subscribe webhook thật ở mục 6
- [x] FR-02-04 (đồng bộ khởi tạo) đã build — `tiktok.initial-sync` chạy ngay sau connect, SLA ≤10
      phút xác nhận bằng tính toán (§4.6 của implementation plan), **chưa đo trên shop sandbox có
      khối lượng đơn gần 10k** — đo thực tế trước khi bật kết nối thật cho seller đầu tiên
- [ ] Dead-letter queue có người trực xử lý, không chỉ log rồi bỏ đó
