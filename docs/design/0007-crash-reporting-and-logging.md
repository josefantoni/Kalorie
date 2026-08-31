# Design: Crash reporting and structured logging

- **Status:** Implemented
- **Scope:** iOS
- **Date:** 2026-08-30

## Context and scope

There is no logging, crash reporting or analytics in the project. The only diagnostic output is
`print` inside `FirestoreDataProvider.log(_:)`, guarded by `#if DEBUG`, so it never runs in a
release build. In a release build a Firestore permission denial, a decoding failure or a network
timeout is caught, mapped to `L10n.Common.errorUnknown` (or silently dropped by `try?`), and then
gone — nobody, including the app maintainer, ever learns it happened (findings **A5-1**, **A5-2**
in `TODO.md`; [ARCHITECTURE.md](../ARCHITECTURE.md) § 5.2).

Firebase is already an SPM dependency (`FirebaseFirestore`, `FirebaseAuth`), and
`GoogleService-Info.plist` already exists for this project — so this is additive to an existing
vendor relationship, not a new one.

[ADR 0018](../adr/0018-per-feature-error-alerts-with-no-global-handler.md) already anticipates
this: it explains why there is no global error handler or error bus, and separately notes that
"keeping a logging call in the `catch` would fit it fine." This doc does not reopen that ADR — it
proposes what the logging call is and where it goes, without changing how errors are presented
to the user.

This doc stops at the pattern and the infrastructure. It does **not** decide, call site by call
site, what each of A5-2's thirteen `try?`/`catch` sites should log — that is the implementation
pass this doc unblocks, not a decision to make up front.

## Goals

- An error that reaches a `catch` in a release build leaves a trace a developer can read
  afterward, without the user having to report it.
- A crash is symbolicated and visible in the Firebase console, not just a silent relaunch.
- One small, consistent call site API (`Log.error(...)` / `Log.warning(...)` or equivalent) that
  every feature calls the same way, instead of hand-rolled `print` / SDK calls scattered per file.
- No change to `AlertItem` / `alertItem` / per-feature dismiss-alert presentation — logging is
  additive at the existing `catch`, not a new user-facing layer.

## Non-goals

- Deciding what each of A5-2's individual call sites should log, or which of the thirteen
  `try?` sites are legitimate silent-optional-enrichment (several are, per A5-2's own text) versus
  bugs. That is the next PR, informed by this one — the *classification* it turns on is decided
  here (see "Two severities" below), the per-site verdict is not.
- Analytics / usage tracking. `GoogleService-Info.plist` currently has
  `IS_ANALYTICS_ENABLED = false`; turning it on is a separate, deliberate product decision, not a
  side effect of adding crash reporting.
- Fixing **A5-3**, **A5-4**, **A5-5** (error-presentation quality — retry affordances, richer
  `AlertItem`, distinguishing "offline" from "unknown"). Related, but this doc only adds a trace;
  it does not change what the user sees.
- Alerting or paging on crash-free-rate thresholds. The Firebase console's default dashboards are
  enough for a project this size; revisit if that stops being true.

## Design

Two complementary mechanisms, doing different jobs:

1. **Firebase Crashlytics** — fatal crash reporting plus non-fatal error recording
   (`Crashlytics.crashlytics().record(error:)`). This is what makes A5-1's "nowhere to report an
   error to" actually false: a release-build user's crash or caught error becomes visible to the
   maintainer without the user doing anything.
2. **`os.Logger`** — structured, categorized local logging, replacing
   `FirestoreDataProvider.log(_:)`'s `#if DEBUG print`. `os.Logger` is Apple-native (no new
   dependency), viewable in Console.app / a sysdiagnose, and — unlike `print` — cheap enough by
   design to leave enabled in Release. The `#if DEBUG` gate can therefore come off the *failure*
   logging; it stays on the parts of `log(_:)` that dump document bodies, for privacy rather than
   cost reasons (see the wiring-point section).

### Two severities, not one

Not every discarded error deserves a remote non-fatal. `AddFoodSheetViewModel`'s
`try? fetchFavouriteFoods()` / `try? fetchMyCreatedMeals()` on appear are legitimate silent
enrichment — recording those would mean every user who opens the sheet on a bad connection
generates a non-fatal, and the Crashlytics console becomes a network-quality dashboard nobody
reads. Losing the user's display name or failing to delete their profile document is a different
category of event.

So the helper has two entry points, and choosing between them *is* the per-call-site judgement
the next PR makes:

- `Log.warning` — local only. Something failed, the app carried on, and carrying on was the
  intended behaviour.
- `Log.error` — local **and** a Crashlytics non-fatal. Something was lost that the user or the
  maintainer would care about.

Wrap both behind one small helper (e.g. `Core/Utils/Log.swift`):

