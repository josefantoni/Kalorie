# 0005. Each sign-in provider gets its own use case, with no shared abstraction over them

- **Status:** Accepted
- **Scope:** iOS
- **Date:** 2026-08-08

## Context

Adding Google alongside Apple produces two use cases that look like siblings:
`SignInWithAppleUseCase` and `SignInWithGoogleUseCase`. The obvious instinct is to put a
`SignInWithProviderUseCaseProtocol` over both and let the view model hold one of them.

The shared behaviour, however, is already factored out — one layer further down.
`LinkOrMergeCredentialUseCase` takes a bare `AuthCredential` and performs link-first,
merge-on-conflict without knowing which provider produced it. That was deliberate in
[ADR 0003](0003-separate-auth-command-provider.md), which separated the auth command provider
specifically so the merge logic would not be tied to Apple.

What is left above that seam is not shared:

- **Signatures diverge irreducibly.** Apple's flow is driven by SwiftUI's
  `SignInWithAppleButton`, which hands back `(ASAuthorization, nonce)`. Google's SDK drives its
  own flow and takes nothing. A common `callAsFunction()` would mean stashing the
  `ASAuthorization` in view-model state or inventing an enum parameter — both worse than two
  honest signatures.
- **The bodies differ.** Apple returns name and e-mail only on first authorization, so it must
  call `updateDisplayName` and persist immediately. Google returns the profile every time and
  Firebase fills in `displayName` itself, so that call must *not* happen.

After the differences are accounted for, a shared protocol would unify roughly two lines.

## Decision

Each provider gets its own use case with its own signature, wrapping its own SDK adapter. The
only abstraction over providers is `LinkOrMergeCredentialUseCaseProtocol`, which operates on
`AuthCredential`.

A third provider, if one is ever added, follows the same shape: a new adapter and a new use
case, both feeding the same credential seam.

## Consequences

Adding a provider touches an additive set of files: one SDK wrapper, one use case, one view
model method, one button, one test file. Nothing existing needs restructuring, and no provider
can break another by changing shared code.

The cost is a visible parallel: two use cases with similar shapes and a few duplicated lines
around persisting the profile. That duplication is intentional and must not be collapsed — the
lines are similar but not identical, and unifying them reintroduces the `updateDisplayName`
call on the Google path, where it is a redundant write.

A future reader who wants to "clean this up" should push shared logic **down** into
`LinkOrMergeCredentialUseCase`, never **up** into a common sign-in protocol.
