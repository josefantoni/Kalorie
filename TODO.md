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

### Port order (analysed 2026-09-24)

State today: the Android Dashboard is read-only. It has the day picker, calendar sheet, sections,
macro summary and swipe-to-delete, but no toolbar (account `person.circle` top-leading, meal types
`list.bullet.circle` top-trailing), no add-food FAB, no row tap, no `DashboardRouter` and no
Navigation 3 back stack, although the Nav3 dependencies are in `Android/app/build.gradle.kts`.
`DashboardViewModel` already has `showMealTypeSheet`, `showAddFoodSheet`, `showAccountSheet`,
`onMealTypesChanged` and `onFoodConsumedUpdated`. `FirestoreDataProvider.kt` implements only
`loadAsync(from)`, `loadFromServerAsync(from)`, the numeric range, `batchSetAsync` and `deleteAsync`.
Each step below adds the provider methods it needs, ported from
`iOS/Kalorie/Core/Networking/FireStone/FirestoreDataProvider.swift`, and a matching
`FirestoreDataProviderFake` entry in `app/src/test`.

Rules for every step. Each one is one PR, `Android/` only unless the step says otherwise:

- Read `Android/CLAUDE.md` first. The port copies iOS names, dependencies, `State` cases and test
  names. Read the ARCHITECTURE section named in the step, and the `Cross-platform`/`Backend`
  records on its *Read first* line.
- **Slicing a big iOS view model.** `AddFoodSheetViewModel` (22 use cases),
  `FoodQuantityViewModel` (9) and `FoodConsumedDetailViewModel` (10) are ported across several
  steps. An intermediate step ports a *verbatim subset* of the iOS members: the properties,
  `init` parameters, functions and tests that belong to that step's feature. It never adds a
  member iOS does not have, and it never stubs a missing feature with a no-op use case in release
  code. If a later feature's UI is missing, it stays missing and is not greyed out. When a later
  step adds a dependency, it goes at the same `init` position as on iOS.
- A step that adds a string adds it to `localisation/strings.json` with the iOS key and runs
  `npm run generate-strings` in `scripts/`.
- Done means that `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` pass. The
  user checks the screen by hand on a device.
- **Stop and ask the user** at any point marked *Decision*. Do not pick a library yourself.

1. [ ] **Meal type sheet.** This screen has no catalogue dependencies, so it comes first and
   exercises the write path. Port `Features/MealTypeSheet/*` (`MealTypeSheetViewModel`,
   `MealTypeSheetView`, `MealTypeItemView`, `CreateMealTypeResult`, `MealTypeSheetConfigurator`,
   `MealTypeSheetRouter`), and `CreateMealTypeUseCase`, `DeleteMealTypeUseCase` and
   `UpdateMealTypeTimesUseCase` with their tests. Add `setAsync` to the provider. Introduce
   `DashboardRouter.kt` holding only `mealTypeSheetConfigurator` for now. The other iOS
   configurators come in with their steps. Add the top-trailing toolbar button and render the
   sheet as a `ModalBottomSheet`. Contract: ADR 0035 (trimmed, case-insensitive unique name;
   window ≥ MealKit `MIN_MEAL_WINDOW_MINUTES`; no overlap), validated against the passed-in list,
   never the server. Reorder moves the *meals* between fixed time slots (ARCHITECTURE § 3.4),
   and the slots do not move with them. Ids are uppercase UUIDs. Read ARCHITECTURE § 3.4.
   *Ported with deviations:* the screen is a full-screen dialog (close X top-leading, no content
   description, as on iOS) instead of a `ModalBottomSheet`, since that is the native Android
   pattern for a full-height editing form. Reorder uses up/down buttons instead of drag handles.
   `MealTypeSheetRouter` and the export toolbar button with `isExportPushed` are left out and come
   with step 14 (`makeExportView`). The view model is created with a per-opening key in the
   Activity's `ViewModelStore`, so old instances live until the Activity is destroyed. Moving it
   into a Nav3 entry would fix that.
