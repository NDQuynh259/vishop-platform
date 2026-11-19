# Xóa mềm và dọn recipient hết hạn

Người dùng xóa thông báo bằng `DELETE /api/v1/notifications/{id}`: chỉ cập nhật
`notification_recipients.deleted_at`, không xóa nội dung chung hoặc recipient của người khác.
List/detail/unread-count bỏ qua recipient đã xóa mềm. Gọi DELETE lặp lại trả 204 trong khi
recipient còn tồn tại; sau khi dọn cứng, ID đó trả 404 cho người dùng này.

## Mốc hết hạn

| Recipient | Điều kiện dọn cứng mặc định |
| --- | --- |
| Mọi bản ghi recipient (còn trong inbox hoặc đã xóa mềm, đã đọc hoặc chưa đọc) | `created_at <= thời điểm chạy − 90 ngày` |

Dùng một mốc thời gian duy nhất tính từ ngày tạo (`created_at`). Recipient chưa đọc hoặc đã xóa mềm đều hết hạn khi quá 90 ngày kể từ lúc tạo.
Scheduler dùng thời gian UTC từ `Clock`, lấy cutoff một lần cho mỗi lượt, giữ độ chính xác microsecond.
Hết hạn có nghĩa đủ điều kiện dọn; bản ghi biến mất khi batch thực sự commit. Backlog hoặc
row đang bị khóa có thể khiến thời điểm xóa thực tế trễ hơn mốc 90 ngày.

Scheduler chỉ xóa `notification_recipients`. Giữ `notifications`, khóa chống trùng sự kiện và
`notification_deliveries`; không cascade từ notification. Replay sự kiện có payload giống hệt
vẫn trả ID notification cũ và không tạo lại recipient đã dọn. Chính sách này không dọn toàn bộ
database: nội dung chung và delivery cần retention riêng nếu muốn giới hạn dung lượng của chúng.

## Cấu hình

Scheduler mặc định **tắt**. Ứng dụng chạy module có thể khai báo `notification.retention`
trong YAML của mình hoặc import cấu hình theo profile qua `spring.config.import`.
Dùng `classpath:notification/application-dev.yml` cho dev và
`classpath:notification/application-prod.yml` cho prod.
Sau khi áp dụng V21, đặt `NOTIFICATION_RETENTION_ENABLED=true` để bật scheduler.
Ví dụ cấu hình bật scheduler:

```yaml
notification:
  retention:
    enabled: true
    period: 90d
    batch-size: 500
    max-batches-per-run: 10
    fixed-delay-ms: 300000
    initial-delay-ms: 60000
```

Mặc định bắt đầu sau 60 giây; lượt kế tiếp bắt đầu 5 phút sau khi lượt trước hoàn tất.
Mỗi lượt tối đa 10 batch, mỗi batch tối đa 500 dòng: tối đa 5.000 recipient/instance/lượt.
Khi một batch xóa ít hơn `batch-size` dòng thì lượt chạy dừng sớm. Đây là giới hạn công việc, không phải cam kết throughput.
Giới hạn hợp lệ: batch 1–10.000 dòng; 1–100 batch/lượt; `period` và fixed delay dương,
initial delay không âm. Cấu hình không hợp lệ làm startup thất bại.

Không có public endpoint xóa cứng. Đây là scheduled job trên toàn Notification DB,
không phải thao tác tenant từ request người dùng.

## Transaction và nhiều instance

`NotificationRetentionService` lặp tối đa `max-batches-per-run` batch, dừng sớm khi hết dữ liệu hết hạn.
Mỗi lệnh repository là transaction `REQUIRES_NEW`, timeout 10 giây; một batch lỗi không rollback các batch đã commit.
Scheduler ghi log lỗi và thử lại phần còn lại ở lượt sau.

Truy vấn chọn theo `(created_at, id)` với điều kiện `created_at <= :cutoff`, giới hạn số dòng rồi dùng
`FOR UPDATE SKIP LOCKED` trước khi DELETE trong cùng câu SQL. Các instance dùng chung DB
có thể chạy đồng thời: dòng đã bị worker khác khóa được bỏ qua và xét lại ở lượt sau.
Tổng tải dọn tăng theo số instance; cần điều chỉnh batch/delay hoặc chỉ bật scheduler trên runtime
chuyên trách nếu muốn giới hạn tải tổng. SKIP LOCKED không loại bỏ chờ khóa DDL/table.

[V21](../src/main/resources/db/migration/V21__unify_notification_recipient_retention_index.sql) thay hai
partial index của [V20](../src/main/resources/db/migration/V20__index_notification_recipient_retention.sql)
bằng một index `idx_notification_recipient_retention (created_at, id)` cho toàn bộ bảng `notification_recipients`.
Migration dùng `CREATE INDEX` thông thường, có thể chặn ghi trong lúc xây index: với bảng lớn, lên lịch bảo trì trước khi
chạy migration và bật scheduler. Chưa đo tải ở quy mô một tỉ dòng.

DELETE tạo dead tuples; autovacuum/VACUUM thu hồi để tái sử dụng, không đảm bảo file database
giảm ngay. Theo dõi thời gian batch, lỗi/timeout, backlog và dead tuples khi điều chỉnh cấu hình.

## Đồng bộ giao diện

Chưa phát SSE event riêng khi retention xóa recipient. Frontend đồng bộ lại list/unread-count
qua REST khi nhận `ready`, reconnect, quay lại tab và đối soát định kỳ theo
[contract SSE](inbox.md#sse-realtime). Không phát `notification.created` cho thao tác xóa.

## Kiểm chứng

`NotificationRetentionPersistenceTest` chạy PostgreSQL thật: cutoff bao gồm đúng mốc `created_at`
cho cả tin còn hiện (đã/chưa đọc) lẫn tin đã xóa mềm, giữ recipient khác/delivery, replay không hồi sinh,
batch có giới hạn, bỏ qua row bị worker khác khóa và commit độc lập.
Unit/config tests kiểm tra budget, dừng sớm khi cạn dữ liệu, bật/tắt, binding và retry sau lỗi.
