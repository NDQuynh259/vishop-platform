# Audit service

Service business rules, architecture and database design: [docs/README.md](docs/README.md).

Audit is an event-driven bounded context organized as a single `modules.auditlog` module.

```text
modules/auditlog/
  domain/
    entity/         Immutable AuditLog (plain Java)
    port/           Append-only AuditLogRepositoryPort
  application/
    command/        RecordAuditLogCommand and transactional handler
    query/          ListAuditLogsQuery and read-only handler
    dto/            AuditLogView read projection
    port/           Read and idempotent-processing contracts
  infrastructure/
    persistence/    JPA entities, Spring Data repositories and port adapters
  presentation/
    messaging/      Kafka inbound adapter
```

Write flow: Kafka -> AuditKafkaListener -> CommandBus -> RecordAuditLogCommandHandler
-> AuditMessageProcessingPort -> domain record -> AuditLogRepositoryPort -> PostgreSQL.
The audit row and consumed-message marker commit in the same transaction. Duplicate events
are skipped using the existing `audit-service` consumer group; errors propagate to the
shared Kafka retry/DLT handler. Kafka topics are unchanged. Schema changes are applied through Flyway migrations.

Read flow: QueryBus -> ListAuditLogsQueryHandler -> AuditLogReadPort -> AuditLogView.
The internal query requires a tenant ID, supports an optional aggregate filter, and uses
bounded pages (1-100 records) ordered by occurredAt and ID descending. It cannot write data.
Callers must authorize tenant access before dispatch. Global user events with a null tenant
remain stored but are outside this tenant-scoped query. CMS uses a separate authorized query
that can include all tenants and global events.

AuditApplication wires the module and shared consumer infrastructure without scanning
producer outbox components. Scheduling is unnecessary for this consumer-only service.

Validation:

```powershell
.\gradlew.bat :services:audit-service:test :services:audit-service:spotlessCheck
```

Tests cover schema/JSONB persistence, duplicate processing, rollback and retry,
tenant isolation, CQRS dispatch, and dependency rules between architecture layers.

## CMS audit list API

`GET /api/v1/cms/audit-logs` through the gateway (port 8000).

Only gateway-authenticated context with systemRole=PLATFORM_ADMIN can read this endpoint.
A tenant OWNER or a tenant role named PLATFORM_ADMIN does not grant CMS access.
No selected tenant is required. Gateway verifies the user JWT and resolves current grants
from Auth Redis, then signs context for audit-service. Audit rejects direct Bearer tokens
and unsigned identity headers; it does not query IAM/Redis on ordinary requests.
See [authorization contract](../../docs/core-architecture/gateway-authorization.md).

The query handler also checks CMS authority before reading the persistence port.
Gateway's specific CMS audit route precedes the general CMS route to IAM.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `pageMode` | `offset` | Select offset or cursor pagination |
| `page` | `1` | One-based page number, must be positive |
| `limit` | `20` | Page size, must be between 1 and 100 |
| `cursor` | omitted | Next-page token, used only with pageMode=cursor |
| `tenantId` | omitted | Exact tenant UUID; omitted includes global events and all tenants |
| `eventType` | omitted | Exact event type, nonblank, up to 200 characters |
| `aggregateType` | omitted | Exact aggregate type, nonblank, up to 100 characters; requires aggregateId |
| `aggregateId` | omitted | Exact aggregate UUID; requires aggregateType |
| `from` | omitted | Inclusive occurredAt lower bound, ISO-8601 instant |
| `to` | omitted | Exclusive occurredAt upper bound; must be after from |

Uses the shared `PaginationRequest`, `ListPagination`, `PagedResult`, and `ListEnvelope.from`.
Both modes are supported. Send pageMode=cursor on the first and subsequent cursor pages.
Cursor tokens bind the actor, filters and sort. A token alone never changes the mode.
Results sort by `occurredAt DESC, id DESC`. Offset pages can shift as new events arrive.
The response contains `object`, `url`, `data`, and `pageInfo` (`page`, `limit`, `total`,
`totalPages`, `hasNextPage`) for offset, or `hasNextPage`/`nextCursor` for cursor.
`payload` is JSON, not an escaped JSON string. Invalid page/limit ranges return 422.

```http
GET /api/v1/cms/audit-logs?page=1&limit=20&eventType=UserRegistered&from=2026-10-01T00:00:00Z&to=2026-11-01T00:00:00Z
```

```json
{
  "object": "list",
  "url": "/api/v1/cms/audit-logs",
  "data": [
    {
      "id": "019a1290-0000-7000-8000-000000000001",
      "eventId": "019a1290-0000-7000-8000-000000000002",
      "eventType": "UserRegistered",
      "tenantId": null,
      "aggregateType": "User",
      "aggregateId": "019a1290-0000-7000-8000-000000000003",
      "producer": "iam-service",
      "payload": {"userId": "019a1290-0000-7000-8000-000000000003"},
      "occurredAt": "2026-10-01T00:00:00Z",
      "createdAt": "2026-10-01T00:00:01Z"
    }
  ],
  "pageInfo": {"page": 1, "limit": 20, "total": 1, "totalPages": 1, "hasNextPage": false}
}
```

- `200`: matching page, including an empty page when no records match.
- `400`: invalid filters, UUIDs, timestamps or pagination.
- `401`: missing, invalid or expired gateway context.
- `403`: authenticated user without PLATFORM_ADMIN system role.

The endpoint is read-only and reflects events already consumed from Kafka; newly completed
IAM actions may not be visible immediately.

## Audit timestamps

`occurredAt` is when the source business event happened. `createdAt` is when audit
constructs the record for persistence; it is not a database commit timestamp.
Flyway V2 renames `recorded_at` to `created_at`, preserving existing values. V1 stays
unchanged for databases where it has already been applied. CMS responses now expose
`createdAt` instead of `recordedAt`.

## Run database migrations separately

Run from the repository root in PowerShell:

```powershell
$env:AUDIT_DB_URL = 'jdbc:postgresql://localhost:5432/vishop_audit'
$env:AUDIT_DB_USER = 'vishop'
$env:AUDIT_DB_PASSWORD = '<audit-db-password>'
.\gradlew.bat :services:audit-service:dbMigrate
```

This task runs Flyway without starting Spring, HTTP, JPA or Kafka and exits when finished.
It requires an existing PostgreSQL database and all three explicit `AUDIT_DB_*` variables;
it does not read `SPRING_DATASOURCE_*` or application.yml defaults. Pending migrations run
once; subsequent invocations validate the history and apply only newly added versions.
Baseline-on-migrate is disabled and database cleaning is disabled.
Normal service startup does not run migrations. Run `migrate` before starting the service; Hibernate only validates the prepared schema.
