# Kiến trúc Bảo mật Hệ thống (Security Architecture)

Tài liệu này đặc tả toàn bộ kiến trúc an ninh, bảo mật thông tin và phân quyền kiểm soát truy cập trong hệ thống microservices của **ViShop Platform** dựa trên các công nghệ: **Spring Security 6**, **Spring Cloud Gateway**, **PostgreSQL Row-Level Security**, **Docker Hardening** và bộ công cụ **DevSecOps**.

---

## 1. Nguyên tắc Thiết kế Cốt lõi (Core Principles)

Kiến trúc bảo mật của ViShop Platform tuân thủ 5 nguyên tắc bắt buộc:

1. **Phòng thủ Đa lớp (Defense in Depth)**: Không phó mặc an ninh cho một chốt chặn duy nhất. Ví dụ: Nếu một truy vấn ở tầng ứng dụng bỏ sót điều kiện `tenant_id`, tầng Database (Row-Level Security) vẫn phải chốt chặn để chống rò rỉ dữ liệu.
2. **Mạng Zero Trust (Zero-Trust Architecture)**: Mọi yêu cầu (request) đi qua API Gateway đều phải được xác thực danh tính, chuẩn hóa và đóng gói thành ngữ cảnh nội bộ (Internal Context). Các microservice hạ nguồn (downstream) không tin tưởng mù quáng vào các header do client trực tiếp gửi lên.
3. **Cô lập Đa người thuê Tuyệt đối (Strict Multi-Tenancy)**: Mọi dữ liệu nghiệp vụ đều thuộc về một Tổ chức (`tenant`). Định danh tenant bắt buộc phải được giải quyết từ ngữ cảnh an toàn, không bao giờ tin cậy `tenant_id` từ request body của người dùng.
4. **Quyền tối thiểu (Least Privilege & Explicit RBAC)**: Phân quyền theo vai trò 2 cấp (Tổ chức & Gian hàng) dựa trên danh mục 75 quyền tĩnh. Không sử dụng ABAC điều kiện tự do để đảm bảo hiệu năng và tính giải trình trong kiểm toán.
5. **Dịch chuyển Bảo mật về sớm (Shift-Left DevSecOps)**: Quét lỗ hổng dependencies, mã nguồn tĩnh (SAST) và container CVEs ngay trong chu trình phát triển và CI pipeline.

---

## 2. Sơ đồ Kiến trúc Bảo mật Tổng thể

Hệ thống bảo vệ dữ liệu và ứng dụng theo mô hình **Phòng thủ Đa lớp (Defense-in-Depth)** từ cửa ngõ Ingress đến tầng lưu trữ dữ liệu:

```mermaid
flowchart TB
    classDef client fill:#EBF5FB,stroke:#2980B9,stroke-width:2px,color:#1B4F72;
    classDef gateway fill:#E8F8F5,stroke:#16A085,stroke-width:2px,color:#0E6251;
    classDef svc fill:#FEF9E7,stroke:#D4AC0D,stroke-width:2px,color:#7D6608;
    classDef data fill:#F4ECF7,stroke:#8E44AD,stroke-width:2px,color:#512E5F;

    subgraph ClientZone["1. TẦNG TRUY CẬP (Client Layer)"]
        Web["🌐 Web SPA (Dashboard)"]:::client
        Mobile["📱 Mobile App"]:::client
        ThirdParty["🔗 Webhook Sàn (TikTok / Shopee)"]:::client
    end

    subgraph IngressZone["2. CỬA NGÕ BẢO MẬT (API Gateway :8000)"]
        WAF["🛡️ CORS & Header Guard\n(Chặn Origin lạ & Header độc hại)"]:::gateway
        RateLimit["⏱️ Redis Rate Limiter\n(Chống Brute-force & DDoS theo IP/Tenant)"]:::gateway
        AntiSpoof["🧹 Anti-Spoofing Filter\n(Xóa sạch header giả mạo X-User-*)"]:::gateway
        AuthFilter["🔑 Token Mediation & Session Verify\n(Kiểm tra Cookie / JWT qua Redis Cache)"]:::gateway
        EnrichContext["🏷️ Context Enrichment\n(Gắn Header an toàn: X-User-Id, X-Tenant-Id, X-Permissions)"]:::gateway
    end

    subgraph ServiceZone["3. MẠNG NỘI BỘ MICROSERVICES (vishop-prod-network)"]
        SecFilter["🔒 Spring Security 6 FilterChain\n(Bảo vệ Stateless & thiết lập Principal)"]:::svc
        TenantFilter["🏢 TenantContextFilter\n(Gán ThreadLocal UUID cho tenant_id)"]:::svc
        RBAC["⚖️ Method Security (@PreAuthorize)\n(Kiểm soát 75 quyền tĩnh & Scope Gian hàng)"]:::svc
    end

    subgraph DataZone["4. TẦNG DỮ LIỆU & KIỂM TOÁN (Persistence & Audit)"]
        Postgres[("🗄️ PostgreSQL (Schema + RLS)\nRow-Level Security chặn rò rỉ dữ liệu chéo Tenant")]:::data
        Kafka[("📨 Apache Kafka\nTenantRecordInterceptor lan truyền Tenant Header")]:::data
        AuditSvc[("📝 Audit Service\nLưu trữ nhật ký kiểm toán bất biến")]:::data
    end

    %% Luồng luân chuyển
    Web & Mobile & ThirdParty -->|HTTPS Request| WAF
    WAF --> RateLimit
    RateLimit --> AntiSpoof
    AntiSpoof --> AuthFilter
    AuthFilter --> EnrichContext
    EnrichContext -->|Forward qua mạng nội bộ kín| SecFilter
    SecFilter --> TenantFilter
    TenantFilter --> RBAC
    RBAC -->|SQL Query kèm tenant_id| Postgres
    RBAC -.->|Async Event| Kafka
    Kafka -.-> AuditSvc
```

