# Design: Store review prompt

- **Status:** Draft
- **Scope:** Cross-platform, iOS, Android
- **Date:** 2026-11-22

## Context and scope

The app has very few store ratings on either store, and ratings feed both conversion on the
listing page and store search ranking. Neither client asks for a rating today. The only route
to the store is the update-required screen
([design 0026](0026-minimum-supported-version.md)), which links to the listing through
`Constants.Store.appStoreURL` (iOS `iOS/Kalorie/Core/Utils/Constants.swift:75`) and
`Constants.Store.PLAY_STORE_URL` (Android `Android/app/src/main/java/antoni/kalorie/core/utils/Constants.kt:16`).

The product owner wants:

- a request for a rating on the user's third day of logging, explaining that Kalorie is a small
  free app without ad money or investors, competing with big funded apps;
- a repeat after a few days if the user dismisses it;
- no more requests once the user has rated.

What the platforms allow shapes the whole design:

| | iOS | Android |
|---|---|---|
| Native API | `RequestReviewAction` (StoreKit) | Play In-App Review API |
| Tells the app whether the user rated or dismissed | No | No |
| Always shows when called | No. At most 3 times in 365 days, otherwise silently nothing | No. Undisclosed quota, roughly monthly |
| Custom text in the dialog | No | No |
| Custom prompt | Guideline 5.6.1 disallows custom review prompts | Policy forbids questions before the review flow ("Do you like the app?"), incentives and gating to happy users |
| Link to the store | `apps.apple.com/app/id…?action=write-review`, documented by Apple | Listing URL `play.google.com/store/apps/details?id=antoni.kalorie` |

So "has the user rated" cannot be known on either platform, a dismissal of the native dialog
cannot be observed, and the custom message cannot appear inside it. This document therefore uses
our own inline card that links to the store, and treats a tap on its **Rate** button as "rated".

This document covers the dashboard card, its show rule, its persistence and a permanent row in
settings. Visual mock-up the product owner approved: the review-card artifact shown in the design
conversation (two phones, card under the day summary).

## Goals

- From the third distinct calendar day on which the user added food, the dashboard shows an inline
  card with the message and two actions, **Rate** and **Not now**.
- **Rate** opens the store's review page and the card never shows again on that device.
- **Not now** hides the card. It comes back after 3 days, then after 5, 8 and 13, and from then on
  every 21 days until the user rates.
- The card never appears on the same day as the sign-in spotlight
  ([design 0017](0017-sign-in-spotlight.md)), which keeps priority.
- Settings has a permanent **Rate Kalorie** row, so a user who dismissed the card for good can
  still rate.

## Non-goals

- The native review dialogs (`RequestReviewAction`, Play In-App Review). See *Alternatives*.
- Finding out whether the user actually left a rating. Not possible; the tap is the proxy.
- Asking for a positive rating, star selection inside the app, or routing unhappy users to the
  feedback e-mail instead of the store. All forbidden by store policy.
- Syncing the prompt state across devices or accounts. Same reasoning as design 0017: per-device
  local state, no Firestore rules or DTOs. A user with two devices may be asked on each.
- A/B testing the copy or timing.

## Design

### Logging days (shared)

A **logging day** is a local calendar day (`yyyy-MM-dd` in the device time zone, taken from the
injected clock) on which the user added at least one food through the add-food sheet.

- Record it only from the add-food sheet's save callback, not from every reload:
  - iOS: the `makeAddFoodSheetView` completion in `iOS/Kalorie/Features/Dashboard/DashboardView.swift:188`;
  - Android: `onFoodSaved` in `Android/app/src/main/java/antoni/kalorie/features/dashboard/DashboardView.kt:530`.

  Both call a new `onFoodAdded()` on `DashboardViewModel`, which records today and then does what
  `onFoodConsumedUpdated()` does today. The food-detail edit callbacks (iOS
  `DashboardView.swift:89`, Android `DashboardConfigurator.kt:126`) keep calling
  `onFoodConsumedUpdated()` and do not count.
