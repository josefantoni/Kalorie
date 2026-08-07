# Kalorie — TODO

## Kinds of data

The app works with three kinds of data. The distinction matters for the items below:

- **Shared food catalogue** (`foodItems`) — a global database visible to everyone. New entries
  are added by the app maintainer, or approved from user submissions.
- **Private user data** (`users/{userId}/foodConsumed`, `mealTypes`) — food entries and meal
  layout, visible only to their owner.
- **User's own meals** — meals a user composes themselves (see below). These stay private and
  do **not** go through approval.

## Planned features

- [ ] **Data export** — export consumed food for a chosen interval to PDF or Excel
- [ ] **Own daily meals** — let a user compose a meal they eat regularly instead of entering the
  ingredients every day; visible only to that user, not subject to approval
- [x] **User authentication** — optional Apple ID sign-in so a user keeps their data across a
  device change and can use the app on both an iPhone and an iPad. Signed-out users keep working
  in the device-bound anonymous mode. See
  [docs/design/0001-user-authentication.md](docs/design/0001-user-authentication.md).
  *Remaining: out-of-band configuration — see [docs/SETUP.md](docs/SETUP.md).*
- [ ] **Prompt to sign in** — the account screen is only reachable from the toolbar icon; add an
  unobtrusive prompt after the first logged meal so users on a second device sign in early
- [ ] **User-submitted food** — the user photographs the packaging, fills in macros and calories,
  reviews and submits for approval. The submission goes to a separate pending collection rather
  than straight into the shared catalogue, and reaches `foodItems` only once the maintainer
  approves it. Requires Firebase Storage for the photos.
- [ ] **Maintainer admin panel** — a list of pending food submissions (including the packaging
  photo) to approve or reject; approving publishes the item to the shared `foodItems` catalogue.
  Requires a maintainer role (Firebase custom claims) and matching security rules — once done,
  direct client writes to `foodItems` get disabled.
- [ ] **Favourite foods** — before the user starts typing in the search field, show a "Favourites"
  section ordered by how often they have logged each food; more frequently logged foods rank
  higher
- [ ] **Rank search results by frequency** — order manual search results by how often the user has
  logged each food, so the most used ones come first
