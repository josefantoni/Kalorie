# Design: Community product photos and the notification bell

- **Status:** Draft
- **Scope:** Backend, Cross-platform, iOS, Android
- **Date:** 2026-11-07

## Context and scope

[Design 0022](0022-food-photos.md) gave catalogue items a photo and listed as a non-goal *"A photo
for an existing item supplied by an ordinary user"*: only a new submission or the maintainer in the
catalogue editor could add one. In practice the maintainer cannot photograph the whole catalogue
alone, so most `foodItems` still have no `photo_url`. **This document reverses that non-goal.** The
rest of 0022 (capture pipeline, `catalogPhotos` as the only source of `photo_url`, copy on approval)
stays in force.

Letting users send photos adds a third maintainer queue next to pending submissions
([design 0009](0009-catalogue-moderation.md)) and reports ([design 0012](0012-report-incorrect-catalogue-data.md)).
Today the maintainer only finds out by opening Settings. The second part of this document adds a
bell to the Dashboard that shows a dot when anything waits and lists the waiting items.

Backend rules are shared, so the backend part is cross-platform. Only the iOS client is built now.

## Goals

- On `FoodQuantity` and `FoodConsumedDetail`, a `.catalogue` item without `photo_url` shows an
  empty photo pill in the toolbar, where the photo pill sits when a photo exists. Tapping it runs the
  0022 capture/crop pipeline and sends the photo as a **photo suggestion**.
- After sending, the pill shows *awaiting approval* for that user and item.
- Every user may send, anonymous users included — the same policy as submissions (0009) and reports
  (0012), [ADR 0001](../adr/0001-anonymous-firebase-auth-as-device-identity.md).