### Bảng đối chiếu 4 Chốt chặn An ninh (The 4 Security Checkpoints)

| Chặng | Thành phần | Trách nhiệm an ninh cốt lõi |
| :---: | :--- | :--- |
| **1** | **API Gateway Biên** | Chặn request từ Origin lạ (CORS), giới hạn tần suất gọi (Rate Limiting chống dò mật khẩu), và xóa sạch mọi header giả mạo `X-User-*` do client tự gửi. |
| **2** | **Xác thực & Gán Context** | Gateway kiểm tra Session/JWT qua Redis cache. Nếu hợp lệ, tự động sinh các header nội bộ sạch (`X-User-Id`, `X-Tenant-Id`, `X-User-Permissions`) trước khi chuyển tiếp. |
| **3** | **Ủy quyền Microservice** | Microservice nhận request trong mạng kín, kích hoạt Spring Security 6 để tái tạo `CommonUserDetails` (`UserDetails`) in-memory, kiểm tra quyền hạn `@PreAuthorize` và lưu `tenant_id` vào `TenantContext` (`ThreadLocal`). |
| **4** | **Cô lập Dữ liệu & Kiểm toán** | Tầng Persistence gán điều kiện `tenant_id` cho mọi query; PostgreSQL Row-Level Security làm chốt chặn vật lý; Kafka tự động truyền context; Audit Service lưu vết kiểm toán bất biến. |

---

## 3. Tầng Cửa ngõ (API Gateway) & Xác thực (Authentication)

API Gateway là điểm tiếp nhận duy nhất cho toàn bộ traffic bên ngoài, chịu trách nhiệm thực thi các chính sách bảo mật biên (Edge Security):

### 3.1. Cơ chế Xác thực Kép (Dual-Mode Authentication)
* **Web Dashboard**: Sử dụng **Opaque Session Cookie** (`HttpOnly`, `SameSite=Lax/Strict`, `Secure`). Cookie được mã hóa và xác thực phiên qua Redis hoặc `identity-service`, tương thích với kiến trúc Better Auth.
* **Mobile App / Public Integration API**: Sử dụng **Stateless Bearer Token** (hoặc JWT) có thời hạn sống ngắn (15–30 phút) kèm cơ chế Refresh Token Rotation (lưu băm SHA-256).

