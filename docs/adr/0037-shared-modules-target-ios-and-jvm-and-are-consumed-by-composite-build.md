# 0037. Shared KMP modules target iOS and JVM and are consumed by composite build

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-09-20

## Context

[Design 0014](../design/0014-data-export.md) states that an Android client "produces identical
files without re-implementing any of it", and MacroKit, MealKit and TextKit exist so that a second
client shares macro totalling, meal-window arithmetic and name folding rather than re-deriving
them. Neither claim was true of the build as it stood:

- All four modules (MacroKit, MealKit, TextKit, ExportKit) declared only `iosArm64()` and
  `iosSimulatorArm64()`, so an Android client could not consume any of them.
- Each is a separate Gradle build with its own `settings.gradle.kts`, no `group`, no `version`, no
  `maven-publish`. There was no way to depend on one from another project, and no recorded choice
  of how one should.
- [ADR 0034](0034-kotlin-toolchain-pinned-at-2.4.10-across-kmp-modules.md) pins Kotlin 2.4.10 across
  these modules and was scoped `iOS`, though the pin constrains every consumer of the artefacts.
- Every Kotlin source file was in the default package. Kotlin and Java cannot import a
  default-package declaration from any named package, and every Android app has one, so no Android
  code could have referenced a single function of any module. iOS never showed it: the Objective-C
  names come from the framework name, not the package.
- No module set `jvmTarget` or a toolchain, so JVM bytecode came out as whatever JDK built it —
  major version 65 (Java 21), which Android's D8/R8 rejects. A composite build compiles the modules
  with the consumer's JDK, so the result would also have depended on the machine.

All of the modules' logic is in `commonMain`. The only platform code is ExportKit's 18-line
`iosMain` `NSData` bridge, which nothing in `commonMain` references. ExportKit's one dependency,
PdfKmp, is the exception to "nothing differs on Android": its `jvm` variant renders through
`java.awt.Graphics`, `java.awt.image.BufferedImage` and `javax.imageio.ImageIO` (`JvmPdfDriver`,
`JvmPdfCanvas`), none of which exist on Android, so an Android consumer resolving ExportKit's `jvm`
variant would hit missing classes at build time or a `NoClassDefFoundError` at run time. PdfKmp
publishes a separate `androidJvm` variant (`pdfkmp-android`) for that reason.

## Decision

1. **Every module declares `jvm()` alongside its two iOS targets.** An Android client consumes the
   JVM variant of MacroKit, MealKit and TextKit. **ExportKit additionally declares an Android
   target**, through `kotlin { android { ... } }` from the `com.android.kotlin.multiplatform.library`
   plugin (the KMP plugin of AGP 9, not `androidTarget()` with `com.android.library`), because its
   PdfKmp dependency differs between the JVM and Android. The other three declare no Android target:
   their `commonMain` is plain Kotlin and it would add the Android Gradle Plugin and SDK as build
   requirements for nothing. A new module declares the two iOS targets and `jvm()`, plus an Android
   target only when a dependency publishes a distinct Android variant. Platform-specific code goes
   in the matching `<target>Main` source set, as ExportKit's `iosMain` does.
2. **Every `jvm()` target, and ExportKit's Android target, set `jvmTarget` to `JVM_17`** through
   `compilerOptions`, not `jvmToolchain`. A toolchain would require a JDK 17 on every machine and in
   CI, where only JDK 21 is installed; `jvmTarget` only sets the bytecode level.
3. **ExportKit's build carries what PdfKmp's Android variant demands**: `google()` in `repositories`
   (its AndroidX dependencies are not on Maven Central), `android.useAndroidX=true`, and `compileSdk`
   37 (`release(37) { minorApiLevel = 0 }`, the `android-37.0` platform). ExportKit alone is on AGP
   9.4.1 and Gradle 9.7.1; the other three stay on Gradle 8.10, each module being its own build.
   AGP 8.13, the last 8.x, lists 36 as its maximum `compileSdk` and could reach 37 only with
   `android.suppressUnsupportedCompileSdk`.
