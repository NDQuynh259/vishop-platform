# ViShop — Kế hoạch thực thi cho đội phát triển

**Đối tượng sử dụng:** đội phát triển ViShop. Đây là tài liệu theo dõi công việc hằng tuần, không phải tài liệu đặc tả.
**Nguồn phạm vi:** [vishop-function-list.md](./vishop-function-list.md) (241 hạng mục, 16 module — 236 trong phạm vi Phase 1) và [vishop-sprint-plan.md](./vishop-sprint-plan.md).
**Ngày lập:** 07/08/2026.

Cách dùng: mỗi bước là một đơn vị bàn giao có đầu ra kiểm chứng được. Một bước chỉ được đóng khi đầu ra ở cột "Điều kiện đóng bước" đã có người khác xác nhận, không phải khi người làm tự thấy xong.

---

## 1. Nhân sự thực tế và hệ quả lên lịch

|                               | Kế hoạch gốc giả định | Đội thực tế           |
| ----------------------------- | --------------------- | --------------------- |
| Backend                       | 4                     | 3 — Hiệp, Quỳnh, Hùng |
| Frontend                      | 3                     | 1 — Lợi               |
| QA                            | 2                     | 0                     |
| Tổng người-tuần trong 16 tuần | 144                   | 64                    |

Năng lực thực tế bằng 44% giả định lập kế hoạch. Ba hệ quả bắt buộc phải xử lý trước khi bắt đầu, không phải điều chỉnh giữa chừng:

**Một — không có QA độc lập.** Kiểm thử phải nằm trong định nghĩa hoàn thành của từng bước, do chính người viết code làm, và người khác trong đội đọc lại. Không có giai đoạn kiểm thử tách riêng ở cuối.

**Hai — một frontend gánh toàn bộ giao diện.** Frontend trở thành nút thắt từ tuần 5 trở đi, khi ba backend cùng sinh ra màn hình cần dựng. Bắt buộc phải chốt hợp đồng API trước khi frontend dựng màn, để Lợi làm được trên dữ liệu giả mà không chờ backend chạy xong.

**Ba — phạm vi 240 hạng mục trong 16 tuần không khả thi với đội này.** Đề xuất cắt xuống lõi vận hành cho mốc Alpha: giữ M00 đến M10, đẩy M11 (Affiliate và Livestream), M12 (Đối soát tài chính), M15 (Gói cước) sang giai đoạn sau. Phần cắt này là quyết định phạm vi, thuộc thẩm quyền Ban giám đốc — kế hoạch dưới đây trình bày cả hai phần, đánh dấu rõ phần đề xuất hoãn.

### Mốc thời gian trong tài liệu này khác với sprint plan

| Mốc                               | [vishop-sprint-plan.md](./vishop-sprint-plan.md) — giả định 9 người | Tài liệu này — đội 4 người                      |
| --------------------------------- | ------------------------------------------------------------------- | ----------------------------------------------- |
| Kết nối được gian hàng thật       | Cuối T4                                                             | Cuối T4                                         |
| Alpha — trọn vòng đời một package | Cuối T8                                                             | Cuối T10                                        |
| Beta — 5 khách chạy thật          | Cuối T12                                                            | Cuối T14                                        |
| Phát hành chính thức              | Cuối T16                                                            | Chưa xác định, phụ thuộc quyết định cắt phạm vi |

Sprint plan giữ nguyên làm bản ghi kế hoạch gốc. Đội bám theo mốc ở tài liệu này. Khi nhân sự thay đổi thì cập nhật tài liệu này trước, sprint plan sau.

---

## 2. Nguyên tắc phân công

Chia theo lát dọc, mỗi người sở hữu trọn một nhóm nghiệp vụ từ cơ sở dữ liệu lên tới API. Không chia theo tầng kỹ thuật.

