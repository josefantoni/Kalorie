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
    var caloriesPerHundredGrams: Double = 0
    var fat: Double = 0
    var fatUnsaturatedFattyAcids: Double = 0
    var carbohydrate: Double = 0
    var carbohydratePureSugar: Double = 0
    var protein: Double = 0
    var salt: Double = 0
}

final class AddFoodSheetViewModel: ObservableObject {

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .loading
    @Published var availableFoodItems: [FoodItemDomain] = []
    @Published var searchText = ""
    @Published var formInput = FoodItemFormInput()
    @Published var isAddNewItemVisible = false
    @Published var isScannerVisible: Bool
    @Published var isAlertVisible = false
    @Published var alertTitle = ""
    @Published private(set) var shouldDismiss = false

    var foodsFiltered: [FoodItemDomain] {
        if searchText.isEmpty {
            return availableFoodItems
        }
        return availableFoodItems.filter { $0.name.lowercased().contains(searchText.lowercased()) }
    }

    private let fetchFoodItems: any FetchFoodItemsUseCaseProtocol
    private let createFoodItem: any CreateFoodItemUseCaseProtocol

    // MARK: - Init

    init(
        fetchFoodItems: any FetchFoodItemsUseCaseProtocol,
        createFoodItem: any CreateFoodItemUseCaseProtocol,
        isScannerVisible: Bool = false
    ) {
        self.isScannerVisible = isScannerVisible
        self.fetchFoodItems = fetchFoodItems
        self.createFoodItem = createFoodItem
    }

    // MARK: - Functions

    @MainActor
    func onAppear() async {
        state = .loading
        do {
            availableFoodItems = try await fetchFoodItems()
            state = .loaded
        } catch {
            state = .error(error)
        }
    }

    @MainActor
    func onCreateFoodItem() async {
        let item = FoodItemDomain(
            id: formInput.scannedCode,
            name: formInput.name,
            weight: formInput.weightOfProduct,
            date: .now,
            caloriesPerHundredGrams: formInput.caloriesPerHundredGrams,
            fat: formInput.fat,
            fatUnsaturatedFattyAcids: formInput.fatUnsaturatedFattyAcids,
            carbohydrate: formInput.carbohydrate,
            carbohydratePureSugar: formInput.carbohydratePureSugar,
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
            case nil:
                alertTitle = L10n.Common.errorUnknown
            }
            isAlertVisible = true
        }
    }
}