### 3.2. Token Mediation & Context Enrichment
API Gateway đóng vai trò làm trung gian biến đổi danh tính:
1. Nhận Session/Token từ Client.
2. Kiểm tra tính hợp lệ và truy xuất thông tin người dùng (`userId`, `status`, danh sách `roles`, danh sách `permissions`).
3. **Chống giả mạo (Anti-Spoofing)**: Gateway chủ động loại bỏ (strip) tất cả các header bắt đầu bằng `X-User-` do Client tự ý gửi lên trước khi forward.
4. Đính kèm các header nội bộ an toàn xuống các downstream microservices:
   * `X-User-Id`: UUID người dùng.
   * `X-Tenant-Id`: UUID tổ chức đang thực hiện thao tác.
   * `X-User-Roles`: Danh sách vai trò ngăn cách bởi dấu phẩy (vd: `OWNER,ACCOUNTANT`).
   * `X-User-Permissions`: Danh sách quyền tĩnh (vd: `order.view,product.create`).

### 3.3. Bảo vệ Tài nguyên & Chống Brute-Force (Rate Limiting)
Triển khai Redis Rate Limiter theo thuật toán **Token Bucket** ngay tại Gateway:
* **Public Endpoints**: Tối đa `20 requests/giây`.
* **Authenticated Endpoints**: Tối đa `100 requests/giây` theo từng `tenant_id`.
* **Login & Auth Endpoints**: Tối đa `5 requests/phút` theo Client IP nhằm triệt tiêu các cuộc tấn công dò mật khẩu (Brute-force / Credential Stuffing).

### 3.4. Cơ chế Xác thực Biên tại Gateway (Tại sao Exclude Spring Security mặc định)
Trong [GatewayApplication.java](../../services/api-gateway/src/main/java/com/vishop/gateway/GatewayApplication.java), hệ thống chủ động loại trừ các cấu hình tự động:
```java
@SpringBootApplication(exclude = {
        ReactiveSecurityAutoConfiguration.class,
        ReactiveUserDetailsServiceAutoConfiguration.class,
        ReactiveManagementWebSecurityAutoConfiguration.class
})
```
* **Lý do kiến trúc**: Cấu hình mặc định của Spring Security WebFlux mang tính nguyên khối (monolithic-oriented), tự động sinh form login, HTTP basic, CSRF và session filters gây suy giảm thông lượng của API Gateway (vốn hoạt động như một High-Throughput Reverse Proxy trên Netty phi đồng bộ).
* **Giải pháp thay thế**: Thay vào đó, Gateway sử dụng một Custom Global Filter chuyên trách: [AuthenticationGlobalFilter.java](../../services/api-gateway/src/main/java/com/vishop/gateway/filter/AuthenticationGlobalFilter.java) với chi phí xử lý tối thiểu (Zero unnecessary allocations, non-blocking pipeline).

---

### 3.5. Bài toán Lệch Quyền hạn & Vô hiệu hóa Token Thời gian thực (The Stale Token & Revocation Problem)

#### A. Đặt vấn đề (The Problem Statement)
Trong mô hình **Stateless JWT**:
1. Một khi Token đã được cấp phát, nó mang theo chữ ký điện tử mật và chứa sẵn thông tin phân quyền tĩnh:
   `{ "sub": "<userId>", "tenantId": "<tenantId>", "roles": ["OWNER"], "permissions": ["order.refund", "tenant.manage"], "exp": ... }`
2. **Kịch bản rủi ro nghiêm trọng phát sinh trong thực tế:**
   * **Kịch bản 1 — Đình chỉ Gian hàng / Khóa Tài khoản:** Ngay sau khi User đăng nhập, Admin vào hệ thống chuyển trạng thái Tenant sang `SUSPENDED` (do vi phạm chính sách / nợ cước) hoặc khóa tài khoản User.
   * **Kịch bản 2 — Thay đổi Vai trò / Hạ quyền:** User vẫn đang hoạt động bình thường, nhưng bị Admin thu hồi quyền (ví dụ: giáng chức từ `OWNER` xuống `STAFF`, hoặc tước quyền `order.refund`).
3. **Lỗ hổng của việc chỉ kiểm tra chữ ký (`jwt.verify`):**
   * Về mặt toán học mật mã, chiếc Token trên máy Client **vẫn hoàn toàn hợp lệ cho đến khi hết hạn (TTL)**.
   * Nếu API Gateway chỉ kiểm tra chữ ký, Gateway vẫn bóc tách header `X-User-Roles: OWNER` và chuyển tiếp sang downstream services.
   * Hậu quả: Kẻ gian hoặc nhân viên bị giáng chức vẫn tiếp tục thực thi các thao tác nhạy cảm của `OWNER` trong suốt khoảng thời gian Token còn sống (Stale Permissions Window)!

