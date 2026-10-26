# Tài liệu Tham chiếu Kiến trúc Dự án Gốc (Source Project Reference: `api-vishop`)

Tài liệu này lưu trữ phân tích chuyên sâu về kiến trúc, mô hình dữ liệu (Database Schema), cơ chế xác thực (Authentication), giải quyết ngữ cảnh tổ chức (Tenant Context) và phân quyền (RBAC) từ dự án gốc:
* **Đường dẫn thư mục gốc**: `C:\Users\ndquynh\Documents\vi-shop\api-vishop`
* **Mục đích**: Làm cơ sở tham chiếu chuẩn để thiết kế, chuyển đổi và đối chiếu khi xây dựng nền tảng microservices Java Spring Boot trên **ViShop Platform** (`vishop-platform`).

---


> Phần phân tích NestJS/Prisma bên dưới mô tả nguồn lịch sử, không phải xác thực runtime Java. Session của nguồn không được port. Java hiện dùng JWT stateless access/refresh; xem [security architecture](security-architecture.md).

## 1. Tổng quan Kiến trúc Dự án Gốc (`api-vishop`)

Dự án gốc là một hệ thống **Modular Monolith** được tổ chức theo chuẩn **DDD (Domain-Driven Design) + CQRS** trong một **Nx Monorepo**:

* **Runtime & Framework**: Node.js 24, NestJS 11/12, Fastify 5, TypeScript 6.
* **Cơ sở dữ liệu**: **1 Database PostgreSQL duy nhất** dùng chung cho toàn bộ các module, quản lý bằng **Prisma 7 ORM** (`prisma/schema.prisma`).
* **Hạ tầng hỗ trợ**: Redis 8 (Pub/Sub, rate limiting, session caching), BullMQ (Queue nền & outbox relay).
* **4 Ứng dụng Runtime (Apps)**:
  1. `api`: Cổng HTTP REST (OpenAPI/Swagger), GraphQL (Mercurius) và WebSocket (Socket.io).
  2. `worker`: Xử lý job nền bất đồng bộ (sync TikTok Shop, tồn kho, xuất nhập file).
  3. `scheduler`: Chạy cron định kỳ (dọn dẹp phiên hết hạn, kiểm toán kho, tính toán chỉ số).
  4. `migration`: Chạy Prisma migration deploy và seed dữ liệu khởi tạo.

---

## 2. Mô hình Dữ liệu Đa người thuê & Phân quyền (Prisma Schema)

Trong `api-vishop`, vì toàn bộ bảng nằm chung trong 1 Database PostgreSQL, quan hệ giữa **User - Session - Tenant - Membership - Roles** được bảo vệ bằng các ràng buộc khóa ngoại (Foreign Keys) nguyên khối:

```mermaid
erDiagram
    User ||--o{ Session : "sở hữu nhiều phiên"
    User ||--o{ TenantMember : "tham gia nhiều tổ chức"
    Tenant ||--o{ TenantMember : "có nhiều thành viên"
    Tenant ||--o{ Shop : "sở hữu nhiều gian hàng"
    Tenant ||--o{ Role : "định nghĩa nhiều vai trò"
    Role ||--o{ RolePermission : "chứa danh sách quyền"
    TenantMember ||--o{ TenantMemberRole : "được gán vai trò"
    Role ||--o{ TenantMemberRole : "áp dụng cho"

    User {
        uuid id PK
        string email UK
        string role "USER | ADMIN"
        string status "ACTIVE | SUSPENDED"
        uuid lastTenantId FK "Tenant gần nhất"
        boolean mustChangePassword
        boolean twoFactorEnabled
    }

    Session {
        uuid id PK
        string token UK
        uuid userId FK
        uuid activeTenantId FK "Tenant đang làm việc của phiên"
        datetime expiresAt
    }

    Tenant {
        uuid id PK
        string slug UK
        string name "Tên doanh nghiệp"
        string status "ACTIVE | SUSPENDED"
        string baseCurrency "VND"
        string legalEntityType "COMPANY | HOUSEHOLD | INDIVIDUAL"
        string stockLockTiming "ON_CREATE | ON_PAYMENT"
        string oversellPolicy "BLOCK | BACKORDER"
    }

    TenantMember {
        uuid id PK
        uuid tenantId FK
        uuid userId FK
        string role "OWNER | ADMIN | MEMBER"
        string status "ACTIVE | INACTIVE"
    }

    Role {
        uuid id PK
        uuid tenantId FK
        string name
        string builtinKey "OWNER | OPERATIONS | WAREHOUSE | ACCOUNTING"
        boolean requireTwoFactor
    }

    RolePermission {
        uuid id PK
        uuid roleId FK
        string permissionKey "VD: order.view, product.create"
    }

    TenantMemberRole {
        uuid id PK
        uuid tenantMemberId FK
        uuid roleId FK
        uuid shopId FK "NULL = toàn tenant, UUID = đúng 1 shop"
        uuid groupId FK
    }
```

