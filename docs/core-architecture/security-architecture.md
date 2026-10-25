# Security architecture

Authentication uses stateless JWT access and refresh tokens. Login/register return both
in the JSON body. No login session, authentication cookie or Redis projection is created.

Access JWTs use issuer `iam-service`, audience `vishop-api`, `token_use=access` and a
15-minute default lifetime. Claims contain user ID, tenant ID, system role, roles and
permissions. Gateway and downstream services verify signatures, issuer, audience, use
and expiry locally. Business APIs accept Bearer access tokens only. JWTs have no `sid`,
session versions or database/Redis authentication lookups.

Refresh JWTs use audience `vishop-refresh`, `token_use=refresh` and a seven-day default
lifetime. `POST /api/v1/auth/refresh` accepts `{ "refreshToken": "..." }` without a valid
access token. IAM validates the refresh JWT and current account/tenant grants before
issuing a new pair. Refresh preserves the original absolute refresh expiry. Previous
refresh JWTs remain valid until expiry: this fully stateless design has no rotation or
reuse tracking in server storage.

Switch tenant requires Bearer access authentication plus `{tenantId,refreshToken}`. IAM
checks that both JWTs belong to the same user and that membership permits access, then
issues a pair carrying the selected tenant. Other tabs retain their own JWT context.

Logout writes an audit event and returns 204. Clients discard both tokens. Already issued
JWTs remain valid until expiry. Account, membership and grant changes are read when new
tokens are issued. IAM use cases also check live business authorization. Gateway and
other services use signed access grants until expiry; immediate global revocation is
not part of this stateless model.

Gateway removes client-supplied identity headers and builds metadata from verified JWTs.
Downstream verifies JWTs independently. PLATFORM_ADMIN permits CMS operations only;
tenant access requires membership. Identity APIs allow a null tenant, business APIs do not.
CORS uses exact `ALLOWED_ORIGINS`. Never log credentials or put tokens into outbox events.

Configure `JWT_SECRET` (at least 32 bytes) consistently in IAM, gateway and downstream.
IAM token lifetimes use `JWT_ACCESS_EXPIRATION_MS` and `JWT_REFRESH_EXPIRATION_MS`.
`IAM_INTERNAL_SECRET` protects a private compatibility introspection endpoint; gateway
does not call it for authentication. Kafka/outbox continue to serve audit/integration events.

Flyway V4 removes sessions, security_states and their triggers/functions. Migration history
V1-V3 remains unchanged to preserve checksums on existing databases. Deploying the new
contract requires clients to log in again and use the new token pair.

Tests use temporary PostgreSQL for IAM and real JWT verification at gateway/downstream.
They cover expiration, forged signatures, access/refresh separation, refresh after access
expiry, tenant isolation, the original refresh deadline and stateless logout behavior.
