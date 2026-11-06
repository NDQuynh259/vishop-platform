# API Notification — hợp đồng dự kiến

Ngày lập: 04/11/2026. Phần lớn endpoint dưới đây là kế hoạch. Các endpoint setting đã có
code chỉ gồm GET list/detail và PUT channel; payload thực tế trong [module notification-setting](setting.md); xem tài liệu đó
để phân biệt phần đã triển khai và backlog provider/IAM. Module/API catalog đã được bỏ;
FE quản lý danh mục theo [shared constants](constants.md). Notification chưa có main
runtime/Gateway wiring để phục vụ HTTP độc lập.
[Thứ tự triển khai](implementation-plan.md). Không tạo API CRUD cho mọi bảng.

## Quy ước

- Base path HTTP: `/api/v1`; CMS: `/api/v1/cms`. Controller dùng `ApiPaths.V1`.
- Tenant/user được lấy từ Gateway context đã ký; request tenant không tự truyền tenantId/userId
  để truy cập hộ. CMS là namespace có quyền platform và audience tường minh.
- List có limit mặc định 20, tối đa 100; cursor opaque với thứ tự ổn định
  `created_at DESC, id DESC`. Response theo envelope/error convention của common-web.
- Response setting gồm `configured`, giá trị configured/default/effective và `version`.
  Missing row dùng version 0 cho create, xử lý unique race thành 409.
- PATCH cấu hình yêu cầu version; sai version 409. PUT recipientIds thay thế toàn bộ
  danh sách của rule; kiểm tra membership/quyền từng người, all-or-nothing.
- GET secret config chỉ trả `credentialsConfigured`, metadata/masked value và
  `connectionVerified`; không trả token/password hoặc secret-store path.
- Secret update nhận write-only `botToken`/SMTP credentials qua HTTPS; bỏ qua trường secret
  nghĩa giữ nguyên, xóa secret cần thao tác disconnect rõ ràng. Redact cả lỗi provider.
- POST có side effect gửi thử/publish/retry yêu cầu Idempotency-Key. Cùng scope/key và
  payload trả kết quả cũ; khác payload 409. Không dùng consumed_messages thay HTTP idempotency.
- 401 chưa xác thực; 403 thiếu quyền; 404 đối tượng ngoài scope hoặc không tồn tại;
  409 version/state/idempotency conflict; 422 cấu hình sai; 429 rate limit.
  Lỗi provider là outcome của job, không biến request đã enqueue thành lỗi giả.

## Quyền đề xuất cần bổ sung IAM

| Quyền | Phạm vi |
| --- | --- |
| notifications.view | Inbox của chính người dùng trong tenant |
| notifications.settings.view | Xem cấu hình tenant đã che bí mật |
| notifications.settings.manage | Sửa feature/type/recipient/channel và test kết nối |
| notifications.deliveries.view | Xem trạng thái gửi trong tenant, không tự cấp quyền đọc nội dung nghiệp vụ |
| notifications.deliveries.retry | Retry lỗi hợp lệ trong tenant |
| PLATFORM_ADMIN | CMS announcement và vận hành platform theo policy; catalog thay đổi qua code release |

Tùy chọn cá nhân yêu cầu membership active và chỉ sửa bản thân.
Quyền notifications.view không thay thế required_permission/shop scope của từng notification.

## 1. In-app — P0

| Method | Endpoint | Hành vi |
| --- | --- | --- |
| GET | /notifications | Danh sách cá nhân; filter category, featureCode, type, severity, read, shopId, cursor, limit |
| GET | /notifications/unread-count | Count theo policy đọc hiện hành, chỉ chính user/tenant |
| GET | /notifications/{id} | Nội dung và trạng thái đọc của chính người nhận |
| PATCH | /notifications/{id}/read | Đánh dấu đọc idempotent; giữ readAt đầu tiên |
| POST | /notifications/read-all | Body before: ISO timestamp; đánh dấu các mục hợp lệ đến cutoff, trả updatedCount |
| DELETE | /notifications/{id} | Ẩn bằng deleted_at của recipient cá nhân; không xóa notification chung |

