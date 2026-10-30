# Chính sách thay đổi quyền và thu hồi credential

Implementation hiện tại dùng JWT HS256, sid riêng cho mỗi lần login và trạng thái
phiên trong PostgreSQL/Redis. Xem [luồng auth](authentication-flow.md).

| Thay đổi | Hành vi |
| --- | --- |
| Logout | Thu hồi sid hiện tại; phiên khác vẫn hoạt động |
| Đổi role, khóa/xóa membership | Tăng tokenVersion toàn user, phải đăng nhập lại |
| Khóa/xóa tài khoản | Tăng tokenVersion, chặn truy cập và đăng nhập |
| Đổi mật khẩu hoặc systemRole | Tăng tokenVersion, phải đăng nhập lại |
| Sửa permissions của role | Đẩy role projection mới, không đăng xuất |

## Thu hồi riêng phiên

auth_sessions lưu id (sid), user_id, token_version lúc login, hạn phiên và revoked_at.
Logout cập nhật có điều kiện trong transaction; trigger tạo projection và handler
ghi audit event một lần. Sau commit IAM chờ Redis nhận REVOKED rồi trả 204.
Redis lỗi trả 503; retry idempotent, không tạo thêm UserLoggedOut.

Access và refresh cùng sid đều mất hiệu lực. Không tăng users.token_version khi
logout nên các phiên khác không bị thu hồi. Không dùng HttpSession.

## Thu hồi toàn user

Nguồn thật là users.token_version. Access và refresh đều chứa version khi cấp.
Session giữ bản chụp version lúc tạo; refresh không thay version để cứu phiên cũ.

Trigger tăng version cùng transaction khi đổi password/status/systemRole/deletedAt
của user hoặc đổi role/status/xóa membership. Gateway so với user projection;
IAM refresh/verifyAccessToken so với DB. Mở khóa không khôi phục credential cũ.
Profile và lastTenantId không tăng tokenVersion.

Một user version dùng chung mọi tenant và thiết bị; thay đổi membership ở một
tenant sẽ yêu cầu đăng nhập lại trên các phiên khác của user đó.

## Cập nhật quyền role

Cache key role bao gồm cả tenantId và roleId. Cùng roleKey ở hai tenant có thể có
permissions khác nhau. Sửa permissions không tăng tokenVersion; cập nhật revision
role projection để gateway lấy quyền mới. Các service tiếp tục kiểm tra permission
và tenant scope. IAM dùng quyền hiện tại từ DB.

Không đưa permissions vào JWT. Revision projection chỉ chống ghi lùi cache, khác
với tokenVersion thu hồi credential và version optimistic lock của auth_sessions.

## Độ trễ và triển khai

Logout 204 chờ Redis xác nhận. Những thay đổi khác đồng bộ qua authorization_outbox
và giới hạn freshness của gateway; không hứa mọi thay đổi có hiệu lực tức thời trên
request đang chạy. Cache/loader lỗi thì chặn request.

V12 thêm session và projection/trigger; migrate trước khi chạy bản mới. JWT cũ
không có sid bị từ chối. Chưa có job dọn các hàng auth_sessions hết hạn trong DB.
