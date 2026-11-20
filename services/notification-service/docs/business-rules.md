# Nghiệp vụ dịch vụ thông báo ViShop

Dịch vụ Notification tiếp nhận sự kiện nghiệp vụ, xác định người nhận trong từng tenant, lưu thông báo vào hộp thư và lập lượt gửi qua các kênh ngoài. **Quyền nghiệp vụ được xét khi phân phối. Người dùng còn truy cập được tenant thì vẫn xem được thông báo đã nhận trước khi mất quyền.**

## Phạm vi và trách nhiệm

| Bên tham gia | Trách nhiệm |
|---|---|
| Dịch vụ nguồn | Phát hiện điều kiện nghiệp vụ, chẳng hạn tồn kho xuống dưới ngưỡng. Cung cấp mã sự kiện, tenant, đối tượng liên quan và dữ liệu để tạo nội dung. |
| IAM và Gateway | Xác thực phiên, xác định người dùng, tenant đang truy cập và quyền trong tenant. |
| Notification | Áp dụng quy tắc của tenant, chọn người và kênh đủ điều kiện, lưu nội dung, trạng thái hộp thư và lượt gửi. |
| Người quản lý cấu hình | Xem cấu hình với quyền `notifications.settings.view`. Tạo hoặc sửa quy tắc, kết nối và loại thông báo của nhóm với quyền `notifications.settings.manage`. |
| Người nhận | Xem, đánh dấu đã đọc hoặc ẩn thông báo của chính mình. Khi mở đường dẫn từ thông báo, dịch vụ đích kiểm tra quyền hiện tại đối với dữ liệu nghiệp vụ. |

## Danh mục thông báo

Mỗi thông báo có một **loại** để cấu hình cách gửi, một **nhóm nghiệp vụ** để hiển thị và lọc, và một **phân loại** `SYSTEM` hoặc `BUSINESS`. Nhóm nghiệp vụ “Kho hàng” khác với nhóm chat Lark “Kho miền Nam”. Nhiều nhóm chat có thể đăng ký cùng một loại.

| Nhóm nghiệp vụ | Loại thông báo được hỗ trợ |
|---|---|
| Kho hàng | Tồn kho thấp (`STOCK_LOW`), đẩy tồn thất bại (`STOCK_PUSH_FAILED`) |
| Sản phẩm | SKU chưa ghép nối (`SKU_UNMAPPED`) |
| Đơn hàng | Kiện hàng thiếu tồn (`PACKAGE_STOCK_SHORTAGE`), vận chuyển bất thường (`SHIPMENT_EXCEPTION`) |
| Tài chính và đối soát | Đơn hàng lỗ (`ORDER_LOSS`), lệch đối soát (`RECONCILIATION_MISMATCH`) |
| Kết nối gian hàng | Ủy quyền gian hàng sắp hết hạn (`AUTHORIZATION_EXPIRING`) |
| Đồng bộ dữ liệu | Đồng bộ lỗi (`SYNC_FAILED`) |
| Tài khoản và thành viên | Lời mời thành viên (`MEMBER_INVITED`) |
| Gói cước | Kích hoạt gói (`SUBSCRIPTION_ACTIVATED`), gói sắp hết hạn (`SUBSCRIPTION_EXPIRING`) |
| Hệ thống | Thông báo chung (`SYSTEM_ANNOUNCEMENT`), bảo trì (`SYSTEM_MAINTENANCE`), sự cố (`SYSTEM_INCIDENT`) |

Danh mục nằm trong mã nguồn. Loại cũ ngoài danh mục vẫn có thể xuất hiện trong lịch sử dưới nhóm `LEGACY`. Luồng tạo mới chỉ chấp nhận loại được hỗ trợ. Thông báo hệ thống cũng có phạm vi tenant và người nhận cụ thể.

## Từ sự kiện đến người nhận

1. Dịch vụ nguồn phát sự kiện có định danh ổn định. Ví dụ, dịch vụ kho xác định SKU đã xuống dưới ngưỡng và phát sự kiện `STOCK_LOW`.
2. Notification xác định loại và tra quy tắc theo cặp `tenant_id + type`. Mỗi tenant có tối đa một quy tắc cho mỗi loại. Khi chưa lưu quy tắc riêng, mặc định bật loại và chọn `IN_APP`. Khi tắt quy tắc hoặc không chọn kênh nào, sự kiện không tạo lượt phân phối mới.
3. Hệ thống lấy giao giữa các kênh được quy tắc cho phép và các kênh đã bật, khả dụng, đủ điều kiện kết nối. Với Telegram và Lark, hệ thống kiểm tra tiếp từng cấu hình nhóm.
4. Bộ xác định người nhận chọn ứng viên phù hợp với sự kiện, kiểm tra tư cách thành viên trong tenant và quyền cần có tại thời điểm phân phối. Có quyền là điều kiện cần. Một người vẫn phải thuộc đối tượng nhận của sự kiện. Nếu không xác định được người đủ điều kiện thì bỏ qua lần phân phối.
5. Nếu có người nhận hoặc đích gửi hợp lệ, hệ thống lưu nội dung chung. Với `IN_APP`, mỗi người nhận có một dòng hộp thư. Với kênh ngoài, mỗi đích hợp lệ có một lượt gửi. Sau khi giao dịch tạo hộp thư thành công, hệ thống mới phát tín hiệu cập nhật tới người nhận.

