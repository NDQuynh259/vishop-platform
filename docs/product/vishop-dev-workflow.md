# ViShop — Nhận việc và làm việc

**Doc này trả lời năm câu:** lấy ticket nào, đổi dữ liệu thế nào cho an toàn, làm sao hai người không giẫm chân nhau, ai chốt tên trường giữa BE và FE, và đưa ticket sang Done cần gì.

**Doc này không trả lời:** quy ước viết code (`vishop-dev-guidelines.md`), mô hình dữ liệu và lý do từng cột (`vishop-erd.md`), dựng máy lần đầu (`CONTRIBUTING.md`), luật board đầy đủ kèm cách cấu hình Jira (VIS-259).

---

## 1. Chọn ticket

Mỗi ticket mang ba nhãn quyết định thứ tự: `module-M00`…`M14`, `prio-p0`/`prio-p1`, `sprint-1-2`…`sprint-7-8`.

Chọn theo đúng thứ tự này, dừng ở điều kiện đầu tiên không thoả:

1. **Thuộc sprint đang chạy.** Ticket sprint sau không được kéo lên làm trước, kể cả khi rảnh — làm trước phần mình thích là cách nhanh nhất để sprint hiện tại trượt.
2. **`prio-p0` trước `prio-p1`.** Hết p0 trong sprint mới sang p1.
3. **Không bị chặn.** Ticket có link `is blocked by` mà bên chặn chưa Done thì chưa vào được. Board có 18 link phụ thuộc, chúng có thật và có lý do.
4. **Còn hạn mức việc đang mở.** Hiệp 2 · Quỳnh 2 · Hùng 1 · Lợi 2. Đủ rồi thì đóng một cái mới mở cái mới.

Hùng để 1 là cố ý, không phải đánh giá thấp: làm xong tử tế một việc có giá trị hơn mở hai việc dở.

### Bốn thứ không tự đổi

`assignee` · nhãn `sprint-*` · nhãn `prio-*` · link phụ thuộc.

Bốn thứ này **là kế hoạch**, không phải thuộc tính kỹ thuật của ticket. Tự đổi thì kế hoạch thành vô nghĩa và không ai truy được ai đổi. Cần đổi thì báo tech lead.

Và không kéo hộ ticket của người khác. Muốn nhận thì đổi assignee trước — chỉ tech lead đổi được.

---

## 2. Trước khi viết dòng code đầu tiên

Đọc phần TIÊU CHÍ HOÀN THÀNH trong ticket trước, không phải sau. Nó là thứ quyết định ticket có được sang Done hay không.

Rồi kiểm tra bảng mình cần đã có chưa. Repo đã dựng sẵn 13 bảng lõi — danh mục, SKU, combo, mapping sàn, kho, ô kệ, tồn, đơn, dòng đơn, package, dòng package. **Không tự tạo lại bảng đã có.** Xem `prisma/schema.prisma` và `vishop-erd.md`.

Nếu bảng đã có nhưng thiếu trường mình cần → mục 3.

---

## 3. Đổi dữ liệu — ba tầng

Nguyên tắc phân tầng: **thứ có khoá ngoại trỏ vào thì đổi sau rất đắt, thứ không ai trỏ vào thì thêm lúc nào cũng được.**

### Tầng 1 — tự làm: tạo bảng mới

Bảng mới trỏ khoá ngoại **vào** 13 bảng lõi là thuần cộng thêm, không sửa gì của ai. Phần lớn việc còn lại của backlog nằm ở tầng này: sổ biến động tồn, log đẩy tồn, phí đơn, đối soát, sóng nhặt, theo dõi vận đơn, lịch báo cáo. ERD có 36 thực thể, mới dựng 13 — 21 cái còn lại là việc của các bạn.

### Tầng 2 — tự làm: thêm cột nullable

Postgres 18 thêm cột nullable không rewrite bảng. Một migration một dòng.

Trước khi thêm, hỏi: **dữ liệu này đã có ở `event_logs` chưa?** Payload thô từ sàn nằm ở đó (`payloadAfter`). Đọc thẳng bằng `->>` không cần migration nào cả. Chứng minh use case trước, nâng thành cột khi thật sự cần lọc hoặc sắp xếp theo nó.

