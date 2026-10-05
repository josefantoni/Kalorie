# Kalorie — instructions for Claude

## Language

- Reply in Czech. Czech is the language of the conversation, not of the project.
- **Everything that goes into the repository is written in English** — type, method and variable names, code comments, commit messages, documentation in `docs/`, `TODO.md` and `README.md`.

## Documentation

Documents are split by lifecycle, not by topic — the rules and templates are in `docs/README.md`. In short:

- **Knowledge about a specific piece of code** → a comment next to the code (within the "no comments" rule below).
- **A decision that shapes several places** → an ADR in `docs/adr/`. Immutable — it is not edited, only replaced by a new one.
- **A proposal before implementation** → a design doc in `docs/design/`. Frozen once shipped, not updated afterwards.
- **Status, review results, test counts** → commit message or PR. It does not belong in `docs/`.
- Every design doc and ADR has a **Scope** (`Backend` / `Cross-platform` / `iOS` / `Android`) because of the second client.

## Platforms

Platform-specific rules live in their own file — iOS in `iOS/CLAUDE.md`, Android in `Android/CLAUDE.md` ([ADR 0038](docs/adr/0038-android-client-mirrors-the-ios-architecture-natively.md)). This file holds only what applies to both.

**Mandatory reading:** before you first read, change or build anything in `iOS/` or `Android/`, read that platform's `CLAUDE.md` if you do not have it in context yet. Claude Code loads it on its own the first time it touches a file in that folder, but not on a bare `xcodebuild` / `gradlew` or `grep`. Never import platform files into this file via `@` — both would be loaded every time.

## Monorepo

iOS, Android, the backend and the shared KMP modules live in one git repository. Rules so it does not fall apart:

- **A commit concerns one platform** (`iOS/`, `Android/`, `backend/`, KMP modules, `docs/`). Mix them only when a single change to a shared contract (rules, document shape, KMP API) has to land on all of them at once.
- **A change to a KMP module or the Firestore contract** = check both clients (iOS build, Android build), not just the one you are working on.
- **Never create a tag or release without a platform prefix** (`ios-1.2`, `android-1.0`).
- **CI**: whenever you change `.github/workflows/`, make sure a change in one platform does not needlessly trigger the other's build. Status and plan are in `TODO.md` (Android readiness).
- If a new CI step or secret is needed, tell the user — do not quietly work it out yourself.

## Code comments

- **Do not write code comments** unless I explicitly ask for them. That covers:
  - Doc comments (`///`, KDoc) above types, protocols, functions
  - Inline explanations (`// ...`) describing what the code does
- Exception: if something in the code is genuinely non-obvious (a workaround for an SDK bug, a non-trivial invariant), add a short comment — but only in such cases.

## Code navigation

Order of tools — always like this, never skip a step:
1. **LSP** (`definition`, `references`, `hover`) — for symbols, types, callers
2. **`grep` / `find`** — for a pattern LSP does not cover
3. **`Read`** — only once you know exactly which file and roughly where

Never read a file "to have a look" — search first, Read only as the last step.

## Documentation navigation

**The same rule applies to `docs/`.** Before you write an ADR, an audit finding, or a claim about how
something works and why, grep `docs/` for the identifiers you are writing about — a field, type,
use case or collection name:

```
grep -rl "food_item_id" docs/
```

Reason: design docs are **frozen, not unimportant**. Frozen means do not edit them, not do not read them.
The decision recorded in them is still in force, and claiming "nobody thought of this"
or "a decision needs to be made here" about it is a mistake — even if the code really is broken.

- **The entry point is `docs/ARCHITECTURE.md`.** Every section links the ADRs and design docs
  of its area at the top. Read that one section, not all of `docs/`.
- When you find a contradiction between the code and a design doc, that is a **finding** — the design doc is not corrected.
- When a decision in a design doc was already accepted together with a mitigation, the finding is not erased by that,
  but it must acknowledge the mitigation and narrow itself to what actually remains.

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

# General working rules

These rules apply to every task unless explicitly overridden.
Bias: caution over speed on non-trivial work. For trivial tasks, use sound judgment.

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
