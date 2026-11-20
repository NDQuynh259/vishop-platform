# Module setting

Quản lý cấu hình kết nối theo tenant trong `notification_settings`. Có bốn kênh:
`IN_APP`, `EMAIL`, `TELEGRAM`, `LARK`.

## API cấu hình kết nối

Base path: `/api/v1`. Tenant lấy từ `GatewayPrincipal`, không lấy từ body/query.

| Method | Endpoint | Quyền |
| --- | --- | --- |
| GET | `/notifications/settings/channels` | `notifications.settings.view` |
| POST | `/notifications/settings/channels` | `notifications.settings.manage` |
| GET | `/notifications/settings/channels/{channelId}` | `notifications.settings.view` |
| PUT | `/notifications/settings/channels/{channelId}` | `notifications.settings.manage` |

`channelId` là UUID của bản ghi trong `notification_settings`, không phải enum kênh.
Response có field `id` để frontend gọi GET/PUT. Truy vấn luôn lọc theo cả ID và tenant
từ principal; ID không tồn tại, đã xóa hoặc thuộc tenant khác trả 404. Schema của
`connectionConfig` lấy theo enum `channel` trong bản ghi. PUT không nhận `type` hay
`channel` bên trong JSON. Các field không thuộc kênh sẽ bị từ chối với HTTP 422.

POST tạo mới nhận `channel`, `enabled`, tùy chọn `name` và `connectionConfig`; không cần version.
`name` dài tối đa 150 ký tự, không được rỗng hoặc chỉ có khoảng trắng khi gửi lên.
Nếu không gửi tên lúc tạo, API dùng tên enum kênh. PUT có thể đổi tên; nếu không gửi
`name` thì giữ tên hiện tại. Tên được trim và trả về trong GET/POST/PUT.
Tenant lấy từ principal. Khi bỏ `connectionConfig`, cấu hình ban đầu là object rỗng.
API trả 201, JSON có `id`/`version`, và Location trỏ tới GET/PUT theo ID vừa tạo.
TELEGRAM và LARK cho phép nhiều nhóm trong cùng tenant. Mỗi nhóm có ID, tên và cấu hình kết nối riêng.
POST tạo nhóm mới. GET/PUT dùng ID để xem và sửa đúng nhóm.
IN_APP và EMAIL có một bản ghi mỗi tenant/kênh, POST trùng trả 409 kể cả khi đã soft-delete.
Danh sách trả các nhóm chưa xóa. Cursor dùng channel và setting ID để phân trang.

```http
POST /api/v1/notification-settings/channels
Content-Type: application/json
```

```json
{"channel":"LARK","name":"Lark alerts","enabled":false,"connectionConfig":{"appToken":"secrets/lark"}}
```

PUT yêu cầu `enabled` và `version`. Khi có `connectionConfig`, object này **thay thế
toàn bộ cấu hình**: field bỏ qua sẽ bị xóa; `{}` xóa cấu hình nhưng giữ dòng và version.
Khi không gửi `connectionConfig`, API chỉ đổi enabled và giữ cấu hình hiện tại.
Không trộn `connectionConfig` với các field Telegram cũ (`botToken`, `chatId`,
`messageThreadId`) ở cấp body.

Mọi cập nhật qua PUT đều dùng `UpdateChannelCommand` và
`NotificationSettingService.updateChannel(tenantId, channelId, change)`.
`ChannelUpdate` gom cấu hình JSON, bật/tắt, tên và các field Telegram legacy;
service kiểm tra version và lưu thay đổi trong cùng transaction.

| Kênh | Field trong `connectionConfig` | Field trả về trong `connectionConfig` |
| --- | --- | --- |
| IN_APP | Object rỗng `{}` | `{}` |
| TELEGRAM | `url`, `token`, `chatId` | `chatId` |
| EMAIL | `connectionRef`, `credentialsRef`, `fromAddress`, `replyTo` | `fromAddress`, `replyTo` |
| LARK | `webhookUrl`, `appSecretRef`, `appToken` | `{}` |

Các field credentials (`token`, `credentialsRef`, `appSecretRef`, `appToken`) là
**tham chiếu tới secret store**, không phải token/password thô. GET/PUT response
không trả các tham chiếu này, URL provider hoặc webhook URL. Webhook URL có thể
chứa bí mật trong đường dẫn. Dùng `credentialsConfigured` để biết credentials đã có.
GET trả dữ liệu đã che, vì vậy không dùng response làm payload thay thế toàn bộ.

Các field cấu hình đều tùy chọn khi lưu draft disabled; field có gửi phải là string
không rỗng. Giới hạn: url/connectionRef 200, secret reference 300, chatId 100,
email address 254, webhookUrl 2048 ký tự. URL phải là HTTPS, không chứa userinfo
hoặc fragment; Telegram base URL không chứa query. Email address phải hợp lệ.

### Ví dụ PUT

`PUT /api/v1/notification-settings/channels/{channelId}`

```json
{
  "enabled": false,
  "version": 0,
  "connectionConfig": {
    "url": "https://api.telegram.org",
    "token": "secrets/tenant-a/telegram",
    "chatId": "-100123456789"
  }
}
```

