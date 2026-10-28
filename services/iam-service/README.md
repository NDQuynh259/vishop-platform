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
  transaction. The OWNER role has all 34 current `CommonPermissions` keys.
* Composite foreign keys prevent cross-tenant role assignment. Repository SQL includes
  explicit tenant predicates. The schema does not enable RLS.
* Tenant mutation transactions lock the tenant row. Concurrent changes cannot demote or
  deactivate the last active owner. Non-owners cannot manage owner memberships.
* A caller cannot create/assign a role with grants they do not hold. Built-in OWNER is
  immutable. Custom role names never confer system authority.

## Code structure

The service follows the [domain module architecture](../../docs/core-architecture/domain-module-anatomy.md):

```text
com/vishop/iam/
├── IamApplication.java
├── composition/
│   ├── onboarding/ # Cross-module tenant creation workflow and its REST entry point
│   └── members/    # Cross-module user/tenant/role/permission read projection
├── modules/
│   ├── identity/    # Global users, authentication, JWT tokens, system roles, user CMS
│   ├── tenant/      # Tenant information/lifecycle, tenant CMS
│   └── access/      # Memberships, tenant roles/permissions, invitations
├── infra/          # Shared IAM security, JPA outbox relay and migration CLI
└── shared/         # Framework-independent actor context, event interface and ports
```

Business features live under `modules/`. Each feature has its own `domain`,
`application`, `infrastructure` and
`presentation/rest` packages. `application/command` and `application/query` contain the
use cases and transaction boundaries; `application/dto` contains use-case results and
shared application contracts. HTTP request bodies and their validation annotations live
in `presentation/rest/dto`. Domain packages use singular names: `entity`, `valueobject`,
`port`, `service` and `event`.
Domain models remain independent of frameworks and are mapped to HTTP response DTOs.

Feature interactions use interfaces in `modules/<feature>/application/api` and public
immutable DTOs in that module's `application/dto`. Public interfaces and DTOs have no
dependency on module internals or frameworks. Implementations also live in
`application/api` as package-private Spring components, so other packages cannot import
or inject their concrete classes. Spring injects the public API interfaces instead. DTO mapping lives in
`application/mapper`. These dependencies can be
bidirectional at the feature level; API methods must not introduce circular bean
dependencies or recursive calls across features.

Public module interfaces and DTOs:

* `IdentityApi` exposes current account data/status and system administration checks.
* `TenantApi` exposes tenant creation, status, a public tenant view and locking inside an existing
  transaction. Access never imports tenant entities or its repository.
* `AccessApi` exposes resolved grants, active tenant IDs and OWNER initialization inside
  an existing transaction. Identity never imports membership/role repositories.
* The exported DTOs are `UserView`, `TenantResponseDto` and `TenantMemberResponseDto`.
  Architecture tests permit these types and the API interfaces across module boundaries;
  implementation classes, other DTOs, entities and repositories remain internal.

`IamArchitectureTest` enforces these boundaries for all three modules and composition:
foreign repository/read ports, entities and implementation classes are forbidden. It also
requires public API interfaces and package-private `@Component` implementations.
These are local Java calls using the same transaction manager, not HTTP calls to IAM.

`composition/onboarding/application/command/CreateTenantCommandHandler` checks `IdentityApi`, calls
`TenantApi.create` to persist the aggregate and its outbox events, then calls
`AccessApi.initializeOwner`, all in one transaction. The composition layer uses only module
contracts; modules never depend on composition. Any failure rolls back the tenant,
default roles, OWNER membership and outbox together.
The domain owner policy uses its own `UserDirectoryPort`; an integration adapter calls
`IdentityApi` to resolve global account status.

All business persistence uses Spring Data JPA entities/repositories with versioning and
explicit locks. Identity/access own their entity mappings, permission collections and
repositories. Database-clock invitation checks use native Spring Data queries.
All five IAM entities declare UUID ID, audit/soft-delete timestamps and optimistic
version mappings directly, without entity inheritance. Membership, role and invitation
also declare immutable `tenantId`. Lombok generates getters/setters and protected JPA
constructors. Each entity defines its own `@PrePersist`/`@PreUpdate` callbacks: inserts
preserve domain-provided `createdAt` and fill missing timestamps; updates refresh `updatedAt`.
There is no shared audit interface/listener. Native bulk updates still maintain
timestamps/version in SQL.
`deleted_at` is nullable metadata added by V6 to all five IAM entity tables. Adding this
column does not enable automatic query filtering or change deletion endpoints.
Each feature owns its mappings and
read port. Access read projections may join user/tenant tables in the shared database;
mutations go through the owning feature. JPA/native queries share the same datasource and transaction.
Flyway SQL owns all schema changes; Hibernate only validates mappings with `generate-ddl: false`
and `ddl-auto: validate`. Spring SQL initialization and automatic Flyway startup are disabled. Run `:services:iam-service:migrate` before starting the application. See the platform
[persistence standard](../../docs/core-architecture/persistence.md).

