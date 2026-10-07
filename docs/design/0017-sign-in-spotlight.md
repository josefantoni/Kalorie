# Design: Sign-in spotlight

- **Status:** Implemented in `db586d4` (iOS) and `23f1087` (Android)
- **Scope:** Cross-platform, iOS, Android
- **Date:** 2026-09-28

## Context and scope

Every install runs on an anonymous Firebase account ([ADR 0001](../adr/0001-anonymous-firebase-auth-as-device-identity.md)).
A user who never signs in loses their whole diary with the device — [design 0001](0001-user-authentication.md)
lists this as a risk that is "not solvable technically — surfaced in the UI", and its Outcome leaves
"no proactive prompt to sign in" open as a follow-up. Today the account screen is reachable only
from the account icon in the dashboard toolbar:

- iOS: `ToolbarItem(placement: .topBarLeading)` in `iOS/Kalorie/Features/Dashboard/DashboardView.swift:142`
- Android: `TopAppBar.navigationIcon` in `Android/app/src/main/java/antoni/kalorie/features/dashboard/DashboardView.kt:126`

This document closes that follow-up. The main argument is protecting the diary from device loss.
Signing in early on a second device is a side benefit only: the merge path
([ADR 0002](../adr/0002-merge-anonymous-data-before-switching-accounts.md)) already handles a late
sign-in correctly.

## Goals

- A signed-out user who has logged food sees, at most once a week, a spotlight that dims the
  screen, leaves the account icon lit and shows a bubble with the benefits of signing in.
- The spotlight also teaches where the account icon is.
- A signed-in user never sees it.

## Non-goals

- A spotlight for any other feature. No generic coach-mark framework — this is one overlay.
- A different sign-in UI. The bubble opens the existing account sheet; the sign-in flows, merge
  handling and error alerts stay in `AccountViewModel`.
- Syncing the "last shown" date across devices. The anonymous identity is per device, so the
  state is too.

## Design

### Step 0 — iOS spike (do this first, it decides the iOS visual)

**Open question:** can SwiftUI report the on-screen frame of a button inside a `ToolbarItem` on
iOS 26? Toolbar items are hosted by the UIKit navigation bar (Liquid Glass on iOS 26). Nothing
public confirms or rules out that `GeometryReader`, `anchorPreference` or `onGeometryChange` report
correct window coordinates from inside it. A web search found nothing reliable either way. The
only related known issue is that TipKit popover tips do not show on a `ToolbarItem` button unless
it has a button style such as `.plain`. That suggests toolbar hosting is special-cased.

Spike, on a throwaway branch and never committed:

1. On the account `Button` in `DashboardView.swift:143`, add
   `.onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { accountFrame = $0 }`
   with a `@State private var accountFrame: CGRect = .zero` on `DashboardView`.
2. On the `NavigationStack` itself (`DashboardView.swift:30`), not on the `List`, add
   `.overlay { Rectangle().stroke(.red, lineWidth: 2).frame(width: accountFrame.width, height: accountFrame.height).position(x: accountFrame.midX, y: accountFrame.midY).ignoresSafeArea().allowsHitTesting(false) }`.
3. If `onGeometryChange` never fires (the frame stays `.zero`), retry with
   `anchorPreference(key:value: .bounds)` on the button and `overlayPreferenceValue` on the
   `NavigationStack`.
4. Check in the simulator and take a screenshot of each case:
   - iPhone 17 Pro, portrait, then rotate to landscape and back;
   - iPad simulator, portrait and landscape;
   - Dynamic Type at the largest accessibility size;
   - after opening and closing the account sheet.

**Pass:** the red rectangle sits on the account icon, including its Liquid Glass capsule, within
about 2 pt in every case, follows rotation, and is drawn **above** the navigation bar rather than
under it. → Build the spotlight as specified below.

**Fail:** any case is off, the frame is never reported, or the overlay renders under the bar. →
iOS uses the fallback: the native `.popover(isPresented:)` + `.presentationCompactAdaptation(.popover)`
on the toolbar button, copying the info popover at `DashboardView.swift:67–95`. The system then
anchors the arrow and there is no dimming. The show rule, the 10 s timer, the bubble content and
the tests below stay the same. Android keeps the spotlight either way.

Do **not** use a hard-coded position (top-left corner plus safe area) or move the account button
out of `.toolbar` into a custom header. The first breaks across devices, orientation and Dynamic
Type. The second breaks the Liquid Glass bar.

### Show rule (shared, lives in the dashboard ViewModel)

