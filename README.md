# Kalorie

iOS app for tracking calories and macros. Built it because existing apps are bloated with features I don't need.

## What it does

- Log food with calories, protein, carbs, fat
- Barcode scanner for adding foods
- Customisable meal types with drag-to-reorder
- Synced via Firestore so data persists across devices

## Stack

SwiftUI, Firebase (Firestore + Auth), XCTest, GitHub Actions

## Architecture

MVVM with a UseCase layer. Each use case is a `struct` with `callAsFunction`, injected via protocol. Dependencies wired through `Configurator` structs at the call site.

```
View → ViewModel → UseCase → FirestoreDataProvider
```

## Status

Work in progress.
