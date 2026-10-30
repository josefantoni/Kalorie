# Design: Catalogue contribution hint

- **Status:** Draft
- **Scope:** Cross-platform, iOS, Android
- **Date:** 2026-10-30

## Context and scope

The *Search catalogue* screen (`AddFoodSheetMode.search`) shows the search field, then — while the
field is empty — the *Favourites*, *My created meals* and *My submissions* sections. Nothing on that
screen tells a user that a food they cannot find can be added through the *New item* segment, or
that a submission improves the shared catalogue for everyone ([design 0009](0009-catalogue-moderation.md)).

This document adds one dismissible hint card between the search field and *Favourites*. It covers
both clients. Nothing changes in Firestore, the rules, KMP or the submission flow itself.

## Goals

- With an empty search field, a card under the search field says the user can add a food they did
  not find, and that people like them improve the catalogue.
- Tapping the card switches the sheet to the *New item* segment, exactly as tapping that segment does.
- A *Hide* action on the right of the card, separated from the message by a vertical divider, hides it on
  this device. It comes back after a growing number of days — 3, 5, 8, 13, 21, then every 30 — and can
  be hidden again, without limit. A monthly reminder is the floor: it is cheap to repeat and the card
  is easy to dismiss.
- Typing into the search field hides the card; clearing the field brings it back (unless hidden).
- Identical behaviour, copy and layout on iOS and Android.

## Non-goals

- **No sync of the hidden state.** It lives on the device (`UserDefaults` / `SharedPreferences`), not
  in Firestore. A second device, a reinstall or a sign-in merge shows the card again. Accepted.
- **No permanent dismissal**, and no special case after the user has submitted a food — the card
  keeps returning, at most 30 days apart, regardless.
- **No change to who may submit.** Anonymous users already may ([design 0009](0009-catalogue-moderation.md)
  § anonymous users), so the card is shown to everyone, signed in or not.
- No analytics on taps or hides.

## Design

### The core rule

```
interval                  = [3, 5, 8, 13, 21, 30] days, indexed by max(hideCount, 1) - 1, clamped to the last
isContributionHintVisible = hiddenAt == nil || now - hiddenAt >= interval
card shown                = mode == .search && searchText.isEmpty && isContributionHintVisible
```

The intervals are Fibonacci numbers, capped at 30. `hideCount` is how many times the user has tapped
*Hide*, so after the first *Hide* the card returns after 3 days, after the second after 5, and from the
sixth on every 30. A `hiddenAt` stored without a count (a build from before the count existed) is read as
one *Hide*.

`isContributionHintVisible` is evaluated once when the ViewModel is created and set to `false` by
the *Hide* tap. It is not re-evaluated while the sheet is open — the sheet is short-lived, and a
boundary crossed mid-session does not matter. The `searchText.isEmpty` part stays in the
view, the same way the *Favourites* section is gated (iOS `AddFoodSheetView.swift:203`, Android
`AddFoodSheetView.kt:318`).

### Storage — copy the sign-in spotlight store

The precedent is the sign-in spotlight's "last shown at" store
([design 0017](0017-sign-in-spotlight.md)). Copy its shape, including the fake:

| | iOS | Android |
|---|---|---|
| Precedent | `iOS/Kalorie/Features/Dashboard/SignInSpotlightStore.swift` | `Android/app/src/main/java/antoni/kalorie/features/dashboard/SignInSpotlightStore.kt` |
| New file | `iOS/Kalorie/Features/AddFoodSheet/ContributionHintStore.swift` | `Android/app/src/main/java/antoni/kalorie/features/addfoodsheet/ContributionHintStore.kt` |
| Protocol | `ContributionHintStoreProtocol: AnyObject { var hiddenAt: Date? { get set }; var hideCount: Int { get set } }` | `interface ContributionHintStoreProtocol { var hiddenAt: Instant?; var hideCount: Int }` |
| Backing | `UserDefaults.standard`, keys `Constants.AddFood.contributionHintHiddenAtKey` = `"contributionHintHiddenAt"` and `contributionHintHideCountKey` = `"contributionHintHideCount"` | `SharedPreferences` named `Constants.AddFood.CONTRIBUTION_HINT_PREFERENCES` = `"contributionHint"`, keys `Constants.AddFood.CONTRIBUTION_HINT_HIDDEN_AT_KEY` = `"contributionHintHiddenAt"` (epoch seconds as `Long`) and `CONTRIBUTION_HINT_HIDE_COUNT_KEY` = `"contributionHintHideCount"` (`Int`) |
| Fake | `ContributionHintStoreFake` under `#if DEBUG` in the same file | `ContributionHintStoreFake` in `app/src/debug/…/features/addfoodsheet/` |

