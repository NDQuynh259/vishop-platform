# Inbox và bảng thông báo

Phần inbox thuộc module `notification`, ánh xạ hai bảng: `notifications` chứa nội dung chung bất biến;
`notification_recipients` chứa người nhận và trạng thái đọc/ẩn độc lập của mỗi user.
V15 thêm cột `id` UUIDv7 làm khóa chính riêng cho recipient, giữ unique
`(tenant_id, notification_id, user_id)` và trạng thái đọc/ẩn hiện có.

## API cá nhân

Base path `/api/v1/notifications`. Các endpoint yêu cầu đăng nhập; tenant, user và
permissions lấy từ principal do gateway/filter tạo, không lấy từ body hoặc query string.

| Method | Endpoint | Kết quả |
| --- | --- | --- |
| GET | `/notifications` | List envelope, offset hoặc cursor, mặc định 20 mục, tối đa 100 |
| GET | `/notifications/unread-count` | `{"count": 3}` |
| GET | `/notifications/stream` | SSE: `ready`, `notification.created` và heartbeat |
| GET | `/notifications/{notificationId}` | Nội dung và readAt của chính người nhận |
| PATCH | `/notifications/{notificationId}/read` | Đọc idempotent, trả item và giữ readAt đầu tiên |
| POST | `/notifications/read-all` | Đọc tới cutoff, trả `{"updatedCount": 3}` |
| DELETE | `/notifications/{notificationId}` | 204, xóa mềm riêng recipient; lặp lại 204 khi recipient chưa bị dọn cứng |

List hỗ trợ `type`, `severity`, `read`, `shopId`, `page`, `limit`, `cursor`, `pageMode`. Quyền được áp dụng
trong DB trước khi phân trang. Thứ tự `created_at DESC, id DESC`; cursor giữ đủ microsecond
để không bỏ sót thông báo được tạo trong cùng millisecond. GET không tự đánh dấu đã đọc.

Dùng `PaginationRequest` chung của project: mặc định offset, page bắt đầu từ 1.
`GET /notifications?page=2&limit=20` trả `pageInfo` gồm page/limit/total/totalPages/hasNextPage.
Total chỉ đếm những mục đúng bộ lọc mà user hiện tại được phép xem.

`GET /notifications?pageMode=cursor&limit=20` lấy trang cursor đầu tiên;
trang tiếp theo dùng `GET /notifications?pageMode=cursor&cursor=<nextCursor>&limit=20`.
Chỉ pageMode quyết định kiểu phân trang. pageMode=offset dùng page/limit và bỏ qua cursor.
Cursor trả hasNextPage/nextCursor, không đếm total. Giữ nguyên bộ lọc khi chuyển trang.
pageMode chỉ nhận offset/cursor. Không truyền limit dùng 20; không truyền page dùng 1
khi dùng offset. Page phải từ 1 trở lên, limit phải trong 1–100; sai phạm vi trả 422
cho cả offset và cursor. Tham số page/limit không phải số và cursor sai định dạng trả 400.
Offset vượt giới hạn integer của JPA trả 400.

Body đọc tất cả: `{"before":"2026-11-07T12:00:00.123456Z"}`. Cutoff không được ở tương lai;
thông báo tạo sau cutoff không bị đánh dấu đọc.

## Phạm vi truy cập

Mọi list/detail/count/read/read-all/archive đều yêu cầu liên kết recipient đúng tenant/user,
notification chưa xóa mềm và user hiện có `required_permission`. Mọi shop thuộc tenant đang đăng nhập
đều được phép truy cập nên các thông báo có gắn `shopId` trong cùng tenant hiển thị bình thường
(và vẫn hỗ trợ lọc theo query `?shopId=`). Các thao tác đọc bỏ recipient đã ẩn. Ngoài phạm vi hoặc
không tồn tại trả 404 theo ID; count/list không để lộ mục bị chặn. Quyền cấu hình không cấp thêm
quyền đọc nội dung nghiệp vụ.

Inbox cá nhân dùng `isAuthenticated()` và kiểm tra từng item ở DB, không yêu cầu permission
`notifications.view` chưa có trong catalog IAM hiện tại. Không nhận scope/permissions từ client.

