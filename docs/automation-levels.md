# Automation levels (FR-14-09)

Three levels, split by whether the work can be taken back — not by how hard it is. A one-line change
that cannot be undone still needs a person; a complicated change that can be undone does not.

| Level | Name              | Means                                                        | Default |
| ----- | ----------------- | ------------------------------------------------------------ | ------- |
| 1     | Auto              | Reads only. Writes nothing, so nothing needs turning on.     | On      |
| 2     | Opt-in            | Writes ViShop's own data, reversibly.                        | **Off** |
| 3     | Approval required | Writes to the marketplace, touches money, or is irreversible | Never   |

Fixed at level 3 and not configurable downwards: buying a shipping label, handing a package over,
approving a return, cancelling an order, and pushing stock through Update Inventory.

## Where the decision is made

`AUTOMATION_ACTION_CATALOG` (`libs/contracts/src/lib/automation/`) is the only place a level is
declared. Each entry carries the level, whether it is fixed, a Vietnamese title, the consequence
spelled out as a sentence, and the TikTok API paths the action reaches.

## Where it is enforced

`TikTokHttpClient.request()` calls `AutomationGuardService.assertPlatformWriteAllowed()` before it
signs anything. If the code running is unattended and the path matches a catalogued action, the call
is refused unless that action was cleared by name first. This is the whole enforcement point —
adding a new automated caller of a level-3 endpoint fails loudly rather than silently working.

"Unattended" has two sources:

- **A process default.** The worker and the scheduler register
  `AutomationModule.forProcess({ defaultOrigin })`, so everything they do is automated unless said
  otherwise. A platform write added to either process years from now is gated without its author
  having to know this document exists.
- **An explicit run.** `AutomationContextService.run()` marks a call tree inside a process that also
  serves people — the assistant, a webhook, a future Open API caller.

The API process has no default: an interactive request is a person clicking, and the levels were
never about them.

## Standing approvals

Some queue jobs carry out a decision a person already made on a screen. They enter the context with
`preApprovedActions`, naming the exemption at the call site rather than hiding it in the guard:

| Job                     | Standing approval         | Given by                                                                                       |
| ----------------------- | ------------------------- | ---------------------------------------------------------------------------------------------- |
| `tiktok.ship-package`   | `orders.package_handover` | Confirming the ship batch                                                                      |
| `tiktok.fetch-label`    | `orders.label_purchase`   | Confirming the print batch                                                                     |
| `tiktok.prefetch-label` | `orders.label_purchase`   | The handover that preceded it — the platform issues a label only after handover, and only once |
| `tiktok.push-stock`     | `inventory.push`          | The manual push screen, or the shop's own `inventorySyncEnabled` switch                        |

Automatic inventory sync is the one automated marketplace write the product ships today. Its
level-3 step is covered by the seller's own per-shop switch, so
`InventoryPushCoordinatorService` does not re-ask the level question — it checks the kill switch
only, which is where "one action stops all automation" has to reach.

## Kill switch

`POST /api/v1/assistant/automation/kill-switch` stops every automated write for the organisation,
immediately, with no deploy. `DELETE` releases it. Engaging an already-engaged switch keeps the
moment it was first pulled: that timestamp is the answer to "since when", and restarting it would
erase the only record of how long automation has been off. Releasing it does not undo per-action
settings — it returns the system to exactly the state it was in before the switch was pulled.

The switch is checked before the level, so an action stays off even if somebody re-enables it while
the switch is out.

## Observation window

A level-2 action can only be switched on after the organisation has run in log-only mode for
`AUTOMATION_SHADOW_MODE_MIN_DAYS` (default 14). The clock starts when the organisation's automation
row is created, which is the first time anything reads it. `GET
/api/v1/assistant/automation/settings` reports `level2AvailableAt`; switching one on before then
answers `409 automation_shadow_mode_too_short`. Switching an action **off** is never blocked.

## Coverage at the entry points

`AskAssistantHandler` (`libs/modules/assistant`) and `IngestTikTokWebhookHandler`
(`libs/modules/shops`) each enter the context at the top of `execute()` — `AUTOMATION_ORIGIN.ASSISTANT`
with the caller's `userId` as actor, `AUTOMATION_ORIGIN.WEBHOOK` with no actor (the platform, not a
ViShop user, triggered it). Neither reaches a catalogued write path today — assistant tool executors
are held read-only by a static architecture test (`read-only-executors.spec.ts`), and the webhook
writes only to ViShop's own tables — but a future action tool or webhook-driven write now lands
inside the context automatically instead of running unattended-but-unmarked, which is what an
unwrapped caller does (see "Where it is enforced" above — `AutomationGuardService` treats an
unmarked call tree as a person acting, not as automation to gate).
