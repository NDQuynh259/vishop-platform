# Security architecture

IAM uses JWT HS256 with separate access/refresh keys and a sid per login. Logout
revokes the current session; security-sensitive account/membership changes revoke
all previous sessions via tokenVersion. See the annotated [authentication flow](authentication-flow.md).

Runtime authentication and authorization use the [gateway authorization contract](gateway-authorization.md).

Gateway verifies the end-user access JWT and resolves current permissions from the dedicated
Auth Redis. IAM owns the durable, transactionally maintained authorization projection and
retries Redis delivery through its coalescing authorization outbox. Backend services consume
request-bound signed context and perform tenant/resource/business authorization locally.
They reject direct user JWTs and unsigned identity headers. IAM additionally checks
current user, session and tenant grants in its own database.

Role permission changes are scoped to tenantId/roleId and do not fan out to every member.
User credential revocation uses tokenVersion. Cache freshness, fail-closed behavior,
context signing secrets, cookie/refresh behavior, migration V12 and rollout instructions
are documented in the linked flow and contract.
