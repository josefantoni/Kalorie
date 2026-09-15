# Design: Catalogue moderation

- **Status:** Implemented
- **Scope:** Backend, Cross-platform, iOS
- **Date:** 2026-09-10

## Context and scope

`foodItems` is a single global catalogue shared by every user, and today any authenticated client
can write to it. [ADR 0011](../adr/0011-foodItems-writable-by-any-authenticated-client.md) accepted
that as a pre-release risk and named its own replacement: user submissions go to a separate pending
collection, a maintainer approves them through an admin panel gated by a Firebase custom claim, and
only the approving path writes to `foodItems`. This document designs that replacement.

Three pieces are in scope, of which `TODO.md` named only the middle one:

1. **Submission flow** — the user fills in the macros off the packaging and submits.
2. **Moderation queue** — the maintainer reviews, approves or rejects.
3. **Catalogue editor** — the maintainer corrects an *existing* catalogue entry.

The third is the one that changes the security model most, and it is the one
[design 0008](0008-food-portions.md) refers here three times: canonical portions are write-once
purely because `foodItems` has no `allow update` rule, and correcting a wrong one
("the packet actually says 30 g, not 33 g") was explicitly deferred to this flow. Finding **A1-7**
in `TODO.md` wants the same thing from the other side — approval is the only moment at which a
correction is known to have happened.

What is deployed today is narrower than ADR 0011's own text describes. `Kalorie/firestore.rules`
grants `allow create` on `foodItems` with per-field validation and no `update` or `delete` at all;
the broader `allow write` that ADR quotes was narrowed once the race it enabled was understood
(`ARCHITECTURE.md` §2.6), and design 0008 §*Context* already recorded the drift.

The decision this document argues for is recorded in
[ADR 0027](../adr/0027-catalogue-writes-require-a-maintainer-claim.md), which supersedes ADR 0011's
Decision against the **deployed** rule rather than the quoted one — and takes effect only when this
design ships, since ADR 0011's own Decision made that the condition.

## Goals

- A user can submit a new catalogue item — the same typed macros as today's *add a new food* form —
  and the item reaches the shared catalogue only after the maintainer approves it.
- **The submitting user can log the food immediately**, before approval, with no degraded macros.
- The maintainer can approve, reject with a reason, and **edit an existing catalogue entry** —
  including its canonical portions, which design 0008 could not make editable.
- A rejected submission tells its author why, and can be fixed and resubmitted without retyping.
- `foodItems` stops being writable by ordinary clients. Write access moves behind a custom claim
  enforced in `firestore.rules`, not merely in the UI.
- The catalogue search path (`SearchFoodItemsUseCase`, six concurrent queries at `limit(10)` each)
  is **not** touched, and finding **A2-12** is not made worse.

## Non-goals

- **A packaging photograph on a submission.** Firebase Storage is not configured in this project
  today (`Kalorie/firebase.json` has only a `firestore` block), and whether the project's plan
  includes free Storage quota is unverified. Adding it is real, separable work — a `storage` block,
  `storage.rules`, a second deploy step, a Blaze-plan check — that this document deliberately does
  not bundle with the rest. The maintainer reviews a submission on its typed values alone, the same
  information `CreateFoodItemUseCase` has always trusted from this form. Recorded as its own
  `TODO.md` item, addable later with no schema change: `FoodItemSubmissionDTO` simply gains an
  optional `photo_path` when it ships, exactly as `foodItems` itself has absorbed optional fields
  before (`ARCHITECTURE.md` §1.3).
- **Reporting a wrong value on an existing catalogue item.** The submission flow refuses a barcode
  already in the catalogue (see *Duplicate handling*), so a correction can only begin with the
  maintainer noticing the error. Deliberate for v1: it keeps submission and correction as two
  non-overlapping paths with one entry point each. Recorded as its own `TODO.md` item.
- **Push notification of an approval or rejection.** Every read in this app is one-shot — nothing
  uses `addSnapshotListener` (`ARCHITECTURE.md` §1.5) — and FCM would mean APNs certificates, a
  Cloud Function trigger and a Blaze plan. The author learns the outcome the next time they open the
  add-food sheet.
- **Deleting a catalogue entry.** `delete` stays denied for everyone, maintainer included, exactly
  as it is today. Removing a bad entry is out of scope; correcting one is not.
