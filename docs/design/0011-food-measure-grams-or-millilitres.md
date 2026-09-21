# Design: A food measured in grams or millilitres

- **Status:** Implemented
- **Scope:** Backend, Cross-platform, iOS

## Context and scope

`TODO.md` → *Unit choice when creating a food item* asked for the new-food form to accept the
package size in g, kg, ml or l, and left open whether ml implies a density assumption or a real
volume field. Analysis (2026-09-15) found the input convenience on its own worth almost nothing
and the real gap one level deeper:

- **`FoodItemDomain.weight` drives no calculation.** Its only readers are
  `FoodItemFormInput(item:)` (`AddFoodSheetViewModel.swift:38`, round-tripping it into the form),
  `FoodItemValidation.swift:50` (`> 0`), and the created-meal logging default
  (`AddFoodSheetConfigurator.swift:65`), which never goes through `FoodItemFormFields`. Design 0008
  (lines 16-18) already records that nothing exposes it.
- **The app already silently treats 1 ml as 1 g, in two places, without saying so.**
  `NutritionLabelParser` accepts a `100 ml` column header as the per-100 g column
  (`isPerHundredHeader`, `NutritionLabelParser.swift:229-232`; linear fallback at `:85`), and
  `packageWeight` multiplies `l` by 1000 exactly like `kg` (`:294`, `:302`). Design 0010 (§ Parsing,
  points 3 and 5) specifies both but never states the density assumption. A drink or oil
  photographed through design 0010 is therefore stored with per-100 ml values under a per-100 g
  label, and then logged in grams — oil (density ≈ 0.92) is off by ≈ 8 %, and the user is asked to
  weigh a liquid they naturally measure by volume.

This design adds one concept — **the unit a food is measured in** — and threads it from the
catalogue to the logged entry. It does not supersede design 0010; it makes its implicit ml≡g
explicit and removes the error it causes (see *Why this does not contradict design 0010*).

## Goals

- A catalogue item carries a **measure**: grams (default) or millilitres.
- For a millilitre item, every amount **of the food itself** reads `ml`: package size, per-100
  label, canonical and personal portions, the quantity picker, the logged entry on the Dashboard and
  its detail screen.
- The nutrition-label parser sets the measure from the label it read.
- The new-food form (all three `FoodItemFormFields` screens) lets the user pick the measure, and
  enter the package size in g/kg or ml/l, converted to the base unit on save and shown back in kg/l
  when ≥ 1000.
- Every existing document keeps decoding and keeps meaning grams, with no backfill.

## Non-goals

- **Density or unit conversion between g and ml.** Never. See *Design → The core rule*.
- **OpenFoodFacts items as millilitres.** `OpenFoodFactsProductDTO.asDomain()` stays grams
  (`weight: 100`, `:69`). OFF's per-serving/per-volume metadata is not reliable enough to key off.
- **Created meals and their ingredients.** A `MyCreatedMealDomain` is always grams
  (`asFoodItem()` passes the default). An ingredient added from a millilitre item stores its amount
  in `grams` numerically equal to the ml entered, and the meal editor keeps its literal `"g"`
  (`MyCreatedMealEditorView.swift:173`). Accepted: the density-weighted mean in `MacroKit` is
  unit-blind, and a meal mixing 200 ml milk with 50 g oats is off only by the milk's density.
- **Changing the Foundation Models extractor** (`NutritionLabelModelExtractorProtocol.swift`). It
  keeps its "per 100 g" guides; the deterministic parser alone decides the measure.
- **Renaming `FoodQuantityUnit.grams` / `.hundredGrams`, `FoodPortionDomain.grams`,
  `foodConsumed.weight`, `calories_per_hundred_grams`.** The names now mean "base unit"; renaming
  them would touch the Firestore contract and ~40 test call sites for no behavioural gain.

## Design

### The core rule

