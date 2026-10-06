# Design: Privacy policy

- **Status:** Proposed
- **Scope:** Cross-platform, Backend, iOS, Android
- **Date:** 2026-10-06

## Context and scope

Google Play Console → App content → *Privacy policy* is the last red item before the first Play
release. Play requires a public URL in the Console **and** a link inside the app. App Store
Connect asks for the same URL (App Information → Privacy Policy URL), and App Store Review
Guideline 5.1.1(i) also wants it reachable inside the app, so one page serves both stores.

No privacy policy exists. Firebase Hosting already serves `https://kalorie-bf11c.web.app`
(`backend/firebase.json`, `backend/hosting/`) with `index.html` and `delete-account.html` from
[design 0015](0015-account-deletion-web-page.md), which names the privacy policy as a separate
item out of its scope.

**Boundary:** an inventory of what the apps and the services behind them actually process, a
static bilingual page `backend/hosting/privacy.html`, one link to it on each client (the account
screen), and a mapping of the inventory onto the Play Data safety form and Apple's
App Privacy answers so that the three never contradict each other.

> **The policy text below is a draft, not legal advice.** The owner's answers are recorded under
> *Owner decisions*; nothing is left open.

## Goals

- A public URL that loads without errors, describes exactly what the inventory below lists, and can
  go into both store consoles.
- The page is one tap away in both apps, signed in or anonymous.
- The Play Data safety answers and the App Store App Privacy answers can be filled in from this
  document alone and match the policy.

## Non-goals

- A consent screen or opt-in flow. Nothing in the apps tracks or advertises (see the inventory),
  and the owner decided against an explicit-consent step for the diary (see *Owner decisions*).
- A user-facing switch for crash reporting. The policy does not promise one, see
  [design 0007](0007-crash-reporting-and-logging.md) *Opt-out*.
- Cleaning up abandoned anonymous accounts (see *Risks*).
- Versioned or archived policies. The page carries an *effective date*; old versions live in git.
- Any change to what the apps collect.

## Data inventory

Verified on 2026-10-06 against the code, not only the docs. "App" means data the app itself
writes to our backend; "Google on our behalf" means data a Google SDK sends to Google as our
processor; "Third party" means an independent controller.

### Collected by the app (stored in Cloud Firestore, project `kalorie-bf11c`)

| Data | Where | Purpose | Retention | Deleted by in-app deletion |
|---|---|---|---|---|
| Firebase Auth user id (uid), anonymous or linked | Firebase Authentication | Ties all data below to one user | Until the account is deleted | Yes |
| Display name and e-mail from Google sign-in (iOS and Android), or from Sign in with Apple (iOS; may be a private relay address, only if the user shares it) | Firebase Authentication and `users/{uid}` (`UserProfileDTO`; written by `SignInWithGoogleUseCase` / `SignInWithAppleUseCase`, never read back) | Account management | Until the account is deleted | Yes (`users/{uid}` and the Auth user) |
| Food diary: food, amount, time, meal type, nutrition snapshot | `users/{uid}/foodConsumed` | Core feature | Until the user deletes the entry or the account | Yes |
| Meal windows (names, times) | `users/{uid}/mealTypes` | Core feature | Same | Yes |
| Favourite foods, own meals, personal portions | `users/{uid}/favouriteFoods`, `myCreatedMeals`, `foodItemPortions` | Core feature | Same | Yes |
| Per-food log counts | `users/{uid}/stats/foodFrequency` ([design 0020](0020-search-ranked-by-log-frequency.md)) | Ranking search results | Same | Yes |
| Catalogue submissions: the submitted food data, `submitted_by` (uid), `submitted_at`, maintainer's `reject_reason` | `foodItemSubmissions` ([design 0009](0009-catalogue-moderation.md)) | Moderated contribution to the shared catalogue | Pending/rejected: until the author withdraws it, the maintainer approves it, or the account is deleted | Yes |
| An **approved** submission's food data | `foodItems` | Shared catalogue | Indefinitely | **No** — it carries no uid or author (`validFoodItem` has no author field), so it is not personal data |
| Incorrect-data reports: barcode, `reported_by` (uid), free-text `reason` (≤ 500 chars), `reported_at` | `foodItemReports` ([design 0012](0012-report-incorrect-catalogue-data.md)) | Correcting the catalogue | Until the maintainer resolves it or the account is deleted | Yes |

