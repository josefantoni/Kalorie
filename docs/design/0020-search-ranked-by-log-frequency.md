# Design: Search results ranked by how often the user logs each food

- **Status:** Implemented in `c44ee51` (iOS) and `6cc77c0` (Android)
- **Scope:** Backend, Cross-platform, iOS, Android
- **Date:** 2026-10-06

## Context and scope

`TODO.md` asks for manual search results ordered by how often the user has logged each food, most
used first — derived from history, not chosen like favourites ([design 0003](0003-favourite-foods.md)),
and not removable by the user.

Today `displayedResults` (iOS `AddFoodSheetViewModel.swift:233`, Android
`AddFoodSheetViewModel.kt:114`) concatenates four tiers — matching favourites, matching created
meals, matching own submissions, then the catalogue results of `SearchFoodItemsUseCase` — with no
ranking inside any tier. Finding **A2-12** in `TODO.md` records why a re-sort of that output is not
enough: the catalogue query is capped at `limit(10)` per field and returned in index order, so a
food the user logs every day can be cut before the client ever sees it. The frequency data has to
reach the client independently of the catalogue query.

`foodItems` is shared by every user, so a per-user count cannot live on the catalogue document.
It has to live under `users/{userId}`.

This document covers storing the counts, keeping them current, and ranking with them. It does not
change `SearchFoodItemsUseCase` or its queries.

## Goals

- A food the user has logged before is listed above foods they have not, and more often logged
  foods above less often logged ones, within the ordering rules below.
- A frequently logged food appears in the results for a matching query even when the catalogue
  query's `limit(10)` cut it.
- Cost on the Firestore free tier (50k reads / 20k writes per day): **one extra read per opening of
  the add-food sheet and one extra write per logged entry**, independent of how many distinct foods
  the user has logged.
- Any failure of the frequency read or write leaves logging and search working exactly as today.

## Non-goals

- **No server code.** No Cloud Functions, no REST endpoint, no rules change.
- **No backfill from existing `foodConsumed` history.** Counts start at zero when this ships and
  build up as the user logs. See *Alternatives considered*.
- **No decrement.** Deleting or editing a logged entry does not change any count.
- **Copying a meal to another window** ([design 0016](0016-copy-meal-to-another-window.md),
  `CopyFoodsConsumedUseCase`) does not count. It holds `FoodConsumedDomain`s, not `FoodItemDomain`s,
  so it has no item snapshot to write, and it is a bulk repeat rather than a choice of a food.
- **Not migrated on anonymous → signed-in merge.** See *Cross-cutting concerns*.
- **Frequency-derived quick-add gram amounts** (the other `TODO.md` item) is a separate feature.
  It may later reuse this document, but nothing here is designed for it.
- No change to the empty-query state of the sheet.
- No change to the external OpenFoodFacts fallback gate ([ADR 0023](../adr/0023-external-search-gate-includes-favourites-and-meals.md)).
  It stays `displayedResults.isEmpty`, which now also counts frequency candidates as "found". That is
  the same reasoning ADR 0023 applies to favourites and created meals.

## Design

### Data model — one document per user

```
users/{userId}/stats/foodFrequency
```

It is a single document, not a collection. One read returns every count, which is the reason for
this design (see *Alternatives considered*). Path helpers: `Constants.Firestore.stats(userId:)` →
`"users/\(userId)/stats"` and a document id constant `foodFrequency`, next to the existing helpers
(iOS `Constants.swift:62-66`, Android `Constants.kt:51-55`).

```
entries: {
  "<food id>": {
    count: Int,                 // incremented with FieldValue.increment(1), never written directly
    last_logged_at: Double,     // epoch seconds, ADR 0008
    item: { ... }               // snapshot of the logged FoodItemDomain
  },
  ...
}
```

- **The map key is the food's id**: barcode, uppercase UUID or created-meal UUID. It is the same
  value as `food_item_id` on `foodConsumed`. An `external` barcode that later enters the catalogue
  under the same barcode keeps its count. The next log overwrites `item`, `food_item_kind` included,
  with the catalogue version, matching "the catalogue outranks OpenFoodFacts" (ARCHITECTURE § 1.4).