- The day recorded is **today**, not the selected diary day. Backfilling three past days in one
  sitting is one logging day. The rule measures days of use, not diary coverage.
- Counting days from `monthCache` was rejected. It counts days logged on another device or
  before a reinstall, and depends on which months happen to be loaded.
- The store keeps at most 3 days. Once it holds 3, recording stops.

### Show rule (shared, lives in the dashboard ViewModel)

```
SNOOZE_DAYS = [3, 5, 8, 13]
REPEAT_DAYS = 21

isReviewCardVisible =
       !store.isCompleted
    && store.loggingDays.count >= 3
    && (store.dismissCount == 0
        || now − store.lastDismissedAt >=
           (SNOOZE_DAYS[store.dismissCount − 1] ?? REPEAT_DAYS) days)
    && !isSignInSpotlightVisible
    && !(signInSpotlightStore.lastShownAt is on today's calendar day)
    && !foodsConsumed.isEmpty                                  // the selected day has food
```

- Showings: day 3 of logging → +3 days → +5 → +8 → +13 → then every +21 days, with no end.
  `dismissCount` past the list falls back to `REPEAT_DAYS`.
- The interval is measured from the **dismissal**, not from when the card first appeared. An
  inline card that the user scrolls past without tapping stays on the dashboard on every visit
  until they act on it. It has no timeout.
- Evaluate it wherever `showSignInSpotlightIfNeeded()` runs (iOS `DashboardViewModel.swift:252`,
  `:278`, `:299`, `:322`, `:330`; Android `DashboardViewModel.kt:261`, `:283`, `:300`, `:322`,
  `:328`), **after** that call, so a spotlight that fires in the same pass wins. Also evaluate it
  after `onFoodAdded()` records a day, so the third day's card appears right after the first save
  of that day.
- "Spotlight shown today" reads the existing `SignInSpotlightStoreProtocol.lastShownAt`. Both
  stores are already injected into the ViewModel.
- `!foodsConsumed.isEmpty`: the card sits under the day summary, which exists only for a
  non-empty day. On an empty day the empty-state overlay owns the screen.
- The visible flag is a published/`StateFlow` property, `isReviewCardVisible`, recomputed by the
  rule. It is never written directly from the View.

### Actions

- **Rate** → `store.isCompleted = true`, hide the card, then open the review URL:
  - iOS: new `Constants.Store.appStoreWriteReviewURL` =
    `https://apps.apple.com/app/id6823155404?action=write-review`, via `openURL`;
  - Android: `Constants.Store.PLAY_STORE_URL` via `LocalUriHandler`. Play has no public
    write-review deep link; the listing shows the rating section.

  `isCompleted` is written before opening, so a failed open (e.g. a simulator without the App
  Store) still counts. Retrying is what the settings row is for.
- **Not now** → `dismissCount += 1`, `lastDismissedAt = now`, hide the card.

### Settings row

- A **Rate Kalorie** row in the *Other* section, next to *Feedback* (iOS
  `iOS/Kalorie/Features/Settings/SettingsView.swift:140`, Android `SettingsView.kt:464`
  `SectionGroup(settings_section_other)`), styled like the existing *Feedback* row.
- It opens the same URL as **Rate** and sets `isCompleted = true`, so a user who rated from
  settings is not asked again on the dashboard.
- It is always visible and has no show rule.

### Persistence

Same pattern as `SignInSpotlightStore` (design 0017): a protocol, a `UserDefaults` /
`SharedPreferences` implementation and a `…Fake` for tests. No DataStore, no Firestore.

```
ReviewPromptStoreProtocol {
    var loggingDays: Set<String>      // yyyy-MM-dd, at most 3
    var dismissCount: Int             // 0 when absent
    var lastDismissedAt: Date?        // Instant? on Android
    var isCompleted: Bool
}
```