### Khi quyền thay đổi

| Thời điểm | Quyền của Bình | Kết quả với cảnh báo kho |
|---|---|---|
| 09:00, phát cảnh báo A | Có `inventory.view` và thuộc đối tượng nhận | A vào hộp thư của Bình. |
| 10:00, thu hồi quyền | Mất `inventory.view`, vẫn là thành viên tenant | Bình vẫn xem, đánh dấu đọc hoặc ẩn A. |
| 11:00, phát cảnh báo B | Vẫn thiếu `inventory.view` | Bình không nhận B, số chưa đọc không tăng, không có tín hiệu SSE về B cho Bình. |
| 12:00, cấp lại quyền | Có lại `inventory.view` | Bình có thể nhận cảnh báo mới nếu đủ điều kiện. B không tự động được gửi bù. |

Trong mã nguồn hiện tại, quyền cần xét được truyền bằng `requiredPermission` và lưu ở `notifications.required_permission`. Quy tắc `notification_alert_rules` lưu trạng thái bật và kênh theo loại. Hộp thư dựa vào dòng `notification_recipients` của đúng tenant và user. Tắt quy tắc, đổi kênh hoặc mất quyền nghiệp vụ sau đó không xóa lịch sử đã nhận. Người đã mất quyền truy cập tenant không thể dùng hộp thư để truy cập tenant đó.

## Kênh gửi và nhóm Telegram, Lark

| Kênh | Đơn vị cấu hình | Điều kiện riêng |
|---|---|---|
| In-app | Một cấu hình mỗi tenant | Mặc định bật. Mỗi người nhận có trạng thái đọc và ẩn riêng. |
| Telegram | Nhiều nhóm mỗi tenant | Mỗi nhóm có ID, cấu hình kết nối, trạng thái bật và danh sách loại được nhận (`allowedTypes`). |
| Lark | Nhiều nhóm mỗi tenant | Mỗi nhóm có ID, cấu hình kết nối, trạng thái bật và danh sách loại được nhận (`allowedTypes`). |

**Một nhóm Telegram hoặc Lark chỉ được chọn khi tất cả điều kiện sau cùng đúng.**

- Quy tắc của tenant đang bật và cho phép đúng kênh `TELEGRAM` hoặc `LARK` đối với loại phát sinh.
- Cấu hình của chính nhóm đang bật, kết nối đã xác minh và `allowedTypes` chứa đúng loại. Nhóm mới và nhóm nâng cấp (Lark trước V23, Telegram trước V24) mặc định có danh sách trống. Khi sửa, bỏ qua trường này để giữ lựa chọn hiện tại hoặc gửi `[]` để bỏ hết.
- Bộ xác minh phải đối chiếu được toàn bộ thành viên của đúng nhóm Telegram hoặc Lark. Mọi thành viên phải đủ điều kiện nhận loại thông báo qua kênh đó. Nếu có người thiếu quyền hoặc chưa xác định được danh tính, hệ thống bỏ qua nhóm. Các đích khác vẫn được xét độc lập.

Ví dụ, nhóm **Kho miền Nam** chọn `STOCK_LOW` và `STOCK_PUSH_FAILED`, nhóm **Vận hành đơn** chọn `SHIPMENT_EXCEPTION`. Khi tồn kho thấp, chỉ nhóm Kho miền Nam được xét. Nếu nhóm này có thành viên thiếu quyền xem kho thì bỏ qua lượt gửi vào nhóm. Nhân viên kho đủ điều kiện vẫn nhận in-app nếu quy tắc cho phép.

Khi thêm lượt gửi Telegram hoặc Lark vào hàng đợi và khi chuyển lượt gửi sang xử lý, database kiểm tra lại quy tắc, trạng thái nhóm và loại được phép. Worker phải xác minh lại thành viên và quyền trước khi gửi thực tế. Việc thu hồi quyền trong ViShop không thu hồi được tin nhắn đã chuyển tới một nhóm chat ngoài hệ thống.

