# Design: Food photos

- **Status:** Approved
- **Scope:** Backend, Cross-platform, iOS, Android
- **Date:** 2026-10-08

## Context and scope

A user picking a food by name cannot tell whether "Jogurt bílý 3 %" is the cup in their hand. This
document adds one photo of the product (the front of the packaging, or the food itself) to every
food that has a source for it, and shows it wherever a single food is looked at.

[Design 0009](0009-catalogue-moderation.md) deferred *"a packaging photograph on a submission"* to
`TODO.md`, framed as a moderation aid stored in Firebase Storage with an optional `photo_path` on
`FoodItemSubmissionDTO`. This document replaces that framing. The photo is **catalogue content
every user sees**, not only something the maintainer checks, and that changes where it lives, who
may write it and who must supply it. 0009's other decisions (two identities, status lifecycle,
log-before-approval, duplicate handling) are unchanged and binding here.

[Design 0010](0010-nutrition-label-photo-prefill.md) is unrelated despite the name: its live scan
reads the nutrition table, keeps no image (*Revision — live scan on both clients*), and stays that
way. The product photo is a separate, explicit capture.

Two sources, decided by the user:

- **`.external` items** (OpenFoodFacts) show OpenFoodFacts' own front photo. Nothing is stored.
- **`.catalogue` items** (`foodItems`) show a photo stored by this project, supplied by the person
  who submits the item and approved together with it.

## Goals

- Every **new submission carries exactly one photo**. Submitting without one is impossible, enforced
  by the client and by `firestore.rules`.
- The photo is visible on `FoodQuantity`, `FoodConsumedDetail`, the moderation Review screen and
  the catalogue editor (which is also where a reported item is reviewed — `ARCHITECTURE.md` § 7.6).
- The maintainer can add or replace the photo of any catalogue item, so items that predate this
  design can gain one.
- iOS and Android behave identically: same capture options, same image processing, same layout.
- A food with no photo looks exactly as it does today — no empty placeholder outside the form.

## Non-goals

- **Photos on search result rows.** Only the screens above. A search result list would multiply
  image downloads per keystroke.
