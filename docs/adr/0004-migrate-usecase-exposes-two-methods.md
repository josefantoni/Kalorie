# 0004. MigrateAnonymousDataUseCase exposes two methods instead of `callAsFunction`

- **Status:** Accepted
- **Scope:** iOS
- **Date:** 2026-08-07

## Context

Project convention is one use case, one `callAsFunction`, so a call site reads as natural
language.

Merging anonymous data has two genuinely different entry points:

- `migrate(...)` — the live path, when `link()` fails during sign-in.
- `resumeIfNeeded()` — crash recovery at launch, when a snapshot from a previous run is still
  on disk.

They differ in trigger, in arguments, and in when they run. What they share is a few lines of
"write the snapshot, then clean it up".

Two options fit the convention: split into two use case types, or keep one type with one
`callAsFunction` and a flag that selects the behaviour.

## Decision

Keep one type with two named methods, deviating from the convention.

## Consequences

- Call sites say what they mean: `resumeIfNeeded()` at launch, `migrate(...)` during sign-in. A
  boolean flag on a single entry point would hide that distinction.
- No second type exists solely to host a few shared lines.
- The convention is no longer uniform, so this record exists to stop the next reader from
  "fixing" it. Splitting the type later is fine — the reason for one type is the shared write
  path, and if that grows, the argument changes.
- Only apply this reasoning when the entry points genuinely differ in trigger and arguments.
  One use case with one `callAsFunction` remains the default.