## Luồng xác thực từ Gateway đến Controller

Hộp thư cá nhân không yêu cầu permission tĩnh mà bảo vệ bằng `@PreAuthorize("isAuthenticated()")`. Luồng chuyển giao context từ Gateway vào Controller:

```text
[ 1. Client ]
      │  Gửi request kèm Token:
      │  GET /api/v1/notifications
      │  Authorization: Bearer eyJhbGci...
      ▼
[ 2. API Gateway ]
      │  • Gateway giải mã & kiểm tra Token hợp lệ.
      │  • Gateway bóc tách User ra và chuyển thành các Header nội bộ:
      │      X-User-Id: "0192d244-..."
      │      X-Tenant-Id: "0192d244-..."
      │      X-Role-Id: "0192d244-..."
      │      X-System-Role: "USER"
      │  • Gateway bắn tiếp HTTP request này vào mạng nội bộ tới notification-service.
      ▼
[ 3. notification-service: GatewayIdentityFilter ]
      │  (Đứng chắn trước mọi Controller)
      │  • Filter đọc các header "X-User-Id", "X-Tenant-Id"... từ request.
      │  • Nếu có header hợp lệ ──► Filter tự tay "nạp" vào Spring Security:
      │      SecurityContextHolder.getContext().setAuthentication(
      │          new UsernamePasswordAuthenticationToken(actor, null, authorities)
      │      );
      ▼
[ 4. Controller: @PreAuthorize("isAuthenticated()") ]
      │  • Trước khi Controller chạy, Spring Security kiểm tra:
      │    "Trong SecurityContextHolder của Thread này đã có Authentication chưa?"
      │  • Vì Filter ở bước 3 đã nạp vào rồi ──► Spring biết ngay user ĐÃ ĐĂNG NHẬP ──► Cho phép chạy!
```

### Cơ chế chi tiết:
1. **API Gateway forward Header**: Gateway loại bỏ token thô và chèn các HTTP header tin cậy: `X-User-Id`, `X-Tenant-Id`, `X-Role-Id`, `X-System-Role`, `X-Session-Id`.
2. **`GatewayIdentityFilter` nạp Context**: Đọc các header này qua `GatewayIdentityHeaders`, lấy role permissions từ Redis (nếu có tenant), khởi tạo `GatewayPrincipal actor`, và nạp vào `SecurityContextHolder` kèm `TenantContext`.
3. **`@PreAuthorize("isAuthenticated()")` bảo vệ Controller**: Spring AOP kiểm tra `SecurityContextHolder.getContext().getAuthentication()`. Do đối tượng `UsernamePasswordAuthenticationToken` đã được nạp và có trạng thái `authenticated=true`, request được phép vào Controller.


## Tạo nội bộ và transaction

Luồng `CreateNotificationCommand → NotificationService → NotificationJpaAdapter`
lưu notification và recipient trong cùng transaction. ID mới là UUIDv7. Không có POST tạo nội dung
hoặc PUT sửa payload từ client; adapter sự kiện và RecipientDirectory xác thực nguồn và tìm
người nhận đủ điều kiện trước khi gọi command.

Natural key `(tenant_id, source_event_id, type, subject_type, subject_id)` chống tạo trùng:
replay payload giống hệt trả ID cũ, giữ người nhận/trạng thái đọc; cùng key nhưng payload khác
trả conflict. Replay không thêm người nhận mới vào snapshot đã phát hành. actionUrl chỉ chấp
nhận đường dẫn nội bộ ứng dụng.

Đánh dấu đọc/ẩn dùng cập nhật có điều kiện ở DB, chỉ tăng version recipient khi thay đổi;
không sửa notification chung hoặc recipient khác. Read-all chỉ tác động mục được phép đọc,
chưa đọc và nằm trước cutoff.

Job [retention recipient](retention.md) có thể bật để dọn cứng: recipient còn trong inbox hết hạn
sau 90 ngày từ lúc tạo, kể cả chưa đọc; recipient xóa mềm hết hạn sau 90 ngày từ lúc xóa.
Sau khi dọn, thao tác theo ID trả 404 cho recipient đó. Giữ nội dung chung, delivery và
recipient khác; replay không tạo lại recipient đã dọn. Job mặc định tắt và cấu hình được.

