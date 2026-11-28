# iOS client — conventions for Claude

This file applies to everything under `iOS/`. The root `CLAUDE.md` still applies for language,
documentation, code navigation, commits and the general working rules.

The Android app mirrors this architecture natively ([ADR 0038](../docs/adr/0038-android-client-mirrors-the-ios-architecture-natively.md)),
so the layering, the UseCase pattern and the naming below are the reference `Android/CLAUDE.md` ports from.

## Code style

- **Formatting multi-condition `if`**: write each condition on its own line; the opening brace `{` goes on its own line after the last condition:
  ```swift
  if
      let foo = foo,
      let bar = bar,
      !bar.isEmpty
  {
      // body
  }
  ```
  Not like this:
  ```swift
  if let foo = foo,
     let bar = bar,
     !bar.isEmpty {
      // body
  }
  ```

- **Never use force unwrap (`!`)** — SwiftLint rejects it. Instead:
  - `guard let x = x else { throw ... }` for things that should not be `nil` but can be
  - `?? fallback` for genuinely independent fallbacks
  - Rewrite the code so the optional never appears (e.g. a different Calendar API)

- **Trailing closure** — if the last parameter is a closure, always use trailing syntax. This also applies in `#Preview` blocks and to callback parameters of ViewModel inits (e.g. `onFoodUpdated:`, `onSaved:`):
  ```swift
  // correct
  FoodConsumedDetailViewModel(food: food, updateFoodConsumed: useCase) {}
  // wrong — SwiftLint trailing_closure violation
  FoodConsumedDetailViewModel(food: food, updateFoodConsumed: useCase, onFoodUpdated: {})
  ```

The rule about code comments is in the root `CLAUDE.md`.

---

## Architecture — MVVM + UseCase Layer

Three-layer structure:

```
View → ViewModel → UseCase → providers (Firestore, Auth, StoreKit, …) / other UseCases
```

- **View** — SwiftUI `struct`, no business logic
- **ViewModel** — `final class`, conforms to `ObservableObject`, holds `@Published` state
- **UseCase** — `struct` (value type), single responsibility, callable via `callAsFunction`

---

## UseCase Pattern

Each use case is defined by a protocol with a `callAsFunction` signature, implemented as a `struct`, and injected as `any UseCaseProtocol`.

```swift
protocol FetchMealTypesUseCaseProtocol {
    func callAsFunction() async throws -> [MealTypeDomain]
}

struct FetchMealTypesUseCase: FetchMealTypesUseCaseProtocol {
    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    func callAsFunction() async throws -> [MealTypeDomain] {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let dtos: [MealTypeDTO] = try await dataProvider.loadAsync(from: Constants.Firestore.mealTypes(userId: userId))
        return dtos.map { MealTypeDomain(...) }.sorted { $0.startMinutes < $1.startMinutes }
    }
}

#if DEBUG
struct FetchMealTypesUseCaseFake: FetchMealTypesUseCaseProtocol {
    var stubbedTypes: [MealTypeDomain] = []

    func callAsFunction() async throws -> [MealTypeDomain] { stubbedTypes }
}
#endif
```

Call site reads as natural language: `mealTypes = try await fetchMealTypes()`.

---

## Networking / Data Access — DataProvider Protocol

- Generic `FirestoreDataProviderProtocol` wraps Firestore: `loadAsync<T: Decodable>` + `saveAsync<T: Encodable>`
- A UseCase that reads or writes Firestore calls the DataProvider directly and maps DTOs to domain types; a UseCase may also compose other UseCases
- Domain models are never coupled to DTO types

---

## Dependency Injection — Configurator Pattern

No singleton DI container. Each feature has a `Configurator` struct that assembles the full dependency graph at the call site.

