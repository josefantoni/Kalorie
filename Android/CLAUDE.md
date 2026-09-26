# Android client — conventions for Claude

This file applies to everything under `Android/`. The root `CLAUDE.md` still applies for language,
documentation, code navigation, commits and the general working rules. `iOS/CLAUDE.md` does not
apply here, but it holds the architecture and patterns this app ports. Why the app is written this way is
[ADR 0038](../docs/adr/0038-android-client-mirrors-the-ios-architecture-natively.md).

## The one rule

**The Android app is a port of the iOS app, not a new design.** Before writing any Kotlin type,
find its Swift original in `iOS/Kalorie/` by name, read it, and port it with the same name,
the same dependencies, the same `State` cases and the same tests. When something has no iOS
counterpart, say so and ask; do not invent a structure.

Before porting behaviour, read the `Cross-platform` and `Backend` records for that area. The
*Read first:* line of each section in `docs/ARCHITECTURE.md` lists them. They are requirements
here. `iOS`-scoped records are only precedent.

When a ported rule has a fixture in `fixtures/`, the port's tests read it at test time, and a
failing case means the port is wrong — iOS is the reference
([ADR 0039](../docs/adr/0039-swift-only-rules-move-into-kmp-or-share-golden-vectors.md)).

## Stack

| Concern | Choice |
|---|---|
| Language / UI | Kotlin 2.4.10 ([ADR 0034](../docs/adr/0034-kotlin-toolchain-pinned-at-2.4.10-across-kmp-modules.md)), Jetpack Compose, Material 3 |
| Build | AGP 9.x, `compileSdk` 37, `minSdk` 26 — both forced by ExportKit ([ADR 0037](../docs/adr/0037-shared-modules-target-ios-and-jvm-and-are-consumed-by-composite-build.md)) |
| Shared logic | MacroKit, MealKit, TextKit, ExportKit via `includeBuild`, as ADR 0037 shows |
| State | `androidx.lifecycle.ViewModel` + `StateFlow` |
| Navigation | Navigation 3 for pushed screens; `ModalBottomSheet` / `AlertDialog` for sheets and alerts |
| Backend | Official Firebase Android SDK (BoM), `kotlinx-coroutines-play-services` for `.await()` |
| Serialization | kotlinx.serialization for DTOs — never `DocumentSnapshot.toObject()` |
| Tests | JUnit 4, `kotlinx-coroutines-test`, hand-written fakes |
| DI | Configurators by hand — no Hilt, Dagger or Koin |

## Layers, iOS → Android

| iOS | Android |
|---|---|
| `protocol XUseCaseProtocol` + `struct XUseCase` | `interface XUseCaseProtocol` + `class XUseCase` |
| `func callAsFunction(...) async throws` | `suspend operator fun invoke(...)` |
| `final class XViewModel: ObservableObject` | `class XViewModel(...) : ViewModel()` |
| `@Published private(set) var state: State` | `private val _state = MutableStateFlow<State>(State.Idle)` + `val state: StateFlow<State>` |
| `enum State { case idle, loading, error(String) }` | `sealed interface State { data object Idle; data object Loading; data class Error(val message: String) }` |
| `@Published var isXPresented = false` | `MutableStateFlow(false)`, rendered as a sheet or dialog |
| `.task { await viewModel.onAppear() }` | `LaunchedEffect(Unit) { viewModel.onAppear() }` |
| `@StateObject` created in a Configurator | `viewModel { XViewModel(...) }` inside the Configurator's `@Composable createView` |
| `navigationDestination(for:)` + Router | Navigation 3 back stack of typed keys; the Router's `make…View` functions are the entry provider |
| `…Domain` struct | `data class`, no Firebase or serialization annotations |
| `…DTO` with `CodingKeys` | `@Serializable data class` with `@SerialName("snake_case")` |
| `enum XError: Error` | `sealed class XError : Exception()` |
| `#if DEBUG struct XFake` in the same file | `XFake` in `app/src/debug/`, the same package |
| `Constants.Firestore` | `object Constants { object Firestore { ... } }` — the only place a path string is written |

Packages mirror the iOS folders under `antoni.kalorie`: `core.usecases`, `core.models`,
`core.networking`, `features.dashboard`, and so on.

## Wire contract gotchas

- **Missing required field = failed decode**, as on iOS. A DTO property with no default value is
  required on read. Give a default only where ARCHITECTURE § 1.7 says the field is optional.
- **Dates are seconds since 1970 as a `Double`**, never milliseconds and never a Firestore
  `Timestamp` ([ADR 0008](../docs/adr/0008-dates-as-epoch-seconds-not-firestore-timestamp.md)).
  `System.currentTimeMillis()` must be divided by 1000.
- **UUID ids are uppercase.** `UUID.randomUUID().toString()` is lowercase, and the rules refuse it
  without saying why (ARCHITECTURE § 1.2). Always write `.uppercase()`.
- **`setAsync` overwrites the whole document.** Send every field.

## Strings

`res/values/strings.xml` (Czech) and `res/values-en/strings.xml` are **generated** from
`localisation/strings.json`, the single source shared with iOS; never edit them by hand. Keys are
1:1 with iOS, so a new string is added to that JSON and then `npm run generate-strings` is run in
`scripts/`. A string only Android needs still goes into the same file.

## Code style

- Official Kotlin coding conventions, enforced by ktlint through detekt. The Swift formatting rules
  in `iOS/CLAUDE.md` do not carry over.
- **Never use `!!`.** Use `?: throw`, `?: fallback`, or restructure so the nullable never appears.
- Trailing lambda syntax whenever the last parameter is a function.
- The same `// MARK: - Properties` / `// MARK: - Init` / `// MARK: - Body` / `// MARK: - Functions`
  headers as iOS, so paired files read alike. Kotlin has no `// MARK:` convention; use them
  anyway.
- No comments beyond what the root `CLAUDE.md` allows.

## Testing

- Every use case gets a test class ported from its iOS `…Tests.swift`, with the same test names.
- `makeSUT()` returns the SUT and its fakes. Use `runTest { }` for suspend code.
- No MockK, no Mockito. Fakes are hand-written classes implementing the `…Protocol` interface.

## Running things

- `./gradlew :app:assembleDebug` to check the build, and `./gradlew :app:testDebugUnitTest` for
  unit tests.
- **Never start or click through the Android emulator** to check a change. The user tests on a
  device by hand, as with the iOS Simulator.
