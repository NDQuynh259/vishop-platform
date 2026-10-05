# Thiết kế phân quyền — đánh giá đề xuất và hướng chốt

Đã chốt · 18/08/2026, cập nhật 19/08/2026

Tài liệu này trả lời hai câu hỏi đang bị gộp làm một: mô hình dữ liệu phân quyền nên đi tới đâu, và
vì sao người quản lý cấu hình quyền hay sai.

Tài liệu liên quan: `vishop-quyen-theo-gian-hang.md` (đã chốt mô hình gán vai trò theo gian hàng),
`vishop-permission-catalog.md` (danh mục quyền).

## Số liệu hiện tại

| Chỉ số                        | Giá trị                                 |
| ----------------------------- | --------------------------------------- |
| Quyền trong danh mục          | 75                                      |
| Nhóm nghiệp vụ                | 18                                      |
| Quyền được đánh dấu nguy hiểm | 7                                       |
| Quyền có câu giải thích       | 37                                      |
| Vai trò dựng sẵn              | 4 (Chủ tổ chức, Vận hành, Kho, Kế toán) |

## Hai vấn đề khác nhau

**A — Không diễn đạt được quyền riêng theo từng gian hàng.** Một thành viên mang đúng một vai trò cho
cả tổ chức. Câu "A là kế toán ở gian hàng O nhưng chỉ là nhân viên kho ở gian hàng P" không viết
ra được. Đây là vấn đề mô hình dữ liệu.

**B — 75 ô tích, người quản lý chọn không nổi và chọn sai.** Đây là vấn đề giao diện và cách đóng gói
danh mục, không phải vấn đề lưu ở đâu.

Cần tách bạch vì **đề xuất trong sơ đồ chỉ giải A**. Chuyển 75 quyền từ bảng này sang bảng khác không
làm 75 quyền dễ chọn hơn một chút nào. Nếu chỉ làm theo sơ đồ, sau vài tháng migration nặng, người
quản lý vẫn đứng trước đúng 75 ô tích như hôm nay.

## Đánh giá từng thành phần của đề xuất

| Thành phần                                   | Kết luận              | Lý do                                                                                                                                                                                                                                                                                                                                |
| -------------------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `ROLE_BINDING` (membership × role × scope)   | **Nhận, bản rút gọn** | Đúng thứ vấn đề A cần. Đã chốt ở `vishop-quyen-theo-gian-hang.md` dưới tên `tenant_member_roles`                                                                                                                                                                                                                                     |
| `SCOPE_NODE` + `ltree`                       | **Bỏ**                | Cây của ViShop sâu đúng hai tầng: tổ chức → gian hàng. `ltree` là extension Postgres, Prisma 7 không có kiểu cho nó, phải khai `Unsupported()` và viết SQL thô cho mọi truy vấn tổ tiên. Một cột `shopId` cho phép rỗng diễn đạt y hệt, có kiểu, index được, và join được bằng Prisma. `ltree` đáng giá từ 4–5 tầng tuỳ biến trở lên |
| `PRINCIPAL` (user / group / service_account) | **Bỏ ở đợt này**      | Hôm nay chỉ tồn tại một loại chủ thể. Thêm bảng này là thêm một join vào **mọi** truy vấn phân quyền, đổi lấy tính năng chưa ai yêu cầu. Thêm sau không phá cấu trúc: `tenant_member_roles.tenantMemberId` đổi thành `principalId` là một migration thẳng                                                                            |
| `GROUP_MEMBER`                               | **Để sau**            | Có giá trị thật khi một tổ chức vượt khoảng 20 người. Chưa có khách nào tới ngưỡng đó                                                                                                                                                                                                                                                |
| `IDENTITY`                                   | **Bỏ**                | Better Auth đã sở hữu bảng `accounts` cho đúng việc này. Dựng thêm một bảng danh tính là hai nguồn sự thật cho đăng nhập, và chúng sẽ lệch nhau                                                                                                                                                                                      |
| `PERMISSION` là bảng dữ liệu                 | **Bỏ**                | Một quyền là một nhánh code, không phải dữ liệu người dùng tạo ra. Danh mục hằng biên dịch bắt lỗi gõ sai ngay lúc build; bảng thì phải chạy mới biết. Bảng chỉ đáng khi cho phép tự định nghĩa quyền — mà điều đó không được phép, vì một quyền không có code đọc nó thì không bảo vệ được gì                                       |
| `ROLE_BINDING.condition` (jsonb, ABAC)       | **Bỏ dứt khoát**      | Xem mục dưới                                                                                                                                                                                                                                                                                                                         |
| `ROLE_BINDING.expires_at`                    | **Nhận**              | Một cột, rẻ, và có nhu cầu thật: nhân sự thời vụ mùa sale                                                                                                                                                                                                                                                                            |
| `ROLE.tenant_id` rỗng = vai trò hệ thống     | **Đã có**             | `roles.builtinKey` đang làm đúng việc này                                                                                                                                                                                                                                                                                            |
| `MEMBERSHIP.status`                          | **Đã có**             | `tenant_members.status`                                                                                                                                                                                                                                                                                                              |

