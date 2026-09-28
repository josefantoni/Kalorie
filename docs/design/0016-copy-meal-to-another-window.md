# Design: Copying a meal to another window and day

- **Status:** Implemented in `61d142c`, `2a70f12`, `9f3ff79` (iOS) and `9f405ef` (Android)
- **Scope:** Cross-platform, iOS, Android
- **Date:** 2026-09-28

## Context and scope

People who follow a meal plan eat the same thing on most days, and log it again every morning:
yesterday's breakfast, re-entered food by food, then yesterday's lunch. The app has no way to
repeat what is already logged.

Relevant existing pieces:

- A `foodConsumed` entry is a self-contained snapshot. Its nutrition values are absolute and
  already scaled to the logged weight ([ADR 0009](../adr/0009-denormalised-nutrition-snapshots.md),
  `ARCHITECTURE.md` § 1.4), so an entry can be duplicated without the catalogue.
- Which Dashboard section an entry lands in depends on its time of day
  ([ADR 0014](../adr/0014-meal-assignment-by-time-of-day-only.md)), unless `meal_type_id` pins it
  ([ADR 0022](../adr/0022-meal-assignment-may-be-pinned-by-the-user.md)). The resolution lives in
  MealKit ([ADR 0039](../adr/0039-swift-only-rules-move-into-kmp-or-share-golden-vectors.md)).
- The Dashboard caches whole months and builds each day from that cache
  ([ADR 0015](../adr/0015-dashboard-caches-a-month-and-derives-the-day.md)).
- Every Dashboard section header has an `info.circle` button. iOS opens it as a popover
  (`DashboardView.swift`, `.presentationCompactAdaptation(.popover)`); Android opens it as a
  `DropdownMenu` anchored to the icon (`DashboardView.kt`).
- `FirestoreDataProviderProtocol.batchSetAsync` already exists on both clients. It commits in
  chunks of `Constants.Firestore.batchWriteLimit` (500).
- `SaveToolbarButton` exists on both clients: a button that swaps to a green checkmark while a
  view-model flag is set (`ARCHITECTURE.md` § 4.5).

**Boundary of this document:** copying every entry of one Dashboard section into one chosen meal
window on one chosen day, on the client. No backend, rules or schema change.

## Goals

- Every section header, *Unassigned* included, gets a copy button next to `info.circle`.
- Tapping it opens a small box anchored to the button, the same kind of presentation as the info
  box: *"Food from [meal name] will be copied to"*, a day picker, a meal window picker, Cancel and
  Copy.
- The box opens on **today** and **the window the current time falls in**. That default is chosen
  for the most common case: copying yesterday's breakfast in the morning.
- Copy is disabled when the target is the source: the same day as the one on display, and the
  same meal type as the section.
- Once the copy succeeds, the Copy button turns into a checkmark with the existing animation.
  About one second later, the box closes itself. The Dashboard stays on the day the user was on,
  even if that is two weeks ago.
- Copied entries show up in the target section with the source's values and in the source's order.

## Non-goals

- **Undo, and deleting a whole section.** A mistaken copy is removed one swipe at a time
  (`ARCHITECTURE.md` § 3.7). That was accepted explicitly: ten copied entries mean ten swipes.
- **Future days.** The day picker only allows today and earlier.
- **Copying single entries, or a whole day.** The unit is one section.
- **Re-reading the catalogue.** A copy reproduces the snapshot as it is and does not pick up
  catalogue changes made since. This is the same behaviour as every other snapshot (ADR 0009).
- **Offline behaviour.** The app is online-only.

## Design

### What a copy is (Cross-platform)

For each source entry, a new `foodConsumed` document is written. Every field is taken from the
source unchanged except:

| Field | Value |
|---|---|
| `id` | new UUID |
| `date` | the target timestamp (below) plus *i* seconds, where *i* is the entry's index in the source section |
| `meal_type_id` | the target meal type's id, **always** |

`measure_unit` and `food_item_kind` must be carried across. If `measure_unit` is dropped, a
millilitre entry is silently relabelled as grams (§ 1.4). If `food_item_kind` is dropped, the
whole day fails to fetch (ADR 0025).

The pin is not optional. Without it, a copy of breakfast into lunch would be placed by its time of
day, and that time is not guaranteed to fall inside the target window.

The *+i seconds* keeps the source order: sections are ordered by `date` (§ 3.2), and entries with
identical timestamps have no defined order.

### The target timestamp (MealKit)

