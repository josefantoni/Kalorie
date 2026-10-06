# 0041. The Dashboard reloads only the affected day after a write or on returning to the foreground

- **Status:** Accepted
- **Scope:** iOS, Android
- **Date:** 2026-10-06

## Context

[ADR 0015](0015-dashboard-caches-a-month-and-derives-the-day.md) loads `foodConsumed` a month at a
time into `monthCache` and invalidates **the whole month** of `selectedDay` on pull-to-refresh,
on returning from the food detail screen, and on `scenePhase` becoming `.active`. Since then the
code has added three more triggers that do the same: saving from the add-food sheet
(`onFoodConsumedUpdated`, DashboardView `:173`), deleting an entry (`onDeleteConfirmed`), and
copying a meal ([design 0016](../design/0016-copy-meal-to-another-window.md), `reloadAfterCopy`).
Android mirrors all of them, with `ON_RESUME` in place of `scenePhase` (`DashboardView.kt:141-142`).

ADR 0015 justifies a month query with *"fetching a month costs the same as fetching its days one
at a time — but only once"*. The invalidation rule in the same record breaks the "only once"
part. Every write and every return to the foreground re-reads the whole month, and Firestore
bills every document again. On iOS, `.active` also fires after the user pulls down Control Centre
or the notification shade.

At about 8 entries a day, a mid-month reload is around 120 reads. With 8 logs and 5 foreground
returns a day, that is roughly 1,500 reads per user per day. This is about 70 % of the app's total
reads, and it would exhaust the Firestore free tier's 50k daily reads at about 20–30 daily active
users.

Every one of those triggers changes, or may have changed, **one day**. A write lands on
`selectedDay` (ARCHITECTURE § 3.5). A copy lands on `copyTargetDay`. Editing an entry in the
detail screen cannot move it to another day (`UpdateFoodConsumedUseCase` keeps `food.date`). The
only thing a foreground return can bring in is a change from another device, and the day on
screen is the one that matters.

## Decision

1. **Month loads stay as in ADR 0015** for cold launch (`onAppear`), stepping or picking a day in
   a month that is not cached yet, paging the calendar, and **pull-to-refresh**. Pull-to-refresh
   is the user's explicit "reload everything" action and stays a full month reload.
2. **Every other trigger reloads one day** with `FetchFoodsConsumedInRangeUseCase`
   (`[startOfDay, startOfNextDay)`) and splices the result into `monthCache` under that day's key:
   - after a save from the add-food sheet, and after returning from the detail screen
     (`onFoodConsumedUpdated`) → `selectedDay`;
   - after a delete (`onDeleteConfirmed`) → `selectedDay`;
   - after a copy (`reloadAfterCopy`) → `copyTargetDay`, **only if its month is already cached**.
     An uncached month is loaded whole when the user first navigates to it, as today;
   - on returning to the foreground (iOS `scenePhase == .active`, Android `ON_RESUME`) and at the
     calendar-day rollover (iOS `NSCalendarDayChanged`, Android `DayChangeEffect`) →
     `selectedDay`, after `advanceSelectedDayIfNeeded`.
3. **When the target day's month is not cached** (e.g. the rollover from the 31st to the 1st),
   the day reload falls back to loading the whole month, because there is no month cache to
   splice into.

## Consequences

- A reload after a write or a foreground return costs as many reads as the day has entries
  (about 8) instead of the month (up to about 250). Across the whole app this is roughly a
  two-thirds cut in reads.
- **Changes made on another device to a day other than `selectedDay` are not picked up by a
  foreground return.** They appear after pull-to-refresh or a cold launch. Until then the
  calendar's activity dots and the other days of a cached month can be stale. ADR 0015's "the
  cache is authoritative between invalidations" still holds. This narrows what a foreground
  return invalidates.
- ADR 0015's warning carries over unchanged: any new write path must route back through a reload
  of the day it wrote, or the Dashboard keeps the pre-write state.
- `refreshMealTypes` still runs on every foreground return (about 5 reads). Out of scope here.
- This supersedes ADR 0015's invalidation paragraph only. The month cache, `cachedMonthKeys` and
  the month query itself are unchanged.

## Implementation handoff

iOS and Android are independent and land as separate commits (`[REFACTOR]`, one per platform).
There is no backend change.

### Shared shape (both platforms)

Add one private method to `DashboardViewModel` and route the triggers through it:

```
reloadDay(date):
    if monthCacheKey(date) not in cachedMonthKeys:
        loadMonth(date)                        // Decision 3
        return
    foods = fetchFoodsConsumedInRange(from: startOfDay(date), to: startOfDay(date))   // `to` is an inclusive day
    monthCache[dayCacheKey(date)] = foods       // replace, never append
    activeDaysInMonth = computeActiveDays(for: selectedDay)
```

- **Day bounds:** compute them in the device time zone, the same way
  `FetchFoodsConsumedForMonthUseCase` computes month bounds. On iOS that is `Calendar.current`,
  using `startOfDay(for:)` and `date(byAdding: .day, value: 1, to:)`, never `+ 86400`, because a
  DST day is 23 or 25 hours long. On Android, mirror the month use case's zone handling. The range
  is half-open (`isLessThan`), matching the month query.
- **Replace the day's key**; do not merge into it. An entry deleted on another device must
  disappear. An empty result stores `[]`, and `computeActiveDays` already skips empty days.
- `computeActiveDays` is computed for `selectedDay`'s month, as everywhere else. A copy into a
  different cached month updates that month's cache. Its dots are recomputed when the user pages
  to it, because `onCalendarMonthChanged` recomputes on a cache hit.
