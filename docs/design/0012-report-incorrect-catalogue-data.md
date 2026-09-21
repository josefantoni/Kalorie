# Design: Reporting incorrect data on a catalogue item

- **Status:** Implemented in `aad2014`
- **Scope:** Backend, Cross-platform, iOS
- **Date:** 2026-09-17

## Context and scope

A user who spots a wrong value on a shared `foodItems` entry has no way to say so.

This is a consequence of a decision, not an oversight.
[ADR 0027](../adr/0027-catalogue-writes-require-a-maintainer-claim.md) closed catalogue writes
behind a maintainer claim and recorded the gap in its own Consequences: *"Ordinary users lose the
ability to correct the catalogue at all. Today anyone can overwrite a wrong entry — badly, but they
can. After this, only the maintainer can, and a user who spots an error has no in-app channel to
report it."* [Design 0009](0009-catalogue-moderation.md) listed the channel as a Non-goal and named
the reason it was deferred rather than dropped: *"it needs somewhere to route reports to, which is
this panel."*

The panel now exists — `ModerationQueueView`, `ModerationReviewView` and
`ModerationCatalogueEditorView`, the last of which is already the maintainer's correction path for
an existing entry. The dependency that deferred this work is therefore discharged, and what remains
is the two halves `TODO.md` names: an affordance on the item's own screen, and a queue for reports
in the panel beside the existing submissions queue.

The boundary of this document: it adds a **signal** from user to maintainer. It does not add a
second way to write to `foodItems`. Correcting an entry stays exactly what design 0009 made it —
`UpdateFoodItemUseCase` called from the catalogue editor by a maintainer.

## Goals

- A user looking at a catalogue food can report that its data is wrong, in one tap plus a sentence.
- The maintainer sees reports in the panel, grouped per catalogue item, and reaches the existing
  catalogue editor from a report.
- The same user reporting the same item twice does not produce two queue entries, while two
  different users reporting the same item do — the count is the maintainer's priority signal.
- Nothing about `foodItems` writes, `SearchFoodItemsUseCase` or the submission flow changes. No
  new index on `foodItems`, so finding **A2-12** is not made worse.
- The reports queue itself needs no composite index (see *No status field*); one composite index
  is still needed on `foodItemReports` for the account-wipe query, the same cost `foodItemSubmissions`
  already pays for the same reason.
- `foodItemSubmissions` is not touched: neither its shape, its rules, nor either of the two
  existence checks design 0009 calls out in *Duplicate handling*.

## Non-goals

- **A suggested replacement value on the report.** The report says *"the fat is wrong"*, not
  *"the fat is 12.4"*. A report carrying a value the maintainer could apply in one tap is a
  submission against an existing barcode, which is the thing *Alternatives considered* rejects
  below and the thing ADR 0027 exists to prevent. The maintainer opens the entry, compares it
  against the packaging, and corrects it — the same judgement the review screen already asks for.
- **Structured "which field is wrong" metadata.** Free text only for v1. The maintainer has to open
  the item and compare it against the packaging either way, so a field picker buys ordering in the
  panel and nothing else. Additive later: the DTO gains an optional array, the same shape every
  other DTO here uses for a field absent on older documents (`ARCHITECTURE.md` §1.3).
- **Notifying the author of the outcome.** Every read in this app is one-shot — nothing uses
  `addSnapshotListener` (`ARCHITECTURE.md` §1.5) — and design 0009 already rejected FCM for the
  same reason. The user learns the outcome by seeing the corrected value the next time they open
  the food.
- **Reporting an OpenFoodFacts result or a user's own created meal.** Neither is ours to correct:
  an external item is surfaced and never imported
  ([ADR 0012](../adr/0012-external-food-is-surfaced-never-imported.md)), and a created meal is
  private, owner-only data its own owner can already edit.
- **Reporting a missing item.** That is the submission flow, which already exists.
- **Deleting a catalogue entry in response to a report.** `delete` on `foodItems` stays denied for
  everyone including the maintainer, exactly as design 0009 left it.
- **A resolution audit trail.** A resolved report is deleted, not retained with a status. See
  *No status field* below.

## Design

### A report is a signal, not a write path

The tempting shape is to reuse `foodItemSubmissions` — a report becomes a submission against an
existing barcode, the panel already has a queue, and `FoodItemFormFields` already edits all
thirteen fields. It is rejected for a specific reason rather than a stylistic one, and the reason
is worth stating here rather than only in *Alternatives considered*, because it is the constraint
that shapes everything else in this document:

