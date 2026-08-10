# 0006. A user reaching an existing account through the other provider is steered to it, with no override

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-08-10

## Context

With Apple as the only provider, one person meant one account. Adding Google breaks that: Firebase
identity is per credential, not per person, so the same human can end up with two unrelated
accounts and their food split between them.

Firebase can only recognise that two credentials belong to one person **by e-mail address**. Given
an existing Apple-linked account holding data, an anonymous user tapping Google produces three
outcomes:

1. The Google account is already a Firebase user — `link()` throws `credentialAlreadyInUse`, and
   the existing merge path ([ADR 0002](0002-merge-anonymous-data-before-switching-accounts.md))
   handles it.
2. The Google e-mail matches the e-mail on the Apple account — `link()` throws
   `emailAlreadyInUse`. Detectable.
3. The e-mails do not match — `link()` succeeds, and a second account is created silently.

Case 3 is the common one, not an edge case: Apple offers a private relay address
(`…@privaterelay.appleid.com`) and many users accept it, so the stored address usually does not
match their Gmail. Firebase sees an unrelated credential and reports nothing. There is no signal
to act on — this is an absence of information, not a missing feature.

For case 2 the desired behaviour was: warn the user their entries live under an Apple account,
steer them there, but let them proceed with Google if they insist. That last part turned out to be
unbuildable. Both halves depend on the same Firebase project setting, in opposite positions:

- **One account per email address** (the default) — Firebase raises `emailAlreadyInUse`, so the
  warning is possible, and it refuses the second account server-side. No override can exist.
- **Multiple accounts per email address** — Firebase creates the second account silently, so the
  override is the *only* behaviour and no error is ever raised to warn on.

There is no configuration in which the app both warns and offers to override.

## Decision

Keep the project on **one account per email address**.

When `link()` fails with `emailAlreadyInUse`, tell the user their earlier entries belong to an
account reached through the other provider, and that signing in with that provider will reach
them. Offer only that and cancelling — no "continue anyway".

Accept case 3 as undetectable. The app does not attempt to guess at it.

## Consequences

A user whose e-mails happen to match is protected: they cannot silently strand their history, and
the message names the way back.

A user who took Apple's private relay and later signs in with Google gets a second account and a
dashboard with no history, with no in-app explanation and no way to reunite the two. Support has
no tooling for this; reuniting them would need the named-to-named merge that
[design 0001](../design/0001-user-authentication.md) rejected. If this generates real reports, the
answer is that merge, not flipping the project setting — flipping it removes the warning for
everyone and makes silent splitting unconditional.

The project setting is now load-bearing. It reads as an innocuous Firebase Console toggle, and
changing it silently disables the collision handling in every client, present and future. It must
not be changed without superseding this record.

Every client is bound by this: the setting is shared, so an Android client sees the same error and
must handle it the same way. A client that skips the warning lets a user strand data that another
client would have protected.
