# Design: Food portions

- **Status:** Implemented
- **Scope:** Backend, Cross-platform, iOS
- **Date:** 2026-09-09

## Context and scope

`TODO.md` lists *Package/portion weight and quick-add gram amounts* as two related but separate
pieces: (1) a food item carrying a known package/portion weight, selectable as a unit when
logging it, and (2) per-user "frequently added weights" derived from `foodConsumed` history. This
document designs **only (1)**. Frequency-derived quick-add stays a separate, untouched future
item — see *Non-goals*.

Today `FoodQuantityView` offers exactly two units, `.grams` and `.hundredGrams`
(`FoodQuantityViewModel.swift:10-20`). `FoodItemDomain.weight` already carries a single number —
"the package weight the entry was read from" (`ARCHITECTURE.md` §1.4) — but nothing in the UI
exposes it as a selectable unit for a catalogue item; it is used today only as the default
quantity when logging a `MyCreatedMeal` in full (`AddFoodSheetView.swift:315`).

The concrete want, established in conversation: a food can carry one or more **named** gram
shortcuts — "1 balení" at 33 g for a muesli bar, "1 krajíc" at 65 g for a self-made loaf that in
practice comes out at a different weight each time. Two kinds of shortcut, with different
ownership:

- **Canonical** — set once, when the food/meal is created, and visible to whoever can see that
  food. For a catalogue item this means every user of the app.
