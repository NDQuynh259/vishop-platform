# ViShop — Lộ trình trợ lý ViAI và tự động hoá (Lô D)

**Chốt:** 07/08/2026 · **Phạm vi:** mở rộng trong Phase 1, đánh số FR tiếp nối trong M13 và M14 · **Không mở module mới** (dải M16–M23 đã dành cho Phase 2)

Tài liệu này nói ba việc: vì sao ViShop cần một trợ lý, cái gì đã dựng xong, và cái gì tuyệt đối không được để máy tự làm. Phần cuối là các FR phát sinh và điều kiện để chuyển sang giai đoạn tiếp theo.

---

## 1. Vấn đề đang có, trước khi nói tới AI

Một người vận hành ViShop mỗi sáng phải mở lần lượt: hàng đợi việc, danh sách chờ bàn giao, bảng tồn, lô hạn dùng, vận đơn có vấn đề, điểm gian hàng. Sáu màn, sáu lần lọc, chỉ để trả lời đúng một câu: _hôm nay việc gì gấp nhất_.

Bốn hệ quả đo được:

- Người mới mất khoảng một tuần mới nhớ hết màn nào chứa số nào.
- Việc gấp bị phát hiện muộn vì nó nằm ở màn hôm đó không ai mở.
- Cùng một câu hỏi, hai người tra ra hai con số khác nhau vì lọc khác nhau.
- Thao tác hàng loạt (gom sóng, lấy tem) phải tự đi tìm điểm bắt đầu.

Màn _Việc hôm nay_ (FR-13-11) đã gom được phần hàng đợi. Phần còn lại — hỏi một câu, ra đúng số, kèm đường đi tới chỗ xử lý — là chỗ trợ lý có ích. Đây là bài toán điều hướng và tổng hợp, không phải bài toán sinh nội dung.

**Nguyên tắc xuyên suốt:** trợ lý không được nói ra con số mà người dùng không đối chiếu được ở màn tương ứng. Trợ lý sai một lần là mất niềm tin vĩnh viễn, và người vận hành sẽ quay lại mở sáu màn như cũ.

---

## 2. Bốn giai đoạn

| Giai đoạn | Nội dung                                                                                 | Trạng thái          |
| --------- | ---------------------------------------------------------------------------------------- | ------------------- |
| **D1**    | Khung hội thoại + danh mục tool + kết quả tính ở server + cổng xác nhận cho thao tác ghi | **Xong 07/08/2026** |
| **D2**    | Tài liệu này: hợp đồng tool, ranh giới an toàn, FR phát sinh                             | **Xong 07/08/2026** |
| **D3**    | Nối mô hình qua function calling, thay bộ định tuyến từ khoá                             | Chưa bắt đầu        |
| **D4**    | Tự động hoá theo luật: việc lặp lại chạy nền, việc rủi ro vẫn chờ người duyệt            | Chưa bắt đầu        |

D1 và D3 tách rời có chủ đích. Danh mục tool, cách trình bày kết quả và cổng xác nhận là phần khó và phần rủi ro; chúng không phụ thuộc vào việc dùng mô hình nào. Làm xong phần đó trước nghĩa là khi nối mô hình, thứ duy nhất thay thế là bước _câu hỏi → chọn tool_, không phải viết lại giao diện.

---

## 3. Hợp đồng tool (đã dựng ở D1)

### 3.1 Hai loại tool

| Loại     | Làm gì                                                                                | Ghi dữ liệu |
| -------- | ------------------------------------------------------------------------------------- | ----------- |
| `read`   | Trả về một thẻ số liệu: tiêu đề, các dòng số, nguồn số liệu, liên kết tới màn xử lý   | Không       |
| `action` | Trả về **đề xuất**: việc sẽ làm, số đối tượng ảnh hưởng, hệ quả, nút mở màn thực hiện | Không       |

Điểm quan trọng nhất của bản này: **tool `action` cũng không ghi gì cả.** Nó chuẩn bị, người dùng mở màn thật và xác nhận ở đó. Đây không phải hạn chế kỹ thuật của bản mock mà là thiết kế sản phẩm giữ lại cả khi nối mô hình — xem mục 4.

