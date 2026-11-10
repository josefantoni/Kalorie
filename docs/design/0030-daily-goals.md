# Design: Daily goals

- **Status:** Draft
- **Scope:** Cross-platform
- **Date:** 2026-11-06

## Context and scope

The app shows only totals: the day's kcal and the three macros. User feedback asks for what calorie tables
show ("2 100 of 3 140 kcal, 120 g of 168 g carbs") against a goal the user sets. This document adds an
opt-in daily goal (kcal plus macros) with a history, an optional calculator that suggests a kcal figure, a
progress ring on the Dashboard, per-macro progress bars, and the knock-on changes to storage, the anonymous
merge, account deletion, the privacy policy and the store forms.

This is the first goal in the app. `ARCHITECTURE.md` § 3.x (the macro summary section: "There are no goals or
targets in the app") MUST be replaced when this ships.

It touches the Firestore contract (one new settings document), a new shared KMP function, the Dashboard
macro summary and donut, a new pushed screen, the Settings sheet, `MigrateAnonymousDataUseCase`,
`DeleteAccountUseCase` and `backend/hosting/privacy.html`. Both clients implement it 1:1
([ADR 0038](../adr/0038-android-client-mirrors-the-ios-architecture-natively.md)).

Read with: `ARCHITECTURE.md` § 1.2–1.6, § 3.5–3.9;
[design 0004](0004-shared-macro-calculation-module.md),
[design 0021](0021-privacy-policy.md),
[design 0028](0028-body-weight-tracking.md) (weight circle, `settings/app`, "valid on a day" lookup,
anchored popover). This document **departs from design 0028** in one place, see *Calculator weight*.

## Goals

- A user can set a daily kcal goal and macro goals, by typing them or by taking a calculator's suggestion.
- The Dashboard shows progress against the goal valid on the selected day, past days included.
- The feature is opt-in and never nags: no spotlight, no proactive prompt.
- Goals follow the account across devices and keep a history.
- The user can hide the goal UI without losing the stored goal.

## Non-goals

- A goal weight, a deadline, and adaptive expenditure from the weight trend.
- A per-day "training today" switch (~300 kcal); one goal applies to every day.
- Micronutrient goals.
- Judgement of any kind: no green/red, no praise or blame wording.
- Offline behaviour; the app is online-only.

## Design

### Goal model

A goal is daily kcal plus macros.

- **Macro mode.** The user switches how macros are set:
  - *split*: carbs / protein / fat in %, e.g. 50/20/30, also shown in grams at 4 / 4 / 9 kcal per gram.
    Changing the kcal goal rescales the grams. The three MUST sum to 100; otherwise the same red text under
    the field as the existing input validation, no alert, and Save is disabled.
  - *grams*: carbs / protein / fat in grams directly (a trainer's "160 g protein"). Changing the kcal goal
    leaves them as they are; changing a gram value moves the kcal goal to the grams' kcal.
- **Switching mode:** split → grams converts with the current kcal goal, each macro rounded to whole grams.
  **The rounding is accepted, not a bug:** 2000 kcal at 20/50/30 becomes 100/250/67 g, which is 2003 kcal. The
  kcal goal stays as typed (so it still matches a slider variant) and the info line below ignores a difference
  of up to 10 kcal, the most whole-gram rounding of three macros can cause (8.5). The first slider move after the
  switch rescales from those rounded grams, up to 1 g off the entered split, because the anchor starts new
  after a mode switch. Grams →
  split converts each macro's share of the grams' own kcal, rounded to whole percent with the largest
  remainder adjusted so the sum is exactly 100.
  The switch is rejected while one or two of the three macros are filled (a split cannot be converted from
  a partial set), a switch to grams is rejected while the kcal goal is empty (there is nothing to scale
  the percent by), and a switch from split to grams is rejected while the three percents do not sum to 100
  (the grams would cover less or more than the kcal goal without the user having typed that). The control
  snaps back and a red caption under the new daily kcal goal row asks for the missing input ("fill in all
  three macros" / "enter the kcal goal first" / "the percentages must add up to 100"); it disappears as soon
  as the input is there and does not come back unless the user tries to switch again. The checks run in that
  order. The caption sits under the kcal row, not under the control. When the switch is rejected
  because the kcal goal is empty, the kcal field is focused and scrolled into view, so the caption stays
  visible above the keyboard; the other two rejections do not move the focus.
- **Grams mode with grams' kcal ≠ kcal goal:** allowed, which happens after the user types a kcal goal
  that differs from the grams by more than the rounding of whole grams (see *Switching mode*). The screen then shows the grams' kcal sum as a neutral info line, only while it
  differs from the kcal goal by more than 10 kcal (`MacroKit.gramsKcalDiffersFromGoal`) and the kcal goal is
  filled. The ring
  uses the kcal goal; each macro bar uses its own gram goal.
- **Goal history.** Each saved goal is valid from the day it was saved. A past day browsed on the Dashboard is
  measured against the goal valid on that day (the same lookup as weights in design 0028). Days before the
  first goal show no goal. A second save on the same day replaces that day's entry. A save also drops every
  entry dated after the day it is saved for: such entries exist only after the device date moved back (time
  zone travel, a wrong clock), and keeping them would let the older save override the newer one the next day.

### Calculator (optional helper)

Fields: sex, birth year, height, activity level; weight is typed on this screen (see below). The result is
recomputed every time the screen opens and is never stored: only the goal the user saves is, so a later weight
change does not move the saved goal.

- **Formula.** Mifflin-St Jeor: `10·kg + 6.25·cm − 5·age + 5` (male) / `− 161` (female), times the activity
  factor. Pure functions, so they live in `MacroKit` with their JVM tests and are shared by both clients; no
  copy of the rules in Swift or Kotlin
  ([ADR 0039](../adr/0039-swift-only-rules-move-into-kmp-or-share-golden-vectors.md)). `Energy.kt` holds the
  formula, the 4 / 4 / 9 kcal per gram conversions and the energy shares used by the donut; `Goals.kt` holds
  the activity factors (`ActivityLevel`), the five variants with the BMR floor (`goalVariants`), the reference
  intake of *Prefill* (`referenceKcal`) and the rounding of a split to 100 (`roundedToHundred`). `ActivityLevel`
  and `GoalVariantKcal` are MacroKit's own types, not domain types or DTOs, so they stretch ADR 0039
  decision 1 (primitives only); each client maps its own activity enum onto `ActivityLevel`.
- **Sex** has two values for the formula's sake; the field is worded "used for the estimate". It defaults
  to male.
- **Age** is derived from a stored birth year (a stored age goes stale). The picker allows ages 18 to 99, matching the 18+ policy of [design 0021](0021-privacy-policy.md).
- **Activity levels** (a picker with descriptions, not bare factors): sedentary 1.2, light 1.375 (training
  1–3×/week), moderate 1.55, high 1.725, very high 1.9 (physical job plus training). **Light is preselected**
  (sedentary job, training 1–3× a week), the same assumption as the reference intake in *Prefill*. The result
  appears once birth year, height and weight are set (sex and activity have defaults).
- **Variants.** One slider with five snap points, left to right: losing −10 % / −5 %, maintaining, gaining
  +5 % / +10 % of daily expenditure. The labels sit above the slider. Moving it writes the variant's kcal into
  the *Daily intake* field at once (in grams mode the grams are rescaled to the same split, always from the split they had when the slider
  was first moved, so repeated moves do not accumulate rounding; editing a macro or switching the mode
  starts a new split). Changing the activity level applies the variant the slider shows again to the new
  estimate (also when it was reached by typing a kcal equal to a variant, or by reopening a saved goal; the slider then stays on the last matched variant even after a macro step or a hand-typed number breaks the match), so the kcal goal, and the grams in grams mode, follow the new activity at the same percentage
  (+5 % stays +5 %). Changing the sex does the same: it is an input of the estimate like the activity level, so the
  kcal goal is replaced by the new estimate at the slider's variant, even over a number typed by hand, and in
  grams mode the grams are rescaled to the same split (the macros only go up or down, never re-split). The labels are
  highlighted only while the field equals a variant; a number typed by hand matches none. Percentages scale with the person. Losing offers only small steps on purpose (cutting too hard too early leaves nowhere to cut later;
  the user lowers the goal again if the weight stops falling); a larger change is entered by hand.
- **BMR floor.** A variant never drops below the BMR (the row says so). With the current variants this cannot
  trigger (lowest factor 1.2 × 0.9 = 1.08 × BMR); it is kept as a safeguard.
- **No "Use" step.** An earlier draft copied a variant into the goal field only after a "Use" button, so the
  goal was never overwritten silently. Dropped: the slider sits directly under the field it drives, nothing is
  saved until Save, and the Save button stays disabled until something changes.
- **Sanity check** (owner, 35, male, 188 cm, 92 kg, desk job, gym 3×/week): BMR ≈ 1 925 kcal; × 1.2 ≈ 2 310,
  × 1.375 ≈ 2 650. Real-life figures are ~2 200 on a rest day and ~2 500 on a training day, so the formula
  lands ~5 % above. Acceptable for an estimate; the UI says it is only an estimate.

#### Calculator weight (departure from design 0028)

Design 0028 makes `bodyWeights` the single source of the user's weight. This design departs from that in one
place: the weight is typed on the goals screen and stored in the goal entry (see *Storage*), because a past
day must show what was true when the goal was set. `bodyWeights` stays the daily weight shown in the chart.

- If the weight was edited on this screen and `bodyWeights` has no entry for today, saving also writes the goal
  weight as today's entry (through design 0028's `SaveBodyWeightUseCase`). An existing entry for today is not
  overwritten, and a weight that was only prefilled (from the last goal or from the latest body weight) is never
  logged as a new measurement. "Edited" is compared in the tenths the picker displays, so scrolling the wheel
  away and back to the same value is not an edit.
- The picker's 70 kg default is only a starting position for the wheel. **Saving it untouched is valid:** the goal
  stores it as entered, and it is not logged. **Touching the wheel is a choice:** once the user has moved it, the
  value counts as edited and is logged even when the wheel ends on 70 kg again. A prefilled value the user
  confirms with Save is their decision and is respected, never second-guessed.
- The field is prefilled with the latest weight valid today when the goal has none.
- The field follows the kg / lb setting of design 0028; stored in kg, never rounded.

### Storage

A new document `users/{userId}/settings/goals`, `snake_case` wire names, containing a list of goals and
nothing else.

Not on `users/{userId}` itself: the sign-in use cases replace that document with `setAsync`, and an anonymous
user has none (placement note in design 0028). Not in `settings/app` either: the anonymous-to-account merge in
design 0028 writes the anonymous `settings/app` only if the account has none, so a goal set before signing in
would be dropped whenever the account already has any setting. `settings/goals` gets the same rule on its own
(written only if the account has no `settings/goals`), at the cost of one more read.

**`settings/goals`**

| Wire name | Type | Meaning |
|---|---|---|
| `goals` | array of maps | newest last; the list is the whole document |

**Entry** (a complete record of the goal as saved; every field optional on read):

| Wire name | Type | Meaning |
|---|---|---|
| `valid_from` | string `yyyy-MM-dd` | day key (design 0028 construction), first day the entry applies |
| `kcal_goal` | number | daily kcal; **absent = goal ended** from `valid_from` |
| `macro_mode` | string `"percent"` / `"grams"` | which set below is authoritative |
| `carbs_pct`, `protein_pct`, `fat_pct` | number | split, in percent |
| `carbs_g`, `protein_g`, `fat_g` | number | grams |
| `sex` | string `"male"` / `"female"` | calculator input |
| `birth_year` | number | calculator input |
| `height_cm` | number | calculator input |
| `weight_kg` | number | calculator input |
| `activity_level` | string `"sedentary"` / `"light"` / `"moderate"` / `"high"` / `"very_high"` | calculator input |

- Every calculator input is stored with the entry because any of them changes over time (three hectic months,
  then home office) and a past day must show what was true then. A goal typed by hand is saved the
  same way (see "Saving"), so it carries whatever the calculator inputs showed when it was saved. Calculator
  inputs are saved only as part of a goal, never on their own.
- The list stays in one document because goals change rarely (a few entries a year, far from the 1 MiB limit)
  and Save rewrites the document anyway; no subcollection, no extra reads.
- **Absent document = no goal.** An unknown value of `sex` or
  `activity_level` reads as absent. An entry whose `macro_mode` is present but unknown or
  not a string, or whose `kcal_goal` is present but not a number, is skipped as a whole, because the macros
  or the goal it describes cannot be interpreted and reading it as percent mode or as an ended goal would
  hide the previous entry; the previous entry then applies. An explicit `null` counts as absent, so a
  `null` `kcal_goal` is still an ended goal. Any other wrong-typed field reads as absent.
- **Writes merge** through `mergeAsync` (design 0028), so a top-level field a future version adds is not
  erased by an older client. The `goals` array is rewritten as a whole, so an unknown field inside an entry,
  or an entry skipped as unreadable, is lost on the next Save; goals change a few times a year, so this is
  accepted. Two Saves from two devices within one round trip are last-write-wins: the second
  rewrites the array it read before the first landed, so the first device's entry is lost. There is no
  transaction API in the data provider and the window is a few hundred milliseconds, so this is accepted too.
- **Security rules.** `users/{userId}/{document=**}` is already owner-only; no rule change. A
  `firestore-rules-tests` case for `settings/goals` SHOULD be added.

### Visibility switch

The goal circle is a permanent element for someone who never wants a goal, so Settings gets a switch next to
the weight one (`show_body_weight` in `settings/app`, design 0028): "Daily goal". Off hides the circle, its
"i" badge, the "/ goal" values, the bars and the "x % of goal" line, and keeps the stored goal. One switch per
optional element, not one per line; the donut is not optional.

- New field `show_daily_goal` in `settings/app`, default `true` when absent. The circle is static, nothing
  blinks or pops up, so the user is never pushed to set a goal.
- The no-flicker and "only after a successful read" rules of design 0028 apply unchanged.
- The switch is labelled *Daily goal*.
- Two further switches in the same Settings section refine what stays visible while the goal is on:
  `show_goal_percent` (the "x % of goal" line) and `show_goal_bars` (the progress bars under the macro
  values). Both default to `true` when absent and are disabled while *Daily goal* is off.

### Dashboard

#### Circle

A leading twin of the weight circle from design 0028: the same 72 pt / 72 dp slot mirrored on the left of the
pie, so the pie stays centred. Rendered only on a selected day with food entries, like the weight circle. A tap
on the circle always pushes the goals screen. Without a goal the circle shows the text "Set goal" (no number,
empty track). On a past day with no goal valid on it the circle is not shown at all: a goal is
always saved from today, so offering to set one for a day that has passed makes no sense.

#### "i" badge

A separate control on the circle's bottom-leading edge, the outer side away from the pie (owner decision,
variant A over the pie-side corner). Hit area 44 × 44 pt / 48 × 48 dp, which does not reach the pie, as design
0028 requires of the weight badge. Opens the same anchored box as the weight badge and the section copy
(popover on iOS, `DropdownMenu` on Android).

- Without a goal: what the goal is and a "Set goal" button into the goals screen.
- With a goal: eaten / goal / remaining (or "over by") in kcal and an "Edit goal" button.
- Neutral wording, no praise or blame.

#### Ring

A progress ring like the Apple Watch activity rings: starts at 12 o'clock, runs clockwise, progress = day kcal
÷ kcal goal. Past 100 % a second lap is drawn over the first (the "coiled snake") with a soft shadow on the
head so the overlap reads; laps are capped (e.g. 3) while the percentage keeps counting. Round caps, faint
track. The stroke is coloured by macro, like the donut: each macro covers an arc of
`macro kcal ÷ kcal goal` up to a full lap (shares from `EnergyKt.energyShares`, normalised to the macro-kcal sum, order fat,
carbs, protein), so the first lap, filled up to the day's progress, is the donut unrolled; every further lap repeats the
same sequence from 12 o'clock over the previous one. No colour changes with progress: passing 100 % is a second lap, not a failure. Segments use butt caps; only the very
start and head are rounded. With no macro energy the stroke is neutral grey.

- Centre text: the percentage only (`108 %`); the goal in kcal does not fit in the ~48 pt inside the stroke
  and is shown in the "i" popover.
- Does not animate: the ring and the percentage jump to the new value (day switch, save).
- Drawn with a custom `Canvas` on both clients, as the macro donut and design 0028's charts are.

#### Macro summary

Each of the three macro labels gains "/ goal" next to its value and a thin progress bar below it once a goal is
set. The bar stays full past the goal, the number keeps growing, and "x % of goal" keeps counting past 100 %
(e.g. 140 %). Under the bar one small line, "x % of goal": the bar's progress. In split mode it is
identical in grams and in kcal, because the gram goal is derived from the kcal goal; in grams mode it is the
grams against the gram goal. The macro's share of the day's energy is not repeated as a number: the donut
shows it.

#### Donut shows energy, not grams (owner decision, from a user's feedback)

Today `MacroDonutView` computes each slice's percentage from grams (`value / total` over g), so 12 g of fat
reads 29 % while it is ~48 % of the energy. The goal is an energy split ("max 30 % of energy from fat"), so the
donut slices and percentages become kcal shares (4 / 4 / 9 per gram) on both platforms, with and without a
goal.

- Slices run clockwise from 12 o'clock in the order fat, carbs, protein.
- This changes what every user sees today. The "i" popover explains it in one sentence; grams stay as the
  numbers under the labels.
- The share computation is a pure function and lives in `MacroKit` (`energyShares`, with JVM tests).
- Two donuts or two bar sets (grams and energy) were rejected: the bars would be identical and a second donut
  does not fit.

### Goals screen

Pushed from the circle on the Dashboard's `NavigationStack`. One list with sections, displayed in the order
2, 3, 1 (the numbers are kept because other parts of this document refer to them; the goal comes last so the
inputs read top to bottom into the estimate slider and the fields it drives):

1. **Current daily goal**: *Daily intake* (kcal), the *Split | Grams* switch and protein, carbs and fat, each
   a typed field with `−` and `+` buttons beside it (1 % in split mode, 5 g in grams mode; split mode also
   shows the grams). This is what is actually saved. The intake must be 500–10 000 kcal and, in split mode,
   the percentages must add up to 100, or Save stays disabled.
   - *Prefill.* With no active goal the intake is prefilled with a reference value by sex (as soon as the estimate
     exists, whether from an earlier goal's profile or because birth year, height and weight were just filled in,
     an untouched prefill is replaced by the estimate at the variant the slider shows, *maintaining* by default; a
     number the user typed is kept): 2 400 kcal (male) or
     1 900 kcal (female), the Mifflin-St Jeor estimate at light activity for a 30-year-old 178 cm / 80 kg man
     and 165 cm / 65 kg woman, rounded to 100. It follows the sex switch while the estimate is not yet
     available; once it is, a sex switch applies the slider's variant (see *Variants*). The split starts at 50 / 20 / 30.
   - *Grams mode.* Changing a gram value moves the intake to the grams' kcal; the intake can still be
     typed afterwards.
   - *Estimate.* Once birth year, height and weight are set (sex and activity always have a value), the section shows BMR, daily
     expenditure and the variant slider between the intake field and the split switch, plus one line "It is
     only an estimate, actual expenditure may differ." (no percentage, it would read as one of the ±10 %
     variants). Until then a hint says which fields are missing.
