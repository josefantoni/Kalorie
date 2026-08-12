//
//  FoodConsumedDetailViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.07.2026.
//

import Foundation

final class FoodConsumedDetailViewModel: ObservableObject {

    // MARK: - Properties

    @Published var weight: Double
    @Published private(set) var state: LoadingState<Void> = .idle
    @Published private(set) var showCheckmark = false
    @Published var alertItem: AlertItem?
    @Published private(set) var isFavourite = false
    @Published private(set) var catalogueItem: FoodItemDomain?

    let food: FoodConsumedDomain

    private var savedWeight: Double
    private let updateFoodConsumed: any UpdateFoodConsumedUseCaseProtocol
    private let isFavouriteFood: any IsFavouriteFoodUseCaseProtocol
    private let addFavouriteFood: any AddFavouriteFoodUseCaseProtocol
    private let removeFavouriteFood: any RemoveFavouriteFoodUseCaseProtocol
    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    private let onFoodUpdated: () -> Void

    var canToggleFavourite: Bool { isFavourite || catalogueItem != nil }

    // MARK: - Init

    init(
        food: FoodConsumedDomain,
        updateFoodConsumed: any UpdateFoodConsumedUseCaseProtocol,
        isFavouriteFood: any IsFavouriteFoodUseCaseProtocol,
        addFavouriteFood: any AddFavouriteFoodUseCaseProtocol,
        removeFavouriteFood: any RemoveFavouriteFoodUseCaseProtocol,
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol,
        onFoodUpdated: @escaping () -> Void
    ) {
        self.food = food
        self.weight = food.weight
        self.savedWeight = food.weight
        self.updateFoodConsumed = updateFoodConsumed
        self.isFavouriteFood = isFavouriteFood
        self.addFavouriteFood = addFavouriteFood
        self.removeFavouriteFood = removeFavouriteFood
        self.fetchFoodItemByBarcode = fetchFoodItemByBarcode
        self.onFoodUpdated = onFoodUpdated
    }

    // MARK: - Functions

    var scaledMacros: ScaledMacros {
        let ratio = food.weight > 0 ? weight / food.weight : 1
        return ScaledMacros(food: food, ratio: ratio)
    }

    var hasWeightChanged: Bool { weight != savedWeight }

    @MainActor
    func onAppear() async {
        isFavourite = (try? await isFavouriteFood(id: food.foodItemId)) ?? false
        catalogueItem = try? await fetchFoodItemByBarcode(barcode: food.foodItemId)
    }

    @MainActor
    func onFavouriteToggled() async {
        let newValue = !isFavourite
        isFavourite = newValue
        do {
            if newValue {
                guard let catalogueItem else { return }
                try await addFavouriteFood(catalogueItem)
            } else {
                try await removeFavouriteFood(id: food.foodItemId)
            }
        } catch {
            isFavourite = !newValue
            alertItem = AlertItem(title: L10n.AddFood.errorFavouriteFailed)
        }
    }

    @MainActor
    func onSave() async {
        guard weight > 0 else {
            alertItem = AlertItem(title: L10n.AddFood.errorInvalidWeight)
            return
        }
        state = .loading
        do {
            try await updateFoodConsumed(food, newWeight: weight)
            savedWeight = weight
            onFoodUpdated()
            state = .loaded
            showCheckmark = true
            try? await Task.sleep(for: .seconds(2))
            showCheckmark = false
        } catch {
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
            state = .loaded
        }
    }
}