### Tầng 3 — phải hỏi tech lead

Đổi **tên cột**, đổi **kiểu dữ liệu**, đổi **khoá** (unique / khoá ngoại) của cột đã có.

Ba thứ này kéo theo FE và dữ liệu đã ghi. FE hiện đã code theo từ vựng trong ERD — `externalOrderId` xuất hiện 75 chỗ, `orderedAt` 40, `avgCost` 35. Đổi một cái tên ở đây là làm vỡ màn hình đơn hàng.

---

## 4. Migration — và chuyện hai MR đụng nhau

```bash
pnpm db:migrate --name add_low_stock_alert
```

Commit **cả hai**: `prisma/schema.prisma` và thư mục migration mới sinh ra. Thiếu một trong hai là hỏng.

**Rebase lên `develop-main` trước khi push.** Đây không phải thói quen cho đẹp, mà là để timestamp migration của mình nằm sau những cái đã merge.

### Vì sao phải cẩn thận ở đúng chỗ này

Hai người cùng sửa `schema.prisma` ở hai model khác nhau → git tự merge, không sao. Cùng sửa một dòng → git báo conflict, ồn ào, cũng không sao.

Nhưng hai người mỗi người thêm **một thư mục migration** thì git merge sạch, **không một dấu hiệu nào**. Tên thư mục khác nhau nên git không coi là xung đột. Vấn đề là migration của người thứ hai được sinh ra dựa trên trạng thái database **chưa có** thay đổi của người thứ nhất. Sau khi cả hai merge, chạy lại từ database trắng theo thứ tự timestamp có thể ra kết quả khác hẳn cái cả hai đã test — hoặc gãy luôn lúc deploy.

Job `schema-check` trong CI bắt đúng ca này: apply toàn bộ chain lên database trắng rồi hỏi Prisma xem kết quả có còn khớp `schema.prisma` không. Nó cũng bắt cả trường hợp sửa schema mà quên tạo migration, và sửa migration bằng tay mà quên cập nhật schema.

MR đỏ ở `schema-check` thì rebase và chạy lại `pnpm db:migrate`, đừng sửa tay file migration cho qua.

---

## 5. Hai hợp đồng không được phá

Hai chỗ này đã có ràng buộc ở tầng database. Làm sai thì transaction của chính mình gãy, không âm thầm làm hỏng dữ liệu người khác — nhưng biết trước vẫn hơn là gãy rồi mới đọc.

### Tiền là số nguyên theo đơn vị nhỏ nhất, không phải "đồng"

TikTok trả tiền dạng **chuỗi** kèm mã tiền tệ, và sàn có 12 loại tiền chứ không riêng VND. Mọi cột tiền trong repo là `BigInt` theo mũ ISO 4217: `1234` với `USD` là $12.34, còn VND mũ 0 nên con số chính là số tiền.

```ts
import { parseAmountToMinorUnits, minorUnitsToNumber } from '@nestjs-fastify-nx/shared';

const total = parseAmountToMinorUnits(payment.total_amount, payment.currency);
```

Hai lỗi phải tránh:

- **Đừng tự parse.** `Number('12.34') * 100` ra `1233.9999999999998`, cắt xuống thành số sai mà không có exception nào. Helper làm bằng phép toán chuỗi.
- **Đừng trả `BigInt` thẳng ra response.** `JSON.stringify` ném lỗi trên `BigInt`, client nhận 500. DTO phải gọi `minorUnitsToNumber()`.

Cột `BigInt` chứ không phải `Int` là vì `int4` trần 2.147.483.647 — khoảng 21,4 triệu IDR, một đơn hàng bình thường ở Indonesia. VND che mất trần này nên lỗi chỉ nổ đúng lúc mở thị trường mới.

### Một id sàn cho mỗi đơn vị hàng

TikTok phát `line_items[]` **một dòng cho mỗi đơn vị**, không có trường `quantity`: ba cái cùng SKU là ba phần tử, mỗi phần tử có `id` riêng. `POST /fulfillment/202309/packages` nhận `order_line_item_ids`.

