# ViShop — Environment & Deployment

**Sản phẩm:** ViShop — Phần mềm quản lý gian hàng TikTok Shop
**Chủ quản:** ViTechGroup · **Phiên bản:** v1.1 · **Ngày:** 07/08/2026 _(v1.0 · 06/08/2026 là baseline gốc, cập nhật theo bản vá §13 build-spec cùng ngày)_
**Nguồn:** Build Spec v1.2 §2.5, §2.6, §3.2, §6 (NFR), §13 (bản vá) · Function List (249 FR, đã audit — 206 FR nền + 3 FR tổ chức + 35 FR bổ sung từ UI + 5 FR tách nhánh 12–14/08/2026) · quy ước tên cột theo `vishop-erd.md` mục 0
**Phạm vi:** môi trường triển khai, pipeline deploy, chiến lược secrets, hạ tầng cho rate governor/job queue/event log, observability tối thiểu, ràng buộc multi-tenant lên thiết kế hạ tầng

---

## 1. Ba môi trường

| Môi trường     | Mục đích                                                                                      | Kết nối TikTok Shop API                                                                                                    | Dữ liệu                                                                   |
| -------------- | --------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| **dev**        | Phát triển tính năng, chạy unit/integration test với mock adapter (yêu cầu DoD §7)            | App credential riêng, không dùng token của shop thật                                                                       | Tenant giả lập, seed script, không có dữ liệu người mua thật              |
| **staging**    | Kiểm thử trước release, diễn tập migration, load test 10× (bắt buộc trước tháng 11 theo §6.1) | Một hoặc vài shop TikTok Shop thật được cấp phép cho mục đích kiểm thử, chạy dưới tier hạn mức thấp nhất của rate governor | Dữ liệu thật giới hạn quy mô nhỏ, tách biệt tenant test khỏi tenant khách |
| **production** | Phục vụ khách hàng                                                                            | Toàn bộ shop đã kết nối qua OAuth (FR-02-01)                                                                               | Dữ liệu thật, hạ tầng đặt tại Việt Nam theo §6.4                          |

TikTok Shop Partner API chưa xác nhận có sandbox app tách biệt production — đây là mục cần Tech Lead chốt trong Capability Matrix §9 trước khi khoá kiến trúc staging. Đến khi có câu trả lời, staging vận hành bằng shop thật dưới governor tier thấp nhất, không giả định có sandbox.

Staging bắt buộc mirror 1:1 topology của production (cùng schema, cùng cấu hình queue/governor) — nếu không, load test 10× ở §6.1 không có giá trị tham chiếu cho GA.

Cả ba môi trường phụ thuộc Gate G0 (tư cách Seller Partner API) đã nêu ở đầu Build Spec: chưa qua G0 thì staging/production không có token thật để kiểm thử, chỉ dev chạy được với mock adapter.

---

## 2. Pipeline deploy

Nguyên tắc: **trunk-based, migration tách biệt khỏi deploy code, không có bước thủ công nào giữa merge và staging.**

```
feature branch → PR → CI (lint, unit test, integration test mock adapter)
              → merge main → auto-deploy staging → smoke test
              → tag release → promote thủ công lên production (rolling/blue-green)
```

Ràng buộc bắt buộc cho từng bước:

- **CI phải chặn merge** nếu thiếu unit test cho logic nghiệp vụ (công thức giá vốn, tính đẩy tồn, tính lợi nhuận — DoD §7 liệt kê là bắt buộc, không phải khuyến nghị).
- **CI phải có lint rule chặn truy vấn thiếu `tenant_id`** — DoD §7 ghi rõ "Không có truy vấn nào thiếu điều kiện tenant_id" là điều kiện release, không kiểm bằng review thủ công.
- **Migration theo mô hình expand–contract**, không xoá cột/bảng trong cùng lần deploy đang phụ thuộc chúng. Lý do trực tiếp từ BR-00-01: "Không xoá cứng bản ghi giao dịch" — migration phá schema theo kiểu xoá thẳng vi phạm nguyên tắc này ở tầng hạ tầng.
- **Rollback** giữ image N-1 sẵn sàng deploy lại; vì không xoá cứng dữ liệu, rollback ứng dụng không kéo theo mất dữ liệu giao dịch.
- **Production deploy là promote thủ công có gate người duyệt**, không tự động sau staging — phù hợp nguyên tắc "hạ tầng và dữ liệu đặt tại Việt Nam, không được sai sót ở lớp production" và khớp với việc G0/G1 là gate do người quyết định, không phải máy.

