//
//  NumberFormatter+Extension.swift
//  Kalorie
//
//  Created by Josef Antoni on 24.07.2026.
//

import Foundation

extension NumberFormatter {
    static let decimal: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.zeroSymbol = ""
        return formatter
    }()
}
