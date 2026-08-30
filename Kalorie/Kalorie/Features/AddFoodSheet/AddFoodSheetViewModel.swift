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
    @Published private(set) var favouriteFoods: [FoodItemDomain] = []
    @Published private(set) var favouriteIds: Set<String> = []
    @Published private(set) var myCreatedMeals: [MyCreatedMealDomain] = []
    let searchPlaceholder: String

    private let searchFoodItems: any SearchFoodItemsUseCaseProtocol
    private let createFoodItem: any CreateFoodItemUseCaseProtocol
    private let searchFoodExternally: any SearchFoodExternallyUseCaseProtocol
    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    private let fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol
    private let fetchFavouriteFoods: any FetchFavouriteFoodsUseCaseProtocol
    private let fetchMyCreatedMeals: any FetchMyCreatedMealsUseCaseProtocol
    private let onFoodSaved: () -> Void
    private let onCreateMealRequested: () -> Void

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
        let matchingIds = mealIds.union(matchingFavouritesFiltered.map(\.id))
        return matchingMeals + matchingFavouritesFiltered
            + localFoodItems.filter { !matchingIds.contains($0.id) }
    }

    // MARK: - Init

    init(
        searchFoodItems: any SearchFoodItemsUseCaseProtocol,
        createFoodItem: any CreateFoodItemUseCaseProtocol,
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
        self.createFoodItem = createFoodItem
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
        async let favourites = try? fetchFavouriteFoods()
        async let meals = try? fetchMyCreatedMeals()
        if let items = await favourites {
            favouriteFoods = items
            favouriteIds = Set(items.map(\.id))
        }
        if let items = await meals {
            myCreatedMeals = items
        }
    }

    func isFavourite(_ item: FoodItemDomain) -> Bool {
        favouriteIds.contains(item.id)
    }

    func isMyCreatedMeal(_ item: FoodItemDomain) -> Bool {
        item.kind == .createdMeal
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
        let item = FoodItemDomain(
            id: formInput.scannedCode,
            kind: .catalogue,
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
