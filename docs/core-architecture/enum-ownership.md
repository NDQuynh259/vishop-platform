# Phạm vi sở hữu enum

Enum dùng chung khi các bên cùng sử dụng một khái niệm, tập giá trị và ý nghĩa chuyển trạng thái.
Số service sử dụng là dấu hiệu để xem xét, không phải điều kiện duy nhất. Cùng có `ACTIVE`,
`PENDING` hay `FAILED` không làm hai vòng đời nghiệp vụ trở thành một.

## Quy tắc tổ chức

- Enum nghiệp vụ chỉ dùng trong một service nằm trong module sở hữu, thường ở `domain/valueobject`.
- Enum trong hợp đồng giữa các service nằm trong thư viện common tương ứng với hợp đồng đó.
- Enum của một thư viện hạ tầng nằm cạnh thành phần sở hữu nó, kể cả khi chưa có nhiều service
  sử dụng thư viện. Không chuyển mọi enum dùng chung vào `common-domain`.
- Không import enum từ implementation của service khác. Dùng hợp đồng chung hoặc ánh xạ tại
  boundary khi hai bên cần mô hình riêng.
- JPA dùng `@Enumerated(EnumType.STRING)` cho enum lưu dưới dạng tên. Native SQL và migration
  tiếp tục dùng giá trị chuỗi theo schema; JPQL và Criteria dùng kiểu enum.

## Kết quả rà soát

Rà soát mã production của cả bốn service và các thư viện common. Không có import trực tiếp
giữa các service. Phạm vi sử dụng phải tính cả JSON/cache và quyết định phân quyền, không chỉ
đếm import Java. `UserStatus`, `TenantStatus` và `MembershipStatus` được chuyển sang
`common-domain/constants` để thống nhất từ vựng identity/tenant dùng trong phân quyền.

| Enum | Phạm vi sử dụng / sở hữu | Vị trí |
| --- | --- | --- |
| `SystemRole` | IAM, gateway, audit, subscription và thư viện security | Giữ `common-domain/constants` |
| `AuthorizationStatus` | Hợp đồng snapshot IAM–gateway; IAM tạo JSON từ outbox SQL | Giữ `common-security/gateway` |
| `OutboxStatus` | Outbox dùng chung của IAM và subscription | Thêm trong `common-persistence`, thay trường trạng thái chuỗi của `OutboxEventEntity` |
| `SagaStatus` | Vòng đời của framework saga | Giữ `common-saga` |
| `UserStatus` | IAM sở hữu vòng đời; gateway tiêu thụ trạng thái qua snapshot | `common-domain/constants` |
| `TenantStatus` | IAM sở hữu vòng đời; gateway tiêu thụ trạng thái qua snapshot | `common-domain/constants` |
| `MembershipStatus` | Từ vựng membership dùng trong cấp/thu hồi quyền; gateway nhận hiệu lực qua tokenVersion | `common-domain/constants` |
| `InvitationStatus` | Vòng đời lời mời trong IAM | Giữ module tenant |
| `TenantApi.Status` | Hợp đồng API giữa các module trong IAM | Giữ API của module tenant; ánh xạ với `TenantStatus` tại boundary |
| `BillingCycle` | Subscription | Giữ module subscription |
| `OrderStatus`, `PaymentStatus`, `FulfillmentStatus` | Vòng đời đơn mua subscription | Giữ module subscription |
| `PurchaseOrder.State` | Trạng thái HTTP tương thích, suy ra từ trạng thái đơn và fulfillment | Giữ cạnh `PurchaseOrder` |

`AuthorizationStatus` là từ vựng chung của snapshot user/tenant/role/session; nó không thay thế
`UserStatus`, `TenantStatus`, `MembershipStatus` dùng trong domain và JPA. Snapshot hiện gộp nhiều
loại bản ghi vào cùng một trường `status`, nên vẫn cần enum projection này để đọc JSON/cache.
Gateway không đọc membership snapshot riêng: V11 đã bỏ projection đó và giữ cơ chế thu hồi
credential khi membership thay đổi. Vì vậy không thêm một bước kiểm tra membership vào gateway
chỉ để tạo tham chiếu Java tới enum dùng chung.
`PaymentStatus` mô tả mức thanh toán của đơn (`UNPAID`, `PARTIAL`, `PAID`, `OVERPAID`), khác trạng thái
giao dịch ngân hàng (`ALLOCATED`, `REVIEW`). Không gộp hai khái niệm này.

## Các trường chuỗi còn lại

Trong subscription, `PaymentTransactionJpaEntity.status` và `SePayInboxJpaEntity.status` vẫn là
chuỗi. Nếu chuyển sang enum, các kiểu này thuộc module payment của subscription; hiện chưa có
service khác dùng chung. Không đưa chúng vào common chỉ vì trùng tên hoặc giá trị với enum khác.

DTO, dữ liệu từ nhà cung cấp và tham số native SQL có thể dùng chuỗi tại boundary; chúng không
phải bằng chứng rằng enum nghiệp vụ cần chuyển sang common. Các mã role tenant và permission
có thể được tạo động nên không mô hình hóa thành enum cố định.
