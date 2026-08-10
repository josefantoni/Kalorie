# Design: Google sign-in as a second provider

- **Status:** Implemented
- **Scope:** Cross-platform, iOS
- **Date:** 2026-08-08

> Frozen. This records the design as it was decided. It is not updated as the code evolves —
> see the ADRs it produced, and the code itself, for current behaviour.

## Context and scope

[Design 0001](0001-user-authentication.md) added optional Apple ID sign-in on top of the
anonymous-by-default identity model. It listed *"sign-in providers other than Apple"* as a
non-goal, but deliberately kept the seam open: `LinkOrMergeCredentialUseCase` takes a bare
`AuthCredential` and knows nothing about Apple.

This document reopens that non-goal and adds **Google** as a second provider. It covers the
client work, the out-of-band configuration, and — the only genuinely hard part — what happens
when one person reaches the app through both providers.

It does not revisit the anonymous baseline, the merge algorithm, or the Firestore data model.
None of them change.

## Goals

- A user can sign in with either Apple or Google and keep their history across devices.
- Google sign-in reuses the existing link-first / merge-on-conflict path unchanged. A user who
  logged food anonymously and then signs in with Google keeps that food.
- Signing out actually signs out: tapping Google again offers the account chooser rather than
  silently restoring the previous session.
- Nothing about the Apple path changes. Guideline 4.8 obliges us to keep offering it.

## Non-goals

- **Linking both providers to one account from the UI.** A user who wants both on one account is
  out of scope; see *Identity collisions* below for what they get instead.
- **Merging two accounts that already exist.** The merge path handles anonymous → named. It does
  not handle named → named, and making it do so would need a two-phase server-side handshake
  (rejected for the same reason in 0001).
- **Any provider beyond Apple and Google.** Facebook, e-mail/password, and phone stay out.
- **Changing the anonymous baseline.** Every install still starts anonymous
  ([ADR 0001](../adr/0001-anonymous-firebase-auth-as-device-identity.md)).

## Design

### The credential seam already exists

The existing chain is provider-agnostic below the credential:

```
AccountView → AccountViewModel
  → SignInWithAppleUseCase          ← the only Apple-specific type
    → LinkOrMergeCredentialUseCase  ← takes AuthCredential, provider-agnostic
      → AuthCommandProvider.link(with:)
      → MigrateAnonymousDataUseCase.migrate(from:credential:)
```

Google enters as a **sibling of `SignInWithAppleUseCase`**. Nothing below that line is touched:
no change to `LinkOrMergeCredentialUseCase`, `MigrateAnonymousDataUseCase`,
`PendingMergeSnapshotStore`, or `firestore.rules`.

### Components

| Component | Status | Responsibility |
|---|---|---|
| `GoogleSignInProviderProtocol` / `GoogleSignInProvider` | new | Wraps `GIDSignIn`. Resolves the client ID, finds a presenting view controller, returns credential + profile. |
| `SignInWithGoogleUseCase` | new | Adapter: get credential from the provider, hand it to `LinkOrMergeCredentialUseCase`, persist the profile. |
| `LinkOrMergeCredentialUseCase` | **changed** | Gains one branch for `emailAlreadyInUse` (see below). |
| `SignOutUseCase` | **changed** | Also clears the Google SDK session. |
| `AccountViewModel`, `AccountView`, `AccountConfigurator` | changed | Second button, second use case in the graph. |

The SDK wrapper is split from the use case for the same reason `AuthCommandProvider` is split
from `AuthProvider` ([ADR 0003](../adr/0003-separate-auth-command-provider.md)): `GIDSignIn`
needs a live `UIViewController` and cannot be faked, so the use case would otherwise be
untestable.

### Profile handling differs from Apple

Apple returns name and e-mail **only on the very first authorization**, which is why
`SignInWithAppleUseCase` calls `updateDisplayName` and writes the profile immediately or loses
it forever. Google has neither constraint: the profile comes back on every sign-in, and Firebase
populates `displayName` from the Google credential itself.

So `SignInWithGoogleUseCase` writes the Firestore profile document for parity but **must not**
call `updateDisplayName`. Copying the Apple use case wholesale would add a redundant write.

### Identity collisions — the hard part

Firebase identity is per credential, not per person. With two providers, one human can end up
with two unrelated accounts and their food split between them. Given an existing Apple-linked
account `A` with data, an anonymous user `C` tapping Google produces three outcomes:

| # | Condition | Firebase behaviour | Detectable |
|---|---|---|---|
| 1 | That Google account is already a Firebase user | `link()` throws `credentialAlreadyInUse` | Yes — already handled, merges `C` into it |
| 2 | Google e-mail matches the e-mail on `A`, project set to *one account per email address* | `link()` throws `emailAlreadyInUse` | Yes — new branch |
| 3 | Google e-mail matches nothing | `link()` **succeeds**; `C` becomes a Google account | **No** |

