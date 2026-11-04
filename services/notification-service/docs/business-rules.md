# Nghiệp vụ Notification

Trạng thái: schema/migration, 4 domain/application module và [shared constants](constants.md)
đã có. Không có module/API catalog. HTTP/persistence adapters, consumer, realtime và sender
chưa triển khai. Xem
[thiết kế module](module-design.md) cho quy tắc đã có code và adapter còn thiếu.

- Phân loại thông báo `SYSTEM` và `BUSINESS`, gom theo feature.
- Tenant chọn nhóm tính năng, loại thông báo và kênh nhận.
- `notification_settings` lưu cấu hình kết nối kênh theo tenant, gồm bot Telegram,
  chat nhận và tham chiếu token; không chứa tùy chọn cá nhân.
- `notification_user_preferences` cho người dùng từ chối nhận từng loại/kênh;
  không bật vượt policy tenant hoặc cấp thêm quyền truy cập.
- Người nhận do danh sách tenant cấu hình hoặc nghiệp vụ xác định; không tự gửi toàn bộ user.
- Chỉ người có quyền xem dữ liệu liên quan được nhận/xem nội dung.
- Mỗi người nhận in-app có trạng thái đọc độc lập; thông báo hệ thống vẫn tách tenant.
- Cùng loại/đối tượng/người nhận/kênh không gửi lại trong cooldown tối thiểu 24 giờ,
  trừ khi mức độ tăng `INFO < WARNING < CRITICAL`.
- Telegram gửi tới chat/group cấu hình; người có mặt trong nhóm có thể đọc tin.
  Kiểm tra quyền recipient in-app không giới hạn được thành viên của nhóm Telegram.
- Ngưỡng nghiệp vụ do service nguồn đánh giá, Notification quản lý cách gửi và người nhận.

Thứ tự áp dụng: mã type được constants hỗ trợ → tenant feature → tenant channel → tenant type rule →
personal preference → người nhận/quyền → cooldown.
Kênh thiếu cấu hình mặc định in-app bật, email/Telegram tắt. Không có dòng personal
preference nghĩa là theo policy tenant. Lịch sử đã nhận không bị xóa khi tắt kênh.

Yêu cầu gốc: [M14](../../../docs/product/vishop-function-list.md).
