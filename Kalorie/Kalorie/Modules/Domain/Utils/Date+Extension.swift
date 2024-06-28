//
//  Date+Extension.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//

import Foundation


extension Date {
    
    // MARK: - Properties
    
    var tupledTime: (String, String) {
        let hour = Calendar.current.component(.hour, from: self).makeDoubleDigit
        let minute = Calendar.current.component(.minute, from: self).makeDoubleDigit
        return (hour, minute)
    }

    
    // MARK: - Function

    func formatDateStyle(with format: String) -> String {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = format
        return dateFormatter.string(from: self)
    }
    
    func withAddedMinutes(minutes: Double) -> Date {
         addingTimeInterval(minutes * 60)
    }

    func withAddedHours(hours: Double) -> Date {
         withAddedMinutes(minutes: hours * 60)
    }
    
    func isBetween(_ start: Date, _ end: Date) -> Bool {
         start < self && self < end
    }
}
