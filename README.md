# Kalorie

Mobile apps for tracking calories and macros. Built because existing apps are bloated with features I don't need.

- **iOS** (`iOS/`) — feature complete, ready for alpha distribution
- **Android** (`Android/`) — every screen ported from iOS; camera features still need on-device verification before release

<table>
  <tr>
    <th align="center">iOS (SwiftUI)<br>Light mode</th>
    <th align="center">Android (Jetpack Compose)<br>Dark mode</th>
  </tr>
  <tr>
    <td align="center"><img src="https://github.com/user-attachments/assets/ba008549-042e-4f36-ba50-5854e803d6bb" width="250" alt="iOS dashboard"></td>
    <td align="center"><img src="https://github.com/user-attachments/assets/ebbe2a3e-7669-4d99-b0c1-285602bd4d9b" width="250" alt="Android dashboard"></td>
  </tr>
  <tr>
    <td align="center"><img src="https://github.com/user-attachments/assets/d703198a-2672-41a3-a967-5086c04d7d75" width="250" alt="iOS add food"></td>
    <td align="center"><img src="https://github.com/user-attachments/assets/a872a09f-1579-49aa-acec-e01c1d1fbaf9" width="250" alt="Android add food"></td>
  </tr>
</table>

## What it does

- Log food with calories, protein, carbs, fat, saturates, sugar, salt and fibre; per-entry weight or portion
- Find food by typed search (whole-word, diacritics-insensitive) or barcode, with OpenFoodFacts as a fallback when the shared catalogue has no match
- Add a missing food by hand or by photographing its nutrition label (on-device OCR); submissions go through maintainer moderation before entering the shared catalogue
- Named portions on a food, plus personal portions per user
- Favourites and your own composed meals, searchable and loggable like any other food
- Customisable meal windows with drag-to-reorder; entries can be moved between meals
- Millilitre foods next to gram foods
- Report incorrect catalogue data
- Export logged food to PDF or Excel
- Anonymous device identity out of the box, Google sign-in to keep data across devices (Apple sign-in is pending)
- Czech and English

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
| `scripts/` | One-off Admin SDK scripts (backfills, maintainer claim) |
| `docs/` | Architecture, ADRs, design docs and setup notes |

## Stack

- **iOS:** SwiftUI, Combine, XCTest, Liquid Glass (iOS 26)
- **Android:** Kotlin, Jetpack Compose, Material 3, `ViewModel` + `StateFlow`
- **Shared:** Kotlin Multiplatform modules consumed by both apps
- **Backend:** Firebase — Firestore and Auth; no custom server
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

Work in progress. Both apps are meant to ship on the App Store and Google Play; the Play release waits on a Play Console account. Open items, mainly Android release readiness, are tracked in [TODO.md](TODO.md).

## License

All rights reserved.
