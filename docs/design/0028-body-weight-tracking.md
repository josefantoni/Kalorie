# Design: Body weight tracking

- **Status:** Proposed
- **Scope:** Cross-platform
- **Date:** 2026-10-27

## Context and scope

The app records what the user eats and nothing about the user's body. This document adds a dated
body-weight history: a small circle next to the Dashboard's calorie pie showing the weight valid on
the selected day, a push screen with the full history, two new settings, and the knock-on changes to
the anonymous-account merge, account deletion, the export, the privacy policy and the store forms.

It touches every layer: a new `users/{userId}` subcollection and a settings document (Firestore
contract), one new provider query shape, `MigrateAnonymousDataUseCase` and `DeleteAccountUseCase`
on both clients, `ExportKit` inputs (iOS and Android), and `backend/hosting/privacy.html`. Both
clients implement it 1:1 ([ADR 0038](../adr/0038-android-client-mirrors-the-ios-architecture-natively.md)).

Read with: `ARCHITECTURE.md` § 1.2–1.6, § 3.5–3.9, § 6.2–6.3, § 8;
[ADR 0008](../adr/0008-dates-as-epoch-seconds-not-firestore-timestamp.md),
[ADR 0015](../adr/0015-dashboard-caches-a-month-and-derives-the-day.md),
[ADR 0041](../adr/0041-dashboard-reloads-only-the-affected-day-after-a-write-or-foreground.md),
[design 0014](0014-data-export.md), [design 0016](0016-copy-meal-to-another-window.md),
[design 0021](0021-privacy-policy.md), [design 0027](0027-slovak-localisation.md).

## Goals

- At most one weight per calendar day, logged through the badge on today or on any earlier day from 1 January 2026 (a
  missed day can be filled in later, but a future day and a day before 2026 cannot) and stored in kilograms without
  rounding. An existing entry is edited through the badge on its day, or deleted from the history
  (see *Decisions made during implementation*).
- The Dashboard shows the weight valid on the selected day, the two entries before it and the two
  after it, for two queries of at most five document reads (one query and three reads on today,
  where nothing can come later).
- A history screen to review and delete entries.
- A circle visibility switch and a kg / lb unit, stored in Firestore so they follow the account.
- Anonymous data survives sign-in; the account's own data always wins a conflict.
- The calorie pie does not move, whether the circle is shown or hidden.

## Non-goals

