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
- **Community product photos and notification bell** — designed in
  [design 0031](docs/design/0031-community-product-photos-and-notification-bell.md); rules and iOS first.
  - **Privacy** — new upload path: update `backend/hosting/privacy.html` (every language), re-confirm App
    Privacy and Data safety. Owner task, before either platform ships the feature.
- **Propagate catalogue name and photo changes into users' copies** — analysis only, nothing implemented;
  write an ADR (Scope: Backend, partly replaces [ADR 0009](docs/adr/0009-denormalised-nutrition-snapshots.md))
  and a design doc (Scope: Backend) first, then implement.
  - **Bug:** an admin edits a name while approving a submission (or later in the editor) and the new
    name shows in the catalogue and in the report detail, but not on the Dashboard or in search. Seen in
    prod: `foodItems/8593803625270` has `cz_name` "Madeta tvaroh s jogurtem jahodový", the author's
    `foodConsumed` entry and `users/{uid}/stats/foodFrequency` still say "Tvaroh s jogurtem jahodový".
    The author logged the food while the submission was still pending, so their copies hold the
    submitted name. Approval does write the edited form, so this is not a client bug.
  - **Cause:** by design. ADR 0009 stores a snapshot of the food in `foodConsumed`, `favouriteFoods`,
    `myCreatedMeals` and (design 0020) `stats/foodFrequency`, and forbids background reconciliation.
    Security rules forbid a maintainer's device from writing another user's `users/{uid}/…`, so no client
    can fix it.
  - **Decision taken:** the owner wants the name to apply everywhere, including logged diary entries.
    Macros, grams and history stay untouched (ADR 0009's reason about macros still holds). Only
    `cz_name`, `eng_name` and `photo_url` propagate. The owner's Firebase project is on the Blaze plan
    already (needed for Storage, [design 0022](docs/design/0022-food-photos.md)).
  - **Design:** first Cloud Functions code in the repo, `backend/functions`, region `europe-west1`
    (Firestore is `eur3`), 2nd gen `onDocumentWritten("foodItems/{id}")`. Return early unless one of the
    three fields differs between `before` and `after`; creation counts (before is absent). Never write
    to `foodItems`, so there is no trigger loop. Deploy by hand with `firebase deploy --only functions`
    like rules and indexes ([SETUP.md](docs/SETUP.md)); no CI step and no new secret. Per copy:
    - `foodConsumed`: collection group query `food_item_id == id`; update `cz_name`, `eng_name` only (no
      `photo_url` field exists there). Needs a `COLLECTION_GROUP` single-field index exemption on
      `foodConsumed.food_item_id` in `backend/firestore.indexes.json`, otherwise the query fails.
    - `favouriteFoods`: collection group query `id == id`; update all three. Same kind of index exemption
      on `favouriteFoods.id`.
    - `stats/foodFrequency`: variant A, chosen over a helper field: read every `stats` document
      (collection group, one read per user per name change), and for those with `entries.<id>`, update
      `entries.<id>.item.cz_name|eng_name|photo_url` using `FieldPath` keys (a barcode key starts with a
      digit, see design 0020). Cost is trivial at the current user count; revisit past a few thousand users
      (variant B: an indexed array of food ids kept by both clients, plus a migration).
    - `myCreatedMeals`: leave alone. Ingredients (array of maps) cannot be queried, a scan is expensive,
      and design 0006 already refreshes them when the meal is opened in the editor.
    - Batch writes of at most 500 (`Constants.Firestore.batchWriteLimit`); make it idempotent so a retry is safe.
  - **Open points for the analysis:** how ADR 0009 is amended without editing it (new ADR that replaces it
    in part); whether `eng_name` is always set; photo removal (`photo_url` deleted on the catalogue item)
    must delete the field in copies, not write `null`; whether OpenFoodFacts-origin copies
    ([ADR 0044](docs/adr/0044-external-favourites-are-upgraded-when-the-food-enters-the-catalogue.md))
    being renamed on catalogue creation is wanted; the function's service account needs Firestore access
    (default is enough); update `docs/ARCHITECTURE.md`, `docs/SETUP.md` and the "no server code" lines in
    designs 0015/0020 are frozen and stay as they are.
  - **Tests:** Firestore emulator test of the function: names change in all three copy types, macros and
    grams do not, an edit that touches none of the three fields writes nothing, creation of a catalogue
    item renames copies made while it was pending.
  - **One-off:** the existing mismatch in prod (the author's `foodConsumed` and `foodFrequency`) is fixed
    by re-saving the item in the admin editor once the function is deployed. Do not write to prod without
    the owner's approval.
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
