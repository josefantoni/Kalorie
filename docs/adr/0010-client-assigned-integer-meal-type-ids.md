# 0010. Meal type IDs are integers assigned by the client

- **Status:** Accepted
- **Scope:** Backend, Cross-platform
- **Date:** 2026-08-27

## Context

`MealTypeDTO.id` is an `Int`, and the document ID under `users/{userId}/mealTypes` is that
integer rendered as a string. Two places assign it:

- `SetupDefaultMealsUseCase` writes the five default meals with the ids `0…4`, taken straight
  from `enumerated()`.
- `CreateMealTypeUseCase` computes `(existingMealTypes.map(\.id).max() ?? -1) + 1`.

`MealTypeDTO` is also the one DTO with no `CodingKeys`: its fields persist as `id`, `name`,
`startMinutes`, `endMinutes` — camelCase, unlike every other collection.

**The reason for both cannot be reconstructed.** Nothing in the code or the history explains
why meal types were given a different identity scheme from `foodConsumed` and `myCreatedMeals`,
which use UUIDs, and nothing depends on the ids being small, dense, or ordered — the app sorts
meal types by `startTime`, never by id. The most plausible explanation is that meal types were
the first per-user collection written and the convention had not settled yet.

This ADR records the decision as it stands, together with what depends on it, so that it can be
changed deliberately rather than by accident.

## Decision

Meal type identity is a per-user integer, assigned client-side as *one greater than the highest
existing id*, and used as the document ID in string form. Field names in this collection stay
camelCase.

## Consequences

- The scheme is per-user, so ids are only unique within one user's subcollection. Nothing may
  treat a meal type id as globally meaningful.
- **The assignment is not safe against concurrent writers.** Two devices that create a meal
  type from the same set of existing types compute the same `newId`, and `setAsync` overwrites
  rather than failing — the second write silently replaces the first. See finding **A1-1** in
  `TODO.md`. UUIDs would not have this problem; this is the main cost of the decision.
- A second client **must** replicate both the arithmetic and the camelCase field names, or the
  two clients will not read each other's meal types. This is the only collection where the
  field-naming convention differs, and it is easy to get wrong by following the pattern of the
  others.
- Reassigning identity to UUIDs is a contained change — it touches
  `SetupDefaultMealsUseCase`, `CreateMealTypeUseCase`, `DeleteMealTypeUseCase`,
  `UpdateMealTypeTimesUseCase` and `DeleteAccountUseCase` — but requires migrating existing
  documents, since the ID is the document key.
- **This decision is safe to revisit.** It was inherited rather than argued for, and no
  requirement rests on it.
