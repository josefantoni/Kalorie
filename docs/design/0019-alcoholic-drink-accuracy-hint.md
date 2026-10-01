# Design: Alcoholic-drink accuracy hint

- **Status:** Implemented in `ae98641` (backend), `482f658` (KMP), `efde8dc` (localisation), `85a3e9f` (iOS) and `b940384` (Android)
- **Scope:** Backend, Cross-platform, iOS, Android
- **Date:** 2026-10-01

## Context and scope

The catalogue holds beers, and their nutrition values are less reliable than those of other items.
EU Regulation 1169/2011, Art. 16(4), exempts drinks above 1.2 % alcohol by volume from the mandatory
nutrition declaration. Producers may declare energy only, and many do no more than that. Catalogue
beers therefore carry carbohydrates and protein taken from secondary sources, or zeros where the
label gave nothing. The user sees those numbers with the same confidence as a yoghurt's.

Nothing in the schema says that an item is alcoholic. This document adds that information to the
data model and shows a hint wherever the user reads an alcoholic item's values. It covers the
Firestore field, how the field gets filled, and the hint UI on both clients.

## Goals

- A catalogue item can record its alcohol content, and a document without it stays valid.
- Wherever the app shows a food's nutrition values for logging or editing, an alcoholic drink gets
  an "Alcoholic drink" label with an info button. Tapping the button explains why the values may be
  inaccurate.
- Users see no new field when they add or submit a food. Only the maintainer sets the value.
- iOS and Android decide which items are alcoholic in exactly the same way.

## Non-goals

- **Energy from alcohol.** [ADR 0007](../adr/0007-derive-missing-energy-kj-from-macros.md) derives a
  missing `energy_kj` from macros and leaves out alcohol, which undercounts beer badly. The new
  field would make it possible to add alcohol energy (≈ ABV × 0.789 × 29 kJ per 100 ml). That is a
  separate change to a calculation every client shares. It is not part of this design.
- **The hint in search result rows.** It was decided against: a row is a single tap target, and the
  hint matters where the values are read, not where the item is picked.
- **The hint on created meals** (`myCreatedMeals`) and on the meal's ingredients. A meal is the
  user's own composition, and its values are a sum of snapshots.
- **ABV in the data export** ([design 0014](0014-data-export.md)).
- **A general food category / tag field.** Alcohol is the only case today.
- **ABV on external (Open Food Facts) items** ([ADR 0012](../adr/0012-external-food-is-surfaced-never-imported.md)).
  OFF has `alcohol_100g`, but mapping it is a separate change to the OFF mapping and its fixture.

## Design

### The core rule

An item is an alcoholic drink when **`alcoholByVolume > 1.2`**. The threshold is the EU limit above
which the label need not carry nutrition values. That limit is the reason the values are
unreliable, so it is also the right trigger for the hint. A non-alcoholic beer (≤ 0.5 %) has a full
mandatory label and gets no hint, even when the maintainer records its 0.5 %.

The rule lives in **MacroKit**, next to `energyKJFromMacros` (`MacroKit/src/commonMain/kotlin/Macros.kt`):

```kotlin
const val ALCOHOL_NUTRITION_LABEL_EXEMPTION_ABV = 1.2
fun isAlcoholicDrink(alcoholByVolume: Double?): Boolean =
    alcoholByVolume != null && alcoholByVolume > ALCOHOL_NUTRITION_LABEL_EXEMPTION_ABV
```

[ADR 0039](../adr/0039-swift-only-rules-move-into-kmp-or-share-golden-vectors.md) §1 applies:
primitives in and out, no new dependency, no domain type. With one implementation there is no
fixture. MacroKit's `commonTest` covers `nil`, `0.5`, `1.2` (false: the limit is exclusive) and
`1.21`.

### Firestore contract (`Backend`, `Cross-platform`)

One new optional number field, **`alcohol_by_volume`**, a percentage in `(0, 100]`, on:

| Collection | DTO | Absent means |
|---|---|---|
| `foodItems` | `FoodItemDTO` | not recorded, so no hint |
| `foodItemSubmissions` (`item` map) | nested `FoodItemDTO`, carried automatically | not recorded |
| `users/{uid}/favouriteFoods` | `FavouriteFoodDTO` | not recorded |
| `users/{uid}/foodConsumed` | `FoodConsumedDTO` | not recorded |

The two snapshot collections carry the field because the consumed-entry detail reads its snapshot,
not the catalogue (ADR 0009, denormalised snapshots). It is not added to `myCreatedMeals` or its
ingredients (see Non-goals), `foodItemPortions` or `mealTypes`. This is the same set of collections
that `measure_unit` was added to ([design 0011](0011-food-measure-grams-or-millilitres.md)), and
every path that copies `measure_unit` must copy this field too.

A second client must decode an absent field as `null`, write the field on every write to those four
collections, and treat zero as absent. Zero is never written: the editor stores an empty or zero
input as absent.

**Rules.** Append to `validFoodItem` (`backend/firestore.rules:14-33`):

```
&& (!('alcohol_by_volume' in data) ||
    (data.alcohol_by_volume is number && data.alcohol_by_volume > 0 && data.alcohol_by_volume <= 100))
```

This covers `foodItems` and `foodItemSubmissions`. The `users/**` subtree has no field validation
and gets none. The current rules ignore unknown keys, so rules and apps can ship in either order.
Deploy the rules first anyway. Add cases to `firestore-rules-tests/firestore.rules.test.js`:

- a maintainer `foodItems` write with `12` is allowed
- writes with `0`, `-1`, `101` and `"12"` are denied
- a write without the field is allowed

**Validation fixture.** `FoodItemValidation` (iOS `Core/Models/FoodItemValidation.swift`, Android
`core/models/FoodItemValidation.kt`) mirrors `validFoodItem` through
`fixtures/food-item-validation-cases.json`. Add the same range check to both, with fixture cases
that match the rules tests, so the client rejects a value before Firestore would.

**Indexes.** The field is never queried. Add
`{ "collectionGroup": "foodItems", "fieldPath": "alcohol_by_volume", "indexes": [] }` to
`fieldOverrides` in `backend/firestore.indexes.json`. This follows the rule in `ARCHITECTURE.md`
§ 1.6 that only queried `foodItems` fields are indexed.

**No backfill script.** See *Filling the field*.

### DTO and domain changes (iOS and Android)

Follow the `measureUnit` precedent from design 0011 § *DTO and domain changes*. The same gotcha
applies: **DTOs get no default, domains do**. That way the compiler finds every DTO construction,
and the domain call sites in tests keep compiling.

1. **`FoodItemDomain`** (iOS `Core/Models/FoodItemModel.swift:16`, Android
   `core/models/FoodItemModel.kt:5`): add `alcoholByVolume: Double? = nil` as the last parameter.
2. **`FoodItemDTO`** (iOS `Core/Networking/FireStone/FoodItemDTO.swift`, Android
   `core/networking/FoodItemDTO.kt`): add `alcoholByVolume: Double?` with key `alcohol_by_volume`.
   Map it both ways in `init(item:)` / `asDomain()`.
3. **`FavouriteFoodDTO`**: the same as 2. It is a full snapshot of `FoodItemDomain`, and
   `ARCHITECTURE.md` § 1.4 records that leaving a copied field out silently drops it.
4. **`FoodConsumedDomain`** (iOS `Core/Models/FoodConsumedModel.swift:11`, Android
   `core/models/FoodConsumedModel.kt:8`): add the field last with a default of `nil`. On iOS it must
   be a `var` so it stays in the memberwise init. **Update `copy(...)`.** The compiler does not catch
   this one, and missing it drops the hint after an entry is edited.
