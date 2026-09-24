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
A minimal scaffold exists in `Android/` ([ADR 0038](docs/adr/0038-android-client-mirrors-the-ios-architecture-natively.md)
Consequences) — no real screen yet. What is left is deferred until porting starts.

- [ ] **Port the first real screen, replacing the scaffold** — `Android/app/.../scaffold/` is a
  build-and-navigation proof, not a feature ([ADR 0038](docs/adr/0038-android-client-mirrors-the-ios-architecture-natively.md)
  Consequences); it has no iOS counterpart and should not stay once a real screen lands.
  **Decided: the first screen is the Dashboard alone, with the anonymous-data merge deferred.**
  Handoff for the implementing session — read `Android/CLAUDE.md` first (it is local-only, the
  global gitignore excludes `CLAUDE.md`), then ARCHITECTURE § 3 and § 6 *Read first* lists.
  - **Android Studio is not needed to create anything.** The Gradle project exists and builds from
    the command line; never run its New Project wizard over `Android/`. The user opens `Android/`
    in Studio only to run the result on an emulator.
  - **Prerequisite (user):** `google-services.json` copied into `Android/app/`. It is gitignored
    (`.gitignore:44`) and cannot be generated.
  - **Gradle:** add `com.google.gms.google-services` (root `apply false` + app), the Firebase BoM
    with `firebase-auth` and `firebase-firestore`, `kotlinx-coroutines-play-services`, and
    `implementation("kalorie:MealKit")` — `MealTypeModel.swift:9` imports MealKit, the app only
    depends on MacroKit today. Check current versions per `docs/SETUP.md:103`. Crashlytics is out
    of scope. CI does not build `:app` (`.github/workflows/ci.yml:41` builds ExportKit only), so no
    CI secret is needed yet; adding `:app` to CI later needs `google-services.json` as a secret.
  - **What to port** (same names, one Kotlin file per Swift file, packages per `Android/CLAUDE.md`):
    - `Core/Utils/Constants.swift` (only the Firestore paths and `Time` constants used below),
      `Core/Utils/LoadingState.swift`, `Core/Utils/AlertItem.swift`.
    - `Core/Auth/AuthProvider.swift` + `AuthProviderFake` (`:60`).
    - `Core/Auth/AuthStateObserver.swift` **without** `resumePendingMerge`, `isMerging`,
      `beginMerge`/`endMerge` and `MergeStatusReporting` — see the deferred-merge item below. It
      keeps the auth-state listener and `signInAnonymously`.
    - `FirestoreDataProvider.swift` — the interface gets **only** the five methods the Dashboard
      calls: `loadAsync(from:)`, `loadFromServerAsync(from:)`, the range `loadAsync(... isGreaterThanOrEqualTo:isLessThan:)`,
      `batchSetAsync` and `deleteAsync`. The rest arrive with the use cases that need them.
      `FirestoreDataProviderFake` lives at `iOS/KalorieTests/CreateFoodItemUseCaseTests.swift:164`;
      port it to `app/src/test/` trimmed to the same five methods.
    - DTOs and domain: `MealTypeDTO`, `FoodConsumedDTO`, `MealTypeModel`, `FoodConsumedModel`.
      **Copy `CodingKeys` verbatim** — `mealTypes` uses camelCase (`startMinutes`, `endMinutes`),
      so the `snake_case` example in `Android/CLAUDE.md` does not apply to every collection.
    - Use cases, each with its fake (`app/src/debug/`) and its ported `…Tests.swift`:
      `FetchMealTypes`, `FetchFoodsConsumedForMonth`, `SetupDefaultMeals`, `ConfirmMealTypesEmpty`,
      `DeleteFoodConsumed`.
    - `Features/Dashboard/`: `DashboardViewModel` (in full, including the `show…Sheet` flags, with
      `DashboardViewModelTests` ported in full), `DashboardView`, `DashboardConfigurator`,
      `DayPickerView`, `MacroSummaryView`, `MealSectionMacroView`, `MonthCalendarView`.
    - Only the ~13 `L10n.Dashboard.*` / `L10n.Common.*` keys these views use, into
      `res/values/strings.xml` (the xcstrings source language, `cs`) and `res/values-en/`. This does
      not decide the *Shared localisation source* item below.
  - **What not to port:** `DashboardRouter` and its four destinations (meal-type sheet, add-food
    sheet, food detail, account). Leave out their entry points in the View — the FAB, the
    meal-layout and account toolbar buttons, and the row tap. The calendar sheet, the delete
    confirmation and the alert stay, since they are Dashboard's own. The ViewModel keeps the flags
    so its tests stay a 1:1 port; the Router is ported with the first destination.
  - **Gotchas:**
    - The rebuild on auth change (`KalorieApp.swift`, `.id(authState.userId)`) needs the
      ViewModel itself keyed by `userId` (`viewModel(key = userId) { … }`); Compose's `key()` alone
      does not discard a `ViewModel`.
    - Month bounds in `FetchFoodsConsumedForMonthUseCase.swift:33-35` use the device calendar and
      time zone; use `java.time` with `ZoneId.systemDefault()` and pass epoch **seconds** as
      `Double`.
    - DTO decoding goes through kotlinx.serialization from `DocumentSnapshot.data`, never
      `toObject()`. Cover the bridge with a unit test feeding a `Long` into a `Double` field —
      ADR 0038 lists that as not verified.
  - **Then:** delete `Android/app/.../scaffold/` and its test, point `MainActivity` at the
    `AuthStateObserver` → `DashboardConfigurator` flow, and remove this item.
  - **Done when:** `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest` pass (report the
    test count), and the user sees on an emulator that a fresh install signs in anonymously,
    creates the default meal sections and shows the empty state, and that the day picker and
    calendar switch days. There is no way to add food on Android yet, so a non-empty day can only
    be checked by hand later.
