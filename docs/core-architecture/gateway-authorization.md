# Gateway authentication và phân quyền Redis

Xem [luồng auth chi tiết và sơ đồ](authentication-flow.md) cùng
[chính sách thu hồi](authorization-policy.md).

Gateway verify access JWT HS256 bằng access key và hợp đồng claims IAM:
issuer, audience, token_use, thời gian, userId, sid, tokenVersion và role theo tenant.
Permissions lấy từ Redis, không nằm trong JWT.

Gateway chỉ cấu hình access key, không giữ refresh key. Chỉ POST /api/v1/auth/refresh
và POST /api/v1/auth/logout trên route IAM nhận refresh cookie. Access JWT gốc và
Authorization không được chuyển xuống service; user/tenant/role/permissions/sid đi
trong header X-Gateway-Context đã được gateway ký. Refresh không phụ thuộc access
còn hạn; logout có thể dùng refresh khi access thiếu/hết hạn. Chi tiết và bảng
forwarding nằm trong [luồng auth](authentication-flow.md#gateway-chuyển-tiếp-credential-và-context).

## Thứ tự xác minh

Code chia theo trách nhiệm:

| Component | Trách nhiệm |
| --- | --- |
| AuthenticationGlobalFilter | Điều phối policy, xác thực và chuyển tiếp |
| GatewayRequestPolicy | Origin/CSRF, route public, API chưa chọn tenant và quy tắc forward refresh |
| GatewayRequestForwarder | Đọc access, bỏ header giả, lọc cookie và ký context gửi service |
| AuthenticationErrorResponse | Ghi lỗi từ chối xác thực theo Problem Details |

LocalAuthenticationVerifier tiếp tục verify JWT và đọc trạng thái/quyền từ Redis.
Lỗi từ service phía sau đi tới GatewayExceptionHandler, không bị bắt thành lỗi token.

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

Key logic do AuthorizationCacheKeys tạo. IAM/gateway dùng CacheKeyBuilder từ common-cache
để thêm prefix chung, cấu hình qua vishop.cache.key-prefix hoặc CACHE_KEY_PREFIX.
AUTH_CACHE_PREFIX cũ vẫn là giá trị dự phòng. AuthorizationSnapshot chỉ chứa dữ liệu.
Session có TTL tuyệt đối theo hạn
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
