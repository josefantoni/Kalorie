//
//  AddFoodSheetConfigurator.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

struct AddFoodSheetConfigurator {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func createView(
        date: Date,
        mealTypes: [MealTypeDomain],
        onFoodSaved: @escaping () -> Void = {},
        withBarcodeScan: Bool = false
    ) -> AddFoodSheetView {
        let mealEditorConfigurator = MyCreatedMealEditorConfigurator(dataProvider: dataProvider, authProvider: authProvider)
        return AddFoodSheetView(
            viewModel: AddFoodSheetViewModel(
                searchFoodItems: SearchFoodItemsUseCase(dataProvider: dataProvider),
                submitFoodItem: SubmitFoodItemUseCase(dataProvider: dataProvider, authProvider: authProvider),
                fetchMySubmissions: FetchMySubmissionsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                updateMySubmission: UpdateMySubmissionUseCase(dataProvider: dataProvider, authProvider: authProvider),
                deleteMySubmission: DeleteMySubmissionUseCase(dataProvider: dataProvider, authProvider: authProvider),
                searchFoodExternally: SearchFoodExternallyUseCase(),
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCase(dataProvider: dataProvider),
                fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCase(),
                fetchFavouriteFoods: FetchFavouriteFoodsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                refreshFavouriteFood: RefreshFavouriteFoodUseCase(dataProvider: dataProvider, authProvider: authProvider),
                fetchMyCreatedMeals: FetchMyCreatedMealsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                deleteMyCreatedMeal: DeleteMyCreatedMealUseCase(dataProvider: dataProvider, authProvider: authProvider),
                recognizeNutritionLabel: RecognizeNutritionLabelUseCase(),
                cameraAuthorizationProvider: CameraAuthorizationProvider(),
                onFoodSaved: onFoodSaved,
                isScannerVisible: withBarcodeScan
            ),
            makeFoodQuantityView: { [self] item, isFavourite, meal, onSaved, onFavouriteChanged, onMealUpdated in
                FoodQuantityView(
                    viewModel: FoodQuantityViewModel(
                        item: item,
                        saveFoodConsumed: SaveFoodConsumedUseCase(dataProvider: dataProvider, authProvider: authProvider),
                        fetchMealTypes: FetchMealTypesUseCase(dataProvider: dataProvider, authProvider: authProvider),
                        selectedDate: date,
                        mealTypes: mealTypes,
                        isFavourite: isFavourite,
                        addFavouriteFood: AddFavouriteFoodUseCase(dataProvider: dataProvider, authProvider: authProvider),
                        removeFavouriteFood: RemoveFavouriteFoodUseCase(dataProvider: dataProvider, authProvider: authProvider),
                        fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                        saveFoodItemPersonalPortions: SaveFoodItemPersonalPortionsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                        fetchMyFoodItemReport: FetchMyFoodItemReportUseCase(dataProvider: dataProvider, authProvider: authProvider),
                        submitFoodItemReport: SubmitFoodItemReportUseCase(dataProvider: dataProvider, authProvider: authProvider),
                        meal: meal,
                        updateMyCreatedMeal: UpdateMyCreatedMealUseCase(dataProvider: dataProvider, authProvider: authProvider),
                        onSaved: onSaved,
                        onMealUpdated: onMealUpdated,
                        onFavouriteChanged: onFavouriteChanged,
                        quantity: meal != nil ? item.weight : (item.portions.isEmpty ? 100 : 1),
                        unit: meal != nil ? .grams : FoodQuantityViewModel.defaultUnit(for: item)
                    )
                )
            },
            makeMealEditorView: { onSaved in
                mealEditorConfigurator.createView(onSaved: onSaved, dismissesOnSave: false)
            }
        ) { meal, onSaved in
            mealEditorConfigurator.createView(existingMeal: meal, onSaved: onSaved)
        }
    }
}