---

## 3. Chiến lược secrets

Ba loại secret cần phân biệt, xử lý khác nhau:

**Token TikTok Shop (OAuth access token + refresh token của từng authorization).**
Mã hoá at-rest, không bao giờ ghi ra log — nguyên văn yêu cầu §6.3. Tự động gia hạn trước khi hết hạn (FR-02-05); thất bại thì chuyển shop sang trạng thái "Cần kết nối lại" và bắn cảnh báo, không âm thầm giữ token chết. **Sửa 07/08/2026:** mọi uỷ quyền của ViShop đều là scope `seller` — TikTok Shop không cho Public App chọn vai trò `creator`/`partner` khi uỷ quyền, mô hình 3 role đó đã bị bỏ (xem build-spec §2.2, runbook §1.2/§2). Scope `seller` có webhook deauthorization/token-expiration, nên cảnh báo hết hạn ủy quyền ở mốc 7/3/1 ngày (FR-02-06) đi bằng webhook, không polling. Chỉ giữ một vòng quét dự phòng 6 giờ ở BE để bắt trường hợp webhook rơi — không phải cơ chế chính.

**Phiên đăng nhập của người dùng.**
Xác thực dùng JWT stateless với access token và refresh token, trả trong JSON body.
API được bảo vệ nhận `Authorization: Bearer <accessToken>`; gateway và downstream xác minh
chữ ký, issuer, audience, loại token và thời hạn tại chỗ. Không lưu session đăng nhập,
không dùng cookie xác thực hoặc Redis projection. Gateway không gọi IAM introspection
trên mỗi request.

`POST /api/v1/auth/refresh` nhận `{ "refreshToken": "..." }`, hoạt động cả khi access token
đã hết hạn. IAM kiểm tra tài khoản và quyền hiện tại trước khi cấp cặp token mới. Thời hạn
mặc định: access 15 phút, refresh 7 ngày; refresh giữ thời điểm hết hạn tuyệt đối ban đầu.
Token refresh cũ còn hiệu lực đến hết hạn; không có cơ chế lưu trạng thái rotation/reuse.

Logout ghi audit, trả 204 và client xoá cả hai token. JWT đã cấp vẫn hợp lệ đến hết hạn;
không cam kết thu hồi tức thời. Tenant context lấy từ JWT đã xác minh; đổi tenant qua
`POST /api/v1/auth/switch-tenant` với Bearer access token và `{tenantId,refreshToken}`,
rồi dùng cặp token mới. Header `X-Tenant-Id` từ client không cấp hoặc đổi quyền tenant.

**Danh tính dịch vụ nội bộ (service-to-service).**
Lời gọi có người dùng chuyển tiếp Bearer access JWT; gateway và service xác minh JWT_SECRET theo cấu hình hiện tại. IAM_INTERNAL_SECRET chỉ bảo vệ endpoint introspection tương thích nội bộ; gateway không dùng endpoint này để xác thực request. Danh tính máy-với-máy ngoài phạm vi token người dùng cần contract riêng, không được giả lập user bằng header client.

**Secret hạ tầng (DB credential, signing key, client secret app TikTok).**
Lưu trong secret manager tập trung (KMS-backed), không đặt plaintext trong biến môi trường hay file cấu hình commit vào repo. Rotate định kỳ, tách biệt secret theo môi trường (dev/staging/prod dùng key khác nhau tuyệt đối).

