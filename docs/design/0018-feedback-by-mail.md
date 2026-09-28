# Design: Feedback by mail

- **Status:** Implemented in `d4743eb` (iOS) and `9412489` (Android)
- **Scope:** Cross-platform, iOS, Android
- **Date:** 2026-09-28

## Context and scope

The only way a user can tell us something today is [design 0012](0012-report-incorrect-catalogue-data.md):
reporting incorrect catalogue data on a single food. There is no general channel for an idea, a
bug or a question. The support address already exists and is public on the account-deletion page
([design 0015](0015-account-deletion-web-page.md)); the app itself did not know it.

This document covers one row in Settings that opens a pre-addressed mail. It does not add a
backend, a form or a moderation view.

## Goals

- Any user, signed in or anonymous, can reach a feedback channel in two taps from the dashboard.
- The mail arrives addressed to the support mailbox with a subject that tells it apart from a
  deletion request: *Zpětná vazba* / *Feedback*.
- A device with no mail app does not fail silently: the user sees the address and can copy it.

## Non-goals

- A Firestore-backed form with categories, reviewed in the moderation section. Considered and
  deferred, see Alternatives.
- Attaching diagnostics (app version, device, user id) to the mail. The body is left empty for the
  user to write.
- Changing how incorrect catalogue data is reported. That stays on the food detail, where the
  user has the item in front of them.

## Design

### Placement

A row under *Export* in the *Other* section of `SettingsView`, not in the account screen.

- The account screen holds identity only (sign-in, sign out, delete account, data attribution).
  An anonymous user, who is the one with the most to say about the app, sees it mostly as a
  sign-in prompt.
- [Design 0006](0006-own-daily-meals.md) already moved a section from the account screen to
  Settings for the same reason: a generic account screen did not match the user's mental model.
- Settings is reachable identically whether or not the user is signed in.

The row is visually the same as *Export*: plain button, primary-coloured title, trailing chevron.
iOS first used a `Link` with an `arrow.up.forward` icon; it took the list's tint colour and read as
a different kind of row, so it was replaced.

### The mail

`mailto:kaloriepodpora@gmail.com` with `subject` set to `settings_button_feedback`
(*Zpětná vazba* / *Feedback*). The address lives in `Constants.Support.email` (iOS) and
`Constants.Support.EMAIL` (Android); it is the same address as on the deletion page.

- **iOS:** `SettingsViewModel.feedbackURL` builds the URL with `URLComponents`, so the subject is
  percent-encoded. The view calls `openURL` with a completion handler.
- **Android:** the view starts an `ACTION_SENDTO` intent on `mailto:` with `EXTRA_EMAIL` and
  `EXTRA_SUBJECT`. The subject is a string resource, which needs a `Context`, so the intent is built
  in the view. There is no `feedbackURL` counterpart and therefore no ViewModel test on Android.

### No mail app

Detecting the missing client is done by the platform's own failure signal, not by asking in
advance:

- **iOS:** `openURL`'s completion receives `false` when nothing opened the URL. This avoids adding
  `mailto` to `LSApplicationQueriesSchemes` in `Info.plist`, which `canOpenURL` would need.
- **Android:** `ActivityNotFoundException` from `startActivity`. `resolveActivity` would need a
  `<queries>` entry in the manifest on Android 11+.

Either failure shows an alert: title *Poštu se nepodařilo otevřít*, a message that names the
address, a *Kopírovat adresu* button and the standard confirmation button. The iOS alert cannot make
text selectable, hence the copy button; the Android message is wrapped in a `SelectionContainer` and
has the same button.

Strings are `settings_button_feedback` and `settings_feedback_noMail_{title,message,copy}` in
`localisation/strings.json`, the message taking the address as its one argument.

### Wording of the confirmation button

The Czech text of `common_ok` is *Dobrá* (English *OK*). It is used on 32 call sites in 29 files on
both platforms and the new alert uses it too. No Czech string in `strings.json` and no literal `OK`
in the Swift, Kotlin or hosting sources contains "OK", so *Dobrá* is applied consistently and there
is no mixed usage to reconcile. Changing it is a single edit to `common_ok` followed by
`npm run generate-strings`.

## Alternatives considered

- **Feedback in the account screen.** Rejected for the reasons under Placement.
- **A form writing to Firestore**, with a category and text, read by the maintainer next to the
  catalogue reports. Rejected for now: it needs rules, a use case, a DTO, a moderation screen and
  UI on both platforms for a channel whose volume is unknown. The mail row can be replaced by it
  later without changing where the user finds it.
- **`MFMailComposeViewController` on iOS.** Rejected: `canSendMail()` is true only with an account
  set up in Apple Mail, so it would report "no mail" for users of Gmail or Outlook, who are the
  people `mailto:` reaches.
- **Detecting a mail client up front** and hiding or disabling the row. Rejected: it needs the
  manifest and `Info.plist` entries above, and a hidden row gives the user no way to reach us.

## Cross-cutting concerns

- **Privacy:** nothing is sent by the app. The user's mail client sends the message from an address
  they choose, so it may not match their sign-in identity.
- **Public address:** it is already public on the deletion page and now also readable in the app
  bundle; expect spam, as [design 0015](0015-account-deletion-web-page.md) already notes.
- **Store requirements:** none beyond the support address the deletion page already provides.

## Risks

- **iOS `openURL` completion not reporting failure on some OS or mail configurations** → no alert
  and no mail, so the tap appears to do nothing → this was not exercised on a device without a mail
  app when written; check it there first, and if it misbehaves fall back to `canOpenURL` with the
  `Info.plist` entry.
- **Feedback and deletion requests share one mailbox** → a request could be missed → the subjects
  differ (*Zpětná vazba* vs *Smazat účet*), so a mailbox filter on the subject separates them.

## Outcome

Implemented in `d4743eb` (iOS) and `9412489` (Android); strings in `62aa2a4`. The first version used a `Link` row and did
nothing without a mail app; both were changed after review, as described under Design. No ADR came
out of it.