Item: id, type, featureCode, category, severity, title, body, actionUrl, shopId,
occurredAt, createdAt, readAt. actionUrl chỉ là đường dẫn/đích hợp lệ đã kiểm soát.
Không có POST tạo thông báo tùy ý hoặc PATCH sửa title/body từ client.

## 2. Nhóm tính năng — kế hoạch, chưa triển khai

FE hardcode danh mục theo cùng mã trong shared/constants backend. Không có API catalog
hoặc UUID/version metadata danh mục. Backend vẫn validate mã trước khi ghi cấu hình.

| Method | Endpoint | Hành vi |
| --- | --- | --- |
| GET | /notification-settings/features | Feature settings của tenant, kèm giá trị hiệu lực |
| PATCH | /notification-settings/features/{featureCode} | Body enabled, version; bật/tắt nhóm tính năng |

V7 bỏ hai bảng catalog; settings/history vẫn lưu business code. Constants không xác nhận
entitlement, provider readiness hoặc source event đã sẵn sàng. Filter category/feature ở
API nghiệp vụ phải map sang type codes từ constants trước khi truy vấn DB, không JOIN
catalog table. Threshold schema vẫn chờ contract của domain nguồn.

## 3. Loại thông báo và người nhận — kế hoạch, chưa triển khai

| Method | Endpoint | Hành vi |
| --- | --- | --- |
| GET | /notification-settings/types | Cấu hình từng loại và nguồn kế thừa |
| GET | /notification-settings/types/{type} | Rule, recipients, threshold schema và effective channels |
| PATCH | /notification-settings/types/{type} | enabled, channels, thresholds, cooldownSeconds, version |
| PUT | /notification-settings/types/{type}/recipients | Body recipientIds, version; thay thế danh sách user |
| GET | /notification-settings/types/{type}/recipient-candidates | Tìm member hợp lệ có quyền, paging/filter q |

JSON channels hiện triển khai là mảng enum `IN_APP`/`EMAIL`/`TELEGRAM`/`LARK`. Không tự thêm kênh tùy ý. Rule absent kế thừa
catalog defaults. cooldownSeconds tối thiểu 86400. Threshold sai schema trả 422;
không có source config contract thì trả unsupported thay vì báo đã áp dụng.
Danh sách rỗng nghĩa dùng resolver theo type; nếu type không có resolver thì không gửi,
response giải thích. Recipient candidates và mutation dùng cùng kiểm tra quyền.
Rule dùng unique tenant/type, API upsert cùng khóa; restore nếu dòng đã xóa mềm.

## 4. Cấu hình kết nối kênh — đã triển khai

Base path `/api/v1`; tenant lấy từ GatewayPrincipal. Chi tiết schema, ví dụ và
response đã che thông tin nhạy cảm: [API cấu hình kết nối](setting.md).

| Method | Endpoint | Hành vi |
| --- | --- | --- |
| GET | /notification-settings/channels | Danh sách IN_APP/EMAIL/TELEGRAM/LARK và trạng thái cấu hình |
| GET | /notification-settings/channels/{channel} | Chi tiết kênh, connectionConfig công khai và version |
| PUT | /notification-settings/channels/{channel} | Upsert enabled, version và connectionConfig theo enum trên URL |

GET yêu cầu `notifications.settings.view`; PUT yêu cầu `notifications.settings.manage`.
PUT có `connectionConfig` sẽ thay thế toàn bộ cấu hình; không có field này thì giữ
nguyên cấu hình và chỉ cập nhật enabled. Không có discriminator type trong JSON.
Credentials dùng secret-store references; API không trả chúng trong response.
External provider hiện chưa có adapter nên chỉ lưu disabled, enable trả 422.
Version cũ trả 409; field sai schema trả 422; enum sai trả 400.

Test connection, gửi thử và disconnect là kế hoạch, chưa triển khai.
## 5. Tùy chọn cá nhân — kế hoạch, chưa triển khai

