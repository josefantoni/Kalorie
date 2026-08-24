//
//  MyCreatedMealEditorViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 20.08.2026.
//

import Foundation

struct MyCreatedMealIngredientDraft: Identifiable {

    // MARK: - Properties

    let id = UUID()
    let item: FoodItemDomain
    var gramsText: String
}

final class MyCreatedMealEditorViewModel: ObservableObject {

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .idle
    @Published var name: String
    @Published var ingredients: [MyCreatedMealIngredientDraft]
    @Published var searchText = ""
    @Published private(set) var searchResults: [FoodItemDomain] = []
    @Published private(set) var externalSearchResults: [FoodItemDomain] = []
    @Published private(set) var isExternalSearchLoading = false
    @Published var isScannerVisible = false
    @Published var lastScannedBarcode = ""
    @Published private(set) var isBarcodeSearchLoading = false
    @Published private(set) var scannedIngredientId: UUID?
    @Published var alertItem: AlertItem?
    @Published var isSaveConfirmationVisible = false
    @Published private(set) var shouldDismiss = false
    let searchPlaceholder: String

    private let existingMeal: MyCreatedMealDomain?
    private let initialName: String
    private let initialIngredients: [MyCreatedMealIngredientDomain]
    private let searchFoodItems: any SearchFoodItemsUseCaseProtocol
    private let searchFoodExternally: any SearchFoodExternallyUseCaseProtocol
    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    private let fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol
    private let createMyCreatedMeal: any CreateMyCreatedMealUseCaseProtocol
    private let updateMyCreatedMeal: any UpdateMyCreatedMealUseCaseProtocol
    private let onSaved: () -> Void

    var isEditing: Bool { existingMeal != nil }

    var title: String { isEditing ? L10n.MyCreatedMeal.titleEdit : L10n.MyCreatedMeal.titleNew }

    var confirmationTitle: String { isEditing ? L10n.MyCreatedMeal.confirmUpdate : L10n.MyCreatedMeal.confirmCreate }

    var ingredientDomains: [MyCreatedMealIngredientDomain] { Self.ingredientDomains(from: ingredients) }

    var hasChanges: Bool { name != initialName || ingredientDomains != initialIngredients }

    var canSave: Bool {
        guard MyCreatedMealValidation.canSave(name: name, ingredients: ingredientDomains) else { return false }
        return !isEditing || hasChanges
    }

    // MARK: - Init

    init(
        searchFoodItems: any SearchFoodItemsUseCaseProtocol,
        searchFoodExternally: any SearchFoodExternallyUseCaseProtocol,
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol,
        fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol,
        createMyCreatedMeal: any CreateMyCreatedMealUseCaseProtocol,
        updateMyCreatedMeal: any UpdateMyCreatedMealUseCaseProtocol,
        existingMeal: MyCreatedMealDomain? = nil,
        onSaved: @escaping () -> Void = {}
    ) {
        self.existingMeal = existingMeal
        self.searchFoodItems = searchFoodItems
        self.searchFoodExternally = searchFoodExternally
        self.fetchFoodItemByBarcode = fetchFoodItemByBarcode
        self.fetchFoodByBarcodeExternally = fetchFoodByBarcodeExternally
        self.createMyCreatedMeal = createMyCreatedMeal
        self.updateMyCreatedMeal = updateMyCreatedMeal
        self.onSaved = onSaved
        let example = L10n.AddFood.searchExamples.randomElement() ?? ""
        searchPlaceholder = L10n.AddFood.searchPlaceholder(example: example)
        let resolvedName = existingMeal?.name ?? ""
        let resolvedIngredients = (existingMeal?.ingredients ?? []).map { ingredient in
            MyCreatedMealIngredientDraft(
                item: FoodItemDomain(
                    id: ingredient.foodItemId,
                    czName: ingredient.czName,
                    engName: ingredient.engName,
                    weight: ingredient.grams,
                    date: .now,
                    nutrition: ingredient.nutrition
                ),
                gramsText: Self.formattedGrams(ingredient.grams)
            )
        }
        name = resolvedName
        ingredients = resolvedIngredients
        initialName = resolvedName
        initialIngredients = Self.ingredientDomains(from: resolvedIngredients)
    }

    // MARK: - Functions

    @MainActor
    func onSearchTextChanged() async {
        guard !searchText.isEmpty else {
            searchResults = []
            externalSearchResults = []
            return
        }
        do {
            try await Task.sleep(for: .milliseconds(300))
        } catch {
            return
        }
        do {
            searchResults = try await searchFoodItems(query: searchText)
        } catch {
            searchResults = []
        }
        guard searchResults.isEmpty && searchText.count >= 3 else {
            externalSearchResults = []
            return
        }
        isExternalSearchLoading = true
        defer { isExternalSearchLoading = false }
        do {
            externalSearchResults = try await searchFoodExternally(query: searchText)
        } catch {
            externalSearchResults = []
        }
    }

    func onScannerButtonTapped() {
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
            scannedIngredientId = onSelectSearchResult(local)
            return
        }
        do {
            if let external = try await fetchFoodByBarcodeExternally(barcode: barcode) {
                isScannerVisible = false
                scannedIngredientId = onSelectSearchResult(external)
                return
            }
        } catch {
            alertItem = AlertItem(title: L10n.AddFood.errorLoadFailed)
            return
        }
        alertItem = AlertItem(title: L10n.AddFood.errorBarcodeNotFound)
    }

    @discardableResult
    func onSelectSearchResult(_ item: FoodItemDomain) -> UUID {
        let draft = MyCreatedMealIngredientDraft(item: item, gramsText: "")
        ingredients.append(draft)
        searchText = ""
        searchResults = []
        externalSearchResults = []
        return draft.id
    }

    func onGramsFieldDefocused(id: UUID) {
        guard
            let index = ingredients.firstIndex(where: { $0.id == id }),
            ingredients[index].gramsText.isEmpty
        else { return }
        ingredients[index].gramsText = "100"
    }

    func onDeleteIngredient(at offsets: IndexSet) {
        ingredients.remove(atOffsets: offsets)
    }

    func onSaveTapped() {
        guard canSave else { return }
        isSaveConfirmationVisible = true
    }

    @MainActor
    func onSaveConfirmed() async {
        guard !state.isLoading else { return }
        state = .loading
        defer { state = .loaded }
        do {
            if let existingMeal {
                try await updateMyCreatedMeal(
                    MyCreatedMealDomain(
                        id: existingMeal.id,
                        name: name,
                        ingredients: ingredientDomains,
                        createdAt: existingMeal.createdAt,
                        updatedAt: existingMeal.updatedAt
                    )
                )
            } else {
                _ = try await createMyCreatedMeal(name: name, ingredients: ingredientDomains)
            }
            onSaved()
            shouldDismiss = true
        } catch {
            alertItem = AlertItem(title: L10n.MyCreatedMeal.errorSaveFailed)
        }
    }

    // MARK: - Private

    private static func ingredientDomains(from ingredients: [MyCreatedMealIngredientDraft]) -> [MyCreatedMealIngredientDomain] {
        ingredients.map { draft in
            MyCreatedMealIngredientDomain(
                foodItemId: draft.item.id,
                czName: draft.item.czName,
                engName: draft.item.engName,
                grams: Self.parsedGrams(draft.gramsText),
                nutrition: draft.item.nutrition
            )
        }
    }

    private static func parsedGrams(_ text: String) -> Double {
        Double(text.replacingOccurrences(of: ",", with: ".")) ?? 0
    }

    private static func formattedGrams(_ value: Double) -> String {
        value.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(value)) : String(value)
    }
}
