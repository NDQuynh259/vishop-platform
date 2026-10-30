# Gateway authentication và phân quyền Redis

Xem [luồng auth chi tiết và sơ đồ](authentication-flow.md) cùng
[chính sách thu hồi](authorization-policy.md).

Gateway verify access JWT HS256 bằng access key và hợp đồng claims IAM:
issuer, audience, token_use, thời gian, userId, sid, tokenVersion và role theo tenant.
Permissions lấy từ Redis, không nằm trong JWT.

## Thứ tự xác minh

1. AuthenticationGlobalFilter phân loại route, kiểm tra Origin/write header và lấy credential.
2. LocalAuthenticationVerifier.verifyAccessToken verify JWT, đọc user snapshot,
   so sánh tokenVersion. JWT mới hơn cache sẽ tải lại snapshot để xác nhận.
3. resolveAuthorization kiểm tra user ACTIVE, rồi session ACTIVE, chưa hết hạn,
   đúng user và tokenVersion.
4. Nếu có tenant, đọc tenant và role snapshot; kiểm tra ACTIVE và đúng role thuộc tenant.
5. Tạo GatewayPrincipal với permissions hiện tại và sid; ký context gửi service đích.
6. Service kiểm tra chữ ký context, permission, tenant scope và điều kiện nghiệp vụ.
   IAM đối chiếu thêm user/session/membership/quyền trong DB của mình.

## Cache

| Key logic | Dữ liệu |
| --- | --- |
| auth:user:{userId} | status, systemRole, tokenVersion |
| auth:session:{sid} | status, userId, tokenVersion, expiresAt |
| auth:tenant:{tenantId} | status |
| auth:role:{tenantId}:{roleId} | status, roleKey, permissions |

Key thực có AUTH_CACHE_PREFIX theo môi trường. Session có TTL tuyệt đối theo hạn
refresh. User/tenant/role giữ revision và dùng freshness riêng; không xóa revision
sớm để tránh relay cũ ghi đè trạng thái mới.

Cache miss/quá freshness gọi GET /internal/authorization của IAM với context kỹ
thuật được ký. Projection trong authorization_outbox là nguồn tải lại sau khi cache
mất. Lua ghi theo revision; lỗi cache/loader chặn truy cập. Gateway không đọc IAM DB.

## Logout

IAM commit thu hồi sid và projection trước, rồi đẩy REVOKED sang Redis đồng bộ.
Chỉ trả 204 sau Redis xác nhận. Lỗi sau commit trả 503; retry logout sẽ phát lại
projection mà không nhân event. Thiết bị có sid khác vẫn hoạt động. Các thay đổi
user/tenant/role khác dùng outbox và freshness; xem tài liệu chính sách.

Downstream audit/subscription không gọi IAM cho mỗi request đã có context hợp lệ.
Các service được bảo vệ ở network và vẫn kiểm tra quyền/tenant tại endpoint/use case.
