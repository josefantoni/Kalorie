# 0033. A created meal's portions can also be edited from the quantity screen

- **Status:** Accepted
- **Scope:** iOS
- **Date:** 2026-09-19

## Context

[Design 0008](../design/0008-food-portions.md) decided that a created meal's shortcuts belong on
the meal itself (`MyCreatedMealDomain.portions`) and are edited in the meal editor, while
personal portions of a catalogue item live in a separate barcode-keyed document and are managed
from the quantity screen. That data decision stands. What it left out is the practical gap: a
user logging a meal who realises a shortcut is missing has to leave the quantity screen, open the
editor, and come back.

## Decision

`FoodQuantityViewModel` takes an optional `meal: MyCreatedMealDomain?`. When it is set:

- `isPersonalPortionsAvailable` is true and the existing `FoodPortionsManagerView` is reused
  unchanged; `personalPortions` is seeded from `meal.portions` in `init`, not fetched.
- `unitOptions` lists `personalPortions` only. `item.portions` is derived from `meal.portions`
  (`asFoodItem()`), so listing both would show every portion twice.
- Add and delete write the whole meal through `UpdateMyCreatedMealUseCase` with the changed
  `portions`, then call `onMealUpdated` so `AddFoodSheetViewModel.myCreatedMeals` — loaded once —
  does not go stale. A failed write restores `personalPortions` and does not call `onMealUpdated`.

`makeFoodQuantityView` receives `MyCreatedMealDomain?` instead of `isMyCreatedMeal: Bool`
(`meal != nil` is the old flag) plus the `onMealUpdated` callback.

## Consequences

- The storage decision of design 0008 is unchanged: portions are still written to `meal.portions`.
  Only the place they can be edited widens.
- Saving rewrites the whole meal document and bumps `updatedAt`, so a meal list sorted by it
  reorders after a portion is added. A concurrent edit from another device is overwritten — the
  same exposure the meal editor already has.
- `UpdateMyCreatedMealUseCase` validates the whole meal, so an unrelated invalid field can fail a
  portion save; the user sees the generic save-failed alert.
