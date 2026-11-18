# Xóa mềm và dọn recipient hết hạn

Người dùng xóa thông báo bằng `DELETE /api/v1/notifications/{id}`: chỉ cập nhật
`notification_recipients.deleted_at`, không xóa nội dung chung hoặc recipient của người khác.
List/detail/unread-count bỏ qua recipient đã xóa mềm. Gọi DELETE lặp lại trả 204 trong khi
recipient còn tồn tại; sau khi dọn cứng, ID đó trả 404 cho người dùng này.

## Hai mốc hết hạn

| Recipient | Điều kiện dọn cứng mặc định |
| --- | --- |
| Còn trong inbox (`deleted_at IS NULL`), đã đọc hoặc chưa đọc | `created_at <= thời điểm chạy − 90 ngày` |
| Người dùng đã xóa mềm (`deleted_at IS NOT NULL`) | `deleted_at <= thời điểm chạy − 90 ngày` |

Hai đồng hồ độc lập: recipient tạo 100 ngày trước nhưng mới xóa mềm hôm nay được giữ
thêm 90 ngày kể từ hôm nay. Recipient chưa đọc vẫn hết hạn theo ngày tạo.
Job dùng thời gian UTC từ `Clock`, lấy cutoff một lần cho mỗi lượt, giữ độ chính xác microsecond.
Hết hạn có nghĩa đủ điều kiện dọn; bản ghi biến mất khi batch thực sự commit. Backlog hoặc
row đang bị khóa có thể khiến thời điểm xóa thực tế trễ hơn mốc 90 ngày.

Job chỉ xóa `notification_recipients`. Giữ `notifications`, khóa chống trùng sự kiện và
`notification_deliveries`; không cascade từ notification. Replay sự kiện có payload giống hệt
vẫn trả ID notification cũ và không tạo lại recipient đã dọn. Chính sách này không dọn toàn bộ
database: nội dung chung và delivery cần retention riêng nếu muốn giới hạn dung lượng của chúng.

## Cấu hình

Job mặc định **tắt**; bật tại runtime đã import `NotificationModuleConfiguration` sau khi áp dụng V20:

```yaml
notification:
  retention:
    enabled: true
    visible-retention: 90d
    archived-retention: 90d
    batch-size: 500
    max-batches-per-run: 10
    fixed-delay-ms: 300000
    initial-delay-ms: 60000
```

Mặc định bắt đầu sau 60 giây; lượt kế tiếp bắt đầu 5 phút sau khi lượt trước hoàn tất.
Mỗi lượt tối đa 10 batch, mỗi batch tối đa 500 dòng: tối đa 5.000 recipient/instance/lượt.
Batch rỗng cũng tính vào giới hạn. Đây là giới hạn công việc, không phải cam kết throughput.
Giới hạn hợp lệ: batch 1–10.000 dòng; 2–100 batch/lượt; retention và fixed delay dương,
initial delay không âm. Cấu hình không hợp lệ làm startup thất bại.

Không có public endpoint xóa cứng. Đây là maintenance job trên toàn Notification DB,
không phải thao tác tenant từ request người dùng.

## Transaction và nhiều instance

`NotificationRetentionService` luân phiên batch archived/visible để cả hai nhóm đều được xử lý.
Khi một nhóm hết dữ liệu, phần budget còn lại dành cho nhóm kia. Mỗi lệnh repository là
transaction `REQUIRES_NEW`, timeout 10 giây; một batch lỗi không rollback các batch đã commit.
Job ghi log lỗi và thử lại phần còn lại ở lượt sau.

Hai truy vấn chọn theo `(deleted_at, id)` hoặc `(created_at, id)`, giới hạn số dòng rồi dùng
`FOR UPDATE SKIP LOCKED` trước khi DELETE trong cùng câu SQL. Các instance dùng chung DB
có thể chạy job đồng thời: dòng đã bị worker khác khóa được bỏ qua và xét lại ở lượt sau.
Tổng tải dọn tăng theo số instance; cần điều chỉnh batch/delay hoặc chỉ bật job trên runtime
maintenance nếu muốn giới hạn tải tổng. SKIP LOCKED không loại bỏ chờ khóa DDL/table.

[V20](../src/main/resources/db/migration/V20__index_notification_recipient_retention.sql) thêm hai
partial index tương ứng cho recipient còn hiện và đã xóa mềm. Migration dùng `CREATE INDEX`
thông thường, có thể chặn ghi trong lúc xây index: với bảng lớn, lên lịch maintenance trước khi
chạy migration và bật job. Chưa đo tải ở quy mô một tỉ dòng.

DELETE tạo dead tuples; autovacuum/VACUUM thu hồi để tái sử dụng, không đảm bảo file database
giảm ngay. Theo dõi thời gian batch, lỗi/timeout, backlog và dead tuples khi điều chỉnh cấu hình.

## Đồng bộ giao diện

Chưa phát SSE event riêng khi retention xóa recipient. Frontend đồng bộ lại list/unread-count
qua REST khi nhận `ready`, reconnect, quay lại tab và đối soát định kỳ theo
[contract SSE](inbox.md#sse-realtime). Không phát `notification.created` cho thao tác xóa.

## Kiểm chứng

`NotificationRetentionPersistenceTest` chạy PostgreSQL thật: cutoff bao gồm đúng mốc,
recipient chưa đọc, hai đồng hồ độc lập, giữ recipient khác/delivery, replay không hồi sinh,
batch có giới hạn, bỏ qua row bị worker khác khóa và commit độc lập.
Unit/config tests kiểm tra budget, luân phiên hai nhóm, bật/tắt, binding và retry sau lỗi.
