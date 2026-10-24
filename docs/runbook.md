# Runbook — ViShop Platform

Operational runbook for on-call engineers. Each section follows the pattern:
**Symptom → Diagnostic → Action → Escalation**.

---

## 1. Outbox stuck

**Symptom:** Domain events stop flowing. Downstream listeners (audit-log, notifications) are silent. `outbox_events` rows accumulate with `status = 'PENDING'` or `status = 'FAILED'`.

**Diagnostic:**

```sql
-- Find events that have failed or exhausted retry attempts
SELECT id, event_type, aggregate_type, aggregate_id, retry_count, occurred_at, error_message
FROM outbox_events
WHERE status IN ('PENDING', 'FAILED')
ORDER BY occurred_at ASC
LIMIT 50;

-- Count pending vs failed events
SELECT status, COUNT(*)
FROM outbox_events
GROUP BY status;
```

```bash
# Check service logs for outbox publishing errors
docker compose -f infra/compose/docker-compose.prod.yml logs --tail=200 iam-service | grep -i "outbox\|error\|failed"
```

```bash
# Check scheduler logs for relay errors
docker compose -f infra/compose/docker-compose.prod.yml logs --tail=200 iam-service | grep -i "outbox\|error\|failed"
```

**Action:**

1. Fix the root cause (broken listener, downstream service down, schema mismatch).
2. Reset `attempts` to allow a replay — do this only after the root cause is fixed:

```sql
-- Reset specific event types for replay
UPDATE outbox_events
SET attempts = 0, "lastError" = NULL, "nextAttemptAt" = NULL
WHERE "processedAt" IS NULL
  AND "eventType" = 'users.registered'
  AND attempts >= 10;
```

Clearing `nextAttemptAt` matters: the relay only claims rows whose backoff window has elapsed, so
resetting `attempts` alone still leaves the row invisible until the last-stamped deadline passes.

3. The scheduler's outbox relay picks up rows with `attempts < OUTBOX_MAX_ATTEMPTS` whose
   `nextAttemptAt` is null or already past, on the next poll cycle (`OUTBOX_POLL_INTERVAL_MS`,
   default 1 s). A row that is merely backing off is **not** stuck — check
   `SELECT "nextAttemptAt" FROM outbox_events WHERE id = …` before resetting anything.
4. Monitor `"processedAt"` population over the next minute.

**Escalation:** If events are still stuck after reset, check `OUTBOX_TX_TIMEOUT_MS` (default 30 s) — a too-short timeout causes P2028 rollback loops. Increase and redeploy.

---

## 2. DLQ full (BullMQ)

**Symptom:** Worker stops processing jobs. Bull Board (`/api/admin/queues`) shows a large failed job count. New jobs queue but never complete.

**Diagnostic:**

```bash
# List failed jobs via Bull Board UI (shows both source queues and dedicated .dlq queues)
open http://localhost:3000/api/admin/queues

# Or inspect via Redis CLI
redis-cli -h $REDIS_QUEUE_HOST -p $REDIS_QUEUE_PORT
> LLEN bull:email-notification:failed
> LLEN bull:tiktok.sync-orders.dlq:waiting
> LRANGE bull:tiktok.sync-orders.dlq:waiting 0 9
```

```bash
# Worker logs for the failure reason
docker compose logs worker --tail=200 | grep -E "failed|error|exception"
```

**Action:**

1. Identify the failure pattern (all same job type? started after a deploy?).
2. Fix the root cause in the worker handler.
3. Redeploy the worker: `docker compose up -d --no-deps worker`.
4. Replay failed jobs from Bull Board → select failed jobs → "Retry all" button.
   Or via Redis CLI — move jobs back to waiting:

```bash
# Retry all failed jobs in a queue (Bull Board preferred; CLI for automation)
redis-cli -h $REDIS_QUEUE_HOST -p $REDIS_QUEUE_PORT
> LRANGE bull:email-notification:failed 0 -1
# Pipe each job ID through `bull:email-notification:retry <id>` using bullmq CLI if installed
```

5. Purge genuinely unrecoverable jobs (data from a bad deploy) after confirming replay is safe:
   Bull Board → "Clean" → select "failed" state → confirm.

**Escalation:** If the queue fills faster than the worker can drain (sustained spike), scale the worker replicas via your orchestrator or increase `BULLMQ_CONCURRENCY`. Alert the team if a runaway job producer is suspected.

---

## 3. Audit log missing events

**Symptom:** Compliance query returns fewer audit records than expected. Users report actions not appearing in the audit trail.

**Diagnostic:**