```swift
enum Log {
    static func warning(_ error: Error, category: String = "app") {
        logger(category).warning("\(String(describing: error), privacy: .public)")
    }

    static func error(_ error: Error, category: String = "app") {
        logger(category).error("\(String(describing: error), privacy: .public)")
        Crashlytics.crashlytics().record(error: error)
    }

    private static func logger(_ category: String) -> Logger {
        Logger(subsystem: "antoni.Kalorie", category: category)
    }
}
```

Three details in that snippet are deliberate and easy to get wrong:

- **`String(describing: error)`, not `error.localizedDescription`.** For a Swift `Error` enum with
  no `LocalizedError` conformance — which is all of them here: `CreateFoodItemError`,
  `CreateMealTypeError`, `DeleteAccountError`, and nothing in the app conforms to `LocalizedError`
  at all — `localizedDescription` is the useless generic "The operation couldn't be completed.
  (Kalorie.CreateFoodItemError error 0.)".
- **`String(describing:)` rather than interpolating the error directly.** `os.Logger`'s
  interpolation with a `privacy:` argument needs a `CustomStringConvertible` (or one of the
  primitive overloads); bare `Error` is neither, so `\(error, privacy: .public)` does not compile.
- **`privacy: .public`.** `os.Logger` treats interpolated non-static values as private and
  redacts them to `<private>` when the log is read back from a device that isn't the developer's.
  Without it the local half of this design silently produces nothing readable. It is safe *here*
  because the value is an error, not user data — see the `FirestoreDataProvider` note below,
  where the opposite applies.

Open questions left to implementation:

- Whether `Logger` categories mirror `Features/` folder names (one per feature) or something
  coarser.
- Whether `Log` also gets a non-error `.debug`/`.info` entry point, or stays at
  warning/error for now — the stated goal is "an error leaves a trace," not general-purpose
  logging.

### Wiring point: where the error ends

The rule is **log where the error stops travelling**, which is not one layer:

- **ViewModel `catch`** — the common case, and the one ADR 0018 describes: "the mapping happens
  at the presentation boundary, the underlying error is discarded there." `Log.error(...)` goes
  alongside the existing `alertItem = AlertItem(...)` assignment.
- **The `try?` itself** — wherever a `try?` swallows the error, no `catch` further up ever sees
  it, so a ViewModel-only rule cannot reach it. Several of A5-2's sites are exactly this, and
  they are in the UseCase layer or below: `DeleteAccountUseCase.swift:71` (profile document),
  `SignInWithAppleUseCase.swift:66,71` and `SignInWithGoogleUseCase.swift:53,59` (display name,
  profile write), `AuthStateObserver.swift:81` (pending-merge resume — not a ViewModel at all).
  These need `do/catch` with a `Log.error` / `Log.warning` in the `catch`, in place, at the
  layer where the `try?` is today.

This does not put error *presentation* in the UseCase layer and so does not reopen ADR 0018 —
nothing propagates further than it does today, and no `AlertItem` moves. The UseCase keeps
swallowing the error on purpose; it just stops doing so invisibly.

`FirestoreDataProvider.log(_:)` is a separate, lower-level change, and it is **not** a straight
swap of `print` for `os.Logger` with the `#if DEBUG` removed. That helper has 36 call sites, and
the successful ones dump whole documents (`$0.data()` per document on every read, `body: \(data)`
on POST/SET/BATCH) while the collection paths carry the user's uid. Leaving that on in Release
would put the user's entire food diary in the device's unified log. So:

- The `❌` failure lines use `Logger.error` **directly, not `Log.error`** — the provider rethrows
  every error it logs, so it is not where the error stops. Calling `record(error:)` here would
  file a second non-fatal for the same failure the ViewModel is about to record. Local trace at
  the origin, remote record at the terminus.
- The `🚀` / `✅` lines and the per-document dumps stay behind `#if DEBUG`, or move to
  `Logger.debug` **without** `privacy: .public` — which is the default, and here the default is
  the right one.

## Alternatives considered

- **Sentry or another third-party crash reporter** — rejected. Firebase is already a configured
  dependency for this exact project (`GoogleService-Info.plist` already exists); a second vendor
  for the same job adds cost with no offsetting benefit here.
- **`os.Logger` only, no Crashlytics** — rejected. A real user's device logs are not retrievable
  after the fact. Without a remote sink, A5-1 stays true for anyone except a developer with the
  device in hand; local logging alone does not close the finding.
- **A global error handler / error bus that all ViewModels publish into** — rejected, and out of
  scope. This doc does not reopen ADR 0018; logging is additive at the point each error is
  already discarded, not a new presentation layer.

## Cross-cutting concerns

- **Xcode project** — add the `FirebaseCrashlytics` SPM product to the app target. It resolves
  through the `firebase-ios-sdk` package reference already used for `FirebaseFirestore` /
  `FirebaseAuth` (`project.pbxproj`); no new package, just a new product.