- **Photos on created meals** (`myCreatedMeals`). Private, composed by the user, nothing to confirm.
- **More than one photo per food**, galleries, pinch-to-zoom.
- **Interactive cropping.** The image is centre-cropped automatically on both platforms; where the
  product sits inside the circle is accepted as it falls (user's decision).
- **A photo for an existing item supplied by an ordinary user.** Only through a new submission
  (which refuses a barcode already in the catalogue, 0009 *Duplicate handling*) or by the
  maintainer in the catalogue editor. Reporting stays text-only.
- **Backfilling photos for existing catalogue items by script.** The maintainer adds them by hand
  in the catalogue editor as items are reported or noticed.
- **Offline behaviour.** The app is online-only.

## Design

### Prerequisites (the user does these; not code)

1. **Upgrade the Firebase project to the Blaze plan.** Since October 2024 a new default Storage
   bucket cannot be created on Spark. Set a budget alert (e.g. 1 USD) in Google Cloud Billing.
2. **Create the default bucket** in Firebase Console → Storage. The no-cost Storage tier
   (5 GB stored, 1 GB/day download) applies only to buckets in `us-central1`, `us-east1` or
   `us-west1`; an EU bucket is billed from the first byte (cents at this volume). Recommended:
   `us-central1` — a 150 KB image's added latency from Czechia is not noticeable.
3. Write the bucket name (`<project>.firebasestorage.app`) into the two rule regexes below and
   into the client constants.

Implementation must not start before 1–2 are done; nothing below can be tested otherwise.

### Wire shape

One new optional field, **`photo_url`** (string), on `FoodItemDTO` — and therefore on both
`foodItems` documents and the nested `item` of a `foodItemSubmissions` document, since 0009 made
the submission's `item` the exact `FoodItemDTO` shape so that approval is a copy.

It holds a Firebase Storage **download URL** (`https://firebasestorage.googleapis.com/v0/b/<bucket>/o/<path>?alt=media&token=…`),
not a bare path. Reasons:

- The read path needs no Storage SDK and no `getDownloadURL()` round trip: both clients load the URL
  directly, exactly like an OpenFoodFacts image URL. `FoodItemDomain` gets one field for both
  sources.
- Deletion still works from the URL: `Storage.storage().reference(forURL:)` /
  `FirebaseStorage.getInstance().getReferenceFromUrl()`.
- A download URL is readable by anyone holding it. Accepted: these are photos of shop products shown
  to every user anyway.

`photo_url` is **not** added to `favouriteFoods`, `foodFrequencies` or `foodConsumed` snapshots — a
snapshot would go stale the moment the maintainer replaces a photo, and older snapshots would lack it
regardless. Screens resolve it at open time (see *Display*).

Domain: `FoodItemDomain` gains `photoURL: URL?` (Kotlin `photoUrl: String?`), filled from
`photo_url` for `.catalogue`, from `image_front_url` for `.external`, `nil` for created meals and
snapshots. `FoodItemFormInput` gains the photo state described under *The form*.

`ARCHITECTURE.md` § 1.7 wire schema gains the row: `photo_url` | string, Storage download URL |
no — `nil` | see rules.

### Storage layout

```
submissionPhotos/{uid}/{uuid}.jpg   author's upload for a pending/rejected submission
catalogPhotos/{itemId}/{uuid}.jpg   photo of an approved catalogue item; maintainer-written only
```

Two folders because ownership changes at approval. If the catalogue pointed at the author's file,
the author (who needs `delete` on their own folder for withdrawal and account deletion) could delete
a photo every user sees. So **approval copies** the file into `catalogPhotos`: download ~150 KB,
upload, done by the maintainer's client once per approval.

Every file name is a fresh UUID; a file is never overwritten. Replacing a photo = upload a new file,
point the document at it, delete the old file. No CDN or client cache can show a stale image,
because the URL itself changes.

### `storage.rules` (new)

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    function isMaintainer() {
      return request.auth != null && request.auth.token.maintainer == true;
    }
    function validJpeg() {
      return request.resource.size < 1024 * 1024 &&
        request.resource.contentType == 'image/jpeg';
    }

    match /submissionPhotos/{uid}/{fileName} {
      allow read: if request.auth != null && (request.auth.uid == uid || isMaintainer());
      allow create: if request.auth != null && request.auth.uid == uid && validJpeg();
      allow delete: if request.auth != null && (request.auth.uid == uid || isMaintainer());
    }

    match /catalogPhotos/{itemId}/{fileName} {
      allow read: if request.auth != null;
      allow create: if isMaintainer() && validJpeg();
      allow delete: if isMaintainer();
    }
  }
}
```

No `update` anywhere (files are immutable). `read` also grants `list`, which account deletion
needs on the author's own folder.

### `firestore.rules` changes

`validFoodItem(data)` stays as is plus an optional, prefix-checked field:

```
(!('photo_url' in data) || (data.photo_url is string && data.photo_url.size() <= 1024))
```

Then, per collection (`BUCKET` = the escaped bucket URL prefix
`https://firebasestorage\\.googleapis\\.com/v0/b/<bucket>/o/`):

- **`foodItems` create/update** (maintainer): if `photo_url` present, it must match
  `BUCKET + 'catalogPhotos%2F.*'`. Optional — existing items have none, and the catalogue editor
  must keep saving them.
- **`foodItemSubmissions` create, and the author's update branch** (resubmit): `item.photo_url`
  **required** and must match `BUCKET + 'submissionPhotos%2F' + request.auth.uid + '%2F.*'` — an
  author can reference only a file in their own folder. This is the server-side half of
  *mandatory*.
- **`foodItemSubmissions` maintainer update branch** (reject): unchanged — `photo_url` optional, so a
  pre-existing photo-less pending submission can still be rejected.

