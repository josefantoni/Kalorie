//
//  MyCreatedMealEditorView.swift
//  Kalorie
//
//  Created by Josef Antoni on 20.08.2026.
//

import SwiftUI
import VisionKit

struct MyCreatedMealEditorView: View {

    // MARK: - Properties

    @StateObject private var viewModel: MyCreatedMealEditorViewModel
    @Environment(\.dismiss) private var dismiss
    @FocusState private var focusedIngredientId: UUID?

    // MARK: - Init

    init(viewModel: MyCreatedMealEditorViewModel) {
        self._viewModel = StateObject(wrappedValue: viewModel)
    }

    // MARK: - Body

    var body: some View {
        ZStack {
            List {
                Section(header: Text(L10n.MyCreatedMeal.fieldNameTitle)) {
                    TextField(L10n.MyCreatedMeal.fieldNamePlaceholder, text: $viewModel.name)
                }

                if !viewModel.ingredients.isEmpty {
                    Section(header: Text(L10n.MyCreatedMeal.sectionIngredients)) {
                        ForEach($viewModel.ingredients) { $draft in
                            ingredientRow($draft)
                        }
                        .onDelete { viewModel.onDeleteIngredient(at: $0) }
                    }
                }

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

                if !viewModel.searchText.isEmpty {
                    Section(header: Text(L10n.AddFood.sectionExternalResults)) {
                        if !viewModel.searchResults.isEmpty {
                            ForEach(viewModel.searchResults, id: \.id) { item in
                                FoodItemRow(item: item, isFavourite: false)
                                    .onTapGesture {
                                        focusedIngredientId = viewModel.onSelectSearchResult(item)
                                    }
                            }
                        } else if viewModel.isExternalSearchLoading {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                        } else {
                            ForEach(viewModel.externalSearchResults, id: \.id) { item in
                                Text(item.displayName)
                                    .onTapGesture {
                                        focusedIngredientId = viewModel.onSelectSearchResult(item)
                                    }
                            }
                        }
                    }
                }
            }

            startDataScannerIfPossible
        }
        .navigationTitle(viewModel.title)
        .navigationBarTitleDisplayMode(.inline)
        .loader(viewModel.state.isLoading)
        .task(id: viewModel.searchText) { await viewModel.onSearchTextChanged() }
        .onChange(of: focusedIngredientId) { oldValue, newValue in
            guard let oldValue, oldValue != newValue else { return }
            viewModel.onGramsFieldDefocused(id: oldValue)
        }
        .onChange(of: viewModel.lastScannedBarcode) { _, newValue in
            guard !newValue.isEmpty else { return }
            Task { await viewModel.onBarcodeScanned() }
        }
        .onChange(of: viewModel.scannedIngredientId) { _, newValue in
            guard let newValue else { return }
            focusedIngredientId = newValue
        }
        .onChange(of: viewModel.shouldDismiss) {
            if viewModel.shouldDismiss { dismiss() }
        }
        .alert(item: $viewModel.alertItem) { item in
            Alert(
                title: Text(item.title),
                dismissButton: .default(Text(L10n.Common.ok))
            )
        }
        .alert(viewModel.confirmationTitle, isPresented: $viewModel.isSaveConfirmationVisible) {
            Button(L10n.Common.buttonNo, role: .cancel) {}
            Button(L10n.Common.buttonYes) {
                Task { await viewModel.onSaveConfirmed() }
            }
        }
        .toolbar {
            if !viewModel.isEditing {
                DismissToolbarItem()
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    viewModel.onSaveTapped()
                } label: {
                    Image(systemName: "checkmark")
                }
                .disabled(!viewModel.canSave)
            }
        }
    }

    // MARK: - Functions

    @ViewBuilder private var startDataScannerIfPossible: some View {
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

    private func ingredientRow(_ draft: Binding<MyCreatedMealIngredientDraft>) -> some View {
        HStack {
            Text(draft.wrappedValue.item.displayName)
                .frame(maxWidth: .infinity, alignment: .leading)
            TextField("100", text: draft.gramsText)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .frame(width: 50)
                .focused($focusedIngredientId, equals: draft.wrappedValue.id)
                .onChange(of: draft.gramsText.wrappedValue) { _, text in
                    var seenSeparator = false
                    let sanitized = String(text.filter { char in
                        if char == "." || char == "," {
                            if seenSeparator { return false }
                            seenSeparator = true
                            return true
                        }
                        return char.isNumber
                    })
                    if sanitized != text {
                        draft.gramsText.wrappedValue = sanitized
                    }
                }
            Text("g")
                .foregroundStyle(.secondary)
        }
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        MyCreatedMealEditorView(
            viewModel: MyCreatedMealEditorViewModel(
                searchFoodItems: SearchFoodItemsUseCaseFake(),
                searchFoodExternally: SearchFoodExternallyUseCaseFake(),
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
                fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseFake(),
                createMyCreatedMeal: CreateMyCreatedMealUseCaseFake(),
                updateMyCreatedMeal: UpdateMyCreatedMealUseCaseFake()
            )
        )
    }
}