```swift
struct ExportConfigurator {
    func createView(mealTypes: [MealTypeDomain]) -> ExportView {
        let dataProvider = FirestoreDataProvider()
        let authProvider = AuthProvider()
        return ExportView(
            viewModel: ExportViewModel(
                mealTypes: mealTypes,
                generateFoodExport: GenerateFoodExportUseCase(
                    fetchFoodsConsumedInRange: FetchFoodsConsumedInRangeUseCase(
                        dataProvider: dataProvider,
                        authProvider: authProvider
                    ),
                    ...
                )
            )
        )
    }
}
```

All dependencies are injected as protocols via `init`. This makes every layer independently testable and keeps the dependency graph explicit and readable.

---

## SwiftUI + Combine State Management

- Primary UI framework: SwiftUI
- `@StateObject` in root/owner views, `@ObservedObject` in child views
- Each ViewModel exposes a `State` enum:

```swift
enum State {
    case idle
    case loading
    case error(String)
}

@Published private(set) var state: State = .idle
```

- Side effects triggered from `.task` modifier:

```swift
.task { await viewModel.onAppear() }
```

---

## Navigation — Router + Configurator

`Router` struct holds Configurators for child screens and creates views for `navigationDestination` and `.sheet`. Navigation state is owned by the ViewModel as `Bool` properties.

```swift
// ViewModel
@Published var isExportPushed = false

// View
.navigationDestination(isPresented: $viewModel.isExportPushed) {
    router.makeExportView(mealTypes: viewModel.mealTypes)
}
```

Navigation primitives used: `NavigationStack`, `navigationDestination`, `.sheet`, `.alert`.

Text size is capped at `xLarge` (`dynamicTypeLimit()`, `Core/Extensions/View+DynamicTypeLimit.swift`). The app root has it, but a `.sheet` or `.popover` is a separate hosting controller and does not inherit it — apply `.dynamicTypeLimit()` to the content of every new one, or its text grows to the full accessibility sizes.
Menus, `Picker(.menu)`, alerts, confirmation dialogs, date picker popups and the share sheet are rendered by UIKit and ignore it; `UIKitDynamicTypeLimit` (same file) caps them through the window scene's content size category, so they need nothing per call site.

---

## Error Handling

- Custom `Error` enums per domain (not generic catch-all types)
- `do/catch` with `async/await` — no Combine error streams
- Error presentation handled per feature (no global error handler)

---

## Code Organisation — MARK Comments

Required section headers in every type:

```swift
// MARK: - Properties
// MARK: - Init
// MARK: - Body        // Views only
// MARK: - Functions
```

**Properties go first.** Every stored property of a type, `static` and property wrappers included, sits in
`// MARK: - Properties` at the start of the type, never between or after the initialisers and functions. Computed
properties are exempt: `body` and view helpers belong under `// MARK: - Body`, and any other computed property may
sit with the stored ones or with the code that uses it. Android has the same rule
([Android/CLAUDE.md](../Android/CLAUDE.md)).

---

## Testing — XCTest + Fake Objects

- Framework: **XCTest** only — no Quick, Nimble, or other libraries
- **Fake objects** (structs/classes implementing the protocol) — no runtime mocking frameworks
- Every test case uses a `makeSUT()` factory method
- Memory leak tracking via `addTeardownBlock` (class SUT only — not structs)

```swift
final class FetchMinimumVersionUseCaseTests: XCTestCase {
    func test_fetch_returnsTheIosField() async throws {
        let (sut, _) = makeSUT(config: AppConfigDTO(minVersionIos: "1.0.1", minVersionAndroid: "9.0"))
        let minimum = try await sut()
        XCTAssertEqual(minimum, AppVersion("1.0.1"))
    }

    private func makeSUT(
        config: AppConfigDTO? = nil
    ) -> (sut: FetchMinimumVersionUseCase, dataProvider: FetchMinimumVersionDataProviderFake) {
        let dataProvider = FetchMinimumVersionDataProviderFake()
        dataProvider.stubbedConfig = config
        let sut = FetchMinimumVersionUseCase(dataProvider: dataProvider)
        return (sut, dataProvider)
    }
}
```

Test coverage targets the **UseCase** layer.

