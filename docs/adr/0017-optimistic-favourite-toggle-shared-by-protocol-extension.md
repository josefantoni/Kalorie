# 0017. Favourite toggling is an optimistic protocol extension, not a use case

- **Status:** Accepted
- **Scope:** iOS
- **Date:** 2026-08-27

## Context

Two screens toggle a favourite: `FoodQuantityViewModel`, before a food is logged, and
`FoodConsumedDetailViewModel`, afterwards from the Dashboard. Both need the same four things —
guard against a double tap, flip the flag immediately, call the right one of two use cases, and
put the flag back if the write fails.

The persistence itself is already split across `AddFavouriteFoodUseCase` and
`RemoveFavouriteFoodUseCase` — two operations, two use cases, no mode flag. What was left over
was the *UI* behaviour, which is not a use case: it mutates `@Published` state and shows an
alert.

`FavouriteToggling` is a `protocol … : AnyObject` requiring `isFavourite`,
`isTogglingFavourite` and `alertItem`, with the whole toggle written once in an extension.
Conforming view models get it for free and expose a one-line `onFavouriteToggled()`.

## Decision

Shared favourite-toggle behaviour lives in a `@MainActor` protocol extension over the three
`@Published` properties it needs, and the two persistence calls are injected as arguments
rather than stored.

## Consequences

- A third screen that needs the toggle declares conformance and implements nothing.
- The optimistic flip is what makes the button feel instant, and the `catch` that restores the
  previous value is what keeps it honest. Both halves are load-bearing; removing the rollback
  leaves the UI claiming a favourite that was never written.
- `item` is optional and `nil` is an error only when *adding* — removal needs an id, not an
  item. That asymmetry exists because the Dashboard cannot always resolve the catalogue item
  behind an entry; see finding **A4-5** in `TODO.md`.
- The extension mutates the conforming object's state directly, so it is untestable in
  isolation and is covered through the two view models instead. Acceptable for behaviour this
  small; the moment it needs branching, it should become a real type.
- Note the direction this abstracts in. [ADR 0005](0005-no-shared-sign-in-provider-abstraction.md)
  argues that shared logic should be pushed *down* toward a common seam rather than *up* into a
  protocol over sibling callers. This record goes the other way, and is only defensible because
  what it shares is presentation state that has no layer below it to sink into. It is not a
  precedent for unifying use cases.