| Người | Sở hữu                                                                                        | Lý do chia như vậy                                                                                                                              |
| ----- | --------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| Hiệp  | Nền tảng, tài khoản và phân quyền, kết nối gian hàng, ghép nối SKU, cảnh báo, gói cước        | Đây là các phần mọi module khác phụ thuộc ngược vào. Người làm nền phải là người chịu trách nhiệm khi nền sai.                                  |
| Quỳnh | Sản phẩm và SKU, tồn kho, chi phí và lợi nhuận, affiliate và livestream                       | Một chuỗi liên tục: giá vốn sinh ra ở sản phẩm, chảy qua tồn, kết thúc ở lợi nhuận. Cắt chuỗi này cho hai người là tạo ra tranh chấp công thức. |
| Hùng  | Đơn hàng và package, in tem và chứng từ, quét mã và kiểm hàng, sau bán và hoàn hàng, đối soát | Trọn vòng đời một đơn từ lúc về tới lúc đối soát xong tiền. Đây là chuỗi có nhiều trạng thái nhất, cần một người nắm hết.                       |
| Lợi   | Toàn bộ giao diện                                                                             | Chỉ có một frontend.                                                                                                                            |

Ba quy tắc bắt buộc:

- Không ai sửa code trong lát của người khác. Cần thay đổi thì mở yêu cầu cho người sở hữu.
- Mọi thứ dùng chung nằm trong lát của Hiệp. Người khác chỉ tiêu thụ, không mở rộng.
- Nghiệp vụ không được gọi thẳng thư viện của TikTok. Mọi lần gọi ra sàn đi qua lớp adapter do Hiệp dựng.

---

## 3. Uỷ quyền — một chiều, một loại

> **Mục này đã được viết lại ngày 07/08/2026.** Bản trước mô tả ba vai trò uỷ quyền (Seller / Creator / Partner-TAP). Mô hình đó không có trong tài liệu chính thức của TikTok Shop. Ai đã đọc bản cũ thì bỏ hoàn toàn nội dung đó.

### ViShop là loại đối tác nào

Partner Center có sáu loại đối tác. Năm loại — TSP, CAP, TAP (US và ngoài US), MCN — là các ngành **dịch vụ**: nhận vận hành thuê, quản lý creator, môi giới creator ăn hoa hồng. Chúng không phải gói nâng cấp quyền đọc dữ liệu.

ViShop thuộc loại còn lại: **Developer**, nhánh **App Developer**, phát hành một **Public App**.

Việc phải làm một lần cho cả hệ thống: đăng ký tài khoản tại `partner.tiktokshop.com/account/sign-up`, chọn thị trường VN, lấy `app_key` và `app_secret`.

Việc khách hàng làm cho từng gian hàng: mở trang uỷ quyền của TikTok, bấm đồng ý. Hết.

### Không có bước chọn vai trò

Màn kết nối chỉ có ba bước: mở trang uỷ quyền → xác nhận phạm vi quyền → đồng bộ khởi tạo. Không có bước chọn vai trò, vì phía TikTok không có khái niệm đó cho một Public App.

Toàn bộ dữ liệu ViShop cần đều nằm trong phạm vi gian hàng tự cấp:

| Nhóm quyền     | Scope                                                                                   | Mở khoá phần nào                                                    |
| -------------- | --------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| Đơn & sản phẩm | `seller.product.info`, `seller.order.info`, `seller.fulfillment`, `seller.finance.info` | Sản phẩm, tồn, đơn, package, tem, tiền về                           |
| Affiliate      | `seller.affiliate_collaboration.read`                                                   | Nguồn đơn, creator gắn với đơn, tỉ lệ hoa hồng, hoàn theo dòng hàng |
| Phân tích LIVE | `data.shop_analytics.public.read`                                                       | Phiên live của official account thuộc gian hàng                     |

Schema đầy đủ và cách kiểm chứng: [vishop-capability-matrix.md](./vishop-capability-matrix.md) mục 4.

### Ba điểm phải nắm trước khi dựng màn kết nối

**Gate theo scope, không gate theo vai trò.** Lý do thật khiến một màn không có dữ liệu là gian hàng uỷ quyền từ bản app cũ chưa có scope đó. Cách xử lý là mời seller uỷ quyền lại, không phải mời họ "kết nối thêm một vai trò".