### Chi tiết các thực thể cốt lõi trong `prisma/schema.prisma`:

#### A. Thực thể `User` (`model User` — Dòng 14-73)
* Đại diện cho **Danh tính toàn cầu (Global Identity)**.
* **`lastTenantId`**: Lưu ID của Tenant mà user thao tác gần đây nhất. Khi tạo Session mới, giá trị này tự động điền vào `Session.activeTenantId` để người dùng không phải chọn lại shop sau mỗi lần đăng nhập.

#### B. Thực thể `Session` (`model Session` — Dòng 75-99)
* Quản lý phiên làm việc dạng Cookie Session (Better Auth).
* **`activeTenantId`**: Điểm mấu chốt giải quyết bài toán đa gian hàng:
  > *Ghi chú từ source code*: "Which tenant this session acts for. Kept on the session, not the user, so the same account can sit in two tenants in two browsers."
  > *(Tenant đang active được gắn trên Session chứ không gắn trên User. Hai browser/profile có session riêng có thể chọn tenant riêng. Hai tab dùng chung cookie vẫn chia sẻ session; muốn chọn tenant độc lập cần context theo tab/request và kiểm tra membership.)*

#### C. Thực thể `TenantMember` (`model TenantMember` — Dòng 460-479)
* Quan hệ nhiều-nhiều (N:M) giữa User và Tenant:
  * Ràng buộc: `@@unique([tenantId, userId], where: { deletedAt: null })`. Một người dùng chỉ có 1 bản ghi thành viên còn hoạt động trong một tổ chức.
  * Cấp vai trò: `role` (`OWNER`, `ADMIN`, `MEMBER`).

#### D. Thực thể `Role` & `RolePermission` (`model Role` — Dòng 481-522)
* **4 Vai trò mặc định (Built-in Roles)**:
  1. `OWNER`: Chủ tổ chức (toàn quyền trong tổ chức, không phải quản trị toàn nền tảng).
  2. `OPERATIONS`: Nhân viên vận hành (xử lý đơn, quản lý thông tin sản phẩm).
  3. `WAREHOUSE`: Thủ kho (quản lý tồn kho, đóng gói, quét mã xuất nhập kho).
  4. `ACCOUNTING`: Kế toán (đối soát doanh thu sàn TMĐT, xem sao kê tài chính).
* **Custom Roles**: Cho phép Chủ shop tự tạo vai trò mới và chọn danh mục quyền tùy ý lưu tại bảng `role_permissions (roleId, permissionKey)`.

---

## 3. Cơ chế Giải quyết Ngữ cảnh (Tenant Context Resolution)

