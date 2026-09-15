# 0027. Catalogue writes require a maintainer claim, with user contributions queued for approval

- **Status:** Accepted
- **Scope:** Backend, Cross-platform
- **Date:** 2026-09-10

## Context

[ADR 0011](0011-foodItems-writable-by-any-authenticated-client.md) left `foodItems` writable by any
authenticated client and named its own replacement without designing it: submissions to a pending
collection, a maintainer approving them behind a Firebase custom claim, and only that path writing
to the catalogue. It also recorded the risk it was accepting — any client can overwrite any
catalogue entry, for every user, with no audit trail.

Part of that risk has since been narrowed in the deployed rules: `foodItems` today grants `create`
with per-field validation and no `update` or `delete` at all. ADR 0011's own rule text was never
updated to match, a drift [design 0008](../design/0008-food-portions.md) §*Context* recorded.

Two things now depend on the missing flow rather than merely wanting it:

- **Design 0008's canonical portions are write-once** purely because there is no `allow update` to
  write through. Correcting a wrong gram value on a catalogue item is impossible from any client.
- **Finding A1-7** needs a moment at which a catalogue correction is known to have happened.
  Approval is that moment; without it, favourites and saved meals have no trigger to re-read against.

The tension that kept this open is that closing client writes naively breaks the only way the
catalogue grows. ADR 0011 put it plainly: *"Without client write access the catalogue stops growing
altogether."* A user standing at the fridge with a packet in hand cannot wait hours for a single
maintainer to approve something before logging what they ate.

[Design 0009](../design/0009-catalogue-moderation.md) resolves that tension by separating *who may
write the shared catalogue* from *when a user may log a food*, which turns out to cost nothing:
`foodConsumed` already carries denormalised nutrition
([ADR 0009](0009-denormalised-nutrition-snapshots.md)) and needs no catalogue document to log
against.

## Decision

Catalogue writes move behind a maintainer claim, and user contributions reach the catalogue only
through an approval queue. Concretely:

- **`foodItems` grants `create` and `update` only to a caller holding the `maintainer` custom
  claim**, both under the same field validation. `delete` stays denied for everyone, maintainer
  included. `read` is unchanged — any authenticated client.
- **User contributions go to a new top-level `foodItemSubmissions/{uuid}` collection.** The document
  id is a client-generated UUID identifying the *queue entry*; the food's own identity is the
  `barcode` field, which becomes the document id in `foodItems` on approval.
- **A submitting user logs the food immediately, before approval.** The logged entry carries
  `food_item_id` = the barcode and `food_item_kind` = `.catalogue`
  ([ADR 0025](0025-food-item-kind-discriminates-entry-origin.md)), referencing a catalogue document
  that does not exist yet — and, if the submission is rejected, never will.
- **Correcting an existing catalogue entry is the maintainer's own action**, not a submission. The
  submission path refuses a barcode already in the catalogue, so the two paths never overlap.

This takes effect when design 0009 ships. Until then the rules stay as deployed and ADR 0011's
accepted risk remains live.

## Consequences

- **Design 0008's write-once rule changes meaning.** Canonical portions stop being write-once
  because no `update` exists, and become write-once *for ordinary clients* while the maintainer may
  edit them. A reader of design 0008 who concludes that updating a catalogue item is impossible is
  reading a statement that was true when written and no longer is.
- **`food_item_kind: .catalogue` no longer implies the document resolves.** ADR 0025's three cases
  describe what an id *refers to*, not whether it currently exists, and no fourth case is added. Any
  client — including a future Android one — must tolerate a `.catalogue` reference that resolves to
  nothing, both temporarily before approval and permanently after a rejection. The logged macros
  stay correct regardless, because they are denormalised.
- **The claim is enforced in `firestore.rules`, never in the client.** The iOS panel reads the claim
  only to decide whether to render. Anyone who moves that check into the client alone reopens
  precisely the risk this record closes.
- **`CreateFoodItemUseCase`'s existence check becomes load-bearing in a new way.** Because the
  maintainer holds `update` and `setAsync` replaces rather than merges, that check is the only thing
  standing between an approval and silently overwriting a catalogue entry — canonical portions
  included — for a barcode that entered the catalogue after the submission was filed. Removing it as
  redundant would destroy data with no error surfaced.
- **Pending submissions are not migrated across an anonymous → signed-in merge**, for the same
  reason meal types are not: `submitted_by` cannot be reassigned in a way the rules can verify. The
  submission still gets approved and still reaches the catalogue; its author merely loses sight of
  its status.
- **The external-search gate widens.** A matching own submission suppresses the OpenFoodFacts
  fallback exactly as a favourite or saved meal already does — an extension of
  [ADR 0023](0023-external-search-gate-includes-favourites-and-meals.md), not a new rule.
- **A2-12 is untouched.** The queue is a separate collection, so `SearchFoodItemsUseCase`'s six
  concurrent queries and their `limit(10)` are not modified and no new index is added to
  `foodItems`. An alternative that put an `approved` flag on the catalogue itself would have needed
  a composite index per query; it was rejected for that reason.
- **Ordinary users lose the ability to correct the catalogue at all.** Today anyone can overwrite a
  wrong entry — badly, but they can. After this, only the maintainer can, and a user who spots an
  error has no in-app channel to report it. Tracked in `TODO.md` as its own item.
- **Reopening `create` to ordinary clients without also removing the queue would produce two
  independent paths into the shared catalogue**, one validated by approval and one not. Whoever
  revisits this must undo both halves together, exactly as ADR 0011 warned that hardening the rule
  alone would break the *add a new food* form.
