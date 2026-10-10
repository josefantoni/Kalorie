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
  must match the published build. The privacy policy page (`backend/hosting/privacy.html`) already
  describes the photos; before the apps are sent to the stores, change its effective date (both
  language halves) to the deploy date, re-read it against the shipped behaviour, and deploy it.
- **Firebase App Check** — Storage and Firestore rules only check who writes, not what writes, so
  anyone with an account can upload files (up to the rules' limits) from a script and the Blaze bill
  is ours. App Check makes the backend accept requests only from genuine builds: App Attest on iOS,
  Play Integrity on Android, a debug provider for simulators and development. Ship both clients in
  monitoring mode first and switch on enforcement for Storage (then Firestore) once the console
  metrics show legitimate traffic, otherwise the app locks itself out. Needs the App Attest
  capability on iOS and the Play Console link on Android. Not blocking the food photos release, but
  do it soon after it.

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
- **Apple sign-in on Android** — Firebase offers it only through a web OAuth flow that needs an
  Apple Services ID this project does not have. The iOS app currently signs in with Google only,
  since there is no paid Apple Developer account, so this waits for both.
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