- [ ] **Split CI by platform** — `.github/workflows/ci.yml` is one macOS job that runs rules tests, KMP
  builds, ExportKit Android and the iOS tests on every pull request, whatever changed. Use path filters
  (`iOS/`, `Android/`, `backend/` + `firestore-rules-tests/`, KMP modules) so an Android-only change
  does not wait for the iOS simulator, and add an Android job running `:app:assembleDebug` and
  `:app:testDebugUnitTest` (needs `google-services.json` as a secret). KMP module changes must still
  trigger both clients.
- [ ] **Anonymous-data merge on Android** — deferred from the Dashboard port above. On iOS
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
- [ ] **Golden vectors for the Swift-only rules** — scaling (`FoodItemDomain.scaled(toGrams:)`),
  meal resolution with a pin (`resolvedMealTypeId`), food validation, the OpenFoodFacts mapping, the
  search merge order and export day bucketing live in Swift only; ARCHITECTURE states them as a
  contract but nothing verifies a second client against it. The only shared test vector today is
  `TextKit/fixtures/text-kit-cases.json`. Decide how much to share, and whether some of it should
  move into KMP instead — [ADR 0038](docs/adr/0038-android-client-mirrors-the-ios-architecture-natively.md)
  item 2 sets the rule (pure, deterministic, no I/O), not which rules qualify.
- [ ] **Shared localisation source** — strings live in `Localizable.xcstrings` behind the `L10n`
  enum ([ADR 0019](docs/adr/0019-l10n-enum-over-the-string-catalogue.md)); Android needs its own
  `strings.xml` and nothing keeps the two in step. Open question, not decided.
- [ ] **Firebase setup for an Android app** — the Firebase console side is done (app
  `antoni.kalorie`, debug SHA-1, `google-services.json`). Still to add: the release and Play App
  Signing SHA-1s, and a real sign-in run to confirm the `docs/SETUP.md` Android section when the
  first sign-in screen is built.
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
