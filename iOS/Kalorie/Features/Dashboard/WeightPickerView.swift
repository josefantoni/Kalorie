//
//  WeightPickerView.swift
//  Kalorie
//
//  Created by Josef Antoni on 27.10.2026.
//

import SwiftUI

struct WeightPickerView: View {

    // MARK: - Properties

    @Binding var kilograms: Double
    let unit: BodyWeightUnit

    private var tenths: Int { unit.tenths(kilograms: kilograms) }

    // MARK: - Body

    var body: some View {
        HStack(spacing: 0) {
            Picker(L10n.Dashboard.weightPickerWhole, selection: wholeBinding) {
                ForEach(unit.rangeInTenths.lowerBound / 10...unit.rangeInTenths.upperBound / 10, id: \.self) { value in
                    Text("\(value)").tag(value)
                }
            }
            .pickerStyle(.wheel)
            .frame(width: 100)

            Text(Locale.current.decimalSeparator ?? ".")
                .font(.title2.bold())

            Picker(L10n.Dashboard.weightPickerTenths, selection: tenthBinding) {
                ForEach(0...9, id: \.self) { value in
                    Text("\(value)").tag(value)
                }
            }
            .pickerStyle(.wheel)
            .frame(width: 60)

            Text(unit.symbol)
                .font(.title3)
                .foregroundStyle(.secondary)
                .frame(width: 40)
        }
        .frame(height: 216)
    }

    // MARK: - Functions

    private var wholeBinding: Binding<Int> {
        Binding(
            get: { tenths / 10 },
            set: { update(tenths: $0 * 10 + tenths % 10) }
        )
    }

    private var tenthBinding: Binding<Int> {
        Binding(
            get: { tenths % 10 },
            set: { update(tenths: tenths / 10 * 10 + $0) }
        )
    }

    private func update(tenths newValue: Int) {
        let clamped = min(max(newValue, unit.rangeInTenths.lowerBound), unit.rangeInTenths.upperBound)
        guard clamped != tenths else { return }
        kilograms = unit.kilograms(from: Double(clamped) / 10)
    }
}
