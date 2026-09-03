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

- [ ] **Data export** — export consumed food for a chosen interval to PDF or Excel
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

### Data loss and correctness

- [x] **A1-1 — Concurrent meal-type creation silently overwrites.** Fixed: meal type identity
  moved from a client-computed `max(existing.id) + 1` integer to a client-generated
  `UUID().uuidString`, the same scheme `foodConsumed` and `myCreatedMeals` already used. Two
  devices creating a meal type from the same starting state now get distinct ids, so `setAsync`
  can no longer overwrite one with the other. See
  [ADR 0021](docs/adr/0021-meal-type-ids-are-uuids.md), which supersedes
  [ADR 0010](docs/adr/0010-client-assigned-integer-meal-type-ids.md).
  `Kalorie/Kalorie/Core/UseCases/CreateMealTypeUseCase.swift:62`

- [x] **A1-3 — Anonymous-data merge breaks past 500 entries.** Fixed: `batchSetAsync` now
  splits `items` into chunks of `Constants.Firestore.batchWriteLimit` (500) and commits one
  `WriteBatch` per chunk, sequentially, instead of building a single batch for the whole array.
  This is the actual break — Firestore's `WriteBatch` hard-caps at 500 operations, and
  `MigrateAnonymousDataUseCase` hands it whatever `loadAsync(from:)` returns. The read side was
  deliberately left unpaginated: `getDocuments()` on a whole collection has no equivalent
  Firestore cap, and `migrate(fromAnonymousUserId:credential:)` already buffers all three
  collections into one `PendingMergeSnapshot` written to disk before any write happens (crash
  recovery), so paging the read would only add round-trips, not reduce memory use.
  `DeleteAccountUseCase`'s unbounded read is unaffected by this fix — it never used
  `batchSetAsync` — and stays "slow, not broken" as noted.
  Chunking trades whole-call atomicity for per-chunk atomicity — see
  [ARCHITECTURE.md § 1.5](docs/ARCHITECTURE.md#15-the-provider) for which callers that's safe
  for and which aren't.
  `Kalorie/Kalorie/Core/Networking/FireStone/FirestoreDataProvider.swift:158`,
  `Kalorie/Kalorie/Core/UseCases/MigrateAnonymousDataUseCase.swift:74`

- [x] **A1-4 — `fat_saturated` and `fiber` fall back to `0`, which is a real value.** Fixed:
  `FoodNutritionValues.fatSaturated`/`.fiber` and `FoodItemDomain.fatSaturated`/`.fiber` are now
  `Double?`, matching the DTOs. The `?? 0` fallbacks in `FoodItemDTO`'s three call sites
  (`SearchFoodItemsUseCase`, `FetchFoodItemByBarcodeUseCase`), `FavouriteFoodDTO.asDomain()` and
  `MyCreatedMealIngredientDTO.asDomain()` are removed — a missing value now stays `nil` through
  the domain and round-trips back into a meal snapshot as `nil`, not as a fabricated `0`.
  `MyCreatedMealDomain.asFoodItem()`'s weighted mean now returns `nil` for a composed meal's
  `fatSaturated`/`fiber` if any ingredient's own value is unknown, rather than silently averaging
  a missing ingredient in as `0`.
  A concrete `Double` is still needed at the one place a value crosses into a schema that has no
  "unknown" representation: `ScaledMacros.init(item:ratio:)` (`FoodConsumedModel.swift`) falls
  back to `0` for `fiber` because `FoodConsumedDTO.fiber` is non-optional — that gap belongs to
  **A1-5**, not this finding. `fatSaturated` never reaches `FoodConsumedDomain` at all, per A1-5.
  `Kalorie/Kalorie/Core/Models/FoodNutritionValues.swift`,
  `Kalorie/Kalorie/Core/Models/FoodItemModel.swift`

- [ ] **A1-5 — `foodConsumed` drops `fat_saturated` and `energy_kj` on write.**
  `FoodConsumedDTO` has neither field, so logging a food discards its saturated fat and its
  kilojoule value permanently — the catalogue item still has them, but a logged entry can never
  recover them. This is not a hypothetical cost:
  [design 0003](docs/design/0003-favourite-foods.md) rejected reconstructing a per-100 g snapshot
  from a logged entry *because of these exact three gaps* — "its calories are stored as a rounded
  `Int`, so dividing back introduces drift, and `energyKJ` and `fatSaturated` are not stored at
  all" — and had to add `food_item_id` instead. Widening `FoodConsumedDTO` removes the reason for
  that workaround and unblocks *Data export* exporting a complete nutrition table. Adding the
  fields is cheap; backfilling existing entries is not, which argues for doing it before release.
  `Kalorie/Kalorie/Core/Networking/FireStone/FoodConsumedDTO.swift`

  Narrowed: `calories_per_hundred_grams` has since been added (fixing **A4-3**), so the "dividing
  back introduces drift" half of design 0003's rationale no longer holds — a logged entry now
  carries its own fractional basis directly. `fat_saturated` and `energy_kj` are still missing;
  design 0003's workaround is still load-bearing for those two.

- [ ] **A1-6 — Meal types silently disappear on the DST transition day.**
  `FetchMealTypesUseCase` rebuilds each window with `Calendar.date(bySettingHour:minute:…)` on
  *today's* date and `compactMap`s away anything that returns `nil`. On the spring-forward day
  the local hour 02:00–02:59 does not exist, so a meal window starting in that hour vanishes from
  the Dashboard entirely, with no error and no trace — and any food logged into it becomes
  unreachable for that day. Narrow, but silent and annual.
  `Kalorie/Kalorie/Core/UseCases/FetchMealTypesUseCase.swift:36`

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

- [ ] **A1-11 — The `foodItems` rule is wider than the feature that needs it.**
  [ADR 0011](docs/adr/0011-foodItems-writable-by-any-authenticated-client.md) records why client
  writes exist and that they go away with the moderation flow. Until then the rule can be
  narrowed at no cost to the barcode path: allow `create` and `update` but not `delete`, and
  require `request.resource.data.id == itemId` so a client cannot write a document whose id
  field disagrees with its key. Field-type validation on the numeric fields is a further cheap
  win. Do **not** remove write access before the moderation flow ships — that silently breaks
  barcode scanning.
  `Kalorie/firestore.rules:15`

- [ ] **A1-12 — Every `foodItems` field is automatically indexed.** There is no
  `firestore.indexes.json`, and none is needed — every query the app issues is single-field, so
  no composite index exists to configure. The consequence is the opposite of the one the open
  question assumed: Firestore auto-indexes all 17 fields of every catalogue document, both
  ascending and descending, which is index storage and write amplification on the one collection
  intended to grow large. Only `cz_name_lowercase`, `eng_name_lowercase` and `id` are ever
  queried. Adding a `firestore.indexes.json` with `fieldOverrides` that disable indexing on the
  purely numeric fields is a one-file change, and it puts index configuration under version
  control — which it is not today.


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

- [ ] **A2-10 — One matching favourite suppresses the external search entirely.** The gate is
  `guard displayedResults.isEmpty && searchText.count >= 3`, and `displayedResults` includes
  favourites and saved meals, not just catalogue hits. So a user whose favourite "Rohlík" matches
  "roh" never sees OpenFoodFacts results for "roh", however narrow the local match was. Plausibly
  intended as a cost guard; if so it should be `localFoodItems.isEmpty`, which is what the
  comment-free code reads as meaning.
  `Kalorie/Kalorie/Features/AddFoodSheet/AddFoodSheetViewModel.swift:156`

- [ ] **A2-12 — Ranking cannot be added on top of the current search.** Results are capped at
  `limit(10)` per field and Firestore returns them in index order, i.e. alphabetically by the
  matched name. Anything cut by that limit is invisible to a re-sort, so *Rank search results by
  frequency* cannot be implemented as a client-side reordering of `SearchFoodItemsUseCase`'s
  output — it needs either a much larger limit (and the read cost that implies) or the frequency
  data denormalised into the query. Constraint, not a bug; recorded so the feature is not
  designed around a false assumption.


## Audit findings — 3. Dashboard and meal types

From the review recorded in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) § 3. Nothing here has
been fixed.

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

