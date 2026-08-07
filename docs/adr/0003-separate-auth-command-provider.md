# 0003. Auth mutations live in a separate provider from auth reads

- **Status:** Accepted
- **Scope:** iOS

- **Date:** 2026-08-07

## Context

`AuthProviderProtocol` existed already and was read-only: use cases asked it for the current
user ID to build a Firestore path. It was injected into eight use cases and faked in six test
files.

Authentication added mutations — link a credential, sign in, sign out, update the display name,
delete the account. Putting them on the same protocol would have forced every existing use case
and every existing fake to carry methods they neither call nor care about, and would have tied
the merge logic to Apple specifically.

## Decision

Keep `AuthProviderProtocol` read-only and add a second protocol, `AuthCommandProvider`, for
mutations.

The merge and linking flow depends only on `AuthCommandProvider` and a generic Firebase
`AuthCredential`, which keeps it provider-agnostic: `LinkOrMergeCredentialUseCase` holds the
logic, and `SignInWithAppleUseCase` is a thin adapter turning an `ASAuthorization` into a
credential.

## Consequences

- Existing use cases and their fakes are untouched — they keep depending on the narrow
  read-only protocol.
- Merge and linking are testable without touching Firebase, because the command provider is
  faked.
- Adding a provider later (Google, email) means writing another thin adapter. The merge logic,
  which is the part that is easy to get wrong, is written once and shared.
- Two protocols instead of one is slightly more indirection at the composition root, and
  `AccountConfigurator` has to wire both.
- The read/write split is a useful precedent for another platform, but nothing about it is
  binding — the cross-platform obligations are in
  [ADR 0001](0001-anonymous-firebase-auth-as-device-identity.md) and
  [ADR 0002](0002-merge-anonymous-data-before-switching-accounts.md).
