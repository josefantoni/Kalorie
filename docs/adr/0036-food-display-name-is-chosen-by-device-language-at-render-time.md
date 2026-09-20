# 0036. The name shown for a food is chosen by device language at render time

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-09-20

## Context

Every food — a catalogue item, a logged entry, a favourite, a created meal — carries two names,
`cz_name` and `eng_name`. Which one a user sees is not stored anywhere: it is decided each time a
name is rendered, by `BilingualNamed.displayName` in Swift.

That rule is behaviour a second client must match, and until now it existed only in Swift, where
the compiler and a single call site enforced it for free:

- [Design 0014](../design/0014-data-export.md) lists it in its own table of contracts an Android
  client must match — an export written by two clients must name the same food the same way.
- [Design 0006](../design/0006-own-daily-meals.md) depends on its fallback: a created meal stores
  `eng_name` as the empty string and relies on the fallback to show `cz_name`.
- ARCHITECTURE § 2.2 carries the reasoning behind its Czech-and-Slovak half: the catalogue is
  Czech-first and has no Slovak names, so a Slovak user is shown the Czech one.

The `isEmpty` fallback in particular is easy to miss when re-implementing from a description, and
missing it shows a created meal as a blank row.

## Decision

The displayed name is, in this order:

1. If the device's **language** is `cs` or `sk`, `cz_name`.
2. Otherwise `eng_name`, or `cz_name` when `eng_name` is the empty string.

"Language" is the primary language subtag alone, lowercase, of the language the platform resolves
for the app (`Locale.current.language.languageCode` on iOS) — never the region, so `cs-CZ`,
`cs-US` and `cs` are all `cs`. An unresolvable language falls into rule 2.

The choice is made **when rendering** and is never persisted: both names are always written, and
neither is dropped or overwritten to reflect the writer's language. A device switching its
language changes what it displays, not what is in Firestore.

## Consequences

- A created meal, whose `eng_name` is always empty, is shown by its `cz_name` in every language.
- A catalogue item with an English name is shown in English to every user outside `cs`/`sk`,
  including users in the Czech Republic whose device language is English.
- Search does not follow this rule: it queries both name fields regardless of display language
  (§ 2.2), so a user can find a food by either name.
- The rule lives in Swift, not in a shared module. Moving it into `TextKit` beside
  `foldDiacritics` would make it shared for free, but nothing does so today; until then a second
  client implements it from this record.
- Adding a third language means either a third name field on every DTO (a schema change across
  all collections) or leaving that language on rule 2 — this record does not choose.
