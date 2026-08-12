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

- [ ] **Data export** — export consumed food for a chosen interval to PDF or Excel
- [ ] **Own daily meals** — let a user compose a meal they eat regularly instead of entering the
  ingredients every day; visible only to that user, not subject to approval
- [x] **User authentication** — optional Apple ID sign-in so a user keeps their data across a
  device change and can use the app on both an iPhone and an iPad. Signed-out users keep working
  in the device-bound anonymous mode. See
  [docs/design/0001-user-authentication.md](docs/design/0001-user-authentication.md).
  *Remaining: out-of-band configuration — see [docs/SETUP.md](docs/SETUP.md).*
- [x] **Google sign-in** — a second provider alongside Apple, reusing the existing link-first /
  merge-on-conflict path. Apple sign-in stays, both because Guideline 4.8 requires it and because
  it is the recommended provider when a user already has an Apple-linked account. See
  [docs/design/0002-google-sign-in.md](docs/design/0002-google-sign-in.md).
  *Remaining: on-device QA checklist (fresh-install merge, second-device merge, cancellation,
  account chooser after sign-out) — see the Outcome section of
  [docs/design/0002-google-sign-in.md](docs/design/0002-google-sign-in.md).*
- [ ] **Prompt to sign in** — the account screen is only reachable from the toolbar icon; add an
  unobtrusive prompt after the first logged meal so users on a second device sign in early
- [ ] **User-submitted food** — the user photographs the packaging, fills in macros and calories,
  reviews and submits for approval. The submission goes to a separate pending collection rather
  than straight into the shared catalogue, and reaches `foodItems` only once the maintainer
  approves it. Requires Firebase Storage for the photos.
- [ ] **Maintainer admin panel** — a list of pending food submissions (including the packaging
  photo) to approve or reject; approving publishes the item to the shared `foodItems` catalogue.
  Requires a maintainer role (Firebase custom claims) and matching security rules — once done,
  direct client writes to `foodItems` get disabled.
- [ ] **`food_item_id` on `foodConsumed`** — a logged entry currently keeps no reference to the
  catalogue item it came from, which blocks favouriting from the Dashboard and any other feature
  that needs to get back to the source item. Add it as a **required** field, written on create and
  preserved on update; no optional, no backfill. Existing development documents get deleted, which
  is only acceptable before first release — do it now rather than after. Prerequisite for
  *Favourite foods*; details in
  [docs/design/0003-favourite-foods.md](docs/design/0003-favourite-foods.md).
- [ ] **Favourite foods** — the user explicitly marks any number of foods as favourite, from the
  food detail either before logging it or afterwards from the Dashboard; before
  they start typing in the search field, show a "Favourites" section with those foods, most
  recently marked first, and once they type, put the matching favourites at the top of the
  results. See [docs/design/0003-favourite-foods.md](docs/design/0003-favourite-foods.md).
- [ ] **Shared macro calculation module (KMP)** — macro scaling and summation is reimplemented at
  five call sites, two of which round calories differently, so the same food at the same weight
  persists a different value depending on whether it was logged or edited. Extract it into a
  Kotlin Multiplatform module consuming only `Double`/`Int`, with the Swift `ScaledMacros` and
  `DailyMacros` kept as adapters so no other call site changes. Deliberately the smallest useful
  module: it is both a real de-duplication and the probe for whether KMP is worth carrying in this
  project. **Prerequisite: the Gradle build must not run inside iCloud Drive.** See
  [docs/design/0004-shared-macro-calculation-module.md](docs/design/0004-shared-macro-calculation-module.md).
- [ ] **Rank search results by frequency** — order manual search results by how often the user has
  logged each food, so the most used ones come first. Distinct from favourites above: this one is
  derived, not chosen, and the user cannot remove an entry from it.

## Documenting what already exists

Everything except authentication was built before `docs/` existed, so there is no written
baseline to judge a change against — no way to tell "this is a documented decision" from "this
is stale and needs fixing". The authentication review is the precedent: it found a stale TODO
entry, a security-rules regression that made the food catalogue unreadable, and a duplicate
document bug, none of which were visible without reading the area as a whole.

Each area below produces an architecture entry, backfilled ADRs for decisions still in effect,
and audit findings — **not** a design doc. See `docs/README.md` → *Documenting code that already
exists*.

Ordered by payoff. The rule of thumb is to analyse an area just before changing it, so items 1
and 2 come first because planned features land there.

- [ ] **1. Data layer and Firestore model** — `FirestoreDataProvider`, DTOs, domain models,
  `Constants.Firestore`, security rules. Highest leverage: it is the `Backend` contract every
  client shares, everything else depends on it, and both bugs found so far lived here. Open
  questions to settle: numeric `mealTypes` IDs as a cross-platform hazard, the one-shot
  `getDocuments()` read model, and whether `foodItems` needs an index strategy as the catalogue
  grows.
- [ ] **2. Food search and catalogue** — `SearchFoodItemsUseCase`, barcode scanning,
  OpenFoodFacts integration, `CreateFoodItemUseCase`. Do this before the moderation flow and the
  favourites/ranking features, all of which land here. Open questions: prefix search vs. a real
  index, behaviour when the external API is unavailable, and where the boundary between the
  shared catalogue and user submissions should sit.
- [ ] **3. Dashboard and meal types** — day/month views, macro aggregation, meal grouping by time
  range. Already patched twice for multi-device (`ConfirmMealTypesEmptyUseCase`, foreground
  refresh) without the area being reviewed as a whole. Open questions: `monthCache` invalidation,
  and what happens to a food entry that falls outside every meal's time range.
- [ ] **4. Food entry flow** — `AddFoodSheet`, `FoodQuantity`, `FoodConsumedDetail`. Lower
  priority: fewer planned changes, and the recent duplicate-document fix already covered the
  riskiest part.
- [ ] **5. Cross-cutting concerns** — error handling and alert presentation, localization,
  `Components/`. Worth doing only if a pattern here starts causing friction; there is no pending
  feature that depends on it.
