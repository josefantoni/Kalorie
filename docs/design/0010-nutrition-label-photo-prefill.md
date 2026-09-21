# Design: Pre-filling the food form from a photo of the packaging

- **Status:** Implemented, including the *Revision — camera-first flow* (2026-09-14)
- **Scope:** iOS
- **Date:** 2026-09-13

> **Read the revision at the end of this document first.** It replaces *Capture* (the still-photo
> `UIImagePickerController` and the `isAvailable`-only permission check) and *Surfacing to the user*
> (a camera button inside the form). The pipeline, parser, consistency checks, Foundation Models
> path and the fill-empty-only merge rule are unchanged by it.

## Context and scope

Adding a catalogue item means typing up to thirteen fields off the packaging by hand:
`FoodItemFormInput` holds the barcode, Czech and English name, package weight, kJ, kcal, fat,
saturates, unsaturates, carbohydrate, sugars, fibre, protein, salt, and a list of portions. The same
input is edited in three places through the shared `FoodItemFormFields` component
(`ARCHITECTURE.md` § 7.3):

- the user's *new item* form in `AddFoodSheetView` (`AddFoodSheetMode.newItem`), which submits
  through `SubmitFoodItemUseCase` / `UpdateMySubmissionUseCase` (§ 7.2);
- the maintainer's `ModerationReviewView`;
- the maintainer's `ModerationCatalogueEditorView`.

This document designs a camera button on all three that reads the packaging and pre-fills the form.
The user still reviews every value and submits exactly as today.

What already exists and is reused: VisionKit is linked and `DataScannerRepresentable` reads barcodes
(§ 2.5), the camera permission and its denied/revoked handling are in place, and
`NSCameraUsageDescription` is declared. The deployment target is iOS 26.0.

Boundary: this is a **pre-fill of a form**. Nothing about what is written to Firestore, the
submission lifecycle (design 0009) or validation (`FoodItemValidation`) changes.

### What was verified before writing this

On macOS 26.6.2 with the iOS 26.5 SDK (not yet on an iPhone — see *Risks*):

- **Vision text recognition supports Czech.** `VNRecognizeTextRequest` at `.accurate`, revision 3,
  lists `cs-CZ`, alongside `pl-PL`, `de-DE` and `en-US`. It does not list Slovak or Hungarian.
- **Foundation Models does not support Czech.** `SystemLanguageModel.default.supportedLanguages` has
  23 entries with no Czech, Slovak or Polish, and `supportsLocale(cs_CZ)` is `false`.
  `LanguageModelSession.GenerationError` has an `unsupportedLanguageOrLocale` case.
- **Foundation Models takes text only.** The iOS 26.5 SDK's `FoundationModels.swiftinterface` has no
  image input, so a photo must go through Vision OCR before the model can see it.
- Availability is reported as `.available` or `.unavailable(deviceNotEligible |
  appleIntelligenceNotEnabled | modelNotReady)`.

This is what shapes the design: Apple Intelligence can only be enabled when the device language is a
supported one, so on an iPhone set to Czech the model is likely never available (unverified on iOS at
the time this section was first written — see *Update, 2026-09-13* below).

**Update, 2026-09-13 — resolves Open question 2, from public sources rather than a physical device.**
No iPhone was available to verify this directly; instead, Apple's own support page and independent
developer reporting confirm the mechanism precisely enough to settle the question without one:

- Apple Intelligence and Foundation Models require the device's **Siri/system language** — not
  just the app's own locale — to be one of a fixed list of supported languages. As of the iOS 26.1
  / 27 rollout that list is English, Chinese (Simplified/Traditional), Danish, Dutch, French,
  German, Italian, Japanese, Korean, Norwegian, Portuguese, Spanish, Swedish, Turkish and
  Vietnamese. **Czech, Slovak, Polish and Hungarian are all absent.**
- There is **no per-app language override**. A developer request for exactly this
  (`LanguageModelSession(preferredLanguage:)`) is open on the Apple Developer Forums
  (thread 805378) as a feature request, not a shipped API. The only workaround is the user
  changing their system-wide Siri language to a supported one, which is not a realistic ask for
  this app's Czech-first users.
- Consequence: on a real device with the system language set to Czech, `SystemLanguageModel.default.availability`
  will be `.unavailable`, not merely `.available` with `unsupportedLanguageOrLocale` thrown per
  request — the framework is gated off before a single request is ever made. The concern this
  section raised is confirmed, not just left open.

This does not disqualify the bonus path from being built (see *Design → Foundation Models*): every
error and unavailability path it can hit already degrades to "the model returned nothing," so a
bug in code that will rarely execute for this app's real users cannot corrupt the base path's
output. It does mean the bonus path's *actual* on-device behaviour for the rare user who does have
a supported Siri language remains genuinely unverified, and stays a residual risk (see *Risks*)
rather than a blocking one.