5. **`FoodConsumedDTO`**: add the field and its key. The compiler then flags every constructor, the
   same ones `measure_unit` touches. On both platforms these are `init(food:mealTypeId:)` /
   the secondary constructor, `SaveFoodConsumedUseCase` and `UpdateFoodConsumedUseCase`.
6. **Approval and catalogue edit.** `ApproveSubmissionUseCase` and `UpdateFoodItemUseCase` on both
   platforms build the stored `FoodItemDTO` from the form input or the domain. Check that the field
   reaches the written document. Recent fixes to these same paths (keeping a barcode-less item's
   UUID on approval) show they rebuild the item rather than pass it through.
7. **`MigrateAnonymousDataUseCase`** needs no change. It rewrites whole DTOs, so the field rides
   along.

### Filling the field (maintainer only)

- **`FoodItemFormInput`** (iOS `Features/AddFoodSheet/AddFoodSheetViewModel.swift:11`, Android
  `features/addfoodsheet/FoodItemFormInput.kt:12`): add an `alcoholByVolume` text value. It maps to
  `Double?`, and an empty input or zero maps to `nil`.
- **`FoodItemFormSections`** (iOS `Components/FoodItemFormSections.swift`, Android
  `components/FoodItemFormSections.kt`): add `showsAlcoholByVolumeField: Bool = false`. When it is
  true, render one optional numeric field, "Alcohol (% vol)", in the same style as the other numeric
  fields, below the nutrition fields.
- Pass `true` only from **`ModerationReviewView`** and **`ModerationCatalogueEditorView`** on both
  platforms. `AddFoodSheetView` keeps the default, so the user's add and submit form does not change.
- **Existing beers** are filled by hand in the catalogue editor (moderation § 7). There are few of
  them, and an Admin SDK script with a hand-kept id → ABV map is more code than the edits it saves.
  If the count grows, a script in `scripts/` next to the backfills is the fallback.
- The nutrition-label parser ([design 0010](0010-nutrition-label-photo-prefill.md)) does **not**
  read ABV. "% vol" on a beer label is usually away from the nutrition table and outside the
  photographed area.

### Display

Show the hint on the screens that display an item's values for logging or editing:

| Screen | iOS | Android | Source of the field |
|---|---|---|---|
| Logging an item (quantity screen) | `Features/FoodQuantity/FoodQuantityView.swift` | `features/foodquantity/FoodQuantityView.kt` | `FoodItemDomain` (from catalogue or favourite) |
| Editing a logged entry | `Features/Dashboard/FoodConsumedDetailView.swift` | `features/dashboard/FoodConsumedDetailView.kt` | `FoodConsumedDomain` snapshot |

The hint is a compact capsule button in the header of the Nutrition section, aligned to the
trailing edge of the rows and only as wide as its content:

```
Plzeň 12°                                      ♡
...
NUTRITION        [🍺 Alcoholic drink · 4.4 %  ⓘ]
Energy                                   215 kcal
```

- The capsule matches the toolbar's confirm button ("Add" / "Save"): `.buttonStyle(.glass)` with
  `.controlSize(.small)` on iOS, a `FilledTonalButton` with the default pill shape on Android.
  On iOS the header measures the trailing edge of a nutrition value and pads itself to it, so the
  capsule lines up with the row content below it. A fixed inset does not work: the system margins
  of headers and rows differ by device settings, not only by screen width.
  On Android the header is a row with the same 16 dp horizontal padding as the value rows, so no
  measuring is needed.
- The icon is SF Symbol `wineglass` on iOS and `Icons.Outlined.LocalBar` on Android, followed by
  `info.circle` / `Icons.Outlined.Info`.
- The ABV is shown with the device locale's decimal separator.
- The whole button is the tap target, not just the `ⓘ`. A tap opens a popover with the
  explanation, not an alert: the text is supplementary and needs no decision, and an alert would
  block the screen for it.
  - **iOS:** `.popover` with `.presentationCompactAdaptation(.popover)`, so that it stays a bubble
    on iPhone instead of becoming a sheet. The text has a fixed width and its ideal height, because
    the popover otherwise sizes to a single line and truncates it.
  - **Android:** Material 3 `TooltipBox` with a `RichTooltip`, shown on click.
