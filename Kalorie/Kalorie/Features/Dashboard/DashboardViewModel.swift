//
//  DashboardViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 05.06.2024.
//

import Foundation

struct DailyMacros {
    let calories: Int
    let protein: Double
    let carbs: Double
    let carbohydrateSugar: Double
    let fat: Double
    let fatUnsaturated: Double
    let fiber: Double
    let salt: Double
}

final class DashboardViewModel: ObservableObject {

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .loading
    @Published var mealTypes: [MealTypeDomain] = []
    @Published var foodsConsumed: [FoodConsumedDomain] = []
    @Published var selectedDay = Date.now
    @Published var showMealTypeSheet = false
    @Published var showAddFoodSheet = false
    @Published var showCalendarSheet = false
    @Published var showAccountSheet = false
    @Published var alertItem: AlertItem?
    @Published private(set) var activeDaysInMonth: Set<Int> = []

    private var monthCache: [String: [FoodConsumedDomain]] = [:]
    private var cachedMonthKeys: Set<String> = []

    private let fetchMealTypes: any FetchMealTypesUseCaseProtocol
    private let fetchFoodsConsumedForMonth: any FetchFoodsConsumedForMonthUseCaseProtocol
    private let setupDefaultMeals: any SetupDefaultMealsUseCaseProtocol
    private let confirmMealTypesEmpty: any ConfirmMealTypesEmptyUseCaseProtocol

    // MARK: - Init

    init(
        fetchMealTypes: any FetchMealTypesUseCaseProtocol,
        fetchFoodsConsumedForMonth: any FetchFoodsConsumedForMonthUseCaseProtocol,
        setupDefaultMeals: any SetupDefaultMealsUseCaseProtocol,
        confirmMealTypesEmpty: any ConfirmMealTypesEmptyUseCaseProtocol
    ) {
        self.fetchMealTypes = fetchMealTypes
        self.fetchFoodsConsumedForMonth = fetchFoodsConsumedForMonth
        self.setupDefaultMeals = setupDefaultMeals
        self.confirmMealTypesEmpty = confirmMealTypesEmpty
    }

    // MARK: - Functions

    var dailyMacros: DailyMacros {
        DailyMacros(
            calories: foodsConsumed.reduce(0) { $0 + $1.calories },
            protein: foodsConsumed.reduce(0) { $0 + $1.protein },
            carbs: foodsConsumed.reduce(0) { $0 + $1.carbohydrate },
            carbohydrateSugar: foodsConsumed.reduce(0) { $0 + $1.carbohydrateSugar },
            fat: foodsConsumed.reduce(0) { $0 + $1.fat },
            fatUnsaturated: foodsConsumed.reduce(0) { $0 + $1.fatUnsaturated },
            fiber: foodsConsumed.reduce(0) { $0 + $1.fiber },
            salt: foodsConsumed.reduce(0) { $0 + $1.salt }
        )
    }

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

    @MainActor
    func onAppear() async {
        selectedDay = Date.now
        state = .loading
        do {
            try await refreshMealTypes()
            try await loadMonth(for: selectedDay)
            foodsConsumed = foodsFromCache(for: selectedDay)
            state = .loaded
        } catch {
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
            state = .loaded
        }
    }

    @MainActor
    func onRefresh() async {
        do {
            try await refreshMealTypes()
            invalidateCache(for: selectedDay)
            try await loadMonth(for: selectedDay)
            foodsConsumed = foodsFromCache(for: selectedDay)
        } catch {
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }

    @MainActor
    func onFoodConsumedUpdated() async {
        do {
            invalidateCache(for: selectedDay)
            try await loadMonth(for: selectedDay)
            foodsConsumed = foodsFromCache(for: selectedDay)
        } catch {
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }

    @MainActor
    func onMealTypesChanged() async {
        do {
            try await refreshMealTypes()
        } catch {
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }

    @MainActor
    func onDayChanged(_ date: Date) async {
        await loadFoods(for: date)
    }

    @MainActor
    func onDaySelected(_ date: Date) async {
        selectedDay = date
        showCalendarSheet = false
        await loadFoods(for: date)
    }

    @MainActor
    func onCalendarMonthChanged(to month: Date) async {
        let key = monthCacheKey(for: month)
        if cachedMonthKeys.contains(key) {
            activeDaysInMonth = computeActiveDays(for: month)
        } else {
            do {
                try await loadMonth(for: month)
            } catch {
                alertItem = AlertItem(title: L10n.Common.errorUnknown)
            }
        }
    }

    // MARK: - Private

    @MainActor
    private func refreshMealTypes() async throws {
        var types = try await fetchMealTypes()
        if
            types.isEmpty,
            try await confirmMealTypesEmpty()
        {
            types = try await setupDefaultMeals()
        }
        mealTypes = types
    }

    private func foodFallsIn(mealType: MealTypeDomain, food: FoodConsumedDomain) -> Bool {
        let calendar = Calendar.current
        func minutes(from date: Date) -> Int {
            let components = calendar.dateComponents([.hour, .minute], from: date)
            return (components.hour ?? 0) * 60 + (components.minute ?? 0)
        }
        let foodMinutes = minutes(from: food.date)
        return foodMinutes >= minutes(from: mealType.startTime) && foodMinutes < minutes(from: mealType.endTime)
    }

    @MainActor
    private func loadFoods(for date: Date) async {
        let key = monthCacheKey(for: date)
        if cachedMonthKeys.contains(key) {
            foodsConsumed = foodsFromCache(for: date)
            activeDaysInMonth = computeActiveDays(for: date)
        } else {
            do {
                try await loadMonth(for: date)
                foodsConsumed = foodsFromCache(for: date)
            } catch {
                alertItem = AlertItem(title: L10n.Common.errorUnknown)
            }
        }
    }

    @MainActor
    private func loadMonth(for date: Date) async throws {
        let foods = try await fetchFoodsConsumedForMonth(for: date)
        populateCache(with: foods, for: date)
        activeDaysInMonth = computeActiveDays(for: date)
    }

    private func monthCacheKey(for date: Date) -> String {
        date.formatDateStyle(with: "yyyy-MM")
    }

    private func dayCacheKey(for date: Date) -> String {
        date.formatDateStyle(with: "yyyy-MM-dd")
    }

    private func populateCache(with foods: [FoodConsumedDomain], for month: Date) {
        let key = monthCacheKey(for: month)
        monthCache = monthCache.filter { !$0.key.hasPrefix(key) }
        cachedMonthKeys.insert(key)
        for food in foods {
            let dayKey = dayCacheKey(for: food.date)
            monthCache[dayKey, default: []].append(food)
        }
    }

    private func foodsFromCache(for date: Date) -> [FoodConsumedDomain] {
        monthCache[dayCacheKey(for: date)] ?? []
    }

    private func computeActiveDays(for month: Date) -> Set<Int> {
        let key = monthCacheKey(for: month)
        var result = Set<Int>()
        for (dayKey, foods) in monthCache where dayKey.hasPrefix(key) && !foods.isEmpty {
            let parts = dayKey.split(separator: "-")
            if parts.count == 3, let day = Int(parts[2]) {
                result.insert(day)
            }
        }
        return result
    }

    private func invalidateCache(for date: Date) {
        let key = monthCacheKey(for: date)
        monthCache = monthCache.filter { !$0.key.hasPrefix(key) }
        cachedMonthKeys.remove(key)
    }
}
