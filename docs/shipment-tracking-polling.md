# Shipment tracking polling (FR-06-14)

How a parcel's carrier journey stays current without a webhook, and why the write path looks the way
it does. Code lives in `libs/modules/orders` (domain + application + infrastructure),
`apps/scheduler` (the cron) and `apps/worker` (the queue consumer).

## Why polling at all

TikTok publishes no webhook for carrier milestones — the `event_type` enum of `GET
/event/202309/webhooks` has 17 kinds and none of them is a shipping scan. `PACKAGE_UPDATE` fires
only when a parcel is combined, split or re-addressed, so it cannot stand in. The journey therefore
has to be asked for.

`ORDER_STATUS_CHANGE` does arrive, and it is free: it costs no API call, so it doubles as the
accelerator (`AWAITING_COLLECTION`, `IN_TRANSIT`, `DELIVERED` → poll now) and the brake
(`CANCELLED` → stop polling) on the schedule.

`DELIVERED` accelerates rather than stops. The delivery scan (`50101`) is only ever read by a poll,
and stopping outright on the status event meant no poll ran after it — measured on sandbox VN, two
delivered orders kept `PENDING_PICKUP`, no `deliveredAt` and no scan while the platform held all
five milestones. The order sync writes `packages.status = COMPLETED` in the same transaction as the
outbox row, so by the time the listener brings the poll forward the package status is already there
to stop it after that one read.

## The pipeline

```
scheduler   ShipmentTrackingPollTask  (@Cron every 5', leader-gated, `running` guard)
              1. EnrollShipmentTrackingCommand — open journeys for shipped parcels that have none
              2. claimDueForPolling            — take what is due, stamp the lease in the claim
              3. dispatch one job per ORDER
                        │  queue tiktok.sync-tracking
worker      TikTokSyncTrackingProcessor → CommandBus → PollShipmentTrackingHandler
              → OrderProvider.getOrderTracking (REPORT tier, behind the rate governor)
```

The cron never calls the platform itself. A single-leader scheduler would issue those calls serially
and could not retry them; the worker runs several at a time, retries three times, and yields to the
rate governor.

### Why the call is per order, not per parcel

`GET /fulfillment/202309/orders/{order_id}/tracking` takes an **order** id and returns the whole
timeline. A split order has several `packages` rows — one `shipment_tracking` row each — and the
platform offers no per-parcel view of the journey. So one call is made per order and its result is
written back to **every** tracking row of that order. Writing only the first is how a split order's
second parcel gets stamped every round and never receives a milestone.

### Journey scope

Because that timeline belongs to the order, a split order's delivery scan may be a sibling's. Each
journey therefore carries a derived, never stored, `scope`:

| Scope     | When                                                                   | Effect                                                                                                                 |
| --------- | ---------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `PACKAGE` | Every order on the parcel has only this parcel (a combined parcel too) | Milestones are the parcel's own. `50101` closes the header and sets `deliveredAt`.                                     |
| `ORDER`   | Any order on the parcel is split across several parcels                | Every scan is still logged. An end state (`DELIVERED`, `RETURNED`) does not move the header and sets no `deliveredAt`. |

A parcel of an `ORDER` journey closes on `package_status = COMPLETED` instead — the platform's
package status is per parcel. `applyPackageStatus` is the one place that reads it, and it moves the
header to `DELIVERED` without inventing a scan or a delivery time. The order's own delivery time is
not lost: `orders.deliveredAt` holds `delivery_time` from the order sync; it is not copied onto each
parcel because a parcel delivered first would carry a later time than it arrived at.

Every path — the poll, the manual sync, the shipping read and the data repair migration — derives
`scope` per parcel from one grouped query over `package_lines`: the most-split order the parcel
carries decides, and the count goes through `journeyScopeFor`. Counting only the order being polled
is not enough: a combined parcel may carry one order shipped whole and another split across
parcels, and its row receives the shared timeline of both.

## Enrolment

`claimDueForPolling` can only see rows that exist. Before this feature a `shipment_tracking` row
appeared only when somebody pressed "sync now" by hand, so a parcel nobody touched was never polled.
`EnrollShipmentTrackingCommand` sweeps `packages` for parcels that carry a tracking number, shipped
inside the give-up window, are not `CANCELLED`, and have no journey row yet, and opens one for each.

A `COMPLETED` parcel is enrolled on purpose: one delivered before its journey opened still has its
whole history on the platform, and the terminal milestone stops the loop on that first read.

