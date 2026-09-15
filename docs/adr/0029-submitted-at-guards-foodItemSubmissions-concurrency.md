# 0029. `submitted_at` guards against concurrent writes to a `foodItemSubmissions` document

- **Status:** Accepted
- **Scope:** Backend, Cross-platform
- **Date:** 2026-09-13

## Context

Two races existed in the moderation flow from [design 0009](../design/0009-catalogue-moderation.md),
neither one caught by [ADR 0028](0028-foodItemSubmissions-update-rule-validates-the-maintainer-branch.md),
which narrowed *what* a write could change but not *when* it was allowed to happen:

- **Reject race.** `RejectSubmissionUseCase` reads a submission once, when the maintainer opens the
  review screen, and later overwrites the whole document with `setAsync`. If the author resubmits
  the same (still-`pending`) submission with edited values while the screen stays open, the
  maintainer's reject silently replaces the author's edit with the stale reviewed values plus
  `status: rejected` — the rule from ADR 0028 does not catch this, because `status` was `pending`
  both before and after the resubmit.
- **Approve race.** `ApproveSubmissionUseCase` had no read of its own — it created a `foodItems`
  document straight from the maintainer's (possibly stale) form values. The same resubmit-while-open
  scenario let a maintainer approve outdated macros into the shared catalogue, and the author's
  newer edit was lost the moment the submission document was deleted.
- **Barcode race, `update`'s author branch.** ADR 0028 locked the maintainer's `update` branch to an
  unchanged `barcode`, but not the author's own resubmit branch. `AddFoodSheetViewModel` already
  disables the barcode field while resubmitting, but nothing in `firestore.rules` enforced it — a
  client bypassing that UI could resubmit under a different barcode than the one refused originally
  as a duplicate, undetected by `SubmitFoodItemUseCase`'s own duplicate check (which only runs once,
  before the first submission).

## Decision

`submitted_at` — already present on every `FoodItemSubmissionDTO` and already bumped by
`FoodItemSubmissionWriter.write` on every resubmit — doubles as an optimistic-concurrency token. No
new field, no schema change.

**Rules.** The maintainer's `update` branch additionally requires
`request.resource.data.submitted_at == resource.data.submitted_at`: a reject can only succeed if the
document's `submitted_at` is still what the maintainer read when the review screen opened. The
author's own resubmit branch gains the same `barcode` equality ADR 0028 already gave the maintainer
branch:

```
allow update: if (isMaintainer()
    && request.resource.data.submitted_by == resource.data.submitted_by
    && request.resource.data.barcode == resource.data.barcode
    && request.resource.data.submitted_at == resource.data.submitted_at
    && request.resource.data.status == 'rejected'
    && request.resource.data.reject_reason is string
    && request.resource.data.reject_reason.size() > 0
    && validFoodItem(request.resource.data.item))
  || (resource.data.submitted_by == request.auth.uid
      && request.resource.data.submitted_by == resource.data.submitted_by
      && request.resource.data.barcode == resource.data.barcode
      && request.resource.data.status == 'pending'
      && validFoodItem(request.resource.data.item));
```

**`RejectSubmissionUseCase`.** The existing `permissionDenied` re-read (added for ADR 0028's
predecessor to distinguish "already resolved" from "expired session") now also compares
`submitted_at`: unchanged means the original denial reason must be something else and is rethrown as-
is; the document gone entirely means `RejectSubmissionError.alreadyResolved`; `submitted_at` moved
forward means `RejectSubmissionError.changedSinceReview` — a new case, so the caller can tell an
author's concurrent edit apart from someone else finishing the review first.

**`ApproveSubmissionUseCase`.** Gains a read it did not have before: before calling
`CreateFoodItemUseCase`, it re-reads the submission and compares `submitted_at` to the value the
caller reviewed (the protocol therefore takes the whole `FoodItemSubmissionDomain`, not just its
`id`, so it has that value to compare against). Missing → `ApproveSubmissionError.alreadyResolved`;
changed → `ApproveSubmissionError.changedSinceReview`. Approval only proceeds once both checks pass.
Neither error is rules-enforced on approval's own writes — `create` on `foodItems` and `delete` on
`foodItemSubmissions` carry no field to compare against — so this half of the guard is client-side
only, same trust boundary the maintainer claim itself already sits behind
(design 0009 § *Authorization*: "a UI gate, not a defence"). What rules do still enforce is that the
approved `item` passes `validFoodItem`, unchanged from before.

## Consequences

- A maintainer can no longer reject over an edit the author resubmitted after the review screen
  opened, and cannot approve a submission from stale form values under the same circumstance — both
  now fail loud (`changedSinceReview`) instead of silently discarding the newer data.
- The author's resubmit branch is now barcode-locked in rules, not just in the form UI, closing the
  gap ADR 0028 left on that branch.
- `ApproveSubmissionUseCaseProtocol.callAsFunction` takes `submission: FoodItemSubmissionDomain`
  instead of `id: String` — every caller and fake was updated with this record.
- `ApproveSubmissionUseCase`'s own delete-after-create failure (create succeeds, the queue-cleanup
  delete does not) is unchanged from design 0009's own accepted risk: it is logged and swallowed
  rather than surfaced, since the catalogue write already succeeded and the stray submission
  resurfaces as a collision on the queue's next load. This record does not revisit that acceptance —
  see design 0009's Risks table.
- Any second client implementing this flow must read and carry `submitted_at` through the same way,
  or it loses this guard silently rather than failing loud.