Keys: `reviewPromptLoggingDays`, `reviewPromptDismissCount`, `reviewPromptLastDismissedAt`,
`reviewPromptIsCompleted`. The settings ViewModel gets the same store injected for the row's
`isCompleted` write.

### Presentation

One shared card component per platform (`ReviewPromptCard`), placed directly under
`MacroSummaryView` in the dashboard scroll content (iOS `DashboardView.swift:55`, inside the
existing `if !viewModel.foodsConsumed.isEmpty`; Android `DashboardView.kt:355`).

- Surface: the dashboard card shape of the platform, with an accent-tinted fill and no border.
  iOS uses the `AccentSecondaryColor` asset (accent at 12 % in light mode, 20 % in dark); Android
  uses `tintedSurfaceColor()`, which now draws the same 12 % accent over white in light mode (the
  tonal surface stays in dark mode). `HintCard` and the Android day strip share these colours, so
  their light-mode tint dropped from the 20 % design 0029 gave them to 12 %; this is intentional.
- Content, top to bottom: a title row with the app badge, the title and a close button (`xmark`,
  44 pt / 48 dp target, accessibility label **Not now**), the body text, then a single **Rate**
  (primary) button aligned to the trailing edge. There is no text **Not now** button; both
  platforms dismiss through the close icon.