#### B. Phân tích Phản kiến trúc: Tại sao KHÔNG ĐƯỢC để Gateway gọi `identity-service` trên mỗi request?
Một phản xạ tự nhiên của lập trình viên là: *"Tại sao Gateway không gọi HTTP sang `identity-service` trên mỗi request để kiểm tra DB xem user/tenant còn active hay quyền đã đổi chưa?"*

Trong kiến trúc Microservices phân tán, đây là một **Thảm họa kiến trúc (Catastrophic Anti-Pattern)** vì 4 lý do:
1. **Bùng nổ Độ trễ (Latency Explosion):** Mỗi request (dù là xem danh mục hàng, tạo giỏ hàng, thanh toán) đều phải chịu thêm **20ms – 100ms** thời gian chờ mạng (Network Hop: Gateway $\rightarrow$ Identity $\rightarrow$ Gateway).
2. **Điểm nghẽn Hạ tầng (Database & CPU Bottleneck):** Nếu toàn hệ sinh thái nhận $10.000\text{ req/s}$, thì `identity-service` và cơ sở dữ liệu của nó cũng phải gánh đúng $10.000\text{ req/s}$ chỉ để chạy các câu lệnh `SELECT status, roles FROM users`. Database sẽ sập ngay lập tức.
3. **Phá vỡ Tính cô lập lỗi (Single Point of Failure - SPoF):** Nếu `identity-service` gặp sự cố mạng hoặc khởi động lại, **100% các microservices khác trong công ty sẽ chết theo**, không một client nào gọi được bất kỳ API nào.
4. **Phá hủy bản chất Stateless của JWT:** Biến kiến trúc token phân tán thành Stateful Session chậm chạp.

#### C. Tổng hợp Tất cả các Giải pháp Khả thi (All Possible Solutions)

| STT | Giải pháp | Cơ chế hoạt động | Ưu điểm | Nhược điểm / Đánh đổi |
| :---: | :--- | :--- | :--- | :--- |
| **1** | **Short-lived Token (Thời hạn sống ngắn)** | Cấu hình Access Token chỉ sống 5–15 phút. Khi hết hạn, Client gọi `/refresh` để cấp token mới mang quyền mới từ DB. | Cực kỳ đơn giản, không cần thêm hạ tầng cache phụ. | Tồn tại độ trễ (Stale Window) 5–15 phút kẻ gian vẫn dùng được quyền cũ. |
| **2** | **Thu hồi Phiên cưỡng bức (Forced Session Revocation)** | Đổi quyền được coi là Security Event $\rightarrow$ Hủy Refresh Token trong DB + Đẩy `userId` vào Redis Blacklist trong thời gian bằng TTL Access Token. | Chặn đứng tức thì trong 1ms, độ an toàn tuyệt đối. | Trải nghiệm người dùng (UX) bị gián đoạn: User bị văng ra màn hình đăng nhập. |
| **3** | **Mô hình Lai (Hybrid Token - Tách Role khỏi JWT)** | JWT chỉ lưu `userId` & `tenantId`. Toàn bộ `roles` & `permissions` lưu tại Redis Cache. Gateway/Service đọc Redis in-memory ($< 0.5\text{ms}$). Đổi role chỉ cần `SET` lại Redis key. | Áp dụng quyền mới tức thì, UX êm mượt không cần cấp lại token. | Tăng phụ thuộc vào Redis. Nếu Redis chết, Gateway không lấy được permissions. |
| **4** | **Phiên bản hóa Token (Token / Role Versioning)** | Mỗi user có `token_version` (khởi đầu = 1) nhúng vào JWT claim `v`. Khi đổi quyền/khóa user: DB tăng `token_version` lên 2 và cập nhật Redis `user:version:{id} = 2`. Gateway so sánh `claim.v != redis.v` $\rightarrow$ Trả về `401 Token Outdated`. Client dùng Refresh Token âm thầm xin JWT mới. | **Hoàn hảo nhất:** Vừa tức thì (Real-time), vừa không nghẽn DB, vừa mượt mà (Client tự refresh ngầm, không văng user). | Cần quản lý trường version trong JWT và 1 key nhỏ trong Redis. |

