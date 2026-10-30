# Common security

## JWT

`jwt/JwtTokenService` dùng để sinh access/refresh token bằng hai key HS256 khác nhau:

```yaml
# Cấu hình trong application.yml của service sử dụng lib.
vishop:
  security:
    jwt:
      access-token-secret: ${JWT_ACCESS_SECRET}
      refresh-token-secret: ${JWT_REFRESH_SECRET}
```

Khi có đủ hai cấu hình, auto-configuration tạo bean `JwtTokenService` để inject:

```java
String access = tokens.generateAccessToken(accessClaims, Duration.ofMinutes(15));
String refresh = tokens.generateRefreshToken(refreshClaims, Duration.ofDays(7));

Claims accessClaimsVerified = tokens.verifyAccessToken(access);
Claims refreshClaimsVerified = tokens.verifyRefreshToken(refresh);
```

- Mỗi key tối thiểu 32 byte UTF-8 và hai key phải khác nhau.
- Service tự chọn claims và thời hạn; common không tự thêm issuer, audience, userId,
  tenantId, role, permissions hay token_use.
- Thời hạn tối thiểu một giây, tính ở độ chính xác giây của JWT. Common đặt `iat` và
  `exp` theo thời hạn truyền vào, ghi đè hai trường này nếu có trong map đầu vào.
- Giữ `jti` do service cung cấp hoặc tự tạo UUID nếu thiếu. Không sửa map đầu vào.
- Verify dùng đúng key tương ứng và bắt buộc có `iat`/`exp` hợp lệ; không thử key còn
  lại. Việc đối chiếu DB/cache hoặc kiểm tra claims nghiệp vụ nằm ở service.
- Không có cấu hình JWT thì không tự tạo token bean. `enabled=false` tắt toàn bộ
  auto-configuration này. IAM inject facade này qua auto-configuration.

`jwt/JwtTokenProvider` chỉ phụ trách thao tác JWT tổng quát:

- `encode(claims)`: tạo JWT và ký bằng key đã cấu hình.
- `verify(token)`: kiểm tra chữ ký và các ràng buộc chuẩn `exp`/`nbf` nếu có, trả claims.
- `decode(token)`: đọc claims sau khi verify; không cung cấp claims chưa xác minh.

Provider chỉ hỗ trợ HS256 với một secret cho mỗi instance. Gateway dùng provider
access; IAM dùng JwtTokenService quản lý hai provider riêng. Ứng dụng có thể tắt bean mặc định bằng
`vishop.security.jwt.enabled=false` hoặc exclude JwtAutoConfiguration.

Bean một-key `JwtTokenProvider` chỉ được tạo khi có `vishop.security.jwt.secret`.
Cấu hình hai-key ở trên không yêu cầu khai báo thêm thuộc tính này.

Thư viện không quy định issuer, audience, subject là UUID, thời hạn access/refresh,
tenant, role, permission hoặc tokenVersion. Việc bắt buộc có `exp`, `iat` hay claim
nghiệp vụ nào thuộc trách nhiệm service.

```java
String token = jwt.encode(claimsCreatedByService);
Claims claims = jwt.verify(token);
// Service kiểm tra hợp đồng claims của mình sau bước xác minh chữ ký.
```

Các overload generateAccessToken/generateRefreshToken nhận Instant cho phép giữ
deadline tuyệt đối của phiên khi chọn tenant. Chúng không kéo dài hạn refresh.
IAM sở hữu sid, tokenVersion và kiểm tra session; thư viện JWT chung vẫn độc lập
với nghiệp vụ và không tự đọc DB. Xem [luồng auth](../../docs/core-architecture/authentication-flow.md).

## Principal và filter

`gateway/GatewayPrincipal` là principal của giao thức context nội bộ, độc lập với JWT.
Backend nhận nó sau khi xác thực context do gateway ký.

Nếu dùng filter JWT tổng quát `InternalHeaderAuthFilter`, service phải cung cấp
`JwtAuthenticationConverter` để kiểm tra claims và tạo `Authentication`. Thư viện
không tự suy ra quyền từ các claim cố định của IAM. Khi không có converter, cấu hình
mặc định không cài filter JWT và không tự xác thực request được bảo vệ.