`ApproveSubmissionUseCase` approves through `CreateFoodItemUseCase`, whose server-only existence
check is a **guard against silently overwriting an existing catalogue entry** — canonical portions
included, which design 0008 made write-once. Design 0009's *Duplicate handling* and ADR 0027's
Consequences both single it out: *"Removing it as redundant would destroy data with no error
surfaced."* A correction's whole purpose is to overwrite an existing entry, so routing corrections
through that path means defeating the guard for some writes and not others. That is precisely the
mistake both records warn against.

So reports get their own collection, and the correction itself keeps happening where design 0009
put it.

### The report document

```
foodItemReports/{barcode}_{reportedBy}
```

```swift
struct FoodItemReportDTO: Codable {
    let barcode: String        // "barcode"
    let reportedBy: String     // "reported_by"
    let reason: String         // "reason"
    let reportedAt: TimeInterval  // "reported_at"
}
```

Four fields, and the id carries the rest. Notes on the shape, each with a reason:

- **The document id is `{barcode}_{reportedBy}`, not a UUID.** This is the one place this document
  deliberately departs from the convention design 0009 established, and the departure is the point.
  A UUID key buys *deliberate duplication* — design 0009 wanted two users submitting the same
  barcode to produce two queue entries, because `setAsync` replaces and a barcode key would
  silently lose one side. Reports invert that requirement. A submission costs the user thirteen
  fields of typing; a report costs one tap, which makes an unbounded write a spam vector rather
  than a feature. A deterministic id makes a user's second report on the same item **replace**
  their first, so one user can never occupy more than one queue slot per catalogue item, and
  Firestore rules can enforce it without counting documents (which rules cannot do). Two *different*
  users still produce two documents, which is the signal the maintainer wants.
- **`id` is not mirrored as a field.** `foodItems` and `foodItemSubmissions` both mirror it because
  their rules compare against it (`ARCHITECTURE.md` §1.3). Here the rule compares the id against
  `barcode + '_' + request.auth.uid`, both of which it already has, so a mirrored field would have
  no reader.
- **No denormalised food name or nutrition snapshot.** Every other collection referencing a food
  carries a snapshot ([ADR 0009](../adr/0009-denormalised-nutrition-snapshots.md)), and this one
  deliberately does not: the maintainer needs the item's **current** state to correct it, not its
  state when the report was filed. The panel resolves names through
  `loadAsync(from:whereDocumentIdIn:)` — see *The panel*.
- **`reason` is required and non-empty**, capped in the rules. A report with no text is a downvote,
  and the maintainer cannot act on a downvote.

### No status field

A report exists while it is open, and the maintainer **deletes** it once the item is corrected or
the report is judged wrong. There is no `resolved` status, for the reason design 0009 gave when it
rejected retaining approved submissions: *"the data is in `foodItems` and the retained copy has no
reader, while the queue grows without bound."*

Two things follow, both worth accepting explicitly:

- The maintainer has no record of having dismissed a report, so a user who reports the same item
  again after a dismissal reappears in the queue. Acceptable at one maintainer; the alternative is
  a status field and a second index for no reader.
- **The queue itself needs no composite index.** `loadAsync(from:orderBy:descending:limit:)` over
  the whole collection is a single-field sort, and Firestore indexes single fields automatically.
  The *"already reported"* read is a direct `loadAsync(id:from:)` by the composed key, not a query,
  so it needs no index either. **One composite index is still needed**, though: the account-wipe
  query (`reported_by ==`, ordered by `reported_at`) combines an equality filter on one field with
  a sort on a different one, which Firestore never auto-indexes — the exact reason
  `foodItemSubmissions` already carries two composite indexes for its own wipe and queue queries.
  `firestore.indexes.json` gains one entry for it, a fraction of what `foodItemSubmissions` needed.

### Security rules

```
match /foodItemReports/{reportId} {
  allow read: if isMaintainer() ||
    (resource == null && reportId.matches('.*_' + request.auth.uid)) ||
    (resource != null && resource.data.reported_by == request.auth.uid);

  allow create: if request.auth != null &&
    reportId == request.resource.data.barcode + '_' + request.auth.uid &&
    request.resource.data.reported_by == request.auth.uid &&
    request.resource.data.barcode.matches('[0-9]{8}|[0-9]{12}|[0-9]{13}') &&
    request.resource.data.reason is string &&
    request.resource.data.reason.size() > 0 &&
    request.resource.data.reason.size() <= 500 &&
    request.resource.data.reported_at is number;

  allow delete: if isMaintainer() || resource.data.reported_by == request.auth.uid;
}
```

