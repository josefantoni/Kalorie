# 0009. Nutrition values are denormalised into every collection that references a food

- **Status:** Accepted
- **Scope:** Backend, Cross-platform
- **Date:** 2026-08-27

## Context

Three collections reference a catalogue item from `foodItems`, and all three copy its nutrition
values in rather than storing a reference and reading the item back:

- `foodConsumed` stores the macros **already scaled to the logged weight**, plus
  `food_item_id`.
- `favouriteFoods` stores a full copy of the `FoodItemDomain` plus `favourited_at`, keyed by
  the same barcode.
- `myCreatedMeals` stores a nutrition snapshot per ingredient inside the `ingredients` array.

The forces:

- Firestore charges per document read and has no joins. Rendering one Dashboard day with a
  reference-only model would mean one read per distinct food on top of the day query, and the
  month view would multiply that by thirty.
- `foodItems` is a shared, maintainer-curated catalogue. A logged entry is a record of what the
  user actually ate, at the values that were true then. Re-reading the catalogue would let a
  later correction rewrite the user's history.
- Offline: the day view has to render from cache, which a fan-out of per-item reads makes
  unreliable.

## Decision

Each of the three collections stores its own nutrition snapshot, taken at write time. Nothing
reads `foodItems` in order to render or aggregate a `foodConsumed`, `favouriteFoods` or
`myCreatedMeals` document. `food_item_id` exists to get *back* to the catalogue item on demand,
not to resolve values at read time.

## Consequences

- A day or month view is exactly one query, and works offline from cache. Adding a field to
  `foodItems` does not change any read path.
- **The snapshots never refresh.** If the maintainer corrects a catalogue item's macros, every
  existing favourite and every saved meal keeps the old values indefinitely, and there is no code
  path that reconciles them. This is deliberate in all three collections, not only in
  `foodConsumed`: both [design 0003](../design/0003-favourite-foods.md) and
  [design 0006](../design/0006-own-daily-meals.md) accept it explicitly in their *Risks* tables
  and name the same mitigation — re-read the item by `food_item_id` at the point the user opens
  it (a favourite when tapped, a meal when opened in the editor), which is a localised change
  needing no schema migration. Do not implement that refresh as a background reconciliation; the
  designs chose a user-triggered refresh precisely so a logged day is never rewritten underneath
  the user.
- Storage is duplicated many times over. This is deliberate and cheap relative to read cost.
- A future reader must not "normalise" this away as redundancy. Doing so changes the meaning of
  a logged entry from *what was eaten* to *what the catalogue currently says*, which is a
  product change, not a refactor.
