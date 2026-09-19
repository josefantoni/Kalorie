# 0031. The meal-type pin is resolved by the caller, not by `SaveFoodConsumedUseCase`

- **Status:** Accepted
- **Scope:** Cross-platform (the contract a second client's save path must match), iOS (the picker)
- **Date:** 2026-09-17

## Context

[ADR 0022](0022-meal-assignment-may-be-pinned-by-the-user.md) added `meal_type_id` to `foodConsumed`
and had `SaveFoodConsumedUseCase` resolve it itself at write time, from `mealTypes.mealType(at:
date)`, because until now every new entry's pin came from exactly one source: the time of day it
was logged at. `TODO.md` asked for a second source — a picker on the quantity screen, so a user can
pin an entry to a meal window other than the one its time of day would resolve to, the same way
`FoodConsumedDetailView`'s picker already does after the fact.

A picker cannot coexist with the use case resolving unconditionally: if `SaveFoodConsumedUseCase`
always computes `mealTypes.mealType(at: date)?.id` internally, any explicit choice the user made in
the picker would be silently overwritten by the auto-resolve the moment the entry is saved.

## Decision

`SaveFoodConsumedUseCaseProtocol` takes the pin directly:

```swift
func callAsFunction(_ item: FoodItemDomain, grams: Double, date: Date, mealTypeId: String?) async throws
```

instead of the previous `mealTypes: [MealTypeDomain]`. The use case no longer resolves anything — it
writes whatever `mealTypeId` it is handed, `nil` included. Resolution moves entirely to
`FoodQuantityViewModel`, which already needs the resolved value to preselect the picker:

- **Default** (user has not touched the picker): `mealTypes.mealType(at: selectedDate)?.id`, the
  same shared `[MealTypeDomain].mealType(at:)` extension `FoodConsumedDetailViewModel` and
  `DashboardViewModel.groupedFoods` already call — a fourth call site, not a new implementation.
- **Explicit pick** (`onMealTypeSelected(_:)` called): the picked id, provided it is confirmed to
  still exist in a freshly refetched `mealTypes` right before saving. `onConfirm()` already refetches
  to avoid pinning against a stale snapshot (existing behaviour, unchanged); the addition is that an
  explicit pick which no longer resolves in the fresh list fails loudly with
  `L10n.Common.errorUnknown` instead of silently falling back to the time-based default — mirroring
  `FoodConsumedDetailViewModel.onSave`'s equivalent guard (`:141-149`) rather than inventing a second
  failure behaviour for the same class of problem.

The picker itself has no "by time of day" option, matching `FoodConsumedDetailView`'s picker: an
unassigned/`nil` selection renders only as a display placeholder (`L10n.FoodQuantity
.mealTypeUnassigned`, reusing the edit screen's own "Podle času" / "By time" wording under a
`FoodQuantity`-scoped key) and cannot be re-selected once a concrete meal type exists to move to.

## Consequences

- **A second client's save path must resolve the pin itself before calling save**, exactly as it
  must already resolve pins for display (ADR 0022's Consequences, "the same three-step read-time
  resolution … and the same write-time window resolution"). What changes for a second client is
  where that write-time resolution lives: not inside a shared save endpoint, but in whatever answers
  "what does this screen's picker show" on that platform. Two clients that resolve differently at
  this point pin identical entries to different meal types at the moment of logging, same as ADR
  0022 already warned against for the time-based case — this record does not relax that, it moves
  which layer is responsible for it.
- **The use case is now a pure writer for this field.** `SaveFoodConsumedUseCaseTests` covers only
  "it stores whatever id it is given, including `nil`"; the resolution behaviour (time-of-day
  matching, pin validity) is covered where it now lives — `FoodQuantityViewModelTests` for the new
  write path, `DashboardViewModelTests` for the existing read path — rather than duplicated in both
  places as it effectively was before (SaveFoodConsumedUseCaseTests previously re-tested the same
  `mealType(at:)` matching `DashboardViewModelTests` already covered).
- **The stale-pin guard is new, not inherited.** Before this record, nothing could hand the use case
  a `mealTypeId` referring to a deleted meal type except a resolve that had already checked it
  existed. A picker introduces a gap between "the user picked it" and "the save actually runs" in
  which the picked meal type can be deleted from another screen or device; the guard is what closes
  that gap for the *new-entry* path the way ADR 0022 already closed it for the *edit* path.
- This does **not** change what gets written for an entry the user never touches the picker for: the
  default still resolves from time of day exactly as before, so every existing test and behaviour
  around unpinned/auto-pinned entries is unaffected.
