# 0023. External search fallback gate description corrected — favourites and saved meals count as "found"

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-09-04

## Context

[ADR 0012](0012-external-food-is-surfaced-never-imported.md)'s Context section describes
`SearchFoodExternallyUseCase` as "run only when the Firestore search returned nothing and the
query is at least three characters." Read literally that is `localFoodItems.isEmpty`. The actual
gate, in `AddFoodSheetViewModel.onSearchTextChanged` (`:161`), is `displayedResults.isEmpty`, and
`displayedResults` (`:62-76`) also folds in matching favourites and matching saved meals, not
just the catalogue query.

This is not a drift between code and decision. [Design 0003](../design/0003-favourite-foods.md)
§*Favourites first in the search results* states the external section "still only appears when
the local result is empty, which now means 'no catalogue hit **and** no favourite hit'," and
[design 0006](../design/0006-own-daily-meals.md) §*Search integration* states the same gate
"correctly stops firing when a created meal matched." Both predate ADR 0012 and neither was
revised by it — ADR 0012's phrase was written for a different question (whether external results
get imported into the shared catalogue) and paraphrased the gate loosely in passing, rather than
reconsidering it.

The project's own audit (`TODO.md`) logged this exact mismatch as a candidate bug, finding
**A2-10**, then downgraded it back to "confirmed intentional" once 0003 and 0006 were checked. An
ADR that a reader could act on — changing `displayedResults.isEmpty` to `localFoodItems.isEmpty`
"because the ADR says so" — is worse than no ADR at all, so the wording is corrected here rather
than left to mislead the next reader the way it misled this review at first.

## Decision

The external OpenFoodFacts fallback fires only when `displayedResults` is empty — no catalogue
hit, no matching favourite, no matching saved meal — and the query is at least three characters.
A local favourite or saved-meal match is treated as sufficient on its own; the network call is
skipped whenever one exists, exactly as designed in 0003 and 0006.

## Consequences

- No code changes. This supersedes only the Context sentence in ADR 0012 that describes the
  gate; the rest of ADR 0012 — external results never write to `foodItems`, the dangling
  `food_item_id`, the "fetched again every session, never cached" consequence — is unchanged and
  still in force.
- `TODO.md`'s A2-10 stays recorded as a downgraded finding ("decided risk, not an open
  question"), not deleted, since it is what caught this.
- A second client implementing the same search screen must gate its own external fallback on the
  full hoisted result list (catalogue + favourites + created meals), not on the catalogue query
  alone.
