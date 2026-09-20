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

- [ ] **Prompt to sign in** — the account screen is only reachable from the toolbar icon; add an
  unobtrusive prompt after the first logged meal so users on a second device sign in early
- [ ] **Packaging photo on a submission** — the other half of the user-submitted-food flow shipped
  in [design 0009](docs/design/0009-catalogue-moderation.md), deferred by that design's Non-goals:
  Firebase Storage is not configured in this project, and whether the project's plan includes free
  Storage quota is unverified. Needs a `storage` block in `firebase.json`, `storage.rules`, and a
  Blaze-plan check before starting. Additive once it lands — `FoodItemSubmissionDTO` gains an
  optional `photo_path`, no other schema change.
- [ ] **Rank search results by frequency** — order manual search results by how often the user has
  logged each food, so the most used ones come first. Distinct from favourites above: this one is
  derived, not chosen, and the user cannot remove an entry from it.
- [ ] **Frequency-derived quick-add gram amounts** — per-user "frequently added weights", gram
  amounts the user logs often for a given food (e.g. 50g oats almost daily, one slice of bread),
  surfaced as quick-add options, likely derived from `foodConsumed` history rather than manually
  maintained. This is the other half of what used to be one combined line here; the first half —
  a food carrying a named, user-chosen package/portion weight selectable as a unit — shipped as
  [design 0008](docs/design/0008-food-portions.md).

## Documentation baseline

[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) describes what exists; `docs/adr/` records the
decisions still in effect. Findings are grouped by area and numbered `A<area>-<n>` — only open
ones are listed below; closed findings live in git history, not here.

### Android-readiness audit — 2026-09-20

The findings below numbered from `A2-15`, `A3-11`, `A4-11` and `A5-13` onwards, plus the
*Android port readiness* section at the end, all come from one audit asking a single question of
each area: **could an Android developer implement this from `docs/` alone, without reading Swift?**

They differ in kind from the findings above them. Almost none is a bug — the iOS client behaves
correctly in every case. They are places where a contract exists only in Swift, where the compiler
enforces it for free and the documentation never had to say it out loud. A second client gets that
enforcement from nothing, so each one is a way the two clients can silently diverge on a shared
account.

## Audit findings — 2. Food search and catalogue

- [ ] **A2-15 — Client-side result matching does not fold diacritics; server-side search does.**
  `AddFoodSheetViewModel.displayedResults` filters favourites, created meals and submissions with
  plain `.lowercased().hasPrefix(query)`, with no `foldingDiacritics()`. So "rohlik" finds a
  catalogue *Rohlík* through `cz_name_folded` but not a favourited *Rohlík*. § 2.2 says only
  "prefix on either name". A client that folds consistently — the obvious reading — produces a
  visibly different list, and with it a different external-fallback gate, since § 2.3 keys that
  gate off `displayedResults`.
- [ ] **A2-16 — The merge order and dedup policy of the six search queries is unstated, and it is
  the user-visible result order.** The code concatenates cz_lowercase → eng_lowercase → cz_folded
  → eng_folded → cz_token → eng_token and dedups first-wins.
  [ADR 0013](docs/adr/0013-prefix-search-over-lowercased-name-fields.md) and
  [ADR 0024](docs/adr/0024-token-array-field-for-whole-word-search.md) say only "concatenated and
  de-duplicated by id". Add the order to § 2.2.
- [ ] **A2-17 — The OpenFoodFacts wire contract is under-specified.** Undocumented: the host
  `world.openfoodfacts.org`, the exact `fields=code,product_name,product_name_cs,product_name_en,nutriments`
  sent on both calls, the search call's `json=1` and `page_size=20`, and the barcode call's
  `status == 1` gate. § 2.4 covers timeout, retry and mapping well, but not these. Divergence here
  changes which products a client finds and silently drops fields. Add to § 2.4.
- [ ] **A2-12 — Ranking cannot be added on top of the current search.** Results are capped at
  `limit(10)` per field — six fields as of [ADR 0024](docs/adr/0024-token-array-field-for-whole-word-search.md)
  — and Firestore returns them in index order, i.e. alphabetically by the matched name. Anything
  cut by that limit is invisible to a re-sort, so *Rank search results by frequency* cannot be
  implemented as a client-side reordering of `SearchFoodItemsUseCase`'s output — it needs either a
  much larger limit (and the read cost that implies) or the frequency data denormalised into the
  query. Constraint, not a bug; recorded so the feature is not designed around a false assumption.

## Audit findings — 3. Dashboard and meal types

- [ ] **A3-11 — Meal-type name validation is unspecified.** `CreateMealTypeUseCase` checks
  `!name.isEmpty` — so a whitespace-only name passes — and enforces uniqueness with `$0.name ==
  name`, an exact, case-sensitive, untrimmed comparison. § 3.4 says only "non-empty name, unique
  name". A client that trims or case-folds rejects names the other accepts, so the same account's
  meal layout becomes editable on one device and not on the other. This is Cross-platform
  behaviour recorded only in a living doc; per `docs/README.md` it wants an ADR, since a second
  client discovers its obligations by scanning the ADR index.
- [ ] **A3-12 — The 30-minute minimum window length is a bare literal.** `MealKit` deliberately
  takes `minimumDurationMinutes` as a parameter rather than owning it, and `30` is hardcoded at
  the `CreateMealTypeUseCase` call site, not in `Constants`. § 3.4 states it in prose with no ADR
  behind it. Nothing currently catches a client that picks a different minimum. Either default it
  in MealKit or state it as a Cross-platform requirement.
