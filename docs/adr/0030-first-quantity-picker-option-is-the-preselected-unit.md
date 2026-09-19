# 0030. The first quantity picker option is the preselected unit

- **Status:** Accepted
- **Scope:** Cross-platform (the array-order invariant below), iOS (the picker and the default)
- **Date:** 2026-09-17

## Context

[Design 0008](../design/0008-food-portions.md) introduced named portions and a runtime-built unit
picker. Its `**Update — 2026-09-16 (iOS-only)**` paragraph then reversed two of its own decisions:
the literal order of `unitOptions`, and the Non-goal that kept the default unit at `1 × 100 g`. The
shipped result is

```swift
var unitOptions: [FoodQuantityUnit] {
    item.portions.map(FoodQuantityUnit.portion) + personalPortions.map(FoodQuantityUnit.portion) + [.grams, .hundredGrams]
}

static func defaultUnit(for item: FoodItemDomain) -> FoodQuantityUnit {
    item.portions.first.map(FoodQuantityUnit.portion) ?? .hundredGrams
}
```

so today the first option in the list and the preselected unit happen to be the same thing: the
item's first canonical portion.

`TODO.md` asked for a different order — personal portions before canonical ones, on the argument
that the user's own shortcuts are more relevant to them than a stranger's. That entry was filed on
two premises that do not hold: that no order was documented anywhere in `docs/`, and that design
0008 had fixed the default at `1 × 100 g`. Design 0008's Update decided both, with a stated
rationale, and is frozen.

Reordering is therefore not a cosmetic change, because it breaks the coincidence above. Design
0008's Update named the obstacle without resolving it:

> Personal portions stay out of this default — they resolve asynchronously in `onAppear`, after the
> picker's initial selection is already made.

`defaultUnit(for:)` is called synchronously by `AddFoodSheetConfigurator` and `AddFoodSheetView`
from data the item already carries; `personalPortions` arrive later, from
`FetchFoodItemPersonalPortionsUseCase` in `onAppear()`. A personal-portion-first list whose default
stays canonical would open the picker on an option that is not the one at the top of it.

One further coupling is worth recording because nothing in the code states it: the order *within*
each group is not sorted anywhere. It is Firestore array order, which is authoring order —
`FoodPortionsSection` appends a new row at the end. Whoever fills in the first portion row on the
add-food form, in `ModerationReviewView` or in `ModerationCatalogueEditorView` is therefore
choosing the default logging unit for every user of that catalogue item.

## Decision

The order of the quantity picker and the preselected unit are **one** decision, not two: **the
first option in the list is always the preselected unit.**

The list order is: **personal portions → canonical portions → `g`/`ml` → `100 g`/`100 ml`**, with
the `g`/`ml` labels following the item's own `measure` per
[design 0011](../design/0011-food-measure-grams-or-millilitres.md).

The default follows the list rather than being stated independently. It resolves in two steps,
because the two portion sets become available at different times:

1. Synchronously, from the item alone — `item.portions.first`, falling back to `.grams`. This is
   what `defaultUnit(for:)` already does and it stays as the configurator's entry point. This
   fallback carries its own quantity: `AddFoodSheetConfigurator` and `AddFoodSheetView` pass
   `quantity: 100` alongside it whenever `item.portions.isEmpty`, so a portion-less item opens at
   `100 × 1 g` rather than `1 × 1 g` — the total (`100 g`) is unchanged from before this revision,
   only which of the two numbers on screen carries it. This supersedes the one remaining half of
   design 0008's Non-goal *"changing the default selected unit based on whether portions exist"* —
   the portion-exists half was already reversed by that document's own 2026-09-16 Update; this
   revises the no-portion half the same way, for the same reason (the picker reads more directly
   as "how many grams" when the editable number is the actual gram count).
2. Once `personalPortions` resolve in `onAppear()`, the selection moves to `personalPortions.first`
   — but **only** while the user has not touched the picker, and **without** being treated as a
   unit change. The quantity resets to `1` at the same time, since it may have started at the `100`
   from step 1's grams fallback, and `100 × 1 hrnek` is not the intended reading of a portion.

A `MyCreatedMeal` is unaffected: it keeps opening at its full composed weight in `.grams`, per
design 0008.

## Consequences

What becomes easier: the list and the default can no longer disagree, and neither can be changed
without the other. A future reordering is one edit to one invariant.

What becomes harder — two traps, both of which will silently produce wrong numbers rather than
fail:

- **`unit` must not be reselected through `.onChange`.** `FoodQuantityView`'s picker binds
  `$viewModel.unit` and reacts with `.onChange(of: viewModel.unit) { … onUnitChanged(from:to:) }`.
  That fires for a programmatic write too, and `onUnitChanged` converts the *gram amount* into the
  new unit — so assigning `personalPortions.first` in `onAppear` would turn `1 × 1 balení (250 g)`
  into `6.25 × 1 hrnek (40 g)` instead of `1 × 1 hrnek`. The step-2 reselection must go through a
  wrapped `Binding` that distinguishes the user's edit from the view model's own write, exactly as
  [design 0010](../design/0010-nutrition-label-photo-prefill.md) does for the photo-prefill field
  highlighting (`ARCHITECTURE.md` § 7.5) and for the same reason. `.onChange` cannot tell the two
  apart.
- **The "user has not touched the picker" flag cannot be set from `onUnitChanged`**, for that same
  reason — `onUnitChanged` runs for both kinds of write. It has to come from the same wrapped
  binding.

What a future reader must not undo without understanding why:

- **Authoring order is load-bearing.** `FoodPortionsSection` appending to the end means the first
  portion row a maintainer fills in becomes the fallback default for every user without a personal
  portion. Sorting `unitOptions` by name or by grams would break that, and would also make the
  default unpredictable from the authoring screen. If a sort is ever wanted, it belongs in the
  authoring UI, not in the picker.

### What a second client inherits

Most of this record is `iOS`: which control the units are picked from, what order they sit in, and
which one starts selected are this client's business, and Android is free to decide differently —
a `DropdownMenu` with an action item and a bottom sheet behave nothing like a SwiftUI `Menu` and a
`.sheet`.

One consequence is **not** `iOS`, because it is about the data rather than the control:

> The order of the `portions` array is meaningful. It is authoring order, and a client must append
> a new portion at the end rather than inserting or sorting.

This binds every client, and the reason is not visible from the schema. `portions` on `foodItems` /
`myCreatedMeals` and the `portions` array in `foodItemPortions` are written **whole** — there is no
partial-update path (`ARCHITECTURE.md` § 1.4), so any client adding one personal portion rewrites
the entire array. A client that sorted that array on the way out — alphabetically, by grams, most
recent first — would silently change which unit the *other* client preselects for the same food, on
the same account. Nothing would error; the user would just find a different unit waiting for them
depending on which device they opened.

So an Android client reading this record needs the sentence above and can ignore the rest. It is
tagged `Cross-platform, iOS` for exactly that reason: `docs/README.md` notes that a second client
discovers its obligations by scanning the ADR index, and an obligation filed under `iOS` alone
would not be read as one.
- **`defaultUnit(for:)` cannot see personal portions** and must not be changed to try. It is called
  before any fetch; its job is the synchronous first guess in step 1.
- Step 2 exists only because personal portions are fetched. `.external` and `.createdMeal` items
  skip that fetch entirely (design 0008's `.catalogue`-only guard), so for them step 1 is the final
  answer.

This record does not supersede design 0008's Update paragraph — that paragraph's rationale for
putting portions ahead of the two generic units still stands, and this record narrows the ordering
within the portions themselves and ties the default to it.
