//
//  LoadTipProductsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 15.10.2026.
//

import Foundation

protocol LoadTipProductsUseCaseProtocol {
    func callAsFunction() async throws -> [TipProductDomain]
}

struct LoadTipProductsUseCase: LoadTipProductsUseCaseProtocol {

    // MARK: - Properties

    private let store: any TipStoreProtocol

    // MARK: - Init

    init(store: any TipStoreProtocol) {
        self.store = store
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [TipProductDomain] {
        try await store.products()
    }
}

struct LoadTipProductsUseCaseFake: LoadTipProductsUseCaseProtocol {

    // MARK: - Properties

    var stubbedProducts: [TipProductDomain] = []
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction() async throws -> [TipProductDomain] {
        if shouldThrow { throw URLError(.notConnectedToInternet) }
        return stubbedProducts
    }
}
