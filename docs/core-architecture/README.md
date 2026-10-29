# Core Platform Architecture

This directory is the central index for reusable Core Platform architecture and preserved architecture documents from the ViShop source project. The source reference is explicitly historical; current authentication follows the Java IAM contract.

## Core platform

- [Core principles and module overview](#core-platform) in this document
- [Module boundaries](./module-boundaries.md)

## Architecture handbook

| Document | Subject |
| --- | --- |
| [System architecture](./architecture.md) | Gradle modules, DDD, hexagonal architecture, CQRS, outbox, Kafka, and Database Persistence/Migration (Flyway/JPA) |
| [Persistence standard](./persistence.md) | Required Spring Data JPA/EntityManager access, validate-only entities, and Flyway SQL migrations for all current services/libraries |
| [API gateway](./api-gateway.md) | Gateway routing, filters, CORS, and error handling |
| [Saga pattern](./saga-pattern.md) | Saga orchestration and compensation |
| [Service decomposition](./scaling-to-services.md) | Service boundaries, decomposition criteria, and extraction playbook |
| [Service module anatomy](./domain-module-anatomy.md) | Domain, application, infrastructure, and presentation layers |
| [Multi-tenancy](./tenancy.md) | Tenant context propagation, 1-to-N membership data modeling, and service boundary decomposition |
| [Kafka architecture](./kafka-architecture.md) | Kafka topology, outbox, consumer transactions, retry/DLT, and replay |
| [Idempotency and tracing](./idempotency-and-tracing.md) | Choosing consumed messages vs. unique event IDs, external API idempotency, and request/message correlation |
| [Observability](./observability-architecture.md) | Logs, metrics, distributed tracing, and alerting |
| [Security architecture](./security-architecture.md) | Authentication, RBAC, Edge Gateway Auth, stateless JWT access/refresh and expiry-based logout, and Multi-Tenancy |
| [Inter-service communication](./inter-service-communication.md) | Protocols: Kafka event-driven, REST/HTTPS, and gRPC decision framework |
| [Billing and SePay design](../product/vishop-billing-sepay-design.md) | Tenant subscriptions, QR bank transfers, payment idempotency, reconciliation and entitlements (proposed) |
| [Billing database](../../services/subscription-service/docs/database-design.md) | Current seven-table schema, ERD, snapshots, idempotency, locking, and proposed extensions |
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

The source-project reference preserves the NestJS design for comparison. Current IAM/gateway, JWT authentication and tenant bootstrap are documented in the security, gateway and tenancy guides above; historical source behavior is not a runtime guarantee.
