# Tenancy

Every business row belongs to a tenant, not to a user. A shop is run by a team, and the same
person may work for more than one — so `tenantId` is never derived from a user id.

## Naming

This concept has exactly one name in code: **`tenant`**. Never `workspace`, `organization` or
`company` — not in table names, columns, routes, classes, variables or `operationId`s. Inside this
repository `workspace` keeps a single unrelated meaning: a package of the frontend monorepo
(`@workspace/ui`), and the Nx workspace root.

The Vietnamese UI renders a tenant as "tổ chức"; a `shops` row is a "gian hàng". The two
are different levels — a tenant owns shops — so the display terms must not be swapped either.

## Model

```
users ──< tenant_members >── tenants ──< roles ──< role_permissions
   │          (role tier)        │              │
   │                             │              └──< tenant_member_roles >──┐
   │                             └──< tenant_invitations                    │
   └─────────────────────────────────────────────────────────────< shops ──┘
```

| Table                 | Holds                                                                                                                                                                                                                    |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `tenants`             | `name`, unique `slug`, `status` (`ACTIVE` \| `SUSPENDED`)                                                                                                                                                                |
| `tenant_members`      | which users may act for which tenant, membership `role` tier, `status`, and soft removal timestamp — no business-role column                                                                                             |
| `roles`               | builtin or tenant-defined business roles; `builtinKey` names the four seeded                                                                                                                                             |
| `role_permissions`    | one row per granted permission key from the catalog — empty for the three non-owner builtin roles, see below                                                                                                             |
| `tenant_member_roles` | one row per (member, business role, shop) — a member can hold different roles in different shops; `shopId = null` means the whole tenant. Replaces the old `tenant_members.roleId` + `user_shop_scope` pair (19/08/2026) |
| `tenant_invitations`  | pending member invitations (`email`, `role`, `roleId`, `token`, `status`, `expiresAt`)                                                                                                                                   |

## Three Role Axes

| Field                                                               | Values                           | Question it answers                                                                                                                            | Guard that reads it                 |
| ------------------------------------------------------------------- | -------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| `User.role`                                                         | `USER \| ADMIN`                  | Is this a ViShop platform operator? Unrelated to any tenant.                                                                                   | `RolesGuard` (`infra-auth`)         |
| `TenantMember.role`                                                 | `OWNER \| ADMIN \| MEMBER`       | Who administers the tenant itself? `OWNER` **bypasses every permission check** — the account holder must never be able to lock themselves out. | `PermissionGuard` (`infra-tenancy`) |
| `TenantMemberRole.roleId` $\rightarrow$ `effectivePermissionKeys()` | Permission keys from the catalog | May this member perform one specific action on business data?                                                                                  | `PermissionGuard` (`infra-tenancy`) |

Permission keys come from `PERMISSION_CATALOG` in `libs/contracts` — the single source for route
guards, the `GET /api/v1/permissions/catalog` response and the seeded roles. Product reference:
[`docs/product/vishop-permission-catalog.md`](./product/vishop-permission-catalog.md).

**Built-in role permissions are computed at read time, not stored.** `role_permissions` only ever
holds rows for tenant-defined custom roles. For the three non-owner seeded roles (`OPERATIONS`,
`WAREHOUSE`, `ACCOUNTING`), `effectivePermissionKeys(builtinKey, storedKeys)` (`libs/contracts`)
returns the catalog's hard-coded list for that `builtinKey` regardless of what — if anything — is
stored; for `OWNER` it returns every catalog key. Adding a permission to a built-in role is a code
change with zero migrations, and it applies to every existing tenant on the next read. `OWNER` is
still the membership-tier bypass above, not this computed set — a stray `TenantMemberRole` row
pointing at the `OWNER` role (should never happen outside a test fixture) computes to **zero**
permissions here on purpose, so it can never accidentally grant anything through this path.