```bash
# Check audit-log listener logs for subscription errors.
# With EVENT_PUBLISHER_DRIVER=outbox the listener runs in the SCHEDULER — that is where the relay
# republishes events. Check api only when running the inprocess driver.
docker compose logs scheduler --tail=500 | grep -i "audit"

# Check if outbox relay is delivering the events
docker compose -f infra/compose/docker-compose.prod.yml logs --tail=200 iam-service | grep "outbox"
```

```sql
-- Check if outbox has the event but audit_logs does not.
-- AuditLogListener writes the event's aggregateId to "userId" and its eventType to action, so those
-- are what the two tables join on — audit_logs has no entity/aggregate column of its own.
SELECT o."eventType", o."aggregateId", o."processedAt", o."lastError"
FROM outbox_events o
WHERE o."eventType" LIKE 'users.%'
  AND o."createdAt" > NOW() - INTERVAL '1 hour'
  AND NOT EXISTS (
    SELECT 1 FROM audit_logs a
    -- audit_logs."userId" is uuid, outbox_events."aggregateId" is text; cast the uuid side, since
    -- casting the text side throws outright on any aggregate id that is not a uuid.
    WHERE a."userId"::text = o."aggregateId"
      AND a.action = o."eventType"
      AND a."createdAt" > o."createdAt" - INTERVAL '5 seconds'
  )
ORDER BY o."createdAt" DESC
LIMIT 20;
```

**Action:**

1. If outbox rows show `"processedAt" IS NOT NULL` but audit rows are absent: the audit listener threw an error silently. Check for schema mismatch or a bug in the listener — redeploy after fix.
2. If outbox rows show `"processedAt" IS NULL`: the relay is stuck — follow the **Outbox stuck** runbook section above.
3. To replay a specific event, reset its `attempts` and `"processedAt"`:

```sql
UPDATE outbox_events
SET attempts = 0, "processedAt" = NULL, "lastError" = NULL
WHERE id = '<event-uuid>';
```

**Escalation:** If missing audit records span a time window matching a deployment, check whether a migration altered the `audit_logs` partition for that month. Verify partition existence:

```sql
SELECT inhrelid::regclass FROM pg_inherits
WHERE inhparent = 'audit_logs'::regclass
ORDER BY 1;
```

---

## 4. Redis down

**Symptom:** API returns 503 on health check (`/api/v1/health/ready`). BullMQ job processing halted. Session validation failing or slow (Better Auth cache miss).

**Diagnostic:**

```bash
# Check Redis container status
docker compose ps redis-cache redis-queue

# Ping Redis directly
redis-cli -h $REDIS_CACHE_HOST -p $REDIS_CACHE_PORT ping
redis-cli -h $REDIS_QUEUE_HOST -p $REDIS_QUEUE_PORT ping

# Check Redis logs
docker compose logs redis-cache --tail=100
docker compose logs redis-queue --tail=100
```

**Fallback behavior while Redis is down:**

- **Session auth**: unaffected. Better Auth is not configured with `secondaryStorage`, so sessions
  always resolve against Postgres — there is no Redis path to lose.
- **BullMQ**: Jobs queue in memory briefly; if the connection times out, new job enqueues fail with a 500.
  The queue client retries reconnecting indefinitely with a 3 s cap, so it recovers on its own once
  Redis is back — no restart needed.
- **Cache (redis-cache)**: Cache misses — all reads go to DB. Expect elevated DB CPU.
- **Socket.io pub/sub**: Disconnects; WebSocket clients will need to reconnect.

**Action:**

1. Restart the affected Redis container:

```bash
docker compose restart redis-cache
# or
docker compose restart redis-queue
```

2. If the container won't start (OOM, disk full), check host resources:

```bash
df -h        # disk usage
free -m      # memory
docker stats # per-container resource use
```

3. If data persistence is needed (RDB/AOF): verify the volume is intact before restart.
4. After restart, confirm health endpoint returns 200:

```bash
curl -s http://localhost:3000/api/v1/health/ready | jq .
```

**Escalation:** If Redis keeps crashing (OOM killer, maxmemory policy eviction storm), set `maxmemory-policy allkeys-lru` and size `maxmemory` appropriately. Engage infrastructure team if running on managed Redis (ElastiCache, Upstash, Redis Cloud).

---

## 5. Migration fail

**Symptom:** Deployment pipeline fails at the migration step. The `migration` container exits non-zero. API/worker containers hang in "Created" state because `service_completed_successfully` is not satisfied.

**Diagnostic:**

```bash
# See the exact migration error
docker compose logs migration

# Check which migration failed
docker compose exec postgres psql -U postgres -d nestjs_db \
  -c "SELECT migration_name, finished_at, applied_steps_count, logs
      FROM _prisma_migrations
      WHERE finished_at IS NULL OR applied_steps_count = 0
      ORDER BY started_at DESC LIMIT 5;"
```

