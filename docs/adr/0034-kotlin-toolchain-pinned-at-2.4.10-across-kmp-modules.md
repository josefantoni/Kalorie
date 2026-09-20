# 0034. All KMP modules build with one Kotlin version, 2.4.10

- **Status:** Superseded in part by [ADR 0037](0037-shared-modules-target-ios-and-jvm-and-are-consumed-by-composite-build.md)
  (its Scope only — the pin binds every consumer of the modules, an Android client included, not
  only iOS; the Decision and every Consequence below still hold)
- **Scope:** Cross-platform, iOS
- **Date:** 2026-09-19

## Context

MacroKit, MealKit and TextKit were created with Kotlin 2.1.0 (commit `43caf95`, design 0004) and
never moved. Nothing recorded 2.1.0 as a deliberate choice; it was the version current when the
first module was written and every later module copied it.

It became a constraint when [design 0014](../design/0014-data-export.md) tried PdfKmp 1.3.0 in a
fourth module. PdfKmp is compiled with Kotlin 2.4.10, and Kotlin/Native 2.1.0 refuses its klib
(`Incompatible ABI version. The current default is '1.201.0', found '2.4.0'`). Each XCFramework
carries its own Kotlin runtime, so a module could technically use a different version, but every
module is built by the same `scripts/build-kmp-framework.sh` on the same machine, and a mixed
toolchain means several Kotlin/Native distributions in `~/.konan` and no single answer to "which
Kotlin does this project use".

## Decision

Every KMP module pins the same Kotlin Multiplatform plugin version, currently **2.4.10**. Bumping
it means bumping all modules in one change.

Gradle stays on the wrapper's 8.10. Kotlin 2.4.10 configures and builds under it, though Gradle
prints a deprecation warning for that version; moving to Gradle 9.x is a separate change.

## Consequences

- Libraries compiled with a newer Kotlin than 2.1.0 can now be consumed. PdfKmp resolved and
  compiled for both iOS targets on 2.4.10.
- A first build on a clean machine downloads the 2.4.10 Kotlin/Native bundle and LLVM into
  `~/.konan`; the 2.1.0 bundle is no longer used and can be deleted.
- A future module must copy the pinned version from the existing ones, not pick its own.
- The Gradle 8.10 deprecation warning is known and left as is until the wrapper is upgraded.