**Case 3 is the common one, not the edge case.** Apple offers a private relay address
(`…@privaterelay.appleid.com`) and many users take it, so the e-mail on account `A` usually does
*not* match the user's Gmail. Firebase sees an unrelated credential, links it cleanly, and
reports nothing. There is no signal to warn on — not a missing feature, an absence of
information.

For case 2 the intended behaviour is: tell the user their earlier entries live under an Apple
account and steer them to sign in with Apple instead.

#### Why there is no "proceed with Google anyway"

The natural third button — *"use Google anyway, start fresh"* — cannot be built alongside the
warning. The two depend on opposite settings of the same Firebase project switch:

| Firebase setting | Case 2 becomes | Warning possible | "Proceed anyway" possible |
|---|---|---|---|
| One account per email address (default) | `emailAlreadyInUse` | **Yes** | No — Firebase refuses the second account server-side |
| Multiple accounts per email address | silently case 3 | No — no error is raised | Yes, but it is the *only* behaviour |

Picking "multiple accounts" does not add a choice; it removes the warning and makes silent
account splitting the unconditional behaviour. There is no configuration in which the app both
warns and offers to override.

The project therefore stays on **one account per email address** (the current default) and the
app offers *Sign in with Apple* / *Cancel*, with no override. Recorded as
[ADR 0006](../adr/0006-google-sign-in-identity-collisions.md), which also covers why case 3 is
accepted rather than mitigated.

#### Error-code correctness

`LinkOrMergeCredentialUseCase` matches on `nsError.code == AuthErrorCode.<x>.rawValue`, so the
wrong constant silently produces a dead branch. The relevant distinction:

- `credentialAlreadyInUse` — *this* credential belongs to another account. Thrown by `link()`.
  Already handled.
- `emailAlreadyInUse` — the credential's e-mail belongs to another account with a *different*
  credential. Thrown by `link()`. This is case 2.
- `accountExistsWithDifferentCredential` — same situation, but thrown by `signIn(with:)`, not
  by `link()`. Not reachable on this path.

### Signing out

`SignOutUseCase` currently drops the pending-merge snapshot and calls Firebase `signOut()`.
Firebase forgets the user; the Google SDK does not. The next tap on the Google button then
re-authenticates silently with the cached session, so **the user cannot switch Google accounts**
and "sign out" looks broken.

`SignOutUseCase` therefore also clears the Google session, behind a protocol rather than
touching `GIDSignIn` directly, so the existing tests keep working with a fake.

### Configuration

None of this is reproducible from source; the runbook entries go in [SETUP.md](../SETUP.md).

- **Google provider must be enabled in the Firebase Console first.** The current
  `GoogleService-Info.plist` has no `CLIENT_ID` and no `REVERSED_CLIENT_ID` — those keys only
  appear once the provider is on. Everything else depends on them.
- Re-download the plist **and re-encode the CI secret** `GOOGLE_SERVICE_INFO_PLIST`. A stale
  secret breaks CI, not the local build, so it fails later and further from the cause.
- `CFBundleURLTypes` with `REVERSED_CLIENT_ID` in `Kalorie/Resources/Info.plist`, plus
  `GIDSignIn.sharedInstance.handle(_:)` on the root view to receive the redirect.
- The client ID is read from `FirebaseApp.app()?.options.clientID` rather than duplicated as
  `GIDClientID` in Info.plist, so it lives in exactly one file.
- New SPM dependency `GoogleSignIn-iOS`, product `GoogleSignIn`. `GoogleSignInSwift` is only
  needed for its ready-made button, which does not match the Apple button visually.

### UI

A second button under the existing `SignInWithAppleButton`, inside the `isAnonymous` branch of
`AccountView`. Apple stays first and unchanged.

Two details that are easy to miss: user cancellation (`GIDSignInError.canceled`) must be
filtered out or dismissing the sheet raises a spurious error alert — the mirror of the existing
`ASAuthorizationError.canceled` check — and Google's brand guidelines require the official mark,
so the button needs a real asset rather than an SF Symbol.

## Alternatives considered

**A shared `SignInWithProviderUseCaseProtocol` over both providers.** Rejected: the shared
behaviour is already factored out one layer down, into `LinkOrMergeCredentialUseCase`. A common
protocol above it would unify two lines while forcing a lowest-common-denominator signature —
Apple needs `(ASAuthorization, String)`, Google needs `()`. See
[ADR 0005](../adr/0005-no-shared-sign-in-provider-abstraction.md).