`PUT /api/v1/notification-settings/channels/{channelId}`

```json
{
  "enabled": false,
  "version": 0,
  "connectionConfig": {
    "connectionRef": "smtp/tenant-a",
    "credentialsRef": "secrets/tenant-a/email",
    "fromAddress": "notifications@example.com",
    "replyTo": "support@example.com"
  }
}
```

`PUT /api/v1/notification-settings/channels/{channelId}`

```json
{
  "enabled": false,
  "version": 0,
  "connectionConfig": {
    "webhookUrl": "https://open.larksuite.com/open-apis/bot/v2/hook/example",
    "appSecretRef": "secrets/tenant-a/lark",
    "appToken": "secrets/tenant-a/lark-app-token"
  }
}
```

`PUT /api/v1/notification-settings/channels/{channelId}`

```json
{"enabled": true, "version": 0, "connectionConfig": {}}
```

### Ví dụ response Telegram

```json
{
  "id": "019a578e-1234-7000-8000-000000000001",
  "name": "Telegram alerts",
  "channel": "TELEGRAM",
  "configured": true,
  "enabled": false,
  "available": false,
  "credentialsConfigured": true,
  "connectionVerified": false,
  "botUsername": null,
  "chatId": "-100123456789",
  "messageThreadId": null,
  "version": 1,
  "connectionConfig": {"chatId": "-100123456789"}
}
```

## Version, validation và provider

- Không có dòng: danh sách vẫn trả trạng thái mặc định với id=null, configured=false, version=0.
- GET/PUT theo UUID chỉ làm việc với bản ghi hiện có; không upsert hoặc restore bản ghi đã xóa.
- Ghi đồng thời hoặc version cũ trả 409. No-op vẫn tăng version; mutation dùng version mới nhất.
- Chưa xác thực: 401; thiếu tenant/quyền: 403; UUID không hợp lệ hoặc JSON sai: 400.
- Thiếu enabled/version, field sai schema, giá trị không hợp lệ hoặc provider chưa sẵn sàng: 422.
- Không cho enable external channel khi thiếu provider adapter hoặc cấu hình chưa được xác minh.

`NotificationSettingConnectionPolicy` là cổng provider/secret store. Hiện chỉ
IN_APP có available/ready=true; EMAIL/TELEGRAM/LARK lưu được draft disabled bằng
tham chiếu credentials. Adapter thực phải xác minh quyền sở hữu tham chiếu theo
tenant và gắn readiness với đúng revision cấu hình. Nhập token thô qua field
Telegram legacy vẫn yêu cầu secret-store adapter và trả 422 khi chưa có adapter.
Test connection, gửi thử và ngắt kết nối chưa nằm trong các API triển khai ở đây.

## Persistence

`NotificationChannelSettingJpaEntity` dùng UUIDv7, unique tenant/id/channel để bảo vệ FK delivery, audit,
soft delete và optimistic locking. `channel` là enum lưu bằng `EnumType.STRING`;
`connectionConfig` là JSONB. Mapper chọn record cấu hình theo enum khi đọc/ghi,
không suy đoán loại từ field JSON. Legacy Telegram aliases vẫn đọc được.

V19 cho phép nhiều nhóm Lark. V22 mở rộng cho Telegram. Partial unique index tiếp tục
giới hạn mỗi tenant ở một cấu hình IN_APP và một cấu hình EMAIL. Khi có nhiều nhóm cùng
kênh, dùng settingId để chọn nhóm cần cập nhật và version để kiểm tra xung đột.

V9 backfill UUIDv7. V10 chuyển các cột connection cũ sang JSONB. V11 cho phép
LARK trong constraint channel. API bật/tắt giữ toàn bộ cấu hình riêng của kênh;
PUT có connectionConfig thực hiện thay thế toàn bộ, bao gồm appToken của LARK.
V12 thêm `name varchar(150) NOT NULL`, điền tên enum kênh cho bản ghi cũ và chặn tên trống.

Feature subscription, recipient selection và personal preference chưa triển khai.
HTTP runtime nhận các request cấu hình qua Gateway và tra quyền theo tenant trong Redis của IAM.

## Phân nhóm quy tắc thông báo

Các bảng cấu hình gồm `notification_settings` cho kết nối kênh và
`notification_alert_rules` cho trạng thái và nhóm nghiệp vụ. Bảng `notification_setting_types` lưu liên kết giữa rule và nhóm chat.

| Method | Endpoint | Quyền |
| --- | --- | --- |
| GET | `/notifications/settings/rules` | `notifications.settings.view` |
| GET | `/notifications/settings/rules/{ruleId}` | `notifications.settings.view` |
| POST | `/notifications/settings/rules` | `notifications.settings.manage` |
| PUT | `/notifications/settings/rules/{ruleId}` | `notifications.settings.manage` |