2. [ ] **Catalogue core types, no UI.** Port `FoodItemModel.swift` (`FoodItemDomain`),
   `FoodPortionModel.swift`, `FoodNutritionValues.swift`, `FoodItemValidation.swift`,
   `FoodItemDTO.swift` and `FoodPortionDTO.swift` (`asDomain()` on the DTO, domain → DTO in a
   DTO constructor, per § 1.1). Also port `ScaledMacros` and `FoodItemDomain.scaled(toGrams:)`
   from `FoodConsumedModel.swift`. Add `implementation("kalorie:TextKit")` (the `includeBuild` is
   already in `settings.gradle.kts`). Add `rootDir.resolve("../fixtures")` as unit-test resources
   and read `food-item-validation-cases.json` in `FoodItemValidationTest`. Optionality of every
   field comes from ARCHITECTURE § 1.7, not from guessing. The scaling fixture also lands
   here: add it to `fixtures/` with an iOS reader test in a separate
   `iOS/` commit first, then the Kotlin reader. Read ARCHITECTURE § 1.3, § 1.4, § 1.7, § 2.6 and
   ADR 0039.
   *Ported with deviations:* `FoodItemValidationError.mapped` is left out and comes with step 12,
   the first caller. `ScaledMacros` got only the `FoodItemDomain` init here; the `FoodConsumedDomain`
   init came with step 3. `isValidSubmissionUUID` is a regex on uppercase hex, because
   `UUID.fromString` accepts input such as `1-1-1-1-1` that the fixture marks invalid.
3. [ ] **Food consumed detail, weight and meal type only.** This step brings in Navigation 3.
   Hold a typed back stack in `KalorieApp`/Dashboard, with a `FoodConsumedDetail(food)` key
   provided by `DashboardRouter.makeFoodConsumedDetailView`, and make the row tappable (iOS:
   `navigationDestination(for: FoodConsumedDomain.self)` in `DashboardView.swift`). Port the subset
   of `FoodConsumedDetailViewModel`: `weight`, `state`, `showCheckmark`, `alertItem`,
   `mealTypeId`, `mealTypes`, `onAppear` (meal types only), `onMealTypeSelected`, `onSave`,
   `withMealTypeId` and `withScaledWeight`. Also port `UpdateFoodConsumedUseCase`,
   `AssignFoodMealTypeUseCase` and their tests, plus the matching `FoodConsumedDetailViewModelTests`
   cases. The single Save writes whichever of weight and meal type changed (ADR 0022, § 3.2).
   There is no "by time" option. A copy of `FoodConsumedDomain` must carry `measure` through
   (§ 1.4, `foodConsumed`). Favourite and report come in steps 7 and 11. Read ARCHITECTURE § 4.4 and § 4.5.
   *Ported with deviations:* `onAppear` is left out. On iOS it loads only the favourite state, the
   catalogue item and the report state, nothing about meal types, so steps 7 and 11 add it. The
   back stack lives in `DashboardViewModel.backStack`, a member iOS does not have, so it survives
   rotation. "By time" is only the label of an unresolved meal type, not a selectable option.
4. [ ] **Add-food sheet, local search only.** Add the bottom FAB and a full-height
   `ModalBottomSheet` with its own Nav3 back stack, because iOS pushes the quantity screen inside
   the sheet's `NavigationStack`. Port `SearchFoodItemsUseCase` (six concurrent queries,
   first-occurrence dedup in the fixed § 2.2 order, no re-sort, query derivation via
   `TextKit.searchQuery`) with its tests. Add `loadAsync(hasPrefix:limit:)` and
   `loadAsync(arrayContains:limit:)` to the provider. Port the `AddFoodSheetViewModel` subset
   `searchText`, `localFoodItems`, `onSearchTextChanged` (300 ms debounce by cancelling the
   collector, as `.task(id:)` does), `displayedResults` (local list only for now), `onSelectFoodItem`
   and `isPushedToQuantityView`, and the `FoodItemRow` component. Read ARCHITECTURE § 2.1–2.3 and
   ADR 0013, 0024.
   *Ported with deviations:* the sheet is a full-screen dialog (as in step 1) with its own Nav3 back
   stack, which holds only the search destination until step 5 adds the quantity screen. Until
   then `isPushedToQuantityView` is set but nothing consumes it. The two provider methods are named
   `loadHasPrefixAsync` and `loadArrayContainsAsync`, since Kotlin cannot overload on argument labels.
   The view model exposes `searchExampleRes` instead of `searchPlaceholder` because it has no
   `Context`; the view formats the placeholder. The Dashboard empty state got the *Add food* button
   iOS has (the FAB is hidden while a day is empty), without the pulse animation. `FoodItemRow`
   takes only `item`; `isFavourite` and `submissionStatus` come with steps 7 and 12. The results
   section header is `addFood_section_externalResults` ("Online results"), copied from iOS even
   though the section lists local results too; `addFood_section_searchResults` is unused on iOS.
   That looks like an iOS finding to settle before step 6.
