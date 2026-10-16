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
- **Support development (tips)** — designed in [design 0023](docs/design/0023-support-development-tips.md):
  three consumable products (`tip_small`, `tip_medium`, `tip_large`) through store billing. What is
  left is the console work and the first real purchase: Play Console payments profile and the three
  in-app products, App Store Connect *Paid Apps* agreement and the products (use neutral names and
  Czech + English descriptions there), the EU trader status decision (the stores publish the
  developer's address), and an accountant for the trade licence and taxes. Test a real purchase on
  Android from the internal testing track with a license tester.
- **First Play release needs a closed test** — a personal developer account created after Nov 2023
  gets production access only after a closed test with at least 12 testers opted in for 14 days in a
  row. Start it as early as possible; it is also the cheapest way to find out whether anyone uses the app.

- **Store release checklist after the Apple/Google developer accounts exist** (state on 2026-10-15):
  - *Done:* iOS build 1 is in TestFlight; Sign in with Apple is enabled for the App ID, the Firebase
    Apple provider and `Kalorie.entitlements`; App Privacy answers (8 data types, no tracking) are saved in
    App Store Connect **but not published**; the privacy policy (photos, Apple, tips) is deployed, effective 16 October 2026.
  - *Still to do, user's console work:* press **Publish** on App Store Connect → App Privacy; fill the Play
    **Data safety** form from the table in [design 0021](docs/design/0021-privacy-policy.md) plus *Photos —
    collected, not shared, App functionality, optional*; create the Play app `antoni.kalorie`, upload
    `Android/app/build/outputs/bundle/release/app-release.aab` (`./gradlew :app:bundleRelease`) to Internal
    testing, then add the *App signing* and *Upload* SHA-1/SHA-256 to Firebase, re-download
    `google-services.json` into `Android/app/` and update the `GOOGLE_SERVICES_JSON` CI secret.
  - *iOS beta:* a public TestFlight link needs an **External testing** group and Beta App Review (the
    build must carry a new `CURRENT_PROJECT_VERSION` after the fix above, so build 2).
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
this file that still has the steps (`git show 60dcabb:TODO.md`). What is still open:

- **Verify the nutrition-label OCR on a device with a good camera** — the barcode scanner reads codes
  on a real Android device, but that device's camera is poor, so the OCR accuracy is untested; the
  label OCR has only been checked by unit tests. Check: ML Kit reads cs/pl/de/en labels including
  diacritics, box coordinates hold under a rotated frame (rows and columns must not collapse), the
  3-second scan window fills the form, and the permission prompt, denied state and revocation behave.
  Real-world glare and curved packaging still need checking before release.
- **Android release signing** — Google sign-in works for the debug keystore and the Play App
  Signing key (both fingerprints are in Firebase and in the `GOOGLE_SERVICES_JSON` CI secret,
  `docs/SETUP.md` Android section). The upload keystore exists and `signingConfigs.release` uses it
  (`docs/SETUP.md` Android section). Still to do before the first Play release:

  **Handoff (analysed 2026-10-08).** The upload key's fingerprints appear in Play Console
  (Protected with Play → App signing) only after the first app bundle is uploaded; add them to
  Firebase too (needed only for release builds signed and installed locally), then **tell the user**
  to re-download `google-services.json` and update the `GOOGLE_SERVICES_JSON` CI secret (base64 of
  the file, `docs/SETUP.md` § CI) — nothing updates it automatically. Pre-release gate.
- **Check the recent Android fixes by hand on an emulator or device** — they were only built and unit
  tested; the camera and the touch handling cannot be covered that way. The barcode scanner must release
  the camera when its dialog is dismissed (the indicator goes off); and approving and rejecting a
  submission that was written from the iOS app must work (it failed on `submitted_at` precision).

## Documentation baseline

[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) describes what exists; `docs/adr/` records the
decisions still in effect. Findings are grouped by area and numbered `A<area>-<n>` — only open
ones are listed below; closed findings live in git history, not here.
