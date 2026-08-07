# Design: User authentication

- **Status:** Implemented in `ce6bb65`
- **Scope:** Backend, Cross-platform, iOS
- **Date:** 2026-08-07

> Frozen. This records the design as it was decided. It is not updated as the code evolves —
> see the ADRs it produced, and the code itself, for current behaviour.

## Context and scope

The app stores what the user eats. Until now every install authenticated as a **new anonymous
Firebase user**, and all data was scoped under that UID:

```
users/{userId}/foodConsumed    — food entries
users/{userId}/mealTypes       — meal layout (breakfast, lunch, …)
foodItems                      — shared food catalogue, global by design
```

Per-user isolation therefore already worked. What did not work was **identity**: the anonymous
UID lives in the device Keychain, so replacing a phone, or using both an iPhone and an iPad,
means starting from zero with no way to recover the history.

This document covers adding an optional account on top of the existing anonymous mode. It does
not cover the shared `foodItems` catalogue or its future moderation flow.

## Goals

- A user can sign in with Apple ID and keep their history across devices and device changes.
- A user who does **not** sign in keeps working exactly as before, with data on that device.
- Signing in never silently loses data written while signed out.
- Two signed-in devices agree on what the user ate.

## Non-goals

- **Real-time sync.** Refreshing when the app returns to the foreground is sufficient for a
  calorie tracker; snapshot listeners would mean rewriting the whole data layer.
- **Merging meal layouts.** Only food entries are merged (see [ADR 0002](../adr/0002-merge-anonymous-data-before-switching-accounts.md)).
- **Sign-in providers other than Apple.** The design keeps the door open (see Alternatives) but
  ships one provider.
- **Recovering data from a device whose anonymous identity was already lost.** Nothing on the
  server links that data to a person; it is unreachable by construction.
- **Cleaning up orphaned anonymous accounts.** Left for a later server-side job.

## Design

### Identity model

Anonymous Firebase Auth remains the baseline for every install; signing in **upgrades** that
same account rather than creating a second one. Rationale and the rejected alternative are in
[ADR 0001](../adr/0001-anonymous-firebase-auth-as-device-identity.md).

### First device — no migration happens

Firebase Auth account linking preserves the UID:

```swift
try await Auth.auth().currentUser?.link(with: credential)
```

The anonymous account becomes a full account with the same UID, so `users/{uid}/…` does not
move. There is no migration step and nothing to get wrong.

### Second device — the normal failure path

`link(with:)` fails with `credentialAlreadyInUse` whenever the Apple ID is already bound to
another account. With an iPhone and an iPad this happens **every time** the second device signs
in — it is the expected path, not an edge case:

```
iPhone:  anonymous A ──link()──►  A (Apple ID bound)        data stays
iPad:    anonymous C ──link()──►  credentialAlreadyInUse
                      ──signIn()──►  A                       data under C is orphaned
```

How much data sits under `C` is a UX question, not a technical one. A user who signs in right
after installing has nothing to merge; a user who logged meals anonymously for a week does. The
app therefore merges rather than discarding — see
[ADR 0002](../adr/0002-merge-anonymous-data-before-switching-accounts.md) for the ordering
constraint that makes this non-obvious.

### Multi-device concerns

Three problems that only appear once one account spans two devices:

**Stale reads.** All reads go through `getDocuments()`, and the Firestore SDK enables offline
persistence by default, so a device can serve cached state. A user who logs breakfast on the
phone and then opens the iPad may not see it and log it twice. Mitigated by refreshing when the
app returns to the foreground; real-time listeners are a non-goal.

**Default meal layout overwriting a real one.** The dashboard treated an empty `mealTypes`
result as "new user" and wrote defaults. After a UID switch an empty result can also mean a
failed or cached read, which would overwrite the user's configured meal times. Defaults are now
written only when a read is *confirmed* empty by the server.

**Stale view state across a UID change.** View models cache loaded data, so after signing in the
user would briefly see the previous account's entries. The root view is keyed on the user ID so
the tree is rebuilt with clean view models when the account changes.

### Backend contract

Firestore security rules are the one artefact shared by every present and future client:

```
match /users/{userId} {
  allow read, write: if request.auth != null && request.auth.uid == userId;
  match /{document=**} {
    allow read, write: if request.auth != null && request.auth.uid == userId;
  }
}

match /foodItems/{itemId} {
  allow read: if request.auth != null;
  allow write: if request.auth != null;   // narrowed once the moderation flow lands
}
```

The `foodItems` block matters beyond authentication: `rules_version = '2'` is default-deny, so
an owner-only rule set without it silently makes the food catalogue unreadable.

### Client architecture (iOS)

Follows the existing MVVM + UseCase conventions. Reading the auth state and mutating it are
separate protocols — see [ADR 0003](../adr/0003-separate-auth-command-provider.md).

