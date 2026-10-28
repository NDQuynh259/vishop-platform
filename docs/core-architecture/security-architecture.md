# Security architecture

Authentication uses stateless JWT access and refresh tokens stored in host-only HttpOnly
cookies. Login/register/refresh/switch-tenant return user/tenant grants in JSON, never JWTs.
No login session or Redis projection is created.

Access JWTs use issuer `iam-service`, audience `vishop-api`, `token_use=access` and a
15-minute default lifetime. Claims contain user ID, tenant ID, systemRole, a single tenantRole and
permissions. Gateway and downstream services verify signatures, issuer, audience, use
and expiry locally. Browsers send the access cookie; gateway forwards verified Bearer access
to business services, stripping browser cookies. JWTs have no `sid`,
session versions or database/Redis authentication lookups.

Refresh JWTs use audience `vishop-refresh`, `token_use=refresh` and a seven-day default
lifetime. `POST /api/v1/auth/refresh` reads the refresh cookie with no body and without a valid
access token. IAM validates the refresh JWT and current account/tenant grants before
setting new HttpOnly cookies. Refresh preserves the original absolute refresh expiry. Previous
refresh JWTs remain valid until expiry: this fully stateless design has no rotation or
reuse tracking in server storage.

Switch tenant reads access/refresh cookies and accepts only `{tenantId}`. IAM
checks that both JWTs belong to the same user and that membership permits access, then
sets new cookies carrying the selected tenant. Tabs in a browser share its cookie jar.

Logout clears both cookie paths and returns 204 even with expired/missing access. A valid
authenticated identity also produces an audit event. Already issued
JWTs remain valid until expiry. Account, membership and grant changes are read when new
tokens are issued. IAM use cases also check live business authorization. Gateway and
other services use signed access grants until expiry; immediate global revocation is
not part of this stateless model.

Gateway removes client-supplied identity headers and builds metadata from verified JWTs.
Downstream verifies JWTs independently. PLATFORM_ADMIN permits CMS operations only;
tenant access requires membership. Identity APIs allow a null tenant, business APIs do not.
CORS uses exact `ALLOWED_ORIGINS`. Never log credentials or put tokens into outbox events.

All unsafe browser API methods, including login/register/refresh/logout, require the
non-simple header `X-Requested-With: XMLHttpRequest`. Gateway and IAM reject missing
headers and untrusted Origins. Credentialed CORS permits only exact configured origins.
The private key-authenticated introspection endpoint is exempt from the browser header.
Frontend fetch calls use `credentials: 'include'`; Scalar supplies these options too.
Cookies default to HttpOnly, host-only, SameSite=Lax. Access uses Path `/`, refresh uses
Path `/api/v1/auth`. `AUTH_COOKIE_SECURE=true` is the production default and requires HTTPS;
SameSite=None is rejected unless Secure is enabled. Local HTTP may use Secure=false.

Configure `JWT_SECRET` (at least 32 bytes) consistently in IAM, gateway and downstream.
IAM token lifetimes use `JWT_ACCESS_EXPIRATION_MS` and `JWT_REFRESH_EXPIRATION_MS`.
`IAM_INTERNAL_SECRET` protects a private compatibility introspection endpoint; gateway
does not call it for authentication. Kafka/outbox continue to serve audit/integration events.

Flyway V4 removes sessions, security_states and their triggers/functions. Migration history
V1-V3 remains unchanged to preserve checksums on existing databases. Deploying the new
cookie contract requires clients to log in again so the browser receives the auth cookies.

Tests use temporary PostgreSQL for IAM and real JWT verification at gateway/downstream.
They cover expiration, forged signatures, access/refresh separation, refresh after access
expiry, tenant isolation, the original refresh deadline and stateless logout behavior.
