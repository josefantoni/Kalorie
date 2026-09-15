//
//  FoodItemFormFields.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import SwiftUI

enum FoodItemFormField: CaseIterable {
    case name, measure, weight, energyKJ, calories, protein, carbohydrate, carbohydrateSugar, fiber, fat, fatSaturated, fatUnsaturated, salt
}

struct FoodItemFormFields: View {

    // MARK: - Properties

    @Binding var formInput: FoodItemFormInput
    var highlightedFields: Set<FoodItemFormField> = []
    var onFieldEdited: (FoodItemFormField) -> Void = { _ in }

    // MARK: - Body

    var body: some View {
        Group {
            measureRow
            weightRow
            BaseDoubleTextField(
                title: L10n.AddFood.fieldEnergyKJ,
                unit: "kJ",
                weight: doubleBinding(\.energyKJ, field: .energyKJ),
                isHighlighted: highlightedFields.contains(.energyKJ)
            )
            BaseDoubleTextField(
                title: formInput.measure == .grams ? L10n.AddFood.fieldCaloriesPer100g : L10n.AddFood.fieldCaloriesPer100ml,
                unit: "kcal",
                weight: doubleBinding(\.caloriesPerHundredGrams, field: .calories),
                isHighlighted: highlightedFields.contains(.calories)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldProtein,
                unit: L10n.Common.unitGrams,
                weight: doubleBinding(\.protein, field: .protein),
                isHighlighted: highlightedFields.contains(.protein)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldCarbs,
                unit: L10n.Common.unitGrams,
                weight: doubleBinding(\.carbohydrate, field: .carbohydrate),
                isHighlighted: highlightedFields.contains(.carbohydrate)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldCarbsSugar,
                unit: L10n.Common.unitGrams,
                weight: doubleBinding(\.carbohydratePureSugar, field: .carbohydrateSugar),
                isHighlighted: highlightedFields.contains(.carbohydrateSugar)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldFiber,
                unit: L10n.Common.unitGrams,
                weight: optionalDoubleBinding(\.fiber, field: .fiber),
                isHighlighted: highlightedFields.contains(.fiber)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldFat,
                unit: L10n.Common.unitGrams,
                weight: doubleBinding(\.fat, field: .fat),
                isHighlighted: highlightedFields.contains(.fat)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldFatSaturated,
                unit: L10n.Common.unitGrams,
                weight: optionalDoubleBinding(\.fatSaturated, field: .fatSaturated),
                isHighlighted: highlightedFields.contains(.fatSaturated)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldFatUnsaturated,
                unit: L10n.Common.unitGrams,
                weight: doubleBinding(\.fatUnsaturatedFattyAcids, field: .fatUnsaturated),
                isHighlighted: highlightedFields.contains(.fatUnsaturated)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldSalt,
                unit: L10n.Common.unitGrams,
                weight: doubleBinding(\.salt, field: .salt),
                isHighlighted: highlightedFields.contains(.salt)
            )
        }
    }

    // MARK: - Functions

    private var measureRow: some View {
        HStack {
            Text(L10n.AddFood.fieldMeasure)
                .font(.system(size: .smallPlus))
                .fontWeight(highlightedFields.contains(.measure) ? .bold : .regular)
                .frame(maxWidth: .infinity, alignment: .leading)
            Picker("", selection: measureBinding) {
                Text(L10n.Common.unitGrams).tag(FoodMeasure.grams)
                Text(L10n.Common.unitMillilitres).tag(FoodMeasure.millilitres)
            }
            .pickerStyle(.segmented)
            .frame(width: 150)
        }
    }

    private var weightRow: some View {
        HStack {
            Text(L10n.AddFood.fieldWeight)
                .font(.system(size: .smallPlus))
                .frame(maxWidth: .infinity, alignment: .leading)
            TextField("0", value: doubleBinding(\.weightOfProduct, field: .weight), formatter: NumberFormatter.decimal)
                .keyboardType(.decimalPad)
                .fontWeight(highlightedFields.contains(.weight) ? .bold : .regular)
                .multilineTextAlignment(.trailing)
                .frame(width: 100, alignment: .center)
            Menu {
                Button(formInput.measure.unitSymbol) { setWeightInThousands(false) }
                Button(formInput.measure.thousandUnitSymbol) { setWeightInThousands(true) }
            } label: {
                Text(formInput.isWeightInThousands ? formInput.measure.thousandUnitSymbol : formInput.measure.unitSymbol)
                    .frame(alignment: .trailing)
                    .padding(.horizontal, 8)
            }
        }
    }

    private var measureBinding: Binding<FoodMeasure> {
        Binding(
            get: { formInput.measure },
            set: { formInput.measure = $0; onFieldEdited(.measure) }
        )
    }

    private func setWeightInThousands(_ newValue: Bool) {
        guard newValue != formInput.isWeightInThousands else { return }
        formInput.weightOfProduct = newValue ? formInput.weightOfProduct / 1000 : formInput.weightOfProduct * 1000
        formInput.isWeightInThousands = newValue
    }

    private func doubleBinding(_ keyPath: WritableKeyPath<FoodItemFormInput, Double>, field: FoodItemFormField) -> Binding<Double> {
        Binding(
            get: { formInput[keyPath: keyPath] },
            set: { formInput[keyPath: keyPath] = $0; onFieldEdited(field) }
        )
    }

    private func optionalDoubleBinding(_ keyPath: WritableKeyPath<FoodItemFormInput, Double?>, field: FoodItemFormField) -> Binding<Double> {
        Binding(
            get: { formInput[keyPath: keyPath] ?? 0 },
            set: { formInput[keyPath: keyPath] = $0; onFieldEdited(field) }
        )
    }
}

// MARK: - Preview

#Preview {
    List {
        FoodItemFormFields(formInput: .constant(FoodItemFormInput()))
    }
}
