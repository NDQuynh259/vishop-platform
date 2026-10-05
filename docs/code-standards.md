# Code Standards

Standards and conventions enforced across the codebase to maintain production quality and developer productivity.

## Logging

**Use nestjs-pino exclusively** via dependency injection. Never use `console.log`, `console.error`, or `console.warn` in application code. Exceptions: one-shot CLI scripts in `apps/migration/` and Webpack/build-time scripts where the NestJS DI container is not available.

```typescript
// DO
constructor(private readonly logger: PinoLogger) {}
this.logger.info('User signed up', { userId: user.id });

// DON'T
console.log('User signed up');
```

Pino logs are structured JSON, correlated with `X-Request-Id`, and automatically included in Sentry events and OpenTelemetry traces.

## Error Handling

### Choosing a status code

The status is part of the API contract — clients branch on it. Pick by what the caller can _do_
about the answer, not by what is nearest to hand. `400` and `500` are not defaults.

| Situation                                                                                           | Status | How                                                            |
| --------------------------------------------------------------------------------------------------- | ------ | -------------------------------------------------------------- |
| Request is malformed — bad JSON, an opaque cursor/token that will not decode, an unparseable header | `400`  | `DomainException({ kind: 'malformed' })`                       |
| Body or query failed `class-validator`                                                              | `422`  | Nothing — `ProblemDetailsValidationPipe` owns it               |
| Request understood, but a domain rule says no, or the state it references violates policy           | `422`  | `DomainException({ kind: 'validation' })` (its default)        |
| No session, or the session expired — re-authenticating would fix it                                 | `401`  | `UnauthorizedException`                                        |
| Session is valid, but this principal may not do this — wrong role, deactivated/banned account       | `403`  | `ForbiddenException`                                           |
| Resource does not exist — or exists, but the caller must not learn that it does                     | `404`  | `DomainException({ kind: 'not_found' })` / `NotFoundException` |
| State conflict a retry could resolve — duplicate key, concurrent update, work still in flight       | `409`  | `DomainException({ kind: 'conflict', permanent: false })`      |
| The **request body itself** is over the size limit                                                  | `413`  | Fastify body limit — never hand-rolled                         |
| Rate limit hit                                                                                      | `429`  | `@fastify/rate-limit` / `ThrottlerGuard`                       |
| The server broke — DB down, S3 unreachable, a bug                                                   | `500`  | `InternalServerErrorException`, with no specific `code`        |

The three distinctions that actually get chosen wrong:

- **401 vs 403** — ask whether authenticating again would help. It cannot help a banned account, so
  that is `403`. Answering `401` traps any client that reacts to it by redirecting to login: it gets
  a fresh valid session and the very next request `401`s again, forever.
- **400 vs 422** — ask whether the server understood the request. `{"key":"uploads/a.png"}` is
  understood perfectly; refusing it because the stored object is oversized is `422`. A cursor that
  will not base64-decode was never understood at all: `400`.
- **413 describes the request payload**, nothing the request refers to. A 40-byte confirm call that
  names an oversized S3 object is `422` — answering `413` tells the client to shrink the wrong thing.

`5xx` responses carry the generic code for their status. Never attach a specific one: the internal
failure taxonomy is not the client's business.

All `5xx` details are always masked on REST, direct Fastify routes, Better Auth, GraphQL, WebSocket
handshakes, and admin surfaces, regardless of `NODE_ENV`. A `messageKey` does not bypass this rule:
its interpolation arguments are runtime data and therefore are not a security boundary. Local
development is not an exception: database/driver messages
can contain credentials, topology, SQL, table names, or personal data copied from bound values.
Debug through structured logs and Sentry using `requestId`; never make a client response more
verbose to improve developer diagnostics.

Expected database conditions must be translated at the repository/application boundary (for
example, Prisma `P2002` to a domain conflict). The global exception filter is only the final
defense-in-depth layer: it maps a small set of known Prisma codes and turns every other
`PrismaClient*` error into a generic internal failure.

### Error codes

`code` is what clients branch on programmatically. Set it explicitly whenever the caller could
reasonably act differently per cause (`invalid_cursor`, `user_not_found`, `idempotency_key_mismatch`).
Leave it unset when the status alone says everything — the filter then fills in the generic code for
that status (`unauthorized`, `conflict`, …). Codes are `snake_case`; see `ERROR_CODES`.

### Domain & application violations

Throw `DomainException` from `@nestjs-fastify-nx/core` for domain violations. It is a plain `Error`
carrying **no HTTP status** — the same handlers run under REST, GraphQL and the scheduler, so the
transport derives the status from `kind`. Its `errors[]` shape matches validation failures so the
frontend renders both through one path. Full guide: [error-handling.md](./error-handling.md).

