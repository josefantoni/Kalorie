# 0012. OpenFoodFacts results are surfaced to the user, never imported into the catalogue

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-08-27

## Context

The app reaches OpenFoodFacts from two places, both of them fallbacks after the local catalogue
comes up empty:

- `SearchFoodExternallyUseCase` — a text search, run only when the Firestore search returned
  nothing and the query is at least three characters.
- `FetchFoodByBarcodeExternallyUseCase` — a barcode lookup, run only when
  `FetchFoodItemByBarcodeUseCase` found no local document.

Both produce a `FoodItemDomain` that the user can select and log exactly like a catalogue item.
Neither writes anything to `foodItems`.

The forces:

- `foodItems` is a curated catalogue whose quality is the point. OpenFoodFacts is
  crowd-sourced and uneven — missing macros, wrong units, product names with HTML entities in
  them. Auto-importing every hit would fill the shared catalogue with exactly the data the
  moderation flow described in `TODO.md` exists to keep out.
- Writing on behalf of the user, silently, into a globally shared collection is also the thing
  [ADR 0011](0011-foodItems-writable-by-any-authenticated-client.md) is uncomfortable about. The
  narrower the client-write surface stays until moderation ships, the smaller the eventual
  tightening.
- The mapping already refuses anything without a name or a positive kcal value, so what the
  user sees is filtered. That filter is a display-quality bar, not a catalogue-quality bar.

## Decision

External results are presented and can be logged, but nothing about them is persisted to
`foodItems`. The catalogue grows only through the *add a new food* form, which a person fills
in deliberately.

## Consequences

- The shared catalogue stays as good as the people who typed into it, and the moderation flow
  can be introduced without first cleaning up an auto-imported backlog.
- **A `foodConsumed` entry logged from an external result has a `food_item_id` that points at
  no document.** `food_item_id` was added as a required field precisely so a feature could get
  back to the source item; for externally sourced entries there is nothing to get back to.
  This is not an oversight. [Design 0006](../design/0006-own-daily-meals.md) → *What
  `food_item_id` means now* already established that the field is not guaranteed to resolve — it
  made the same trade for a logged created meal, whose `food_item_id` is a UUID — and deliberately
  deferred a `food_item_kind` discriminator with an explicit revisit condition: *"if a second
  consumer of `food_item_id` appears"*. **That condition has now arrived**, in the shape of *Rank
  search results by frequency*. Finding **A2-1** in `TODO.md` carries the detail.
- The same product is fetched from OpenFoodFacts again on every device and every session; the
  network result is never cached locally either. Fine at the current scale, and the reason the
  external call is gated behind "local search found nothing".
- Favourites and saved meals still work on external items, because both store a full nutrition
  snapshot rather than a reference ([ADR 0009](0009-denormalised-nutrition-snapshots.md)). That
  is why the dangling reference has not surfaced as a visible bug yet.
