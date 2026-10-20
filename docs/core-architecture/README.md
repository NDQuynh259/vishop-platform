# Core Platform Architecture

Core Platform is a reusable Java 21 and Spring Boot 3.3 foundation. It provides optional libraries for common application concerns; it does not define business services, product domains, deployment topology, or tenant-specific policy.

## Principles

- Modules are independently consumable and dependencies flow toward lower-level abstractions.
- Spring integrations stay in adapter modules; domain contracts remain framework-independent.
- Applications opt into modules they need rather than depending on a single all-inclusive starter.
- Service implementations, API contracts, database schemas, and infrastructure are owned by consuming projects.
- Observability uses standard Micrometer/OpenTelemetry interfaces and OTLP-compatible exporters.

## Modules

| Module | Responsibility |
| --- | --- |
| `common-domain` | DDD building blocks, CQRS contracts, and domain exceptions |
| `common-web` | Spring web support, request identifiers, pagination, and CQRS dispatch adapters |
| `common-security` | Reusable request security context primitives; applications own authentication and authorization policy |
| `common-events` | Event envelope and Kafka integration primitives |
| `common-persistence` | Persistence support for outbox and idempotent consumption |
| `common-saga` | Saga orchestration primitives and persistence contracts |
| `common-observability` | Actuator, Micrometer, tracing, and structured logging integration |
| `common-test` | Shared test utilities |

See [module boundaries](module-boundaries.md) for dependencies and ownership boundaries. See [contribution rules](../../CONTRIBUTING.md) for branch and commit conventions.

## Build and test

```powershell
.\gradlew.bat clean test
```

Shared Java packages use the `io.github.coreplatform.common` namespace. Existing ViShop documents under `docs/` are preserved as source-project documentation; reusable core architecture is maintained in this directory.
