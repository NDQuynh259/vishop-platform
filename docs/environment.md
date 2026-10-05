# Environment Variables

Copy `.env.example` to `.env` and fill in the values.

## Database

| Variable                          | Default                                                   | Required    | Description                                                                                              |
| --------------------------------- | --------------------------------------------------------- | ----------- | -------------------------------------------------------------------------------------------------------- |
| `DATABASE_URL`                    | `postgresql://postgres:postgres@localhost:5432/nestjs_db` | Yes         | PostgreSQL connection string                                                                             |
| `DATABASE_DIRECT_URL`             | —                                                         | No          | Bypasses PgBouncer for migrations; also the feature flag that enables the PgBouncer deep-health check    |
| `DATABASE_REPLICA_URL`            | —                                                         | No          | Read replica. When set, `PrismaService.dbRead` routes lag-tolerant reads here                            |
| `DATABASE_REPLICA_POOL_MAX`       | `10`                                                      | No          | Max pool connections against the replica                                                                 |
| `DATABASE_POOL_MAX`               | `20`                                                      | No          | Max pool connections                                                                                     |
| `DATABASE_POOL_MIN`               | `0`                                                       | No          | Min pool connections                                                                                     |
| `DATABASE_IDLE_TIMEOUT_MS`        | `10000`                                                   | No          | Idle connection timeout                                                                                  |
| `DATABASE_CONNECTION_TIMEOUT_MS`  | `5000`                                                    | No          | Connection acquire timeout                                                                               |
| `DATABASE_STATEMENT_TIMEOUT_MS`   | `30000`                                                   | No          | Per-statement timeout                                                                                    |
| `DATABASE_APPLICATION_NAME`       | `nestjs-fastify-api`                                      | No          | Surfaced in `pg_stat_activity`                                                                           |
| `DB_PASSWORD_FILE`                | —                                                         | No          | Docker/Kubernetes secret file injected into password-less DB URLs                                        |
| `DATABASE_SLOW_QUERY_MS`          | `200`                                                     | No          | Logs a `warn` (query template + duration, never params) above this threshold                             |
| `DATABASE_LOG_QUERIES`            | `false`                                                   | No          | Dev only: logs every query at `debug` **with params** (set `LOG_LEVEL=debug` too). Ignored in production |
| `DB_REPLICATION_LAG_THRESHOLD_MS` | `30000`                                                   | No          | Deep-health threshold for physical replica replay lag                                                    |
| `POSTGRES_USER`                   | `postgres`                                                | Docker only | DB username for Compose                                                                                  |
| `POSTGRES_PASSWORD`               | `postgres`                                                | Docker only | DB password for Compose                                                                                  |
| `POSTGRES_DB`                     | `nestjs_db`                                               | Docker only | Database name for Compose                                                                                |
| `POSTGRES_PORT`                   | `5432`                                                    | Docker only | Host port for Compose                                                                                    |

## Tenancy

| Variable                      | Default | Required | Description                                                                                                                                                   |
| ----------------------------- | ------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `TENANT_MEMBER_LIMIT_DEFAULT` | `10`    | No       | Interim default member limit per tenant (count of active members + pending invitations). Will be swapped for subscription plan lookup when M15 Billing lands. |
| `TENANT_INVITATION_TTL_HOURS` | `72`    | No       | Expiration time for pending tenant member invitations in hours.                                                                                               |

## Redis

| Variable                     | Default     | Required | Description                                                    |
| ---------------------------- | ----------- | -------- | -------------------------------------------------------------- |
| `REDIS_CACHE_HOST`           | `localhost` | Yes      | Cache Redis hostname                                           |
| `REDIS_CACHE_PORT`           | `6379`      | Yes      | Cache Redis port                                               |
| `REDIS_QUEUE_HOST`           | `localhost` | Yes      | Queue Redis hostname                                           |
| `REDIS_QUEUE_PORT`           | `6380`      | Yes      | Queue Redis port                                               |
| `REDIS_QUEUE_PREFIX`         | `bull`      | No       | BullMQ key prefix                                              |
| `REDIS_PUBSUB_DB`            | `2`         | No       | Redis DB index used by the Socket.io adapter                   |
| `WS_CONNECTION_LIMIT_PER_IP` | `50`        | No       | Maximum active WebSocket leases per resolved client IP         |
| `WS_SESSION_REVALIDATE_MS`   | `60000`     | No       | Recheck active sessions and renew socket leases; max 5 minutes |

## Storage (S3-compatible)

| Variable                               | Default                 | Required             | Description                                                                                                                                                                                                                                                   |
| -------------------------------------- | ----------------------- | -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `STORAGE_ENDPOINT`                     | `http://localhost:9000` | Yes                  | S3-compatible endpoint (app → storage)                                                                                                                                                                                                                        |
| `STORAGE_PUBLIC_ENDPOINT`              | _(unset)_               | No                   | Browser-facing endpoint for presigned URLs; set when the app reaches storage at an internal hostname (e.g. `http://minio:9000` in containers) the browser can't resolve                                                                                       |
| `STORAGE_ACCESS_KEY`                   | `minioadmin`            | Yes (rotate in prod) | Access key                                                                                                                                                                                                                                                    |
| `STORAGE_SECRET_KEY`                   | `minioadmin`            | Yes (rotate in prod) | Secret key                                                                                                                                                                                                                                                    |
| `STORAGE_BUCKET`                       | `uploads`               | Yes                  | Default bucket name                                                                                                                                                                                                                                           |
| `STORAGE_REGION`                       | `us-east-1`             | No                   | Region used for request signing. It must match what the backend expects — the bucket region on real AWS S3, or whatever a self-hosted backend was configured with (Garage defaults to `garage`). A mismatch fails signing with `AuthorizationHeaderMalformed` |
| `STORAGE_FORCE_PATH_STYLE`             | `true`                  | No                   | Path-style addressing (`endpoint/bucket/key`), which every self-hosted backend serves. Set `false` against real AWS S3, which documents virtual-hosted-style as the supported form                                                                            |
| `STORAGE_CHECKSUM_MODE`                | `WHEN_SUPPORTED`        | No                   | `WHEN_SUPPORTED` lets the SDK attach `x-amz-checksum-crc32` to every upload (AWS S3 and current MinIO accept it). `WHEN_REQUIRED` stops sending it for backends that reject the header — older Ceph RGW, Backblaze B2, some gateways                          |
| `UPLOAD_PRESIGN_EXPIRES_SECONDS`       | `300`                   | No                   | Presigned POST policy lifetime (60–3600 seconds)                                                                                                                                                                                                              |
| `STORAGE_DOWNLOAD_URL_EXPIRES_SECONDS` | `3600`                  | No                   | Signed download URL lifetime (60–86400 seconds)                                                                                                                                                                                                               |
| `MALWARE_SCANNER_ENABLED`              | `false`                 | Prod                 | Enable ClamAV scanning before an upload can become `READY`                                                                                                                                                                                                    |
| `MALWARE_SCANNER_HOST`                 | `localhost`             | No                   | ClamAV daemon host (production worker uses the internal `malware-scanner` service)                                                                                                                                                                            |
| `MALWARE_SCANNER_PORT`                 | `3310`                  | No                   | ClamAV TCP port                                                                                                                                                                                                                                               |
| `MALWARE_SCANNER_TIMEOUT_MS`           | `30000`                 | No                   | Maximum scan duration before the file remains quarantined                                                                                                                                                                                                     |
| `MALWARE_SCANNER_MAX_BYTES`            | `2147483648`            | No                   | Objects larger than this are published with `scanOutcome=SKIPPED_TOO_LARGE` instead of being sent to the scanner. The default is ClamAV's internal 2 GiB per-file ceiling, past which it skips the file — and, without `AlertExceedsMax`, reports it clean    |

