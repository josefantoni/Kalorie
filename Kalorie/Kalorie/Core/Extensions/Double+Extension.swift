//
//  Double+Extension.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.08.2026.
//

import Foundation

extension Double {

    func formattedGrams(fractionDigits: Int = 1) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = fractionDigits
        formatter.maximumFractionDigits = fractionDigits
        let number = formatter.string(from: NSNumber(value: self)) ?? String(self)
        return "\(number) \(L10n.Common.unitGrams)"
    }
}
