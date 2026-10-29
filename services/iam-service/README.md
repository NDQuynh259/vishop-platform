# IAM service

`iam-service` combines identity, system access, tenants and tenant access on PostgreSQL
`vishop_iam`. It replaces the old identity/tenant runtimes in Gradle, gateway routes and
Docker Compose. The old service sources and Dockerfiles have been removed; use Git
history to reference their original schemas during data migration.

## Identity and access

* `users.system_role`: `USER` or `PLATFORM_ADMIN`, used only for CMS. No system-role
  permission tables exist. Public registration always creates a global `USER` account.
* Tenant permissions come from `tenant_members.role_id → roles → role_permissions`.
  A user can belong to several tenants with different roles. System administrators
  do not automatically have tenant membership; tenant owners do not get CMS access.
* Creating a tenant creates its four default roles and OWNER membership in one database
  transaction. The OWNER role has all 36 current `CommonPermissions` keys.
* Composite foreign keys prevent cross-tenant role assignment. Repository SQL includes
  explicit tenant predicates. The schema does not enable RLS.
* Tenant mutation transactions lock the tenant row. Concurrent changes cannot demote or
  deactivate the last active owner. Non-owners cannot manage owner memberships.
* A caller cannot create/assign a role with grants they do not hold. Built-in OWNER is
  immutable. Custom role names never confer system authority.

## Code structure

