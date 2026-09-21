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
    var textAlignment: TextAlignment = .center
    var keyboardType: UIKeyboardType = .default
    var isHighlighted: Bool = false

    // MARK: - Body

    var body: some View {
        HStack {
            if !title.isEmpty {
                Text(title)
                    .font(.system(size: .smallPlus))
            }
            TextField(placeholder, text: $text)
                .keyboardType(keyboardType)
                .fontWeight(isHighlighted ? .bold : .regular)
                .frame(maxWidth: .infinity)
                .multilineTextAlignment(textAlignment)
        }
    }
}
