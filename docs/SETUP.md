# Setup and deployment

Configuration that lives outside the repository. None of it is reproducible from source, so it
has to be written down.

## Xcode capabilities

| Capability | Required by | Notes |
|---|---|---|
| Sign in with Apple | `SignInWithAppleUseCase` | Xcode → Signing & Capabilities → **+ Sign in with Apple**. Adds `com.apple.developer.applesignin` to `Kalorie/Resources/Kalorie.entitlements`. Without it the authorization request fails at runtime. |

`Kalorie.entitlements` also carries `com.apple.security.app-sandbox` and
`com.apple.security.files.user-selected.read-only`. Those are macOS keys with no effect on iOS —
they can be removed next time the file is touched.

## Apple Developer portal

Enable **Sign in with Apple** for the App ID. This is separate from the Xcode capability; both
are required.

## Firebase Console

| Setting | Where | Notes |
|---|---|---|
| Apple sign-in provider | Authentication → Sign-in method → Apple | Must be enabled before Apple credentials are accepted. |
| Google sign-in provider | Authentication → Sign-in method → Google | Must be enabled before Google credentials are accepted — turning it on is what adds `CLIENT_ID` and `REVERSED_CLIENT_ID` to `GoogleService-Info.plist`; without those keys the Google SDK cannot be configured at all. |
| User account linking | Authentication → Settings → User account linking | Must stay on **One account per email address** (the default). [ADR 0006](adr/0006-google-sign-in-identity-collisions.md) depends on it: switching to *multiple accounts* silently disables the collision warning in every client and makes silent account splitting unconditional. Do not change without superseding that record. |
| Anonymous provider | Authentication → Sign-in method → Anonymous | Already enabled; the app depends on it for the signed-out mode (see [ADR 0001](adr/0001-anonymous-firebase-auth-as-device-identity.md)). |

Google sign-in needs **no** new Xcode capability and no Apple Developer portal change. The client
ID is read at runtime from `FirebaseApp.app()?.options.clientID`, so `GIDClientID` is deliberately
**not** added to Info.plist — the value stays in `GoogleService-Info.plist` only. The one place it
is duplicated is the `CFBundleURLTypes` URL scheme in `Kalorie/Resources/Info.plist`, which must
match `REVERSED_CLIENT_ID` exactly; if the Firebase project is ever replaced, that scheme has to
be updated by hand along with the plist and the CI secret.

## Firestore security rules

Rules are versioned in `Kalorie/firestore.rules` and field-index overrides in
`Kalorie/firestore.indexes.json`, but **the repository is not the source of truth for what is
live** — they have to be deployed explicitly:

```sh
cd Kalorie
firebase deploy --only firestore:rules,firestore:indexes
```

Test before deploying. The rules have an automated suite in `firestore-rules-tests/` that runs
against the Firestore emulator (needs JDK 21 and Node); CI runs it on every pull request:

```sh
cd firestore-rules-tests
npm ci
npm test
```

When you change `firestore.rules`, change the suite in the same commit. The Rules Playground in the
console is still fine for one-off experiments.

`rules_version = '2'` is default-deny: a collection with no matching rule block is inaccessible,
and the failure only shows up at runtime. When adding a collection to `Constants.Firestore`, add
a matching rule block in the same change.

### Maintainer claim (catalogue moderation)

The moderation panel ([design 0009](design/0009-catalogue-moderation.md)) is gated by a Firebase
custom claim, `maintainer: true`, checked both client-side (renders the panel) and in
`firestore.rules` (the only real enforcement — the client-side check is a UI gate, not a defence).
Setting it:

1. Find your own UID: Firebase Console → Authentication → Users, or sign in on-device and check
   the account screen / Crashlytics user ID. Use the UID linked to your Apple/Google sign-in, not
   a bare anonymous session — every install starts anonymous (§ 6.2 in `ARCHITECTURE.md`), and
   that UID is throwaway. Signing in with the same Apple/Google account always converges back to
   the same linked UID (via `credentialAlreadyInUse` → migrate), so the claim survives reinstalls
   as long as you sign in again after each one.
