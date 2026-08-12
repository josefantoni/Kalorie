# Design: Favourite foods

- **Status:** Draft
- **Scope:** Backend, Cross-platform, iOS
- **Date:** 2026-08-10

## Context and scope

Adding food today goes through `AddFoodSheetView`. The user types into a search field; after a
300 ms debounce `SearchFoodItemsUseCase` runs two prefix queries against the shared `foodItems`
catalogue (`cz_name_lowercase`, `eng_name_lowercase`, `limit: 10` each). Only when the local
result is empty *and* the query has at least three characters does `SearchFoodExternallyUseCase`
fall back to OpenFoodFacts. Tapping a row pushes `FoodQuantityView`, which scales the macros and
writes a fully denormalised `FoodConsumedDTO` into `users/{userId}/foodConsumed`.

Before the user types anything the sheet is empty — a search field and nothing else. Every
logging of a food a user eats daily costs the same typing as the first time.

This document designs **explicit favourites**: the user marks a food on its detail screen — either
on the way to logging it, or afterwards from the Dashboard — the marked foods appear before
typing, matching favourites are hoisted to the top of the search results once typing starts, and
the mark is visible on every row that shows one.

### Conflict with the TODO wording

`TODO.md` describes *Favourite foods* as an implicit feature — "show a Favourites section
**ordered by how often they have logged each food**". That is the same mechanism as the separate
TODO item *Rank search results by frequency*, only rendered in a different place; the user never
decides what is in the list.

The two are different features and this document deliberately picks the explicit one:

- **Explicit favourites** (this document) — the user decides. Predictable, removable, works for a
  food eaten rarely but always in the same way.
- **Frequency ranking** (separate TODO item, unchanged) — the app decides. Better for ordering
  search results, worse as a curated list, because the user cannot get rid of an entry.

They compose: explicit marks decide *which* rows float to the top, and frequency ranking can later
order everything below them. The TODO entry for favourites should be reworded to match, and the
"ordered by how often" clause moved to the frequency item.

## Goals

- A user can mark any number of foods as favourite; the list is private to that user.
- The mark is set **on the food detail and nowhere else** — no swipe action, no context menu, no
  tappable icon in a row. "Food detail" means both screens that show one food: `FoodQuantityView`
  on the way to logging it, and `FoodConsumedDetailView` when tapping an already logged entry on
  the Dashboard. The same button in both.
- A change made on `FoodQuantityView` propagates back to the sheet within a single presentation.
- With an empty search field the sheet shows a *Favourites* section; tapping an entry goes
  straight to `FoodQuantityView`, exactly as a search result does.
- With a non-empty search field, favourites matching the query are the first rows of the result
  list, above the rest of the catalogue matches.
- Favourites survive sign-in (anonymous → Apple/Google merge) and are removed with the account.
- No change to `firestore.rules`.

## Non-goals

- **Frequency ranking or auto-favourites** — the separate TODO item. Ordering *inside* the
  favourites list is `favourited_at`, never a usage count; nothing in this feature reads how often
  a food was logged. The known gap this leaves: a user who logs the same food every day but never
  marks it gets no help at all. That is real, and it is the frequency item's job — a curated list
  cannot fix it without becoming the derived list this design rejected. Until that item ships,
  the mitigation is discoverability of the mark itself, not a heuristic.
- **Migrating or backfilling food logged before this feature ships.** `food_item_id` is required,
  not optional, and existing development documents get deleted rather than migrated — see *The
  Dashboard entry point*. This is a decision about a pre-release app and does not survive first
  release: once real users have logged food, the same change would need a migration.
- Manual reordering, folders, notes, or a default portion size per favourite.
- Sharing favourites between users, or favourites influencing the shared catalogue.
- Anything beyond the offline behaviour the Firestore SDK already provides.

## Design

### Data model

```
users/{userId}/favouriteFoods/{foodItemId}
```

The document id is `FoodItemDomain.id`. That id is the barcode: `CreateFoodItemUseCase` rejects
anything that is not a non-empty run of digits, and the OpenFoodFacts `code` has the same shape,
so it is always a safe Firestore document id and the collection is de-duplicated for free —
favouriting twice is idempotent.

The document stores a **snapshot** of the food, not a reference:

```swift
struct FavouriteFoodDTO: Codable {
    let id: String
    let czName: String            // "cz_name"
    let engName: String           // "eng_name"
    let weight: Double
    let date: TimeInterval
    let energyKJ: Double?         // "energy_kj"
    let caloriesPerHundredGrams: Double   // "calories_per_hundred_grams"
    let fat: Double
    let fatSaturated: Double?     // "fat_saturated"
    let fatUnsaturatedFattyAcids: Double  // "fat_unsaturated_fatty_acids"
    let carbohydrate: Double
    let carbohydratePureSugar: Double     // "carbohydrate_pure_sugar"
    let fiber: Double?
    let protein: Double
    let salt: Double
    let favouritedAt: TimeInterval        // "favourited_at"
}
```

The field set is `FoodItemDTO` plus `favouritedAt`; reuse its `CodingKeys` naming so the two
documents read the same in the Firestore console. `favouritedAt` orders the section — most
recently marked first, which needs no extra bookkeeping and behaves sensibly when the list grows.

Snapshotting is the same choice the app already makes for `FoodConsumedDTO`, and it buys three
things: the section renders from **one** query, it works for an OpenFoodFacts item that is not in
the shared catalogue, and it works offline from the SDK cache. The cost is staleness — see
*Risks*.

### Security rules

No change required. The existing block already covers every subcollection under a user:

```
match /users/{userId} {
  allow read, write: if request.auth != null && request.auth.uid == userId;
  match /{document=**} { allow read, write: if ... }
}
```

Verify this holds after implementing rather than assuming it; the authentication review found a
rules regression exactly here.

### Use cases

Four, following the existing `Create…` / `Delete…` pairing (`CreateMealTypeUseCase` /
`DeleteMealTypeUseCase`) rather than a single `Toggle…`. The ViewModel already knows the current
state, so a toggle use case would only hide a branch that belongs in the ViewModel.

```swift
protocol FetchFavouriteFoodsUseCaseProtocol {
    func callAsFunction() async throws -> [FoodItemDomain]
}

protocol AddFavouriteFoodUseCaseProtocol {
    func callAsFunction(_ item: FoodItemDomain) async throws
}

protocol RemoveFavouriteFoodUseCaseProtocol {
    func callAsFunction(id: String) async throws
}

protocol IsFavouriteFoodUseCaseProtocol {
    func callAsFunction(id: String) async throws -> Bool
}
```

The fourth exists only for the Dashboard, which shows one food and must not load the whole
favourites list to answer one boolean. It needs no new provider method — `FavouriteFoodDTO` has an
`id` field, so the existing `loadAsync(from:where:isEqualTo:) -> T?` answers it in one read, the
same call shape `FetchFoodItemByBarcodeUseCase` already uses against `foodItems`.

All three take `FirestoreDataProviderProtocol` + `AuthProviderProtocol` and throw
`AuthError.notAuthenticated` when `userId` is nil, like `SaveFoodConsumedUseCase`.
`AddFavouriteFoodUseCase` uses `setAsync(_:id:in:)` (upsert, no duplicate check needed);
`RemoveFavouriteFoodUseCase` uses `deleteAsync(id:from:)`. Both already exist on the provider.

`FetchFavouriteFoodsUseCase` needs the **one new provider method** in this design:

```swift
func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T]
```

Mirror the existing `loadAsync(from:where:hasPrefix:limit:)` in style and logging. Call it with
`orderBy: "favourited_at", descending: true, limit: 50` — see *Risks* on the cap.

Add `Constants.Firestore.favouriteFoods(userId:) -> "users/\(userId)/favouriteFoods"` next to the
existing helpers.

### AddFoodSheetViewModel

New state:

```swift
@Published private(set) var favouriteFoods: [FoodItemDomain] = []
@Published private(set) var favouriteIds: Set<String> = []
```

`favouriteIds` is derived from the same single fetch and answers "is this search result a
favourite?" without a per-row read. Favourites are small enough that loading the whole set once
per sheet presentation is cheaper than any lookup scheme.

- `onAppear()` — loads favourites once; failure leaves the section hidden and shows no alert
  (a missing shortcut list must not block logging food). Wire it as `.task { await
  viewModel.onAppear() }`; note the existing `.task(id: viewModel.searchText)` already fires on
  appear for the search path and must stay separate.
- `isFavourite(_ item: FoodItemDomain) -> Bool` — `favouriteIds.contains(item.id)`.
- `onFavouriteChanged(id: String, isFavourite: Bool, item: FoodItemDomain)` — **not** a use-case
  call. The sheet never writes; it only reflects what `FoodQuantityViewModel` already committed,
  by inserting into or removing from `favouriteIds` and `favouriteFoods`. A newly marked food goes
  to the front of `favouriteFoods`, matching the `favourited_at desc` order the next fetch will
  return.