- **`create` covers the replace case too.** A user re-reporting the same item writes the same
  document id, which Firestore treats as `create` when the document is absent and `update` when it
  is present. Granting `update` separately with the same conditions would duplicate the block, so
  instead the author's re-report is served by `allow create` when the earlier report is gone and by
  a **deliberately absent** `update` otherwise — meaning a second report while the first is still
  open is refused by the rules. The client therefore reads the existing report first and shows the
  user that they have already reported this item (see *The affordance*), rather than writing
  blind. This keeps the rule block to three clauses and the "one slot per user per item" invariant
  enforced server-side rather than assumed.
- **`read` needs a branch for a report that does not exist yet.** For an absent document `resource`
  is `null`, so `resource.data.reported_by` cannot be evaluated and the read would be denied — which
  is exactly the case the client's *already reported?* read hits on a first report. The branch
  `resource == null && reportId.matches('.*_' + request.auth.uid)` turns that into a not-found and
  lets a user probe only ids ending in their own uid. It shipped with the implementation and is now
  covered by `firestore-rules-tests/`.
- **The `reason` length cap belongs in the rules, not only in the client.** It is the only
  server-side bound on how much a client can write into this collection.
- **`delete` for the author** exists for account deletion, not as a product feature, exactly as the
  same clause on `foodItemSubmissions` does. The client exposes no withdraw button in v1.
- The barcode format pattern is the same literal the `foodItems` and `validFoodItem` blocks already
  use. It is duplicated rather than extracted, matching how those two already spell it out twice.

`validFoodItem()` is not involved — a report carries no food data.

### The affordance

A report MUST be offered only for a food whose kind is `.catalogue`
([ADR 0025](../adr/0025-food-item-kind-discriminates-entry-origin.md)). The gate already exists on
both screens that show one food on its own:

| Screen | Gate already in the code |
|---|---|
| `FoodConsumedDetailView` | `catalogueItem != nil`, already used by `canShowFavouriteButton` |
| `FoodQuantityView` | `item.kind == .catalogue`, already used to gate personal portions |

`FoodConsumedDetailView` is the primary one — a wrong value is usually noticed after logging, with
the day's macros in view — but both get it, because a user who spots the error while choosing the
amount should not have to log the food first to report it.

Two view models sharing one optimistic action is a shape this project has twice already:
`FavouriteToggling` ([ADR 0017](../adr/0017-optimistic-favourite-toggle-shared-by-protocol-extension.md))
and `NutritionLabelPrefilling`. A third protocol extension, `FoodItemReporting`
(`Core/Utils/`), holds the reason-entry state, the submit call and the alert handling, so neither
view model carries a copy.

**Placement:** a `ToolbarItem` menu (`ellipsis`), not a third control in the header row. The header
row already carries the food's name and the favourite heart (`ARCHITECTURE.md` §4.6 records that
the two screens place that button identically on purpose), and reporting is a once-a-year action —
putting it beside a once-a-day one mis-ranks both. Tapping *Report incorrect data* presents an
alert with a text field for the reason and a confirm button, following
[ADR 0018](../adr/0018-per-feature-error-alerts-with-no-global-handler.md)'s per-feature alert
convention rather than introducing a sheet for one text field.

The protocol extension reads the user's existing report for this barcode
(`loadAsync(id:from:)`, one document by key) when the menu is opened, and renders *Already
reported* — disabled — instead of the action when one exists. This is what keeps the client from
attempting the write the rules refuse (see *Security rules*).

### The panel

`ModerationQueueView` gains a **second queue**, reached the same way the catalogue editor already
is: a `NavigationLink` from `AccountView`'s maintainer section, beside the existing one. Per
`TODO.md`'s own phrasing — *"a queue for reports in the panel, beside the existing submissions
queue"* — it is a sibling screen, not a second section inside the submissions queue, whose rows
mean something different and whose row tap goes somewhere else.

`ModerationReportsView` / `ModerationReportsViewModel`:

1. `FetchFoodItemReportsUseCase` loads the collection ordered by `reported_at` descending, limited
   to `Constants.Firestore.reportsPageLimit` (50, matching the limit `favouriteFoods` and
   `myCreatedMeals` already use).