`firestore-rules-tests` gets cases for: create without `photo_url` denied; create pointing at another
uid's folder denied; create pointing at a non-Storage URL denied; `foodItems` with a
`submissionPhotos` URL denied; maintainer reject of a photo-less submission allowed.

### Image processing (identical on both platforms)

Applied to every captured or picked image before it is held in the form:

1. Decode, applying EXIF orientation.
2. Centre-crop to a square (side = shorter edge).
3. Downscale to 1080 × 1080 px (never upscale; a smaller square stays as is).
4. Encode JPEG, quality 0.7 (iOS `jpegData(compressionQuality: 0.7)`, Android
   `Bitmap.compress(JPEG, 70, …)`). Expected 100–250 KB.
5. Upload **only these re-encoded bytes**, never the original file. Re-encoding drops all EXIF,
   including GPS — this is the privacy guarantee, so it must not be "optimised" into uploading the
   original.

Specified as one pure function per platform (`FoodPhotoProcessing.process(_:) -> Data` /
`ByteArray`), unit-tested on both: output is square, ≤ 1080 px, decodes as JPEG, carries no EXIF.

### Capture

Tapping the photo slot opens an action sheet (iOS `confirmationDialog`, Android `ModalBottomSheet`
per `Android/CLAUDE.md` stack table):

- **Take photo** — system camera UI. iOS `UIImagePickerController(sourceType: .camera)`,
  `allowsEditing = false`. Android `ActivityResultContracts.TakePicture` into a `cacheDir` file via
  `FileProvider`.
- **Choose from library** — iOS `PhotosPicker` (images only), Android
  `ActivityResultContracts.PickVisualMedia(ImageOnly)`. Neither needs a permission.
- **Cancel.**

No *Remove* action: the photo is mandatory on a submission, so it is only ever replaced. A new
result always replaces the previous one; a cancelled camera/picker leaves the existing photo
untouched.

Camera permission: both apps already hold it for the live scanner and already have a three-state
check (design 0010 camera-first revision). Reuse it. **Android gotcha:** because the manifest
declares `CAMERA`, `ACTION_IMAGE_CAPTURE` throws `SecurityException` when the permission is not
granted, even though it launches another app. Request the permission first; on denial show the same
"camera access denied → Settings" alert the scanner uses, and leave *Choose from library* working.

### The form (`FoodItemFormSections`, both platforms)

`FoodItemFormSections` is shared by the *add a new food* form (`AddFoodSheetView`),
`ModerationReviewView` and `ModerationCatalogueEditorView`, so one change covers all three.
Today its single `Section` holds name, barcode, the scan button and the nutrition fields. It becomes:

```
Section 1   name, barcode                    (moved out unchanged)
Section 2   photo slot                       (new, clear row background, centred)
Section 3   nutrition-label scan button, FoodItemFormFields
(FoodPortionsSection stays first, above all three, as today)
```

**Photo slot** — new component `FoodPhotoPicker` (`Components/`, same name on Android):

- 120 pt circle, centred, caption below: *Fotka produktu* / *Product photo*.
- **Empty:** dashed `secondary` outline, `fork.knife` symbol (Android
  `Icons.Filled.Restaurant`) in `secondary` inside. Badge bottom-right: 36 pt accent circle with a
  white `camera.fill` (Android `Icons.Filled.PhotoCamera`) — the same colours as `BadgeButton`,
  but not `BadgeButton` itself, since the whole circle is the tap target and the badge is not a
  button of its own.
- **Filled:** the image fills the circle (`scaledToFill`, clipped). Badge icon becomes
  `arrow.triangle.2.circlepath.camera` (Android `Icons.Filled.Cameraswitch`), meaning *retake*.
- The whole circle is the tap target and opens *Capture*.
- **Validation highlight:** when the user tries to submit without a photo, the outline turns
  `Color.error` and the caption reads *Přidejte fotku produktu* / *Add a product photo*, the same
  highlight mechanism the other form fields use (`highlightedFields`, new case `.photo`).
