//
//  FoodPortionsManagerView.swift
//  Kalorie
//
//  Created by Josef Antoni on 09.09.2026.
//

import SwiftUI

struct FoodPortionsManagerView: View {

    // MARK: - Properties

    @ObservedObject var viewModel: FoodQuantityViewModel
    @State private var newName = ""
    @State private var newGramsText: String
    @Environment(\.dismiss) private var dismiss

    // MARK: - Init

    init(viewModel: FoodQuantityViewModel) {
        self.viewModel = viewModel
        self._newGramsText = State(initialValue: FoodQuantityView.formattedQuantity(viewModel.grams))
    }

    // MARK: - Body

    var body: some View {
        NavigationStack {
            List {
                if viewModel.personalPortions.isEmpty {
                    Text(L10n.MyPortions.empty)
                        .foregroundStyle(.secondary)
                } else {
                    Section {
                        ForEach(viewModel.personalPortions, id: \.self) { portion in
                            HStack {
                                Text(portion.name)
                                Spacer()
                                Text(portion.grams.formattedGrams())
                                    .foregroundStyle(.secondary)
                            }
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    Task { await viewModel.onDeletePersonalPortion(portion) }
                                } label: {
                                    Image(systemName: "trash")
                                }
                            }
                        }
                    }
                }

                Section {
                    HStack {
                        BaseStringTextField(
                            placeholder: L10n.FoodPortion.fieldNamePlaceholder,
                            title: "",
                            text: $newName,
                            textAlignment: .leading
                        )
                        TextField("0", text: $newGramsText)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 50)
                        Text(L10n.Common.unitGrams)
                            .foregroundStyle(.secondary)
                    }
                    Button(L10n.FoodPortion.buttonAdd) {
                        let grams = Double(newGramsText.replacingOccurrences(of: ",", with: ".")) ?? 0
                        guard FoodPortionValidation.validate(name: newName, grams: grams) == nil else { return }
                        Task {
                            await viewModel.onAddPersonalPortion(name: newName, grams: grams)
                            newName = ""
                            newGramsText = FoodQuantityView.formattedQuantity(viewModel.grams)
                        }
                    }
                }
            }
            .navigationTitle(L10n.MyPortions.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                DismissToolbarItem()
            }
        }
    }
}

// MARK: - Preview

#Preview {
    FoodPortionsManagerView(
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
