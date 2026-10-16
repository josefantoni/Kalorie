//
//  PurchaseTipUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 15.10.2026.
//

import Foundation

protocol PurchaseTipUseCaseProtocol {
    func callAsFunction(productId: String) async throws -> TipPurchaseOutcome
}

struct PurchaseTipUseCase: PurchaseTipUseCaseProtocol {

    // MARK: - Properties

    private let store: any TipStoreProtocol

    // MARK: - Init

    init(store: any TipStoreProtocol) {
        self.store = store
    }

    // MARK: - Functions

    func callAsFunction(productId: String) async throws -> TipPurchaseOutcome {
        try await store.purchase(productId: productId)
    }
}

struct PurchaseTipUseCaseFake: PurchaseTipUseCaseProtocol {

    // MARK: - Properties

    var stubbedOutcome: TipPurchaseOutcome = .purchased
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(productId: String) async throws -> TipPurchaseOutcome {
        if shouldThrow { throw URLError(.notConnectedToInternet) }
        return stubbedOutcome
    }
}
