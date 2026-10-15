# Design: Linking a second sign-in provider to one account

- **Status:** Approved
- **Scope:** Cross-platform, iOS, Android
- **Date:** 2026-10-15

## Context and scope

One Firebase user (`uid`) can carry several provider identities. Today the clients never put more
than one on an account: the Apple and Google buttons on the Account screen exist only while the
user is anonymous, and `LinkOrMergeCredentialUseCase` links the chosen credential onto the
anonymous `uid` or, on `credentialAlreadyInUse`, merges into the existing account
([ADR 0002](../adr/0002-merge-anonymous-data-before-switching-accounts.md)).
[Design 0002](0002-google-sign-in.md) listed *"linking both providers to one account from the UI"*
as a non-goal, not as a rejected option. This document takes it up.

The motivation is [ADR 0006](../adr/0006-google-sign-in-identity-collisions.md), case 3: a user
who accepted Apple's private relay address and later signs in with Google gets a second, empty
account with no in-app explanation. Letting a signed-in user link the other provider on purpose
prevents that split. It does not repair accounts that are already split.

The document also brings **Sign in with Apple to Android**. [Design 0001](0001-user-authentication.md)
scoped Apple as `iOS` and left Android free to decide; Android now decides to offer it. The decision
belongs here because both features share the Apple console setup and the token-revocation work.

What already works without new code: when Apple and Google are both linked on `uid A`, an anonymous
user signing in with either one gets `credentialAlreadyInUse` from `link()`, and the existing merge
path signs them in as `A`. The anonymous sign-in flow does not change.

## Goals

- A signed-in user can see which providers their account has and link the missing one. After
  linking, both providers lead to the same `uid` on every client.
- A failed link never changes the signed-in `uid` or moves data. Every failure has its own message.
- Re-authentication (before account deletion) works on an account with two providers.
- Deleting an account that has Apple linked revokes the Apple token, as Apple requires.
- Android can sign in, link, re-authenticate and delete with Apple, with the same behaviour as iOS.

## Non-goals

- **Merging two named accounts.** If the provider the user tries to link is already a separate
  Kalorie account (`credentialAlreadyInUse` while signed in), the link is refused with an
  explanation. A merge on the client or in a Cloud Function is not built until real reports ask for
  it. The rejection in design 0002 (*Non-goals*) stays in force; see
  [ADR 0045](../adr/0045-signed-in-users-may-link-the-other-provider.md).
- **Unlinking a provider.** Not in this version. If it is added later, the last provider must not be
  unlinkable, and unlinking Apple must revoke its token.
- **Detecting already-split accounts.** ADR 0006 case 3 stays undetectable.
- **Changing the Firebase setting.** The project stays on *one account per email address*
  (ADR 0006).
- **Letting the user pick the provider for re-authentication.** It is chosen automatically.
- **Any provider beyond Apple and Google.**

## Design

### Firebase behaviour this relies on

`currentUser.link(with:)` also works on a named user and adds the provider to the same `uid`. On a
signed-in user it can fail with:

| Firebase error | Meaning | Client behaviour |
|---|---|---|
| `providerAlreadyLinked` | The account already has this provider. | Message "already linked". Normally unreachable, since the button is hidden. |
| `credentialAlreadyInUse` | This Apple ID / Google account is already a different Firebase user `B`. | Message: that sign-in already has its own Kalorie account. To link it here, sign in with it, delete that account, sign back in, and link it. Nothing changes. |
| `emailAlreadyInUse` | The credential's e-mail belongs to a third account `C`. | Message that the e-mail belongs to another account. Nothing is linked. |
| user cancelled | Prompt dismissed. | Silent, via the existing `isUserCancellation`. |
| anything else | Network etc. | Existing generic error. |

When the credential's e-mail matches the e-mail of `A` itself, the link succeeds. A linked Hide My
Email address links without trouble, because linking compares credentials, not e-mails.

### The merge path accepts only an anonymous user

`LinkOrMergeCredentialUseCase` (`iOS/Kalorie/Core/UseCases/LinkOrMergeCredentialUseCase.swift:41`,
`Android/.../core/usecases/LinkOrMergeCredentialUseCase.kt:25`) reads `authProvider.userId` as
`anonymousUserId` and never checks that it is anonymous. If a signed-in user ever reached it and got
`credentialAlreadyInUse`, it would merge `A`'s data into `B` and sign them in as `B`. Today only the
UI prevents that. Both clients MUST guard the use case: it throws (a new
`notAnonymous` case on the existing error type) when `authProvider.isAnonymous` is false, before
calling `link`. Linking from a signed-in state never goes through this use case.

### Linking use cases

