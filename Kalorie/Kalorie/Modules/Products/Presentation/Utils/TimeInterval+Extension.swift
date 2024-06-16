//
//  TimeInterval+Extension.swift
//  Kalorie
//
//  Created by Josef Antoni on 15.06.2024.
//

import Foundation


extension TimeInterval {
    
    var toDate: Date {
        Date(timeIntervalSince1970: self)
    }
}
