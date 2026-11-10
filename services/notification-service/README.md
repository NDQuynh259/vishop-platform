# Notification service

Service business rules, architecture and database ERDs: [docs/README.md](docs/README.md).

This service contains three business modules (`notification`, `setting`, `announcement`), shared notification constants, database
schema and standalone Flyway migration CLI. See [module design](docs/module-design.md)
for implemented use cases and adapter contracts. [Shared constants](docs/constants.md) define
feature/type codes and defaults; there is no catalog module, registry or catalog HTTP API.
The setting module implements notification_settings and notification_alert_rules:
channel and per-type rule HTTP APIs with transactional JPA adapters; see
[setting API and limits](docs/setting.md). The notification module owns shared content, in-app recipients
and external deliveries. Its JPA adapters persist deliveries;
HTTP adapters expose personal list/detail/unread/read/archive APIs; see [inbox API](docs/inbox.md).
Other persistence/HTTP adapters, event consumers,
runtime gateway authentication, realtime and provider workers are subsequent work.
It does not yet start a standalone server or send notifications.

V14 removes the unused tables for feature subscriptions, explicit rule recipients and personal preferences.
NotificationSettingDispatchPolicy combines per-type rules with enabled, available and verified
channel connections. Recipient authorization still belongs to RecipientDirectory.

## CQRS

The setting controller dispatches reads through `QueryBus` and writes through `CommandBus`.
Channel handlers call `NotificationSettingService`, which owns list/get-by-ID,
creation and update operations and calls the repository port. The service never calls
a CQRS handler. `NotificationSettingManagementApi` remains its interface for callers
outside the setting module. Shared connection validation and response mapping are in
`NotificationChannelSupport`; this helper performs no persistence operations.
Rule handlers call `NotificationAlertRuleService` through the same CQRS flow.
Command handlers run in a write transaction; query handlers use read-only transactions.
The buses execute synchronously in the same process; they are independent of Kafka.

| Module | Queries | Commands |
| --- | --- | --- |
| setting | `ListChannelsQuery`, `GetChannelQuery`, `ListRulesQuery`, `GetRuleQuery` | `CreateChannelCommand`, `UpdateChannelCommand`, `CreateRuleCommand`, `UpdateRuleCommand` |
| notification | `ListInboxQuery`, `GetNotificationQuery`, `UnreadCountQuery` | `CreateNotificationCommand`, `MarkNotificationReadCommand`, `MarkAllNotificationsReadCommand`, `ArchiveNotificationCommand`, `EnqueueDeliveriesCommand` |
| announcement | — | `CreateAnnouncementCommand`, `ReviseAnnouncementCommand`, `PublishAnnouncementCommand` |

`NotificationDispatchCoordinator` dispatches inbox creation and delivery enqueue commands
through the shared bus. Eligible routes are dispatched without a cooldown gate.
The journal continues to own workflow idempotency and its eventual transaction boundary.
Setting registers its handlers and the conditional common CQRS buses in
`NotificationSettingModuleConfiguration`; notification uses `NotificationModuleConfiguration`.
Other handlers are Spring components and need
their application API implementations when those modules are wired into a runtime.
CQRS does not add the missing provider, persistence or HTTP runtime adapters listed above.

## Migrate

Use PostgreSQL 16 and a dedicated `vishop_notification` database. Fresh infrastructure
volumes create it through `infra/postgres/init-db.sql`. For an existing PostgreSQL
volume, create the database explicitly as an administrator; do not reset the volume:

```sql
CREATE DATABASE vishop_notification OWNER vishop;
```

Replace `vishop` with your database owner. Configure migration credentials explicitly:

```powershell
$env:NOTIFICATION_DB_URL = 'jdbc:postgresql://localhost:5432/vishop_notification'
$env:NOTIFICATION_DB_USER = 'vishop'
$env:NOTIFICATION_DB_PASSWORD = '<database-password>'
.\gradlew.bat migrateNotification
```

The equivalent module task is `:services:notification-service:migrate`. Migrations
do not baseline existing unmanaged schemas or clean databases. No runtime migration
or database creation occurs in the CLI. Application UUIDs require no extensions.

## Tables

