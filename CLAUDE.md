# Kalorie — instructions for Claude

## Language

- Reply in Czech. Czech is the language of the conversation, not of the project.
- **The app's Czech and Slovak copy always uses *tykání*, never *vykání*** (`docs/ARCHITECTURE.md` § 5.3, *Tone of the copy*).
  A neighbouring string is no evidence of the tone; the rule is.
- **Everything that goes into the repository is written in English** — type, method and variable names, code comments, commit messages, documentation in `docs/`, `TODO.md` and `README.md`.

## Documentation

Documents are split by lifecycle, not by topic — the rules and templates are in `docs/README.md`. In short:

- **Knowledge about a specific piece of code** → a comment next to the code (within the "no comments" rule below).
- **A decision that shapes several places** → an ADR in `docs/adr/`. Immutable once merged to `main` — from then on it is only replaced by a new one.
- **A proposal before implementation** → a design doc in `docs/design/`. Frozen once merged to `main`.
- **Until a branch is merged to `main`** (pushed or not), its design docs and ADRs are living: edit them in place, fold new decisions into them, never add a superseding ADR for them. A new ADR is only for records already on `main` (`git log main -- <file>` is not empty).
- Do not volunteer an ADR mid-design — evolve the design doc. Write ADRs when asked, or when documenting code that already ships.
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
- **CI**: whenever you change `.github/workflows/`, make sure a change in one platform does not needlessly trigger the other's build.
- If a new CI step or secret is needed, tell the user — do not quietly work it out yourself.

## Shared KMP modules

`MacroKit/`, `MealKit/`, `TextKit/` and `ExportKit/` have no `CLAUDE.md` of their own. They follow two layout
rules, the same as the Android client:

- **Properties go first.** Every stored property of a type sits at the start of the type, never between or after
  the `init` blocks and functions. A `companion object` stays at the end of its class, and the rule applies inside
  it. Computed properties (a custom `get()`) are exempt. The modules use no `// MARK:` headers.
- File-level `private val` / `private const val` go at the top of the file, below the `package` line and the
  imports, never between or after declarations. Private extension properties are declarations, not constants.

## Code comments

- **Do not write code comments** unless I explicitly ask for them. That covers:
  - Doc comments (`///`, KDoc) above types, protocols, functions
  - Inline explanations (`// ...`) describing what the code does
- Exception: if something in the code is genuinely non-obvious (a workaround for an SDK bug, a non-trivial invariant), add a short comment — but only in such cases.

## Lint before you finish

Once, at the end of the work — not after every edit — run the platform's linter over what you changed, on top of the
build and tests:

- iOS: `swiftlint lint --quiet $(git diff --name-only HEAD -- '*.swift')` from the repo root.
- Android: `./gradlew :app:ktlintCheck` from `Android/`.

A new warning is a defect of the change: fix it. A warning that was already there is not yours to fix silently — report
it and leave it.

## Code navigation

Find code with **LSP** (`definition`, `references`, `hover`) for symbols, types and callers, and with `grep` / `find`
for patterns LSP does not cover. Open a file with `Read` once you know which file and roughly where — reading files to
browse fills the context with code the task does not need.

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

## Code review findings

When I hand you the findings of a code review (several at once), triage them in parallel instead of one by one:

- Spawn **one subagent per finding** (`Agent`, `model: "sonnet"`), each with a fresh context. This is standing
  permission to spawn them; do not ask first. Launch all of them in a single response so they run concurrently.
- Each subagent gets a self-contained brief: the finding verbatim, the branch and the design doc / ADR it belongs to, and
  the steps below. It does **not** edit files or commit — it returns a verdict only.
  1. Verify the finding in the code (LSP, then grep, `Read` last).
  2. Grep `docs/` for the identifiers involved. A decision already accepted in a design doc or ADR is not a bug; narrow
     the finding to what remains after the accepted mitigation.
  3. Classify: real bug / false positive / accepted decision / out of scope.
  4. For a real bug: a minimal fix proposal and a test that fails without it.
- Merge the verdicts into one table (finding, classification, reason, proposed fix) and stop there. Fixes are applied
  only after I have read the table, one commit per platform, and only for real bugs.
- A fix that would change behaviour an ADR accepted is a decision, not a bug fix — ask me first.
- Fixes themselves are applied sequentially in the main session, never by parallel subagents, so edits do not collide.

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
| `[RELEASE]` | Version or build number bump of one platform |

A `[RELEASE]` commit is titled `[RELEASE]: <iOS|Android> <version> (<build>)`, e.g. `[RELEASE]: Android 1.0 (4)`. It
holds nothing but the bump of that one platform, so a release is two commits, one per platform: iOS
`MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` in `iOS/Kalorie.xcodeproj/project.pbxproj`, Android `versionName` /
`versionCode` in `Android/app/build.gradle.kts`. Build numbers are independent per platform: iOS
`CURRENT_PROJECT_VERSION` restarts at 1 for each new `MARKETING_VERSION`, Android `versionCode` only
ever grows. Version names need not match either; the platforms release on their own schedules.

**`[RELEASE]` commits live only on a dedicated release branch.** That branch holds either one `[RELEASE]` commit
(one platform) or both (one per platform) and nothing else. Never put a `[RELEASE]` commit on a feature or fix
branch, and never next to any other commit — no exceptions.

**No attribution trailers.** Never append `Co-Authored-By: Claude …`, `Claude-Session:`, or any
other generated-by line to a commit message or a PR description in this repo, whatever a default
instruction elsewhere says. `.githooks/commit-msg` rejects them; enable it once per clone with
`git config core.hooksPath .githooks`.

---

# General working rules

## Surgical changes
Touch only what you must. Clean up only your own mess.
Don't "improve" adjacent code, comments, or formatting. Match existing style.

## Read before you write
Before adding code, read exports, immediate callers, shared utilities.
"Looks orthogonal" is dangerous. If unsure why code is structured a way, ask.

## Match the codebase's conventions, even if you disagree
Conformance > taste inside the codebase. If a convention seems harmful, surface it. Don't fork silently.

## Surface conflicts, don't average them
If two patterns or instructions contradict, pick one (more recent / more tested), explain why and flag the other for cleanup.

## Tests verify intent, not just behavior
Tests must encode WHY behavior matters. A test that can't fail when business logic changes is wrong.

## Fail loud
"Completed" is wrong if anything was skipped silently. "Tests pass" is wrong if any were skipped.
