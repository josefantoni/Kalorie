# 0019. Localized strings are reached through a hand-written `L10n` enum

- **Status:** Accepted
- **Scope:** iOS
- **Date:** 2026-08-27

## Context

Strings live in a single `Localizable.xcstrings` catalogue with `cs` as the source language and
`en` alongside it. No call site references a catalogue key directly. Instead
`Resources/Localization/L10n.swift` declares a nested enum — `L10n.Dashboard.navigationTitle`,
`L10n.AddFood.errorInvalidCode` — where each member is a computed property wrapping
`String(localized: "…")`, and the nesting mirrors the key prefixes
(`dashboard_`, `addFood_`, `mealTypeSheet_`).

The file is written and maintained by hand. A code generator (SwiftGen, or Xcode's own
generated symbols) would produce something similar, at the cost of a build-time dependency and
a generated file in the repository.

The properties are `var`, not `let`, on purpose: a `static let` would resolve the string once
per process and keep it after an in-app language change.

## Decision

All user-facing strings are reached through `L10n`. Call sites never contain a raw catalogue
key and never contain a raw literal. `L10n` is maintained by hand, grouped by feature, with one
computed property per key and a function where the string takes an argument.

## Consequences

- Renaming a key is a compiler-checked change at every call site, and the grouping makes the
  catalogue's feature prefixes discoverable from code.
- **A key can be added to the catalogue without a matching `L10n` member, and vice versa;
  nothing checks.** The catalogue currently holds 133 entries against 121 `String(localized:)`
  calls — the difference is junk harvested from `Text` literals (finding **A5-7**).
- SwiftUI's `Text("…")`, `Button("…")` and `accessibilityLabel("…")` take a
  `LocalizedStringKey`, so **any literal passed to them silently becomes a catalogue key**.
  That is how `"g"`, `"kcal"` and two interpolated format strings ended up in the catalogue.
  A literal that is genuinely not translatable belongs in `Text(verbatim:)`, which the project
  does not use anywhere today.
- Numbers and units formatted with `String(format:)` bypass both `L10n` and the catalogue
  entirely, so they are neither translatable nor locale-aware (finding **A5-6**).