**Numbers are always in the item's own unit, and are never converted between grams and
millilitres.** A millilitre item's `weight` is millilitres, its portions' `grams` are millilitres,
its `calories_per_hundred_grams` is kcal per 100 ml, and a `foodConsumed` entry's `weight` is the
millilitres logged. Every calculation (`FoodItemDomain.scaled(toGrams:)`, `ScaledMacros`,
`MacroKit`) is unchanged, because `per-100-ml value × ml / 100` is exactly as correct as the gram
version. The measure is a **label on the numbers, not a conversion of them** — which is why no
density is ever needed, and why this is small despite touching many screens.

Nutrient amounts are unaffected: fat, protein, sugar, salt, fibre are grams of nutrient in every
case. Only amounts **of the food** change label.

### Domain type

New file `iOS/Kalorie/Core/Models/FoodMeasure.swift`:

```swift
enum FoodMeasure: String, Codable, Hashable {
    case grams
    case millilitres
}
```

The raw values are the Firestore wire values. Add computed properties for labels there (`unitSymbol`
→ `L10n.Common.unitGrams` / `L10n.Common.unitMillilitres`, etc.) rather than `switch`ing at every
call site.

### Firestore contract (`Backend`, `Cross-platform`)

One new optional string field, **`measure_unit`**, values `"grams"` | `"millilitres"`, on:

| Collection | DTO | Absent means |
|---|---|---|
| `foodItems` | `FoodItemDTO` | grams |
| `foodItemSubmissions` (`item` map) | nested `FoodItemDTO` — automatic | grams |
| `users/{uid}/favouriteFoods` | `FavouriteFoodDTO` | grams |
| `users/{uid}/foodConsumed` | `FoodConsumedDTO` | grams |

Not on `myCreatedMeals`, `foodItemPortions` (a personal portion list takes its label from the item
it belongs to) or `mealTypes`.

A second client must: decode an absent or unknown value as grams, write the field on every write of
those four collections, and never convert numbers based on it.

**Rules** — `backend/firestore.rules:9-24`, append to `validFoodItem`:

```
&& (!('measure_unit' in data) || data.measure_unit in ['grams', 'millilitres'])
```

This covers `foodItems` and `foodItemSubmissions` both, since both call `validFoodItem`. The
`users/**` subtree has no field validation and gets none. Rules and app can ship in either order:
current rules ignore unknown keys, and the new clause accepts documents without the field. Deploy
rules first anyway. **No automated test covers rules** — verify by hand in the Rules Playground: a
maintainer `foodItems` create with `measure_unit: "millilitres"` is allowed, with `"litres"` is
denied, without the field is allowed.

**Indexes** — add `{ "collectionGroup": "foodItems", "fieldPath": "measure_unit", "indexes": [] }`
to `fieldOverrides` in `backend/firestore.indexes.json`, matching the existing rule that only
queried `foodItems` fields are indexed (`ARCHITECTURE.md` § 1.6).

**No backfill script.** Absent = grams is the correct value for every existing document.

### DTO and domain changes (iOS)

Gotcha that decides where defaults go: **DTOs get no default, domains do.** A DTO property without
a default forces every memberwise construction to pass it, so the compiler finds every write path.
A domain property with a default keeps ~40 test call sites compiling — but then nothing forces the
copy paths to carry it, so those need explicit tests (below).

1. **`FoodItemDomain`** (`Core/Models/FoodItemModel.swift:16-76`) — add `let measure: FoodMeasure`,
   and `measure: FoodMeasure = .grams` as the **last** init parameter, after `portions`. No existing
   call site changes.
2. **`FoodItemDTO`** (`Core/Networking/FireStone/FoodItemDTO.swift`) — `let measureUnit:
   FoodMeasure?`, `case measureUnit = "measure_unit"`; `init(item:)` (`:59`) writes `item.measure`;
   `asDomain()` passes `measureUnit ?? .grams`. Use `decodeIfPresent`-compatible optional so an
   unknown future value does not fail the decode — if the synthesized `Codable` would throw on an
   unknown raw value, decode it as `String?` and map with `FoodMeasure(rawValue:) ?? .grams`.
   **Prefer the `String?` form**; a thrown decode on one catalogue document fails the whole search.
