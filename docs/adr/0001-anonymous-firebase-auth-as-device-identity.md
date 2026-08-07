# 0001. Anonymous Firebase Auth is the device identity for signed-out users

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-08-07

## Context

Signing in is optional. A user who never signs in still needs a stable identity so their food
entries stay theirs and stay private, and so security rules have something to check.

The obvious alternative is for the app to generate its own identifier — a UUID in
`UserDefaults` (or `SharedPreferences` on Android) — and use it as the path segment under
`users/`.

| | App-generated UUID | Anonymous Firebase Auth |
|---|---|---|
| Survives uninstall | No — cleared with the app | Usually — stored in the Keychain |
| Verifiable by the server | No — an arbitrary string the client sends | Yes — signed token, `request.auth.uid` |
| Usable in security rules | No | Yes |
| Can be upgraded to a real account | No — needs a manual migration | Yes — account linking keeps the UID |

The decisive row is verifiability. A self-generated identifier cannot be checked by Firestore,
so rules would have to stay `allow read, write: if true` and every user's data would be
readable by anyone who knows the project ID. The client-side path structure would be a
convention, not a boundary.

## Decision

Every install signs in anonymously on first launch and keeps that account as its identity for
as long as the user stays signed out. Signing in **upgrades that same account** via account
linking rather than creating a new one.

The app does not generate identifiers of its own.

## Consequences

- Security rules can be owner-only (`request.auth.uid == userId`), which makes the data boundary
  real rather than conventional.
- Upgrading to a real account is free: linking preserves the UID, so the data does not move.
- Anonymous identity is still tied to a device. It usually survives uninstall via the Keychain,
  but "Erase All Content", a restore without Keychain, or a new device will lose it. That risk
  is inherent to a signed-out mode and is surfaced in the UI rather than engineered away.
- Orphaned anonymous accounts accumulate — one per install that later signs in to an existing
  account. They are cheap and cleanup is deferred.
- **Any future client MUST do the same.** A client that skips anonymous sign-in, or invents its
  own identifier, gives the same person two unrelated accounts and cannot be covered by the
  existing rules.
