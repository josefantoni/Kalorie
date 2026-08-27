# 0016. A logged entry is rescaled from its own stored values, never from the catalogue

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-08-27

## Context

A `foodConsumed` document stores macros **absolute for the logged weight**, not per 100 g
([ADR 0009](0009-denormalised-nutrition-snapshots.md)). When the user later changes the weight
of an entry, the new values have to come from somewhere. There are two candidates:

- re-read the catalogue item via `food_item_id` and scale its per-100 g values, or
- scale the entry's own stored values by `newWeight / oldWeight`.

`UpdateFoodConsumedUseCase` does the second. It never touches `foodItems`.

That is the only choice consistent with ADR 0009: a logged entry records *what the user ate*,
at the values that were true when they ate it. Re-reading the catalogue would let a later
correction — or an OpenFoodFacts item that never existed in the catalogue at all
([ADR 0012](0012-external-food-is-surfaced-never-imported.md)) — silently rewrite history the
moment the user adjusts a weight. It also keeps editing available offline and costs no extra
read.

The two paths that produce those values are deliberately different, and the difference is the
part worth knowing:

- **Logging** (`SaveFoodConsumedUseCase`) scales the catalogue item's *fractional*
  `caloriesPerHundredGrams` and rounds once, via `MacroKit.scaledCalories`.
- **Editing** (`ScaledMacros`) scales the entry's *already-rounded* `Int` calories, via
  `MacroKit.Macros.scaled`.

The macros are `Double` and unaffected; only `calories` is an `Int`.

## Decision

Editing an entry's weight rescales the entry's own stored values. `foodConsumed` does not store
a per-100 g basis and is not given one, so the rounded integer calorie count is what gets
scaled.

## Consequences

- History is stable. Nothing the maintainer does to `foodItems` can alter an entry the user has
  already logged, and editing works for entries whose `food_item_id` points nowhere.
- **Calorie rounding compounds across an edit.** The error is the original rounding error
  multiplied by `newWeight / oldWeight`, so it is invisible at ordinary weights and total at
  small ones: 1 g of a 33 kcal/100 g food rounds to 0 kcal on logging, and stays 0 at every
  weight it is later edited to. Recorded as finding **A4-3** in `TODO.md`.
- Storing `calories_per_hundred_grams` on `foodConsumed` would remove the compounding without
  reopening this decision — the rescale would still use the entry's own snapshot, just a
  fractional one. That is the fix to reach for, not a re-read of the catalogue.
- A future reader must not "simplify" `UpdateFoodConsumedUseCase` into fetching the catalogue
  item. It compiles, it looks tidier, and it changes what a logged entry means.