**Nhóm phân tích creator (M11-B) không thiếu quyền — nó chờ Legal.** Dữ liệu đã có sẵn. Chốt chặn là ToS Partner Center §3.8 về lưu trữ và dùng lại dữ liệu creator, và **khả năng Legal không chấp nhận là có thật**. Không xây thêm tầng lưu trữ hồ sơ creator trước khi có văn bản. Nếu Legal trả lời không thì bỏ hẳn nhóm này.

**Tính năng thiếu quyền phải khoá kèm lý do, không được ẩn.** Màn hình cần quyền chưa có thì hiển thị trạng thái khoá, nói rõ thiếu scope nào và bấm vào đâu để uỷ quyền lại. Không ẩn menu, không hiện bảng rỗng, không hiện số 0.

---

## 4. Giai đoạn 0 — Dựng khung (T1–T2)

Trong hai tuần này Hiệp làm một mình phần khung. Quỳnh và Hùng làm song song phần không cần khung chạy được.

| Bước | Tuần  | Người | Nội dung                                                                                              | Điều kiện đóng bước                                                                      |
| ---- | ----- | ----- | ----------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| 0.1  | T1    | Hiệp  | Khởi tạo workspace, cấu hình build, biến môi trường, kết nối cơ sở dữ liệu, khung migration           | Đồng đội clone về chạy được lệnh khởi động và lệnh migration, không cần hỏi thêm         |
| 0.2  | T1    | Hiệp  | Cách ly nhiều khách hàng: mọi bảng có cột định danh khách hàng, tầng chặn ép điều kiện ở mọi truy vấn | Có bài test chứng minh truy vấn của khách hàng A không đọc được dữ liệu của khách hàng B |
| 0.3  | T1    | Hiệp  | Chuẩn phản hồi API: cấu trúc danh sách, phân trang theo con trỏ, định dạng lỗi, mã lỗi                | Tài liệu hợp đồng API cập nhật xong, Lợi đọc và xác nhận dựng được màn trên đó           |
| 0.4  | T2    | Hiệp  | Hàng đợi công việc, hộp thư đi, cơ chế chống xử lý trùng                                              | Có bài test chạy lại cùng một công việc hai lần cho ra đúng một kết quả                  |
| 0.5  | T2    | Hiệp  | Khung adapter sàn, khung ghi nhật ký và giám sát                                                      | Gọi thử một endpoint bất kỳ của TikTok qua adapter, log ra đủ thông tin truy vết         |
| 0.6  | T2    | Hiệp  | Tích hợp liên tục: chạy test và kiểm kiểu tự động trên mọi thay đổi                                   | Một thay đổi làm hỏng test bị chặn tự động, không vào được nhánh chính                   |
| 0.7  | T1–T2 | Quỳnh | Mô hình dữ liệu sản phẩm, SKU, giá vốn, tồn — bảng, ràng buộc, chỉ mục                                | Sơ đồ quan hệ được Hiệp và Hùng đọc duyệt, không còn tranh chấp về khoá                  |
| 0.8  | T1–T2 | Hùng  | Mô hình dữ liệu đơn, package, máy trạng thái package — liệt kê đủ trạng thái và lối chuyển hợp lệ     | Bảng chuyển trạng thái được duyệt, mọi lối chuyển không hợp lệ đều bị chặn ở tầng miền   |
| 0.9  | T1–T2 | Lợi   | Dựng khung giao diện: bố cục, điều hướng, hệ màu, bộ thành phần dùng chung                            | Giao diện chạy trên dữ liệu giả, không phụ thuộc backend                                 |

**Không được làm trong giai đoạn 0:** gọi API thật của TikTok, viết logic đồng bộ, dựng màn hình nghiệp vụ. Nền chưa xong mà chạy trước thì phải viết lại.

---

## 5. Giai đoạn 1 — Kết nối và dữ liệu gốc (T3–T4)