- **If the target day is today and the current time falls inside the target window:** the time
  of day is *now*.
- **Otherwise:** the time of day is the target window's `startMinutes`.

This is what logging a new food does today (§ 3.5: a today-entry gets *now*). A copy made in the
morning into today's dinner therefore gets 17:00, a time that has not happened yet. That is
accepted, because the pin already places it correctly.

The rule takes only primitives, so ADR 0039 rule 1 puts it into MealKit, next to `mealWindowAt`:

```kotlin
fun copyTargetMinutes(nowMinutes: Int?, targetId: String, windows: List<MealWindow>): Int?
```

Kotlin/Native exports every function whose name starts with `copy` under a `doCopy…` name (the
Obj-C *copy* method family has its own ownership rules), so iOS calls it as
`MealWindowsKt.doCopyTargetMinutes`. The Kotlin name stays, and Android uses it as written.

`nowMinutes` is `null` when the target day is not today. The function returns `null` when
`targetId` is not among `windows`. The window check uses `isMinuteWithinWindow` against the target
window itself, not `mealWindowAt`, so the rule does not depend on the overlap tie-break. It is
covered in `commonTest`.

Each client turns the target day and the returned minutes into a timestamp through its calendar
(`Calendar` / `java.time`), built from components rather than by adding seconds, so that a DST
transition day does not shift it. Both are thin adapters, the same as `mealType(at:)`.

### Use case (both clients)

`CopyFoodsConsumedUseCase(foods, toDay, mealTypeId, mealTypes)`:

1. Resolves the target timestamp.
2. Maps each food to a DTO, as in the table above.
3. Writes everything with one `batchSetAsync` into `Constants.Firestore.foodConsumed(userId:)`.

A section never comes near 500 entries, so the write is a single atomic batch: either every entry
is copied, or none is. The use case is tested at the use-case layer, like every other.

### Dashboard (both clients)

- **Header.** A copy icon (`doc.on.doc` on iOS, a `CopyIcon` vector on Android — the project
  ships `material-icons-core` only, and `ContentCopy` is in the extended set) goes in the section
  header, before `info.circle`. It has the same styling as the info button and opens the same kind
  of anchored box, with the open state kept per section index.
- **Box content.** The sentence with the source meal's name (or *Unassigned*), a day picker
  limited to today and earlier, a meal type picker over the current `mealTypes`, Cancel and a
  filled *Copy* button in the accent colour, like the Add button. On success the button turns
  green and shows a checkmark with the same spring animation as `SaveToolbarButton`.
  `SaveToolbarButton` itself is not reused: its checkmark is green on a transparent button and
  would disappear on a green fill.
- **Size.** The box does not force a width. iOS gives it `minWidth 260 / idealWidth 300 /
  maxWidth 340`, because a fixed width wider than the space beside the icon is clipped when the
  popover opens sideways; Android uses 300 dp. The sentence wraps onto as many lines as it needs.
- **Defaults.** Day: today. Window: `mealTypes.mealType(at: now)`. If there is none, the source
  section's meal type. If that is `nil` too (source is *Unassigned*), the first meal type.
- **State.** The open box's section index (`copyPopoverIndex`), the target day, the target meal
  type id, `isCopying` and `showCopyCheckmark` live in `DashboardViewModel`, so that the delayed
  close is part of the tested flow. Copy is enabled when all of these hold:
  - not `isCopying`,
  - not `showCopyCheckmark`,
  - the target is not the source (same calendar day as `selectedDay` **and** same meal type id).
    For *Unassigned* the source id is `nil`, so any target counts as different.
- **Success.**
  1. Set `showCopyCheckmark`.
  2. Invalidate the target day's month in `monthCache`. If that month is the one on display,
     reload it so `activeDaysInMonth` gains the target day. Do not move `selectedDay`.
  3. After ~1 s, close the box and clear the flag. On Android this runs in a `finally`, because
     the calling scope can be cancelled during the delay (rotation) and the flags would otherwise
     stay set, leaving Copy disabled.
- **Failure.** The box stays open with no checkmark, and the error surfaces the same way the
  delete path does (`alertItem` on iOS). Because of the batch, nothing is half-written.
- **Double submission.** While `isCopying` or `showCopyCheckmark` is set, the Copy button is
  disabled. The iOS popover sets `.interactiveDismissDisabled`; the Android menu ignores an
  outside tap in that state.