2. Reports are **grouped by `barcode`** in the view model — a computed property over the loaded
   list, the same way `DashboardViewModel.groupedFoods` derives its sections. One row per catalogue
   item, showing the item's name, how many users reported it, and their reasons.
3. Names come from one call to `FetchFoodItemByBarcodeUseCase.callAsFunction(barcodes:)` for the
   unique barcodes, reusing the batched lookup `ModerationQueueViewModel` already calls for its own
   collision check. Chunking past `Constants.Firestore.inQueryLimit` (30) is not the view model's
   concern — `FirestoreDataProvider.loadAsync(from:whereDocumentIdIn:)` already chunks internally, so
   a page of 50 reports resolving to more than 30 unique barcodes needs no special handling here. A
   barcode that resolves to nothing renders as the bare barcode — the entry was deleted outside the
   app, or the report predates a barcode change.
4. A row pushes `ModerationCatalogueEditorView`, pre-loaded with that barcode, so the maintainer
   lands on the existing correction screen with the item already fetched. This is the whole reason
   the report needs no editing UI of its own.
5. Resolving is a swipe-to-delete on the row, deleting every report for that barcode through
   `DeleteFoodItemReportUseCase`. Deletion is per document, so a partial failure leaves the rest —
   the same visible, idempotent shape design 0009 accepted for approval's two non-transactional
   writes.

The catalogue editor is reached two ways after this — its existing toolbar button on the
submissions queue (search by barcode) and a report row (barcode supplied). Its view model gains an
optional barcode to load on appear; nothing else about it changes.

### File-by-file impact

New:

- `Core/Models/FoodItemReportModel.swift` — `FoodItemReportDomain`, and
  `FoodItemReportError` for the client-side validation (empty reason, reason too long, already
  reported)
- `Core/Networking/FireStone/FoodItemReportDTO.swift` — the DTO above, `asDomain()` plus the
  domain-taking `init`, matching every other DTO in the project
- `Core/UseCases/SubmitFoodItemReportUseCase.swift` — validates, resolves `userId`, `setAsync` with
  the composed id
- `Core/UseCases/FetchMyFoodItemReportUseCase.swift` — `loadAsync(id:from:)` for the composed id,
  returns `FoodItemReportDomain?`, used by the affordance's *Already reported* state
- `Core/UseCases/FetchFoodItemReportsUseCase.swift` — the maintainer's queue
- `Core/UseCases/DeleteFoodItemReportUseCase.swift` — one document by id
- `Core/Utils/FoodItemReporting.swift` — the protocol extension shared by the two view models
- `Features/Moderation/ModerationReportsView.swift` + `ModerationReportsViewModel.swift`

Changed:

- `Constants.swift` — `foodItemReports`, `reportsPageLimit`, and the reason length cap shared with
  the rules. Per `SETUP.md`, a new collection and its rule block land in the same change
- `backend/firestore.rules` — the `foodItemReports` block. **No** change to `isMaintainer()`,
  `validFoodItem()`, `foodItems` or `foodItemSubmissions`
- `backend/firestore.indexes.json` — unchanged, and that is a claim to verify rather than assume:
  run the queue query against the emulator and confirm Firestore does not ask for an index
- `FoodConsumedDetailViewModel.swift` / `FoodConsumedDetailView.swift` — conform to
  `FoodItemReporting`, the toolbar menu
- `FoodQuantityViewModel.swift` / `FoodQuantityView.swift` — the same two changes
- `FoodConsumedDetailConfigurator.swift` / `AddFoodSheetConfigurator.swift` — the two new use case
  dependencies
- `ModerationConfigurator.swift` — assembles the reports screen and passes a barcode into the
  catalogue editor
- `ModerationCatalogueEditorViewModel.swift` — an optional barcode loaded on appear
- `AccountView.swift` — the second `NavigationLink` in the maintainer section
- `DeleteAccountUseCase.swift` — `foodItemReports where reported_by == uid` added to
  `wipeFirestoreData`
- `L10n.swift`, `Localizable.xcstrings`
- `docs/ARCHITECTURE.md` — §1.2, §1.4, §1.6 and §7 after shipping, since it is the living
  description of what exists
- `docs/README.md` — the design doc index

Tests (XCTest, fakes, `makeSUT()`, per CLAUDE.md — coverage targets the UseCase layer):

