# Constants thông báo

Danh mục được quản lý trực tiếp tại
`src/main/java/com/vishop/notification/shared/constants/`:

| Enum | Nội dung |
| --- | --- |
| NotificationFeatureCode | Mã nhóm tính năng và tên hiển thị |
| NotificationTypeCode | Mã loại, nhóm sở hữu, SYSTEM/BUSINESS, tên và kênh mặc định |

Không còn module catalog, registry, CatalogApi, CatalogReader, Spring bean hoặc REST
controller cho danh mục. Không có UUID/version metadata của catalog và không có API
để FE tải danh mục. Các mã là enum name, ví dụ INVENTORY và STOCK_LOW.

## Sử dụng

```java
var type = NotificationTypeCode.fromCode("STOCK_LOW")
    .orElseThrow(() -> new IllegalArgumentException("Unknown notification type"));

var featureCode = type.feature().code(); // INVENTORY
var channels = type.defaultChannels();  // IN_APP
```

fromCode trả Optional.empty cho mã không hỗ trợ, null hoặc sai chữ hoa/thường; không
tự biến mã legacy trong DB thành loại hợp lệ. Defaults là dữ liệu bất biến trong code.

NotificationDispatchCoordinator kiểm tra type trực tiếp qua constants, rồi áp policy tenant,
người nhận, personal preference và gửi. Cooldown chưa áp dụng. NotificationSystemTypePolicy chỉ cho
announcement sử dụng type SYSTEM đã được định nghĩa.

Policy feature/type/recipient/preference hiện chỉ còn cổng NotificationDispatchPolicy
ở composition, chưa có production implementation. notification-setting chỉ quản lý kênh.

Constants không cấp quyền, không xác nhận tenant entitlement, provider readiness hoặc
source event đã tích hợp. Các kiểm tra đó vẫn thuộc runtime/setting/dispatch.

## FE và DB

FE tự quản lý danh mục lựa chọn; mã gửi lên phải khớp enum backend. Thêm/đổi loại cần cập
nhật FE và backend cùng đợt phát hành, cùng event mapping/template/recipient resolver.
Không tái sử dụng một mã cũ cho ý nghĩa nghiệp vụ mới.

DB chỉ giữ cấu hình tenant/người dùng, người nhận, kênh/bot và lịch sử theo business code.
V7 đã định nghĩa việc bỏ hai bảng catalog; không cần migration thêm catalog khi thêm enum.
V1–V7 vẫn được giữ nguyên để bảo toàn lịch sử migration.

Mã legacy còn trong history/config vẫn được giữ, nhưng dispatch từ chối nếu không có
trong NotificationTypeCode. API ghi cấu hình khi triển khai phải validate code bằng
NotificationTypeCode.fromCode / NotificationFeatureCode.fromCode trước khi ghi.

## Trạng thái và kiểm thử

Service có 3 module: `setting`, `notification`, `announcement`. Constants là Java
thuần dùng chung, không phụ thuộc các module/framework. Chưa có main runtime, HTTP API
nghiệp vụ, Kafka hoặc sender; migration CLI và các use case nền vẫn hoạt động qua ports.

NotificationConstantsTest kiểm tra mapping, category, defaults bất biến và unknown codes.
Dispatch/announcement unit tests dùng constants thật, không mock CatalogApi.
Architecture test cấm module catalog quay lại và cấm shared phụ thuộc ngược vào module.
Schema/CLI tests tiếp tục kiểm tra PostgreSQL và nâng cấp V1–V6 tới V7.