- Split the shared refresh entry point in two:
  - `onRefresh()` stays pull-to-refresh only: `advanceSelectedDayIfNeeded`, `refreshMealTypes`,
    `invalidateCache`, `loadMonth`. This is unchanged behaviour.
  - New `onForeground()`: the same `hasCompletedInitialLoad` guard as `onRefresh` (which prevents
    a double fetch on launch), `advanceSelectedDayIfNeeded`, `refreshMealTypes`,
    `reloadDay(selectedDay)`, `foodsConsumed = foodsFromCache(selectedDay)`, and
    `showSignInSpotlightIfNeeded()`, as `onRefresh` does today.
- Inject `FetchFoodsConsumedInRangeUseCaseProtocol` into `DashboardViewModel`. The use case and
  its fake already exist, because export uses them.

### iOS

- `Features/Dashboard/DashboardViewModel.swift`:
  - `onFoodConsumedUpdated` (`:217`) and `onDeleteConfirmed` (`:234`): replace
    `invalidateCache` + `loadMonth` with `reloadDay(selectedDay)`.
  - `reloadAfterCopy` (`:369`): replace it with "if the month of `copyTargetDay` is cached →
    `reloadDay(copyTargetDay)`; then, if `copyTargetDay` is the same day as `selectedDay`,
    refresh `foodsConsumed`". **Drop the `invalidateCache(for: copyTargetDay)`.** It used to throw
    away an entire cached month to force a lazy reload, and that is exactly the cost this ADR
    removes.
  - `observeDayChange` (`:399-405`): call `onForeground()` instead of `onRefresh()`.
  - Add `onForeground()` and `reloadDay(_:)` next to `loadMonth` (`:427`).
  - `invalidateCache` (`:473`) keeps exactly one caller, `onRefresh`.
- `Features/Dashboard/DashboardView.swift:203-207`: in the `scenePhase == .active` handler, call
  `onForeground()`. `.refreshable` (`:103`) stays `onRefresh()`.
- `Features/Dashboard/DashboardConfigurator.swift:18-20`: pass
  `FetchFoodsConsumedInRangeUseCase(dataProvider:authProvider:)`. Pass the fake in the preview at
  `DashboardView.swift:285` and in `KalorieTests/DashboardViewModelTests.swift:576`.

### Android

- `features/dashboard/DashboardViewModel.kt`: apply the same changes to `onFoodConsumedUpdated`
  (`:195`), `onDeleteConfirmed` (`:208`) and `reloadAfterCopy` (`:320`). Add `onForeground()` and
  `reloadDay()` next to `loadMonth` (`:355`). `invalidateCache` (`:390`) keeps only `onRefresh`
  (`:170`) as a caller.
- `features/dashboard/DashboardView.kt:141-142`: `ON_RESUME` and `DayChangeEffect` call
  `onForeground()`. `PullToRefreshBox` (`:206-210`) stays `onRefresh()`.
- `features/dashboard/DashboardConfigurator.kt:43`: inject `FetchFoodsConsumedInRangeUseCase`.
  The fake is in `app/src/debug/.../FetchFoodsConsumedInRangeUseCaseFake.kt`. Wire it into
  `DashboardViewModelTest.kt:739`.

### Tests (both platforms)

Each test must fail if the behaviour regresses to month reloads. Have the month fake and the range
fake count their calls and record their arguments.

- After `onFoodConsumedUpdated`, the range fake was called once with exactly
  `[startOfDay(selectedDay), startOfNextDay(selectedDay))`, the month fake was not called again,
  and `foodsConsumed` equals what the range fake returned.
- After `onDeleteConfirmed`, the same.
- `onForeground` after the initial load does a range call and no month call. Before the initial
  load it does nothing.
- `onRefresh` (pull-to-refresh) still makes a month call. This pins Decision 1.
- A day reload **replaces** the day: seed the month with two entries on `selectedDay` and have the
  range fake return one. Exactly one entry remains.
- A day reload returning `[]` removes that day from `activeDaysInMonth`.
- A copy into another day of the cached month makes a range call for `copyTargetDay` and no month
  call. A copy into an uncached month makes no fetch at all.
- A rollover into an uncached month (selected day moves from the 31st to the 1st) makes a month
  call. This pins Decision 3.
- Existing tests that assert a month reload after save, delete, copy or foreground must be
  **updated to the new expectation, not deleted**. Grep both test files for the month fake's call
  count.

### Docs (`[DOCS]`, after both clients)

- ARCHITECTURE § 3.6 *Refresh triggers*: split the `scenePhase → .active` row from
  pull-to-refresh. Add the rollover row, the save / delete / copy rows, and a link to this ADR.
- ADR 0015 Status line: `Superseded by ADR 0041 (the invalidation paragraph only — the month
  cache and month query are unchanged)`. Update the matching row in `docs/README.md`.

### Manual verification

1. Log a food, then open the Firebase console's usage tab, or watch the `#if DEBUG` provider log
   (`FirestoreDataProvider.swift`, `log(_:)`). The reload returns only the day's documents.
2. Log on device A, then bring device B to the foreground on the same day. The new entry appears.
3. Log on device A for **yesterday**, then bring device B (showing today) to the foreground.
   Yesterday is not refreshed until pull-to-refresh. This is the accepted consequence; confirm it
   is the only staleness.
4. Copy a meal to another day in the same month. The target day shows the copy, and its calendar
   dot appears.
5. Leave the app open across midnight on the last day of a month. The new day and month load
   correctly.
