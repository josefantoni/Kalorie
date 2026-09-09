//
//  FoodQuantityView.swift
//  Kalorie
//
//  Created by Josef Antoni on 24.07.2026.
//

import SwiftUI

struct FoodQuantityView: View {

    // MARK: - Properties

    @StateObject private var viewModel: FoodQuantityViewModel
    @FocusState private var isQuantityFocused: Bool
    @State private var quantityText = "1"

    // MARK: - Init

    init(viewModel: FoodQuantityViewModel) {
        self._viewModel = StateObject(wrappedValue: viewModel)
        self._quantityText = State(initialValue: Self.formattedQuantity(viewModel.quantity))
    }

    // MARK: - Body

    var body: some View {
        List {
            Section {
                quantityRow
            }

            Section(header: Text(L10n.FoodQuantity.sectionNutrition)) {
                macroRow(label: L10n.FoodQuantity.calories, value: "\(viewModel.scaledCalories) kcal")
                macroRow(label: L10n.FoodQuantity.protein, value: viewModel.scaledProtein.formattedGrams())
                macroRow(label: L10n.FoodQuantity.carbs, value: viewModel.scaledCarbohydrate.formattedGrams())
                macroRow(label: L10n.FoodQuantity.fat, value: viewModel.scaledFat.formattedGrams())
                macroRow(label: L10n.FoodQuantity.fiber, value: viewModel.scaledFiber.formattedGrams())
            }

            Section {
                HStack {
                    Spacer()
                    FavouriteButton(isFavourite: viewModel.isFavourite) {
                        Task { await viewModel.onFavouriteToggled() }
                    }
                    .disabled(viewModel.isTogglingFavourite)
                    Spacer()
                }
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
        }
        .navigationTitle(viewModel.item.displayName)
        .navigationBarTitleDisplayMode(.inline)
        .loader(viewModel.state.isLoading)
        .alert(item: $viewModel.alertItem) { item in
            Alert(
                title: Text(item.title),
                message: item.message.map(Text.init),
                dismissButton: .default(Text(L10n.Common.ok))
            )
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(L10n.FoodQuantity.buttonAdd) {
                    isQuantityFocused = false
                    Task { await viewModel.onConfirm() }
                }
            }
        }
        .task { await viewModel.onAppear() }
        .sheet(isPresented: $viewModel.isPersonalPortionsSheetVisible) {
            FoodPortionsManagerView(viewModel: viewModel)
        }
    }

    // MARK: - Functions

    var quantityRow: some View {
        HStack {
            Text(L10n.FoodQuantity.inputGrams)
                .font(.system(size: .smallPlus))
                .frame(maxWidth: .infinity, alignment: .leading)
            TextField("1", text: $quantityText)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .frame(width: 60)
                .focused($isQuantityFocused)
                .onChange(of: quantityText) { _, text in
                    var seenSeparator = false
                    let sanitized = String(text.filter { char in
                        if char == "." || char == "," {
                            if seenSeparator { return false }
                            seenSeparator = true
                            return true
                        }
                        return char.isASCII && char.isNumber
                    })
                    if sanitized != text {
                        quantityText = sanitized
                    }
                    let normalized = sanitized.replacingOccurrences(of: ",", with: ".")
                    viewModel.quantity = Double(normalized) ?? 0
                }
            Picker("", selection: $viewModel.unit) {
                ForEach(viewModel.unitOptions, id: \.self) { option in
                    Text(Self.label(for: option)).tag(option)
                }
            }
            .pickerStyle(.menu)
            .onChange(of: viewModel.unit) { oldUnit, newUnit in
                viewModel.onUnitChanged(from: oldUnit, to: newUnit)
                quantityText = Self.formattedQuantity(viewModel.quantity)
            }

            if viewModel.isPersonalPortionsAvailable {
                Button {
                    viewModel.isPersonalPortionsSheetVisible = true
                } label: {
                    Image(systemName: "bookmark")
                }
                .accessibilityLabel(L10n.FoodQuantity.buttonMyPortions)
            }
        }
    }

    func macroRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.system(size: .smallPlus))
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(verbatim: value)
                .foregroundStyle(.secondary)
        }
    }

    static func label(for unit: FoodQuantityUnit) -> String {
        switch unit {
        case .grams: return L10n.FoodQuantity.unitGrams
        case .hundredGrams: return L10n.FoodQuantity.unitHundredGrams
        case .portion(let portion): return "\(portion.name) (\(portion.grams.formattedGrams()))"
        }
    }

    static func formattedQuantity(_ value: Double) -> String {
        var text = String(format: "%.2f", value)
        while text.hasSuffix("0") {
            text.removeLast()
        }
        if text.hasSuffix(".") {
            text.removeLast()
        }
        return text
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        FoodQuantityView(
            viewModel: FoodQuantityViewModel(
                item: FoodItemDomain(
                    id: "1",
                    kind: .catalogue,
                    czName: "Vejce",
                    engName: "Egg",
                    weight: 100,
                    date: .now,
                    energyKJ: 648,
                    caloriesPerHundredGrams: 155,
                    fat: 10,
                    fatSaturated: 3,
                    fatUnsaturatedFattyAcids: 3,
                    carbohydrate: 1,
                    carbohydratePureSugar: 0,
                    fiber: 0,
                    protein: 13,
                    salt: 0.3
                ),
                saveFoodConsumed: SaveFoodConsumedUseCaseFake(),
                fetchMealTypes: FetchMealTypesUseCaseFake(),
                selectedDate: .now,
                mealTypes: [],
                isFavourite: false,
                addFavouriteFood: AddFavouriteFoodUseCaseFake(),
                removeFavouriteFood: RemoveFavouriteFoodUseCaseFake(),
                fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseFake(),
                saveFoodItemPersonalPortions: SaveFoodItemPersonalPortionsUseCaseFake(),
                onSaved: {}
            ) { _, _ in }
        )
    }
}