5. [ ] **Food quantity and saving.** This step completes the first "log a food" loop. Port
   `FoodQuantityViewModel`/`FoodQuantityView` with `quantity`, `unit`, `FoodQuantityUnit`,
   `unitOptions` (canonical portions, then `.grams`, then `.hundredGrams`; personal portions come
   in step 8), `defaultUnit(for:)`, `mealTypes`, `selectedMealTypeId` and `onConfirm`. Also port
   `SaveFoodConsumedUseCase` and its tests. The sheet passes the Dashboard's `selectedDay`
   (its time of day is what the entry is stamped with, § 3.5). The caller resolves the meal-type
   pin (ADR 0031; untouched default `mealTypes.mealType(at:)`). The first picker option is the
   preselected unit (ADR 0030), and portion-less items open at `100 × 1 g`. Amounts follow the
   item's `measure` label, and nutrients stay grams (design 0011). Read ARCHITECTURE § 4.1–4.4.
   *Ported with deviations:* `FoodQuantityView` is a destination of the sheet's own Nav3 back stack,
   which is derived from `isPushedToQuantityView` and `selectedFoodItem` as `navigationDestination
   (isPresented:)` does on iOS, so `AddFoodSheetView` now takes a `makeFoodQuantityView` composable
   and `AddFoodSheetConfigurator` builds the view model. The view model is a verbatim subset: no
   favourites, personal portions, reporting, created meals or `onAppear` yet, and no
   `hasUserSelectedUnit` (it is only read by `onAppear`, step 8). The unit picker is a dropdown
   menu without the *My portions* entry (step 8). Numeric input sanitises to ASCII digits and one
   separator like iOS, but the field is a Material `TextField` with the decimal keyboard, and the
   Dashboard's add-food FAB is centred as on iOS (`FabPosition.Center`) instead of the Material
   default bottom end. `SaveFoodConsumedUseCaseTest` adds two tests iOS lacks (uppercase document
   id, epoch-seconds date stamp), per the wire-contract gotchas in `Android/CLAUDE.md`.
6. [ ] **OpenFoodFacts fallback.** *Decision:* the HTTP client. iOS uses `URLSession` directly, so
   the candidates are `HttpURLConnection` with no new dependency, or OkHttp/Ktor. Port
   `OpenFoodFactsProductDTO`, `SearchFoodExternallyUseCase` and
   `FetchFoodByBarcodeExternallyUseCase` with their tests. Read `open-food-facts-mapping-cases.json`
   (the first *Android readers* bullet below). Match § 2.4 exactly: host, both paths, the
   `fields=` list, the 10 s timeout, retrying only 429/5xx and timeout/connection-lost up to 3
   attempts with linear 500 ms backoff, and `.serverError` distinct from not-found. *Decision:*
   the `User-Agent` value. iOS sends `Kalorie-iOS/<version>`. Wire the external list into the
   sheet behind the § 2.3 gate (`displayedResults` empty and ≥ 3 characters; a failure is
   logged, not alerted) and add `FetchFoodItemByBarcodeUseCase` plus `loadAsync(id:)`.
   *Ported with deviations:* the decisions were `HttpURLConnection` with no new dependency, behind
   `HttpSessionProtocol` (the counterpart of the injected `URLSession`; tests use `HttpSessionFake`),
   and the `User-Agent` `Kalorie-Android/<versionName>`, which needed `buildConfig` enabled. The
   `URLError` codes map to JVM exceptions: `SocketTimeoutException` for `.timedOut`, `EOFException`
   or a `SocketException` other than `ConnectException`/`NoRouteToHostException` for
   `.networkConnectionLost` (both retried), and `UnknownHostException` for
   `.notConnectedToInternet` (not retried). The provider method is `loadAsync(id, from, serializer)`.
   `FetchFoodItemByBarcodeUseCase` has only the single-barcode call; the batched `barcodes:` variant
   and `whereDocumentIdIn` come with steps 7 and 9. Nothing calls it yet. The sheet shows external
   rows as plain text like iOS. Tests beyond iOS: the exact request URL and timeout of both calls,
   the percent-encoded barcode, a 404 not being retried, and the § 2.3 gate (three characters, a
   local match suppresses the search).