```typescript
if (await this.users.exists(email)) {
  throw new DomainException({
    // `kind` is the only decision this layer makes; the filter maps conflict → 409. Pass `title`
    // whenever the kind is not 'validation', because the default title only reads right there.
    kind: 'conflict',
    // A duplicate can disappear before the next attempt, so this one is worth retrying.
    permanent: false,
    title: I18N_KEYS.common.conflict,
    code: ERROR_CODES.USER_ALREADY_EXISTS,
    messageKey: I18N_KEYS.errors.users.already_exists,
    violations: [
      {
        path: 'email',
        code: 'already_exists',
        message: 'That email is already registered',
        messageKey: I18N_KEYS.errors.users.already_exists,
      },
    ],
  });
}
```

Pass `messageKey`/`title` as i18n keys, not literals: `GlobalExceptionFilter` only translates dotted
strings, so a literal silently ships English to every locale.

### Input Validation

Use `class-validator` decorators on DTOs. The global `ProblemDetailsValidationPipe` automatically
converts validation failures to RFC 9457 Problem Details with a flat `errors[]` array. It never
echoes the rejected value; `path`, `rule`, and safe constraint metadata are sufficient for clients.

```typescript
export class CreateUserDto {
  @IsEmail()
  email: string;

  @MinLength(8)
  password: string;
}
```

**Never** hand-roll error responses — the global filter owns the shape.

## DTOs

**Application and presentation DTOs are separate, deliberately.** An application DTO is the transport
type a handler returns; a presentation DTO is the HTTP shape. Reusing one for both is what makes the
application layer import `@nestjs/swagger`, and the layering is gone the moment it does — a handler
must stay callable from REST, GraphQL, or a queue consumer without dragging HTTP along.

```typescript
// libs/modules/users/src/application/dtos/user-list-item.dto.ts
// Pure TS. No @nestjs/swagger, no class-validator — nothing framework-shaped.
export interface UserListItemDto {
  id: string;
  email: string;
  role: string;
}

// libs/modules/users/src/presentation/dto/auth-response.dto.ts
// Swagger + class-validator decorators live here and nowhere else.
export class UserListItemResponseDto {
  @ApiProperty()
  id!: string;

  @ApiProperty()
  email!: string;

  @ApiProperty()
  role!: string;
}
```

The duplication is the point: it buys the boundary. Sensitive columns are kept out of responses by
projecting into a purpose-built DTO that simply never declares them — not by `@Exclude`.

**Re-export from module barrel only what is public.** Keep domain entities and repositories private. Command/query **handlers stay private** — they are registered with the global `CommandBus`/`QueryBus` by `CqrsModule.forRoot()`'s explorer and are never injected directly. Export the **command/query classes + their result types** so composition libs and cross-cutting resolvers can dispatch `commandBus.execute(new SomeCommand(...))` / `queryBus.execute(new SomeQuery(...))`. Queries/commands extend `Query<TResult>`/`Command<TResult>` so `execute()` infers the return type.

```typescript
// libs/modules/users/src/index.ts
export { UsersModule } from './users.module';
export type { UserListItemDto } from './application/dtos/user-list-item.dto';
// Query class + result type — consumers dispatch via QueryBus, no handler injection.
export {
  GetUserProfileQuery,
  type UserProfileResult,
} from './application/queries/get-user-profile/get-user-profile.query';

// Don't export: User (entity), UserRepository, GetUserProfileHandler (handlers are
// found by the CqrsModule explorer, so they never need to leave the module).
```

## Module Boundaries

**Enforce strict DDD boundaries** via `@nx/enforce-module-boundaries` in `eslint.config.mjs`. Lint errors on imports must be fixed by extracting to a composition lib — never relax the rule.

```typescript
// DO: composition lib aggregates
// libs/composition/admin/src/controllers/admin-users.controller.ts
export class AdminUsersController {
  constructor(
    private readonly usersModule: UsersModule,
    private readonly auditModule: AuditLogModule,
  ) {}
}

// DON'T: direct cross-module imports
// libs/modules/users/src/… → libs/modules/audit-log/src/…
```

See `docs/architecture.md` → Module Boundary Rules for the full dependency matrix.

## Testing

**Ratio: integration > e2e > unit.**

- **Integration**: Test business logic with real Postgres (Testcontainers). This is the primary test type.
- **E2E**: Test full HTTP stacks with Supertest and `createTestApp()`.
- **Unit**: Test pure logic (utilities, value objects) without I/O.