Because the sheet holds no mutating use case, `AddFoodSheetConfigurator` builds only
`FetchFavouriteFoodsUseCase` for it; the two mutating ones go to the `FoodQuantity` graph.

The section is rendered when `searchText.isEmpty && !favouriteFoods.isEmpty`. No empty-state
placeholder — an empty section is noise on a screen whose job is the search field.

### Favourites first in the search results

Once the field is non-empty the section disappears and the favourites instead reorder the result
list. Because `favouriteFoods` is already fully in memory, this needs **no extra query, no index
and no server-side ordering** — it is a local partition of what the search already returned, plus
the favourites the search did not return at all:

```swift
var displayedResults: [FoodItemDomain] {
    let query = searchText.lowercased()
    guard !query.isEmpty else { return foodItems }
    let matchingFavourites = favouriteFoods.filter {
        $0.czName.lowercased().hasPrefix(query) || $0.engName.lowercased().hasPrefix(query)
    }
    let matchingIds = Set(matchingFavourites.map(\.id))
    return matchingFavourites + foodItems.filter { !matchingIds.contains($0.id) }
}
```

Three properties of this shape are deliberate:

- **The prefix rule is the same one the Firestore query uses** (`cz_name_lowercase` /
  `eng_name_lowercase`, `hasPrefix`), so a favourite is never hoisted above a result the user
  would not consider a match. The snapshot stores the display-cased names, so lowercase in memory;
  do **not** add lowercase fields to `FavouriteFoodDTO` just for this.
- **Deduplication is by `id`**, and it removes from the lower list, not the upper one — a
  favourite that is also a catalogue hit appears once, at the top, with its heart.
- **It runs before the 300 ms debounce fires.** Favourite matches therefore appear on the first
  keystroke while the catalogue results arrive a moment later. That is a feature, not a race: the
  rows the user is most likely to want are the ones that need no round trip.

It also closes a hole the previous draft accepted: a favourite that is an OpenFoodFacts item and
not in `foodItems` was invisible to search. It is now reachable by typing its name, because the
filter runs over the snapshots rather than the catalogue.

The external-results section is unaffected — it still only appears when the local result is empty,
which now means "no catalogue hit **and** no favourite hit".

### FoodQuantityViewModel

This is where the whole write path lives. It gains `@Published private(set) var isFavourite: Bool`,
injected as an initial value by the caller (the sheet knows it already), both mutating use cases,
and `onFavouriteToggled() async`: flip `isFavourite` first, call `AddFavouriteFoodUseCase` or
`RemoveFavouriteFoodUseCase`, and on failure flip back and set `alertItem`. Optimistic because the
button must feel instant and the failure is recoverable by tapping again.

Note the screen is reachable from the *Favourites* section too, so a food arriving with
`isFavourite: true` and being unmarked there is a normal path, not an edge case.

Propagating the change back to the sheet follows the existing `onSaved` callback pattern:
`FoodQuantityViewModel` takes `onFavouriteChanged: (String, Bool) -> Void`, which
`AddFoodSheetViewModel` uses to update `favouriteIds` and `favouriteFoods`. The closure is passed
through `AddFoodSheetView.makeFoodQuantityView`, whose signature therefore grows a third
parameter; both call sites (`AddFoodSheetConfigurator` and the `#Preview`) must be updated, and
the trailing-closure convention still applies to the last parameter.

`AddFoodSheetConfigurator` builds all three use cases from the `dataProvider` / `authProvider` it
already holds — no new dependency reaches the configurator — and hands the two mutating ones to
the `FoodQuantityViewModel` it creates, keeping the sheet's own graph read-only.

### The Dashboard entry point

Putting the same button on `FoodConsumedDetailView` is the one requirement in this design that
changes a stored document, because a logged entry currently cannot say which catalogue item it
came from. `FoodConsumedDTO` holds a fresh `UUID`, the two names, and macros **already scaled to
the eaten weight** — no barcode, and no per-100 g values. A favourite, meanwhile, is keyed by the
barcode and stores per-100 g values. The gap is real, not cosmetic.

