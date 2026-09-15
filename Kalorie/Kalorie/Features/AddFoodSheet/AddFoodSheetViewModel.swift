//
//  AddFoodSheetViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 18.06.2024.
//

import Foundation
import UIKit

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
    var portions: [FoodPortionDraft] = [FoodPortionDraft(name: "", gramsText: "")]
    var measure: FoodMeasure = .grams
    var isWeightInThousands = false
}

extension FoodItemFormInput {
    init(item: FoodItemDomain) {
        let weightDisplay = Self.weightDisplay(for: item.weight)
        self.init(
            scannedCode: item.id,
            name: item.czName,
            engName: item.engName,
            weightOfProduct: weightDisplay.value,
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
            portions: item.portions.isEmpty
                ? [FoodPortionDraft(name: "", gramsText: "")]
                : item.portions.map { FoodPortionDraft(name: $0.name, gramsText: String(format: "%g", $0.grams)) },
            measure: item.measure,
            isWeightInThousands: weightDisplay.isInThousands
        )
    }

    static func weightDisplay(for weight: Double) -> (value: Double, isInThousands: Bool) {
        weight >= 1000 ? (weight / 1000, true) : (weight, false)
    }

    func asFoodItemDomain(kind: FoodItemKind = .catalogue, date: Date = .now) -> FoodItemDomain {
        FoodItemDomain(
            id: scannedCode,
            kind: kind,
            czName: name,
            engName: engName,
            weight: isWeightInThousands ? weightOfProduct * 1000 : weightOfProduct,
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
            portions: Self.parsedPortions(portions),
            measure: measure
        )
    }

    static func parsedPortions(_ drafts: [FoodPortionDraft]) -> [FoodPortionDomain] {
        drafts.compactMap { draft in
            let grams = Double(draft.gramsText.replacingOccurrences(of: ",", with: ".")) ?? 0
            guard grams >= 1 else { return nil }
            return FoodPortionDomain(name: draft.name, grams: grams)
        }
    }

    @discardableResult
    mutating func applying(_ reading: NutritionLabelReading) -> Set<FoodItemFormField> {
        var applied: Set<FoodItemFormField> = []

        func fillIfEmpty(_ current: inout Double, with value: Double?, field: FoodItemFormField) {
            guard current == 0, let value else { return }
            current = value
            applied.insert(field)
        }
        func fillIfEmpty(_ current: inout Double?, with value: Double?, field: FoodItemFormField) {
            guard (current ?? 0) == 0, let value else { return }
            current = value
            applied.insert(field)
        }

        if
            scannedCode.isEmpty,
            let value = reading.scannedCode
        {
            scannedCode = value
        }
        if
            name.isEmpty,
            let value = reading.name
        {
            name = value
            applied.insert(.name)
        }
        if
            weightOfProduct == 0,
            let value = reading.weightOfProduct
        {
            let display = Self.weightDisplay(for: value)
            weightOfProduct = display.value
            isWeightInThousands = display.isInThousands
            applied.insert(.weight)
        }
        if
            measure == .grams,
            let value = reading.measure
        {
            measure = value
            applied.insert(.measure)
        }
        fillIfEmpty(&energyKJ, with: reading.energyKJ, field: .energyKJ)
        fillIfEmpty(&caloriesPerHundredGrams, with: reading.caloriesPerHundredGrams, field: .calories)
        fillIfEmpty(&fat, with: reading.fat, field: .fat)
        fillIfEmpty(&fatSaturated, with: reading.fatSaturated, field: .fatSaturated)
        fillIfEmpty(&fatUnsaturatedFattyAcids, with: reading.fatUnsaturatedFattyAcids, field: .fatUnsaturated)
        fillIfEmpty(&carbohydrate, with: reading.carbohydrate, field: .carbohydrate)
        fillIfEmpty(&carbohydratePureSugar, with: reading.carbohydratePureSugar, field: .carbohydrateSugar)
        fillIfEmpty(&fiber, with: reading.fiber, field: .fiber)
        fillIfEmpty(&protein, with: reading.protein, field: .protein)
        fillIfEmpty(&salt, with: reading.salt, field: .salt)
        if
            isPortionsEmpty,
            let value = reading.portions,
            !value.isEmpty
        {
            portions = value.map { FoodPortionDraft(name: $0.name, gramsText: String(format: "%g", $0.grams)) }
        }

        return applied
    }

