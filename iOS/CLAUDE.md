# iOS client — conventions for Claude

This file applies to everything under `iOS/`. The root `CLAUDE.md` still applies for language,
documentation, code navigation, commits and the general working rules.

The Android app mirrors this architecture natively ([ADR 0038](../docs/adr/0038-android-client-mirrors-the-ios-architecture-natively.md)),
so the layering, the UseCase pattern and the naming below are the reference `Android/CLAUDE.md` ports from.

## Styl kódu

- **Formátování vícepodmínkových `if`**: každou podmínku piš na samostatný řádek, otevírací závorka `{` patří na vlastní řádek za poslední podmínku:
  ```swift
  if
      let foo = foo,
      let bar = bar,
      !bar.isEmpty
  {
      // tělo
  }
  ```
  Ne takto:
  ```swift
  if let foo = foo,
     let bar = bar,
     !bar.isEmpty {
      // tělo
  }
  ```

- **Nikdy nepoužívej force unwrap (`!`)** — SwiftLint to odmítne. Místo toho:
  - `guard let x = x else { throw ... }` pro věci, které by být `nil` neměly, ale mohou
  - `?? fallback` pro skutečně nezávislé fallbacky
  - Přepis kódu tak, aby optional nevznikl (např. jiná Calendar API)

- **Trailing closure** — pokud je poslední parametr closure, vždy používej trailing syntax. Platí i v `#Preview` blocích a callback parametrech ViewModel initů (např. `onFoodUpdated:`, `onSaved:`):
  ```swift
  // správně
  FoodConsumedDetailViewModel(food: food, updateFoodConsumed: useCase) {}
  // špatně — SwiftLint trailing_closure violation
  FoodConsumedDetailViewModel(food: food, updateFoodConsumed: useCase, onFoodUpdated: {})
  ```

Pravidlo o komentářích v kódu je v kořenovém `CLAUDE.md`.

---

## Architecture — MVVM + UseCase Layer

Three-layer structure:

```
View → ViewModel → UseCase → (DataProvider / persistence directly)
```

- **View** — SwiftUI `struct`, no business logic
- **ViewModel** — `final class`, conforms to `ObservableObject`, holds `@Published` state
- **UseCase** — `struct` (value type), single responsibility, callable via `callAsFunction`

---

## UseCase Pattern

Each use case is defined by a protocol with a `callAsFunction` signature, implemented as a `struct`, and injected as `any UseCaseProtocol`.

```swift
protocol LoadExchangeRatesUseCaseProtocol {
    func callAsFunction(for currency: String) async throws -> [ExchangeRateDomain]
}

struct LoadExchangeRatesUseCase: LoadExchangeRatesUseCaseProtocol {
    private let dataProvider: any DataProvider

    func callAsFunction(for currency: String) async throws -> [ExchangeRateDomain] {
        let response: [ExchangeRateDTO] = try await dataProvider.loadAsync(
            ExchangeRateEndpoint.rates(currency: currency)
        )
        return response.map(ExchangeRateDomain.init)
    }
}

struct LoadExchangeRatesUseCaseFake: LoadExchangeRatesUseCaseProtocol {
    func callAsFunction(for currency: String) async throws -> [ExchangeRateDomain] { [] }
}
```

Call site reads as natural language: `try await loadExchangeRates(for: currency)`.

For CoreData-backed UseCases the UseCase takes `NSManagedObjectContext` directly and performs the persistence operation itself — no extra layer in between.

---

## Networking / Data Access — DataProvider Protocol

- Generic `FirestoreDataProviderProtocol` wraps Firestore: `loadAsync<T: Decodable>` + `saveAsync<T: Encodable>`
- Each UseCase calls the DataProvider directly and maps DTOs to domain types
- CoreData UseCases receive `NSManagedObjectContext` via `init` and operate on it directly
- Domain models are never coupled to DTO or CoreData types

---

## Dependency Injection — Configurator Pattern

No singleton DI container. Each feature has a `Configurator` struct that assembles the full dependency graph at the call site.

```swift
struct LoginConfigurator {
    func createView() -> LoginView {
        LoginView(
            viewModel: LoginViewModel(
                loginUseCase: LoginUseCase(
                    dataProvider: FirestoreDataProvider()
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
@Published var isCardPickerPushed = false

// View
.navigationDestination(isPresented: $viewModel.isCardPickerPushed) {
    router.makeCardPickerView()
}
```

Navigation primitives used: `NavigationStack`, `navigationDestination`, `.sheet`, `.alert`.

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

---

## Testing — XCTest + Fake Objects

- Framework: **XCTest** only — no Quick, Nimble, or other libraries
- **Fake objects** (structs/classes implementing the protocol) — no runtime mocking frameworks
- Every test case uses a `makeSUT()` factory method
- Memory leak tracking via `addTeardownBlock` (class SUT only — not structs)

```swift
final class LoadExchangeRatesUseCaseTests: XCTestCase {
    func test_loadsRates_returnsExpectedResult() async throws {
        let (sut, _) = makeSUT()
        let rates = try await sut(for: "USD")
        XCTAssertFalse(rates.isEmpty)
    }

    private func makeSUT() -> (sut: LoadExchangeRatesUseCase, dataProvider: DataProviderFake) {
        let dataProvider = DataProviderFake()
        let sut = LoadExchangeRatesUseCase(dataProvider: dataProvider)
        return (sut, dataProvider)
    }
}
```

Test coverage targets the **UseCase** layer.

**Nikdy nespouštěj a neklikej v iOS Simulátoru**, abys ověřil, že změna funguje — to si otestuje
uživatel sám ručně, spouštění simulátoru zbytečně žere tokeny. `xcodebuild build` (ověření
kompilace) a `xcodebuild test` (spuštění unit testů) jsou v pořádku a žádoucí; jen to nekonči
spuštěním/klikáním v běžící appce.

---

## Naming Conventions

| Category | Convention | Example |
|---|---|---|
| Protocols | `Protocol` suffix | `LoadExchangeRatesUseCaseProtocol` |
| Fake test objects | `Fake` suffix | `DataProviderFake` |
| UI lifecycle callbacks | `on` prefix | `onAppear`, `onSegmentChanged` |
| Boolean properties | `is/has/can` prefix | `isLoading`, `hasError` |
| Collections | plural | `exchangeRateRows`, `cards` |
| Files | `[Feature][Type].swift` | `ExchangeRateViewModel.swift` |

---

## Liquid Glass (iOS 26+)

Deployment target je iOS 26 — používej Liquid Glass bez `@available` podmínek.

**Floating Action Button (FAB)** — standardní pattern pro primární akci na obrazovce:

```swift
.safeAreaInset(edge: .bottom) {
    Button { action() } label: {
        Image(systemName: "plus")
            .font(.title2)
            .fontWeight(.semibold)
            .padding(20)
    }
    .glassEffect(.regular, in: .circle)
    .padding(.bottom, 8)
}
```

- `.safeAreaInset` místo `ToolbarItem(.bottomBar)` — správně odsadí obsah pod tlačítkem
- `.glassEffect(.regular, in: .circle)` — čirý glass efekt; `.tinted()` neexistuje, `.tint(.accentColor)` způsobuje neviditelnou ikonku (modrá na modrém)
- Pro centrování vynech `HStack { Spacer(); ... }` — `safeAreaInset` centruje obsah ve výchozím stavu
- Pro zarovnání vpravo dole: `HStack { Spacer(); Button; ... }` s `.padding(.trailing, 20)`
- Nikdy nepoužívej `BaseImage` s velkou velikostí (`.extraLarge` = 60 pt) v toolbaru
