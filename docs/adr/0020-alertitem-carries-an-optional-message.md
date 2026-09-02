# 0020. `AlertItem` carries an optional message alongside its title

- **Status:** Accepted
- **Scope:** iOS
- **Date:** 2026-09-02

## Context

[ADR 0018](0018-per-feature-error-alerts-with-no-global-handler.md) made `AlertItem`
deliberately minimal — an `id` and a `title` — and listed the cost as a Consequence: an error
that needs the user to *do* something, such as `errorDeleteRequiresRecentLogin`, cannot say so
in a single line (finding **A5-4** in `TODO.md`).

`AlertItem` gained an optional `message: String?` field to close that gap, but the first use of
it — fixing **A5-5**, distinguishing an offline failure from every other unknown error — reached
for `title` instead (`L10n.Common.errorOffline` vs. `errorUnknown`), and left `message` wired
through every `.alert(item:)` call site with no producer anywhere. A code review of the commit
that shipped the field caught the gap (finding **A5-9**); the follow-up closed it:
`DashboardViewModel.unknownErrorAlertItem(for:)` now supplies both a title and a message for its
offline/unknown branches.

## Decision

`AlertItem` keeps `title: String` and adds `message: String? = nil`, defaulting to `nil` so
every existing call site stays source-compatible. A view renders it as
`Alert(title: Text(item.title), message: item.message.map(Text.init), dismissButton: .default(Text(L10n.Common.ok)))`.
Producing a message is opt-in per call site — most stay title-only; `DashboardViewModel`'s
offline/unknown alerts are the first, and as of this record the only, producer.

This does not reopen ADR 0018's core decision: alerts are still dismiss-only, per-feature, and
there is still no global handler or shared retry affordance. It supersedes only that ADR's
Consequences bullet claiming `AlertItem` "carries no message and no actions" — the message half
of that sentence is no longer true.

## Consequences

- An error alert can explain what happened (`title`) and what to do about it (`message`)
  without inventing a new title per situation.
- Nothing forces a call site to produce a message; most `AlertItem(title:)` calls stay
  single-line by default. This is not a route for action buttons into `AlertItem` — that stays
  out of scope per ADR 0018; the re-login prompt uses `.alert(_:isPresented:)` for that reason,
  not `AlertItem` (finding **A5-7**).
- Wiring a message for one situation does not imply every similar one gets one —
  `FoodQuantityViewModel`, `AddFoodSheetViewModel`, `MyCreatedMealListViewModel` and
  `FoodConsumedDetailViewModel` still show `L10n.Common.errorUnknown` as a title-only alert.
  Whether they should get a message is a decision for whoever next touches those screens, not
  implied by this record.