```typescript
@Post('packages/:id/ship')
@RequirePermission(PERM.ORDERS_PACKAGE_SHIP)
ship(@CurrentTenant() tenant: TenantContext) { /* … */ }

// Several keys mean AND, not OR.
@Get('event-logs')
@RequirePermission(PERM.AUDIT_LOG_VIEW, PERM.DATA_BUYER_PII_VIEW)
list() { /* … */ }
```

**`RequireAnyPermission` is the OR counterpart** — the caller needs at least one of the listed
keys, not all of them. Use it for a "shared view" route that several unrelated permissions should
each unlock on their own (e.g. anyone holding any one of the five `print.*` keys may read the print
audit trail — none of them implies the others). If a route somehow carries both decorators, the
`RequirePermission` (AND) check runs first and denies before the OR check is ever reached — the
stricter default always wins.

```typescript
// Any ONE of these unlocks the route — not all five.
@Get('print-jobs/history')
@RequireAnyPermission(
  PERM.PRINT_LABEL,
  PERM.PRINT_LABEL_REPRINT,
  PERM.PRINT_DOCUMENT,
  PERM.PRINT_TEMPLATE_MANAGE,
  PERM.PRINT_WAVE_MANAGE,
)
history() { /* … */ }
```

## Provisioning & Invitation Acceptance

Signup provisions a tenant with that user as `OWNER` unless a pending invitation matches the registrant's email:

1. **Organic Signup**: `provision_tenant_for_user()` Postgres `AFTER INSERT` trigger creates a brand-new tenant, seeds the four builtin roles through `seed_builtin_roles()`, and makes the user `OWNER`. Seeding runs in the same transaction — a tenant without roles is unusable, so it must never exist even briefly.
2. **Invitation Signup**: If a `PENDING` unexpired `tenant_invitations` row matches `NEW.email`, `provision_tenant_for_user()` inserts `TenantMember` against the inviting tenant with the invited `role`, plus a `TenantMemberRole` row (`shopId: null`) when the invitation carried a `roleId`, and marks the invitation `ACCEPTED` without creating a phantom tenant.
3. **Existing Account Invitation**: Signed-in user redeems token via `POST /tenants/members/invitations/accept`. Account membership is attached directly without trigger execution.

## Resolution

`TenantContextGuard` is a global guard registered **after** `BetterAuthGuard`, which is what
resolves the session it reads.

1. Public routes and websocket frames are skipped.
2. A request with no session is left to the auth guard.
3. `X-Tenant-Id`, if sent, must name a tenant the caller belongs to. It applies to that one request
   and does not change what the session stores.
4. Otherwise `sessions.activeTenantId` is used, if it still names a tenant the caller belongs to.
5. Otherwise the sole membership is used; more than one is `422 tenant_ambiguous`.

The header outranks the session so a machine-to-machine caller can act on one tenant per request
without disturbing anyone's stored choice. A browser uses the session instead: making an SPA attach
a header to every call means one forgotten call writes to the wrong workspace with no visible
signal.

Step 4 falls through rather than failing when the stored choice has gone stale — membership
revoked, tenant suspended or soft-deleted. Losing access to one workspace must not lock the account
out of the one it still has.

Sessions pick their workspace through `PUT /api/v1/sessions/active-tenant` (FR-01-13). That endpoint,
`GET /api/v1/tenants` and `POST /api/v1/tenants` are the three that must work _before_ a workspace
is chosen, so they carry no `@RequirePermission` and never read `@CurrentTenant()` — membership on
`userId` is what bounds them.

The lookup is one indexed read per request and is deliberately **not cached** — a membership
revoked mid-session has to take effect on the next request. `BetterAuthGuard` passes
`disableCookieCache: true`, so a switch is visible on the very next request rather than up to five
minutes later.

Handlers read the result with `@CurrentTenant()`:

```typescript
@Get('shops')
async listShops(@CurrentTenant() tenant: TenantContext) {
  return this.queryBus.execute(new ListShopsQuery({ tenantId: tenant.tenantId }));
}
```

