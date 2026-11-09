# Database Notification

Schema V16 giữ **9 bảng**, nâng cấp bằng [migration V16](../src/main/resources/db/migration/V16__redesign_notification_routing_and_delivery.sql).
Database riêng: `vishop_notification`. V1–V15 giữ nguyên checksum; V16 không tạo lại DB hoặc xóa bản ghi hiện có.

## Trách nhiệm từng bảng

| Bảng | Trách nhiệm |
| --- | --- |
| `notifications` | Nội dung chung, sự kiện nguồn, đối tượng nghiệp vụ, quyền và phạm vi shop |
| `notification_recipients` | Inbox in-app của từng user; trạng thái đọc/ẩn riêng |
| `notification_settings` | Kết nối provider theo tenant/kênh, tên, bật/tắt và connection_config |
| `notification_alert_rules` | Bật/tắt loại thông báo và tập kênh channels |
| `notification_deliveries` | Hàng đợi EMAIL/TELEGRAM/LARK, đích gửi, retry và lease |
| `notification_delivery_attempts` | Lịch sử mỗi lần gửi, gồm kết quả không xác định |
| `notification_deduplication` | Cooldown theo user hoặc đích gửi chung |
| `consumed_messages` | Chống xử lý lặp sự kiện |
| `outbox_events` | Phát sự kiện sau khi transaction thành công |

Danh mục feature/type nằm trong code. Không khôi phục các bảng catalog, personal preferences hoặc danh sách user theo rule đã được loại bỏ trước đây.

```mermaid
erDiagram
    notifications ||--o{ notification_recipients : tenant_notification
    notifications ||--o{ notification_deliveries : tenant_notification
    notification_deliveries ||--o{ notification_delivery_attempts : tenant_delivery
    notification_settings o|--o{ notification_deliveries : tenant_setting_channel
    notification_settings o|--o{ notification_deduplication : tenant_setting_channel

    notifications {
        uuid id PK
        uuid tenant_id
        varchar source_event_id
        varchar type
        varchar subject_type
        varchar subject_id
        uuid shop_id
        varchar required_permission
        varchar title
        text body
        jsonb metadata
    }
    notification_recipients {
        uuid id PK
        uuid tenant_id FK
        uuid notification_id FK
        uuid user_id "NOT NULL"
        timestamptz read_at "NULL means unread"
        timestamptz deleted_at
    }
    notification_settings {
        uuid id PK
        uuid tenant_id
        varchar channel
        varchar name
        boolean enabled
        jsonb connection_config
    }
    notification_alert_rules {
        uuid id PK
        uuid tenant_id
        varchar type
        boolean enabled
        text_array channels
        jsonb thresholds "legacy data retained"
        int cooldown_seconds "legacy data retained"
    }
    notification_deliveries {
        uuid id PK
        uuid tenant_id FK
        uuid notification_id FK
        uuid user_id "nullable"
        uuid setting_id FK "nullable"
        varchar channel "EMAIL TELEGRAM LARK"
        varchar destination
        text destination_key "generated SHA256"
        varchar status
        int attempt_count
        timestamptz next_attempt_at
        uuid lock_token
        timestamptz locked_until
        timestamptz sent_at
    }
    notification_delivery_attempts {
        uuid id PK
        uuid tenant_id FK
        uuid delivery_id FK
        int attempt_number
        varchar status
        text error_message
    }
    notification_deduplication {
        uuid id PK
        uuid tenant_id
        varchar type
        varchar subject_type
        varchar subject_id
        uuid user_id "nullable for external channels"
        uuid setting_id FK "nullable"
        varchar channel
        varchar destination "nullable for user routes"
        text recipient_key "generated"
        timestamptz last_notified_at
        varchar last_severity
    }
    consumed_messages {
        varchar message_id PK
        varchar consumer_group PK
        varchar event_type
        timestamptz processed_at
    }
    outbox_events {
        uuid id PK
        uuid tenant_id
        varchar event_type
        uuid aggregate_id
        jsonb payload
        varchar status
    }
```

## Người nhận và đích gửi

In-app luôn có một dòng `notification_recipients` cho mỗi user nhận thông báo.
Khóa chính là `id` UUIDv7; unique `(tenant_id, notification_id, user_id)` ngăn tạo trùng inbox.
API lấy danh sách bằng tenant/user từ principal, join nội dung theo tenant/notification và đọc `read_at` của recipient đó. Đánh dấu đọc chỉ sửa dòng recipient.

Kênh ngoài dùng `notification_deliveries`. `user_id` có thể có khi gửi cho một người, hoặc NULL khi gửi nhóm Telegram, webhook Lark hay email ngoài hệ thống.
`destination` bắt buộc, tối đa 2048 ký tự, là snapshot đích gửi lúc tạo job.
Queue không nhận IN_APP; trạng thái đã đọc không thuộc queue.