- [ ] **A3-13 — § 3.2 describes `mealType(at:)`'s sort key as `startTime`.** That is a `Date`
  which `FetchMealTypesUseCase` anchors to today's `dayStart`, making it equivalent to minutes
  since midnight. The wording should say so, because that tie-break is what decides which window
  an overlapping food lands in.

## Audit findings — 4. Food entry flow

- [ ] **A4-11 — Optional nutrients must survive scaling as `nil`, and this is stated nowhere.**
  `ScaledMacros` feeds `fiber ?? 0` into `Macros.scaled` but then discards that result, keeping
  `food.fatSaturated.map { $0 * ratio }` and `food.fiber.map { $0 * ratio }` instead. § 4.4 says
  only that "macro `Double` fields keep scaling by `newWeight / food.weight`", and
  [ADR 0032](docs/adr/0032-unknown-optional-nutrient-shown-as-dash-not-zero.md) governs *display*,
  not the write path. A client that coalesces to `0` before scaling writes `0` where iOS wrote
  absent, irreversibly destroying the "unknown" state ADR 0032's dash depends on. One sentence in
  § 4.4.
- [ ] **A4-12 — `energyKJ`'s scaling rule is never named.** It is scaled by `ratio` on edit and by
  `grams / 100` on log, and never re-derived. § 4.4 names calories and "macro `Double` fields" but
  not `energyKJ`; § 8.4 says only that the *export* does not re-derive it. A reader following
  [ADR 0007](docs/adr/0007-derive-missing-energy-kj-from-macros.md) could plausibly re-derive it
  from macros on every edit, silently changing stored values for any food whose label kJ disagrees
  with the 37/17/17 factors. Belongs in § 4.4.

## Audit findings — 5. Cross-cutting concerns

- [ ] **A5-13 — `BilingualNamed.displayName` is Cross-platform behaviour filed under an iOS
  label.** § 5 opens with "Scope: `iOS` throughout" and "Read first: nothing", yet the rule —
  `cs`/`sk` → `czName`, otherwise `engName` falling back to `czName` when empty — decides which
  name a user sees, is named in [design 0014](docs/design/0014-data-export.md)'s own "contract an
  Android client must match" table, carries § 2.2's Slovak-diacritic reasoning, and is depended on
  by design 0006 for created meals via its empty-`engName` fallback. It lives in Swift rather than
  in `TextKit`, so a second client will re-implement it and the `isEmpty` fallback is easy to
  miss. Wants an ADR, and is a candidate for moving into `TextKit` alongside `foldDiacritics`.

## Audit findings — 9. Android port readiness

Not an `ARCHITECTURE.md` section. This area is introduced by the 2026-09-20 audit for findings
that belong to no single feature area: the shared-module build, and the places where the
documentation index itself hides an obligation from a second client.

- [ ] **A9-1 — No KMP module declares an Android or JVM target, so the reuse premise is
  currently aspirational.** All four `build.gradle.kts` files declare only `iosArm64()` and
  `iosSimulatorArm64()`; `androidTarget()` and `jvm()` appear nowhere, and `src/` holds only
  `commonMain`, `commonTest` and — in ExportKit — `iosMain`.
  [Design 0014](docs/design/0014-data-export.md) states that "an Android client produces identical
  files without re-implementing any of it"; today such a client cannot consume the module at all.
  PdfKmp is not the obstacle: its published Gradle metadata lists `androidJvm` and `jvm` variants
  alongside the iOS ones. Wants a decision, then an ADR recording the target set.
- [ ] **A9-2 — There is no consumption path for the modules, and the choice is unrecorded.** Each
  module is a separate Gradle build with its own `settings.gradle.kts` and
  `rootProject.name`; there is no root `settings.gradle.kts`, no `maven-publish`, and no `group`
  or `version` anywhere. An Android app can neither `implementation(project(":MacroKit"))` nor
  resolve them from a repository. `scripts/build-kmp-framework.sh` only ever runs
  `assemble<Module>ReleaseXCFramework`. Composite builds versus publishing to Maven is an unmade
  decision, and whichever is chosen must keep the existing XCFramework build working.
- [ ] **A9-3 — [Design 0013](docs/design/0013-catalogue-item-without-barcode.md) is indexed
  `Proposed` while the rule it designs is deployed.** `validItemId()` already accepts the uppercase
  UUID that document specifies. A second client reading the index skips it as not-yet-built and
  then cannot explain why its submissions are rejected; ARCHITECTURE § 1.2 now states the id shape, but the index still says `Proposed`. Reconcile the status.
- [ ] **A9-4 — [ADR 0033](docs/adr/0033-created-meal-portions-editable-from-the-quantity-screen.md)
  is missing from the Decision records table in `docs/README.md`.** The file exists and
  ARCHITECTURE § 4.2 links it; it is the only gap in the 0001–0034 range. By that README's own
  reasoning — a second client discovers obligations by scanning the index — a record absent from
  the index is invisible.
- [ ] **A9-5 — [ADR 0034](docs/adr/0034-kotlin-toolchain-pinned-at-2.4.10-across-kmp-modules.md)
  is scoped `iOS` but constrains a shared artefact.** It pins Kotlin 2.4.10 across the very
  modules an Android client would consume, and its Consequences address "a future module" but
  never a future *consumer*. As labelled, an Android developer treats 2.4.10 as mere iOS
  precedent rather than as a constraint they share.
