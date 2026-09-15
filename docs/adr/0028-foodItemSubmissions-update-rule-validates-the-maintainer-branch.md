# 0028. The `foodItemSubmissions` update rule validates the maintainer branch too

- **Status:** Accepted
- **Scope:** Backend
- **Date:** 2026-09-12

## Context

[Design 0009](../design/0009-catalogue-moderation.md) specified the `foodItemSubmissions` `update`
rule with two branches — the author resubmitting a rejected item, and the maintainer resolving one
— and only validated the author's branch:

```
allow update: if isMaintainer()
    || (resource.data.submitted_by == request.auth.uid
        && request.resource.data.submitted_by == resource.data.submitted_by
        && request.resource.data.status == 'pending'
        && validFoodItem(request.resource.data.item));
```

The maintainer branch was left as a bare `isMaintainer()`, with no `validFoodItem` check and no
constraint on `status` or any other field — unlike `foodItems`, where `validFoodItem` gates
`create` **and** `update` identically for the same claim holder. `RejectSubmissionUseCase`, the
maintainer's only write through this rule (`ApproveSubmissionUseCase` deletes the submission
rather than updating it), always writes a structurally valid `item`, an unchanged `barcode` and
`submitted_by`, `status: 'rejected'`, and a non-empty `reject_reason` — but nothing in the rule
required any of that. A client holding a maintainer token (compromised, modified, or a replayed
token) could set `status` to an arbitrary value, corrupt `item`, or reassign `submitted_by`, and
Firestore would accept it.

Separately, `validFoodItem` validated only the nine required numeric fields and three optional
ones — it never checked that `data.id` (the barcode) matches the format `foodItems` enforces on
its own document id (`itemId.matches('[0-9]{8}|[0-9]{12}|[0-9]{13}')`). For a top-level `foodItems`
write this was harmless, because a separate check already ties `request.resource.data.id` to
`itemId`, which is itself format-checked. `foodItemSubmissions`' embedded `item.id` has no such
document-id tie-in — nothing checked its format on `create` or `update`, so a submission (and,
were the maintainer branch ever exploited, the catalogue entry approved from it) could carry a
malformed barcode.

Both gaps were introduced by design 0009's own rule text, not by drift between the code and the
design — the deployed rules matched the design exactly. Per `docs/README.md`, that design doc is
frozen and is not corrected; this ADR is the record of tightening it, not a claim that design 0009
overlooked something no one could have decided.

## Decision

`validFoodItem(data)` gains a format check on `data.id`, the same regex `foodItems` already
applies to its document id:

```
data.id is string && data.id.matches('[0-9]{8}|[0-9]{12}|[0-9]{13}')
```

This applies wherever `validFoodItem` is already called — `foodItems` `create`/`update` and
`foodItemSubmissions` `create`/`update` — so the embedded submission item is now held to the same
barcode shape as a top-level catalogue document, with no new call site.

The `foodItemSubmissions` `update` rule's maintainer branch is narrowed to match exactly what
`RejectSubmissionUseCase` writes:

```
allow update: if (isMaintainer()
    && request.resource.data.submitted_by == resource.data.submitted_by
    && request.resource.data.barcode == resource.data.barcode
    && request.resource.data.status == 'rejected'
    && request.resource.data.reject_reason is string
    && request.resource.data.reject_reason.size() > 0
    && validFoodItem(request.resource.data.item))
  || (resource.data.submitted_by == request.auth.uid
      && request.resource.data.submitted_by == resource.data.submitted_by
      && request.resource.data.status == 'pending'
      && validFoodItem(request.resource.data.item));
```

A maintainer can therefore only move a submission to `rejected`, with a non-empty reason, an
unchanged `barcode` and `submitted_by`, and a structurally valid `item`. Approving still deletes
the submission rather than updating it, so this branch does not need to permit that path.

## Consequences

- A maintainer-claimed request can no longer set `foodItemSubmissions.status` to an arbitrary
  value, reassign `submitted_by`, change `barcode`, write a malformed `item`, or clear
  `reject_reason` while rejecting. Closes the gap this record's Context describes.
- If a legitimate maintainer action other than reject ever needs to update a submission in place,
  this rule must grow a matching branch rather than reopening the bare `isMaintainer()` clause —
  the whole point of this record is that every field a write can touch is named.
- `foodItemSubmissions`' `item.id` (and therefore the barcode a submission can carry) must now be
  8, 12 or 13 digits, matching `foodItems`. `FoodItemValidation` already enforces this client-side
  ([ARCHITECTURE.md § 2.6](../ARCHITECTURE.md)), so no legitimate client write is newly rejected;
  this closes the gap only for a client that bypasses that validation.
- Supersedes only the two rule fragments quoted in Context. Every other rule and consequence in
  [design 0009](../design/0009-catalogue-moderation.md) and
  [ADR 0027](0027-catalogue-writes-require-a-maintainer-claim.md) is unchanged.
