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
    @Published var alertItem: AlertItem?
    @Published private(set) var shouldDismiss = false
    @Published var isPushedToQuantityView = false
    @Published private(set) var selectedFoodItem: FoodItemDomain?
    @Published var lastScannedBarcode = ""
    @Published private(set) var isBarcodeSearchLoading = false

    private let searchFoodItems: any SearchFoodItemsUseCaseProtocol
    private let createFoodItem: any CreateFoodItemUseCaseProtocol
    private let searchFoodExternally: any SearchFoodExternallyUseCaseProtocol
    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    private let fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol
    private let onFoodSaved: () -> Void

    // MARK: - Init

    init(
        searchFoodItems: any SearchFoodItemsUseCaseProtocol,
        createFoodItem: any CreateFoodItemUseCaseProtocol,
        searchFoodExternally: any SearchFoodExternallyUseCaseProtocol,
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol,
        fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol,
        onFoodSaved: @escaping () -> Void = {},
        isScannerVisible: Bool = false
    ) {
        self.isScannerVisible = isScannerVisible
        self.searchFoodItems = searchFoodItems
        self.createFoodItem = createFoodItem
        self.searchFoodExternally = searchFoodExternally
        self.fetchFoodItemByBarcode = fetchFoodItemByBarcode
        self.fetchFoodByBarcodeExternally = fetchFoodByBarcodeExternally
        self.onFoodSaved = onFoodSaved
    }

    // MARK: - Functions

    func onScannerButtonTapped() {
        isAddNewItemVisible = false
        isScannerVisible = true
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
        do {
            if let external = try await fetchFoodByBarcodeExternally(barcode: barcode) {
                isScannerVisible = false
                onSelectFoodItem(external)
                return
            }
        } catch {
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
            alertItem = AlertItem(title: L10n.AddFood.errorLoadFailed)
            return
        }
        guard localFoodItems.isEmpty && searchText.count >= 3 else {
            externalFoodItems = []
            return
        }
        isExternalSearchLoading = true
        defer { isExternalSearchLoading = false }
        do {
            externalFoodItems = try await searchFoodExternally(query: searchText)
        } catch {
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
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidCode)
            case .invalidName:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidName)
            case .invalidCalories:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidCalories)
            case .invalidWeight:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidWeight)
            case .itemAlreadyExists:
                alertItem = AlertItem(title: L10n.AddFood.errorItemAlreadyExists)
            case nil:
                alertItem = AlertItem(title: L10n.Common.errorUnknown)
            }
        }
    }
}