```
shouldShow = authProvider.isAnonymous
          && monthCache contains at least one entry
          && (lastShownAt == nil || now − lastShownAt ≥ 7 days)
          && no sheet, alert, confirmation or popover is presented
```

- Evaluate it at the end of a **successful** `onAppear()` and `onRefresh()`
  (iOS `DashboardViewModel.swift:155` / `:174`, Android `DashboardViewModel.kt:145` / `:160`).
  `onAppear` runs once per process and the app can stay alive for days, which is why `onRefresh`
  (return to foreground) is also needed.
- Also evaluate it after a food is saved (`onFoodConsumedUpdated()`) and when the add-food sheet is
  dismissed, so the first logged food is warned about right away and not at the next app open.
  Whichever of the two happens last wins, because the sheet is still up while the reload runs.
- The "presented" check covers `showSettings`, `showAddFoodSheet`, `showCalendarSheet`,
  `showAccountSheet`, `alertItem`, the delete confirmation and the meal-section info/copy popovers.
  A presentation requested while another one is up is silently dropped by SwiftUI.
- `lastShownAt` is written **when the spotlight becomes visible**, not when it is dismissed. A
  spotlight that times out or is tapped away still counts as shown.
- There is no display cap: it repeats weekly for as long as the user stays anonymous. This is the
  product decision.
- A signed-in user never matches `isAnonymous`. After sign-in the root view is rebuilt keyed on the
  user id ([design 0001](0001-user-authentication.md), "Stale view state across a UID change"), so
  a stale ViewModel cannot keep showing it.
- The ViewModels do not have `authProvider` today. Inject `AuthProviderProtocol` into
  `DashboardViewModel` on both platforms (iOS `DashboardViewModel.swift:103`, wired in
  `DashboardConfigurator.swift:18`; Android `DashboardViewModel.kt:77`, wired in
  `DashboardConfigurator.kt:47`). Also inject the "now" clock (`() -> Date` / `() -> Instant`)
  so tests can move time.

### Persistence

This is the first local key-value preference in either client — neither uses `UserDefaults`,
`@AppStorage`, `SharedPreferences` or `DataStore` today. Put it behind a one-property protocol so
the ViewModel tests use a fake:

- `SignInSpotlightStoreProtocol { var lastShownAt: Date? { get set } }`, with the concrete type on
  `UserDefaults.standard` under the key `signInSpotlightLastShownAt` and a `…Fake` for tests.
- Android: the same interface on plain `SharedPreferences` (no new dependency — do **not** add
  DataStore for one value).

Firestore was rejected: it would need a new security-rules block and a DTO, and anonymous users
have no `users/{uid}` parent document (`docs/ARCHITECTURE.md` § 1.2).

### Presentation

- **Dim:** black at the same opacity as the system alert dimming (≈ 0.4), full screen including
  the navigation bar and status bar, with a circular cut-out around the account icon (6 pt / 6 dp
  padding). Fade in and out over 0.25 s. No scale animation when Reduce Motion is on.
- **Bubble:** below the icon, leading-aligned with it, with an arrow pointing up at the icon's
  centre. Content:
  - a title, e.g. "Keep your diary safe";
  - one or two sentences on the benefits (the diary survives losing or replacing the phone and is
    available on another device);
  - two buttons: **Sign in** (primary) and **Not now**.
- **Dismissal:** automatically after **10 seconds**, or earlier by tapping anywhere. Behaviour:
  - **Sign in**, or a tap on the cut-out icon itself → dismiss and set `showAccountSheet = true`;
  - **Not now** or a tap on the dimmed area → dismiss only.
- **Accessibility:** when VoiceOver (iOS `UIAccessibility.isVoiceOverRunning`) or TalkBack
  (Android `AccessibilityManager.isTouchExplorationEnabled`) is on:
  - the 10 s timer is disabled — the user must be able to finish reading (WCAG 2.2.1);
  - accessibility focus moves to the bubble title on appear.
- **iOS placement:** the overlay goes on the `NavigationStack`, where the spike proved it draws
  above the bar. Use the frame from `onGeometryChange`, and the cut-out via `.mask` with
  `.blendMode(.destinationOut)` or an even-odd `Path`. The timer is a `.task` on the overlay
  (`try? await Task.sleep(for: .seconds(10))`, then dismiss) and is cancelled automatically when
  the overlay goes away.
- **Android placement:** wrap the `Scaffold` in a `Box` and draw the overlay as its last child.
  Take the icon bounds from `Modifier.onGloballyPositioned { it.boundsInWindow() }` on the
  navigation `IconButton`, and cut out with `Canvas` + `BlendMode.Clear` on a layer with
  `CompositingStrategy.Offscreen`. The timer is a `LaunchedEffect(visible)` with `delay(10_000)`.
