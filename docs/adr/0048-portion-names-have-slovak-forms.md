# 0048. Portion names have Slovak forms and follow the app's resolved language

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-10-31

## Context

[ADR 0043](0043-portion-names-are-localized-and-normalized-at-render-time.md) gave
`localizedPortionName` a `czech: Boolean`, and [design 0027](../design/0027-slovak-localisation.md)
listed Slovak portion names as a non-goal: a Slovak device took the Czech branch. With the Slovak
UI shipped, `1 lžíce` next to Slovak text reads as a mistake.

## Decision

1. `localizedPortionName(name, languageCode)` in TextKit replaces `czech: Boolean`. `cs` and `sk`
   get their own form of every vocabulary entry (`1 porce` / `1 porcia`, `1 lžíce` / `1 lyžica`,
   `1 sklenice` / `1 pohár`, …); any other language gets English. The code is lowercased and cut at
   the first `-` or `_`, so `sk-SK` and `SK` both mean `sk`.
2. Slovak spellings are recognised as input, so a typed `Lyžica` normalises to `1 lyžica`. The
   match key folds the Slovak letters `ä ô ľ ĺ ŕ` on top of `foldDiacritics`, in the portion key
   only; search folding is unchanged.
3. A Slovak word that is also a different Czech word is **not** a match key. `pohár` is a Slovak
   glass but a Czech goblet or sundae cup, so a stored `Pohár` stays as typed in every language;
   only the output form for `sk` is `1 pohár`.
4. The language passed in is the one the app shows its strings in, not the first device
   language: iOS passes `Locale.current`, Android `Locale.getDefault()`. Both are already
   resolved by the system against the app's resources, so a device list such as `[de, sk]`
   gives the name in the same language as the UI around it.
5. `TextKit/fixtures/text-kit-cases.json` carries a `language` field instead of `czech` and pins
   points 1–3 for both clients.

## Consequences

- This reverses the Slovak portion-name non-goal of design 0027 and the `czech` parameter of
  ADR 0043; the rest of ADR 0043 stands.
- A new Slovak vocabulary word must be checked against Czech before it becomes a match key.