**Action:**

1. **Do not retry the migration** until the root cause is understood — a half-applied migration with destructive DDL (DROP COLUMN, etc.) may leave the schema inconsistent.

2. If the migration is idempotent and safe to re-run (e.g. a connectivity blip caused the failure):

```bash
docker compose run --rm migration
```

3. If the migration applied partial DDL and must be rolled back:

```bash
# Connect to DB and inspect current schema state
docker compose exec postgres psql -U postgres -d nestjs_db

# Manually reverse the DDL if no automatic rollback exists (Prisma does not
# generate down migrations — the rollback SQL must be written manually).
# Example: if a column was added and needs removal:
# ALTER TABLE users DROP COLUMN IF EXISTS new_column;
```

4. After manual rollback, mark the failed migration as rolled back:

```sql
-- Remove the failed migration record so Prisma can reapply it cleanly
DELETE FROM _prisma_migrations WHERE migration_name = '20260516_failing_migration';
```

5. Fix the migration file, commit, redeploy.

**Prevention:**

- Always use additive-only migrations in production (add columns nullable first, backfill, then add NOT NULL constraint in a second migration).
- Test migrations against a production-sized DB snapshot in staging before merging.
- Take a backup immediately before any migration run: `./scripts/pg-backup.sh backup` (see **Backups** below).

**Escalation:** If the schema is in an unknown state and the DB cannot be brought back online, restore the backup taken before the migration run with `./scripts/pg-backup.sh restore <dump-file>`. This assumes such a backup exists — the repo ships the script but does NOT schedule it; if your deployment has no pre-migration backup, there is nothing to restore from, so wire the backup into your rollout before relying on this step. Engage the DBA team and document the incident timeline.

### 5a. Migration "drift detected" after pulling boilerplate updates

**Symptom:** `prisma migrate deploy` aborts with `P3017 / drift detected` immediately after pulling a new release. The `_prisma_migrations` checksum for `20260501000000_init` no longer matches the on-disk migration file.

**Root cause:** This boilerplate occasionally extends the `init` migration (e.g. squashing follow-up PR migrations into it) to keep first-boot deployments clean. Forks that already applied the previous shape of `init` will detect the checksum drift and refuse to proceed. The DB schema is fine — only the recorded checksum is stale.

**Diagnostic:**

```bash
# Confirm the only drifted migration is the init folder
docker compose exec postgres psql -U postgres -d nestjs_db \
  -c "SELECT migration_name, checksum FROM _prisma_migrations
      WHERE migration_name = '20260501000000_init';"
```

Compare the recorded checksum against `prisma migrate diff --from-empty --to-migrations prisma/migrations` to confirm the DDL is identical and only the file content shifted.

**Action (only when the schema matches — verify with `prisma migrate status` first):**

```bash
# Recompute and store the current checksum without re-applying DDL
docker compose exec postgres psql -U postgres -d nestjs_db <<'SQL'
UPDATE _prisma_migrations
   SET checksum = '<paste the value reported by `prisma migrate diff`>'
 WHERE migration_name = '20260501000000_init';
SQL

# Re-run deploy — drift should clear, subsequent migrations apply normally.
docker compose run --rm migration
```

Faster alternative for staging / sandbox: `prisma migrate resolve --applied 20260501000000_init` (requires Prisma CLI access). Production should prefer the explicit UPDATE so the change is reviewable.

**Escalation:** If `prisma migrate diff` shows ACTUAL schema differences (not just metadata), treat as a real migration mismatch — restore a pre-change backup (`./scripts/pg-backup.sh restore <dump-file>`) or manually apply the missing DDL before resolving the checksum. Never resolve drift on a DB whose schema you have not personally verified.

---

### 5b. "Applied migration missing from local directory" after a squash

**Symptom:** `prisma migrate deploy`/`dev` reports a migration that is recorded in `_prisma_migrations` but no longer exists on disk (e.g. a follow-up migration was folded into `20260501000000_init` and its folder removed to keep the boilerplate at a single init migration).

**Root cause:** The folded DDL now lives inside `init`, and a fork that already applied the standalone follow-up migration has a phantom ledger row pointing at a deleted folder. The DB schema is correct — the squashed DDL is already present — only the ledger references a file that is gone.

**Action (dev / sandbox):** `prisma migrate reset` rebuilds the DB from the single init migration (drops data — dev only).

**Action (preserve data — verify schema first with `prisma migrate status`):**

