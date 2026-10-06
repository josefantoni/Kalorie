# 0040. Search is diacritic-insensitive everywhere, no longer queries the lowercase fields, and needs two characters

- **Status:** Accepted
- **Scope:** Backend, Cross-platform, iOS, Android
- **Date:** 2026-10-06

## Context

`SearchFoodItemsUseCase` runs six concurrent `limit(10)` queries per search (ARCHITECTURE § 2.2):
a prefix range over `cz_name_lowercase` / `eng_name_lowercase` ([ADR 0013](0013-prefix-search-over-lowercased-name-fields.md)),
the same over `cz_name_folded` / `eng_name_folded` (finding A2-3), and `array-contains` over
`cz_name_search_terms` / `eng_name_search_terms` ([ADR 0024](0024-token-array-field-for-whole-word-search.md)).
Firestore bills every returned document and at least one read per query, so a search costs
between 6 and 60 reads. Searching is the second-largest read cost in the app, after the Dashboard
month reload ([ADR 0041](0041-dashboard-reloads-only-the-affected-day-after-a-write-or-foreground.md)).

**The lowercase pair finds nothing that the folded pair does not.** `foldDiacritics`
(`TextKit/src/commonMain/kotlin/DiacriticFolding.kt`) maps one character to one character. So
whenever `name.lowercase()` starts with `q`, `foldDiacritics(name.lowercase())` also starts with
`foldDiacritics(q)`. The only reason ARCHITECTURE § 2.2 gives for keeping the lowercase pair is
catalogue documents written before the folded fields existed. `scripts/backfill-name-folding.js`
has since written the folded fields onto every pre-existing document, and both clients'
`FoodItemDTO(item:)` (iOS `FoodItemDTO.swift:69-72`, Android `FoodItemDTO.kt:50-53`) always write
the folded and search-term fields. The rules do not require them, though (`validFoodItem`,
`backend/firestore.rules:14`), so a document written outside the apps could still lack them.

**Client-side matching is not diacritic-insensitive.** `displayedResults` (iOS
`AddFoodSheetViewModel.swift:233-252`, Android `AddFoodSheetViewModel.kt:114-131`) matches
favourites, created meals and own submissions with `lowercased().hasPrefix(query)`. So "rohlik"
finds a catalogue *Rohlík* but not a favourited one, and "mlék" never finds a favourited
*Polotučné mléko*. ARCHITECTURE § 2.2 records this as the current state, not as a decision. The
product expectation is that `ěščřžýáíé` and `escrzyaie` are interchangeable everywhere.

**A one-character query is the most expensive search and the least useful one.** The catalogue
query runs once the 300 ms debounce settles, with no length check (iOS
`AddFoodSheetViewModel.swift:440`, `MyCreatedMealEditorViewModel.swift:154`, and their Android
counterparts). A single letter fills every `limit(10)`, so it is close to the 60-read maximum, and
returns an alphabetical slice of the catalogue that nobody picks from.

## Decision

1. **`SearchFoodItemsUseCase` drops the two `*_lowercase` queries on both platforms.** Four
   queries remain, merged first-occurrence-wins in this order: `cz_name_folded` →
   `eng_name_folded` → `cz_name_search_terms` → `eng_name_search_terms`.
2. **The `*_lowercase` fields are still written and still required.** `validFoodItem` keeps
   requiring them and `FoodItemDTO` keeps decoding them as non-optional. Only the read side
   changes, because removing the fields is a schema migration with no read-cost benefit.
3. **`validFoodItem` additionally requires** `cz_name_folded is string`,
   `eng_name_folded is string`, `cz_name_search_terms is list` and `eng_name_search_terms is list`.
   This guarantees that the remaining four queries can find every document written from now on.
4. **Client-side matching uses the catalogue's semantics**, through a new TextKit function
   ([ADR 0039](0039-swift-only-rules-move-into-kmp-or-share-golden-vectors.md) rule 1: strings in,
   Boolean out):

   ```kotlin
   fun matchesSearchQuery(name: String, query: SearchQuery): Boolean =
       foldDiacritics(name.lowercase()).startsWith(query.folded) ||
           searchTerms(name).contains(query.lastWord)
   ```

   `displayedResults` calls it with `searchQuery(searchText)` in place of
   `lowercased().hasPrefix(...)`. Each tier keeps the names it matches today: favourites and
   submissions use either name, created meals use `czName` only. Only the predicate changes. The
   tier order, the `query.isEmpty` guard and the dedupe rules stay as they are.
5. **`SearchFoodItemsUseCase` returns `[]` without querying Firestore when the query, trimmed of
   whitespace, is shorter than 2 characters.** This applies on both platforms, so all three
   callers inherit it: the add-food sheet, the created-meal editor and moderation's similar-items
   lookup. The client-side matching of decision 4 is free and **is not gated**. A single letter
   still lists matching favourites, meals and submissions.