### Cost and lifecycle

- [ ] **A3-6 — `monthCache` grows for the life of the process.** `populateCache` evicts only the
  month it is repopulating, and nothing else ever removes an entry. Paging back through a year in
  the calendar keeps twelve months of `FoodConsumedDomain` values in memory until the app is
  killed. Bounded by user behaviour rather than by code, and harmless today, but it is a cache
  with no eviction policy at all — worth an explicit cap before the *Data export* feature starts
  walking long intervals.
  `Kalorie/Kalorie/Features/Dashboard/DashboardViewModel.swift:265`

- [ ] **A3-7 — The month is fetched twice on a cold launch.** `DashboardView` has both
  `.task { await viewModel.onAppear() }` and `.onChange(of: scenePhase)` firing `onRefresh()`
  when the phase becomes `.active`, and both run at launch. Two identical month queries, and the
  second invalidates the cache the first just filled. Cheap to fix by ignoring the first
  `.active` transition.
  `Kalorie/Kalorie/Features/Dashboard/DashboardView.swift:167`

- [ ] **A3-8 — The selected day is silently reset, and never advances.** `onAppear` sets
  `selectedDay = Date.now`, so any re-creation of the Dashboard view drops the user back on
  today without a word. The mirror image is also true: because `.task` runs once and `onRefresh`
  deliberately does not touch `selectedDay`, an app left open across midnight keeps showing
  yesterday as though it were today, including the "today" highlight in the day picker.
  `Kalorie/Kalorie/Features/Dashboard/DashboardViewModel.swift:130`