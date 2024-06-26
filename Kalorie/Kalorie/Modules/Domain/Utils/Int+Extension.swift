//
//  Int+Extension.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//

import Foundation


extension Int {
    
    var toInt16: Int16 {
        Int16(self)
    }
    
    var toString: String {
        String(self)
    }
    
    var makeDoubleDigit: String {
        String(format: "%02d", self)
    }
}


extension Int16 {
    
    var toInt: Int {
        Int(self)
    }
}
