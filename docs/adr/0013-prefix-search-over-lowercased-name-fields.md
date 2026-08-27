# 0013. Catalogue search is a Firestore prefix range over pre-lowercased name fields

- **Status:** Accepted
- **Scope:** Backend, Cross-platform
- **Date:** 2026-08-27

## Context

Firestore has no text search. It offers equality, range and ordering on indexed fields, and
nothing else — no `contains`, no tokenisation, no relevance. The documented workarounds are
either a range query that emulates a prefix match, or an external search service (Algolia,
Typesense, Elastic) fed by a Cloud Function.

`CreateFoodItemUseCase` therefore writes two extra fields on every catalogue document,
`cz_name_lowercase` and `eng_name_lowercase`, which exist for no other purpose.
`SearchFoodItemsUseCase` runs two concurrent range queries over them:

```
whereField(field, isGreaterThanOrEqualTo: prefix)
whereField(field, isLessThan: prefix + "\u{f8ff}")
limit(10)
```

`\u{f8ff}` is a high private-use code point, so the range covers every string starting with the
prefix. Results from the two queries are concatenated and de-duplicated by id.

An external search service was not adopted. No requirement forced the choice, and the project
has no Cloud Functions at all — adding one for search would be the first, and would introduce a
second system to keep in sync with `foodItems`.

## Decision

Catalogue search stays inside Firestore as a case-folded **prefix** range over two denormalised
lowercase name fields. No search service, no Cloud Function, no client-side full-collection
scan.

## Consequences

- Search costs two document reads per keystroke-batch and needs no infrastructure. Both fields
  are single-field indexed automatically, so nothing has to be configured.
- **The lowercase fields are not redundant copies — deleting them breaks search.** They are
  written once at create time and never updated, so any future edit path for `foodItems` must
  rewrite them too.
- **Only prefixes match.** "mléko" does not find "Polotučné mléko". This is the single biggest
  limitation and it is inherent to the approach, not a bug to fix in place — finding **A2-4**.
- **Only case is folded, not diacritics.** `lowercased()` maps "Rohlík" to "rohlík", so a user
  typing "rohlik" on a keyboard without diacritics finds nothing. For a Czech-first app this is
  severe, and unlike the prefix limit it *is* fixable within this decision, by writing a
  diacritics-folded field alongside the lowercase one — finding **A2-3**.
- **Results are not ranked.** Firestore returns them in index order, which is alphabetical by
  the matched field, and the `limit(10)` cuts before anything can re-rank. *Rank search results
  by frequency* cannot be implemented as a re-sort of these results — see finding **A2-12**.
- A second client must reproduce the `\u{f8ff}` upper bound and the same lowercasing, or the two
  clients return different results for the same query.
- Moving to a search service later is a contained change on the read side — one use case — but
  requires backfilling the index from the whole catalogue.
