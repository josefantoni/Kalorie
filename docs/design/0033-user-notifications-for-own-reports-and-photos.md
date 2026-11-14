# Design: User notifications for own reports and photo suggestions

- **Status:** Draft
- **Scope:** Backend, Cross-platform, iOS, Android
- **Date:** 2026-11-14

## Context and scope

[Design 0031](0031-community-product-photos-and-notification-bell.md) added a bell to the Dashboard. It is
shown to the maintainer only and lists what waits for review. It was named generically *"because the bell
is meant to serve non-maintainers later too"*. This document is that later step.

Today a user who reports wrong data ([design 0012](0012-report-incorrect-catalogue-data.md)) or sends a
photo for an item (0031) gets no trace of it outside the food's own screen, never learns what happened to
it, and cannot send a second report for the same item even after the first one was dealt with.

This document turns every report and photo suggestion into a **ticket** with a state. The user sees each
of their tickets as a notification behind the bell. The maintainer resolves a ticket with a written
response instead of deleting it, and the user's notification shows the new state and the response.

These are in-app notifications read from Firestore. They are not push notifications.

**Relation to earlier records.** This document reverses three earlier decisions on purpose:

- Design 0012 *No status field* and its alternative *A `resolved` status instead of deleting* (rejected
  there for having no reader). The reader is now the author of the report. A resolved report is kept with
  its state and the maintainer's response.
- Design 0012 non-goal *"Notifying the author of the outcome"*. The outcome is now the main content of
  the notification.
- The fixed document ids `{barcode}_{uid}` (0012) and `{itemId}_{uid}` (0031), which allowed one report
  or suggestion per user and item ever. A kept ticket would block every later one, so each ticket gets
  its own id. The "one at a time" rule moves to the client (see *One pending ticket per user and item*).

Design 0031's photo approval deleted every other user's suggestion for the same item, because the item
had a photo afterwards and approving another one failed. That is reversed too: other suggestions stay
pending, and approving one replaces the current catalogue photo.

Design 0031 denied `update` on `photoSuggestions` so a pending photo could not be swapped after the
maintainer saw it. The `update` rules here never let the author touch `photo_url`, so that reason holds.

Designs 0012 and 0031 left a *withdraw* action out of the client. This document adds it, for pending
tickets only.

## Goals

- Every user, anonymous included, sees the bell in the Dashboard toolbar.
- A non-maintainer's bell sheet lists their own tickets, reports and photo suggestions, newest event
  first. A notification appears right after the user sends the report or photo.
- A ticket is in one of three states: **pending**, **accepted** (a report: *resolved*; a photo:
  *approved*) or **rejected**. Accepted and rejected are final.
- The maintainer resolves each ticket separately, never several at once. Every resolution carries a
  **response** to the author; it is mandatory and never empty.
  - *Save and resolve* (report) and *Approve* (photo) pre-fill a thank-you response in the **author's**
    app language. The maintainer may edit it but not send it empty.
  - *Reject* starts with an empty response the maintainer must write.
- Tapping a notification pushes its detail: the state, the date sent, what the user sent (the report's
  note, or a preview of the photo while it still exists) and, once resolved, the maintainer's response.
- While a ticket is **pending**, the detail has a *Withdraw* action (with a confirmation) that deletes
  the ticket completely — document, and for a photo the file. Once resolved, *Withdraw* is gone.
- The user can **hide** a notification from their list at any time. Hiding is local to the user's list
  and changes nothing for the maintainer:
  - a ticket hidden while pending shows up again, unread, when the maintainer resolves it;
  - a ticket hidden after it was resolved stays hidden for good, because a resolved ticket has no
    further event.
- A notification is **unread** from every event (sent, resolved) until its detail has been shown after
  that event. The read state is stored in Firestore, so it follows the account across devices and
  reinstalls. Opening the detail of a read notification writes nothing.
- The bell shows the red dot while at least one visible notification is unread. Opening the list alone
  does not clear it. Each unread row has its own dot.
- A user has at most one **pending** ticket per item and kind. After it is resolved they can send
  another one for the same item.
- A ticket is never deleted by the app except by the author's withdrawal. Deleting the account
  anonymises the author's tickets and keeps them, including a pending photo, which the maintainer can
  still approve.
- The maintainer's moderation sheet lists **pending** tickets only, one row per ticket.

## Non-goals