7. [ ] **Favourites.** Port `FetchFavouriteFoods`, `AddFavouriteFood`, `RemoveFavouriteFood`,
   `IsFavouriteFood` and `RefreshFavouriteFoodUseCase`, `FavouriteFoodDTO` and the
   `FavouriteButton` component. Add the remaining members to all three view models: the sheet's
   favourites section and `displayedResults` ranking step 1, the quantity screen's toggle, and the
   detail screen's toggle and `loadCatalogueItem`. Note that the local match is
   `searchText.lowercased()` with no fold, which differs from the server search on purpose
   (§ 2.2). Read design 0003 and ADR 0023.
   *Ported with deviations:* the sheet's `displayedResults` ranks only favourites above the
   catalogue matches for now; created meals and own submissions join it in steps 10 and 12. The
   favourites section is a heading plus rows in the one list, with no `Section` grouping, and
   `makeFoodQuantityView` gained `isFavourite` and `onFavouriteChanged` as on iOS. `FavouriteToggling`
   is an interface with a default `toggleFavourite`, over `MutableStateFlow` properties instead of
   `@Published` ones. The detail view model's `onAppear` covers only the favourite state and the
   catalogue lookup (the report state joins in step 11), and the button shows when the entry is a
   favourite or its item resolves, as on iOS. The provider method is `loadAsync(from, orderBy,
   descending, limit)`. Tests beyond iOS: a favourite that is also a catalogue match is listed once,
   the plain lowercased prefix match without diacritics folding, `onFavouriteChanged`, and the
   three toggle tests on the quantity view model.
8. [ ] **Personal portions.** Port `FetchFoodItemPersonalPortionsUseCase`,
   `SaveFoodItemPersonalPortionsUseCase`, `FoodItemPersonalPortionsDTO`, `FoodPortionsManagerView`
   (pushed, sharing the same `FoodQuantityViewModel`), `PortionInputRow` and the portion-draft
   members of `FoodQuantityViewModel`. Only `.catalogue` items qualify. After they load, the
   selection moves to `personalPortions.first` unless the user has already picked a unit
   (ADR 0030 step 2). Read § 4.2 and design 0008.
   *Ported with deviations:* `FoodPortionsManagerView` is a full-screen dialog over the quantity
   screen (as in step 1), not a Nav3 destination, and it shares the same `FoodQuantityViewModel`.
   Its errors show in the quantity screen's alert dialog rather than in a second one, so two
   dialogs never stack. The unit picker is a dropdown menu with a divider and the *My portions*
   item, and the quick-add chips are Material `AssistChip`s spread across the row. Drafts are
   `FoodPortionDraft` values in a `StateFlow` list, and swipe-to-delete reuses `SwipeToDeleteRow`.
   The meal branch of `isPersonalPortionsAvailable`, `persistPersonalPortions` and the
   `updateMyCreatedMeal`/`onMealUpdated` wiring wait for step 10. Tests beyond iOS: a fetch use
   case test, a failing fetch keeping the synchronous default, and the comma decimal separator.