- **An admin panel on a second platform.** The approval logic lives in Swift inside the iOS client.
  An Android client would either reimplement it or ship without an admin panel. The second is fine
  for a single maintainer, but it is a decision, not an omission — see *Cross-cutting concerns*.
- **Migrating pending submissions across an anonymous → signed-in merge.** See *Cross-cutting
  concerns*; the same reasoning that leaves meal types unmerged applies.
- **Moderating `myCreatedMeals` or personal portions.** Both are private, owner-only data
  (`ARCHITECTURE.md` §1.2) and no one else ever sees them.

## Design

### Two identities, not one

A submission carries two distinct identifiers, and conflating them is the main way this design can
go wrong:

- **`foodItemSubmissions/{uuid}`** — the identity of *the item in the queue*. A client-generated
  `UUID().uuidString`, the same convention `foodConsumed` and `mealTypes` already use
  ([ADR 0021](../adr/0021-meal-type-ids-are-uuids.md)). It exists so two users submitting the same
  barcode produce two queue entries instead of overwriting one another — `setAsync` replaces rather
  than creates (`ARCHITECTURE.md` §1.5), so a barcode-keyed queue would silently lose one side.
- **`barcode`** — the identity of *the food*. This is what goes into `food_item_id` on a logged
  entry, and what becomes the document id in `foodItems` on approval.

Because the logged entry points at the barcode from the start, approval makes the reference resolve
with no migration and no rewrite of anything already logged.

### The submission document

```
foodItemSubmissions/{uuid}
```

```swift
enum FoodItemSubmissionStatus: String, Codable {
    case pending
    case rejected
}

struct FoodItemSubmissionDTO: Codable {
    let id: String                          // the UUID, mirrored as a field — see below
    let barcode: String                     // "barcode"
    let submittedBy: String                 // "submitted_by"
    let status: FoodItemSubmissionStatus    // "status"
    let submittedAt: TimeInterval           // "submitted_at"
    let rejectReason: String?               // "reject_reason", present only when rejected
    let item: FoodItemDTO                   // "item", a nested map
}
```

Notes on the shape, each with a reason:

- **`id` is mirrored as a field** for the same reason `foodItems.id` is: `firestore.rules` cannot
  compare against a document id it has not been handed (`ARCHITECTURE.md` §1.3).
- **`barcode` is duplicated at the top level**, even though `item.id` already holds it. Rules and
  queries both need it, and reaching into a nested map for either is worse to read and worse to
  validate.
- **`item` is a nested `FoodItemDTO`**, not flattened. `MyCreatedMealDTO.ingredients` already
  establishes nested maps as the convention here, and keeping the payload in the exact DTO shape
  `foodItems` expects means approval is a copy, not a re-mapping.
- **`status` has only two cases.** There is no `approved`: approving **creates the catalogue
  document and deletes the submission**. The approved data lives in `foodItems`, so a retained
  submission would be a second copy of it with no reader. The author still learns the outcome — the
  item stops being marked *pending* in search results and simply behaves like any catalogue food.
- **`status` decodes as the enum, not as a `String`.** An unrecognised value fails the decode rather
  than falling through to a default, exactly as `food_item_kind` does
  ([ADR 0025](../adr/0025-food-item-kind-discriminates-entry-origin.md)) — a submission whose state
  cannot be determined must not be silently treated as pending and approved.
- **No `photo_path` field.** See *Non-goals* — Storage is out of scope for v1. When it is added
  later, this DTO gains an optional field, the same additive shape every other DTO in this project
  uses for a field absent on older documents (`ARCHITECTURE.md` §1.3).

Approval is two writes without a shared transaction (create the `foodItems` document, delete the
submission). If the delete fails, a submission survives for a barcode that is now in the catalogue —
which the panel shows as a collision on the next pass, and the maintainer clears by hand. That is
idempotent and visible, which is the property that matters.

### Authorization

The panel is a section of the iOS app, which means it is a **client**, not a privileged process:
Admin SDK bypasses rules, a client does not. The custom claim is therefore genuinely required.

**Setting the claim** is a fourth script in `scripts/`, beside the two backfills — `firebase-admin`
and the service account key are already there. It calls
`admin.auth().setCustomUserClaims(uid, { maintainer: true })`. The runbook (how to find your own
UID, how to set it, how to verify) belongs in `docs/SETUP.md` next to the *User account linking*
row, since it is configuration that lives outside the repository.

