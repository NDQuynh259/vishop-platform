# Module setting

Quản lý cấu hình kết nối theo tenant trong `notification_settings`. Có bốn kênh:
`IN_APP`, `EMAIL`, `TELEGRAM`, `LARK`.

## API cấu hình kết nối

Base path: `/api/v1`. Tenant lấy từ `GatewayPrincipal`, không lấy từ body/query.

| Method | Endpoint | Quyền |
| --- | --- | --- |
| GET | `/notification-settings/channels` | `notifications.settings.view` |
| GET | `/notification-settings/channels/{channel}` | `notifications.settings.view` |
| PUT | `/notification-settings/channels/{channel}` | `notifications.settings.manage` |

`channel` trên URL quyết định schema của `connectionConfig`. Không gửi `type` hay
`channel` bên trong JSON. Các field không thuộc kênh sẽ bị từ chối với HTTP 422.

PUT yêu cầu `enabled` và `version`. Khi có `connectionConfig`, object này **thay thế
toàn bộ cấu hình**: field bỏ qua sẽ bị xóa; `{}` xóa cấu hình nhưng giữ dòng và version.
Khi không gửi `connectionConfig`, API chỉ đổi enabled và giữ cấu hình hiện tại.
Không trộn `connectionConfig` với các field Telegram cũ (`botToken`, `chatId`,
`messageThreadId`) ở cấp body.

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

`PUT /api/v1/notification-settings/channels/TELEGRAM`

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

`PUT /api/v1/notification-settings/channels/EMAIL`

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

`PUT /api/v1/notification-settings/channels/LARK`

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

`PUT /api/v1/notification-settings/channels/IN_APP`

```json
{"enabled": true, "version": 0, "connectionConfig": {}}
```

### Ví dụ response Telegram

```json
{
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

- Không có dòng: IN_APP mặc định bật; kênh khác tắt, configured=false, version=0.
- PUT upsert lần đầu dùng version=0 và trả version=1. Mutation tiếp theo dùng version mới nhất.
- Ghi đồng thời hoặc version cũ trả 409. No-op vẫn tăng version; restore dùng cùng khóa.
- Chưa xác thực: 401; thiếu tenant/quyền: 403; enum không hợp lệ hoặc JSON sai: 400.
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

Feature/type rule/recipient/preference chưa triển khai trong module này. Main
HTTP runtime và Gateway verifier production vẫn chưa có trong service.
