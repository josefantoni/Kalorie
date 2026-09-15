//
//  AddFoodSheetViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 18.06.2024.
//

import Foundation

struct FoodItemFormInput {

    // MARK: - Properties

    var scannedCode = ""
    var name = ""
    var engName = ""
    var weightOfProduct: Double = 0
    var energyKJ: Double = 0
    var caloriesPerHundredGrams: Double = 0
    var fat: Double = 0
    var fatSaturated: Double? = 0
    var fatUnsaturatedFattyAcids: Double = 0
    var carbohydrate: Double = 0
    var carbohydratePureSugar: Double = 0
    var fiber: Double? = 0
    var protein: Double = 0
    var salt: Double = 0
    var portions: [FoodPortionDraft] = []
}

extension FoodItemFormInput {
    init(item: FoodItemDomain) {
        self.init(
            scannedCode: item.id,
            name: item.czName,
            engName: item.engName,
            weightOfProduct: item.weight,
            energyKJ: item.energyKJ,
            caloriesPerHundredGrams: item.caloriesPerHundredGrams,
            fat: item.fat,
            fatSaturated: item.fatSaturated,
            fatUnsaturatedFattyAcids: item.fatUnsaturatedFattyAcids,
            carbohydrate: item.carbohydrate,
            carbohydratePureSugar: item.carbohydratePureSugar,
            fiber: item.fiber,
            protein: item.protein,
            salt: item.salt,
            portions: item.portions.map { FoodPortionDraft(name: $0.name, gramsText: String(format: "%g", $0.grams)) }
        )
    }

    func asFoodItemDomain(kind: FoodItemKind = .catalogue, date: Date = .now) -> FoodItemDomain {
        FoodItemDomain(
            id: scannedCode,
            kind: kind,
            czName: name,
            engName: engName,
            weight: weightOfProduct,
            date: date,
            energyKJ: energyKJ,
            caloriesPerHundredGrams: caloriesPerHundredGrams,
            fat: fat,
            fatSaturated: fatSaturated,
            fatUnsaturatedFattyAcids: fatUnsaturatedFattyAcids,
            carbohydrate: carbohydrate,
            carbohydratePureSugar: carbohydratePureSugar,
            fiber: fiber,
            protein: protein,
            salt: salt,
            portions: Self.parsedPortions(portions)
        )
    }

    static func parsedPortions(_ drafts: [FoodPortionDraft]) -> [FoodPortionDomain] {
        drafts.compactMap { draft in
            let grams = Double(draft.gramsText.replacingOccurrences(of: ",", with: ".")) ?? 0
            guard grams >= 1 else { return nil }
            return FoodPortionDomain(name: draft.name, grams: grams)
        }
    }
}

final class AddFoodSheetViewModel: ObservableObject {

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .idle
    @Published var localFoodItems: [FoodItemDomain] = []
    @Published var externalFoodItems: [FoodItemDomain] = []
    @Published private(set) var isExternalSearchLoading = false
    @Published var searchText = ""
    @Published var formInput = FoodItemFormInput()
    @Published var isAddNewItemVisible = false
    @Published var isScannerVisible: Bool
    @Published var alertItem: AlertItem?
    @Published private(set) var shouldDismiss = false
    @Published var isPushedToQuantityView = false
    @Published private(set) var selectedFoodItem: FoodItemDomain?
    @Published var lastScannedBarcode = ""
    @Published private(set) var isBarcodeSearchLoading = false
    @Published private(set) var favouriteFoods: [FoodItemDomain] = []
    @Published private(set) var favouriteIds: Set<String> = []
    @Published private(set) var myCreatedMeals: [MyCreatedMealDomain] = []
    @Published private(set) var mySubmissions: [FoodItemSubmissionDomain] = []
    @Published var isSubmissionConfirmationVisible = false
    @Published private(set) var rejectionReasonBeingEdited: String?
    let searchPlaceholder: String

    var isEditingSubmission: Bool { editingSubmissionId != nil }

    private let searchFoodItems: any SearchFoodItemsUseCaseProtocol
    private let submitFoodItem: any SubmitFoodItemUseCaseProtocol
    private let fetchMySubmissions: any FetchMySubmissionsUseCaseProtocol
    private let updateMySubmission: any UpdateMySubmissionUseCaseProtocol
    private let searchFoodExternally: any SearchFoodExternallyUseCaseProtocol
    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    private let fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol
    private let fetchFavouriteFoods: any FetchFavouriteFoodsUseCaseProtocol
    private let fetchMyCreatedMeals: any FetchMyCreatedMealsUseCaseProtocol
    private let onFoodSaved: () -> Void
    private let onCreateMealRequested: () -> Void
    private var editingSubmissionId: String?

