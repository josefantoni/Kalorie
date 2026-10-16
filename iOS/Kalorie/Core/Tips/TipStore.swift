//
//  TipStore.swift
//  Kalorie
//
//  Created by Josef Antoni on 15.10.2026.
//

import Foundation
import StoreKit

enum TipStoreError: Error {
    case unverifiedTransaction
}

protocol TipStoreProtocol {
    func products() async throws -> [TipProductDomain]
    func purchase(productId: String) async throws -> TipPurchaseOutcome
}

struct StoreKitTipStore: TipStoreProtocol {

    // MARK: - Functions

    func products() async throws -> [TipProductDomain] {
        let products = try await Product.products(for: Constants.Tips.productIds)
        return Constants.Tips.productIds.compactMap { id in
            products.first { $0.id == id }.map { TipProductDomain(id: $0.id, displayPrice: $0.displayPrice) }
        }
    }

    func purchase(productId: String) async throws -> TipPurchaseOutcome {
        guard let product = try await Product.products(for: [productId]).first else {
            return .cancelled
        }
        switch try await product.purchase() {
        case .success(let verification):
            switch verification {
            case .verified(let transaction):
                await transaction.finish()
                return .purchased
            case .unverified:
                throw TipStoreError.unverifiedTransaction
            }
        case .pending:
            return .pending
        case .userCancelled:
            return .cancelled
        @unknown default:
            return .cancelled
        }
    }
}

final class TipTransactionObserver {

    // MARK: - Properties

    private var task: Task<Void, Never>?

    // MARK: - Functions

    func start() {
        guard task == nil else { return }
        task = Task.detached {
            for await update in Transaction.updates {
                if case .verified(let transaction) = update {
                    await transaction.finish()
                }
            }
        }
    }
}