### Vì sao bỏ điều kiện ABAC

Cho phép mỗi lần gán vai trò kèm một điều kiện tự do là đánh đổi một thứ không lấy lại được: khả
năng trả lời câu "ai đang xem được giá vốn?".

Hôm nay câu đó là một truy vấn. Có điều kiện tự do rồi thì phải lấy từng dòng gán, chạy thử điều kiện
với từng bối cảnh có thể, mới ra được danh sách — và bối cảnh là vô hạn. Hệ quả kéo theo:

- Màn hình không hiện đúng được ai có quyền gì, chỉ hiện được "có điều kiện".
- Nhật ký kiểm toán không giải thích được vì sao một thao tác bị từ chối.
- Phải tự thiết kế, tài liệu hoá và kiểm thử một ngôn ngữ điều kiện, kèm mọi rủi ro của việc chạy
  biểu thức do người dùng nhập.

Mọi nhu cầu đang thấy đều diễn đạt được bằng phạm vi gian hàng. Khi nào gặp một nhu cầu thật mà phạm
vi không diễn đạt nổi, mở lại quyết định này với ca cụ thể đó trên tay.

## Vấn đề B — hướng chốt: base role + lưới quyền theo nhóm

### Cách các hệ khác làm — đã tra lại trực tiếp, không suy đoán

Bản trước của tài liệu này trích Shopify dùng thang _View · Create and edit · Delete_ cho mọi nhóm.
**Sai** — đã tra lại trực tiếp `help.shopify.com` (mục Store permissions): Shopify chia quyền thành
**18 nhóm** (Orders, Products, Inventory, Customers, Finance…), và trong mỗi nhóm là **các quyền đặt
tên riêng, tích rời rạc** (`View`, `Manage order information`, `Edit orders`, `Apply discounts`…).
Không có bậc nào áp dụng đều cho cả 18 nhóm.

Tra thêm bốn hệ khác cho cùng một kết quả:

| Hệ         | Cách làm                                                                                    |
| ---------- | ------------------------------------------------------------------------------------------- |
| Shopify    | 18 nhóm × quyền đặt tên riêng, tích rời rạc                                                 |
| Zendesk    | Nhóm theo hạng mục (Tickets, People, Channels…) × quyền đặt tên riêng, tích rời rạc         |
| GitHub     | 5 base role xếp bậc (Read→Admin) + custom role **cộng thêm** quyền lẻ trên nền base         |
| GitLab     | Giống GitHub — custom role chỉ được cộng, không được bớt khỏi base                          |
| Salesforce | Base profile tối thiểu + Permission Set cộng thêm, lưu tái sử dụng, sửa một chỗ lan mọi nơi |

