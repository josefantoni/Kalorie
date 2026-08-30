# Design: My created meals

- **Status:** Implemented
- **Scope:** Backend, Cross-platform, iOS
- **Date:** 2026-08-20

## Context and scope

A user who eats the same composed meal every day — porridge with three toppings, a protein shake
with two powders — has to log every ingredient separately every time. Today that means N passes
through `AddFoodSheetView`: type, wait for the 300 ms debounce, tap a result, push
`FoodQuantityView`, enter grams, confirm. `SaveFoodConsumedUseCase` writes one `FoodConsumedDTO`
per ingredient into `users/{userId}/foodConsumed`, and the Dashboard shows N rows.

`TODO.md` calls this *Own daily meals*: "let a user compose a meal they eat regularly instead of
entering the ingredients every day; visible only to that user, not subject to approval". The
reference behaviour is Kalorické tabulky — pick catalogue foods with gram amounts, name the result,
save it privately, then find it through the same search as any other food and add it by a gram
quantity.

This document designs that feature. The boundary: it does **not** touch the shared `foodItems`
catalogue, its moderation path, or how search results are ranked.

### Naming

**Decided: `MyCreatedMeal`.** `MyCreatedMealDTO`, `MyCreatedMealDomain`,
`users/{userId}/myCreatedMeals`, `Features/MyCreatedMeal/`, `L10n.MyCreatedMeal`, and the four
`…MyCreatedMeal…` use cases.

The name carries a known and accepted cost: `Meal` already means the *time-window* concept —
`MealTypeDomain`/`MealTypeDTO`, `MealTypeSheetView`, six `…MealType…` use cases,
`SetupDefaultMealsUseCase`, `MealSectionMacroView`, the `MealKit` KMP module (`MealWindows.kt`),
`L10n.DefaultMeals`, `L10n.Dashboard.buttonMealLayout`. A repo-wide `grep Meal` therefore returns
two unrelated concepts and a reader must parse the `MyCreated` prefix to tell them apart. The
mitigation is that the prefix is unambiguous wherever it appears and never abbreviated: no
`MealDTO`, no `CreatedMeal`, no `MCMeal`. The type is always written out in full.

The **code** name is `MyCreatedMeal`; the **Czech UI** string is *Vlastní jídlo* / *Vlastní jídla*.
The two are allowed to differ — repository content is English, UI copy is not.

## Goals

- A user composes a meal from any number of catalogue foods, each with a gram amount, gives it a
  private name, and saves it — from one screen that does the whole job.
- The meal is findable through the **same search field** as regular foods, and is added to the day
  by a **gram quantity**, exactly as a food is — same screen, same interaction.
- Logging one produces **one** Dashboard row bearing the meal's name, whose macros are correct and
  which behaves like any other logged entry (tap to open, edit the weight).
- Meals are private to the user, never enter `foodItems`, and are not moderated.
- Full create / read / update / delete. Unlike favourites, a wrong gram value inside an
  eight-ingredient meal is expensive to fix by re-creating it.
- Meals survive sign-in (anonymous → Apple/Google merge) and are removed with the account.
- No change to `firestore.rules`.

## Non-goals

- **Preparation steps, servings, photos, categories, or sharing meals between users.** A
  `MyCreatedMeal` is a named ingredient list with amounts and nothing else.
- **Any visual distinction on the Dashboard.** Decided: a logged `MyCreatedMeal` renders exactly
  like a logged food — no badge, no icon, no different row. It is one entry with a name and a
  weight, which is all the Dashboard has ever shown.
- **Cooking-loss correction.** The composed weight is the sum of the ingredient weights; the design
  assumes mass is conserved. It is not, when food is cooked. See *Risks* — this is the one known
  modelling flaw and it is accepted for v1.
- **Nesting.** A `MyCreatedMeal` cannot contain another one. One level only; it removes cycle
  detection, recursive density computation and a whole class of edit-propagation questions for a
  case nobody has asked for.
- **Retroactive edits.** Editing a meal does not change entries already logged from it — those are
  snapshots, like every other `foodConsumed` document. Stated as a goal-shaped non-goal because
  users will expect one behaviour or the other and the doc must pick.
- **Frequency ranking of created meals** — the separate `TODO.md` item. See *Scope boundary*.
- **Getting an ingredient into the shared catalogue.** That is *User-submitted food*, also separate.
- Anything beyond the offline behaviour the Firestore SDK already provides.

## Design

### The central decision: one aggregate entry, not N ingredient entries

How a logged meal persists into `foodConsumed` determines everything else. Two variants were on
the table.

**Variant B — N per-ingredient documents.** Log a meal by writing one `FoodConsumedDTO` per
ingredient, in exactly today's shape. Preserves the schema and the write path untouched.

**Variant A — one aggregate document** carrying the meal's name and the summed, scaled macros,
written by the existing `SaveFoodConsumedUseCase` from a `FoodItemDomain` that represents the meal.

**Decided: Variant A.** Four reasons, in order of weight:

1. **It is what the feature is for.** "Pomocí gramáže přidat do snězeného jídla" — added by a gram
   quantity. A gram quantity of *the meal* only exists if the meal is one thing with one weight.
   Variant B has no such quantity; it has N of them.

2. **It leaves the search path unchanged.** `AddFoodSheetViewModel.displayedResults` is
   `[FoodItemDomain]`, `onSelectFoodItem(_:)` takes a `FoodItemDomain`, `selectedFoodItem` is one,
   and `AddFoodSheetView.makeFoodQuantityView` is typed on it (`AddFoodSheetView.swift:19`). If a
   `MyCreatedMeal` *is* a `FoodItemDomain` — synthetic id, computed per-100 g macros — then the
   row, the tap, the push, the quantity screen and the write all work with no change to their
   types. Variant B turns `displayedResults` into a sum type and turns one `onSelectFoodItem` into
   an N-step wizard, which is a far larger blast radius inside the sheet than the data-model change
   Variant A costs.

3. **Variant B has no grouping field, and no way to get one cheaply.** Nothing in the schema groups
   `foodConsumed` documents. Without it the Dashboard cannot render, edit or remove the meal as a
   unit, and `DashboardViewModel.groupedFoods` would scatter the ingredients across meal-type
   windows independently. Adding a `meal_group_id` is possible but then every consumer —
   `groupedFoods`, `DailyMacros(foods:)`, `FoodConsumedDetailViewModel`, `UpdateFoodConsumedUseCase`
   — grows a group-aware branch, which is strictly more work than Variant A's single new
   field-semantics question.

4. **There is no delete path for a logged entry today.** `FoodConsumedDetailView` edits the weight
   and nothing else; `deleteAsync` against `foodConsumed` appears only inside
   `DeleteAccountUseCase.swift:51`. Variant B would therefore let one tap deposit eight permanently
   unremovable rows on the Dashboard. That is not a theoretical objection.

Variant A is also what makes the "no visual distinction on the Dashboard" decision free: the logged
entry genuinely *is* an ordinary `FoodConsumedDTO`, so there is nothing to hide.

The price of Variant A is stated in full under *What `food_item_id` means now*.

### Data model

```
users/{userId}/myCreatedMeals/{mealId}
```

`mealId` is a fresh `UUID().uuidString`, matching how `SaveFoodConsumedUseCase` mints ids
(`SaveFoodConsumedUseCase.swift:48`). Deliberately **not** a slug of the name: names are neither
unique nor stable, and renaming must not move the document.

