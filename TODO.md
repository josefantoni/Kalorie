# Kalorie — TODO

## Kinds of data

The app works with three kinds of data. The distinction matters for the items below:

- **Shared food catalogue** (`foodItems`) — a global database visible to everyone. New entries
  are added by the app maintainer, or approved from user submissions.
- **Private user data** (`users/{userId}/foodConsumed`, `mealTypes`) — food entries and meal
  layout, visible only to their owner.
- **User's own meals** — meals a user composes themselves (see below). These stay private and
  do **not** go through approval.

## Planned features

- **Firebase App Check** — Storage and Firestore rules only check who writes, not what writes, so
  anyone with an account can upload files (up to the rules' limits) from a script and the Blaze bill
  is ours. App Check makes the backend accept requests only from genuine builds: App Attest on iOS,
  Play Integrity on Android, a debug provider for simulators and development. Ship both clients in
  monitoring mode first and switch on enforcement for Storage (then Firestore) once the console
  metrics show legitimate traffic, otherwise the app locks itself out. Needs the App Attest
  capability on iOS and the Play Console link on Android. Not blocking the food photos release, but
  do it soon after it.
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
  row. Start it as early as possible; it is also the cheapest way to find out whether anyone uses the app.

- **Store release checklist** (state on 2026-10-16):
  - *Done:* build 4 (1.0) is in Play Internal testing and with Apple in Beta App Review (external TestFlight).
    App Store Connect: product page text, screenshots (iPhone and iPad), review contact and notes, price 0,
    all 175 countries, Food & Drink, age rating 4+, Content Rights, accessibility draft (Dark Interface),
    EU trader status = not a trader, App Privacy published. Play: store listing text and screenshots, Food & Drink
    (no tags), all App content declarations including Data safety, submitted and approved or in review.
  - *Still to do, user's console work:* press **Add for Review** on the iOS 1.0 version once the TestFlight
    review is done.
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
  updating; the spotlight needs a second, independent "last shown" state.
- **Verify the duplicate default meals fix** — default meal types now get fixed ids from MealKit's
  `DEFAULT_MEAL_WINDOWS` (the setup raced and wrote them twice, 10 instead of 5, on 4 of 24 accounts).
  The day after the fix ships to a device, sign in with fresh anonymous accounts on both platforms and
  count `users/{uid}/mealTypes` in prod Firestore: it must be exactly 5 for every new account.
- **Search ignores words wrapped in parentheses** — `searchTerms` (`TextKit/.../SearchTerms.kt`, mirrored by
  `scripts/lib/search-terms.js`, both pinned by `TextKit/fixtures/text-kit-cases.json`) and
  `matchesSearchQuery` split a name on spaces only, so in "Jahodová marmeláda (džem)" the word `(džem)`
  produces the terms `(`, `(d`, `(dz`… and a query for "džem" finds nothing. Fix: strip punctuation when
  splitting (add the cases to the fixture), then run `scripts/backfill-search-terms.js` on prod. Until then
  the catalogue items added in October 2026 whose names carry a parenthesised word (the nine "… marmeláda
  (džem)" items, "Rýže natural (hnědá)", "Rýže vařená (bílá)", "Vejce smažené (volské oko)",
  "Párek (vepřovohovězí)", "Jelito krevní", "Švestková povidla") got the extra paren-free terms written by
  hand into `cz_name_search_terms` / `eng_name_search_terms`; re-saving such an item from the app would
  regenerate the terms and drop them again.

## Android readiness

The shared KMP modules build for Android ([ADR 0037](docs/adr/0037-shared-modules-target-ios-and-jvm-and-are-consumed-by-composite-build.md)).
The Android client in `Android/` ([ADR 0038](docs/adr/0038-android-client-mirrors-the-ios-architecture-natively.md))
has every screen of the iOS app ported; how each step deviated from iOS is recorded in the last version of
this file that still has the steps (`git show 60dcabb:TODO.md`). Nothing is open at the moment.

## Documentation baseline

[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) describes what exists; `docs/adr/` records the
decisions still in effect. Findings are grouped by area and numbered `A<area>-<n>` — only open
ones are listed below; closed findings live in git history, not here.