`TenantOnboardingController` and `CreateTenantRequest` live in `composition/onboarding` and handle
`POST /api/v1/tenants`. `TenantController` handles tenant reads and renames.
`composition/members` owns the member detail and self-service read endpoints through
`TenantMemberController`. Membership writes and the existing member list remain in access.
`UserCmsController` belongs to identity and `TenantCmsController` to tenant. Tenant
membership/role endpoints belong to access's `TenantAccessController`. All HTTP paths are
preserved. Controllers dispatch through `CommandBus`/`QueryBus`.

Shared components have no dependency on business features, frameworks or infrastructure.
They retain IAM-specific ports and the domain-event interface; reusable domain/CQRS
primitives continue to come from `libs/common-*`. `AuthenticatedUser` holds only request
identity/context, never a User entity or identity response DTO. Password hashing, JWTs and
the authentication filter/configuration remain under `modules/identity/infrastructure/security`.
Generic opaque tokens and the Spring Security principal live under
`infra/security`; the JPA outbox relay and migration CLI live under
`infra/messaging` and `infra/database`.

This layout adapts the source project's library boundaries inside the IAM microservice.
It does not introduce a second platform core or move service-specific code into the
platform libraries. Each feature keeps its own persistence/security adapters in
`modules/<feature>/infrastructure`. Cross-module synchronous checks use contract APIs;
tenant creation keeps its transaction boundary in a thin CQRS composition handler.

Architecture tests prevent domain dependencies on frameworks or outer layers, application
dependencies on concrete adapters, controllers bypassing the CQRS boundary, foreign feature
internals, shared components depending on frameworks/infrastructure, and common
infrastructure depending on module internals. Integration
tests cover cross-module JPA rollback when event publication fails, tenant version updates, existing
authorization/concurrency rules, and the unchanged HTTP/event contracts.

## List the current user's tenants

`GET /api/v1/tenants` is handled by `TenantController` through
`ListMyTenantsQuery`. The user ID comes from the authenticated principal, not a request
parameter. It returns active tenants in which that user has an active membership,
ordered by creation time and ID by default. Use `GET /api/v1/tenants?page=1&limit=20`; the endpoint uses
the shared `PaginationRequest` normalization (default page 1, limit 20, maximum 100).
It supports offset pagination only; a nonblank `cursor` returns 400.

The response is the system `ListEnvelope`: `{object, url, data, pageInfo}`. Each item
in `data` contains `{id, code, name, status, createdAt}`. `pageInfo` contains `page`,
`limit`, `total`, `totalPages`, and `hasNextPage`. The database filters by the current
user and active statuses before applying LIMIT/OFFSET; the count uses the same filters.

The handler obtains the current user's active tenant IDs through the public `AccessApi`.
`TenantReadAdapter` uses `SpringDataTenantRepository` / `JpaSpecificationExecutor` with
`Specification`, `PageRequest`, and `Sort`; it does not build SQL or use `EntityManager`
directly. Membership authorization remains inside the access module. Text searches use
case-normalized JPA `LIKE` with escaped wildcard characters.

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
401. A user without active tenants receives empty `data` and total 0. A page beyond
the last page also has empty `data` while retaining the matching total.
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
requires `tenant.member.view`. Reading yourself requires active tenant access. Both
routes retain user/tenant/membership/MFA checks and return `Cache-Control: no-store`.
Authorized managers can inspect inactive memberships; their assigned permissions do
not imply that the member can currently exercise them. Missing target memberships
return 404 after authorization; callers without tenant access receive 403.

```text
TenantMemberController
  -> GetTenantMemberQueryHandler
       -> AccessApi.requireAccess (authorization)
       -> TenantMemberReadPort
            -> JpaTenantMemberReadAdapter (EntityManager native query)
```

The adapter uses JPA `EntityManager` with a parameterized native query and maps its
`Tuple` result directly to the response DTO, without loading a managed entity graph.
It joins `users`, `tenants`, `tenant_members`, `roles` and `role_permissions`
within the IAM datasource. It aggregates grants into one result; a LEFT JOIN preserves
roles with no permissions. Joins use tenant/role/user IDs, never role names. Authorization
and projection share a read-only REPEATABLE READ transaction. The handler imports only
public module contracts and its own read port, not module entities or repositories.
Grants come from the database, not cached token claims. The requested tenant is checked
independently of the JWT's selected tenant.

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
require a tenant context. Domain use cases still check current user/membership permissions.

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
role key/name. Permission grants remain a collection.

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