**Never launch or click through the iOS Simulator** to check that a change works — the user
tests that by hand, and running the simulator needlessly burns tokens. `xcodebuild build` (verifying
compilation) and `xcodebuild test` (running unit tests) are fine and welcome; just do not finish by
launching or clicking through the running app.

Build against a named simulator, as CI does:
`xcodebuild build -project iOS/Kalorie.xcodeproj -scheme Kalorie -destination 'platform=iOS Simulator,name=iPhone 17,OS=latest'`.
Never use `generic/platform=iOS Simulator` — it also builds x86_64, the KMP frameworks are
arm64-only, and the link fails with `Undefined symbols for architecture x86_64`.

The Xcode build phase runs `swiftlint` with the same `iOS/.swiftlint.yml`, so a warning in Xcode and one from the
terminal command in the root `CLAUDE.md` are the same warning.

---

## Naming Conventions

| Category | Convention | Example |
|---|---|---|
| Protocols | `Protocol` suffix | `FetchMealTypesUseCaseProtocol` |
| Fake test objects | `Fake` suffix | `FetchMealTypesUseCaseFake`, `AuthProviderFake` |
| UI lifecycle callbacks | `on` prefix | `onAppear`, `onSaveTapped` |
| Boolean properties | `is/has/can` prefix | `isLoading`, `hasChanges`, `canSave` |
| Collections | plural | `mealTypes`, `favouriteFoods` |
| Files | `[Feature][Type].swift` | `SettingsViewModel.swift` |

---

## Liquid Glass (iOS 26+)

The deployment target is iOS 26 — use Liquid Glass without `@available` conditions.

**Floating Action Button (FAB)** — the standard pattern for a screen's primary action:

```swift
.safeAreaInset(edge: .bottom) {
    Button { action() } label: {
        Image(systemName: "plus")
            .font(.title2)
            .fontWeight(.semibold)
            .foregroundStyle(.white)
            .padding(20)
    }
    .glassEffect(.regular.tint(.accentColor).interactive(), in: .circle)
    .padding(.bottom, 8)
}
```

- `.safeAreaInset` instead of `ToolbarItem(.bottomBar)` — it correctly insets the content beneath the button
- `.glassEffect(.regular.tint(.accentColor).interactive(), in: .circle)` — glass in the app's accent colour (the green `AccentColor` from the asset catalog, not the system blue) with an explicitly white icon; without `.foregroundStyle(.white)` the icon would take the accent and be green on green. Clear `.glassEffect(.regular, in: .circle)` is only for secondary actions
- Never write colours as literals (`.red`, `.blue`, `Color(red:…)`) — always a role from the asset catalog via generated symbols (`Color.protein`, `Color.favourite`, …), see `docs/ARCHITECTURE.md` § 5.6
- No `.buttonStyle(.glassProminent)` on the FAB — it adds its own inner padding and the circle grows
- To centre, omit `HStack { Spacer(); ... }` — `safeAreaInset` centres content by default
- To align bottom-right: `HStack { Spacer(); Button; ... }` with `.padding(.trailing, 20)`
- Never use `BaseImage` at a large size (`Constants.ImageSize.extraLarge` = 60 pt) in a toolbar

**Text buttons** — a full-width primary action (Export, Create, Delete) is `PrimaryButton` (`Components/PrimaryButton.swift`): `PrimaryButton(title, style: .primary | .destructive) { … }`. It is a 56 pt Liquid Glass capsule, bold white text, tinted with the accent (`.primary`) or `Color.error` (`.destructive`). Never rebuild it with `.glassProminent`, `.controlSize(.large)` or a hand-made `.background(...)` at the call site. A secondary action stays `.buttonStyle(.glass)`; `borderedProminent` is not used for new buttons. Android has the same component and styles ([Android/CLAUDE.md](../Android/CLAUDE.md)).