`Constants.AddFood` is new on both platforms: an `enum` in `iOS/Kalorie/Core/Utils/Constants.swift` and an `object` in
`Android/app/src/main/java/antoni/kalorie/core/utils/Constants.kt` (modelled on `Auth`, which holds the spotlight keys).

### ViewModel (both clients, same names)

`AddFoodSheetViewModel` gets two new constructor dependencies, mirroring how
`DashboardViewModel` takes the spotlight store (iOS `DashboardViewModel.swift:125-147`):

- `contributionHintStore: any ContributionHintStoreProtocol` / `ContributionHintStoreProtocol`
- `now: () -> Date = { Date.now }` / `now: () -> Instant = { Instant.now() }`

New members:

- `private static let contributionHintHideDays = [3, 5, 8, 13, 21, 30]` (same style as
  `signInSpotlightInterval`, `DashboardViewModel.swift:573`); Android a private top-level `val` in the
  same file.
- `@Published private(set) var isContributionHintVisible: Bool` / `StateFlow<Boolean>`, initialised
  in `init` from the core rule.
- `func onContributionHintHidden()` — stores `now()` into `hiddenAt`, increments `hideCount`, sets the
  flag to `false`.
- `func onContributionHintTapped()` — calls `onModeSelected(.newItem)` (iOS
  `AddFoodSheetViewModel.swift:342`, Android `AddFoodSheetViewModel.kt:153`). That already resets
  the form, so a half-filled rejected resubmission is not carried over. Do not duplicate that reset.

Call sites that construct the ViewModel and must be updated:

- iOS: `AddFoodSheetConfigurator.swift:42` (pass `ContributionHintStore()`), the test `makeSUT` at
  `AddFoodSheetViewModelTests.swift:861` (pass `ContributionHintStoreFake` and a fixed `now`), and
  any `#Preview` — `grep -rn "AddFoodSheetViewModel(" iOS/`.
- Android: `AddFoodSheetConfigurator.kt:62` (wrap in `remember { ContributionHintStore(context) }`, as
  `DashboardConfigurator.kt:49` does), the test `makeSUT` at `AddFoodSheetViewModelTest.kt:1195`, and
  any preview — `grep -rn "AddFoodSheetViewModel(" Android/`.

### Layout

Placement: directly after the search field's section, before *Favourites*. It is shown even when
*Favourites* is empty — then it sits above *My created meals* / *My submissions*, or alone.

```
[ Search catalogue        ][ New item ][ Create meal ]
┌──────────────────────────────────────────────┐
│ Search food…                            [▦]  │
└──────────────────────────────────────────────┘
┌─────────────────────────────────────┐ ┌──────┐   ← tinted background
│ Can't find a food? Add it. People   │ │      │   ← message taps → New item
│ like you help us make our catalogue │ │ Hide │   ← Hide only hides
│ better.                             │ │      │
└─────────────────────────────────────┘ └──────┘
FAVOURITES
```

The card is one reusable component, `HintCard`, on both platforms
(`iOS/Kalorie/Components/HintCard.swift`, `Android/app/src/main/java/antoni/kalorie/components/HintCard.kt`).
It takes a message, an action title, a tap handler for the message and a handler for the action. The
screen supplies the row insets; the component draws the content and its background.

- **Message:** subheadline / `bodyMedium`, medium weight, default primary text colour (iOS `.primary`,
  Android `onSurface`), 16 pt/dp padding on all sides, multi-line, leading-aligned. No icon.
- **Gap:** 6 pt/dp wide, full height of the card edge to edge, so the card reads as two pieces of one
  object. On Android it is a `Box` in the screen's background colour (`colorScheme.background`). On iOS
  it is a real gap: the message and *Hide* are two separate filled shapes, the outer corners 26 pt and
  the inner ones square (`UnevenRoundedRectangle`). A divider painted over one shape was tried and
  dropped — it looked like a stripe, not a gap.
