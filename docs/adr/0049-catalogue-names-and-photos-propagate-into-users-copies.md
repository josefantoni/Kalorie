# 0049. Catalogue names and photos propagate into users' copies

- **Status:** Accepted
- **Scope:** Backend
- **Date:** 2026-11-08

## Context

[ADR 0009](0009-denormalised-nutrition-snapshots.md) copies a food into `foodConsumed`,
`favouriteFoods`, `myCreatedMeals` and (design 0020) `stats/foodFrequency` and forbids reconciling
the copies with `foodItems`. For macros that is right: a logged entry must keep the numbers it was
logged with. For the name it is not: a maintainer who corrects a name while approving a submission,
or later in the editor, sees the new name in the catalogue and in the report detail, but the author's
Dashboard and search keep the submitted one. The same holds for `photo_url` in favourites and the
frequency snapshot. Security rules forbid a maintainer's device from writing another user's
`users/{uid}/…`, so no client can fix it.

## Decision

1. `cz_name`, `eng_name` and `photo_url` of a `foodItems` document propagate into every copy that
   references it. Macros, grams, dates, kind and portions never propagate; ADR 0009's reasoning about
   them stands.
2. The propagation is server code: a Cloud Function triggered by writes to `foodItems/{id}`
   ([design 0032](../design/0032-propagating-catalogue-changes.md)). This replaces ADR 0009 **in part**
   (its ban on reconciliation, for these three fields only); ADR 0009 is not edited.
3. A name is never removed from a copy: both clients decode `cz_name` and `eng_name` as non-optional.
   A removed photo deletes the field in the copy, it does not write `null`.
4. `myCreatedMeals` is left alone; design 0006 already refreshes ingredients when the meal is opened.

## Consequences

- First server code in the repository. It is deployed by hand, like rules and indexes.
- Copies made while a submission was pending (`food_item_id` = the barcode) are renamed on approval.
- The stale `photo_url` in favourite and frequency snapshots noted by design 0031 is fixed too.