- **Notifications for pending new-food submissions** (`foodItemSubmissions`). The user already has
  *My submissions* with a status (design 0009).
- **User notifications for a maintainer.** A maintainer's own report already appears in the moderation
  list. The two sheets are separate; a maintainer sees only the moderation one.
- Push notifications, snapshot listeners, a count on the bell.
- **A message from the user with a photo suggestion.** Only reports carry a note.
- **A conversation.** The user cannot reply to the response, and the maintainer cannot change a
  resolved ticket. A user who disagrees sends a new ticket.
- **Suggesting a photo for an item that already has one.** Stays denied (0031). Only suggestions that
  were pending before the item got its photo can replace it.
- Editing the report or replacing the photo from the detail. The user withdraws and sends again.
- Resolving a report with plain *Save*. The catalogue editor opened from a ticket has only *Save and
  resolve* and *Reject*.
- Migrating tickets across the anonymous → signed-in merge. Tickets stay under the old uid (0012, 0031).
- Offline behaviour. The app is online-only.

## Design

### Data

No new collection. A ticket **is** the report or suggestion document, now kept after resolution:

| Ticket | Collection | Owner field | Created field |
|---|---|---|---|
| report | `foodItemReports/{autoId}` | `reported_by` | `reported_at` |
| photo suggestion | `photoSuggestions/{autoId}` | `suggested_by` | `suggested_at` |

**Document id.** A Firestore auto id, generated on the client before the write (a photo needs it for
the Storage path first). Neither the item nor the uid is in the id any more, which is also what lets an
anonymised ticket lose its link to the account.

New fields on both collections:

| Field | Type | Written by | Notes |
|---|---|---|---|
| `status` | string: `pending` / `accepted` / `rejected` | author on create (`pending`), maintainer on resolve | |
| `locale` | string: `cs` / `en` / `sk` | author on create | The app language the author used when sending. Selects the language of the pre-filled response. |
| `response` | string, 1–1000 | maintainer on resolve | Mandatory with `accepted` / `rejected`. |
| `resolved_at` | number, seconds ([ADR 0008](../adr/0008-dates-as-epoch-seconds-not-firestore-timestamp.md)) | maintainer on resolve | |
| `seen_at` | number, seconds | author | Last time the detail was shown. |
| `hidden` | bool | author on create (`false`) and on hide (`true`), maintainer on resolve (`false`) | Hidden from the user's list. The list query filters on it, so hidden tickets are never downloaded. |
| `author_deleted` | bool, `true` | author on account deletion | Set together with removing the owner field. |

**Derived state (both clients).** `lastEventAt = resolved_at ?? created`.

- unread: `seen_at == nil || seen_at < lastEventAt`

Resolution moves `lastEventAt` past an earlier `seen_at`, so the notification is unread again.

**Hidden** is a stored flag, not derived from dates, because Firestore cannot filter by comparing two
fields of a document; a derived visibility would make the user's query download and bill every hidden
ticket only for the client to drop it. The maintainer's resolution writes `hidden: false`, so a ticket
hidden while pending comes back. A ticket hidden after resolution stays hidden for good, because
`status` is final and nothing writes `hidden` again.

**The pending photo's file** moves from `photoSuggestions/{uid}/{itemId}.jpg` to
`photoSuggestions/{uid}/{itemId}/{suggestionId}.jpg`. The item stays in the path so the Storage rule can
keep checking that the item has no photo yet.

### One pending ticket per user and item

The client enforces it: before offering *Report* / *Suggest a photo*, the food screen asks for the
user's pending ticket for that item (`FetchMyFoodItemReportUseCase`, `FetchMyPhotoSuggestionUseCase`):
`where reported_by == uid && barcode == itemId && status == 'pending'`, `limit 1` (and the same with
`suggested_by` / `item_id`). Pure equality filters need no composite index.

The rules **cannot** enforce it: they cannot check that no other pending document exists. A modified
client can create a duplicate; the maintainer sees it as one more ticket and rejects it. Accepted by the
owner over a fixed-id "lock" document moved on every resolution (see *Alternatives considered*).

### Rules (`backend/firestore.rules`)

`match /foodItemReports/{reportId}`:

```
allow read: if isMaintainer() ||
  (resource.data.get('reported_by', null) == request.auth.uid);

allow create: if request.auth != null &&
  request.resource.data.keys().hasOnly(['barcode', 'reported_by', 'reason', 'reported_at', 'status', 'locale', 'hidden']) &&
  request.resource.data.reported_by == request.auth.uid &&
  validItemId(request.resource.data.barcode) &&
  request.resource.data.reason is string &&
  request.resource.data.reason.size() > 0 &&
  request.resource.data.reason.size() <= 500 &&
  request.resource.data.reported_at is number &&
  request.resource.data.status == 'pending' &&
  request.resource.data.hidden == false &&
  request.resource.data.locale in ['cs', 'en', 'sk'];

// author: read marker (any number of times) and hiding
allow update: if request.auth != null &&
  resource.data.get('reported_by', null) == request.auth.uid &&
  request.resource.data.diff(resource.data).affectedKeys().hasOnly(['seen_at', 'hidden']) &&
  request.resource.data.get('seen_at', 0) is number &&
  (request.resource.data.hidden == true || request.resource.data.hidden == resource.data.hidden);

// author: anonymise on account deletion
allow update: if request.auth != null &&
  resource.data.get('reported_by', null) == request.auth.uid &&
  request.resource.data.diff(resource.data).affectedKeys().hasOnly(['reported_by', 'author_deleted']) &&
  !('reported_by' in request.resource.data) &&
  request.resource.data.author_deleted == true;

// maintainer: resolve a pending ticket once
allow update: if isMaintainer() &&
  resource.data.status == 'pending' &&
  request.resource.data.diff(resource.data).affectedKeys().hasOnly(['status', 'response', 'resolved_at', 'hidden']) &&
  request.resource.data.status in ['accepted', 'rejected'] &&
  request.resource.data.hidden == false &&
  request.resource.data.response is string &&
  request.resource.data.response.size() > 0 &&
  request.resource.data.response.size() <= 1000 &&
  request.resource.data.resolved_at is number;

allow delete: if isMaintainer() ||
  (resource.data.get('reported_by', null) == request.auth.uid && resource.data.status == 'pending');
```

`match /photoSuggestions/{suggestionId}`: the same with `suggested_by` / `suggested_at` / `item_id`, and:

- create: keys `['item_id', 'suggested_by', 'suggested_at', 'photo_url', 'status', 'locale', 'hidden']`;
  `photo_url` must match `photoUrlPrefix() + 'photoSuggestions%2F' + uid + '%2F' + item_id + '%2F' +
  suggestionId + '\\.jpg.*'`; the item must exist and have no `photo_url` (unchanged from 0031).
- No rule lets anyone change `photo_url` after create.

