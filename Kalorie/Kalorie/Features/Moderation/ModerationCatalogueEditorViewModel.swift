//
//  ModerationCatalogueEditorViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation

final class ModerationCatalogueEditorViewModel: ObservableObject {

    // MARK: - Properties

    @Published var barcodeQuery = ""
    @Published private(set) var loadedItem: FoodItemDomain?
    @Published var formInput = FoodItemFormInput()
    @Published private(set) var state: LoadingState<Void> = .idle
    @Published var alertItem: AlertItem?
    @Published private(set) var didSave = false

    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    private let updateFoodItem: any UpdateFoodItemUseCaseProtocol

    // MARK: - Init

    init(
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol,
        updateFoodItem: any UpdateFoodItemUseCaseProtocol
    ) {
        self.fetchFoodItemByBarcode = fetchFoodItemByBarcode
        self.updateFoodItem = updateFoodItem
    }

    // MARK: - Functions

    @MainActor
    func onSearchTapped() async {
        guard !barcodeQuery.isEmpty else { return }
        state = .loading
        defer { state = .loaded }
        do {
            guard let item = try await fetchFoodItemByBarcode(barcode: barcodeQuery) else {
                loadedItem = nil
                alertItem = AlertItem(title: L10n.AddFood.errorBarcodeNotFound)
                return
            }
            loadedItem = item
            formInput = FoodItemFormInput(item: item)
            didSave = false
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }

    @MainActor
    func onSaveTapped() async {
        state = .loading
        defer { state = .loaded }
        let item = formInput.asFoodItemDomain(date: loadedItem?.date ?? .now)
        do {
            try await updateFoodItem(item)
            loadedItem = item
            didSave = true
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            switch error as? UpdateFoodItemError {
            case .invalidCode:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidCode)
            case .invalidName:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidName)
            case .invalidCalories:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidCalories)
            case .invalidWeight:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidWeight)
            case .invalidPortion(let portionError):
                alertItem = AlertItem(title: portionError.alertTitle)
            case nil:
                alertItem = AlertItem(title: L10n.Common.errorUnknown)
            }
        }
    }

}
