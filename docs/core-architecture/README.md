# Core Platform Architecture

This directory is the central index for reusable Core Platform architecture and preserved architecture documents from the ViShop source project. The source reference is explicitly historical. Current IAM authentication is documented in the [authentication flow](./authentication-flow.md).

## Core platform

- [Core principles and module overview](#core-platform) in this document
- [Module boundaries](./module-boundaries.md)

## Architecture handbook

| Document | Subject |
| --- | --- |
| [Authentication flow](./authentication-flow.md) | Four auth APIs, JWT HS256, per-device sid sessions, Redis grants, signed context, diagrams and V12 rollout |
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
| [Security architecture](./security-architecture.md) | Authentication, RBAC, Edge Gateway Auth, gateway-signed context, Redis permissions and token revocation, and Multi-Tenancy |
| [Inter-service communication](./inter-service-communication.md) | Protocols: Kafka event-driven, REST/HTTPS, and gRPC decision framework |
| [Billing and SePay design](../product/vishop-billing-sepay-design.md) | Tenant subscriptions, QR bank transfers, payment idempotency, reconciliation and entitlements (proposed) |
| [Subscription/payment database](../../services/subscription-service/docs/database-design.md) | Nine-table V2 schema, ERD, payment allocation ledger, independent order/payment/fulfillment states and V1 upgrade |
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