3. **`FavouriteFoodDTO`** (`:15-46`, init at `:56`, `asDomain`) — same as `FoodItemDTO`. It is a
   full snapshot of `FoodItemDomain`; `ARCHITECTURE.md` § 1.4 already records (for `portions`) that
   leaving a copied field out silently drops it.
4. **`FoodConsumedDomain`** (`Core/Models/FoodConsumedModel.swift:11-33`) — it uses the synthesized
   memberwise init, so add **`var measure: FoodMeasure = .grams` as the last stored property**
   (after `mealTypeId`). It must be `var`: a `let` with an initial value is excluded from the
   memberwise init. Then **update `copy(...)` (`:38-73`) to pass `measure: measure`** — this is the
   one place the compiler will not catch, and missing it relabels an edited milk entry as grams.
5. **`FoodConsumedDTO`** (`:11-48`) — `let measureUnit: String?` (same reasoning as 2),
   `case measureUnit = "measure_unit"`, `asDomain()` (`:53`) maps with `?? .grams`. The compiler
   then flags all three constructors:
   - `FoodConsumedDTO.init(food:mealTypeId:)` (`:79`) → `food.measure.rawValue`
   - `SaveFoodConsumedUseCase.swift:33` → `item.measure.rawValue`
   - `UpdateFoodConsumedUseCase.swift:38` → `food.measure.rawValue`
6. **`MigrateAnonymousDataUseCase`** — no code change. It loads and re-writes whole DTOs
   (`:42-49`, `:75-76`), so the field rides along. `PendingMergeSnapshot` on disk encodes the same
   DTOs; the field is optional, so a snapshot written by the previous app version still decodes.

### Form (`FoodItemFormFields`, all three screens)

`FoodItemFormInput` (`AddFoodSheetViewModel.swift:11-29`):

- add `var measure: FoodMeasure = .grams`;
- add `var isWeightInThousands = false` — client-only, never stored. When true, `weightOfProduct`
  holds kg / l;
- `init(item:)` (`:32`): `measure: item.measure`; if `item.weight >= 1000`, set
  `isWeightInThousands = true` and `weightOfProduct = item.weight / 1000`;
- `asFoodItemDomain` (`:55`): `weight: isWeightInThousands ? weightOfProduct * 1000 :
  weightOfProduct`, `measure: measure`.

Put the ≥ 1000 rule in one static helper on `FoodItemFormInput` and use it from both `init(item:)`
and `applying(_:)` below, so a photo-filled 1500 ml and a reloaded 1500 ml display identically.

`FoodItemFormFields` (`Components/FoodItemFormFields.swift`):

- a segmented `Picker` bound to `formInput.measure` (`g` | `ml`) **above** the weight field;
- the weight field (`:26-31`) gets a trailing menu picker over `isWeightInThousands` showing
  `g`/`kg` or `ml`/`l` per the measure, replacing the fixed `unit:` suffix; toggling it converts
  `weightOfProduct` (×/÷ 1000) so the physical amount is preserved — same rule as
  `FoodQuantityViewModel.onUnitChanged(from:to:)`;
- the calories title (`:39`) becomes `L10n.AddFood.fieldCaloriesPer100g` or the new
  `fieldCaloriesPer100ml`, per `formInput.measure`;
- nutrient fields (`:44-91`) keep `L10n.Common.unitGrams`. Do not change them.

`FoodPortionsSection.swift:107` — the portion amount suffix follows the measure; the section needs
the measure passed in from `FoodItemFormSections`.

`FoodItemFormField` (`:10-12`) gains `case measure` so a photo-set measure is highlighted like any
other pre-filled field. Nothing in the app target iterates `allCases` (checked); grep
`KalorieTests` for `FoodItemFormField` before relying on that.

### Parser (`Core/NutritionLabelRecognition/`)

- `NutritionLabelReading` (`NutritionLabelReading.swift:10-50`) gains `var measure: FoodMeasure?`
  (init param defaulting to `nil`), and `recognizedFields` (`:71` area) inserts `.measure` when it
  is non-nil.
