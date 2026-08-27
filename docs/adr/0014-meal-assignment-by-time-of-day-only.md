# 0014. A food is assigned to a meal by time of day alone, never by calendar date

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-08-27

## Context

A meal type is a recurring window — "lunch is 11:00–14:00" — not an event on a particular day.
Firestore stores it that way: `MealTypeDTO` holds `startMinutes` and `endMinutes`, integers
counting minutes since midnight, with no date component at all.

The Swift domain type is the awkward part. `MealTypeDomain` holds `startTime: Date` and
`endTime: Date`, because SwiftUI's `DatePicker` needs `Date` values to bind to.
`FetchMealTypesUseCase` therefore materialises those minutes onto an arbitrary anchor day —
*today* — with `Calendar.date(bySettingHour:minute:second:of: Date.now)`. The anchor is
meaningless; only the time-of-day survives.

That makes it easy to misread the model. A reader who sees two `Date`s on both sides is one
step away from writing `food.date >= mealType.startTime && food.date < mealType.endTime`, which
compiles, passes a test written on today's data, and silently classifies nothing at all as soon
as the user looks at yesterday.

## Decision

Meal assignment compares **minutes since midnight only**. `DashboardViewModel.foodFallsIn`
converts both sides with `Date.minutesSinceMidnight` and delegates to
`MealKit.isMinuteWithinWindow`. The `Date` values on `MealTypeDomain` are a UI-binding
convenience and carry no meaning beyond their time-of-day.

The same rule governs the two validation predicates, `mealWindowsOverlap` and
`isMealWindowLongEnough` — all three live in `MealKit` so that a second client cannot drift.

## Consequences

- The day view works identically for today, last week and next month, and needs no per-day
  re-derivation of meal windows.
- A window may wrap past midnight (`endMinutes <= startMinutes`). `MealKit.dayRanges` splits
  such a window into two same-day ranges, so callers never handle wraparound themselves. Any
  new predicate over meal windows belongs in `MealKit` for the same reason.
- **The date part of `MealTypeDomain.startTime` / `.endTime` must never be compared against
  anything.** It is whatever day the meal types were last fetched on. Code that needs a real
  date has to build one from the food's own `date`.
- Because assignment ignores the calendar date, a food logged at 00:00 is assigned by minute
  `0` — which the default meal layout (05:00–20:00) does not cover. That is not a flaw in this
  decision, but it is what makes finding **A3-3** in `TODO.md` visible.
- `FetchMealTypesUseCase` anchoring to `Date.now` also means the materialisation can fail on a
  daylight-saving transition, silently dropping the meal type — finding **A1-6**. Storing
  minutes and comparing minutes is right; round-tripping them through `Date` is the part that
  is fragile.