#### D. Lựa chọn Kiến trúc cho ViShop Platform (The Chosen Strategy)

ViShop Platform lựa chọn **Chiến lược Phối hợp Đa tầng Phòng thủ (Multi-Layered Defense Strategy)** kết hợp giữa **Token Versioning & Blacklist qua Redis (Giải pháp 4 & 2)** cùng với **Kafka Event-Driven Invalidation**:

```mermaid
flowchart TD
    subgraph Trigger["1. BIẾN ĐỘNG QUYỀN HẠN / TRẠNG THÁI"]
        AdminAction["Admin thao tác tại Backoffice:\n- Đổi Role (OWNER -> STAFF)\n- Đình chỉ Shop (Tenant: SUSPENDED)\n- Khóa tài khoản (User: SUSPENDED)"]
    end

    subgraph Sync["2. ĐỒNG BỘ TRẠNG THÁI BẤT ĐỒNG BỘ"]
        DBUpdate["Ghi DB nghiệp vụ (tăng token_version / đổi status)"]
        Outbox["Transactional Outbox Relay\n(Bắn Event lên Kafka)"]
        RedisSet["Ghi tức thì vào Redis Cache:\n- tenant:status:{tenantId} = SUSPENDED\n- user:version:{userId} = version_moi\n- user:blacklist:{userId} = true"]
    end

    subgraph Gateway["3. CHỐT CHẶN BIÊN TẠI API GATEWAY (:8000)"]
        direction TB
        RecvReq["Nhận Request từ Client kèm JWT cũ"]
        VerifySig["1. Kiểm tra chữ ký JWT (in-memory 0ms)"]
        CheckRedis["2. Tra cứu Redis O(1) in-memory (< 1ms):\n- Check tenant:status == SUSPENDED?\n- Check token_version == redis_version?"]
        
        Block403["Trả về 403 Forbidden\n(Gian hàng đã bị đình chỉ)"]
        Block401["Trả về 401 Token Outdated\n(Quyền đã thay đổi)"]
        Forward["3. Hợp lệ: Context Enrichment\n(Gắn X-User-Id, X-Tenant-Id, X-Roles) -> Chuyển tiếp downstream"]
    end

    AdminAction --> DBUpdate
    DBUpdate --> Outbox
    DBUpdate --> RedisSet

    RecvReq --> VerifySig --> CheckRedis
    CheckRedis -- "Tenant SUSPENDED" --> Block403
    CheckRedis -- "Lệch Version" --> Block401
    CheckRedis -- "Khớp & Active" --> Forward

    classDef act fill:#FADBD8,stroke:#E74C3C,stroke-width:2px,color:#78281F;
    classDef sync fill:#FCF3CF,stroke:#F1C40F,stroke-width:2px,color:#7D6608;
    classDef gw fill:#E8F8F5,stroke:#16A085,stroke-width:2px,color:#0E6251;
    classDef block fill:#F2D7D5,stroke:#C0392B,stroke-width:2px,color:#922B21;

    class AdminAction act;
    class DBUpdate,Outbox,RedisSet sync;
    class RecvReq,VerifySig,CheckRedis,Forward gw;
    class Block403,Block401 block;
```

##### 3 Nguyên tắc thực thi cốt lõi:
1. **Kiểm tra Siêu tốc tại Gateway ($< 1\text{ms}$):**
   * API Gateway **tuyệt đối không gọi HTTP sang `identity-service`**.
   * Gateway chỉ đọc 2 key O(1) từ Redis Cache:
     * `tenant:status:{tenantId}`: Nếu khác `ACTIVE` $\rightarrow$ Ngắt ngay bằng `403 Forbidden` (`The organization/tenant has been suspended`).
     * `user:version:{userId}`: Nếu giá trị version trong Redis khác với claim `v` trong token $\rightarrow$ Trả về `401 Unauthorized` (`Security context outdated, refresh required`).
2. **Kênh Đồng bộ Sự kiện (Kafka Event Backbone):**
   * Khi `tenant-service` phát sinh `TenantStatusChangedEvent`, sự kiện được Outbox đẩy sang topic `vishop.tenant.events`.
   * Consumer cập nhật trạng thái mới vào Redis trong vòng vài chục mili-giây, bảo đảm toàn bộ Gateway clusters đều đồng bộ.