**Chặn log lộ token ở tầng hạ tầng, không chỉ ở code.**
Structured logger dùng field allowlist (chỉ log field được khai báo rõ là an toàn), không dùng blocklist — blocklist luôn thiếu sót khi thêm field mới. Áp dụng cho mọi log pipeline, kể cả log lỗi từ TikTok trả về (BR §6.5 yêu cầu giữ nguyên mã lỗi gốc trong log — mã lỗi được giữ, nhưng payload request/response chứa token phải bị cắt trước khi ghi).

---

## 4. Hạ tầng cho rate governor + job queue + event log bất biến

### 4.1 Rate limit governor (FR-00-05, §2.6)

Hạn mức TikTok Shop đã xác minh (ghi trong Function List, đối chiếu tài liệu chính thức partner.tiktokshop.com/docv2): Heavy write/phân tích 0.2–1 rps, Standard write 1–3 rps, Standard read/sync 3–10 rps, ramp-up khuyến nghị tăng dần 20–30% mỗi 10–15 phút.

Governor implement bằng token bucket theo cặp `(shopId, apiClass)` với `apiClass ∈ {heavy_write, standard_write, standard_read}` khớp ba nhóm hạn mức trên — không dùng một bucket chung cho cả shop vì các nhóm API có trần khác nhau. Bucket lưu ở store có độ trễ thấp, chia sẻ được giữa nhiều instance xử lý (governor phải là quyết định tập trung, không phải mỗi worker tự đếm riêng — nếu không, nhiều instance cùng gọi API sẽ vượt trần thật dù mỗi instance tưởng mình còn hạn mức).

Thứ tự ưu tiên luồng theo §2.6: **đồng bộ đơn > đẩy tồn > lấy tem > báo cáo.** Thực thi bằng hàng đợi riêng theo tier, governor rút request theo thứ tự tier trước khi gọi TikTok — đảm bảo đúng yêu cầu BR-07-06 "không để in tem làm cạn hạn mức của đồng bộ đơn và đẩy tồn". Khi shop mới kết nối lại sau gián đoạn, bucket khởi động ở mức thận trọng và ramp-up theo đúng tỉ lệ 20–30%/10–15 phút đã xác minh, không mở hết hạn mức ngay.

### 4.2 Job queue (FR-00-02, §2.6)

Mọi lệnh gọi API sàn đi qua hàng đợi bền vững (broker phải sống sót qua restart process — §6.2 yêu cầu "không mất dữ liệu đơn trong mọi tình huống lỗi, hàng đợi phải bền vững"). Retry backoff luỹ thừa, tối đa 5 lần, dead-letter queue cho request thất bại vượt số lần retry — khớp nguyên văn FR-00-02. DLQ phải có cơ chế xem lại và replay thủ công, không phải nơi request biến mất.

Idempotency (FR-00-03) khoá theo `(platform, shopId, externalId)` — job queue và governor đều phải tôn trọng khoá này để re-sync không tạo bản ghi trùng, kể cả khi cùng một sự kiện bị đẩy vào queue hai lần do retry hoặc do webhook gửi lại.

### 4.3 Event log bất biến (FR-00-01, §2.5)

Schema cố định (camelCase, tên cột vật lý trùng tên field — xem `vishop-erd.md` mục 0): `entityType, entityId, eventType, payloadBefore, payloadAfter, actorType (user|system|platform), actorId, source, createdAt`. Append-only ở tầng ứng dụng là chưa đủ — role DB ghi vào bảng `event_logs` không được cấp quyền UPDATE/DELETE, chặn ở tầng grant chứ không chỉ ở convention code. Đây là cách duy nhất thực thi được câu "không sửa, không xoá" một cách đáng tin, vì logic ứng dụng có bug thì vẫn có thể vô tình update — quyền DB thì không.