    private var isPortionsEmpty: Bool {
        portions.allSatisfy { $0.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && $0.gramsText.isEmpty }
    }
}

enum AddFoodSheetMode: CaseIterable, Identifiable, Equatable {
    case search
    case newItem
    case createMeal

    var id: Self { self }

    var title: String {
        switch self {
        case .search:
            L10n.AddFood.modeSearch
        case .newItem:
            L10n.AddFood.modeNewItem
        case .createMeal:
            L10n.AddFood.modeCreateMeal
        }
    }
}

final class AddFoodSheetViewModel: ObservableObject, NutritionLabelPrefilling {

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .idle
    @Published var recognizedFields: Set<FoodItemFormField> = []
    @Published var isRecognizingNutritionLabel = false
    @Published var isNutritionLabelCameraVisible = false
    @Published var nutritionLabelCameraHint: String?
    @Published var cameraAccess: CameraAccess
    @Published var isReviewPushed = false
    @Published var isBarcodeRescanVisible = false
    @Published var rescannedBarcode = ""
    @Published var localFoodItems: [FoodItemDomain] = []
    @Published var externalFoodItems: [FoodItemDomain] = []
    @Published private(set) var isExternalSearchLoading = false
    @Published var searchText = ""
    @Published var formInput = FoodItemFormInput()
    @Published private(set) var mode: AddFoodSheetMode = .search
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
    @Published var isSubmissionDeleteConfirmationVisible = false
    let searchPlaceholder: String

    var isEditingSubmission: Bool { editingSubmissionId != nil }

