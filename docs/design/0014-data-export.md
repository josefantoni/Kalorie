# Design: Exporting consumed food to PDF or Excel

- **Status:** Implemented
- **Scope:** Cross-platform, iOS
- **Date:** 2026-09-19

## Context and scope

A user can see their logged food one day at a time on the Dashboard, and nowhere else. There is
no way to take the data out of the app, e.g. to hand it to a nutritionist or a doctor. `TODO.md`
tracks this as *Data export*.

Relevant existing pieces:

- `foodConsumed` entries store absolute, already-scaled values (`ARCHITECTURE.md` § 1.4), so an
  export only sums them. It never scales and never rounds.
- `FetchFoodsConsumedForMonthUseCase` shows the query shape: a numeric range over `date`
  (epoch seconds, [ADR 0008](../adr/0008-dates-as-epoch-seconds-not-firestore-timestamp.md)).
- Meal assignment is `[MealTypeDomain].resolvedMealTypeId(for:)` in Swift
  (`Core/Models/MealTypeModel.swift`): a pin if one resolves
  ([ADR 0022](../adr/0022-meal-assignment-may-be-pinned-by-the-user.md)), otherwise the window
  containing the entry's time of day ([ADR 0014](../adr/0014-meal-assignment-by-time-of-day-only.md)).
- `DashboardViewModel.monthCache` is sized for one visible dashboard and never evicts
  ([ADR 0015](../adr/0015-dashboard-caches-a-month-and-derives-the-day.md)). The export does not
  use it.
- There are three KMP modules (`MacroKit`, `MealKit`, `TextKit`). Each is a separate Gradle
  build (Kotlin 2.1.0) that produces its own XCFramework. All have only `commonMain`, no
  platform-specific source set, and no third-party dependency
  ([design 0004](0004-shared-macro-calculation-module.md)).

**Boundary of this document:** generating a report file on the device from the user's own
`foodConsumed` entries and handing it to the system share sheet. Nothing is generated or stored
server-side.

## Goals

- The user picks a date interval (from, to) and a format (PDF or Excel `.xlsx`), and gets a
  file in the system share sheet.
- Both formats show the same hierarchy: **day → meal (with its time window) → foods**. Each food
  shows its amount, kcal and every stored nutrient. Each meal gets a subtotal and each day gets
  a total of all its meals.
- Every day in the interval appears, including a day with no entries.
- The report's structure, its sums and both file formats come from shared Kotlin code, so an
  Android client produces identical files without re-implementing any of it.

## Non-goals

- **An average, or a total over the whole interval.** The report ends with the last day's total.
- **Historical meal windows.** Meal types are not versioned. Every day is grouped by the
  user's *current* meal types, exactly as the Dashboard does for past days.
- **Charts or any graphics** in either format.
- **CSV.** The user explicitly wants `.xlsx`.
- **Server-side generation**, scheduled exports, or e-mailing a file. The share sheet covers
  delivery.
- **Exporting anything other than `foodConsumed`.** Favourites, created meals and portions are
  out of scope.
- **An upper limit on the interval.** Two years is about 11,000 entries, a few MB in memory.
  The cost is Firestore reads (one per entry, per export), which is accepted.

## Design

### Report content (Cross-platform)

This section is the contract an Android client must match.

Columns, in this order:

| Column | Source | Single-food value when unknown |
|---|---|---|
| Food | `BilingualNamed.displayName` | — |
| Amount | `weight` + `g` / `ml` from `measure` ([design 0011](0011-food-measure-grams-or-millilitres.md)) | — |
| kcal | `calories` | — |
| kJ | `energyKJ` (already derived when absent, [ADR 0007](../adr/0007-derive-missing-energy-kj-from-macros.md)) | — |
| Protein | `protein` | — |
| Carbohydrate | `carbohydrate` | — |
| of which sugars | `carbohydrateSugar` | — |
| Fat | `fat` | — |
| of which saturated | `fatSaturated` | `–` |
| of which unsaturated | `fatUnsaturated` | — |
| Fibre | `fiber` | `–` |
| Salt | `salt` | — |

Rules:

1. Days run chronologically from `from` to `to`, both inclusive. A day belongs to the device's
   current calendar and time zone, the same way the Dashboard buckets days.
2. Within a day, meal sections follow `startTime` order, the same order as
   `DashboardViewModel.groupedFoods`. A meal type with no entries that day is omitted. Entries
   that resolve to no meal type go into a trailing **Unassigned** section, labelled like the
   Dashboard's (`L10n.Dashboard.sectionUnassignedFoods`).
3. A meal section's header is its name and its window, e.g. `Breakfast 07:00–10:00`.
4. Within a section, foods are ordered by `date` ascending.
5. After each section comes a **subtotal** row. After the last section of a day comes a **day
   total** row, the sum over all the day's foods.
6. A day with no entries appears as its day header followed by a single "No entries" row. It
   has no sections and no total.
7. A single food's unknown `fatSaturated` / `fiber` is shown as `–`. Subtotals and totals add
   unknowns as `0`. Both follow [ADR 0032](../adr/0032-unknown-optional-nutrient-shown-as-dash-not-zero.md),
   whose Decision separates exactly these two cases.
8. Mixed measures are not a problem for the sums. The amount column is never summed, and
   nutrient values are always grams of nutrient.
9. Every label and number format follows the device language
   ([ADR 0019](../adr/0019-l10n-enum-over-the-string-catalogue.md)).
10. **An interval with no entries at all still produces a file**, one "No entries" row per day,
    consistent with rule 6. The user is not stopped by an alert.

### Division of work between Swift and Kotlin

```
MealTypeSheetView ─▶ ExportView ─▶ ExportViewModel ─▶ GenerateFoodExportUseCase
                                                        │  1. FetchFoodsConsumedInRangeUseCase
                                                        │  2. day bucketing + meal resolution (Swift)
                                                        │  3. ExportKit: build report → render xlsx / pdf
                                                        ▼
                                                   temp file URL ─▶ share sheet
```

**Swift keeps** everything that depends on `Calendar`, `Locale` or existing Swift-only logic:

- Bucketing entries into days (`Calendar.current`). This keeps `kotlinx-datetime` out of the
  module for the same reason design 0004 kept it out of MacroKit.
- Resolving each entry's meal type through the existing `resolvedMealTypeId(for:)`, so the
  export and the Dashboard cannot disagree about which meal an entry is in.
- Producing every display string: the day headers (localized date), the section headers
  (name + window), column headers, "No entries", "Unassigned", subtotal/total labels, the unit
  suffix, and the decimal separator.

**ExportKit (Kotlin) owns**:

- Building the report tree from flat input: rules 1–8 above (ordering, omission of empty
  sections, subtotals, day totals, the dash-versus-zero rule).
- Rendering that tree to `.xlsx` bytes and to PDF bytes.

The input crosses the language boundary as flat primitives and lists, following design 0004's
*Interop* guidance: a list of days (index + label), a list of meal sections (id, header label,
sort key) and a list of entries (day index, section id or null, food name, amount label, kcal
as `Int`, nutrients as `Double`, the two optional ones as nullable), plus a labels object.

### ExportKit module

A fourth KMP module, next to the other three, with the same Gradle shape (`iosArm64`,
`iosSimulatorArm64`, XCFramework `ExportKit`, bundle id `antoni.Kalorie.ExportKit`).

- **No dependency on MacroKit or MealKit.** Each XCFramework carries its own Kotlin runtime and
  its own copy of every type, so a MacroKit `Macros` passed through ExportKit would be a
  different Swift type. The only arithmetic ExportKit needs is adding already-absolute values,
  with no scaling and no rounding, so there is nothing to diverge from.
- **First `iosMain` source set in the project.** Returning a `ByteArray` makes Swift receive a
  `KotlinByteArray`, which can only be copied one element at a time across the bridge, far too
  slow for a multi-MB file. `iosMain` exposes the result as `NSData` instead (`usePinned` +
  `NSData.create(bytes:length:)`). An Android client uses the `commonMain` `ByteArray` directly.
