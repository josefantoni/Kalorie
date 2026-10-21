# Design: Minimum supported version

- **Status:** Approved
- **Scope:** Backend, Cross-platform, iOS, Android
- **Date:** 2026-10-21

## Context and scope

A backend change that old clients cannot survive has no safe rollout today. The clients have no
way to learn that their version is too old, so an incompatible change shows up for them as bare
`permission-denied` errors. The first such change is Firebase App Check (see `TODO.md`): once it is
enforced, every build without the App Check SDK is refused by Storage and then Firestore. Rules
changes that tighten a document shape will cause the same problem later.

This document adds a minimum supported app version per platform, stored in Firestore. An app below
it shows one blocking screen that links to the store. It covers only releases that contain this
change. Every release so far (up to iOS 1.0 and Android 1.0) cannot be reached and keeps failing the
old way. That cost is accepted, see *Risks*.

## Goals

- The maintainer can raise the minimum version of either platform from the Firebase Console, with
  no client release and no rules deploy, and every gated release below it stops on its next launch
  or return to the foreground.
- The blocked screen has one action, opening the app's store page. Nothing else in the app is
  reachable from it.
- The gate never blocks on its own failure. A missing document, a missing field, a value that is not a
  string or does not parse lets the app run as if no minimum were set. A failed read keeps the
  previous answer.

## Non-goals

- **Soft "update available" prompts.** Only a hard minimum. A nag screen can be added later on
  the same document.
- **Reaching releases already shipped.** They do not contain the gate.
- **Telling builds of one version apart.** Only one build per version reaches the store. TestFlight,
  Play testing tracks and debug builds are not gated separately.
- **Play In-App Updates or any store SDK.** See *Alternatives considered*.
- **Per-version messages, release notes, or a maintenance mode.** One fixed, localised message.
- **Offline behaviour.** The app is online-only. The gate reads like any other screen and the
  existing failure handling applies.

## Design

### What is compared

The **marketing version**: iOS `CFBundleShortVersionString` (`MARKETING_VERSION`), Android
`BuildConfig.VERSION_NAME` (`versionName`). The build number is not used. On iOS it identifies a
build of one version and starts again at 1 for each new version, so 1.01 (1) is newer than 1.0 (4)
even though its build number is lower. Android's `versionCode` only grows, but comparing it there
and the version on iOS would mean two different rules for one field shape.

Versions are compared **component by component as integers**, with a missing component counting as
0: `1.0` = `1.0.0` < `1.0.1` < `1.01` = `1.1` < `1.2` < `1.10`. This is the same ordering the App
Store applies to `CFBundleShortVersionString`, so the gate and the store never disagree about which
release is newer. A side effect follows from it: `1.01` and `1.1` are the same version. A version
that contains anything other than digits and dots does not parse, and the gate is then off.

The comparison is the one piece of logic both platforms must implement identically. Its cases live
in `fixtures/app-version-comparison.json` (pairs and the expected order), and both ports' tests
read that file, as the other ported rules in `fixtures/` do (`Android/CLAUDE.md`). It is too
small for a KMP module.

### Document

```
config/app          one document, written by hand in the Firebase Console
  min_version_ios       string, optional — e.g. "1.0.1", compared with CFBundleShortVersionString
  min_version_android   string, optional — e.g. "1.0.1", compared with versionName
```

A field that is absent means no minimum for that platform. Each platform has its own field because
App Store review and the Play rollout finish at different times, and the two platforms' version
names need not match.

The collection name goes into `Constants.Firestore` on both platforms
(`iOS/Kalorie/Core/Utils/Constants.swift`, `Android/…/core/utils/Constants.kt`), next to the
existing paths.

### Rules

```
match /config/{docId} {
  allow read: if true;
  allow write: if false;
}
```

The read is public on purpose. The gate runs alongside anonymous sign-in
([ADR 0001](../adr/0001-anonymous-firebase-auth-as-device-identity.md)), not after it, so it must
not depend on `request.auth`. The document contains nothing private. Writes are only possible from
the console and the Admin SDK, which bypass rules. Test both rules in
`firestore-rules-tests/firestore.rules.test.js`: an unauthenticated read succeeds, and an
authenticated maintainer's write is denied.

### Client

Both platforms get the same pieces, named the same way (`Android/CLAUDE.md` port rules):

- `AppVersion`, a value type parsed from a string (failable), comparable by the rule above.
- `FetchMinimumVersionUseCase` reads `config/app` through the existing Firestore data provider and
  returns the current platform's field parsed as an optional `AppVersion`. A missing field, a
  field that is not a string (`AppConfigDTO` decodes it as absent) and a value that does not parse
  all return no minimum. Errors from the read are not caught here.