Không hệ nào dùng thang bậc áp đều cho mọi nhóm. Khuôn chung thật sự: **nhóm theo hạng mục + quyền
đặt tên riêng, tích rời rạc**, cộng thêm điểm xuất phát từ base role để đỡ tích từ đầu.

### Vì sao không chọn thang Xem · Thao tác · Quản lý

Thử dựng lại đúng ba vai trò dựng sẵn bằng thang này: vòng đầu **trượt cả ba**, thiếu 7 quyền vì vai
trò thật cắt ngang thang (ví dụ Kho cần `orders.note.edit` nhưng không tạo kiện — bậc Thao tác đòi cả
gói). Tách 5 quyền loại này ra ngoại lệ thì khớp lại được, nhưng tổng cộng vẫn còn **27/75 quyền phải
quyết riêng từng cái** — số ô tích giảm không nhiều, thứ giảm là điều phải học. Thêm nữa, bậc "Thao
tác" gộp chung nhiều hành động khác mức rủi ro làm một, thô hơn tích lẻ. Và nguồn trích dẫn ban đầu
(Shopify) hoá ra không dùng mô hình này. **Không chọn.**

### Vì sao không chọn năng lực (nhóm theo đầu việc)

Ý tưởng: gom quyền hay đi cùng nhau thành cụm theo đầu việc thật (ví dụ "Đóng gói & bàn giao" =
`orders.package.create` + `orders.package.ship` + `print.label` + …), người quản lý chọn 5–8 cụm thay
vì rà 75 ô.

Không chọn vì hai lý do:

- Bảng năng lực phải tự dựng từ danh mục quyền, **chưa đối chiếu với quy trình thật của khách** — tự
  nhận đây là giả định yếu nhất nếu đi hướng này. Gom sai một cụm là cấp thừa quyền cho mọi vai trò
  dùng cụm đó cùng lúc.
- Năng lực chỉ là **cách hỏi lúc tạo vai trò** — vai trò vẫn lưu danh sách khoá rời rạc như hôm nay
  (chốt ở mục dưới: nội dung của khách phải lưu nguyên văn, không tự đổi). Hệ quả: sau này sửa một
  năng lực, vai trò đã tạo từ nó **không tự cập nhật** — đúng vấn đề Salesforce từng gặp và đổi hướng
  bỏ (họ gọi là "profile sprawl": nhân bản khối quyền liên tục để chỉnh từng chút, ngày càng khó bảo
  trì).

### Hướng chốt: base role làm điểm xuất phát, lưới quyền theo nhóm là nền

Giữ nguyên lưới 75 ô / 18 nhóm đang chạy thật hôm nay (`permission-group-list.tsx`, đã có preset
"Không có / Chỉ xem / Toàn quyền" theo từng nhóm) làm nền — không đổi. Thêm ba thứ nhẹ:

1. **Điểm xuất phát từ base role.** Bấm "+ Vai trò mới" thì chọn luôn 1 trong 4 vai trò dựng sẵn (hoặc
   bắt đầu trống) làm gốc — lưới tự tick sẵn đúng quyền của vai trò gốc, admin chỉ cộng/bớt. Giống
   cách GitHub/GitLab dựng custom role từ một base role. Màn "Vai trò & quyền" hiện tại đã có nút
   "Nhân bản" làm gần đúng việc này (chọn vai trò có sẵn → nhân bản → sửa tiếp) — chỉ cần đưa lựa
   chọn đó lên đúng lúc bấm tạo mới, không phải xây từ đầu.
2. **Câu tóm tắt tiếng Việt.** Trên màn gán vai trò, hiện một câu thay cho bảng ma trận:

   > Xử lý đơn và in tem ở mọi gian hàng. **Không** xem được giá vốn, **không** sửa được tồn kho.

   Sinh từ danh sách quyền, không nhập tay. Người duyệt đọc một câu, không đọc một ma trận.

