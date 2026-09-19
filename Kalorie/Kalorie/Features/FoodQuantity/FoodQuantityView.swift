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
    @State private var isDeleteConfirmationVisible = false
    @State private var isMealEditorPushed = false
    private var makeMealEditorView: (() -> MyCreatedMealEditorView)?
    private var onDeleteMealConfirmed: (() -> Void)?

    // MARK: - Init

    init(viewModel: FoodQuantityViewModel) {
        self._viewModel = StateObject(wrappedValue: viewModel)
        self._quantityText = State(initialValue: viewModel.quantity.formattedTrimmed())
    }

    // MARK: - Body

    var body: some View {
        List {
            Section {
                HStack {
                    Text(viewModel.item.displayName)
                        .font(.headline)
                        .lineLimit(1)
                    Spacer()
                    FavouriteButton(isFavourite: viewModel.isFavourite) {
                        Task { await viewModel.onFavouriteToggled() }
                    }
                    .disabled(viewModel.isTogglingFavourite)
                }
                quantityRow
                mealTypeRow
            }

            Section(header: Text(L10n.FoodQuantity.sectionNutrition)) {
                macroRow(label: L10n.FoodQuantity.calories, value: "\(viewModel.scaledCalories) kcal")
                macroRow(label: L10n.FoodQuantity.protein, value: viewModel.scaledProtein.formattedGrams())
                macroRow(label: L10n.FoodQuantity.carbs, value: viewModel.scaledCarbohydrate.formattedGrams())
                macroRow(label: L10n.AddFood.fieldCarbsSugar, value: viewModel.scaledCarbohydrateSugar.formattedGrams())
                macroRow(label: L10n.FoodQuantity.fat, value: viewModel.scaledFat.formattedGrams())
                macroRow(label: L10n.AddFood.fieldFatSaturated, value: viewModel.scaledFatSaturated.formattedGrams())
                macroRow(label: L10n.FoodQuantity.fiber, value: viewModel.scaledFiber.formattedGrams())
                macroRow(label: L10n.AddFood.fieldSalt, value: viewModel.scaledSalt.formattedGrams(fractionDigits: 2))
            }

            if onDeleteMealConfirmed != nil {
                Section {
                    Button(L10n.MyCreatedMeal.buttonDelete, role: .destructive) {
                        isDeleteConfirmationVisible = true
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .alert(L10n.MyCreatedMeal.confirmDelete, isPresented: $isDeleteConfirmationVisible) {
            Button(L10n.Common.buttonCancel, role: .cancel) {}
            Button(L10n.MyCreatedMeal.buttonDelete, role: .destructive) {
                onDeleteMealConfirmed?()
            }
        }
        .loader(viewModel.state.isLoading)
        .alert(item: $viewModel.alertItem) { item in
            Alert(
                title: Text(item.title),
                message: item.message.map(Text.init),
                dismissButton: .default(Text(L10n.Common.ok))
            )
        }
        .toolbar {
            if makeMealEditorView != nil {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        isMealEditorPushed = true
                    } label: {
                        Image(systemName: "pencil")
                    }
                    .accessibilityLabel(L10n.MyCreatedMeal.buttonEdit)
                }
            }
            if viewModel.canReportIncorrectData {
                ToolbarItem(placement: .topBarLeading) {
                    Menu {
                        Button(
                            viewModel.hasReportedCurrentItem ? L10n.FoodItemReport.buttonAlreadyReported : L10n.FoodItemReport.buttonReport
                        ) {
                            viewModel.onReportIncorrectDataTapped()
                        }
                        .disabled(viewModel.hasReportedCurrentItem)
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button(L10n.FoodQuantity.buttonAdd) {
                    isQuantityFocused = false
                    Task { await viewModel.onConfirm() }
                }
            }
        }
        .task { await viewModel.onAppear() }
        .navigationDestination(isPresented: $viewModel.isPersonalPortionsManagerPushed) {
            FoodPortionsManagerView(viewModel: viewModel)
        }
        .navigationDestination(isPresented: $isMealEditorPushed) {
            if let makeMealEditorView {
                makeMealEditorView()
            }
        }
        .alert(L10n.FoodItemReport.alertTitle, isPresented: $viewModel.isReportReasonAlertVisible) {
            TextField(L10n.FoodItemReport.alertPlaceholder, text: $viewModel.reportReasonText)
            Button(L10n.Common.buttonCancel, role: .cancel) {}
            Button(L10n.FoodItemReport.buttonSend) {
                Task { await viewModel.onReportSubmitted() }
            }
        }
    }

    // MARK: - Functions

    func mealActions(makeEditorView: @escaping () -> MyCreatedMealEditorView, onDelete: @escaping () -> Void) -> Self {
        var copy = self
        copy.makeMealEditorView = makeEditorView
        copy.onDeleteMealConfirmed = onDelete
        return copy
    }

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
            Text(verbatim: "×")
                .foregroundStyle(.secondary)
            Menu {
                Picker("", selection: unitBinding) {
                    ForEach(viewModel.unitOptions, id: \.self) { option in
                        Text(Self.label(for: option, measure: viewModel.item.measure)).tag(option)
                    }
                }
                if viewModel.isPersonalPortionsAvailable {
                    Divider()
                    Button(L10n.FoodQuantity.buttonMyPortions) {
                        viewModel.onPortionsManagerOpened()
                        viewModel.isPersonalPortionsManagerPushed = true
                    }
                }
            } label: {
                HStack(spacing: 4) {
                    Text(Self.label(for: viewModel.unit, measure: viewModel.item.measure))
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .onChange(of: viewModel.unit) { _, _ in
                quantityText = viewModel.quantity.formattedTrimmed()
            }
        }
    }

    var unitBinding: Binding<FoodQuantityUnit> {
        Binding(
            get: { viewModel.unit },
            set: { viewModel.onUnitSelected($0) }
        )
    }

    var mealTypeRow: some View {
        LabeledContent(L10n.FoodQuantity.labelMealType) {
            Picker("", selection: mealTypeBinding) {
                if viewModel.selectedMealTypeId == nil {
                    Text(L10n.FoodQuantity.mealTypeUnassigned).tag(String?.none)
                }
                ForEach(viewModel.mealTypes, id: \.id) { mealType in
                    Text(mealType.name).tag(String?.some(mealType.id))
                }
            }
            .pickerStyle(.menu)
        }
    }

    var mealTypeBinding: Binding<String?> {
        Binding(
            get: { viewModel.selectedMealTypeId },
            set: { newValue in
                guard let newValue else { return }
                viewModel.onMealTypeSelected(newValue)
            }
        )
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

    static func label(for unit: FoodQuantityUnit, measure: FoodMeasure) -> String {
        switch unit {
        case .grams: return measure == .grams ? L10n.FoodQuantity.unitGrams : L10n.FoodQuantity.unitMillilitres
        case .hundredGrams: return measure == .grams ? L10n.FoodQuantity.unitHundredGrams : L10n.FoodQuantity.unitHundredMillilitres
        case .portion(let portion): return "\(portion.name) (\(portion.grams.formattedAmount(measure: measure)))"
        }
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
