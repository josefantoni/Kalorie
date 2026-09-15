# Kalorie — TODO

## Kinds of data

The app works with three kinds of data. The distinction matters for the items below:

- **Shared food catalogue** (`foodItems`) — a global database visible to everyone. New entries
  are added by the app maintainer, or approved from user submissions.
- **Private user data** (`users/{userId}/foodConsumed`, `mealTypes`) — food entries and meal
  layout, visible only to their owner.
- **User's own meals** — meals a user composes themselves (see below). These stay private and
  do **not** go through approval.

## Planned features

- [ ] **Data export** — export consumed food for a chosen interval to PDF or Excel. Needs its own
  memory strategy for walking long intervals (streaming/paging the query) rather than routing
  through `DashboardViewModel.monthCache`, which caches every loaded month with no eviction and is
  sized for a single visible dashboard, not an arbitrary export range.
- [ ] **Prompt to sign in** — the account screen is only reachable from the toolbar icon; add an
  unobtrusive prompt after the first logged meal so users on a second device sign in early
- [ ] **Packaging photo on a submission** — the other half of the user-submitted-food flow shipped
  in [design 0009](docs/design/0009-catalogue-moderation.md), deferred by that design's Non-goals:
  Firebase Storage is not configured in this project, and whether the project's plan includes free
  Storage quota is unverified. Needs a `storage` block in `firebase.json`, `storage.rules`, and a
  Blaze-plan check before starting. Additive once it lands — `FoodItemSubmissionDTO` gains an
  optional `photo_path`, no other schema change.
- [ ] **Report incorrect data on a catalogue item** — a user who spots a wrong value on a shared
  catalogue entry has no way to say so. Correcting one is the maintainer's own action in the
  moderation panel ([design 0009](docs/design/0009-catalogue-moderation.md), shipped), and the
  submission flow deliberately refuses a barcode already in the catalogue, so a correction can only
  start from the maintainer noticing the error personally — recorded as a Non-goal in that design,
  not an oversight. Needs a report affordance on the item's own screen and a queue for reports in
  the panel, beside the existing submissions queue.
- [ ] **Rank search results by frequency** — order manual search results by how often the user has
  logged each food, so the most used ones come first. Distinct from favourites above: this one is
  derived, not chosen, and the user cannot remove an entry from it.
- [ ] **Frequency-derived quick-add gram amounts** — per-user "frequently added weights", gram
  amounts the user logs often for a given food (e.g. 50g oats almost daily, one slice of bread),
  surfaced as quick-add options, likely derived from `foodConsumed` history rather than manually
  maintained. This is the other half of what used to be one combined line here; the first half —
  a food carrying a named, user-chosen package/portion weight selectable as a unit — shipped as
  [design 0008](docs/design/0008-food-portions.md).
- [ ] **Food measured in grams or millilitres** — decided and specified in
  [design 0011](docs/design/0011-food-measure-grams-or-millilitres.md), ready to implement. Replaces
  the former *Unit choice when creating a food item* line: a client-only g/kg/ml/l input was found
  to change nothing after saving, since `FoodItemDomain.weight` drives no calculation, while
  [design 0010](docs/design/0010-nutrition-label-photo-prefill.md)'s parser already stores per-100 ml
  labels as per-100 g. The fix is an optional `measure_unit` field carried from `foodItems` to
  `foodConsumed`, with numbers never converted.

## Documentation baseline

[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) describes what exists; `docs/adr/` records the
decisions still in effect. Findings are grouped by area and numbered `A<area>-<n>` — only open
ones are listed below; closed findings live in git history, not here.

## Audit findings — 1. Data layer and Firestore model

- [ ] **A1-7 — Favourites and saved meals never see catalogue corrections.** *Downgraded on
  review of the design docs — this is a decided risk, not an open question.* Both
  [design 0003](docs/design/0003-favourite-foods.md) and
  [design 0006](docs/design/0006-own-daily-meals.md) accept the staleness explicitly for v1 and
  specify the same mitigation: re-read by `food_item_id` when the user opens the item — a
  favourite when tapped, a meal when opened in the editor. Nothing to decide; the only thing
  worth adding is a **trigger**, since there is currently no way to learn that a catalogue item
  was corrected in the first place. Left open as a reminder that the mitigation is designed but
  not built. `ModerationCatalogueEditorViewModel.onSaveTapped` ([design 0009](docs/design/0009-catalogue-moderation.md),
  shipped) is now the only place a correction happens, so it is also the only place a trigger could
  fire from — still not built. `loadAsync(id:from:)` already makes the re-read itself cheap
  whenever that lands.

## Audit findings — 2. Food search and catalogue

- [ ] **A2-12 — Ranking cannot be added on top of the current search.** Results are capped at
  `limit(10)` per field — six fields as of [ADR 0024](docs/adr/0024-token-array-field-for-whole-word-search.md)
  — and Firestore returns them in index order, i.e. alphabetically by the matched name. Anything
  cut by that limit is invisible to a re-sort, so *Rank search results by frequency* cannot be
  implemented as a client-side reordering of `SearchFoodItemsUseCase`'s output — it needs either a
  much larger limit (and the read cost that implies) or the frequency data denormalised into the
  query. Constraint, not a bug; recorded so the feature is not designed around a false assumption.