- **Strings:** add them wherever `account_anonymous_description` exists, on both platforms (iOS
  `L10n.swift:36` and its string catalog, Android `res/values*/strings.xml`), in every locale that
  key has.

## Alternatives considered

- **Inline card on the dashboard.** Less intrusive, but it does not teach where the account icon
  is and it takes up list space permanently. Rejected by the product owner in favour of pointing
  at the icon.
- **Native popover with an arrow and no dimming.** Robust, because the system anchors it. Less
  visible. Kept as the iOS fallback if the spike fails.
- **TipKit `.popoverTip`.** It persists its own state, but a tip closed with its × is invalidated
  for good, so the weekly repeat would need workarounds. Android has no equivalent, so the show
  rule would be written twice in two different forms. It also has the `ToolbarItem` known issue
  noted above.
- **Hide it after 3 seconds.** Too short to read two sentences, and it breaks WCAG 2.2.1.
  Replaced by 10 s with tap-to-dismiss.

## Cross-cutting concerns

- Online-only app — no offline behaviour to design.
- No App Store / Play policy concern: the prompt is dismissible, does not block use and sign-in
  stays optional.
- No data migration.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Toolbar frame wrong on iOS | Cut-out and arrow point at nothing | Step 0 spike before any production code; native popover fallback |
| Spotlight fired while a sheet is up | Silently dropped or shown late over the wrong screen | "Nothing presented" clause in the show rule |
| Weekly repeat feels naggy | Users annoyed | Product decision; only after the user has logged food; one tap dismisses |
| Timer cuts off screen-reader users | Cannot read the bubble | Timer disabled while VoiceOver / TalkBack is running |

## Test plan

ViewModel unit tests on both platforms, iOS in `iOS/KalorieTests/DashboardViewModelTests.swift`,
Android in `Android/app/src/test/java/antoni/kalorie/features/dashboard/DashboardViewModelTest.kt`.
Each test names the reason it exists:

1. **Anonymous user with logged food, never shown → shown** after `onAppear`, and `lastShownAt`
   equals the injected now. *(The whole point: an unprotected diary gets a warning.)*
2. **Signed-in user with logged food → never shown**, even when `lastShownAt` is nil.
   *(A signed-in user's data is already safe; showing it would be noise.)*
3. **Anonymous user with no entries → not shown.** *(Nothing to lose yet; an empty-app spotlight
   is noise on first launch.)*
3a. **First food logged → shown right away** via `onFoodConsumedUpdated`. *(The first food is the
   moment to warn; waiting for the next app open loses it.)*
4. **Shown 6 days ago → not shown; shown 7 days ago → shown on `onRefresh`.** *(Weekly cadence,
   and a long-running process still gets it on foreground.)*
5. **Any presentation flag true → not shown, and `lastShownAt` unchanged.** Parameterise over
   every flag. *(A dropped presentation must not burn the week.)*
6. **Sign in action → spotlight hidden and `showAccountSheet == true`.** *(The bubble is a way into
   the existing flow, not a new one.)*
7. **Not now → hidden, `showAccountSheet == false`, `lastShownAt` unchanged from when it
   appeared.**
8. **`onAppear` fails (fetch throws) → not shown.** *(No spotlight over an error alert.)*

Verify by hand — no automated test covers these:

- The 10 s auto-dismiss, and that it does not fire with VoiceOver / TalkBack on.
- Cut-out and arrow alignment on the Step 0 device matrix, and on an Android emulator in portrait
  and landscape.
- The overlay dims the navigation bar and status bar, not just the list.
- Reset for testing: delete the app (iOS) / clear app data (Android) to clear `lastShownAt`.

## Outcome

Implemented on iOS and Android, with these differences:

- The Step 0 spike was checked by hand and passed, so iOS uses the spotlight, not the native popover fallback.
- The bubble has no **Sign in** / **Not now** buttons. Tapping the cut-out icon opens the account sheet, tapping anywhere else dismisses. VoiceOver and TalkBack get a "Sign in" action and an escape / dismiss action on the bubble instead, because the timer is off for them.
- The arrow is always centred on the icon and the bubble is positioned to fit it (corner radius 16, 8 pt screen margin), instead of the bubble being leading-aligned with the icon.
- The meal-section info popover index (`macroPopoverIndex`) moved from view state into `DashboardViewModel` so the show rule can see it.
- A long press on the **+** button forces the spotlight in debug builds. It is inert in release: `#if DEBUG` on iOS, `BuildConfig.DEBUG` on Android.
