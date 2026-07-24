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
    @Environment(\.dismiss) var dismiss

    // MARK: - Init

    init(viewModel: AddFoodSheetViewModel) {
        self._viewModel = StateObject(wrappedValue: viewModel)
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
                                .rotation3DEffect(Angle(degrees: 180), axis: (x: 0, y: 1.0, z: 0))
                        }
                    }
                    .rotation3DEffect(
                        .degrees(viewModel.isAddNewItemVisible ? 180 : 0),
                        axis: (x: 0, y: 1, z: 0)
                    )

                    startDataScannerIfPossible
                }
            }
            .loader(viewModel.state.isLoading)
            .alert(isPresented: $viewModel.isAlertVisible) {
                Alert(
                    title: Text(viewModel.alertTitle),
                    dismissButton: Alert.Button.default(Text(L10n.Common.ok))
                )
            }
            .task(id: viewModel.searchText) { await viewModel.onSearchTextChanged() }
            .task(id: viewModel.lastScannedBarcode) { await viewModel.onBarcodeScanned() }
            .onChange(of: viewModel.shouldDismiss) {
                if viewModel.shouldDismiss { dismiss() }
            }
            .navigationDestination(isPresented: $viewModel.isPushedToQuantityView) {
                if let item = viewModel.selectedFoodItem {
                    FoodQuantityView(viewModel: viewModel.makeQuantityViewModel(for: item))
                }
            }
            .toolbar {
                DismissToolbarItem()
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        withAnimation { viewModel.isAddNewItemVisible.toggle() }
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
                    TextField(L10n.AddFood.searchPlaceholder, text: $viewModel.searchText)
                    BaseButton(
                        style: .plain,
                        imageName: .barCode,
                        imageSize: .medium
                    ) {
                        if DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
                            viewModel.onScannerButtonTapped()
                        } else {
                            viewModel.alertTitle = L10n.AddFood.cameraPermissionAlert
                            viewModel.isAlertVisible.toggle()
                        }
                    }
                }
            }
            Section(header: Text(L10n.AddFood.sectionSearchResults)) {
                ForEach(viewModel.localFoodItems, id: \.id) { item in
                    Text(item.displayName)
                        .onTapGesture {
                            viewModel.onSelectFoodItem(item)
                        }
                }
            }
            if viewModel.localFoodItems.isEmpty && !viewModel.searchText.isEmpty {
                Section(header: Text(L10n.AddFood.sectionExternalResults)) {
                    if viewModel.isExternalSearchLoading {
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
                        weight: $viewModel.formInput.weightOfProduct
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldEnergyKJ,
                        weight: $viewModel.formInput.energyKJ
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldCaloriesPer100g,
                        weight: $viewModel.formInput.caloriesPerHundredGrams
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldProtein,
                        weight: $viewModel.formInput.protein
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldCarbs,
                        weight: $viewModel.formInput.carbohydrate
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldCarbsSugar,
                        weight: $viewModel.formInput.carbohydratePureSugar
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldFiber,
                        weight: $viewModel.formInput.fiber
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldFat,
                        weight: $viewModel.formInput.fat
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldFatSaturated,
                        weight: $viewModel.formInput.fatSaturated
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldFatUnsaturated,
                        weight: $viewModel.formInput.fatUnsaturatedFattyAcids
                    )
                    BaseDoubleTextField(
                        title: L10n.AddFood.fieldSalt,
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
            saveFoodConsumed: SaveFoodConsumedUseCaseFake(),
            searchFoodExternally: SearchFoodExternallyUseCaseFake(),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
            fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseFake(),
            selectedDate: .now
        )
    )
}