- One test file per new use case. `SubmitFoodItemReportUseCaseTests` **MUST** cover that the written
  document id is exactly `{barcode}_{userId}`, since that string is what the rules enforce and a
  mismatch fails every write with `permissionDenied` rather than failing visibly in a test
- `ModerationReportsViewModelTests` — grouping by barcode, and ordering groups by report count
- `DeleteAccountUseCaseTests` — the new collection in the wipe
- `FoodConsumedDetailViewModelTests` / `FoodQuantityViewModelTests` — the `.catalogue` gate, and
  that an already-reported item offers no second report

## Alternatives considered

- **Reuse `foodItemSubmissions` with a `correction` status.** No new collection, no new rules, and
  the review screen already edits every field. Rejected because it breaks both of design 0009's
  *Duplicate handling* checks in opposite directions: `SubmitFoodItemUseCase` refuses a barcode
  already in the catalogue and would need that refusal conditionally lifted, and
  `CreateFoodItemUseCase`'s existence check — the guard against silently overwriting canonical
  portions, which ADR 0027 says must not be removed — would have to be bypassed for exactly the
  writes whose purpose is to overwrite. Cheaper on paper, and the one change in this area that
  ADR 0027 explicitly warns destroys data with no error surfaced.
- **A UUID-keyed report, matching `foodItemSubmissions`.** Consistent with the neighbouring
  collection, and rejected for the reason design 0009 chose UUIDs in the first place, read the other
  way round: it buys deliberate duplication, which a one-tap write does not want. Rules cannot count
  documents, so nothing would bound one user's reports on one item.
- **A suggested value on the report, applied by the maintainer in one tap.** The most useful version
  for the maintainer and the one that reopens ADR 0027's risk: a report carrying values is a
  submission, and applying it in one tap is a catalogue write whose validation lives in the client
  that composed it. Rejected for v1. If it is revisited, it belongs in `foodItemSubmissions` with
  the existence check made a first-class *update-or-create* decision, not bolted onto a report.
- **A `resolved` status instead of deleting.** Gives the maintainer a dismissal record and prevents
  a re-report from reappearing. Rejected on the same grounds design 0009 rejected `status: approved`
  — no reader, unbounded growth — and it would add the composite index this design otherwise avoids.
- **A denormalised item name on the report.** One fewer query in the panel. Rejected: the maintainer
  needs the item's current values, so the panel reads `foodItems` regardless, and a stale cached
  name on a corrected item is a second thing to be wrong.
- **A report affordance on the search result row (`FoodItemRow`).** Reaches the user one step
  earlier. Rejected: the row shows a name and a heart and nothing the user could have judged wrong
  yet — the macros are on the next screen. Reporting from a row would mostly report the wrong item.
- **A structured field picker instead of free text.** Deferred, not rejected on principle — see
  *Non-goals*; it is additive once there is evidence the free text is hard to act on.
- **No in-app channel at all; an e-mail or App Store review link.** The cheapest option, and how
  the gap is handled today by accident. Rejected because the maintainer cannot connect a review to
  a barcode, which is the one piece of information that makes a report actionable.

## Cross-cutting concerns

- **Privacy / account deletion (Cross-platform).** `foodItemReports` is top-level and keyed partly
  by uid, so `DeleteAccountUseCase.wipeFirestoreData` MUST delete the user's reports, exactly as it
  already does for `foodItemSubmissions` — which is why the author holds `delete` on their own
  report. A `reason` is user-authored free text and may contain anything the user typed, so leaving
  it behind after account deletion is a privacy obligation, not a tidiness one.
- **Merge on sign-in (Cross-platform).** Reports are **not** migrated across an anonymous →
  signed-in merge, for the same reason submissions are not: `reported_by` cannot be reassigned in a
  way the rules can verify (`ARCHITECTURE.md` §6.2). The loss is smaller here than for submissions —
  the report still reaches the maintainer and the item still gets corrected; the author merely loses
  the *Already reported* state and could report the same item again under the new uid, producing a
  second document. Bounded and harmless.
- **Anonymous users** may report like anyone else — anonymous sign-in is authenticated sign-in
  ([ADR 0001](../adr/0001-anonymous-firebase-auth-as-device-identity.md)). This is the same exposure
  the submission flow already carries, and the deterministic id bounds it per install rather than
  per account: clearing the app's data yields a new anonymous uid and therefore a new report slot.
  Accepted, and the reason *Risks* keeps abuse on the list rather than calling it solved.