Each platform gets one use case that links a bare credential and maps errors to a domain error, plus
one per provider that obtains the credential. This mirrors the split between
`SignInWithAppleUseCase` / `SignInWithGoogleUseCase` and `LinkOrMergeCredentialUseCase`
([ADR 0005](../adr/0005-no-shared-sign-in-provider-abstraction.md): no shared provider
abstraction).

- `LinkCredentialUseCase(credential)` → `AuthCommandProvider.link`. Errors become a
  `LinkCredentialError` enum: `alreadyLinked`, `credentialBelongsToAnotherAccount`,
  `emailBelongsToAnotherAccount`. Other errors pass through. It refuses to run while the user is
  anonymous (`AuthError.notAuthenticated`) or while `PendingMergeSnapshotStore.load()` returns a
  snapshot (`LinkCredentialError.mergeInProgress`). An unfinished merge must finish first.
- `LinkAppleUseCase` → `AppleSignInProvider.signIn()` then `LinkCredentialUseCase`. After success it
  calls the same `saveProfileIfNeeded` logic as `SignInWithAppleUseCase`, and only fills fields that
  are empty. It must not overwrite an existing display name with Apple's.
- `LinkGoogleUseCase` → `GoogleSignInProvider.signIn()` then `LinkCredentialUseCase`.

### Which providers an account has

`AuthProviderProtocol.linkedProviderKind: AuthProviderKind?` becomes
`linkedProviderKinds: Set<AuthProviderKind>`, built from every entry of `providerData`. The code
comment at `AuthProvider.swift:49` describes an invariant this design removes, so the comment goes
with it. On Android, `AuthProviderKind` gains `APPLE` (`AuthProvider.kt:11`), and the mapping at
`AuthProvider.kt:42` covers `apple.com`.

A second property, `currentSessionProviderKind: AuthProviderKind?`, returns the provider the current
session signed in with. It comes from the ID token's `signInProvider` (iOS
`getIDTokenResult().signInProvider`, Android `getIdToken(false).signInProvider`). It is async on
both platforms, so it is a suspend/async function, not a stored property.

### Re-authentication

`ReauthenticateUseCase` uses `currentSessionProviderKind`. When that is nil or not linked, it falls
back to the only linked provider if exactly one exists, and otherwise throws
`AuthError.notAuthenticated`. If the user picks a different Google or Apple identity in the prompt,
Firebase throws `userMismatch`. That gets its own error case, `ReauthenticateError.wrongAccount`, and
its own message ("choose the account you are signed in with"), not the generic one.

### Account deletion revokes the Apple token

Apple requires apps that offer Sign in with Apple to revoke the user's token when the account is
deleted. Neither client does this today, which is a defect independent of linking. It is fixed
first.

- **iOS:** `AppleSignInResult` gains `authorizationCode: String?`, taken from
  `ASAuthorizationAppleIDCredential.authorizationCode`. When the account has `apple`, deletion
  re-authenticates with Apple even if the session provider is Google, because a fresh authorization
  code is needed. It then calls `Auth.auth().revokeToken(withAuthorizationCode:)` before
  `deleteCurrentUser()`. A revocation failure is logged and does not block deletion.
- **Android:** the equivalent revocation API from the Firebase Auth SDK the project pins. It needs
  verifying at implementation time, together with how the web flow exposes the code or token
  (see *Risks*).

Both need the Apple provider in Firebase Console to carry the **Team ID, Key ID and private key**.
Today only the iOS bundle is configured.

### Account screen

On a signed-in account, a "Sign-in methods" section shows one row per provider (Apple, Google). A
linked provider shows a check mark. A missing one shows a "Link" button that runs `LinkAppleUseCase`
or `LinkGoogleUseCase`. Success shows a short confirmation and reloads the provider set; `uid` and
data do not change. Failures use the per-feature alert ([ADR 0018](../adr/0018-per-feature-error-alerts-with-no-global-handler.md)).
Signing out does not change.

The anonymous view keeps its two sign-in buttons. Android gets the Apple button there as well.

### Sign in with Apple on Android

Firebase supports Apple on Android only through its OAuth web flow:
`OAuthProvider.newBuilder("apple.com")` with scopes `email` and `name`, run through
`startActivityForSignInWithProvider`, `FirebaseUser.startActivityForLinkWithProvider` or
`startActivityForReauthenticateWithProvider`. The SDK performs the auth operation itself and does
not hand a credential back first. This is the one place the Android port cannot copy iOS
structurally:

- New `AppleSignInProvider` (`core/auth/`) exposing `link()`, `reauthenticate()` and
  `revokeOnDeletion()` over the current activity (`CurrentActivityProvider`). It returns the
  `AuthResult`, and its errors map to the same domain errors as iOS.