- **Hide:** the right part, plain text in the accent colour, semibold, no fill and no glass, with a
  16 pt/dp horizontal padding and a tap area as tall as the card.
- **Shape:** the card keeps the corner radius of a row in a section — on iOS it *is* a list section, on
  Android a `SectionCard` (12 dp). No custom radius on either side; the divider simply cuts the shape.
- **No disclosure chevron on Android.** `Android/CLAUDE.md` asks for one on rows that push a screen;
  this card switches a segment instead. Deliberate exception.
- **Spacing:** the gap between the search field and the card equals the gap between the segment and the
  search field. iOS: `.listSectionSpacing(.custom(16))` on the card's section (the segment's bottom
  padding is 16). Android: `Modifier.padding(top = 7.dp)` on the card, which was matched against a
  screenshot, because the search card's bottom underline made 4 dp render too tight.

**iOS** (`AddFoodSheetView.swift`, between the search section and *Favourites*):

```swift
if viewModel.searchText.isEmpty && viewModel.isContributionHintVisible {
    Section {
        HintCard(message: …, actionTitle: …, onTap: { viewModel.onContributionHintTapped() }, onAction: { viewModel.onContributionHintHidden() })
            .listRowInsets(EdgeInsets())
    }
    .listRowBackground(Color.clear)
    .listSectionSpacing(.custom(16))
}
```

Gotcha: a plain `Button` inside a `List` row makes the **whole row** fire that button. Inside `HintCard`
the *Hide* button uses `.buttonStyle(.borderless)` and the message has its own `onTapGesture`, so the two
taps stay independent. Verify both by hand. The glass button style was tried and dropped in favour of
the plain accent text.

**Android** (`AddFoodSheetView.kt`, a new `item { }` between the search item and the favourites block):

- `SectionCard` gets a `color: Color = MaterialTheme.colorScheme.surfaceContainer` parameter and
  `HintCard` forwards its own `color` to it. Every existing `SectionCard` call site keeps its colour
  through the default.
- Inside `HintCard`: a `Row` of fixed intrinsic height with the message (`Modifier.weight(1f)`,
  `clickable`), the divider `Box`, and a `Box` holding the *Hide* text, `clickable`, filling the height.
  Both `clickable`s consume their own clicks, so no extra handling is needed.
- Collect `isContributionHintVisible` with `collectAsState()` next to `searchText`.

### Colour

- **iOS:** a flat fill, `.background(Color.accentColor.opacity(0.2), in: …)`, applied inside `HintCard` to
  each of its two pieces. The list row's own background is `Color.clear`, so the fill is the only surface
  and the list background shows through the gap. Liquid Glass (`.glassEffect`) was tried and dropped: it
  draws its own rim highlight around each piece. The 26 pt outer radius approximates the corner of a
  grouped-list section and was set by eye.
- **Android:** the same look is a computed tint, `tintedSurfaceColor()`
  (`components/TintedSurfaceColor.kt`), shared with the Dashboard's `DayPickerView`:
  - **Dark mode:** `MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)`, which Material 3 renders as
    `surface` with `primary` (`#1B7F6B`) laid over it at about 8 % (`(4.5 · ln(3 + 1) + 2) / 100`) — the
    same colour `DayPickerView` always drew from `tonalElevation = 3.dp`. A Material role, so § 5.6
    allows it and no `AppColors` role is added.
  - **Light mode:** the Material 3 baseline surface (`#FEF7FF`) is pinkish, so 8 % of the accent over it
    came out grey-blue. The tint is therefore `primary` at 20 % over pure white, `#D1E5E1`
    (8 % and 15 % looked too pale).

**The platforms differ on purpose.** iOS keeps Liquid Glass on the Dashboard's day strip and gives the hint
card a flat translucent fill; Android has no glass, so it tints its surfaces instead. The Android
Dashboard day strip uses the same `tintedSurfaceColor()` as the hint card, which in light mode makes it
faintly green where it used to be grey-lilac. iOS has no counterpart change, so the Dashboard strip is not
1:1 across the clients, and that is deliberate (also noted in `Android/CLAUDE.md`).