| Table | Purpose |
| --- | --- |
| `notifications` | Shared title/body, type, severity, source event, subject, permission and optional shop reference |
| `notification_recipients` | In-app inbox and independent read timestamp per tenant/user |
| `notification_alert_rules` | Per-tenant/type channel settings; legacy thresholds/cooldown columns are inactive |
| `notification_settings` | Tenant channel switches and provider/bot connection configuration |
| `notification_deliveries` | Durable EMAIL/TELEGRAM/LARK jobs, optional user/setting IDs, retries and worker leases |
| `consumed_messages` | Event processing idempotency, compatible with common-persistence |
| `outbox_events` | Transactional publication, including future realtime signals |

V18 removes `notification_deduplication_archive` and `notification_delivery_attempts`.
The current schema has seven business tables, plus Flyway's schema history. Delivery status,
attempt counters and provider IDs stay in `notification_deliveries`; technical details belong in logging.

V19 allows multiple Lark group settings per tenant. Each group is addressed by its setting ID;
the tenant/id/channel key remains for delivery foreign keys. The other channels retain one
setting per tenant. Listing returns every active Lark group; cursor identity includes the setting ID.

Tenant/user/shop IDs are external references to their owning services; no cross-database
foreign keys. Local foreign keys include tenant ID to reject mismatched tenant links.
These constraints do not replace authorization: every API query must scope by tenant
and user, and recheck membership and business permission before exposing content.
Tenant members can access every shop belonging to their tenant; no per-user shop grant is required.

In-app list and unread queries use `(tenant_id, user_id)` on recipients; join notifications
on both tenant and notification ID. Read-all should use a cutoff timestamp so it does not
mark concurrently arriving notifications as read. Setting entities maintain timestamps through
lifecycle callbacks and versions through `@Version`; other modules still require persistence adapters.

V15 gives `notification_recipients` its own UUIDv7 `id` primary key. Existing rows are
backfilled without changing read/archive state; new recipient IDs come from the application.
The unique `(tenant_id, notification_id, user_id)` constraint and tenant/notification FK remain.

V16 redesigns the existing nine tables without deleting their rows. Rule channels move
to a `channels TEXT[]` collection; queue channels include EMAIL, TELEGRAM and LARK.
Delivery/cooldown routes may bind a tenant/channel setting and may target a shared
destination without a user. Deduplication receives its own UUIDv7 ID. SHA256 destination
keys preserve email case normalization and case-sensitive chat/webhook identities.
See the [V16 database design and ERD](docs/database-design.md) for migration, constraints
and the deployment requirement to update JPA code together with the schema.

V5 adds common audit columns to business/configuration tables: `created_at`/`updated_at`
default to `now()`, `deleted_at` defaults to NULL and `version` defaults to 0 with a
nonnegative constraint. Queue rows have timestamps/version without soft deletion;
delivery attempt history only gains `created_at`. Operational outbox, consumed-message
and cooldown rows retain their specialized columns. See the [database design](docs/database-design.md)
for the per-table policy, backfill and soft-delete query requirements.

`notification_alert_rules` configures tenant type/channel policies and
`notification_settings` configures tenant channel connections. V14 drops the unused
`notification_tenant_features`, `notification_user_preferences`, and
`notification_alert_rule_recipients` tables, including their stored data. Feature switches,
personal preferences and per-rule user lists are not part of the current setting API.
Inbox recipients, deliveries, attempts, cooldown and event-processing state are retained.

## Tenant choices and system notifications

V9 gives notification_settings a UUIDv7 primary key and keeps tenant/channel unique.
The application generates new IDs with UuidV7.nextId(); the migration backfills existing
rows without changing connection data or audit state. There is no composite ID class.

V7 moves definitions from PostgreSQL to shared NotificationFeatureCode/NotificationTypeCode enums.
The catalog module and its read APIs have been removed. FE manages its selection list using
the same codes. Tenant settings/history remain in PostgreSQL with no catalog FK. New configuration
writes must validate codes through the shared enums. Existing legacy codes stay stored
but do not implicitly become supported types. Tenant admins edit channel connections and type rules.

The current two-table routing policy evaluates:

1. Supported type code and per-type rule (missing row: code catalog defaults).
2. Rule channel choices intersected with enabled, available, verified channel connections.
3. Recipient authorization through the existing dispatch ports.

Feature groups, per-rule recipient lists and personal preferences are not consulted in this phase.

There are no default recipient lists: domain recipient resolution and tenant-selected users
must be implemented by consumers. Settings cannot grant access to data or features outside
a tenant's entitlements. `connection_config.connectionRef` identifies provider configuration held outside this
schema; do not place SMTP passwords or access tokens here. V3 preserves enabled email channels
for tenants with existing enabled email rules. New tenants default to in-app delivery only.