## Hộp thư, gửi lại và lưu giữ

- **Hộp thư cá nhân.** Người dùng xem danh sách, chi tiết và số chưa đọc trong tenant đang truy cập. Có thể lọc theo nhóm, loại, mức độ, trạng thái đọc và shop. Mở chi tiết không tự đánh dấu đã đọc. Đánh dấu đọc lặp lại giữ mốc đọc đầu tiên. “Đọc tất cả” chỉ áp dụng đến thời điểm đã chọn.
- **Ẩn và hết hạn.** Ẩn chỉ tác động đến hộp thư của người thao tác. Khi bật tác vụ dọn dữ liệu, dòng người nhận đủ điều kiện dọn sau thời hạn mặc định 90 ngày tính từ ngày tạo, kể cả mục chưa đọc hoặc đã ẩn. Việc dọn dòng người nhận giữ lại nội dung chung và lịch sử gửi ngoài.
- **Cập nhật thời gian thực.** SSE báo cho các kết nối của đúng tenant và người nhận sau khi ghi thành công. Tín hiệu chứa định danh để giao diện tải nội dung qua API. Khi kết nối lại, giao diện tải lại hộp thư và số chưa đọc để đồng bộ.
- **Chống trùng.** Phát lại cùng sự kiện không được tạo thêm dòng hộp thư hay lượt gửi trùng đích. Hai sự kiện khác ID trên cùng đối tượng vẫn được xử lý độc lập. Service nguồn chịu trách nhiệm về ngưỡng phát cảnh báo.
- **Hàng đợi ngoài hệ thống.** Lượt gửi có đích, trạng thái, số lần thử và thời điểm thử tiếp. Worker nhận lượt đang chờ, ghi thành công, lên lịch thử lại hoặc ghi thất bại theo giới hạn. Lượt gửi vào hàng đợi chưa có nghĩa nhà cung cấp đã chuyển tin.
- **Ghi cấu hình đồng thời.** Mỗi lần sửa nhóm hoặc quy tắc phải gửi phiên bản hiện tại. Khi một quản trị viên đã lưu trước, yêu cầu dùng phiên bản cũ trả lỗi xung đột để người còn lại tải lại cấu hình.

## Dữ liệu và mức độ triển khai

| Dữ liệu | Vai trò nghiệp vụ |
|---|---|
| `notifications` | Nội dung chung, loại, tenant, đối tượng nguồn và quyền dùng để chọn người nhận. |
| `notification_recipients` | Ghi nhận ai đã nhận in-app, thời điểm đọc và trạng thái ẩn. |
| `notification_alert_rules` | Quy tắc bật/tắt và tập kênh cho từng loại trong tenant. |
| `notification_settings` | Cấu hình kênh hoặc nhóm. Danh sách loại được phép áp dụng cho từng nhóm Lark từ V23 và Telegram từ V24. |
| `notification_setting_types` | Từ V25 mỗi dòng liên kết một nhóm với một rule của cùng tenant. Nhóm chưa đăng ký loại nào có 0 dòng. |
| `notification_deliveries` | Lượt gửi ngoài hệ thống, cấu hình đích, trạng thái xử lý và lịch sử thử gửi. Số lần thử và trạng thái gần nhất nằm trên từng lượt gửi. |
| `consumed_messages`, `outbox_events` | Hỗ trợ chống xử lý lặp và ghi sự kiện cần phát cùng giao dịch nghiệp vụ. |

**Đã có trong mã nguồn:** API cấu hình kênh và quy tắc, hộp thư cá nhân, lọc nhóm, SSE và Redis fan-out, lưu lượt gửi, chính sách chọn nhóm Lark và Telegram, điều kiện kiểm tra của V23–V24 và tác vụ dọn dòng người nhận. Module announcement đã có nghiệp vụ tạo, sửa và phát hành ở tầng ứng dụng.

**Còn cần tích hợp để chạy trọn luồng:** consumer nhận sự kiện nguồn, adapter IAM xác định người nhận theo quyền thực tế, giao dịch xử lý sự kiện chống lặp, xác minh kết nối và thành viên nhóm, worker gửi Email/Telegram/Lark. Phát thông báo toàn hệ thống còn cần lưu announcement, API quản trị và bước phân phối tới từng tenant. Các thay đổi V23–V24 và quyền xem lịch sử mô tả ở đây là thiết kế trong mã nguồn, chưa được xác nhận triển khai trên môi trường dev.

Chi tiết triển khai và hợp đồng API nằm trong [cấu hình kênh và quy tắc](setting.md), [hộp thư và SSE](inbox.md), [lưu giữ dữ liệu](retention.md) và [thiết kế database](database-design.md).