The `resource == null` read branch for the "already reported?" lookup by fixed id goes away with the
fixed id. Old fixed-id documents do not exist in production (only the maintainer's build has shipped).

`backend/storage.rules`, `match /photoSuggestions/{uid}/{itemId}/{fileName}`: read and delete as today;
create checks `validItemId(itemId)`, a file name that is a Firestore auto id + `.jpg`, that the item
exists and has no `photo_url`. The check that no suggestion document exists for `{itemId}_{uid}` is
removed — the client-side pending check replaces it.

`backend/firestore.indexes.json`:

| Collection | Fields | Used by |
|---|---|---|
| `foodItemReports` | `reported_by` ASC, `reported_at` DESC | exists — account-deletion lookup |
| `photoSuggestions` | `suggested_by` ASC, `suggested_at` DESC | account-deletion lookup |
| `foodItemReports` | `reported_by` ASC, `hidden` ASC, `reported_at` DESC | the user's list |
| `photoSuggestions` | `suggested_by` ASC, `hidden` ASC, `suggested_at` DESC | the user's list |
| `foodItemReports` | `status` ASC, `reported_at` DESC | the maintainer's queue |
| `photoSuggestions` | `status` ASC, `suggested_at` DESC | the maintainer's queue |

`firestore-rules-tests/firestore.rules.test.js`, for both collections:

- create: with `status: 'pending'`, `hidden: false` and a valid `locale` succeeds; with any other
  `status`, `hidden: true` or missing, a missing or unknown `locale`, or an extra key (`response`,
  `seen_at`) is denied;
- the author may write `seen_at` repeatedly and set `hidden: true`, but not set `hidden: false`, nor
  write `status`, `response`, `resolved_at`, `reason`, `photo_url`;
- the maintainer's resolution must set `hidden: false`, also on a ticket the author has hidden;
- the author may anonymise (remove owner, set `author_deleted`) and nothing else in the same write;
- the maintainer may resolve a pending ticket with a non-empty response; an empty response, a
  response over 1000, a second resolution, or touching any other field is denied;
- another user may not read or write the ticket;
- the author may delete a pending ticket, not a resolved one;
- photo create with a `photo_url` for another suggestion id or item is denied.

### Writing and reading (both clients)

Names are the iOS ones. Android ports them 1:1.

**Create.** `SubmitFoodItemReportUseCase` and `SubmitPhotoSuggestionUseCase` generate the document id,
write `status: 'pending'`, `hidden: false` and `locale` (the language the app is displayed in — `Bundle.main
.preferredLocalizations.first` on iOS, the resolved app locale on Android — mapped to `cs` / `en` /
`sk`). The photo is uploaded to the new path first, as today.

**`FirestoreDataProviderProtocol`** needs a document id before the write and a query with several
equality filters. Add both **with a default implementation in the protocol extension** that throws
`.unsupported`, as the string-range overload already does, so the hand-written test fakes keep
compiling:

- `func newDocumentId(in collection: String) -> String`
- `func loadAsync<T: Decodable>(from collection: String, whereEqualTo filters: [(String, String)], limit: Int) async throws -> [T]`

```swift
struct UserNotificationDomain: Identifiable, Equatable {
    enum Kind { case report, photoSuggestion }
    enum Status { case pending, accepted, rejected }
    let kind: Kind
    let id: String            // the document id
    let itemId: String
    let createdAt: Date
    var status: Status
    var response: String?
    var resolvedAt: Date?
    var seenAt: Date?
    var reason: String?       // the note written with a report, nil for a photo suggestion
    var photoURL: URL?        // the sent photo while it exists, nil for a report
    var lastEventAt: Date     // resolvedAt ?? createdAt
    var isUnread: Bool
}
```

`FetchUserNotificationsUseCase() async throws -> [UserNotificationDomain]`

- Two queries, run concurrently: own reports `where reported_by == uid && hidden == false` by
  `reported_at` desc, and the same for suggestions. This needs a query with two equality filters and an
  order, added to `FirestoreDataProviderProtocol` like the methods below.
- Maps both and returns them by `lastEventAt` desc. Either query failing throws.

`MarkUserNotificationSeenUseCase(_:)`: returns if `!isUnread`; otherwise
`updateAsync(NotificationSeenDTO(seenAt: now), id:, in:)`. No special `PERMISSION_DENIED` handling any
more — repeated writes are allowed.

`HideUserNotificationUseCase(_:)`: `updateAsync(NotificationHiddenDTO(hidden: true), id:, in:)`.

`WithdrawUserNotificationUseCase(_:)`: only for `status == .pending` (the caller never offers it
otherwise). A report: `deleteAsync`. A photo: `PhotoSuggestionRemover` deletes the file (missing is
fine), then the document. `PERMISSION_DENIED` means the maintainer resolved it meanwhile: the use case
throws a dedicated `alreadyResolved` error, and the list reloads so the user sees the outcome.

**Account deletion.** `DeleteAccountUseCase` no longer deletes the user's reports and suggestions, nor
the suggestion files. For each own ticket it writes the anonymising update (`reported_by` /
`suggested_by` removed, `author_deleted: true`) with `FieldValue.delete()`. Afterwards no query by uid
finds the ticket; the maintainer still sees it in the queue and can resolve it, and a pending photo
can still be approved.

### The maintainer's side

**Queue.** `FetchFoodItemReportsUseCase` and `FetchPhotoSuggestionsUseCase` load `where status ==
'pending'` ordered by the created field, newest first. `FetchHasPendingNotificationsUseCase` counts with
the existing `countAsync(from:where:isEqualTo:)` on `status == 'pending'`. Reports are no longer grouped
by barcode: one row per ticket, and two reports for the same item are two rows.

**Report.** A row pushes `ModerationCatalogueEditorView` with that one ticket. Opened from a ticket it
shows the ticket's note and **two** actions; plain *Save* is shown only when the editor is opened
without a ticket (the moderation queue's toolbar button), as today.

- *Save and resolve*: validates and saves the item first. On success opens a response sheet pre-filled
  with `moderation_response_report_accepted` **in the ticket's `locale`**. *Send* (disabled while the
  trimmed text is empty) writes `status: 'accepted'`, `response`, `resolved_at` and `hidden: false`. If the ticket write
  fails, the item stays saved and the ticket pending; the maintainer retries *Send*.
- *Reject*: opens the same sheet empty. *Send* writes `status: 'rejected'`, `response`, `resolved_at`, `hidden: false`.

`DeleteFoodItemReportUseCase` is replaced by `ResolveFoodItemReportUseCase(ticketId:status:response:)`.

**Photo.** `ModerationPhotoSuggestionView`:

- *Approve*: response sheet pre-filled with `moderation_response_photo_accepted` in the ticket's
  `locale`. *Send*: copies the photo into the catalogue (replacing the item's current photo if it has
  one — the `itemAlreadyHasPhoto` guard goes), writes the resolution, then deletes the suggestion file.
  The other users' pending suggestions for the item are **left alone**.
- *Reject*: empty response sheet. *Send*: writes the resolution, then deletes the file.

A failed file delete after a successful resolution is logged and leaves an orphaned file (see *Risks*).
The pre-filled text comes from the app's own localisation in the requested language (iOS: the
`<locale>.lproj` bundle; Android: `createConfigurationContext` with that locale), not from the
maintainer's app language.

