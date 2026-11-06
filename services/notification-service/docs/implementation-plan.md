# Kế hoạch triển khai Notification-service

Ngày lập: 04/11/2026. Trạng thái: kế hoạch tổng thể; [module notification-setting](setting.md)
chỉ giữ HTTP/JPA cho notification_settings; feature/type/recipient/preference đã bỏ
khỏi code để triển khai sau. Các API/provider còn thiếu ghi rõ trong tài liệu module.
Nền hiện có: schema V1–V9, migration CLI và kiểm thử PostgreSQL; catalog enum trong code.
Đã bổ sung nền domain/application của 4 module và composition, xem
[trạng thái module](module-design.md). Các hạng mục chưa có trong setting bên dưới vẫn là backlog.
Module/API catalog đã bỏ; dùng [shared constants](constants.md) và danh mục phía FE.
Chưa có main HTTP runtime hoặc Gateway integration.
[API dự kiến](api-contract.md), [nghiệp vụ](business-rules.md), [DB](database-design.md).

## 1. Phạm vi và ưu tiên

| Nhóm | Bản đầu | Kết quả người dùng nhận được |
| --- | --- | --- |
| In-app | P0 | Chuông/badge, danh sách, chi tiết, lọc loại/nhóm/mức độ, đọc từng mục/tất cả |
| Tenant settings | P0 | Chọn tính năng, bật/tắt loại, chọn người nhận và kênh |
| Kết nối kênh | P0 | Cấu hình email, bot/chat Telegram, kiểm tra kết nối và gửi thử |
| Cá nhân | P0 | Từ chối từng loại/kênh và xem cấu hình hiệu lực |
| Gửi nền | P0 | Email và Telegram, chống trùng, retry, lịch sử và xử lý lỗi |
| Realtime | P0 | WebSocket /ws báo có thông báo mới; API luôn là nguồn nội dung |
| Thông báo hệ thống | P0 | CMS tạo nháp, chọn audience, publish; ghi nhận phạm vi gửi |
| Vận hành | P0 | Health, metrics, DLT, retry có kiểm soát, audit thao tác cấu hình/publish |
| Zalo | P1 | Bổ sung adapter và schema khi chốt contract |
| Digest | P1 | Gom cảnh báo cùng loại theo khung giờ |
| Hẹn lịch hệ thống | P1 | Publish vào thời điểm đã chọn, hủy lịch chưa chạy |

Telegram được đưa vào bản đầu theo phạm vi đã trao đổi, dù M14 đánh dấu kênh mở rộng là P1.
Bản đầu backend cung cấp API/contract cho FE; việc xây màn hình là phần tích hợp riêng.

## 2. Module đề xuất

- `notification-inbox`: thông báo, recipient, đọc/ẩn và policy đọc.
- `shared/constants`: enum feature/type/defaults dùng chung, không phải module/API.
- `notification-setting`: tenant feature/type/channel và personal preference.
- `notification-delivery`: template, routing, jobs, attempts, email/Telegram adapter.
- `notification-announcement`: draft, audience, publish và fan-out thông báo hệ thống.
- `composition`: phối hợp policy/recipient resolution với các module qua application API.
- `infra`: Gateway context, IAM adapter, Kafka, secrets adapter, scheduler, realtime.

Domain không phụ thuộc JPA/provider. Dùng CQRS/ports/adapters và transaction theo chuẩn repo.
Runtime không tự migrate; thêm Spring Boot main, dependencies, dev/start/bootJar/migrate,
Docker/Compose, Gateway route và context secret riêng khi triển khai runtime.

## 3. Các đợt triển khai

### Đợt 1 — Runtime, quyền và hợp đồng tích hợp

- Dựng runtime theo mẫu service hiện hành; giữ migration CLI riêng.
- Bổ sung quyền IAM cho notification, cấu hình Gateway HTTP và WebSocket.
- JPA mappings validate schema hiện hành, JSON mapping, optimistic locking, soft-delete filtering.
- Contract recipient/permission lookup qua IAM API hoặc projection có độ mới rõ ràng;
  không join IAM DB, không đọc repository của service khác.
- Chốt template theo type/channel; template có version, nằm trong code ở bản đầu.
- Thực thi ranh giới bất biến: notification payload không sửa sau tạo, attempts/consumed
  không sửa, outbox chỉ đổi metadata gửi, queue chỉ đổi trạng thái vận hành.
  Lập migration/DB role hoặc trigger cho ràng buộc cần cưỡng chế, có đường retention riêng.

