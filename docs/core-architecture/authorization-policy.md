# Chính sách thay đổi quyền và thu hồi credential

Trạng thái: **đã triển khai trong code**. Hợp đồng runtime được ghi trong
[gateway-authorization.md](gateway-authorization.md). Gateway dùng ba nhóm cache;
migration V11 ngừng tạo/phát member projection, giữ thu hồi tokenVersion của V10.
Migration chưa tự được áp dụng lên database đang dùng; cần chạy quy trình triển khai.

## Hành vi

| Thay đổi | Hành vi |
|---|---|
| Đổi role, khóa/xóa membership | Bắt user đăng nhập lại |
| Khóa tài khoản | Chặn truy cập và đăng nhập đến khi mở khóa |
| Đổi mật khẩu | Bắt đăng nhập lại |
| Sửa permissions của role | Dùng quyền mới từ Redis, không đăng xuất |

Thu hồi cả access và refresh chính là bắt đăng nhập lại. Một tokenVersion thuộc toàn
user nên thay đổi membership ở một tenant thu hồi credential của user trên mọi tenant
và thiết bị. Mở khóa tài khoản không làm credential đã thu hồi sống lại; cần đăng nhập mới.

Không thêm authorizationVersion, membershipVersion hoặc roleVersion vào JWT. Phương án
giữ refresh để tự cập nhật role sau thay đổi membership không còn được chọn.

## Một tokenVersion toàn user

Nguồn thật là users.token_version. Access và refresh JWT đều chứa giá trị này khi phát hành.

```text
Đổi role/status hoặc xóa membership
  → tăng tokenVersion cùng transaction nghiệp vụ
  → authorization outbox cập nhật user snapshot trong Redis
  → access cũ bị gateway từ chối
  → refresh cũ bị IAM từ chối
  → user đăng nhập lại
```

V10 đã tăng tokenVersion khi đổi tenant/user/role/status/deletedAt hoặc xóa membership.
Giữ V10; không sửa checksum migration đã phát hành. Đổi mật khẩu/status/systemRole của
user cũng tăng tokenVersion theo cơ chế hiện có. Profile và lastTenantId không làm
credential mất hiệu lực. Tạo membership mới không cần thu hồi credential cũ nếu không
thay đổi membership hiện có; muốn chọn tenant mới phải qua IAM kiểm tra grants.

Nếu giữ tokenVersion hiện tại, không được tự hạ version hoặc tái sử dụng giá trị cũ.
Counter snapshot outbox/Redis vẫn cần để chống ghi lùi; đây là cơ chế đồng bộ cache,
không phải một version credential bổ sung trong JWT.

## Access và refresh JWT

Access JWT có metadata chuẩn iss, aud, sub, iat, exp, jti, token_use và các trường:

```json
{
  "sub": "user-id",
  "tenantId": "tenant-id",
  "tenantRoleId": "role-id",
  "systemRole": "USER",
  "tenantRole": "WAREHOUSE",
  "tokenVersion": 7
}
```

JWT dùng tenantRoleId để chỉ rõ role của tenant được chọn. Chưa chọn tenant thì
tenantId, tenantRoleId và tenantRole không có giá trị. tenantRole là built-in key hoặc
CUSTOM:{roleId}, không phải tên hiển thị. systemRole vẫn dùng enum USER/PLATFORM_ADMIN,
không có systemRoleId. Không chứa permissions.

IamTokenClaims.AccessToken dùng SystemRole enum trong Java; khi ghi JWT thì serialize thành
chuỗi tên enum. tokenVersion vẫn là metadata bắt buộc để thu hồi access và refresh.
Tên roleId trong API quản lý role, payload cache và context gateway không thay đổi.

File application/dto/IamTokenClaims.java chứa hai record AccessToken và RefreshToken,
không phụ thuộc JWT/persistence. IamTokenClaimsParser trong infrastructure kiểm tra
chữ ký và metadata dùng chung. Access có thông tin role; Refresh chỉ có userId,
tenantId, tokenVersion và expiresAt. Không dùng access thay cho refresh hoặc ngược lại.

TokenPort.verifyAccessToken ở IAM xác minh chữ ký/cấu trúc rồi kiểm tra DB trong cùng
snapshot transaction: user ACTIVE/chưa xóa, tokenVersion và systemRole khớp; tenant,
membership, role còn hiệu lực và tenantRoleId/tenantRole khớp dữ liệu hiện tại.
VerifyAccessTokenQuery/IdentityApi.verifyAccessToken dùng luồng này. Kiểm tra role đi
qua port của module tenant bằng query boolean, không tải permissions. Hàm trả DTO
claims, không dựng AuthenticatedUser. Parser JWT thuần vẫn không đọc DB; lỗi hạ tầng
không được đổi thành thông báo sai credential. Khi refresh, IAM kiểm tra user active
và tokenVersion hiện tại trước khi trả credential mới.

Gateway tiếp tục kiểm tra Redis để cấp signed context cho request thông thường;
không gọi lại IAM.verifyAccessToken trên mỗi request đến service khác.

Refresh JWT chỉ cần metadata chuẩn, userId trong sub, tenantId tùy chọn và tokenVersion;
không giữ role/permissions. IAM kiểm tra chữ ký, mục đích refresh, thời hạn, trạng thái
user và tokenVersion hiện tại trước khi phát token mới. Refresh không vượt qua bước
thu hồi: version cũ phải bị từ chối, không được đổi sang version mới rồi cấp credential.

