# Common security

## JWT

`jwt/JwtTokenProvider` chỉ phụ trách thao tác JWT tổng quát:

- `encode(claims)`: tạo JWT và ký bằng key đã cấu hình.
- `verify(token)`: kiểm tra chữ ký và các ràng buộc chuẩn `exp`/`nbf` nếu có, trả claims.
- `decode(token)`: đọc claims sau khi verify; không cung cấp claims chưa xác minh.

Thư viện không quy định issuer, audience, subject là UUID, thời hạn access/refresh,
tenant, role, permission hoặc tokenVersion. Việc bắt buộc có `exp`, `iat` hay claim
nghiệp vụ nào thuộc trách nhiệm service.

```java
String token = jwt.encode(claimsCreatedByService);
Claims claims = jwt.verify(token);
// Service kiểm tra hợp đồng claims của mình sau bước xác minh chữ ký.
```

IAM quản lý payload phát hành trong `IamJwtTokenFactory` và kiểm tra access claims
trong `IamAccessTokenClaims`; refresh contract nằm trong `JwtTokenAdapter`.
Gateway có hợp đồng nhận credential IAM riêng và lấy quyền hiện tại từ Redis.

## Principal và filter

`gateway/GatewayPrincipal` là principal của giao thức context nội bộ, độc lập với JWT.
Backend nhận nó sau khi xác thực context do gateway ký.

Nếu dùng filter JWT tổng quát `InternalHeaderAuthFilter`, service phải cung cấp
`JwtAuthenticationConverter` để kiểm tra claims và tạo `Authentication`. Thư viện
không tự suy ra quyền từ các claim cố định của IAM. Khi không có converter, cấu hình
mặc định không cài filter JWT và không tự xác thực request được bảo vệ.
