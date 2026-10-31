# 0047. OpenFoodFacts `serving_quantity` sets one portion and the measure

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-10-31

## Context

[Design 0008](../design/0008-food-portions.md) lists personal portions on OpenFoodFacts
(`.external`) items as a non-goal for v1. Its reasoning: `weight` is hardcoded to `100` for them,
and OpenFoodFacts' `quantity` field is free text (`"6x25g"`, `"1L"`) that cannot be parsed
reliably. The design never looked at `serving_quantity`, a separate numeric field. The consequence
is that a packaged biscuit of 47 g opens at `100 g`, and the user has to type the weight.

[Design 0011](../design/0011-food-measure-grams-or-millilitres.md) likewise keeps every
OpenFoodFacts item in grams, because "OFF's per-serving/per-volume metadata is not reliable enough
to key off". So a drink is shown and logged in grams even though the app supports millilitres.

A read of the API on 2026-10-31 covered the three biscuits a user had logged and the 50 most
scanned Czech products (21 305 in total):

- `serving_quantity` was set on all 50, with `serving_quantity_unit` `g` or `ml`. On one of the
  three biscuits the value was the string `"30"`, on the others a number.
- `serving_size` is free text in several languages (`"15 gram"`, `"1 portion (330 ml)"`,
  `"0.5 can (207 g)"`, `"1 Square (16.666 g)"`). `quantity` also contains `"g"`, `"0.00 - 1.00"`
  and `"1pcs"`. Neither is usable.
- `serving_quantity` carries noise: fractions such as `16.666`, and a value of exactly `100` on
  pasta and flakes, where the producer repeats the per-100 g label. The unit is sometimes wrong
  for a drink (`250 g` for a 1.5 l cola).
- The sample is ordered by scans, so these products are filled in better than the long tail.

## Decision

An external product carries **at most one portion**, built from `serving_quantity` alone, and its
**measure** follows `serving_quantity_unit`. `serving_size` and `quantity` are not read.

1. **Request.** `serving_quantity,serving_quantity_unit` are added to the `fields` list
   (`Constants.OpenFoodFacts.productFields` on iOS, `PRODUCT_FIELDS` on Android), for both the
   search and the barcode request. Without them OpenFoodFacts does not send the fields.
2. **Decoding.** `serving_quantity` is accepted as a JSON number or as a string holding a plain
   decimal number (digits with an optional `.` fraction; surrounding spaces, tabs, `\n` and `\r`
   are ignored and no other character is; the parse lives in TextKit `servingQuantity` /
   `servingQuantityUnit`, so both clients trim the same set; `30f`,
   `0x10` and `1e3` are not numbers here).
   Anything else decodes as absent. `serving_quantity_unit` is an optional string.
3. **Validity.** The portion is offered only when all of these hold; otherwise
   `portions` is `[]`:
   - the value is a finite number, rounded to one decimal place (`16.666` → `16.7`);
   - the rounded value passes `FoodPortionValidation.validate(name:grams:)`, which the mapping calls (today `>= 1`);
   - the rounded value is `<= 5000`, so a typo such as `1000000` does not become the preselected
     unit (see [ADR 0030](0030-first-quantity-picker-option-is-the-preselected-unit.md));
   - the rounded value is not `100`, because the `100 g` / `100 ml` unit already exists in the
     picker;
   - `serving_quantity_unit` is `g` or `ml` (case-insensitive).

   The bound is `5000`: a `1000 ml` bottle is a real serving, while anything above 5 kg or 5 l is
   far more likely a data-entry error, and the portion is preselected, so one tap would log it.
4. **Name.** The portion is named `1 porce`. That is already a known entry in
   [ADR 0043](0043-portion-names-are-localized-and-normalized-at-render-time.md), so it displays
   as `1 serving` on an English device.
5. **Measure.** `measure` is `.millilitres` when `serving_quantity_unit` is `ml`
   (case-insensitive) and `serving_quantity` is valid by point 3 apart from the `100` rule,
   otherwise `.grams` as today. A drink with exactly `100 ml` therefore gets millilitres and no
   portion, while an `ml` unit next to a missing, garbled or out-of-range quantity stays grams:
   the measure is stored in logged entries and favourites and cannot be edited afterwards, so it
   is not set from a bare unit string. The unit is a label on the numbers, never a conversion
   ([design 0011](../design/0011-food-measure-grams-or-millilitres.md), *The core rule*); the
   nutriment values are used as they are.