2. **Your body data**: sex (a *Male | Female* switch, male by default), and birth year, height and weight as
   rows with the up/down chevron that open a wheel popover, the same picker as the weight badge on the
   Dashboard (weight in kg or lb per design 0028, height in cm or ft/in). A field that is not set yet opens
   on a default: birth year 30 years back, height 178 cm (male) / 165 cm (female), weight the latest weight
   valid today or 70 kg.
3. **Your weekly activity**: one slider with five snap points (sedentary … very high), with the captions
   "Less active" and "More active" above the ends, like the labels of the estimate slider. A level is always selected, "Light" when the user has not
   chosen one (see *Calculator*), so the slider never has an "unset" position. Below the slider the selected
   level shows its name and description,
   e.g. "Light" and "Desk job, training 1–3× a week". The factors are not shown. The collapsed summary line is
   the level name only.

Sections 2 and 3 are collapsible once their values are known from an earlier entry (section 2: sex, birth year,
height and weight; section 3: activity). They then start collapsed, and a chevron badge in the header expands or
collapses them. A collapsed section shows one summary line with its stored values (e.g. "Male · 1990 · 178 cm ·
80 kg"). The chevron rotates from pointing down (expanded) to pointing left (collapsed). Without stored
values the sections are open and have no chevron.

Each section header has an "i" button, the same glass circle as on the Dashboard's meal sections, that opens
a popover explaining what the section is for.
5. **End goal**: a destructive `PrimaryButton` at the bottom of the list, behind
   a confirmation alert that says what happens (tracking stops from today, past days keep their goal, nothing
   is deleted); it is disabled, and its confirmation ignored, while a save is in flight. It appends an entry valid from today with no `kcal_goal`; the circle returns to blurred from
   today on. The goal history and the weight history stay (account deletion still removes everything). A
   returning user who wants to lose weight does not need it: they update the activity (the slider's variant is
   applied to it at once) and, if needed, move the slider, then "Save".

**Saving.** A "Save" button in the navigation bar, disabled until an input differs from what the screen opened
with (the weight prefilled from the latest entry does not count), writes the goal in section 1 together with the calculator
inputs as filled in, as one new entry (not a write per field). The slider only fills the goal field and
saves nothing. If the weight was edited and today has no body weight entry, Save also writes the goal weight as today's entry. A goal typed by hand (e.g. from a trainer) is saved the same way. The keyboard's "Done" only
dismisses the keyboard. First run uses the same screen, not a wizard.

### Anonymous user and the merge

The anonymous user has the full feature. `MigrateAnonymousDataUseCase` (both clients) captures the anonymous
`settings/goals` with the existing collections into `PendingMergeSnapshot`, and replays it only if the account
has none, after reading the target's `settings/goals` first. The snapshot's new field MUST decode as absent →
`nil` (`decodeIfPresent` on iOS, default value on Android), as design 0028 requires, or a pending merge from
an older release never finishes. Re-run is idempotent for the same reason as settings.

### Account deletion

`DeleteAccountUseCase` on both clients deletes `settings/goals` with the other settings documents, before the
profile document. The web deletion page ([design 0015](0015-account-deletion-web-page.md)) is unaffected if it
defers to the same wipe; check this when implementing.

### Localisation

All new strings (circle, badge, popover copy, goals screen, activity descriptions, validation text, end-goal
alert, the setting) in Czech, English and Slovak through the shared `localisation/strings.json`
([design 0027](0027-slovak-localisation.md)).

### Privacy policy and store forms

Body measurements (sex, birth year, height, weight) are a new data category.

- `backend/hosting/privacy.html`: a bullet in *Co ukládáme a proč* (every language on the page) for the goal
  and the optional body data used for the estimate; entered only by the user, optional, deleted with the
  account.
- **Play Console → Data safety** and **App Store Connect → App Privacy**: body weight is already declared
  under Health (design 0028); verify that sex, birth year and height fall under the same Health / Fitness
  declaration, and re-confirm the forms. Manual, owner.
- The consent position of design 0028 (no explicit-consent step) applies unchanged; revisit both together.

## Alternatives considered

- **Fields in `users/{userId}`.** Erased by every sign-in's full-overwrite profile write and absent for
  anonymous users. Rejected.
- **Fields in `settings/app`.** The merge rule "written only if the account has none" would drop a goal set
  before sign-in whenever the account has any setting. Rejected for `settings/goals`.
- **A `goals` subcollection with one document per entry.** More reads per Dashboard load for a handful of
  entries a year. Rejected for one document.
- **A spotlight or other prompt to set a goal** (design 0017 reuse). A goal is a personal choice; a static
  circle that says "Set goal" is enough and nothing pops up. Rejected.
- **Reading the weight from `bodyWeights` only.** A past day's goal must show the weight it was computed
  from, and a later weight change would otherwise silently rewrite history. Rejected; see *Calculator weight*.
- **Storing the calculator result.** Would let a weight change move the saved goal. Rejected; only the goal
  the user saves is stored.
- **Two donuts or two bar sets (grams and energy).** Identical bars; a second donut does not fit. Rejected.
- **A per-field visibility switch.** One switch per optional element, not per line.
- **A chart library for the ring.** Same reasoning as design 0028: `Canvas` on both clients.

## Cross-cutting concerns

- **Privacy:** see above. Body data is optional and user-entered.
- **Migration of existing data:** none; an absent `settings/goals` means no goal. The donut change is visible
  to every user the day it ships.
- **Online-only:** no offline behaviour is designed.
- **Health claims:** the estimate is labelled as an estimate; no medical wording.

## Risks

- **Donut change surprises existing users** → the "i" popover explains it in one sentence.
- **Calculator drifting between clients** → the formula lives once in KMP, pinned by golden vectors.
- **Goal-mode edge cases** (switching mode, grams' kcal ≠ kcal goal) → proposed rules above; confirm before
  implementation.
- **Snapshot decode** → a regression strands a pending merge permanently; keep the backward-compatible decode.
- **Ring centre text on small screens and large text** → verify on iPhone SE (375 pt) and a 360 dp Android at
  the largest text size.
- **A weight written by the goal save** → only when today has no entry; covered by a use-case test.

## Implementation checklist

- [ ] KMP: `EnergyKit` / `MacroKit` additions (Mifflin-St Jeor, kcal share) with golden vectors
- [ ] `GoalsDTO` / domain, "valid on a day" lookup — iOS + Android
- [ ] Use cases: fetch goals, save goal, end goal; save-weight reuse
- [ ] Settings: `show_daily_goal`
- [ ] Dashboard: goal circle, ring, "i" badge and popover, macro bars, donut in kcal shares
- [ ] Goals screen: three sections, validation, Save, End goal alert
- [ ] `MigrateAnonymousDataUseCase` + `PendingMergeSnapshot` (backward-compatible decode)
- [ ] `DeleteAccountUseCase`
- [ ] Strings CZ / EN / SK
- [ ] `firestore-rules-tests`: owner-only coverage of `settings/goals`
- [ ] `privacy.html`; Play Data safety and App Privacy re-confirmed (manual, owner)
- [ ] `ARCHITECTURE.md` (macro summary, data model, merge, deletion) and `TODO.md`

## Outcome

*Filled in once after shipping.*
