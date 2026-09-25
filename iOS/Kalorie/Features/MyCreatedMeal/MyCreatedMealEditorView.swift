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
        List {
            Section {
                TextField(L10n.MyCreatedMeal.fieldNamePlaceholder, text: $viewModel.name)
            }

            if !viewModel.ingredients.isEmpty {
                Section(header: Text(L10n.MyCreatedMeal.sectionIngredients)) {
                    ForEach($viewModel.ingredients) { $draft in
                        ingredientRow($draft)
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    if let index = viewModel.ingredients.firstIndex(where: { $0.id == draft.id }) {
                                        viewModel.onDeleteIngredient(at: IndexSet(integer: index))
                                    }
                                } label: {
                                    Image(systemName: "trash")
                                }
                            }
                    }
                }
            }

            Section(header: Text(L10n.MyCreatedMeal.sectionCatalogue)) {
                HStack {
                    TextField(viewModel.searchPlaceholder, text: $viewModel.searchText)
                    BaseButton(
                        style: .plain,
                        imageName: .barCode,
                        imageSize: .basic
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
                Section(header: Text(viewModel.searchResults.isEmpty ? L10n.AddFood.sectionExternalResults : L10n.AddFood.sectionSearchResults)) {
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
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    focusedIngredientId = viewModel.onSelectSearchResult(item)
                                }
                        }
                    }
                }
            }

            FoodPortionsSection(portions: $viewModel.portions)
        }
        .contentMargins(.top, 0, for: .scrollContent)
        .navigationTitle(viewModel.title)
        .navigationBarTitleDisplayMode(.inline)
        .keyboardDoneToolbar()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(L10n.MyCreatedMeal.buttonSave) {
                    viewModel.onSaveTapped()
                }
                .disabled(!viewModel.canSave)
            }
        }
        .loader(viewModel.state.isLoading)
        .fullScreenCover(isPresented: $viewModel.isScannerVisible) {
            BarcodeScannerOverlay(
                scannedCode: $viewModel.lastScannedBarcode,
                isSearching: viewModel.isBarcodeSearchLoading
            ) {
                viewModel.isScannerVisible = false
            }
        }
        .task { await viewModel.onAppear() }
        .task(id: viewModel.searchText) { await viewModel.onSearchTextChanged() }
        .onChange(of: focusedIngredientId) { oldValue, newValue in
            guard let oldValue, oldValue != newValue else { return }
            viewModel.onGramsFieldDefocused(id: oldValue)
        }
        .task(id: viewModel.lastScannedBarcode) {
            guard !viewModel.lastScannedBarcode.isEmpty else { return }
            await viewModel.onBarcodeScanned()
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
                message: item.message.map(Text.init),
                dismissButton: .default(Text(L10n.Common.ok))
            )
        }
        .alert(viewModel.confirmationTitle, isPresented: $viewModel.isSaveConfirmationVisible) {
            Button(L10n.Common.buttonNo, role: .cancel) {}
            Button(L10n.Common.buttonYes) {
                Task { await viewModel.onSaveConfirmed() }
            }
        }
    }

    // MARK: - Functions

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
                fetchFoodItemsByIds: FetchFoodItemsByIdsUseCaseFake(),
                createMyCreatedMeal: CreateMyCreatedMealUseCaseFake(),
                updateMyCreatedMeal: UpdateMyCreatedMealUseCaseFake()
            )
        )
    }
}