- Anonymous sign-in: `SignInWithAppleUseCase` calls `link()` on the anonymous user. On
  `FirebaseAuthUserCollisionException` with `ERROR_CREDENTIAL_ALREADY_IN_USE`, it takes
  `exception.updatedCredential` and hands it to `MigrateAnonymousDataUseCase.migrate`, the same path
  Google takes. `ERROR_EMAIL_ALREADY_IN_USE` maps to
  `LinkOrMergeCredentialError.AccountExistsWithAnotherProvider`, like the iOS alert. To avoid
  duplicating that mapping, `LinkOrMergeCredentialUseCase` gets a second entry point that takes the
  link operation as a suspend function and reads the credential for the merge from the collision
  exception. It goes through the same anonymous-only guard.
- Signed-in linking: `LinkAppleUseCase` calls `link()` on the named user. The errors are the same as
  in the table above.
- Apple returns the name only on first authorisation, and the web flow may not pass it to Firebase.
  On Android the display name may stay empty, which is acceptable.

**Console setup (manual, owner action; documented in `docs/SETUP.md` when implemented):** an Apple
**Services ID** with Sign in with Apple enabled, domain `<project>.firebaseapp.com`, return URL
`https://<project>.firebaseapp.com/__/auth/handler`. In Firebase Console → Authentication → Apple:
the Services ID, Team ID, Key ID and private key. No CI secret is needed, because the key lives in
Firebase, not in the app.

### Strings

New keys in `localisation/strings.json` only, generated by `scripts/generate-strings.js`:
the section title, "Link" (with the Apple and Google labels already present), link success,
the four `LinkCredentialError` messages, `mergeInProgress`, and `wrongAccount` for re-authentication.

## Alternatives considered

**Named-to-named merge on the client.** Sign in as `B`, snapshot its data to disk, delete `B`, sign
back in as `A`, link and write. Rejected for now: it is irreversible, takes two prompts, and adds
crash-recovery states to a path that today recovers from one snapshot. The current anonymous merge
also copies only four of the six per-user collections, so a full merge would need meal-type id
collisions and frequency summation designed first.

**Named-to-named merge in a Cloud Function.** A second `FirebaseApp` instance would let the client
send both ID tokens, which avoids design 0001's "one token at a time" objection. Rejected for now:
the repo has no Functions, so it would need the Blaze plan, a new deploy step and a CI secret, all
for a case with no reports yet.

**Letting the user choose the re-authentication provider.** Rejected: an extra decision in a
destructive flow, and the session provider is always valid.

**Auto-linking on `emailAlreadyInUse` from the anonymous state.** Still rejected, as in design 0002.
That rejection was about an automatic link triggered by an error. This design covers a deliberate
action taken by a user who is already signed in as `A`.

## Cross-cutting concerns

### Platform scope

| Area | Scope | Note |
|---|---|---|
| Error → behaviour table, anonymous-only merge guard, no named-to-named merge | `Cross-platform` | Both clients share the Firebase project. A client that merges on a signed-in collision corrupts data seen by the other. |
| Re-auth provider selection, Apple revocation on deletion | `Cross-platform` | |
| Android Apple web flow, `updatedCredential` merge entry point | `Android` | |
| `authorizationCode` on `AppleSignInResult` | `iOS` | |

### Security and privacy

No Firestore rule changes: the `uid` does not change. Revoking Apple on deletion is an App Store
requirement (Guideline 5.1.1(v)) and in effect also a Play requirement for parity. The privacy policy
([design 0021](0021-privacy-policy.md)) already names both providers. Check its wording for "one
provider per account" before release.

### App Store / Play Store

Guideline 4.8 is still met on iOS. On Android, Apple is optional and Google stays primary.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Users who are already split hit `credentialAlreadyInUse` and keep two accounts. | Frustration. | The message names the way out (delete the unneeded account, then link). Reports trigger the merge alternatives. |
| Google is a "trusted provider" for `@gmail.com`; a `signIn` (not `link`) can drop unverified providers from the account. | A linked Apple could disappear. | Our paths always `link` first, but verify by hand in the test project: link Apple to a Gmail account, sign out, sign in with Google, and check that Apple is still in `providerData`. |
| Android web flow: `updatedCredential` or revocation behaves differently from the docs. | Merge or revocation broken on Android only. | Verify by hand on a device before release: anonymous → Apple onto an existing Apple account merges, and deletion revokes (the app disappears from appleid.apple.com → Sign in with Apple). |
| Apple key missing in Firebase Console. | Revocation and the Android flow both fail. | Console setup is a prerequisite step, done by the owner before the code lands. |
| Linking while a merge snapshot is pending. | Data written under the wrong `uid`. | `mergeInProgress` guard. |

## Outcome

To be filled in once implemented.