2. Run the script with a service account key (the same one the backfill scripts under `scripts/`
   already use):
   ```sh
   node scripts/set-maintainer-claim.js --key <path-to-service-account.json> --uid <your-uid>
   ```
   Pass `--remove` to unset the claim instead.
3. **Sign out and back in on the device**, or force a token refresh
   (`getIDTokenResult(forcingRefresh: true)`). Custom claims only propagate to a fresh ID token
   (roughly hourly otherwise), so without this step the panel stays invisible and looks broken.

## Android client

No Android app exists yet; this is what has to be configured outside the repository before one can
build and sign in. The Gradle rows were exercised with a scratch app; the Firebase rows were not —
they are the known shape of Firebase's Android setup, not a record of a run.

| Step | Where | Notes |
|---|---|---|
| Register the Android app | Firebase Console → Project settings → Your apps → Add app → Android | Same Firebase project as the iOS app, so both clients share `firestore.rules` and every user's data. The package name chosen here is permanent. |
| Add the signing SHA-1 (and SHA-256) | Same screen, *SHA certificate fingerprints* | Google sign-in on Android is bound to the signing certificate's fingerprint. Add the **debug** keystore's, the **release** keystore's and, if Play App Signing is on, the **app-signing key** shown in Play Console → App integrity (it differs from the upload key). A missing fingerprint fails as `DEVELOPER_ERROR` / code 10 with no further hint. Print the debug one with `./gradlew signingReport`. |
| Download `google-services.json` | Same screen, after the fingerprints are added | Download it *after* adding them; an older copy lacks the OAuth client entries. Not committed, same as `GoogleService-Info.plist` (§ Local build); CI needs its own secret. |
| Force Kotlin 2.4.10 | Android app's root `build.gradle.kts` | AGP 9.4.1 ships Kotlin 2.2.0, which cannot read the shared modules. Add `kotlin-gradle-plugin:2.4.10` to `buildscript` classpath — the snippet is in [ADR 0037](adr/0037-shared-modules-target-ios-and-jvm-and-are-consumed-by-composite-build.md) item 7. |
| Give Gradle more memory | Android app's `gradle.properties` | `org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g`; the default Metaspace runs out with the four composite builds. |
| Compile against API 37 | Android app's `compileSdk` | [ADR 0037](adr/0037-shared-modules-target-ios-and-jvm-and-are-consumed-by-composite-build.md): ExportKit's PDF dependency refuses anything lower. |
| Consume the shared modules | Android app's `settings.gradle.kts` | Composite build, per [ADR 0037](adr/0037-shared-modules-target-ios-and-jvm-and-are-consumed-by-composite-build.md); the app lives in this repository, so the paths are relative to it. |

Not covered because it is undecided: how Apple sign-in works on Android (Firebase offers it only
through a web-based OAuth flow, which needs an Apple Services ID this project does not have), and
how the maintainer claim (§ Maintainer claim) is set for an Android session — it is per-UID, so an
account already claimed on iOS carries over once the same Apple/Google account signs in.

## Crashlytics

No Firebase Console toggle is needed — unlike the Auth providers above, Crashlytics activates
itself on the first symbol/crash upload. Confirm it shows up in the console after the first
Release build that ships with the `FirebaseCrashlytics` product and the dSYM-upload Run Script
phase (see [design doc 0007](design/0007-crash-reporting-and-logging.md)). Collection is enabled
in Release and disabled in `DEBUG` (`AppDelegate.application(_:didFinishLaunchingWithOptions:)`).

## CI

`.github/workflows` runs `xcodebuild test` on pull requests against `main`. It writes
`GoogleService-Info.plist` from the `GOOGLE_SERVICE_INFO_PLIST` repository secret (base64), so a
new Firebase project means updating that secret.

## Local build

`Kalorie/Kalorie/Resources/GoogleService-Info.plist` is required and is not in the repository.
Download it from the Firebase Console (Project settings → iOS app) and place it there.
