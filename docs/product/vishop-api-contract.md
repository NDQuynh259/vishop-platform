# ViShop — API Contract nội bộ (FE ↔ BE) v1.0

**Nguồn đối chiếu:** `vishop-build-spec-v1.2.md` và `vishop-function-list.md` (249 FR đã audit) cho phần nghiệp vụ; code Java hiện tại trong `vishop-platform` cho xác thực và tenant bootstrap (§0, §9.3); các API nghiệp vụ chưa port vẫn là đặc tả từ nguồn `api-vishop`. Hai nguồn này không ngang hàng: nghiệp vụ nói **cái gì**, repo nói **viết ra sao** — và repo thắng ở mọi tranh chấp về hình dạng request/response, mã lỗi, phân trang và xác thực, trừ các đoạn được đánh dấu rõ là quyết định contract chưa hiện thực hoá.
**Phạm vi:** 6 nhóm theo `ChannelAdapter` §2.1 — AuthProvider, ProductProvider, OrderProvider, FulfillmentProvider, InventoryProvider, FinanceProvider. Các module không nằm sau adapter (M08 Quét mã, M09 Sau bán, M11 Affiliate/Livestream, M13 Báo cáo, M14 Cảnh báo, M15 Billing) là API nội bộ thuần — hợp đồng riêng, không thuộc file này.
**Chốt trước khi code:** contract dưới đây giả định Capability Matrix §9 đã xác minh cho từng FR P0 liên quan. Riêng nhóm FinanceProvider/M12 còn treo theo câu hỏi #1 ở §9 (Finance API trả settlement theo đơn hay chỉ theo kỳ) — nếu kết quả xác minh là "chỉ theo kỳ", shape ở mục 6 phải sửa lại trước khi FE bắt tay code màn đối soát.

---

## 0. Quy ước chung

Xác thực và tenant bootstrap bám code Java IAM hiện tại. Những quy ước nghiệp vụ chưa port được giữ như đặc tả sản phẩm; không suy ra rằng module NestJS hoặc endpoint nguồn đang chạy trong repo này.

### 0.1 Đường dẫn và xác thực

- **Base path:** `/api/v1/...`; auth được triển khai bởi `AuthController` trong `iam-service`.
- **JWT stateless:** login/register trả `accessToken`, `refreshToken`, `user`, `tenantId`, `roles`, `permissions` trong body. API được bảo vệ nhận Bearer access token. Không dùng Better Auth hoặc cookie đăng nhập.
- **Refresh:** `POST /api/v1/auth/refresh` nhận `{refreshToken}`, không cần access token còn hạn. Access mặc định 15 phút, refresh 7 ngày. Refresh không kéo dài hạn tuyệt đối ban đầu; token refresh cũ vẫn hợp lệ đến hết hạn.
- **Logout:** `POST /api/v1/auth/logout` với Bearer access token trả 204; client xoá cả hai JWT. Server không lưu trạng thái thu hồi token.
- **Tenant context:** lấy từ access JWT đã xác minh. Đổi tenant bằng `POST /api/v1/auth/switch-tenant` với Bearer access token và `{tenantId,refreshToken}`; hai token phải cùng user và user phải có membership hoạt động. Client thay cặp token sau khi đổi tenant. `X-Tenant-Id` không override tenant đã ký trong JWT.
- **ID:** dùng UUID; ID dữ liệu mới được sinh bằng UUID v7. Spring bind UUID trên route; không khẳng định mọi route từ chối UUID v4 nếu code chưa có validation tương ứng.

Các ví dụ nghiệp vụ chưa port bên dưới là đặc tả sản phẩm, không phải bằng chứng endpoint đã tồn tại trong Java. Khi tích hợp xác thực hoặc tenant bootstrap, dùng code IAM và [IAM README](../../services/iam-service/README.md) làm nguồn đối chiếu.

### 0.2 Hình dạng response

- **Tên field JSON là camelCase.** `platformOrderNo`, `costPrice`, `lastSyncedAt` — không phải `platform_order_no`. Quy ước này áp cho cả request body và query param. Riêng `code` trong thân lỗi là snake_case (mục 0.3).
- **2xx của một tài nguyên đơn trả thẳng tài nguyên đó**, không bọc `{ data }`, không bọc `{ data, meta }` (kiểu Stripe).
- **Collection độc lập trả `ListResponseDto`** (`libs/contracts/src/lib/dto/list-response.dto.ts`), dựng qua `toCursorListResponse()`:

```jsonc
{
  "object": "list",
  "url": "/api/v1/orders",
  "data": [],
  "pageInfo": {
    "hasNextPage": true,
    "nextCursor": "MjAyNi0wOC0wNlQwMjowMDowMC4wMDBaOjAxOTczMmRiLTYwMTAtN2Y3Zi1iNDY0LTBkMjBjNWUzYThmOQ",

    // Chỉ có trên endpoint bật đếm — xem gạch đầu dòng thứ hai bên dưới:
    // "totalCount": 234,
    // "totalPages": 12
  },
}
```

- **Mặc định mọi collection dùng cursor:** `?limit=20&cursor=<pageInfo.nextCursor của trang trước>`. `limit` trong khoảng 1–100, mặc định 20. Cursor là chuỗi mờ do endpoint định nghĩa — client không được tự dựng hoặc giải mã; đổi filter/sort thì phải bỏ cursor và bắt đầu lại.
- **Dual-mode — cursor hoặc số trang:** một số collection nhận thêm `?page=N` (1–10 000, không có giá trị mặc định). **Có `page` là chọn chế độ số trang**: response là `OffsetListResponseDto` với `pageInfo: { hasNextPage, total, page, limit, totalPages }`, không có `nextCursor`, và `cursor` gửi kèm bị bỏ qua. **Không gửi `page`** thì giữ nguyên envelope cursor ở trên và không có tổng. `total` đếm đúng tập đang lọc — cùng bộ lọc, cùng phạm vi gian hàng, không phụ thuộc thứ tự sắp xếp; `page` vượt `totalPages` trả `data: []`. Sau khi chạy codegen, `pageInfo` của các endpoint này là union — đọc `nextCursor` phải thu hẹp kiểu bằng `"nextCursor" in pageInfo`.
  - Đơn hàng & vận chuyển: `GET /orders`, `GET /orders/missing-stock`, `GET /packages`, `GET /shipments`, `GET /shipments/issues`, `GET /pick-waves`, `GET /returns`, `GET /print-jobs/history`, `GET /scan-sessions`.
- **`totalCount` / `totalPages` là tuỳ chọn của từng endpoint.** Endpoint nào có màn cần thanh phân trang thì trả thêm hai trường ấy trong `pageInfo`; endpoint nào không thì **vắng hẳn key**, không phải `null` — vắng nghĩa là "danh sách này không báo tổng", còn `null` sẽ đọc thành "đã đếm và ra rỗng". Đếm là chạy lại bộ lọc của trang trên cả tập, nên nó là một lựa chọn có giá chứ không phải mặc định. `totalPages` = `ceil(totalCount / limit)`, bằng 0 khi không có kết quả nào. Hai số này để **đánh số** trang, không để nhảy tới trang — lật trang vẫn tuần tự qua `nextCursor`. Số liệu tổng hợp nhiều chiều vẫn đi qua endpoint summary/report riêng.
  - Đang bật: `GET /sku-mappings`, `GET /sku-mappings/candidates`, `GET /sku-mappings/platform-skus/{platformSkuId}/pending-packages`.
- **Sắp xếp:** `?sort=createdAt:desc,name:asc` — CSV, mỗi phần tử là `field:direction`.
- **Lọc:** key=value đơn giản (`?status=active`). Bộ lọc phức tạp dùng query DTO riêng, không dùng cú pháp `?filter[field][op]=`.
- **`X-Request-Id` có trên mọi response**, do middleware sinh — controller không tự đặt.

### 0.3 Lỗi — RFC 9457 Problem Details

Mọi lỗi trả `Content-Type: application/problem+json` do global exception filter dựng. **Không có endpoint nào tự nặn thân lỗi.**

```json
{
  "type": "/errors/business-rule-violation",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "SKU combo không đẩy tồn trực tiếp — tồn được tính tự động theo SKU thành phần.",
  "instance": "/api/v1/inventory/push",
  "code": "inv_combo_not_directly_updatable",
  "requestId": "01973..",
  "timestamp": "2026-08-07T04:31:11.469Z"
}
```

- **`code` là snake_case và bắt buộc lấy từ `ERROR_CODES`** (`libs/contracts/src/lib/errors/error-codes.ts`) — không dùng chuỗi tự đặt, vì client khoá i18n theo giá trị này. Mọi mã lỗi nghiệp vụ ViShop trong tài liệu này phải được **thêm vào `ERROR_CODES` trước khi implement**; tên trong bảng dưới các mục sau là tên đề xuất, chưa có trong repo.
- **Handler chọn `kind`, không chọn HTTP status.** Ném `DomainException` từ `@nestjs-fastify-nx/core` với một trong năm `kind`; transport tự gán status. Cùng handler chạy dưới REST, GraphQL và listener của scheduler nên không được gắn cứng status.

  | `kind`       | Status | Dùng khi                                  |
  | ------------ | ------ | ----------------------------------------- |
  | `malformed`  | 400    | Không phân tích nổi đầu vào (cursor hỏng) |
  | `validation` | 422    | Phân tích được nhưng sai luật — mặc định  |
  | `not_found`  | 404    | Không có, hoặc không được phép biết là có |
  | `conflict`   | 409    | Trùng khoá, phiên bản cũ, việc đang chạy  |
  | `forbidden`  | 403    | Đã đăng nhập nhưng không đủ quyền         |

- **`permanent` mặc định `true`.** Đặt `false` chỉ khi một lần thử sau có thể thắng (`conflict`) — nếu không, `OutboxRelayService` sẽ đốt hết ngân sách retry cho một lỗi không bao giờ tự hết.
- **Lỗi kiểm tra đầu vào đi qua `errors[]`**, mỗi phần tử `{ path, code, message, messageKey?, rule?, constraint? }`. Không echo giá trị bị từ chối.
- **Mọi 5xx đều là thông điệp chung ở mọi môi trường.** Nguyên nhân đầy đủ chỉ nằm trong log có cấu trúc và Sentry, tra theo `requestId`.

#### Mã lỗi gốc của TikTok đi đường nào — đã chốt, không mở rộng thân lỗi

`buildProblemDetails` chỉ phát ra đúng `type / title / status / detail / instance / code / requestId / timestamp / errors / checks` và **loại bỏ mọi thành viên mở rộng lạ**, nên `platformErrorCode` không có chỗ trong thân problem+json. **Không mở rộng `ProblemDetailsBody` để nhét nó vào.** Ba lý do:

1. §6.5 build-spec yêu cầu **giữ nguyên mã lỗi gốc trong nhật ký**, không yêu cầu trả nó cho client. Đọc kỹ thì không có mâu thuẫn nào để phải xử lý.
2. Mã lỗi của TikTok là chi tiết của một nhà cung cấp phía sau. Đưa nó vào contract công khai là buộc FE viết nhánh theo mã của bên thứ ba, và mã đó đổi thì FE vỡ — đúng thứ mà lớp ChannelAdapter sinh ra để chặn.
3. Sửa `libs/contracts` cho một nhu cầu hiển thị là làm hỏng một hợp đồng dùng chung cho REST, GraphQL và scheduler.

Mã gốc vì vậy đi ba đường, đủ cho cả vận hành lẫn hỗ trợ:

| Đường                    | Nơi ghi                                                                                                                                       | Ai đọc                        |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------- |
| Nhật ký nghiệp vụ        | `inventoryPushLog.errorReason` (FR-05-15), `failedPackages[].platformErrorCode` của print-job, `results[].error.platformErrorCode` của lô RTS | Seller, ngay trên màn nhật ký |
| Log có cấu trúc + Sentry | tra theo `requestId` trả về trong chính thân lỗi                                                                                              | Dev, vận hành                 |
| `event_log`              | khi lỗi làm đổi trạng thái bản ghi                                                                                                            | Điều tra lệch dữ liệu         |

Ba nơi ở cột giữa của dòng đầu đều là **response 2xx của ViShop**, không phải problem+json — nên chúng được phép mang `platformErrorCode` mà không đụng gì tới `ProblemDetailsBody`. Đây chính là lý do lỗi từng phần trong lô được thiết kế trả 2xx (§0.4).

Hệ quả cho FE: khi nhận problem+json, đừng đi tìm mã gốc trong thân. Hiển thị `detail`, và cho người dùng một lối mở nhật ký tương ứng nếu cần xem mã sàn.

### 0.4 Quy ước nghiệp vụ

- **Phân quyền cấp trường (FR-01-06):** `costPrice` và giá trị tồn theo giá vốn cần `data.cost.view`; `profit`, `profitMargin` cần `data.profit.view`; `buyer.phone`, `buyer.fullAddress` cần `data.buyer_pii.view`. `buyer.name` không thuộc phạm vi quyền này. Không có quyền → **field bị lược khỏi payload**, không trả giá trị thật rồi để FE tự che. Cách làm theo repo là **ánh xạ DTO tường minh** — handler chiếu thực thể sang một DTO không khai báo cột nhạy cảm; **không dùng `ClassSerializerInterceptor` / `@Exclude`**.
- **Idempotency:** mọi POST phát sinh ghi ra sàn (RTS, đẩy tồn, lấy tem) gửi header `Idempotency-Key`. Cơ chế đã có sẵn ở backend dưới dạng **plugin Fastify** (`apps/api/src/common/idempotency/register-idempotency.ts`, cache Redis DB 5), không phải interceptor Nest: lần gọi 2xx đầu tiên được phát lại nguyên văn kèm header `Idempotent-Replayed: true`. Dùng lại key với thân khác → `422 idempotency_key_mismatch`; trùng lúc đang chạy → `409 idempotency_key_conflict`. Redis chết thì mở cổng cho qua, không chặn.

  Cơ chế này độc lập với FR-00-03: FR-00-03 khoá `(platform, shopId, externalId)` cho bản ghi **đồng bộ về từ sàn** — chiều dữ liệu ngược lại. Hai thứ không thay thế nhau.

- **Ánh xạ tên khi cột DB khác tên field JSON:** ngoài các trường hợp này, tên field JSON lấy nguyên tên cột DB.

  | Cột DB                           | Field JSON                                                           | Quan hệ                                                                                                                                                                            |
  | -------------------------------- | -------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
  | `merchantSku.avgCost`            | `costPrice` (SKU) / `costOfGoods` (tổng theo đơn = Σ `avgCost` × SL) | Cùng khái niệm giá vốn; `avgCost` phản ánh phương pháp tính (bình quân gia quyền), `costPrice`/`costOfGoods` là tên nghiệp vụ theo ngữ cảnh SKU đơn lẻ hay tổng theo đơn           |
  | `order.externalOrderId`          | `platformOrderNo`                                                    | Cùng giá trị, đổi tên cho seller-facing (unique constraint áp trên cột DB)                                                                                                         |
  | `order.buyerName`                | `buyer.name`                                                         | Cùng giá trị, gộp vào object `buyer` — không bị chặn theo FR-01-06 (khác hai dòng dưới)                                                                                            |
  | `order.buyerPhone`               | `buyer.phone`                                                        | Field-level permission FR-01-06; giá trị có thể đã bị TikTok che một phần khi đơn dùng platform logistics, không phải lỗi hiển thị (§3)                                            |
  | `order.buyerFullAddress`         | `buyer.fullAddress`                                                  | Field-level permission FR-01-06; lưu nguyên chuỗi địa chỉ gốc từ sàn, không tách thành phần                                                                                        |
  | `orderFee.source = "settlement"` | `dataStatus = "reconciled"`, `feeSource = "settlement"`              | Cùng trạng thái "đã có số thật sau payout". `dataStatus` ở mức response và là nhãn hai mức của FR-10-03; `feeSource` nói rõ ước tính dựa trên số sàn khai hay biểu phí ViShop (§6) |

- **Thao tác hàng loạt không bao giờ hỏng cả lô vì một phần tử.** BR-06-06 nói ở cấp package, nguyên tắc áp cho mọi lô — RTS, đẩy tồn, in tem, bàn giao. Response luôn trả mảng kết quả từng phần tử. Lưu ý: một lô có phần tử lỗi vẫn là **2xx** (kết quả nằm trong thân), không phải problem+json — problem+json chỉ dành cho lỗi của chính request đó.
- **Rate limit ngược ra FE:** `429` với `code: "rate_limited"` kèm `Retry-After` khi governor (§2.6 build-spec) từ chối nhận thêm việc cho shop đó. Không bao giờ để request treo chờ hạn mức.
- **Timeout:** handler vượt `HTTP_REQUEST_TIMEOUT_MS` (mặc định 30 giây) bị cắt với `504 request_timeout`. Node không huỷ được promise nên việc vẫn chạy tiếp ở server — chỉ thời gian chờ của client là có biên. Mọi việc dài hơn ngưỡng này (in 300 tem, đồng bộ khởi tạo, đẩy tồn toàn shop) phải là job chạy nền có endpoint hỏi tiến độ, không phải một request đồng bộ.
- **Tiền tệ:** số nguyên theo **đơn vị nhỏ nhất của loại tiền (mũ ISO 4217)**, không field nào là số thực (BR-00-03). `1234` với `USD` là $12.34; VND mũ 0 nên con số chính là số tiền. Kiểu cột là `BigInt` vì `int4` tràn ở khoảng 21,4 triệu IDR — một đơn hàng bình thường ở thị trường đó. Mọi response tiền đi kèm `currency` dạng ISO 4217.

---

## 1. AuthProvider — M02 Kết nối gian hàng & Authorization

| Method | Path                                 | FR            | Ghi chú                                                                        |
| ------ | ------------------------------------ | ------------- | ------------------------------------------------------------------------------ |
| POST   | `/api/v1/shops/connect`              | FR-02-01 (P0) | Khởi tạo OAuth Open API                                                        |
| GET    | `/api/v1/shops`                      | FR-02-03 (P0) | Trạng thái, hạn token, lần sync cuối, số đơn/tồn đã sync                       |
| PATCH  | `/api/v1/shops/{shopId}`             | FR-02-02 (P0) | Đổi nickname, unique trong tenant                                              |
| POST   | `/api/v1/shops/{shopId}/reauthorize` | FR-02-05 (P0) | Kích hoạt lại OAuth khi token hỏng                                             |
| DELETE | `/api/v1/shops/{shopId}`             | FR-02-09 (P0) | Ngắt kết nối, giữ dữ liệu lịch sử, dừng sync + đẩy tồn                         |
| GET    | `/api/v1/shops/{shopId}/sync-logs`   | FR-02-08 (P0) | Loại dữ liệu, thời điểm, số bản ghi, số lỗi, lý do                             |
| POST   | `/api/v1/shops/{shopId}/sync`        | FR-02-10 (P1) | Đồng bộ thủ công theo loại dữ liệu                                             |
| GET    | `/api/v1/shops/{shopId}/health`      | FR-02-11 (P1) | Đèn xanh/vàng/đỏ                                                               |
| GET    | `/api/v1/authorizations`             | FR-02-12 (P0) | Danh sách uỷ quyền theo gian hàng kèm `grantedScopes` và capability set suy ra |
| POST   | `/api/v1/authorizations/connect`     | FR-02-13 (P0) | Khởi tạo luồng uỷ quyền gian hàng, trả URL trang uỷ quyền của TikTok           |

**Request/response rút gọn:**

```json
// POST /api/v1/shops/connect → 200
{ "oauthUrl": "https://auth.tiktok-shops.com/..." }

// GET /api/v1/shops?limit=20 → 200
{
  "object": "list",
  "url": "/api/v1/shops",
  "data": [{
    "id": "01973...",
    "shopId": "string",
    "nickname": "string",
    "status": "connected | reconnect_required | syncing_initial | disconnected",
    "tokenExpiresAt": "2026-08-20T00:00:00Z",
    "lastSyncedAt": "2026-08-06T02:00:00Z",
    "syncedOrderCount": 1200,
    "syncedSkuCount": 340
  }],
  "pageInfo": { "hasNextPage": false, "nextCursor": null }
}
```

Danh sách gian hàng vẫn dùng cùng contract cursor dù thường có ít phần tử; client không cần một nhánh xử lý riêng.

**Mã lỗi nghiệp vụ** — tên đề xuất, phải thêm vào `ERROR_CODES` trước khi implement.

| `code`                 | `kind` → status    | Nguồn    | Tình huống                       | `detail` seller-facing                                                                           | FR/BR    |
| ---------------------- | ------------------ | -------- | -------------------------------- | ------------------------------------------------------------------------------------------------ | -------- |
| `shop_already_linked`  | `conflict` → 409   | internal | Shop TikTok này đã ở tenant khác | "Gian hàng này đã được kết nối ở một tài khoản ViShop khác. Liên hệ hỗ trợ nếu đây là nhầm lẫn." | BR-02-01 |
| `shop_limit_reached`   | `validation` → 422 | internal | Vượt số gian hàng theo gói       | "Gói hiện tại cho phép tối đa {n} gian hàng. Nâng gói để kết nối thêm."                          | BR-02-02 |
| `token_refresh_failed` | `conflict` → 409   | platform | Tự gia hạn token thất bại        | "Không thể tự gia hạn kết nối — vào Kết nối lại để cấp quyền mới."                               | FR-02-05 |

`token_refresh_failed` đặt `permanent: false`: kết nối hỏng vì token là thứ một lần thử sau có thể thắng, khác với vượt hạn mức gói.

