# 0025. `food_item_kind` discriminates what `food_item_id` points to

- **Status:** Accepted
- **Scope:** Backend, Cross-platform
- **Date:** 2026-09-08

## Context

[Design 0003](../design/0003-favourite-foods.md) introduced `food_item_id` as required, meaning
"the barcode of the catalogue item this entry came from." Two things already broke that meaning
by the time [design 0006](../design/0006-own-daily-meals.md) shipped:

- [ADR 0012](0012-external-food-is-surfaced-never-imported.md) established that an entry logged
  from an OpenFoodFacts result carries a `food_item_id` that "points at no document" — a barcode,
  but one `foodItems` never contains.
- Design 0006's Variant A logs a `MyCreatedMeal` as an ordinary `foodConsumed` document, and writes
  the **meal id** — a UUID — into `food_item_id` instead of a barcode.

By then the field could hold three different things, distinguishable only by inspecting the id's
shape (a run of digits vs. a UUID) and, for digits, not distinguishable at all between "resolves
in `foodItems`" and "came from OpenFoodFacts and never will." Design 0006 named this explicitly
under *What `food_item_id` means now*, accepted the ambiguity for v1, and set a revisit condition:
add a discriminator "if a second consumer of `food_item_id` appears."

That consumer arrived: *Rank search results by frequency* (`TODO.md` **A2-1**) needs to group
logged entries by origin, and cannot do so reliably from id shape alone. The field was added and
shipped, but its only record is a paragraph inside design 0006 — a document frozen since
2026-08-20 — under *What `food_item_id` means now*, and a passing mention in ADR 0012. Neither is
where a second client, or a reader scanning the ADR index for cross-platform obligations, would
find a required field two collections now carry. This record is that ADR, written after the fact
per the design-doc rule in `docs/README.md`: a `Backend` / `Cross-platform` decision that emerges
after a design doc ships gets its own ADR rather than a further addendum to the frozen document.

## Decision

`FoodConsumedDTO` and `FavouriteFoodDTO` each gain a required field, `food_item_kind`
(`FoodItemKind: .catalogue | .external | .createdMeal`, wire values `catalogue` / `external` /
`created_meal`), set once at the point the food is selected and carried through unchanged by
every writer:

- **`.catalogue`** — `food_item_id` is a barcode that resolves against `foodItems`.
- **`.external`** — `food_item_id` is a barcode from OpenFoodFacts, guaranteed **not** to resolve
  against `foodItems` (ADR 0012).
- **`.createdMeal`** — `food_item_id` is a UUID naming a document under `myCreatedMeals`, not a
  barcode at all (design 0006).

The field is **non-optional**, the same choice design 0003 made for `food_item_id` itself: the app
has no production users yet, so a required field with no backfill costs nothing, and it removes a
`nil` branch from every reader instead of adding one.

`FoodConsumedDetailViewModel.onAppear()` gates its two lookups by kind rather than by a single
`== .catalogue` check: the catalogue lookup (`FetchFoodItemByBarcodeUseCase`) runs only for
`.catalogue`, since `.external` and `.createdMeal` are both a guaranteed miss against `foodItems`;
the favourite lookup (`IsFavouriteFoodUseCase`) runs for `.catalogue` **and** `.external` —
favouriting queries the separate `favouriteFoods` collection by id and does not care where the id
came from — and is skipped only for `.createdMeal`.

## Consequences

- A second consumer of `food_item_id` can group entries by origin without inferring it from id
  shape. Design 0006's own "the two values cannot collide" argument — digits vs. UUID — was
  already load-bearing by accident; this makes that inference explicit and typed instead of
  leaving a future reader to rediscover it.
- **Every writer to `foodConsumed` and `favouriteFoods` must set this field.** A second client
  reading either collection must switch on all three cases; an unrecognised or absent value fails
  to decode the containing document, and for `foodConsumed` that takes a whole day's fetch down
  with it — the same failure mode design 0003 accepted for `food_item_id`, now paid twice for the
  same reason.
- Supersedes nothing. It fills exactly the gap design 0006 predicted and named a trigger for; that
  document's own "Update — the discriminator now exists" paragraph is left as written — this ADR
  is the record that paragraph should have pointed to, not a correction of it.
- Any future third origin for a logged or favourited entry adds a fourth `FoodItemKind` case here,
  not a fourth id-shape convention.
