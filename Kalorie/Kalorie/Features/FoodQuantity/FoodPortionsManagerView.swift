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
    @FocusState private var isNameFocused: Bool

    // MARK: - Init

    init(viewModel: FoodQuantityViewModel) {
        self.viewModel = viewModel
    }

    // MARK: - Body

    var body: some View {
        List {
            Section {
                if viewModel.personalPortions.isEmpty {
                    Text(L10n.MyPortions.empty)
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.personalPortions, id: \.self) { portion in
                        HStack {
                            Text(portion.name)
                            Spacer()
                            Text(portion.grams.formattedAmount(measure: viewModel.item.measure))
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

                if viewModel.isAddPortionFormVisible {
                    HStack {
                        BaseStringTextField(
                            placeholder: L10n.FoodPortion.fieldNamePlaceholder,
                            title: "",
                            text: $viewModel.newPortionName,
                            textAlignment: .leading
                        )
                        .focused($isNameFocused)
                        TextField("0", text: $viewModel.newPortionGramsText)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 50)
                        Text(viewModel.item.measure.unitSymbol)
                            .foregroundStyle(.secondary)
                    }
                    Button(L10n.FoodPortion.buttonAdd) {
                        isNameFocused = false
                        Task { await viewModel.onAddPersonalPortion() }
                    }
                } else {
                    BaseButton(style: .plain, imageName: .plusCircle, imageSize: .extraLarge) {
                        viewModel.onShowAddPortionForm()
                        isNameFocused = true
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .navigationTitle(L10n.MyPortions.title)
        .navigationBarTitleDisplayMode(.inline)
        .alert(item: $viewModel.alertItem) { item in
            Alert(
                title: Text(item.title),
                message: item.message.map(Text.init),
                dismissButton: .default(Text(L10n.Common.ok))
            )
        }
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
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
}
