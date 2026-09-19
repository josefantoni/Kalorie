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
    @Environment(\.dismiss) private var dismiss
    @FocusState private var focusedField: UUID?

    private static let edgeRowSpacingExtra: CGFloat = 8
    private static let rowSpacing: CGFloat = 12
    private static let addButtonSize: CGFloat = 28

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
                    ForEach(Array(viewModel.personalPortions.enumerated()), id: \.element) { index, portion in
                        readOnlyPortionRow(portion: portion, index: index)
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    Task { await viewModel.onDeletePersonalPortion(portion) }
                                } label: {
                                    Image(systemName: "trash")
                                }
                            }
                    }
                }

                ForEach(Array(viewModel.portionDrafts.enumerated()), id: \.element.id) { index, draft in
                    PortionInputRow(
                        name: $viewModel.portionDrafts[index].name,
                        gramsText: $viewModel.portionDrafts[index].gramsText,
                        measure: viewModel.item.measure,
                        focusedField: $focusedField,
                        focusValue: draft.id
                    )
                    .padding(.bottom, Self.rowSpacing)
                    .overlay(alignment: .bottom) {
                        if index < viewModel.portionDrafts.count - 1 {
                            Rectangle()
                                .fill(Color(uiColor: .separator))
                                .frame(height: 1)
                                .padding(.horizontal, 16)
                        }
                    }
                    .listRowInsets(EdgeInsets(
                        top: index == 0 && viewModel.personalPortions.isEmpty ? Self.edgeRowSpacingExtra : Self.rowSpacing,
                        leading: 0,
                        bottom: index == viewModel.portionDrafts.count - 1 ? Self.edgeRowSpacingExtra : 0,
                        trailing: 0
                    ))
                    .listRowSeparator(.hidden)
                    .listRowSeparatorTint(.clear)
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            viewModel.onDeletePortionDraft(draft)
                        } label: {
                            Image(systemName: "trash")
                        }
                    }
                }
            }

            Section {
                Button {
                    viewModel.onAddPortionDraftTapped()
                    focusedField = viewModel.portionDrafts.last?.id
                } label: {
                    Image(systemName: BaseImageName.plus.rawValue)
                        .font(.footnote)
                        .fontWeight(.semibold)
                        .foregroundStyle(.white)
                        .frame(width: Self.addButtonSize, height: Self.addButtonSize)
                }
                .background(Color.accentColor)
                .clipShape(.circle)
                .disabled(!viewModel.arePortionDraftsComplete)
                .opacity(viewModel.arePortionDraftsComplete ? 1 : 0.4)
                .frame(maxWidth: .infinity)
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
            }
            .listSectionSpacing(0)
        }
        .navigationTitle(L10n.MyPortions.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                SaveToolbarButton(
                    title: L10n.MyPortions.buttonSave,
                    showCheckmark: viewModel.showPortionCheckmark,
                    isEnabled: viewModel.canSavePortionDrafts
                ) {
                    focusedField = nil
                    Task { await viewModel.onSavePersonalPortions() }
                }
            }
        }
        .alert(item: $viewModel.alertItem) { item in
            Alert(
                title: Text(item.title),
                message: item.message.map(Text.init),
                dismissButton: .default(Text(L10n.Common.ok))
            )
        }
    }

    // MARK: - Functions

    private func readOnlyPortionRow(portion: FoodPortionDomain, index: Int) -> some View {
        HStack {
            Text(portion.name)
            Spacer()
            Text(portion.grams.formattedAmount(measure: viewModel.item.measure))
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal)
        .padding(.vertical, 12)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(Color(uiColor: .separator))
                .frame(height: 1)
                .padding(.horizontal, 16)
        }
        .listRowInsets(EdgeInsets(
            top: index == 0 ? Self.edgeRowSpacingExtra : 0,
            leading: 0,
            bottom: 0,
            trailing: 0
        ))
        .listRowSeparator(.hidden)
        .listRowSeparatorTint(.clear)
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
                fetchMyFoodItemReport: FetchMyFoodItemReportUseCaseFake(),
                submitFoodItemReport: SubmitFoodItemReportUseCaseFake(),
                meal: nil,
                updateMyCreatedMeal: UpdateMyCreatedMealUseCaseFake(),
                onSaved: {},
                onMealUpdated: { _ in }
            ) { _, _ in }
        )
    }
}
