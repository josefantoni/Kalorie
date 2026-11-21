# Design: Slovak catalogue names

- **Status:** Draft
- **Scope:** Backend, Cross-platform, iOS, Android
- **Date:** 2026-11-21

## Context and scope

Every food carries two names, `cz_name` and `eng_name`. A Slovak device is shown `cz_name`
([ADR 0036](../adr/0036-food-display-name-is-chosen-by-device-language-at-render-time.md)) and
searches it ([ADR 0042](../adr/0042-search-matches-the-displayed-name-only.md)).
[Design 0027](0027-slovak-localisation.md) listed Slovak catalogue names as a non-goal, because a
third name field touches the rules, the import scripts and the search index "for little gain".
ADR 0036 left the choice open: a third name field everywhere, or the third language on the
fallback.

The gain is larger than that estimate. Many everyday food names differ between the two languages:
*mléko* / *mlieko*, *brambory* / *zemiaky*, *rajče* / *paradajka*, *okurka* / *uhorka*, *chléb* /
*chlieb*. A Slovak user who types `mlieko` finds nothing in the catalogue today.

The app can now force an update ([design 0026](0026-minimum-supported-version.md),
`config/app.min_version_ios` / `min_version_android`), so a new field can be required in every
document rather than added as optional.

This document adds a required Slovak name to every document that carries a food name, defines one
rule for filling it, and changes the search so that the number of queries does not grow with the
number of languages.

## Goals

- Every food document carries a non-empty `sk_name`: the catalogue, submissions and every user's
  copy of a food.
- A Slovak device shows and searches `sk_name`.
- One fill rule, **Slovak → Czech → English**, applies at every write that creates a name and to
  the backfill.
- A Slovak or Czech device sends **two** Firestore queries per catalogue search, not four.
- The field is required everywhere from the release that introduces it. Nothing reads it as optional.

## Non-goals

- **Translating the catalogue.** This work only creates the field. Until an item is translated, its
  `sk_name` equals its `cz_name`. Translating is a later task, done through the catalogue editor and
  the `catalogue-import` skill.
- **A Slovak name input for users.** The new-item form, the submission flow and the created-meal
  editor keep their current fields. Only the maintainer's catalogue and moderation editors get a
  Slovak field.
- **Changing the English side.** An English device keeps the `eng_name` → `cz_name` fallback and
  its four queries (see *Search*).
- **Portion names.** These already have Slovak forms
  ([ADR 0048](../adr/0048-portion-names-have-slovak-forms.md)).
- **Slovak store screenshots.** These stay Czech, as design 0027 decided.

## Design

### Data contract

| Where | New fields |
|---|---|
| `foodItems`, `foodItemSubmissions.item` | `sk_name`, `sk_name_folded`, `sk_name_search_terms` |
| `users/{uid}/foodConsumed`, `favouriteFoods`, `stats` (`entries.<id>.item`), `myCreatedMeal` ingredients | `sk_name` |

- `sk_name` is a non-empty string everywhere. `sk_name_folded` and `sk_name_search_terms` are
  derived exactly as their `cz_` / `eng_` counterparts (TextKit fold, every prefix of every word,
  [ADR 0024](../adr/0024-token-array-field-for-whole-word-search.md)).
- No `sk_name_lowercase`. The `*_lowercase` fields are read by no query
  ([ADR 0040](../adr/0040-search-is-diacritic-insensitive-everywhere-and-drops-the-lowercase-queries.md)),
  so a new one would only add weight.
- `validFoodItem()` in `backend/firestore.rules` requires `sk_name is string && sk_name.size() > 0`,
  `sk_name_folded is string` and `sk_name_search_terms is list`. The documents under `users/{uid}`
  are not validated field by field by the rules. Their DTOs require the field instead.
- The DTOs on both platforms declare `sk_name` as non-optional: `FoodItemDTO`, `FoodConsumedDTO`,
  `FavouriteFoodDTO`, `FoodFrequencyDTO` and `MyCreatedMealDTO` (the ingredient). The domain models
  carry `skName` alongside `czName` and `engName`.

