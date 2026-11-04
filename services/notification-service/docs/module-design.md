# Module Notification-service

## Trạng thái

Service có 4 module domain/application, repository ports, composition và CLI Flyway:
configuration, inbox, delivery, announcement. Không còn module catalog hoặc API catalog.
Nhóm/loại/defaults nằm trong [shared constants](constants.md).

Hiện chưa có main Spring runtime, persistence/HTTP adapters, Kafka, realtime, secrets
adapter hay provider sender. API nghiệp vụ trong [contract](api-contract.md) là backlog.
Schema V7 giữ 12 bảng cấu hình/history; hai bảng catalog đã được bỏ bằng migration.

```text
com/vishop/notification/
├── modules/
│   ├── configuration/
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
infrastructure và presentation. Announcement có thêm SystemTypePolicy application port.

## Ownership

| Module | API nội bộ | Trách nhiệm / bảng |
| --- | --- | --- |
| configuration | ConfigurationApi | Tenant feature/type/channel và personal restriction; settings, tenant_features, alert_rules/recipients, user_preferences |
| inbox | InboxApi | Immutable notification và recipient; notifications, notification_recipients |
| delivery | DeliveryApi | Cooldown, queue/retry; deliveries, delivery_attempts, deduplication |
| announcement | AnnouncementApi | Draft/publish snapshot có version; persistence announcement cần migration tiếp theo |

Constants dùng chung không phải module và không sở hữu bảng. API module trả DTO bất biến,
không trả domain/JPA entity. Composition nối API/port; module không dùng repository/entity
của module khác. Shared không phụ thuộc ngược module, composition hoặc framework.

## Composition và quy tắc đã có code

NotificationDispatchCoordinator tra NotificationTypeCode trực tiếp → tenant policy →
người nhận hợp lệ → personal preference → cooldown → inbox/delivery → realtime outbox.

- DispatchJournal.once cần adapter transaction thật cho consumed marker, cooldown, inbox,
  queue và outbox; hiện chưa có implementation PostgreSQL.
- RecipientDirectory kiểm tra membership/quyền/shop, resolver theo type và đích đã xác minh.
  Explicit selection vẫn giới hạn kết quả nếu directory trả dư người.
- Delivery cooldown port phải lock/upsert; application từ chối adapter mở rộng user/channel.
- Delivery gộp EMAIL destination sau trim/lowercase; claim/complete/fail theo lease/token,
  due time và attempt budget. Không tự reclaim kết quả provider chưa xác định.
- Recipient read idempotent, giữ readAt đầu tiên; archive không sửa notification chung.
- NotificationSystemTypePolicy dùng constants thật, chỉ cho announcement type SYSTEM.
- Announcement draft có audience tường minh; published snapshot bất biến. Repository save
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