```swift
struct MyCreatedMealDTO: Codable {
    let id: String
    let name: String
    let ingredients: [MyCreatedMealIngredientDTO]
    let createdAt: TimeInterval    // "created_at"
    let updatedAt: TimeInterval    // "updated_at"
}

struct MyCreatedMealIngredientDTO: Codable {
    let foodItemId: String         // "food_item_id"
    let czName: String             // "cz_name"
    let engName: String            // "eng_name"
    let grams: Double
    let energyKJ: Double?          // "energy_kj"
    let caloriesPerHundredGrams: Double   // "calories_per_hundred_grams"
    let fat: Double
    let fatSaturated: Double?      // "fat_saturated"
    let fatUnsaturatedFattyAcids: Double  // "fat_unsaturated_fatty_acids"
    let carbohydrate: Double
    let carbohydratePureSugar: Double     // "carbohydrate_pure_sugar"
    let fiber: Double?
    let protein: Double
    let salt: Double
}
```

Three properties are deliberate:

- **The ingredient is a snapshot, not a reference**, with the same field set and the same
  `CodingKeys` naming as `FavouriteFoodDTO` (`FavouriteFoodDTO.swift:33`) — including the same
  optionals, so a `FoodItemDTO` missing `energy_kj` / `fat_saturated` / `fiber` round-trips
  identically. This is the third time the app makes this choice, after `FoodConsumedDTO` and
  `FavouriteFoodDTO`. It buys one read per meal and it works offline. Note the argument is weaker
  here than in 0003, because catalogue-only search means every ingredient *is* in `foodItems` at
  composition time — the honest remaining reasons are the read count and consistency with the two
  existing precedents, not impossibility of the alternative. The cost is staleness, compounded
  N-fold — see *Risks*.
- **Ingredients are a nested array, not a subcollection.** A meal is read and written as a whole; a
  subcollection would turn every render into 1 + N reads and every edit into a fan-out with no
  transaction around it. The 1 MiB document limit is not a constraint at ~250 bytes per ingredient.
- **`grams` is stored per ingredient, and the meal stores no total.** The total is
  `ingredients.reduce(0) { $0 + $1.grams }`, derived on read. Storing it as well would create a
  field that can silently disagree with the array it summarises.

`foodItemId` on the ingredient is the *source* item's barcode, kept so the editor can re-resolve the
catalogue entry and so a future feature can walk back to it. It is not a document key here.

### The arithmetic: a created meal is a gram-weighted mean of its ingredients

For a meal to enter the existing pipeline it must present as `FoodItemDomain`, whose macros are
**per 100 g**. The composed per-100 g value of a macro `m` is:

```
density(m) = Σ(m_i × grams_i / 100) / totalGrams × 100
           = Σ(m_i × grams_i) / Σ(grams_i)
```

— the gram-weighted mean of the ingredients' per-100 g values. Two consequences matter:

- **It introduces no new rounding.** Every term is a `Double`, including `caloriesPerHundredGrams`.
  Calories are rounded exactly once, at log time, by the existing
  `MacrosKt.scaledCalories(caloriesPerHundredGrams:ratio:)`. This preserves the invariant commit
  `27c9419` was written to restore and that both `FoodQuantityViewModel.scaledMacros` and
  `SaveFoodConsumedUseCase` document in comments.
- **`List<Macros>.total()` is the wrong primitive for it.** `Macros.calories` is `Int` and
  `Macros.scaled` rounds, so summing already-scaled `Macros` would round N times and then again at
  log time. The density needs a new pure function over `Double`s.

That function belongs in **MacroKit** — see *Shared logic (KMP)* below for why it, and only it,
crosses the module boundary.

### Shared logic (KMP)

An Android client is a realistic future direction, so the question for every piece of logic here is
not "could this be shared" but "**what breaks if the two clients implement it differently**". That
test sorts this feature's logic into three groups, and only the first earns a Kotlin module.

**Must be shared — divergence silently corrupts data.**

One function, added to **MacroKit**, matching its charter from design 0004 (pure macro arithmetic,
`Double`/`Int` only, no platform types):

```kotlin
fun weightedMeanPerHundredGrams(values: List<Double>, grams: List<Double>): Double
```

It returns `0.0` when `Σ grams` is `0`, which is the guard the Swift caller would otherwise need at
ten call sites (one per macro field). A meal with no ingredients cannot be saved, so this is a
defensive default rather than a reachable state.

This is not a convenience. The same meal at the same weight must yield the same calories on both
platforms, and the composition is where that is easiest to get subtly wrong — an implementation
that sums already-scaled values instead of taking the weighted mean rounds N times instead of once
and produces numbers that are close enough to look right and wrong enough to drift a daily total.
It goes in MacroKit rather than a new module because it *is* macro arithmetic; that is the shelf it
belongs on.

**Documented as a cross-platform rule, but not extracted — for now.**

The composition validity rule (trimmed name non-empty, at least one ingredient, every
`grams >= 1`) is a genuine cross-platform requirement: both clients must enforce it or one writes
documents the other considers malformed. It is **not** extracted into a module for v1, on two
grounds:

- **The stakes are much lower than the arithmetic.** If Android permits a `0.5 g` ingredient, iOS
  still reads the meal and still computes its density correctly. The result is a sloppy meal, not a
  wrong number. Corruption is what justifies the infrastructure; sloppiness does not.
- **A module built before its second consumer exists gets an API shaped for one caller.** Design
  0004 framed MacroKit explicitly as a probe for whether KMP is worth carrying, and each module
  costs a Gradle build, an XCFramework, and CI time on every commit (the pre-build step added in
  `70a42e8`). Five lines of predicate do not pay for that, and extracting them *with Android in
  hand* is when the right signature becomes obvious.

The rule is therefore written out here as a `Cross-platform` obligation, enforced in
`CreateMyCreatedMealUseCase` / `UpdateMyCreatedMealUseCase`, and flagged as the first candidate to
extract when the Android client starts.

**Deliberately not shared.**

- **`asFoodItem()`** — field copying between platform model types. Only its macro half is real logic,
  and that half is the MacroKit function above.
- **Total grams** — `ingredients.sum()`. Wrapping a language builtin in an XCFramework is cost with
  no benefit.
- **The search hoisting order** (created meals → favourites → catalogue) — presentation ranking over
  view-model types. Android is free to rank differently; nothing it writes depends on the order.
- **`canSave`** — the *same predicate* as the validation rule above, but living on the view model.
  Sharing the rule does not mean sharing the view state that mirrors it.

### From `MyCreatedMealDTO` to `FoodItemDomain`

```swift
extension MyCreatedMealDomain {
    func asFoodItem() -> FoodItemDomain
}
```

- `id` — the meal id.
- `czName` — the meal name. `engName` — **empty string**. `BilingualNamed.displayName` falls back to
  `czName` when `engName` is empty, so one user-entered name renders correctly in both locales, and
  the prefix filter (below) simply never matches on `engName`. No second name field, no translation
  question.
- `weight` — the composed total grams.
- `date` — `createdAt`.
- every macro — `weightedMeanPerHundredGrams` over the ingredients.

From here the meal is indistinguishable from a catalogue item to every downstream consumer. That is
the whole point of Variant A, and it is what makes the no-distinction decisions cost nothing.

### What `food_item_id` means now

This is the cost of Variant A and it must be paid deliberately.

`FoodConsumedDTO.foodItemId` is required and, since design 0003, has meant "the barcode of the
catalogue item this entry came from". Logging a `MyCreatedMeal` writes the **meal id** there
instead — a UUID, not a barcode.

Both readers of the field already tolerate this:

- `FoodConsumedDetailViewModel.onAppear()` (`FoodConsumedDetailViewModel.swift:66-69`) resolves both
  the favourite state and the catalogue item with `try?`, and `canShowFavouriteButton` (`:32`)
  already handles `catalogueItem == nil` by hiding the button rather than disabling it — a control
  the user can never use on this screen (the item has no catalogue counterpart to favourite) is
  hidden, not disabled; `canToggleFavourite` still disables it for the brief in-flight window while
  a toggle is being written.
- Nothing else reads `food_item_id`.