3. **Cơ chế Tự làm mới Mượt mà (Silent Refresh on Client):**
   * Khi nhận mã `401 Token Outdated`, ứng dụng Frontend/Mobile không văng user ra màn hình đăng nhập, mà kích hoạt Axios Interceptor gửi ngầm Refresh Token lên `identity-service/api/v1/auth/refresh`.
   * `identity-service` kiểm tra DB, đọc quyền hạn mới nhất (`STAFF`) và cấp JWT mới (có version mới). User tiếp tục sử dụng ứng dụng mà không hề cảm thấy bị gián đoạn.

---

## 4. Tầng Dịch vụ & Phân quyền (Authorization - RBAC)

### 4.1. Mô hình Phân quyền Cấp Tổ chức (Tenant-Scoped RBAC)
Theo nguyên tắc thiết kế cốt lõi của ViShop: **Một Tenant sở hữu nhiều Shops (Shopee, TikTok Shop, Lazada...), và tất cả nhân viên của Tenant đều có quyền quản lý toàn bộ các Shop trực thuộc** (tương ứng theo chức vụ/role của họ). 

```text
               ┌────────────────────────────────────────────────────────┐
               │                      TENANT (Tổ chức)                  │
               └───────────────────────────┬────────────────────────────┘
                                           │
                    ┌──────────────────────┴──────────────────────┐
                    ▼                                             ▼
       ┌────────────────────────┐                    ┌────────────────────────┐
       │     TENANT MEMBERS     │                    │         SHOPS          │
       │ (Nhân sự của tổ chức)  │                    │  (Các kênh bán lẻ)     │
       ├────────────────────────┤                    ├────────────────────────┤
       │ Vai trò theo chức vụ:  │                    │ - Shop TikTok A        │
       │ - CHỦ SHOP (Owner)     │ ── Toàn quyền ───► │ - Shop Shopee B        │
       │ - KẾ TOÁN (Accountant) │    quản lý         │ - Shop Lazada C        │
       │ - NHÂN VIÊN (Staff)    │    tất cả shop     │                        │
       └────────────────────────┘                    └────────────────────────┘
```

* **Phân quyền tập trung theo vai trò (Tenant Roles)**:
  * Người dùng được gán vai trò tại bảng `tenant_members` (ví dụ: `OWNER`, `STAFF`, `ACCOUNTANT`).
  * Vai trò này có hiệu lực trên **toàn bộ các gian hàng con** mà Tenant sở hữu mà không cần phân quyền vi mô theo từng `shop_id`.
  * *Ví dụ*: `STAFF` được tạo/sửa sản phẩm và xử lý đơn trên cả TikTok Shop và Shopee; `ACCOUNTANT` xem được báo cáo tài chính hợp nhất của toàn bộ các shop.
* **Danh mục 75 quyền tĩnh**: Toàn bộ quyền hạn được khai báo dưới dạng enum tĩnh (`Permission`) trong `platform-common:common-security`. Hệ thống từ chối ABAC tự do (JSON conditions) để giữ truy vấn kiểm tra quyền đơn giản, có index và dễ dàng kiểm toán.

### 4.2. Tận dụng `UserDetails` trong Microservices (`CommonUserDetails`)
Thay vì để mỗi microservice phải tự truy vấn database `users` (vi phạm ranh giới dịch vụ và gây nghẽn DB), hệ thống tận dụng chuẩn `org.springframework.security.core.userdetails.UserDetails` bằng mô hình **In-Memory Reconstruction (Tái tạo không tốn truy vấn DB - 0ms)**:

```java
package io.github.coreplatform.common.security.context;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Triển khai chuẩn UserDetails của Spring Security dành cho kiến trúc Microservices.
 * Chứa đầy đủ ngữ cảnh định danh người dùng, tổ chức (tenant) và các gian hàng được phân quyền.
 */
public record CommonUserDetails(
    UUID userId,
    UUID tenantId,
    Set<UUID> accessibleShopIds,
    Collection<? extends GrantedAuthority> authorities
) implements UserDetails {

    @Override
    public String getUsername() {
        return userId.toString();
    }

    @Override
    public String getPassword() {
        return ""; // Ở tầng downstream không cần mật khẩu do đã xác thực tại Gateway
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return true; }

    /** Kiểm tra xem user có quyền truy cập vào gian hàng cụ thể hay không */
    public boolean hasShopAccess(UUID shopId) {
        return accessibleShopIds != null && accessibleShopIds.contains(shopId);
    }
}
```