- VoiceOver / TalkBack label: *Fotka produktu* plus *vyfotit* or *vyfotit znovu*.

**State** — `FoodItemFormInput` gains:

```swift
enum FoodItemFormPhoto: Equatable {
    case none
    case remote(URL)      // already stored: editing a rejected submission, review, catalogue editor
    case local(Data)      // processed JPEG, not yet uploaded
}
```

Upload happens **only when the form is submitted/saved**, never on capture — otherwise every
abandoned form leaves a file behind.

**Which screens require it:**

| Screen | Use case on save | Photo |
|---|---|---|
| Add a new food | `SubmitFoodItemUseCase` | required |
| Edit a rejected submission | `UpdateMySubmissionUseCase` | required (an old photo-less one must gain one) |
| Moderation Review → Approve | `ApproveSubmissionUseCase` | required |
| Moderation Review → Reject | `RejectSubmissionUseCase` | ignored; a local replacement is discarded |
| Catalogue editor | `UpdateFoodItemUseCase` | optional |

The requirement is a check in the use case (new error case `photoMissing` on
`FoodItemSubmissionError` and on the approve error), mirrored by the view model's `canSave`, the
same "one predicate, two callers" pattern 0009 *Outcome* describes. It is **not** added to
`FoodItemValidation`, which the catalogue editor also uses.

### Write paths

A new use case wraps Storage: `UploadFoodPhotoUseCase(data:folder:) -> URL` and
`DeleteFoodPhotoUseCase(url:)`, over a new `StorageDataProviderProtocol`
(`uploadAsync(data:path:contentType:) -> URL`, `downloadAsync(url:) -> Data`,
`deleteAsync(url:)`, `listAsync(prefix:) -> [URL]`) — the Storage counterpart of
`FirestoreDataProviderProtocol`, with a `Fake` for tests.

- **Submit:** `.local(data)` → upload to `submissionPhotos/{uid}/{uuid}.jpg` → write the submission
  with `item.photo_url`. If the Firestore write fails, delete the just-uploaded file (best effort,
  logged).
- **Resubmit:** if the photo is `.local`, upload the new one, update the submission, then delete
  the old file. If `.remote`, nothing touches Storage.
- **Withdraw** (`DeleteMySubmissionUseCase`): delete the submission, then its photo. A failed photo
  delete is only logged.
- **Approve** (`ApproveSubmissionUseCase`), in this order:
  1. Get the bytes: the maintainer's `.local` replacement, or download the submission's photo.
  2. Upload to `catalogPhotos/{itemId}/{uuid}.jpg`.
  3. `CreateFoodItemUseCase` with `photo_url` = the catalogue URL. Its existence check stays the
     guard (0009). On failure, delete the file from step 2.
  4. Delete the submission document (as today), then the submission's photo file.
  Steps 4's failures are logged, matching 0009's accepted "two writes, no transaction".
- **Reject:** no Storage change. The author keeps their photo for the resubmit.
- **Catalogue editor save:** if `.local`, upload to `catalogPhotos/{itemId}/`, update the item,
  then delete the previous catalogue file if there was one.
- **Account deletion** (`DeleteAccountUseCase`): after the existing submission wipe, list and
  delete everything under `submissionPhotos/{uid}/`. Listing the folder, rather than following the
  deleted submissions' URLs, also removes orphans from earlier failed writes. `catalogPhotos` is
  untouched — approved items are shared catalogue content, the same call 0009 made for the items
  themselves.

Orphaned files that survive all of this (a crash between upload and write) are accepted; they are
small, owned by a uid, and removed at that account's deletion.

### OpenFoodFacts

Both `FetchFoodByBarcodeExternallyUseCase` and `SearchFoodExternallyUseCase`, on both platforms,
add `image_front_url` to their `fields` parameter. `OpenFoodFactsProductDTO` decodes it as an
optional string and maps it into `FoodItemDomain.photoURL`. One field, no extra request.