```text
com/vishop/iam/
|-- modules/
|   |-- identity/  # Users, passwords/JWTs, system roles and user CMS
|   `-- tenant/    # Tenant lifecycle, memberships, roles/permissions and invitations
|       |-- domain/          # Tenant, Membership, Role, Invitation, policies and ports
|       |-- application/
|       |   |-- api/         # TenantApi and its package-private implementation
|       |   |-- command/    # tenant/, member/, role/, invitation/
|       |   |-- query/      # tenant/, member/, role/
|       |   `-- dto/port/service/mapper/
|       |-- infrastructure/ # Owning JPA entities/repositories and integration adapters
|       `-- presentation/   # Tenant, CMS, member, role and invitation endpoints
|-- composition/
|   |-- authentication/ # Implements identity's outbound port using TenantApi
|   `-- members/        # User + tenant member/detail/active-owner read projections
|-- infra/              # HTTP security/CORS, OpenAPI, relay and migration CLI
`-- shared/             # Framework-independent actor/event contracts and ports
```

Tenant and access are one module: there is no separate `modules/access` or `AccessApi`.
Tenant owns `tenants`, `tenant_members`, `roles`, `role_permissions` and
`tenant_invitations`. Identity owns `users`, password/JWT adapters and system roles.
Tenant-scoped permissions do not grant `PLATFORM_ADMIN`; system administrators do not
implicitly gain tenant membership.

Tenant use cases and HTTP entry points stay in tenant. `TenantController` handles
creation, listing, detail and rename; tenant CMS, membership, role and invitation
controllers also live in this module. Commands are grouped into `tenant`, `member`,
`role` and `invitation` packages. Tenant creation and owner initialization share one
transaction with default roles and outbox events; tenant locking, final-owner and
role-assignment rules retain their existing behavior.

CMS updates a tenant through `PUT /api/v1/cms/tenants/{id}` with the complete mutable
representation, for example `{"name":"Updated tenant","status":"SUSPENDED"}`.
Both fields are required; statuses are `ACTIVE`, `SUSPENDED` and `ARCHIVED`.
Only an access JWT carrying system role `PLATFORM_ADMIN` can use this endpoint. `id`, `code` and
`createdAt` remain immutable. Name and status updates share one transaction and
outbox publication; repeating an unchanged update emits no additional domain events.
Archived tenants cannot be reactivated. This replaces the tenant status-only PATCH endpoint.
Each endpoint in `TenantCmsController` declares `@PreAuthorize` using
`iamAuthorization.canAccessCms(authentication)`, which checks the verified JWT
principal without database or IAM calls. Handlers retain the same claims-based
authorization for non-HTTP callers.

`TenantApi` replaces the former tenant/access facades. Its implementation is
package-private and keeps mutation APIs inside the caller's transaction. `Grant`
represents tenant role/permissions. The tenant-owned `MemberReadPort` groups user/member
projections and active-owner counting; composition implements it without exposing
identity repositories or entities to tenant.

Identity depends only on its own `IdentityApi.TenantAccessPort`. The adapter in
`composition/authentication` implements that port through `TenantApi`, so tenant can
check `IdentityApi` without introducing a module dependency cycle. Calls are local Java
calls within IAM's datasource/transaction manager, not HTTP requests. The service-wide
security filter uses `IdentityApi.authenticate`.

The tenant list uses the actual owning `TenantJpaEntity` and `MembershipJpaEntity`
with a correlated JPA `EXISTS`, Spring Data specifications, sort and offset/keyset
pagination. No intermediate ID query or duplicate tenant/membership read-model entities
are needed. Token issuance obtains active tenant IDs through a tenant-owned JPQL query.
Only reads that still combine user data with tenant data remain in composition's JPA
member projection. All writes stay in the owning module.

Public contracts are `IdentityApi`, `TenantApi`, their nested ports/value types, and
explicit immutable DTOs (`UserView`, `TenantResponseDto`, `MemberView`,
`TenantMemberResponseDto`). Modules do not import composition or another module's
internal entities, repositories or implementations. `IamArchitectureTest` enforces
these boundaries, acyclic dependencies, table ownership and absence of the old access
module. Domain and shared packages remain framework-independent.

Database tables, HTTP paths, JSON contracts, JWT cookies, pagination/filter/sort,
transaction boundaries and outbox events are preserved. This merge requires no schema
migration. Flyway still owns schema changes; JPA only validates mappings.

## List the current user's tenants

`GET /api/v1/tenants` is handled by `TenantController` through
`ListMyTenantsQuery`. The user ID comes from the authenticated principal, not a request
parameter. It returns active tenants in which that user has an active membership,
ordered by creation time and ID by default. Use `GET /api/v1/tenants?page=1&limit=20`; the endpoint uses
the shared `PaginationRequest` normalization (default page 1, limit 20, maximum 100).
Offset pagination is the default. Use `paginationMode=cursor` for the first cursor
page; supplying a nonblank `cursor` automatically selects cursor mode and takes
precedence over `page`. Other `paginationMode` values return 400.

The response is the system `ListEnvelope`: `{object, url, data, pageInfo}`. Each item
in `data` contains `{id, code, name, status, createdAt}`. In offset mode, `pageInfo`
contains `page`, `limit`, `total`, `totalPages`, and `hasNextPage`. The database filters
by the current user and active statuses before applying LIMIT/OFFSET; the count uses
the same filters. Cursor mode returns `hasNextPage` and, when another page exists,
`nextCursor`; it does not count totals or return page numbers.

```http
GET /api/v1/tenants?paginationMode=cursor&limit=20&name=shop&sort=createdAt:desc
GET /api/v1/tenants?cursor={nextCursor}&limit=20&name=shop&sort=createdAt:desc
```

Keep the same filter and sort parameters on each cursor request. The token is bound
to the authenticated user and normalized criteria; a malformed token or a mismatch
returns 400. The page size may change. Tokens preserve the exact last sort value and
ID, including microsecond timestamps, and remain usable if the anchor row is deleted.
Changing a sort value while browsing can move a row across the cursor boundary;
separate HTTP requests do not share a database snapshot.

The handler passes the authenticated user ID directly to `TenantReadPort`.
`TenantReadAdapter` uses `SpringDataTenantRepository` / `JpaSpecificationExecutor` with
`Specification`, `PageRequest`, and `Sort` for offset pages, or Spring Data keyset
scrolling (`ScrollPosition` / `Window`) for cursor pages. A correlated JPA `EXISTS` checks the current
user's active membership inside the tenant query; no intermediate tenant ID list is
loaded into Java. The same predicate is used for the count query when needed.
The query uses the owning `TenantJpaEntity` and `MembershipJpaEntity` inside the
merged tenant module. It adds no table or migration. Text searches use case-normalized
JPA `LIKE` with escaped wildcard characters.

Optional field filters are combined with AND:

| Parameter | Match |
| --- | --- |
| `id` | Exact tenant UUID |
| `code` | Case-insensitive contains; trimmed, maximum 50 characters |
| `name` | Case-insensitive contains; trimmed, maximum 200 characters |
| `status` | Exact `ACTIVE`, `SUSPENDED`, or `ARCHIVED`; inactive tenants remain excluded, so requesting another status gives no matches |
| `createdFrom` | Inclusive lower bound on `createdAt`, ISO-8601 instant |
| `createdTo` | Exclusive upper bound on `createdAt`, ISO-8601 instant |

Blank text filters are ignored. `%`, `_`, and `!` in text filters are literal
characters, not wildcard syntax. When both date bounds are present, `createdFrom`
must be before `createdTo`.

Use `sort=field:direction`, for example `sort=createdAt:desc`. Fields are
`id`, `code`, `name`, `status`, or `createdAt`; directions are `asc` or `desc`.
The default is `createdAt:asc`; ties use `id asc` so page boundaries remain deterministic for an
unchanged dataset. Only the allowlisted fields and directions are accepted; invalid
sort/filter values return 400. Text values and date/ID filters are bound parameters.

```http
GET /api/v1/tenants?page=1&limit=20&name=shop&sort=createdAt:desc
```

Client-supplied user IDs or identity headers cannot select another user's tenants.
System admins also receive only their own memberships on this endpoint.
The caller must still be active in the database. Missing/invalid credentials return
401. A user without active tenants receives empty `data`, with total 0 in offset
mode or `hasNextPage: false` in cursor mode. A page beyond the last offset page also
has empty `data` while retaining the matching total. Active user and membership
checks are repeated for every request, including cursor requests.
Tenant and membership reads share a read-only REPEATABLE READ transaction.

The list returns tenant information rather than the old membership projection with
`roleId` and `role`. Tenant editing continues through `PATCH /api/v1/tenants/{id}`
with `{"name":"New name"}`, requiring active membership and `tenant.manage`.

## Cross-module member read projection

* `GET /api/v1/tenants/{tenantId}/members/me` returns the caller's membership.
* `GET /api/v1/tenants/{tenantId}/members/{userId}` returns the specified membership.

The response contains `id` (membership ID), `tenantId`, `userId`, `email`, `fullName`,
`roleId`, `role`, membership `status`, and the assigned role's `permissions` array.
Built-in roles use their key; custom roles use their name. Inspecting another user
requires `tenant.member.view` in the JWT. Reading yourself requires a tenant-scoped
JWT. Both routes require the URL tenant to match the JWT tenant and return
`Cache-Control: no-store`.
Authorized managers can inspect inactive memberships; their assigned permissions do
not imply that the member can currently exercise them. Missing target memberships
return 404 after authorization; callers without tenant access receive 403.

```text
TenantMemberController
  -> GetTenantMemberQueryHandler
       -> TenantApi.requireAccess (authorization)
       -> TenantApi.MemberReadPort
            -> JpaTenantMemberReadAdapter (EntityManager native query)
