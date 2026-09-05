//
//  DashboardViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 05.06.2024.
//

import Foundation
import MacroKit

struct DailyMacros {
    let calories: Int
    let protein: Double
    let carbs: Double
    let carbohydrateSugar: Double
    let fat: Double
    let fatUnsaturated: Double
    let fiber: Double
    let salt: Double

    init(
        calories: Int,
        protein: Double,
        carbs: Double,
        carbohydrateSugar: Double,
        fat: Double,
        fatUnsaturated: Double,
        fiber: Double,
        salt: Double
    ) {
        self.calories = calories
        self.protein = protein
        self.carbs = carbs
        self.carbohydrateSugar = carbohydrateSugar
        self.fat = fat
        self.fatUnsaturated = fatUnsaturated
        self.fiber = fiber
        self.salt = salt
    }

    init(foods: [FoodConsumedDomain]) {
        let macros = foods.map {
            Macros(
                calories: Int32($0.calories),
                protein: $0.protein,
                carbohydrate: $0.carbohydrate,
                carbohydrateSugar: $0.carbohydrateSugar,
                fat: $0.fat,
                fatUnsaturated: $0.fatUnsaturated,
                fiber: $0.fiber ?? 0,
                salt: $0.salt
            )
        }
        let total = MacrosKt.total(macros)
        calories = Int(total.calories)
        protein = total.protein
        carbs = total.carbohydrate
        carbohydrateSugar = total.carbohydrateSugar
        fat = total.fat
        fatUnsaturated = total.fatUnsaturated
        fiber = total.fiber
        salt = total.salt
    }
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
    @Published var showMyCreatedMealEditor = false
    @Published var alertItem: AlertItem?
    @Published var isDeleteConfirmationVisible = false
    @Published private(set) var activeDaysInMonth: Set<Int> = []

    private var isMyCreatedMealEditorPending = false
    private var isViewingToday = true
    private var hasCompletedInitialLoad = false
    private var monthCache: [String: [FoodConsumedDomain]] = [:]
    private var cachedMonthKeys: Set<String> = []
    private var foodPendingDeletion: FoodConsumedDomain?
    private var dayChangeObserver: NSObjectProtocol?

    private let fetchMealTypes: any FetchMealTypesUseCaseProtocol
    private let fetchFoodsConsumedForMonth: any FetchFoodsConsumedForMonthUseCaseProtocol
    private let setupDefaultMeals: any SetupDefaultMealsUseCaseProtocol
    private let confirmMealTypesEmpty: any ConfirmMealTypesEmptyUseCaseProtocol
    private let deleteFoodConsumed: any DeleteFoodConsumedUseCaseProtocol

    // MARK: - Init

    init(
        fetchMealTypes: any FetchMealTypesUseCaseProtocol,
        fetchFoodsConsumedForMonth: any FetchFoodsConsumedForMonthUseCaseProtocol,
        setupDefaultMeals: any SetupDefaultMealsUseCaseProtocol,
        confirmMealTypesEmpty: any ConfirmMealTypesEmptyUseCaseProtocol,
        deleteFoodConsumed: any DeleteFoodConsumedUseCaseProtocol
    ) {
        self.fetchMealTypes = fetchMealTypes
        self.fetchFoodsConsumedForMonth = fetchFoodsConsumedForMonth
        self.setupDefaultMeals = setupDefaultMeals
        self.confirmMealTypesEmpty = confirmMealTypesEmpty
        self.deleteFoodConsumed = deleteFoodConsumed
        observeDayChange()
    }

    deinit {
        if let dayChangeObserver {
            NotificationCenter.default.removeObserver(dayChangeObserver)
        }
    }

    // MARK: - Functions

    var dailyMacros: DailyMacros { DailyMacros(foods: foodsConsumed) }

    var groupedFoods: [(mealType: MealTypeDomain?, foods: [FoodConsumedDomain])] {
        var foodsByMealTypeId: [String: [FoodConsumedDomain]] = [:]
        var unassigned: [FoodConsumedDomain] = []

        for food in foodsConsumed {
            if let resolvedMealTypeId = mealTypes.resolvedMealTypeId(for: food) {
                foodsByMealTypeId[resolvedMealTypeId, default: []].append(food)
            } else {
                unassigned.append(food)
            }
        }

        var result: [(mealType: MealTypeDomain?, foods: [FoodConsumedDomain])] = mealTypes
            .sorted { $0.startTime < $1.startTime }
            .compactMap { mealType in
                foodsByMealTypeId[mealType.id].map { (mealType: mealType, foods: $0) }
            }
        if !unassigned.isEmpty {
            result.append((mealType: nil, foods: unassigned))
        }

        return result
    }

    @MainActor
    func onAppear() async {
        guard !hasCompletedInitialLoad else { return }
        selectedDay = Date.now
        isViewingToday = true
        state = .loading
        do {
            try await refreshMealTypes()
            try await loadMonth(for: selectedDay)
            foodsConsumed = foodsFromCache(for: selectedDay)
            state = .loaded
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            alertItem = unknownErrorAlertItem(for: error)
            state = .loaded
        }
        hasCompletedInitialLoad = true
    }

