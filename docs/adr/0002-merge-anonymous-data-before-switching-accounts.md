# 0002. Merge anonymous data before switching accounts, not after

- **Status:** Accepted
- **Scope:** Cross-platform
- **Date:** 2026-08-07

## Context

Account linking preserves the UID, so the first device to sign in needs no migration. The
second device is different: the Apple ID is already bound, `link()` fails with
`credentialAlreadyInUse`, and the user ends up with two accounts — the local anonymous one with
whatever they logged on that device, and the existing one with their real history.

Because the app is meant to run on a phone and a tablet, this is the **normal** path for the
second device, not an edge case. Discarding the anonymous data would mean routine silent data
loss for anyone who logged meals before signing in.

The ordering is the non-obvious part. Owner-only security rules mean that the moment
`signIn()` succeeds, the client is the new user and **can no longer read the anonymous
account's data**. Anything not already in hand is unreachable.

A server-side merge via a Cloud Function was considered and rejected: a client holds one auth
token at a time, so it would need a two-phase handshake (request the merge as the anonymous
user, confirm it as the signed-in one) for no practical gain over doing it locally.

## Decision

On `credentialAlreadyInUse`, merge before switching:

1. Still anonymous — read the food entries.
2. Write that snapshot to disk.
3. Sign in. The account changes here.
4. Write the entries under the new account, reusing the original document IDs.
5. Delete the snapshot.

Only food entries are merged. Meal layouts are not: their IDs are small integers that collide
between any two accounts, so merging them would scramble the target account's configuration.
The target account's layout wins.

## Consequences

- No data is lost when a user signs in on a second device after logging meals anonymously.
- Step 2 is what makes it crash-safe. Without it, a crash between steps 3 and 4 destroys the
  data — it exists only in memory, and the account that owned it is no longer reachable. The
  snapshot is resumed on the next launch.
- Step 4 is idempotent because document IDs equal the entry's own ID (fixed in `ea39f9b`), so a
  resumed merge overwrites rather than duplicating.
- The snapshot is only valid for the account the merge was heading to, so it MUST be deleted on
  sign-out. Otherwise the next launch writes the previous user's food into a fresh anonymous
  account.
- Resuming is crash recovery and must run at most once per launch. Signing in during a live
  merge re-triggers the auth state listener, which would otherwise start the same merge a
  second time concurrently.
- Data under the abandoned anonymous account stays in Firestore. The client cannot delete it —
  it lost access at step 3.
- **Any future client MUST merge the same way.** One that signs in without merging drops entries
  that another client would have kept, and users move between clients.