### The bell

`NotificationBellViewModel`:

- The bell is always shown. `isBellVisible` is replaced by `@Published private(set) var isMaintainer`,
  set from `FetchMaintainerClaimUseCase` in `refresh()`; a failed claim lookup means `false`.
- `refreshDot()`: a maintainer as today. Otherwise the dot is on when `FetchUserNotificationsUseCase`
  returns any unread notification. An error means no dot and no alert.
- **Gate.** A device-local `UserNotificationsStore` (keyed by uid, the `SignInSpotlightStore` pattern)
  decides whether a non-maintainer's refresh queries at all, as before: `mayHaveUnread`, `lastCheckedAt`,
  `lastSubmittedAt`. The submit use cases set `mayHaveUnread = true`; a refresh queries when
  `mayHaveUnread` is true or the last check is older than 24 hours; each successful fetch stores
  `mayHaveUnread = result.contains { $0.isUnread }` and `lastCheckedAt = now`. See *Decisions* —
  the maintainer's resolution is invisible to the gate.
- Pull to refresh on the Dashboard refreshes the dot with the gate ignored
  (`NotificationBellViewModel.onRefresh()`).
- The dot is refreshed on appear, on foreground, on dismissal of the bell sheet, of Settings and of the
  add-food sheet, and when the Dashboard reappears after a pushed screen is popped.

### The list and the detail

`NotificationsView` / `NotificationsViewModel` serve both roles, built with `isMaintainer`, as before.

- Rows by `lastEventAt`, newest first. Each row: kind icon (`pencil` / `camera.fill`), item name, a
  state label (*Pending* / *Resolved* or *Approved* / *Rejected*), and a red dot (`Color.error`, 9 pt)
  while unread.
- **Hide**: swipe to delete on iOS, the same gesture the moderation list uses; on Android the
  equivalent swipe. No confirmation. The row disappears at once; `HideUserNotificationUseCase` runs; on
  failure the row comes back and the `L10n.Common.errorUnknown` alert shows.
- Item names via `FetchFoodItemByBarcodeUseCase(barcodes:)`, falling back to the id.

`UserNotificationDetailView`: item name as title, the state, the photo (while `photoURL` points to an
existing file, i.e. pending; a resolved photo shows its state instead, see *Decisions*), the message for kind and state, *Sent* date, *Your note* (a report),
and once resolved the *Response* section with the maintainer's text and the resolution date. A
destructive *Withdraw* button only while pending. On appear it calls `viewModel.onDetailShown(id:)`,
which marks the notification seen if unread and updates the row in memory; a failure is logged and the
row stays unread.

### Copy (`localisation/strings.json`, CS / EN / SK)