- **`item` uses the `FavouriteFoodDTO` field set minus `favourited_at`.** Introduce
  `FoodFrequencyItemDTO` by copying `FavouriteFoodDTO` (iOS
  `Core/Networking/FireStone/FavouriteFoodDTO.swift`, Android `core/networking/FavouriteFoodDTO.kt`), including
  `food_item_kind`, `portions`, `measure_unit` and `alcohol_by_volume`. ARCHITECTURE § 1.4 explains
  why each of those is carried on favourites, and the same reasons apply here. Do not copy the
  catalogue's `*_lowercase` / `*_folded` / `*_search_terms` fields. They are derived, and matching
  derives them locally (below).
- On read, an entry without a decodable `item` is dropped, not failed. Never fail the whole
  document over one entry.

Size: an entry is roughly 0.5–1 KB, and the document limit is 1 MiB. **The fetch use case trims
the map to the 300 most recently logged entries** (the oldest `last_logged_at` goes first, ties
broken by the lower `count`). Ranking by `count` first would delete a food logged for the first time
(`count` 1) as soon as 300 entries had a count of 2 or more, so it could never gain a count. When the loaded map is larger than that, it removes the excess in one
`updateData` with `FieldValue.delete()` per removed key. This runs rarely and costs one write.
Without the trim, a long-time user eventually hits the size limit, every frequency write starts
failing, and because those failures are swallowed by design, nobody would notice.

### Writing — `RecordFoodFrequencyUseCase`

Make this a new use case on both platforms, not part of `SaveFoodConsumedUseCase`. Signature:
`(item: FoodItemDomain, date: Date) async throws`.

Call it from `FoodQuantityViewModel` **after** `saveFoodConsumed` succeeds (iOS
`FoodQuantityViewModel.swift:327`, the same place on Android), in its own `do/catch` that only does
`Log.warning(error, category: Constants.LogCategory.foodQuantity)`. A frequency failure must never
show the error alert or block `onSaved()`. The write is not awaited either, so it cannot delay closing
the screen: an unstructured `Task` on iOS, and on Android a launch in
`KalorieApplication.applicationScope`, because `viewModelScope` is cancelled as soon as the sheet
closes. `SaveFoodConsumedUseCase` and its tests stay unchanged.
`SaveFoodConsumedUseCase` has exactly one caller in production code on iOS, so this is the only log
path that counts.

**The write is not `setAsync`.** `setAsync` replaces the whole document (ARCHITECTURE § 1.5). That
would erase every other entry, and a read-modify-write would cost an extra read per log. Add one
provider method on both platforms that does the following:

1. `updateData` with **`FieldPath` keys**, not dotted strings. A barcode key starts with a digit and
   a UUID key contains `-`, so a dotted string path either fails to parse or needs backtick quoting.
   Use `FieldPath(["entries", id, "item"])` on iOS and `FieldPath.of("entries", id, "item")` on
   Android. Write three keys:
   - `entries.<id>.item` → the encoded `FoodFrequencyItemDTO` map. Write **the whole `item` as one
     field** so it is replaced wholesale. Do not use `setData(merge: true)` for it: merge deep-merges
     nested maps, so an optional the catalogue has since dropped (`alcohol_by_volume`, `portions`,
     `energy_kj`) would survive in the old snapshot forever.
   - `entries.<id>.last_logged_at` → epoch seconds.
   - `entries.<id>.count` → `FieldValue.increment(1)`.
2. If that fails with **not found** (the document does not exist yet), fall back to
   `setData(["entries": [id: [item, last_logged_at, count: 1]]], merge: true)`. Handle not found
   **inside the provider**, before the existing error mapping (iOS `mapError`,
   `FirestoreDataProvider.swift:317`; Android `performFirestoreCall`). Otherwise the use case only
   sees a mapped error it cannot tell apart from anything else.

The DTO is encoded the way each platform already encodes: `Firestore.Encoder()` on iOS and
`FirestoreDataMapper.encode` on Android.

**Provider protocol and test fakes:** iOS has about 40 hand-written
`FirestoreDataProviderProtocol` conformers in `KalorieTests/`, and Android has a single
`FirestoreDataProviderFake.kt`. Follow the precedent at iOS `FirestoreDataProvider.swift:34-50`:
put a default implementation in a protocol extension so the existing fakes keep compiling. The
default for the new write and trim methods **throws** (e.g. a new
`FirestoreDataProviderError.unsupported`), so a forgotten override in the real provider surfaces as
a logged warning rather than a silent no-op. Override the methods in the real provider and in the
fakes of the new use cases' tests. On Android, add them to `FirestoreDataProviderFake.kt`.

