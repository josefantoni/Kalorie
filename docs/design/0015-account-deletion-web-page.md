# Design: Account deletion web page for Google Play

- **Status:** Proposed
- **Scope:** Backend
- **Date:** 2026-09-26

## Context and scope

Google Play requires every app that lets users create an account, third-party sign-in included,
to give a **web link** where the account and its data can be deleted without the app installed.
The link goes into Play Console → App content → Data safety. `TODO.md` tracks this as
*Play Store account deletion*, a pre-release gate next to the Play App Signing SHA-1s.

Policy as checked on 2026-09-26
(<https://support.google.com/googleplay/android-developer/answer/13327111>):

- The link may lead to **a request pathway**: "a customer service email or a form they can
  submit a request through". Self-service deletion on the web is not required.
- The page must load without errors, show the deletion pathway prominently, and name **the app or
  developer name as it appears on the store listing**.
- Data kept after deletion, and for how long, must be disclosed.

Relevant existing pieces:

- In-app deletion exists on both clients: `iOS/Kalorie/Core/UseCases/DeleteAccountUseCase.swift`
  and `Android/app/src/main/java/antoni/kalorie/core/usecases/DeleteAccountUseCase.kt`. Both do
  the same thing in the same order (`wipeFirestoreData`, then deleting the Firebase Auth user).
  That order is the reference for what "all data" means (see *Manual deletion runbook*).
- There is no Cloud Functions code and no Hosting. `backend/firebase.json` configures only
  Firestore rules, indexes and the emulator. The Firebase project is `kalorie-bf11c`
  (`backend/.firebaserc`).
- The catalogue (`foodItems`) stores no author: `validFoodItem` in `backend/firestore.rules`
  has no uid field. An item approved from a user's submission therefore keeps no personal data
  and stays after deletion.

**Boundary:** a static page on Firebase Hosting that explains in-app deletion and takes deletion
requests by email, plus a runbook the maintainer follows when a request arrives. Nothing in the
apps changes.

## Goals

- A public URL that meets the Play policy above and can go into the Data safety form.
- Every request gets the same result as in-app deletion, following a written procedure.

## Non-goals

- Self-service deletion on the web (see *Alternatives considered*).
- A privacy policy page. Play requires one as well, but it is a separate item. If it lands on the
  same Hosting site later, link it from this page.
- Any change to iOS or Android.

## Design

### Hosting

Add Firebase Hosting to `backend/firebase.json`, with the public directory next to it:

```json
"hosting": {
  "public": "hosting",
  "ignore": ["firebase.json", "**/.*"],
  "cleanUrls": true
}
```

- Files: `backend/hosting/delete-account.html` and a minimal `backend/hosting/index.html` that
  links to it, so the root URL does not 404.
- Public URL: `https://kalorie-bf11c.web.app/delete-account`.
- Hosting is available on the free Spark plan. Nothing else needs to be enabled.
- Deploy by hand from `backend/`: `firebase deploy --only hosting`. **Never** run a bare
  `firebase deploy`. It would redeploy the Firestore rules and indexes as well.
- CI does not deploy it and no workflow changes. `backend/` has no CI job that this would
  trigger.

### Page content

A single self-contained HTML file: inline CSS, no JavaScript, no external requests (no fonts,
no analytics). Czech first, then English, on the same page. The apps ship in both languages, so
no language switcher.

Sections, in this order:

1. **Heading** with the app name **exactly as on the Play store listing** (placeholder until the
   listing exists, see *Open inputs*). The developer name goes at the end of the section instead
   of next to the heading: the policy only requires it to be present somewhere on the page, and
   the deletion pathway is what needs to be prominent, not the attribution line.
2. **Delete in the app** (the fastest way): *Account* screen (the person icon in the dashboard
   toolbar) → *Delete account*. Before writing the button label, check the exact text in the
   `account_*` keys of `localisation/strings.json`. Mention that the app may ask the user to sign
   in again first.
3. **Delete without the app**: send an email to the support address from the email the account
   uses to sign in (the Google account; on iOS, possibly an Apple private relay address), with the
   subject matching `account_button_deleteAccount` in `localisation/strings.json` for that
   section's language (`Smazat účet` / `Delete account`). Include a `mailto:` link with the
   subject prefilled. State the response time: deletion within 30 days, confirmation email when
   it is done. The runbook matches requests by sender address, not subject text, so either
   subject is handled the same way.
4. **What is deleted**: food diary entries, meal types, favourite foods, own meals, personal
   portions, pending or rejected catalogue submissions, open incorrect-data reports, and the
   sign-in account itself.
5. **What is kept**: catalogue items already approved from the user's submissions. They contain
   no personal data or author. Before writing this, check `docs/design/0007-crash-reporting-and-logging.md`
   to see whether crash reports can be linked to a user. If they cannot, say that nothing else is
   kept. If they can, state their retention period.
6. **Anonymous use**: data created without signing in is tied to a random ID on the device and
   cannot be matched to an email. The user deletes it in the app the same way.

### Manual deletion runbook

Add it to `docs/SETUP.md` as a new section *Handling an account deletion request* (SETUP.md is the
living runbook, see `docs/README.md`). Steps:

1. Firebase Console → Authentication → Users → search the sender's email → copy the **User UID**.
   If there is no match, reply that no account exists for that address, and stop.
2. Delete the user subtree recursively (covers `mealTypes`, `foodConsumed`, `favouriteFoods`,
   `myCreatedMeals`, `foodItemPortions` and the `users/{uid}` document itself):
   `firebase firestore:delete users/<UID> --recursive --project kalorie-bf11c`
3. Firestore console → `foodItemSubmissions` → filter `submitted_by == <UID>` → delete each
   document.
4. Firestore console → `foodItemReports` → filter `reported_by == <UID>` → delete each document
   (the ids have the form `<barcode>_<UID>`).
5. Authentication → the user → *Delete account*.
6. Reply to the requester that it is done.

The collection list must match `wipeFirestoreData` in both `DeleteAccountUseCase` files. Add one
line to the runbook saying so, so whoever adds a new per-user collection updates both places.

### Open inputs (from the user, before implementation)

- **Support email address** shown on the page. It is public and will get spam. A dedicated
  address or alias is better than a personal one.
- **App name and developer name** as they will appear on the Play store listing.

### Implementation order

1. Get the open inputs.
2. `backend/firebase.json` + `backend/hosting/*.html` → one `[FEAT]` commit.
3. `docs/SETUP.md` runbook → one `[DOCS]` commit.
4. The user deploys (`firebase deploy --only hosting`) and opens the URL on a phone. It must load,
   be readable at phone width, and the `mailto:` link must open a mail client with the subject
   filled in.
5. The user pastes the URL into Play Console → Data safety once the app exists there. After that,
   remove the *Play Store account deletion* item from `TODO.md`.

## Alternatives considered

**Self-service web deletion (Firebase JS SDK: Google sign-in + the same wipe as
`DeleteAccountUseCase`).** Rejected for now. The policy does not require it, and it would be a
third copy of the wipe logic to keep in sync with Swift and Kotlin. It also needs a Google OAuth
web client and authorised domains. And it cannot serve Apple sign-in accounts: web Apple sign-in
needs a Services ID the project does not have (`TODO.md`, *Apple sign-in on Android*). Revisit if
requests become frequent.

**Cloud Function that deletes on a signed request.** Rejected. It needs the Blaze plan and
introduces a server runtime the project does not have, for something rare.

**GitHub Pages.** Rejected. It depends on the repository's visibility and puts the page outside
the Firebase project that already owns the domain and the data.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| A new per-user collection is added to the apps but not to the runbook | Manual deletion leaves data behind | The runbook line pointing to `wipeFirestoreData` in both use cases |
| A request comes from an email that does not own the account | Someone deletes another person's account | Act only on mail sent **from** the account's sign-in address; never delete on a request that only names an address |
| `firebase deploy` run without `--only hosting` | Rules and indexes redeployed from a working tree that may be ahead of production | The command is written out in full in the runbook |

## Outcome

_Filled in once after shipping._
