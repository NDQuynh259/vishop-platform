# TikTok Shop Integration

How this codebase talks to the TikTok Shop Open API: where the code lives, how a shop
gets connected, how to make a call, and what to look at when something breaks.

Environment variables are in [environment.md](./environment.md#tiktok-shop).

## Where the code lives

| Library              | Scope           | Holds                                                                  |
| -------------------- | --------------- | ---------------------------------------------------------------------- |
| `libs/infra/tiktok`  | `scope:infra`   | OAuth, request signing, token storage, rate limiting, webhook verifier |
| `libs/modules/shops` | `scope:modules` | HTTP routes, request DTOs, command handlers                            |
| `libs/infra/tenancy` | `scope:infra`   | Which tenant a request acts for                                        |

The split matters: the worker and scheduler load `infra-tiktok` and neither serves HTTP.
Putting a controller there would drag `@nestjs/swagger` and the whole presentation layer
into two processes that never use it.

## Connecting a shop

```
FE                        api                          TikTok
 │                         │                             │
 ├─ GET /oauth/authorize ─▶│                             │
 │                         ├─ mint state (Redis, 10 min) │
 │◀── { url } ─────────────┤                             │
 ├─────────── redirect the seller to url ───────────────▶│
 │                         │                             ├─ seller consents
 │                         │◀── GET /oauth/callback ─────┤   (code + state)
 │                         ├─ consume state              │
 │                         ├─ POST token/get ───────────▶│
 │                         ├─ encrypt + store grant      │
 │                         ├─ GET authorized_shops ─────▶│  (resolves shop_cipher)
 │                         ├─ check BR-02-01 (tenant)    │  (throws 409 if linked elsewhere)
 │◀── { shops: [{ shopId, shopName }] } ┤               │
```

`state` is single-use and carries the tenant, so the callback — which arrives with no
session cookie of ours — still lands on the right tenant. It is consumed _before_ the code
exchange, so a replayed callback cannot start a second exchange.

**BR-02-01 Enforcement**: A physical TikTok shop (`externalShopId`, unique together with `platform`) can only be linked to a single tenant at a time. If the callback resolves a shop already belonging to another tenant, a `409 Conflict` (`TIKTOK_SHOP_OWNED_BY_ANOTHER_TENANT`) is thrown. Newly connected shops start with `PENDING_INITIAL_SYNC` status before initial sync completes.

**"At a time" is load-bearing**: both `TikTokShop.@@unique([tenantId, openId])` and `@@unique([externalShopId])` are partial (`WHERE "deletedAt" IS NULL`), not global. A disconnect (soft-delete) followed by a reconnect must not collide with the row it is disconnecting — `storeTokens`'s upsert runs outside the transaction `saveShopIdentity` commits in, so on a global unique the second call would 500 against the still-live index entry from the soft-deleted row and leave an orphaned `PENDING_INITIAL_SYNC` row with no `externalShopId`, permanently blocking every later connect attempt for that shop. `findByExternalShopId` reads through `dbReadUnguarded`, which still applies the soft-delete scope — the partial index is what keeps the DB and the app agreeing on what "already connected" means.

Every shop-scoped call after this needs the `shop_cipher` resolved in the last step.

## Calling the API

```typescript
constructor(private readonly tiktok: TikTokHttpClient) {}

const { order_list } = await this.tiktok.request<{ order_list?: Order[] }>({
  tenantId,                            // Tenant ID owning the shop
  shopId,                              // local shops UUID, not TikTok's shop id
  method: 'GET',
  apiPath: '/order/202309/orders',     // full versioned path, exactly as documented
  apiClass: 'standard_read',           // 'heavy_write' | 'standard_write' | 'standard_read'
  tier: TikTokPriorityTier.SYNC_ORDER, // SYNC_ORDER (1) > PUSH_STOCK (2) > PRINT_LABEL (3) > REPORT (4)
  query: { ids: orderId },
});
```

The client handles, per call: rate-limit budget, a valid access token, the HMAC signature,
`shop_cipher`, and turning the response envelope into a `DomainException`.

Two things it deliberately does not do:

- **Inject an API version.** TikTok versions each endpoint separately (`/order/202309/…`,
  `/product/202312/…`), so the caller passes the full path.
- **Retry.** One forced token rotation and replay on an auth rejection is the only retry —
  TikTok does not distinguish "expired" from "revoked" by code, so the replay is what tells
  them apart. Everything else is the caller's decision (usually: let BullMQ retry the job).

### Rate-limit governor

`TikTokRateLimitService` gates every call through a token bucket keyed
`(tenantId, shopId, apiClass)`. Two independent axes on `TikTokRequestOptions`:

- **`apiClass`** — which of TikTok's own published rate-limit tiers the endpoint belongs
  to: `heavy_write` (0.2–1 rps), `standard_write` (1–3 rps), `standard_read` (3–10 rps).
  Picks the bucket and its floor/ceiling. Get this from the actual TikTok endpoint
  classification, not from what the call "feels like" — e.g. a bulk/analytics pull is
  `heavy_write` even though it's a read.
- **`tier`** (`TikTokPriorityTier`) — ViShop's own business priority within whatever
  bucket the call lands in: `SYNC_ORDER` (1) > `PUSH_STOCK` (2) > `PRINT_LABEL` (3) >
  `REPORT` (4), lower number served first. A single `apiClass` bucket can be shared by
  more than one business flow (e.g. đẩy tồn and lấy tem both hitting `standard_write` for
  the same shop); each tier may only drain the bucket down to a reserved floor — 0% for
  `SYNC_ORDER` (may drain it to zero), rising through the lower tiers — so a bulk label
  print or report pull can't starve order sync even when they share a bucket. This is
  checked atomically inside the same Lua script that consumes the token, not by
  coordinating across the separate BullMQ queues each flow runs on.

A bucket starts at its `apiClass` floor and ramps +25% every 15 minutes absent a 429
(`TIKTOK_RATE_LIMIT_RAMP_PCT`/`_RAMP_INTERVAL_MS`). A real 429 drops that specific
`apiClass` bucket back to its floor and locks ramp-up for 15 minutes
(`TIKTOK_RATE_LIMIT_LOCK_ON_429_MS`) — other `apiClass`es for the same shop are
unaffected. `TikTokRateLimitService.markInitialSyncStarted(tenantId, shopId)` marks a
time-bounded window (`TIKTOK_RATE_LIMIT_INITIAL_SYNC_WINDOW_MS`) during which calls
passing `syncMode: 'initial'` get an elevated ceiling for FR-02-04's ≤10-minute initial
sync — the flag is a safety cap, not a trusted claim: a call passing `syncMode: 'initial'`
without an active window from `markInitialSyncStarted` falls back to its normal `tier`
throttling instead of being silently elevated forever.

Full design and the open questions it depends on:
`docs/ticket/FR-00/FR-00-05/implementation-plan.md`.

### Scopes

A seller can decline individual scopes on the consent screen, so what the app asked for is
not what it got. Gate on what was granted:

```typescript
@UseGuards(TikTokScopeGuard)
@RequireTikTokScopes('seller.order.info')
@Get(':shopId/orders')
```

The guard matches only shops owned by the caller's tenant. Someone else's shop answers
`404 tiktok_shop_not_connected`, exactly like one that does not exist — the response must
never let a caller probe which shops are out there.

### Capability catalog and the shop list (FR-02-03/12)

`TIKTOK_CAPABILITY_CATALOG` (`libs/infra/tiktok/src/lib/scopes/tiktok-capability-catalog.ts`) is
the named, human-facing side of the same mechanism: a small closed map from a capability key
(`products`, `ordersFulfillment`, `finance`, `affiliate`, `livePerformance`) to the TikTok scope(s)
it needs, taken from `vishop-build-spec-v1.2.md` §2.2 — corrected there against the actual
registered app scopes, not copied blind. `scopesSatisfy`/`missingScopes` are the single
implementation of `required ⊆ granted`; `TikTokScopeGuard` and `deriveTikTokCapabilities` both call
them, so the route-level gate and the catalog can't drift apart.

`GET /api/v1/shops` (`ShopsController`, `shops.view`) lists every shop for the caller's tenant —
including `REVOKED`/`EXPIRED` ones, so the seller can see and act on a shop that needs
reconnecting; `DISCONNECTED` shops (FR-02-09) are already invisible via the soft-delete extension —
with `grantedScopes` and `capabilities` derived **per shop**, never unioned across a tenant's shops
(BR the AC calls out explicitly: two shops with different grants must never make each other look
more capable than they are). This is the FE's source for greying out a locked feature with a
reason, instead of discovering it from a failed request against a route guarded by
`TikTokScopeGuard`. `ShopsController` is the single controller for every single-shop operation —
listing, renaming (`nickname`, FR-02-02), and the inventory-push switch (FR-05-11) — there is no
separate "authorizations" controller despite the FR-02-12 ticket name; the "authorizations" wording
described a capability lens on this same list, not a distinct HTTP surface.

Adding a capability: it must first exist as a row in build-spec §2.2 with a concrete scope: this
repo does not invent scope requirements. Then add one entry to `TIKTOK_CAPABILITY_CATALOG` and one
field to `TikTokCapabilitiesDto` (`libs/modules/shops/src/presentation/dto/shop-list-item.dto.ts`).

## Background work

| Queue                   | Producer                                  | Processor                      |
| ----------------------- | ----------------------------------------- | ------------------------------ |
| `tiktok.sync-orders`    | webhook                                   | `TikTokSyncOrdersProcessor`    |
| `tiktok.webhook-deauth` | webhook                                   | `TikTokWebhookDeauthProcessor` |
| `tiktok.push-stock`     | none yet                                  | `TikTokPushStockProcessor`     |
| `tiktok.token-refresh`  | scheduler cron                            | `TikTokTokenRefreshTask`       |
| `tiktok.initial-sync`   | `ConnectShopHandler` (post-OAuth-connect) | `TikTokInitialSyncProcessor`   |
| `tiktok.sync-inventory` | none yet                                  | none yet                       |
| `tiktok.reports`        | none yet                                  | none yet                       |

Webhook jobs are keyed `tiktok-webhook__<webhookEventId>`. Inbound payloads are stored verbatim in the `TikTokWebhookEvent` inbox table prior to queueing, deduped via a unique constraint on TikTok's `tts_notification_id`. Webhook jobs carry `webhookEventId` referencing the inbox row, enabling accurate payload replay from the DLQ.

### Webhook Inbox & Event Routing (`TikTokWebhookEvent`)

The single public endpoint `POST /tiktok/webhook` receives all TikTok Shop push events. The request flow is:

1. **HMAC Signature Verification:** Verifies `Authorization` header using HMAC-SHA256(appSecret, appKey + rawBody). Rejects with HTTP 403 on mismatch.
2. **Event-Type Resolution:** Resolves numeric `type` (1–68) into string `eventType` (e.g. `ORDER_STATUS_CHANGE`, `SELLER_DEAUTHORIZATION`, `PACKAGE_UPDATE`) via `TIKTOK_WEBHOOK_TYPE_MAP`. Unmapped types resolve to `UNKNOWN`.
3. **Inbox Persistence & Dedup:** Stores raw payload in `platform_webhook_events`. The unique constraint `@@unique([eventId])` on `tts_notification_id` stops redeliveries at the DB layer, returning `{ enqueued: false, duplicate: true }` without re-enqueueing.
4. **Queue Dispatch:**
   - Order-status events (`ORDER_STATUS_CHANGE`, `CANCELLATION_STATUS_CHANGE`, `ORDER_RETURN_STATUS_CHANGE`, `REVERSE_STATUS_UPDATE`, `RECIPIENT_ADDRESS_UPDATE`) dispatch to `tiktok.sync-orders`.
   - Deauth/expire events (`SELLER_DEAUTHORIZATION`, `AUTH_EXPIRE`) dispatch to `tiktok.webhook-deauth`.
   - Unbuilt event groups (`PACKAGE_UPDATE`, `PRODUCT_*`) are stamped `status: 'UNHANDLED'` and stored without queueing until their downstream modules exist.

### Webhook registration (`tiktok.webhook-subscription`, FR-00-13)

Receiving events is only half of it — TikTok pushes nothing until the shop is registered, and both
Event API endpoints require `shop_cipher`, so registration is per shop rather than per application.
A missing registration raises no error and writes no log: it looks exactly like a quiet shop.

- **Domain port:** `EventSubscriptionProviderPort` (`libs/core/src/lib/channel/`). Topics are ViShop's
  own (`ChannelEventTopic`); `TikTokEventSubscriptionProvider` translates them to the platform's
  `event_type` enum. That translation table is separate from `TIKTOK_WEBHOOK_TYPE_MAP` on purpose —
  the inbound map calls two topics `AUTH_EXPIRE` and `ORDER_RETURN_STATUS_CHANGE`, both of which
  already reached the published OpenAPI contract and cannot be renamed, and neither is a string the
  registration endpoint accepts.
- **Eight topics** are registered: order status, cancellation, return, reverse, recipient address,
  package, seller deauthorization, upcoming authorization expiration. Product topics are left out
  until something consumes them — an unread topic only fills the inbox with `UNHANDLED` rows.
- **Triggers:** the `tiktok.shop_connected` and `tiktok.shop_disconnected` outbox events, plus a
  6-hourly sweep over every active shop (`TikTokWebhookSubscriptionReconcileTask`). Registration runs
  through a listener rather than inside `ConnectShopHandler` so that a failure to register can
  never fail the connect.
- **State:** `shop_webhook_subscriptions`, one row per (shop, topic), with `desiredState` (what
  ViShop wants) separate from `observedState` (what Get Shop Webhooks last reported). A read that
  fails records `UNKNOWN`, never `ABSENT` — treating an unreachable platform as "nothing registered"
  would re-register every topic of every shop on every sweep. `GET /shops` surfaces the derived badge
  as `webhookRegistrationStatus`.
- **Still required:** the 6-hourly fallback poll of FR-02-06 stays. The platform's own guidance is not
  to treat webhooks as the only source of truth.
- **Ordering:** a redelivered `SELLER_DEAUTHORIZATION` carries a fresh `tts_notification_id`, so the
  inbox's uniqueness constraint does not stop it. `TikTokWebhookDeauthProcessor` compares the event
  timestamp against `shops.authorizedAt` and ignores an event older than the current grant —
  otherwise revoke → reconnect → redelivery would disconnect a live shop days later.

### Initial sync (`tiktok.initial-sync`, FR-02-04)

Right after `ConnectShopHandler` links a shop identity, the TikTok connector's initial-sync scheduler
enqueues one `tiktok.initial-sync` job (jobId `tiktok-initial-sync__<shopId>__<lockToken>`; a second
connect before the first sync finishes finds the per-shop overlap lock held and skips the enqueue).
`TikTokInitialSyncProcessor` then:

1. Calls `TikTokRateLimitService.markInitialSyncStarted` — opens FR-00-05's elevated-ceiling
   15-minute window for the shop, which every call below tags via `syncMode: 'initial'`.
2. **Product phase**: pages `POST /product/202502/products/search` and upserts each result into
   `PlatformProduct`/`PlatformSku` (`libs/modules/catalog`) via `UpsertPlatformProductCommand`.
   Only search-response fields are populated (title, status, sku id/seller_sku/price/inventory) —
   full per-product detail (`GetProduct`) is deliberately deferred to FR-03-02's nightly refresh
   job, not fetched here, so this phase cannot blow the order-phase SLA on a large catalogue.
3. **Order phase**: pages `POST /order/202309/orders/search` and upserts each result into
   `Order`/`OrderLine` (`libs/modules/orders`) via `UpsertOrderCommand`. The window is the last 30
   days on a genuinely first-ever connect, or — on a reconnect — the actual gap since
   `TikTokShop.lastOrderSyncedAt`, clamped to 90 days (FR-02-07, see §"Reconnect catch-up" below).
   TikTok's response already embeds full `line_items[]` per order — no per-order follow-up call.
   Line items are per-unit, not per-SKU-with-quantity, so `TikTokOrderSyncService` groups them by
   (product_id, sku_id) into one `OrderLine` per SKU before the upsert.
4. **Per-item failures don't sink the job.** Each product/order upsert is wrapped individually —
   a malformed item is logged, counted into `progress.{products,orders}.failed`, and the loop moves
   on, the same partial-success handling `MerchantSkuImport` uses for bad rows. Only a failure of
   the page/API call itself (rate limit, TikTok outage, DB down) aborts and retries the whole job.
5. Progress is written to `TikTokShop.initialSyncProgress` after every page — polled via
   `GET /api/v1/tiktok/shops/:shopId/initial-sync`, following the repo's standard
   `{status, progress: {total, done, failed}}` job/progress convention (§4.1 of the API contract).
6. On success, `TikTokShop.status` transitions `PENDING_INITIAL_SYNC` → `ACTIVE` — even when some
   items landed in `failed`; only the page/API-level failure path in point 4 leaves it stuck. The
   endpoint's `syncStatus` field (`QUEUED | PROCESSING | COMPLETED | COMPLETED_WITH_ERRORS |
FAILED`, derived from `startedAt`/`completedAt`/`failedReason`/the failed counters, never stored)
   is what actually answers "is this running or stuck" — `status` alone can't, since it stays
   `PENDING_INITIAL_SYNC` whether the job is queued, actively running, or has just dead-lettered
   after 6 attempts. On a fully exhausted retry (job DLQs), `progress.failedReason` is set and
   `syncStatus` reads `FAILED`; the shop itself stays `PENDING_INITIAL_SYNC`, no separate alert
   channel.

Both upsert commands key on the existing `(platform, shopId, external<Entity>Id)` composite unique
(see `docs/architecture/domain-module-anatomy.md#marketplace-sync-idempotency`), so a retried job safely re-pages
from the start instead of needing a checkpoint — a page that gets fully retried re-upserts items
already marked `done`/`failed` on a prior attempt, which is safe because the upsert is idempotent,
not because the counters are deduplicated across attempts.

### Retry and dead-lettering

A failed job is retried 5 times on a fixed ladder — 10 s, 1 min, 5 min, 30 min, 2 h — set by
`TIKTOK_BACKOFF_DELAYS`. The ladder is deliberately not a plain exponential: the first two steps
absorb a rate-limit burst within the same minute, and the last two stretch far enough to outlive a
TikTok-side incident without burning the budget on it.

After the 5th retry the job is dead-lettered to `<queue>.dlq` by `DeadLetterRouter`, a
`@QueueEventsListener` in the worker. That stream has no checkpoint, so a `failed` event emitted
while the worker is restarting is missed — `DlqMonitorTask.reconcile()` sweeps every source queue's
terminal-failed jobs every 5 minutes and routes the stragglers. Both paths key the DLQ entry
`dlq__<originalJobId>`, so a job routed twice produces one DLQ record.

DLQs never auto-purge; triage them on Bull Board at `/api/admin/queues`. `DlqMonitorTask` warns once
a DLQ reaches `DLQ_ALERT_THRESHOLD` (default 10) — see runbook §2.

### Manual replay (admin API, FR-00-09)

Bull Board's own DLQ retry already replays a job's exact original payload (job data only carries
`webhookEventId`; the processor re-reads `payload` from the immutable `TikTokWebhookEvent` row) —
but only while the failed BullMQ job still exists. Two cases fall outside that window: a job past
its retention, and a row that was never enqueued at all (`UNHANDLED` — no dispatch entry for its
type at the time, later added). `AdminTikTokWebhookEventsController`
(`libs/composition/admin`) covers both, decoupled from BullMQ/DLQ state entirely:

- `GET /api/v1/admin/tiktok/webhook-events?status=<status>&limit=20&cursor=...` — cursor-paginated browse across every
  tenant (`@Roles('ADMIN')`, not tenant-scoped by design — see the controller's own comment).
  `UNHANDLED`/`DEAD_LETTER` are the statuses worth replaying. Response omits `payload` (buyer PII,
  BR-14-05) — it never leaves the server via this surface.
- `POST /api/v1/admin/tiktok/webhook-events/{id}/replay` — re-enqueues from the persisted row.
  Always resolves the target shop/tenant live from the row's own `externalShopId`
  (`TikTokTokenStoreService.findByExternalShopId`), never from a caller-supplied value — there is no
  tenant parameter to override, so a replay can never be redirected to a different tenant than the
  original delivery. `jobId` is fresh per replay (`tiktok-webhook-replay__<rowId>__<generateId()>`),
  so it is never deduped against the original delivery's retained job. Marks the row `ENQUEUED` and
  publishes `tiktok.webhook_event_replayed` (consumed by the existing generic `tiktok.*`
  `AuditLogListener` handler — no new listener needed) inside one transaction, before the BullMQ
  `.add()` call. If BullMQ rejects the enqueue, the handler conditionally restores the prior inbox
  status so the row is not left falsely marked `ENQUEUED`.
- An event type with no `TIKTOK_WEBHOOK_DISPATCH` entry (never routed anywhere, even on first
  receipt) or whose shop can no longer be resolved 422s (`TIKTOK_WEBHOOK_EVENT_NOT_REPLAYABLE`) —
  replaying it would just repeat exactly what happened the first time.

### Implementation Checklist for Future TikTok Producers & Processors

When adding a producer or processor for still-unbuilt TikTok queues (`PUSH_STOCK`, `SYNC_INVENTORY`, `REPORTS`), follow this checklist:

1. Register the queue for injection where it is produced (`BullModule.registerQueue({ name: TIKTOK_QUEUE_NAMES... })`).
2. Derive a deterministic `jobId` from the triggering event's identity to dedup at enqueue time.
3. Spread `TIKTOK_JOB_OPTIONS` on every `.add(...)` call (`attempts: 6` — BullMQ counts total runs, so this is the first attempt plus 5 retries — and custom backoff type).
4. Decorate `@Processor(TIKTOK_QUEUE_NAMES..., { settings: TIKTOK_WORKER_SETTINGS })`. Note: `backoff: { type: 'custom' }` in job options alone is NOT sufficient — BullMQ resolves custom strategies from `WorkerOptions.settings.backoffStrategy`.
5. Register `DeadLetterModule.forFeature(TIKTOK_QUEUE_NAMES.<NAME>)` for each queue in `apps/worker/src/app/app.module.ts` (the method accepts a single queue name string, called once per queue).
6. Adding the queue to `TIKTOK_QUEUE_NAMES` automatically surfaces both source and `.dlq` queues on Bull Board (`/api/admin/queues`) and connects `DlqMonitorTask` monitoring — no extra UI or scheduler wiring needed.
7. Any side-effecting write in the processor should pass `sourceJobId: job.id` to the command for idempotency protection.

## Tokens

Access tokens live ~24 h, refresh tokens ~365 days. Both are AES-256-GCM encrypted at rest
(`v1:<hex>` format) and never logged.

`access_token_expire_in` and `refresh_token_expire_in` are **absolute epoch seconds**, not a
TTL. Treating them as a TTL yields an expiry roughly 56 years out, so nothing ever refreshes
and every call starts failing the day the real token dies. `toTokenExpiryDate` is what
converts them; do not hand-roll `Date.now() + expire * 1000`.

Rotation is guarded by a Redis lock per shop, so concurrent requests produce one refresh.
The scheduler also rotates ahead of expiry (`TikTokTokenRefreshTask`).

### Reconnect signal (FR-02-05)

`TikTokTokenStoreService.markStatus` is the single choke point every REVOKED/EXPIRED transition goes
through — the refresh-failure paths in `rotate()` (refresh token expired, or TikTok rejected the
grant), and `TikTokWebhookDeauthProcessor`'s `SELLER_DEAUTHORIZATION` handling. On the first
transition into REVOKED/EXPIRED (a no-op if the shop is already in one of those, e.g. a retried
webhook job), it publishes `DOMAIN_EVENTS.TIKTOK_SHOP_DEAUTHORIZED` with `{tenantId, previousStatus,
status, reason}` inside the same `prisma.transaction()` as the status write, so the two commit
atomically. `AuditLogListener` already subscribes to the whole `tiktok.*` stream, so every such
transition lands a queryable `audit_logs` row for free — no new schema needed for that half.

This event is a durable **signal**, not a user-facing notification — no in-app/email delivery
consumes it yet. That is FR-02-06's own scope (`AUTH_EXPIRE` webhook events are already stored by
`TikTokWebhookDeauthProcessor`, parked awaiting exactly this kind of consumer, VIS-62); this event is
its natural hook for the REVOKED/EXPIRED side of "Cần kết nối lại" once that consumer exists.

### Disconnect (FR-02-09)

`DisconnectTikTokShopHandler` (`POST /api/v1/tiktok/shops/:shopId/disconnect`,
`shops.disconnect`) calls the same `markStatus` choke point with `'DISCONNECTED'` —
but that transition is handled differently from REVOKED/EXPIRED: it is **voluntary**, so
`markStatus` also sets `deletedAt` on the row (soft-delete) and publishes
`DOMAIN_EVENTS.TIKTOK_SHOP_DISCONNECTED` instead of `TIKTOK_SHOP_DEAUTHORIZED`. `TikTokShop` is
already in `SOFT_DELETE_MODELS`, so once `deletedAt` is set the shop disappears from every
tenant-facing query (`db`/`dbRead`/`dbUnguarded`/`dbReadUnguarded`) — the shop list, `TikTokScopeGuard`,
`getSyncStatus`, webhook routing — with no extra filtering needed anywhere. The same property is
what stops every background job: `requireActiveShop`/`listActiveShops`/`refreshExpiringTokens`/the
auth-expiry backup sweep were already scoped to `status IN (ACTIVE, PENDING_INITIAL_SYNC)`, so a
disconnected shop drops out of all of them on the next tick without any processor-level change.

This is the deliberate opposite of REVOKED/EXPIRED, which must stay visible so the seller sees
"Cần kết nối lại" — `markStatus` never sets `deletedAt` for those two.

Reconnecting (the same OAuth flow as a first connect, not a dedicated endpoint) resurrects the
exact same row: `storeTokens` reads and upserts through `prisma.dbWithDeleted` (tenant-guarded,
soft-delete OFF) instead of `db`, so it finds the soft-deleted row by `(tenantId, platform, openId)` and its
`update` branch unconditionally clears `deletedAt`. Reusing `db` there would make the row invisible
to `storeTokens` too, and reconnect would create a second row instead of resurrecting the first —
exactly the failure mode the partial unique indexes on `TikTokShop` were built to avoid (see the
BR-02-01 note above).

### Sync activity log (FR-02-08)

`GET /api/v1/tiktok/shops/:shopId/sync-logs` (`shops.view`) is a read-model over the immutable
`EventLog` table — no dedicated mutable table, per the AC. One row per (data type, job run):

```
entityType = 'TIKTOK_SYNC_RUN'
entityId   = shopId
eventType  = 'TIKTOK_SYNC_PRODUCTS' | 'TIKTOK_SYNC_ORDERS'   // the "loại dữ liệu" filter dimension
payloadAfter = {
  syncKind: 'initial' | 'catchup' | 'periodic' | 'manual',
  result: 'completed' | 'failed',
  processedCount, failedCount, errorReason?, startedAt, completedAt,
}
```

`dataType` is encoded directly in `eventType` rather than filtered via a JSON path expression on
`payloadAfter` — it turns "lọc theo loại dữ liệu" into a plain indexed equality (the same
`event_logs_source_job_idx` composite already covers `[tenantId, entityType, entityId, eventType]`)
instead of an untested JSON containment query. `syncKind` stays in `payloadAfter` only — AC2 does
not require filtering by it, just display.

Two writers, both worker-side (`apps/worker`, allowed to depend on both `modules-shops` and
`modules-event-log`; `modules-shops` itself cannot import `modules-event-log` —
`@nx/enforce-module-boundaries` blocks any `scope:modules` → `scope:modules` dependency):

- `TikTokInitialSyncProcessor` writes 2 rows (products, orders) at the end of a run — on success
  from `progress.products`/`progress.orders` (already tracked for FR-02-04's polling endpoint), on
  final-attempt failure with `errorReason`. Covers both **initial** (first connect) and
  **catchup** (FR-02-07 reconnect) — same processor, `kind` from `resolveOrderWindow` becomes
  `syncKind` and `source` (`initial-sync` / `catchup-sync`).
- `TikTokSyncProductsProcessor` writes 1 row only for a whole-shop run (`externalProductId` unset)
  — a single-SKU resync (`products.resync`, FR-03-04) is a different feature and would otherwise
  flood the shop-level log with one row per ad-hoc refresh. `syncKind` is `'manual'` when
  `job.data.source === JOB_SOURCES.USER_ACTION` (dispatched from
  `POST /api/v1/shops/{shopId}/platform-products/sync`, FR-02-10) and `'periodic'` otherwise (the
  nightly cron, `TikTokProductSyncTask`) — same job shape either way, only `source` tells them apart.
- `TikTokSyncOrdersProcessor` writes 1 row for its **shop-wide window** mode (`sinceDays` set,
  `orderId` unset — FR-02-10's manual order resync, `POST /api/v1/tiktok/shops/{shopId}/orders/sync`),
  aggregating counts across every page `TikTokOrderSyncService.searchOrders` returns. `syncKind` is
  `'manual'` for `source: user_action`; `'periodic'` is reserved for a future cron that would
  enqueue the same job shape (none does yet).

The processor's **per-order** mode (webhook-triggered, `orderId` set, 1 job per order) is
deliberately **not** wired into this log — it is not one of the AC's four run kinds
(initial/periodic/catchup/manual), and already has its own per-order trail via
`RecordEventLogCommand(entityType: 'ORDER', eventType: 'TIKTOK_ORDER_SYNCED')`, readable through the
existing `GET /api/v1/event-logs`. The two modes share one processor/queue (`SYNC_ORDERS`) but never
overlap: a job carries either `orderId` or `sinceDays`, checked before the per-order path's own
"carried no orderId" warning can fire.

Both writers wrap the `RecordEventLogCommand` dispatch in `.catch()` — a logging failure must never
turn an otherwise-successful sync into a failed job. The shared write-side shape
(`buildTikTokSyncLogEntry`, exported from `modules-shops`) is what keeps the two processors and
the read side (`TikTokSyncLogRepository`) from drifting on the `entityType`/`eventType`/payload
convention.

`ListTikTokSyncLogsHandler` checks the shop exists for the caller's tenant first (`dbRead`,
soft-delete-scoped) — a shop disconnected via FR-02-09 answers `404 tiktok_shop_not_connected` here
too, consistent with every other shop-scoped route.

### Manual sync on demand (FR-02-10)

Two routes, not one polymorphic endpoint — `TikTokScopeGuard`/`@RequireTikTokScopes` gate on a
static decorator, so a single route accepting `dataType` in the body couldn't pick the right TikTok
scope to require at runtime:

- **Products**: `POST /api/v1/shops/{shopId}/platform-products/sync` (FR-03-01/04) — already
  existed before this ticket, already went through the Queue/Idempotency/Rate Governor AC requires.
  FR-02-10 only fixed its sync-log labeling (see above).
- **Orders**: `POST /api/v1/tiktok/shops/{shopId}/orders/sync` (`shops.sync.run`, TikTok scope
  `seller.order.info`) — new. Re-pulls the last 7 days of orders for the whole shop
  (`MANUAL_ORDER_SYNC_WINDOW_DAYS` in `trigger-tiktok-manual-order-sync.handler.ts`), not a full
  backfill.

Neither route needs a dedicated Rate Governor tier: both call the same
`TikTokProductSyncService`/`TikTokOrderSyncService` the automatic flows already use, which always
request `TikTokPriorityTier.SYNC_ORDER` — manual and automatic runs share one budget, so a manual
trigger can never out-prioritize the automatic order-sync flow (AC3). The order-window path also
deliberately never calls `rateLimit.markInitialSyncStarted` — `searchOrders` always requests
`syncMode: 'initial'` internally, and with no active initial-sync window that falls back to normal
per-shop throttling, which is exactly what a manual resync should get instead of the elevated
initial-sync budget.

"Tồn kho" (inventory) is out of scope: `TIKTOK_QUEUE_NAMES.SYNC_INVENTORY` is a declared queue name
with no processor behind it — there is no pipeline to reuse yet.

### Reconnect catch-up (FR-02-07)

`ConnectShopHandler` schedules the same `tiktok.initial-sync` job for a first-ever connect and
a reconnect — `TikTokTokenStoreService.storeTokens` upserts by `(tenantId, platform, openId)`, so a reconnect
always resolves back onto the same shop row, and its pre-upsert `lastOrderSyncedAt` (returned as
`previousLastOrderSyncedAt`) is carried into the job as `catchUpSinceIso`.

`TikTokInitialSyncProcessor` uses that to size the order-search window instead of the fixed 30-day
first-connect default:

- No `catchUpSinceIso` → `kind: 'initial'`, fixed 30-day window (unchanged FR-02-04 default).
- `catchUpSinceIso` present → `kind: 'catchup'`, window = `ceil(now - lastOrderSyncedAt)` days,
  **always fully covered, never truncated** (14/08/2026: an earlier revision clamped this to 90
  days and dropped anything older — review correctly rejected that as silent data loss against the
  AC's own "kéo bù đơn phát sinh trong khoảng gián đoạn" wording, no cap stated). `progress.gapDays`
  carries the actual gap size for display. A gap wider than
  `CATCH_UP_WARN_THRESHOLD_DAYS` (180) only logs a structured warning — the run still covers the
  full gap, just takes longer. There is no hard cap: the watermark advances only after the full
  paged sweep succeeds, so old orders cannot be skipped and then marked as synchronized.
- A window wider than 30 days is paged in sequential 30-day sub-windows (newest first), not one call
  — TikTok's OAS doesn't document a range ceiling for `create_time_ge`/`create_time_lt`, so this is
  ViShop's own defensive chunking, not a confirmed API limit.
- `TikTokShop.lastOrderSyncedAt` only advances (to the sweep's _start_ timestamp) after **both**
  phases fully succeed — same gate as the `PENDING_INITIAL_SYNC → ACTIVE` transition, so a job that
  dies partway through leaves the watermark untouched and the next attempt recomputes the same gap.
  It is never advanced by `TikTokSyncOrdersProcessor` (the single-order webhook path), which would
  otherwise mask a real gap by making a partial sync look like a full sweep.
- Products/inventory need no equivalent — `TikTokProductSyncService.searchProducts` already re-lists
  the entire catalogue on every run with no time filter, so a reconnect fully re-covers them by
  construction.
- The status endpoint (`GET .../initial-sync`) exposes `progress.kind`/`progress.gapDays` so the FE
  can distinguish "đang đồng bộ khởi tạo" from "đang kéo bù dữ liệu gián đoạn" instead of both
  reading as the same first-connect flow.
- `ConnectShopHandler` also cleans up after a BR-02-01 rejection: if `storeTokens` just
  created the shop row (`isNewShop: true` — no prior `(tenantId, platform, openId)` match) and the
  cross-tenant check then rejects it, the row is deleted (`deleteUnclaimedShop`) rather than left
  behind as a token-holding phantom stuck at `PENDING_INITIAL_SYNC` forever. A reconnect onto an
  _existing_ shop (`isNewShop: false`) never takes this branch — that row's order/product/event-log
  history must survive a rejected re-auth untouched.

## When something breaks

The one thing to internalize: **a rejected call still returns HTTP 200.** TikTok reports
failure in the envelope (`{ code, message, request_id }`), so status alone tells you nothing.

Three things are always logged, regardless of any flag:

| Situation                             | Level   | Log message                                         |
| ------------------------------------- | ------- | --------------------------------------------------- |
| Envelope `code` ≠ 0                   | `warn`  | `TikTok rejected the request`                       |
| Transport failure (timeout, DNS, 5xx) | `error` | `TikTok call failed before it produced an envelope` |
| Webhook signature mismatch            | `warn`  | `TikTok webhook signature verification failed`      |

Each carries `tiktokRequestId` — quote it when opening a ticket with TikTok; it is how they
find the call on their side.

For the full picture during local work, set `TIKTOK_TRACE_HTTP=true` and `LOG_LEVEL=debug`:
every request, response and inbound webhook body is logged, with `app_secret`, `sign`,
`access_token`, `refresh_token` and `auth_code` redacted and payloads over 2 000 characters
truncated. Keep it off elsewhere — the bodies are seller data.

### Traces and metrics

Every call is wrapped in a `tiktok <METHOD> <path>` span carrying `tiktok.code`,
`tiktok.request_id`, `tiktok.shop_id` and `url.path`. This span exists because the
auto-instrumented `http.client` span reads a rejected call as a success — HTTP 200 is HTTP 200.
A forced token rotation and its replay stay inside the same span, marked `tiktok.retried`.

Two metrics, labelled by `api_path` + `outcome` (`ok` / `api_error` / `transport_error`):

| Metric                                | Type      |
| ------------------------------------- | --------- |
| `tiktok_api_requests_total`           | counter   |
| `tiktok_api_request_duration_seconds` | histogram |

Paths are normalized before they become labels — `/product/202309/products/172938.../inventory`
is recorded as `.../products/{id}/inventory`, so one product does not become one time series.
The API version segment (`202309`) is deliberately left intact.

These go out over OTLP, which is enabled in the **worker and scheduler** only. The API's metrics
source of truth is @prometheus-io/client on `/metrics`, so there the span carries this, not a counter.

### Common failures

| Symptom                                    | Cause                                                                 |
| ------------------------------------------ | --------------------------------------------------------------------- |
| `tiktok_oauth_exchange_failed` on callback | `auth_code` already used or expired (they are single-use, ~10 min)    |
| `tiktok_shop_not_connected` on every call  | Grant revoked seller-side, or the shop belongs to another tenant      |
| `tiktok_scope_not_granted`                 | Seller declined that scope — they must re-authorize                   |
| `tiktok_rate_limited`                      | Per-shop budget exhausted, or TikTok answered 429 and paused the shop |
| Webhook 403 with a signature you computed  | Signed a re-serialized body; the digest covers the raw bytes as sent  |

## Adding an endpoint

1. Command or query in `libs/modules/shops/src/application/`, extending `Command<T>` / `Query<T>`.
2. Handler injects `TikTokHttpClient` and dispatches domain work.
3. Route in `presentation/`, thin — `commandBus.execute(...)` and nothing else.
4. Presentation DTO with class-validator + Swagger decorators. Never reuse an application DTO.
5. Unit-test the handler; add an e2e spec for the route.

`ConnectShopHandler` and its spec are the reference implementation.
