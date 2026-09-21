# 0038. The Android client is native Kotlin mirroring the iOS architecture, sharing only pure logic

- **Status:** Accepted
- **Scope:** Android
- **Date:** 2026-09-21

## Context

[ADR 0037](0037-shared-modules-target-ios-and-jvm-and-are-consumed-by-composite-build.md) made the
four KMP modules consumable from Android and placed the future app in `Android/`, but did not say
how the app itself is written. Before its first screen, one decision has to be made that is
effectively permanent: **how much code the two clients share.** Everything else — UI toolkit,
state model, test framework — follows from it.

The forces:

- The iOS app exists and ships: 52 use cases, 12 view models, 23 screens, 76 test files, written
  to the conventions in the root `CLAUDE.md` (MVVM + UseCase, Configurator DI, XCTest with fakes).
- The maintainer knows Swift and does not know Kotlin or Android. Android code is written with an
  AI assistant, typically a cheaper model working from written records.
- Google ships no official Firebase SDK for Kotlin Multiplatform (checked 2026-09-21). Sharing the
  data layer would mean depending on a community wrapper (GitLive `firebase-kotlin-sdk`) for the
  lifetime of both apps.
- Jetpack Navigation 3 is stable (1.0, November 2025) and is Google's recommended navigation for
  Compose; its back stack is plain state owned by the app, not a controller.
- The Android Firestore SDK's `DocumentSnapshot.toObject()` leaves a field that is absent from the
  document at its Kotlin default value instead of failing. iOS `Codable` fails the decode, and
  ARCHITECTURE § 1.7's *Required on read* column is built on that behaviour.

Three options were considered:

| | Shared in KMP | Written twice | Cost to iOS |
|---|---|---|---|
| **A. Native Android mirroring iOS** | pure logic only, as today | use cases, DTOs, Firestore access, UI | none |
| **B. Shared data layer** | also use cases, DTOs, Firestore | UI and view models | rewrite 52 use cases and their tests in Kotlin |
| **C. Compose Multiplatform** | everything, UI included | nothing | discard the SwiftUI app |

B was rejected because it moves the core of *both* apps into a language the maintainer cannot
read, rewrites a working iOS data layer, and puts a community Firebase wrapper under both. C was
rejected because it discards a finished iOS UI.

## Decision

1. **The Android app is written natively in Kotlin with Jetpack Compose, and mirrors the iOS
   architecture layer for layer:** View → ViewModel → UseCase → `FirestoreDataProviderProtocol`.
   Each Swift type in `iOS/Kalorie/` that has a counterpart has one Kotlin file, in the
   matching package, **with the same type name**. A ported file can be found from its iOS original
   by name alone.
2. **KMP holds pure, deterministic logic with no I/O that both clients must compute identically.**
   That is the rule MacroKit, MealKit and TextKit were already built on. Use cases, DTOs, Firestore
   access, view models and UI are **not** moved into KMP. Which of the Swift-only rules move under
   this rule is the separate `TODO.md` item *Golden vectors for the Swift-only rules*.
3. **Names are identical to iOS, including the `Protocol` suffix**, even though Kotlin style would
   say `interface DeleteFoodConsumedUseCase` + `class DefaultDeleteFoodConsumedUseCase`. The
   pairing is worth more than the idiom:

   ```kotlin
   interface DeleteFoodConsumedUseCaseProtocol {
       suspend operator fun invoke(id: String)
   }

   class DeleteFoodConsumedUseCase(
       private val dataProvider: FirestoreDataProviderProtocol,
       private val authProvider: AuthProviderProtocol,
   ) : DeleteFoodConsumedUseCaseProtocol {
       override suspend operator fun invoke(id: String) {
           val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
           dataProvider.deleteAsync(id = id, from = Constants.Firestore.foodConsumed(userId))
       }
   }
   ```

   `operator fun invoke` is Kotlin's `callAsFunction`, so call sites read the same:
   `deleteFoodConsumed(id)`.
