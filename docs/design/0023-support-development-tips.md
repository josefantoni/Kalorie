# Design: Support development with in-app tips

- **Status:** Proposed — implemented on both platforms (StoreKit 2, Play Billing); not shipped, the
  products do not exist in the store consoles yet
- **Scope:** Cross-platform, iOS, Android
- **Date:** 2026-10-11

## Context and scope

The app is free and has no income. Running it does cost money: the Blaze plan (budget alert
150 CZK/month, [design 0022](0022-food-photos.md)), the Google Play registration (paid) and, before
iOS can ship at all, the Apple Developer Program (99 USD/year). There is no paid Apple account yet,
so only Google Play is active.

This document covers a voluntary tip from Settings. It does not unlock anything, so the app stays
fully free and nothing about the existing features changes.

## Goals

- A user can send a one-off tip in a few taps from Settings, on both platforms, signed in or
  anonymous.
- The purchase goes through the store's own billing, so it passes review on both stores.
- If the store is unavailable or the products do not load, the row is not shown at all — no empty
  or broken screen.
- After a successful tip the user sees a thank-you; nothing else in the app changes.

## Non-goals

- A subscription or a premium tier. Possible later if the app gets traction; it would need its own
  design (entitlements, restore, server-side verification).
- Ads. They need a third-party SDK, a consent banner and new store privacy declarations, and they
  cost the user experience more than they would earn at this scale.
- Recording tips in Firestore or showing a supporter badge. A tip grants no entitlement, so there is
  nothing to verify or store.
- Nagging: no prompt, popup or badge outside Settings asks for a tip.

## Design

### Why store billing and not an external link

A link to Buy Me a Coffee, Ko-fi or a bank account is the simplest option, but both stores treat a
payment made from inside the app to its developer as a digital purchase:

- **App Store** — guideline 3.1.1 requires In-App Purchase for tips to the developer; external
  donation links are only allowed for approved nonprofits. Since May 2025 (Epic v. Apple) apps on
  the **US storefront** may link to external payments, and the EU has its own terms under the DMA.
  Neither is a way out: the US change applies to that storefront only, sources disagree on whether
  it covers a donation link to the developer, and developers report reviewers still rejecting
  "Support the Developer" links under 3.1.1 (relabelling does not help). The 3.2.1(vii) gift
  exemption covers gifts between individuals, not tips to the app's developer. A link on the
  project website or in the store listing, outside the app, is fine but is not an in-app tip.
- **Google Play** — the Payments policy requires Google Play Billing for in-app payments for
  digital goods and services; a tip to the developer of the app falls under it.

Both stores take 15 % under their small-business programmes (Apple requires enrolling in the
App Store Small Business Program; on Google Play it applies automatically up to 1 M USD/year).

### Products

Three **consumable** products with the same IDs on both stores, so the UI and the strings are
shared:

| Product ID | Suggested price tier |
|---|---|
| `tip_small` | ~29 CZK / 0.99 EUR |
| `tip_medium` | ~79 CZK / 2.99 EUR |
| `tip_large` | ~199 CZK / 6.99 EUR |

Consumable means the same tip can be bought again. The display price always comes from the store
(`Product.displayPrice` / `ProductDetails.oneTimePurchaseOfferDetails.formattedPrice`), never from
our strings.

### Flow

1. Settings row *Podpořit vývoj* / *Support development* (the placeholder that already exists on
   iOS) pushes a `SupportView`.
2. `SupportView` loads the three products. While loading: a progress indicator. On failure or an
   empty result: the error alert pattern from [ADR 0018](../adr/0018-per-feature-error-alerts-with-no-global-handler.md)
   and the row stays usable for a retry.
3. Tapping a product starts the store purchase sheet.
4. On success: a thank-you message on the same screen. On user cancel: nothing. On a pending
   purchase (Android: payment method that settles later; iOS: Ask to Buy): a short "the payment is
   pending" message, and the purchase is finished when the store reports it.

The row sits in its own section between *Other* and *Moderation*; it is styled differently from
the *Other* rows on purpose (icon, subtitle), unlike the "all styled alike" rule of § 3.9 in
`ARCHITECTURE.md`. Once shipped, § 3.9 has to list it.

### iOS (StoreKit 2)

