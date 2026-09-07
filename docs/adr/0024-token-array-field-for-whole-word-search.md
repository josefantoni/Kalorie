# 0024. A per-word prefix array closes the "second word" gap in catalogue search

- **Status:** Accepted
- **Scope:** Backend, Cross-platform
- **Date:** 2026-09-07

## Context

[ADR 0013](0013-prefix-search-over-lowercased-name-fields.md) chose a Firestore range query over
the whole, lowercased name as catalogue search, and named the cost explicitly: only a **prefix of
the first word** matches. "mléko" does not find "Polotučné mléko" — finding **A2-4** in
`TODO.md`. ADR 0013 called this "inherent to the approach, not a bug to fix in place," and
`TODO.md` asked for the fix to be a deliberate choice between two options: an n-gram/token array
field, or an external search service (Algolia, Typesense, Elastic).

An external service was rejected for the same reason ADR 0013 rejected it originally: the project
has no Cloud Functions, and standing one up to keep a second system in sync with `foodItems`
solely for search is disproportionate to the problem — a missing second-word match, not a missing
relevance ranking (finding **A2-12**, which this record does not touch).

Firestore supports `array-contains`, an exact-match filter over one value in an array field, with
no partial-match variant. The standard workaround for prefix search inside an array is to store,
for every word, **every prefix of that word** as a separate array element, then query
`array-contains` with the user's own (folded) input. A query for "mlék" hits the stored prefix
"mlek" inside `["p", "po", …, "polotucne", "m", "ml", "mle", "mlek", "mleko"]` directly.

## Decision

Every catalogue document gains two new fields, written by `FoodItemDTO.init(item:)`:

- `cz_name_search_terms` — every prefix (length 1 up to the full word) of every word in `cz_name`.
- `eng_name_search_terms` — the same, over `eng_name`.

Each word is folded through the same pipeline as ADR 0013's `cz_name_folded` /
`eng_name_folded` — lowercased, then diacritics-folded — before its prefixes are generated, so
"mlék" and "mlek" both match a stored "mléko". Unlike the two-tier lowercase/folded split ADR
0013 kept for backward compatibility, there is only **one** variant per language here: this is a
new field with a backfill script (`scripts/backfill-search-terms.js`, mirroring
`scripts/backfill-name-folding.js`), so no catalogue document is ever caught between an old and a
new writer the way the folded fields were.

The algorithm — split on whitespace, fold, generate every prefix, de-duplicate — lives in
**TextKit** as `searchTerms(input:)`, bridged into Swift as `String.searchTerms()`, for the same
reason `foldDiacritics` does: a second client writing to `foodItems` must produce the identical
array or its documents become unsearchable by whatever prefix a first client's user types.

`SearchFoodItemsUseCase` adds two more concurrent queries, `array-contains` on
`cz_name_search_terms` / `eng_name_search_terms` against the **last word** of the same folded
query used against `cz_name_folded` / `eng_name_folded`, `limit(10)` each, merged and
de-duplicated by id exactly like the existing four. The last word, not the whole folded query, is
what gets matched: a stored array element is always a single word's prefix, so a multi-word query
like "polotučné mlék" would never hit anything if matched whole. This query is independent of the
other five, not a filter layered on top of them — it matches on the trailing word alone, with no
check that any leading word in the query corresponds to anything in the candidate name. A query
like "jablko mlék" therefore also surfaces "Polotučné mléko", which has nothing to do with
"jablko". This is intentional: the token lookup is a loose, any-word search, not an
all-words-must-match one — see Consequences. This required one new query shape on
`FirestoreDataProviderProtocol` — `loadAsync(from:where:arrayContains:limit:)` — following the
provider's one-method-per-query-shape convention.

`foodItems`' security rules validate no field of this kind already (`cz_name_lowercase` and the
folded fields go unchecked too), so the two new fields are added with no rules change.
`firestore.indexes.json` disables automatic indexing only for the catalogue's numeric fields;
array fields keep Firestore's default single-field array-contains index, so no index
configuration changes either.

## Consequences

- "mléko" now finds "Polotučné mléko", and any word in a catalogue name is reachable by typing a
  prefix of *that* word, not only the first one. Closes **A2-4**.
- **Storage grows with word length, not name length.** A three-word Czech name with an
  eight-letter longest word adds roughly 15–20 array entries per language field. Cheap at
  catalogue scale, the same trade ADR 0009 already accepted for denormalised nutrition.
- **This is still a prefix match, not a substring match.** "léko" (a mid-word fragment) still
  does not find "mléko" — only "m", "ml", "mle", … do. Nothing in this record promises substring
  search; that remains outside what a Firestore array-contains query can do without a real search
  service.
- **`array-contains` is exact-match only.** The query string must equal a stored prefix exactly
  (after the same fold), so this adds no fuzziness or typo tolerance — a query has to be a genuine
  prefix of a genuine word.
- **The token lookup matches on the query's last word alone, regardless of the other words typed.**
  A multi-word query is not required to match word-by-word against the candidate name — only its
  trailing word has to hit a stored token. "jablko mlék" surfaces "Polotučné mléko" even though
  "jablko" matches nothing in it. Chosen deliberately over the more literal-minded alternative
  (requiring every word to match): a false positive here just adds an extra, ignorable row to the
  result list, while requiring every word to match would silently drop a food whose name doesn't
  happen to contain the user's earlier, less important words in order.
- The `limit(10)` per query and Firestore's lack of relevance ranking are unchanged. **A2-12**
  stays open: results cut by the per-field limit are still invisible to any client-side re-sort,
  and now cut across six queries instead of four.
- A document written before this record's backfill runs decodes both new fields as `nil` and is
  found only through the existing four prefix-of-first-word queries, exactly as before. This is
  the same transitional state ADR 0013 already documents for `cz_name_folded` /
  `eng_name_folded`, not a new kind of gap.
- **A second client that writes to `foodItems` must produce byte-identical `search_terms`
  arrays**, via the same TextKit function — not its own re-tokenisation — or a name it writes
  becomes unsearchable by a prefix a first-client user would expect to work, and vice versa.