6. **Label.** The picker label stays `"<name> (<amount>)"`, its unit taken from `measure` as for
   every item. A whole amount is shown without a decimal part (`47 g`, not `47,0 g`); a
   fractional one keeps its single decimal (`16,7 g`). A drink therefore shows
   `1 porce (330 ml)`, `100 ml`, and is logged as `330 ml`.
7. **Where it is built.** In `OpenFoodFactsProductDTO.asDomain()`, so search and barcode lookup
   cannot disagree. It is never written to `foodItems`. It is written wherever a whole
   `FoodItemDomain` is snapshotted: `favouriteFoods` (`FavouriteFoodDTO`) and
   `stats/foodFrequency` (`FoodFrequencyDTO`) copy `portions` and `measure_unit`, and a
   `foodConsumed` entry stores the `measure_unit` it was logged in. No schema change: all three
   already carry `measure_unit` on both platforms.
8. **Tests.** The cases go into the shared `fixtures/open-food-facts-mapping-cases.json`
   ([ADR 0039](0039-swift-only-rules-move-into-kmp-or-share-golden-vectors.md), point 5), read by
   `OpenFoodFactsProductDTOTests` (iOS) and `OpenFoodFactsProductDTOTest` (Android): `expected`
   gains `portions` and `measure`, and both readers compare them, which neither does today.
   Cases: number and string forms, rounding, the `100` and `< 1` rejections, the unit check, an
   absent field, `ml` with exactly `100` (millilitres, no portion), and `ml` with a missing or
   invalid quantity (grams).

Unchanged: `weight` stays `100`; personal portions stay `.catalogue`-only
(design 0008, non-goals), so the manager button and its fetch are still hidden for `.external`.

## Consequences

- **The picker opens on the portion.** By [ADR 0030](0030-first-quantity-picker-option-is-the-preselected-unit.md)
  the first option is the preselected unit (`FoodQuantityViewModel.defaultUnit` on both
  platforms), so a product with a valid `serving_quantity` opens at
  `1 porce (47 g)` instead of `100 g`. A product without one behaves as before.
- **Drinks are shown in millilitres.** For a drink this is more accurate than before:
  OpenFoodFacts stores a liquid's `_100g` values per 100 ml as printed on the label, so the
  numbers were always per 100 ml and only the label was wrong.
- **A wrong unit cannot be detected.** There is no second source to check
  `serving_quantity_unit` against. `250 g` on a 1.5 l cola stays grams, `1 porce (250 g)`, exactly
  as the whole item is shown today. The opposite mistake, `ml` on a solid food, mislabels it as
  millilitres; the numbers stay correct, since the measure converts nothing.
- **A serving equal to the whole package** (`47 g` on a `47 g` bar) is offered. There is no
  second number to distinguish a serving from a package, and the amount is correct in both
  readings.
- **Snapshots keep the portion.** An external favourite and its `stats/foodFrequency` entry keep
  the portion they had when written, under the same staleness caveat as the rest of the snapshot.
  [ADR 0044](0044-external-favourites-are-upgraded-when-the-food-enters-the-catalogue.md) says
  such a snapshot has "an empty `portions` list"; that no longer holds for snapshots written
  after this change. Its upgrade does not depend on it: `RefreshFavouriteFoodUseCase` replaces
  the whole item, portions included, when the food enters `foodItems`
  (`test_refresh_whenExternalItemNowExistsInCatalogue_…`), and the catalogue's `measure_unit`
  with it. The dashboard detail re-fetches an external item from OpenFoodFacts only for the photo
  and the favourite toggle; the logged entry keeps the measure it was logged in.
- **Coverage is unknown.** All 50 sampled products had the field because they are the most
  scanned. How many products across the catalogue lack it is not measured; those simply get no
  portion.
- This reverses one sentence of design 0008, that an external item has no portions
  (`OpenFoodFactsProductDTO`'s mapping "is `[]`, exactly correct there"). Its other non-goal
  reason, that `quantity` cannot be parsed, still holds and is the reason `quantity` is not read.
- This reverses the non-goal "OpenFoodFacts items as millilitres" of design 0011. Its reason
  was that the metadata is not reliable enough; `serving_quantity_unit` was set on every sampled
  product, and a wrong value costs only a label, not the numbers.
- A second client must request the same two fields and apply the same validity and measure
  rules, or the two apps show different portions and units for the same barcode. The shared fixture (point 8) pins it.
- **The clients may ship this separately.** Until both have it, the same barcode opens at
  `1 porce (330 ml)` on one and `100 g` on the other. A snapshot written by the newer client
  carries the portion and `measure_unit`, and the older client already reads both; nothing
  breaks, the difference is only the default unit and the label of a freshly fetched item.