9. [ ] **Barcode scanner.** *Decision:* the scanner stack. The likely pick is CameraX + ML Kit
   barcode scanning, which is VisionKit's counterpart; it adds the CAMERA permission. Port the
   inline scanner with the close button (`BarcodeScannerOverlay`), the duplicate-delivery
   suppression, and the permission-denied path, including revocation while visible (§ 2.5).
   *Ported with deviations:* the decision was CameraX 1.6.2 + ML Kit `barcode-scanning` 17.3.0
   (bundled model). `DataScannerRepresentable` became `DataScannerView` (a `PreviewView` with an
   `ImageAnalysis` analyzer) and `BarcodeScannerOverlay` shows it in a full-screen dialog (as in
   step 1) instead of a `fullScreenCover`. There is no `CameraAuthorizationProvider` yet: on iOS
   the view model uses it only for the nutrition-label camera (step 12), so the view checks
   `checkSelfPermission` itself as the counterpart of `DataScannerViewController.isAvailable`. VisionKit
   asks for access on its own, so the scan button here launches the `CAMERA` permission request
   when it is not granted and opens the scanner on approval, or shows the alert on refusal. The view
   model gets `fetchFoodItemByBarcode`, `fetchFoodByBarcodeExternally` and `isScannerVisible`, but no
   `isBarcodeRescanVisible`/`rescannedBarcode` (they belong to the submission form, step 12). The
   barcode icon is a small hand-drawn `ImageVector`, since `material-icons-extended` is not a
   dependency. Both `onScenePhaseActive` and the six `onBarcodeScanned` tests are ported verbatim,
   plus a check that the scanned code is cleared afterwards so it can be delivered again.
10. [ ] **Created meals.** Port `FetchMyCreatedMeals`, `CreateMyCreatedMeal`, `UpdateMyCreatedMeal`
    and `DeleteMyCreatedMealUseCase`, and `FetchFoodItemsByIdsUseCase` (provider
    `whereDocumentIdIn`). Also port `MyCreatedMealDTO`, `asFoodItem()` (MacroKit weighted mean),
    `MyCreatedMealEditor*`, the created-meal members of the sheet and quantity view models, and
    the quantity screen's pencil and *Delete meal*. Read § 1.4 `myCreatedMeals`, § 4.2, design 0006
    and ADR 0033.
    *Ported with deviations:* the provider method is `loadAsync(from, whereDocumentIdIn, serializer)`,
    chunked by `Constants.Firestore.IN_QUERY_LIMIT` (30) like iOS, but as a required member of the
    interface, not a default one; `FirestoreDataProviderFake` gained it. The sheet gets `AddFoodSheetMode`
    with only `SEARCH` and `CREATE_MEAL` (`NEW_ITEM` comes with step 12), rendered as a segmented button
    row that is the first list item of each mode, since the editor owns its own top bar with *Save*. In
    create-meal mode the editor is embedded (`dismissesOnSave = false`) and its top bar has the sheet's
    close button. The editor pushed from a created meal's quantity screen is a full-screen dialog over
    that screen (as for `FoodPortionsManagerView`), not a Nav3 destination, and reaches the quantity screen
    through `MealActions`, the counterpart of `.mealActions(makeEditorView:onDelete:)`. The editor view
    model exposes `titleRes`, `confirmationTitleRes` and `searchExampleRes` instead of strings, and
    `onDeleteIngredient` takes a `Set<Int>` for `IndexSet`. `FoodPortionsSection` is ported here for the
    editor, since step 12 needs it too. `ScannerAccess` (shared by the sheet and the editor) wraps the
    camera permission request that VisionKit does on its own, and `BarcodeIcon` is one shared vector.
    The sheet's `onAppear` loads favourites and meals concurrently, as `async let` does. Tests beyond
    iOS: the created-meal id is uppercase, and `FetchFoodItemsByIds` reads the deduplicated ids in
    order.
11. [ ] **Catalogue reports.** Port `FetchMyFoodItemReportUseCase`, `SubmitFoodItemReportUseCase` and
    `FoodItemReportDTO` into the quantity and detail screens. The id is `{barcode}_{userId}`, there
    is no update path, and the client reads its own report first (§ 1.6, design 0012).
    *Ported with deviations:* the toolbar menu is a `MoreVert` icon button with a one-item
    `DropdownMenu` (the counterpart of the ellipsis `Menu`), and the reason prompt is an `AlertDialog`
    with a text field, both shared by the quantity and detail screens as `ReportIncorrectDataMenu` and
    `ReportReasonDialog`. `FoodItemReporting` is an interface with default methods over
    `MutableStateFlow` properties, like `FavouriteToggling`. The detail view model loads the report state
    after the favourite and the catalogue item, as on iOS. The 500-character limit is `String.length`,
    which counts UTF-16 units as the rules do. Tests beyond iOS: none.
