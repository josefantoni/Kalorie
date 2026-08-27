# 0015. The Dashboard fetches a whole month and derives every day view from it

- **Status:** Accepted
- **Scope:** iOS
- **Date:** 2026-08-27

## Context

The Dashboard has to answer three questions at once: what did the user eat on the selected day,
which days this month have any entries at all (the dots in the month calendar and the day
picker), and what happens when they step to the neighbouring day. Answering them with per-day
queries means one Firestore query per day switch plus thirty more to draw the calendar dots.

`FetchFoodsConsumedForMonthUseCase` exists for this: one range query over `date` covering
`[startOfMonth, startOfNextMonth)`. `DashboardViewModel` keeps the result in `monthCache`, a
dictionary keyed by day (`yyyy-MM-dd`), plus `cachedMonthKeys` recording which months have been
loaded. `FetchFoodsConsumedUseCase` — the single-day equivalent — still exists but the
Dashboard does not use it.

Firestore bills per document read, not per query, so fetching a month costs the same as
fetching its days one at a time — but only once, and only in one round trip.

## Decision

The Dashboard loads a month at a time and serves the day view, the activity dots and day
switching from `monthCache`. A month is fetched when it is first needed and re-fetched only
when explicitly invalidated.

Invalidation is by whole month, in three places: pull-to-refresh (`onRefresh`), returning from
the food detail screen (`onFoodConsumedUpdated`), and `scenePhase` becoming `.active`. All
three invalidate the month of `selectedDay`.

## Consequences

- Stepping between days inside a loaded month is instant and costs nothing. Opening the month
  calendar and paging through months costs one query per month, once.
- **The cache is authoritative between invalidations.** Anything that writes a `foodConsumed`
  document must route back through one of the three invalidation paths, or the Dashboard will
  keep showing the pre-write month. Adding a fourth write path without a matching invalidation
  is the failure mode to watch for.
- Invalidation is scoped to `selectedDay`'s month, so a write landing in a *different* month
  leaves that month's cache stale until the app is relaunched. Not currently reachable — every
  write uses `selectedDay` as its date — but it is a one-line change away from being reachable.
- `monthCache` is never evicted beyond the month being repopulated, so it grows for the life of
  the process — finding **A3-6** in `TODO.md`.
- Cache keys are produced by `Date.formatDateStyle`, which builds an unconfigured
  `DateFormatter` and therefore inherits `Locale.current`. That is fine for lookup, which is
  self-consistent, but not for `computeActiveDays`, which parses the day number back out of the
  key — finding **A3-5**.
- The month query is unbounded in result size. A month of entries is small enough that this has
  never mattered; the same is not true of the unfiltered whole-collection reads elsewhere
  (finding **A1-3**).