- **Personal** — added later by a specific user for a specific item ("I usually eat two bars at
  once"), visible only to them.

The free-text gram entry `FoodQuantityView` already has is untouched by this document — a portion
is a shortcut into that field, never a restriction on it. Typing a different number always
remains possible.

### Why canonical portions are write-once for catalogue items

`foodItems` is the shared, unmoderated catalogue. `firestore.rules` grants `allow create` on it
with per-field validation and **no `allow update`** — update is refused by Firestore's default
deny. `ARCHITECTURE.md` §2.6 records this as a deliberate fix: an earlier, broader `allow write`
(covering update and delete, no field validation, no audit trail) was the subject of
[ADR 0011](../adr/0011-foodItems-writable-by-any-authenticated-client.md)'s accepted risk, and was
narrowed to create-only once the race it enabled was understood. (ADR 0011's own rule text no
longer matches what is deployed — it still describes the broader `allow write`. That is a
documentation drift finding, not something this design corrects; recorded here only because it is
the reason "just add `allow update`" looks like a two-line change and is not.)

Reopening update for a shared, unmoderated document is out of scope for this document by
decision. Correcting a wrong canonical value on an existing catalogue item — "the packet actually
says 30 g, not 33 g" — needs the moderation flow `TODO.md` and ADR 0011 already name as the
planned replacement for unrestricted catalogue writes (a pending-collection + maintainer-approval
path). This document does not build that flow; it only makes sure nothing here works against it
later. See *Non-goals*.

`myCreatedMeals` carries no such constraint — it is private, user-owned data with a full
create/update path already (`CreateMyCreatedMealUseCase`, `UpdateMyCreatedMealUseCase`), so a
meal's own portions are ordinary editable fields, no split needed.

## Goals

- A food/meal can carry zero or more named portions (`name`, `grams`), set at creation time:
  - for a catalogue item, through the existing "add a new food" form,
  - for a `MyCreatedMeal`, through the existing editor.
- `FoodQuantityView`'s unit picker offers every canonical portion the selected item carries,
  alongside the existing `g` / `100 g` options, labelled with both the name and the gram value
  (e.g. "1 balení (33 g)").
- A user can add a **personal** portion to any catalogue item — new or already in the catalogue —
  from `FoodQuantityView`, and it appears in that picker for them from then on, for that item,
  regardless of who created the catalogue entry.
- A user can remove a personal portion they added. Canonical portions cannot be removed or edited
  from this UI (write-once, see *Context and scope*).
- Portions survive sign-in (anonymous → Apple/Google merge) and are removed with the account, like
  every other piece of user data.
- No change to `firestore.rules`.

## Non-goals

- **Editing or removing a canonical portion after the food/meal that owns it was created.**
  Belongs to the moderation flow `TODO.md` already plans for catalogue corrections generally, not
  to this document. Confirmed in conversation: v1 only ever *adds* (a canonical portion at
  creation, or a personal one at any time), never corrects one already written.
- **Personal portions on OpenFoodFacts (`.external`) items.** `weight` is hardcoded to `100` for
  these (`ARCHITECTURE.md` §2.4) — not a real package size — and OpenFoodFacts' own `quantity`
  field is free text ("6x25g", "1L"), not reliably parseable. Confirmed in conversation: out of
  scope for v1. The personal-portions collection this document adds is keyed by barcode with no
  dependency on catalogue membership, so lifting this restriction later is a UI-only change — no
  schema change — should OpenFoodFacts' `quantity` field turn out worth parsing.
- **A unit picker on `FoodConsumedDetailView`.** That screen edits a logged entry's raw weight as
  a number, with no unit concept at all (`ARCHITECTURE.md` §4.5); this document does not add one.
  Portions apply only to the initial logging screen, `FoodQuantityView`.
- **Changing the default selected unit based on whether portions exist.** A catalogue item still
  opens at `1 × 100 g`, a `MyCreatedMeal` still opens at the full composed weight in grams — both
  unchanged from today. Portions are additional picker entries, not a new default.
- **Frequency-derived quick-add amounts.** The other half of the same `TODO.md` line — gram
  amounts inferred from `foodConsumed` history rather than named by the user. Untouched, separate,
  and composes with this document rather than depending on it.
- **A second, English name field for a portion.** A portion has one free-text `name`, typed once
  by whoever creates it, the same way a manually created catalogue item already has no English
  name field (`ARCHITECTURE.md` §2.6 — `eng_name_lowercase` is written `""` for it).
- **A cap on how many portions a food may carry**, beyond a generous sanity limit (see
  *Validation*). Nothing here ranks or paginates portions; the picker just lists them.

## Design

### Data model

```swift
struct FoodPortionDomain: Equatable {
    let name: String
    let grams: Double
}

struct FoodPortionDTO: Codable {
    let name: String
    let grams: Double
    // no CodingKeys enum — both field names are already snake/camel-identical
}
```

Both live in new files, `Core/Models/FoodPortionModel.swift` and
`Core/Networking/FireStone/FoodPortionDTO.swift`, matching the project's DTO/domain split.

**Canonical portions**, on the two food-shaped documents:

```swift
// FoodItemDTO
let portions: [FoodPortionDTO]?     // "portions", optional — absent on every pre-existing document

// MyCreatedMealDTO
let portions: [FoodPortionDTO]?     // "portions", optional — absent on every pre-existing document
```

Optional on read for the same reason `czNameFolded` / `czNameSearchTerms` are: every document
written before this ships has no such field, and decoding it as `nil → []` costs nothing (single
user, but the pattern is the same one the codebase already uses for every additive field on
`foodItems`).

`FoodItemDomain` and `MyCreatedMealDomain` each gain:

```swift
let portions: [FoodPortionDomain] = []
```

Declared as a **defaulted** stored property, directly on the struct (not routed through the
`extension`-based convenience init, which stays untouched). This is deliberate: `FoodItemDomain(`
has ~13 non-test construction sites and `MyCreatedMealDomain(` has three, almost none of which
have any real portions to supply — a preview, a test fixture, `OpenFoodFactsProductDTO`'s mapping
(portions are out of scope for `.external`, so `[]` is exactly correct there, not a stand-in), an
ingredient-as-`FoodItemDomain` snapshot inside the meal editor. Defaulting the property means only
the handful of sites that have a real value to put there change at all:

- `FoodItemDTO.asDomain()` — `portions: portions?.map { $0.asDomain() } ?? []`
- `FavouriteFoodDTO.asDomain()` — same, once `FavouriteFoodDTO` gains the field below
- `MyCreatedMealDomain.asFoodItem()` — `portions: portions`, so a created meal's own canonical
  portions reach `FoodQuantityView` the same way a catalogue item's do
- `MyCreatedMealDTO.asDomain()` — `portions: portions?.map { $0.asDomain() } ?? []`
- `CreateMyCreatedMealUseCase` / the editor's update call — pass through the drafted portions

**`FavouriteFoodDTO` gains the same field.** It is documented as "a full copy of the
`FoodItemDomain`" (`ARCHITECTURE.md` §1.4); leaving `portions` out would make favouriting a food
silently drop its canonical portions, inconsistent with every other field. Same optionality,
same staleness caveat **A1-7** already accepts for the rest of the snapshot.

**Personal portions**, one new private collection:

```
users/{userId}/foodItemPortions/{barcode}
```

```swift
struct FoodItemPersonalPortionsDTO: Codable {
    let id: String                  // the catalogue item's barcode — matches the doc id, same
                                     // convention as FoodItemDTO.id / FavouriteFoodDTO.id
    let portions: [FoodPortionDTO]
}
```

Keyed by barcode, one document per item the user has personal portions for — not one document
holding every item's portions, so an unrelated food's portions are never read or rewritten when
this one's change. No domain-level wrapper type: the two new use cases (below) work directly with
`[FoodPortionDomain]`, keyed by the `barcode: String` they are called with, the same shape
`FetchFavouriteFoodsUseCase` / `AddFavouriteFoodUseCase` already use for a barcode-keyed
per-user collection.

Add to `Constants.Firestore`:

```swift
static func foodItemPortions(userId: String) -> String { "users/\(userId)/foodItemPortions" }
```

### Firestore rules

**No change.** `users/{userId}/foodItemPortions/**` is already covered by the existing
`match /users/{userId} { … match /{document=**} { allow read, write: if request.auth.uid == userId } }`
block — the same reasoning design 0006 recorded for `myCreatedMeals`, and it is worth re-verifying
after implementing for the same reason that document gives: *"the authentication review found a
rules regression in exactly this block."*

`foodItems`' rule also does not change: canonical portions are written **only** as part of the
existing `create`, inside `CreateFoodItemUseCase`, which already has the right to write every
other field on that document. Nothing here asks for a new permission.

### Validation

```swift
enum FoodPortionError: Error {
    case invalidName
    case invalidGrams
    case tooMany
}

enum FoodPortionValidation {
    static func validate(name: String, grams: Double) -> FoodPortionError?
    static func validate(portions: [FoodPortionDomain]) -> FoodPortionError?  // count only
}
```

Per-portion: trimmed `name` non-empty, `grams >= 1` — the same shape and threshold
`MyCreatedMealValidation` already uses for an ingredient. List-level: a soft cap, **20** portions,
to keep a manually-typed list from growing unbounded; no product reason to allow more, and it
costs one comparison. This is the same shared-predicate pattern design 0006 established for
`MyCreatedMealValidation` — one static validator, called from both a view model's `canSave`-style
property and the use case that actually writes, so the two cannot drift.

- `CreateFoodItemUseCase` validates the drafted portions the same way it validates weight and
  calories, and gains `CreateFoodItemError.invalidPortion`.
- `CreateMyCreatedMealUseCase` / `UpdateMyCreatedMealUseCase` validate the same way through
  `MyCreatedMealValidation`, which gains a `.invalidPortion` case delegating to
  `FoodPortionValidation`.
- The new `SaveFoodItemPersonalPortionsUseCase` (below) validates the same way before writing.

### Use cases

Two new ones, following the project's `FirestoreDataProviderProtocol` + `AuthProviderProtocol`,
`AuthError.notAuthenticated`-on-`nil`-userId shape every other use case here uses:

```swift
protocol FetchFoodItemPersonalPortionsUseCaseProtocol {
    func callAsFunction(barcode: String) async throws -> [FoodPortionDomain]
}

protocol SaveFoodItemPersonalPortionsUseCaseProtocol {
    func callAsFunction(barcode: String, portions: [FoodPortionDomain]) async throws
}
```

- `FetchFoodItemPersonalPortionsUseCase` — `loadAsync(id: barcode, from: foodItemPortions(userId:))`,
  returns `.portions ?? []` when the document does not exist (no personal portions yet — the
  common case for most items).
- `SaveFoodItemPersonalPortionsUseCase` — validates via `FoodPortionValidation`, then
  `setAsync(FoodItemPersonalPortionsDTO(id: barcode, portions: portions), id: barcode, in: …)`.
  Takes the **whole desired list**, like `UpdateMyCreatedMealUseCase` takes the whole meal — the
  caller (which already has the current list, from the fetch above) is responsible for the
  add/remove, not the use case. One use case therefore covers add, remove *and* would cover
  rename, even though rename is not exposed in v1 — there is no dedicated "delete a portion" use
  case; deleting is saving the list without it.

No new use case is needed for the **canonical** side — it rides along inside
`CreateFoodItemUseCase` (a new `portions: [FoodPortionDomain]` parameter, defaulted to `[]` so
every existing call site keeps compiling) and inside `CreateMyCreatedMealUseCase` /
`UpdateMyCreatedMealUseCase` the same way.

### `FoodQuantityUnit` becomes a case with an associated value

```swift
enum FoodQuantityUnit: Hashable {
    case grams
    case hundredGrams
    case portion(FoodPortionDomain)

    var gramsPerUnit: Double {
        switch self {
        case .grams: return 1
        case .hundredGrams: return 100
        case .portion(let portion): return portion.grams
        }
    }
}
```

This drops `CaseIterable` — the set of portions is per-item and only known at runtime, so the
Picker's options are built by the view model rather than enumerated from the type:

```swift
var unitOptions: [FoodQuantityUnit] {
    [.grams, .hundredGrams] + item.portions.map(FoodQuantityUnit.portion) + personalPortions.map(FoodQuantityUnit.portion)
}
```

`item.portions` is already on the `FoodItemDomain` the view model was constructed with — no fetch
needed, canonical portions travel with the item exactly like every other field.
`personalPortions: [FoodPortionDomain]` is a new `@Published` property, loaded once via
`FetchFoodItemPersonalPortionsUseCase` in a new `onAppear()` — this view has none today, so
`FoodQuantityView` gains a `.task { await viewModel.onAppear() }`, matching the project's
side-effect convention — and **only** when `item.kind == .catalogue`, per the OpenFoodFacts
non-goal above. `.external` and `.createdMeal` skip the fetch entirely; `.createdMeal` still shows
its own canonical `item.portions`, just no personal ones (a created meal's shortcuts belong on the
meal itself, editable in its own editor — see below).

Picker label, both for the type-level `Text` and the per-row option: `"\(portion.name) (\(portion.grams.formattedGrams()))"`,
e.g. *"1 balení (33 g)"*, reusing `Double.formattedGrams()` (`ARCHITECTURE.md` §5.5) so the number
follows the device locale like every other gram value in the app. No visual distinction between a
canonical and a personal portion in the list — the name the creator chose already carries that
meaning, and a flat list is simplest; see *Alternatives considered*.

`onUnitChanged(from:to:)` is untouched — it already converts by `gramsPerUnit` alone, which a
`.portion` case now supplies just like the two fixed cases always have.

### Where a canonical portion is authored

**Shared component**, `Components/FoodPortionsSection.swift` — a `List` `Section` of rows (name +
grams, `BaseStringTextField` + a grams `TextField` matching `FoodQuantityView`'s own sanitiser
pattern rather than `BaseDoubleTextField`, for the same "not-yet-filled" reason design 0006 gives
for the meal editor's grams input), swipe-to-delete, and a trailing "+ Přidat porci" row. Backed
by a small `Binding<[FoodPortionDraft]>`:

```swift
struct FoodPortionDraft: Identifiable, Equatable {
    let id = UUID()
    var name: String
    var gramsText: String
}
```

Used in two places, both of which already have a place to hold a `[FoodPortionDraft]` and a
save path to convert it to `[FoodPortionDomain]` (parse `gramsText`, drop rows that don't parse to
`>= 1` before the use case's own validation runs — the same non-blocking-typo tolerance
`MyCreatedMealEditorViewModel.parsedGrams` already has):

1. **`AddFoodSheetView.addCustomFoodItem`** — a new *Porce* `Section`, optional, below the existing
   nutrition fields. `FoodItemFormInput` gains `var portions: [FoodPortionDraft] = []`.
   `onCreateFoodItem()` passes the parsed list to `CreateFoodItemUseCase` via the new
   `FoodItemDomain.portions` argument.
2. **`MyCreatedMealEditorView`** — the same section, below *Suroviny*. `MyCreatedMealEditorViewModel`
   gains `@Published var portions: [FoodPortionDraft] = []`, seeded from `existingMeal?.portions`
   when editing (so this is also how an *existing* meal's canonical portions get edited — never
   write-once here, matching the *Context* distinction: `myCreatedMeals` has no create/update
   asymmetry to begin with). `canSave` / `hasChanges` extend to cover it, same predicate the use
   cases validate.

Reusing one component instead of writing this twice is the only reuse decision in this document
worth calling out — see *Alternatives considered* for what was rejected instead.

### Where a personal portion is authored

`FoodQuantityView` gains a small icon button (bookmark, matching the FAB/icon-button precedent —
not `BaseImage` at a large size) next to the unit picker, shown only when `item.kind == .catalogue`,
opening a `.sheet`: `FoodPortionsManagerView`, a small `List`:

- existing `personalPortions`, one row each, swipe-to-delete,
- a trailing add row: a name field and a grams field, **the grams field pre-filled with the
  quantity screen's current `grams`** (`viewModel.grams`) — the direct answer to "I just weighed
  this, save it as a shortcut" — editable before confirming.

Saving calls `onAddPersonalPortion(name:grams:)` on `FoodQuantityViewModel`, which appends to the
already-fetched `personalPortions`, calls `SaveFoodItemPersonalPortionsUseCase(barcode: item.id, portions:)`
with the full list, and on success updates the published property so the picker's options refresh
immediately; on failure it raises the existing `alertItem`, list unchanged. Deleting a row is the
mirror image — remove locally, save the reduced list, revert and alert on failure. Both follow the
optimistic-then-revert shape `FavouriteToggling` already established
([ADR 0017](../adr/0017-optimistic-favourite-toggle-shared-by-protocol-extension.md)), though as
two plain view-model methods here rather than a shared protocol extension — there is exactly one
call site for each, so extracting a protocol for reuse that does not exist yet would be the
premature abstraction Rule 2 rules out.

`FoodQuantityViewModel`'s `init` gains two more use case parameters:
`fetchFoodItemPersonalPortions`, `saveFoodItemPersonalPortions` — both defaulted nowhere (every
other dependency here is required, not optional), so every call site
(`AddFoodSheetConfigurator`, the `#Preview`) is a compile-time reminder to wire them.

### Localization

New keys, source language `cs`, split across a new `L10n.FoodPortion` namespace (shared between
the two canonical-authoring screens and the personal-portions sheet, since the section header and
field placeholders are identical copy in all three) and a handful under `L10n.FoodQuantity` /
`L10n.AddFood` for the screen-specific bits:

| Key | cs | en |
|---|---|---|
| `foodPortion_section_title` | Porce | Portions |
| `foodPortion_button_add` | Přidat porci | Add portion |
| `foodPortion_field_namePlaceholder` | Název (např. 1 balení) | Name (e.g. 1 package) |
| `foodPortion_error_invalidName` | Zadejte název porce | Enter a portion name |
| `foodPortion_error_invalidGrams` | Zadejte gramáž větší než 0 | Enter a weight greater than 0 |
| `foodPortion_error_tooMany` | Můžete přidat nejvýš 20 porcí | You can add at most 20 portions |
| `foodQuantity_button_myPortions` | Moje porce | My portions |
| `myPortions_title` | Moje porce | My portions |
| `myPortions_empty` | Zatím nemáte žádnou vlastní porci | You have no personal portions yet |
| `myPortions_error_saveFailed` | Porci se nepodařilo uložit | Could not save the portion |
| `myPortions_error_deleteFailed` | Porci se nepodařilo smazat | Could not delete the portion |

The grams field's trailing unit label is the literal `g`, unlocalized, matching
`BaseDoubleTextField`'s existing convention.

### File-by-file impact

New:

- `Kalorie/Core/Models/FoodPortionModel.swift` — `FoodPortionDomain`, `FoodPortionError`,
  `FoodPortionValidation`
- `Kalorie/Core/Networking/FireStone/FoodPortionDTO.swift` — `FoodPortionDTO`
- `Kalorie/Core/Networking/FireStone/FoodItemPersonalPortionsDTO.swift`
- `Kalorie/Core/UseCases/FetchFoodItemPersonalPortionsUseCase.swift`,
  `SaveFoodItemPersonalPortionsUseCase.swift`
- `Kalorie/Components/FoodPortionsSection.swift` — shared canonical-portions editor section,
  `FoodPortionDraft`
- `Kalorie/Features/FoodQuantity/FoodPortionsManagerView.swift` — the personal-portions sheet
- `KalorieTests/FetchFoodItemPersonalPortionsUseCaseTests.swift`,
  `SaveFoodItemPersonalPortionsUseCaseTests.swift`, `FoodPortionValidationTests.swift`

Changed:

- `Constants.swift` — `foodItemPortions(userId:)`
- `FoodItemDTO.swift` / `FavouriteFoodDTO.swift` / `MyCreatedMealDTO.swift` — the optional
  `portions` field, threaded through `init(item:)` / `init(meal:)` and `asDomain()`
- `FoodItemModel.swift` (`FoodItemDomain`) / `MyCreatedMealModel.swift` (`MyCreatedMealDomain`,
  `asFoodItem()`) — the defaulted `portions` property
- `CreateFoodItemUseCase.swift` — new parameter, validation, `.invalidPortion` error case
- `CreateMyCreatedMealUseCase.swift` / `UpdateMyCreatedMealUseCase.swift` — portions threaded
  through, `MyCreatedMealError.invalidPortion`
- `FoodQuantityViewModel.swift` / `FoodQuantityView.swift` — the reworked `FoodQuantityUnit`,
  `personalPortions`, `onAppear()`, the two new use case dependencies, the manager-sheet button
- `AddFoodSheetView.swift` / `AddFoodSheetViewModel.swift` — the new form section,
  `FoodItemFormInput.portions`
- `MyCreatedMealEditorView.swift` / `MyCreatedMealEditorViewModel.swift` — the new section,
  `portions` published property, `canSave` / `hasChanges` extended
- `PendingMergeSnapshotStore.swift`, `MigrateAnonymousDataUseCase.swift`,
  `DeleteAccountUseCase.swift` — see *Cross-cutting concerns*
- `L10n.swift`, `Localizable.xcstrings`
- `AddFoodSheetConfigurator.swift` — the two new `FoodQuantityViewModel` dependencies
- Existing tests unaffected by the defaulted `portions` properties still compile unchanged;
  `CreateFoodItemUseCaseTests`, `CreateMyCreatedMealUseCaseTests`, `UpdateMyCreatedMealUseCaseTests`,
  `FoodQuantityViewModelTests`, `AddFoodSheetViewModelTests`, `MyCreatedMealEditorViewModelTests`,
  `MigrateAnonymousDataUseCaseTests`, `DeleteAccountUseCaseTests` gain cases for the new behaviour.

## Alternatives considered

- **Reopening `foodItems`' `allow update`, scoped to a diff that touches only `portions`.**
  Rejected for v1 by decision in conversation — it revives part of the exact risk ADR 0011's
  narrowing closed (any authenticated client mutating a shared, unaudited document), for a v1 with
  a single user where the personal-portions path already covers the real want ("my own shortcut
  for this item"). The moderation flow is the right place for genuine corrections when it exists.
- **Storing personal portions on the shared `foodItems` document**, e.g. a map keyed by `userId`.
  Rejected: mixes private, unbounded-by-user data into the one document every client reads on
  every search result, growing it for everyone as any single user adds shortcuts, and still needs
  the same update-permission reopening as the previous option.
- **A single scalar override of `weight`** instead of a named array — closer to what already
  exists, cheapest change. Rejected by the conversation's own example: a muesli bar and a
  self-sliced loaf both want *named, possibly multiple* shortcuts ("1 balení" vs. "1 tyčinka" vs.
  "2 tyčinky"), which a single number cannot express, and `weight` already means something else
  (the whole package/composed total) that existing code depends on
  (`AddFoodSheetView.swift:315`'s `myCreatedMeal` default quantity) — overloading it would risk
  that call site silently changing meaning.
- **An alert with an embedded `TextField`** for adding a personal portion, instead of a small
  sheet. Rejected: no precedent in this codebase (`AlertItem` is single-button and means
  "something failed" everywhere it appears — design 0006 explicitly declined to generalise it for
  its own save-confirmation need), whereas a `BaseStringTextField`-based sheet reuses an existing
  primitive and an existing pattern (`MyCreatedMealEditorView`'s own presentation shape).
- **Visually distinguishing canonical from personal portions in the picker** (a section split, an
  icon). Rejected for v1: the portion's own name already carries that meaning if its author chose
  to make it clear, and a flat list is simpler to build and to read. Cheap to add later if it
  proves confusing in practice — a client-side grouping change only.
- **A dedicated "manage my portions" screen reachable from `AccountView`**, independent of
  `FoodQuantityView`. Rejected on the same grounds design 0006 rejected an equivalent for created
  meals: it does not match where the need actually arises — mid-logging, with the gram value
  already typed — and a generic account screen is a worse mental model than "the screen I'm
  already on."
- **Reusing `MyCreatedMealIngredientDraft`'s shape for `FoodPortionDraft`.** Considered, since both
  are `Identifiable` drafts with a text-backed numeric field. Rejected: the ingredient draft
  carries a whole `FoodItemDomain` plus its own search/scan machinery; a portion draft is two
  strings. Sharing the type would either bloat the portion case with unused fields or force the
  ingredient case through an initializer it does not need.

## Cross-cutting concerns

- **Merge on sign-in (Cross-platform).** `PendingMergeSnapshot` gains
  `foodItemPortions: [FoodItemPersonalPortionsDTO]`, loaded alongside the other three in
  `MigrateAnonymousDataUseCase.migrate(fromAnonymousUserId:)` and `batchSetAsync` in
  `writeAndCleanup`, decoded with `decodeIfPresent … ?? []` exactly as `myCreatedMeals` already is
  — an app updated mid-merge must still load an older snapshot file. Conflicts need no resolution:
  a document id is a barcode, and a personal-portions document present on both sides after a merge
  overwrites itself with the anonymous side's list, the same "last write wins, ids can't collide
  meaningfully" reasoning `myCreatedMeals`' merge already accepts.
- **Account deletion (Cross-platform).** `DeleteAccountUseCase.wipeFirestoreData` gains the same
  fetch-then-delete loop over `foodItemPortions(userId:)` as its four existing collections.
  Firestore does not cascade-delete a subcollection, so skipping this leaves orphaned personal
  portions behind — a privacy obligation, not tidiness, same framing design 0006 used for the
  equivalent `myCreatedMeals` gap.
- **`firestore.rules` (Backend).** No change anywhere in this document — re-verify after
  implementing regardless, per the standing note in design 0006.
- **Second platform (Backend / Cross-platform vs. iOS).** The collection layout, the DTO shapes,
  the write-once-canonical / editable-personal split, the validation thresholds and the
  merge/deletion obligations are `Backend` / `Cross-platform` — an Android client must honour the
  same "canonical portions come only from the creating write" rule or the two clients disagree
  about what a catalogue item's portions are. The picker's option-building, the manager sheet, and
  the shared editor section are `iOS`.
- **Anonymous users.** `users/{uid}/foodItemPortions` exists for anonymous users the same way
  every other per-user subcollection does (ADR 0001) — no special case.
- **Offline.** Ordinary Firestore SDK cache behaviour; no custom queue, same as every other
  collection here.
- **Privacy.** Personal portions are exactly as private as `myCreatedMeals` — owner-only rules, no
  new PII, no moderation path. Canonical portions are exactly as visible as the food/meal that
  carries them — no new visibility surface.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| `SaveFoodItemPersonalPortionsUseCase` uses `setAsync`, whole-document overwrite, read-then-write from client state | Two devices editing the same item's personal portions concurrently could drop one side's change | Accepted: single user today, and the same class of risk `UpdateMyCreatedMealUseCase` and `CreateFoodItemUseCase`'s own duplicate-write race (**A2-2**) already carry. Worth a note if a second device becomes real |
| A typo in a canonical portion at creation time is permanent under this design | A wrong "33 g" ships to every user of the catalogue with no in-app fix | By decision: the fix is the moderation flow already planned for catalogue corrections generally, not a special case for portions. Recorded as a `TODO.md` follow-up, not solved here |
| Unbounded canonical or personal portion lists | A long, manually-typed list makes the picker unwieldy | `FoodPortionValidation`'s 20-item cap. Self-limiting in practice — nobody hand-types fifty shortcuts — but the cap exists so a fat-fingered loop (holding "add") fails predictably instead of silently growing forever |
| `FoodItemDomain`/`MyCreatedMealDomain` gain a field with ~16 total construction sites | A missed call site means a food's canonical portions silently do not reach the picker | Defaulted to `[]` rather than required, so every site compiles either way; only the handful listed in *File-by-file impact* need a real value, and each is named explicitly there |
| `.external` items have no personal-portions affordance | A user scanning the same OpenFoodFacts product repeatedly cannot save a shortcut for it | Accepted for v1, confirmed in conversation. The collection is already barcode-keyed with no catalogue dependency, so lifting the `.catalogue`-only guard later is a one-line UI change, no schema change |
| The personal-portions fetch adds one Firestore read to every catalogue item's `FoodQuantityView` appearance | Marginal latency before the picker's full option set is ready | Small and bounded, the same shape `IsFavouriteFoodUseCase`'s single extra read on the detail screen already costs. The `g` / `100 g` options and the free-text field work immediately; only the portion rows populate a moment later |
| Canonical and personal portions can produce visually identical rows (same name, same grams) | A confusing duplicate in the picker | Rare in practice (a user would have had to name their own shortcut identically to an existing canonical one) and harmless if it happens — both select the same weight |

## Outcome

Shipped as designed: `FoodPortionDomain`/`FoodPortionDTO`, canonical portions threaded through
`FoodItemDTO`/`FavouriteFoodDTO`/`MyCreatedMealDTO` and their domains, the
`users/{userId}/foodItemPortions/{barcode}` collection with its fetch/save use cases, the
write-once canonical path in `CreateFoodItemUseCase` and `CreateMyCreatedMealUseCase` /
`UpdateMyCreatedMealUseCase`, the shared `FoodPortionsSection` editor in both authoring screens,
`FoodQuantityUnit` becoming `case portion(FoodPortionDomain)`, the personal-portions manager sheet,
and the merge/deletion handling in `MigrateAnonymousDataUseCase` / `DeleteAccountUseCase`.

Two points where the code differs from the design's literal text, both `iOS`-scoped and neither
changing the wire format or the write-once/editable split other clients must honour:

- **`FoodPortionDomain` is `Hashable`, not `Equatable`.** `FoodQuantityUnit` needs to be `Hashable`
  for SwiftUI's `Picker` selection binding once it carries a `case portion(FoodPortionDomain)`
  associated value, and Swift's `Hashable` synthesis for an enum requires every associated value to
  itself be `Hashable`. `Hashable` is a superset of `Equatable`, so this satisfies the design's
  equality requirement and adds nothing else.
- **`FoodItemDomain.portions` and `MyCreatedMealDomain.portions` are backed by an explicit `init`,
  not a bare `let portions: [FoodPortionDomain] = []` stored-property default.** Verified against
  the current Swift compiler: a stored property with an inline default value is excluded from the
  synthesized memberwise initializer entirely — there is no way to pass a real value for it through
  that initializer, which would have silently made every canonical portion `[]` forever, including
  at the handful of sites this document names as needing a real value. An explicit initializer with
  `portions: [FoodPortionDomain] = []` as its last parameter produces the behaviour the design
  actually wants (most of the ~16 construction sites untouched, the named few pass a real list) —
  it just can't be the bare-default spelling the design shows.

Everything else — the validation thresholds, the barcode-keyed personal-portions document shape,
the `.catalogue`-only guard, the flat unsectioned picker list, the write-once canonical rule, and
the merge/deletion obligations — matches the design as written.