Trong `api-vishop`, module [libs/infra/tenancy](file:///C:/Users/ndquynh/Documents/vi-shop/api-vishop/libs/infra/tenancy) chịu trách nhiệm tự động xác định người dùng đang thao tác trên Tenant nào:

```mermaid
flowchart TD
    Req["Incoming HTTP Request"] --> Guard["TenantContextGuard (NestJS)"]
    Guard --> Extract["1. Lấy userId từ Session\n2. Đọc Header 'x-tenant-id' (nếu có)\n3. Đọc session.activeTenantId"]
    Extract --> Resolve["TenantContextService.resolve()"]
    
    Resolve --> QueryDB["SELECT * FROM tenant_members\nWHERE userId = :userId AND tenant.status = 'ACTIVE'"]
    
    QueryDB --> CheckMatch{"Kiểm tra logic"}
    CheckMatch -- "Không thuộc tenant nào" --> Err1["Ném lỗi: NO_TENANT (403)"]
    CheckMatch -- "Có Header x-tenant-id & Hợp lệ" --> MakeCtx["Xây dựng TenantContext"]
    CheckMatch -- "Không Header + Có session.activeTenantId" --> MakeCtx
    CheckMatch -- "Thuộc > 1 tenant mà chưa chọn" --> Err2["Ném lỗi: TENANT_AMBIGUOUS (400)\n(Frontend phải hiện màn hình chọn Shop)"]
    
    MakeCtx --> Attach["Gắn request.tenant = {\n  tenantId,\n  tenantName,\n  role,\n  permissions: Set<string>,\n  shopScope\n}"]
    Attach --> Next["Chuyển tiếp sang Controller"]
```

### Mã nguồn xử lý lõi tại `TenantContextService`:
```typescript
// Trích xuất từ libs/infra/tenancy/src/lib/tenant-context.service.ts
async resolve(userId: string, requestedTenantId?: string, sessionTenantId?: string | null): Promise<TenantContext> {
  const memberships = await this.listMemberships(userId);
  if (memberships.length === 0) throw this.noTenant();

  // Ưu tiên 1: Client truyền tường minh qua header x-tenant-id
  if (requestedTenantId) {
    const match = memberships.find((m) => m.tenantId === requestedTenantId);
    if (!match) throw this.noTenant();
    return this.toContext(match);
  }

  // Ưu tiên 2: Lấy theo activeTenantId của phiên Session hiện tại
  if (sessionTenantId) {
    const match = memberships.find((m) => m.tenantId === sessionTenantId);
    if (match) return this.toContext(match);
  }

  // Nếu user thuộc nhiều tenant nhưng chưa chỉ định -> Bắt buộc chọn
  if (memberships.length > 1) throw this.tenantAmbiguous();

  return this.toContext(memberships[0]);
}
```

---

## 4. Cơ chế Phân quyền (RBAC & Permission Guard)

Hệ thống tuân thủ nguyên tắc: **Backend kiểm tra Permission tĩnh, Giao diện quản lý theo Role**.

* **Bộ lọc phân quyền (`PermissionGuard`)**:
  ```typescript
  // libs/infra/tenancy/src/lib/permission.guard.ts
  @Injectable()
  export class PermissionGuard implements CanActivate {
    canActivate(context: ExecutionContext): boolean {
      const required = this.reflector.get<string>(PERMISSION_KEY, context.getHandler());
      const { tenant } = request;
      
      // Role OWNER tự động bypass mọi kiểm tra quyền
      if (tenant.role === TENANT_ROLES.OWNER) return true;
      
      // Kiểm tra xem Set<string> permissions có chứa quyền này không
      return tenant.permissions.has(required);
    }
  }
  ```
* **Khai báo trên Controller API**:
  ```typescript
  @RequirePermission('order.package.ship')
  @Post(':id/ship')
  async shipOrder(@Param('id') orderId: string, @CurrentTenant() tenant: TenantContext) {
    ...
  }
  ```

---

## 5. Bảng Đối chiếu Ánh xạ sang Java Spring Boot (`vishop-platform`)

Dưới đây là bảng đối chiếu trực tiếp giữa giải pháp trong dự án gốc NestJS và giải pháp đang triển khai trên Java Spring Boot Microservices:

| Thành phần kiến trúc | Dự án gốc: `api-vishop` (NestJS) | Dự án đích: `vishop-platform` (Java Microservices) |
| :--- | :--- | :--- |
| **Kiểu kiến trúc** | Modular Monolith (1 repo, 1 database). | Các runtime hiện tại: `iam-service`, `api-gateway`, `audit-service`; nghiệp vụ khác có bounded context riêng. |
| **ORM / Migration** | Prisma 7 (`prisma/schema.prisma`). | Toàn dự án dùng Spring Data JPA/EntityManager; Hibernate chỉ validate mapping, Flyway SQL quản lý schema. |
| **Cơ chế Xác thực** | Better Auth Cookie Session (`better-auth.session_token`). | Stateless JWT access/refresh tokens; local access verification and refresh-time grant checks. |
| **Tenant Context Ingestion** | NestJS `TenantContextGuard` truy vấn DB `tenant_members` in-flight. | Gateway xác minh access JWT tại chỗ và chuyển tiếp JWT nguyên vẹn; metadata tenant lấy từ claims đã xác minh. |
| **Quản lý Context trong Service** | `request.tenant` (Fastify Request property). | [TenantContext.java](../../libs/common-security/src/main/java/io/github/coreplatform/common/security/context/TenantContext.java) (`ThreadLocal<UUID>`) tự động dọn dẹp ở `finally`. |
| **Bảo vệ API / Kiểm tra Quyền** | Decorator `@RequirePermission('key')` + `PermissionGuard`. | IAM kiểm tra membership/permission hiện tại trong application service; downstream dùng Spring method security cho tenant permissions. |
| **Nhận diện Người dùng** | Decorator `@CurrentUser()`. | IAM dùng `@AuthenticationPrincipal IamPrincipal`; downstream dùng `CommonUserDetails`. |
| **Transactional Outbox** | Bảng `outbox_events` + BullMQ Relay. | IAM ghi EventEnvelope vào outbox trong transaction và relay Kafka sau commit; các service JPA dùng common-persistence. |
| **Xử lý 1 User - Nhiều Tenant** | Nằm chung 1 DB PostgreSQL, `JOIN` trực tiếp Prisma. | Runtime **`iam-service`** dùng membership N:M và system role độc lập trong `vishop_iam`; hai service cũ đã ngừng tham gia build/deploy. |

---

## 6. Bài học & Quy tắc Kế thừa quan trọng cho `vishop-platform`

1. **Đối chiếu danh mục quyền tĩnh (`CommonPermissions`)**:
   * Java [CommonPermissions.java](../../libs/common-security/src/main/java/io/github/coreplatform/common/security/constants/CommonPermissions.java) hiện có **34** key. Schema IAM dùng danh mục này và có kiểm thử so sánh quyền OWNER với code. Cần tiếp tục đối chiếu với source gốc trước khi khẳng định tương đương đầy đủ; con số 75 trong bản mô tả trước chưa khớp code hiện tại.
2. **Kế thừa 4 Role chuẩn (`OWNER`, `OPERATIONS`, `WAREHOUSE`, `ACCOUNTING`)**:
   * Khi khởi tạo Tenant mới, hệ thống tự động sinh 4 vai trò này để người dùng có thể sử dụng ngay mà không cần cấu hình thủ công.
3. **Tenant context trong JWT thay cho state đăng nhập của nguồn**:
   * Token phản ánh **tổ chức đang chọn**. `POST /api/v1/auth/switch-tenant` nhận `{tenantId}` cùng cookie HttpOnly, kiểm tra membership rồi thay cookie JWT cho tenant đã chọn; JSON chỉ chứa user/tenant grants. Đổi gian hàng trong cùng tổ chức không nhất thiết đổi tenant/token.

## 7. Hành vi bảo mật hiện tại

* Runtime IAM đã quản lý global user, system role, membership N:M, invitation và JWT access/refresh; xem [IAM README](../../services/iam-service/README.md).
* Public registration tạo global user với system role USER, không nhận tenant/role từ client. API tạo tenant ghi tenant, bốn role và OWNER membership trong cùng transaction.
* Gateway xóa header danh tính/tenant và khoá nội bộ do client gửi, xác minh access JWT tại chỗ và chuyển tiếp JWT nguyên vẹn. Downstream tự verify JWT; không dựng principal từ header client.
* Tenant OWNER chỉ có tenant permissions. API CMS kiểm tra system role PLATFORM_ADMIN; system admin không mặc nhiên là thành viên mọi tenant. Không có bảng permissions cho system role.
* IAM, gateway và downstream dùng JWT_SECRET (ít nhất 32 byte). IAM_INTERNAL_SECRET chỉ bảo vệ introspection tương thích nội bộ; gateway không introspect IAM và không dùng cookie đăng nhập.
Current IAM logout is stateless: clients discard tokens, which remain valid until expiry. See [security architecture](security-architecture.md).
* Source đã chuyển runtime/configuration. Việc import dữ liệu từ database cũ và chuyển traffic trên hạ tầng đang chạy vẫn phải làm theo hướng dẫn cutover.
