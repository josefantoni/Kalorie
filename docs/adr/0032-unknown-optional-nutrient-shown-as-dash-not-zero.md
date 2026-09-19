# 0032. An unknown optional nutrient is displayed as a dash, never as zero or a hidden row

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-09-18

## Context

`FoodNutritionValues.fiber` and `.fatSaturated` are `Double?`, because not every source (a
catalogue entry, an OpenFoodFacts import, a photo-parsed label) states them. `nil` here means
"unknown", not "zero" — the same distinction [ADR 0007](0007-derive-missing-energy-kj-from-macros.md)
already drew for `energyKJ`, which is why that record derives an estimate instead of defaulting
to `0`. Fiber and saturated fat cannot be derived the same way — there is no general conversion
from the other macros — so the same falseness had to be resolved differently.

Before this record, no display convention existed and the two optional fields had already
drifted apart: `FoodConsumedDetailView`'s fiber row silently fell back to `?? 0`, while
`FoodQuantityView` and `FoodConsumedDetailView` simply omitted a saturated-fat row altogether
rather than deciding what an absent value should show. Neither choice was a decision — the
`?? 0` fallback was carried over from the arithmetic call sites that need a concrete number to
scale or sum, and the missing row was an oversight tracked in `TODO.md`.

This is `Cross-platform` scope, not `iOS`, because it is a user-facing reading of a value the
backend and every client share: a user checking the same food on iOS and (eventually) Android
must see the same thing when the data is genuinely missing, not one client's silent zero next to
another's honest placeholder.

## Decision

When a single food's `fiber` or `fatSaturated` is `nil`, every screen that displays it as its own
row shows a dash (`–`), never `0` and never by omitting the row.

This applies only to **displaying one food's own value**. Summing several foods' macros into a
day or meal-section total (`DashboardViewModel.dailyMacros`, `MealSectionMacroView`) still needs
a concrete number to add, and keeps its existing `?? 0` fallback for that arithmetic — this
record does not touch aggregation, only the display of a single food's row.

## Consequences

A future optional nutrient field follows the same rule by default: dash for a single food's
unknown value, never a silent zero or a missing row. An Android client reads this record to know
why a blank field shows `–` rather than `0`.

The existing `?? 0` fallback in `FoodQuantityViewModel.scaledFiber` and
`FoodConsumedDetailView`'s fiber row is superseded by this record and must be replaced with the
dash convention as part of adopting it, not left as a pre-existing exception.
