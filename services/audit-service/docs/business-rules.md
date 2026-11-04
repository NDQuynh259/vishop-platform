# Nghiệp vụ Audit

Audit lưu các sự kiện đã phát sinh để truy vết, không thực hiện lại nghiệp vụ nguồn.

- Nhật ký bất biến theo luồng ghi hiện tại; không cung cấp API sửa nội dung lịch sử.
- Một event nhận lại trong cùng consumer group không tạo bản ghi lần hai.
- Ghi audit và đánh dấu consumed message trong cùng transaction.
- Truy vấn tenant chỉ trả dữ liệu tenant được phép truy cập; event toàn cục có
  `tenant_id=NULL` không xuất hiện trong truy vấn tenant.
- CMS có truy vấn riêng, yêu cầu quyền quản trị phù hợp, có thể xem event toàn cục.
- Trang kết quả giới hạn 1–100 bản ghi, thứ tự `occurredAt` và ID giảm dần.

Luồng và API hiện hành: [README service](../README.md).
