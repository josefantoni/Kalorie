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

    let food: FoodConsumedDomain

    private var savedWeight: Double
    private let updateFoodConsumed: any UpdateFoodConsumedUseCaseProtocol
    private let onFoodUpdated: () -> Void

    // MARK: - Init

    init(
        food: FoodConsumedDomain,
        updateFoodConsumed: any UpdateFoodConsumedUseCaseProtocol,
        onFoodUpdated: @escaping () -> Void
    ) {
        self.food = food
        self.weight = food.weight
        self.savedWeight = food.weight
        self.updateFoodConsumed = updateFoodConsumed
        self.onFoodUpdated = onFoodUpdated
    }

    // MARK: - Functions

    var scaledMacros: ScaledMacros {
        let ratio = food.weight > 0 ? weight / food.weight : 1
        return ScaledMacros(food: food, ratio: ratio)
    }

    var hasWeightChanged: Bool { weight != savedWeight }

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