**Reading the claim** does not fit either existing auth protocol. `AuthProviderProtocol` is five
synchronous computed properties over `Auth.auth().currentUser`; the claim comes from
`getIDTokenResult()`, which is `async throws`.
[ADR 0003](../adr/0003-separate-auth-command-provider.md) split auth into read-only and command
providers, and this is a third category: an *asynchronous* read of auth state. It gets a use case,
because every asynchronous operation in this project is one:

```swift
protocol FetchMaintainerClaimUseCaseProtocol {
    func callAsFunction() async throws -> Bool
}
```

Neither existing auth protocol changes, so ADR 0003 stands as written.

**The claim in the client is a UI gate, not a defence.** It decides whether the section renders.
Everything that actually protects the catalogue is in `firestore.rules`. This is the one place in
the design where a mistake means *any modified client can overwrite the shared catalogue for every
user* — precisely the risk ADR 0011 accepted temporarily and this work exists to close.

**The claim propagates on token refresh** (roughly hourly, or on `forcingRefresh: true`). After
setting it, sign out and back in, or force a refresh. Worth a line in `SETUP.md`: without it, the
section stays invisible and looks broken.

### Security rules

A shared predicate, because rules have no other way to avoid duplicating field validation across
`create` and `update`, and an unvalidated `update` would let a malformed document break decoding for
every user:

```
function isMaintainer() {
  return request.auth != null && request.auth.token.maintainer == true;
}

function validFoodItem(data) {
  // the nine required numeric fields and three optional ones currently inline in `create`
}
```

`foodItems` — `create` narrows from any authenticated client to the maintainer, and `update` opens
for the first time:

```
match /foodItems/{itemId} {
  allow read: if request.auth != null;
  allow create, update: if isMaintainer()
    && itemId.matches('[0-9]{8}|[0-9]{12}|[0-9]{13}')
    && request.resource.data.id == itemId
    && validFoodItem(request.resource.data);
  // delete stays denied, for everyone
}
```

`foodItemSubmissions` — the first collection in this project with two differently-privileged readers
of the same document. It cannot live under `users/{userId}`, whose `match /{document=**}` block
grants access to the owner alone, leaving the maintainer unable to read anyone's submission:

```
match /foodItemSubmissions/{submissionId} {
  allow read: if isMaintainer() || resource.data.submitted_by == request.auth.uid;

  allow create: if request.auth != null
    && request.resource.data.id == submissionId
    && request.resource.data.submitted_by == request.auth.uid
    && request.resource.data.status == 'pending'
    && validFoodItem(request.resource.data.item);

  allow update: if isMaintainer()
    || (resource.data.submitted_by == request.auth.uid
        && request.resource.data.submitted_by == resource.data.submitted_by
        && request.resource.data.status == 'pending'
        && validFoodItem(request.resource.data.item));

  allow delete: if isMaintainer() || resource.data.submitted_by == request.auth.uid;
}
```

The author's `update` clause is the only rule in the whole project that guards a **specific field
value** rather than ownership alone. It has to: without `status == 'pending'` an author could
approve their own submission, and without the `submitted_by` equality they could reassign it. Both
are worth a Rules Playground case each.

The author's `delete` exists for account deletion (see *Cross-cutting concerns*) and now also backs
withdrawal from **My submissions** (finding **A7-1**, closed) — the author can withdraw a pending or
rejected submission of their own from the add-food sheet.

### Indexes

`firestore.indexes.json` today only disables single-field indexes on `foodItems`. Two composite
indexes are added, both on `foodItemSubmissions`:

| Fields | Serves |
|---|---|
| `status` ASC, `submitted_at` DESC | the maintainer's queue |
| `submitted_by` ASC, `submitted_at` DESC | the author's own submissions |

### Where the submission flow replaces the catalogue write

`AddFoodSheetView.addCustomFoodItem` keeps its **fields** unchanged — the same macros, the same
portions section design 0008 added. What changes is the destination (`SubmitFoodItemUseCase` instead
of `CreateFoodItemUseCase`) and two additions it needs for the resubmit path in *Where the author
sees the outcome*: `FoodItemFormInput` gains an initializer from an existing `FoodItemDomain`, and
the form gains an optional rejection-reason header. Both are inert on the ordinary "new food" path.

`CreateFoodItemUseCase` survives unchanged in purpose but moves behind approval — it becomes the
thing `ApproveSubmissionUseCase` calls. Its validation (id all-digits and of a valid barcode length,
non-empty Czech name, calories > 0, weight > 0, portions) is needed by the submission path too, so
it is extracted into a shared `FoodItemValidation`, following the pattern `FoodPortionValidation`
and `MyCreatedMealValidation` already established: one static validator called both by a view
model's `canSave` and by the use case that writes, so the two cannot drift.