    var displayedResults: [FoodItemDomain] {
        let query = searchText.lowercased()
        guard !query.isEmpty else { return localFoodItems }
        let matchingMeals = myCreatedMeals
            .map { $0.asFoodItem() }
            .filter { $0.czName.lowercased().hasPrefix(query) }
        let matchingFavourites = favouriteFoods.filter {
            $0.czName.lowercased().hasPrefix(query) || $0.engName.lowercased().hasPrefix(query)
        }
        let mealIds = Set(matchingMeals.map(\.id))
        let matchingFavouritesFiltered = matchingFavourites.filter { !mealIds.contains($0.id) }
        let matchingFavouriteAndMealIds = mealIds.union(matchingFavouritesFiltered.map(\.id))
        var seenSubmissionIds = matchingFavouriteAndMealIds
        let matchingSubmissions = mySubmissions
            .map(\.item)
            .filter { $0.czName.lowercased().hasPrefix(query) || $0.engName.lowercased().hasPrefix(query) }
            .filter { seenSubmissionIds.insert($0.id).inserted }
        let matchingIds = matchingFavouriteAndMealIds.union(matchingSubmissions.map(\.id))
        return matchingMeals + matchingFavouritesFiltered + matchingSubmissions
            + localFoodItems.filter { !matchingIds.contains($0.id) }
    }

    // MARK: - Init

    init(
        searchFoodItems: any SearchFoodItemsUseCaseProtocol,
        submitFoodItem: any SubmitFoodItemUseCaseProtocol,
        fetchMySubmissions: any FetchMySubmissionsUseCaseProtocol,
        updateMySubmission: any UpdateMySubmissionUseCaseProtocol,
        searchFoodExternally: any SearchFoodExternallyUseCaseProtocol,
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol,
        fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol,
        fetchFavouriteFoods: any FetchFavouriteFoodsUseCaseProtocol,
        fetchMyCreatedMeals: any FetchMyCreatedMealsUseCaseProtocol,
        onFoodSaved: @escaping () -> Void = {},
        onCreateMealRequested: @escaping () -> Void = {},
        isScannerVisible: Bool = false
    ) {
        self.isScannerVisible = isScannerVisible
        self.searchFoodItems = searchFoodItems
        self.submitFoodItem = submitFoodItem
        self.fetchMySubmissions = fetchMySubmissions
        self.updateMySubmission = updateMySubmission
        self.searchFoodExternally = searchFoodExternally
        self.fetchFoodItemByBarcode = fetchFoodItemByBarcode
        self.fetchFoodByBarcodeExternally = fetchFoodByBarcodeExternally
        self.fetchFavouriteFoods = fetchFavouriteFoods
        self.fetchMyCreatedMeals = fetchMyCreatedMeals
        self.onFoodSaved = onFoodSaved
        self.onCreateMealRequested = onCreateMealRequested
        let example = L10n.AddFood.searchExamples.randomElement() ?? ""
        self.searchPlaceholder = L10n.AddFood.searchPlaceholder(example: example)
    }

    // MARK: - Functions

    func onScannerButtonTapped() {
        isAddNewItemVisible = false
        isScannerVisible = true
    }

    @MainActor
    func onAddNewItemToggleTapped() {
        isAddNewItemVisible.toggle()
        guard isAddNewItemVisible else { return }
        formInput = FoodItemFormInput()
        editingSubmissionId = nil
        rejectionReasonBeingEdited = nil
    }

    func onScenePhaseActive(isCameraAvailable: Bool) {
        guard isScannerVisible, !isCameraAvailable else { return }
        isScannerVisible = false
        alertItem = AlertItem(title: L10n.AddFood.cameraPermissionAlert)
    }

    @MainActor
    func onBarcodeScanned() async {
        let barcode = lastScannedBarcode
        guard !barcode.isEmpty else { return }
        isBarcodeSearchLoading = true
        defer {
            lastScannedBarcode = ""
            isBarcodeSearchLoading = false
        }
        do {
            if let local = try await fetchFoodItemByBarcode(barcode: barcode) {
                isScannerVisible = false
                onSelectFoodItem(local)
                return
            }
        } catch is CancellationError {
            return
        } catch {
            Log.warning(error, category: Constants.LogCategory.addFoodSheet)
        }
        do {
            if let external = try await fetchFoodByBarcodeExternally(barcode: barcode) {
                isScannerVisible = false
                onSelectFoodItem(external)
                return
            }
        } catch is CancellationError {
            return
        } catch let error as URLError where error.code == .cancelled {
            return
        } catch {
            Log.error(error, category: Constants.LogCategory.addFoodSheet)
            alertItem = AlertItem(title: L10n.AddFood.errorLoadFailed)
            return
        }
        alertItem = AlertItem(title: L10n.AddFood.errorBarcodeNotFound)
    }