| Key | CS | EN |
|---|---|---|
| `notifications_user_kind_report` | Nahlášení potraviny | Food report |
| `notifications_user_kind_photo` | Fotka potraviny | Food photo |
| `notifications_user_status_pending` | Čeká na vyřízení | Pending |
| `notifications_user_status_resolved` | Vyřešeno | Resolved |
| `notifications_user_status_approved` | Schváleno | Approved |
| `notifications_user_status_rejected` | Zamítnuto | Rejected |
| `notifications_user_report_message` | Tvému nahlášení se budu co nejdříve věnovat. Až ho vyřídím, dám ti tady vědět. | I will look at your report as soon as I can. I will let you know here once I have dealt with it. |
| `notifications_user_photo_message` | Na tvoji fotku se co nejdříve podívám. Až ji vyřídím, dám ti tady vědět. | I will look at your photo as soon as I can. I will let you know here once I have dealt with it. |
| `notifications_user_response_title` | Odpověď | Response |
| `notifications_user_sent_at` | Odesláno %@ | Sent %@ |
| `notifications_user_resolved_at` | Vyřízeno %@ | Dealt with %@ |
| `notifications_user_unread_accessibility` | Nepřečteno | Unread |
| `notifications_user_reason_title` | Tvoje poznámka | Your note |
| `notifications_user_hide` | Smazat | Delete |
| `notifications_user_withdraw_button` | Zrušit odeslání | Withdraw |
| `notifications_user_withdraw_confirm_title` | Zrušit odeslání? | Withdraw this submission? |
| `moderation_response_title` | Odpověď uživateli | Reply to the user |
| `moderation_response_send` | Odeslat | Send |
| `moderation_response_report_accepted` | Díky za nahlášení! Údaje u potraviny jsem opravil. | Thanks for the report! I have corrected the food's details. |
| `moderation_response_photo_accepted` | Díky za fotku! Přidal jsem ji k potravině. | Thanks for the photo! I have added it to the food. |

Slovak follows the Czech text. The owner confirms the final wording.

### Tests

iOS, with Android ports under the same names:

- `SubmitFoodItemReportUseCaseTests`, `SubmitPhotoSuggestionUseCaseTests`: writes a generated id,
  `status: 'pending'` and `locale`; photo path contains item and suggestion id; a successful submit sets
  `mayHaveUnread`, a failed one does not.
- `FetchMyFoodItemReportUseCaseTests`, `FetchMyPhotoSuggestionUseCaseTests`: query only the user's
  **pending** ticket for the item — a resolved ticket for the same item does not block a new one.
- `FetchUserNotificationsUseCaseTests`: queries with `hidden == false`; maps state, response, dates;
  a ticket resolved after the user had seen it is unread; ordering by `lastEventAt`.
- `MarkUserNotificationSeenUseCaseTests`: unread → one write; read → none; seen before resolution →
  unread again after it, and marking writes again.
- `HideUserNotificationUseCaseTests`, `WithdrawUserNotificationUseCaseTests` (pending only;
  `alreadyResolved` on `PERMISSION_DENIED`).
- `ResolveFoodItemReportUseCaseTests`, `ApprovePhotoSuggestionUseCaseTests`,
  `RejectPhotoSuggestionUseCaseTests`: write status, response, `resolved_at` and `hidden: false`; an empty or whitespace
  response throws without writing; approval replaces an existing catalogue photo and leaves other
  suggestions for the item untouched.
- `ModerationCatalogueEditorViewModelTests`: opened from a ticket, a failed save does not resolve; a
  failed ticket write after a successful save keeps the ticket pending; the pre-filled response is in
  the ticket's locale, not the device's.
- `DeleteAccountUseCaseTests`: tickets are anonymised, not deleted; suggestion files stay.
- `NotificationBellViewModelTests`, `NotificationsViewModelUserNotificationsTests`: as in the previous
  draft, plus hide (row removed, restored on failure) and *Withdraw* hidden for a resolved row.
- Android `DTOWireShapeTest`: the create DTOs carry `status`, `hidden` and `locale` and **no**
  `response`, `resolved_at`, `seen_at` keys.

### Order of work

One commit per platform. The current branch implements the previous draft; its iOS and Android commits
are reworked (the branch is unpushed).

1. **Backend**: Firestore rules, Storage rules, indexes, rules tests. Owner deploys and waits for the
   indexes.
2. **iOS**, 3. **Android**: data layer, submit and account deletion, maintainer side, bell, list and
   detail, localisation.
4. **Docs**: `ARCHITECTURE.md` § 7.7, § 1.6 rules, § 1.7 wire schema.

## Decisions made during the write-up

- **A resolution reaches the user within 24 hours** (the gate in *The bell*), or at once after a pull
  to refresh. Accepted by the owner over querying on every refresh while a ticket is pending.
- **The uid in a deleted user's photo path** (`photoSuggestions/{uid}/…`, `photo_url`) stays. It is a
  random identifier that maps to nothing once the Auth account is deleted. Accepted by the owner.