**Calling `GIDSignIn` directly from the use case.** Rejected: it needs a presenting
`UIViewController`, which makes the use case untestable and puts UIKit lookup in the domain
layer.

**Switching the project to *multiple accounts per email address*.** Rejected: it does not add
the "proceed anyway" option so much as make silent account splitting unconditional, and it
removes the only signal the app has for case 2. Reconsider only if collision reports never
materialise.

**Detecting the collision before attempting `link()`**, via `fetchSignInMethods(forEmail:)`.
Rejected: Firebase deprecated the method precisely because it enables e-mail enumeration, and it
still would not cover case 3.

**Handling `emailAlreadyInUse` by auto-linking Google onto the Apple account.** Rejected: it
requires the user to be signed in as `A` first, so it is the same as telling them to sign in
with Apple — but with a confusing intermediate state and no way back if they abandon it.

## Cross-cutting concerns

### Platform scope

| Area | Scope | Notes for a second client |
|---|---|---|
| Which providers are offered | `Cross-platform` | An Android client MUST offer the same pair. If Android ships Google-only, an iPhone user who chose Apple silently lands in a different account on their tablet. |
| Link-first, merge-on-conflict per provider | `Cross-platform` | Unchanged from 0001, now applies to both credential types. |
| Collision behaviour on `emailAlreadyInUse` | `Cross-platform` | The Firebase project setting is shared, so every client sees the same error and must handle it the same way. |
| `GIDSignIn` wrapper, presenting-VC lookup | `iOS` | Android uses Credential Manager; nothing here transfers. |
| Firestore paths, security rules | `Backend` | Untouched — rules key on `request.auth.uid`, which is provider-independent. |

### App Store requirements

Guideline 4.8 requires a privacy-preserving login option wherever a third-party one is offered.
Sign in with Apple satisfies it and stays in place, so adding Google is compliant — but it also
means Apple sign-in can no longer be removed. Guideline 5.1.1(v) is already satisfied by
`DeleteAccountUseCase` and is unaffected.

### Privacy

Google always returns a real e-mail address — there is no relay equivalent. Users who chose
Apple's private relay and then sign in with Google hand over an address they had previously
withheld. The Firestore profile document keeps storing only name and e-mail.

Worth noting: `UserProfileDTO` is currently **write-only** — nothing in the app reads it back.
That is a pre-existing observation, not something this change should fix.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Case 3 splits a user's data silently | Entries spread over two accounts, no way to reunite them in-app | Not preventable — no signal exists. Documented in ADR; revisit if support requests appear. |
| Wrong `AuthErrorCode` constant in the new branch | Dead code; user sees the generic failure alert | Test the branch against `emailAlreadyInUse` explicitly, not just the happy path |
| Stale `GOOGLE_SERVICE_INFO_PLIST` CI secret | CI breaks after merge while local builds pass | Regenerate the secret in the same change as the plist |
| `gtm-session-fetcher` resolution conflict | Project stops building | Firebase is pinned at 10.28.1 → gtm-session-fetcher 3.5.0. Verify SPM resolution after adding GoogleSignIn; pin to 7.1.x if 8.x moves it out of range. |
| Google session outliving sign-out | User cannot switch Google accounts | Clear the SDK session in `SignOutUseCase` |
| Cancellation surfaced as an error | Spurious alert on every dismissed sheet | Filter `GIDSignInError.canceled` |

## Outcome

Implemented as designed: `GoogleSignInProvider` / `GoogleSessionProvider` wrap the SDK,
`SignInWithGoogleUseCase` is a sibling of `SignInWithAppleUseCase`, and `LinkOrMergeCredentialUseCase`
gained the `emailAlreadyInUse` branch with no other change to the credential seam. Decisions
extracted into [ADR 0005](../adr/0005-no-shared-sign-in-provider-abstraction.md) and
[ADR 0006](../adr/0006-google-sign-in-identity-collisions.md).

One implementation detail not anticipated in the design: `GoogleSignInProviderProtocol` is
`@MainActor`, which makes Swift infer `@MainActor` isolation for the whole conforming
`GoogleSignInProvider` type — including its implicit initializer. `AccountConfigurator.createView()`
is not `@MainActor` (its caller, `DashboardRouter`, is out of scope for this change), so the
initializer had to be marked `nonisolated` explicitly on both the real provider and its `Fake`.

Out-of-band configuration (Firebase Console provider, `GoogleService-Info.plist`, CI secret) is
not done by code — see [SETUP.md](../SETUP.md). The on-device checklist (fresh install merge,
second-device `credentialAlreadyInUse` merge, cancellation, account-chooser after sign-out) is a
manual QA pass, not something this change can verify by itself.