So the degradation is precise and small: **a logged `MyCreatedMeal` cannot be favourited from the
Dashboard.** That is acceptable, and arguably right — a created meal is already a curated private
item, which is what favouriting a food produces.

The two values cannot collide: `CreateFoodItemUseCase.swift:37` requires a catalogue id to be a
non-empty run of digits, and a `UUID().uuidString` always contains hyphens and letters. Opening a
logged meal's detail screen therefore costs two Firestore reads that are guaranteed to miss.

**Update — the discriminator now exists.** This section originally deferred a `food_item_kind`
field, with an explicit revisit condition: "if a second consumer of `food_item_id` appears." That
consumer arrived as *Rank search results by frequency* (`TODO.md` **A2-1**), which needs to group
logged entries by origin and cannot do so on an id that may be a catalogue barcode, an
uncatalogued OpenFoodFacts barcode, or a meal UUID. `FoodItemDomain.kind` /
`FoodConsumedDTO.foodItemKind` (`FoodItemKind`: `.catalogue` / `.external` / `.createdMeal`) now
carries this explicitly, set once at selection time and threaded through unchanged. The same field
was added to `FavouriteFoodDTO`, since a favourite can originate from either a catalogue or an
external item and would otherwise reintroduce the same ambiguity one hop upstream.
`FoodConsumedDetailViewModel.onAppear()` now gates the two reads separately, not by one combined
`== .catalogue` check: the catalogue lookup (`fetchFoodItemByBarcode`) only ever runs for
`.catalogue`, since `.external` and `.createdMeal` are both a guaranteed miss against `foodItems`.
The favourite lookup (`isFavouriteFood`) runs for `.catalogue` **and** `.external` — favouriting
queries the separate `favouriteFoods` collection by id and doesn't care where the id came from
(ADR 0012's *"favourites ... still work on external items"*) — and is skipped only for
`.createdMeal`, matching the "cannot be favourited from the Dashboard" degradation above.

### Security rules

No change required. The existing block covers every subcollection under a user:

```
match /users/{userId} {
  allow read, write: if request.auth != null && request.auth.uid == userId;
  match /{document=**} { allow read, write: if ... }
}
```

Verify this holds after implementing rather than assuming it — the authentication review found a
rules regression in exactly this block.

### Use cases

Four, following the `Create…` / `Delete…` shape the project already uses:

```swift
protocol FetchMyCreatedMealsUseCaseProtocol {
    func callAsFunction() async throws -> [MyCreatedMealDomain]
}

protocol CreateMyCreatedMealUseCaseProtocol {
    func callAsFunction(name: String, ingredients: [MyCreatedMealIngredientDomain]) async throws -> MyCreatedMealDomain
}

protocol UpdateMyCreatedMealUseCaseProtocol {
    func callAsFunction(_ meal: MyCreatedMealDomain) async throws
}

protocol DeleteMyCreatedMealUseCaseProtocol {
    func callAsFunction(id: String) async throws
}
```

All four take `FirestoreDataProviderProtocol` + `AuthProviderProtocol` and throw
`AuthError.notAuthenticated` when `userId` is `nil`, like `SaveFoodConsumedUseCase`.

- `FetchMyCreatedMealsUseCase` reuses the **existing** `loadAsync(from:orderBy:descending:limit:)`
  (`FirestoreDataProvider.swift:17`) with `orderBy: "updated_at", descending: true, limit: 50` — the
  same call shape and cap `FetchFavouriteFoodsUseCase.swift:32` uses. **No new provider method is
  needed in this design**, which means no fake in `KalorieTests` stops compiling.
- `CreateMyCreatedMealUseCase` validates: non-empty trimmed name, at least one ingredient, every
  `grams >= 1`. Reject with a `MyCreatedMealError` enum, per the project's per-domain error
  convention. It mints the `UUID`, stamps `createdAt == updatedAt`, and writes with
  `setAsync(_:id:in:)`.
- `UpdateMyCreatedMealUseCase` runs the same validation, preserves `createdAt`, stamps `updatedAt`,
  and writes with `setAsync` — which **overwrites the whole document**. That hazard has now bitten
  this codebase three times (the `UserProfileDTO` overwrite, `UpdateFoodConsumedUseCase`, and the
  `food_item_id` trap in 0003). Here it is contained by construction: the use case takes a whole
  `MyCreatedMealDomain` rather than a patch, so there is no field it can omit. Worth one test that
  asserts `createdAt` survives an update.
- `DeleteMyCreatedMealUseCase` uses `deleteAsync(id:from:)`.

The validation is written once, in a shared private function, and it is the **same rule** the save
button's enabled state uses — see *The editor screen*. Duplicating that predicate in the view model
and the use case is how the two drift.

Add to `Constants.Firestore` (`Constants.swift:16-22`), matching the existing shape:

```swift
static func myCreatedMeals(userId: String) -> String { "users/\(userId)/myCreatedMeals" }
```

### Search integration — a section, then hoisted results

Created meals are per-user and capped at 50, so the whole set is fetched once per sheet presentation
and filtered **in memory**. No lowercase index fields on the documents, no Firestore prefix query,
no composite index — the same reasoning that made favourites free in 0003, and the reason the
`limit: 50` cap matters.

`AddFoodSheetViewModel` gains:

```swift
@Published private(set) var myCreatedMeals: [MyCreatedMealDomain] = []
```

`onAppear()` (`AddFoodSheetViewModel.swift:165-170`) already fetches favourites and swallows the
error to keep logging food working; created meals load in the same method under the same rule. The
two fetches are independent and should run concurrently with `async let`.

`displayedResults` (`:58-66`) grows one layer, above favourites:

```swift
var displayedResults: [FoodItemDomain] {
    let query = searchText.lowercased()
    guard !query.isEmpty else { return localFoodItems }
    let matchingMeals = myCreatedMeals
        .map { $0.asFoodItem() }
        .filter { $0.czName.lowercased().hasPrefix(query) }
    let matchingFavourites = favouriteFoods.filter {
        $0.czName.lowercased().hasPrefix(query) || $0.engName.lowercased().hasPrefix(query)
    }
    let matchingIds = Set(matchingMeals.map(\.id) + matchingFavourites.map(\.id))
    return matchingMeals + matchingFavourites.filter { !matchingIds.contains($0.id) }
        + localFoodItems.filter { !matchingIds.contains($0.id) }
}
```

- **Created meals rank above favourites**, which rank above catalogue matches. A created meal is the
  most deliberately constructed thing the user owns; if they typed its prefix, they meant it.
- **The prefix rule is the same one the Firestore query uses**, so nothing is hoisted above a row the
  user would not consider a match.
- **Meals and favourites cannot collide by id** — favourites are keyed by barcode, meals by UUID —
  so the dedup exists only to keep the shape honest as the list grows.
- It runs **before** the 300 ms debounce fires, so a created meal appears on the first keystroke.
- The external-OpenFoodFacts fallback is gated on `displayedResults.isEmpty` (`:140`), so it
  correctly stops firing when a created meal matched.

With an **empty** search field the sheet renders a *Vlastní jídla* section above the existing
*Oblíbené* section, when `searchText.isEmpty && !myCreatedMeals.isEmpty`, using the same
`FoodItemRow` with `isFavourite: false`. Tapping a row calls the unchanged `onSelectFoodItem(_:)`
with `meal.asFoodItem()` — that is, **tapping a created meal in the add-food sheet logs it**, and
`FoodQuantityView` opens exactly as it does for a food.

The rows themselves carry **no visual distinction** from catalogue foods. This follows the Dashboard
decision and 0003's precedent, which deliberately rejected per-row marks as noise.

### Quantity semantics at log time

The meal's `weight` is the composed total, and `FoodQuantityView` already offers a grams / 100 g
unit picker (`FoodQuantityView.swift:103-107`). Logging 100 g of a created meal logs 100 g of the
mixture — the density is exactly what makes that meaningful.

One change: `FoodQuantityViewModel` currently starts at `quantity = 1`, `unit = .hundredGrams`
(`FoodQuantityViewModel.swift:27-28`). For a created meal the overwhelmingly common case is "I ate
the whole thing", so the screen should open at `unit = .grams, quantity = totalWeight`. That means
two new `init` parameters with defaults matching today's values, and updating the call sites
(`AddFoodSheetConfigurator`, the `#Preview` in `AddFoodSheetView.swift:252`). `FoodQuantityView`
seeds `quantityText` from `viewModel.quantity` at `:22`, so it follows for free.

No per-ingredient ratio editing at log time. A user who ate a different amount of one ingredient did
not eat this meal; they should log the ingredients, or edit the meal.

### The entry point

`AddFoodSheetView` gains a **floating button** at the bottom centre, over the search-results list:
*Vytvořit vlastní jídlo z potravin*.

Per the Liquid Glass conventions this is `.safeAreaInset(edge: .bottom)`, which centres its content
by default and correctly insets the `List` above it so the last result is never trapped under the
button, with `.glassEffect(.regular, in: .capsule)` — a capsule rather than a circle because the
control is text-labelled, and a capsule keeps its proportions when Dynamic Type grows the label. Not
`ToolbarItem(.bottomBar)`, and not `BaseImage` at a large size. It is the same mechanism the
Dashboard's add-food FAB already uses (`DashboardView.swift:96-111`), minus that one's
`foodsConsumed.isEmpty` condition — this button is always present.

**This placement is provisional.** It is a deliberate holding position, not a considered outcome:
it competes for the same corner as the Dashboard FAB one layer down, and a full-width text capsule
is a lot of visual weight for a secondary action on a screen whose job is the search field. Nothing
else in this design depends on where the button sits — moving it later touches one view and no
model, use case or document. Recorded here so a future reader does not mistake it for a decision
that was argued for.

Tapping it **dismisses the add-food sheet and presents the editor as a new modal**, which is a
sheet-to-sheet transition and the one piece of mechanics here that is easy to get wrong.

`DashboardView` owns the presentation — it already presents four sheets (`DashboardView.swift:130-145`),
and `AddFoodSheetViewModel` already carries the `shouldDismiss` flag that `AddFoodSheetView` turns
into a `dismiss()` (`AddFoodSheetView.swift:62-64`). The editor is the fifth sheet on `DashboardView`,
and the handoff uses `.sheet(isPresented:onDismiss:)`:

```swift
.sheet(isPresented: $viewModel.showAddFoodSheet) {
    viewModel.onAddFoodSheetDismissed()
} content: {
    router.makeAddFoodSheetView(for: viewModel.selectedDay) { … }
}
```

`AddFoodSheetViewModel` sets a pending flag and `shouldDismiss`; `DashboardViewModel` reads that
flag in `onAddFoodSheetDismissed()` and only then sets `showMyCreatedMealEditor = true`.

**Setting the second flag anywhere other than `onDismiss` is a bug**, not a style preference: SwiftUI
drops a sheet presented while another is still dismissing, and the failure mode is a button that
works intermittently — worst on a fast tap, invisible in a simulator screenshot. This is the single
most likely place for this feature to ship broken, so it gets a named mitigation in *Risks*.

The add-food sheet is the only entry point for **creating**. The entry point for **editing** an
existing meal is deliberately left open below.

### The editor screen

One screen serves create and edit; the difference is whether it opens on an empty draft or one
preloaded from a `MyCreatedMealDomain`. A new feature directory `Features/MyCreatedMeal/` with
`MyCreatedMealEditorView`, `MyCreatedMealEditorViewModel` and `MyCreatedMealEditorConfigurator`,
following the project's Router + Configurator pattern.

Layout, top to bottom, inside a `NavigationStack` + `List`:

1. **Name** — a single `BaseStringTextField`, placeholder *Název jídla*.
2. **Selected ingredients** — a section of rows, each showing the food's `displayName` and a grams
   input. Swipe-to-delete removes a row.
3. **Search field**, then **search results** — typing runs the same debounced
   `SearchFoodItemsUseCase` the add-food sheet uses; tapping a result **appends it to the
   ingredients list** and does not navigate anywhere. Selecting a result also **clears the search
   field and its results** — the query and its matches are spent once they have produced a row, and
   leaving them on screen underneath the new row is clutter, not state worth keeping — and moves
   keyboard focus to the new row's grams field (see below).

Toolbar: a **checkmark** in `.topBarTrailing` that saves, plus the existing `DismissToolbarItem`.

**This screen does not reuse `AddFoodSheetView`.** That is a direct consequence of the layout above
and it removes what the previous draft called the largest unknown in the estimate. `AddFoodSheetView`
hard-codes its outcome — a tap pushes `FoodQuantityView`, which writes a `FoodConsumedDTO` — so
reusing it as a picker would have meant either a mode enum threaded through its view model or
extracting its search half into a shared component. Neither is needed: the editor embeds its own
search field over the same **use case**, which is where the actual logic lives. The duplication is
the ~15 lines of debounce-and-assign in `onSearchTextChanged()`, and that is the right thing to
duplicate rather than the wrong thing to abstract.

**Revised: the editor's search also has the barcode scanner and the OpenFoodFacts fallback**, wired
identically to `AddFoodSheetView` — the same `DataScannerRepresentable`, the same
`FetchFoodItemByBarcodeUseCase` / `FetchFoodByBarcodeExternallyUseCase` / `SearchFoodExternallyUseCase`
trio, gated the same way (`searchResults.isEmpty && searchText.count >= 3`).

The first draft of this document excluded both, reasoning that a screen "whose job is composing from
foods the user can already find" shouldn't need the external-search machinery. That reasoning does not
survive contact with what an ingredient actually is: per *Data model* above, `MyCreatedMealIngredientDTO`
is a **snapshot**, taken at the moment the ingredient is picked — the exact same shape of snapshot
`FoodConsumedDTO` takes when logging a food for the day. Nothing about composing a meal instead of
logging a day asks for a narrower set of foods to compose it from; the "user can already find it
locally" premise was simply false whenever the ingredient lives only on OpenFoodFacts. Excluding the
scanner made that case unreachable in the editor for no corresponding benefit — the mechanism the
add-food sheet already has (search → resolve `FoodItemDomain` → append) is exactly the mechanism the
editor needs, whether the result comes from the local catalogue, a barcode scan, or an OpenFoodFacts
text search.

The prior cost this bought — "a food only available on OpenFoodFacts cannot be an ingredient until it
reaches the catalogue" — is gone. The corresponding row in *Risks* is removed accordingly.

#### The grams input

Per the spec: an input the user **must** fill, in grams, with placeholder `100`, followed by a
static `g` unit label after the field. The row does not prefill the value — a freshly added
ingredient starts with an empty `gramsText` and the field takes keyboard focus immediately, so the
user can type a number over the placeholder. Only if they defocus the field — by adding another
ingredient, tapping elsewhere, or dismissing the keyboard — while it is still empty does it fall
back to `100`. This keeps the common case ("add a food, type its grams, add the next one") a single
uninterrupted flow, while still landing on a valid, saveable value for someone who just taps through.

The draft therefore holds `gramsText: String` per ingredient, **not** `Double`. A `Double` binding
cannot represent "not yet filled" — it would render `0` where the placeholder belongs and make an
untouched row indistinguishable from a deliberate zero. This also rules out reusing
`BaseDoubleTextField` (`BaseDoubleTextField.swift`), which binds `Double` and hard-codes the `"0"`
placeholder; bending it would change every existing call site in `addCustomFoodItem`.

The pattern to follow instead is the one `FoodQuantityView` already uses: a `String`-backed
`TextField` with `.keyboardType(.decimalPad)` and an `.onChange` sanitiser
(`FoodQuantityView.swift:80-101`). Keeping the text on the draft item rather than in per-row
`@State` keeps one source of truth inside a `ForEach`.

Focus is tracked in the view as `@FocusState private var focusedIngredientId: UUID?`, keyed by the
draft's `id` rather than its index — a `ForEach` row's identity must survive deletions elsewhere in
the list. `onSelectSearchResult` returns the new draft's id so the view can assign focus to it, and
an `.onChange(of: focusedIngredientId)` calls back into the view model
(`onGramsFieldDefocused(id:)`) when focus moves away from a row, which is where the empty-becomes-
`100` fallback lives. Keeping that fallback on the view model rather than inline in the view's
`onChange` closure is what keeps it unit-testable at the `MyCreatedMealEditorViewModelTests` level.

#### The save button's enabled state

The checkmark is enabled only when **all three** hold:

- the trimmed name is non-empty,
- there is at least one ingredient,
- every ingredient parses to `grams >= 1`.

Expressed as one computed `var canSave: Bool` on the view model, which **must** be the same
predicate `CreateMyCreatedMealUseCase` and `UpdateMyCreatedMealUseCase` validate with. The use case
still validates: a disabled button is a UI affordance, not an invariant, and the use case is what a
second client and the tests hold to.

When editing an existing meal, `canSave` additionally requires `hasChanges` — the current name and
ingredients must differ from the snapshot the editor was opened with. Opening an existing meal
already satisfies the three conditions above (it has a name, at least one ingredient, valid grams),
so without this the checkmark would be enabled the instant the screen appears, before the user
touched anything. Creating has no such snapshot to compare against, so there `canSave` is the bare
validation predicate, same as before — this is not a second predicate, just the edit case's extra
guard.

Disabled rather than hidden — a control that vanishes is a control the user cannot learn from. Since
the button is a bare glyph with no text to explain itself, the empty ingredients section carries a
short placeholder line telling the user what is missing.

#### Save confirmation

Tapping the checkmark does **not** write. It raises a two-button `ANO` / `NE` alert; the write
happens only on `ANO`. This applies to both creating and editing.

`AlertItem` as it exists is single-button — `AddFoodSheetView.swift:50-55` builds
`Alert(title:dismissButton:)` — and it is used for errors across the app. **Do not generalise it.**
The editor adds its own `@Published var isSaveConfirmationVisible = false` and a separate `.alert`
with two buttons, leaving `alertItem` to go on meaning "something failed". One shared alert type
serving both confirmation and error reporting is how a cancel button ends up dismissing an error.

On `ANO`: run the use case, and on success dismiss the modal. On failure set `alertItem` and stay,
so the composed draft is not lost.

### Editing and deleting — the management section

The editor opens **preloaded** with an existing meal's ingredients. Where the user taps to get there
cannot be the created-meal row in the add-food sheet, because that row logs the meal — the primary
purpose of the feature. Editing therefore needs its own entry point, and so does deleting.

**Decided: an inline section inside `MealTypeSheetView`**, headed *Vlastní jídla* and placed above
the sheet's existing *Rozvržení jídel* section, in the same `List`:

- one row per meal, showing the name and the composed total weight,
- **tap** pushes the editor onto the sheet's own `NavigationStack`, preloaded with that meal,
- **swipe-to-delete** removes it, independent of any edit-mode toggle.

**Placement: inside the meal-layout sheet, not a separate screen.** Meal composition
(`MyCreatedMeal`) and meal layout (`MealTypeDomain`, *Rozvržení jídel*) are the same mental model to
the user — both describe how the day's food is organised — so they belong in the same sheet rather
than a generic account screen. `MealTypeSheetView` already opens from the Dashboard's
`.topBarTrailing` toolbar button; that button is icon-only (`list.bullet.circle`, with
`.accessibilityLabel(L10n.Dashboard.buttonMealLayout)` carrying the existing string forward) so the
toolbar stays uncluttered. Adding the new section costs one `Section` in that sheet's `List` and no
new navigation surface, unlike a dedicated pushed screen or a row in `AccountView`, either of which
would give the same concern two separate places to be found.

The sheet's own edit-mode toggle for *Rozvržení jídel* — reordering and deleting `MealTypeDomain`s —
is unaffected in behaviour, only in trigger: the pencil/checkmark toolbar pair is replaced by a
single capsule button (*Upravit* / *Hotovo*) in that section's header, toggling the same `editMode`.
*Hotovo* is disabled until the order actually changes since entering edit mode — deletion already
persists immediately through `onDelete`, so a reorder is the only pending write the button can
represent, and there is nothing to save on a plain enter-then-exit. `editMode` stays scoped to
*Rozvržení jídel* only — *Vlastní jídla* declares neither `.onDelete` nor
`.onMove` on its `ForEach`, and SwiftUI only produces delete/reorder affordances on rows that opt in.
Confirmed on an iOS 26.5 simulator with `editMode` forced `.active`: *Vlastní jídla*'s rows stayed
plain while *Rozvržení jídel*'s showed the delete circle and drag handle. A single shared `List` was
sufficient; two separate `List`s were not needed as a fallback.

Deletion is confirmed with the same two-button `ANO` / `NE` alert as saving. This is an inference
rather than a stated requirement — but a confirmation was asked for on a *non-destructive* save, so
omitting one on an irreversible delete would be the inconsistent choice.

`MyCreatedMealListViewModel` holds the fetched meals, reloads on `.task`, and refreshes after the
editor reports a change through the existing `onSaved`-style callback pattern. It owns
`FetchMyCreatedMealsUseCase` and `DeleteMyCreatedMealUseCase`; the editor owns create and update.
Deletion is optimistic — remove the row, restore it and raise `alertItem` on failure — matching the
optimistic-toggle-and-revert shape `FavouriteToggling` established.

### Localization

New keys in `Localizable.xcstrings` (source language `cs`), exposed through a new
`L10n.MyCreatedMeal` namespace, plus one key on `L10n.AddFood` (the section header and the button
belong to the sheet that renders them, matching how `addFood_section_favourites` is scoped at
`L10n.swift:57`) and two on `L10n.Common` (`ANO`/`NE` are generic and this will not be the last
confirmation in the app).

| Key | cs | en |
|---|---|---|
| `addFood_section_myCreatedMeals` | Vlastní jídla | My meals |
| `addFood_button_createMeal` | Vytvořit vlastní jídlo z potravin | Create a meal from foods |
| `myCreatedMeal_title_new` | Nové vlastní jídlo | New meal |
| `myCreatedMeal_title_edit` | Upravit vlastní jídlo | Edit meal |
| `myCreatedMeal_field_namePlaceholder` | Název jídla | Meal name |
| `myCreatedMeal_section_ingredients` | Suroviny | Ingredients |
| `myCreatedMeal_ingredients_empty` | Vyhledejte a přidejte alespoň jednu surovinu | Search and add at least one ingredient |
| `myCreatedMeal_confirm_create` | Vytvořit toto jídlo? | Create this meal? |
| `myCreatedMeal_confirm_update` | Uložit změny? | Save changes? |
| `myCreatedMeal_confirm_delete` | Smazat toto jídlo? | Delete this meal? |
| `myCreatedMeal_error_saveFailed` | Jídlo se nepodařilo uložit | Could not save the meal |
| `myCreatedMeal_error_deleteFailed` | Jídlo se nepodařilo smazat | Could not delete the meal |
| `myCreatedMeal_list_title` | Vlastní jídla | My meals |
| `myCreatedMeal_list_empty` | Zatím nemáte žádné vlastní jídlo | You have no meals yet |
| `mealTypeSheet_button_edit` | Upravit | Edit |
| `mealTypeSheet_button_editDone` | Hotovo | Done |
| `common_button_yes` | Ano | Yes |
| `common_button_no` | Ne | No |

The grams placeholder is the literal string `100`, and the `g` unit label after the field is the
literal string `g`; neither is localized — it is a unit, and the `g` suffix follows the same
hard-coded convention `BaseDoubleTextField` already uses.

### File-by-file impact

New:

- `Kalorie/Core/Models/MyCreatedMealModel.swift` — `MyCreatedMealDomain`,
  `MyCreatedMealIngredientDomain`, `asFoodItem()`
- `Kalorie/Core/Networking/FireStone/MyCreatedMealDTO.swift`
- `Kalorie/Core/UseCases/FetchMyCreatedMealsUseCase.swift`, `CreateMyCreatedMealUseCase.swift`,
  `UpdateMyCreatedMealUseCase.swift`, `DeleteMyCreatedMealUseCase.swift`
- `Kalorie/Features/MyCreatedMeal/` — `MyCreatedMealEditorView.swift`,
  `MyCreatedMealEditorViewModel.swift`, `MyCreatedMealEditorConfigurator.swift`,
  `MyCreatedMealListViewModel.swift`
- `MacroKit/src/commonMain/kotlin/…` — `weightedMeanPerHundredGrams`, plus its commonTest
- `KalorieTests/FetchMyCreatedMealsUseCaseTests.swift`, `CreateMyCreatedMealUseCaseTests.swift`,
  `UpdateMyCreatedMealUseCaseTests.swift`, `DeleteMyCreatedMealUseCaseTests.swift`,
  `MyCreatedMealEditorViewModelTests.swift`, `MyCreatedMealListViewModelTests.swift`

Changed:

- `Constants.swift` — `myCreatedMeals(userId:)`
- `AddFoodSheetViewModel.swift` / `View.swift` / `Configurator.swift` — the section, the
  `displayedResults` layer, the bottom button and the dismiss handoff
- `DashboardView.swift` / `DashboardViewModel.swift` / the Dashboard router — the fifth sheet, the
  `onDismiss` handoff, and the meal-layout toolbar button becoming icon-only
- `MealTypeSheetView.swift` / `MealTypeSheetConfigurator.swift` — the inline *Vlastní jídla* section
  and the *Upravit*/*Hotovo* capsule button replacing the pencil/checkmark toolbar pair
- `FoodQuantityViewModel.swift` — default quantity and unit parameters
- `PendingMergeSnapshotStore.swift`, `MigrateAnonymousDataUseCase.swift` (see *Cross-cutting*)
- `DeleteAccountUseCase.swift` (see *Cross-cutting*)
- `L10n.swift`, `Localizable.xcstrings`
- `AddFoodSheetViewModelTests.swift`, `MigrateAnonymousDataUseCaseTests.swift`,
  `DeleteAccountUseCaseTests.swift`

Tests follow the `makeSUT()` + `Fake` conventions and target the use-case layer. Per Rule 9, five
carry intent rather than mechanics: the density test encodes that composing then scaling must round
exactly once (assert a fractional-calorie meal at a fractional weight against the hand-computed
value, not against the implementation); the `canSave` test encodes that the button's predicate and
the use case's validation cannot drift, by driving both from the same cases; the update test encodes
that editing must not reset `created_at` **or** alter already-logged entries; and the merge and
deletion tests encode that a created meal is user data with the same lifecycle guarantees as a
logged meal.

## Alternatives considered

- **Variant B — N per-ingredient `foodConsumed` documents.** Rejected for the four reasons in *The
  central decision*. The strongest single one: there is no delete path for a logged entry today, so
  Variant B makes one tap produce N permanently unremovable Dashboard rows.
- **Variant B plus a `meal_group_id` grouping field.** The honest version of Variant B. Rejected: it
  makes group-awareness a cross-cutting concern of `groupedFoods`, `DailyMacros(foods:)`,
  `FoodConsumedDetailViewModel` and `UpdateFoodConsumedUseCase` all at once, and it still cannot
  answer "what gram quantity of the meal did you eat" without proportionally rewriting N documents.
- **A synthetic entry in the shared `foodItems` catalogue**, so nothing downstream changes at all.
  Rejected outright: `foodItems` is the shared, moderated catalogue, and `TODO.md` states these
  meals stay private and unapproved. It would also need a fake barcode, which
  `CreateFoodItemUseCase.swift:37` would have to be weakened to accept.
- **A new document shape for logged meals**, distinct from `FoodConsumedDTO`. Rejected: it forks the
  Dashboard's fetch, cache, grouping and summing paths in two for a row that renders identically —
  and the decision that the Dashboard shows no distinction makes a second shape pure cost.
- **Reusing `AddFoodSheetView` as the ingredient picker**, via a mode enum or by extracting its
  search half. Rejected by the screen layout: the editor needs the search field *inline*, below the
  ingredient list, adding rows in place rather than navigating. Sharing the `SearchFoodItemsUseCase`
  gets the real reuse; sharing the view would mean threading a second outcome through a view model
  whose every path currently ends in a write.
- **Reference-only ingredients (`{ food_item_id, grams }`).** Keeps macros permanently fresh, and is
  more viable here than in 0003 because catalogue-only search guarantees every ingredient exists in
  `foodItems`. Rejected on read count: rendering the *Vlastní jídla* section would cost 1 + N reads
  per meal on every sheet presentation instead of one, and a catalogue item can still be deleted
  later, which turns a stale macro into a missing ingredient.
- **A subcollection for ingredients.** Rejected: 1 + N reads per meal and a non-atomic fan-out on
  every edit, to escape a 1 MiB limit that ~250-byte ingredients will not approach.
- **Storing a precomputed density, or a precomputed `total_grams`, on the meal document.** Rejected:
  a denormalised field that can silently disagree with the ingredient array it summarises, for an
  arithmetic operation over at most a few dozen doubles.
- **Summing with `List<Macros>.total()`** from MacroKit. Rejected on arithmetic: `Macros.calories` is
  `Int` and `Macros.scaled` rounds, so the composition would round N times and again at log time —
  the exact double-rounding class of bug commit `27c9419` fixed. The weighted mean over `Double`s
  rounds once.
- **Computing the density in Swift** rather than adding to MacroKit. Rejected: it is pure macro
  arithmetic over `Double`s that a second client must reproduce **identically** or the same meal
  yields different calories per platform. That is precisely MacroKit's charter (design 0004).
- **A new KMP module for the whole composition concept** — density plus validation plus total
  weight, mirroring how `MealKit` and `TextKit` were carved out in 0005. Tempting, and it is the
  shape to revisit when Android starts. Rejected for v1: the density already has a correct home in
  MacroKit, which leaves the new module holding a five-line validation predicate whose divergence
  costs a sloppy meal rather than a wrong number — too little to pay for a Gradle build, an
  XCFramework and CI time on every commit. See *Shared logic (KMP)*.
- **Putting the validation predicate into MacroKit** to get it shared at zero infrastructure cost.
  Rejected: it is not macro arithmetic, and the first function admitted on convenience grounds is
  how the one module every client depends on becomes the junk drawer.
- **Reusing `BaseDoubleTextField` for the grams input.** Rejected: it binds `Double`, which cannot
  express "not yet filled", so the `100` placeholder and the must-fill rule are both unrepresentable.
  Adding a placeholder parameter would touch all twelve existing call sites in `addCustomFoodItem`
  for one new consumer.
- **Extending `AlertItem` to carry a second button** for the save confirmation. Rejected: `AlertItem`
  means "something failed" everywhere it is used today, and merging confirmation into it is how a
  cancel button ends up dismissing an error. A local two-button `.alert` is smaller and clearer.
- **Saving directly from the checkmark, with no confirmation.** Rejected by decision. Note the cost
  it accepts: a confirmation on every save makes iterative editing (add ingredient, fix a gram
  value, save) two taps instead of one.
- **Presenting the editor from inside `AddFoodSheetView`** as a nested sheet, instead of dismissing
  first. Rejected by decision, and it would also stack two modals — but it is worth recording that
  it is the shape that avoids the `onDismiss` timing hazard entirely, should that hazard prove
  worse on device than expected.
- **A swipe action or context menu on the created-meal row** in the add-food sheet, instead of a
  management list. Cheapest option and no new screen. Rejected: 0003 explicitly rejected a second
  gesture on a sheet row, and it hides both editing and deleting behind a gesture with no visible
  affordance — on a row whose visible behaviour is "logs the meal".
- **A row accessory** (an "i" or a chevron) in the *Vlastní jídla* section that opens the editor
  while the row body still logs. Rejected: two tap targets in one `List` row is the exact mis-tap
  hazard 0003 rejected, and here the mis-tap logs a meal the user meant to edit — it writes a
  document rather than merely navigating wrongly. It also still leaves deletion homeless.
- **A new, dedicated Dashboard toolbar item** for the management section. Rejected: both toolbar
  slots are taken (`DashboardView.swift:114-127`) and the bottom inset is the add-food FAB, so a
  fourth affordance would compete with the screen's primary action for an occasional management
  task. The section is reached through the existing *Rozvržení jídel* toolbar button instead, which
  needed no new slot.
- **A dedicated pushed screen**, reached either from a row in `AccountView` or from a "see all" row
  inside the *Vlastní jídla* section itself. Rejected: a generic account screen does not match the
  user's mental model for managing composed meals, and a "see all" row would add a second layer of
  navigation for content the inline section already shows in full.
- **Naming it `Recipe`.** Free namespace, no collision with the meal-window vocabulary, and the term
  every comparable app uses. Rejected by decision in favour of `MyCreatedMeal`; the collision cost
  is recorded under *Naming*.
- **Nested created meals.** Rejected as a non-goal: cycle detection and recursive density for a case
  nobody has asked for.
- **Retroactively updating logged entries when a meal is edited.** Rejected: every other logged entry
  in this app is a snapshot, and rewriting history silently changes days the user has already
  reviewed.
- **Create + delete only, deferring edit to v2** (the shape 0003 used for favourites). Rejected: the
  editor is the same screen for both, so edit costs one use case and a preloaded draft, while its
  absence forces a user to rebuild an eight-ingredient meal to fix one gram value.

## Cross-cutting concerns

- **Merge on sign-in (Cross-platform).** `PendingMergeSnapshot` currently carries `foodConsumed` and
  `favouriteFoods` (`PendingMergeSnapshotStore.swift:10-32`). Without a change, an anonymous user who
  composes meals and then signs in loses them — the failure ADR 0002 exists to prevent. Add
  `myCreatedMeals: [MyCreatedMealDTO]`, load it in `migrate(fromAnonymousUserId:)`
  (`MigrateAnonymousDataUseCase.swift:41-50`) and `batchSetAsync` it in `writeAndCleanup` (`:65-72`).
  Conflicts need no resolution — ids are UUIDs, so a meal present on both sides overwrites itself.
  The snapshot is persisted as JSON on disk, so an app updated mid-merge could load an old file:
  decode `myCreatedMeals` with `decodeIfPresent … ?? []`, exactly as `favouriteFoods` already does at
  `:30`.
- **Account deletion (Cross-platform).** `DeleteAccountUseCase` deletes `mealTypes`, `foodConsumed`
  and `favouriteFoods` document by document (`DeleteAccountUseCase.swift:44-57`); Firestore does not
  cascade, so `myCreatedMeals` would survive the account as orphaned data. Add the same loop. A
  privacy obligation, not tidiness.
- **The meaning of `food_item_id` (Backend).** No schema change, but a **semantic** change a second
  client must know about: the field is no longer guaranteed to be a barcode resolvable in
  `foodItems`. Any client reading it must tolerate a miss. Digits ⇒ catalogue barcode, UUID ⇒
  private created-meal id.
- **New MacroKit function (Cross-platform).** `weightedMeanPerHundredGrams` must produce identical
  results on every client, which is why it lives in the shared module rather than being
  reimplemented. The full sorting of this feature's logic into shared / documented-only /
  platform-local is in *Shared logic (KMP)*; the one rule an Android client must implement by hand
  is the composition validity predicate.
- **Anonymous users.** Everything lives under `users/{uid}`, which exists for anonymous users too
  (ADR 0001), so created meals work signed out with no special case.
- **Offline.** Reads and writes go through the Firestore SDK cache; the editor is entirely local
  until the confirmation is accepted. No custom queue.
- **Second platform.** The collection layout, the snapshot shape, the density arithmetic, the
  `food_item_id` semantics and the merge/deletion obligations are `Backend` / `Cross-platform`. The
  editor layout, the entry point, the confirmation alert and the use-case split are `iOS`.
- **Privacy.** A private subcollection under the owner-only rules. No new PII, nothing exposed to the
  shared catalogue, no moderation path — exactly what `TODO.md` specifies for this data kind.

### Scope boundary against adjacent TODO items

- **User-submitted food** — untouched. That feature is about getting an item into the *shared*
  catalogue through moderation; this one is explicitly private and unmoderated. The editor's search
  now reaches OpenFoodFacts directly (see *The editor screen*), so the two no longer meet at the point
  the original draft described.
- **Rank search results by frequency** — untouched, and composes: created meals occupy a band above
  favourites in `displayedResults`, and frequency ranking can later order the catalogue matches
  below them. One note for whoever implements it: a logged created meal's `food_item_id` is not a
  catalogue id, so a naive frequency count keyed on that field will produce phantom entries. That is
  a constraint on the frequency feature, not a change here.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| The dismiss-then-present handoff races | The bottom button works intermittently — worst on a fast tap, and invisible in a simulator screenshot | Set `showMyCreatedMealEditor` **only** in the add-food sheet's `onDismiss:`, never alongside `shouldDismiss`. Verify on device with a deliberate fast tap, not just in the canvas. This is the most likely way this feature ships broken |
| Cooking loss — 300 g of ingredients becomes 250 g of porridge | Logging the real cooked weight under-counts calories by the evaporation ratio; logging the composed weight over-counts the portion | The known modelling flaw, accepted for v1 and named in *Non-goals*. Mitigated in practice because the quantity screen defaults to the full composed weight, which is the arithmetically correct total for the whole meal. The v2 fix is an optional "final weight" on the meal that overrides `totalGrams` in the density denominator — an additive field, no migration |
| Ingredient snapshots go stale, N-fold | A created meal logs outdated macros indefinitely, and compounds across its ingredients | Accepted for v1; `FoodConsumedDTO` and `FavouriteFoodDTO` have the same property. If it bites, refresh the snapshots by `food_item_id` when the meal is opened in the editor — a localised change, no schema migration |
| `canSave` and the use case's validation drift | The button enables for a meal the use case then rejects, or disables for one it would accept | One shared predicate, and a test that drives both from the same cases. Listed as an intent-carrying test above |
| Confirmation on every save | Iterative editing costs two taps per save, which is felt most while fixing a single gram value | Accepted by decision. If it grates, the narrower rule is to confirm on create and on destructive edits only |
| The composition validity rule is documented, not extracted | An Android client implements it differently and writes meals iOS considers malformed — a 0.5 g ingredient, or an untrimmed name | Accepted deliberately: the failure is a sloppy meal, not a wrong macro, because the density is shared through MacroKit and computes correctly regardless. The rule is written out in *Shared logic (KMP)* and is the first thing to extract when the Android client starts |
| `food_item_id` is no longer always a catalogue barcode | A future consumer written against the old meaning silently mis-resolves | Resolved: `food_item_kind` (added for A2-1) makes the origin explicit instead of relying on id-shape inference |
| Two guaranteed-miss Firestore reads when opening a logged meal's detail | Wasted round trips | Small and bounded — the screen is opened deliberately, one entry at a time. The favourite button is hidden rather than shown-disabled, so the reads have no visible UI cost beyond the round trip itself |
| The management section is buried inside the meal-layout sheet | A user never finds out their meals can be edited or deleted, and re-creates one to fix a gram value | Accepted: it sits above *Rozvržení jídel*, a sheet the user already opens to manage how their day is organised — the same mental model as managing composed meals. If it proves too hidden, the cheap escalation is a *Vlastní jídla* row in the add-food sheet's section footer, not a third Dashboard toolbar item |
| Two screens can now write the same meal | The editor pushed from the section and a stale section behind it disagree after a save | The section reloads on the editor's save callback, the same `onSaved` pattern `AddFoodSheetViewModel.onFoodConsumedSaved()` already uses. Worth one view-model test per direction (save, delete) |
| `editMode`'s environment leaks visual edit affordances onto *Vlastní jídla* | Rows would show a delete circle or drag handle while *Rozvržení jídel* is being reordered, reading as broken | Verified on-device: SwiftUI only produces `.onDelete`/`.onMove` controls on rows that declare them, and *Vlastní jídla*'s `ForEach` declares neither. A single shared `List` was sufficient |
| *Hotovo* is `.disabled()` until a reorder happens, and it is the sheet's only way out of edit mode (`DismissToolbarItem` and interactive dismiss are both suppressed while `editMode == .active`) | A user who taps *Upravit* without meaning to has no way back to `.inactive` without first making a change | Fixed before ship, in this same commit: the button is always tappable, and `onSaveReorder()` skips the network write when `hasPendingReorder` is false instead of the button being disabled |
| Unbounded created meals | The sheet becomes a wall of rows before the user types, and the in-memory filter runs over a bigger array per keystroke | `limit: 50`, the same cap and reasoning as favourites. At that size the filter is free |
| A meal whose ingredients sum to zero grams | Division by zero in the density | The `grams >= 1` rule makes it unreachable through the UI, the use cases validate it independently, and `weightedMeanPerHundredGrams` returns `0.0` as a third line of defence. One test per guard |
| `UpdateMyCreatedMealUseCase` uses `setAsync`, which overwrites | A dropped field, the fourth instance of this hazard in the codebase | Contained by taking a whole `MyCreatedMealDomain` rather than a patch, so there is no field to omit. One test asserts `created_at` survives an update |
| Created meals hoisted above a better catalogue match | The user types a prefix and their own meal outranks the food they meant | Only meals matching the **same prefix rule** are hoisted, and a created meal is by definition something this user built and named |

## Outcome

Shipped as designed, in one commit: `weightedMeanPerHundredGrams`, the data layer, merge/deletion,
the editor + entry point, search integration, and the *Vlastní jídla* management section inside
`MealTypeSheetView`.

- **`displayedResults` had a self-exclusion bug in this document's own snippet**, caught by a
  test rather than found on device. `matchingIds` was built from both `matchingMeals` and
  `matchingFavourites` and then used to filter `matchingFavourites` itself, so every favourite's
  own id was already in the set it was being tested against — every favourite silently vanished
  from any non-empty search, a regression from 0003. Fixed to the three-tier shape the prose
  actually described: `matchingFavourites` is filtered only against meal ids, and the union of
  both is used solely to filter `localFoodItems`. Flagged to the user before fixing, per standing
  instruction not to resolve doc/implementation discrepancies unilaterally; confirmed to proceed
  with the fix above.
- **The editor's `NavigationStack` moved out of the view and into its call sites**, which this
  document did not anticipate. The screen is reached two ways — a `.sheet` from the add-food
  button (needs its own `NavigationStack` for the toolbar to render) and a `NavigationLink` push
  from the *Vlastní jídla* section (must **not** wrap a second `NavigationStack` inside
  `MealTypeSheetView`'s own). `DashboardView` and `MealTypeSheetView` each wrap the editor content
  in whichever stack they already own. `DismissToolbarItem` is shown only when `!isEditing` (the
  editor) or `editMode == .inactive` (the meal-layout sheet), matching the existing precedent
  (`FoodConsumedDetailView`) that a pushed screen relies on the native back button rather than a
  second explicit close control.
- **`FoodQuantityViewModel`'s new parameters were `quantity`/`unit` with defaults**, not the
  `initialQuantity`/`initialUnit` naming the prose implied — matching the existing `quantity`/`unit`
  `@Published` property names avoids a second, confusing vocabulary for the same value.
- **The management section's placement moved during implementation, before anything was pushed.**
  This document originally specified a dedicated `MyCreatedMealListView` reached from a row in
  `AccountView`. Before that reached the shared branch, feedback was that a generic "Účet" screen
  did not match the user's mental model for managing composed meals, so the section moved inline
  into `MealTypeSheetView` instead — the version described above and the only one that shipped.
  `MyCreatedMealListViewModel` is unchanged by the move; only its host view differs from the
  original draft.
- **`MealTypeSheetView`'s pencil/checkmark toolbar pair became a section-header capsule button**
  (*Upravit*/*Hotovo*) as part of the same placement change, to make room for the new section
  without a third toolbar affordance on that sheet. *Hotovo* is `.disabled()` until the order
  actually changes, since `onDelete` already persists immediately and a plain enter-then-exit has
  nothing to save.
