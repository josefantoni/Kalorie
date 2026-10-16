//
//  SupportConfigurator.swift
//  Kalorie
//
//  Created by Josef Antoni on 15.10.2026.
//

import Foundation

struct SupportConfigurator {

    // MARK: - Functions

    func createView() -> SupportView {
        let store = StoreKitTipStore()
        return SupportView(
            viewModel: SupportViewModel(
                loadTipProducts: LoadTipProductsUseCase(store: store),
                purchaseTip: PurchaseTipUseCase(store: store)
            )
        )
    }
}
