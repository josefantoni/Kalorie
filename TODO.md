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
  not built.

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

- [ ] **A1-9 — Reading one document costs an equality query.**
  `FirestoreDataProviderProtocol` has no get-by-ID method, so `IsFavouriteFoodUseCase` and
  `CreateFoodItemUseCase` query `where "id" == value` with `limit(1)` against documents whose ID
  *is* that value. A query is billed the same as a read here, but it is weaker: it needs the
  duplicated `id` field to stay in sync with the document key, and it cannot distinguish "not
  found" from "index not ready". Adding `loadAsync(id:from:)` removes both call sites' need for
  the duplicated field.
  `Kalorie/Kalorie/Core/UseCases/IsFavouriteFoodUseCase.swift:32`,
  `Kalorie/Kalorie/Core/UseCases/CreateFoodItemUseCase.swift:41`

- [ ] **A1-10 — `FoodItemDTO` is the only DTO without a mapping, and carries dead code.**
  Every other DTO owns its `asDomain()`; `FoodItemDTO`'s mapping is copy-pasted into
  `SearchFoodItemsUseCase`, `CreateFoodItemUseCase` and `FetchFoodItemByBarcodeUseCase`, which is
  how A1-4's `?? 0` ended up written three times. It also has a `dictionary` computed property
  that round-trips through `JSONEncoder` and is referenced nowhere. Its fields are `var` where
  every other DTO uses `let`. Belongs to area 2 to fix, listed here because it is the reason the
  fallback bug is triplicated.
  `Kalorie/Kalorie/Core/Networking/FireStone/FoodItemDTO.swift:32`

### Security and cost

- [x] **A1-11 — The `foodItems` rule is wider than the feature that needs it.** Fixed: `write` is
  split into `allow create, update` — `delete` now falls through to Firestore's default deny, so
  no client can remove a catalogue entry. The rule also requires
  `request.resource.data.id == itemId`, so a document's `id` field can no longer disagree with
  its key, and adds `is number` checks on every numeric field (`weight`, `date`,
  `calories_per_hundred_grams`, `fat`, `fat_unsaturated_fatty_acids`, `carbohydrate`,
  `carbohydrate_pure_sugar`, `protein`, `salt`), plus the same check gated on presence for the
  three fields that are optional on write (`energy_kj`, `fat_saturated`, `fiber`), since
  `CreateFoodItemUseCase` omits them from the payload when `nil` rather than sending `null` (see
  **A1-4**). Write access itself is unchanged, per [ADR 0011](docs/adr/0011-foodItems-writable-by-any-authenticated-client.md)
  — this narrows the shape of an allowed write, it does not remove write access ahead of the
  moderation flow.
  `Kalorie/firestore.rules:15`

- [x] **A1-12 — Every `foodItems` field is automatically indexed.** Fixed: added
  `Kalorie/firestore.indexes.json` with `fieldOverrides` disabling single-field indexing (both
  directions) on the twelve purely numeric fields — every field except `id`, `cz_name`,
  `eng_name`, `cz_name_lowercase` and `eng_name_lowercase`, since only the two lowercase fields
  and `id` are ever queried. `firebase.json` now points `"indexes"` at the new file, so index
  configuration is under version control.


## Audit findings — 2. Food search and catalogue

From the review recorded in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) § 2. Nothing here has
been fixed.

### Correctness

- [ ] **A2-2 — The duplicate check before a catalogue write can read a stale cache.**
  `CreateFoodItemUseCase` queries `where "id" == item.id` through `loadAsync`, which uses
  Firestore's default source and is therefore served from the offline cache when the server is
  unreachable. A cache that has never seen the document answers "not found", and the following
  `setAsync` **overwrites** the existing shared catalogue entry with the user's typed values —
  silently, for every user. The fix is `loadFromServerAsync` for this one check, or a proper
  document read, or a security rule that forbids updating an existing `foodItems` document
  (see A2-11 and [ADR 0011](docs/adr/0011-foodItems-writable-by-any-authenticated-client.md)).
  `Kalorie/Kalorie/Core/UseCases/CreateFoodItemUseCase.swift:41`