- Manual verification performed: full test suite green, zero SwiftLint violations, `xcodebuild
  build` succeeds and the app launches in the simulator. The `editMode` scoping between *Vlastní
  jídla* and *Rozvržení jídel* was verified visually on an iOS 26.5 simulator. **Not** verified on
  this pass: the fast-tap race on the dismiss → present handoff (trap 1) and some on-device visual
  checks (favourite button visible-but-disabled on a logged meal's detail screen, sign-in merge
  with meals composed anonymously) — these need a human on a real device or simulator session
  driving the UI, which this implementation pass did not have. Recorded here rather than silently
  assumed to pass, per the handoff's Definition of done.
- Everything else — the data model, the KMP density function, the validation predicate shared
  between `canSave` and the use cases, the merge/deletion obligations, the search hoisting order —
  matches this document as written.
- **Revised after shipping: the editor's search gained the barcode scanner and OpenFoodFacts
  fallback**, reusing `DataScannerRepresentable`, `FetchFoodItemByBarcodeUseCase`,
  `FetchFoodByBarcodeExternallyUseCase` and `SearchFoodExternallyUseCase` — the same wiring
  `AddFoodSheetView` already had. The original "local catalogue only" decision rested on a false
  premise: an ingredient is a **snapshot** (per *Data model*), the same shape of snapshot logging a
  food for the day already takes, so there was never a reason to compose from a narrower set of foods
  than the one you can log with. See the rewritten *The editor screen* section and the removed *Risks*
  row. `MyCreatedMealEditorViewModelTests` covers the same cases
  `AddFoodSheetViewModelTests` does for barcode scanning, plus the local-vs-external search gating.
- **The grams input's placeholder and fill behaviour changed before ship**, replacing the original
  `1g`-placeholder-only spec described above. Selecting a search result now leaves `gramsText`
  empty, moves keyboard focus straight to that row's grams field (placeholder `100`, plus a static
  `g` label after the field instead of baked into the placeholder), and only backfills `100` if the
  user defocuses the field without typing anything — via `onGramsFieldDefocused(id:)` on the view
  model, driven by an `@FocusState private var focusedIngredientId: UUID?` in the view. Selecting a
  result also clears the search field and its results, which the original spec omitted. Reasoning
  above under *The grams input* and *Layout*.
