//
//  FoodQuantityViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 24.07.2026.
//

import Foundation

enum FoodQuantityUnit: Hashable {
    case hundredGrams
    case grams
    case portion(FoodPortionDomain)

    var gramsPerUnit: Double {
        switch self {
        case .hundredGrams: return 100
        case .grams: return 1
        case .portion(let portion): return portion.grams
        }
    }
}

final class FoodQuantityViewModel: ObservableObject, FavouriteToggling, FoodItemReporting {

    // MARK: - Properties

    @Published var unit: FoodQuantityUnit
    @Published var quantity: Double
    @Published private(set) var state: LoadingState<Void> = .idle
    @Published var alertItem: AlertItem?
    @Published var isFavourite: Bool
    @Published var isTogglingFavourite = false
    @Published private(set) var personalPortions: [FoodPortionDomain] = []
    @Published var isPersonalPortionsManagerPushed = false
    @Published var portionDrafts = [FoodPortionDraft(name: "", gramsText: "")]
    @Published private(set) var showPortionCheckmark = false
    @Published private(set) var mealTypes: [MealTypeDomain]
    @Published var selectedMealTypeId: String?
    @Published var hasReportedCurrentItem = false
    @Published var isSubmittingReport = false
    @Published var isReportReasonAlertVisible = false
    @Published var reportReasonText = ""

    let item: FoodItemDomain
    private var meal: MyCreatedMealDomain?
    private let saveFoodConsumed: any SaveFoodConsumedUseCaseProtocol
    private let fetchMealTypes: any FetchMealTypesUseCaseProtocol
    private let addFavouriteFood: any AddFavouriteFoodUseCaseProtocol
    private let removeFavouriteFood: any RemoveFavouriteFoodUseCaseProtocol
    private let fetchFoodItemPersonalPortions: any FetchFoodItemPersonalPortionsUseCaseProtocol
    private let saveFoodItemPersonalPortions: any SaveFoodItemPersonalPortionsUseCaseProtocol
    private let fetchMyFoodItemReport: any FetchMyFoodItemReportUseCaseProtocol
    private let submitFoodItemReport: any SubmitFoodItemReportUseCaseProtocol
    private let updateMyCreatedMeal: any UpdateMyCreatedMealUseCaseProtocol
    private let selectedDate: Date
    private let onSaved: () -> Void
    private let onFavouriteChanged: (String, Bool) -> Void
    private let onMealUpdated: (MyCreatedMealDomain) -> Void
    private var hasUserSelectedUnit = false
    private var hasUserSelectedMealType = false

    var grams: Double { quantity * unit.gramsPerUnit }

    private var scaledMacros: ScaledMacros { item.scaled(toGrams: grams) }

    var scaledCalories: Int { scaledMacros.calories }
    var scaledProtein: Double { scaledMacros.protein }
    var scaledCarbohydrate: Double { scaledMacros.carbohydrate }
    var scaledCarbohydrateSugar: Double { scaledMacros.carbohydrateSugar }
    var scaledFat: Double { scaledMacros.fat }
    var scaledFatSaturated: Double? { scaledMacros.fatSaturated }
    var scaledFiber: Double? { scaledMacros.fiber }
    var scaledSalt: Double { scaledMacros.salt }

    var arePortionDraftsComplete: Bool {
        portionDrafts.allSatisfy { draft in
            !draft.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !draft.gramsText.isEmpty
        }
    }

    var canSavePortionDrafts: Bool {
        portionDrafts.contains { draft in
            !draft.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !draft.gramsText.isEmpty
        }
    }

    var isPersonalPortionsAvailable: Bool { item.kind == .catalogue || meal != nil }
    var canReportIncorrectData: Bool { item.kind == .catalogue }

    var unitOptions: [FoodQuantityUnit] {
        let ownPortions = meal == nil ? item.portions : []
        return personalPortions.map(FoodQuantityUnit.portion) + ownPortions.map(FoodQuantityUnit.portion) + [.grams, .hundredGrams]
    }

