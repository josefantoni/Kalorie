# Architecture overview

A description of what exists today. This document is **living** — update it when the structure
changes. It is not a plan and not a history; for those see `docs/design/` and `docs/adr/`.

Written retroactively, area by area, following `docs/README.md` → *Documenting code that
already exists*. All five areas are covered; the audit findings each one produced are in
`TODO.md`.

**This document is the entry point into `docs/`.** Each section opens with the ADRs and design
docs covering its area, so reading one section gives the full reading list for that area without
opening anything else. Design docs are frozen, which means they are not edited — not that they
are out of date: a decision recorded in one is still in force.

## Contents

1. [Data layer and Firestore model](#1-data-layer-and-firestore-model)
2. [Food search and catalogue](#2-food-search-and-catalogue)
3. [Dashboard and meal types](#3-dashboard-and-meal-types)
4. [Food entry flow](#4-food-entry-flow)
5. [Cross-cutting concerns](#5-cross-cutting-concerns)

---

## 1. Data layer and Firestore model

**Scope:** `Backend` for the collection layout, field names and security rules;
`Cross-platform` for the encoding conventions; `iOS` for the provider shape.

**Read first:** [design 0001](design/0001-user-authentication.md) (the `users` collection and the
first version of the security rules), [design 0003](design/0003-favourite-foods.md)
(`favouriteFoods`, and why `food_item_id` is required), [design 0006](design/0006-own-daily-meals.md)
(`myCreatedMeals`, and what `food_item_id` means once a meal can be logged).

### 1.1 Layering

```
View → ViewModel → UseCase → FirestoreDataProviderProtocol → Firestore
                          ↘ AuthProviderProtocol (userId)
```

There is no repository or store between the use case and the provider. A use case that touches
Firestore owns the whole operation: it resolves the current `userId`, builds the DTO, calls one
provider method, and maps the result to a domain type. This is why almost every persistence use
case carries the same two dependencies (`dataProvider`, `authProvider`) and the same opening
line, `guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }`.

Three kinds of type take part:

| Type | Lives in | Role |
|---|---|---|
| **DTO** (`…DTO`) | `Core/Networking/FireStone/` | The Firestore wire shape. Owns `CodingKeys`, therefore owns the field names in the database. |
| **Domain** (`…Domain`) | `Core/Models/` | What the app works with. Non-optional, no Firestore or `Codable` coupling. |
| **Use case** | `Core/UseCases/` | The mapping between the two, plus validation. |

DTO → domain conversion lives on the DTO as `asDomain()`; domain → DTO lives in a DTO `init`.
`FoodItemDTO` is the exception — it has neither, and its mapping is inlined at each call site
(`SearchFoodItemsUseCase`, `CreateFoodItemUseCase`, `FetchFoodItemByBarcodeUseCase`). See
finding **A1-10**.

### 1.2 Collection layout

Collection paths are centralised in `Constants.Firestore` and are the only place a path string
is written.

```
foodItems/{barcode}                        shared, global catalogue
users/{userId}                             profile document (displayName, email)
users/{userId}/mealTypes/{intId}           the user's meal windows
users/{userId}/foodConsumed/{uuid}         logged entries
users/{userId}/favouriteFoods/{barcode}    explicitly favourited catalogue items
users/{userId}/myCreatedMeals/{uuid}       user-composed meals
```

Everything under `users/{userId}` is private to that user. `foodItems` is shared by every user
and every future client.

Document IDs are meaningful, not random:

- `foodItems` and `favouriteFoods` are keyed by the **barcode**, which is also the item's `id`
  field. `CreateFoodItemUseCase` enforces that the id is all digits.
- `foodConsumed` and `myCreatedMeals` are keyed by a client-generated `UUID().uuidString`.
- `mealTypes` is keyed by the stringified integer id — see
  [ADR 0010](adr/0010-client-assigned-integer-meal-type-ids.md).

The `users/{userId}` profile document is written **only** by `SignInWithAppleUseCase` and
`SignInWithGoogleUseCase`. An anonymous user therefore has subcollections but no parent
document — which is legal in Firestore, and means the parent document's existence must never be
used as a signal that a user exists.

### 1.3 Encoding conventions

These are the contract a second client has to match exactly.

- **Field names are `snake_case`** in Firestore and `camelCase` in Swift, bridged by explicit
  `CodingKeys`. Fields whose two spellings coincide (`id`, `name`, `weight`, `date`, `fat`,
  `protein`, `salt`, `fiber`, `grams`, `calories`, `carbohydrate`, `ingredients`) are listed on
  the first `case` line unrenamed.
- **Dates are `TimeInterval`** — a `Double` of **seconds** since 1970, not a Firestore
  `Timestamp` and not milliseconds. See
  [ADR 0008](adr/0008-dates-as-epoch-seconds-not-firestore-timestamp.md).
- **`id` is stored both as the document ID and as a field** inside the document, so a query can
  filter on it. `IsFavouriteFoodUseCase` and `CreateFoodItemUseCase` both use the field rather
  than a document read; see finding **A1-9**.
- **Nutrition is denormalised** into every collection that references a food, rather than
  joined from `foodItems` at read time. See
  [ADR 0009](adr/0009-denormalised-nutrition-snapshots.md).
- **Three fields are optional on read** — `energy_kj`, `fat_saturated`, `fiber` — because
  catalogue documents predating them exist. `energy_kj` is derived from macros when absent
  ([ADR 0007](adr/0007-derive-missing-energy-kj-from-macros.md)); the other two fall back to
  `0`, which finding **A1-4** flags as unsafe.

### 1.4 Per-collection shape

**`foodItems`** (`FoodItemDTO`) — the catalogue. Values are **per 100 g**
(`calories_per_hundred_grams`) except `weight`, which is the package weight the entry was read
from. Carries `cz_name_lowercase` / `eng_name_lowercase`, written by `CreateFoodItemUseCase`
purely so the prefix-range search in `SearchFoodItemsUseCase` has something case-insensitive to
range over.

**`foodConsumed`** (`FoodConsumedDTO`) — one logged entry. Values are **absolute for the logged
weight**, already scaled, not per 100 g; `calories` is an `Int`. `food_item_id` points back at
the catalogue entry and is required (no optional, no backfill — see `TODO.md`). This DTO's
field names diverge from the rest of the model (`carbohydrate_sugar` vs.
`carbohydrate_pure_sugar`, `fat_unsaturated` vs. `fat_unsaturated_fatty_acids`) and it drops
`fat_saturated` and `energy_kj` entirely — findings **A1-8** and **A1-5**.

**`mealTypes`** (`MealTypeDTO`) — `startMinutes` / `endMinutes` are minutes since midnight
(0–1439), stored **unrenamed in camelCase**, unlike every other DTO. A window may wrap past
midnight (`endMinutes < startMinutes`); `MealKit` owns that arithmetic.

**`favouriteFoods`** (`FavouriteFoodDTO`) — a full copy of the `FoodItemDomain` plus
`favourited_at`. Fetched ordered by `favourited_at` descending, limit 50.

**`myCreatedMeals`** (`MyCreatedMealDTO`) — `ingredients` is an **array of nested maps**
(`MyCreatedMealIngredientDTO`), each a nutrition snapshot plus `grams`. Fetched ordered by
`updated_at` descending, limit 50. `MyCreatedMealDomain.asFoodItem()` collapses the meal into a
synthetic `FoodItemDomain` whose per-100 g values are the gram-weighted mean of the
ingredients (`MacroKit.weightedMeanPerHundredGrams`), which is what lets a saved meal appear in
search and be logged like any catalogue food.

### 1.5 The provider

`FirestoreDataProviderProtocol` is a flat list of one method per **query shape**, not a query
builder:

| Method | Firestore call |
|---|---|
| `loadAsync(from:)` | `collection.getDocuments()` — whole collection |
| `loadFromServerAsync(from:)` | same, `source: .server` — bypasses the offline cache |
| `loadAsync(from:where:isGreaterThanOrEqualTo:isLessThan:)` | numeric range, used for date windows |
| `loadAsync(from:where:hasPrefix:limit:)` | range over `[prefix, prefix + )` — prefix search |
| `loadAsync(from:where:isEqualTo:)` | equality, `limit(1)`, returns `T?` |
| `loadAsync(from:orderBy:descending:limit:)` | ordered page |
| `saveAsync(_:to:)` | `addDocument` — Firestore-generated ID |
| `setAsync(_:id:in:)` | `document(id).setData` — full overwrite |
| `batchSetAsync(_:in:)` | one `WriteBatch` |
| `deleteAsync(id:from:)` | `document(id).delete()` |

Consequences worth knowing before adding a method:

- **All reads are one-shot.** Nothing in the app uses `addSnapshotListener`, so no screen
  updates itself when the data changes underneath — the Dashboard's foreground refresh exists
  because of this.
- **Reads default to Firestore's own source resolution**, which serves the offline cache when
  the server is unreachable. `loadFromServerAsync` exists solely so
  `ConfirmMealTypesEmptyUseCase` can tell "this user genuinely has no meal types" from "the
  cache has not been populated yet" before overwriting them with defaults.
- **There is no read-single-document-by-ID method.** Code that needs one document issues an
  equality query instead (finding **A1-9**).
- **`setAsync` replaces the document**, it never merges. Every writer must therefore send every
  field it wants to keep.
- **Every method logs its request and full response body under `#if DEBUG`**, via the
  file-private `log(_:)` at the bottom of `FirestoreDataProvider.swift`.

### 1.6 Security rules

`Kalorie/firestore.rules`, deployed via `Kalorie/firebase.json`. In full:

- `users/{userId}` and everything beneath it: read and write require
  `request.auth.uid == userId`. Anonymous users are authenticated users, so this covers them.
- `foodItems`: read and write require only `request.auth != null` — see
  [ADR 0011](adr/0011-foodItems-writable-by-any-authenticated-client.md).
- Everything else is denied by Firestore's default.

There is **no `firestore.indexes.json`**. Every query the app issues today is a single-field
range, equality or order, all of which Firestore indexes automatically, so no composite index
is configured or needed. The cost of that automatic indexing on the catalogue is finding
**A1-12**.

---

## 2. Food search and catalogue

**Scope:** `Backend` for the two lowercase index fields; `Cross-platform` for the search and
fallback behaviour; `iOS` for the scanner.

**Read first:** [design 0003](design/0003-favourite-foods.md) (favourites hoisted into the result
list, and why an OpenFoodFacts item outside the catalogue is accepted),
[design 0006](design/0006-own-daily-meals.md) (a created meal appearing in the same search).

### 2.1 The four ways a food is found

Everything in this area answers one question — *which `FoodItemDomain` is the user about to
log?* — through four paths, all converging on `AddFoodSheetViewModel.onSelectFoodItem`:

| Path | Use case | Source |
|---|---|---|
| Typed search, local | `SearchFoodItemsUseCase` | `foodItems` prefix query |
| Typed search, fallback | `SearchFoodExternallyUseCase` | OpenFoodFacts `/cgi/search.pl` |
| Barcode, local | `FetchFoodItemByBarcodeUseCase` | `foodItems` equality query |
| Barcode, fallback | `FetchFoodByBarcodeExternallyUseCase` | OpenFoodFacts `/api/v2/product/{code}` |

Both fallbacks are strictly *second*: the external call only happens after the local one comes
up empty. Neither writes what it found back into the catalogue — see
[ADR 0012](adr/0012-external-food-is-surfaced-never-imported.md).

A fifth path exists that is not a search at all: the *add a new food* form, reached from the
carrot button in the sheet's toolbar. It is the only writer to `foodItems`
(`CreateFoodItemUseCase`), and its `scannedCode` field is typed by hand — the scanner does not
populate it.

### 2.2 Local search

`SearchFoodItemsUseCase` is a case-folded prefix range over `cz_name_lowercase` and
`eng_name_lowercase`, ten results each, run as two concurrent `async let` queries and
de-duplicated by id. The mechanics and the limits are recorded in
[ADR 0013](adr/0013-prefix-search-over-lowercased-name-fields.md).

Ranking happens **above** the use case, in `AddFoodSheetViewModel.displayedResults`, which is a
pure computed property over three already-loaded lists:

1. matching **my created meals** (`asFoodItem()`, prefix on the meal name),
2. matching **favourites** not already listed as a meal,
3. the local search results, minus anything already listed.

Favourites and meals are loaded once in `onAppear`, not per keystroke, so this re-ranking costs
nothing. With an empty query, `displayedResults` returns `localFoodItems`, which is empty — the
Favourites section in the view renders from `favouriteFoods` directly.

### 2.3 Search orchestration

`onSearchTextChanged` is driven by `.task(id: viewModel.searchText)`, so SwiftUI cancels and
restarts it on every keystroke. Debouncing is a `Task.sleep(for: .milliseconds(300))` at the
top: a cancelled sleep throws, and the `catch` returns, which is what makes the debounce work.

The external fallback is then gated twice:

```swift
guard displayedResults.isEmpty && searchText.count >= 3 else { … }
```

`displayedResults`, not `localFoodItems` — so one matching favourite or one matching saved meal
suppresses the external search entirely (finding **A2-10**). A failed external search is
swallowed into an empty list, which the UI cannot tell from "no such product" (finding
**A2-5**).

### 2.4 OpenFoodFacts integration

One DTO file, `OpenFoodFactsProductDTO.swift`, covering three response shapes: the search
envelope (`products`), the barcode envelope (`status` + optional `product`), and the product
itself with its `nutriments`. Both requests send a `fields=` parameter so the API returns only
what is mapped.

`URLSession.shared` is used **directly**, not through `FirestoreDataProviderProtocol` — the
protocol is Firestore-shaped and does not apply. The consequence, recorded in a comment in
`FetchFoodByBarcodeExternallyUseCase`, is that the fakes can stub a return value but cannot
assert which URL was called.

Mapping OpenFoodFacts → `FoodItemDomain` has a fixed shape:

- **Reject** anything without `nutriments`, without a positive `energy-kcal_100g`, or without
  any usable name. A partial item would render as 0 kcal, which is worse than "not found"; the
  caller treats `nil` as *product not found*.
- **Name preference** is `product_name_cs` → `product_name_en` → `product_name` for the Czech
  name, and `product_name_en` → `product_name` → the same raw name for the English one. Both
  run through `TextKit`'s `decodingHTMLEntities()`, because OpenFoodFacts names contain raw
  entities.
- **Every other nutrient defaults to `0`** when absent, and unsaturated fat is *derived* as
  `max(0, fat - saturatedFat)` — OpenFoodFacts does not publish it.
- `weight` is always `100`, because an external product carries no package weight.
- `energy_100g` (kJ) falls back to `MacroKit.energyKJFromMacros`, per
  [ADR 0007](adr/0007-derive-missing-energy-kj-from-macros.md).

This mapping is **duplicated verbatim**, 32 lines, in both external use cases (finding
**A2-6**).

### 2.5 Barcode scanning

`DataScannerRepresentable` wraps VisionKit's `DataScannerViewController`, configured for
barcodes only, one item at a time, accurate quality. The scanner is mounted inside the sheet's
`ZStack` whenever `isScannerVisible` **and** `DataScannerViewController.isSupported` **and**
`.isAvailable` — the last of which is false when camera permission was denied, in which case
nothing renders and nothing explains why (finding **A2-9**).

The delivery path is deliberately indirect: the coordinator writes into a `@Binding` bound to
`viewModel.lastScannedBarcode`, and the view's `.onChange` on that property calls
`onBarcodeScanned()`. The view model then clears the property, so the same code can be
delivered again later. The coordinator keeps its own `lastDeliveredCode` to suppress the
repeated callbacks VisionKit fires while a barcode stays in frame — but it never clears it,
which is finding **A2-8**.

Scanning is stopped while a lookup is in flight (`isSearching` → `stopScanning()`), and a
`ProgressView` over `.ultraThinMaterial` covers the camera preview.

### 2.6 Creating a catalogue item

`CreateFoodItemUseCase` validates in a fixed order — id non-empty and all digits, Czech name
non-empty, calories > 0, weight > 0 — then checks for an existing document and writes with
`setAsync`. Each failure is a distinct `CreateFoodItemError` case, and `AddFoodSheetViewModel`
switches over them exhaustively to pick the alert string; anything unrecognised falls back to
`L10n.Common.errorUnknown`.

Two things to know before touching it: the existence check is an equality query on the `id`
field rather than a document read, so it can be served from a stale offline cache and let an
overwrite through (finding **A2-2**); and `eng_name_lowercase` is written as `""` for every
manually created item, because the form has no English name field.

`FetchFoodItemsUseCase` loads the entire catalogue and **has no callers** (finding **A2-7**).

---

## 3. Dashboard and meal types

**Scope:** `Cross-platform` for meal assignment and the meal-window rules; `iOS` for the
caching and the screen structure.

**Read first:** [design 0005](design/0005-meal-window-and-html-entity-decoding.md) (the meal-window
arithmetic and which call sites delegate to `MealKit`),
[design 0004](design/0004-shared-macro-calculation-module.md) (macro totalling),
[design 0001](design/0001-user-authentication.md) (why the multi-device meal-type guard exists).

### 3.1 What the Dashboard is

One screen showing one day: a macro summary at the top, then the day's foods grouped into
sections by meal. Around it sit three navigations — a horizontal day picker, a month calendar
sheet, and a meal-type editing sheet — plus the add-food sheet and the account sheet.

`DashboardViewModel` owns all of it. It holds two pieces of server state (`mealTypes`,
`foodsConsumed`), the selected day, five `Bool`s driving sheets, and the month cache. Everything
displayed is derived from those by computed properties, so no aggregate is ever stored.

### 3.2 Meal assignment

The whole grouping rests on one rule: **a food belongs to a meal window if its time of day falls
in the window**, with the calendar date playing no part. See
[ADR 0014](adr/0014-meal-assignment-by-time-of-day-only.md), which is the thing to read before
changing anything here.

`groupedFoods` builds the sections:

1. For each meal type in `startTime` order, filter `foodsConsumed` for foods falling in that
   window. Skip the meal entirely if nothing matches — empty meals are not rendered.
2. Collect everything that matched *no* window into a trailing section with `mealType: nil`,
   rendered under `L10n.Dashboard.sectionUnassignedFoods`.

That trailing section is the answer to "what happens to a food outside every meal's range": it
is shown, labelled, and counted in the day total — but always last, and there is no way to move
it (finding **A3-2**). Note also that step 1 filters *all* foods, not the not-yet-assigned ones,
so overlapping windows list the same food twice (finding **A3-4**).

### 3.3 Macro aggregation

Two levels, both computed, both delegating to `MacroKit`:

- `DashboardViewModel.dailyMacros` → `DailyMacros(foods: foodsConsumed)` → `MacroKit.total`.
- Per-section, in the header popover: `MealSectionMacroView(name:foods:)` runs the same
  `DailyMacros(foods:)` over that section's foods.

`DailyMacros` is an adapter, not logic — it maps `[FoodConsumedDomain]` into `[Macros]`, calls
`MacroKit.total`, and maps back. There are no goals or targets in the app; the summary shows
totals only.

Because the day total is computed from `foodsConsumed` directly and the sections are computed
separately, a food double-listed by overlapping windows inflates the section totals but not the
day total.

### 3.4 Meal type lifecycle

`refreshMealTypes()` is the only entry point, and it carries the multi-device guard:

```swift
var types = try await fetchMealTypes()
if types.isEmpty, try await confirmMealTypesEmpty() { types = try await setupDefaultMeals() }
```

`FetchMealTypesUseCase` reads through Firestore's default source, which serves the offline cache
when the server is unreachable — so on a second device with a cold cache it can legitimately
return empty for a user who has meal types. `ConfirmMealTypesEmptyUseCase` re-asks with
`source: .server` before the defaults get written over them. Both calls are needed; dropping
either reintroduces the bug.

Editing happens in `MealTypeSheetViewModel`, and the set of possible edits is deliberately
narrow:

- **Create** — validated in `CreateMealTypeUseCase`: non-empty name, unique name, at least 30
  minutes long, no overlap with an existing window. The validation runs against the
  `existingMealTypes` array the sheet passes in, i.e. against client state, never against the
  server.
- **Delete** — blocked when only one meal type remains (`errorLastMealType`), so the collection
  can never become empty through the UI. `setupDefaultMeals` therefore only ever fires for a
  genuinely new user.
- **Reorder** — and this one is surprising enough to spell out. `onMove` snapshots the time
  windows *by position*, moves the rows, then writes the original times back by position. The
  time slots stay put; the meals move between them. Dragging "Dinner" above "Lunch" gives
  Dinner the earlier window, it does not carry its 17:00–20:00 with it. This is intentional —
  it is what keeps the windows contiguous and non-overlapping — and it is why the reorder is
  saved through `UpdateMealTypeTimesUseCase` rather than through some ordering field.

There is no path that edits a window's times directly, which is why `UpdateMealTypeTimesUseCase`
needs no overlap validation: it only ever receives a permutation of an already-valid set.

### 3.5 Day selection and the month cache

The month-at-a-time caching strategy, its invalidation rule and its costs are recorded in
[ADR 0015](adr/0015-dashboard-caches-a-month-and-derives-the-day.md).

What matters at the call sites is that `selectedDay` is not just a day — it is a full `Date`,
and **its time-of-day is what a newly logged food inherits**. `DashboardView` passes
`viewModel.selectedDay` into `makeAddFoodSheetView(for:)`, which passes it down to
`FoodQuantityViewModel.selectedDate`, which hands it to `SaveFoodConsumedUseCase`. The two ways
of changing the day disagree about what that time should be:

- `DayPickerView` steps with `calendar.date(byAdding: .day, …, to: selectedDay)`, which
  **preserves** the time of day.
- `MonthCalendarView.selectDay` rebuilds the date from year/month/day components, which
  **discards** it — the result is local midnight.

Combined with § 3.2, picking a day from the calendar and then logging a food puts that food at
minute 0, outside every default meal window. That is finding **A3-3**, and it is sticky: the day
picker preserves the midnight it inherited.

### 3.6 Refresh triggers

| Trigger | Method | Effect |
|---|---|---|
| `.task` on the view | `onAppear` | resets `selectedDay` to now, loads meal types and the month |
| `scenePhase` → `.active` | `onRefresh` | meal types + invalidate and reload the month |
| Pull to refresh | `onRefresh` | same |
| Returning from food detail | `onFoodConsumedUpdated` | invalidate and reload the month |
| Meal type sheet changed | `onMealTypesChanged` | meal types only |
| Day changed / picked | `onDayChanged` / `onDaySelected` | cache hit, or load that month |
| Calendar month paged | `onCalendarMonthChanged` | cache hit, or load that month |

`onAppear` and the `scenePhase` handler both fire on a cold launch, so the month is loaded twice
(finding **A3-7**).

---

## 4. Food entry flow

**Scope:** `Cross-platform` for the scaling rules; `iOS` for the screens and input handling.

**Read first:** [design 0004](design/0004-shared-macro-calculation-module.md) (calorie rounding —
it unified the *rule*, not the *basis*), [design 0003](design/0003-favourite-foods.md) (the detail
screen's favourite button and its catalogue lookup),
[design 0006](design/0006-own-daily-meals.md) (the quantity defaults for a created meal, and why
the favourite button is hidden rather than disabled).

### 4.1 The path a food takes

```
AddFoodSheet ──select──▶ FoodQuantity ──confirm──▶ SaveFoodConsumedUseCase ──▶ foodConsumed
                                                                                    │
Dashboard ────tap────▶ FoodConsumedDetail ──save──▶ UpdateFoodConsumedUseCase ──────┘
```

`AddFoodSheet` answers *which food* (§ 2). `FoodQuantity` answers *how much*, and is the only
screen that writes a new entry. `FoodConsumedDetail` is the after-the-fact editor, and the only
thing it can change is the weight.

Both quantity screens are assembled by `AddFoodSheetConfigurator.createView(date:…)`, which
takes the Dashboard's `selectedDay` and threads it down to `FoodQuantityViewModel.selectedDate`.
That date is what the entry is stamped with — see § 3.5 for why its time-of-day matters.

### 4.2 Choosing an amount

`FoodQuantityViewModel` holds `quantity: Double` and `unit: FoodQuantityUnit` (`.grams` = 1 g,
`.hundredGrams` = 100 g), and derives everything from `grams = quantity * unit.gramsPerUnit`.
`FoodQuantityUnit` is the whole of the unit system today; package and portion weights are a
planned extension (`TODO.md`).

Two defaults are set by the configurator rather than by the view model: selecting one of the
user's own meals pre-fills `quantity: item.weight, unit: .grams` — the meal's total gram weight,
because a saved meal is normally logged whole — while everything else starts at `1 × 100 g`.

The macro preview shown under the input is computed by `scaledMacros` / `scaledCalories` on the
view model. Those recompute on every keystroke and are never persisted; the values that *are*
persisted are computed again, independently, inside `SaveFoodConsumedUseCase`. The two blocks
are identical, comment included (finding **A4-4**).

Unit switching goes through `onUnitChanged(from:to:)`, which converts the current gram amount
into the new unit and then `.rounded()`s it with a floor of 1. The rounding is right for grams
and lossy for hundred-gram units (finding **A4-1**).

### 4.3 Numeric input

Two different text-field strategies coexist:

- **`FoodQuantityView`** binds a `String` (`quantityText`) and sanitises it in `onChange`:
  keep digits, allow at most one `.` or `,`, normalise the comma to a dot, then
  `Double(normalized)` into `viewModel.quantity`. The view model is only updated when parsing
  succeeds, so the field and the model can disagree (finding **A4-2**).
- **`BaseDoubleTextField`**, used by the add-a-new-food form, binds a `Double` through
  `NumberFormatter.decimal` — a shared static with `numberStyle = .decimal` and
  `zeroSymbol = ""`, so an empty field reads as zero and a zero renders as empty. Being a
  `TextField(value:formatter:)`, it commits on end-editing rather than per keystroke. Its unit
  suffix is a hardcoded `Text("g")` for every field it renders, kJ and kcal included (finding
  **A4-6**).

### 4.4 Writing and rescaling

`SaveFoodConsumedUseCase` scales the catalogue item's per-100 g values by `grams / 100`. It
scales `calories` **separately** from the rest, because `caloriesPerHundredGrams` is fractional
and folding it into `Macros.scaled` would round it twice — this is one of the few places in the
codebase carrying an explanatory comment, and it is there for a reason.

`UpdateFoodConsumedUseCase` scales the *entry's own* stored values by `newWeight / food.weight`
and never consults the catalogue. Why, and what it costs numerically, is
[ADR 0016](adr/0016-logged-entries-rescale-from-their-own-stored-values.md).

Note the asymmetry this creates: the logging path rounds a fractional value once, the editing
path rescales an already-rounded integer. `FoodConsumedDetailViewModel.scaledMacros` shows the
editing-path number, so the preview on the detail screen and the preview on the quantity screen
are not computed the same way for the same food at the same weight.

### 4.5 The detail screen

`FoodConsumedDetailViewModel` keeps `weight` (the edited value) alongside `savedWeight` (what is
on the server), so `hasWeightChanged` can gate the Save button. `food` itself is never mutated,
which is what keeps repeated edits correct: every rescale is computed against the originally
loaded weight, not against the previous edit.

`onAppear` runs two lookups, sequentially: whether the entry is favourited
(`IsFavouriteFoodUseCase`) and whether its catalogue item still exists
(`FetchFoodItemByBarcodeUseCase`). The second is needed because favouriting *adds* a full
snapshot and therefore needs a `FoodItemDomain` — which is why `canShowFavouriteButton` is
`isFavourite || catalogueItem != nil` and the button disappears for entries logged from
OpenFoodFacts (finding **A4-5**).

After a successful save the screen shows a checkmark for two seconds via `Task.sleep`, then
clears it.

### 4.6 Favourite toggling

Shared between `FoodQuantityViewModel` and `FoodConsumedDetailViewModel` through the
`FavouriteToggling` protocol extension: guard against re-entry, flip optimistically, call
`AddFavouriteFoodUseCase` or `RemoveFavouriteFoodUseCase`, roll the flag back and alert on
failure. See [ADR 0017](adr/0017-optimistic-favourite-toggle-shared-by-protocol-extension.md).

`FoodQuantityViewModel` additionally passes an `onToggled` callback so `AddFoodSheetViewModel`
can keep its in-memory favourites list in step without re-querying.

---

## 5. Cross-cutting concerns

**Scope:** `iOS` throughout.

**Read first:** nothing. No design doc covers error presentation, localization or `Components/` —
this area has never been designed up front, which is itself worth knowing.
[design 0004](design/0004-shared-macro-calculation-module.md) and
[design 0005](design/0005-meal-window-and-html-entity-decoding.md) cover only the KMP modules the
extensions bridge to.

### 5.1 Application root

`KalorieApp` renders one of three things off `AuthStateObserver.state`: a `ProgressView` while
authentication resolves, the Dashboard once it is `.loaded`, or a full-screen error with a
**Retry** button. Google's `onOpenURL` handler and the account-merge overlay hang off the loaded
branch.

The Dashboard carries `.id(authState.userId)`. That is what guarantees a clean slate when the
signed-in user changes — every view model below is rebuilt rather than re-pointed at another
user's data. The cost is that any auth transition also discards the Dashboard's transient state,
including the selected day (finding **A5-9**).

### 5.2 Error handling

Two conventions coexist, and the split is intentional:

| Where | Mechanism | Recovery |
|---|---|---|
| Root (auth) | `LoadingState.error(Error?)` rendered as a screen | Retry button |
| Every feature | `@Published var alertItem: AlertItem?` + `.alert(item:)` | Dismiss only |

[ADR 0018](adr/0018-per-feature-error-alerts-with-no-global-handler.md) records why there is no
global handler. In practice a feature's `catch` does one of three things:

- **switch over a typed error enum** and pick a specific message — `CreateFoodItemError` and
  `CreateMealTypeError` are the two use cases that make this possible;
- **collapse to `L10n.Common.errorUnknown`** — the common case;
- **swallow with `try?`** — thirteen call sites, mostly optional enrichment such as
  `fetchFavouriteFoods()` on appear or `saveProfileIfNeeded` after sign-in.

`LoadingState<T>` is the shared state enum (`idle`, `loading`, `loaded(T)`, `error(Error?)`),
with a `LoadingState<Void>.loaded` convenience for the many screens whose success carries no
value. Features use `.idle` / `.loading` / `.loaded` and never `.error`; only the auth root uses
the error case.

`View.loader(_:)` is the shared busy indicator — a `ProgressView` overlay plus
`allowsHitTesting(!isLoading)`, so a loading screen is inert.

**There is no logging, crash reporting or analytics in the project.** The only diagnostic output
is `print` inside `FirestoreDataProvider`, guarded by `#if DEBUG`. In a release build a caught
error leaves no trace anywhere (findings **A5-1**, **A5-2**).

### 5.3 Localization

One `Localizable.xcstrings` catalogue, source language `cs`, with `en` alongside — reached
exclusively through the hand-written `L10n` enum, per
[ADR 0019](adr/0019-l10n-enum-over-the-string-catalogue.md).

Two things about the app are Czech-first in ways the catalogue does not cover:

- `BilingualNamed.displayName` picks `czName` when the device language is `cs` or `sk`, and
  otherwise `engName` falling back to `czName`. This is data-level localisation — the food's own
  two names — and it is independent of the string catalogue.
- `NSCameraUsageDescription` in `Info.plist` is a hardcoded Czech string with no `InfoPlist`
  localisation.

Numbers and units do not go through localisation at all. Macro values are rendered with
`String(format: "%.1f g", …)` at seventeen call sites across five files: the decimal separator
is a dot regardless of locale — including in Czech, where the input fields accept and display a
comma — and the unit is baked into the format string (finding **A5-6**).

### 5.4 Components

`Components/` holds nine small view types, split into two groups by naming:

- **`Base*`** — generic primitives with no domain knowledge: `BaseButton`, `BaseImage`
  (+ `Helpers/BaseImageName`, an enum of the SF Symbol / asset names in use),
  `BaseStringTextField`, `BaseDoubleTextField`.
- **Domain views** — `FoodItemRow` (a catalogue item plus its favourite heart), `FoodConsumedView`
  (a logged entry: weight, name, calories), `MacroDonutView`, `FavouriteButton`,
  `DismissToolbarItem`.

Every one carries a `#Preview`, which is where most of the project's sample `FoodItemDomain`
values live.

Two conventions to be aware of when adding to this folder. First, `FoodConsumedView` lives in
`FoodItemView.swift` — file name, type name and header comment all disagree, against the
project's own `[Feature][Type].swift` rule (finding **A5-8**). Second, `BaseDoubleTextField`
hardcodes its unit suffix, which is why it renders "g" next to kilojoules (finding **A4-6**).

### 5.5 Extensions

`Core/Extensions/` is eight files of small, mostly single-purpose helpers. Two carry real
behaviour and are worth knowing about before adding a third:

- `Date+Extension` — `minutesSinceMidnight` (the bridge into `MealKit`, see
  [ADR 0014](adr/0014-meal-assignment-by-time-of-day-only.md)), `formatDateStyle(with:)` (the
  Dashboard's cache-key builder, see finding **A5-6**'s sibling **A3-5**), and the
  `withAddedMinutes` / `withAddedHours` arithmetic the meal sheet uses.
- `String+Extension` — HTML entity decoding, delegating to `TextKit`.

The rest (`CGFloat`, `Int`, `TimeInterval`, `NumberFormatter`, `UIWindowScene`) are one or two
members each.