**The field.** `FoodConsumedDTO` gains `foodItemId: String` (`"food_item_id"`), carried into
`FoodConsumedDomain`, written by `SaveFoodConsumedUseCase` from `item.id`. **Non-optional** — the
app is mid-development and there is no logged food worth preserving, so the field is a plain
requirement rather than a compatibility branch. That removes the `nil` case from every call site:
no hidden button, no half-resolved state, no backfill question.

The premise has a price and it must be paid deliberately: a non-optional field means **any
`foodConsumed` document written before this change fails to decode**, and it takes the whole day's
fetch with it — the Dashboard would show an error, not a shorter list. So the change ships with a
one-off step: delete the existing `foodConsumed` documents in the development Firestore project.
Doing it as a documented step is the point; discovering it as a decode error on device is not.

**The trap, and why `String` beats `String?` here.** `UpdateFoodConsumedUseCase` rebuilds
`FoodConsumedDTO` field by field and writes it with `setAsync`, which overwrites the whole
document — the third instance of that hazard in this codebase, after the `UserProfileDTO`
overwrite in *Alternatives*. Had the field been optional, forgetting it there would compile fine
and silently strip the catalogue link from every edited entry. Required, the memberwise init
refuses to compile until the use case passes it. The compiler does the work the reviewer would
otherwise have to; the test in `UpdateFoodConsumedUseCaseTests` then only has to prove the value
is the right one, not that it exists.

**The screen.** `FoodConsumedDetailViewModel` gains `isFavourite`, `IsFavouriteFoodUseCase`, the
two mutating use cases, and `FetchFoodItemByBarcodeUseCase` — which already exists and does
exactly what is needed here.

- `onAppear()` — resolve the favourite state with `IsFavouriteFoodUseCase(id: food.foodItemId)`
  and fetch the catalogue item by the same id. The button is always present; only its enabled
  state depends on what comes back.
- **Why the catalogue fetch:** adding a favourite needs the per-100 g snapshot, and the logged
  entry cannot supply it — its macros are scaled by the eaten weight and its calories are stored
  as a rounded `Int`, so dividing back introduces drift, and `energyKJ` and `fatSaturated` are not
  stored at all. Removing needs only the id, so removal works even when the catalogue fetch fails.
- The one branch left: an id that no longer resolves to a catalogue item. Then the button stays
  visible and disabled for adding, while unfavouriting still works.

Both mutating calls are the same use cases `FoodQuantityViewModel` uses, with the same optimistic
flip and revert. Nothing propagates back to the Dashboard list — it renders no hearts, so there is
no second state to keep in sync.

### View

The two roles are split cleanly across the two screens: the sheet **shows** the state, the detail
**sets** it.

#### Sheet row — indicator only

Name on the leading edge, a filled heart on the trailing edge, and **nothing at all** in the
trailing slot when the food is not a favourite. There is no outline state and the heart is not
tappable. Both the search rows and the favourites rows therefore collapse into one small
component:

```swift
struct FoodItemRow: View {
    let item: FoodItemDomain
    let isFavourite: Bool
}
```

```swift
HStack {
    Text(item.displayName)
        .frame(maxWidth: .infinity, alignment: .leading)
    if isFavourite {
        BaseImage(name: .heartFill)   // trailing, tint below
    }
}
```

Selection stays exactly as it is today — the caller keeps `.onTapGesture { … }` on the whole row.
The row has one tap target, so the two-Buttons-in-one-`List`-row problem the earlier draft had to
work around does not arise at all, and neither does the mis-tap risk.

#### The two detail screens — the only control

One capsule button, `Oblíbené` + a heart, with the state carried by fill vs. outline. Identical on
`FoodQuantityView` and `FoodConsumedDetailView`:

```swift
Button {
    Task { await viewModel.onFavouriteToggled() }
} label: {
    HStack(spacing: 6) {
        Text(L10n.Common.buttonFavourite)
        BaseImage(name: viewModel.isFavourite ? .heartFill : .heart)
    }
    .padding(.horizontal, 16)
    .padding(.vertical, 10)
}
.buttonStyle(.plain)
.foregroundStyle(viewModel.isFavourite ? Color.white : .red)
.background {
    Capsule()
        .fill(viewModel.isFavourite ? Color.red : .clear)
        .strokeBorder(Color.red, lineWidth: 1)
}
.contentShape(.capsule)
```

- **The shape is `Capsule()`**, which is exactly "corner radius equal to half the height" and
  stays correct when Dynamic Type grows the label — a hardcoded `cornerRadius` does not.
