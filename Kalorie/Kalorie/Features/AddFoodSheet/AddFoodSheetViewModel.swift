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
    var weightOfProduct: Double = 0
    var energyKJ: Double = 0
    var caloriesPerHundredGrams: Double = 0
    var fat: Double = 0
    var fatSaturated: Double = 0
    var fatUnsaturatedFattyAcids: Double = 0
    var carbohydrate: Double = 0
    var carbohydratePureSugar: Double = 0
    var fiber: Double = 0
    var protein: Double = 0
    var salt: Double = 0
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
    @Published var isAlertVisible = false
    @Published var alertTitle = ""
    @Published private(set) var shouldDismiss = false
    @Published var isPushedToQuantityView = false
    @Published private(set) var selectedFoodItem: FoodItemDomain?
    @Published var lastScannedBarcode = ""
    @Published private(set) var isBarcodeSearchLoading = false

    private let searchFoodItems: any SearchFoodItemsUseCaseProtocol
    private let createFoodItem: any CreateFoodItemUseCaseProtocol
    private let saveFoodConsumed: any SaveFoodConsumedUseCaseProtocol
    private let searchFoodExternally: any SearchFoodExternallyUseCaseProtocol
    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    private let fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol
    private let selectedDate: Date
    private let onFoodSaved: () -> Void

    // MARK: - Init

    init(
        searchFoodItems: any SearchFoodItemsUseCaseProtocol,
        createFoodItem: any CreateFoodItemUseCaseProtocol,
        saveFoodConsumed: any SaveFoodConsumedUseCaseProtocol,
        searchFoodExternally: any SearchFoodExternallyUseCaseProtocol,
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol,
        fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol,
        selectedDate: Date,
        onFoodSaved: @escaping () -> Void = {},
        isScannerVisible: Bool = false
    ) {
        self.isScannerVisible = isScannerVisible
        self.searchFoodItems = searchFoodItems
        self.createFoodItem = createFoodItem
        self.saveFoodConsumed = saveFoodConsumed
        self.searchFoodExternally = searchFoodExternally
        self.fetchFoodItemByBarcode = fetchFoodItemByBarcode
        self.fetchFoodByBarcodeExternally = fetchFoodByBarcodeExternally
        self.selectedDate = selectedDate
        self.onFoodSaved = onFoodSaved
    }

    // MARK: - Functions

    func onScannerButtonTapped() {
        isAddNewItemVisible = false
        isScannerVisible.toggle()
    }

    @MainActor
    func onBarcodeScanned() async {
        let barcode = lastScannedBarcode
        guard !barcode.isEmpty else { return }
        lastScannedBarcode = ""
        isBarcodeSearchLoading = true
        defer { isBarcodeSearchLoading = false }
        if let local = try? await fetchFoodItemByBarcode(barcode: barcode) {
            isScannerVisible = false
            onSelectFoodItem(local)
            return
        }
        if let external = try? await fetchFoodByBarcodeExternally(barcode: barcode) {
            isScannerVisible = false
            onSelectFoodItem(external)
            return
        }
        alertTitle = L10n.AddFood.errorBarcodeNotFound
        isAlertVisible = true
    }

    @MainActor
    func onSearchTextChanged() async {
        guard !searchText.isEmpty else {
            localFoodItems = []
            externalFoodItems = []
            return
        }
        try? await Task.sleep(for: .milliseconds(300))
        guard !Task.isCancelled else { return }
        localFoodItems = (try? await searchFoodItems(query: searchText)) ?? []
        guard localFoodItems.isEmpty && searchText.count >= 3 else {
            externalFoodItems = []
            return
        }
        isExternalSearchLoading = true
        defer { isExternalSearchLoading = false }
        externalFoodItems = (try? await searchFoodExternally(query: searchText)) ?? []
    }

    func onSelectFoodItem(_ item: FoodItemDomain) {
        selectedFoodItem = item
        isPushedToQuantityView = true
    }

    func makeQuantityViewModel(for item: FoodItemDomain) -> FoodQuantityViewModel {
        FoodQuantityViewModel(
            item: item,
            saveFoodConsumed: saveFoodConsumed,
            selectedDate: selectedDate
        ) { [weak self] in
            self?.onFoodSaved()
            self?.shouldDismiss = true
        }
    }

    @MainActor
    func onCreateFoodItem() async {
        state = .loading
        defer { state = .loaded }
        let item = FoodItemDomain(
            id: formInput.scannedCode,
            czName: formInput.name,
            engName: "",
            weight: formInput.weightOfProduct,
            date: .now,
            energyKJ: formInput.energyKJ,
            caloriesPerHundredGrams: formInput.caloriesPerHundredGrams,
            fat: formInput.fat,
            fatSaturated: formInput.fatSaturated,
            fatUnsaturatedFattyAcids: formInput.fatUnsaturatedFattyAcids,
            carbohydrate: formInput.carbohydrate,
            carbohydratePureSugar: formInput.carbohydratePureSugar,
            fiber: formInput.fiber,
            protein: formInput.protein,
            salt: formInput.salt
        )
        do {
            _ = try await createFoodItem(item)
            shouldDismiss = true
        } catch {
            switch error as? CreateFoodItemError {
            case .invalidCode:
                alertTitle = L10n.AddFood.errorInvalidCode
            case .invalidName:
                alertTitle = L10n.AddFood.errorInvalidName
            case .invalidCalories:
                alertTitle = L10n.AddFood.errorInvalidCalories
            case .invalidWeight:
                alertTitle = L10n.AddFood.errorInvalidWeight
            case .itemAlreadyExists:
                alertTitle = L10n.AddFood.errorItemAlreadyExists
            case nil:
                alertTitle = L10n.Common.errorUnknown
            }
            isAlertVisible = true
        }
    }
}
