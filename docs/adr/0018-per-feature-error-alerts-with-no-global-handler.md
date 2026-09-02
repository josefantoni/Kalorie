# 0018. Errors are presented per feature as a dismissible alert, with no global handler

- **Status:** Superseded by [ADR 0020](0020-alertitem-carries-an-optional-message.md) (the
  "`AlertItem` carries no message and no actions" bullet in Consequences only — the rest of this
  record is unchanged)
- **Scope:** iOS
- **Date:** 2026-08-27

## Context

Every screen that can fail owns its own failure presentation. A view model holds
`@Published var alertItem: AlertItem?`, a `catch` maps the error to an `L10n` string, and the
view attaches `.alert(item:)`. `AlertItem` is deliberately minimal — an `id` and a `title` —
so there is nothing to configure and nothing to route.

The alternative would have been an app-wide error subject that every view model publishes into
and the root view renders. For an app this size that means a single point every feature has to
agree with, and a presentation layer that no longer knows which screen the error came from or
what the user was trying to do.

There is one exception, and it is deliberate: the root `KalorieApp` renders
`LoadingState.error` from `AuthStateObserver` as a full-screen state with a **Retry** button.
Authentication failing is not an interruption of a task, it is the app having no usable state
at all — there is no screen underneath to dismiss the alert back to.

## Decision

Each feature catches its own errors and presents them as a single-line alert with an OK button.
There is no global error handler, no error bus, and no shared retry affordance. The root
authentication failure is the only screen-level error state.

## Consequences

- Error text is written where the failing operation is understood, so a use case's typed error
  cases map onto specific messages — `CreateFoodItemError` and `CreateMealTypeError` are both
  switched over exhaustively at their call sites.
- **Nothing is retried.** An alert is dismissed and the user re-attempts the action manually;
  no view model distinguishes a transient network failure from a permanent one. That is
  acceptable for a write the user just initiated and much less so for a background load.
- `AlertItem` carries no message and no actions, so an error that needs the user to *do*
  something cannot say so in the alert — `errorDeleteRequiresRecentLogin` is a message that
  asks for a re-login and offers no way to start one (finding **A5-4** in `TODO.md`).
- Where a use case's errors are not typed, the `catch` collapses to `L10n.Common.errorUnknown`;
  `DashboardViewModel` alone does this six times (finding **A5-5**). The pattern makes this
  easy to reach for and offers nothing better.
- Because the mapping happens at the presentation boundary, the underlying error is discarded
  there — and with no crash reporter or logger in the project, it is discarded for good
  (findings **A5-1**, **A5-2**). Adopting the convention does not require that; keeping a
  logging call in the `catch` would fit it fine.
