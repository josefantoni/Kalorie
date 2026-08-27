# Project documentation

All project documentation and code comments are written in **English**, regardless of the
language used in day-to-day communication.

## Which document do I write?

Documents are separated by **lifecycle**, not by topic. Mixing them produces a document that
is neither a plan nor a description of reality.

| Document | Written | Lifecycle | Lives in |
|---|---|---|---|
| **Design doc** | Before implementation | **Frozen** once shipped — a historical record of why we built it this way | `docs/design/` |
| **ADR** | When a decision is made | **Immutable** — never edited, only superseded by a newer ADR | `docs/adr/` |
| **Setup / runbook** | When manual configuration exists | **Living** — updated whenever the environment changes | `docs/SETUP.md` |
| **Code comment** | While writing the code | Lives and dies with the code it explains | Next to the code |
| **Status, review findings, test counts** | — | Belongs in the commit message or the PR, and dies there | Not in `docs/` |

The rule of thumb:

> Knowledge about **one piece of code** belongs next to that code.
> A decision that shapes **several pieces** belongs in an ADR.
> **Status** belongs in the PR and dies there.

### Do not update a shipped design doc

Once a feature is implemented, the design doc stops being maintained. Appending
implementation notes to it turns earlier sections into lies — "what is missing" lists things
that now exist, "decisions to make" describes decisions already made. If the design turned out
wrong, write a new ADR that supersedes it; do not rewrite history.

### Documenting code that already exists

Most of the app was written before this process existed. Documenting it retroactively is worth
doing, but **not as a design doc** — a design doc argues for a plan, and writing one for code
that already ships produces a fiction that pretends the outcome was foreseen. Retroactive work
splits into three real outputs:

| Output | What it is | Where |
|---|---|---|
| **Architecture overview** | A description of what exists: layers, data model, how a feature is wired. Living — updated when the structure changes. | `docs/ARCHITECTURE.md` |
| **Backfilled ADRs** | Decisions already in effect that a reader could plausibly undo by accident. Written with their real rationale, not a reconstructed one. | `docs/adr/` |
| **Audit findings** | Bugs, inconsistencies, stale assumptions the review turned up. | `TODO.md`, or fixed straight away |

### Search the docs before you claim something

The same rule this project applies to code — *search first, never open a file just to look
around* — applies here. Before writing an ADR, an audit finding, or any statement about how
something works and why, grep for the identifiers involved:

```
grep -rl "food_item_id" docs/
```

**Frozen means do not edit, not do not read.** A decision recorded in a shipped design doc is
still in force, and writing "nobody considered this" or "this needs deciding" about one is wrong
even when the code really is broken. [ARCHITECTURE.md](ARCHITECTURE.md) is the entry point: each
section opens with a **Read first:** line naming the ADRs and design docs for that area, so one
section is the whole reading list and nothing else has to be opened.

Two consequences worth stating:

- A conflict between the code and a design doc is a **finding**. The design doc is not corrected.
- If a design doc already accepts a risk and names its mitigation, a finding about it is not
  void — but it must say so and narrow to what genuinely remains.

Two rules keep this from becoming busywork:

- **Analyse an area just before you change it**, not speculatively. That is when the findings
  feed a real decision instead of a document nobody reads.
- **If the reason for a decision cannot be reconstructed, say so** rather than inventing one.
  "This was inherited and nobody remembers why" is a legitimate ADR context, and it is the one
  that tells the next reader the decision is safe to revisit.

## Platform scope

The project targets iOS today, but the backend is shared and an Android client is a realistic
future direction. Every design doc and ADR therefore declares a **Scope**:

| Scope | Meaning | Consequence for a second platform |
|---|---|---|
| `Backend` | Firestore data model, security rules, Cloud Functions | One implementation serves all clients. Changing it affects every platform at once. |
| `Cross-platform` | Behaviour every client must implement identically | Re-implemented per platform. Clients must not diverge, or they corrupt each other's data. |
| `iOS` | Implementation detail of this client | Another platform is free to decide differently. |

Getting this label right is what makes these documents reusable later: when the Android client
is written, `Backend` and `Cross-platform` records are requirements, and `iOS` records are
merely precedent.

## Standards these follow