| Method | Endpoint | Hành vi |
| --- | --- | --- |
| GET | /notification-preferences | Các tùy chọn type/channel của chính user |
| PATCH | /notification-preferences/{type} | enabled, channels, version |
| DELETE | /notification-preferences/{type} | Soft-delete preference, quay về kế thừa tenant |
| GET | /notification-preferences/effective | Kết quả policy theo type/channel và lý do bị tắt |

Effective response phân biệt personalDisabled, tenantChannelDisabled,
tenantFeatureDisabled, tenantTypeDisabled, providerNotReady, featureUnavailable.
Không trình bày recipient eligibility thành quyền đã được cấp; quyền phải được kiểm tra
theo đối tượng lúc thực sự phát sinh. Không trả cấu hình bot/secret cho người chỉ có quyền cá nhân.

## 6. Lịch sử gửi và retry — P0

| Method | Endpoint | Hành vi |
| --- | --- | --- |
| GET | /notification-deliveries | Tenant history; filter channel, status, type, time range, cursor |
| GET | /notification-deliveries/{id} | Metadata job và outcome, destination được mask theo quyền |
| GET | /notification-deliveries/{id}/attempts | Lịch sử append-only, lỗi đã redacted |
| POST | /notification-deliveries/{id}/retry | 202; chỉ FAILED retryable theo policy/budget, audit người thao tác |

SENT/PROCESSING không được retry; UNKNOWN cần workflow review riêng, không tự replay.
Job bị tắt kênh/mất quyền thì không gửi lại. Counter không vượt max_attempts: cấp budget
mới có kiểm soát hoặc tạo resend generation qua migration/contract đã chốt.
Không PATCH status tùy ý, không sửa/xóa attempt và không sửa destination/template snapshot.

## 7. Thông báo hệ thống từ CMS — P0

| Method | Endpoint | Hành vi |
| --- | --- | --- |
| GET | /cms/notification-announcements | Danh sách draft/publish run |
| POST | /cms/notification-announcements | Tạo draft có type SYSTEM, nội dung, kênh và audience |
| GET | /cms/notification-announcements/{id} | Chi tiết draft/snapshot và version |
| PATCH | /cms/notification-announcements/{id} | Chỉ sửa DRAFT; version bắt buộc |
| DELETE | /cms/notification-announcements/{id} | Chỉ xóa mềm DRAFT |
| POST | /cms/notification-announcements/{id}/preview | Ước lượng audience hợp lệ/bị loại; không gửi |
| POST | /cms/notification-announcements/{id}/publish | 202 publishRunId; snapshot audience/nội dung, enqueue fan-out |
| GET | /cms/notification-announcements/{id}/runs/{runId} | Tiến độ và số tenant/recipient thành công/lỗi/bị bỏ qua |

Audience bắt buộc: mode ALL_ACTIVE_TENANTS hoặc SELECTED_TENANTS + tenantIds,
kèm recipientPolicy đã hỗ trợ. Không nhận arbitrary SQL/filter expression.
Preview là ước lượng; publish recheck và snapshot tenant audience.
Publish tuân thủ tenant feature/type/channel/preference; không có bypass mandatory mặc định.
Danh sách chi tiết recipient phải kiểm soát quyền/PII; progress mặc định dùng số lượng.
Schema announcement/publish job chưa có trong V5 và phải được bổ sung trước nhóm API này.

## 8. Realtime và internal contract

- WebSocket `/ws`, event `notification.created`, payload duy nhất
  `{ notificationId, tenantId }`; xác thực và origin/session checks.
- Không có client API join room của user khác. Chỉ server chọn room theo identity đã xác thực.
- API inbox/count dùng sau reconnect, đổi tenant và nhận signal; WS không thay API quyền đọc.
- Consumer Kafka nhận integration events/commands theo contract versioned.
- Không mở public `POST /notifications/send` cho tenant tự điền recipients/payload.
  Nếu sau này cần internal send-command API thì service-auth riêng, allowlist type/template,
  idempotency và authorization contract; không chỉ dựa Gateway user context.
- Health/readiness/metrics dùng Actuator theo chuẩn service, không coi là API nghiệp vụ.