    private let searchFoodItems: any SearchFoodItemsUseCaseProtocol
    private let submitFoodItem: any SubmitFoodItemUseCaseProtocol
    private let fetchMySubmissions: any FetchMySubmissionsUseCaseProtocol
    private let updateMySubmission: any UpdateMySubmissionUseCaseProtocol
    private let deleteMySubmission: any DeleteMySubmissionUseCaseProtocol
    private let searchFoodExternally: any SearchFoodExternallyUseCaseProtocol
    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    private let fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol
    private let fetchFavouriteFoods: any FetchFavouriteFoodsUseCaseProtocol
    private let fetchMyCreatedMeals: any FetchMyCreatedMealsUseCaseProtocol
    private let recognizeNutritionLabelUseCase: any RecognizeNutritionLabelUseCaseProtocol
    private let cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol
    private let onFoodSaved: () -> Void
    private var editingSubmissionId: String?
    private var isNutritionLabelCameraReviewPending = false
    private var submissionPendingDeletion: FoodItemSubmissionDomain?

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
        deleteMySubmission: any DeleteMySubmissionUseCaseProtocol,
        searchFoodExternally: any SearchFoodExternallyUseCaseProtocol,
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol,
        fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol,
        fetchFavouriteFoods: any FetchFavouriteFoodsUseCaseProtocol,
        fetchMyCreatedMeals: any FetchMyCreatedMealsUseCaseProtocol,
        recognizeNutritionLabel: any RecognizeNutritionLabelUseCaseProtocol,
        cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol,
        onFoodSaved: @escaping () -> Void = {},
        isScannerVisible: Bool = false
    ) {
        self.isScannerVisible = isScannerVisible
        self.searchFoodItems = searchFoodItems
        self.submitFoodItem = submitFoodItem
        self.fetchMySubmissions = fetchMySubmissions
        self.updateMySubmission = updateMySubmission
        self.deleteMySubmission = deleteMySubmission
        self.searchFoodExternally = searchFoodExternally
        self.fetchFoodItemByBarcode = fetchFoodItemByBarcode
        self.fetchFoodByBarcodeExternally = fetchFoodByBarcodeExternally
        self.fetchFavouriteFoods = fetchFavouriteFoods
        self.fetchMyCreatedMeals = fetchMyCreatedMeals
        self.recognizeNutritionLabelUseCase = recognizeNutritionLabel
        self.cameraAuthorizationProvider = cameraAuthorizationProvider
        self.cameraAccess = cameraAuthorizationProvider.status
        self.onFoodSaved = onFoodSaved
        let example = L10n.AddFood.searchExamples.randomElement() ?? ""
        self.searchPlaceholder = L10n.AddFood.searchPlaceholder(example: example)
    }

    // MARK: - Functions

    func onScannerButtonTapped() {
        mode = .search
        isScannerVisible = true
    }

    @MainActor
    func onModeSelected(_ mode: AddFoodSheetMode) {
        guard mode != self.mode else { return }
        self.mode = mode
        isScannerVisible = false
        isReviewPushed = false
        guard mode == .newItem else { return }
        formInput = FoodItemFormInput()
        recognizedFields = []
        editingSubmissionId = nil
        rejectionReasonBeingEdited = nil
    }

    @MainActor
    func onNutritionLabelPromptTapped() async {
        formInput = FoodItemFormInput()
        recognizedFields = []
        editingSubmissionId = nil
        rejectionReasonBeingEdited = nil
        await openNutritionLabelCamera(using: cameraAuthorizationProvider) {
            cameraAccess = .denied
        }
    }

    @MainActor
    func onReviewNutritionLabelCameraTapped() async {
        await openNutritionLabelCamera(using: cameraAuthorizationProvider) {
            alertItem = AlertItem(title: L10n.AddFood.cameraPermissionAlert)
        }
    }

    @MainActor
    func onNutritionLabelCaptured(_ image: UIImage, liveBarcode: String?) async {
        let succeeded = await recognizeNutritionLabel(from: image, liveBarcode: liveBarcode, using: recognizeNutritionLabelUseCase)
        if succeeded {
            isNutritionLabelCameraReviewPending = true
        }
    }

    func onNutritionLabelCameraDismissed() {
        guard isNutritionLabelCameraReviewPending else { return }
        isNutritionLabelCameraReviewPending = false
        isReviewPushed = true
    }

    func onBarcodeRescanTapped() {
        isBarcodeRescanVisible = true
    }

    func onBarcodeRescanned() {
        guard !rescannedBarcode.isEmpty else { return }
        formInput.scannedCode = rescannedBarcode
        rescannedBarcode = ""
        isBarcodeRescanVisible = false
    }

    func onScenePhaseActive(isCameraAvailable: Bool) {
        cameraAccess = cameraAuthorizationProvider.status
        if isNutritionLabelCameraVisible, cameraAccess != .authorized {
            isNutritionLabelCameraVisible = false
        }
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

    @MainActor
    func onMyCreatedMealSaved() async {
        mode = .search
        do {
            myCreatedMeals = try await fetchMyCreatedMeals()
        } catch {
            Log.warning(error, category: Constants.LogCategory.addFoodSheet)
        }
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

    func onDeleteSubmissionRequested(_ submission: FoodItemSubmissionDomain) {
        submissionPendingDeletion = submission
        isSubmissionDeleteConfirmationVisible = true
    }

    @MainActor
    func onDeleteSubmissionConfirmed() async {
        guard let submission = submissionPendingDeletion else { return }
        submissionPendingDeletion = nil
        let index = mySubmissions.firstIndex { $0.id == submission.id }
        mySubmissions.removeAll { $0.id == submission.id }
        do {
            try await deleteMySubmission(id: submission.id)
        } catch {
            Log.error(error, category: Constants.LogCategory.addFoodSheet)
            if let index { mySubmissions.insert(submission, at: min(index, mySubmissions.count)) }
            alertItem = AlertItem(title: L10n.AddFood.errorWithdrawSubmissionFailed)
        }
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
        recognizedFields = []
        editingSubmissionId = submission.id
        rejectionReasonBeingEdited = submission.rejectReason
        mode = .newItem
        isReviewPushed = true
    }

}
