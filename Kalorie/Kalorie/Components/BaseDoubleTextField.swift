//
//  BaseDoubleTextField.swift
//  Kalorie
//
//  Created by Josef Antoni on 05.07.2024.
//

import Foundation
import SwiftUI

struct BaseDoubleTextField: View {

    // MARK: - Properties

    var title: String
    var unit: String
    @Binding var weight: Double

    // MARK: - Body

    var body: some View {
        HStack {
            Text(title)
                .font(
                    .system(
                        size: .smallPlus
                    )
                )
                .frame(
                    maxWidth: .infinity,
                    alignment: .leading
                )

            TextField("0", value: $weight, formatter: NumberFormatter.decimal)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .frame(
                    width: 100,
                    alignment: .center
                )
            Text(unit)
                .frame(alignment: .trailing)
                .padding(.horizontal, 8)
        }
    }
}