- `.strokeBorder` (not `.stroke`) draws the 1 pt line *inside* the shape, so the filled and the
  outlined state have identical outer dimensions and the button does not visually jump on toggle.
  Drawing the border in both states, rather than only in the unset one, is what keeps the size
  stable; over the fill it is invisible.
- `.buttonStyle(.plain)` + `.contentShape(.capsule)` — inside a `List` row the default button
  style would make the entire row tappable, and the capsule would stop being the hit area.

**Placement:** its own `Section` after the nutrition section, one row, capsule centred, with
`.listRowBackground(Color.clear)` and `.listRowSeparator(.hidden)`. Both screens are a `List` with
a quantity row and a nutrition section, so the same placement lands in the same visual position on
each — which is what makes it read as one control rather than two. Below the macros because it is
the secondary action on a screen whose job is entering a quantity; still above the fold on a
normal phone, since the whole list is seven rows. The primary action (*Add* / *Save*) stays alone
in the toolbar — the two do not compete for the same corner.

Because the button is now built twice, it becomes `Kalorie/Components/FavouriteButton.swift`
taking `isFavourite: Bool` and an action closure. Two call sites earn a component; one would not.

### The mark itself

Which symbol carries "favourite" in this app. The candidates considered:

| Symbol | Reads as | Apple precedent for "Oblíbené" | Against |
|---|---|---|---|
| `star` / `star.fill` | favourite, generic | Safari, Maps | Overused; carries a "rating" connotation from every other app |
| `heart` / `heart.fill` | a food I like | **Photos** (the Czech UI literally labels the heart *Oblíbené*) | Slight risk of reading as "healthy" in a nutrition app |
| `bookmark` / `bookmark.fill` | saved for later | News, Safari Reading List | Reads as *saved*, not *favourite*; weaker fit for a food |
| `pin` / `pin.fill` | pinned to the top of the list | Notes, Messages | Literally accurate but not an established "favourites" idiom |

**Decided: `heart`, everywhere.** Direct Apple precedent (Photos) for the exact Czech word the
section uses, visually distinct from the star the rest of the category leans on, and a heart on a
food needs no explanation. One symbol for one concept: the button and the row indicator are the
same feature and must look like it.

Rendering:

- **Row** — `heart.fill` only, and **nothing rendered at all** when the food is not a favourite.
  Not a dimmed outline, not a transparent placeholder: the trailing slot is empty. Ragged trailing
  edges across rows are the intended look — the marks are meant to be scannable, and an outline in
  every row makes them invisible.
- **Button** — `heart.fill` on the fill, `heart` on the outline, sized to the label via
  `.imageScale(.medium)` rather than a fixed point size, so it tracks Dynamic Type. Never
  `BaseImage` at `.extraLarge` here — that is the toolbar mistake the conventions call out.
- **One tint for the whole feature: red.** The button's fill, the button's border and the row's
  heart are all `Color.red`, or the two screens read as two features. On the filled button the
  label and heart flip to white for contrast. Two things to check on device: red is the system's
  destructive colour, so the filled state must not end up next to a delete action in the same
  view, and the white-on-red label needs to survive Increase Contrast — if it does not, the fix is
  a darker red asset, not a lighter label.
- `.contentTransition(.symbolEffect(.replace))` on the heart, plus
  `.symbolEffect(.bounce, value: isFavourite)` on the tap. Both are free on iOS 26 and make the
  toggle feel deliberate rather than accidental. The row's heart appears and disappears with the
  same replace transition when the sheet gets the callback.
- Add `case heart = "heart"` and `case heartFill = "heart.fill"` to `BaseImageName` (it currently
  holds five cases). The outline case exists only for the button; no row ever renders it.
- Accessibility: the button is text-labelled, so it needs only `.accessibilityAddTraits(.isSelected)`
  when set rather than a label that duplicates the word. The row's heart is the opposite case —
  silent to VoiceOver unless told otherwise, so set
  `.accessibilityLabel("\(item.displayName), \(L10n.AddFood.sectionFavourites)")` on the marked row
  and mark the image itself `.accessibilityHidden(true)`.

The section header is *Oblíbené* / *Favourites*, matching the existing
`addFood_section_externalResults` pattern.

### Localization

New keys in `Localizable.xcstrings`, both `cs` and `en` (source language is `cs`):

