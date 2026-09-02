//
//  AddFoodSheetView.swift
//  Kalorie
//
//  Created by Josef Antoni on 17.06.2024.
//

import Foundation
import SwiftUI
import VisionKit

struct AddFoodSheetView: View {

    // MARK: - Properties

    @StateObject var viewModel: AddFoodSheetViewModel
    @State private var flipAngle: Double = 0
    @Environment(\.dismiss) var dismiss
    private let makeFoodQuantityView: (FoodItemDomain, Bool, Bool, @escaping () -> Void, @escaping (String, Bool) -> Void) -> FoodQuantityView

    // MARK: - Init

    init(
        viewModel: AddFoodSheetViewModel,
        makeFoodQuantityView: @escaping (FoodItemDomain, Bool, Bool, @escaping () -> Void, @escaping (String, Bool) -> Void) -> FoodQuantityView
    ) {
        self._viewModel = StateObject(wrappedValue: viewModel)
        self.makeFoodQuantityView = makeFoodQuantityView
    }

    // MARK: - Body

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ZStack {
                    Group {
                        if !viewModel.isAddNewItemVisible {
                            addFoodItem
                        } else {
                            addCustomFoodItem
                        }
                    }
                    .rotation3DEffect(.degrees(flipAngle), axis: (x: 0, y: 1, z: 0))

                    startDataScannerIfPossible
                }
            }
            .loader(viewModel.state.isLoading)
            .alert(item: $viewModel.alertItem) { item in
                Alert(
                    title: Text(item.title),
                    message: item.message.map(Text.init),
                    dismissButton: Alert.Button.default(Text(L10n.Common.ok))
                )
            }
            .task { await viewModel.onAppear() }
            .task(id: viewModel.searchText) { await viewModel.onSearchTextChanged() }
            .onChange(of: viewModel.lastScannedBarcode) { _, newValue in
                guard !newValue.isEmpty else { return }
                Task { await viewModel.onBarcodeScanned() }
            }
            .onChange(of: viewModel.shouldDismiss) {
                if viewModel.shouldDismiss { dismiss() }
            }
            .navigationDestination(isPresented: $viewModel.isPushedToQuantityView) {
                if let item = viewModel.selectedFoodItem {
                    makeFoodQuantityView(item, viewModel.isFavourite(item), viewModel.isMyCreatedMeal(item), viewModel.onFoodConsumedSaved) { id, isFavourite in
                        viewModel.onFavouriteChanged(id: id, isFavourite: isFavourite, item: item)
                    }
                }
            }
            .toolbar {
                DismissToolbarItem()
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        let target = !viewModel.isAddNewItemVisible
                        withAnimation(.easeIn(duration: 0.2)) {
                            flipAngle = 90
                        } completion: {
                            viewModel.isAddNewItemVisible = target
                            withAnimation(.easeOut(duration: 0.2)) {
                                flipAngle = 0
                            }
                        }
                    } label: {
                        BaseImage(
                            imageName: .carrotFill,
                            imageSize: 17
                        )
                    }
                    .clipShape(Circle())
                    .buttonStyle(.borderedProminent)
                }
            }
            .background(Color(.secondarySystemBackground))
        }
    }

    // MARK: - Functions

    @ViewBuilder var startDataScannerIfPossible: some View {
        if viewModel.isScannerVisible && DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
            ZStack {
                DataScannerRepresentable(
                    scannedCode: $viewModel.lastScannedBarcode,
                    isSearching: viewModel.isBarcodeSearchLoading
                )
                if viewModel.isBarcodeSearchLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(.ultraThinMaterial)
                }
            }
        }
    }

    var addFoodItem: some View {
        List {
            Section {
                HStack {
                    TextField(viewModel.searchPlaceholder, text: $viewModel.searchText)
                    BaseButton(
                        style: .plain,
                        imageName: .barCode,
                        imageSize: .medium
                    ) {
                        if DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
                            viewModel.onScannerButtonTapped()
                        } else {
                            viewModel.alertItem = AlertItem(title: L10n.AddFood.cameraPermissionAlert)
                        }
                    }
                }
            }
            if viewModel.searchText.isEmpty && !viewModel.myCreatedMeals.isEmpty {
                Section(header: Text(L10n.AddFood.sectionMyCreatedMeals)) {
                    ForEach(viewModel.myCreatedMeals, id: \.id) { meal in
                        FoodItemRow(item: meal.asFoodItem(), isFavourite: false)
                            .onTapGesture {
                                viewModel.onSelectFoodItem(meal.asFoodItem())
                            }
                    }
                }
            }
            if viewModel.searchText.isEmpty && !viewModel.favouriteFoods.isEmpty {
                Section(header: Text(L10n.AddFood.sectionFavourites)) {
                    ForEach(viewModel.favouriteFoods, id: \.id) { item in
                        FoodItemRow(item: item, isFavourite: true)
                            .onTapGesture {
                                viewModel.onSelectFoodItem(item)
                            }
                    }
                }
            }
            if !viewModel.displayedResults.isEmpty || !viewModel.searchText.isEmpty {
                Section(header: Text(L10n.AddFood.sectionExternalResults)) {
                    if !viewModel.displayedResults.isEmpty {
                        ForEach(viewModel.displayedResults, id: \.id) { item in
                            FoodItemRow(item: item, isFavourite: viewModel.isFavourite(item))
                                .onTapGesture {
                                    viewModel.onSelectFoodItem(item)
                                }
                        }
                    } else if viewModel.isExternalSearchLoading {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    } else {
                        ForEach(viewModel.externalFoodItems, id: \.id) { item in
                            Text(item.displayName)
                                .onTapGesture {
                                    viewModel.onSelectFoodItem(item)
                                }
                        }
                    }
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            Button {
                viewModel.onCreateMealButtonTapped()
            } label: {
                Text(L10n.AddFood.buttonCreateMeal)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
            }
            .glassEffect(.regular, in: .capsule)
            .padding(.bottom, 8)
        }
    }

    var addCustomFoodItem: some View {
        VStack(spacing: 0) {
            List {
                Section(header: Text(L10n.AddFood.sectionNewItem)) {
                    BaseStringTextField(
                        placeholder: L10n.AddFood.fieldBarcodePlaceholder,
                        title: L10n.AddFood.fieldBarcodeTitle,
                        text: $viewModel.formInput.scannedCode
                    )
                    BaseStringTextField(
                        placeholder: L10n.AddFood.fieldNamePlaceholder,
                        title: L10n.AddFood.fieldNameTitle,
                        text: $viewModel.formInput.name
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldWeight,
                        unit: "g",
                        weight: $viewModel.formInput.weightOfProduct
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldEnergyKJ,
                        unit: "kJ",
                        weight: $viewModel.formInput.energyKJ
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldCaloriesPer100g,
                        unit: "kcal",
                        weight: $viewModel.formInput.caloriesPerHundredGrams
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldProtein,
                        unit: "g",
                        weight: $viewModel.formInput.protein
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldCarbs,
                        unit: "g",
                        weight: $viewModel.formInput.carbohydrate
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldCarbsSugar,
                        unit: "g",
                        weight: $viewModel.formInput.carbohydratePureSugar
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldFiber,
                        unit: "g",
                        weight: $viewModel.formInput.fiber
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldFat,
                        unit: "g",
                        weight: $viewModel.formInput.fat
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldFatSaturated,
                        unit: "g",
                        weight: $viewModel.formInput.fatSaturated
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldFatUnsaturated,
                        unit: "g",
                        weight: $viewModel.formInput.fatUnsaturatedFattyAcids
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldSalt,
                        unit: "g",
                        weight: $viewModel.formInput.salt
                    )
                }
            }
            addButton
                .padding(.horizontal)
                .padding(.vertical, 12)
                .background(Color(.secondarySystemBackground))
        }
    }

    var addButton: some View {
        Button {
            Task { await viewModel.onCreateFoodItem() }
        } label: {
            Text(L10n.AddFood.buttonAdd)
                .frame(maxWidth: .infinity)
                .frame(height: 35)
                .font(.system(size: .basic, weight: .bold))
        }
        .buttonStyle(.borderedProminent)
    }
}

// MARK: - Preview

#Preview {
    AddFoodSheetView(
        viewModel: AddFoodSheetViewModel(
            searchFoodItems: SearchFoodItemsUseCaseFake(),
            createFoodItem: CreateFoodItemUseCaseFake(),
            searchFoodExternally: SearchFoodExternallyUseCaseFake(),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
            fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseFake(),
            fetchFavouriteFoods: FetchFavouriteFoodsUseCaseFake(),
            fetchMyCreatedMeals: FetchMyCreatedMealsUseCaseFake()
        )
    ) { item, isFavourite, isMyCreatedMeal, onSaved, onFavouriteChanged in
        FoodQuantityView(
            viewModel: FoodQuantityViewModel(
                item: item,
                saveFoodConsumed: SaveFoodConsumedUseCaseFake(),
                selectedDate: .now,
                isFavourite: isFavourite,
                addFavouriteFood: AddFavouriteFoodUseCaseFake(),
                removeFavouriteFood: RemoveFavouriteFoodUseCaseFake(),
                onSaved: onSaved,
                onFavouriteChanged: onFavouriteChanged,
                quantity: isMyCreatedMeal ? item.weight : 1,
                unit: isMyCreatedMeal ? .grams : .hundredGrams
            )
        )
    }
}
