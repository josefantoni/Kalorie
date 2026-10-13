# Design: Moving a logged entry to another meal by dragging it

- **Status:** Implemented in `8af132f3` (Android), `36d4903b` (iOS)
- **Scope:** Cross-platform, iOS, Android
- **Date:** 2026-10-13

## Context and scope

A user who gets up late logs breakfast at 9:30, which the default windows put into *second
breakfast* (08:30–11:00, `ARCHITECTURE.md` § 3.4). Moving it today means opening the entry's
detail, picking the meal type and saving — three screens for one correction made every morning.

Relevant existing pieces:

- An entry can already be pinned to a meal type regardless of its time of day — `meal_type_id`,
  [ADR 0022](../adr/0022-meal-assignment-may-be-pinned-by-the-user.md). Pinning never touches
  `date`.
- `AssignFoodMealTypeUseCase` writes that pin, on both clients
  (`iOS/Kalorie/Core/UseCases/AssignFoodMealTypeUseCase.swift`,
  `Android/app/src/main/java/antoni/kalorie/core/usecases/AssignFoodMealTypeUseCase.kt`). Today
  only `FoodConsumedDetailViewModel` uses it.
- `groupedFoods` emits a section only for a meal type that has at least one entry resolved to it,
  plus a trailing *Unassigned* section (`ARCHITECTURE.md` § 3.2). An emptied section therefore
  disappears on its own and comes back when something resolves into it again.
- There is no "by time" option: a pin can be moved to another meal type, never cleared (§ 3.2).
- Within a section entries are ordered by `date`, not by position
  ([ADR 0022](../adr/0022-meal-assignment-may-be-pinned-by-the-user.md) Consequences).

**Boundary of this document:** moving one entry, within the selected day, into another section
that is currently on screen, on the client. No backend, rules or schema change.

## Goals

- Long-press a food row on the Dashboard, drag it onto another meal section, release → the entry
  is pinned to that meal type. No mode switch, no edit button.
- The row moves immediately (optimistic); a failed write puts it back and shows an alert.
- Haptic feedback on both platforms: when the row lifts and when a move is committed.
- If the source section became empty, it disappears — no extra code, `groupedFoods` already does
  this.
- While dragging, the list scrolls when the finger nears its top or bottom edge, so a section off
  screen can be reached in one drag — on both platforms. Android rows are taller, so a day there
  overflows the screen sooner.

## Non-goals

- **Dropping into a meal type that has no section on screen.** Only visible sections are targets.
  If the day has only *second breakfast*, the gesture still starts and the row can be dragged, but
  there is nowhere to drop it; releasing cancels and nothing is written.
- **Dropping into *Unassigned*.** Would mean clearing the pin, which does not exist. Dragging *out
  of* *Unassigned* into a meal is allowed and pins the entry.
- **Positional drop.** The target is the whole section. The entry lands where its `date` sorts it,
  not where the finger was released.
- **Moving to another day.** A pin governs the section within a day only (§ 3.2).
- **Accessibility alternative** (VoiceOver / TalkBack action). The detail screen's picker stays
  the accessible path. Not addressed here.

## Design

### Behaviour (Cross-platform — both clients must match)

`onFoodMoved(food, target: MealTypeDomain)` in `DashboardViewModel`:

1. Resolve the source: `mealTypes.resolvedMealTypeId(for: food)` (Android: the same helper used in
   `groupedFoods`). If it equals `target.id` → return, no write, no haptic.
2. Optimistic update: `moved = food.copy(mealTypeId: target.id)` (Android: `food.copy(mealTypeId =
   target.id)`; the `withMealTypeId` helper in `FoodConsumedDetailViewModel.swift` is `private`). Replace the entry (match by `id`) in `monthCache[dayCacheKey(food.date)]`; if
   that day is `selectedDay`, set `foodsConsumed = foodsFromCache(for: selectedDay)`.
3. Bump a haptic trigger (see below).
4. `try await assignFoodMealType(food, mealTypeId: target.id)`.
5. On failure: `Log.error(…, category: Constants.LogCategory.dashboard)`, restore the **original**
   `food` in the same cache slot (again only refresh `foodsConsumed` if that day is still
   selected), `alertItem = AlertItem(title: L10n.Dashboard.errorMoveFailed)`. Revert locally —
   do **not** call `reloadDay`.
6. On success: nothing more. No `reloadDay` — only `meal_type_id` changed and the cache already
   holds it. (This is consistent with [ADR 0041](../adr/0041-dashboard-reloads-only-the-affected-day-after-a-write-or-foreground.md):
   the affected day is already correct.)

Key the cache slot by the food's own `date`, not by `selectedDay`, so a day change mid-write
cannot revert into the wrong day.

Drop target validity is decided in the view: only sections with a non-nil `mealType` accept a
drop. The view model guard in step 1 covers drops onto the source section.

New string, both platforms, key `dashboard_error_moveFailed`:
cs *„Potravinu se nepodařilo přesunout“*, en *"Could not move the food"* (matches
`dashboard_error_deleteFailed` wording).

### iOS