- **No third-party dependency.** PdfKmp was dropped after the spike (see the Update in Outcome).
- Swift wraps ExportKit behind an adapter, as design 0004 did for `Macros`, so no Kotlin type
  leaks into view models.

### xlsx rendering (hand-written, commonMain)

No KMP xlsx library exists. All Kotlin xlsx libraries wrap Apache POI, which is JVM-only.
ExportKit writes a minimal Office Open XML workbook itself:

- Parts: `[Content_Types].xml`, `_rels/.rels`, `xl/workbook.xml`, `xl/_rels/workbook.xml.rels`,
  `xl/styles.xml`, `xl/worksheets/sheet1.xml`. One sheet.
- Strings are inline (`t="inlineStr"`), so there is no shared-strings table. Every text value is
  XML-escaped (`& < > " '`), because food names are user and OpenFoodFacts data.
- Numbers are numeric cells, not text, so they can be summed and filtered in Excel. Excel
  applies the viewer's locale for the decimal separator. `styles.xml` defines a bold style for
  day headers, section headers and total rows, and number formats `0` (kcal, kJ) and `0.0`
  (nutrients).
- A single food's unknown value is the text cell `–`, and totals use rule 7.
- The ZIP container uses **stored** entries (no compression), a table-based CRC32, local file
  headers, a central directory and an end-of-central-directory record. ZIP64 is not needed: the
  file stays far below 4 GB. Compression is skipped deliberately. The file is small, and
  `commonMain` has no deflate implementation.

### PDF rendering (PdfKmp)

[PdfKmp](https://github.com/conamobiledev/PdfKmp) (Apache 2.0, version 1.3.0 at the time of
writing). The document model and layout live in `commonMain`. Rendering goes through each
platform's native PDF stack (Core Graphics on iOS, `android.graphics.pdf.PdfDocument` on
Android), so text including Czech diacritics renders with a real font (bundled Inter).

- A4 **landscape**, because twelve columns do not fit portrait.
- One table per page flow with the **column header repeated on every page**. PdfKmp supports
  table row slicing with repeating headers.
- Day header, section header, subtotal and day total rows are bold. Days and sections are not
  forced onto a new page.
- A title line: the report name and the interval.

**Gated by a spike (step 0 below).** PdfKmp is young and small, and its behaviour inside this
project's build is unverified.

### Data access

`FetchFoodsConsumedInRangeUseCase(from: Date, to: Date)` runs one query over
`[startOfDay(from), startOfDay(to) + 1 day)` through the existing range query
`loadAsync(from:where:isGreaterThanOrEqualTo:isLessThan:)` and maps DTOs with `asDomain()`. It
returns the full result of that one query. There is no paging. The volume above does not need a
cursor. No new provider method is needed.

`mealTypes` are not re-fetched. The sheet already holds the user's current meal types, and they
are passed into the export feature's configurator.

### iOS feature

- **Entry point:** a toolbar button in `MealTypeSheetView`, the sheet opened from the
  Dashboard's trailing toolbar icon. It pushes `ExportView` inside the sheet's existing
  `NavigationStack` (`isExportPushed` on `MealTypeSheetViewModel`, the Router pattern).
- **`ExportView`:** two `DatePicker`s (`from`, `to`), a segmented format picker (PDF / Excel)
  and an *Export* button.
  - Defaults: `from` = first day of the current month, `to` = today.
  - The button is disabled while `from > to`.
  - While generating, the screen shows the shared `View.loader(_:)`.
- **Delivery:** the bytes are written to `FileManager.default.temporaryDirectory` as
  `Kalorie_<from>_<to>.pdf` / `.xlsx` (ISO dates), then presented through a
  `UIActivityViewController` wrapper. `ShareLink` is not used: it needs its item before the tap,
  and the file only exists after an async generation. The temp file is removed when the share
  sheet completes.
- **Structure:** `Features/Export/` with `ExportConfigurator`, `ExportView`, `ExportViewModel`
  (`State`: `idle` / `generating`, `alertItem` for errors, per
  [ADR 0018](../adr/0018-per-feature-error-alerts-with-no-global-handler.md)).
  `GenerateFoodExportUseCase` and `FetchFoodsConsumedInRangeUseCase` go in `Core/UseCases/`,
  each with a `Fake`.