    // MARK: - Init

    init(
        item: FoodItemDomain,
        saveFoodConsumed: any SaveFoodConsumedUseCaseProtocol,
        fetchMealTypes: any FetchMealTypesUseCaseProtocol,
        selectedDate: Date,
        mealTypes: [MealTypeDomain],
        isFavourite: Bool,
        addFavouriteFood: any AddFavouriteFoodUseCaseProtocol,
        removeFavouriteFood: any RemoveFavouriteFoodUseCaseProtocol,
        fetchFoodItemPersonalPortions: any FetchFoodItemPersonalPortionsUseCaseProtocol,
        saveFoodItemPersonalPortions: any SaveFoodItemPersonalPortionsUseCaseProtocol,
        fetchMyFoodItemReport: any FetchMyFoodItemReportUseCaseProtocol,
        submitFoodItemReport: any SubmitFoodItemReportUseCaseProtocol,
        meal: MyCreatedMealDomain?,
        updateMyCreatedMeal: any UpdateMyCreatedMealUseCaseProtocol,
        onSaved: @escaping () -> Void,
        onMealUpdated: @escaping (MyCreatedMealDomain) -> Void,
        onFavouriteChanged: @escaping (String, Bool) -> Void,
        quantity: Double = 1,
        unit: FoodQuantityUnit = .hundredGrams
    ) {
        self.item = item
        self.saveFoodConsumed = saveFoodConsumed
        self.fetchMealTypes = fetchMealTypes
        self.selectedDate = selectedDate
        self.mealTypes = mealTypes
        self.selectedMealTypeId = mealTypes.mealType(at: selectedDate)?.id
        self.isFavourite = isFavourite
        self.addFavouriteFood = addFavouriteFood
        self.removeFavouriteFood = removeFavouriteFood
        self.fetchFoodItemPersonalPortions = fetchFoodItemPersonalPortions
        self.saveFoodItemPersonalPortions = saveFoodItemPersonalPortions
        self.fetchMyFoodItemReport = fetchMyFoodItemReport
        self.submitFoodItemReport = submitFoodItemReport
        self.meal = meal
        self.updateMyCreatedMeal = updateMyCreatedMeal
        self.personalPortions = meal?.portions ?? []
        self.onSaved = onSaved
        self.onFavouriteChanged = onFavouriteChanged
        self.onMealUpdated = onMealUpdated
        self.quantity = quantity
        self.unit = unit
    }

    // MARK: - Functions

    static func defaultUnit(for item: FoodItemDomain) -> FoodQuantityUnit {
        item.portions.first.map(FoodQuantityUnit.portion) ?? .grams
    }

    @MainActor
    func onAppear() async {
        if canReportIncorrectData {
            await loadReportState(barcode: item.id, fetchMyFoodItemReport: fetchMyFoodItemReport)
        }
        guard item.kind == .catalogue else { return }
        do {
            personalPortions = try await fetchFoodItemPersonalPortions(barcode: item.id)
            if
                !hasUserSelectedUnit,
                let firstPersonalPortion = personalPortions.first
            {
                quantity = 1
                unit = .portion(firstPersonalPortion)
            }
        } catch {
            Log.warning(error, category: Constants.LogCategory.foodQuantity)
        }
    }

    @MainActor
    func onReportSubmitted() async {
        await onReportSubmitted(barcode: item.id, submitFoodItemReport: submitFoodItemReport)
    }

    func onUnitSelected(_ newUnit: FoodQuantityUnit) {
        hasUserSelectedUnit = true
        let currentGrams = quantity * unit.gramsPerUnit
        unit = newUnit
        quantity = currentGrams / newUnit.gramsPerUnit
    }

    func onMealTypeSelected(_ mealTypeId: String) {
        hasUserSelectedMealType = true
        selectedMealTypeId = mealTypeId
    }