12. [ ] **Catalogue submissions.** Port `SubmitFoodItem`, `FetchMySubmissions`, `UpdateMySubmission`
    and `DeleteMySubmissionUseCase`, `FoodItemSubmissionFetcher`/`Writer` and
    `FoodItemSubmissionDTO`. Also port the new-food form components (`FoodItemFormFields`,
    `FoodItemFormSections`, `FoodPortionsSection`) and the sheet's submission members. A food
    without a barcode gets an uppercase UUID id (design 0013). Nutrition-label OCR
    (`RecognizeNutritionLabelUseCase`, design 0010) is a follow-up step, with its own *Decision*
    on ML Kit text recognition. Read § 7 and design 0009.
    *OCR follow-up, in progress:* decided to use ML Kit Text Recognition (bundled Latin model), which
    covers the same cs/pl/de/en labels as iOS's Vision setup. The parser is ported as a separate Kotlin
    copy (`core/nutritionlabelrecognition`), with iOS left unchanged by decision: a shared KMP parser was
    considered and declined. The Foundation Models path (`merging`, `NutritionLabelModelExtractor`) is not
    ported, since it is iOS-only and does not support Czech. Risk: the two parsers can drift apart as real
    labels drive fixes. Pin them with a shared golden-vector fixture (ADR 0039) when an iOS reader test
    is acceptable. ML Kit boxes are pixels with y down, so its wrapper converts them to Vision's
    normalized, y-up boxes before the parser sees them.
    *Ported with deviations (without OCR):* `AddFoodSheetMode` gets `NEW_ITEM`, and the review screen is
    a Nav3 destination (`isReviewPushed` → `AddFoodSheetDestination.Review`). The new-item prompt shows
    only *Add food manually*, since the camera prompt, `cameraAccess`, `recognizedFields`,
    `NutritionLabelPrefilling`, `FoodItemFormInput.applying`, the highlighted form fields,
    `onFieldEdited` and the nutrition-label scan button are all OCR and wait for that follow-up. The
    barcode rescan (`isBarcodeRescanVisible`, `rescannedBarcode`) is here, since it belongs to the form.
    `FoodItemFormInput` is an immutable data class edited through `copy`, and numeric fields are
    `DecimalTextField`s that keep their own text and write the parsed `Double` back. The sheet's alert
    dialog and loader moved from the search screen to the sheet root, because the review screen raises
    alerts too. `FoodItemRow` got `submissionStatus`, and `FoodMeasure` got `thousandUnitSymbolRes`.
    The provider gained `loadAsync(from, field, isEqualTo, orderBy, descending)` and
    `loadFromServerAsync(id, from)`. Tests beyond iOS: the submission id is an uppercase UUID, and the
    barcode rescan copies the code into the form.
13. [ ] **Account.** This step absorbs the anonymous-data merge and part 1 of *Firebase
    setup* below; read it. Port `AccountView`/`AccountViewModel`, and `SignInWithGoogle`,
    `LinkOrMergeCredential`, `MigrateAnonymousData` (plus the `resumeIfNeeded` call in
    `AuthStateObserver.kt`), `SignOut`, `Reauthenticate`, `DeleteAccount` and
    `FetchMaintainerClaimUseCase`. Add the top-leading toolbar button. Apple sign-in stays out
    (*Apple sign-in on Android*). Read § 6, ADR 0002 and ADR 0004.
    *Ported with deviations:* the code is done and a real sign-in and sign-out run passed by hand.
    Credential Manager 1.6.0 with `googleid` 1.2.1, using
    `GetSignInWithGoogleOption` (the button flow, which always shows the account chooser) and the
    `default_web_client_id` resource. `GoogleSignInProvider` finds the presenting activity through
    `CurrentActivityProvider`, held by the new `KalorieApplication`, the counterpart of iOS's
    `UIApplication.shared`. `AuthProviderKind` has only `GOOGLE`, `AuthProvider.linkedProviderKind`
    skips the synthetic `firebase` provider entry, and `SignOutUseCase` and
    `AccountViewModel.onSignOutTapped` are `suspend` since clearing the Google session is. User
    cancellation is `GetCredentialCancellationException`. The snapshot is stored in `filesDir` through
    kotlinx.serialization. `AccountView` is a full-screen dialog with only the Google button, and its
    maintainer *Moderation* section came with step 15. The merge
    overlay lives in `KalorieApp`. Tests beyond iOS: the snapshot store tolerates missing optional
    collections, and `DeleteAccountUseCase` deletes the `users` document.