`setting_id` tùy chọn để tương thích job cũ và gửi giao dịch không gắn cấu hình tenant.
Khi có ID, FK `(tenant_id, setting_id, channel)` bảo đảm đúng tenant và đúng kênh.
Tắt/xóa mềm setting không xóa lịch sử job. Không dùng cascade delete.

Không có FK tới user/tenant/shop của service khác. Quyền xem dữ liệu và audience của nhóm ngoài hệ thống vẫn do RecipientDirectory/adapter tin cậy kiểm tra.

## Cấu hình và kênh

Mỗi tenant hiện có một setting cho mỗi kênh, unique `(tenant_id, channel)`.
connection_config chứa cấu hình và tham chiếu secret; API không trả secret.
Thiết kế hiện tại chưa cung cấp nhiều kết nối cho cùng kênh.

Rule unique `(tenant_id, type)`. V16 chuyển bốn boolean thành `channels TEXT[]`,
ví dụ `['IN_APP','TELEGRAM']`; tập rỗng hợp lệ.
DB chặn mã lạ, NULL, phần tử trùng và mảng nhiều chiều. Entity/JPA adapter dùng trường mới; API vẫn trả tập kênh.

thresholds và cooldown_seconds cũ được giữ để không mất dữ liệu. API setting hiện chưa sử dụng hai trường này; không tự coi chúng là policy dispatch đã có hiệu lực.

## Chống trùng và lịch sử

notification_deduplication có id UUIDv7 riêng. Unique nghiệp vụ gồm tenant, type, subject, channel và recipient_key do DB sinh:

- Route user: `USER:<userId>`. In-app bắt buộc có user và không có destination.
- Route có destination: `DESTINATION:<SHA256(destination chuẩn hóa)>`, kể cả khi kèm userId. Nhiều user trỏ cùng nhóm dùng cùng khóa cooldown.
- EMAIL cũ chưa có destination giữ khóa user; không suy đoán địa chỉ để backfill.

Khi triển khai adapter cooldown mới, route EMAIL có cả user và destination phải kiểm
tra thêm khóa USER cũ trong giai đoạn chuyển tiếp. Migration giữ trạng thái cũ nhưng
không thể suy ra chắc chắn email đã nhận để tự đổi các khóa này sang destination.

Destination được trim; riêng EMAIL chuyển về chữ thường. Telegram/Lark giữ phân biệt hoa thường.
Quy tắc này cũng dùng cho unique delivery `(tenant_id, notification_id, channel, destination_key)`.
Hash SHA256 giữ kích thước index ổn định với webhook URL dài.
Hàm notification_destination_key dùng chuyển đổi UTF-8 cố định, không cần extension.

Cooldown phải được lock/upsert trong transaction. Schema không tự tạo worker, gọi provider hay thực thi policy thời gian.
Queue giữ nguyên attempts, retry, lease, trạng thái và dữ liệu template.
Job mới không chỉ định template dùng GENERIC_NOTIFICATION; template cũ không đổi.

## Index và audit

Giữ index inbox theo tenant/user và unread; bổ sung idx_notifications_feed cho tenant/created_at/id khi nội dung chưa xóa.
Giữ index pending, lease hết hạn, lịch sử delivery và unique tenant/delivery/attempt number.

Các bảng nội dung, inbox và setting giữ created_at, updated_at, deleted_at, version.
Queue giữ audit/version, hủy bằng status. Attempt giữ lịch sử append-only.
Dedup và các bảng sự kiện dùng timestamps chuyên biệt.
ID mới do ứng dụng cấp bằng UuidV7.nextId(), không có runtime UUID default.

## Nâng cấp và giới hạn

V16 backfill channels từ các cờ cũ, backfill ID dedup từ last_notified_at.
Giữ bản ghi xóa mềm, type legacy, metadata, read/archive state, template, job, attempt,
cooldown, consumed-message và outbox. Test nâng cấp V15 so sánh dữ liệu cả 9 bảng trước/sau và kiểm tra migrate lần hai không thay đổi gì.

Triển khai code JPA mới cùng đợt migration: code cũ còn đọc các cột boolean đã thay thế.
Không chạy lẫn phiên bản cũ/mới trong lúc đổi schema. Các thay đổi đã yêu cầu ở V14/V15 giữ nguyên lịch sử migration.

Provider worker, delivery/cooldown persistence adapter và audience resolver vẫn là phần chưa có triển khai runtime; schema hỗ trợ các kênh không đồng nghĩa hệ thống đã gửi được Telegram/Lark.
Xem [README](../README.md) để chạy CLI migrate cho đúng database.
