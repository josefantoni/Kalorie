# Kalorie

Mobile apps for tracking calories and macros. Built because existing apps are bloated with features I don't need.

- **iOS** (`iOS/`) — [available on the App Store](https://apps.apple.com/cz/app/kalorie/id6823155404?l=cs)
- **Android** (`Android/`) — version 1.0 almost done with closed testing on Google Play, then production access

<table>
  <tr>
    <th align="center">iOS (SwiftUI)<br>Light mode</th>
    <th align="center">Android (Jetpack Compose)<br>Dark mode</th>
  </tr>
  <tr>
    <td align="center"><img src="https://github.com/user-attachments/assets/ca5f7fd9-fe1e-4d2f-8d04-8263aa0af80f" width="250" alt="iOS dashboard"></td>
    <td align="center"><img src="https://github.com/user-attachments/assets/5b15021d-5ff3-4969-a40c-02dd227618af" width="250" alt="Android dashboard"></td>
  </tr>
  <tr>
    <td align="center"><img src="https://github.com/user-attachments/assets/1b7f3225-b55d-48b2-99a2-37f84901063a" width="250" alt="iOS add food"></td>
    <td align="center"><img src="https://github.com/user-attachments/assets/06b14870-5d29-4846-a7b1-de195a2a95f0" width="250" alt="Android add food"></td>
  </tr>
  <tr>
    <td align="center"><img src="https://github.com/user-attachments/assets/19ed6bfb-bd4e-4a4f-9545-18632d6eeb6d" width="250" alt="iOS add new food to catalogue"></td>
    <td align="center"><img src="https://github.com/user-attachments/assets/b09333a6-67a9-4a84-8ee0-42aced06c360" width="250" alt="Android add new food to catalogue"></td>
  </tr>
</table>

## What it does

- Log food with calories, protein, carbs, fat, saturates, sugar, salt and fibre; per-entry weight or portion
- Find food by typed search (whole-word, diacritics-insensitive, most-logged foods first) or barcode, with OpenFoodFacts as a fallback when the shared catalogue has no match
- Add a missing food by hand or by photographing its nutrition label (on-device OCR); submissions go through maintainer moderation before entering the shared catalogue
- Product photos: when you log a food, or open a logged one, its photo is shown if there is one (the catalogue's own, or OpenFoodFacts' front photo); when you submit a missing food you can attach a photo from the camera or the library
- Named portions on a food, plus personal portions per user
- Favourites and your own composed meals, searchable and loggable like any other food
- Customisable meal windows with drag-to-reorder; entries can be moved between meals, and a logged meal can be copied to another window or day
- Millilitre foods next to gram foods
- Body weight: one entry per day, shown next to the calorie pie on the Dashboard, with a history screen; kg or lb, and the circle can be hidden in Settings
- Report incorrect catalogue data, send feedback by mail from Settings
- A hint on alcoholic drinks that their nutrition values are less reliable
- Export logged food to PDF or Excel
- Anonymous device identity out of the box, Sign in with Apple or Google to keep data across devices; the other provider can be linked later from the Account screen
- Delete your account together with all your data, submissions and photos from Settings
- Runs on Mac as an iPad app (Designed for iPad), with the camera features hidden there
- An outdated build is blocked by an update-required screen until the app is updated
- Czech, Slovak and English

## Repository layout

| Path | What lives there |
|---|---|
| `iOS/` | SwiftUI app |
| `Android/` | Jetpack Compose app, a native port of the iOS architecture |
| `backend/` | Firestore security rules and indexes, Firebase config |
| `MacroKit`, `MealKit`, `TextKit`, `ExportKit` | Shared Kotlin Multiplatform modules used by both apps |
| `fixtures/` | Golden vectors that pin cross-platform rules (validation, scaling, OpenFoodFacts mapping, export bucketing) |
| `firestore-rules-tests/` | Security-rules tests |
| `localisation/` | Shared strings |
| `scripts/` | One-off Admin SDK scripts (backfills, maintainer claim, cleanup of merged anonymous accounts) |
| `docs/` | Architecture, ADRs, design docs and setup notes |

## Stack

- **iOS:** SwiftUI, Combine, XCTest, Liquid Glass (iOS 26)
- **Android:** Kotlin, Jetpack Compose, Material 3, `ViewModel` + `StateFlow`
- **Shared:** Kotlin Multiplatform modules consumed by both apps
- **Backend:** Firebase — Firestore, Auth and Storage (food photos); no custom server
- **CI:** GitHub Actions on pull requests to `main`; path filters run only the jobs a change touches (rules, KMP, iOS, Android, localisation)

## Architecture

MVVM with a UseCase layer on both platforms. Each use case is a value type with a single `callAsFunction`, injected via protocol. Dependencies are wired by a `Configurator` per feature at the call site; there is no DI container.

```
View → ViewModel → UseCase → FirestoreDataProvider → Firestore
```

Data is private per user (`users/{userId}/…`) except the shared food catalogue, which only a maintainer can write. Details, collection layout and the wire schema are in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — what exists today; the entry point into `docs/`
- [docs/README.md](docs/README.md) — how the docs are organised and where a new document goes
- [docs/SETUP.md](docs/SETUP.md) — Firebase, CI secrets and Android setup
- [TODO.md](TODO.md) — planned features and open findings

## Status

iOS is released on the [App Store](https://apps.apple.com/cz/app/kalorie/id6823155404?l=cs). Android is almost done with closed testing ahead of its first public release: on Google Play a new personal developer account has to run the closed test with at least 12 testers opted in for 14 days before it can apply for production access. Open items are tracked in [TODO.md](TODO.md).

## License

All rights reserved.
