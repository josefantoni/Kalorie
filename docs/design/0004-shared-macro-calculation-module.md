# Design: Shared macro calculation module (Kotlin Multiplatform)

- **Status:** Implemented
- **Scope:** Cross-platform, iOS
- **Date:** 2026-08-12

## Context and scope

Macro arithmetic — scaling a food's nutrient values to a weight, and summing a list of foods —
is currently reimplemented at five call sites with no shared source of truth:

| Site | What it does | Basis |
|---|---|---|
| `Kalorie/Features/FoodQuantity/FoodQuantityViewModel.swift:42-46` | Scales `FoodItemDomain` for a live preview before saving | `grams / 100` |
| `Kalorie/Core/UseCases/SaveFoodConsumedUseCase.swift:32-48` | Scales `FoodItemDomain` for the persisted document | `grams / 100` |
| `Kalorie/Core/Models/FoodConsumedModel.swift:30-50` (`ScaledMacros`) | Rescales an already-logged `FoodConsumedDomain` to a new weight | `newWeight / oldWeight` |
| `Kalorie/Features/Dashboard/DashboardViewModel.swift:60-72` (`dailyMacros`) | Sums a day's foods | — |
| `Kalorie/Features/Dashboard/MealSectionMacroView.swift:17-23` | Sums one meal section's foods | — |

The project targets iOS today, with a second client a realistic direction (see
`docs/README.md` → *Platform scope*). Macro arithmetic is `Cross-platform` behaviour by that
definition: two clients that round differently write divergent values into the same Firestore
documents.

This document proposes extracting that arithmetic into a Kotlin Multiplatform module consumed by
the iOS app as a binary framework. The module is deliberately the smallest useful unit — it is
both a real de-duplication and the vehicle for establishing whether KMP is viable in this
project at all.

**Boundary of this document:** the calculation module and its consumption from Swift. Nothing
else moves to KMP.

## Goals

- One implementation of macro scaling and summation, shared and covered by tests in Kotlin.
- All five call sites above delegate to it; no macro arithmetic remains inline in Swift.
- The existing Swift test suite passes unchanged, except where this document explicitly records
  a behaviour change (see *Rounding*).
- The KMP toolchain — Gradle build, XCFramework production, Xcode integration — is proven end to
  end on a case small enough that failures are toolchain failures, not logic failures.

## Non-goals

- **Migrating anything else to KMP.** Not the data layer, not auth, not search. `FirestoreDataProvider`
  and the Apple/Google sign-in providers stay in Swift, permanently as far as this document is
  concerned.
- **Sharing UI.** Compose Multiplatform is out of scope.
- **An Android client.** This module makes one possible; it does not start one.
- **Moving date/meal-type logic.** `DashboardViewModel.groupedFoods` and `foodFallsIn(mealType:food:)`
  are the obvious second candidate but need `kotlinx-datetime`; keeping them out means this step
  has exactly one new variable.
- **Changing the Firestore schema or DTOs.** The module operates on values already in memory.

## Design

### Module shape

A single Kotlin source set (`commonMain`) with no platform-specific code and no third-party
dependencies:

```kotlin
data class Macros(
    val calories: Int,
    val protein: Double,
    val carbohydrate: Double,
    val carbohydrateSugar: Double,
    val fat: Double,
    val fatUnsaturated: Double,
    val fiber: Double,
    val salt: Double,
)

fun Macros.scaled(by factor: Double): Macros
fun List<Macros>.total(): Macros
```

**One primitive, not two.** The five call sites differ only in how they derive the factor
(`grams / 100` vs. `newWeight / oldWeight`); the arithmetic is identical. The factor is computed
at the call site and `scaled(by:)` stays basis-agnostic. Introducing separate
`scaledFromHundredGrams` / `scaledFromExisting` entry points would re-create the duplication
inside the module.

### Field naming

`FoodItemDomain` and `FoodConsumedDomain` disagree on two field names for the same quantity:

| `FoodItemDomain` | `FoodConsumedDomain` | `Macros` |
|---|---|---|
| `carbohydratePureSugar` | `carbohydrateSugar` | `carbohydrateSugar` |
| `fatUnsaturatedFattyAcids` | `fatUnsaturated` | `fatUnsaturated` |
| `caloriesPerHundredGrams` | `calories` | `calories` |

