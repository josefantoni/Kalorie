//
//  FoodQuantityView.swift
//  Kalorie
//
//  Created by Josef Antoni on 24.07.2026.
//

import SwiftUI

struct FoodQuantityView: View {

    // MARK: - Properties

    @ObservedObject var viewModel: FoodQuantityViewModel

    // MARK: - Body

    var body: some View {
        List {
            Section {
                quantityRow
            }

            Section(header: Text(L10n.FoodQuantity.sectionNutrition)) {
                macroRow(label: L10n.FoodQuantity.calories, value: "\(viewModel.scaledCalories)", suffix: "kcal")
                macroRow(label: L10n.FoodQuantity.protein, value: formatted(viewModel.scaledProtein), suffix: "g")
                macroRow(label: L10n.FoodQuantity.carbs, value: formatted(viewModel.scaledCarbohydrate), suffix: "g")
                macroRow(label: L10n.FoodQuantity.fat, value: formatted(viewModel.scaledFat), suffix: "g")
                macroRow(label: L10n.FoodQuantity.fiber, value: formatted(viewModel.scaledFiber), suffix: "g")
            }
        }
        .navigationTitle(viewModel.item.displayName)
        .navigationBarTitleDisplayMode(.inline)
        .loader(viewModel.state.isLoading)
        .alert(item: $viewModel.alertItem) { item in
            Alert(
                title: Text(item.title),
                dismissButton: .default(Text(L10n.Common.ok))
            )
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(L10n.FoodQuantity.buttonAdd) {
                    Task { await viewModel.onConfirm() }
                }
            }
        }
    }

    // MARK: - Functions

    var quantityRow: some View {
        HStack {
            Text(L10n.FoodQuantity.inputGrams)
                .font(.system(size: .smallPlus))
                .frame(maxWidth: .infinity, alignment: .leading)
            TextField("1", value: $viewModel.quantity, formatter: NumberFormatter.decimal)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .frame(width: 60)
            Picker("", selection: $viewModel.unit) {
                Text(L10n.FoodQuantity.unitPortions).tag(FoodQuantityUnit.portions)
                Text(L10n.FoodQuantity.unitGrams).tag(FoodQuantityUnit.grams)
            }
            .pickerStyle(.menu)
            .onChange(of: viewModel.unit) {
                viewModel.onUnitChanged()
            }
        }
    }

    func macroRow(label: String, value: String, suffix: String) -> some View {
        HStack {
            Text(label)
                .font(.system(size: .smallPlus))
                .frame(maxWidth: .infinity, alignment: .leading)
            Text("\(value) \(suffix)")
                .foregroundStyle(.secondary)
        }
    }

    func formatted(_ value: Double) -> String {
        String(format: "%.1f", value)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        FoodQuantityView(
            viewModel: FoodQuantityViewModel(
                item: FoodItemDomain(
                    id: "1",
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
                selectedDate: .now
            ) {}
        )
    }
}