**Upload pattern** — clients call `POST /api/v1/upload/presign` to receive a
short-lived (5 min) S3 presigned-POST policy, upload the bytes browser→S3
directly, then call `POST /api/v1/upload/confirm` so the server HEADs the
object and verifies size + MIME via magic-byte detection
(`libs/modules/upload/src/presentation/controllers/file-signature.ts`). The
allow-list is hard-coded to `image/jpeg`, `image/png`, `image/gif`,
`image/webp`, `application/pdf`. Both the multipart parser and the presign
policy honour `UPLOAD_MAX_FILE_BYTES` so the two layers reject at the same
threshold.

Confirmed objects are tracked in `stored_files`: `FINALIZING` before the S3 copy,
`VERIFYING` after durable BullMQ enqueue, then `READY` or `REJECTED` in the worker.
The scheduler removes stale records, committed objects belonging to deleted users,
and objects rejected by verification.

`READY` on its own does not mean the object was scanned. Every scanner has a size ceiling — ClamAV
skips anything past 2 GiB — so `stored_files.scanOutcome` records which happened: `CLEAN` for an
object the scanner actually inspected, `SKIPPED_TOO_LARGE` for one published without inspection.
Anything treating `READY` as "virus-checked" must read `scanOutcome` too.

**Which backend to run.** The bundled MinIO is a development convenience, not a recommendation:
upstream archived the repository in February 2026, the last published image is
`RELEASE.2025-09-07`, and there will be no further security patches. The adapter is plain S3, so
moving off it is a matter of pointing `STORAGE_ENDPOINT` somewhere else — provided the target
implements the three operations this upload flow is built on.

| Operation                                      | Used for                                                  |
| ---------------------------------------------- | --------------------------------------------------------- |
| `PostObject` (presigned POST policy)           | The browser uploads bytes directly, under a size/MIME cap |
| `CopyObject` with `x-amz-copy-source-if-match` | Confirm promotes the exact bytes that were verified       |
| `GetObject` with `Range`                       | Magic-byte inspection without downloading the object      |

Measured against a live container of each, running that flow end to end:

| Backend                    | POST policy | `CopySourceIfMatch` | `Range` | SDK CRC32 | Licence    | Upstream          |
| -------------------------- | ----------- | ------------------- | ------- | --------- | ---------- | ----------------- |
| AWS S3                     | yes         | yes                 | yes     | yes       | commercial | —                 |
| SeaweedFS                  | yes         | enforced            | yes     | yes       | Apache-2.0 | active            |
| Garage 2.x                 | yes         | enforced            | yes     | yes       | AGPL-3.0   | active            |
| MinIO `RELEASE.2025-09-07` | yes         | enforced            | yes     | yes       | AGPL-3.0   | archived Feb 2026 |
| Cloudflare R2              | **no**      | **no**              | yes     | n/a       | commercial | active            |

"enforced" means a stale ETag was actually refused with `PreconditionFailed`, not merely accepted
and ignored — a guard that silently passes is worse than none.

