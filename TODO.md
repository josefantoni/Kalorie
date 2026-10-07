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

- **Packaging photo on a submission** — the other half of the user-submitted-food flow shipped
  in [design 0009](docs/design/0009-catalogue-moderation.md), deferred by that design's Non-goals:
  Firebase Storage is not configured in this project, and whether the project's plan includes free
  Storage quota is unverified. Needs a `storage` block in `firebase.json`, `storage.rules`, and a
  Blaze-plan check before starting. Additive once it lands — `FoodItemSubmissionDTO` gains an
  optional `photo_path`, no other schema change.
- **Frequency-derived quick-add gram amounts** — per-user "frequently added weights", gram
  amounts the user logs often for a given food (e.g. 50g oats almost daily, one slice of bread),
  surfaced as quick-add options, likely derived from `foodConsumed` history rather than manually
  maintained. This is the other half of what used to be one combined line here; the first half —
  a food carrying a named, user-chosen package/portion weight selectable as a unit — shipped as
  [design 0008](docs/design/0008-food-portions.md).

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
- **Firebase setup for an Android app** — the Firebase console side is done (app
  `antoni.kalorie`, debug SHA-1, `google-services.json`), and Google sign-in was verified by hand on a
  debug build (`docs/SETUP.md` Android section). Still to add: the release and Play App Signing SHA-1s.

  **Handoff (analysed 2026-09-24).** One part left, blocked on the user.

  1. **Release and Play App Signing SHA-1s — blocked on the user, before the first Play release.**
     No release keystore exists and `app/build.gradle.kts`'s `release` block has no `signingConfig`.
     With Play App Signing (the default for new apps), users' installs are signed by **Google's**
     app-signing key, so that key's SHA-1 (Play Console → App integrity) is the one Google sign-in
     needs in production; the upload key's SHA-1 only matters for release builds signed and
     installed locally. Neither fingerprint exists until a Play Console account exists and the app
     is created in it. Decided 2026-09-24: the Android app will ship on Google Play alongside the
     App Store, so this is a pre-release gate, not an open question — it waits only on the user
     creating the Play Console developer account (one-time fee; Google Play's counterpart of App
     Store Connect). Nothing earlier depends on it: development and testing use the already
     registered debug keystore. When it happens: create the upload keystore with `keytool` outside the repo, wire
     `signingConfigs.release` to read its path and passwords from `~/.gradle/gradle.properties` or
     env vars (never committed), add both SHA-1 and SHA-256 fingerprints in Firebase → Project
     settings → the Android app, re-download `google-services.json`, and **tell the user** to update
     the `GOOGLE_SERVICES_JSON` CI secret (base64 of the file, `docs/SETUP.md` § CI) — nothing
     updates it automatically. Belongs next to *Play Store account deletion*, the other
     pre-release gate.
- **Play Store account deletion** — Google Play requires a web link for requesting account
  deletion in addition to in-app deletion (policy checked 2026-09-26; an email request pathway is
  enough). Designed in [design 0015](docs/design/0015-account-deletion-web-page.md); the page
  (`backend/hosting/`) and the runbook (`docs/SETUP.md`) are written, deployed to
  `https://kalorie-bf11c.web.app/delete-account`, and checked by hand on an Android phone
  (readable, `mailto:` opens with the subject filled). Left, blocked on the same Play Console
  account as the SHA-1 item above: paste the URL into Play Console → Data safety once the app
  exists there, then remove this item.
- **Privacy policy** — Google Play (App content → Privacy policy) and App Store Connect both
  require a public privacy policy URL and an in-app link; Play's Data safety answers must match it.
  Designed in [design 0021](docs/design/0021-privacy-policy.md): the data inventory, the page
  `backend/hosting/privacy.html` (`https://kalorie-bf11c.web.app/privacy`), the account-screen
  link on both clients, and the Data safety mapping. Owner decisions are recorded,
  ready to implement; pasting the URL into Play Console waits on the Play Console account.
  Pre-release gate next to *Play Store account deletion*.
- **Check the recent Android fixes by hand on an emulator or device** — they were only built and unit
  tested; the camera and the touch handling cannot be covered that way. The barcode scanner must release
  the camera when its dialog is dismissed (the indicator goes off); and approving and rejecting a
  submission that was written from the iOS app must work (it failed on `submitted_at` precision).
- **Handle `NoCredentialException` in Android Google sign-in** — `GoogleSignInProvider.signIn`
  (`CredentialManager.getCredential`) throws it when the device has no Google account, and it now
  surfaces as a generic failure. Decide what the user should see (for example a hint to add a Google
  account), then catch it explicitly. Android lint reports it as `CredentialManagerMisuse`.
- **Update the Android dependencies Android lint reports as outdated** — `navigation3-runtime` and
  `navigation3-ui` 1.1.7 → 1.2.0, `kotlinx-serialization-json` 1.9.0 → 1.11.0,
  `kotlinx-coroutines-play-services` and `kotlinx-coroutines-test` 1.10.1 → 1.11.0
  (`Android/app/build.gradle.kts`), and the Gradle wrapper 9.7.1 → 9.8.0. Do it on its own branch with
  the full unit test suite, since navigation and serialization can change behaviour.

## Documentation baseline

[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) describes what exists; `docs/adr/` records the
decisions still in effect. Findings are grouped by area and numbered `A<area>-<n>` — only open
ones are listed below; closed findings live in git history, not here.
