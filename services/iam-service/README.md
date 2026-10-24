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
├── composition/     # Cross-module onboarding workflow and its REST entry point
├── contracts/
│   ├── identity/    # IdentityApi, UserView
│   ├── tenant/      # TenantApi, TenantResponseDto
│   └── access/      # AccessApi, AccessApi.Grant
├── modules/
│   ├── identity/    # Global users, authentication, sessions, system roles, user CMS
│   ├── tenant/      # Tenant information/lifecycle, tenant CMS
│   └── access/      # Memberships, tenant roles/permissions, invitations
└── shared/      # Actor context, event contract/outbox, opaque tokens, JDBC helpers, migration CLI
```

Business features live under `modules/`. Each feature has its own `domain`,
`application`, `infrastructure` and
`presentation/rest` packages. `application/command` and `application/query` contain the
use cases and transaction boundaries; `application/dto` contains use-case results and
shared application contracts. HTTP request bodies and their validation annotations live
in `presentation/rest/dto`. Domain packages use singular names: `entity`, `valueobject`,
`port`, `service` and `event`.
Domain models remain independent of frameworks and are mapped to HTTP response DTOs.

Feature interactions use interfaces and immutable DTOs in `contracts/<feature>`.
`contracts`, `modules`, `composition` and `shared` are sibling packages under `iam`.
Contracts have no dependency on module internals or frameworks. Implementations live in
`application/api`; DTO mapping lives in `application/mapper`. These dependencies can be
bidirectional at the feature level; API methods must not introduce circular bean
dependencies or recursive calls across features.

Public contracts:

* `IdentityApi` exposes current account data/status and system administration checks.
* `TenantApi` exposes tenant creation, status, a public tenant view and locking inside an existing
  transaction. Access never imports tenant entities or its repository.
* `AccessApi` exposes resolved grants, active tenant IDs and OWNER initialization inside
  an existing transaction. Identity never imports membership/role repositories.

`composition/application/command/CreateTenantCommandHandler` checks `IdentityApi`, calls
`TenantApi.create` to persist the aggregate and its outbox events, then calls
`AccessApi.initializeOwner`, all in one transaction. The composition layer uses only module
contracts; modules never depend on composition. Any failure rolls back the tenant,
default roles, OWNER membership and outbox together.
The domain owner policy uses its own `UserActivityPort`; an integration adapter calls
`IdentityApi` to resolve global account status.

Tenant persistence uses Spring Data JPA with versioning and explicit pessimistic locks.
Identity/access adapters retain explicit JDBC SQL. Each feature owns its row mappings and
read port. Access read projections may join user/tenant tables in the shared database;
mutations go through the owning feature. JPA/JDBC use the same datasource and transaction.
Flyway owns schema changes and Hibernate only validates the schema.

`TenantOnboardingController` and `CreateTenantRequest` live in composition and handle
`POST /api/v1/tenants`. `TenantController` handles tenant reads and renames.
`UserCmsController` belongs to identity and `TenantCmsController` to tenant. Tenant
membership/role endpoints belong to access's `TenantAccessController`. All HTTP paths are
preserved. Controllers dispatch through `CommandBus`/`QueryBus`.

Shared components have no dependency on business features. `IamActor` holds only request
identity/context, never a User entity or identity response DTO. Password hashing, JWTs and
the authentication filter/configuration remain under `modules/identity/infrastructure/security`.
Generic opaque tokens and the Spring Security principal live under
`shared/infrastructure/security`; outbox and migration CLI live under
`shared/infrastructure/messaging` and `shared/infrastructure/database`.

Architecture tests prevent domain dependencies on frameworks or outer layers, application
dependencies on concrete adapters, controllers bypassing the CQRS boundary, foreign feature
internals, and shared components depending on business features. Integration
tests cover JPA/JDBC rollback when event publication fails, tenant version updates, existing
authorization/concurrency rules, and the unchanged HTTP/event contracts.

## Authentication

Login/registration sets an HTTP-only `vishop_session` cookie containing a random opaque
token. Only its SHA-256 hash is stored in `sessions`. Cookies default to Secure and
SameSite=Lax. Login also returns a signed access JWT with a session ID and a 15-minute
lifetime, bounded by the session expiry (sessions default to seven days).

With no selected tenant, the JWT can access identity/onboarding/CMS APIs, subject to
their authorization rules. Selecting a tenant validates user, tenant, membership and
permissions. Session cookies use the selected tenant; Bearer tokens retain their own
tenant context, so switching a cookie context does not overwrite another tab's token.

The gateway introspects both cookies and Bearer tokens through the private IAM endpoint
on every protected request. IAM reloads current system role, membership and permissions
and returns a fresh signed token for downstream. Gateway strips identity headers and
cookies before forwarding protected requests. Requests to business services require a
tenant context. If IAM is unavailable, gateway returns 503; it does not use stale grants.

Logout revokes the session, invalidating its opaque cookie and previously issued JWTs
on subsequent gateway/IAM requests. Account suspension and membership/role changes are
also checked on each request. Downstream JWT validation alone does not consult the IAM
database: downstream ports must remain private behind the gateway.

System authorities use `SYSTEM_*`; downstream tenant role labels use `TENANT_ROLE_*`.
Business authorization should check the specific tenant permission, not a role display name.

Cookie-authenticated writes require an allowed Origin at both gateway and IAM. Bearer
authentication takes precedence; a bad Authorization header never falls back to cookies.
Use `credentials: 'include'` and an allowed browser origin for cookie flows. Refresh via
cookie after an access JWT expires; Bearer-only clients can refresh while their JWT is
still valid or log in again. Explicitly sending an expired Bearer header returns 401.

MFA enrollment/verification is not included. A role with `require_two_factor=true` fails
closed for tenant access until a verified MFA flow exists. Invitation creation returns
its raw token once to the authorized administrator; deliver it through a secure channel.
No email is sent automatically. Acceptance requires the token and a signed-in account
with the matching email, and rechecks the inviting member's current authority.

## APIs

All public endpoints below have prefix `/api/v1`.

| Method/path | Behavior |
| --- | --- |
| `POST /auth/register` | `{email,password,fullName}` → global account + session + JWT; rejects tenantId/role |
| `POST /auth/login` | `{email,password}` → new session + JWT |
| `GET /auth/me` | Global profile including systemRole; never returns a password hash |
| `POST /auth/refresh` | Fresh JWT from the live session and current grants |
| `POST /auth/switch-tenant` | `{tenantId}` → selected tenant JWT; updates this session's selection |
| `POST /auth/logout` | Revoke current session and clear cookie |
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
Responses expose `systemRole` on the user; tenantId, roles and permissions are separate
fields of the auth response. The old per-user tenantId/role contract is retired.

`POST /internal/auth/introspect` is not routed publicly. It requires `X-Iam-Internal-Key`
and `{credential,type: 'BEARER'|'SESSION'}`. Only gateway uses this endpoint.

## Run and verify

Set independent random `JWT_SECRET` and `IAM_INTERNAL_SECRET` values (at least 32 bytes),
database credentials, and `ALLOWED_ORIGINS` (comma-separated exact origins). IAM signs JWTs;
downstream services verify with the same JWT secret. Gateway only needs the internal secret.

```powershell
.\gradlew.bat :services:iam-service:bootRun
.\gradlew.bat test :services:iam-service:bootJar :services:api-gateway:bootJar
```

For local HTTP only, set `VISHOP_SECURITY_COOKIE_SECURE=false`. Docker Compose dev does
this for IAM. Production defaults to Secure cookies. No system administrator is seeded:
after registering the initial operator, promote that specific account through a trusted
database connection, then use the CMS APIs. Never promote legacy tenant roles automatically.

Tests start isolated real PostgreSQL 16 processes and run Flyway migrations, HTTP auth/RBAC,
invitation, revocation, concurrent owner updates and outbox acknowledgement/failure checks.
They do not require Docker or connect to an existing database. First execution downloads
embedded PostgreSQL binaries from Maven Central. Use a non-root user on Linux.

## Database preparation and legacy cutover

The schema contains users, tenants, roles, role_permissions, tenant_members,
tenant_invitations, sessions, outbox_events and consumed_messages. Creating a new tenant
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