Seeded feature groups include system, IAM, subscriptions, shops, inventory, products,
fulfillment, finance and synchronization. Seeded types cover M14 alerts, shipment exceptions,
invitations, subscription activation/expiry, and system announcements/maintenance/incidents.
Catalog entries describe configurable capabilities; they do not imply source event producers
or senders are implemented.

`SYSTEM` is a type category, not a tenant bypass. Platform announcements are materialized
into separate tenant-scoped `notifications` and recipient rows using the same source event
ID, so content access and read states remain tenant/user scoped. V3 does not add an
announcement CMS, audience expansion job or mandatory notification exceptions. System
notifications follow the same type/channel choices and permission checks as other
notifications. All notifications still require an explicit permission; category alone never
grants access. The future producer/consumer contract must map each type to the correct IAM
permission and recipients.

## Channel connection settings

V4 renames the original personal `notification_settings` table to
`notification_user_preferences`, and `notification_tenant_channels` to
`notification_settings`. Both existing personal preferences and tenant provider references
are preserved. `notification_settings` is now unique by `(tenant_id, channel)` and holds
channel connection information, not a user's choice of notification types.

| Field | Meaning |
| --- | --- |
| `tenant_id`, `channel` | Tenant and channel: `IN_APP`, `EMAIL`, `TELEGRAM` |
| `name` | Display name, at most 150 characters; defaults to the channel name through the API |
| `enabled` | Enable this channel for the tenant |
| `connection_config` | JSONB: `connectionRef`, `credentialsRef`, `telegramBotUsername`, `telegramChatId`, `telegramMessageThreadId` |

A Telegram configuration can be saved disabled while incomplete. Enabling it requires a
nonblank chat ID and credentials reference. Bot-specific fields cannot be attached to
another channel, and in-app needs no provider credentials. A reference is not a token:
the setting API exposes write-only bot credentials through a provider/secret-store port and
returns only connection metadata. The default port rejects credential writes and external
channel enablement until secret storage and provider verification adapters are installed.
See [setting implementation](docs/setting.md).

Tenant type rules and code catalog defaults select enabled channels. The V16 queue supports
EMAIL/TELEGRAM/LARK. Delivery JPA
adapters are registered in `NotificationModuleConfiguration`. Provider workers,
audience resolvers and test-connection APIs remain
subsequent work. Creating a bot configuration does not itself send messages.

Consumers must record consumed messages, notifications, recipients, delivery jobs and outbox
events in one transaction. Distinct source events are dispatched independently, even for
the same subject and severity. Event replay protection and per-notification destination
uniqueness remain active. Threshold evaluation belongs to the source domain.

V17 retires cooldown. It renames the old deduplication table to
`notification_deduplication_archive` to preserve existing data. No runtime entity,
repository or reservation command accesses the archive. Deploy this migration together
with the updated dispatch code; older versions still expect the original table name.

V18 then deletes the unused archive and per-attempt history table. Back up existing databases
before applying this cleanup; the remaining operational tables and their rows are preserved.

External jobs may target users, invitees or shared groups; `user_id` is optional.
Destinations are snapshots, deduplicated per notification/channel. EMAIL ignores case;
TELEGRAM/LARK preserve case. Workers must recheck recipient eligibility, claim jobs with
`FOR UPDATE SKIP LOCKED`, set a unique lock token and lease, and fence every completion
by that token. Expired leases can be recovered. `SENT` records require `sent_at`;
terminal failures retain their history. SMTP cannot guarantee exactly-once delivery after
an ambiguous provider timeout; use provider idempotency where available and record
`UNKNOWN` outcomes. No provider secrets belong in these tables or template data.

Realtime outbox payloads should only contain `{ notificationId, tenantId }`; clients
fetch content through authorized APIs. Telegram sending, Zalo and digest are not implemented.

## Verification

```powershell
.\gradlew.bat :services:notification-service:check
```

Tests use an isolated embedded PostgreSQL 16 instance. They verify fresh/repeated
migrations, per-user read state, tenant foreign keys, destination deduplication,
delivery state constraints, event transaction rollback, cooldown archive preservation,
personal preference isolation, feature/channel/type choices per tenant, system type categories,
Telegram configuration constraints, code-owned catalog metadata and upgrades from V1–V6 without loss of existing
notifications, custom type configuration, personal preferences or provider connections.

V10 migrates the five connection columns into `connection_config` JSONB, preserving existing values, audit fields and version. Credentials remain secret-store references.
