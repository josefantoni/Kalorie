# Design: Slovak localisation

- **Status:** Proposed
- **Scope:** Cross-platform
- **Date:** 2026-10-25

## Context and scope

Both apps ship in Czech and English. A Slovak device gets the English UI, because no Slovak
resources exist, while the catalogue already shows it Czech food names (`BilingualNamed`,
`isCzechOrSlovak`; [ADR 0036](../adr/0036-food-display-name-is-chosen-by-device-language-at-render-time.md)).
On the App Store the Slovak storefront shows the English description with Czech screenshots and no
subtitle, because the store listing has no Slovak localisation either.

This document adds Slovak (`sk`) as a third UI language in both apps, on the two web pages the
stores link to, and in both store listings.

## Goals

- A device set to Slovak gets a Slovak UI, a Slovak system permission prompt and a Slovak store
  listing on both platforms.
- `localisation/strings.json` stays the single source for UI strings (`Android/CLAUDE.md` → *Strings*).
  A missing `sk` text fails the generator, as a missing `cs` or `en` text does today.
- The privacy policy and the account-deletion page have a Slovak section, and each page opens with
  a language switcher.

## Non-goals

- **Slovak catalogue names.** `foodItems` keeps `cz_name` / `eng_name`. A Slovak user keeps seeing the
  Czech name first, as ADR 0036 and `ARCHITECTURE.md` § 5.3 decide, and keeps searching it by the
  Czech folding in TextKit. Czech and Slovak share most food names, and a third name field would
  touch the rules, the import scripts and the search index for little gain.
- **Slovak portion names.** `localizedPortionName(czech:)` already takes the Czech branch for `sk`
  ([ADR 0043](../adr/0043-portion-names-are-localized-and-normalized-at-render-time.md)).
- **Changing the fallback.** English stays the default for every language that is not `cs` or `sk`.
- **An in-app language picker.** The app follows the device language, as today.

## Design

### Strings

`localisation/strings.json` gets an `sk` key on every entry (292 at the time of writing).
`scripts/lib/localisation.js` adds `'sk'` to `LANGUAGES`, so `validateSource` requires it and both
generators loop over it. `generateAndroidStrings` returns one more file, written to
`Android/app/src/main/res/values-sk/strings.xml` by `scripts/generate-strings.js`; the
`--check` run in CI covers it automatically. `generateXcstrings` writes an `sk` localisation into
`Localizable.xcstrings`.

The Slovak texts are written from the Czech and English text together, Czech being the source
language. They are not a word-for-word port: Slovak differs from Czech in vocabulary and
in forms of address, and the tone of the existing strings (informal *ty*) is kept.

### iOS

- `InfoPlist.xcstrings` (`CFBundleDisplayName`, `CFBundleName`, `NSCameraUsageDescription` and the
  entry-intent string) gets `sk`. It is not generated, so it is edited by hand.
- The App Store lists a language only when the built bundle contains it. Check that the archived app
  has an `sk.lproj`, the way it has a `cs.lproj`, rather than assuming the `.xcstrings` entry is
  enough. `knownRegions` in `project.pbxproj` currently lists only `en` and `Base`.
- No Swift code changes: all strings go through `L10n`, which reads the catalogue.

### Android

- `values-sk/strings.xml` is generated. The Gradle build has no `resourceConfigurations`
  filter and no `localeConfig`, so the new folder is picked up as it is.
- `isCzechOrSlovakLocale` already covers `sk`.

### Web pages

`backend/hosting/privacy.html` already holds a `<div lang="cs">` and a `<div lang="en">` block;
`delete-account.html` has the same pair as bare headings. Both get a third block, `<div lang="sk">`,
and a one-line switcher at the top: `Čeština · Slovenčina · English`, three `<a href="#cs|#sk|#en">`
links to an `id` on each block. No script, no hidden blocks, so the pages keep working with
JavaScript off and a store reviewer sees all of the text. The `<html lang>` and `<title>` stay as
they are (title is already bilingual).

The policy text is a legal document: the Slovak section is a translation of the Czech one and
must say the same thing. When the Czech text changes, the Slovak one changes in the same commit.

### Store listings

Texts to prepare, in this order of priority, Slovak and English kept in sync with the existing Czech
ones:

| Field | App Store Connect | Google Play |
|---|---|---|
| App name | 30 chars | 30 chars |
| Subtitle | 30 chars | — |
| Short description | — | 80 chars |
| Full description | 4000 chars | 4000 chars |
| Keywords | 100 chars | — |
| What's New | per version | per release |
| Screenshots | reuse the Czech ones | reuse the Czech ones |

Screenshots stay Czech: the Slovak catalogue names are Czech and the UI differences are minimal. A
Slovak set can come later if the Slovak store traffic justifies it.

Releasing:

- **App Store:** a new localisation's metadata is part of a version, so it ships with the build that
  adds the Slovak UI. A subtitle on the Slovak storefront comes with it.
- **Google Play:** a store listing translation can be added at any time; the Slovak UI needs a new
  release because `values-sk` is part of the APK/AAB.
- Both releases follow the `[RELEASE]` rules in the root `CLAUDE.md`.

## Alternatives considered

- **Send Slovak users to the Czech UI.** Rejected: it needs a duplicate resource set or a locale
  mapping on each platform, and the UI would still not be Slovak.
- **Translate the web pages with a script.** A language-detecting redirect hides the other languages
  from a store reviewer and a crawler. The anchor switcher keeps one URL and the full text.
- **A `sk_name` on catalogue items.** See *Non-goals*.

## Risks

- **Translation quality.** The author is not a native speaker. Mitigation: a short review pass by
  someone who is, before the release, at least for the store texts and the privacy policy.
- **Czech-only search folding.** Slovak diacritics outside the Czech set (`ä ô ĺ ľ ŕ`) pass through
  TextKit's `foldDiacritics` unfolded. This is the accepted limitation already recorded in
  `ARCHITECTURE.md` § 5.3; it is unchanged by this work, since the search matches the Czech name.
- **Format specifiers.** A Slovak string with a different argument count or order breaks at run
  time; the generator's `convertFormat` already checks specifiers per entry.

## Outcome

_To be filled in after shipping._
