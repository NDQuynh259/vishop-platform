# ViShop — Quy ước Code cho Dev Team

**Áp dụng:** toàn bộ code nghiệp vụ Phase 1, module M00–M15
**Tham chiếu bắt buộc:** `vishop-build-spec-v1.2.md` (bản duy nhất có hiệu lực) và `vishop-function-list.md` (249 FR đã audit — nguồn chân lý cho FR-ID/priority). Về hình dạng kỹ thuật, nguồn chân lý là **repo backend `api-vishop`** (`CLAUDE.md`, `CONTRIBUTING.md`, `docs/`) — mục 0 dưới đây tóm tắt phần bắt buộc, repo thắng khi có mâu thuẫn.
**Đối tượng:** toàn bộ dev, reviewer, tech lead tham gia ViShop

Tài liệu này không lặp lại spec nghiệp vụ. Nó quy định cách viết code để spec không bị vi phạm ở tầng implementation — nơi review thường bỏ sót.

---

## 0. Nền tảng kỹ thuật — không phải lựa chọn của dev

Backend ViShop: **Java 21 LTS, Spring Boot 3.3.x, Gradle Multi-Module, PostgreSQL 16 + Flyway + Spring Data JPA, kiến trúc DDD/CQRS** (`CommandBus`/`QueryBus`), Transactional Outbox pattern, Apache Kafka làm event broker phân tán, OpenTelemetry + Micrometer Prometheus cho quan sát.

Kiến trúc bao gồm nền tảng `platform-common` và các microservices độc lập (`tenant-service`, `identity-service`, `audit-service`...). Module mới tiếp tục đi **bên trong** khung DDD/CQRS hiện hữu. Tám điều dưới đây là ràng buộc cứng, PR vi phạm bị chặn:

1. **Tạo service chuẩn cấu trúc 4 tầng:** Đăng ký trong `settings.gradle.kts`, cấu hình `build.gradle.kts` kế thừa các module `platform-common`, và phân chia đầy đủ 4 tầng (`domain`, `application`, `infrastructure`, `presentation`).
2. **Handler không biết HTTP status.** Vi phạm nghiệp vụ thì `throw new DomainException` với mã lỗi nghiệp vụ tương ứng (`EntityNotFoundException`, `ConflictException`, `BusinessRuleViolationException`), **không bao giờ** hardcode mã HTTP trong domain entity hay application handler.
3. **Không tự dựng thân lỗi.** Lỗi là RFC 9457 Problem Details do `GlobalExceptionHandler` sinh ra. `code` phải mang ý nghĩa nghiệp vụ (ví dụ: `tenant_code_already_exists`).
4. **Response 2xx trả thẳng tài nguyên**, collection độc lập trả `ListEnvelope<T>`. Toàn bộ list dùng `?limit=&cursor=` và trả `pageInfo.hasNextPage` + `pageInfo.nextCursor`; cấm `offset`, `page`, `pageSize` và bare array response.
5. **Multi-tenancy:** Mọi thao tác nghiệp vụ phải cô lập theo tenant, context được tự động inject qua `TenantContextFilter` và `TenantRecordInterceptor` (cho Kafka) vào `TenantContext`.
6. **Validate bằng Jakarta Validation.** Body validate bằng Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@Size`) trên DTO; Mọi UUID phải sinh bằng `UuidV7.nextId()` từ `platform-common:common-domain` để đảm bảo time-ordering.
7. **Che field nhạy cảm bằng mapping DTO tường minh.** Quyền `Xem giá vốn` / `Xem lợi nhuận` / `Xem thông tin người mua` hiện thực bằng cách chiếu domain model sang DTO **không khai báo** cột đó.
8. **Cổng trước khi commit:** `.\gradlew.bat compileJava` và `.\gradlew.bat test`. Toàn bộ tests phải pass 100%.

Test: **không mock database** trong integration tests — test tích hợp chạy Postgres thật và Kafka thật qua Testcontainers (`platform-common:common-test`).

---

## 1. ChannelAdapter — ranh giới bắt buộc

Quyết định kiến trúc §2.1: core không được biết TikTok là gì. Interface định nghĩa trước, TikTok là implementation đầu tiên trong chuỗi sẽ có Shopee, Lazada, Shopify/Woo, Tiki (M16, Phase 2).

```
ChannelAdapter
  ├── AuthProvider        : authorize, refresh, revoke, getScopes
  ├── ProductProvider     : listProducts, getProduct
  ├── OrderProvider       : listOrders, getOrder, listPackages
  ├── FulfillmentProvider : createPackage, shipPackage, batchShip, getLabel
  ├── InventoryProvider   : updateStock, batchUpdateStock
  └── FinanceProvider     : listSettlements, getOrderSettlement