- `NutritionLabelParser.parse` (`:48`) sets it, in this order:
  1. the per-100 column header found by `perHundredColumnX` (`:223`): contains `100ml` →
     `.millilitres`, `100g` → `.grams`;
  2. else, for the linear fallback, the same test over the compact text in `hasNutritionContext`
     (`:81-86`) — only when exactly one of `100ml` / `100g` occurs;
  3. else, the unit `packageWeight` matched (`:288-305`): `ml`/`l` → `.millilitres`, `g`/`kg` →
     `.grams`. This means `weightValue` must return the unit to the caller, which it already does.
  4. else `nil`. If step 1 or 2 and step 3 disagree, step 1/2 wins — the nutrition basis is what
     the numbers mean; the package line is only a hint.
- `FoodItemFormInput.applying(_:)` (`AddFoodSheetViewModel.swift:86-130`): fill `measure` only when
  the form's measure is still `.grams` (its default) and the reading's is non-nil — the same
  "only into a field still at its default" rule design 0010 decided for every field. A user who
  explicitly picked grams and then photographs a `100 ml` label gets ml; accepted, the highlight
  shows it. Apply the ≥ 1000 helper when filling `weightOfProduct`.

### Logging and display

| Where | Now | Becomes |
|---|---|---|
| `FoodQuantityView.swift:136-144` `label(for:)` | `1 g` / `100 g` / `name (N g)` | takes the item's measure; `1 ml` / `100 ml` / `name (N ml)` |
| `FoodQuantityView.swift:82` | `Množství` | unchanged (unit-neutral already) |
| `FoodPortionsManagerView.swift:40, 66` | grams suffix | item's measure |
| `FoodConsumedView.swift:27` | `weight.formattedGrams(fractionDigits: 0)` | amount formatted with `foodConsumed.measure` |
| `FoodConsumedDetailView.swift:52` | literal `Text("g")` | `viewModel.food.measure` symbol |
| macro rows (`FoodQuantityView.swift:35-38`, `FoodConsumedDetailView.swift:82-100`, `MealSectionMacroView`, `MacroSummaryView`) | `formattedGrams` | **unchanged** — nutrient grams |

Add a sibling to `Double.formattedGrams(fractionDigits:)` (`Core/Extensions/Double+Extension.swift:12`)
that takes a `FoodMeasure`, rather than `switch`ing in each view. **Do not replace `formattedGrams`
globally** — 20+ of its call sites are nutrient amounts and must stay grams.

`FoodQuantityUnit` cases are not renamed (see Non-goals); only their labels depend on the measure.

### Localization

New keys in `iOS/Kalorie/Resources/Localizable.xcstrings`, exposed through `L10n`
(`Resources/Localization/L10n.swift`, per ADR 0019 — never a raw literal in `Text`):

| Key | cs | en |
|---|---|---|
| `common_unit_millilitres` | ml | ml |
| `common_unit_kilograms` | kg | kg |
| `common_unit_litres` | l | l |
| `foodQuantity_unit_millilitres` | 1 ml | 1 ml |
| `foodQuantity_unit_hundredMillilitres` | 100 ml | 100 ml |
| `addFood_field_caloriesPer100ml` | Kalorie na 100 mililitrů | Calories per 100ml |
| `addFood_field_measure` | Měří se v | Measured in |

### Why this does not contradict design 0010

Design 0010 is frozen and its decisions stand: `100 ml` is still accepted as the per-100 column and
`l` is still ×1000. This design only **records** what that already meant (ml≡g numerically) and
adds a label so the number is no longer misread as grams. Per `CLAUDE.md`, the unstated density
assumption in design 0010 is a finding; it is closed by this design's *core rule*, not by editing
design 0010.

## Tests

XCTest, fakes, `makeSUT()`, per `CLAUDE.md`. Each test states **why** it matters:

- `FoodItemDTO` decodes a document **without** `measure_unit` as `.grams` — every catalogue item
  written before this design must stay grams.