- **ADR** — Architecture Decision Record, [Michael Nygard's format](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
  (*Context → Decision → Consequences*), as popularised by the [MADR](https://adr.github.io/madr/) template.
- **Design doc** — Google-style design doc (*Context and scope → Goals and non-goals → Design →
  Alternatives considered → Cross-cutting concerns*), frozen after implementation.
- **RFC 2119** — MUST / SHOULD / MAY, when a requirement needs to be unambiguous.

`arc42` and the `C4 model` are deliberately not used — they document a whole system as a living
artefact, which is more process than a project this size can keep honest.

## Templates

### Design doc — `docs/design/NNNN-short-title.md`

```markdown
# Design: <Title>

- **Status:** Draft | Approved | Implemented in `<commit>` | Superseded by <link>
- **Scope:** Backend | Cross-platform | iOS  (list all that apply)
- **Date:** YYYY-MM-DD

## Context and scope
What exists today, what problem this solves, and where the boundary of this document is.

## Goals
What success looks like, in terms a reader can check.

## Non-goals
What this deliberately does not solve. This section prevents scope creep and is usually the
most useful part six months later.

## Design
The proposal. Per-area subsections; call out anything that constrains other platforms.

## Alternatives considered
Each with the reason it was rejected. A rejected option with no reason is not an alternative,
it is a footnote.

## Cross-cutting concerns
Security, privacy, offline behaviour, App Store / Play Store requirements, migration of
existing data.

## Risks
Risk → impact → mitigation.

## Outcome
Filled in once. Link the implementing commit and the ADRs this produced. Then stop editing
the document.
```

### ADR — `docs/adr/NNNN-short-title.md`

```markdown
# NNNN. <Decision, phrased as a statement>

- **Status:** Accepted | Superseded by <link>
- **Scope:** Backend | Cross-platform | iOS
- **Date:** YYYY-MM-DD

## Context
The forces at play: constraints, requirements, what made this a decision rather than an
obvious default.

## Decision
What we do, in the active voice. One decision per record.

## Consequences
What becomes easier, what becomes harder, and what a future reader must not undo without
understanding why.
```

## Index

### Architecture

| Document | Covers |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Living description of what exists, area by area. Currently: data layer and Firestore model; food search and catalogue; dashboard and meal types; food entry flow; cross-cutting concerns. |

### Design docs

| # | Title | Status | Scope |
|---|---|---|---|
| [0001](design/0001-user-authentication.md) | User authentication | Implemented | Backend, Cross-platform, iOS |
| [0002](design/0002-google-sign-in.md) | Google sign-in as a second provider | Implemented | Cross-platform, iOS |
| [0003](design/0003-favourite-foods.md) | Favourite foods | Implemented | Backend, Cross-platform, iOS |
| [0004](design/0004-shared-macro-calculation-module.md) | Shared macro calculation module (Kotlin Multiplatform) | Implemented | Cross-platform, iOS |
| [0005](design/0005-meal-window-and-html-entity-decoding.md) | Meal-window arithmetic and HTML entity decoding (Kotlin Multiplatform) | Implemented | Cross-platform, iOS |
| [0006](design/0006-own-daily-meals.md) | My created meals | Implemented | Backend, Cross-platform, iOS |

### Decision records

| # | Decision | Status | Scope |
|---|---|---|---|
| [0001](adr/0001-anonymous-firebase-auth-as-device-identity.md) | Anonymous Firebase Auth as device identity | Accepted | Cross-platform |
| [0002](adr/0002-merge-anonymous-data-before-switching-accounts.md) | Merge anonymous data before switching accounts | Accepted | Cross-platform |
| [0003](adr/0003-separate-auth-command-provider.md) | Separate auth command provider from read-only auth provider | Accepted | iOS |
| [0004](adr/0004-migrate-usecase-exposes-two-methods.md) | MigrateAnonymousDataUseCase exposes two methods | Accepted | iOS |
| [0005](adr/0005-no-shared-sign-in-provider-abstraction.md) | Each sign-in provider gets its own use case, with no shared abstraction | Accepted | iOS |
| [0006](adr/0006-google-sign-in-identity-collisions.md) | A user reaching an existing account through the other provider is steered to it, with no override | Accepted | Cross-platform |
| [0007](adr/0007-derive-missing-energy-kj-from-macros.md) | Missing energyKJ is derived from macros, not defaulted to 0 | Accepted | Cross-platform |
| [0008](adr/0008-dates-as-epoch-seconds-not-firestore-timestamp.md) | Dates are stored as epoch seconds, not as a Firestore `Timestamp` | Accepted | Backend, Cross-platform |
| [0009](adr/0009-denormalised-nutrition-snapshots.md) | Nutrition values are denormalised into every collection that references a food | Accepted | Backend, Cross-platform |
| [0010](adr/0010-client-assigned-integer-meal-type-ids.md) | Meal type IDs are integers assigned by the client | Accepted | Backend, Cross-platform |
| [0011](adr/0011-foodItems-writable-by-any-authenticated-client.md) | `foodItems` is writable by any authenticated client, pending the moderation flow | Accepted | Backend |
| [0012](adr/0012-external-food-is-surfaced-never-imported.md) | OpenFoodFacts results are surfaced to the user, never imported into the catalogue | Accepted | Cross-platform |
| [0013](adr/0013-prefix-search-over-lowercased-name-fields.md) | Catalogue search is a Firestore prefix range over pre-lowercased name fields | Accepted | Backend, Cross-platform |
| [0014](adr/0014-meal-assignment-by-time-of-day-only.md) | A food is assigned to a meal by time of day alone, never by calendar date | Accepted | Cross-platform |
| [0015](adr/0015-dashboard-caches-a-month-and-derives-the-day.md) | The Dashboard fetches a whole month and derives every day view from it | Accepted | iOS |
| [0016](adr/0016-logged-entries-rescale-from-their-own-stored-values.md) | A logged entry is rescaled from its own stored values, never from the catalogue | Accepted | Cross-platform |
| [0017](adr/0017-optimistic-favourite-toggle-shared-by-protocol-extension.md) | Favourite toggling is an optimistic protocol extension, not a use case | Accepted | iOS |
| [0018](adr/0018-per-feature-error-alerts-with-no-global-handler.md) | Errors are presented per feature as a dismissible alert, with no global handler | Accepted | iOS |
| [0019](adr/0019-l10n-enum-over-the-string-catalogue.md) | Localized strings are reached through a hand-written `L10n` enum | Accepted | iOS |
