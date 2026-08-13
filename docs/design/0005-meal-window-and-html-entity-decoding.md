# Design: Meal-window arithmetic and HTML entity decoding (Kotlin Multiplatform)

- **Status:** Implemented
- **Scope:** Cross-platform, iOS
- **Date:** 2026-08-13

## Context and scope

[0004](0004-shared-macro-calculation-module.md) extracted macro scaling/summation into a Kotlin
Multiplatform module (`MacroKit`) and named meal-type bucketing as the intended next step, on the
assumption that it would need `kotlinx-datetime` since Kotlin has no equivalent of `Calendar`.

Re-reading the actual call sites shows the assumption was broader than the real duplication.
Nothing here needs a `Date` or a calendar component on the Kotlin side — the shared part is pure
`Int` arithmetic on **minutes since midnight**, already extracted from `Date` by `Calendar` on the
Swift side before any shared code runs:

| Site | What it does |
|---|---|
| `DashboardViewModel.swift:213-221` (`foodFallsIn`) | Converts a food's `Date` and a meal type's start/end to minutes, checks range membership |
| `CreateMealTypeUseCase.swift:45-57` | Minimum-duration guard (`endTime - startTime >= 30 min`), pairwise overlap check against existing meal types, minutes conversion for the persisted DTO |
| `UpdateMealTypeTimesUseCase.swift:33-35` | Same minutes conversion for the persisted DTO |
| `SetupDefaultMealsUseCase.swift:46-49` | Same minutes conversion for the persisted DTO |

Separately, and unrelated to time: `String+Extension.swift:10-22` (`decodingHTMLEntities`) is a
small, pure, dependency-free string transform used when parsing OpenFoodFacts responses
(`FetchFoodByBarcodeExternallyUseCase.swift:54,61`, `SearchFoodExternallyUseCase.swift:46,53`). It
was flagged alongside the meal-window candidate as the next-best fit by the same criteria: no
platform coupling, real call sites, real value to a second client hitting the same API.

**Boundary of this document:** minutes-since-midnight arithmetic for meal windows, and HTML
entity decoding. Nothing else moves to KMP, and `Calendar`/`Date` handling stays in Swift.

## Goals

- One Kotlin implementation of the minutes-since-midnight arithmetic (conversion, range check,
  overlap check, minimum-duration check), used by all four call sites above.
- One Kotlin implementation of HTML entity decoding, used by both external-fetch use cases.
- No `Date`, `Calendar`, or `kotlinx-datetime` dependency introduced — the shared functions take
  and return primitives only.
- The existing Swift test suite (`CreateMealTypeUseCaseTests`, `SetupDefaultMealsUseCaseTests`,
  `DashboardViewModelTests`) passes unchanged; this step is behaviour-neutral.

## Non-goals

- **`kotlinx-datetime` / a Kotlin `Calendar` equivalent.** Not needed — see *Context*. Extracting
  hour/minute from a `Date` remains Swift's job; only the resulting integers cross the boundary.
- **Growing `MacroKit` beyond macros.** An earlier draft of this document added both new files
  to the existing `MacroKit` module to avoid duplicating Gradle/Xcode wiring. On review, that
  makes the module's name describe less and less of its contents — see *Alternatives* for why a
  second small module was accepted as the correct cost instead.
- **Changing the Firestore schema.** `MealTypeDTO.startMinutes`/`endMinutes` are already `Int`;
  this step only changes what computes the values, not their shape.

## Design

### Module shape

Two new, independent Gradle/KMP modules, each mirroring `MacroKit`'s own structure exactly (own
`gradlew`, `settings.gradle.kts`, `build.gradle.kts` producing one `XCFramework`) rather than
folding into it:

- **`MealKit`** — meal-window arithmetic
- **`TextKit`** — HTML entity decoding

`MacroKit` itself is untouched by this document: it keeps exactly the macro arithmetic from 0004.
Each module stays a one-sentence answer to "what is this for," which is what lets a future
Android client (or a future Claude session) depend on `TextKit` without pulling in meal-window
logic it has no use for, and vice versa. The cost is real and accepted: three `gradlew`
invocations, three XCFrameworks, three Xcode build phases where 0004 had one — see *Alternatives*.

