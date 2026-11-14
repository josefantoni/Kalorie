# Kalorie — TODO

## Planned features

- **User notifications as tickets (branch `feat/user-notifications`, unpushed)** — the spec is
  [design 0033](docs/design/0033-user-notifications-for-own-reports-and-photos.md), rewritten
  2026-11-14 after the owner's decisions; it is the source of truth, read it whole first. The branch's
  existing commits (`9f447917` rules, `74525f70` strings, `7feaea02` iOS, `506e7651` Android) implement
  the **previous** draft (notification disappears on resolution, `seen_at` written once, fixed doc ids).
  Rework them; fixup/squash + `--force-with-lease` is fine on this branch. One commit per platform, order:
  backend → iOS → Android → docs (`ARCHITECTURE.md` § 7.7, § 1.6, § 1.7). Commit 0033 itself as
  `[DOCS]` first. What the doc does not spell out:
  - **Fixed ids are gone everywhere.** Every caller of `FoodItemReportDomain.id(barcode:userId:)`,
    `PhotoSuggestionDomain.id(itemId:userId:)`, `.storagePath(itemId:userId:)`, `.ids(fromDocumentId:)`
    switches to the document's own id / stored `photo_url`: iOS `SubmitFoodItemReportUseCase.swift:47`,
    `SubmitPhotoSuggestionUseCase.swift:41,43,60`, `FetchMyFoodItemReportUseCase.swift:33`,
    `FetchMyPhotoSuggestionUseCase.swift:33`, `MarkUserNotificationSeenUseCase.swift:37–39`,
    `WithdrawUserNotificationUseCase.swift:43`, `PhotoSuggestionRemover.swift:30,34`,
    `DeleteFoodItemReportUseCase.swift:33` (the use case is replaced by `ResolveFoodItemReportUseCase`),
    `ApprovePhotoSuggestionUseCase.swift:66–85` (`removeAllSuggestions` is deleted, not ported). Domain
    models need an `id` field decoded from the document id (`@DocumentID` on iOS; on Android check how
    `FirestoreDataProvider.kt` maps ids). Android counterparts have the same names under
    `core/usecases/` and `core/models/`.
  - **`PhotoSuggestionRemover`** must take the storage path from `photo_url`, not from the uid: an
    anonymised ticket has no `suggested_by`.
  - **Reports are no longer grouped by barcode.** iOS `ModerationReportsViewModel.swift:84–95`
    (`Dictionary(grouping:)`, `ReportGroup.id = barcode`, `onGroupReportsChanged`) and
    `NotificationsViewModel.swift:17,106–116,213,239` (`.report(barcode:reports:)`, `onReportRowChanged`)
    become one row per ticket; `ModerationCatalogueEditorViewModel` takes one optional ticket instead of
    `reports: [FoodItemReportDomain]` (`:25,47,161–179`), and its view hides plain *Save*
    (`ModerationCatalogueEditorView.swift:76`) when a ticket is present; `ForEach(…, id: \.reportedBy)`
    at `:29` goes. `ModerationConfigurator.swift:132` wires the new use case.
  - **`ModerationPhotoSuggestionViewModel.swift:66`** catches `itemAlreadyHasPhoto`; the case is
    removed with the guard (`ApprovePhotoSuggestionUseCase.swift:12,55`). Check that
    `UpdateFoodItemUseCase` deletes the previous `catalogPhotos` file when replacing a photo; if not,
    that is a finding to report, not to fix silently.
  - **Account deletion** (iOS `DeleteAccountUseCase.swift:159–176`, Android `DeleteAccountUseCase.kt`
    ≈ 112–130): drop the suggestion-file listing and both `batchDeleteAsync` calls; replace with the
    anonymising update. It needs `FieldValue.delete()` for the owner field, which `updateAsync` (an
    `Encodable`) cannot express — add one provider method for it, with a throwing default in the
    protocol extension (`FirestoreDataProvider.swift` ≈ line 50, the pattern used there) so the test
    fakes compile. The ids lookup by `reported_by` / `suggested_by` stays (that is what the two
    `… ASC, … DESC` indexes are kept for).
  - **Locale**: map the displayed app language to `cs` / `en` / `sk`; anything else → `en`. The
    pre-filled response must be read from that localisation, not the maintainer's — verify by hand with
    the maintainer's app in Czech and the author's in English.
  - **Rules tests** (`firestore-rules-tests/firestore.rules.test.js`): the existing `seen_at`
    "second write denied" cases from `9f447917` invert (repeated writes are now allowed). Storage rules
    have no test harness in the repo; say so in the commit instead of claiming coverage.
  - **Deploy**: tell the owner to run `firebase deploy --only firestore:rules,firestore:indexes,storage`
    and wait for the four new indexes before testing a client. No new CI step or secret is needed.
  - **Do not duplicate screens**: the user list stays inside `NotificationsView` / `NotificationsViewModel`
    with `isMaintainer`; the response sheet is one shared view for report and photo, accept and reject.

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