- `Product.products(for:)`, `product.purchase()`, then `transaction.finish()` after checking the
  `VerificationResult`. No receipt is sent anywhere.
- A `Transaction.updates` listener started at app launch, so pending (Ask to Buy) and interrupted
  purchases get finished — StoreKit warns when it is missing.
- A `.storekit` configuration file in the project for testing in the simulator without App Store
  Connect.

### Android (Google Play Billing Library)

- `BillingClient` with `enablePendingPurchases`, `queryProductDetailsAsync`, `launchBillingFlow`.
- Every purchase in state `PURCHASED` MUST be **consumed** (`consumeAsync`). A purchase that is
  neither consumed nor acknowledged within 3 days is refunded automatically by Google.
- Unconsumed purchases are also checked with `queryPurchasesAsync` when `SupportView` opens, for the
  case where the app was killed during the purchase.
- Testing needs the app uploaded to at least the internal testing track and license testers added
  in Play Console; product IDs do not resolve for a build that was never uploaded.

### Privacy

The app does not collect or send any purchase data; the stores process the payment. The privacy
policy (`backend/hosting/privacy.html`) should still say that optional tips are processed by Apple /
Google and that the app does not receive card details. Check before release whether the Play Data
safety form needs *Purchase history* — the answer depends on whether only the Play Billing Library
handles it.

## Prerequisites outside the code

These are the user's to do; the code cannot be released without them.

- **Google Play payments profile** — Play Console → Monetization setup: a merchant (payments)
  profile with a bank account and tax information. Products can be created only after that.
- **EU trader status (DSA)** — a developer account that earns money is a *trader*, and both stores
  then publish the developer's address, phone and email on the store listing. Decide whether a
  home address is acceptable or whether a business address is needed.
- **Czech tax and trade licence** — income from the stores is taxable income; whether a trade
  licence (*živnost*) is needed and how VAT works (the store is usually the deemed supplier to the
  customer) is a question for an accountant, not something to infer here.
- **Apple** — paid Apple Developer Program, *Paid Apps* agreement, banking and tax forms in App
  Store Connect, enrolment in the Small Business Program. Only when the iOS app is going to ship.

## Alternatives considered

- **External donation link** — rejected for the store rules above; a rejection on review would cost
  more time than implementing billing.
- **Paid app** — kills the downloads of an app nobody knows yet.
- **Subscription / premium features** — the only option with real income potential, but it needs a
  reason to pay (features worth paying for), entitlement handling and restore. Deferred until there
  are users to ask.
- **RevenueCat** — a wrapper over both stores; useful for subscriptions, overkill for three
  consumables with no entitlement.

## Rollout

1. Android first (the only active store): payments profile → products in Play Console →
   implementation → internal testing with a license tester.
2. iOS when the Apple account is paid for: the same products in App Store Connect, StoreKit 2.
3. Until the feature is implemented, the placeholder row only shows a "Coming soon" alert.

## Implementation notes

- **Error handling:** when the products do not load, `SupportView` shows the error alert and stays on
  screen so the user can leave and retry; the Settings row is always visible. The goal "do not show the
  row at all" above was not implemented, because the row cannot know in advance that the store is down.
- **Thank-you:** a successful purchase does not show an alert. The screen fades in "Děkuji!" for five
  seconds with an animation drawn in code (no image assets), chosen by the tier: `tip_small` rains
  baguettes, `tip_medium` shows a roast chicken lunch, `tip_large` shows a grandma cooking. The tiers
  are named *Svačina* / *Trošku větší obídek* / *Babička navařila* in the app; the store listings use
  neutral names.
- **Ports:** the iOS and Android drawings use the same coordinates and colour roles
  (`Android/.../components/illustrations/` mirrors `iOS/.../Components/`). Android draws them in
  `DrawScope` units of dp around the centre of the canvas.

- **Testing without a store:** iOS gets the products from `iOS/Tips.storekit`; Android debug builds
  use `TipStoreFake` (`createTipStore` in the `debug` source set returns it; the `release` source set
  returns `PlayBillingTipStore`). Setting `USE_FAKE_STORE` to `false` in the debug factory tests the real
  Play Billing, which only resolves for a build signed like the one uploaded to the testing track.

## Outcome

*Filled in once after shipping.*