Sources: [How to get Apple Intelligence](https://support.apple.com/en-us/121115),
[Apple Intelligence Languages in iOS 27](https://www.macobserver.com/tips/round-ups/apple-intelligence-languages-ios-27-all-15-siri-ai-october/),
[Foundation Models unavailable for millions of users due to device language restriction](https://developer.apple.com/forums/thread/805378),
[Exploring Foundation Models: Supported Languages and Internationalization](https://rudrank.com/exploring-foundation-models-supported-languages-internationalization).

## Goals

- One tap on a camera button fills every field the photo supports, on **every** iOS 26 device,
  with Apple Intelligence off.
- On a device where Foundation Models is available, fields the deterministic path could not fill
  (name, package weight, portions) are filled as well.
- A value is never invented: each pre-filled number appears in the recognised text, and passes the
  consistency checks below, or the field is left untouched.
- Pre-filled fields are visibly marked in the form until the user edits them. The mark is UI state
  only and is never persisted.
- The photo never leaves the device and is not stored.
- The recognition logic is covered by XCTest against recorded OCR output, with no camera or Vision
  call in the test run.

## Non-goals

- **Storing the photo or attaching it to a submission.** That is the separate *Packaging photo on a
  submission* item in `TODO.md`, blocked on Firebase Storage. This design needs no Storage.
- **Telling the maintainer which values came from a photo.** Decided: marking is form-only. A
  `values_source` field on `foodItemSubmissions` would be a `Backend` change with its own ADR, and
  is not part of this work.
- **A cloud model.** No network, no Cloud Function, no Blaze plan, no per-call cost.
- **Parsing in the shared Kotlin modules.** See *Alternatives considered*.
- **Recognising a food from a photo of the food itself.** Only packaging with a nutrition table.
- **Reading values per portion and converting them to per 100 g.** The form is per 100 g, and the
  EU table always has a per-100 g column. A label with only a per-portion column fills nothing
  numeric.

## Design

### Pipeline

```
photo ──► OCR (Vision) ──► NutritionLabelParser ──► consistency checks ──► reading
                  │                                                           │
                  └──► FoundationModelExtractor (if available) ──► grounding ─┘
                                                                              ▼
                                                           merge into FoodItemFormInput
```

Layered per project convention, with the platform frameworks behind injected protocols so the
use case is testable:

| Type | Kind | Does |
|---|---|---|
| `RecognizeNutritionLabelUseCase` | use case, `callAsFunction(image:) async throws -> NutritionLabelReading` | runs the pipeline end to end |
| `TextRecognizerProtocol` / `BarcodeDetectorProtocol` / `VisionTextRecognizer` | protocols + one Vision adapter implementing both | image → `[RecognizedTextLine]` (string, bounding box) and image → barcode `String?` |
| `NutritionLabelParser` | pure `enum` namespace of `static` functions | lines → per-field candidates; no framework imports |
| `NutritionLabelModelExtractorProtocol` / `FoundationModelExtractor` | protocol + Foundation Models adapter | OCR text → candidate fields, or `nil` when unavailable |
| `NutritionLabelReading` | domain value | the accepted values, each optional, plus which fields are set |

`RecognizedTextLine` is the seam that makes the parser testable: tests feed it directly, as
hand-written fixtures (see *Open questions* for why these are synthetic rather than captured from
Vision), and never touch Vision. It does not carry a confidence score — nothing in the design ended
up needing one, since a candidate is trusted or discarded by the consistency checks below, not by
how confident Vision was.

### Capture

*Superseded in part by the revision below: the live scanner now triggers the capture, the still
photo is still what gets parsed, and the permission check is three-state.*

A still photo, not the live `DataScannerViewController`. Parsing a table needs every line of the
same frame with its geometry at once. Live text recognition hands over items as they stabilise,
and a table half-read in one frame and half in the next cannot be assembled reliably. The still
capture itself is `UIImagePickerController(sourceType: .camera)`, wrapped the same way
`DataScannerRepresentable` wraps its `UIViewControllerRepresentable` — the simplest API that hands
back one `UIImage` and, with no explicit save call, never writes it to the Photos library.

The camera button's availability check reuses `DataScannerViewController.isSupported` /
`.isAvailable` (§ 2.5) rather than a separate `AVCaptureDevice` permission check — both features
gate on the same camera permission, so a second check would just be a second way to ask the same
question.

The still image goes through `VNRecognizeTextRequest` with `.accurate`,
`recognitionLanguages = ["cs-CZ", "pl-PL", "de-DE", "en-US"]`, and language correction **off**,
since correction is built for words and may rewrite `0,5` or `<0,5`. Whether it actually does is
unverified; it is the first thing the fixture set should settle.

The same still image also runs `VNDetectBarcodesRequest`, so a photo that includes the barcode
fills `scannedCode`. The barcode field stays read-only when it is already locked
(`isEditingSubmission`, and the editor's existing item).

### Deterministic parser (the base path)

EU Regulation 1169/2011 fixes both the content and the order of the mandatory table: energy (kJ and
kcal), fat, of which saturates, carbohydrate, of which sugars, protein, salt. Fibre and unsaturates
are optional. This is what makes a deterministic parser realistic.

1. **Rows.** Group lines into rows by vertical overlap of their bounding boxes. A row label is the
   leftmost text; values are the numbers to its right.
2. **Label dictionary.** Match row labels, after `TextKit` accent folding and lowercasing, against
   a per-field keyword list in Czech, Slovak, Polish, German and English, e.g. `energetická hodnota
   | wartość energetyczna | brennwert | energy` and `z toho nasycené | w tym kwasy nasycone | davon
   gesättigte | of which saturates`. Multilingual labels put several translations in one row, and
   any match counts.
3. **Column.** Choose the per-100 g column from its header (`100 g`, `100 ml`, `100g`). Ignore
   per-portion and `%RI` / `RHP` columns. Without a recognisable per-100 g header, no numeric field
   is filled.
4. **Numbers.** Accept a decimal comma or point, an optional unit, and `<` or `≤` (`<0,5 g` becomes
   `0.5`, the stated upper bound, not `0`). Energy commonly appears as `1550 kJ / 370 kcal` in one
   cell, so split it by unit.
5. **Package weight** is a separate search over all lines, outside the table: a number followed by
   `g`, `kg`, `ml` or `l`, next to `hmotnost | netto | obsah | net weight`. More than one candidate
   means no value. The bare `e` mark (estimated quantity, EU convention) from the original plan was
   dropped during implementation: a single letter as a keyword would match almost any line by
   substring, which is a worse failure mode than missing the rare label whose only weight cue is `e`.

### Consistency checks

A candidate is accepted only if the checks involving it pass. A failing field is left empty rather
than pre-filled with a likely-misread number.

- kJ ≈ kcal × 4.184, within a small tolerance. On a mismatch, neither is filled.
- Saturates ≤ fat, sugars ≤ carbohydrate.
- Fat + carbohydrate + protein + salt + fibre ≤ 100 g.
- Declared kJ within a tolerance of `MacroKit.energyKJFromMacros(fat:carbohydrate:protein:)`, the
  same EU conversion factors ADR 0007 uses. This check catches a single misread digit, the most
  likely OCR error.

**Unsaturates** are not on most labels. When absent, the form value is derived as
`max(0, fat − saturates)`, the same derivation the OpenFoodFacts mapping already applies
(`ARCHITECTURE.md` § 2.4).

### Foundation Models (the bonus path)

Runs only when `SystemLanguageModel.default.availability == .available`. Otherwise the model is
skipped silently, since the base path has already done its job and this path is only an addition.

- **Input:** the OCR text, not the image (see *What was verified*). Instructions are in English;
  the label text is passed as data.
- **Output:** a `@Generable` struct with optional fields (name, package weight, portions, and the
  table values), each with a `@Guide`, so the model returns typed values instead of prose.
- **Grounding:** every number the model returns must appear in the OCR text. A number that does
  not is discarded. A name must be a substring of the OCR text.
- **Merge rule:** the model **only fills fields the parser left empty**, and its numbers go through
  the same consistency checks. Where both produced a value, the parser wins, because it is
  deterministic and tested.
- **Errors:** every error `LanguageModelSession.respond` can throw — `unsupportedLanguageOrLocale`,
  `exceededContextWindowSize`, `guardrailViolation`, and any other `GenerationError` case a future
  SDK adds — is caught with one generic `catch` and treated as "the model returned nothing," logged
  via `Log.warning`. None of them reaches the user. Broader than the three named cases originally
  planned, deliberately: enumerating specific cases would leave the bonus path one SDK update away
  from an unhandled error reaching the UI for a path whose entire value proposition is that it fails
  invisibly.

Whether the model extracts anything useful from Czech label text, or throws
`unsupportedLanguageOrLocale` on it, is unverified and is the main open question of this path.

### Merging into the form

`RecognizeNutritionLabelUseCase` returns a `NutritionLabelReading`, and the view model applies it to
`formInput`. The fields it set are stored in a view-model `Set<FoodItemFormField>` that is passed
to `FoodItemFormFields`, which marks those fields. Editing a field removes it from the set.
`FoodItemFormInput` itself gains no properties, so nothing new can leak into
`asFoodItemDomain()`.

**Decided (was Open question 1) — fill empty fields only, identically on all three screens.**
A field already holding a non-default value is never overwritten by the photo, on the new-item
form, the maintainer's review, or the catalogue editor alike. This was chosen over overwriting
everywhere, and over the two-tier "editor overwrites, review only fills" split the draft
considered: a single rule is simpler to implement once and verify once, it can never hide a
difference between what a user typed and what the packaging says (the risk that ruled out
overwriting on the review screen), and it turns out to need no special case for the barcode
field either — `scannedCode` is already non-empty exactly when design's own text called it
"locked" (editing a rejected submission, or the catalogue editor's loaded item), so "fill empty
only" reproduces that lock as a consequence of the one rule rather than as a second one. The
editor's own "correct a wrong value" use case is unaffected by this choice, since a maintainer
who wants to replace a specific value they can already see still types over it by hand, same as
today — this feature only ever offers to fill in what is blank.

- **New item form:** the recognised fields are written, the others stay as they are.
- **Maintainer review and editor:** identical rule — recognised values are written only into
  fields that are still at their default.

### Surfacing to the user

*Superseded by the revision below for the new-item form; the moderation screens keep a camera
button, but it opens the new live camera.*

- A camera button in the form section of all three screens, reusing the scanner's existing
  permission guard and `cameraPermissionAlert` path (§ 2.5).
- A progress state while recognition runs, which is usually a second or two on-device.
- **Nothing recognised** → an alert with a message (ADR 0018, ADR 0020), e.g. "No nutrition table
  found — photograph the table on the back of the packaging." A partial result gives no alert: the
  marked fields speak for themselves.
- Strings go through `L10n` (ADR 0019).
- `NSCameraUsageDescription` currently says the camera is for scanning barcodes only. It must be
  broadened to cover reading packaging, or App Review may treat the purpose string as inaccurate.

## Alternatives considered

- **Foundation Models alone.** Rejected: no Czech support, and Apple Intelligence is likely
  unavailable on a Czech-language iPhone, so the button would do nothing for most users.
- **A cloud multimodal model through a Cloud Function.** Best recognition quality, and it reads
  the image directly. Rejected: per-call cost, a Blaze plan, network dependence, and the photo
  leaving the device, all for a form the user reviews anyway.
- **Parser only, no model.** Viable, and it is the base of this design. Rejected as the whole
  design because name, package weight and portions have no fixed position on packaging and are
  poorly served by rules. The model covers them at no cost where it exists.
- **Live `DataScannerViewController` with `.text()`.** Rejected as the *source* of values for the
  table: see *Capture*. The revision below uses it only as the *trigger* for a still capture.
- **The parser in a shared Kotlin module (like `TextKit`).** Rejected. Design 0005 moved logic to
  KMP where clients must produce identical results or corrupt each other's data. A pre-fill that
  the user reviews before submitting cannot corrupt anything, and an Android client's OCR (ML Kit)
  produces differently shaped output anyway. The keyword dictionary is the one piece worth sharing
  later, as data rather than code.
- **Recording in the submission that values came from a photo.** Declined by decision. See
  *Non-goals*.

## Cross-cutting concerns

- **Privacy:** on-device only, and the image is discarded after recognition. Nothing new goes to
  Firebase, so the App Store privacy label is unchanged. Only the camera purpose string changes.
- **Offline:** both paths work offline. Foundation Models may report `modelNotReady` while the model
  downloads, which counts as unavailable.
- **Security rules and schema:** untouched. `FoodItemValidation` and the rules remain the only
  guards on what is written.
- **Second platform:** nothing binding. This is `iOS` precedent: an Android client may pre-fill
  differently or not at all.
- **Logging:** a parse that fills nothing loses no user data, so per design 0007 it is a local
  `Log.warning` rather than a Crashlytics non-fatal (`Log.error`). It logs counts only (rows found,
  fields matched), never the recognised text.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| OCR misreads a digit (`8`↔`3`, lost decimal comma) | wrong macros submitted | consistency checks against kJ/kcal and `MacroKit`; failing fields stay empty; user and maintainer review |
| Label layouts vary more than the rules cover (rotated text, curved packets, glare) | few fields filled | test fixtures are synthetic, not photographed (see *Open questions*) — real fixtures should replace/extend them before this ships to catch this; a partial fill is still less typing. One instance already materialised and was fixed — the linear declaration format small packages use instead of a table (see Outcome, 2026-09-14) — which is the kind of gap synthetic fixtures cannot catch by construction |
| Slovak or Hungarian-only row labels, which Vision does not list | some rows unmatched | Czech recognition reads Slovak text closely; dictionary includes Slovak; Polish/German/English rows usually present on the same label |
| Foundation Models framework behaviour on Czech OCR text is unverified on a real device | bonus path silently never fires, or fires with unexpected output | base path is independent and unaffected either way; every known failure mode already degrades to "nothing extracted" (see *Update, 2026-09-13*) |
| Model returns plausible but absent numbers | invented values | grounding: every number must occur in the OCR text; parser values always win |
| Language correction rewrites numbers | systematic misreads | correction off; not yet confirmed against a real device — synthetic fixtures can't exercise Vision's actual correction behaviour |

## Open questions

All three questions this section originally raised are resolved; see the inline updates above
(*What was verified* for device verification, *Merging into the form* for the maintainer-screen
behaviour) and the fixture note directly below. Nothing here still blocks implementation.

**Decided (was Open question 3) — hand-written synthetic fixtures, not photographs of real
packaging.** No camera or real packaging was available while writing the implementation, so the
fixtures feeding `NutritionLabelParserTests` are hand-authored `RecognizedTextLine` literals (text
+ bounding box), shaped like Vision's own output for the row/column layout EU Regulation
1169/2011 mandates, rather than JSON captured from an actual `VNRecognizeTextRequest` run. They
cover the cases this question originally asked for: a single-language Czech label, a
multi-language label (Czech rows alongside Polish/German/English translations), a label with no
per-100 g column, and the inputs each consistency check is meant to reject (a kJ/kcal mismatch, a
`<0,5` value, saturates exceeding fat). What they **cannot** cover, because nothing about them came
from a camera: real OCR misreads (a digit misread, a lost decimal comma), rotated or curved text,
glare, and the true shape of `VNRecognizeTextRequest`'s line segmentation on a physical label —
whether a label's number ends up as its own `RecognizedTextLine` or fused with its neighbour is an
assumption here, not an observation. This is a known gap, not an oversight: before this ships,
real fixtures captured from an actual device (per the original three product categories above)
should replace or extend the synthetic set, and the row-grouping and column-detection heuristics
in `NutritionLabelParser` should be re-tuned against whatever they show. Tracked as a residual
risk below, not as a blocker — a synthetic-only test suite still catches a regression in the
parsing *rules themselves* (which is what it was written to do), it just cannot catch the parser
being fed text shaped differently than expected.

## Outcome

**Update — 2026-09-14.** The first real photo tried against this (a small deli-tub label, not a
press photo) filled nothing but the barcode. The label used EU 1169/2011's **linear declaration**
format — "Tuky 3,3 g z toho nasycené mastné kyseliny 0,6 g, Sacharidy…" as one sentence — which
small packages are legally allowed to use instead of the table this design's parser assumed
throughout *Deterministic parser*. This was already named as a residual risk ("Label layouts vary
more than the rules cover"), but the specific shape of the gap — an *entire second legal format*,
not just noisy geometry on the assumed one — was bigger than that entry implied.

`NutritionLabelParser` now has two passes: the table-column pass this document already describes,
and a fallback that runs only when that pass found none of the five macro fields *and* the text
contains a nutrition-declaration cue (`"výživové údaje"` and its Polish/German/English equivalents,
or a bare `"100 g"` / `"100 ml"` mention) — never on text with no such cue, so a label with no
nutrition information at all still correctly fills nothing. The fallback has no column geometry to
key off, so it locates each field by its keyword's position in the full OCR text and reads the
number in the gap before the next field's keyword starts. The same real photo's ingredients list
turned up a second, sharper issue: it names salt three times before the actual value does ("jedlá
sůl" as an ingredient), so the fallback anchors its scan to start after the nutrition-section cue
when one is found, rather than the whole label's text — otherwise it would confidently return the
wrong number instead of the right one.

The photo also exposed a smaller, unrelated miss in *package weight*: `Hmotnost:` and `200 g` were
two separate Vision lines (the value is set in a much larger font, directly below its label), and
the original single-line search never looked at a neighbouring line. It now also checks the nearest
line below a keyword line when the keyword's own line has no number on it.

Both fixes are covered by `NutritionLabelParserTests`, including the collision case above, using
fixtures transcribed from the real photo rather than invented. The synthetic-fixture gap this
document already flagged is exactly what let the linear format go unnoticed until a real photo was
tried — nothing about this update changes that residual risk, it is a case of it materialising.

Implemented. Scope trims made during implementation, not called out elsewhere in this document:

- The pre-filled mark (`FoodItemFormField` highlighting in `FoodItemFormFields`) covers the twelve
  numeric/name rows that component renders. `scannedCode` (rendered separately, outside
  `FoodItemFormFields`, on the new-item and review screens) and `portions` (its own
  `FoodPortionsSection`) are filled by the same merge but are not visually marked — marking them
  would mean teaching two more components this feature's vocabulary for a mark that, on a screen
  the user has just opened, they have little reason to look for a difference on.

## Revision — camera-first flow (2026-09-14)

**Status:** implemented. Scope: iOS. Everything in *Pipeline*, *Deterministic
parser*, *Consistency checks*, *Foundation Models* and *Merging into the form* still holds.

### Why

Filling the form by hand remained the primary path, with the camera as one row in it. The camera
is what makes a submission fast and accurate, so the new-item flow now starts from the camera and
the form becomes a review step after it.

### Decisions

1. **The new-item tab is a capture prompt, not a form.** `AddFoodSheetMode.newItem` shows a
   centred camera icon and an explanatory text: photograph the nutrition table, then check the
   values. There is **no manual-entry path** for a brand-new item — no camera, no new item.
   Decided deliberately: a catalogue item without a readable label is not wanted.
2. **Three-state camera permission.** Checked with `AVCaptureDevice.authorizationStatus(for: .video)`,
   replacing *Capture*'s decision to rely on `DataScannerViewController.isAvailable` alone, which
   cannot tell "not asked yet" from "denied".
   - `.notDetermined` → the prompt (icon + text). Tapping the icon calls
     `AVCaptureDevice.requestAccess(for: .video)`; granted opens the camera, refused moves to denied.
   - `.denied` / `.restricted` → neither icon nor text; instead a message that camera access must
     be enabled in Settings to read the nutrition table, and a button opening
     `UIApplication.openSettingsURLString`.
   - `.authorized` → the prompt; tapping opens the camera straight away.
   - `DataScannerViewController.isSupported == false` (unsupported device, Simulator) → a message
     that this device cannot scan. No fallback, per decision 1.
   - The state is re-read on every `scenePhase` → `.active` transition, so returning from Settings
     updates the screen, and a camera already open is closed if access was revoked.
3. **Live scanner as a trigger, still photo as the source.** The camera is a full-screen
   `DataScannerViewController` recognising `.text()` and `.barcode()`. On every recognised-items
   update the text items are converted to `[RecognizedTextLine]` and run through
   `NutritionLabelParser.parse(lines:)`. When the result is complete enough (see *Auto-capture
   predicate*) the scanner calls `capturePhoto()` once, and the still image goes through
   `RecognizeNutritionLabelUseCase` exactly as before. *Capture*'s reason for rejecting live text —
   a table assembled from several frames is unreliable — does not apply, because no live value is
   ever written to the form.
4. **Manual shutter as a fallback.** A shutter button is always visible, for packaging the trigger
   never locks onto (curved packets, glare).
5. **Nothing recognised → stay on the camera.** When the still yields no nutrition field
   (`reading.recognizedFields.isEmpty`; a barcode alone does not count), the camera stays open,
   scanning resumes, and a hint is shown **inside the camera view**. No push, no empty form.
6. **Success → close the camera, then push the review screen** inside the add-food sheet's
   `NavigationStack`: `NewFoodItemReviewView` (name used in this document; any name following the
   conventions is fine).
7. **Barcode.** The live scanner remembers the first barcode payload it sees at any point in the
   session, so the user can show the barcode side and then the table. After the still is parsed,
   `reading.scannedCode` is taken from the still, or from the remembered live barcode when the still
   had none. The review screen's barcode row also has a scan icon for when neither caught it. The
   existing fill-empty-only rule keeps a locked barcode untouched.
8. **Field order everywhere:** portions → name → barcode → all remaining fields. Portions come
   first because they are the part OCR almost never fills on a Czech-language device (Foundation
   Models is unavailable there — see *Update, 2026-09-13*), so they are what the user must add.
9. **Screens that open with existing data skip the camera.** Editing a rejected submission pushes
   the review screen directly. `ModerationReviewView` and `ModerationCatalogueEditorView` are
   already forms: they adopt the field order from decision 8, and their camera button opens the
   same live camera from decision 3 (the still-photo picker is removed everywhere). On those
   screens, and on the review screen, a successful capture closes the camera and merges into the
   form in place — no push.

   **Update — 2026-09-19 (decision):** the *Scan nutrition label* button is removed from the new-item
   review screen (`AddFoodSheetView`). That screen is only reachable after the camera flow already
   ran (or after *Add manually*), so the button offered a second scan of what was just scanned.
   `FoodItemFormSections.onNutritionLabelScanTapped` is now optional and the button renders only
   when a caller passes it; `ModerationReviewView` and `ModerationCatalogueEditorView` still do, so
   the maintainer screens keep their in-place camera. `AddFoodSheetViewModel.onReviewNutritionLabelCameraTapped`
   was deleted with it. The barcode-rescan icon on the barcode row is unaffected.

### Auto-capture predicate

Capture fires when a parse of the live lines has `caloriesPerHundredGrams` **and** `fat`,
`carbohydrate` and `protein` all non-nil. Because `parse(lines:)` already applies the consistency
checks, a live read with a misread digit usually fails the predicate rather than firing. The
threshold is a guess to tune on a device; it lives in one pure, tested function so tuning is a
one-line change.

### Risks added by this revision

| Risk | Impact | Mitigation |
|---|---|---|
| Live `.text()` recognition is too noisy for the predicate to ever pass on real labels | auto-capture never fires | manual shutter; predicate threshold is one function |
| `capturePhoto()` returns a lower resolution than `UIImagePickerController` did | still-photo OCR gets worse | **unverified — check on device first**; if it is worse, fall back to an `AVCapturePhotoOutput` capture, which is a larger change |
| Live item geometry is in view points, y-down; the parser expects Vision's normalised, y-up boxes | rows grouped in reverse, weight search looks above instead of below | explicit conversion function with its own tests |
| No manual entry for a new item | a food without a readable label cannot be submitted; nothing can be submitted from the Simulator | accepted by decision 1 |

### Implementation handoff

Written for an implementer without this conversation's context. Paths are relative to the repo
root; line numbers are as of commit `37c9c36`.

**Order of work** — each step builds and passes tests on its own:

1. **Pure pieces + tests first.**
   - `NutritionLabelReading.isCompleteForAutoCapture: Bool` (extension in
     `iOS/Kalorie/Core/NutritionLabelRecognition/NutritionLabelReading.swift`), implementing the
     predicate above. Tests beside `NutritionLabelParserTests`: passes with kcal+fat+carbs+protein;
     fails when any one is nil; fails for a barcode-only reading. Encode the *why* in assertion
     messages (a premature capture wastes the photo, a missing one strands the user).
   - A pure conversion from a live text item's quad to `RecognizedTextLine`, e.g.
     `static func recognizedTextLine(transcript: String, bounds: RecognizedItem.Bounds, in viewSize: CGSize) -> RecognizedTextLine`.
     Keep the testable core free of VisionKit: take the four `CGPoint`s + `CGSize`. Output must be
     normalised 0…1 with **origin bottom-left**: `x = minX / width`, `y = 1 - maxY / height`,
     `width = (maxX - minX) / width`, `height = (maxY - minY) / height`. The parser depends on this:
     `groupIntoRows` sorts by `midY` descending as "top first" (`NutritionLabelParser.swift:194`),
     and `nearestLineBelow` treats a smaller `maxY` as lower on the label (`:287`). Test with two
     lines, one above the other, that the upper one gets the larger `midY`.
2. **Camera permission.** New `CameraAuthorizationProviderProtocol` (`status` returning a small
   domain enum `CameraAccess { notDetermined, authorized, denied, unsupported }`, and
   `requestAccess() async -> Bool`), a real implementation over `AVCaptureDevice` and
   `DataScannerViewController.isSupported`, and a `CameraAuthorizationProviderFake`. Injected into
   `AddFoodSheetViewModel`, `ModerationReviewViewModel` and `ModerationCatalogueEditorViewModel`
   via `init`, assembled in `AddFoodSheetConfigurator.swift:44` and `ModerationConfigurator.swift:42,52`.
   Every `#Preview` and test `makeSUT` constructing these VMs must pass the fake
   (`AddFoodSheetView.swift` preview, `ModerationReviewView.swift` preview,
   `ModerationCatalogueEditorView.swift:106`, `ModerationQueueView.swift:94,104`, and the three
   `*ViewModelTests.swift` files).
3. **Live camera.** Replace `iOS/Kalorie/Components/NutritionLabelCameraRepresentable.swift`
   (delete it; it is the `UIImagePickerController` wrapper) with:
   - `NutritionLabelScannerRepresentable`: `UIViewControllerRepresentable` over
     `DataScannerViewController(recognizedDataTypes: [.text(), .barcode()], qualityLevel: .accurate,
     recognizesMultipleItems: true, isHighlightingEnabled: true, …)`. Copy the shape of
     `iOS/Kalorie/Features/AddFoodSheet/DataScannerRepresentable.swift` — an `isProcessing: Bool`
     input that calls `stopScanning()` / `startScanning()` in `updateUIViewController`, exactly like
     its `isSearching`. Its coordinator, in `didUpdate`/`didAdd` over `allItems`: records the first
     `.barcode` payload; converts `.text` items via step 1; if the parse passes the predicate and no
     capture is in flight, `try await dataScanner.capturePhoto()` and hands `(UIImage, liveBarcode: String?)`
     to an `onCaptured` closure. Manual shutter: a `manualCaptureRequest: Int` input — when it
     changes, `updateUIViewController` triggers the same capture. Do **not** reuse or modify
     `DataScannerRepresentable`; it asserts on non-barcode items (`DataScannerRepresentable.swift:49`)
     and drives the search flow.
   - `NutritionLabelCameraView` (SwiftUI): the representable, a shutter button, a close button, a
     `ProgressView` overlay while `isRecognizingNutritionLabel`, and a hint text. The hint must be
     rendered **inside this view**: an `.alert` attached to the presenting screen does not appear
     while a `fullScreenCover` is on top.
4. **Shared prefill logic.** In `iOS/Kalorie/Core/Utils/NutritionLabelPrefilling.swift`:
   - add `isNutritionLabelCameraVisible: Bool` and `nutritionLabelCameraHint: String?` to the
     protocol (all three VMs already declare `isNutritionLabelCameraVisible` — e.g.
     `AddFoodSheetViewModel.swift:166`);
   - change `recognizeNutritionLabel(from:using:)` (`:21-34`) to take the live barcode, set
     `reading.scannedCode = reading.scannedCode ?? liveBarcode` before merging, and on
     `nothingRecognized`, any other error, **or** a reading whose `recognizedFields` is empty: set
     `nutritionLabelCameraHint = L10n.AddFood.nutritionLabelNothingRecognized`, keep the camera
     open, do not set `alertItem`, and do not merge. On success: merge as today, clear the hint,
     set `isNutritionLabelCameraVisible = false`, and return `true` so the caller can push.
   - add the camera-open action shared by all three screens: read the provider; `.authorized` →
     open; `.notDetermined` → `requestAccess()` then open or record denied; `.denied` →
     `alertItem = AlertItem(title: L10n.AddFood.cameraPermissionAlert)` (moderation screens and the
     review screen keep today's alert; only the prompt shows the denied state inline).
5. **Shared form layout.** Decision 8 needs name separated from the nutrient rows. Remove the name
   row from `iOS/Kalorie/Components/FoodItemFormFields.swift:26-31` and add a component, e.g.
   `FoodItemFormSections`, rendering in order: `FoodPortionsSection`, then one `Section` with the
   name field (highlighted via `.name`), an optional barcode row, the camera button, and
   `FoodItemFormFields`. Parameters: `formInput` binding, `highlightedFields`, `barcodeRow`
   (hidden / locked / editable-with-scan-action), `onNutritionLabelScanTapped`, and
   `onFieldEdited` **last**, called with trailing-closure syntax (SwiftLint `trailing_closure`).
   Use it in:
   - `ModerationReviewView.swift:34-50` — barcode locked (`.disabled(true)`, no scan icon);
   - `ModerationCatalogueEditorView.swift:41-50` — barcode hidden (today it is not shown; the
     search field above holds it);
   - the new review screen — barcode editable with scan icon, or locked when `isEditingSubmission`.
   Replace both moderation screens' `.fullScreenCover` (`ModerationReviewView.swift:55-59`,
   `ModerationCatalogueEditorView.swift:70-74`) and `nutritionLabelScanButton`
   (`:92-102`, `:85-95`) with `NutritionLabelCameraView` and the shared open action.
6. **Add-food sheet.** In `iOS/Kalorie/Features/AddFoodSheet/`:
   - `AddFoodSheetViewModel`: `@Published var cameraAccess: CameraAccess`, `@Published var isReviewPushed = false`,
     and a private "capture succeeded" flag. `onNutritionLabelCaptured` (`:277`) sets that flag
     from step 4's return value.
   - The push must happen in `.fullScreenCover(isPresented:onDismiss:)`'s `onDismiss` →
     `viewModel.onNutritionLabelCameraDismissed()`, which sets `isReviewPushed = true` only when the
     flag is set, then clears it. Setting the push while the cover is still animating out is
     routinely dropped by SwiftUI.
   - Starting a capture from the **prompt** begins a fresh item: reset `formInput`,
     `recognizedFields`, `editingSubmissionId` and `rejectionReasonBeingEdited` the same way
     `onModeSelected` does (`:265-275`) before opening the camera. The review screen's own camera
     button merges into the current form instead. (Implementer's default, not a user decision — the
     user only defined the forward path; flag it in the PR if it feels wrong on a device.)
   - `openEditingForm(for:)` (`:487-494`) additionally sets `isReviewPushed = true`.
   - `onModeSelected` also sets `isReviewPushed = false`.
   - `onScenePhaseActive(isCameraAvailable:)` (`:281`) additionally refreshes `cameraAccess` and
     closes the nutrition-label camera if access is gone. Existing scanner tests at
     `AddFoodSheetViewModelTests.swift:30-50` must keep passing.
   - A barcode-rescan action for the review screen: a separate `fullScreenCover` over
     `DataScannerRepresentable` whose delivered code is written to `formInput.scannedCode` and
     closes the cover — **no** catalogue lookup (`onBarcodeScanned`, `:288`, is the search flow and
     must not run here). Use a separate binding, not `lastScannedBarcode`, because
     `AddFoodSheetView.swift:86-89` triggers the lookup on any change to it.
   - `AddFoodSheetView`: `addCustomFoodItem` (`:230-262`) becomes the prompt / denied / unsupported
     view; the form, rejection-reason section and `addButton` move to the new review screen, pushed
     with `.navigationDestination(isPresented: $viewModel.isReviewPushed)` next to the existing one
     at `:98-104`. The review screen takes the same `AddFoodSheetViewModel` instance (as
     `@ObservedObject`) — `formInput`, submit and editing state already live there; no new VM.
     Remove `nutritionLabelScanButton` (`:265-275`) and the old cover (`:67-71`).
   - Submission confirmation (`isSubmissionConfirmationVisible`) and dismissal stay as they are; the
     alert is attached to the sheet's root view, so move or duplicate it onto the review screen if
     it does not show while that screen is pushed — verify on device.
7. **Strings** (`iOS/Kalorie/Resources/Localizable.xcstrings`, source language `cs`, plus `en`;
   accessors in `Resources/Localization/L10n.swift` next to `:84` and `:126-127`, per ADR 0019):
   prompt title, prompt body ("photograph the nutrition table, then check the values"), denied
   message, "Open Settings" button, unsupported-device message, live hint while searching, shutter
   and barcode-scan accessibility labels. `NSCameraUsageDescription` in `InfoPlist.xcstrings:28`
   already covers nutrition tables — no change.
8. **Docs after the code lands:** rewrite `docs/ARCHITECTURE.md` § 7.5 (the camera flow and three
   screens) and add a pointer in § 2.5 that the nutrition-label camera is a separate scanner from
   the barcode one. Mark this revision's status implemented and add its outcome here.

**Tests to add** (XCTest, `makeSUT`, fakes only; `RecognizeNutritionLabelUseCaseFake` and
`CameraAuthorizationProviderFake`):

- predicate and coordinate conversion (step 1);
- `AddFoodSheetViewModel`: `.notDetermined` + grant → camera opens; `.notDetermined` + refuse →
  `cameraAccess == .denied`, camera stays closed; a successful capture → camera closed, and
  `isReviewPushed` only after `onNutritionLabelCameraDismissed()`; `nothingRecognized` → camera
  still open, hint set, `alertItem == nil`, form untouched; a reading with only a barcode → treated
  as nothing recognised; a live barcode fills `scannedCode` when the still had none, and does not
  overwrite a locked one; capture from the prompt after editing a rejected submission →
  `isEditingSubmission == false`;
- update `test_onSelectRejectedSubmission_prefillsFormAndShowsRejectionReason`
  (`AddFoodSheetViewModelTests.swift:191`) to assert `isReviewPushed`, and
  `test_onModeSelected_afterClosingRejectedSubmissionEdit_…` (`:206`) to assert it is reset;
- one test per moderation VM: success closes the camera and merges without touching filled fields.

**Verify by hand on a device** (nothing automated covers these):

1. `capturePhoto()` resolution and the resulting OCR quality vs. the previous picker photo on the
   deli-tub label from *Outcome* — do this **before** step 6; it decides whether decision 3 holds.
2. Auto-capture fires on a table label and on a linear-format label; it does not fire on an
   ingredients list alone.
3. Barcode side first, then table → barcode filled.
4. Permission: fresh install (prompt → system dialog), deny → Settings message, enable in Settings
   and return → prompt reappears without reopening the sheet.
5. Push after capture actually happens (the `onDismiss` sequencing).
6. Submission confirmation alert shows from the pushed review screen.
- The Foundation Models bonus path was built for real (not stubbed) — see the *Update, 2026-09-13*
  note above for why that was the right call despite having no device to verify it on.

### Outcome

**Update — 2026-09-14.** Implemented per the handoff, in the order given; every step built and the
full suite (including the tests listed above) passed on a simulator build before the next step
started. Nothing in the six on-device checks above has been run yet — no physical device was
available during implementation, same limitation the base design's own *Outcome* section already
hit. They remain exactly as listed, not yet struck off.

One thing the handoff didn't anticipate: `DataScannerViewController.isSupported` is main-actor
isolated in the iOS 26 SDK (`RecognizeNutritionLabelUseCase` and the rest of the base pipeline
never touch it, so this didn't surface until this revision's permission check). Reading it from
`CameraAuthorizationProvider.status`, a plain nonisolated computed property — needed so the many
nonisolated and non-`async` call sites already in `AddFoodSheetViewModel` (`onScenePhaseActive`,
whose scanner tests the handoff required to keep passing unchanged) didn't all have to become
`@MainActor` — uses `MainActor.assumeIsolated`, on the same reasoning `VisionKit`'s own delegate
callbacks rely on: this provider is only ever constructed and read from SwiftUI/view-model code,
which already runs on the main thread. `AVCaptureDevice.authorizationStatus(for:)` carries no such
isolation and needed no bridging.

Two small implementer's-default shapes, not called out elsewhere in this document: the shared
camera-open action (*Implementation handoff*, step 4) is exposed as two protocol-extension entry
points rather than one parameterised function — `onNutritionLabelPromptTapped()` (resets the form,
denial sets `cameraAccess = .denied` inline) and a plain `openNutritionLabelCamera(onDenied:)` used
as-is by the review screen and both moderation screens (denial shows the existing alert) — because
the two denial behaviours decision 2 and step 4 ask for don't share a signature cleanly. And
`FoodItemFormSections`' `barcodeRow` parameter is a three-case enum
(`hidden` / `locked` / `editable(onScanTapped:)`) rather than the two booleans (`isHidden`,
`isLocked`) a first draft used, since the third state needed a closure anyway and one enum reads
better at every call site than two independently-settable flags that are never both true.

**Update — 2026-09-15 — real-device bug: both auto-capture and the manual shutter wedged the
scanner.** The first real run (an oil bottle's linear-format label) reproduced exactly the failure
`isCapturing` was meant to prevent: after taking a photo the screen stayed on the frozen frame with
no hint, no push, nothing — and once that happened, aiming at any label afterward never captured
again either, auto or manual. Root cause: `NutritionLabelScannerRepresentable.Coordinator`'s local
`isCapturing` lock only wrapped the `capturePhoto()` call itself, not the recognition round trip
after it. `capture()` reset the flag (via `defer`) the moment the still image came back — which is
well before `RecognizeNutritionLabelUseCase`'s OCR/Foundation-Models pass finishes and
`isRecognizingNutritionLabel` actually flips to `true` on the view model, since that happens on a
separate, detached `Task` the coordinator fired and forgot about (`onCaptured` was
`(UIImage, String?) -> Void`, called synchronously). A live `didUpdate` landing in that gap saw
`isCapturing == false` and fired a second, overlapping `capturePhoto()` call while the first was
still being processed — `DataScannerViewController` does not document (and evidently does not
handle) concurrent capture requests, and the observed symptom is consistent with that second call
wedging it rather than completing or throwing.

Fixed by making the coordinator await the *entire* round trip instead of a detached fire-and-forget:
`onCaptured` is now `(UIImage, String?) async -> Void`, and `capture()`'s `Task` awaits it directly,
so `isCapturing` — and, symmetrically, an explicit `dataScanner.stopScanning()` before the await and
`try? await dataScanner.startScanning()` after it, rather than trusting the `isProcessing` binding's
own `updateUIViewController` cycle to close the same gap from the outside — now covers the whole
capture-through-merge-or-hint sequence. The three call sites (`AddFoodSheetView`,
`ModerationReviewView`, `ModerationCatalogueEditorView`) lost their redundant inner
`Task { await viewModel.onNutritionLabelCaptured(...) }` wrapper accordingly; the closure passed to
`onCaptured` now just awaits the view model call directly.

**Update — 2026-09-15 (same day, second pass) — the fix above was incomplete, and its own
`stopScanning()` call was most likely the actual trigger.** A second on-device run, this time with
the Xcode console attached, showed the real failure: `capturePhoto()` itself throwing
`AVFoundationErrorDomain` `-11800` ("Operaci nelze dokončit"), immediately followed by a capture
*session* runtime error (`-11819`, "Zkuste to znovu později") — not a hang, a genuine AVCaptureSession
fault. The symmetric `dataScanner.stopScanning()` the first pass added immediately before
`capturePhoto()` is the most likely trigger: `capturePhoto()` is documented to work against the
still-running live session, and issuing it right as the session is mid-transition from an explicit
stop is exactly the shape of failure `-11800`/`-11819` describes. Once the session dies this way,
`startScanning()` cannot revive it — a `AVCaptureSession` runtime error requires a fresh session,
i.e. a fresh `DataScannerViewController`, which is what produced the black screen the user had to
dismiss with the close button: the same, now-broken view controller kept being reused.

Fixed by (1) removing the `stopScanning()` call before `capturePhoto()` — `isCapturing` alone already
fully closes the overlapping-capture race described above, the extra stop was unnecessary and
harmful; and (2) adding an `onCaptureFailed` callback from the coordinator up to
`NutritionLabelCameraView`, which responds by bumping a local `@State` token applied via `.id(_:)` on
the representable — forcing SwiftUI to fully deallocate the dead `DataScannerViewController` and
call `makeUIViewController` again, i.e. a brand new `AVCaptureSession`, plus a brief on-screen message
(`addFood_nutritionLabel_cameraCaptureFailed`) so the failure is visible instead of silent. This is
still the best fix available without being able to attach a debugger to the device myself — the
change is reasoned from the exact error codes logged, not guessed, but has not been re-verified
on-device after this second pass.

**Update — 2026-09-15 (same day, third pass) — removing that `stopScanning()` did not fix it; the
retry itself was compounding the failure.** A third on-device run, same error codes
(`AVFoundationErrorDomain -11800`, then repeated `FigCaptureSourceRemote`/`FigXPCUtilities` errors,
several in a row rather than once). Two things follow from "several in a row": first,
`stopScanning()` before `capturePhoto()` was not the (or not the only) trigger — `capturePhoto()`
fails on this device/session configuration regardless. Second, and more urgent: the second pass's own
recovery was very likely the reason the errors repeated — recreating the scanner via `.id(_:)` after
a failure immediately re-exposes the same live label to the same freshly-`true`
`isCompleteForAutoCapture` predicate, firing another auto-capture, which fails the same way, which
resets again — a fail/reset loop with no user action in between, not the intended one-off recovery.

Two changes, addressing each half separately since the true root cause of `capturePhoto()` itself
failing is still not confirmed:

- **Break the loop.** `NutritionLabelCameraView` now tracks `isAutoCaptureEnabled` (`@State`,
  starts `true`) and turns it off the moment `onCaptureFailed` fires, threading it down to
  `NutritionLabelScannerRepresentable.Coordinator.isAutoCaptureEnabled` (checked in `process()`
  alongside `isCapturing`). The manual shutter is unaffected — `requestManualCapture` never reads
  this flag — so a failure now costs the user one explicit retry tap instead of an invisible loop of
  failing background attempts.
- **Reduce the load on the capture session**, since heavy continuous live analysis competing with a
  photo-capture request is the most plausible reason `capturePhoto()` itself would fail this
  consistently: `qualityLevel` dropped from `.accurate` to `.balanced` and
  `isHighFrameRateTrackingEnabled` from `true` to `false` for this scanner only. Neither affects the
  *captured* still's OCR quality — that still goes through `VisionTextRecognizer` at `.accurate`
  independently in `RecognizeNutritionLabelUseCase` — only how hard `DataScannerViewController`
  works on the live preview while deciding when to auto-fire.

Whether this second change actually addresses why `capturePhoto()` fails at all remains unverified —
plausible reasoning from the error's shape, not a confirmed cause, same caveat as the passes before
it. If the manual shutter *also* fails after this, that would rule out live-analysis contention as
the cause entirely and point at something more fundamental about `capturePhoto()` on this
device/iOS build in this recognizedDataTypes configuration, which no code change here can fix without
a device to iterate against directly.

**Update — 2026-09-15 (same day, fourth pass) — the shutter now works; a second, unrelated UI bug
found.** A fourth on-device run: the manual shutter captured successfully and pushed to the review
screen (some but not all macro fields filled — a recognition-*quality* gap, tracked separately from
this camera-mechanics thread, not yet investigated). Opening the camera a **second** time from the
review screen's own scan button showed a screen with neither the shutter nor the close button
visible — no way to retry, and no way to back out except force-navigating away.

Cause: in `NutritionLabelCameraView`, the `isRecognizing` progress overlay
(`ProgressView` + `.ultraThinMaterial`) was the last, topmost `ZStack` child, so while
`isRecognizing` was true it visually covered the entire screen including the close button — not just
the shutter, which is deliberately disabled during recognition, but the close button, which never
should be reachable-blocked. Whether `isRecognizing` was genuinely (and unexpectedly) still true at
that second open, or the overlay simply flashed at the wrong moment, this stacking was a bug either
way: the close button must never depend on recognition state to be visible. Fixed by moving the
progress overlay above the live scanner but below the controls in `ZStack` order, so the close
button (and the shutter, visible though disabled while recognizing) render on top of the blur rather
than under it.

**Update — 2026-09-15 (same day, fifth pass) — the same "no way out" shape existed on the
*barcode*-only scanners too, pre-dating this revision.** Flagged by the user directly: the § 2.5
barcode scanner embedded in `AddFoodSheetView`'s search tab (`startDataScannerIfPossible`) has never
had a close button — `isScannerVisible` was only ever cleared by a successful scan or by switching
segmented-control tabs, never by an explicit dismiss. The same code, close-button gap included, is
duplicated verbatim in `MyCreatedMealEditorView`. And this revision's own barcode-rescan
`fullScreenCover` (step 6 of the handoff, added fresh in this revision) reused bare
`DataScannerRepresentable` directly and inherited the same gap on day one. Three surfaces, one
missing affordance — not three unrelated bugs.

Decided, per the user's own framing ("X pro dismiss musí být vždy"): every camera surface gets a
close button, unconditionally, and the manual/automatic decision is a **separate, per-scanner**
question, not a variable to unify away — a barcode read is inherently automatic (there is no "photo"
to take, just a payload to detect, exactly like today), while the nutrition-label camera has both
auto-capture and a manual shutter fallback for the reasons decision 3/4 of this revision already
give. Unifying *that* choice into one behaviour would be wrong for barcodes, not just unnecessary.
What genuinely needed unifying was the close button, which had nothing to do with capture mode.

New shared component `BarcodeScannerOverlay` (`Components/`) wraps `DataScannerRepresentable` with
the same close-button treatment `NutritionLabelCameraView` already has (top-trailing
`BaseButton(.close)`, white on the live camera feed) and the existing progress overlay, unchanged.
All three sites — the two inline `startDataScannerIfPossible` copies and the barcode-rescan cover —
now go through it, closing on `viewModel.isScannerVisible = false` /
`viewModel.isBarcodeRescanVisible = false` directly, matching how `NutritionLabelCameraView`'s own
`onClose` already worked. The two inline sites keep their prior (non-full-bleed) layout; only the
`fullScreenCover` site adds `.ignoresSafeArea()`, since that one alone is presented full-screen.

**Update — 2026-09-15 (same day, sixth pass) — the new close button was itself unreachable on the
barcode-rescan screen, hidden under the status bar.** `.ignoresSafeArea()` was applied at the
`fullScreenCover` call site, wrapping the *entire* `BarcodeScannerOverlay` including its close
button — not scoped to the camera preview the way `NutritionLabelCameraView` already (correctly)
does it. With the whole view ignoring the safe area, the close button's `VStack` lost the top inset
that would otherwise have pushed it below the status bar/Dynamic Island, so it rendered right at the
physical top edge, under the system clock/battery — visible but not reliably tappable, per the
user's own report from a photo taken mid-session. Fixed by moving `.ignoresSafeArea()` inside
`BarcodeScannerOverlay` onto the `DataScannerRepresentable` alone (mirroring
`NutritionLabelCameraView`'s existing pattern) and removing it from the call site; the two inline,
non-full-bleed sites are unaffected since ignoring the safe area has no visible effect on a view
that isn't touching a screen edge in the first place.

**Update — 2026-09-15 (same day, seventh pass) — the recognition-quality gap flagged after the
fourth pass ("some but not all macro fields filled") investigated against the real photo that
produced it.** The user's oil-bottle photo (a table-format label, `ve 100 ml` column) surfaced three
independent `NutritionLabelParser` defects, each confirmed against a fixture transcribed from that
photo (`test_parse_realOilBottleLabel_neverReadsANumberOutOfAnUnrelatedLabel`,
`test_parse_energyLabelledWithTheShortCzechWord_stillFillsEnergy`) and fixed:

1. **The `.energy` keyword list only had the long form** (`"energetická hodnota"` / `"energy"`
   etc.), never the plain `"Energie"` / `"Energia"` this label (and plenty of others) use — a
   short form EU 1169/2011 also permits. Every such label filled no energy field at all. Fixed by
   adding `"energie"` / `"energia"` to the list.
2. **A value candidate was accepted by column geometry alone, with nothing checking it actually
   looked like a value.** This label's "z toho nasycené mastné kyseliny" row is printed twice — a
   manufacturer misprint, not an OCR artefact — which shifts every row below it out of alignment
   with the value column by one line; the protein row's nearest-by-x candidate ended up the
   unrelated "Omega 3 mastné kyseliny" line beneath it, and its stray "3" was read as the protein
   value — the exact symptom the user reported ("everything empty, but protein is mysteriously
   3"; the other shifted rows landed on `0`, which `BaseDoubleTextField`'s `zeroSymbol = ""`
   (`ARCHITECTURE.md` § 4.3) renders indistinguishably from empty, which is why only the stray "3"
   stood out). Fixed with `isPlausibleValueText`, checked before a row's candidate is accepted:
   after stripping known units (`g`, `kj`, `kcal`, `ml`, `%`), anything left over that isn't a
   digit, decimal separator, `<`/`≤`, or punctuation fails, so a neighbouring label fragment is
   rejected instead of mined for a digit. Scoped to the table-column pass (`apply(row:)`) only —
   the linear-declaration fallback (`parseLinear`) has the same shape of gap in principle, but a
   fix there risks rejecting text this document's own linear fixtures already rely on (a matched
   keyword's own untranslated continuation, e.g. "z toho nasycené" leaving "mastné kyseliny"
   trailing before its value), and no real photo has yet exercised that path to design the fix
   against. Left as a known residual gap in the fallback, not a blocker for the table-pass fix.
3. **A space-grouped thousands separator split one number into two.** Czech typography prints
   `"3 404 kJ"` for 3404, which both number-parsing routines (`numbers(in:)`, `energyValues(in:)`)
   read as `"3"` and `"404"` separately, losing the leading digit. Any energy value at or above
   1000 — true of most energy-dense foods, oils included — was affected whenever OCR preserved the
   printed gap. Fixed with `joiningThousandsSeparators`, collapsing a space with a digit on both
   sides before parsing; safe unconditionally, since a genuine word boundary never has a digit on
   both sides.

Not yet re-verified on-device against this exact photo — the fixtures above transcribe it, but
nobody has re-run the camera against the physical bottle since these fixes landed.

**Update — 2026-09-15 (same day, eighth pass) — the inline/full-screen split from the base design's
*Surfacing to the user* section is superseded, by the user's own explicit choice.** That section
(and the sixth-pass fix above) treated the two inline barcode scanners — `AddFoodSheetView`'s search
tab and `MyCreatedMealEditorView` — as deliberately non-full-bleed, distinct from the full-screen
nutrition-label camera and the barcode-rescan cover. Asked directly, the user preferred one
consistent presentation everywhere: full-screen with a close button, the same shape the nutrition
label camera already had, specifically because it can be dismissed with one tap on the frame's
scanner rather than needing to switch segments away from it. Both inline sites now present
`BarcodeScannerOverlay` via `.fullScreenCover(isPresented: $viewModel.isScannerVisible)` instead of
an inline `ZStack` overlay; `startDataScannerIfPossible` (both copies) is removed, and its
`isSupported`/`isAvailable` re-check is dropped from the presentation site since it was only ever
guarding the same state the scan button itself already checks before setting `isScannerVisible`
(§ 2.5) — `onScenePhaseActive` still closes the cover on a mid-session permission revocation. No
view-model change was needed: `isScannerVisible` already meant "the scanner should be visible," and
a `fullScreenCover` binding to it is exactly that. Full regression suite (468 tests) still passes,
and a full simulator build succeeds; not yet re-verified on a physical device.

**Update — 2026-09-15 (same day, ninth pass) — the seventh pass's fix did not hold: the same photo
re-scanned still produced "protein = 3, nothing else."** The seventh pass only hardened the
table-column pass (`apply(row:)`); `parseLinear` — the linear-declaration fallback — has the exact
same shape of gap and was explicitly left unfixed at the time ("no real photo has yet exercised that
path"). This report is that photo exercising it. Two fixes were attempted and both were rejected
before landing, for reasons worth recording so they aren't re-tried blind:

- **A fixed-length cap on how far past a keyword a value may be found.** Rejected: the legitimate
  case (`"z toho nasycené"` leaving `"mastné kyseliny"` before its own value, ~24 characters) and
  the failure case (an unrelated `"Omega 3 mastné kyseliny"` row, as close as ~20 characters when a
  field ends up last-matched) overlap in distance. No single cutoff separates them.
- **Skipping words that are wordy but not the field's own keyword.** Rejected on the same
  fixture: `"mastné kyseliny"` ("fatty acids") is generic vocabulary shared by the saturates row
  *and* the omega rows — tokenised word-by-word, neither `"mastné"` nor `"kyseliny"` alone
  identifies which row it belongs to. No local, geometry-free heuristic over this text can tell
  them apart.

The likely underlying mechanism (reasoned, not confirmed — see the diagnostic below): this label
**is** a genuine table (`perHundredColumnX` would find its `"ve 100 ml"` header), so `parseLinear`
should never be the path filling it at all. If Vision's real on-device line order groups the label
column and value column as two separate contiguous blocks — plausible for two text blocks this far
apart horizontally, and unverifiable from a screenshot — `groupIntoRows`' vertical-overlap grouping
never pairs a label with its value (they'd never appear adjacent in the sorted-by-midY sequence the
way this document's own fixtures assume), the table pass fills nothing, `hasNoMacros` is true, and
`parseLinear` then reads what is structurally a two-column table as if it were one linear sentence —
an input shape its own design assumption ("a value is stated immediately after its keyword") does
not hold for. Tightening the trigger condition (only fall back when `perHundredColumnX` finds no
header at all) was considered and rejected without landing it: `isPerHundredHeader`'s substring
check already fires on the existing linear fixtures' own intro line ("Výživové údaje **na 100 g**:
Energetická hodnota…"), so gating on it as written would break
`test_parse_linearFormatLabel_fillsMacrosViaTheFallback` — the header check itself is too loose to
use as a discriminator without its own fix, which is out of scope for this pass.

No further blind attempt was made. Added instead: a `#if DEBUG`-only `print` of every recognised
line's text and bounding box in `RecognizeNutritionLabelUseCase`, console-only (never persisted,
uploaded, or reaching the production `Log.warning` path, which stays counts-only per design's own
*Logging* decision) — the fastest way to replace this section's guesswork with the actual on-device
Vision output next time this label is scanned. Blocked on that data before attempting a further fix.

**Update — 2026-09-15 (same day, tenth pass) — root cause found from that console log: the still
photo was fed to Vision with the wrong image orientation.** The user re-scanned the same bottle and
sent the `[NutritionLabelOCR]` output. Every bounding box came back narrow-and-tall (width
~0.01–0.03, height up to ~0.26) instead of the wide-and-short shape a horizontal text line normally
has, and the label-column lines (`"Bilkoviny |"`, `"Sůl | Sol"`, …, y ≈ 0.35–0.44) and the
value-column lines (`"ve 100 ml"`, `"92 g"`, …, y ≈ 0.68–0.73) landed in two disjoint Y-bands with no
overlap at all — confirming the ninth pass's reasoned-but-unconfirmed hypothesis exactly: Vision was
reading the photo sideways. Root cause: `RecognizeNutritionLabelUseCase.callAsFunction` called
`image.cgImage` and handed that raw `CGImage` to `VNImageRequestHandler(cgImage:options:)` with no
`orientation:` — a `UIImage`'s `cgImage` carries only the sensor's raw pixels, never the
`UIImage.imageOrientation` the wrapper stores separately, so Vision analysed the unrotated sensor
frame while a human (and the live preview) saw it corrected. With the geometry effectively
transposed, `groupIntoRows`/`perHundredColumnX` could never pair a label with its value — the table
pass filled nothing, `parseLinear` took over on an input whose column-major shape it isn't built for,
and a diacritic misread (`"ů"` → `"ủ"`, plausibly itself a consequence of reading the image rotated)
made `"Sůl"` miss every salt keyword, leaving protein as the last-matched field and its window
running unbounded into `"Omega 3 mastné kyseliny"` — the ninth pass's mechanism, now confirmed
end-to-end rather than reasoned from a screenshot.

Fixed at the source: `TextRecognizerProtocol.recognizeText(in:orientation:)` and
`BarcodeDetectorProtocol.detectBarcode(in:orientation:)` now take a `CGImagePropertyOrientation`,
`VisionTextRecognizer` passes it straight into both `VNImageRequestHandler` calls, and
`RecognizeNutritionLabelUseCase` derives it once from `image.imageOrientation` via a small
`CGImagePropertyOrientation.init(_ uiOrientation:)` mapping (there is no built-in conversion in the
SDK). None of this reaches `NutritionLabelParser` — the eighth/ninth-pass fixes there stand, and are
expected to matter far less now that the table pass should actually see correctly-oriented geometry;
`parseLinear`'s column-major vulnerability the ninth pass documented is unresolved in principle but
should no longer be reachable by a genuinely tabular label once orientation is correct. Covered by
`test_cgImagePropertyOrientation_matchesEachUIImageOrientationOneToOne` (all 8 cases); full suite
(469 tests) passes and a full simulator build succeeds. Not yet re-verified against the physical
bottle — that is the next on-device check, and the one that actually settles whether this was the
root cause or one of several.

**Update — 2026-09-15 (same day, eleventh pass) — orientation was fixed, but the deeper geometry bug
was found from the corrected console log.** The user re-scanned with the tenth pass's fix in place
and sent a second `[NutritionLabelOCR]` log: bounding boxes were now correctly wide-and-short (the
orientation fix worked), but the UI still showed the wrong values — `Tuky` filled as `7`, energy
untouched. Hand-tracing `groupIntoRows` against the exact transcribed coordinates found the actual
root cause: `verticallyOverlaps` compared each candidate line against the **union** of every line
already accumulated into the row, not against the row's first (seed) line. On this label, `"Energie
| Energia"` and its value line overlap enough to merge, and that merge grows the row's bounding box
tall enough to *also* satisfy the overlap threshold against `"92 g"` (the unrelated `Tuky` row's
value, sitting just below) — a transitive/creeping merge, not a one-off coincidence: once a row's
box has grown from merging two lines, it can go on to swallow a further line that only overlaps the
*enlarged* box, never the original seed. With `"92 g"` absorbed into the energy row, `"Tuky"` was
left to pair with whatever came next by the same flawed logic — `"7 g"`, the saturates row's value —
which is exactly the bug the user reported.

Fixed by comparing each candidate against `row.first` (the row's original seed line) instead of the
row's accumulated min/max — traced by hand against the transcribed real coordinates that this
correctly separates every row on this label, including the pathological case above. Covered by
`test_parse_realOilBottleLabelWithCorrectedOrientation_pairsEachRowWithItsOwnValue`, built from the
exact bounding boxes in the user's second log, asserting `fat == 92` (not `7`) among the rest. Full
suite (470 tests) passes; existing fixtures were unaffected because none of them has a row with more
than two lines, where anchor-vs-union grouping can differ.

Two things this pass does **not** fix, both visible in the same trace and left as known limits:

- **The duplicate `"z toho nasycené mastné kyseliny"` printed row still overwrites saturates with
  0** (the same manufacturer misprint the seventh pass already named) — this parser has no way to
  know which of two identically-labelled rows is the real one; a later `apply(row:)` call for the
  same field always wins over an earlier one. Unrelated to and not caused by this pass's fix.
- **Salt stays empty**, not because of a parser bug but because Vision itself misread `"Sůl"` as
  `"Sül"` in this photo (a different diacritic than the ninth pass's `"ủ"` misread, from the same
  underlying OCR difficulty) — none of the `.salt` keywords match, so the row is correctly skipped
  rather than guessed. Whether this specific misread recurs on a straighter/closer photo is
  unverified.

Also reported by the user: auto-capture fires more readily when a finger covers the manufacturer
text just below the table than when the whole label is visible. Not investigated directly, but
plausible under the same root cause: `isCompleteForAutoCapture` runs this same row-grouping
live, per frame, and the extra `Omega 3`/`Omega 6` rows visible below the mandatory table give the
old accumulate-and-swallow bug more lines to mis-merge with — occluding them removes exactly the
lines that were interfering. Expected to improve as a side effect of this fix rather than needing
its own change; not confirmed on-device.

**Update — 2026-09-15 (same day, twelfth pass) — parsing now works; submitting from the review
screen silently did nothing.** With the eleventh pass's fix in place, the user's next capture parsed
correctly (`fat: 92`, `fat_saturated: 7`, the rest genuinely `0` for this oil) and the Xcode console
showed the Firestore write actually succeeding (`✅ SET … → foodItemSubmissions`) — but the app stayed
on the review screen with no visible confirmation, looking stuck. Not a validation problem:
`FoodItemValidation` (§ 2.6) never checks any macro field, only id/name/calories/weight/portions, all
of which this item satisfies; the write succeeding at all is proof validation already passed.

This is the *Implementation handoff*'s own flagged, unverified risk materialising: "the alert is
attached to the sheet's root view, so move or duplicate it onto the review screen if it does not
show while that screen is pushed — verify on device." It didn't. `AddFoodSheetViewModel.onCreateFoodItem`
correctly set `isSubmissionConfirmationVisible = true` on success, but the
`.alert(L10n.AddFood.submissionSubmitted, isPresented:)` modifier was attached to the root
`NavigationStack`'s content — a sibling of `.navigationDestination(isPresented: $viewModel.isReviewPushed)`,
not the pushed `newItemReviewView` itself, where `addButton` (the only caller of `onCreateFoodItem`)
lives. Fixed by moving that `.alert` onto `newItemReviewView`. `onSubmissionConfirmationDismissed()`
(`isSubmissionConfirmationVisible = false; shouldDismiss = true`) and the root's own
`.onChange(of: viewModel.shouldDismiss)` were left as they are — `onChange` has no equivalent
topmost-view presentation requirement, so dismissing the whole sheet from a pushed screen was never
the broken half. Full suite (470 tests) still passes.

**Confirmed working end-to-end, 2026-09-15.** The user re-tried the full flow after this fix — the
confirmation now shows and the sheet dismisses. This closes the thread the tenth/eleventh/twelfth
passes traced from a single real photo: wrong image orientation → broken row/column geometry → the
review screen's confirmation alert never visible. All three were independent bugs that happened to
surface from the same bug report; nothing about them was actually related beyond timing. The ninth
pass's `#if DEBUG` diagnostic print in `RecognizeNutritionLabelUseCase` (added to get the real Vision
output that found the tenth/eleventh pass's root causes) has been removed now that it served its
purpose — the production `Log.warning` path it sat beside is untouched and still counts-only.

Residual, not blocking: the duplicate-printed-row and `"Sůl"`-diacritic-misread limitations named in
the eleventh pass are about this specific label's own printing/OCR quirks, not about the app, and
remain exactly as described there. The Foundation Models bonus path (§ *Foundation Models*) is still
unverified on a real device — everything confirmed in this thread was the deterministic parser and
the surrounding camera/UI flow.
