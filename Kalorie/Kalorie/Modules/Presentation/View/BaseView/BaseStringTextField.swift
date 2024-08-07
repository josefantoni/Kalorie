//
//  BaseStringTextField.swift
//  Kalorie
//
//  Created by Josef Antoni on 05.07.2024.
//

import Foundation
import SwiftUI


struct BaseStringTextField: View {
    
    // MARK: - Properties

    let placeholder: String
    let title: String
    @Binding var text: String

    
    // MARK: - Body

    var body: some View {
        HStack {
            Text(title)
                .font(.system(size: .smallPlus))
            TextField(placeholder, text: $text)
                .keyboardType(.numberPad)
                .frame(maxWidth: .infinity)
                .multilineTextAlignment(.center)
        }
    }
}
