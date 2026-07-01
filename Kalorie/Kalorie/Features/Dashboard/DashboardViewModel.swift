//
//  DashboardViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 05.06.2024.
//

import Foundation

final class DashboardViewModel: ObservableObject {

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .loading
    @Published var mealTypes: [MealTypeDomain] = []
    @Published var foodsConsumed: [FoodConsumedDomain] = []
    @Published var selectedDay = Date.now
    @Published var showMealTypeSheet = false
    @Published var showAddFoodSheet = false
    @Published var showingAlert = false
    @Published var alertTitle = ""

    private let fetchMealTypes: any FetchMealTypesUseCaseProtocol
    private let fetchFoodsConsumed: any FetchFoodsConsumedUseCaseProtocol
    private let setupDefaultMeals: any SetupDefaultMealsUseCaseProtocol

    // MARK: - Init

    init(
        fetchMealTypes: any FetchMealTypesUseCaseProtocol,
        fetchFoodsConsumed: any FetchFoodsConsumedUseCaseProtocol,
        setupDefaultMeals: any SetupDefaultMealsUseCaseProtocol
    ) {
        self.fetchMealTypes = fetchMealTypes
        self.fetchFoodsConsumed = fetchFoodsConsumed
        self.setupDefaultMeals = setupDefaultMeals
    }

    // MARK: - Functions

    @MainActor
    func onAppear() async {
        let today = Date.now
        if !Calendar.current.isDate(selectedDay, inSameDayAs: today) {
            selectedDay = today
        }
        state = .loading
        do {
            var types = try await fetchMealTypes()
            if types.isEmpty {
                types = try await setupDefaultMeals()
            }
            mealTypes = types
            foodsConsumed = try await fetchFoodsConsumed(for: selectedDay)
            state = .loaded
        } catch {
            alertTitle = L10n.Common.errorUnknown
            showingAlert = true
            state = .loaded
        }
    }
}