| Key | cs | en |
|---|---|---|
| `addFood_section_favourites` | Oblíbené | Favourites |
| `addFood_error_favouriteFailed` | Oblíbené se nepodařilo uložit | Could not save favourite |
| `common_button_favourite` | Oblíbené | Favourite |

The first two are exposed through `L10n.AddFood`. The third goes to `L10n.Common`, not to
`L10n.FoodQuantity`, because two features render the same button — putting it under one screen's
namespace would make the other screen import a string it does not own.

The button's label does **not** change between states — the fill carries the state, and a label
that flips to "Odebrat z oblíbených" would make the capsule resize on every tap. No accessibility
strings are needed beyond these: the control is text-labelled and the row reuses the section
title.

### File-by-file impact

New:

- `Kalorie/Core/Networking/FireStone/FavouriteFoodDTO.swift`
- `Kalorie/Core/UseCases/FetchFavouriteFoodsUseCase.swift`
- `Kalorie/Core/UseCases/AddFavouriteFoodUseCase.swift`
- `Kalorie/Core/UseCases/RemoveFavouriteFoodUseCase.swift`
- `Kalorie/Core/UseCases/IsFavouriteFoodUseCase.swift`
- `Kalorie/Components/FoodItemRow.swift` — shared by the favourites section and the search results
- `Kalorie/Components/FavouriteButton.swift` — shared by the two detail screens
- `KalorieTests/FetchFavouriteFoodsUseCaseTests.swift`,
  `AddFavouriteFoodUseCaseTests.swift`, `RemoveFavouriteFoodUseCaseTests.swift`,
  `IsFavouriteFoodUseCaseTests.swift`

Changed:

- `Constants.swift` — `favouriteFoods(userId:)`
- `FirestoreDataProvider.swift` — `loadAsync(from:orderBy:descending:limit:)` + protocol entry
  (every existing `FirestoreDataProviderProtocol` fake in `KalorieTests` must implement it)
- `BaseImageName.swift`, `L10n.swift`, `Localizable.xcstrings`
- `AddFoodSheetViewModel.swift`, `AddFoodSheetView.swift`, `AddFoodSheetConfigurator.swift`
- `FoodQuantityViewModel.swift`, `FoodQuantityView.swift`
- `FoodConsumedDTO.swift`, `FoodConsumedDomain.swift` — `foodItemId: String` (required)
- `SaveFoodConsumedUseCase.swift` — write it; `UpdateFoodConsumedUseCase.swift` — **preserve** it
- `FoodConsumedDetailViewModel.swift`, `FoodConsumedDetailView.swift`, and whichever Dashboard
  configurator builds that screen — four use cases injected
- `PendingMergeSnapshotStore.swift`, `MigrateAnonymousDataUseCase.swift` (see *Cross-cutting*)
- `DeleteAccountUseCase.swift` (see *Cross-cutting*)
- `AddFoodSheetViewModelTests.swift`, `MigrateAnonymousDataUseCaseTests.swift`,
  `DeleteAccountUseCaseTests.swift`, `FoodQuantityViewModelTests.swift`,
  `FoodConsumedDetailViewModelTests.swift`, `UpdateFoodConsumedUseCaseTests.swift`

Tests follow the existing `makeSUT()` + `Fake` conventions and target the use-case layer, with
ViewModel tests for section visibility, optimistic toggle, and revert-on-failure. Per Rule 9 four
of them carry the intent rather than the mechanics: the merge and deletion tests encode that a
favourite is user data with the same lifecycle guarantees as a logged meal; the update test
encodes that editing a portion must not sever the catalogue link; and the Dashboard ViewModel test
encodes that a catalogue item that no longer resolves still allows unfavouriting.

## Alternatives considered

- **Reference-only favourite documents (`{ id, favourited_at }`).** Keeps macros always fresh.
  Rejected: rendering the section then needs either N document reads or a
  `whereField(FieldPath.documentID(), in:)` query capped at 30 ids, and neither works for an
  OpenFoodFacts item that is not in `foodItems`. The app already denormalises `FoodConsumedDTO`
  the same way, so the snapshot is the consistent choice, not a shortcut.
- **An array of favourite ids on the `users/{userId}` profile document.** Rejected on two counts:
  it mixes feature data into the auth profile, and `SignInWithAppleUseCase` /
  `SignInWithGoogleUseCase` write `UserProfileDTO` with `setAsync`, which **overwrites** the whole
  document — every sign-in would silently wipe the array.