OpenFoodFacts images are CC BY-SA. The existing attribution in `AccountView`
(`L10n.Account.dataAttribution`) is reworded to cover images as well as data.

### Display

**Resolving the URL on open.** `FoodQuantityView` receives the item from search (has `photoURL`),
from a favourite or the frequency list (snapshot, no `photoURL`), or from OFF.
`FoodConsumedDetailViewModel.loadCatalogueItem()` already fetches the full item on appear by kind.
`FoodQuantityViewModel.onAppear()` does the same, **only when `item.photoURL == nil`** and kind is
`.catalogue` or `.external`, reusing `FetchFoodItemByBarcodeUseCase` /
`FetchFoodByBarcodeExternallyUseCase`. Search-originated items cost nothing extra.

**`FoodPhotoThumbnail`** (new component, both screens):

- A 60 pt circle with the image, anchored bottom-left, `padding(.leading, 20)`, `.padding(.bottom, 8)`,
  added with `.safeAreaInset(edge: .bottom)` on the `List` (`iOS/CLAUDE.md` FAB pattern). It does not
  scroll; the `List` gets a bottom inset so its last row (left-aligned label, e.g. *Sůl*) can scroll
  clear of it. Android: `Scaffold` bottom content / `Box(Alignment.BottomStart)` plus matching
  `contentPadding` on the `LazyColumn`.
- Badge bottom-right: 24 pt accent circle, white `arrow.up.left.and.arrow.down.right`
  (Android `Icons.Filled.OpenInFull`) — "enlarge", deliberately not `+`, which on this screen would
  read as *add the food*.
- **Absent** (no inset either) when `photoURL == nil`, while the image is still loading, or when
  loading failed. No placeholder, no spinner.
- **Hidden while the quantity keyboard is up** (`isQuantityFocused`), so it does not sit over the
  little content left visible.
- Tap → **`FoodPhotoViewer`**: full-screen overlay, black background, image `scaledToFit`, animated
  out of the thumbnail (`matchedGeometryEffect`; Android `SharedTransitionLayout`). Tap anywhere or
  swipe down closes it. No zoom.

Moderation screens show the photo through the form's `FoodPhotoPicker` (filled state). A 120 pt
circle is too small to check a product against its name, so when the slot is filled its action
sheet gains a first option *Show photo* / *Zobrazit fotku* → `FoodPhotoViewer`. The same sheet is
used on the author's form, so the component has one behaviour everywhere.

**Loading.** iOS: `AsyncImage` (no new dependency; `URLCache.shared` caches by URL, and URLs never
change content). Android has no image loader today: add **Coil 3** (`io.coil-kt.coil3:coil-compose`
and `coil-network-okhttp`) — a new dependency, the Android equivalent of `AsyncImage`.

### Dependencies

- iOS: `FirebaseStorage` product from the existing Firebase SPM package.
- Android: `com.google.firebase:firebase-storage` (BoM), Coil 3 as above, a `FileProvider` entry in
  the manifest for the camera output file.

### Backend config

- `backend/firebase.json`: `"storage": { "rules": "storage.rules" }`, and a `storage` emulator
  (port 9199) in `emulators`.
- `backend/storage.rules`: new, as above.
- `firestore-rules-tests`: the `test` script runs `--only firestore,storage`; new storage rule cases
  beside the Firestore ones (`@firebase/rules-unit-testing` supports both).
- **CI:** `.github/workflows/ci.yml` runs these tests; the storage emulator must be added there.
  Tell the user before changing the workflow (root `CLAUDE.md`).
- Deploy: `firebase deploy --only firestore:rules,storage`. Firestore rules and Storage rules go out
  together, before any client that writes `photo_url` ships.

### Localization

Source `cs`; iOS `L10n` + `Localizable.xcstrings`, Android `values/strings.xml` +
`values-en/strings.xml`.

