//
//  View+Extension.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2024.
//

import Foundation
import SwiftUI


extension View {
    
   @ViewBuilder
   func `if`<Content: View>(_ conditional: Bool, content: (Self) -> Content) -> some View {
        if conditional {
            content(self)
        } else {
            self
        }
    }
}
