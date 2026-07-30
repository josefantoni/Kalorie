//
//  FoodConsumedDetailView.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.07.2026.
//

import SwiftUI

struct FoodConsumedDetailView: View {

    // MARK: - Properties

    @StateObject var viewModel: FoodConsumedDetailViewModel
    @State private var weightText: String

    // MARK: - Init

    init(viewModel: FoodConsumedDetailViewModel) {
        let weight = viewModel.weight
        let text = weight.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(weight)) : String(weight)
        self._viewModel = StateObject(wrappedValue: viewModel)
        self._weightText = State(initialValue: text)
    }

    // MARK: - Body

    var body: some View {
        let macros = viewModel.scaledMacros
        List {
            Section {
                LabeledContent(L10n.AddFood.fieldWeight) {
                    HStack(spacing: 4) {
                        TextField("0", text: $weightText)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 80)
                            .onChange(of: weightText) { _, text in
                                var seenSeparator = false
                                let sanitized = String(text.filter { char in
                                    if char == "." || char == "," {
                                        if seenSeparator { return false }
                                        seenSeparator = true
                                        return true
                                    }
                                    return char.isNumber
                                })
                                if sanitized != text { weightText = sanitized }
                                let normalized = sanitized.replacingOccurrences(of: ",", with: ".")
                                if let value = Double(normalized) { viewModel.weight = value }
                            }
                        Text("g")
                    }
                }
                LabeledContent(L10n.FoodConsumedDetail.labelTime) {
                    Text(viewModel.food.date, style: .time)
                }
            }

            Section(L10n.FoodQuantity.sectionNutrition) {
                LabeledContent(L10n.FoodQuantity.calories) {
                    Text("\(macros.calories) kcal")
                }
                LabeledContent(L10n.FoodQuantity.protein) {
                    Text(String(format: "%.1f g", macros.protein))
                }
                LabeledContent(L10n.FoodQuantity.carbs) {
                    Text(String(format: "%.1f g", macros.carbohydrate))
                }
                LabeledContent(L10n.AddFood.fieldCarbsSugar) {
                    Text(String(format: "%.1f g", macros.carbohydrateSugar))
                }
                LabeledContent(L10n.FoodQuantity.fat) {
                    Text(String(format: "%.1f g", macros.fat))
                }
                LabeledContent(L10n.AddFood.fieldFatUnsaturated) {
                    Text(String(format: "%.1f g", macros.fatUnsaturated))
                }
                LabeledContent(L10n.FoodQuantity.fiber) {
                    Text(String(format: "%.1f g", macros.fiber))
                }
                LabeledContent(L10n.AddFood.fieldSalt) {
                    Text(String(format: "%.2f g", macros.salt))
                }
            }
        }
        .navigationTitle(viewModel.food.displayName)
        .navigationBarTitleDisplayMode(.large)
        .loader(viewModel.state.isLoading)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await viewModel.onSave() }
                } label: {
                    if viewModel.showCheckmark {
                        Image(systemName: "checkmark")
                            .foregroundStyle(.green)
                            .transition(.scale.combined(with: .opacity))
                    } else {
                        Text(L10n.FoodConsumedDetail.buttonSave)
                            .transition(.opacity)
                    }
                }
                .animation(.spring(duration: 0.4), value: viewModel.showCheckmark)
                .disabled(!viewModel.hasWeightChanged || viewModel.state.isLoading)
            }
        }
        .alert(item: $viewModel.alertItem) { item in
            Alert(
                title: Text(item.title),
                dismissButton: .default(Text(L10n.Common.ok))
            )
        }
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        FoodConsumedDetailView(
            viewModel: FoodConsumedDetailViewModel(
                food: FoodConsumedDomain(
                    id: "1",
                    czName: "Ovesné vločky",
                    engName: "Oats",
                    weight: 80,
                    date: .now,
                    calories: 295,
                    protein: 10,
                    carbohydrate: 52,
                    carbohydrateSugar: 8,
                    fat: 5,
                    fatUnsaturated: 2,
                    fiber: 6,
                    salt: 0.1
                ),
                updateFoodConsumed: UpdateFoodConsumedUseCaseFake()
            ) {}
        )
    }
}
