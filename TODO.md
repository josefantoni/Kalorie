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

- [ ] **Prompt to sign in** — the account screen is only reachable from the toolbar icon; add an
  unobtrusive prompt after the first logged meal so users on a second device sign in early
- [ ] **Packaging photo on a submission** — the other half of the user-submitted-food flow shipped
  in [design 0009](docs/design/0009-catalogue-moderation.md), deferred by that design's Non-goals:
  Firebase Storage is not configured in this project, and whether the project's plan includes free
  Storage quota is unverified. Needs a `storage` block in `firebase.json`, `storage.rules`, and a
  Blaze-plan check before starting. Additive once it lands — `FoodItemSubmissionDTO` gains an
  optional `photo_path`, no other schema change.
- [ ] **Rank search results by frequency** — order manual search results by how often the user has
  logged each food, so the most used ones come first. Distinct from favourites above: this one is
  derived, not chosen, and the user cannot remove an entry from it.
- [ ] **Frequency-derived quick-add gram amounts** — per-user "frequently added weights", gram
  amounts the user logs often for a given food (e.g. 50g oats almost daily, one slice of bread),
  surfaced as quick-add options, likely derived from `foodConsumed` history rather than manually
  maintained. This is the other half of what used to be one combined line here; the first half —
  a food carrying a named, user-chosen package/portion weight selectable as a unit — shipped as
  [design 0008](docs/design/0008-food-portions.md).

## Android readiness

The shared KMP modules build for Android ([ADR 0037](docs/adr/0037-shared-modules-target-ios-and-jvm-and-are-consumed-by-composite-build.md)).
The Android client in `Android/` ([ADR 0038](docs/adr/0038-android-client-mirrors-the-ios-architecture-natively.md))
has the Dashboard screen ported. What is left is deferred until the next screen is ported.

- [ ] **Anonymous-data merge on Android** — deferred from the Dashboard port. On iOS
  `AuthStateObserver` resumes a pending merge (`MigrateAnonymousDataUseCase.resumeIfNeeded`) before
  publishing the user; Android leaves it out because it has no sign-in, so no merge can ever be
  pending. It has to land together with the first sign-in screen (Account), with
  `MergeStatusReporting` and the merging overlay from `KalorieApp.swift`. Read ARCHITECTURE § 6,
  [ADR 0002](docs/adr/0002-merge-anonymous-data-before-switching-accounts.md) and
  [ADR 0004](docs/adr/0004-migrate-usecase-exposes-two-methods.md) first — the merge algorithm is
  `Cross-platform`.
- [ ] **Apple sign-in on Android** — Firebase offers it only through a web OAuth flow that needs an
  Apple Services ID this project does not have. The iOS app currently signs in with Google only,
  since there is no paid Apple Developer account, so this waits for both.
- [ ] **Second-client readers for the golden vectors** — what ADR 0039 defers until the screen
  that needs it is ported:
  - **Android readers** — `fixtures/food-item-validation-cases.json` when `FoodItemValidation` is
    ported and `fixtures/open-food-facts-mapping-cases.json` when
    `FetchFoodByBarcodeExternallyUseCase` is. Add `rootDir.resolve("../fixtures")` as a unit-test
    resources directory in `Android/app/build.gradle.kts` and parse with
    `Json.parseToJsonElement`.
  - **Scaling fixture**, with the FoodQuantity port: a `FoodItemDomain` and grams in,
    `ScaledMacros` out (iOS `ScaledMacros.init(item:ratio:)` and `FoodItemDomain.scaled(toGrams:)` in
    `FoodConsumedModel.swift`).
  - **Export day-bucketing fixture**, with the Export port: `Europe/Prague`, including 2026-03-29
    and 2026-10-25 (iOS `FoodExportReportFactory.makeReport`, the `dayStarts` / `dayIndexes` bucketing).
- [ ] **Firebase setup for an Android app** — the Firebase console side is done (app
  `antoni.kalorie`, debug SHA-1, `google-services.json`). Still to add: the release and Play App
  Signing SHA-1s, and a real sign-in run to confirm the `docs/SETUP.md` Android section when the
  first sign-in screen is built.

  **Handoff (analysed 2026-09-24).** Two parts left, both blocked.

  1. **Real Google sign-in run — not a standalone task.** Blocked on the Account screen, which the
     *Anonymous-data merge on Android* item above already ties to the same screen. Treat this run as
     that port's acceptance check rather than doing it separately. What that port needs from here:
     `app/build.gradle.kts` has no Credential Manager dependency yet — add `androidx.credentials`,
     `androidx.credentials:credentials-play-services-auth` and `com.google.android.libraries.identity.googleid`
     (check current versions). `setServerClientId()` takes the `client_type` 3 (Web) entry's
     `client_id` from `google-services.json`; the file today holds one `client_type` 1 entry (the
     debug keystore) and one `client_type` 3 entry. Read it at build time from the `default_web_client_id`
     string resource the google-services plugin generates — never hard-code it. Verify by hand on a
     debug build installed from the machine whose `~/.android/debug.keystore` is registered; CI's
     runner generates its own throwaway debug keystore, so a CI-built APK fails Google sign-in with
     `DEVELOPER_ERROR` / code 10 by design, not by bug. Once it works, delete the *"no app has signed
     in with it yet"* sentence from `docs/SETUP.md`.
  2. **Release and Play App Signing SHA-1s — blocked on the user, before the first Play release.**
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
- [ ] **Play Store account deletion** — Google Play's policy has, to my knowledge, required a web
  link for requesting account deletion in addition to in-app deletion. The iOS
  `DeleteAccountUseCase` exists; no web page does. Check the current policy before the first release.

## Documentation baseline

[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) describes what exists; `docs/adr/` records the
decisions still in effect. Findings are grouped by area and numbered `A<area>-<n>` — only open
ones are listed below; closed findings live in git history, not here.

## Audit findings — 2. Food search and catalogue

- [ ] **A2-12 — Ranking cannot be added on top of the current search.** Results are capped at
  `limit(10)` per field — six fields as of [ADR 0024](docs/adr/0024-token-array-field-for-whole-word-search.md)
  — and Firestore returns them in index order, i.e. alphabetically by the matched name. Anything
  cut by that limit is invisible to a re-sort, so *Rank search results by frequency* cannot be
  implemented as a client-side reordering of `SearchFoodItemsUseCase`'s output — it needs either a
  much larger limit (and the read cost that implies) or the frequency data denormalised into the
  query. Constraint, not a bug; recorded so the feature is not designed around a false assumption.