The dashboard is a `ScrollView` + `LazyVStack` of section cards, and rows use the custom
`RevealingSwipeToDeleteRow` (`iOS/Kalorie/Components/RevealingSwipeToDeleteRow.swift`) instead of `.swipeActions`.
`List` was tried first, but a row could not be dragged from one section to another there, so it
was dropped. Deployment target is iOS 26.

- **Section identity first.** `ForEach(Array(viewModel.groupedFoods.enumerated()), id: \.offset)`
  identifies sections by index; when a section disappears after a move, every following section
  shifts and SwiftUI animates/keys them wrongly. Change the outer `ForEach` id to the meal type id
  (`group.mealType?.id ?? "unassigned"`), keep `index` for `copyPopoverIndex` as it is. Land this
  as its own `[REFACTOR]` commit before the feature.
- **Payload.** `DraggedFoodConsumed { let id: String }` is `Codable` and builds an `NSItemProvider`
  with `registerDataRepresentation(for: .kalorieFoodConsumed, visibility: .ownProcess)` (JSON).
  The UTType `antoni.Kalorie.food-consumed` is **exported** in `iOS/Kalorie/Resources/Info.plist`
  (`UTExportedTypeDeclarations`, conforms to `public.data`). Do not use plain text — the sections
  would accept foreign text drags and the id could be dropped into another app; `.ownProcess`
  keeps the payload inside the app. `Transferable` / `.draggable` / `.dropDestination` are not used
  because the `ScrollView` layout uses `onDrag` / `onDrop` with item providers.
- **Source:** `.onDrag { DraggedFoodConsumed(id: food.id).itemProvider } preview: { FoodConsumedView(food) }`
  on the row button. Long press starts the drag; tap (detail) and the custom swipe (delete) keep
  working — verify on device.
- **Target:** `.onDrop(of: [.kalorieFoodConsumed], isTargeted:)` on the section card, only when
  `group.mealType != nil`, highlighting the card while targeted. The drop calls
  `viewModel.onFoodsDropped(providers, on: mealType)`, which loads the id, looks the food up in
  `foodsConsumed` and calls `onFoodMoved`. The entry sorts by `date` after the drop.
- **Haptics:** the system drag lift has its own haptic — verify on device; add nothing for the lift
  unless it is missing. For the commit add `@Published private(set) var moveFeedbackTrigger = 0`
  to the view model, increment it in step 3, and `.sensoryFeedback(.success, trigger:
  viewModel.moveFeedbackTrigger)` on the scroll view.
- **Wiring:** add `assignFoodMealType: any AssignFoodMealTypeUseCaseProtocol` to
  `DashboardViewModel.init`. Call sites that break: `DashboardConfigurator.swift:18`
  (pass `AssignFoodMealTypeUseCase(dataProvider: dataProvider, authProvider: authProvider)`),
  the preview `DashboardView.swift:287` and `makeSut` in
  `iOS/KalorieTests/DashboardViewModelTests.swift:787` (pass `AssignFoodMealTypeUseCaseFake()`).
- **L10n:** `L10n.Dashboard.errorMoveFailed` next to `errorCopyFailed`
  (`iOS/Kalorie/Resources/Localization/L10n.swift:80`) + both languages in
  `iOS/Kalorie/Resources/Localizable.xcstrings`.

### Android

`DashboardView.kt:222-262` — `LazyColumn`; each section is two `item {}`s: a `SectionHeader` and a
`SectionCard` containing all rows (`RevealingSwipeToDeleteRow` → `FoodConsumedView` with `clickable`).
Compose BOM `2026.09.00`.

- **Section identity.** The `item {}`s have no `key`. Give both items keys derived from the meal
  type id (`"header-${id}"`, `"card-${id}"`, `"unassigned"` for nil), same reason as on iOS. Same
  separate `[REFACTOR]` commit.
- **Source:** `Modifier.dragAndDropSource` on `FoodConsumedView` (the overload that starts on long
  press). Transfer data: a `ClipData` plain-text with `food.id`, **no** `View.DRAG_FLAG_GLOBAL`
  (keeps the drag inside the app). Check `SwipeToDismissBox` still swipes and `clickable` still
  opens the detail.
- **Target:** `Modifier.dragAndDropTarget` on the `SectionCard` (and on the `SectionHeader`, so a
  drop on the title counts), only when `group.mealType != null`. Highlight the card while a drag
  hovers it (`onEntered` / `onExited`; e.g. a border in `MaterialTheme.colorScheme.primary`).
  `onDrop` reads the id from the clip, finds the food in `foodsConsumed.value`, launches
  `viewModel.onFoodMoved(food, mealType)`, returns `true`.
- **Haptics:** `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)`
  when the drag starts (inside the transfer-data lambda), `HapticFeedbackType.Confirm` when a move
  is committed — drive it from a `moveFeedbackTrigger: StateFlow<Int>` in the view model observed
  with `LaunchedEffect`, mirroring iOS.