The `reason` and submitted food names are free text: a user *could* type personal data into them.
The policy says so and asks them not to.

### Collected by Google on our behalf (processor)

| SDK | Platforms | What it sends | Purpose | Retention |
|---|---|---|---|---|
| Firebase Authentication | both | IP address, user agent, Firebase app id, sign-in tokens | Sign-in, abuse prevention | IP: "a few weeks"; account data until we delete the user, then out of Google's backups within 180 days ([Firebase privacy](https://firebase.google.com/support/privacy)) |
| Cloud Firestore | both | Firebase user agent; the documents above; search query text in the request (not stored) | Storage | Documents as above |
| Firebase Crashlytics | both | Stack traces, app state at the crash, device model, OS version, a Crashlytics installation UUID; non-fatal errors from `Log.error` | Crash reporting | 90 days (Crashlytics default) |
| Google Sign-In / Credential Manager (Google Play services on Android, `GoogleSignIn` on iOS) | both | The Google account the user picks | Sign-in | Governed by Google's own account terms |
| ML Kit barcode scanning + text recognition, **bundled** models (`com.google.mlkit:barcode-scanning`, `com.google.mlkit:text-recognition`) | Android | Image processing is on-device. ML Kit itself sends Google diagnostics: device and app info, a per-installation id, latency, API configuration, error codes ([ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)). **No images or recognised text.** | ML Kit diagnostics | Google's |

Crashlytics has **no** user id, custom keys or custom logs attached: no `setUserId` /
`setCustomKey` anywhere in `iOS/Kalorie` or `Android/app/src/main`. Collection is off in debug
builds on both clients (`KalorieApp.swift`, `KalorieApplication.kt`). This is why the deletion page
already says crash reports are not linked to the account; keep it that way, or the policy and the
deletion page both become wrong.

### Sent to a third party (independent controller)

| Recipient | When | What | Notes |
|---|---|---|---|
| Open Food Facts (`world.openfoodfacts.org`, HTTPS) | A barcode scan or typed search finds nothing in our catalogue ([ADR 0012](../adr/0012-external-food-is-surfaced-never-imported.md), [ADR 0023](../adr/0023-external-search-gate-includes-favourites-and-meals.md)) | The search text or barcode, the device's IP address, a `User-Agent` with the app name and version. No uid, no account data. | Governed by Open Food Facts' own privacy policy. Its data is credited on the account screen already. |

### Processed only on the device (not collected)

- Camera frames for barcode scanning and nutrition-label reading: iOS VisionKit `DataScanner`,
  Vision OCR and Apple Foundation Models ([design 0010](0010-nutrition-label-photo-prefill.md));
  Android CameraX + bundled ML Kit. No image leaves the device, none is stored.
- PDF / Excel export ([design 0014](0014-data-export.md)): the file is generated on the device and
  handed to the system share sheet; where it goes is the user's choice.
- Feedback ([design 0018](0018-feedback-by-mail.md)): the app opens the user's mail client;
  the app sends nothing. Mail that reaches `kaloriepodpora@gmail.com` is held in that Gmail mailbox.

### Not present (checked)

Analytics (`IS_ANALYTICS_ENABLED = false` in `GoogleService-Info.plist`; no
`firebase-analytics` dependency on Android), advertising and the advertising id, location,
contacts, photo library or file picker, Firebase Storage, push messaging SDKs
(`IS_GCM_ENABLED` is `true` in the plist but no messaging SDK is linked), cross-app tracking.
Android permissions are only `INTERNET` and `CAMERA`; iOS declares only `NSCameraUsageDescription`.

## Design

### Page

- File `backend/hosting/privacy.html`, URL `https://kalorie-bf11c.web.app/privacy`
  (`cleanUrls` is already on).
- Same form as `delete-account.html`: one self-contained file, the same inline `<style>` block,
  no JavaScript, no external requests, Czech first, `<hr>`, then English. `lang="cs"` on `<html>`,
  `lang="en"` on the English half's wrapper.
- Title: `Kalorie – Zásady ochrany osobních údajů / Privacy policy`.
- Links: `index.html` gains a second link to `/privacy`; `delete-account.html` gains one line
  linking to `/privacy` at the bottom of each language half; the policy links to `/delete-account`.