A parcel the platform has not labelled has no tracking number. TikTok writes that as
`tracking_number: ""`, not an absent field, so the order sync stores it as `NULL` — an empty string
passed every `IS NOT NULL` check as shipped, and blocked the Seller Shipping fill-in that is guarded
on `NULL`.

`packages_awaiting_tracking_idx` backs that sweep. It is an expression index over
`COALESCE("rtsAt", "shipAcceptedAt", "createdAt")` with `WHERE "trackingNumber" IS NOT NULL` —
Prisma has no syntax for either half, so it is written by hand in the migration and only noted as a
comment on `model Package`. The shipping moment is indexed rather than `createdAt` because a package
row is created when the order syncs and may reach the carrier days later.

Each enrolled row gets its first slot spread randomly across the first stage. A whole batch
defaulting to `now()` would come due on one tick — the burst the rate-limit tiers exist to avoid.

## Cadence

`nextShipmentPollSchedule` derives the interval from `quietPolls` rather than compounding the stored
one, so a parcel that has just reached `OUT_FOR_DELIVERY` does not inherit the slow cadence of the
stage before it.

| Milestone                      | Base interval | Env var                             |
| ------------------------------ | ------------- | ----------------------------------- |
| `PENDING_PICKUP`               | 6 h           | `TRACKING_POLL_PENDING_PICKUP_MS`   |
| `PICKED_UP`, `IN_TRANSIT`      | 3 h           | `TRACKING_POLL_IN_TRANSIT_MS`       |
| `OUT_FOR_DELIVERY`             | 45 min        | `TRACKING_POLL_OUT_FOR_DELIVERY_MS` |
| `DELIVERY_FAILED`, `RETURNING` | 4 h           | `TRACKING_POLL_EXCEPTION_MS`        |

A quiet poll multiplies the base by `1.5 ** quietPolls`, capped at `TRACKING_POLL_MAX_INTERVAL_MS`
(12 h), then jittered ±10 % so a batch shipped together does not come back in lockstep.

`nextPollAt = NULL` means **stop asking**, for one of four reasons: delivered (by the delivery scan,
or by the package status), returned, the package was cancelled, or the parcel has said nothing past
`TRACKING_POLL_ABANDON_AFTER_MS` (14 days). The give-up clock is anchored on `silentSince` — the
later of the newest milestone and the enrolment moment, so a parcel ViShop has never actually polled
is not abandoned on the strength of a scan that predates enrolment.

`stopReason` records **which** of the four it was, written by the same statement that clears
`nextPollAt` and cleared again by `markDueNow`. The shipping screen needs it to tell "the carrier has
gone quiet" apart from "ViShop gave up", which look identical through `nextPollAt` alone.

The read model uses a different rule for the same-sounding question: `carrierSilentSince` reports the
newest scan whenever there is one, and falls back to the enrolment moment only when the carrier has
never scanned. Otherwise a backfilled parcel whose last scan was eleven days ago would appear on the
screen as freshly updated — the opposite of what that screen exists to surface.

### The lease

`claimDueForPolling` pushes `nextPollAt` out by `TRACKING_POLL_LEASE_MS` **inside the claim
statement itself**, so a worker that dies mid-poll frees the row on the next tick instead of holding
it forever. The lease has to outlive the job it hands out, or the next tick re-claims an order still
in flight and spends a second REPORT-tier call on it. `minimumTrackingPollLeaseMs()` derives that
floor from `3 × TIKTOK_HTTP_TIMEOUT_MS + 15 s` (three BullMQ attempts plus the 5 s + 10 s waits
between them); the scheduler and worker env validators refuse to boot below it.

The claim also filters by `shopIds` **before** stamping the lease. A shop the caller cannot poll
must never be claimed at all — otherwise its rows are re-leased every tick and hold batch slots
nothing will ever use.

## The write path

Three writers touch a `shipment_tracking` row, in three different processes:

| Writer                          | Process   | Writes                              |
| ------------------------------- | --------- | ----------------------------------- |
| `EnrollShipmentTrackingHandler` | scheduler | inserts only                        |
| `PollShipmentTrackingHandler`   | worker    | updates the header + appends events |
| `SyncPackageTrackingHandler`    | api       | inserts or updates                  |

Each holds a snapshot read **before** its own platform call, which takes seconds. Three rules keep
that safe, and all are enforced in SQL rather than by convention:

**1. The header only moves forward.** `milestone` / `lastEventAt` / `deliveredAt` are a projection of
the newest milestone, so `save()` guards its update with `lastEventAt < <the value being written>`.
A writer whose snapshot is behind matches zero rows and its stale value is dropped. Without it the
slower writer wins and the row is stuck: `toDomain` rebuilds `milestone` from the column, the next
poll finds no new milestone, so nothing is written back and the regression never heals — a delivered
parcel keeps reading as in transit and keeps polling until the abandon threshold.

**2. An end state is final.** The same update also requires the stored `milestone` not to be
`DELIVERED` or `RETURNED`: a snapshot taken before another writer reached one reads a later scan as
a plain forward move. A header closed by the package status has no newer scan behind it, so the
`lastEventAt` guard would drop it; a second, one-way statement writes `DELIVERED` onto a row not yet
at an end state.

**3. A delivery is never cleared.** The forward guard alone is not enough: a snapshot taken before a
concurrent writer recorded the delivery carries `deliveredAt: null` and still passes the guard on a
later milestone. `deliveredAt` is therefore written by a separate statement guarded on
`deliveredAt IS NULL`.

None of the guards touches `shipment_tracking_events`. That log is append-only and every milestone
lands regardless of who won the header — `shipment_tracking_events_identity_key`
(`trackingId, occurredAt, description`, `NULLS NOT DISTINCT`) plus `createMany({ skipDuplicates })`
makes re-reading the same timeline a no-op. The unique needs `NULLS NOT DISTINCT` because
`description` is nullable and Postgres counts every NULL as different by default; Prisma cannot
express the clause, so the index is hand-written in the migration and `schema.prisma` declares the
plain `@@unique` under the same name to keep `prisma migrate diff` quiet.

### Opening a journey

`packageId` is unique on `shipment_tracking`, so exactly one insert can win. `openTracking` uses
`createMany({ skipDuplicates })` — `ON CONFLICT DO NOTHING` — and reports the outcome as a count.
This is not tidiness: a unique violation **aborts the enclosing Postgres transaction**, and catching
it in JavaScript does not revive it for the statements that follow.

`SyncPackageTrackingHandler` relies on that. It reads, opens and writes inside one transaction so
the read resolves to the primary — outside a transaction it would hit the read replica, where lag
hides a row the enrolment sweep opened moments ago. Under READ COMMITTED the re-read after
`openTracking` returns whichever row won, and the handler adopts it. Without this, an operator
pressing "sync now" while the 5-minute sweep ran got a 409 (or a 500, when the violation surfaced
through the driver adapter as `P2010`, which the exception filter does not map).

## The 1970 sentinel

`lastEventAt` is `NOT NULL`, so a journey no carrier has scanned yet has to store something.
`noMilestoneYet()` (the epoch) is that value, and `hasNoMilestoneYet()` is how the presentation
mapper turns it back into `null` on the way out — the API would otherwise date every freshly
enrolled parcel to 1970 in a field the client renders as a scan time.

The enrolment moment cannot be used instead: `recordEvent` only moves forward, so a scan made before
enrolment — the ordinary case, since the sweep runs every 5 minutes and the carrier scanned whenever
it scanned — would be discarded and the parcel would never leave `PENDING_PICKUP`.

## Known limitation

`markDueNow` (the accelerator) can be overwritten by a poll of the same order that is already in
flight: the poll finishes after the listener and pushes `nextPollAt` back out to its normal
interval. The window is a few seconds and no data is lost — the parcel is simply asked again later
than intended. Fixing it means splitting `saveSchedule` so `lastPolledAt` is written unconditionally
while `nextPollAt` is guarded; not done, deliberately.

## Environment

See `docs/environment.md` for the full list. The defaults are a business estimate, not a
measurement — re-check them against real `ingestLagMs` values once the poll has run in production.

## Debugging

Every poll turn logs one line (`Polled shipment tracking`) carrying `externalOrderId`, `shippingType`,
`journeyScope`, `eventsReturned`, `eventsNew`, `ingestLagMs`, `milestone`, `nextPollAt`, `stopReason`
and the `(actionCode, description, milestone, recognised)` of each new milestone. `ingestLagMs` is
the platform's own ingest delay and bounds how tight an interval can usefully be.

Parcels given up on log a `warn` naming `silentSince`.

## Milestones from action codes

