# Core Platform Architecture

This directory is the central index for reusable Core Platform architecture and preserved architecture documents from the ViShop source project. The source documents remain unchanged in `docs/architecture/`.

## Core platform

- [Core principles and module overview](#core-platform) in this document
- [Module boundaries](./module-boundaries.md)

## Architecture handbook

| Document | Subject |
| --- | --- |
| [System architecture](./architecture.md) | Gradle modules, DDD, hexagonal architecture, CQRS, outbox, Kafka, and Database Persistence/Migration (Flyway/JPA) |
| [API gateway](./api-gateway.md) | Gateway routing, filters, CORS, and error handling |
| [Saga pattern](./saga-pattern.md) | Saga orchestration and compensation |
| [Service decomposition](./scaling-to-services.md) | Service boundaries, decomposition criteria, and extraction playbook |
| [Service module anatomy](./domain-module-anatomy.md) | Domain, application, infrastructure, and presentation layers |
| [Multi-tenancy](./tenancy.md) | Tenant context propagation, 1-to-N membership data modeling, and service boundary decomposition |
| [Idempotency and tracing](./idempotency-and-tracing.md) | Deduplication and request/message correlation |
| [Observability](./observability.md) | Logs, metrics, distributed tracing, and alerting |
| [Security architecture](./security-architecture.md) | Authentication, RBAC, Edge Gateway Auth, Dynamic Token Revocation (Redis/Kafka), and Multi-Tenancy |
| [Inter-service communication](./inter-service-communication.md) | Protocols: Kafka event-driven, REST/HTTPS, and gRPC decision framework |
| [Spring Boot migration plan](./spring-boot-microservices-migration-plan.md) | Migration strategy and operational considerations |
| [Source project reference](./source-project-architecture-reference.md) | Deep-dive analysis of NestJS `api-vishop` architecture, Prisma schema, Better Auth sessions, and mapping to Java |

## Core principles

Core Platform provides optional Java 21 and Spring Boot libraries; consuming applications own business services, product domains, deployment topology, and tenant-specific policy. Modules are independently consumable, domain contracts remain framework-independent, and applications opt into the modules they need.

The shared Java namespace is `io.github.coreplatform.common`. See [module boundaries](./module-boundaries.md) for dependency and ownership rules, and [contribution rules](../../CONTRIBUTING.md) for branch and commit conventions.

## Build and test

```powershell
.\gradlew.bat clean test
```

## Source-project documents

The handbook entries above are preserved copies of the original documents in `docs/architecture/`. They describe the ViShop source project and may refer to services or infrastructure that are not part of the reusable core build. Shared module links in these copies point to the current core source paths where available.
