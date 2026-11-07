# Module setting

Quản lý cấu hình kết nối theo tenant trong `notification_settings`. Có bốn kênh:
`IN_APP`, `EMAIL`, `TELEGRAM`, `LARK`.

## API cấu hình kết nối

Base path: `/api/v1`. Tenant lấy từ `GatewayPrincipal`, không lấy từ body/query.

| Method | Endpoint | Quyền |
| --- | --- | --- |
| GET | `/notification-settings/channels` | `notifications.settings.view` |
| POST | `/notification-settings/channels` | `notifications.settings.manage` |
| GET | `/notification-settings/channels/{channelId}` | `notifications.settings.view` |
| PUT | `/notification-settings/channels/{channelId}` | `notifications.settings.manage` |

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
Mỗi tenant chỉ có một bản ghi cho mỗi kênh: POST trùng trả 409, không ghi đè hoặc
khôi phục bản ghi đã soft-delete. Điều kiện unique vẫn được giữ khi tạo đồng thời.

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

`NotificationChannelSettingJpaEntity` dùng UUIDv7, unique tenant/channel, audit,
soft delete và optimistic locking. `channel` là enum lưu bằng `EnumType.STRING`;
`connectionConfig` là JSONB. Mapper chọn record cấu hình theo enum khi đọc/ghi,
không suy đoán loại từ field JSON. Legacy Telegram aliases vẫn đọc được.

V9 backfill UUIDv7. V10 chuyển các cột connection cũ sang JSONB. V11 cho phép
LARK trong constraint channel. API bật/tắt giữ toàn bộ cấu hình riêng của kênh;
PUT có connectionConfig thực hiện thay thế toàn bộ, bao gồm appToken của LARK.
V12 thêm `name varchar(150) NOT NULL`, điền tên enum kênh cho bản ghi cũ và chặn tên trống.

Feature/type rule/recipient/preference chưa triển khai trong module này. Main
HTTP runtime và Gateway verifier production vẫn chưa có trong service.
