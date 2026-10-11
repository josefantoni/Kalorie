# 0042. Search matches the displayed name only

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-10-11

## Context

[ADR 0036](0036-food-display-name-is-chosen-by-device-language-at-render-time.md) chooses the name
a food is shown by from the device language, and records in its Consequences that "search does not
follow this rule: it queries both name fields regardless of display language, so a user can find a
food by either name." [ADR 0040](0040-search-is-diacritic-insensitive-everywhere-and-drops-the-lowercase-queries.md)
repeats the same for favourites and submissions ("use either name").

In practice this shows a result whose displayed name does not resemble the query. In a Czech
app, typing `Bee` lists *Hovězí maso libové syrové* and *Červená řepa* because their English names
are *Beef* and *Beet*. The row reads as a wrong result, and the same query would not find the
OpenFoodFacts beer that the user was after, because the gate in
[ADR 0023](0023-external-search-gate-includes-favourites-and-meals.md) sees a local hit.

## Decision

A food is matched by the name it is displayed with: `displayName` of ADR 0036, which is `cz_name`
for `cs`/`sk` and `eng_name` otherwise.

The fallback of ADR 0036 applies to matching as well, so a food without a name in the app language
is still found by the other one:

1. `cs` / `sk`: `cz_name`, or `eng_name` when `cz_name` is empty.
2. Otherwise `eng_name`, or `cz_name` when `eng_name` is empty.

This holds for the catalogue search, favourites, own submissions and the frequency candidates. A
created meal was already matched by `cz_name` only, which is its displayed name in every language.

The Firestore queries do not change: all four are still sent, because the fallback needs the
other language's index fields. The client drops, from the merged result, every item whose
displayed name does not match the query (`TextKit.matchesSearchQuery`), so the merge order and the
per-query limit of 10 stay as in [ADR 0024](0024-token-array-field-for-whole-word-search.md).

## Consequences

- Supersedes the "Search does not follow this rule" consequence of ADR 0036 and the "either name"
  statement in ADR 0040. Everything else in both records is unchanged.
- A food is no longer found by its name in the other language. A Czech user who types `beef` gets
  no catalogue hit, and an English one who types `hovězí` gets none.
- A query that matched only a hidden name no longer suppresses the OpenFoodFacts fallback
  (ADR 0023), because the gate reads the already filtered list.
- The filter can leave fewer than 10 results from a query, since the cut-off by `limit(10)` happens
  before it. Frozen [design 0003](../design/0003-favourite-foods.md) and
  [design 0020](../design/0020-search-ranked-by-log-frequency.md) describe matching on either name;
  that is what this record replaces.
- Both clients implement the same filter, in `SearchFoodItemsUseCase` and in the result ranking of the
  add-food sheet.