4. **Dependency injection is the Configurator pattern, by hand.** No Hilt, Dagger or Koin. A
   Configurator's `createView` becomes a `@Composable` function that builds the ViewModel through
   `viewModel { ... }` (the androidx initializer DSL), which gives the `@StateObject` lifetime —
   the ViewModel survives configuration changes such as rotation.
5. **State:** a ViewModel extends `androidx.lifecycle.ViewModel` and exposes
   `StateFlow<State>`, where `State` is a `sealed interface` in place of the Swift `enum State`.
   Side effects start from `LaunchedEffect(Unit) { viewModel.onAppear() }`, the counterpart of
   `.task`.
6. **Navigation:** pushed screens use Navigation 3. Its back stack of typed keys is the
   counterpart of `navigationDestination(for:)`, and a Router's `make…View` functions become the
   bodies of its entry provider. Sheets stay ViewModel-owned `Boolean` state rendered as
   `ModalBottomSheet`, and alerts stay ViewModel-owned state rendered as `AlertDialog`, as on iOS.
   Parent callbacks (`onFoodUpdated`, `onSaved`) are passed as lambdas exactly as on iOS.
7. **Firestore goes through the official Firebase Android SDK** behind the same
   `FirestoreDataProviderProtocol` method list as ARCHITECTURE § 1.5, awaiting `Task`s with
   `kotlinx-coroutines-play-services`. **DTOs are `@Serializable` (kotlinx.serialization) with
   `@SerialName` for each snake_case wire name**, and the provider converts the document map to
   and from them. `toObject()` is not used, because it silently defaults missing fields and would
   break the *Required on read* contract.
8. **Tests are JUnit 4 plus `kotlinx-coroutines-test`, with hand-written fakes**, a `makeSUT()`
   factory per test class, and no MockK or Mockito. Coverage targets the use case layer. Fakes
   live in the `debug` source set — the counterpart of `#if DEBUG`, visible to Compose previews
   and to `testDebugUnitTest`, absent from release builds. There is no counterpart to the iOS
   memory-leak teardown check.
9. **Claude does not run the Android emulator**, as it does not run the iOS Simulator. Compiling
   (`./gradlew :app:assembleDebug`) and unit tests (`./gradlew :app:testDebugUnitTest`) are
   expected.

The day-to-day rules that follow from this are in `Android/CLAUDE.md`.

## Consequences

- Porting a feature is a translation with a fixed target. The iOS file names the Kotlin file,
  its package, its dependencies and its tests. This is the property the model-split workflow
  depends on.
- **Every use case, DTO and screen exists twice, and the two copies can drift.** The guards are
  the `Cross-platform` ADRs and design docs, ARCHITECTURE § 1.3/§ 1.7 as the wire contract, and
  moving deterministic rules into KMP under item 2. A behaviour change on iOS that touches a
  `Cross-platform` rule is not finished until it is recorded where the Android port will find it.
- Kotlin readers will find `…Protocol` names and `// MARK: -` headers unidiomatic. That is
  deliberate, and not something to "clean up".
- The `Scope` values in `docs/README.md` gain `Android`, for records that bind this client only,
  as `iOS` does for the other.
- Android-only concerns that have no iOS counterpart — the Activity, the back gesture, process
  death, the Play Store's web account-deletion link — are decided when they come up. This record
  does not cover them.
- Rejected option B stays reachable in part: a pure piece of logic can move into KMP one module at
  a time without revisiting this record. Moving I/O into KMP would supersede item 2.
- **Not verified:** everything above is written before an Android project exists. The first
  scaffold must confirm that Navigation 3's ViewModel scoping (`lifecycle-viewmodel-navigation3`)
  composes with `viewModel { ... }` from a Configurator, and that the kotlinx.serialization
  bridge handles Firestore's `Long`/`Double` number split for fields declared `Double`.