- A target weight or any judgement of a trend (no green/red, no smoothing, no trend line).
- Apple Health / Health Connect sync.
- Offline behaviour; the app is online-only.
- The monthly `foodConsumed` read cost (`TODO.md`, *Dashboard read cost*).
- BMR. This design must not block it — see [Relation to BMR](#relation-to-bmr).

## Design

### Data model

```
users/{userId}/bodyWeights/{dayKey}    one entry per calendar day
users/{userId}/settings/app            app-wide user settings (one document)
```

**`bodyWeights/{dayKey}`**

| Wire name | Type | Required on read | Meaning |
|---|---|---|---|
| `id` | string `yyyy-MM-dd` | yes | the day key, equal to the document id (§ 1.3 convention) |
| `weight_kg` | number | yes | kilograms, unrounded |

- **The document id is the day key**, so "one entry per day" holds by construction: a second write
  for the same day is a `setAsync` to the same id and replaces the first. No read-before-write, no
  duplicates to clean up.
- **The day key is a calendar date, not an instant**, which is why this collection deviates from
  ADR 0008's epoch seconds. The key is built from the **Gregorian** calendar in the device's
  **current time zone**, formatted with a POSIX locale (`en_US_POSIX` / `Locale.ROOT`), from
  `selectedDay`. It must not use `Calendar.current` for the components: on a device set to the
  Buddhist or Japanese calendar that yields a different year. Day *boundaries* are the same in every
  calendar, so the key names the same day the Dashboard buckets `foodConsumed` into. Only the key is
  pinned to Gregorian; every displayed date keeps the device's calendar, as in the rest of the app.
- The zero-padded format sorts lexicographically in date order, which the queries below rely on.
- **Known asymmetry:** `foodConsumed` stores an instant and is bucketed into days at read time, so it
  follows the user across time zones; a weight is pinned to the day it was written for. A user who
  logs in Prague on the 27th and travels to New York still sees it on the 27th. Accepted: a weight
  belongs to a day, not to a moment.
- The field is `weight_kg`, not `weight`: `weight` already means a food amount in grams across the
  model (§ 1.3), and the unit belongs in the name.
- **Units.** Stored always in kg as entered or converted (`lb × 0.45359237`), never rounded. Display
  rounds to one decimal in the chosen unit. Storing rounded kg would drift a lb user's value on every
  edit (180.0 lb → 81.6 kg → 179.9 lb); storing unrounded kg round-trips exactly.
- **Range** 30.0–300.0 kg, step 0.1. The lb picker is derived and rounded *inwards* so every value
  converts back inside the kg range: 66.2–661.3 lb. The kg bounds are the contract; lb is display.

**`settings/app`**

| Wire name | Type | Required on read | Default when absent |
|---|---|---|---|
| `show_body_weight` | bool | no | `true` |
| `body_weight_unit` | string `"kg"` / `"lb"` | no | `"kg"`; an unknown value also reads as `"kg"` |

- **Placement — a dedicated document, not `users/{userId}`.** The profile document is written by
  `SignInWithAppleUseCase` / `SignInWithGoogleUseCase` with `setAsync`, which replaces the whole
  document (§ 1.5): every sign-in would erase the settings stored beside the profile. It would also
  mix this data's `snake_case` into the profile's camelCase, and an anonymous user has no profile
  document at all (§ 1.2). `settings/app` follows the existing `stats/foodFrequency` shape: a fixed
  subcollection name and a fixed document id.
- **Absent document = defaults.** Nothing is written until the user changes a setting.
- **Every field optional on read**, so a setting added later never breaks an older client's decode.
- **Writes merge, they do not replace.** With `setAsync` an older client that does not know a future
  field would erase it on its next write. This adds one provider method,
  `mergeAsync(_:id:in:)` → `document(id).setData(data, merge: true)`, used only here. Each write
  still sends all the fields the client knows, as the brief asks; merge only protects the fields it
  does not know.

**Security rules.** `users/{userId}/{document=**}` already restricts read and write to the owner
(§ 1.6), which covers both paths. No rule change is needed and none is proposed: the `users/**`
subtree has no field validation anywhere, and adding it for one collection would be the first. A
`firestore-rules-tests` case that an owner can and another user cannot read/write
`bodyWeights/{day}` and `settings/app` documents the coverage cheaply and SHOULD be added.

### Provider: one new query shape

```
loadAsync(from:where:isLessThanOrEqualTo:orderByDescending limit:)
  → whereField(f, isLessThanOrEqualTo: v).order(by: f, descending: true).limit(to: n)
```

Filter and order on the **same** field (`id`), so the automatic single-field index serves it and no
composite index is needed. Both clients' providers gain it. A second new method, `mergeAsync`, is
above. A third, `id >= first AND id <= last` (no limit), serves the history screen one calendar
year at a time.

### Dashboard circle

**Only with food.** The circle sits in the pie's slot, so it is rendered only on a selected day that has
food entries; on an empty day the Dashboard shows its empty state and there is no circle, badge or way
into the history (see *Decisions made during implementation*).

**Layout.** The pie stays exactly where it is (160 pt, centred in `MacroSummaryView`). The circle
lives in a fixed-width trailing slot that is always laid out, and is merely empty when the circle is
hidden, so the pie is centred independently of it (an overlay or a symmetric leading spacer —
not an `HStack` that recentres). Smallest supported widths: iOS 26 starts at 375 pt (iPhone SE 2/3),
Android small phones at 360 dp. Free space right of the pie is `(375 − 160) / 2 ≈ 107 pt`, minus the
16 pt margin ≈ 91 pt (Android ≈ 84 dp). Proposed circle **72 pt / 72 dp**, centred in that space,
with ≥ 6 pt clearance to the pie on Android's narrowest case. The number uses a scale factor
(`minimumScaleFactor` / auto-size text) so `172,8 lb` fits at large Dynamic Type / font scale; the
circle itself does not grow.

**Content** — from up to three entries with `id ≤ dayKey(selectedDay)` (newest first; the first is
the weight valid on the day) and up to two entries with `id > dayKey(selectedDay)` (oldest first):

| Entries valid on or before the day | Circle |
|---|---|
| 0 | blurred placeholder (no number, no line), even if later entries exist |
| 1, and none later | number only |
| otherwise | number + polyline through the actual points, including the later ones |

- The number is the newest entry: the weight *valid* on the selected day (that day's entry, or the
  last earlier one). `78,4 kg` / `172,8 lb`, decimal separator from the locale, one decimal.
- The polyline connects the real values; x is evenly spaced by entry order, not by date (three
  points spread over months would otherwise collapse into one edge — the circle answers "which
  direction", the detail answers "when").
- **The newest entry is the centre of the graph**, marked with a dot; older entries run to the left
  and the right half stays empty, since nothing is known about the days after the selected one.
- **Y axis spans at least 2 kg** (≈ 4.4 lb) and is centred on the newest entry, so a 0.2 kg wobble
  is not drawn as a cliff. When an older entry is further than 1 kg from the newest, the span grows
  to contain it.
- **One neutral colour** for the line (secondary label colour); a fall is not "good" and a rise is
  not "bad" in an app without a goal.

**Badge** on the circle's bottom-trailing edge:

- `plus` when the selected day has no entry (newest `id` ≠ selected day key), `pencil` when it has.
- **Rendered on today and on any earlier day**, so a missed day can be back-filled (owner decision
  2026-10-28, reversing 2026-10-27). Not rendered on a future day. With `dayKey(selectedDay) = d`,
  `today = dayKey(now)`: shown iff `Constants.BodyWeight.firstDayKey <= d <= today`. The lower bound is
  the first day the history screen can show (see *History screen*), so an entry the user could neither
  see nor delete there is never written. `SaveBodyWeightUseCase` repeats both bounds
  (`futureDay`, `beforeFirstYear`); the history screen cannot add a day and the Firestore rules do not
  validate dates. Rendered in the blurred state too, otherwise the first value could never be entered.
- Visual 24 pt, **hit area 44 × 44 pt / 48 × 48 dp**, a separate control layered above the circle so
  a tap on it does not also trigger the circle's push. The hit area may extend outside the circle
  into the trailing margin but MUST NOT overlap the pie.
- Tap opens the same anchored box as copying a section (§ 3.8): a popover on iOS
  (`.presentationCompactAdaptation(.popover)`), a `DropdownMenu` on Android. Contents: a weight
  picker and a green confirm button; on success the button turns into the checkmark
  (`showCopyCheckmark` pattern), then the box closes.
- **Picker** — two wheels, whole units and tenths (30–300 | 0–9 in kg; 66–661 | 0–9 in lb with the
  ends clamped to 66.2 / 661.3). A single 2,700-row wheel is unusable. Height ≈ 216 pt, which fits
  a popover on a 667 pt-tall SE. Android has no Compose wheel; the Android implementation chooses
  between wrapping `NumberPicker` and an existing in-app pattern (Android scope).
- **Initial value:** that day's entry → else the weight valid on that day → else **70 kg**
  (154.3 lb in lb mode, i.e. 70 kg converted and rounded).
- Writes `setAsync({id, weight_kg}, id: dayKey, in: bodyWeights)` for **the selected day**, which by
  the rule above is today or an earlier day. Confirm is disabled while writing. On failure the
  box stays open and `alertItem` reports it.

**Accessibility.** Circle and badge are two separate elements. Circle: "Weight 78.4 kg, show
history" (blurred: "Weight, no entries, show history"). Badge: "Log weight" / "Edit weight".

**Tap on the circle** pushes the history screen, in every state including blurred.

### Loading and the weight cache

Two queries per load, run concurrently — **at most five reads**:

- `id ≤ dayKey(selectedDay)`, descending, limit 3: the weight valid on the day and the two before it.
- `id > dayKey(selectedDay)`, ascending, limit 2: the two entries after it. Skipped when the selected
  day is today or later, because a future weight cannot be written, so it would only cost a read.

Both filter and order on the same field (`id`), so the automatic index serves them.

- `weightCache: [dayKey: [BodyWeight]]` in `DashboardViewModel`, alongside `monthCache`, keyed by
  the selected day (not the month — the window is per day).
- Triggers mirror § 3.6 for food:

| Trigger | Effect |
|---|---|
| `onAppear` | load settings and the selected day's weights, in parallel with meal types and the month |
| `onForeground` / day rollover | reload settings and the selected day's weights (overwrite that key) |
| Pull to refresh | reload settings, clear `weightCache`, reload the selected day |
| Day changed / picked | cache hit, or load that day |
| Weight written (badge) | clear `weightCache`, reload the selected day |
| Returning from history with changes | clear `weightCache`, reload the selected day |
| Settings sheet changed a setting | apply the new settings; no read |

- **A write clears the whole cache, not one key.** Writing day D changes the window of every day
  around it already cached. Clearing is simpler than computing which keys are affected and costs at
  most five reads per later visit.
- **A failed load is not cached and not retried on its own.** The circle stays empty and the badge
  unavailable (a real entry must not be overwritten with the default), nothing is shown to the user
  and the error is only logged, as for the settings read. The next foreground, pull to refresh or
  day change loads the window again, because only a successful load fills `weightCache`. A failed
  reload after this client's own write or delete also clears the window already shown, since it is
  known to predate the change; a failed foreground or pull-to-refresh reload keeps it.
- Staleness for days other than the selected one after a write on another device is the same
  trade-off ADR 0041 already accepts for food.
- **Cost.** The two queries are ~15 % of a Dashboard foreground's reads on a past day, ~10 % on today; at 300 users it is in the order of
  tens of cents a month. Denormalising the last weights into a summary document was rejected: it saves
  cents and adds a second copy that can diverge.

### Settings and the no-flicker rule

- The settings document is read with `loadAsync(id:from:)` (1 read) on the triggers above.
- **Until the first read completes, the circle is not rendered** — its slot is laid out but empty, so
  the pie does not move and the circle never appears and then vanishes for a user who switched it
  off on another device. It appears together with the rest of the day's data, like the food list.
- On a reload (foreground, pull to refresh) the current value is kept until the new one arrives; it
  is never reset to the default in between.
- A failed settings read keeps the last known value; on cold launch the circle stays hidden until a
  read succeeds (foreground or pull to refresh), so a user who turned it off never sees it come back.
  It does not block the Dashboard or raise an alert (the food load already reports connectivity).
- **A read never overwrites a newer change.** A read that started before the user changed a setting
  is discarded when it returns, so a foreground or pull-to-refresh read that is still in flight cannot
  bring the old unit back over the one just saved from the Settings sheet.
- No local (UserDefaults / DataStore) mirror: it would add a second source of truth for one read's
  worth of latency, and the rule above already prevents the flicker.

**Settings sheet** (§ 3.9) gains a section **between *Meal layout* and *Other***, hidden in edit mode
like everything below the meal layout:

- *Show weight on overview* — toggle, default on. Off hides the circle completely (empty slot); data
  is never deleted.
- *Weight unit* — segmented `kg | lb`, shown even when the circle is off (it also applies to the
  history screen and the export).

- **Only after a successful read.** The section is not rendered, and the view model ignores a change,
  while the Dashboard has no successful `settings/app` read (cold launch with a failed read, until a
  foreground or pull to refresh succeeds). Each write sends every known field, so a change made over
  the defaults would overwrite the stored unit or switch. The state is taken when the sheet opens; a
  read that completes while it is open shows the section the next time.

A change writes the whole settings object through `mergeAsync`. On failure the control reverts and
`alertItem` reports it. The Dashboard is told through a callback (like `onMealTypesChanged`) and
applies the value it already knows without a read.

### History screen

Pushed from the circle on the Dashboard's `NavigationStack`.

- **Load:** the current calendar year of `bodyWeights`, once per appearance — one range query on
  `id`, at most 366 documents. Paging back to a period that starts in an earlier year reads that
  year, once, and prepends it; a year already loaded is never read again. Opening the screen
  therefore costs one year however long the history is, and a user who never pages back never pays
  for older years. Chart and list both derive from the loaded array, so the list shows the years
  loaded so far. Switching range or paging within loaded years reads nothing. A failed read of an
  earlier year alerts and is retried by the next page or range change.
- **No history before 2026.** The feature ships in 2026 and the badge refuses days before it, so
  paging back stops at 1 January 2026 (`Constants.BodyWeight.firstYear`, `canPageBack`): a period that
  starts there has no previous one, and no year earlier than 2026 is ever read.
- **Chart** — line segments between the real points, same neutral colour and 2 kg minimum Y span as
  the circle, x by date. A segmented range switcher **Week / Month / Year**, default **Month**, on
  both clients. A horizontal swipe (and previous / next arrows) pages to the previous or next
  period; scrolling day by day through years of history is too slow without the switcher.
  Switching range or paging reads nothing — it only changes which part of the already loaded array
  is drawn.
  The Y axis lists the entries' own values (one decimal, in the selected unit) at their height,
  not the bounds of the padded range. Labels are placed greedily, highest, lowest, newest, oldest
  and then the rest, and a label closer than 14 pt to one already placed is dropped, so a month or
  a year shows the extremes and as many others as fit.
  A faint horizontal guide line runs through the plot at each labelled value, lighter than the
  baseline so the two are not confused.
- **Drawing — custom `Canvas` on both clients**, for the circle's mini graph and for this chart, the
  way `MacroDonutView` already draws the macro pie on both (SwiftUI `Canvas` / Compose `Canvas`).
  No chart library: Swift Charts has no Android counterpart, so it would split the two clients'
  behaviour, and Android would need a new dependency. Paging by period instead of free scrolling
  keeps the hand-drawn chart simple: one period's points, its axis labels, nothing to virtualise.
- **List** — only days with an entry, newest first, `date — 78,4 kg`. No empty rows.
- **No editing** on this screen (see *Decisions made during implementation*).
  A row is not tappable. An entry is changed through the Dashboard badge on its day.
- **Delete** — the existing trailing swipe (`RevealingSwipeToDeleteRow`) with the same confirmation
  alert as a food entry (§ 3.7); no primary delete button.
- **No adding** on this screen. A new day is logged only through the badge, and only for today.
- **Empty state** — an unobtrusive info box explaining the feature and where the badge is; no
  button that forces an entry.
- When the screen made any change, the Dashboard is told on return and invalidates its cache.

### Anonymous user and the merge

The anonymous user has the full feature. Losing the device or uninstalling loses the data, as with
food — accepted.

`MigrateAnonymousDataUseCase` (both clients):

1. **Capture** (before sign-in): load the anonymous `bodyWeights` collection and `settings/app`
   concurrently with the existing four collections, into `PendingMergeSnapshot`.
2. **Replay** (`writeAndCleanup`, after sign-in): read the *target* account's `bodyWeights`
   (whole collection) and `settings/app` first.
   - Weights: write only anonymous entries whose `id` the account does **not** have. The account
     wins every day both have.
   - Settings: write the anonymous document only if the account has **none**.
   - Then `batchSetAsync` as today.

Today's replay writes blind because UUIDs cannot collide; with day-key ids a blind write would
overwrite the account's data, which is why the read comes first.

**Re-run safety** (§ 1.5). After a partial failure, entries that already landed are now the
account's own, so the resumed replay skips them — the result equals a full run. The same holds for
settings. Resume therefore stays idempotent with no extra state.

**Snapshot compatibility — required.** `PendingMergeSnapshot` is `Codable` with non-optional arrays
on iOS (and the Android equivalent is serialized the same way). A snapshot written by the current
release lacks the new keys; decoding it with the new type would throw, and § 6.2 deliberately leaves
an undecodable snapshot on disk forever, so that user's pending merge would never finish. The new
fields MUST decode as absent → empty list / `nil` (`decodeIfPresent` on iOS, default values on
Android).

A merged history can be nonsensical (60 / 100 / 61 kg) when the two accounts were used by different
people or carelessly; that is the user's doing and the app does not try to resolve it.

### Account deletion

`DeleteAccountUseCase` on both clients deletes every `bodyWeights` document with one `batchDeleteAsync` (a daily entry makes the collection
grow without bound, so a round trip per day would be hundreds after a few years) and then `settings/app`,
before the profile document. No composite index is
involved. The web deletion page ([design 0015](0015-account-deletion-web-page.md)) is unaffected if
it defers to the same wipe; check this when implementing.

### Export

Design 0014 / § 8. Rules:

- Every day of the interval shows the weight valid on it: its own entry, else the last earlier one.
- Days before the first entry show none.
- A user with no entries at all gets an export identical to today's — no row, column or empty cell.

Implementation:

- Weights needed are those with `id ≤ dayKey(to)`. Load them with **one query**: `id ≤ dayKey(to)`,
  newest first, `limit = days in the interval + 1`. At most one entry exists per day, so the interval's
  own entries take at most `days` of the limit and the last one is the latest entry before `from`.
  At most interval-length + 1 reads.
- **A failed weight read fails the export.** An export without the weights it should carry looks
  complete and is not, so the error is surfaced instead of dropping them silently. Only the unit
  setting degrades: if `settings/app` cannot be read the weights are still exported, in kilograms.
- The weight is formatted on the platform side (unit, decimal separator, as for every other export
  string) and **appended to the day label**: `Po 27. 10. 2026 · 78,4 kg`. ExportKit does not change;
  PDF and xlsx stay identical; a user without weights gets byte-identical output.
- Alternative kept in reserve: a nullable `weightLabel` on `ExportDayInput` rendered as its own cell
  of the day row. Only worth it if the weight must be numeric in Excel.

### Localisation

New strings for the circle, badge, accessibility labels, picker box, history screen (title, range
switcher, empty state, delete alert, errors) and the two settings, in **Czech, English and
Slovak** ([design 0027](0027-slovak-localisation.md)) through the shared `localisation/strings.json`
that feeds both clients.

### Privacy policy and store forms

**This feature removes the premise of an owner decision in design 0021.** Decision 2 (*No
explicit-consent step*) was taken because "Kalorie stores no weight, goals, body measurements or
conditions, only what was eaten and when". Body weight is data concerning health under GDPR Art. 9
without any of the ambiguity a food diary carries. Design 0021 stays frozen; the new decision is
recorded here.

**Owner decision (2026-10-27): no explicit-consent step for body weight either.** The risk design
0021 accepted for the food diary is extended to body weight. The legal basis stays Art. 6(1)(b), the
privacy policy informs about the new data (Art. 13), and neither app gains a consent UI. The owner
accepts that this is a weaker position than for the diary, since weight is unambiguously health
data. If it is ever revisited, the fix is the one 0021 already names: a consent step on both
clients, in its own design doc — for weight alone, a one-time *I agree* in the badge box before the
first entry would be enough.

`backend/hosting/privacy.html`, section 3 *Co ukládáme a proč* (and the same section in every other
language on the page): a new bullet — *Tělesná váha:* the weight and the day it belongs to, entered
only by the user, optional, used to show the user's own history and in the export; stored like the
diary, deleted with the account or entry by entry. Section 7 *Co neděláme* must still be true (no
sharing, no ads). The legal basis sentence does not change.

**Play Console → Data safety.** *Health and fitness → Health info* is already declared (food
diary) as Collected, not shared, Required, App functionality. Body weight falls under the same
type; the category does not change. The row stays *Required* because the food diary is; nothing
else changes. Re-submit the form so the declaration is re-confirmed against the new feature.

**App Store Connect → App Privacy.** *Health & Fitness* is already declared as *Data Linked to You*,
purpose App Functionality, no tracking. Body weight is Apple's *Health* sub-type, which the existing
declaration covers; verify the *Health* box (not only *Fitness*) is ticked. No new category.

### Relation to BMR

`TODO.md` *Basal metabolic rate* plans a weight field in `users/{userId}`. This design makes
`bodyWeights` the single source of the user's weight; a future BMR feature reads "the weight valid
today" with the same query the circle uses and stores no weight of its own. Nothing here blocks it.
The conflict is already noted in `TODO.md`; it should be updated to point to this document.

## Decisions made during implementation

Settled while building and reviewing the feature, before it shipped, so they are recorded here
instead of in ADRs. Both clients follow them.

**History reviews and deletes; only the Dashboard badge edits.** An edit popover on the history rows
duplicated the picker, the save/checkmark/dismiss flow and an error alert, for a path the badge already
covers (open the day, tap the badge). Cost: correcting an old entry takes one more step.

**The weight circle exists only where the pie does.** It lives in `MacroSummaryView`, which the
Dashboard shows only on a day with food entries. On a day without food there is no circle, badge or way
into the history, even on today. Accepted: a user who weighs in before the first meal cannot log the
weight until a food entry exists. Existing entries stay in the history, export and merge. If this
proves to be a problem, give the circle a slot on an empty day too.

**The Dashboard never shows, or offers to overwrite, a window that is not current.** The badge opens a
picker seeded from the window on screen and saving writes a whole entry, so a window of another day, or
one that never loaded, would overwrite a real entry with the wrong number or the default.

1. A window is shown only for the day it was loaded for (`shownWeightsKey`); selecting another day
   clears the circle until that day's window arrives.
2. The badge needs a window shown for the selected day: today or earlier, never a future day, never
   while loading or after a failed load.
3. A failed load is logged, not cached or surfaced. A failed reload after this client's own write or
   delete clears the shown window (it predates the change); a failed foreground or pull-to-refresh
   reload keeps it.
4. A load overtaken by a write or refresh is discarded (cache generation); a settings read that started
   before a setting change is dropped (`settingsChangeCount`).
5. Weights load even when meal types or the month fail at launch.

Cost: an empty circle while a load runs or until the next trigger after a failure.

**Export: a failed weight read fails the export; an unreadable unit does not.** A file missing the
weights it should carry looks complete and is indistinguishable from days with no entry. If only the
unit fails to read, the export succeeds in kilograms and the failure is logged. The unit is read once,
and only when at least one weight was loaded, so a user without weights gets an export identical to one
made before the feature.

## Alternatives considered

- **Random UUID ids + a `date` field.** Needs a read before every write to find that day's entry,
  and duplicates if two devices write the same day. Rejected for the day-key id.
- **Epoch-seconds `date` per ADR 0008.** An instant has no day of its own; deduplicating by day
  would need a time-zone decision at every read. A day key states the intent.
- **Settings in `users/{userId}`.** Erased by every sign-in's full-overwrite profile write; mixed
  naming; absent for anonymous users. Rejected.
- **Local settings cache to avoid flicker.** A second source of truth; the empty-slot-until-loaded
  rule achieves the same.
- **Last weights denormalised into a summary document.** Saves cents, risks divergence. Rejected
  (brief).
- **History loaded per visible range.** Fewer reads for long histories, but the list needs all
  entries anyway. Rejected while a year is ≤ 365 reads.
- **Swift Charts on iOS, a chart library (e.g. Vico) on Android.** Two different engines for one
  chart, and a new Android dependency. Rejected for `Canvas` on both, as the macro pie is drawn.
- **A trend line or smoothing.** Out of scope by intent: the app has no goal to measure against.
- **Weight as a separate ExportKit column.** Changes the layout for every user, including those
  with no weights. Rejected in favour of the day label.

## Risks

- **Day-key construction diverging between clients** (calendar, time zone, locale) would make the
  two clients write different ids for the same day and produce two entries. Mitigation: a shared
  fixture in `fixtures/` (date, time zone, calendar → expected key) run by both test suites, as
  `food-item-validation-cases.json` is.
- **Snapshot decode** — see the merge section; a regression strands a pending merge permanently.
- **Small screens and large text** — verify on iPhone SE (375 pt) and a 360 dp Android at the
  largest text size, circle and badge hit area not touching the pie.
- **Popover height** — the two-wheel picker plus button on iPhone SE in landscape is not supported
  (app is portrait); verify portrait only.

## Implementation checklist

- [ ] Provider: `loadAsync(from:where:isLessThanOrEqualTo:orderByDescending limit:)`, `mergeAsync` — iOS + Android
- [ ] `BodyWeightDTO` / domain, day-key builder + shared fixture — iOS + Android
- [ ] `SettingsDTO` / domain with defaults — iOS + Android
- [ ] Use cases: fetch weights up to a day, fetch all weights, save weight, delete weight, fetch / save settings
- [ ] Dashboard: circle slot, circle, badge, picker box, `weightCache`, triggers
- [ ] History screen: chart + range switcher, list, swipe delete, empty state
- [ ] Settings sheet: new section
- [ ] `MigrateAnonymousDataUseCase` + `PendingMergeSnapshot` (backward-compatible decode)
- [ ] `DeleteAccountUseCase`
- [ ] Export: weight in day label, one query
- [ ] Strings CZ / EN (/ SK)
- [ ] `firestore-rules-tests`: owner-only coverage of both paths
- [ ] `privacy.html`; Play Data safety and App Privacy re-confirmed (manual, owner)
- [ ] `ARCHITECTURE.md` § 1.2, 1.4, 1.5, 3.6, 3.9, 6.2, 6.3, 8 and `TODO.md` BMR note

## Outcome

*Filled in once after shipping.*
