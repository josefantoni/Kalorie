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

From the review recorded in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) § 1. Nothing here has
been fixed; each item is a decision still to make.

### Data loss and correctness

- [ ] **A1-1 — Concurrent meal-type creation silently overwrites.** `CreateMealTypeUseCase`
  assigns `max(existing.id) + 1` client-side and writes with `setAsync`, which replaces rather
  than fails. Two devices creating a meal type from the same starting state produce the same id;
  the second write destroys the first, with no error on either device. Reachable whenever the
  user has an iPhone and an iPad, which is the case authentication was built for. Fix is either
  UUID document IDs (see ADR 0010, which is safe to revisit) or a transaction.
  `Kalorie/Kalorie/Core/UseCases/CreateMealTypeUseCase.swift:62`

- [ ] **A1-3 — Anonymous-data merge breaks past 500 entries.** `MigrateAnonymousDataUseCase`
  reads whole collections with the unfiltered `loadAsync(from:)` and hands them to
  `batchSetAsync`, which builds a single Firestore `WriteBatch`. Firestore caps a batch at 500
  operations. A user who logs six items a day reaches that in about three months, after which
  signing in fails and the merge never completes. `batchSetAsync` needs chunking; the reads need
  paging. `DeleteAccountUseCase` has the same unbounded read, though it deletes one at a time so
  it only gets slow, not broken.
  `Kalorie/Kalorie/Core/Networking/FireStone/FirestoreDataProvider.swift:153`,
  `Kalorie/Kalorie/Core/UseCases/MigrateAnonymousDataUseCase.swift:74`

- [ ] **A1-4 — `fat_saturated` and `fiber` fall back to `0`, which is a real value.** Both are
  optional on read and mapped with `?? 0` in `FoodItemDTO`'s three call sites,
  `FavouriteFoodDTO.asDomain()` and `MyCreatedMealIngredientDTO.asDomain()`. A missing value and
  a genuine zero become indistinguishable, and the zero is then persisted into `foodConsumed`
  and into meal snapshots, where it is permanent. This does not merely resemble the bug
  [ADR 0007](docs/adr/0007-derive-missing-energy-kj-from-macros.md) fixed for `energy_kj` — it
  contradicts that record. Its Decision says the new fallback replaces "every existing `?? 0`",
  and two of them survived inside the very expressions that were edited to fix the third. Unlike
  `energy_kj` these cannot be derived from the other macros, so the fix is not another fallback:
  keep them optional through the domain type so a missing value stays missing.

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


## Audit findings — 4. Food entry flow

From the review recorded in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) § 4. Nothing here has
been fixed.

### The amount being logged is not always the amount the user chose

- [ ] **A4-2 — Clearing the quantity field silently keeps the previous amount.** The field binds
  a `String`, and `viewModel.quantity` is only updated when `Double(normalized)` succeeds. Select
  all, delete, tap Confirm — the field reads empty, `grams > 0` passes on the stale value, and the
  old amount is logged with no warning. The same gap swallows non-ASCII digits: `char.isNumber`
  accepts them, so they survive sanitising, `Double` returns `nil`, and the displayed text and the
  logged amount diverge. An empty field should clear `quantity` and let the existing
  `errorInvalidQuantity` alert do its job.
  `Kalorie/Kalorie/Features/FoodQuantity/FoodQuantityView.swift:99`

- [ ] **A4-3 — Editing a weight rescales already-rounded calories.**
  [ADR 0016](docs/adr/0016-logged-entries-rescale-from-their-own-stored-values.md) explains why
  the rescale uses the entry's own values, which is right. The cost is that `calories` is an
  `Int`, so the original rounding error is multiplied by `newWeight / oldWeight`. Invisible at
  ordinary weights; total at small ones — 1 g of a 33 kcal/100 g food rounds to 0 on logging and
  stays 0 however the weight is later edited.

  Note what this is *not*. [Design 0004](docs/design/0004-shared-macro-calculation-module.md)
  found that the two paths rounded differently — logging truncated, editing rounded — and unified
  them on `roundToInt`. That half is fixed and stays fixed. What remains is the difference in
  **basis**: logging rounds a fractional per-100 g value once, editing rescales an already-rounded
  `Int`. 0004 could not touch it, having declared DTO changes a non-goal. A reader of 0004 who
  believes the log-vs-edit discrepancy is closed is therefore half right. Fix without reopening
  [ADR 0016](docs/adr/0016-logged-entries-rescale-from-their-own-stored-values.md): store
  `calories_per_hundred_grams` on `foodConsumed` and rescale from that — the same widening
  **A1-5** argues for on other grounds.