## Consequences

- **Reads per search drop from 6–60 to 4–40.** No change to which catalogue documents a query can
  find.
- **A one-character query shows no catalogue results**, only local matches. The external
  OpenFoodFacts fallback already needed 3 characters, so nothing changes there. This saves the
  most expensive search of every typing session whenever the user pauses after the first letter.
- **Ordering changes slightly.** Today the lowercase results come first in the merge, so a query
  typed *with* diacritics ("mlé") lists exact-diacritic matches ("mléko") before "mleté". From
  now on both sort together, in folded alphabetical order. This makes a visible difference only
  when a field has more than 10 matches and the limit cuts some. Accepted as cosmetic.
- **Locally, favourites, meals and submissions match more often.** That includes non-first words:
  "mlék" now finds a favourited *Polotučné mléko*. The external OpenFoodFacts fallback is gated on
  `displayedResults.isEmpty` ([ADR 0023](0023-external-search-gate-includes-favourites-and-meals.md)),
  so it fires in fewer cases. That is the gate working as designed, with a more complete
  `displayedResults`. ARCHITECTURE § 2.2 noted that a client folding consistently "triggers the
  OpenFoodFacts search in different cases". This decision makes both clients fold, so they still
  agree with each other.
- `SearchQuery.lowercased` is no longer read by any search path. It stays in TextKit, because
  removing it would supersede ADR 0039 item 3 for no gain.
- Slovak-only characters (`ä ô ĺ ľ ŕ`) are still not folded (ARCHITECTURE § 2.2). Unchanged.
- [Design 0020](../design/0020-search-ranked-by-log-frequency.md) uses the same
  `matchesSearchQuery` for its frequency candidates. Whichever lands first adds the function, and
  the other reuses it.
- This supersedes ADR 0013's query shape (the range over `*_lowercase`). The fields themselves,
  the `\u{f8ff}` upper bound and the rest of ADR 0013 are unchanged.

## Implementation handoff

Order: **TextKit → rules → iOS → Android.** Each step is its own commit, since commits are scoped
to one platform.

### 1. TextKit (`[FEAT]`)

- Add `matchesSearchQuery` exactly as above to `TextKit/src/commonMain/kotlin/SearchTerms.kt`.
- Add tests to `TextKit/src/commonTest/kotlin/SearchQueryTest.kt`. Each case states why it
  matters:
  - "rohlik" matches "Rohlík" (folded query vs. accented name);
  - "rohlík" matches "Rohlik" (accented query vs. plain name);
  - "mlék" matches "Polotučné mléko" (a non-first word through the tokens);
  - "polotučné ml" matches "Polotučné mléko" (full-name prefix with a multi-word query);
  - "léko" does not match "Polotučné mléko" (prefix per word, not substring);
  - an empty query does not crash. Callers guard against it, so assert only that the function
    returns.
- Swift sees it as `SearchTermsKt.matchesSearchQuery(name:query:)`, next to the existing
  `SearchTermsKt.searchQuery(query:)` (`SearchFoodItemsUseCase.swift:30`). Rebuild the XCFramework
  the same way as for any other TextKit change.

### 2. Rules (`[FEAT]`, `backend/` + `firestore-rules-tests/`)

- **Before deploying**, confirm that no catalogue document lacks the fields. Run
  `node scripts/backfill-name-folding.js --key <sa.json> --dry-run` and the same for
  `scripts/backfill-search-terms.js`. Both must report nothing left to do. If either reports
  anything, run it without `--dry-run` first. Ask the user to run these: they need the service
  account key.
- Add the four clauses to `validFoodItem` (`backend/firestore.rules:14`). That function also
  guards `foodItemSubmissions` create/update (`:57-75`), which is intended: every client builds
  `item` through `FoodItemDTO(item:)`.
- `firestore-rules-tests/firestore.rules.test.js`: add the four fields to the `foodItem()` fixture
  (`:17`). `cz_name_folded: 'rohlik'`, `cz_name_search_terms: ['r', 'ro', ...]` and the
  equivalents are enough. Add their names to the `requires ${field}` loop (`:146`). Add one test
  that an item with `cz_name_search_terms: 'rohlik'` (a string, not a list) is denied.
- **Deploying the rules must precede neither client release nor depend on one.** Both clients
  already write the fields. The only writers this would deny are app builds older than the
  folded-field fix. Mention that to the user before deploying, in case such builds are still in
  testers' hands.

### 3. iOS (`[REFACTOR]` for the query, `[FIX]` for local matching)