### Reading — `FetchFoodFrequencyUseCase`

Use `loadAsync(id: "foodFrequency", from: stats(userId))` and return `[String: FoodFrequencyEntry]`
(domain: `count`, `lastLoggedAt`, `item: FoodItemDomain`). If the document is missing, return an
empty map. If there are more than 300 entries, trim as above (warn on failure, still return the
trimmed map).

Call it in `AddFoodSheetViewModel.onAppear` (iOS `:511`) as a fourth `async let` next to favourites,
meals and submissions, with the same `do/catch` + `Log.warning` pattern. A failure leaves an empty
map, which reproduces today's ordering exactly. Store it in a new published property, e.g.
`foodFrequency`.

This costs one read per sheet opening. Do not add an app-level cache. At about 10 sheet openings
per user per day, it is not worth the invalidation logic.

### Matching — `matchesSearchQuery` from ADR 0040

Frequency candidates stand in for catalogue results that `limit(10)` cut off. They must therefore
match the way the catalogue query matches, so that "mlék" finds a frequently logged
"Polotučné mléko" even when the catalogue did not return it.
[ADR 0040](../adr/0040-search-is-diacritic-insensitive-everywhere-and-drops-the-lowercase-queries.md)
defines that rule as `matchesSearchQuery(name, query)` in TextKit, and makes favourites, created
meals and submissions use it too. Candidates use it on `czName` and `engName`. If this design
lands before ADR 0040, add the function as specified there, tests included, and ADR 0040 reuses
it.

### Ranking — in `displayedResults`

Keep this per client, with one test per platform. That follows ADR 0039 rule 3, which keeps the
merge itself per client because it is a few lines. Both platforms must produce the same order:

1. **Favourites stay first**, as today, sorted among themselves by `count` descending. The sort is
   stable, so favourites with equal counts (including 0) keep their current `favourited_at` order.
   Favourites stay above everything because they are chosen and the rest is derived. That is the
   distinction the `TODO.md` item draws.
2. **Everything else forms one list**, in today's order, followed by the frequency candidates:
   created meals → own submissions → catalogue results → candidates. Deduplicate by id (first
   occurrence wins), exclude favourites, then **stable-sort by `count` descending**. Foods with
   count 0 keep today's relative order below every counted food.
   - **Frequency candidates** are entries whose `item` matches `matchesSearchQuery` on either name.
     Exclude entries with `food_item_kind == created_meal`, because created meals already come from
     `myCreatedMeals`. A stored meal snapshot would show stale values after an edit, and a deleted
     meal after a delete. A created meal still gets its count from the map by id.
   - Tie on `count`: the more recent `last_logged_at` first. Only counted foods are affected by
     this, and both platforms must apply it.
