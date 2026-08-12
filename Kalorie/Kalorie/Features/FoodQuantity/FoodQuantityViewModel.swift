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

final class FoodQuantityViewModel: ObservableObject {

    // MARK: - Properties

    @Published var unit: FoodQuantityUnit = .hundredGrams
    @Published var quantity: Double = 1
    @Published private(set) var state: LoadingState<Void> = .idle
    @Published var alertItem: AlertItem?
    @Published private(set) var isFavourite: Bool

    let item: FoodItemDomain
    private let saveFoodConsumed: any SaveFoodConsumedUseCaseProtocol
    private let addFavouriteFood: any AddFavouriteFoodUseCaseProtocol
    private let removeFavouriteFood: any RemoveFavouriteFoodUseCaseProtocol
    private let selectedDate: Date
    private let onSaved: () -> Void
    private let onFavouriteChanged: (String, Bool) -> Void

    var grams: Double { quantity * unit.gramsPerUnit }
    var scaledCalories: Int { Int(item.caloriesPerHundredGrams * grams / 100) }
    var scaledProtein: Double { item.protein * grams / 100 }
    var scaledCarbohydrate: Double { item.carbohydrate * grams / 100 }
    var scaledFat: Double { item.fat * grams / 100 }
    var scaledFiber: Double { item.fiber * grams / 100 }

    // MARK: - Init

    init(
        item: FoodItemDomain,
        saveFoodConsumed: any SaveFoodConsumedUseCaseProtocol,
        selectedDate: Date,
        isFavourite: Bool,
        addFavouriteFood: any AddFavouriteFoodUseCaseProtocol,
        removeFavouriteFood: any RemoveFavouriteFoodUseCaseProtocol,
        onSaved: @escaping () -> Void,
        onFavouriteChanged: @escaping (String, Bool) -> Void
    ) {
        self.item = item
        self.saveFoodConsumed = saveFoodConsumed
        self.selectedDate = selectedDate
        self.isFavourite = isFavourite
        self.addFavouriteFood = addFavouriteFood
        self.removeFavouriteFood = removeFavouriteFood
        self.onSaved = onSaved
        self.onFavouriteChanged = onFavouriteChanged
    }

    // MARK: - Functions

    func onUnitChanged(from oldUnit: FoodQuantityUnit, to newUnit: FoodQuantityUnit) {
        let currentGrams = quantity * oldUnit.gramsPerUnit
        quantity = max(1, (currentGrams / newUnit.gramsPerUnit).rounded())
    }

    @MainActor
    func onFavouriteToggled() async {
        let newValue = !isFavourite
        isFavourite = newValue
        do {
            if newValue {
                try await addFavouriteFood(item)
            } else {
                try await removeFavouriteFood(id: item.id)
            }
            onFavouriteChanged(item.id, newValue)
        } catch {
            isFavourite = !newValue
            alertItem = AlertItem(title: L10n.AddFood.errorFavouriteFailed)
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
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }
}