| Bước | Tuần  | Người | Nội dung                                                                             | Điều kiện đóng bước                                                                         |
| ---- | ----- | ----- | ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------- |
| 1.1  | T3    | Hiệp  | Đăng nhập, phiên làm việc, phân quyền theo vai trò                                   | Ma trận quyền có test phủ đủ, vai trò thấp không gọi được API của vai trò cao               |
| 1.2  | T3    | Hiệp  | Uỷ quyền TikTok: nhận mã, đổi lấy token, lưu mã hoá, tự làm mới token                | Kết nối một gian hàng thật, token tự làm mới sau khi hết hạn mà không cần thao tác tay      |
| 1.3  | T3–T4 | Hiệp  | Bộ nhận webhook từ TikTok: xác thực chữ ký, chống nhận trùng, hàng đợi chờ xử lý lại | Gửi lại cùng một sự kiện ba lần chỉ sinh một lần xử lý; sự kiện lỗi vào hàng chờ, không mất |
| 1.4  | T4    | Hiệp  | Bộ điều tiết hạn mức API theo từng gian hàng, có thứ tự ưu tiên luồng                | Chạy đồng thời lấy tem hàng loạt và đồng bộ đơn, đồng bộ đơn không bị đói hạn mức           |
| 1.5  | T3–T4 | Quỳnh | Đồng bộ sản phẩm và SKU từ sàn về, xử lý biến thể và sản phẩm gộp                    | Đồng bộ một gian hàng thật, số lượng SKU khớp với Seller Center                             |
| 1.6  | T4    | Hiệp  | Ghép nối SKU sàn với SKU nội bộ: ghép tay và ghép hàng loạt                          | Ghép được toàn bộ SKU của một gian hàng thật, có nhật ký ghi rõ dòng nào ghép bằng cách nào |
| 1.7  | T3–T4 | Hùng  | Đồng bộ đơn hàng từ sàn về, tách package, dựng máy trạng thái                        | Đơn thật hiện đúng trạng thái, đối chiếu tay với Seller Center không lệch                   |
| 1.8  | T3–T4 | Lợi   | Màn kết nối gian hàng, danh sách sản phẩm, màn ghép nối, danh sách đơn               | Bốn màn chạy trên API thật, không còn dữ liệu giả                                           |

**Mốc kiểm tra cuối T4:** kết nối một gian hàng thật, thấy sản phẩm thật, ghép nối được SKU, thấy đơn thật. Chưa xong mốc này thì không mở giai đoạn 2.

---

## 6. Giai đoạn 2 — Vòng đời đơn hàng (T5–T10)

Đây là giai đoạn nặng nhất. Ba backend chạy song song, phụ thuộc chéo được kiểm soát bằng hợp đồng API chốt trước.