| Component | Responsibility |
|---|---|
| `AuthProviderProtocol` | Read-only: current user ID, anonymous flag, display name |
| `AuthCommandProvider` | Mutations: link, sign in, sign out, delete |
| `LinkOrMergeCredentialUseCase` | Provider-agnostic: link → on conflict, merge → sign in |
| `SignInWithAppleUseCase` | Thin adapter from `ASAuthorization` to a Firebase credential |
| `MigrateAnonymousDataUseCase` | Crash-safe merge of food entries ([ADR 0004](../adr/0004-migrate-usecase-exposes-two-methods.md)) |
| `SignOutUseCase`, `DeleteAccountUseCase` | Sign out; delete account and its data |
| `Features/Account/*` | Account screen, reachable from the dashboard toolbar |

## Alternatives considered

**A device ID generated by the app, stored in `UserDefaults`.** Rejected: it cannot be verified
by the server, so security rules could not be written against it and the database would have to
stay world-writable. It also does not survive uninstall, which anonymous Firebase Auth usually
does. Full comparison in [ADR 0001](../adr/0001-anonymous-firebase-auth-as-device-identity.md).

**Discard anonymous data when the Apple ID already has an account.** Rejected: with two devices
this is the normal path, so it would mean routine silent data loss.

**Merge on the server via a Cloud Function.** Rejected as disproportionate. A client holds one
auth token at a time, so a server-side merge needs a two-phase handshake (request as the
anonymous user, confirm as the signed-in one). The client-side merge with an on-disk snapshot
gives the same crash safety for a fraction of the complexity.

**Requiring sign-in at first launch.** Rejected: signing out is an explicitly supported mode.
The cost is the merge path above.

**Real-time snapshot listeners.** Rejected for now — see Non-goals.

## Cross-cutting concerns

### Platform scope

This is written for iOS but the backend is shared, so a future Android client inherits some of
it and must re-decide the rest:

| Area | Scope | Notes for a second client |
|---|---|---|
| Firestore paths, security rules | `Backend` | Single implementation. Any client change that needs a rule change affects all clients. |
| Anonymous-by-default identity | `Cross-platform` | An Android client MUST also start anonymous, or the same user gets two unrelated accounts. |
| Link-first, merge-on-conflict | `Cross-platform` | Divergence here corrupts data: a client that signs in without merging drops entries another client would have kept. |
| Merging food entries only | `Cross-platform` | Numeric meal-type IDs collide across accounts; every client leaves them alone. |
| Pending-merge snapshot on disk | `iOS` | Crash-recovery detail, local to one install. Format is not a shared contract; another client can pick its own. |
| MVVM + UseCase, protocol + `Fake` | `iOS` | Precedent, not a requirement. |
| Sign in with Apple as the provider | `iOS` | On Android, Apple sign-in only works through Firebase's OAuth web flow, and Google Sign-In is the natural primary. Adding it triggers App Store Guideline 4.8, which requires Apple sign-in to stay offered on iOS. |

### App Store requirements

- **Guideline 5.1.1(v)** — an app that creates accounts must let the user delete one in-app.
  This is why `DeleteAccountUseCase` exists, and it removes Firestore data as well as the auth
  account.
- **Guideline 4.8** — adding a third-party provider later obliges us to keep offering Sign in
  with Apple.

### Apple sign-in specifics

- A nonce is mandatory; Firebase rejects the credential without it.
- Name and email are returned **only on the very first authorization** — they must be stored
  immediately or they are gone.
- Email may be a private relay address, so it is not usable as an identifier.
- `user.delete()` requires a recent login and can fail with `requiresRecentLogin`.

### Privacy

The profile document stores only what Apple returns and only if the user chose to share it.
Signed-out users are never asked for anything.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Crash between switching accounts and writing merged data | Irrecoverable data loss | Snapshot written to disk first, resumed on next launch |
| Collection missing from security rules | Feature silently broken in production | Add the rule block in the same change as the path constant; test before deploy |
| Default meal layout written over a real one | User's meal times reset | Defaults only on a server-confirmed empty read |
| View state surviving a UID change | User sees another account's data | Root view keyed on user ID |
| Orphaned pending-merge snapshot after sign-out | Previous user's entries written into a new anonymous account | Snapshot deleted before signing out |
| Stale reads on a second device | Same meal logged twice | Refresh on foreground |
| Anonymous identity lost with the device | Data loss for signed-out users | Not solvable technically — surfaced in the UI |

## Outcome

Implemented in `ce6bb65`. Decisions extracted into
[ADR 0001](../adr/0001-anonymous-firebase-auth-as-device-identity.md),
[0002](../adr/0002-merge-anonymous-data-before-switching-accounts.md),
[0003](../adr/0003-separate-auth-command-provider.md) and
[0004](../adr/0004-migrate-usecase-exposes-two-methods.md).

Two items were deliberately left open:

- Out-of-band configuration (Xcode capability, Apple Developer portal, Firebase provider, rules
  deployment) is not done by code — see [SETUP.md](../SETUP.md).
- There is no proactive prompt to sign in; the account screen has to be found in the toolbar.
  Tracked as a follow-up.