```

**Quy tắc cứng, không có ngoại lệ "tạm thời":**

- Không một dòng logic nghiệp vụ nào (service, use-case, job) được import trực tiếp SDK hoặc HTTP client của TikTok. Mọi lời gọi API sàn đi qua interface adapter.
- SDK/HTTP client TikTok chỉ được reference bên trong package implement adapter (`TikTokChannelAdapter` hoặc tương đương). Import ngoài phạm vi này là vi phạm, block PR.
- Model trả về từ adapter là domain model của ViShop (`Order`, `Package`, `MerchantSku`...), không phải response shape thô của TikTok. Việc map response → domain model nằm trong adapter, không nằm ở service layer.
- Lỗi từ TikTok đi qua adapter phải giữ nguyên **mã lỗi gốc** (yêu cầu NFR §6.5) — adapter không được nuốt hoặc đổi tên mã lỗi thành generic error.
- Feature/business logic chỉ được gọi vào adapter qua interface (`AuthProvider`, `ProductProvider`...), không gọi thẳng class implementation. Đây là điều kiện để Shopee/Lazada cắm vào M16 mà không đụng core.

Review PR chạm vào integration TikTok: reviewer grep import SDK TikTok ngoài thư mục adapter — thấy là reject, không thương lượng.

---

## 2. Idempotency key — bắt buộc cho mọi ghi từ webhook/sync

FR-00-03 (P0, M00): mọi bản ghi từ sàn khoá theo `(platform, shopId, externalId)`.

Áp dụng cho **mọi** write path nhận dữ liệu từ TikTok — không chỉ webhook, cả polling/sync định kỳ và sync thủ công (FR-02-10):

- Order: unique `(platform, shopId, externalOrderId)` — đúng ràng buộc §3.2.
- Package: unique `(platform, shopId, externalPackageId)`.
- Bất kỳ entity mới nào đồng bộ từ sàn ở M16 (Shopee, Lazada...) phải theo cùng pattern `(platform, shopId, externalId)`, không tự nghĩ khoá riêng.

Cách làm đúng: `INSERT ... ON CONFLICT (platform, shopId, externalId) DO UPDATE` hoặc `upsert` tương đương ở tầng repository — không phải `SELECT` rồi `if not exists then insert` ở tầng service. Race condition giữa webhook và job sync định kỳ chạy cùng lúc cho cùng order là tình huống thật, không phải giả định lý thuyết — đây chính là lý do FR-02-07 yêu cầu "kết nối lại kéo bù đơn phát sinh trong lúc gián đoạn, không trùng".

Job đồng bộ lại (re-sync) từ sàn **không được tạo bản ghi trùng, chỉ update trạng thái + ghi log** — đây là AC đã chốt ở M06. Test case bắt buộc cho mọi sync job: gọi cùng một externalId hai lần liên tiếp, verify chỉ có một bản ghi.

---

## 3. Event-log-first — mọi thay đổi trạng thái đi qua event log trước

§2.5: mọi biến động tồn kho, chuyển trạng thái đơn/package, thay đổi giá vốn ghi bản ghi append-only vào `event_log` (FR-00-01, P0):

```
event_log: entityType, entityId, eventType, payloadBefore, payloadAfter,
           actorType (user|system|platform), actorId, source, createdAt