**Duplicate handling happens twice, and both times matter.**

`SubmitFoodItemUseCase` gets a copy of the server-only existence check
(`loadFromServerAsync(id:from:)`, introduced for finding **A2-2**), so a barcode already in the
catalogue is refused before the user fills anything in. `foodItems` stays readable by every
authenticated client; only its writes close.

`CreateFoodItemUseCase` **keeps its own check** rather than handing it over. This is the part that
is easy to get wrong: the maintainer now holds `update` on `foodItems`, and `setAsync` replaces
rather than merges (`ARCHITECTURE.md` §1.5), so without that check approving a submission for a
barcode that entered the catalogue *after* the submission was filed would silently overwrite the
existing entry — including the canonical portions design 0008 made write-once. The queue showing a
collision (*The panel*) is an affordance, not a guard; the check is the guard. Its existing
`CreateFoodItemError.itemAlreadyExists` surfaces in the panel as a refusal to approve, and the
maintainer resolves the collision through the catalogue editor instead.

Two pending submissions for the same barcode are **allowed** — that is what the UUID key buys. The
maintainer approves one; approving the second then fails on exactly the check above, and it is
rejected or cleared by hand.

### Logging before approval

Nothing about logging changes, because `foodConsumed` carries denormalised nutrition
([ADR 0009](../adr/0009-denormalised-nutrition-snapshots.md)) and needs no catalogue document at
all — only `food_item_id` and `food_item_kind`
([ADR 0025](../adr/0025-food-item-kind-discriminates-entry-origin.md)). The entry is written with
`food_item_id` = the barcode and `food_item_kind` = `.catalogue`, and the reference resolves once
the item is approved.

Until then, and permanently if the submission is rejected, `loadCatalogueItem()` returns `nil` and
`canShowFavouriteButton` (`isFavourite || catalogueItem != nil`) hides the favourite button. The
existing code degrades correctly with no change (`ARCHITECTURE.md` §4.5).

This introduces a state `food_item_kind` did not previously have: `.catalogue` pointing at a
document that does not exist *yet*, or after a rejection, never will. **No fourth case is added.**
ADR 0025's three cases describe what the id refers to, not whether it currently resolves, and
`.external` already means "resolves against nothing" for a different reason. A rejected submission
leaves a permanently dangling reference whose logged macros stay correct — the user ate the food and
the calories hold. Accepted, and recorded in *Risks*.

### Search integration

The author must find their own pending item again on later days, not only at the moment they created
it. This costs almost nothing, because `AddFoodSheetViewModel.displayedResults` is already a pure
computed property over three pre-loaded lists — created meals, favourites, and search results — with
favourites and meals loaded once in `onAppear` rather than per keystroke (`ARCHITECTURE.md` §2.2).

Own submissions become a **fourth such list**: one equality query (`submitted_by == me`) when the
sheet opens, one clause in `displayedResults`. `SearchFoodItemsUseCase` and its six concurrent
queries are untouched, so no new index is needed on `foodItems` and finding **A2-12** is unaffected.

One consequence to state up front rather than rediscover: the external OpenFoodFacts fallback is
gated on `displayedResults.isEmpty` (`ARCHITECTURE.md` §2.3), so a matching own submission
suppresses it — exactly as a matching favourite or created meal already does. This is consistent
with [ADR 0023](../adr/0023-external-search-gate-includes-favourites-and-meals.md), which exists
only because that behaviour previously read like a bug. Written down here so it does not need a
second such record.

### Where the author sees the outcome

In the search results, on the row itself. A pending item carries a *waiting for approval* marker; a
rejected one carries a *rejected* marker. Tapping a rejected row opens
`AddFoodSheetView.addCustomFoodItem` **pre-filled from the submission**, with the rejection reason
shown at the top — the same form, reached with existing values instead of empty ones. Saving calls
`UpdateMySubmissionUseCase`, which resets `status` to `pending`; the author never retypes the macros.

No new screen, and no badge anywhere else. The cost is that a rejection reason has to fit a search
result row until the form is opened, so the marker says only *rejected* and the reason lives in the
form.

