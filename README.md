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

## API path convention

Production Java code uses the shared [ApiPaths](libs/common-domain/src/main/java/io/github/coreplatform/common/domain/constants/ApiPaths.java)
constants for version prefixes, including controllers, security matching, cookie paths,
OpenAPI customization and response URLs:

```java
@RequestMapping(ApiPaths.V1 + "/invitations")
```

Versions remain explicit per controller. Gateway YAML and external API examples retain
their literal public URLs; the version prefix is an API contract, not an environment setting.

## Build and test

Runtime database access uses Spring Data JPA or JPA `EntityManager`, including native SQL.
Hibernate only validates entity mappings; Flyway SQL migrations own every schema change.
Applications do not run migrations at startup. Run each service's `migrate` task before `dev`,
`start`, `bootRun` or `runJar`, with migration credentials pointing to the application database.
See the [persistence standard](docs/core-architecture/persistence.md).

```powershell
.\gradlew.bat clean test
```

## Project commands

Each service has its own command file, equivalent to a per-service `package.json`:

```text
services/
  iam-service/build.gradle.kts
  audit-service/build.gradle.kts
  api-gateway/build.gradle.kts
```

| Node.js command | Gradle equivalent for IAM |
| --- | --- |
| `npm run` | `.\gradlew.bat :services:iam-service:commands` |
| `npm run dev` | `.\gradlew.bat :services:iam-service:dev` |
| `npm start` | `.\gradlew.bat :services:iam-service:start` |
| `npm run build` | `.\gradlew.bat :services:iam-service:bootJar` |
| `npm test` | `.\gradlew.bat :services:iam-service:test` |
| `npm run migrate` | `.\gradlew.bat :services:iam-service:migrate` |

Replace `iam-service` with `audit-service` or `api-gateway` to run another service.
Gateway has no migration command. `dev` starts the selected service with Spring profile
`dev`; `start` runs it with profile `prod`, using database URLs and credentials from the
environment. These commands run on the current machine and accept BootRun's `--args` option.

The per-service `commands` listing also includes all standard build and diagnostic commands:

| Category | Commands |
| --- | --- |
| Run | `dev`, `start`, `bootRun`, `runJar` |
| Compile/package | `compileJava`, `classes`, `build`, `bootJar`, `clean` |
| Verify | `test`, `check`, `spotlessApply`, `spotlessCheck` |
| Inspect build | `tasks --all`, `dependencies`, `dependencyInsight` |
| Inspect JVM | `javaVersion`, `javaProcesses`, `threadDump`, `heapInfo` |
| Database | `migrate` for IAM and Audit |

JDK commands are shared through [gradle/jvm-tools.gradle.kts](gradle/jvm-tools.gradle.kts)
and use the project's Java 21 toolchain. Diagnostics require the explicit process ID of a
local JVM. A service prefix selects the command; `-Ppid` selects the actual target process.

```powershell
.\gradlew.bat :javaVersion
.\gradlew.bat :javaProcesses
.\gradlew.bat :threadDump -Ppid=12345
.\gradlew.bat :heapInfo -Ppid=12345
.\gradlew.bat :services:iam-service:dependencyInsight --dependency spring-core --configuration runtimeClasspath
.\gradlew.bat :services:iam-service:test --tests '*IamArchitectureTest'

# Build the selected service's boot JAR and run it (prod profile by default).
.\gradlew.bat :services:iam-service:runJar -Pprofile=dev
# Run an existing JAR, without rebuilding it; relative paths resolve from the selected project.
.\gradlew.bat :runJar -Pjar=services/iam-service/build/libs/iam-service-0.1.0-SNAPSHOT.jar -Pprofile=prod
```

`runJar` runs until the application exits; stop it with Ctrl+C. `dev`, `start`, and `bootRun`
accept `--args`; `runJar` uses `-Pjar` and `-Pprofile` instead.

The `projectCommands` map in [build.gradle.kts](build.gradle.kts) is the central command
list for root shortcuts and aggregate operations. List available commands with:

```powershell
.\gradlew.bat commands
# Gradle's built-in listing also works:
.\gradlew.bat tasks --group project
```

| Command | Purpose |
| --- | --- |
| `runIam` | Start IAM on port 8080 |
| `runGateway` | Start Gateway on port 8000 |
| `runAudit` | Start Audit on port 8082 |
| `migrateIam` | Migrate IAM using `IAM_DB_URL`, `IAM_DB_USER`, `IAM_DB_PASSWORD` |
| `migrateAudit` | Migrate Audit using `AUDIT_DB_URL`, `AUDIT_DB_USER`, `AUDIT_DB_PASSWORD` |
| `packageAll` | Build boot JARs for all three services |
| `buildAll` | Build and check all projects |
| `compileAll` | Compile Java sources across all projects |
| `classesAll` | Compile and process resources across all projects |
| `dependenciesAll` | Show dependency trees across all projects |
| `testAll` | Run all module tests and root architecture tests |
| `checkAll` | Run all checks |
| `formatAll` | Format Java sources across all projects |
| `lintAll` | Check formatting without modifying sources |
| `cleanAll` | Remove Gradle build outputs across all projects |

Run any command as `.\gradlew.bat <command>`. Start each service in its own terminal.
Database migration commands run independently of services; see the [IAM](services/iam-service/README.md)
and [Audit](services/audit-service/README.md) guides for database configuration.

## Services and access

Active runtimes are `iam-service`, `api-gateway` and `audit-service`. IAM owns global
accounts, system roles, tenants, memberships, tenant roles and permissions in `vishop_iam`.
The retired identity/tenant services have been removed. Their original code/schema can be
referenced through Git history. See [IAM runtime and API guide](services/iam-service/README.md).

IAM uses stateless JWT access and refresh tokens stored only in HttpOnly cookies; JSON
responses contain user/tenant grants, never credentials. Browser requests use
`credentials: 'include'` and `X-Requested-With: XMLHttpRequest` for POST/PUT/PATCH/DELETE.
Gateway verifies the access cookie locally and forwards Bearer access internally, without
session storage, Redis or IAM introspection. Refresh reads its cookie with no body;
switch-tenant accepts only `{tenantId}`. Logout clears both cookies. Issued JWTs remain
valid until expiry. Production cookies use `AUTH_COOKIE_SECURE=true` and HTTPS.
CMS requires PLATFORM_ADMIN; tenant OWNER does not grant CMS access.

Configure a random JWT_SECRET (at least 32 bytes),
database credentials and exact ALLOWED_ORIGINS. JWT_SECRET is shared by IAM, gateway and
downstream verification. IAM does not expose an internal introspection endpoint.


Public registration accepts email, password and fullName; tenantId/role assignment is
rejected. Create an organization through POST /api/v1/tenants, then select it through
POST /api/v1/auth/switch-tenant. The user profile returns systemRole separately from
tenantRole/permissions in the selected tenant. Existing legacy databases require a planned
import before deploying the new runtime; source changes do not migrate those records.

## Contribution rules

See [CONTRIBUTING.md](CONTRIBUTING.md) for Conventional Commit messages, branch naming, and pull request expectations.

## Architecture documentation

Start with the [Core Architecture index](docs/core-architecture/README.md) for all architecture documents and the reusable core module boundaries. Original architecture documents remain preserved under `docs/architecture/`; product documentation and other guides remain in their existing locations.
