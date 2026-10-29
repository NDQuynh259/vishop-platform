# Security architecture

Runtime authentication and authorization use the [gateway authorization contract](gateway-authorization.md).

Gateway verifies the end-user access JWT and resolves current permissions from the dedicated
Auth Redis. IAM owns the durable, transactionally maintained authorization projection and
retries Redis delivery through its coalescing authorization outbox. Backend services consume
request-bound signed context and perform tenant/resource/business authorization locally.
They reject direct user JWTs and unsigned identity headers.

Role permission changes are scoped to tenantId/roleId and do not fan out to every member.
User credential revocation uses tokenVersion. Cache freshness, fail-closed behavior,
context signing secrets, cookie/refresh behavior, migration V9 and rollout instructions
are documented in the linked contract. The historical JWT-only runtime ended with V9.