**Never mock the database.** Use Testcontainers (see `libs/testing/src/lib/database-cleaner.ts`).

```typescript
describe('CreateUserHandler', () => {
  let handler: CreateUserHandler;
  let prisma: PrismaService;

  beforeEach(async () => {
    const module = await Test.createTestingModule({
      providers: [
        CreateUserHandler,
        { provide: PrismaService, useValue: TestcontainersPrisma.client },
      ],
    }).compile();

    handler = module.get(CreateUserHandler);
    prisma = module.get(PrismaService);
  });

  it('creates a user and publishes domain event', async () => {
    const result = await handler.execute(new CreateUserCommand('user@example.com', 'password'));
    expect(result.id).toBeDefined();

    // Query real Postgres
    const user = await prisma.user.findUnique({ where: { id: result.id } });
    expect(user.email).toBe('user@example.com');
  });
});
```

## Conventions

| Convention     | Style       | Example                           |
| -------------- | ----------- | --------------------------------- |
| File names     | kebab-case  | `create-user.handler.ts`          |
| Class names    | PascalCase  | `CreateUserHandler`               |
| Function names | camelCase   | `publishUserRegistered()`         |
| JSON keys      | camelCase   | `{ userId: '...', email: '...' }` |
| Error `code`   | snake_case  | `duplicate_email`, `not_found`    |
| HTTP headers   | kebab-case  | `X-Request-Id`, `Content-Type`    |
| Env vars       | UPPER_SNAKE | `AUTH_RATE_LIMIT_MAX`             |

## Comments

**Only WHY comments.** Remove WHAT narration — code reads for itself.

```typescript
// DO: Why
// Retry only transient errors; permanent constraint violations must fail immediately
if (error.code === 'P2002') {
  // Unique constraint
  throw error;
}

// DON'T: What
// Check if error code is P2002
if (error.code === 'P2002') {
  throw error;
}
```

Never reference task IDs, callers, or "added for X" — those belong in PR descriptions.

## Email Job Idempotency

`EmailNotificationProcessor` records successful sends in Redis by `job.id` for 30 days and reuses a deterministic SMTP `Message-ID`. Normal BullMQ redelivery after a completed attempt is therefore suppressed. Raw SMTP is still an at-least-once boundary: a worker crash after the SMTP server accepts a message but before Redis records success can redeliver it. The stable `Message-ID` lets providers and mail clients deduplicate that rare case.

**Consequence for jobId design:** every email job whose `jobId` is reused within 30 days will be silently deduplicated. This is correct for stalled-recovery (same logical send), but wrong if the intent is a genuine re-send triggered by the user.

Rules for producers that call `queue.add('email-notification', payload, { jobId: '...' })`:

- **Single-fire flows** (welcome email, password-reset, verification): use `${purpose}__${event.eventId}` — the outbox eventId is unique per event, so no dedup window applies across separate sends.
- **Resendable flows** (manual "resend verification", ops retrigger): include a timestamp or nonce — e.g. `${templateId}__${to}__${Date.now()}` — so each user-initiated resend produces a distinct jobId and bypasses the SETNX guard.
- **Anti-pattern** (will silently drop resends within 30 days): `${userId}__${type}` with no timestamp. If a user requests two verification emails within that window only the first will be delivered.

## HTTP Idempotency & Request Timeout

Two cross-cutting resilience layers wrap every HTTP request.

**Idempotency-Key (Stripe pattern)** — a Fastify plugin (`register-idempotency.ts`,
wired in `main.ts` before `@fastify/compress`) guards mutating `/api/v1/*` requests
that carry an `Idempotency-Key` header. It runs at the Fastify layer, not as a Nest
interceptor, because `preHandler`/`onSend` have native access to the final status +
serialized body — a Nest interceptor cannot replay the exact response since Nest sets
the status after the interceptor chain.

- First request wins an atomic `SET NX` lock (Redis cache DB 5), runs, and its 2xx
  response is stored and replayed byte-for-byte on retries (`Idempotent-Replayed: true`).
- Concurrent duplicate → `409 idempotency_key_conflict`; key reused with a different
  body (fingerprint = `method + url + body`) → `422 idempotency_key_mismatch`; malformed
  key → `400 idempotency_key_invalid`.
- Scope: the store key hashes the session token (or client IP for anonymous) with the
  key, so principals never read each other's cached response.
- **Fail-open** on a Redis error (mirrors the throttler). Non-2xx responses release the
  lock so the client may retry — safe because command handlers roll their transaction
  back on error, leaving no committed side effect to duplicate.