3. **Nhãn quyền nguy hiểm.** Danh mục đã đánh dấu 7 quyền nguy hiểm (`orders.package.ship`,
   `shops.disconnect`, `roles.manage`…) và `permission-group-list.tsx` đã hiện badge "Thận trọng" cho
   từng quyền — thêm đếm số quyền nguy hiểm ngay trên tiêu đề nhóm lúc thu gọn, để không phải mở ra
   mới biết nhóm đó có gì nhạy.

Không đổi schema, không endpoint mới — `GET/POST /api/v1/tenants/roles`,
`PATCH /api/v1/tenants/roles/:id/permissions` đã đủ.

### Vì sao đây là hướng ít phải sửa lại nhất khi hai đợt sau triển khai

- **Đợt 2 (phạm vi gian hàng).** Đúng lưới này dùng lại nguyên vẹn cho vai trò gán theo gian hàng —
  chỉ thêm một dòng chọn phạm vi phía trên. Năng lực hay thang đều phải thiết kế lại hoặc nhân đôi
  bảng ánh xạ cho từng gian hàng.
- **Đợt 3 (nhóm người dùng).** Khi có khách thật cần gộp nhanh nhiều quyền hay đi cùng nhau, lúc đó
  mới đầu tư một tầng kiểu Permission Set của Salesforce — cộng thêm module chọn nhanh phía trên
  lưới, không phải thay lưới.

Không giải trước một bảng phân loại 75 quyền chưa kiểm chứng — đúng khuyến nghị UX SaaS phổ biến:
bắt đầu tối giản, chỉ thêm tầng trừu tượng khi khách thật chạm giới hạn cụ thể của mô hình đơn giản.

## Chi phí thay đổi quyền về sau

Câu hỏi: thêm một quyền mới, hoặc đổi quyền mặc định của một vai trò dựng sẵn, thì phải sửa mấy chỗ?

### Đo trên code hiện tại

Thêm **một** khoá quyền hôm nay phải chạm **5 chỗ**:

| #   | Chỗ phải sửa                                             | Có gì chặn nếu quên                                     |
| --- | -------------------------------------------------------- | ------------------------------------------------------- |
| 1   | `permission-catalog.ts` — `PERM` và `PERMISSION_CATALOG` | Là nguồn, không quên được                               |
| 2   | Guard của endpoint dùng quyền đó                         | Biên dịch báo lỗi nếu gõ sai khoá                       |
| 3   | Hàm SQL `seed_builtin_roles` — cho tổ chức **mới**       | `builtin-roles-seed.spec.ts` đối chiếu SQL với danh mục |
| 4   | Migration bù cho tổ chức **đang chạy**                   | **Không có gì**                                         |
| 5   | `apps/app/lib/permissions.ts` — bản chép tay ở frontend  | **Không có gì**                                         |

Hai dòng cuối là chỗ thật sự đau, và đúng như Sếp cảm nhận.

**Chỗ 4:** quyền của ba vai trò dựng sẵn được chép thành dòng `role_permissions` riêng cho từng tổ
chức, lúc tổ chức được tạo. Thêm một quyền vào vai trò Vận hành thì tổ chức tạo **sau** có, tổ chức
tạo **trước** không có — và không bài kiểm tra nào phát hiện. Sai kiểu này im lặng: người dùng chỉ thấy
một nút không bấm được, không thấy thông báo nào.

**Chỗ 5:** frontend giữ một bản danh mục chép tay, trong khi endpoint `GET /api/v1/permissions/catalog`
đã tồn tại và không ai gọi. Hai bản lệch nhau thì màn phân quyền hiện thiếu hoặc thừa quyền so với
thứ backend thật sự kiểm.

### Chỗ đang làm đúng

Vai trò **Chủ tổ chức** không lưu dòng quyền nào. `effectivePermissionKeys()` trả về toàn bộ
danh mục lúc đọc. Thêm quyền mới là chủ tổ chức có ngay, mọi tổ chức, không migration.