- **Offline.** Submitting a report is an ordinary Firestore write with no Storage dependency, so it
  behaves like every other write here — served from the offline queue. The *Already reported* read
  goes through Firestore's default source resolution and may serve a stale cache
  (`ARCHITECTURE.md` §1.5), so offline it can offer a report the rules will later refuse. The queued
  write then fails silently against the absent `update` clause. Acceptable: the user's report is
  already recorded server-side in that case, which is the outcome they wanted.
- **`firestore.rules` (Backend).** Changes, and MUST be tested in the Rules Playground or the
  emulator **before** deploying, per `SETUP.md`. Design 0006, 0008 and 0009 all carry the standing
  note that the authentication review once found a rules regression in exactly this area. The cases
  that matter: the composed-id clause (a client writing `{barcode}_{someoneElsesUid}` must be
  refused), the reason length cap, a non-maintainer reading someone else's report, and a second
  report while the first is open.
- **Second platform (Backend / Cross-platform vs. iOS).** The collection shape, the composed id,
  the absent `update` clause and the rules are `Backend`. The one-report-per-user-per-item invariant
  and the `.catalogue`-only restriction are `Cross-platform` — a client that keys reports
  differently reopens the spam vector for everyone. The affordance's placement, the protocol
  extension and the panel are `iOS`; an Android client shipping without the panel is the same
  accepted consequence design 0009 recorded.
- **Relation to finding A1-7.** A correction made from a report is still a correction, so it remains
  invisible to favourites and saved meals holding a stale snapshot. This design does not close
  **A1-7** and does not make it worse — but it does raise the frequency of the event A1-7 is about,
  since corrections stop depending on the maintainer noticing an error personally. The trigger A1-7
  wants still has exactly one place to fire from, `ModerationCatalogueEditorViewModel.onSaveTapped`,
  and is still not built. Deliberately kept out of scope here rather than bundled.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Report spam — one tap per item, any authenticated user including anonymous | The queue fills with noise and the maintainer stops reading it | The composed document id bounds one user to one report per item, enforced in the rules, and the absent `update` clause stops a re-report while the first is open. Not fully solved: a new install is a new uid and therefore a new slot. Revisit with App Check if it happens |
| Free-text `reason` is unbounded user content in a collection the maintainer reads | Abusive or injected text rendered in the panel; unbounded write size | Length capped in the rules, not only the client; rendered as plain `Text`, never interpolated into anything that interprets markup; deleted with the account |
| The composed id is built in two places — the client writes it, the rules verify it | Every write fails with `permissionDenied` and the client cannot tell it apart from an expired session | One helper composes the id, used by both the submit and the fetch-mine use cases, and `SubmitFoodItemReportUseCaseTests` asserts the exact string. Named as a required test case in *File-by-file impact* |
| Reports accumulate for a barcode absent from `foodItems` | Rows the maintainer cannot act on, forever | The row renders the bare barcode and stays swipe-deletable, so an unactionable report is still clearable |
| Deleting a barcode's reports is N non-transactional writes | A partial failure leaves some reports behind | Idempotent and visible — the remaining ones show on the next pass, the same shape design 0009 accepted for approval |
| The account-wipe composite index is missing from `firestore.indexes.json` at deploy time | The wipe query fails for a user with a report, so `DeleteAccountUseCase` fails and the account cannot be deleted | Added in the same change as the rules, matching the `foodItemSubmissions` precedent; verified against the emulator before deploying |

## Outcome

Shipped as designed, per commit `aad2014` — the `foodItemReports` collection, its rules and
composite index, the four use cases, `FoodItemReporting`, the toolbar affordance on both screens
and `ModerationReportsView` all match this document; see `ARCHITECTURE.md` § 1.2/1.4/1.6 and § 7.6.
Two corrections were made to this document during implementation rather than after, since neither
had shipped yet: the account-wipe query needs its own composite index after all (a query combining
an equality filter with a sort on a different field is never covered by Firestore's automatic
single-field indexing, the same reason `foodItemSubmissions` already pays for two), and the queue's
name lookup needs no manual chunking, since `FirestoreDataProvider.loadAsync(from:whereDocumentIdIn:)`
already chunks past `Constants.Firestore.inQueryLimit` internally. Both are reflected in the *Goals*
and *No status field* sections above rather than left as stale claims.

The Rules Playground / emulator verification this document calls out for the new rule block
(*Cross-cutting concerns*) is still owed and is not tracked by any automated check.
