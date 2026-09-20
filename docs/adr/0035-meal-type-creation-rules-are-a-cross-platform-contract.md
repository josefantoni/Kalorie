# 0035. Meal-type creation rules are a cross-platform contract

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-09-20

## Context

`CreateMealTypeUseCase` validated a new meal type's name with `!name.isEmpty` and checked
uniqueness with `$0.name == name`. Both were accidents of how the check was first written, and
neither was ever written down as a rule:

- A name of only spaces passed, and was stored and displayed as a blank section header.
- "Snídaně" and "snídaně", or "Snídaně" and "Snídaně " with a trailing space, counted as two
  different meals.

The rules read `mealTypes` per user, so an account is edited from every device that signs in to
it. A second client that trims or case-folds would reject names the iOS client accepted, and one
that does not would accept names iOS rejects — the same account's meal layout editable on one
device and not on the other.

The 30-minute minimum length of a window had the same problem. `MealKit` deliberately takes
`minimumDurationMinutes` as a parameter rather than owning it, and `30` was a bare literal at the
single iOS call site — invisible to any other client.

## Decision

A meal type's name and window are validated as follows, by every client:

1. **The name is trimmed** of leading and trailing whitespace and newlines before anything else,
   and the trimmed value is what is stored.
2. **An empty trimmed name is rejected.**
3. **Uniqueness compares trimmed names case-insensitively**, against the trimmed name of every
   existing meal type — including ones stored before this record, which may carry padding. Case
   folding is the platform's locale-independent lowercase (`lowercased()` on iOS, `lowercase()`
   with the root locale on the JVM), not a locale-aware one.
4. **A window is at least 30 minutes long**, counting both sides of a window that wraps midnight.
   The value is `MIN_MEAL_WINDOW_MINUTES` in `MealKit`, and it is the default of
   `isMealWindowLongEnough`'s `minimumDurationMinutes`. A client that has `MealKit` reads it from
   there; iOS passes the constant explicitly, because Kotlin default arguments are not exported to
   Swift.
5. **A window does not overlap an existing one**, per `mealWindowsOverlap`.

Names already in Firestore are not migrated. Rule 3 is applied to them when compared, not by
rewriting them.

## Consequences

- A whitespace-only or padded name can no longer be created from iOS. Existing padded names keep
  displaying as stored.
- Two meal types cannot differ only by case or padding, so a second client is never asked to
  render or disambiguate lookalikes.
- Changing the minimum is a change to `MealKit` and therefore to every client at once. That is
  the intent: it is a shared rule, not an iOS preference.
- The validation still runs against the `existingMealTypes` the caller passes in, i.e. client
  state, never the server. Two devices creating the same name from the same stale snapshot can
  still both succeed; nothing here closes that race.
- This record does not cover editing a window's times, which has no direct path (ARCHITECTURE § 3.4).
