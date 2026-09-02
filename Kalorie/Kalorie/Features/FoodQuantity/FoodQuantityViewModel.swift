//
//  FoodQuantityViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 24.07.2026.
//

import Foundation

enum FoodQuantityUnit: CaseIterable {
    case hundredGrams
    case grams

    var gramsPerUnit: Double {
        switch self {
        case .hundredGrams: return 100
        case .grams: return 1
        }
    }
}

final class FoodQuantityViewModel: ObservableObject, FavouriteToggling {

    // MARK: - Properties

    @Published var unit: FoodQuantityUnit
    @Published var quantity: Double
    @Published private(set) var state: LoadingState<Void> = .idle
    @Published var alertItem: AlertItem?
    @Published var isFavourite: Bool
    @Published var isTogglingFavourite = false

    let item: FoodItemDomain
    private let saveFoodConsumed: any SaveFoodConsumedUseCaseProtocol
    private let addFavouriteFood: any AddFavouriteFoodUseCaseProtocol
    private let removeFavouriteFood: any RemoveFavouriteFoodUseCaseProtocol
    private let selectedDate: Date
    private let onSaved: () -> Void
    private let onFavouriteChanged: (String, Bool) -> Void

    var grams: Double { quantity * unit.gramsPerUnit }

    private var scaledMacros: ScaledMacros { item.scaled(toGrams: grams) }

    var scaledCalories: Int { scaledMacros.calories }
    var scaledProtein: Double { scaledMacros.protein }
    var scaledCarbohydrate: Double { scaledMacros.carbohydrate }
    var scaledFat: Double { scaledMacros.fat }
    var scaledFiber: Double { scaledMacros.fiber }

    // MARK: - Init

    init(
        item: FoodItemDomain,
        saveFoodConsumed: any SaveFoodConsumedUseCaseProtocol,
        selectedDate: Date,
        isFavourite: Bool,
        addFavouriteFood: any AddFavouriteFoodUseCaseProtocol,
        removeFavouriteFood: any RemoveFavouriteFoodUseCaseProtocol,
        onSaved: @escaping () -> Void,
        onFavouriteChanged: @escaping (String, Bool) -> Void,
        quantity: Double = 1,
        unit: FoodQuantityUnit = .hundredGrams
    ) {
        self.item = item
        self.saveFoodConsumed = saveFoodConsumed
        self.selectedDate = selectedDate
        self.isFavourite = isFavourite
        self.addFavouriteFood = addFavouriteFood
        self.removeFavouriteFood = removeFavouriteFood
        self.onSaved = onSaved
        self.onFavouriteChanged = onFavouriteChanged
        self.quantity = quantity
        self.unit = unit
    }

    // MARK: - Functions

    func onUnitChanged(from oldUnit: FoodQuantityUnit, to newUnit: FoodQuantityUnit) {
        let currentGrams = quantity * oldUnit.gramsPerUnit
        quantity = currentGrams / newUnit.gramsPerUnit
    }

    @MainActor
    func onFavouriteToggled() async {
        let itemId = item.id
        await toggleFavourite(
            item: item,
            removalId: itemId,
            addFavouriteFood: addFavouriteFood,
            removeFavouriteFood: removeFavouriteFood
        ) { [weak self] newValue in
            self?.onFavouriteChanged(itemId, newValue)
        }
    }

    @MainActor
    func onConfirm() async {
        guard !state.isLoading else { return }
        guard grams > 0 else {
            alertItem = AlertItem(title: L10n.FoodQuantity.errorInvalidQuantity)
            return
        }
        state = .loading
        defer { state = .loaded }
        do {
            try await saveFoodConsumed(item, grams: grams, date: selectedDate)
            onSaved()
        } catch {
            Log.error(error, category: Constants.LogCategory.foodQuantity)
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }
}