Partition theo `tenantId` kết hợp `createdAt` để truy vấn debug lệch tồn theo tenant không quét toàn bảng. Retention tối thiểu theo §6.4: lịch sử biến động tồn giữ ≥2 năm.

---

## 5. Observability tối thiểu

Bốn nguồn ghi log bắt buộc, mục đích khác nhau, không gộp chung một bảng:

| Log                 | FR       | Nội dung                                                              | Phục vụ                                                                                               |
| ------------------- | -------- | --------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| **Audit log**       | FR-00-04 | user, hành động, đối tượng, IP, thời gian                             | Truy vết ai làm gì — bắt buộc cho mọi thao tác ghi, kể cả truy cập hàng loạt dữ liệu người mua (§6.3) |
| **Event log**       | FR-00-01 | Biến động tồn, chuyển trạng thái đơn/package, thay đổi giá vốn (§2.5) | Debug lệch tồn, đối soát, audit nghiệp vụ                                                             |
| **Nhật ký đẩy tồn** | FR-05-15 | SKU, shop, giá trị đẩy, kết quả, lý do lỗi, thời gian                 | Nuôi cảnh báo fail liên tiếp (FR-05-17) và dashboard vận hành                                         |
| **Nhật ký in**      | FR-07-08 | Ai, lúc nào, bao nhiêu package, lô nào, cờ "in lại"                   | Hỗ trợ in lại trong 30 ngày (FR-07-07), truy vết khiếu nại đóng gói sai                               |

Audit log và event log phục vụ hai câu hỏi khác nhau — "ai bấm nút" và "hệ thống chuyển trạng thái gì" — không gộp vì mục đích truy vấn và chính sách retention khác nhau (audit log cần tra theo user, event log cần tra theo entity).

Yêu cầu tầng hạ tầng theo §6.5, áp dụng cho cả bốn log trên:

- `trace_id` xuyên suốt một request, xuất hiện ở mọi dòng log liên quan — bắt buộc để nối một lỗi đẩy tồn với request gốc gây ra nó.
- Dashboard nội bộ theo dõi realtime: tỉ lệ lỗi API theo shop, độ sâu hàng đợi, độ trễ sync, tỉ lệ đẩy tồn thất bại — bốn chỉ số này đọc trực tiếp từ job queue, rate governor và nhật ký đẩy tồn ở mục 4, không cần hệ thống log riêng.
- Cảnh báo vận hành tự động khi hàng đợi tồn đọng hoặc tỉ lệ lỗi vượt ngưỡng — ngưỡng cụ thể do Tech Lead định nghĩa khi go-live, không có trong spec nghiệp vụ.
- Lỗi từ sàn giữ nguyên mã lỗi gốc trong cả log và thông báo cho người dùng — không dịch/diễn giải lại mã lỗi TikTok, tránh mất thông tin khi debug case thực tế (đã thấy rõ giá trị này qua các mã lỗi cụ thể mà audit Function List liệt kê ở FR-05-16, ví dụ 12052849/12052850).

---

## 6. Multi-tenant isolation — ràng buộc lên thiết kế hạ tầng/DB

FR-00-06 và §3.2 đặt cùng một yêu cầu ở hai chỗ: **mọi bảng có `tenant_id`, là index đầu tiên, và không truy vấn nào được thiếu điều kiện `tenant_id`.** Đây không phải review checklist, phải là ràng buộc thực thi được bằng máy:

- **Tầng repository/ORM bắt buộc scope tenant** — không có code path nào gọi thẳng xuống DB mà bỏ qua tenant filter. Convention "nhớ thêm WHERE tenant_id" không đủ tin cậy ở quy mô 249 FR và nhiều dev cùng viết code.
- **Row-Level Security ở tầng Postgres làm lớp phòng thủ thứ hai** — nếu một truy vấn lọt qua review mà thiếu điều kiện tenant, RLS vẫn chặn cross-tenant leak ở tầng DB. Đây là cách duy nhất biến DoD §7 checkbox "Không có truy vấn nào thiếu điều kiện tenant_id" từ lời hứa thành thứ có thể verify bằng test tự động (thử query không có tenant context, kỳ vọng trả rỗng chứ không lỗi).
- **CI lint rule** chặn merge nếu phát hiện pattern truy vấn thiếu tenant_id — chặn ở PR trước khi RLS phải vào cuộc lần đầu ở production.