A row in **My submissions** also carries a swipe-to-delete action, confirmed with an alert that warns
the rejection reason is lost too. Withdrawal is `DeleteMySubmissionUseCase`, an optimistic removal
from `mySubmissions` — see *Withdrawal races* below for what it does not guard against.

### Withdrawal races

Three races, accepted rather than coded around:

- **Withdraw vs. approve.** `ApproveSubmissionUseCase` re-reads, then creates `foodItems`, then
  deletes the submission. A withdrawal landing between the re-read and the create still ends up in
  the catalogue; the approve's own delete then no-ops or fails, and is only logged. The window is
  milliseconds and the result is an approved item, not corrupt data.
- **Withdraw vs. reject.** Already handled by the rules above: `setAsync` on a missing document is
  evaluated as a **create**, the create rule requires `submitted_by == request.auth.uid`, the
  maintainer's write is denied, the re-read returns `nil`, and `RejectSubmissionUseCase` surfaces
  `RejectSubmissionError.alreadyResolved` in `ModerationReviewViewModel`.
- **Logged diary entry.** A pending item the author already logged stays in `foodConsumed` as a
  snapshot after withdrawal — the same consequence already accepted for the anonymous → signed-in
  merge (*Cross-cutting concerns*). Nothing to do.

### The panel

A section in `AccountView`, rendered only when `FetchMaintainerClaimUseCase` returns `true`.
`AccountConfigurator` already assembles more use cases around one shared provider pair than any
other feature (`ARCHITECTURE.md` §6.3); this adds to that rather than creating a parallel structure.

Three screens:

1. **Queue** — pending submissions, newest first, each showing the parsed values and whether the
   barcode already exists in the catalogue.
2. **Review** — one submission: every field editable before approving (a maintainer fixing a typo
   should not have to reject and wait for a resubmission), *Approve* and *Reject with a reason*.
   With no photograph to check against, this is a closer read of the typed values than approval
   will be once *Non-goals*' photo lands — worth flagging to whoever moderates, not just to the
   design record.
3. **Catalogue editor** — search the catalogue by barcode, load an item, edit any field including
   its canonical portions, save. This is the path design 0008 deferred here.

### Use cases

```swift
SubmitFoodItemUseCase            // validate, duplicate-check, write submission
FetchMySubmissionsUseCase        // the fourth displayedResults list
UpdateMySubmissionUseCase        // resubmit after a rejection
FetchPendingSubmissionsUseCase   // the queue
ApproveSubmissionUseCase         // CreateFoodItemUseCase, then delete the submission
RejectSubmissionUseCase          // status + reason
UpdateFoodItemUseCase            // the maintainer's correction of an existing entry
FetchMaintainerClaimUseCase      // the claim
DeleteMySubmissionUseCase        // author withdraws a pending or rejected submission
```

Every one of these is `FirestoreDataProviderProtocol` + `AuthProviderProtocol`, the same shape every
persistence use case in this codebase already has (`ARCHITECTURE.md` §1.1). Nothing here talks to
Storage — see *Non-goals*.

`FetchMySubmissionsUseCase` and `FetchPendingSubmissionsUseCase` both query a collection whose read
rule depends on `resource.data`. Firestore evaluates a `list` against the **query**, not the
returned documents, so each query must carry the filter its rule branch relies on: the author's
query must include `where submitted_by == <own uid>`, or it is denied outright even though every
document it would return is theirs. The maintainer's query is unaffected — `isMaintainer()` does not
reference `resource` and short-circuits the `||`. Worth an emulator case, because the failure mode
is a blanket `permission-denied` that looks like a broken claim rather than a missing filter.

### Localization

New keys, source language `cs`, reached through the hand-written `L10n` enum per
[ADR 0019](../adr/0019-l10n-enum-over-the-string-catalogue.md). A new `L10n.Moderation` namespace
for the panel, plus a handful under `L10n.AddFood` for the author-facing markers:

