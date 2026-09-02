//
//  FoodConsumedDetailViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.07.2026.
//

import Foundation

final class FoodConsumedDetailViewModel: ObservableObject, FavouriteToggling {

    // MARK: - Properties

    @Published var weight: Double
    @Published private(set) var state: LoadingState<Void> = .idle
    @Published private(set) var showCheckmark = false
    @Published var alertItem: AlertItem?
    @Published var isFavourite = false
    @Published var isTogglingFavourite = false
    @Published private(set) var catalogueItem: FoodItemDomain?

    let food: FoodConsumedDomain

    private var savedWeight: Double
    private let updateFoodConsumed: any UpdateFoodConsumedUseCaseProtocol
    private let isFavouriteFood: any IsFavouriteFoodUseCaseProtocol
    private let addFavouriteFood: any AddFavouriteFoodUseCaseProtocol
    private let removeFavouriteFood: any RemoveFavouriteFoodUseCaseProtocol
    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    private let fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol
    private let onFoodUpdated: () -> Void

    var canShowFavouriteButton: Bool { isFavourite || catalogueItem != nil }
    var canToggleFavourite: Bool { !isTogglingFavourite && canShowFavouriteButton }

    // MARK: - Init

    init(
        food: FoodConsumedDomain,
        updateFoodConsumed: any UpdateFoodConsumedUseCaseProtocol,
        isFavouriteFood: any IsFavouriteFoodUseCaseProtocol,
        addFavouriteFood: any AddFavouriteFoodUseCaseProtocol,
        removeFavouriteFood: any RemoveFavouriteFoodUseCaseProtocol,
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol,
        fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol,
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
        self.fetchFoodByBarcodeExternally = fetchFoodByBarcodeExternally
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
        async let favourite = loadIsFavourite()
        async let catalogueItem = loadCatalogueItem()
        isFavourite = await favourite
        self.catalogueItem = await catalogueItem
    }

    private func loadIsFavourite() async -> Bool {
        guard food.foodItemKind != .createdMeal else { return false }
        do {
            return try await isFavouriteFood(id: food.foodItemId)
        } catch {
            Log.warning(error, category: Constants.LogCategory.dashboard)
            return false
        }
    }

    private func loadCatalogueItem() async -> FoodItemDomain? {
        do {
            switch food.foodItemKind {
            case .catalogue:
                return try await fetchFoodItemByBarcode(barcode: food.foodItemId)
            case .external:
                return try await fetchFoodByBarcodeExternally(barcode: food.foodItemId)
            case .createdMeal:
                return nil
            }
        } catch {
            Log.warning(error, category: Constants.LogCategory.dashboard)
            return nil
        }
    }

    @MainActor
    func onFavouriteToggled() async {
        await toggleFavourite(
            item: catalogueItem,
            removalId: food.foodItemId,
            addFavouriteFood: addFavouriteFood,
            removeFavouriteFood: removeFavouriteFood
        )
    }

    @MainActor
    func onSave() async {
        guard !state.isLoading else { return }
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
        } catch UpdateFoodConsumedError.invalidWeight {
            alertItem = AlertItem(title: L10n.AddFood.errorInvalidWeight)
            state = .loaded
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
            state = .loaded
        }
    }
}
