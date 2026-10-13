# 0044. An `external` food is upgraded to its catalogue version when it appears in `foodItems`

- **Status:** Accepted
- **Scope:** Backend, Cross-platform
- **Date:** 2026-10-13

## Context

[ADR 0025](0025-food-item-kind-discriminates-entry-origin.md) describes an `external` id as
"guaranteed **not** to resolve against `foodItems`." Maintainer catalogue imports
([ADR 0027](0027-catalogue-writes-require-a-maintainer-claim.md)) broke that: a
food users had logged from OpenFoodFacts can later be added to `foodItems`, with portions and a
corrected name.

A favourite (and its `stats/foodFrequency` snapshot) saved while the food was OpenFoodFacts-only
keeps `food_item_kind: external` and an empty `portions` list. `RefreshFavouriteFoodUseCase`
refreshed only `.catalogue` items, so that user never saw the catalogue version. Favourites are
listed first in search and deduplicate by id, so the stale favourite hid the catalogue result, and
logging from it wrote `external` back into `foodFrequency`, so the "next log overwrites the
snapshot" behaviour of design 0020 never happened for these users.

## Decision

The meaning of `.external` is narrowed to "came from OpenFoodFacts **when it was selected**; may
resolve against `foodItems` later."

`RefreshFavouriteFoodUseCase` skips only `.createdMeal`. For an `external` item it looks the id up
in `foodItems`; on a hit the catalogue version (`kind = catalogue`, its portions) is returned and
the stored favourite is rewritten with it, keeping the original `favourited_at`. On a miss the
stored item is returned unchanged with no write.

`AddFoodSheetViewModel.onSelectResult` treats an `external` frequency candidate like a
`.catalogue` one, so it is refreshed too. `foodFrequency` itself is not patched; the next log
rewrites it from the selected item.

## Consequences

- Opening a stale `external` favourite costs one `foodItems` read, as a `.catalogue` favourite
  already does.
- `FoodConsumedDetailViewModel` still skips the catalogue lookup for `external` log entries. That
  is deliberate and unchanged.
- ADR 0025 stays in force except for the "guaranteed not to resolve" wording.