> **Ghi chú cho FE (sửa 07/08/2026):** cảnh báo hết hạn uỷ quyền (FR-02-06) đi bằng webhook. Mọi uỷ quyền của ViShop đều là scope `seller`, mà scope này có webhook deauthorization/token expiration — không còn scope nào phải polling. Vòng quét dự phòng 6 giờ ở BE chỉ để bắt trường hợp webhook rơi, FE không cần biết đến nó. _(Bản trước mô tả `creator`/`partner` phải polling — mô hình 3 role đó đã bị bỏ, xem build-spec §2.2.)_
> **Ghi chú cho FE (thêm 14/08/2026, FR-02-06):** WebSocket `/ws`, sự kiện `notification.created` —
> payload là **`{ notificationId, tenantId }` thuần, không kèm title/body/actionUrl**. Lý do: socket
> chỉ join theo `userId` (room `user:<userId>`), không biết tenant nào đang active trên tab đó — một
> user thuộc nhiều tenant sẽ nhận cùng một sự kiện trên mọi tab/socket đang mở bất kể tab đó đang xem
> tenant nào. Nếu gửi kèm nội dung, tab đang mở tenant B sẽ thấy nội dung của tenant A. FE nhận sự
> kiện này chỉ như một tín hiệu "có gì đó mới" — nếu `tenantId` khớp tenant đang active thì gọi lại
> `GET /api/v1/notifications` (đã tự scope theo tenant hiện tại) để lấy nội dung thật; nếu không khớp
> thì bỏ qua (hoặc chỉ cập nhật badge số lượng chưa đọc cho tenant đó nếu FE có menu chuyển tenant).
> Không hiển thị trực tiếp field nào trong payload WS lên UI.
> **Ghi chú cho FE (thêm 12/08/2026, sửa 13/08/2026, FR-02-04):** trước khi `GET /api/v1/shops`
> (FR-02-03) tồn tại, tiến độ đồng bộ khởi tạo poll qua
> `GET /api/v1/tiktok/shops/{shopId}/initial-sync` (route tạm trên `TikTokController` hiện có,
> không phải `/api/v1/shops/*`, yêu cầu quyền `shops.view`) →
> `200 { status, syncStatus, progress: { products: {total, done, failed}, orders: {total, done,
failed} }, startedAt, completedAt }`, theo đúng khuôn `progress: {total, done, failed}` đã dùng ở
> §4.1 — một sản phẩm/đơn lỗi khi upsert bị đếm vào `failed` và bỏ qua, không làm hỏng cả lô, cùng
> nguyên tắc với FR-03-12. `status` là chuỗi nội bộ `PENDING_INITIAL_SYNC | ACTIVE | REVOKED |
EXPIRED | DISCONNECTED` (trạng thái kết nối của shop) — **không** phải trạng thái của lượt sync
> này, đó là việc của field mới `syncStatus`: `QUEUED | PROCESSING | COMPLETED |
FAILED`, cùng enum với `MerchantSkuImport.status`. Tách hai field vì gộp
> chung sẽ khiến "đang chạy", "chưa bắt đầu" và "đã dead-letter sau 6 lần thử" đọc ra cùng một giá
> trị `PENDING_INITIAL_SYNC` — FE không có cách phân biệt "đang chạy" với "đã treo" nếu chỉ nhìn
> `status`. `status` cũng không phải enum `syncing_initial` ở bảng `GET /api/v1/shops` phía trên —
> khi FR-02-03 xây `/api/v1/shops`, việc gộp field này vào response đó (thay vì trỏ FE sang hai
> endpoint) là việc của ticket đó, chưa làm ở đây.
> **Ghi chú cho FE (thêm 15/08/2026, FR-02-09):** `POST /api/v1/tiktok/shops/{shopId}/disconnect`
> (quyền `shops.disconnect`) → `200 { shopId, status: 'DISCONNECTED' }`. Sau khi gọi, shop biến
> mất hoàn toàn khỏi mọi danh sách/API của tenant (kể cả endpoint poll `initial-sync` ở trên —
> trả `404 tiktok_shop_not_connected`) — tương đương xoá mềm, không phải chỉ đổi badge trạng thái.
> Gọi lại route này lần 2 trên cùng shop cũng trả `404 tiktok_shop_not_connected`, không có mã lỗi
> riêng cho "đã ngắt kết nối trước đó". Kết nối lại đi qua đúng luồng OAuth ở FR-02-01
> (`GET /api/v1/tiktok/oauth/authorize` → callback), không phải endpoint riêng — sau khi thành
> công shop hiện lại với cùng `shopId`, giữ nguyên lịch sử đơn/sản phẩm đã đồng bộ trước đó.
> **Ghi chú cho FE (thêm 17/08/2026, FR-02-08):** trước khi `GET /api/v1/shops/{shopId}/sync-logs`
> (FR-02-03) tồn tại, nhật ký đồng bộ đọc qua route tạm
> `GET /api/v1/tiktok/shops/{shopId}/sync-logs` (trên `TikTokController`, không phải
> `/api/v1/shops/*`, quyền `shops.view`) — cùng mẫu route tạm với `initial-sync` ở trên. Query
> params: `dataType` (`products | orders`, optional), `from`/`to` (ISO timestamp, optional),
> `cursor`/`limit` (cursor-only, như mọi list endpoint khác). Response là `ListResponseDto` chuẩn,
> mỗi phần tử: `{ id, dataType, syncKind, result, processedCount, failedCount, errorReason?,
startedAt, completedAt, createdAt }` — `syncKind` là `initial | catchup | periodic | manual`
> (`manual` bắt đầu phát sinh dòng từ FR-02-10, xem ghi chú riêng bên dưới), `result` là
> `completed | failed`. Một shop đã bị disconnect (FR-02-09) trả `404 tiktok_shop_not_connected`
> giống mọi route theo `shopId` khác — log lịch sử của nó không đọc được cho tới khi kết nối lại.
> **Ghi chú cho FE (thêm 20/08/2026, FR-02-10):** đồng bộ thủ công theo yêu cầu **không phải 1
> endpoint chung nhận `dataType` trong body** — bảng route ở trên (`POST /api/v1/shops/{shopId}/sync`)
> là đích cuối cùng khi `/api/v1/shops` (FR-02-03) tồn tại; cho tới lúc đó, chọn loại dữ liệu là
> chọn đúng route:
>
> - **Sản phẩm**: dùng route đã có sẵn `POST /api/v1/shops/{shopId}/platform-products/sync`
>   (FR-03-01/04, quyền `products.resync`, scope TikTok `seller.product.basic`) — không phải
>   endpoint mới, chỉ khác ở chỗ log sync giờ đã gắn đúng `syncKind: 'manual'`.
> - **Đơn hàng**: route mới `POST /api/v1/tiktok/shops/{shopId}/orders/sync` (route tạm dưới
>   `/tiktok/*`, cùng mẫu với `initial-sync`/`sync-logs` ở trên — sẽ dời khi FR-02-03 xây xong),
>   quyền `shops.sync.run`, scope TikTok `seller.order.info`. `202 { jobId, status: 'queued' }`.
>   Kéo lại đơn trong 7 ngày gần nhất cho toàn shop (không phải backfill toàn bộ như FR-02-04/07).
> - **Tồn kho**: chưa có — không nằm trong FR-02-10, không có pipeline nào để tái sử dụng (xem
>   `docs/ticket/2. in-progress/FR-02-10/implementation-plan.md` mục 0). Tách ticket riêng khi cần.
>   **Ghi chú cho FE (thêm 17/08/2026, FR-02-12):** `GET /api/v1/authorizations` (quyền `shops.view`)
>   đã xây ở path cuối cùng — không phải route tạm dưới `/tiktok/*` như các mục ở trên. `ListResponseDto`
>   chuẩn, mỗi phần tử: `{ shopId, sellerName, status, grantedScopes, capabilities }`. `status` gồm cả
>   `REVOKED`/`EXPIRED` (shop vẫn hiện để seller uỷ quyền lại) — chỉ `DISCONNECTED` (FR-02-09) là biến
>   mất khỏi danh sách. `capabilities` là object cố định 5 field boolean: `products`,
>   `ordersFulfillment`, `finance`, `affiliate`, `livePerformance` — suy ra **riêng theo từng shop**
>   từ `grantedScopes` của chính shop đó (không gộp theo tenant). FE dùng field này để tô khoá icon +
>   lý do trước khi seller bấm vào, không cần chờ 1 request lỗi từ route thật. Nguồn map
>   capability → scope: `vishop-build-spec-v1.2.md` §2.2 (đã sửa tên scope 17/08/2026).
>   **Ghi chú cho FE (thêm 13/08/2026, sửa 14/08/2026, FR-02-07):** `progress` có thêm 2 field.
>   `progress.kind: 'initial' | 'catchup'` — `'initial'` cho lần đồng bộ đầu tiên của một shop mới,
>   `'catchup'` khi đây là lượt kéo bù sau khi kết nối lại một shop từng gián đoạn (token hết hạn,
>   seller revoke, lỗi mạng...). Copy hiển thị nên khác nhau giữa hai trường hợp thay vì đều đọc là
>   "đang đồng bộ khởi tạo".
>
> **Sửa 14/08/2026:** bản mô tả trước nói khoảng gián đoạn quá 90 ngày chỉ kéo bù 90 ngày gần nhất
> — review đúng là điều này làm mất dữ liệu, vi phạm AC gốc ("kéo bù đơn phát sinh trong khoảng gián
> đoạn", không giới hạn). Đã sửa: khoảng gián đoạn luôn được kéo bù **toàn bộ**, không còn cắt ở 90
> ngày. `progress.gapDays?: number` — xuất hiện trên mọi lượt `'catchup'`, là số ngày gián đoạn thực
> tế, thuần thông tin hiển thị ("kéo bù N ngày gần đây"), không ảnh hưởng phạm vi kéo bù.
> Không có hard cap cho catch-up window: watermark chỉ tiến lên sau khi toàn bộ khoảng gián đoạn đã
> đồng bộ thành công, nên hệ thống không thể bỏ dữ liệu cũ rồi đánh dấu như đã hoàn tất.

### 1.1a `shopCipher` — chỉ nội bộ BE, không có trong response FE

`shopCipher` là `data.shops.cipher` trả về từ API [Get Authorized Shops](https://partner.tiktokshop.com/docv2/page/650a2ff42abe5b02be89e83c) của TikTok khi đổi authorization code. Đây là tham số bắt buộc mà BE phải đính kèm khi gọi hầu hết endpoint cấp `shop` sang TikTok (Get Order Detail, Get Shop LIVE Performance List, Search Seller Affiliate Orders, …) — không phải một field seller-facing.

- **Lưu ở đâu:** trên record `authorization`, không phải trên `shop` (xem `vishop-erd.md` mục 4). Một shop có thể có nhiều lần uỷ quyền theo thời gian; `shopCipher` gắn với lần uỷ quyền đang hiệu lực.
- **Vòng đời:** không đổi khi refresh token định kỳ. Chỉ có thể đổi khi seller **uỷ quyền lại** (re-authorize) — trường hợp này bắt buộc ghi `event_log` (loại `authorization.reauthorized`) vì mọi lệnh gọi TikTok đang cache `shopCipher` cũ sẽ lỗi cho tới khi đồng bộ lại.
- **Không trả về FE:** các endpoint ở §1 (`GET /api/v1/shops`, `GET /api/v1/authorizations`, …) không có field `shopCipher` trong response — nó chỉ tồn tại trong tầng gọi sàn của BE, giảm bề mặt rò rỉ.

### 1.1 Sức khoẻ gian hàng (SPS) — M02

`GET /shops/{shopId}/health` ở bảng trên trả đèn kết nối. Điểm hiệu suất do **sàn chấm** là dữ liệu khác hẳn, có endpoint riêng.

| Method | Path                                              | FR            | Ghi chú                                                  |
| ------ | ------------------------------------------------- | ------------- | -------------------------------------------------------- |
| GET    | `/api/v1/shops/performance`                       | FR-02-14 (P0) | Điểm và trạng thái của toàn bộ shop, một dòng mỗi shop   |
| GET    | `/api/v1/shops/{shopId}/performance`              | FR-02-14 (P0) | Điểm tổng + 6 chỉ số thành phần của một shop             |
| GET    | `/api/v1/shops/{shopId}/performance/{metricCode}` | FR-02-14 (P0) | Xu hướng theo ngày + danh sách đơn đang kéo chỉ số xuống |

```json
// GET /api/v1/shops/{shopId}/performance → 200
{
  "shopId": "01973...",
  "recordDate": "2026-08-06",
  "score": 4.2,
  "status": "AVERAGE",
  "metrics": [{ "metricCode": "OTDR", "value": 2.1, "threshold": 2.0, "status": "POOR" }]
}
```

Nguồn: [Get SPS Overview](https://partner.tiktokshop.com/docv2/page/6a5f09c44c0d61049ea780c5) · [Get SPS Metrics](https://partner.tiktokshop.com/docv2/page/6a5f09c563a9f5049704bfa1) · [Get SPS Metric Diagnosis](https://partner.tiktokshop.com/docv2/page/6a5f09ade90897049c66a0d3) · [Get SPS Metric Problem Details](https://partner.tiktokshop.com/docv2/page/6a5f09c3ca0ac8049e62b234) · [Get SPS Metric Top Items](https://partner.tiktokshop.com/docv2/page/6a5f09c3bab25d0499c34658).

Ba ràng buộc:

1. **`status` là giá trị ViShop quy đổi, không phải enum sẵn của sàn.** Sàn trả `status` + `status_text` riêng cho từng chỉ số cùng hai mốc `benchmarks.excellent_threshold` và `poor_threshold`. BE quy về đúng bốn bậc `EXCELLENT` / `AVERAGE` / `POOR` / `NO_DATA` để tô màu nhất quán giữa các chỉ số — **mốc thì lấy của sàn, chỉ có cách gộp bậc là của ViShop**. Sáu `metricCode` (`OTDR`, `SFCR`, `NBFR`, `NRR`, `AHT`, `IM_DSAT`) thì ngược lại: đó là tập của sàn, không thêm bớt. Lưu ý tên field của sàn giữ nguyên snake_case khi đọc từ TikTok; camelCase chỉ áp cho response ViShop trả ra FE.
2. **Không nội suy.** Ngày sàn không trả dữ liệu thì `NO_DATA`, không lấy giá trị ngày trước điền vào. Biểu đồ xu hướng phải để đứt đoạn ở đó.
3. **`threshold` lưu theo từng bản ghi**, không đọc từ một bảng cấu hình chung — sàn đổi ngưỡng được, mà báo cáo tháng trước vẫn phải giải thích được vì sao lúc đó shop bị chấm `POOR`.

---

## 2. ProductProvider — M03 Sản phẩm & SKU, M04 Ghép nối SKU

| Method | Path                                                                  | FR                                | Ghi chú                                                                                                                                                                                    |
| ------ | --------------------------------------------------------------------- | --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| GET    | `/api/v1/shops/{shopId}/platform-products`                            | FR-03-02, FR-03-03 (P0)           | Ảnh, tên, SKU sàn, biến thể, giá, tồn trên sàn                                                                                                                                             |
| POST   | `/api/v1/shops/{shopId}/platform-products/sync`                       | FR-03-01, FR-03-04 (P0/P1)        | Đồng bộ lại một chiều                                                                                                                                                                      |
| POST   | `/api/v1/shops/{shopId}/platform-products/{platformProductId}/sync`   | FR-03-04 (P1)                     | `platformProductId` là UUID nội bộ; server tra `externalProductId` rồi đồng bộ product sàn và toàn bộ biến thể                                                                             |
| POST   | `/api/v1/merchant-skus` / PATCH `/api/v1/merchant-skus/{id}`          | FR-03-05 (P0)                     | Mã unique/tenant. Body tạo nhận luôn giá vốn, GTIN, ngưỡng tồn                                                                                                                             |
| GET    | `/api/v1/merchant-skus/{id}/cost-history`                             | FR-03-15 (P1)                     | Lịch sử thay đổi giá vốn theo SKU (append-only §2.5). Phân trang cursor & offset. Cần `skus.view` & `data.cost.view`                                                                       |
| PATCH  | `/api/v1/merchant-skus/bulk`                                          | FR-03-13 (P0)                     | Cập nhật hàng loạt giá vốn/danh mục/trạng thái/ngưỡng                                                                                                                                      |
| PUT    | `/api/v1/merchant-skus/{id}/gtins`                                    | FR-03-07 (P0)                     | Thay nguyên danh sách; GTIN duy nhất trong tenant, tối đa 10                                                                                                                               |
| PUT    | `/api/v1/merchant-skus/{id}/uom-conversions`                          | FR-03-16 (P2)                     | Thay nguyên bộ quy đổi đơn vị                                                                                                                                                              |
| PUT    | `/api/v1/merchant-skus/{id}/combo-components`                         | FR-03-11 (P0)                     | Thay nguyên danh sách SKU con                                                                                                                                                              |
| POST   | `/api/v1/merchant-skus/import`                                        | FR-03-12 (P0)                     | Excel, báo lỗi theo dòng                                                                                                                                                                   |
| GET    | `/api/v1/merchant-skus/export`                                        | FR-03-12 (P0)                     |                                                                                                                                                                                            |
| GET    | `/api/v1/sku-mappings`                                                | FR-04-01 (P0)                     | ✅ Một dòng = một SKU sàn. Lọc `mappingStatus` / `shopId` / `search`, con trỏ `(createdAt, id)`. `withTopSuggestion=true` nhúng gợi ý tốt nhất vào từng dòng UNMAPPED                      |
| GET    | `/api/v1/sku-mappings/summary`                                        | FR-04-09, FR-04-01 (P0)           | ✅ Bốn số của thẻ + tách theo shop. Tài nguyên đơn, không bọc `ListResponseDto` — con trỏ không suy ra tổng                                                                                |
| GET    | `/api/v1/sku-mappings/candidates`                                     | FR-04-04, FR-04-06 (P0)           | ✅ SKU kho còn ghép được cho một biến thể. Lọc `platformSkuId` / `shopId` / `search`, trả kèm `available`                                                                                  |
| GET    | `/api/v1/sku-mappings/suggestions`                                    | FR-04-02 (P0)                     | ✅ Thứ tự: mã trùng → mã vạch trùng → tên tương đồng. Nhận `platformSkuIds` (CSV, tối đa 100)                                                                                              |
| POST   | `/api/v1/sku-mappings/accept`                                         | FR-04-03 (P0)                     | ✅ Hàng loạt, tối đa 500 cặp tường minh. Mỗi dòng có phán quyết riêng, không hỏng cả lô vì một dòng                                                                                        |
| POST   | `/api/v1/sku-mappings`                                                | FR-04-04, FR-04-06 (P0)           | ✅ 1 SKU sàn ↔ n SKU kho theo số lượng. Từ chối biến thể đã có ghép nối                                                                                                                    |
| PUT    | `/api/v1/sku-mappings/platform-skus/{platformSkuId}`                  | FR-04-04, FR-04-06, FR-04-08 (P0) | ✅ Đường sửa duy nhất: gửi **nguyên bộ** thành phần listing cần có, trong một transaction. Thêm/bớt/đổi thành phần đều là lệnh này. `components: []` bị từ chối 422 — gỡ có endpoint riêng |
| POST   | `/api/v1/sku-mappings/auto-create`                                    | FR-04-05 (P0)                     | ✅ Tạo SKU kho + ghép nối 1-1 trong một transaction, tối đa 500 dòng, cùng trần với `/accept`. Cần **cả** `skus.create` và `skus.mapping.edit`. Mỗi dòng có phán quyết riêng               |
| POST   | `/api/v1/sku-mappings/import`                                         | FR-04-07 (P0)                     | ✅ Nhận XLSX (multipart hoặc `key` đã verify) → 202 kèm `importId`. Chạy nền, lỗi bán phần theo dòng. Quyền `skus.mapping.edit`                                                            |
| GET    | `/api/v1/sku-mappings/import/{importId}`                              | FR-04-07 (P0)                     | ✅ Tiến độ + `errors[]` (50 dòng hỏng đầu, kèm message). `skippedRows` tách khỏi `failedRows`. Chỉ người tạo job đọc được                                                                  |
| POST   | `/api/v1/sku-mappings/export`                                         | FR-04-07 (P0)                     | ✅ Nhận đúng ba bộ lọc của `GET /sku-mappings` → 202 kèm `jobId`. Bộ lọc bị đóng băng vào job. Quyền `skus.view`                                                                           |
| GET    | `/api/v1/sku-mappings/export/{jobId}`                                 | FR-04-07 (P0)                     | ✅ Trạng thái + `downloadUrl` ký sẵn khi `DONE`. Chỉ người tạo job đọc được                                                                                                                |
| DELETE | `/api/v1/sku-mappings/platform-skus/{platformSkuId}`                  | FR-04-08 (P0)                     | ✅ Gỡ **toàn bộ** ghép nối của listing. Gỡ là gỡ hết — bớt một thành phần của combo là **sửa**, đi qua `PUT`. Idempotent, lặp lại không ghi thêm dấu vết                                   |
| GET    | `/api/v1/sku-mappings/platform-skus/{platformSkuId}/pending-packages` | FR-04-08 (P0)                     | ✅ Kiện đang bị ảnh hưởng — FE gọi trước khi cho bấm sửa/gỡ                                                                                                                                |

**Request/response rút gọn:**

```json
// POST /api/v1/merchant-skus → 201 (trả thẳng tài nguyên vừa tạo, không bọc)
{ "id": "01973...", "skuCode": "AO-THUN-DEN-M", "name": "string", "gtin": ["string"],
  "weightGram": 200, "dimensionsCm": { "length": 10, "width": 5, "height": 2 }, "lowStockThreshold": 10,
  "isCombo": false, "comboComponents": [{ "merchantSkuId": "01973...", "quantity": 1 }] }

// PUT    /api/v1/sku-mappings/platform-skus/{platformSkuId}
//        body { components: [{ merchantSkuId, quantity? }], applyToPendingPackages? }
// DELETE /api/v1/sku-mappings/platform-skus/{platformSkuId}?applyToPendingPackages=true
// → 200, cùng shape cho cả hai: đúng một dòng của GET /sku-mappings, không thêm gì
{
  "platformSkuId": "01973...", "mappingStatus": "MAPPED", "pushableStock": 12,
  "mappings": [ /* ... */ ]    // DELETE trả mappings: [] và mappingStatus: UNMAPPED
}
```

`GET /api/v1/sku-mappings` trả một dòng cho mỗi **biến thể sàn** — ghép nối diễn ra ở cấp biến thể, gom về cấp sản phẩm là làm hỏng đúng việc chính của màn:

```jsonc
{
  "platformSkuId": "01973...",
  "shopId": "01973...",
  "shopName": "Gian hàng A",
  "productName": "Áo thun cotton",
  "variantName": "Đen · M",
  "sellerSkuCode": "AO-DEN-M",
  "imageUrl": null,
  "price": 199000,
  "currency": "VND",
  "platformStock": 12,
  "mappingStatus": "MAPPED", // UNMAPPED | PARTIAL | MAPPED
  "pushableStock": 3, // MIN(available ÷ quantity), làm tròn xuống (BR-05-03)
  "mappings": [
    {
      "id": "01973...",
      "merchantSkuId": "01973...",
      "merchantSkuCode": "KHO-01",
      "merchantSkuName": "…",
      "quantity": 2,
      "available": 7,
      "usable": true,
    },
  ],
}
```

`PARTIAL` = đã có ghép nối **nhưng** ít nhất một SKU kho đích không còn nhặt được: đã xoá mềm, `DISCONTINUED`, hoặc là combo chưa khai thành phần. Trạng thái này tồn tại vì xoá mềm một SKU kho **không** chạm vào FK `Restrict` của `sku_mappings` — dòng ghép nối sống sót và listing sẽ tiếp tục tự nhận là đã ghép nếu không có ai kiểm. Thành phần `usable: false` kéo `pushableStock` về 0 thay vì bị bỏ qua.

`GET /api/v1/sku-mappings/summary` trả tổng của tenant kèm `byShop[]` cùng shape:

```jsonc
{
  "totalCount": 320,
  "mappedCount": 302,
  "partialCount": 3,
  "unmappedCount": 15,
  "mappingRate": 94, // mappedCount ÷ totalCount, PARTIAL tính là chưa đạt
  "affectedOrderCount": 7, // đơn chưa kết thúc có dòng hàng không quy ra được SKU kho nhặt được
  "byShop": [
    {
      "shopId": "01973...",
      "shopName": "…",
      "totalCount": 120,
      "mappedCount": 118,
      "partialCount": 0,
      "unmappedCount": 2,
      "mappingRate": 98,
      "affectedOrderCount": 1,
    },
  ],
}
```

**Ghi ghép nối.** `POST /api/v1/sku-mappings` tạo, `PUT /api/v1/sku-mappings/platform-skus/{platformSkuId}` thay nguyên bộ. Cả hai trả về **đúng dòng của `GET /sku-mappings`** đã cập nhật (`SkuMappingResponseDto`), nên màn ghép nối vá lại một dòng thay vì nạp lại cả trang con trỏ:

```jsonc
// POST /api/v1/sku-mappings → 201
{
  "platformSkuId": "01973...",
  "components": [
    { "merchantSkuId": "01973...", "quantity": 2 }, // quantity mặc định 1 nếu bỏ trống
    { "merchantSkuId": "01974...", "quantity": 1 },
  ],
}
```

Ba điều đã chốt về cặp `POST` / `PUT`:

1. `POST` **từ chối** biến thể đã có ghép nối (`409 mapping_already_exists`) — thao tác kéo thả không được âm thầm xoá bộ thành phần đang có. Muốn sửa thì gọi `PUT`.
2. `PUT` thay nguyên bộ trong **một transaction**, nên không có khoảnh khắc nào listing rơi về "chưa ghép" giữa chừng, và sửa số lượng một thành phần không đụng ghép nối của listing khác (AC FR-04-06).
3. `PUT` với `components: []` **là đường gỡ ghép nối cả cụm**. Không có endpoint `DELETE` theo `platformSkuId`.

`GET /api/v1/sku-mappings/candidates` trả SKU kho còn ghép được, đã loại sẵn thứ sẽ bị từ chối lúc submit: SKU đã xoá mềm, `DISCONTINUED`, combo chưa khai thành phần, và SKU mà tự nó đã tạo thành **đúng** dấu chân của một biến thể khác trong cùng gian hàng (BR-04-04). Combo đã khai thành phần **vẫn là ứng viên hợp lệ** — `available` của nó suy từ SKU con theo BR-03-02:

```jsonc
// GET /api/v1/sku-mappings/candidates?platformSkuId=01973...&search=ao-thun
{
  "merchantSkuId": "01973...",
  "skuCode": "AO-THUN-01",
  "name": "…",
  "isCombo": false,
  "available": 7,
}
```

`mappingRate` tính `PARTIAL` là **chưa đạt**: BR-04-02 chặn bật đẩy tồn theo con số này, mà một listing thiếu một phần bộ thì đẩy tồn lên là sai. `affectedOrderCount` đếm đơn chưa ở trạng thái kết thúc (`DELIVERED`/`COMPLETED`/`CANCELLED`) có ít nhất một dòng hàng chưa quy ra được SKU kho nhặt được — kể cả dòng chưa có `platformSkuId` vì listing chưa đồng bộ.

**Sửa / gỡ ghép nối (FR-04-08).** Cả hai đều khoá theo `platformSkuId` — cấp listing, không phải cấp dòng thành phần. Ranh giới giữa chúng là ranh giới nghiệp vụ, không phải kỹ thuật:

- **Sửa** = `PUT …/platform-skus/{platformSkuId}` với bộ thành phần listing cần có sau khi sửa. Thêm một thành phần, bớt một thành phần, đổi SKU kho, đổi số lượng — tất cả là lệnh này. Một transaction, nên listing không bao giờ rơi vào trạng thái nửa vời giữa chừng.
- **Gỡ** = `DELETE …/platform-skus/{platformSkuId}`. **Gỡ là gỡ hết.** Bớt một thành phần của combo không phải gỡ, đó là sửa cấu hình bộ — đi qua `PUT`.

Vì sao không có "gỡ một thành phần": BR-04-04 kiểm trên **trạng thái cuối**, nên `PUT` đi thẳng tới đó, còn gỡ từng dòng sẽ bị kiểm ở mọi trạng thái trung gian. Tình huống có thật: listing `Bộ đồ` ghép `{AO, QUAN}`, shop còn `Áo lẻ` ghép `{AO}` và `Quần lẻ` ghép `{QUAN}` — cả ba hợp lệ. Gỡ dòng `AO` thì `Bộ đồ` còn `{QUAN}`, trùng khít `Quần lẻ` → 409; gỡ dòng `QUAN` thì trùng khít `Áo lẻ` → 409. Không thứ tự nào thoát, listing kẹt vĩnh viễn. `DELETE` về thẳng cụm rỗng, mà cụm rỗng không tiêu thụ gì nên không thể trùng ai — đó là lý do nó là con đường duy nhất được miễn BR-04-04.

`PUT` với `components: []` bị từ chối `422`: để nguyên thì có hai cửa cùng gỡ, mà chỉ một cửa ghi được lựa chọn BR-04-03.

Luồng đúng là **đọc trước, ghi sau**: `GET /api/v1/sku-mappings/platform-skus/{platformSkuId}/pending-packages` trả đúng danh sách kiện **chưa giao** (`TO_FULFILL`, **kể cả** kiện sàn chưa báo trạng thái) mà thay đổi sẽ chạm tới, để người dùng **xem** rồi mới quyết định — chứ không phải bấm gỡ rồi mới nhận lỗi kèm một mảng id. Truy vấn đi `packages → orders → order_lines`, có **đường dự phòng** theo `(shopId, externalSkuId)` cho dòng đơn chưa phân giải được `platformSkuId`, vì đếm thiếu ở màn cảnh báo tệ hơn đếm thừa. Kiện đã giao không nằm trong danh sách và không bị đụng tới — lịch sử trừ tồn đã phát sinh giữ nguyên.

`applyToPendingPackages` là **lựa chọn BR-04-03 của người vận hành, được ghi lại chứ không được thi hành ở đây**: áp quy tắc mới cho kiện chưa giao nghĩa là nhả rồi đặt lại khoá tồn, mà `products` không sở hữu `inventories` lẫn `packages`. Nó được ghi vào `event_logs` (sổ lưu vĩnh viễn, phục vụ truy vết) **và** phát ra sự kiện qua outbox **trong cùng transaction với lệnh ghi** — dạng **thông báo** (cũ → mới, cờ, danh sách kiện), không phải mệnh lệnh, để M05/M06 tự quyết cách xử lý khi FR-06-15a có mặt. Cho tới lúc đó chưa có consumer nào, và cũng chưa có khoá tồn nào để nhả. Cờ đi theo body ở `PUT`, theo query ở `DELETE`.

Ba lệnh ghi phát ba sự kiện cùng một payload — `products.sku_mapping_created` / `_replaced` / `_removed`. Chúng nằm dưới `products.*` chứ không phải một stream riêng, vì đó là tiền tố mà `AuditLogListener` đăng ký: nhờ vậy mọi lần ghi ghép nối đều có một dòng `audit_logs` bên cạnh dòng `event_logs`. `create` mang `before: []`, `remove` mang `after: []` — consumer phân biệt ba loại bằng payload, không cần đọc tên sự kiện.

**Hai đường ghi hàng loạt phát cùng sự kiện `_created` đó, một sự kiện cho mỗi SKU sàn.** `POST /sku-mappings/accept` và `POST /sku-mappings/auto-create` không có tên sự kiện riêng và không có payload riêng: duyệt năm trăm gợi ý để lại đúng dấu vết mà năm trăm lần ghi lẻ để lại, nên `audit_logs` không có lỗ hổng ở đúng cái endpoint sinh ra để làm hàng loạt, và consumer không phải viết nhánh thứ hai cho việc đến theo lô. Đơn vị là **listing chứ không phải dòng gửi lên** — nhiều dòng cùng một SKU sàn dựng thành một bộ thì gộp về một sự kiện mang cả cụm. Cả hai luôn mang `before: []`, `applyToPendingPackages: false`, `affectedPackageCount: 0`: chúng từ chối biến thể đã có ghép nối, nên không có cụm cũ nào để một kiện chưa giao từng nhặt theo. Lô gửi lại lần hai cho mọi dòng `skipped` và **không phát sự kiện nào** — `event_logs` bị trigger cấm xoá, một vòng retry không được phép chất đầy nó bằng những dòng không mô tả thay đổi gì.

Cả `PUT` lẫn `DELETE` trả **200** với **đúng một dòng mapping**, không kèm phạm vi ảnh hưởng. `DELETE` không trả `204` vì client cần dựng lại dòng vừa gỡ mà không phải gọi thêm một lượt. Phạm vi kiện là việc của `GET …/pending-packages`, gọi **trước** khi bấm xác nhận: FE đã có danh sách đó trong tay rồi, nên trả lại lần nữa chỉ là một bản sao đã cũ ngay tại thời điểm nó về tới nơi. Số kiện vẫn được đếm **bên trong chính transaction ghi** và vẫn nằm trong `event_logs` cùng payload sự kiện (`affectedPackageCount` là con số đầy đủ, `affectedPackageIds` là mẫu tối đa 200 id mới nhất) — đó mới là chỗ dùng để truy vết khi tồn kho lệch. Lý do mẫu có trần: một listing bán chạy có thể tồn đọng hàng nghìn kiện `TO_FULFILL`, kéo cả mớ đó qua transaction ghi và vào một dòng `event_logs` không xoá lại được.

`DELETE` là **idempotent**: gỡ một listing vốn chưa ghép trả `200` với `mappingStatus: UNMAPPED`, nhưng **không** ghi `event_logs` và **không** phát sự kiện — `event_logs` bị trigger cấm xoá và giữ mãi mãi, nên một vòng retry không được phép chất đầy nó bằng những dòng không mô tả thay đổi nào.

`GET /api/v1/sku-mappings/suggestions?platformSkuIds=<csv>` trả một mục cho **mỗi** biến thể được hỏi, đúng thứ tự đã hỏi, kèm `matchedBy` và `tier` cho từng gợi ý:

```jsonc
// GET /api/v1/sku-mappings/suggestions?platformSkuIds=01973d20-...,01973d21-...
{
  "results": [
    {
      "platformSkuId": "01973d20-...",
      "suggestions": [
        {
          "merchantSkuId": "01973e11-...",
          "skuCode": "AO-THUN-01",
          "name": "Áo thun cotton đen M",
          "isCombo": false,
          "available": 7,
          "matchedBy": "sku_code", // sku_code | barcode | name
          "score": 100,
          "tier": "high",
        },
      ],
    },
    { "platformSkuId": "01973d21-...", "suggestions": [] }, // dưới ngưỡng → để trống, sang ghép tay
  ],
}
```

Bốn điều đã chốt về endpoint này:

1. **Dừng ở tầng đầu tiên có kết quả.** Có khớp mã SKU thì tuyệt đối không kèm gợi ý theo tên bên dưới — chôn một khớp chính xác dưới một đống phỏng đoán là bắt người duyệt đọc thêm đúng chỗ họ không cần đọc.
2. **`matchedBy` là enum, không phải câu chữ tiếng Việt.** Căn cứ khớp là dữ liệu, nhãn hiển thị là việc của FE — trả chuỗi hiển thị từ BE là chôn i18n vào response.
3. **`tier` do backend phân hạng**, không để client suy từ `score`: ngưỡng là quy tắc nghiệp vụ của FR-04-02, đặt ở client sẽ phân tán và lệch khi đổi ngưỡng. Chỉ `high` (tầng mã SKU / mã vạch) được mở chấp nhận hàng loạt.
4. **Dưới ngưỡng tin cậy tối thiểu thì trả mảng rỗng**, không hạ chuẩn để có cái mà hiện. Dòng trống đọc là "tự quyết đi"; dòng sai là một listing ghép nhầm rồi đẩy tồn lên sai SKU kho.

Biến thể **đã có ghép nối** luôn trả `suggestions: []` — màn này là hàng chờ listing chưa ghép, gợi ý cho dòng đã ghép chỉ mời người dùng ghi đè một bộ thành phần mà họ không nhìn thấy từ dòng đó.

Giao diện ghép nối cần toàn bộ gợi ý trước thao tác kéo thả, nên endpoint này trả gợi ý cho cả trang hàng chờ trong một lượt gọi, không gọi lẻ theo từng SKU.

**Riêng bảng hàng chờ chỉ cần gợi ý tốt nhất, và lấy được ngay trong một lượt gọi** — thêm `withTopSuggestion=true` vào `GET /api/v1/sku-mappings`:

```jsonc
// GET /api/v1/sku-mappings?mappingStatus=UNMAPPED&withTopSuggestion=true
{
  "object": "list",
  "data": [
    {
      "platformSkuId": "01973d20-...",
      "productName": "Áo thun cotton",
      "mappingStatus": "UNMAPPED",
      // Cùng shape với một phần tử trong `suggestions[]` của endpoint trên.
      "topSuggestion": {
        "merchantSkuId": "01973e30-...",
        "skuCode": "AO-DEN-M",
        "name": "Áo thun cotton - Đen · M",
        "isCombo": false,
        "available": 9,
        "matchedBy": "sku_code",
        "score": 100,
        "tier": "high",
      },
    },
    { "platformSkuId": "01973d21-...", "mappingStatus": "UNMAPPED", "topSuggestion": null },
  ],
}
```

Ba trạng thái của `topSuggestion`, FE cần phân biệt đủ cả ba:

| Giá trị      | Nghĩa                                                               |
| :----------- | :------------------------------------------------------------------ |
| **vắng mặt** | không hỏi (`withTopSuggestion` không bật)                           |
| `null`       | có hỏi, nhưng không gợi ý nào qua ngưỡng — hoặc dòng đã có ghép nối |
| object       | gợi ý thắng cuộc                                                    |

Ba điều dễ bỏ sót:

- **Chỉ dòng `UNMAPPED` mới tốn một lượt truy vấn.** Dòng đã ghép trả thẳng `null` mà không hỏi DB — bảng "đã ghép" dùng chung endpoint này, và khi onboarding xong thì phần lớn dòng đã ghép.
- **Mặc định tắt.** Tầng tên là phép quét trigram; không nên tính tiền cho mọi lần phân trang khi người dùng chỉ đang cuộn xem.
- **Không thay thế `/suggestions`.** Khu vực ghép nối hiện nhiều gợi ý, và khu kéo thả tô sáng **mọi** SKU kho trên ngưỡng — một gợi ý duy nhất không trả lời được hai chỗ đó. Cả hai đường dùng chung một hàm xếp hạng, nên `tier` ở bảng và ở panel không thể lệch nhau.

**Tầng mã vạch so theo GTIN-14, không so chuỗi thô.** GTIN-8 / UPC-12 / EAN-13 / GTIN-14 **không phải bốn chuẩn khác nhau** — chúng là một mã GS1 viết ở bốn độ dài, và các số 0 đằng trước không mang nghĩa (đệm số 0 cũng không đổi check digit). `merchant_skus` lưu mã đúng như người dùng nhập, nên trong cùng một tenant có cả mã 8 số lẫn 13 số. Vì vậy phép so căn phải cả hai vế về 14 số trước khi so:

```js
listing '012345678905'  (UPC-12)   ┐
kho     '0012345678905' (EAN-13)   ┘ →  cùng '00012345678905'  →  khớp
```

Dữ liệu đang lưu **không bị đổi**, không có migration nào chạm vào `merchant_sku_gtins` — chỉ phép so sánh chuẩn hoá. Mã vạch không phải chuỗi 1–14 chữ số thì không khớp gì cả, thay vì bị cắt bớt thành một mã của người khác.

> Không lưu kèm nhãn chuẩn mã (`GTIN`/`EAN`/`UPC`/`ISBN`/`JAN`) mà TikTok gửi trong `identifier_code.type`. Với bốn dạng trên thì nhãn không nói thêm điều gì bản thân dãy số chưa nói, nên nó sẽ là một cột chỉ ghi vào mà không ai đọc.

⚠️ **Tầng mã vạch chỉ có dữ liệu sau khi đồng bộ lại sản phẩm.** `platform_skus.barcode` là cột mới, `NULL` cho toàn bộ listing đã đồng bộ trước đó, và chỉ endpoint chi tiết sản phẩm (`/product/202309/products/{id}`) mới mang `identifier_code`. Chưa chạy lại đồng bộ đầy đủ thì tầng 2 không bắt được gì — đó là dữ liệu thiếu, không phải code sai.

**`POST /api/v1/sku-mappings/accept`** nhận **danh sách cặp tường minh** (không nhận bộ lọc: bộ lọc chạy lại phía server có thể ôm cả listing vừa xuất hiện sau khi người duyệt nhìn), tối đa **500 cặp**, và trả phán quyết cho từng dòng thay vì hỏng cả lô vì một dòng:

```jsonc
// POST /api/v1/sku-mappings/accept → 200
{
  "acceptedCount": 1,
  "skippedCount": 1,
  "rejectedCount": 1,
  "results": [
    { "platformSkuId": "01973d20-...", "merchantSkuId": "01973e11-...", "status": "accepted" },
    {
      "platformSkuId": "01973d21-...",
      "merchantSkuId": "01973e12-...",
      "status": "skipped",
      "reason": "already_mapped", // bấm hai lần không nhân đôi
    },
    {
      "platformSkuId": "01973d22-...",
      "merchantSkuId": "01973e11-...",
      "status": "rejected",
      "reason": "conflicts_in_batch", // BR-04-04, đối thủ nằm ngay trong lô này
    },
  ],
}
```

`reason` ∈ `platform_sku_not_found` · `merchant_sku_not_found` · `target_unusable` · `already_mapped` · `conflicts_stock_push` · `conflicts_in_batch`.

Hai điều dễ bỏ sót:

- **BR-04-04 phải so cả với chính lô đang gửi.** Unique index là `(platformSkuId, merchantSkuId)`, nên hai dòng **khác SKU sàn, cùng gian hàng**, cùng trỏ về một SKU kho thì DB không chặn, mà phép kiểm tra so-với-DB cũng không thấy vì chưa dòng nào commit. Bỏ vế này là để oversell lọt qua đúng cái endpoint sinh ra để duyệt hàng loạt. Dòng đầu tiên thắng, các dòng sau nhận `conflicts_in_batch`.
- **So sánh chạy trên tập SKU vật lý sau khi bung combo**, không so `merchantSkuId` thô: một listing ghép combo và một listing ghép SKU con của chính combo đó đang quảng cáo cùng một lượng tồn trên kệ.

Gửi trùng **nguyên cặp** `(platformSkuId, merchantSkuId)` trong một lô là `422` ngay ở tầng validate — để nó xuống DB thì một dòng trùng làm hỏng cả lô.

**`POST /api/v1/sku-mappings/auto-create`** là lối tắt onboarding: seller chưa có SKU kho nào thì mọi số liệu vận hành phía sau ghép nối đều đứng im cho tới khi họ gõ tay xong cả danh mục. Mỗi dòng sinh **một** SKU kho kế thừa tên và ảnh của listing, rồi ghép nối luôn **trong cùng transaction** — hỏng giữa chừng không để lại SKU kho mồ côi. Tối đa **500 dòng** — cùng trần với `POST /sku-mappings/accept`, để hai thao tác hàng loạt cạnh nhau trên màn hàng chờ nhận được cùng một tập dòng đã chọn, cần **cả hai** quyền `skus.create` và `skus.mapping.edit`.

```jsonc
// POST /api/v1/sku-mappings/auto-create
{
  "items": [
    { "platformSkuId": "01973d20-..." },
    { "platformSkuId": "01973d21-...", "skuCode": "AO-DEN-L" }, // ghi đè mã sàn khai
  ],
}

// → 200
{
  "createdCount": 1,
  "skippedCount": 1,
  "results": [
    {
      "platformSkuId": "01973d20-...",
      "status": "created",
      "merchantSkuId": "01973e30-...",
      "skuCode": "AO-DEN-M",
      "name": "Áo thun cotton - Đen · M",
    },
    { "platformSkuId": "01973d21-...", "status": "skipped", "reason": "sku_code_already_exists" },
  ],
}
```

`reason` ∈ `platform_sku_not_found` · `already_mapped` · `sku_code_missing` · `sku_code_too_long` · `sku_code_already_exists` · `duplicate_in_batch`.

Bốn điều dễ bỏ sót:

- **Mã SKU kho không bao giờ được bịa.** Lấy `skuCode` trong body, không có thì lấy `sellerSkuCode` của listing; cả hai rỗng thì dòng đó `sku_code_missing`. Một mã seller không tự chọn là mã họ không nhận ra trên phiếu nhặt hàng, và sẽ đụng độ với mã thật ngày họ import.
- **Trùng mã so không phân biệt hoa thường**, cả với dữ liệu đã có lẫn trong chính lô đang gửi — unique index của `merchant_skus` thì có phân biệt, nên `ABC` và `abc` là hai dòng với Postgres nhưng một SKU với seller.
- **Hai biến thể cùng gian hàng khai chung một `sellerSkuCode` là chuyện thường.** Dòng sau nhận `duplicate_in_batch` chứ không ghép chung vào một SKU kho — ghép chung chính là thứ BR-04-04 cấm.
- **Tên kế thừa cắt phần tên sản phẩm, giữ nguyên tên biến thể.** Cắt đuôi chuỗi đã ghép sẽ khiến mọi biến thể của một listing tên dài ra SKU trùng tên hệt nhau. Giá vốn luôn `0` — endpoint không nhận giá vốn, sửa qua `PATCH /merchant-skus/bulk`.

**`POST /api/v1/sku-mappings/import` + `POST /api/v1/sku-mappings/export` (FR-04-07)** là kênh thao tác thay thế cho seller có hàng nghìn SKU, hoặc cần duyệt offline trước khi áp. Cả hai **chạy nền** và trả `jobId` — một tenant có danh mục thật thì cả việc dựng file lẫn việc áp file đều lâu hơn thời gian một request được phép mở (§0.4).

**Bộ cột — 6 cột, dùng chung cho cả hai chiều:**

| Cột                 | Vai trò khi import                                                              |
| :------------------ | :------------------------------------------------------------------------------ |
| `ID SKU sàn`        | **Khoá chính.** Export luôn điền; import khớp theo cột này trước                |
| `Gian hàng`         | Nửa đầu khoá dự phòng — khớp theo **tên** gian hàng, không phân biệt hoa thường |
| `Mã SKU sàn`        | Nửa sau khoá dự phòng (`sellerSkuCode`)                                         |
| `Tên biến thể`      | Tham chiếu cho người đọc, **không** dùng để khớp                                |
| `Mã SKU kho`        | Cột được ghi. Khớp không phân biệt hoa thường                                   |
| `Số lượng tiêu hao` | Cột được ghi. Nguyên trong khoảng 1–1.000.000; bỏ trống = 1                     |

Vì sao khoá dự phòng là **một cặp** chứ không phải `Mã SKU sàn` đứng một mình: `PlatformSku.sellerSkuCode` là `NULLABLE`, và thứ bảng thật sự bảo đảm duy nhất là `(platform, shopId, externalSkuId, tenantId)` — hai gian hàng cùng bán một mã là chuyện thường. Cặp khớp ra **nhiều hơn một** biến thể → dòng đó `platform_sku_ambiguous`, **không đoán**.

**Cấu trúc N:1 diễn tả bằng nhiều dòng cùng khoá.** Một SKU sàn ghép với ba SKU kho là **ba dòng** lặp lại bốn cột đầu. Trần **50 dòng cho một SKU sàn**, cùng trần với `PUT /sku-mappings/platform-skus/{id}` — vượt thì cả cụm nhận `too_many_components_for_variant`. Import gom mọi dòng cùng một SKU sàn thành **một cụm** rồi áp bằng đường **thay nguyên bộ** trong một transaction — ghi từng dòng rời vừa để BR-04-04 phán trên một cụm nửa vời, vừa không diễn tả nổi vế "cập nhật" của AC, vì chỉ thay nguyên bộ mới xoá được thành phần mà file không còn liệt kê.

**Ô `Mã SKU kho` để trống = không có chỉ thị gì cho biến thể đó** — ghép nối hiện tại giữ nguyên, dòng tính vào `skippedRows`. Nhờ vậy import lại đúng file vừa export mà chưa sửa gì là **no-op**: export liệt kê cả biến thể chưa ghép, và những dòng đó ra file với hai cột cuối rỗng. Muốn gỡ ghép nối thì dùng `DELETE /api/v1/sku-mappings/{id}` (FR-04-08), nơi có cảnh báo kiện chờ.

**Xử lý lỗi bán phần.** Không rollback cả file: mỗi cụm là một transaction riêng, cụm hỏng bị bỏ qua kèm lý do, mọi cụm còn lại vẫn được ghi. Một dòng không đọc được sẽ kéo **cả cụm** của nó xuống — áp phần đọc được trong khi bỏ dòng hỏng sẽ âm thầm xoá mất thành phần mà dòng đó khai.

```jsonc
// POST /api/v1/sku-mappings/import   (multipart 'file', hoặc JSON { "key": "files/…xlsx" })
// → 202
{ "id": "01973f40-...", "status": "QUEUED", "totalRows": 0, "processedRows": 0,
  "succeededRows": 0, "skippedRows": 0, "failedRows": 0, "errors": [], "createdAt": "…" }

// GET /api/v1/sku-mappings/import/{importId} → 200
{ "id": "01973f40-...", "status": "COMPLETED_WITH_ERRORS",
  "totalRows": 1000, "processedRows": 1000, "succeededRows": 940, "skippedRows": 55,
  "failedRows": 5, "completedAt": "…",
  "errors": [
    { "rowNumber": 12, "reason": "merchant_sku_not_found",
      "message": "SKU kho không tồn tại",
      "platformSkuRef": "01973f40-...", "merchantSkuCode": "KHO-404" }
  ] }
```

`status` ∈ `QUEUED | PROCESSING | COMPLETED | COMPLETED_WITH_ERRORS | FAILED` — cùng enum với FR-03-12. **`skippedRows` tách khỏi `failedRows`**: "file không nói gì về dòng này" không phải là "dòng này sai", và gộp hai số lại sẽ khiến một file hợp lệ trông như file hỏng.

**Dòng hỏng trả thẳng trong response** — `errors[]`, tối đa **50 dòng đầu** theo thứ tự trong file, **một phần tử cho mỗi dòng Excel hỏng**, kể cả các dòng khác của cùng cụm bị kéo theo. Không có file lỗi để tải: `failedRows` mới là tổng thật, một file hỏng quá 50 dòng là file cần sửa rồi nộp lại chứ không phải để đọc từng dòng. Danh sách này điền dần trong lúc job chạy, không đợi tới lúc kết thúc.

Mỗi phần tử có `rowNumber` · `platformSkuRef` · `merchantSkuCode` · `reason` · `message`. `reason` ∈ `mapping_row_key_missing` · `invalid_platform_sku_id` · `platform_sku_not_found` · `platform_sku_ambiguous` · `merchant_sku_not_found` · `merchant_sku_ambiguous` · `merchant_sku_unusable` · `invalid_quantity` · `duplicate_merchant_sku_for_variant` · `too_many_components_for_variant` · `mapping_conflicts_stock_push` · `unable_to_import_row`. `message` là câu tiếng Việt dựng sẵn cho `reason` đó — hiển thị được ngay; client muốn tự đặt câu chữ thì key theo `reason`.

> Đây là **chuỗi trong file Excel, không phải `ERROR_CODES`** — cùng khuôn với importer M03. Không mã lỗi HTTP mới nào được thêm cho FR-04-07.

```jsonc
// POST /api/v1/sku-mappings/export   body: { shopId?, mappingStatus?, q? }  (đúng ba bộ lọc của GET /sku-mappings)
// → 202
{ "jobId": "01973f41-..." }

// GET /api/v1/sku-mappings/export/{jobId} → 200
{ "id": "01973f41-...", "status": "DONE", "rowCount": 4210,
  "downloadUrl": "https://…/sku-mappings.xlsx", "failureReason": null, "completedAt": "…" }
```

`status` ∈ `QUEUED | RUNNING | DONE | FAILED`; `downloadUrl` chỉ xuất hiện khi `DONE`.

Ba điều dễ bỏ sót:

- **Bộ lọc bị đóng băng vào job lúc bấm.** Chạy lại bộ lọc ở thời điểm worker tiêu thụ có thể ôm cả listing vừa xuất hiện sau khi người dùng nhìn — đúng thứ "export theo bộ lọc hiện tại" loại trừ.
- **Phạm vi gian hàng cũng bị đóng băng**, vào cả job import lẫn job export. Worker chạy không có ngữ cảnh request nên không tự suy được quyền; thiếu ảnh chụp này thì một thành viên chỉ được giao gian hàng A upload file chứa listing gian hàng B vẫn ghi được.
- **Chỉ người tạo job đọc lại được job đó** (404 cho người khác) — file mang đúng phạm vi mà người đó thấy tại thời điểm chạy.

**Mã lỗi nghiệp vụ** — tên đề xuất, phải thêm vào `ERROR_CODES` trước khi implement.

| `code`                         | `kind` → status    | Tình huống                                                                            | `detail` seller-facing                                                                       | BR       |
| ------------------------------ | ------------------ | ------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- | -------- |
| `sku_delete_blocked`           | `conflict` → 409   | Xoá SKU đang có tồn / ghép nối / là thành phần combo / đã phát sinh giao dịch         | "Không thể xoá SKU này — {lý do cụ thể}. Chuyển sang Ngừng bán thay vì xoá."                 | BR-03-04 |
| `sku_code_locked`              | `validation` → 422 | Sửa mã SKU đã phát sinh giao dịch                                                     | "Mã SKU không thể đổi sau khi đã có giao dịch."                                              | BR-03-05 |
| `mapping_not_found`            | `not_found` → 404  | Không có biến thể sàn đó trong tenant, hoặc nằm ngoài phạm vi gian hàng của người gọi | "Không tìm thấy ghép nối SKU."                                                               | FR-01-05 |
| `mapping_already_exists`       | `conflict` → 409   | `POST` cho biến thể đã có ghép nối                                                    | "SKU sàn này đã có ghép nối — dùng thao tác sửa để thay cả bộ thành phần."                   | AC M04   |
| `mapping_conflicts_stock_push` | `conflict` → 409   | Bộ SKU kho sau khi ghi **trùng khít** bộ của một biến thể khác trong cùng gian hàng   | "Gian hàng này đã có SKU sàn {platformSkuId} bán đúng bộ hàng đó — đẩy tồn sẽ chồng nhau."   | BR-04-04 |
| `mapping_target_unusable`      | `validation` → 422 | SKU kho đích đã xoá mềm / `DISCONTINUED` / combo chưa khai thành phần                 | "SKU kho này không ghép nối được: đã xoá, đã ngừng bán, hoặc là combo chưa khai thành phần." | BR-03-02 |
| `mapping_has_pending_packages` | `conflict` → 409   | Gỡ ghép nối khi còn kiện chờ xử lý                                                    | Xem ví dụ trên                                                                               | AC M04   |

> `sku_delete_blocked` nay phủ **cả ghép nối**: `DELETE /api/v1/merchant-skus/{id}` từ chối một SKU đang được biến thể sàn ghép tới. Khoá ngoại `Restrict` của `sku_mappings` chỉ chặn xoá cứng, mà đây là xoá mềm — không kiểm ở tầng ứng dụng thì dòng ghép nối sống sót và listing tiếp tục tự nhận là đã ghép trong khi không nhặt được gì.
> **Giới hạn khi build đồng bộ hàng loạt:** `Search Products` chỉ trả key properties — mỗi `platformProductId` cần thêm một lệnh `GET /api/v1/shops/{shopId}/platform-products/{id}` để lấy đủ giá/tồn/biến thể (N+1). Endpoint sync hàng loạt phải tính chi phí này vào ngân sách rate limit trước khi FE cho phép "Đồng bộ lại toàn bộ" trên shop lớn (FR-03-02). Đồng bộ toàn shop chắc chắn vượt `HTTP_REQUEST_TIMEOUT_MS` nên phải là job nền có endpoint hỏi tiến độ (§0.4), không phải một request đồng bộ.
> Webhook Product creation chỉ bắn cho `COMBINED_PRODUCT`/`GPR_PRODUCT` — không dùng làm tín hiệu duy nhất cho quy tắc ghép nối tự động (FR-04-10, P1).

---

## 3. OrderProvider — M06 Đơn hàng (danh sách & chi tiết)

| Method   | Path                                 | FR                          | Ghi chú                                       |
| -------- | ------------------------------------ | --------------------------- | --------------------------------------------- |
| GET      | `/api/v1/orders`                     | FR-06-01, 06-02, 06-03 (P0) | Hợp nhất đa shop, filter đầy đủ, tìm nhanh    |
| GET      | `/api/v1/orders/summary`             | FR-06-01 (P0)               | Badge đếm 5 tab + Chờ RTS — xem §3.3          |
| GET/POST | `/api/v1/orders/flags`               | FR-06-19 (P0)               | Cờ cấp đơn, xem §3.4                          |
| GET/POST | `/api/v1/order-filters`              | FR-06-04 (P0)               | Bộ lọc lưu, đặt tên, ghim                     |
| POST     | `/api/v1/orders/exports`             | FR-06-06 (P0)               | Nhận trọn bộ lọc của `GET /orders`, xem §3.2b |
| GET      | `/api/v1/orders/exports/{exportId}`  | FR-06-06 (P0)               | Tiến độ phiên xuất đã xếp hàng                |
| GET      | `/api/v1/orders/{orderId}`           | FR-06-08…14 (P0)            | Chi tiết đơn, 2 tầng order→package (FR-06-26) |
| GET      | `/api/v1/orders/{orderId}/tracking`  | FR-06-14 (P0)               | Hành trình vận chuyển                         |
| GET/POST | `/api/v1/packages/{packageId}/notes` | FR-06-13 (P0)               | Ghi chú **cấp kiện** (BR-06-06), xem §3.5     |

**Request/response rút gọn:**

```json
// GET /api/v1/orders?tab=..&shopId=..&status=..&flagId=..&orderSource=..&search=..
//   &orderedFrom=..&orderedTo=..&paidFrom=..&paidTo=..&sku=..&buyerName=..&buyerPhone=..
//   &externalOrderId=..&trackingNumber=..&limit=50&cursor=..
// Mọi tiêu chí gộp bằng AND.
// tab nhận: all | processing | shipping | delivered | cancelled (xem §3.3).
// orderSource nhận 6 giá trị content_type của TikTok, cộng "UNKNOWN" để lọc đơn chưa xác định nguồn.
// Khớp đơn có ÍT NHẤT MỘT dòng mang nguồn đó; "UNKNOWN" khớp đơn chưa dòng nào xác định được nguồn
// (giá trị này chỉ dùng khi lọc, không xuất hiện trong response).
// search là ô tìm nhanh: API tự nhận diện mã đơn / mã vận đơn / SĐT / tên người nhận. Mã khớp CHÍNH XÁC,
// tên và SĐT khớp MỘT PHẦN. Khi có search thì `tab` bị bỏ qua — tìm nhanh quét toàn tenant, không giới
// hạn theo tab đang mở (FR-06-03), nên FE cứ giữ nguyên tab trong query string lúc người dùng gõ.
{
  "object": "list",
  "url": "/api/v1/orders",
  "data": [
    {
      "id": "01973...",
      "externalOrderId": "string",
      "shopId": "01973...",
      "platform": "tiktok",
      "status": "UNPAID | ON_HOLD | AWAITING_SHIPMENT | PARTIALLY_SHIPPING | AWAITING_COLLECTION | IN_TRANSIT | DELIVERED | COMPLETED | CANCELLED",
      "buyerName": "***",
      "buyerPhone": "(+84) 90*-***-123",
      "buyerFullAddress": "string",
      "buyerAddressKind": "available | masked | not_synced",
      "buyerNameState": "available | masked | not_synced",
      "buyerPhoneState": "available | masked | not_synced",
      "platformSyncedAt": "2026-08-06T08:01:44.000Z | null",
      "fulfillmentType": "FULFILLMENT_BY_SELLER | FULFILLMENT_BY_TIKTOK | null",
      "buyerRequestedCancel": false,
      "flagId": "01973... | null",
      "sources": ["SHOP | VIDEO | LIVE | PRE_LIVE | PROMOTION_PAGE | LINKSHARE"],
      "lines": [{ "id": "01973...", "source": "LIVE | null" }], // chi tiết đơn có thêm 4 field, xem §3.6
      "currency": "VND",
      // Toàn bộ khối tiền cấp đơn nằm trong đây, không có field tiền nào phẳng ở cấp `data[]`.
      // Bốn con số đầu là của sàn và KHÔNG chặn theo quyền; `costOfGoods`/`profit` mới cần
      // `data.cost.view`/`data.profit.view`, và `unavailableReason` chỉ nói về hai cái đó.
      // `paymentPlatformDiscountAmount` là khuyến mãi do cổng thanh toán (ví, ngân hàng) tài trợ,
      // đứng NGOÀI `revenue` — khác `platformDiscountAmount` là sàn giảm giá hàng.
      "financialSummary": {
        "revenue": 450000,
        "originalTotalProductPrice": 500000,
        "sellerDiscountAmount": 30000,
        "platformDiscountAmount": 20000,
        "paymentPlatformDiscountAmount": 10000,
        "costOfGoods": null,
        // Hiệu số giữa tiền khách trả và số sàn trả (hoặc hứa trả) — KHÔNG chặn theo quyền.
        "totalFees": 29250,
        "profit": null,
        "profitMargin": null,
        "dataStatus": "estimated | reconciled | insufficient_data",
        "feeSource": "settlement | platform_estimate | rate_card",
        "unavailableReason": "permission | missing_sku_mapping | missing_cost_data | null"
      }
    }
  ],
  "pageInfo": { "hasNextPage": true, "nextCursor": "MjAyNi0wOC0wNlQwOToz..." }
}
```

> **Bản trước của mục này đã lỗi thời ở bốn chỗ**, sửa ngày 25/08/2026 cho khớp `openapi/openapi.json`:
> `platformOrderNo` → **`externalOrderId`**; `status` không phải 7 giá trị chữ thường mà là **9 token
> gốc của TikTok viết hoa**; `buyer` **không lồng** mà là bốn field phẳng `buyerName` / `buyerPhone` /
> `buyerFullAddress` / `buyerAddressKind`; `revenue` → `totalAmount`. `docs/mock/types.ts` của FE
> vốn đã đúng ở ba chỗ đầu — chỉ tài liệu này sai.
>
> **Cập nhật 27/08/2026.** Hai câu trên đã bị chính code lật lại, sửa cho khớp
> `openapi/openapi.json` hiện tại: `packages[]` và `financialSummary` **nay đều có trong response**.
> Và `totalAmount` **không còn phẳng ở cấp đơn** — nó là `financialSummary.revenue`, cùng chỗ với
> `originalTotalProductPrice` / `sellerDiscountAmount` / `platformDiscountAmount` /
> `paymentPlatformDiscountAmount` vừa gom vào. Ở cấp `data[]` chỉ còn `currency`; tiền theo từng
> dòng hàng vẫn nằm phẳng trong `lines[]`.
>
> Phần xuất file **không** đọc theo khoá của DTO này: mỗi cột xuất có hàm lấy giá trị riêng
> (`orders-export.definition.ts`), nên `totalAmount` là một cột hợp lệ và điền đúng
> `financialSummary.revenue`. Danh mục cột đầy đủ ở §3.2b.

**Bốn cặp tên lệch giữa `docs/mock/types.ts` và API — dùng tên bên phải.** FE mock đặt tên trước khi
BE chốt, nên bốn field này cùng nghĩa nhưng khác tên; API là bên đúng vì tên bám theo ERD và
`openapi/openapi.json`:

| `docs/mock/types.ts`      | API (`OrderResponseDto`)  | Nguồn TikTok              |
| ------------------------- | ------------------------- | ------------------------- |
| `rtsDueAt`                | `fulfillmentDueAt`        | `rts_sla_time`            |
| `cancellationInitiator`   | `cancelledBy`             | `cancellation_initiator`  |
| `lines[].variantName`     | `lines[].skuName`         | `line_items[].sku_name`   |
| `lines[].platformSkuCode` | `lines[].externalSkuCode` | `line_items[].seller_sku` |

`fulfillmentDueAt` là hạn sàn áp để bàn giao cho đơn vị vận chuyển — cái đồng hồ đếm ngược "quá hạn
giao vận" ở màn danh sách đọc field này, không phải `rtsAt` (`rtsAt` là lúc lệnh giao đã gọi xong).

FE không dựng phân trang số trang trên danh sách đơn — chỉ tải tiếp theo `pageInfo.nextCursor` (§0.2).

**Ba trạng thái của một ô sàn trả (`buyerNameState`, `buyerPhoneState`, `buyerAddressKind`).**
`available` là có giá trị dùng được. `masked` là sàn có trả nhưng đã che bớt bằng `*` — dữ liệu
hợp lệ và **sẽ không bao giờ đầy đủ hơn**, đừng chờ đồng bộ lại. `not_synced` là **ViShop không
có dữ liệu cho ô này**; giá trị đi kèm luôn là `null`, không bao giờ là chuỗi rỗng.

`not_synced` **không khẳng định được vì sao**: có thể sàn chưa trả (đơn chưa thanh toán thì chưa
có người nhận), có thể đơn chưa qua lượt đồng bộ nào. Đọc **`platformSyncedAt`** để tách hai khả
năng: `null` là chưa đồng bộ lần nào — chờ lượt sau là có; có giá trị là đã đồng bộ rồi mà sàn
vẫn không trả ô đó. Không có cách nào ở tầng API phân biệt "sàn không trả" với "ViShop map sai",
chỗ đó thuộc log/monitoring chứ không thuộc response (chốt 4 ngày 25/08: không expose payload thô).

**Quy ước viết hoa giá trị enum.** Giá trị nào **bắt nguồn từ sàn** thì giữ nguyên token gốc dạng `UPPER_SNAKE`: `status`, `shippingType`, `source`, `fulfillmentType`, `stage` (§4.3), `cancelledBy`. Không dịch, không hạ chữ thường — đối soát với payload TikTok phải so được trực tiếp. Giá trị nào **do ViShop tự đặt** thì viết thường: `tab` (`processing`…), `buyerAddressKind` (`available`…), và `code` của lỗi.

Bản trước của tài liệu này viết "giá trị enum giữ snake_case thường", mâu thuẫn với chính đoạn `shippingType` ngay dưới và với `docs/mock/types.ts` — đã bỏ.

`shippingType` dùng thẳng 2 token của sàn là `TIKTOK`/`SELLER` (ERD `package.shippingType`). Bản trước của tài liệu này ghi `tiktok_shipping`/`seller_shipping` — không khớp cả OpenAPI của TikTok lẫn FE đã code (`shippingType === 'TIKTOK'`).

`buyer.phone`/`buyer.name` có thể tới từ sàn đã bị che một phần (dạng `(+1)213-***-1234`) khi đơn dùng platform logistics — đây là dữ liệu gốc từ `Get Package Detail`, không phải ViShop tự che. UI phải chấp nhận giá trị masked làm dữ liệu hợp lệ, không coi là lỗi hiển thị (FR-06-08).

`buyer` là nhóm field nhạy cảm: quyền xem số điện thoại/địa chỉ được cắt bằng **mapping DTO tường minh** ở tầng presentation (§0.2), không dựa vào interceptor serialize. Người không đủ quyền nhận `buyer` đã lược field, không nhận field rỗng.

Nguồn đơn lấy từ endpoint riêng `Search Seller Affiliate Orders` (seller-scope, không cần TAP), lookback tối đa 3 tháng/lần gọi — sync job cho field này **tách khỏi** job sync Order chính, không suy ra nguồn chỉ từ Order API (FR-06-11).

TikTok trả `content_type` **theo từng SKU**, nên nguồn được lưu ở từng dòng hàng: `lines[].source` là nguồn của đúng dòng đó (`null` khi chưa xác định), còn `sources` ở cấp đơn là tập các nguồn distinct đã xác định được của đơn — mảng rỗng nghĩa là chưa dòng nào có nguồn. Một đơn mua một phần từ livestream và một phần từ video hợp lệ mang cả hai giá trị. Field `source` đơn lẻ ở cấp đơn **không còn** — bản trước gộp về một giá trị và bỏ cả đơn về "chưa xác định" khi các dòng khác nhau, làm mất dữ liệu không khôi phục được.

`financialSummary.costOfGoods`/`profit` là `null` khi user thiếu quyền `Xem giá vốn`/`Xem lợi nhuận`, hoặc khi SKU chưa có giá vốn (BR-10-02) — hai lý do khác nhau, response phân biệt bằng `financialSummary.unavailableReason: "permission" | "missing_sku_mapping" | "missing_cost_data"` để FE hiển thị đúng thông điệp.

**Giá vốn `0` là "chưa nhập", không phải "hàng miễn phí" (VIS-163).** `merchant_skus.avgCost` là `NOT NULL DEFAULT 0`, nên SKU chưa ai định giá tới đây dưới dạng số 0. Đọc 0 là giá vốn thật thì mọi đơn như vậy hiện biên lợi nhuận đúng bằng `100% − 5% − 1,5% = 93,5%` — dấu vân tay của lỗi này. Combo cũng vậy: BR-03-03 không lưu giá vốn của combo, nó là Σ(giá vốn thành phần × SL), và **một thành phần chưa có giá làm cả combo mất giá vốn** thay vì cộng phần biết rồi bỏ phần không biết.

`financialSummary.totalFees` và `feeSource` **không bị chặn theo quyền**: phí là khoản sàn thu, không phải con số nói về biên lợi nhuận của gian hàng, và không có `totalFees` thì `revenue − costOfGoods − ? = profit` là hộp đen người xem không kiểm chứng được. Chi tiết ba mức của `feeSource` ở §6.

---

### 3.2b Xuất đơn hàng ra file — FR-06-06

`POST /api/v1/orders/exports` nhận **trọn bộ lọc của `GET /api/v1/orders`** trừ `limit` và `cursor`
— file phải chứa đúng tập đơn đang hiện trên màn, nên hai bên dùng chung một bản dịch bộ lọc. Cần
quyền `orders.export`.

Endpoint có **hai đường trả về**, phân biệt bằng mã HTTP và bằng `status`:

```json
// POST /api/v1/orders/exports
// body: mọi tham số của GET /orders (trừ limit/cursor) + { columns?: string[], format?: "xlsx" | "csv" }

// → 200 khi tập đơn nhỏ: file dựng ngay trong request, KHÔNG tạo bản ghi phiên xuất
{
  "status": "done",
  "exportId": null,
  "rowCount": 42,
  "estimatedRows": null,
  "downloadUrl": "https://…",
  "containsRestrictedColumns": false
}

// → 202 khi tập đơn lớn: đã xếp hàng chạy nền
{
  "status": "queued",
  "exportId": "01973b8e-…",
  "rowCount": null,
  "estimatedRows": 12840,
  "downloadUrl": null,
  "containsRestrictedColumns": true
}

// GET /api/v1/orders/exports/{exportId} → 200
{
  "id": "01973b8e-…",
  "status": "queued",            // queued | running | done | failed
  "rowCount": null,
  "failureReason": null,
  "downloadUrl": null,           // link ký sẵn, hạn 24h, chỉ có khi status = done
  "containsRestrictedColumns": true,
  "createdAt": "2026-09-03T03:00:00.000Z",
  "completedAt": null
}
```

**Ngưỡng chuyển giữa hai đường do vận hành đặt** (`ORDERS_EXPORT_SYNC_THRESHOLD_ROWS`, mặc định
200 đơn), FE không chọn được và không nên đoán theo số đơn — cứ đọc `status`. `GET` chỉ phục vụ
phiên đã xếp hàng; phiên chạy thẳng không tạo bản ghi nào và file của nó đã nằm trong `downloadUrl`
của chính lượt `POST` đó.

**Danh mục cột** (`columns[]`, gửi khoá camelCase; bỏ trống thì dùng 10 cột đánh dấu ✅):

| Cột                | Mặc định | Quyền trường cần có   |
| ------------------ | -------- | --------------------- |
| `externalOrderId`  | ✅       | —                     |
| `status`           | ✅       | —                     |
| `sources`          | ✅       | —                     |
| `orderedAt`        | ✅       | —                     |
| `paymentMethod`    | ✅       | —                     |
| `buyerName`        | ✅       | —                     |
| `shopName`         | ✅       | —                     |
| `paidAt`           | ✅       | —                     |
| `totalAmount`      | ✅       | —                     |
| `buyerPhone`       | ✅       | `data.buyer_pii.view` |
| `fulfillmentDueAt` | —        | —                     |
| `buyerFullAddress` | —        | `data.buyer_pii.view` |
| `costOfGoods`      | —        | `data.cost.view`      |
| `profit`           | —        | `data.profit.view`    |

- Khoá ngoài danh mục ⇒ **422**. Mảng rỗng cũng **422** — không cột nào thì không có file để xuất;
  muốn bộ mặc định thì bỏ hẳn tham số.
- Cột cần quyền mà người gọi không có ⇒ **cột biến mất khỏi cả header**, không phải ô rỗng, và
  **không** phải lỗi.
- `containsRestrictedColumns` trả lời về **tài khoản, không phải về lượt xuất vừa rồi**: `true`
  nghĩa là người gọi bị chặn khỏi ít nhất một cột trong **toàn bộ** bảng trên, kể cả cột lần này
  không chọn. Người có `data.buyer_pii.view` nhưng thiếu `data.cost.view` vẫn nhận `true` dù chỉ xin
  đúng `externalOrderId`. Cố ý như vậy để cờ không nhấp nháy theo thao tác tick/bỏ tick cột — FE
  dùng nó để hiện một dòng "tài khoản của bạn không xuất được đủ cột", không phải để suy ra tệp vừa
  nhận thiếu cột nào.
- Thứ tự cột trong file là thứ tự chuẩn ở bảng trên, không phải thứ tự gửi lên.
- Lọc theo `buyerPhone` khi thiếu `data.buyer_pii.view` ⇒ **403**, giống `GET /orders`: `rowCount`
  của phiên xuất cũng đủ để dò ra một số có trong hệ thống hay không.

**Bộ lọc và phạm vi gian hàng bị đóng băng vào phiên lúc bấm.** Worker chạy không có ngữ cảnh
request nên không tự suy được quyền; thiếu ảnh chụp này thì thành viên chỉ được giao gian hàng A
vẫn nhận được file chứa đơn gian hàng B.

`GET /api/v1/orders/exports/{exportId}` trả **404** cho mã không tồn tại, phiên của tổ chức khác,
phiên của người khác, hoặc mã của một loại xuất khác — không phân biệt, vì phân biệt là đã tiết lộ
người khác đã xuất gì.

---

### 3.3 Tab trạng thái và badge đếm — FR-06-01

Danh sách gom 9 trạng thái sàn thành 5 tab. Bản đồ do **API sở hữu** (`libs/shared/order-status-tab.ts`), không phải FE tự gom — nếu hai bên mỗi bên giữ một bản thì badge sẽ nói khác số dòng bên dưới nó.

| `tab`        | Gồm các `status`                                               |
| ------------ | -------------------------------------------------------------- |
| `all`        | không lọc theo trạng thái                                      |
| `processing` | `UNPAID`, `ON_HOLD`, `AWAITING_SHIPMENT`, `PARTIALLY_SHIPPING` |
| `shipping`   | `AWAITING_COLLECTION`, `IN_TRANSIT`                            |
| `delivered`  | `DELIVERED`, `COMPLETED`                                       |
| `cancelled`  | `CANCELLED`                                                    |

`tab` và `status` **gộp bằng AND**, không cái nào ghi đè cái nào: `?tab=shipping&status=CANCELLED` trả rỗng vì đó là mâu thuẫn client tự viết ra.

```jsonc
// GET /api/v1/orders/summary — nhận CÙNG bộ query của GET /api/v1/orders, bỏ qua `tab` và `status`
{
  "all": 2340,
  "processing": 412,
  "shipping": 890,
  "delivered": 1002,
  "cancelled": 36,
  "pendingRts": 385,
}
```

FE gọi song song với `GET /api/v1/orders` — một màn hình vẫn chỉ tốn hai request, không phải N. Đếm bằng một lệnh `GROUP BY status` duy nhất.

`pendingRts` là thẻ "Chờ RTS" (`status = AWAITING_SHIPMENT`), **tập con thật sự** của `processing` chứ không phải tab thứ sáu — `PARTIALLY_SHIPPING` cũng nằm trong `processing` nhưng phần đã gửi thì không còn RTS.

Hai thẻ **Thiếu tồn** và **Bất thường** của màn danh sách **chưa có trong response này**, và cố ý không trả `0`: thiếu tồn cần tồn khả dụng từ M05 (FR-06-20), bất thường cần bảng bất thường chưa tồn tại (FR-06-21). Trả `0` sẽ hiển thị thành "0 đơn thiếu tồn" — một khẳng định sai.

### 3.4 Cờ đánh dấu — FR-06-19

**Cờ gắn ở cấp `order`, không phải `package`** (chốt 25/08/2026). Bản trước của tài liệu ghi `POST /api/v1/packages/flags` — sai, và `docs/mock/types.ts` (`Order.flags`) cùng `docs/spec/30-don-hang.md` vốn đã đúng.

**Một đơn mang tối đa một cờ.** Gắn cờ mới là **thay thế**, không cộng dồn.

| Method | Path                            | Quyền                | Mã 2xx  |
| ------ | ------------------------------- | -------------------- | ------- |
| GET    | `/api/v1/orders/flags`          | `orders.view`        | 200     |
| POST   | `/api/v1/orders/flags`          | `orders.flag.manage` | **201** |
| PATCH  | `/api/v1/orders/flags/{flagId}` | `orders.flag.manage` | 200     |
| DELETE | `/api/v1/orders/flags/{flagId}` | `orders.flag.manage` | 204     |
| POST   | `/api/v1/orders/flags/assign`   | `orders.flag.manage` | 200     |

Chỉ `POST /orders/flags` trả **201** vì nó tạo ra một tài nguyên mới; `assign` là POST nhưng chỉ sửa
đơn đã có nên trả **200**, cùng quy ước với các POST-không-tạo khác trong API này.

```jsonc
// POST /api/v1/orders/flags/assign — gắn/gỡ hàng loạt, tối đa 500 đơn mỗi lần
// flagId: null để GỠ cờ. Thiếu hẳn field flagId là 422, không phải "gỡ".
{ "orderIds": ["01973...", "01973..."], "flagId": "01973... | null" }

// → 200
{ "updated": 2, "matched": 3 }
```

`matched` là số đơn trong danh sách gửi lên mà **thực sự thuộc tenant và data scope** của người gọi; `updated` là số đơn đổi cờ thật. `matched` nhỏ hơn số id gửi lên nghĩa là có id không phải của họ — đơn ngoài scope bị bỏ qua im lặng, không báo lỗi (cùng lý do `?shopId=` ngoài scope trả rỗng chứ không 404).

Xoá một cờ còn đang gắn trên đơn → **409 `order_flag_in_use`**, không tự gỡ nhãn khỏi những đơn người dùng không chọn. `color` là hex 6 chữ số (`#F97316`); tên cờ trùng trong cùng tenant → **409 `order_flag_name_already_exists`**.

### 3.5 Ghi chú hai loại — FR-06-13

**Ghi chú gắn ở cấp `package`, không phải `order`** (BR-06-06, chốt 27/08/2026). Bản trước của tài
liệu ghi `POST /api/v1/orders/{orderId}/notes` — sai: một đơn tách thành ba kiện thì người nhặt mỗi
kiện đọc một thứ khác nhau, và phiếu đóng gói (FR-07-04) in theo kiện.

**Một kiện mang được nhiều ghi chú của nhiều người.** POST là **thêm**, không ghi đè.

| Method | Path                                          | Quyền              | Mã 2xx  |
| ------ | --------------------------------------------- | ------------------ | ------- |
| GET    | `/api/v1/packages/{packageId}/notes`          | `orders.view`      | 200     |
| POST   | `/api/v1/packages/{packageId}/notes`          | `orders.note.edit` | **201** |
| PATCH  | `/api/v1/packages/{packageId}/notes/{noteId}` | `orders.note.edit` | 200     |
| DELETE | `/api/v1/packages/{packageId}/notes/{noteId}` | `orders.note.edit` | 204     |

Cả bốn route đều **bắt buộc `?shopId=`**, như mọi route `/packages/*` thao tác trên **một kiện**:
kiện thuộc về gian hàng chứ không thuộc trực tiếp về tổ chức. Kiện ngoài phạm vi gian hàng của
người gọi trả **404**, không trả danh sách rỗng — danh sách rỗng đã là một lời khẳng định về kiện đó.
Ngoại lệ duy nhất là `GET /api/v1/packages` (danh sách), nơi `shopId` là bộ lọc chứ không phải
khoá — xem §4.2.

```jsonc
// POST /api/v1/packages/{packageId}/notes?shopId=01973...
{ "type": "PICKING | INTERNAL", "content": "Gói thêm màng xốp hơi." }

// → 201
{
  "id": "01973...",
  "packageId": "01973...",
  "type": "PICKING",
  "content": "Gói thêm màng xốp hơi.",
  "createdByUserId": "01973...",
  "updatedByUserId": null,
  "createdAt": "2026-08-27T03:00:00.000Z",
  "updatedAt": "2026-08-27T03:00:00.000Z"
}
```

**`type` không phải nhãn hiển thị, nó là ràng buộc dữ liệu.** `PICKING` được in ra phiếu đóng gói
theo thứ tự thời gian tạo; `INTERNAL` **không bao giờ** lọt vào bất kỳ luồng in nào. Ràng buộc nằm ở
tầng repository — luồng in chỉ có đúng một hàm đọc và hàm đó cố định điều kiện `PICKING`, không nhận
tham số — nên không có cách gọi nào lấy được ghi chú nội bộ ra phiếu. Muốn gỡ một ghi chú khỏi phiếu
mà vẫn giữ nội dung thì `PATCH` đổi `type` sang `INTERNAL`.

`content` tối đa 500 ký tự, bị cắt khoảng trắng hai đầu trước khi lưu; rỗng sau khi cắt → **422**
(muốn bỏ ghi chú thì gọi `DELETE`, không lưu chuỗi rỗng). Ghi chú của kiện thuộc gian hàng khác →
**404 `package_note_not_found`**, giống hệt trường hợp id không tồn tại.

Mọi thao tác tạo/sửa/xoá đều ghi vào lịch sử thao tác của kiện (FR-06-12) trong **cùng transaction**
với chính ghi chú: `PACKAGE_NOTE_ADDED` · `PACKAGE_NOTE_UPDATED` · `PACKAGE_NOTE_DELETED`, kèm
`payloadBefore` nên nội dung đã xoá vẫn tra lại được.

### 3.6 Dòng sản phẩm ở chi tiết đơn — FR-06-09

Chi tiết đơn dùng cùng cấu trúc dòng hàng như danh sách, **cộng thêm bốn field ghép nối kho**.
Danh sách cố ý không có chúng: nối danh mục kho cùng thành phần combo cho mọi dòng của mọi đơn
trong một trang là nhân khối lượng truy vấn cho một màn hình không hiển thị chúng.

```jsonc
// GET /api/v1/orders/{orderId} → lines[]
{
  // …mọi field như dòng hàng ở danh sách…
  "mappingStatus": "mapped | unmapped | sku_deleted",
  "merchantSkuCode": "AO-THUN-DEN-L | null",
  "merchantSkuName": "Áo thun cotton unisex - Đen / L | null",
  "components": [
    {
      "merchantSkuId": "01973...",
      "skuCode": "AO-DEN-L",
      "skuName": "Áo thun đen L",
      "quantity": 2,
    },
  ],
}
```

`mappingStatus` có **ba** giá trị, không phải hai: `unmapped` là chưa ai ghép nối; `mapped` là đã
ghép và SKU kho còn dùng được; `sku_deleted` là dòng vẫn trỏ tới một SKU kho **đã bị xoá**. Ba
trạng thái này dẫn tới ba việc khác nhau, nên phải hiển thị khác nhau. `merchantSkuCode` và
`merchantSkuName` là `null` khi `mappingStatus` khác `mapped`.

`merchantSkuName` là tên trong **danh mục kho của ViShop** và đổi theo danh mục — khác
`lines[].productName` / `lines[].skuName` vốn là tên **của sàn**, chụp lại tại thời điểm bán và
không bao giờ đổi (BR-06-05).

> **Lưu ý cho FE:** khoá `skuName` xuất hiện ở **hai chỗ với hai nghĩa khác nhau**.
> `lines[].skuName` là tên biến thể **của sàn**; `lines[].components[].skuName` là tên SKU **kho**
> của thành phần combo. Chúng ở hai cấp lồng nhau khác nhau nên không đè nhau, nhưng đừng map chung
> một kiểu dữ liệu/nhãn hiển thị cho cả hai.

`components` **rỗng với SKU thường** — đó là trạng thái bình thường, không phải thiếu dữ liệu.
Với SKU combo, đây mới là thứ phải nhặt khỏi kệ (combo không giữ tồn vật lý riêng). `quantity` của
thành phần tính cho **một** đơn vị combo, nhân với `quantity` của dòng để ra tổng phải nhặt. Thành
phần có SKU đã bị xoá mềm bị loại khỏi mảng, vì người kho không nhặt được thứ không còn trong danh mục.

### 3.7 Quy tắc tự động — FR-06-23, FR-06-24

Một quy tắc gồm nhiều điều kiện gộp bằng `AND`/`OR` và nhiều hành động chạy nối tiếp.

| Method | Path                                       | FR                      | Ghi chú                            |
| ------ | ------------------------------------------ | ----------------------- | ---------------------------------- |
| GET    | `/api/v1/orders/automation-rules`          | FR-06-23 (P1)           | Sắp theo `priority` tăng dần       |
| POST   | `/api/v1/orders/automation-rules`          | FR-06-23, FR-06-24 (P1) |                                    |
| PATCH  | `/api/v1/orders/automation-rules/{ruleId}` | FR-06-23 (P1)           | Gửi trọn bộ điều kiện và hành động |
| DELETE | `/api/v1/orders/automation-rules/{ruleId}` | FR-06-23 (P1)           |                                    |
| POST   | `/api/v1/orders/automation-rules/simulate` | FR-06-24 (P1)           | Đếm số đơn khớp, chỉ đọc           |

```jsonc
// GET /api/v1/orders/automation-rules → phần tử của data[]
{
  "id": "01973f80-...",
  "name": "Đơn Livestream giá trị cao — ưu tiên đi trước",
  "enabled": true,
  "priority": 20,
  "logic": "AND",
  "conditions": [
    { "field": "source", "value": "LIVE" },
    { "field": "revenueAbove", "value": 2000000 },
  ],
  "actions": [
    { "type": "auto_flag", "flag": "Giao gấp" },
    { "type": "set_print_priority", "level": "high" },
    { "type": "add_to_wave" },
  ],
  "shopIds": ["01973f1a-..."],
  "createdAt": "2026-07-22T09:00:00+07:00",
  "matchedCount30d": 128,
  "lastMatchedAt": "2026-08-11T14:20:00+07:00",
}
```

`conditions[]` và `actions[]` là union tagged theo `field`/`type`. Thứ tự phần tử trong `actions[]` là thứ tự chạy. Giá trị số (`revenueAbove`, `itemCountAbove`) trả về kiểu number.

**Gian hàng (`shopIds`).** Quy tắc chạy ở gian hàng nào chỉ nằm ở `shopIds` — không có loại điều kiện gian hàng. POST/PATCH gửi `shopIds` tuỳ chọn:

- Gửi danh sách: mọi gian phải trong phạm vi người gọi và đang kết nối với tenant, sai trả `422 automation_rule_shop_out_of_scope`. Phần tử trùng được gộp.
- Không gửi hoặc gửi `[]`: "mọi gian hàng tôi được làm" — chủ shop lưu `[]` (mọi gian, kể cả gian kết nối sau), nhân viên lưu đúng các gian đang được giao và còn kết nối. Ở PATCH, không gửi nghĩa là giữ nguyên; gửi `[]` cùng nghĩa với POST.
- `conditions` được để rỗng khi `shopIds` (sau khi tính) khác rỗng — nghĩa là mọi đơn của các gian đó. Rỗng cả hai trả `422 automation_rule_incomplete`.

`matchedCount30d` và `lastMatchedAt` chỉ có ở `GET`. Quy tắc đang bật với `matchedCount30d = 0` là dấu hiệu điều kiện đặt sai.

**Thứ tự chạy.** Một đơn có thể khớp nhiều quy tắc. Hệ thống chạy theo `priority` tăng dần; trong một quy tắc, các hành động chạy theo `position` tăng dần. Hành động `block_processing` dừng toàn bộ chuỗi — các hành động sau nó và các quy tắc sau nó không chạy.

**Tạm giữ.** `auto_hold` và `block_processing` tạm giữ mọi kiện của đơn (như nút tạm giữ, lý do ghi tên quy tắc; `block_processing` ghi "Chờ duyệt tay"). Việc giữ chạy trước hành động của mọi quy tắc khớp, nên khi đơn khớp một quy tắc giữ thì `auto_rts` của mọi quy tắc khác bị bỏ qua — kể cả quy tắc `priority` nhỏ hơn, và kể cả khi giữ thất bại. Bỏ giữ bằng nút bỏ giữ kiện để xử lý tiếp.

**Tổ hợp hành động bị từ chối** (`422`, `automation_rule_actions_conflict`): `auto_rts` + `auto_hold`, `auto_rts` + `block_processing`, `block_processing` + `add_to_wave`. Kiểm tại thời điểm lưu vì `auto_rts` ghi sang sàn và không hoàn tác được.

```jsonc
// POST /api/v1/orders/automation-rules/simulate — chạy trên 200 đơn gần nhất, không ghi
{ "conditions": [{ "field": "revenueAbove", "value": 3000000 }], "logic": "AND", "sampleSize": 200 }
// → { "matchedCount": 37, "sampleSize": 200, "sampleOrders": [...] }
```

| Tình huống                                            | Kind → status      | `code`                                |
| ----------------------------------------------------- | ------------------ | ------------------------------------- |
| Điều kiện hoặc hành động thiếu giá trị bắt buộc       | `validation` → 422 | `automation_rule_value_required`      |
| `revenueAbove` hoặc `itemCountAbove` ≤ 0              | `validation` → 422 | `automation_rule_value_invalid`       |
| Tổ hợp hành động xung đột                             | `validation` → 422 | `automation_rule_actions_conflict`    |
| Trùng loại điều kiện hoặc hành động trong một quy tắc | `validation` → 422 | `automation_rule_duplicated_entry`    |
| Không có hành động, hoặc không điều kiện + không gian | `validation` → 422 | `automation_rule_incomplete`          |
| Trùng tên quy tắc trong tenant                        | `conflict` → 409   | `automation_rule_name_taken`          |
| `assign_warehouse` trỏ kho ngoài tenant               | `not_found` → 404  | `automation_rule_warehouse_not_found` |
| `shopIds` ngoài phạm vi hoặc không thuộc tenant       | `validation` → 422 | `automation_rule_shop_out_of_scope`   |
| Người bị giới hạn gian hàng mà không có gian hàng nào | `validation` → 422 | `automation_rule_shop_scope_required` |
| Người bị giới hạn sửa/xoá quy tắc ngoài phạm vi       | `not_found` → 404  | `order_automation_rule_not_found`     |

Quyền: `orders.automation.manage` cho mọi lệnh ghi, `orders.view` cho `simulate`.

> **`/api/v1/orders/automation-rules` chưa có implementation** (chỉ mới là hợp đồng API dự kiến
> cho engine điều kiện AND/OR + nhiều loại hành động của FR-06-23). VIS-217 (FR-06-24, "trọng tâm"
> của mục này) được triển khai độc lập, KHÔNG đi qua engine này — xem 3.7b ngay dưới. Khi
> `automation-rules` được xây, một action `auto_hold` ở đó có thể tái dùng lại chính engine đánh
> giá rủi ro của 3.7b (`evaluateRiskHold`), nhưng cấu hình/endpoint của hai cơ chế không gộp chung.

### 3.7b Chặn RTS tự động theo rủi ro nghiệp vụ — FR-06-24 (VIS-217)

Cơ chế phòng vệ hẹp, độc lập với engine 3.7: mỗi tenant chỉ cấu hình đúng 2 điều kiện (ngưỡng giá
trị đơn hàng, danh sách SKU nhạy cảm), không có AND/OR, không có nhiều loại hành động — thoả mãn
tự động là chặn RTS (`Package.heldAt/holdReason`), không có lựa chọn hành động khác. Áp dụng cho cả
Batch RTS và RTS đơn lẻ vì cả hai đi qua cùng `POST /api/v1/packages/shipments`.

| Method | Path                       | Quyền                      | Ghi chú                                                          |
| ------ | -------------------------- | -------------------------- | ---------------------------------------------------------------- |
| GET    | `/api/v1/order-risk-rules` | `orders.automation.manage` | Trả về cấu hình hiện tại, mặc định `null`/`[]` nếu chưa cấu hình |
| PUT    | `/api/v1/order-risk-rules` | `orders.automation.manage` | Cập nhật `revenueAboveMinorUnits` và/hoặc `sensitiveSkuIds`      |

```jsonc
// GET/PUT /api/v1/order-risk-rules
{
  "revenueAboveMinorUnits": 5000000, // null = chưa cấu hình, không chặn theo giá trị
  "sensitiveSkuIds": ["018f2e5a-0000-7000-8000-000000000009"], // MerchantSku.id
  "updatedAt": "2026-09-11T07:00:00.000Z",
}
```

Mở chặn thủ công dùng lại nguyên route đã có của FR-06-18: `DELETE
/api/v1/packages/{packageId}/hold` (quyền `orders.hold.manage`) — không có route
`POST .../unhold` riêng, tránh hai API cho cùng một hành động.

### 3.8 Lịch sử thao tác trên đơn — FR-06-12

```js
GET /api/v1/orders/{orderId}/history?limit=&cursor=
```

**Quyền:** `orders.view` · **Chỉ có `GET`.** Không có `POST`/`PATCH`/`DELETE` trên đường dẫn này —
AC-4 nói nhật ký bất biến, và cách thể hiện điều đó ở tầng API là không tồn tại đường ghi. Ở tầng
DB, trigger `enforce_event_log_immutability` cấm UPDATE/DELETE trên `event_logs`.

Gộp thao tác trên **chính đơn** với thao tác trên **mọi kiện thuộc đơn** vào một dòng thời gian, mới
nhất trước. Kiện gộp phục vụ nhiều đơn thì thao tác của nó xuất hiện ở lịch sử của **cả** những đơn
đó — đúng như `packages[]` của `GET /api/v1/orders/{orderId}` đã định nghĩa "kiện của đơn".

**Đây không phải** lịch sử đồng bộ (các lượt chạy job — xem §10.2 `GET /api/v1/orders/anomalies`),
và cũng không phải tiến trình trạng thái đơn. Ba thứ trả lời ba câu khác nhau: _ai đã làm gì_,
_đồng bộ có lỗi không_, _đơn đang ở đâu_.

```json
{
  "object": "list",
  "url": "/api/v1/orders/019dd1a5-0000-7000-8000-000000000001/history",
  "data": [
    {
      "id": "019dd200-0000-7000-8000-0000000000aa",
      "action": "PACKAGE_HELD",
      "occurredAt": "2026-09-03T10:12:00.000Z",
      "actor": {
        "type": "user",
        "id": "019dd1a5-0000-7000-8000-0000000000f1",
        "name": "Trần Thị Vận Hành"
      },
      "target": {
        "type": "PACKAGE",
        "id": "019dd1a5-0000-7000-8000-000000000003",
        "reference": "576912345678901"
      },
      "before": { "heldAt": null, "holdReason": null },
      "after": { "heldAt": "2026-09-03T10:12:00.000Z", "holdReason": "Khách xin đổi màu" }
    },
    {
      "id": "019dd1ff-0000-7000-8000-0000000000ab",
      "action": "ORDER_FLAG_ATTACHED",
      "occurredAt": "2026-09-03T09:40:12.000Z",
      "actor": {
        "type": "user",
        "id": "019dd1a5-0000-7000-8000-0000000000f2",
        "name": "Lê Văn Kho"
      },
      "target": {
        "type": "ORDER",
        "id": "019dd1a5-0000-7000-8000-000000000001",
        "reference": "5769000111222"
      },
      "before": { "flagId": null, "flagName": null },
      "after": { "flagId": "019dd1a5-0000-7000-8000-0000000000c7", "flagName": "Ưu tiên" }
    },
    {
      "id": "019dd1fe-0000-7000-8000-0000000000ac",
      "action": "ORDERS_STATUS_CHANGED",
      "occurredAt": "2026-09-03T09:05:00.000Z",
      "actor": { "type": "system", "id": null, "name": null },
      "target": {
        "type": "ORDER",
        "id": "019dd1a5-0000-7000-8000-000000000001",
        "reference": "5769000111222"
      },
      "before": { "status": "ON_HOLD" },
      "after": { "status": "AWAITING_SHIPMENT" }
    }
  ],
  "pageInfo": { "hasNextPage": false, "nextCursor": null }
}
```

**`action` — mười hai giá trị, mỗi giá trị một chỗ ghi.** Giá trị tiếng Anh; FE tự dịch nhãn, giống cách
nó đã làm với `type` của `/orders/anomalies`. BE **không** trả câu tiếng Việt dựng sẵn.

| `action`                                       | `target.type` | Khoá được trả trong `before`/`after` |
| ---------------------------------------------- | ------------- | ------------------------------------ |
| `ORDERS_STATUS_CHANGED`                        | ORDER         | `status`                             |
| `ORDER_FLAG_ATTACHED` · `ORDER_FLAG_DETACHED`  | ORDER         | `flagId`, `flagName`                 |
| `PACKAGE_HELD` · `PACKAGE_UNHELD`              | PACKAGE       | `heldAt`, `holdReason`               |
| `PACKAGE_SHIPPED`                              | PACKAGE       | `shipAcceptedAt`, `trackingNumber`   |
| `PACKAGE_HANDED_OVER`                          | PACKAGE       | `handedOverAt`                       |
| `PACKAGE_NOTE_ADDED` · `_UPDATED` · `_DELETED` | PACKAGE       | `noteId`, `type`                     |
| `PACKAGE_ALLOCATION_PINNED`                    | PACKAGE       | `warehouseId`, `reason`              |
| `PACKAGE_ALLOCATION_UNPINNED`                  | PACKAGE       | `warehouseId`                        |

`ORDERS_STATUS_CHANGED` số nhiều là **cố ý** — đó là nguyên văn chuỗi đã nằm trong `event_logs` từ
đầu; đổi tên là làm biến mất mọi dòng đã ghi.

**`before`/`after` không phải snapshot bản ghi.** Chúng chỉ chứa các khoá ở bảng trên, chiếu qua một
danh sách trắng theo `action`; khoá không có trong bảng thì không ra khỏi server. Vì thế nội dung
ghi chú (`content`), thông tin người mua và giá vốn **không bao giờ** đi qua đường này — và vì thế
`orders.view` là đủ. Mặt compliance `GET /api/v1/event-logs` trả snapshot nguyên vẹn nên đòi đồng
thời `audit_log.view` và `data.buyer_pii.view`; hai mặt đọc khác nhau, giữ nguyên cả hai.

`null` ở `before`/`after` nghĩa là hành động **không có vế đó** (thêm mới thì không có `before`, xoá
thì không có `after`), không phải "có nhưng bị giấu". Nội dung ghi chú đọc ở
`GET /api/v1/packages/{packageId}/notes`, nơi có quyền riêng.

**`actor.type` là trường trả lời AC-3.** `user` là người dùng nội bộ, `system` là tác vụ nền của
ViShop, `platform` là sàn đẩy về. FE phân biệt tự động với thủ công **bằng trường này**, không suy
từ `action`. `actor.id` và `actor.name` là `null` khi `type` khác `user`; `name` cũng `null` khi tài
khoản đã bị xoá, còn `id` vẫn giữ để truy vết.

`flagName` là khoá **suy thêm** ở BE, đứng cạnh `flagId`, `null` khi cờ đã bị xoá. Lý do giống
`actorName` ở §9.1: màn chi tiết đơn không tải danh sách cờ nên FE không có cách nào đổi UUID thành
tên trong một lần gọi.

`target.reference` là con số người vận hành nhìn thấy trên sàn và trên tem: `externalOrderId` với
`ORDER`, `externalPackageId` với `PACKAGE`. Với `ORDER` luôn có giá trị; với `PACKAGE` có thể `null`
— sàn chưa sắp kiện thì chưa cấp mã.

Sắp xếp mới nhất trước theo `(occurredAt, id)`, cùng khoá với cursor. Không có bộ lọc theo `action`,
theo người hay theo khoảng ngày: một đơn có vài chục dòng, lọc ở đây là tính năng không ai cần — bộ
lọc thuộc màn nhật ký toàn hệ thống (§9.1).

| Tình huống                                                         | Kind → status     | `code`            |
| ------------------------------------------------------------------ | ----------------- | ----------------- |
| Đơn không tồn tại **hoặc** thuộc gian hàng ngoài phạm vi người gọi | `not_found` → 404 | `order_not_found` |
| `cursor` không giải được                                           | `malformed` → 400 | `invalid_cursor`  |

> **Giới hạn cần nói với người dùng:** nhật ký chỉ có thao tác xảy ra **từ khi tính năng được triển
> khai**. Thao tác cũ hơn không backfill được — bảng bị trigger cấm ghi lùi, và dữ liệu nguồn (ai
> bấm, lúc nào) không tồn tại ở đâu khác.

Nguồn ghi chưa có: `PACKAGE_CREATED` (tạo kiện — VIS-180). Nó **không** được khai trước trong enum:
một giá trị không ai ghi là nhánh chết ở FE. Phần sửa phân bổ tồn (VIS-211) đã ghi, dưới hai giá trị
`PACKAGE_ALLOCATION_PINNED` và `PACKAGE_ALLOCATION_UNPINNED` — xem FR-06-22 ở §4.1.

---

## 4. FulfillmentProvider — M06 (xử lý package) + M07 (in tem)

### 4.1 Package lifecycle

| Method | Path                                                       | FR                       | Ghi chú                                               |
| ------ | ---------------------------------------------------------- | ------------------------ | ----------------------------------------------------- |
| POST   | `/api/v1/orders/{orderId}/packages`                        | FR-06-15a (P0)           | Tạo package (một hoặc tách nhiều)                     |
| POST   | `/api/v1/packages/shipments`                               | FR-06-15b (P0)           | **Batch RTS** — `202`, trả `batchId` ngay             |
| GET    | `/api/v1/ship-batches/{batchId}`                           | FR-06-15c (P0)           | Poll tiến độ lô RTS, kết quả từng kiện                |
| POST   | `/api/v1/ship-batches/{batchId}/retry-failed`              | FR-06-15c (P0)           | Xếp lại nhóm `FAILED` + `retryable`                   |
| POST   | `/api/v1/ship-batches/{batchId}/items/{packageId}/release` | FR-06-15c (P0)           | Nhả khoá kiện đang giữ chỗ                            |
| GET    | `/api/v1/packages/{packageId}/tracking-number`             | FR-06-16 (P0)            |                                                       |
| POST   | `/api/v1/packages/batch-handover`                          | FR-06-17 (P0)            | Đánh dấu đã bàn giao hàng loạt                        |
| POST   | `/api/v1/packages/{packageId}/hold` / `/unhold`            | FR-06-18 (P0)            | Tồn vẫn `locked` khi tạm giữ (BR-06-04)               |
| POST   | `/api/v1/orders/flags/assign`                              | FR-06-19 (P0)            | Gắn/gỡ cờ hàng loạt — **cấp đơn**, xem §3.4           |
| GET    | `/api/v1/packages/insufficient-stock`                      | FR-06-20 (P0)            |                                                       |
| GET    | `/api/v1/packages/anomalies`                               | FR-06-21 (P0)            | Lỗi sync, lệch dữ liệu, thiếu thông tin               |
| POST   | `/api/v1/packages/{packageId}/allocation/pin`              | FR-06-22 (P0)            | Ghim kiện vào kho người vận hành chọn                 |
| DELETE | `/api/v1/packages/{packageId}/allocation/pin`              | FR-06-22 (P0)            | Trả kiện về phân bổ tự động                           |
| Method | Path                                                       | FR                       | Ghi chú                                               |
| ------ | --------------------------------------------------         | ------------------------ | ----------------------------------------------------- |
| POST   | `/api/v1/orders/{orderId}/packages`                        | FR-06-15a (P0)           | Tạo package (một hoặc tách nhiều)                     |
| POST   | `/api/v1/packages/shipments`                               | FR-06-15b (P0)           | **Batch RTS** — `Idempotency-Key` khuyến nghị         |
| GET    | `/api/v1/ship-batches/{batchId}`                           | FR-06-15c (P0)           | Poll tiến độ hàng đợi RTS, retry độc lập từng package |
| GET    | `/api/v1/packages/{packageId}/tracking-number`             | FR-06-16 (P0)            |                                                       |
| POST   | `/api/v1/packages/handovers`                               | FR-06-17 / FR-07-12 (P0) | Đánh dấu đã bàn giao hàng loạt + sinh biên bản PDF    |
| GET    | `/api/v1/packages/handover-manifests/{manifestId}`         | FR-07-12 (P0)            | Xem chi tiết biên bản bàn giao & link tải PDF         |
| GET    | `/api/v1/packages/handover-manifests`                      | FR-07-12 (P0)            | Tra cứu danh sách biên bản bàn giao theo shop         |
| POST   | `/api/v1/packages/{packageId}/hold` / `/unhold`            | FR-06-18 (P0)            | Tồn vẫn `locked` khi tạm giữ (BR-06-04)               |
| POST   | `/api/v1/orders/flags/assign`                              | FR-06-19 (P0)            | Gắn/gỡ cờ hàng loạt — **cấp đơn**, xem §3.4           |
| GET    | `/api/v1/packages/insufficient-stock`                      | FR-06-20 (P0)            |                                                       |
| GET    | `/api/v1/packages/anomalies`                               | FR-06-21 (P0)            | Lỗi sync, lệch dữ liệu, thiếu thông tin               |
| POST   | `/api/v1/packages/{packageId}/allocation/pin`              | FR-06-22 (P0)            | Ghim kiện vào kho người vận hành chọn                 |
| DELETE | `/api/v1/packages/{packageId}/allocation/pin`              | FR-06-22 (P0)            | Trả kiện về phân bổ tự động                           |

**Request/response rút gọn:**

```json
// POST /api/v1/packages/shipments → 202
// Header: Idempotency-Key: <uuid>   (khuyến nghị, KHÔNG bắt buộc)
{
  "packageIds": ["01973...", "01973..."],
  // Chỉ cho kiện shippingType = SELLER. Kiện TikTok Shipping có mục ở đây bị loại
  // với self_shipment_not_applicable — sàn tự cấp mã vận đơn cho luồng đó.
  "selfShipments": [
    { "packageId": "01973...", "trackingNumber": "SF123", "shippingProviderId": "66175..." }
  ]
}
// handoverMethod KHÔNG bắt buộc — tham số này bị TikTok loại bỏ cho seller SEA,
// BE không được validate required trên field này (FR-06-15b, changelog TikTok đã xác nhận)

// → 202 (lô đã tạo; job gọi sàn cho từng kiện đủ điều kiện đang được xếp hàng)
{
  "batchId": "01973...",
  "requestedCount": 300, "eligibleCount": 287, "excludedCount": 13,
  "excludedReasons": { "package_not_scanned": 9, "order_remorse_window_open": 4 },
  "results": [
    { "packageId": "01973...", "status": "queued",  "reason": null, "warnings": [] },
    { "packageId": "01973...", "status": "skipped", "reason": "package_not_scanned", "warnings": [] }
  ]
}

// GET /api/v1/ship-batches/{batchId} → 200
{
  "id": "01973...",
  "status": "PARTIAL_SUCCESS",           // QUEUED | RUNNING | PARTIAL_SUCCESS | DONE | FAILED
  "progress": { "total": 287, "succeeded": 274, "failed": 13 },
  "results": [
    { "packageId": "01973...", "status": "SUCCEEDED", "trackingNumber": null,
      "attempts": 1, "retryable": false, "platformErrorCode": null, "errorMessage": null },
    { "packageId": "01973...", "status": "FAILED", "trackingNumber": null,
      "attempts": 1, "retryable": true, "platformErrorCode": "11xxxxx",
      "errorMessage": "rate limited" }
  ]
}

// POST /api/v1/packages/handovers → 200
// Body: { "shopId": "01973...", "packageIds": ["01973...", "01973..."] }
{
  "succeeded": ["01973..."],
  "failed": [{ "packageId": "01973...", "reason": "package_on_hold" }],
  "manifestId": "01973b8e-4c21-7a10-9f3d-90a1b2c3d4e5",
  "manifestDownloadUrl": "https://storage.example.com/manifests/manifest-1.pdf?..."
}

// GET /api/v1/packages/handover-manifests/{manifestId} → 200
{
  "id": "01973b8e-4c21-7a10-9f3d-90a1b2c3d4e5",
  "shopId": "01973b8e-4c21-7a10-9f3d-111111111111",
  "createdByUserId": "01973b8e-4c21-7a10-9f3d-222222222222",
  "packageCount": 15,
  "itemsSnapshot": [
    {
      "packageId": "01973b8e-4c21-7a10-9f3d-333333333333",
      "externalPackageId": "PKG123456789",
      "trackingNumber": "VN1234567890"
    }
  ],
  "downloadUrl": "https://storage.example.com/manifests/manifest-1.pdf?...",
  "createdAt": "2026-08-29T10:00:00.000Z"
}

// POST /api/v1/packages/{packageId}/allocation/pin → 200  (quyền orders.allocation.edit)
// Body: { "shopId": "01973...", "warehouseId": "01973...", "reason": "Kho HN đang kiểm kê" }
// → PackageResponseDto, với hai trường mới:
{
  "allocationPinnedWarehouseId": "01973...",   // kho do người vận hành chọn; null = máy tự chọn
  "allocationPinReason": "Kho HN đang kiểm kê",
  "lines": [{ "warehouseId": "01973...", "lockedQuantity": 2 }]
}
// DELETE /api/v1/packages/{packageId}/allocation/pin?shopId=01973... → 200
// Trả kiện về phân bổ tự động; máy có thể chọn kho khác ngay trong cùng giao dịch.
```

**Ghim là ràng buộc lên phân bổ, không phải một lần ghi tồn (FR-06-22).** Bộ phân bổ tự động **vẫn
chạy** cho kiện đã ghim, chỉ bị thu hẹp về đúng kho đã ghim; số lượng giữ chỗ vẫn tính theo nội dung
đơn hiện tại, nên khách đổi 2 thành 3 thì kiện giữ 3 tại kho đã ghim. Khoá cả số lượng sẽ để lại
khoá 2 / xuất 3 — một khoản nợ tồn không ai giải thích được.

`lines[].warehouseId` là kho **đang** giữ hàng; `allocationPinnedWarehouseId` là ràng buộc người
vận hành đặt lên. Hai bên bằng nhau, trừ khi kho đã ghim bị tắt hoặc bị gỡ tuyến — lúc đó kiện giữ
nguyên kho ghim và giữ nguyên phần đã khoá ở đó, nhưng không khoá thêm (hàng vẫn nằm trên kệ, tạo
thiếu ảo là sai). Kiện chưa khoá được gì hiện ở `GET /api/v1/orders/missing-stock`.

| `code`                            | `kind` → status    | Tình huống                                                        |
| --------------------------------- | ------------------ | ----------------------------------------------------------------- |
| `warehouse_not_eligible_for_shop` | `validation` → 422 | Kho không hoạt động, không thuộc tổ chức, hoặc không có tuyến     |
| `package_in_active_pick_wave`     | `conflict` → 409   | Sóng nhặt đang `PICKING`; sóng `DRAFT` thì kiện được gỡ khỏi sóng |
| `package_already_shipped`         | `conflict` → 409   | `stockDeductedAt` đã có — tồn đã trừ thật, không còn gì để chuyển |
| `package_version_conflict`        | `conflict` → 409   | Hai người bấm cùng lúc; đọc lại kiện rồi thử lại                  |

**Ba điểm bề mặt này khác bản đề xuất đầu (đã cài, 30/08):**

- `POST` trả **202**, không phải 200: lô chạy nền — lô 1 kiện gọi Ship Package, lô từ 2 kiện gom theo
  gian hàng thành từng lời gọi Batch Ship (tối đa 50 kiện/lời gọi). Kết quả thật đọc ở `GET`.
- `results[]` phẳng (`platformErrorCode`, `retryable`, `errorMessage`, `attempts`), không bọc trong object `error`.
  `errorMessage` là văn bản đã duyệt, không phải nguyên văn lỗi ném ra — nguyên nhân đầy đủ chỉ nằm ở log.
- `status` từng kiện dùng chính trạng thái item (`PENDING`/`SENDING`/`VERIFYING`/`SUCCEEDED`/`FAILED`/`NEEDS_ATTENTION`),
  vì hai trạng thái giữa không gộp được vào "success/failed": `VERIFYING` — lệnh đã gửi nhưng chưa rõ
  kết quả, hệ thống đang tự đọc lại sàn (theo `rts_time` của từng kiện) để kết luận; `NEEDS_ATTENTION` —
  đọc lại nhiều lần vẫn không kết luận được, cần người xác minh trên Seller Center. Kiện `VERIFYING`
  không tính vào `progress.succeeded`/`progress.failed` và vẫn giữ khoá (lô giữ `RUNNING`).
- `trackingNumber` từng kiện là mã vận đơn hiện có của kiện, `null` cho tới khi sàn cấp.

Lô RTS 300 kiện có 13 kiện hỏng **không** trả problem+json: request đã được xử lý đúng, thất bại nằm ở từng phần tử (§0.4). `POST` trả `202`, `GET` trả `200`. Chỉ khi **không kiện nào** vào được lô mới trả problem+json — `422 ship_batch_empty`, và mỗi kiện bị loại là một dòng trong `errors[]` với `path` = packageId, `code` = lý do bị loại.

**Lý do bị loại từng kiện** (`results[].reason` và `excludedReasons`) **không phải mã lỗi** — chúng không bao giờ tự làm hỏng một request, nên không nằm trong `ERROR_CODES` mà là `ShipGateBlockReason` trong module orders:

`not_found`, `ship_already_requested`, `self_shipment_required`, `self_shipment_not_applicable`,
`package_cancelled`, `package_already_shipped`, `package_on_hold`, `package_not_scanned`,
`package_stock_not_allocated`,
`order_already_cancelled`, `order_cancel_requested`, `order_payment_time_unknown`,
`order_remorse_window_open`, `order_on_hold`, `order_fulfilled_by_platform`, `enqueue_failed`.

`package_stock_not_allocated` chỉ mang một chuỗi lý do, không kèm số. Số cụ thể đọc ở
`GET /api/v1/packages/{packageId}`: mỗi `lines[]` trả `warehouseId` và `lockedQuantity` — kho đã
phân bổ và số đơn vị đang giữ ở kho đó, theo đơn vị của chính dòng. `lockedQuantity < quantity` là
đúng phần kho không giữ nổi, và cũng là số nợ hàng dưới `oversellPolicy = BACKORDER`.

**Mã lỗi nghiệp vụ đã thêm vào `ERROR_CODES`** — chỉ những lỗi làm hỏng **cả request**:

| `code`                      | `kind` → status    | Tình huống                                                            |
| --------------------------- | ------------------ | --------------------------------------------------------------------- |
| `ship_batch_empty`          | `validation` → 422 | Không kiện nào đủ điều kiện; từng kiện nằm trong `errors[]`           |
| `ship_batch_not_found`      | `not_found` → 404  | Không có lô đó trong tenant, hoặc lô chạm gian hàng ngoài scope       |
| `ship_batch_item_not_found` | `not_found` → 404  | Lô có tồn tại nhưng không có kiện đó                                  |
| `ship_item_not_releasable`  | `conflict` → 409   | Kiện không nhả được (đã `SUCCEEDED`, hoặc đang `SENDING`/`VERIFYING`) |

Ba mã `seller_shipping_no_label` / `package_on_hold` / `platform_rts_rejected` mà bản đề xuất đầu liệt kê **không được thêm**: cả ba mô tả tình huống của **một kiện trong lô**, mà một kiện bị loại thì lô vẫn chạy — chúng là `reason` ở trên, không phải Problem Details. Riêng lý do sàn từ chối vẫn giữ nguyên mã gốc, trả ở `results[].platformErrorCode` kèm `retryable`.

### 4.2 In tem & chứng từ

**Đã dựng (đồng bộ, lô nhỏ):**

| Method | Path                      | Quyền            | Ghi chú                                                                |
| ------ | ------------------------- | ---------------- | ---------------------------------------------------------------------- |
| POST   | `/api/v1/packages/labels` | `print.label`    | Tối đa **20** kiện. Gọi sàn lấy tem, gộp PDF, trả `downloadUrl` có hạn |
| POST   | `/api/v1/packages/prints` | `print.document` | Ghi nhận đã in tem ra giấy (`printedAt`)                               |

```json
// POST /api/v1/packages/labels → 200
{ "shopId": "01973...", "packageIds": ["01973...", "01973..."], "pageSize": "A6" }
{ "downloadUrl": "https://...", "pageCount": 4, "succeeded": ["01973..."], "failed": [] }
```

Thứ tự `packageIds` **là** thứ tự trang trong file. `pageSize` nhận `A4 | A5 | A6 | K80 | K57`; `A6` (100×150mm) là khổ tem tiêu chuẩn, `K80`/`K57` là giấy in nhiệt dạng cuộn gọi theo bề ngang. Tem giữ nguyên bản gốc của sàn, chỉ co giãn và xoay cho vừa khổ.

`downloadUrl` không được lưu lên `Package`: liên kết sàn cấp có hạn, nên **in lại là gọi lại endpoint**, không phải mở file cũ.

Trần 20 kiện là do chạy đồng bộ dưới `HTTP_REQUEST_TIMEOUT_MS` (30 giây) — mỗi kiện tốn một lượt gọi sàn cộng một lượt tải tem về để gộp. Lô tới 500 kiện theo BR-07-02 dùng lệnh in chạy nền dưới đây.

**Đã dựng (chạy nền, lô lớn, đa gian hàng) — FR-07-01, FR-07-01d.01e.02.06 (VIS-295, 27/08/2026):**

| Method | Path                                           | Quyền         | Ghi chú                                                              |
| ------ | ---------------------------------------------- | ------------- | -------------------------------------------------------------------- |
| POST   | `/api/v1/print-jobs/labels`                    | `print.label` | **202**. Tự tách theo gian hàng, tối đa 500 kiện/lệnh con (BR-07-02) |
| GET    | `/api/v1/print-jobs/groups/{requestGroupId}`   | `print.label` | Tiến độ tổng hợp + từng lệnh con của một yêu cầu                     |
| GET    | `/api/v1/print-jobs/{jobId}`                   | `print.label` | Tiến độ + `downloadUrl` khi đã có file                               |
| POST   | `/api/v1/print-jobs/{jobId}/partial-downloads` | `print.label` | File PDF bộ phận, gộp lại mỗi lần gọi — không cần đợi lệnh xong      |

```json
// POST /api/v1/print-jobs/labels → 202
// packageIds có thể trộn nhiều gian hàng — KHÔNG còn field "shopId" ở body này.
{ "packageIds": ["01973...", "01973...", "01973..."], "pageSize": "A6" }
{
  "requestGroupId": "01973...",
  "jobs": [
    { "jobId": "01973...", "shopId": "shop-a", "packageCount": 120 },
    { "jobId": "01973...", "shopId": "shop-b", "packageCount": 40 }
  ],
  "excluded": [
    { "packageId": "01973...", "reason": "not_found" },
    { "packageId": "01973...", "reason": "package_not_ready", "stage": "NEED_LABEL" }
  ]
}

// GET /api/v1/print-jobs/groups/{requestGroupId} → 200
{
  "requestGroupId": "01973...",
  "aggregate": { "total": 160, "fetched": 150, "failed": 2 },
  "jobs": [ /* mỗi phần tử đúng shape của GET /print-jobs/{jobId} bên dưới */ ]
}

// GET /api/v1/print-jobs/{jobId} → 200
{ "id": "01973...", "status": "queued | running | done | partial_success | failed",
  "progress": { "total": 300, "fetched": 288, "failed": 12 },
  "failedPackages": [{ "packageId": "01973...", "reason": "tiktok_api_error" }],
  "failureReason": null, "downloadUrl": "https://...",
  "createdAt": "...", "completedAt": "..." }

// POST /api/v1/print-jobs/{jobId}/partial-downloads → 200
{ "downloadUrl": "https://...", "pageCount": 8, "fetchedCount": 8, "totalCount": 120 }
// 409 khi chưa kiện nào trong lệnh có tem: code "print_job_nothing_to_download"
```

**Server tự tách lệnh theo gian hàng, không suy `shopId` từ kiện đầu tiên và không nới cột này
thành nullable** — mỗi `PrintJob`/`shopCipher` vẫn đúng 1 gian hàng, tránh lấy tem nhầm credential.
Một yêu cầu chỉ có 1 gian hàng vẫn nhận `requestGroupId` riêng (nhóm của chính nó), để hai luồng
tra cứu tiến độ luôn cùng một shape. Một shop vượt 500 kiện tự chia thành nhiều `PrintJob` liên
tiếp theo đúng thứ tự đã gửi (BR-07-02), không phải bị từ chối.

`partial_success` **có** `downloadUrl` như trước; `partial-downloads` bổ sung khả năng lấy file
sớm hơn, **trong lúc lệnh còn `queued`/`running`** — gọi lại nhiều lần, mỗi lần trả file gộp lại từ
những kiện đã xong tính đến thời điểm gọi, đầy đủ hơn lần trước. Package vừa vào file (kể cả qua
API bộ phận) được đánh dấu `is_printed` ngay — xem cuối mục này.

Thứ tự `packageIds` được chụp vào từng lệnh con theo đúng thứ tự tương đối trong yêu cầu gốc, kể cả
khi bị tách theo shop hoặc chia nhỏ do vượt 500 kiện. Không có tham số `sortBy`: sắp xếp là việc
của màn hình trước khi gửi.

**Chốt chặn `stage = NEED_PRINT` (FR-07-17).** Kiện chưa ở stage `NEED_PRINT` (còn `NEED_LABEL` hay
sớm hơn) bị loại vào `excluded[]` với `reason: "package_not_ready"` kèm `stage` hiện tại, không vào
`jobs[]`/`packageCount`. Chỉ áp dụng cho endpoint chạy nền này — `POST /api/v1/packages/labels` và
`POST /api/v1/packages/prints` (đồng bộ, ≤20 kiện) vẫn tự gọi lấy tem tại chỗ nếu kiện chưa có, phục
vụ thao tác in nhanh thủ công.

`progress` cập nhật mỗi 10 kiện trong lúc chạy. Không có `cacheHitRatio` — bộ nhớ đệm tem
(`LabelCache`, FR-07-07/FR-07-01c) giờ được đọc/ghi cho mọi lượt lấy tem (kể cả từ `POST
/api/v1/packages/labels` đồng bộ), nhưng tỉ lệ hit không được trả ra API vì nó không phải điều
seller cần biết để hành động.

Lệnh đã kết thúc **không chạy lại**: mỗi lượt chạy gọi sàn lấy tem cho từng kiện, mà tem đã lấy rồi
không giao thì phải huỷ với sàn.

**Seller Shipping: loại khỏi luồng, không từ chối cả lô (BR-07-07).** Áp dụng thống nhất cho cả
`POST /api/v1/packages/labels` (đồng bộ) lẫn `POST /api/v1/print-jobs/labels` (nền) — hai endpoint
dùng chung một lớp xử lý. Package Seller Shipping không bao giờ được gọi tới TikTok; nó xuất hiện
trong `failed`/`failedPackages` với `reason: "seller_shipping"`, các package còn lại trong cùng lô
vẫn được xử lý bình thường. Chỉ khi **toàn bộ** package của một lệnh đều bị loại hoặc lỗi mới trả
`409 print_label_merge_all_failed` cho lệnh đó (không phải `422` từ chối cả lô như bản ghi cũ ở đây
trước 27/08/2026 — đã đổi cùng đợt này).

**`is_printed` tự động (FR-07-07, VIS-212)** — chỉ áp dụng cho pipeline `PrintJob` (job nền +
partial-downloads): ngay khi một package thật sự có tem trong file (lệnh hoàn tất, hoặc một lần
tải bộ phận), `printedAt` được đặt và một dòng nhật ký in (`print_logs`, xem `GET
/print-jobs/history` phía trên) được ghi lại — **không cần gọi thêm `POST /api/v1/packages/prints`**
cho những package đó. Lô nhỏ đồng bộ (`POST /api/v1/packages/labels`) **giữ nguyên** quy ước cũ:
lấy tem và xác nhận đã in vẫn là hai lệnh gọi tách biệt (`POST /packages/prints`), không tự động
hoá.

`GET /api/v1/packages` hỗ trợ lọc `?isPrinted=true|false` (cùng khuôn với `?isHeld=`) để tách tab
Chưa in / Đã in — xem Swagger cho chi tiết tham số.

Ô tìm `?search=` (≤200 ký tự) khớp **một phần**, không phân biệt hoa thường, mã kiện sàn, mã vận đơn,
hoặc mã đơn sàn của bất kỳ đơn nào kiện có hàng. Gộp AND với mọi bộ lọc khác, kể cả `stage`,
`orderId` và `sort`.

**`?shopId=` trên `GET /api/v1/packages` là tuỳ chọn** (đổi 28/08/2026; trước đây bắt buộc).
Bỏ trống thì trả kiện của **mọi gian hàng trong phạm vi người gọi** — màn kho đa gian hàng không
phải gọi N lượt rồi tự gộp. Gửi `?shopId=` chỉ để thu hẹp về một gian hàng.

Kèm theo đó, gian hàng ngoài phạm vi trên endpoint **danh sách** này trả **200 kèm mảng rỗng**,
không còn trả 404: ở đây `shopId` là một **bộ lọc** trên tập đã bị giới hạn sẵn theo phạm vi, và
một bộ lọc không khớp gì thì trả về không gì. Quy ước này giống hệt `GET /api/v1/orders`.
Các route **một kiện** (`/packages/{packageId}`, `/tracking`, `/notes`, `/hold`…) **không đổi**:
ở đó `shopId` định danh đúng một bản ghi, nên ngoài phạm vi vẫn là **404**.

**Chứng từ kho nội bộ & Cấu hình template in (FR-07-03, FR-07-04, FR-07-05, FR-07-10):**

Các chứng từ kho nội bộ (phiếu nhặt hàng, phiếu đóng gói, phiếu tổng hợp lô in) được tạo trực tiếp dưới dạng stream PDF chuẩn (`application/pdf`) cho trình duyệt in qua `window.print()`, tách biệt hoàn toàn khỏi pipeline tem vận chuyển TikTok (BR-07-01: không ghi `PrintLog`, không queue BullMQ, không ảnh hưởng trạng thái `is_printed` của kiện).

| Method | Path                                 | Quyền            | FR                 | Ghi chú                                                                                        |
| ------ | ------------------------------------ | ---------------- | ------------------ | ---------------------------------------------------------------------------------------------- |
| GET    | `/api/v1/print-template-config`      | `print.document` | FR-07-10 (AC-1, 3) | Lấy cấu hình template in của tenant (fallback mặc định A4 + full trường nếu chưa lưu)          |
| PUT    | `/api/v1/print-template-config`      | `print.document` | FR-07-10 (AC-1, 2) | Cập nhật danh sách trường hiển thị (`enabledFieldKeys`) và khổ giấy (`A4`, `A5`, `K80`, `K57`) |
| POST   | `/api/v1/orders/print/pick-list`     | `print.document` | FR-07-03, FR-07-10 | Xuất PDF phiếu nhặt hàng (hỗ trợ `groupBy: 'SKU'` hoặc `'PACKAGE'`, tối đa 500 kiện)           |
| POST   | `/api/v1/orders/print/packing-slips` | `print.document` | FR-07-04, FR-07-10 | Xuất PDF phiếu đóng gói theo từng kiện, kèm ghi chú nhặt hàng (`PICKING` notes)                |
| POST   | `/api/v1/orders/print/batch-summary` | `print.document` | FR-07-05, FR-07-10 | Xuất PDF phiếu tổng hợp lô in (tổng số kiện, tổng SKU, tổng số lượng và bảng SKU gộp)          |

In tem lô lớn là job nền vì hai lý do độc lập: hàng trăm tem chưa có sẵn vượt xa 30 giây timeout
(§0.4), và người dùng cần đóng tab mà lô in vẫn chạy.

SLA ≤10 giây cho 300 tem của FR-07-01 vẫn phụ thuộc `LabelCache` đã ấm sẵn (pre-fetch ngay sau RTS
thành công, FR-07-14, chưa dựng) — hôm nay lượt fetch đầu tiên của mỗi package vẫn gọi sàn và mất
vài phút cho lô lớn, đó là lý do `progress` và `partial-downloads` phải tồn tại.

### 4.3 Chuỗi bước đóng gói (packing stage) — một máy trạng thái, mọi màn đọc chung — FR-06-28, FR-06-29, FR-06-30

Trước đây mỗi màn tự suy trạng thái từ vài cờ rời (`labelPrinted`, `scanned`, `rtsAt`), nên hai màn cùng lọc một tập kiện lại ra hai con số khác nhau. Nay BE trả **một** field `stage` cho mỗi package; FE không tự tính lại.

`stage` nhận đúng một trong các giá trị dưới đây, xếp theo thứ tự bắt buộc:

| #   | `stage`             | Điều kiện vào                         | Màn xử lý             | Cho phép bước sau?                                |
| --- | ------------------- | ------------------------------------- | --------------------- | ------------------------------------------------- |
| 1   | `NEED_MAPPING`      | Còn dòng hàng chưa ghép SKU kho       | `/sku-mapping`        | Chặn **vào sóng nhặt**; **không** chặn RTS/in tem |
| 2   | `NEED_PICK`         | Đã ghép SKU, chưa thuộc sóng nhặt nào | `/picking`            | Bước hiển thị, không chặn                         |
| 3   | `PICKING`           | Đã vào sóng, sóng chưa nhặt xong      | `/picking`            | Bước hiển thị, không chặn                         |
| 4   | `NEED_SCAN`         | Nhặt xong, chờ soát khớp              | `/scan`               |                                                   |
| 5   | `NEED_SHIP`         | Đã soát, chờ gọi lệnh giao            | `/orders/pending-rts` | Có — chỉ bước này mới gọi Ship Package được       |
| 6   | `NEED_LABEL`        | Sàn đã nhận lệnh giao, chờ lấy tem    | `/print`              |                                                   |
| 7   | `NEED_PRINT`        | Có tem trong máy, chưa in ra giấy     | `/print`              |                                                   |
| 8   | `READY_TO_HANDOVER` | Đã in tem, chờ hãng đến lấy           | `/print`              | Có — bàn giao được                                |
| —   | `SHIPPED`           | Hãng đã lấy hàng                      | —                     | Trạng thái cuối                                   |
| —   | `CANCELLED`         | Kiện bị huỷ                           | —                     | Trạng thái cuối                                   |

Thứ tự này là của `docs/spec/02` §2 — chín bước, cộng `CANCELLED` tách riêng. **Soát hàng đứng trước
lệnh giao**, vì ở Đông Nam Á tem chỉ có sau khi `Ship Package` thành công, mà cam kết với hãng rồi
mới phát hiện thiếu hàng thì Việt Nam không huỷ lại được sau `AWAITING_COLLECTION`.

Ba ràng buộc bắt buộc, không phải gợi ý UI:

1. **Chỉ quét được kiện đã nhặt xong mà chưa gọi lệnh giao.** Bàn quét mở kiện bằng **mã kiện** — quét kiểm đứng trước cả RTS lẫn tem, nên lúc đó chưa có tem để đọc. BE trả `422` với `code = "scan_stage_not_reached"` (kind `validation`) và nói rõ bước hiện tại trong `detail` — ví dụ `"Kiện đang ở bước Chờ nhặt, chưa quét được."`. Bước hiện tại **không** đi kèm dạng field riêng trong thân lỗi (§0.3); FE đã có `stage` trên dòng kiện đó.
2. **Không gọi lệnh giao cho kiện chưa quét.** `POST /api/v1/packages/shipments` loại những kiện chưa soát khớp và trả chúng trong `results[]` với `status: "skipped"` kèm `reason` — giao hàng chưa soát là nguồn gốc khiếu nại thiếu hàng, nên đây là chặn cứng ở BE chứ không chỉ disable nút. Cổng này **không** so sánh bằng `stage`: nó đọc thẳng các tín hiệu (đã ghép SKU, đã soát khớp, trạng thái kiện, trạng thái từng đơn). So bằng `stage` sẽ kéo trạng thái sóng nhặt vào làm điều kiện chặn, mà kho được phép nhặt tay ngoài sóng (`docs/spec/31`) — kiện đó sẽ mãi không RTS được. **Ngoại lệ theo BR-04-01:** kiện còn dòng chưa ghép SKU nằm ngoài phạm vi quét (BR-08-01) nên bỏ qua cổng này và **vẫn RTS được**; response đánh dấu `warnings: ["scan_gate_skipped_unmapped"]` để vận hành biết kiện đó đi mà chưa soát.
3. **Tem và mã vận đơn về cùng một lần gọi, và lần gọi đó nằm SAU `Ship Package`.** [Get Package Shipping Document](https://partner.tiktokshop.com/docv2/page/650aa5fac16ffe02b8f112ca) trả cả `data.doc_url` và `data.tracking_number` trong một response, nhưng ở Đông Nam Á nó chỉ trả về sau khi [Ship Package](https://partner.tiktokshop.com/docv2/page/650aa4f1defece02be6e7cb1) thành công (`docs/spec/02` §2, `docs/spec/31`). Không thiết kế luồng "xin tem trước rồi mới ship", và không thiết kế màn giả định tem với mã vận đơn đến rời nhau.

Đơn nhiều kiện: `stage` của đơn = bước **chậm nhất** trong các kiện chưa huỷ của đơn đó. Đơn chưa tách kiện nào — hoặc mọi kiện đều đã huỷ — coi như còn ở `NEED_MAPPING`.

**Bốn hoàn cảnh của đơn chặn mọi việc kho** (FR-06-29 AC-4): đơn đã huỷ, khách đang xin huỷ
(`is_buyer_request_cancel`), đơn `ON_HOLD`, đơn sàn tự giao (`FULFILLMENT_BY_TIKTOK`). Bốn cái này
chặn **vào sóng nhặt, mở phiên quét, lấy tem, in tem và bàn giao** — kiện gộp nhiều đơn thì chỉ cần
một đơn vướng là cả kiện bị loại. Đường nào trả danh sách từng kiện thì ghi mã lý do
(`order_already_cancelled`, `order_cancel_requested`, `order_on_hold`,
`order_fulfilled_by_platform`); đường nào chặn cả lô thì trả `409 order_not_fulfillable`.

**Cửa sổ đổi ý 1 giờ KHÔNG nằm trong nhóm đó** — nó chỉ chặn lệnh giao. Nhặt và soát là việc đảo
ngược được (khách huỷ thì trả hàng lên kệ, mất công chứ không mất tiền), còn RTS thì cam kết với
hãng rồi và Việt Nam không rút lại được sau `AWAITING_COLLECTION`. Chặn cả cụm vì một rủi ro chỉ
nằm ở bước cuối là bắt kho ngồi chơi một tiếng cho **mỗi** đơn, đúng vào giờ đơn đổ về nhiều nhất.

#### Lọc theo bước

| Endpoint                      | Ngữ nghĩa                                                                   |
| ----------------------------- | --------------------------------------------------------------------------- |
| `GET /api/v1/packages?stage=` | Bước của **kiện**. Đây là hàng đợi hai màn kho dùng (`/print`, `/scan`).    |
| `GET /api/v1/orders?stage=`   | Khớp **đúng** bước của đơn (kiện chậm nhất), không phải "có một kiện ở đó". |

Cấp đơn cố ý khớp đúng chứ không phải `some`: lọc `NEED_PRINT` mà dòng hiện ra ghi `NEED_MAPPING`
là bộ lọc không tin được. Cả hai gộp AND với mọi bộ lọc sẵn có của endpoint đó; giá trị lạ trả
`422`. Bước **không lưu thành cột** (FR-06-28 AC-1), nên bộ lọc dịch lại đúng luật đó sang câu truy
vấn — một test đối chiếu hai bản dịch trên Postgres thật giữ cho chúng không lệch nhau.

### 4.4 Sóng nhặt hàng (pick wave) — M07

| Method | Path                                        | FR            | Ghi chú                                                                                                                                           |
| ------ | ------------------------------------------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| GET    | `/api/v1/pick-waves`                        | FR-07-15 (P0) | Lọc theo kho, trạng thái sóng, người nhặt; `search` khớp một phần mã sóng hoặc mã kiện sàn trong sóng                                             |
| POST   | `/api/v1/pick-waves`                        | FR-07-15 (P0) | Gom package `stage = NEED_PICK` của **một kho** thành một sóng                                                                                    |
| GET    | `/api/v1/pick-waves/{waveId}/items`         | FR-07-16 (P0) | Đường đi nhặt hàng, 1 dòng/kiện, xếp theo `binCode`                                                                                               |
| PATCH  | `/api/v1/pick-waves/{waveId}/items/reorder` | FR-07-16 (P0) | Đổi thứ tự nhặt thủ công, ghi Audit Log                                                                                                           |
| POST   | `/api/v1/pick-waves/{waveId}/assign`        | FR-07-15 (P1) | Giao sóng cho nhân viên kho                                                                                                                       |
| POST   | `/api/v1/pick-waves/{waveId}/complete`      | FR-07-15 (P0) | Đóng sóng → mọi package trong sóng chuyển sang `NEED_SCAN`                                                                                        |
| POST   | `/api/v1/pick-waves/{waveId}/cancel`        | FR-07-15 (P0) | Huỷ sóng đang mở, giải phóng kiện. **Không** xoá `warehouseId`/SKU: từ FR-05-28 hai cột đó do phân bổ tồn sở hữu, hàng vẫn đang được giữ ở kho đó |
| GET    | `/api/v1/pick-waves/waiting`                | FR-07-15 (P0) | Package chờ gom, nhóm theo kho                                                                                                                    |

```json
// POST /api/v1/pick-waves → 201
{
  "id": "01973...",
  "code": "PW-K8J2-A1B2",
  "warehouseId": "01973...",
  "status": "DRAFT",
  "packageCount": 2,
  "shortages": [
    {
      "packageId": "01973...",
      "merchantSkuId": "01973...",
      "warehouseId": "01973...",
      "requested": 3,
      "available": 1,
      "shortfall": 2
    }
  ],
  "createdAt": "2026-08-28T03:00:00.000Z"
}
```

`shortages` liệt kê SKU thiếu tồn tại đúng kho của sóng — mảng rỗng khi đủ tồn. Kiện **đã được
phân bổ tồn** (FR-05-28) đo theo số đã giữ (`package_lines.lockedQuantity`), không đo lại
`inventories.available`: phần đã giữ vốn đã rời khỏi `available`. Kiện chưa phân bổ giữ nguyên luật
cũ — so với tồn khả dụng của đúng kho được yêu cầu.

Chặn hay không chỉ áp cho **kiện đã phân bổ**, và do `oversellPolicy` của tenant quyết định
(FR-05-28 AC-3):

- Kiện **chưa phân bổ** — giữ nguyên BR-06-03/FR-06-20: **không chặn** dù `oversellPolicy` là gì.
  Kiện thiếu tồn vẫn vào sóng, in tem và RTS bình thường, FE chỉ dùng `shortages` để cảnh báo.
- Kiện **đã phân bổ** + `BACKORDER` — cũng không chặn; `lockedQuantity < quantity` là dấu nợ hàng.
- Kiện **đã phân bổ** + `BLOCK` (mặc định) — trả `409 order_not_fulfillable`, mỗi SKU thiếu là một
  dòng `violations[]` với `code = "package_stock_not_allocated"` và `constraint` gồm `packageId`,
  `merchantSkuId`, `warehouseId`, `shortfall`.

```json
// POST /api/v1/pick-waves
{ "warehouseId": "01973...", "packageIds": ["01973...", "01973..."] }

// 422 nếu kiện thuộc kho khác — một sóng không đi hai kho:
{
  "type": "/errors/wave-mixed-warehouse",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Sóng nhặt chỉ gom được kiện trong cùng một kho.",
  "instance": "/api/v1/pick-waves",
  "code": "wave_mixed_warehouse",
  "requestId": "01973...",
  "timestamp": "2026-08-07T04:31:11.469Z"
}

// GET /api/v1/pick-waves/{waveId}/items → 200
{
  "waveId": "01973...",
  "items": [
    { "packageId": "01973...", "externalPackageId": "PKG-001", "trackingNumber": null,
      "binCode": "A-01", "sortOrder": 0, "unassignedBin": false, "state": "PENDING" },
    { "packageId": "01973...", "externalPackageId": "PKG-002", "trackingNumber": null,
      "binCode": "B-02", "sortOrder": 1, "unassignedBin": false, "state": "PENDING" },
    { "packageId": "01973...", "externalPackageId": "PKG-003", "trackingNumber": null,
      "binCode": null, "sortOrder": 2, "unassignedBin": true, "state": "PENDING" }
  ]
}

// PATCH /api/v1/pick-waves/{waveId}/items/reorder
{ "items": [ { "packageId": "01973...", "sortOrder": 0 },
             { "packageId": "01973...", "sortOrder": 1 } ] }
// → 200, trả lại { waveId, items } đầy đủ theo shape của GET ở trên (thứ tự đã cập nhật). Không
// dùng envelope phân trang (ListResponseDto/OffsetListResponseDto) vì đây là toàn bộ item của
// một sóng, không phải tập lọc/phân trang được — nhưng cũng không phải bare array (repo cấm
// response 2xx là array trần ở top-level, xem scripts/check-openapi-cursor-contract.mjs).

// 409 nếu sóng không còn ở DRAFT/PICKING — đã PICKED hoặc CANCELLED thì không đổi thứ tự được nữa:
{
  "type": "/errors/pick-wave-not-reorderable",
  "title": "Conflict",
  "status": 409,
  "detail": "Sóng nhặt không còn ở trạng thái có thể đổi thứ tự.",
  "instance": "/api/v1/pick-waves/{waveId}/items/reorder",
  "code": "pick_wave_not_reorderable",
  "requestId": "01973...",
  "timestamp": "2026-08-28T03:10:00.000Z"
}
```

Danh sách **đã sắp theo `binCode`** (FR-07-16) — thứ tự đi trong kho, không phải thứ tự đơn. Đây là toàn bộ lý do tồn tại của sóng nhặt: gom nhiều đơn thành một vòng đi kho thay vì đi lại nhiều lần cho từng đơn. Kiện chưa xác định được `binCode` (chưa binning, hoặc SKU chưa gán vị trí) luôn xếp cuối danh sách kèm `unassignedBin: true` để FE hiển thị cảnh báo, không chặn thao tác.

`PATCH .../items/reorder` cho nhân viên kho tự sắp lại thủ công khi thứ tự tự động không khớp thực tế — chỉ áp dụng khi sóng đang `DRAFT` hoặc `PICKING`; mọi `packageId` gửi lên phải thuộc đúng sóng này (422 nếu không). Mỗi lần đổi thứ tự publish domain event `orders.pick_wave_reordered`, `AuditLogListener` ghi lại `resource: "pick_wave"` kèm `oldOrder`/`newOrder` vào `audit_logs` — không có endpoint riêng để đọc lịch sử này, tra qua `GET /api/v1/audit-logs`.

Màn in tem vì vậy sắp mặc định **theo thứ tự nhặt trong kho** (`bin_code`) trước khi gửi, để chồng tem xếp cùng chiều với chồng hàng vừa nhặt. Việc sắp xếp nằm ở màn hình: `POST /api/v1/print-jobs/labels` không có tham số `sortBy`, nó nhận `packageIds` đã theo đúng thứ tự người dùng muốn in (§4.3).

Một kiện chỉ thuộc **một sóng đang mở** (`status` là `DRAFT` hoặc `PICKING`). `POST /api/v1/pick-waves` trả `409` với `code = "package_in_another_wave"` (kind `conflict`) — mã sóng đang giữ kiện nằm trong `detail`, không phải field riêng. Ràng buộc này đặt ở tầng database chứ không ở tầng ứng dụng — hai người bấm gom sóng cùng lúc là chuyện xảy ra thật trong ca cao điểm, và kiểm tra ở tầng ứng dụng thì thua race condition.

`POST /api/v1/pick-waves` gửi kèm `Idempotency-Key`: gom sóng là thao tác tạo tài nguyên, bấm hai lần vì mạng chậm không được sinh hai sóng (§0.4).

### 4.5 Theo dõi vận chuyển — M06

| Method | Path                                          | FR                      | Ghi chú                                                                                                                                                                                                                                                                                                                                 |
| ------ | --------------------------------------------- | ----------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GET    | `/api/v1/shipments`                           | FR-06-31 (P0)           | Kiện đã rời kho, kèm hành trình rút gọn. Lọc: `shopId`, `search` (mã vận đơn / mã kiện sàn, chính xác), `packageStatus`, `milestone` (kiện chưa mở theo dõi không khớp giá trị nào), `carrier` (chính xác, lấy từ `/shipping-providers`), `handedOverFrom`/`handedOverTo`. `sort`: `createdAt:desc` (mặc định), `lastEventAt:asc\|desc` |
| GET    | `/api/v1/shipments/{packageId}`               | FR-06-14, FR-06-31 (P0) | Một kiện kèm toàn bộ mốc đã ghi nhận                                                                                                                                                                                                                                                                                                    |
| GET    | `/api/v1/shipping-providers`                  | FR-06-32 (P0)           | Danh mục đơn vị vận chuyển, cache được                                                                                                                                                                                                                                                                                                  |
| GET    | `/api/v1/shipments/issues`                    | FR-06-33 (P0)           | Kiện bất thường: đứng im quá lâu, giao thất bại, đang chuyển hoàn                                                                                                                                                                                                                                                                       |
| GET    | `/api/v1/shipments/issues/{issueId}`          | FR-06-33 (P0)           | Một vụ việc kèm toàn bộ lịch sử xử lý                                                                                                                                                                                                                                                                                                   |
| POST   | `/api/v1/shipments/issues/{issueId}/assign`   | FR-06-34 (P0)           | Gán người phụ trách                                                                                                                                                                                                                                                                                                                     |
| POST   | `/api/v1/shipments/issues/{issueId}/comments` | FR-06-34 (P0)           | Ghi một lượt trao đổi vào lịch sử vụ việc                                                                                                                                                                                                                                                                                               |
| POST   | `/api/v1/shipments/issues/{issueId}/resolve`  | FR-06-34 (P0)           | Đóng vụ việc kèm kết quả xử lý                                                                                                                                                                                                                                                                                                          |

```json
// GET /api/v1/shipments/{packageId} → 200
{
  "packageId": "01973...",
  "shopId": "01973...",
  "orderIds": ["01973..."],
  "externalPackageId": "1163027975392396789",
  "trackingNumber": "string",
  "carrierName": "Giao Hàng Nhanh",
  "shippingType": "TIKTOK",
  "packageStatus": "FULFILLING",
  "heldAt": null,
  "handedOverAt": "2026-08-04T02:10:00Z",
  "createdAt": "2026-08-03T11:00:00Z",
  "journey": {
    "milestone": "IN_TRANSIT",
    "scope": "PACKAGE",
    "lastEventAt": "2026-08-05T14:20:00Z",
    "silentSince": "2026-08-05T14:20:00Z",
    "deliveredAt": null,
    "lastPolledAt": "2026-08-05T14:35:00Z",
    "failedDeliveryAttempts": 0,
    "isTracking": true,
    "stopReason": null
  },
  "events": [
    {
      "milestone": "PICKED_UP",
      "description": "string",
      "actionCode": 30101,
      "location": null,
      "occurredAt": "2026-08-04T09:10:00Z"
    }
  ]
}
```

`GET /api/v1/shipments` trả cùng object này **không có `events`**, bọc trong `ListResponseDto`.

Hành trình của một kiện trả **thẳng object**, không bọc `ListResponseDto`: `events` là thuộc tính của kiện, không phải một danh sách phân trang độc lập (§0.2). Ngược lại `GET /api/v1/shipments` và `GET /api/v1/shipments/issues` là danh sách thật — trả `ListResponseDto` với cursor.

Nguồn: [Get Tracking](https://partner.tiktokshop.com/docv2/page/650aa630c16ffe02b8f11803) (`GET /fulfillment/202309/orders/{order_id}/tracking`) và [Get Shipping Providers](https://partner.tiktokshop.com/docv2/page/650aa48d4a0bb702c06d85cd).

**Thay đổi phá vỡ (FR-06-31).** `GET /api/v1/packages/{packageId}/tracking` **đã bị bỏ**, thay bằng `GET /api/v1/shipments/{packageId}`. Ba khác biệt FE phải chịu:

- Route mới **không nhận `shopId` trên query**; phạm vi gian hàng lấy từ phiên của người gọi.
- Trường `providerId` (thực chất là tên hãng, mô tả cũ ghi nhầm là mã danh mục) đổi thành `carrierName`.
- Kiện chưa có mốc nào trả **200** với `journey: null` và `events: []`, không còn 404 `shipment_tracking_not_found` — hãng chưa quét là một trạng thái của kiện, không phải tài nguyên không tồn tại. 404 `package_not_found` vẫn giữ cho kiện ngoài phạm vi.

Cụm hành trình gom vào object `journey` thay vì nằm phẳng: cả cụm hoặc có, hoặc `null` cùng lúc, nên một lần kiểm `journey === null` thay cho tám lần kiểm từng trường. `POST /api/v1/packages/{packageId}/tracking/syncs` **giữ nguyên** — đó là hành động trên kiện.

Quyền: cả hai route đọc dùng `shipping.view` theo `vishop-permission-catalog.md`.

Năm điểm FE và BE phải thống nhất trước khi code:

1. **Đơn bị tách thì các kiện DÙNG CHUNG một hành trình, và `journey.scope` nói rõ điều đó.** `GET /fulfillment/202309/orders/{order_id}/tracking` nhận mã ĐƠN và trả **một** mảng timeline; request lẫn response đều không có chiều kiện, và sàn không có endpoint nào nhận mã kiện. Nên ViShop ghi kết quả của một lượt gọi cho MỌI dòng hành trình của các kiện thuộc đơn đó. `journey.scope = PACKAGE` khi đơn chỉ có kiện này (kể cả kiện gộp nhiều đơn) — các mốc là của kiện. `journey.scope = ORDER` khi đơn có nhiều kiện — các mốc là của cả đơn, mốc giao trong đó có thể là của kiện khác, nên kiện chỉ thành `DELIVERED` khi sàn báo kiện `COMPLETED` và `journey.deliveredAt` luôn `null`. Với `ORDER`, màn hình ghi "hành trình chung của đơn" và đọc `packageStatus` để biết kiện đã giao; giờ giao của đơn nằm ở `orders.deliveredAt`.
2. **Sàn KHÔNG có webhook cho hành trình vận chuyển.** `event_type` của Get Shop Webhooks không có loại nào, và `PACKAGE_UPDATE` chỉ bắn khi kiện bị gộp, tách hoặc đổi địa chỉ. Mốc về qua **đồng bộ định kỳ** — nhịp và ngưỡng bỏ cuộc khai ở `docs/environment.md` mục "Shipment tracking polling".
3. **`description` giữ nguyên văn từ sàn, không dịch lại.** Mốc chuẩn hoá nằm ở `milestone`, suy từ `actionCode` (mã bước `action_code` của sàn) theo [bảng mã chính thức](https://partner.tiktokshop.com/docv2/page/37tihrdq) — không suy từ câu mô tả, vì chính tài liệu của sàn ghép câu "đã giao" với mã lấy hàng. Mã không đổi chặng (huỷ, thất lạc, đổi lịch giao…) hoặc chưa có trong bảng thì giữ chặng trước đó. Câu mô tả là bằng chứng thô khi khách khiếu nại. `actionCode` là `null` với các mốc ghi trước FR-06-14.
4. **`lastPolledAt` là thời điểm ViShop ĐỌC, `lastEventAt` là thời điểm hãng QUÉT.** Hiển thị "cập nhật lúc HH:mm" phải dùng `lastPolledAt`; đọc nhầm sang `lastEventAt` sẽ báo là hệ thống đứng im trong khi nó vẫn đang hỏi.
5. **Dữ liệu không realtime.** Nhịp cập nhật do đơn vị vận chuyển quyết định. Polling dày không làm dữ liệu mới hơn, chỉ đốt hạn mức — nhịp gọi dày lúc `OUT_FOR_DELIVERY`, giãn dần khi kiện đứng yên, và **dừng hẳn** khi `milestone` là `DELIVERED`/`RETURNED` (kể cả khi sàn báo kiện `COMPLETED` mà chưa trả mốc giao), khi kiện bị huỷ, hoặc khi kiện im lặng quá ngưỡng bỏ cuộc. Đơn chuyển `DELIVERED` thì ViShop hỏi sàn thêm **một** lượt để ghi mốc giao rồi mới dừng.

#### 4.5.1 Vụ việc kiện bất thường — FR-06-33, FR-06-34

Vụ việc do **job nền** sinh ra (30 phút một lần, không chạy khi người dùng mở màn hình), không phải do giao diện tạo. Job đọc dữ liệu nội bộ của ViShop, không gọi sàn.

```jsonc
// GET /api/v1/shipments/issues?status=open&limit=20 → 200 (ListResponseDto)
{
  "id": "01973b8e-4c21-7a10-9f3d-aabbccddeeff",
  "packageId": "01973b8e-4c21-7a10-9f3d-778899aabbcc",
  // false = sàn đã gộp/tách kiện nên bản ghi kiện không còn. Vụ việc vẫn sống vì hàng vẫn đang đi
  // dưới mã kiện khác; FE KHÔNG điều hướng sang màn chi tiết kiện khi cờ này false.
  "packageExists": true,
  "shopId": "01973b8e-4c21-7a10-9f3d-444555666777",
  "externalPackageId": "1163027975392396789",
  "trackingNumber": "JT0123456789",
  "carrierName": "Giao Hàng Nhanh",
  "reason": "stalled",
  "severity": "warning",
  "stalledThresholdHours": 24,
  "detectedAt": "2026-09-01T02:10:00Z",
  "assignee": { "userId": "01973...", "name": "Trần Thị Vận Hành" },
  "assignedAt": "2026-09-01T03:00:00Z",
  "resolution": null,
  "closedAt": null,
  // null KÈM closedAt có giá trị = HỆ THỐNG tự đóng. Đây là cách FE phân biệt hai đường đóng.
  "closedBy": null,
  // Đúng hình dạng `journey` của §4.5, để màn danh sách không phải gọi thêm /shipments/{packageId}.
  "journey": { "milestone": "IN_TRANSIT", "...": "..." },
}
```

**Bộ lọc của `GET /api/v1/shipments/issues`** (gộp bằng AND): `shopId`, `status` (mặc định `open`),
`assigneeUserId`, `reason`, `search` (≤128 ký tự, khớp **chính xác** mã vận đơn hoặc mã kiện sàn) và
`carrierName` (≤120 ký tự, khớp **chính xác** `carrierName` trên vụ việc).

`GET /api/v1/shipments/issues/{issueId}` trả cùng object kèm `events[]`, **cũ nhất trước** — đây là dòng thời gian của một vụ việc, đọc xuôi mới hiểu diễn biến (khác `events` của hành trình vốn xếp mới nhất trước).

```jsonc
"events": [
  { "type": "detected",  "reason": "stalled",   "note": null, "assignee": null, "actor": null, "createdAt": "…" },
  { "type": "assigned",  "reason": null, "note": "Nhờ Vân gọi hãng", "assignee": { "userId": "…", "name": "…" }, "actor": { "…": "…" }, "createdAt": "…" },
  { "type": "commented", "reason": null, "note": "Đã gọi hãng…", "assignee": null, "actor": { "…": "…" }, "createdAt": "…" },
  { "type": "escalated", "reason": "returning", "note": null, "assignee": null, "actor": null, "createdAt": "…" },
  { "type": "resolved",  "reason": null, "note": "[Hệ thống] Kiện đã giao thành công lúc 03/09/2026 14:20 — tự đóng.", "assignee": null, "actor": null, "createdAt": "…" }
]
```

Ba lệnh ghi:

```jsonc
// POST /shipments/issues/{issueId}/assign   → 200, trả vụ việc sau khi gán
{ "assigneeUserId": "01973…", "note": "Nhờ Vân gọi hãng" }        // note tuỳ chọn, ≤ 2000 ký tự

// POST /shipments/issues/{issueId}/comments → 201, trả mốc lịch sử vừa ghi
{ "note": "Đã gọi hãng, hẹn quét lại trong hôm nay" }              // bắt buộc, không rỗng, ≤ 2000

// POST /shipments/issues/{issueId}/resolve  → 200, trả vụ việc đã đóng
{ "resolution": "Hãng giao lại thành công ngày 03/09", "note": null } // resolution BẮT BUỘC, ≤ 2000
```

Bảy điểm FE và BE đã thống nhất:

1. **Mỗi kiện đúng một nhãn**, xếp theo mức nặng `returning` > `delivery_failed` > `stalled`. Backend giữ đúng thứ tự của `vishop-next/apps/app/lib/shipping.ts`, nếu không thẻ đếm ở màn `/shipping/exceptions` và số của "Việc hôm nay" sẽ lệch nhau.
2. **`delivery_failed` là lịch sử, không phải trạng thái hiện tại.** Kiện giao hỏng một lần rồi quay lại `OUT_FOR_DELIVERY` vẫn mang nhãn này, vì việc phải làm là gọi khách trước lượt giao lại. Nó không bao giờ tự khỏi.
3. **Chưa có loại `overdue`.** Hệ thống chưa lưu hạn giao sàn cam kết với người mua (`deliveryDueAt`), và trả một số suy đoán còn tệ hơn không trả. Thẻ "Quá hạn giao" vĩnh viễn bằng 0 cho tới khi có ticket bổ sung cột đó.
4. **`severity` suy từ `reason` ở backend**, trả kèm để FE không giữ bản sao thứ hai của bảng tra.
5. **`journey.stopReason = "abandoned"` phải hiện rõ**: ViShop đã ngừng hỏi sàn nên dữ liệu sẽ không tốt lên nữa, người xử lý phải gọi hãng. Vụ việc **không** tự đóng ở ca này — đó đúng là ca kiện có thể thất lạc thật.
6. **Hệ thống chỉ tự đóng hai ca**: kiện đã giao thành công và kiện đã bị huỷ. Kiện `RETURNED` **không** tự đóng vì kho chưa nhận và chưa nhập lại tồn, mà module hoàn hàng thì chưa dựng. Không có cơ chế mở lại: kiện tắc lần nữa sẽ sinh vụ việc mới.
7. **Ba lệnh ghi không nhận `Idempotency-Key`** (§0.4 chỉ bắt buộc header đó cho lệnh ghi ra sàn). Hệ quả: bấm hai lần vì mạng chậm sinh hai mốc lịch sử **không xoá được**, nên nút phải khoá trong lúc gửi.

Quyền: hai route đọc dùng `shipping.view` (vai Kho cần thấy kiện đang chuyển hoàn để chuẩn bị nhận hàng), ba lệnh ghi dùng `shipping.exception.handle`.

Mã lỗi riêng: 404 `shipment_issue_not_found` (kể cả khi vụ việc thuộc gian hàng ngoài phạm vi), 409 `shipment_issue_already_closed`, 404 `tenant_member_not_found` khi gán cho người ngoài tổ chức, 422 `validation_failed` khi đóng mà thiếu `resolution`.

### 4.6 Tem SKU nội bộ (barcode) — M07

Phục vụ dán lên sản phẩm hoặc vị trí kệ kho để quét kiểm hàng (M08 / FR-08-02). Độc lập tuyệt đối với shipping label TikTok, không gọi API sàn, không bắt buộc `shopId`.

| Method | Path                                       | FR            | Ghi chú                                                        |
| ------ | ------------------------------------------ | ------------- | -------------------------------------------------------------- |
| POST   | `/api/v1/merchant-skus/label-jobs`         | FR-07-11 (P1) | Tạo lệnh in tem mã vạch Code 128 Set B chạy nền (202 Accepted) |
| GET    | `/api/v1/merchant-skus/label-jobs/{jobId}` | FR-07-11 (P1) | Xem tiến độ & link tải `downloadUrl` khi hoàn thành (200 OK)   |

```json
// POST /api/v1/merchant-skus/label-jobs → 202
{
  "items": [
    {
      "merchantSkuId": "01973b8e-4c21-7a10-9f3d-90a1b2c3d4e5",
      "quantity": 5
    }
  ],
  "labelSize": "SKU40x30"
}

// Response (202 / 200)
{
  "id": "01973b8e-4c21-7a10-9f3d-5e6f7a8b9c0d",
  "status": "queued",
  "labelSize": "SKU40x30",
  "totalLabelCount": 5,
  "downloadUrl": null,
  "failureReason": null,
  "createdAt": "2026-08-28T10:00:00Z",
  "completedAt": null
}
```

### 4.7 Bàn quét kho (scan session) — M08

| Method | Path                                         | FR                      | Ghi chú                                                                |
| ------ | -------------------------------------------- | ----------------------- | ---------------------------------------------------------------------- |
| POST   | `/api/v1/scan-sessions`                      | FR-08-01/02 (P0)        | Mở phiên bằng `packageId` (quét kiểm) hoặc `trackingNumber` (bàn giao) |
| GET    | `/api/v1/scan-sessions/{sessionId}`          | FR-08-02, FR-08-06 (P0) | Lấy lại phiên dở, **kèm nhật ký từng lượt quét**                       |
| POST   | `/api/v1/scan-sessions/{sessionId}/scans`    | FR-08-02/03 (P0)        | Một lượt bóp cò                                                        |
| POST   | `/api/v1/scan-sessions/{sessionId}/complete` | FR-08-04/09 (P0/P1)     | Chốt phiên, kèm `actualWeightG` tuỳ chọn                               |
| GET    | `/api/v1/scan-sessions`                      | FR-08-06 (P0)           | Nhật ký cấp phiên, phân trang **cursor**                               |

**Hai loại việc, một màn.** `sessionType` là `inspection` (quét kiểm, đứng trước lệnh giao) hoặc `handover` (quét bàn giao, sau khi in tem). Bỏ trống khi mở phiên thì BE tự nhận theo `stage` của kiện: `NEED_SCAN` → `inspection`, `READY_TO_HANDOVER` → `handover`. Đây là giá trị **do ViShop tự đặt** nên viết thường (§quy ước enum ở §3).

**Quyền xét theo loại việc, không theo route.** Cả năm endpoint chấp nhận `scan.check` **hoặc** `scan.handover`; quyền cụ thể được kiểm sau khi biết loại phiên — `inspection` cần `scan.check`, `handover` cần `scan.handover`, thiếu thì `403`. Lý do: loại việc chỉ chốt được sau khi đọc kiện, nên không gác được ở cửa route.

```json
// POST /api/v1/scan-sessions → 201
{ "packageId": "01973...", "autoComplete": false }

// 201 — phiên vừa mở (hoặc phiên dở được trả lại, khi đó resumed = true)
{
  "id": "01973...", "packageId": "01973...", "externalPackageId": "576620672268508345",
  "sessionType": "inspection", "operatorUserId": "01973...", "operatorName": "Nguyễn Văn An",
  "result": "in_progress", "expectedItems": 3, "scannedItems": 0, "mismatchNote": null,
  "expectedWeightG": 750, "actualWeightG": null, "weightWarned": false, "autoComplete": false,
  "lines": [ { "merchantSkuId": "01973...", "skuCodeSnapshot": "AO-THUN-XANH-M",
               "expectedQty": 2, "scannedQty": 0 } ],
  "startedAt": "2026-08-28T04:31:11.469Z", "completedAt": null, "durationSec": 0,
  "resumed": false
}

// POST /api/v1/scan-sessions/{sessionId}/scans → 201
{ "code": "8938505974194" }
{ "outcome": "accepted", "completed": false,
  "line": { "merchantSkuId": "01973...", "skuCodeSnapshot": "AO-THUN-XANH-M",
            "expectedQty": 2, "scannedQty": 1 },
  "session": { "...": "phiên sau lượt quét" } }

// ... còn lượt bị từ chối thì kèm `message`:
{ "outcome": "rejected_not_in_package",
  "message": "Mã này không thuộc kiện đang quét",
  "line": null, "completed": false,
  "session": { "...": "counters không đổi" } }

// POST /api/v1/scan-sessions/{sessionId}/complete → 201
{ "mismatchNote": null, "actualWeightG": 900 }
{ "session": { "result": "match", "durationSec": 42, "...": "..." },
  "weightWarning": true, "handedOver": true }
```

Sáu điểm FE và BE phải thống nhất trước khi code:

1. **Quét kiểm mở kiện bằng mã kiện, quét bàn giao mở bằng mã vận đơn.** Không đảo được: lúc quét kiểm chưa gọi lệnh giao nên chưa có tem, mà cũng chưa có mã vận đơn để đọc (§4.3 ràng buộc 1). Gửi cả hai trường trong một request là `422` — hai mã khác nhau là hai kiện khác nhau, và chọn bừa một cái là cách bàn quét soát nhầm kiện.
2. **Lượt quét sai vẫn trả `201`.** `outcome` mang lý do: `accepted`, `rejected_not_in_package`, `rejected_over_quantity`, `rejected_duplicate_window`. Với bàn quét bắn liên tục thì mã sai là chuyện thường ngày chứ không phải lỗi hệ thống, nên FE đọc `outcome` để phát âm thanh cảnh báo (FR-08-03) chứ không bắt lỗi HTTP. `rejected_duplicate_window` là máy quét cầm tay bắn hai lần trong một lần bóp cò — bỏ qua, không đếm, và **không** cảnh báo như quét sai.

   Lượt bị từ chối kèm **`message`** — cùng lý do đó diễn đạt bằng lời, hiển thị thẳng lên bàn quét được. Lượt `accepted` không có trường này. BE giữ lời để hai màn quét (kiểm và bàn giao) không tự chế ra hai câu khác nhau cho cùng một sự từ chối; FE cần đổi lời hay đổi ngôn ngữ thì khoá theo `outcome`, đó mới là giá trị bền. Cùng khuôn với `reason` + `message` của `POST /api/v1/sku-mappings/accept`.

3. **`result` chỉ có ba giá trị**: `in_progress`, `match`, `mismatch`. Thiếu, thừa và sai SKU đều là `mismatch`; muốn biết lệch ở đâu thì đọc `lines[]` — chênh lệch nằm ở `expectedQty` so với `scannedQty` từng dòng.
4. **Phiên lệch không bàn giao** (BR-08-02). `complete` một phiên `handover` chỉ đánh dấu kiện đã bàn giao khi `result = match`; phiên lệch trả `handedOver: false` và kiện giữ nguyên. Phiên đã chốt là bằng chứng, không chốt lại được — gọi lần hai trả `409 scan_session_already_completed`.
5. **Cảnh báo lệch cân không chặn** (FR-08-09). Gửi `actualWeightG` khi chốt, lệch quá ngưỡng của workspace thì `weightWarning: true` nhưng phiên vẫn chốt bình thường: kiện đã đóng rồi, chặn ở đây chỉ làm nó kẹt lại không ai gỡ được. `expectedWeightG` là `null` khi kiện có SKU chưa khai cân nặng — một tổng thiếu phần sẽ bị đọc nhầm thành tổng thật, nên thà không có.
6. **Mọi lượt quét vào nhật ký, kể cả lượt bị từ chối** (BR-08-03), và nhật ký ghi **người đang quét** chứ không phải người mở phiên — đổi ca giữa chừng vẫn truy được đúng người. `operatorName` trên phiên thì ngược lại: chụp lại lúc mở và không đổi nữa, vì đổi tên tài khoản sau đó không được viết lại lịch sử.

`durationSec` suy tại thời điểm đọc, không lưu: phiên đã chốt đếm từ lúc mở tới lúc chốt, phiên còn mở đếm tới thời điểm gọi API nên giá trị còn tăng.

#### 4.7.1 Nhật ký hai tầng — VIS-136

Nhật ký quét có **hai tầng**, và FE đọc tầng nào tuỳ màn:

| Tầng          | Endpoint                                             | Trả về                                                                             |
| ------------- | ---------------------------------------------------- | ---------------------------------------------------------------------------------- |
| **Phiên**     | `GET /api/v1/scan-sessions`                          | Mỗi dòng là một lượt soát: ai mở, kiện nào, lúc nào, kết quả gì. Phân trang cursor |
| **Lượt quét** | `GET /api/v1/scan-sessions/{sessionId}` → `events[]` | Từng lần bóp cò trong một phiên, **cũ nhất trước**                                 |

```jsonc
// GET /api/v1/scan-sessions/{sessionId} → 200 — như phiên ở trên, thêm events[]
{
  "id": "01973...",
  "result": "mismatch",
  "...": "...",
  "events": [
    {
      "id": "01973...",
      "scannedCode": "8938505974194",
      "matchedMerchantSkuId": "01973...",
      "outcome": "accepted",
      "operatorUserId": "01973...",
      "operatorName": "Nguyễn Văn An",
      "scannedAt": "2026-08-28T04:31:11.469Z",
    },
    {
      "id": "01973...",
      "scannedCode": "MU-DO-S",
      "matchedMerchantSkuId": null,
      "outcome": "rejected_not_in_package",
      "operatorUserId": "01973...",
      "operatorName": "Trần Thị Bình",
      "scannedAt": "2026-08-28T04:31:19.002Z",
    },
  ],
}
```

Bốn điểm về nhật ký:

1. **`events[]` chỉ có ở route chi tiết.** `POST /{sessionId}/scans` cũng trả nguyên phiên sau mỗi
   lượt, nhưng **không** kèm `events[]`: đó là đường nóng nhất của module, bàn quét bắn liên tục, kéo
   cả nhật ký theo mỗi lần bóp cò là cái giá không cần trả. Muốn xem nhật ký thì gọi route chi tiết.
2. **Tối đa 500 lượt gần nhất mỗi phiên**, không phân trang. Một phiên soát đúng một kiện nên số lượt
   bị chặn trên bởi sức một người quét trước khi chốt; trần này là chốt chặn cho bàn quét bị bỏ quên.
3. **`operatorName` trong `events[]` tra tại thời điểm đọc**, khác với `operatorName` của phiên
   (chụp lại lúc mở). Ở tầng lượt quét thì `operatorUserId` mới là bằng chứng, tên chỉ là nhãn — và
   ghi tên vào mỗi lượt đồng nghĩa đặt một truy vấn tài khoản lên đường nóng. Tài khoản đã xoá trả `null`.
4. **Lượt bị từ chối cũng nằm trong `events[]`** (BR-08-03) — đó chính là dữ liệu cần khi truy vết.

**Bộ lọc của `GET /api/v1/scan-sessions`** (gộp bằng AND):

| Tham số                     | Ý nghĩa                              |
| --------------------------- | ------------------------------------ |
| `packageId`                 | Theo kiện                            |
| `sessionType`               | `inspection` `handover`              |
| `result`                    | `in_progress` `match` `mismatch`     |
| `scannedByUserId`           | Theo **người quét** (xem dưới)       |
| `startedFrom` / `startedTo` | Khoảng thời gian mở phiên (ISO 8601) |

`scannedByUserId` khớp phiên do người đó **mở**, hoặc có ít nhất một **lượt quét** của người đó trong
`scan_events`. Đổi ca giữa phiên là chuyện thường trong kho, nên người mở và người quét có thể khác
nhau, và tra theo ai cũng phải ra phiên. Phiên vừa mở mà chưa quét lượt nào vẫn khớp người mở. Mỗi
phiên xuất hiện một lần dù người đó quét nhiều lượt.

---

### 4.8 Hoàn hàng — M09

| Method | Path                           | Quyền                             | Ghi chú                                                                                                                                                     |
| ------ | ------------------------------ | --------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GET    | `/api/v1/returns`              | `returns.view`                    | Cursor, mới nhất trước. Tab `state=all\|pending`. `overdueOnly=true` lọc đơn quá hạn nhập kho (FR-09-05). `search` khớp một phần mã yêu cầu hoặc mã đơn sàn |
| GET    | `/api/v1/returns/summary`      | `returns.view`                    | `{ total, pending }` cho hai tab và huy hiệu sidebar                                                                                                        |
| GET    | `/api/v1/returns/{id}`         | `returns.view`                    | Kèm `lines[]` của **riêng** yêu cầu đó, mỗi dòng có `originWarehouseId` (gợi ý) và `restockWarehouseId` (đã chốt)                                           |
| POST   | `/api/v1/returns/sync`         | `returns.view` + `shops.sync.run` | Kéo một gian hàng về theo cửa sổ thời gian                                                                                                                  |
| POST   | `/api/v1/returns/{id}/receive` | `returns.receive`                 | Đánh dấu kho đã nhận hàng về                                                                                                                                |
| POST   | `/api/v1/returns/{id}/restock` | `returns.receive`                 | VIS-168: ghi kết quả phân loại tốt/lỗi. Hợp đồng đầy đủ ở mục 5 phía dưới (§5.1)                                                                            |

**Bốn điều FE phải dựng theo, không phải bốn điều tuỳ chọn:**

1. **Một đơn có thể phát sinh nhiều yêu cầu hoàn.** Mỗi yêu cầu chỉ động tới một tập con SKU và số
   lượng. Màn chi tiết đơn phải chịu được danh sách, không phải một ô.
2. **`arbitrationStatus` là trường riêng**, không phải một giá trị của `returnStatus`. Một yêu cầu
   vừa đang chờ người bán duyệt vừa đang bị khiếu nại là chuyện bình thường.
3. **`returnType: REPLACEMENT` không phải một dạng hoàn tiền.** Đổi hàng sinh một lượt giao mới,
   không có lần trả tiền nào. Gộp chung luồng duyệt là sai nghiệp vụ, không phải sai giao diện.
4. **`canBuyerKeepItem: true` nghĩa là không có hàng nào về kho.** Sàn hoàn tiền mà cho khách giữ
   hàng. Màn nhận hàng phải chặn, không được cho bấm rồi tạo tồn không có thật.
5. **`isOverdue` tính thời gian thực, không lưu (FR-09-05).** Cờ biến mất ngay khi `receivedAt`
   hoặc `restockedAt` được ghi — không cần đợi chu kỳ quét cảnh báo tiếp theo. Badge trên màn danh
   sách phải đọc lại giá trị mỗi lần fetch, không cache theo id.

**`state=pending`** là nhóm người bán phải xử lý, gồm đúng hai trạng thái:
`RETURN_OR_REFUND_REQUEST_PENDING` (chờ người bán quyết) và `BUYER_SHIPPED_ITEM` (khách đã gửi,
kho phải nhận). `AWAITING_BUYER_SHIP` và `AWAITING_BUYER_RESPONSE` đang chờ khách; năm trạng thái
còn lại đã kết thúc. Không cái nào là việc người bán làm được.

**Số đếm tách khỏi danh sách có chủ ý.** `GET /returns/summary` chỉ thu hẹp theo `shopId`, không
theo `returnStatus` hay `returnType`: hai bộ lọc đó nằm _bên trong_ một tab, số đếm mà đổi theo thì
mâu thuẫn với chính cái tab nó dán nhãn.

**`sellerNextActionDeadline` đọc từ sàn, đi kèm `sellerNextAction`.** Không có hằng số 24 giờ nào
trong hệ thống — con số đó là của luồng huỷ đơn, khác luồng.

**Tiền.** `refundAmount` là số nguyên theo đơn vị nhỏ nhất của `currency` trên chính yêu cầu đó, như
mọi số tiền khác trong hợp đồng này. `null` nghĩa là sàn không gửi số tiền, hoặc gửi bằng loại tiền
hệ thống chưa khai báo — trường hợp sau được đếm ở `amountsUnconverted` của route đồng bộ. Yêu cầu
vẫn hiện, chỉ để trống ô tiền; không suy đoán.

**`POST /returns/sync`** nhận `{ shopId, updatedFrom?, updatedTo? }`. Cửa sổ tính theo **thời điểm
sàn cập nhật**, không phải lúc tạo: một yêu cầu mở từ tuần trước vẫn đổi trạng thái hôm nay. Bỏ
trống thì lùi 7 ngày tính từ `updatedTo` (mặc định là hiện tại). Trả về:

```json
{
  "created": 12,
  "updated": 40,
  "amountsUnconverted": 0,
  "ordersLinked": 2,
  "pagesProcessed": 2
}
```

Chạy lại **không** nhân đôi dữ liệu và **không** xoá `restockedQuantity` kho đã ghi. Yêu cầu về
trước khi đơn gốc kịp đồng bộ vẫn lưu được với `orderId: null` — `externalOrderId` luôn có, và lần
chạy sau nối lại, đếm ở `ordersLinked`.

**`lines[]`** là SKU và tiền hoàn của riêng yêu cầu đó. Sàn trả một dòng cho **mỗi đơn vị hàng**;
ViShop gom theo SKU và đếm, giữ lại mã từng đơn vị. `merchantSkuId: null` nghĩa là listing chưa ghép
nối — dòng đó **không nhập lại kho được**, và màn nhận hàng phải nói ra thay vì lặng lẽ bỏ qua.

**`POST /returns/{id}/restock` (VIS-168).** Phân loại tốt/lỗi là việc nội bộ ViShop — TikTok không
có chỗ nhận số tốt/lỗi theo từng món. Body:

```json
{
  "defectiveWarehouseId": "<uuid kho RETURN — bắt buộc khi có defectiveQuantity > 0>",
  "lines": [
    {
      "returnLineId": "<uuid>",
      "restockQuantity": 2,
      "defectiveQuantity": 1,
      "warehouseId": "<uuid kho SALES — bắt buộc khi restockQuantity > 0>"
    }
  ]
}
```

Kho hàng tốt chọn **theo từng dòng** (đơn xuất từ nhiều kiện ở nhiều kho là trường hợp tổng quát);
kho hàng lỗi chọn **chung cho cả yêu cầu**. 200 trả `{ alreadyRestocked, movements[], defectiveMovements[] }`
— `movements[]` cộng `available` kho SALES và ghi `RETURN_RECEIVED`; `defectiveMovements[]` cộng
`defectiveQty` kho RETURN, ghi `RETURN_DEFECTIVE_RECEIVED`, không đổi `available` nên không đẩy lên
sàn. Kho sai loại (kho lỗi cho hàng tốt, kho bán cho hàng lỗi) → 422
`return_restock_warehouse_type_mismatch`, không ghi gì. Gọi lại một yêu cầu đã chốt → 200
`alreadyRestocked: true`, không ghi thêm.

---

## 5. InventoryProvider — M05 Tồn kho & đẩy tồn

| Method | Path                                    | FR                      | Ghi chú                                                                                                    |
| ------ | --------------------------------------- | ----------------------- | ---------------------------------------------------------------------------------------------------------- |
| GET    | `/api/v1/inventories`                   | FR-05-01 (P0)           | `onHand`, `available`, `locked`, `incoming`, giá vốn, giá trị tồn                                          |
| POST   | `/api/v1/inventories/stock-in`          | FR-05-02 (P0)           | SKU, SL, đơn giá, lý do, ghi chú                                                                           |
| POST   | `/api/v1/inventories/stock-out`         | FR-05-03 (P0)           | SL, lý do                                                                                                  |
| POST   | `/api/v1/inventories/stocktakes`        | FR-05-04 (P0)           | Tạo phiếu, nhập số đếm, tính lệch, xác nhận                                                                |
| GET    | `/api/v1/inventories/movements`         | FR-05-05, FR-05-06 (P0) | Sổ biến động, filter, export                                                                               |
| POST   | `/api/v1/inventories/import`            | FR-05-07 (P0)           | Nhập kho hàng loạt                                                                                         |
| GET    | `/api/v1/inventories/import/template`   | FR-05-07 (P0)           | Tải file XLSX mẫu: `skuCode`, `quantity`, `unitCost`, `reason`, `note`                                     |
| PUT    | `/api/v1/shops/{shopId}/inventory-push` | FR-05-11, 05-12 (P0)    | Bật/tắt đẩy tự động, tần suất, số lần thử lại. Tỉ lệ đẩy và tồn an toàn **không** ở đây — xem tuyến (§5.1) |
| POST   | `/api/v1/inventories/push`              | FR-05-16 (P0)           | Đẩy thủ công — `scope: sku\|bulk\|shop` — `Idempotency-Key` bắt buộc                                       |
| GET    | `/api/v1/inventories/push-logs`         | FR-05-15 (P0)           | SKU, shop, giá trị đẩy, kết quả, lý do lỗi                                                                 |
| POST   | `/api/v1/inventories/push-preview`      | FR-05-19 (P1)           | Xem trước tác động trước khi bật đẩy tồn lần đầu                                                           |

`GET /api/v1/inventories/movements` và `GET /api/v1/inventories/push-logs` là hai bảng chỉ tăng, không sửa — bắt buộc cursor `?limit=&startingAfter=`, và `totalCount` để `null`. Đây đúng trường hợp mà repo cấm đếm tổng (§0.2).

### 5.1 Kho, vị trí kệ và tồn theo kho

Tồn không còn là một con số cho mỗi SKU. Một SKU nằm ở nhiều kho, mỗi kho có tồn riêng, và kiện hàng phải được nhặt ở đúng kho có hàng.

| Method           | Path                                               | FR            | Trạng thái | Ghi chú                                                                                                                                                                               |
| ---------------- | -------------------------------------------------- | ------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GET, POST        | `/api/v1/warehouses`                               | FR-05-20 (P0) | Đã dựng    | Kho ViShop, kèm `type`, tỉnh/xã và người trực. Ánh xạ sang kho sàn **không** nằm ở đây — xem `/inventories/push-routes`                                                               |
| GET, PUT, DELETE | `/api/v1/warehouses/{id}`                          | FR-05-20 (P0) | Đã dựng    | Đọc một kho theo khoá; sửa tên/địa chỉ/loại/liên hệ, chuyển cờ mặc định (`isDefault: true`), ngừng dùng (`status: INACTIVE`) hoặc xoá mềm. Kho mặc định không ngừng và không xoá được |
| GET, POST        | `/api/v1/inventories/push-routes`                  | FR-05-26 (P0) | Đã dựng    | Tuyến kho × gian hàng: mang `platformWarehouseId`, `ratio`, `safetyStock`                                                                                                             |
| GET              | `/api/v1/shops/{shopId}/platform-warehouses`       | FR-05-20 (P0) | Đã dựng    | Kho phía sàn của một gian hàng, để đối chiếu                                                                                                                                          |
| POST             | `/api/v1/shops/{shopId}/platform-warehouses/syncs` | FR-05-20 (P0) | Đã dựng    | Kéo danh sách kho từ [Get Warehouse List](https://partner.tiktokshop.com/docv2/page/650aa418defece02be6e66b6) — **một gian hàng mỗi lượt gọi**                                        |
| GET, POST        | `/api/v1/warehouses/{warehouseId}/bins`            | FR-05-21 (P0) | Đã dựng    | Vị trí kệ — khái niệm nội bộ ViShop, sàn không có. Sắp theo khu rồi mã kệ, kèm số SKU và tồn trên kệ. `?search=` lọc theo mã kệ hoặc khu                                              |
| PATCH, DELETE    | `/api/v1/warehouses/{warehouseId}/bins/{binId}`    | FR-05-21 (P0) | Đã dựng    | Sửa mã kệ/khu, hoặc xoá kệ đang trống. Kệ còn hàng thì từ chối xoá                                                                                                                    |
| PUT              | `/api/v1/inventories/bin-assignments`              | FR-05-21 (P0) | Đã dựng    | Xếp hàng của một SKU vào kệ, `binId: null` là đưa về khu tạm. Ghi `inventory_bin_histories` kèm người thao tác                                                                        |
| GET              | `/api/v1/inventories/by-warehouse`                 | FR-05-22 (P0) | Chưa dựng  | `onHand`/`available`/`locked` tách theo từng kho                                                                                                                                      |
| POST             | `/api/v1/inventories/transfers`                    | FR-05-23 (P0) | Chưa dựng  | Phiếu chuyển kho, tồn nguồn chuyển sang `inTransit`                                                                                                                                   |

> **`type` chia kho bán và kho hàng lỗi, và kho hàng lỗi không bao giờ là kho mặc định.** `SALES` giữ hàng bán được và cấp tồn cho tuyến đẩy; `RETURN` (VIS-168) **chỉ giữ hàng hoàn bị đánh giá là hàng lỗi** (`inventories.defectiveQty`) — không bao giờ nhận hàng tốt, không tạo được tuyến đẩy (`422 push_route_return_warehouse_not_allowed`), và không được bộ phân bổ đơn chọn. Ba đường có thể biến một kho hàng lỗi thành kho mặc định — tạo kho đầu tiên của tổ chức, `PUT /{id}` với `isDefault: true`, và `PUT /{id}` đổi `type` của kho mặc định — cả ba đều trả `422` với `warehouse_default_cannot_be_disabled`. Ràng buộc `warehouses_default_is_sales_chk` giữ cùng luật ở tầng CSDL cho dịch vụ nào đó sau này ghi thẳng vào bảng. Đổi loại kho còn bị chặn khi kho còn hàng: SALES→RETURN từ chối nếu còn tuyến đẩy hoặc còn `available`/`locked`/`reserved`/`blockedQty`; RETURN→SALES từ chối nếu còn `defectiveQty` — cả hai trả `422 warehouse_type_change_not_allowed`.
> **Xoá kho chặn cả hàng đang trên đường, không chỉ hàng trên kệ.** Ngoài kho mặc định (`warehouse_default_cannot_be_deleted`) và `onHand > 0` (`warehouse_delete_has_stock`), `DELETE` còn trả `409 warehouse_delete_has_open_transfer` khi còn phiếu chuyển kho `DRAFT`/`IN_TRANSIT` trỏ vào kho ở một trong hai đầu, hoặc còn `inTransitQty`/`incoming` khác 0. Hàng của một phiếu đang đi đã rời kho nguồn và chỉ có thể hạ xuống kho đích: xoá kho lúc ấy là để nó biến mất khỏi mọi báo cáo mà không dòng sổ nào giải thích (VIS-370). Tuyến đẩy tồn trỏ vào kho bị xoá theo, và mọi vị trí kệ của kho cũng bị xoá mềm theo.
> **Địa chỉ kho là hai cấp: tỉnh rồi xã.** Việt Nam bỏ cấp quận huyện từ 01/07/2025. `province`/`ward` là chữ tự do, không có bảng tham chiếu — ViShop không định tuyến vận chuyển, địa chỉ lấy hàng do sàn giữ, nên một cột mã sẽ là khoá ngoại trỏ vào hư không. `managerName`/`phone` cũng là chữ tự do vì nhân viên kho thường không có tài khoản ViShop. Gửi chuỗi rỗng để xoá — API đọc thành `null`.
> **Ánh xạ kho ViShop ↔ kho sàn nằm trên tuyến, không nằm trên kho.** `platformWarehouseId` là cột của `shop_warehouse_route`, khoá theo cặp `(shopId, warehouseId)`. Một kho ViShop bán cho ba gian hàng thì khớp với ba kho sàn khác nhau, nên "kho sàn của một kho ViShop" không phải một khái niệm biểu diễn được. Đừng đặt trường này lên `GET /api/v1/warehouses`.
> **Kho ViShop ≠ kho TikTok.** Sàn quản warehouse để tính phí và vùng giao ([Get Warehouse List](https://partner.tiktokshop.com/docv2/page/650aa418defece02be6e66b6), tồn theo kho đọc từ [Inventory Search](https://partner.tiktokshop.com/docv2/page/650a9191c16ffe02b8eec161)). Vị trí kệ (`bin`) là thứ chỉ ViShop có — TikTok không biết hàng nằm ở kệ nào, và ViShop không gửi `bin_code` lên sàn.
> **Đẩy tồn gửi một dòng cho mỗi kho sàn, không gửi một con số tổng.** [Update Inventory](https://partner.tiktokshop.com/docv2/page/6503068fc20ad60284b38858) nhận `inventory[]` với `warehouse_id` bắt buộc trên từng phần tử — không có dạng payload nào chấp nhận một số duy nhất. Số của mỗi dòng tính trên `available` của kho ViShop nối tới kho sàn đó qua tuyến, sau khi áp `ratio` và trừ `safetyStock` của chính tuyến ấy; nhiều kho ViShop trỏ chung một kho sàn thì cộng dồn. Xem BR-05-07 trong build-spec.
>
> [Update Inventory](https://partner.tiktokshop.com/docv2/page/6503068fc20ad60284b38858) **không cho đổi danh sách warehouse** của SKU (mã lỗi `12052533`/`12052530`/`12052531`/`12019028` ở bảng dưới). Việc mở/đóng kho cho một SKU phải làm trên Seller Center; ViShop chỉ ghi số lượng vào kho sàn đã mở sẵn.

### 5.2 Lô hàng & hạn sử dụng

| Method | Path                                          | FR            | Ghi chú                                  |
| ------ | --------------------------------------------- | ------------- | ---------------------------------------- |
| GET    | `/api/v1/inventory/batches`                   | FR-05-24 (P0) | Lô, NSX, HSD, số còn lại, kho, vị trí kệ |
| POST   | `/api/v1/inventory/batches`                   | FR-05-24 (P0) | Tạo lô khi nhập kho hàng có hạn          |
| POST   | `/api/v1/inventory/batches/{batchId}/block`   | FR-05-25 (P0) | Khoá lô, không cho sóng nhặt lấy vào     |
| POST   | `/api/v1/inventory/batches/{batchId}/dispose` | FR-05-25 (P0) | Huỷ lô, ghi giảm tồn kèm lý do           |

Bốn mức cảnh báo, xét theo số ngày còn lại tới HSD:

| Mức       | Điều kiện                   | Ý nghĩa vận hành                             |
| --------- | --------------------------- | -------------------------------------------- |
| `EXPIRED` | Đã quá HSD                  | Không được bán, phải huỷ                     |
| `BLOCKED` | Còn ≤ `salesCutoffDays`     | Sàn không cho bán nữa dù chưa hết hạn        |
| `WARNING` | Còn ≤ `expirationAlertDays` | Còn cứu được — đẩy bán trước hoặc chuyển kho |
| `OK`      | Ngoài các mức trên          |                                              |

Ba ngưỡng trên đọc từ `exp_base_info` của [Search Goods Hazmat And Expiration Info](https://partner.tiktokshop.com/docv2/page/69e7252e795c140490ac8ef4) — phía TikTok là `sales_cutoff_days`, `expiration_alert_days`, `inbound_cutoff_days`; ViShop trả ra FE dưới dạng `salesCutoffDays`, `expirationAlertDays`, `inboundCutoffDays`. Cùng response còn có hai cờ `is_lot_code` và `is_expiration_management` (→ `isLotCode`, `isExpirationManagement`) cho biết mặt hàng đó có bị sàn quản theo lô/hạn hay không.

> **⚠️ Giới hạn phạm vi — BE phải xử lý tường minh, không được im lặng.** Endpoint này nằm dưới nhóm **FBT** (`POST /fbt/202603/goods/hazmat_and_expiration_info/search`), tức chính sách áp cho hàng nằm trong kho TikTok. Với hàng seller tự giữ — đa số hàng ViShop quản — **chưa xác minh được có nguồn ngưỡng tương đương**.
>
> Vì vậy response của `GET /api/v1/inventory/batches` bắt buộc có thêm `thresholdSource`, nhận `platform_fbt` hoặc `vishop_default`. FE dùng field này để chọn cách nói: `platform_fbt` thì mới được viết "sàn không cho bán lô này", `vishop_default` thì phải viết là cảnh báo nội bộ và cho seller sửa ngưỡng ở cấp danh mục hoặc cấp SKU. Nói sai chỗ này là nói với seller rằng sàn cấm bán trong khi sàn không cấm.

**Request/response rút gọn:**

```json
// POST /api/v1/inventory/push
// Header: Idempotency-Key: <uuid>
{ "scope": "bulk", "merchantSkuIds": ["01973...", "01973...", "01973..."] }

// 200
{
  "results": [
    { "merchantSkuId": "01973...", "shopId": "01973...", "status": "success", "pushedQuantity": 76 },
    { "merchantSkuId": "01973...", "shopId": "01973...", "status": "success",
      "pushedQuantity": 3, "note": "SKU sàn combo ghép nối (FR-04-06) — đẩy thẳng cho chính SKU này, SL theo BR-05-03" }
  ]
}
```

Như lô RTS ở §4.1, đây là kết quả từng phần tử trong một request đã xử lý thành công — **200**, không phải problem+json, kể cả khi một phần SKU đẩy hỏng (§0.4).

`pushedQuantity` tính theo `BR-05-01`: `FLOOR((available − tồn_an_toàn) × tỉ_lệ_đẩy)`, tối thiểu 0.

Tài liệu trước đây gộp lẫn hai loại "combo" khác nhau — phải tách bạch:

- **SKU sàn dạng combo ghép nối của ViShop** (FR-04-06 — một SKU sàn ↔ nhiều SKU kho theo số lượng qua `sku_mapping`): đây là SKU thường trên TikTok, sàn **không chặn ghi tồn**. Đích đẩy vẫn là chính SKU sàn đó; SL tính theo `BR-05-03` = `MIN(available từng SKU kho ÷ SL trong mapping)`. Một SKU kho biến động → mọi combo chứa nó phải được đẩy lại (BR-05-03 nguyên văn).
- **SKU combo native của TikTok** (`COMBINED_PRODUCT` — loại sản phẩm đặc biệt phía sàn, ghi chú audit FR-04-10): Update Inventory API **từ chối** ghi tồn trực tiếp cho SKU này (lỗi `12052849`/`12052850`, ghi chú audit FR-05-16) — sàn tự tính tồn từ SKU thành phần. BE **không gọi Update Inventory** cho SKU loại này; các SKU thành phần được đẩy qua luồng đẩy tồn thông thường của chính chúng, không phải một hành vi "resolve combo về SKU con" riêng.

`BR-05-03` trong build-spec không phân biệt hai loại combo trên — cách tách bạch ở trên là diễn giải dựa trên ghi chú audit FR-04-10/FR-05-16, **cần Tech Lead xác nhận trước khi implement**, chưa phải điều đã chốt trong spec.

**Mã lỗi nghiệp vụ — nhóm quan trọng nhất của module này, đã audit thật từ Update Inventory API:**

| `code`                             | `kind` → status                     | Mã lỗi gốc TikTok                                 | Tình huống                                                                                                                                                                           | `detail` seller-facing                                                           | Xử lý bắt buộc                                                                                                                                                                                                                                                                                                                               |
| ---------------------------------- | ----------------------------------- | ------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `inv_combo_not_directly_updatable` | `validation` → 422                  | `12052849` / `12052850`                           | Gọi Update Inventory trực tiếp cho SKU combo **native TikTok (`COMBINED_PRODUCT`)** — không áp dụng cho SKU sàn combo ghép nối FR-04-06 (loại này đẩy tồn bình thường, xem mục trên) | "SKU combo không đẩy tồn trực tiếp — tồn được tính tự động theo SKU thành phần." | BE chặn ở tầng service trước khi gọi sàn cho SKU `COMBINED_PRODUCT`; các SKU thành phần đẩy qua luồng đẩy tồn thông thường của chính chúng. FE không hiển thị lựa chọn "đẩy tồn" trên chi tiết SKU combo loại này, chỉ hiển thị tồn khả dụng tính toán. Phân loại combo dựa trên diễn giải audit FR-04-10/FR-05-16 — cần Tech Lead xác nhận. |
| `inv_warehouse_change_blocked`     | `validation` → 422                  | `12052533` / `12052530` / `12052531` / `12019028` | Update Inventory có kèm thêm/bớt warehouse                                                                                                                                           | "Không thể đổi kho khi đẩy tồn qua API — thao tác trên Seller Center."           | Payload đẩy tồn của ViShop không bao giờ gửi thay đổi `warehouseId`; nếu response sàn vẫn trả các mã này, coi là bất thường, ghi vào `GET /api/v1/inventory/push-logs` với `result: "anomaly"`, không âm thầm retry.                                                                                                                         |
| `inv_variant_limit_exceeded`       | `validation` → 422                  | `12052050`                                        | Product có > 100 SKU biến thể                                                                                                                                                        | "Sản phẩm vượt quá 100 biến thể — sàn không nhận đẩy tồn cho phần vượt ngưỡng."  | Validate ở bước ghép nối (M04), không đợi tới lúc đẩy tồn mới báo — cảnh báo ngay khi số biến thể của một `platformProductId` chạm 100, chặn ghép thêm SKU mới vào product đó thay vì để user phát hiện qua lỗi đẩy tồn.                                                                                                                     |
| `inv_push_failed_retry_exceeded`   | `conflict` → 409, `permanent: true` | tuỳ theo lỗi gốc kèm theo                         | Đẩy tồn fail liên tiếp > 3 lần                                                                                                                                                       | "Đẩy tồn cho {shop} thất bại 3 lần liên tiếp — kiểm tra kết nối shop."           | FR-05-17: dừng auto-retry sau lần 3, chuyển cảnh báo, không lặp vô hạn. `permanent: true` chính là tín hiệu cho hàng đợi ngừng thử lại.                                                                                                                                                                                                      |

> Mọi mã lỗi gốc của TikTok phải xuất hiện nguyên văn trong `GET /api/v1/inventory/push-logs` (FR-05-15) — đây là yêu cầu §6.5, không được dịch rồi bỏ mã gốc. Thân **problem+json** thì không mang mã gốc: quy ước đã chốt ở §0.3, mã sàn đi qua nhật ký chứ không qua thân lỗi HTTP.

---

### 5.3 Tuyến đẩy tồn: kho → gian hàng — FR-05-26

Tồn đẩy lên sàn là tổng khả dụng của các kho được cấu hình cấp tồn cho gian hàng đó. Quan hệ N–N, lưu ở `shop_warehouse_route` (ERD mục 8).

| Method | Path                            | FR                                | Ghi chú                                            |
| ------ | ------------------------------- | --------------------------------- | -------------------------------------------------- |
| GET    | `/api/v1/inventory/push-routes` | FR-05-26 (P0)                     | Toàn bộ tuyến của tenant kèm số liệu tổng hợp      |
| PUT    | `/api/v1/inventory/push-routes` | FR-05-12, FR-05-13, FR-05-18 (P0) | Thay toàn bộ tập tuyến. `Idempotency-Key` bắt buộc |

```jsonc
// GET /api/v1/inventory/push-routes
{
  "object": "list",
  "url": "/api/v1/inventory/push-routes",
  "data": [
    {
      "id": "01973f2a-...",
      "warehouseId": "01973a10-...",
      "shopId": "01973b41-...",
      "ratio": 60,
      "safetyStock": 5,
      "priority": true,
      "enabled": true,
    },
  ],
  "pageInfo": { "hasNextPage": false, "nextCursor": null },
  "allocation": [{ "warehouseId": "01973a10-...", "totalRatio": 100, "overAllocated": false }],
  "shopsWithoutSource": ["01973b45-..."],
}
```

`allocation` và `shopsWithoutSource` là dữ liệu suy ra nhưng do backend tính: đây là căn cứ từ chối lệnh ghi, client tính lại có thể ra kết quả khác.

`PUT` thay toàn bộ tập tuyến thay vì sửa từng dòng. Bất biến cần bảo vệ — tổng `ratio` mỗi kho ≤ 100 — trải trên nhiều dòng; sửa lẻ buộc đi qua trạng thái trung gian vi phạm ràng buộc và không chống được ghi đè khi hai người sửa đồng thời. Tuyến không có trong payload sẽ bị xoá.

| Tình huống                               | Kind → status      | `code`                          |
| ---------------------------------------- | ------------------ | ------------------------------- |
| Tổng `ratio` một kho > 100               | `validation` → 422 | `push_route_over_allocated`     |
| `warehouseId` hoặc `shopId` ngoài tenant | `not_found` → 404  | `push_route_target_not_found`   |
| Trùng cặp kho–gian hàng trong payload    | `validation` → 422 | `push_route_duplicated`         |
| Kho có `effectStatus = DISABLED`         | `validation` → 422 | `push_route_warehouse_disabled` |

`push_route_over_allocated` kèm `violations[]` chỉ rõ kho và tổng tỉ lệ hiện tại.

`PUT /api/v1/shops/{shopId}/inventory-push` giữ phạm vi mức gian hàng: bật/tắt đẩy tự động, tần suất, số lần thử lại. Tỉ lệ và tồn an toàn chuyển hoàn toàn sang tuyến; hai trường này bị loại khỏi payload của endpoint cũ.

**Thứ tự ưu tiên tồn an toàn — cấp nào có giá trị thì dừng ở đó:**

1. `shop_warehouse_route.safetyStock` — cặp (gian hàng, kho)
2. `shop_inventory_safety_stocks.safetyStock` — cặp (gian hàng, SKU)
3. `merchant_skus.safetyStock` — SKU, toàn tổ chức
4. `tenants.defaultSafetyStock` — mặc định của tổ chức

Tỉ lệ đẩy chỉ đọc từ `shop_warehouse_route.ratio`; `tenants.stockPushRatePercent` là giá trị khởi tạo cho tuyến mới, không tham gia vào lúc tính. Bảng xem trước (FR-05-19) và số đẩy thật phải gọi **chung một hàm tính** — hai đường tính riêng là cách chắc chắn nhất để hai con số lệch nhau.

Quyền: `inventory.push.config`.

---

## 6. FinanceProvider — M10 Chi phí & Lợi nhuận, M12 Đối soát

> **Câu hỏi #1 của Capability Matrix §9 đã có lời giải (06/09/2026).** TikTok **có** trả breakdown theo đơn: `GET /finance/202501/statements/{statement_id}/statement_transactions` trả từng giao dịch kèm `order_id`, bóc tách theo tên trường — 32 loại phí, 11 loại thuế, 29 khoản cước và khoản bổ sung. Không cần mô hình phân bổ ước tính theo kỳ. Bản 202309 cũ chỉ trả phí dạng cột phẳng nên **không dùng**.
>
> Tầng dữ liệu của M12 đã dựng xong — xem §6.1. Các endpoint so sánh dự kiến/thực nhận trong bảng dưới (`/settlements`, `/settlements/discrepancies`) dựng **trên** tầng đó và còn phải làm; chúng chưa tồn tại. `/reports/cashflow` (VIS-53) đã dựng xong, tại `GET /api/v1/reconciliation/reports/cashflow` — theo đúng quy ước `/reconciliation/*` mà `/settlements` thật (FR-12-01/02) đã dùng, không phải path phẳng ghi trong bảng dưới.

| Method | Path                                                | FR                              | Ghi chú                                                                                  |
| ------ | --------------------------------------------------- | ------------------------------- | ---------------------------------------------------------------------------------------- |
| GET    | `/api/v1/orders/{orderId}/fees`                     | FR-10-01 (P0)                   | Mỗi loại phí một dòng, không gộp; `feeType` là mã nguyên văn của sàn                     |
| GET    | `/api/v1/orders/{orderId}/profit`                   | FR-10-02 (P0)                   | Quyền vào cửa `orders.view`; hai quyền tiền gate từng trường, **không còn 403 cả cụm**   |
| GET    | `/api/v1/reports/profit/by-shop`                    | FR-10-06 (P0)                   |                                                                                          |
| GET    | `/api/v1/reports/profit/by-sku`                     | FR-10-07 (P0)                   | Kèm SL bán, tỉ lệ hoàn                                                                   |
| GET    | `/api/v1/reports/profit/by-day`                     | FR-10-08 (P0)                   | Biểu đồ xu hướng                                                                         |
| GET    | `/api/v1/reports/profit/export`                     | FR-10-09 (P0)                   |                                                                                          |
| GET    | `/api/v1/settlements`                               | FR-12-01, FR-12-02 (P0)         | Số dự kiến vs thực nhận vs chênh lệch                                                    |
| GET    | `/api/v1/settlements/discrepancies`                 | FR-12-04 (P0)                   | Sắp xếp theo mức lệch                                                                    |
| GET    | `/api/v1/settlements/{orderId}/lines`               | FR-12-05 (P0)                   | Dòng phí thực tế so với ước tính                                                         |
| GET    | `/api/v1/reconciliation/reports/cashflow` (đã dựng) | FR-12-06 (P0)                   | Tổng dự kiến, đã nhận, còn chờ theo kỳ; hỗ trợ kỳ theo tháng dương lịch hoặc theo payout |
| GET    | `/api/v1/settlements/export`                        | FR-12-07 (P0)                   |                                                                                          |
| POST   | `/api/v1/settlements/{id}/dispute-flag`             | BR-12-03 (P0, theo FR-12-02/03) | Chỉ thêm ghi chú/cờ tranh chấp — **không sửa số**                                        |

**Request/response rút gọn:**

```jsonc
// GET /api/v1/orders/{orderId}/profit → 200 (đủ quyền, đủ dữ liệu)
// Đơn combo: khách trả 550.000 tiền hàng + 30.000 cước; affiliate 50.000; giá vốn combo 270.000.
{
  "revenue": 580000,
  "costOfGoods": 270000,
  "fees": [
    { "feeType": "platform_commission_amount", "amount": 27500, "source": "platform_estimate" },
    { "feeType": "affiliate_commission_amount", "amount": 50000, "source": "platform_estimate" },
    { "feeType": "unexplained_difference", "amount": 15000, "source": "platform_estimate" }
  ],
  "totalFees": 92500,
  "profit": 217500,
  "profitMargin": 0.375,
  "dataStatus": "estimated",
  "feeSource": "platform_estimate"
}

// Thiếu giá vốn một SKU trong đơn → 200, profit là null, doanh thu và phí VẪN trả
{
  "revenue": 580000,
  "costOfGoods": null,
  "totalFees": 92500,
  "profit": null,
  "profitMargin": null,
  "dataStatus": "insufficient_data",
  "feeSource": "platform_estimate",
  "unavailableReason": "missing_cost_data",
  "missingCostSkuIds": ["01973..."]
}

// Thiếu quyền tiền → 200 (KHÔNG phải 403), cùng hình dạng, khác lý do
{
  "revenue": 580000,
  "costOfGoods": null,
  "totalFees": 92500,
  "profit": null,
  "profitMargin": null,
  "dataStatus": "estimated",
  "feeSource": "platform_estimate",
  "unavailableReason": "permission"
}
```

Thiếu giá vốn **không phải lỗi HTTP**: seller hỏi lợi nhuận của một đơn có thật, hệ thống trả lời được rằng chưa tính được và thiếu ở đâu. Trả 422 ở đây sẽ khiến FE phải bắt lỗi để hiển thị một màn bình thường.

**Thiếu quyền cũng không còn là lỗi HTTP (VIS-163).** Quyền vào cửa của `/profit` là `orders.view` — khối này thuộc màn đơn, payload không chứa gì ngoài chính đơn đó. `data.cost.view` và `data.profit.view` gate **từng trường** như `financialSummary` đã làm: thiếu quyền thì `costOfGoods`/`profit`/`profitMargin` là `null` kèm `unavailableReason: "permission"`, còn `revenue`, `fees[]` và `totalFees` vẫn trả — đúng ý FR-01-06 "chỉ thấy phần được phép". Nhánh `forbidden` cũ trên endpoint này đã bỏ.

`totalFees` lấy bằng **hiệu số** giữa tiền khách trả và số sàn trả (hoặc hứa trả), không phải tổng `fees[]`: sàn ghi dấu khác nhau giữa các khối nên cộng thành phần là phải tự đoán quy ước. `fees[]` chỉ **giải thích** tổng; phần chưa bóc tách được (cước, điều chỉnh) nằm ở dòng `unexplained_difference`, nên các dòng luôn cộng đúng bằng `totalFees`.

`dataStatus` bắt buộc trong mọi response profit/settlement — phân biệt `estimated` (trước payout), `reconciled` (sau payout, số thực) và `insufficient_data` (thiếu giá vốn — BR-10-02: **tuyệt đối không trả số sai**, `profit` là `null` chứ không phải `0` hay số ước lượng sai).

Giá trị thứ tư `partially_reconciled` **chỉ** xuất hiện ở ba báo cáo kỳ `/reports/profit/by-day|by-shop|by-sku` (và `compare`, vốn đọc lại ba báo cáo đó): một dòng gom nhiều đơn, trong đó có đơn đã có số quyết toán và có đơn chưa. Cấp đơn, `financialSummary` của đơn và các màn affiliate không bao giờ trả giá trị này. Quy tắc gom nhãn cho một dòng báo cáo:

| Tập đơn của dòng                           | `dataStatus`           |
| ------------------------------------------ | ---------------------- |
| Có đơn thiếu giá vốn (ưu tiên cao nhất)    | `insufficient_data`    |
| Mọi đơn có số quyết toán                   | `reconciled`           |
| Có đơn có, có đơn chưa                     | `partially_reconciled` |
| Không đơn nào có (kể cả dòng không có đơn) | `estimated`            |

`feeSource` nói ước tính đó dựa trên gì, ba mức theo thứ tự ưu tiên:

| `feeSource`         | Nguồn                                                    | `dataStatus` |
| ------------------- | -------------------------------------------------------- | ------------ |
| `settlement`        | `order_settlements` — số sàn đã thực trả                 | `reconciled` |
| `platform_estimate` | `order_settlement_estimates` — sàn tự khai               | `estimated`  |
| `rate_card`         | Biểu phí cứng 5% + 1,5% của ViShop, khi sàn chưa khai gì | `estimated`  |

Mức 2 dùng số sàn khai **dù đọc trước hay sau lúc đơn giao**. Sàn nói `est_shipping_cost_amount` có thể chưa đủ khi đơn chưa giao, nhưng phí và hoa hồng trong bản chụp đó vẫn là số của sàn, sát hơn hẳn biểu phí cứng; phần cước lệch được chấp nhận. Sweep số dự kiến lúc 02:00 hằng ngày đọc lại các giao dịch chưa quyết toán tạo trong 30 ngày (có hạn mức trang), nên cước thường được cập nhật trước khi đơn quyết toán.

**Ba báo cáo lãi theo kỳ tính từ đúng phép tính cấp đơn.** `/reports/profit/by-day`, `by-shop`, `by-sku` gom kết quả của cùng một hàm tính lãi cấp đơn mà `/orders/{orderId}/profit` dùng — cùng thác phí ở bảng trên, cùng giá vốn combo, cùng định nghĩa đơn huỷ (`status = CANCELLED` **hoặc** có `cancelledAt`). Doanh thu gốc luôn là `orders.totalAmount`; ở `by-sku` doanh thu và phí của một dòng hàng là phần chia của đơn theo giá dòng, nên tổng các dòng của một đơn đúng bằng đơn đó. Fee breakdown và danh sách đơn lỗ **chưa** đi qua phép tính này, vẫn dùng `rate_card` cho đơn chưa quyết toán.

Chi phí vận hành chung được cắt theo tỷ trọng **toàn tenant** (doanh thu hoặc số đơn không huỷ của kỳ, theo `allocationMethod`) trước khi rải theo trục, nên lọc gian hàng không đổi số của gian hàng còn lại trên màn.

`by-day` thêm trường, không đổi trường cũ:

| Trường                                                      | Nghĩa                                                                                                                                                   |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `items[].revenueForMargin`, `summary.totalRevenueForMargin` | Doanh thu của các đơn đủ giá vốn — đúng tập đơn tính `profit`. `profitMargin = profit / revenueForMargin`; `revenue` vẫn là doanh thu mọi đơn không huỷ |
| `items[].adCost`, `summary.totalAdCost`                     | Chi phí quảng cáo đã khai, rải theo ngày cùng cách với chi phí vận hành. `profit` đã trừ khoản này                                                      |

**Trạng thái và mã lỗi:**

| `code` / trạng thái                                                | `kind` → status   | Tình huống                                       | `detail` seller-facing                                                       | BR       |
| ------------------------------------------------------------------ | ----------------- | ------------------------------------------------ | ---------------------------------------------------------------------------- | -------- |
| `insufficient_data` (giá trị của `dataStatus`, **không phải lỗi**) | — → 200           | SKU trong đơn chưa có giá vốn hoặc chưa ghép nối | "Thiếu giá vốn — {n} SKU chưa cập nhật, xem chi tiết."                       | BR-10-02 |
| `settlement_edit_forbidden`                                        | `forbidden` → 403 | Cố sửa trực tiếp số liệu đối soát                | "Dữ liệu đối soát không sửa tay được — thêm ghi chú hoặc gắn cờ tranh chấp." | BR-12-03 |

---

### 6.1 Sổ quyết toán — M12 (đợt 1: đồng bộ và đọc)

Tầng dữ liệu thật, thay cho hai biểu phí cứng (`0.05` hoa hồng, `0.015` phí thanh toán) mà toàn bộ màn lợi nhuận đang dựa vào. Hai biểu phí đó tính một đơn affiliate và một đơn thường **giống hệt nhau**, trong khi hoa hồng nhà sáng tạo thường lớn hơn hoa hồng sàn — nên mọi con số lợi nhuận đang hiển thị là **sai**, không phải xấp xỉ.

| Method | Path                                              | Quyền                                    | Ghi chú                                                  |
| ------ | ------------------------------------------------- | ---------------------------------------- | -------------------------------------------------------- |
| GET    | `/api/v1/reconciliation/statements`               | `reconciliation.view`                    | Danh sách kỳ, mới nhất trước, phân trang cursor          |
| GET    | `/api/v1/reconciliation/statements/{statementId}` | `reconciliation.view`                    | Phần tổng của một kỳ                                     |
| GET    | `/api/v1/reconciliation/settlements`              | `reconciliation.view`                    | Giao dịch kèm bóc tách phí; lọc theo kỳ hoặc theo đơn    |
| POST   | `/api/v1/reconciliation/sync`                     | `reconciliation.view` + `shops.sync.run` | Xếp job kéo kỳ + giao dịch, trả `{ jobId, state }` → 202 |
| GET    | `/api/v1/reconciliation/sync?shopId=`             | `reconciliation.view`                    | Trạng thái job đồng bộ gần nhất của gian hàng            |

Ba điểm FE phải biết trước khi dựng màn:

**Một đơn có thể có nhiều giao dịch.** Tiền hàng là một giao dịch, tiền giữ lại (`RESERVE`) là một giao dịch khác, và các khoản điều chỉnh phát sinh sau (`CHARGE_BACK`, `PLATFORM_PENALTY`, `SHIPPING_FEE_ADJUSTMENT`…) là những giao dịch tiếp theo — có thể rơi vào kỳ khác hẳn. Đừng dựng màn theo giả định mỗi đơn một dòng.

**`components[]` khoá theo tên trường nguyên văn của sàn**, không phải nhóm phí do ViShop đặt: `platform_commission_amount`, `affiliate_commission_amount`, `vn_fix_infrastructure_fee`. Bộ mã **khác nhau theo thị trường** (`vn_fix_infrastructure_fee` là của Việt Nam, `gst_amount` thì không) và sàn thêm mã mới không báo trước. Màn hình phải hiện được mã lạ kèm số tiền, tuyệt đối không lọc bỏ mã chưa nhận ra — đó chính là lý do chọn sổ theo mã thay vì chín mươi cột.

**`currency` có thể null.** Null nghĩa là sàn không báo loại tiền cho bản ghi đó, và khi ấy **mọi số tiền cũng null** — ràng buộc này do cơ sở dữ liệu ép, không phải quy ước của tầng ứng dụng. Một số tiền không có đơn vị là số không đọc được, không phải số nhỏ hơn. FE hiển thị "—", không hiển thị 0.

```json
// GET /api/v1/reconciliation/settlements?statementId=… → 200
{
  "object": "list",
  "data": [
    {
      "externalTransactionId": "7412...",
      "transactionType": "ORDER",
      "orderId": "01973...",
      "externalOrderId": "576473917261451851",
      "currency": "VND",
      "revenueAmount": 450000,
      "feeTaxAmount": -32000,
      "shippingCostAmount": -15000,
      "settlementAmount": 403000,
      "components": [
        { "category": "FEE", "code": "platform_commission_amount", "amount": -20000 },
        { "category": "FEE", "code": "affiliate_commission_amount", "amount": -12000 },
        { "category": "TAX", "code": "vat_amount", "amount": -10000 }
      ]
    }
  ]
}
```

`settlementAmount = revenueAmount - shippingCostAmount - feeTaxAmount - adjustmentAmount` là **số học của sàn**; ViShop lưu nguyên từng số hạng. Tổng của kỳ cũng lấy nguyên từ sàn, không tự cộng lại từ các giao dịch bên dưới: khi hai bên lệch nhau thì chính chỗ lệch đó mới là kết quả cần tìm của việc đối soát, và một tổng do hệ thống tự tính sẽ không bao giờ lộ ra nó.

`transactionsSyncedAt = null` trên một kỳ nghĩa là **mới có phần tổng, chưa kéo chi tiết** — một trạng thái thật chứ không phải lỗi: danh sách kỳ là một lời gọi rẻ và chạy thường xuyên hơn hẳn thao tác kéo sâu.

**`POST /sync` chạy nền (breaking so với bản trả `200` + kết quả).** Endpoint xếp một job rồi trả ngay `202 { jobId, state }`; worker chạy tối đa 10 vòng khi còn việc. FE poll `GET /sync?shopId=` (2 giây trong 30 giây đầu, sau đó 5 giây; dừng khi `completed`/`failed`/404 hoặc sau 10 phút) và đọc thông báo từ `result` — cùng shape kết quả cũ (`statementsCreated`, `settlementsCreated`, `ordersLinked`, `amountsUnconverted`, `hasMoreWork`…), cộng dồn qua các vòng. `hasMoreWork = true` ở `result` nghĩa là đã hết 10 vòng mà còn việc, bấm lại cùng cửa sổ. **Mỗi gian hàng một job:** job đang chờ/chạy thì yêu cầu mới nhập vào đó, cửa sổ của job đang chạy được giữ. Cửa sổ ngược vẫn trả `422 window_inverted` ngay ở request. Scheduler còn tự kéo 35 ngày gần nhất lúc 07:30 hằng ngày.

```jsonc
// POST /api/v1/reconciliation/sync → 202
{ "jobId": "settlement-sync__01973...", "state": "queued" }

// GET /api/v1/reconciliation/sync?shopId=01973... → 200
{
  "jobId": "settlement-sync__01973...",
  "shopId": "01973...",
  "state": "running", // queued | running | completed | failed
  "progress": { "round": 2 },
  "result": null, // chỉ có khi completed
  "enqueuedAt": "2026-09-17T08:00:00.000Z",
  "finishedAt": null
}
```

Giao dịch về trước khi đơn gốc kịp đồng bộ vẫn lưu được, `orderId` để null và `externalOrderId` giữ liên kết; lần chạy sau nối lại và trả về ở `ordersLinked`.

**Mã lỗi riêng:** `statement_not_found` → 404; `settlement_sync_job_not_found` → 404 (gian hàng chưa có job, hoặc job đã hết hạn lưu: 24 giờ sau khi xong, 7 ngày sau khi lỗi); `settlement_sync_next_page_token_not_advancing` → 503 (lỗi nội bộ của sweep, không phải lỗi người gọi). Gian hàng ngoài phạm vi của người gọi → 404, không phải 403.

---

### 6.2 Chênh lệch đối soát theo đơn — M12 (đợt 2: vế "dự kiến")

| Method | Path                                     | Quyền                                    | Ghi chú                                                 |
| ------ | ---------------------------------------- | ---------------------------------------- | ------------------------------------------------------- |
| GET    | `/api/v1/reconciliation/order-variances` | `reconciliation.view`                    | Số dự kiến, số thực trả, mức lệch — phân trang số trang |
| POST   | `/api/v1/reconciliation/estimates/sync`  | `reconciliation.view` + `shops.sync.run` | Chụp số dự kiến của sàn, chạy nền, trả `jobId` → 202    |

**`expectedAmount` là số của SÀN, không phải số ViShop tự tính.** Nguồn là `est_settlement_amount` từ `GET /finance/202507/orders/unsettled` — đã trừ hoa hồng nhà sáng tạo, cước sàn trả hãng vận chuyển và phí theo thị trường. Trước đó trường này lấy từ biểu phí cứng 5% + 1,5%, vốn cho một đơn affiliate và một đơn thường **cùng một con số**; phép so vì thế đổi từ "ViShop đoán vs sàn trả" thành "sàn hứa vs sàn trả".

Đây là số **sàn tự khai**, không phải cam kết thanh toán: sàn còn sửa cho tới lúc quyết toán, và với đơn **chưa giao** thì phần cước bên trong chưa đủ — chính sàn nói vậy.

**`expectedAmount` là tổng nhiều giao dịch**, cùng lý do với `settledAmount`: một đơn có một dòng đơn hàng cộng các dòng điều chỉnh. Khoản sàn trừ **không thuộc đơn nào** (phạt, phí quảng cáo, phí kho FBT) cũng được lưu nhưng không xuất hiện ở danh sách này.

**`expectedCapturedAt` là lần cuối đọc được số đó**, không phải mốc chốt cứng. Sàn xoá một giao dịch khỏi nguồn ngay khi quyết toán xong, nên mốc này luôn nằm trước payout; đọc càng muộn thì phần cước càng đủ.

`expectedAmount = null` (`varianceReason = no_expected_snapshot`) nghĩa là đơn đã quyết toán trước khi ViShop kịp đọc — **không backfill được**, sàn không còn trả giao dịch đó nữa.

Số dự kiến được so **nguyên như sàn khai**, không xét bản chụp đọc trước hay sau lúc đơn giao — cùng cách báo cáo lợi nhuận dùng nó. Sweep lúc 02:00 hằng ngày đọc lại các giao dịch chưa quyết toán tạo trong 30 ngày (có hạn mức trang), nên cước chốt sau khi giao thường ghi đè được bản chụp cũ trước khi đơn quyết toán — không bảo đảm.

Cảnh báo `orders.reconciliation_mismatch` dùng **đúng** phép phân loại của bảng này, kể cả ngưỡng riêng theo gian hàng — đơn bảng gọi `settled` thì không bao giờ bị cảnh báo. Lượt quét hằng ngày chỉ xét gian hàng đang bật cảnh báo (dòng riêng của gian hàng thắng dòng cấp tenant), tối đa 50 đơn lệch nhất mỗi gian hàng, và nối tiếp đúng chỗ lượt trước dừng — một lần quyết toán chỉ được xét một lần, lượt bị lỡ được lượt sau quét bù.

**`GET /reports/cashflow`** có thêm `untrackedOrderCount`: đơn trong kỳ chưa có số dự kiến lẫn quyết toán. Các đơn này không tính vào `pendingOrderCount` và không làm tổng tiền thành null.

**`GET /reports/monthly-revenue`**: `platformDeductions = Σ(revenueAmount − settlementAmount)` trên giao dịch đủ hai số, không còn cộng ba cột phí (dấu phí sàn không nhất quán). `grossRevenue − platformDeductions = netSettled` khi `incompleteTransactionCount = 0`.

`POST /estimates/sync` **không nhận cửa sổ thời gian**: cửa sổ do client chọn là cách làm mất vĩnh viễn số dự kiến của những đơn nằm ngoài nó. Server tự tính cửa sổ từ mốc phủ sóng của gian hàng. Chạy lại không nhân đôi dữ liệu — lượt sau ghi đè lên đúng giao dịch đó.

```jsonc
// POST /api/v1/reconciliation/estimates/sync  { "shopId": "01973..." } → 202
{ "jobId": "settlement-estimate-sync__01973...__5813712" }
```

**Mã lỗi riêng:** không có. Dùng lại `validation` / `forbidden` / `not_found` đã có.

---

## 7. Báo cáo theo lịch — M13

Không phải adapter: toàn bộ nội bộ, kênh gửi là email như M14.

| Method | Path                                            | FR            | Ghi chú                                                      |
| ------ | ----------------------------------------------- | ------------- | ------------------------------------------------------------ |
| GET    | `/api/v1/report-schedules`                      | FR-13-12 (P1) | Danh sách lịch, kèm lần gửi gần nhất và lần kế tiếp          |
| POST   | `/api/v1/report-schedules`                      | FR-13-12 (P1) | Loại báo cáo, tần suất, giờ gửi, người nhận, bộ lọc kèm theo |
| PATCH  | `/api/v1/report-schedules/{scheduleId}`         | FR-13-12 (P1) | Sửa lịch, bật/tắt                                            |
| GET    | `/api/v1/report-schedules/{scheduleId}/runs`    | FR-13-13 (P1) | Lịch sử gửi: thời điểm, kết quả, lý do lỗi                   |
| POST   | `/api/v1/report-schedules/{scheduleId}/run-now` | FR-13-12 (P1) | Gửi thử ngay, không đổi lịch                                 |

```json
// POST /api/v1/report-schedules → 201
{
  "reportKind": "sales | inventory | orders | profit",
  "frequency": "daily | weekly | monthly",
  "sendAtLocal": "07:30",
  "recipients": ["ke-toan@example.com"],
  "filters": { "shopIds": ["01973..."], "period": "last_7_days" }
}
```

Hai ràng buộc:

1. **`filters` dùng đúng cấu trúc bộ lọc của màn báo cáo tương ứng.** Không định nghĩa một ngôn ngữ lọc thứ hai cho email — người dùng phải nhận được đúng thứ họ thấy trên màn hình.
2. **Gửi lỗi phải hiện lý do**, không im lặng bỏ qua rồi để kế toán tự phát hiện tháng sau là chưa từng nhận báo cáo nào. `runs[].errorReason` giữ nguyên văn lỗi SMTP.

`sendAtLocal` là giờ UTC+7 theo quy ước §0.4 — lưu UTC, hiển thị và nhập theo giờ Việt Nam.

`POST /api/v1/report-schedules/{scheduleId}/run-now` gửi kèm `Idempotency-Key`: bấm hai lần không được gửi hai email cho kế toán.

---

## 8. Trợ lý ViAI — M13, M14

Không phải adapter: trợ lý đọc lại dữ liệu ViShop đã có, không gọi thêm endpoint nào của sàn. Bối cảnh đầy đủ ở [vishop-ai-automation-roadmap.md](./vishop-ai-automation-roadmap.md).

| Method | Path                      | FR            | Ghi chú                                                                   |
| ------ | ------------------------- | ------------- | ------------------------------------------------------------------------- |
| GET    | `/api/v1/assistant/tools` | FR-13-15 (P0) | Danh mục việc trợ lý làm được: `name`, `kind`, `description`, câu hỏi mẫu |
| POST   | `/api/v1/assistant/ask`   | FR-13-14 (P1) | Câu hỏi vào, một kết quả ra; `toolName` là `null` khi không khớp việc nào |
| GET    | `/api/v1/assistant/logs`  | FR-14-10 (P1) | Nhật ký: câu hỏi, tool đã gọi, kết quả, đề xuất, người xác nhận           |

```json
// POST /api/v1/assistant/ask → 200
{
  "toolName": "prepare_pick_wave",
  "kind": "action",
  "headline": "12 kiện đang chờ gom sóng",
  "facts": [
    { "label": "Kho nhiều kiện chờ nhất", "value": "Kho Hà Nội" },
    { "label": "Kiện chưa ghép SKU kho", "value": "4", "tone": "warning" }
  ],
  "source": "Kiện chưa nằm trong sóng nào, gom theo kho bán hàng đang kích hoạt.",
  "link": { "href": "/picking", "label": "Mở màn gom sóng" },
  "proposal": {
    "summary": "Mở một sóng nhặt tại Kho Hà Nội cho 8 kiện, tương đương 14 sản phẩm.",
    "effect": "Sóng mới khoá số kiện này lại cho một người nhặt. Kiện còn dòng chưa ghép SKU kho không vào được sóng.",
    "runLabel": "Mở màn gom sóng để xác nhận",
    "affectedCount": 8,
    "notExecutedNotice": "Hệ thống chưa thực hiện bất kỳ thay đổi nào — tác vụ chỉ chạy khi bạn xác nhận tại màn thực hiện tương ứng."
  }
}
```

Giá trị của `toolName` (`prepare_pick_wave`, …) giữ snake_case — nó là **định danh tool** gửi cho mô hình, không phải tên field.

Sáu ràng buộc, cả sáu đều chặn phát hành:

1. **`source` là trường bắt buộc.** Không có nguồn thì không được trả số (BR-13-06).
2. **`link` là trường bắt buộc.** Trả lời xong mà người dùng vẫn phải tự đi tìm màn xử lý thì chưa giải quyết được gì.
3. **`proposal` không bao giờ kèm việc đã chạy.** Endpoint này không ghi dữ liệu, kể cả với `kind: "action"` (BR-14-04). Việc thật chạy qua endpoint của module tương ứng, sau khi người dùng xác nhận ở màn đó.
4. **Không trả về tên, số điện thoại hay địa chỉ người mua** (BR-14-05). `facts` chứa số đếm và mã đơn, không chứa dữ liệu cá nhân.
5. **`proposal.affectedCount` tách bạch khỏi câu văn của `summary`** (FR-14-08) — số đối tượng bị ảnh hưởng luôn là một trường riêng, không chỉ nhắc trong câu, để giao diện có thể làm nổi bật mà không cần phân tích câu chữ.
6. **`proposal.notExecutedNotice` là câu cố định do máy chủ tự gắn** (FR-14-08, BR-14-04) — giống nhau ở mọi tool `action`, không do từng tool tự soạn, xác nhận rõ chưa có thay đổi nào được thực hiện.

`toolName` giữ nguyên sau khi phát hành: nó là hợp đồng với mô hình khi nối function calling, và là khoá đối chiếu của nhật ký cũ.

Câu hỏi không khớp tool nào **không phải lỗi** — trả 200 với `toolName: null` và một `headline` nói rõ trợ lý chưa làm được việc đó, kèm `link` tới danh mục việc làm được. Trả 422 ở đây biến một câu hỏi hợp lệ thành lỗi hệ thống trong mắt người dùng.

Gọi mô hình ngôn ngữ bên ngoài có thể chạm trần `HTTP_REQUEST_TIMEOUT_MS` 30 giây (§0.4). BE đặt timeout riêng cho lệnh gọi mô hình **thấp hơn** ngưỡng đó và trả câu trả lời rút gọn khi hết giờ, thay vì để cả request rơi vào `504 request_timeout` — người dùng nhận `504` sẽ không biết là trợ lý chậm hay ViShop hỏng.

---

## 9. Nền tảng nội bộ — M00, M01

Hai nhóm endpoint dưới đây không đi qua ChannelAdapter: chúng phục vụ chính ViShop, không gọi sang sàn.

### 9.1 Nhật ký thao tác — FR-00-04

```js
GET /api/v1/audit-logs?userId=&action=&resource=&from=&to=&limit=&cursor=
```

```json
{
  "object": "list",
  "url": "/api/v1/audit-logs",
  "data": [
    {
      "id": "0199…",
      "actorId": "0199…",
      "actorName": "Trần Thị Vận Hành",
      "action": "Sửa giá vốn SKU",
      "target": "merchant_sku:SKU-000123",
      "ip": "203.0.113.5",
      "userAgent": "Mozilla/5.0",
      "metadata": {},
      "createdAt": "2026-08-11T02:14:00.000Z"
    }
  ],
  "pageInfo": { "hasNextPage": true, "nextCursor": "MjAyNi0wOC0xMVQwMjoxNDowMFo6MDE5OQ" }
}
```

**`actorName` là trường bắt buộc, không phải tiện ích.** Màn nhật ký hiển thị cột "Người thao tác" bằng tên người; trả mỗi `actorId` dạng UUID là biến màn hình thành danh sách chuỗi không đọc được, tức là hỏng đúng mục đích tồn tại của FR-00-04 — truy ra **ai** làm. BE join sang `users`; FE không có cách nào đổi UUID thành tên trong một lần gọi.

`target` là chuỗi `<loại đối tượng>:<mã>` chứ không tách hai trường: mọi màn đọc nó đều chỉ để hiển thị và tìm kiếm, tách ra chỉ tăng số cột mà không ai lọc riêng theo loại.

`metadata` **không được chứa** token, `app_secret`, hay dữ liệu người mua nguyên văn. Đây là ràng buộc của FR-00-04, và cách an toàn là danh sách trắng các khoá được phép — bóc vài khoá đã biết rồi cho qua phần còn lại là fail-open, chỉ cần một người thêm trường vào payload là rò rỉ và nằm lại 12 tháng.

Bộ lọc chạy ở server, không ở client: bảng này lớn dần theo mọi thao tác của mọi người dùng.

### 9.2 Cờ tính năng — FR-00-07

```js
GET  /api/v1/feature-flags?limit=20&cursor=...                 # tenant hiện tại
GET  /api/v1/admin/feature-flags?tenantId=...&limit=20&cursor=...
PUT  /api/v1/admin/feature-flags/{key}
```

Hai endpoint GET đều theo contract cursor ở §0.2. Danh sách luôn gồm toàn bộ catalog đã phát hành;
flag chưa có bản ghi override của tenant được trả về với `enabled=false`. Cursor là opaque key cursor,
client phải truyền lại nguyên văn và không được tự suy ra từ `key`.

Endpoint tenant lấy tenant từ access JWT đã xác minh (`tenantId`), không nhận tenant override từ query và chỉ trả
`key`, `name`, `description`, `enabled`. `updatedAt`/`updatedBy` là metadata vận hành nội bộ, chỉ có
trong endpoint admin. Endpoint admin yêu cầu platform role `ADMIN`; `PUT` chỉ chấp nhận key có trong
`FEATURE_FLAG_CATALOG`, key sai trả `feature_flag_key_unknown`, tenant không tồn tại trả
`tenant_not_found`.

```json
{
  "object": "list",
  "url": "/api/v1/admin/feature-flags",
  "data": [
    {
      "key": "ai_assistant",
      "name": "Trợ lý AI",
      "description": "Hỏi đáp số liệu và đề xuất hành động — M14.",
      "enabled": false,
      "updatedAt": "2026-08-08T03:00:00.000Z",
      "updatedBy": "Trần Thị Vận Hành"
    }
  ],
  "pageInfo": { "hasNextPage": false, "nextCursor": null }
}
```

Cờ này chỉ trả lời **"ViShop đã mở tính năng cho khách hàng đó chưa"**. Việc gian hàng có đủ scope để tính năng chạy được hay không là cơ chế riêng (FR-02-12) — hai cái phải cùng đúng thì tính năng mới hiện.

### 9.3 Kéo lại webhook TikTok bị treo (Manual Replay) — FR-00-09

```js
GET  /api/v1/admin/tiktok/webhook-events?status={status}&limit=20&cursor=...
POST /api/v1/admin/tiktok/webhook-events/{id}/replay
```

`GET` là mảng cursor-phân trang (`ListResponseDto`), lọc theo `status` (`PENDING | ENQUEUED |
PROCESSED | UNHANDLED | DEAD_LETTER` — `UNHANDLED`/`DEAD_LETTER` là hai giá trị ops thường tìm để
replay). Response
**không có field `payload`** — payload chứa dữ liệu người mua (BR-14-05), không được rời server qua
mặt này.

```json
{
  "id": "0199…",
  "tenantId": "0199…",
  "shopId": "0199…",
  "externalShopId": "7495",
  "eventType": "ORDER_STATUS_CHANGE",
  "rawType": 1,
  "status": "DEAD_LETTER",
  "eventTimestamp": "2026-08-14T02:00:00.000Z",
  "receivedAt": "2026-08-14T02:00:01.000Z",
  "processedAt": null
}
```

`POST .../{id}/replay` xếp lại đúng payload gốc đã lưu (`200 { webhookEventId, queuedJobId }`) —
không nhận tham số tenant nào, luôn tự xác định lại shop/tenant từ chính bản ghi
(`externalShopId`), nên không thể bị điều hướng sang tenant khác. `404
tiktok_webhook_event_not_found` nếu id không tồn tại; `422 tiktok_webhook_event_not_replayable` nếu
loại sự kiện chưa từng được định tuyến, hoặc shop không còn xác định được.

**Ràng buộc quan trọng nhất của mục này nằm ở mã lỗi.** Khi một tính năng bị khoá, response phải nói rõ khoá vì lý do nào:

| Tình huống              | Status | `code`                              |
| ----------------------- | ------ | ----------------------------------- |
| Chưa mở beta cho tenant | 403    | `feature_not_enabled`               |
| Gian hàng thiếu scope   | 403    | mã **khác**, do FR-02-12 định nghĩa |

Hai tình huống này nhìn giống hệt nhau với người dùng nhưng dẫn tới hai lối thoát trái ngược: thiếu scope thì uỷ quyền lại là xong, chưa mở beta thì uỷ quyền lại bao nhiêu lần cũng vô ích. FE khoá i18n theo `code`, nên **dùng chung một mã cho cả hai là làm FE mất khả năng phân biệt vĩnh viễn** — và hậu quả cụ thể là CS hướng dẫn khách uỷ quyền lại gian hàng trong khi sàn không cấm gì.

Mọi thao tác bật/tắt cờ ghi vào `audit_logs` (FR-00-04).

### 9.3 Tổ chức, thành viên & phân quyền — M01 (FR-01-02, FR-01-12..14)

#### Danh sách, chọn và tạo tổ chức

Contract bootstrap hiện tại của Java IAM:

| Method | Path | Quyền/ngữ cảnh | Request / response |
| --- | --- | --- | --- |
| GET | `/api/v1/tenants` | Access cookie; cho phép tenant null | Mảng tenant đang hoạt động mà user có membership; hiện chưa phân trang |
| POST | `/api/v1/auth/switch-tenant` | Auth cookies và membership đích | `{tenantId}` → thay cookie HttpOnly, JSON chỉ chứa user/tenant grants |
| POST | `/api/v1/tenants` | Access cookie; cho phép tenant null | `{code,name}` → tenant mới; user trở thành OWNER |

```http
POST /api/v1/auth/switch-tenant
Cookie: vishop_access_token=<HttpOnly>; vishop_refresh_token=<HttpOnly>
X-Requested-With: XMLHttpRequest
Content-Type: application/json

{ "tenantId": "0199a222-2222-7000-8000-000000000002" }
```

```json
{
  "user": {
    "id": "0199a111-1111-7000-8000-000000000001",
    "email": "owner@example.com",
    "fullName": "Owner",
    "systemRole": "USER",
    "status": "ACTIVE",
    "createdAt": "2026-10-25T00:00:00Z"
  },
  "tenantId": "0199a222-2222-7000-8000-000000000002",
  "roles": ["OWNER"],
  "permissions": ["tenant.view", "tenant.manage"]
}
```

`permissions` trong ví dụ được rút gọn. JWT chỉ được ghi qua `Set-Cookie` với HttpOnly,
không trả trong JSON. Frontend dùng `credentials: 'include'` và `X-Requested-With: XMLHttpRequest`
cho request ghi. Các tab cùng trình duyệt dùng chung cookie và tenant hiện tại.
Membership/tenant không hoạt động trả 403 theo kiểm tra quyền IAM; token không hợp lệ hoặc
hai token khác user trả 401. Không dùng lỗi `tenant_ambiguous` hay contract switch session cũ.

Login ưu tiên tenant ghi nhớ còn hợp lệ, sau đó tự chọn nếu chỉ có một active membership;
còn lại trả tenant null.
Gateway cho phép các API identity/bootstrap với tenant null, nhưng chặn business API thiếu
tenant bằng 403. FE hiển thị picker, gọi switch và chuyển màn sau khi cookie được thay thành công.
Tạo tenant và đổi tenant là hai thao tác riêng; tạo tenant không đổi JWT đang giữ.

Danh sách tenant hiện trả các field `id`, `code`, `name`, `status`, `createdAt` theo
`TenantResponseDto`. Các field `isActive`, `slug`, `logoUrl`, `baseCurrency` và pagination
trong thiết kế sản phẩm chưa thuộc contract bootstrap Java hiện tại.

#### Thành viên Tenant (`/tenants/members`)

- `GET /api/v1/tenants/members` — Danh sách thành viên trong tenant (`limit`, `cursor`).
- `POST /api/v1/tenants/members/invitations` — Mời thành viên mới (OWNER/ADMIN). Body: `{ email, role: "ADMIN" | "MEMBER", roleId? }`.
- `GET /api/v1/tenants/members/invitations` — Danh sách lời mời (`status`, `limit`, `cursor`).
- `POST /api/v1/tenants/members/invitations/accept` — Chấp nhận lời mời bằng token. Body: `{ token }`.
- `POST /api/v1/tenants/members/invitations/{id}/revoke` — Hủy lời mời PENDING (OWNER/ADMIN).
- `PATCH /api/v1/tenants/members/{id}/role` — Gán/đổi vai trò kinh doanh (`roleId`) cho thành viên.
- Reset password bởi OWNER là yêu cầu sản phẩm chưa triển khai trong Java IAM; không mô tả endpoint nguồn như API đang hoạt động, và không cam kết thu hồi JWT trước hạn.
- `POST /api/v1/tenants/members/{id}/deactivate` — OWNER vô hiệu membership trong tenant hiện hành; không ban global User.
- `POST /api/v1/tenants/members/{id}/reactivate` — OWNER khôi phục membership cùng vai trò đã gán.
- `DELETE /api/v1/tenants/members/{id}` — OWNER xoá mềm membership, giữ lịch sử và giải phóng seat.

Lifecycle membership có phạm vi tenant, còn trạng thái tài khoản có phạm vi global user.
IAM kiểm tra trạng thái và quyền hiện tại khi cấp token và trong use case nghiệp vụ.
JWT đã cấp ở gateway/downstream vẫn có hiệu lực đến expiry; không có session để revoke.
Forced password change và reset password bởi OWNER cần thiết kế/triển khai riêng trước
khi áp dụng các acceptance criteria tương ứng của đặc tả nguồn.

#### Danh mục quyền (`/permissions/catalog`)

```js
GET / api / v1 / permissions / catalog;
```

Trả về danh mục quyền đầy đủ, nhóm theo module. Nguồn sự thật là hằng số trong `libs/contracts`; xem [vishop-permission-catalog.md](./vishop-permission-catalog.md).

```jsonc
{
  "object": "list",
  "url": "/api/v1/permissions/catalog",
  "data": [
    {
      "key": "orders",
      "label": "Đơn hàng & kiện",
      "moduleCode": "M06",
      "viewKey": "orders.view",
      "permissions": [
        { "key": "orders.view", "label": "Xem đơn hàng" },
        {
          "key": "orders.package.ship",
          "label": "Bàn giao vận chuyển (RTS)",
          "hint": "Ghi sang sàn, không hoàn tác được",
          "dangerous": true,
        },
      ],
    },
  ],
  "pageInfo": { "hasNextPage": false, "nextCursor": null },
}
```

Chỉ yêu cầu phiên đăng nhập hợp lệ, không cần quyền riêng: giao diện cần danh mục để hiển thị cả màn chỉ đọc.

#### Vai trò & Phân quyền (`/tenants/roles`)

| Method | Path                                         | Quyền          | Ghi chú                                           |
| ------ | -------------------------------------------- | -------------- | ------------------------------------------------- |
| GET    | `/api/v1/tenants/roles`                      | `members.view` | Vai trò dựng sẵn và tuỳ chỉnh                     |
| GET    | `/api/v1/tenants/roles/{roleId}`             | `members.view` | Chi tiết kèm `permissionKeys[]`                   |
| POST   | `/api/v1/tenants/roles`                      | `roles.manage` | Body `{ name, permissionKeys: string[] }`         |
| PUT    | `/api/v1/tenants/roles/{roleId}/permissions` | `roles.manage` | Body `{ permissionKeys: string[] }`, thay toàn bộ |
| DELETE | `/api/v1/tenants/roles/{roleId}`             | `roles.manage` | Chỉ vai trò tuỳ chỉnh chưa gán cho thành viên nào |

Ba ràng buộc ở tầng ứng dụng:

1. `permissionKeys[]` chỉ nhận khoá có trong danh mục — khoá lạ trả `422` mã `permission_key_unknown`.
2. Vai trò tuỳ chỉnh không được chứa `roles.manage`: khoá này cho phép tự cấp mọi quyền còn lại. Vi phạm trả `422` mã `permission_escalation_denied`.
3. Mỗi khoá hành động yêu cầu khoá `view` cùng nhóm. Thiếu → `422` mã `permission_view_required`.

Vai trò dựng sẵn (`builtinKey` khác `null`) chỉ đọc; sửa trả `409` mã `role_builtin_readonly`. Response vẫn kèm `isBuiltin` như trường tính toán để client khỏi tự suy.

Vai trò `builtinKey = "OWNER"` không gán được cho thành viên nào: `PATCH /tenants/members/{id}/role` và lời mời mang `roleId` đó đều trả `409` mã `role_owner_not_assignable`. Quyền của chủ tài khoản đến từ `TenantMember.role`, nên vai trò này không lưu grant nào — gán cho người khác sẽ tạo ra một vai trò mang tên "Chủ tài khoản" mà không cấp gì. Khi đọc, API trả `permissionKeys` của nó là toàn bộ danh mục.

#### Phạm vi gian hàng (`/tenants/members/{memberId}/shop-scope`)

| Method | Path                                            | Quyền            | Ghi chú                              |
| ------ | ----------------------------------------------- | ---------------- | ------------------------------------ |
| PUT    | `/api/v1/tenants/members/{memberId}/shop-scope` | `members.manage` | Body `{ shopIds: string[] \| null }` |

`null` nghĩa là toàn bộ gian hàng của tenant — trạng thái mặc định khi thành viên chưa có bản ghi `user_shop_scope` nào (FR-01-05, ERD mục 8.3).

---

## 10. Dashboard, hàng đợi việc, báo cáo và cấu hình — M00, M13

Các màn tổng hợp và cấu hình, bổ sung sau lượt đối chiếu cây route với hợp đồng.

Toàn bộ là lệnh đọc trừ `PATCH /settings`: không cần `Idempotency-Key`, không phát sinh sự kiện outbox, dữ liệu tổng hợp từ các mục trên.

### 10.1 Dashboard và Việc hôm nay — FR-13-01, FR-13-02, FR-13-11

| Method | Path                 | FR            | Ghi chú                                                    |
| ------ | -------------------- | ------------- | ---------------------------------------------------------- |
| GET    | `/api/v1/dashboard`  | FR-13-01 (P0) | Số liệu hôm nay + so cùng kỳ                               |
| GET    | `/api/v1/work-queue` | FR-13-11 (P0) | Hàng đợi theo **việc phải làm**, không theo trạng thái đơn |

```jsonc
// GET /api/v1/dashboard?shopIds=...
{
  "period": { "from": "2026-08-12T00:00:00+07:00", "to": "2026-08-12T23:59:59+07:00" },
  "revenue": { "value": 48250000, "changePercent": 12.4 },
  "orderCount": { "value": 214, "changePercent": -3.1 },
  "pendingRtsCount": 37,
  "missingStockCount": 5,
  // Lược khỏi response khi người gọi thiếu `data.profit.view` — FR-01-06, xem §0.2.
  "estimatedProfit": { "value": 9120000, "changePercent": 8.0 },
  // Khối "Cần xử lý ngay" — mỗi mục có đường dẫn để bấm thẳng vào việc.
  "actionItems": [
    { "kind": "pending_rts", "count": 37, "href": "/orders/pending-rts" },
    { "kind": "unmapped_sku", "count": 12, "href": "/sku-mapping" },
    { "kind": "shop_reconnect", "count": 1, "href": "/shops" },
  ],
}
```

`changePercent` trả `null` khi kỳ trước không có dữ liệu, không trả `0`: không có dữ liệu và không tăng trưởng là hai trạng thái khác nhau.

`GET /api/v1/work-queue` trả danh sách việc đã sắp theo hạn, mỗi việc kèm `dueAt` và `blockedBy` (bước chặn theo FR-06-29), dùng cursor như mọi collection. UI phải theo `pageInfo.hasNextPage` thay vì hiểu một trang là toàn bộ hàng đợi.

### 10.2 Đơn cần can thiệp — FR-06-20, FR-06-21

| Method | Path                                   | FR            | Ghi chú                                          |
| ------ | -------------------------------------- | ------------- | ------------------------------------------------ |
| GET    | `/api/v1/orders/missing-stock`         | FR-06-20 (P0) | Đơn kèm **SKU nào thiếu và thiếu bao nhiêu**     |
| GET    | `/api/v1/orders/missing-stock/summary` | FR-06-20 (P0) | Ba thẻ của màn Thiếu tồn                         |
| GET    | `/api/v1/orders/anomalies`             | FR-06-21 (P0) | Đơn lỗi đồng bộ / lệch dữ liệu / thiếu thông tin |
| GET    | `/api/v1/orders/anomalies/summary`     | FR-06-21 (P0) | Tổng số đơn bất thường và các nhóm nguyên nhân   |
| POST   | `/api/v1/orders/{orderId}/resync`      | FR-06-21 (P0) | Hành động khắc phục: kéo lại đơn từ sàn          |

Tách khỏi `GET /api/v1/orders` vì response cần phần thiếu hụt tính theo tồn khả dụng tại thời điểm gọi.

**Nguyên tắc chung của cả bốn endpoint: không lưu cột trạng thái nào.** Mọi dòng đều suy ra tại thời
điểm gọi, nên đơn tự rời danh sách khi nguyên nhân hết — không cần job dọn, và không có khoảng thời
gian nào danh sách sai. Vì vậy **không có API "đánh dấu đã xử lý"**: một nút như thế sẽ đóng được
dòng mà nguyên nhân vẫn còn.

#### `GET /api/v1/orders/missing-stock`

**Auth:** Bearer access JWT với tenant context đã ký; quyền và shop scope là yêu cầu nghiệp vụ cần được kiểm tra phía server khi module tương ứng được triển khai.
**Query:** `limit?` (1–100, mặc định 20) · `cursor?` · `shopId?: uuid` · `reason?: insufficient_stock|unmapped_sku|sku_deleted` · `search?: string` (≤200)

`reason` thu hẹp **danh sách đơn**, không cắt bớt `shortages[]` của đơn được giữ lại.

`search` là ô tìm: mã đơn sàn hoặc tên người mua, khớp một phần, không phân biệt hoa thường; `%` và
`_` được hiểu là ký tự thường. Không có nhánh số điện thoại. Áp cho cả `data` lẫn `pageInfo.total`;
thẻ `/summary` không nhận `search`.

Liệt kê đơn đang chờ xử lý (`AWAITING_SHIPMENT`, `PARTIALLY_SHIPPING`) mà tồn khả dụng không đủ. Dòng
**chưa ghép nối SKU cũng tính là thiếu** (`docs/spec/30-don-hang.md`), vì không ghép nối thì không đọc
được tồn để khẳng định là đủ. Thiếu tồn **không chặn RTS và không chặn in tem** (BR-06-03).

```jsonc
{
  "object": "list",
  "url": "/api/v1/orders/missing-stock",
  "data": [
    {
      "orderId": "01973c...",
      "externalOrderId": "5776...",
      "shopId": "01973b...",
      "shopName": "Shop A",
      "buyerName": "Nguyễn Văn A",
      "status": "AWAITING_SHIPMENT",
      "orderedAt": "2026-09-01T08:00:00.000Z",
      "fulfillmentDueAt": "2026-09-03T08:00:00.000Z",
      // Số kiện chưa huỷ — nút "Tạm giữ" của màn này thao tác ở cấp KIỆN.
      "packageCount": 1,
      "shortages": [
        {
          "merchantSkuId": "01973e...",
          "merchantSkuCode": "SKU-001",
          "skuName": "Áo thun đen L",
          "required": 3,
          "available": 1,
          "shortBy": 2,
          "reason": "insufficient_stock",
        },
        {
          // Chưa ghép nối: `available` là `null`, KHÔNG phải `0` — không đo được tồn khác với đo
          // được và bằng không. `merchantSkuCode` lùi về mã SKU người bán đặt trên sàn để dòng vẫn
          // có nhãn đọc được.
          "merchantSkuId": null,
          "merchantSkuCode": "TT-SKU-9",
          "skuName": "Quần jean",
          "required": 2,
          "available": null,
          "shortBy": 2,
          "reason": "unmapped_sku",
        },
      ],
    },
  ],
  "pageInfo": {
    "hasNextPage": false,
    "nextCursor": null,
    // Tổng đơn khớp MỌI bộ lọc đang áp (`shopId` + `reason`), không phải số dòng của trang này.
    "totalCount": 85,
    "totalPages": 5,
  },
}
```

Điều kiện "thiếu tồn" chạy ngay trong SQL trên toàn tập đơn tồn đọng, nên **trang đầy đủ `limit`
dòng khi `hasNextPage` là `true`** và `totalCount` là con số thật, không phải phần đã soi. Ngoại lệ
duy nhất là đơn vừa được bù đủ hàng giữa hai lượt đọc: nó tự rời trang thay vì hiện một dòng rỗng.
Client vẫn đi theo `nextCursor` cho tới khi nó là `null`.

> **Đổi hành vi (FR-06-20b).** Trước đây một trang có thể ngắn hơn `limit` mà `hasNextPage` vẫn
> `true`; điều đó không còn xảy ra. Client nào có xử lý riêng cho trang rỗng thì gỡ được.

#### `GET /api/v1/orders/missing-stock/summary`

**Query:** `shopId?: uuid`. Tính bằng **đúng một luật** với danh sách nên hai bên không lệch nhau, và
đếm trên **toàn bộ** đơn tồn đọng — không có trần soi.

Cố ý **không nhận `reason`**: ba thẻ là bức tranh tổng theo `shopId` (thẻ thứ ba đã là phân rã theo
reason rồi), còn con số theo bộ lọc nằm ở `pageInfo.totalCount` cạnh bảng.

```jsonc
{ "missingStockOrderCount": 12, "totalShortQuantity": 47, "unmappedCauseOrderCount": 3 }
```

#### `GET /api/v1/orders/anomalies`

**Auth:** quyền `orders.view`, phạm vi theo `tenant.shopScope`.
**Query:** `limit?` · `cursor?` · `shopId?: uuid` · `type?` · `search?: string` (≤200)

`search` cùng nghĩa với `GET /api/v1/orders/missing-stock`, khớp trên đơn mà dòng bất thường trỏ tới.
Khi có `search`, một trang có thể ngắn hơn `limit` — kể cả rỗng — trong khi `hasNextPage` vẫn là
`true`; client tiếp tục theo `nextCursor`.

Năm loại, phủ ba nhóm nguyên nhân của FR-06-21:

| Nhóm            | `type`                    | Còn mở khi                                      |
| --------------- | ------------------------- | ----------------------------------------------- |
| Thiếu thông tin | `UNMAPPED_SKU`            | Đơn chưa đóng còn dòng chưa ghép nối            |
| Thiếu thông tin | `SKU_DELETED`             | Đơn chưa đóng còn dòng trỏ vào SKU kho đã xoá   |
| Lệch dữ liệu    | `STATUS_DRIFT`            | `orders.platformSyncedAt <= thời điểm ghi nhận` |
| Lệch dữ liệu    | `UNKNOWN_PLATFORM_STATUS` | `orders.platformSyncedAt <= thời điểm ghi nhận` |
| Lỗi đồng bộ     | `SYNC_FAILED`             | `orders.platformSyncedAt <= thời điểm ghi nhận` |

Một đơn vừa có dòng chưa ghép nối vừa có dòng SKU đã xoá chỉ hiện **một** dòng `SKU_DELETED` — lỗi cụ
thể hơn, và đủ để mở đúng màn xử lý.

**Đổi so với mock FE:** FE cũ nhóm theo `anomalyReason` (chuỗi tự do). Hợp đồng chốt là `type` (enum)

- `reasonLabel` (nhãn tiếng Việt để hiển thị) — nhóm theo chuỗi tự do thì không gắn được mỗi mã một
  hành động khắc phục, đúng thứ AC-4 đòi.

```jsonc
{
  "object": "list",
  "url": "/api/v1/orders/anomalies",
  "data": [
    {
      "id": "01973f...",
      "orderId": "01973c...",
      "externalOrderId": "5776...",
      "shopId": "01973b...",
      "shopName": "Shop A",
      "buyerName": "Nguyễn Văn A",
      "orderedAt": "2026-09-01T08:00:00.000Z",
      "type": "SYNC_FAILED",
      // Chỉ khác `null` khi `type` là `SYNC_FAILED`.
      "reasonCode": "AUTH_TOKEN_EXPIRED",
      "reasonLabel": "Uỷ quyền gian hàng đã hết hạn hoặc bị thu hồi",
      "detectedAt": "2026-09-02T10:00:00.000Z",
      // Suy ra từ `type` + `reasonCode`, không lưu. Mảng rỗng là câu trả lời hợp lệ.
      "availableActions": ["reauthorize_shop"],
    },
  ],
  "pageInfo": { "hasNextPage": false, "nextCursor": null },
}
```

Ba mã lỗi đồng bộ và hành động tương ứng (AC-4, AC-5):

| `reasonCode`               | `availableActions`        | Vì sao                                               |
| -------------------------- | ------------------------- | ---------------------------------------------------- |
| `AUTH_TOKEN_EXPIRED`       | `reauthorize_shop`        | Thử lại chắc chắn hỏng tiếp                          |
| `SHOP_CREDENTIAL_MISMATCH` | `resync_shop_credentials` | Đồng bộ lại thông tin gian hàng, không động uỷ quyền |
| `PLATFORM_ERROR`           | `resync_order`            | Thử lại có ích                                       |

`STATUS_DRIFT` → `resync_order` (BR-06-01: kéo lại theo sàn). `UNKNOWN_PLATFORM_STATUS` → **không có
hành động**: sàn thêm trạng thái mới thì phải cập nhật build, không phải việc vận hành.
`UNMAPPED_SKU` / `SKU_DELETED` → `open_sku_mapping`, chỉ là đường dẫn FE.

`SHOP_CREDENTIAL_MISMATCH` dùng chung bộ mã lý do với màn sức khoẻ kết nối (VIS-69), không dựng bộ mã
thứ hai. Cách phân loại **suy từ trạng thái nội bộ**: sàn gộp mọi lỗi uỷ quyền vào dải 10000–10999 và
chưa công bố mã riêng cho `shopCipher` lệch, nên "bị từ chối uỷ quyền **mà refresh token còn hạn**"
được đọc là cipher lệch (mục 1.1a). Thu hẹp lại theo mã thật nếu sàn công bố.

#### `GET /api/v1/orders/anomalies/summary`

**Auth:** quyền `orders.view`, phạm vi theo `tenant.shopScope`.
**Query:** `shopId?: uuid`. Không nhận `type` hay `search`: lọc theo `type` thì chỉ còn đúng một nhóm.

Số liệu đầu màn Bất thường, đếm trên **toàn bộ** tập mà `GET /api/v1/orders/anomalies` duyệt qua —
cùng một câu SQL — chứ không phải trên trang đang mở.

```jsonc
{
  // Số ĐƠN khác nhau: một đơn mắc nhiều loại lỗi chỉ tính một lần.
  "totalOrders": 128,
  // Số DÒNG = tổng `count` của `groups` = `pageInfo.total` của danh sách gọi cùng `shopId`.
  "totalAnomalies": 135,
  // Chỉ nhóm đang có đơn, nhiều đơn trước. Số nhóm nguyên nhân là độ dài mảng.
  "groups": [
    {
      "type": "SYNC_FAILED",
      // Chỉ khác `null` khi `type` là `SYNC_FAILED`; mã sàn trả mà bản này chưa biết rơi vào nhóm `null`.
      "reasonCode": "AUTH_TOKEN_EXPIRED",
      "reasonLabel": "Uỷ quyền gian hàng đã hết hạn hoặc bị thu hồi",
      // Mỗi đơn có tối đa một dòng mỗi loại, nên đây vừa là số dòng vừa là số đơn của nhóm.
      "count": 12,
      "availableActions": ["reauthorize_shop"],
    },
  ],
}
```

Nhóm theo `type`, và theo `reasonCode` khi `type` là `SYNC_FAILED` — cùng cách nhóm của danh sách.
Đây là request riêng với danh sách, nên hai con số có thể lệch nhau nếu dữ liệu đổi giữa hai lần gọi.

#### `POST /api/v1/orders/{orderId}/resync`

**Auth:** `orders.view` **và** `shops.sync.run` (vì nó gọi sang sàn).

**202** → `{ "jobId": "order-resync__5776...", "status": "queued" }`

Xếp hàng đúng luồng đồng bộ một đơn mà webhook của sàn vẫn dùng — không dựng queue mới, không viết
luồng sync mới. Không cần `Idempotency-Key`: gọi trùng cùng lắm tốn một lượt đồng bộ.

#### Mã lỗi của cả bốn endpoint

| Tình huống                           | Kind → status      | `code`                       |
| ------------------------------------ | ------------------ | ---------------------------- |
| `cursor` hỏng                        | `malformed` → 400  | `invalid_cursor`             |
| `limit` ngoài khoảng                 | `validation` → 422 | — (`CursorPaginationDto`)    |
| Thiếu quyền                          | `forbidden` → 403  | `forbidden`                  |
| Đơn ngoài `shopScope` khi resync     | `not_found` → 404  | `order_not_found`            |
| Resync một dòng `AUTH_TOKEN_EXPIRED` | `conflict` → 409   | `order_resync_not_available` |

### 10.3 Báo cáo — FR-13-03, FR-13-04, FR-13-05, FR-13-08

| Method | Path                                 | FR            | Ghi chú                                |
| ------ | ------------------------------------ | ------------- | -------------------------------------- |
| GET    | `/api/v1/reports/sales`              | FR-13-03 (P0) | Doanh thu theo shop/SKU/ngày           |
| GET    | `/api/v1/reports/inventory`          | FR-13-04 (P0) | Giá trị tồn, SKU tồn thấp, SKU tồn lâu |
| GET    | `/api/v1/reports/orders`             | FR-13-05 (P0) | Tổng đơn, đơn hiệu quả, tỉ lệ huỷ/hoàn |
| GET    | `/api/v1/reports/staff-productivity` | FR-13-08 (P1) | Số kiện xử lý/in/quét theo người       |
| GET    | `/api/v1/reports/{kind}/export`      | FR-13-07 (P0) | Xuất Excel theo đúng bộ lọc đang xem   |

Bốn báo cáo dùng chung bộ tham số lọc (FR-13-06): `?from=&to=&shopIds=&categoryIds=`. Cùng cấu trúc lọc với lịch gửi tự động ở mục 7 — hai ngôn ngữ lọc sẽ khiến số trong email lệch với số trên màn hình.

Ba ràng buộc:

1. Cột lợi nhuận và giá vốn bị lược khỏi response khi thiếu `data.profit.view` / `data.cost.view` (FR-01-06), áp dụng cho cả file Excel xuất ra.
2. `/reports/staff-productivity` chứa dữ liệu về người, gác riêng bằng `reports.staff_productivity.view`.
3. Báo cáo tổng hợp là một report resource; các series theo ngày/shop/SKU là field lồng bên trong, không phải collection endpoint. Khoảng thời gian tối đa 366 ngày; vượt → `422` mã `report_range_too_wide`.

### 10.4 Đối soát cước vận chuyển — chưa có FR

| Method | Path                    | FR  | Ghi chú                                    |
| ------ | ----------------------- | --- | ------------------------------------------ |
| GET    | `/api/v1/shipping/fees` | —   | Cước sàn tính vs cước thực nhận, theo kiện |

> **Cần PM bổ sung FR.** Màn `/shipping/fees` đã dựng nhưng chưa có FR phủ. Không thuộc M12 vì cước vận chuyển được sàn trừ theo kiện, không theo kỳ quyết toán. Xem thêm [danh mục quyền](./vishop-permission-catalog.md) §5.

### 10.5 Cấu hình tổ chức — M00 (FR-00-10)

| Method | Path                                   | Quyền                            | Ghi chú                                                                |
| ------ | -------------------------------------- | -------------------------------- | ---------------------------------------------------------------------- |
| GET    | `/api/v1/settings`                     | Bất kỳ thành viên đã xác thực    | Trả cả 2 nhóm field bên dưới                                           |
| PATCH  | `/api/v1/settings/business-info`       | **OWNER-only** (membership tier) | 404 nếu không phải OWNER — cùng kiểu che giấu như `PATCH /tenants/:id` |
| PATCH  | `/api/v1/settings/display-preferences` | `settings.manage`                | Permission-based, không phải membership tier                           |

Tách 1 PATCH thành 2 endpoint vì 2 nhóm field có 2 mô hình phân quyền khác nhau — thông tin doanh
nghiệp là hồ sơ pháp nhân dùng xuất hoá đơn VAT (nhạy cảm hơn `settings.manage`), trong khi tuỳ chọn
hiển thị chỉ là cấu hình vô hại. Trả về object phẳng theo 2 nhóm lồng (`businessInfo`,
`displayPreferences`), không dùng danh sách khoá–giá trị.

```jsonc
// GET /api/v1/settings
{
  "tenantId": "0198...",
  "businessInfo": {
    "companyName": "Công ty TNHH ViShop Việt Nam",
    "taxCode": "0312345678",
    "businessAddress": "Tầng 5, Toà nhà ABC, Quận 1, TP. Hồ Chí Minh",
    "representativeName": "Nguyễn Bảo Trọng",
    "contactEmail": "trong@vishop.vn",
    "contactPhone": "0901234567",
    "logoUrl": null,
  },
  "displayPreferences": {
    // IANA time zone / ISO 4217 currency / enum cố định — dùng CHỈ khi định dạng báo cáo xuất
    // Excel. Không đụng baseCurrency (tiền hạch toán thật) hay cách hiển thị dữ liệu trong app,
    // luôn là GMT+7/VND bất kể giá trị ở đây.
    "timezone": "Asia/Ho_Chi_Minh",
    "dateFormat": "dd/mm/yyyy", // dd/mm/yyyy | mm/dd/yyyy | yyyy-mm-dd
    "currency": "VND",
  },
}
```

`logoUrl` có ngữ nghĩa khác nhau giữa ghi và đọc:

- **Ghi** (`PATCH .../business-info`): client gửi `key` trả về từ `POST /upload/confirm` (một
  storage path, không phải URL) vào field `logoUrl`. Server kiểm tra `key` đó thuộc file chính
  tài khoản này đã upload **và** đã qua xác minh malware (`status = READY`) trước khi lưu — sai
  điều kiện nào cũng trả `404 tenant_logo_not_found`.
- **Đọc** (`GET /api/v1/settings`, và trong response của cả 2 PATCH): server luôn resolve `key`
  đã lưu thành **signed URL còn hạn** tại thời điểm gọi (không lưu URL, không cache) — do đó field
  `logoUrl` trong mọi response luôn là URL dùng hiển thị ngay được, không bao giờ là raw key.

Cấu hình quét (FR-08-07, FR-08-08) và ngưỡng cảnh báo mặc định — có trong bản phác thảo cũ của mục
này — bị loại khỏi FR-00-10 vì chưa có spec cụ thể; sẽ là ticket riêng khi được lên lịch.

---

## Phụ lục — bảng đối chiếu module → adapter

| ChannelAdapter (§2.1)           | Module build-spec                                                 | Section trong file này |
| ------------------------------- | ----------------------------------------------------------------- | ---------------------- |
| AuthProvider                    | M02 (kết nối + sức khoẻ gian hàng)                                | 1, 1.1                 |
| ProductProvider                 | M03, M04                                                          | 2                      |
| OrderProvider                   | M06 (danh sách & chi tiết)                                        | 3                      |
| FulfillmentProvider             | M06 (xử lý package, theo dõi vận chuyển), M07 (in tem, sóng nhặt) | 4, 4.1–4.5             |
| InventoryProvider               | M05 (tồn, đa kho, lô & hạn dùng)                                  | 5, 5.1–5.2             |
| FinanceProvider                 | M10, M12                                                          | 6                      |
| — _(nội bộ, không qua adapter)_ | M13 (lịch báo cáo)                                                | 7                      |
| — _(nội bộ, không qua adapter)_ | M13 (trợ lý ViAI), M14 (nhật ký trợ lý)                           | 8                      |
| — _(nội bộ, không qua adapter)_ | M00, M01 (nhật ký thao tác, cờ tính năng, quản lý thành viên)     | 9, 9.1–9.3             |