| Bước | Tuần   | Người | Nội dung                                                                                     | Điều kiện đóng bước                                                                                          |
| ---- | ------ | ----- | -------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| 2.1  | T5–T6  | Quỳnh | Tồn kho nhiều kho: kho, vị trí kệ, tồn theo kho, chuyển kho                                  | Nhập kho và chuyển kho một lô hàng thật, số tồn khớp ở cả hai đầu                                            |
| 2.2  | T6–T7  | Quỳnh | Công thức tồn khả dụng, tồn an toàn, tồn neo theo package                                    | Có bài test cho từng công thức, kể cả trường hợp tồn âm ảo                                                   |
| 2.3  | T7     | Quỳnh | Đẩy tồn tự động theo biến động, gộp theo cửa sổ thời gian, đẩy theo lô                       | Đẩy tồn cho ba gian hàng, độ trễ đo ở mức 95% dưới 30 giây                                                   |
| 2.4  | T8     | Quỳnh | Đẩy tồn thủ công: một SKU, nhiều SKU, toàn gian hàng; nhật ký đẩy tồn                        | Đẩy lại toàn gian hàng sau khi sửa cấu hình, nhật ký ghi đủ kết quả từng dòng                                |
| 2.5  | T5–T6  | Hùng  | Chi tiết đơn, chi tiết package, bộ lọc và tìm kiếm                                           | Tìm được đơn theo mã đơn, mã vận đơn, số điện thoại; trường bị sàn che hiển thị đúng trạng thái che          |
| 2.6  | T7     | Hùng  | Sẵn sàng giao hàng theo lô, xử lý kết quả độc lập từng package                               | Một package lỗi trong lô không làm hỏng các package còn lại, lý do lỗi hiện rõ                               |
| 2.7  | T8     | Hùng  | Lấy tem và in tem, in trước ngay sau khi sẵn sàng giao hàng                                  | In được tem thật cho 100 package liên tiếp, không hỏng tem, không trùng                                      |
| 2.8  | T9     | Hùng  | Sóng nhặt hàng, phiếu nhặt sắp theo vị trí kệ                                                | Chạy một sóng nhặt thật, thứ tự phiếu đúng theo đường đi trong kho                                           |
| 2.9  | T9–T10 | Hùng  | Quét mã kiểm hàng, quét bàn giao, chế độ quét toàn màn hình                                  | Quét 100 kiện liên tiếp bằng máy quét, không sai, không phải chạm màn hình                                   |
| 2.10 | T5–T6  | Hiệp  | Quy tắc ghép nối tự động cho listing mới, kèm đường quét định kỳ không phụ thuộc webhook     | Đăng một listing mới trên Seller Center, hệ thống tự ghép trong vòng một chu kỳ quét, không cần thao tác tay |
| 2.11 | T6     | Hiệp  | Màn sức khoẻ gian hàng, đọc điểm hiệu suất từ sàn                                            | Điểm hiển thị khớp với Seller Center; sàn không trả dữ liệu thì hiện trạng thái thiếu, không hiện số 0       |
| 2.12 | T7–T8  | Hiệp  | Cảnh báo và thông báo, trung tâm cảnh báo                                                    | Cảnh báo hết hạn uỷ quyền và cảnh báo tồn thấp bắn đúng, không bắn trùng                                     |
| 2.13 | T9–T10 | Hiệp  | Chịu tải nền tảng dưới lưu lượng thật: đo hạn mức API, hàng đợi tồn đọng, thời gian phản hồi | Chạy tải mô phỏng ba gian hàng cùng lúc, không có công việc nào kẹt quá một chu kỳ xử lý                     |
| 2.14 | T9–T10 | Quỳnh | Tồn theo tốc độ bán, ngưỡng cảnh báo tự tính, chuẩn bị dữ liệu giá vốn cho giai đoạn 3       | Ngưỡng tự tính ra số hợp lý trên dữ liệu bán thật của ba gian hàng, không dùng ngưỡng cố định                |
| 2.15 | T5–T10 | Lợi   | Toàn bộ giao diện tồn kho, đơn hàng, package, in tem, quét mã, sức khoẻ gian hàng            | Từng màn chạy trên API thật ngay khi backend đóng bước tương ứng                                             |

**Mốc Alpha cuối T10:** chạy trọn vòng đời một package thật từ lúc đơn về tới lúc bàn giao đơn vị vận chuyển, trên gian hàng thật, không có thao tác tay ngoài hệ thống.

---

## 7. Giai đoạn 3 — Sau bán và lợi nhuận (T11–T14)

| Bước | Tuần    | Người | Nội dung                                                                                                 | Điều kiện đóng bước                                                                                                          |
| ---- | ------- | ----- | -------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| 3.1  | T11     | Hùng  | Đồng bộ đơn hoàn và yêu cầu huỷ từ sàn                                                                   | Đơn hoàn thật hiện đúng trạng thái và đúng lý do                                                                             |
| 3.2  | T11–T12 | Hùng  | Duyệt và từ chối yêu cầu huỷ, đồng hồ đếm ngược hạn 24 giờ                                               | Duyệt và từ chối ghi được sang sàn; đồng hồ tính từ mốc sàn báo, không phải mốc hệ thống nhận                                |
| 3.3  | T12     | Hùng  | Nhập kho hàng hoàn, phân loại tình trạng hàng                                                            | Hàng hoàn nhập kho làm tăng tồn khả dụng đúng số lượng                                                                       |
| 3.4  | T11–T12 | Quỳnh | Quản lý lô và hạn dùng, cảnh báo cận hạn                                                                 | Xuất hàng theo lô cũ trước, cảnh báo bắn đúng ngưỡng cấu hình                                                                |
| 3.5  | T13–T14 | Quỳnh | Chi phí và lợi nhuận: giá vốn đóng băng tại thời điểm xuất kho, dòng phí, lợi nhuận theo đơn và theo SKU | Đối chiếu tay lợi nhuận của 20 đơn thật, lệch bằng không; SKU thiếu giá vốn hiện trạng thái thiếu dữ liệu, không hiện số sai |
| 3.6  | T13–T14 | Hiệp  | Báo cáo và bảng điều khiển, màn việc hôm nay                                                             | Số trên bảng điều khiển khớp với số trên màn chi tiết tương ứng                                                              |
| 3.7  | T11–T14 | Lợi   | Giao diện sau bán, hạn dùng, lợi nhuận, báo cáo                                                          | Từng màn chạy trên API thật                                                                                                  |