14. [ ] **Export.** Port `FetchFoodsConsumedInRangeUseCase`, `GenerateFoodExportUseCase`,
    `FoodExportReportFactory` and `Export*` through ExportKit, and share the file with an
    `ACTION_SEND` intent (the counterpart of `ActivityView`). It carries the day-bucketing fixture.
    Read § 8 and design 0014.
    *Ported with deviations:* the fixture `fixtures/export-day-bucketing-cases.json` came first, with
    an iOS reader (`FoodExportDayBucketingTests`) in its own commit, then the Kotlin reader in
    `FoodExportReportFactoryTest`. Both read entries as ISO offset date-times and check only the day
    count and each entry's day index, not the locale-dependent labels. `FoodExportReportFactory` takes a
    `StringProvider` (a `Context`-free counterpart of `L10n`), a `ZoneId` and a `Locale`, and formats the
    day labels with `FormatStyle.FULL`. The file goes to `cacheDir/exports`, and the folder is emptied
    before each export instead of deleting the file after sharing, because `ACTION_SEND` never reports
    when the receiving app has finished reading the URI. The share sheet is a `Intent.createChooser`
    over a `FileProvider` URI. The export screen is a full-screen dialog over the meal type sheet,
    and `MealTypeSheetRouter` and `isExportPushed` from step 1 arrive here with the toolbar button.
    The date pickers are Material `DatePickerDialog`s. PDF rendering cannot run in the JVM unit tests
    (PdfKmp needs an Android context), so the PDF cases of `GenerateFoodExportUseCaseTests` are left
    out and ExportKit's own `jvmTest` covers the renderer. Tests beyond iOS: `ExportViewModelTest`
    and the cleanup of an earlier export.
15. [ ] **Moderation.** *Decision:* whether Android needs it at all. It is maintainer-only, and the
    maintainer may keep using iOS. If yes, port `Features/Moderation/*` and its use cases last.
    *Ported with deviations (without OCR):* the decision was to port it. `CreateFoodItemUseCase`,
    `UpdateFoodItemUseCase`, `FetchPendingSubmissionsUseCase`, `ApproveSubmissionUseCase`,
    `RejectSubmissionUseCase`, `FetchFoodItemReportsUseCase` and `DeleteFoodItemReportUseCase` come with
    their tests. `FetchFoodItemByBarcodeUseCase` gained the batched `invoke(barcodes)` that step 6 had
    deferred, as a default interface method like on iOS. A denied Firestore write is recognised by
    `Throwable.isFirestorePermissionDenied` (`FirebaseFirestoreException.Code.PERMISSION_DENIED`), the
    counterpart of `matches(domain:code:)`. `FirestoreDataProviderFake` gained `stubbedSetError`,
    `stubbedDeleteError`, `stubbedServerReadError` and `stubbedServerDocumentSequence`, since iOS tests
    used a private fake per test. The four screens are full-screen dialogs (as in step 1), opened from
    `AccountView`'s maintainer section through `ModerationConfigurator`, and the queue and the reports
    screen push the review and editor dialogs over themselves. The review and editor view models are
    verbatim subsets without the OCR members (`NutritionLabelPrefilling`, `recognizedFields`, the camera
    and `onFormFieldEdited`), which wait for the nutrition-label follow-up of step 12. The report
    *Resolved* action is a trailing text button, not a swipe action, and pull-to-refresh is
    `PullToRefreshBox`. When a review ends with an alert (already resolved, changed since review), the
    dialog closes after the alert is acknowledged, since an alert cannot outlive its dialog. Tests beyond
    iOS: the batched barcode fetch (empty input, deduplication).

- [ ] **Apple sign-in on Android** — Firebase offers it only through a web OAuth flow that needs an
  Apple Services ID this project does not have. The iOS app currently signs in with Google only,
  since there is no paid Apple Developer account, so this waits for both.
- [ ] **Firebase setup for an Android app** — the Firebase console side is done (app
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