### Duplication and inconsistency

- [ ] **A4-4 — The macro preview and the persisted macros are computed by two copies of the same
  code.** `FoodQuantityViewModel.scaledMacros` / `scaledCalories` and the body of
  `SaveFoodConsumedUseCase` build the same `Macros(calories: 0, …)`, apply the same
  `.scaled(factor: grams / 100)`, and call the same `MacroKit.scaledCalories` — down to the same
  three-line comment explaining why calories are handled separately. Whatever the user is shown
  and whatever is written are therefore only equal by coincidence of maintenance. This is the
  duplication class [design 0004](docs/design/0004-shared-macro-calculation-module.md) set out to
  remove; it survived because what repeats here is the *assembly* of the struct, not the
  arithmetic. A `FoodItemDomain.scaled(toGrams:)` helper would collapse both.
  `Kalorie/Kalorie/Features/FoodQuantity/FoodQuantityViewModel.swift:46`,
  `Kalorie/Kalorie/Core/UseCases/SaveFoodConsumedUseCase.swift:35`

- [ ] **A4-5 — The same external food can be favourited before logging but not after.**
  *Narrowed on review of the design docs.* `canShowFavouriteButton` is
  `isFavourite || catalogueItem != nil`, so the button is not rendered when `food_item_id`
  resolves to nothing. **The hiding itself is intended** —
  [design 0006](docs/design/0006-own-daily-meals.md) states it in as many words ("hidden, not
  disabled ... a control the user can never use on this screen"), superseding the earlier
  "visible and disabled" wording in [design 0003](docs/design/0003-favourite-foods.md). For a
  logged created meal that reasoning is sound: there is genuinely no catalogue item to favourite.

  It does not carry over to an OpenFoodFacts entry. Design 0003 accepts favouriting such an item
  as "accepted and intended" — favourites are explicitly allowed to be a private mini-catalogue —
  and `FoodQuantityViewModel` does exactly that, from its own snapshot, one screen earlier. The
  Dashboard refuses the same action on the same food purely because it rebuilds the item by
  lookup instead of carrying it. Resolved for free by A2-1's discriminator, which lets the screen
  tell "no catalogue item exists" from "this kind of entry has no catalogue item".
  `Kalorie/Kalorie/Features/Dashboard/FoodConsumedDetailViewModel.swift:32`

- [ ] **A4-6 — Every field in the add-a-new-food form is labelled "g".**
  `BaseDoubleTextField` renders an unconditional `Text("g")` after its input, so the form reads
  "Energy (kJ) … g" and "Calories per 100 g … g" alongside the fields where grams are correct.
  The unit belongs in the component's API.
  `Kalorie/Kalorie/Components/BaseDoubleTextField.swift:40`

### Smaller gaps

- [ ] **A4-7 — The detail screen's Save has no re-entrancy guard.**
  `FoodQuantityViewModel.onConfirm` opens with `guard !state.isLoading else { return }`;
  `FoodConsumedDetailViewModel.onSave` has no equivalent. A double tap issues two writes and two
  `onFoodUpdated()` callbacks, so the Dashboard invalidates and refetches the month twice. The
  writes themselves are idempotent, which is why this has stayed invisible.
  `Kalorie/Kalorie/Features/Dashboard/FoodConsumedDetailViewModel.swift:83`

- [ ] **A4-8 — A no-op update reports success.** `UpdateFoodConsumedUseCase` opens with
  `guard food.weight > 0 else { return }` — a silent early return, not a throw. The caller then
  sets `savedWeight`, shows the checkmark and tells the Dashboard to refresh, all for a write that
  never happened. Only reachable for an entry with a non-positive weight, which nothing currently
  creates, so this is a latent trap rather than a live bug: it should throw.
  `Kalorie/Kalorie/Core/UseCases/UpdateFoodConsumedUseCase.swift:32`

- [ ] **A4-9 — The detail screen makes two round trips where one would do.** `onAppear` awaits
  `isFavouriteFood(id:)` and then `fetchFoodItemByBarcode(barcode:)` sequentially, though neither
  depends on the other; `async let` for both halves the screen's opening latency.
  [Design 0006](docs/design/0006-own-daily-meals.md) already notes the sharper half of this — for
  a logged created meal the two reads are "guaranteed to miss", because a UUID can never match a
  digits-only catalogue id — and accepted it as the price of not adding a discriminator. A2-1's
  discriminator removes those reads entirely. (Both are also equality queries that should be
  document reads — **A1-9**.)
  `Kalorie/Kalorie/Features/Dashboard/FoodConsumedDetailViewModel.swift:67`