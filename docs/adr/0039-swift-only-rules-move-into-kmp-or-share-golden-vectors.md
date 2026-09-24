# 0039. Swift-only rules either move into KMP or are pinned by shared golden-vector fixtures

- **Status:** Accepted
- **Scope:** Cross-platform, Backend
- **Date:** 2026-09-24

## Context

[ADR 0038](0038-android-client-mirrors-the-ios-architecture-natively.md) item 2 puts pure,
deterministic logic with no I/O into KMP, but left open *which* of the rules that still live only
in Swift qualify. ARCHITECTURE states six of them as a contract a second client must match:

1. scaling a food to a gram amount — `FoodItemDomain.scaled(toGrams:)` (§ 4.2);
2. meal resolution with a pin — `[MealTypeDomain].resolvedMealTypeId(for:)` (§ 3.2);
3. food validation — `FoodItemValidation` (§ 2.6), whose id rule is also `validItemId()` in
   `backend/firestore.rules` (§ 1.6);
4. the OpenFoodFacts → `FoodItemDomain` mapping — `OpenFoodFactsProductDTO.asDomain()` (§ 2.4);
5. the search merge order and query derivation — `SearchFoodItemsUseCase` (§ 2.2);
6. export day bucketing — `FoodExportReportFactory` (§ 8).

Nothing verified a second implementation against any of them. The only shared test vector was
`TextKit/fixtures/text-kit-cases.json` ([ADR 0026](0026-js-backfill-duplicates-textkit-under-a-shared-fixture.md)).
On 2026-09-24 only rule 2 existed twice: the Android Dashboard port carries its own copy in
`MealTypeModel.kt`, untested on the Android side.

Two facts changed what is possible. ADR 0026 hand-copied the fixture into Kotlin tests because
TextKit had no JVM test target; [ADR 0037](0037-shared-modules-target-ios-and-jvm-and-are-consumed-by-composite-build.md)
has since given every module `jvm()`, so a `jvmTest` can read a repo file. And Android JUnit tests
can read repo files the same way, so a checked-in JSON fixture can be read, not copied, by every
test suite: XCTest, JUnit, `node:test` for the rules.

Moving a rule into KMP removes the second implementation. A fixture only detects when two
implementations drift apart. Moving is therefore preferred, but only where it is cheap.
ADR 0038 keeps domain types, DTOs and decoding on the platforms, and a rule whose input is a
domain type or a decoded DTO would have to drag those along. A rule needing calendar arithmetic
would add `kotlinx-datetime`, which ARCHITECTURE § 8.4 already declined for ExportKit.

## Decision

1. **A rule moves into KMP when its inputs and outputs are primitives, it needs no new
   dependency, and it does not need a domain type or DTO.** Everything else stays per platform and
   gets a golden-vector fixture once it exists on both.
2. **Meal resolution moves into MealKit**, over minutes rather than dates: a window list of
   `(id, startMinutes, endMinutes)`, the entry's minutes since midnight and an optional pinned id
   in, an optional id out. The tie-break is part of the function: windows are ordered by
   `startMinutes`. The existing `mealType(at:)` and `resolvedMealTypeId(for:)` on both clients
   become thin adapters that convert `Date`/`Instant` to minutes and call it. Covered by MealKit's
   `commonTest`; no fixture, since there is one implementation.
3. **Search query derivation moves into TextKit**: from the raw query, the lowercased query, the
   lowercased-and-folded query, and the last space-separated non-empty word of the folded query,
   falling back to the whole folded query when there is none. This is the input to the six
   Firestore queries in `SearchFoodItemsUseCase`. The merge itself (concatenate in the fixed field
   order, first occurrence of an id wins) stays in each client's use case and is covered there by
   one test per platform. It is a single line of code, and a fixture would be heavier than it.
4. **Food validation gets a fixture read by three suites**: iOS `FoodItemValidationTests`, the
   Android port's tests, and `firestore-rules-tests`. The fixture has two parts. `itemId` cases
   are valid/invalid item ids, shared by all three: the rules implement only the id check. `foodItem`
   cases are whole items with the expected first validation error, used by the two clients only.
5. **The OpenFoodFacts mapping gets a fixture**: a raw OpenFoodFacts `product` JSON object in, the
   expected mapped fields out (or `null` for *not found*). It includes decoding, because decoding
   is where two clients are most likely to diverge.
6. **Scaling and export day bucketing get fixtures only when the screen that needs them is
   ported** (FoodQuantity, Export). The arithmetic core of scaling is already in MacroKit; bucketing
   needs a calendar, so its fixture fixes the time zone to `Europe/Prague` and includes both DST
   transition days.
7. **App-level fixtures live in a top-level `fixtures/` directory**, one JSON file per rule. A KMP
   module's own fixture stays in `<Module>/fixtures/`. **iOS is the reference implementation**:
   expected values are what the current iOS code produces. When an iOS test fails against a
   fixture, either the fixture is wrong, or the change is deliberate. In that case the fixture
   changes first, and every suite that reads it follows.
8. **Every fixture is read at test time, never copied into a test by hand.** This supersedes ADR
   0026's Kotlin half. TextKit's fixture-backed tests move from `commonTest`, where
   they held hand-copied literals, to `jvmTest`, which reads `text-kit-cases.json`. The JS half of
   ADR 0026 is unchanged.

## Consequences

- One copy of meal resolution and query derivation, where there were two (or were about to be).
  Both clients keep their existing call sites through the adapters, so no screen changes.
- A fixture change touches up to three test suites in three languages, and CI must run all of them
  when `fixtures/` changes. The `ios`, `android` and `rules` path filters in
  `.github/workflows/ci.yml` each include `fixtures/**`. This is a deliberate exception to the
  rule that one platform's change does not build the other.
- TextKit's fixture cases no longer run on the iOS Native test targets, only on JVM. The code under
  test is the same `commonMain`, and CI runs only `jvmTest`, so no coverage that ran before is lost.
- Adding a rule to the list above follows item 1: move it if it qualifies, otherwise add a fixture
  when the second implementation lands.
- Fixtures pin *current* iOS behaviour, including behaviour that may be unintended. Where a case
  looks wrong, the fixture records it as it is and `TODO.md` gets a finding; the fixture is not
  quietly "corrected".