- **Autoscroll (required).** Platform drag-and-drop does not scroll a `LazyColumn` by itself, and
  `DashboardView.kt` has no list state today. Add `val listState = rememberLazyListState()` and
  pass it to the `LazyColumn`. Wrap the `LazyColumn` in a `Box` and overlay two invisible edge
  zones, `Modifier.align(TopCenter)` / `align(BottomCenter)`, full width, `AUTOSCROLL_EDGE = 72.dp`
  high, each with its own `dragAndDropTarget`:
  - `shouldStartDragAndDrop` returns `true` (the zones must receive enter/exit for any drag
    started by a food row);
  - `onEntered` starts a coroutine (`rememberCoroutineScope`) that loops
    `while (isActive) { listState.scrollBy(±AUTOSCROLL_STEP_PX); withFrameNanos {} }` — negative
    for the top zone, positive for the bottom; a step of ~12 px per frame is a starting value,
    tune on a device;
  - `onExited`, `onEnded` and `onDrop` cancel that job; `onDrop` returns `false` (an edge zone is
    never a valid drop — the user drags back onto a section after the list has scrolled).
  Zones carry no `pointerInput`/`clickable`, so taps and swipes underneath are unaffected — verify.
  The bottom zone sits over the FAB area; that is fine, the FAB is not a drop target.
- **Wiring:** add `assignFoodMealType: AssignFoodMealTypeUseCaseProtocol` to the
  `DashboardViewModel` constructor (`DashboardViewModel.kt:85`). Call sites:
  `DashboardConfigurator.kt` (`AssignFoodMealTypeUseCase(dataProvider, authProvider)`, as in
  `FoodConsumedDetailConfigurator.kt:44`) and `DashboardViewModelTest.kt`
  (`AssignFoodMealTypeUseCaseFake` from `app/src/debug/…/AssignFoodMealTypeUseCaseFake.kt`). Use
  the existing `perform(onFailure = …)` helper (`DashboardViewModel.kt:331`) for the write; the
  revert goes into `onFailure`.
- **Strings:** `dashboard_error_moveFailed` in `res/values/strings.xml` (cs) and
  `res/values-en/strings.xml` next to `dashboard_error_deleteFailed` (line 124).

### Tests (both clients, `DashboardViewModelTests` / `DashboardViewModelTest`)

Each test states why it matters:

- Drop onto the section the entry already resolves to → the use case fake records no call (a
  write there would pin an unpinned entry and freeze it against future window edits).
- Move from second breakfast to breakfast → the entry appears in the breakfast group immediately
  and the use case received the target id.
- Moving the last entry of a section → that section is gone from `groupedFoods` (the user-visible
  promise of this feature).
- Moving an entry from *Unassigned* → it is pinned and *Unassigned* disappears if it was last.
- Write fails (`shouldThrow = true`) → the entry is back in its original section, `alertItem` is
  the move-failed alert.
- Day total (`dailyMacros`) is unchanged after a move (§ 3.3 invariant: every food in exactly one
  section).

The fake must record calls for the first test; extend `AssignFoodMealTypeUseCaseFake` with a
recorded-calls property on both platforms if it does not have one.

### Manual verification (no automated test covers it)

On a device, both platforms: tap still opens the detail; swipe still deletes; long-press lifts
with a haptic; drop on another meal moves with a haptic; drop on own section / *Unassigned* /
empty space does nothing; a failed write → the row snaps back and the alert shows (covered by the unit test); the emptied
section disappears with a sane animation. Autoscroll: log enough entries that the top and bottom
sections cannot both be on screen, drag a row from the bottom section to the top one in a single
drag — the list must scroll while the finger rests near the edge and stop when it leaves the edge
or the drag ends. On iOS the list is a plain `ScrollView`, so confirm on a device that drag
autoscroll engages; if it does not, that is a blocker to report, not to work around silently.

## Alternatives considered

- **Edit mode with `onMove` / reorder handles.** Rejected by the product requirement — no toggle.
  `onMove` is also positional reordering, which a time-sorted list cannot honour.
- **`List` with `.swipeActions` and `.onDrag` / `.onDrop` (iOS).** Tried first and
  rejected: a row could not be dragged between sections. The shipped layout is a `ScrollView` +
  `LazyVStack` with a custom `RevealingSwipeToDeleteRow`; the cost is losing `.swipeActions` and the `List`
  autoscroll. The VoiceOver delete action is restored on the row as an `.accessibilityAction`, and
  the Android row has the matching TalkBack custom action.
- **Reload the day after the write instead of an optimistic update.** Simpler, but the row would
  visibly jump back to its source for the duration of the round trip on every move.
- **Third-party reorderable list (Android).** Built for positional reordering, not for dropping
  onto a group.

## Risks

- **Android autoscroll is hand-written.** If the edge zones do not receive `onEntered` while a
  section target underneath also listens, the zones must be checked first on a device before
  anything else is built on them; fallback is a single `dragAndDropTarget` on the wrapping `Box`
  that reads the pointer position in `onMoved` and scrolls when it is within `AUTOSCROLL_EDGE` of
  either edge.
- **Discoverability.** Without a toggle there is no visible hint that rows can be dragged. The
  detail-screen picker remains.
- **Pinning is permanent.** A moved entry no longer follows its time window if the user later
  retimes meal types — this is the existing ADR 0022 behaviour, not new.