**Cloudflare R2 does not work with this flow as written.** It implements neither `PostObject` nor
`x-amz-copy-source-if-match` ([R2 S3 API compatibility](https://developers.cloudflare.com/r2/api/s3/api/)).
Using it means switching presign to a `PUT` URL and replacing the ETag guard — a code change, not a
configuration change.

Two smaller differences to plan for: Garage implements only the `Expiration` and
`AbortIncompleteMultipartUpload` lifecycle actions, and SeaweedFS ships no `mc`, so both need their
own bucket creation and orphan-expiry setup instead of the bundled `minio-init` one-shot. Neither
accepts a `tagging` field inside a POST policy, which is why staging objects are separated by the
`uploads/` key prefix and the lifecycle rule filters on that prefix rather than on a tag.

## Authentication (Better Auth)

Auth is handled by [Better Auth](https://www.better-auth.com/) using cookie
sessions backed by Postgres. There are no JWT secrets — Better Auth derives
session tokens from `BETTER_AUTH_SECRET` and stores them in the `Session`
table. Credentials live in the `Account` table (provider `credential`,
scrypt-hashed via `better-auth/crypto`).

| Variable             | Default                                                                        | Required   | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| -------------------- | ------------------------------------------------------------------------------ | ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `BETTER_AUTH_SECRET` | —                                                                              | Yes        | Min 32 chars; signs cookies and derives session tokens                                                                                                                                                                                                                                                                                                                                                                                                                |
| `BETTER_AUTH_URL`    | —                                                                              | Prod       | Public origin used to build cookie scope and trusted-origin list                                                                                                                                                                                                                                                                                                                                                                                                      |
| `FRONTEND_BASE_URL`  | dev: falls back to `BETTER_AUTH_URL` w/ warning; **prod: boot fails if unset** | Yes (prod) | SPA origin that renders `/reset`, `/verify-email`, `/delete-account` pages. The SPA pulls `?token=` from the URL, shows a form, and POSTs to the matching `/api/auth/*` endpoint. Backend has no UI for these flows. Also consumed by `apps/scheduler` (`TikTokAuthExpiryWarningListener`, FR-02-06) to build the `/shops/connect` link on auth-expiry notifications/emails — validated separately there since the scheduler and api boot from independent env files. |
| `CORS_ORIGINS`       | —                                                                              | Prod       | Comma-separated allowed origins; required for cookie-bearing CORS and Socket.io upgrades                                                                                                                                                                                                                                                                                                                                                                              |
| `TWO_FACTOR_ISSUER`  | `ViShop`                                                                       | No         | FR-01-10. Name shown in the authenticator app next to the account (TOTP `issuer` in the QR/otpauth URI). No env means the two-factor plugin is still on — this only labels the entry.                                                                                                                                                                                                                                                                                 |

### Social login (OAuth)

Optional. Each provider activates only when **both** its `*_CLIENT_ID` and `*_CLIENT_SECRET` are set — otherwise it stays disabled. Register the callback `${BETTER_AUTH_URL}/api/auth/callback/<provider>` in the provider's console. The frontend triggers login via `signIn.social({ provider, callbackURL })`; the API renders no OAuth UI. Account linking relies on Better Auth's provider verified-email signal; no provider is listed in `accountLinking.trustedProviders`, because that option bypasses the verification requirement.

| Variable                                        | Default | Required | Description              |
| ----------------------------------------------- | ------- | -------- | ------------------------ |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`     | —       | No       | Enables Google sign-in   |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET`     | —       | No       | Enables GitHub sign-in   |
| `FACEBOOK_CLIENT_ID` / `FACEBOOK_CLIENT_SECRET` | —       | No       | Enables Facebook sign-in |

## Application

| Variable              | Default                 | Required | Description                                                                                                                                                                 |
| --------------------- | ----------------------- | -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `NODE_ENV`            | `development`           | No       | `development` or `production`                                                                                                                                               |
| `PORT`                | `3000`                  | No       | HTTP port                                                                                                                                                                   |
| `API_BASE_URL`        | `http://localhost:3000` | No       | Origin the generated Axios client targets from SSR/Node. Origin only — the generated operations already carry `/api/v1`, so a path here is sent twice                       |
| `TRUST_PROXY_HOPS`    | `0`                     | No       | Trusted reverse-proxy depth. `0` only when the API is exposed directly — `compose.prod.yml` binds it to loopback, so a production deploy needs `1` (see `deploy/README.md`) |
| `TZ`                  | `UTC`                   | No       | IANA timezone resolved by Node (requires `tzdata` in the image)                                                                                                             |
| `THROTTLER_ENABLED`   | `true`                  | No       | Toggle the global rate limiter                                                                                                                                              |
| `THROTTLER_LIMIT`     | `100`                   | No       | Requests per window                                                                                                                                                         |
| `THROTTLER_TTL`       | `60`                    | No       | Window length in seconds                                                                                                                                                    |
| `THROTTLER_FAIL_OPEN` | `true`                  | No       | Permit general API traffic during throttler Redis outages                                                                                                                   |

## Rate Limiting & Request Body Limits

Two-tier auth rate limit (Auth0/Cognito pattern). STRICT bucket guards
credential endpoints (`sign-in`, `sign-up`, `forget-password`, `reset-password`)
keyed by IP + email; LOOSE bucket guards session ops (`sign-out`, `get-session`,
`list-sessions`, …) keyed by IP only. Both are enforced by `@fastify/rate-limit`
because `reply.hijack()` bypasses the NestJS ThrottlerGuard.

| Variable                            | Default    | Required | Description                                                                             |
| ----------------------------------- | ---------- | -------- | --------------------------------------------------------------------------------------- |
| `AUTH_RATE_LIMIT_MAX`               | `5`        | No       | STRICT: max requests per window for one normalized email account                        |
| `AUTH_IP_RATE_LIMIT_MAX`            | `50`       | No       | STRICT: total credential requests per IP per window                                     |
| `AUTH_RATE_LIMIT_WINDOW_MS`         | `900000`   | No       | STRICT window in milliseconds (15 min default)                                          |
| `AUTH_SESSION_RATE_LIMIT_MAX`       | `60`       | No       | LOOSE: max requests per window on other `/api/auth/*` paths (per IP)                    |
| `AUTH_SESSION_RATE_LIMIT_WINDOW_MS` | `60000`    | No       | LOOSE window in milliseconds (60 sec default)                                           |
| `AUTH_RATE_LIMIT_FAIL_OPEN`         | `false`    | No       | Allow credential requests when account-limit Redis is unavailable; default fails closed |
| `HTTP_BODY_LIMIT_BYTES`             | `1048576`  | No       | Max raw JSON request body size (1 MB default)                                           |
| `UPLOAD_MAX_FILE_BYTES`             | `10485760` | No       | Max multipart file size (10 MB default); pinned in S3 presigned-POST policy             |
| `HTTP_MAX_EVENT_LOOP_DELAY_MS`      | `1000`     | No       | `@fastify/under-pressure` load-shed threshold; over this the API replies 503            |

## Resilience & Idempotency

`HTTP_REQUEST_TIMEOUT_MS` caps handler execution via a global `TimeoutInterceptor`
— a hung `await` is aborted with a `504` (RFC 9457 `request_timeout`) so it can't
pin a worker. Node cannot cancel the orphaned promise, so the background work still
finishes; the client just stops waiting.

Idempotency-Key replay (Stripe pattern) protects mutating `/api/v1/*` requests. A
client sends `Idempotency-Key: <opaque>`; the first response (2xx) is cached in
Redis (cache DB 5) and replayed byte-for-byte on retries, with an
`Idempotent-Replayed: true` header. Reuse with a different body → `422`
(`idempotency_key_mismatch`); a still-in-flight duplicate → `409`
(`idempotency_key_conflict`). A Redis outage fails **open** (the write proceeds
without protection). Non-2xx responses release the lock so the client may retry —
safe because command handlers roll back their transaction on error.

| Variable                       | Default | Required | Description                                                                        |
| ------------------------------ | ------- | -------- | ---------------------------------------------------------------------------------- |
| `HTTP_REQUEST_TIMEOUT_MS`      | `30000` | No       | Handler execution cap; `504` on breach. `0` disables. Keep below the lock TTL (ms) |
| `IDEMPOTENCY_ENABLED`          | `true`  | No       | Toggles the Idempotency-Key plugin                                                 |
| `IDEMPOTENCY_TTL_SECONDS`      | `86400` | No       | How long a completed response stays replayable (24h, matches Stripe)               |
| `IDEMPOTENCY_LOCK_TTL_SECONDS` | `60`    | No       | In-flight lock lifetime; MUST exceed `HTTP_REQUEST_TIMEOUT_MS` (validated on boot) |

## Error documentation

| Variable              | Default              | Required | Description                                                                                                                                                                                                                                                                                                                                                                           |
| --------------------- | -------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ERROR_DOCS_BASE_URL` | `/errors` (relative) | No       | Base URL used to build the `type` field of RFC 9457 Problem Details responses (`<base>/<code-with-dashes>`). Left unset, the api serves the documentation itself at `/errors` and `/errors/<slug>`, which is what RFC 9457 §3.1.1 expects the URI to resolve to. Point it at an absolute URL only if you publish those pages elsewhere — the built-in ones stay reachable either way. |

## Mail (SMTP)

| Variable             | Default               | Required                   | Description                                          |
| -------------------- | --------------------- | -------------------------- | ---------------------------------------------------- |
| `MAIL_HOST`          | `localhost`           | Yes (real host in prod)    | SMTP host; must point at a real server in production |
| `MAIL_PORT`          | `1025`                | Yes                        | SMTP port                                            |
| `MAIL_USER`          | —                     | No                         | SMTP username                                        |
| `MAIL_PASSWORD`      | —                     | No                         | SMTP password                                        |
| `MAIL_IGNORE_TLS`    | `true`                | No                         | Skip STARTTLS (Mailpit/dev only)                     |
| `MAIL_SECURE`        | `false`               | No                         | Use TLS on connect (port 465)                        |
| `MAIL_REQUIRE_TLS`   | `false`               | No                         | Force STARTTLS upgrade                               |
| `MAIL_DEFAULT_EMAIL` | `noreply@example.com` | Yes (real address in prod) | Default `From:` address                              |
| `MAIL_DEFAULT_NAME`  | `No Reply`            | No                         | Default `From:` display name                         |

> **Prod TLS rule.** In production the env validator requires TLS (`MAIL_IGNORE_TLS=false` **and** one of `MAIL_SECURE`/`MAIL_REQUIRE_TLS`) **only when `MAIL_USER` is set** — the rule protects SMTP credentials, and a no-auth relay (e.g. a local Mailpit in the prod-parity smoke) sends nothing secret in plaintext. Set real credentials + TLS for any production SMTP that authenticates.

## Telegram alert channel (FR-14-06)

| Variable                      | Default                    | Required   | Description                                                                                                                                                                                                                                                       |
| ----------------------------- | -------------------------- | ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `TELEGRAM_BOT_TOKEN`          | —                          | production | Bot token from `@BotFather` (`/newbot`). Empty disables the channel silently.                                                                                                                                                                                     |
| `TELEGRAM_BOT_USERNAME`       | —                          | production | Bot's public `@username` (no `@`), used to build the `t.me` deep link                                                                                                                                                                                             |
| `TELEGRAM_WEBHOOK_SECRET`     | —                          | production | Arbitrary secret you generate; verified against Telegram's `X-Telegram-Bot-Api-Secret-Token` header on every webhook call                                                                                                                                         |
| `TELEGRAM_ALERTS_OPT_OUT`     | —                          | No         | Set to the exact value `telegram-alerts-disabled` to boot production with the three variables above empty. Declared, never inferred: an empty token on its own still stops the boot. Opted out, no alert reaches Telegram and nothing else takes the channel over |
| `TELEGRAM_API_BASE_URL`       | `https://api.telegram.org` | No         | Telegram Bot API base — never change outside testing                                                                                                                                                                                                              |
| `TELEGRAM_HTTP_TIMEOUT_MS`    | `10000`                    | No         | Timeout for outbound `sendMessage` calls                                                                                                                                                                                                                          |
| `WORKER_TELEGRAM_CONCURRENCY` | `5`                        | No         | Worker-side concurrency for the `telegram-notification` BullMQ queue                                                                                                                                                                                              |

Read by `api` (link-token/status/unlink endpoints + the `/api/v1/webhooks/telegram` webhook) and by
`worker` (sends the actual message). Empty in dev/test — the channel is simply inert, no crash, no
messages sent; the 3 credential vars are required only in production
(`requireTelegramCredentials`, mirroring `requireTikTokCredentials`).

**One-time setup, after a real bot token exists and the app has a public URL:**

1. Create a bot: message `@BotFather` on Telegram, run `/newbot`, follow the prompts. Note the
   token and the username it assigns.
2. Generate a webhook secret: `openssl rand -hex 32`.
3. Put all three in the deployment's env (never commit real values).
4. Register the webhook once (repeat only if the public domain changes):

   ```bash
   curl -X POST "https://api.telegram.org/bot<TELEGRAM_BOT_TOKEN>/setWebhook" \
     -d "url=https://<public-api-domain>/api/v1/webhooks/telegram" \
     -d "secret_token=<TELEGRAM_WEBHOOK_SECRET>"
   ```

A user then links their own account from ViShop by requesting a link token
(`POST /api/v1/notifications/telegram/link-token`) and opening the returned `t.me` URL — Telegram
sends the bot a `/start <token>` message, the webhook resolves it, and the account is linked. No
manual OTP entry.

## Seeding

| Variable                   | Default | Required     | Description                                                                        |
| -------------------------- | ------- | ------------ | ---------------------------------------------------------------------------------- |
| `RUN_SEED`                 | `false` | No           | When `true`, the migration container runs `prisma/seed.mjs` after `migrate deploy` |
| `SEED_ADMIN_EMAIL`         | —       | When seeding | Admin email; the seed creates a `User` + `Account` row pair                        |
| `SEED_ADMIN_PASSWORD`      | —       | When seeding | Admin password, scrypt-hashed via `better-auth/crypto`                             |
| `MIGRATION_MAX_ATTEMPTS`   | `10`    | No           | Cold-start retry count for `prisma migrate deploy`                                 |
| `MIGRATION_RETRY_DELAY_MS` | `1500`  | No           | Non-blocking delay between migration attempts                                      |

## Eventing & Outbox

| Variable                          | Default     | Required | Description                                                                                                                                                                                  |
| --------------------------------- | ----------- | -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `EVENT_PUBLISHER_DRIVER`          | `inprocess` | No       | `inprocess` (EventEmitter2) or `outbox` (Postgres outbox + scheduler relay)                                                                                                                  |
| `OUTBOX_TX_TIMEOUT_MS`            | `30000`     | No       | Outbox interactive tx timeout (30 sec); increase if events hang in publish                                                                                                                   |
| `OUTBOX_POLL_INTERVAL_MS`         | `1000`      | No       | Relay polling cadence                                                                                                                                                                        |
| `AUTOMATION_SHADOW_MODE_MIN_DAYS` | `14`        | No       | FR-14-09. Days of log-only observation required before a level-2 automation action may be switched on. Must match across api/worker/scheduler; `0` disables the wait (tests only)            |
| `OUTBOX_BATCH_SIZE`               | `50`        | No       | Max events relayed per poll                                                                                                                                                                  |
| `OUTBOX_MAX_ATTEMPTS`             | `10`        | No       | Retry budget before an event is parked                                                                                                                                                       |
| `OUTBOX_RETRY_BASE_MS`            | `2000`      | No       | Backoff base. A failed row waits `min(BASE × 2^attempts, MAX)`, where `attempts` is the **pre-increment** count — so the wait after the Nth attempt is `BASE × 2^(N-1)`                      |
| `OUTBOX_RETRY_MAX_MS`             | `300000`    | No       | Backoff cap (5 min). At the defaults the 1st and 10th attempts are **~13.5 min** apart instead of ~10 s. The delay stamped by the 10th attempt is never consumed — the row is already parked |
| `OUTBOX_RETENTION_DAYS`           | `7`         | No       | Age after which a **processed** row is hard-deleted (03:15 UTC). Unprocessed rows are never purged by this cron                                                                              |
| `OUTBOX_PURGE_BATCH_SIZE`         | `1000`      | No       | Rows deleted per purge batch                                                                                                                                                                 |
| `OUTBOX_PURGE_MAX_BATCHES`        | `200`       | No       | Batches per purge run — the cap is `BATCH_SIZE × MAX_BATCHES` rows/night                                                                                                                     |

## Export

| Variable                            | Default | Required | Description                                                                                                                                                                                                                                                                                                                         |
| ----------------------------------- | ------- | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `EXPORT_SYNC_THRESHOLD_ROWS`        | `1000`  | No       | Shared Export Service Framework (FR-00-11) threshold-routing cutoff. `estimateRowCount()` at or below this runs inline in the request; above it queues a background job instead                                                                                                                                                     |
| `ORDERS_EXPORT_SYNC_THRESHOLD_ROWS` | `200`   | No       | Same cutoff for `POST /api/v1/orders/exports` only, overriding the shared one. Lower because the inline branch is bounded by `HTTP_REQUEST_TIMEOUT_MS` and an order row carries its lines, shop name and SKU costs. Read per request, so it can be turned without a deploy; `0` forces every orders export onto the background path |

## Scheduler cleanup

| Variable                                  | Default    | Description                                                                                                                                                                                                                                                                                                                                                                                                              |
| ----------------------------------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `AUDIT_LOG_RETENTION_MONTHS`              | `12`       | Monthly `audit_logs` partitions kept before DROP                                                                                                                                                                                                                                                                                                                                                                         |
| `DLQ_ALERT_THRESHOLD`                     | `10`       | Alert threshold per dead-letter queue                                                                                                                                                                                                                                                                                                                                                                                    |
| `EVENT_LOG_RETENTION_MONTHS`              | `0`        | Monthly `event_logs` partitions kept before DROP (03:40 UTC), current month included. **`0` disables the sweep** — an audit trail expires only when an operator sets a period. Max `120`                                                                                                                                                                                                                                 |
| `INACTIVE_USER_RETENTION_DAYS`            | `90`       | Age before inactive users are purged                                                                                                                                                                                                                                                                                                                                                                                     |
| `USER_PURGE_BATCH_SIZE`                   | `500`      | Users deleted per cleanup batch                                                                                                                                                                                                                                                                                                                                                                                          |
| `USER_PURGE_MAX_BATCHES`                  | `200`      | Maximum user cleanup batches per run                                                                                                                                                                                                                                                                                                                                                                                     |
| `STORED_FILE_CLEANUP_BATCH_SIZE`          | `500`      | Stored-file lifecycle records scanned per hour                                                                                                                                                                                                                                                                                                                                                                           |
| `STORED_FILE_FINALIZING_STALE_MINUTES`    | `60`       | FINALIZING age considered abandoned                                                                                                                                                                                                                                                                                                                                                                                      |
| `STORED_FILE_VERIFYING_STALE_HOURS`       | `24`       | VERIFYING age considered abandoned                                                                                                                                                                                                                                                                                                                                                                                       |
| `STORED_FILE_ORPHAN_GRACE_MINUTES`        | `60`       | Grace before an owner-deleted file is reclaimed                                                                                                                                                                                                                                                                                                                                                                          |
| `LABEL_CACHE_CLEANUP_BATCH_SIZE`          | `500`      | Expired shipping label cache records purged per daily run (03:25 UTC)                                                                                                                                                                                                                                                                                                                                                    |
| `SHIP_ITEM_STALE_AFTER_MS`                | `1800000`  | How long a batch-RTS item may sit `SENDING` before the sweep (every 10 min) hands it to the ship reconciler as `VERIFYING`. `SENDING` only follows a claim and a claim is never retried, so this need only outlast one platform call and its outcome write. Nothing else can move a `SENDING` item: the claim only matches `PENDING`, precisely so a shipment the platform may already have accepted is never sent twice |
| `SHIP_ITEM_PENDING_STALE_AFTER_MS`        | `10800000` | How long a batch-RTS item may sit `PENDING` before the same sweep fails it as retryable (`ship job was lost before it ran`), so `retry-failed` or release can free it. Must stay above a job's whole TikTok backoff run (~2h36m) plus the per-shop stagger, or an item whose job is still retrying before its claim is failed under it                                                                                   |
| `SHIP_ITEM_SWEEP_BATCH_SIZE`              | `200`      | Stale ship items handled per sweep run, per status                                                                                                                                                                                                                                                                                                                                                                       |
| `SHIP_CHUNK_SIZE`                         | `50`       | Every process loading the orders module. Packages per Batch Ship call when a batch-RTS run holds two or more packages (a single package still uses Ship Package). Must be 1–50 — the platform refuses a 51st with `36009004` — and a value outside that range fails the process at startup instead of being clamped                                                                                                      |
| `SHIP_RECONCILE_DELAY_MS`                 | `60000`    | Scheduler and worker. How long a ship item sits `VERIFYING` before it is read back: the delay of the reconcile job a Batch Ship chunk queues for itself, and the age at which the reconcile sweep (every 5 min) picks up whatever that job missed                                                                                                                                                                        |
| `SHIP_RECONCILE_SWEEP_BATCH_SIZE`         | `200`      | Scheduler. `VERIFYING` ship items queued for reconcile per sweep run, oldest first, one job per shop                                                                                                                                                                                                                                                                                                                     |
| `SHIP_RECONCILE_GRACE_MS`                 | `600000`   | Worker. How long after the send the platform may still show a package unshipped before the reconciler fails it as retryable. The platform shows `rts_time` within seconds; the margin exists because an early "not shipped" makes an operator resend for nothing                                                                                                                                                         |
| `SHIP_RECONCILE_MAX_ATTEMPTS`             | `5`        | Worker. Platform reads that settle nothing (order not found, package missing, read failed) before a `VERIFYING` item becomes `NEEDS_ATTENTION` for a person to check on Seller Center                                                                                                                                                                                                                                    |
| `VERIFICATION_PURGE_GRACE_DAYS`           | `1`        | Age past `expiresAt` before a token is deleted                                                                                                                                                                                                                                                                                                                                                                           |
| `VERIFICATION_PURGE_BATCH_SIZE`           | `1000`     | Verification rows deleted per batch                                                                                                                                                                                                                                                                                                                                                                                      |
| `VERIFICATION_PURGE_MAX_BATCHES`          | `200`      | Maximum verification purge batches per run                                                                                                                                                                                                                                                                                                                                                                               |
| `AFFILIATE_SYNC_RETENTION_DAYS`           | `30`       | Age after which a **completed** `affiliate_source_sync_runs` row is hard-deleted (03:55 UTC)                                                                                                                                                                                                                                                                                                                             |
| `AFFILIATE_SYNC_ABANDONED_RETENTION_DAYS` | `90`       | Age after which an **abandoned** row is hard-deleted (same cron, run second). Must be `>=` `AFFILIATE_SYNC_RETENTION_DAYS`. `pending`/`running`/`failed` rows are never purged by this cron — they're either active or awaiting ops investigation                                                                                                                                                                        |
| `AFFILIATE_SYNC_PURGE_BATCH_SIZE`         | `1000`     | Rows deleted per purge batch (shared by both cutoffs above)                                                                                                                                                                                                                                                                                                                                                              |
| `AFFILIATE_SYNC_PURGE_MAX_BATCHES`        | `200`      | Batches per cutoff per purge run — the cap is `BATCH_SIZE × MAX_BATCHES` rows/night, per status                                                                                                                                                                                                                                                                                                                          |

## TikTok Shop

Credentials come from the TikTok Shop Partner Center. All four are **required in production** —
the api, worker and scheduler validators each enforce it, so a missing key fails the boot that
needs it rather than the first request.

| Variable                                       | Default                                 | Required   | Description                                                                                                                                                                                                                                                                                                                                                         |
| ---------------------------------------------- | --------------------------------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `TIKTOK_APP_KEY`                               | —                                       | production | App key; signs every request and every inbound webhook                                                                                                                                                                                                                                                                                                              |
| `TIKTOK_APP_SECRET`                            | —                                       | production | App secret; the HMAC-SHA256 key                                                                                                                                                                                                                                                                                                                                     |
| `TIKTOK_SERVICE_ID`                            | —                                       | production | Identifies the app on the consent screen                                                                                                                                                                                                                                                                                                                            |
| `TIKTOK_TOKEN_ENCRYPTION_KEY`                  | —                                       | production | 64 hex chars (32 bytes). AES-256-GCM key for tokens at rest                                                                                                                                                                                                                                                                                                         |
| `TIKTOK_AUTH_BASE_URL`                         | `https://auth.tiktok-shops.com`         | no         | Token exchange and refresh host                                                                                                                                                                                                                                                                                                                                     |
| `TIKTOK_SERVICES_BASE_URL`                     | `https://services.tiktokshop.com`       | no         | Consent screen host                                                                                                                                                                                                                                                                                                                                                 |
| `TIKTOK_API_BASE_URL`                          | `https://open-api.tiktokglobalshop.com` | no         | Shop API host                                                                                                                                                                                                                                                                                                                                                       |
| `TIKTOK_HTTP_TIMEOUT_MS`                       | `30000`                                 | no         | Per-call timeout (1 000–120 000)                                                                                                                                                                                                                                                                                                                                    |
| `TIKTOK_LABEL_FETCH_TIMEOUT_MS`                | `8000`                                  | no         | Per-package shipping label fetch HTTP timeout (5 000–10 000 ms, AC-5)                                                                                                                                                                                                                                                                                               |
| `TIKTOK_TRACE_HTTP`                            | `false`                                 | no         | Logs every request/response body at `debug`. Seller data — local work only                                                                                                                                                                                                                                                                                          |
| `TIKTOK_WEBHOOK_CALLBACK_URL`                  | —                                       | production | Public https URL TikTok delivers webhooks to. Registered per shop on connect and re-checked every 6 h (FR-00-13). Empty in production stops the boot: without it no shop is registered, no event arrives, and nothing reports the gap                                                                                                                               |
| `TIKTOK_WEBHOOK_OPT_OUT`                       | —                                       | no         | Set to the exact value `webhook-registration-disabled` to boot production with `TIKTOK_WEBHOOK_CALLBACK_URL` empty — for a deployment that has no public https endpoint yet. The URL must then be empty: a half-configured http callback would be stored as though it worked. `ReconcileShopWebhookSubscriptionsHandler` still throws when asked to register a shop |
| `TIKTOK_RATE_LIMIT_HEAVY_WRITE_FLOOR_RPS`      | `0.2`                                   | no         | Floor capacity (req/sec) for heavy write calls (e.g. bulk catalog)                                                                                                                                                                                                                                                                                                  |
| `TIKTOK_RATE_LIMIT_HEAVY_WRITE_CEILING_RPS`    | `1`                                     | no         | Ceiling capacity (req/sec) for heavy write calls                                                                                                                                                                                                                                                                                                                    |
| `TIKTOK_RATE_LIMIT_STANDARD_WRITE_FLOOR_RPS`   | `1`                                     | no         | Floor capacity (req/sec) for standard write calls (e.g. stock push)                                                                                                                                                                                                                                                                                                 |
| `TIKTOK_RATE_LIMIT_STANDARD_WRITE_CEILING_RPS` | `3`                                     | no         | Ceiling capacity (req/sec) for standard write calls                                                                                                                                                                                                                                                                                                                 |
| `TIKTOK_RATE_LIMIT_STANDARD_READ_FLOOR_RPS`    | `3`                                     | no         | Floor capacity (req/sec) for standard read calls (e.g. order sync)                                                                                                                                                                                                                                                                                                  |
| `TIKTOK_RATE_LIMIT_STANDARD_READ_CEILING_RPS`  | `10`                                    | no         | Ceiling capacity (req/sec) for standard read calls                                                                                                                                                                                                                                                                                                                  |
| `TIKTOK_RATE_LIMIT_RAMP_PCT`                   | `0.25`                                  | no         | Step percentage (+25%) for adaptive ramp-up                                                                                                                                                                                                                                                                                                                         |
| `TIKTOK_RATE_LIMIT_RAMP_INTERVAL_MS`           | `900000`                                | no         | Interval (15 min) between adaptive ramp-up steps                                                                                                                                                                                                                                                                                                                    |
| `TIKTOK_RATE_LIMIT_LOCK_ON_429_MS`             | `900000`                                | no         | Lock duration (15 min) freezing ramp-up and resetting to floor on 429                                                                                                                                                                                                                                                                                               |
| `TIKTOK_RATE_LIMIT_INITIAL_SYNC_WINDOW_MS`     | `900000`                                | no         | Initial sync elevated window duration (15 min)                                                                                                                                                                                                                                                                                                                      |
| `TIKTOK_RATE_LIMIT_INITIAL_SYNC_CEILING_RPS`   | `10`                                    | no         | Ceiling RPS assigned during initial sync elevated window                                                                                                                                                                                                                                                                                                            |
| `TIKTOK_INITIAL_SYNC_LOOKBACK_DAYS`            | `30`                                    | no         | Days of history for a shop's first order sync and FR-06-11's one-time affiliate-order-source backfill (1–90)                                                                                                                                                                                                                                                        |

Generate the encryption key with:

```bash
openssl rand -hex 32
```

Rotating it makes every stored token undecryptable — sellers must reconnect. See
[tiktok-integration.md](./tiktok-integration.md).

## Shipment tracking polling

TikTok publishes no webhook for carrier milestones, so the journey is polled: the scheduler claims
what is due every 5 minutes and the worker calls Get Tracking on the `REPORT` priority tier. Read by
**scheduler and worker** — keep the two in step. The cadence values are business estimates, not
measurements: every poll logs `ingestLagMs`, and if the platform is routinely slower than the
out-for-delivery interval, that interval is asking for data it does not have.

| Variable                            | Default             | Required | Description                                                                                                                                                                                |
| ----------------------------------- | ------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `TRACKING_POLL_PENDING_PICKUP_MS`   | `21600000` (6 h)    | No       | Interval for `PENDING_PICKUP`                                                                                                                                                              |
| `TRACKING_POLL_IN_TRANSIT_MS`       | `10800000` (3 h)    | No       | Interval for `PICKED_UP` and `IN_TRANSIT`                                                                                                                                                  |
| `TRACKING_POLL_OUT_FOR_DELIVERY_MS` | `2700000` (45 min)  | No       | Interval for `OUT_FOR_DELIVERY` — the window where a buyer actually watches                                                                                                                |
| `TRACKING_POLL_EXCEPTION_MS`        | `14400000` (4 h)    | No       | Interval for `DELIVERY_FAILED` and `RETURNING`                                                                                                                                             |
| `TRACKING_POLL_MAX_INTERVAL_MS`     | `43200000` (12 h)   | No       | Ceiling for the ×1.5 backoff applied after every silent poll                                                                                                                               |
| `TRACKING_POLL_ABANDON_AFTER_MS`    | `1209600000` (14 d) | No       | Silence past this and the parcel is given up on (`nextPollAt = NULL` + a warn line). AC-7 — a stuck parcel must not be asked forever                                                       |
| `TRACKING_POLL_BATCH_SIZE`          | `200`               | No       | Rows claimed per 5-minute tick                                                                                                                                                             |
| `TRACKING_POLL_LEASE_MS`            | `900000` (15 min)   | No       | How long a claimed row stays invisible; MUST be at least `3 × TIKTOK_HTTP_TIMEOUT_MS + 15000` (validated on boot in scheduler and worker), or a slow shop gets the same order queued twice |

`DELIVERED` and `RETURNED` stop the loop outright, as does a package the platform cancelled — there
is no interval for them. See [shipment-tracking-polling.md](./shipment-tracking-polling.md) for how
the loop is wired and why the write path is guarded.

## Shipment issue detection

FR-06-33. A scheduler sweep every 30 minutes turns a misbehaving parcel into a `shipment_issues`
row an operator can work. It reads ViShop's own tables only and calls no platform API, so the
cadence costs no rate-limit budget. Read by the **scheduler** alone.

| Variable                              | Default           | Required | Description                                                                                                                                                        |
| ------------------------------------- | ----------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `SHIPMENT_ISSUE_STALLED_AFTER_MS`     | `86400000` (24 h) | No       | Carrier silence past this raises `stalled`. MUST exceed `TRACKING_POLL_MAX_INTERVAL_MS` (validated on boot) — the gate also requires a poll inside the same window |
| `SHIPMENT_ISSUE_DETECTION_BATCH_SIZE` | `200`             | No       | Rows examined per tick, per pass (open new / review open)                                                                                                          |
| `SHIPMENT_ISSUE_AUTO_CLOSE_ENABLED`   | `true`            | No       | Self-closes the two unambiguous cases (parcel `DELIVERED`, package `CANCELLED`). Never relaxes the manual close route, which always demands an outcome             |

`RETURNING` deliberately does NOT self-close: the carrier saying it sent the parcel back is not the
warehouse having received it, and no returns module exists yet — the open issue is the only record
telling the warehouse to expect it.

## Bull Board

| Variable              | Default | Required             | Description                                                           |
| --------------------- | ------- | -------------------- | --------------------------------------------------------------------- |
| `BULL_BOARD_ENABLED`  | `true`  | No                   | Mount `/api/admin/queues`                                             |
| `BULL_BOARD_USER`     | `admin` | Yes (rotate in prod) | Basic-auth username                                                   |
| `BULL_BOARD_PASSWORD` | `admin` | Yes (rotate in prod) | Basic-auth password; the env validator rejects defaults in production |

Brute-force protection is not configurable: `/api/admin/queues` allows 10 **failed** Basic-auth
attempts per IP per minute, counted in the shared rate-limit Redis (db 4) so the ceiling holds
across replicas. Authenticated traffic is never counted — the dashboard polls its queue API every
few seconds. If that Redis is unreachable the surface fails closed with `503`.

## Observability

| Variable                         | Default                                  | Required | Description                                                                                                                                                                    |
| -------------------------------- | ---------------------------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `LOG_LEVEL`                      | `info`                                   | No       | pino log level: `trace`, `debug`, `info`, `warn`, `error`                                                                                                                      |
| `LOG_PRETTY`                     | `true`                                   | No       | Pretty-print development logs (host and dev Compose alike); `pino-pretty` ships as a runtime dependency in dev images specifically to support this                             |
| `COMPOSE_PROJECT_NAME`           | `vishop`                                 | No       | Prefix for Compose containers, networks, volumes, and locally built image tags                                                                                                 |
| `SWARM_STACK_NAME`               | `vishop`                                 | No       | Stack namespace used by `scripts/swarm-local-test.sh`; independent from the Compose project name                                                                               |
| `PROD_STARTUP_TIMEOUT_SECONDS`   | `120`                                    | No       | Maximum time `build-prod.sh` waits for the production stack to become healthy                                                                                                  |
| `ENABLE_METRICS`                 | `false`                                  | No       | Expose `/metrics` for Prometheus (excluded from `api/v1` prefix)                                                                                                               |
| `METRICS_ALLOW_CIDRS`            | —                                        | No       | Comma-separated CIDR ranges allowed to hit `/metrics` (empty=closed)                                                                                                           |
| `OTEL_ENABLED`                   | `false`                                  | No       | Bootstrap the OpenTelemetry SDK                                                                                                                                                |
| `OTEL_SERVICE_NAME`              | `nestjs-fastify-api`                     | No       | Reported service name                                                                                                                                                          |
| `OTEL_SERVICE_NAMESPACE`         | `app`                                    | No       | Reported service namespace                                                                                                                                                     |
| `OTEL_SERVICE_VERSION`           | `0.0.0`                                  | No       | Release/version attached to OTel resources and Sentry events                                                                                                                   |
| `OTEL_EXPORTER_OTLP_ENDPOINT`    | `http://localhost:4318`                  | No       | OTLP/HTTP collector endpoint                                                                                                                                                   |
| `OTEL_EXPORTER_OTLP_HEADERS`     | —                                        | No       | Optional collector auth headers                                                                                                                                                |
| `OTEL_TRACES_SAMPLER_RATIO`      | `1`                                      | No       | Head sampling ratio for **new** traces. Keep `1` with Collector tail sampling; discarded head traces cannot be recovered                                                       |
| `OTEL_TRUST_INBOUND_TRACEPARENT` | `false`                                  | No       | Trust client `traceparent`/`baggage`. Keep `false` on a public edge; `true` only behind a trusted mesh/gateway                                                                 |
| `OTEL_METRICS_EXPORT_ENABLED`    | `false`                                  | No       | Push OTLP metrics from this process. Keep `false` for the API (@prometheus-io/client `/metrics` is source of truth); `true` for worker/scheduler which have no scrape endpoint |
| `OTEL_DEBUG`                     | `false`                                  | No       | Verbose SDK logging                                                                                                                                                            |
| `TRUST_INBOUND_REQUEST_ID`       | `false`                                  | No       | Accept sanitized `X-Request-Id` from a trusted gateway; keep false for public callers                                                                                          |
| `GRAFANA_ADMIN_PASSWORD`         | `admin`                                  | No       | Local Grafana admin password; must be replaced on shared/remote hosts                                                                                                          |
| `SENTRY_DSN`                     | —                                        | No       | Sentry DSN; leave empty to disable                                                                                                                                             |
| `SENTRY_TRACES_SAMPLE_RATE`      | `0.01` (api, scheduler) · `0.1` (worker) | No       | Sentry tracing sample rate. The api default is deliberately 1%: at 1k RPS, 0.1 burns ~26M traces/day                                                                           |
| `SENTRY_ENVIRONMENT`             | `development`                            | No       | Reported Sentry environment tag                                                                                                                                                |

## CI / Nx Cloud

| Variable              | Default | Required | Description                            |
| --------------------- | ------- | -------- | -------------------------------------- |
| `NX_CLOUD_AUTH_TOKEN` | —       | No       | Auth token for Nx Cloud remote caching |

## Docker images

| Variable             | Default | Required | Description                                  |
| -------------------- | ------- | -------- | -------------------------------------------- |
| `API_IMAGE`          | —       | Prod     | Immutable API reference ending in `@sha256:` |
| `WORKER_IMAGE`       | —       | Prod     | Immutable worker image reference             |
| `SCHEDULER_IMAGE`    | —       | Prod     | Immutable scheduler image reference          |
| `MIGRATION_IMAGE`    | —       | Prod     | Immutable migration image reference          |
| `API_PORT`           | `3000`  | No       | Host port mapped to the API container        |
| `API_DEBUG_PORT`     | `9229`  | No       | Host port mapped to the Node inspector (dev) |
| `API_REPLICAS`       | `1`     | No       | API replicas used by Swarm                   |
| `WORKER_REPLICAS`    | `1`     | No       | Worker replicas used by Swarm                |
| `SCHEDULER_REPLICAS` | `1`     | No       | Leader-elected scheduler replicas            |
