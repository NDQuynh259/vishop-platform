# Tài liệu theo service

Tài liệu riêng của service nằm tại `services/<service>/docs/`. Thư mục này là mục lục
chung, giúp tìm đúng nơi sở hữu nghiệp vụ và thiết kế.

| Service | Phạm vi |
| --- | --- |
| [IAM](../../services/iam-service/docs/README.md) | Tài khoản, tenant, thành viên, phân quyền và phiên đăng nhập |
| [API Gateway](../../services/api-gateway/docs/README.md) | Định tuyến, xác thực và context nội bộ |
| [Audit](../../services/audit-service/docs/README.md) | Nhật ký sự kiện và truy vấn phục vụ truy vết |
| [Subscription](../../services/subscription-service/docs/README.md) | Gói cước, đơn mua và thanh toán |
| [Notification](../../services/notification-service/docs/README.md) | Cấu hình kênh, người nhận và thông báo |

## Quy ước lưu trữ

Mỗi service có cấu trúc:

```text
services/<service>/
├── README.md                  # Giới thiệu, cấu hình và lệnh chạy
└── docs/
    ├── README.md              # Mục lục tài liệu service
    ├── business-rules.md       # Phạm vi, luồng nghiệp vụ và quy tắc
    ├── architecture.md         # Module, luồng xử lý, tích hợp và giới hạn
    └── database-design.md      # Quyền sở hữu dữ liệu, ERD, khóa và migration
```

- Tài liệu mô tả nghiệp vụ một service được bổ sung/cập nhật tại service đó.
- Thiết kế bổ sung có thể đặt cạnh các file trên; cập nhật mục lục khi thêm tài liệu.
- [Kiến trúc dùng chung](../core-architecture/README.md) tiếp tục chứa các chuẩn liên service.
  [Tài liệu sản phẩm](../product/vishop-function-list.md) giữ phạm vi và yêu cầu toàn sản phẩm.
- Link tới tài liệu đã có thay vì sao chép toàn bộ nội dung sang nhiều nơi. Tài liệu tập
  trung cũ vẫn được giữ để bảo toàn liên kết; khi chuyển nội dung phải cập nhật link nguồn.
- Phân biệt rõ đã triển khai, thiết kế dự kiến và phần còn thiếu. Migration SQL là nguồn
  kiểm chứng schema; ERD phải theo phiên bản migration mới nhất.
- Khi thay đổi nghiệp vụ/schema/contract, cập nhật tài liệu service liên quan trong cùng
  thay đổi. Giữ bản thiết kế cũ có nhãn lịch sử nếu cần đối chiếu.
