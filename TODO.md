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


## Audit findings — 1. Data layer and Firestore model

From the review recorded in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) § 1.

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

- [ ] **A1-8 — Two field names for the same nutrient.** `foodConsumed` persists
  `carbohydrate_sugar` and `fat_unsaturated`; `foodItems`, `favouriteFoods` and the
  `myCreatedMeals` ingredients persist `carbohydrate_pure_sugar` and
  `fat_unsaturated_fatty_acids`. `mealTypes` additionally persists camelCase where every other
  collection is snake_case. A second client has to memorise the exceptions rather than follow a
  rule, and the compiler cannot catch a mistake. This is known, not newly found:
  [design 0004](docs/design/0004-shared-macro-calculation-module.md) tabulates the same divergence
  at the *domain* level, declares renaming out of scope, and is why `Macros` adopted the
  `FoodConsumedDomain` spelling. What its non-goal ("changing the Firestore schema or DTOs") left
  untouched is the persisted layer — which is the layer that costs a second client. Renaming is a
  data migration; the cheaper alternative is to write the exceptions down as a contract, which
  ARCHITECTURE § 1.4 now does.

## Audit findings — 2. Food search and catalogue

From the review recorded in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) § 2. A2-3's client-side
fix is in place, pending backfill. A2-9 is fixed. Nothing else here has been fixed.

### Correctness

- [ ] **A2-3 — Search does not fold diacritics, in a Czech-first app.** `cz_name_lowercase` is
  `czName.lowercased()`, so "Rohlík" is stored as "rohlík" and a user typing "rohlik" — which is
  what most people type — used to find nothing. Fixed for new documents: `FoodItemDTO` and
  `SearchFoodItemsUseCase` now also write and query `cz_name_folded` / `eng_name_folded` (see
  ARCHITECTURE § 1.4 and § 2.2). Left open for the one remaining piece:

  **Backfill.** Existing catalogue documents have no `cz_name_folded` / `eng_name_folded` and
  stay findable only through the exact-diacritics path until someone runs a one-off script
  (Admin SDK) that reads every `foodItems` document and writes the two new fields. There is no
  migration tooling in this repo (`scripts/` holds only `build-kmp-framework.sh`) — this needs a
  human with the Firebase project. Gets more expensive to run the longer the catalogue grows in
  the meantime.

  A2-4 is untouched by this fix — the prefix-only gap it tracks is separate and structural, not
  something folding addresses.
  `Kalorie/Kalorie/Core/UseCases/CreateFoodItemUseCase.swift:51`

- [ ] **A2-4 — Search matches prefixes only.** "mléko" does not find "Polotučné mléko", and
  there is no way to reach it except by knowing the first word. This one *is* inherent to
  [ADR 0013](docs/adr/0013-prefix-search-over-lowercased-name-fields.md) — a real fix means an
  n-gram/token array field or an external search service. Worth deciding deliberately rather
  than discovering under a support request.

- [ ] **A2-5 — Every OpenFoodFacts failure looks like "no such product".** Neither external use
  case checks the HTTP status — `let (data, _) = try await URLSession.shared.data(from: url)`
  discards the response — so a 429, a 500 or a maintenance page becomes a `JSONDecoder` error.
  In search that error is caught and turned into `externalFoodItems = []`, which the UI renders
  identically to a genuinely empty result. Three related gaps: no `User-Agent` is sent, and
  OpenFoodFacts' terms require an identifying one and rate-limit anonymous clients harder; the
  default 60-second `URLSession` timeout applies, so a hanging request blocks the search
  spinner for a minute; and there is no retry or backoff.
  `Kalorie/Kalorie/Core/UseCases/SearchFoodExternallyUseCase.swift:32`,
  `Kalorie/Kalorie/Core/UseCases/FetchFoodByBarcodeExternallyUseCase.swift:37`

- [ ] **A2-8 — Rescanning the same barcode after a failed lookup does nothing.**
  `DataScannerRepresentable.Coordinator` keeps `lastDeliveredCode` to suppress VisionKit's
  repeated callbacks while a barcode stays in frame, but never clears it. After "product not
  found", pointing the camera at the same product again produces no callback, no request and no
  feedback — the user has to close and reopen the scanner, with nothing on screen saying so.
  The coordinator needs to reset the code when a lookup ends, which means the view model's
  outcome has to reach it.
  `Kalorie/Kalorie/Features/AddFoodSheet/DataScannerRepresentable.swift:23`

### Behaviour worth confirming rather than fixing

- [ ] **A2-12 — Ranking cannot be added on top of the current search.** Results are capped at
  `limit(10)` per field and Firestore returns them in index order, i.e. alphabetically by the
  matched name. Anything cut by that limit is invisible to a re-sort, so *Rank search results by
  frequency* cannot be implemented as a client-side reordering of `SearchFoodItemsUseCase`'s
  output — it needs either a much larger limit (and the read cost that implies) or the frequency
  data denormalised into the query. Constraint, not a bug; recorded so the feature is not
  designed around a false assumption.