# ViShop — Sprint Plan T1–T16

**Nguồn:** `vishop-build-spec-v1.2.md` §8 (Lộ trình & Phụ thuộc) + `vishop-function-list.md` (249 FR, 16 module, đã audit — 206 FR nền + 3 FR tổ chức + 35 FR bổ sung từ UI + 5 FR tách nhánh 12–14/08/2026). **Phạm vi Phase 1 là 244 FR** — 4 FR nhóm M11-B nằm ngoài cam kết và 1 FR (FR-11-18c) bỏ hẳn, theo quyết định 07/08/2026, xem mục 5.
**Đơn vị thời gian:** T = tuần. 4 sprint × 4 tuần = 16 tuần, khớp roadmap đã chốt trong spec.

Tiền đề bắt buộc trước T1: **G0 — hồ sơ Seller Partner API phải được TikTok duyệt.** Đây không phải việc trong Sprint 1-2, mà là điều kiện mở khoá M02. R1 trong spec xếp thẳng "Chặn dự án" — nếu G0 chưa duyệt, lùi ngày bắt đầu toàn bộ roadmap, không lùi từng sprint riêng lẻ.

---

## 1. Bảng tổng hợp

| Sprint     | Tuần    | Module                        | Tổng FR                         | P0 (+P0\*)                | P1      | P2    | Mốc kiểm tra                                        |
| ---------- | ------- | ----------------------------- | ------------------------------- | ------------------------- | ------- | ----- | --------------------------------------------------- |
| Sprint 1-2 | T1-T4   | M00, M01, M02, M03, M04       | 60                              | 48                        | 10      | 2     | Kết nối shop thật, thấy đơn thật, ghép nối được SKU |
| Sprint 3-4 | T5-T8   | M05, M06, M07                 | 84                              | 70                        | 12      | 2     | Alpha nội bộ — trọn vòng đời một package thật       |
| Sprint 5-6 | T9-T12  | M05 (hạn dùng), M08, M09, M10 | 34                              | 26                        | 8       | 0     | Beta — 5 khách chạy thật, đo tỉ lệ đơn xử lý        |
| Sprint 7-8 | T13-T16 | M11, M12, M13, M14, M15       | 63 → **58**                     | 39 (3 P0\* ngoài cam kết) | 19 → 18 | 2 → 1 | GA                                                  |
| **Tổng**   |         | 16 module                     | **241** → **236** trong Phase 1 |                           |         |       |                                                     |

Sprint 3-4 gánh khối lượng P0 nặng nhất dự án (70 FR — hơn 80% số FR toàn sprint) và trùng đúng mốc Alpha, không có buffer để trễ. Sprint 5-6 là sprint duy nhất không có FR P2, tải nhẹ nhất — chỗ hợp lý để nhét công việc hardening.

