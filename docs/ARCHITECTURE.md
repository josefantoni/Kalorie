# Architecture overview

A description of what exists today. This document is **living** — update it when the structure
changes. It is not a plan and not a history; for those see `docs/design/` and `docs/adr/`.

Written retroactively, area by area, following `docs/README.md` → *Documenting code that
already exists*. All five areas are covered; the audit findings each one produced are in
`TODO.md`.

**This document is the entry point into `docs/`.** Each section opens with the ADRs and design
docs covering its area, so reading one section gives the full reading list for that area without
opening anything else. Design docs are frozen, which means they are not edited — not that they
are out of date: a decision recorded in one is still in force.

## Contents

1. [Data layer and Firestore model](#1-data-layer-and-firestore-model)
2. [Food search and catalogue](#2-food-search-and-catalogue)
3. [Dashboard and meal types](#3-dashboard-and-meal-types)
4. [Food entry flow](#4-food-entry-flow)
5. [Cross-cutting concerns](#5-cross-cutting-concerns)
6. [Authentication](#6-authentication)
7. [Catalogue moderation](#7-catalogue-moderation)

---

## 1. Data layer and Firestore model

**Scope:** `Backend` for the collection layout, field names and security rules;
`Cross-platform` for the encoding conventions; `iOS` for the provider shape.

**Read first:** [design 0001](design/0001-user-authentication.md) (the `users` collection and the
first version of the security rules), [design 0003](design/0003-favourite-foods.md)
(`favouriteFoods`, and why `food_item_id` is required), [design 0006](design/0006-own-daily-meals.md)
(`myCreatedMeals`, and what `food_item_id` means once a meal can be logged),
[design 0008](design/0008-food-portions.md) (`portions` on `foodItems`/`myCreatedMeals`, and the
barcode-keyed `foodItemPortions` collection), [design 0009](design/0009-catalogue-moderation.md)
(`foodItemSubmissions`, and the maintainer claim narrowing `foodItems` writes — see § 7),
[design 0012](design/0012-report-incorrect-catalogue-data.md) (`foodItemReports`, keyed by
`{barcode}_{userId}` rather than a UUID, and why that key shape is the opposite choice from
`foodItemSubmissions` — see § 7),
[design 0011](design/0011-food-measure-grams-or-millilitres.md) (`measure_unit` on `foodItems`,
`favouriteFoods` and `foodConsumed`, and why the numbers are never converted between grams and
millilitres).

### 1.1 Layering

```
View → ViewModel → UseCase → FirestoreDataProviderProtocol → Firestore
                          ↘ AuthProviderProtocol (userId)
```

There is no repository or store between the use case and the provider. A use case that touches
Firestore owns the whole operation: it resolves the current `userId`, builds the DTO, calls one
provider method, and maps the result to a domain type. This is why almost every persistence use
case carries the same two dependencies (`dataProvider`, `authProvider`) and the same opening
line, `guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }`.

Three kinds of type take part:

| Type | Lives in | Role |
|---|---|---|
| **DTO** (`…DTO`) | `Core/Networking/FireStone/` | The Firestore wire shape. Owns `CodingKeys`, therefore owns the field names in the database. |
| **Domain** (`…Domain`) | `Core/Models/` | What the app works with. Non-optional, no Firestore or `Codable` coupling. |
| **Use case** | `Core/UseCases/` | The mapping between the two, plus validation. |

DTO → domain conversion lives on the DTO as `asDomain()`; domain → DTO lives in a DTO `init`.
Every DTO follows this, `FoodItemDTO` included — `SearchFoodItemsUseCase`, `CreateFoodItemUseCase`
and `FetchFoodItemByBarcodeUseCase` all go through it rather than each carrying its own copy of
the mapping.

### 1.2 Collection layout

Collection paths are centralised in `Constants.Firestore` and are the only place a path string
is written.

```
foodItems/{barcode}                        shared, global catalogue
users/{userId}                             profile document (displayName, email)
users/{userId}/mealTypes/{uuid}            the user's meal windows
users/{userId}/foodConsumed/{uuid}         logged entries
users/{userId}/favouriteFoods/{barcode}    explicitly favourited catalogue items
users/{userId}/myCreatedMeals/{uuid}       user-composed meals
users/{userId}/foodItemPortions/{barcode}  personal gram-shortcut portions
foodItemSubmissions/{uuid}                 shared, a user's pending/rejected catalogue submission
foodItemReports/{barcode}_{userId}         shared, a report that a catalogue entry looks wrong
```

Everything under `users/{userId}` is private to that user. `foodItems` is shared by every user
and every future client.

Document IDs are meaningful, not random:

- `foodItems`, `favouriteFoods` and `foodItemPortions` are keyed by the **barcode**, which is also
  the item's `id` field on the first two. `CreateFoodItemUseCase` enforces that the id is all
  digits.
- `foodConsumed`, `myCreatedMeals`, `mealTypes` and `foodItemSubmissions` are keyed by a
  client-generated `UUID().uuidString` — see [ADR 0021](adr/0021-meal-type-ids-are-uuids.md) for
  `mealTypes`, which used a client-assigned integer until this record.
- `foodItemReports` is keyed by `{barcode}_{userId}`, the one collection keyed by neither a UUID
  nor the barcode alone — see [design 0012](design/0012-report-incorrect-catalogue-data.md) for why
  a composed, deterministic id is the opposite choice from `foodItemSubmissions`' UUID.

The `users/{userId}` profile document is written **only** by `SignInWithAppleUseCase` and
`SignInWithGoogleUseCase`. An anonymous user therefore has subcollections but no parent
document — which is legal in Firestore, and means the parent document's existence must never be
used as a signal that a user exists.

### 1.3 Encoding conventions

These are the contract a second client has to match exactly.

- **Field names are `snake_case`** in Firestore and `camelCase` in Swift, bridged by explicit
  `CodingKeys`. Fields whose two spellings coincide (`id`, `name`, `weight`, `date`, `fat`,
  `protein`, `salt`, `fiber`, `grams`, `calories`, `carbohydrate`, `ingredients`) are listed on
  the first `case` line unrenamed.
- **Dates are `TimeInterval`** — a `Double` of **seconds** since 1970, not a Firestore
  `Timestamp` and not milliseconds. See
  [ADR 0008](adr/0008-dates-as-epoch-seconds-not-firestore-timestamp.md).
- **`id` is stored both as the document ID and as a field** inside the document, purely because
  `firestore.rules` checks `request.resource.data.id == itemId` on a `foodItems` write — nothing
  queries it by equality any more; single documents are read by key through `loadAsync(id:from:)`
  / `loadFromServerAsync(id:from:)`.
- **Nutrition is denormalised** into every collection that references a food, rather than
  joined from `foodItems` at read time. See
  [ADR 0009](adr/0009-denormalised-nutrition-snapshots.md).
- **Three fields are optional on read** — `energy_kj`, `fat_saturated`, `fiber` — because
  catalogue documents predating them exist. `energy_kj` is derived from macros when absent
  ([ADR 0007](adr/0007-derive-missing-energy-kj-from-macros.md)); the other two stay optional
  through `FoodItemDomain`/`FoodNutritionValues` rather than defaulting to `0`, since neither
  can be derived from the other macros.
- **A food carries a `measure`** (`FoodMeasure`: `.grams` or `.millilitres`), wire field
  `measure_unit`, absent on read meaning grams. This is a **label on the numbers, not a
  conversion of them** — a millilitre item's `weight`, portions and `calories_per_hundred_grams`
  are the same numbers they would be for a per-100 ml label, just read as millilitres; no density
  is ever applied. Only amounts of the food itself carry this label — nutrient amounts (fat,
  protein, sugar, salt, fibre) are always grams of nutrient. See
  [design 0011](design/0011-food-measure-grams-or-millilitres.md).

### 1.4 Per-collection shape

**`foodItems`** (`FoodItemDTO`) — the catalogue. Values are **per 100 g**
(`calories_per_hundred_grams`) except `weight`, which is the package weight the entry was read
from. Also carries an optional `portions` — named gram shortcuts (`FoodPortionDomain`: `name`,
`grams`) set once at creation and never updated afterward, since `foodItems` has no `allow update`
rule to write through (see [design 0008](design/0008-food-portions.md) for why that write-once
shape is deliberate). Absent on every document written before this shipped, decoded as `nil → []`.
Carries `cz_name_lowercase` / `eng_name_lowercase`, written by `CreateFoodItemUseCase`
purely so the prefix-range search in `SearchFoodItemsUseCase` has something case-insensitive to
range over — and `cz_name_folded` / `eng_name_folded` alongside them, additionally
diacritics-stripped so the same search also matches a query typed without diacritics (finding
**A2-3**, fixed — a one-off Admin SDK script, `scripts/backfill-name-folding.js`, has since
written both fields onto every pre-existing catalogue document). Both fields stay optional on
read, since nothing guarantees a document created outside `CreateFoodItemUseCase` carries them.
Alongside them, `cz_name_search_terms` / `eng_name_search_terms` hold every prefix of every word
in the name, folded the same way — see [ADR 0024](adr/0024-token-array-field-for-whole-word-search.md)
and its own backfill script, `scripts/backfill-search-terms.js`. Also optional on read, for the
same reason. Both backfill scripts re-implement TextKit's folding and tokenisation in JS, since
Node cannot call the compiled XCFramework; [ADR 0026](adr/0026-js-backfill-duplicates-textkit-under-a-shared-fixture.md)
accepts that duplication and pins both sides to a shared fixture,
`TextKit/fixtures/text-kit-cases.json`. Also carries optional `measure_unit` (§ 1.3), absent
meaning grams; there is no backfill script for it, since absent already means the correct value.

**`foodConsumed`** (`FoodConsumedDTO`) — one logged entry. Values are **absolute for the logged
weight**, already scaled, not per 100 g; `calories` is an `Int`. `food_item_id` points back at
the catalogue entry and is required (no optional, no backfill — see `TODO.md`), and
`food_item_kind` says what it points at: `catalogue` (a `foodItems` document), `external` (an
OpenFoodFacts barcode that resolves against nothing — ADR 0012) or `created_meal` (a
`myCreatedMeals` UUID). It is required for the same reason `food_item_id` is, and an absent or
unrecognised value fails the whole day's fetch — see
[ADR 0025](adr/0025-food-item-kind-discriminates-entry-origin.md). This DTO's
field names diverge from the rest of the model (`carbohydrate_sugar` vs.
`carbohydrate_pure_sugar`, `fat_unsaturated` vs. `fat_unsaturated_fatty_acids`) — finding
**A1-8**. It also carries `energy_kj`, `fat_saturated` and `fiber`, all optional so that entries
logged before those fields were persisted still decode — they were never backfilled; `energy_kj`
falls back to `MacroKit.energyKJFromMacros` when absent, `fat_saturated` and `fiber` stay `nil`.
Also carries `measure_unit`, absent meaning grams — `FoodConsumedDomain.copy(...)` must pass it
through explicitly (it is a `var` on a memberwise init, not caught by the compiler), since missing
it would relabel an edited millilitre entry as grams.

**`mealTypes`** (`MealTypeDTO`) — `startMinutes` / `endMinutes` are minutes since midnight
(0–1439), stored **unrenamed in camelCase**, unlike every other DTO. A window may wrap past
midnight (`endMinutes < startMinutes`); `MealKit` owns that arithmetic.

**`favouriteFoods`** (`FavouriteFoodDTO`) — a full copy of the `FoodItemDomain` plus
`favourited_at`, `portions` included — leaving it out would silently drop a favourited item's
canonical portions, inconsistent with every other copied field. The snapshot is refreshed from the catalogue when the
favourite is tapped (`RefreshFavouriteFoodUseCase`, `.catalogue` items only, rewritten only if it
differs, `favourited_at` kept, any failure falls back to the stored copy). The document id is the food's own id, and
`food_item_kind` says which of the three things that id is, exactly as on `foodConsumed`
(ADR 0025) — required here too. Also carries `measure_unit`, for the same reason `portions` is
carried: leaving it out would silently relabel a favourited millilitre item as grams. Fetched
ordered by `favourited_at` descending, limit 50.

**`myCreatedMeals`** (`MyCreatedMealDTO`) — `ingredients` is an **array of nested maps**
(`MyCreatedMealIngredientDTO`), each a nutrition snapshot plus `grams`. Also carries an optional
`portions`, the same shape as `foodItems`' — but editable here, through the ordinary
`CreateMyCreatedMealUseCase` / `UpdateMyCreatedMealUseCase` path, since a meal has no write-once
constraint to begin with. Fetched ordered by `updated_at` descending, limit 50. Opening an existing
meal in the editor re-reads its ingredients by `food_item_id` (`FetchFoodItemsByIdsUseCase`) and
applies any catalogue change to the draft as an unsaved edit — nothing is written until the user
saves. An ingredient whose id is not in the catalogue keeps its snapshot; one that later appears
there adopts the catalogue values, because the catalogue outranks OpenFoodFacts.
`MyCreatedMealDomain.asFoodItem()` collapses the meal into a synthetic `FoodItemDomain` whose
per-100 g values are the gram-weighted mean of the ingredients
(`MacroKit.weightedMeanPerHundredGrams`), carrying `portions` straight through unchanged, which is
what lets a saved meal appear in search and be logged like any catalogue food.

**`foodItemPortions`** (`FoodItemPersonalPortionsDTO`) — one document per catalogue item a user
has added a personal portion to, keyed by that item's barcode; `id` plus a `portions` array, the
same `FoodPortionDomain` shape again. Private, `.catalogue`-only (OpenFoodFacts items have no
reliable package size to key a shortcut off — [design 0008](design/0008-food-portions.md)),
fetched and saved whole by `FetchFoodItemPersonalPortionsUseCase` /
`SaveFoodItemPersonalPortionsUseCase` — the caller does the add/remove and writes the full list
back with `setAsync`, there is no partial-update path.

**`foodItemSubmissions`** (`FoodItemSubmissionDTO`) — a user-submitted catalogue entry awaiting
maintainer review, keyed by a client-generated UUID (not the barcode, so two users submitting the
same barcode don't overwrite each other). Carries `barcode` (the food's own identity, duplicated at
top level for rules and queries), `submitted_by`, `status` (`pending` / `rejected`), `submitted_at`,
an optional `reject_reason`, and `item` — a nested `FoodItemDTO` in the exact shape `foodItems`
expects. There is no `approved` status: approving is `ApproveSubmissionUseCase` writing `item` to
`foodItems` under `barcode` and deleting the submission, so the collection only ever holds queue
entries still awaiting a maintainer. See [design 0009](design/0009-catalogue-moderation.md) and
§ 7 for the full flow.

**`foodItemReports`** (`FoodItemReportDTO`) — a user's flag that a `.catalogue` item's data looks
wrong, keyed by `{barcode}_{userId}` rather than a UUID: a second report from the same user on the
same item replaces the first, which is what bounds one user to one open report per item without
any rule that can count documents. Four fields — `barcode`, `reported_by`, `reason` (free text,
capped at `Constants.Firestore.reportReasonMaxLength`) and `reported_at` — and no nested item
snapshot, since the maintainer needs the item's *current* values, not what they were when the
report was filed. There is no `status` field: a report exists while it is open and is deleted once
the maintainer corrects the item or dismisses it, the same reasoning that kept
`foodItemSubmissions` from retaining `approved` entries. See
[design 0012](design/0012-report-incorrect-catalogue-data.md) and § 7 for the full flow.

### 1.5 The provider

`FirestoreDataProviderProtocol` is a flat list of one method per **query shape**, not a query
builder:

| Method | Firestore call |
|---|---|
| `loadAsync(from:)` | `collection.getDocuments()` — whole collection |
| `loadFromServerAsync(from:)` | same, `source: .server` — bypasses the offline cache |
| `loadAsync(from:where:isGreaterThanOrEqualTo:isLessThan:)` | numeric range, used for date windows |
| `loadAsync(from:where:hasPrefix:limit:)` | range over `[prefix, prefix + )` — prefix search |
| `loadAsync(from:where:arrayContains:limit:)` | `whereField(_:arrayContains:)` — token search over `*_name_search_terms`, see [ADR 0024](adr/0024-token-array-field-for-whole-word-search.md) |
| `loadAsync(from:where:isEqualTo:)` | equality, `limit(1)`, returns `T?` |
| `loadAsync(id:from:)` | `document(id).getDocument()` — one document by key, returns `T?` |
| `loadFromServerAsync(id:from:)` | same, `source: .server` — one document, bypasses the offline cache |
| `loadAsync(from:orderBy:descending:limit:)` | ordered page |
| `saveAsync(_:to:)` | `addDocument` — Firestore-generated ID |
| `setAsync(_:id:in:)` | `document(id).setData` — full overwrite |
| `batchSetAsync(_:in:)` | one `WriteBatch` per `Constants.Firestore.batchWriteLimit` (500) items |
| `deleteAsync(id:from:)` | `document(id).delete()` |

Consequences worth knowing before adding a method:

- **All reads are one-shot.** Nothing in the app uses `addSnapshotListener`, so no screen
  updates itself when the data changes underneath — the Dashboard's foreground refresh exists
  because of this.
- **Reads default to Firestore's own source resolution**, which serves the offline cache when
  the server is unreachable. `loadFromServerAsync` exists solely so
  `ConfirmMealTypesEmptyUseCase` can tell "this user genuinely has no meal types" from "the
  cache has not been populated yet" before overwriting them with defaults.
- **A single document is read by `loadAsync(id:from:)`**, which resolves its source the same way
  every other read does. `loadFromServerAsync(id:from:)` is the server-only variant, used where a
  stale cache hit would be wrong rather than just late — `CreateFoodItemUseCase`'s duplicate check
  (see § 2.6 below).
- **`setAsync` replaces the document**, it never merges. Every writer must therefore send every
  field it wants to keep.
- **`batchSetAsync` is atomic only within a single 500-item chunk, not across the whole call.**
  Firestore's `WriteBatch` hard-caps at 500 operations, so a call with more items than that
  splits into several sequential `WriteBatch` commits; if one chunk fails, earlier chunks have
  already landed and later ones never run. `MigrateAnonymousDataUseCase` is safe under this
  because every id is a client-generated UUID, so re-running the migrate/resume path after a
  partial failure just re-writes the same documents. `SetupDefaultMealsUseCase` and
  `UpdateMealTypeTimesUseCase` call `batchSetAsync` with the user's `mealTypes`, which today
  never approaches 500 — if that ever changes, the same partial-write risk applies to them
  without the retry path migration has.
- **Every method logs its request and full response body under `#if DEBUG`**, via the
  file-private `log(_:)` at the bottom of `FirestoreDataProvider.swift`.

### 1.6 Security rules

`Kalorie/firestore.rules`, deployed via `Kalorie/firebase.json`. In full:

- `users/{userId}` and everything beneath it: read and write require
  `request.auth.uid == userId`. Anonymous users are authenticated users, so this covers them.
- `isMaintainer()` — `request.auth != null && request.auth.token.maintainer == true`, a custom
  claim set out-of-band via `scripts/set-maintainer-claim.js` (see `docs/SETUP.md`). Gates every
  catalogue write; the client only reads it to decide whether to render the moderation panel
  (§ 7), never to allow anything by itself.
- `validFoodItem(data)` — the shared per-field numeric validation plus a barcode-format check on
  `data.id` ([ADR 0028](adr/0028-foodItemSubmissions-update-rule-validates-the-maintainer-branch.md)),
  called with either `request.resource.data` (a `foodItems` write) or `request.resource.data.item`
  (a `foodItemSubmissions` write), so `create` and `update` on both collections can't drift apart.
  Also checks that `measure_unit`, when present, is `'grams'` or `'millilitres'`
  ([design 0011](design/0011-food-measure-grams-or-millilitres.md)) — covering both collections
  the same way, for the same reason.
- `foodItems`: read requires only `request.auth != null`. `create`/`update` require
  `isMaintainer()`, the document id to match the barcode length whitelist,
  `request.resource.data.id == itemId`, and `validFoodItem`. `delete` is not granted and falls
  through to Firestore's default deny. Client writes were removed by
  [ADR 0027](adr/0027-catalogue-writes-require-a-maintainer-claim.md), which superseded
  [ADR 0011](adr/0011-foodItems-writable-by-any-authenticated-client.md)'s Decision — ADR 0011's
  Context is still the record of *why* client writes existed in the first place.
- `foodItemSubmissions/{submissionId}`: the first collection with two differently-privileged
  readers of the same document — `allow read` is `isMaintainer() || resource.data.submitted_by ==
  request.auth.uid`. `create` requires the author's own uid, `status == 'pending'` and
  `validFoodItem(item)`. `update`'s author branch is the only rule in the project that guards a
  specific field value (`status == 'pending'`) rather than ownership alone, since without it an
  author could approve their own submission by editing its status; the maintainer branch is the
  reject path — `isMaintainer()` plus an unchanged `barcode`/`submitted_by`, `status ==
  'rejected'`, a non-empty `reject_reason` and `validFoodItem(item)` — narrowed by
  [ADR 0028](adr/0028-foodItemSubmissions-update-rule-validates-the-maintainer-branch.md) to match
  exactly what `RejectSubmissionUseCase` writes; approving deletes the submission rather than
  updating it, so this branch never needs to allow that. `delete` is `isMaintainer() ||` the
  author, the latter existing for account deletion, not as a product feature. See
  [design 0009](design/0009-catalogue-moderation.md) for the original rule text and the Rules
  Playground cases it calls out, and ADR 0028 for what changed since.
- `foodItemReports/{reportId}`: `allow read` is `isMaintainer() || resource.data.reported_by ==
  request.auth.uid`, the same two-reader shape `foodItemSubmissions` has. `create` requires the
  author's own uid and, critically, that `reportId` itself equals
  `request.resource.data.barcode + '_' + request.auth.uid` — the one rule in the project that
  validates a document id against a concatenation of two of its own fields. There is **no `update`
  clause**: a second report from the same user re-submits the same document id, which Firestore
  routes through `create` while the first is absent and refuses outright while it is still open,
  since no rule grants it. The client reads its own report first (`loadAsync(id:from:)`) and offers
  no second report when one exists, rather than writing blind against a rule that would refuse it.
  `delete` is `isMaintainer() ||` the author, for account deletion, exactly as on
  `foodItemSubmissions`. See [design 0012](design/0012-report-incorrect-catalogue-data.md).
- Everything else is denied by Firestore's default.

`Kalorie/firestore.indexes.json`, deployed via the same `firebase.json`, disables single-field
indexing on `foodItems`' numeric fields via `fieldOverrides` — only `cz_name_lowercase`,
`eng_name_lowercase`, `cz_name_folded`, `eng_name_folded` and `id` are ever queried, so every
other field would otherwise be indexed in both directions for no reason. It also carries two
composite indexes for `foodItemSubmissions` and one for `foodItemReports` — every one of them
exists solely for `DeleteAccountUseCase`'s or a moderation queue's `where` + `orderBy` on two
different fields, which a single-field index cannot serve.

---

## 2. Food search and catalogue

**Scope:** `Backend` for the two lowercase index fields; `Cross-platform` for the search and
fallback behaviour; `iOS` for the scanner.

**Read first:** [design 0003](design/0003-favourite-foods.md) (favourites hoisted into the result
list, and why an OpenFoodFacts item outside the catalogue is accepted),
[design 0006](design/0006-own-daily-meals.md) (a created meal appearing in the same search),
[design 0009](design/0009-catalogue-moderation.md) (the author's own pending/rejected submissions
as a fourth such list — see § 7).

### 2.1 The four ways a food is found

Everything in this area answers one question — *which `FoodItemDomain` is the user about to
log?* — through four paths, all converging on `AddFoodSheetViewModel.onSelectFoodItem`:

| Path | Use case | Source |
|---|---|---|
| Typed search, local | `SearchFoodItemsUseCase` | `foodItems` prefix query |
| Typed search, fallback | `SearchFoodExternallyUseCase` | OpenFoodFacts `/cgi/search.pl` |
| Barcode, local | `FetchFoodItemByBarcodeUseCase` | `foodItems` equality query |
| Barcode, fallback | `FetchFoodByBarcodeExternallyUseCase` | OpenFoodFacts `/api/v2/product/{code}` |

Both fallbacks are strictly *second*: the external call only happens after the local one comes
up empty. Neither writes what it found back into the catalogue — see
[ADR 0012](adr/0012-external-food-is-surfaced-never-imported.md).

A fifth path exists that is not a search at all: the *add a new food* form, reached from the
carrot button in the sheet's toolbar. It is the only writer to `foodItems`
(`CreateFoodItemUseCase`), and its `scannedCode` field is typed by hand — the scanner does not
populate it.

### 2.2 Local search

`SearchFoodItemsUseCase` is a case-folded prefix range over `cz_name_lowercase` and
`eng_name_lowercase`, plus a diacritics-and-case-folded prefix range over `cz_name_folded` and
`eng_name_folded` for the same query also stripped of diacritics — four concurrent `async let`
queries, ten results each, de-duplicated by id. The folded pair is what lets "rohlik" find
"Rohlík"; the plain lowercase pair stays alongside it so a catalogue document written before the
fix, and therefore missing the folded fields, is still found. The diacritic fold itself
(`foldDiacritics`, a Czech accent-to-base character map) lives in KMP `TextKit`, bridged into
Swift as `String.foldingDiacritics()`, so a second client shares the exact folding rather than
re-deriving it. The mechanics and the limits are recorded in
[ADR 0013](adr/0013-prefix-search-over-lowercased-name-fields.md). The fold map covers only the
15 Czech diacritic pairs; Slovak-specific characters (`ä ô ĺ ľ ŕ`, absent from Czech) pass through
unfolded. This is by design, not a gap: `foodItems` is a Czech-first catalogue and § 5.3 already
has `BilingualNamed.displayName` show a Slovak user the Czech name — a Slovak diacritic in a
catalogue name is not an expected case to search around.

Two further concurrent queries match by **any word**, not only the first: `array-contains` over
`cz_name_search_terms` / `eng_name_search_terms`, each holding every prefix of every word in the
name (folded the same way). This is what lets "mlék" find "Polotučné mléko" — finding **A2-4**,
fixed. The tokenisation (`searchTerms`) lives in KMP `TextKit` alongside `foldDiacritics`, for the
same cross-client reason. See
[ADR 0024](adr/0024-token-array-field-for-whole-word-search.md) for the field shape, the backfill
script, and what this still doesn't do — it is a prefix match per word, not a substring match, and
it does not change the per-query `limit(10)` or add any ranking (**A2-12** is untouched).

Ranking happens **above** the use case, in `AddFoodSheetViewModel.displayedResults`, which is a
pure computed property over four already-loaded lists:

1. matching **favourites** (`favouriteFoods`, prefix on either name),
2. matching **my created meals** (`asFoodItem()`, prefix on the meal name) not already listed as a
   favourite,
3. matching **own catalogue submissions** ([design 0009](design/0009-catalogue-moderation.md)) not
   already listed as a favourite or meal,
4. the local search results, minus anything already listed.

A created-meal row carries a trailing chevron and a swipe-to-delete with confirmation. Editing happens on the meal's quantity screen instead (§ 4.2): a pencil in the leading toolbar pushes `MyCreatedMealEditorView`, and a *Delete meal* button closes the list. Those two places are the only ones where created meals are edited or deleted ([design 0006](design/0006-own-daily-meals.md), *Update — 2026-09-19*).

Favourites, meals and submissions are loaded once in `onAppear`, not per keystroke, so this
re-ranking costs nothing. With an empty query, `displayedResults` returns `localFoodItems`, which
is empty — the Favourites and My submissions sections in the view render from `favouriteFoods` /
`mySubmissions` directly. A submission row carries a *pending* or *rejected* marker
(`FoodItemRow.submissionStatus`); tapping a rejected one reopens the new-food form pre-filled from
it instead of pushing to the quantity screen (§ 7).

### 2.3 Search orchestration

`onSearchTextChanged` is driven by `.task(id: viewModel.searchText)`, so SwiftUI cancels and
restarts it on every keystroke. Debouncing is a `Task.sleep(for: .milliseconds(300))` at the
top: a cancelled sleep throws, and the `catch` returns, which is what makes the debounce work.

The external fallback is then gated twice:

```swift
guard displayedResults.isEmpty && searchText.count >= 3 else { … }
```

`displayedResults`, not `localFoodItems` — so one matching favourite or one matching saved meal
suppresses the external search entirely. This reads like a bug until you check
[design 0003](design/0003-favourite-foods.md) §*Favourites first in the search results* and
[design 0006](design/0006-own-daily-meals.md) §*Search integration*, which both document this as
deliberate — a local favourite or meal match is treated as sufficient, so the network fallback is
skipped. [ADR 0012](adr/0012-external-food-is-surfaced-never-imported.md)'s "local search found
nothing" phrasing is a loose paraphrase of this, not a separate decision;
[ADR 0023](adr/0023-external-search-gate-includes-favourites-and-meals.md) corrects that phrasing
for a reader who hits the same "looks like a bug" reaction. A failed external search is still
swallowed into an empty list — interrupting a live search with an alert on every network hiccup
would be worse than showing nothing — but the failure is no longer silent: it is logged through
`Log.warning`, and since § 2.4's fix the thrown error distinguishes a genuine "no such product"
from a service failure rather than conflating the two (finding **A2-5**, fixed — see § 2.4).
`MyCreatedMealEditorViewModel`'s equivalent search path was the same call site copied into a
second feature and got the same logging fix.

### 2.4 OpenFoodFacts integration

One DTO file, `OpenFoodFactsProductDTO.swift`, covering three response shapes: the search
envelope (`products`), the barcode envelope (`status` + optional `product`), and the product
itself with its `nutriments`. Both requests send a `fields=` parameter so the API returns only
what is mapped.

`URLSession` is used **directly**, not through `FirestoreDataProviderProtocol` — the protocol is
Firestore-shaped and does not apply. Both use cases take a `session: URLSession` in their `init`,
defaulting to `.shared` so every existing call site is unaffected; the parameter exists purely so
`SearchFoodExternallyUseCaseTests` / `FetchFoodByBarcodeExternallyUseCaseTests` can inject a
session configured with `URLProtocolStub` and assert against a real request/response round trip.
The consequence recorded in a comment in `FetchFoodByBarcodeExternallyUseCase` still holds one
level up: `SearchFoodExternallyUseCaseFake` / `FetchFoodByBarcodeExternallyUseCaseFake`, used by
every *other* feature's tests, can only stub a return value and cannot assert which URL was
called — only the two use cases' own tests exercise the network layer for real.

Each request carries a `User-Agent` (`Constants.OpenFoodFacts.userAgent`, `Kalorie-iOS/<app
version>`) and a `Constants.OpenFoodFacts.requestTimeout` of 10 s rather than the default
60 — OpenFoodFacts' terms require an identifying client and rate-limit anonymous ones harder, and
a hanging request must not block the search spinner for a minute. The HTTP status is checked
before decoding: a non-2xx response throws `.serverError(statusCode:)` instead of falling into
`JSONDecoder` and surfacing as a generic decode failure indistinguishable from "no such product"
— finding **A2-5**, fixed. A 429 or 5xx is now retried, through the shared
`OpenFoodFactsTransientRequest.data(for:session:retryDelay:)`, up to
`Constants.OpenFoodFacts.maxAttempts` (3) with a linear backoff (`retryDelay * attempt`,
`Constants.OpenFoodFacts.retryDelay` = 500 ms) before the caller gives up — gated on the status
code of a response that was actually received, so it only ever retries the two codes that are
plausibly transient. A thrown `URLError` is retried too, but only `.timedOut` and
`.networkConnectionLost` — the two that stand a real chance of succeeding a moment later; any
other `URLError` (`.notConnectedToInternet` included) is rethrown on the first attempt, since
retrying it would not plausibly change the outcome — finding **A2-5**, fixed. Both use cases take
a `retryDelay` in their `init` alongside `session`, for the same reason: `URLProtocolStub`'s tests
inject `.zero` so a retry-exhaustion test doesn't pay the real backoff.

Mapping OpenFoodFacts → `FoodItemDomain` has a fixed shape:

- **Reject** anything without `nutriments`, without a positive `energy-kcal_100g`, or without
  any usable name. A partial item would render as 0 kcal, which is worse than "not found"; the
  caller treats `nil` as *product not found*.
- **Name preference** is `product_name_cs` → `product_name_en` → `product_name` for the Czech
  name, and `product_name_en` → `product_name` → the same raw name for the English one. Both
  run through `TextKit`'s `decodingHTMLEntities()`, because OpenFoodFacts names contain raw
  entities.
- **Every other nutrient defaults to `0`** when absent, and unsaturated fat is *derived* as
  `max(0, fat - saturatedFat)` — OpenFoodFacts does not publish it.
- `weight` is always `100`, because an external product carries no package weight.
- `energy_100g` (kJ) falls back to `MacroKit.energyKJFromMacros`, per
  [ADR 0007](adr/0007-derive-missing-energy-kj-from-macros.md).

This mapping lives in `OpenFoodFactsProductDTO.asDomain()`, following the same convention every
other DTO uses, and both external use cases call it rather than each carrying their own copy.

### 2.5 Barcode scanning

`DataScannerRepresentable` wraps VisionKit's `DataScannerViewController`, configured for
barcodes only, one item at a time, accurate quality. The scanner is mounted inside the sheet's
`ZStack` whenever `isScannerVisible` **and** `DataScannerViewController.isSupported` **and**
`.isAvailable`. Both permission-denied paths are guarded explicitly rather than left to that `if`
alone: the scan button in `addFoodItem` checks `isSupported && isAvailable` before ever calling
`onScannerButtonTapped()`, showing `cameraPermissionAlert` instead when it is not — so
`isScannerVisible` never becomes `true` while access is denied. If access is revoked *while* the
scanner is already visible (the app is backgrounded, the user disables the camera permission, then
returns), `AddFoodSheetView`'s `scenePhase` handler calls
`AddFoodSheetViewModel.onScenePhaseActive(isCameraAvailable:)` on the transition back to `.active`,
which closes the scanner and shows the same alert instead of leaving a blank `ZStack`.

The delivery path is deliberately indirect: the coordinator writes into a `@Binding` bound to
`viewModel.lastScannedBarcode`, and the view's `.onChange` on that property calls
`onBarcodeScanned()`. The view model then clears the property, so the same code can be
delivered again later. The coordinator keeps its own `lastDeliveredCode` to suppress the
repeated callbacks VisionKit fires while a barcode stays in frame, and clears it once a lookup
ends: `updateUIViewController` reports every `isSearching` change to the coordinator, and the
`true → false` transition — the same transition that resumes scanning — is what resets
`lastDeliveredCode` to `nil`. A rescan of the same barcode after a failed lookup therefore
produces a new callback instead of silence.

Scanning is stopped while a lookup is in flight (`isSearching` → `stopScanning()`), and a
`ProgressView` over `.ultraThinMaterial` covers the camera preview.

`AddFoodSheetView`'s inline scanner, `MyCreatedMealEditorView`'s identical one, and the barcode-rescan
`fullScreenCover` on the nutrition-label review screen (§ 7.5) all go through `BarcodeScannerOverlay`
(`Components/`) rather than `DataScannerRepresentable` directly — it adds the one thing all three
need and none had before design 0010's revision: a top-trailing close button, so `isScannerVisible`
(or `isBarcodeRescanVisible`) has an explicit user-driven path back to `false` instead of only
clearing on a successful scan.

**This is a separate scanner from the nutrition-label camera** (§ 7.5): `DataScannerRepresentable`
here is barcode-only, single item, and drives the search flow; `NutritionLabelScannerRepresentable`
recognises text and barcodes together and drives `RecognizeNutritionLabelUseCase` instead. Neither
wraps or extends the other, on purpose — reusing this one would mean teaching it to assert on text
items it isn't built to see (§ 7.5).

### 2.6 Creating a catalogue item

**Since [design 0009](design/0009-catalogue-moderation.md), `CreateFoodItemUseCase` is no longer
reachable from `AddFoodSheetViewModel` at all** — its only caller now is `ApproveSubmissionUseCase`
(§ 7). The *add a new food* form still collects the same fields, but `onCreateFoodItem` calls
`SubmitFoodItemUseCase` instead, which writes to `foodItemSubmissions`, not `foodItems`.

Its validation — id non-empty, all digits, of a valid barcode length; Czech name non-empty;
calories > 0; weight > 0; portions — is `FoodItemValidation`, a static predicate shared with
`SubmitFoodItemUseCase` and `UpdateFoodItemUseCase` (§ 7) so the three write paths cannot drift.
Each use case still wraps the result in its own error enum (`CreateFoodItemError`,
`FoodItemSubmissionError`, `UpdateFoodItemError`) so each caller's exhaustive `switch` keeps its
own alert strings; `AddFoodSheetViewModel` now switches over `FoodItemSubmissionError`, and
`ModerationReviewViewModel` / `ModerationCatalogueEditorViewModel` over the other two.

Two things to know before touching `CreateFoodItemUseCase` itself. First, the existence check reads
the target document straight from the server (`loadFromServerAsync(id:from:)`) rather than the
offline-capable equality query it used before. `firestore.rules` now grants the maintainer
`update` on `foodItems` (§ 1.6), so — unlike before design 0009 — the rule no longer closes the
race by itself; this existence check is the only thing standing between an approval and silently
overwriting a catalogue entry whose barcode entered the catalogue after the submission was filed
(the design's own required test case for this is `ApproveSubmissionUseCaseTests`). A
`permissionDenied` from `setAsync` is re-read and rethrown as `.itemAlreadyExists` rather than
assumed, since the same denial code can also mean an expired auth session. Whether an offline
`setAsync` hangs rather than throwing before reaching this rule is unverified — not reproducible
from a unit test or the simulator's default networking, and nobody has tried it on a real device
yet. Second, `eng_name_lowercase` is written as `""` for every manually created item, because the
form has no English name field.

`FetchFoodItemsUseCase`, which loaded the entire catalogue with no callers, has been deleted.

---

## 3. Dashboard and meal types

**Scope:** `Cross-platform` for meal assignment and the meal-window rules; `iOS` for the
caching and the screen structure.

**Read first:** [design 0005](design/0005-meal-window-and-html-entity-decoding.md) (the meal-window
arithmetic and which call sites delegate to `MealKit`),
[design 0004](design/0004-shared-macro-calculation-module.md) (macro totalling),
[design 0001](design/0001-user-authentication.md) (why the multi-device meal-type guard exists),
[ADR 0022](adr/0022-meal-assignment-may-be-pinned-by-the-user.md) (how a user moves an entry
between meals without touching its timestamp, and why a new entry is pinned at write time instead
of starting out unpinned), [ADR 0031](adr/0031-meal-type-pin-resolved-by-the-caller-not-the-save-use-case.md)
(why `SaveFoodConsumedUseCase` no longer resolves that pin itself — § 4.2 covers the picker this
enabled).

### 3.1 What the Dashboard is

One screen showing one day: a macro summary at the top, then the day's foods grouped into
sections by meal. Around it sit three navigations — a horizontal day picker, a month calendar
sheet, and a meal-type editing sheet — plus the add-food sheet and the account sheet.

`DashboardViewModel` owns all of it. It holds two pieces of server state (`mealTypes`,
`foodsConsumed`), the selected day, five `Bool`s driving sheets, and the month cache. Everything
displayed is derived from those by computed properties, so no aggregate is ever stored.

### 3.2 Meal assignment

The base rule is still **a food belongs to a meal window if its time of day falls in the
window**, with the calendar date playing no part — see
[ADR 0014](adr/0014-meal-assignment-by-time-of-day-only.md), which is the thing to read before
changing anything here. [ADR 0022](adr/0022-meal-assignment-may-be-pinned-by-the-user.md) layers
one override on top: a `foodConsumed` document may carry an optional `meal_type_id`, pinning the
entry to that meal regardless of what its time of day would otherwise select.

A new entry's `meal_type_id` is resolved before `SaveFoodConsumedUseCase` is ever called — the use
case just persists whatever it is handed
([ADR 0031](adr/0031-meal-type-pin-resolved-by-the-caller-not-the-save-use-case.md)). § 4.2 covers
the picker that does the resolving; the default it falls back to when untouched is still
`mealTypes.mealType(at:)`, the same window match ADR 0022 introduced. An entry logged outside every
window is still written unpinned; there is nothing to resolve it to. The meal-type pickers in
`FoodQuantityView` and `FoodConsumedDetailView` only ever move the pin to another concrete meal
type — there is no "by time" option to revert to the implicit, time-derived state, on either
screen. On the edit screen, picking a meal type stages the change; the screen's single Save
button writes whichever of weight and meal type actually changed. Documents that predate this
field keep `meal_type_id` absent and keep resolving dynamically through the fallback described
next; they are not migrated.

`groupedFoods` resolves each food to at most one meal type id up front, via
`mealTypes.resolvedMealTypeId(for:)`: a food **pinned to it** (`mealTypeId` names a meal type that
still exists) resolves to that pin; otherwise it falls back to `mealType(at:)`, which sorts the
meal types by `startTime` and returns the first whose window contains the food's time of day.
Foods are grouped by their resolved id into a dictionary, then `groupedFoods` walks `mealTypes` in
`startTime` order and emits a section for each one with a non-empty group — a meal type with
nothing resolved to it is skipped, so empty meals are not rendered. Foods that resolve to no meal
type at all — outside every window, with no pin or an unresolvable one — collect into a trailing
section with `mealType: nil`, rendered under `L10n.Dashboard.sectionUnassignedFoods`.

Overlapping windows list an unpinned food once, in the earliest-starting window that contains it,
because `mealType(at:)` itself picks exactly one match per lookup — there is no separate
per-meal-type filter or assigned-tracking involved. A pin bypasses the window search entirely,
since `resolvedMealTypeId` returns it directly whenever the named meal type still exists.

Within a section, foods are not reordered — they keep the order the month query returned, which
is by `date`. A pinned entry therefore does not sort to the top of its section; an entry logged at
22:00 and pinned to breakfast appears after every entry actually logged that morning. This is
deliberate ([ADR 0022](adr/0022-meal-assignment-may-be-pinned-by-the-user.md) Consequences), not
an oversight.

The trailing unassigned section is still the answer to "what happens to a food outside every
meal's range and with no usable pin": it is shown, labelled, and counted in the day total — but
always last. What ADR 0022 does not reach: the day a food lands on. A pin governs the section
within a day; the entry's `date` — and therefore which day it appears on — is never touched. An
entry logged just after midnight for the previous day therefore sits on the wrong day, with no pin
able to move it. This is an accepted limitation, not tracked as an open finding: editing an
entry's date/time would add a second axis of state to reconcile against the month cache
([ADR 0015](adr/0015-dashboard-caches-a-month-and-derives-the-day.md)) for a rare edge case, more
complexity than the app's day-boundary case warrants.

### 3.3 Macro aggregation

Two levels, both computed, both delegating to `MacroKit`:

- `DashboardViewModel.dailyMacros` → `DailyMacros(foods: foodsConsumed)` → `MacroKit.total`.
- Per-section, in the header popover: `MealSectionMacroView(name:foods:)` runs the same
  `DailyMacros(foods:)` over that section's foods.

`DailyMacros` is an adapter, not logic — it maps `[FoodConsumedDomain]` into `[Macros]`, calls
`MacroKit.total`, and maps back. There are no goals or targets in the app; the summary shows
totals only.

The day total is computed from `foodsConsumed` directly, while the sections are computed
separately — the two agree only because § 3.2 assigns every food to exactly one section. A
change there that let a food appear twice would inflate the section totals while leaving the day
total right, which is how that class of bug stays invisible.

### 3.4 Meal type lifecycle

`refreshMealTypes()` is the only entry point, and it carries the multi-device guard:

```swift
var types = try await fetchMealTypes()
if types.isEmpty, try await confirmMealTypesEmpty() { types = try await setupDefaultMeals() }
```

`FetchMealTypesUseCase` reads through Firestore's default source, which serves the offline cache
when the server is unreachable — so on a second device with a cold cache it can legitimately
return empty for a user who has meal types. `ConfirmMealTypesEmptyUseCase` re-asks with
`source: .server` before the defaults get written over them. Both calls are needed; dropping
either reintroduces the bug.

Editing happens in `MealTypeSheetViewModel`, and the set of possible edits is deliberately
narrow:

- **Create** — validated in `CreateMealTypeUseCase`: non-empty name, unique name, at least 30
  minutes long, no overlap with an existing window. The validation runs against the
  `existingMealTypes` array the sheet passes in, i.e. against client state, never against the
  server.
- **Delete** — blocked when only one meal type remains (`errorLastMealType`), so the collection
  can never become empty through the UI. `setupDefaultMeals` therefore only ever fires for a
  genuinely new user.
- **Reorder** — and this one is surprising enough to spell out. `onMove` snapshots the time
  windows *by position*, moves the rows, then writes the original times back by position. The
  time slots stay put; the meals move between them. Dragging "Dinner" above "Lunch" gives
  Dinner the earlier window, it does not carry its 17:00–20:00 with it. This is intentional —
  it is what keeps the windows contiguous and non-overlapping — and it is why the reorder is
  saved through `UpdateMealTypeTimesUseCase` rather than through some ordering field.

There is no path that edits a window's times directly, which is why `UpdateMealTypeTimesUseCase`
needs no overlap validation: it only ever receives a permutation of an already-valid set.

### 3.5 Day selection and the month cache

The month-at-a-time caching strategy, its invalidation rule and its costs are recorded in
[ADR 0015](adr/0015-dashboard-caches-a-month-and-derives-the-day.md).

What matters at the call sites is that `selectedDay` is not just a day — it is a full `Date`,
and **its time-of-day is what a newly logged food inherits**. `DashboardView` passes
`viewModel.selectedDay` into `makeAddFoodSheetView(for:)`, which passes it down to
`FoodQuantityViewModel.selectedDate`, which hands it to `SaveFoodConsumedUseCase`. Both ways of
changing the day carry over a sensible time of day:

- `DayPickerView` steps with `calendar.date(byAdding: .day, …, to: selectedDay)`, which
  **preserves** the time of day.
- `MonthCalendarView.selectDay` rebuilds the date from year/month/day components, then sets its
  time from `Date.now` if the picked day is today, otherwise from `selectedDay`'s own time — so
  it never resolves to local midnight.

### 3.6 Refresh triggers

| Trigger | Method | Effect |
|---|---|---|
| `.task` on the view | `onAppear` | resets `selectedDay` to now, loads meal types and the month |
| `scenePhase` → `.active` | `onRefresh` | meal types + invalidate and reload the month |
| Pull to refresh | `onRefresh` | same |
| Returning from food detail | `onFoodConsumedUpdated` | invalidate and reload the month |
| Meal type sheet changed | `onMealTypesChanged` | meal types only |
| Day changed / picked | `onDayChanged` / `onDaySelected` | cache hit, or load that month |
| Calendar month paged | `onCalendarMonthChanged` | cache hit, or load that month |

`onRefresh` guards against a cold-launch race with `hasCompletedInitialLoad`: the `scenePhase`
handler and pull-to-refresh both call it, but it is a no-op until `onAppear`'s initial load has
completed, so the month is never fetched twice on launch.

### 3.7 Deleting a logged entry

A trailing swipe on a food row calls `onDeleteRequested`, which stages the entry in
`foodPendingDeletion` and raises a confirmation alert; `onDeleteConfirmed` then calls
`DeleteFoodConsumedUseCase` — a single `deleteAsync(id:from:)` against the user's `foodConsumed`
collection — invalidates the day's month cache entry and reloads it, exactly as the update path
does. There is no undo and no soft-delete flag: the document is gone, and the confirmation alert
is the only thing standing in front of that. Failure surfaces as
`L10n.Dashboard.errorDeleteFailed` and the row stays.

The delete lives on the Dashboard row only — `FoodConsumedDetail` (§ 4.5) edits an entry, it does
not remove one.

This path did not exist when [design 0006](design/0006-own-daily-meals.md) was written, and its
fourth argument for Variant A — *"Variant B would therefore let one tap deposit eight permanently
unremovable rows"* — is no longer true. The design doc is frozen and stays as written; the
decision it reached still stands, since its other three arguments are untouched.

---

## 4. Food entry flow

**Scope:** `Cross-platform` for the scaling rules; `iOS` for the screens and input handling.

**Read first:** [design 0004](design/0004-shared-macro-calculation-module.md) (calorie rounding —
it unified the *rule*, not the *basis*), [design 0003](design/0003-favourite-foods.md) (the detail
screen's favourite button and its catalogue lookup),
[design 0006](design/0006-own-daily-meals.md) (the quantity defaults for a created meal, and why
the favourite button is hidden rather than disabled), [design 0008](design/0008-food-portions.md)
(the portion unit options and the personal-portions sheet — including its dated `Update` paragraph,
which reversed that document's own order and default),
[ADR 0030](adr/0030-first-quantity-picker-option-is-the-preselected-unit.md) (why the picker's
order and its preselected unit are one decision, and the two ways reselecting a unit
programmatically goes wrong), [design 0011](design/0011-food-measure-grams-or-millilitres.md)
(why every amount of the food itself, but not a nutrient amount, follows the item's measure),
[ADR 0031](adr/0031-meal-type-pin-resolved-by-the-caller-not-the-save-use-case.md) (the meal-type
picker on this screen, and why resolving its default moved out of `SaveFoodConsumedUseCase`).

### 4.1 The path a food takes

```
AddFoodSheet ──select──▶ FoodQuantity ──confirm──▶ SaveFoodConsumedUseCase ──▶ foodConsumed
                                                                                    │
Dashboard ────tap────▶ FoodConsumedDetail ──save──▶ UpdateFoodConsumedUseCase ──────┘
          └───swipe──▶ (confirm) ──────────────────▶ DeleteFoodConsumedUseCase ──────┘
```

`AddFoodSheet` answers *which food* (§ 2). `FoodQuantity` answers *how much*, and is the only
screen that writes a new entry. `FoodConsumedDetail` is the after-the-fact editor: it changes the
weight and, since [ADR 0022](adr/0022-meal-assignment-may-be-pinned-by-the-user.md), the meal type
the entry is pinned to — its single Save button writes whichever of the two actually changed, so
`meal_type_id` is emphatically **not** written only at logging time (§ 3.2). Removing an entry is
not on that screen at all; it is a swipe on the Dashboard row (§ 3.7).

Both quantity screens are assembled by `AddFoodSheetConfigurator.createView(date:…)`, which
takes the Dashboard's `selectedDay` and threads it down to `FoodQuantityViewModel.selectedDate`.
That date is what the entry is stamped with — see § 3.5 for why its time-of-day matters.

### 4.2 Choosing an amount

`FoodQuantityViewModel` holds `quantity: Double` and `unit: FoodQuantityUnit` (`.grams` = 1 g,
`.hundredGrams` = 100 g, or `.portion(FoodPortionDomain)`), and derives everything from
`grams = quantity * unit.gramsPerUnit`. `unitOptions` builds the picker's list at runtime —
`personalPortions` first, then the item's own canonical `portions`, then `.grams` and
`.hundredGrams` — rather than `FoodQuantityUnit` being `CaseIterable`, since the portion set is per-item
and only known once the item is loaded. Neither group is sorted: the order within each is
Firestore array order, which is the order the portions were authored in. This order, and the rule
that the first option is always the preselected unit, is
[ADR 0030](adr/0030-first-quantity-picker-option-is-the-preselected-unit.md)'s decision.

`personalPortions` is fetched once in `onAppear()` and only when `item.kind == .catalogue`. For a
created meal it is instead seeded from `meal.portions` in `init`, `unitOptions` omits `item.portions`
(they are the same list), and add/delete write the meal through `UpdateMyCreatedMealUseCase`
([ADR 0033](adr/0033-created-meal-portions-editable-from-the-quantity-screen.md)). When
`isPersonalPortionsAvailable` (a catalogue item or a created meal), the unit `Menu` gains a `Divider()` and a button
(`L10n.FoodQuantity.buttonMyPortions`) that pushes `FoodPortionsManagerView` via
`navigationDestination`, pre-filling the grams field with the screen's current `grams`. Pushing
rather than sheeting means it now follows the same pushed-not-sheeted pattern § 7.3 describes
instead of being the one exception to it; it shares the *same* `FoodQuantityViewModel` rather than
owning one.
See [design 0008](design/0008-food-portions.md) for the write-once/editable split
between a canonical portion (authored once, in `AddFoodSheetView`'s new-food form or the meal
editor) and a personal one. Frequency-derived quick-add amounts are the still-unbuilt other half
of the same `TODO.md` line.

Both call sites of `PortionInputRow` — `FoodPortionsSection.swift`'s canonical-portion rows and
`FoodPortionsManagerView.swift`'s personal-portion rows — draw their own full-width `Rectangle`
separator between rows instead of the system default: `.listRowSeparator(.hidden)` alone does not
reliably suppress it on this List, and the default separator's inset is asymmetric (aligned to
the row's leading content, flush on the trailing edge) rather than spanning the row evenly. Each
row's `listRowInsets` widens at the edges for a visual separation.

`FoodPortionsManagerView` deliberately mirrors the portions editor of *Build your own meal*
(`FoodPortionsSection`). Saved personal portions are listed read-only, followed by
`FoodQuantityViewModel.portionDrafts` — a list that **always holds at least one row**: opening the
screen seeds one draft (`onPortionsManagerOpened`, always blank),
deleting the last draft re-seeds an empty one, and a successful save resets to one empty row. The
`+` button appends an empty draft and is disabled until *every* draft has a name and grams
(`arePortionDraftsComplete`); the navigation-bar Save is enabled as soon as *at least one* draft is
complete (`canSavePortionDrafts`), so a filled row stays savable after `+` has added an empty one
below it. `onSavePersonalPortions` skips fully empty drafts but alerts on a half-filled one instead
of dropping it silently, and writes all remaining drafts in one `setAsync`.

The `+` button must live in its **own `Section`** with `.listSectionSpacing(0)`, as it does in
`FoodPortionsSection`. Placed inside the rows' `Section`, the List draws the group as continuing
through the button row and the last portion row loses its bottom corner radius.

For a created meal, `FoodQuantityView` gains two more things, attached through `mealActions(makeEditorView:onDelete:)` from `AddFoodSheetView`: a pencil in the leading toolbar and a destructive *Delete meal* button in a last section, behind an alert. The editor's `navigationDestination` is declared on `FoodQuantityView` itself, not on the sheet's root — a second `navigationDestination(isPresented:)` on the root, activated while the quantity screen is already pushed through another, replaces that screen's content instead of pushing. Saving or deleting sets `isPushedToQuantityView = false`, returning to the sheet's list, since the quantity screen would otherwise keep showing the pre-edit meal.

Two defaults are set by the configurator rather than by the view model: selecting one of the
user's own meals pre-fills `quantity: item.weight, unit: .grams` — the meal's total gram weight,
because a saved meal is normally logged whole — while everything else goes through
`FoodQuantityViewModel.defaultUnit(for:)`, which returns `item.portions.first` and falls back to
`.grams` for an item with no canonical portion — paired with `quantity: 100` from the configurator,
so a portion-less item opens at `100 × 1 g` rather than `1 × 100 g`
([ADR 0030](adr/0030-first-quantity-picker-option-is-the-preselected-unit.md)). Personal portions are deliberately not part
of this synchronous step: they resolve in `onAppear()`, after the picker's initial selection has
already been made (design 0008's `Update — 2026-09-16`). Once they resolve,
[ADR 0030](adr/0030-first-quantity-picker-option-is-the-preselected-unit.md)'s step 2 moves the
selection to `personalPortions.first` — unless the user has already picked a unit through
`FoodQuantityView.unitBinding`, which routes every user-driven pick through
`FoodQuantityViewModel.onUnitSelected(_:)` and marks the picker as touched. `onAppear`'s own write
goes straight to the `unit` property instead, so it neither rescales `quantity` nor counts as a
touch.

The macro preview shown under the input is computed by `scaledMacros` / `scaledCalories` on the
view model, which recompute on every keystroke and are never persisted. Both the preview and
`SaveFoodConsumedUseCase`'s persisted values go through the same
`FoodItemDomain.scaled(toGrams:)` helper, so the two never diverge.

Every label naming an amount of the food itself — the unit picker's rows, the personal-portions
sheet, the Dashboard row, the detail screen's weight suffix — reads `g` or `ml` off the item's own
`measure` (`Double.formattedAmount(measure:)`, a sibling of `formattedGrams`). Macro rows are the
one exception and stay `formattedGrams` unconditionally, since a nutrient amount is always grams
regardless of what the food itself is measured in. See
[design 0011](design/0011-food-measure-grams-or-millilitres.md).

Unit switching goes through `onUnitChanged(from:to:)`, which converts the current gram amount
into the new unit by dividing, with no rounding — a fractional quantity survives a unit switch.

A second `List` row, below the quantity row, holds a meal-type picker — `selectedMealTypeId`,
preselected from `mealTypes.mealType(at: selectedDate)?.id` at init, the same window match
`SaveFoodConsumedUseCase` used to do internally before
[ADR 0031](adr/0031-meal-type-pin-resolved-by-the-caller-not-the-save-use-case.md). Picking a row
calls `onMealTypeSelected(_:)`, which marks the pick as user-driven; `onConfirm()` then refetches
`mealTypes` (as it already did) and, for a user-driven pick, confirms the chosen id still exists in
that fresh list before saving — failing loudly with `L10n.Common.errorUnknown` if a meal type was
deleted from under the picker rather than silently falling back to the time-based default. An
untouched pick always resolves fresh against the refetched list too, so the pin still reflects
whatever `mealTypes` looks like at save time, not at sheet-open time. Like the edit screen's
picker, there is no "by time" option to select — an unresolved pin renders only as a display
placeholder, `L10n.FoodQuantity.mealTypeUnassigned`.

### 4.3 Numeric input

Two different text-field strategies coexist:

- **`FoodQuantityView`** binds a `String` (`quantityText`) and sanitises it in `onChange`:
  keep ASCII digits, allow at most one `.` or `,`, normalise the comma to a dot, then
  `Double(normalized) ?? 0` into `viewModel.quantity`. An empty or unparseable field therefore
  clears `quantity` to `0` rather than keeping a stale value, and `onConfirm`'s existing
  `grams > 0` guard shows `errorInvalidQuantity` for it.
- **`BaseDoubleTextField`**, used by the add-a-new-food form, binds a `Double` through
  `NumberFormatter.decimal` — a shared static with `numberStyle = .decimal` and
  `zeroSymbol = ""`, so an empty field reads as zero and a zero renders as empty. Being a
  `TextField(value:formatter:)`, it commits on end-editing rather than per keystroke. Its unit
  suffix is an explicit `unit: String` parameter, so energy and calorie fields are labelled
  correctly instead of defaulting to grams.

### 4.4 Writing and rescaling

`SaveFoodConsumedUseCase` scales the catalogue item's per-100 g values by `grams / 100`. It
scales `calories` **separately** from the rest, because `caloriesPerHundredGrams` is fractional
and folding it into `Macros.scaled` would round it twice — this is one of the few places in the
codebase carrying an explanatory comment, and it is there for a reason.

`UpdateFoodConsumedUseCase` scales the *entry's own* stored values by `newWeight / food.weight`
and never consults the catalogue. Why is [ADR 0016](adr/0016-logged-entries-rescale-from-their-own-stored-values.md).

`foodConsumed` stores `calories_per_hundred_grams` alongside the absolute `calories` — the
entry's own fractional snapshot, set once at logging time and never touched by an edit. Both
paths therefore round a fractional per-100g value exactly once, via
`MacroKit.scaledCalories(caloriesPerHundredGrams:ratio:)`: logging uses `grams / 100` against the
catalogue item's value, editing uses `newWeight / 100` against the entry's own stored value. The
macro `Double` fields are unaffected and keep scaling by `newWeight / food.weight` from their
absolute stored values, since only `calories` was ever rounded to an `Int`. A document written
before this field existed decodes `calories_per_hundred_grams` as missing and
`FoodConsumedDTO.asDomain()` derives it once, from `calories / weight * 100`
(`MacroKit.caloriesPerHundredGrams(calories:weight:)`) — the same one-time approximation the
rounded value always implied, just no longer compounding on every subsequent edit.

### 4.5 The detail screen

`FoodConsumedDetailViewModel` keeps `weight` (the edited value) alongside `savedWeight` (what is
on the server), so `hasWeightChanged` can gate the Save button together with `hasMealTypeChanged`
— `hasChanges` is the union of the two, and `onSave` writes each half only if it moved. `food`
itself is replaced only after a write succeeds (`withScaledWeight`, `withMealTypeId`), never while
the user is editing, which is what keeps repeated edits correct: `scaledMacros` always scales from
the last persisted state, so typing 200 then 150 gives the same result as typing 150 outright.

`onAppear` runs two lookups concurrently: whether the entry is favourited
(`IsFavouriteFoodUseCase`) and whether its catalogue item still exists, via
`loadCatalogueItem()` — which switches on `food.foodItemKind`: `.catalogue` uses
`FetchFoodItemByBarcodeUseCase`, `.external` uses `FetchFoodByBarcodeExternallyUseCase` against
OpenFoodFacts, and `.createdMeal` has none. The catalogue item is needed because favouriting
*adds* a full snapshot and therefore needs a `FoodItemDomain` — `canShowFavouriteButton` is
`isFavourite || catalogueItem != nil`, so the button resolves correctly for OpenFoodFacts entries
too, and only stays hidden for a user's own created meals.

After a successful save the screen shows a checkmark for two seconds via `Task.sleep`, then
clears it. The toolbar button itself is the shared `SaveToolbarButton` (`Components/`): a *Save*
that is disabled until there is something to save and swaps to a green checkmark while the flag is
set. `FoodPortionsManagerView` uses the same component, driven by `showPortionCheckmark` on
`FoodQuantityViewModel`. The flag stays in the view model, not in the component.

### 4.6 Favourite toggling

Shared between `FoodQuantityViewModel` and `FoodConsumedDetailViewModel` through the
`FavouriteToggling` protocol extension: guard against re-entry, flip optimistically, call
`AddFavouriteFoodUseCase` or `RemoveFavouriteFoodUseCase`, roll the flag back and alert on
failure. See [ADR 0017](adr/0017-optimistic-favourite-toggle-shared-by-protocol-extension.md).

`FoodQuantityViewModel` additionally passes an `onToggled` callback so `AddFoodSheetViewModel`
can keep its in-memory favourites list in step without re-querying.

The two screens also *place* the button identically — inline in a header row alongside the food's
name, in place of the nav bar title, above the quantity/weight content — rather than the earlier
header-less `Section` centred between two `Spacer`s. That sameness is load-bearing rather than
incidental: `FavouriteButton` exists as a component precisely because the button is built twice
([design 0003](design/0003-favourite-foods.md)), so the placement moved on both screens together.

---

## 5. Cross-cutting concerns

**Scope:** `iOS` throughout.

**Read first:** nothing. No design doc covers error presentation, localization or `Components/` —
this area has never been designed up front, which is itself worth knowing.
[design 0004](design/0004-shared-macro-calculation-module.md) and
[design 0005](design/0005-meal-window-and-html-entity-decoding.md) cover only the KMP modules the
extensions bridge to.

### 5.1 Application root

`KalorieApp` renders one of three things off `AuthStateObserver.state`: a `ProgressView` while
authentication resolves, the Dashboard once it is `.loaded`, or a full-screen error with a
**Retry** button. Google's `onOpenURL` handler and the account-merge overlay hang off the loaded
branch.

The Dashboard carries `.id(authState.userId)`. That is what guarantees a clean slate when the
signed-in user changes — every view model below is rebuilt rather than re-pointed at another
user's data. This is deliberate; the accepted cost is that any auth transition also discards the
Dashboard's transient state, including the selected day.

### 5.2 Error handling

Two conventions coexist, and the split is intentional:

| Where | Mechanism | Recovery |
|---|---|---|
| Root (auth) | `LoadingState.error(Error?)` rendered as a screen | Retry button |
| Every feature | `@Published var alertItem: AlertItem?` + `.alert(item:)` | Dismiss only |

[ADR 0018](adr/0018-per-feature-error-alerts-with-no-global-handler.md) records why there is no
global handler. In practice a feature's `catch` does one of three things:

- **switch over a typed error enum** and pick a specific message — `CreateFoodItemError` and
  `CreateMealTypeError` are the two use cases that make this possible;
- **collapse to `L10n.Common.errorUnknown`** — the common case;
- **swallow with `try?`** — thirteen call sites, mostly optional enrichment such as
  `fetchFavouriteFoods()` on appear or `saveProfileIfNeeded` after sign-in.

`LoadingState<T>` is the shared state enum (`idle`, `loading`, `loaded(T)`, `error(Error?)`),
with a `LoadingState<Void>.loaded` convenience for the many screens whose success carries no
value. Features use `.idle` / `.loading` / `.loaded` and never `.error`; only the auth root uses
the error case.

`View.loader(_:)` is the shared busy indicator — a `ProgressView` overlay plus
`allowsHitTesting(!isLoading)`, so a loading screen is inert.

`Log` (`Core/Utils/Log.swift`) wraps Crashlytics and OSLog behind `Log.error` / `Log.warning`,
categorised via `Constants.LogCategory`. Most `catch` blocks that don't present an alert still
log through it, so a caught error in a release build reaches Crashlytics even when nothing is
shown on screen.

### 5.3 Localization

One `Localizable.xcstrings` catalogue, source language `cs`, with `en` alongside — reached
exclusively through the hand-written `L10n` enum, per
[ADR 0019](adr/0019-l10n-enum-over-the-string-catalogue.md). A second catalogue,
`InfoPlist.xcstrings`, covers `Info.plist` keys the OS reads directly and that `L10n` cannot
reach — today just `NSCameraUsageDescription`, matched by key name rather than by going through
`L10n`.

One thing about the app is Czech-first in a way neither catalogue covers:

- `BilingualNamed.displayName` picks `czName` when the device language is `cs` or `sk`, and
  otherwise `engName` falling back to `czName`. This is data-level localisation — the food's own
  two names — and it is independent of both string catalogues.

Numbers and units go through `Double.formattedGrams(fractionDigits:)`
(`Core/Extensions/Double+Extension.swift`), a locale-aware helper that replaced seventeen
hardcoded `String(format: "%.1f g", …)` call sites across five files — the decimal separator now
follows the device locale, including the Czech comma.

### 5.4 Components

`Components/` holds nine small view types, split into two groups by naming:

- **`Base*`** — generic primitives with no domain knowledge: `BaseButton`, `BaseImage`
  (+ `Helpers/BaseImageName`, an enum of the SF Symbol / asset names in use),
  `BaseStringTextField`, `BaseDoubleTextField`.
- **Domain views** — `FoodItemRow` (a catalogue item plus its favourite heart), `FoodConsumedView`
  (a logged entry: weight, name, calories), `MacroDonutView`, `FavouriteButton`,
  `DismissToolbarItem`.

Every one carries a `#Preview`, which is where most of the project's sample `FoodItemDomain`
values live.

`FoodConsumedView` lives in `FoodConsumedView.swift`, matching the project's own
`[Feature][Type].swift` rule.

### 5.5 Extensions

`Core/Extensions/` is nine files of small, mostly single-purpose helpers. Four carry real
behaviour and are worth knowing about before adding a fifth:

- `Date+Extension` — `minutesSinceMidnight` (the bridge into `MealKit`, see
  [ADR 0014](adr/0014-meal-assignment-by-time-of-day-only.md)), `formatCacheKey(with:)` (the
  Dashboard's cache-key builder — locale-independent via a cached `en_US_POSIX` formatter per
  format string), and the `withAddedMinutes` / `withAddedHours` arithmetic the meal sheet uses.
- `String+Extension` — HTML entity decoding, delegating to `TextKit`.
- `Double+Extension` — `formattedGrams`, the locale-aware gram formatter every screen goes
  through (see § 5.3).
- `View+Loader` — `View.loader(_:)`, the shared busy indicator every screen overlays (see § 5.2).

The rest (`CGFloat`, `Int`, `TimeInterval`, `NumberFormatter`, `UIWindowScene`) are one or two
members each.

---

## 6. Authentication

**Scope:** `Backend` for the security rules' dependence on `request.auth.uid` (see § 1.6);
`Cross-platform` for the anonymous-first identity model and the merge algorithm; `iOS` for the
provider adapters, the on-disk snapshot format and the account screen.

**Read first:** [design 0001](design/0001-user-authentication.md) (anonymous-first identity,
Apple sign-in, the merge algorithm), [design 0002](design/0002-google-sign-in.md) (Google as a
second provider, identity collisions), [ADR 0001](adr/0001-anonymous-firebase-auth-as-device-identity.md),
[ADR 0002](adr/0002-merge-anonymous-data-before-switching-accounts.md),
[ADR 0003](adr/0003-separate-auth-command-provider.md),
[ADR 0004](adr/0004-migrate-usecase-exposes-two-methods.md),
[ADR 0005](adr/0005-no-shared-sign-in-provider-abstraction.md),
[ADR 0006](adr/0006-google-sign-in-identity-collisions.md). Both design docs are frozen at
2026-08-07/08 and predate three things this section covers that exist only in the code: the
re-auth guard in front of account deletion (§ 6.3), the `Log.warning`/`Log.error` calls in `try?`
branches (§ 6.4), and Google session clearing (§ 6.1).

### 6.1 Reading vs. mutating auth state

`AuthProviderProtocol` (`AuthProvider.swift`) is read-only — `userId`, `isAnonymous`,
`displayName`, `lastSignInDate`, `linkedProviderKind` — every property computed straight from
`Auth.auth().currentUser`. `AuthCommandProviderProtocol` (`AuthCommandProvider.swift`) holds every
mutation: `link`, `signIn`, `reauthenticate`, `signOut`, `updateDisplayName`,
`deleteCurrentUser`. The split exists so the eight persistence use cases that only ever need a
`userId` keep depending on the narrow read-only protocol instead of carrying auth mutations they
never call — see [ADR 0003](adr/0003-separate-auth-command-provider.md).

`linkedProviderKind` maps `providerData.first?.providerID` (`"apple.com"` / `"google.com"`) to
`AuthProviderKind`. The `.first` relies on an invariant the type itself does not enforce — an
account never carries more than one linked provider — which is instead guaranteed by
`LinkOrMergeCredentialUseCase` (§ 6.2) refusing to link a second one.

Each provider gets its own sign-in adapter with no shared protocol above them:
`AppleSignInProvider` wraps a delegate-driven `ASAuthorizationController`, generating and hashing
its own nonce via `NonceGenerator`; `GoogleSignInProvider` wraps `GIDSignIn`, resolving the client
ID from `FirebaseApp.app()?.options.clientID` rather than duplicating it in `Info.plist`. Per
[ADR 0005](adr/0005-no-shared-sign-in-provider-abstraction.md) the two signatures are irreducibly
different — Apple hands back `(ASAuthorization, nonce)` through a delegate callback, Google's SDK
drives its own flow and takes nothing — so a shared protocol above them would unify a couple of
lines at the cost of a lowest-common-denominator signature. Both adapters are `@MainActor`, since
both need a live presenting window; on `GoogleSignInProvider` that inference extends to the whole
struct including its synthesized initializer, which is why the initializer is marked `nonisolated`
by hand on both it and its `Fake` — an implementation detail design 0002's Outcome section flags as
unanticipated. `GoogleSessionProvider.clearSession()` is the third small wrapper here: a one-method
protocol over `GIDSignIn.sharedInstance.signOut()`, used only by `SignOutUseCase` (§ 6.2) because
Firebase forgetting the user does not make the Google SDK forget its cached session, and design
0002 requires that signing out actually offers the account chooser again.

`ReauthenticateUseCase`, added 2026-09-01 and covered by neither design doc, is a third consumer of
the same two adapters: it switches on `authProvider.linkedProviderKind` to decide which provider to
re-run, then feeds the resulting credential to `authCommandProvider.reauthenticate(with:)`. See
§ 6.3 for why it exists.

### 6.2 Anonymous-first identity and the merge

Every install signs in anonymously on first launch and keeps that account as its identity for as
long as the user stays signed out — `AuthStateObserver` calls `Auth.auth().signInAnonymously`
whenever the auth-state listener reports no user. This is the identity model
[ADR 0001](adr/0001-anonymous-firebase-auth-as-device-identity.md) chose over a client-generated
identifier: only a Firebase UID is verifiable by `request.auth.uid` in the security rules (§ 1.6),
so it is what makes owner-only rules possible at all.

Signing in goes through `LinkOrMergeCredentialUseCase`, which is provider-agnostic — it takes a
bare `AuthCredential`, never an `ASAuthorization` or a Google type — and tries `link()` first. Three
outcomes, all from [ADR 0006](adr/0006-google-sign-in-identity-collisions.md):

- **Succeeds** — the anonymous account is upgraded in place, same UID, nothing to migrate.
- **`credentialAlreadyInUse`** — the credential already belongs to another Firebase account (the
  normal case on a second device). Handled by migrating, below.
- **`emailAlreadyInUse`** — the credential's e-mail belongs to an account reached through the
  *other* provider. `LinkOrMergeCredentialUseCase` throws
  `LinkOrMergeCredentialError.accountExistsWithAnotherProvider`, which `AccountViewModel` catches
  by name to show a "sign in with Apple instead" alert rather than the generic sign-in failure.
  There is deliberately no "proceed with Google anyway" option — seeing this error at all depends
  on the Firebase project staying on *one account per email address*; the alternative setting
  removes the error and makes the next case (below) the unconditional behaviour instead.
- **Anything else that links cleanly** — an e-mail mismatch (common with Apple's private relay
  address) reaches no error path at all and silently produces a second, unrelated account. Case 3
  in ADR 0006's table; accepted as undetectable rather than mitigated.

The migrate path is `MigrateAnonymousDataUseCase`, one type with two methods rather than a single
`callAsFunction` — see [ADR 0004](adr/0004-migrate-usecase-exposes-two-methods.md) for why the
convention is deviated from here. `migrate(fromAnonymousUserId:credential:)` is the live path: it
loads `foodConsumed`, `favouriteFoods`, `myCreatedMeals` and `foodItemPortions` for the anonymous
user concurrently via `async let`, writes the result to disk as a `PendingMergeSnapshot` *before* calling
`authCommandProvider.signIn(with:)`, then replays the snapshot under the new UID and deletes it.
The disk write has to come first: the moment `signIn` succeeds, owner-only security rules mean the
client can no longer read the anonymous account's data, so anything not already captured is
unreachable — [ADR 0002](adr/0002-merge-anonymous-data-before-switching-accounts.md) is the record
of why this ordering is the non-obvious part. The replay (`writeAndCleanup`) is idempotent because
every document keeps its original id through `batchSetAsync`, so resuming after a crash overwrites
rather than duplicates (§ 1.5 covers `batchSetAsync`'s own chunking behaviour, which this use case
is explicitly designed to tolerate). Meal types are never merged — their historical ids collided
across accounts — so the signed-in account's own layout always wins.

`resumeIfNeeded()` is the crash-recovery path, called once per launch from
`AuthStateObserver.resumePendingMergeOnce` and guarded by `hasAttemptedPendingMergeResume`: without
that guard, the `signIn(with:)` call inside `migrate` would re-trigger the very same auth-state
listener that calls `resumeIfNeeded`, running the resume concurrently with the migrate that is
still in flight. On resume, a snapshot whose `sourceAnonymousUserId` still matches the current user
means the app crashed before `signIn` ever completed, so the snapshot is simply deleted; otherwise
`writeAndCleanup` runs exactly as it would from the live path. `SignOutUseCase` deletes any
snapshot before signing out, for a mirror-image reason: sign-out immediately provisions a fresh
anonymous UID, and a snapshot destined for the *previous* account would otherwise be resumed
against it on the next launch.

`AuthStateObserver.isMerging` is a counter (`activeMergeCount`), not a plain `Bool`, because two
independent call sites raise it concurrently — this resume, and the live merge
`AccountViewModel.onSignInWithAppleTapped` / `onSignInWithGoogleTapped` start via the same
`MergeStatusReporting` protocol — and whichever finishes first must not tear down the overlay while
the other is still running. `KalorieApp` (§ 5.1) shows the merge overlay and disables hit-testing
off this flag.

### 6.3 The account screen and deleting an account

`AccountConfigurator.createView()` wires one `AuthCommandProvider`, one `PendingMergeSnapshotStore`
and one `LinkOrMergeCredentialUseCase` into `SignOutUseCase`, both sign-in use cases,
`DeleteAccountUseCase` and `ReauthenticateUseCase` — the only feature configurator assembling this
many use cases around one shared pair of providers. `AccountViewModel.State` is `idle` / `linking`
/ `deletingAccount`, driving the same `View.loader(_:)` overlay every other feature uses (§ 5.2).

Both sign-in handlers filter user cancellation before it reaches the generic alert path —
`ASAuthorizationError.canceled` for Apple, `GIDSignInError.canceled` for Google — matching design
0002's note that an unfiltered cancellation looks like a spurious failure every time the sheet is
dismissed.

`DeleteAccountUseCase.callAsFunction(skipDataWipe:)` checks `authProvider.lastSignInDate` against
`Constants.Auth.recentLoginThreshold` (4 minutes) **before** touching Firestore, and throws
`DeleteAccountError.requiresRecentLogin(dataAlreadyDeleted: false)` immediately if the session is
stale — this upfront guard is finding **A1-2**, fixed: previously the same failure was discovered
only after `wipeFirestoreData` had already run, by `authCommandProvider.deleteCurrentUser()`
itself throwing `requiresRecentLogin`. That second path still exists as a fallback for the race
where the session goes stale between the check and the call, and is distinguished by
`dataAlreadyDeleted: true` so `AccountViewModel.performDelete` knows whether a retry needs to wipe
Firestore again. The wipe itself deletes `mealTypes`, `foodConsumed`, `favouriteFoods`,
`myCreatedMeals`, `foodItemPortions` and the user's own `foodItemSubmissions` document by document,
then the `users` profile document last — the profile delete
alone is wrapped in `Log.error` rather than rethrown, since a stray profile document left behind is
not the kind of correctness problem an orphaned collection would be.

`AccountViewModel.onReauthenticateConfirmed` is what turns the guard into a usable flow: it shows
an alert naming the reauthenticate button, calls `ReauthenticateUseCase` (§ 6.1) — which resigns in
through whichever provider is already linked — and on success calls `performDelete()` again with
`isDataAlreadyWiped` carried over from the first attempt. Neither design doc describes any of this;
it was added afterward specifically to close the gap the guard above exposes.

### 6.4 What error handling does differently here

This area's `catch` blocks join § 5.2's typed-error-switch category rather than inventing a new
convention: `LinkOrMergeCredentialError.accountExistsWithAnotherProvider` and
`DeleteAccountError.requiresRecentLogin(dataAlreadyDeleted:)` sit alongside `CreateFoodItemError`
and `CreateMealTypeError` as the enums a view model switches over by case rather than collapsing to
`L10n.Common.errorUnknown`.

What is specific to auth is where `Log.warning` / `Log.error` sit inside `try?`-shaped code —
finding **A5-2**, fixed. `SignInWithAppleUseCase.saveProfileIfNeeded` and its Google counterpart
each wrap `updateDisplayName` and the profile `setAsync` in their own `do/catch` that only logs: a
failed profile write must not fail the sign-in it is attached to, but before this fix the failure
was invisible even in Crashlytics. `AuthStateObserver.resumePendingMergeOnce` logs the same way
around `resumeIfNeeded()` — a failed crash-recovery merge must not block app launch, but it must
not vanish silently either.

---

## 7. Catalogue moderation

**Scope:** `Backend` for the claim and the rules it gates; `Cross-platform` for the submission
lifecycle and the log-before-approval behaviour; `iOS` for the panel.

**Read first:** [design 0009](design/0009-catalogue-moderation.md) (the whole flow, the two
identities a submission carries, and the alternatives rejected),
[ADR 0027](adr/0027-catalogue-writes-require-a-maintainer-claim.md) (the claim decision and why it
supersedes [ADR 0011](adr/0011-foodItems-writable-by-any-authenticated-client.md)),
[ADR 0028](adr/0028-foodItemSubmissions-update-rule-validates-the-maintainer-branch.md) (the
maintainer `update` branch narrowed to what the reject path actually writes),
[ADR 0029](adr/0029-submitted-at-guards-foodItemSubmissions-concurrency.md) (`submitted_at` as an
optimistic-concurrency token against a submission edited while under review),
[design 0010](design/0010-nutrition-label-photo-prefill.md) (the camera pre-fill on all three
`FoodItemFormFields` screens — § 7.5), [design 0011](design/0011-food-measure-grams-or-millilitres.md)
(the parser setting a submission's measure from the label it read),
[design 0012](design/0012-report-incorrect-catalogue-data.md) (the report flow — § 7.6 — and why it
is a signal collection, not a second write path into `foodItems`). § 1.2/1.4/1.6 cover the
collection shape and rules; § 2.6 covers where this replaces the old direct-write path.

### 7.1 What exists

Ten submission/moderation use cases plus four report ones, all `FirestoreDataProviderProtocol` +
`AuthProviderProtocol` except `FetchMaintainerClaimUseCase` (§ 7.3, which touches `FirebaseAuth`
directly and takes neither):

| Use case | Does |
|---|---|
| `SubmitFoodItemUseCase` | validates, checks the barcode isn't already in `foodItems`, writes a `pending` submission |
| `FetchMySubmissionsUseCase` | the author's own submissions, for the fourth `displayedResults` list (§ 2.2) |
| `UpdateMySubmissionUseCase` | resubmits a rejected submission under its existing id, resetting `status` to `pending` |
| `DeleteMySubmissionUseCase` | the author withdraws a pending or rejected submission |
| `FetchPendingSubmissionsUseCase` | the maintainer's queue |
| `ApproveSubmissionUseCase` | re-reads the submission to guard against a concurrent edit (ADR 0029), calls `CreateFoodItemUseCase` (§ 2.6), then deletes the submission |
| `RejectSubmissionUseCase` | overwrites the submission with `status: rejected` and a reason — `setAsync` replaces, so every other field is resent unchanged; a denied write is re-read to tell "already resolved" apart from "resubmitted since review" (ADR 0029) |
| `UpdateFoodItemUseCase` | the maintainer's correction of an existing `foodItems` entry; same `FoodItemValidation`, no existence check |
| `FetchMaintainerClaimUseCase` | reads the `maintainer` custom claim via `getIDTokenResult()` |
| `SubmitFoodItemReportUseCase` | validates the reason, writes under the composed `{barcode}_{userId}` id (§ 7.6) |
| `FetchMyFoodItemReportUseCase` | reads the caller's own report for one barcode, by key — the *already reported* check |
| `FetchFoodItemReportsUseCase` | the maintainer's report queue, ordered by `reported_at` |
| `DeleteFoodItemReportUseCase` | the maintainer clears one report, by its composed key, after correcting or dismissing it |

### 7.2 The submission flow (AddFoodSheet)

Nothing about *finding* or *logging* a food changes. What changes is where the *add a new food*
form writes: `AddFoodSheetViewModel.onCreateFoodItem` calls `SubmitFoodItemUseCase` for a fresh
submission, or `UpdateMySubmissionUseCase` when `editingSubmissionId` is set (a resubmit). Both
share `FoodItemFormInput.asFoodItemDomain()` and the same `FoodItemSubmissionError` switch for
alerts. On success the sheet shows a one-line confirmation
(`isSubmissionConfirmationVisible`) before dismissing.

The user's own submissions are the fourth list folded into `displayedResults` (§ 2.2) and — with an
empty search — their own always-visible section, `mySubmissions`, fetched once in `onAppear`
alongside favourites and created meals. `FoodItemRow.submissionStatus` renders the *pending* /
*rejected* marker. Tapping a **pending** row behaves like any other result (logs it, via the
ordinary quantity screen); tapping a **rejected** row calls `onSelectRejectedSubmission`, which
pre-fills `formInput` from `FoodItemFormInput(item:)`, shows the rejection reason as a banner, and
routes the next save through `UpdateMySubmissionUseCase` instead of `SubmitFoodItemUseCase`.

Logging a food that points at a pending or rejected submission needs no special case: `foodConsumed`
already carries denormalised nutrition (§ 1.3) and never reads `foodItems` at write time, so
`food_item_id` = the barcode resolves once — or if rejected, never — without the entry itself ever
being wrong.

### 7.3 The panel (Account)

A `Section` in `AccountView`, rendered only when `AccountViewModel.isMaintainer` is true —
populated in `onAppear()` from `FetchMaintainerClaimUseCase`, a plain `Bool` with no retry. This is
a **UI gate, not a defence**: the rules in § 1.6 are what actually restrict a write, exactly as
design 0009 requires.

`ModerationConfigurator` assembles four pushed (not sheeted) views, all reached through
`NavigationLink`s inside `AccountView`'s existing `NavigationStack`, matching how
`MyCreatedMealEditorView` is pushed from a created meal's quantity screen (its pencil button) rather than sheeted. The queue and the
reports screen (§ 7.6) are two separate, sibling `NavigationLink`s in `AccountView`'s maintainer
section — not one nested inside the other — since a report row means something different from a
submission row and goes somewhere else:

- **`ModerationQueueView`** — `FetchPendingSubmissionsUseCase`'s list, each row concurrently
  checked against `FetchFoodItemByBarcodeUseCase` so an already-collided barcode shows a marker
  before the maintainer opens it (an affordance; `ApproveSubmissionUseCase`'s own existence check
  in `CreateFoodItemUseCase` is the actual guard). A toolbar button pushes the catalogue editor.
- **`ModerationReviewView`** — one submission, every field editable via the same
  `FoodItemFormFields` component the *add a new food* form and the editor use (extracted by this
  work to avoid a third copy of a thirteen-field list), *Approve* and *Reject with a reason*. A
  `CreateFoodItemError.itemAlreadyExists` from approval surfaces as
  `L10n.Moderation.errorAlreadyExists` rather than a generic failure.
- **`ModerationCatalogueEditorView`** — search `foodItems` by barcode
  (`FetchFoodItemByBarcodeUseCase`), edit any field including canonical portions, save via
  `UpdateFoodItemUseCase`. This is the correction path [design 0008](design/0008-food-portions.md)
  deferred here, since canonical portions were write-once purely for lack of an `update` rule. Also
  reachable with a barcode already supplied — see § 7.6.
- **`ModerationReportsView`** (§ 7.6) — the report queue, grouped by barcode.

### 7.4 What this does not do

Per design 0009's Non-goals: no packaging photo (Storage isn't configured), no push notification of
the outcome, no `delete` on `foodItems` for anyone including the maintainer, and no second-platform
admin panel — an Android client ships without one, which is an accepted consequence of hosting it in
the iOS client alone. All three are tracked in `TODO.md`, not here, per this document's own rule
that open work lives in the backlog, not in the description of what exists. The fourth item design
0009 listed here — a report-a-problem channel for an existing catalogue item — is no longer a gap;
see § 7.6.

### 7.5 Pre-filling the form from a photo

[Design 0010](design/0010-nutrition-label-photo-prefill.md), including its *camera-first flow*
revision, drives the same recognition pipeline from a live camera on all three screens
(`AddFoodSheetView`'s new-item tab, `ModerationReviewView`, `ModerationCatalogueEditorView`).
`RecognizeNutritionLabelUseCase` (`Core/UseCases/`) runs a pure-Swift pipeline over
`Core/NutritionLabelRecognition/`: Vision OCR (`VisionTextRecognizer`) and barcode detection produce
`[RecognizedTextLine]`, `NutritionLabelParser` turns the EU-regulation nutrition declaration into a
`NutritionLabelReading` through a multilingual keyword dictionary, tried two ways: row/column
geometry for the table format most labels use, and a keyword-position fallback (bounded to the text
after a nutrition-section cue, when one is found) for the **linear** format small packages are
legally allowed to use instead — one sentence, no table at all. The fallback only runs when the
table pass found none of the five macro fields, so a label with neither format present still
correctly fills nothing. The same pass also sets the reading's `measure` — from the per-100
column header if it names `ml` or `g`, else from the linear fallback's own header test, else from
the package-weight unit, else `nil` — and `FoodItemFormInput.applying(_:)` fills the form's
`measure` from it only while the form is still at its `.grams` default, per the same
still-at-default rule as every other field. See
[design 0011](design/0011-food-measure-grams-or-millilitres.md). Only when `SystemLanguageModel.default.availability == .available` does
`FoundationModelExtractor` fill what neither deterministic pass can (name, package weight,
portions), grounded against the OCR text and never overriding a value either pass already found.

**The new-item tab is camera-first.** `AddFoodSheetMode.newItem` opens on a capture prompt, not a
form — there is no manual-entry path for a brand-new item. The prompt's state comes from
`CameraAuthorizationProviderProtocol.status` (`Core/Camera/`), a three-state-plus-unsupported
`CameraAccess` read from `AVCaptureDevice.authorizationStatus(for: .video)` and
`DataScannerViewController.isSupported`, refreshed on every `scenePhase` → `.active` transition the
same way § 2.5's barcode scanner already re-checks its own permission. Tapping the prompt opens
`NutritionLabelCameraView` (`Components/`): a full-screen `DataScannerViewController` configured for
`.text()` **and** `.barcode()` (`NutritionLabelScannerRepresentable`, distinct from § 2.5's
barcode-only `DataScannerRepresentable` — the two are never reused for each other's job). Its
coordinator converts each live text item's quad to a `RecognizedTextLine` (`LiveTextItemConversion`,
kept free of VisionKit so it is unit-testable against plain `CGPoint`s) and re-parses on every
update; once a parse clears `NutritionLabelReading.isCompleteForAutoCapture` (kcal, fat, carbs and
protein all present) it calls `capturePhoto()` once, the same still-image entry point
`RecognizeNutritionLabelUseCase` already used. A manual shutter triggers the same capture for labels
the auto-capture predicate never locks onto. Nothing is written to the form from a live-only read —
only the captured still goes through the pipeline, which is what makes reusing a live scanner safe
here despite § 2.5's `DataScannerRepresentable` being barcode-only for an unrelated reason (a
multi-frame text read cannot be assembled reliably; see design 0010's *Capture*).

The merge rule is one rule for all three screens, decided in design 0010 rather than left as three
different behaviours: a recognised value is written only into a field still at its default
(`FoodItemFormInput.applying(_:)`). `NutritionLabelPrefilling` (`Core/Utils/`, the same
protocol-extension shape as `FavouriteToggling`, § 4.6) is what lets `AddFoodSheetViewModel`,
`ModerationReviewViewModel` and `ModerationCatalogueEditorViewModel` share this without three
copies, including the "nothing recognised" path: the camera stays open and a hint renders inside
`NutritionLabelCameraView` itself (an `.alert` over a `fullScreenCover` never appears), and a
capture whose `recognizedFields` is empty counts as nothing recognised even when a barcode alone
came back. `AddFoodSheetViewModel` additionally tracks whether a capture succeeded and pushes the
review screen only from the cover's own `onDismiss`, not at capture time — pushing while the cover
is still animating out is dropped by SwiftUI. The fields a photo actually changed are tracked as a
`Set<FoodItemFormField>` and passed to `FoodItemFormSections` (`Components/`, wrapping
`FoodPortionsSection` then a name field, an optional barcode row, the scan button and
`FoodItemFormFields` in that order), which bolds those fields' values
(`BaseStringTextField` / `BaseDoubleTextField` both take an `isHighlighted` flag) until the user
edits them — detected by wrapping each field's `Binding` rather than `.onChange`, since `.onChange`
would also fire for the merge's own programmatic write and immediately clear the mark it had just
set.

No photo is stored and nothing new reaches Firestore — this is a client-side prefill of the same
form `SubmitFoodItemUseCase` / `UpdateMySubmissionUseCase` / `UpdateFoodItemUseCase` already wrote
before this design.

### 7.6 Reporting incorrect data

A user looking at a `.catalogue` item — on `FoodConsumedDetailView` or `FoodQuantityView`, gated by
`canReportIncorrectData` (`food.foodItemKind == .catalogue` / `item.kind == .catalogue`) — can flag
that its data looks wrong. An `.external` (OpenFoodFacts) item or a created meal offers no such
affordance: neither is a `foodItems` entry a report could point the maintainer at.

`FoodItemReporting` (`Core/Utils/`) is the third protocol-extension pair sharing state across two
view models, the same shape as `FavouriteToggling` (§ 4.6) and `NutritionLabelPrefilling` (§ 7.5):
`onAppear()` loads whether the current user already has an open report for this barcode
(`FetchMyFoodItemReportUseCase`, one document read by the composed `{barcode}_{userId}` key — no
query), and the toolbar menu offers *Report incorrect data* or a disabled *Already reported*
accordingly. Submitting (`SubmitFoodItemReportUseCase`) validates the reason is non-empty and under
`Constants.Firestore.reportReasonMaxLength`, then `setAsync`s under that same composed key — a
second report from the same user replaces rather than duplicates, which is what the key shape buys
over `foodItemSubmissions`' UUID (§ 1.2): one open report per user per item, enforced by the rules
having no `update` clause to fall back on rather than by client-side counting.

`ModerationReportsView` / `ModerationReportsViewModel` (§ 7.3) is the maintainer's queue:
`FetchFoodItemReportsUseCase` loads the whole collection ordered by `reported_at`, and the view
model groups the result by barcode client-side — `Dictionary(grouping:by:)` over an already-loaded
list, not a second query — so two different users reporting the same item collapse into one row
showing a count, sorted with the most-reported item first. Names resolve through the same
`FetchFoodItemByBarcodeUseCase.callAsFunction(barcodes:)` batched lookup `ModerationQueueViewModel`
already uses for its own collision check; a barcode that resolves to nothing still renders, as the
bare barcode, since the report is still clearable. Tapping a row pushes
`ModerationCatalogueEditorView` pre-loaded with that barcode (`initialBarcode`, resolved in its own
`onAppear()`) — reporting adds no correction UI of its own, it only routes to the one design 0009
already built. Resolving is a swipe action that calls `DeleteFoodItemReportUseCase` once per report
in the group; a partial failure leaves the rest, the same visible, idempotent shape design 0009
accepted for approval's own two non-transactional writes.

There is no `status` field and no retained "resolved" record — a report is deleted once acted on,
the same reasoning [design 0009](design/0009-catalogue-moderation.md) gave for not retaining
approved submissions. `DeleteAccountUseCase.wipeFirestoreData` queries `foodItemReports where
reported_by == uid` and deletes each, the same privacy obligation `foodItemSubmissions` already
carries; unlike that query, this one needed its own composite index (§ 1.6), since equality on one
field plus a sort on another is never covered by Firestore's automatic single-field indexing.

See [design 0012](design/0012-report-incorrect-catalogue-data.md) for why this is a signal
collection rather than a second write path into `foodItems` — routing a correction through
`foodItemSubmissions` would have meant conditionally lifting `SubmitFoodItemUseCase`'s own
existence check and bypassing `CreateFoodItemUseCase`'s, the exact guard
[ADR 0027](adr/0027-catalogue-writes-require-a-maintainer-claim.md) says must not be removed.