| Key | cs | en |
|---|---|---|
| `foodPhoto_title` | Fotka produktu | Product photo |
| `foodPhoto_error_required` | Přidejte fotku produktu | Add a product photo |
| `foodPhoto_action_take` | Vyfotit | Take photo |
| `foodPhoto_action_choose` | Vybrat z galerie | Choose from library |
| `foodPhoto_action_show` | Zobrazit fotku | Show photo |
| `foodPhoto_accessibility_retake` | Vyfotit znovu | Retake photo |
| `foodPhoto_accessibility_enlarge` | Zvětšit fotku | Enlarge photo |
| `foodPhoto_error_uploadFailed` | Fotku se nepodařilo nahrát. Zkuste to znovu. | The photo could not be uploaded. Try again. |

### File-by-file impact

Backend: `backend/firebase.json`, `backend/storage.rules` (new), `backend/firestore.rules`,
`firestore-rules-tests/` (package script + cases), `.github/workflows/ci.yml` (after telling the
user).

Both clients (Swift path; Android mirrors names under `antoni/kalorie/`):

- New: `StorageDataProvider` (+ protocol, Fake), `UploadFoodPhotoUseCase`, `DeleteFoodPhotoUseCase`,
  `FoodPhotoProcessing`, `Components/FoodPhotoPicker`, `Components/FoodPhotoThumbnail`,
  `Components/FoodPhotoViewer`, `FoodItemFormPhoto`.
- Changed: `FoodItemDTO` (`photo_url`), `FoodItemModel` (`photoURL`), `OpenFoodFactsProductDTO`,
  `FetchFoodByBarcodeExternallyUseCase`, `SearchFoodExternallyUseCase` (`fields`),
  `FoodItemFormInput`, `FoodItemFormSections`, `SubmitFoodItemUseCase`,
  `UpdateMySubmissionUseCase`, `DeleteMySubmissionUseCase`, `ApproveSubmissionUseCase`,
  `UpdateFoodItemUseCase`, `DeleteAccountUseCase`, `FoodItemSubmissionModel` (error case),
  `AddFoodSheetViewModel`, `ModerationReviewViewModel`, `ModerationCatalogueEditorViewModel`,
  `FoodQuantityView`/`ViewModel`, `FoodConsumedDetailView`, the three configurators that build these
  use cases, `Constants` (bucket, folder names), `AccountView` attribution text, localization files.
- Android only: `build.gradle.kts`, `AndroidManifest.xml` (`FileProvider`), `res/xml/file_paths.xml`.

Tests (one per changed/new use case, fakes, `makeSUT()`), the ones that guard intent:

- `SubmitFoodItemUseCaseTests`: no photo → `photoMissing`, nothing uploaded, nothing written; upload
  succeeds + write fails → uploaded file deleted.
- `ApproveSubmissionUseCaseTests`: catalogue item's `photo_url` points to `catalogPhotos`, **never**
  to `submissionPhotos` (the author could delete that); collision still refused (0009), and the
  copied file is deleted when it is.
- `UpdateFoodItemUseCaseTests`: saving without a photo still succeeds (old items).
- `DeleteAccountUseCaseTests`: lists and deletes the user's `submissionPhotos` folder, touches no
  `catalogPhotos`.
- `FoodPhotoProcessingTests`: square, ≤ 1080, no EXIF/GPS in output.
- `DTOWireShapeTest` (Android) / iOS DTO tests: `photo_url` round-trips and is absent-tolerant.

Docs: `ARCHITECTURE.md` § 1.2 (Storage layout), § 1.6 (rules), § 1.7 (`photo_url`), § 7 (photo in
the submission flow) after shipping; `SETUP.md` Firebase Console section (Blaze, bucket, Storage
rules deploy); privacy policy text and store declarations (below).

## Alternatives considered

- **Photo bytes in Firestore** (a `Bytes` field in a side document). No Blaze, atomic batch writes
  with the submission. Rejected once the photo became catalogue content: every viewer's read is a
  billed document read with no CDN or HTTP caching, and Spark's 50 k reads/day would be shared with
  the whole app.
