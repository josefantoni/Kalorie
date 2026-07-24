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

    var groupedFoods: [(mealType: MealTypeDomain?, foods: [FoodConsumedDomain])] {
        var result: [(mealType: MealTypeDomain?, foods: [FoodConsumedDomain])] = []
        var assignedIds = Set<String>()

        for mealType in mealTypes.sorted(by: { $0.startTime < $1.startTime }) {
            let matching = foodsConsumed.filter { foodFallsIn(mealType: mealType, food: $0) }
            guard !matching.isEmpty else { continue }
            result.append((mealType: mealType, foods: matching))
            matching.forEach { assignedIds.insert($0.id) }
        }

        let unassigned = foodsConsumed.filter { !assignedIds.contains($0.id) }
        if !unassigned.isEmpty {
            result.append((mealType: nil, foods: unassigned))
        }

        return result
    }

    private func foodFallsIn(mealType: MealTypeDomain, food: FoodConsumedDomain) -> Bool {
        let calendar = Calendar.current
        func minutes(from date: Date) -> Int {
            let c = calendar.dateComponents([.hour, .minute], from: date)
            return (c.hour ?? 0) * 60 + (c.minute ?? 0)
        }
        let foodMinutes = minutes(from: food.date)
        return foodMinutes >= minutes(from: mealType.startTime) && foodMinutes < minutes(from: mealType.endTime)
    }

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