```kotlin
// MealKit/src/commonMain/kotlin/MealWindows.kt
fun minutesSinceMidnight(hour: Int, minute: Int): Int = hour * 60 + minute

fun isMinuteWithinWindow(minutes: Int, startMinutes: Int, endMinutes: Int): Boolean =
    minutes >= startMinutes && minutes < endMinutes

fun mealWindowsOverlap(startMinutes: Int, endMinutes: Int, otherStartMinutes: Int, otherEndMinutes: Int): Boolean =
    startMinutes < otherEndMinutes && endMinutes > otherStartMinutes

fun isMealWindowLongEnough(startMinutes: Int, endMinutes: Int, minimumDurationMinutes: Int): Boolean =
    endMinutes - startMinutes >= minimumDurationMinutes
```

```kotlin
// TextKit/src/commonMain/kotlin/HtmlEntities.kt
private val htmlEntities = listOf(
    "&amp;" to "&", "&quot;" to "\"", "&lt;" to "<",
    "&gt;" to ">", "&apos;" to "'", "&#39;" to "'", "&nbsp;" to " ",
)

fun decodeHtmlEntities(input: String): String =
    htmlEntities.fold(input) { acc, (entity, replacement) -> acc.replace(entity, replacement) }
```

Both follow 0004's pattern: top-level functions on primitives, no third-party dependencies, one
concern per file — now also one concern per module. `minimumDurationMinutes` is passed explicitly
by the caller (`30`) rather than given a Kotlin default value, to avoid relying on how
Kotlin/Native exports default arguments to Objective-C — consistent with 0004's *Interop* guidance
to keep the crossing boundary unsurprising.

### Swift integration

- **`Date+Extension.swift`** imports `MealKit` (not `MacroKit`) and gains one computed property
  that is the single place `Calendar` meets `MealWindowsKt`:

  ```swift
  var minutesSinceMidnight: Int32 {
      let components = Calendar.current.dateComponents([.hour, .minute], from: self)
      return MealWindowsKt.minutesSinceMidnight(hour: Int32(components.hour ?? 0), minute: Int32(components.minute ?? 0))
  }
  ```

  `CreateMealTypeUseCase.swift` and `DashboardViewModel.swift` import `MealKit` directly for the
  overlap/duration/range checks. `UpdateMealTypeTimesUseCase.swift` and
  `SetupDefaultMealsUseCase.swift` only call the `Date` extension property — a `Int32`, a built-in
  type — so neither needs to import `MealKit` at all; a stray `import MacroKit` inherited from an
  earlier draft of this change was removed from both as dead weight.
- **`String+Extension.swift`** imports `TextKit` (not `MacroKit`); `decodingHTMLEntities()` keeps
  its signature and delegates its body to `HtmlEntitiesKt.decodeHtmlEntities(input:)`. Both
  external-fetch use cases are untouched — they call the extension, not the Kotlin function.
- `DashboardViewModel.swift` imports both `MacroKit` (for `Macros`/`DailyMacros`, from 0004) and
  `MealKit` (for `foodFallsIn`) — the one file in the app that legitimately depends on two of the
  three modules.
- As in 0004, `Int` crosses as `Int32`; no `data class` or collection type is involved here, so
  none of 0004's reference-semantics concerns apply.

## Alternatives considered

- **Bring `kotlinx-datetime` in now, to do the `Calendar`-equivalent work in Kotlin too.**
  Rejected: no call site needs it once the boundary is drawn at "minutes," and 0004 already flagged
  it as a second unknown not worth taking on together with a first real usage of KMP beyond macros.
