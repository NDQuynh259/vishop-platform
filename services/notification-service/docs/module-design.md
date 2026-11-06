# Module Notification-service

## Trạng thái

Service có 4 module domain/application, repository ports, composition và CLI Flyway:
notification-setting, notification-inbox, notification-delivery, notification-announcement. Không còn module catalog hoặc API catalog.
Nhóm/loại/defaults nằm trong [shared constants](constants.md).

Thư mục source Java khớp với package: `announcement`, `delivery`,
`inbox`, `setting` (không dùng dấu `-`) để IDE phân giải type đúng.
Tên class/interface dùng tiền tố `Notification`.

Module notification-setting chỉ có HTTP API và JPA cho notification_settings:
1 entity, 1 Spring Data repository, mapper và adapter. Xem [notification-setting](setting.md) cho
API đã triển khai và giới hạn tích hợp. Hiện chưa có main Spring runtime, Kafka,
realtime, secrets adapter hay provider sender. API các module khác vẫn là backlog.
Schema V10 có 12 bảng cấu hình/history; notification_settings dùng UUIDv7 PK và unique tenant/channel.
Hai bảng catalog đã được bỏ bằng migration V7.

```text
com/vishop/notification/
├── modules/
│   ├── setting/
│   ├── inbox/
│   ├── delivery/
│   └── announcement/
├── composition/
│   ├── dispatch/
│   └── publication/
├── shared/
│   ├── constants/
│   │   ├── NotificationFeatureCode.java
│   │   └── NotificationTypeCode.java
│   └── contract/
└── infra/database/
```

Mỗi module có domain/entity, domain/port, application/api, application/service,
infrastructure và presentation. NotificationAnnouncement có thêm NotificationAnnouncementTypePolicy application port.

## Ownership

| Module | API nội bộ | Trách nhiệm / bảng |
| --- | --- | --- |
| notification-setting | NotificationSettingManagementApi | Chỉ cấu hình kênh tenant; notification_settings |
| notification-inbox | NotificationInboxApi | Immutable notification và recipient; notifications, notification_recipients |
| notification-delivery | NotificationDeliveryApi | Cooldown, queue/retry; deliveries, delivery_attempts, deduplication |
| notification-announcement | NotificationAnnouncementApi | Draft/publish snapshot có version; persistence announcement cần migration tiếp theo |

Constants dùng chung không phải module và không sở hữu bảng. API module trả DTO bất biến,
không trả domain/JPA entity. Composition nối API/port; module không dùng repository/entity
của module khác. Shared không phụ thuộc ngược module, composition hoặc framework.

## Composition và quy tắc đã có code

NotificationDispatchCoordinator tra NotificationTypeCode trực tiếp → tenant policy →
người nhận hợp lệ → personal preference → cooldown → inbox/delivery → realtime outbox.

Feature/type/recipient/preference code và API đã bỏ khỏi notification-setting, chờ
triển khai sau. Dispatch phụ thuộc NotificationDispatchPolicy tại composition;
chưa có implementation của policy này, không có mặc định cho phép gửi.

- DispatchJournal.once cần adapter transaction thật cho consumed marker, cooldown, inbox,
  queue và outbox; hiện chưa có implementation PostgreSQL.
- RecipientDirectory kiểm tra membership/quyền/shop, resolver theo type và đích đã xác minh.
  Explicit selection vẫn giới hạn kết quả nếu directory trả dư người.
- Delivery cooldown port phải lock/upsert; application từ chối adapter mở rộng user/channel.
- Delivery gộp EMAIL destination sau trim/lowercase; claim/complete/fail theo lease/token,
  due time và attempt budget. Không tự reclaim kết quả provider chưa xác định.
- Recipient read idempotent, giữ readAt đầu tiên; archive không sửa notification chung.
- NotificationSystemTypePolicy dùng constants thật, chỉ cho announcement type SYSTEM.
- NotificationAnnouncement draft có audience tường minh; published snapshot bất biến. Repository save
  phải compare-and-swap expectedVersion; publish chưa fan-out hoặc gửi provider.
- Telegram group vẫn cần audience policy riêng và gom target theo chat; adapter chưa có.

## Kiểm thử

NotificationArchitectureTest kiểm tra 4 module, chặn cross-module internals/cycle, cấm
framework trong domain, cấm module catalog quay lại và chặn phụ thuộc ngược từ shared.
Unit tests kiểm tra constants thật, unknown type, policy, read/archive, lease/retry,
dedup destination, announcement và dispatch replay. Test journal không thay transaction
adapter production. Schema/CLI tests chạy PostgreSQL độc lập.

```powershell
.\gradlew.bat :services:notification-service:check
```