### Implementation order

0. **Spike: PdfKmp in this build.** Create the ExportKit Gradle module with PdfKmp and render a
   two-page A4-landscape table with a repeated header and the text *Příliš žluťoučký kůň úpěl
   ďábelské ódy*. Build the XCFramework with the project's Kotlin 2.1.0, embed it, write the
   PDF to a file from a unit test, and inspect it.
   **Exit criteria:** it builds without raising the Kotlin version beyond what MacroKit,
   MealKit and TextKit accept; diacritics render correctly; the header repeats.
   **If it fails:** PDF moves to Swift (`UIGraphicsPDFRenderer`, drawing the same report tree
   that ExportKit returns), and this document gets a dated `Update` paragraph recording that.
1. ExportKit: report tree and `commonTest` coverage of rules 1–8.
2. ExportKit: xlsx writer and ZIP/CRC32, with `commonTest`.
3. ExportKit: PDF renderer.
4. iOS: provider method, the two use cases, the Swift adapter, the feature screens.

### File-by-file impact

| File | Change |
|---|---|
| `ExportKit/` (new) | Gradle module: `commonMain` (report, xlsx, PDF), `iosMain` (`NSData` bridge), `commonTest` |
| `Kalorie.xcodeproj` | Embed `ExportKit.xcframework`, add the Gradle build phase (manual edit, as for the other modules) |
| `Core/UseCases/FetchFoodsConsumedInRangeUseCase.swift` (new) | Range read over the existing provider query |
| `Core/UseCases/GenerateFoodExportUseCase.swift` (new) | Fetch, day bucketing, meal resolution, ExportKit call, temp file |
| `Features/Export/` (new) | Configurator, View, ViewModel, share-sheet wrapper |
| `Features/MealTypeSheet/MealTypeSheetView.swift`, `…ViewModel.swift`, `…Configurator.swift` | Toolbar button, `isExportPushed`, destination |
| `Localizable.xcstrings`, `L10n` | Screen strings, column headers, report labels |
| `TODO.md` | Remove *Data export* once shipped |
| `docs/ARCHITECTURE.md` | New subsection once shipped |

## Alternatives considered

- **CSV instead of xlsx.** Trivial to write and opens in Excel. Rejected by the requirement:
  the user wants a real `.xlsx`, with the same hierarchy as the PDF and bold structure rows,
  which CSV cannot carry.
- **Native rendering per platform** (iOS `UIGraphicsPDFRenderer` + a Swift xlsx writer; Android
  its own). No dependency and no `iosMain`, but two implementations of the same file formats that
  must match byte-for-byte in structure. That is exactly the divergence design 0004 created KMP
  modules to prevent. It remains the PDF fallback if the spike fails.
- **An xlsx library via SPM on iOS (e.g. libxlsxwriter).** Robust, but iOS-only. Android would
  have to match it with Apache POI, which brings back the two-implementation problem.
- **A hand-written PDF writer in `commonMain`.** No dependency, but Czech characters outside
  WinAnsi (`č ř ě ů ň ť ď`) require an embedded, subsetted TrueType font. That is far more work
  than the rest of this design combined, and PdfKmp already solves it.
- **Compose-based PDF libraries** (KmPdf, compose-multiplatform-pdf-export, pdf-kmp). Rejected:
  they render Compose UI, and shared UI is out of scope per design 0004.
- **Routing the export through `DashboardViewModel.monthCache`.** Rejected: it is sized for one
  visible dashboard and never evicts, which a multi-month export would stress.
- **Month-by-month fetching with a progress indicator.** Not needed at the expected volume. One
  query is simpler. Revisit only if a real export proves slow.
- **Doing day bucketing and meal resolution in Kotlin.** More sharing, but it needs
  `kotlinx-datetime` and a second copy of ADR 0022's pin resolution, which lives in Swift today.
  Rejected for this step. An Android client re-implements both for its Dashboard anyway.

## Cross-cutting concerns

- **Privacy.** The file contains the user's full diet history. It leaves the app only through
  a share sheet the user drives. The temp file is deleted after sharing. Nothing is uploaded.
