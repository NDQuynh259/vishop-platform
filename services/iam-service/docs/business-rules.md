# Nghiệp vụ IAM

IAM sở hữu tài khoản toàn cục, tenant, thành viên, vai trò, quyền và phiên đăng nhập.
Không sở hữu gói cước, thanh toán hoặc cấu hình kênh thông báo.

- Đăng ký/đăng nhập tạo phiên có `sid`; access và refresh JWT dùng khóa riêng.
- Logout thu hồi phiên hiện tại. Thay đổi nhạy cảm của tài khoản/thành viên làm mất
  hiệu lực credential cũ qua `tokenVersion`.
- Quyền theo tenant dựa vào membership và role; không dùng vai trò của tenant khác.
- Thay đổi quyền role cập nhật projection Redis để Gateway áp dụng quyền hiện hành.
- Lời mời thành viên có thời hạn, tenant và role cụ thể; việc chấp nhận phải kiểm tra
  các điều kiện nghiệp vụ và chống chấp nhận lại.
- IAM phát sự kiện qua outbox; gửi email/lời mời qua provider thuộc Notification.

Contract endpoint, cookie và cấu hình: [README service](../README.md).
Chi tiết cấp/thu hồi credential: [Authentication flow](../../../docs/core-architecture/authentication-flow.md).
Chính sách quyền dùng chung: [Authorization policy](../../../docs/core-architecture/authorization-policy.md).
