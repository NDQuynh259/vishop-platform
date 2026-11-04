# Dữ liệu API Gateway

Gateway không sở hữu database PostgreSQL, entity nghiệp vụ hay migration Flyway.
Không thêm bảng user, tenant hoặc session riêng vào Gateway.

Service đọc Auth Redis để kiểm tra trạng thái và quyền hiện hành. IAM sở hữu dữ kiện
gốc và cơ chế cập nhật projection; Gateway không trở thành nguồn dữ liệu quyền thứ hai.

| Dữ liệu | Nguồn sở hữu |
| --- | --- |
| User và token version | IAM |
| Phiên theo sid | IAM |
| Tenant, membership và role grants | IAM |
| Projection/cache quyền | IAM cập nhật, Gateway đọc qua Auth Redis |
| Route và khóa ký context backend | Cấu hình môi trường |

Redis host/port/database và cache prefix nằm trong
[application.yml](../src/main/resources/application.yml).
Định dạng và độ mới của projection: [Gateway authorization](../../../docs/core-architecture/gateway-authorization.md).
