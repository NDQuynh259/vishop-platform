# Core Platform

Reusable Java 21 and Spring Boot 3.3 libraries for building service-oriented applications. The platform is modular: include only the shared capabilities an application needs, and keep business services and deployment choices in the consuming project.

## Technology

- Java 21
- Spring Boot 3.3.5 and Gradle Kotlin DSL
- JUnit 5
- Micrometer and OpenTelemetry for observability integrations

## Modules

- `common-domain`: DDD building blocks, CQRS contracts, and domain exceptions
- `common-web`: Spring web adapters, pagination, and CQRS dispatch
- `common-security`: request context primitives
- [`common-cache`](libs/common-cache/README.md): shared Redis cache with JSON values and TTL; callers choose when to cache
- `common-events`: event envelope and Kafka integration
- `common-persistence`: outbox and idempotent consumer support
- `common-saga`: saga orchestration primitives
- `common-observability`: Actuator, metrics, tracing, and structured logging integration
- `common-test`: shared test utilities

Shared code uses the `io.github.coreplatform.common` Java package namespace. See [Core Architecture](docs/core-architecture/README.md) and [Module Boundaries](docs/core-architecture/module-boundaries.md) for design and dependency guidance.

## Build and test

Runtime database access uses Spring Data JPA or JPA `EntityManager`, including native SQL.
Hibernate only validates entity mappings; Flyway SQL migrations own every schema change.
See the [persistence standard](docs/core-architecture/persistence.md).

```powershell
.\gradlew.bat clean test
```

## Services and access

Active runtimes are `iam-service`, `api-gateway` and `audit-service`. IAM owns global
accounts, system roles, tenants, memberships, tenant roles and permissions in `vishop_iam`.
The retired identity/tenant services have been removed. Their original code/schema can be
referenced through Git history. See [IAM runtime and API guide](services/iam-service/README.md).

IAM uses stateless JWT access and refresh tokens, returned in the response body. Gateway
and downstream services verify access tokens locally without session storage, Redis state
or IAM introspection. Refresh accepts `{ "refreshToken": "..." }`; logout records an audit
event and clients discard both tokens. Issued JWTs remain valid until expiry.
CMS requires PLATFORM_ADMIN; tenant OWNER does not grant CMS access.

Configure independent JWT_SECRET and IAM_INTERNAL_SECRET secrets (at least 32 bytes each),
database credentials and exact ALLOWED_ORIGINS. JWT_SECRET is shared by IAM, gateway and
downstream verification. IAM_INTERNAL_SECRET protects the private compatibility endpoint.


Public registration accepts email, password and fullName; tenantId/role assignment is
rejected. Create an organization through POST /api/v1/tenants, then select it through
POST /api/v1/auth/switch-tenant. The user profile returns systemRole separately from
roles/permissions in the selected tenant. Existing legacy databases require a planned
import before deploying the new runtime; source changes do not migrate those records.

## Contribution rules

See [CONTRIBUTING.md](CONTRIBUTING.md) for Conventional Commit messages, branch naming, and pull request expectations.

## Architecture documentation

Start with the [Core Architecture index](docs/core-architecture/README.md) for all architecture documents and the reusable core module boundaries. Original architecture documents remain preserved under `docs/architecture/`; product documentation and other guides remain in their existing locations.