`NotificationModuleConfiguration` đăng ký entity, adapter, service và CQRS handler.
Schema có sẵn từ V1/V5 và khóa chính recipient riêng từ V15. Event consumer production, DispatchJournal/outbox toàn workflow,
runtime HTTP/gateway và provider sender vẫn cần wiring/adapter tương ứng.
Fan-out SSE giữa replica đã có adapter [Redis Pub/Sub](realtime-redis.md).

## SSE realtime

`GET /api/v1/notifications/stream` trả `Content-Type: text/event-stream`,
`Cache-Control: no-store`, `X-Accel-Buffering: no`. `NotificationModuleConfiguration`
đăng ký controller, registry và listener. Runtime phải bật method security và cấu hình
filter xác thực đáng tin cậy giống các API inbox. Không nhận user/tenant/permissions từ client.

Luồng mặc định: tạo notification → DB commit → `NotificationRedisRelay` publish Redis → mọi
instance nhận Redis → `broadcastLocal()` gửi tới SSE đang giữ ở instance đó. `@TransactionalEventListener`
nằm ở publisher; registry SSE không tự lắng nghe Spring event. Xem [cấu hình Redis](realtime-redis.md).

Kết nối bắt đầu bằng:

```text
event:ready
retry:3000
data:{}

```

Khi tạo mới notification có recipient in-app, chỉ sau khi transaction commit thành công:

```text
event:notification.created
data:{"notificationId":"<uuid>","tenantId":"<uuid>"}

```

Rollback và replay idempotent không phát tín hiệu. Chỉ các kết nối có đúng tenant, user thuộc
snapshot recipient và có `required_permission` mới nhận event. Payload không chứa danh sách
người nhận hay nội dung nghiệp vụ. Registry index theo `(tenantId, userId)` nên mỗi event chỉ tra
các recipient của nó, không quét toàn bộ kết nối. Mỗi tab có kết nối riêng; mặc định tối đa 5 kết nối
cho một user/tenant trên mỗi instance, 1000 kết nối mỗi instance; vượt giới hạn trả 429 và `Retry-After: 3`.

Lỗi stream dùng `BadRequestException`, `TooManyRequestsException` và `ServiceUnavailableException`
từ `common-domain`; `GlobalExceptionHandler` trong `common-web` trả Problem Details cùng
`code`, `requestId`, `timestamp`, `instance`. Controller không có handler riêng.
SSE chỉ định retry 3 giây cho lỗi hạn mức và shutdown; lỗi thiếu tenant/user không có `Retry-After`.

Heartbeat là comment `:heartbeat` khi kết nối không vừa gửi dữ liệu. Mặc định gửi sau 12–15 giây
nhàn rỗi, có jitter để rải thời điểm gửi và scheduler kiểm tra mỗi giây. Không xếp heartbeat khi
writer đang chạy hoặc chờ. Kết nối sống tối đa 60 giây rồi đóng để client
kết nối lại qua gateway và lấy quyền/session hiện hành. Quyền trong stream là snapshot lúc
subscribe; thay đổi quyền hoặc logout chưa chủ động ngắt stream đang mở, tối đa tới hết lifetime.
REST vẫn kiểm tra quyền hiện hành khi tải nội dung. Registry dọn kết nối khi timeout, lỗi,
completion và shutdown. Mỗi kết nối có tối đa một tác vụ ghi đang chạy hoặc chờ trên pool riêng,
và mailbox FIFO mặc định tối đa 32 event chờ ngoài event đang ghi. `ready` cũng nằm trong mailbox
và luôn đi trước thông báo. Pool mặc định có 4 platform thread; queue tác vụ tối đa bằng hạn mức
kết nối. Không gộp hay thay ID của `notification.created`.

