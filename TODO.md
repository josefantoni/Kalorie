# Kalorie — TODO

## Kinds of data

The app works with three kinds of data. The distinction matters for the items below:

- **Shared food catalogue** (`foodItems`) — a global database visible to everyone. New entries
  are added by the app maintainer, or approved from user submissions.
- **Private user data** (`users/{userId}/foodConsumed`, `mealTypes`) — food entries and meal
  layout, visible only to their owner.
- **User's own meals** — meals a user composes themselves (see below). These stay private and
  do **not** go through approval.

## Planned features

- **Food photos** — designed in [design 0022](docs/design/0022-food-photos.md). The Blaze plan
  (budget alert 150 CZK/month) and the default Storage bucket (`us-central1`) are set up. Before
  the release that ships it: update the App Store privacy label and the Google Play Data safety form
  (*Photos*) — see the design's *Privacy* section. Do not change the store declarations earlier, they
  must match the published build.
- **Firebase App Check** — Storage and Firestore rules only check who writes, not what writes, so
  anyone with an account can upload files (up to the rules' limits) from a script and the Blaze bill
  is ours. App Check makes the backend accept requests only from genuine builds: App Attest on iOS,
  Play Integrity on Android, a debug provider for simulators and development. Ship both clients in
  monitoring mode first and switch on enforcement for Storage (then Firestore) once the console
  metrics show legitimate traffic, otherwise the app locks itself out. Needs the App Attest
  capability on iOS and the Play Console link on Android. Not blocking the food photos release, but
  do it soon after it.
- **Support development (tips)** — designed in [design 0023](docs/design/0023-support-development-tips.md) and
  implemented on both platforms and switched off by `Constants.Tips.isEnabled`, so the apps ship without
  tips. Blocked on the owner's trade licence being suspended: income from the stores
  likely counts as business income, so decide with an accountant before enabling. To enable: renew the
  licence, create the Play payments profile and the three consumable products (`tip_small`, `tip_medium`,
  `tip_large`), accept the App Store Connect *Paid Apps* agreement and create the same products (neutral
  names, Czech + English descriptions), decide the EU trader address, then set `Constants.Tips.isEnabled` / `IS_ENABLED` to `true` on both platforms and restore the tips paragraph in
  `backend/hosting/privacy.html` (Apple / Google process the payment, the app receives no card or purchase
  data) and test a real purchase from Internal testing with a license tester.
- **First Play release needs a closed test** — a personal developer account created after Nov 2023
  gets production access only after a closed test with at least 12 testers opted in for 14 days in a
  row. Start it as early as possible; it is also the cheapest way to find out whether anyone uses the app.

- **Store release checklist** (state on 2026-10-16):
  - *Done:* iOS build 2 is archived and sent to TestFlight; Sign in with Apple is enabled for the App ID, the
    Firebase Apple provider and `Kalorie.entitlements`; App Privacy answers (8 data types, no tracking) are saved
    in App Store Connect **but not published**; the privacy policy (photos, Apple, tips) is deployed, effective
    16 October 2026. The Play app `antoni.kalorie` exists, build 1.0 (2) is rolled out to Internal testing and
    Google sign-in works on a device installed from Play.
  - *Still to do, user's console work:* press **Publish** on App Store Connect → App Privacy; fill the Play
    **Data safety** form from the table in [design 0021](docs/design/0021-privacy-policy.md) plus *Photos —
    collected, not shared, App functionality, optional*, and the other Play App content declarations (content
    rating, target audience, ads); store listings and screenshots for both stores.
  - *iOS beta:* a public TestFlight link needs an **External testing** group and Beta App Review.
  - *Open question for a lawyer:* whether a food diary is health data under GDPR Art. 9 (design 0021 chose
    no explicit-consent step).
- **Verify the duplicate default meals fix** — default meal types now get fixed ids from MealKit's
  `DEFAULT_MEAL_WINDOWS` (the setup raced and wrote them twice, 10 instead of 5, on 4 of 24 accounts).
  The day after the fix ships to a device, sign in with fresh anonymous accounts on both platforms and
  count `users/{uid}/mealTypes` in prod Firestore: it must be exactly 5 for every new account.

## Android readiness

The shared KMP modules build for Android ([ADR 0037](docs/adr/0037-shared-modules-target-ios-and-jvm-and-are-consumed-by-composite-build.md)).
The Android client in `Android/` ([ADR 0038](docs/adr/0038-android-client-mirrors-the-ios-architecture-natively.md))
has every screen of the iOS app ported; how each step deviated from iOS is recorded in the last version of
this file that still has the steps (`git show 60dcabb:TODO.md`). Nothing is open at the moment.

## Documentation baseline

[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) describes what exists; `docs/adr/` records the
decisions still in effect. Findings are grouped by area and numbered `A<area>-<n>` — only open
ones are listed below; closed findings live in git history, not here.
