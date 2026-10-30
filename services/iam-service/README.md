# IAM service

IAM owns identity and tenant management. Authentication uses JWT HS256 with separate
access/refresh keys, a sid per login and revocable device sessions. It does not use
HttpSession. See the [authentication flow](../../docs/core-architecture/authentication-flow.md)
for annotated diagrams, classes, API contracts and revocation policy.

## Authentication endpoints

| POST endpoint | Behavior |
| --- | --- |
| /api/v1/auth/register | Creates USER/ACTIVE with BCrypt password, session and cookies (201) |
| /api/v1/auth/login | Verifies password, selects optional tenant and creates a new session |
| /api/v1/auth/refresh | Checks user/session/version in DB, refreshes access; optional tenantId selects tenant |
| /api/v1/auth/logout | Revokes current sid, waits for Redis acknowledgement and clears cookies (204) |

Tokens are returned only in HttpOnly cookies. JSON contains identity, current grants
and expiry times. Refresh preserves the original session deadline; it is not rotating.
There is no /auth/me or /auth/switch-tenant endpoint.

Business APIs verify signed gateway context, then check current DB user/session and
tenant grants. Controllers enforce permissions, with business checks retained in use
cases. The temporary reset interceptor has been removed.

## Configuration

Configure database/Kafka plus Auth Redis. IAM and gateway must agree on:

- JWT_ACCESS_SECRET (at least 32 UTF-8 bytes).
- JWT_REFRESH_SECRET, different from access secret.
- GATEWAY_IAM_CONTEXT_SECRET for signed internal context.
- AUTH_CACHE_PREFIX and Redis host/port/password/database.
- ALLOWED_ORIGINS for browser access.

Production requires secrets and origins; cookies are Secure by default. Development
defaults are only for local execution. See application.yml/application-prod.yml and
infra/.env.example. Browser writes send X-Requested-With: XMLHttpRequest and credentials.

## Migrate, run and verify

Hibernate validates mappings; migrations do not run on application startup. Set
IAM_DB_URL, IAM_DB_USER and IAM_DB_PASSWORD, then apply migrations including V12:

```powershell
.\gradlew.bat :services:iam-service:migrateIamDb
.\gradlew.bat :services:iam-service:dev
.\gradlew.bat :services:iam-service:test :services:iam-service:bootJar
```

Tests use isolated embedded PostgreSQL/Redis and cover HTTP cookies, permissions,
fixed refresh expiry, concurrent/idempotent logout, Redis failure/retry, global
credential revocation and schema/architecture rules. They do not migrate a running
environment. Existing tokens without sid require a new login after deployment.

Logout affects only its sid. Password, system-role, user-status and membership
changes invalidate all previous sessions via users.token_version. Role permission
edits update Redis grants without logout. Session Redis keys expire automatically;
expired database sessions do not yet have an automated cleanup job.