### 3.2 Tám tool đầu tiên

| Tên hàm               | Loại   | Trả lời câu hỏi                           | Màn xử lý              |
| --------------------- | ------ | ----------------------------------------- | ---------------------- |
| `work_queue_summary`  | read   | Hôm nay việc gì đang chặn vận hành        | `/work`                |
| `orders_due_soon`     | read   | Đơn nào sắp trễ hạn bàn giao              | `/orders/pending-rts`  |
| `inventory_low_cover` | read   | SKU nào sắp hết theo tốc độ bán           | `/inventory`           |
| `batch_expiry`        | read   | Lô nào quá hạn hoặc quá mốc cấm bán       | `/inventory/batches`   |
| `shipment_issues`     | read   | Vận đơn nào đang có vấn đề                | `/shipping/exceptions` |
| `shop_health`         | read   | Điểm gian hàng đang thế nào               | `/shops/health`        |
| `prepare_pick_wave`   | action | Gom sóng nhặt cho kho nào, bao nhiêu kiện | `/picking`             |
| `prepare_label_batch` | action | Lấy tem cho bao nhiêu kiện đã nhặt xong   | `/print`               |

Mỗi tool khai báo: tên hàm, loại, mô tả (viết cho máy đọc), câu nói mở đầu, câu hỏi mẫu, tập từ khoá. Khai báo nằm ở một chỗ duy nhất; thêm việc mới là thêm ở đó rồi bổ sung phần tính số tương ứng.

**Tên hàm đã phát hành thì không đổi.** Khi D3 nối mô hình, tên hàm trở thành một phần hợp đồng với mô hình; đổi tên là làm hỏng mọi ví dụ và mọi bản ghi nhật ký cũ.

### 3.3 Ba ràng buộc bắt buộc trên mỗi câu trả lời

1. **Có nguồn.** Mỗi thẻ nêu số liệu lấy từ đâu và tính tại mốc thời gian nào. Số của sàn phải nói rõ là của sàn; số ViShop tự suy phải nói rõ là tự suy. Ví dụ đang áp dụng: mốc cấm bán theo hạn dùng chỉ được nói là "sàn không cho bán" khi ngưỡng thật sự đến từ sàn, còn hàng seller tự giữ thì đó là mặc định ViShop và sửa được (xem FR-05-24, FR-05-25).
2. **Có đường đi.** Mỗi thẻ có đúng một nút dẫn tới màn xử lý. Trợ lý trả lời xong mà người dùng vẫn phải tự đi tìm màn thì chưa giải quyết được gì.
3. **Không khớp thì nói thẳng.** Câu hỏi không thuộc tám việc trên thì trả lời là chưa làm được và liệt kê những việc làm được. Tuyệt đối không chọn tool gần đúng nhất rồi đưa ra số của việc khác.

### 3.4 Chỗ D3 sẽ thay

Hôm nay bước _câu hỏi → chọn tool_ là so khớp từ khoá tiếng Việt, có chuẩn hoá bỏ dấu nên gõ "sap het han" cũng khớp. Cách chấm điểm là cộng độ dài các cụm khớp được, để cụm dài thắng cụm ngắn — "sắp hết hàng" phải ra tồn kho chứ không ra hạn dùng.

Cách này đủ dùng cho tám việc và có một ưu điểm mà mô hình không có: nó sai một cách đoán trước được. Khi số tool vượt khoảng hai mươi, hoặc khi cần hiểu câu hỏi ghép nhiều điều kiện, nó hết tác dụng và phải chuyển sang function calling. Phần thay thế đúng một hàm; mọi thứ còn lại giữ nguyên.

---

## 4. Ranh giới an toàn

### 4.1 Ba mức

