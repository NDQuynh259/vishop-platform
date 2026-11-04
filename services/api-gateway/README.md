# API Gateway

Gateway là cổng HTTP của platform, cổng mặc định `8000`; định tuyến IAM, Audit và
Subscription. Service xác thực access JWT, đọc projection quyền từ Auth Redis và ký
context riêng cho backend đích.

Tài liệu riêng: [docs/README.md](docs/README.md).

```powershell
.\gradlew.bat :services:api-gateway:dev
.\gradlew.bat :services:api-gateway:test
```

Cấu hình route, Redis và biến môi trường:
[application.yml](src/main/resources/application.yml).
Contract quyền: [Gateway authorization](../../docs/core-architecture/gateway-authorization.md).
Gateway không có PostgreSQL hoặc lệnh migrate.
