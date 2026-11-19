---
name: catalogue-import
description: Bulk-add or fix foods in the prod `foodItems` catalogue (Ciqual / OpenFoodFacts data, portions, photos) via gcloud + Firestore REST. Use when the user asks to import, add, correct or photograph catalogue foods directly in production.
---

# Catalogue import into prod `foodItems`

Prod project: `kalorie-bf11c`. Writes use `gcloud auth print-access-token` + Firestore REST with the
`x-goog-user-project` header. This is admin access: it **bypasses `backend/firestore.rules`**, so every
document must be checked against `validFoodItem()` by hand.

## Before writing

1. Read `docs/ARCHITECTURE.md` → catalogue section and the designs it links (0008 portions, 0011
   grams/millilitres, 0013 items without barcode, 0022 photos, 0032 propagating changes).
2. Check for duplicates against the existing `foodItems` (names, folded names).
3. Show the user the records (name, kcal and macros per 100 g/ml, portions in grams) and wait for approval.

## Building a document

- All fields required by `validFoodItem()`, including `cz_name_folded` / `eng_name_folded` and
  `*_search_terms` generated with `scripts/lib/search-terms.js`.
- Generic items get our own ids: EAN-13 with the 12-digit base `200000000000 + n` plus a check digit.
  Find the highest `n` already used in prod before allocating. An OpenFoodFacts hit on a `2000000000…`
  id is a false positive.
- Liquids are `measure_unit: "millilitres"`; convert per-100-g source values to per-100-ml with a density
  at import time (design 0011 forbids g↔ml conversion at runtime).
- Alcohol: `alcohol_by_volume` computed from Ciqual alcohol g/100 g and density.
- Missing kcal: derive `4P + 4C + 9F + 2·fibre` and tell the user which items.
- Missing required numbers from OpenFoodFacts are written as 0 and reported to the user.

## Data sources

- **Ciqual 2020** (ANSES, CC BY 4.0, Zenodo record 4770600) — preferred for generic foods; EU data only,
  no USDA. Attribution is shown in AccountView on both platforms.
- **OpenFoodFacts** — descriptive User-Agent (generic client name, no personal data), ~1 s pause
  between calls, retries; otherwise lookups fail silently.

## Photos

User downloads Pexels / Wikimedia Commons files to `~/Downloads` named after the food. Crop centre-square,
1080 px, JPEG q90 (with `sips`: crop to PNG first, then resize and save), upload to Storage
`catalogPhotos/{id}/` via REST with a download token, PATCH `photo_url`, verify the URL fetches 200.
Commons images need attribution in the app before or right after upload — ask the user.

## After writing

- Adding a food users logged from OpenFoodFacts is safe: their `external` favourites and frequency
  snapshots are upgraded by the clients on next use (ADR 0044) — no manual patching.
- Report what was written: ids, count, items without photos, estimated values.