4. **The consuming Android app must compile against Android API 37 or newer, with AGP 9.x.**
   PdfKmp 1.3.0's AAR metadata refuses a lower `compileSdk`, so this constrains the app, not only
   ExportKit.
5. **Every Kotlin source file, main and test, declares `package antoni.kalorie.<module>`**, with the
   module name in lowercase: `antoni.kalorie.macrokit`, `.mealkit`, `.textkit`, `.exportkit`. The
   Swift names are unchanged (`ExportKitExportDayInput` and the like stay as they were). A new module
   follows the same pattern.
6. **Modules are consumed by composite build, not published.** A consumer includes the module and
   substitutes it explicitly, because the modules have no `group`:

   ```kotlin
   includeBuild("<path to>/Kalorie/MealKit") {
       dependencySubstitution {
           substitute(module("kalorie:MealKit")).using(project(":"))
       }
   }
   // dependencies { implementation("kalorie:MealKit") }
   ```

   No `maven-publish`, `group` or `version` is added. `scripts/build-kmp-framework.sh` and the
   XCFramework build are unchanged.
7. **A consumer's Kotlin toolchain must be able to read what Kotlin 2.4.10 produced.** ADR 0034's
   pin is therefore a constraint on Android as well as on iOS, and is not iOS precedent. It bites in
   practice: the Kotlin built into AGP 9.4.1 is 2.2.0, which fails with `Module was compiled with an
   incompatible version of Kotlin ... expected version is 2.2.0`. The app's root `build.gradle.kts`
   must pull the newer one in:

   ```kotlin
   buildscript {
       repositories { google(); mavenCentral() }
       dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10") }
   }
   ```

   The consuming build also needs a larger Gradle daemon than the default, for example
   `org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g`; with four included builds plus AGP and KGP it
   ran out of Metaspace.

## Consequences

- Verified on 2026-09-20: `./gradlew jvmTest` passes in all four modules; JVM bytecode is major
  version 61; ExportKit's `bundleAndroidMainAar` produces `ExportKit.aar`;
  `assembleExportKitReleaseXCFramework` still succeeds with `ANDROID_HOME` pointing at a nonexistent
  directory, so the Xcode build phase does not need an Android SDK; `xcodebuild build` of the app is
  unaffected; and a scratch `kotlin("jvm")` project on Kotlin 2.4.10 compiled against MealKit
  through the `includeBuild` above (`kalorie:MealKit -> project :MealKit`).
- Verified the same day with a scratch Android app (AGP 9.4.1, `compileSdk` 37, `minSdk` 26, outside
  the repository) that depends on all four modules through the `includeBuild` above: `assembleDebug`
  and `assembleRelease` with R8 both pass. The APK contains no `java/awt` reference and PdfKmp's
  Android PDF driver (`android/graphics/pdf`), so the Android variant was the one selected. The iOS
  app builds and its unit tests pass without a Swift change.
- **Not verified:** PDF rendering on a device or emulator (no AVD was available), and any consumer
  Kotlin version other than 2.4.10. An older Kotlin cannot read the modules; that is now observed,
  not open, and is why item 7 exists.
- CI runs `jvmTest` for all four modules but does not build ExportKit's Android target: whether the
  runner's SDK carries API 37 was not checked.
- ExportKit's iOS build now applies AGP. Nothing failed without an SDK on 2026-09-20, but that was
  observed, not guaranteed by AGP.
- `commonMain` code is compiled for and tested on the JVM as well as Kotlin/Native, so behaviour that
  differs between the two (string case mapping, number formatting) surfaces in `commonTest` instead
  of in a second client. `jvmTest` is the fast way to run them.
- The Android app lives in this repository, beside `iOS/` (for example `Android/`), so the
  `includeBuild` paths are relative to the repository root and CI is shared. If the app ever moves
  to its own repository, publishing to a Maven repository is the alternative; it needs a repository,
  a `group` and a versioning policy, none of which exist. This record does not choose them.
- ExportKit's `minSdk` is 26. That is a choice, not a floor derived from a dependency, and an app's
  `minSdk` must be at least ExportKit's, so lower it only after checking PdfKmp's own minimum.
- Supersedes ADR 0034 in part: its Scope. The 2.4.10 pin itself is unchanged.
