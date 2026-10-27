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
    let fatSaturated: Double
    let fiber: Double
    let salt: Double

    init(
        calories: Int,
        protein: Double,
        carbs: Double,
        carbohydrateSugar: Double,
        fat: Double,
        fatSaturated: Double,
        fiber: Double,
        salt: Double
    ) {
        self.calories = calories
        self.protein = protein
        self.carbs = carbs
        self.carbohydrateSugar = carbohydrateSugar
        self.fat = fat
        self.fatSaturated = fatSaturated
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
                fatSaturated: $0.fatSaturated ?? 0,
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
        fatSaturated = total.fatSaturated
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
    @Published var showSettings = false
    @Published var showAddFoodSheet = false
    @Published var showCalendarSheet = false
    @Published var showAccountSheet = false
    @Published var alertItem: AlertItem?
    @Published var isDeleteConfirmationVisible = false
    @Published var copyPopoverIndex: Int?
    @Published var macroPopoverIndex: Int?
    @Published private(set) var isSignInSpotlightVisible = false
    @Published var copyTargetDay = Date.now
    @Published var copyTargetMealTypeId: String?
    @Published private(set) var isCopying = false
    @Published private(set) var showCopyCheckmark = false
    @Published private(set) var activeDaysInMonth: Set<Int> = []
    @Published private(set) var moveFeedbackTrigger = 0
    @Published private(set) var recentWeights: [BodyWeightDomain] = []
    @Published private(set) var appSettings = AppSettingsDomain.defaults
    @Published private(set) var hasLoadedSettings = false
    @Published var isWeightPopoverVisible = false
    @Published var weightPickerKilograms = Constants.BodyWeight.defaultKilograms
    @Published private(set) var isSavingWeight = false
    @Published private(set) var showWeightCheckmark = false

    private var activeDaysMonth = Date.now
    private var isViewingToday = true
    private var hasCompletedInitialLoad = false
    private var monthCache: [String: [FoodConsumedDomain]] = [:]
    private var cachedMonthKeys: Set<String> = []
    private var weightCache: [String: [BodyWeightDomain]] = [:]
    private var foodPendingDeletion: FoodConsumedDomain?
    private var dayChangeObserver: NSObjectProtocol?

    private let fetchMealTypes: any FetchMealTypesUseCaseProtocol
    private let fetchFoodsConsumedForMonth: any FetchFoodsConsumedForMonthUseCaseProtocol
    private let fetchFoodsConsumedInRange: any FetchFoodsConsumedInRangeUseCaseProtocol
    private let setupDefaultMeals: any SetupDefaultMealsUseCaseProtocol
    private let confirmMealTypesEmpty: any ConfirmMealTypesEmptyUseCaseProtocol
    private let deleteFoodConsumed: any DeleteFoodConsumedUseCaseProtocol
    private let copyFoodsConsumed: any CopyFoodsConsumedUseCaseProtocol
    private let assignFoodMealType: any AssignFoodMealTypeUseCaseProtocol
    private let fetchBodyWeights: any FetchBodyWeightsUseCaseProtocol
    private let fetchAppSettings: any FetchAppSettingsUseCaseProtocol
    private let saveBodyWeight: any SaveBodyWeightUseCaseProtocol
    private let authProvider: any AuthProviderProtocol
    private let signInSpotlightStore: any SignInSpotlightStoreProtocol
    private let now: () -> Date

    // MARK: - Init

    init(
        fetchMealTypes: any FetchMealTypesUseCaseProtocol,
        fetchFoodsConsumedForMonth: any FetchFoodsConsumedForMonthUseCaseProtocol,
        fetchFoodsConsumedInRange: any FetchFoodsConsumedInRangeUseCaseProtocol,
        setupDefaultMeals: any SetupDefaultMealsUseCaseProtocol,
        confirmMealTypesEmpty: any ConfirmMealTypesEmptyUseCaseProtocol,
        deleteFoodConsumed: any DeleteFoodConsumedUseCaseProtocol,
        copyFoodsConsumed: any CopyFoodsConsumedUseCaseProtocol,
        assignFoodMealType: any AssignFoodMealTypeUseCaseProtocol,
        fetchBodyWeights: any FetchBodyWeightsUseCaseProtocol,
        fetchAppSettings: any FetchAppSettingsUseCaseProtocol,
        saveBodyWeight: any SaveBodyWeightUseCaseProtocol,
        authProvider: any AuthProviderProtocol,
        signInSpotlightStore: any SignInSpotlightStoreProtocol,
        now: @escaping () -> Date = { Date.now }
    ) {
        self.authProvider = authProvider
        self.signInSpotlightStore = signInSpotlightStore
        self.now = now
        self.fetchMealTypes = fetchMealTypes
        self.fetchFoodsConsumedForMonth = fetchFoodsConsumedForMonth
        self.fetchFoodsConsumedInRange = fetchFoodsConsumedInRange
        self.setupDefaultMeals = setupDefaultMeals
        self.confirmMealTypesEmpty = confirmMealTypesEmpty
        self.deleteFoodConsumed = deleteFoodConsumed
        self.copyFoodsConsumed = copyFoodsConsumed
        self.assignFoodMealType = assignFoodMealType
        self.fetchBodyWeights = fetchBodyWeights
        self.fetchAppSettings = fetchAppSettings
        self.saveBodyWeight = saveBodyWeight
        observeDayChange()
    }

    deinit {
        if let dayChangeObserver {
            NotificationCenter.default.removeObserver(dayChangeObserver)
        }
    }

    // MARK: - Functions

    var dailyMacros: DailyMacros { DailyMacros(foods: foodsConsumed) }

    var isWeightCircleVisible: Bool { hasLoadedSettings && appSettings.showBodyWeight }

    var canLogWeight: Bool { BodyWeightDayKey.make(from: selectedDay) <= BodyWeightDayKey.make(from: now()) }

    var hasWeightForSelectedDay: Bool { recentWeights.first?.dayKey == BodyWeightDayKey.make(from: selectedDay) }

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
            .sorted { $0.startMinutes < $1.startMinutes }
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
            showActiveDays(for: selectedDay)
            await refreshSettings()
            await loadWeights(for: selectedDay)
            state = .loaded
            showSignInSpotlightIfNeeded()
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
            showActiveDays(for: selectedDay)
            weightCache = [:]
            await refreshSettings()
            await loadWeights(for: selectedDay)
            showSignInSpotlightIfNeeded()
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            alertItem = unknownErrorAlertItem(for: error)
        }
    }

    @MainActor
    func onForeground() async {
        guard hasCompletedInitialLoad else { return }
        do {
            advanceSelectedDayIfNeeded()
            try await refreshMealTypes()
            try await reloadDay(selectedDay)
            foodsConsumed = foodsFromCache(for: selectedDay)
            await refreshSettings()
            await loadWeights(for: selectedDay, forceReload: true)
            showSignInSpotlightIfNeeded()
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            alertItem = unknownErrorAlertItem(for: error)
        }
    }

    func onSignInSpotlightSignInTapped() {
        isSignInSpotlightVisible = false
        showAccountSheet = true
    }

    func onSignInSpotlightDismissed() {
        isSignInSpotlightVisible = false
    }

    @MainActor
    func onFoodConsumedUpdated() async {
        do {
            try await reloadDay(selectedDay)
            foodsConsumed = foodsFromCache(for: selectedDay)
            showSignInSpotlightIfNeeded()
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            alertItem = unknownErrorAlertItem(for: error)
        }
    }

    func onAddFoodSheetDismissed() {
        showSignInSpotlightIfNeeded()
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
            try await reloadDay(selectedDay)
            foodsConsumed = foodsFromCache(for: selectedDay)
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            alertItem = AlertItem(title: L10n.Dashboard.errorDeleteFailed)
        }
    }

    @MainActor
    @discardableResult
    func onFoodsDropped(_ providers: [NSItemProvider], on mealType: MealTypeDomain) -> Task<Void, Never> {
        Task { [weak self] in
            guard
                let id = await DraggedFoodConsumed.loadId(from: providers),
                let self,
                let food = foodsConsumed.first(where: { $0.id == id })
            else { return }
            await onFoodMoved(food, to: mealType)
        }
    }

    @MainActor
    func onFoodMoved(_ food: FoodConsumedDomain, to mealType: MealTypeDomain) async {
        guard mealTypes.resolvedMealTypeId(for: food) != mealType.id else { return }
        replaceInCache(food.copy(mealTypeId: mealType.id))
        do {
            try await assignFoodMealType(food, mealTypeId: mealType.id)
            moveFeedbackTrigger += 1
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            if cachedFood(food)?.mealTypeId == mealType.id { replaceInCache(food) }
            alertItem = AlertItem(title: L10n.Dashboard.errorMoveFailed)
        }
    }

    func onCopyRequested(from mealType: MealTypeDomain?, at index: Int) {
        copyTargetDay = Date.now
        copyTargetMealTypeId = mealTypes.mealType(at: Date.now)?.id ?? mealType?.id ?? mealTypes.first?.id
        copyPopoverIndex = index
    }

    func canCopy(from mealType: MealTypeDomain?) -> Bool {
        guard
            !isCopying,
            !showCopyCheckmark,
            let targetMealTypeId = copyTargetMealTypeId
        else {
            return false
        }
        let isSameDay = Calendar.current.isDate(copyTargetDay, inSameDayAs: selectedDay)
        return !(isSameDay && targetMealTypeId == mealType?.id)
    }

    @MainActor
    func onCopyConfirmed(_ foods: [FoodConsumedDomain], from mealType: MealTypeDomain?) async {
        guard
            canCopy(from: mealType),
            let targetMealTypeId = copyTargetMealTypeId
        else {
            return
        }
        isCopying = true
        do {
            try await copyFoodsConsumed(foods, toDay: copyTargetDay, mealTypeId: targetMealTypeId, mealTypes: mealTypes)
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            alertItem = AlertItem(title: L10n.Dashboard.errorCopyFailed)
            isCopying = false
            return
        }
        isCopying = false
        showCopyCheckmark = true
        await reloadAfterCopy()
        try? await Task.sleep(for: .seconds(1))
        copyPopoverIndex = nil
        showCopyCheckmark = false
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
        await loadWeights(for: date)
    }

    @MainActor
    func onDaySelected(_ date: Date) async {
        isViewingToday = Calendar.current.isDate(date, inSameDayAs: Date.now)
        selectedDay = date
        showCalendarSheet = false
        await loadFoods(for: date)
        await loadWeights(for: date)
    }

    func onWeightBadgeTapped() {
        guard canLogWeight else { return }
        let initial = recentWeights.first?.kilograms ?? Constants.BodyWeight.defaultKilograms
        weightPickerKilograms = initial
        isWeightPopoverVisible = true
    }

    @MainActor
    func onWeightConfirmed() async {
        guard
            !isSavingWeight,
            !showWeightCheckmark
        else { return }
        isSavingWeight = true
        do {
            try await saveBodyWeight(kilograms: weightPickerKilograms, for: selectedDay)
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            alertItem = AlertItem(title: L10n.Dashboard.errorWeightSaveFailed)
            isSavingWeight = false
            return
        }
        isSavingWeight = false
        showWeightCheckmark = true
        await onWeightsChanged()
        try? await Task.sleep(for: .seconds(1))
        isWeightPopoverVisible = false
        showWeightCheckmark = false
    }

    @MainActor
    func onWeightsChanged() async {
        weightCache = [:]
        await loadWeights(for: selectedDay)
    }

    func onAppSettingsChanged(_ settings: AppSettingsDomain) {
        appSettings = settings
        hasLoadedSettings = true
    }

    @MainActor
    func onCalendarMonthChanged(to month: Date) async {
        let key = monthCacheKey(for: month)
        if cachedMonthKeys.contains(key) {
            showActiveDays(for: month)
        } else {
            do {
                try await loadMonth(for: month)
                showActiveDays(for: month)
            } catch {
                Log.error(error, category: Constants.LogCategory.dashboard)
                alertItem = unknownErrorAlertItem(for: error)
            }
        }
    }

    @MainActor
    func onCalendarDismissed() {
        showActiveDays(for: selectedDay)
    }

    // MARK: - Private

    @MainActor
    private func refreshSettings() async {
        do {
            appSettings = try await fetchAppSettings()
            hasLoadedSettings = true
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            hasLoadedSettings = true
        }
    }

    @MainActor
    private func loadWeights(for date: Date, forceReload: Bool = false) async {
        let key = BodyWeightDayKey.make(from: date)
        if
            !forceReload,
            let cached = weightCache[key]
        {
            recentWeights = cached
            return
        }
        do {
            let weights = try await fetchBodyWeights(upTo: date)
            weightCache[key] = weights
            if BodyWeightDayKey.make(from: selectedDay) == key {
                recentWeights = weights
            }
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
        }
    }

    private static let signInSpotlightInterval: TimeInterval = 7 * 24 * 60 * 60

    private var isAnyPresentationActive: Bool {
        showSettings
            || showAddFoodSheet
            || showCalendarSheet
            || showAccountSheet
            || alertItem != nil
            || isDeleteConfirmationVisible
            || copyPopoverIndex != nil
            || macroPopoverIndex != nil
            || isWeightPopoverVisible
    }

    private func showSignInSpotlightIfNeeded() {
        guard
            authProvider.isAnonymous,
            monthCache.values.contains(where: { !$0.isEmpty }),
            !isSignInSpotlightVisible,
            !isAnyPresentationActive
        else { return }
        let currentDate = now()
        if let lastShownAt = signInSpotlightStore.lastShownAt {
            guard currentDate.timeIntervalSince(lastShownAt) >= Self.signInSpotlightInterval else { return }
        }
        signInSpotlightStore.lastShownAt = currentDate
        isSignInSpotlightVisible = true
    }

    private func unknownErrorAlertItem(for error: Error) -> AlertItem {
        if error.isFirestoreUnreachable {
            AlertItem(title: L10n.Common.errorOffline, message: L10n.Common.errorOfflineMessage)
        } else {
            AlertItem(title: L10n.Common.errorUnknown, message: L10n.Common.errorUnknownMessage)
        }
    }

    @MainActor
    private func reloadAfterCopy() async {
        guard cachedMonthKeys.contains(monthCacheKey(for: copyTargetDay)) else { return }
        do {
            try await reloadDay(copyTargetDay)
            if Calendar.current.isDate(copyTargetDay, inSameDayAs: selectedDay) {
                foodsConsumed = foodsFromCache(for: selectedDay)
            }
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            alertItem = unknownErrorAlertItem(for: error)
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
            Task { @MainActor in await self?.onForeground() }
        }
    }

    @MainActor
    private func loadFoods(for date: Date) async {
        let key = monthCacheKey(for: date)
        if cachedMonthKeys.contains(key) {
            foodsConsumed = foodsFromCache(for: date)
            showActiveDays(for: date)
        } else {
            do {
                try await loadMonth(for: date)
                foodsConsumed = foodsFromCache(for: date)
                showActiveDays(for: date)
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
    }

    @MainActor
    private func reloadDay(_ date: Date) async throws {
        if cachedMonthKeys.contains(monthCacheKey(for: date)) {
            let day = Calendar.current.startOfDay(for: date)
            monthCache[dayCacheKey(for: day)] = try await fetchFoodsConsumedInRange(from: day, to: day)
            activeDaysInMonth = computeActiveDays(for: activeDaysMonth)
        } else {
            try await loadMonth(for: date)
            showActiveDays(for: date)
        }
    }

    @MainActor
    private func showActiveDays(for month: Date) {
        activeDaysMonth = month
        activeDaysInMonth = computeActiveDays(for: month)
    }

    @MainActor
    private func monthCacheKey(for date: Date) -> String {
        date.formatCacheKey(with: Constants.DateFormat.monthCacheKey)
    }

    @MainActor
    private func dayCacheKey(for date: Date) -> String {
        date.formatCacheKey(with: Constants.DateFormat.dayCacheKey)
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
    private func replaceInCache(_ food: FoodConsumedDomain) {
        let key = dayCacheKey(for: food.date)
        guard let index = monthCache[key]?.firstIndex(where: { $0.id == food.id }) else { return }
        monthCache[key]?[index] = food
        if dayCacheKey(for: selectedDay) == key {
            foodsConsumed = foodsFromCache(for: selectedDay)
        }
    }

    @MainActor
    private func cachedFood(_ food: FoodConsumedDomain) -> FoodConsumedDomain? {
        monthCache[dayCacheKey(for: food.date)]?.first { $0.id == food.id }
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
