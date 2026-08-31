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

Rules are versioned in `Kalorie/firestore.rules` but **the repository is not the source of
truth for what is live** — they have to be deployed explicitly:

```sh
cd Kalorie
firebase deploy --only firestore:rules
```

Test before deploying, either in the Rules Playground in the console or against the emulator:

```sh
firebase emulators:start
```

`rules_version = '2'` is default-deny: a collection with no matching rule block is inaccessible,
and the failure only shows up at runtime. When adding a collection to `Constants.Firestore`, add
a matching rule block in the same change.

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
