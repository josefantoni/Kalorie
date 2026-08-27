# 0011. `foodItems` is writable by any authenticated client, pending the moderation flow

- **Status:** Accepted
- **Scope:** Backend
- **Date:** 2026-08-27

## Context

`foodItems` is a single global catalogue shared by every user. The security rule guarding it is:

```
match /foodItems/{itemId} {
  allow read: if request.auth != null;
  allow write: if request.auth != null;
}
```

Anonymous sign-in counts as authenticated ([ADR 0001](0001-anonymous-firebase-auth-as-device-identity.md)),
so in practice this means *anyone who has installed the app*.

The write permission is not accidental. `CreateFoodItemUseCase` backs the *add a new food*
form in `AddFoodSheet` — the user types a barcode, a name and the macros off the packaging, and
the app writes the result straight into the shared `foodItems` catalogue from the client. That
form is the **only** way the catalogue grows: neither the barcode scanner nor the OpenFoodFacts
search writes anything back (see
[ADR 0012](0012-external-food-is-surfaced-never-imported.md)). Without client write access the
catalogue stops growing altogether.

The planned replacement is already written down in `TODO.md`: user submissions go to a separate
pending collection, a maintainer approves them through an admin panel gated by a Firebase
custom claim, and only the approving path writes to `foodItems`.

## Decision

`foodItems` stays client-writable by any authenticated user until the moderation flow ships. At
that point the rule tightens to `allow read: if request.auth != null; allow write: if false;`
for clients, with writes moving behind the maintainer claim.

## Consequences

- The barcode-scan path works today with no server-side component, which is the whole reason
  the catalogue has any breadth at all.
- **Any client can also overwrite or delete any catalogue entry, for every user.** `allow
  write` covers create, update *and* delete, the rule validates no fields, and there is no
  audit trail — nothing records who wrote a document. A buggy or hostile client can corrupt or
  empty the shared catalogue, and the only recovery is a Firestore backup restore. This is
  accepted as a pre-release risk, not as a permanent state; finding **A1-11** in `TODO.md`
  records the narrower rule that could reduce it in the meantime.
- Tightening the rule **must** be done together with the moderation flow, not before it. A
  reader who hardens this rule on its own silently breaks the *add a new food* form — the write
  fails inside `CreateFoodItemUseCase` and surfaces as a generic error. This has already
  happened once, on the read side, during the authentication work.
- Because writes go through `setAsync`, which replaces rather than creates, a client that
  reaches the write with a barcode that already exists **overwrites** the existing catalogue
  entry rather than failing. `CreateFoodItemUseCase` guards against this with a prior existence
  query, but that guard can read a stale offline cache — finding **A2-2** in `TODO.md`.
- The decision is scoped `Backend`, so it constrains every future client identically.