- No stars, no rating scale, no question in the title. The title is a request ("Help a small
  app?"), not a satisfaction question.
- Appear and disappear with the dashboard's existing 0.25 s ease; no motion when Reduce Motion /
  "Remove animations" is on.
- Accessibility: buttons keep the 44 pt / 48 dp minimum target; text
  scales with Dynamic Type / font scale (the dashboard's `dynamicTypeLimit()` applies).

### Strings

Add them wherever `settings_button_feedback` / `L10n.Settings.buttonFeedback` exists, in every
locale that key has (en, cs, sk). The store name is a placeholder so each platform names its own
store.

| Key | en | cs | sk |
|---|---|---|---|
| title | Help a small app | Pomož malé aplikaci | Pomôž malej aplikácii |
| body | Kalorie is free, with no ads and no investors. Big apps spend millions on marketing; we only have you. A rating on %@ helps others find us. | Kalorie je zdarma, bez reklam a bez investorů. Velké aplikace mají na marketing miliony, my máme jen tebe 🫵. Hodnocení v %@ pomůže, aby nás našli další. | Kalorie je zadarmo, bez reklám a bez investorov. Veľké aplikácie majú na marketing milióny, my máme len teba 🫵. Hodnotenie v %@ pomôže, aby nás našli ďalší. |
| store name | the App Store / Google Play | App Storu / Google Play | App Store / Google Play |
| rate | Rate | Ohodnotit | Ohodnotiť |
| not now | Not now | Teď ne | Teraz nie |
| settings row | Rate Kalorie | Ohodnotit Kalorie | Ohodnotiť Kalorie |

The copy must stay true: no ads, no investors. If tips (`Constants.Tips.isEnabled`) or ads are
ever turned on, revisit the body text.

## Alternatives considered

- **Native dialog only (`RequestReviewAction` / Play In-App Review) on day 3.** The highest-converting
  flow and fully policy-safe. Rejected as the main mechanism because neither "rated" nor "dismissed"
  can be observed, the system quotas silently drop the repeats, and the product owner's
  message cannot be shown.
- **Our own message, then the native dialog on "Rate".** Google's policy forbids asking anything
  before the in-app review flow. On iOS the dialog may silently not appear because of the quota,
  which would leave a **Rate** button that does nothing.
- **Modal popup with the same content.** More visible, but it interrupts logging and is the form
  App Review most readily treats as a custom review prompt under 5.6.1. The inline card can be
  scrolled past and blocks nothing.
- **Fixed 3-day repeat.** The product owner's first idea. Rejected for an escalating schedule
  (3, 5, 8, 13 days) that settles at one showing every 21 days: a short fixed repeat is what
  turns a would-be 5-star user into a 1-star review about nagging. The tail has no cap by the
  product owner's decision, so a user who neither rates nor stops using the app keeps seeing it.
- **Card below the meals.** Less intrusive, but on a day with several meals it is below the fold
  and many users would never see it.
- **State in Firestore.** Same reasons as design 0017: new rules block and DTO for a per-device
  preference, and anonymous users have no `users/{uid}` document.

## Cross-cutting concerns

- **App Store guideline 5.6.1.** The card is our own prompt, which 5.6.1 disallows in favour of
  the system API. Apple itself documents the `action=write-review` link for an in-app control, and
  the card stays clear of the things App Review rejects: it is not modal, has no stars, does not
  ask whether the user likes the app, does not ask for a specific rating and offers no reward.
  Residual risk: a reviewer may still reject it. The fallback is under *Risks*.
- **Google Play.** The card does not use the In-App Review API, so the API's "no questions before
  the flow" rule does not apply, and the general ratings policy (no incentives, no manipulation)
  is met.
- **Privacy.** No data leaves the device. No analytics events in this iteration.
- **Online-only app.** No offline behaviour to design. Opening the store needs no backend.
- **Migration.** None. Existing users start with an empty store, so their logging days count from
  the release.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| App Review rejects the card under 5.6.1 | iOS release blocked | Keep the card policy-clean (above); if rejected, ship iOS with the native `RequestReviewAction` on the third logging day plus the settings row, and keep the card on Android |
| Card shown together with the sign-in spotlight | Two asks at once, both ignored | Spotlight-visible and spotlight-today clauses in the show rule |
| User taps **Rate** and does not actually rate | Never asked again | Accepted; nothing better is observable. The settings row stays |
| Copy becomes false (tips or ads enabled) | Misleading text in a store-reviewed app | Note in *Strings*; review the text whenever monetisation changes |
| Repeats feel naggy | Bad reviews | Escalating 3/5/8/13 then every 21 days, one tap dismisses, shown only on a day with food |

## Test plan

ViewModel unit tests on both platforms, iOS in `iOS/KalorieTests/DashboardViewModelTests.swift`,
Android in `Android/app/src/test/java/antoni/kalorie/features/dashboard/DashboardViewModelTest.kt`,
with the store fake and the injected clock. Each test states why it exists:

1. **Two logging days → not visible; third day's first `onFoodAdded()` → visible.** *(The ask
   comes only after real use, and right away on day 3.)*
2. **Three foods added on the same day → one logging day.** *(It counts days of use, not
   entries.)*
3. **A food added to a past selected day records today, not the selected day.** *(Backfilling is
   not three days of use.)*
4. **Edits through `onFoodConsumedUpdated()` do not record a day.** *(Only adding counts.)*
5. **Not now → hidden; visible again at exactly +3 days, not at +3 days − 1 s; then +5; +8;
   +13.** *(The escalating schedule.)*
6. **After the fourth dismissal the card keeps returning at exactly +21 days, not at +21 days −
   1 s.** *(The slow tail replaces the cap.)*
7. **Rate → hidden and `isCompleted`; never visible again even with dismissals pending.**
   *(Rated users are not asked again.)*
8. **Sign-in spotlight shown today → card not visible; tomorrow → visible.** *(One ask per
   day, and the diary-safety ask wins.)*
9. **Selected day empty → not visible, even when all other conditions hold.** *(The card lives
   under the day summary; an empty day shows the empty state.)*

Settings ViewModel, both platforms:

10. **Rate row tap sets `isCompleted`.** *(Rating from settings stops the dashboard card.)*

Manual check on a device: **Rate** opens the write-review sheet in the App Store on iOS and the
Kalorie listing in Google Play on Android.

## Outcome

_To be filled in once implemented._
