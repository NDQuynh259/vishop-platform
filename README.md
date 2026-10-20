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
- `common-events`: event envelope and Kafka integration
- `common-persistence`: outbox and idempotent consumer support
- `common-saga`: saga orchestration primitives
- `common-observability`: Actuator, metrics, tracing, and structured logging integration
- `common-test`: shared test utilities

Shared code uses the `io.github.coreplatform.common` Java package namespace. See [Core Architecture](docs/core-architecture/README.md) and [Module Boundaries](docs/core-architecture/module-boundaries.md) for design and dependency guidance.

## Build and test

```powershell
.\gradlew.bat clean test
```

## Contribution rules

See [CONTRIBUTING.md](CONTRIBUTING.md) for Conventional Commit messages, branch naming, and pull request expectations.

## Preserved source-project documentation

Existing documents under `docs/architecture/`, `docs/product/`, and other `docs/` directories are preserved from the ViShop source project. They remain available for reference; reusable core architecture is documented under `docs/core-architecture/`.
