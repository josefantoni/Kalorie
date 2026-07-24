//
//  FoodQuantityViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 24.07.2026.
//

import Foundation

enum FoodQuantityUnit: CaseIterable {
    case portions
    case grams

    var gramsPerUnit: Double {
        switch self {
        case .portions: return 100
        case .grams: return 1
        }
    }

    var defaultQuantity: Double {
        switch self {
        case .portions: return 1
        case .grams: return 100
        }
    }
}

final class FoodQuantityViewModel: ObservableObject {

    // MARK: - Properties

    @Published var unit: FoodQuantityUnit = .portions
    @Published var quantity: Double = 1
    @Published private(set) var state: LoadingState<Void> = .idle
    @Published var isAlertVisible = false
    @Published private(set) var alertTitle = ""

    let item: FoodItemDomain
    private let saveFoodConsumed: any SaveFoodConsumedUseCaseProtocol
    private let selectedDate: Date
    private let onSaved: () -> Void

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
        onSaved: @escaping () -> Void
    ) {
        self.item = item
        self.saveFoodConsumed = saveFoodConsumed
        self.selectedDate = selectedDate
        self.onSaved = onSaved
    }

    // MARK: - Functions

    func onUnitChanged() {
        quantity = unit.defaultQuantity
    }

    @MainActor
    func onConfirm() async {
        guard !state.isLoading else { return }
        state = .loading
        defer { state = .loaded }
        do {
            try await saveFoodConsumed(item, grams: grams, date: selectedDate)
            onSaved()
        } catch {
            alertTitle = L10n.Common.errorUnknown
            isAlertVisible = true
        }
    }
}