- **Add both files to the existing `MacroKit` module instead of new modules.** This was the
  original shape of this document, on the reasoning that nothing here needs isolation from
  `Macros.kt` (no conflicting dependencies, no separate release cadence) and a shared module avoids
  duplicating Gradle/Xcode wiring. Rejected on review: a module's name is a promise about what
  depending on it pulls in, and `MacroKit` already meant "macro arithmetic" to every existing call
  site (`FoodConsumedModel`, `DashboardViewModel`'s `DailyMacros`). Growing it to also mean
  "meal-window arithmetic and HTML decoding" breaks that promise for no reader's benefit — a
  hypothetical Android client wanting only text decoding would still link macro arithmetic it
  never calls. Two small modules cost more wiring today and pay it back the first time anything
  needs one concern without the others.
- **Leave `decodingHTMLEntities` in Swift since it's topically unrelated to meal arithmetic.**
  Rejected: this project's KMP boundary (established in 0004) is "pure, dependency-free logic a
  second client would need identically," not topic grouping. It qualifies on that basis regardless
  of which module it ends up in.

## Cross-cutting concerns

- **Data integrity.** No behaviour change: `minutesSinceMidnight` computes the same value the
  inline `Calendar` code did, and `decodeHtmlEntities` applies the same replacement list in the
  same order. Nothing written to Firestore changes shape or value.
- **Second client.** An Android client parsing the same OpenFoodFacts responses or evaluating the
  same meal-window rules inherits identical behaviour by construction.
- **App size / build time.** No new third-party dependency, but two new Gradle modules means two
  more `gradlew` invocations and two more embedded XCFrameworks in every clean build — see
  *Outcome* for the measured cost.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Rewriting `foodFallsIn` / `CreateMealTypeUseCase` changes behaviour at a boundary condition (e.g. exact start/end minute) | Silent regression in meal grouping or validation | `DashboardViewModelTests` already asserts exact-start-included / exact-end-excluded; `CreateMealTypeUseCaseTests` already asserts overlap and duration edge cases — both must stay green unmodified |
| `Int32` conversion at the Swift boundary introduces an off-by-cast bug | Wrong minute values with no compiler error (both are integers) | Route every call through `Date.minutesSinceMidnight` rather than converting inline at each of the four sites |

## Outcome

Implemented 2026-08-13. `MealKit` and `TextKit` were added as two new, independent KMP modules
mirroring `MacroKit`'s structure; `MacroKit` itself is unchanged from 0004 (still `Macros.kt` /
`MacrosTest.kt` only).

- **Call sites.** All four meal-window call sites (`DashboardViewModel.foodFallsIn`,
  `CreateMealTypeUseCase`, `UpdateMealTypeTimesUseCase`, `SetupDefaultMealsUseCase`) and both
  HTML-decoding call sites now delegate to Kotlin. `Date+Extension.swift`'s new
  `minutesSinceMidnight: Int32` property is the single place `Calendar` output is handed to
  `MealWindowsKt`; no call site extracts `hour`/`minute` components itself anymore.
- **Imports match dependencies exactly.** `Date+Extension.swift` and `CreateMealTypeUseCase.swift`
  import `MealKit`; `String+Extension.swift` imports `TextKit`; `DashboardViewModel.swift` imports
  both `MacroKit` and `MealKit`, the only file with a legitimate two-module dependency.
  `UpdateMealTypeTimesUseCase.swift` and `SetupDefaultMealsUseCase.swift` need neither import —
  they only touch `Date.minutesSinceMidnight`, which returns a plain `Int32`.
- **Behaviour.** No test needed a changed expectation. The full Swift suite (156 tests) passed
  unmodified against the refactored code (`xcodebuild test`, iPhone 17 simulator, iOS 26.3.1),
  including `DashboardViewModelTests` (exact-start included, exact-end excluded, overlap ordering)
  and `CreateMealTypeUseCaseTests` (duration, overlap, wrapping-slot cases). New Kotlin tests
  (`MealWindowsTest`, `HtmlEntitiesTest`) cover the same boundary conditions at the source of truth.
- **`kotlinx-datetime` was confirmed unnecessary**, contra 0004's assumption: every shared function
  takes and returns `Int`, and the only `Calendar` call left in the codebase is the one producing
  `minutesSinceMidnight`'s input, which stays in Swift.
- **Build.** Three independent clean builds: `MacroKit` ~11s, `MealKit` ~9s, `TextKit` ~9s
  (`gradlew clean assemble<Name>ReleaseXCFramework` each). Xcode runs the three "Build … (Gradle)"
  script phases sequentially ahead of `Sources`, so a clean app build now pays roughly ~29s of
  Gradle time versus 0004's ~17s for `MacroKit` alone — the accepted cost of *Alternatives*'
  module-per-concern decision. XCFramework sizes: `MacroKit` 2.4 MB, `MealKit` 2.3 MB, `TextKit`
  2.5 MB — each pays the Kotlin/Native runtime independently rather than sharing it, which is the
  concrete, measurable version of the tradeoff described in *Alternatives*.
- **Naming debt from the original single-module draft is resolved**, not just flagged: each
  module's name is exactly what it contains. The next KMP candidate gets the same choice — module
  per concern — rather than inheriting a decision to keep sharing `MacroKit`.
