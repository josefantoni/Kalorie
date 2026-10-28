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

- **Minimum supported version — release** — implemented on both clients per
  [design 0026](docs/design/0026-minimum-supported-version.md), not released yet. Before the release
  deploy `backend/firestore.rules` (`firebase deploy --only firestore:rules`; the `config` read is public)
  and create the `config/app` document in the console with no fields. Then release both clients together
  with the App Check install, whose enforcement it makes safe.
- **Firebase App Check — enforcement** — installed on both clients and registered in the Firebase Console
  (App Attest on iOS, Play Integrity on Android, Cloud project linked in Play Console, debug tokens for the
  maintainer's simulator and device), nothing enforced yet. Storage and Firestore rules only check who
  writes, not what writes, so until enforcement anyone with an account can upload files from a script and
  the Blaze bill is ours.

  **Blocked on [design 0026](docs/design/0026-minimum-supported-version.md) (minimum supported version).**
  Every build without the gate can never be told to update, so only the gate makes enforcement safe for
  gated users. Remaining, in this order:

  1. Release both clients with the gate (see above; the App Check install is already in the code).
  2. Once that release is live on both stores, raise `config/app.min_version_ios` / `min_version_android`
     to it (design 0026 runbook).
  3. Watch Console → App Check → Storage and Firestore until the unverified share is only pre-gate
     releases and noise; those can never be updated by force and are locked out by enforcement.
  4. Check the Mac availability in App Store Connect first: `DCAppAttestService.isSupported` is unreliable
     for an iPhone app on an Apple-silicon Mac (not verified), so those users may lose Storage and later
     Firestore. Turn Mac availability off or accept it.
  5. Enforce Storage; after a quiet week, enforce Firestore. Unenforcing in the console is the rollback and
     takes effect within minutes. Leave Authentication unenforced.
  6. Verify by hand: a TestFlight / Play Internal build shows up as verified in the metrics; a debug build
     with an unregistered token gets `permission-denied` on a photo upload once Storage is enforced.

  Every new simulator, emulator or debug device needs its debug token registered (the debug provider prints
  it on first launch: Xcode console / logcat) under Firebase Console → App Check → app menu → Manage debug
  tokens. A token is a credential, do not put it in the shared `Kalorie.xcscheme`.
- **Support development (tips)** — designed in [design 0023](docs/design/0023-support-development-tips.md) and
  implemented on both platforms and switched off by `Constants.Tips.isEnabled`, so the apps ship without
  tips. Blocked on the owner's trade licence being suspended: income from the stores
  likely counts as business income, so decide with an accountant before enabling. To enable: renew the
  licence, create the Play payments profile and the three consumable products (`tip_small`, `tip_medium`,
  `tip_large`), accept the App Store Connect *Paid Apps* agreement and create the same products (neutral
  names, Czech + English descriptions), switch the App Store Connect DSA status from *not a trader* to *trader* (an address, phone and email are then published on the product page) and make the Play listing consistent, then set `Constants.Tips.isEnabled` / `IS_ENABLED` to `true` on both platforms and restore the tips paragraph in
  `backend/hosting/privacy.html` (Apple / Google process the payment, the app receives no card or purchase
  data) and test a real purchase from Internal testing with a license tester.
- **First Play release needs a closed test (in progress)** — a personal developer account created after Nov 2023
  gets production access only after a closed test with at least 12 testers opted in for 14 days in a
  row. The closed test is running with more than 12 testers; the 14 days have to pass before production
  access can be requested. It is also the cheapest way to find out whether anyone uses the app.
- **Basal metabolic rate (opt-in)** — not designed yet; write a design doc first (Scope: Cross-platform).
  Decisions taken so far: the app offers to estimate the user's BMR (sex, age, height, weight) and the
  dashboard then shows the day's kcal total against it. The offer is never automatic: it reuses the
  [design 0017](docs/design/0017-sign-in-spotlight.md) spotlight and repeats the same way until the user
  enters the data. The inputs are stored in Firestore under `users/{userId}`, so they follow the account
  across devices. Open points for the design doc: `ARCHITECTURE.md` § 944 states there are no goals or
  targets in the app, so this introduces the first one (decide whether to compare against BMR alone or
  against total daily expenditure with an activity level); the formula (Mifflin-St Jeor) belongs in a
  shared KMP module next to `MacroKit`; `users/{userId}` is currently written only by the Apple sign-in
  use case and uses camelCase; health data is a new category, so `backend/hosting/privacy.html`, App
  Privacy (App Store Connect), Data safety (Play), the Firestore rules and account deletion all need
  updating; the spotlight needs a second, independent "last shown" state. Weight: do not add a weight
  field to `users/{userId}`. [Design 0028](docs/design/0028-body-weight-tracking.md) makes
  `users/{userId}/bodyWeights` the single source of the user's weight; BMR reads "the weight valid
  today" with the same query the Dashboard circle uses and stores none of its own.
- **Body weight tracking — Android port** — iOS is done on branch `feature/body-weight-tracking` per
  [design 0028](docs/design/0028-body-weight-tracking.md); Android is not started and waits until the
  maintainer is happy with iOS. Read design 0028 and `docs/ARCHITECTURE.md` §§ 1.2, 1.4, 1.5, 3.6, 3.9, 6.2, 6.3, 8.1
  first, then port 1:1 from the Swift originals (`Android/CLAUDE.md`: same names, same tests). Shared items
  (fixture `fixtures/body-weight-day-key-cases.json`, rules tests, `privacy.html`, `strings.json` and the generated
  Android `strings.xml`) are already done; the Android `L10n`-equivalent only needs to use the new string keys.
  Land it in slices, one commit per slice, `[FEAT]`/`[TEST]` Android-only, in this order:

  1. **Provider** — `core/networking/FirestoreDataProvider.kt` gets the three query shapes iOS added in
     `FirestoreDataProvider.swift`: `id <= key` ordered descending with a limit, `id > key` ordered ascending with
     a limit, `mergeAsync` (`SetOptions.merge()`) and `batchDeleteAsync(ids, collection)` (one `WriteBatch` per 500 ids). Unlike iOS there are no protocol-extension defaults, so
     `FirestoreDataProviderFake` in `app/src/test/.../networking/` and any other implementer must get the new
     methods (grep implementers first). `Constants.Firestore` gets `appSettingsDocumentId = "app"`,
     `bodyWeightRecentLimit = 3`, `bodyWeightLaterLimit = 2`, `Field.id`, `bodyWeights(userId)`, `settings(userId)`
     and `BodyWeight.defaultKilograms = 70.0`.
  2. **Models and DTOs** — port `BodyWeightModel.swift` (`BodyWeightUnit` kg/lb with `display`, `kilograms(from)`,
     `rangeInTenths` 300..3000 / 662..6613, `formatted`, `tenths`; `BodyWeightDomain`; `BodyWeightWindow` with
     `earlier` newest-first, `later` oldest-first, `chronological`; `AppSettingsDomain.defaults`;
     `BodyWeightDayKey.make(from, timeZone)` / `date(from)` at noon), `BodyWeightDTO.swift`, `AppSettingsDTO.swift`.
     Day key is Gregorian, device time zone, POSIX `yyyy-MM-dd` (use `java.time` with `Locale.ROOT` and
     `IsoChronology`, not the default locale). The test must read `fixtures/body-weight-day-key-cases.json`
     ([ADR 0039](docs/adr/0039-swift-only-rules-move-into-kmp-or-share-golden-vectors.md)); a failing case means the
     port is wrong. Weight stays unrounded kg in Firestore; settings fields are all optional on read and written
     with merge.
  3. **Use cases + tests** — `FetchBodyWeightsUseCase` (`upTo`, `after`, `from..to` with limit `days + 1`, all),
     `SaveBodyWeightUseCase` (range 30..300 kg, no future day, `BodyWeightError`), `DeleteBodyWeightUseCase`,
     `AppSettingsUseCases` (fetch; save via merge), each with a Fake in `app/src/debug/`. Port
     `BodyWeightUseCasesTests`.
  4. **Account paths** — `MigrateAnonymousDataUseCase.kt`: replay reads the target account first and the account wins
     per day; settings are written only if the account has none. `PendingMergeSnapshotStore.kt`: snapshot gains
     `bodyWeights` and `appSettings` and must still decode a legacy snapshot without them (`decodeIfPresent` →
     defaults in kotlinx.serialization). `DeleteAccountUseCase.kt`: wipe `bodyWeights` (one `batchDeleteAsync`) and `settings` (iOS extracted
     `wipeBodyWeightData(userId)`). Port the matching test changes, including the legacy-snapshot test. The
     migrate fake is hit concurrently, so make it thread-safe.
  5. **Export** — `GenerateFoodExportUseCase.kt` now requires `fetchBodyWeights` and `fetchAppSettings` (update its
     Configurator and every test construction); one weight query `id <= key(to)` with limit `days + 1`; the valid
     weight per day is appended to the day label as `· 78,4 kg` (use the unit from settings, fetched only when any
     weight exists). Port `FoodExportReportFactory` changes (`weights:`, `weightUnit:`) and its tests. This is one
     query where design 0028 says two; iOS deviated deliberately, keep Android identical.
  6. **Dashboard** — `DashboardViewModel.kt` gets `fetchBodyWeights`, `fetchAppSettings`, `saveBodyWeight` and the
     state/functions listed in `DashboardViewModel.swift` (`recentWeights`, `laterWeights`, `appSettings`,
     `hasLoadedSettings`, weight popover/picker/saving/checkmark state, `onWeightBadgeTapped`, `onWeightConfirmed`,
     `onWeightsChanged`, `onAppSettingsChanged`). The window is two concurrent queries (earlier ≤ 3 incl. a valid
     weight, later ≤ 2), at most 5 reads, the later query skipped when the selected day is today or later;
     `weightCache` is keyed by selected day and cleared entirely on any write. The tests that iOS keeps in
     `DashboardViewModelBodyWeightTests.swift` are the spec; assert query counts, not only results.
     UI: the circle (`WeightCircleView.swift`, 72 dp) sits as the trailing accessory of `MacroSummaryView.kt`,
     centred at 2/3 of the donut height (160 dp), at most 40 dp from the donut, 20 dp from the trailing edge;
     the "+" badge is bottom-trailing (offset 14, 44 dp hit area) and only when a weight can be logged; tapping
     the circle pushes the history screen; the badge opens a popover/bottom sheet with the picker
     (`WeightPickerView`) and a confirm button that shows a checkmark. The mini graph is `WeightSparkline`: the
     weight valid for the selected day is the centre, marked by a dot, older points to the left and later to the
     right, `step = width/2 / max(centreIndex, rightCount, 1)`, Y centred on the centre value with half-span
     `max(max deviation, minimumSpan / 2)` (`minimumSpan` = 2 kg in the display unit). Port `WeightSparklineTests`
     to pin it. With no entry on or before the selected day the circle shows the blurred placeholder even when
     later entries exist. The circle lives inside `MacroSummaryView`, so it is shown only on days with food
     (maintainer decided to keep that).
  7. **History screen** — `BodyWeightHistoryViewModel/View/ChartView/Configurator`: Week/Month/Year range, paging
     (back to `Constants.BodyWeight.firstYear` = 2026 and no further), swipe-to-delete rows, no editing
     (design 0028, *Decisions made during implementation*), `onChanged` fired when the screen disappears so the Dashboard reloads.
     On Android add the trailing chevron only where the exception in `Android/CLAUDE.md` says. Port
     `BodyWeightHistoryViewModelTests`.
  8. **Settings** — section "Body weight" with a switch (`show_body_weight`) and a kg/lb segmented control, hidden in
     the edit mode; on a failed save revert the value and show the alert. `SettingsViewModel` takes `appSettings`
     as a required parameter (no default, a debug-only fake default breaks release builds). Port
     `SettingsViewModelTests`.

  Gotchas: no code comments; Android strings are generated, never edited by hand (`node scripts/generate-strings.js`);
  run the Android unit tests and `assembleRelease` at the end (debug-only fakes must not leak into production code);
  a change that touches the Firestore contract means checking the iOS build too. Do not push or merge anything.
  Manual owner steps that stay open after the code lands: re-confirm Play Data safety and App Store Connect App
  Privacy (verify *Health* is ticked), and deploy hosting for the updated `privacy.html` (effective date
  28 Oct 2026). When Android ships, fill the checklist and Outcome in design 0028 and delete this item.
- **Dashboard read cost** — not urgent. Firestore bills per document read, and the dominant cost is the
  whole-month `foodConsumed` load on cold launch and pull to refresh
  ([ADR 0015](docs/adr/0015-dashboard-caches-a-month-and-derives-the-day.md)): ~300 documents per load at
  ~10 entries a day. A rough estimate for 300 active users (2 cold launches, 5 foregrounds, 10 day switches
  each per day) is ~225,000 reads a day, about 80 % of them month loads, i.e. a few USD a month above the
  free tier. Revisit if the user count grows by orders of magnitude.
- **Account deletion: batch the per-document deletes** — `DeleteAccountUseCase` deletes `mealTypes`,
  `foodConsumed`, `favouriteFoods`, `myCreatedMeals`, `foodItemPortions` and the user's submissions one
  document per round trip; only `bodyWeights` uses `batchDeleteAsync` (one `WriteBatch` per 500 ids).
  `foodConsumed` grows fastest (~10 entries a day), so a long-time user waits for thousands of sequential
  deletes and a timeout leaves the account half wiped. Move the loops to `batchDeleteAsync`, iOS and
  Android together (Scope: Cross-platform), keeping the order the tests pin (profile document last) and
  the existing delete tests as the spec. One `[REFACTOR]` commit per platform.

## Android readiness

The shared KMP modules build for Android ([ADR 0037](docs/adr/0037-shared-modules-target-ios-and-jvm-and-are-consumed-by-composite-build.md)).
The Android client in `Android/` ([ADR 0038](docs/adr/0038-android-client-mirrors-the-ios-architecture-natively.md))
has every screen of the iOS app ported; how each step deviated from iOS is recorded in the last version of
this file that still has the steps (`git show 60dcabb:TODO.md`). Nothing is open at the moment.

## Documentation baseline

[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) describes what exists; `docs/adr/` records the
decisions still in effect. Findings are grouped by area and numbered `A<area>-<n>` — only open
ones are listed below; closed findings live in git history, not here.