Mailbox đầy chỉ ngắt kết nối tương ứng để client reconnect và đồng bộ lại qua REST. Gỡ registry
và bỏ event chờ ngay; nếu đang ghi socket, writer hoàn tất/đóng transport sau khi lần ghi trả về,
không bắt thread sau commit hoặc heartbeat chờ socket đó. Slot toàn instance chỉ được trả lại
khi writer kết thúc, tránh liên tục nhận kết nối mới trong khi writer cũ còn bị kẹt.
Một client chậm không thể chiếm nhiều worker bằng các tác vụ ghi xếp chồng; nhiều client chậm
vẫn có thể chiếm hết pool, nên timeout ghi socket tại runtime/proxy vẫn bắt buộc.

Các hạn mức có thể cấu hình trong runtime import `NotificationModuleConfiguration`:

`NotificationSseProperties` thuộc `notification.infrastructure.config`, phụ trách binding Spring.
Module configuration chuyển properties thành `NotificationSseSettings` thuần Java để bộ phát SSE
trong `presentation.realtime` không phụ thuộc vào infrastructure.

```yaml
notification:
  sse:
    max-connections: 1000
    max-connections-per-user: 5
    max-pending-events: 32
    writer-threads: 4
    heartbeat-interval: 15s
```

Các giới hạn phải dương; heartbeat từ 1ms và nhỏ hơn 60s. Jitter gửi sớm tối đa 20% so với interval.
Lifetime 60 giây được giữ cố định vì chưa có adapter thu hồi session/quyền cho stream sống lâu.
Tăng hạn mức không chứng minh hệ thống phục vụ được lượng tải tương ứng; cần load test cùng
gateway và REST inbox/count trước khi chọn cấu hình production.

Frontend phải tải lại inbox/count khi nhận `ready` (cả lần đầu, reconnect và mọi lần phát lại trên cùng stream),
`notification.created`, và khi đổi tenant; đóng stream cũ khi logout hoặc đổi tenant.
Nên gộp các lần refetch liên tiếp và xử lý event tới trong lúc request đang chạy bằng một lần
refetch bổ sung. Không tự tăng badge theo số event. Stream không có replay log hoặc event ID;
`Last-Event-ID` không dùng để khôi phục dữ liệu. DB/REST là nguồn chuẩn khi mất kết nối hoặc process restart.
Redis resubscribe hoặc listener quá tải có thể làm `ready` phát lại để bù tín hiệu bị lỡ.
Frontend cũng cần đối soát REST định kỳ và khi trở lại tab; gộp refetch và rải thời điểm đối soát.

Gateway hiện hỗ trợ cookie `access_token` khi không có header Authorization.
Frontend dùng cookie xác thực có thể kết nối trực tiếp:

```javascript
const stream = new EventSource('/api/v1/notifications/stream', { withCredentials: true });
stream.addEventListener('ready', refreshInboxAndCount);
stream.addEventListener('notification.created', refreshInboxAndCount);
// Gọi stream.close() khi logout, đổi tenant hoặc unmount.
```

Gateway cũng hỗ trợ bearer token: native `EventSource` không hỗ trợ đặt header Authorization.
Nếu frontend dùng bearer header, cần client SSE qua `fetch`/thư viện hỗ trợ bearer header, parse event stream,
reconnect và hủy bằng AbortController. Không truyền access token trên URL.

Runtime notification hiện chưa có server standalone hoặc route gateway. Khi wiring runtime,
thêm route `/api/v1/notifications/**`, giữ response streaming, tắt buffering/cache/compression
gây gom chunk trên proxy, đặt idle timeout lớn hơn heartbeat và cho phép async dispatch
trong security filter chain. Thiết lập timeout ghi socket để client chậm không giữ writer vô hạn.

Mỗi instance vẫn giữ registry và socket cục bộ. Transport mặc định `notification.realtime.transport=redis`
phân phối tín hiệu sau commit tới mọi replica dùng chung channel. Không gửi trực tiếp SSE ở publisher
để tránh gửi trùng tại instance tạo notification. Redis Pub/Sub không replay; mất tín hiệu được bù
bằng REST. Chế độ `local` chỉ dành cho dev/test hoặc một instance; xem [Redis fan-out](realtime-redis.md).