- **Invariant:** `IDEMPOTENCY_LOCK_TTL_SECONDS * 1000 > HTTP_REQUEST_TIMEOUT_MS` (validated
  on boot) so a finishing request always still owns its lock — preventing a lock-steal.

**Request timeout** — a global `TimeoutInterceptor` (`HTTP_REQUEST_TIMEOUT_MS`, default
30s) aborts a handler that runs too long with `504 request_timeout`. WebSocket handlers
are exempt. Node cannot cancel the orphaned promise, so background work still completes;
the client just stops waiting and the socket is freed. Auth routes (`reply.hijack()`)
bypass the Nest pipeline and are unaffected.

## Read Replica Routing

`PrismaService` exposes two clients: `db` (primary write) and `dbRead` (replica
or alias to `db` when `DATABASE_REPLICA_URL` is unset). Both clients apply the
shared soft-delete extension: reads and mutations of soft-deletable models are
automatically scoped to `deletedAt IS NULL`, and physical deletes are rejected.

Pass `{ withDeleted: true }` to `getDb()` / `getDbRead()` only for explicit lifecycle
operations such as restore, audit, maintenance, and test cleanup. Plain `db` / `dbRead`
remain the safe default and exclude deleted records. Prisma client extensions cannot
inspect raw SQL, so every `$queryRaw` / `$executeRaw` statement against a soft-deletable
table must still include `deletedAt IS NULL` explicitly.

**Rules:**

- `findMany` / `count` / `aggregate` for list endpoints **MUST** use
  `prisma.dbRead`. These reads tolerate the typical 10–50 ms replication lag.
- Single-row PK/UK lookups (`findUnique`/`findFirst` by id or natural key)
  **MUST** use `prisma.db`. A request that writes then reads the same row on
  the same handler (post-signup → `/users/me`) is the rule, not the exception
  — replica lag would surface as 404. Point-lookup cost on primary is
  negligible.
- `exists`-style pre-checks before a write may use `prisma.dbRead`: a
  false-negative on lag is safe because the unique constraint enforces
  correctness at write time.
- Better Auth uses `prisma.getDb({ withDeleted: true })` because its schema is outside
  the business soft-delete policy. Outbox relay and interactive transactions remain
  bound to the primary client.

## Raw SQL

Raw SQL is allowed, but it is the one place in this repository where the safety nets are off: the
Prisma client extensions cannot see it, the result type is unchecked, and one of its two building
blocks splices text verbatim. Reach for it only when the Prisma query API genuinely cannot express
the statement.

**Justified reasons** (each is in `prisma-sku-mapping.repository.ts`, which is the reference
implementation for this section):

- **Row locks.** Prisma has no `FOR UPDATE` API at all. A check-then-write that must not race has
  no alternative.
- **Aggregates and expressions Prisma cannot build**: `MIN()` over a derived expression,
  `COUNT(*) FILTER (WHERE …)`, `LATERAL`, `CASE` columns that are then filtered or grouped on.
- **Avoiding N+1.** Computing a per-row derived value (availability, coverage) in TypeScript means
  one query per row on exactly the screens that page thousands of rows.
- **Row-value cursor comparison**: `(sortField, id) < ($1, $2)` matches a composite index; the `OR`
  expansion Prisma would emit does not.
