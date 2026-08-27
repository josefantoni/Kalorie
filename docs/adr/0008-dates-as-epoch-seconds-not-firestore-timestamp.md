# 0008. Dates are stored as epoch seconds, not as a Firestore `Timestamp`

- **Status:** Accepted
- **Scope:** Backend, Cross-platform
- **Date:** 2026-08-27

## Context

Every date the app persists — `date` on `foodItems` and `foodConsumed`, `favourited_at`,
`created_at` and `updated_at` on `myCreatedMeals` — is written as a `Double` holding
`Date.timeIntervalSince1970`: **seconds** since the Unix epoch.

Firestore offers a native `Timestamp` type, and `Firestore.Encoder` would have encoded a Swift
`Date` as one without any extra work. It was not used. The original reason cannot be
reconstructed from the code or the history; the most likely explanation is that `TimeInterval`
was simply the shape the domain models already had, and nothing forced the question.

Whatever the origin, the encoding is now load-bearing: `FetchFoodsConsumedUseCase` and
`FetchFoodsConsumedForMonthUseCase` query day and month windows with a numeric range
(`isGreaterThanOrEqualTo` / `isLessThan`) over that `Double`, and every stored document uses it.

## Decision

Dates are persisted as `TimeInterval` — a `Double` of seconds since 1970 — and are converted at
the DTO boundary only. No Firestore `Timestamp` appears anywhere in the data model.

## Consequences

- The range queries in the fetch use cases work as plain numeric comparisons and need no
  special handling. Changing to `Timestamp` means rewriting every one of them together with
  every stored document; the two cannot be migrated separately.
- **A second client must use seconds, not milliseconds.** This is the sharp edge: Kotlin's
  `System.currentTimeMillis()` and `java.time.Instant.toEpochMilli()` — the obvious things to
  reach for on Android — are off by a factor of 1000, and the error is silent. Every date lands
  in the year 56000 and every window query returns nothing.
- Firestore's console shows raw numbers rather than readable dates, and server-side features
  that understand `Timestamp` (TTL policies, `serverTimestamp()`) cannot be applied to these
  fields.
- This is a decision that is **safe to revisit** if a migration is on the table anyway — no
  requirement depends on it. It is not safe to revisit for one collection at a time.
