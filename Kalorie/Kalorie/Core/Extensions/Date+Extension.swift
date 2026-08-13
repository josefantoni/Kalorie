//
//  Date+Extension.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//

import Foundation
import MealKit

extension Date {

    // MARK: - Properties

    var tupledTime: (String, String) {
        let hour = Calendar.current.component(.hour, from: self).makeDoubleDigit
        let minute = Calendar.current.component(.minute, from: self).makeDoubleDigit
        return (hour, minute)
    }

    var minutesSinceMidnight: Int32 {
        let components = Calendar.current.dateComponents([.hour, .minute], from: self)
        return MealWindowsKt.minutesSinceMidnight(hour: Int32(components.hour ?? 0), minute: Int32(components.minute ?? 0))
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
