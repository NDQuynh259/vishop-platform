# Module Notification-service

## Trạng thái

Service có 3 module theo nghiệp vụ: `notification`, `setting`, `announcement`,
cùng repository ports, composition và CLI Flyway. Không còn module catalog hoặc API catalog.
Nhóm/loại/defaults nằm trong [shared constants](constants.md).

Thư mục source Java khớp với package. `notification` sở hữu nội dung chung,
recipient in-app và external delivery. Inbox và delivery là các trách nhiệm
trong cùng module, không còn là hai module ngang hàng.
Tên class/interface dùng tiền tố `Notification`; service trong `application/service`
không cần lặp thêm `Application` trong tên class.

Module `setting` có HTTP/JPA cho channel connection và alert rule; xem [setting](setting.md).
Module `notification` có HTTP/JPA cho nội dung và recipient in-app. External delivery có
`NotificationDeliveryJpaEntity`, mapper, Spring Data repository và adapter. Provider sender còn cần tích hợp.
Cooldown đã bỏ khỏi runtime. V18 xóa archive cooldown và bảng attempt history không dùng.
Chưa có main Spring runtime, Kafka, realtime, secrets adapter hay provider sender
trong checkout hiện tại. Schema V19 có 7 bảng nghiệp vụ; Lark hỗ trợ nhiều cấu hình nhóm theo ID.
Lỗi kỹ thuật và từng lần gửi dùng logging.

```text
com/vishop/notification/
├── modules/
│   ├── setting/
│   ├── notification/
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

JPA adapter nằm trong `infrastructure/persistence/adapters` của chính module đó,
cùng cấp với `entities`, `mapper` và `repositories`:

```text
modules/notification/infrastructure/persistence/adapters/
  NotificationJpaAdapter.java
  NotificationDeliveryJpaAdapter.java
modules/setting/infrastructure/persistence/adapters/
  NotificationSettingJpaAdapter.java
  NotificationAlertRuleJpaAdapter.java
```

## Ownership

| Module | API nội bộ | Trách nhiệm / bảng |
| --- | --- | --- |
| setting | NotificationSettingManagementApi, NotificationAlertRuleApi | Kết nối kênh và rule loại thông báo; notification_settings, notification_alert_rules |
| notification | NotificationApi, NotificationDeliveryApi | Nội dung, recipient in-app, đọc/ẩn và quyền truy cập; external delivery |
| announcement | NotificationAnnouncementApi | Draft/publish snapshot có version; persistence announcement cần migration tiếp theo |

Tên thành phần chính:

| Thành phần | Trách nhiệm |
| --- | --- |
| NotificationService / NotificationApi / NotificationRepository | Tạo nội dung và quản lý inbox của user |
| NotificationDeliveryService / NotificationDeliveryApi / NotificationDeliveryRepository | External delivery qua JPA adapter |
| NotificationDelivery | Trạng thái và lease của một delivery |
| NotificationInboxController | HTTP API inbox tại `/api/v1/notifications` |
| NotificationModuleConfiguration | Cấu hình Spring cho các adapter notification hiện có |
| NotificationAnnouncementService | Vòng đời draft, revision và publication của bản tin |

Entity nghiệp vụ có lưu DB phải đi kèm JPA entity, mapper và repository adapter thực tế.
JPA annotations thuộc infrastructure; domain giữ thuần Java. DTO/filter/scope không phải bảng.
Announcement chưa có bảng trong schema hiện tại; persistence của nó cần migration riêng.

Delivery ánh xạ đầy đủ bảng `notification_deliveries`, gồm template legacy, generated
destination_key, audit, lease và `@Version`. Cột last_error cũ chỉ được đọc để giữ history;
adapter không ghi thêm chi tiết lỗi kỹ thuật. `NotificationModuleConfiguration` đăng ký
repository, mapper, delivery service và CQRS enqueue handler.

Constants dùng chung không phải module và không sở hữu bảng. API module trả DTO bất biến,
không trả domain/JPA entity. Composition nối API/port; module không dùng repository/entity
của module khác. Ngoại lệ hẹp: JPA association giữa `NotificationDeliveryJpaEntity` và
`NotificationChannelSettingJpaEntity` có hai chiều ManyToOne/OneToMany. Hai module configuration
đăng ký entity scan cho các mapping này. Architecture test loại riêng các cạnh metadata JPA
khỏi kiểm tra cycle; service vẫn dùng application API và bị kiểm tra cycle nghiệp vụ.
Shared không phụ thuộc ngược module, composition hoặc framework.

## Composition và quy tắc đã có code

NotificationDispatchCoordinator tra NotificationTypeCode trực tiếp → tenant policy →
người nhận hợp lệ → inbox/delivery → realtime outbox.

NotificationSettingDispatchPolicy đã triển khai tại composition, kết hợp rule theo type
với kết nối kênh enabled/available/verified. Giai đoạn này không áp dụng tenant feature,
explicit recipients hoặc personal preferences. Các bảng còn lại giữ schema và dữ liệu.

- DispatchJournal.once cần adapter transaction thật cho consumed marker, inbox,
  queue và outbox; hiện chưa có implementation PostgreSQL.
- RecipientDirectory kiểm tra membership/quyền trong tenant, resolver theo type và đích đã xác minh.
  Explicit selection vẫn giới hạn kết quả nếu directory trả dư người.
- Dispatch dùng trực tiếp các route đã qua kiểm tra quyền/cấu hình, không reserve cooldown.
- Delivery gộp EMAIL destination sau trim/lowercase; claim/complete/fail theo lease/token,
  due time và attempt budget. Không tự reclaim kết quả provider chưa xác định.
- Recipient read idempotent, giữ readAt đầu tiên; archive không sửa notification chung.
- Inbox HTTP/JPA đã có; [API và phạm vi đọc](inbox.md) kiểm tra tenant, recipient và
  `required_permission` (mọi shop thuộc tenant đang đăng nhập đều truy cập được).
- NotificationSystemTypePolicy dùng constants thật, chỉ cho announcement type SYSTEM.
- NotificationAnnouncement draft có audience tường minh; published snapshot bất biến. Repository save
  phải compare-and-swap expectedVersion; publish chưa fan-out hoặc gửi provider.
- Telegram group vẫn cần audience policy riêng và gom target theo chat; adapter chưa có.

## Kiểm thử

NotificationArchitectureTest kiểm tra 3 module, chặn package inbox/delivery cũ, cross-module internals/cycle, cấm
framework trong domain, cấm module catalog quay lại và chặn phụ thuộc ngược từ shared.
Unit tests kiểm tra constants thật, unknown type, policy, read/archive, lease/retry,
dedup destination, announcement và dispatch replay. Test journal không thay transaction
adapter production. Schema/CLI tests chạy PostgreSQL độc lập.

```powershell
.\gradlew.bat :services:notification-service:check
```