`toShipmentMilestone` reads the milestone off the platform's `action_code` through TikTok's official
table ([docv2/page/37tihrdq](https://partner.tiktokshop.com/docv2/page/37tihrdq), 64 codes). The
description is never read: Get Tracking 202309's own documentation pairs "Your package was
delivered!" with `30901`, which the table calls `pickup_success`. Six codes were measured on sandbox
VN on 2026-09-11 with exactly the names the table gives them — `20101 pkg_shipped`,
`30901 pickup_success`, `31301 sc_inbound`, `40501 delivery_start`, `50101 signed_personally`,
`110101 pkg_cancelled` — so 202309 and 202604 share the table.

| Milestone          | Codes (`action_code_name`)                                                                                                                                                                                                                                                            |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `PENDING_PICKUP`   | 10101 pkg_created, 20101 pkg_shipped, 20201 pickup_start, 20301 pickup_failure, 20801 drop_off_intransit, 20901 ready_for_pickup, 21001 data_send_carrier, 21101 2nd_pickup_failure, 21201 3rd_pickup_failure, 21301 4th_pickup_failure, 21401 pickup_schedule, 21402 pickup_check_in |
| `PICKED_UP`        | 30901 pickup_success, 21901 manifest_scaned, 36201 drop_off_success, 39901 pickup_station_in, 301002 pickup_station_handover                                                                                                                                                          |
| `IN_TRANSIT`       | 31001, 31101, 31201, 31301, 31401, 32001, 32201, 32301, 32401, 32501, 32601, 32701, 32801, 3010001, 3013901, 40101, 40201 station_out, 40301, 41301                                                                                                                                   |
| `OUT_FOR_DELIVERY` | 40501 delivery_start, 41001 delivery_twice, 41101 delivery_thrice, 41201 delivery_multiple, 41401 delivery_exception, 41501 pop_arrived                                                                                                                                               |
| `DELIVERY_FAILED`  | 40601 1st_attempt_failed, 40801 2nd_attempt_failed, 41801 3rd_attempt_failed, 41901 multiple_attempt_failed, 60101 sign_failure                                                                                                                                                       |
| `DELIVERED`        | 50101 signed_personally                                                                                                                                                                                                                                                               |
| `RETURNING`        | 70201, 70301, 70401, 70501, 60201 unreachable_returned_wh, 43101 platform_wh_received, 43201 pkg_wh_received                                                                                                                                                                          |
| `RETURNED`         | 80101 unreachable_returned                                                                                                                                                                                                                                                            |
| no milestone       | 42001 delivery_schedule, 90101 pkg_damaged, 100101 pkg_lost, 110101 pkg_cancelled, 120101 lo_cancelled, 140101 operate_delay, 160101 pkg_scrap, 59801 pkg_received                                                                                                                    |

The codes that fit no stage cleanly, and why each landed where it did:

- **41001 / 41101 / 41201 / 41401 → `OUT_FOR_DELIVERY`, not `DELIVERY_FAILED`.** They announce a
  redelivery or a delay. The failed attempt has its own code (40601/40801/41801/41901/60101), so
  counting both would double `failedDeliveryAttempts` and raise false "delivery failed" issues.
- **Pickup failures (20301, 21101–21301) → `PENDING_PICKUP`.** The parcel has not left the seller;
  `DELIVERY_FAILED` would ask someone to call the buyer about the carrier's pickup.
- **40201 station_out → `IN_TRANSIT`.** It has left the last facility but is not on a delivery run
  yet; that is 40501.
- **41501 pop_arrived → `OUT_FOR_DELIVERY`.** The last leg: the parcel waits for the buyer at a
  pickup point.
- **Back at a return warehouse (60201, 43101, 43201) → `RETURNING`, not `RETURNED`.** The return
  warehouse is not the seller. `RETURNED` is terminal and would stop polling before 80101 arrives.
- **Cancelled, lost, damaged, scrapped (110101, 120101, 100101, 90101, 160101) → no milestone.** The
  platform documents a `CANCELLED` package as "normally … due to the package being lost or damaged",
  and that status already stops polling (`package_cancelled`) and keeps the parcel out of issues.
- **42001, 140101** (rescheduled, overdue) change no stage. **59801** is a UK-only return warehouse.

A code that moves no stage and a code outside the table both keep the previous milestone and are
stored with `recognised = false`, so a count over milestones leaves them out. Only a code outside
the table logs the `warn` (`Carrier milestones the marker table did not recognise` — the text is
kept because alerting greps it): the platform maintains the table and may add codes, and that line
is the only way to learn about one.

**Limit.** 58 of the 64 codes have not been seen on sandbox, which cannot produce failed deliveries,
returns or losses; they follow the official explanations. Verify against a real shop before relying
on the failure and return branches.