| Mức                       | Định nghĩa                                            | Ví dụ                                                                   | Ai quyết định                        |
| ------------------------- | ----------------------------------------------------- | ----------------------------------------------------------------------- | ------------------------------------ |
| **Tự chạy**               | Chỉ đọc, không đổi trạng thái gì                      | Đọc số liệu, tính lại cảnh báo, gửi báo cáo theo lịch                   | Máy                                  |
| **Chạy được sau khi bật** | Ghi trong ViShop, hoàn tác được, không gọi sang sàn   | Gom sóng nhặt, gán vị trí kệ, đánh dấu cảnh báo đã đọc                  | Người bật một lần cho từng loại việc |
| **Luôn cần người duyệt**  | Gọi ghi sang sàn, đụng tiền, hoặc không hoàn tác được | Lấy tem, bàn giao vận chuyển, đẩy tồn lên sàn, duyệt hoàn hàng, huỷ đơn | Người duyệt từng lần                 |

Tiêu chí phân mức là **có hoàn tác được không**, không phải mức độ phức tạp. Một việc đơn giản mà không rút lại được thì vẫn nằm ở mức ba.

### 4.2 Vì sao lấy tem nằm ở mức ba

Mỗi kiện chỉ lấy tem một lần. Có tem rồi thì muốn đổi hãng vận chuyển phải huỷ kiện và tách lại từ đầu. Một lượt lấy tem tự động cho hai trăm kiện sai hãng là hai trăm lần thao tác tay để sửa. Chi phí một lần bấm xác nhận rẻ hơn nhiều lần đó.

