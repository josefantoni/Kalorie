//
//  Date+Extension.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//

import Foundation


extension Date {
    
    // MARK: - Function

    func formatDateStyle(with format: String) -> String {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = format
        return dateFormatter.string(from: self)
    }
}
