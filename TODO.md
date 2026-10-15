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
- **Support development (tips)** — designed in [design 0023](docs/design/0023-support-development-tips.md):
  three consumable products (`tip_small`, `tip_medium`, `tip_large`) through store billing, Android
  first. The Settings row exists on both platforms only as a placeholder that shows a "Coming soon" alert;
  replace the alert with the real flow. Before the code: set up the Play Console payments profile, decide
  on the EU trader status (the stores publish the developer's address), and ask an accountant about the
  trade licence and taxes.
- **First Play release needs a closed test** — a personal developer account created after Nov 2023
  gets production access only after a closed test with at least 12 testers opted in for 14 days in a
  row. Start it as early as possible; it is also the cheapest way to find out whether anyone uses the app.

- **iOS: tell the user when Apple sign-in hits an account that was created through Google**
  (analysed 2026-10-15). **Handoff** — no other context needed.

  *Symptom.* On the account screen a user whose e-mail already belongs to a Google-created account
  taps Sign in with Apple (sharing the same e-mail) and gets the generic *"Nepodařilo se přihlásit"*.
  Firebase answers `emailAlreadyInUse`; `LinkOrMergeCredentialUseCase.callAsFunction`
  (`iOS/Kalorie/Core/UseCases/LinkOrMergeCredentialUseCase.swift:46`) already turns that into
  `LinkOrMergeCredentialError.accountExistsWithAnotherProvider`, and `SignInWithAppleUseCase` passes it
  through (line 44). Only the Google handler catches it:
  `AccountViewModel.onSignInWithGoogleTapped` (`iOS/Kalorie/Features/Account/AccountViewModel.swift:99`).
  `onSignInWithAppleTapped` (same file, ~line 70–86) has no such `catch`, so the error lands in the
  generic one at line 85. [ADR 0006](docs/adr/0006-google-sign-in-identity-collisions.md) requires the
  warning in both directions and in every client; it was written when Apple was the only provider, so
  the one existing message (`account_error_accountExistsWithApple`) only fits Google → Apple. This is a
  gap in the code, not a decision — the ADR stays as it is.

  *Change.*
  1. Add to `onSignInWithAppleTapped`, before the cancellation catch, the same shape as the Google
     handler: `catch LinkOrMergeCredentialError.accountExistsWithAnotherProvider { alertItem = AlertItem(title: L10n.Account.errorAccountExistsWithGoogle) }`.
  2. Add the key `account_error_accountExistsWithGoogle` to **`localisation/strings.json`** (next to
     `account_error_accountExistsWithApple`, line 46) — cs: *"Tvé dřívější záznamy jsou u účtu
     propojeného s Google. Pro přístup k nim se prosím přihlas přes Google."*, en: *"Your earlier entries
     are on a Google-linked account. Please sign in with Google to reach them."* Then run
     `npm run generate-strings` in `scripts/`. **Never edit `Localizable.xcstrings` or the Android
     `strings.xml` by hand** — the generator rewrites them (`docs/ARCHITECTURE.md` § localisation).
  3. Add `errorAccountExistsWithGoogle` to `iOS/Kalorie/Resources/Localization/L10n.swift` next to line 49.
  4. Add `test_onSignInWithAppleTapped_whenAccountExistsWithAnotherProvider_showsSpecificAlert` to
     `iOS/KalorieTests/AccountViewModelTests.swift`, copied from the Google variant at line 115
     (`SignInWithAppleUseCaseFake(errorToThrow: LinkOrMergeCredentialError.accountExistsWithAnotherProvider)`).

  *Not needed.* Android has no Apple sign-in yet (`AccountViewModel.kt` handles only Google, line 67), so
  it has no Apple handler to fix; the generated Android strings pick the new key up automatically.

  *Verify by hand* (no automated test reaches Firebase): on a device sign in with Google using an
  e-mail, sign out, then Sign in with Apple choosing **Share My Email** with that same address — the
  specific alert must appear. With **Hide My Email** Apple sends a `privaterelay.appleid.com` address,
  Firebase sees no collision and silently creates a second empty account; that case is accepted and
  undetectable per ADR 0006, so do not try to "fix" it. Commit as `[FIX]` for iOS; the strings JSON and
  generated Android files belong to the same commit because the key is shared.
- **Store release checklist after the Apple/Google developer accounts exist** (state on 2026-10-15):
  - *Done:* iOS build 1 is in TestFlight; Sign in with Apple is enabled for the App ID, the Firebase
    Apple provider and `Kalorie.entitlements`; App Privacy answers (8 data types, no tracking) are saved in
    App Store Connect **but not published**; the privacy policy page in the repo says 15 October 2026 but is
    **not deployed**.
  - *Still to do, user's console work:* press **Publish** on App Store Connect → App Privacy; deploy the
    policy (`firebase deploy --only hosting` from `backend/`, never a bare `firebase deploy`); fill the Play
    **Data safety** form from the table in [design 0021](docs/design/0021-privacy-policy.md) plus *Photos —
    collected, not shared, App functionality, optional*; create the Play app `antoni.kalorie`, upload
    `Android/app/build/outputs/bundle/release/app-release.aab` (`./gradlew :app:bundleRelease`) to Internal
    testing, then add the *App signing* and *Upload* SHA-1/SHA-256 to Firebase, re-download
    `google-services.json` into `Android/app/` and update the `GOOGLE_SERVICES_JSON` CI secret.
  - *iOS beta:* a public TestFlight link needs an **External testing** group and Beta App Review (the
    build must carry a new `CURRENT_PROJECT_VERSION` after the fix above, so build 3).
  - *Open question for a lawyer:* whether a food diary is health data under GDPR Art. 9 (design 0021 chose
    no explicit-consent step); the privacy policy also still lists Sign in with Apple, which now exists.

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