    @MainActor
    func onRefresh() async {
        guard hasCompletedInitialLoad else { return }
        do {
            advanceSelectedDayIfNeeded()
            try await refreshMealTypes()
            invalidateCache(for: selectedDay)
            try await loadMonth(for: selectedDay)
            foodsConsumed = foodsFromCache(for: selectedDay)
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            alertItem = unknownErrorAlertItem(for: error)
        }
    }

    @MainActor
    func onFoodConsumedUpdated() async {
        do {
            invalidateCache(for: selectedDay)
            try await loadMonth(for: selectedDay)
            foodsConsumed = foodsFromCache(for: selectedDay)
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            alertItem = unknownErrorAlertItem(for: error)
        }
    }

    func onCreateMealRequested() {
        isMyCreatedMealEditorPending = true
    }

    func onDeleteRequested(_ food: FoodConsumedDomain) {
        foodPendingDeletion = food
        isDeleteConfirmationVisible = true
    }

    @MainActor
    func onDeleteConfirmed() async {
        guard let food = foodPendingDeletion else { return }
        foodPendingDeletion = nil
        do {
            try await deleteFoodConsumed(id: food.id)
            invalidateCache(for: selectedDay)
            try await loadMonth(for: selectedDay)
            foodsConsumed = foodsFromCache(for: selectedDay)
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            alertItem = AlertItem(title: L10n.Dashboard.errorDeleteFailed)
        }
    }

    func onAddFoodSheetDismissed() {
        guard isMyCreatedMealEditorPending else { return }
        isMyCreatedMealEditorPending = false
        showMyCreatedMealEditor = true
    }

    @MainActor
    func onMealTypesChanged() async {
        do {
            try await refreshMealTypes()
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            alertItem = unknownErrorAlertItem(for: error)
        }
    }

    @MainActor
    func onDayChanged(_ date: Date) async {
        isViewingToday = Calendar.current.isDate(date, inSameDayAs: Date.now)
        await loadFoods(for: date)
    }

    @MainActor
    func onDaySelected(_ date: Date) async {
        isViewingToday = Calendar.current.isDate(date, inSameDayAs: Date.now)
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
                Log.error(error, category: Constants.LogCategory.dashboard)
                alertItem = unknownErrorAlertItem(for: error)
            }
        }
    }

    // MARK: - Private

    private func isOffline(_ error: Error) -> Bool {
        (error as? FirestoreDataProviderError) == .unreachable
    }

    private func unknownErrorAlertItem(for error: Error) -> AlertItem {
        if isOffline(error) {
            AlertItem(title: L10n.Common.errorOffline, message: L10n.Common.errorOfflineMessage)
        } else {
            AlertItem(title: L10n.Common.errorUnknown, message: L10n.Common.errorUnknownMessage)
        }
    }

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

    private func advanceSelectedDayIfNeeded() {
        guard isViewingToday else { return }
        guard !Calendar.current.isDate(selectedDay, inSameDayAs: Date.now) else { return }
        selectedDay = Date.now
    }

    private func observeDayChange() {
        dayChangeObserver = NotificationCenter.default.addObserver(
            forName: .NSCalendarDayChanged,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in await self?.onRefresh() }
        }
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
                Log.error(error, category: Constants.LogCategory.dashboard)
                alertItem = unknownErrorAlertItem(for: error)
            }
        }
    }

    @MainActor
    private func loadMonth(for date: Date) async throws {
        let foods = try await fetchFoodsConsumedForMonth(for: date)
        populateCache(with: foods, for: date)
        activeDaysInMonth = computeActiveDays(for: date)
    }

    @MainActor
    private func monthCacheKey(for date: Date) -> String {
        date.formatCacheKey(with: "yyyy-MM")
    }

    @MainActor
    private func dayCacheKey(for date: Date) -> String {
        date.formatCacheKey(with: "yyyy-MM-dd")
    }

    @MainActor
    private func populateCache(with foods: [FoodConsumedDomain], for month: Date) {
        let key = monthCacheKey(for: month)
        monthCache = monthCache.filter { !$0.key.hasPrefix(key) }
        cachedMonthKeys.insert(key)
        for food in foods {
            let dayKey = dayCacheKey(for: food.date)
            monthCache[dayKey, default: []].append(food)
        }
    }

    @MainActor
    private func foodsFromCache(for date: Date) -> [FoodConsumedDomain] {
        monthCache[dayCacheKey(for: date)] ?? []
    }

    @MainActor
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

    @MainActor
    private func invalidateCache(for date: Date) {
        let key = monthCacheKey(for: date)
        monthCache = monthCache.filter { !$0.key.hasPrefix(key) }
        cachedMonthKeys.remove(key)
    }
}
