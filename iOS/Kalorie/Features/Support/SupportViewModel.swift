//
//  SupportViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 15.10.2026.
//

import Foundation

final class SupportViewModel: ObservableObject {

    enum State {
        case idle
        case loading
        case purchasing
    }

    enum ThankYouAnimation {
        case baguettes
        case lunch
        case grandma
    }

    // MARK: - Properties

    @Published private(set) var state: State = .idle
    @Published private(set) var products: [TipProductDomain] = []
    @Published private(set) var isThankYouVisible = false
    @Published private(set) var thankYouAnimation = ThankYouAnimation.baguettes
    @Published var alertItem: AlertItem?

    private let loadTipProducts: any LoadTipProductsUseCaseProtocol
    private let purchaseTip: any PurchaseTipUseCaseProtocol

    // MARK: - Init

    init(
        loadTipProducts: any LoadTipProductsUseCaseProtocol,
        purchaseTip: any PurchaseTipUseCaseProtocol
    ) {
        self.loadTipProducts = loadTipProducts
        self.purchaseTip = purchaseTip
    }

    // MARK: - Functions

    @MainActor
    func onAppear() async {
        guard products.isEmpty else { return }
        state = .loading
        defer { state = .idle }
        do {
            products = try await loadTipProducts()
            if products.isEmpty {
                alertItem = AlertItem(title: L10n.Support.errorProductsUnavailable)
            }
        } catch {
            Log.error(error, category: Constants.LogCategory.support)
            alertItem = AlertItem(title: L10n.Support.errorProductsUnavailable)
        }
    }

    func onThankYouDismissed() {
        isThankYouVisible = false
    }

    @MainActor
    func onProductTapped(_ product: TipProductDomain) async {
        guard state == .idle, !isThankYouVisible else { return }
        state = .purchasing
        defer { state = .idle }
        do {
            switch try await purchaseTip(productId: product.id) {
            case .purchased:
                state = .idle
                switch product.id {
                case Constants.Tips.mediumProductId: thankYouAnimation = .lunch
                case Constants.Tips.largeProductId: thankYouAnimation = .grandma
                default: thankYouAnimation = .baguettes
                }
                isThankYouVisible = true
            case .pending:
                alertItem = AlertItem(title: L10n.Support.pendingMessage)
            case .cancelled:
                break
            }
        } catch {
            Log.error(error, category: Constants.LogCategory.support)
            alertItem = AlertItem(title: L10n.Support.errorPurchaseFailed)
        }
    }
}
