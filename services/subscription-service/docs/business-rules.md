# Nghiệp vụ Subscription và Payment

Service thu phí theo tenant, hỗ trợ mua/gia hạn chủ động qua QR SePay; không tự động
trích tiền gia hạn. Chu kỳ tháng/năm theo lịch, timezone `Asia/Ho_Chi_Minh`.

- Giá và hạn mức được snapshot trên đơn, không thay đổi đơn cũ khi sửa catalog giá.
- Tiền lưu số nguyên VND; trạng thái đơn, thanh toán và cấp kỳ được theo dõi riêng.
- Webhook xác thực HMAC trên body gốc và lưu inbox bền vững trước khi xử lý.
- Chống nhận trùng theo SePay transaction ID và các ràng buộc tham chiếu ngân hàng.
  Cùng ID nhưng dữ kiện tiền khác không được xem là retry hợp lệ.
- Allocation ghi số tiền thực sự phân bổ; không phân bổ vượt số tiền đã nhận.
- Cấp kỳ idempotent theo đơn, không cộng hạn hai lần khi retry.
- Ghi tiền, cấp kỳ, cập nhật inbox và outbox cùng transaction.
- Khoản phát sinh trên đơn đã cấp kỳ giữ kết quả cấp kỳ và đi vào luồng review phù hợp.

API, quyền `billing.*`, trạng thái và các trường hợp review:
[README service](../README.md). Ràng buộc chi tiết: [Database](database-design.md).
