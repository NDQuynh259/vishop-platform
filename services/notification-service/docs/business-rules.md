# Nghiệp vụ Notification

Trạng thái: schema/migration, 3 module nghiệp vụ và [shared constants](constants.md)
đã có. Không có module/API catalog. Module [notification-setting](setting.md) có HTTP/JPA
cho notification_settings và notification_alert_rules. V14 xóa các bảng feature subscription,
recipient selection và personal preference cũ không được runtime sử dụng.
consumer, realtime, sender và adapters của các module còn lại chưa triển khai. Xem
[thiết kế module](module-design.md) cho quy tắc đã có code và adapter còn thiếu.

- Phân loại thông báo `SYSTEM` và `BUSINESS`, gom theo feature.
- Tenant chọn loại thông báo và kênh nhận; feature dùng để phân nhóm trong code catalog.
- `notification_settings` lưu cấu hình kết nối kênh theo tenant, gồm bot Telegram,
  chat nhận và tham chiếu token; không chứa tùy chọn cá nhân.
- Người nhận do RecipientDirectory xác định từ nghiệp vụ/quyền; setting chưa có danh sách user nhận.
- Chỉ người có quyền xem dữ liệu liên quan được nhận/xem nội dung.
- Mỗi người nhận in-app có trạng thái đọc độc lập; thông báo hệ thống vẫn tách tenant.
- Chưa áp dụng cooldown. Hai sự kiện nguồn khác ID vẫn được gửi khi cùng loại/đối tượng/người nhận/kênh.
- Giữ chống xử lý lại cùng sự kiện và chống trùng đích nhận trong cùng một thông báo.
- Telegram gửi tới chat/group cấu hình; người có mặt trong nhóm có thể đọc tin.
  Kiểm tra quyền recipient in-app không giới hạn được thành viên của nhóm Telegram.
- Ngưỡng nghiệp vụ do service nguồn đánh giá, Notification quản lý cách gửi và người nhận.

Thứ tự áp dụng: mã type được constants hỗ trợ → tenant type rule → kênh đã bật,
khả dụng và kết nối hợp lệ → người nhận/quyền → tạo inbox/delivery.
Kênh thiếu cấu hình mặc định in-app bật, email/Telegram/Lark tắt. Provider policy mặc định
chỉ hỗ trợ in-app; cần adapter provider để bật các kênh ngoài. Lịch sử đã nhận không bị
xóa khi tắt kênh.

Yêu cầu gốc: [M14](../../../docs/product/vishop-function-list.md).