| Key | cs | en |
|---|---|---|
| `moderation_section_title` | Moderace | Moderation |
| `moderation_queue_title` | Ke schválení | Pending review |
| `moderation_queue_empty` | Žádné položky ke schválení | Nothing to review |
| `moderation_queue_collision` | Tento čárový kód už v katalogu je | This barcode is already in the catalogue |
| `moderation_button_approve` | Schválit | Approve |
| `moderation_button_reject` | Zamítnout | Reject |
| `moderation_reject_reasonPlaceholder` | Důvod zamítnutí | Reason for rejection |
| `moderation_error_reasonRequired` | Uveďte důvod zamítnutí | Enter a reason for the rejection |
| `moderation_editor_title` | Úprava položky | Edit catalogue item |
| `moderation_editor_searchPlaceholder` | Čárový kód | Barcode |
| `moderation_error_alreadyExists` | Položka s tímto kódem už v katalogu je | An item with this code is already in the catalogue |
| `addFood_submission_pending` | Čeká na schválení | Waiting for approval |
| `addFood_submission_rejected` | Zamítnuto | Rejected |
| `addFood_submission_rejectedReason` | Zamítnuto: %@ | Rejected: %@ |
| `addFood_submission_submitted` | Odesláno ke schválení | Submitted for approval |

### File-by-file impact

New:

- `Kalorie/Core/Models/FoodItemSubmissionModel.swift` — `FoodItemSubmissionDomain`,
  `FoodItemSubmissionStatus`, `FoodItemSubmissionError`
- `Kalorie/Core/Models/FoodItemValidation.swift` — the validation lifted out of
  `CreateFoodItemUseCase` so both write paths share one predicate
- `Kalorie/Core/Networking/FireStone/FoodItemSubmissionDTO.swift`
- `Kalorie/Core/UseCases/` — `SubmitFoodItemUseCase`, `FetchMySubmissionsUseCase`,
  `UpdateMySubmissionUseCase`, `FetchPendingSubmissionsUseCase`, `ApproveSubmissionUseCase`,
  `RejectSubmissionUseCase`, `UpdateFoodItemUseCase`, `FetchMaintainerClaimUseCase`
- `Kalorie/Features/Moderation/` — `ModerationConfigurator`, and the three screens from *The panel*
  with a view model each: queue, review, catalogue editor
- `scripts/set-maintainer-claim.js` — the fourth Admin SDK script, beside the two backfills

Changed:

- `Constants.swift` — `foodItemSubmissions`. Per `SETUP.md`, a new collection and its rule block
  land in the same change
- `Kalorie/firestore.rules` — `isMaintainer()`, `validFoodItem()`, the narrowed `foodItems` block,
  the new `foodItemSubmissions` block
- `Kalorie/firestore.indexes.json` — the two composite indexes from *Indexes*
- `CreateFoodItemUseCase.swift` — validation delegates to `FoodItemValidation`; the existence check
  **stays** (see *Duplicate handling*); no longer called from `AddFoodSheetViewModel`
- `AddFoodSheetViewModel.swift` — `onCreateFoodItem` writes a submission instead of a catalogue
  item, so its exhaustive `CreateFoodItemError` switch (`ARCHITECTURE.md` §5.2) becomes a
  `FoodItemSubmissionError` switch; own submissions fetched in `onAppear` and added to
  `displayedResults` as the fourth list; a tapped rejected row opens the pre-filled form
- `AddFoodSheetView.swift` — pending/rejected markers on a result row, the optional
  rejection-reason header on `addCustomFoodItem`
- `AddFoodSheetConfigurator.swift` — the new use case dependencies
- `AccountView.swift` / `AccountViewModel.swift` / `AccountConfigurator.swift` — the claim-gated
  entry point into `ModerationConfigurator`
- `DeleteAccountUseCase.swift` — `foodItemSubmissions` added to `wipeFirestoreData`
- `L10n.swift`, `Localizable.xcstrings`
- `docs/SETUP.md` — the claim runbook and the token-refresh caveat
- `docs/ARCHITECTURE.md` — updated after shipping, since it is the living description of what exists

Tests (XCTest, fakes, `makeSUT()`, per CLAUDE.md — coverage targets the UseCase layer):

- One test file per new use case. `ApproveSubmissionUseCaseTests` **must** cover the collision case
  from *Duplicate handling* — approval refused when the barcode entered the catalogue after the
  submission was filed — since that is the case where a missing guard silently destroys data
- `FoodItemValidationTests`, mirroring `FoodPortionValidationTests`
- `CreateFoodItemUseCaseTests` — adjusted for the extracted validation, existence-check cases kept
- `DeleteAccountUseCaseTests` — the new collection in the wipe
- `AddFoodSheetViewModelTests` — the fourth `displayedResults` list, the submission path, the
  external-fallback suppression noted in *Search integration*

## Alternatives considered

