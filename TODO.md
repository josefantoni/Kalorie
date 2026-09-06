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

From the review recorded in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) § 2. A2-2 has a
client-side fix in place, pending rule deployment; nothing else here has been fixed.

### Correctness

- [ ] **A2-2 — The duplicate check before a catalogue write can read a stale cache.**
  `CreateFoodItemUseCase` queries `where "id" == item.id` through `loadAsync`, which uses
  Firestore's default source and is therefore served from the offline cache when the server is
  unreachable. A cache that has never seen the document answers "not found", and the following
  `setAsync` **overwrites** the existing shared catalogue entry with the user's typed values —
  silently, for every user.
  `Kalorie/Kalorie/Core/UseCases/CreateFoodItemUseCase.swift:41`

  The window is bounded — when the server is reachable the query is served from it and answers
  correctly — but the two failure conditions are **correlated, not independent**: being offline
  also makes the prefix search return nothing, which is what sends the user to the *add a new
  food* form in the first place. Offline raises both the chance of entering the form and the
  chance of the guard waving the write through.

  **Decided: both layers. The rule is the invariant, the server read is the affordance; neither
  alone is enough.**

  1. **`firestore.rules` drops `update` from the `foodItems` block**, keeping `create` with its
     existing field validation. Firestore matches `create` only when the document does not exist,
     so founding a new entry still works while overwriting an existing one is refused by the
     server — regardless of cache state, client version, or a second client that never implements
     the check. This is the only layer that closes the race: two clients can both read "not
     found" and both write, and only the server can arbitrate at commit time.
     `CreateFoodItemUseCase.swift:51` is the only writer to `foodItems`, so nothing legitimate
     breaks, and maintainer corrections from the console or the Admin SDK bypass rules entirely.
  2. **The duplicate check becomes a server-only read by document id** — a new
     `loadFromServerAsync(id:from:)` on `FirestoreDataProviderProtocol` (today's
     `loadFromServerAsync` takes a collection only). Mostly this is the affordance — it tells the
     user the food already exists before they fill twelve fields rather than after — but it also
     covers a case layer 1 cannot: a duplicate created **while offline** is queued locally,
     accepted by the cache, and only rejected when it reaches the server, at which point Firestore
     reverts the local mutation and the user, long gone from the form, is never told. Refusing to
     attempt the write at all when the server cannot be reached turns that silent lost write into
     an immediate, legible failure.

  **Done (client side):**

  - `FirestoreDataProviderProtocol` gained `loadFromServerAsync(id:from:)`; `CreateFoodItemUseCase`
    uses it for the duplicate check instead of the offline-capable equality query.
  - `setAsync`'s `permissionDenied` (a rule-refused overwrite) is caught in `CreateFoodItemUseCase`
    and rethrown as `CreateFoodItemError.itemAlreadyExists`, right where the other domain errors
    for this use case already live — not in the shared `mapError`, which stays a transport-level
    concern and would otherwise mislabel every unrelated permission denial as a duplicate item.
    `permissionDenied` alone doesn't prove a duplicate, though — an auth session invalidated
    between the pre-check and the write would deny with the same code — so the catch re-reads the
    document by id before relabelling; only a confirmed hit becomes `itemAlreadyExists`, otherwise
    the original error is rethrown.
  - `AddFoodSheetViewModel.onCreateFoodItem()` checks `error.isFirestoreUnreachable`
    before the `CreateFoodItemError` switch and shows the existing offline alert
    (`L10n.Common.errorOffline` / `errorOfflineMessage`) instead of "unknown error".
    `isFirestoreUnreachable` lives on `Error` in `Error+Matching.swift`, replacing what would
    otherwise be a second private copy of `DashboardViewModel`'s existing offline check.
  - `firestore.rules` now grants `create` only on `foodItems` (`update` dropped); the field
    validation is unchanged.
  - `firestore.indexes.json` disables the automatic index on `foodItems.id`, since nothing queries
    it by equality any more.
  - All 24 `FirestoreDataProviderProtocol` fakes in `KalorieTests/` got the new method.
    `CreateFoodItemUseCaseTests`' fake now wires `stubbedExistingDTO` to
    `loadFromServerAsync(id:)`, distinguishes the pre-write check from the post-denial re-read via
    a second `stubbedConfirmationDTO`, and four new tests cover unreachable, permission-denied
    confirmed as a duplicate, and permission-denied for another reason. Full suite (298 tests) and
    the Kalorie target build both pass.

  **Still open — needs a human with the Firebase project, not a code change:**

  - **Deploy the rule.** Editing `firestore.rules` in the repo does not push it; someone with
    project access runs `firebase deploy --only firestore:rules` (and `--only firestore:indexes`
    for the index override). Per the sequencing note below, do this only after the client build
    above has shipped.
  - **Verify on a real project, by hand**, per the note below — there is no rules emulator/harness
    in this repo to do it automatically: creating a genuinely new barcode still succeeds, and
    re-submitting an existing one is refused with the new "item already exists" message rather
    than "unknown error".
  - **Verify on device:** whether `setAsync` offline hangs rather than throwing. Firebase fires a
    write's completion on server acknowledgement, which would mean today's offline form spins
    indefinitely and the overwrite lands on reconnect. If so, failing fast on the server-only read
    is a strict improvement to the offline path, not a new restriction. Not reproducible from a
    unit test or the simulator's default networking.

  Implementation notes (kept for the deploy/verification step above):

  - **The rule change has no automated test and cannot get one cheaply.** There is no emulator
    config and no rules harness in the repo — `firebase.json` wires up `firestore.rules` and
    `firestore.indexes.json` and nothing else. So the rule ships verified by hand or not at all.
    ADR 0011 records that a rule tightening has already silently broken this collection once, on
    the read side, during the authentication work — that is the failure mode to test for, not a
    hypothetical.
  - **Sequencing.** A rules deploy is instant and an app release is not, so the two cannot land
    atomically. Ship the client first (the `permissionDenied` → `.itemAlreadyExists` mapping is
    what makes the rule's refusal legible), then deploy the rule. Reversing the order leaves
    installed clients showing "unknown error" on a duplicate — tolerable pre-release, but pointless.

  **This does not reopen [ADR 0011](docs/adr/0011-foodItems-writable-by-any-authenticated-client.md).**
  That record decides `foodItems` stays *client-writable* until the moderation flow ships, and
  warns that hardening the rule on its own breaks the *add a new food* form. Refusing `update`
  leaves `create` untouched, so the form keeps working and the catalogue keeps growing; what
  disappears is only the overwrite, which ADR 0011's own Consequences section names as the hole
  A2-2 tracks. A1-11 already narrowed this rule once after ADR 0011 was written, on the same basis.

  *Cross-reference corrected:* this finding previously cited **A2-11** for the rule option. A2-11
  was the barcode-validation finding, closed in `daed3e0`. The rule finding was **A1-11**, closed
  in `2e9a03f`, which split `allow write` into `create, update` and dropped `delete` but kept
  `update` without recording why — so refusing `update` was never rejected, only never reached.
  [ADR 0011](docs/adr/0011-foodItems-writable-by-any-authenticated-client.md) still cites A1-11 as
  open; it is pushed and therefore frozen, so that one reference stays stale.

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