- `FoodItemDTO` decodes an **unknown** value (`"litres"`) as `.grams` without throwing — one bad
  document must not fail a whole search.
- `FoodConsumedDomain.copy(weight:)` preserves `.millilitres` — an edited milk entry must not turn
  into grams.
- `UpdateFoodConsumedUseCaseTests`: the written DTO carries the food's measure.
- `SaveFoodConsumedUseCaseTests`: the written DTO carries the item's measure.
- `AddFavouriteFoodUseCaseTests` / `FetchFavouriteFoodsUseCaseTests`: measure round-trips.
- `FoodItemFormInputTests`: 1.5 l → domain `weight == 1500, measure == .millilitres`;
  `init(item:)` with `weight 1500` → `1.5` with `isWeightInThousands`; `weight 999` → not in
  thousands.
- `NutritionLabelParserTests`: `100 ml` header → `.millilitres`; `100 g` → `.grams`; no header and
  package `1 l` → `.millilitres`; header `100 g` with package `500 ml` → `.grams`; nothing → `nil`.
  The existing oil-bottle fixture (`test_parse_realOilBottleLabel_neverReadsANumberOutOfAnUnrelatedLabel`)
  additionally asserts `.millilitres`.
- `FoodItemFormInput.applying`: a reading with `.millilitres` sets the measure and returns
  `.measure` in the changed set; a form already at `.millilitres` is not overwritten by `.grams`.

Verify by hand (no test covers UI): create a millilitre item from a drink label on a device; log
250 ml; the Dashboard row reads `250 ml`, the detail screen's field suffix is `ml`, macros still
read `g`; edit to 300 ml and confirm it still reads `ml`.

## Alternatives considered

- **Client-only unit input (convert to grams on save, store nothing).** Rejected: `weight` feeds no
  calculation, so it changes nothing a user sees after saving, and it cannot round-trip ml vs g.
- **A real density field and g↔ml conversion.** Rejected: labels declare per 100 ml for liquids and
  users measure liquids in ml, so staying in the label's own unit is exact; a density would be one
  more number to OCR or guess, and would only matter for mixing units, which nothing here does.
- **Measure only on `foodItems`, resolved at display time.** Rejected: nutrition is denormalised
  into every referencing collection (ADR 0009), and `foodConsumed` must not read `foodItems` to
  render a row.

## Cross-cutting concerns

- **Second client:** the whole contract is *Firestore contract* above — one optional string, absent
  = grams, numbers never converted.
- **Staleness (A1-7):** a maintainer switching an item from grams to ml in the catalogue editor
  does not relabel existing favourites or logged entries — same accepted staleness as every other
  snapshot field.

## Risks

| Risk | Consequence | Mitigation |
|---|---|---|
| `copy(...)` or a DTO init path misses `measure` | a ml entry silently reads grams after an edit | DTOs without defaults (compiler), explicit `copy` test |
| Someone replaces `formattedGrams` globally | nutrient grams shown as `ml` | called out in *Logging and display*; macro rows listed as unchanged |
| Parser mis-detects the measure | wrong label on a submitted item | highlighted as pre-filled, maintainer reviews every submission (design 0009) |

## Rollout

1. Rules + indexes (`firebase deploy --only firestore:rules,firestore:indexes`), Rules Playground check.
2. App change in one PR.
3. After merge, update `ARCHITECTURE.md` (living): § 1.3 (the core rule), § 1.4 (field on the four
   collections), § 1.6 (`validFoodItem` clause), § 4.2 (labels follow measure), § 7.5 (parser sets
   measure); add this design to § 1/§ 4/§ 7 *Read first*; remove the TODO line; set this doc's
   Status and fill *Outcome*.

## Outcome

Shipped as designed, per commit `0d855a6` — `FoodMeasure`, the four DTOs, the form, the parser and
the display sites all match this document; see `ARCHITECTURE.md` § 1.3/1.4/1.6, § 4.2 and § 7.5.
The two hand-verification steps this document calls out (Rules Playground, on-device logging) are
still owed and are not tracked by any automated check.