#### Các lợi ích vượt trội:
1. **Tiêm trực tiếp bằng `@AuthenticationPrincipal`**:
   Lập trình viên dễ dàng lấy thông tin `userId` và `tenantId` ngay tại Controller mà không cần đọc Header thủ công:
   ```java
   @PostMapping("/api/v1/orders")
   public ResponseEntity<OrderResponse> createOrder(
           @AuthenticationPrincipal CommonUserDetails currentUser,
           @Valid @RequestBody CreateOrderRequest request) {
       UUID tenantId = currentUser.tenantId();
       UUID userId = currentUser.userId();
       ...
   }
   ```
2. **Tự động hóa Kiểm toán (`AuditorAware<UUID>`)**:
   Spring Data JPA tự động nhận diện `userId` từ `CommonUserDetails` để điền giá trị `@CreatedBy` và `@LastModifiedBy` cho các Entity kiểm toán.

### 4.3. Cấu hình Spring Security 6 trong Microservices
Các microservice tiêu thụ thư viện [common-security](../../platform-common/common-security) và cấu hình chuẩn:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class CommonSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, InternalHeaderAuthFilter authFilter) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

* **Bộ lọc `InternalHeaderAuthFilter` tái tạo `CommonUserDetails`**:
```java
@Component
public class InternalHeaderAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String userIdStr = request.getHeader("X-User-Id");
        String tenantIdStr = request.getHeader("X-Tenant-Id");
        String permissionsStr = request.getHeader("X-User-Permissions");
        String shopsStr = request.getHeader("X-User-Shops");

        if (userIdStr != null && tenantIdStr != null) {
            UUID userId = UUID.fromString(userIdStr);
            UUID tenantId = UUID.fromString(tenantIdStr);
            
            List<SimpleGrantedAuthority> authorities = Arrays.stream(permissionsStr != null ? permissionsStr.split(",") : new String[0])
                    .filter(p -> !p.isBlank())
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            Set<UUID> shopIds = Arrays.stream(shopsStr != null ? shopsStr.split(",") : new String[0])
                    .filter(s -> !s.isBlank())
                    .map(UUID::fromString)
                    .collect(Collectors.toSet());

            CommonUserDetails userDetails = new CommonUserDetails(userId, tenantId, shopIds, authorities);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
```

* **Method Security (`@PreAuthorize`)**:
```java
@PreAuthorize("hasAuthority('product.create') and #currentUser.hasShopAccess(#shopId)")
@PostMapping("/api/v1/shops/{shopId}/products")
public ResponseEntity<ProductResponse> createProduct(
        @PathVariable UUID shopId,
        @AuthenticationPrincipal CommonUserDetails currentUser,
        @Valid @RequestBody CreateProductRequest request) {
    ...
}
```

---

## 5. Bảo mật Đa người thuê (Multi-Tenancy Isolation)

ViShop Platform thiết lập **5 tầng cô lập** nhằm đảm bảo không rò rỉ dữ liệu chéo giữa các tổ chức:

| Tầng cô lập | Thành phần kỹ thuật | Cơ chế chốt chặn |
| :--- | :--- | :--- |
| **1. Ingress Filter** | `TenantResolutionGlobalFilter` | Kiểm tra tính hợp lệ của UUID trong header `X-Tenant-Id`. |
| **2. Context Scope** | [TenantContextFilter.java](../../platform-common/common-security/src/main/java/io/github/coreplatform/common/security/TenantContextFilter.java) | Trích xuất header và gán vào [TenantContext.java](../../platform-common/common-security/src/main/java/io/github/coreplatform/common/security/TenantContext.java) (`ThreadLocal<UUID>`). Giải phóng ở `finally`. |
| **3. ORM / Query** | [BaseTenantJpaEntity.java](../../platform-common/common-persistence/src/main/java/io/github/coreplatform/common/persistence/BaseTenantJpaEntity.java) | Entity kế thừa cột `tenant_id` không thể cập nhật (`updatable = false`). Query repository bắt buộc truyền `tenantId`. |
| **4. Database RLS** | PostgreSQL Row-Level Security | Kích hoạt RLS trên bảng: `CREATE POLICY tenant_isolation ON <table> USING (tenant_id = current_setting('app.current_tenant')::uuid)`. |
| **5. Messaging** | [TenantRecordInterceptor.java](../../platform-common/common-events/src/main/java/io/github/coreplatform/common/events/consumer/TenantRecordInterceptor.java) | Tự động lan truyền và tái tạo `TenantContext` xuyên suốt các sự kiện Kafka. |