```

Không sửa, không xoá — đây là nền tảng debug lệch tồn và audit. Ba việc không được làm:

- Không ghi event log **sau khi** đã commit thay đổi trạng thái ở bảng nghiệp vụ trong một transaction khác. Event log và state change phải cùng transaction, hoặc dùng outbox pattern nếu khác datastore — không có khoảng hở giữa hai bước. Outbox đã có sẵn trong repo (bảng `outbox_events` + `OutboxRelayService` ở scheduler), dùng nó chứ không dựng cơ chế thứ hai.
- Không bỏ qua event log cho thao tác "nhỏ" (sửa số lượng phân bổ tồn thủ công — FR-06-22, điều chỉnh kiểm kho — FR-05-04). Spec không có khái niệm "thay đổi không đáng ghi log".
- Không dùng `audit_logs` (FR-00-04 — ai, thao tác gì, đối tượng nào, IP, thời gian) thay cho `event_log`. Hai bảng khác mục đích: audit_logs trả lời "ai làm gì", event_log trả lời "trạng thái đã đổi thế nào" — nhiều thao tác cần ghi cả hai.

`payloadBefore`/`payloadAfter` phải là snapshot đủ để tái dựng lại giá trị tại thời điểm đó — không ghi delta khi thiếu context để hiểu delta đó nghĩa là gì.

---

## 4. Multi-tenant isolation

FR-00-06 (P0): mọi truy vấn bắt buộc `tenantId`. Ràng buộc §3.2: mọi bảng có `tenantId` làm index đầu tiên.

- Không có truy vấn nào — kể cả job nền, kể cả script migration dữ liệu, kể cả query phục vụ debug production — được thiếu điều kiện `tenantId`. Đây là hạng mục cuối cùng trong Definition of Done (§7): "Không có truy vấn nào thiếu điều kiện tenantId".
- Filter theo tenant nằm ở tầng repository/query builder, không phó mặc cho service layer nhớ truyền vào. Nếu ORM/query layer hỗ trợ scope mặc định theo tenant (row-level security hoặc middleware bắt buộc `tenantId`), dùng nó — không dựa vào kỷ luật cá nhân của người viết code sau này.
- Data scope theo gian hàng (FR-01-05) và quyền cấp trường (FR-01-06 — `Xem giá vốn`, `Xem lợi nhuận`, `Xem thông tin người mua`) chặn ở **tầng API**, không chỉ ẩn ở UI (NFR §6.3, AC M01: "API response cũng không chứa giá trị thật"). Trả field bị khoá bằng `null` hoặc lược field khỏi response, không trả giá trị thật rồi để frontend che.
- Reviewer bất kỳ PR nào thêm bảng mới hoặc query mới: việc đầu tiên kiểm tra là `tenantId` có trong WHERE clause và có index không, trước khi đọc logic nghiệp vụ.

---

## 5. Rate-limit Governor — tích hợp và thứ tự ưu tiên

FR-00-05 (P0): rate limit governor theo shop, có thứ tự ưu tiên luồng (§2.6). Số liệu đã xác minh với tài liệu TikTok (partner.tiktokshop.com/docv2/page/rate-limits): Heavy write/phân tích 0.2–1 rps, Standard write 1–3 rps, Standard read/sync 3–10 rps. Ramp-up khi tăng tải: tăng dần 20–30% mỗi 10–15 phút, không nhảy thẳng lên trần.

**Mọi lệnh gọi API TikTok đi qua queue có governor — không có lời gọi "tắt qua" trực tiếp**, kể cả cho thao tác gấp hay debug thủ công trên production. Governor quản lý hạn mức theo từng shop, không theo tenant hay global — hai shop cùng tenant không dùng chung ngân sách rate limit.

Thứ tự ưu tiên luồng theo §2.6 (không được đảo):

1. Đồng bộ đơn
2. Đẩy tồn
3. Lấy tem
4. Báo cáo

Hệ quả trực tiếp: **in tem không được phép làm cạn hạn mức của đồng bộ đơn** (BR-07-06 nhắc lại nguyên tắc này riêng cho M07 vì đây là nơi dễ vi phạm nhất — batch in 300+ package cùng lúc). Implement governor phải là hàng đợi có priority thật (priority queue hoặc nhiều queue riêng theo priority level), không phải một queue FIFO chung rồi hy vọng traffic pattern tự nhiên giữ đúng thứ tự.

Queue phía dưới governor (FR-00-02): retry backoff luỹ thừa, tối đa 5 lần, dead-letter queue. Package/order rơi vào dead-letter phải xuất hiện ở danh sách Bất thường (FR-06-21), không nằm im trong DLQ chờ ai đó tình cờ phát hiện.

---

## 6. Lỗi API TikTok đã audit — xử lý như hành vi bình thường của hệ thống

Đây là điểm dev hay làm sai nhất: coi các case dưới đây là "edge case hiếm gặp" rồi xử lý qua loa bằng try/catch chung chung. Cả bốn case này đã được audit và **xảy ra thường xuyên trong vận hành thật** của seller TikTok VN — code phải có nhánh xử lý riêng, có UI thông báo cụ thể, không phải error message chung.

### 6.1 SKU combo bị từ chối bởi Update Inventory API

FR-05-16 (P0, M05): API `Update Inventory` trả lỗi `12052849`/`12052850` khi cố sửa tồn trực tiếp cho SKU combo. Đổi kho (thêm/bớt warehouse) cũng bị chặn tương tự (`12052533`/`12052530`/`12052531`/`12019028`).

Xử lý đúng: **không bao giờ gọi Update Inventory trực tiếp cho SKU đã xác định là combo**. Tầng service tính và đẩy tồn combo theo công thức BR-05-03 (`available = MIN(available SKU con ÷ số lượng trong combo)`) rồi đẩy giá trị đó cho từng SKU sàn map tới combo — không đẩy nguyên request combo lên. Nếu response vẫn trả `12052849`/`12052850`, đó là bug ở logic phân loại combo/non-combo phía ViShop, không phải lỗi tạm thời cần retry.

### 6.2 Giới hạn 100 SKU biến thể/sản phẩm

FR-05-16 cùng chỗ: một sản phẩm tối đa 100 SKU biến thể, vượt trả lỗi `12052050`.

Xử lý đúng: validate số lượng biến thể **trước khi** gửi batch update, không đợi API trả lỗi rồi parse message. Với batch update tồn theo lô (tối đa 500 SKU/lần theo `Update Stock Operation Settings` — ghi chú FR-05-14), nếu một sản phẩm trong lô vượt 100 biến thể, tách riêng sản phẩm đó ra khỏi lô và báo lỗi cụ thể theo sản phẩm — không để một sản phẩm vượt giới hạn làm fail cả lô 500 SKU (nguyên tắc chung: lỗi từng phần không chặn cả lô, DoD §7).

### 6.3 Dữ liệu người nhận bị mask

FR-06-08 (P0, M06): `Get Package Detail` trả số điện thoại/tên người nhận bị che một phần khi đơn dùng platform logistics — ví dụ `(+1)213-***-1234`.

Xử lý đúng: UI hiển thị đúng giá trị mask nhận được, **không cố unmask, không báo lỗi "thiếu dữ liệu", không hiển thị field trống**. Phân biệt đủ ba trạng thái _có dữ liệu_ / _sàn che_ / _chưa đồng bộ_, ô bị che kèm dấu "Sàn che". Tìm kiếm theo số điện thoại không khớp được đơn bị che thì phải nói ra, không im lặng trả về ít kết quả hơn. Xuất Excel giữ nguyên chuỗi bị che. Đây là dữ liệu hợp lệ ở trạng thái mask — vận hành (đóng gói, giao hàng) phải làm việc được với dữ liệu này vì phần lớn đơn TikTok Shipping sẽ ở dạng này. Nếu tính năng nào cần số điện thoại đầy đủ (ví dụ liên hệ khách khi có vấn đề giao hàng), phải thiết kế fallback riêng (liên hệ qua kênh sàn), không giả định trường này luôn đầy đủ.

### 6.4 Các quirk đã audit khác — áp dụng cùng nguyên tắc "bình thường hoá", không phải danh sách đầy đủ

| FR-ID     | Quirk đã audit                                                                                                                                               | Nguyên tắc xử lý                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| FR-03-02  | `Search Products` chỉ trả key properties, phải gọi thêm `Get Product` theo từng `productId` (N+1)                                                            | **Đã chốt:** N+1 không nằm trên đường đi của người dùng. Màn danh sách đọc bản sao trong CSDL, kèm mốc "cập nhật lúc…"; việc gọi sàn dồn vào job quét theo lịch đêm chạy qua hàng đợi trong hạn mức Rate Governor của shop. Hết hạn mức thì dừng và chạy tiếp lượt sau, log rõ số SKU còn lại — cấm cắt bớt im lặng. Nút làm mới từng sản phẩm tốn đúng một lượt gọi                                                                                                                                                                                           |
| FR-06-15b | Tham số `handoverMethod` trong Ship Package/Batch Ship Package sẽ bị loại bỏ với seller SEA (đã có changelog TikTok)                                         | Không hard-code logic phụ thuộc field này; đọc response theo optional field. **Đã chốt:** không gửi field này cho thị trường SEA ngay từ đầu; giá trị theo market nằm trong cấu hình adapter, không rải trong code nghiệp vụ — sàn gỡ hẳn thì chỉ sửa adapter                                                                                                                                                                                                                                                                                                  |
| FR-06-11  | Nguồn đơn affiliate (Shop/Video/Live/...) nằm ở endpoint riêng `Search Seller Affiliate Orders`, không phải Order API chính; lookback tối đa 3 tháng/lần gọi | Thiết kế job đồng bộ riêng cho nguồn đơn, không gộp vào job sync Order. **Đã chốt:** job chạy mỗi giờ cho cửa sổ 7 ngày gần nhất; shop mới kết nối thì backfill 3 tháng đúng một lần; đơn cũ hơn 3 tháng chốt vĩnh viễn `UNKNOWN` và ngừng retry — retry vô hạn cho dữ liệu sàn không còn trả về là đốt hạn mức vô ích. Cấm mặc định gán một nguồn cụ thể cho đơn chưa xác định                                                                                                                                                                                |
| FR-09-01  | Seller phải xử lý yêu cầu huỷ trong 24h, quá hạn TikTok tự duyệt và hoàn tiền                                                                                | Đây là rủi ro mất doanh thu thật, không phải thông tin tham khảo — alert phải bắn sớm đủ để vận hành kịp xử lý trong cửa sổ 24h, không đợi gần hết hạn mới cảnh báo. **Đã chốt:** thao tác Duyệt/Từ chối nằm ở `FR-09-09`; đồng hồ đếm ngược tính từ mốc phát sinh yêu cầu trong payload webhook, không tính từ lúc ViShop nhận được                                                                                                                                                                                                                           |
| FR-04-10  | Webhook Product creation chỉ bắn cho `COMBINED_PRODUCT`/`GPR_PRODUCT`, không phải tín hiệu chung cho mọi listing mới                                         | Không dùng webhook này làm trigger duy nhất. **Đã chốt:** nguồn kích hoạt chính là quét delta theo `updateTime` mỗi 30 phút, cộng quét toàn bộ mỗi đêm; webhook chỉ là đường tăng tốc. Thứ tự quy tắc ghép: trùng mã SKU trước, trùng mã vạch sau. Khớp nhiều kết quả thì **không** tự ghép — đẩy vào hàng chờ duyệt tay, vì ghép sai tốn nhiều công sửa hơn một lần xác nhận                                                                                                                                                                                  |
| FR-02-06  | Webhook hết hạn uỷ quyền có thể rơi, không được coi là kênh duy nhất                                                                                         | **Đã chốt và đơn giản hoá 07/08/2026:** mọi uỷ quyền của ViShop là scope seller, mà scope seller có webhook deauthorization/token expiration — không còn scope nào phải polling để bù capability _(bản trước ghi creator/partner phải polling; mô hình 3 role đó đã bỏ)_. Vẫn giữ vòng quét dự phòng 6 giờ, cộng một lần quét bắt buộc ngay trước mỗi job dài có dùng token đó — quét-trước-khi-dùng chặn được lỗi tại chỗ, quét theo lịch thì không. Mất quyền thì hạ đúng tính năng phụ thuộc scope đó sang trạng thái khoá kèm lý do, không làm fail cả job |
| FR-00-05  | TikTok công bố hạn mức theo dải (0.2–1 / 1–3 / 3–10 rps), không phải số cố định                                                                              | **Đã chốt:** mặc định lấy cận dưới của dải; ramp +25% mỗi 15 phút tới cận trên khi không gặp 429; gặp 429 thì tụt thẳng về cận dưới và khoá ramp 15 phút. Thứ tự nhường quota: đồng bộ đơn > đẩy tồn > lấy tem > báo cáo — luồng ưu tiên thấp bị **hoãn**, không bị huỷ. Toàn bộ số nằm trong bảng cấu hình theo shop, đổi không cần deploy                                                                                                                                                                                                                    |
| FR-00-09  | Webhook đến muộn, đến trùng, hoặc đến khi handler phía sau đang lỗi                                                                                          | Lưu payload **nguyên văn trước khi** xử lý để phát lại được. Chống trùng theo mã sự kiện của sàn, không theo nội dung. Mốc thời gian luôn lấy từ payload, không lấy thời điểm nhận — lấy sai mốc là sai đồng hồ 24h của FR-09-09. Quá 5 lần thử thì vào dead-letter kèm cảnh báo, cấm bỏ qua im lặng                                                                                                                                                                                                                                                           |
| FR-05-14  | SLA đẩy tồn ≤30s va vào hạn mức ghi 1–3 rps của sàn                                                                                                          | **Đã chốt:** gom biến động theo cửa sổ 5 giây, gộp theo shop, đẩy lô ≤500 SKU. SLA ≤30s đo ở **p95**, không phải mọi trường hợp — ghi rõ cách đo trong test plan để QA không đo sai rồi báo lỗi nhầm. Cạn hạn mức thì ưu tiên SKU sắp hết hàng trước, vì lệch tồn ở SKU sắp hết mới gây bán vượt                                                                                                                                                                                                                                                               |
| FR-05-16  | Update Inventory từ chối SKU combo (12052849/12052850), chặn đổi kho (12052533/12052530/12052531/12019028), giới hạn 100 biến thể (12052050)                 | **Đã chốt:** chặn ở phía ViShop **trước khi** gọi ra, không để sàn trả lỗi rồi mới xử lý. Quá 100 biến thể thì chặn kèm thông báo giới hạn; SKU combo tính theo BR-05-03 rồi đẩy SKU con; đổi kho không cho làm qua đường đẩy tồn. Mọi mã lỗi đã biết map sang một câu tiếng Việt có hành động cụ thể — người vận hành không đọc mã lỗi số                                                                                                                                                                                                                     |
| FR-12-10  | `Get Payments API` hiện không khả dụng cho thị trường SEA (bao gồm VN)                                                                                       | **Đã chốt:** không chờ TikTok/BD. Tiền về đi qua một cổng nạp duy nhất với nhiều bộ tiếp nhận; v1 chỉ có bộ tiếp nhận file sao kê nhập tay. Logic đối chiếu không được biết dữ liệu đến từ đâu, để mai sàn mở API thì thêm bộ tiếp nhận chứ không sửa phần đối chiếu                                                                                                                                                                                                                                                                                           |

Nguyên tắc chung: khi gặp lỗi/giới hạn API TikTok **đã có trong bảng trên**, code phải có nhánh xử lý named rõ ràng (không phải catch-all), thông báo lỗi cho người dùng có nội dung hành động được, và giữ nguyên mã lỗi gốc trong log (NFR §6.5). Khi gặp lỗi TikTok **chưa có trong bảng trên**, ghi vào API Capability Matrix (§9 spec), báo Tech Lead — không tự quyết cách xử lý rồi ship, vì đây là dấu hiệu spec còn thiếu audit ở chỗ đó.

---

## 7. Git branch & PR convention

**Branch:**

```
feature/<module>-<fr-id>-<slug>     ví dụ: feature/M05-fr-05-16-combo-stock-push
fix/<module>-<fr-id>-<slug>         ví dụ: fix/M06-fr-06-15b-handover-method
chore/<slug>                        việc không gắn FR cụ thể (nâng cấp dependency, refactor nội bộ)
```

- Một branch gắn một hoặc một nhóm FR liên quan chặt (ví dụ FR-06-15a/15b/15c cùng luồng RTS đi chung một branch hợp lý). Không gộp FR không liên quan vào cùng branch vì lý do "tiện".
- Branch chạm vào adapter TikTok bắt buộc prefix đúng module (`M02` cho auth, `M03` cho product, `M05` cho inventory, `M06` cho order/package, `M07` cho label).

**PR:**

- Tiêu đề PR giữ nguyên FR-ID để truy vết ngược (nguyên tắc đã chốt ở đầu spec): `[FR-05-16] Đẩy tồn combo qua công thức BR-05-03, chặn gọi trực tiếp Update Inventory cho combo`.
- Mô tả PR liệt kê: FR-ID liên quan, Business Rule áp dụng (nếu có), Acceptance Criteria đã cover, và AC nào **chưa** cover kèm lý do.
- PR đụng schema hoặc thay đổi hành vi ghi dữ liệu: bắt buộc nêu rõ tác động tới `tenantId` isolation, idempotency key, và event_log — ba mục ở phần 2–4 tài liệu này.
- Không merge PR thiếu unit test cho logic nghiệp vụ (công thức giá vốn, tính đẩy tồn, tính lợi nhuận — DoD §7 liệt kê đây là bắt buộc, không phải nice-to-have).

---

## 8. Checklist code review — tham chiếu FR-ID

Dựa trên Definition of Done §7 của spec. Review áp dụng đủ checklist dưới cho PR chạm shared/persisted state hoặc gọi API sàn; PR thuần UI/copy không cần đủ bộ.

- [ ] Đáp ứng đủ Acceptance Criteria của FR liên quan (đối chiếu đúng mã FR trong `vishop-function-list.md`, không phải diễn giải lại theo trí nhớ)
- [ ] Tuân thủ Business Rule liên quan (BR-xx-xx) — nêu rõ trong PR description BR nào áp dụng
- [ ] Quyền kiểm tra ở tầng API, không chỉ ẩn ở UI (đặc biệt PR chạm `Xem giá vốn`/`Xem lợi nhuận`/`Xem thông tin người mua` — FR-01-06)
- [ ] Có unit test cho công thức giá vốn (BR-03-01), tính đẩy tồn (BR-05-01/BR-05-03), tính lợi nhuận (M10) nếu PR chạm các logic này
- [ ] Có integration test dùng mock `ChannelAdapter` cho luồng gọi API sàn — không test qua API TikTok thật trong CI
- [ ] Event log / audit log ghi đủ theo phần 3–4 tài liệu này, cùng transaction với thay đổi trạng thái
- [ ] Lỗi từng phần không làm hỏng cả lô (batch RTS, batch in tem, batch cập nhật SKU); message lỗi cho người dùng hành động được, giữ mã lỗi gốc TikTok
- [ ] Mọi truy vấn mới có điều kiện `tenantId`, có index phù hợp
- [ ] Idempotency key `(platform, shopId, externalId)` áp dụng đúng cho mọi write path mới từ sàn (webhook lẫn polling)
- [ ] Nếu PR chạm case đã audit ở phần 6 (combo SKU, giới hạn 100 SKU, dữ liệu mask, hoặc bảng quirk 6.4): xử lý bằng nhánh riêng có tên rõ, không catch-all
- [ ] Có metric/log để xác nhận tính năng chạy đúng trên production sau khi merge (DoD §7, mục "Đo được")
- [ ] Nếu PR chạm rate limit/queue: không phá vỡ thứ tự ưu tiên §2.6 (đồng bộ đơn > đẩy tồn > lấy tem > báo cáo)

Reviewer từ chối PR thiếu bất kỳ mục nào áp dụng được cho phạm vi thay đổi — không approve kèm comment "để sau", vì DoD không có khái niệm "xong một phần".