Ảnh hưởng cụ thể lên các cấu phần đã mô tả ở mục 4:

- **Rate governor và job queue namespace theo `(tenant_id, shop_id)`**, không chỉ `shop_id`. BR-02-01 đảm bảo một shop TikTok chỉ thuộc một tenant tại một thời điểm nên về lý thuyết `shop_id` đã đủ duy nhất — nhưng namespace kèm tenant_id là phòng thủ rẻ, tránh phụ thuộc hoàn toàn vào một business rule ở tầng ứng dụng để giữ đúng ở tầng hạ tầng.
- **Backup không đủ, cần export theo tenant.** BR-15-06 yêu cầu khách export toàn bộ dữ liệu của họ bất cứ lúc nào kể cả sau khi hết hạn gói — hạ tầng backup toàn DB không trả lời được yêu cầu này, phải có pipeline extract riêng theo `tenant_id`, test định kỳ (§6.2 yêu cầu kiểm tra khôi phục hằng tháng — mở rộng bài kiểm tra này để bao gồm cả extract theo tenant, không chỉ restore toàn bộ).
- **Load test 10× ở §6.1 phải dùng tenant giả lập trong staging**, tách biệt khỏi tenant test thật đang chạy song song — noisy neighbor giữa tenant tải cao và tenant đang được QA thủ công sẽ làm sai kết quả cả hai.
- **Xoá dữ liệu cá nhân theo yêu cầu (§6.4)** thực thi ở đúng một tenant, giữ nguyên dữ liệu giao dịch ẩn danh cho nghĩa vụ kế toán của các tenant khác không bị ảnh hưởng — thao tác xoá phải scope tenant chặt như mọi truy vấn đọc/ghi khác, cùng cơ chế RLS.

---

## 7. Phụ thuộc và rủi ro mở

- **Địa điểm hạ tầng và nhà cung cấp chưa chốt** — câu hỏi mở #5 trong Build Spec §12, người trả lời là đội Kỹ thuật, có chặn. Tài liệu này giả định hạ tầng đặt tại Việt Nam (yêu cầu cứng §6.4) nhưng chưa có nhà cung cấp cụ thể để tính chi tiết network/latency giữa các thành phần ở mục 4.
- **Load test 10× lưu lượng ngày thường bắt buộc trước tháng 11** (§6.1) — chưa có kết quả, rate governor và job queue ở mục 4 mới là thiết kế, chưa qua kiểm chứng tải thật.
- **R11 (Build Spec §10):** hạn mức API khi in tem quy mô lớn là rủi ro trung–cao, giảm thiểu bằng pre-fetch + cache + governor (đã phản ánh ở mục 4.1) và load test 1.000 package/giờ trước GA — mục này chưa thực hiện.
- **R7:** hạ tầng chưa xác nhận chịu được đỉnh campaign — phụ thuộc trực tiếp vào việc chốt nhà cung cấp ở trên.
- **Đã chốt 07/08/2026 — không còn là rủi ro mở:** khoảng trống webhook receiver (inbound) đã tách thành `FR-00-09` riêng, P0, Sprint 1-2, xem `vishop-sprint-plan.md` mục 1. Hạ tầng cần một endpoint public dùng chung định tuyến theo `type`, xác thực chữ ký trước khi xử lý, trả 200 ngay và xử lý bất đồng bộ qua hàng đợi ở mục 4.2, dead-letter sau 5 lần thử. Giữ lại ở đây như ghi chú triển khai, không còn ở trạng thái "chưa đặc tả".