IAM đọc user version, membership và role từ snapshot DB nhất quán khi phát access JWT.
Không ghép role cũ với tokenVersion mới bằng các lượt đọc không nhất quán. Cấu trúc claims
thuộc IAM; common-security/jwt chỉ phụ trách ký và xác minh JWT tổng quát.

## Sửa permissions không đăng xuất

```text
Sửa role_permissions
  → cập nhật role snapshot/outbox cùng transaction
  → relay cập nhật key role trong Redis
  → gateway dựng context với permissions mới
  → service kiểm tra quyền từ context đó
```

Không tăng tokenVersion của các thành viên. Không cập nhật từng user và không cần refresh
JWT chỉ để lấy permissions mới. Thêm quyền có thể làm UI cần tải lại thông tin quyền;
bỏ quyền làm thao tác kế tiếp trả 403 nếu user không còn được phép. Frontend không dùng
quyền lưu trong RAM để thay thế phân quyền server và không refresh lặp khi nhận 403.
Role bị xóa không được giữ key ACTIVE cũ; projection phải có tombstone/status không hoạt động.

## Ba nhóm cache gateway

| Key | Payload |
|---|---|
| vishop:dev:auth:user:{userId} | status, systemRole, tokenVersion |
| vishop:dev:auth:tenant:{tenantId} | status |
| vishop:dev:auth:role:{tenantId}:{roleId} | status, roleKey, permissions |

Production dùng vishop:prod:. IAM/gateway phải cùng prefix. Ví dụ có ngoặc nhọn để chỉ
chỗ thay ID, không yêu cầu ghi ngoặc nhọn vào key thực tế.

Luồng gateway:

1. Xác minh chữ ký, metadata và thời hạn access JWT.
2. Đọc user snapshot, kiểm tra ACTIVE và tokenVersion bằng JWT.
3. Nếu có tenant, kiểm tra tenant ACTIVE và lấy role bằng tenantId + tenantRoleId trong JWT.
4. Kiểm tra role còn hợp lệ, lấy permissions hiện tại và dựng context có chữ ký xuống service.
5. Service kiểm tra permission, tenant của tài nguyên và điều kiện nghiệp vụ.

Chỉ được bỏ lookup membership nếu mọi thay đổi membership làm mất hiệu lực tenantRoleId cũ
đều tăng tokenVersion cùng transaction. Điều kiện này phải áp dụng cả cập nhật ngoài
repository. Không được bỏ so version rồi tin tenantRoleId trong JWT một cách độc lập.

Nếu chuyển membership giữa user/tenant hoặc tái tạo sau xóa, phải bảo đảm credential cũ
không sống lại. Không xóa counter user cùng việc xóa membership. Khóa các user bị ảnh
hưởng theo thứ tự ổn định khi một mutation tác động nhiều user.

Gateway đọc và kiểm tra tenantRoleId trong JWT, không đọc member snapshot. V11 bỏ trigger
projection auth_member, giữ trigger thu hồi auth_member_token_version, và xóa member
snapshots cũ trong outbox. Endpoint cache loader không còn nhận key auth:member:*.
Các Redis hash member cũ cần được dọn theo đúng prefix sau khi nâng cấp tất cả gateway.

Nếu JWT mới hơn user cache, gateway xác nhận lại version với IAM trước khi cấp quyền.
Không coi version trong JWT là nguồn để tự cập nhật trạng thái user hoặc bỏ qua kiểm tra.

## Lỗi, refresh và độ trễ

- Access hết hạn thông thường: client có thể dùng refresh còn hợp lệ.
- Access có tokenVersion cũ: yêu cầu đăng nhập lại; client không lặp refresh.
- Refresh có tokenVersion cũ: từ chối và xóa cookie tại IAM khi xử lý phản hồi phù hợp.
- User không hoạt động: chặn cả truy cập lẫn login/refresh, báo rõ trạng thái tài khoản.
- Không đủ permission: 403; không đăng xuất và không tự lặp refresh.
- Redis/IAM cần thiết nhưng lỗi: 503; không fallback quyền JWT, không xóa refresh vì lỗi hạ tầng.

Giữ freshness cache, Lua chống ghi lùi, outbox retry và chính sách fail-closed hiện có.
Thu hồi có hiệu lực khi version mới được nhìn thấy; không cam kết tức thì lúc DB commit.
Request đang thực thi/context đã cấp không bị hủy ngược. Permissions mới cũng chịu độ
trễ đồng bộ này. Tác vụ nhạy cảm chạy trễ phải kiểm tra quyền lúc thực thi.

## Kiểm tra khi triển khai

- Đổi role/khóa/xóa membership: access và refresh cũ đều bị từ chối ở mọi tenant/thiết bị.
- Role A → B → A, xóa rồi thêm lại membership: credential cũ không sống lại.
- Đổi mật khẩu: credential cũ bị từ chối; khóa rồi mở user vẫn yêu cầu login mới.
- Sửa permissions: tokenVersion không đổi; request dùng quyền mới sau đồng bộ Redis.
- Không lẫn role cùng tên giữa các tenant; JWT thiếu/sai tenantRoleId không được cấp quyền tenant.
- Profile/lastTenantId không gây logout; không có token mang role cũ với user version mới.
- Rollback mutation/version/outbox, retry đảo thứ tự, cache miss và lỗi Redis/IAM.
- UI phân biệt 401 thu hồi phiên, 403 thiếu quyền và 503 hạ tầng.