- `Core/UseCases/SearchFoodItemsUseCase.swift:34-45`: delete `byName` and `byOriginalName`.
  Remove them from the tuple and the merge, and stop reading `searchQuery.lowercased` there.
- `KalorieTests/SearchFoodItemsUseCaseTests.swift`:
  - `test_search_mergesResultsFromBothNameFields` (`:15`) and
    `test_search_withResultsFromBothLowercaseAndFoldedFields_deduplicatesById` (`:55`) stub the
    lowercase fields. Rewrite them against the folded fields.
  - The fake's `case "cz_name_lowercase"` / `"eng_name_lowercase"` (`:162-163`) should become
    unreachable. Make the fake **fail the test** (`XCTFail`) if those fields are ever queried, so a
    regression that brings the queries back is caught.
  - Add a test that the merge order is folded cz → folded en → token cz → token en.
- `Features/AddFoodSheet/AddFoodSheetViewModel.swift:233-252` (`displayedResults`): build
  `let query = SearchTermsKt.searchQuery(query: searchText)` once. Replace the three
  `lowercased().hasPrefix(query)` predicates (`:237`, `:241`, `:248`) with
  `SearchTermsKt.matchesSearchQuery(name:query:)` over the same names. Keep the
  `guard !searchText.isEmpty`.
- Decision 5: at the top of `SearchFoodItemsUseCase.callAsFunction`, add
  `guard query.trimmingCharacters(in: .whitespaces).count >= Constants.Search.minimumQueryLength
  else { return [] }`. Add a new `enum Search { static let minimumQueryLength = 2 }` to `Constants` in `Core/Utils/Constants.swift`. It does not exist yet.
  The view models need no change: their own `isEmpty` guards stay, and the existing
  `searchText.count >= 3` external gate covers 1–2 characters.
- `SearchFoodItemsUseCaseTests`: test that "m" and " m " make **no** provider call (the fake
  records calls) and return `[]`, and that "ml" queries. The test pins the read-cost intent, so
  name it that way, e.g. `test_search_withSingleCharacter_doesNotQueryFirestore`.
- `KalorieTests/AddFoodSheetViewModelTests.swift`: add a test that a one-letter query still lists
  a matching favourite. Add tests that "rohlik" finds a favourited
  "Rohlík", that "mlék" finds a favourited "Polotučné mléko", and that a created meal is still
  matched only by `czName`.
- `MyCreatedMealEditorViewModel` (`:165`) and `ModerationReviewViewModel` (`:70`) call the same
  use case and need no change.

### 4. Android (same split)

- `core/usecases/SearchFoodItemsUseCase.kt`: the same deletion, and the same decision 5 guard
  (`query.trim().length < MINIMUM_QUERY_LENGTH` → `emptyList()`). Add a new
  `object Search { const val MINIMUM_QUERY_LENGTH = 2 }` to `Constants` in `core/utils/Constants.kt`,
  mirroring iOS.
- `SearchFoodItemsUseCaseTest.kt`: the same test changes as on iOS, including the
  one-character tests.
- `features/addfoodsheet/AddFoodSheetViewModel.kt:114-131`: the same predicate change, using
  `searchQuery` / `matchesSearchQuery` from TextKit directly.
- `AddFoodSheetViewModelTest.kt:858`
  `displayedResults_matchesFavouritesByLowercasedPrefixWithoutFoldingDiacritics` **pins the old
  behaviour and must be inverted**, not deleted. Rename it to say a favourite matches without
  diacritics and assert that `displayedResults` contains the item. Add the same new tests as on
  iOS.

### 5. Docs (`[DOCS]`, after both clients)

- ARCHITECTURE § 2.2: four queries, the new merge order, and drop the "plain lowercase pair stays
  alongside it" sentence. Replace the paragraph saying "not diacritics-folded … deliberately
  differ today" with the new matching rule and a link to this ADR.
- ARCHITECTURE § 2.3: the 2-character minimum for the catalogue query, next to the existing
  3-character external gate.
- ARCHITECTURE § 1.6 / § 1.7: the four newly required fields.
- ADR 0013 Status line: `Superseded by ADR 0040 (the query over *_lowercase only — the fields are
  still written and required)`. Update the matching row in `docs/README.md`.

### Manual verification

On both platforms, against the real catalogue:

1. "rohlik" and "rohlík" return the same catalogue results.
2. With *Rohlík* favourited, "rohlik" lists it in the favourites tier.
3. With *Polotučné mléko* favourited, "mlék" lists it, and the OpenFoodFacts section does not
   appear.
4. Typing a single letter shows matching favourites but no catalogue results. In the `#if DEBUG`
   provider log there is no `foodItems` query until the second letter.
5. Both platforms return the same list for the same query.
