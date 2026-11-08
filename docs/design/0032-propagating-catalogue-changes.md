# Design: Propagating catalogue name and photo changes into users' copies

- **Status:** Draft
- **Scope:** Backend
- **Date:** 2026-11-08

## Context and scope

[ADR 0049](../adr/0049-catalogue-names-and-photos-propagate-into-users-copies.md) decides that
`cz_name`, `eng_name` and `photo_url` propagate from `foodItems` into users' copies. This document
designs the Cloud Function that does it. No client changes.

## Design

- `backend/functions/` — plain JavaScript (no build step, like `firestore-rules-tests/`), runtime
  `nodejs22`, 2nd gen `onDocumentWritten({ document: "foodItems/{id}", region: "europe-west1" })`
  (Firestore is `eur3`). The trigger is a wrapper around the exported `propagate(db, id, before, after)`,
  which tests and one-off fixes call directly. Firestore does not order trigger events, so the trigger calls
  `propagateCurrent(db, id, before)`, which propagates the current `foodItems/{id}` document, not the
  event's `after`. Commit order is not read order, so after `propagate` it re-reads the document and, if
  any of the three fields changed meanwhile, propagates again from the value it just wrote (at most three
  times): the last invocation to finish has then seen the latest document.
- `propagate` returns early when `after` is absent or none of the three fields differs between `before`
  and `after`. Creation counts (`before` is absent). It never writes to `foodItems`, so there is no
  trigger loop.
- A name field is propagated only when `after` has it as a string. A removed photo deletes `photo_url`
  with `FieldValue.delete()`. Only fields whose value differs in the copy are written, so a rerun writes
  nothing.
- Copies with `food_item_kind == created_meal` are skipped. `external` copies with the same barcode are
  renamed and keep their kind; [ADR 0044](../adr/0044-external-favourites-are-upgraded-when-the-food-enters-the-catalogue.md)
  still upgrades the rest lazily.

| Copy | Found by | Fields written |
|---|---|---|
| `users/{uid}/foodConsumed` | collection group, `food_item_id == id` | `cz_name`, `eng_name` (the document has no `photo_url`) |
| `users/{uid}/favouriteFoods` | collection group, `id == id` | all three |
| `users/{uid}/stats/foodFrequency` | collection group `stats`, document id `foodFrequency`, `select(entries.<id>)` | `entries.<id>.item.<field>` through `FieldPath` — a barcode key starts with a digit (design 0020) |

- Writes go through `db.bulkWriter()`. `NOT_FOUND` (copy deleted meanwhile) is swallowed, other errors
  are retried three times and then thrown so the invocation is logged as failed. Trigger `retry` stays off.
- **Indexes.** Both collection group queries need a `COLLECTION_GROUP` field override in
  `firestore.indexes.json` (`foodConsumed.food_item_id`, `favouriteFoods.id`). The override replaces the
  automatic indexes of those fields; neither client queries them at collection scope, so only the group
  index is listed. Deploy the indexes and wait for them to build **before** deploying the function.
- **Deploy.** `firebase deploy --only functions` from `backend/`; no CI step and no new secret.

## Risks and accepted gaps

- Every catalogue *creation* triggers it, bulk gcloud imports included: two small queries and one read
  per user's `stats` document per item. Trivial today; revisit past a few thousand users (an indexed
  array of food ids kept by both clients would replace the stats scan).
- An author who changed the barcode while the submission was pending has older entries under the old id
  (design 0013 accepts this); the function cannot see them.
- Editing a name again in the editor does not repair an already diverged copy, because no field differs.
  A one-off fix calls `propagate(db, id, null, <current item data>)` from a local script.
- Clients write names and photos from memory in two places, and neither listens to snapshots: copying a
  section to another day (`CopyFoodsConsumedUseCase`) and logging an item from a list loaded before the
  propagation (the `item` map in `stats/foodFrequency`). If a rename lands while the screen is open, the new
  document carries the old name until the item changes in the catalogue again. Accepted: it needs a rename
  during one open screen, and the damage is cosmetic.

## Outcome

*Filled in once after shipping.*
