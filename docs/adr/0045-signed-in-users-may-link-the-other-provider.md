# 0045. A signed-in user may link the other provider; existing named accounts are never merged

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-10-15

## Context

[ADR 0006](0006-google-sign-in-identity-collisions.md) keeps the project on *one account per email
address*. It accepts that a user who took Apple's private relay address and later signs in with
Google ends up with two accounts, with "no way to reunite the two". It names the named-to-named
merge as the answer if this generates reports.

Firebase can put both providers on one `uid` when a user who is already signed in links the second
one. That prevents the split without changing the project setting. It does not help a user whose
two accounts already exist. Linking a provider that is already its own account raises
`credentialAlreadyInUse`, which is the named-to-named case.

## Decision

A signed-in user may link the provider their account lacks, from the Account screen
([design 0025](../design/0025-linking-a-second-sign-in-provider.md)).

When that link raises `credentialAlreadyInUse`, the client refuses. It explains that the identity
already has its own account and that deleting that account frees it for linking. Clients do not
merge two named accounts, either on the client or on a server.

The merge path of [ADR 0002](0002-merge-anonymous-data-before-switching-accounts.md) runs only from
an anonymous user. Every client enforces this in the use case, not only in the UI.

The project setting from ADR 0006 does not change.

## Consequences

This narrows the consequence in ADR 0006: a Hide My Email user who links the other provider while
signed in never splits. The rest of ADR 0006 holds. Case 3 is still undetectable, and an account
that has already split still cannot be reunited in place. The user can now delete one account and
link its provider to the other, losing the data in the deleted account.

If reports of split accounts arrive, the next step is still the named-to-named merge, as ADR 0006
says. Design 0025 records why the client-side and Cloud Function variants were deferred.

A client that runs the anonymous merge for a signed-in user would move the signed-in account's data
into another account and switch the user to it. The anonymous-only guard prevents that and must not
be removed.