- **OpenFoodFacts photos for catalogue items too** (look the barcode up on OFF). Rejected by the
  user: our items exist largely because OFF lacks them, and items without a barcode (UUID ids) have
  nothing to look up.
- **The catalogue pointing at the author's uploaded file** (no copy on approval). Rejected: the author
  holds `delete` on their own folder, so they — or account deletion — would remove a photo every user
  sees. Denying them `delete` instead would leave withdrawn and deleted-account photos behind.
- **Optional photo.** Rejected by the user: the point is that a catalogue food can be recognised.
- **Interactive crop** (`allowsEditing` on iOS). Rejected: Android has no equivalent system crop, and
  the two platforms must behave the same.
- **`photo_path` (bare Storage path) instead of a download URL.** Rejected: every display would need a
  `getDownloadURL()` round trip, and OFF items would need a second field for their HTTP URL.
- **Denormalising `photo_url` into favourites / frequency / consumed snapshots.** Rejected: stale on
  replacement, missing on all existing snapshots; one read on open is cheaper than keeping copies in
  sync.

## Cross-cutting concerns

- **Privacy.** A photo is new user-supplied content leaving the device. Re-encoding strips EXIF/GPS
  (*Image processing*). [Design 0021](0021-privacy-policy.md) currently states *"No image leaves the
  device, none is stored"*, lists Firebase Storage among SDKs not used, and declares *Photos: No* in
  the store tables — all three change. The privacy policy page (`backend/hosting`) gets a paragraph:
  a product photo attached to a submission is stored, visible to the maintainer, and after approval
  to every user; deleted with the account unless already approved. **App Store** privacy label:
  *Photos or Videos — collected, linked to the user (uid), App Functionality, not tracking*.
  **Google Play** Data safety: *Photos — collected, shared: no, purpose App functionality, user can
  request deletion*. Both are the user's console work; ship them with the release that uploads.
- **Content.** A user can upload any picture, which other users see once approved. The approval gate
  is the mitigation; the maintainer must look at the photo, not only the numbers.
- **Anonymous → signed-in merge.** Unchanged from 0009: submissions are not merged, so photos under the
  anonymous uid stay attached to the anonymous submissions and are not cleaned until that uid is
  deleted.
- **Cost.** At ~150 KB per photo, 5 GB is ~33 000 photos; on the US no-cost tier this project stays at
  0 USD for the foreseeable future. Downloads: one image per food detail opened, cached per URL.
- **Second platform.** Everything here is Cross-platform; the moderation screens exist on both
  clients, so both get the photo in the form.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Blaze plan with a billing account | An unexpected bill | Budget alert; US bucket for the no-cost tier; uploads capped at 1 MB by rules |
| A client ships before `storage.rules` is deployed | Storage denies every upload by default, so every submission fails at the photo step | Deploy Storage and Firestore rules first, then release clients |
| Old clients keep running after the new Firestore rules deploy | Their submissions lack `photo_url` and are denied — users of an outdated build cannot submit | Accepted: submitting is a minor path; the denial surfaces as the existing generic submit error. Ship both clients' updates promptly after the deploy |
| Approval copies the file, then the create fails | An orphan in `catalogPhotos` | Deleted in the same use case on failure; covered by a test |
| Original image uploaded instead of the processed bytes | GPS location of the user's home published | Only `FoodPhotoProcessing` output reaches `UploadFoodPhotoUseCase` (its parameter is the processed `Data` type); test asserts no EXIF |
| Android `TakePicture` with `CAMERA` declared but not granted | Crash (`SecurityException`) | Permission requested first, as specified in *Capture* |
| Old pending submissions have no photo | Cannot be approved as-is | Maintainer adds a photo in Review; reject still works without one |
| Inappropriate image | Shown to every user after approval | Approval gate; catalogue editor can replace it |

## Outcome

Not yet implemented.
