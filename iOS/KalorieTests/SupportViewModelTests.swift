//
//  SupportViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 15.10.2026.
//

import Combine
import XCTest
@testable import Kalorie

final class SupportViewModelTests: XCTestCase {

    private let small = TipProductDomain(id: "tip_small", displayPrice: "29 Kč")

    @MainActor
    func test_onAppear_loadsProducts() async {
        let sut = makeSUT(loadTipProducts: LoadTipProductsUseCaseFake(stubbedProducts: [small]))
        await sut.onAppear()
        XCTAssertEqual(sut.products, [small])
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onAppear_whenLoadingFails_showsRetryableAlert() async {
        let sut = makeSUT(loadTipProducts: LoadTipProductsUseCaseFake(shouldThrow: true))
        await sut.onAppear()
        XCTAssertTrue(sut.products.isEmpty)
        XCTAssertEqual(sut.alertItem?.title, L10n.Support.errorProductsUnavailable)
    }

    @MainActor
    func test_onAppear_whenNoProductsReturned_showsAlert() async {
        let sut = makeSUT()
        await sut.onAppear()
        XCTAssertEqual(sut.alertItem?.title, L10n.Support.errorProductsUnavailable)
    }

    @MainActor
    func test_onProductTapped_whenPurchased_showsThankYouWithoutAlertAndKeepsItUntilDismissed() async {
        let sut = makeSUT(purchaseTip: PurchaseTipUseCaseFake(stubbedOutcome: .purchased))
        var visibility: [Bool] = []
        let cancellable = sut.$isThankYouVisible.sink { visibility.append($0) }
        await sut.onProductTapped(small)
        cancellable.cancel()
        XCTAssertEqual(visibility, [false, true])
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onThankYouDismissed_hidesThankYou() async {
        let sut = makeSUT()
        await sut.onProductTapped(small)

        sut.onThankYouDismissed()

        XCTAssertFalse(sut.isThankYouVisible)
    }

    @MainActor
    func test_onProductTapped_whenMediumTipPurchased_choosesLunchAnimation() async {
        let sut = makeSUT()
        await sut.onProductTapped(TipProductDomain(id: Constants.Tips.mediumProductId, displayPrice: "79 Kč"))
        XCTAssertEqual(sut.thankYouAnimation, .lunch)
    }

    @MainActor
    func test_onProductTapped_whenLargeTipPurchased_choosesGrandmaAnimation() async {
        let sut = makeSUT()
        await sut.onProductTapped(TipProductDomain(id: Constants.Tips.largeProductId, displayPrice: "199 Kč"))
        XCTAssertEqual(sut.thankYouAnimation, .grandma)
    }

    @MainActor
    func test_onProductTapped_whenSmallTipPurchased_choosesBaguetteAnimation() async {
        let sut = makeSUT()
        await sut.onProductTapped(small)
        XCTAssertEqual(sut.thankYouAnimation, .baguettes)
    }

    @MainActor
    func test_onProductTapped_whenCancelled_showsNothing() async {
        let sut = makeSUT(purchaseTip: PurchaseTipUseCaseFake(stubbedOutcome: .cancelled))
        await sut.onProductTapped(small)
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onProductTapped_whenPending_showsPendingMessage() async {
        let sut = makeSUT(purchaseTip: PurchaseTipUseCaseFake(stubbedOutcome: .pending))
        await sut.onProductTapped(small)
        XCTAssertEqual(sut.alertItem?.title, L10n.Support.pendingMessage)
    }

    @MainActor
    func test_onProductTapped_whenPurchaseFails_showsError() async {
        let sut = makeSUT(purchaseTip: PurchaseTipUseCaseFake(shouldThrow: true))
        await sut.onProductTapped(small)
        XCTAssertEqual(sut.alertItem?.title, L10n.Support.errorPurchaseFailed)
    }

    @MainActor
    func test_onProductTapped_whileThankYouIsVisible_ignoresTheTap() async {
        let sut = makeSUT()
        await sut.onProductTapped(small)

        await sut.onProductTapped(TipProductDomain(id: "tip_large", displayPrice: "199 Kč"))

        XCTAssertEqual(sut.thankYouAnimation, .baguettes)
    }

    // MARK: - Helpers

    private func makeSUT(
        loadTipProducts: any LoadTipProductsUseCaseProtocol = LoadTipProductsUseCaseFake(),
        purchaseTip: any PurchaseTipUseCaseProtocol = PurchaseTipUseCaseFake()
    ) -> SupportViewModel {
        let sut = SupportViewModel(loadTipProducts: loadTipProducts, purchaseTip: purchaseTip)
        addTeardownBlock { [weak sut] in XCTAssertNil(sut) }
        return sut
    }
}