Never derive the tenant from `@CurrentUser().userId`.

## Member lifecycle and account scope

An owner action is tenant-scoped. Deactivate/reactivate changes `TenantMember.status` only; it does
not change global `User.status`, so the same person keeps access to other tenants. Removing a member
sets `TenantMember.deletedAt`, preserves audit/history rows, and frees the tenant seat. A platform
admin ban is a separate global operation against `User.status` and must not be implemented through
the tenant-member endpoints.

Passwords are different: Better Auth stores one credential per `User`, not per membership. Owner
password reset is therefore allowed only when the target has exactly one live membership across all
tenants, including suspended tenants. The reset and invitation acceptance share a transaction-level
advisory lock so a second membership cannot be attached between the check and the credential write.

## Shop scope

A tenant runs several shops, and a member may be limited to a subset of them (FR-01-05). The limit
lives on `TenantMemberRole.shopId` — one row per (member, role, shop); `shopId: null` means the whole
tenant — and reaches handlers on the tenant context:

```typescript
interface TenantContext {
  readonly tenantId: string;
  readonly permissions?: ReadonlySet<string>;
  /** Shops this member may act on. `null` means every shop of the tenant. */
  readonly shopScope?: ReadonlySet<string> | null;
}
```

`TenantContextService` derives `shopScope` from the same `TenantMemberRole` rows it reads for
permissions — no extra DB round trip. If **any** binding has `shopId: null` the member is
unrestricted; otherwise `shopScope` is the set of every bound `shopId`.

**`null` means unrestricted, not empty.** A member with no role binding, or with a whole-tenant
binding, sees every shop, including ones connected after their account was created. Default-closed
was rejected: it hides newly connected shops from everybody until someone remembers to grant them,
which fails silently during onboarding.

### Enforced in `orders` — the pattern every shop-scoped module follows

`libs/modules/orders`' repository is the first, and so far only, consumer — `PrismaOrderRepository`
filters both the list query and the single-resource lookup on `shopScope`. Any new module that owns
shop-scoped business data follows the same three rules.

**1. Filter every shop-scoped read and write.** Same rule as `tenantId`: from the guard, never from
a request body field.

```typescript
async list(tenantId: string, shopScope: ReadonlySet<string> | null) {
  const client = this.prisma.currentTransaction ?? this.prisma.dbRead;

  return client.order.findMany({
    where: {
      tenantId,
      // A null scope adds no clause. Do not translate it into `shopId: { in: [] }` — that
      // matches nothing and empties the screen instead of showing everything.
      ...(shopScope ? { shopId: { in: [...shopScope] } } : {}),
    },
  });
}
```

**2. Answer `404` for a single resource outside the scope, not `403`.** Same reasoning as the
tenant check: a distinguishable `403` turns the id into a probe for shops the caller cannot see.

**3. Cover it with an integration test.** A member scoped to shop A must not receive shop B rows
through the list endpoint, the detail endpoint, or any export. The case is already in
`docs/product/vishop-test-plan.md` under FR-01-05, and exercised for orders in
`apps/api/e2e/shop-scope.e2e-spec.ts`.

### Why the filter is not in the guard

A guard sees the request, not the query — it cannot add a `WHERE` clause to a statement the
repository has not built yet. A Prisma middleware injecting `shopId` globally would break every
query that legitimately spans shops: cross-shop reports, the tenant dashboard, the sync worker.
Scoping is a repository decision, exactly like `tenantId` scoping.

Row-level security in Postgres is the intended safety net for both, once it lands.

## Two-factor authentication

FR-01-10. TOTP + backup codes are entirely better-auth's `twoFactor` plugin — every
`/api/auth/two-factor/*` endpoint (enable, verify, disable, backup codes) is native, needs no Nest
route, and works before a tenant is resolved. What this app adds on top is a **mandatory-2FA
policy**, which the plugin has no concept of (it knows users, not tenants):

