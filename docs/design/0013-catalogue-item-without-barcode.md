# Design: Manual entry of a catalogue item without a barcode

- **Status:** Proposed
- **Scope:** Backend, Cross-platform, iOS
- **Date:** 2026-09-18

## Context and scope

Some foods have neither a barcode nor a nutrition label: salt, loose fruit and vegetables, bulk
goods. They are also frequently missing from OpenFoodFacts in Czech ("kukuřice" never resolves
externally). Today a user cannot log them at all, for two independent reasons:

1. [Design 0010](0010-nutrition-label-photo-prefill.md), *Camera-first flow*, Decision 1 made the
   new-item tab a capture prompt with **no manual-entry path** — "a catalogue item without a
   readable label is not wanted". Its Risks table accepted that such a food cannot be submitted.
2. The barcode is the item's identity. `foodItems`, `favouriteFoods` and `foodItemPortions` are
   keyed by it (`ARCHITECTURE.md` § 1.2), `foodItemReports` by `{barcode}_{userId}`,
   `FoodItemValidation.validate` requires an 8/12/13-digit id, and `firestore.rules` checks the
   same regex in `validFoodItem`, on `foodItems` create and on `foodItemReports` create.

**This design supersedes design 0010's Decision 1.** A readable label is still the preferred path
and stays the prominent one; it is no longer the only one.

## Goals

- A user can reach the new-item form without the camera, from the capture prompt and from the
  camera-denied state alike.
- The barcode field is optional. An empty barcode shows an inline warning under the field and,
  on submit, a confirmation alert; confirming submits the item without a barcode.
- A barcode-less item gets a stable identity that works unchanged for favourites, portions,
  reports, logged entries (`food_item_id`) and the moderation flow.
- The maintainer sees, while reviewing a barcode-less submission, catalogue items with a similar
  name, so duplicates can be rejected rather than approved.

## Non-goals

- **Adding a barcode to an already-approved barcode-less item.** The identity is fixed at
  submission time; an item that later turns out to have a barcode is a new catalogue item. Fixing
  this would mean decoupling identity from barcode for every item (see *Alternatives*).
- **Merging duplicate catalogue items.** Duplicates are prevented at review time only.
- **Detecting duplicates among pending submissions.** Five users submitting "kukuřice" produce five
  queue entries. Once the maintainer approves the first, the remaining four show it as a similar
  catalogue item during review, so sequential review covers this without a separate check.
- **Duplicate lookup for submissions that have a barcode.** The existing existence check on
  `foodItems/{barcode}` covers exact duplicates there.

## Design

### Entry point

`AddFoodSheetMode.newItem` keeps the camera icon and explanatory text from design 0010. Below them
sits a plain text button, *"Nebo přidejte položku ručně"*, which opens the same form the camera
flow lands on after capture, with every field empty and no `recognizedFields`. The button is also
shown in the `.denied` / `.restricted` state below the *open Settings* button, since that state
currently offers no way to add an item at all.

### Barcode optional in the form

- The barcode row in `FoodItemFormSections` stays editable. When it is empty, a warning line is
  shown under it: *"Bez čárového kódu potravinu nenajdete při vyhledávání katalogu přes čárový kod"*.
  Colour: yellow (was red until 2026-09-19, changed by decision) — a warning the user should notice
  even though it does not block submission. It disappears as soon as the barcode field is non-empty.
- On submit with an empty barcode, a confirmation alert is shown before anything is written:
  *"K jídlu nemáte přiřazený čárový kód. Opravdu chcete pokračovat?"* with *Ne* (cancel, stay on
  the form) and *Ano* (submit). `AlertItem` supports a single button only, so this is a separate
  `@Published var isMissingBarcodeConfirmationVisible` bound to a two-button `.alert`.
- The same alert fires in the camera path when the scanner captured no barcode, since both paths
  end in the same form and the same submit action.

### Identity of a barcode-less item

A barcode-less item's `id` is the **UUID of its submission**.

- `FoodItemSubmissionWriter.write(id:item:...)` already receives the submission id. When
  `item.id` is empty it replaces it with that id before validation, so `item.id == submission.id`.
  An edit of the author's own submission (`UpdateMySubmissionUseCase`) goes through the same writer
  with the same id, so the identity is stable across resubmits. Adding a barcode during an edit
  replaces the UUID with the barcode, removing it does the opposite; both are fine while the
  submission is pending.
- `ApproveSubmissionUseCase` needs no change: `CreateFoodItemUseCase` already writes under
  `item.id`, which is now either a barcode or the submission UUID.
- A user's own pending submission is already loggable from search, and the entry's
  `food_item_id` is `item.id`. With this scheme that id is exactly the document id the item gets
  on approval, so entries logged before approval keep pointing at the right item afterwards.
- `UUID().uuidString` is uppercase hex with dashes, so it can never collide with an all-digit
  barcode, and `{id}_{userId}` report ids stay unambiguous.

`FoodItemDomain` gains a computed `barcode: String?`: `id` when it is a valid barcode (the check
`FoodItemValidation` does today, moved into one shared predicate), `nil` otherwise. Every place
that shows or uses the id *as a barcode* switches to it, so a UUID is never shown in a barcode
field or sent to OpenFoodFacts:

- `FoodItemFormInput` built from an item (`AddFoodSheetViewModel` sets `scannedCode: item.id`
  today) takes `item.barcode ?? ""`.