- **Variant A — nobody logs the food until it is approved.** The cleanest model and the worst
  outcome: with a single maintainer, latency is hours to days, so the user standing at the fridge
  with the packaging simply cannot log what they ate. ADR 0011 accepted client writes precisely
  because *"without client write access the catalogue stops growing altogether"*; a flow nobody
  completes has the same effect.
- **Variant D — optimistic publish: keep `allow create` open, add an `approved` flag on
  `foodItems`.** Genuinely attractive — no second collection, submission and correction become the
  same edit on the same document. Rejected on the search cost: hiding unapproved items means
  `where approved == true` in all six of `SearchFoodItemsUseCase`'s concurrent queries, and a
  composite index for each. Filtering client-side is not an option, because `limit(10)` applies
  server-side, so unapproved noise would evict approved results before the client ever sees them —
  the mechanism finding **A2-12** describes. It also leaves the shared catalogue accumulating
  unapproved entries.
- **Variant C — private per-user food items, offered to the catalogue separately.** Rejected: the
  new collection would be `myCreatedMeals` minus ingredients, duplicating a model that already
  exists for very little gain.
- **Keying the submission by barcode instead of a UUID.** Cheaper (deduplication for free, no
  collision query) but `setAsync` replaces rather than creates, so the second user to submit a
  barcode would silently overwrite the first user's submission.
- **A Node CLI in `scripts/` instead of an in-app panel.** The cheapest option by a wide margin —
  the Admin SDK bypasses rules entirely, so no custom claim would be needed at all, and
  `backfill-runner.js` is the pattern. With no packaging photograph to review (*Non-goals*), the
  strongest argument for an in-app screen is gone, and this alternative is worth re-opening if the
  photo stays out of scope for a while. Kept as the chosen direction here mainly because the review
  and edit screens reuse `FoodItemValidation`, the existing form components and `L10n` directly,
  where a CLI would re-type all of that in JS — the same cost
  [ADR 0026](../adr/0026-js-backfill-duplicates-textkit-under-a-shared-fixture.md) already accepts
  once for the backfill scripts. A CLI remains the cheaper choice if this is revisited.
- **A web panel on Firebase Hosting.** Platform-neutral and survives an Android client. Rejected for
  v1: a second client with its own build, auth flow, deployment and a JS reimplementation of the
  validation — and [ADR 0026](../adr/0026-js-backfill-duplicates-textkit-under-a-shared-fixture.md)
  already governs one such duplication with a shared fixture, which is the cost of the second.
- **Retaining approved submissions with `status: approved`.** Rejected: the data is in `foodItems`
  and the retained copy has no reader, while the queue grows without bound.
- **A separate report-a-problem channel for existing items, built now.** Deferred to `TODO.md` — it
  needs somewhere to route reports to, which is this panel.

## Cross-cutting concerns

- **Merge on sign-in (Cross-platform).** `foodItemSubmissions` is top-level, so an anonymous user's
  submissions are not swept up by `MigrateAnonymousDataUseCase`'s per-user collections, and
  rewriting `submitted_by` would require a rule that permits reassigning ownership — which cannot be
  verified server-side. **Submissions are therefore not merged**, the same call
  `ARCHITECTURE.md` §6.2 records for meal types. The loss is bounded: a pending submission still
  gets approved and still reaches the catalogue for everyone; the author merely loses sight of its
  status. One consequence to be aware of rather than fix: the logged entry *does* migrate (it lives
  under `users/{uid}/foodConsumed`), so after signing in the user keeps the food in their diary
  while the submission that would have marked it *pending* is no longer theirs to see. Logging the
  same food again finds nothing locally and falls through to the external OpenFoodFacts search, and
  a second submission for the same barcode is accepted — which the UUID key already permits.
- **Account deletion (Cross-platform).** `DeleteAccountUseCase.wipeFirestoreData` gains a query over
  `foodItemSubmissions where submitted_by == uid` and deletes each — which is why the author holds
  `delete` on their own submission. Already-approved items stay: they are shared catalogue content,
  no longer the user's data. A privacy obligation, framed as design 0006 framed the equivalent
  `myCreatedMeals` gap. No Storage cleanup here in v1 — see *Non-goals*; that obligation lands
  alongside the photo when it does.
- **Offline.** Submitting is an ordinary Firestore write with no Storage dependency in v1, so it
  behaves exactly like every other write here — served from the offline queue, same as logging.