---

## 6. Bảo vệ Dữ liệu & Quản lý Bí mật (Data & Secrets Protection)

### 6.1. Mã hóa Dữ liệu Nhạy cảm khi lưu trữ (Encryption-at-Rest)
* **Token tích hợp sàn TMĐT (TikTok Shop, Shopee)**:
  * Được mã hóa bằng thuật toán **AES-256-GCM** trước khi lưu vào bảng `channel_authorizations`.
  * Khóa bí mật (Master Encryption Key) được cấp phát qua biến môi trường hoặc Key Management Service (Vault/AWS KMS), không bao giờ lưu cùng cơ sở dữ liệu.
  * Chỉ duy nhất tầng adapter chuyên trách (`TikTokChannelAdapter`, `ShopeeChannelAdapter`) mới được quyền giải mã token ngay trước thời điểm thực hiện lệnh gọi HTTP sang sàn.

### 6.2. Che giấu Dữ liệu Cá nhân (PII Masking & Log Sanitization)
* Tích hợp bộ lọc Logback Pattern Masking:
  * Tự động quét và che giấu các trường: `password`, `accessToken`, `refreshToken`, `authorization`, `cookie`, `phone`, `email`.
  * OpenTelemetry distributed tracing sanitize toàn bộ URL query parameters và span attributes để không đẩy thông tin nhạy cảm lên Grafana/Tempo.

---

## 7. An ninh Hạ tầng, Mạng & DevSecOps

### 7.1. Cô lập Mạng Container
* **Môi trường Production ([docker-compose.prod.yml](../../infra/compose/docker-compose.prod.yml))**:
  * Chỉ duy nhất cổng Ingress **8000** (API Gateway) và **3000** (Grafana) được mở ra ngoài host.
  * Toàn bộ PostgreSQL (5432), Kafka (9092), Redis (6379) và các dịch vụ nghiệp vụ chạy trong mạng nội bộ kín `vishop-prod-network`.
* **Chạy phi đặc quyền (Non-Root Container)**:
  * Tất cả Dockerfile đều tạo và chuyển sang user không có quyền quản trị `appuser:appgroup` (UID 1001).
  * Vô hiệu hóa toàn bộ cổng gỡ lỗi từ xa JVM Remote Debugging (JDWP 5000–5003).

### 7.2. Tự động hóa Quét An ninh trong CI/CD (DevSecOps Tooling)

Chi tiết quy chuẩn tại [docs/security.md](../security.md):

| Tầng bảo vệ | Công cụ | Kịch bản kiểm tra | Hành động chặn (Gate) |
| :--- | :--- | :--- | :--- |
| **Dependencies** | **OSV-Scanner** | Quét CVE trong các thư viện Java/Gradle và npm | Thất bại CI nếu có CVE mức `HIGH` hoặc `CRITICAL` |
| **Mã nguồn (SAST)** | **Semgrep** | Quét OWASP Top 10, SQLi, XSS, Secret commit | Thất bại CI nếu vi phạm mức `ERROR` |
| **Container Image** | **Trivy** | Quét CVE hệ điều hành Alpine và runtime JRE | Chặn deploy image lên registry nếu có lỗ hổng chưa vá |
| **Supply Chain** | **Cosign** | Ký số và xác minh chữ ký provenance của Docker images | Chỉ cho phép orchestrator chạy các image có chữ ký số hợp lệ |

### 7.3. Nhật ký Kiểm toán Bất biến (Immutable Audit Trail)
* Mọi hành động nhạy cảm (thêm/sửa/xóa quyền, đăng nhập thất bại, xuất dữ liệu hàng loạt, thay đổi cấu hình gian hàng) đều phát sinh sự kiện `AuditLogEvent`.
* Sự kiện được gửi tới Kafka topic `vishop.audit.events` và được [audit-service](../../services/audit-service) lưu trữ dài hạn phục vụ điều tra sự cố và tuân thủ an toàn thông tin.
