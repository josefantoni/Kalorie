# 0022. A logged food may be pinned to a meal type, overriding the time-of-day rule

- **Status:** Accepted
- **Scope:** Backend, Cross-platform
- **Date:** 2026-09-03

## Context

[ADR 0014](0014-meal-assignment-by-time-of-day-only.md) decided that a food belongs to the meal
window its time of day falls in, and to no other. Nothing on a `foodConsumed` document records a
meal type; the grouping is derived at read time by `DashboardViewModel.groupedFoods` through
`MealKit.isMinuteWithinWindow`. The consequence is that **the only lever that moves an entry
between sections is its `date`** — recorded as finding **A3-2a** in `TODO.md`.

What makes this a decision rather than a defect is the use case behind it. A user logs a whole
day in one sitting in the evening — going back over what they ate on a day they had no time to
log. Every entry carries that evening's timestamp, so every entry lands in the evening's window
or outside every window, however early in the day the food was actually eaten.

The timestamp is not wrong. It records when the entry was written, which is the only thing the
app can honestly know. Only the *section* is wrong. Repairing it by rewriting `date` would trade
a wrong section for a false record of when the entry was made, and — because the day view is
derived from `date` ([ADR 0015](0015-dashboard-caches-a-month-and-derives-the-day.md)) — a
rewrite large enough to reach the intended window can move the entry to a different day.

The two facts therefore want separate storage: *when the entry was written*, and *which meal it
belongs to*.

## Decision

`foodConsumed` documents gain an optional `meal_type_id: String?`, holding a meal type id
([ADR 0021](0021-meal-type-ids-are-uuids.md)):

- **absent / `nil`** — the section is derived from the time of day, exactly as ADR 0014 says.
- **set** — the entry is pinned to that meal type and appears there whatever its time of day.

`date` is never rewritten in order to change a food's section. Grouping resolves pinned entries
first, then the time-of-day windows over what remains, then the unassigned section. A pin naming
a meal type that no longer exists is treated as no pin.

A new entry almost always belongs to whatever meal window it was logged into — the evening
catch-up case above is the exception, not the rule. So `SaveFoodConsumedUseCase` resolves that
window itself at write time (`mealTypes.mealType(at:)`, the earliest-`startTime` window whose
range contains the entry's time of day — the same `[MealTypeDomain].mealType(at:)` resolution
`DashboardViewModel.groupedFoods` uses at read time) and writes its id as `meal_type_id`
immediately, instead of leaving the field absent. An entry logged outside every window is still
written unpinned; there is nothing to resolve it to.

The meal-type picker in `FoodConsumedDetailView` only ever moves the pin to another concrete meal
type — there is no "by time" option to revert to the implicit, time-derived state. Once an entry
has a window to log into, that assignment is explicit from the start; the user corrects it by
picking a different meal type, never by clearing it back to absent.

Documents that predate this field — every `foodConsumed` document written before this decision —
keep `meal_type_id` absent and keep resolving purely by time of day through the fallback above.
This is not migrated.

## Consequences

- **This supersedes ADR 0014's "alone, never by anything else", not its rule.** Time of day
  remains how every entry is assigned unless the user has said otherwise, and ADR 0014's standing
  prohibition — that the date part of `MealTypeDomain.startTime`/`.endTime` must never be
  compared against anything — is untouched, because a pin compares ids, not dates.
- **This narrows the original A3-2 finding; it does not close it.** An entry's `date` still
  cannot be edited, so an entry logged after midnight for the previous day lands on the wrong
  *day*, and no pin can move it — a pin governs the section within a day, never which day the
  entry belongs to. `TODO.md` split the finding accordingly: **A3-2a** is what this record
  decides. **A3-2b** — the day itself — was decided against separately: editing an entry's
  date/time would add a second axis of state to reconcile against the month cache
  ([ADR 0015](0015-dashboard-caches-a-month-and-derives-the-day.md)) for a rare edge case, more
  complexity than the app's day-boundary case warrants. It is an accepted limitation, not tracked
  as an open finding.
- Finding **A3-3** (an entry logged at minute 0, outside every default window) stops being
  permanent: the user can pin it into a meal, even though its cause — `MonthCalendarView.selectDay`
  discarding the time of day — is untouched.
- **Every writer must carry the field through.** `FirestoreDataProviderProtocol` has no partial
  update: `setAsync` rewrites the whole document from a DTO built out of the domain model. A
  writer that does not round-trip `meal_type_id` through `DTO → asDomain() → DTO` silently unpins
  the entry — saving a new weight would undo the user's move. This is the single easiest way to
  implement this decision wrongly.
- **The read-time fallback stays load-bearing.** `DashboardViewModel.groupedFoods` resolves an
  unpinned (or unknown-id-pinned) entry's section via `[MealTypeDomain].mealType(at:)` — the
  successor to what earlier records call `foodFallsIn`, now shared with the write-time resolution
  above instead of duplicated. It is what every pre-existing document, and every new entry logged
  outside every window, continues to rely on to be assigned at all. This resolution must not be
  removed or "simplified away" on the assumption that every entry is pinned now — it is not, in
  either of those cases.
- `AssignFoodMealTypeUseCaseProtocol.callAsFunction(_:mealTypeId:)` takes a non-optional `String`:
  the picker can only ever move a pin to a concrete meal type, never clear it. The optionality
  that matters — "no pin yet" — lives entirely in `FoodConsumedDomain.mealTypeId` and in whether
  `SaveFoodConsumedUseCase` found a window to resolve at write time, not in this use case's write
  path.
- **A section no longer implies a time range.** A pinned entry sorts inside its section by the
  order the month query returns, which is by `date`, so an entry logged at 22:00 and pinned to
  breakfast appears after the entries logged that morning. Accepted deliberately; no separate
  within-section ordering is introduced to hide it.
- Daily totals are unaffected — `dailyMacros` sums `foodsConsumed` directly — while per-section
  totals move with the pin, which is the point of the feature.
- Reordering meal types swaps time windows between names, not names between windows
  (`ARCHITECTURE.md` § 3.4). A pinned entry therefore follows the meal type's **identity**, not
  the time slot it occupied when the pin was made.
- A second client **MUST** implement the same three-step read-time resolution, the same
  unknown-id fallback, and the same write-time window resolution (earliest-`startTime`-first,
  overlapping-window rule). Two clients that disagree here show different sections for identical
  data, or pin identical entries to different meal types at the moment of logging.
- No migration, no index, no rules change: the field is optional and decodes to `nil` on every
  pre-existing document, the month query still ranges over `date`, and the `users/{userId}`
  subtree is already writable by its owner.