### Policy text (English draft; translate to Czech for the first half)

Sections in this order. Placeholders are in `[brackets]`.

1. **Who we are.** "Kalorie is developed by Josef Antoni, who is the controller of your personal
   data. Contact: kaloriepodpora@gmail.com. This policy is effective from [date of deployment]."
   No postal address (see *Owner decisions*).
2. **Using the app without an account.** "You can use Kalorie without signing in. The app then
   creates an anonymous account identified only by a random ID. Everything you record is stored
   under that ID on our servers; we cannot link it to your name or e-mail."
3. **What we store and why.** One short bullet per row of *Collected by the app*: account
   (name and e-mail from Google or Apple, only when you sign in), food diary and meal windows,
   favourites, own meals and portions, how often you log each food (to rank search results),
   foods you submit to the shared catalogue, and reports of incorrect food data. Add: "Do not write
   personal information into food names or report text; these may be read by the maintainer."
4. **Camera.** "The camera is used to read barcodes and nutrition labels. Images are processed on
   your device and are never uploaded or stored."
5. **Services we use.** Google Firebase (Authentication, Cloud Firestore, Crashlytics) as our
   processor, Google Sign-In, Sign in with Apple (iOS), Google ML Kit (Android, on-device; sends
   Google technical diagnostics only), Open Food Facts (what it receives and when, as in the
   inventory). Link each provider's privacy policy. State that the diary and everything else in
   Cloud Firestore is stored in the EU (location `eur3`, Belgium and the Netherlands), and that
   Firebase Authentication and Crashlytics may process data in the USA, where Google LLC is
   certified under the EU–US Data Privacy Framework.
6. **Crash reports.** What Crashlytics collects, that it is not linked to your account, kept 90 days.
7. **What we do not do.** No advertising, no analytics, no tracking across apps, no selling or
   sharing data for marketing.
8. **How long we keep data.** Until you delete it or your account. Approved catalogue foods stay,
   with no link to you. Crash reports 90 days. After deletion, Google removes the data from its
   backups within 180 days. E-mails you send us are deleted no later than 30 days after your
   request has been handled.
9. **Deleting your data.** In the app: Account → *Delete account* (exact label from
   `account_button_deleteAccount`). Without the app: link to `/delete-account`. Uninstalling the
   app does not delete server data.