- **Localization.** New strings go into `L10n.Dashboard` on iOS and into the Android string
  resources, in Czech and English.

### The day picker inside the box

- **iOS:** a `.compact` `DatePicker` with `in: ...Date.now`. It is used as it comes: an accent
  coloured replacement drawn over it, and a calendar expanded inline in the popover, were both
  tried and dropped (the first blocked the tap, the second needed a taller popover than a small
  screen has).
- **Android:** a button showing the day that opens a Material 3 `DatePickerDialog`, with
  `selectableDates` limited to today and earlier. The menu ignores an outside tap while the dialog
  is open.

On both platforms the calendar opens as a second layer on top of the anchored box (see *Risks*).

The meal type picker is a menu on iOS. On Android it is a list of radio rows inside the box, so no
menu opens from inside a menu.

## Alternatives considered

- **System alert.** A SwiftUI `.alert` can only hold text, buttons and text fields, never a
  picker. A `UIAlertController` with pickers inside needs private layout hacks that break between
  iOS versions. Rejected on iOS. On Android an `AlertDialog` could do it, but it would not match
  the info box right next to the button.
- **Sheet.** The safest option on iOS, since the pickers have room. Rejected because it is a
  different visual language from the info button beside it, and the user wanted the two to
  behave alike.
- **Custom overlay card.** Dimming, dismissal, accessibility and animation would all be
  hand-written for one call site.
- **Navigating to the target day after the copy.** Rejected: a user who scrolled back two weeks
  to find the meal would lose their place.
- **Allowing source = target (duplicating in place).** Rejected: it is far more likely to be an
  accidental tap than an intent, and the result can only be undone one swipe at a time.
- **Keeping the source's time of day, without a pin.** The copy would land in whichever window
  contains that time, not the one picked.
- **Keeping the source's time of day, with a pin.** Workable, but a lunch copied into breakfast
  would carry 12:30. That is inconsistent with how every other entry gets its time.

## Cross-cutting concerns

- **Security rules.** `users/{userId}/{document=**}` is owner-only and validates no schema.
  Nothing changes, and no rules test is added.
- **Account migration.** Copied entries are ordinary `foodConsumed` documents, so
  `MigrateAnonymousDataUseCase` and account deletion cover them without change.
- **Export (design 0014).** A copy is indistinguishable from a hand-logged entry, as intended.
- **Existing data.** No migration.
- **CI.** The MealKit change triggers both client builds, which is correct for a KMP change.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| The calendar layer over the anchored box misbehaves: on iOS a compact `DatePicker` inside a compact-adapted popover, on Android a `DatePickerDialog` opened from a `DropdownMenu` that dismisses on an outside tap. | The day cannot be picked, or the box closes mid-pick. | Check it on a device first, on both platforms. If it misbehaves, replace the calendar on that platform with a menu of days (*Today, Yesterday, …* back 14 days). That needs no second layer and covers the use case. **iOS:** checked on a device, the native compact `DatePicker` works and the fallback is not needed. **Android:** the menu's outside-tap dismissal is guarded while the dialog is open; not yet checked on a device. |
| A copy lands in a month other than the one on display (30 Sep → 1 Oct). | The target month would be served stale from cache. | Invalidate by the target day's month key, not by `selectedDay`'s. |
| The meal types change between opening the box and tapping Copy (a second device). | The pin names a meal type that no longer exists, and the entry falls back to its time of day. | Accepted. `resolvedMealWindowId` already handles an unresolvable pin, and it only happens with concurrent edits on two devices. |

## Outcome

Implemented on iOS and Android as designed, with these differences:

- The Copy button is a dedicated filled button, not `SaveToolbarButton` (see *Box content*).
- The box's open index lives in `DashboardViewModel`, not in view state.
- iOS reaches the MealKit rule as `doCopyTargetMinutes`; Kotlin/Native renames `copy…` functions.
- The iOS box has a flexible width instead of a fixed one, and uses the native compact
  `DatePicker`. Two custom variants were tried and dropped.
- Android draws the copy icon itself (`CopyIcon`) and lists the meal types as radio rows.
- `FoodConsumedDTO` gained optional `id` and `date` overrides on its copy initialiser, on both
  clients.
- The Android use case has no counterpart to iOS's `invalidTargetDate` error: `java.time` builds
  a timestamp from a local date and time without a failure case.

Shipped in `61d142c` (MealKit rule), `2a70f12` (strings), `9f3ff79` (iOS) and `9f405ef` (Android).