> **Tải Sprint 3-4 và cách xử lý — đã chốt 07/08/2026.** Trong 35 FR bổ sung, **20 FR rơi vào Sprint 3-4** (nhóm M05 đa kho + M06 đóng gói/vận chuyển), sprint vốn đã được đánh dấu "không có slack". Để nguyên thì số FR sprint này tăng từ 66 lên 86 (+30%), P0 tăng từ 53 lên 72 (+36%). Giữ nguyên mốc Alpha cuối T8 với cùng quy mô đội là không thực tế.
>
> **Quyết định: dời nhóm hạn dùng (FR-05-24, FR-05-25) sang Sprint 5-6.** Bảng trên đã tính theo quyết định này. Lý do chọn cách này thay vì tăng người hay lùi Alpha: hai FR đó đang là hạng mục chặn G1 thứ năm — ngưỡng hạn dùng ngoài FBT chưa xác minh được nguồn ([Capability Matrix](./vishop-capability-matrix.md) mục 2 dòng #5). Làm chúng trong Sprint 3-4 nghĩa là code trước rồi sửa lại sau khi có câu trả lời, tốn hai lần. Dời sang Sprint 5-6 vừa giảm tải sprint nặng nhất, vừa cho Tech Lead thêm 8 tuần để chốt nguồn ngưỡng. Tăng người bị loại vì Sprint 3-4 là chuỗi phụ thuộc M05 → M06 → M07, thêm người không rút ngắn được chuỗi. Lùi Alpha bị loại vì Alpha là điều kiện mở Beta ở T12, lùi một mốc là lùi cả hai.
>
> **Hệ quả phải chấp nhận:** Phase 1 mốc Alpha không có quản lý lô và hạn dùng. Đa kho, vị trí kệ, chuyển kho và tồn theo kho vẫn đủ để chạy trọn vòng đời một package — nhóm hạn dùng chỉ chặn seller ngành hàng thực phẩm và mỹ phẩm, không chặn Alpha nội bộ.

---

## 2. Sprint 1-2 (T1-T4) — Nền tảng, Auth, Kết nối, SKU, Ghép nối

| Module                     | FR  | P0  | P1  | P2  |
| -------------------------- | --- | --- | --- | --- |
| M00 Nền tảng hệ thống      | 12  | 8   | 3   | 1   |
| M01 Tài khoản & Phân quyền | 16  | 13  | 3   | 0   |
| M02 Kết nối gian hàng      | 14  | 12  | 2   | 0   |
| M03 Sản phẩm & SKU         | 16  | 12  | 3   | 1   |
| M04 Ghép nối SKU           | 10  | 9   | 1   | 0   |

Bổ sung 12–14/08/2026 sau đợt rà soát backlog: M00 nhận FR-00-04b, FR-00-04c, FR-00-07b; M01 nhận FR-01-03b, FR-01-04b. Bảng M01 trước đó ghi 11 FR — thiếu ba FR tổ chức (FR-01-12…14) đã bổ sung từ trước, nay tính lại cho khớp Function List.

**Phụ thuộc cứng:** G0 → M02 → M03 → M04. Không đảo được thứ tự — M03 cần OAuth từ M02 để đồng bộ sản phẩm, M04 cần catalog SKU từ M03 để ghép nối. M00 (event log, job queue, idempotency, rate governor) không phụ thuộc module nghiệp vụ nào nhưng mọi module sau đều phụ thuộc ngược vào nó — làm sai idempotency ở FR-00-03 thì đơn/tồn ở mọi module sau đều có nguy cơ trùng lặp khi retry.

Bổ sung 07/08/2026: M02 nhận FR-02-14 — màn Sức khoẻ gian hàng, đọc điểm SPS từ [Get SPS Overview](https://partner.tiktokshop.com/docv2/page/6a5f09c44c0d61049ea780c5) và bốn endpoint SPS liên quan. Nằm ở sprint này vì cùng phụ thuộc OAuth với phần còn lại của M02, không phải vì độ ưu tiên.

**Đã khép trước khi vào sprint (07/08/2026):** khoảng trống webhook receiver của FR-00-08 đã được bịt bằng `FR-00-09` (bộ nhận webhook từ TikTok), P0, nằm ngay trong Sprint 1-2 này. Scope M00 vì thế lên 9 FR thay vì 8 và đã khoá — backlog sprint không còn nguy cơ đổi giữa chừng. FR-00-09 phải xong trước FR-09-09 ở Sprint 5-6, vì đồng hồ đếm ngược 24 giờ của FR-09-09 lấy mốc từ payload webhook.

---

## 3. Sprint 3-4 (T5-T8) — Tồn kho, Đơn hàng/Package, In tem

| Module                 | FR  | P0  | P1  | P2  |
| ---------------------- | --- | --- | --- | --- |
| M05 Tồn kho            | 25  | 21  | 3   | 1   |
| M06 Đơn hàng & Package | 38  | 32  | 5   | 1   |
| M07 In tem & Chứng từ  | 21  | 17  | 4   | 0   |

M06 là module lớn nhất toàn dự án — 38 FR, riêng nó đã lớn hơn cả M08+M09+M10 cộng lại của Sprint 5-6.

Phần bổ sung 07/08/2026 rơi vào sprint này: M05 nhận 6 FR đa kho (FR-05-20, FR-05-21, FR-05-22, FR-05-23, FR-05-26, FR-05-27) cộng FR-05-09 chuyển từ P2 lên P0 — **FR-05-24 và FR-05-25 đã dời sang Sprint 5-6** theo quyết định ở mục 1; M06 nhận 3 FR máy trạng thái đóng gói (FR-06-28 → FR-06-30) và 6 FR theo dõi vận chuyển (FR-06-31 → FR-06-36); M07 nhận 3 FR sóng nhặt (FR-07-15 → FR-07-17). Thứ tự làm trong sprint không đổi: kho và vị trí kệ (FR-05-20, FR-05-21) phải xong trước sóng nhặt, vì sóng nhặt sắp theo `bin_code`.

**Phụ thuộc cứng:** M04 phải xong (Sprint 1-2) trước khi mở M05 — không có ghép nối thì không có gì để đẩy tồn, spec nói thẳng không được đảo thứ tự này. Trong nội bộ sprint: M05 → M06 → M07 theo chuỗi §8, vì package (M06) cần trạng thái tồn khả dụng để tạo, và in tem (M07) chỉ chạy được sau khi package đã RTS (FR-07-14: pre-fetch tem ngay sau RTS thành công).

**Rủi ro tiến độ:**

- FR-06-08 bị TikTok che một phần SĐT/tên người nhận khi dùng platform logistics. Đã chốt: dữ liệu bị che là trạng thái hợp lệ, thiết kế màn hình đón nó ngay từ đầu chứ không vá sau khi QA phát hiện.
- FR-05-16 chặn sửa tồn trực tiếp cho SKU combo (lỗi 12052849/12052850) — phải build công thức BR-05-03 trước, không gọi thẳng Update Inventory API.
- Đây là sprint quyết định mốc Alpha (cuối T8) — không có slack để bù trễ sang Sprint 5-6, vì Sprint 5-6 nuôi mốc Beta ngay sau đó.

---

## 4. Sprint 5-6 (T9-T12) — Quét & Kiểm hàng, Sau bán, Chi phí & Lợi nhuận

| Module                                          | FR  | P0  | P1  | P2  |
| ----------------------------------------------- | --- | --- | --- | --- |
| M05 Tồn kho — nhóm hạn dùng (dời từ Sprint 3-4) | 2   | 2   | 0   | 0   |
| M08 Quét mã & Kiểm hàng                         | 11  | 8   | 3   | 0   |
| M09 Sau bán & Hoàn hàng                         | 9   | 7   | 2   | 0   |
| M10 Chi phí & Lợi nhuận                         | 12  | 9   | 3   | 0   |

**Phụ thuộc cứng:** M10 cần giá vốn từ M03 và dòng phí từ M06 — cả hai đã xong từ hai sprint trước, không có phụ thuộc chặn trong nội bộ sprint này. M08 và M09 độc lập với nhau, chạy song song được.

**Đã chốt trước khi vào sprint (07/08/2026):** FR-09-01 chỉ đồng bộ một chiều, trong khi TikTok bắt seller trả lời yêu cầu huỷ trong 24h rồi mới tự duyệt hoàn tiền. Lỗ hổng này đã được bịt bằng `FR-09-09` (Duyệt/Từ chối kèm đồng hồ đếm ngược), P0, nằm trong chính Sprint 5-6 này nên không lùi mốc. Sprint 5-6 vì thế lên 34 FR thay vì 33.

Tải P0 sprint này thấp hơn hẳn hai sprint trước (25 so với 47 và 70) — dùng phần dư để chạy regression cho Alpha và chuẩn bị hạ tầng đo lường cho mốc Beta cuối T12, không rút người.

Bổ sung 07/08/2026: M08 nhận FR-08-10 (quét toàn màn hình) và FR-08-11 (gộp kiểm hàng với bàn giao trên cùng một máy quét). M05 nhận FR-05-24 và FR-05-25 dời từ Sprint 3-4 — **điều kiện tiên quyết: Tech Lead phải chốt xong nguồn ngưỡng hạn dùng cho hàng ngoài FBT trước T9**, nếu chưa chốt thì làm phần lô hàng trước và để trạng thái hạn dùng chạy bằng ngưỡng mặc định ViShop.

---

## 5. Sprint 7-8 (T13-T16) — Affiliate & Livestream, Đối soát, Báo cáo, Cảnh báo, Billing

| Module                                              | FR  | P0  | P0\* | P1  | P2  |
| --------------------------------------------------- | --- | --- | ---- | --- | --- |
| M11 Affiliate & Livestream (đã trừ 4 FR M11-B hoãn) | 13  | 9   | 0    | 3   | 1   |
| M12 Đối soát tài chính                              | 10  | 7   | 0    | 2   | 1   |
| M13 Báo cáo & Dashboard                             | 17  | 10  | 0    | 7   | 0   |
| M14 Cảnh báo & Thông báo                            | 10  | 6   | 0    | 4   | 0   |
| M15 Gói cước & Billing                              | 9   | 7   | 0    | 2   | 0   |

**Phụ thuộc cứng nội bộ sprint:** M11-A (FR-11-01a đến 06a) cần M06 (đã xong) **và** M12 — hai module này nằm cùng một sprint, không phải sprint khác nhau như các cặp phụ thuộc trước. Nghĩa là M12 phải lên trước trong lịch làm việc 4 tuần này, không chạy song song ngang hàng với M11-A. Khuyến nghị: M12 chiếm 2 tuần đầu, M11-A bắt đầu từ tuần 2 sau khi có dữ liệu hoa hồng từ Finance.

**M11-B nằm ngoài cam kết Phase 1 — quyết định 07/08/2026.** 4 FR phụ thuộc dữ liệu creator (FR-11-02b, FR-11-05b, FR-11-06b, FR-11-09b) không nằm trong Sprint 7-8 nữa.

Lý do là **pháp lý, không phải capability**: §3.8(a)/(e)/(f) của ToS Partner Center giới hạn việc lưu trữ và dùng lại dữ liệu creator, chưa có xác nhận Legal (R10). **Khả năng Legal không chấp nhận là có thật** — nếu vậy thì bỏ hẳn, không phải đưa sang sprint sau. Không đưa 4 FR này vào kế hoạch nguồn lực của bất kỳ sprint nào cho tới khi có văn bản.

_(Bản đầu của đoạn này ghi lý do là "cần tư cách TikTok Affiliate Partner". Sai — đã kiểm chứng schema gốc, endpoint chạy bằng quyền của chính gian hàng. Xem [vishop-capability-matrix.md](./vishop-capability-matrix.md) mục 4.)_

Tách được mà không kéo sập sprint vì kiến trúc §8 đã cô lập sẵn: 13 FR còn lại của M11 (M11-A affiliate + M11-C livestream official account) chạy trên dữ liệu tổng hợp, không lưu hồ sơ creator. Bảng trên đã trừ 4 FR này ra.

Phần nguồn lực dôi ra dồn sang M12 và M13 — hai module này vốn là chuỗi phụ thuộc chặt nhất của sprint, thêm người vào đó có tác dụng thật.

Ràng buộc giữ lại cho lúc mở khoá: chi tiết ở mục _Hoãn nhóm phân tích creator khỏi Phase 1_ trong [vishop-function-list.md](./vishop-function-list.md). Tóm tắt: cờ tính năng mặc định tắt, chỉ bật khi có đủ cả tư cách đối tác còn hiệu lực lẫn xác nhận Legal bằng văn bản.

Bổ sung 07/08/2026: M13 nhận FR-13-11 (màn "Việc hôm nay" làm trang chủ vận hành, P0), FR-13-12 và FR-13-13 (quản lý lịch báo cáo và lịch sử gửi, P1). FR-13-11 chỉ là cách bày lại dữ liệu đã có từ các module trước, không sinh phụ thuộc mới; FR-13-12/13 là phần vận hành hoá FR-13-10.

Bổ sung 07/08/2026 (đợt Lô D — trợ lý ViAI): M13 nhận thêm FR-13-14…FR-13-17, M14 nhận FR-14-08…FR-14-10. Chi tiết ở [vishop-ai-automation-roadmap.md](./vishop-ai-automation-roadmap.md). Nhóm này **phụ thuộc ngược lên toàn bộ các module trước** — trợ lý chỉ đọc lại số của M05, M06, M07 và M02, nên nó phải nằm ở sprint cuối và không thể kéo lên sớm. Đổi lại, nó không chặn gì cả: bỏ hẳn nhóm này khỏi Phase 1 thì GA vẫn đủ điều kiện. Xếp đây là nhóm cắt đầu tiên nếu Sprint 7-8 quá tải, trừ FR-13-15 và FR-13-16 — hai FR này là ràng buộc thiết kế, phải chốt trước khi ai đó viết dòng code đầu tiên của trợ lý, không phải hạng mục làm sau.

**Rủi ro tiến độ khác — đã khép:** FR-12-10 (đối chiếu sao kê ngân hàng, P2) từng treo vì Get Payments API không khả dụng ở thị trường SEA. Đã chốt không chờ TikTok/BD: v1 làm nhập tay file sao kê với đối chiếu bán tự động, thiết kế qua một cổng nạp nhiều bộ tiếp nhận để sau này mở API chỉ thêm bộ tiếp nhận. Vẫn là P2, không ảnh hưởng GA.

---

## 6. Chuỗi phụ thuộc cứng xuyên toàn bộ roadmap

```
G0 (duyệt Seller API)
  → M02 (T1-T4)
    → M03 (T1-T4)
      → M04 (T1-T4)
        → M05 (T5-T8)
          → M06 (T5-T8)
            → M07 (T5-T8)

M03 + M06 → M10 (T9-T12)
M10 → M12 (T13-T16)
M06 + M12 → M11-A (T13-T16, M12 phải đi trước trong cùng sprint)
Legal duyệt ToS §3.8 → M11-B (ngoài cam kết Phase 1, có thể bỏ hẳn)
```

Toàn bộ chuỗi từ G0 đến M07 là một đường thẳng — bất kỳ FR nào trong M02-M04 trễ đều dội thẳng vào Sprint 3-4. Ngược lại M08-M10 (Sprint 5-6) không có phụ thuộc chéo nội bộ, là sprint linh hoạt nhất để hấp thụ trễ từ trước nếu có.

---

## 7. Khuyến nghị quy mô đội và thời lượng

Team cố định 4 BE + 3 FE + 2 QA (9 người), giữ nguyên suốt 16 tuần — không co giãn theo từng sprint. Lý do: Sprint 3-4 là đỉnh tải (53 P0 FR), nên team phải đủ sức cho đỉnh tải đó ngay từ đầu; đổi người giữa sprint nghĩa là dev vừa quen OAuth/rate-limiter ở M02 lại phải bàn giao cho người mới đúng lúc M05/M06 cần hiểu logic ghép nối SKU họ vừa xây — chi phí bàn giao domain đắt hơn chi phí giữ người rảnh vài tuần.

**Con số 9 người là giả định lập kế hoạch, chưa được chốt.** Build-spec §12 câu hỏi mở #4 và PRD Open Questions #2 đều liệt "Headcount và ngân sách 16 tuần đầu?" là câu hỏi chưa trả lời, người quyết là Ban giám đốc, cột Chặn = Có — kế hoạch phân bổ dưới đây chạy trên giả định 9 người, không phải nguồn lực đã phê duyệt. Nếu headcount thực tế thấp hơn, ảnh hưởng nặng nhất rơi đúng vào Sprint 3-4 — sprint đỉnh tải (53 P0 FR) đã xác định là không có buffer để trễ; toàn bộ lịch 16 tuần phải tính lại trước khi Ban giám đốc chốt số, không phải điều chỉnh ngầm giữa sprint.

Phân bổ theo sprint:

- **Sprint 1-2 (T1-T4):** đủ 9 người, nhưng QA chạy ở mức tối thiểu — tập trung test contract API và permission matrix (M01), auth OAuth (M02). Tải nhẹ hơn Sprint 3-4 nên đây là sprint để BE làm chắc nền (M00) mà không bị ép tiến độ.
- **Sprint 3-4 (T5-T8):** full lực 9 người, không nhận thêm việc ngoài roadmap. Đây là sprint duy nhất không có buffer — mốc Alpha là cam kết ra ngoài (khách/nội bộ), trễ ở đây kéo trễ toàn bộ Beta/GA phía sau.
- **Sprint 5-6 (T9-T12):** cùng 9 người nhưng tải P0 chỉ bằng 40% Sprint 3-4 — dùng phần dư (không cắt giảm người) cho regression test trước Beta và dọn nợ kỹ thuật phát sinh từ tốc độ Sprint 3-4.
- **Sprint 7-8 (T13-T16):** cùng 9 người. Sắp xếp nội bộ: BE dồn M12 hai tuần đầu trước khi mở M11-A; FE dồn M13/M14/M15 (dashboard, cảnh báo, billing) từ tuần 1 vì các module này không có phụ thuộc chéo, chạy song song an toàn với M12.

Thời lượng 4 tuần/sprint giữ nguyên theo roadmap đã chốt — không kéo dài sprint nào. Nếu Sprint 3-4 có dấu hiệu trễ giữa chừng (theo dõi burndown cuối tuần 2), quyết định là tăng tạm 1 BE từ nguồn ngoài dự án cho 2 tuần cuối, không phải dịch mốc Alpha — mốc Alpha là điểm đo tỉ lệ xử lý đơn thật, dịch mốc này kéo theo dịch toàn bộ input cho quyết định go/no-go Beta.
