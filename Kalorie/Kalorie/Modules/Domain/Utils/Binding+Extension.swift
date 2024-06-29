//
//  Binding+Extension.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2024.
//

import Foundation
import SwiftUI


public extension Binding {
    
    // Create a non-optional version of an optional `Binding` with a default value
    // - Parameters:
    //   - lhs: The original `Binding<Value?>` (binding to an optional value)
    //   - rhs: The default value if the original `wrappedValue` is `nil`
    // - Returns: The `Binding<Value>` (where `Value` is non-optional)
    static func ?? (lhs: Binding<Value?>, rhs: Value) -> Binding<Value> {
        Binding {
            lhs.wrappedValue ?? rhs
        } set: {
            lhs.wrappedValue = $0
        }
    }
}
