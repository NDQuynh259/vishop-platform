# Fork guide: running and understanding the codebase

> This guide describes the historical Fastify/Better Auth source. Current Java IAM uses
> stateless JWT access/refresh tokens. See [IAM README](../services/iam-service/README.md)
> for the active API and authentication contract.


This is the shortest path for a developer who forks the repository and needs to
understand the deployable services, API surfaces, and code ownership.

## Why are there four production env files?

Local development only needs one shared `.env`. Production separates runtime
configuration into:

| File             | Process             | Main credential scope                          |
| ---------------- | ------------------- | ---------------------------------------------- |
| `.env.api`       | Fastify API         | JWT auth, S3, CORS, API database role      |
| `.env.worker`    | BullMQ worker       | queues, mail, S3, ClamAV, worker database role |
| `.env.scheduler` | cron + outbox relay | queue/cache, scheduler database role           |
| `.env.migration` | Prisma migration    | migration/admin database role                  |

This reduces the blast radius: the API does not automatically receive SMTP
credentials, the worker cannot migrate or alter the schema, the scheduler does
not receive API credentials, and the migration credential is not present in a
request-serving process. These files are ignored by Git; commit only
`.env.example`, never real values.

Local forks need only `.env` because all services use local development
credentials. For production, create the four files as described in
[deployment.md](deployment.md).

## Quick start

```bash
cp .env.example .env
pnpm install
docker compose -f docker/compose.yml -f docker/compose.dev.yml up -d
pnpm db:migrate
pnpm nx serve api
```

The API runs at `http://localhost:3000` by default.

## Endpoint map

`/api` is the global prefix and `/v1` is the default URI version. Errors use
`application/problem+json` and include `X-Request-Id`.

### REST

| Method | Endpoint                      | Auth              | Purpose                            |
| ------ | ----------------------------- | ----------------- | ---------------------------------- |
| `GET`  | `/`                           | public            | service name and version           |
| `GET`  | `/api/v1/health/live`         | public            | liveness probe                     |
| `GET`  | `/api/v1/health/ready`        | public            | readiness: PostgreSQL + Redis      |
| `GET`  | `/api/v1/health`              | public            | aggregate health check             |
| `GET`  | `/api/v1/health/dependencies` | metrics allowlist | BullMQ, PgBouncer, and replica lag |
| `GET`  | `/metrics`                    | metrics allowlist | Prometheus metrics                 |
| `GET`  | `/api/v1/users/me`            | Bearer access JWT | current user profile               |
| `POST` | `/api/v1/upload/presign`      | Bearer access JWT | issue a presigned POST policy      |
| `POST` | `/api/v1/upload/confirm`      | Bearer access JWT | confirm an upload                  |

Upload flow:

```text
POST /api/v1/upload/presign { "contentType": "image/png" }
  -> browser POSTs multipart data directly to S3/MinIO
  -> POST /api/v1/upload/confirm { "key": "uploads/<user>/<file>.png" }
  -> VERIFYING -> worker checks magic bytes + ClamAV -> READY
```

A download URL is returned only after the file reaches `READY`.

### Authentication hiện tại (Java IAM)

Xác thực dùng JWT stateless với access token và refresh token trong cookie HttpOnly; JSON chỉ trả user/tenant grants.
Browser gửi cookie; gateway chuyển JWT đã xác minh qua Bearer tới downstream. Hai bên xác minh
chữ ký, issuer, audience, loại token và thời hạn tại chỗ. Không lưu session đăng nhập,
không dùng Redis projection. Gateway không gọi IAM introspection
trên mỗi request.

`POST /api/v1/auth/refresh` đọc refresh cookie, không nhận body, hoạt động cả khi access token
đã hết hạn. IAM kiểm tra tài khoản và quyền hiện tại trước khi cấp cặp token mới. Thời hạn
mặc định: access 15 phút, refresh 7 ngày; refresh giữ thời điểm hết hạn tuyệt đối ban đầu.
Token refresh cũ còn hiệu lực đến hết hạn; không có cơ chế lưu trạng thái rotation/reuse.

Logout xóa cả hai cookie, ghi audit khi có danh tính hợp lệ và trả 204. JWT đã cấp vẫn hợp lệ đến hết hạn;
không cam kết thu hồi tức thời. Tenant context lấy từ JWT đã xác minh; đổi tenant qua
`POST /api/v1/auth/switch-tenant` với cookie và `{tenantId}`, rồi nhận cookie mới.
Frontend dùng `credentials: 'include'` và `X-Requested-With: XMLHttpRequest` cho các request ghi.
Header `X-Tenant-Id` từ client không cấp hoặc đổi quyền tenant.

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/switch-tenant
POST /api/v1/auth/logout
GET  /api/v1/auth/me
```

Các API GraphQL, WebSocket và module TypeScript bên dưới là tham khảo nguồn,
không phải runtime Java đang triển khai.

### GraphQL

Endpoint: `POST /graphql`.

```graphql
query {
  me {
    id
    email
    name
    role
    status
  }
}
```

The `users(...)` query requires the `ADMIN` role. Introspection is disabled in
production.

### WebSocket

Java runtime hiện tại chưa cung cấp WebSocket auth adapter. Khi port luồng này, dùng access JWT được xác minh ở handshake và khi cần revalidation; không dùng cookie đăng nhập. Không coi đường dẫn `/ws` của nguồn là endpoint Java đã triển khai.

## Where to find the code

| Concern                                     | Location               |
| ------------------------------------------- | ---------------------- |
| HTTP bootstrap, CORS, Helmet, rate limiting | `apps/api/src/main.ts` |
| Authentication and role guards (source only)   | `libs/infra/auth`      |
| User use cases and repositories             | `libs/modules/users`   |
| Upload presign and confirmation             | `libs/modules/upload`  |
| Prisma schema and migrations                | `prisma/`              |
| Queue processing                            | `apps/worker`          |
| Cron jobs and outbox relay                  | `apps/scheduler`       |
| Database, Redis, and S3 adapters            | `libs/infra`           |
| DTOs, errors, and OpenAPI contracts         | `libs/contracts`       |
| Generated REST client                       | `libs/api-client`      |

After changing a DTO or controller, run:

```bash
pnpm codegen:full
pnpm typecheck:all
pnpm nx run-many -t lint --all
pnpm test -- --run
```
