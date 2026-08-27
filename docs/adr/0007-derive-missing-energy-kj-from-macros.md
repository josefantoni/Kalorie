# 0007. Missing energyKJ is derived from macros, not defaulted to 0

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-08-27

## Context

`energyKJ` is optional on every source of food data: `FoodItemDTO`, `FavouriteFoodDTO`, and the
OpenFoodFacts `nutriments` payload all model it as `Double?`, because not every entry carries a kJ
figure alongside its kcal one. Every call site that read one of these sources
(`SearchFoodItemsUseCase`, `FetchFoodItemsUseCase`, `FetchFoodItemByBarcodeUseCase`,
`FetchFoodByBarcodeExternallyUseCase`, `SearchFoodExternallyUseCase`, `FavouriteFoodDTO.asDomain()`)
defaulted a missing value with `?? 0`, because `FoodItemDomain.energyKJ` itself is a plain
non-optional `Double`.

This was harmless-looking but wrong: a food with 100 g of fat and no stated kJ value clearly does
not have zero energy, and showing `0 kJ` next to real fat/carbohydrate/protein numbers is a visibly
false reading, not an absence of one.

The cost of that falseness changed when `MyCreatedMeal` composition shipped
([design 0006](../design/0006-own-daily-meals.md)). `MyCreatedMealDomain.asFoodItem()` feeds every
ingredient's `energyKJ` into `weightedMeanPerHundredGrams`, a gram-weighted average across the
whole meal. A single ingredient whose `energyKJ` fell back to `0` no longer produced an isolated,
cosmetic wrong number on its own row — it pulled the **composed meal's** kJ figure down in
proportion to that ingredient's weight, corrupting a number the user directly reads and logs
against. This was found while auditing `MyCreatedMealIngredientDTO`'s decode safety (bringing its
optionals in line with `FavouriteFoodDTO`), not while working on the meal feature itself.

## Decision

Add `MacroKit.energyKJFromMacros(fat:carbohydrate:protein:)`, computing energy from the EU
Regulation 1169/2011 (Annex XIV) general conversion factors — 37 kJ/g fat, 17 kJ/g carbohydrate,
17 kJ/g protein — and use it as the fallback everywhere a source's own `energyKJ` is missing,
replacing every existing `?? 0`.

The function lives in **MacroKit**, not as a private Swift helper, for the same reason
`weightedMeanPerHundredGrams` does ([design 0004](../design/0004-shared-macro-calculation-module.md),
[design 0006](../design/0006-own-daily-meals.md)): the same food must estimate the same energy on
every client. A Swift-only implementation would let an Android client derive a different number for
an identical ingredient with a genuinely missing kJ field.

## Consequences

A food or ingredient with no stated kJ value now shows a plausible estimate instead of a provably
wrong zero. It is still an estimate — the factors omit fibre, alcohol, and organic acids, and the
source's own figure (when present) is always preferred and never overridden.

Any future food source (a new importer, a second external API) must route its `energyKJ` fallback
through `energyKJFromMacros` rather than reintroducing `?? 0`. The factors live in one shared place
specifically so no source and no platform is free to disagree with another.

An Android client must implement the identical 37 / 17 / 17 factors when it starts; this record is
what tells it why the number is not simply `0`.
