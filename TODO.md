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
- **Google Search Console follow-up (from 2026-11-27)** — the property `https://kalorie-official.web.app/` is
  verified by `backend/hosting/google0ef1b9c5fae18000.html` (never delete it, or verification is lost).
  `/` and `/sk/` are indexed, each with its own canonical, so `/sk/` is not a duplicate of `/`. Still open:
  - `sitemap.xml` showed *Couldn't fetch* until 2026-11-26, when it was resubmitted. Check that *Sitemaps*
    shows *Success* with 6 discovered pages; if it still fails, remove and resubmit it.
  - `/en/` is unknown to Google. *Request indexing* for it in *URL inspection* (the daily quota ran out on
    2026-11-26), then check that its Google-selected canonical is the inspected URL.
  - *Pages* was still processing on 2026-11-26; check which URLs are indexed.
- **Merging the daily goal changes with the onboarding (`fix/daily-goal-kcal-consistency` + `feat/onboarding`)** —
  not urgent until the second of the two branches is merged, but required then. A trial merge on 2026-11-27 conflicts
  in `iOS/Kalorie/Features/DailyGoals/DailyGoalsView.swift` and
  `Android/app/src/main/java/antoni/kalorie/features/dailygoals/DailyGoalsView.kt`. While resolving, on both platforms:
  - The onboarding goal step MUST show the out-of-range section (`kcalOutsideRangeSection` /
    `KcalOutsideRangeSection`) with its checkbox. The step shows only the goal card and a pinned Save, so without the
    section a goal outside the −10 % to +10 % variants leaves Save disabled with nothing to tick.
  - Decide whether the onboarding Save asks for the confirmation too. Its button calls `onSaveTapped` directly, while
    the pushed screen opens the dialog (`isSaveConfirmationVisible`). Recommendation, not yet confirmed by the owner:
    no dialog in the onboarding. If it gets one, the iOS alert has to be attached to the list the onboarding shows.
  - Keep the calls of `onKcalEditingEnded` in both views: on focus loss of the kcal field and on the Save button before
    the dialog opens. The onboarding goal step needs the focus one too, or a typed goal rescales the grams only on a
    macro step, the mode control or Save. No unit test covers the views.
  - Afterwards run the tests on both platforms and check the onboarding goal step by hand with a goal outside the range.
