# Membership cache V13

The login session is independent of the selected tenant. Two devices have different `sid`
values and may select different tenants. Changes to a membership must affect that tenant,
without invalidating the user's other sessions or refresh credentials.

## Physical Redis layout

All keys below have the configured application/environment prefix.

```text
auth:user:{userId}                 Account status, systemRole, tokenVersion; no key TTL
auth:member:{tenantId}:{userId}    Membership status, userId, roleId; no key TTL
auth:session:{sid}                Hash payload/version/checkedAt; absolute session TTL
auth:tenant:{tenantId}            Hash payload/version/checkedAt; no key TTL
auth:role:{tenantId}:{roleId}     Hash payload/version/checkedAt; shared permissions
```

Each entry is a separate Redis Hash containing exactly `payload` (AuthorizationSnapshot JSON),
`version` (monotonic decimal revision) and `checkedAt` (Redis server confirmation time).
The same key identifies the durable outbox row and internal loader request; no field mapping
or user-wide JSON rewrite is needed.

Lua updates only the addressed key. A slow loader cannot overwrite a newer revision or refresh
its timestamp. Updating user state or tenant A never refreshes tenant B. Session expiry cannot
remove account/membership watermarks.

Memberships contain no permission lists. Permission edits update one role snapshot, without
per-member fan-out. The reader fetches only the requested keys and never loads every membership
for a user. Separate keys keep the storage protocol uniform across all authorization entities.

## Authorization behavior

Gateway validates JWT, user tokenVersion, and session first. For a selected tenant it reads
tenant and membership state, then resolves permissions using the membership's current roleId.
Existing JWT role claims remain accepted for compatibility, but do not grant authority.
IAM still verifies signed context against current DB state.

- Inactive/deleted/missing membership: 403 `membership-inactive`.
- Role reassignment: the same access token uses the new role's permissions on the next
  confirmed cache read; refresh does not require a new login.
- Inactive tenant A: tenant B and all login sessions remain valid. A refresh request can
  explicitly select B even if the refresh token previously selected A.
- Account security changes still increase users.token_version and revoke old credentials.
- Re-activating a membership can make an unexpired token usable again. No membership generation
  is embedded in JWT; this is current-state authorization, not permanent per-token revocation.

## Commit and delivery

V13 replaces the membership global-revocation trigger with a durable, versioned membership
projection. Deletion and soft deletion leave INACTIVE tombstones. INSERT/UPDATE/DELETE and
identity changes update the appropriate logical rows; rollback also rolls back those rows.

Membership repository writes register an after-commit callback. Delivery uses a new transaction,
locks the outbox row, and publishes only committed state. API success means Redis acknowledged
the current membership snapshot. A delivery error returns 503 while the DB change and pending
outbox survive. Repeating deactivate re-delivers without duplicating its audit event.
The scheduled relay retries pending changes and also handles direct SQL mutations.

This does not cancel requests already authorized. A failed delivery may leave older cache data
usable until the freshness deadline (default 30 seconds). Cache/loader failures deny the request;
strict instantaneous authorization must be enforced at the business operation against an
authoritative state, rather than inferred from a failed cache write.

## Cutover from V12

1. Prepare matching IAM, gateway and common-cache binaries. Old gateways do not check membership.
2. Drain/stop old gateway traffic before applying V13; do not mix old gateways with V13.
3. Apply Flyway V13 using the existing explicit migration command. It backfills membership
   outbox rows, preserving tokenVersion and existing sessions.
4. Start the new IAM and gateways. The relay publishes keys; cold or stale keys can load
   through IAM. Missing membership snapshots deny access. Existing V12 access/refresh tokens
   remain structurally compatible.
5. Verify inactive A/active B, role reassignment, session logout, cache delivery failure/retry,
   and membership key revisions before restoring normal traffic.

Rolling back to old gateway code alone is unsafe after V13: it requires restoring equivalent
membership enforcement or the old global revocation mechanism as well.

## Capacity checks

No production load capacity is implied by the embedded Redis tests. Measure representative
user/membership/session counts and request rates. Monitor INFO memory (`used_memory`,
`maxmemory`), INFO stats (`evicted_keys`, hits/misses), command rates, and application Redis
latency/loader errors. Session keys expire; membership/user watermarks and tombstones do
not currently have a retention job. Include them in memory sizing. Evicted keys reload from
the durable IAM outbox; they do not fall back to JWT permissions.