- **A resolved photo has no preview** — the suggestion file is deleted on resolution. The detail says
  the photo was approved or rejected; an approved one gets its own presentation, designed during
  implementation.

## Implementation notes

**iOS**

- New `FirestoreDataProviderProtocol` methods go with a throwing default in the protocol extension
  (`FirestoreDataProvider.swift`, below the protocol), so the fakes in at least ten test files compile
  unchanged.
- The create DTOs must not send the maintainer's or the read-state fields: optional `nil` properties
  are omitted by the synthesized encoder, which the `keys().hasOnly` create rules need. Keep them out
  of the create inits.
- `UserNotificationsStore` follows `Features/Dashboard/SignInSpotlightStore.swift`, keyed by uid.
- A `.sheet` does not inherit the text size cap: apply `.dynamicTypeLimit()` to new sheet content.
- Colours only through asset roles.

**Android**

- `FirestoreDataMapper.kt` sets `explicitNulls = false`; declare the optional DTO properties `= null`
  so a create omits them. `DTOWireShapeTest` pins this.
- Fakes live in `app/src/debug/.../core/usecases/`.

**Verify by hand:**

- A second account: report an item → dot → open detail → dot gone. As the maintainer, *Save and
  resolve* with the pre-filled text → after a pull to refresh the user sees *Resolved*, the response
  and the dot. Report the same item again → a new pending ticket.
- Hide a pending notification, resolve it as the maintainer → it is back, unread. Hide it again → it
  stays hidden after reinstall and on a second device; the document still exists in the console.
- Reject without writing a response → *Send* is disabled.
- A user with the app in English: the maintainer's pre-filled response is in English.
- Two users suggest a photo for one item; approve one, then the other → the second replaces the first.
- Withdraw a pending report and photo → document and file gone. A resolved ticket has no *Withdraw*.
- Delete an account with a pending photo → the ticket is in the queue without an owner and can be
  approved.

## Alternatives considered

- **Read state kept on the device.** Rejected by the owner in favour of Firestore.
- **A separate `users/{uid}/notifications` collection.** Rejected: two writes that can diverge; the
  ticket document already carries everything the notification needs.
- **A fixed-id "lock" document per user and item**, moved to an auto id on resolution, so the rules
  enforce one pending ticket. Rejected by the owner: resolution becomes a create + delete in a batch,
  for protection only against a modified client, which the maintainer handles by rejecting.
- **Resolving all reports for an item at once** (0012). Rejected: two reports about different fields
  must be answered separately; fixing one does not resolve the other.
- **Deleting the tickets on account deletion** (0012, 0031). Rejected by the owner: the report and the
  record of what changed in the catalogue are kept; only the link to the person is removed.
- **Marking everything read when the list is opened.** Rejected by the owner: read means the detail was
  shown.
- **One sheet mixing moderation rows and the maintainer's own notifications.** Rejected.
- **Querying on every dot refresh.** Rejected by the owner as too expensive; see *Decisions*.
- **Visibility derived from a `hidden_at` timestamp** compared with the last event. Rejected: Firestore
  cannot filter on a comparison of two fields, so hidden tickets would still be downloaded and billed.

## Cross-cutting concerns

- **Cost.** Tickets are never deleted, so storage grows by one small document per ticket. The user's
  list reads only tickets they have not hidden; the maintainer's queue reads pending ones only.
- **Privacy.** Kept tickets hold the user's note but, after account deletion, no uid. The privacy
  policy must say that reports and photos sent for the shared catalogue are kept in anonymised form.
- **Old clients.** None shipped to users; only the maintainer's build is in production. Its rules
  and queries change with this release.
- **Sequencing.** Rules, Storage rules and indexes before the client.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Index not built when the client ships | Queue or user list fails | Deploy and wait first |
| A modified client creates a second pending ticket for the same item | One extra row in the queue | Maintainer rejects it |
| Resolution written but suggestion file delete fails | Orphaned file in Storage | Logged; account deletion no longer cleans it, so a cleanup is manual |
| Item saved but report resolution write fails | Ticket stays pending though fixed | The editor keeps the response sheet open for a retry |
| Maintainer claim lookup fails | Maintainer gets the user sheet for that refresh | The next foreground corrects it |

## Outcome

*Filled in once after shipping.*