```

The adapter uses JPA `EntityManager` with a parameterized native query and maps its
`Tuple` result directly to the response DTO, without loading a managed entity graph.
It joins `users`, `tenants`, `tenant_members`, `roles` and `role_permissions`
within the IAM datasource. It aggregates grants into one result; a LEFT JOIN preserves
roles with no permissions. Joins use tenant/role/user IDs, never role names. Authorization
and projection share a read-only REPEATABLE READ transaction. The handler imports only
public module contracts and its own read port, not module entities or repositories.
The returned membership and grants are current database data, not a replacement for
the request's signed authorization claims. Authorization uses only the JWT snapshot;
the requested tenant must match its selected tenant. Switch tenant before accessing
another tenant, even when the user belongs to both.

This adapter depends on the local IAM schema, so migrations must preserve the projection
or update it alongside the schema. It performs no writes and does not query other
microservices' databases. Cross-service aggregation requires APIs or an event-fed read model.

## Authentication

Login, registration, refresh and tenant switching set HttpOnly cookies
`vishop_access_token` (Path `/`) and `vishop_refresh_token` (Path `/api/v1/auth`). JSON
contains only `user`, `tenantId`, `tenantRole`, and `permissions`, never tokens. No server-side
session is created. Access lifetime defaults to 15 minutes (`JWT_ACCESS_EXPIRATION_MS`),
refresh lifetime to seven days (`JWT_REFRESH_EXPIRATION_MS`).

Both settings use milliseconds and are passed through the dev/prod Compose configuration.
Set them in the deployment env file or the IAM process environment, then restart IAM:

```dotenv
JWT_ACCESS_EXPIRATION_MS=900000
JWT_REFRESH_EXPIRATION_MS=604800000
```

Refresh lifetime must exceed access lifetime. New settings affect subsequently issued
tokens; existing JWTs retain their signed expiry. Cookie lifetimes follow the access lifetime
and remaining absolute refresh expiry. JavaScript does not read either token.

Browsers authenticate with the access cookie. Gateway converts verified access to
`Authorization: Bearer <accessToken>` for downstream services and strips browser cookies
from business requests; only IAM auth routes receive refresh cookies. Gateway and services
verify signature, issuer, audience, token use and expiry locally. JWTs have no `sid`,
session version or Redis projection. Identity routes allow a null tenant; business APIs
require a tenant context. Controllers and handlers authorize using signed `systemRole`,
`tenantRole` and `permissions` claims only; they do not query IAM, a database or Redis
to reconstruct the caller's grants. Permission authorities remain separate from roles.
Custom tenant roles use `CUSTOM:{roleId}` in the token so a role named `OWNER` cannot
impersonate the built-in owner. Membership projection responses still expose display names.

Role/permission changes, membership removal, account suspension and logout do not
revoke previously issued access JWTs. Existing access remains valid until expiry
(15 minutes by default). Login, refresh and tenant switching read current account,
tenant, membership and MFA requirements before issuing new claims. Removed members
and suspended accounts cannot refresh their old grants. No session, denylist or
per-request IAM introspection is used.

Stateless authorization does not remove business persistence: profile and membership
reads return current data; tenant/member/role writes still load and lock their targets,
enforce final-owner/final-admin and grant-assignment rules, and write outbox events.
Invitation acceptance also validates the invitation, recipient email and current issuer
grants as part of redeeming that stored invitation, not as request authentication.

`POST /api/v1/auth/refresh` has no body and reads the HttpOnly refresh cookie, without requiring
a valid access token. It validates the refresh JWT and current account/tenant grants, then sets
new cookies. The refreshed token retains the original absolute refresh expiry, so refreshing
does not extend login indefinitely. The previous refresh JWT remains valid until expiry;
there is no server-side rotation/reuse tracking in this fully stateless model.

`POST /api/v1/auth/switch-tenant` accepts only `{ "tenantId": "..." }` and reads access/refresh
cookies. Both tokens must belong to the same user. New cookies carry the selected tenant.
Tabs in the same browser share these cookies and the current tenant context.

Login prefers `users.last_tenant_id` when the user still has ACTIVE membership in an
ACTIVE tenant. Otherwise, exactly one active membership is selected automatically;
zero or multiple memberships return `tenantId: null`. The frontend can use
`GET /api/v1/tenants` to distinguish `/no-tenant` from `/select-tenant`.
Successful login selection and tenant switching persist the preference; stale preferences
are replaced or cleared on login. Token issuance still validates current access and MFA
requirements before saving a selection. Refresh keeps the tenant in its own token and
does not change the remembered preference. Other browser/device cookie jars remain independent.
This adds no IAM lookup to gateway authentication on business requests.

Logout clears both cookie paths and returns 204, even when access has expired. A valid
authenticated identity also produces an audit event. Without a
revocation store, already issued JWTs remain usable until expiry. User/role/tenant changes
are reflected when tokens are issued again; IAM domain authorization also checks live data.
No Redis or Kafka security projection is required to authenticate requests.

Browser clients must send `credentials: 'include'`. All unsafe API methods require
`X-Requested-With: XMLHttpRequest`, including login/register/refresh/logout; requests with
an Origin outside exact `ALLOWED_ORIGINS` are rejected. CORS allows credentials for that
allowlist only. Cookies are host-only with SameSite=Lax by default. Local HTTP uses
`AUTH_COOKIE_SECURE=false`; production Compose defaults to true. `AUTH_COOKIE_SAME_SITE=None`
is supported only with Secure cookies. Scalar `/docs` supplies credentials and the header.

```javascript
await fetch('http://localhost:8000/api/v1/auth/login', {
  method: 'POST', credentials: 'include',
  headers: {'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest'},
  body: JSON.stringify({email: 'user@example.com', password: 'StrongPass123!'})
});
```

## APIs

All public endpoints below have prefix `/api/v1`.

The OpenAPI 3 JSON specification is available at `/v3/api-docs`, or through gateway at
`/v3/api-docs/iam-service`. The runtime uses the springdoc API-only starter without Swagger UI.

| Method/path | Behavior |
| --- | --- |
| `POST /auth/register` | `{email,password}` → global USER account + HttpOnly cookies and user/grants JSON; other fields return 400; fullName starts null |
| `POST /auth/login` | `{email,password}` → HttpOnly cookies and user/grants JSON |
| `GET /auth/me` | Global profile including systemRole; never returns a password hash |
| `POST /auth/refresh` | No body; refresh cookie → new HttpOnly cookies and current grants |
| `POST /auth/switch-tenant` | `{tenantId}` + auth cookies → new HttpOnly cookies and selected tenant grants |
| `POST /auth/logout` | Clear both cookies; audit authenticated logout |
| `GET /tenants` | Active tenants the caller belongs to |
| `POST /tenants` | `{code,name}` → tenant + four roles + OWNER membership |
| `GET /tenants/{id}` | Member with tenant.view reads the tenant |
| `PATCH /tenants/{id}` | `{name}`; requires tenant.manage |
| `GET /tenants/{id}/members` | Requires tenant.member.view |
| `PUT /tenants/{id}/members/{userId}/role` | `{roleId}`; requires tenant.member.role_assign |
| `DELETE /tenants/{id}/members/{userId}` | Deactivate membership; requires tenant.member.remove |
| `GET /tenants/{id}/roles` | Tenant role catalogue; requires tenant.member.view |
| `POST /tenants/{id}/roles` | `{name,permissions}`; create custom role |
| `PUT /tenants/{id}/roles/{roleId}` | `{name,permissions}`; edit grants with delegation checks |
| `POST /tenants/{id}/invitations` | `{email,roleId}` → one-time raw invitation token |
| `POST /invitations/accept` | `{token}` → membership; reactivates an inactive membership |
| `GET /cms/tenants`, `GET /cms/users` | PLATFORM_ADMIN only; bounded limit/offset pagination |
| `PATCH /cms/tenants/{id}/status` | `{status}`; PLATFORM_ADMIN only |
| `PATCH /cms/users/{id}/system-role` | `{systemRole}`; protects the last active platform administrator |
| `PATCH /cms/users/{id}/status` | `{status}`; locks/suspends global accounts |

CMS tenant status management has moved from the old `/tenants/{id}/status` endpoint.

Registration accepts exactly `email` and `password`. Email normalization, uniqueness and
password policy still apply. `fullName` is optional profile data and starts as null;
V7 makes `users.full_name` nullable while preserving existing names. Extra registration
fields are rejected with 400, and missing/invalid required fields follow the existing
validation response (422).
Responses expose `systemRole` on the user; tenantId, tenantRole and permissions are separate
fields of the auth response. The old per-user tenantId/role contract is retired.

The internal introspection endpoint has been removed. Gateway and downstream services
verify access JWTs locally; no separate IAM internal secret is required.

## Run and verify

Set a random `JWT_SECRET` (at least 32 bytes),
database credentials, and `ALLOWED_ORIGINS` (comma-separated exact origins). IAM signs JWTs;
downstream services and gateway verify with the same JWT secret.

```powershell
.\gradlew.bat :services:iam-service:bootRun
.\gradlew.bat test :services:iam-service:bootJar :services:api-gateway:bootJar
```

Authentication uses Bearer JWTs. No system administrator is seeded:
after registering the initial operator, promote that specific account through a trusted
database connection, then use the CMS APIs. Never promote legacy tenant roles automatically.

Tests start isolated real PostgreSQL 16 processes and run Flyway migrations, HTTP auth/RBAC,
invitation, stateless refresh/logout, concurrent owner updates and outbox acknowledgement/failure checks.
They do not require Docker or connect to an existing database. First execution downloads
embedded PostgreSQL binaries from Maven Central. Use a non-root user on Linux.

## Database preparation and legacy cutover

The schema contains users, tenants, roles, role_permissions, tenant_members,
tenant_invitations, outbox_events and consumed_messages. Creating a new tenant
seeds built-in roles in its transaction. An application transaction creates its owner.
Future permission catalogue changes need a new migration updating the check constraint,
default-role trigger and existing built-in grants.

To prepare an isolated local DB, set `IAM_DB_PASSWORD` and optionally IAM_DB_USER/PORT:

```powershell
docker compose -f infra/compose/docker-compose.iam-db.yml up -d iam-postgres
docker compose -f infra/compose/docker-compose.iam-db.yml run --rm iam-migrate
```

It creates `vishop_iam` on `127.0.0.1:5433` by default. For an existing empty IAM DB,
set IAM_DB_URL (a JDBC URL), IAM_DB_USER and IAM_DB_PASSWORD, then run:

```powershell
.\gradlew.bat :services:iam-service:migrateIamDb
```

Flyway does not clean or auto-baseline a non-empty schema. The shared PostgreSQL init
script creates IAM/audit databases only on fresh volumes. Existing volumes need IAM
database creation through an authorized connection; do not reset their data volumes.

For deployments with legacy data, complete the data cutover before starting the new
runtime against that dataset:

1. Stop legacy writers/relays and back up both old databases. Export stable snapshots.
2. Migrate the new IAM DB. Import tenant IDs, codes, names, statuses and timestamps first.
3. Import original user IDs/password hashes as global users with system_role=USER.
4. Create memberships from legacy users.tenant_id. Report orphan tenant IDs and duplicate
   case-insensitive emails for correction; do not invent organizations or silently merge users.
5. Review old OWNER/ADMIN/STAFF grant mappings. Preserve appropriate grants using custom
   roles; confirm OWNER grants and at least one active owner per active tenant.
6. Preserve pending outbox events and consumed-message IDs. Verify counts, grants and IDs.
7. Rotate old signing credentials, reauthenticate clients, and start IAM/gateway/audit.
   Decommission the old containers; retain database backups and Git history for rollback.

This change switches source/deployment configuration; it does not copy existing databases
or deploy running infrastructure. Do not run the old Flyway V1 files against vishop_iam.

Outbox events use the common EventEnvelope format on vishop.user.events and
vishop.tenant.events. Delivery is at least once with row locking and acknowledgement
before marking published. Failures retry up to five attempts; operators can investigate
FAILED rows and requeue them after recovery. Consumers must deduplicate event IDs.

## Single tenant role contract

Each membership has one `role_id`. Authentication uses a single nullable `tenantRole`
for the selected tenant, separate from the user's `systemRole`. Role catalogs and the
`/tenants/{id}/roles` endpoints still return multiple available roles.

JWT access claims and login/register/refresh/switch-tenant responses use `tenantRole`
instead of the previous `roles` array. Without a selected tenant, `tenantRole` is null
(the null JWT claim may be omitted). With a selected tenant it is the membership's
built-in role key or `CUSTOM:{roleId}` for a custom role. Membership/detail APIs return
the custom role's display name separately. Permission grants remain a collection.

```json
{
  "user": {"id": "<user-id>", "systemRole": "USER"},
  "tenantId": "<selected-tenant-id>",
  "tenantRole": "OWNER",
  "permissions": ["tenant.view"]
}
```

Old access JWTs with the `roles` claim are rejected. Deploy IAM, gateway, and downstream
services together; clients must read `tenantRole` and obtain a new access token by refresh
or login. Refresh credentials are unchanged. The existing membership schema needs no migration.
