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

A second problem sits in the submission flow. The new-item form has one name field, and its value
is always stored as `cz_name`. A user on a Slovak or English device who types a name in their own
language has it stored as Czech.

The app can now force an update ([design 0026](0026-minimum-supported-version.md),
`config/app.min_version_ios` / `min_version_android`), so a new field can be required in every
document rather than added as optional.

This document adds a Slovak name to every document that carries a food name, stores a user's name
in the field of the language it was written in, makes the maintainer complete all three names on
approval, and reduces every catalogue search to two queries.

## Goals

- Every `foodItems` document carries three non-empty names: `cz_name`, `sk_name`, `eng_name`.
- A submission stores the user's name in the field of the device language and records that language.
  The other two name fields stay empty until approval.
- On approval, the maintainer can fill all three names. Every name left empty is filled with the
  value of the submission's original-language field, as the maintainer left it.
- A device shows the name in its language, and falls back to a related language only where a name
  can be empty (a pending submission and the user's copies of it).
- Every device sends **two** Firestore queries per catalogue search, whatever its language.

## Non-goals

- **Translating the existing catalogue.** The backfill copies `cz_name` into `sk_name`. Translating
  is a later task, done through the catalogue editor and the `catalogue-import` skill.
- **More than one name input for users.** The new-item form and the submission edit keep one name
  field. Only the maintainer's editors show three.
- **Portion names.** These already have Slovak forms
  ([ADR 0048](../adr/0048-portion-names-have-slovak-forms.md)).
- **Slovak store screenshots.** These stay Czech, as design 0027 decided.

## Design

### Language and field mapping

"Language" keeps ADR 0036's definition: the primary language subtag of the resolved app language.
It maps to a name field as follows:

| Language | Name field | `name_language` value |
|---|---|---|
| `cs` | `cz_name` | `cs` |
| `sk` | `sk_name` | `sk` |
| anything else | `eng_name` | `en` |

### Data contract

| Where | Names | New fields |
|---|---|---|
| `foodItems` | all three non-empty | `sk_name`, `sk_name_folded`, `sk_name_search_terms` |
| `foodItemSubmissions` | the original-language name non-empty, the other two may be `""` | `name_language` (top level), `item.sk_name`, `item.sk_name_folded`, `item.sk_name_search_terms` |
| `users/{uid}/foodConsumed`, `favouriteFoods`, `stats` (`entries.<id>.item`), `myCreatedMeal` ingredients | at least one non-empty, copied from the source | `sk_name` |

- `sk_name_folded` and `sk_name_search_terms` are derived exactly as their `cz_` / `eng_`
  counterparts (TextKit fold, every prefix of every word,
  [ADR 0024](../adr/0024-token-array-field-for-whole-word-search.md)). An empty name gives `""`
  and `[]`.
- No `sk_name_lowercase`. The `*_lowercase` fields are read by no query
  ([ADR 0040](../adr/0040-search-is-diacritic-insensitive-everywhere-and-drops-the-lowercase-queries.md)),
  so a new one would only add weight.
- `name_language` lives on the submission, not on the item. Once approved, the catalogue item has
  all three names and no original language.
- The DTOs on both platforms declare `sk_name` as a non-optional string: `FoodItemDTO`,
  `FoodConsumedDTO`, `FavouriteFoodDTO`, `FoodFrequencyDTO` and `MyCreatedMealDTO` (the ingredient).
  The submission DTO declares `name_language` as non-optional. The domain models carry `skName`
  alongside `czName` and `engName`, and `FoodItemSubmissionDomain` carries `nameLanguage`.
- Every name is trimmed before it is stored. "Empty" means blank (empty or whitespace only) in every
  rule below.

### Firestore rules

`validFoodItem()` in `backend/firestore.rules` checks the shape only: the three names, their
folded and search-terms fields, all of the right type. Two predicates sit on top of it:

- `completeNames(item)`: `cz_name`, `sk_name` and `eng_name` all have `size() > 0`. Required on
  every `foodItems` create and update.
- `originalNameFilled(submission)`: `name_language in ['cs', 'sk', 'en']` and the item's name in
  that language has `size() > 0`. Required on a submission create and on both update branches
  (maintainer reject, owner edit). `name_language` cannot change on update.

The documents under `users/{uid}` are not validated field by field by the rules. Their DTOs
require the field instead.

### Writes

| Write | `cz_name` | `sk_name` | `eng_name` |
|---|---|---|---|
| User submission (new-item form) | the typed name if the device is `cs`, else `""` | the typed name if `sk`, else `""` | the typed name otherwise, else `""` |
| User edits own submission | as above, but by the submission's `name_language`, not the current device language | ← | ← |
| Maintainer approves a submission | the field, or the original-language field when left empty | ← | ← |
| Maintainer catalogue editor | required | required | required |
| OpenFoodFacts mapping | `product_name_cs` → `_en` → `product_name` | `product_name_sk` → `cz_name` | `product_name_en` → `product_name` → `cz_name` |
| Created meal logged as one entry | the meal name if the device is `cs`, else `""` | the meal name if `sk`, else `""` | the meal name otherwise, else `""` |
| Copy into user data (consumed, favourite, frequency, created-meal ingredient) | copied from the source food, never recomputed | ← | ← |
| Backfill, catalogue | unchanged | `cz_name` | unchanged |
| Backfill, user data | unchanged | the document's `cz_name`, or `eng_name` when `cz_name` is empty | unchanged |

Notes:

- **Approval fill.** The maintainer's review editor opens with the submission's names, three
  fields, the original-language field marked as the original. On approve, `completeNames(main,
  czech, slovak, english)` in TextKit fills every empty name with the value of the `main` field.
  The value is the field as edited, so a typo fixed in the original field is what gets copied.
  Validation requires only the original field to be non-empty.
- **Catalogue editor.** A catalogue item already has three names, so an empty field means the
  maintainer cleared it. Validation rejects it with the existing invalid-name error. No fill happens there.
- **Submission edit.** The user's edit form shows the original-language name in its one field and
  writes it back to the same field, so editing on a device in another language cannot move or
  duplicate the name.
- **OpenFoodFacts.** All three names are always non-empty. `product_name_sk` is added to `fields`
  in both calls (search and barcode), on both platforms (`Constants.OpenFoodFacts.productFields`
  and its Android counterpart), and goes through `decodingHTMLEntities()` like the other names.
  `fixtures/open-food-facts-mapping-cases.json` covers each step of the Slovak chain.
- **Existing catalogue.** As of 2026-11-21 all 776 `foodItems` documents have a non-empty `cz_name`
  and `eng_name`, and there are no pending submissions. The backfill still lists any item whose
  `cz_name` or `eng_name` is blank, and such an item is fixed before the rules are deployed.
- A backfilled `sk_name` equal to `cz_name` is indistinguishable from a real translation that
  happens to be identical (*jablko*, *hruška*). That is accepted. See *Risks*.

### Display

`displayName(languageCode, czech, slovak, english)` in TextKit returns the first non-blank name in
this order:

| Language | Order |
|---|---|
| `cs` | `cz_name` → `sk_name` → `eng_name` |
| `sk` | `sk_name` → `cz_name` → `eng_name` |
| anything else | `eng_name` → `cz_name` → `sk_name` |

The related language comes before English, because a Czech reader understands Slovak and the other
way round better than English.

A catalogue item has all three names, so it always shows its own language. The fallback serves
only documents that can have empty names: a pending submission, the user's copies of it
(`foodConsumed`, favourites, frequency), and a logged created meal. Copies of a submission keep the
submitted name after approval, because approval creates the catalogue item and
[ADR 0049](../adr/0049-catalogue-names-and-photos-propagate-into-users-copies.md) propagates only
updates.

The choice is still made at render time. `BilingualNamed` on both platforms calls the TextKit
function, as ADR 0036 names as the way to share the rule. ExportKit receives names that are already
displayed, so it does not change.

### Search

Since ADR 0042, every result whose displayed name does not match the query is dropped. Because a
catalogue item always shows the name of the device language, the search queries that field only:

| Language | Queries, in merge order | Count |
|---|---|---|
| `cs` | `cz_name_folded` → `cz_name_search_terms` | 2 |
| `sk` | `sk_name_folded` → `sk_name_search_terms` | 2 |
| anything else | `eng_name_folded` → `eng_name_search_terms` | 2 |

- A further language with a required name adds no query. It gets its own two.
- The moderation duplicate check is the one caller that overrides the language: it searches the
  submission's `name_language`, not the maintainer's device language (see *Implementation map*).
- Each query keeps `limit(10)`, de-duplication keeps "first occurrence wins", and the
  `matchesSearchQuery` filter on `displayName` stays as it is.
- The in-memory matching of favourites, own submissions and frequency candidates goes through
  `displayName` and so follows the fallback on its own.
- Both new fields use single-field indexes, as the existing `cz_` / `eng_` ones do.

### Diacritic folding

`foldDiacritics` gains the Slovak letters missing from the Czech map: `ä→a`, `ô→o`, `ĺ→l`, `ľ→l`,
`ŕ→r`, plus their uppercase forms. The shared fixture and its JS duplicate change with it. Existing
`cz_` / `eng_` folded values change only where one of these letters occurs, and the backfill
recomputes them anyway.

### Backend

- `backend/firestore.rules`: as in *Firestore rules*.
- `backend/functions/propagate.js`: `NAME_FIELDS` gains `sk_name`, so a translated catalogue name
  reaches `foodConsumed`, `favouriteFoods` and `stats` entries (ADR 0049).

### Scripts and tooling

- A backfill script:
  - catalogue (`foodItems`): writes `sk_name` = `cz_name`, `sk_name_folded` and
    `sk_name_search_terms`, recomputes the `cz_` / `eng_` folded and search-terms fields under the
    extended fold map, and lists every item with a blank `cz_name` or `eng_name`;
  - submissions (`foodItemSubmissions`): writes `name_language` = `cs` and the Slovak trio as
    `""` / `[]`. A submission created before this design always has its name in `cz_name`;
  - user data (collection groups `foodConsumed`, `favouriteFoods`, `stats` entries and
    `myCreatedMeal` ingredients): writes `sk_name` from the document's own names, not from the
    catalogue.

  The script is idempotent. `scripts/lib/backfill-runner.js` provides a collection-group mode and
  updates of nested items (map entries, ingredient arrays).
- `scripts/backfill-name-folding.js` and `backfill-search-terms.js` know the `sk_` fields.
- The `catalogue-import` skill writes all three names and the `sk_` trio. When it has no Slovak
  source, it copies `cz_name`.

### Clients

Both platforms change the same places:

- the DTOs and domain models listed above, plus `name_language` on the submission;
- the OpenFoodFacts DTO, `fields` constant and mapping;
- `BilingualNamed` / `displayName`;
- `SearchFoodItemsUseCase`;
- the new-item form's mapping to a domain item, which writes the one name into the field of the
  device language, and the submission writers, which record `name_language`;
- `FoodItemValidation`, which checks the original-language name (submission) or all three
  (catalogue editor), blank-aware;
- the moderation review editor (three fields, original marked, approval fill) and the catalogue
  editor (three required fields);
- `SaveFoodConsumedUseCase`, plus the favourite, frequency and created-meal writers, which copy
  `sk_name`.

### Rollout

Old clients ignore unknown fields on read (iOS `Codable`, Android `FirestoreDataMapper` with
`ignoreUnknownKeys = true`), but they drop them on a full overwrite (`setAsync` in
`UpdateFoodItemUseCase`, `CreateFoodItemUseCase` and `RefreshFavouriteFoodUseCase`), and the
documents they create lack them. The rollout order is:

1. Deploy `propagate.js` with `sk_name`. It is harmless before the field exists.
2. **Backfill, first run.** This covers nearly all existing documents.
3. Release N on both stores. It decodes `sk_name` and `name_language` as required.
4. As soon as N is live in both stores, raise `config/app.min_version_ios` and
   `min_version_android` to N. From then on, old clients stop writing.
5. **Backfill, second run.** It catches what old clients wrote or overwrote between steps 2 and 4.
6. Deploy the rules.

Steps 3 to 5 should follow each other on the same day. Between them, an N client can meet a
document that lacks `sk_name` (see *Risks*).

### Implementation map

The branch `feat/slovak-catalogue-names` already implements an earlier revision of this document,
in which `sk_name` was always filled by the chain Slovak → Czech → English, the user's name was
always stored as `cz_name`, the maintainer editors had a Czech and a Slovak field only, and English
kept four queries. The places below have to change from that state.

**TextKit (KMP)**, `TextKit/src/commonMain/kotlin/NameRules.kt`:

- `slovakName(slovak, czech, english)` stays: it is the OpenFoodFacts chain and the user-data
  backfill rule (first non-blank of Slovak → Czech → English).
- `completeNames(nameLanguage, czech, slovak, english)` returns a `FoodNames` with every blank name
  filled from the original-language one. `originalName` reads that one, and `namesInLanguage`
  puts a single name into its field and leaves the other two empty.
- `nameLanguage(languageCode)` maps a device language to `cs` / `sk` / `en`. The form, the search,
  the submission writer and the created-meal entry all use it, so they cannot disagree.
- `displayName` implements the table in *Display*, blank-aware (`isBlank`, not `isEmpty`).
- `TextKit/fixtures/text-kit-cases.json` covers every row of both tables, including a blank
  (whitespace-only) name. JS duplicate: `scripts/lib/name-rules.js` keeps only `slovakName`, the
  one rule the backfill needs.
- `fixtures/food-item-validation-cases.json` carries all three names in its base item, and a
  whitespace-only name is now invalid, matching the trim on write.
- After the change, run `scripts/build-kmp-framework.sh` for iOS.

**iOS**, followed by Android with the same names (`Android/app/src/main/java/antoni/kalorie/…`):

| Area | iOS | Android |
|---|---|---|
| Form mapping | `Features/AddFoodSheet/AddFoodSheetViewModel.swift`, `asFoodItemDomain` (`czName: name` today) | `features/addfoodsheet/FoodItemFormInput.kt`, its domain mapping |
| Form component | `Components/FoodItemFormSections.swift`: `showsSlovakNameField` becomes a three-name maintainer mode | `components/FoodItemFormSections.kt` |
| Validation | `Core/Models/FoodItemValidation.swift` (`czName.isEmpty` today) | `core/models/FoodItemValidation.kt` |
| Submission model / DTO | `Core/Models/FoodItemSubmissionModel.swift` and its DTO | `core/models/FoodItemSubmissionModel.kt`, `FoodItemSubmissionWriter.kt` |
| Submission writers | `Core/UseCases/SubmitFoodItemUseCase.swift`, `UpdateMySubmissionUseCase.swift` | same names in `core/usecases/` |
| Approval | `Features/Moderation/ModerationReviewViewModel.swift`, `Core/UseCases/ApproveSubmissionUseCase.swift` | same names |
| Catalogue editor | `Features/Moderation/ModerationCatalogueEditorViewModel.swift` / `View.swift` | same names |
| Created meal entry | `Core/Models/MyCreatedMealModel.swift`, the `FoodItemDomain` built with `czName: name, skName: name, engName: ""` | `core/models/MyCreatedMealModel.kt` |
| Search | `Core/UseCases/SearchFoodItemsUseCase.swift`, `searchFields(for:)` (four fields for English today) | `core/usecases/SearchFoodItemsUseCase.kt` |
| Display | `Core/Models/BilingualNamed.swift` (keep `isCzechOrSlovak`, `FoodPortionModel.swift` uses it) | `core/models/BilingualNamed.kt` |
| Strings | `localisation/strings.json`: titles for the Czech and English name fields and the "original" marker in the maintainer mode | ← |

Gotchas:

- **The food form is shared.** The user's new-item form and both maintainer editors use the same
  `FoodItemFormSections` and `FoodItemFormInput`. Add the maintainer mode behind one parameter of
  the shared component, not as a copy of the form.
- **The form's `name` field stays the user's one field.** In the user mode it maps to the field of
  the device language (new item) or of `name_language` (edit). In the maintainer mode the form has
  three explicit fields; do not route them through `name`.
- **Created meals are matched by `czName`** (`AddFoodSheetViewModel.swift`, Android
  `AddFoodSheetViewModel.kt`). With the meal name in the device-language field, that match must use
  `displayName` instead, or a meal logged on a Slovak or English device is not found.
- **`similarCatalogueItems`** (`ModerationReviewViewModel.swift`) searches by
  `submission.item.czName`, which is empty for a Slovak or English submission. It searches by the
  original-language name instead. It also passes
  `submission.nameLanguage` to `SearchFoodItemsUseCase` (an optional `languageCode` that defaults to the
  device language), so the query hits that language's `*_name_folded` / `*_name_search_terms` fields and
  the `displayName` filter uses the same language. Otherwise a Slovak submission `Zemiaky` reviewed on a
  `cs` device would query `cz_name_*`, find nothing and let a duplicate of the catalogue item
  `{cz: Brambory, sk: Zemiaky}` through.
- **`NutritionLabelPrefilling`** builds a `FoodItemFormInput` and leaves the names empty.
- **Previews and fixtures.** Every `FoodItemDomain` / `FoodConsumedDomain` initialiser call passes
  `skName` explicitly. Do not give the initialiser a default, because a default would hide a writer
  that forgets it.
- **Tests that must fail when the field choice is wrong**, on both platforms:
  - search: each language queries exactly its own two fields, asserted on the queried fields
    through the data provider fake, not on the result count;
  - form: a `sk` device stores the typed name in `sk_name` only, an `en` device in `eng_name` only;
  - submission edit on a device in another language writes back to the `name_language` field;
  - approval: a blank Slovak and English field get the edited original value, a filled field is
    kept;
  - display: every row of the *Display* table, including a whitespace-only name.

**Backend and scripts:**

- `backend/firestore.rules` (`validFoodItem`, the `foodItems` and `foodItemSubmissions` matches),
  with tests in `firestore-rules-tests/firestore.rules.test.js` for each predicate. Deploy **last**
  (rollout step 6).
- `backend/functions/propagate.js` (`NAME_FIELDS`), with tests in
  `backend/functions/test/propagate.test.js`.
- `scripts/lib/slovak-name-backfill.js` and `scripts/backfill-slovak-names.js`: the submission
  pass (`name_language`) and the blank-name report for `eng_name`.
- `.claude/skills/catalogue-import/SKILL.md`: the required fields gain the `sk_` trio.

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
- **Fill every empty name at submission time** (Slovak → Czech → English), so even a submission has
  three names. Rejected: a Slovak name typed on a Slovak device would be stored as Czech too, and
  the maintainer could not tell which field holds the original.
- **Allow empty names in the catalogue and fall back at render time.** Rejected: an empty displayed
  name forces the search to query the fallback fields too, so every language would send four or six
  queries, and each further language would add two more.
- **Optional `sk_name`, falling back to `cz_name` on read.** Rejected: the app can force an update,
  so a required field is reachable, and only a complete catalogue lets the search use two queries.
- **One `names` map (`{cs, sk, en}`) instead of flat fields.** It scales better to more languages,
  but renames every existing name field in every collection, in the rules, scripts, DTOs and
  propagation. Flat fields fit the existing schema and add one language at a time.

## Cross-cutting concerns

- **Migration of existing data:** the two backfill runs above, covering the catalogue, the
  submissions and every user's copies. Both are idempotent.
- **Cost:** the backfill reads and writes every user document that carries a name once per run.
  Each backfilled `foodItems` document also fires `propagate`, which scans `stats` across all users
  (ADR 0049). See *Risks*. Search drops from four queries to two on English devices.
- **Privacy:** no new personal data. `name_language` is the app language, not the user's location.
- **Store requirements:** the forced-update screen of design 0026 is already accepted practice.
  Nothing new.

## Risks

- **An N client meets a document without `sk_name` or `name_language`** (between steps 3 and 5) →
  that load can fail → keep the window short by running steps 4 and 5 immediately after the
  release becomes available, and check before release how one undecodable document affects a list
  on each platform.
- **Backfill triggers `propagate` once per catalogue item**, and each run scans `stats` → cost
  grows with catalogue size × users → measure on a dry run first. If it is too high, undeploy the
  trigger for the duration of the backfill, since the backfill writes the copies itself.
- **A catalogue item with a blank name slips in before the rules are deployed** → it is not found
  by that language's two queries → the backfill's second run lists it, and it is fixed before
  step 6.
- **Backfilled Slovak names cannot be told apart from translated ones** (`sk_name == cz_name`) → no
  list of what still needs translating → accepted. A translation pass works through the catalogue
  in order, not from a diff.
- **The maintainer approves without translating** → the original name is copied into the other two
  languages → accepted: the item is still found and shown everywhere, and translating it later in
  the catalogue editor propagates to users' copies.
- **Translation quality** → the author is not a native speaker → a native review pass, as design
  0027 already requires for the UI texts.

## Outcome

_Filled in once implemented._
