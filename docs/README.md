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

### Design docs

| # | Title | Status | Scope |
|---|---|---|---|
| [0001](design/0001-user-authentication.md) | User authentication | Implemented | Backend, Cross-platform, iOS |

### Decision records

| # | Decision | Status | Scope |
|---|---|---|---|
| [0001](adr/0001-anonymous-firebase-auth-as-device-identity.md) | Anonymous Firebase Auth as device identity | Accepted | Cross-platform |
| [0002](adr/0002-merge-anonymous-data-before-switching-accounts.md) | Merge anonymous data before switching accounts | Accepted | Cross-platform |
| [0003](adr/0003-separate-auth-command-provider.md) | Separate auth command provider from read-only auth provider | Accepted | iOS |
| [0004](adr/0004-migrate-usecase-exposes-two-methods.md) | MigrateAnonymousDataUseCase exposes two methods | Accepted | iOS |
