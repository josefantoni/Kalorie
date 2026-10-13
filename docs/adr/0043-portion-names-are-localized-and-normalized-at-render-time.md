# 0043. Portion names are localized and normalized at render time

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-10-13

## Context

A portion (`FoodPortionDomain`, `{name, grams}`) has a single free-text `name`.
[Design 0008](../design/0008-food-portions.md) lists a second, English name field as a non-goal,
and the name is shown exactly as it was typed.

In practice the names come from a small vocabulary, written in whichever language the writer's
device used:

- the quick-add chips in `PortionInputRow` write `foodPortion_quickAdd_*` in the device language
  (`1 kus` / `1 piece`, `1 balení` / `1 package`, `1 lžíce` / `1 spoon`);
- the nutrition label parser proposes names such as `1 balení`;
- catalogue portions were typed by hand.

A read of every portion in Firestore on 2026-10-13 (catalogue, favourites, personal portions,
created meals; submissions were empty) found 17 distinct names: `1 porce`, `Lžíce`, `Lžička`,
`1 vanička`, `1 balení`, `1 kus`, `Kus`, `1 vejce`, `Vejce S`, `Vejce M`, `Vejce L`, `Vejce XL`,
`1 sklenice`, `1 plechovka`, `Poloviční porce`, `1 piece`, `1 spoon`. An English-language user
sees Czech portions, and a Czech user sees the English ones. Capitalisation is inconsistent too:
`1 kus` next to `Kus`, `1 lžíce` next to `Lžíce`.

## Decision

A portion's name is passed through one shared function whenever it is **displayed**:

```kotlin
// TextKit, antoni.kalorie.textkit
fun localizedPortionName(name: String, czech: Boolean): String
```

`czech` is the same language test as [ADR 0036](0036-food-display-name-is-chosen-by-device-language-at-render-time.md):
the device language is `cs` or `sk`.

1. **Matching key:** trim the name, collapse whitespace runs to one space, lowercase,
   `foldDiacritics`, then drop an optional leading `"1 "`. If the remaining key ends in a size
   token ` s`, ` m`, ` l` or ` xl` and the part before it is a known entry, the token is kept
   aside and is appended back in uppercase (`Vejce L` → `1 vejce L` / `1 egg L`).
2. **Known entry:** if the key matches any entry's `match` list, return that entry's `cs` or `en`
   form (plus the size token).
3. **Unknown name:** return it trimmed, with whitespace runs collapsed and the first character
   uppercased. Names that start with a digit do not change.

| `cs` | `en` | `match` (folded, without leading `1 `) |
|---|---|---|
| `1 porce` | `1 serving` | `porce`, `serving`, `portion` |
| `½ porce` | `½ serving` | `½ porce`, `½ serving`, `polovicni porce`, `half serving`, `half portion` |
| `1 lžíce` | `1 tbsp` | `lzice`, `tbsp`, `tablespoon`, `spoon` |
| `1 lžička` | `1 tsp` | `lzicka`, `tsp`, `teaspoon` |
| `1 kus` | `1 piece` | `kus`, `ks`, `piece` |
| `1 balení` | `1 package` | `baleni`, `package`, `pack` |
| `1 vanička` | `1 tub` | `vanicka`, `tub` |
| `1 vejce` | `1 egg` | `vejce`, `egg` |
| `1 sklenice` | `1 glass` | `sklenice`, `glass` |
| `1 plechovka` | `1 can` | `plechovka`, `can` |

The canonical form is always "count + unit", lowercase after the count. That is what removes the
mixed capitalisation, so known names do not need a stored-data fix to look consistent.

As with ADR 0036, the result is **never persisted**. Firestore keeps whatever was typed, edit
fields show the raw stored name, and a device that switches language changes only what it
displays.

The English quick-add `foodPortion_quickAdd_spoon` changes from `1 spoon` to `1 tbsp`, so that it
means the same as the Czech `1 lžíce`.

## Consequences

- This reverses one premise of design 0008, that the name is shown as its author typed it. The
  0008 non-goal still holds: there is no second name field and no schema change.
- The table is a closed list. A name outside it (`2 kusy`, `1 plátek`) is shown in the language it
  was written in, only normalized. Adding a word means adding a row and a fixture case.
- Czech plural forms are not handled. Only "1 …" and count-less names are recognised.
- Two portions that differ only in spelling (`Kus` and `1 kus`) now render identically in the
  picker, which is the same harmless duplicate design 0008 already accepts.
- Before release, on 2026-10-13, the stored names listed above were rewritten once to their
  canonical `cs` form (13 documents in the catalogue, favourites and personal portions; grams were not
  changed). The data therefore holds a single spelling. The render-time rule is still needed
  for the other language and for names typed in the future.
- The rule lives in `TextKit`, so both clients share it and the shared fixture tests it.
