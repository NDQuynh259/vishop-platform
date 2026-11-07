# Inbox và bảng thông báo

Module inbox ánh xạ hai bảng có sẵn: `notifications` chứa nội dung chung bất biến;
`notification_recipients` chứa người nhận và trạng thái đọc/ẩn độc lập của mỗi user.
Không sửa migration cũ hoặc thêm bảng cho phần này.

## API cá nhân

Base path `/api/v1/notifications`. Các endpoint yêu cầu đăng nhập; tenant, user và
permissions lấy từ principal do gateway/filter tạo, không lấy từ body hoặc query string.

| Method | Endpoint | Kết quả |
| --- | --- | --- |
| GET | `/notifications` | List envelope, cursor, mặc định 20 mục, tối đa 100 |
| GET | `/notifications/unread-count` | `{"count": 3}` |
| GET | `/notifications/{notificationId}` | Nội dung và readAt của chính người nhận |
| PATCH | `/notifications/{notificationId}/read` | Đọc idempotent, trả item và giữ readAt đầu tiên |
| POST | `/notifications/read-all` | Đọc tới cutoff, trả `{"updatedCount": 3}` |
| DELETE | `/notifications/{notificationId}` | 204, ẩn riêng recipient; gọi lại vẫn 204 |

List hỗ trợ `type`, `severity`, `read`, `shopId`, `limit`, `cursor`. Quyền được áp dụng
trong DB trước khi phân trang. Thứ tự `created_at DESC, id DESC`; cursor giữ đủ microsecond
để không bỏ sót thông báo được tạo trong cùng millisecond. GET không tự đánh dấu đã đọc.

Body đọc tất cả: `{"before":"2026-11-07T12:00:00.123456Z"}`. Cutoff không được ở tương lai;
thông báo tạo sau cutoff không bị đánh dấu đọc.

## Phạm vi truy cập

Mọi list/detail/count/read/read-all/archive đều yêu cầu liên kết recipient đúng tenant/user,
notification chưa xóa mềm, user hiện có required_permission và quyền shop tương ứng nếu có shopId.
Các thao tác đọc bỏ recipient đã ẩn. Ngoài phạm vi hoặc không tồn tại trả 404 theo ID;
count/list không để lộ mục bị chặn. Quyền cấu hình không cấp thêm quyền đọc nội dung nghiệp vụ.

Inbox cá nhân dùng `isAuthenticated()` và kiểm tra từng item ở DB, không yêu cầu permission
`notifications.view` chưa có trong catalog IAM hiện tại. `NotificationInboxShopAccess` là port
đọc phạm vi shop từ nguồn tin cậy; bean mặc định trả tập rỗng và chặn mục có shopId đến khi có
adapter thực tế. Không nhận scope/permissions từ client. Test cung cấp scope giả lập để xác nhận
cả nhánh được phép và bị từ chối.

## Tạo nội bộ và transaction

Luồng `CreateNotificationCommand → NotificationInboxApplicationService → NotificationInboxJpaAdapter`
lưu notification và recipient trong cùng transaction. ID mới là UUIDv7. Không có POST tạo nội dung
hoặc PUT sửa payload từ client; adapter sự kiện và RecipientDirectory xác thực nguồn và tìm
người nhận đủ điều kiện trước khi gọi command.

Natural key `(tenant_id, source_event_id, type, subject_type, subject_id)` chống tạo trùng:
replay payload giống hệt trả ID cũ, giữ người nhận/trạng thái đọc; cùng key nhưng payload khác
trả conflict. Replay không thêm người nhận mới vào snapshot đã phát hành. actionUrl chỉ chấp
nhận đường dẫn nội bộ ứng dụng.

Đánh dấu đọc/ẩn dùng cập nhật có điều kiện ở DB, chỉ tăng version recipient khi thay đổi;
không sửa notification chung hoặc recipient khác. Read-all chỉ tác động mục được phép đọc,
chưa đọc và nằm trước cutoff.

`NotificationInboxModuleConfiguration` đăng ký entity, adapter, service và CQRS handler.
Schema có sẵn từ V1/V5. Event consumer production, DispatchJournal/outbox toàn workflow,
runtime HTTP/gateway, realtime và provider sender vẫn cần wiring/adapter tương ứng.