- **Local-only storage (UserDefaults / a file).** Rejected: the point of the authentication work
  is that user data follows the account across devices, and favourites are user data.
- **Frequency-derived favourites** (the TODO's original wording). Rejected as *this* feature: a
  list the user cannot edit is a ranking, not a favourites list. Kept as the separate TODO item.
- **A single `ToggleFavouriteFoodUseCase`.** Rejected: it would have to read current state to
  decide, duplicating what the ViewModel already knows, and it breaks the one-responsibility
  shape every other use case in the project has.
- **An outline heart on every non-favourite row.** Rejected: with a control in every row the mark
  stops being scannable — twenty grey outlines and one filled heart read as a form, not as a list
  with three foods called out. It also forces two tap targets into a `List` row, whose failure
  mode is a mis-tap that silently unfavourites a food the user meant to log. The cost of dropping
  it is that the row can no longer set the mark at all — which is what moves the whole control to
  the detail screen.
- **A separate *Favourites* section above the results while searching.** Rejected in favour of one
  list with favourites hoisted: a section header claims the two groups are different kinds of
  thing, when they are the same foods differing only in rank. The filled hearts already explain
  why the order is not pure relevance, and one list means the user's eye does not have to decide
  which group to read first. The section survives only for the empty-query state, where there is
  no result list to merge into.
- **Reconstructing the per-100 g snapshot from the logged entry** instead of adding
  `food_item_id`. Rejected on arithmetic, not on taste: `calories` is stored as a rounded `Int`
  and scaled by the eaten weight, so dividing back drifts (a 37 g portion round-trips to a
  different per-100 g value than the catalogue holds), and `energyKJ` and `fatSaturated` are not
  stored at all. It would also produce a favourite with no id, and the id is the document key.
- **Matching the logged entry to the catalogue by name** instead of storing the id. Rejected:
  names are not unique in a barcode-keyed catalogue, and a wrong match writes someone else's
  macros into a favourite.
- **`food_item_id` as `String?`**, tolerating documents written before the change. Rejected while
  the app is pre-release: the optional would buy compatibility with development data nobody needs,
  at the price of a `nil` branch in the Dashboard, a "no button" state to design and test, and a
  silent-overwrite bug in `UpdateFoodConsumedUseCase` that the compiler would no longer catch.
  Deleting the old documents costs one action, once. Note this reasoning expires at first release.
- **A second entry point in the sheet** — swipe action, context menu, or a tappable heart in the
  row. Rejected by decision: the control lives on the detail screens, one place per food, which is
  easier to explain than three and keeps the row a single tap target with the heart doing one job.
  The cost is that unmarking requires opening the food — and the Dashboard entry point now means
  that "the food" can be one already logged, which is the case where reopening is cheapest.
- **Hoisting by a server-side query** (a favourites-aware search). Rejected: the whole favourites
  set is already in memory for the section, so the hoist is a local filter — a second query would
  add an index, latency and a failure mode for zero benefit.

## Cross-cutting concerns

- **Merge on sign-in (Cross-platform).** `PendingMergeSnapshot` currently carries only
  `foodConsumed`. Without a change, an anonymous user who marks favourites and then signs in
  loses them — the exact failure ADR 0002 exists to prevent. Add
  `favouriteFoods: [FavouriteFoodDTO]` to the snapshot, load it in `migrate(fromAnonymousUserId:)`
  and `batchSetAsync` it in `writeAndCleanup`. Conflicts need no resolution: the document id is
  the food id, so a favourite present on both sides overwrites itself.
  Note the snapshot is persisted as JSON on disk, so an app updated mid-merge could load an old
  snapshot without the field — decode `favouriteFoods` with a default of `[]`.
- **Account deletion (Cross-platform).** `DeleteAccountUseCase` deletes `mealTypes` and
  `foodConsumed` document by document; Firestore does **not** cascade, so `favouriteFoods` would
  survive the account as orphaned data. Add the same loop. This is a privacy obligation, not a
  tidiness one.
- **A new required field on an existing document (Backend).** `food_item_id` on
  `users/{userId}/foodConsumed` is the only schema change to something that already exists, and it
  is the one thing here a second client must know about — it is **required**, so a client that
  writes `foodConsumed` without it produces documents this app cannot decode at all. Always
  written on create, always preserved on update; and the one-off deletion of pre-change
  development documents happens when this ships, while the app has no released users.
- **Anonymous users.** Everything lives under `users/{uid}`, which exists for anonymous users too
  (ADR 0001), so favourites work signed out with no special case.
- **Offline.** Reads and writes go through the Firestore SDK cache; the optimistic UI covers the
  latency and the SDK replays writes. No custom queue.
- **Second platform.** The collection layout, the snapshot shape, and the merge/deletion
  obligations are `Cross-platform` — an Android client that skips the merge step corrupts the same
  account. The row layout, the symbol, and the use-case split are `iOS`.
- **Privacy.** A private subcollection under the owner-only rules; no new PII, nothing exposed to
  the shared catalogue.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Snapshot goes stale when the maintainer corrects a catalogue item | A favourite logs outdated macros indefinitely | Accepted for v1; `FoodConsumedDTO` has the same property. If it bites, re-read the item by barcode when the favourite is tapped and refresh the snapshot — a localised change, no schema migration |
| Favouriting an OpenFoodFacts item that is not in `foodItems` | Favourites quietly become a private mini-catalogue parallel to the shared one | Accepted and intended — the snapshot makes it self-contained, and the in-memory hoist means such an item is still findable by typing its name. Worth revisiting alongside *User-submitted food*, which is the proper path for getting such an item into the shared catalogue |
| Unbounded favourites | The sheet becomes a wall of rows before the user types, and the in-memory hoist filter runs over a bigger array on every keystroke | `limit: 50` on the query; at that size the filter is free. If the cap is ever hit in practice, add a "show all" screen rather than raising it |
| The mark can only be set on a food detail | A user never finds out favourites exist, or has to open a food just to unmark it | Small: the detail screens sit on the path to **every** logged food, both before logging and after, and the control there is a text-labelled capsule, not a bare icon — it is read, not decoded. Unmarking costs one extra tap, which is the right side of the trade against a mis-tap that silently unfavourites a food from the list |
| A `foodConsumed` document written before the field exists survives the cleanup | It fails to decode, and it takes the whole day's fetch down with it — an error screen, not a missing row | Delete the development `foodConsumed` documents as part of shipping this, and verify the Dashboard on a fresh account afterwards. This is the cost of a required field, taken knowingly because the app is pre-release |
| `UpdateFoodConsumedUseCase` drops `food_item_id` | Editing a portion severs the catalogue link | Largely neutralised by the field being required: the memberwise init stops compiling until every construction site passes it, so the omission is a build error rather than a silent write. The remaining failure is passing a wrong or empty value, which `UpdateFoodConsumedUseCaseTests` covers by editing a weight and asserting the id survived |
| A favourited food's catalogue item disappears from `foodItems` | The Dashboard button cannot build a snapshot to add | Removal still works (it needs only the id), and the button stays visible-but-disabled for adding rather than vanishing, so the state is legible instead of mysterious |
| One-way sync from detail to sheet | The sheet's `favouriteIds` drifts from Firestore if the callback is missed on some path | The callback fires from the same place that commits the write, and the fetch on the next sheet presentation is authoritative. Worth one ViewModel test per direction (mark, unmark) |
| Hoisted favourites push down a better catalogue match | The user types a prefix, the exact item they want is third | Only favourites matching the **same prefix rule** are hoisted, so every hoisted row is a legitimate match; and a favourite is by definition a food this user chose |
| New protocol method on `FirestoreDataProviderProtocol` | Every fake in `KalorieTests` stops compiling at once | Expected and cheap; add the method to all fakes in the same commit |

## Outcome

Shipped as designed, in two commits (`food_item_id` prerequisite, then the feature itself).

- Existing development `foodConsumed` documents predating the required `food_item_id` field caused the
  anticipated decode failure on first run after shipping — confirmed on device, fixed by deleting the
  stale documents as documented above, not by relaxing the field back to optional.
- One deviation from the closure-signature wording in *FoodQuantityViewModel*: propagating both the
  injected initial `isFavourite` and the `onFavouriteChanged` callback through
  `AddFoodSheetView.makeFoodQuantityView` needed two new parameters, not the one the text implied —
  the closure went from `(FoodItemDomain, () -> Void)` to `(FoodItemDomain, Bool, () -> Void, (String, Bool) -> Void)`.
  Behaviour matches the rest of the design; only the parameter count differs from the prose.
- Verified manually on a simulator: the Favourites section renders on empty search, the button toggles
  correctly on `FoodQuantityView`, and the mark round-trips through Firestore.