**Badge button** — a round accent button with a white icon is `BadgeButton` (`Components/BadgeButton.swift`): `BadgeButton(imageName:) { … }`. It is a 34 pt accent circle (`size:` overrides it) with a white 18 pt symbol, dimmed to 0.4 when `.disabled(true)`. Never rebuild it at the call site with `BaseButton(.plusCircle)` or a hand-made `.background(Color.accentColor)`. It is not the FAB above, which is a screen's primary action. Android has the same component ([Android/CLAUDE.md](../Android/CLAUDE.md)).

- **Add a row** (new portion, new meal in the settings layout) is `BadgeButton(imageName: .plus)`.
- **A button inside a text field** (barcode scan next to search, the barcode field) is passed as `FloatingLabelTextField(..., trailingButton: BadgeButton(imageName:) { … })`. The field overlays it centred on the title + text block and reserves trailing space for it, so it never adds height — the row stays at the 52 pt of a plain field. Never put the field and a button side by side in an `HStack`.

**Picker value** — a value in a row that opens a picker (a menu or a wheel popover) shows the value and its up/down chevron both in the accent colour, so it reads as tappable; a row title next to it stays `Color.primary`. An unset value is "—" in the same colour.

**Spinner** — there is one spinner, `AccentProgressView()` (`Components/AccentProgressView.swift`, a UIKit indicator in the accent colour). Use it bare for a small spot (a button, a pill, a row), never a SwiftUI `ProgressView()`, which is grey. `LoaderIndicator` is the same spinner on a 50 pt card, and `.loader(_:)` is that card as a full-screen overlay. The white determinate bar over the camera is the one deliberate exception.

**Several `PrimaryButton`s stacked in a `List`** — each in its own `Section` with `.listRowInsets(EdgeInsets())` and `.listRowBackground(Color.clear)` (see `ModerationReviewView.swift`), no extra `.padding` and no non-zero `.listRowInsets` around the button. Any "empty" gap inside the row exposes the Liquid Glass shadow/material on a colour that does not match the List's real background (even a manual `Color(uiColor: .systemGroupedBackground)` on `.listRowBackground` fixes it unreliably) — a visible coloured fringe appears around the button. The gap between buttons is the list's section spacing (below), not padding or insets inside the row.

**Section spacing** is 20 on every screen. Every `List` and `Form` gets `.appSectionSpacing()` (`Core/Extensions/View+SectionSpacing.swift`); never write `.listSectionSpacing(.custom(_:))` at a call site. The gap between the navigation bar and the first object is `Constants.Layout.topContentSpacing` (8). The same modifier sets it. A section header with a button is `ButtonSectionHeader` (`Components/ButtonSectionHeader.swift`), which keeps the button out of the header's height so it is spaced like a plain title; never put the title and a button in an `HStack`. The button is a `BadgeButton` of size 28, never a `.glass` text button. A section header keeps the system header's top inset, so a title gets more room above it than an untitled section. The one exception is a header that is the first object of its list: it gets `.appFirstSectionHeader()`, which drops the inset so the top gap matches the other screens. A shared section that is first only on some screens takes a `startsList` flag (`FoodItemFormSections`). It must be the header's last modifier; a modifier applied after it (seen with `onGeometryChange`) keeps it from taking effect. Never add `.contentMargins(.top, …)` at a call site; a screen that is not a `List` or `Form` pads its first object with the constant. The section holding a `HintCard` takes `.hintCardSectionSpacing()` (16, the gap between the add sheet's mode picker and the search field). The only other spacing value is `.listSectionSpacing(0)` in `PortionDraftAddSection`, which keeps the `+` button against its rows (`docs/ARCHITECTURE.md`); the section after it in `FoodItemFormSections` carries `.appSectionSpacing()` itself to restore the gap.

## App Check debug tokens

App Check is installed but not enforced on Storage and Firestore (console status: Monitoring; enforcement is a later step, see design 0026). Debug builds use the debug provider, so every new simulator or debug device must have its debug token registered under Firebase Console → App Check → app menu → Manage debug tokens. The provider prints the token on first launch (Xcode console). Once enforcement is on, an unregistered build gets `permission-denied`. A token is a credential, do not put it in the shared `Kalorie.xcscheme`.
