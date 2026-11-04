# Chính sách truy cập Gateway

Gateway quyết định request có được chuyển tiếp theo chính sách xác thực/ủy quyền
hiện hành; backend vẫn kiểm tra quyền và điều kiện nghiệp vụ của mình.

- Xác thực access JWT và trạng thái user/session/tenant/role trong projection Redis.
- Chỉ các đường public được policy cho phép mới bỏ qua access authentication.
- Chính sách Origin và CSRF áp dụng theo loại request; webhook SePay có ngoại lệ riêng.
- Refresh cookie chỉ được chuyển tới endpoint IAM mà policy cho phép.
- Context nội bộ được ký theo backend đích; không tin các header danh tính tự gửi từ client.
- Quyền chọn tenant và system role không thay thế kiểm tra sở hữu dữ liệu tại service đích.

Nguồn kiểm chứng: [GatewayRequestPolicy](../src/main/java/com/vishop/gateway/security/GatewayRequestPolicy.java)
và [Gateway authorization](../../../docs/core-architecture/gateway-authorization.md).
