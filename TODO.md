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

- [ ] **Prompt to sign in** — the account screen is only reachable from the toolbar icon; add an
  unobtrusive prompt after the first logged meal so users on a second device sign in early
- [ ] **Packaging photo on a submission** — the other half of the user-submitted-food flow shipped
  in [design 0009](docs/design/0009-catalogue-moderation.md), deferred by that design's Non-goals:
  Firebase Storage is not configured in this project, and whether the project's plan includes free
  Storage quota is unverified. Needs a `storage` block in `firebase.json`, `storage.rules`, and a
  Blaze-plan check before starting. Additive once it lands — `FoodItemSubmissionDTO` gains an
  optional `photo_path`, no other schema change.
- [ ] **Rank search results by frequency** — order manual search results by how often the user has
  logged each food, so the most used ones come first. Distinct from favourites above: this one is
  derived, not chosen, and the user cannot remove an entry from it.
- [ ] **Frequency-derived quick-add gram amounts** — per-user "frequently added weights", gram
  amounts the user logs often for a given food (e.g. 50g oats almost daily, one slice of bread),
  surfaced as quick-add options, likely derived from `foodConsumed` history rather than manually
  maintained. This is the other half of what used to be one combined line here; the first half —
  a food carrying a named, user-chosen package/portion weight selectable as a unit — shipped as
  [design 0008](docs/design/0008-food-portions.md).

## Android readiness

The shared KMP modules build for Android ([ADR 0037](docs/adr/0037-shared-modules-target-ios-and-jvm-and-are-consumed-by-composite-build.md));
no Android app exists yet. What is left is deferred until that work starts.

- [ ] **Apple sign-in on Android** — Firebase offers it only through a web OAuth flow that needs an
  Apple Services ID this project does not have. The iOS app currently signs in with Google only,
  since there is no paid Apple Developer account, so this waits for both.
- [ ] **Golden vectors for the Swift-only rules** — scaling (`FoodItemDomain.scaled(toGrams:)`),
  meal resolution with a pin (`resolvedMealTypeId`), food validation, the OpenFoodFacts mapping, the
  search merge order and export day bucketing live in Swift only; ARCHITECTURE states them as a
  contract but nothing verifies a second client against it. The only shared test vector today is
  `TextKit/fixtures/text-kit-cases.json`. Decide how much to share, and whether some of it should
  move into KMP instead — [ADR 0038](docs/adr/0038-android-client-mirrors-the-ios-architecture-natively.md)
  item 2 sets the rule (pure, deterministic, no I/O), not which rules qualify.
- [ ] **Shared localisation source** — strings live in `Localizable.xcstrings` behind the `L10n`
  enum ([ADR 0019](docs/adr/0019-l10n-enum-over-the-string-catalogue.md)); Android needs its own
  `strings.xml` and nothing keeps the two in step. Open question, not decided.
- [ ] **Build the Android target in CI** — CI runs `jvmTest` for the four modules only. Check that
  the runner's SDK carries API 37, or install it, then add the ExportKit Android build.
- [ ] **Firebase setup for an Android app** — the Firebase console side is done (app
  `antoni.kalorie`, debug SHA-1, `google-services.json`). Still to add: the release and Play App
  Signing SHA-1s, and a real sign-in run to confirm the `docs/SETUP.md` Android section when the
  first sign-in screen is built.
- [ ] **Play Store account deletion** — Google Play's policy has, to my knowledge, required a web
  link for requesting account deletion in addition to in-app deletion. The iOS
  `DeleteAccountUseCase` exists; no web page does. Check the current policy before the first release.

## Documentation baseline

[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) describes what exists; `docs/adr/` records the
decisions still in effect. Findings are grouped by area and numbered `A<area>-<n>` — only open
ones are listed below; closed findings live in git history, not here.

## Audit findings — 2. Food search and catalogue

- [ ] **A2-12 — Ranking cannot be added on top of the current search.** Results are capped at
  `limit(10)` per field — six fields as of [ADR 0024](docs/adr/0024-token-array-field-for-whole-word-search.md)
  — and Firestore returns them in index order, i.e. alphabetically by the matched name. Anything
  cut by that limit is invisible to a re-sort, so *Rank search results by frequency* cannot be
  implemented as a client-side reordering of `SearchFoodItemsUseCase`'s output — it needs either a
  much larger limit (and the read cost that implies) or the frequency data denormalised into the
  query. Constraint, not a bug; recorded so the feature is not designed around a false assumption.