**Mốc Beta cuối T14:** năm khách hàng chạy thật, đo tỉ lệ đơn xử lý trọn vẹn trong hệ thống.

---

## 8. Giai đoạn 4 — Phần đề xuất hoãn sang sau (T15 trở đi)

Phần dưới đây nằm ngoài lõi vận hành. Đề xuất chưa làm trong 16 tuần đầu với quy mô đội hiện tại.

| Nhóm                                    | Người dự kiến | Lý do hoãn được                                                                                                                                                                                                |
| --------------------------------------- | ------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Đối soát tài chính                      | Hùng          | Đối soát chạy sau khi tiền về, tức là sau khi hệ thống đã vận hành ít nhất một chu kỳ thanh toán. Làm sớm không có dữ liệu để kiểm chứng.                                                                      |
| Affiliate và livestream, phần công khai | Quỳnh         | Phụ thuộc dữ liệu hoa hồng từ đối soát. Không có đối soát thì không có gì để tính.                                                                                                                             |
| Affiliate, phần dữ liệu creator         | Chưa giao     | Dữ liệu đã có sẵn trong quyền gian hàng — hoãn không phải vì thiếu quyền đọc, mà vì chờ Legal xác nhận quyền **lưu trữ và dùng lại** theo ToS §3.8. Có thể bị bỏ hẳn. Không giao việc khi chưa có câu trả lời. |
| Gói cước và tính phí                    | Hiệp          | Cần thiết khi bán ra ngoài, không cần cho giai đoạn chạy thử với khách hàng chỉ định.                                                                                                                          |
| Trợ lý ngôn ngữ và tự động hoá          | Hiệp          | Chỉ đọc lại số liệu của các module trước. Bỏ hẳn khỏi giai đoạn một vẫn đủ điều kiện phát hành. Chi tiết ở [vishop-ai-automation-roadmap.md](./vishop-ai-automation-roadmap.md).                               |

---

## 9. Quy tắc đóng một bước

Một bước chỉ được đánh dấu hoàn thành khi đủ cả năm điều kiện:

1. Chức năng chạy đúng trên dữ liệu thật, không phải dữ liệu dựng sẵn.
2. Có test tự động cho phần logic nghiệp vụ và cho các trường hợp biên đã liệt kê trong đặc tả.
3. Có người thứ hai trong đội đọc lại thay đổi và xác nhận.
4. Tài liệu hợp đồng API đã cập nhật nếu bước này thêm hoặc đổi endpoint.
5. Lỗi phát sinh được ghi log đủ để truy vết, không nuốt lỗi im lặng.

Đội không có kiểm thử viên độc lập, nên điều kiện 2 và 3 là hàng rào duy nhất. Bỏ qua chúng nghĩa là lỗi sẽ được khách hàng phát hiện.

---

## 10. Quy tắc làm việc chung

**Hợp đồng API chốt trước khi dựng màn.** Backend đăng định nghĩa endpoint lên tài liệu hợp đồng trước khi viết code. Lợi dựng màn trên dữ liệu giả theo đúng định nghĩa đó. Backend đổi hợp đồng sau khi đã chốt thì phải báo trước, không đổi ngầm.

**Bí mật không nằm trong mã nguồn.** Không commit `app_key`, `app_secret`, `access_token`, `refresh_token`, mã uỷ quyền hay dữ liệu người mua vào kho mã. Nạp bí mật từ biến môi trường hoặc trình quản lý bí mật.