- **New Run Script build phase** — Crashlytics needs a dSYM upload step to symbolicate crashes
  (the standard Firebase SPM script, run against
  `${DWARF_DSYM_FOLDER_PATH}/${DWARF_DSYM_FILE_NAME}`). Two settings it depends on are already
  correct and need no change: **Debug Information Format** is `dwarf-with-dsym` on the Release
  configuration (`project.pbxproj:523`; Debug is plain `dwarf`, and the app target does not
  override either), and **`ENABLE_USER_SCRIPT_SANDBOXING`** — which would otherwise break the
  upload script — is `NO` on the app target in both configurations (`project.pbxproj:559,603`),
  even though the project level says `YES`. The build phase itself is the only new piece; the
  project already carries four other Run Script phases (SwiftLint, three KMP framework builds).
- **CI** — `.github/workflows` currently only runs `xcodebuild test`; it does not archive a
  release build or upload dSYMs. Wherever release archives actually happen (Xcode Organizer,
  Fastlane, Xcode Cloud — not discoverable from this repo) needs the dSYM upload step added; this
  doc cannot name that step because the archive pipeline isn't in the repo.
- **`docs/SETUP.md`** — needs a note once this ships. Crashlytics itself needs no Firebase
  Console toggle (it activates on first symbol/crash upload, unlike the Auth providers table
  already in `SETUP.md`), but that's worth confirming in the console after the first build that
  ships it.
- **Privacy** — Crashlytics collects device/OS identifiers and stack traces. The Firebase SDK
  ships its own privacy manifest for its required-reason API usage, but whether this changes the
  App Store Connect "App Privacy" answers (Data Used to Track You / Diagnostics) is a product
  question to confirm before submitting the release that ships it, not an engineering one this
  doc can settle.
- **Opt-out** — Crashlytics collection can be disabled by default via the
  `FirebaseCrashlyticsCollectionEnabled` Info.plist key and toggled at runtime with
  `setCrashlyticsCollectionEnabled(_:)`. Whether the app needs a user-facing switch is the same
  product/legal question as above, but the mechanism should be wired even if the switch is not
  exposed — a privacy policy that promises an opt-out with no code behind it is worse than no
  promise. Turning collection off in `DEBUG` also keeps development noise out of the console.
- **Device logs** — removing the `#if DEBUG` gate from `FirestoreDataProvider.log(_:)` wholesale
  would put user documents and uids into the device's unified log; see the wiring-point section
  for what stays gated and why `privacy: .public` is applied to errors only.
- **GDPR (Czech/EU users)** — crash and non-fatal data here is diagnostic, not analytics
  (`IS_ANALYTICS_ENABLED` stays `false`). Worth a line in the app's privacy policy if one exists;
  not a blocker to shipping.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| `Log.error` adopted inconsistently — some `catch` blocks keep bare `try?` | A5-1 looks fixed but stays half-true | Treat "wire A5-2's call sites" as the immediate follow-up PR, not a someday |
| dSYM upload misconfigured | Crashes arrive unsymbolicated in Crashlytics, silently defeating the point | Force one deliberate test crash after wiring, before calling this done. Note a non-fatal uploads on the *next* app launch, not immediately — a console that looks empty right after the test is expected, not a failure |
| Over-logging | Non-fatals become noise nobody reads, and the console stops being checked | The `warning`/`error` split above: only "something was lost" reaches Crashlytics. Silent enrichment (`fetchFavouriteFoods` / `fetchMyCreatedMeals` on appear) is local-only, or every user on a bad connection generates non-fatals |
| Collection left unconditionally on | No opt-out mechanism behind a privacy-policy promise; `DEBUG` runs pollute the console | Wire `FirebaseCrashlyticsCollectionEnabled` / `setCrashlyticsCollectionEnabled(_:)` when Crashlytics is added, before any user-facing switch is decided |
| `os.Logger` values redacted as `<private>` | The local half of the design produces nothing readable off-device, and the gap is invisible until someone actually needs a log | `privacy: .public` on error interpolations in `Log`; verify once by reading a Release build's log from a second machine |

## Outcome

`Core/Utils/Log.swift`, the `FirebaseCrashlytics` SPM product on the app target, the dSYM-upload
Run Script phase, and the `FirebaseCrashlyticsCollectionEnabled` opt-out mechanism (off in
`DEBUG`, on in Release) shipped as designed. `FirestoreDataProvider`'s `❌` lines now go through
`Logger.error` directly, ungated, with `privacy: .public` on the error only — the message itself
(collection paths built from `Constants.Firestore.*(userId:)` carry a real uid) stays at the
default `.private`; the `🚀`/`✅`/document-dump lines are unchanged, still behind `#if DEBUG`.
Wiring A5-2's individual call sites with `Log.warning`/`Log.error` remains the follow-up PR this
doc always deferred.