- [ ] **A2-3 — Search does not fold diacritics, in a Czech-first app.** `cz_name_lowercase` is
  `czName.lowercased()`, so "Rohlík" is stored as "rohlík" and a user typing "rohlik" — which is
  what most people type — finds nothing. Not a limitation of the prefix approach, just of the
  folding: writing a diacritics-stripped field alongside the lowercase one and querying that
  fixes it. Needs a backfill of existing catalogue documents, so it gets more expensive the
  longer it waits.
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

- [ ] **A2-9 — Denied camera permission renders nothing at all.** The scanner is gated on
  `DataScannerViewController.isAvailable`, which is false when the user refused camera access.
  The `if` simply fails, so tapping the scan button leaves the search list on screen with no
  camera, no message and no route to Settings. Also worth noting while here:
  `NSCameraUsageDescription` in `Resources/Info.plist` is a hardcoded Czech string, not
  localized.
  `Kalorie/Kalorie/Features/AddFoodSheet/AddFoodSheetView.swift:102`

### Behaviour worth confirming rather than fixing

- [ ] **A2-12 — Ranking cannot be added on top of the current search.** Results are capped at
  `limit(10)` per field and Firestore returns them in index order, i.e. alphabetically by the
  matched name. Anything cut by that limit is invisible to a re-sort, so *Rank search results by
  frequency* cannot be implemented as a client-side reordering of `SearchFoodItemsUseCase`'s
  output — it needs either a much larger limit (and the read cost that implies) or the frequency
  data denormalised into the query. Constraint, not a bug; recorded so the feature is not
  designed around a false assumption.


## Audit findings — 3. Dashboard and meal types

From the review recorded in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) § 3.

### Missing capabilities that turn small mistakes into permanent ones

- [ ] **A3-2 — A logged entry's time and date cannot be changed either.**
  `UpdateFoodConsumedUseCase` takes `(food, newWeight)` and rewrites the document with
  `date: food.date.timeIntervalSince1970` unchanged. Combined with
  [ADR 0014](docs/adr/0014-meal-assignment-by-time-of-day-only.md) — meal assignment is by time
  of day — this means an entry that lands in the wrong meal section, or in the *unassigned*
  section, can never be moved. The only workaround is to reshape the meal windows around it.
  `Kalorie/Kalorie/Core/UseCases/UpdateFoodConsumedUseCase.swift:41`

### Correctness

- [ ] **A3-4 — Overlapping meal windows list and count the same food twice.** `groupedFoods`
  filters *all* of `foodsConsumed` for each meal window; `assignedIds` is only consulted
  afterwards, to build the unassigned bucket. Two overlapping windows therefore both display the
  food, and both section popovers include it in their totals. The day total stays correct,
  because it is computed from `foodsConsumed` directly — which makes the discrepancy harder to
  notice, not easier. `CreateMealTypeUseCase` does reject overlaps, but only against the
  client's own `existingMealTypes` array, so the multi-device race in **A1-1** produces exactly
  this state. One-line fix: filter the not-yet-assigned foods instead of all of them.
  `Kalorie/Kalorie/Features/Dashboard/DashboardViewModel.swift:110`

- [ ] **A3-5 — Cache keys come from an unconfigured `DateFormatter`.**
  `Date.formatDateStyle` creates a `DateFormatter` with a `dateFormat` but no `locale` and no
  `calendar`, so both are inherited from `Locale.current`. Lookup is self-consistent and works,
  but `computeActiveDays` parses the day number back out of the key with `Int(parts[2])` — and
  under a locale that renders Eastern Arabic digits that returns `nil` for every key, so the
  month calendar and the day picker show no activity dots at all. Under a non-Gregorian calendar
  locale the keys are self-consistent but no longer mean what they say. The fix is the standard
  one: `locale = Locale(identifier: "en_US_POSIX")` and an explicit Gregorian calendar for any
  formatter used as a key. Secondary: the formatter is rebuilt on every call, which is once per
  logged food on every month load.
  `Kalorie/Kalorie/Core/Extensions/Date+Extension.swift:28`