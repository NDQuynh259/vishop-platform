# IAM service

Authentication has been reset for a fresh implementation. The old login/register,
refresh/logout, JWT, cookies, gateway-context verification and Redis authorization
projection code have been removed.

## Current HTTP behavior

| Endpoint | Behavior |
| --- | --- |
| POST /api/v1/auth/register | Validates the request contract, then returns 501 |
| POST /api/v1/auth/login | Validates the request contract, then returns 501 |
| POST /api/v1/auth/refresh | Returns 501 |
| POST /api/v1/auth/logout | Returns 501 |
| Existing tenant/user/CMS/invitation endpoints | Temporarily return 503 |

The four auth routes are skeletons. They do not create users, authenticate callers,
issue tokens/cookies or perform logout. The /auth/me, /auth/switch-tenant and
/internal/authorization endpoints have been removed.

See [authentication reset](../../docs/core-architecture/iam-auth-reset.md) for scope,
validation and the temporary business-API gate.

## Retained code and data

- Identity owns users and system-role management data/use cases.
- Tenant owns tenants, memberships, roles, permissions and invitations.
- Application/domain permission checks remain with their business use cases.
- Invitation random-token generation is independent of login and lives in infra/tokens.
- The composition/members read model joins user and tenant-owned data.
- JPA persistence, Kafka outbox relay and the explicit migration CLI remain.
- Existing Flyway migrations and database contents are unchanged. Historical
  token_version/authorization_outbox schema is retained; no authorization relay runs.

Old IAM security packages, JWT/password adapters, authentication command/query handlers,
claims DTOs, cookie helper, auth-specific OpenAPI configuration and Redis integration
have been deleted. JWT/security auto-configurations are explicitly excluded in
IamApplication; no default generated-password authentication takes their place.

IamApiResetConfiguration is a temporary MVC interceptor, not a replacement login
implementation. It blocks business HTTP handlers before actor resolution or mutation.
When implementing authentication again, restore verified actor propagation and
controller authorization before removing this gate.

## Run and verify

Configure the database and Kafka endpoints. IAM currently needs no JWT secrets,
Auth Redis connection, cookie policy or gateway-context signing key.

```powershell
.\gradlew.bat :services:iam-service:dev
.\gradlew.bat :services:iam-service:test :services:iam-service:bootJar
```

JPA validates schema; it does not generate/update tables. Flyway does not run on
application startup. Apply migrations explicitly using IAM_DB_URL, IAM_DB_USER and
IAM_DB_PASSWORD:

```powershell
.\gradlew.bat :services:iam-service:migrateIamDb
```

Tests cover the four non-operational auth endpoints, blocked business routes,
absence of old auth beans, request validation, architecture rules, tenant domain
rules and PostgreSQL/Flyway constraints. Tests use embedded PostgreSQL.
