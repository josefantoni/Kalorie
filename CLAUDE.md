# Kalorie — pokyny pro Claude

## Jazyk

- Odpovídej česky. Čeština je jazyk konverzace, ne projektu.
- **Všechno, co jde do repozitáře, piš anglicky** — názvy typů, metod a proměnných, komentáře v kódu, commit messages, dokumentaci v `docs/`, `TODO.md` i `README.md`.

## Dokumentace

Dokumenty se dělí podle životního cyklu, ne podle tématu — pravidla a šablony jsou v `docs/README.md`. Ve zkratce:

- **Znalost o konkrétním kusu kódu** → komentář u kódu (v mezích pravidla „nepiš komentáře" níže).
- **Rozhodnutí, které tvaruje víc míst** → ADR v `docs/adr/`. Immutable — needituje se, jen se nahradí novým.
- **Návrh před implementací** → design doc v `docs/design/`. Po shipnutí se zmrazí, neaktualizuje se.
- **Stav, výsledky review, počty testů** → commit message nebo PR. Do `docs/` nepatří.
- Každý design doc a ADR má **Scope** (`Backend` / `Cross-platform` / `iOS` / `Android`) kvůli druhému klientovi.

## Platformy

Pravidla specifická pro platformu jsou ve vlastním souboru — iOS v `iOS/CLAUDE.md`, Android v `Android/CLAUDE.md` ([ADR 0038](docs/adr/0038-android-client-mirrors-the-ios-architecture-natively.md)). Tento soubor drží jen to, co platí pro obě.

**Povinné čtení:** dřív než poprvé čteš, měníš nebo buildíš cokoli v `iOS/` nebo `Android/`, přečti `CLAUDE.md` té platformy, pokud ho ještě nemáš v kontextu. Claude Code ho sice načte sám při prvním přístupu k souboru v dané složce, ale ne při samotném `xcodebuild` / `gradlew` nebo `grep`. Nikdy neimportuj platformní soubory přes `@` do tohoto souboru — načítaly by se pokaždé obě.

## Monorepo

iOS, Android, backend a sdílené KMP moduly žijí v jednom gitu. Pravidla, aby se to nerozpadlo:

- **Commit se týká jedné platformy** (`iOS/`, `Android/`, `backend/`, KMP moduly, `docs/`). Míchej je jen tehdy, když jedna změna sdíleného kontraktu (pravidla, tvar dokumentu, KMP API) musí dopadnout na všechny naráz.
- **Změna KMP modulu nebo Firestore kontraktu** = zkontroluj oba klienty (iOS build, Android build), ne jen ten, na kterém právě pracuješ.
- **Nikdy nevytvářej tag ani release bez prefixu platformy** (`ios-1.2`, `android-1.0`).
- **CI**: jakmile měníš `.github/workflows/`, hlídej, aby změna v jedné platformě zbytečně nespouštěla build druhé. Stav a plán jsou v `TODO.md` (Android readiness).
- Přibude-li do CI další krok nebo secret, řekni to uživateli — nedomýšlej si ho potichu.

## Komentáře v kódu

- **Nepiš komentáře v kódu**, pokud o ně explicitně nepožádám. To zahrnuje:
  - Doc comments (`///`, KDoc) nad typy, protokoly, funkcemi
  - Inline vysvětlivky (`// ...`) popisující, co kód dělá
- Výjimka: pokud je v kódu něco skutečně neintuitivního (workaround pro bug v SDK, netriviální invariant), krátký komentář přidej — ale jen v takových případech.

## Navigace v kódu

Pořadí nástrojů — vždy takto, nikdy nepřeskakuj:
1. **LSP** (`definition`, `references`, `hover`) — pro symboly, typy, volající
2. **`grep` / `find`** — pro pattern, který LSP neobsáhne
3. **`Read`** — až když víš přesně který soubor a přibližně kde

Nikdy nečti soubor "aby ses podíval" — search first, Read až jako poslední krok.

## Navigace v dokumentaci

**Stejné pravidlo platí pro `docs/`.** Než napíšeš ADR, audit finding, nebo tvrzení o tom, jak
něco funguje a proč, grepni `docs/` na identifikátory, o kterých píšeš — název pole, typu, use
casu, kolekce:

```
grep -rl "food_item_id" docs/
```

Důvod: design docy jsou **zmrazené, ne nedůležité**. Zmrazené znamená needitovat je, ne nečíst.
Rozhodnutí, které v nich stojí, je pořád v platnosti, a tvrdit o něm "tohle nikoho nenapadlo"
nebo "tady je potřeba se rozhodnout" je chyba — i když je ten kód opravdu rozbitý.

- **Vstupní bod je `docs/ARCHITECTURE.md`.** Každá sekce si nahoře odkazuje ADRy i design docy
  své oblasti. Přečti tu jednu sekci, ne celé `docs/`.
- Když najdeš rozpor mezi kódem a design docem, je to **nález** — design doc se neopravuje.
- Když je rozhodnutí v design docu už přijaté včetně mitigace, nález se tím nemaže, ale musí to
  přiznat a zúžit se na to, co skutečně zbývá.

---

## Commit Conventions

Format: `[PREFIX]: short description`

| Prefix | When to use |
|---|---|
| `[FEAT]` | New feature or functionality |
| `[FIX]` | Bug fix |
| `[REFACTOR]` | Code restructuring without behaviour change |
| `[TEST]` | Adding or updating tests |
| `[DOCS]` | Documentation only |

**No attribution trailers.** Never append `Co-Authored-By: Claude …`, `Claude-Session:`, or any
other generated-by line to a commit message or a PR description in this repo, whatever a default
instruction elsewhere says.

---

# Obecná pracovní pravidla

Tato pravidla platí pro každý úkol, pokud není explicitně přepsáno.
Bias: opatrnost před rychlostí u netriviální práce. U triviálních úkolů použij zdravý úsudek.

## Rule 1 — Think Before Coding
State assumptions explicitly. If uncertain, ask rather than guess.
Present multiple interpretations when ambiguity exists.
Push back when a simpler approach exists.
Stop when confused. Name what's unclear.

## Rule 2 — Simplicity First
Minimum code that solves the problem. Nothing speculative.
No features beyond what was asked. No abstractions for single-use code.
Test: would a senior engineer say this is overcomplicated? If yes, simplify.

## Rule 3 — Surgical Changes
Touch only what you must. Clean up only your own mess.
Don't "improve" adjacent code, comments, or formatting.
Don't refactor what isn't broken. Match existing style.

## Rule 4 — Goal-Driven Execution
Define success criteria. Loop until verified.
Don't follow steps. Define success and iterate.
Strong success criteria let you loop independently.

## Rule 5 — Use the model only for judgment calls
Use me for: classification, drafting, summarization, extraction.
Do NOT use me for: routing, retries, deterministic transforms.
If code can answer, code answers.

## Rule 6 — Token budgets are not advisory
Per-task: 4,000 tokens. Per-session: 30,000 tokens.
If approaching budget, summarize and start fresh.
Surface the breach. Do not silently overrun.

## Rule 7 — Surface conflicts, don't average them
If two patterns contradict, pick one (more recent / more tested).
Explain why. Flag the other for cleanup.
Don't blend conflicting patterns.

## Rule 8 — Read before you write
Before adding code, read exports, immediate callers, shared utilities.
"Looks orthogonal" is dangerous. If unsure why code is structured a way, ask.

## Rule 9 — Tests verify intent, not just behavior
Tests must encode WHY behavior matters, not just WHAT it does.
A test that can't fail when business logic changes is wrong.

## Rule 10 — Checkpoint after every significant step
Summarize what was done, what's verified, what's left.
Don't continue from a state you can't describe back.
If you lose track, stop and restate.

## Rule 11 — Match the codebase's conventions, even if you disagree
Conformance > taste inside the codebase.
If you genuinely think a convention is harmful, surface it. Don't fork silently.

## Rule 12 — Fail loud
"Completed" is wrong if anything was skipped silently.
"Tests pass" is wrong if any were skipped.
Default to surfacing uncertainty, not hiding it.