`ruleId` là UUID bản ghi. Tenant lấy từ principal; ID tenant khác, đã xóa hoặc không tồn tại trả 404.
`type` lúc tạo là mã enum `NotificationTypeCode`, không đổi qua PUT.
Từ V26, `featureCode` là nhóm nghiệp vụ lưu trong cột `feature_code`. Ví dụ `INVENTORY` là kho hàng, `PRODUCT` là sản phẩm và `FULFILLMENT` là đơn hàng.
Mỗi tenant chỉ có một rule cho mỗi type. POST trùng trả 409, kể cả bản ghi đã soft-delete.
PUT yêu cầu version hiện tại; version cũ trả 409, kể cả hai request ghi đồng thời.

POST không cần version, trả 201 và Location theo ID:

```json
{"type":"STOCK_LOW","enabled":true,"featureCode":"INVENTORY"}
```

PUT cập nhật nhóm nghiệp vụ và trạng thái bật/tắt:

```json
{"enabled":true,"featureCode":"INVENTORY","version":1}
```

Mỗi rule trả `featureCode` và `featureName` để giao diện nhóm theo cùng nghiệp vụ với hộp thư.
GET chỉ trả các dòng còn hiệu lực trong bảng `notification_alert_rules` của tenant. Dữ liệu type cũ ngoài catalog vẫn đọc được, nhưng không tạo
hoặc cập nhật như type đang hỗ trợ.

Rule phải bật và kết nối phải sẵn sàng. Telegram/Lark cần liên kết đúng rule trong `notification_setting_types`.
Không có truy vấn feature, recipient list hoặc personal preference trong policy này;
kiểm tra người nhận/quyền vẫn do RecipientDirectory đảm nhiệm.

V13 thêm `lark_enabled` vào bảng rule. Các cột `thresholds` và `cooldown_seconds` cũ được
giữ nguyên dữ liệu, không đưa vào API cấu hình mới. Các API test connection, gửi thử và
provider adapter vẫn là phần chưa triển khai.

## List response

GET `/notification-settings/channels` and `/notifications/settings/rules` use the shared
`PaginationRequest` and return `ListEnvelope`: `object`, `url`, `data`, `pageInfo`.
Use `pageMode=offset&page=1&limit=20` (maximum limit 100), or `pageMode=cursor&limit=20`.
Continue cursor pages with `pageMode=cursor&cursor=<nextCursor>`. Cursor keys use the
channel key or persisted rule type. Read items from `data`.
## Chọn loại thông báo cho từng nhóm Telegram hoặc Lark

FE quản lý các rule của nhóm qua `/api/v1/notifications/settings/channels/{settingId}/rules`.

| Method | Quyền | Dữ liệu |
| --- | --- | --- |
| GET | `notifications.settings.view` | Trả `settingId`, `version` và danh sách UUID `ruleIds`. |
| PUT | `notifications.settings.manage` | Nhận `version` từ lần đọc và toàn bộ `ruleIds` muốn giữ. Trả cấu hình sau khi lưu. |

Khi chỉnh sửa nhóm, FE chọn UUID từ danh sách rule đã lưu của tenant.
Gửi `{"version":1,"ruleIds":[]}` để gỡ toàn bộ rule của nhóm. Nếu phiên bản đã cũ,
API trả 409. Nếu ID rule không tồn tại trong tenant, API trả 404.

API tạo và sửa kênh hiện có vẫn nhận và trả `allowedTypes`. Từ V25, database lưu mỗi lựa chọn bằng một dòng
`notification_setting_types(id, setting_id, rule_id)`. Nếu tenant chưa có rule cho loại đã chọn,
thao tác lưu nhóm tạo rule với nhóm nghiệp vụ mặc định của loại đó.

Khi tạo nhóm bằng `POST /api/v1/notifications/settings/channels` hoặc sửa nhóm bằng
`PUT /api/v1/notifications/settings/channels/{channelId}`, truyền `allowedTypes` ở cấp
ngoài cùng. API đọc kênh cũng trả trường này. Ví dụ dưới đây chọn hai loại cho nhóm Kho
khi cập nhật một cấu hình đang tắt:

```json
{"enabled":false,"version":1,"allowedTypes":["STOCK_LOW","STOCK_PUSH_FAILED"]}
```

Nếu bỏ qua `allowedTypes` khi sửa, nhóm giữ danh sách hiện tại. Gửi `[]` để xóa lựa
chọn. Nhóm mới, nhóm Lark có trước V23 và nhóm Telegram có trước V24 mặc định nhận 0 loại. Muốn đưa một thông báo vào
hàng đợi, rule của tenant phải bật, đúng nhóm phải được bật, xác minh
kết nối và cho phép loại đó. Chỉ người có quyền quản lý cấu hình mới sửa được danh sách.

Trong hộp thư, mỗi người chỉ đọc được thông báo đã được gửi cho mình trong tenant.
Luồng phân phối kiểm tra `required_permission` khi chọn người nhận. Với nhóm chat dùng chung, bộ điều phối yêu cầu adapter
`RecipientDirectory` xác minh danh sách thành viên đầy đủ của đúng đích gửi, và
mọi thành viên đều phải đủ điều kiện nhận thông báo qua Lark. Nếu chưa xác minh được
thành viên, bộ điều phối bỏ qua nhóm. Worker gửi thực tế cần kiểm tra lại thành viên
và quyền ngay trước khi gửi.