- The button is visible only when `isAlcoholicDrink(alcoholByVolume)` returns true. The view models
  expose one boolean plus the formatted ABV and do not compare numbers themselves.

**Copy (cs / en)**, through `localisation/` and `scripts/generate-strings.js`:

- Label: "Alkoholický nápoj" / "Alcoholic drink"
- Popover: "Alkoholické nápoje nemusí na etiketě uvádět nutriční hodnoty. Sacharidy a bílkoviny
  proto mohou pocházet z jiných zdrojů nebo chybět." / "Alcoholic drinks don't have to list
  nutrition values on the label. Carbohydrates and protein may therefore come from other sources
  or be missing."

### Tests

- MacroKit `commonTest`: the threshold, as listed in *The core rule*.
- Rules tests and fixture cases: as listed in *Firestore contract*.
- DTO round trip on both platforms: `alcohol_by_volume` survives `FoodItemDTO`, `FavouriteFoodDTO`
  and `FoodConsumedDTO`, and a document without the key decodes as `nil`. **Why:** a dropped copy
  silently loses the hint and nothing else fails.
- `FoodConsumedDomain.copy(...)` keeps the field. **Why:** the compiler does not catch it (point 4
  above).
- Approve and catalogue-edit use cases write the value the maintainer entered, and an empty input
  writes no key.
- Each of the two detail view models: the hint flag is true for 4.4 and false for `nil` and 0.5.

## Alternatives considered

- **`is_alcoholic: Bool`.** Rejected. A switch has to be judged, while ABV is copied from the label.
  A boolean also cannot tell a 0.5 % non-alcoholic beer, whose label is complete, from a 4 % one.
  ABV also opens the energy correction from the Non-goals; a boolean does not.
- **A `category` enum.** Rejected as speculative. Alcohol is the only category that changes
  behaviour today, and an enum would need its own unknown-value policy
  (compare [ADR 0025](../adr/0025-food-item-kind-discriminates-entry-origin.md)).
- **An ABV field in the user's add-food form.** Rejected. Every user would see a field that matters
  for a few dozen items, and moderation already puts a maintainer in front of every catalogue write.
- **A 0.5 % threshold** (the usual "non-alcoholic" limit). Rejected. Between 0.5 % and 1.2 % the
  label must still carry nutrition values, so the reason for the hint does not apply there.
- **The hint in search rows.** Rejected (see Non-goals).

## Cross-cutting concerns

- **Migration:** none. An absent field means no hint. Favourites refresh their snapshot from the
  catalogue and pick up the value. A `foodConsumed` entry logged before its beer got an ABV never
  shows the hint, which is acceptable for a past entry.
- **Store review:** an informational label on existing content. No age gate is triggered: the app
  already lists beers, and this does not promote alcohol.
- **Privacy:** none. It is catalogue data, and on user documents only a copy of catalogue data.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| The maintainer forgets to fill ABV on a newly approved beer | No hint on that item | The field sits in the review form the maintainer already fills; can be corrected later in the editor |
| A copy path drops the field | Hint disappears after favouriting or editing an entry | DTO round-trip and `copy` tests (see *Tests*) |
| A client decodes `alcohol_by_volume` as a non-optional value | The whole catalogue search fails on old documents | The field is optional in every DTO, and a test decodes a document without the key |

## Rollout

1. Rules, rules tests and the index override (`backend/`, `firestore-rules-tests/`), then deploy.
2. MacroKit rule and validation fixture (KMP / `fixtures/`). Check that both clients build.
3. iOS: DTOs, domains, form field in moderation, hint. One commit per platform.
4. Android: the same.
5. Fill ABV on the existing beers through the catalogue editor.

## Outcome

*To be filled in once shipped.*