```prisma
model Tenant {
  require2faForAll Boolean @default(false) // applies to every member
}

model Role {
  requireTwoFactor Boolean @default(false) // applies to members holding this role
}
```

Effective policy for a membership = `tenant.require2faForAll || assignedRole.requireTwoFactor`.
Enforced in `TenantContextService.resolve()` — the same choke point every tenant-scoped route
already goes through — rather than at better-auth's sign-in hook, because the policy is per-tenant
and sign-in does not yet know which of a caller's several workspaces (FR-01-12/13) will be active.
A member of tenant A (mandatory) and tenant B (optional) must not be blocked signing in just
because A requires it.

**The owner is not exempt.** Unlike the `PermissionGuard` bypass, this check runs for OWNER too —
2FA is account security, not a business permission, and the owner is the account most worth
protecting.

**Self-lockout guard.** `resolve()` is also what gates the endpoint used to turn the policy off, so
an actor enabling it without having enabled their own 2FA first would lock themselves out of the
very next request. `SetTenantTwoFactorPolicyHandler` / `SetRoleTwoFactorPolicyHandler` reject
`kind: 'validation'` (`two_factor_self_required`) when the caller tries to mandate 2FA on a policy
that would immediately apply to their own membership. It only guards the actor's own exposure —
mandating it tenant-wide still locks out every other member who has not enabled 2FA yet, which is
the point of "bắt buộc".

**Owner-assisted backup-code reissue** (`POST /api/v1/tenants/members/:id/two-factor/reset-backup-codes`)
exists because the plugin's own reissue endpoint is session-bound to the caller — there is no
HTTP way for an Owner to act on a locked-out member. Restricted to the tenant OWNER — not merely
a caller holding `members.manage`, which a custom role can grant — via the shared
`assertOwnerActingOnAnotherMember` guard (`member-lifecycle.guards.ts`) it runs inside
`prisma.transaction()` alongside the other four owner-driven lifecycle actions (reset-password,
deactivate, reactivate, remove); skipping the guard would let any custom role holding
`members.manage` harvest another member's, including the Owner's, plaintext backup codes. The
handler calls `generateEncryptedBackupCodes()` (`libs/infra/auth`), a small reimplementation of the
plugin's own backup-code format using only its public `better-auth/crypto` primitives
(`generateRandomString`, `symmetricEncrypt`) — the plugin's own generator is an internal,
unexported function. Cannot target the caller's own membership: this path skips the password
confirmation the self-service endpoint requires, and is rejected the same way as every other
self-targeted lifecycle action (`member_self_action_denied`), not a two-factor-specific code.

## Errors

| Code                       | Status | Raised when                                                                                                    |
| -------------------------- | ------ | -------------------------------------------------------------------------------------------------------------- |
| `tenant_not_found`         | 404    | No active membership, or `X-Tenant-Id` names one the caller lacks                                              |
| `tenant_ambiguous`         | 422    | Several memberships and no header to pick between them                                                         |
| `two_factor_required`      | 403    | Mandatory-2FA policy applies to this membership and 2FA is not enabled                                         |
| `two_factor_self_required` | 422    | Caller tried to mandate 2FA on a policy that covers their own membership, without having enabled it themselves |

A tenant that exists but is not the caller's answers `404`, the same as one that does not
exist — `403` would turn the header into a probe for tenant ids.

## Writing tenant-scoped queries

Every read and write filters on `tenantId`, from the request, never from a body field the
client controls:

```typescript
// Correct — the tenant comes from the guard.
where: { id: shopId, tenantId: tenant.tenantId }

// Wrong — a caller who knows an id from another tenant gets it.
where: { id: shopId }
```

`TikTokScopeGuard` is the worked example: it takes `shopId` off the request and matches it
against `(id, tenantId, ACTIVE)`.

Row-level security in Postgres is the intended next layer — a safety net for the query
someone forgets to scope, not a replacement for scoping it.