### The fill rule

`sk_name` is the first non-empty value of: **a Slovak source → `cz_name` → `eng_name`**. An empty
or whitespace-only value is skipped. The rule lives in `TextKit` as one function, so both clients
and the JS backfill (through its shared fixture,
[ADR 0026](../adr/0026-js-backfill-duplicates-textkit-under-a-shared-fixture.md) /
[ADR 0039](../adr/0039-swift-only-rules-move-into-kmp-or-share-golden-vectors.md)) use the same rule.

What counts as the Slovak source depends on the write:

| Write | Slovak source | Result |
|---|---|---|
| OpenFoodFacts mapping | `product_name_sk` | `product_name_sk` → `product_name_cs` → `product_name_en` → `product_name` |
| New item / user submission | none | `cz_name` (required by `FoodItemValidation`) |
| Created meal logged as one entry | none | the meal name, which is already its `cz_name` |
| Maintainer catalogue / moderation editor | the Slovak field, prefilled by the rule | the field, or the rule when it is left empty |
| Copy into user data (consumed, favourite, frequency, created-meal ingredient) | — | copied from the source food, never recomputed |
| Backfill | none | `cz_name`, or `eng_name` when `cz_name` is empty |

OpenFoodFacts:

- `product_name_sk` is added to `fields` in both calls (search and barcode), on both platforms
  (`Constants.OpenFoodFacts.productFields` and its Android counterpart).
- The Slovak name goes through `decodingHTMLEntities()` like the other two names.
- `fixtures/open-food-facts-mapping-cases.json` gains cases for each step of the Slovak chain.

The `cz_name` and `eng_name` preferences are unchanged.

A stored `sk_name` equal to `cz_name` is indistinguishable from a real translation that happens to
be identical (*jablko*, *hruška*). That is accepted. See *Risks*.

### Display

`displayName` becomes:

1. `cs`: `cz_name`, or `eng_name` when `cz_name` is empty.
2. `sk`: `sk_name`.
3. Otherwise: `eng_name`, or `cz_name` when `eng_name` is empty.

"Language" keeps ADR 0036's definition: the primary language subtag of the resolved app language.
The choice is still made at render time. The rule moves into `TextKit` as a pure function of the
language code and the three names, and `BilingualNamed` on both platforms calls it. ADR 0036 already
names this as the way to share the rule. ExportKit receives names that are already displayed, so it
does not change.

### Search

Since ADR 0042, every result whose displayed name does not match the query is dropped. A query on
another language's fields therefore only serves the fallback, for when the displayed name is empty.
The rule becomes:

> Query the folded-prefix field and the search-terms field of the language the name is displayed
> in. Add the fallback language's pair only when the displayed name can be empty.

| Device language | Queries, in merge order | Count |
|---|---|---|
| `sk` | `sk_name_folded` → `sk_name_search_terms` | 2 |
| `cs` | `cz_name_folded` → `cz_name_search_terms` | 2 |
| other | `cz_name_folded` → `eng_name_folded` → `cz_name_search_terms` → `eng_name_search_terms` (unchanged) | 4 |

- `sk_name` is never empty by contract, and `cz_name` is never empty because `FoodItemValidation`
  rejects it. `eng_name` may be empty, so English keeps the fallback pair and today's order.
- A further language with a required name adds no query. It gets its own two.
- Each query keeps `limit(10)`, de-duplication keeps "first occurrence wins", and the
  `matchesSearchQuery` filter on `displayName` stays as it is.
- The in-memory matching of favourites, own submissions and frequency candidates already goes
  through `displayName` and follows on its own.
- Both new fields use single-field indexes, as the existing `cz_` / `eng_` ones do.

### Diacritic folding

`foldDiacritics` gains the Slovak letters missing from the Czech map: `ä→a`, `ô→o`, `ĺ→l`, `ľ→l`,
`ŕ→r`, plus their uppercase forms. The shared fixture and its JS duplicate change with it. The
statement in `ARCHITECTURE.md` § 2.2 that Slovak characters pass through unfolded "by design" no
longer holds and gets updated with the implementation. Existing `cz_` / `eng_` folded values change
only where one of these letters occurs, and the backfill recomputes them anyway.

