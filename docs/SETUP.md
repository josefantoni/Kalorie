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
| Anonymous provider | Authentication → Sign-in method → Anonymous | Already enabled; the app depends on it for the signed-out mode (see [ADR 0001](adr/0001-anonymous-firebase-auth-as-device-identity.md)). |

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

## CI

`.github/workflows` runs `xcodebuild test` on pull requests against `main`. It writes
`GoogleService-Info.plist` from the `GOOGLE_SERVICE_INFO_PLIST` repository secret (base64), so a
new Firebase project means updating that secret.

## Local build

`Kalorie/Kalorie/Resources/GoogleService-Info.plist` is required and is not in the repository.
Download it from the Firebase Console (Project settings → iOS app) and place it there.