3. An empty query still returns `localFoodItems` unchanged (`displayedResults`' first `guard`).

Selecting a frequency candidate goes through `onSelectFoodItem`. For `catalogue` items, reuse
`RefreshFavouriteFoodUseCase` the way `onSelectFavouriteFood` does (`:482`), so a stale snapshot is
refreshed before the quantity screen opens. Its `.catalogue`-only gate and fallback to the stored
copy are exactly what is needed. Writing a log refreshes the snapshot anyway, so staleness is
bounded by how often the food is logged.

## Alternatives considered

- **A dedicated REST API.** It does not remove the problem, only moves it: the server would still
  need an index that joins a name match with one user's counts, i.e. this same data stored
  somewhere. It also adds hosting, auth and deployment, and the project has no server code today
  (`backend/` holds rules, indexes and hosting only).
- **One document per food (`users/{userId}/foodFrequency/{foodId}`).** It is the natural Firestore
  shape, but reads are billed per document. Loading the top 50 on every sheet opening is about 500
  reads per user per day, which exhausts the free daily read quota at roughly 100 daily active
  users from this feature alone. The single document costs one read regardless of size.
- **Counting from `foodConsumed` on the client.** It would need the whole history on every sheet
  opening, which costs even more reads.
- **Only counts in the map, rows resolved by id** (`FetchFoodItemsByIdsUseCase`). It keeps the
  document small, but it costs extra reads per keystroke for catalogue items, and `external` items
  cannot be resolved from Firestore at all, which would need an OpenFoodFacts call per candidate.
  The snapshot follows the favourites precedent and resolves all kinds with zero extra reads.
- **`setData(merge: true)` for every write.** It is simpler, but nested maps deep-merge, so an
  optional removed from a catalogue item would never disappear from the snapshot (see *Writing*).
- **Backfill from `foodConsumed`.** It is possible with an admin script like
  `scripts/backfill-search-terms.js`, but `foodConsumed` stores values scaled to the logged weight
  without `portions`, so the snapshot would have to be rebuilt from the catalogue, and `external`
  items cannot be rebuilt. Revisit only if there is a meaningful number of real users with history
  when this ships.

## Cross-cutting concerns

- **Security rules:** none. `users/{userId}/{document=**}` (`backend/firestore.rules:41`) already
  covers the new document. There are no index changes, because it is a single-document read.
- **Account deletion:** `DeleteAccountUseCase.wipeFirestoreData` enumerates the collections it
  deletes (iOS `DeleteAccountUseCase.swift:69-100`, the Android counterpart). **Add
  `deleteAsync(id: "foodFrequency", from: stats(userId))` on both platforms**, otherwise private
  data outlives the account. Deleting a missing document is not an error in Firestore. Extend both
  `DeleteAccountUseCaseTests` to assert the delete.
- **Anonymous → signed-in merge:** `MigrateAnonymousDataUseCase` is not extended. Its safety
  argument (ARCHITECTURE § 1.5) is that a resumed migration re-writes the same UUID-keyed documents
  idempotently. Merging counts with `increment` is not idempotent, so a resume would double them.
  The anonymous user's ranking is lost and rebuilds as they log. The source user's other data is
  not deleted by the migration either, so nothing new is orphaned.
- **Data export** ([design 0014](0014-data-export.md)): not included. The data is derived from
  `foodConsumed`, which is exported.
- **Offline:** not designed for; the app is online-only.
- **Privacy:** the data is private to the user, of the same nature as `foodConsumed`. Nothing new
  to declare.

## Risks

- **Not-found fallback race** → two devices log the first entry at the same instant and one
  `setData(merge:)` lands on top of the other's → one count is 1 instead of 2. This is negligible
  and self-heals.
- **A swallowed write failure hides a real bug** (e.g. a wrong `FieldPath`) → ranking silently
  never fills. Mitigation: the use-case test asserts the exact field paths, and the first manual
  check below inspects the document in the Firebase console.
- **The default protocol implementation masks a missing override in the real provider** →
  mitigated by the default throwing rather than no-op'ing, see *Writing*.

### Manual verification (no automated test covers these)

1. Log a food on a fresh account, then confirm in the Firebase console that
   `users/{uid}/stats/foodFrequency` exists with `entries.<id>.count == 1` and a full `item` map.
   Log it again and confirm `count == 2`.
2. Log a barcode item (digit-leading key) and a no-barcode catalogue item (UUID key). Both entries
   are written, which proves the `FieldPath` keys work.
3. Type a one-word query whose catalogue results exceed 10, where a logged food sorts alphabetically
   after the tenth result. It is listed, and above the uncounted results.
4. Delete the account and confirm the document is gone.
5. Do steps 1–3 on both iOS and Android against the same account. Both rank identically.

### Sequencing

The data is client-only, so there is no rules deploy and no ordering constraint against an app
release. iOS and Android can ship independently. A client without the feature ignores the
document, and a client with it reads counts written by the other. The `TextKit` change lands first
in its own commit, because both clients consume it.

## Outcome

Shipped as designed on both clients, with no change to the data model, the write path or the
ranking rules. Choices the design left open:

- `last_logged_at` is the moment the entry was logged (`now`), not the day it was logged for.
  Logging a past day does not make a food look less recently used.
- Favourites are sorted by `count` only. The `last_logged_at` tie-break applies to the non-favourite
  list, as *Ranking* specifies.
- The manual verification steps (Firebase console inspection, `FieldPath` keys for digit-leading and
  UUID ids, ranking parity across platforms) are not covered by any automated test.
