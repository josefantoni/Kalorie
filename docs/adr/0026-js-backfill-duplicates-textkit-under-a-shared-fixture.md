# 0026. The JS backfill scripts' TextKit duplication is governed by a shared fixture

- **Status:** Accepted
- **Scope:** Backend, Cross-platform
- **Date:** 2026-09-08

## Context

[ADR 0024](0024-token-array-field-for-whole-word-search.md) puts `searchTerms` in **TextKit**
specifically so no writer to `foodItems` re-derives it, and its Consequences state that a second
client "must produce byte-identical `search_terms` arrays, via the same TextKit function — not
its own re-tokenisation." `scripts/backfill-search-terms.js` and the earlier
`scripts/backfill-name-folding.js` are such writers, and cannot honour that: they are one-off
Admin SDK scripts run with Node, and Node cannot call TextKit's compiled Kotlin/Native
XCFramework. `scripts/lib/search-terms.js` and `scripts/lib/diacritics.js` are therefore a second,
independent implementation of `searchTerms` and `foldDiacritics` — the exact thing ADR 0024 rules
out, and ADR 0024 does not acknowledge that its own named script can't comply.

Audited 2026-09-08 (`TODO.md` **A2-13**): the two implementations are currently equivalent — both
fold-then-prefix in the same order, split on the same literal `" +"`, and hold the identical 15
Czech diacritic pairs. So this was a latent divergence risk, not a live bug — but nothing tested
the two against each other, and a one-character edit to either fold map would have silently made
part of the catalogue unsearchable by a prefix the other side generates, with no test failing
anywhere.

ADR 0024 is Accepted and already shared (`origin/main`), so per `docs/README.md` it is not
edited — this is a new record, not a correction of it.

## Decision

The JS duplication is accepted as a deliberate, permanent exception to ADR 0024's "same TextKit
function" rule, scoped to exactly `scripts/lib/diacritics.js` and `scripts/lib/search-terms.js`.
In exchange, both implementations are checked against one shared, checked-in fixture:
`TextKit/fixtures/text-kit-cases.json` — a list of `{ input, expected }` cases for
`foldDiacritics` and for `searchTerms`.

- `scripts/lib/diacritics.test.js` and `scripts/lib/search-terms.test.js` load the fixture file
  directly (`fs.readFileSync`) and generate one `node:test` case per entry.
- `TextKit/src/commonTest/kotlin/DiacriticFoldingTest.kt` and `SearchTermsTest.kt` assert the
  identical cases, copied by hand. TextKit has no JVM or JS test target — only `iosArm64` and
  `iosSimulatorArm64` — so a Kotlin/Native test cannot read a repo file at test time the way the
  Node suite does; each test carries a comment pointing back at the fixture and the JS suite that
  loads it.

Anyone changing what either function does for a given input edits the fixture first, then updates
both suites to match it — the fixture is the single place the two algorithms' expected behaviour
is written down.

## Consequences

- A future divergence between the Kotlin and JS implementations now fails a test on whichever
  side was edited, instead of silently shipping a catalogue write that one client can't find by a
  prefix the other client's user would expect to work.
- **This is not automatic cross-language enforcement.** The Kotlin side is a hand-maintained
  mirror of the JSON fixture, not a runtime read of it — editing the fixture and forgetting the
  Kotlin literal is still possible, just no longer silent once someone runs the Kotlin suite with
  a case the fixture no longer describes correctly, and not caught at all if nobody adds the new
  case to both. A stronger guarantee — giving TextKit a `js(IR)` target so a Node test could
  `require()` the compiled Kotlin output directly and diff it against `scripts/lib/*.js` at test
  time — would close that gap, but is new build infrastructure, not a documentation fix, and is
  not adopted here.
- The exception is scoped to these two files. A third writer to `foodItems`' folded or token
  fields — in this backfill tooling or elsewhere — falls under this same rule and this same
  fixture, not a fresh copy of the algorithm.
- Supersedes nothing in ADR 0024; the token-array field and its Firestore-side behaviour are
  unchanged. This fills the gap ADR 0024 left about the one writer that can't share TextKit's
  code.
