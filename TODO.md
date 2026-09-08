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
- [ ] **User-submitted food** — the user photographs the packaging, fills in macros and calories,
  reviews and submits for approval. The submission goes to a separate pending collection rather
  than straight into the shared catalogue, and reaches `foodItems` only once the maintainer
  approves it. Requires Firebase Storage for the photos.
- [ ] **Maintainer admin panel** — a list of pending food submissions (including the packaging
  photo) to approve or reject; approving publishes the item to the shared `foodItems` catalogue.
  Requires a maintainer role (Firebase custom claims) and matching security rules — once done,
  direct client writes to `foodItems` get disabled.
- [ ] **Rank search results by frequency** — order manual search results by how often the user has
  logged each food, so the most used ones come first. Distinct from favourites above: this one is
  derived, not chosen, and the user cannot remove an entry from it.
- [ ] **Package/portion weight and quick-add gram amounts** — `FoodQuantityView` currently only
  offers 1g/100g. Two related pieces: (1) a food item can carry a known package/portion weight
  (e.g. a muesli bar is 33g, a Pepsi can is 80g) that shows up as a selectable unit; (2) per-user
  "frequently added weights" — gram amounts the user logs often for a given food (e.g. 50g oats
  almost daily, one slice of bread) — surfaced as quick-add options, likely derived from
  `foodConsumed` history rather than manually maintained.

## Documentation baseline

Everything except authentication was built before `docs/` existed. All five areas have now been
read as a whole and written up: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) describes what
exists, `docs/adr/0008`–`0019` record the decisions still in effect, and the audit findings each
area produced are listed below.

Findings are grouped by area and numbered `A<area>-<n>`. Each is a decision still to make unless
marked `[x]`.

A second pass followed on 2026-09-08, after the ~50 commits that closed almost all of the first
audit's findings. Its subject was different: not undocumented code, but **whether the living
description still matches the code**, since `ARCHITECTURE.md` is the one document that is meant
to be updated rather than frozen. It checked documentation coverage, doc-to-code drift, decisions
that shipped without a record, and use-case test coverage — it did **not** re-read the source
file by file, so it is not a replacement for a code-level audit of any area. Findings from that
pass are A1-13 upward, A2-13 upward, A3-9 upward, A5-12 upward, and the new area 6.


## Audit findings — 1. Data layer and Firestore model

From the review recorded in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) § 1. A1-8 is closed;
A1-13 onward are from the 2026-09-08 documentation pass. A1-14 is closed by
[ADR 0025](docs/adr/0025-food-item-kind-discriminates-entry-origin.md); A1-13 and A1-15 are closed
by describing `food_item_kind` in § 1.4 and adding the `arrayContains` row to § 1.5.

### Data model consistency

- [ ] **A1-7 — Favourites and saved meals never see catalogue corrections.** *Downgraded on
  review of the design docs — this is a decided risk, not an open question.* Both
  [design 0003](docs/design/0003-favourite-foods.md) and
  [design 0006](docs/design/0006-own-daily-meals.md) accept the staleness explicitly for v1 and
  specify the same mitigation: re-read by `food_item_id` when the user opens the item — a
  favourite when tapped, a meal when opened in the editor. Nothing to decide; the only thing
  worth adding is a **trigger**, since there is currently no way to learn that a catalogue item
  was corrected in the first place. Left open as a reminder that the mitigation is designed but
  not built. The trigger depends on **Maintainer admin panel** above — approval is the only
  moment a correction is known to have happened, so design the trigger alongside that panel
  rather than as a standalone doc now. `loadAsync(id:from:)` already makes the re-read itself
  cheap whenever that lands.

## Audit findings — 2. Food search and catalogue

From the review recorded in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) § 2. A2-3, A2-4, A2-5,
A2-8 and A2-9 are fixed. Nothing else here has been fixed; A2-13 onward are from the 2026-09-08
documentation pass.

### Behaviour worth confirming rather than fixing

- [ ] **A2-12 — Ranking cannot be added on top of the current search.** Results are capped at
  `limit(10)` per field — six fields as of [ADR 0024](docs/adr/0024-token-array-field-for-whole-word-search.md)
  — and Firestore returns them in index order, i.e. alphabetically by the matched name. Anything
  cut by that limit is invisible to a re-sort, so *Rank search results by frequency* cannot be
  implemented as a client-side reordering of `SearchFoodItemsUseCase`'s output — it needs either a
  much larger limit (and the read cost that implies) or the frequency data denormalised into the
  query. Constraint, not a bug; recorded so the feature is not designed around a false assumption.

### The tokenisation exists twice

A2-13 is closed by [ADR 0026](docs/adr/0026-js-backfill-duplicates-textkit-under-a-shared-fixture.md):
the JS copy is accepted as a deliberate exception, pinned to
`TextKit/fixtures/text-kit-cases.json`, which `scripts/lib/diacritics.test.js` and
`scripts/lib/search-terms.test.js` load directly and `DiacriticFoldingTest` /
`SearchTermsTest` mirror by hand (TextKit has no JVM/JS test target to read the file itself).

A2-14 is closed: the catalogue is Czech-only by design, and a Slovak user gets Czech names — see
[ARCHITECTURE.md](docs/ARCHITECTURE.md) § 2.2. No map or backfill change.

## Audit findings — 3. Dashboard and meal types

Every finding from the first pass is closed. These are from the 2026-09-08 documentation pass over
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) § 3. A3-9 is closed by the new § 3.7, which describes
the delete path and what it does to design 0006's fourth argument.

A3-10 is closed: `FetchFoodsConsumedForMonthUseCaseTests` and `UpdateMealTypeTimesUseCaseTests` now
cover both.

## Audit findings — 4. Food entry flow

Every finding from the first pass is closed. The 2026-09-08 documentation pass over
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) § 4 produced A4-10, now closed: § 4.1 and § 4.5 say
what the detail screen actually changes, and where deleting an entry lives instead.

## Audit findings — 5. Cross-cutting concerns

Every finding from the first pass is closed. The 2026-09-08 documentation pass over
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) § 5 produced A5-12, now closed: § 5.5 counts nine
files and names `Double+Extension` and `View+Loader`.

## Audit findings — 6. Documentation coverage

New area, opened by the 2026-09-08 pass. These are not findings about one part of the app but
about what `docs/` does and does not reach. A6-2 is closed — `docs/README.md` → *Do not update a
shipped design doc* now states which post-ship edits are allowed inline (the Outcome section, or a
marked `iOS`-scoped update) and which need their own ADR (`Backend` / `Cross-platform`), which is
also what closed A1-14.

A6-1 is closed: [ARCHITECTURE.md](docs/ARCHITECTURE.md) § 6 now covers `Core/Auth/` and
`Features/Account/`, including the three things the frozen design docs predate — the re-auth guard
before account deletion (A1-2), the `Log.warning`/`Log.error` calls in the `try?` branches (A5-2),
and Google session clearing.

