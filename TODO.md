# Kalorie — TODO

## Planned features

- **Firebase App Check — enforcement** — installed on both clients and registered in the Firebase Console
  (App Attest on iOS, Play Integrity on Android, Cloud project linked in Play Console, debug tokens for the
  maintainer's simulator and device), nothing enforced yet. Storage and Firestore rules only check who
  writes, not what writes, so until enforcement anyone with an account can upload files from a script and
  the Blaze bill is ours.

  **Waiting for the Android release with the [design 0026](docs/design/0026-minimum-supported-version.md)
  gate (minimum supported version) to reach production.** iOS 1.0.3 has the gate, Android is still in
  closed testing. Every build without the gate can never be told to update, so only the gate makes
  enforcement safe for gated users. Remaining, in this order:

  1. Once the gated release is live on both stores, raise `config/app.min_version_ios` /
     `min_version_android` to it (design 0026 runbook).
  2. Watch Console → App Check → Storage and Firestore until the unverified share is only pre-gate
     releases and noise; those can never be updated by force and are locked out by enforcement.
  3. Check the Mac availability in App Store Connect first: `DCAppAttestService.isSupported` is unreliable
     for an iPhone app on an Apple-silicon Mac (not verified), so those users may lose Storage and later
     Firestore. Turn Mac availability off or accept it.
  4. Enforce Storage; after a quiet week, enforce Firestore. Unenforcing in the console is the rollback and
     takes effect within minutes. Leave Authentication unenforced.
  5. Verify by hand: a TestFlight / Play Internal build shows up as verified in the metrics; a debug build
     with an unregistered token gets `permission-denied` on a photo upload once Storage is enforced.

  Every new simulator, emulator or debug device needs its debug token registered (the debug provider prints
  it on first launch: Xcode console / logcat) under Firebase Console → App Check → app menu → Manage debug
  tokens. A token is a credential, do not put it in the shared `Kalorie.xcscheme`.
- **Support development (tips)** — designed in [design 0023](docs/design/0023-support-development-tips.md) and
  implemented on both platforms and switched off by `Constants.Tips.isEnabled`, so the apps ship without
  tips. Blocked on the owner's trade licence being suspended: income from the stores
  likely counts as business income, so decide with an accountant before enabling. To enable: renew the
  licence, create the Play payments profile and the three consumable products (`tip_small`, `tip_medium`,
  `tip_large`), accept the App Store Connect *Paid Apps* agreement and create the same products (neutral
  names, Czech + English descriptions), switch the App Store Connect DSA status from *not a trader* to *trader* (an address, phone and email are then published on the product page) and make the Play listing consistent, then set `Constants.Tips.isEnabled` / `IS_ENABLED` to `true` on both platforms and restore the tips paragraph in
  `backend/hosting/privacy.html` (Apple / Google process the payment, the app receives no card or purchase
  data) and test a real purchase from Internal testing with a license tester.
- **First Play release needs a closed test** — a personal developer account created after Nov 2023
  gets production access only after a closed test with at least 12 testers opted in for 14 days in a
  row. The closed test is running with more than 12 testers; the 14 days have to pass before production
  access can be requested. It is also the cheapest way to find out whether anyone uses the app.
- **Basal metabolic rate (opt-in)** — not designed yet; write a design doc first (Scope: Cross-platform).
  Decisions taken so far: the app offers to estimate the user's BMR (sex, age, height, weight) and the
  dashboard then shows the day's kcal total against it. The offer is never automatic: it reuses the
  [design 0017](docs/design/0017-sign-in-spotlight.md) spotlight and repeats the same way until the user
  enters the data. The inputs are stored in Firestore under `users/{userId}`, so they follow the account
  across devices. Open points for the design doc: `ARCHITECTURE.md` § 944 states there are no goals or
  targets in the app, so this introduces the first one (decide whether to compare against BMR alone or
  against total daily expenditure with an activity level); the formula (Mifflin-St Jeor) belongs in a
  shared KMP module next to `MacroKit`; `users/{userId}` is currently written only by the Apple sign-in
  use case and uses camelCase; health data is a new category, so `backend/hosting/privacy.html`, App
  Privacy (App Store Connect), Data safety (Play), the Firestore rules and account deletion all need
  updating; the spotlight needs a second, independent "last shown" state. Weight: do not add a weight
  field to `users/{userId}`. [Design 0028](docs/design/0028-body-weight-tracking.md) makes
  `users/{userId}/bodyWeights` the single source of the user's weight; BMR reads "the weight valid
  today" with the same query the Dashboard circle uses and stores none of its own.
- **Dashboard read cost** — not urgent. Firestore bills per document read, and the dominant cost is the
  whole-month `foodConsumed` load on cold launch and pull to refresh
  ([ADR 0015](docs/adr/0015-dashboard-caches-a-month-and-derives-the-day.md)): ~300 documents per load at
  ~10 entries a day. Foreground returns, saves, deletes and copies already reload one day only
  ([ADR 0041](docs/adr/0041-dashboard-reloads-only-the-affected-day-after-a-write-or-foreground.md)). A rough
  estimate for 300 active users (2 cold launches a day) is ~180,000 month-load reads a day, i.e. about
  1.5–2 USD a month above the free tier. Revisit if the user count grows by orders of magnitude. The
  cheapest cut then is loading only the selected day on cold launch and the month lazily (about −75 % of
  reads, but the calendar dots arrive late); a per-month `activeDays` document would give the dots for
  1 read but must be kept in sync on every write. A snapshot listener does not help (resume tokens last
  ~30 minutes).
