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

- **Prompt to sign in** — the account screen is only reachable from the toolbar icon; add an
  unobtrusive prompt after the first logged meal so users on a second device sign in early
- **Packaging photo on a submission** — the other half of the user-submitted-food flow shipped
  in [design 0009](docs/design/0009-catalogue-moderation.md), deferred by that design's Non-goals:
  Firebase Storage is not configured in this project, and whether the project's plan includes free
  Storage quota is unverified. Needs a `storage` block in `firebase.json`, `storage.rules`, and a
  Blaze-plan check before starting. Additive once it lands — `FoodItemSubmissionDTO` gains an
  optional `photo_path`, no other schema change.
- **Rank search results by frequency** — order manual search results by how often the user has
  logged each food, so the most used ones come first. Distinct from favourites above: this one is
  derived, not chosen, and the user cannot remove an entry from it.
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

- **Verify the camera features on an emulator** — there is no physical Android device, so the barcode
  scanner and the nutrition-label OCR have only been checked by unit tests.
  Fake the camera in the Android Studio emulator (Extended controls → Camera → Virtual scene with a photo
  of a barcode or a label, or the laptop webcam) and check: ML Kit reads cs/pl/de/en labels including
  diacritics, box coordinates hold under a rotated frame (rows and columns must not collapse), auto-capture
  and the shutter both work, and the permission prompt, denied state and revocation behave. Real-world
  glare and curved packaging still need a real phone before release.
- **Pin the two nutrition-label parsers with a shared fixture** — the Android parser
  (`core/nutritionlabelrecognition`) is a Kotlin copy of iOS's `NutritionLabelParser`, with iOS left
  unchanged by decision (a shared KMP parser was declined). Real labels will keep driving fixes, so the two
  can drift apart. Add a golden-vector fixture in `fixtures/` per ADR 0039 when an iOS reader test is
  acceptable. The Foundation Models path is iOS-only by design and not ported.
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
- **Play Store account deletion** — Google Play's policy has, to my knowledge, required a web
  link for requesting account deletion in addition to in-app deletion. The iOS
  `DeleteAccountUseCase` exists; no web page does. Check the current policy before the first release.
- **Check the recent Android fixes by hand on an emulator or device** — they were only built and unit
  tested; the camera and the touch handling cannot be covered that way. Auto-capture of a nutrition label
  must take a photo (it called `takePicture` off the main thread before); the barcode scanner must release
  the camera when its dialog is dismissed (the indicator goes off); and approving and rejecting a
  submission that was written from the iOS app must work (it failed on `submitted_at` precision).
- **Check that the loading overlays block touches** — `AddFoodSheetView.kt`, `FoodQuantityView.kt` and
  `ExportView.kt` cover the screen with a `Box` using `Modifier.clickable(enabled = false) {}`. It is not
  established whether a disabled `clickable` consumes pointer events or lets them through to the content
  below. If it lets them through, use `pointerInput(Unit) {}` instead. Moderation review has no overlay
  guard at all, only the view model returning while it is loading, so that one is covered either way.
- **Set up ktlint or detekt, or correct `Android/CLAUDE.md`** — it says the code style is enforced by
  ktlint through detekt, but neither is configured in `build.gradle.kts`, the version catalogue or CI, so
  nothing checks Kotlin formatting.
- **Compile the release variant in CI** — the Android job runs only `:app:assembleDebug` and
  `:app:testDebugUnitTest`, so a change that breaks only the release build (R8, signing, a debug-only
  source set leaking into `main`) is found at release time. Adding a step to `.github/workflows/ci.yml`
  is a CI change, so tell the user before doing it.
- **Test `FirestoreDataProvider.perform` on Android** — it decides what is logged as a warning and what
  is thrown as `Unreachable`, and has no test because it needs a Firebase instance. Extract the decision
  or wrap the Firebase calls behind something a test can replace.

## Documentation baseline

[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) describes what exists; `docs/adr/` records the
decisions still in effect. Findings are grouped by area and numbered `A<area>-<n>` — only open
ones are listed below; closed findings live in git history, not here.

## Audit findings — 2. Food search and catalogue

- **A2-12 — Ranking cannot be added on top of the current search.** Results are capped at
  `limit(10)` per field — six fields as of [ADR 0024](docs/adr/0024-token-array-field-for-whole-word-search.md)
  — and Firestore returns them in index order, i.e. alphabetically by the matched name. Anything
  cut by that limit is invisible to a re-sort, so *Rank search results by frequency* cannot be
  implemented as a client-side reordering of `SearchFoodItemsUseCase`'s output — it needs either a
  much larger limit (and the read cost that implies) or the frequency data denormalised into the
  query. Constraint, not a bug; recorded so the feature is not designed around a false assumption.
