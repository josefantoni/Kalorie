//
//  MealTypeSheetView.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.06.2024.
//

import Foundation
import SwiftUI

struct MealTypeSheetView: View {
    @Environment(\.dismiss) var dismiss
    @State var foodItems = ["Snídaně", "Oběd", "Večeře"]

    var body: some View {
        VStack {
            HStack {
                Spacer()
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark.circle")
                        .font(.largeTitle)
                        .foregroundColor(.gray)
                }
            }
            List($foodItems, id: \.self, editActions: .move) { food in
                // TODO: List item UI
                MealTypeItemView(food.wrappedValue)
            }
            .environment(\.editMode, .constant(EditMode.active))
         }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
        .padding()
    }
}