```bash
# Drop the phantom ledger row; its DDL is already applied and now lives inside init.
docker compose exec postgres psql -U postgres -d nestjs_db \
  -c "DELETE FROM _prisma_migrations WHERE migration_name = '20260601000000_indexes_hardening';"

# Re-run deploy — the ledger now matches the on-disk single init migration.
docker compose run --rm migration
```

**Escalation:** Only delete a ledger row after confirming the folded DDL is genuinely present in the live database — `prisma migrate diff --from-schema-datasource prisma/schema.prisma --to-migrations prisma/migrations` diffs the live DB against the on-disk migrations; an empty diff means they match. Never drop a ledger row on a DB whose schema you have not verified.

### 5c. `event_logs_default` is holding rows

**Symptom:** The scheduler logs `event_logs_default holds rows` (error level, emitted by `EventLogCleanupTask` after the 03:20 UTC partition-ensure run).

**Root cause:** `event_logs` is range-partitioned by month. `ensure_event_log_partition()` pre-creates the current month plus the next two; the DEFAULT partition is a safety net so an insert never fails when that has not happened. Rows landing there mean the ensure cron did not run for at least three months (scheduler down, leadership lease never acquired, or the function's `EXECUTE` grant was lost).

Rows in the DEFAULT partition are still readable and still immutable, but they are **outside** the monthly retention scheme — `drop_expired_event_log_partitions()` deliberately skips it (its regex only matches `event_logs_YYYY_MM`), so they are never purged. Worse, Postgres refuses to attach a new monthly partition whose range overlaps rows already sitting in DEFAULT, so the next ensure run for that month fails until the rows are moved.

**Diagnostic:**

```sql
SELECT date_trunc('month', "createdAt") AS month, count(*)
FROM event_logs_default
GROUP BY 1 ORDER BY 1;
```

**Action** — detach the DEFAULT partition to a staging table, create the months it was covering, copy the rows in, and drop the staging table. This mirrors what the partitioning migration does with `event_logs_legacy`, and for the same reason: the append-only trigger is cloned onto every partition, so `DELETE FROM event_logs_default` is rejected — `DROP TABLE` is not.

Take a backup of the table first (§10); the rows are irreplaceable if a step is mistyped.

```bash
docker compose exec postgres psql -U postgres -d nestjs_db <<'SQL'
BEGIN;

ALTER TABLE event_logs DETACH PARTITION event_logs_default;
ALTER TABLE event_logs_default RENAME TO event_logs_default_drain;
CREATE TABLE event_logs_default PARTITION OF event_logs DEFAULT;

-- One partition per month actually present in the drained rows.
DO $$
DECLARE m timestamptz;
BEGIN
  FOR m IN SELECT DISTINCT date_trunc('month', "createdAt") FROM event_logs_default_drain LOOP
    PERFORM public.ensure_event_log_partition(m);
  END LOOP;
END;
$$;

INSERT INTO event_logs (
  "id", "tenantId", "entityType", "entityId", "eventType",
  "payloadBefore", "payloadAfter", "actorType", "actorId",
  "source", "sourceJobId", "createdAt"
)
SELECT
  "id", "tenantId", "entityType", "entityId", "eventType",
  "payloadBefore", "payloadAfter", "actorType", "actorId",
  "source", "sourceJobId", "createdAt"
FROM event_logs_default_drain;

COMMIT;
SQL
```

Verify the counts match before dropping the staging table — this is the last point at which the rows still exist twice:

```sql
SELECT (SELECT count(*) FROM event_logs_default_drain) AS drained,
       (SELECT count(*) FROM event_logs
         WHERE ("id", "createdAt") IN (SELECT "id", "createdAt" FROM event_logs_default_drain)) AS copied;
```

When `drained = copied`:

```sql
DROP TABLE event_logs_default_drain;
```

Then fix why the cron stopped — the drain is a symptom fix, not the cause.

**Escalation:** If `ensure_event_log_partition()` fails with `permission denied for function`, the scheduler's role lacks the grant (see 5d).

### 5d. Granting the partition functions to an existing cluster

`20260810030000_event_log_partitioning` creates both functions `SECURITY DEFINER` and revokes `PUBLIC`, so **no role can execute them until granted**. A fresh dev stack runs as the owner and is unaffected; a provisioned cluster where the scheduler connects as a least-privilege role needs this once, out of band — Prisma migrations must stay environment-agnostic and cannot name your roles.

```bash
docker compose exec postgres psql -U postgres -d nestjs_db <<'SQL'
GRANT EXECUTE ON FUNCTION public.ensure_event_log_partition(timestamptz) TO scheduler_user;
GRANT EXECUTE ON FUNCTION public.drop_expired_event_log_partitions(date)  TO scheduler_user;
SQL
```

Verify:

```sql
SELECT has_function_privilege('scheduler_user',
  'public.ensure_event_log_partition(timestamptz)', 'EXECUTE');
```

Without it the ensure cron fails silently every night and rows accumulate in DEFAULT until 5c fires.

### 5e. Order channels blank after `20260820012711_affiliate_order_source_per_line`

**Symptom:** after this migration, orders that previously showed a channel come back with `sources: []` ("chưa xác định nguồn"), and they never fill in.

TikTok reports `content_type` per sold SKU, so the channel moved from `orders.source` to `order_lines.source` (TD-06-01). The migration **drops `orders.source` without backfilling** — the old value was a single collapsed channel for the whole order and cannot be attributed back to the line it came from. It was written for a dev database where losing it is free.

It does not self-heal. `AffiliateOrderSourceSyncTask.resolveWindow()` starts a rolling run at `min(affiliateSourceSyncedAt, now - 7 days)`, so only the last week is ever re-walked; anything older is outside every future window.

On any environment carrying real orders, clear the coverage watermark **after** the migration applies. A null watermark makes the next hourly tick schedule a backfill instead of a rolling run (`isBackfill`), which re-walks `TIKTOK_INITIAL_SYNC_LOOKBACK_DAYS` in 3-month chunks, one per tick:

```sql
UPDATE shops
   SET "affiliateSourceSyncedAt" = NULL,
       "affiliateSourceBackfillAbandonedCount" = 0;
```

Both columns, not just the watermark: `isBackfill` also requires the abandoned counter to be under `MAX_BACKFILL_ABANDON_ATTEMPTS`. A shop that already exhausted its backfill attempts would otherwise fall through to a rolling run and re-walk only the last 7 days — the exact thing this is meant to fix.

Do this only when no run is in flight — one unfinished run per shop is allowed, and an in-flight one blocks the fresh backfill from being created:

```sql
SELECT "shopId", status, "windowFrom", "windowTo"
  FROM affiliate_source_sync_runs WHERE "isUnfinished" = true;
```

Confirm recovery by watching the worker's `Affiliate order source sync attempt finished` log line. A steady `skippedLineNotFound` with `updated` at zero is a different fault — it means the affiliate endpoint's `skus[].sku_id` is not addressing the same lines the order sync wrote, not a missing backfill.

---

## 6. Metrics endpoint unreachable or leaking

**Symptom:** Prometheus scrape jobs return 403 or connection refused. Or, conversely, `/metrics` is accidentally reachable from the public internet.

**Diagnostic:**

```bash
# Confirm ENABLE_METRICS=true in the running container
docker compose exec api env | grep METRICS

# Check what IP the Prometheus pod sees as the source
# (must match a CIDR in METRICS_ALLOW_CIDRS or loopback)
curl -v http://<api-host>:3000/metrics 2>&1 | grep "< HTTP"
```

**IP allow-list gotcha:** The guard uses `socket.remoteAddress` (the TCP peer), **not** `X-Forwarded-For`. With `trustProxy: 1` set, `req.ip` would resolve from XFF and could be forged by a client that reaches a misconfigured ingress. Configure `METRICS_ALLOW_CIDRS` with the direct peer IPs reaching the API process:

- In Kubernetes: the Prometheus pod CIDR (e.g. `10.244.0.0/16`), not the node external IP.
- Behind an internal load balancer: the LB's internal IP range.
- If the metrics port is the same as the public API port, ensure the edge proxy strips or blocks `X-Forwarded-For` before forwarding to `/metrics`.

**Action:**

1. Set `METRICS_ALLOW_CIDRS` to the Prometheus scraper's pod CIDR or IP.
2. Preferred: bind `/metrics` on a separate internal port via reverse proxy (nginx `location /metrics { deny all; }` on the public vhost, separate internal vhost for Prometheus).
3. Restart the API container for env changes to take effect.

**Escalation:** If metrics are confirmed reachable from the public internet, treat as a security incident — Prometheus metrics expose internal labels, queue depths, and memory usage that aid attackers in profiling the system. Rotate any secrets that may appear in metric labels, restrict access immediately, and review ingress firewall rules.

### 5f. `audit_logs_default` is holding rows

**Symptom:** the scheduler logs `audit_logs_default holds rows` (error level, emitted by `CleanupTask` after the 04:00 UTC partition-ensure run).

**Root cause:** `audit_logs` is range-partitioned by month, and `ensure_audit_log_partition()` pre-creates the current month plus the next two. Rows landing in the DEFAULT partition mean that ensure did not run for at least three months — scheduler down, leadership lease never acquired, or the `EXECUTE` grant lost (see 5d).

Until VIS-258 the cause was simpler and permanent, and there were two of them. `ensureAuditLogPartitions()` called the function through `$queryRaw`, and `@prisma/adapter-pg` cannot decode the `void` return, so it threw `UnsupportedNativeDataType` before creating anything. Behind that, `ensure_audit_log_partition()` itself created an unqualified table under `search_path = pg_catalog, public`, so Postgres targeted `pg_catalog` and refused with `permission denied to create "pg_catalog.audit_logs_YYYY_MM"`. Either alone was enough; the error was caught and logged at one line while the cron reported success nightly. If an environment predates that fix, check the window before anything else:

```sql
SELECT relname FROM pg_class c
JOIN pg_inherits i ON i.inhrelid = c.oid
WHERE i.inhparent = '"audit_logs"'::regclass
ORDER BY relname DESC LIMIT 5;
```

A newest partition older than the current month means audit entries have been failing outright — those rows are gone, not misfiled, because the DEFAULT partition did not exist before VIS-258 either.

Rows in DEFAULT are readable but sit **outside** the monthly retention scheme: `drop_expired_audit_log_partitions()` skips it (its regex matches only `audit_logs_YYYY_MM`), so they are never purged. Postgres also refuses to attach a monthly partition whose range overlaps rows already in DEFAULT, so the next ensure run for those months fails until they move.

**Diagnostic:**

```sql
SELECT date_trunc('month', "createdAt") AS month, count(*)
FROM audit_logs_default
GROUP BY 1 ORDER BY 1;
```

**Action** — unlike `event_logs`, `audit_logs` carries no append-only trigger, so the rows can be moved with an ordinary `INSERT ... SELECT` plus `DELETE` inside one transaction. Take a backup first (§10).

The order below is not interchangeable. The rows must leave the DEFAULT partition **before** the month partitions are created, because Postgres refuses to attach a partition whose range overlaps a row still sitting in DEFAULT:

```
ERROR:  updated partition constraint for default partition "audit_logs_default"
        would be violated by some row
```

That is the same rule described above, seen from the other side, and it is why creating the partitions first fails on the very first statement.

```bash
docker compose exec postgres psql -U postgres -d nestjs_db <<'SQL'
BEGIN;

-- Step 1: take the rows out of DEFAULT. ON COMMIT DROP means a failed run leaves nothing behind,
-- and the rows return with the transaction.
CREATE TEMP TABLE audit_logs_drained ON COMMIT DROP AS
WITH drained AS (
  DELETE FROM audit_logs_default
  RETURNING "id", "userId", action, resource, metadata, "ipAddress", "userAgent", "createdAt",
            "tenantId"
)
SELECT * FROM drained;

-- Step 2: one partition per month present in the drained rows. DEFAULT is empty now, so nothing
-- blocks the attach.
DO $$
DECLARE m timestamptz;
BEGIN
  FOR m IN SELECT DISTINCT date_trunc('month', "createdAt") FROM audit_logs_drained LOOP
    PERFORM public.ensure_audit_log_partition(m);
  END LOOP;
END;
$$;

-- Step 3: put them back. They route into the month partitions created above.
INSERT INTO audit_logs ("id", "userId", action, resource, metadata, "ipAddress", "userAgent",
                        "createdAt", "tenantId")
SELECT "id", "userId", action, resource, metadata, "ipAddress", "userAgent", "createdAt", "tenantId"
FROM audit_logs_drained;

COMMIT;
SQL
```

The whole drain is one transaction, so a reader either sees the rows in DEFAULT or in their month partition, never in both and never in neither.

Verify DEFAULT is empty before considering it done:

```sql
SELECT count(*) FROM audit_logs_default;
```

Then fix why the cron stopped — the drain is a symptom fix, not the cause.

**Escalation:** if `ensure_audit_log_partition()` fails with `permission denied for function`, the scheduler's role lacks the grant (see 5d).

### 6a. BullMQ counters inflated by replica count

**Symptom:** `rate(bullmq_jobs_total{status="failed"}[5m])` jumps to N× the rate observed in the worker logs, where N matches `API_REPLICAS`. SLO alerts on the counter fire even though the actual worker error rate is fine.

**Root cause:** Every API replica subscribes to the same BullMQ `QueueEvents` Redis stream, so each terminal event (completed/failed/stalled/delayed) increments `bullmq_jobs_total` once per replica. The fan-out is intentional (it keeps the duration histogram observable per-replica) but the counter sample is multiplied by the replica count.

**Recipe for dashboards / alerts:** divide by the live API instance count instead of hard-coding the replica number.

```promql
# Replica-corrected failure rate
sum(rate(bullmq_jobs_total{status="failed"}[5m]))
  /
sum(up{job="api"})
```

Or, equivalently, average across instances:

```promql
avg by (queue, status) (rate(bullmq_jobs_total[5m]))
```

The `bullmq_job_duration_seconds` histogram is NOT inflated — each replica observes its own per-job samples — so quantile alerts on it work unmodified.

**Action:** update dashboards / recording rules to apply the division pattern. If you later move metric collection onto a leader-elected collector (see comment in `bullmq-metrics.listener.ts`), drop the divisor.

**Prevention:** when introducing a new BullMQ-derived counter, document its replica behaviour next to the metric definition in `metrics.service.ts` so dashboard authors are warned at definition time.

---

## 7. Upload bucket fills up with orphan objects

**Symptom:** S3 bucket size grows beyond what active users justify; many objects under `uploads/` were never referenced from any domain entity.

**Root cause:** The upload flow is presign -> browser POST -> confirm. A client can abandon staging, or the API can crash between S3 finalization and its HTTP response. Final objects can also outlive a user unless ownership is tracked durably.

**Mitigation in code:** presign writes staging under `uploads/` tagged `committed=false`. Confirm creates a `stored_files` row before copying the exact ETag-checked version to a fresh `files/` key, advances it to `VERIFYING`, then durably enqueues verification. Retrying after a lost response recovers the same database record and final object. `StoredFileCleanupTask` claims stale/rejected rows with optimistic concurrency before deleting S3 data, and removes committed files whose user no longer exists. Abandoned staging remains bounded by the lifecycle rule below.

**Required bucket lifecycle rule (apply at infra time, NOT in code):**

```json
{
  "Rules": [
    {
      "ID": "expire-uncommitted-uploads",
      "Status": "Enabled",
      "Filter": {
        "And": {
          "Prefix": "uploads/",
          "Tags": [{ "Key": "committed", "Value": "false" }]
        }
      },
      "Expiration": { "Days": 1 }
    }
  ]
}
```

Because presign tags objects `committed=false` upfront, the single tag-filtered rule above matches every orphan — no separate untagged sweep is needed (apply via `aws s3api put-bucket-lifecycle-configuration --bucket <name> --lifecycle-configuration file://lifecycle.json`).

**MinIO equivalent (`mc`):**

```bash
mc ilm rule add --expire-days 1 --tags "committed=false" myminio/<your-bucket>
```

**Action when bucket already bloated:** list orphan objects via `aws s3api list-objects-v2` + filter by the `committed=false` tag, then bulk delete. Cost-wise the lifecycle rule is the right long-term fix; manual cleanup is one-time.

**Escalation:** If orphan rate is high (>10% of total uploads), investigate whether legitimate clients are failing to call `/confirm` (network errors, FE bug, mobile background suspend). Add a Prometheus alert on `s3_bucket_object_count` divergence from a confirmed-upload counter.

---

## 8. Outbox cleanup (retention purge)

**Symptom:** `outbox_events` table keeps growing even though events are being processed normally. `processedAt IS NOT NULL` row count climbs unbounded.

**Root cause:** The outbox relay sets `processedAt` but never deletes rows. The daily purge cron (`OutboxCleanupTask`, 03:15 UTC) removes processed rows older than `OUTBOX_RETENTION_DAYS` (default 7). If the scheduler is down or the cron keeps erroring, rows accumulate.

**Diagnostic:**

```sql
-- How many processed rows are older than the retention window?
SELECT count(*)
FROM outbox_events
WHERE "processedAt" IS NOT NULL
  AND "createdAt" < NOW() - INTERVAL '7 days';

-- How many unprocessed rows exist? (these are NEVER deleted by the purge cron)
SELECT count(*)
FROM outbox_events
WHERE "processedAt" IS NULL;
```

Check if the purge cron ran recently:

```bash
docker compose logs scheduler --tail=500 | grep -i "outbox purge"
```

**Manually trigger a purge run** (without restarting the container):

```bash
# Connect a psql session and run the equivalent batched DELETE directly
docker compose exec postgres psql -U postgres -d nestjs_db <<'SQL'
DO $$
DECLARE
  deleted int;
  total   int := 0;
BEGIN
  LOOP
    DELETE FROM outbox_events
      WHERE id IN (
        SELECT id FROM outbox_events
         WHERE "processedAt" IS NOT NULL
           AND "createdAt" < NOW() - INTERVAL '7 days'
         LIMIT 1000
      );
    GET DIAGNOSTICS deleted = ROW_COUNT;
    EXIT WHEN deleted = 0;
    total := total + deleted;
  END LOOP;
  RAISE NOTICE 'Purged % row(s)', total;
END;
$$;
SQL
```

**Action:**

1. Confirm the scheduler container is running: `docker compose ps scheduler`.
2. Check logs for purge errors: `docker compose -f infra/compose/docker-compose.prod.yml logs --tail=200 iam-service | grep -E "purge|error"`.
3. If the cron has been silently failing (e.g. Prisma `$executeRawUnsafe` auth error), fix the root cause and redeploy the scheduler.
4. If the backlog is very large (millions of rows), raise `OUTBOX_PURGE_MAX_BATCHES` temporarily — the default cap of `200 × 1000 = 200k rows/run` may not drain the backlog in a single nightly run. Set `OUTBOX_PURGE_MAX_BATCHES=5000` and redeploy the scheduler, then revert after the backlog is clear.

**Monitoring:**

```sql
-- Daily trend: rows purged vs rows inserted (proxy for event rate)
SELECT date_trunc('day', "createdAt") AS day,
       count(*) FILTER (WHERE "processedAt" IS NOT NULL) AS processed,
       count(*) FILTER (WHERE "processedAt" IS NULL)     AS pending
FROM outbox_events
GROUP BY 1
ORDER BY 1 DESC
LIMIT 14;
```

If `ENABLE_METRICS=true`, the `outbox_lag_seconds` gauge reflects the age of the oldest unprocessed event in real time — alert when it trends upward after the relay is healthy.

**Escalation:** If processed rows accumulate despite the purge running successfully (i.e. the cron completes but `count(*)` still climbs), the event volume exceeds `OUTBOX_PURGE_MAX_BATCHES × OUTBOX_PURGE_BATCH_SIZE`. Calculate the required cap:

```text
required_max_batches = ceil(daily_event_volume / OUTBOX_PURGE_BATCH_SIZE)
```

Set `OUTBOX_PURGE_MAX_BATCHES` to at least that value and redeploy.

---

## 9. Tampered upload (declared MIME ≠ actual binary)

**Symptom:** Worker log entry like `verify-magic-bytes: MIME mismatch — deleting tampered upload`. The object disappears without a `/confirm` follow-through visible to the client.

**Cause:** Async magic-byte verification (`UploadVerificationProcessor` in the worker) reads the first 16 bytes of every confirmed object via S3 `GetObject Range`, runs `detectFileType()` from `libs/shared/src/lib/file-signature.ts`, and deletes the object if the binary signature contradicts the MIME declared at presign time. This catches `.exe` renamed to `.png` and similar evasion attempts.

**Diagnostic:**

- Bull Board UI: `http://localhost:3000/api/admin/queues` → `upload-verification` queue → inspect failed/completed jobs.
- Worker logs for the specific key: `docker compose logs worker | grep "<key>"`.

**Action:**

1. Genuine tampering — no action needed beyond noting the source IP in audit logs.
2. False positive on a legitimate format — extend `SIGNATURES` in `libs/shared/src/lib/file-signature.ts` to recognize the binary header, add a unit test, redeploy worker.
3. Repeated false positives from a single user — review whether the allow-list in the `presign` DTO is too narrow for the use case.

**Escalation:** Surge of deletions correlated with a single IP range may indicate an automated probe. Tighten `/upload/presign` rate-limit (currently 10/min per session — see `PRESIGN_LIMIT` in `upload.controller.ts`) or add a global ban list.

## 10. Backups

**Reality check:** this repo does NOT schedule database backups. It ships `scripts/pg-backup.sh` (on-demand `pg_dump` in custom format) so the restore steps elsewhere in this runbook have something to restore from — but scheduling and off-host retention are the operator's responsibility. A backup on the same volume/host as the database it protects is not a backup.

**On-demand backup:**

```bash
./scripts/pg-backup.sh backup ./backups        # writes ./backups/<db>-<UTC-timestamp>.dump
# then copy the artifact OFF-HOST (object storage that is NOT the app's own MinIO/S3)
```

**Restore (destructive — overwrites the target DB):**

```bash
./scripts/pg-backup.sh restore ./backups/<db>-<timestamp>.dump
```

**Before going to production, wire this in:**

- Schedule it (cron / systemd timer / CI job) at a cadence that matches your RPO.
- Push each dump to durable, off-host storage with its own retention policy.
- Periodically test a restore into a scratch DB — an untested backup is a guess.
- For low-RPO needs, graduate from logical dumps to continuous WAL archiving (pgBackRest / wal-g) against a managed Postgres or a replica; `pg_dump` alone cannot give point-in-time recovery.