    @MainActor
    func onSearchTextChanged() async {
        guard !isPushedToQuantityView else { return }
        guard !searchText.isEmpty else {
            localFoodItems = []
            externalFoodItems = []
            return
        }
        do {
            try await Task.sleep(for: .milliseconds(300))
        } catch {
            return
        }
        do {
            localFoodItems = try await searchFoodItems(query: searchText)
        } catch {
            return
        }
        guard displayedResults.isEmpty && searchText.count >= 3 else {
            externalFoodItems = []
            return
        }
        isExternalSearchLoading = true
        defer { isExternalSearchLoading = false }
        do {
            externalFoodItems = try await searchFoodExternally(query: searchText)
        } catch {
            Log.warning(error, category: Constants.LogCategory.addFoodSheet)
            externalFoodItems = []
        }
    }

    @MainActor
    func onSelectFoodItem(_ item: FoodItemDomain) {
        guard !isPushedToQuantityView else { return }
        selectedFoodItem = item
        isPushedToQuantityView = true
    }

    func onFoodConsumedSaved() {
        onFoodSaved()
        shouldDismiss = true
    }

    func onCreateMealButtonTapped() {
        onCreateMealRequested()
        shouldDismiss = true
    }

    @MainActor
    func onAppear() async {
        async let favourites = fetchFavouriteFoods()
        async let meals = fetchMyCreatedMeals()
        async let submissions = fetchMySubmissions()
        do {
            let items = try await favourites
            favouriteFoods = items
            favouriteIds = Set(items.map(\.id))
        } catch {
            Log.warning(error, category: Constants.LogCategory.addFoodSheet)
        }
        do {
            myCreatedMeals = try await meals
        } catch {
            Log.warning(error, category: Constants.LogCategory.addFoodSheet)
        }
        do {
            mySubmissions = try await submissions
        } catch {
            Log.warning(error, category: Constants.LogCategory.addFoodSheet)
        }
    }

    func isFavourite(_ item: FoodItemDomain) -> Bool {
        favouriteIds.contains(item.id)
    }

    func isMyCreatedMeal(_ item: FoodItemDomain) -> Bool {
        item.kind == .createdMeal
    }

    func submissionStatus(for item: FoodItemDomain) -> FoodItemSubmissionStatus? {
        mySubmissions.first { $0.barcode == item.id }?.status
    }

    @MainActor
    func onSelectRejectedSubmission(_ item: FoodItemDomain) {
        guard let submission = mySubmissions.first(where: { $0.barcode == item.id }) else { return }
        openEditingForm(for: submission)
    }

    @MainActor
    func onSelectSubmission(_ submission: FoodItemSubmissionDomain) {
        guard submission.status == .rejected else {
            onSelectFoodItem(submission.item)
            return
        }
        openEditingForm(for: submission)
    }

    func onSubmissionConfirmationDismissed() {
        isSubmissionConfirmationVisible = false
        shouldDismiss = true
    }

    func onFavouriteChanged(id: String, isFavourite: Bool, item: FoodItemDomain) {
        favouriteFoods.removeAll { $0.id == id }
        if isFavourite {
            favouriteIds.insert(id)
            favouriteFoods.insert(item, at: 0)
        } else {
            favouriteIds.remove(id)
        }
    }

    @MainActor
    func onCreateFoodItem() async {
        state = .loading
        defer { state = .loaded }
        let item = formInput.asFoodItemDomain()
        do {
            if let editingSubmissionId {
                _ = try await updateMySubmission(id: editingSubmissionId, item: item)
            } else {
                _ = try await submitFoodItem(item)
            }
            editingSubmissionId = nil
            rejectionReasonBeingEdited = nil
            isSubmissionConfirmationVisible = true
        } catch {
            Log.error(error, category: Constants.LogCategory.addFoodSheet)
            guard !error.isFirestoreUnreachable else {
                alertItem = AlertItem(title: L10n.Common.errorOffline, message: L10n.Common.errorOfflineMessage)
                return
            }
            switch error as? FoodItemSubmissionError {
            case .invalidCode:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidCode)
            case .invalidName:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidName)
            case .invalidCalories:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidCalories)
            case .invalidWeight:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidWeight)
            case .invalidPortion(let portionError):
                alertItem = AlertItem(title: portionError.alertTitle)
            case .itemAlreadyExists:
                alertItem = AlertItem(title: L10n.AddFood.errorItemAlreadyExists)
            case nil:
                alertItem = AlertItem(title: L10n.Common.errorUnknown)
            }
        }
    }

    // MARK: - Private

    @MainActor
    private func openEditingForm(for submission: FoodItemSubmissionDomain) {
        formInput = FoodItemFormInput(item: submission.item)
        editingSubmissionId = submission.id
        rejectionReasonBeingEdited = submission.rejectReason
        isAddNewItemVisible = true
    }

}