The two platforms cannot match exactly — the iOS fill is translucent over the list background, the
Android tint is opaque. Put an Android device and an iPhone side by side in both modes; if they look far apart, tune the
`opacity` on iOS or the alpha on Android.

The text stays the default primary colour on both (iOS `.primary`, Android `onSurface`).

### Copy

Through `localisation/strings.json`, then `npm run generate-strings` in `scripts/` (generates the
iOS `Localizable.xcstrings` and both Android `strings.xml`; never edit those by hand). Add the
accessors to `iOS/Kalorie/Resources/Localization/L10n.swift` under `AddFood` (next to
`sectionFavourites`, line 150).

| Key | cs | en | sk |
|---|---|---|---|
| `addFood_contributionHint_message` | Chybí ti potravina? Přidej ji. Lidé jako ty nám pomáhají zkvalitňovat náš katalog. | Can't find a food? Add it. People like you help us make our catalogue better. | Chýba ti potravina? Pridaj ju. Ľudia ako ty nám pomáhajú zlepšovať náš katalóg. |
| `addFood_contributionHint_hide` | Skrýt | Hide | Skryť |

### Tests

Same test names on both platforms (Android ports the iOS ones), in
`AddFoodSheetViewModelTests.swift` / `AddFoodSheetViewModelTest.kt`, with a fixed `now` and the
store fake. Name style follows the spotlight tests (`DashboardViewModelTests.swift:812`):

- `test_init_whenNeverHidden_showsContributionHint`
- `test_init_whenHiddenOnceLessThanThreeDaysAgo_hidesContributionHint` — 2 days 23 h ago
- `test_init_whenHiddenOnceThreeDaysAgo_showsContributionHint` — exactly 3 days (the boundary is `>=`)
- `test_init_whenHiddenTwiceFourDaysAgo_hidesContributionHint` — the second wait is 5 days
- `test_init_whenHiddenTwiceFiveDaysAgo_showsContributionHint`
- `test_init_whenHiddenManyTimes_capsTheIntervalAtThirtyDays` — 29 days hides, 30 days shows
- `test_init_whenHiddenBeforeTheCountWasStored_treatsItAsTheFirstHide` — `hideCount` 0 with a timestamp
- `test_onContributionHintHidden_storesNowAndCountsTheHide`
- `test_onContributionHintTapped_switchesToNewItemMode`

The `searchText.isEmpty` gate is view-only and has no unit test; check it by hand.

**Verify by hand on a device (both platforms):** card visible with empty field; disappears on the
first typed character and returns on clearing; tap on the text opens *New item*; tap on *Hide* only
hides (does not open *New item*); closing and reopening the sheet keeps it hidden; light and dark mode
contrast; largest allowed text size (iOS caps at `xLarge`) wraps the message without clipping *Hide*.

## Alternatives considered

- **Show the hint only when a search returns nothing.** Rejected: the requirement is a card shown
  up front that disappears once the user types, and an empty local search already falls through to
  OpenFoodFacts results, so "nothing found" is rare and comes late.
- **Store `hiddenAt` in Firestore** so it follows the account. Rejected — a write per dismissal, a new
  field in the user document and rules for a cosmetic preference. The spotlight precedent is local too.
- **Hide forever after a successful submission.** Rejected by the user; the card keeps returning, at most 30 days apart.

## Cross-cutting concerns

- **Privacy:** one local timestamp, no personal data, nothing leaves the device. No change to the
  privacy policy or the data export.
- **Migration:** none; a missing `hiddenAt` means "never hidden" and a missing `hideCount` means 0, which is read as one *Hide*.
- **Store review:** nothing new.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| iOS row tap fires *Hide* (or vice versa) | User hides the card while trying to open the form | `.buttonStyle(.borderless)` on *Hide*, separate `onTapGesture` on the message + manual check above |
| iOS translucent fill and the Android opaque tint drift apart | Cards look different on the two platforms | Side-by-side check described under *Colour* |
| Card pushes *Favourites* down on small screens | Favourites need a scroll | Accepted; the card is hideable |

## Outcome

_Filled in once after shipping._