- **`firestore.rules` (Backend).** Changes; must be tested in the Rules Playground or the emulator
  **before** deploying, per `SETUP.md`. Design 0006 and design 0008 both carry the standing note
  that the authentication review once found a rules regression in exactly this area. No
  `storage.rules` in v1 — see *Non-goals*.
- **Second platform (Backend / Cross-platform vs. iOS).** The submission document shape, the two
  identities, the status lifecycle, the rules and the claim are `Backend`. The
  log-before-approval behaviour and the duplicate rule are `Cross-platform` — a client that logs a
  pending item differently will disagree about what `food_item_id` means. The panel's screens, the
  claim read and the search-row markers are `iOS`, and an Android client shipping without an admin
  panel is an accepted consequence of hosting the panel here.
- **Anonymous users** may submit like anyone else — anonymous sign-in is authenticated sign-in
  ([ADR 0001](../adr/0001-anonymous-firebase-auth-as-device-identity.md)) — subject to the merge
  caveat above.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| The claim is checked only in the UI, not in rules | Any modified client overwrites the shared catalogue for every user — the exact risk ADR 0011 accepted temporarily | Rules are the only defence; the claim gates rendering alone. Playground and emulator cases for both `foodItems` writes and the author's `update` clause, before deploy |
| `update` on `foodItems` without field validation | One malformed document breaks decoding for every user, not just its author | `validFoodItem()` shared by `create` and `update`; no path writes the document without it |
| Approval is two writes with no transaction | A submission survives for a barcode now in the catalogue | Shows as a collision on the next pass and is cleared by hand; idempotent and visible |
| Approving a submission whose barcode entered the catalogue in the meantime | `setAsync` replaces, and the maintainer now holds `update`, so an existing entry — canonical portions included — is silently overwritten | `CreateFoodItemUseCase` keeps its own existence check (*Duplicate handling*); the queue's collision marker is an affordance, not the guard. Explicitly named as a required test case in *File-by-file impact* |
| A rejected submission leaves `food_item_id` pointing at a barcode that never becomes a document | A logged entry permanently without a catalogue link | Accepted. Macros are denormalised and stay correct; the favourite button hides itself with no code change |
| Single maintainer, no SLA on the queue | Submissions go stale, users stop bothering | Structurally mitigated: logging never waits for approval, so a slow queue degrades discovery for others rather than blocking the author |
| No packaging photo to check a submission against | The maintainer approves typed values with nothing to verify them against — easier to wave through a typo than with today's own-entry form, where the typist and the approver were the same person | Accepted for v1 — see *Non-goals*. The review screen stays editable so a spotted error is fixed in place rather than bounced back |
| The admin section ships in the production binary | App Store guideline 2.3.1; a reviewer cannot exercise the feature | Low in practice — a server-gated admin section is ordinary. Noted, not designed around |
| Submissions are not merged on sign-in | An author loses sight of a pending submission after signing in | Accepted, with the meal-type precedent. The submission itself is unaffected and still reaches the catalogue |

## Outcome

Shipped as designed, with one shared-code addition not anticipated here: `CreateFoodItemUseCase`'s
validation, `UpdateFoodItemUseCase`'s validation and the *add a new food* form's validation all
needed the identical predicate, so it was extracted into `FoodItemValidation` and each use case
wraps its result in its own error enum (`CreateFoodItemError`, `FoodItemSubmissionError`,
`UpdateFoodItemError`) rather than sharing one — each caller's exhaustive `switch` keeps its own
alert strings. The three moderation screens' field list is a fourth shared piece,
`FoodItemFormFields`, extracted for the same reason (the same ~13 fields, three call sites).

The data-provider protocol gained one new query shape,
`loadAsync(from:where:isEqualTo:orderBy:descending:)`, for the two `foodItemSubmissions` queries
(the maintainer's queue, the author's own list) — neither existing shape covered an equality filter
combined with an order.

Everything else matches the design: the two identities, the write-once-to-write-editable rules
change, the log-before-approval behaviour, the duplicate-handling guard in
`ApproveSubmissionUseCase` (covered by its required collision test), and the panel as three pushed
screens under `AccountView` rather than a separate sheet — pushed to match how
`MyCreatedMealEditorView` is already reached from `MealTypeSheetView`, since `AccountView` already
owns a `NavigationStack`. See [ARCHITECTURE.md § 7](../ARCHITECTURE.md#7-catalogue-moderation) for
the living description and [ADR 0027](../adr/0027-catalogue-writes-require-a-maintainer-claim.md)
for the decision this took effect for.