- One suggestion per user per item; nothing reaches other users before the maintainer approves it.
- The maintainer approves (the photo becomes the item's catalogue photo) or rejects a suggestion.
- A bell in the Dashboard navigation bar, with a dot when something waits; tapping it opens a list of
  the waiting items, and tapping an item pushes its detail.

## Non-goals

- `.external` (OpenFoodFacts) items and created meals — the pill appears only for `.catalogue`.
- Replacing an existing catalogue photo by a user. Only items without `photo_url`.
- A count on the bell. It shows a dot only.
- Push notifications. The app has none; this design does not add them.
- Removing the *Moderation* section from Settings. It stays; the bell is an additional entry point,
  because the bell is meant to serve non-maintainers later too.
- A *withdraw* action in the UI. The author's delete permission exists for retry and account deletion.
- Time-based rate limiting (rules cannot count per minute). See *Abuse*.
- Offline behaviour. The app is online-only.
- Android implementation in this iteration.

## Design

### Data

**Storage** — a new folder next to the two from 0022 (`Constants.Storage.photoSuggestionsFolder`):

```
photoSuggestions/{uid}/{itemId}.jpg     a user's suggested photo for an existing catalogue item
```

The file name is the item **id** (a barcode, or the uppercase UUID of an item without one,
[design 0013](0013-catalogue-item-without-barcode.md)) — not "barcode". The path is fixed, so one
user has at most one file per item.

**Firestore** — new top-level collection `photoSuggestions`, document id `{itemId}_{uid}` (the
`foodItemReports` pattern):

| Field | Type | Notes |
|---|---|---|
| `item_id` | string | `validItemId()` |
| `suggested_by` | string | `== request.auth.uid` |
| `photo_url` | string | download URL of `photoSuggestions/<own uid>/<item_id>.jpg`, ≤ 1024 chars |
| `suggested_at` | number, seconds | |

A suggestion is either pending (the document exists) or gone. Approve and reject both delete it, so
there is no `status` field and the pending count is the size of the collection.

### Rules

`backend/firestore.rules`, `match /photoSuggestions/{suggestionId}`:

- `read`: `isMaintainer()` ‖ (`resource == null` && `suggestionId.matches('.*_' + request.auth.uid)`)
  ‖ (`resource != null` && `resource.data.suggested_by == request.auth.uid`). Copy the
  `foodItemReports` read rule and its comment; the null branch lets the client's "already
  suggested?" read return not-found instead of being denied.
- `create`: `request.auth != null` && id == `item_id + '_' + uid` && `suggested_by == uid` &&
  `validItemId(item_id)` && `suggested_at is number` && `photo_url` is a string ≤ 1024 matching
  `photoUrlPrefix() + 'photoSuggestions%2F' + uid + '%2F' + item_id + '\\.jpg.*'` &&
  `exists(foodItems/item_id)` && `!('photo_url' in get(foodItems/item_id).data)`.
- `update`: denied.
- `delete`: `isMaintainer()` ‖ `resource.data.suggested_by == request.auth.uid`.

`backend/storage.rules`, `match /photoSuggestions/{uid}/{fileName}`:

- `read`: owner ‖ `isMaintainer()`.
- `create`: owner && `validJpeg()` && `fileName` matches `<validItemId shape>\.jpg` &&
  `firestore.exists(/databases/(default)/documents/foodItems/<id>)` && the item has no `photo_url`
  (`firestore.get(...)`). `<id>` is `fileName` without `.jpg`.
- `update`: denied (Storage treats overwriting an existing object as `update`).
- `delete`: owner ‖ `isMaintainer()`. The maintainer needs it to clear other users' suggestions on
  approval.

**Owner task before deploy:** cross-service rules (`firestore.get()` in Storage rules) need the
Storage service agent to be granted Firestore read access. The Firebase console offers it on the
first deploy of such rules. Each such check is a billed Firestore read on upload only.

`firestore-rules-tests` (`firestore.rules.test.js`, `storage.rules.test.js`) gets cases for:
create own suggestion allowed (anonymous too); foreign `suggested_by` / mismatched id / foreign
`photo_url` denied; item missing or already having `photo_url` denied; update denied; author and
maintainer may delete, others not; not-found read of own id allowed, of another's id denied; Storage
create into another uid's folder, non-JPEG, ≥ 1 MB, bad file name, item with photo — all denied;
overwrite of an existing own file denied; maintainer delete allowed.

### Sending a suggestion (iOS)

`SubmitPhotoSuggestionUseCase(itemId:, photo: Data)`:

1. Best-effort delete of the own object at `photoSuggestions/{uid}/{itemId}.jpg`, ignoring
   not-found. This is the retry path: a previous attempt may have uploaded the file and then failed
   on the Firestore write, and the fixed path cannot be overwritten. `StorageDataProvider` gains a
   path-based delete for this.
2. `UploadFoodPhotoUseCase` to that path → download URL.
3. Create `photoSuggestions/{itemId}_{uid}`. If this fails, delete the uploaded file (best effort)
   and rethrow — the same order and cleanup as `FoodItemSubmissionWriter` (0022).

`FetchMyPhotoSuggestionUseCase(itemId:) -> Bool` reads `photoSuggestions/{itemId}_{uid}` (not-found
→ `false`).

The image goes through `FoodPhotoProcessing` (square crop, 1080 px, JPEG 0.7, EXIF dropped), the
same picker/camera flow as the new-food form.

### The photo pill (iOS)

The decision is made **after** the catalogue item has been fetched, never from a snapshot. Favourite
and frequency snapshots carry `photo_url` and stay stale after an approval (0022), so deciding from
the snapshot would ask the user to photograph an item that already has a photo.

`FoodQuantityViewModel` already fetches the catalogue item when `item.photoURL == nil`;
`FoodConsumedDetailViewModel.loadCatalogueItem()` always fetches it. Both expose a new state:

```swift
enum FoodPhotoPillState { case hidden, photo(URL), empty, awaitingApproval }
```

- Not `.catalogue` → `hidden`.
- Until the fetch finishes → `hidden` (no flash of the empty pill).
- Fetched item has `photoURL` → `photo(url)` — today's `FoodPhotoThumbnail`.
- No photo → `FetchMyPhotoSuggestionUseCase`; `true` → `awaitingApproval`, `false` → `empty`.
  A failed read → `hidden`.

`foodPhotoThumbnail(url:)` becomes `foodPhotoPill(state:onSend:)`, using the same principal toolbar
placement and width (`min(width − 2 × 132, 320)`, 44 pt high), so the toolbar does not shift between
states.

**Look** (chosen by the owner; mockups https://claude.ai/artifact/SWnNu4RLrjhsjCoxxLtM6b): the
empty-photo language of `FoodPhotoPicker` carried over to the toolbar.

- `empty`: capsule with a `Color.secondary` dashed border (`lineWidth: 2, dash: [6]`) and, centred,
  the picker's accent badge — a small `Color.accentColor` circle with a white `camera.fill`. No
  `fork.knife` placeholder; at 44 pt there is no room for both. Tap → photo source choice → crop →
  `SubmitPhotoSuggestionUseCase` → `awaitingApproval`. Failure → alert, state stays `empty`.
- `awaitingApproval`: the same dashed capsule with a `Color.secondary` `clock` instead of the badge.
  Tap → a popover saying the photo is awaiting approval.

Rejected looks: a filled accent capsule (on most items it would outweigh the primary *Add* button); a
bare `+` (0022 rejected it on this screen because it reads as *add the food*).

### Review (iOS)

`ModerationPhotoSuggestionView` (+ ViewModel, in `Features/Moderation`): the photo (via
`DownloadFoodPhotoUseCase`), the item name, *Approve* and *Reject*.

- **Approve** (`ApprovePhotoSuggestionUseCase`):
  1. Fetch the item. If it already has `photo_url`, approval is impossible: show an alert and leave
     only *Reject*.
  2. Download the suggestion → upload to `catalogPhotos/{itemId}/{uuid}.jpg` → `UpdateFoodItemUseCase`
     with the new `photo_url` (the copy from 0022's `ApproveSubmissionUseCase`; on a failed Firestore
     write delete the just-uploaded catalogue file).
  3. Delete **all** suggestions for this item: query `photoSuggestions where item_id == itemId`
     (single-field index, automatic), and for each delete the Storage file and the document, best
     effort. Other users' suggestions for the item would otherwise wait forever.
- **Reject** (`RejectPhotoSuggestionUseCase`): delete the file, then the document. Only a file that is
  already gone (`objectNotFound`) is ignored; any other Storage error aborts before the document is
  touched, so the row stays and the action can be retried. Approve's cleanup of other users' suggestions
  logs that failure and moves on.

On success the view calls back so the list that pushed it drops the row (the
`onSubmissionResolved` pattern in `ModerationQueueView`).

The Settings *Moderation* section gets a third row, *Photo suggestions*, listing pending suggestions
(oldest `suggested_at` first) and pushing the same view.

### The notification bell (iOS)

**Placement.** A `bell` button in `DashboardView`'s `.topBarTrailing`, to the **left** of the
gearshape. It is shown only when the user has a notification source; today the only source is the
maintainer claim. Name it generically (`NotificationBell`, `NotificationsView`) because non-maintainer
sources may come later.

**Maintainer claim.** `MaintainerClaimCache` is created today in `SettingsConfigurator`. Move its
ownership up so the Dashboard and Settings share one instance, and inject
`FetchMaintainerClaimUseCase` into the Dashboard.

**Dot.** `FetchHasPendingNotificationsUseCase -> Bool` runs Firestore `count()` aggregations on
`foodItemSubmissions where status == "pending"`, `foodItemReports` and `photoSuggestions`, and
returns whether any is non-zero. About one billed read per query, maintainer only. No rules change
for these reads. The bell's visibility (the maintainer claim, served from the shared cache) and the dot are
recomputed together when the Dashboard appears and when the app returns to the foreground, so one failed
lookup does not hide the bell for the rest of the session; the dot alone is recomputed when the bell sheet
is dismissed. A sign-in or sign-out rebuilds the Dashboard (`.id(authState.userId)`), so it starts afresh.
The dot is a small red circle on the bell's top-right. Any error → no dot, no alert. Errors here are network loss, an App Check token failure, a stale maintainer claim
in the cached ID token, or rules not yet deployed.

**Sheet.** Tapping the bell presents `NotificationsView` in its own `NavigationStack`. It is one
list of concrete waiting items, oldest first (a report row dates from its oldest report), each row
with an icon per kind, the item name and a kind label:

| Row | Pushes |
|---|---|
| pending submission | `ModerationReviewView` (as in the queue) |
| reports for one item (grouped as in `ModerationReportsViewModel`) | `ModerationCatalogueEditorView` with those reports |
| photo suggestion | `ModerationPhotoSuggestionView` |

Data comes from the existing `FetchPendingSubmissionsUseCase`, `FetchFoodItemReportsUseCase` and
the new `FetchPhotoSuggestionsUseCase`, which loads the oldest `reportsPageLimit` (50)
suggestions by `suggested_at` ascending for both the sheet and the Settings row. Item names for reports and suggestions are resolved with
`FetchFoodItemByBarcodeUseCase`, as in `ModerationReportsViewModel`. A row disappears when its detail
resolves it. Empty list → the same empty-state text style as the queue.

### Account deletion

`DeleteAccountUseCase` lists `photoSuggestions/{uid}/` (as it does `submissionPhotos`) and deletes every
file, then queries `photoSuggestions where suggested_by == uid` (as it does for reports) and batch-deletes
the documents. Files and documents are found independently, so a document whose upload was lost is still
removed and a file without a document cannot make the wipe fail: deleting a missing document is denied
by the `delete` rule, because `resource` is null. The author's `read` rule already allows the query. If
either lookup fails, abort so the deletion can be retried.

### Merge on sign-in

Suggestions are **not** migrated across an anonymous → signed-in merge, the same as reports
(0012). The anonymous account's suggestion stays pending under its old uid. It is still reviewed, and
the new uid can send its own.

### Copy (CS, EN, SK — `localisation/`)

Strings for: the empty pill's accessibility label (*Add photo*), the awaiting popover (*Your photo is
awaiting approval*), the send failure alert, the review buttons and alerts, the Settings row
*Photo suggestions*, the bell's accessibility label, the sheet title, the kind labels and the empty
state.

### Tests (iOS)

- `SubmitPhotoSuggestionUseCaseTests`: deletes the stale own file before upload; deletes the
  uploaded file when the Firestore write fails; writes `photo_url` pointing at `photoSuggestions`.
- `ApprovePhotoSuggestionUseCaseTests`: catalogue `photo_url` points at `catalogPhotos`, **never**
  at `photoSuggestions` (the suggestion folder is private); all suggestions for the item are deleted;
  an item that already has a photo is not overwritten.
- Pill state: no `empty` before the fetch completes; a snapshot without `photoURL` whose catalogue
  item has one ends in `photo`, not `empty`; an existing own suggestion ends in `awaitingApproval`.
- `FetchHasPendingNotificationsUseCaseTests`: any non-zero → `true`; a thrown error → `false`.
- `DeleteAccountUseCaseTests`: suggestion files and documents deleted independently (the document by
  `suggested_by`); listing failure aborts.

### Order of work

1. Rules + rules tests (`firestore-rules-tests`), then `firebase deploy --only firestore:rules,storage`
   (owner, after granting the cross-service permission). Rules first, so no client writes into an
   unguarded path.
2. iOS data layer: constants, DTO, use cases, `StorageDataProvider` path delete, account deletion.
3. iOS pill on both screens.
4. iOS review view and the Settings row.
5. iOS bell + sheet, shared maintainer claim cache.
6. Localisation, `ARCHITECTURE.md` (§ 1.2 Storage layout, § 1.6 rules, § 1.7 wire schema, § 7
   moderation, the Settings/Dashboard sections).

## Alternatives considered

- **Signed-in users only.** Rejected. Anonymous users can already upload to `submissionPhotos` with
  arbitrary file names and no limit, so the restriction would not close the larger existing path. It
  would also need a pending intent held above the Dashboard's `.id(authState.userId)` across sign-in
  and the merge overlay.
- **Firestore document first, Storage gated on its existence.** Rejected in favour of the 0022 order
  (file first, document second, cleanup on failure). The retry is solved by the author deleting the
  own file at the fixed path.
- **Allowing `update` (overwrite) of the own Storage file.** Rejected. It would let a pending
  suggestion's file be swapped after the maintainer has seen it.
- **Bell as a router to the three queues with counts.** Rejected by the owner. The bell lists
  concrete items and pushes their detail; the badge is a dot, not a number.
- **Moving the Moderation section from Settings into the bell.** Rejected by the owner. The bell will
  serve non-maintainers later, and moving it again would follow.
- **A snapshot listener for the dot.** Rejected. A count on appear, foreground and sheet dismissal is
  enough and costs nothing between them.

## Cross-cutting concerns

- **Privacy (owner task).** User-uploaded photos of existing items are a new upload path. Update
  `backend/hosting/privacy.html` (every language) and re-confirm App Privacy and, before Android
  ships, Data safety.
- **App Store 1.2 (user-generated content).** Suggestions are visible only to their author and the
  maintainer until approved. Pre-moderation is the filter.
- **Old clients** never write `photoSuggestions`. The new rules do not affect them.
- **Cost.** One "already suggested?" read per opening of a photo-less catalogue item. One
  cross-service read per upload. About three aggregation reads per dot refresh, maintainer only.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Abuse: one account suggests a photo for every photo-less item | Up to items × 1 MB of storage per account; review load | Fixed path bounds it per account. Fallback if it happens: a `blockedUploaders/{uid}` document checked by both rule sets, plus a "delete all from this user" action in review |
| Cross-service permission not granted | Every Storage create denied | Owner grants it before deploy (*Rules*) |
| Pending suggestion's item gets a photo through the catalogue editor | Suggestion can no longer be approved | Approve detects it and offers only *Reject* |

## Android

The Android client implements the same pill, review, bell and account deletion against the same
rules, per [ADR 0038](../adr/0038-android-client-mirrors-the-ios-architecture-natively.md). The bell is a
`BadgedBox` in the Dashboard top bar and the notification list is a bottom sheet; the Settings → *Photo
suggestions* row and the review screens are pushed with Navigation 3.

## Outcome

*Filled in once after shipping.*
