# IAM authentication

Luồng xác thực đã được triển khai lại. Xem [luồng auth hiện tại](authentication-flow.md)
để đọc sơ đồ, API, claims, session, Redis, cấu hình và migration.

Bốn endpoint register/login/refresh/logout đã hoạt động; các API nghiệp vụ nhận
context gateway và kiểm tra quyền. Logout thu hồi riêng sid hiện tại, còn thay đổi
bảo mật toàn user thu hồi mọi phiên qua tokenVersion.

Áp dụng migration V12 trước khi khởi chạy. Tài liệu này giữ lại đường dẫn của giai
đoạn reset trước đây; trạng thái 501/503 tạm thời của giai đoạn đó đã kết thúc.
