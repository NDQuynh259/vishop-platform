# Thiết kế IAM

Runtime tổ chức nghiệp vụ trong các module identity và tenant; phần composition nối
các contract công khai của module. Domain không phụ thuộc entity/repository của module khác.

```mermaid
flowchart LR
    Client --> Gateway
    Gateway --> REST[IAM REST]
    REST --> CQRS[Command và Query]
    CQRS --> DB[(IAM PostgreSQL)]
    DB --> Projection[Authorization projection]
    Projection --> Redis[(Auth Redis)]
    Redis --> Gateway
    DB --> Outbox[Business outbox relay]
    Outbox --> Kafka
```

API đăng nhập kiểm tra credential; API nghiệp vụ kiểm tra context nội bộ và trạng thái
hiện hành theo thiết kế IAM. PostgreSQL là nguồn dữ kiện bền vững; Redis là projection
ủy quyền. Thay đổi DB và outbox cùng transaction; không phát dữ liệu chưa commit.

Không xác nhận được authorization projection sau commit thì ném `ServiceUnavailableException`
dùng chung, giữ nguyên cause để log và trả 503 qua `GlobalExceptionHandler` của `common-web`.
Lỗi tài nguyên DB được `DatabaseExceptionHandler` chung trả 503; không chuyển lỗi hạ tầng thành
401 và không tự dựng Problem Details trong controller/advice riêng của IAM. Exception không
chỉ định thời gian phục hồi thì response không tự thêm `Retry-After`.

Xem [README service](../README.md), [Gateway authorization](../../../docs/core-architecture/gateway-authorization.md)
và [Authentication flow](../../../docs/core-architecture/authentication-flow.md) cho contract chi tiết.