`Macros` adopts the `FoodConsumedDomain` names, since that is the persisted shape. The Swift
side maps both domain types into `Macros` at the call site; the domain types themselves are not
renamed (out of scope, and `caloriesPerHundredGrams` carries meaning the others do not).

### Rounding

The call sites currently disagree, and this is a live inconsistency rather than a design choice:

- `SaveFoodConsumedUseCase:40` and `FoodQuantityViewModel:42` **truncate**: `Int(calories * ratio)`
- `ScaledMacros` **rounds**: `Int((calories * ratio).rounded())`

The consequence today is that logging 150 g of a 133 kcal/100 g food persists 199 kcal, while
logging 100 g and then editing the weight to 150 g persists 200 kcal. Same food, same weight,
different stored value depending on the path taken.

**Decision: the shared module rounds** (`kotlin.math.roundToInt`, half away from zero), and the
truncating sites change to match. Rounding is the more defensible rule, it is already what the
edit path does, and unifying on truncation would mean making the *displayed* preview wrong in a
new way. Non-integer macros (`Double`) are not rounded at all — only `calories` is an `Int`.

**This is a behaviour change.** Tests asserting truncated calorie values will fail and must be
updated to the rounded expectation — deliberately, with the new value stated in the test, not by
relaxing the assertion. Existing Firestore documents are not backfilled; a ±1 kcal difference on
historical entries is not worth a migration.

### Swift integration

The framework is produced as an XCFramework and embedded in the app target. `Macros` arrives in
Swift as a **class**, not a struct — see *Interop* below — so the Swift side does not adopt it as
a domain type. Instead:

- `ScaledMacros` (`FoodConsumedModel.swift`) and `DailyMacros` (`DashboardViewModel.swift`) are
  **kept** as Swift structs and become thin adapters: they build a `Macros`, call the shared
  function, and expose the result through their existing properties.
- Every consumer of those two types (`MacroSummaryView`, `FoodConsumedDetailViewModel`,
  `UpdateFoodConsumedUseCase`, `MealSectionMacroView`) therefore compiles unchanged.
- `FoodQuantityViewModel`'s five `scaled*` computed properties and `MealSectionMacroView`'s seven
  `reduce` properties are replaced by a single call through those adapters.

This keeps the KMP type from leaking past the adapter layer, which is what makes the module
removable if the experiment fails.

### Interop — what changes at the language boundary

Kotlin is exported to Swift through a generated Objective-C header, so the project's Swift
conventions do not survive the crossing. Expect and accept:

- **`data class` becomes a Swift class** — reference semantics, not value semantics. `==` works
  (Kotlin's generated `equals` maps to `isEqual:`), but assignment shares an instance. This is
  why `Macros` stays behind an adapter instead of replacing `ScaledMacros` outright.
- **No `callAsFunction`, no `any Protocol`, no generics with value types.** A `List<Macros>`
  crosses as `[Macros]`, but an `Int?` inside a generic position arrives as `KotlinInt?`. The
  API above is deliberately shaped to avoid every one of these cases: only `Double`, `Int`, and
  a flat list.
- **Kotlin `object`/top-level functions** land as static members of a generated `…Kt` class.
  Extension functions on `Macros` become static functions taking the receiver as the first
  argument.

The whole point of keeping the API to primitives and one flat list is that none of the above
becomes a problem in this step. It will become a problem in any later step, which is exactly the
information this experiment is meant to produce.

### Repository location

**The Gradle build must not run inside iCloud Drive.** Kotlin/Native build and cache directories
hold tens of thousands of files and multiple gigabytes; iCloud attempts to sync them and may evict
files mid-build, and the old path contained a space, a `~`, and a diacritic.

**Resolved 2026-08-12:** the repository was moved out of iCloud Drive to `~/Kalorie`. This
prerequisite is met and the module can be built in place. Do not move the repository back.

### Xcode wiring

The Xcode project uses `PBXFileSystemSynchronizedRootGroup`, so Swift sources appear
automatically from the filesystem. A binary framework does **not** — embedding the XCFramework
and adding the Gradle build phase are manual project edits.

## Alternatives considered

- **Extract to a Swift package instead.** Solves the duplication and the rounding inconsistency
  with a fraction of the effort, and is the right answer if de-duplication were the only goal.
  Rejected because it produces nothing reusable by a second client and none of the toolchain
  knowledge this experiment exists to obtain. Worth revisiting as the fallback if the KMP
  toolchain proves not to be worth carrying.
- **Start with the data layer / Firestore.** Highest theoretical payoff, since it is the
  `Backend` contract every client shares. Rejected: it would require a third-party Firebase KMP
  binding over two native SDKs, so a toolchain failure and a logic failure would be
  indistinguishable.
- **Start with meal-type bucketing.** Rejected as a *first* step only — it needs
  `kotlinx-datetime` and has no Kotlin equivalent of `Calendar`, adding a second unknown. It is
  the intended second step.
- **Replace `ScaledMacros`/`DailyMacros` with the Kotlin type directly.** Rejected: reference
  semantics would leak into SwiftUI view state, and it would couple every consumer to the
  experiment.

## Cross-cutting concerns

- **Data integrity.** The rounding change is the only behavioural effect. It alters values
  written from this point on by at most 1 kcal and does not touch stored documents.
- **Second client.** Once this module exists, an Android client inherits identical macro
  arithmetic by construction rather than by re-reading this document. That is the `Cross-platform`
  payoff and the reason the module is worth more than a Swift package.
- **Build time and CI.** The Gradle build is a new step ahead of every clean Xcode build.
  Measure it; if it is not tolerable on this machine, that is a finding, not a failure to work
  around.
- **App size.** A Kotlin/Native framework carries a runtime. Expect a measurable increase for
  fifty lines of arithmetic — the trade is accepted here because the module is a probe, but it
  is a real argument against KMP for a module this small in isolation.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| iCloud Drive corrupts or stalls the Gradle build | Hours lost to failures that look like KMP bugs | Resolve the repository location before writing any Kotlin (see *Repository location*) |
| Kotlin/Native toolchain setup dominates the effort | The learning value lands on Gradle, not Kotlin | Accept it — toolchain setup *is* the transferable part; the Kotlin here is deliberately trivial |
| Rounding change breaks tests in unexpected places | Silent acceptance of wrong values | Update each failing test to the explicit new expected value; do not loosen assertions |
| Framework weight is judged not worth it | Module gets reverted | The Swift-package fallback above is a clean exit; adapters mean call sites do not change again |

## Outcome

Implemented 2026-08-12. The module builds, all five call sites delegate to it, and the full
Swift test suite is green with no test needing a changed expectation (commit 1 already unified
rounding, so the extraction was behaviour-neutral as intended).

- **Toolchain.** No JDK, Gradle, or Kotlin was present on the machine beforehand; all three were
  installed via Homebrew (`openjdk@21`, `gradle`) with no `sudo` required — the Gradle wrapper
  itself needed bootstrapping once with a plain local Gradle install, because the JetBrains Kotlin
  Multiplatform plugin (2.1.0) does not configure under Gradle 9.x; the wrapper was pinned to
  8.10 instead. The Kotlin/Native compiler downloads its own LLVM/sysroot dependencies on first
  use (`~/.konan`, ~1.3 GB) — a one-time cost, not repeated on subsequent builds.
- **Build time.** Clean build (`clean assembleMacroKitReleaseXCFramework`, toolchain already
  downloaded): ~17s. Incremental rebuild with no source changes: <1s. This runs as an Xcode build
  phase ahead of every app build; on this machine the steady-state cost is negligible, but a clean
  Kotlin/Native build is not.
- **App size.** The XCFramework is 2.4 MB on disk (656 KB per architecture slice) for what is
  currently ~50 lines of arithmetic. Confirms the doc's prediction: the runtime overhead is real
  and only justified by the toolchain/cross-platform payoff, not by this module's own size.
- **Xcode wiring.** `PBXFileSystemSynchronizedRootGroup` does not pick up the XCFramework or the
  Gradle build phase automatically, as expected — both were added by hand-editing
  `project.pbxproj`. One toolchain-specific pitfall not anticipated in the *Risks* table: declaring
  the Gradle script phase's `outputPaths` as the XCFramework path created a build cycle with
  Xcode's own `ProcessXCFramework` step in the new build system. Fixed by matching the existing
  SwiftLint phase's pattern (`alwaysOutOfDate = 1`, no declared outputs) instead of trying to give
  Xcode fine-grained dependency tracking for the script.
- **Verdict.** The toolchain is viable on this machine and the de-duplication is real. Whether it
  is worth carrying long-term depends on whether a second client actually materializes — the
  Swift-package fallback remains available if not.