10. **Your rights.** Access, rectification, erasure, restriction, portability (note the PDF/Excel
    export), objection, withdrawal of consent where consent is the basis, and complaint to the Czech
    supervisory authority, Úřad pro ochranu osobních údajů (<https://uoou.gov.cz>). Requests go to
    the contact e-mail. Legal bases: the account, diary, submissions and reports — performance of
    the service you asked for (Art. 6(1)(b) GDPR); crash reports and handling e-mails — legitimate
    interest in a working app and in answering you (Art. 6(1)(f)).
11. **Children.** "Kalorie is intended for users aged 18 and over."
12. **Changes.** "We will update this page and its effective date."

### Links in the apps

Both clients, same URL, same string.

- **String:** one new key in `localisation/strings.json`, `common_privacyPolicy`:
  cs *Zásady ochrany osobních údajů*, en *Privacy policy*. Run the generator
  (`npm run generate-strings` in `scripts/`) and commit the generated `Localizable.xcstrings` and
  both `strings.xml` with it. iOS also needs the hand-written `L10n` entry
  ([ADR 0019](../adr/0019-l10n-enum-over-the-string-catalogue.md)).
- **Constant:** `Constants.Support.privacyPolicyURL` (iOS, `iOS/Kalorie/Core/Utils/Constants.swift`)
  and `Constants.Support.PRIVACY_POLICY_URL` (Android,
  `Android/app/src/main/java/antoni/kalorie/core/utils/Constants.kt`), next to the support e-mail.
- **Account screen** (`AccountView.swift`, `AccountView.kt`): a second link in the bottom
  attribution bar, under the Open Food Facts link, same style. This is where the user signs in and
  hands over their e-mail, so the policy is visible at that moment.
- No ViewModel change and no unit test: the link is a constant and a view action. Verified by hand.

### Store consoles

**Play Console → App content → Privacy policy:** `https://kalorie-bf11c.web.app/privacy`.
**App Store Connect → App Information → Privacy Policy URL:** the same.

### Play Data safety form

Answers that follow from the inventory. A Google SDK acting as our processor is *collected*, not
*shared* (service-provider exemption).

- Does the app collect or share required user data types? **Yes.**
- Is all user data encrypted in transit? **Yes** (Firebase and Open Food Facts are HTTPS only).
- Account creation: **Yes** — OAuth (Google) and anonymous. Deletion URL:
  `https://kalorie-bf11c.web.app/delete-account`. Users can request data deletion: **Yes.**

| Play data type | Collected | Shared | Ephemeral | Required / optional | Purposes |
|---|---|---|---|---|---|
| Personal info → **Name** | Yes | No | No | Optional (only on sign-in) | Account management |
| Personal info → **Email address** | Yes | No | No | Optional | Account management |
| Personal info → **User IDs** (Firebase uid) | Yes | No | No | Required | App functionality, Account management |
| Health and fitness → **Health info** (food diary) | Yes | No | No | Required | App functionality |
| App activity → **In-app search history** | Yes | **Yes** (Open Food Facts) | Yes | Required | App functionality |
| App activity → **Other user-generated content** (submissions, reports) | Yes | No | No | Optional | App functionality |
| App info and performance → **Crash logs** | Yes | No | No | Required | Analytics *(Play's label for diagnostics)* |
| App info and performance → **Diagnostics** (Crashlytics non-fatals, ML Kit metrics) | Yes | No | No | Required | Analytics |
| Device or other IDs (Crashlytics installation UUID, ML Kit per-installation id) | Yes | No | No | Required | Analytics |
| Photos / Videos / Location / Contacts / Financial / Messages / Audio / Files | No | — | — | — | — |

Judgement calls, recommended answer first:

- **Health info vs Fitness info.** Play defines neither as diet. *Health info* is the conservative
  reading for a food and calorie diary and matches the Art. 9 risk under *Owner decisions*.
- **Search history shared with Open Food Facts.** Conservative: *shared*. The alternative is the
  user-initiated exemption, but the fallback fires automatically and is not disclosed at the moment
  it happens. Over-declaring is not a policy violation; under-declaring is.
- **Search history collected at all.** The query text is never stored, which is *ephemeral*; Play
  still asks it to be declared and marked as such.
- **Crashlytics "Required".** There is no in-app switch, so it cannot be optional.

### App Store App Privacy (for consistency)

Same inventory: *Data Linked to You* — Contact Info (name, e-mail), Health & Fitness (food diary),
User Content (submissions, reports), Identifiers (User ID), Search History. *Data Not Linked to You*
— Diagnostics (crash data). *Tracking:* none. ML Kit is Android-only; the iOS camera pipeline is
on-device and collects nothing.

### Owner decisions (2026-10-06)

1. **Controller:** Josef Antoni, as on the deletion page. Contact is the e-mail
   `kaloriepodpora@gmail.com` only, with no postal address. GDPR Art. 13(1)(a) asks for the
   controller's identity and contact details, and an e-mail address is a contact detail.
2. **No explicit-consent step.** Whether a food diary is health data under Art. 9 has no settled
   answer. The CJEU reads "data concerning health" broadly
   ([C-21/23 *Lindenapotheke*](https://curia.europa.eu/juris/liste.jsf?num=C-21/23): data that
   *can* reveal health status counts). Some trackers treat diaries as sensitive (MyFitnessPal's
   policy says so); others rely on Art. 6 alone (YAZIO). Kalorie stores no weight, goals, body
   measurements or conditions, only what was eaten and when. The owner accepted the risk: the
   policy states Art. 6(1)(b), and the apps do not change. If this is ever revisited, the fix is a
   first-launch consent screen on both clients, in its own design doc.
3. **Data location:** Firestore `(default)` database is in `eur3` (checked with
   `firebase firestore:databases:get "(default)" --project kalorie-bf11c`). Auth and Crashlytics
   are global Google services covered by the Data Privacy Framework.
4. **Minimum age: 18.** Stricter than the Czech digital-consent age of 15, which is allowed. In
   Play Console → App content → Target audience, select only 18+.
5. **Support e-mails:** deleted no later than 30 days after the request is handled. Mechanism, no
   script needed: once a request is handled, move the thread to Gmail's Trash, which Gmail empties
   automatically after 30 days. Add this as the last step of the deletion runbook in
   `docs/SETUP.md` and as a note for feedback mail.

### Implementation order and done criteria

1. `backend/hosting/privacy.html`, plus the links in `index.html` and `delete-account.html` →
   one `[FEAT]` commit (backend).
   Same commit: the `docs/SETUP.md` runbook gains the Gmail Trash step (*Owner decisions* 5) and
   the *Keeping the policy true* line — or a separate `[DOCS]` commit if that reads better.
   *Done:* every row of the inventory appears in both language halves; the effective date is
   filled in (`grep -n "\[" backend/hosting/privacy.html` is empty); the two halves say the same
   thing.
2. The owner deploys with `firebase deploy --only hosting` from `backend/` (never a bare
   `firebase deploy`, see `docs/SETUP.md`) and opens `/privacy` on a phone.
   *Done:* loads, readable at phone width in light and dark mode, links to `/delete-account` and
   the provider policies work.
3. Strings (`common_privacyPolicy`) → one `[FEAT]` commit, like `62aa2a4`.
   *Done:* the CI `l10n` job's check passes locally.
4. iOS: constant, `L10n`, account-screen link → one `[FEAT]` commit.
   *Done:* builds; the link opens the page in Safari, anonymous and signed in.
5. Android: constant, account-screen link → one `[FEAT]` commit.
   *Done:* builds; the link opens the page in the browser, anonymous and signed in.
6. Owner pastes the URL into Play Console (Privacy policy) and App Store Connect, and fills Data
   safety and App Privacy from the tables above. Then remove the *Privacy policy* item from
   `TODO.md`.

## Alternatives considered

- **Generated policy (a policy-generator service).** Rejected: generic text lists data the app
  does not collect and misses what it does (Open Food Facts, ML Kit diagnostics), which is exactly
  the mismatch Play reviews flag.
- **Policy inside the app only (a native screen).** Rejected: both stores need a public URL anyway,
  and two copies drift. The app links to the web page.
- **Hosting on GitHub Pages or a Google Doc.** Rejected for the same reason design 0015 rejected
  it: the Firebase project already owns the domain and the deletion page.
- **A second link in Settings.** Rejected: the account screen is where the e-mail is handed over,
  and one entry point per client is enough.

## Cross-cutting concerns

- **Store requirements:** Play (URL in Console + in-app link + Data safety consistent with policy),
  App Store (URL in App Store Connect + in-app access + App Privacy consistent with policy).
- **Keeping the policy true:** the policy, the deletion page, `wipeFirestoreData` in both
  `DeleteAccountUseCase` files and the `docs/SETUP.md` runbook all describe the same per-user data.
  A new per-user collection, a new SDK, analytics, or a Crashlytics user id changes all of them and
  the store forms. Add that line to `docs/SETUP.md` next to the existing runbook note.
- **Existing gap found:** `delete-account.html` *What is deleted* does not list the per-food log
  counts added by design 0020 (`stats/foodFrequency`), though both use cases and the runbook's
  recursive delete do remove them. Fix the page text in step 2.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| A feature adds collection without updating the policy | Policy and Data safety become false; Play can reject or remove the app | The *Keeping the policy true* line in `docs/SETUP.md`; this document's inventory as the checklist |
| Diary data is health data under Art. 9 and no explicit consent exists | GDPR exposure | Accepted by the owner (*Owner decisions* 2); the remedy, if needed, is a first-launch consent screen |
| Handled e-mails are not moved to Trash | The 30-day promise is broken | The runbook step; the same mailbox habit covers feedback mail |
| Anonymous accounts are never deleted (Firebase does not expire them; uninstalling can lose the uid) | Orphaned diary data kept indefinitely, against "until you delete it" | The policy states it honestly; a cleanup job is out of scope and would need Cloud Functions (Blaze plan) — raise separately if it matters |
| Crashlytics later gets a user id or custom logs | The deletion page's "not linked to your account" becomes false | Called out under *Collected by Google on our behalf* |

## Outcome

_Filled in once after shipping._