Đó chính là hình mẫu cho ba vai trò còn lại.

### Đề xuất: tính vai trò dựng sẵn thay vì chép

Khách **không sửa được** vai trò dựng sẵn — `update-role-permissions` và `delete-role` đều chặn bằng
`role_is_builtin`. Nghĩa là nội dung của chúng hoàn toàn thuộc về ta, không có tuỳ biến nào của khách
để mà xung đột. Vậy không có lý do gì phải chép chúng xuống từng tổ chức.

Chuyển ba vai trò dựng sẵn sang tính lúc đọc, đúng cách chủ tổ chức đang làm:

|                                    | Trước   | Sau                      |
| ---------------------------------- | ------- | ------------------------ |
| Chỗ phải sửa khi thêm một quyền    | 5       | **2** (danh mục + guard) |
| Migration bù cho tổ chức đang chạy | Mỗi lần | Không bao giờ            |
| Bản chép trong SQL                 | Có      | Không                    |
| Bài kiểm tra chống lệch SQL        | Cần     | Bỏ được                  |

Đổi lấy: một migration một lần, xoá các dòng `role_permissions` của vai trò dựng sẵn, và hàm seed chỉ
còn tạo bốn dòng `roles`. Không mất dữ liệu của khách vì đó không phải dữ liệu của khách.

Cộng thêm: frontend đã bỏ khoá quyền chép tay — kiểu `PermissionKey` giờ lấy thẳng từ client sinh ra
bởi orval (`RoleResponseDtoPermissionKeysItem`), nên một khoá bị đổi tên hay xoá ở backend báo lỗi
biên dịch ngay ở frontend. Một bài kiểm tra (`tests/unit/permissions.test.ts`) khoá chiều còn lại:
backend thêm khoá mới mà danh mục hiển thị (`PERMISSION_GROUPS`) chưa cập nhật thì test đỏ. Nhãn,
gợi ý và cờ nguy hiểm của từng quyền vẫn đang chép tay ở frontend (không đổi qua fetch runtime từ
`GET /api/v1/permissions/catalog`, vì endpoint đó yêu cầu đăng nhập nên không gọi được lúc build, và
đổi sang fetch lúc chạy sẽ động vào khoảng mười tệp cùng bộ test đang chạy tốt của màn phân quyền để
đổi lấy lợi ích nhỏ) — rủi ro còn lại chỉ là nhãn hiển thị lệch chữ, không phải lệch quyền.

### Khách sửa quyền thì sao

Rẻ, và không đổi.

| Việc                        | Chi phí                                                                          |
| --------------------------- | -------------------------------------------------------------------------------- |
| Khách sửa vai trò tuỳ chỉnh | Một endpoint, ghi lại `role_permissions` của đúng vai trò đó. Không lan sang đâu |
| Khách tạo vai trò mới       | Một endpoint                                                                     |
| Khách sửa vai trò dựng sẵn  | Không làm được — phải nhân bản rồi sửa bản sao                                   |

### Quy tắc chung cho mọi tầng tiện ích thêm sau này

Base role ở Đợt 1 chỉ là **điểm xuất phát lúc tạo**; vai trò tuỳ chỉnh vẫn lưu danh sách khoá của
riêng nó ngay sau khi tạo, không còn liên hệ gì với vai trò gốc. Nguyên tắc này áp cho mọi tầng tiện
ích có thể thêm sau (base role, và sau này nếu có permission set kiểu Salesforce): tầng tiện ích chỉ
gợi ý lúc tạo/sửa, không bao giờ được phép âm thầm lan quyền vào vai trò đã tồn tại — nếu không, một
quyết định sản phẩm của ta sẽ âm thầm cấp thêm quyền cho mọi khách hàng, điều không được phép trong
một hệ phân quyền.

Quy tắc chung để về sau không phải cân nhắc lại từng ca:

- Nội dung **của ta** (danh mục quyền, vai trò dựng sẵn) → tính lúc đọc, lan tự động.
- Nội dung **của khách** (vai trò tuỳ chỉnh, kể cả khi tạo từ điểm xuất phát base role) → lưu nguyên
  văn ngay sau khi tạo, không bao giờ tự đổi theo vai trò gốc nữa.

## Hướng chốt

| Đợt | Việc                                                                                           | Giải vấn đề             | Đổi schema    | Rủi ro             |
| --- | ---------------------------------------------------------------------------------------------- | ----------------------- | ------------- | ------------------ |
| 0   | Tính vai trò dựng sẵn thay vì chép; frontend lấy kiểu `PermissionKey` từ client sinh bởi orval | Chi phí thay đổi về sau | Xoá dòng thừa | Thấp — **đã xong** |
| 1   | Base role làm điểm xuất phát + lưới quyền theo nhóm (đã có) + câu tóm tắt + nhãn nguy hiểm     | B                       | Không         | Thấp               |
| 2   | `tenant_member_roles` theo `vishop-quyen-theo-gian-hang.md`                                    | A                       | Có            | **Cao — đã xong**  |
| 3   | Nhóm người dùng, hạn dùng của vai trò                                                          | —                       | Có            | Trung bình         |

Đợt 0 nhỏ và đi trước tất cả: nó hạ số chỗ phải sửa mỗi khi đụng vào quyền từ 5 xuống 2, nên các đợt
sau đều rẻ hơn. Đã xong — xem `libs/contracts/src/lib/permissions/permission-catalog.ts` và
`effectivePermissionKeys()`.

Đợt 2 — `tenant_member_roles` — cũng đã xong, sớm hơn dự kiến trong kế hoạch này, theo đúng thiết kế
ở `vishop-quyen-theo-gian-hang.md`. Vì Đợt 1 và Đợt 2 đổi chỗ thứ tự thực thi so với đề xuất ban đầu,
màn hình gán quyền theo gian hàng ở Đợt 2 hiện đang dùng đúng lưới 75 ô của Đợt 1 làm nền — không có
tầng năng lực đứng giữa như phương án cũ hình dung.

Đợt 1 rẻ, không có migration, và là thứ người dùng cảm nhận được ngay. Đợt 3 chỉ mở khi có khách thật
yêu cầu gộp nhanh quyền hay đi cùng nhau.

## Phải nói trước với khách

Hai giới hạn không phân quyền nào chặn được, vì dữ liệu phía sau không thuộc gian hàng nào:

- **Tồn kho** là một con số chung cho cả tổ chức. Nhân viên chỉ làm gian hàng O vẫn xuất kho
  được SKU đang bán ở gian hàng P.
- **Giá vốn** nằm trên SKU kho, không nằm trên gian hàng.

Muốn tách thật thì phải tách kho theo gian hàng — đó là đổi mô hình sản phẩm, không phải đổi phân
quyền.

## Chỗ còn chưa chắc

Đợt 1 không giải quyết gốc rễ "75 ô nhiều quá" — chỉ giảm điểm khởi đầu (chọn base role thay vì tích
từ ô trống). Số quyết định khi cần tuỳ biến sâu không giảm nhiều. Nếu sau này khách thật đề nghị gộp
nhanh nhiều quyền hay đi cùng nhau, đó là tín hiệu để mở Đợt 3 kiểu permission set — lúc đó thiết kế
với dữ liệu thật trong tay thay vì đoán trước, đúng bài học rút ra từ việc bỏ hướng năng lực ở trên.

18 nhóm hiện tại cũng nên soát lại độ dài: nhóm nào vượt quá khoảng 10 quyền (`inventory`, `org`) nên
cân nhắc tách nhỏ để lưới còn dễ quét mắt — việc này độc lập với Đợt 1 và có thể làm bất cứ lúc nào.