    func onPortionsManagerOpened() {
        if case .portion = unit {
            portionDrafts = [FoodPortionDraft(name: "", gramsText: "")]
        } else {
            portionDrafts = [FoodPortionDraft(name: "", gramsText: grams.formattedTrimmed())]
        }
    }

    func onAddPortionDraftTapped() {
        portionDrafts.append(FoodPortionDraft(name: "", gramsText: ""))
    }

    func onDeletePortionDraft(_ draft: FoodPortionDraft) {
        portionDrafts.removeAll { $0.id == draft.id }
        if portionDrafts.isEmpty {
            portionDrafts = [FoodPortionDraft(name: "", gramsText: "")]
        }
    }

    @MainActor
    func onSavePersonalPortions() async {
        let filledDrafts = portionDrafts.filter { draft in
            !draft.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !draft.gramsText.isEmpty
        }
        guard !filledDrafts.isEmpty else { return }
        var newPortions: [FoodPortionDomain] = []
        for draft in filledDrafts {
            let grams = Double(draft.gramsText.replacingOccurrences(of: ",", with: ".")) ?? 0
            if let error = FoodPortionValidation.validate(name: draft.name, grams: grams) {
                alertItem = AlertItem(title: error.alertTitle)
                return
            }
            newPortions.append(FoodPortionDomain(name: draft.name, grams: grams))
        }
        let original = personalPortions
        personalPortions = original + newPortions
        do {
            try await persistPersonalPortions()
            portionDrafts = [FoodPortionDraft(name: "", gramsText: "")]
            showPortionCheckmark = true
            try? await Task.sleep(for: .seconds(2))
            showPortionCheckmark = false
        } catch let error as FoodPortionError {
            Log.error(error, category: Constants.LogCategory.foodQuantity)
            personalPortions = original
            alertItem = AlertItem(title: error.alertTitle)
        } catch {
            Log.error(error, category: Constants.LogCategory.foodQuantity)
            personalPortions = original
            alertItem = AlertItem(title: L10n.MyPortions.errorSaveFailed)
        }
    }

    @MainActor
    func onDeletePersonalPortion(_ portion: FoodPortionDomain) async {
        let original = personalPortions
        let originalUnit = unit
        let originalQuantity = quantity
        personalPortions.removeAll { $0 == portion }
        if !unitOptions.contains(unit) {
            quantity = grams
            unit = .grams
        }
        do {
            try await persistPersonalPortions()
        } catch {
            Log.error(error, category: Constants.LogCategory.foodQuantity)
            personalPortions = original
            unit = originalUnit
            quantity = originalQuantity
            alertItem = AlertItem(title: L10n.MyPortions.errorDeleteFailed)
        }
    }

    @MainActor
    private func persistPersonalPortions() async throws {
        guard let meal else {
            try await saveFoodItemPersonalPortions(barcode: item.id, portions: personalPortions)
            return
        }
        let updated = MyCreatedMealDomain(
            id: meal.id,
            name: meal.name,
            ingredients: meal.ingredients,
            createdAt: meal.createdAt,
            updatedAt: meal.updatedAt,
            portions: personalPortions
        )
        try await updateMyCreatedMeal(updated)
        self.meal = updated
        onMealUpdated(updated)
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
            do {
                mealTypes = try await fetchMealTypes()
            } catch {
                Log.warning(error, category: Constants.LogCategory.foodQuantity)
            }
            let mealTypeId: String?
            if hasUserSelectedMealType {
                guard
                    let selectedMealTypeId,
                    mealTypes.contains(where: { $0.id == selectedMealTypeId })
                else {
                    alertItem = AlertItem(title: L10n.Common.errorUnknown)
                    return
                }
                mealTypeId = selectedMealTypeId
            } else {
                mealTypeId = mealTypes.mealType(at: selectedDate)?.id
            }
            try await saveFoodConsumed(item, grams: grams, date: selectedDate, mealTypeId: mealTypeId)
            onSaved()
        } catch {
            Log.error(error, category: Constants.LogCategory.foodQuantity)
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }
}