### Backend

- `backend/firestore.rules`: `validFoodItem()` as above.
- `backend/functions/propagate.js`: `NAME_FIELDS` gains `sk_name`, so a translated catalogue name
  reaches `foodConsumed`, `favouriteFoods` and `stats` entries
  ([ADR 0049](../adr/0049-catalogue-names-and-photos-propagate-into-users-copies.md)). Its tests
  cover the new field.

### Scripts and tooling

- A new backfill script:
  - catalogue (`foodItems`, `foodItemSubmissions.item`): writes `sk_name`, `sk_name_folded` and
    `sk_name_search_terms`, and recomputes the `cz_` / `eng_` folded and search-terms fields under
    the extended fold map;
  - user data (collection groups `foodConsumed`, `favouriteFoods`, `stats` entries and
    `myCreatedMeal` ingredients): writes `sk_name` by the fill rule from the document's own names,
    not from the catalogue.

  `scripts/lib/backfill-runner.js` reads a single top-level collection today. It needs a
  collection-group mode and a way to update nested items (map entries, ingredient arrays). The
  script is idempotent and reports catalogue items whose `cz_name` is empty.
- `scripts/backfill-name-folding.js` and `backfill-search-terms.js` learn the `sk_` fields.
- The `catalogue-import` skill writes the three Slovak fields. When it has no Slovak source, it
  applies the fill rule.

### Clients

Both platforms change the same places:

- the DTOs and domain models listed above;
- the OpenFoodFacts DTO, `fields` constant and mapping;
- `BilingualNamed` / `displayName`;
- `SearchFoodItemsUseCase`;
- `SaveFoodConsumedUseCase`, plus the favourite, frequency and created-meal writers, which copy
  `sk_name`;
- the moderation review and catalogue editors, which get a Slovak name field.

The user-facing forms do not change.

### Rollout

The field is required from release N. Old clients ignore it on read (iOS `Codable`, Android
`FirestoreDataMapper` with `ignoreUnknownKeys = true`), but they drop it on a full overwrite
(`setAsync` in `UpdateFoodItemUseCase`, `CreateFoodItemUseCase` and `RefreshFavouriteFoodUseCase`),
and the documents they create lack it. The rollout order is:

1. Deploy `propagate.js` with `sk_name`. It is harmless before the field exists.
2. **Backfill, first run.** This covers nearly all existing documents.
3. Release N on both stores. It decodes `sk_name` as required.
4. As soon as N is live in both stores, raise `config/app.min_version_ios` and
   `min_version_android` to N. From then on, old clients stop writing.
5. **Backfill, second run.** It catches what old clients wrote or overwrote between steps 2 and 4.
6. Deploy the rules that require the Slovak fields.

Steps 3 to 5 should follow each other on the same day. Between them, an N client can meet a
document that lacks `sk_name` (see *Risks*).

### Implementation map

The design is reached through these places. Line numbers are as of the commit that adds this document.

**TextKit (KMP)**, built first because both clients depend on it:

- `TextKit/src/commonMain/kotlin/DiacriticFolding.kt:21`: `foldDiacritics` gains the Slovak letters.
- A new function holds the fill rule, `slovakName(slovak, czech, english)`: the first value that
  is not blank.
- A new function holds the display rule, `displayName(languageCode, czech, slovak, english)`.
- Shared fixture `TextKit/fixtures/text-kit-cases.json`, read by
  `TextKit/src/jvmTest/kotlin/TextKitFixtureTest.kt`: new `foldDiacritics` cases for
  `ä ô ĺ ľ ŕ`, and new sections for the two functions.
- JS duplicate: `scripts/lib/diacritics.js` and its `diacritics.test.js` read the same fixture
  (ADR 0026).
- After the change, run `scripts/build-kmp-framework.sh` for iOS.

**iOS**, followed by Android with the same names (`Android/app/src/main/java/antoni/kalorie/…`):

