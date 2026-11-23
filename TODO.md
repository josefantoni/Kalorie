# Kalorie — TODO

## Planned features

- **Support development (tips)** — designed in [design 0023](docs/design/0023-support-development-tips.md) and
  implemented on both platforms and switched off by `Constants.Tips.isEnabled`, so the apps ship without
  tips. Blocked on the owner's trade licence being suspended: income from the stores
  likely counts as business income, so decide with an accountant before enabling. To enable: renew the
  licence, create the Play payments profile and the three consumable products (`tip_small`, `tip_medium`,
  `tip_large`), accept the App Store Connect *Paid Apps* agreement and create the same products (neutral
  names, Czech + English descriptions), switch the App Store Connect DSA status from *not a trader* to *trader* (an address, phone and email are then published on the product page) and make the Play listing consistent, then set `Constants.Tips.isEnabled` / `IS_ENABLED` to `true` on both platforms and restore the tips paragraph in
  `backend/hosting/privacy.html` (Apple / Google process the payment, the app receives no card or purchase
  data) and test a real purchase from Internal testing with a license tester.
- **In-app review prompt** — neither platform asks for a store rating yet. Ratings raise the store
  conversion rate and feed ASO. Use the system prompt (`RequestReviewAction` / `SKStoreReviewController` on
  iOS, Play In-App Review API on Android), triggered only for an engaged user, e.g. after a food entry on the
  third distinct day with entries, never on first launch or mid-flow. The OS throttles how often the dialog
  appears and may show nothing, so the app must not depend on it.
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
- **Google Search Console follow-up (from 2026-11-25)** — the property `https://kalorie-official.web.app/` is
  verified by `backend/hosting/google0ef1b9c5fae18000.html` (never delete it, or verification is lost).
  `sitemap.xml` was submitted on 2026-11-23 and showed *Couldn't fetch*, which is normal for a new property.
  Check that *Sitemaps* shows *Success* with 9 discovered pages; if it still fails, remove and resubmit it.
  *Request indexing* for `/`, `/sk/` and `/en/` hit the daily quota on 2026-11-23, so retry it in *URL
  inspection*. Then check *Pages* for which URLs are indexed and that `/sk/` and `/en/` are not reported as
  duplicates of `/`.

## Fixes

- **Flaky iOS test `FetchUserNotificationsUseCaseTests.test_fetch_queriesOnlyTheUsersVisibleTickets_inBothCollections`**
  — failed once on CI (run 44741267745) with only the `photoSuggestions` query recorded and the
  `foodItemReports` one missing. `FetchUserNotificationsUseCase` runs both queries concurrently with
  `async let`, and `TicketDataProviderFake` (`iOS/KalorieTests/TicketTestSupport.swift`) appends to
  `orderedQueries` from both `loadAsync` overloads without synchronisation, so one append can be lost.
  Fix: guard both appends and the read with an `NSLock` (same pattern as
  `arrayContainsValuesLock` in `SearchFoodItemsUseCaseTests.swift`). Own `[FIX]` commit on its own
  iOS branch; check the other fakes in `TicketTestSupport.swift` for the same unguarded mutation.
