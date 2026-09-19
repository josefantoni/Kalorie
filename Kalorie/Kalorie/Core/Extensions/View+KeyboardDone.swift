//
//  View+KeyboardDone.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import SwiftUI

extension View {
    func keyboardDoneToolbar() -> some View {
        toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(L10n.Common.buttonDone) {
                    UIApplication.shared.sendAction(
                        #selector(UIResponder.resignFirstResponder),
                        to: nil,
                        from: nil,
                        for: nil
                    )
                }
            }
        }
    }
}