Nghiệm thu: context giả/sai backend bị từ chối; dữ liệu tenant A không xuất hiện tại B;
schema validate và mapping đầy đủ; đọc/ghi dùng JPA, không JDBC runtime.

### Đợt 2 — In-app, catalog và cấu hình

- API inbox, detail, unread-count, read, read-all, ẩn thông báo cá nhân.
- API tenant feature/type settings và danh sách người nhận; FE dùng danh mục hardcode.
- API preference cá nhân và effective-settings kèm lý do kênh bị tắt.
- Lookup quyền giống nhau cho list/detail/count/realtime: membership đang active,
  quyền dữ liệu/shop còn hiệu lực, parent/recipient chưa xóa.
- Không có recipient explicit: resolver theo từng type; type chưa có resolver không gửi.
  Thông báo không có người hợp lệ không fan-out cho toàn tenant.
- Ngưỡng có schema/validator theo type và được service nghiệp vụ sở hữu tiếp nhận qua
  contract versioned; không chỉ lưu JSON rồi coi ngưỡng đã có hiệu lực.

Nghiệm thu: trạng thái đọc độc lập; read-all có cutoff; preference không vượt tenant;
config thay đổi dùng version; restore cùng natural key không tạo dòng trùng.

### Đợt 3 — Kết nối kênh, secrets và worker

- Chọn/triển khai secrets adapter để lưu token bot/SMTP credentials; client không tự gán
  arbitrary `connection_config.credentialsRef`/`connection_config.connectionRef`. GET chỉ trả cấu hình đã che bí mật.
- API config/test-connection/test-message, token rotation và disconnect.
- SMTP host/port/TLS/from lưu trong provider config theo connection_config.connectionRef; Telegram lưu
  bot username/chat/topic tại notification_settings và token trong secrets storage.
- Kiểm tra bot, chat và topic trước khi enable; API không chỉ tin username client nhập.
- Migration tiếp theo mở rộng channel queue/dedup cho Telegram và liên kết job với
  connection cụ thể. Snapshot destination/template phải rõ ràng khi cấu hình đổi.
- Email tạo job theo người nhận; Telegram tạo một job theo tenant/chat/type/subject/event,
  tránh gửi N bản giống nhau khi N user đủ điều kiện cùng dùng một chat.
- Telegram là shared destination: mỗi type/chat cần xác nhận audience phù hợp; không gửi
  nội dung riêng tư dựa chỉ trên quyền của một recipient. Khi chưa xác lập policy cho chat,
  giữ kênh tắt cho type đó. Không tự động thêm PII hoặc link/token mời vào group.
- Claim jobs bằng SKIP LOCKED, lease/token fencing; recheck policy/quyền trước provider call.
  Backoff cấu hình, phân biệt lỗi tạm thời/vĩnh viễn và UNKNOWN.
- Không tự retry UNKNOWN nếu có nguy cơ gửi trùng; operator xem rõ cảnh báo trước retry.
- Test-message là side effect có idempotency/rate limit/audit, chỉ gửi đích đã cấu hình.

Nghiệm thu: đổi tenant không đọc được bot/token; cấu hình invalid không enable;
hai worker không hoàn tất cùng lease; secrets không xuất hiện response/log/error;
chat nhận một tin cho cùng event; provider lỗi không làm rollback dữ liệu service nguồn.

### Đợt 4 — Consumer nghiệp vụ và realtime

- Consume event envelope theo contract; write consumed marker, notification, recipients,
  jobs/dedup/outbox nguyên tử trong transaction Notification DB.
- Lock dedup key trước quyết định cooldown; giữ 24h trừ khi severity tăng.
- Event không hợp lệ đi DLT; retry kỹ thuật không tạo lại recipient/job/outbox.
- Mapping có version từ event nguồn sang notification type, subject, permission và template.
- WebSocket `/ws`: xác thực cookie, kiểm tra origin/session, quản lý reconnect và thu hồi.
  Sau commit chỉ phát `notification.created { notificationId, tenantId }` tới user hợp lệ.
- FE refetch inbox/count khi reconnect, đổi tenant hoặc nhận signal phù hợp tenant.
  Nếu tự đếm badge từ signal sẽ sai khi mất/trùng message; count API là nguồn chuẩn.

Tích hợp đầu tiên:

| Event/loại | Việc cần làm |
| --- | --- |
| SubscriptionActivated | Mapping payload hiện có; resolve owner/admin hoặc recipient cấu hình và email qua IAM |
| MemberInvited | Event hiện chỉ có actorId/subjectId, không có email/link mời; cần contract transactional invitation riêng |
| Subscription expiring | Subscription sở hữu lịch/mốc hết hạn và phát event, Notification không tự quét DB Subscription |
| Các alert M14 | Service nguồn phát sự kiện khi producer được triển khai; đăng ký catalog không có nghĩa đã tích hợp |

Invitation: IAM quyết định token/link dùng một lần; không suy ra raw token từ hash,
không chuyển token nhạy cảm vào event audit rộng. Chốt trusted delivery command/reference,
ACL và cách resolve nội dung trước khi bật email lời mời. Transactional invitee chưa là
member không áp recipient policy của cảnh báo thành viên; phải có policy riêng được kiểm thử.

Nghiệm thu: replay event vẫn một kết quả; event tenant khác không lộ nội dung;
thu hồi membership làm mất quyền đọc/nhận signal; IAM/Subscription không gửi provider
trực tiếp trong request transaction.

### Đợt 5 — CMS thông báo hệ thống và vận hành

- Thêm schema draft/audience/publish run; notification đã phát hành là snapshot bất biến.
- CMS tạo nháp, preview audience count, publish idempotent; không sửa nội dung đã publish.
- Audience khai báo rõ ALL_ACTIVE_TENANTS hoặc tenantIds cụ thể và recipient policy;
  không suy ra “tất cả” khi thiếu audience.
- Worker fan-out theo batch, checkpoint và resume; một event nguồn materialize thành
  notification/recipient riêng từng tenant. Chống trùng per run/tenant/recipient.
- Đóng băng audience tenant tại publish; kiểm tra người nhận/quyền/policy lại trước gửi.
  Muốn đính chính tạo thông báo mới, không ghi đè bản đã đọc.
- CMS/admin xem delivery lỗi, sanitized attempt history, retry FAILED theo budget;
  không sửa/xóa attempts hoặc consumed marker qua CRUD.
- Metrics: consumer lag, DLT, pending/expired lease, oldest job age, retry, failure theo kênh,
  fan-out progress và realtime reconnect. Không đặt user/token vào metric labels.

Nghiệm thu: retry publish không nhân đôi; restart tiếp tục batch; type/kênh tắt bị tôn trọng;
CMS platform role không mặc định được đọc mọi email body/token; admin tenant chỉ thấy tenant mình.

## 4. Các thay đổi DB còn cần

Đây là backlog, chưa chỉ định số migration vì còn phụ thuộc thứ tự code triển khai.

| Hạng mục | Mục đích |
| --- | --- |
| Telegram queue/dedup và connection reference | Routing, unique theo chat, cooldown và provider adapter |
| NotificationAnnouncement draft/audience/publish run/checkpoint | CMS publish có thể resume, không sửa notification đã phát |
| Idempotency record | Cùng key/payload trả kết quả cũ, khác payload trả conflict; scope theo tenant/user/operation |
| Connection test/verification metadata | Lưu outcome đã che bí mật và revision cấu hình đã kiểm tra |
| Ngưỡng nghiệp vụ/config propagation | Revision và trạng thái áp dụng nếu source service cần nhận threshold config |
| Immutable write protection | Chặn sửa historical payload, giữ đường cập nhật trạng thái và retention |
| Retention/purge | Xóa theo thứ tự FK, giữ chống replay và thời gian bảo toàn sự kiện đã chốt |

Không thêm bảng chỉ để dựng CRUD. Trước khi thêm table cần xác định owner, lifecycle,
quyền đọc/ghi, transaction và migration/backfill.

## 5. Điều kiện hoàn thành bản đầu

- Các API P0 trong contract được triển khai/OpenAPI và nối Gateway/IAM.
- Có ít nhất một event nghiệp vụ end-to-end và một announcement hệ thống.
- In-app, realtime, email và Telegram được kiểm chứng bằng provider sandbox/mock có
  kịch bản lỗi; test integration thật chỉ dùng kết nối/đích được cấu hình cho việc test.
- Có test tenant/user isolation, membership revocation, idempotency/race, cooldown,
  immutable payload, secrets redaction, worker lease và fan-out resume.
- HTTP không phải chờ provider; migration vẫn chạy riêng.
- Docs service cập nhật trạng thái thật, source events đã hỗ trợ và các giới hạn còn lại.
