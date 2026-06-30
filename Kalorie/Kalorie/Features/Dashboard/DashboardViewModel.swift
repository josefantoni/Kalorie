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
            if UserDefaults.standard.object(forKey: Constants.UserDefaultsKeys.firstOpen) == nil {
                mealTypes = try await setupDefaultMeals()
                UserDefaults.standard.set(true, forKey: Constants.UserDefaultsKeys.firstOpen)
            } else {
                mealTypes = try await fetchMealTypes()
            }
            foodsConsumed = try await fetchFoodsConsumed(for: selectedDay)
            state = .loaded
        } catch {
            state = .error(error)
        }
    }
}