Nên `order_lines.externalLineIds` phải có **đúng bằng `quantity` phần tử** (hoặc 0 với đơn nhập tay). Database chặn cứng. Thiếu một id là dòng đó không tạo được package — chặn lúc ghi vẫn hơn để lỗi hiện ra ở lời gọi sàn với thông báo khó hiểu.

`package_lines.externalLineIds` giữ tập con nằm trong package đó. Không suy ra lúc chạy: một `order_line` số lượng 3 chia vào 2 package thì phải chốt đơn vị nào đi package nào, nếu suy lại mỗi lần thì retry có thể cắt sai lát và ship nhầm.

---

## 6. Hợp đồng với FE — không ai được tự đặt tên trường

Hiện trạng để hiểu vì sao mục này tồn tại: FE có **49 màn và 0 lời gọi API thật**, tất cả chạy trên `mock/types.ts` do FE tự khai. BE mới viết DTO cho vài endpoint. Hai bên chưa từng gặp nhau ở đâu ngoài tài liệu.

Endpoint đầu tiên thực sự được nối — nhật ký thao tác — đã lệch **3 trên 6 trường**: BE trả `userId`, FE cần `actorName`; `resource` vs `target`; `ipAddress` vs `ip`. Không ai làm sai cả, hai bên chỉ đọc hai tài liệu khác nhau. Với hơn 200 FR còn lại, đây là loại nợ nhân lên chứ không cộng vào.

Nên từ giờ, mỗi endpoint merge phải kèm hai thứ:

1. **DTO nằm ở `libs/contracts`**, không phải type rời trong module.
2. **FE import từ client sinh ra**, không tự khai lại:

```bash
pnpm codegen:full   # DTO → spec OpenAPI → orval → libs/api-client
```

Lý do dùng đường ống này thay vì đối chiếu bằng mắt: lệch tên trường khi đó **gãy ở typecheck**, không gãy lúc chạy. Đối chiếu bằng mắt là thứ vừa để lọt 3 trường ở trên.

Trường nào không có trong hợp đồng thì đừng tự đặt. `vishop-api-contract.md` là nơi chốt tên; thiếu thì bổ sung vào đó trước, rồi mới viết code — sửa một dòng tài liệu rẻ hơn sửa hai repo.

## 7. Trước khi mở MR

```bash
pnpm nx affected -t lint test build typecheck --base=origin/main
```

Dùng `origin/main`, không phải `main`. Khi commit trên nhánh local đang track `main`, `--base=main` trỏ về đúng commit hiện tại, `affected` trả về rỗng và cổng gác bị bỏ qua trong im lặng.

Ticket đụng bảng dùng chung — tồn kho, package, giá vốn, event log — thì **gắn nhãn `shared-data`**. Nhãn này quyết định reviewer bắt buộc là tech lead. Automation không đọc được ngữ nghĩa ticket, nên nhãn phải gắn tay.

---

## 8. Đưa ticket sang In Review và Done

Luồng cột một chiều, cấm nhảy cóc: `To Do → In Progress → In Review → Done`.

**Vào In Review** cần: ticket có link tới MR. Không có MR thì không có gì để review.

**Vào Done** cần cả ba:

- Toàn bộ TIÊU CHÍ HOÀN THÀNH đã tick hết
- MR đã **merge**, không phải "đang chờ duyệt"
- Ticket FE: có ảnh chụp màn hình chạy thật, không nhận mockup
- Ticket có endpoint: DTO nằm ở `libs/contracts` và FE đã đổi import sang client sinh ra (mục 6) — không có bước này thì endpoint coi như chưa giao được cho ai

**Người bấm In Review → Done phải là người khác assignee.** Đây là chốt chặn quan trọng nhất của cả board. Không tự duyệt việc mình.

Review trả về thì về **In Progress**, không về To Do — về vạch xuất phát là xoá mất dấu vết đã làm tới đâu.

`Done` là bất biến. Nhầm thì tech lead mở lại và ghi lý do vào comment, không ai tự lùi.