**Dữ liệu người mua không rời hệ thống.** Không gửi tên, số điện thoại, địa chỉ người mua ra dịch vụ ngoài trong bất kỳ trường hợp nào, kể cả khi tích hợp mô hình ngôn ngữ.

**Mọi lần ghi ra sàn phải chống trùng.** Bấm hai lần, gọi lại sau lỗi mạng, chạy lại công việc nền — cả ba đều phải cho ra đúng một kết quả trên sàn.

**Dữ liệu bị sàn che là trạng thái hợp lệ.** Với đơn dùng vận chuyển của sàn, TikTok che một phần tên và số điện thoại người nhận. Giao diện phải hiển thị đúng trạng thái bị che ngay từ đầu, không coi đó là lỗi dữ liệu.

**Số ước tính phải có nhãn.** Lợi nhuận trước khi tiền về, lợi nhuận phiên live, hoa hồng trước đối soát đều là số ước tính. Hiển thị kèm nhãn phân biệt, không trình bày như số đã đối soát.

---

## 11. Rủi ro đang mở

| Rủi ro                                                                | Ảnh hưởng                                                                                                                                                                                                                                 | Người quyết  | Hạn cần câu trả lời      |
| --------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ | ------------------------ |
| Hồ sơ đăng ký App Developer và Public App chưa được TikTok duyệt      | Chặn toàn bộ dự án — không kết nối được gian hàng nào                                                                                                                                                                                     | Ban giám đốc | Trước T1                 |
| Đội 4 người so với giả định 9 người                                   | Phạm vi 241 hạng mục trong 16 tuần không khả thi                                                                                                                                                                                          | Ban giám đốc | Trước T1                 |
| Không có kiểm thử viên                                                | Chất lượng phụ thuộc hoàn toàn vào kỷ luật tự kiểm của đội                                                                                                                                                                                | Ban giám đốc | Trước T5                 |
| Quyền lưu trữ và sử dụng dữ liệu creator theo ToS Partner Center §3.8 | Bốn hạng mục phân tích creator không giao được việc. **Có thể bị bỏ hẳn, không phải hoãn.** Lưu ý: dữ liệu đã nằm sẵn trong quyền gian hàng, nên không có rào cản kỹ thuật nào ngăn đội vô tình build phần này — chặn bằng kỷ luật review | Legal        | Trước khi mở giai đoạn 4 |
| Nguồn ngưỡng hạn dùng cho hàng ngoài kho FBT                          | Bước 3.4 phải chạy bằng ngưỡng mặc định tự đặt                                                                                                                                                                                            | Tech Lead    | Trước T11                |

---

## 12. Tài liệu liên quan

| Tài liệu                                                                       | Dùng khi nào                                                                |
| ------------------------------------------------------------------------------ | --------------------------------------------------------------------------- |
| [vishop-function-list.md](./vishop-function-list.md)                           | Tra chi tiết một hạng mục cần làm gì                                        |
| [vishop-api-contract.md](./vishop-api-contract.md)                             | Trước khi viết endpoint mới hoặc dựng màn mới                               |
| [vishop-erd.md](./vishop-erd.md)                                               | Trước khi thêm bảng hoặc đổi quan hệ                                        |
| [vishop-dev-guidelines.md](./vishop-dev-guidelines.md)                         | Quy ước code và các điểm khác thường của API TikTok                         |
| [vishop-capability-matrix.md](./vishop-capability-matrix.md)                   | Kiểm tra một hạng mục có nguồn dữ liệu thật hay không trước khi bắt tay làm |
| [vishop-tiktok-integration-runbook.md](./vishop-tiktok-integration-runbook.md) | Khi xử lý sự cố tích hợp với sàn                                            |
| [vishop-test-plan.md](./vishop-test-plan.md)                                   | Khi viết test cho một bước                                                  |
| [vishop-sprint-plan.md](./vishop-sprint-plan.md)                               | Xem bối cảnh kế hoạch gốc theo sprint                                       |