- `ModerationReviewView` and the catalogue editor show *"bez čárového kódu"* instead of the id.
- `AddFoodSheetViewModel` matches a search result to the user's own submission with
  `$0.barcode == item.id`; this becomes `$0.item.id == item.id`, which holds for both kinds.

`FoodItemSubmissionDTO.barcode` becomes optional and is omitted for a barcode-less submission.
It exists for rules and queries; nothing queries by it. `FoodItemReportDTO.barcode` keeps its
wire name for compatibility with existing reports but now holds the item id, which may be a UUID —
naming debt, recorded here rather than migrated.

`FoodItemValidation.validate` accepts an id that is either a valid barcode or a UUID in
`uuidString` form. `FoodItemValidationError.invalidCode` stays for a non-empty malformed barcode.

### Security rules

A second id pattern is added next to the barcode regex:

```
function validItemId(id) {
  return id.matches('[0-9]{8}|[0-9]{12}|[0-9]{13}') ||
    id.matches('[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}');
}
```

It replaces the barcode regex in `validFoodItem(data)`, on `foodItems` create (`itemId`), and on
`foodItemReports` create (`request.resource.data.barcode`). The `foodItemSubmissions` update
rule's `request.resource.data.barcode == resource.data.barcode` compares two absent fields for a
barcode-less submission; that must be verified in the Rules Playground rather than assumed.

The uppercase pattern is a cross-platform contract: a second client must generate the id in the
same form (`UUID().uuidString` on iOS, uppercased on other platforms).

### Similar names during moderation review

`ModerationReviewViewModel` gains `searchFoodItems: any SearchFoodItemsUseCaseProtocol`. When the
reviewed submission has no barcode, `onAppear` runs `searchFoodItems(query: submission.item.czName)`
and publishes the result as `similarCatalogueItems`. `ModerationReviewView` shows them in a
read-only section above the approve/reject actions: name, kcal per 100 g, measure unit. An empty
result shows *"Žádné podobné položky v katalogu"* so the maintainer can tell "none found" from
"not loaded". A failed search is logged and shows the section as unavailable; it does not block
approval.

This reuses the app's normal name search, with its known limit (finding **A2-12**: at most 10
results per field, in index order). For a duplicate check on one short name that is sufficient;
the section is an aid to the maintainer, not a guarantee.

### File-by-file impact

| File | Change |
|---|---|
| `AddFoodSheetView` | *Add manually* button under the capture prompt and in the denied state; missing-barcode alert |
| `AddFoodSheetViewModel` | `onAddManuallyTapped`, `isMissingBarcodeConfirmationVisible`, submit split into confirm + write; `item.barcode` in form input; submission matching by `item.id` |
| `FoodItemFormSections` | warning line under an empty barcode row |
| `FoodItemDomain` | computed `barcode: String?` |
| `FoodItemValidation` | barcode-or-UUID id check; shared barcode predicate |
| `FoodItemSubmissionWriter` | empty `item.id` → submission id; `barcode: item.barcode` |
| `FoodItemSubmissionDomain` / `DTO` | `barcode: String?` |
| `ModerationReviewViewModel` / `View` | similar-name lookup and section; no-barcode label |
| Moderation configurator | inject `SearchFoodItemsUseCase` |
| `firestore.rules` | `validItemId` in three places |
| `L10n` / `Localizable` | button, warning, alert, similar-items section strings |

## Alternatives considered

- **Decouple identity from barcode for every item** — `id` always a UUID, `barcode` an optional
  queryable field. Cleaner, and would allow adding a barcode later, but requires migrating every
  `foodItems`, `favouriteFoods` and `foodItemPortions` document and turning the scanner's document
  read into a query. Out of proportion to the problem.
- **Client-generated UUID independent of the submission id.** Works, but adds a second identifier
  for the same thing, and entries logged before approval would need it carried separately.
- **Deterministic id from the normalised name** (e.g. `name:kukurice`). Would dedupe identical
  names automatically, but two genuinely different items with the same name would collide, and
  renaming in moderation would change the identity.
- **Keep design 0010's Decision 1.** Leaves the motivating foods impossible to log.

## Cross-cutting concerns

- **Second client:** must accept both id shapes everywhere an item id is handled, generate
  uppercase UUIDs, and never treat an item id as a barcode without the same predicate.
- **Rules verification:** the widened regexes and the absent-`barcode` comparison need Rules
  Playground / emulator checks, like design 0012's rules.
- **Tests:** `FoodItemValidationTests` (UUID accepted, malformed barcode rejected, lowercase UUID
  rejected), `SubmitFoodItemUseCaseTests` (empty id becomes the submission id; barcode kept when
  present), `ModerationReviewViewModelTests` (search runs only for barcode-less submissions; a
  search failure does not block approval), `AddFoodSheetViewModelTests` (empty barcode shows the
  confirmation and writes nothing until confirmed).

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Duplicate barcode-less items approved | search shows several entries for one food | similar-name section in review; residual risk accepted |
| Users skip the barcode even when the item has one | item cannot be found by scanning | inline warning plus confirmation alert |
| Search misses a duplicate (A2-12 limits, different wording) | duplicate approved | accepted; the section is an aid, not a guarantee |
| Barcode known only after approval | a second item with the barcode is created | accepted as a Non-goal |

## Outcome

_Filled in once after shipping._