Cùng logic với đẩy tồn: [Update Inventory](https://partner.tiktokshop.com/docv2/page/6503068fc20ad60284b38858) chặn thao tác thêm/bớt warehouse (lỗi 12052533/12052530/12052531/12019028), nên một lệnh đẩy sai cấu hình không sửa được từ ViShop mà phải vào Seller Center.

### 4.3 Nhật ký

Mọi lượt hỏi ghi lại: câu hỏi, tool đã gọi, kết quả trả về, đề xuất nếu có, và ai xác nhận. Nhật ký này gắn vào nhật ký hệ thống của M01. Không có nhật ký thì khi số liệu sai không truy được là trợ lý đọc sai hay dữ liệu nguồn đã sai.

### 4.4 Bí mật

Khi D3 nối mô hình và D4 gọi API sàn theo lịch, khoá vẫn theo đúng ràng buộc đang áp cho toàn bộ dự án:

> Không commit `app_key`, `app_secret`, `access_token`, `refresh_token`, mã uỷ quyền hay dữ liệu seller vào mã nguồn. Nạp bí mật từ biến môi trường hoặc trình quản lý bí mật.

Thêm một ràng buộc riêng của giai đoạn AI: **không gửi dữ liệu người mua (tên, số điện thoại, địa chỉ) ra ngoài hệ thống ViShop.** Tool trả về số đếm và mã đơn; phần thông tin người mua chỉ hiện ở màn xử lý, nơi đã có phân quyền.

---

## 5. FR phát sinh

Đánh số tiếp nối trong M13 (Báo cáo & Dashboard) và M14 (Cảnh báo & Thông báo). Trợ lý đọc số nên ở cùng nhà với dashboard và _Việc hôm nay_; phần ranh giới tự động hoá thuộc M14 vì nó quyết định việc gì được chạy không cần người.

### M13 bổ sung (tiếp) — Trợ lý ViAI

| FR ID    | Priority | Chức năng                                                                                                                      | Ghi chú cần lưu ý                                                               |
| -------- | -------- | ------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------- |
| FR-13-14 | P1       | Khung hội thoại trợ lý mở từ mọi màn: hỏi bằng tiếng Việt có dấu hoặc không dấu, nhận về thẻ số liệu kèm nút dẫn tới màn xử lý | Đặt ở sidebar, không chiếm chỗ của màn đang làm việc.                           |
| FR-13-15 | P0       | Danh mục tool khai báo tập trung: tên hàm, loại `read`/`action`, mô tả cho máy đọc, câu hỏi mẫu                                | Tên hàm đã phát hành thì không đổi — nó là hợp đồng với mô hình ở giai đoạn D3. |
| FR-13-16 | P0       | Mỗi câu trả lời nêu nguồn số liệu và mốc thời gian tính; câu hỏi không khớp tool nào thì trả lời là chưa làm được              | Cấm chọn tool gần đúng nhất rồi trả số của việc khác.                           |
| FR-13-17 | P1       | Sáu việc đọc mở màn: hàng đợi việc · hạn bàn giao · tồn theo tốc độ bán · lô hạn dùng · vận đơn có vấn đề · điểm gian hàng     | Số liệu tính ở server, không kéo dữ liệu vận hành xuống trình duyệt.            |

### M14 bổ sung — Tự động hoá có người duyệt

| FR ID    | Priority | Chức năng                                                                                                                                              | Ghi chú cần lưu ý                                                                                                           |
| -------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------- |
| FR-14-08 | P0       | Tool loại `action` chỉ soạn đề xuất: việc sẽ làm, số đối tượng ảnh hưởng, hệ quả — không tự ghi dữ liệu                                                | Người dùng mở màn thực hiện và xác nhận tại đó. Giữ nguyên cả sau khi nối mô hình.                                          |
| FR-14-09 | P1       | Ba mức tự động: tự chạy (chỉ đọc) · chạy sau khi bật (ghi nội bộ, hoàn tác được) · luôn cần người duyệt (ghi sang sàn, đụng tiền, không hoàn tác được) | Tiêu chí phân mức là hoàn tác được hay không, không phải độ phức tạp. Lấy tem, bàn giao, đẩy tồn, duyệt hoàn luôn ở mức ba. |
| FR-14-10 | P1       | Nhật ký trợ lý: câu hỏi, tool đã gọi, kết quả, đề xuất và người xác nhận                                                                               | Gắn vào nhật ký hệ thống của M01. Không có nhật ký thì không truy được số sai từ đâu ra.                                    |

**Bổ sung đợt Lô D: 7 FR** (M13: 4 · M14: 3). Không phát sinh module mới.

---

## 6. Rủi ro và cách kiểm chứng

| Rủi ro                                | Vì sao nghiêm trọng                              | Cách kiểm chứng trước khi phát hành                                                                                     |
| ------------------------------------- | ------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------- |
| Trợ lý trả số lệch với màn tương ứng  | Mất niềm tin là mất luôn tính năng               | Với mỗi tool, mở màn đích và đối chiếu từng con số. Đây là điều kiện chặn phát hành, không phải việc kiểm tra tuỳ chọn. |
| Bộ định tuyến chọn nhầm tool          | Người dùng đọc số của việc khác mà không biết    | Bộ câu hỏi mẫu có cả biến thể không dấu và biến thể dễ nhầm ("sắp hết hàng" vs "sắp hết hạn")                           |
| Mô hình ở D3 bịa số                   | Mô hình có thể tự sinh con số thay vì gọi tool   | Kết quả hiển thị lấy nguyên từ giá trị tool trả về; phần mô hình sinh ra chỉ là câu dẫn, không chứa số                  |
| Tự động hoá ở D4 chạy quá tay         | Một lượt sai hàng loạt tốn nhiều giờ sửa tay     | Mọi luật tự động chạy ở chế độ chỉ ghi nhật ký trong hai tuần trước khi được phép ghi thật                              |
| Rò dữ liệu người mua qua tầng mô hình | Vi phạm cam kết dữ liệu với sàn và với người mua | Tool không trả về tên, số điện thoại, địa chỉ người mua                                                                 |

---

## 7. Điều kiện chuyển giai đoạn

**D1 → D3 (nối mô hình).** Đủ cả bốn:

1. Tám tool hiện có đã đối chiếu khớp với màn đích, không còn lệch số.
2. Danh mục tool ổn định ít nhất một sprint, không đổi tên hàm.
3. Đã chốt mô hình dùng ở đâu và dữ liệu đi qua đâu, có văn bản.
4. Nhật ký trợ lý (FR-14-10) đã chạy, để so được trước và sau khi nối mô hình.

**D3 → D4 (tự động hoá).** Đủ cả ba:

1. Ba mức tự động (FR-14-09) đã cấu hình được ở giao diện, không hard-code.
2. Có ít nhất một luật chạy ở chế độ chỉ ghi nhật ký đủ hai tuần và số liệu đúng.
3. Có đường tắt dừng toàn bộ tự động hoá trong một thao tác, không cần deploy.

Điều kiện thứ ba của D4 là điều kiện không thương lượng. Một hệ tự động không có công tắc tắt là một hệ không được phép bật.