- **Reading another module's tables without importing it.** `scope:modules` may not import another
  `scope:modules` (see [Module Boundaries](#module-boundaries)). A single narrowly scoped raw read
  keeps the boundary intact where an import would break it. Confine it to one method and say why in
  a comment.

**`Prisma.sql` and `Prisma.raw` are opposites — know which one you are holding:**

|                                | Behaviour                                                                                                                                                                      | Safe for user input?                       |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------ |
| `` Prisma.sql`… ${value} …` `` | Tagged template. Every `${}` becomes a **bound parameter** (`$1`, `$2`) sent separately from the statement text. Nested `Prisma.Sql` fragments compose and renumber correctly. | **Yes.** The value is never parsed as SQL. |
| `Prisma.raw(text)`             | Splices `text` into the statement **verbatim, unescaped**.                                                                                                                     | **No.** This is the injection surface.     |

**Rules:**

- Interpolate every value through `Prisma.sql`. Never build a statement by string concatenation.
- `Prisma.raw` is for **identifiers only** — a table alias or column name, which a bound parameter
  cannot stand in for. Its parameters **MUST** be typed as a closed union of the literals actually
  used, never `string`:

  ```ts
  type SkuAlias = 'ms' | 'held_sku' | 'candidate';
  function expandCombo(skuAlias: SkuAlias, joinAlias: ComboJoinAlias): Prisma.Sql;
  ```

  A `string` parameter compiles fine today and becomes an injection the first time someone passes a
  variable. The union makes that a type error instead.

- `$queryRawUnsafe` / `$executeRawUnsafe` take positional parameters — pass caller- or env-derived
  values through them, and keep interpolated fragments code-owned literals. `purgeInBatches`
  (`apps/scheduler/src/app/tasks/purge-in-batches.ts`) documents that split at its call signature;
  follow it.
- **Carry the predicates the extensions would have injected.** `tenantGuardExtension` and
  `softDeleteExtension` stop at the client layer. Every raw statement must spell out `"tenantId"` on
  **each table it touches** (not just the main one), `"deletedAt" IS NULL` on every soft-deletable
  table, and the caller's shop/tenant scope. Forgetting one leaks across tenants silently — nothing
  raises.
- **Every raw query needs an integration test on real Postgres**, including a cross-tenant case. The
  generic on `$queryRaw<Row[]>` is an _assertion_, not validation: rename a column in the SQL and
  TypeScript stays quiet until a field reads `undefined` at runtime.
- **Cast counts to `::int`.** Postgres returns `COUNT(*)` as `bigint`, which `JSON.stringify` throws
  on.
- **Escape `LIKE` wildcards** from user input and declare `ESCAPE`. Unescaped `%` is not injection
  but it is a full table scan a user can trigger by typing one character (`toLikePattern`).
- **No backticks inside a `Prisma.sql` comment.** The statement is a template literal, so a backtick
  in an SQL comment terminates it and breaks the parse.

## Tenant-Scoped Tables & Isolation

The concept is called **`tenant`** everywhere in code — never `workspace`, `organization` or
`company`, in any identifier including `operationId`s. `workspace` is reserved for the frontend
monorepo package prefix and the Nx workspace root. See [tenancy.md](./tenancy.md#naming).

Every new tenant-owned Prisma model must follow these rules:

1. Declare `tenantId String @db.Uuid` as its first non-id field.
2. Lead its primary lookup index with `tenantId` (e.g. `@@index([tenantId, createdAt(sort: Desc)])`).
3. Add the model name (PascalCase) to `TENANT_SCOPED_MODELS` in `libs/infra/database/src/lib/tenant-scoped-models.ts`. The Prisma `tenantGuardExtension` runtime guard automatically blocks query-based reads/writes (`findMany`, `findFirst`, `findFirstOrThrow`, `findUnique`, `findUniqueOrThrow`, `update`, `updateMany`, `upsert`, `delete`, `deleteMany`, `count`, `aggregate`, `groupBy`) lacking a safe `tenantId` filter. Note that `findUnique` and `upsert` on tenant-scoped models require a `tenantId`-bearing compound unique key (e.g. `@@unique([tenantId, featureKey])`). Models with system-wide unique primary keys (listed in `ID_SCOPED_MODELS`) may be queried via single scalar `id`. Where legitimate tenant lookup operations occur before tenant context is resolved, use the reviewed `dbUnguarded`/`dbReadUnguarded` escape hatch explicitly.
4. Include a cross-tenant isolation test covering the model.

_Cautionary example:_ Pre-fix `AuditLog` shipped without a `tenantId` column, making tenant-scoped query filtering impossible until migrated.

## Tenant Feature Flags & Route Guarding

- **Guard:** Use `@RequireFeatureFlag('featureKey')` with `FeatureFlagGuard`.
- **Catalog:** Register every toggleable key in `FEATURE_FLAG_CATALOG`; admin writes reject unknown
  keys so a typo cannot create a dead database row.
- **Reads:** Tenant code calls `GET /api/v1/feature-flags`; admin code calls
  `GET /api/v1/admin/feature-flags?tenantId=...`. Both are cursor lists. Tenant responses omit
  operator audit identity; admin responses include it.
- **Default:** A catalog key without a tenant override is disabled. Do not create disabled rows only
  to make a flag appear in the list.
- **Response Contract:** On block, `FeatureFlagGuard` throws `DomainException` carrying `ERROR_CODES.FEATURE_NOT_ENABLED` (422/403).
- **Two Independent Locks:** `FeatureFlagGuard` operates independently of `TikTokScopeGuard` (which throws `TIKTOK_SCOPE_NOT_GRANTED`). When stacked on a route, each guard reports its own distinct error code so the frontend can render specific UI feedback (e.g., "Feature not in beta" vs. "Re-authorize TikTok Shop").

## Production Quality

- No half-finished code on main
- No `TODO` comments without a task ID and plan to fix
- No temporary solutions or hacks
- All code paths tested (including error paths)
- All breaking changes documented in `CHANGELOG.md`
