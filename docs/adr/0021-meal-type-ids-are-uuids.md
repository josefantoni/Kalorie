# 0021. Meal type ids are UUIDs, assigned client-side at creation

- **Status:** Accepted
- **Scope:** Backend, Cross-platform
- **Date:** 2026-09-02

## Context

[ADR 0010](0010-client-assigned-integer-meal-type-ids.md) assigned meal type identity as a
per-user integer, computed client-side as `max(existing.id) + 1`, and recorded the decision as
inherited rather than argued for. Its own Consequences section named the cost: two devices
creating a meal type from the same starting state compute the same id, and `setAsync` overwrites
rather than fails, so the second write silently destroys the first (finding **A1-1** in
`TODO.md`). The same record noted the decision was "safe to revisit" — nothing depends on ids
being small, dense, or ordered, since the app sorts meal types by `startTime`, never by id — and
listed reassigning to UUIDs as a contained change touching exactly five use cases.

The app has no production users yet, so there is no existing data to migrate.

## Decision

Meal type identity moves from a client-computed `Int` to a client-generated `UUID().uuidString`,
used directly as the Firestore document id — the same scheme `foodConsumed` and
`myCreatedMeals` already use. `SetupDefaultMealsUseCase`, `CreateMealTypeUseCase`,
`DeleteMealTypeUseCase`, `UpdateMealTypeTimesUseCase`, `DeleteAccountUseCase`, `MealTypeDTO` and
`MealTypeDomain` all move to the `String` id. ADR 0010's other decision — camelCase field names
for this one collection, and identity scoped per-user rather than global — is unchanged.

## Consequences

- Two devices creating a meal type concurrently now get distinct ids, so `setAsync` can no
  longer silently overwrite one device's meal type with another's. Closes **A1-1**.
- Meal type ids are no longer sequential or human-legible. Nothing read them that way.
- This supersedes ADR 0010's Decision and its "not safe against concurrent writers" Consequence
  bullet; the rest of that record — camelCase fields, per-user identity scope — still holds.