- `AppVersionGate`, an observable owned by the app root (iOS `KalorieApp`, Android
  `application/KalorieApp.kt`). It exposes `isUpdateRequired`, which starts as `false`. The gate
  runs the use case at launch and every time the app returns to the foreground (iOS `scenePhase ==
  .active`, Android `Lifecycle.Event.ON_START`). It sets `isUpdateRequired = current < minimum`
  when a minimum is present. On any thrown error it logs through `Log` and leaves the value
  unchanged. Both clients log a non-fatal the same way they do today.
- The current version is read once: iOS `Bundle.main.infoDictionary["CFBundleShortVersionString"]`,
  Android `BuildConfig.VERSION_NAME`. If it does not parse, the gate is off.
- While `isUpdateRequired` is `true`, the root renders `UpdateRequiredView` / `UpdateRequiredScreen`
  **instead of** the whole auth `switch` / `when`. That includes the loading, error and Dashboard
  states, and the merge overlay. The app does not wait for the gate before it shows content, so a
  slow read never delays launch. A block that arrives later replaces whatever is on screen.
- `UpdateRequiredView` shows a title, one sentence and a button that opens the store page. iOS:
  `https://apps.apple.com/app/id<APP_STORE_ID>`. Android:
  `https://play.google.com/store/apps/details?id=antoni.kalorie`. Put both URLs in `Constants`. The
  App Store numeric id is not in the repo yet. Take it from App Store Connect → App Information →
  Apple ID.
- Strings go into `localisation/strings.json` and are generated by `scripts/generate-strings.js`
  for both platforms, Czech and English.

### Raising the minimum

This becomes a runbook entry in `docs/SETUP.md` (§ Firebase Console):

1. Wait until the new version is live on that platform's store, not just approved.
2. In the console, set `config/app.min_version_<platform>` to that version string, exactly as in
   `MARKETING_VERSION` / `versionName`.
3. Lowering the value or deleting the field undoes it on the next launch or foreground.

### Ordering with App Check

App Check is installed (not enforced) in the **same release** as this gate. One release then
carries both the attestation and the means to force later updates. Enforcement then follows the
App Check item in `TODO.md`, with one step added before it: raise `min_version_*` to that release.
Every gated client is then on an attested build. Only the pre-gate releases remain unverified, and
the console's unverified share measures how many there are.

## Alternatives considered

- **Comparing the build number.** One integer is simpler to compare, but on iOS the build number
  restarts with every version, so it does not order releases. Rejected.
- **Comparing version and build together.** It would tell builds of one version apart, which only
  matters for test tracks that are not gated anyway (*Non-goals*). Rejected as extra state.
- **Firebase Remote Config.** It is built for this, but it adds an SDK on both platforms plus
  fetch-interval and activation semantics, all to read two strings. Firestore is already in the
  app and already covered by rules tests. Rejected as an unneeded dependency.
- **Play In-App Updates (immediate flow).** Native on Android, but iOS has no equivalent, so iOS
  would still need this document. Update priority can be set only through the Play Developer API
  when the release is uploaded, not in the console, which adds a second tool for a one-person
  workflow. Rejected in favour of one mechanism on both platforms.
- **Blocking launch until the gate answers.** It guarantees that nothing below the minimum ever
  runs, but it adds a read to every cold start before any content appears, and it turns a read
  failure into a launch failure unless it fails open anyway. Rejected. A stale client that runs
  for a few hundred milliseconds only hits the same errors it would hit without the gate.
- **Checking in the rules (client sends its version in each write).** It would protect writes from
  old releases, but old releases do not send the field, so a required field would only reproduce
  the bare `permission-denied`. Reads are not covered at all. Rejected.

## Cross-cutting concerns

- **Security:** the gate is a convenience, not an access control. A patched client can ignore it,
  and App Check plus the rules stay the actual protection.
- **Privacy:** nothing user-related is read or written. `backend/hosting/privacy.html` needs no
  change.
- **Store review:** a forced-update screen is routinely accepted by both stores as long as it links to
  the store and does not block for a version that is not published yet (runbook step 1).
- **Cost:** one document read per launch and foreground per device. Negligible next to the
  Dashboard reads.

## Risks

- **A wrong value blocks everyone** (for example the next version instead of the current one) →
  every user stuck on the update screen with no update to install → runbook step 1, and the undo
  (step 3) applies on the next foreground with no release.
- **Pre-gate releases stay unreachable** → those users see errors once App Check is enforced →
  accepted. Enforcement waits until the console shows that their share is small enough.
- **The two ports order versions differently** → one platform blocks a release the other lets
  through → the shared fixture, read by both test suites.
- **A version name stops being digits and dots** (e.g. `1.1-beta`) → the gate is silently off on
  that platform → both version names are plain today. A unit test parses each platform's real
  current value.

## Outcome

To be filled in once shipped.