- **Cost.** One Firestore read per exported entry, per export, with no limit on the interval.
- **Anonymous users** can export. Export reads only their own `users/{userId}/foodConsumed`,
  which the existing rules already allow. No rule change.
- **App size and build time.** A fourth Kotlin/Native runtime plus PdfKmp and its bundled font.
  Measure after the spike, as design 0004 required for MacroKit.
- **Second client.** The report rules, the xlsx writer and the PDF layout are shared. Android
  supplies only day bucketing, meal resolution, strings and a share intent.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| PdfKmp requires a newer Kotlin than 2.1.0, or fails in the XCFramework build | PDF blocked | Spike (step 0). Fallback: Swift `UIGraphicsPDFRenderer` over the shared report tree |
| PdfKmp is abandoned (small project, ~54 stars) | Maintenance falls on us | Apache 2.0, can be forked. The report tree stays independent of it, so only the renderer is affected |
| Hand-written ZIP/xlsx has a structural error Excel rejects | File won't open | `commonTest` against CRC32 test vectors and the XML parts. Open the output in Excel and Numbers before shipping |
| Grouping uses current meal types, not the ones in effect on a past day | An old day looks regrouped | Accepted (Non-goals). Same behaviour as the Dashboard |
| Very long interval makes generation slow on older devices | Long spinner | Loader keeps the screen inert. Revisit month-by-month fetching only if observed |

## Outcome

_Not yet implemented._

**Update — 2026-09-19 (step 0 failed; PDF moves to Swift).** The spike could not consume PdfKmp
1.3.0 from ExportKit: the library is compiled with Kotlin 2.4.10, and Kotlin/Native 2.1.0 (the
version MacroKit, MealKit and TextKit build with) skips its klib with `Incompatible ABI version.
The current default is '1.201.0', found '2.4.0'`. Raising ExportKit's Kotlin alone is not an
option (each XCFramework carries its own runtime, but the project pins one toolchain), and raising
all four is out of scope. Per the fallback above, PDF rendering is done in Swift with
`UIGraphicsPDFRenderer`, drawing the report tree that ExportKit returns. ExportKit therefore owns
the report tree and the xlsx writer only; it has no third-party dependency and no PdfKmp. An
Android client will need its own PDF renderer over the same tree.

**Update — 2026-09-19 (later; the blocker is gone).** [ADR 0034](../adr/0034-kotlin-toolchain-pinned-at-2.4.10-across-kmp-modules.md)
moved all KMP modules to Kotlin 2.4.10, and PdfKmp 1.3.0 then compiled for `iosArm64` and
`iosSimulatorArm64` in ExportKit. The Swift renderer above still ships, since it is implemented and
tested; moving PDF into ExportKit is now possible and would give an Android client the same layout
for free, but has not been done.

**Update — 2026-09-19 (PDF is back in ExportKit).** With the toolchain at Kotlin 2.4.10 the
originally designed path works: ExportKit depends on PdfKmp 1.3.0 and `renderPdf(report)` /
`renderPdfData(report)` produce the PDF, so the Swift `UIGraphicsPDFRenderer` fallback was removed
and an Android client gets the identical layout. Two consequences for this document: the
*No third-party dependency* line in ExportKit module now reads *one dependency, PdfKmp*, and
`ExportLabels` gained `decimalSeparator`, because PDF cells are formatted as text in Kotlin
(`formatNumber`) rather than as numbers the way xlsx cells are; Swift passes
`Locale.current.decimalSeparator`. Checked by rasterising a sample: Czech diacritics render, the
column header repeats on every continuation page, and a page break can separate a section's foods
from its subtotal row (accepted, rows are not kept together).

**Update — 2026-09-19 (offline handling removed).** The app works only with data delivered online,
so the export has no offline behaviour of its own. The server-only provider method
`loadFromServerAsync(from:where:isGreaterThanOrEqualTo:isLessThan:)`, `FoodExportError.offline`, its
alert and the *Offline* concern were dropped; `FetchFoodsConsumedInRangeUseCase` uses the ordinary
range read like every other feature.