| Area | iOS | Android |
|---|---|---|
| Display rule | `Core/Models/BilingualNamed.swift` (`isCzechOrSlovak` is also used by `FoodPortionModel.swift`, so keep it) | `core/models/BilingualNamed.kt` |
| Catalogue DTO | `Core/Networking/FireStone/FoodItemDTO.swift:16-23, 45-52, 67-74` | `core/networking/FoodItemDTO.kt:21-22, 47-54` |
| Copy DTOs | `FoodConsumedDTO.swift`, `FavouriteFoodDTO.swift`, `FoodFrequencyDTO.swift`, `MyCreatedMealDTO.swift` (ingredient, not the meal's own `name`) | same file names in `core/networking/` |
| Domain models | `Core/Models/FoodItemModel.swift`, `FoodConsumedModel.swift`, `MyCreatedMealModel.swift` | `core/models/` same names |
| Field constants | `Core/Utils/Constants.swift:161-164` (Field), `:46` (`productFields`) | `core/utils/Constants.kt:160-163`, `:94` |
| OpenFoodFacts | `Core/Networking/OpenFoodFacts/OpenFoodFactsProductDTO.swift:47, 75-76` | `core/networking/OpenFoodFactsProductDTO.kt:62-63` |
| Search | `Core/UseCases/SearchFoodItemsUseCase.swift:34-65` | `core/usecases/SearchFoodItemsUseCase.kt:31-39` |
| Writing an entry | `Core/UseCases/SaveFoodConsumedUseCase.swift:37-38` | `core/usecases/SaveFoodConsumedUseCase.kt:33-34` |
| Created meal ingredients | `Features/MyCreatedMeal/MyCreatedMealEditorViewModel.swift:113, 159, 302` | `features/mycreatedmeal/MyCreatedMealEditorViewModel.kt:67, 159, 308` |
| Food form | `Features/AddFoodSheet/AddFoodSheetViewModel.swift:18, 40, 66`; `Components/FoodItemFormFields.swift`, `FoodItemFormSections.swift` | `features/addfoodsheet/FoodItemFormInput.kt:17, 40, 115`; `components/FoodItemFormSections.kt` |
| Maintainer editors | `Features/Moderation/ModerationCatalogueEditorViewModel.swift`, `ModerationReviewViewModel.swift` | `features/moderation/` same names |

Gotchas:

- **The food form is shared.** The user's new-item form and both maintainer editors use the same
  `FoodItemFormSections` and `FoodItemFormInput`. Add the Slovak field behind one parameter of the
  shared component, not as a copy of the form for the maintainer.
- **`NutritionLabelPrefilling`** (`Core/Utils/NutritionLabelPrefilling.swift`, Android
  `core/utils/NutritionLabelPrefilling.kt`) builds a `FoodItemFormInput`. It has to compile with
  the new field and leaves it empty.
- **Previews and fixtures.** Every `FoodItemDomain` / `FoodConsumedDomain` initialiser call gets
  a new argument: SwiftUI previews (`FoodItemRow.swift`, `FoodConsumedView.swift`,
  `DashboardView.swift`, `FoodConsumedDetailView.swift`, `FoodQuantityView.swift`,
  `FoodPortionsManagerView.swift`, `ModerationReviewView.swift`) and about 40 test files on each
  platform (`git grep -l czName -- iOS/KalorieTests Android/app/src/test`). Pass `skName` explicitly
  in tests. Do not give the initialiser a default, because a default would hide a writer that forgets it.
- **Created meals are matched by `czName`** (`AddFoodSheetViewModel.swift:258`, Android
  `AddFoodSheetViewModel.kt:136`). Leave that unchanged: the meal name is its `cz_name` and its
  `sk_name`.
- **`similarCatalogueItems`** (`ModerationReviewViewModel.swift:70`) searches by
  `submission.item.czName`. Leave it unchanged.
- **New search tests**, on both platforms:
  - an `sk` device queries only the two `sk_` fields;
  - a `cs` device queries only the two `cz_` fields;
  - an English device queries the four fields in today's order.

  A test that only counts results cannot fail when the field choice is wrong. Assert on the
  queried fields through the data provider fake.
- **`fixtures/open-food-facts-mapping-cases.json`** gains `product_name_sk` input and an
  expected Slovak name in every case, plus cases for `sk` present, `sk` empty → `cs`, and `sk`/`cs`
  empty → `en`.

**Backend and scripts:**

- `backend/firestore.rules:18-46` (`validFoodItem`), with tests in `firestore-rules-tests/firestore.rules.test.js`.
  Deploy **last** (rollout step 6).
- `backend/functions/propagate.js:3` (`NAME_FIELDS`), with tests in `backend/functions/test/propagate.test.js`.
- `scripts/lib/backfill-runner.js`: today it reads `db.collection(collectionName)` only. Add the
  collection-group mode for user data. `scripts/backfill-name-folding.js` and
  `backfill-search-terms.js` add the `sk_` fields.
- `.claude/skills/catalogue-import/SKILL.md:21`: the required fields gain the `sk_` trio.

**Not verified by any test, check by hand before release:**

- what each platform does when one document in a list fails to decode (rollout risk);
- the backfill dry run's write count and the number of `propagate` invocations it causes.

Implement in this order:

1. TextKit and fixtures.
2. iOS.
3. Android.
4. `propagate.js`.
5. Scripts.
6. Rules.

Each is its own commit, by the monorepo rule. The Firestore contract change touches both clients,
so build both before merging.

## Alternatives considered

- **Leave it a non-goal (design 0027).** Rejected: Czech and Slovak food names differ often enough
  that a Slovak user cannot find common foods by their own word.
- **Optional `sk_name`, falling back to `cz_name` on read.** Rejected: the app can force an update,
  so a required field is reachable, and a required field is what lets search drop the fallback
  queries. An optional field would keep a fallback in every reader on both platforms.
- **Leave `sk_name` empty until translated and fall back at render time.** Rejected: an empty
  displayed name brings back the fallback queries, so a Slovak device would send four or six, and
  each further language would add two more.
- **Query every language's fields (six queries).** Rejected: ADR 0042 drops every result that does
  not match the displayed name, so the other languages' queries would only be fetched to be
  thrown away.
- **One `names` map (`{cs, sk, en}`) instead of flat fields.** It scales better to more languages,
  but renames every existing name field in every collection, in the rules, scripts, DTOs and
  propagation. Flat fields fit the existing schema and add one language at a time.

## Cross-cutting concerns

- **Migration of existing data:** the two backfill runs above, covering the catalogue and every
  user's copies. Both are idempotent.
- **Cost:** the backfill reads and writes every user document that carries a name once per run.
  Each backfilled `foodItems` document also fires `propagate`, which scans `stats` across all users
  ([ADR 0049](../adr/0049-catalogue-names-and-photos-propagate-into-users-copies.md)). See *Risks*.
- **Privacy:** no new personal data.
- **Store requirements:** the forced-update screen of design 0026 is already accepted practice.
  Nothing new.

## Risks

- **An N client meets a document without `sk_name`** (between steps 3 and 5) → that load can fail
  → keep the window short by running steps 4 and 5 immediately after the release becomes
  available, and check before release how one undecodable document affects a list on each platform.
- **Backfill triggers `propagate` once per catalogue item**, and each run scans `stats` → cost
  grows with catalogue size × users → measure on a dry run first. If it is too high, undeploy the
  trigger for the duration of the backfill, since the backfill writes the copies itself.
- **A catalogue item with an empty `cz_name` despite validation** → a Czech device's two-query
  search would miss it → the backfill reports such items, and they are fixed before rollout.
- **Untranslated items cannot be told apart from translated ones** (`sk_name == cz_name`) → no list
  of what still needs translating → accepted. A translation pass works through the catalogue in
  order, not from a diff.
- **Translation quality** → the author is not a native speaker → a native review pass, as design
  0027 already requires for the UI texts.

## Outcome

_Filled in once implemented._
