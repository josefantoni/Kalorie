# 0046. Search words are trimmed of edge punctuation, not split on it

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-10-21

## Context

[ADR 0024](0024-token-array-field-for-whole-word-search.md) splits a name on whitespace and
[ADR 0026](0026-js-backfill-duplicates-textkit-under-a-shared-fixture.md) on the same literal `" +"`.
A word that starts with punctuation, such as `(džem)` in "Jahodová marmeláda (džem)", produced the
prefixes `(`, `(d` … and never `dzem`, so a query for "džem" found nothing. The client-side
`matchesSearchQuery` filter dropped the item even when Firestore returned it.

## Decision

`searchWords` in TextKit trims a fixed set of punctuation (`()[]{},;:.!?"'„“”‚‘’«»`) from the start
and end of each whitespace-separated word. It does not split on punctuation: `1,5%` and
`vepřovo-hovězí` stay single words. `searchTerms`, `searchQuery.lastWord` and `matchesSearchQuery`
all use it, and `scripts/lib/search-terms.js` mirrors it, pinned by `text-kit-cases.json`.

## Consequences

- Stored `*_search_terms` arrays written before this change are stale for names with edge
  punctuation; `scripts/backfill-search-terms.js` now rewrites any document whose arrays differ
  from the freshly computed ones.
- Old app versions keep the old filter, so the fix reaches users with new builds only.
